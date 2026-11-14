package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * River algae (M9 river life, the first link of the food chain, 2026-11-14; look C picked by the user in
 * {@code python tools/model_viewer.py algae}): a thin green mat on the river bed under water, laid over whatever the bed
 * is (silt, mud, sand, stone), with streamers standing up from it when thick (an animated texture, so they sway).
 *
 * <p>{@link #GROWTH}: 0 grazed bare (a few specks; it never goes, so a grazed patch grows back without seeding again),
 * 1 thin, 2 thick, 3 lush. It grows on random ticks (about one stage a game day, my pick), only as far as the water's
 * depth lets it ({@link #mostFor}: lush only in shallow water, thin at most in the deep channel, where little light
 * reaches), and a thick mat spreads to bare bed beside it. The grazing fish (the next step) bite it down a stage at a
 * time through {@link #graze}. Like seagrass it always holds water: broken, it leaves water.
 */
public class AlgaeBlock extends BushBlock implements LiquidBlockContainer, BonemealableBlock {
    public static final MapCodec<AlgaeBlock> CODEC = simpleCodec(AlgaeBlock::new);
    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, 3);
    public static final int BARE = 0, THIN = 1, THICK = 2, LUSH = 3;
    /** The chance a random tick grows the mat a stage (about one stage a game day: a block gets ~17 random ticks a day). */
    static final float GROW_CHANCE = 1 / 14f;
    /** The chance a random tick of a thick or lush mat seeds bare bed beside it. */
    static final float SPREAD_CHANCE = 1 / 10f;
    /** How deep the water is counted (deeper is all the same: the channel). */
    static final int DEEPEST = 8;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public AlgaeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GROWTH, THIN));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GROWTH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP) && !state.is(Blocks.MAGMA_BLOCK);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState water = context.getLevel().getFluidState(context.getClickedPos());
        return water.is(FluidTags.WATER) && water.isSource() ? super.getStateForPlacement(context) : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        BlockState after = super.updateShape(state, facing, facingState, level, pos, facingPos);
        if (!after.isAir()) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return after;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    /** How many blocks of water stand here, this cell counted (up to {@link #DEEPEST}). */
    public static int depth(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos at = pos.mutable();
        int d = 0;
        while (d < DEEPEST && level.getFluidState(at).is(FluidTags.WATER)) {
            d++;
            at.move(Direction.UP);
        }
        return d;
    }

    /** The thickest a mat grows in water this deep: lush in 1 or 2 blocks, thick in 3 or 4, thin deeper (my picks). */
    public static int mostFor(int depth) {
        return depth <= 2 ? LUSH : depth <= 4 ? THICK : THIN;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int growth = state.getValue(GROWTH);
        if (growth < mostFor(depth(level, pos)) && random.nextFloat() < GROW_CHANCE) {
            level.setBlock(pos, state.setValue(GROWTH, growth + 1), Block.UPDATE_CLIENTS);
        }
        if (growth >= THICK && random.nextFloat() < SPREAD_CHANCE) {
            spread(level, pos, random);
        }
    }

    /** Seeds a bare mat on the bed beside {@code pos} (one cell, the bed up to a block higher or lower), if there is room. */
    static void spread(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos to = pos.relative(side).above(random.nextInt(3) - 1);
        if (!level.isLoaded(to)) {
            return;
        }
        BlockState there = level.getBlockState(to);
        BlockState bare = level.getBlockState(pos).getBlock().defaultBlockState().setValue(GROWTH, BARE);
        if (there.is(Blocks.WATER) && there.getFluidState().isSource() && bare.canSurvive(level, to)) {
            level.setBlock(to, bare, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }

    /**
     * A fish's bite: the mat here drops a stage. Whether there was anything to eat (a bare mat has nothing; it stays, to
     * grow back).
     */
    public static boolean graze(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AlgaeBlock) || state.getValue(GROWTH) == BARE) {
            return false;
        }
        level.setBlock(pos, state.setValue(GROWTH, state.getValue(GROWTH) - 1), Block.UPDATE_CLIENTS);
        return true;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(GROWTH) < LUSH;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(GROWTH, state.getValue(GROWTH) + 1), Block.UPDATE_CLIENTS);
    }
}
