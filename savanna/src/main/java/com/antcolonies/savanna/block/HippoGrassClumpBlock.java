package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hippo grass's rooted clump (2026-11-22, {@code docs/plans/papyrus-and-hippo-grass.md} step 2; Vossia cuspidata): a
 * coarse tuft at the water's edge, on the bank with water beside it, from which the floating mat
 * ({@link HippoGrassBlock}) grows out over the water.
 *
 * <p>The clump leads its patch through the year (the user's idea, 2026-11-22: a life cycle ending in dying):
 * {@link #STAGE} young shoots, grown, flowering, dying back, then young again (the roots at the bank live on). It moves on
 * a stage on random ticks, about 2 game days a stage, so a full cycle in about 8 (my pick, so a player sees it turn); its
 * mats take their stage from it ring by ring, and while it is not dying back it sends a new mat out over the water beside
 * it now and then. Once seasons come (M9.5) the stage follows the shared season source instead (dying back in the dry
 * season, flowering late in the wet), per the season rule.
 */
public class HippoGrassClumpBlock extends BushBlock {
    public static final MapCodec<HippoGrassClumpBlock> CODEC = simpleCodec(HippoGrassClumpBlock::new);
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
    public static final int YOUNG = 0, GROWN = 1, FLOWERING = 2, DYING = 3;
    /** The chance a random tick moves the clump on a stage: about 2 game days a stage (a block gets about 17 random ticks a day). */
    static final float STAGE_CHANCE = 1 / 35f;
    /** The chance a random tick sends a mat out over the water beside it (when there is room). */
    static final float SPREAD_CHANCE = 1 / 3f;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public HippoGrassClumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, GROWN));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        net.minecraft.world.phys.Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, 0, offset.z);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ReedBlock.GROUND);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos) && !level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                && ReedBlock.atWaterline(level, pos);
    }

    /** The stage after {@code stage} (dying back comes round to young). */
    public static int next(int stage) {
        return (stage + 1) % 4;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (random.nextFloat() < STAGE_CHANCE) {
            level.setBlock(pos, state.setValue(STAGE, next(stage)), Block.UPDATE_CLIENTS);
            return;
        }
        if (stage != DYING && random.nextFloat() < SPREAD_CHANCE) {
            HippoGrassBlock.sendOut(level, pos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random)), stage, 1);
        }
    }
}
