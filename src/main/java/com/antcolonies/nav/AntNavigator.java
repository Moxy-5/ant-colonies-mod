package com.antcolonies.nav;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The legs of an insect: given a goal cell, plans a route over floors and walls with {@link AntPathFinder} and walks it.
 *
 * <p>This is only "how to get there". Deciding "where to go and why" belongs to the goals now and to the colony brain
 * later, so a smarter mind can be put on top without touching the movement. A mind says {@link #moveTo} a cell, or
 * hands over a route it planned itself ({@link #follow}: later a trail or a marching column shared by many insects).
 *
 * <p>Every step of a route says which surface the insect holds and what kind of move it is, so nothing is guessed:
 * walking on a floor uses the normal walking (with gravity), and every move on a wall or round an edge is an exact
 * motion along the surface, the body pressed against it, so the insect never floats. It climbs up head up, down head
 * first, sideways head sideways, turns inside corners and rounds outside edges, and goes over the top of a wall and
 * over the edge of a drop like a real ant. {@link #getSurface} and {@link #getHeading} tell the body how to lie.
 * When a walk ends on a wall (in a shaft) the insect keeps holding on there.
 */
public class AntNavigator {
    /** The insect must be this close (in blocks, sideways) to the middle of a floor goal cell to count as arrived. */
    private static final double ARRIVE_DISTANCE_SQR = 0.3 * 0.3;
    /** If it can't get to the middle of the goal cell within this many ticks, it counts as arrived anyway. */
    private static final int ARRIVE_TIMEOUT = 20;
    /** A step that is not done after this many ticks (6 seconds) means stuck: plan the route again. */
    private static final int STEP_TICKS = 120;
    /** Waiting this long for a falling insect to land is the most. */
    private static final int FALL_TICKS = 200;
    private static final int MAX_REPLANS = 3;
    /** How far (in blocks per tick) an insect moves along a wall, per point of its speed attribute (a worker: 0.25 -> 0.06). */
    private static final double CLIMB_FACTOR = 0.24;
    private static final double MIN_CLIMB_SPEED = 0.012;
    /** An insect this close (in blocks) to a wall of its cell counts as holding on to it. */
    private static final double CLING_GAP = 0.2;
    /** Kept between the body and the faces it holds, so the collision never catches on them. */
    private static final double EPS = 0.002;
    /** A move along a surface that gets nowhere for this many ticks is blocked: plan round it. */
    private static final int BLOCKED_TICKS = 20;
    /** How long (in ticks) a foothold that blocked a move is avoided when planning. */
    private static final int AVOID_TICKS = 400;
    /** Corrections up to this size (in blocks) are made at once, before the body moves on (see moveAttached). */
    private static final double SQUARE_UP = 0.03;
    /** How far past the edge (in blocks) the body goes onto the top of a block when it climbs over. */
    private static final double OVER_THE_TOP = 0.05;

    private final AbstractAntEntity ant;
    @Nullable
    private Route route;
    /** The step being walked (step 0 is the start). */
    private int next;
    /** The part of the current step (some steps are two moves: up the wall, then over the top). */
    private int leg;
    private BlockPos goal = BlockPos.ZERO;
    private double speed = 1.0;
    private boolean failed;
    private int replans;
    private int stepTicks;
    private int arriveTicks;
    /** The foothold the current step starts from; null while the insect drops to where its route starts. */
    @Nullable
    private Foothold at;
    /** The surface the body is on right now: {@link Direction#DOWN} = a floor, a horizontal direction = that wall. */
    private Direction surface = Direction.DOWN;
    /** On a wall: where its head points, in degrees within the wall: 0 up, 180 down, +90 to its left, -90 to its right. */
    private float heading;
    /** True while the motion is set exactly (holding a wall, going round an edge), not by walking. */
    private boolean attached;
    private Vec3 motion = Vec3.ZERO;
    /** For noticing a blocked move: where the insect was when the clock started, and for how long it has not got further. */
    private Vec3 blockedFrom = Vec3.ZERO;
    private int blockedTicks;
    /**
     * Footholds where a move got physically stuck (something the planner can't see), avoided for a while: the game
     * time until which each one is avoided. A small memory of the legs, so a new route goes round the trouble.
     */
    private final Map<Foothold, Long> avoid = new HashMap<>();
    /** The extra costs the mind gave for this walk (see {@link #moveTo(BlockPos, double, AntPathFinder.StepCosts)}). */
    private AntPathFinder.StepCosts mindCosts = AntPathFinder.StepCosts.NONE;

    public AntNavigator(AbstractAntEntity ant) {
        this.ant = ant;
    }

    /**
     * Starts walking to the goal cell. Returns false (and does nothing) if there is no route to it.
     * {@code speed} multiplies the insect's normal speed.
     */
    public boolean moveTo(BlockPos goal, double speed) {
        return moveTo(goal, speed, AntPathFinder.StepCosts.NONE);
    }

    /**
     * Like {@link #moveTo(BlockPos, double)}, with the mind's own costs for this walk (kept when the route is planned
     * again): for example {@link AntPathFinder.StepCosts#forbid} the hole it is about to seal, so it never ends up on
     * the far side of it.
     */
    public boolean moveTo(BlockPos goal, double speed, AntPathFinder.StepCosts mindCosts) {
        this.goal = goal.immutable();
        this.speed = speed;
        this.replans = 0;
        this.mindCosts = mindCosts;
        return plan(null);
    }

    /**
     * Walks a route a mind planned (a trail, a marching column: one route for many insects). The insect first finds
     * its way onto the route's start, then follows it to the end. False if it cannot get onto it.
     */
    public boolean follow(Route shared, double speed) {
        this.goal = shared.end().cell();
        this.speed = speed;
        this.replans = 0;
        this.mindCosts = AntPathFinder.StepCosts.NONE;
        return plan(shared);
    }

    /** True when there is nothing left to walk: arrived, stopped, or no route. See {@link #hasFailed()}. */
    public boolean isDone() {
        return route == null;
    }

    /** True if the last {@link #moveTo} found no route, or the insect got stuck and couldn't recover. */
    public boolean hasFailed() {
        return failed;
    }

    /** True while the body is on a wall (climbing, crawling or holding on). */
    public boolean isClimbing() {
        return surface.getAxis().isHorizontal();
    }

    /** True while the navigator sets the exact motion (see {@link #getClimbMotion}): on a wall, or going round an edge. */
    public boolean isAttached() {
        return attached;
    }

    /** The exact motion (blocks per tick) the insect has this tick while {@link #isAttached}. */
    public Vec3 getClimbMotion() {
        return motion;
    }

    /** The surface the body is on: {@link Direction#DOWN} = a floor, a horizontal direction = the wall on that side. */
    public Direction getSurface() {
        return surface;
    }

    /** On a wall: where the head points within the wall, in degrees (0 up, 180 down, +90 to its left). */
    public float getHeading() {
        return heading;
    }

    /** The foothold the insect holds (or the current step starts from); null while it drops. */
    @Nullable
    public Foothold getFoothold() {
        return at;
    }

    /** The planned route (null when idle). */
    @Nullable
    public Route getPlannedRoute() {
        return route;
    }

    /** The cell of every step of the planned route (null when idle): for tests and debugging. See {@link #getNextWaypoint}. */
    @Nullable
    public List<BlockPos> getRoute() {
        if (route == null) {
            return null;
        }
        List<BlockPos> cells = new ArrayList<>();
        for (Route.Step step : route.steps()) {
            cells.add(step.to().cell());
        }
        return cells;
    }

    /** How many times the current walk had to be planned again (stuck, blocked, the way changed): for tests and debugging. */
    public int getReplans() {
        return replans;
    }

    /** The step being walked: an index into {@link #getRoute}. */
    public int getNextWaypoint() {
        return next;
    }

    /** Stops walking. An insect on a wall keeps holding on where it is. */
    public void stop() {
        if (route != null) {
            Foothold here = currentFoothold(new LevelCellMap(ant.level()));
            if (here != null) {
                at = here;
            }
        }
        route = null;
        failed = false;
        leg = 0;
    }

    /** Plans from where the insect is: to the goal cell, or onto the start of a shared route and then along it. */
    private boolean plan(@Nullable Route shared) {
        CellMap map = new LevelCellMap(ant.level());
        Foothold start = currentFoothold(map);
        List<Foothold> starts = new ArrayList<>();
        if (start != null) {
            starts.add(start);
        } else {
            // In the air: plan from where it will land.
            Foothold landing = AntPathFinder.landing(map, ant.blockPosition());
            if (landing != null) {
                starts.add(landing);
            }
        }
        long now = ant.level().getGameTime();
        avoid.values().removeIf(until -> until <= now);
        AntPathFinder.StepCosts costs = avoid.isEmpty() ? mindCosts
                : mindCosts.and((from, to, kind) -> avoid.containsKey(to) ? 50.0 : 0.0);
        Route found = null;
        if (!starts.isEmpty()) {
            if (shared == null) {
                found = AntPathFinder.find(map, starts, goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs);
            } else {
                Foothold join = shared.start();
                Route onto = AntPathFinder.find(map, starts, join::equals, join.cell(),
                        AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs);
                if (onto != null) {
                    List<Route.Step> steps = new ArrayList<>(onto.steps());
                    steps.addAll(shared.steps().subList(1, shared.size()));
                    found = new Route(steps, onto.cost() + shared.cost());
                }
            }
        }
        failed = found == null;
        route = found;
        next = 1;
        leg = 0;
        stepTicks = 0;
        arriveTicks = 0;
        blockedTicks = 0;
        blockedFrom = ant.position();
        at = start;
        return found != null;
    }

    /** Called every tick from the insect, before it moves. */
    public void tick() {
        CellMap map = new LevelCellMap(ant.level());
        attached = false;
        motion = Vec3.ZERO;
        if (route == null) {
            hold(map);
            return;
        }
        if (at == null) {
            // Dropping to where the route starts.
            surface = Direction.DOWN;
            if (!standsOnFloor(map, ant.blockPosition())) {
                if (++stepTicks > FALL_TICKS) {
                    replan("never landed");
                }
                return;
            }
            Foothold landed = Foothold.floor(ant.blockPosition());
            if (!landed.equals(route.start())) {
                replan("landed somewhere else");
                return;
            }
            at = landed;
            stepTicks = 0;
        }
        if (next >= route.size()) {
            arrive(map);
            return;
        }
        Route.Step step = route.step(next);
        String changed = !step.to().isValid(map) ? "nothing to hold at " + step.to()
                : !at.isValid(map) && step.kind() != StepKind.FALL ? "lost its hold at " + at
                : AntPathFinder.passesThrough(at, step.to(), step.kind()).stream().anyMatch(cell -> !map.isOpen(cell))
                        ? "blocked on the way from " + at + " to " + step.to() : null;
        if (changed != null) {
            replan("the way changed: " + changed + ", " + step.kind().name().toLowerCase()); // a block put in the way (the hill growing, a hole sealed, a player)
            return;
        }
        if (step.kind() != StepKind.FALL && ant.position().distanceToSqr(Vec3.atCenterOf(step.to().cell())) > 9.0) {
            replan("knocked off the route");
            return;
        }
        if (advance(map, at, step)) {
            at = step.to();
            next++;
            leg = 0;
            stepTicks = 0;
        } else if (isBlocked()) {
            // Pushing along a surface and getting nowhere: something is in the way that the planner can't see.
            avoid.put(step.to(), ant.level().getGameTime() + AVOID_TICKS);
            replan("blocked on " + step.kind().name().toLowerCase() + " (part " + leg + ") from " + from(step) + " to " + step.to() + " at "
                    + String.format("%.3f, %.3f, %.3f", ant.getX(), ant.getY(), ant.getZ()));
            return;
        } else if (++stepTicks > (step.kind() == StepKind.FALL ? FALL_TICKS : STEP_TICKS)) {
            replan("stuck on " + step.kind().name().toLowerCase() + " (part " + leg + ") from " + from(step) + " to " + step.to()
                    + " at " + String.format("%.3f, %.3f, %.3f", ant.getX(), ant.getY(), ant.getZ()) + ", motion " + motion);
            return;
        }
        faceTheWayItGoes();
    }

    /** Moves the insect one tick along a step. True once the step is done (this tick's motion completes it). */
    private boolean advance(CellMap map, Foothold from, Route.Step step) {
        Foothold to = step.to();
        BlockPos c = from.cell();
        switch (step.kind()) {
            case START:
                return true;
            case WALK: {
                surface = Direction.DOWN;
                Vec3 aim = Vec3.atBottomCenterOf(to.cell());
                ant.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
                return ant.blockPosition().equals(to.cell());
            }
            case FALL: {
                surface = Direction.DOWN;
                Vec3 aim = Vec3.atBottomCenterOf(to.cell());
                ant.getMoveControl().setWantedPosition(aim.x, ant.getY(), aim.z, speed);
                return ant.onGround() && ant.blockPosition().equals(to.cell());
            }
            case ONTO_WALL: {
                // Walk up to the foot of the wall, then the body tips up onto it.
                surface = Direction.DOWN;
                if (moveAttached(wallPoint(to, c.getY()))) {
                    surface = to.surface();
                    heading = 0.0F;
                    return true;
                }
                return false;
            }
            case OFF_WALL: {
                // Down the wall to the floor, then the body tips back onto it.
                surface = from.surface();
                if (moveAttached(wallPoint(from, c.getY()))) {
                    setYaw(from.surface().toYRot() - heading);
                    surface = Direction.DOWN;
                    return true;
                }
                return false;
            }
            case CLIMB_UP:
            case CLIMB_DOWN:
            case CRAWL_SIDEWAYS: {
                surface = to.surface();
                Vec3 target = wallPoint(to, clampY(to.cell(), ant.getY()));
                if (step.kind() != StepKind.CRAWL_SIDEWAYS) {
                    target = new Vec3(target.x, to.cell().getY(), target.z);
                } else {
                    target = with(target, sideAxis(to.surface()), centerOf(to.cell(), sideAxis(to.surface())));
                }
                return moveAttached(target);
            }
            case TURN_CORNER: {
                // Along this wall into the corner, where the body turns onto the next wall.
                surface = from.surface();
                Vec3 corner = with(wallPoint(from, clampY(c, ant.getY())), to.surface().getAxis(), pinned(c, to.surface()));
                if (moveAttached(corner)) {
                    surface = to.surface();
                    return true;
                }
                return false;
            }
            case ROUND_EDGE: {
                // The wall ends sideways: along it until the body is past the block's edge, then round the edge onto its side face.
                Direction w = from.surface();
                BlockPos n = to.cell().relative(w.getOpposite());
                Direction side = Direction.fromDelta(n.getX() - c.getX(), 0, n.getZ() - c.getZ());
                double past = pinned(n, side.getOpposite());
                if (leg == 0) {
                    surface = w;
                    Vec3 edge = with(wallPoint(from, clampY(c, ant.getY())), side.getAxis(), past);
                    if (moveAttached(edge)) {
                        leg = 1;
                    }
                    return false;
                }
                surface = to.surface();
                Vec3 round = with(with(ant.position(), side.getAxis(), past), w.getAxis(), pinned(to.cell(), w.getOpposite()));
                return moveAttached(round);
            }
            case OVER_TOP: {
                // Up the wall until the feet reach its top, then over the edge onto the block.
                Direction w = from.surface();
                if (leg == 0) {
                    surface = w;
                    if (moveAttached(wallPoint(from, c.getY() + 1 + EPS))) {
                        setYaw(w.toYRot() - heading);
                        leg = 1;
                    }
                    return false;
                }
                surface = Direction.DOWN;
                BlockPos block = c.relative(w);
                double onTop = centerOf(block, w.getAxis()) - w.getAxisDirection().getStep() * (0.5 - half() - OVER_THE_TOP);
                return moveAttached(with(new Vec3(ant.getX(), c.getY() + 1 + EPS, ant.getZ()), w.getAxis(), onTop));
            }
            case OVER_EDGE: {
                // To the edge of the floor and over it, then head first down the side of the block.
                Direction wall = to.surface();
                Direction out = wall.getOpposite();
                if (leg == 0) {
                    surface = Direction.DOWN;
                    double over = pinned(to.cell(), wall);
                    Vec3 edge = with(new Vec3(ant.getX(), c.getY(), ant.getZ()), out.getAxis(), over);
                    edge = with(edge, sideAxis(wall), clampIn(c, sideAxis(wall), coord(ant.position(), sideAxis(wall))));
                    if (moveAttached(edge)) {
                        surface = wall;
                        heading = 180.0F;
                        leg = 1;
                    }
                    return false;
                }
                surface = wall;
                return moveAttached(wallPoint(to, to.cell().getY()));
            }
            default:
                return true;
        }
    }

    private String from(Route.Step step) {
        return at == null ? "the air" : at.toString();
    }

    /** True when the insect has pushed along a surface for {@link #BLOCKED_TICKS} ticks without getting anywhere. */
    private boolean isBlocked() {
        if (!attached || motion.lengthSqr() < 1.0E-4 || ant.position().distanceToSqr(blockedFrom) > 0.01 * 0.01) {
            blockedTicks = 0;
            blockedFrom = ant.position();
            return false;
        }
        return ++blockedTicks >= BLOCKED_TICKS;
    }

    /** At the end of the route: on a floor, walk to the middle of the cell; on a wall, hold on. */
    private void arrive(CellMap map) {
        Foothold end = route.end();
        if (end.isFloor()) {
            surface = Direction.DOWN;
            Vec3 aim = Vec3.atBottomCenterOf(end.cell());
            double dx = ant.getX() - aim.x;
            double dz = ant.getZ() - aim.z;
            if (dx * dx + dz * dz > ARRIVE_DISTANCE_SQR && ++arriveTicks <= ARRIVE_TIMEOUT) {
                ant.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
                return;
            }
        }
        at = end;
        route = null;
        failed = false;
        hold(map);
    }

    /**
     * Nothing to walk. An insect on a wall (in a shaft, say) keeps holding on to it where it is, so it never falls to the
     * bottom and has to climb all the way up again; on a floor it just stands.
     */
    private void hold(CellMap map) {
        if (at != null && at.isWall() && at.isValid(map)
                && ant.position().distanceToSqr(Vec3.atCenterOf(at.cell())) < 1.5 * 1.5) {
            surface = at.surface();
            moveAttached(wallPoint(at, clampY(at.cell(), ant.getY())));
            faceTheWayItGoes();
            return;
        }
        if (at != null && at.isWall()) {
            at = null; // its wall is gone: let go
        }
        surface = Direction.DOWN;
    }

    /**
     * Where the insect really holds on now, worked out from where its body is (a route must start where the body is,
     * or its first move cuts across to the wrong place): the wall it is on in its cell, else its floor, else the wall
     * of its cell it is closest to. Only between two cells (going over an edge) the foothold the step started from.
     * Null in the air.
     */
    @Nullable
    private Foothold currentFoothold(CellMap map) {
        BlockPos cell = ant.blockPosition();
        if (surface.getAxis().isHorizontal() && Foothold.isValid(map, cell, surface) && gapToWall(cell, surface) < CLING_GAP) {
            return new Foothold(cell, surface);
        }
        if (standsOnFloor(map, cell)) {
            if (Foothold.isValid(map, cell, Direction.DOWN)) {
                return Foothold.floor(cell);
            }
            // On the ground, but its middle is over an edge (the rim of a hole): it stands on the floor of a cell next to it.
            Foothold under = floorBeside(map, cell);
            if (under != null) {
                return under;
            }
        }
        Foothold nearest = null;
        double best = CLING_GAP;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            double gap = gapToWall(cell, side);
            if (Foothold.isValid(map, cell, side) && gap < best) {
                nearest = new Foothold(cell, side);
                best = gap;
            }
        }
        if (nearest == null && at != null && at.isValid(map) && ant.position().distanceToSqr(Vec3.atCenterOf(at.cell())) < 1.2 * 1.2) {
            return at;
        }
        return nearest;
    }

    /** The floor of a cell beside {@code cell} that the body overlaps (standing at the edge of it), the nearest one; or null. */
    @Nullable
    private Foothold floorBeside(CellMap map, BlockPos cell) {
        Foothold best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos other = cell.offset(dx, 0, dz);
                if ((dx != 0 || dz != 0) && Foothold.isValid(map, other, Direction.DOWN)
                        && ant.getBoundingBox().intersects(other.getX(), other.getY(), other.getZ(), other.getX() + 1, other.getY() + 1, other.getZ() + 1)) {
                    double distance = ant.position().distanceToSqr(Vec3.atBottomCenterOf(other));
                    if (distance < bestDistance) {
                        best = Foothold.floor(other);
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** Whether the insect is standing on the floor of the cell it is in (also when it just stopped pushing down on it). */
    private boolean standsOnFloor(CellMap map, BlockPos cell) {
        return ant.onGround() || (map.isSolidFace(cell.below(), Direction.UP) && ant.getY() - cell.getY() < 0.05);
    }

    /**
     * Sets the exact motion toward a point at the insect's climbing speed. True if this tick's motion gets there.
     * Tiny sideways corrections (the body a hair over the edge of its cell) are made first, on their own: moving on with
     * the body poking into the next column would catch on the block there and stop it for good.
     */
    private boolean moveAttached(Vec3 target) {
        attached = true;
        Vec3 delta = target.subtract(ant.position());
        double largest = Math.max(Math.abs(delta.x), Math.max(Math.abs(delta.y), Math.abs(delta.z)));
        if (largest > SQUARE_UP) {
            Vec3 squareUp = new Vec3(small(delta.x), small(delta.y), small(delta.z));
            if (squareUp.lengthSqr() > 0.0) {
                motion = squareUp;
                return false;
            }
        }
        double distance = delta.length();
        double step = climbSpeed();
        motion = distance <= step ? delta : delta.scale(step / distance);
        return distance <= step;
    }

    /**
     * A correction worth making first: up to {@link #SQUARE_UP}, but not under a thousandth of a block (that is still
     * inside the cell's margin, and the game drops motions that small anyway, so waiting for it would wait for ever).
     */
    private static double small(double value) {
        return Math.abs(value) <= SQUARE_UP && Math.abs(value) > 1.0E-3 ? value : 0.0;
    }

    private double climbSpeed() {
        return Math.max(MIN_CLIMB_SPEED, ant.getAttributeValue(Attributes.MOVEMENT_SPEED) * CLIMB_FACTOR * speed);
    }

    /** On a wall the body faces the wall and the head turns the way it moves; on a floor it faces the way it moves. */
    private void faceTheWayItGoes() {
        if (surface.getAxis().isHorizontal()) {
            setYaw(surface.toYRot());
            if (attached && motion.lengthSqr() > 1.0E-8) {
                Direction left = surface.getCounterClockWise();
                double sideways = motion.x * left.getStepX() + motion.z * left.getStepZ();
                if (Math.abs(sideways) > 1.0E-5 || Math.abs(motion.y) > 1.0E-5) {
                    heading = (float) Math.toDegrees(Math.atan2(sideways, motion.y));
                }
            }
        } else if (attached && motion.horizontalDistanceSqr() > 1.0E-8) {
            setYaw((float) (Mth.atan2(motion.z, motion.x) * 180.0 / Math.PI) - 90.0F);
        }
    }

    private void setYaw(float yaw) {
        ant.setYRot(yaw);
        ant.yBodyRot = yaw;
        ant.yHeadRot = yaw;
    }

    // --- Geometry of a cell: where the body's middle is when it holds a surface ---

    private double half() {
        return ant.getBbWidth() / 2.0;
    }

    /** The body's middle on the wall's axis when it holds the wall on side {@code wall} of {@code cell}. */
    private double pinned(BlockPos cell, Direction wall) {
        return centerOf(cell, wall.getAxis()) + wall.getAxisDirection().getStep() * (0.5 - half() - EPS);
    }

    /** The point on a wall foothold at height {@code y}: pressed against the wall, sideways where the body is (kept inside the cell). */
    private Vec3 wallPoint(Foothold foothold, double y) {
        Direction wall = foothold.surface();
        Direction.Axis along = sideAxis(wall);
        Vec3 point = with(new Vec3(ant.getX(), y, ant.getZ()), wall.getAxis(), pinned(foothold.cell(), wall));
        return with(point, along, clampIn(foothold.cell(), along, coord(ant.position(), along)));
    }

    /** The horizontal axis along a wall (sideways on it). */
    private static Direction.Axis sideAxis(Direction wall) {
        return wall.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    private double clampIn(BlockPos cell, Direction.Axis axis, double value) {
        double low = cell.get(axis) + half() + EPS;
        return Mth.clamp(value, low, cell.get(axis) + 1 - half() - EPS);
    }

    private double clampY(BlockPos cell, double y) {
        return Mth.clamp(y, cell.getY(), cell.getY() + 1 - ant.getBbHeight() - EPS);
    }

    private static double centerOf(BlockPos cell, Direction.Axis axis) {
        return cell.get(axis) + 0.5;
    }

    private static double coord(Vec3 v, Direction.Axis axis) {
        return axis.choose(v.x, v.y, v.z);
    }

    private static Vec3 with(Vec3 v, Direction.Axis axis, double value) {
        return switch (axis) {
            case X -> new Vec3(value, v.y, v.z);
            case Y -> new Vec3(v.x, value, v.z);
            case Z -> new Vec3(v.x, v.y, value);
        };
    }

    /** How far (in blocks) the body still is from the wall on this side of the cell it is in. */
    private double gapToWall(BlockPos cell, Direction wall) {
        double toward = (ant.getX() - (cell.getX() + 0.5)) * wall.getStepX() + (ant.getZ() - (cell.getZ() + 0.5)) * wall.getStepZ();
        return 0.5 - toward - half();
    }

    private void replan(String reason) {
        if (++replans > MAX_REPLANS) {
            AntColoniesMod.LOGGER.debug("Ant {} gave up walking to {} ({})", ant.getId(), goal.toShortString(), reason);
            Foothold here = currentFoothold(new LevelCellMap(ant.level()));
            route = null;
            failed = true;
            at = here;
            return;
        }
        AntColoniesMod.LOGGER.debug("Ant {} plans a new route to {} ({})", ant.getId(), goal.toShortString(), reason);
        if (!plan(null)) {
            AntColoniesMod.LOGGER.debug("Ant {} found no route to {}", ant.getId(), goal.toShortString());
        }
    }
}
