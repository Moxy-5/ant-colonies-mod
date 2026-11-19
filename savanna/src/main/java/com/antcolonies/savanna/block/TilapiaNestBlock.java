package com.antcolonies.savanna.block;

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
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.player.Player;

import com.antcolonies.savanna.registry.SavannaBlocks;

/**
 * A tilapia's nest pit (M9, animal life cycle step 2, 2026-11-19; the look the user confirmed in the life viewer,
 * "they all look perfect"): the round saucer a breeding male fans out of a shallow silt or sand bed, a block across, a
 * darker hollow inside a low rim of the dug-up ground ({@code tools/breeding_art.py}). It lies in the water over the
 * bed, always holding water like seagrass; no collision. {@link #SANDY} draws it in river sand on a sandy bed.
 *
 * <p>It fades on random ticks ({@link #AGE}, 3 steps in about 3 game days as the broken shell, my pick) back to plain
 * water: a pit outlives its male's stay. Broken by hand at once, dropping nothing.
 */
public class TilapiaNestBlock extends Block implements LiquidBlockContainer {
    public static final MapCodec<TilapiaNestBlock> CODEC = simpleCodec(TilapiaNestBlock::new);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    public static final BooleanProperty SANDY = BooleanProperty.create("sandy");
    /** The chance a random tick ages it a step: 3 steps in about 3 game days (a block gets ~17 random ticks a day). */
    static final float AGE_CHANCE = 1 / 17f;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1.5, 16);

    public TilapiaNestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(SANDY, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, SANDY);
    }

    /** A fresh pit over this bed block: sandy when the bed is a sand (any falling ground). */
    public static BlockState over(BlockState bed) {
        return SavannaBlocks.TILAPIA_NEST.get().defaultBlockState().setValue(SANDY, bed.getBlock() instanceof FallingBlock);
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
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return canSurvive(state, level, pos) ? state : Blocks.WATER.defaultBlockState();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < AGE_CHANCE) {
            int age = state.getValue(AGE);
            level.setBlock(pos, age < 2 ? state.setValue(AGE, age + 1) : Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
