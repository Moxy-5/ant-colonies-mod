package com.antcolonies.savanna.tree;

import com.antcolonies.colony.LandReady;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.TreeBranchBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.Population;
import com.antcolonies.debug.Perf;
import com.antcolonies.savanna.registry.TreeBlocks;
import com.antcolonies.savanna.worldgen.FallenBranchFeature;
import com.antcolonies.savanna.worldgen.TreePlacer;

import com.antcolonies.savanna.species.SavannaNumbers;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
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
    private static final String DATA_NAME = SavannaMod.MODID + "_forest";
    /** Kinds of seedlings coming up by themselves (my picks): new land's umbrella 6, camel 3, and baobabs very rare. */
    static final String[] KINDS = {"umbrella_thorn", "camel_thorn", "baobab"};
    static final double[] SPROUT_WEIGHTS = {6, 3, 0.25};
    /** Seedlings come up in squares of land this wide (blocks). */
    static final int REGION = 64;
    /** How far round each player land gets its seedlings after rain. */
    static final int SPROUT_RANGE = 96;
    /** Per square of land and day of rain: how many places are tried, and the chance one that suits takes (my picks;
     * doubled-ish for the dead wood the raids need, 2026-10-25: was 2 and 0.35). */
    static final int SPROUT_TRIES = 4;
    static final double SPROUT_CHANCE = 0.6;
    /** No seedling comes up this close to another tree's foot or a colony's middle. */
    static final int SPROUT_SPACING = 4;
    /**
     * How many living trees a square of land holds before no more seedlings come up in it: a grown tree counts
     * {@link #GROWN_ROOM}, a seedling or young one 1 (the user's call, 2026-11-26, review R7-13; my picks: new land's own
     * 1 big tree and about 3 young ones fill a square, so the savanna stays as open as the world maker lays it).
     */
    static final int SQUARE_ROOM = 6, GROWN_ROOM = 3;
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

    /** {@code found}: read from a loaded chunk's own record of its trees, not put down just now (review R7-15). */
    private record Queued(ResourceKey<Level> dimension, Tree tree, boolean found) {
    }

    /** Takes this world's trees of new land from the waiting list into its book. True if there were any. */
    private static boolean takeIn(ServerLevel level, Forest forest) {
        boolean any = false;
        for (java.util.Iterator<Queued> it = QUEUE.iterator(); it.hasNext(); ) {
            Queued q = it.next();
            if (q.dimension() != level.dimension()) {
                continue;
            }
            it.remove();
            if (q.found() && (forest.at(q.tree().foot) != null || !LandReady.at(level, q.tree().foot) || level.getBlockState(q.tree().foot).isAir())) {
                continue; // the book has it, or nothing stands there any more (a tree whose record ended)
            }
            q.tree().lastDay = today(level);
            HeartRot.newLand(q.tree(), today(level));
            forest.add(q.tree());
            any = true;
        }
        return any;
    }

    /**
     * A world closes (after its last save): the trees of new land made since the last look go into its book and the book
     * is written once more (review R7-15: a tree whose land was made in the last second before the player quit was never
     * in the book, and stood frozen for good).
     */
    public static void closing(ServerLevel level) {
        if (takeIn(level, get(level))) {
            level.getDataStorage().save();
        }
    }

    /** The server has stopped: no tree of this world is kept for the next one opened. */
    public static void forgetAll() {
        QUEUE.clear();
        // The worked-out shapes go too (review R7-7: they were kept, tens of MB, into the next world opened).
        TreePlacer.forgetShapes();
        com.antcolonies.savanna.worldgen.GiantTreePiece.forgetShapes();
        com.antcolonies.savanna.worldgen.FloodplainTreePiece.forgetShapes();
    }

    /** The game tests: how many trees of new land wait to be taken into a book. */
    public static int waitingForTests() {
        return QUEUE.size();
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
        /** How far along its stage's slow change a dead tree is (step 5b, {@link TreeLife#deadStep}), and what is in the world. */
        public int step, placedStep;
        /**
         * Step 5c: a dead acacia due to fall has come down (in a storm, or after waiting {@link TreeLife#RAIN_WAIT} days);
         * until then it stands shed. {@code toppling}: it is being drawn falling (its standing wood already taken away).
         */
        public boolean fell, toppling;
        /**
         * Step 5f: a fungus-growing termite colony's reach takes in its foot (once past young): its lower trunk is eaten
         * hollow, it ages twice as fast, and fire and lightning kill it more surely. {@code placedTermites}: whether the
         * shape in the world is the hollowed one.
         */
        public boolean termites, placedTermites;
        /**
         * Dead wood for the raids, part 4 ({@link HeartRot}): the day heart rot got into its trunk ({@link HeartRot#NONE}:
         * sound); {@code placedRot}: whether the shape in the world is the rot-hollowed one; {@code brackets}: how many
         * bracket fungi have been put on its bark; {@code woundShown}: whether the scar where the rot got in is on its bark.
         */
        public long rotFrom = HeartRot.NONE;
        public boolean placedRot, woundShown;
        public int brackets;
        /** How many columns of a rot-killed log have crumbled to pulp ({@link HeartRot#crumbleColumn}). */
        public int crumbled;

        public Tree(String kind, BlockPos foot, long seed, double age, TreeStage stage, double placedAge, long lastDay) {
            this.kind = kind;
            this.foot = foot.immutable();
            this.seed = seed;
            this.age = age;
            this.stage = stage;
            this.placedAge = placedAge;
            this.lastDay = lastDay;
            this.placedStage = stage;
            this.lived = TreeLife.livedAtStart(treeKind(), seed, age);
            if (stage != TreeStage.LIVING) {
                died = DIED_UNKNOWN;
                step = placedStep = TreeLife.fullStep(stage); // new land shows the stage's finished shape
                fell = stage == TreeStage.FALLEN;
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

        /** Whether the shape in the world is the one it should have now. */
        boolean upToDate() {
            return placedAge == TreeLife.shapeAge(age) && placedStage == stage && placedStep == step && placedTermites == termites
                    && placedRot == HeartRot.hollow(this);
        }

        TreeKind treeKind() {
            return TreePlacer.kind(kind);
        }

        TreePlacer.Placed shape(double atAge) {
            return TreePlacer.placed(shapeKind(termites, HeartRot.hollow(this)), seed, TreeLife.shapeAge(atAge), stage);
        }

        /** The name its shapes are kept under: its kind, hollowed by termites (step 5f) and by heart rot (part 4) or not. */
        String shapeKind(boolean hollowed, boolean rotted) {
            return kind + (hollowed ? TreePlacer.HOLLOWED : "") + (rotted ? TreePlacer.ROTTED : "");
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
            tag.putInt("step", step);
            tag.putInt("placedStep", placedStep);
            tag.putBoolean("fell", fell);
            tag.putBoolean("toppling", toppling);
            tag.putBoolean("termites", termites);
            tag.putBoolean("placedTermites", placedTermites);
            tag.putLong("rotFrom", rotFrom);
            tag.putBoolean("placedRot", placedRot);
            tag.putInt("brackets", brackets);
            tag.putBoolean("woundShown", woundShown);
            tag.putInt("crumbled", crumbled);
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
                t.step = tag.getInt("step");
                t.placedStep = tag.getInt("placedStep");
                t.fell = tag.contains("fell") ? tag.getBoolean("fell") : t.placedStage == TreeStage.FALLEN;
                t.toppling = tag.getBoolean("toppling");
                t.termites = tag.getBoolean("termites");
                t.placedTermites = tag.getBoolean("placedTermites");
            }
            if (tag.contains("rotFrom")) {
                t.rotFrom = tag.getLong("rotFrom");
                t.placedRot = tag.getBoolean("placedRot");
                t.brackets = tag.getInt("brackets");
                t.woundShown = tag.getBoolean("woundShown");
                t.crumbled = tag.getInt("crumbled");
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
    /** Step 6b: dead branches the grown trees have dropped (for the stopwatch and the tests). */
    public int shed;
    /** Part 4: pulp heaps spilled where hollow trees snapped (for the tests). */
    public int pulpHeaps;

    /** The trees being drawn falling right now (step 5c), by their foot. Not saved: one cut off by a stop is placed at once. */
    private final Long2ObjectOpenHashMap<TreeFall> falls = new Long2ObjectOpenHashMap<>();
    /** Step 5d: the last day each tree's trunk was in a fire (its death rolled once a day), by its foot. Not saved. */
    private final Long2LongOpenHashMap fireDays = new Long2LongOpenHashMap();
    /** Step 5d: hollow trunks killed by fire, smouldering: where the smoke comes out, and until when (in days). Not saved. */
    private final Long2ObjectOpenHashMap<Smoulder> smoulders = new Long2ObjectOpenHashMap<>();

    private record Smoulder(BlockPos top, double until) {
    }

    /** Where the once-a-second walk goes on next second, when its time ran out (review R7-2). Not saved. */
    private int walkFrom;
    /**
     * Living hollow trees a storm is breaking, waiting for their shapes to be worked out in the background (review R7-4:
     * the three shapes were worked out on the server's own thread, a hitch of tens of ms). Not saved: one cut off by a
     * stop was simply not broken.
     */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet snapping = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    /** Fallen logs whose "is anything of it left" look is due and waits for its shape or its land (review R7-19). Not saved. */
    /** The trees lightning has struck in this thunderstorm: each draws one strike a storm (review R7-12; not saved). */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet struckThisStorm = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    /** The trees a block was broken beside: looked at this second for a trunk cut through (review R7-31; not saved). */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet cutLooks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet logLooks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    /** How long a hollow trunk killed by fire smoulders (my pick: half a day). */
    static final double SMOULDER_DAYS = 0.5;

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
        QUEUE.add(new Queued(level.dimension(), new Tree(kind, foot, seed, age, stage, age, Long.MIN_VALUE), false));
    }

    /**
     * A chunk came into the game: each giant tree that world making put in it and the book does not know goes on the
     * waiting list, and into the book if something still stands on its foot (review R7-15: the waiting list lives in
     * memory only, so a crash lost every tree made since the last autosave, and such a tree stood frozen for good; this
     * also mends worlds already hit). The chunk keeps its trees' kind, seed, age and foot itself, with its structures.
     */
    public static void chunkLoaded(ServerLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk) {
        if (chunk.getAllStarts().isEmpty()) {
            return;
        }
        Forest forest = get(level);
        for (net.minecraft.world.level.levelgen.structure.StructureStart start : chunk.getAllStarts().values()) {
            for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                if (piece instanceof com.antcolonies.savanna.worldgen.GiantTreePiece tree && !com.antcolonies.savanna.worldgen.GiantTreePiece.fallenYoung(tree.pick())
                        && forest.at(tree.foot()) == null) {
                    found(level, tree.pick().kind(), tree.foot(), tree.pick().seed(), tree.pick().age(), tree.pick().stage());
                }
            }
        }
    }

    /** A tree read from its chunk's record ({@link #chunkLoaded}; public for the tests). */
    public static void found(ServerLevel level, String kind, BlockPos foot, long seed, double age, TreeStage stage) {
        QUEUE.add(new Queued(level.dimension(), new Tree(kind, foot, seed, age, stage, age, Long.MIN_VALUE), true));
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
        snapping.remove(tree.foot.asLong());
        logLooks.remove(tree.foot.asLong());
        cutLooks.remove(tree.foot.asLong());
        struckThisStorm.remove(tree.foot.asLong());
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
                smoulders.remove(t.foot.asLong());
                fireDays.remove(t.foot.asLong());
            }
        }
    }

    // ---------------------------------------------------------------- the server's clock

    /** Called every tick: rain is written down, and once a second the trees due are brought up to date. */
    public static void tick(ServerLevel level) {
        Forest forest = get(level);
        if (!forest.falls.isEmpty()) {
            boolean timed = Perf.begin(Perf.Part.TREES);
            try {
                forest.falls.values().removeIf(TreeFall::tick);
            } finally {
                if (timed) {
                    Perf.end();
                }
            }
        }
        if (level.getGameTime() % 20 != 7) {
            return;
        }
        takeIn(level, forest);
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
        if (!level.isThundering()) {
            struckThisStorm.clear(); // the storm is over
        }
        long start = System.nanoTime();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                sproutAround(level, player.blockPosition(), today);
            }
        }
        smoulder(level);
        // Only the trees of loaded land, chunk by chunk, going on next second where the time ran out (review R7-2: the
        // walk took every tree ever met, storms acted on trees far away, and it always started from the same end).
        long[] chunks = byChunk.keySet().toLongArray();
        long daySecond = Math.floorMod(level.getDayTime(), 24000L) / 20;
        for (int i = 0; i < chunks.length; i++) {
            int at = (walkFrom + i) % chunks.length;
            if (System.nanoTime() - start > BUDGET_NANOS) {
                walkFrom = at; // the rest next second, from here
                break;
            }
            LongList inChunk = byChunk.get(chunks[at]);
            if (inChunk == null || !LandReady.chunk(level, ChunkPos.getX(chunks[at]), ChunkPos.getZ(chunks[at]))) {
                continue;
            }
            for (long foot : inChunk.toLongArray()) {
                Tree t = trees.get(foot);
                if (t != null) {
                    second(level, t, today, daySecond);
                }
            }
        }
    }

    /** One loaded tree's second. */
    private void second(ServerLevel level, Tree t, long today, long daySecond) {
        {
            if (t.settled()) {
                if (t.died == DIED_UNKNOWN) {
                    // A log of new land: just fallen (so it weathers away as long after as any other log).
                    t.died = today - TreeLife.daysDeadIn(t.treeKind(), t.seed, TreeStage.FALLEN, TreeLife.dice(t.seed, -9).nextDouble());
                    setDirty();
                }
                // A log the rot killed crumbles soon; a sound one weathers away in the end (review R7-13).
                if (t.placedAge >= 0 && (HeartRot.diedOfRot(t) || time(level) >= HeartRot.crumbleFrom(t))) {
                    crumble(level, t);
                } else if (t.placedAge >= 0) {
                    lookForLog(level, t, daySecond);
                }
                return;
            }
            if (t.living() && t.placedAge >= 0 && BranchShedding.stormShakes(level, t.age, t.foot)) {
                // Step 6b: a thunderstorm shakes a dead branch out of a grown tree's crown.
                TreePlacer.Placed now = soon(t, t.placedAge, t.placedStage, t.placedStep, t.placedTermites, t.placedRot);
                if (now != null && loaded(level, now.box(t.foot))
                        && BranchShedding.shed(level, t.foot, now, new Random(level.getRandom().nextLong()), true)) {
                    shed++;
                    if (HeartRot.wound(t, today, HeartRot.SHED_CHANCE, new Random(level.getRandom().nextLong()))) {
                        setDirty(); // the rot got in where the limb broke
                    }
                }
            }
            if (snapping.contains(t.foot.asLong()) ? t.living() : HeartRot.stormKills(level, t)) {
                if (!snapShapesReady(t)) {
                    snapping.add(t.foot.asLong()); // its shapes are being worked out: it breaks the second they are ready
                } else {
                    snapping.remove(t.foot.asLong());
                    kill(t, today, "storm"); // part 4: the storm breaks the hollow tree
                    if (!fellNow(level, t)) {
                        t.fell = true; // snapped at the hollow, its top comes down now (drawn falling where it can be)
                    }
                }
            }
            if (t.dead() && !t.fell && t.died != DIED_UNKNOWN && stormFells(level, t)) {
                t.fell = true; // the storm brings it down
                setDirty();
            }
            if (cutLooks.remove(t.foot.asLong()) || t.lastDay < today || !t.upToDate() || t.dead() && t.died != DIED_UNKNOWN && changesNow(t, time(level))) {
                update(level, t, today);
            }
        }
    }

    /**
     * A block was broken here: the trees whose foot is close are looked at within a second for a trunk cut through
     * (review R7-31; before, a cut tree was only found at the next dawn). Fire, blasts and termites are found at dawn.
     */
    public void brokenAt(BlockPos pos) {
        for (Tree t : near(pos, 8)) {
            if (Math.abs(pos.getY() - t.foot.getY()) <= 1 && !t.settled()) {
                cutLooks.add(t.foot.asLong());
            }
        }
    }

    /** Whether the three shapes a storm-broken tree needs ({@link #fellNow}) are worked out (asked for if not). */
    private static boolean snapShapesReady(Tree t) {
        if (shapesAtOnce) {
            return true;
        }
        boolean rot = HeartRot.hollow(t);
        String kind = t.shapeKind(t.termites, rot);
        boolean now = TreePlacer.placedSoon(t.shapeKind(t.placedTermites, t.placedRot), t.seed, t.placedAge, t.placedStage, t.placedStep) != null;
        boolean shed = TreePlacer.placedSoon(kind, t.seed, TreeLife.shapeAge(t.age), TreeStage.SHED, TreeLife.fullStep(TreeStage.SHED)) != null;
        boolean lying = TreePlacer.placedSoon(kind, t.seed, TreeLife.shapeAge(t.age), TreeStage.FALLEN, TreeLife.fullStep(TreeStage.FALLEN)) != null;
        return now && shed && lying;
    }

    /**
     * A fallen log is looked at once a day, each log at its own second: when no block of its trunk and limbs is left (termites ate it,
     * the player took it, it burnt) its record ends (review R7-19: the record stayed for ever, keeping seedlings 4
     * blocks from a foot no longer there). A log the rot killed ends by crumbling instead ({@link #crumble}).
     * {@code lookForLogNow}: the look itself, for the tests.
     */
    public void lookForLogNow(ServerLevel level, Tree t) {
        logLooks.add(t.foot.asLong());
        lookForLog(level, t, -1);
    }

    private void lookForLog(ServerLevel level, Tree t, long daySecond) {
        long key = t.foot.asLong();
        if (daySecond == Math.floorMod(Long.hashCode(key * 0x9E3779B97F4A7C15L), 1200)) {
            logLooks.add(key);
        }
        if (!logLooks.contains(key)) {
            return;
        }
        TreePlacer.Placed lying = soon(t, t.placedAge, t.placedStage, t.placedStep, t.placedTermites, t.placedRot);
        if (lying == null) {
            return; // its shape is being worked out: next second
        }
        logLooks.remove(key);
        if (!loaded(level, lying.box(t.foot))) {
            return; // tomorrow
        }
        TreePlacer.Placed laid = TreePlacer.rest(lying, t.foot, TreePlacer.ground(level, t.foot));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < laid.size(); i++) {
            pos.set(t.foot.getX() + laid.xs()[i], t.foot.getY() + laid.ys()[i], t.foot.getZ() + laid.zs()[i]);
            // (its trunk and limbs count; the twigs and loose pieces lying round it do not keep a record)
            if (laid.states()[i].getBlock() instanceof RotatedPillarBlock && level.getBlockState(pos).is(laid.states()[i].getBlock())) {
                return; // something of it still lies there
            }
        }
        remove(t);
    }

    // ---------------------------------------------------------------- 4b, 4d, 4e: growing and dying

    /**
     * Brings one tree up to today, if its land is loaded: every day it missed is lived (growing, maybe dying), then its
     * shape in the world is brought up to date (one day's step even in sight; more than one only out of sight). Returns
     * false if its land is not loaded (it waits).
     */
    public boolean update(ServerLevel level, Tree t, long today) {
        return update(level, t, today, today(level) == today ? time(level) : today);
    }

    /** The game's clock in days, with the part of today gone by (a dead tree's slow changes go by it). */
    public static double time(ServerLevel level) {
        return level.getDayTime() / 24000.0;
    }

    /** Whether a dead tree's stage or slow change has moved on since it was last brought up to date. */
    private static boolean changesNow(Tree t, double time) {
        TreeStage stage = deadStageNow(t, time - t.died);
        return stage != t.stage || stage != null && deadStepNow(t, stage, time - t.died) != t.step;
    }

    /** A dead tree's stage so many days after its death: one due to fall stands shed until it has come down (step 5c). */
    static TreeStage deadStageNow(Tree t, double daysDead) {
        TreeStage stage = TreeLife.deadStage(t.treeKind(), t.seed, daysDead);
        if (t.fell) {
            return TreeStage.FALLEN; // once down it stays down
        }
        if (stage == TreeStage.FALLEN) {
            if (daysDead < TreeLife.stageFrom(t.treeKind(), t.seed, TreeStage.FALLEN) + TreeLife.RAIN_WAIT) {
                return TreeStage.SHED;
            }
            t.fell = true; // it waited long enough: it comes down on a still day
        }
        return stage;
    }

    private static int deadStepNow(Tree t, TreeStage stage, double daysDead) {
        if (stage == TreeStage.FALLEN) {
            return 0;
        }
        return stage == TreeStage.SHED && TreeLife.deadStage(t.treeKind(), t.seed, daysDead) == TreeStage.FALLEN ? TreeLife.fullStep(stage)
                : TreeLife.deadStep(t.treeKind(), t.seed, daysDead);
    }

    /**
     * Step 5c: a dead acacia due to fall comes down in a storm: each second of rain over it a small chance, more in a
     * thunderstorm (my picks: about a minute of rain, 20 seconds of thunder).
     */
    private static boolean stormFells(ServerLevel level, Tree t) {
        if (!level.isRaining() || !level.getBiome(t.foot).value().hasPrecipitation()
                || TreeLife.deadStage(t.treeKind(), t.seed, time(level) - t.died) != TreeStage.FALLEN) {
            return false;
        }
        return level.getRandom().nextInt(level.isThundering() ? 20 : 60) == 0;
    }

    /** As {@link #update(ServerLevel, Tree, long)}, with the clock's time in days (with the part of the day). */
    public boolean update(ServerLevel level, Tree t, long today, double time) {
        // The book counts as changed only when the tree's record really changed (review R7-6: every look at a tree
        // waiting to be out of sight marked it, so the whole book was written at every autosave).
        CompoundTag before = t.save();
        boolean done = bring(level, t, today, time);
        if (trees.get(t.foot.asLong()) == t && !before.equals(t.save())) {
            setDirty(); // (a tree whose record ended has marked the book itself)
        }
        return done;
    }

    private boolean bring(ServerLevel level, Tree t, long today, double time) {
        if (falls.containsKey(t.foot.asLong())) {
            return true; // being drawn falling: it lands by itself
        }
        if (!LandReady.at(level, t.foot)) {
            return false;
        }
        TreePlacer.Placed now = t.placedAge >= 0 ? soon(t, t.placedAge, t.placedStage, t.placedStep, t.placedTermites, t.placedRot) : null;
        if (t.placedAge >= 0 && now == null) {
            return false; // its shape is being worked out
        }
        if (now != null && !loaded(level, now.box(t.foot))) {
            return false;
        }
        if (now != null && !(t.dead() && t.treeKind().isBaobab()) && cutThrough(level, t, now)) {
            // Its trunk was cut through at the foot (the user's call, 2026-11-26, review R7-31: the record just ended
            // and the trunk and crown hung in the air for good).
            if (t.placedAge < TreeLife.YOUNG_END || t.fell || t.placedStage == TreeStage.FALLEN) {
                if (t.fell || t.placedStage == TreeStage.FALLEN) {
                    remove(t); // a log: the record ends
                } else {
                    die(level, t, now); // a little tree: what is left of it goes
                }
                return true;
            }
            if (t.treeKind().isBaobab()) {
                kill(t, today, "cut"); // a baobab dies and slumps into its heap
            } else if (!snapShapesReady(t)) {
                cutLooks.add(t.foot.asLong()); // its shapes are being worked out: it falls the second they are ready
                return false;
            } else if (!fellNow(level, t)) {
                return false; // its land is not all loaded: it waits
            } else {
                t.cause = "cut";
                return true;
            }
        }
        if (t.lastDay > today) {
            t.lastDay = today; // the clock was set back
        }
        if (t.died == DIED_UNKNOWN) {
            // A dead tree of new land: it died some days ago, somewhere inside the stage it stands in.
            double roll = TreeLife.dice(t.seed, -9).nextDouble();
            t.died = today - TreeLife.daysDeadIn(t.treeKind(), t.seed, t.stage, roll);
        }
        long fromDay = t.lastDay;
        int lying = -1;     // the branches under its crown: counted once, at the first day that drops one (review R7-1)
        while (t.lastDay < today) {
            t.lastDay++;
            if (t.dead()) {
                continue; // its stage comes from the days since it died, below
            }
            if (!t.termites && t.age >= TreeLife.YOUNG_END && !t.treeKind().isBaobab() && termitesUnder(level, t.foot)) {
                // (never a baobab, the user's call 2026-11-26, review R7-14: its wet fibre is not their wood, and
                // the hollow was made for an acacia's trunk)
                t.termites = true; // termites have come in from below: its heartwood is eaten from now on
            }
            HeartRot.daily(t, t.lastDay);
            t.lived += 1 + (t.termites ? 1 : 0) + HeartRot.extraAgeing(t);
            if (t.age >= TreeLife.YOUNG_END && t.lived >= TreeLife.lifespan(t.treeKind(), t.seed)) {
                kill(t, t.lastDay, HeartRot.hollow(t) ? "rot" : "age"); // a hollow tree's life ran out with the rot
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
            // Step 6b: a grown tree drops a dead branch under its crown now and then (more when old).
            Random shedDice = TreeLife.dice(t.seed, t.lastDay * 31 + 17);
            double shedChance = BranchShedding.chance(t.age) * (HeartRot.hollow(t) ? 2 : 1); // a hollow tree sheds more
            if (now != null && now.size() > 0 && shedDice.nextDouble() < shedChance) {
                if (lying < 0) {
                    lying = BranchShedding.lyingUnder(level, t.foot, now);
                }
                if (lying < BranchShedding.MAX_UNDER_TREE && BranchShedding.drop(level, t.foot, now, shedDice, t.lastDay == today)) {
                    lying++;
                    shed++;
                    HeartRot.wound(t, t.lastDay, HeartRot.SHED_CHANCE, shedDice);
                }
            }
        }
        int stageSteps = 0;
        if (t.dead()) {
            double daysDead = Math.max(today, time) - t.died;
            t.stage = deadStageNow(t, daysDead);
            t.step = t.stage == null ? 0 : deadStepNow(t, t.stage, daysDead);
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
        if (now != null && t.living() && t.brackets < HeartRot.bracketsWanted(t)) {
            // Part 4: the shelf fungi come out on the bark.
            t.brackets += HeartRot.placeBrackets(level, t.foot, now, HeartRot.bracketsWanted(t) - t.brackets, t.lastDay - t.rotFrom - HeartRot.BRACKETS_FROM,
                    TreeLife.dice(t.seed, today * 61 + 3));
        }
        if (now != null && t.lastDay > fromDay && t.brackets > 0 && t.rotFrom != HeartRot.NONE) {
            // The brackets already out grow on with the days gone by (their own growing needs a player near).
            HeartRot.ageBrackets(level, t.foot, now, t.lastDay - t.rotFrom - HeartRot.BRACKETS_FROM);
        }
        if (now != null && t.living() && !t.woundShown && HeartRot.stage(t, t.lastDay) != HeartRot.Stage.NONE) {
            // The scar where the rot got in, low on the trunk (the looks picked 2026-10-26).
            t.woundShown = HeartRot.placeWound(level, t.foot, now, TreeLife.dice(t.seed, -43));
        }
        if (t.upToDate()) {
            t.pendingDays = 0;
            return true;
        }
        TreePlacer.Placed next = soon(t, t.age, t.stage, t.step, t.termites, HeartRot.hollow(t));
        boolean jump = t.dead() ? stageSteps > 1 : t.pendingDays > 1;
        if (next == null || !loaded(level, next.box(t.foot))) {
            return false;
        }
        if (jump && !outOfSight(level, t, next)) {
            if (now != null && t.living() && t.placedRot != HeartRot.hollow(t)) {
                // The core rots inside the trunk, unseen: its hollow is put in now, at the shape's old age (the growth
                // still waits until nobody looks, one day's step at a time in sight).
                TreePlacer.Placed rotted = soon(t, t.placedAge, t.placedStage, t.placedStep, t.placedTermites, HeartRot.hollow(t));
                if (rotted != null) {
                    TreePlacer.grow(level, now, rotted, t.foot, Block.UPDATE_ALL, new ArrayList<>(), null);
                    t.placedRot = HeartRot.hollow(t);
                    setDirty();
                }
            }
            return false;
        }
        if (t.stage == TreeStage.FALLEN && now != null && !t.toppling && !outOfSight(level, t, next)) {
            topple(level, t, now, next);
            return true;
        }
        place(level, t, now, next);
        return true;
    }

    /** Step 5c: a dead tree starts falling in sight (drawn by {@link TreeFall}; it lands by itself, {@link #landed}). */
    public void topple(ServerLevel level, Tree t, TreePlacer.Placed now, TreePlacer.Placed next) {
        next = TreePlacer.rest(next, t.foot, TreePlacer.ground(level, t.foot)); // the log lies on the land where it falls
        t.toppling = true;
        setDirty();
        falls.put(t.foot.asLong(), new TreeFall(level, this, t, now, next));
    }

    /** For testing (the command): a living grown tree dies now. Returns false if it is not living. */
    public boolean killNow(ServerLevel level, Tree t) {
        if (!t.living()) {
            return false;
        }
        kill(t, today(level), HeartRot.hollow(t) ? "rot" : "age");
        return true;
    }

    /**
     * For testing (the command): a grown acacia, living or dead but standing, is put at once into its shed shape and then
     * falls in sight (drawn, wherever the players are). Returns false for a baobab, a fallen log or one already falling.
     */
    public boolean fellNow(ServerLevel level, Tree t) {
        TreeKind kind = t.treeKind();
        if (kind.isBaobab() || t.fell || t.toppling || falling(t) || t.placedAge < 0 || !LandReady.at(level, t.foot)) {
            return false;
        }
        boolean rot = HeartRot.hollow(t);
        TreePlacer.Placed now = burnt(t, TreePlacer.placed(t.shapeKind(t.placedTermites, t.placedRot), t.seed, t.placedAge, t.placedStage, t.placedStep), t.placedStage);
        TreePlacer.Placed shed = burnt(t, TreePlacer.placed(t.shapeKind(t.termites, rot), t.seed, TreeLife.shapeAge(t.age), TreeStage.SHED), TreeStage.SHED);
        TreePlacer.Placed lying = burnt(t, TreePlacer.placed(t.shapeKind(t.termites, rot), t.seed, TreeLife.shapeAge(t.age), TreeStage.FALLEN), TreeStage.FALLEN);
        if (!loaded(level, now.box(t.foot)) || !loaded(level, shed.box(t.foot)) || !loaded(level, lying.box(t.foot))) {
            return false; // every chunk the tree and its log reach, not only its foot's (review R7-20)
        }
        if (t.living()) {
            t.cause = rot ? "rot" : "age";
            killed++;
        }
        // Dead long enough to be due to fall today.
        t.died = (long) Math.floor(time(level) - TreeLife.stageFrom(kind, t.seed, TreeStage.FALLEN));
        t.stage = TreeStage.SHED;
        t.step = TreeLife.fullStep(TreeStage.SHED);
        place(level, t, now, shed);
        t.fell = true;
        t.stage = TreeStage.FALLEN;
        t.step = 0;
        topple(level, t, shed, lying);
        return true;
    }

    /**
     * A log of a tree the rot killed (and in the end a sound log, weathered: {@link HeartRot#WEATHER_DAYS}) crumbles to pulp, a column a second from its far end in to the stump, over
     * {@link HeartRot#CRUMBLE_DAYS} days once its time has come ({@link HeartRot#crumbleFrom}); when the last column has
     * gone the tree's record ends and only the pulp is left, sinking into the soil. Called each second for every
     * rot-killed log (public for the tests).
     */
    public void crumble(ServerLevel level, Tree t) {
        double f = (time(level) - HeartRot.crumbleFrom(t)) / HeartRot.CRUMBLE_DAYS;
        if (f <= 0) {
            return;
        }
        TreePlacer.Placed lying = soon(t, t.placedAge, t.placedStage, t.placedStep, t.placedTermites, t.placedRot);
        if (lying == null || !loaded(level, lying.box(t.foot))) {
            return;
        }
        List<int[]> columns = HeartRot.crumbleOrder(lying);
        int due = (int) Math.min(columns.size(), Math.ceil(f * columns.size()));
        if (t.crumbled < due) {
            // The column's top as the log really lies: laid on its land, each slice moved up or down to its own ground
            // (review R7-16: the flat-ground top left a log's upper wood hanging where it fell uphill).
            int[] column = columns.get(t.crumbled);
            TreePlacer.Placed laid = TreePlacer.rest(lying, t.foot, TreePlacer.ground(level, t.foot));
            int top = Integer.MIN_VALUE;
            for (int i = 0; i < laid.size(); i++) {
                if (laid.xs()[i] == column[0] && laid.zs()[i] == column[1]) {
                    top = Math.max(top, laid.ys()[i]);
                }
            }
            if (top != Integer.MIN_VALUE) {
                column = new int[] {column[0], column[1], Math.max(top, column[2])};
            }
            pulpHeaps += HeartRot.crumbleColumn(level, t.foot, column, TreeLife.dice(t.seed, -71 - t.crumbled));
            t.crumbled++;
            setDirty();
        }
        if (t.crumbled >= columns.size()) {
            remove(t);
            setDirty();
        }
    }

    /** Whether a tree is being drawn falling. */
    public boolean falling(Tree t) {
        return falls.containsKey(t.foot.asLong());
    }

    /** A falling tree hits the ground: the fallen log is placed. */
    void landed(ServerLevel level, Tree t, TreePlacer.Placed now, TreePlacer.Placed next) {
        place(level, t, now, next);
    }

    /** A grown tree dies (step 5): its dead stages start (the day it dies its leaves wither on the living shape). */
    public void kill(Tree t, long day, String cause) {
        t.died = day;
        t.cause = cause;
        killed++;
        setDirty();
    }

    /** A tree's shape at an age and stage, if worked out (or being worked out in the background: null). */
    private static TreePlacer.Placed soon(Tree t, double age, TreeStage stage, int step, boolean hollowed, boolean rotted) {
        String kind = t.shapeKind(hollowed, rotted);
        return burnt(t, shapesAtOnce ? TreePlacer.placed(kind, t.seed, TreeLife.shapeAge(age), stage, step)
                : TreePlacer.placedSoon(kind, t.seed, TreeLife.shapeAge(age), stage, step), stage);
    }

    /**
     * Step 5f: whether a fungus-growing termite colony's foraging reach takes in this place (real: Macrotermes eat dead
     * wood, the heartwood of the trees over their galleries and round their mound among it).
     */
    public static boolean termitesUnder(ServerLevel level, BlockPos foot) {
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (colony.profile().section(SavannaNumbers.GARDEN).isEmpty()) {
                continue;
            }
            double dx = colony.getCenter().getX() - foot.getX(), dz = colony.getCenter().getZ() - foot.getZ();
            double reach = colony.foragingRadius();
            if (dx * dx + dz * dz <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** Step 5d: a tree killed by fire stands and lies black (its dead wood charred) instead of silver. */
    private static TreePlacer.Placed burnt(Tree t, TreePlacer.Placed shape, TreeStage stage) {
        return "fire".equals(t.cause) && stage != TreeStage.LIVING ? TreePlacer.charred(shape) : shape;
    }

    /**
     * Step 5d: a fire has charred or scorched these blocks (by {@link TreeFire}). A young tree it touches dies; a grown
     * living tree whose trunk is in the fire (its lowest 4 blocks) may die, rolled once a day, more surely when old and
     * hollow ({@link TreeLife#fireDeathChance}); a hollow one killed smoulders for half a day.
     */
    public void inFire(ServerLevel level, List<BlockPos> hit) {
        BlockPos first = hit.get(0);
        long today = today(level);
        for (Tree t : near(first, 30)) {
            if (!t.living() || t.placedAge < 0 || falling(t)) {
                continue;
            }
            TreePlacer.Placed now = soon(t, t.placedAge, t.placedStage, t.placedStep, t.placedTermites, t.placedRot);
            if (now == null) {
                continue;
            }
            BoundingBox box = now.box(t.foot);
            if (!loaded(level, box)) {
                continue; // (review R7-20: a young tree killed here had its blocks taken out with no such check)
            }
            boolean any = false, trunk = false;
            for (BlockPos pos : hit) {
                if (box.isInside(pos) && isPart(now, t.foot, pos, level.getBlockState(pos))) {
                    any = true;
                    int dy = pos.getY() - t.foot.getY(), dx = pos.getX() - t.foot.getX(), dz = pos.getZ() - t.foot.getZ();
                    trunk |= dy <= 3 && dx * dx + dz * dz <= 16 && level.getBlockState(pos).is(TreeBlocks.TREE_WOOD);
                }
            }
            if (!any) {
                continue;
            }
            if (t.age < TreeLife.YOUNG_END) {
                die(level, t, now); // a little tree's thin bark: the fire kills it
                continue;
            }
            if (!trunk || fireDays.containsKey(t.foot.asLong()) && fireDays.get(t.foot.asLong()) >= today) {
                continue;
            }
            fireDays.put(t.foot.asLong(), today);
            if (TreeLife.fireKills(t.seed, today, t.age, t.termites || HeartRot.hollow(t))) {
                kill(t, today, "fire");
                if (TreeLife.smoulders(t.age) || HeartRot.hollow(t)) {
                    smoulders.put(t.foot.asLong(), new Smoulder(trunkTop(now, t.foot), time(level) + SMOULDER_DAYS));
                }
            } else {
                HeartRot.wound(t, today, HeartRot.FIRE_CHANCE, TreeLife.dice(t.seed, today * 67 + 1)); // the fire scar lets the rot in
            }
        }
    }

    /** Step 5e: how far (sideways from its foot) a big tree pulls a lightning strike to its top (my pick). */
    public static final int LIGHTNING_REACH = 24;
    /** How tall (blocks over its foot) a tree must stand to draw lightning (my pick: a big tree). */
    static final int LIGHTNING_HEIGHT = 10;

    /**
     * Step 5e: a lightning strike at {@code at}. The tallest big standing tree (living, or dead and still upright) whose
     * foot is within {@link #LIGHTNING_REACH} pulls it to its top like a lightning rod (real: a lone tall tree is the
     * classic target on the savanna) and is struck ({@link #struck}), each tree once a thunderstorm (the user's call,
     * 2026-11-26, review R7-12: every bolt pulled onto a tree killed about 1 big tree in 8 a storm, by sum). Returns where the strike lands now, over the tree's
     * highest block, or null if no tree draws it.
     */
    public BlockPos lightning(ServerLevel level, BlockPos at) {
        Tree best = null;
        TreePlacer.Placed bestShape = null;
        int top = LIGHTNING_HEIGHT - 1, topX = 0, topZ = 0;
        for (Tree t : near(at, LIGHTNING_REACH)) {
            int dx = t.foot.getX() - at.getX(), dz = t.foot.getZ() - at.getZ();
            if (dx * dx + dz * dz > LIGHTNING_REACH * LIGHTNING_REACH || struckThisStorm.contains(t.foot.asLong()) || t.placedAge < 0 || t.fell || t.toppling || falling(t)
                    || t.placedStage == TreeStage.FALLEN || t.placedStage == TreeStage.HEAP || t.placedStage == TreeStage.HEAP_DRY) {
                continue;
            }
            TreePlacer.Placed now = soon(t, t.placedAge, t.placedStage, t.placedStep, t.placedTermites, t.placedRot);
            if (now == null || !loaded(level, now.box(t.foot))) {
                continue;
            }
            for (int i = 0; i < now.size(); i++) {
                if (now.ys()[i] > top) {
                    top = now.ys()[i];
                    topX = now.xs()[i];
                    topZ = now.zs()[i];
                    best = t;
                    bestShape = now;
                }
            }
        }
        if (best == null) {
            return null;
        }
        BlockPos to = best.foot.offset(topX, top + 1, topZ);
        struckThisStorm.add(best.foot.asLong());
        struck(level, best, bestShape);
        return to;
    }

    /**
     * A big tree struck by lightning: a scar of charred bark down one side of its trunk, from the top of the trunk to the
     * ground; 1 in 2 it sets the foot of the trunk on fire (which may kill it, {@link #inFire}); 1 in 3 a living one dies
     * of the strike itself ({@link TreeLife#strike}).
     */
    private void struck(ServerLevel level, Tree t, TreePlacer.Placed now) {
        long today = today(level);
        TreeLife.Strike strike = TreeLife.strike(t.seed, today, t.termites || HeartRot.hollow(t));
        double sx = Math.cos(strike.side()), sz = Math.sin(strike.side());
        int height = trunkTop(now, t.foot).getY() - t.foot.getY();
        // The trunk's outermost block on the struck side at each height.
        int[] scar = new int[height];
        double[] far = new double[height];
        java.util.Arrays.fill(scar, -1);
        for (int i = 0; i < now.size(); i++) {
            int x = now.xs()[i], y = now.ys()[i], z = now.zs()[i];
            if (y < 0 || y >= height || x * x + z * z > 25 || !now.states()[i].is(TreeBlocks.TREE_WOOD)) {
                continue;
            }
            double d = x * sx + z * sz;
            if (scar[y] < 0 || d > far[y]) {
                scar[y] = i;
                far[y] = d;
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos foot = null;
        for (int y = 0; y < height; y++) {
            int i = scar[y];
            if (i < 0) {
                continue;
            }
            pos.set(t.foot.getX() + now.xs()[i], t.foot.getY() + y, t.foot.getZ() + now.zs()[i]);
            BlockState there = level.getBlockState(pos);
            if (TreePlacer.same(there, now.states()[i])) {
                level.setBlock(pos, TreePlacer.charred(there), Block.UPDATE_CLIENTS);
                if (foot == null) {
                    foot = pos.immutable();
                }
            }
        }
        setDirty();
        if (strike.lit() && foot != null) {
            // The fire at the foot of the scar, on the ground beside it.
            Direction out = Direction.getNearest(sx, 0, sz);
            for (int up = 0; up <= 1; up++) {
                BlockPos fire = foot.relative(out).above(up);
                if (level.getBlockState(fire).isAir() && level.getBlockState(fire.below()).isFaceSturdy(level, fire.below(), Direction.UP)) {
                    level.setBlock(fire, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                    break;
                }
            }
        }
        if (strike.kills() && t.living()) {
            kill(t, today, "lightning");
        } else {
            HeartRot.wound(t, today, HeartRot.LIGHTNING_CHANCE, TreeLife.dice(t.seed, today * 71 + 2)); // the burst bark lets the rot in
        }
    }

    /** Whether a tree is smouldering (for the tests). */
    public boolean smouldering(Tree t) {
        return smoulders.containsKey(t.foot.asLong());
    }

    /** The top of a tree's trunk: its highest wood block within a block of its middle. */
    private static BlockPos trunkTop(TreePlacer.Placed shape, BlockPos foot) {
        int top = 0;
        for (int i = 0; i < shape.size(); i++) {
            if (Math.abs(shape.xs()[i]) <= 1 && Math.abs(shape.zs()[i]) <= 1 && shape.states()[i].is(TreeBlocks.TREE_WOOD)) {
                top = Math.max(top, shape.ys()[i]);
            }
        }
        return foot.above(top + 1);
    }

    /** Smoke out of the hollow trunks killed by fire, until their time is up. */
    private void smoulder(ServerLevel level) {
        if (smoulders.isEmpty()) {
            return;
        }
        double time = time(level);
        smoulders.values().removeIf(s -> s.until() < time);
        for (Smoulder s : smoulders.values()) {
            if (!LandReady.at(level, s.top())) {
                continue;
            }
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, s.top().getX() + 0.5, s.top().getY(), s.top().getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.005);
            level.sendParticles(ParticleTypes.SMOKE, s.top().getX() + 0.5, s.top().getY() - 1, s.top().getZ() + 0.5, 4, 0.6, 0.6, 0.6, 0.01);
        }
    }

    /** Brings the tree's shape in the world from what is there to its age now. */
    private void place(ServerLevel level, Tree t, TreePlacer.Placed now, TreePlacer.Placed next) {
        // What lies (a fallen log, broken limbs) lies on the land where it is (the world's own shape fitted the same way).
        now = TreePlacer.rest(now, t.foot, TreePlacer.ground(level, t.foot));
        next = TreePlacer.rest(next, t.foot, TreePlacer.ground(level, t.foot));
        if (t.stage == TreeStage.FALLEN && t.placedStage != TreeStage.FALLEN) {
            TreeFall.clearWhereItLies(level, t.foot, now, next); // step 5c: what is soft where the log lands is smashed
        }
        List<BlockPos> shed = new ArrayList<>();
        List<BlockPos> leaves = new ArrayList<>(), wood = new ArrayList<>();
        List<BlockState> leafStates = new ArrayList<>();
        TreePlacer.grow(level, now, next, t.foot, Block.UPDATE_ALL, shed, t.dead() ? (pos, was) -> {
            if (was.is(TreeBlocks.TREE_LEAVES)) {
                leaves.add(pos);
                leafStates.add(was);
            } else if ((was.getBlock() instanceof RotatedPillarBlock || was.getBlock() instanceof TreeBranchBlock) && pos.getY() > t.foot.getY() + 2) {
                wood.add(pos);
            }
        } : null);
        if (t.placedStage != t.stage || t.placedStep != t.step) {
            deadSteps++;
        }
        fallingLeaves(level, leaves, leafStates, TreeLife.dice(t.seed, t.lastDay * 16 + t.step));
        fallingLimbs(level, wood);
        if (t.stage == TreeStage.FALLEN && t.placedStage != TreeStage.FALLEN && !t.toppling) {
            TreeFall.crushUnder(level, t.foot, next, java.util.Set.of()); // it fell out of sight: what lay where it lies is crushed all the same
        }
        if (t.stage == TreeStage.FALLEN && t.placedStage != TreeStage.FALLEN && HeartRot.snapped(t)) {
            pulpHeaps += HeartRot.spillPulp(level, t.foot, next, TreeLife.dice(t.seed, -61)); // the rotten core spills out
        }
        t.toppling = false;
        t.placedAge = TreeLife.shapeAge(t.age);
        t.placedStage = t.stage;
        t.placedStep = t.step;
        t.placedTermites = t.termites;
        t.placedRot = HeartRot.hollow(t);
        t.pendingDays = 0;
        setDirty();
        if (!shed.isEmpty() && t.living()) {
            // The twigs it shed (self-pruning) fall to the ground under them, one fallen branch (my pick).
            BlockPos from = shed.get(TreeLife.dice(t.seed, t.lastDay + 7).nextInt(shed.size()));
            dropBranch(level, from, TreeLife.dice(t.seed, t.lastDay + 13));
        }
    }

    /** How hard a breaking limb hits what is under it (step 5b, my pick: 3 hearts; an insect dies). */
    static final float LIMB_DAMAGE = 6;
    /** The damage a falling branch does ({@code data/antcolonies/damage_type/falling_branch.json}). */
    public static final ResourceKey<DamageType> FALLING_BRANCH = ResourceKey.create(Registries.DAMAGE_TYPE,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "falling_branch"));

    /** A few bits of brown leaf drifting down where the withered leaves dropped (not every block: the air would be thick with them). */
    private static void fallingLeaves(ServerLevel level, List<BlockPos> leaves, List<BlockState> states, Random dice) {
        int shown = 0;
        for (int i = 0; i < leaves.size() && shown < 60; i++) {
            if (dice.nextInt(4) != 0) {
                continue;
            }
            BlockPos pos = leaves.get(i);
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, states.get(i)), pos.getX() + 0.5, pos.getY() + 0.3,
                    pos.getZ() + 0.5, 2, 0.35, 0.2, 0.35, 0);
            shown++;
        }
    }

    /**
     * Limbs breaking off a dead tree come down on what is under them: anything alive under the broken wood is hit once
     * (real: a dead acacia's heavy limbs drop without warning).
     */
    private static void fallingLimbs(ServerLevel level, List<BlockPos> wood) {
        if (wood.isEmpty()) {
            return;
        }
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap columns = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        BoundingBox box = BoundingBox.encapsulatingPositions(wood).orElseThrow();
        for (BlockPos pos : wood) {
            long column = ChunkPos.asLong(pos.getX(), pos.getZ());
            columns.put(column, Math.max(columns.getOrDefault(column, Integer.MIN_VALUE), pos.getY()));
        }
        AABB under = new AABB(box.minX() - 1, box.minY() - 40, box.minZ() - 1, box.maxX() + 2, box.maxY(), box.maxZ() + 2);
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FALLING_BRANCH));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, under, LivingEntity::isAlive)) {
            BlockPos at = e.blockPosition();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long column = ChunkPos.asLong(at.getX() + dx, at.getZ() + dz);
                    if (columns.containsKey(column) && columns.get(column) > at.getY() && openBetween(level, at, columns.get(column))) {
                        e.hurt(source, LIMB_DAMAGE);
                        dx = dz = 2; // once
                    }
                }
            }
        }
    }

    /** Whether nothing solid lies between something and the wood over it (a roof keeps a falling limb off). */
    private static boolean openBetween(ServerLevel level, BlockPos at, int woodY) {
        BlockPos.MutableBlockPos pos = at.mutable();
        for (int y = at.getY() + 2; y < woodY; y++) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (state.isSolid() && !state.is(TreeBlocks.TREE_WOOD) && !state.is(TreeBlocks.TREE_LEAVES)) {
                return false;
            }
        }
        return true;
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

    /**
     * Whether a tree's trunk has been cut through at its foot (fewer than half its foot blocks still its own). Termites
     * eating a dead tree from its foot (step 6d) cut it too: a foot block eaten half through or more no longer holds
     * (real termite-hollowed snags topple).
     */
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
            BlockState state = level.getBlockState(pos);
            if (TreePlacer.same(state, now.states()[i])
                    || state.is(now.states()[i].getBlock()) && state.getValue(com.antcolonies.savanna.block.DeadWoodBlock.EATEN) < 2) {
                there++;
            }
        }
        return foot > 0 && there * 2 < foot;
    }

    private static boolean loaded(ServerLevel level, BoundingBox box) {
        return LandReady.box(level, box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1); // (one more: a block set at the edge tells its neighbours)
    }

    /** Whether no player can see the tree's foot, middle or top (a catch-up of many days waits until then). */
    private static boolean outOfSight(ServerLevel level, Tree t, TreePlacer.Placed next) {
        List<Vec3> eyes = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (com.antcolonies.colony.Watchers.watches(player) && player.blockPosition().closerThan(t.foot, Population.SIGHT + 48)) {
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
                return TreePlacer.same(there, shape.states()[i]);
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
                if (!LandReady.box(level, x0, z0, x0 + REGION - 1, z0 + REGION - 1)) {
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
            if (crowd(x0, z0) >= SQUARE_ROOM) {
                break; // the square is full: seedlings come again when a tree has died
            }
            Random dice = new Random(level.getSeed() ^ ChunkPos.asLong(x0, z0) * 31 ^ day * 0x5DEECE66DL);
            for (int i = 0; i < SPROUT_TRIES; i++) {
                int x = x0 + dice.nextInt(REGION), z = z0 + dice.nextInt(REGION);
                boolean takes = dice.nextDouble() < SPROUT_CHANCE;
                String kind = pickKind(dice);
                long seed = dice.nextLong() & 0xFFFFFFFFL;
                BlockPos foot = sproutSite(level, x, z);
                if (takes && foot != null && TreeLands.mayStand(kind, level.getBiome(foot)) && crowd(x0, z0) < SQUARE_ROOM) {
                    Tree t = addSeedling(kind, foot, seed, day + 1, false);
                    sprouted++;
                    count++;
                    update(level, t, today); // it lives the days since it came up, and comes up (or has died)
                }
            }
        }
        return count;
    }

    /** How full a square of land is with living trees ({@link #SQUARE_ROOM}). */
    private int crowd(int x0, int z0) {
        int crowd = 0;
        for (Tree t : near(new BlockPos(x0 + REGION / 2, 0, z0 + REGION / 2), REGION / 2)) {
            if (t.living() && t.foot.getX() >= x0 && t.foot.getX() < x0 + REGION && t.foot.getZ() >= z0 && t.foot.getZ() < z0 + REGION) {
                crowd += t.age >= TreeLife.YOUNG_END ? GROWN_ROOM : 1;
            }
        }
        return crowd;
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
        if (underCrown || !level.getBiome(foot).is(BiomeTags.IS_SAVANNA)
                || !com.antcolonies.savanna.worldgen.BigSavanna.lives(level, level.getBiome(foot), x, z)) {
            return null; // under a crown (seedlings there are shaded and outcompeted), not savanna, or a savanna too small to live in
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
            try {
                forest.add(Tree.load((CompoundTag) t));
            } catch (RuntimeException e) {
                // One record that cannot be read (a kind renamed or taken out) is left out, not the whole book (review R7-18).
                com.mojang.logging.LogUtils.getLogger().error("Tree book: a tree's record could not be read and is left out: {}", t, e);
            }
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
