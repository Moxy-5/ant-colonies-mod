package com.antcolonies.worldgen;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.antcolonies.block.FallenBranchBlock;
import com.antcolonies.block.MudCrustBlock;
import com.antcolonies.block.TreeBranchBlock;
import com.antcolonies.block.TreeLeavesBlock;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.TreeBlocks;
import com.antcolonies.tree.TreeKind;
import com.antcolonies.tree.TreeLife;
import com.antcolonies.tree.TreePart;
import com.antcolonies.tree.TreeShapes;
import com.antcolonies.tree.TreeStage;
import com.antcolonies.tree.TreeVoxels;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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

    /** A tree's kind by name (umbrella_thorn, camel_thorn, baobab), read once. */
    public static TreeKind kind(String name) {
        synchronized (KINDS) {
            return KINDS.computeIfAbsent(name, TreeKind::load);
        }
    }

    /**
     * A tree worked out into blocks, around its foot at (0, 0, 0): each block's place and state, which ones hold the
     * tree up (the trunk goes down from them to meet the ground), and the mud plates beside the wood.
     */
    public record Placed(int[] xs, int[] ys, int[] zs, BlockState[] states, boolean[] holds, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ) {
        public int size() {
            return states.length;
        }

        /** The box round the tree with its foot at {@code foot}, room for the trunk to go down included. */
        public BoundingBox box(BlockPos foot) {
            return new BoundingBox(foot.getX() + minX, foot.getY() + minY - MAX_FILL_DOWN, foot.getZ() + minZ,
                    foot.getX() + maxX, foot.getY() + maxY, foot.getZ() + maxZ);
        }
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
        Placed p = s < 0 ? work(kind(kindName), TreeShapes.build(kind(kindName), seed, age, stage), stage) : between(kindName, seed, age, stage, s);
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
        BlockState crust = ModBlocks.MUD_CRUST.get().defaultBlockState();
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
        });
    }

    /** A worked-out tree from its blocks; {@code holding}: the ones that hold it up (the trunk goes down from them). */
    private static Placed build(Long2ObjectOpenHashMap<BlockState> states, LongPredicate holding) {
        int n = states.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        BlockState[] out = new BlockState[n];
        boolean[] holds = new boolean[n];
        int minX = 0, minY = -1, minZ = 0, maxX = 0, maxY = 0, maxZ = 0, i = 0;
        for (Long2ObjectMap.Entry<BlockState> e : states.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            xs[i] = BlockPos.getX(k);
            ys[i] = BlockPos.getY(k);
            zs[i] = BlockPos.getZ(k);
            out[i] = e.getValue();
            holds[i] = holding.test(k);
            minX = Math.min(minX, xs[i]);
            minY = Math.min(minY, ys[i]);
            minZ = Math.min(minZ, zs[i]);
            maxX = Math.max(maxX, xs[i]);
            maxY = Math.max(maxY, ys[i]);
            maxZ = Math.max(maxZ, zs[i]);
            i++;
        }
        return new Placed(xs, ys, zs, out, holds, minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ---------------------------------------------------------------- step 5b: a dead tree's slow changes

    /** The stages with a slow change in steps (a dead tree's withering day, mud climbing, limbs breaking, a heap shrinking). */
    private static boolean gradual(TreeStage stage) {
        return stage == TreeStage.LIVING || stage == TreeStage.MUD || stage == TreeStage.SHED || stage == TreeStage.HEAP_DRY;
    }

    private static final Placed NOTHING = new Placed(new int[0], new int[0], new int[0], new BlockState[0], new boolean[0], 0, -1, 0, 0, 0, 0);

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
        return build(out, holds::contains);
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
        return build(out, holds::contains);
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

    /** The real block for a part of a tree of this kind (null: nothing). */
    static BlockState state(TreeKind kind, TreePart part, boolean overBark) {
        return switch (part) {
            case SEEDLING -> block(kind.blocks().seedling());
            case THORNS -> TreeBlocks.THORNY_TWIG.get().defaultBlockState();
            case PODS -> TreeBlocks.CAMEL_THORN_PODS.get().defaultBlockState();
            case LEAVES -> block(kind.blocks().leaves());
            case THIN, LIMB -> block(kind.blocks().branch());
            case DEAD_THIN, DEAD_LIMB -> TreeBlocks.BLEACHED_BRANCH.get().defaultBlockState();
            case FALLEN_BRANCH -> ModBlocks.FALLEN_BRANCH.get().defaultBlockState();
            case FRASS -> TreeBlocks.FRASS.get().defaultBlockState();
            case INNER -> block(kind.blocks().log());
            case BARK -> block(kind.blocks().wood());
            case DEAD_INNER -> TreeBlocks.BLEACHED_LOG.get().defaultBlockState();
            case DEAD_BARK -> TreeBlocks.BLEACHED_WOOD.get().defaultBlockState();
            case PEELING -> TreeBlocks.PEELING_BLEACHED_LOG.get().defaultBlockState();
            case FIBRE -> TreeBlocks.BAOBAB_FIBRE.get().defaultBlockState();
            case ROTTEN -> TreeBlocks.ROTTEN_WOOD.get().defaultBlockState();
            // Mud over bark: the dead wood stays under it (the plates go beside it); mud on the ground: plates only.
            case MUD -> overBark ? TreeBlocks.BLEACHED_WOOD.get().defaultBlockState() : ModBlocks.MUD_CRUST.get().defaultBlockState();
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
        return ModBlocks.FALLEN_BRANCH.get().defaultBlockState().setValue(FallenBranchBlock.AXIS, axis).setValue(FallenBranchBlock.PART, piece);
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
        return state.is(TreeBlocks.CHARRED_LOG.get()) || state.is(TreeBlocks.CHARRED_WOOD.get()) || state.is(TreeBlocks.CHARRED_BRANCH.get());
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
     * scorched by a fire (a scar is still the tree).
     */
    public static boolean same(BlockState there, BlockState was) {
        Block b = was.getBlock();
        return there.is(b) || there.getBlock() == charredOf(b) || there.getBlock() == scorchedOf(b);
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
            return new Placed(q.xs(), q.ys(), q.zs(), states, q.holds(), q.minX(), q.minY(), q.minZ(), q.maxX(), q.maxY(), q.maxZ());
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
