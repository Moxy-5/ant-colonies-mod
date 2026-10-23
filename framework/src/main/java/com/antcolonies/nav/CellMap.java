package com.antcolonies.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * What the route planner needs to know about the world, one block-sized "cell" at a time.
 * Kept separate from the real game world so the planner can be tested on its own.
 */
public interface CellMap {
    /** True if a tiny insect can be inside this cell: no solid block, no water, lava, fire or web. Unloaded = false. */
    boolean isOpen(BlockPos pos);

    /** True if the block at {@code pos} has a solid surface on its {@code face} side (something to stand or cling on). */
    boolean isSolidFace(BlockPos pos, Direction face);

    /**
     * For a cell holding a slope ({@link Slope}, a ramp): how high (in pixels, 0 to 16) its surface is over column
     * (u, w) of the 8 x 8 (u along east, w along south). -1 if the cell holds no slope.
     */
    default int slopeHeight(BlockPos cell, int u, int w) {
        return -1;
    }

    /** Whether the cell holds a slope an insect walks on (see {@link #slopeHeight}). */
    default boolean isSlope(BlockPos cell) {
        return slopeHeight(cell, 0, 0) >= 0;
    }

    /**
     * How high (in pixels) a slope's surface gets at its highest, looked at in its four corners (a ramp is highest
     * along a side or in a corner; a flat one, like a fungus comb, is the same everywhere). -1 if the cell holds no slope.
     */
    default int slopeTop(BlockPos cell) {
        return Math.max(Math.max(slopeHeight(cell, 0, 0), slopeHeight(cell, Slope.COLUMNS - 1, 0)),
                Math.max(slopeHeight(cell, 0, Slope.COLUMNS - 1), slopeHeight(cell, Slope.COLUMNS - 1, Slope.COLUMNS - 1)));
    }

    /**
     * For a cell holding a block with a channel the insect fits through ({@link Channel}, a termite tunnel): the sides
     * the channel opens out of, as a mask (bit {@code Direction.get3DDataValue()}). 0 if there is none (or the insect
     * does not fit). Such a cell is not open ({@link #isOpen}): the insect is only ever in its channel.
     */
    default int channelSides(BlockPos cell) {
        return 0;
    }

    /**
     * Whether the channel in this cell is a doorway's tube (a chimney mouth): its opening on top is the mouth an
     * insect comes out of and goes in by, with the doorway's own motion ({@link Doorway}, {@link DoorwayMotion}).
     */
    default boolean isMouth(BlockPos cell) {
        return false;
    }
}
