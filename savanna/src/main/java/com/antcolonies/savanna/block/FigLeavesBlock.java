package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A sycamore fig's leaves (floodplain trees step 3): small rounded leaves, nearly closed, in three tones so a leaf pad
 * has a sunlit top and a dark underside, as in the model viewer the user picked the fig from ({@link #TONE}: 0 sunlit,
 * 1 plain, 2 shaded). Like the dry trees' leaves they never decay on their own ({@link TreeLeavesBlock}).
 */
public class FigLeavesBlock extends TreeLeavesBlock {
    public static final MapCodec<FigLeavesBlock> CODEC = simpleCodec(FigLeavesBlock::new);
    /** 0 sunlit, 1 plain, 2 shaded. */
    public static final IntegerProperty TONE = IntegerProperty.create("tone", 0, 2);

    public FigLeavesBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TONE, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TONE);
    }
}
