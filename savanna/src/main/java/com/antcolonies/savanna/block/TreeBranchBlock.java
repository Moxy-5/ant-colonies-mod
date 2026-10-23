package com.antcolonies.savanna.block;

import com.antcolonies.savanna.registry.TreeBlocks;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A thin branch of a giant savanna tree (M7.5), 6 pixels thick: the twigs and small limbs between the big limbs (built
 * of logs) and the leaves. It joins every way it touches tree wood (logs, wood, other branches) or leaves, like
 * Minecraft's chorus plant, so a crown's twigs read as one tree.
 */
public class TreeBranchBlock extends PipeBlock {
    public static final MapCodec<TreeBranchBlock> CODEC = simpleCodec(TreeBranchBlock::new);

    public TreeBranchBlock(Properties properties) {
        super(0.1875F, properties);
        BlockState state = stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /** Whether a branch joins this neighbour: tree wood or leaves (the tags {@code antcolonies:tree_wood} and {@code tree_leaves}). */
    public static boolean joins(BlockState neighbour) {
        return neighbour.is(TreeBlocks.TREE_WOOD) || neighbour.is(TreeBlocks.TREE_LEAVES);
    }

    /** This branch with an arm toward every neighbour it joins. */
    public BlockState joined(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), joins(level.getBlockState(pos.relative(direction))));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return joined(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), joins(neighbour));
    }
}
