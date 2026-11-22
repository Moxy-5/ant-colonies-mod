package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hippo grass's floating mat (2026-11-22, {@code docs/plans/papyrus-and-hippo-grass.md} step 2): long grassy stems lying
 * on the water's surface out from the bank, like a lily pad a little higher and shaggier, short blades sticking up through
 * it. It sits on a water source as a lily pad does; no collision, so swimmers and waders pass through it.
 *
 * <p>{@link #REACH}: how many blocks out from its clump ({@link HippoGrassClumpBlock}) it lies, 1 next to the bank. Each
 * mat takes its {@link HippoGrassClumpBlock#STAGE} from the mat a ring nearer the bank (or the clump), so a patch turns
 * together, ring by ring outward. Young mats reach 1 block out, grown and flowering ones out to 1 to 3 (a ragged edge,
 * fixed per spot; the plan's fringe 1 to 3 wide); dying back, the mats go from the outside in, leaving the clump. A mat
 * with nothing nearer the bank keeps its stage (a player's mat on a pond stays as placed) unless it is dying back.
 */
public class HippoGrassBlock extends BushBlock {
    public static final MapCodec<HippoGrassBlock> CODEC = simpleCodec(HippoGrassBlock::new);
    public static final IntegerProperty STAGE = HippoGrassClumpBlock.STAGE;
    public static final IntegerProperty REACH = IntegerProperty.create("reach", 1, 3);
    /** The farthest a mat lies out from its clump (blocks; the plan's 1 to 3). */
    public static final int FARTHEST = 3;
    /** The chance a random tick of a dying mat with nothing farther out lets it go (about a game day for a whole patch). */
    static final float DIE_CHANCE = 1 / 2f;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public HippoGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, HippoGrassClumpBlock.GROWN).setValue(REACH, 1));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, REACH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** On a water source with nothing over the water, as a lily pad. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getFluidState(pos).getType() == Fluids.WATER && level.getFluidState(pos.above()).isEmpty();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        int reach = FARTHEST;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState there = context.getLevel().getBlockState(pos.relative(side));
            if (there.getBlock() instanceof HippoGrassClumpBlock) {
                reach = 1;
            } else if (there.getBlock() instanceof HippoGrassBlock) {
                reach = Math.min(reach, Math.min(FARTHEST, there.getValue(REACH) + 1));
            }
        }
        return defaultBlockState().setValue(REACH, reach);
    }

    /** The farthest out a mat may lie at {@code pos} while its patch is grown or flowering: 1 to 3, fixed per spot (a ragged edge). */
    public static int mostReachAt(BlockPos pos) {
        return 1 + (int) Math.floorMod(Mth.getSeed(pos.getX(), 0, pos.getZ()) >> 9, 3L);
    }

    /** Lays a new mat of {@code stage} at {@code pos} lying {@code reach} out, if it is open water there. */
    public static boolean sendOut(ServerLevel level, BlockPos pos, int stage, int reach) {
        if (!level.isLoaded(pos) || !level.isEmptyBlock(pos) || reach > FARTHEST
                || stage == HippoGrassClumpBlock.YOUNG && reach > 1 || reach > mostReachAt(pos)) {
            return false;
        }
        BlockState mat = com.antcolonies.savanna.registry.SavannaBlocks.HIPPO_GRASS.get().defaultBlockState()
                .setValue(STAGE, stage).setValue(REACH, reach);
        if (!mat.canSurvive(level, pos)) {
            return false;
        }
        level.setBlock(pos, mat, Block.UPDATE_ALL);
        return true;
    }

    /** The stage of what lies a ring nearer the bank beside {@code pos} (the clump for a mat 1 out), or -1 if nothing does. */
    static int nearerStage(BlockGetter level, BlockPos pos, int reach) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState there = level.getBlockState(pos.relative(side));
            if (reach == 1 ? there.getBlock() instanceof HippoGrassClumpBlock
                    : there.getBlock() instanceof HippoGrassBlock && there.getValue(REACH) == reach - 1) {
                return there.getValue(STAGE);
            }
        }
        return -1;
    }

    /** Whether a mat lies farther out beside {@code pos}. */
    static boolean fartherOut(BlockGetter level, BlockPos pos, int reach) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState there = level.getBlockState(pos.relative(side));
            if (there.getBlock() instanceof HippoGrassBlock && there.getValue(REACH) > reach) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE), reach = state.getValue(REACH);
        int lead = nearerStage(level, pos, reach);
        if (lead >= 0 && lead != stage) {
            level.setBlock(pos, state.setValue(STAGE, lead), Block.UPDATE_CLIENTS);
            return;
        }
        if (stage == HippoGrassClumpBlock.DYING) {
            if (!fartherOut(level, pos, reach) && random.nextFloat() < DIE_CHANCE) {
                level.removeBlock(pos, false);
            }
            return;
        }
        if (lead >= 0 && random.nextFloat() < HippoGrassClumpBlock.SPREAD_CHANCE) {
            sendOut(level, pos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random)), stage, reach + 1);
        }
    }
}
