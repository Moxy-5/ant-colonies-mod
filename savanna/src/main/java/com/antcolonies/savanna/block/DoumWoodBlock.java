package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A doum palm's stem (floodplain trees step 5): a log or wood turned by how it was placed, its bark the living one (look
 * A, the user's pick) or a dead palm's grey ({@link #DEAD}). A palm stem has no rings and no bark that peels: its log end
 * is fibrous, speckled with the cut vascular strands. Every stem is a column two blocks wide (the user's "nothing too
 * thin").
 */
public class DoumWoodBlock extends RotatedPillarBlock {
    public static final MapCodec<DoumWoodBlock> CODEC = simpleCodec(DoumWoodBlock::new);
    /** A dead palm's weathered grey bark. */
    public static final BooleanProperty DEAD = BooleanProperty.create("dead");

    public DoumWoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DEAD, false));
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DEAD);
    }
}
