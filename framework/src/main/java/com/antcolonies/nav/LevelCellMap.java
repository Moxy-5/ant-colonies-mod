package com.antcolonies.nav;

import com.antcolonies.colony.LandReady;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The real game world, seen as cells for the route planner. Seen through the eyes of one insect if it is given: a block
 * that lets it through (a termite chimney block for termites) is an open cell for it, a doorway, and nothing to stand
 * or cling on, because it would fall into it (except the sides of a column that can be climbed, {@link ClimbShape}).
 * A slope ({@link Slope}) is a floor in its own cell, and so is a flat part-block the body rests on (a path, farmland,
 * a bottom slab, a carpet: {@link #lowTop}). A block with a channel ({@link Channel}) is never an open cell: an
 * insect that fits crawls through its channel. Species-neutral: what lets whom through is up to the block.
 */
public final class LevelCellMap implements CellMap {
    private final Level level;
    private final CollisionContext context;
    @Nullable
    private final Entity insect;

    public LevelCellMap(Level level) {
        this(level, null);
    }

    public LevelCellMap(Level level, @Nullable Entity insect) {
        this.level = level;
        this.context = insect == null ? CollisionContext.empty() : CollisionContext.of(insect);
        this.insect = insect;
    }

    @Override
    public boolean isOpen(BlockPos pos) {
        if (!LandReady.at(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof Channel) {
            return false; // only ever crawled through by its channel (see channelSides)
        }
        if (com.antcolonies.fight.HeldCells.barred(level, pos, insect)) {
            return false; // a foe holds it (the held mouth): a wall to this insect while the holder lives
        }
        return state.getCollisionShape(level, pos, context).isEmpty()
                && state.getFluidState().isEmpty()
                && !state.is(BlockTags.FIRE)
                && !state.is(Blocks.COBWEB)
                && !state.is(Blocks.SWEET_BERRY_BUSH)
                && !state.is(Blocks.POWDER_SNOW);
    }

    /** A full face, or a side of a column that can be climbed ({@link ClimbShape}, even one the insect may pass through). */
    @Override
    public boolean isSolidFace(BlockPos pos, Direction face) {
        if (!LandReady.at(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ClimbShape shape && face.getAxis().isHorizontal()) {
            return shape.isClimbable(state);
        }
        return state.isFaceSturdy(level, pos, face) && !state.getCollisionShape(level, pos, context).isEmpty();
    }

    /** The ground's own cost ({@link SoftGround}) of the block this foothold holds on to. */
    @Override
    public double footCost(Foothold at) {
        if (at.isChannel()) {
            return 0.0;
        }
        BlockPos solid = at.cell().relative(at.surface());
        if (!LandReady.at(level, solid)) {
            return 0.0;
        }
        BlockState state = level.getBlockState(solid);
        return state.getBlock() instanceof SoftGround ground ? ground.insectCost(state) : 0.0;
    }

    @Override
    public int channelSides(BlockPos cell) {
        if (!LandReady.at(level, cell)) {
            return 0;
        }
        BlockState state = level.getBlockState(cell);
        return state.getBlock() instanceof Channel channel && channel.fits(insect) ? channel.openSides(level, cell, state) : 0;
    }

    @Override
    public boolean isMouth(BlockPos cell) {
        return LandReady.at(level, cell) && level.getBlockState(cell).getBlock() instanceof Doorway && channelSides(cell) != 0;
    }

    @Override
    public int slopeHeight(BlockPos cell, int u, int w) {
        if (!LandReady.at(level, cell)) {
            return -1;
        }
        BlockState state = level.getBlockState(cell);
        return state.getBlock() instanceof Slope slope ? slope.surfaceHeight(state, u, w) : lowTop(cell, state);
    }

    /**
     * A part-block with a flat top the body really rests on (a dirt path, farmland, a bottom slab, a carpet, snow
     * layers): the height of that top in pixels, so the planner treats it as a flat slope, a floor in the block's own
     * cell (the user, 2026-11-26: these are plain ground). -1 for everything else: air, a full block or one whose top
     * is a sturdy face (a floor for the cell above already), a block not as wide as its cell (a fence, a pot), one in
     * water, a campfire, and the blocks with a walking rule of their own (a channel, a column that is climbed).
     */
    private int lowTop(BlockPos cell, BlockState state) {
        if (state.isAir() || state.isCollisionShapeFullBlock(level, cell) || !state.getFluidState().isEmpty()
                || state.getBlock() instanceof Channel || state.getBlock() instanceof ClimbShape
                || state.is(BlockTags.CAMPFIRES)) {
            return -1;
        }
        VoxelShape shape = state.getCollisionShape(level, cell, context);
        if (shape.isEmpty() || state.isFaceSturdy(level, cell, Direction.UP)) {
            return -1;
        }
        double top = shape.max(Direction.Axis.Y);
        if (top >= 1.0 || shape.min(Direction.Axis.Y) > 0.0
                || shape.min(Direction.Axis.X) > 0.0 || shape.max(Direction.Axis.X) < 1.0
                || shape.min(Direction.Axis.Z) > 0.0 || shape.max(Direction.Axis.Z) < 1.0
                || shape.toAabbs().size() != 1) {
            return -1;
        }
        return Math.max(1, (int) Math.round(top * Slope.PIXELS));
    }
}
