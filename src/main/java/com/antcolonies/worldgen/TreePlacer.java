package com.antcolonies.worldgen;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.antcolonies.block.FallenBranchBlock;
import com.antcolonies.block.MudCrustBlock;
import com.antcolonies.block.TaperingBranchBlock;
import com.antcolonies.block.TreeBranchBlock;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.TreeBlocks;
import com.antcolonies.tree.TreeKind;
import com.antcolonies.tree.TreePart;
import com.antcolonies.tree.TreeShapes;
import com.antcolonies.tree.TreeStage;
import com.antcolonies.tree.TreeVoxels;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
        String key = kindName + "|" + seed + "|" + age + "|" + stage.id;
        synchronized (CACHE) {
            Placed p = CACHE.get(key);
            if (p != null) {
                return p;
            }
        }
        Placed p = work(kind(kindName), TreeShapes.build(kind(kindName), seed, age, stage), stage);
        synchronized (CACHE) {
            CACHE.put(key, p);
        }
        return p;
    }

    private static final java.util.Set<String> WORKING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * The tree worked out into blocks if it is ready; if not, it is worked out in the background (a grown giant's shape
     * takes tens of milliseconds, too long for one server tick) and this returns null: ask again later.
     */
    public static Placed placedSoon(String kindName, long seed, double age, TreeStage stage) {
        String key = kindName + "|" + seed + "|" + age + "|" + stage.id;
        synchronized (CACHE) {
            Placed p = CACHE.get(key);
            if (p != null) {
                return p;
            }
        }
        if (WORKING.add(key)) {
            net.minecraft.Util.backgroundExecutor().execute(() -> {
                try {
                    placed(kindName, seed, age, stage);
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
        taper(states);
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
            TreePart part = v.get(k);
            holds[i] = ys[i] <= 0 && part != null && part.isWood() && part != TreePart.FALLEN_BRANCH
                    && part != TreePart.THIN && part != TreePart.DEAD_THIN
                    && part != TreePart.LIMB && part != TreePart.DEAD_LIMB;
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
            boolean own = was != null && there.is(was.getBlock());
            boolean ground = to.ys()[i] < 0;
            if (!own && (ground ? !isSoil(there) && !isOpen(there) : !isOpen(there))) {
                continue;
            }
            if (state.getBlock() instanceof FallenBranchBlock && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                continue;
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
            if (!level.getBlockState(pos).is(e.getValue().getBlock())) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
            changed++;
            if (e.getValue().getBlock() instanceof TreeBranchBlock) {
                shed.add(pos.immutable());
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
            if (level.getBlockState(pos).is(tree.states()[i].getBlock())) {
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
