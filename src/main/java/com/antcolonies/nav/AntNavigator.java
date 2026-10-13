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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;

/**
 * The legs of an insect: given a goal cell, plans a route over floors, walls and ceilings with {@link AntPathFinder} and walks it.
 *
 * <p>This is only "how to get there". Deciding "where to go and why" belongs to the goals now and to the colony brain
 * later, so a smarter mind can be put on top without touching the movement. A mind says {@link #moveTo} a cell, or
 * hands over a route it planned itself ({@link #follow}: later a trail or a marching column shared by many insects).
 *
 * <p>Every step of a route says which surface the insect holds and what kind of move it is, so nothing is guessed:
 * walking on a floor uses the normal walking (with gravity), and every move on a wall or round an edge is an exact
 * motion along the surface, the body pressed against it, so the insect never floats. It climbs up head up, down head
 * first, sideways head sideways, turns inside corners and rounds outside edges, and goes over the top of a wall and
 * over the edge of a drop like a real ant, and hangs upside down from ceilings and the undersides of blocks.
 * {@link #getSurface}, {@link #getHeading} and {@link #getCeilingRef} tell the body how to lie. On a column that
 * narrows ({@link ClimbShape}, a termite spire) the body hugs the real column: every point of a move is worked out as
 * for a full block (the "plan" position) and then drawn in onto the column's narrower sides and edges at that height
 * ({@link #toBody}, {@link #pos}). On a slope ({@link Slope}, a ramp) it walks with the normal walking. In a channel
 * ({@link Channel}, a termite tunnel) the body moves exactly along the channel's middle, its feet on the channel's floor,
 * and in and out of the openings; a chimney mouth's tube is walked with the doorway's own motion ({@link DoorwayMotion}).
 * When a walk ends on a wall (in a shaft), a ceiling or in a channel the insect keeps holding on there, also after the
 * world is saved and loaded again ({@link #getHold}, {@link #restoreHold}).
 */
public class AntNavigator {
    /** The insect must be this close (in blocks, sideways) to the middle of a floor goal cell to count as arrived. */
    private static final double ARRIVE_DISTANCE_SQR = 0.3 * 0.3;
    /** If it can't get to the middle of the goal cell within this many ticks, it counts as arrived anyway. */
    private static final int ARRIVE_TIMEOUT = 20;
    /** A step that is not done after this many ticks (6 seconds) means stuck: plan the route again. */
    private static final int STEP_TICKS = 120;
    /** Coming out of a doorway's mouth or going in by it takes longer (up through the spire, a look round, down the outside). */
    private static final int DOORWAY_STEP_TICKS = 400;
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
    /** Walking up a slope to the wall at its high side: this close (in blocks) to the wall, the body steps onto it. */
    private static final double SLOPE_TO_WALL = 0.06;
    /** A body this close (in blocks, sideways) to the middle line of a channel it is in counts as in the channel. */
    private static final double IN_THE_CHANNEL = 0.1;
    /** Out of a hole in a floor, the body steps this far (in blocks) off the hole's middle, so it stands on the floor round it. */
    private static final double OFF_THE_HOLE = 0.25;

