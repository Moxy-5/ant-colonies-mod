package com.antcolonies.nav;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The real game world, seen as cells for the route planner. Seen through the eyes of one insect if it is given: a block
 * that lets it through (a termite chimney block for termites) is an open cell for it, a doorway, and nothing to stand
 * or cling on, because it would fall into it (except the sides of a column that can be climbed, {@link ClimbShape}).
 * A slope ({@link Slope}) is a floor in its own cell. Species-neutral: what lets whom through is up to the block.
 */
public final class LevelCellMap implements CellMap {
    private final Level level;
    private final CollisionContext context;

    public LevelCellMap(Level level) {
        this(level, null);
    }

    public LevelCellMap(Level level, @Nullable Entity insect) {
        this.level = level;
        this.context = insect == null ? CollisionContext.empty() : CollisionContext.of(insect);
    }

    @Override
    public boolean isOpen(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
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
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ClimbShape shape && face.getAxis().isHorizontal()) {
            return shape.isClimbable(state);
        }
        return state.isFaceSturdy(level, pos, face) && !state.getCollisionShape(level, pos, context).isEmpty();
    }

    @Override
    public int slopeHeight(BlockPos cell, int u, int w) {
        if (!level.isLoaded(cell)) {
            return -1;
        }
        BlockState state = level.getBlockState(cell);
        return state.getBlock() instanceof Slope slope ? slope.surfaceHeight(state, u, w) : -1;
    }
}
