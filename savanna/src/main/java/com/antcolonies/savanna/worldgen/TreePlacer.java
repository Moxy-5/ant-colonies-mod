package com.antcolonies.savanna.worldgen;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.antcolonies.savanna.block.FallenBranchBlock;
import com.antcolonies.savanna.block.MudCrustBlock;
import com.antcolonies.savanna.block.TaperingBranchBlock;
import com.antcolonies.savanna.block.TreeBranchBlock;
import com.antcolonies.savanna.block.TreeLeavesBlock;
import com.antcolonies.savanna.registry.TreeBlocks;
import com.antcolonies.savanna.tree.TreeKind;
import com.antcolonies.savanna.tree.TreeLife;
import com.antcolonies.savanna.tree.TreePart;
import com.antcolonies.savanna.tree.TreeShapes;
import com.antcolonies.savanna.tree.TreeStage;
import com.antcolonies.savanna.tree.TreeVoxels;
import com.antcolonies.savanna.registry.SavannaBlocks;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Puts a giant savanna tree's shape ({@link TreeShapes}) into the world with its kind's blocks (M7.5 step 3): bark as
 * wood (bark on every side), the wood inside as logs, thin branches joined to what they touch, dead wood bleached
 * silver, termite mud as Mud Crust plastered over the dead bark. A tree fits itself to the ground: it only takes air,
 * grass and other plants (it never breaks into rock or a hillside, which it simply goes into), its roots take the soil
 * at its foot, and where the ground dips under its foot the trunk goes down to meet it.
 *
 * <p>One tree is worked out once and kept for a while ({@link #CACHE}), as a giant crosses many chunks and each chunk
 * puts its own part of it in place.
 */
public final class TreePlacer {
    /** How far a trunk goes down to meet ground that dips under its foot. */
    public static final int MAX_FILL_DOWN = 8;
    private static final int CACHE_SIZE = 48;
    private static final Map<String, Placed> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Placed> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private static final Map<String, TreeKind> KINDS = new LinkedHashMap<>();

    private TreePlacer() {
    }

    /** A tree's kind by name (umbrella_thorn, camel_thorn, baobab), read once ({@link #HOLLOWED} and {@link #ROTTED} on the name aside). */
    public static TreeKind kind(String name) {
        String base = name.endsWith(ROTTED) ? name.substring(0, name.length() - ROTTED.length()) : name;
        base = base.endsWith(HOLLOWED) ? base.substring(0, base.length() - HOLLOWED.length()) : base;
        synchronized (KINDS) {
            return KINDS.computeIfAbsent(base, TreeKind::load);
        }
    }

    /**
     * Step 5f: put after a kind's name, the tree's shape is hollowed by termites ({@code TreeShapes.hollowOut}); kept
     * apart from the plain shape everywhere a shape is kept.
     */
    public static final String HOLLOWED = "+termites";

    /**
     * Dead wood for the raids, part 4: put after a kind's name (after {@link #HOLLOWED} if both), the tree's trunk is
     * hollowed by heart rot ({@code TreeShapes.hollowOut}, {@code tree/HeartRot}).
     */
    public static final String ROTTED = "+rot";

    /**
     * A tree worked out into blocks, around its foot at (0, 0, 0): each block's place and state, which ones hold the
     * tree up (the trunk goes down from them to meet the ground), and the mud plates beside the wood.
     */
    public record Placed(int[] xs, int[] ys, int[] zs, BlockState[] states, boolean[] holds, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ, Lying lying) {
        public int size() {
            return states.length;
        }

        /** The box round the tree with its foot at {@code foot}, room for the trunk to go down included. */
        public BoundingBox box(BlockPos foot) {
            return new BoundingBox(foot.getX() + minX, foot.getY() + minY - MAX_FILL_DOWN, foot.getZ() + minZ,
                    foot.getX() + maxX, foot.getY() + maxY, foot.getZ() + maxZ);
        }
    }

    /**
     * What lies on the ground, by block: {@link #FIXED} (the standing tree or a fallen tree's stump, which stays where it
     * stands), {@link #LOOSE} (a loose piece, which lies on the ground under it on its own), or the slice of a fallen log
     * it is in: {@link #SLICES} times the log (the trunk 0, its limb stubs 1 to 3) plus the slice along that log from its
     * start (1 block a slice). {@code core}: a block on its log's middle line (within a block of it), the ones that rest
     * on the ground.
     */
    public record Lying(int[] slice, boolean[] core) {
    }

    public static final int FIXED = -1, LOOSE = -2, SLICES = 1000;
    /**
     * How far a fallen log may settle into the soil where the ground under it is uneven: none (a block of wood is never
     * placed into the soil, so a bedded floor left the hollow's roof over a gap), and the thick hollow trunk by the stump
     * stays open (a home for Matabele ants and other creatures, the user's plan 2026-10-19).
     */
    public static final int MAX_SINK = 0;
    /** How far up and down a column is searched for the ground a fallen log lies on. */
    static final int GROUND_SEARCH = 32;

    /** Where the ground is: the first free block's height over the solid ground at a column (world coordinates). */
    public interface Ground {
        int freeY(int x, int z);
    }

    /**
     * A fallen tree fitted to the land it lies on (the user's reports 2026-10-19: on a slope fallen giants stood on walls of
     * wood, then floated in places; the user's rule: every block touches the ground, the log keeping its shape). Real: an
     * old hollow giant does not stay a stiff beam when it crashes onto uneven ground; it cracks and settles along the land.
     * Each slice of a log (1 block along the way it lies) lies with its underside on the
     * ground: as one piece where the ground under it is even, and where it is not, each column of it settles onto its own
     * ground (each column keeps all its blocks, so the log keeps its full thickness, the hollow open). The stump stays;
     * loose pieces (broken limbs, fallen branches, also round a standing dead tree) lie on the ground under them; nothing
     * goes down to meet the ground. A tree with nothing lying is returned as it is.
     */
    public static Placed rest(Placed tree, BlockPos foot, Ground ground) {
        if (tree == null || tree.lying() == null) {
            return tree;
        }
        int[] slice = tree.lying().slice();
        int slices = 0;
        for (int sl : slice) {
            slices = Math.max(slices, sl + 1);
        }
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap heights = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        java.util.function.IntBinaryOperator at = (x, z) -> heights.computeIfAbsent(BlockPos.asLong(x, 0, z),
                c -> ground.freeY(foot.getX() + x, foot.getZ() + z) - foot.getY());
        // The underside of each slice's columns, then how far each must move to sit on the ground.
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap bottom = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        bottom.defaultReturnValue(Integer.MAX_VALUE);
        for (int i = 0; i < tree.size(); i++) {
            if (slice[i] >= 0) {
                long key = column(slice[i], tree.xs()[i], tree.zs()[i]);
                bottom.put(key, Math.min(bottom.get(key), tree.ys()[i]));
            }
        }
        int[] lowest = new int[slices], highest = new int[slices];
        java.util.Arrays.fill(lowest, Integer.MAX_VALUE);
        java.util.Arrays.fill(highest, Integer.MIN_VALUE);
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap need = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        for (int i = 0; i < tree.size(); i++) {
            int sl = slice[i];
            if (sl < 0) {
                continue;
            }
            long key = column(sl, tree.xs()[i], tree.zs()[i]);
            if (!need.containsKey(key)) {
                int d = at.applyAsInt(tree.xs()[i], tree.zs()[i]) - bottom.get(key);
                need.put(key, d);
                lowest[sl] = Math.min(lowest[sl], d);
                highest[sl] = Math.max(highest[sl], d);
            }
        }
        int n = tree.size();
        int[] ys = new int[n];
        boolean[] holds = new boolean[n];
        int minX = 0, minY = -1, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (int i = 0; i < n; i++) {
            int sl = slice[i];
            if (sl >= 0) {
                // The slice as one piece, its highest column bedded in by at most MAX_SINK; a column that would still hang
                // settles onto its own ground.
                int whole = Math.max(lowest[sl], highest[sl] - MAX_SINK);
                ys[i] = tree.ys()[i] + Math.min(whole, need.get(column(sl, tree.xs()[i], tree.zs()[i])));
            } else if (sl == LOOSE) {
                ys[i] = tree.ys()[i] + at.applyAsInt(tree.xs()[i], tree.zs()[i]);
            } else {
                ys[i] = tree.ys()[i];
                holds[i] = tree.holds()[i];
            }
            minX = Math.min(minX, tree.xs()[i]);
            minY = Math.min(minY, ys[i]);
            minZ = Math.min(minZ, tree.zs()[i]);
            maxX = Math.max(maxX, tree.xs()[i]);
            maxY = Math.max(maxY, ys[i]);
            maxZ = Math.max(maxZ, tree.zs()[i]);
        }
        return new Placed(tree.xs(), ys, tree.zs(), tree.states(), holds, minX, minY, minZ, maxX, maxY, maxZ, null);
    }

    /** A key for one column of one slice of a lying log. */
    private static long column(int slice, int x, int z) {
        return ((long) slice << 40) ^ ((long) (x & 0xFFFFF) << 20) ^ (z & 0xFFFFF);
    }

    /**
     * The ground in a loaded world, from the foot's height: down a column to the first block that is neither open nor a
     * tree's own (wood, leaves, mud, fallen branches: a falling tree's own crown is not ground), or, where the foot's height
     * is inside a hill, up to the first free block; water counts as ground (a log floats). A column in land not loaded
     * counts as level with the foot.
     */
    public static Ground ground(net.minecraft.world.level.Level level, BlockPos foot) {
        return (x, z) -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, foot.getY(), z);
            if (!level.hasChunkAt(pos)) {
                return foot.getY();
            }
            if (isGround(level.getBlockState(pos))) {
                for (int y = foot.getY() + 1; y <= foot.getY() + GROUND_SEARCH; y++) {
                    pos.setY(y);
                    if (!isGround(level.getBlockState(pos))) {
                        return y;
                    }
                }
                return foot.getY() + GROUND_SEARCH;
            }
            for (int y = foot.getY() - 1; y >= foot.getY() - GROUND_SEARCH; y--) {
                pos.setY(y);
                if (isGround(level.getBlockState(pos))) {
                    return y + 1;
                }
            }
            return foot.getY();
        };
    }

    /** Ground a fallen log may lie on: solid and not a tree's own, or water. */
    private static boolean isGround(BlockState state) {
        return !state.getFluidState().isEmpty() || !isOpen(state) && !isTreeOwn(state);
    }

    /** A giant tree's own block (or any tree's leaves and logs): not ground for a fallen log to rest on. */
    private static boolean isTreeOwn(BlockState state) {
        return state.is(TreeBlocks.TREE_WOOD) || state.is(TreeBlocks.TREE_LEAVES) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.getBlock() instanceof FallenBranchBlock || state.getBlock() instanceof MudCrustBlock
                || state.getBlock() instanceof TreeBranchBlock;
    }

    /** The tree of this kind, seed, age and stage worked out into blocks (kept for a while). */
    public static Placed placed(String kindName, long seed, double age, TreeStage stage) {
        return placed(kindName, seed, age, stage, TreeLife.fullStep(stage));
    }

    /**
     * The tree of this kind, seed, age and stage, so many steps into its stage's slow change (step 5b, see
     * {@link TreeLife#deadStep}: a dead tree's leaves withering and dropping, mud climbing, limbs breaking off, a dried
     * heap shrinking), worked out into blocks (kept for a while).
     */
    public static Placed placed(String kindName, long seed, double age, TreeStage stage, int step) {
        int s = stepOf(stage, step);
        String key = key(kindName, seed, age, stage, s);
        synchronized (CACHE) {
            Placed p = CACHE.get(key);
            if (p != null) {
                return p;
            }
        }
        Placed p = s < 0 ? work(kind(kindName), TreeShapes.build(kind(kindName), seed, age, stage, false, kindName.contains(HOLLOWED), kindName.endsWith(ROTTED)), stage) : between(kindName, seed, age, stage, s);
        synchronized (CACHE) {
            CACHE.put(key, p);
        }
        return p;
    }

    private static String key(String kindName, long seed, double age, TreeStage stage, int step) {
        return kindName + "|" + seed + "|" + age + "|" + stage.id + (step >= 0 ? "|" + step : "");
    }

    /** The step of a slow change worked out, or -1 where it is the stage's own shape. */
    private static int stepOf(TreeStage stage, int step) {
        return !gradual(stage) || step == TreeLife.fullStep(stage) ? -1 : step;
    }

    private static final java.util.Set<String> WORKING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * The tree worked out into blocks if it is ready; if not, it is worked out in the background (a grown giant's shape
     * takes tens of milliseconds, too long for one server tick) and this returns null: ask again later.
     */
    public static Placed placedSoon(String kindName, long seed, double age, TreeStage stage, int step) {
        int s = stepOf(stage, step);
        String key = key(kindName, seed, age, stage, s);
        synchronized (CACHE) {
            Placed p = CACHE.get(key);
            if (p != null) {
                return p;
            }
        }
        if (WORKING.add(key)) {
            net.minecraft.Util.backgroundExecutor().execute(() -> {
                try {
                    placed(kindName, seed, age, stage, step);
                } finally {
                    WORKING.remove(key);
                }
            });
        }
        return null;
    }

    /**
     * Dead wood lying loose on the ground, not part of any tree (M7.5 step 6c, {@code tree.Litter}): every block a loose
     * piece, to be laid on the land with {@link #rest}.
     */
    public static Placed litter(TreeVoxels v) {
        return work(kind("umbrella_thorn"), v, TreeStage.FALLEN);
    }

    private static Placed work(TreeKind kind, TreeVoxels v, TreeStage stage) {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<EnumSet<Direction>> plates = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<TreePart> e : v.entries()) {
            long k = e.getLongKey();
            BlockState state = state(kind, e.getValue(), v.isOverBark(k));
            if (state != null) {
                states.put(k, state);
            }
        }
        for (Long2ObjectMap.Entry<TreePart> e : v.entries()) {
            long k = e.getLongKey();
            BlockState state = states.get(k);
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            if (state == null) {
                continue;
            }
            if (state.getBlock() instanceof TreeBranchBlock) {
                // A thin branch reaches toward every piece of wood or leaves it touches.
                for (Direction d : Direction.values()) {
                    BlockState n = states.get(BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()));
                    state = state.setValue(TreeBranchBlock.PROPERTY_BY_DIRECTION.get(d), n != null && TreeBranchBlock.joins(n));
                }
                states.put(k, state);
            } else if (e.getValue() == TreePart.FALLEN_BRANCH) {
                states.put(k, fallenBranch(v, x, y, z));
            }
            if (e.getValue() == TreePart.MUD && v.isOverBark(k)) {
                // Termite mud over the dead bark: a plate on the wood on each side where it meets the air.
                for (Direction d : Direction.values()) {
                    long out = BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                    if (d != Direction.DOWN && v.get(out) == null) {
                        plates.computeIfAbsent(out, o -> EnumSet.noneOf(Direction.class)).add(d.getOpposite());
                    }
                }
            } else if (e.getValue() == TreePart.MUD) {
                // Mud round the foot on the ground: a plate on each piece of dead wood it touches (none: no mud).
                states.remove(k);
                for (Direction d : Direction.values()) {
                    BlockState n = states.get(BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()));
                    if (d != Direction.DOWN && n != null && n.is(TreeBlocks.TERMITE_WOOD)) {
                        plates.computeIfAbsent(k, o -> EnumSet.noneOf(Direction.class)).add(d);
                    }
                }
            }
        }
        taper(states);
        BlockState crust = SavannaBlocks.MUD_CRUST.get().defaultBlockState();
        for (Long2ObjectMap.Entry<EnumSet<Direction>> e : plates.long2ObjectEntrySet()) {
            if (states.containsKey(e.getLongKey())) {
                continue;
            }
            BlockState plate = crust;
            for (Direction d : e.getValue()) {
                plate = plate.setValue(MudCrustBlock.FACES.get(d), true);
            }
            states.put(e.getLongKey(), plate);
        }
        return build(states, k -> {
            TreePart part = v.get(k);
            return BlockPos.getY(k) <= 0 && part != null && part.isWood() && part != TreePart.FALLEN_BRANCH
                    && part != TreePart.THIN && part != TreePart.DEAD_THIN
                    && part != TreePart.LIMB && part != TreePart.DEAD_LIMB;
        }, v.lying() == null ? null : lying(v), v::isLoose);
    }

    /** Each block's slice of a fallen log (see {@link Lying}), coded as slice * 2, plus 1 for a block on its middle line. */
    private static java.util.function.LongToIntFunction lying(TreeVoxels v) {
        java.util.List<double[]> logs = v.lying();
        LongOpenHashSet stumpColumns = new LongOpenHashSet();
        for (Long2ObjectMap.Entry<TreePart> e : v.entries()) {
            if (v.isStump(e.getLongKey())) {
                stumpColumns.add(BlockPos.asLong(BlockPos.getX(e.getLongKey()), 0, BlockPos.getZ(e.getLongKey())));
            }
        }
        return k -> {
            TreePart part = v.get(k);
            int x = BlockPos.getX(k), z = BlockPos.getZ(k);
            if (v.isStump(k) || part == null && stumpColumns.contains(BlockPos.asLong(x, 0, z))) {
                return FIXED; // the stump, and the mud plates on it
            }
            if (part == TreePart.FALLEN_BRANCH) {
                return LOOSE;
            }
            // The log it belongs to: the nearest middle line.
            int best = 0;
            double bestD = Double.MAX_VALUE, bestT = 0, bestLen = 1;
            for (int l = 0; l < logs.size(); l++) {
                double[] g = logs.get(l);
                double ux = g[2] - g[0], uz = g[3] - g[1], len = Math.max(1e-6, Math.hypot(ux, uz));
                double t = Math.max(0, Math.min(len, ((x - g[0]) * ux + (z - g[1]) * uz) / len));
                double d = Math.hypot(x - (g[0] + ux / len * t), z - (g[1] + uz / len * t));
                if (d < bestD - 1e-9) {
                    best = l;
                    bestD = d;
                    bestT = t;
                    bestLen = len;
                }
            }
            if (logs.isEmpty()) {
                return FIXED;
            }
            int slice = best * SLICES + Math.min(SLICES - 1, (int) Math.floor(Math.min(bestT, bestLen)));
            boolean core = part != null && part != TreePart.MUD && bestD <= 1.0;
            return slice * 2 + (core ? 1 : 0);
        };
    }

    /** A worked-out tree from its blocks; {@code holding}: the ones that hold it up (the trunk goes down from them). */
    private static Placed build(Long2ObjectOpenHashMap<BlockState> states, LongPredicate holding) {
        return build(states, holding, null, k -> false);
    }

    /** As above; {@code lying} (null: not lying): a fallen tree's slices, coded as in {@link #lying(TreeVoxels)}. */
    private static Placed build(Long2ObjectOpenHashMap<BlockState> states, LongPredicate holding, java.util.function.LongToIntFunction lying,
                                LongPredicate marked) {
        int n = states.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        BlockState[] out = new BlockState[n];
        boolean[] holds = new boolean[n];
        LongOpenHashSet loose = loose(states);
        for (long k : states.keySet()) {
            if (marked.test(k)) {
                loose.add(k);
            }
        }
        boolean fits = lying != null || !loose.isEmpty();
        int[] slice = fits ? new int[n] : null;
        boolean[] core = fits ? new boolean[n] : null;
        int minX = 0, minY = -1, minZ = 0, maxX = 0, maxY = 0, maxZ = 0, i = 0;
        for (Long2ObjectMap.Entry<BlockState> e : states.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            xs[i] = BlockPos.getX(k);
            ys[i] = BlockPos.getY(k);
            zs[i] = BlockPos.getZ(k);
            out[i] = e.getValue();
            holds[i] = holding.test(k) && !loose.contains(k);
            if (fits) {
                int code = loose.contains(k) ? LOOSE : lying == null ? FIXED : lying.applyAsInt(k);
                slice[i] = code < 0 ? code : code / 2;
                core[i] = code >= 0 && code % 2 == 1;
            }
            minX = Math.min(minX, xs[i]);
            minY = Math.min(minY, ys[i]);
            minZ = Math.min(minZ, zs[i]);
            maxX = Math.max(maxX, xs[i]);
            maxY = Math.max(maxY, ys[i]);
            maxZ = Math.max(maxZ, zs[i]);
            i++;
        }
        return new Placed(xs, ys, zs, out, holds, minX, minY, minZ, maxX, maxY, maxZ, fits ? new Lying(slice, core) : null);
    }

    /** How high a loose piece on the ground may reach (a thick fallen limb, a fallen branch). */
    private static final int LOOSE_HEIGHT = 3;

    /**
     * The pieces lying loose on the ground round a dead tree (broken limbs, fallen branches): low blocks joined (face to
     * face) neither to the tree's foot nor to its biggest piece (a fallen log). They rest on the ground under them.
     */
    private static LongOpenHashSet loose(Long2ObjectOpenHashMap<BlockState> states) {
        LongOpenHashSet body = new LongOpenHashSet();
        java.util.List<LongOpenHashSet> pieces = new java.util.ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (long start : states.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            LongOpenHashSet piece = new LongOpenHashSet();
            LongArrayList todo = new LongArrayList();
            todo.add(start);
            boolean foot = false;
            while (!todo.isEmpty()) {
                long k = todo.removeLong(todo.size() - 1);
                piece.add(k);
                int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                foot |= y <= 0 && x * x + z * z <= 16;
                for (Direction d : Direction.values()) {
                    long nb = BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                    if (states.containsKey(nb) && seen.add(nb)) {
                        todo.add(nb);
                    }
                }
            }
            if (foot) {
                body.addAll(piece);
            } else {
                pieces.add(piece);
            }
        }
        LongOpenHashSet loose = new LongOpenHashSet();
        LongOpenHashSet biggest = pieces.stream().max(java.util.Comparator.comparingInt(LongOpenHashSet::size)).orElse(null);
        for (LongOpenHashSet piece : pieces) {
            boolean low = true;
            for (long k : piece) {
                low &= BlockPos.getY(k) <= LOOSE_HEIGHT;
            }
            if (low && (piece != biggest || body.size() > piece.size())) {
                loose.addAll(piece);
            }
        }
        return loose;
    }

    // ---------------------------------------------------------------- step 5b: a dead tree's slow changes

    /** The stages with a slow change in steps (a dead tree's withering day, mud climbing, limbs breaking, a heap shrinking). */
    private static boolean gradual(TreeStage stage) {
        return stage == TreeStage.LIVING || stage == TreeStage.MUD || stage == TreeStage.SHED || stage == TreeStage.HEAP_DRY;
    }

    private static final Placed NOTHING = new Placed(new int[0], new int[0], new int[0], new BlockState[0], new boolean[0], 0, -1, 0, 0, 0, 0, null);

    /** The order the blocks of a slow change change in. */
    private enum Order {
        /** From the ground up (termite mud climbing a snag), a ragged front. */
        UP,
        /** From the top down (a heap shrinking). */
        DOWN,
        /** A limb with its branches at a time (the limbs of a dead tree breaking off one by one). */
        LIMBS
    }

    private static Placed between(String kindName, long seed, double age, TreeStage stage, int step) {
        double f = step / (double) TreeLife.STEPS;
        long salt = seed * 0x9E3779B97F4A7C15L + stage.ordinal();
        return switch (stage) {
            case LIVING -> withering(kind(kindName), placed(kindName, seed, age, TreeStage.LIVING), f);
            case MUD -> blend(placed(kindName, seed, age, TreeStage.SNAG), placed(kindName, seed, age, TreeStage.MUD), f, Order.UP, salt);
            case SHED -> blend(placed(kindName, seed, age, TreeStage.MUD), placed(kindName, seed, age, TreeStage.SHED), f, Order.LIMBS, salt);
            // A dried heap shrinks to a low remnant by the end of its stage (then it is gone).
            case HEAP_DRY -> blend(placed(kindName, seed, age, TreeStage.HEAP_DRY), NOTHING, 0.8 * f, Order.DOWN, salt);
            default -> placed(kindName, seed, age, stage);
        };
    }

    /**
     * The day a tree dies: its leaves turn brown, each at its own moment, and drop a little later (real: a dead acacia's
     * fine leaflets dry and fall within days; squeezed into the death day). {@code f} from 0 (green) to 1 (all dropped).
     */
    private static Placed withering(TreeKind kind, Placed living, double f) {
        BlockState dead = block(kind.blocks().leaves().replace("_leaves", "_dead_leaves"));
        Long2ObjectOpenHashMap<BlockState> out = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet holds = new LongOpenHashSet();
        for (int i = 0; i < living.size(); i++) {
            long k = BlockPos.asLong(living.xs()[i], living.ys()[i], living.zs()[i]);
            BlockState state = living.states()[i];
            if (state.getBlock() instanceof TreeLeavesBlock) {
                double h = hash01(k);
                // Each leaf browns at its own moment and drops 4 tenths of the day later.
                if (f >= 0.6 * h + 0.4) {
                    continue; // dropped
                }
                if (f >= 0.6 * h) {
                    state = dead;
                }
            }
            out.put(k, state);
            if (living.holds()[i]) {
                holds.add(k);
            }
        }
        joinBranches(out);
        return build(out, holds::contains, null, looseOf(living)::contains);
    }

    /** The blocks of a worked-out tree that are loose pieces on the ground (see {@link Lying}). */
    private static LongOpenHashSet looseOf(Placed p) {
        LongOpenHashSet out = new LongOpenHashSet();
        if (p.lying() != null) {
            for (int i = 0; i < p.size(); i++) {
                if (p.lying().slice()[i] == LOOSE) {
                    out.add(BlockPos.asLong(p.xs()[i], p.ys()[i], p.zs()[i]));
                }
            }
        }
        return out;
    }

    /**
     * Part way ({@code f}, 0 to 1) from one shape to the next: each block that differs changes at its own moment, in the
     * {@code order} of the change.
     */
    private static Placed blend(Placed a, Placed b, double f, Order order, long salt) {
        Long2ObjectOpenHashMap<BlockState> as = statesOf(a), bs = statesOf(b);
        LongOpenHashSet ah = holdsOf(a), bh = holdsOf(b);
        LongOpenHashSet keys = new LongOpenHashSet(as.keySet());
        keys.addAll(bs.keySet());
        LongArrayList diffs = new LongArrayList();
        for (long k : keys) {
            if (!java.util.Objects.equals(as.get(k), bs.get(k))) {
                diffs.add(k);
            }
        }
        Long2DoubleOpenHashMap when = order == Order.LIMBS ? byLimb(diffs, as, bs, salt) : byHeight(diffs, order == Order.UP, salt);
        Long2ObjectOpenHashMap<BlockState> out = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet holds = new LongOpenHashSet();
        for (long k : keys) {
            boolean next = when.containsKey(k) && when.get(k) < f;
            BlockState state = next ? bs.get(k) : as.get(k);
            if (state == null) {
                continue;
            }
            out.put(k, state);
            if (next ? bh.contains(k) : ah.contains(k)) {
                holds.add(k);
            }
        }
        joinBranches(out);
        LongOpenHashSet loose = looseOf(a);
        loose.addAll(looseOf(b));
        return build(out, holds::contains, null, loose::contains);
    }

    /** Each changing block's moment (0 to 1) by its height, a little ragged. */
    private static Long2DoubleOpenHashMap byHeight(LongArrayList diffs, boolean up, long salt) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (long k : diffs) {
            lo = Math.min(lo, BlockPos.getY(k));
            hi = Math.max(hi, BlockPos.getY(k));
        }
        Long2DoubleOpenHashMap when = new Long2DoubleOpenHashMap();
        for (long k : diffs) {
            int y = BlockPos.getY(k);
            double rank = up ? y - lo : hi - y;
            when.put(k, (rank + 0.9 * hash01(k ^ salt)) / (hi - lo + 1));
        }
        return when;
    }

    /**
     * Each changing block's moment by its limb: the wood that goes up high falls apart into limbs (pieces joined to each
     * other), which go one after another, spread evenly over the change in an order by the tree's dice; every other
     * change (the fallen pieces on the ground, the plates and branch ends round a break) comes with the nearest limb.
     */
    private static Long2DoubleOpenHashMap byLimb(LongArrayList diffs, Long2ObjectOpenHashMap<BlockState> as,
                                                 Long2ObjectOpenHashMap<BlockState> bs, long salt) {
        LongOpenHashSet broken = new LongOpenHashSet();
        for (long k : diffs) {
            if (BlockPos.getY(k) >= 2 && as.containsKey(k) && !bs.containsKey(k)) {
                broken.add(k);
            }
        }
        // The limbs: pieces of broken wood touching each other (corners too).
        java.util.List<LongArrayList> limbs = new java.util.ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (long start : broken) {
            if (!seen.add(start)) {
                continue;
            }
            LongArrayList limb = new LongArrayList();
            LongArrayList todo = new LongArrayList();
            todo.add(start);
            while (!todo.isEmpty()) {
                long k = todo.removeLong(todo.size() - 1);
                limb.add(k);
                int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            long n = BlockPos.asLong(x + dx, y + dy, z + dz);
                            if (broken.contains(n) && seen.add(n)) {
                                todo.add(n);
                            }
                        }
                    }
                }
            }
            limbs.add(limb);
        }
        // Their order: by the dice, spread evenly.
        double[] hash = new double[limbs.size()];
        Integer[] order = new Integer[limbs.size()];
        double[][] middle = new double[limbs.size()][2];
        for (int i = 0; i < limbs.size(); i++) {
            long first = Long.MAX_VALUE;
            for (long k : limbs.get(i)) {
                first = Math.min(first, k);
                middle[i][0] += BlockPos.getX(k);
                middle[i][1] += BlockPos.getZ(k);
            }
            middle[i][0] /= limbs.get(i).size();
            middle[i][1] /= limbs.get(i).size();
            hash[i] = hash01(first ^ salt);
            order[i] = i;
        }
        java.util.Arrays.sort(order, (p, q) -> Double.compare(hash[p], hash[q]));
        double[] moment = new double[limbs.size()];
        for (int rank = 0; rank < order.length; rank++) {
            moment[order[rank]] = (rank + 0.5) / order.length;
        }
        Long2DoubleOpenHashMap when = new Long2DoubleOpenHashMap();
        for (int i = 0; i < limbs.size(); i++) {
            for (long k : limbs.get(i)) {
                when.put(k, moment[i]);
            }
        }
        for (long k : diffs) {
            if (when.containsKey(k)) {
                continue;
            }
            int best = -1;
            double near = Double.MAX_VALUE;
            for (int i = 0; i < limbs.size(); i++) {
                double d = Math.hypot(BlockPos.getX(k) - middle[i][0], BlockPos.getZ(k) - middle[i][1]);
                if (d < near) {
                    near = d;
                    best = i;
                }
            }
            when.put(k, best < 0 ? hash01(k ^ salt) : moment[best]);
        }
        return when;
    }

    /** Thin branches reach only toward the wood and leaves still there (a break leaves no arm into the air). */
    private static void joinBranches(Long2ObjectOpenHashMap<BlockState> states) {
        for (Long2ObjectMap.Entry<BlockState> e : states.long2ObjectEntrySet()) {
            BlockState state = e.getValue();
            if (!(state.getBlock() instanceof TreeBranchBlock)) {
                continue;
            }
            long k = e.getLongKey();
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            for (Direction d : Direction.values()) {
                BlockState n = states.get(BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()));
                state = state.setValue(TreeBranchBlock.PROPERTY_BY_DIRECTION.get(d), n != null && TreeBranchBlock.joins(n));
            }
            e.setValue(state);
        }
    }

    private static Long2ObjectOpenHashMap<BlockState> statesOf(Placed p) {
        Long2ObjectOpenHashMap<BlockState> m = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < p.size(); i++) {
            m.put(BlockPos.asLong(p.xs()[i], p.ys()[i], p.zs()[i]), p.states()[i]);
        }
        return m;
    }

    private static LongOpenHashSet holdsOf(Placed p) {
        LongOpenHashSet m = new LongOpenHashSet();
        for (int i = 0; i < p.size(); i++) {
            if (p.holds()[i]) {
                m.add(BlockPos.asLong(p.xs()[i], p.ys()[i], p.zs()[i]));
            }
        }
        return m;
    }

    /** A number from 0 to 1 (never 1) for a place: the same every time. */
    private static double hash01(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * Tapering branches (TaperingBranchBlock) thin out away from the wood, as a player builds them: step 0 where a
     * branch meets a log, one step more for each branch block along; a branch touching no log is the thinnest.
     */
    private static void taper(Long2ObjectOpenHashMap<BlockState> states) {
        Long2IntOpenHashMap steps = new Long2IntOpenHashMap();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (Long2ObjectMap.Entry<BlockState> e : states.long2ObjectEntrySet()) {
            if (e.getValue().getBlock() instanceof TaperingBranchBlock) {
                long k = e.getLongKey();
                for (Direction d : Direction.values()) {
                    BlockState n = states.get(BlockPos.offset(k, d));
                    if (n != null && n.is(TreeBlocks.TREE_WOOD) && !(n.getBlock() instanceof TreeBranchBlock)) {
                        steps.put(k, 0);
                        queue.enqueue(k);
                        break;
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            long k = queue.dequeueLong();
            Block block = states.get(k).getBlock();
            int next = steps.get(k) + 1;
            for (Direction d : Direction.values()) {
                long o = BlockPos.offset(k, d);
                BlockState n = states.get(o);
                if (n != null && n.is(block) && !steps.containsKey(o)) {
                    steps.put(o, next);
                    queue.enqueue(o);
                }
            }
        }
        for (Long2ObjectMap.Entry<BlockState> e : states.long2ObjectEntrySet()) {
            if (e.getValue().getBlock() instanceof TaperingBranchBlock) {
                int step = Math.min(steps.getOrDefault(e.getLongKey(), TaperingBranchBlock.MAX_STEP), TaperingBranchBlock.MAX_STEP);
                e.setValue(e.getValue().setValue(TaperingBranchBlock.STEP, step));
            }
        }
    }

    /** The real block for a part of a tree of this kind (null: nothing). */
    static BlockState state(TreeKind kind, TreePart part, boolean overBark) {
        return switch (part) {
            case SEEDLING -> block(kind.blocks().seedling());
            case THORNS -> TreeBlocks.THORNY_TWIG.get().defaultBlockState();
            case PODS -> TreeBlocks.CAMEL_THORN_PODS.get().defaultBlockState();
            case LEAVES -> block(kind.blocks().leaves());
            case THIN, LIMB -> block(kind.blocks().branch());
            case DEAD_THIN, DEAD_LIMB -> TreeBlocks.BLEACHED_BRANCH.get().defaultBlockState();
            case FALLEN_BRANCH -> SavannaBlocks.FALLEN_BRANCH.get().defaultBlockState();
            case FRASS -> TreeBlocks.FRASS.get().defaultBlockState();
            case INNER -> block(kind.blocks().log());
            case BARK -> block(kind.blocks().wood());
            case DEAD_INNER -> TreeBlocks.BLEACHED_LOG.get().defaultBlockState();
            case DEAD_BARK -> TreeBlocks.BLEACHED_WOOD.get().defaultBlockState();
            case PEELING -> TreeBlocks.PEELING_BLEACHED_LOG.get().defaultBlockState();
            case FIBRE -> TreeBlocks.BAOBAB_FIBRE.get().defaultBlockState();
            case ROTTEN -> TreeBlocks.ROTTEN_WOOD.get().defaultBlockState();
            // Mud over bark: the dead wood stays under it (the plates go beside it); mud on the ground: plates only.
            case MUD -> overBark ? TreeBlocks.BLEACHED_WOOD.get().defaultBlockState() : SavannaBlocks.MUD_CRUST.get().defaultBlockState();
        };
    }

    private static BlockState block(String id) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        BlockState state = block.defaultBlockState();
        return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : state;
    }

    /** A fallen branch lying along its neighbours (the way the line of branch runs), thinner toward its ends. */
    private static BlockState fallenBranch(TreeVoxels v, int x, int y, int z) {
        boolean east = v.get(x + 1, y, z) == TreePart.FALLEN_BRANCH, west = v.get(x - 1, y, z) == TreePart.FALLEN_BRANCH;
        boolean south = v.get(x, y, z + 1) == TreePart.FALLEN_BRANCH, north = v.get(x, y, z - 1) == TreePart.FALLEN_BRANCH;
        int alongX = (east ? 1 : 0) + (west ? 1 : 0), alongZ = (south ? 1 : 0) + (north ? 1 : 0);
        Direction.Axis axis = alongZ > alongX ? Direction.Axis.Z : Direction.Axis.X;
        int along = Math.max(alongX, alongZ);
        FallenBranchBlock.Part piece = along >= 2 ? FallenBranchBlock.Part.MIDDLE : FallenBranchBlock.Part.TIP;
        return SavannaBlocks.FALLEN_BRANCH.get().defaultBlockState().setValue(FallenBranchBlock.AXIS, axis).setValue(FallenBranchBlock.PART, piece);
    }

    /**
     * Puts the tree into the world with its foot at {@code foot}, only the blocks inside {@code clip} (one chunk at
     * world generation). Returns how many blocks it placed.
     */
    public static int place(LevelAccessor level, Placed tree, BlockPos foot, BoundingBox clip, int flags) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int i = 0; i < tree.size(); i++) {
            pos.set(foot.getX() + tree.xs()[i], foot.getY() + tree.ys()[i], foot.getZ() + tree.zs()[i]);
            if (!clip.isInside(pos)) {
                continue;
            }
            BlockState state = tree.states()[i];
            BlockState there = level.getBlockState(pos);
            boolean ground = tree.ys()[i] < 0;
            if (ground ? !isSoil(there) && !isOpen(there) : !isOpen(there)) {
                continue;
            }
            if (state.getBlock() instanceof FallenBranchBlock && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                continue;
            }
            level.setBlock(pos, state, flags);
            placed++;
            if (tree.holds()[i]) {
                // The ground dips under the foot: the trunk goes down to meet it.
                for (int d = 1; d <= MAX_FILL_DOWN; d++) {
                    pos.move(Direction.DOWN);
                    if (!clip.isInside(pos) || !isOpen(level.getBlockState(pos))) {
                        break;
                    }
                    level.setBlock(pos, state, flags);
                    placed++;
                }
            }
        }
        return placed;
    }

    /**
     * Grows a tree in the world from one age to the next (M7.5 step 4): only the blocks that differ between the two
     * shapes change. A new block goes only into air, grass, flowers or snow (soil for the roots), or where the old shape
     * put this tree's own block that is still there; never into a player's block, a colony's block or another tree (a
     * crown simply grows round what is in its way; my pick, to confirm). A block of the old shape the new one no longer
     * has (a twig the young tree sheds, leaves moving out as the crown widens) goes, if it is still the tree's own. The
     * places of shed wood are added to {@code shed}. {@code from} null: nothing of the tree is in the world yet.
     * Returns how many blocks changed.
     */
    public static int grow(LevelAccessor level, Placed from, Placed to, BlockPos foot, int flags, java.util.List<BlockPos> shed) {
        return grow(level, from, to, foot, flags, shed, null);
    }

    // ---------------------------------------------------------------- step 5d: charred and scorched

    /** The charred block a giant tree's wood turns to in a fire (null: not wood that chars). */
    public static Block charredOf(Block b) {
        Block plain = com.antcolonies.savanna.tree.RotMarks.base(b);
        if (plain != b) {
            // A heart rot mark stays on the charred bark.
            Block c = charredOf(plain);
            return c == null ? null : com.antcolonies.savanna.tree.RotMarks.marked(c, com.antcolonies.savanna.tree.RotMarks.mark(b));
        }
        if (b == TreeBlocks.CHARRED_LOG.get() || b == TreeBlocks.CHARRED_WOOD.get() || b == TreeBlocks.CHARRED_BRANCH.get()) {
            return null;
        }
        if (b == TreeBlocks.BLEACHED_LOG.get() || b == TreeBlocks.PEELING_BLEACHED_LOG.get()) {
            return TreeBlocks.CHARRED_LOG.get();
        }
        if (b == TreeBlocks.BLEACHED_WOOD.get()) {
            return TreeBlocks.CHARRED_WOOD.get();
        }
        if (b == TreeBlocks.BLEACHED_BRANCH.get()) {
            return TreeBlocks.CHARRED_BRANCH.get();
        }
        for (TreeBlocks.Kind k : TreeBlocks.KINDS) {
            if (b == k.log().get()) {
                return TreeBlocks.CHARRED_LOG.get();
            }
            if (b == k.wood().get()) {
                return TreeBlocks.CHARRED_WOOD.get();
            }
            if (b == k.branch().get()) {
                return TreeBlocks.CHARRED_BRANCH.get();
            }
        }
        return null;
    }

    /** Whether a block is burnt wood. */
    public static boolean isCharred(BlockState state) {
        Block b = com.antcolonies.savanna.tree.RotMarks.base(state.getBlock());
        return b == TreeBlocks.CHARRED_LOG.get() || b == TreeBlocks.CHARRED_WOOD.get() || b == TreeBlocks.CHARRED_BRANCH.get();
    }

    /** A wood block charred (its axis or branch arms kept); itself if it does not char. */
    public static BlockState charred(BlockState state) {
        Block c = charredOf(state.getBlock());
        return c == null ? state : c.withPropertiesOf(state);
    }

    /** The withered leaves green leaves turn to when scorched (null: not a giant tree's green leaves). */
    public static Block scorchedOf(Block b) {
        for (TreeBlocks.Kind k : TreeBlocks.KINDS) {
            if (b == k.leaves().get()) {
                return k.deadLeaves().get();
            }
        }
        return null;
    }

    /**
     * Whether a block in the world is still the tree's own block of its shape: the block itself, or it charred or
     * scorched by a fire (a scar is still the tree). Wood termites have started eating (step 6d) is theirs now: the tree
     * book leaves it alone (never grown over or taken away), as it does their mud casts.
     */
    public static boolean same(BlockState there, BlockState was) {
        if (com.antcolonies.savanna.block.DeadTreeWoodBlock.isEaten(there)) {
            return false;
        }
        Block b = was.getBlock(), t = com.antcolonies.savanna.tree.RotMarks.base(there.getBlock()); // a heart rot mark is the same wood
        return t == b || t == charredOf(b) || t == scorchedOf(b);
    }

    /** Charred shapes: a tree killed by fire stands and lies black, not silver (kept as long as their shape is). */
    private static final Map<Placed, Placed> CHARRED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** A shape with all its dead wood charred (a tree killed by fire, step 5d). */
    public static Placed charred(Placed p) {
        if (p == null) {
            return null;
        }
        return CHARRED.computeIfAbsent(p, q -> {
            BlockState[] states = q.states().clone();
            for (int i = 0; i < states.length; i++) {
                Block b = states[i].getBlock();
                if (b == TreeBlocks.BLEACHED_LOG.get() || b == TreeBlocks.PEELING_BLEACHED_LOG.get() || b == TreeBlocks.BLEACHED_WOOD.get()
                        || b == TreeBlocks.BLEACHED_BRANCH.get()) {
                    states[i] = charred(states[i]);
                }
            }
            return new Placed(q.xs(), q.ys(), q.zs(), states, q.holds(), q.minX(), q.minY(), q.minZ(), q.maxX(), q.maxY(), q.maxZ(), q.lying());
        });
    }

    /** As {@link #grow}, and every block that goes is told to {@code gone} (its place and what it was), if not null. */
    public static int grow(LevelAccessor level, Placed from, Placed to, BlockPos foot, int flags, java.util.List<BlockPos> shed,
                           java.util.function.BiConsumer<BlockPos, BlockState> gone) {
        Long2ObjectOpenHashMap<BlockState> old = new Long2ObjectOpenHashMap<>();
        if (from != null) {
            for (int i = 0; i < from.size(); i++) {
                old.put(BlockPos.asLong(from.xs()[i], from.ys()[i], from.zs()[i]), from.states()[i]);
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        it.unimi.dsi.fastutil.longs.LongOpenHashSet kept = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (int i = 0; i < to.size(); i++) {
            long k = BlockPos.asLong(to.xs()[i], to.ys()[i], to.zs()[i]);
            kept.add(k);
            BlockState state = to.states()[i], was = old.get(k);
            if (state.equals(was)) {
                continue;
            }
            pos.set(foot.getX() + to.xs()[i], foot.getY() + to.ys()[i], foot.getZ() + to.zs()[i]);
            BlockState there = level.getBlockState(pos);
            boolean own = was != null && same(there, was);
            boolean ground = to.ys()[i] < 0;
            if (!own && (ground ? !isSoil(there) && !isOpen(there) : !isOpen(there))) {
                continue;
            }
            if (state.getBlock() instanceof FallenBranchBlock && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                continue;
            }
            if (own && isCharred(there) && charredOf(was.getBlock()) != null) {
                state = charred(state); // a fire's scar stays as the wood changes (step 5d)
            }
            if (own) {
                state = com.antcolonies.savanna.tree.RotMarks.keep(there, state); // and a heart rot mark
            }
            level.setBlock(pos, state, flags);
            changed++;
            if (to.holds()[i] && (was == null || !own)) {
                // The trunk widens over a dip in the ground: it goes down to meet it.
                for (int d = 1; d <= MAX_FILL_DOWN; d++) {
                    pos.move(Direction.DOWN);
                    if (!isOpen(level.getBlockState(pos))) {
                        break;
                    }
                    level.setBlock(pos, state, flags);
                    changed++;
                }
            }
        }
        for (Long2ObjectMap.Entry<BlockState> e : old.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            if (kept.contains(k)) {
                continue;
            }
            pos.set(foot.getX() + BlockPos.getX(k), foot.getY() + BlockPos.getY(k), foot.getZ() + BlockPos.getZ(k));
            if (!same(level.getBlockState(pos), e.getValue())) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
            changed++;
            if (e.getValue().getBlock() instanceof TreeBranchBlock) {
                shed.add(pos.immutable());
            }
            if (gone != null) {
                gone.accept(pos.immutable(), e.getValue());
            }
        }
        return changed;
    }

    /**
     * Takes a tree out of the world (a little one that died): every block of its shape that is still its own goes.
     * Returns how many.
     */
    public static int remove(LevelAccessor level, Placed tree, BlockPos foot, int flags) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int removed = 0;
        for (int i = 0; i < tree.size(); i++) {
            if (tree.ys()[i] < 0) {
                continue; // the roots stay in the ground
            }
            pos.set(foot.getX() + tree.xs()[i], foot.getY() + tree.ys()[i], foot.getZ() + tree.zs()[i]);
            if (same(level.getBlockState(pos), tree.states()[i])) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                removed++;
            }
        }
        return removed;
    }

    /** Air, or a plant or snow a tree may grow through (grass, flowers, dry grass); never water. */
    public static boolean isOpen(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /** Soil a tree's roots may take at its foot. */
    private static boolean isSoil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
    }
}
