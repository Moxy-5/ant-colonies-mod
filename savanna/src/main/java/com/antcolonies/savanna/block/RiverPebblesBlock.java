package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
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

/**
 * River pebbles (sand banks, 2026-11-16, {@code docs/plans/sand-banks.md}): small round 3D stones lying on the sand bars
 * and lining the river bed, look B "fine pixels" picked by the user in the sand viewer ({@code tools/sand_art.py}: each
 * stone a squashed egg of quarter-pixel layers, 12 to 23 boxes a block). Seven arrangements ({@link #LOOK}: one big flat
 * stone, two medium, a medium and two small, a cluster, a scatter of tiny ones, a half-buried stone, a mixed handful),
 * each turned a random quarter and nudged off the middle by the block's position, as Minecraft does for flowers. Holds
 * water; no collision; broken by hand at once, dropping itself.
 */
public class RiverPebblesBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<RiverPebblesBlock> CODEC = simpleCodec(RiverPebblesBlock::new);
    public static final int LOOKS = 7;
    public static final IntegerProperty LOOK = IntegerProperty.create("look", 0, LOOKS - 1);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 3, 14);

    public RiverPebblesBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOOK, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOOK, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** The look a pebble block placed at {@code pos} takes (from its position, so a placed row is not all one look). */
    public static int lookAt(BlockPos pos) {
        return (int) Math.floorMod(Mth.getSeed(pos.getX(), 0, pos.getZ()) >> 16, (long) LOOKS);
    }

    /** Pebbles at {@code pos} in their look by position, holding water if {@code wet}. */
    public BlockState at(BlockPos pos, boolean wet) {
        return defaultBlockState().setValue(LOOK, lookAt(pos)).setValue(WATERLOGGED, wet);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return at(context.getClickedPos(), fluid.is(FluidTags.WATER) && fluid.isSource());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (canSurvive(state, level, pos)) {
            return state;
        }
        return state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
