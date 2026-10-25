package com.antcolonies.block;

import com.antcolonies.nav.Slope;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A soil ramp (planned with the user 2026-10-24, {@code docs/plans/soil-ramp.md}): the floor of a sloping burrow, carved
 * from the soil it is dug in rather than built (a digger bites the step's block down to a slope instead of taking it
 * out whole). It rises 45 degrees, in 8 small 2-pixel steps, from its low side to a full block at its high side
 * ({@link #FACING}); straight only for now (corners come when a species digs curving tunnels). Insects walk on it as a
 * slope ({@link Slope}), players walk up it. One ramp per soil, drawn with that soil's own texture, signed up in
 * {@link SoilRamps}; breaking one gives what breaking its soil gives (its loot table). Species-neutral.
 */
public class SoilRampBlock extends Block implements Slope {
    /** The high side of the ramp. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final int LAYER_PIXELS = 2;
    private static final int LAYERS = PIXELS / LAYER_PIXELS;
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (int u = 0; u < LAYERS; u++) {
                for (int w = 0; w < LAYERS; w++) {
                    shape = Shapes.or(shape, Block.box(u * LAYER_PIXELS, 0, w * LAYER_PIXELS, (u + 1) * LAYER_PIXELS,
                            layers(facing, u, w) * LAYER_PIXELS, (w + 1) * LAYER_PIXELS));
                }
            }
            SHAPES[facing.get2DDataValue()] = shape.optimize();
        }
    }

    public SoilRampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** How many 2-pixel layers column (u, w) of the 8 x 8 holds (1 at the low side, 8 at the high side), as the termite ramp. */
    private static int layers(Direction facing, int u, int w) {
        return switch (facing) {
            case EAST -> u + 1;
            case WEST -> LAYERS - u;
            case SOUTH -> w + 1;
            default -> LAYERS - w;
        };
    }

    @Override
    public int surfaceHeight(BlockState state, int u, int w) {
        return layers(state.getValue(FACING), u, w) * LAYER_PIXELS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
