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
 * (the entity applies {@link #getClimbMotion()}), so climbing never depends on touching a wall.
 * It picks one wall for a whole straight stretch (so it never crosses a shaft in mid-air), and when a walk ends
 * with the ant hanging in a shaft it keeps clinging to the wall instead of falling.
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
    /** The ant keeps moving but reaches no waypoint it has not been at before for this many ticks: it is going in circles, plan again. */
    private static final int CIRCLE_TICKS = 160;
    private static final int MAX_REPLANS = 3;
    /** Blocks per tick an ant rises while climbing. */
    private static final double CLIMB_SPEED = 0.06;
    /** Blocks per tick an ant slides sideways while clinging (toward the wall it hugs or the ledge it climbs over). */
    private static final double CLING_SIDE_SPEED = 0.04;
    /** The ant waits to rise until it is closer than this (in blocks) to the wall it hugs. */
    private static final double WALL_GAP = 0.06;
    /** Blocks per tick an ant that has stopped in a shaft slides to the wall it clings to. */
    private static final double HOLD_SIDE_SPEED = 0.08;
    /** An ant this close (in blocks) to a wall of its cell counts as hanging on it. */
    private static final double CLING_GAP = 0.12;

    private final AbstractAntEntity ant;
    @Nullable
    private List<BlockPos> route;
    /** The next waypoint to reach (route[0] is where the ant started). */
    private int next;
    private BlockPos goal = BlockPos.ZERO;
    private double speed = 1.0;
    private boolean failed;
    private boolean climbing;
    private double rise;
    private Vec3 climbMotion = Vec3.ZERO;
    private int replans;
    private int arriveTicks;
    private int offRouteTicks;
    private int stuckTicks;
    private int stuckNext;
    /** The furthest waypoint the ant has been heading for on this route, and how long it has gone without getting any further. */
    private int bestNext;
    private int circleTicks;
    private Vec3 stuckPos = Vec3.ZERO;
    /** The wall the ant hugs on the straight climb it is on, and the last route index of that climb (null = not chosen yet). */
    @Nullable
    private Direction climbWall;
    private int climbEnd;
    /** The wall an ant that has stopped in a shaft clings to. */
    @Nullable
    private Direction holdWall;
    /** The wall an ant crawling round the corner of its cell is on (set by crawlAim). */
    private Direction crawlWall = Direction.NORTH;

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
        rise = 0.0;
        climbWall = null;
    }

    private boolean plan() {
        List<BlockPos> found = AntPathFinder.find(
                new LevelCellMap(ant.level()), ant.blockPosition(), goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS);
        failed = found == null;
        route = found;
        next = 1;
        bestNext = next;
        climbWall = null;
        circleTicks = 0;
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
        CellMap map = new LevelCellMap(ant.level());
        if (route == null) {
            holdOnToWall(map);
            return;
        }
        BlockPos here = ant.blockPosition();
        int last = route.size() - 1;

        // Where on the route is the ant? Look a few waypoints ahead (it may have skipped some) and
        // anywhere behind (it may have fallen back). The corner cell of the up-and-over stretch it is on is
        // not a waypoint, but the ant passes through it, and it can be the very cell the route started from
        // (planned in mid-air above a ledge): that must not read as "back at the start of the route", or the
        // ant would walk back down and climb the same ledge again for ever.
        BlockPos stretchCorner = next >= 1 && next <= last ? cornerOf(route.get(next - 1), route.get(next)) : null;
        int at = -1;
        for (int i = Math.min(last, next + LOOKAHEAD); i >= 0; i--) {
            if (route.get(i).equals(here) && !(i < next - 1 && here.equals(stretchCorner))) {
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

        // Safety net: a healthy walk gets to a new waypoint every few seconds at most.
        if (next > bestNext) {
            bestNext = next;
            circleTicks = 0;
        } else if (next <= last && ++circleTicks > CIRCLE_TICKS) {
            replan("going in circles");
            return;
        }

        Vec3 aim;
        Vec3 face = null;
        if (next > last) {
            // In the goal cell: finish walking to its middle.
            aim = Vec3.atBottomCenterOf(goal);
            double dx = ant.getX() - aim.x;
            double dz = ant.getZ() - aim.z;
            // A goal cell with no floor (in a shaft) counts as reached as soon as the ant is in it: it clings to the wall
            // there (see holdOnToWall) instead of falling while it tries to get to the middle of the cell.
            boolean noFloor = !map.isSolidFace(goal.below(), Direction.UP);
            if (noFloor || dx * dx + dz * dz <= ARRIVE_DISTANCE_SQR || ++arriveTicks > ARRIVE_TIMEOUT) {
                stop();
                holdOnToWall(map);
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
                rise = Math.max(0.0, Math.min(CLIMB_SPEED, waypoint.getY() - ant.getY()));
                // The wall the ant has to be on for this stretch (to rise along it, or to climb out over it).
                Direction needed = null;
                if (waypoint.getX() == from.getX() && waypoint.getZ() == from.getZ()) {
                    // Straight up: hug one wall for the whole stretch, preferably the one the ant will climb out over.
                    Direction wall = chooseWall(map, here, waypoint);
                    needed = wall;
                    if (wall != null) {
                        aim = aim.add(wall.getStepX() * 0.7, 0.0, wall.getStepZ() * 0.7);
                        if (gapToWall(here, wall) > WALL_GAP && standsOnFloor(map, here)) {
                            // Still on the floor: walk up to the wall like any walk. Only the ant that is against
                            // the wall clings to it (and leans against it), so it never looks like it hovers.
                            // In the air (a shaft) the ant keeps rising while it slides to its wall: it never hangs waiting.
                            rise = 0.0;
                            climbing = false;
                        }
                    }
                } else if (here.equals(from) && map.isSolidFace(from.below(), Direction.UP)
                        && gapToWall(here, Direction.fromDelta(waypoint.getX() - from.getX(), 0, waypoint.getZ() - from.getZ())) > WALL_GAP) {
                    // Up and over a ledge from a floor (not from the top of a shaft): first walk up to the foot of
                    // the wall, and only then start climbing. No rising through the air in front of it.
                    climbing = false;
                    rise = 0.0;
                } else if (here.equals(from)) {
                    needed = Direction.fromDelta(waypoint.getX() - from.getX(), 0, waypoint.getZ() - from.getZ());
                }
                if (climbing && needed != null && !standsOnFloor(map, here)) {
                    // Hanging on another wall of this cell (a new job came while it clung there): crawl over the walls
                    // and round the corner to the one it needs, never letting go and drifting across the open cell.
                    Vec3 crawl = crawlAim(map, here, needed);
                    if (crawl != null) {
                        aim = crawl;
                        face = Vec3.atBottomCenterOf(here).add(crawlWall.getStepX() * 0.7, 0.0, crawlWall.getStepZ() * 0.7);
                        if (!map.isSolidFace(here.above().relative(crawlWall), crawlWall.getOpposite())) {
                            rise = 0.0; // the wall it crawls on ends above: get round the corner first
                        }
                    }
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
        // A crawling ant keeps facing the wall it is on (the renderer leans it against what it faces).
        Vec3 look = face != null ? face : aim;
        ant.getMoveControl().setWantedPosition(look.x, aim.y, look.z, speed);
    }

    /**
     * The point an ant hanging on a wall of its cell crawls to on its way to the {@code needed} wall of the same cell:
     * the corner where the wall it is on meets the needed one, or, from the opposite wall, the corner with a side wall
     * first. Sets {@link #crawlWall} to the wall it is on. Null when it is already on the needed wall, is on no wall
     * at all, or there is no way round (then it slides straight there, as before).
     */
    @Nullable
    private Vec3 crawlAim(CellMap map, BlockPos here, Direction needed) {
        if (gapToWall(here, needed) <= WALL_GAP || !map.isSolidFace(here.relative(needed), needed.getOpposite())) {
            return null;
        }
        // The wall it is on: one it touches, a side wall of the needed one first (from there it can go straight round).
        Direction on = null;
        boolean onSideWall = false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side == needed || gapToWall(here, side) > CLING_GAP || !map.isSolidFace(here.relative(side), side.getOpposite())) {
                continue;
            }
            boolean sideWall = side.getAxis() != needed.getAxis();
            if (on == null || sideWall && !onSideWall) {
                on = side;
                onSideWall = sideWall;
            }
        }
        if (on == null) {
            return null;
        }
        Direction toward = needed;
        if (!onSideWall) {
            // On the opposite wall: go round by the nearer side wall.
            toward = null;
            double nearest = Double.MAX_VALUE;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (side.getAxis() != needed.getAxis() && map.isSolidFace(here.relative(side), side.getOpposite())
                        && gapToWall(here, side) < nearest) {
                    toward = side;
                    nearest = gapToWall(here, side);
                }
            }
            if (toward == null) {
                return null;
            }
        }
        crawlWall = on;
        double inset = 0.5 - ant.getBbWidth() / 2.0;
        return new Vec3(here.getX() + 0.5 + (on.getStepX() + toward.getStepX()) * inset, ant.getY(),
                here.getZ() + 0.5 + (on.getStepZ() + toward.getStepZ()) * inset);
    }

    /** Whether the ant is standing on the floor of the cell it is in (also when it just stopped pushing down on it). */
    private boolean standsOnFloor(CellMap map, BlockPos cell) {
        return ant.onGround() || (map.isSolidFace(cell.below(), Direction.UP) && ant.getY() - cell.getY() < 0.05);
    }

    /**
     * The wall to hug on the straight climb the ant is on. It is chosen once for the whole climb, from the cells the
     * climb still goes through: a wall that is solid beside every one of them, the one the ant will climb out over if
     * there is one, otherwise a side wall. Switching walls from cell to cell would make the ant slide across the shaft in mid-air.
     */
    @Nullable
    private Direction chooseWall(CellMap map, BlockPos here, BlockPos waypoint) {
        if (climbWall != null && next <= climbEnd && map.isSolidFace(here.relative(climbWall), climbWall.getOpposite())) {
            return climbWall;
        }
        int end = next;
        while (end + 1 < route.size()
                && route.get(end + 1).getX() == route.get(end).getX() && route.get(end + 1).getZ() == route.get(end).getZ()
                && route.get(end + 1).getY() > route.get(end).getY()) {
            end++;
        }
        Direction exit = exitDirection();
        Direction best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean solidAlong = true;
            for (int i = next - 1; i <= end && solidAlong; i++) {
                BlockPos cell = route.get(i);
                solidAlong = map.isSolidFace(cell.relative(side), side.getOpposite());
            }
            if (!solidAlong) {
                continue;
            }
            // Out wall first, then a side wall of it (the shortest turn at the top), then the wall the ant is nearest to.
            double score = (side == exit ? 100.0 : exit != null && side.getAxis() != exit.getAxis() ? 10.0 : 0.0)
                    - gapToWall(here, side);
            if (score > bestScore) {
                best = side;
                bestScore = score;
            }
        }
        if (best == null) {
            climbWall = null;
            return findWall(map, here, waypoint, exit);
        }
        climbWall = best;
        climbEnd = end;
        return best;
    }

    /**
     * Nothing left to walk. An ant that is hanging in a shaft (no floor in its cell, a wall beside it) keeps clinging
     * to the wall where it is, so it does not fall to the bottom and have to climb all the way up again
     * (for example while it scrapes up soil, or after it has put a pellet in the hill from inside the shaft).
     */
    private void holdOnToWall(CellMap map) {
        climbing = false;
        climbMotion = Vec3.ZERO;
        BlockPos here = ant.blockPosition();
        if (ant.onGround() || ant.isInLiquid() || map.isSolidFace(here.below(), Direction.UP)) {
            holdWall = null;
            return;
        }
        if (holdWall == null || !map.isSolidFace(here.relative(holdWall), holdWall.getOpposite())) {
            holdWall = null;
            double nearest = Double.MAX_VALUE;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                double gap = gapToWall(here, side);
                if (map.isSolidFace(here.relative(side), side.getOpposite()) && gap < nearest) {
                    holdWall = side;
                    nearest = gap;
                }
            }
        }
        if (holdWall == null) {
            return; // nothing to cling to: it falls
        }
        double slide = Math.max(0.0, Math.min(HOLD_SIDE_SPEED, gapToWall(here, holdWall)));
        climbing = true;
        climbMotion = new Vec3(holdWall.getStepX() * slide, 0.0, holdWall.getStepZ() * slide);
        // Face the wall (the renderer leans the ant against what it faces), without pushing anywhere.
        Vec3 face = Vec3.atBottomCenterOf(here).add(holdWall.getStepX() * 0.7, 0.0, holdWall.getStepZ() * 0.7);
        ant.getMoveControl().setWantedPosition(face.x, ant.getY(), face.z, 0.0);
    }

    /** The corner cell of an up-and-over stretch (the cell above its start), or null if the stretch is not one. */
    @Nullable
    private static BlockPos cornerOf(BlockPos from, BlockPos waypoint) {
        boolean sideways = waypoint.getX() != from.getX() || waypoint.getZ() != from.getZ();
        return sideways && waypoint.getY() > from.getY() ? new BlockPos(from.getX(), waypoint.getY(), from.getZ()) : null;
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
