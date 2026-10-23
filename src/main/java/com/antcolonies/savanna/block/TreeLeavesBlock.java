package com.antcolonies.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The leaves of a giant savanna tree (M7.5): fine, see-through, flat layers of tiny leaflets. Unlike Minecraft's leaves
 * they never decay on their own: a giant crown spreads far wider than 7 blocks from its trunk, and our trees die and drop
 * their leaves by their own rules (M7.5 step 5). Like leaves they dim the light a little and let it through.
 */
public class TreeLeavesBlock extends Block {
    public static final MapCodec<TreeLeavesBlock> CODEC = simpleCodec(TreeLeavesBlock::new);

    public TreeLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 1;
    }
}
