package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A root step at a sycamore fig's foot (floodplain trees step 3): a low piece of root in the corner of the buttresses,
 * half a block high or ({@link #LOW}) 5 pixels, so the trunk runs down into the ground in steps as a real fig's
 * buttress roots do (the model viewer's {@code slab8} and {@code slab5}). It wears the fig's barks ({@link FigBark}).
 */
public class FigRootBlock extends Block {
    public static final MapCodec<FigRootBlock> CODEC = simpleCodec(FigRootBlock::new);
    /** The lower step, 5 pixels high (else 8). */
    public static final BooleanProperty LOW = BooleanProperty.create("low");
    private static final VoxelShape HIGH_SHAPE = box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape LOW_SHAPE = box(0, 0, 0, 16, 5, 16);

    public FigRootBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LOW, false).setValue(FigBark.BARK, FigBark.MOTTLED));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOW, FigBark.BARK);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(LOW) ? LOW_SHAPE : HIGH_SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
