package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.antcolonies.savanna.registry.SavannaBlocks;

/**
 * A broken snail shell (M9 river life, 2026-11-16, the user: "Yes, crabs eat snails"): the Garden spiral's shell cracked
 * open and empty, with a few chips round it, left where a crab ate the snail ({@link #leave}; art
 * {@code tools/shell_art.py}). Looks only, fun first.
 *
 * <p>{@link #AGE}: 0 fresh (the snail's browns and cream), 1 bleached, 2 crumbling; it ages on random ticks and is gone
 * in about 3 game days (my pick), back to water or air. Holds water like any waterlogged block; broken by hand at once,
 * dropping nothing.
 */
public class CrabShellBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<CrabShellBlock> CODEC = simpleCodec(CrabShellBlock::new);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** The chance a random tick ages it a step: 3 steps in about 3 game days (a block gets ~17 random ticks a day). */
    static final float AGE_CHANCE = 1 / 17f;
    /** How far down a dropped shell sinks looking for the bed. */
    static final int DEEPEST = 16;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 2, 13);

    public CrabShellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, fluid.is(FluidTags.WATER) && fluid.isSource());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return canSurvive(state, level, pos) ? state : gone(state);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < AGE_CHANCE) {
            int age = state.getValue(AGE);
            level.setBlock(pos, age < 2 ? state.setValue(AGE, age + 1) : gone(state), Block.UPDATE_ALL);
        }
    }

    private static BlockState gone(BlockState state) {
        return state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /**
     * A crab finished with a snail at {@code at}: a fresh broken shell on the ground under it (sunk to the bed in water),
     * or beside it when that cell is taken. Returns where it went, or null.
     */
    @Nullable
    public static BlockPos leave(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int d = 0; d < DEEPEST && open(level, pos.below()); d++) {
            pos = pos.below();
        }
        if (place(level, pos)) {
            return pos;
        }
        for (Direction side : Direction.Plane.HORIZONTAL.shuffledCopy(level.getRandom())) {
            if (place(level, pos.relative(side))) {
                return pos.relative(side);
            }
        }
        return null;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState there = level.getBlockState(pos);
        return level.isLoaded(pos) && (there.isAir() || (there.is(Blocks.WATER) && there.getFluidState().isSource()));
    }

    private static boolean place(ServerLevel level, BlockPos pos) {
        if (!open(level, pos)) {
            return false;
        }
        BlockState fresh = SavannaBlocks.CRAB_SHELL.get().defaultBlockState()
                .setValue(WATERLOGGED, level.getBlockState(pos).is(Blocks.WATER));
        if (!fresh.canSurvive(level, pos)) {
            return false;
        }
        level.setBlock(pos, fresh, Block.UPDATE_ALL);
        return true;
    }
}
