package com.antcolonies.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Dead acacia wood: the trunk of a fallen tree and the stump it snapped off (see {@code worldgen.FallenTreeFeature}),
 * silver-grey and weathered with the last of its bark peeling off. Termites eat it (real Macrotermes live on dead wood
 * like this, hollowing a log out from under a crust of mud): it goes through 4 {@link #EATEN} stages, more and more of
 * it plastered with mud and the core hollowed out, and the last piece leaves nothing. A whole block is worth far more
 * than a tuft of grass (numbers in {@code macrotermes.json}). Like a log it lies along an axis and burns.
 */
public class DeadWoodBlock extends RotatedPillarBlock implements EatenInStages {
    public static final MapCodec<DeadWoodBlock> CODEC = simpleCodec(DeadWoodBlock::new);
    public static final int MAX_EATEN = 3;
    public static final IntegerProperty EATEN = IntegerProperty.create("eaten", 0, MAX_EATEN);

    public DeadWoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(EATEN, 0));
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(EATEN);
    }

    @Override
    public BlockState eatenTo(BlockState state, int cut, int pieces) {
        return state.setValue(EATEN, Math.min(MAX_EATEN, cut * (MAX_EATEN + 1) / Math.max(1, pieces)));
    }

    @Override
    public int eatenAlready(BlockState state, int pieces) {
        return state.getValue(EATEN) * pieces / (MAX_EATEN + 1);
    }

    // Dry dead wood: catches fire a little more easily than a living log.

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 10;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 10;
    }
}
