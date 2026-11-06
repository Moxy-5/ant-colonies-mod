package com.antcolonies.nav;

import com.antcolonies.debug.Perf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;
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
 * ({@link #drawIn}, {@link #pos}). On a slope ({@link Slope}, a ramp) it walks with the normal walking. In a channel
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
    /** Farther than this (in blocks) from the middle of the cell its step goes to, the insect was knocked off its route. */
    private static final double OFF_THE_ROUTE = 3.0;
    /** A held wall, ceiling or channel this far (in blocks) from the body's middle at most is still held; farther, it lets go. */
    private static final double HOLD_REACH = 1.5;
    /** Between two cells (over an edge) the foothold the step started from still counts as its own this close (in blocks). */
    private static final double STILL_ON_IT = 1.2;
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
    /** Left between the body and a channel's side wall it holds (in blocks). */
    private static final double CHANNEL_GAP = 0.01;
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
    private Predicate<Foothold> goalTest;
    /**
     * With {@link #goalTest}: every foothold the test accepts lies within this many blocks of {@link #goal} each way (the
     * mind's promise, its reach), so a walk is known to have no route when no foothold in that box passes, without a
     * route search ({@code docs/plans/apex-termites.md}, T4). -1: no such promise.
     */
    private int goalReach = -1;
    /**
     * When the mind gave a choice of cells (the free spots of a chamber, say) and the walk goes to the nearest it can
     * reach; else null. Only for the first plan: once it is found the goal is that one cell (planning again keeps to it).
     */
    @Nullable
    private Collection<BlockPos> goalCells;
    /** When the mind handed over a route to walk ({@link #follow}); else null. Kept when the route is planned again. */
    @Nullable
    private Route shared;
    /** The step of the shared route the insect (re)joins it at: 0 at first, where it left it after planning again. */
    private int sharedFrom;
    /** The shared route's step {@code k} is step {@code k + sharedOffset} of {@link #route}. */
    private int sharedOffset;
    private double speed = 1.0;
    private boolean failed;
    private int replans;
    private int stepTicks;
    /**
     * BUGS 122: on a slow step (a doorway's mouth, a fall: {@link #stepLimit} past {@link #STEP_TICKS}), where it stood
     * when it last moved, and the ticks it has stood there since. Standing still {@link #STEP_TICKS} on such a step is
     * stuck, as on any other step; the step's longer time is for a slow motion, not for standing.
     */
    @Nullable
    private Vec3 stillAt;
    private int stillTicks;
    /** BUGS 122: moving less than this (blocks) from {@link #stillAt} counts as standing still. */
    private static final double STILL_MOVE = 0.02;
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
    /** True when this tick's motion is only a hair's correction (see {@link #moveAttached}): it never turns the head. */
    private boolean squaring;
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
    /** The insect's own pace on floors (ticks a step, averaged over its last steps; 0 before its first), for a hidden trip's time. */
    private double pace;
    /** While a hidden trip of this walk is under way: the route and the step it went under at (a break-in puts it back mid-way). */
    @Nullable
    private Route underRoute;
    private int underFrom = -1;

    // --- Personal space (real sizes, step 3, docs/plans/real-sizes.md; the grid of bodies is nav/Crowd) ---

    /**
     * How far (blocks) an insect's own lane lies off a cell's middle at most (my pick: a real Megaponera column runs
     * single file to 2 or 3 abreast; with lanes spread over half a block, 0.2-block ants run 1 to 3 abreast and two can
     * pass in a one-block tunnel).
     */
    private static final double LANE_ROOM = 0.25;
    /** The gap kept between a lane and a cell's side wall (blocks). */
    private static final double LANE_MARGIN = 0.08;
    /** How far past touching (blocks) a walker looks ahead for a body in its way. */
    private static final double LOOK = 0.15;
    /** Ticks a walker keeps behind a nestmate that has stopped in front of it before it steps round it (my pick). */
    private static final int FOLLOW_WAIT = 40;
    /** Ticks a walker waits behind a standing body with no room to pass before it squeezes by (real termites push past in their tunnels). */
    private static final int PASS_WAIT = 30;
    /** Ticks it then squeezes past, bodies brushing, before it looks at the crowd again. */
    private static final int SQUEEZE_TICKS = 20;
    /** How far (blocks) from a floor goal's middle the free spot it stops on may lie. */
    private static final double SPOT_REACH = 0.28;
    /** Within this (blocks) of its free spot a walk is over. */
    private static final double AT_SPOT = 0.06;
    /**
     * Walking onto its free spot it keeps its pace until this close (blocks), then eases to at least
     * {@link #ARRIVE_SLOWEST} of it: a real ant walks up and stops, it never creeps the last bit (the break-in test
     * counted every creep as a stop, real sizes step 3).
     */
    private static final double ARRIVE_EASE = 0.15;
    private static final double ARRIVE_SLOWEST = 0.6;
    /** A standing insect closer than this share of the personal distance to an older stander shuffles aside. */
    private static final double KEEP_APART = 0.85;
    /** How far (blocks) one standing squeezed in looks for a free spot round it (my pick: a major's body and a half). */
    private static final double FREE_REACH = 0.7;
    /** The farther look when nothing is free near (blocks; my pick: a cell and a half, the room round a crowded spot). */
    private static final double FREE_REACH_FAR = 1.5;
    /** Ticks a packed-in insect waits before it looks for a free spot again (my pick; the look costs a few hundred checks). */
    private static final int FREE_RETRY = 10;
    /** At most this far (blocks) a shuffle aims in one go, and at this pace (my pick: a quick step aside, as a jostled ant does). */
    private static final double SHUFFLE_MOST = 0.2;
    private static final double SHUFFLE_PACE = 1.0;
    /** Ticks after a shuffle aside its stopping still counts as the crowd's doing ({@link #heldForCrowd}; my pick: the step takes a few). */
    private static final int SHUFFLE_GRACE = 10;

    /** {@link #keepApart}: packed in, it looks for a free spot again from this tick (its own tick count). */
    private int freeSearchAfter;
    /** The game time it stopped walking (the newcomer to a crowd gives way to those who stood there first); none while walking. */
    private long standSince = Long.MAX_VALUE;
    /** Ticks it has waited behind the body in front of it (following or with no room to pass). */
    private int crowdWaitTicks;
    /** The game time the legs last held it still for the crowd (behind a nestmate, no room to pass, met a stander on arriving). */
    private long crowdHeldAt = Long.MIN_VALUE;
    /** The game time it last shuffled aside for one standing too near ({@link #keepApart}). */
    private long shuffledAt = Long.MIN_VALUE / 2;
    /** Game time until which it squeezes past bodies in its way. */
    private long squeezeUntil;
    /** The free spot in the goal cell this walk ends on (picked on its first tick of arriving; null before). */
    @Nullable
    private Vec3 spot;
    /** The way it walked last (a unit step: its lane lies at right angles to it). */
    private int laneX;
    private int laneZ = 1;
    /** The mind stepped it up to something on purpose ({@link #stepToward}) since its last walk: never shuffled away from it. */
    private boolean closedIn;

    public AntNavigator(AbstractAntEntity ant) {
        this.ant = ant;
    }

    // --- Starting and stopping a walk (what a mind calls) ---

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
        return begin(goal, null, null, -1, null, null, speed, mindCosts);
    }

    /**
     * Starts walking to the nearest of these cells it can reach (one route search for them all); {@link #getGoal} then
     * says which. False (and does nothing) if there is no route to any of them.
     */
    public boolean moveTo(Collection<BlockPos> goals, double speed) {
        if (goals.isEmpty()) {
            return false;
        }
        BlockPos here = ant.blockPosition();
        BlockPos nearest = goals.stream().min(Comparator.comparingInt(cell -> cell.distManhattan(here))).orElseThrow();
        return begin(nearest, null, null, -1, goals, null, speed, AntPathFinder.StepCosts.NONE);
    }

    /**
     * Starts walking to one exact foothold, for example a spot on a ceiling to hang from, or one wall of a shaft.
     * False (and does nothing) if there is no route to it.
     */
    public boolean moveTo(Foothold goal, double speed) {
        return begin(goal.cell(), goal, null, -1, null, null, speed, AntPathFinder.StepCosts.NONE);
    }

    /**
     * Starts walking to the nearest foothold the mind accepts ({@code isGoal}, for example any spot from which a block
     * can be reached), searching toward {@code towards} first. False (and does nothing) if there is none to walk to.
     */
    public boolean moveTo(Predicate<Foothold> isGoal, BlockPos towards, double speed) {
        return begin(towards, null, isGoal, -1, null, null, speed, AntPathFinder.StepCosts.NONE);
    }

    /**
     * Like {@link #moveTo(Predicate, BlockPos, double)}, where {@code isGoal} accepts no foothold more than {@code reach}
     * blocks from {@code towards} each way (a spot to work on a block from, within the insect's reach). When none in
     * that box passes, it knows at once there is no route (the same as a search that found none, without the search:
     * many workers handed a job nobody can reach no longer search the whole nest for it in turn, docs/plans/apex-termites.md T4).
     */
    public boolean moveTo(Predicate<Foothold> isGoal, BlockPos towards, int reach, double speed) {
        return begin(towards, null, isGoal, reach, null, null, speed, AntPathFinder.StepCosts.NONE);
    }

    /**
     * Walks a route a mind planned (a trail, a marching column: one route for many insects). The insect first finds
     * its way onto the route's start, then follows it to the end; knocked off it or blocked on it, it finds its way back
     * onto it where it left it (or further on) and goes on along it. False if it cannot get onto it.
     */
    public boolean follow(Route shared, double speed) {
        return begin(shared.end().cell(), null, null, -1, null, shared, speed, AntPathFinder.StepCosts.NONE);
    }

    /**
     * Real sizes, step 2 ({@code docs/plans/real-sizes.md}): one tick of the last little bit straight at a point beside
     * it on its own floor (a foe, a stung termite, a mouth), once a walk is over: a walk ends anywhere within
     * {@link #ARRIVE_DISTANCE_SQR} of a cell's middle, while a small insect's jaws reach only a third of its length. The
     * point may lie in its own cell, or in a neighbouring cell level with it that it fits into; one lower (down in a
     * broken tunnel) or not to be stood in, it leans out as far as its own cell lets it, never stepping off its floor.
     * False if it cannot step nearer now (walking a route, on a wall or a ceiling, in the air, or already as near as its
     * cell lets it). Legs layer: the mind only says where; the steering is here, as {@link #arrive} walks to a middle.
     */
    public boolean stepToward(Vec3 point, double speed) {
        if (route != null || at != null && !at.isFloor() || surface != Direction.DOWN || !ant.onGround() || ant.isClimbing()) {
            return false;
        }
        BlockPos here = ant.blockPosition();
        BlockPos there = BlockPos.containing(point.x, ant.getY() + 0.01, point.z);
        if (Math.abs(there.getX() - here.getX()) > 1 || Math.abs(there.getZ() - here.getZ()) > 1) {
            return false;
        }
        double x = point.x;
        double z = point.z;
        boolean fits = there.equals(here) || Foothold.isValid(new LevelCellMap(ant.level(), ant), there, Direction.DOWN)
                && ant.level().noCollision(ant, ant.getBoundingBox().move(x - ant.getX(), 0.0, z - ant.getZ()));
        if (!fits) {
            // (leaning over a hole's edge or up to a wall: the middle of its body stays over its own cell, its box clear)
            x = Mth.clamp(x, here.getX() + 0.01, here.getX() + 0.99);
            z = Mth.clamp(z, here.getZ() + 0.01, here.getZ() + 0.99);
            if (!ant.level().noCollision(ant, ant.getBoundingBox().move(x - ant.getX(), 0.0, z - ant.getZ()))) {
                double half = ant.getBbWidth() / 2.0 + 0.01;
                x = Mth.clamp(point.x, here.getX() + half, here.getX() + 1.0 - half);
                z = Mth.clamp(point.z, here.getZ() + half, here.getZ() + 1.0 - half);
            }
        }
        double dx = x - ant.getX();
        double dz = z - ant.getZ();
        closedIn = true;
        if (dx * dx + dz * dz < 0.02 * 0.02) {
            return false;
        }
        ant.getMoveControl().setWantedPosition(x, ant.getY(), z, speed);
        return true;
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

    /**
     * A new walk: to the goal cell, or one exact foothold ({@code foothold}), or any foothold that passes {@code test}
     * (searched toward the goal cell), or the nearest of {@code cells}, or along a shared route; with the mind's costs
     * for it. Plans the route at once.
     */
    private boolean begin(BlockPos goal, @Nullable Foothold foothold, @Nullable Predicate<Foothold> test, int reach,
            @Nullable Collection<BlockPos> cells, @Nullable Route shared, double speed, AntPathFinder.StepCosts mindCosts) {
        this.goal = goal.immutable();
        this.goalFoothold = foothold;
        this.goalTest = test;
        this.goalReach = test == null ? -1 : reach;
        this.goalCells = cells;
        this.shared = shared;
        this.sharedFrom = 0;
        this.speed = speed;
        this.replans = 0;
        this.habits = ant.walkHabits();
        this.mindCosts = mindCosts;
        this.standSince = Long.MAX_VALUE;
        this.crowdWaitTicks = 0;
        this.spot = null;
        this.closedIn = false;
        return plan();
    }

    // --- What the body, the mind and the tests can ask; holding on after a reload ---

    /** What the standing shuffle ({@link #keepApart}) did or why not, last tick it was asked (for the tests' messages). */
    private String apartWhy = "not asked";
    /** The last free-spot searches' rejected spots, by why (for the debug log). */
    private int freeNotClear, freeNotStandable, freePastWall;

    /** For the tests' messages: its personal-space state now (walking or standing, since when, the last shuffle's outcome). */
    public String crowdState() {
        long now = ant.level().getGameTime();
        return (route == null ? "no route" : "route") + ", stood " + (standSince == Long.MAX_VALUE ? "-" : String.valueOf(now - standSince))
                + " ticks, shuffle: " + apartWhy + ", last shuffle " + (now - shuffledAt) + " ago";
    }

    /**
     * Whether its legs held it still for the crowd this tick or the last (waiting behind a nestmate, for room to pass,
     * or stopping on meeting one already standing on its spot), or it has just shuffled aside for one standing too
     * near: real sizes step 3, so a test counting stops can tell giving way in a crowd from stopping dead.
     */
    public boolean heldForCrowd() {
        long now = ant.level().getGameTime();
        return now - crowdHeldAt <= 1 || now - shuffledAt <= SHUFFLE_GRACE;
    }

    /** Whether it has shuffled aside for one standing too near in the last {@link #SHUFFLE_GRACE} ticks (tests sort stops by it). */
    public boolean shuffledLately() {
        return ant.level().getGameTime() - shuffledAt <= SHUFFLE_GRACE;
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
    public boolean isMovingExactly() {
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

    /** The cell this walk goes to (after a walk to the nearest of several cells: the one it chose). */
    public BlockPos getGoal() {
        return goal;
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

    // --- Planning a route (and planning again when stuck, blocked or the way changed) ---

    /** Plans from where the insect is: to the goal, or onto a shared route (at {@link #sharedFrom}) and then along it. */
    private boolean plan() {
        CellMap map = new LevelCellMap(ant.level(), ant);
        Foothold start = currentFoothold(map);
        List<Foothold> starts = new ArrayList<>();
        boolean fromBeside = false;
        if (start != null) {
            starts.add(start);
        } else {
            // In the air: plan from where it will land.
            Foothold landing = AntPathFinder.landing(map, ant.blockPosition());
            if (landing != null) {
                starts.add(landing);
            } else {
                // Inside a block that is not open (a thin plate of mud put down where it stood, the user's bug of
                // 2026-10-14): from the open spots beside it, which it can step out to.
                // Also a ramp it stands on with a block put on top of it (BUGS 148: a termite on a mound ramp, a
                // builder's thin layer laid over it, no longer counts as a floor): the walls beside it as well as the
                // floors, since on the face of a mound there is often no floor within a drop.
                for (Direction side : Direction.values()) {
                    if (side == Direction.DOWN) {
                        continue;
                    }
                    BlockPos next = ant.blockPosition().relative(side);
                    List<Foothold> holds = AntPathFinder.footholdsIn(map, next);
                    if (!holds.isEmpty()) {
                        starts.addAll(holds);
                        continue;
                    }
                    Foothold beside = AntPathFinder.landing(map, next);
                    if (beside != null) {
                        starts.add(beside);
                    }
                }
                fromBeside = !starts.isEmpty();
            }
        }
        long now = ant.level().getGameTime();
        avoid.values().removeIf(until -> until <= now);
        AntPathFinder.StepCosts costs = avoid.isEmpty() ? mindCosts
                : mindCosts.and((from, to, kind) -> avoid.containsKey(to) ? 50.0 : 0.0);
        Route found = null;
        if (!starts.isEmpty() && mayHaveGoal()) {
            Perf.asking(ant.getType().builtInRegistryHolder().key().location().getPath() + " " + ant.runningGoals()
                    + (replans > 0 ? " (again)" : ""));
            try {
                // Keeping to its habits if it can; if no route does, without them.
                found = habits == AntPathFinder.StepCosts.NONE ? null : search(map, starts, costs.and(habits));
                if (found == null) {
                    found = search(map, starts, costs);
                }
            } finally {
                Perf.asking(null);
            }
        }
        failed = found == null;
        route = found;
        if (goalCells != null) {
            // The nearest of the cells is chosen: planning again keeps to it.
            goalCells = null;
            if (found != null) {
                goal = found.end().cell();
            }
        }
        if (found != null && shared != null) {
            sharedOffset = found.size() - shared.size(); // the shared route's steps are the route's last ones
        }
        next = 1;
        startLeg();
        stepTicks = 0;
        arriveTicks = 0;
        blockedTicks = 0;
        blockedFrom = ant.position();
        spot = null;
        at = start;
        if (found != null && fromBeside) {
            stepOutTo(found.start());
        }
        return found != null;
    }

    /**
     * Shut in where it stands (a block put on it, or over the ramp it stands on, BUGS 148), its route starts on a spot
     * beside it: it steps straight out onto it (at most a block, a short hop), and the walk goes on from there. Without
     * this it stood where it was, "landed somewhere else" every tick, and gave the walk up.
     */
    private void stepOutTo(Foothold start) {
        BlockPos cell = start.cell();
        if (start.isFloor() && cell.getY() < ant.blockPosition().getY()) {
            // A floor further down beside it: out over the edge at its own height, and it drops onto it (the walk
            // starts once it has landed there, as after any fall).
            ant.moveTo(cell.getX() + 0.5, ant.getY(), cell.getZ() + 0.5, ant.getYRot(), ant.getXRot());
            ant.setDeltaMovement(Vec3.ZERO);
            at = null;
            surface = Direction.DOWN;
            return;
        }
        double y = start.isFloor() ? cell.getY() : Mth.clamp(ant.getY(), cell.getY(), cell.getY() + 1.0 - ant.getBbHeight());
        double x = cell.getX() + 0.5;
        double z = cell.getZ() + 0.5;
        if (start.isWall()) {
            // Right up against the wall it holds, not in the cell's middle.
            double in = 0.5 - ant.getBbWidth() / 2.0 - 0.01;
            x += start.surface().getStepX() * in;
            z += start.surface().getStepZ() * in;
        }
        ant.moveTo(x, y, z, ant.getYRot(), ant.getXRot());
        ant.setDeltaMovement(Vec3.ZERO);
        at = start;
        surface = start.inChannel() ? Direction.DOWN : start.surface();
    }

    /**
     * False when the walk is known to have no route before any search: the mind promised its goal footholds lie within
     * {@link #goalReach} of the goal cell, and no foothold there passes its test (every kind of foothold a cell can give
     * is tried: each surface, in a channel or not). True otherwise (then the search decides).
     */
    private boolean mayHaveGoal() {
        if (goalTest == null || goalReach < 0 || shared != null) {
            return true;
        }
        for (BlockPos cell : BlockPos.betweenClosed(goal.offset(-goalReach, -goalReach, -goalReach), goal.offset(goalReach, goalReach, goalReach))) {
            for (Direction surface : Direction.values()) {
                if (goalTest.test(new Foothold(cell, surface, false)) || goalTest.test(new Foothold(cell, surface, true))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One search for a route from the starts: to the goal, or onto the shared route and along it. Null if none. */
    @Nullable
    private Route search(CellMap map, List<Foothold> starts, AntPathFinder.StepCosts costs) {
        if (shared == null) {
            if (goalCells != null) {
                return AntPathFinder.find(map, starts, goalCells, AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs);
            }
            return goalTest != null
                    ? AntPathFinder.find(map, starts, goalTest, goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs)
                    : goalFoothold == null
                    ? AntPathFinder.find(map, starts, goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs)
                    : AntPathFinder.find(map, starts, goalFoothold::equals, goal, AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs);
        }
        // Onto the shared route where it joins it (its start, or where it left it); if that spot can't be reached (a
        // block put on the trail), onto any later step of it.
        Foothold join = shared.step(sharedFrom).to();
        Route onto = AntPathFinder.find(map, starts, join::equals, join.cell(), AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs);
        if (onto == null && sharedFrom + 1 < shared.size()) {
            Set<Foothold> later = new HashSet<>();
            for (Route.Step step : shared.steps().subList(sharedFrom + 1, shared.size())) {
                later.add(step.to());
            }
            onto = AntPathFinder.find(map, starts, later::contains, shared.end().cell(), AntPathFinder.DEFAULT_MAX_EXPANSIONS, costs);
        }
        if (onto == null) {
            return null;
        }
        int joined = sharedFrom;
        while (!shared.step(joined).to().equals(onto.end())) {
            joined++;
        }
        List<Route.Step> steps = new ArrayList<>(onto.steps());
        steps.addAll(shared.steps().subList(joined + 1, shared.size()));
        return new Route(steps, onto.cost() + shared.cost());
    }

    private void replan(String reason) {
        if (++replans > MAX_REPLANS) {
            ColonyFrameworkMod.LOGGER.debug("Ant {} gave up walking to {} ({})", ant.getId(), goal.toShortString(), reason);
            Foothold here = currentFoothold(new LevelCellMap(ant.level(), ant));
            route = null;
            failed = true;
            at = here;
            return;
        }
        ColonyFrameworkMod.LOGGER.debug("Ant {} plans a new route to {} ({})", ant.getId(), goal.toShortString(), reason);
        if (shared != null && route != null && next - sharedOffset > sharedFrom) {
            // Back onto the shared route where it left it: the step it was walking to.
            sharedFrom = Math.min(shared.size() - 1, next - sharedOffset);
        }
        if (!plan()) {
            ColonyFrameworkMod.LOGGER.debug("Ant {} found no route to {}", ant.getId(), goal.toShortString());
        }
    }

    // --- One tick of walking: the step being walked, stuck or blocked, arriving, holding on ---

    /** Called every tick from the insect, before it moves. */
    public void tick() {
        CellMap map = new LevelCellMap(ant.level(), ant);
        noteInCrowd();
        if (route == null) {
            standSince = Math.min(standSince, ant.level().getGameTime());
        } else {
            standSince = Long.MAX_VALUE;
        }
        attached = false;
        exact = false;
        inChannel = false;
        motion = Vec3.ZERO;
        squaring = false;
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
        if (stepTicks == 0 && leg == 0 && goUnder()) {
            return; // out of the world along a hidden stretch of the galleries: it comes back at the far end
        }
        Route.Step step = route.step(next);
        stepFrom = at;
        stepTo = step.to();
        String changed = AntPathFinder.whatChanged(map, at, step.to(), step.kind());
        if (changed != null) {
            replan("the way changed: " + changed + ", " + step.kind().name().toLowerCase()); // a block put in the way (the hill growing, a hole sealed, a player)
            return;
        }
        if (step.kind() != StepKind.FALL && ant.position().distanceToSqr(Vec3.atCenterOf(step.to().cell())) > OFF_THE_ROUTE * OFF_THE_ROUTE) {
            replan("knocked off the route");
            return;
        }
        if (advance(map, at, step)) {
            if (step.kind() == StepKind.WALK) {
                double took = Math.min(40, stepTicks + 1);
                pace = pace <= 0 ? took : pace * 0.9 + took * 0.1;
            }
            at = step.to();
            next++;
            startLeg();
            stepTicks = 0;
        } else if (isBlocked()) {
            // Pushing along a surface and getting nowhere: something is in the way that the planner can't see.
            avoid.put(step.to(), ant.level().getGameTime() + AVOID_TICKS);
            replan("blocked on " + describe(step));
            return;
        } else if (++stepTicks > stepLimit(step)) {
            replan("stuck on " + describe(step) + ", motion " + motion);
            return;
        } else if (standsTooLong(step)) {
            replan("standing still on " + describe(step) + ", motion " + motion);
            return;
        }
        faceTheWayItGoes();
    }

    // --- Hidden trips (termites underground, step 2b): a long closed stretch of the galleries is travelled unseen ---

    /**
     * At the start of a step: if the route runs from here along at least {@link HiddenWays#shortest} steps of hidden way
     * (the insect's colony's closed galleries), the stretch is handed to the hidden ways, which take the insect out of
     * the world for as long as the walk takes at its own pace. Its route goes on from the stretch's far end (the last
     * floor of it), where it is put back. True if it went.
     */
    private boolean goUnder() {
        if (at == null || !at.isFloor()) {
            return false;
        }
        HiddenWays ways = ant.hiddenWays();
        if (ways == null || !ways.isHidden(at.cell())) {
            return false;
        }
        int end = next - 1;
        while (end + 1 < route.size() && route.step(end + 1).kind() != StepKind.FALL && ways.isHidden(route.step(end + 1).to().cell())) {
            end++;
        }
        while (end > next - 1 && !route.step(end).to().isFloor()) {
            end--;
        }
        if (end - (next - 1) < ways.shortest()) {
            return false;
        }
        List<Foothold> run = new ArrayList<>();
        run.add(at);
        for (int i = next; i <= end; i++) {
            run.add(route.step(i).to());
        }
        long ticks = Math.max(1, Math.round((run.size() - 1) * stepPace()));
        Route walked = route;
        int from = next - 1;
        if (!ways.goUnder(ant, run, ticks)) {
            return false;
        }
        underRoute = walked;
        underFrom = from;
        at = walked.step(end).to();
        next = end + 1;
        surface = Direction.DOWN;
        startLeg();
        stepTicks = 0;
        arriveTicks = 0;
        blockedTicks = 0;
        return true;
    }

    /** Ticks a step takes this insect on a floor: its own pace once it has walked a few, else worked out from its speed. */
    private double stepPace() {
        if (pace > 0) {
            return pace;
        }
        double perTick = 1.2 * ant.getAttributeValue(Attributes.MOVEMENT_SPEED) * speed;
        return perTick <= 0.01 ? 20.0 : 1.0 / perTick;
    }

    /**
     * Back in the world from a hidden trip, at the foothold {@code index} of its stretch ({@code where}): the far end, or
     * mid-way when someone broke into the gallery. The walk goes on from there (if it is still the walk it went under on;
     * after a game was loaded again it is a new insect with no walk, and just stands there).
     */
    public void cameUp(int index, Foothold where) {
        if (route != null && route == underRoute && underFrom >= 0 && underFrom + index < route.size()
                && route.step(underFrom + index).to().equals(where)) {
            at = where;
            next = underFrom + index + 1;
        } else if (route == null) {
            at = where;
        }
        underRoute = null;
        underFrom = -1;
        surface = Direction.DOWN;
        attached = false;
        startLeg();
        stepTicks = 0;
        arriveTicks = 0;
        blockedTicks = 0;
        blockedFrom = ant.position();
    }

    /** Moves the insect one tick along a step. True once the step is done (this tick's motion completes it). */
    private boolean advance(CellMap map, Foothold from, Route.Step step) {
        Foothold to = step.to();
        return switch (step.kind()) {
            case START -> true;
            case WALK -> walk(map, to);
            case FALL -> fall(map, to);
            case ONTO_WALL -> ontoWall(map, from, to);
            case OFF_WALL -> offWall(from);
            case CLIMB_UP, CLIMB_DOWN, CRAWL_SIDEWAYS -> climb(to, step.kind() == StepKind.CRAWL_SIDEWAYS);
            case TURN_CORNER -> turnCorner(from, to);
            case ROUND_EDGE -> roundEdge(from, to);
            case OVER_TOP -> overTop(map, from, to);
            case OVER_EDGE -> overEdge(map, from, step);
            case ONTO_CEILING -> ontoCeiling(from);
            case OFF_CEILING -> offCeiling(from, to);
            case CRAWL_CEILING -> crawlCeiling(from, to);
            case UNDER_EDGE -> underEdge(from, to);
            case OUT_FROM_UNDER -> outFromUnder(from, to);
            case CRAWL_CHANNEL -> crawlChannel(from.cell(), to.cell());
            case INTO_CHANNEL -> intoChannel(from, to.cell());
            case OUT_OF_CHANNEL -> outOfChannel(from.cell(), to);
            case OUT_OF_MOUTH -> outOfMouth(from.cell(), to);
            case INTO_MOUTH -> intoMouth(from, to.cell());
        };
    }

    /** A step starts from its first part (and a doorway's motion from its first point, not looking round). */
    private void startLeg() {
        stillAt = null;
        stillTicks = 0;
        leg = 0;
        legTicks = 0;
        holding = false;
        peeking = false;
        slowTurn = false;
    }

    /**
     * BUGS 122: on a slow step, whether it has stood still for as long as any step may take ({@link #STEP_TICKS}); its
     * doorway's own pauses (a peek, {@link DoorwayMotion#PEEK_TICKS}) are shorter, and waiting for the crowd never counts.
     * Termite workers stood a minute at a mound's doorway: 20 s a try, three tries, never moving.
     */
    private boolean standsTooLong(Route.Step step) {
        if (stepLimit(step) <= STEP_TICKS) {
            return false;
        }
        Vec3 here = ant.position();
        if (stillAt == null || here.distanceToSqr(stillAt) > STILL_MOVE * STILL_MOVE || heldForCrowd()) {
            stillAt = here;
            stillTicks = 0;
            return false;
        }
        return ++stillTicks > STEP_TICKS;
    }

    /** How many ticks this step may take before the insect counts as stuck: a fall and a doorway's mouth take longer. */
    private int stepLimit(Route.Step step) {
        return step.kind() == StepKind.FALL ? FALL_TICKS : isDoorwayStep(step) ? DOORWAY_STEP_TICKS : STEP_TICKS;
    }

    /** Whether this step comes out of a doorway's mouth or goes in by it (it takes longer than other steps). */
    private boolean isDoorwayStep(Route.Step step) {
        return step.kind() == StepKind.OUT_OF_MOUTH || step.kind() == StepKind.INTO_MOUTH
                || (step.kind() == StepKind.OVER_TOP && at != null && doorwayAt(at.cell()) != null)
                || (step.kind() == StepKind.OVER_EDGE && doorwayAt(step.to().cell()) != null);
    }

    /** The step being walked, for the log: its kind, which part of it, from where to where, and where the body is. */
    private String describe(Route.Step step) {
        return step.kind().name().toLowerCase() + " (part " + leg + ") from " + (at == null ? "the air" : at.toString()) + " to " + step.to()
                + " at " + String.format("%.3f, %.3f, %.3f", ant.getX(), ant.getY(), ant.getZ());
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
        if (end.isFloor() && Crowd.keepsSpace(ant)) {
            surface = Direction.DOWN;
            long now = ant.level().getGameTime();
            if (spot == null || arriveTicks % 5 == 4 && Crowd.of(ant.level()).clearance(ant, spot, spaceFor(Crowd.radius(ant)), now) < 0.0) {
                spot = freeSpot(map, end.cell()); // (first, or someone has stopped on its spot meanwhile: another)
            }
            double dx = ant.getX() - spot.x;
            double dz = ant.getZ() - spot.z;
            double far = Math.sqrt(dx * dx + dz * dz);
            // Pressing into one already standing there, it stops at once and steps aside as the newcomer ({@link #keepApart}),
            // never walking on through it to its spot.
            boolean met = far > AT_SPOT && arriveTicks >= 2 && insideStander(now);
            if (met) {
                crowdHeldAt = now;
            }
            if (far > AT_SPOT && ++arriveTicks <= ARRIVE_TIMEOUT && !met) {
                // (slowing as it nears, so a small body stops on its spot instead of circling it)
                Vec3 aim = steer(spot, speed * Mth.clamp(far / ARRIVE_EASE, ARRIVE_SLOWEST, 1.0));
                ant.getMoveControl().setWantedPosition(aim.x, spot.y, aim.z, aimSpeed);
                return;
            }
        } else if (end.isFloor()) {
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
        if (at != null && at.isChannel() && at.isValid(map) && ant.position().distanceToSqr(Vec3.atCenterOf(at.cell())) < HOLD_REACH * HOLD_REACH) {
            // In a channel: lie still in its middle.
            exact = true;
            inChannel = true;
            moveAttached(channelPoint(at.cell()));
            faceTheWayItGoes();
            return;
        }
        if (at != null && !at.isFloor() && !at.isChannel() && at.isValid(map)
                && ant.position().distanceToSqr(Vec3.atCenterOf(at.cell())) < HOLD_REACH * HOLD_REACH) {
            surface = at.surface();
            moveAttached(at.isCeiling() ? ceilingPoint(at) : wallPoint(at, clampY(at.cell(), ant.getY())));
            faceTheWayItGoes();
            return;
        }
        if (at != null && !at.isFloor()) {
            at = null; // its wall or ceiling is gone: let go
        }
        surface = Direction.DOWN;
        keepApart();
    }

    // --- The moves on floors, walls and ceilings (one tick each; true once the move is done) ---

    /** Along a floor into the next cell, with the normal walking. */
    private boolean walk(CellMap map, Foothold to) {
        surface = Direction.DOWN;
        Vec3 aim = Vec3.atBottomCenterOf(to.cell());
        if (Crowd.keepsSpace(ant) && stepFrom != null) {
            aim = lanePoint(map, stepFrom.cell(), to.cell(), aim);
            Vec3 steered = steer(aim, speed);
            ant.getMoveControl().setWantedPosition(steered.x, aim.y, steered.z, aimSpeed);
            return inFloorCell(map, to.cell());
        }
        ant.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
        return inFloorCell(map, to.cell());
    }

    // --- Personal space: lanes, stepping round, free spots, standing apart (real sizes, step 3) ---

    /** The speed {@link #steer} last chose (slower behind a nestmate, 0 while waiting). */
    private double aimSpeed;

    /** Writes the insect into its world's crowd for this tick, if it keeps its space and stands on a floor. */
    private void noteInCrowd() {
        if (!Crowd.keepsSpace(ant) || surface != Direction.DOWN || inChannel || attached || ant.isPassenger()
                || ant.isDowned() || ant.isPinned()) {
            return; // (on a wall, a ceiling or in a channel it moves exactly; felled or held, the others step over it)
        }
        Vec3 v = ant.getDeltaMovement();
        Crowd.of(ant.level()).note(new Crowd.Body(ant, ant.getX(), ant.getY(), ant.getZ(), Crowd.radius(ant), ant.getBbHeight(),
                v.x, v.z, route == null, standSince), ant.level().getGameTime());
    }

    /**
     * Its own lane, -1 to 1, from its id: fixed for life, more often near the middle than at the sides (two dice added),
     * so a column of them runs mostly 1 to 3 abreast.
     */
    private double lane() {
        long bits = ant.getUUID().getLeastSignificantBits() ^ ant.getUUID().getMostSignificantBits();
        double a = ((bits >>> 8) & 0xFFFF) / 65536.0;
        double b = ((bits >>> 32) & 0xFFFF) / 65536.0;
        return a + b - 1.0;
    }

    /** How far its lane may lie off a cell's middle in a passage {@code width} wide sideways: up to {@link #LANE_ROOM}. */
    private double laneRoom() {
        return Mth.clamp((1.0 - ant.getBbWidth()) / 2.0 - LANE_MARGIN, 0.0, LANE_ROOM);
    }

    /**
     * Where in the next cell its lane runs: the cell's middle moved sideways (at right angles to the step) by its lane.
     * The middle itself on a slope or where its box would not fit there (a doorway, a narrow gap: single file).
     */
    private Vec3 lanePoint(CellMap map, BlockPos from, BlockPos to, Vec3 middle) {
        if (laneRoute == route && laneStep == next && laneAim != null) {
            return laneAim; // (worked out once a step)
        }
        laneRoute = route;
        laneStep = next;
        laneAim = laneFor(map, from, to, middle);
        return laneAim;
    }

    @Nullable
    private Route laneRoute;
    private int laneStep = -1;
    @Nullable
    private Vec3 laneAim;

    private Vec3 laneFor(CellMap map, BlockPos from, BlockPos to, Vec3 middle) {
        int dx = Integer.signum(to.getX() - from.getX());
        int dz = Integer.signum(to.getZ() - from.getZ());
        if (dx != 0 && dz != 0 || dx == 0 && dz == 0) {
            return middle;
        }
        laneX = dx;
        laneZ = dz;
        if (map.isSlope(to) || map.isSlope(from)) {
            return middle;
        }
        double off = lane() * laneRoom();
        Vec3 point = middle.add(-dz * off, 0.0, dx * off);
        return fits(point) ? point : middle;
    }

    /** Whether its box fits standing at this point, at its own height (no block in the way). */
    private boolean fits(Vec3 point) {
        return ant.level().noCollision(ant, ant.getBoundingBox().move(point.x - ant.getX(), 0.0, point.z - ant.getZ()));
    }

    /**
     * Where to aim this tick on the way to {@code aim} so as not to walk into anyone, and how fast ({@link #aimSpeed}): a
     * nestmate walking ahead the same way is followed a little gap behind (slower, or waiting while it is too near); a
     * body standing in the way, or one met head on, is stepped round on the side away from it, inside the cells it walks
     * through; with no room it waits, and after {@link #PASS_WAIT} ticks squeezes past (bodies brushing, as termites
     * push by in their tunnels). Only bodies on its floor within reach ahead count. Legs layer: no mind is asked.
     */
    private Vec3 steer(Vec3 aim, double pace) {
        aimSpeed = pace;
        long now = ant.level().getGameTime();
        if (now < squeezeUntil) {
            return aim;
        }
        double ox = aim.x - ant.getX();
        double oz = aim.z - ant.getZ();
        double toAim = Math.sqrt(ox * ox + oz * oz);
        if (toAim < 1.0E-3) {
            crowdWaitTicks = 0;
            return aim;
        }
        double ux = ox / toAim;
        double uz = oz / toAim;
        double mine = Crowd.radius(ant);
        Crowd.Body ahead = null;
        double aheadAlong = Double.MAX_VALUE;
        double aheadLat = 0.0;
        for (Crowd.Body body : Crowd.of(ant.level()).near(ant, ant.getX(), ant.getY(), ant.getZ(), mine + 0.5 + LOOK, now)) {
            double need = mine + body.radius();
            double bx = body.x() - ant.getX();
            double bz = body.z() - ant.getZ();
            double along = bx * ux + bz * uz;
            double lat = ux * bz - uz * bx;
            if (along <= 0.0 || along >= Math.min(need + LOOK, toAim + need) || Math.abs(lat) >= need) {
                continue;
            }
            if (along < aheadAlong) {
                ahead = body;
                aheadAlong = along;
                aheadLat = lat;
            }
        }
        if (ahead == null) {
            crowdWaitTicks = 0;
            return aim;
        }
        double need = mine + ahead.radius();
        double goesMyWay = ahead.vx() * ux + ahead.vz() * uz;
        if (!ahead.standing() && goesMyWay > 0.01 && crowdWaitTicks < FOLLOW_WAIT) {
            // Following: keep a little gap behind it, slowing as it nears, holding while too close.
            double gap = aheadAlong - need;
            if (gap <= 0.0) {
                crowdWaitTicks++;
                aimSpeed = 0.0;
                crowdHeldAt = now;
                return ant.position();
            }
            aimSpeed = pace * Mth.clamp(gap / LOOK, 0.25, 1.0);
            return aim;
        }
        // Stepping round it, on the side away from it (ties: by id), inside the cells it walks through.
        double side = Math.abs(aheadLat) > 1.0E-3 ? -Math.signum(aheadLat) : (ant.getId() < ahead.ant().getId() ? 1.0 : -1.0);
        double wantLat = aheadLat + side * need * 1.05;
        double px = ant.getX() + ux * aheadAlong + (-uz) * wantLat;
        double pz = ant.getZ() + uz * aheadAlong + ux * wantLat;
        double half = ant.getBbWidth() / 2.0 + 0.01;
        BlockPos cell = BlockPos.containing(aim.x, aim.y + 0.01, aim.z);
        if (Math.abs(ux) > Math.abs(uz)) {
            pz = Mth.clamp(pz, cell.getZ() + half, cell.getZ() + 1.0 - half); // walking along x: sideways is z
        } else {
            px = Mth.clamp(px, cell.getX() + half, cell.getX() + 1.0 - half);
        }
        Vec3 round = new Vec3(px, ant.getY(), pz);
        double roomLat = Math.abs(ux * (pz - ahead.z()) - uz * (px - ahead.x()));
        if (roomLat >= 0.75 * need && fits(round)) {
            crowdWaitTicks = 0;
            return round;
        }
        // (already inside it, pushed in by a shuffle or a turn: no waiting in there, it squeezes on past at once)
        boolean inside = Math.hypot(ahead.x() - ant.getX(), ahead.z() - ant.getZ()) < Crowd.INSIDE * need;
        if (inside || ++crowdWaitTicks > (ahead.standing() ? PASS_WAIT : FOLLOW_WAIT + PASS_WAIT)) {
            crowdWaitTicks = 0;
            squeezeUntil = now + SQUEEZE_TICKS;
            return aim;
        }
        aimSpeed = 0.0;
        crowdHeldAt = now;
        return ant.position();
    }

    /**
     * A free spot to stop on in a floor goal cell: its lane's point first, then the middle and rings round it (the
     * nearest to it first), within {@link #SPOT_REACH} of the middle, its box fitting and clear of anyone standing there
     * (several to a block: a waiting raid, a crowd at a door). With none clear, the nearest free spot round the cell
     * (within {@link #FREE_REACH_FAR}), else the least crowded one.
     */
    private Vec3 freeSpot(CellMap map, BlockPos cell) {
        Vec3 middle = Vec3.atBottomCenterOf(cell);
        if (map.isSlope(cell)) {
            return middle;
        }
        double mine = Crowd.radius(ant);
        long now = ant.level().getGameTime();
        Crowd crowd = Crowd.of(ant.level());
        List<Vec3> spots = new ArrayList<>(17);
        double off = lane() * laneRoom();
        spots.add(middle.add(-laneZ * off, 0.0, laneX * off));
        List<Vec3> ring = new ArrayList<>(16);
        ring.add(middle);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            ring.add(middle.add(Math.cos(angle) * SPOT_REACH * 0.55, 0.0, Math.sin(angle) * SPOT_REACH * 0.55));
            ring.add(middle.add(Math.cos(angle + Math.PI / 8.0) * SPOT_REACH, 0.0, Math.sin(angle + Math.PI / 8.0) * SPOT_REACH));
        }
        Vec3 here = ant.position();
        ring.sort(Comparator.comparingDouble(p -> p.distanceToSqr(here.x, p.y, here.z)));
        spots.addAll(ring);
        Vec3 best = middle;
        double bestGap = -Double.MAX_VALUE;
        for (Vec3 p : spots) {
            if (!fits(p)) {
                continue;
            }
            double gap = crowd.clearance(ant, p, mine, now);
            if (gap >= 0.0) {
                return p;
            }
            if (gap > bestGap) {
                bestGap = gap;
                best = p;
            }
        }
        // The cell is full: the nearest free spot round it (the crowd spills into the cells round it), else the least crowded.
        Vec3 spill = freeNear(middle, mine, FREE_REACH_FAR, 0.15);
        return spill != null ? spill : best;
    }

    /**
     * Standing on a floor with nothing to walk: if it stands inside the personal space of someone who stood there
     * before it (or who must hold still), it shuffles a little aside, at most {@link #FREE_REACH} (so a mind waiting
     * near its place still finds it there; a crowd overflowing a cell spills into the cells round it, onto ground only). Never while it holds still itself, closed in on something on purpose, or a mind
     * moves it this tick.
     */
    private void keepApart() {
        if (!Crowd.keepsSpace(ant) || closedIn || ant.holdsStill() || !ant.onGround() || ant.getMoveControl().hasWanted()) {
            apartWhy = closedIn ? "closed in" : ant.holdsStill() ? "holds still" : !ant.onGround() ? "off the ground"
                    : ant.getMoveControl().hasWanted() ? "a move wanted" : "no size";
            return;
        }
        double mine = Crowd.radius(ant);
        double pushX = 0.0;
        double pushZ = 0.0;
        boolean pressed = false;
        for (Crowd.Body body : Crowd.of(ant.level()).near(ant, ant.getX(), ant.getY(), ant.getZ(), mine + 0.5, ant.level().getGameTime())) {
            if (!body.standing()) {
                continue;
            }
            boolean givesWay = body.ant().holdsStill() || body.standSince() < standSince
                    || body.standSince() == standSince && ant.getId() > body.ant().getId();
            if (!givesWay) {
                continue;
            }
            double need = (mine + body.radius()) * KEEP_APART;
            double dx = ant.getX() - body.x();
            double dz = ant.getZ() - body.z();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist >= need) {
                continue;
            }
            pressed = true;
            if (dist < 1.0E-3) {
                double angle = (ant.getId() * 0.618034) * Math.PI * 2.0; // right on top: a way of its own
                pushX += Math.cos(angle) * need;
                pushZ += Math.sin(angle) * need;
                continue;
            }
            pushX += dx / dist * (need - dist);
            pushZ += dz / dist * (need - dist);
        }
        if (!pressed) {
            apartWhy = "not pressed";
            return;
        }
        double push = Math.sqrt(pushX * pushX + pushZ * pushZ);
        Vec3 to = null;
        if (push >= 0.02) {
            double scale = Math.min(1.0, SHUFFLE_MOST / push) * 1.2;
            Vec3 want = new Vec3(ant.getX() + pushX * scale, ant.getY(), ant.getZ() + pushZ * scale);
            if (standable(want) && Crowd.of(ant.level()).clearance(ant, want, spaceFor(mine), ant.level().getGameTime()) >= 0.0) {
                to = want;
            }
        }
        if (to == null && ant.tickCount >= freeSearchAfter) {
            freeNotClear = freeNotStandable = freePastWall = 0;
            // Pushed into a wall, off an edge, into others, or squeezed from all sides alike: the nearest free spot round
            // it, near first, then a little farther (a crowd overflowing a cell spills into the cells round it).
            to = freeNear(ant.position(), mine, FREE_REACH, 0.1);
            if (to == null) {
                to = freeNear(ant.position(), mine, FREE_REACH_FAR, 0.15);
            }
            if (to == null) {
                freeSearchAfter = ant.tickCount + FREE_RETRY; // (packed in: it stands, and looks again a little later)
                ColonyFrameworkMod.LOGGER.debug("[crowd] ant {} at {} found no free spot: {} spots taken, {} no ground or no room, {} past a wall",
                        ant.getId(), ant.position(), freeNotClear, freeNotStandable, freePastWall);
            }
        }
        if (to == null || to.distanceToSqr(ant.position()) < 0.02 * 0.02) {
            apartWhy = to == null ? "no free spot" : "free spot here";
            return;
        }
        apartWhy = "shuffled";
        ant.getMoveControl().setWantedPosition(to.x, ant.getY(), to.z, SHUFFLE_PACE);
        shuffledAt = ant.level().getGameTime();
    }

    /**
     * The radius to ask {@link Crowd#clearance} for so that the gap it wants from a body like its own is the
     * {@link #KEEP_APART} share of their radii, as the shuffle's push wants it.
     */
    private static double spaceFor(double mine) {
        return mine * (2.0 * KEEP_APART - 1.0);
    }

    /** Whether it stands inside the body of one standing still ({@link Crowd#inside}'s measure). */
    private boolean insideStander(long now) {
        double mine = Crowd.radius(ant);
        for (Crowd.Body body : Crowd.of(ant.level()).near(ant, ant.getX(), ant.getY(), ant.getZ(), mine + 0.5, now)) {
            if (body.standing() && Math.hypot(body.x() - ant.getX(), body.z() - ant.getZ()) < Crowd.INSIDE * (mine + body.radius())
                    && Math.abs(body.y() - ant.getY()) <= Crowd.SAME_FLOOR) {
                return true;
            }
        }
        return false;
    }

    /** Standing room: its box fits there at its height, with ground under it (never a shuffle off an edge). */
    private boolean standable(Vec3 point) {
        double dx = point.x - ant.getX();
        double dz = point.z - ant.getZ();
        return fits(point) && !ant.level().noCollision(ant, ant.getBoundingBox().move(dx, -0.2, dz));
    }

    /**
     * The nearest spot within {@code reach} of {@code centre} (on a grid {@code grid} apart) where it stands clear of everyone
     * standing, with room and ground; null if none. A crowd overflowing a cell spills into the cells round it.
     */
    @Nullable
    private Vec3 freeNear(Vec3 centre, double mine, double reach, double grid) {
        Crowd crowd = Crowd.of(ant.level());
        long now = ant.level().getGameTime();
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        int steps = (int) Math.round(reach / grid);
        for (int i = -steps; i <= steps; i++) {
            for (int j = -steps; j <= steps; j++) {
                double distance = (i * i + j * j) * grid * grid;
                if (distance >= bestDistance || distance > reach * reach) {
                    continue;
                }
                Vec3 p = new Vec3(centre.x + i * grid, ant.getY(), centre.z + j * grid);
                boolean clear = crowd.clearance(ant, p, spaceFor(mine), now) >= 0.0;
                boolean stand = clear && standable(p);
                boolean through = stand && fits(new Vec3((centre.x + p.x) / 2.0, ant.getY(), (centre.z + p.z) / 2.0)); // (no spot past a wall)
                if (!clear) {
                    freeNotClear++;
                } else if (!stand) {
                    freeNotStandable++;
                } else if (!through) {
                    freePastWall++;
                }
                if (through) {
                    best = p;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** Lets go and drops: steered over the cell it lands in, until it is on the ground there. */
    private boolean fall(CellMap map, Foothold to) {
        surface = Direction.DOWN;
        Vec3 aim = Vec3.atBottomCenterOf(to.cell());
        ant.getMoveControl().setWantedPosition(aim.x, ant.getY(), aim.z, speed);
        return ant.onGround() && inFloorCell(map, to.cell());
    }

    /**
     * Walk up to the foot of the wall, then the body tips up onto it. From a slope (the wall goes on up from its high
     * side) it first walks up the slope to the wall with the normal walking.
     */
    private boolean ontoWall(CellMap map, Foothold from, Foothold to) {
        surface = Direction.DOWN;
        if (leg == 0 && map.isSlope(from.cell())) {
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

    /**
     * Down the wall to the floor, then the body tips back onto it, then it walks straight out from the wall until its
     * rear is off the wall too, before it turns where the route goes (the user's report, 2026-10-18: turning at once at
     * the foot slid the body sideways with the abdomen still raised against the wall; a real ant coming down head first
     * walks on until the whole body is on the floor). How far: its box's width, about its body behind the middle and
     * the bend's settling (the worker's 0.3 blocks, as the films showed its gaster flat after), for every body.
     */
    private boolean offWall(Foothold from) {
        Direction wall = from.surface();
        if (leg == 0) {
            surface = wall;
            if (moveAttached(wallPoint(from, from.cell().getY()))) {
                setYaw(wall.toYRot() - heading);
                surface = Direction.DOWN;
                leg = 1;
            }
            return false;
        }
        surface = Direction.DOWN;
        Direction.Axis axis = wall.getAxis();
        double out = Math.min(ant.getBbWidth(), 1.0 - 2.0 * (half() + EPS));
        double clear = pinned(from.cell(), wall) - wall.getAxisDirection().getStep() * out;
        if (Math.abs(coord(ant.position(), axis) - pinned(from.cell(), wall)) >= out - CLEAR_OF_WALL_SLACK) {
            return true;
        }
        // at the pace it came down (an exact motion, as on the wall): walking from a standstill made it creep out
        // and then dash (docs/BUGS.md 53)
        moveAttached(with(ant.position(), axis, clear));
        return false;
    }

    /** How near the walk out from the foot of a wall counts as there (blocks; well under one tick's walk). */
    private static final double CLEAR_OF_WALL_SLACK = 0.02;

    /** Up, down or sideways along a wall into the next cell's part of it. */
    private boolean climb(Foothold to, boolean sideways) {
        surface = to.surface();
        Vec3 target = wallPoint(to, clampY(to.cell(), ant.getY()));
        if (!sideways) {
            target = new Vec3(target.x, to.cell().getY(), target.z);
        } else {
            target = with(target, sideAxis(to.surface()), centerOf(to.cell(), sideAxis(to.surface())));
        }
        return moveAttached(target);
    }

    /** Along this wall into the corner, where the body turns onto the next wall. */
    private boolean turnCorner(Foothold from, Foothold to) {
        BlockPos c = from.cell();
        surface = from.surface();
        Vec3 corner = with(wallPoint(from, clampY(c, ant.getY())), to.surface().getAxis(), pinned(c, to.surface()));
        if (moveAttached(corner)) {
            surface = to.surface();
            return true;
        }
        return false;
    }

    /** The wall ends sideways: along it until the body is past the block's edge, then round the edge onto its side face. */
    private boolean roundEdge(Foothold from, Foothold to) {
        BlockPos c = from.cell();
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

    /**
     * Up the wall until the feet reach its top (above a slope on it as high as its surface rises under where the body
     * will be), then over the edge onto the block. Out of a doorway by its mouth instead, if the wall is a doorway's.
     */
    private boolean overTop(CellMap map, Foothold from, Foothold to) {
        BlockPos c = from.cell();
        Doorway door = doorwayAt(c);
        if (door != null) {
            exact = true;
            return comeOutOf(door, from);
        }
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

    /**
     * To the edge of the floor and over it, then head first down the side of the block. Into a doorway by its mouth
     * instead, if the block it goes down is a doorway.
     */
    private boolean overEdge(CellMap map, Foothold from, Route.Step step) {
        Foothold to = step.to();
        BlockPos c = from.cell();
        Doorway door = doorwayAt(to.cell());
        if (door != null) {
            exact = true;
            return goInto(door, step);
        }
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

    /** Up the wall into the corner, where the body tips on over onto the ceiling. */
    private boolean ontoCeiling(Foothold from) {
        surface = from.surface();
        if (moveAttached(wallPoint(from, ceilingY(from.cell())))) {
            surface = Direction.UP;
            ceilingRef = from.surface();
            heading = 0.0F;
            return true;
        }
        return false;
    }

    /** Along the ceiling to the wall, then the body tips down onto it, head first. */
    private boolean offCeiling(Foothold from, Foothold to) {
        surface = Direction.UP;
        if (moveAttached(with(ceilingPoint(from), to.surface().getAxis(), pinned(from.cell(), to.surface())))) {
            surface = to.surface();
            heading = 180.0F;
            return true;
        }
        return false;
    }

    /** Upside down along the ceiling into the next cell's. */
    private boolean crawlCeiling(Foothold from, Foothold to) {
        surface = Direction.UP;
        Direction.Axis along = to.cell().getX() != from.cell().getX() ? Direction.Axis.X : Direction.Axis.Z;
        return moveAttached(with(ceilingPoint(to), along, centerOf(to.cell(), along)));
    }

    /** Down the wall until the body is below the block's bottom edge, then in under the block, upside down. */
    private boolean underEdge(Foothold from, Foothold to) {
        Direction w = from.surface();
        if (leg == 0) {
            surface = w;
            if (moveAttached(wallPoint(from, from.cell().getY() - ant.getBbHeight() - EPS))) {
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

    /** Along the ceiling until the body is past its edge, then round the edge and up the block's side face. */
    private boolean outFromUnder(Foothold from, Foothold to) {
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

    // --- Channels (termite tunnels, see Channel): the body lies in the channel's middle, its feet on its floor ---

    /**
     * Where the body is in the channel of a cell: lying on its floor, in the middle; holding one of its side walls
     * (going up or down it), pressed against that wall, feet at the channel's floor height. (Before real sizes the body
     * filled the channel, so its middle touched every wall; a 0.15-block worker in the middle of a 6-pixel channel hung in
     * the air, "floating" up and down a mud cast, docs/BUGS.md 129.)
     */
    private Vec3 channelPoint(BlockPos cell) {
        return channelPoint(cell, cell.getY() + Channel.FLOOR + EPS);
    }

    /** {@link #channelPoint(BlockPos)} with its feet at height {@code y}. */
    private Vec3 channelPoint(BlockPos cell, double y) {
        Vec3 middle = new Vec3(cell.getX() + 0.5, y, cell.getZ() + 0.5);
        if (!surface.getAxis().isHorizontal()) {
            return middle;
        }
        double out = channelWallOffset();
        return middle.add(surface.getStepX() * out, 0.0, surface.getStepZ() * out);
    }

    /** How far from a channel's middle line the body's middle is when it holds a side wall of the channel (0 if it fills it). */
    private double channelWallOffset() {
        return Math.max(0.0, Channel.HALF_WIDTH - half() - CHANNEL_GAP);
    }

    /** The wall of a channel an insect going up or down it holds: the one it is on already, else {@code fresh}. */
    private Direction channelWall(Direction fresh) {
        return surface.getAxis().isHorizontal() ? surface : fresh;
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
            // Up the wall in front of it, or head first down the wall under the edge it goes over; one already on a
            // wall of the channel keeps to it (it turns round on it at the top to come back down, as a real termite).
            surface = channelWall(d == Direction.UP ? channelFacing : channelFacing.getOpposite());
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
                surface = channelWall(channelFacing);
                channelFacing = surface; // it comes out over the rim of the wall it holds
                if (moveAttached(channelPoint(c, c.getY() + 1 + EPS))) {
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
                surface = channelWall(channelFacing.getOpposite());
                if (moveAttached(channelPoint(c, ceilingY(to.cell())))) {
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

    // --- Chimney mouths and other doorways: their own exact motion (see DoorwayMotion) ---

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
        List<DoorwayMotion.Key> keys = DoorwayMotion.comeOut(door.mouthHeight(), door.mouthRadius(), half(), OVER_THE_TOP, ant.getScale());
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
        List<DoorwayMotion.Key> keys = new ArrayList<>(DoorwayMotion.goIn(door.mouthHeight(), door.mouthRadius(), half(), OVER_THE_TOP, ant.getScale()));
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

    /**
     * Out of a doorway over its top, by its mouth (see {@link DoorwayMotion#comeOut}): from the wall inside the block up
     * the middle of the spire until the head is out, a look round, over the rim and head first down the outside of the
     * spire, and onto the top of the block beside (where the route goes on). The body is hidden inside the block until
     * it comes up the spire.
     */
    private boolean comeOutOf(Doorway door, Foothold from) {
        return walkDoorway(DoorwayMotion.comeOut(door.mouthHeight(), door.mouthRadius(), half(), OVER_THE_TOP, ant.getScale()), from.cell(), from.surface());
    }

    /**
     * Into a doorway over its edge, by its mouth (see {@link DoorwayMotion#goIn}): from the top of the block beside to
     * the foot of the spire, up its outside, over the rim and head first down inside it into the block, then over to the
     * wall the route goes on from (hidden inside).
     */
    private boolean goInto(Doorway door, Route.Step step) {
        Foothold to = step.to();
        BlockPos c = to.cell();
        Direction s = to.surface(); // toward the block the insect comes from: the side it goes in by
        List<DoorwayMotion.Key> keys = DoorwayMotion.goIn(door.mouthHeight(), door.mouthRadius(), half(), OVER_THE_TOP, ant.getScale());
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

    /** The doorway block in this cell if this insect passes through it (a termite in a chimney block), else null. */
    @Nullable
    private Doorway doorwayAt(BlockPos cell) {
        BlockState state = ant.level().getBlockState(cell);
        return state.getBlock() instanceof Doorway door
                && state.getCollisionShape(ant.level(), cell, CollisionContext.of(ant)).isEmpty() ? door : null;
    }

    // --- Where the body is: its foothold, read from the world ---

    /**
     * Where the insect really holds on now, worked out from where its body is (a route must start where the body is,
     * or its first move cuts across to the wrong place): the wall it is on in its cell, else its floor, else the wall
     * of its cell it is closest to. Only between two cells (going over an edge) the foothold the step started from.
     * Null in the air.
     */
    @Nullable
    private Foothold currentFoothold(CellMap map) {
        BlockPos feet = ant.blockPosition();
        double inChannelReach = Math.max(IN_THE_CHANNEL, channelWallOffset() + 2 * CHANNEL_GAP);
        if (map.channelSides(feet) != 0 && Math.abs(ant.getX() - (feet.getX() + 0.5)) < inChannelReach
                && Math.abs(ant.getZ() - (feet.getZ() + 0.5)) < inChannelReach) {
            // In a channel (a termite tunnel): it lies in the channel's middle, or holds one of its side walls. (A body
            // hugging the outside of a narrow hollow spire piece can have its feet in the piece's block, but always
            // outside the channel: at least the channel's half width plus its own half width from the middle line.)
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
        if (nearest == null && at != null && at.isValid(map) && pos().distanceToSqr(Vec3.atCenterOf(at.cell())) < STILL_ON_IT * STILL_ON_IT) {
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

    // --- Moving the body: the exact motion toward a point, and which way the body faces ---

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
                squaring = true;
                return false;
            }
        }
        double distance = delta.length();
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

    /**
     * On a wall the body faces the wall and the head turns the way it moves; on a ceiling the head turns the way it
     * moves (measured from {@link #ceilingRef}); on a floor it faces the way it moves.
     */
    private void faceTheWayItGoes() {
        if (surface == Direction.UP) {
            if (attached && !squaring && motion.horizontalDistanceSqr() > 1.0E-8) {
                Direction left = ceilingRef.getCounterClockWise();
                double sideways = motion.x * left.getStepX() + motion.z * left.getStepZ();
                double away = -(motion.x * ceilingRef.getStepX() + motion.z * ceilingRef.getStepZ());
                if (Math.abs(sideways) > 1.0E-5 || Math.abs(away) > 1.0E-5) {
                    heading = (float) Math.toDegrees(Math.atan2(sideways, away));
                }
            }
            setYaw(ceilingRef.toYRot() + 180.0F + heading);
        } else if (surface.getAxis().isHorizontal()) {
            if (attached && !squaring && motion.lengthSqr() > 1.0E-8) {
                Direction left = surface.getCounterClockWise();
                double sideways = motion.x * left.getStepX() + motion.z * left.getStepZ();
                // only a motion mostly along the wall turns the head: the last bit of the way out over an edge onto
                // the wall (across it) once had a drift of 0.00004 sideways in it, which put the head sideways for a
                // tick and broke the pose over the edge (docs/BUGS.md 57)
                if (sideways * sideways + motion.y * motion.y > 0.25 * motion.lengthSqr()) {
                    heading = (float) Math.toDegrees(Math.atan2(sideways, motion.y));
                }
            }
            // The yaw is the way the head points with the wall laid down flat in front of it: into the wall going up,
            // out from it going down (docs/BUGS.md 53). Off the wall at its foot or over its top the body goes on the
            // way it already faces, so the yaw never swings half round there (the drawn body on a wall is turned by
            // the wall and the heading, not by this yaw).
            setYaw(surface.toYRot() - heading);
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
        return with(point, along, besideOpening(foothold.cell(), wall, along, clampIn(foothold.cell(), along, coord(here, along))));
    }

    /**
     * docs/BUGS.md 135 (real sizes): a wall whose block has a channel opening facing the body (a tunnel mouth in the
     * mound's shaft wall) is held on its solid rim beside the opening, the nearer side, never over the hole with
     * nothing under the legs (a body wider than the opening covers it and stays where it is).
     */
    private double besideOpening(BlockPos cell, Direction wall, Direction.Axis along, double value) {
        BlockPos held = cell.relative(wall);
        BlockState state = ant.level().getBlockState(held);
        if (!(state.getBlock() instanceof Channel channel) || !Channel.has(channel.openSides(ant.level(), held, state), wall.getOpposite())) {
            return value;
        }
        double mid = held.get(along) + 0.5;
        double band = Channel.HALF_WIDTH + half() + EPS;
        if (band + half() > 0.5 || Math.abs(value - mid) >= band) {
            return value; // too big to stand beside it (it covers it), or beside it already
        }
        return clampIn(cell, along, value < mid ? mid - band : mid + band);
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
        double x = clampIn(cell, Direction.Axis.X, ant.getX());
        double z = clampIn(cell, Direction.Axis.Z, ant.getZ());
        BlockPos held = cell.above();
        BlockState state = ant.level().getBlockState(held);
        if (state.getBlock() instanceof Channel channel && Channel.has(channel.openSides(ant.level(), held, state), Direction.DOWN)) {
            // (docs/BUGS.md 144, as 135 on a wall: a ceiling with a channel opening over the body is held on its solid
            // rim beside the opening, the nearer way out of it, never under the hole with nothing over the legs)
            double band = Channel.HALF_WIDTH + half() + EPS;
            double dx = x - (held.getX() + 0.5), dz = z - (held.getZ() + 0.5);
            if (band + half() <= 0.5 && Math.abs(dx) < band && Math.abs(dz) < band) {
                if (Math.abs(dx) >= Math.abs(dz)) {
                    x = clampIn(cell, Direction.Axis.X, held.getX() + 0.5 + (dx < 0 ? -band : band));
                } else {
                    z = clampIn(cell, Direction.Axis.Z, held.getZ() + 0.5 + (dz < 0 ? -band : band));
                }
            }
        }
        return new Vec3(x, ceilingY(cell), z);
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
}
