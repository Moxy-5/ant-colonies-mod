package com.antcolonies.savanna.block;

import java.util.Map;

import javax.annotation.Nullable;

import com.antcolonies.animal.EggClutchBlock;
import com.antcolonies.animal.EggClutchBlockEntity;
import com.antcolonies.savanna.registry.SavannaBlockEntities;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A river snail's egg clutch (M9, animal life cycle step 2, 2026-11-19; the look the user confirmed in the life
 * viewer): a small cluster of pearly eggs stuck to the side of a stone, a pebble or the bank, best just over the
 * waterline, else under it or on the bed ({@code tools/breeding_art.py}). {@link #FACING} points away from the block it
 * sticks to. It holds water when laid in water. The kit's clutch ({@link EggClutchBlock}): it hatches into baby snails
 * after the snail's hatching days when a watcher is near and the river has room for snails, and long unwatched it is
 * gone. It falls off with the block it sticks to. Broken by hand at once, dropping nothing.
 */
public class SnailEggsBlock extends EggClutchBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<SnailEggsBlock> CODEC = simpleCodec(SnailEggsBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP, Block.box(4, 0, 4, 12, 3, 12),
            Direction.DOWN, Block.box(4, 13, 4, 12, 16, 12),
            Direction.NORTH, Block.box(4, 4, 13, 12, 12, 16),
            Direction.SOUTH, Block.box(4, 4, 0, 12, 12, 3),
            Direction.WEST, Block.box(13, 4, 4, 16, 12, 12),
            Direction.EAST, Block.box(0, 4, 4, 3, 12, 12));

    public SnailEggsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EggClutchBlockEntity(SavannaBlockEntities.EGG_CLUTCH.get(), pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Whether eggs facing this way could stick here: the block behind them has a sturdy face toward them. */
    public static boolean sticksAt(LevelReader level, BlockPos pos, Direction facing) {
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return sticksAt(level, pos, state.getValue(FACING));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace())
                .setValue(WATERLOGGED, fluid.is(FluidTags.WATER) && fluid.isSource());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
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

    private static BlockState gone(BlockState state) {
        return state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /**
     * Lays a fresh clutch of {@code eggs} of this kind here, facing that way, holding water if the spot is water. False
     * if the spot is taken or the eggs cannot stick.
     */
    public static boolean lay(ServerLevel level, BlockPos pos, Direction facing, EntityType<?> kind, int eggs) {
        BlockState there = level.getBlockState(pos);
        boolean water = there.is(Blocks.WATER) && there.getFluidState().isSource();
        if (!(there.isAir() || water) || !sticksAt(level, pos, facing)) {
            return false;
        }
        level.setBlock(pos, SavannaBlocks.SNAIL_EGGS.get().defaultBlockState().setValue(FACING, facing).setValue(WATERLOGGED, water),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof EggClutchBlockEntity clutch) {
            clutch.lay(kind, eggs, level.getGameTime());
            return true;
        }
        return false;
    }
}
