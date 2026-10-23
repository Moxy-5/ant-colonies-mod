package com.antcolonies.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

    /** How many layers this heap may have at most (a full block for seeds, bread and forage). */
    public int maxLayers() {
        return MAX_LAYERS;
    }

    // --- the heap's own rules, for the colony's food store (the split, step S3: a heap whose layers age, the termites'
    // fungus comb, overrides them, so the shared food store names no comb) ----------------------------------------------

    /**
     * Whether the heap's layers age (fungus comb): a piece is built onto a heap with room before a new one is started,
     * a layer is only taken where one is ready ({@link #hasLayerToTake}), and never off a heap an egg or a young one lies on.
     */
    public boolean ages() {
        return false;
    }

    /** A new heap: one layer. */
    public BlockState newHeap() {
        return defaultBlockState();
    }

    /** The heap with one more layer (call only with room for it). */
    public BlockState oneLayerMore(BlockState state) {
        return state.setValue(LAYERS, state.getValue(LAYERS) + 1);
    }

    /** The heap with one layer less, or air if it was the last. */
    public BlockState oneLayerLess(BlockState state) {
        int layers = state.getValue(LAYERS);
        return layers <= 1 ? Blocks.AIR.defaultBlockState() : state.setValue(LAYERS, layers - 1);
    }

    /** Whether a layer may be taken off this heap to eat (any seed heap). */
    public boolean hasLayerToTake(BlockState state) {
        return true;
    }

    /** What grows on the heap and goes with it once it is eaten right down (fungus nodules), else 0. */
    public int growthOn(BlockState state) {
        return 0;
    }

    /** A layer was just laid at {@code spot}: its sound and look (nothing for seeds). */
    public void laid(ServerLevel level, BlockPos spot) {
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
