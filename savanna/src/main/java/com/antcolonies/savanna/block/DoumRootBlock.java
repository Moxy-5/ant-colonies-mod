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
 * A root step at a doum palm's swollen foot (floodplain trees step 6): a low piece of the clump's base in the corner of
 * its steps, half a block high or ({@link #LOW}) 5 pixels (the model viewer's {@code slab8} and {@code slab5}), in the
 * palm's bark or a dead palm's grey ({@link #DEAD}).
 */
public class DoumRootBlock extends Block {
    public static final MapCodec<DoumRootBlock> CODEC = simpleCodec(DoumRootBlock::new);
    /** The lower step, 5 pixels high (else 8). */
    public static final BooleanProperty LOW = BooleanProperty.create("low");
    /** A dead palm's grey bark. */
    public static final BooleanProperty DEAD = BooleanProperty.create("dead");
    private static final VoxelShape HIGH_SHAPE = box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape LOW_SHAPE = box(0, 0, 0, 16, 5, 16);

    public DoumRootBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LOW, false).setValue(DEAD, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOW, DEAD);
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
