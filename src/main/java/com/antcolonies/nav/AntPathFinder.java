package com.antcolonies.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Plans a route for a tiny climbing insect over the surfaces of the world: floors and walls (see {@link Foothold}).
 *
 * <p>The insect always holds on to a surface, so a route never crosses open air: to get to another wall it crawls
 * round the corner, to get up a step it climbs the wall and over the top, to get down a hole it goes over the edge and
 * climbs down head first. Only where there is nothing to climb down does it let go and fall. The moves and their cost
 * (a lower total is a better route; about 1 per block):
 * <ul>
 * <li>{@link StepKind#WALK} along a floor to the next cell (1)</li>
 * <li>{@link StepKind#ONTO_WALL} / {@link StepKind#OFF_WALL}: from the floor onto a wall of the same cell and back (0.5)</li>
 * <li>{@link StepKind#CLIMB_UP} (1.2), {@link StepKind#CLIMB_DOWN} head first (1.1), {@link StepKind#CRAWL_SIDEWAYS} (1.2) along a wall</li>
 * <li>{@link StepKind#TURN_CORNER} (0.6): an inside corner, onto the next wall of the same cell</li>
 * <li>{@link StepKind#ROUND_EDGE} (1.2): an outside edge, where a wall ends sideways, onto the block's side face</li>
 * <li>{@link StepKind#OVER_TOP} (1.5): up over the top edge of a wall onto the block; {@link StepKind#OVER_EDGE} (1.5): off a
 * floor's edge onto the side of the block below</li>
 * <li>{@link StepKind#FALL} (3 + the height): let go and drop, only where nothing can be climbed down</li>
 * </ul>
 * A mind can add its own costs to any step ({@link StepCosts}: later scent trails, avoiding enemies, raids). Nothing
 * here is specific to ants, so termites and later species use the same planner.
 */
public final class AntPathFinder {
    /** Stops a search that can't find the goal, so an unreachable goal never costs much time. */
    public static final int DEFAULT_MAX_EXPANSIONS = 6000;
    /** The deepest drop a route may take (where nothing can be climbed down). */
    public static final int MAX_FALL = 12;

    private AntPathFinder() {
    }

    /** Receives the moves an insect can make from a foothold. */
    public interface MoveSink {
        void add(Foothold to, StepKind kind, double cost);
    }

    /** Extra cost a mind adds to a move (0 = none); {@link Double#POSITIVE_INFINITY} rules the move out. */
    public interface StepCosts {
        StepCosts NONE = (from, to, kind) -> 0.0;

        double extra(Foothold from, Foothold to, StepKind kind);

        /** No move into (or through) any of these cells: for example the hole an ant is about to seal. */
        static StepCosts forbid(java.util.Collection<BlockPos> cells) {
            return (from, to, kind) -> cells.contains(to.cell()) || passesThrough(from, to, kind).stream().anyMatch(cells::contains)
                    ? Double.POSITIVE_INFINITY : 0.0;
        }

        /** Both together. */
        default StepCosts and(StepCosts other) {
            return (from, to, kind) -> extra(from, to, kind) + other.extra(from, to, kind);
        }
    }

    /**
     * The cells a move passes through on its way besides where it starts and ends (going over a top edge passes the
     * cell above the start, over an edge or round an edge the cell beside it). They must stay open while it is walked.
     */
    public static List<BlockPos> passesThrough(Foothold from, Foothold to, StepKind kind) {
        return switch (kind) {
            case OVER_TOP -> List.of(from.cell().above());
            case OVER_EDGE -> List.of(to.cell().above());
            case ROUND_EDGE -> List.of(to.cell().relative(from.surface().getOpposite()));
            default -> List.of();
        };
    }

    private static final class Node {
        final Foothold at;
        final StepKind kind;
        final double cost;
        final double estimate;
        final Node parent;

        Node(Foothold at, StepKind kind, double cost, double estimate, Node parent) {
            this.at = at;
            this.kind = kind;
            this.cost = cost;
            this.estimate = estimate;
            this.parent = parent;
        }
    }

    /**
     * A route from a cell to a cell. The insect starts on any surface it can hold in {@code start} (or, if there is
     * none, where it lands when it drops from there), and the route ends in {@code goal} (see the next method). Null if there
     * is none (or it is too far to find within {@code maxExpansions} steps of searching).
     */
    @Nullable
    public static Route find(CellMap map, BlockPos start, BlockPos goal, int maxExpansions) {
        List<Foothold> starts = footholdsIn(map, start);
        if (starts.isEmpty()) {
            Foothold landing = landing(map, start);
            if (landing == null) {
                return null;
            }
            starts = List.of(landing);
        }
        return find(map, starts, goal, maxExpansions, StepCosts.NONE);
    }

    /** A route from this foothold into the {@code goal} cell (see the next method), or null. */
    @Nullable
    public static Route find(CellMap map, Foothold start, BlockPos goal, int maxExpansions) {
        return find(map, List.of(start), goal, maxExpansions, StepCosts.NONE);
    }

    /**
     * A route from any of the {@code starts} into the {@code goal} cell, with a mind's extra costs. It ends on the cell's
     * floor if it has one (never hanging at the foot of a wall), otherwise (in a shaft) on any of its walls.
     */
    @Nullable
    public static Route find(CellMap map, List<Foothold> starts, BlockPos goal, int maxExpansions, StepCosts costs) {
        final BlockPos target = goal.immutable();
        if (!map.isOpen(target)) {
            return null;
        }
        boolean hasFloor = Foothold.isValid(map, target, Direction.DOWN);
        return find(map, starts, at -> at.cell().equals(target) && (at.isFloor() || !hasFloor), target, maxExpansions, costs);
    }

    /**
     * The general search: a route from any of the {@code starts} to the cheapest foothold {@code isGoal} accepts
     * (a mind can ask for "any cell of this room", "the nearest unexplored spot" and so on). {@code towards} is where
     * the goal roughly is, to search that way first.
     */
    @Nullable
    public static Route find(CellMap map, List<Foothold> starts, Predicate<Foothold> isGoal, BlockPos towards,
            int maxExpansions, StepCosts costs) {
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimate, b.estimate));
        Map<Foothold, Double> bestCost = new HashMap<>();
        Set<Foothold> closed = new HashSet<>();
        for (Foothold start : starts) {
            open.add(new Node(start, StepKind.START, 0.0, distance(start.cell(), towards), null));
            bestCost.put(start, 0.0);
        }
        int expansions = 0;
        while (!open.isEmpty() && expansions < maxExpansions) {
            Node current = open.poll();
            if (!closed.add(current.at)) {
                continue; // an older, worse entry for a foothold that was already finished
            }
            if (isGoal.test(current.at)) {
                return buildRoute(current);
            }
            expansions++;
            neighbours(map, current.at, (to, kind, moveCost) -> {
                if (closed.contains(to)) {
                    return;
                }
                double extra = costs.extra(current.at, to, kind);
                if (extra == Double.POSITIVE_INFINITY) {
                    return; // ruled out by the mind
                }
                double cost = current.cost + moveCost + extra;
                Double known = bestCost.get(to);
                if (known == null || cost < known) {
                    bestCost.put(to, cost);
                    open.add(new Node(to, kind, cost, cost + distance(to.cell(), towards), current));
                }
            });
        }
        return null;
    }

    private static double distance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static Route buildRoute(Node end) {
        List<Route.Step> steps = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) {
            steps.add(new Route.Step(node.at, node.parent == null ? StepKind.START : node.kind));
        }
        Collections.reverse(steps);
        return new Route(steps, end.cost);
    }

    /** Every surface an insect could hold on to in this cell: the floor first, then the walls. Empty if the cell is not open. */
    public static List<Foothold> footholdsIn(CellMap map, BlockPos cell) {
        List<Foothold> found = new ArrayList<>();
        if (Foothold.isValid(map, cell, Direction.DOWN)) {
            found.add(Foothold.floor(cell));
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (Foothold.isValid(map, cell, side)) {
                found.add(new Foothold(cell, side));
            }
        }
        return found;
    }

    /** Where something dropped in this open cell lands: the first floor at or below it, or null (too deep, or no floor to stand on). */
    @Nullable
    public static Foothold landing(CellMap map, BlockPos cell) {
        BlockPos at = cell;
        for (int i = 0; i <= MAX_FALL; i++) {
            if (!map.isOpen(at)) {
                return null;
            }
            if (map.isSolidFace(at.below(), Direction.UP)) {
                return Foothold.floor(at);
            }
            at = at.below();
        }
        return null;
    }

    /** Every move an insect can make from this foothold. Public so other code (a mind, wandering) can reuse the movement rules. */
    public static void neighbours(CellMap map, Foothold from, MoveSink out) {
        BlockPos c = from.cell();
        if (from.isFloor()) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                // The wall at the side of this cell: step up onto it.
                if (Foothold.isValid(map, c, d)) {
                    out.add(new Foothold(c, d), StepKind.ONTO_WALL, 0.5);
                }
                BlockPos n = c.relative(d);
                if (!map.isOpen(n)) {
                    continue;
                }
                if (Foothold.isValid(map, n, Direction.DOWN)) {
                    out.add(Foothold.floor(n), StepKind.WALK, 1.0);
                } else if (map.isOpen(n.below())) {
                    // A drop: over the edge and down the side of the block it stands on, head first.
                    if (Foothold.isValid(map, n.below(), d.getOpposite())) {
                        out.add(new Foothold(n.below(), d.getOpposite()), StepKind.OVER_EDGE, 1.5);
                    } else {
                        addFall(map, n, out);
                    }
                }
            }
            return;
        }
        if (!from.isWall()) {
            return;
        }
        Direction w = from.surface();
        boolean climbs = false;
        if (Foothold.isValid(map, c.above(), w)) {
            out.add(new Foothold(c.above(), w), StepKind.CLIMB_UP, 1.2);
        }
        if (Foothold.isValid(map, c.below(), w)) {
            out.add(new Foothold(c.below(), w), StepKind.CLIMB_DOWN, 1.1);
            climbs = true;
        }
        if (Foothold.isValid(map, c, Direction.DOWN)) {
            out.add(Foothold.floor(c), StepKind.OFF_WALL, 0.5);
            climbs = true;
        }
        for (Direction side : new Direction[] {w.getClockWise(), w.getCounterClockWise()}) {
            if (Foothold.isValid(map, c, side)) {
                out.add(new Foothold(c, side), StepKind.TURN_CORNER, 0.6);
            }
            BlockPos n = c.relative(side);
            if (!map.isOpen(n)) {
                continue;
            }
            if (Foothold.isValid(map, n, w)) {
                out.add(new Foothold(n, w), StepKind.CRAWL_SIDEWAYS, 1.2);
            } else if (Foothold.isValid(map, n.relative(w), side.getOpposite())) {
                // The wall ends beside the insect: round the block's vertical edge onto its side face.
                out.add(new Foothold(n.relative(w), side.getOpposite()), StepKind.ROUND_EDGE, 1.2);
            }
        }
        // Over the top of the wall onto the block.
        BlockPos top = c.relative(w).above();
        if (map.isOpen(c.above()) && Foothold.isValid(map, top, Direction.DOWN)) {
            out.add(Foothold.floor(top), StepKind.OVER_TOP, 1.5);
        }
        // The wall ends below and there is no floor: nothing left but to let go.
        if (!climbs && map.isOpen(c.below())) {
            addFall(map, c.below(), out);
        }
    }

    private static void addFall(CellMap map, BlockPos from, MoveSink out) {
        Foothold landing = landing(map, from);
        if (landing != null) {
            out.add(landing, StepKind.FALL, 3.0 + (from.getY() - landing.cell().getY()) + 1);
        }
    }
}
