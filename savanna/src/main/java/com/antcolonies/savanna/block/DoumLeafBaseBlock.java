package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The stub of an old leaf's base left on a doum palm's stem (floodplain trees step 5): a dark split sheath against the
 * bark with the paler boot of the leaf stalk over it, as the viewer draws them on the stem's sides. It sits in the air
 * beside the stem, against the stem face it is {@linkplain #FACING turned from}; high or low on its block, wide or
 * narrow, pale or darker, a little to one side or the other, so the stubs up a stem never line up.
 */
public class DoumLeafBaseBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DoumLeafBaseBlock> CODEC = simpleCodec(DoumLeafBaseBlock::new);
    /** Low or high on its block. */
    public static final BooleanProperty LOW = BooleanProperty.create("low");
    /** Fourteen pixels wide or ten. */
    public static final BooleanProperty WIDE = BooleanProperty.create("wide");
    /** The boot paler or darker. */
    public static final BooleanProperty PALE = BooleanProperty.create("pale");
    /** How far to one side: 0 to 4, 2 the middle, a pixel's step each. */
    public static final IntegerProperty SHIFT = IntegerProperty.create("shift", 0, 4);
    private static final VoxelShape[] SHAPES = {
            box(4, 0, 0, 12, 16, 5),   // the stem to the north: stub against the block's north face (facing south)
            box(11, 0, 4, 16, 16, 12), // stem to the east
            box(4, 0, 11, 12, 16, 16), // stem to the south
            box(0, 0, 4, 5, 16, 12)};  // stem to the west

    public DoumLeafBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.EAST).setValue(LOW, false).setValue(WIDE, true)
                .setValue(PALE, false).setValue(SHIFT, 2));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOW, WIDE, PALE, SHIFT);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        // Placed against a stem's side: it faces away from the block clicked.
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // FACING points away from the stem; the stub lies against the stem's side of the block.
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPES[0];
            case WEST -> SHAPES[1];
            case NORTH -> SHAPES[2];
            default -> SHAPES[3];
        };
    }
}
