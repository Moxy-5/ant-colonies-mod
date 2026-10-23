package com.antcolonies.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a climbing insect is: the cell (block space) it is in, and the surface in that cell it holds on to.
 * {@code surface} points from the insect to the block it stands on: {@link Direction#DOWN} = the floor (the top of the
 * block below), a horizontal direction = a wall (the side of the block next to it in that direction),
 * {@link Direction#UP} = the ceiling (the underside of the block above: the insect hangs upside down).
 *
 * <p>Or the insect is inside a narrow channel through the block in that cell ({@link Channel}, a termite tunnel):
 * {@code inChannel}, and then the surface is always {@link Direction#DOWN} (the legs work out how the body lies in it).
 */
public record Foothold(BlockPos cell, Direction surface, boolean inChannel) {
    public Foothold {
        cell = cell.immutable();
    }

    public Foothold(BlockPos cell, Direction surface) {
        this(cell, surface, false);
    }

    public static Foothold floor(BlockPos cell) {
        return new Foothold(cell, Direction.DOWN);
    }

    public static Foothold ceiling(BlockPos cell) {
        return new Foothold(cell, Direction.UP);
    }

    /** Inside the channel of the block in this cell. */
    public static Foothold channel(BlockPos cell) {
        return new Foothold(cell, Direction.DOWN, true);
    }

    public boolean isFloor() {
        return surface == Direction.DOWN && !inChannel;
    }

    public boolean isWall() {
        return surface.getAxis().isHorizontal() && !inChannel;
    }

    public boolean isCeiling() {
        return surface == Direction.UP && !inChannel;
    }

    public boolean isChannel() {
        return inChannel;
    }

    /** A slope this low (in pixels, at its highest) leaves room for an insect over it in its own cell, even under a ceiling. */
    static final int LOW_SLOPE_PIXELS = 8;

    /**
     * Whether an insect can hold on here: the cell is open and the block on that side has a solid face toward it. A
     * slope's cell has a floor too (its surface), if there is room over it: the cell above is open, or the slope is low
     * (at most {@link #LOW_SLOPE_PIXELS}, like a fungus comb on the floor of a room one block tall); nothing else in it
     * can be held. A channel foothold: the block in the cell has a channel the insect fits through, open out of at
     * least one side.
     */
    public boolean isValid(CellMap map) {
        if (inChannel) {
            return map.channelSides(cell) != 0;
        }
        return isValid(map, cell, surface);
    }

    static boolean isValid(CellMap map, BlockPos cell, Direction surface) {
        if (surface == Direction.DOWN && map.isSlope(cell)) {
            return map.isOpen(cell.above()) || map.slopeTop(cell) <= LOW_SLOPE_PIXELS;
        }
        return map.isOpen(cell) && map.isSolidFace(cell.relative(surface), surface.getOpposite());
    }

    @Override
    public String toString() {
        return cell.toShortString() + (inChannel ? " channel" : isFloor() ? " floor" : isCeiling() ? " ceiling" : " " + surface.getSerializedName() + " wall");
    }
}
