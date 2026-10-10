package com.antcolonies.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A heap of seeds on the floor of the colony's food store. Every seed an ant delivers adds one layer (two pixels
 * high, up to a full block of eight). Nothing collides with it, so ants and players walk straight through.
 */
public class SeedPileBlock extends Block {
    public static final int MAX_LAYERS = 8;
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, MAX_LAYERS);

    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LAYERS + 1];

    static {
        for (int layers = 1; layers <= MAX_LAYERS; layers++) {
            SHAPES[layers] = Block.box(0.0, 0.0, 0.0, 16.0, layers * 2.0, 16.0);
        }
    }

    public SeedPileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    // Seeds need a floor: without one the pile is gone.
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}