    private final AbstractAntEntity ant;
    @Nullable
    private Route route;
    /** The footholds the step being walked goes from and to (or the hold, when idle): which column the body hugs. */
    @Nullable
    private Foothold stepFrom;
    @Nullable
    private Foothold stepTo;
    /** While a doorway's exact motion or a channel is walked: its points are where the body goes, nothing is drawn in. */
    private boolean exact;
    /** Whether the body is inside a channel now (drawn in the channel's middle, not pressed against a wall). */
    private boolean inChannel;
    /**
     * In a channel: the way the body last crawled sideways. Up a channel it holds the channel's wall on that side, head
     * up; down a channel the wall on the other side, head first, as if it had gone over the edge of the floor.
     */
    private Direction channelFacing = Direction.NORTH;
    /** The step being walked (step 0 is the start). */
    private int next;
    /** The part of the current step (some steps are two moves: up the wall, then over the top). */
    private int leg;
    private BlockPos goal = BlockPos.ZERO;
    /** When the mind asked for one exact foothold (a spot on a ceiling, say) instead of a cell; else null. */
    @Nullable
    private Foothold goalFoothold;
    /** When the mind accepts any foothold that passes a test (anywhere within reach of a block, say); else null. */
    @Nullable
    private java.util.function.Predicate<Foothold> goalTest;
    private double speed = 1.0;
    private boolean failed;
    private int replans;
    private int stepTicks;
    /** Ticks spent staying at a point of a doorway's motion (see {@link DoorwayMotion}). */
    private int legTicks;
    /** Whether it has reached the point of the doorway's motion it walks to ({@link #leg}) and stays there now. */
    private boolean holding;
    /** Whether it holds still looking round, with its head out of a doorway's mouth (the body plays its look round). */
    private boolean peeking;
    /** Whether the body tips over to its new surface slowly (going over the rim of a doorway's mouth). */
    private boolean slowTurn;
    private int arriveTicks;
    /** The foothold the current step starts from; null while the insect drops to where its route starts. */
    @Nullable
    private Foothold at;
    /** The surface the body is on right now: {@link Direction#DOWN} = a floor, a horizontal direction = that wall. */
    private Direction surface = Direction.DOWN;
    /**
     * On a wall: where its head points, in degrees within the wall: 0 up, 180 down, +90 to its left, -90 to its right.
     * On a ceiling: the same, measured from {@link #ceilingRef} as if the wall went on over the ceiling: 0 = away from
     * that wall, 180 = toward it, +90 = to the left as seen on that wall.
     */
    private float heading;
    /** On a ceiling: the wall its heading is measured from (the wall it came up, so the body turns on without a jump). */
    private Direction ceilingRef = Direction.NORTH;
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
    /**
     * The insect's habits for this walk ({@link AbstractAntEntity#walkHabits}, asked once per walk): moves it would
     * rather not make. Unlike the mind's costs they give way: when no route keeps to them, it is planned without them.
     */
    private AntPathFinder.StepCosts habits = AntPathFinder.StepCosts.NONE;

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
        this.goalFoothold = null;
        this.goalTest = null;
        this.speed = speed;
        this.replans = 0;
        this.habits = ant.walkHabits();
        this.mindCosts = mindCosts;
        return plan(null);
    }

    /**
     * Starts walking to one exact foothold, for example a spot on a ceiling to hang from, or one wall of a shaft.
     * False (and does nothing) if there is no route to it.
     */
    public boolean moveTo(Foothold goal, double speed) {
        this.goal = goal.cell();
        this.goalFoothold = goal;
        this.goalTest = null;
        this.speed = speed;
        this.replans = 0;
        this.habits = ant.walkHabits();
        this.mindCosts = AntPathFinder.StepCosts.NONE;
        return plan(null);
    }

    /**
     * Starts walking to the nearest foothold the mind accepts ({@code isGoal}, for example any spot from which a block
     * can be reached), searching toward {@code towards} first. False (and does nothing) if there is none to walk to.
     */
    public boolean moveTo(java.util.function.Predicate<Foothold> isGoal, BlockPos towards, double speed) {
        this.goal = towards.immutable();
        this.goalFoothold = null;
        this.goalTest = isGoal;
        this.speed = speed;
        this.replans = 0;
        this.habits = ant.walkHabits();
        this.mindCosts = AntPathFinder.StepCosts.NONE;
        return plan(null);
    }

    /**
     * Walks a route a mind planned (a trail, a marching column: one route for many insects). The insect first finds
     * its way onto the route's start, then follows it to the end. False if it cannot get onto it.
     */
    public boolean follow(Route shared, double speed) {
        this.goal = shared.end().cell();
        this.goalFoothold = null;
        this.goalTest = null;
        this.speed = speed;
        this.replans = 0;
        this.habits = ant.walkHabits();
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

    /** True while the body is on a wall or a ceiling (climbing, crawling, hanging or holding on), not on a floor. */
    public boolean isClimbing() {
        return surface != Direction.DOWN;
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

    /** On a wall: where the head points within the wall, in degrees (0 up, 180 down, +90 to its left). On a ceiling see {@link #getCeilingRef}. */
    public float getHeading() {
        return heading;
    }

    /** On a ceiling: the wall the heading is measured from (0 = head away from it, 180 = toward it). */
    public Direction getCeilingRef() {
        return ceilingRef;
    }

    /** Whether the insect holds still with its head out of a doorway's mouth, looking round (the body plays its "peek"). */
    public boolean isPeeking() {
        return peeking;
    }

    /**
     * Whether it is walking an exact motion now that is not pressed against a surface the usual way: up or down the tube
     * of a chimney mouth (see {@link DoorwayMotion}), or in a channel.
     */
    public boolean isWalkingADoorway() {
        return exact;
    }

    /** Whether the body is inside a channel now (a termite tunnel; the body is drawn in the channel's middle). */
    public boolean isInChannel() {
        return inChannel;
    }

    /** Whether the body should tip over to its new surface slowly (going over the rim of a doorway's mouth). */
    public boolean isTurningSlowly() {
        return slowTurn;
    }

    /**
     * The wall or ceiling the insect holds on to right now, to be saved with it; null on a floor or in the air (then
     * there is nothing to keep: it just stands, or falls).
     */
    @Nullable
    public Foothold getHold() {
        Foothold here = route == null ? at : currentFoothold(new LevelCellMap(ant.level(), ant));
        return here == null || here.isFloor() ? null : here;
    }

    /**
     * After loading: the insect was saved holding this wall or ceiling, so it holds on to it again from its first tick
     * instead of dropping and climbing all the way back up. (If the block is gone it lets go as usual.)
     */
    public void restoreHold(Foothold hold) {
        if (hold.isFloor()) {
            return;
        }
        at = hold;
        surface = hold.surface();
        inChannel = hold.isChannel();
        heading = 0.0F;
        route = null;
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
            Foothold here = currentFoothold(new LevelCellMap(ant.level(), ant));
            if (here != null) {
                at = here;
            }
        }
        route = null;
        failed = false;
        startLeg();
    }

    /** A step starts from its first part (and a doorway's motion from its first point, not looking round). */
    private void startLeg() {
        leg = 0;
        legTicks = 0;
        holding = false;
        peeking = false;
        slowTurn = false;
    }

    /** Plans from where the insect is: to the goal cell, or onto the start of a shared route and then along it. */
    private boolean plan(@Nullable Route shared) {
        CellMap map = new LevelCellMap(ant.level(), ant);
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
            // Keeping to its habits if it can; if no route does, without them.
            found = habits == AntPathFinder.StepCosts.NONE ? null : search(map, starts, shared, costs.and(habits));
            if (found == null) {
                found = search(map, starts, shared, costs);
            }
        }
        failed = found == null;
        route = found;
        next = 1;
        startLeg();
        stepTicks = 0;
        arriveTicks = 0;
        blockedTicks = 0;
        blockedFrom = ant.position();
        at = start;
        return found != null;
    }

    /** One search for a route from the starts: to the goal, or onto the start of a shared route and along it. Null if none. */
    @Nullable
    private Route search(CellMap map, List<Foothold> starts, @Nullable Route shared, AntPathFinder.StepCosts costs) {
        if (shared == null) {
            return goalTest != null
                    ? AntPathFinder.find(map, starts, goalTest, goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs)
                    : goalFoothold == null
                    ? AntPathFinder.find(map, starts, goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs)
                    : AntPathFinder.find(map, starts, goalFoothold::equals, goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs);
        }
        Foothold join = shared.start();
        Route onto = AntPathFinder.find(map, starts, join::equals, join.cell(), AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs);
        if (onto == null) {
            return null;
        }
        List<Route.Step> steps = new ArrayList<>(onto.steps());
        steps.addAll(shared.steps().subList(1, shared.size()));
        return new Route(steps, onto.cost() + shared.cost());
    }

    /** Called every tick from the insect, before it moves. */
    public void tick() {
        CellMap map = new LevelCellMap(ant.level(), ant);
        attached = false;
        exact = false;
        inChannel = false;
        motion = Vec3.ZERO;
        if (route == null) {
            stepFrom = at;
            stepTo = null;
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
            Foothold landed = floorUnder(map);
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
        stepFrom = at;
        stepTo = step.to();
        String changed = AntPathFinder.whatChanged(map, at, step.to(), step.kind());
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
            startLeg();
            stepTicks = 0;
        } else if (isBlocked()) {
            // Pushing along a surface and getting nowhere: something is in the way that the planner can't see.
            avoid.put(step.to(), ant.level().getGameTime() + AVOID_TICKS);
            replan("blocked on " + step.kind().name().toLowerCase() + " (part " + leg + ") from " + from(step) + " to " + step.to() + " at "
                    + String.format("%.3f, %.3f, %.3f", ant.getX(), ant.getY(), ant.getZ()));
            return;
        } else if (++stepTicks > (step.kind() == StepKind.FALL ? FALL_TICKS : isDoorwayStep(step) ? DOORWAY_STEP_TICKS : STEP_TICKS)) {
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
                return inFloorCell(map, to.cell());
            }
            case FALL: {
                surface = Direction.DOWN;
                Vec3 aim = Vec3.atBottomCenterOf(to.cell());
                ant.getMoveControl().setWantedPosition(aim.x, ant.getY(), aim.z, speed);
                return ant.onGround() && inFloorCell(map, to.cell());
            }
            case ONTO_WALL: {
                // Walk up to the foot of the wall, then the body tips up onto it. From a slope (the wall goes on up from
                // its high side) it first walks up the slope to the wall with the normal walking.
                surface = Direction.DOWN;
                if (leg == 0 && map.isSlope(c)) {
                    Vec3 foot = wallPoint(to, to.cell().getY());
                    ant.getMoveControl().setWantedPosition(foot.x, foot.y, foot.z, speed);
                    if (gapToWall(to.cell(), to.surface()) < SLOPE_TO_WALL) {
                        leg = 1;
                    }
                    return false;
                }
                if (moveAttached(wallPoint(to, to.cell().getY()))) {
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
                Vec3 round = with(with(pos(), side.getAxis(), past), w.getAxis(), pinned(to.cell(), w.getOpposite()));
                return moveAttached(round);
            }
            case OVER_TOP: {
                Doorway door = doorwayAt(c);
                if (door != null) {
                    exact = true;
                    return comeOutOf(door, from, step);
                }
                // Up the wall until the feet reach its top (above a slope on it as high as its surface rises under where
                // the body will be), then over the edge onto the block.
                Direction w = from.surface();
                double lip = lipWithin(map, to.cell(), w.getOpposite(), 2 * half() + OVER_THE_TOP);
                if (leg == 0) {
                    surface = w;
                    if (moveAttached(wallPoint(from, c.getY() + 1 + lip + EPS))) {
                        setYaw(w.toYRot() - heading);
                        leg = 1;
                    }
                    return false;
                }
                surface = Direction.DOWN;
                BlockPos block = c.relative(w);
                double onTop = centerOf(block, w.getAxis()) - w.getAxisDirection().getStep() * (0.5 - half() - OVER_THE_TOP);
                return moveAttached(with(new Vec3(ant.getX(), c.getY() + 1 + lip + EPS, ant.getZ()), w.getAxis(), onTop));
            }
            case OVER_EDGE: {
                Doorway door = doorwayAt(to.cell());
                if (door != null) {
                    exact = true;
                    return goInto(door, from, step);
                }
                // To the edge of the floor and over it, then head first down the side of the block.
                Direction wall = to.surface();
                Direction out = wall.getOpposite();
                if (leg == 0) {
                    surface = Direction.DOWN;
                    double over = pinned(to.cell(), wall);
                    Vec3 edge = with(new Vec3(ant.getX(), c.getY() + lip(map, c, out), ant.getZ()), out.getAxis(), over);
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
            case ONTO_CEILING: {
                // Up the wall into the corner, where the body tips on over onto the ceiling.
                surface = from.surface();
                if (moveAttached(wallPoint(from, ceilingY(c)))) {
                    surface = Direction.UP;
                    ceilingRef = from.surface();
                    heading = 0.0F;
                    return true;
                }
                return false;
            }
            case OFF_CEILING: {
                // Along the ceiling to the wall, then the body tips down onto it, head first.
                surface = Direction.UP;
                if (moveAttached(with(ceilingPoint(from), to.surface().getAxis(), pinned(c, to.surface())))) {
                    surface = to.surface();
                    heading = 180.0F;
                    return true;
                }
                return false;
            }
            case CRAWL_CEILING: {
                surface = Direction.UP;
                Direction.Axis along = to.cell().getX() != c.getX() ? Direction.Axis.X : Direction.Axis.Z;
                return moveAttached(with(ceilingPoint(to), along, centerOf(to.cell(), along)));
            }
            case UNDER_EDGE: {
                // Down the wall until the body is below the block's bottom edge, then in under the block, upside down.
                Direction w = from.surface();
                if (leg == 0) {
                    surface = w;
                    if (moveAttached(wallPoint(from, c.getY() - ant.getBbHeight() - EPS))) {
                        surface = Direction.UP;
                        ceilingRef = w;
                        heading = 180.0F;
                        leg = 1;
                    }
                    return false;
                }
                surface = Direction.UP;
                return moveAttached(with(ceilingPoint(to), w.getAxis(), pinned(to.cell(), w.getOpposite())));
            }
            case OUT_FROM_UNDER: {
                // Along the ceiling until the body is past its edge, then round the edge and up the block's side face.
                Direction out = to.surface().getOpposite();
                BlockPos beside = to.cell().below();
                if (leg == 0) {
                    surface = Direction.UP;
                    if (moveAttached(with(ceilingPoint(from), out.getAxis(), pinned(beside, to.surface())))) {
                        surface = to.surface();
                        heading = 0.0F;
                        leg = 1;
                    }
                    return false;
                }
                surface = to.surface();
                return moveAttached(wallPoint(to, to.cell().getY()));
            }
            case CRAWL_CHANNEL:
                return crawlChannel(c, to.cell());
            case INTO_CHANNEL:
                return intoChannel(from, to.cell());
            case OUT_OF_CHANNEL:
                return outOfChannel(c, to);
            case OUT_OF_MOUTH:
                return outOfMouth(c, to);
            case INTO_MOUTH:
                return intoMouth(from, to.cell());
            default:
                return true;
        }
    }

    // --- Channels (termite tunnels, see Channel): the body lies in the channel's middle, its feet on its floor ---

    /** Where the body is in the channel of a cell: in the middle, its feet on the channel's floor. */
    private Vec3 channelPoint(BlockPos cell) {
        return new Vec3(cell.getX() + 0.5, cell.getY() + Channel.FLOOR + EPS, cell.getZ() + 0.5);
    }

    /** Just outside the opening of the channel in {@code cell} on the horizontal side {@code d}, feet at {@code y}: the body touches the face. */
    private Vec3 outsideOpening(BlockPos cell, Direction d, double y) {
        double out = 0.5 + half() + EPS;
        return new Vec3(cell.getX() + 0.5 + d.getStepX() * out, y, cell.getZ() + 0.5 + d.getStepZ() * out);
    }

    /** The side of {@code from} that faces {@code to} (cells side by side). */
    private static Direction toward(BlockPos from, BlockPos to) {
        return Direction.getNearest(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    /** Along the channel into the next block's: sideways lying on the channel's floor, up or down holding its wall. */
    private boolean crawlChannel(BlockPos c, BlockPos next) {
        exact = true;
        inChannel = true;
        Direction d = toward(c, next);
        if (d.getAxis().isHorizontal()) {
            if (surface != Direction.DOWN) {
                setYaw(d.toYRot());
            }
            surface = Direction.DOWN;
            channelFacing = d;
        } else {
            surface = d == Direction.UP ? channelFacing : channelFacing.getOpposite();
        }
        return moveAttached(channelPoint(next));
    }

    /**
     * Into a channel by an opening: from the floor in front of a hole in a block's side (to the foot of the wall under
     * it, up the little step and in), from the wall with the hole (along it to the hole's lower lip, then in), from the
     * floor over a hole (to its middle, then head first down into it) or from the ceiling under a hole (to its middle,
     * then up into it).
     */
    private boolean intoChannel(Foothold from, BlockPos ch) {
        exact = true;
        BlockPos c = from.cell();
        Direction d = toward(ch, c); // the channel's side it comes in by
        if (from.isCeiling()) {
            if (leg == 0) {
                surface = Direction.UP;
                if (moveAttached(new Vec3(ch.getX() + 0.5, ceilingY(c), ch.getZ() + 0.5))) {
                    leg = 1;
                }
                return false;
            }
            inChannel = true;
            surface = channelFacing;
            return moveAttached(channelPoint(ch));
        }
        if (d == Direction.UP) {
            if (leg == 0) {
                surface = Direction.DOWN;
                if (moveAttached(new Vec3(ch.getX() + 0.5, c.getY() + EPS, ch.getZ() + 0.5))) {
                    leg = 1;
                }
                return false;
            }
            inChannel = true;
            surface = channelFacing.getOpposite();
            return moveAttached(channelPoint(ch));
        }
        double lip = ch.getY() + Channel.FLOOR + EPS;
        if (leg == 0) {
            if (from.isWall()) {
                surface = from.surface();
                if (moveAttached(outsideOpening(ch, d, lip))) {
                    leg = 2;
                }
                return false;
            }
            surface = Direction.DOWN;
            if (moveAttached(outsideOpening(ch, d, c.getY() + EPS))) {
                leg = 1;
            }
            return false;
        }
        if (leg == 1) {
            surface = Direction.DOWN;
            if (moveAttached(outsideOpening(ch, d, lip))) {
                leg = 2;
            }
            return false;
        }
        if (surface != Direction.DOWN) {
            setYaw(d.getOpposite().toYRot()); // from the wall it tips forward into the hole
        }
        inChannel = true;
        surface = Direction.DOWN;
        channelFacing = d.getOpposite();
        return moveAttached(channelPoint(ch));
    }

    /**
     * Out of a channel by an opening: out of a hole in the block's side onto the floor in front (a little drop) or head
     * first down the wall it is in, up out of a hole on top onto the floor round it, or down out of a hole underneath
     * onto the ceiling round it.
     */
    private boolean outOfChannel(BlockPos c, Foothold to) {
        exact = true;
        Direction d = toward(c, to.cell());
        if (d == Direction.UP) {
            if (leg == 0) {
                inChannel = true;
                surface = channelFacing;
                if (moveAttached(new Vec3(c.getX() + 0.5, c.getY() + 1 + EPS, c.getZ() + 0.5))) {
                    leg = 1;
                }
                return false;
            }
            if (surface != Direction.DOWN) {
                setYaw(channelFacing.toYRot());
            }
            surface = Direction.DOWN;
            return moveAttached(new Vec3(c.getX() + 0.5 + channelFacing.getStepX() * OFF_THE_HOLE, c.getY() + 1 + EPS,
                    c.getZ() + 0.5 + channelFacing.getStepZ() * OFF_THE_HOLE));
        }
        if (d == Direction.DOWN) {
            if (leg == 0) {
                inChannel = true;
                surface = channelFacing.getOpposite();
                if (moveAttached(new Vec3(c.getX() + 0.5, ceilingY(to.cell()), c.getZ() + 0.5))) {
                    leg = 1;
                }
                return false;
            }
            surface = Direction.UP;
            ceilingRef = channelFacing.getAxis().isHorizontal() ? channelFacing : Direction.NORTH;
            heading = 0.0F;
            return true;
        }
        if (leg == 0) {
            inChannel = true;
            if (surface != Direction.DOWN) {
                setYaw(d.toYRot());
            }
            surface = Direction.DOWN;
            channelFacing = d;
            if (moveAttached(outsideOpening(c, d, c.getY() + Channel.FLOOR + EPS))) {
                leg = 1;
            }
            return false;
        }
        if (to.isWall()) {
            // Over the hole's lower lip and head first down the wall it is in.
            surface = to.surface();
            heading = 180.0F;
            attached = true;
            motion = Vec3.ZERO;
            return true;
        }
        surface = Direction.DOWN;
        return moveAttached(new Vec3(ant.getX(), to.cell().getY() + EPS, ant.getZ()));
    }

    /**
     * Out of a chimney mouth by its tube (see {@link DoorwayMotion#comeOut}): from the channel's middle up the tube until
     * the head is out, a look round, over the rim and head first down the outside of the tube, then onto the top of the
     * block beside, or on down the outside of the mouth's own column (a mouth alone on a spire) to the wall foothold.
     */
    private boolean outOfMouth(BlockPos mouth, Foothold to) {
        exact = true;
        if (!(ant.level().getBlockState(mouth).getBlock() instanceof Doorway door)) {
            return true; // no mouth any more: the next tick's check plans again
        }
        BlockPos beside = to.isFloor() ? to.cell().below() : to.cell();
        Direction e = toward(mouth, beside);
        List<DoorwayMotion.Key> keys = DoorwayMotion.comeOut(door.mouthHeight(), door.mouthRadius(), half(), OVER_THE_TOP);
        if (to.isFloor()) {
            return walkDoorway(keys, mouth, e);
        }
        List<DoorwayMotion.Key> down = keys.subList(0, keys.size() - 1);
        if (leg < down.size()) {
            walkDoorway(down, mouth, e);
            return false;
        }
        // On down the outside of the mouth's own column, hugging it.
        exact = false;
        slowTurn = false;
        surface = to.surface();
        return moveAttached(wallPoint(to, clampY(to.cell(), to.cell().getY() + 1 - ant.getBbHeight() - EPS)));
    }

    /**
     * Into a chimney mouth by its tube (see {@link DoorwayMotion#goIn}): from the top of the block beside to the foot of
     * the tube (or up the outside of the mouth's own column to it), up the tube's outside, over the rim and head first
     * down inside, then to the channel's middle.
     */
    private boolean intoMouth(Foothold from, BlockPos mouth) {
        exact = true;
        if (!(ant.level().getBlockState(mouth).getBlock() instanceof Doorway door)) {
            return true; // no mouth any more: the next tick's check plans again
        }
        BlockPos beside = from.isFloor() ? from.cell().below() : from.cell();
        Direction e = toward(mouth, beside);
        List<DoorwayMotion.Key> keys = new ArrayList<>(DoorwayMotion.goIn(door.mouthHeight(), door.mouthRadius(), half(), OVER_THE_TOP));
        if (from.isWall()) {
            DoorwayMotion.Key first = keys.get(0);
            keys.set(0, new DoorwayMotion.Key(first.out(), first.y(), DoorwayMotion.On.OUTSIDE, first.speed(), first.hold(), first.peek(), first.slowTurn()));
        }
        if (leg < keys.size()) {
            walkDoorway(keys, mouth, e);
            return false;
        }
        inChannel = true;
        slowTurn = false;
        surface = e;
        channelFacing = e.getOpposite();
        return moveAttached(channelPoint(mouth));
    }

    /** The doorway block in this cell if this insect passes through it (a termite in a chimney block), else null. */
    @Nullable
    private Doorway doorwayAt(BlockPos cell) {
        BlockState state = ant.level().getBlockState(cell);
        return state.getBlock() instanceof Doorway door
                && state.getCollisionShape(ant.level(), cell, CollisionContext.of(ant)).isEmpty() ? door : null;
    }

    /** Whether this step comes out of a doorway's mouth or goes in by it (it takes longer than other steps). */
    private boolean isDoorwayStep(Route.Step step) {
        return step.kind() == StepKind.OUT_OF_MOUTH || step.kind() == StepKind.INTO_MOUTH
                || (step.kind() == StepKind.OVER_TOP && at != null && doorwayAt(at.cell()) != null)
                || (step.kind() == StepKind.OVER_EDGE && doorwayAt(step.to().cell()) != null);
    }

    /**
     * Out of a doorway over its top, by its mouth (see {@link DoorwayMotion#comeOut}): from the wall inside the block up
     * the middle of the spire until the head is out, a look round, over the rim and head first down the outside of the
     * spire, and onto the top of the block beside (where the route goes on). The body is hidden inside the block until
     * it comes up the spire.
     */
    private boolean comeOutOf(Doorway door, Foothold from, Route.Step step) {
        return walkDoorway(DoorwayMotion.comeOut(door.mouthHeight(), door.mouthRadius(), half(), OVER_THE_TOP), from.cell(), from.surface());
    }

    /**
     * Into a doorway over its edge, by its mouth (see {@link DoorwayMotion#goIn}): from the top of the block beside to
     * the foot of the spire, up its outside, over the rim and head first down inside it into the block, then over to the
     * wall the route goes on from (hidden inside).
     */
    private boolean goInto(Doorway door, Foothold from, Route.Step step) {
        Foothold to = step.to();
        BlockPos c = to.cell();
        Direction s = to.surface(); // toward the block the insect comes from: the side it goes in by
        List<DoorwayMotion.Key> keys = DoorwayMotion.goIn(door.mouthHeight(), door.mouthRadius(), half(), OVER_THE_TOP);
        if (leg < keys.size()) {
            walkDoorway(keys, c, s);
            return false;
        }
        // Inside, over to the wall the route goes on from.
        surface = s;
        slowTurn = false;
        return moveAttached(wallPoint(to, clampY(c, c.getY() + 0.5)));
    }

    /**
     * Walks a doorway's motion one point after the other ({@link #leg} is the point it walks to), on the surface each
     * point says, at its speed, staying at it as long as it says (looking round if it says so). {@code c}: the doorway
     * block; {@code side}: the side it leaves by (or comes in by). True once the last point is reached.
     */
    private boolean walkDoorway(List<DoorwayMotion.Key> keys, BlockPos c, Direction side) {
        DoorwayMotion.Key key = keys.get(leg);
        Direction on = switch (key.on()) {
            case INSIDE -> side;
            case OUTSIDE -> side.getOpposite();
            case TOP -> Direction.DOWN;
        };
        if (on == Direction.DOWN && surface.getAxis().isHorizontal()) {
            setYaw(surface.toYRot() - heading); // from a wall onto a top: the body faces the way its head pointed
        }
        surface = on;
        slowTurn = key.slowTurn();
        if (!holding) {
            Vec3 point = new Vec3(c.getX() + 0.5 + side.getStepX() * key.out(), c.getY() + key.y(), c.getZ() + 0.5 + side.getStepZ() * key.out());
            double walking = speed;
            speed = walking * key.speed();
            boolean there = moveAttached(point);
            speed = walking;
            if (!there) {
                return false;
            }
            if (key.hold() == 0) {
                leg++; // on to the next point from the next tick
                return leg >= keys.size();
            }
            holding = true;
            legTicks = 0;
            return false;
        }
        // At the point: hold still for a while (looking round, if it says so: the whole look round, every tick of it).
        attached = true;
        motion = Vec3.ZERO;
        if (legTicks++ < key.hold()) {
            peeking = key.peek();
            return false;
        }
        peeking = false;
        holding = false;
        leg++;
        return leg >= keys.size();
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
     * Nothing to walk. An insect on a wall (in a shaft, say) or a ceiling keeps holding on to it where it is, so it never
     * falls to the bottom and has to climb all the way up again; on a floor it just stands.
     */
    private void hold(CellMap map) {
        if (at != null && at.isChannel() && at.isValid(map) && ant.position().distanceToSqr(Vec3.atCenterOf(at.cell())) < 1.5 * 1.5) {
            // In a channel: lie still in its middle.
            exact = true;
            inChannel = true;
            moveAttached(channelPoint(at.cell()));
            faceTheWayItGoes();
            return;
        }
        if (at != null && !at.isFloor() && !at.isChannel() && at.isValid(map)
                && ant.position().distanceToSqr(Vec3.atCenterOf(at.cell())) < 1.5 * 1.5) {
            surface = at.surface();
            moveAttached(at.isCeiling() ? ceilingPoint(at) : wallPoint(at, clampY(at.cell(), ant.getY())));
            faceTheWayItGoes();
            return;
        }
        if (at != null && !at.isFloor()) {
            at = null; // its wall or ceiling is gone: let go
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
        BlockPos feet = ant.blockPosition();
        if (map.channelSides(feet) != 0 && Math.abs(ant.getX() - (feet.getX() + 0.5)) < IN_THE_CHANNEL
                && Math.abs(ant.getZ() - (feet.getZ() + 0.5)) < IN_THE_CHANNEL) {
            // In a channel (a termite tunnel): it lies in the channel's middle. (A body hugging the outside of a narrow
            // hollow spire piece can have its feet in the piece's block, but never on the channel's middle line.)
            return Foothold.channel(feet);
        }
        BlockPos cell = BlockPos.containing(pos());
        if (surface == Direction.UP && Foothold.isValid(map, cell, Direction.UP)
                && cell.getY() + 1 - (ant.getY() + ant.getBbHeight()) < CLING_GAP) {
            return Foothold.ceiling(cell);
        }
        if (surface.getAxis().isHorizontal() && Foothold.isValid(map, cell, surface) && gapToWall(cell, surface) < CLING_GAP) {
            return new Foothold(cell, surface);
        }
        if (standsOnFloor(map, cell)) {
            if (Foothold.isValid(map, cell, Direction.DOWN)) {
                return Foothold.floor(cell);
            }
            if (map.isSlope(cell.below()) && Foothold.isValid(map, cell.below(), Direction.DOWN)) {
                return Foothold.floor(cell.below()); // on the top edge of a slope
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
        if (nearest == null && at != null && at.isValid(map) && pos().distanceToSqr(Vec3.atCenterOf(at.cell())) < 1.2 * 1.2) {
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
        return ant.onGround() || ((map.isSolidFace(cell.below(), Direction.UP) || map.isSlope(cell.below())) && ant.getY() - cell.getY() < 0.05);
    }

    /** The floor the insect stands on: of the cell it is in, or of the slope under it (standing on its top edge). */
    private Foothold floorUnder(CellMap map) {
        BlockPos cell = ant.blockPosition();
        return !Foothold.isValid(map, cell, Direction.DOWN) && map.isSlope(cell.below()) ? Foothold.floor(cell.below()) : Foothold.floor(cell);
    }

    /** Whether the insect stands in this floor cell (on a slope also on its top edge, in the cell above it). */
    private boolean inFloorCell(CellMap map, BlockPos cell) {
        BlockPos here = ant.blockPosition();
        return here.equals(cell) || (map.isSlope(cell) && here.equals(cell.above()) && ant.getY() - here.getY() < 0.05);
    }

    /**
     * How high (in blocks) the surface of a slope in {@code cell} rises within {@code depth} blocks in from its edge on
     * {@code side} (where a body coming over that edge will stand); 0 for a plain floor.
     */
    private static double lipWithin(CellMap map, BlockPos cell, Direction side, double depth) {
        if (!map.isSlope(cell)) {
            return 0.0;
        }
        int last = Slope.COLUMNS - 1;
        int rows = Math.min(Slope.COLUMNS, (int) Math.ceil(depth * Slope.COLUMNS));
        int highest = 0;
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < Slope.COLUMNS; i++) {
                int u = side == Direction.EAST ? last - r : side == Direction.WEST ? r : i;
                int w = side == Direction.SOUTH ? last - r : side == Direction.NORTH ? r : i;
                highest = Math.max(highest, map.slopeHeight(cell, u, w));
            }
        }
        return highest / (double) Slope.PIXELS;
    }

    /** How high (in blocks) the surface of a slope in {@code cell} rises along its edge on {@code side}; 0 for a plain floor. */
    private static double lip(CellMap map, BlockPos cell, Direction side) {
        return map.isSlope(cell) ? AntPathFinder.edgeHighest(map, cell, side) / (double) Slope.PIXELS : 0.0;
    }

    /**
     * Sets the exact motion toward a point at the insect's climbing speed. True if this tick's motion gets there.
     * Tiny sideways corrections (the body a hair over the edge of its cell) are made first, on their own: moving on with
     * the body poking into the next column would catch on the block there and stop it for good.
     */
    private boolean moveAttached(Vec3 target) {
        attached = true;
        double step = climbSpeed();
        BlockPos column = huggedColumn();
        if (column != null && isNarrowNear(column, target, step)) {
            return hugAlong(column, target, step);
        }
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
        motion = distance <= step ? delta : delta.scale(step / distance);
        return distance <= step;
    }

    /** Whether the column is narrower than a full block anywhere the body is, goes next or ends up. */
    private boolean isNarrowNear(BlockPos column, Vec3 target, double step) {
        double height = ant.getBbHeight();
        return widestBetween(column, ant.getY() - step, ant.getY() + height + step) < 1.0
                || widestBetween(column, target.y - step, target.y + height + step) < 1.0;
    }

    /**
     * A move along a column that is narrower than a block (a spire piece, a mouth): along the plan one step at a time,
     * drawn in onto the column at the height it goes to, so the body follows the column's shape (a taper, a narrow
     * mouth over a wider piece) instead of cutting across. Sideways along the face it is drawn in by the column's width
     * level with the body there; out from the face by the widest part within a step above or below too, so the body is
     * already clear of a wider part it is about to pass. True once it is at the target.
     */
    private boolean hugAlong(BlockPos column, Vec3 target, double step) {
        Vec3 plan = pos();
        Vec3 planDelta = target.subtract(plan);
        double planDistance = planDelta.length();
        boolean reaches = planDistance <= step;
        Vec3 next = reaches ? target : plan.add(planDelta.scale(step / planDistance));
        double height = ant.getBbHeight();
        double along = columnWidth(column, next.y);
        double across = widestBetween(column, next.y - step, next.y + height + step);
        Vec3 body = drawIn(next, column, along, across);
        Vec3 delta = body.subtract(ant.position());
        double swept = widestBetween(column, Math.min(ant.getY(), next.y), Math.max(ant.getY(), next.y) + height);
        if (Math.abs(delta.y) > 1.0E-6 && delta.horizontalDistanceSqr() > 1.0E-6
                && outFrom(column, ant.position()) < hugOut(swept) - 1.0E-4) {
            // Not yet clear of a wider part of the column it is about to pass (a wider step below, a wider block
            // above): away from it first, then on, so the body never sits on the step's edge or cuts into it.
            Vec3 away = new Vec3(delta.x, 0.0, delta.z);
            double length = away.length();
            motion = length <= step ? away : away.scale(step / length);
            return false;
        }
        double distance = delta.length();
        double most = step * 1.5; // drawn in, a step along the plan can be a little longer on the body
        motion = distance <= most ? delta : delta.scale(most / distance);
        return reaches && distance <= most;
    }

    /** How far from a column's middle the body's middle is when it hugs a column this wide. */
    private double hugOut(double width) {
        return width / 2.0 + half() + EPS;
    }

    /** How far (in blocks) a point is from the middle of a column, across the wall the body holds. */
    private double outFrom(BlockPos column, Vec3 point) {
        Direction.Axis across = surface.getAxis();
        return Math.abs(coord(point, across) - centerOf(column, across));
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

    /**
     * On a wall the body faces the wall and the head turns the way it moves; on a ceiling the head turns the way it
     * moves (measured from {@link #ceilingRef}); on a floor it faces the way it moves.
     */
    private void faceTheWayItGoes() {
        if (surface == Direction.UP) {
            if (attached && motion.horizontalDistanceSqr() > 1.0E-8) {
                Direction left = ceilingRef.getCounterClockWise();
                double sideways = motion.x * left.getStepX() + motion.z * left.getStepZ();
                double away = -(motion.x * ceilingRef.getStepX() + motion.z * ceilingRef.getStepZ());
                if (Math.abs(sideways) > 1.0E-5 || Math.abs(away) > 1.0E-5) {
                    heading = (float) Math.toDegrees(Math.atan2(sideways, away));
                }
            }
            setYaw(ceilingRef.toYRot() + 180.0F + heading);
        } else if (surface.getAxis().isHorizontal()) {
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

    // --- Hugging a column that narrows (ClimbShape) ---
    //
    // Every move is worked out as if the column were a full block (the plan position, in the cells the route planner
    // sees). On a narrower column the plan position is drawn in: the sides of the full block onto the column's sides
    // at that height and the face across onto its narrower face, so the body stays pressed against the real column.

    /** The column the body hugs now (the block on the wall side it holds), or null (a floor, a ceiling, a doorway's motion). */
    @Nullable
    private BlockPos huggedColumn() {
        if (exact || !surface.getAxis().isHorizontal()) {
            return null;
        }
        if (stepTo != null && stepTo.surface() == surface) {
            return stepTo.cell().relative(surface);
        }
        if (stepFrom != null && stepFrom.surface() == surface) {
            return stepFrom.cell().relative(surface);
        }
        BlockPos cell = ant.blockPosition();
        return ant.level().getBlockState(cell).getBlock() instanceof ClimbShape ? cell : cell.relative(surface);
    }

    /**
     * How wide the column is beside a body whose feet are at {@code y}: the widest part of it the body is level with
     * (any other solid block counts as full width), so the body never cuts into a wider step of it. 1 if nothing there
     * is narrower than a full block.
     */
    private double columnWidth(BlockPos column, double y) {
        return widestBetween(column, y, y + ant.getBbHeight());
    }

    /** The widest part of the column between the heights {@code low} and {@code high} (see {@link #columnWidth}). */
    private double widestBetween(BlockPos column, double low, double high) {
        double widest = 0.0;
        boolean any = false;
        for (int by = Mth.floor(low); by <= Mth.floor(high - 1.0E-6); by++) {
            BlockPos pos = new BlockPos(column.getX(), by, column.getZ());
            BlockState state = ant.level().getBlockState(pos);
            if (state.getBlock() instanceof ClimbShape shape) {
                for (int k = 0; k < Slope.COLUMNS; k++) {
                    double bottom = by + k / (double) Slope.COLUMNS;
                    if (bottom + 1.0 / Slope.COLUMNS > low && bottom < high) {
                        widest = Math.max(widest, shape.widthAt(state, (k + 0.5) / Slope.COLUMNS));
                        any = true;
                    }
                }
            } else if (!state.getCollisionShape(ant.level(), pos).isEmpty()) {
                widest = 1.0;
                any = true;
            }
        }
        return any ? Math.min(1.0, widest) : 1.0;
    }

    /**
     * A plan position drawn in onto a column: sideways along the face it holds by the width {@code along}, out from the
     * face by the width {@code across}.
     */
    private Vec3 drawIn(Vec3 plan, BlockPos column, double along, double across) {
        Direction.Axis out = surface.getAxis();
        double x = squeeze(plan.x, column.getX() + 0.5, out == Direction.Axis.X ? across : along);
        double z = squeeze(plan.z, column.getZ() + 0.5, out == Direction.Axis.Z ? across : along);
        return new Vec3(x, plan.y, z);
    }

    /** Where the body really goes for a plan position: drawn in onto the hugged column if it is narrower than a block. */
    private Vec3 toBody(Vec3 plan) {
        BlockPos column = huggedColumn();
        if (column == null) {
            return plan;
        }
        double w = columnWidth(column, plan.y);
        if (w >= 1.0) {
            return plan;
        }
        return new Vec3(squeeze(plan.x, column.getX() + 0.5, w), plan.y, squeeze(plan.z, column.getZ() + 0.5, w));
    }

    /** The plan position of the body (where it would be on a full block): what every move is worked out from. */
    private Vec3 pos() {
        BlockPos column = huggedColumn();
        if (column == null) {
            return ant.position();
        }
        // Sideways along the face: by the column's width level with the body. Out from the face: by the width the body
        // is hugging (how far out it is), so a body pressed against the column is always pressed against the plan's
        // full face, whatever the column's width level with it (above the column's top, at a mouth's tube).
        Direction.Axis out = surface.getAxis();
        double w = columnWidth(column, ant.getY());
        double d = coord(ant.position(), out) - centerOf(column, out);
        double hugged = 2.0 * (Math.abs(d) - half() - EPS);
        double across = hugged > 0.0 ? Math.min(1.0, hugged) : w;
        double x = unsqueeze(ant.getX(), column.getX() + 0.5, out == Direction.Axis.X ? across : w);
        double z = unsqueeze(ant.getZ(), column.getZ() + 0.5, out == Direction.Axis.Z ? across : w);
        return new Vec3(x, ant.getY(), z);
    }

    /** One axis drawn in: within the block's width scaled to the column's, outside it moved in by what the column lacks. */
    private static double squeeze(double v, double center, double width) {
        double d = v - center;
        double a = Math.abs(d);
        return a <= 0.5 ? center + d * width : center + Math.signum(d) * (width / 2.0 + a - 0.5);
    }

    /** The other way round. */
    private static double unsqueeze(double v, double center, double width) {
        double d = v - center;
        double a = Math.abs(d);
        return a <= width / 2.0 ? center + d / width : center + Math.signum(d) * (a - width / 2.0 + 0.5);
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
        Vec3 here = pos();
        Vec3 point = with(new Vec3(here.x, y, here.z), wall.getAxis(), pinned(foothold.cell(), wall));
        return with(point, along, clampIn(foothold.cell(), along, coord(here, along)));
    }

    /** The horizontal axis along a wall (sideways on it). */
    private static Direction.Axis sideAxis(Direction wall) {
        return wall.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    private double clampIn(BlockPos cell, Direction.Axis axis, double value) {
        double low = cell.get(axis) + half() + EPS;
        return Mth.clamp(value, low, cell.get(axis) + 1 - half() - EPS);
    }

    /** The height of the body's feet when its back is pressed against the ceiling of this cell. */
    private double ceilingY(BlockPos cell) {
        return cell.getY() + 1 - ant.getBbHeight() - EPS;
    }

    /** The point on a ceiling foothold: pressed against the ceiling, sideways where the body is (kept inside the cell). */
    private Vec3 ceilingPoint(Foothold foothold) {
        BlockPos cell = foothold.cell();
        return new Vec3(clampIn(cell, Direction.Axis.X, ant.getX()), ceilingY(cell), clampIn(cell, Direction.Axis.Z, ant.getZ()));
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
        Vec3 here = pos();
        double toward = (here.x - (cell.getX() + 0.5)) * wall.getStepX() + (here.z - (cell.getZ() + 0.5)) * wall.getStepZ();
        return 0.5 - toward - half();
    }

    private void replan(String reason) {
        if (++replans > MAX_REPLANS) {
            AntColoniesMod.LOGGER.debug("Ant {} gave up walking to {} ({})", ant.getId(), goal.toShortString(), reason);
            Foothold here = currentFoothold(new LevelCellMap(ant.level(), ant));
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
