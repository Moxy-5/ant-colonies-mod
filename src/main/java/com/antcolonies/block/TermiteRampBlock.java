package com.antcolonies.block;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ramp of termite mound soil (cathedral mound): it hides the stair steps of the mound's sides, rising in 8 small
 * 2-pixel steps from the low side to a full block at the high side ({@link #FACING}). Like stairs, ramps placed side by
 * side join into outer and inner corners on their own ({@link #SHAPE}; left and right as for stairs). A player walks up
 * one like a slope.
 */
public class TermiteRampBlock extends MoundPieceBlock {
    /** The high side of the ramp. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;

    private static final Map<Direction, Map<StairsShape, VoxelShape>> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<StairsShape, VoxelShape> byShape = new EnumMap<>(StairsShape.class);
            for (StairsShape shape : StairsShape.values()) {
                byShape.put(shape, fromHeights((u, w) -> height(facing, shape, u, w)));
            }
            SHAPES.put(facing, byShape);
        }
    }

    public TermiteRampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, StairsShape.STRAIGHT)
                .setValue(MoundSoil.AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, MoundSoil.AGE);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return SHAPES.get(state.getValue(FACING)).get(state.getValue(SHAPE));
    }

    /**
     * How many 2-pixel layers column (u, w) of the 8 x 8 holds (1 at the low side, 8 at the high side). A corner rises
     * toward two sides: an outer corner only where both are high (the lower of the two), an inner corner where either
     * is (the higher). The same sum is in {@code tools/termite_blocks.py}.
     */
    public static int height(Direction facing, StairsShape shape, int u, int w) {
        int toFacing = rise(facing, u, w);
        if (shape == StairsShape.STRAIGHT) {
            return toFacing;
        }
        boolean left = shape == StairsShape.OUTER_LEFT || shape == StairsShape.INNER_LEFT;
        int toSide = rise(left ? facing.getCounterClockWise() : facing.getClockWise(), u, w);
        boolean outer = shape == StairsShape.OUTER_LEFT || shape == StairsShape.OUTER_RIGHT;
        return outer ? Math.min(toFacing, toSide) : Math.max(toFacing, toSide);
    }

    /** Layers of column (u, w) for a straight ramp rising toward {@code side}. */
    private static int rise(Direction side, int u, int w) {
        return switch (side) {
            case EAST -> u + 1;
            case WEST -> LAYERS - u;
            case SOUTH -> w + 1;
            default -> LAYERS - w;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        return state.setValue(SHAPE, shapeAt(state, context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        return direction.getAxis().isHorizontal() ? state.setValue(SHAPE, shapeAt(state, level, pos)) : state;
    }

    // Corners join the way stairs do (the same rules as vanilla StairBlock).
    private static StairsShape shapeAt(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockState ahead = level.getBlockState(pos.relative(facing));
        if (ahead.getBlock() instanceof TermiteRampBlock) {
            Direction other = ahead.getValue(FACING);
            if (other.getAxis() != facing.getAxis() && canTakeShape(state, level, pos, other.getOpposite())) {
                return other == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
            }
        }
        BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
        if (behind.getBlock() instanceof TermiteRampBlock) {
            Direction other = behind.getValue(FACING);
            if (other.getAxis() != facing.getAxis() && canTakeShape(state, level, pos, other)) {
                return other == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
            }
        }
        return StairsShape.STRAIGHT;
    }

    private static boolean canTakeShape(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockState beside = level.getBlockState(pos.relative(side));
        return !(beside.getBlock() instanceof TermiteRampBlock) || beside.getValue(FACING) != state.getValue(FACING);
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
