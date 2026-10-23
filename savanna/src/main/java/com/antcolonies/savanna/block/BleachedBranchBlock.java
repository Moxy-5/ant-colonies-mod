package com.antcolonies.savanna.block;

import com.antcolonies.block.EatenInStages;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A dead giant tree's silver branch (M7.5 step 6d): termites eat it where they reach it (a fallen tree's limbs on the
 * ground), like a fallen branch, but it does not change its look as it is eaten (its many tapering shapes have no room
 * for eaten stages; my pick); the last piece takes it away.
 */
public class BleachedBranchBlock extends TaperingBranchBlock implements EatenInStages {
    public static final MapCodec<BleachedBranchBlock> CODEC = simpleCodec(BleachedBranchBlock::new);

    public BleachedBranchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState eatenTo(BlockState state, int cut, int pieces) {
        return state;
    }

    @Override
    public int eatenAlready(BlockState state, int pieces) {
        return 0;
    }
}
