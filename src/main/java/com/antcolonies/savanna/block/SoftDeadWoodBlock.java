package com.antcolonies.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Soft dead wood with no grain to lie along (M7.5 step 6d): the heart-rot inside an old trunk and the fibre heap a dead
 * baobab slumps into. Termites eat it in the same 4 {@link DeadWoodBlock#EATEN} stages as the logs, but it gives fewer
 * pieces (it is quicker to eat; numbers in {@code macrotermes.json}).
 */
public class SoftDeadWoodBlock extends Block implements EatenInStages {
    public static final MapCodec<SoftDeadWoodBlock> CODEC = simpleCodec(SoftDeadWoodBlock::new);

    public SoftDeadWoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DeadWoodBlock.EATEN, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DeadWoodBlock.EATEN);
    }

    @Override
    public BlockState eatenTo(BlockState state, int cut, int pieces) {
        return DeadTreeWoodBlock.stage(state, cut, pieces);
    }

    @Override
    public int eatenAlready(BlockState state, int pieces) {
        return DeadTreeWoodBlock.already(state, pieces);
    }
}
