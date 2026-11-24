package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * The raid's march, out and home (the raid rewrite, step 3: lifted out of {@link RaidColumn} with its behaviour
 * unchanged; the user, 2026-11-23: "the march works"). It owns the way out and the way home along the colony's scent
 * trail and everything a walk along them needs: the stream behind the front ({@link #step}), the raiders coming up
 * out of the nest, the gaps in a broken trail and the casting about at them. It decides nothing about the raid: who
 * is in it, which ant leads and when a walk begins or ends are its owner's, told to it at every step; what it must say
 * or ask goes through its {@link Host}.
 */
final class RaidMarch {
    /** What the march needs from the raid that owns it. */
    interface Host {
        /** One line in the raid's diary. */
        void say(String what);

        /** One raider in words, for the diary. */
        String who(MatabeleEntity ant, Route route);

        /** This raider was still in the nest long after the column left: it stays home, out of the raid. */
        void staysHome(UUID ant);

        /** Whether a former scout walks with the raid (it can lead across a wide gap in the trail from memory). */
        boolean formerScoutIn();
    }

    /**
     * The front (the user, 2026-11-07: "give him like a 5% speed boost, its subtle but it will keep him infront"): the
     * front walks this much faster than the column's own pace, and never stops. (A slower front till the column had
     * closed up behind it, 0.9, was tried on 2026-11-08 and taken out the same day: the crowd always leaves a few raiders
     * two to four rows back, so "closed up" never came off a low mound and came on the top of a tall one, before anyone
     * had climbed down; what had stretched the column was the slow climb, {@link #MARCH_CLIMB}.)
     */
    static final double FRONT_PACE = 1.05;
    /**
     * A raider level with the front or past it walks this slow, so it drops back behind it without stopping (a raider
     * that got past the front at the front's own pace stayed in front of it all the march). My pick.
     */
    static final double PASSED_PACE = 0.4;
    /**
     * A raider far behind its place (one just out of the nest) catches up at up to this pace (a run; was 1.35,
     * the user's plan of 2026-11-08: at 1.35 a raider gained only 0.7 blocks a second on the front, so the tail of 47
     * raiders took some 50 blocks to close up; my pick).
     */
    static final double CATCH_UP_PACE = 1.6;
    /** How far ahead (blocks along the trail) of itself the front walks to at a time. */
    static final double LEAD_STEP = 6.0;
    /** A raider counts as at the gathering place, or out at the entrance, this near it (blocks, flat). */
    static final double NEAR = 4.0;
    /** Raid flow: a walking raider aims this far (blocks along the trail) past its place, so it walks on, never stopping. */
    static final double CARROT = 2.5;
    /**
     * Raid flow: its pace goes up (or down) this much for each block it is behind (or ahead of) its place (was 0.12, the
     * user's plan of 2026-11-08: at 0.12 the last two blocks closed slowly, a few seconds each; my pick).
     */
    static final double PACE_PER_BLOCK = 0.3;
    /** The simple march: a raider ahead of its place slows to no less than this (it lets the row come up; nobody crawls). */
    static final double LEAST_MARCH_PACE = 0.85;
    /** R4: while the front casts about for a broken trail the column closes up to this share of its spacing (nose to tail; my pick). */
    static final double CLOSE_UP = 0.75;
    /**
     * The raid queue fix (2026-11-06): a marching raider walks to a place at most this far (blocks along the trail) ahead
     * of itself at a time, then the next (a far place across a dead mound's hump found no way, and the ant was sent home
     * after 15 s; my pick, about 8 major lengths).
     */
    static final double WALK_AHEAD = 4.0;
    /** The raid queue fix: an ant lower than the entrance this near it (flat blocks) is in the chimney's shaft. */
    static final double CHIMNEY_REACH = 1.5;
    /** The raid queue fix: how far on (blocks walked) a place that snapped back behind its ant looks for one ahead of it. */
    static final double AHEAD_LOOK = 16.0;
    /** The raid queue fix: an ant is on the trail this near its height there (blocks up or down). */
    static final double ON_TRAIL_HEIGHT = 1.5;
    /** R4: a broken trail point this far ahead of the front (blocks along) is a gap it sees; it stops this far short of it. */
    static final double GAP_LOOK = 4.0;
    static final double GAP_STOP = 1.5;
    /** R4: a trail point counts as on the route this near it (blocks, flat). */
    static final double ON_ROUTE = 2.0;
    /** A raider and its place both this near the trail (blocks) hop along it, not straight ({@link #hopAlong}; my pick). */
    static final double HOP_OFF = 4.0;
    /** R4: how many ants at the front cast about at a gap (the front and the next ones out). */
    static final int CASTERS = 3;
    /** R4: the casters turn to a new loop every this many ticks. */
    static final long CAST_BEAT = 30;
    /** The march and the walk home are told in the log every this long (ticks). */
    static final long WAIT_SAY_EVERY = 200;

    private final Colony colony;
    private final SavannaNumbers.ColumnNumbers numbers;
    private final SavannaNumbers.TrailNumbers trailNumbers;
    private final ScentTrails.Trail trail;
    private final Host host;
    /** The march out, from the entrance to the gathering place, and the walk home, the other way. */
    private final Route out;
    private final Route back;
    /** Told at every step by the owner: the ant in front, when this walk began, and where the places are written. */
    private UUID leader;
    private long stageSince;
    private Map<UUID, RaidColumn.Order> orders = new HashMap<>();
    /** Kept for one update only: each raider's place along each route (asked many times an update). */
    private final Map<MatabeleEntity, Double> alongOutNow = new IdentityHashMap<>();
    private final Map<MatabeleEntity, Double> alongBackNow = new IdentityHashMap<>();
    /** The gap look-up (kept till the trail's points change). */
    @Nullable
    private Route gapRoute;
    private int gapShape = -1;
    private double gapTurnedAt = Double.NaN;
    private double[] gapAlong = new double[0];
    private int[] gapIndex = new int[0];
    /** Where the front walks to now, and where it is (blocks along the route). */
    private double head;
    private double frontAt;
    /** Each marching raider's distance behind its place at the last update (blocks walked; tests measure the march by it). */
    private final Map<UUID, Double> behindPlace = new HashMap<>();
    /** When the march was last told in the diary. */
    private long marchSaidAt = Long.MIN_VALUE / 2;
    /**
     * The stream: the column's order at the last update, the front first, then the line by how far each has walked
     * along the trail (the one farthest along first); a fallen front's place goes to the next in it.
     */
    private final List<UUID> lineOrder = new ArrayList<>();
    /** Tests: the route {@link #hillEnd} was worked out for, and where its first climbs and drops end (along). */
    @Nullable
    private Route hillRoute;
    private double hillEndAt;

    /** R4: a gap in the trail: where it starts and where the trail goes on (blocks along the route walked). */
    private record Gap(double near, double far) {
    }

    /** R4: since when the front casts about at a gap (-1 not), that gap, the casters, the time cast this walk in all. */
    private long castingSince = -1;
    @Nullable
    private Gap castGap;
    private final Set<UUID> casters = new HashSet<>();
    private long castTotal;
    /** R4: gaps crossed, whether the raid turned home having lost the trail, and the march's ask to turn home now. */
    private int gapsCrossed;
    private boolean lostTrail;
    private boolean turnBack;
    /** R4: how far out (along the way out) the gap the raid turned back at began, or -1: the way home leaves it behind. */
    private double turnedAt = -1.0;

    /**
     * The two ways along the colony's scent trail to a find at {@code spot}: out from the first cell to stand in beside
     * the entrance to the gathering place, the trail's last point at least {@code stop_short} from the spot, and home,
     * the same line the other way. Always at least one point (the start itself).
     */
    RaidMarch(ServerLevel level, Colony colony, SavannaNumbers.ColumnNumbers numbers, SavannaNumbers.TrailNumbers trailNumbers,
              ScentTrails.Trail scent, BlockPos spot, Host host) {
        this.colony = colony;
        this.numbers = numbers;
        this.trailNumbers = trailNumbers;
        this.trail = scent;
        this.host = host;
        // The scent line starts at the entrance itself: points right over it are the shaft (a dead mound's chimney is an
        // open hole, so the ground's height there is the room at its foot), and a route through them dives into the hump
        // (BUGS 85: the column home stuck at the hump's foot).
        // Raid rework R3: the line starts at the first cell to stand in beside the entrance, toward the find, never on the
        // entrance itself (a dead mound's open chimney: a place there is in the air over the hole, BUGS 87).
        // R4: the line is the scent trail on the ground (the scout's line, or a column's trail walked before).
        List<BlockPos> points = new ArrayList<>(scent.points());
        BlockPos start = lineStart(level, colony.getCenter(), points.isEmpty() ? spot : points.get(points.size() - 1), points);
        double startOut = flat(start, colony.getCenter());
        points.removeIf(p -> flat(p, colony.getCenter()) < Math.max(1.5, startOut + 0.5));
        points.add(0, start);
        // The gathering place: the last point of the scent line at least stop_short from the find.
        int gather = 0;
        for (int i = 0; i < points.size(); i++) {
            if (flat(points.get(i), spot) >= numbers.stopShort()) {
                gather = i;
            }
        }
        List<BlockPos> outPoints = new ArrayList<>(points.subList(0, Math.max(1, gather) + 1));
        this.out = new Route(outPoints);
        List<BlockPos> backPoints = new ArrayList<>(outPoints);
        Collections.reverse(backPoints);
        this.back = new Route(backPoints);
    }

    Route out() {
        return out;
    }

    Route back() {
        return back;
    }

    /** A new update of the raid begins: what was kept for one update is forgotten. */
    void newUpdate() {
        alongOutNow.clear();
        alongBackNow.clear();
    }

    /** A new walk begins (or the raid moved on to another stage): the stream and the casting start afresh. */
    void newWalk() {
        lineOrder.clear();
        castingSince = -1;
        castGap = null;
        casters.clear();
        castTotal = 0;
        head = 0.0;
        frontAt = 0.0;
        behindPlace.clear();
    }

    /** The walk goes on from here: the front already this far along its route (a raid shown partway). */
    void startAt(double along) {
        frontAt = along;
        head = along;
    }

    /** Where the front was at the last step (blocks along the route walked then). */
    double frontAt() {
        return frontAt;
    }

    /** The stream's order at the last step, the front first. */
    List<UUID> lineOrder() {
        return lineOrder;
    }

    /** R4: whether this raider casts about at a gap in the trail now. */
    boolean isCasting(UUID ant) {
        return castingSince >= 0 && casters.contains(ant);
    }

    /** R4: the width of the gap the front casts about at now (blocks), or NaN when it is not casting. */
    double castingGapWidth() {
        return castingSince >= 0 && castGap != null ? castGap.far() - castGap.near() : Double.NaN;
    }

    /** R4: the time spent casting about at gaps in this walk so far (ticks): it does not count against the walk's time. */
    long castTime(long now) {
        return castTotal + (castingSince >= 0 ? now - castingSince : 0);
    }

    /** The raid's clocks waited this long (ticks): so does the casting's. */
    void holdClocks(long by) {
        if (castingSince >= 0) {
            castingSince += by;
        }
    }

    int gapsCrossed() {
        return gapsCrossed;
    }

    boolean lostTrail() {
        return lostTrail;
    }

    /** Whether the last step asked to turn the raid home (the trail lost at a wide gap); the ask is taken. */
    boolean takeTurnBack() {
        boolean asked = turnBack;
        turnBack = false;
        return asked;
    }

    /** Tests: how far (blocks walked) this marching raider was behind its place at the last step (NaN if it has none). */
    double behindPlace(UUID id) {
        return behindPlace.getOrDefault(id, Double.NaN);
    }

    /**
     * Raid rework R3: where the line starts: a cell to stand in 1 to 4 blocks out from the entrance, toward the trail's
     * first point past the entrance (or the find), turning a little either way if that is no ground; the entrance
     * itself only if none is found.
     */
    private static BlockPos lineStart(ServerLevel level, BlockPos entrance, BlockPos far, List<BlockPos> trail) {
        BlockPos toward = trail.stream().filter(p -> flat(p, entrance) >= 2.0).findFirst().orElse(far);
        double angle = Math.atan2(toward.getZ() - entrance.getZ(), toward.getX() - entrance.getX());
        for (double r = 1.5; r <= 4.0; r += 0.5) {
            for (double turn : new double[] {0.0, 0.4, -0.4, 0.8, -0.8, 1.2, -1.2}) {
                BlockPos cell = standAt(level, entrance.getX() + 0.5 + Math.cos(angle + turn) * r, entrance.getZ() + 0.5 + Math.sin(angle + turn) * r,
                        entrance.getY());
                if (cell != null && flat(cell, entrance) >= 1.0 && Math.abs(cell.getY() - entrance.getY()) <= 2) {
                    return cell;
                }
            }
        }
        return entrance;
    }

    /**
     * On the march, out or home: the stream (2026-11-08, the user's OK; it replaced the ribbon, formation B with exit 2,
     * whose fixed rows and lanes landed on one-block steps and holes on real savanna and piled the raiders up there).
     * Real Megaponera columns are a stream 1 to 3 ants wide along the scout's trail, bunching and spreading with the ground
     * (Longhurst and Howse 1979, Frank et al. 2017). The front walks the trail ahead of itself and never stops, a little
     * faster than the column ({@link #FRONT_PACE}), so it stays in front. On the way out the scout comes out of the nest
     * first: it calls at the foot of the nest's shaft, and till it is out the raiders already outside wait for it by the
     * entrance and the ones in the nest climb up behind it ({@link #comeOut}); down a hump every marcher climbs at about its
     * walking pace ({@link #MARCH_CLIMB}). Every other raider walks the trail itself, the line the scout walked (known to be
     * walkable), its place {@code spacing} behind the one ahead of it in the line, the line sorted every update by how far
     * each has walked along the trail, so each follows the ant really ahead of it. Side by side is left to the legs: each
     * insect's own lane and personal space spread the stream where there is room and thin it to single file where there is
     * not. An ant behind its place hurries, one ahead of it walks on slower: nobody is ever told to stand while the column
     * marches. The one halt kept: a gap in the scent trail (a player broke it), where the front casts about and the rest
     * close up behind it. At the route's end true (the next stage).
     */
    boolean step(ServerLevel level, List<MatabeleEntity> ants, Route route, UUID leader, long stageSince,
                 Map<UUID, RaidColumn.Order> orders, long now) {
        this.leader = leader;
        this.stageSince = stageSince;
        this.orders = orders;
        MatabeleEntity front = ants.stream().filter(ant -> ant.getUUID().equals(leader)).findFirst().orElse(ants.get(0));
        frontAt = alongOf(route, front);
        if (frontAt >= route.length() - 1.5) {
            return true; // the front is at the route's end
        }
        List<MatabeleEntity> line = new ArrayList<>();
        List<MatabeleEntity> notOut = new ArrayList<>();
        for (MatabeleEntity ant : ants) {
            if (ant != front) {
                (isOut(level, ant) ? line : notOut).add(ant);
            }
        }
        // R4: a gap in the scent trail just ahead: the front stops at it and the ants ahead cast about, until the trail
        // is picked up past it (or a former scout leads on, or the raid turns home).
        boolean casting = gapAt(level, route, front, line, now);
        if (turnBack) {
            return false;
        }
        double frontW = route.walkedOf(front.getX(), front.getY(), front.getZ());
        Map<UUID, Double> at = walkedAlong(route, line);
        line.sort(Comparator.comparingDouble(ant -> -at.get(ant.getUUID())));
        lineOrder.clear();
        lineOrder.add(front.getUUID());
        line.forEach(ant -> lineOrder.add(ant.getUUID()));
        double spacing = casting ? numbers.spacing() * CLOSE_UP : numbers.spacing();
        double frontPlace = casting ? Math.min(frontW, route.walked(castGap.near() - GAP_STOP)) : frontW;
        boolean frontOut = route != out || isOut(level, front);
        BlockPos ahead = route.point(Math.min(route.length(), frontAt + 4.0));
        if (casting) {
            orders.put(front.getUUID(), new RaidColumn.Order(castSpot(level, route, 0, now), 1.0, route.point(castGap.far())));
        } else {
            if (frontAt >= head - LEAD_STEP / 2) {
                head = Math.min(route.length(), frontAt + LEAD_STEP);
            }
            orders.put(front.getUUID(), RaidColumn.Order.marching(route.place(level, head, 0.0), FRONT_PACE, ahead));
        }
        BlockPos lineStart = frontOut ? null : route.place(level, 0.0, 0.0);
        for (int k = 0; k < line.size(); k++) {
            MatabeleEntity ant = line.get(k);
            // (the scout still in the nest: a raider already outside waits for it at the trail's start by the entrance,
            // facing the trail; walking on ahead of it, it could stand out along the trail ahead of the scout)
            orders.put(ant.getUUID(), frontOut ? follow(level, route, ant, k, at.get(ant.getUUID()), frontW, frontPlace, spacing, casting, now)
                    : new RaidColumn.Order(lineStart, 1.0, ahead));
        }
        comeOut(level, route, front, notOut, ahead, now);
        sayMarch(route, front, line, notOut, now);
        return false;
    }

    /**
     * How far each raider out has walked along the trail, climbs and drops counted, not the flat distance (down a steep
     * hump the flat distance says the front has hardly moved); one not yet on the trail, its flat distance (0 or less).
     */
    private Map<UUID, Double> walkedAlong(Route route, List<MatabeleEntity> line) {
        Map<UUID, Double> at = new HashMap<>();
        for (MatabeleEntity ant : line) {
            double flatAlong = alongOf(route, ant);
            at.put(ant.getUUID(), flatAlong <= 0.0 ? flatAlong : route.walkedOf(ant.getX(), ant.getY(), ant.getZ()));
        }
        return at;
    }

    /**
     * One raider's order in the stream, {@code k}-th behind the front, {@code own} blocks walked along the trail: its place
     * is {@code (k + 1) * spacing} behind the front's. It walks toward a point on the trail a little past its place, at the
     * front's pace when at its place, a little faster for each block it is behind it (up to {@link #CATCH_UP_PACE}), a
     * little slower when ahead of it (never under {@link #LEAST_MARCH_PACE}: nobody crawls). One level with the front or
     * past it drops back behind it at {@link #PASSED_PACE}. Behind a broken trail it closes up to its place and stands
     * there (never walking back); one of the casters casts about.
     */
    private RaidColumn.Order follow(ServerLevel level, Route route, MatabeleEntity ant, int k, double own, double frontW, double frontPlace,
                         double spacing, boolean casting, long now) {
        UUID id = ant.getUUID();
        if (casting && casters.contains(id)) {
            return new RaidColumn.Order(castSpot(level, route, 1 + k, now), 1.0, route.point(castGap.far()));
        }
        double place = frontPlace - (k + 1) * spacing;
        double placeAlong = Math.max(0.0, route.alongAtWalked(Math.max(0.0, place)));
        BlockPos look = route.point(Math.min(route.length(), placeAlong + 3.0));
        behindPlace.put(id, place - own);
        double pace = Mth.clamp(FRONT_PACE + PACE_PER_BLOCK * (place - own), LEAST_MARCH_PACE, CATCH_UP_PACE);
        if (casting) {
            double target = Math.min(place, own + WALK_AHEAD);
            return target < own + 0.3 ? new RaidColumn.Order(null, 1.0, look) : new RaidColumn.Order(placeAhead(level, route, target, own), pace, look);
        }
        if (own > frontW - spacing * 0.5) {
            pace = PASSED_PACE; // (level with the front or past it: it drops back behind it, still walking)
        }
        double target = Math.min(frontW - spacing * 0.5, Math.max(place + CARROT, own + 1.0));
        target = Mth.clamp(Math.max(own + 0.5, Math.min(target, own + WALK_AHEAD)), 0.0, route.walkedLength());
        return RaidColumn.Order.marching(placeAhead(level, route, target, own), pace, look);
    }

    /**
     * The raiders not yet out of the nest come up to the trail's start: behind the scout while it is still in, at a walk,
     * a hair under its {@link #FRONT_PACE}, then hurrying. It called at the foot of the nest's shaft, so they climb it
     * behind it; insects do not block one another, so it is their pace that keeps them there (hurrying, 1.6, they climbed
     * straight through it, 2026-11-08; 0.85 only spread them out). One still in the nest {@code formTicks} after the
     * column left stays home.
     */
    private void comeOut(ServerLevel level, Route route, MatabeleEntity front, List<MatabeleEntity> notOut, BlockPos ahead, long now) {
        BlockPos lineStart = route.place(level, 0.0, 0.0);
        boolean frontOut = isOut(level, front);
        for (MatabeleEntity ant : notOut) {
            if (route == out && now - stageSince > numbers.formTicks() && colony.nest().isInside(level, ant.blockPosition())) {
                host.say(RaidGround.shortId(ant.getUUID()) + " stays home, still in the nest " + (now - stageSince) / 20 + " s after the column left: " + host.who(ant, out));
                host.staysHome(ant.getUUID());
                continue;
            }
            orders.put(ant.getUUID(), new RaidColumn.Order(lineStart, frontOut ? CATCH_UP_PACE : 1.0, ahead));
        }
    }

    /**
     * Tests: where the route's first climbs and drops end (along; 0 for a level start), a dead mound's hump: the last
     * change of the ground's height before 3 level blocks, looked for in its first 24 blocks.
     */
    double hillEnd(ServerLevel level, Route route) {
        if (hillRoute != route) {
            hillRoute = route;
            double end = 0.0;
            int[] before = laneHeights(level, route, 0.0);
            for (double t = 0.5; t <= Math.min(route.length(), 24.0); t += 0.5) {
                int[] here = laneHeights(level, route, t);
                if (!Arrays.equals(here, before)) {
                    end = t;
                } else if (t - end > 3.0) {
                    break; // (3 blocks level: off the hump; rolling ground farther on is not the hump)
                }
                before = here;
            }
            hillEndAt = end;
        }
        return hillEndAt;
    }

    /**
     * The ground's height in the three whole-block lanes at this place along a route (the ground, not the trail line's
     * height: on a low worn hump the line's height ran out 2 to 3 blocks before its last step, the lanes merged into one
     * block on the tread above it and the raiders queued to step down it one at a time).
     */
    private static int[] laneHeights(ServerLevel level, Route route, double along) {
        return new int[] {route.place(level, along, -1.0).getY(), route.place(level, along, 0.0).getY(),
                route.place(level, along, 1.0).getY()};
    }

    /** The diary: every {@link #WAIT_SAY_EVERY} ticks of the march, where the front is, how many are out and in the stream. */
    private void sayMarch(Route route, MatabeleEntity front, List<MatabeleEntity> line, List<MatabeleEntity> notOut, long now) {
        if (now - marchSaidAt < WAIT_SAY_EVERY) {
            return;
        }
        marchSaidAt = now;
        MatabeleEntity hindmost = line.stream().min(Comparator.comparingDouble(ant -> alongOf(route, ant))).orElse(null);
        host.say("marching " + (now - stageSince) / 20 + " s: the front " + host.who(front, route) + "; " + line.size() + " in the stream, "
                + notOut.size() + " still coming out; the hindmost " + (hindmost == null ? "-" : host.who(hindmost, route)));
    }

    /**
     * R4: the first gap in the scent trail ahead of the front on this route: its first broken point at least a block
     * behind the front and at most {@link #GAP_LOOK} ahead of it, and the first whole point past the broken run (or the
     * route's end); null if none. Trail points off the route are not counted.
     */
    @Nullable
    private Gap gapAhead(Route route, double frontAt) {
        onRoute(route);
        Double near = null;
        for (int k = 0; k < gapAlong.length; k++) {
            double along = gapAlong[k];
            boolean broken = trail.isBroken(gapIndex[k]); // (read now: only the points' places are kept)
            if (near == null) {
                if (broken && along >= frontAt - 1.0) {
                    if (along > frontAt + GAP_LOOK) {
                        return null;
                    }
                    near = along;
                }
            } else if (!broken && along > near) {
                return new Gap(near, along);
            }
        }
        return near == null ? null : new Gap(near, route.length());
    }

    /**
     * The raid code review, finding 9: the trail's points on this route (within {@link #ON_ROUTE} of it), by how far
     * along it, worked out again only when the route, the trail's points or the turned-back place change (it was every
     * update, every point).
     */
    private void onRoute(Route route) {
        if (route == gapRoute && trail.shape() == gapShape && Double.compare(turnedAt, gapTurnedAt) == 0) {
            return;
        }
        gapRoute = route;
        gapShape = trail.shape();
        gapTurnedAt = turnedAt;
        List<double[]> on = new ArrayList<>();
        trail.forEachPoint((index, at, broken) -> {
            double x = at.getX() + 0.5, z = at.getZ() + 0.5;
            double[] near = route.nearest(x, z);
            double along = near[0];
            // (on the way home after turning back, the gap turned back at lies behind: the casters' loops may have
            // carried the front a little into it, so it is skipped by where it was, not by where the front is)
            boolean turnedGap = route == back && turnedAt >= 0.0 && along <= route.length() - turnedAt + 0.5;
            if (near[1] <= ON_ROUTE && !turnedGap) {
                on.add(new double[] {along, index});
            }
        });
        on.sort(Comparator.comparingDouble(p -> p[0]));
        gapAlong = new double[on.size()];
        gapIndex = new int[on.size()];
        for (int k = 0; k < on.size(); k++) {
            gapAlong[k] = on.get(k)[0];
            gapIndex[k] = (int) on.get(k)[1];
        }
    }

    /** This raider's place along this route, worked out once an update (finding 9). */
    double alongOf(Route route, MatabeleEntity ant) {
        Map<MatabeleEntity, Double> now = route == out ? alongOutNow : route == back ? alongBackNow : null;
        if (now == null) {
            return route.along(ant.getX(), ant.getZ());
        }
        return now.computeIfAbsent(ant, key -> route.along(key.getX(), key.getZ()));
    }

    /**
     * R4: the front at a gap in the trail. While the gap is not crossed the front and the next ants out cast about round
     * it (true: casting). The far side is picked up once they have cast {@code cast_ticks} and {@code cast_per_block} a
     * block of the gap, if it is within {@code cast_reach}; a wider gap after {@code cast_give_ticks} is crossed behind a
     * former scout (it leads on from memory), else on the march the raid turns home ({@link #turnBack}), on the way home
     * they go on anyway. Crossing, the ants lay new scent over the gap ({@link ScentTrails.Trail#mend}). False if no
     * gap is ahead now or it was just crossed.
     */
    private boolean gapAt(ServerLevel level, Route route, MatabeleEntity front, List<MatabeleEntity> line, long now) {
        Gap gap = castingSince >= 0 ? castGap : gapAhead(route, frontAt);
        if (gap == null || frontAt < gap.near() - GAP_STOP - 0.5) {
            return false;
        }
        double width = gap.far() - gap.near();
        if (castingSince < 0) {
            castingSince = now;
            castGap = gap;
            casters.clear();
            casters.add(front.getUUID());
            line.stream().sorted(Comparator.comparingDouble(ant -> -alongOf(route, ant)))
                    .limit(CASTERS - 1).forEach(ant -> casters.add(ant.getUUID()));
            host.say("the scent trail is broken ahead, a gap of " + Math.round(width * 10) / 10.0 + " blocks at " + Math.round(gap.near() * 10) / 10.0
                    + " along: the front stops and casts about");
        }
        long cast = now - castingSince;
        boolean pickedUp = width <= trailNumbers.castReach() && cast >= trailNumbers.castTicks() + Math.round(width * trailNumbers.castPerBlock());
        boolean givenUp = !pickedUp && cast >= trailNumbers.castGiveTicks();
        boolean scoutLeads = givenUp && host.formerScoutIn();
        if (!pickedUp && !givenUp) {
            return true;
        }
        castTotal += cast;
        castingSince = -1;
        castGap = null;
        casters.clear();
        if (givenUp && !scoutLeads && route == out) {
            lostTrail = true;
            turnBack = true;
            turnedAt = gap.near();
            host.say("the trail is lost at a gap of " + Math.round(width * 10) / 10.0 + " blocks after casting " + cast / 20
                    + " s, and no former scout walks with them: the raid turns home");
            return false;
        }
        int laid = trail.mend(level, p -> {
            double[] near = route.nearest(p.getX() + 0.5, p.getZ() + 0.5);
            return near[1] <= ON_ROUTE && near[0] >= gap.near() - 1.0 && near[0] <= gap.far() + 1.0;
        });
        gapsCrossed++;
        host.say((pickedUp ? "the trail is picked up past the gap after casting " : givenUp && route == back ? "home past the gap anyway after casting "
                : "a former scout leads on across the gap after casting ") + cast / 20 + " s; new scent laid over " + laid + " point(s)");
        head = Math.min(route.length(), gap.far() + LEAD_STEP);
        return false;
    }

    /**
     * R4: where caster {@code c} goes now: loops out from the gap's near end into it and to either side, a new loop every
     * {@link #CAST_BEAT} ticks (real ants that lose a trail search in widening loops where it ended; my pick).
     */
    private BlockPos castSpot(ServerLevel level, Route route, int c, long now) {
        Gap gap = castGap;
        double[] base = route.at(Math.max(0.0, gap.near() - 0.5));
        double[] d = route.direction(gap.near());
        double heading = Math.atan2(d[1], d[0]);
        long beat = Math.max(0, now - castingSince) / CAST_BEAT;
        double swing = ((beat + c) % 2 == 0 ? 1.0 : -1.0) * (0.3 + 0.45 * ((beat + c * 2L) % 3));
        double reach = Math.min(trailNumbers.castReach(), gap.far() - gap.near() + 1.5);
        double radius = 1.0 + reach * (((beat + c) % 3) + 1) / 3.0;
        BlockPos spot = standAt(level, base[0] + Math.cos(heading + swing) * radius, base[1] + Math.sin(heading + swing) * radius,
                (int) Math.round(base[2]));
        return spot != null ? spot : route.place(level, Math.max(0.0, gap.near() - GAP_STOP), 0.0);
    }

    boolean isOut(ServerLevel level, MatabeleEntity ant) {
        BlockPos entrance = colony.getCenter();
        if (colony.nest().isInside(level, ant.blockPosition())) {
            return false;
        }
        if (flat(ant.blockPosition(), entrance) <= NEAR && ant.getY() < entrance.getY() - 1.5
                && (flat(ant.blockPosition(), entrance) <= CHIMNEY_REACH || !level.canSeeSky(ant.blockPosition().above()))) {
            // (The raid queue fix: only in the shaft itself or under cover. An ant on the hump's open side, a block or two
            // under its top and two off the trail, counted as down the chimney, was called back up to the line's start it
            // could not climb to from there, and 14 of 47 were sent home "stuck" in one test run.)
            // Lower than the entrance near it: down the chimney (BUGS 87), unless it is on the trail at the trail's own
            // height (the raid queue fix, 2026-11-06: off a tall dead mound the trail drops steeply down the hump's side;
            // an ant stepping down it counted as still in the nest, was called back up to the trail's start, then down
            // again, and the whole crowd jammed at the hump's edge for 20 s and more).
            // (Measured straight to the trail's line, height counted: halfway down a sheer wall the flat place along it
            // cannot say how high the trail is there.)
            return out.offBy(ant.getX(), ant.getZ()) <= ON_ROUTE && out.awayFrom(ant.getX(), ant.getY(), ant.getZ()) <= ON_TRAIL_HEIGHT;
        }
        return true;
    }

    /**
     * The raid queue fix (2026-11-06): the cell to stand in {@code walked} blocks walked along the route (climbs counted),
     * but never one behind the ant it is for ({@code own}, walked). Partway down a sheer wall there is no floor, so a
     * place there snaps to the nearest floor, and that was at times the wall's top behind the ant: raiders halfway down a
     * tall dead mound's side were sent back up, got nowhere for 15 s and were sent home "stuck". A place that snaps back
     * looks a little further on instead (at most {@link #AHEAD_LOOK} blocks), then the route's end side of it. The ribbon
     * (2026-11-07): a place that snaps to the ant's own cell counts as behind it too (on a tall dead mound's top the
     * places partway down its wall all snapped to the top, the ant was sent to the cell it stood in and stood there for
     * the whole march; the wall's foot, up to {@link #AHEAD_LOOK} blocks walked on, is the place).
     */
    private static BlockPos placeAhead(ServerLevel level, Route route, double walked, double own) {
        double end = route.walkedLength();
        BlockPos first = null;
        for (double t = Math.min(walked, end); ; t = Math.min(end, t + 0.5)) {
            BlockPos cell = route.place(level, route.alongAtWalked(t), 0.0);
            if (first == null) {
                first = cell;
            }
            if (route.walkedOf(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5) >= Math.min(own + 0.5, walked) - 0.25) {
                return cell;
            }
            if (t >= end || t >= walked + AHEAD_LOOK) {
                return first;
            }
        }
    }

    /**
     * The raid code review, finding 17: a hop of {@code hop} blocks from {@code from} toward {@code to} along the
     * column's trail (out and home are one line), when both lie within {@link #HOP_OFF} of it and more than a hop apart
     * along it; null otherwise (the raider walks straight). A raider far behind, or told to the entrance from the far
     * end, so follows the trail's bends instead of cutting across them.
     */
    @Nullable
    BlockPos hopAlong(ServerLevel level, BlockPos from, BlockPos to, double hop) {
        double fx = from.getX() + 0.5, fz = from.getZ() + 0.5, tx = to.getX() + 0.5, tz = to.getZ() + 0.5;
        if (out.offBy(fx, fz) > HOP_OFF || out.offBy(tx, tz) > HOP_OFF) {
            return null;
        }
        double a = out.along(fx, fz), b = out.along(tx, tz);
        if (Math.abs(b - a) <= hop) {
            return null;
        }
        return out.place(level, a + Math.signum(b - a) * hop, 0.0);
    }

    @Nullable
    private static BlockPos standAt(ServerLevel level, double x, double z, int y) {
        return RaidGround.standAt(level, x, z, y);
    }

    private static double flat(BlockPos a, BlockPos b) {
        return RaidGround.flat(a, b);
    }
}
