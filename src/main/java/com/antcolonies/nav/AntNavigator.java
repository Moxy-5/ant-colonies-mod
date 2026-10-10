package com.antcolonies.nav;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The legs of an ant: given a goal cell, plans a route with {@link AntPathFinder} and walks it.
 *
 * <p>This is only "how to get there". Deciding "where to go and why" belongs to the goals now and to
 * the colony brain later, so a smarter mind can be put on top without touching the movement.
 *
 * <p>An ant counts as having reached a waypoint when it is inside that waypoint's cell (no fuzzy
 * distances), and the final goal is reached when it is standing near the middle of its cell.
 * On a stretch of the route that goes up, the ant clings to the wall and rises at a steady speed
 * (the entity applies {@link #getRiseSpeed()}), so climbing never depends on touching a wall.
 */
public class AntNavigator {
    /** How many waypoints ahead to check, in case the ant skipped some (for example it fell down a shaft). */
    private static final int LOOKAHEAD = 3;
    /** The ant must be this close (in blocks, sideways) to the middle of the goal cell to count as arrived. */
    private static final double ARRIVE_DISTANCE_SQR = 0.3 * 0.3;
    /** If the ant can't get to the middle of the goal cell within this many ticks, it counts as arrived anyway. */
    private static final int ARRIVE_TIMEOUT = 20;
    /** No progress for this many ticks means stuck: plan the route again. */
    private static final int STUCK_TICKS = 30;
    /** Not on the route at all for this many ticks: plan the route again. */
    private static final int OFF_ROUTE_TICKS = 20;
    private static final int MAX_REPLANS = 3;
    /** Blocks per tick an ant rises while climbing. */
    private static final double CLIMB_SPEED = 0.06;
    /** Blocks per tick an ant slides sideways while clinging (toward the wall it hugs or the ledge it climbs over). */
    private static final double CLING_SIDE_SPEED = 0.04;
    /** The ant waits to rise until it is closer than this (in blocks) to the wall it hugs. */
    private static final double WALL_GAP = 0.06;

    private final AbstractAntEntity ant;
    @Nullable
    private List<BlockPos> route;
    /** The next waypoint to reach (route[0] is where the ant started). */
    private int next;
    private BlockPos goal = BlockPos.ZERO;
    private double speed = 1.0;
    private boolean failed;
    private boolean climbing;
    /** Set by {@link #cling()} each tick the ant should hold on where it is; makes it stick to the wall instead of falling. */
    private boolean clingRequested;
    private boolean holding;
    private double rise;
    private Vec3 climbMotion = Vec3.ZERO;
    private int replans;
    private int arriveTicks;
    private int offRouteTicks;
    private int stuckTicks;
    private int stuckNext;
    private Vec3 stuckPos = Vec3.ZERO;

    public AntNavigator(AbstractAntEntity ant) {
        this.ant = ant;
    }

    /**
     * Starts walking to the goal cell. Returns false (and does nothing) if there is no route to it.
     * {@code speed} multiplies the ant's normal walking speed.
     */
    public boolean moveTo(BlockPos goal, double speed) {
        this.goal = goal.immutable();
        this.speed = speed;
        this.replans = 0;
        return plan();
    }

    /** True when there is nothing left to walk: arrived, stopped, or no route. See {@link #hasFailed()}. */
    public boolean isDone() {
        return route == null;
    }

    /** True if the last {@link #moveTo} found no route, or the ant got stuck and couldn't recover. */
    public boolean hasFailed() {
        return failed;
    }

    /** True while the ant is on an upward stretch of the route (clinging to a wall and rising). */
    public boolean isClimbing() {
        return climbing;
    }

    /**
     * The exact motion (blocks per tick) the ant should have this tick while climbing: rising toward the
     * next waypoint (or holding still when level with it) and sliding toward the wall or ledge it is aiming at.
     */
    public Vec3 getClimbMotion() {
        return climbMotion;
    }

    /** The planned route (null when idle) and which waypoint the ant is heading for: for tests and debugging. */
    @Nullable
    public List<BlockPos> getRoute() {
        return route;
    }

    public int getNextWaypoint() {
        return next;
    }

    public void stop() {
        route = null;
        failed = false;
        climbing = false;
        holding = false;
        rise = 0.0;
    }

    /**
     * Asks the ant to hold on to the wall beside it where it is (call it every tick it should hold). Ants cling to
     * any solid wall, so one can work on something high up a cliff or a tree without falling off.
     */
    public void cling() {
        clingRequested = true;
    }

    /** True while the ant is holding on to a wall (the entity then does not fall). */
    public boolean isHolding() {
        return holding;
    }

    private boolean plan() {
        List<BlockPos> found = AntPathFinder.find(
                new LevelCellMap(ant.level()), ant.blockPosition(), goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS);
        failed = found == null;
        route = found;
        next = 1;
        arriveTicks = 0;
        offRouteTicks = 0;
        resetStuckClock();
        if (found == null) {
            climbing = false;
            rise = 0.0;
        }
        return found != null;
    }

    private void resetStuckClock() {
        stuckTicks = 0;
        stuckNext = next;
        stuckPos = ant.position();
    }

    /** Called every tick from the ant, before the move control does the actual walking. */
    public void tick() {
        rise = 0.0;
        if (route == null) {
            hold();
            return;
        }
        clingRequested = false;
        holding = false;
        CellMap map = new LevelCellMap(ant.level());
        BlockPos here = ant.blockPosition();
        int last = route.size() - 1;

        // Where on the route is the ant? Look a few waypoints ahead (it may have skipped some) and
        // anywhere behind (it may have fallen back).
        int at = -1;
        for (int i = Math.min(last, next + LOOKAHEAD); i >= 0; i--) {
            if (route.get(i).equals(here)) {
                at = i;
                break;
            }
        }
        if (at >= 0) {
            next = at + 1;
            offRouteTicks = 0;
        } else if (next > last) {
            if (last == 0) {
                // The route was only the goal cell (the ant started in it) and the ant has drifted out: plan a real route back.
                replan("left the goal cell");
                return;
            }
            next = last; // wandered out of the goal cell while centring: head back
        }

        Vec3 aim;
        if (next > last) {
            // In the goal cell: finish walking to its middle.
            aim = Vec3.atBottomCenterOf(goal);
            double dx = ant.getX() - aim.x;
            double dz = ant.getZ() - aim.z;
            if (dx * dx + dz * dz <= ARRIVE_DISTANCE_SQR || ++arriveTicks > ARRIVE_TIMEOUT) {
                stop();
                return;
            }
            climbing = false;
        } else {
            BlockPos from = route.get(next - 1);
            BlockPos waypoint = route.get(next);
            if (!map.isOpen(waypoint)) {
                replan("the way ahead was blocked");
                return;
            }

            // Up stretch: straight up a wall, or up and over the top of one. The ant counts as on it
            // while in the cell it starts from, the cell it ends in, or the cell above its start (the corner).
            BlockPos corner = new BlockPos(from.getX(), waypoint.getY(), from.getZ());
            boolean up = waypoint.getY() > from.getY();
            climbing = up && (here.equals(from) || here.equals(corner) || here.equals(waypoint));
            aim = Vec3.atBottomCenterOf(waypoint);
            if (climbing) {
                rise = Math.max(0.0, Math.min(CLIMB_SPEED * speed, waypoint.getY() - ant.getY()));
                if (waypoint.getX() == from.getX() && waypoint.getZ() == from.getZ()) {
                    // Straight up: hug a wall, preferably the one the ant will climb out over.
                    Direction wall = findWall(map, here, waypoint, exitDirection());
                    if (wall != null) {
                        aim = aim.add(wall.getStepX() * 0.7, 0.0, wall.getStepZ() * 0.7);
                        // Get against the wall first, then rise: no floating up the middle of the shaft.
                        if (gapToWall(here, wall) > WALL_GAP) {
                            rise = 0.0;
                        }
                    }
                } else if (here.equals(from) && map.isSolidFace(from.below(), Direction.UP)
                        && gapToWall(here, Direction.fromDelta(waypoint.getX() - from.getX(), 0, waypoint.getZ() - from.getZ())) > WALL_GAP) {
                    // Up and over a ledge from a floor (not from the top of a shaft): first walk up to the foot of
                    // the wall, and only then start climbing. No rising through the air in front of it.
                    climbing = false;
                    rise = 0.0;
                }
            }

            if (at < 0 && !here.equals(corner) && ++offRouteTicks > OFF_ROUTE_TICKS) {
                replan("off the route");
                return;
            }
            if (++stuckTicks >= STUCK_TICKS) {
                boolean moved = ant.position().distanceToSqr(stuckPos) > 0.05 * 0.05;
                if (next == stuckNext && !moved) {
                    replan("stuck");
                    return;
                }
                resetStuckClock();
            }
        }
        if (climbing) {
            double sideX = aim.x - ant.getX();
            double sideZ = aim.z - ant.getZ();
            double distance = Math.sqrt(sideX * sideX + sideZ * sideZ);
            double slide = Math.min(CLING_SIDE_SPEED * speed, distance);
            climbMotion = distance < 1.0E-6 ? new Vec3(0.0, rise, 0.0)
                    : new Vec3(sideX / distance * slide, rise, sideZ / distance * slide);
        }
        ant.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
    }

    /** Holds the ant against a wall of the cell it is in if it was asked to (see {@link #cling()}), otherwise lets go. */
    private void hold() {
        boolean wanted = clingRequested;
        clingRequested = false;
        holding = false;
        climbing = false;
        if (!wanted) {
            return;
        }
        BlockPos here = ant.blockPosition();
        Direction wall = findWall(new LevelCellMap(ant.level()), here, here, null);
        if (wall == null) {
            return;
        }
        holding = true;
        climbing = true;
        // Get against the wall, then stay put.
        climbMotion = gapToWall(here, wall) > WALL_GAP
                ? new Vec3(wall.getStepX() * CLING_SIDE_SPEED, 0.0, wall.getStepZ() * CLING_SIDE_SPEED) : Vec3.ZERO;
    }

    /** How far (in blocks) the ant's body still is from the wall on this side of the cell it is in. */
    private double gapToWall(BlockPos cell, @Nullable Direction wall) {
        if (wall == null) {
            return 0.0;
        }
        double towardWall = (ant.getX() - (cell.getX() + 0.5)) * wall.getStepX()
                + (ant.getZ() - (cell.getZ() + 0.5)) * wall.getStepZ();
        return 0.5 - towardWall - ant.getBbWidth() / 2.0;
    }

    /**
     * If the current stretch of straight-up climbing ends by going over the top of a wall, the direction
     * of that wall from the last climbing cell; otherwise null.
     */
    @Nullable
    private Direction exitDirection() {
        for (int i = next; i < route.size(); i++) {
            BlockPos before = route.get(i - 1);
            BlockPos cell = route.get(i);
            if (cell.getX() != before.getX() || cell.getZ() != before.getZ()) {
                if (cell.getY() > before.getY()) {
                    return Direction.fromDelta(cell.getX() - before.getX(), 0, cell.getZ() - before.getZ());
                }
                return null;
            }
            if (cell.getY() < before.getY()) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private static Direction findWall(CellMap map, BlockPos here, BlockPos waypoint, @Nullable Direction preferred) {
        Direction fallback = null;
        if (preferred != null) {
            if (map.isSolidFace(here.relative(preferred), preferred.getOpposite())) {
                return preferred;
            }
            // The exit wall is not solid here (for example the tunnel opening): hug a wall next to it, so the
            // ant slides along it to the exit wall and never crosses the open shaft.
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (side.getAxis() != preferred.getAxis() && map.isSolidFace(here.relative(side), side.getOpposite())) {
                    return side;
                }
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (map.isSolidFace(here.relative(side), side.getOpposite())) {
                if (map.isSolidFace(waypoint.relative(side), side.getOpposite())) {
                    return side;
                }
                fallback = side;
            }
        }
        return fallback;
    }

    private void replan(String reason) {
        if (++replans > MAX_REPLANS) {
            AntColoniesMod.LOGGER.debug("Ant {} gave up walking to {} ({})", ant.getId(), goal.toShortString(), reason);
            route = null;
            failed = true;
            climbing = false;
            rise = 0.0;
            return;
        }
        AntColoniesMod.LOGGER.debug("Ant {} plans a new route to {} ({})", ant.getId(), goal.toShortString(), reason);
        if (!plan()) {
            AntColoniesMod.LOGGER.debug("Ant {} found no route to {}", ant.getId(), goal.toShortString());
        }
    }
}
