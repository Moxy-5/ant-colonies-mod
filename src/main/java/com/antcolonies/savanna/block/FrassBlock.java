package com.antcolonies.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Frass (M7.5): the fine pale wood dust and droppings beetle larvae and termites leave behind, a thin layer (2 pixels)
 * on the floor of a hollow log and its galleries. It lies on anything below it, like a carpet.
 */
public class FrassBlock extends CarpetBlock {
    public static final MapCodec<FrassBlock> CODEC = simpleCodec(FrassBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 2, 16);

    public FrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CarpetBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
