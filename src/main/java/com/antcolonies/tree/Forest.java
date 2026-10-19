package com.antcolonies.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.Population;
import com.antcolonies.debug.Perf;
import com.antcolonies.worldgen.FallenBranchFeature;
import com.antcolonies.worldgen.TreePlacer;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * The tree book (M7.5 step 4): every giant savanna tree in one dimension, kept in the world's saved data, and its life
 * in play (docs/DESIGN.md "Growing in play"; the numbers in {@link TreeLife}).
 * <ul>
 * <li><b>4a, the book:</b> a record per tree (kind, foot, seed, age, stage, the day it last grew, and the age whose
 * shape is in the world). New land's trees are written in when their structure piece is placed; a seedling planted by a
 * player or come up by itself writes one too. A tree whose trunk has been cut through at its foot ends its record.</li>
 * <li><b>4b, growing:</b> about once an in-game day each loaded tree grows a small step, even in sight; only the blocks
 * that differ change ({@link TreePlacer#grow}); twigs the young tree sheds fall as fallen branches. Bone meal grows a
 * seedling or young tree one day's step at once, never past young.</li>
 * <li><b>4c, seedlings after rain:</b> real acacia seeds pass through browsers and sprout in their dung far from the
 * parent, mostly after a wet spell; so the morning after a day of rain a few seedlings come up scattered over loaded
 * savanna ground, not round a parent (and leaves drop a seedling now and then for a player, their loot).</li>
 * <li><b>4d, most die young:</b> each day a little tree may die ({@link TreeLife#deathChance}): a seedling dries and
 * vanishes, a young tree leaves a fallen branch.</li>
 * <li><b>4e, time away:</b> a tree whose land was not loaded catches up every day it missed (growing and dying) when its
 * land loads, placed out of sight at once; ground away gets the seedlings of the rain days it missed.</li>
 * </ul>
 * <b>Step 5, death:</b> every tree has its own lifespan ({@link TreeLife#lifespan}); on the day it is reached the tree
 * dies, and from then on its dead stages follow day by day ({@link TreeLife#deadStages}): acacias wither, stand as a
 * snag, get termite mud, shed their limbs and fall; baobabs slump into a heap that dries and is gone. Each change is
 * placed only where blocks differ, one stage at a time in sight, several only out of sight.
 */
public class Forest extends SavedData {
    private static final String DATA_NAME = AntColoniesMod.MODID + "_forest";
    /** Kinds of seedlings coming up by themselves (my picks): new land's umbrella 6, camel 3, and baobabs very rare. */
    static final String[] KINDS = {"umbrella_thorn", "camel_thorn", "baobab"};
    static final double[] SPROUT_WEIGHTS = {6, 3, 0.25};
    /** Seedlings come up in squares of land this wide (blocks). */
    static final int REGION = 64;
    /** How far round each player land gets its seedlings after rain. */
    static final int SPROUT_RANGE = 96;
    /** Per square of land and day of rain: how many places are tried, and the chance one that suits takes (my picks). */
    static final int SPROUT_TRIES = 2;
    static final double SPROUT_CHANCE = 0.35;
    /** No seedling comes up this close to another tree's foot or a colony's middle. */
    static final int SPROUT_SPACING = 4;
    static final int COLONY_SPACING = 8;
    /** How long a tree's work in a second may take before the rest waits for the next second. */
    private static final long BUDGET_NANOS = 4_000_000L;
    /** Rain days remembered (for land away a long time). */
    private static final int RAIN_MEMORY = 400;

    /** {@link Tree#died}: living. */
    public static final long ALIVE = Long.MIN_VALUE;
    /** {@link Tree#died}: dead in new land, the day worked out when it is first looked at. */
    public static final long DIED_UNKNOWN = Long.MIN_VALUE + 1;

    /** New land's trees, written in by world generation threads, taken into the book on the server thread. */
    private static final ConcurrentLinkedQueue<Queued> QUEUE = new ConcurrentLinkedQueue<>();

    private record Queued(ResourceKey<Level> dimension, Tree tree) {
    }

    /** One tree in the book. {@code placedAge} -1: nothing of it is in the world yet (a seedling not come up yet). */
    public static final class Tree {
        public final String kind;
        public final BlockPos foot;
        public final long seed;
        public TreeStage stage;
        public double age;
        public double placedAge;
        public long lastDay;
        /** Days lived since its shape in the world was last brought up to date (more than 1: placed out of sight). */
        int pendingDays;
        /** Days lived since it came up (step 5: it dies of old age when this reaches its lifespan). */
        public double lived;
        /** The day it died ({@link #ALIVE}: living; {@link #DIED_UNKNOWN}: dead in new land, the day not worked out yet). */
        public long died = ALIVE;
        /** What killed it: age, fire, lightning (empty while living). */
        public String cause = "";
        /** The stage whose shape is in the world. */
        public TreeStage placedStage;

        public Tree(String kind, BlockPos foot, long seed, double age, TreeStage stage, double placedAge, long lastDay) {
            this.kind = kind;
            this.foot = foot.immutable();
            this.seed = seed;
            this.age = age;
            this.stage = stage;
            this.placedAge = placedAge;
            this.lastDay = lastDay;
            this.placedStage = stage;
            this.lived = TreeLife.daysAt(Math.min(age, TreeShapes.ANCIENT));
            if (stage != TreeStage.LIVING) {
                died = DIED_UNKNOWN;
            }
        }

        public boolean living() {
            return stage == TreeStage.LIVING && died == ALIVE;
        }

        public boolean dead() {
            return died != ALIVE;
        }

        /** A fallen log: nothing more happens to it here (termites eating it come later). */
        boolean settled() {
            return stage == TreeStage.FALLEN && placedStage == TreeStage.FALLEN;
        }

        TreeKind treeKind() {
            return TreePlacer.kind(kind);
        }

        TreePlacer.Placed shape(double atAge) {
            return TreePlacer.placed(kind, seed, TreeLife.shapeAge(atAge), stage);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", kind);
            tag.putLong("foot", foot.asLong());
            tag.putLong("seed", seed);
            tag.putDouble("age", age);
            tag.putDouble("placed", placedAge);
            tag.putString("stage", stage.id);
            tag.putLong("day", lastDay);
            tag.putInt("pending", pendingDays);
            tag.putDouble("lived", lived);
            tag.putLong("died", died);
            tag.putString("cause", cause);
            tag.putString("placedStage", placedStage.id);
            return tag;
        }

        public static Tree load(CompoundTag tag) {
            Tree t = new Tree(tag.getString("kind"), BlockPos.of(tag.getLong("foot")), tag.getLong("seed"), tag.getDouble("age"),
                    stage(tag.getString("stage")), tag.getDouble("placed"), tag.getLong("day"));
            t.pendingDays = tag.getInt("pending");
            if (tag.contains("lived")) {
                t.lived = tag.getDouble("lived");
                t.died = tag.getLong("died");
                t.cause = tag.getString("cause");
                t.placedStage = stage(tag.getString("placedStage"));
            }
            return t;
        }
    }

    private final Long2ObjectOpenHashMap<Tree> trees = new Long2ObjectOpenHashMap<>();
    /** The trees by the chunk their foot is in. */
    private final Long2ObjectOpenHashMap<LongList> byChunk = new Long2ObjectOpenHashMap<>();
    /** The days it rained. */
    private final LongAVLTreeSet rainDays = new LongAVLTreeSet();
    /** For each square of land: the day its seedlings were last brought up to date. */
    private final Long2LongOpenHashMap regionDays = new Long2LongOpenHashMap();
    /** What happened, for the tests and the command: grown steps, deaths, seedlings come up. */
    public int grownSteps, deaths, sprouted;
    /** Grown trees that died (step 5), and dead stages placed. */
    public int killed, deadSteps;

    /** The game tests: shapes are worked out at once rather than in the background. */
    public static boolean shapesAtOnce;

    public static Forest get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Forest::new, Forest::load, null), DATA_NAME);
    }

    /** The in-game day (whole days since the world began, by the world's own clock). */
    public static long today(ServerLevel level) {
        return Math.floorDiv(level.getDayTime(), 24000L);
    }

    // ---------------------------------------------------------------- 4a, the book

    /** World generation placed a tree (any thread): it goes into the book on the next server second. */
    public static void queue(ServerLevel level, String kind, BlockPos foot, long seed, double age, TreeStage stage) {
        QUEUE.add(new Queued(level.dimension(), new Tree(kind, foot, seed, age, stage, age, Long.MIN_VALUE)));
    }

    /** Writes a tree in (unless one already stands on that foot). Returns the tree in the book there. */
    public Tree add(Tree tree) {
        Tree there = trees.get(tree.foot.asLong());
        if (there != null) {
            return there;
        }
        trees.put(tree.foot.asLong(), tree);
        byChunk.computeIfAbsent(ChunkPos.asLong(tree.foot), c -> new LongArrayList()).add(tree.foot.asLong());
        setDirty();
        return tree;
    }

    /** A new seedling at this foot, of this kind, with its own seed (planted by a player or come up by itself). */
    public Tree addSeedling(String kind, BlockPos foot, long seed, long day, boolean inWorld) {
        return add(new Tree(kind, foot, seed, 0, TreeStage.LIVING, inWorld ? 0 : -1, day));
    }

    public void remove(Tree tree) {
        trees.remove(tree.foot.asLong());
        LongList list = byChunk.get(ChunkPos.asLong(tree.foot));
        if (list != null) {
            list.rem(tree.foot.asLong());
            if (list.isEmpty()) {
                byChunk.remove(ChunkPos.asLong(tree.foot));
            }
        }
        setDirty();
    }

    public Tree at(BlockPos foot) {
        return trees.get(foot.asLong());
    }

    public int size() {
        return trees.size();
    }

    public List<Tree> all() {
        return new ArrayList<>(trees.values());
    }

    /** The trees whose foot is within {@code radius} blocks (sideways) of a place. */
    public List<Tree> near(BlockPos pos, int radius) {
        List<Tree> out = new ArrayList<>();
        int c = (radius >> 4) + 1;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int x = cx - c; x <= cx + c; x++) {
            for (int z = cz - c; z <= cz + c; z++) {
                LongList list = byChunk.get(ChunkPos.asLong(x, z));
                if (list == null) {
                    continue;
                }
                for (long k : list) {
                    Tree t = trees.get(k);
                    if (t != null && Math.abs(t.foot.getX() - pos.getX()) <= radius && Math.abs(t.foot.getZ() - pos.getZ()) <= radius) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    /** Forgets every tree whose foot is inside a box (a game test that is done). */
    public void forgetInside(net.minecraft.world.phys.AABB box) {
        for (Tree t : all()) {
            if (box.contains(Vec3.atCenterOf(t.foot))) {
                remove(t);
            }
        }
    }

    // ---------------------------------------------------------------- the server's clock

    /** Called every tick: rain is written down, and once a second the trees due are brought up to date. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 7) {
            return;
        }
        Forest forest = get(level);
        for (Queued q; (q = QUEUE.peek()) != null && q.dimension() == level.dimension(); ) {
            QUEUE.poll();
            q.tree().lastDay = today(level);
            forest.add(q.tree());
        }
        boolean timed = Perf.begin(Perf.Part.TREES);
        try {
            forest.second(level, today(level));
        } finally {
            if (timed) {
                Perf.end();
            }
        }
    }

    void second(ServerLevel level, long today) {
        if (level.isRaining() && rainDays.add(today)) {
            while (rainDays.size() > RAIN_MEMORY) {
                rainDays.remove(rainDays.firstLong());
            }
            setDirty();
        }
        long start = System.nanoTime();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                sproutAround(level, player.blockPosition(), today);
            }
        }
        for (Tree t : all()) {
            if (System.nanoTime() - start > BUDGET_NANOS) {
                break; // the rest next second
            }
            if (!t.settled() && (t.lastDay < today || t.placedAge != TreeLife.shapeAge(t.age) || t.placedStage != t.stage)) {
                update(level, t, today);
            }
        }
    }

    // ---------------------------------------------------------------- 4b, 4d, 4e: growing and dying

    /**
     * Brings one tree up to today, if its land is loaded: every day it missed is lived (growing, maybe dying), then its
     * shape in the world is brought up to date (one day's step even in sight; more than one only out of sight). Returns
     * false if its land is not loaded (it waits).
     */
    public boolean update(ServerLevel level, Tree t, long today) {
        if (!level.hasChunkAt(t.foot)) {
            return false;
        }
        TreePlacer.Placed now = t.placedAge >= 0 ? soon(t, t.placedAge, t.placedStage) : null;
        if (t.placedAge >= 0 && now == null) {
            return false; // its shape is being worked out
        }
        if (now != null && !loaded(level, now.box(t.foot))) {
            return false;
        }
        if (now != null && cutThrough(level, t, now)) {
            remove(t); // its trunk was cut through at the foot: the record ends
            return true;
        }
        if (t.lastDay > today) {
            t.lastDay = today; // the clock was set back
        }
        if (t.died == DIED_UNKNOWN) {
            // A dead tree of new land: it died some days ago, somewhere inside the stage it stands in.
            double roll = TreeLife.dice(t.seed, -9).nextDouble();
            t.died = today - TreeLife.daysDeadIn(t.treeKind(), t.seed, t.stage, roll);
        }
        while (t.lastDay < today) {
            t.lastDay++;
            if (t.dead()) {
                continue; // its stage comes from the days since it died, below
            }
            t.lived++;
            if (t.age >= TreeLife.YOUNG_END && t.lived >= TreeLife.lifespan(t.treeKind(), t.seed)) {
                kill(t, t.lastDay, "age");
                continue;
            }
            Random dice = TreeLife.dice(t.seed, t.lastDay);
            double next = TreeLife.grow(t.age, TreeLife.speed(t.seed));
            if (dice.nextDouble() < TreeLife.deathChance(t.age, next)) {
                die(level, t, now);
                return true;
            }
            t.age = next;
            t.pendingDays++;
            grownSteps++;
        }
        int stageSteps = 0;
        if (t.dead()) {
            t.stage = TreeLife.deadStage(t.treeKind(), t.seed, today - t.died);
            if (t.stage == null) {
                // A baobab's dried heap is gone.
                if (now != null) {
                    TreePlacer.remove(level, now, t.foot, Block.UPDATE_ALL);
                }
                remove(t);
                return true;
            }
            stageSteps = Math.abs(TreeLife.stageIndex(t.treeKind(), t.stage) - TreeLife.stageIndex(t.treeKind(), t.placedStage));
        }
        setDirty();
        if (TreeLife.shapeAge(t.age) == t.placedAge && t.stage == t.placedStage) {
            t.pendingDays = 0;
            return true;
        }
        TreePlacer.Placed next = soon(t, t.age, t.stage);
        boolean jump = t.dead() ? stageSteps > 1 : t.pendingDays > 1;
        if (next == null || !loaded(level, next.box(t.foot)) || jump && !outOfSight(level, t, next)) {
            return false;
        }
        place(level, t, now, next);
        return true;
    }

    /** A grown tree dies (step 5): its dead stages start (the day it dies its leaves wither on the living shape). */
    public void kill(Tree t, long day, String cause) {
        t.died = day;
        t.cause = cause;
        killed++;
        setDirty();
    }

    /** A tree's shape at an age and stage, if worked out (or being worked out in the background: null). */
    private static TreePlacer.Placed soon(Tree t, double age, TreeStage stage) {
        return shapesAtOnce ? TreePlacer.placed(t.kind, t.seed, TreeLife.shapeAge(age), stage)
                : TreePlacer.placedSoon(t.kind, t.seed, TreeLife.shapeAge(age), stage);
    }

    /** Brings the tree's shape in the world from what is there to its age now. */
    private void place(ServerLevel level, Tree t, TreePlacer.Placed now, TreePlacer.Placed next) {
        List<BlockPos> shed = new ArrayList<>();
        TreePlacer.grow(level, now, next, t.foot, Block.UPDATE_ALL, shed);
        if (t.placedStage != t.stage) {
            deadSteps++;
        }
        t.placedAge = TreeLife.shapeAge(t.age);
        t.placedStage = t.stage;
        t.pendingDays = 0;
        setDirty();
        if (!shed.isEmpty() && t.living()) {
            // The twigs it shed (self-pruning) fall to the ground under them, one fallen branch (my pick).
            BlockPos from = shed.get(TreeLife.dice(t.seed, t.lastDay + 7).nextInt(shed.size()));
            dropBranch(level, from, TreeLife.dice(t.seed, t.lastDay + 13));
        }
    }

    /** A little tree dies: a seedling dries and vanishes, a young one leaves a fallen branch. */
    private void die(ServerLevel level, Tree t, TreePlacer.Placed now) {
        if (now != null) {
            TreePlacer.remove(level, now, t.foot, Block.UPDATE_ALL);
            if (t.placedAge >= TreeLife.YOUNG) {
                dropBranch(level, t.foot.above(), TreeLife.dice(t.seed, t.lastDay + 3));
            }
        }
        deaths++;
        remove(t);
    }

    /** A fallen branch on the ground under this place (straight down to the first solid ground), or close by. */
    static boolean dropBranch(ServerLevel level, BlockPos from, Random dice) {
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(dice.nextLong());
        int[][] around = {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {2, 1}, {-1, 2}, {-2, -1}, {1, -2}, {3, 0}, {0, 3}, {-3, 0}, {0, -3}};
        for (int[] o : around) {
            BlockPos.MutableBlockPos pos = from.offset(o[0], 0, o[1]).mutable();
            for (int i = 0; i < 64 && TreePlacer.isOpen(level.getBlockState(pos.below())) && pos.getY() > level.getMinBuildHeight(); i++) {
                pos.move(Direction.DOWN);
            }
            if (TreePlacer.isOpen(level.getBlockState(pos)) && FallenBranchFeature.placeBranch(level, pos.immutable(), random, Block.UPDATE_ALL)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a tree's trunk has been cut through at its foot (fewer than half its foot blocks still its own). */
    private static boolean cutThrough(ServerLevel level, Tree t, TreePlacer.Placed now) {
        int foot = 0, there = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < now.size(); i++) {
            boolean base = now.ys()[i] == 0 && (now.holds()[i] || now.size() == 1);
            if (!base) {
                continue;
            }
            foot++;
            pos.set(t.foot.getX() + now.xs()[i], t.foot.getY(), t.foot.getZ() + now.zs()[i]);
            if (level.getBlockState(pos).is(now.states()[i].getBlock())) {
                there++;
            }
        }
        return foot > 0 && there * 2 < foot;
    }

    private static boolean loaded(ServerLevel level, BoundingBox box) {
        return level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ());
    }

    /** Whether no player can see the tree's foot, middle or top (a catch-up of many days waits until then). */
    private static boolean outOfSight(ServerLevel level, Tree t, TreePlacer.Placed next) {
        List<Vec3> eyes = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.blockPosition().closerThan(t.foot, Population.SIGHT + 48)) {
                eyes.add(player.getEyePosition());
            }
        }
        if (eyes.isEmpty()) {
            return true;
        }
        int top = Math.max(1, next.maxY());
        for (int y : new int[] {0, top / 2, top}) {
            if (!Population.outOfSight(level, Vec3.atCenterOf(t.foot.above(y)), eyes)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Bone meal on a seedling or a young tree (any of its blocks): it grows one day's step at once, never past young.
     * Returns whether it grew.
     */
    public boolean boneMeal(ServerLevel level, BlockPos clicked) {
        for (Tree t : near(clicked, 8)) {
            if (!t.living() || t.age >= TreeLife.YOUNG_END || t.placedAge < 0) {
                continue;
            }
            TreePlacer.Placed now = t.shape(t.placedAge);
            if (!now.box(t.foot).isInside(clicked) || !isPart(now, t.foot, clicked, level.getBlockState(clicked))) {
                continue;
            }
            update(level, t, today(level)); // any days owed first
            if (trees.get(t.foot.asLong()) != t) {
                return true; // it had died meanwhile
            }
            t.age = Math.min(TreeLife.YOUNG_END, TreeLife.grow(t.age, TreeLife.speed(t.seed)));
            grownSteps++;
            TreePlacer.Placed next = t.shape(t.age);
            if (loaded(level, next.box(t.foot))) {
                place(level, t, t.shape(t.placedAge), next);
            }
            level.levelEvent(1505, clicked, 15); // the bone meal sparkles
            return true;
        }
        return false;
    }

    /** Whether the block at {@code pos} is a block of this tree's shape (still its own). */
    private static boolean isPart(TreePlacer.Placed shape, BlockPos foot, BlockPos pos, BlockState there) {
        int x = pos.getX() - foot.getX(), y = pos.getY() - foot.getY(), z = pos.getZ() - foot.getZ();
        for (int i = 0; i < shape.size(); i++) {
            if (shape.xs()[i] == x && shape.ys()[i] == y && shape.zs()[i] == z) {
                return there.is(shape.states()[i].getBlock());
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 4c: seedlings after rain

    /** The squares of land round a player get the seedlings of every day of rain since they last did. */
    private void sproutAround(ServerLevel level, BlockPos at, long today) {
        int r0x = Math.floorDiv(at.getX() - SPROUT_RANGE, REGION), r1x = Math.floorDiv(at.getX() + SPROUT_RANGE, REGION);
        int r0z = Math.floorDiv(at.getZ() - SPROUT_RANGE, REGION), r1z = Math.floorDiv(at.getZ() + SPROUT_RANGE, REGION);
        for (int rx = r0x; rx <= r1x; rx++) {
            for (int rz = r0z; rz <= r1z; rz++) {
                long key = ChunkPos.asLong(rx, rz);
                long last = regionDays.getOrDefault(key, Long.MIN_VALUE);
                if (last >= today) {
                    continue;
                }
                int x0 = rx * REGION, z0 = rz * REGION;
                if (!level.hasChunksAt(x0, z0, x0 + REGION - 1, z0 + REGION - 1)) {
                    continue;
                }
                regionDays.put(key, today);
                setDirty();
                if (last == Long.MIN_VALUE) {
                    continue; // land seen for the first time: new land's own young trees are its seedlings
                }
                sproutIn(level, x0, z0, last, today);
            }
        }
    }

    /**
     * Seedlings for every day of rain from {@code since} (included) to {@code today} (not included) in one square of
     * land: they come up the morning after the rain. Public for the tests.
     */
    public int sproutIn(ServerLevel level, int x0, int z0, long since, long today) {
        int count = 0;
        for (long day : rainDays.subSet(since, today)) {
            Random dice = new Random(level.getSeed() ^ ChunkPos.asLong(x0, z0) * 31 ^ day * 0x5DEECE66DL);
            for (int i = 0; i < SPROUT_TRIES; i++) {
                int x = x0 + dice.nextInt(REGION), z = z0 + dice.nextInt(REGION);
                boolean takes = dice.nextDouble() < SPROUT_CHANCE;
                String kind = pickKind(dice);
                long seed = dice.nextLong() & 0xFFFFFFFFL;
                BlockPos foot = sproutSite(level, x, z);
                if (takes && foot != null) {
                    Tree t = addSeedling(kind, foot, seed, day + 1, false);
                    sprouted++;
                    count++;
                    update(level, t, today); // it lives the days since it came up, and comes up (or has died)
                }
            }
        }
        return count;
    }

    static String pickKind(Random dice) {
        double total = 0;
        for (double w : SPROUT_WEIGHTS) {
            total += w;
        }
        double roll = dice.nextDouble() * total;
        for (int i = 0; i < KINDS.length; i++) {
            roll -= SPROUT_WEIGHTS[i];
            if (roll < 0) {
                return KINDS[i];
            }
        }
        return KINDS[0];
    }

    /**
     * Where a seedling may come up in this column: open savanna ground (grass or bare soil, nothing over it, no crown
     * above), not too near another tree or a colony. Null if not.
     */
    public BlockPos sproutSite(ServerLevel level, int x, int z) {
        // The ground: down from the top past leaves, open air and plants (and a game test arena's barrier ceiling).
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        boolean underCrown = false;
        while (at.getY() > level.getMinBuildHeight()) {
            BlockState below = level.getBlockState(at.below());
            if (below.is(BlockTags.LEAVES)) {
                underCrown = true;
            } else if (!below.is(net.minecraft.world.level.block.Blocks.BARRIER) && !TreePlacer.isOpen(below)) {
                break;
            }
            at.move(Direction.DOWN);
        }
        BlockPos foot = at.immutable();
        if (underCrown || !level.getBiome(foot).is(BiomeTags.IS_SAVANNA)) {
            return null; // under a crown (seedlings there are shaded and outcompeted), or not savanna
        }
        BlockState ground = level.getBlockState(foot.below());
        if (!ground.is(BlockTags.DIRT) || !TreePlacer.isOpen(level.getBlockState(foot)) || !level.getFluidState(foot).isEmpty()) {
            return null;
        }
        if (!near(foot, SPROUT_SPACING).isEmpty()) {
            return null;
        }
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (colony.getCenter().closerThan(foot, COLONY_SPACING)) {
                return null;
            }
        }
        return foot;
    }

    /** Writes down that it rained on this day (the tests). */
    public void rainedOn(long day) {
        rainDays.add(day);
        setDirty();
    }

    // ---------------------------------------------------------------- saving

    private static TreeStage stage(String id) {
        for (TreeStage s : TreeStage.values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return TreeStage.LIVING;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Tree t : trees.values()) {
            list.add(t.save());
        }
        tag.put("trees", list);
        tag.putLongArray("rain", rainDays.toLongArray());
        long[] regions = new long[regionDays.size() * 2];
        int i = 0;
        for (Long2LongOpenHashMap.Entry e : regionDays.long2LongEntrySet()) {
            regions[i++] = e.getLongKey();
            regions[i++] = e.getLongValue();
        }
        tag.putLongArray("regions", regions);
        return tag;
    }

    private static Forest load(CompoundTag tag, HolderLookup.Provider registries) {
        Forest forest = new Forest();
        for (Tag t : tag.getList("trees", Tag.TAG_COMPOUND)) {
            forest.add(Tree.load((CompoundTag) t));
        }
        for (long day : tag.getLongArray("rain")) {
            forest.rainDays.add(day);
        }
        long[] regions = tag.getLongArray("regions");
        for (int i = 0; i + 1 < regions.length; i += 2) {
            forest.regionDays.put(regions[i], regions[i + 1]);
        }
        return forest;
    }
}
