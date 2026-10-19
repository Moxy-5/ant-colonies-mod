package com.antcolonies.worldgen;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.antcolonies.block.FallenBranchBlock;
import com.antcolonies.block.MudCrustBlock;
import com.antcolonies.block.TreeBranchBlock;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.TreeBlocks;
import com.antcolonies.tree.TreeKind;
import com.antcolonies.tree.TreePart;
import com.antcolonies.tree.TreeShapes;
import com.antcolonies.tree.TreeStage;
import com.antcolonies.tree.TreeVoxels;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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

    /** Air, or a plant or snow a tree may grow through (grass, flowers, dry grass); never water. */
    public static boolean isOpen(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /** Soil a tree's roots may take at its foot. */
    private static boolean isSoil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
    }
}
