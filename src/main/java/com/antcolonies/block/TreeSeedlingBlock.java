package com.antcolonies.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A seedling of a giant savanna tree (M7.5): a thin stem and its first leaves. It only stands for now; growing into a
 * young tree comes in M7.5 step 4.
 */
public class TreeSeedlingBlock extends BushBlock {
    public static final MapCodec<TreeSeedlingBlock> CODEC = simpleCodec(TreeSeedlingBlock::new);
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 12, 13);

    public TreeSeedlingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
