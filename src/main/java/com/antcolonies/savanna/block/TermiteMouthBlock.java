package com.antcolonies.savanna.block;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.nav.Channel;
import com.antcolonies.nav.ClimbShape;
import com.antcolonies.nav.Doorway;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The chimney mouth at the tip of a termite spire (cathedral mound): a short hollow tube standing on top of the block,
 * open at its tip (rim 28 pixels up, 6 pixels wide). Like the old chimney crown ({@link TermiteHoleBlock}) it is solid
 * for the player and every other creature but termites walk through it, and they come out of the tube's mouth, look
 * round and climb down it ({@link Doorway}).
 *
 * <p>Below the tube the block is either a full block (where mound soil stands beside it, so it sits flush in the
 * mound) or, standing alone on a spire, a piece narrowing from 10 to 6 pixels that continues the spire up to the tube
 * ({@link #SLIM}, chosen by its neighbours).
 *
 * <p>For termite workers the tube is a channel ({@link Channel}): it goes on down into the hollow spire piece under the
 * mouth, so a termite crawls up inside the spire, comes out of the mouth with its look round, and goes back in the same
 * way. The route planner sees the mouth as a channel whose opening on top is the doorway; nobody walks through the
 * block's sides.
 */
public class TermiteMouthBlock extends MoundPieceBlock implements Doorway, ClimbShape, Channel {
    /** True when no solid block stands beside it: the lower part narrows like a spire piece instead of filling the block. */
    public static final BooleanProperty SLIM = BooleanProperty.create("slim");
    /** The rim of the tube: 28 pixels above the block's bottom. */
    private static final double RIM = 28.0 / 16.0;
    /** The tube is 6 pixels wide: its outside is 3 pixels from the middle. */
    private static final double TUBE_HALF_WIDTH = 3.0 / 16.0;
    private static final VoxelShape SLIM_SHAPE = tapered(10, 6, 0, LAYERS);

    public TermiteMouthBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SLIM, true).setValue(MoundSoil.AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SLIM, MoundSoil.AGE);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return state.getValue(SLIM) ? SLIM_SHAPE : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() != null
                && entityContext.getEntity().getType().is(ModTags.PASSES_TERMITE_HOLES)) {
            return Shapes.empty();
        }
        return shape(state);
    }

    @Override
    public boolean fits(@Nullable Entity insect) {
        return insect != null && insect.getType().is(ModTags.FITS_TERMITE_TUNNELS);
    }

    /** The tube joins the channel of the block under it (a hollow spire piece, or a tunnel block). */
    @Override
    public boolean canJoin(BlockState state, Direction side) {
        return side == Direction.DOWN;
    }

    /** Always open on top (the mouth); down where the block under it has a channel that opens up. */
    @Override
    public int openSides(BlockGetter level, BlockPos pos, BlockState state) {
        int sides = 1 << Direction.UP.get3DDataValue();
        if (Channel.joinsToward(level, pos.below(), Direction.UP)) {
            sides |= 1 << Direction.DOWN.get3DDataValue();
        }
        return sides;
    }

    /** Insects climb the outside of its lower part (a slim one narrows like a spire piece). */
    @Override
    public boolean isClimbable(BlockState state) {
        return true;
    }

    @Override
    public double widthAt(BlockState state, double y) {
        if (!state.getValue(SLIM)) {
            return 1.0;
        }
        int layer = Math.max(0, Math.min(LAYERS - 1, (int) Math.floor(y * LAYERS)));
        return taper(10, 6, layer, LAYERS) / 16.0;
    }

    @Override
    public double mouthHeight() {
        return RIM;
    }

    @Override
    public double mouthRadius() {
        return TUBE_HALF_WIDTH;
    }

    /** Slim unless a block with a solid face stands right beside it. */
    private static boolean slimAt(BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = pos.relative(side);
            if (level.getBlockState(beside).isFaceSturdy(level, beside, side.getOpposite())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(SLIM, slimAt(context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(SLIM, slimAt(level, pos));
        }
        return state;
    }
}
