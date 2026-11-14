package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A sycamore fig's log or wood (floodplain trees step 3): turned by how it was placed, like any log, and wearing one of
 * the fig's barks ({@link FigBark}). A tree grown by the mod sets each block's bark; a player places the plain mottled one.
 */
public class FigWoodBlock extends RotatedPillarBlock {
    public static final MapCodec<FigWoodBlock> CODEC = simpleCodec(FigWoodBlock::new);

    public FigWoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FigBark.BARK, FigBark.MOTTLED));
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FigBark.BARK);
    }
}
