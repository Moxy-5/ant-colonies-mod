package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Plans a runway of mud sheeting (the user's plan of 2026-10-14): real Macrotermes plaster a covered runway of soil
 * over the ground from a hole out to food near it, and walk and eat under it unseen. The runway is a row of cells along
 * the ground, each the cell a termite walks in (the air just over the ground), face to face from the cell over the hole
 * to a cell beside the food. Where the ground steps up or down by one block, the cell over the lower step is part of it
 * too (the termite climbs the step inside the runway). The workers then plaster a crust of mud over every open side of
 * it (its roof and walls, see {@link NestWalls}). Species-neutral, and kept apart from the world so it can be tested
 * on its own.
 */
public final class RunwayRoute {
    /** Entering a cell on the ground costs this; a cell over a lower step (a climb) costs more, so a flat way wins. */
    private static final int GROUND_COST = 1;
    private static final int CLIMB_COST = 3;
    /** At most this many cells are looked at. */
    private static final int MAX_SEARCH = 4000;

    private RunwayRoute() {
    }

    /** What the planner needs to know about the world. */
    public interface Ground {
        /** The height of the top ground block of this column (not counting plants or fallen wood on it), or null if unknown. */
        @Nullable
        Integer groundTop(int x, int z);

        /** Whether a runway may run through this cell: open air or a plant, no water, nothing of the colony's in it. */
        boolean isFree(BlockPos cell);
    }

    /**
     * The runway's cells from {@code start} (the cell over the hole) to a cell beside one of the {@code food} blocks,
     * in order, each touching the one before it face to face; null if there is none of at most {@code maxLength} cells.
     */
    @Nullable
    public static List<BlockPos> plan(BlockPos start, Set<BlockPos> food, Ground ground, int maxLength) {
        if (food.isEmpty()) {
            return null;
        }
        Map<BlockPos, Integer> cost = new HashMap<>();
        Map<BlockPos, Integer> length = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        PriorityQueue<BlockPos> queue = new PriorityQueue<>((a, b) -> Integer.compare(cost.get(a), cost.get(b)));
        cost.put(start, 0);
        length.put(start, 1);
        queue.add(start);
        int looked = 0;
        while (!queue.isEmpty() && looked++ < MAX_SEARCH) {
            BlockPos at = queue.poll();
            if (isBesideFood(at, food)) {
                List<BlockPos> cells = new ArrayList<>();
                for (BlockPos cell = at; cell != null; cell = cameFrom.get(cell)) {
                    cells.add(0, cell);
                }
                return cells;
            }
            if (length.get(at) >= maxLength) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                int kind = kind(next, ground);
                if (kind == 0 || food.contains(next)) {
                    continue;
                }
                // Up or down only between the two cells of a column (a climb over a step); never up into open air, and
                // never along the side of a step (from one climb cell to another).
                if (direction.getAxis().isVertical() ? !(kind == 2 && direction == Direction.UP || kind == 1 && direction == Direction.DOWN)
                        : kind == 2 && kind(at, ground) == 2) {
                    continue;
                }
                int newCost = cost.get(at) + (kind == 1 ? GROUND_COST : CLIMB_COST);
                Integer old = cost.get(next);
                if (old == null || newCost < old) {
                    cost.put(next, newCost);
                    length.put(next, length.get(at) + 1);
                    cameFrom.put(next, at);
                    queue.remove(next);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    /**
     * 1: a cell just over the ground (a termite walks in it); 2: the cell over that one, where the next column is a
     * step higher (it climbs the step there); 0: no runway cell.
     */
    private static int kind(BlockPos cell, Ground ground) {
        Integer top = ground.groundTop(cell.getX(), cell.getZ());
        if (top == null || !ground.isFree(cell)) {
            return 0;
        }
        if (cell.getY() == top + 1) {
            return 1;
        }
        if (cell.getY() == top + 2 && ground.isFree(cell.below())) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Integer next = ground.groundTop(cell.getX() + side.getStepX(), cell.getZ() + side.getStepZ());
                if (next != null && next == top + 1) {
                    return 2;
                }
            }
        }
        return 0;
    }

    private static boolean isBesideFood(BlockPos cell, Set<BlockPos> food) {
        for (Direction direction : Direction.values()) {
            if (food.contains(cell.relative(direction))) {
                return true;
            }
        }
        return false;
    }
}
