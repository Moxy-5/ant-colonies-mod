package com.antcolonies.nav;

import com.antcolonies.debug.Perf;

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
 * Plans a route for a tiny climbing insect over the surfaces of the world: floors, walls and ceilings (see {@link Foothold}).
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
 * <li>{@link StepKind#ONTO_CEILING} / {@link StepKind#OFF_CEILING} (0.8): from the top of a wall onto the ceiling and back</li>
 * <li>{@link StepKind#CRAWL_CEILING} (1.3): upside down along a ceiling; a little dearer than a floor, so a route only
 * takes the ceiling where it is a real shortcut (a mind can ask for a ceiling spot on purpose)</li>
 * <li>{@link StepKind#UNDER_EDGE} / {@link StepKind#OUT_FROM_UNDER} (2): round a block's bottom edge between its side
 * face and its underside (an overhang)</li>
 * <li>{@link StepKind#FALL} (3 + the height): let go and drop, only where nothing can be climbed down (or off a ceiling)</li>
 * </ul>
 * A slope ({@link Slope}, a ramp) is a floor in its own cell: an insect walks onto it, along it and off it wherever the
 * two surfaces meet without a step of more than half a block anywhere along the edge between them (up its high side
 * onto the block beside it, down onto it from there, across onto a ramp beside it, off its low side). Where a wall
 * goes on up from its high side the insect steps onto the wall and back ({@link StepKind#ONTO_WALL},
 * {@link StepKind#OFF_WALL}); it climbs over a wall onto a slope's low side, and goes over a slope's low side down the
 * block under it, but never over a slope's high side (the ramp's own steep face is there).
 * A block with a channel through it ({@link Channel}, a termite tunnel) is a channel foothold for an insect that fits:
 * {@link StepKind#CRAWL_CHANNEL} on into the channel of the block beside where both open toward each other (1, up or
 * down 1.1), {@link StepKind#INTO_CHANNEL} / {@link StepKind#OUT_OF_CHANNEL} by an opening (1): a hole in a block's
 * side from the floor in front of it or the wall it is in, a hole in a floor from above, a hole in a ceiling from
 * below. A chimney mouth's channel ends in the mouth on top: {@link StepKind#OUT_OF_MOUTH} / {@link StepKind#INTO_MOUTH}
 * are the doorway's own motion, up and down the outside of the tube, onto the top of the block beside or the outside of
 * the mouth's own column (3).
 * Turning over onto a ceiling costs a little extra, so a route never flips upside down for a tiny shortcut (like
 * curling from the tunnel ceiling up into the shaft instead of walking the last block).
 * A mind can add its own costs to any step ({@link StepCosts}: later scent trails, avoiding enemies, raids). Nothing
 * here is specific to ants, so termites and later species use the same planner.
 */
public final class AntPathFinder {
    /** Cost of crawling along a channel to the next block, sideways; up or down a little more. */
    public static final double CHANNEL_COST = 1.0;
    private static final double CHANNEL_UP_DOWN_COST = 1.1;
    /** Cost of going into or out of a channel by an opening. */
    private static final double CHANNEL_DOOR_COST = 1.0;
    /** Cost of coming out of a chimney mouth (up the tube, a look round, down the outside) or going in by it. */
    private static final double MOUTH_COST = 3.0;
    /** Cost of one block upside down along a ceiling. */
    public static final double CEILING_COST = 1.3;
    /** Stops a search that can't find the goal, so an unreachable goal never costs much time. */
    public static final int DEFAULT_MAX_EXPANSIONS = 6000;
    /** The deepest drop a route may take (where nothing can be climbed down). */
    public static final int MAX_FALL = 12;
    /** The tallest step (in pixels) a walk may take between two floors: half a block. */
    public static final int MAX_STEP_PIXELS = 8;
    /** A slope's side this low (pixels, at its highest) is an edge to climb over or go over like a floor's edge. */
    public static final int LOW_EDGE_PIXELS = 4;
    /** A slope's side this high (pixels, at its lowest) meets a wall going on up from it. */
    public static final int HIGH_EDGE_PIXELS = 14;
    /**
     * Extra cost of a move along a wall (sideways, round a corner or an edge) that ends where there is a floor to stand
     * on: there the insect steps off the wall and walks, instead of leaning on the wall while standing (an insect told
     * to hold a wall low down, beside a spire, starts its next walk there).
     */
    private static final double FOOT_OF_WALL = 2.0;

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
     * cell above the start, over an edge or round an edge the cell beside it, under or out from under a block the cell
     * below the wall). They must stay open while it is walked.
     */
    public static List<BlockPos> passesThrough(Foothold from, Foothold to, StepKind kind) {
        return switch (kind) {
            case OVER_TOP -> List.of(from.cell().above());
            case OVER_EDGE -> List.of(to.cell().above());
            case ROUND_EDGE -> List.of(to.cell().relative(from.surface().getOpposite()));
            case UNDER_EDGE -> List.of(from.cell().below());
            case OUT_FROM_UNDER -> List.of(to.cell().below());
            case OUT_OF_MOUTH -> mouthWay(from.cell(), to);
            case INTO_MOUTH -> mouthWay(to.cell(), from);
            default -> List.of();
        };
    }

    /** The open cells a move out of (or into) a chimney mouth passes: the space of the tube over it, and over the cell beside. */
    private static List<BlockPos> mouthWay(BlockPos mouth, Foothold outside) {
        return outside.cell().getY() == mouth.getY() ? List.of(mouth.above(), outside.cell().above()) : List.of(mouth.above());
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
        if (!map.isOpen(target) && !map.isSlope(target) && map.channelSides(target) == 0) {
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
        boolean timed = Perf.begin(Perf.Part.ROUTES);
        long started = System.nanoTime();
        int[] steps = new int[1];
        Route route = null;
        try {
            route = search(map, starts, isGoal, towards, maxExpansions, costs, steps);
            return route;
        } finally {
            if (timed) {
                Perf.end();
                Perf.search(steps[0], route != null, System.nanoTime() - started);
            }
        }
    }

    @Nullable
    private static Route search(CellMap map, List<Foothold> starts, Predicate<Foothold> isGoal, BlockPos towards,
            int maxExpansions, StepCosts costs, int[] steps) {
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
                steps[0] = expansions;
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
        steps[0] = expansions;
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

    /**
     * Every surface an insect could start from in this cell: the floor first, then the walls. Empty if the cell is not
     * open. (Not the ceiling: an insect is only up there if it climbed up on purpose, and then it knows.)
     */
    public static List<Foothold> footholdsIn(CellMap map, BlockPos cell) {
        List<Foothold> found = new ArrayList<>();
        if (map.channelSides(cell) != 0) {
            found.add(Foothold.channel(cell));
            return found;
        }
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
            if (map.isSlope(at)) {
                return Foothold.isValid(map, at, Direction.DOWN) ? Foothold.floor(at) : null;
            }
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
        if (from.isChannel()) {
            channelNeighbours(map, from, out);
            return;
        }
        intoChannels(map, from, out);
        if (from.isFloor()) {
            boolean onSlope = map.isSlope(c);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                // The wall at the side of this cell: step up onto it.
                if (Foothold.isValid(map, c, d)) {
                    out.add(new Foothold(c, d), StepKind.ONTO_WALL, 0.5);
                }
                // At the high side of a slope a wall goes on up from it: step onto it.
                if (onSlope && edgeLowest(map, c, d) >= HIGH_EDGE_PIXELS && Foothold.isValid(map, c.above(), d)) {
                    out.add(new Foothold(c.above(), d), StepKind.ONTO_WALL, 0.5);
                }
                BlockPos n = c.relative(d);
                if (map.isSlope(n)) {
                    if (Foothold.isValid(map, n, Direction.DOWN) && edgesMeet(map, c, d, n)) {
                        out.add(Foothold.floor(n), StepKind.WALK, 1.0);
                    }
                    continue;
                }
                if (!map.isOpen(n)) {
                    // Up the high side of a slope onto the block beside it (or onto a slope on that block).
                    BlockPos up = n.above();
                    if (onSlope && Foothold.isValid(map, up, Direction.DOWN) && edgesMeet(map, c, d, up)) {
                        out.add(Foothold.floor(up), StepKind.WALK, 1.0);
                    }
                    continue;
                }
                if (Foothold.isValid(map, n, Direction.DOWN)) {
                    if (edgesMeet(map, c, d, n)) {
                        out.add(Foothold.floor(n), StepKind.WALK, 1.0);
                    }
                } else if (map.isSlope(n.below())) {
                    // Down onto the high side of a slope.
                    if (Foothold.isValid(map, n.below(), Direction.DOWN) && edgesMeet(map, c, d, n.below())) {
                        out.add(Foothold.floor(n.below()), StepKind.WALK, 1.0);
                    }
                } else if (map.isOpen(n.below()) && (!onSlope || edgeHighest(map, c, d) <= LOW_EDGE_PIXELS)) {
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
        if (from.isCeiling()) {
            ceilingNeighbours(map, from, out);
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
        // At the foot of the wall a slope rises to it: step off onto its high side.
        if (map.isSlope(c.below()) && Foothold.isValid(map, c.below(), Direction.DOWN) && edgeLowest(map, c.below(), w) >= HIGH_EDGE_PIXELS) {
            out.add(Foothold.floor(c.below()), StepKind.OFF_WALL, 0.5);
            climbs = true;
        }
        for (Direction side : new Direction[] {w.getClockWise(), w.getCounterClockWise()}) {
            if (Foothold.isValid(map, c, side)) {
                out.add(new Foothold(c, side), StepKind.TURN_CORNER, 0.6 + footOfWall(map, c));
            }
            BlockPos n = c.relative(side);
            if (!map.isOpen(n)) {
                continue;
            }
            if (Foothold.isValid(map, n, w)) {
                out.add(new Foothold(n, w), StepKind.CRAWL_SIDEWAYS, 1.2 + footOfWall(map, n));
            } else if (Foothold.isValid(map, n.relative(w), side.getOpposite())) {
                // The wall ends beside the insect: round the block's vertical edge onto its side face.
                out.add(new Foothold(n.relative(w), side.getOpposite()), StepKind.ROUND_EDGE, 1.2 + footOfWall(map, n.relative(w)));
            }
        }
        // Over the top of the wall onto the block.
        BlockPos top = c.relative(w).above();
        if (map.isOpen(c.above()) && Foothold.isValid(map, top, Direction.DOWN)
                && (!map.isSlope(top) || edgeHighest(map, top, w.getOpposite()) <= LOW_EDGE_PIXELS)) {
            out.add(Foothold.floor(top), StepKind.OVER_TOP, 1.5);
        }
        // Up into the corner and onto the ceiling.
        if (Foothold.isValid(map, c, Direction.UP)) {
            out.add(Foothold.ceiling(c), StepKind.ONTO_CEILING, 0.8);
        }
        // The wall ends below and the block hangs over open space: round its bottom edge onto its underside.
        BlockPos under = c.relative(w).below();
        if (map.isOpen(c.below()) && Foothold.isValid(map, under, Direction.UP)) {
            out.add(Foothold.ceiling(under), StepKind.UNDER_EDGE, 2.0);
        }
        // The wall ends below and there is no floor: nothing left but to let go.
        if (!climbs && map.isOpen(c.below())) {
            addFall(map, c.below(), out);
        }
    }

    /**
     * The moves from a channel: on into the channel beside where both open toward each other, or out by an opening onto
     * the floor or the wall in front of a side hole, the floor over a hole on top, or the ceiling under a hole below. Out
     * of a chimney mouth's tube: onto the top of a block beside the mouth, or the outside of the mouth's own column.
     */
    private static void channelNeighbours(CellMap map, Foothold from, MoveSink out) {
        BlockPos c = from.cell();
        int sides = map.channelSides(c);
        for (Direction d : Direction.values()) {
            if (!Channel.has(sides, d)) {
                continue;
            }
            BlockPos n = c.relative(d);
            if (d == Direction.UP && map.isMouth(c)) {
                if (map.isOpen(n)) {
                    for (Direction e : Direction.Plane.HORIZONTAL) {
                        BlockPos beside = c.relative(e);
                        if (Foothold.isValid(map, beside.above(), Direction.DOWN)) {
                            out.add(Foothold.floor(beside.above()), StepKind.OUT_OF_MOUTH, MOUTH_COST);
                        }
                        if (map.isOpen(beside.above()) && Foothold.isValid(map, beside, e.getOpposite())) {
                            out.add(new Foothold(beside, e.getOpposite()), StepKind.OUT_OF_MOUTH, MOUTH_COST);
                        }
                    }
                }
                continue;
            }
            if (Channel.has(map.channelSides(n), d.getOpposite())) {
                out.add(Foothold.channel(n), StepKind.CRAWL_CHANNEL, d.getAxis().isVertical() ? CHANNEL_UP_DOWN_COST : CHANNEL_COST);
                continue;
            }
            if (!map.isOpen(n)) {
                continue;
            }
            if (d.getAxis().isHorizontal()) {
                if (Foothold.isValid(map, n, Direction.DOWN)) {
                    out.add(Foothold.floor(n), StepKind.OUT_OF_CHANNEL, CHANNEL_DOOR_COST);
                }
                if (Foothold.isValid(map, n, d.getOpposite())) {
                    out.add(new Foothold(n, d.getOpposite()), StepKind.OUT_OF_CHANNEL, CHANNEL_DOOR_COST);
                }
            } else if (d == Direction.UP) {
                if (Foothold.isValid(map, n, Direction.DOWN)) {
                    out.add(Foothold.floor(n), StepKind.OUT_OF_CHANNEL, CHANNEL_DOOR_COST);
                }
            } else if (Foothold.isValid(map, n, Direction.UP)) {
                out.add(Foothold.ceiling(n), StepKind.OUT_OF_CHANNEL, CHANNEL_DOOR_COST);
            }
        }
    }

    /**
     * The ways into a channel from a floor, wall or ceiling: by a hole in the side of a block in front of a floor, in the
     * wall it holds, in the floor under it or the ceiling over it; and by a chimney mouth's tube from the top of the
     * block beside the mouth or from the outside of the mouth's own column.
     */
    private static void intoChannels(CellMap map, Foothold from, MoveSink out) {
        BlockPos c = from.cell();
        if (from.isFloor() && !map.isSlope(c)) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos block = c.relative(d);
                if (!map.isMouth(block) && Channel.has(map.channelSides(block), d.getOpposite())) {
                    out.add(Foothold.channel(block), StepKind.INTO_CHANNEL, CHANNEL_DOOR_COST);
                }
                BlockPos mouth = c.below().relative(d);
                if (map.isMouth(mouth) && Channel.has(map.channelSides(mouth), Direction.UP) && map.isOpen(mouth.above())) {
                    out.add(Foothold.channel(mouth), StepKind.INTO_MOUTH, MOUTH_COST);
                }
            }
            BlockPos below = c.below();
            if (!map.isMouth(below) && Channel.has(map.channelSides(below), Direction.UP)) {
                out.add(Foothold.channel(below), StepKind.INTO_CHANNEL, CHANNEL_DOOR_COST);
            }
        } else if (from.isWall()) {
            BlockPos block = c.relative(from.surface());
            if (map.isMouth(block)) {
                if (Channel.has(map.channelSides(block), Direction.UP) && map.isOpen(block.above()) && map.isOpen(c.above())) {
                    out.add(Foothold.channel(block), StepKind.INTO_MOUTH, MOUTH_COST);
                }
            } else if (Channel.has(map.channelSides(block), from.surface().getOpposite())) {
                out.add(Foothold.channel(block), StepKind.INTO_CHANNEL, CHANNEL_DOOR_COST);
            }
        } else if (from.isCeiling()) {
            BlockPos above = c.above();
            if (!map.isMouth(above) && Channel.has(map.channelSides(above), Direction.DOWN)) {
                out.add(Foothold.channel(above), StepKind.INTO_CHANNEL, CHANNEL_DOOR_COST);
            }
        }
    }

    /** The moves from a ceiling: down a wall of the cell, on along the ceiling, out round its edge, or let go. */
    private static void ceilingNeighbours(CellMap map, Foothold from, MoveSink out) {
        BlockPos c = from.cell();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (Foothold.isValid(map, c, d)) {
                out.add(new Foothold(c, d), StepKind.OFF_CEILING, 0.8);
            }
            BlockPos n = c.relative(d);
            if (!map.isOpen(n)) {
                continue;
            }
            if (Foothold.isValid(map, n, Direction.UP)) {
                out.add(Foothold.ceiling(n), StepKind.CRAWL_CEILING, CEILING_COST);
            } else if (Foothold.isValid(map, n.above(), d.getOpposite())) {
                // The ceiling ends: round the block's bottom edge and up its side face.
                out.add(new Foothold(n.above(), d.getOpposite()), StepKind.OUT_FROM_UNDER, 2.0);
            }
        }
        addFall(map, c, out);
    }

    /**
     * The height (in pixels from y = 0) of a floor cell's surface along its edge on {@code side}, at each of the 8
     * columns along it (in the order of the world axis along the edge): a plain floor is flat at the bottom of the cell,
     * a slope rises as its surface does.
     */
    public static int[] floorEdge(CellMap map, BlockPos cell, Direction side) {
        int[] edge = new int[Slope.COLUMNS];
        int base = cell.getY() * Slope.PIXELS;
        boolean slope = map.isSlope(cell);
        int last = Slope.COLUMNS - 1;
        for (int i = 0; i < Slope.COLUMNS; i++) {
            int height = 0;
            if (slope) {
                int u = side == Direction.EAST ? last : side == Direction.WEST ? 0 : i;
                int w = side == Direction.SOUTH ? last : side == Direction.NORTH ? 0 : i;
                height = map.slopeHeight(cell, u, w);
            }
            edge[i] = base + height;
        }
        return edge;
    }

    /** Whether the floors of two cells side by side meet without a step of more than half a block anywhere along the edge. */
    static boolean edgesMeet(CellMap map, BlockPos from, Direction side, BlockPos to) {
        if (!map.isSlope(from) && !map.isSlope(to)) {
            return from.getY() == to.getY();
        }
        int[] a = floorEdge(map, from, side);
        int[] b = floorEdge(map, to, side.getOpposite());
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > MAX_STEP_PIXELS) {
                return false;
            }
        }
        return true;
    }

    /** The lowest point (pixels above the cell's bottom) of a slope's surface along its edge on {@code side}. */
    public static int edgeLowest(CellMap map, BlockPos cell, Direction side) {
        int lowest = Integer.MAX_VALUE;
        for (int h : floorEdge(map, cell, side)) {
            lowest = Math.min(lowest, h - cell.getY() * Slope.PIXELS);
        }
        return lowest;
    }

    /** The highest point (pixels above the cell's bottom) of a slope's surface along its edge on {@code side}. */
    public static int edgeHighest(CellMap map, BlockPos cell, Direction side) {
        int highest = 0;
        for (int h : floorEdge(map, cell, side)) {
            highest = Math.max(highest, h - cell.getY() * Slope.PIXELS);
        }
        return highest;
    }

    /**
     * Whether a move along a route can still be made in the world as it is now: the footholds it goes from and to are
     * there, the cells it passes through are open, between two channels both still open toward each other (a window
     * closed, a block put in the way), and a chimney mouth it goes in or out by is still a mouth. Null if it can, else
     * what changed.
     */
    @Nullable
    public static String whatChanged(CellMap map, @Nullable Foothold from, Foothold to, StepKind kind) {
        if (!to.isValid(map)) {
            return "nothing to hold at " + to;
        }
        if (from == null) {
            return null;
        }
        if (!from.isValid(map) && kind != StepKind.FALL) {
            return "lost its hold at " + from;
        }
        if (passesThrough(from, to, kind).stream().anyMatch(cell -> !map.isOpen(cell))) {
            return "blocked on the way from " + from + " to " + to;
        }
        // A mouth reshaped into a spire piece while an insect was going in or coming out of it (the mound growing).
        if (kind == StepKind.OUT_OF_MOUTH && !map.isMouth(from.cell())) {
            return "no mouth any more at " + from;
        }
        if (kind == StepKind.INTO_MOUTH && !map.isMouth(to.cell())) {
            return "no mouth any more at " + to;
        }
        if (kind == StepKind.CRAWL_CHANNEL) {
            Direction d = Direction.getNearest(to.cell().getX() - from.cell().getX(), to.cell().getY() - from.cell().getY(),
                    to.cell().getZ() - from.cell().getZ());
            if (!Channel.has(map.channelSides(from.cell()), d) || !Channel.has(map.channelSides(to.cell()), d.getOpposite())) {
                return "the channel from " + from + " to " + to + " closed";
            }
        }
        return null;
    }

    /** {@link #FOOT_OF_WALL} where a move along a wall ends in a cell with a floor, else nothing. */
    private static double footOfWall(CellMap map, BlockPos cell) {
        return Foothold.isValid(map, cell, Direction.DOWN) ? FOOT_OF_WALL : 0.0;
    }

    private static void addFall(CellMap map, BlockPos from, MoveSink out) {
        Foothold landing = landing(map, from);
        if (landing != null) {
            out.add(landing, StepKind.FALL, 3.0 + (from.getY() - landing.cell().getY()) + 1);
        }
    }
}
