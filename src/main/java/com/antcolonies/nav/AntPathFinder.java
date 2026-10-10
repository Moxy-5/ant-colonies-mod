package com.antcolonies.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Plans a route for a tiny climbing insect, cell by cell (one cell = one block).
 *
 * <p>Unlike the vanilla pathfinder, this one knows that an ant can cling to any solid wall, so a
 * route can go along a floor, up a shaft wall, over the top of a ledge, or drop down a hole.
 * Moves, with their cost (a lower total cost is a shorter route):
 * <ul>
 * <li><b>walk</b> to a side cell, only while standing on a floor (1; 1.5 if the cell has no floor, i.e. stepping off a ledge)</li>
 * <li><b>climb up</b> a wall: the cell above, while a solid wall runs beside both cells (1.5)</li>
 * <li><b>climb over</b> a wall's top: up and across onto the wall block (2)</li>
 * <li><b>fall</b> into the open cell below (1): also how an ant goes down a shaft</li>
 * </ul>
 * Nothing here is specific to ants, so termites can use the same planner.
 */
public final class AntPathFinder {
    /** Stops a search that can't find the goal, so an unreachable goal never costs much time. */
    public static final int DEFAULT_MAX_EXPANSIONS = 1500;

    private AntPathFinder() {
    }

    /** Receives the cells an ant can move to from one cell, with the cost of that move. */
    public interface MoveSink {
        void add(BlockPos to, double cost);
    }

    private static final class Node {
        final BlockPos pos;
        final double cost;
        final double estimate;
        final Node parent;

        Node(BlockPos pos, double cost, double estimate, Node parent) {
            this.pos = pos;
            this.cost = cost;
            this.estimate = estimate;
            this.parent = parent;
        }
    }

    /**
     * The route from {@code start} to {@code goal}, both included, or null if there is none
     * (or it is too far to find within {@code maxExpansions} steps of searching).
     */
    @Nullable
    public static List<BlockPos> find(CellMap map, BlockPos start, BlockPos goal, int maxExpansions) {
        start = start.immutable();
        goal = goal.immutable();
        if (start.equals(goal)) {
            List<BlockPos> single = new ArrayList<>();
            single.add(start);
            return single;
        }
        if (!map.isOpen(goal)) {
            return null;
        }

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimate, b.estimate));
        Map<BlockPos, Double> bestCost = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        open.add(new Node(start, 0.0, distance(start, goal), null));
        bestCost.put(start, 0.0);

        int expansions = 0;
        while (!open.isEmpty() && expansions < maxExpansions) {
            Node current = open.poll();
            if (!closed.add(current.pos)) {
                continue; // an older, worse entry for a cell that was already finished
            }
            if (current.pos.equals(goal)) {
                return buildRoute(current);
            }
            expansions++;
            final BlockPos target = goal;
            neighbours(map, current.pos, (to, moveCost) -> {
                if (closed.contains(to)) {
                    return;
                }
                double cost = current.cost + moveCost;
                Double known = bestCost.get(to);
                if (known == null || cost < known) {
                    BlockPos immutable = to.immutable();
                    bestCost.put(immutable, cost);
                    open.add(new Node(immutable, cost, cost + distance(immutable, target), current));
                }
            });
        }
        return null;
    }

    private static double distance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static List<BlockPos> buildRoute(Node end) {
        List<BlockPos> route = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) {
            route.add(node.pos);
        }
        Collections.reverse(route);
        return route;
    }

    /** Every move an ant can make from this cell. Public so other code (wandering) can reuse the movement rules. */
    public static void neighbours(CellMap map, BlockPos p, MoveSink out) {
        BlockPos above = p.above();
        BlockPos below = p.below();
        boolean standing = map.isSolidFace(below, Direction.UP);
        boolean aboveOpen = map.isOpen(above);

        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = p.relative(side);
            if (map.isSolidFace(beside, side.getOpposite())) {
                // A wall: climb up over its top, if the block on top of it is a place to stand.
                BlockPos over = beside.above();
                if (aboveOpen && map.isOpen(over) && map.isSolidFace(beside, Direction.UP)) {
                    out.add(over, 2.0);
                }
            } else if (standing && map.isOpen(beside)) {
                out.add(beside, map.isSolidFace(beside.below(), Direction.UP) ? 1.0 : 1.5);
            }
        }

        // Straight up a wall: the same wall must run beside this cell and the one above it.
        if (aboveOpen) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (map.isSolidFace(p.relative(side), side.getOpposite())
                        && map.isSolidFace(above.relative(side), side.getOpposite())) {
                    out.add(above, 1.5);
                    break;
                }
            }
        }

        // Down: slide down a wall or fall (ants take no fall damage).
        if (map.isOpen(below)) {
            out.add(below, 1.0);
        }
    }
}
