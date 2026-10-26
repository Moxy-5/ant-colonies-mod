package com.antcolonies.savanna.block;

import java.util.Map;

import com.antcolonies.savanna.registry.TreeBlocks;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bracket fungus (dead wood for the raids, part 4): the hard woody shelf of a heart rot fungus (real: <i>Ganoderma</i>
 * and <i>Phellinus</i> on savanna acacias), sticking out of the bark of a tree whose core is rotting away. It grows from
 * the trunk behind it ({@link #FACING} points away from the trunk) and drops off when that wood is gone (the tree fell
 * or was cut).
 */
public class BracketFungusBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<BracketFungusBlock> CODEC = simpleCodec(BracketFungusBlock::new);
    /** The shelf against the trunk, by the way it faces (the wall on the far side). */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, box(3, 4, 8, 13, 10, 16),
            Direction.SOUTH, box(3, 4, 0, 13, 10, 8),
            Direction.WEST, box(8, 4, 3, 16, 10, 13),
            Direction.EAST, box(0, 4, 3, 8, 10, 13));

    public BracketFungusBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** The trunk wood it grows from. */
    public static boolean holdsOn(BlockState behind) {
        return behind.is(TreeBlocks.TREE_WOOD) || behind.is(TreeBlocks.ROTTEN_WOOD.get());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return holdsOn(level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** A player sets it on the side of a trunk they click. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, face);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }
}
