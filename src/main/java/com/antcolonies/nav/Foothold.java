package com.antcolonies.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a climbing insect is: the cell (block space) it is in, and the surface in that cell it holds on to.
 * {@code surface} points from the insect to the block it stands on: {@link Direction#DOWN} = the floor (the top of the
 * block below), a horizontal direction = a wall (the side of the block next to it in that direction),
 * {@link Direction#UP} = the ceiling (the underside of the block above: the insect hangs upside down).
 */
public record Foothold(BlockPos cell, Direction surface) {
    public Foothold {
        cell = cell.immutable();
    }

    public static Foothold floor(BlockPos cell) {
        return new Foothold(cell, Direction.DOWN);
    }

    public static Foothold ceiling(BlockPos cell) {
        return new Foothold(cell, Direction.UP);
    }

    public boolean isFloor() {
        return surface == Direction.DOWN;
    }

    public boolean isWall() {
        return surface.getAxis().isHorizontal();
    }

    public boolean isCeiling() {
        return surface == Direction.UP;
    }

    /** Whether an insect can hold on here: the cell is open and the block on that side has a solid face toward it. */
    public boolean isValid(CellMap map) {
        return isValid(map, cell, surface);
    }

    static boolean isValid(CellMap map, BlockPos cell, Direction surface) {
        return map.isOpen(cell) && map.isSolidFace(cell.relative(surface), surface.getOpposite());
    }

    @Override
    public String toString() {
        return cell.toShortString() + (isFloor() ? " floor" : isCeiling() ? " ceiling" : " " + surface.getSerializedName() + " wall");
    }
}
