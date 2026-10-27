package com.antcolonies.savanna.block;

import com.antcolonies.block.EatenInStages;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
 * Dead wood for the raids (heart rot, 2026-10-26): the pulp heap, the crumbly rotten heartwood (punk wood) that spills
 * out where a hollow tree snapped, at the stump's foot and the lying top's ends. Real: brown-rotted heartwood breaks
 * into soft cubes and white-rotted into pale stringy punk; termites and beetle larvae eat it, and what is left sinks
 * into the soil, which grows greener there. In {@link #LAYERS} like snow (1 to 8, each 2 pixels); termites eat it a
 * layer at a time; a layer sinks into the ground every few days (my pick: a heap of 6 is gone in about two weeks), and
 * when the last sinks the soil under it turns to grass with a tuft on it.
 */
public class PulpHeapBlock extends Block implements EatenInStages {
    public static final MapCodec<PulpHeapBlock> CODEC = simpleCodec(PulpHeapBlock::new);
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    /** Each random tick, a layer sinks one time in this many (about 18 random ticks a day: a layer about every 2 days). */
    static final int SINK_ONE_IN = 40;
    private static final VoxelShape[] SHAPES = new VoxelShape[9];

    static {
        for (int i = 1; i <= 8; i++) {
            SHAPES[i] = box(0, 0, 0, 16, i * 2, 16);
        }
    }

    public PulpHeapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 4));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    public BlockState withLayers(int layers) {
        return defaultBlockState().setValue(LAYERS, Math.clamp(layers, 1, 8));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction from, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(SINK_ONE_IN) != 0) {
            return;
        }
        int layers = state.getValue(LAYERS);
        if (layers > 1) {
            level.setBlock(pos, state.setValue(LAYERS, layers - 1), Block.UPDATE_ALL);
            return;
        }
        // The last of it has sunk in: the soil it fed grows green.
        BlockPos below = pos.below();
        BlockState soil = level.getBlockState(below);
        boolean fed = soil.is(Blocks.DIRT) || soil.is(Blocks.COARSE_DIRT) || soil.is(Blocks.GRASS_BLOCK);
        if (fed && !soil.is(Blocks.GRASS_BLOCK)) {
            level.setBlock(below, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(pos, fed ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Eaten from the top: a full block's worth is 8 layers, so a small heap goes in fewer pieces. */
    @Override
    public BlockState eatenTo(BlockState state, int cut, int pieces) {
        int left = (int) Math.ceil(8 * (1 - cut / (double) pieces));
        return state.setValue(LAYERS, Math.clamp(Math.min(left, state.getValue(LAYERS)), 1, 8));
    }

    @Override
    public int eatenAlready(BlockState state, int pieces) {
        return pieces * (8 - state.getValue(LAYERS)) / 8;
    }
}
