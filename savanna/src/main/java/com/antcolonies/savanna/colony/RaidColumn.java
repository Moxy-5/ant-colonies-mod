package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.AwayLife;
import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleLoads;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * M8 step 3b, one raid column ({@link Raids}; {@code docs/DESIGN.md}, "Step 3b, the column"). Real Megaponera
 * (Longhurst and Howse 1979, Hoelldobler et al. 1994, Frank et al. 2017-2018, Yusuf 2026): a scout home from a find
 * recruits nestmates at the nest and they follow it out along its scent line as a column (here the ribbon, the user's
 * pick of 2026-11-07: the scout leads off at once and the raiders unroll out of the nest behind it in rows of 3 and 2,
 * never stopping on the march, {@link #march}); a little short of the termites it halts and bunches up, then they rush
 * in all at once (the majors bite the
 * galleries open, the minors go in and sting); after the fight they gather again and walk home as a column.
 *
 * <p>Here the colony's brain keeps the column: who is in it (the scout leads), what stage it is at, and where along the
 * trail each ant should be now ({@link #orderFor}). Each raider's mind ({@code MatabeleRaidGoal}) only walks to that
 * place through its navigator; the fight at the spot is step 2's ({@code FightGoal}), each raider sent there with a
 * fight order. Not saved: after a reload the raiders are just ants outside, and go home.
 * Numbers: the species file's {@code raids.column} ({@link SavannaNumbers.ColumnNumbers}).
 *
 * <p>Step 3c, home with the prey ({@code docs/DESIGN.md}, "Step 3c, home with the prey"): after the fight the raiders
 * gather the stung termites lying round the spot ({@link Stage#COLLECTING}: each one with room in its jaws is given the
 * nearest one nobody has, the termite workers first, and picks it up, {@link #pickUp}), a major a bundle of up to 10, a
 * minor 1 to 3 (real: Yusuf 2026), then regroup and walk home, a little slower laden; at home each lays its termites on
 * the prey pile ({@code BringPreyGoal}). A raid that found nobody to sting spreads out round the spot for a while
 * ({@link Stage#SPREADING}), going for any termite it sees, and gathers what it stung; none: home empty, and the find
 * counts an empty check. A raider that leaves the column hurt (crippled, or a termite soldier clamped on a leg) is
 * handed to the colony's raids to limp home along the trail ({@link #takeLetGo}). Numbers: {@code raids.carry}
 * ({@link SavannaNumbers.CarryNumbers}).
 *
 * <p>Raid rework R4, the scent trail ({@link ScentTrails}): the column walks its colony's trail to the find, and every
 * raider lays scent on the way out and home, so the trail is fresh again ({@link ScentTrails.Trail#walked}). Where the
 * trail is broken (a block dug, put or flooded on it) the front stops at the gap and the ants ahead cast about in loops
 * ({@link #isCasting}); a short gap's far side is picked up, a wide one is crossed only behind a former scout (from
 * memory), and either way the ants lay new scent over it (the gap mended); a wide gap with no former scout turns the raid
 * home ({@link #lostTrail}). On the way home a gap is only a pause: the way home is always found. Numbers:
 * {@code raids.trails} ({@link SavannaNumbers.TrailNumbers}).
 */
public final class RaidColumn {
    public enum Stage { FORMING, MARCHING, GATHERING, ATTACKING, COLLECTING, SPREADING, REGROUPING, HOMING, DONE }

    /**
     * Where a raider should walk now and how fast, or that it should stand where it is ({@code to} null); {@code take}:
     * the stung termite it is to pick up there (step 3c), null for none. The ribbon (the user's pick of 2026-11-07):
     * {@code shift}, how far to the side of the trail's middle it walks (blocks, a flat world offset: its lane in the
     * column's row; null its legs' own lane), and {@code onTheMove}: a marching place that moves on with the column,
     * walked toward without ever settling at it.
     */
    public record Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt, @Nullable UUID take,
                        @Nullable net.minecraft.world.phys.Vec3 shift, boolean onTheMove) {
        public Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt) {
            this(to, pace, lookAt, null);
        }

        public Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt, @Nullable UUID take) {
            this(to, pace, lookAt, take, null, false);
        }

        /** The ribbon: a place in the moving column, this far to the side of the trail's middle. */
        static Order marching(BlockPos to, double pace, @Nullable BlockPos lookAt, @Nullable net.minecraft.world.phys.Vec3 shift) {
            return new Order(to, pace, lookAt, null, shift, true);
        }

        Order slower(double by) {
            return new Order(to, pace * by, lookAt, take, shift, onTheMove);
        }
    }

    /** The carry numbers when a species file has none (the same as Megaponera's). */
    public static final SavannaNumbers.CarryNumbers DEFAULT_CARRY =
            new SavannaNumbers.CarryNumbers(4, 10, 1, 3, 10.0, 1200, 10, 600, 8.0, 5.0, 0.85, 10, 2400, 6000);
    /** A raider picks up a termite this near it (blocks). */
    public static final double PICK_REACH = 1.6;
    /** A termite a raider found no way to is left to the others this long (ticks; BUGS 76: never searched every tick). */
    private static final int NO_WAY_TICKS = 200;

    /**
     * The ribbon (the user, 2026-11-07: "give him like a 5% speed boost, its subtle but it will keep him infront"): the
     * front walks this much faster than the column's own pace, and never stops.
     */
    private static final double FRONT_PACE = 1.05;
    /**
     * The ribbon: the front walks at {@link #FRONT_PACE} while the nearest raider is within its spacing and this much
     * more behind it (blocks, walked); farther back, it eases off {@link #PACE_PER_BLOCK} a block, down to
     * {@link #SLOWEST_PACE}, still walking (my pick).
     */
    private static final double FRONT_KEEP = 2.0;
    /** The ribbon: down a dead mound's hump the rows keep this many times their gap (my pick). */
    private static final double HILL_SPACING = 2.0;
    /**
     * A row's ants within this many blocks (walked) behind its lead count as the row (the lead lets them come up beside it,
     * the front eases for them); one farther back is a straggler the row does not wait for. My pick.
     */
    private static final double ROW_REACH = 3.0;
    /**
     * How strongly a raider walks to its row's middle, against how strongly the row walks to its place (1 the same): above
     * 1, a row's ants come side by side quicker than the row closes on the row ahead. My pick.
     */
    private static final double ROW_PULL = 2.0;
    /**
     * A raider level with the front or past it walks this slow (slower than the front ever walks, {@link #SLOWEST_PACE}),
     * so it drops back behind it without stopping: with the front easing for the ribbon, a raider that got past it at the
     * same slow pace stayed in front of it all the march. My pick.
     */
    private static final double PASSED_PACE = 0.4;
    /** A straggler, or the scout running in to recruit, hurries at this pace. */
    private static final double HURRY_PACE = 1.15;
    /** The ribbon: a raider far behind its place (one just out of the nest) catches up at up to this pace (my pick). */
    private static final double CATCH_UP_PACE = 1.35;
    /**
     * The ribbon: the lanes in a row lie this far apart (blocks; my pick: a little over a major's personal distance, 0.4,
     * so ants side by side never count as in each other's way): 3 abreast at -0.44, 0, 0.44, 2 at -0.22, 0.22.
     */
    private static final double LANE_GAP = 0.44;
    /** How far ahead (blocks along the trail) of itself the front walks to at a time. */
    private static final double LEAD_STEP = 6.0;
    /** A raider counts as at the gathering place, or out at the entrance, this near it (blocks, flat). */
    private static final double NEAR = 4.0;
    /** The share of the raiders that must be at the gathering place before the attack or the walk home (my pick). */
    private static final double MOST_THERE = 0.75;
    /**
     * A raider is home this near the entrance (blocks, flat and up or down): there the column breaks up and each ant goes
     * in by itself, down whatever hole it likes (BUGS 85: a dead mound's chimney mouth is an open hole, no cell to stand
     * in, so a column walking right to it stalled on the hump's side).
     */
    private static final double HOME = 8.0;
    /** R7: on the way home an ant this much past the home reach from the entrance leaves the line and walks in (my pick). */
    private static final double STREAM_IN = 4.0;
    /**
     * R7: on the way home, if for this long (ticks) no ant got a block farther along the way home and nobody new came
     * home, the line breaks up and every ant walks in by its own way (my pick, 10 s; a front stuck at the foot of a dead
     * mound's hump, unable to reach its place on the slope, held the whole line still in the R7 chain test).
     */
    private static final long HOME_STALL = 200;
    /** Raid flow: a walking raider aims this far (blocks along the trail) past its place, so it walks on, never stopping. */
    private static final double CARROT = 2.5;
    /** Raid flow: its pace goes up (or down) this much for each block it is behind (or ahead of) its place. */
    private static final double PACE_PER_BLOCK = 0.12;
    /** Raid flow: the slowest pace of a raider ahead of its place (it lets the column come up to it, still walking). */
    private static final double SLOWEST_PACE = 0.55;
    /** R4: while the front casts about for a broken trail the column closes up to this share of its spacing (nose to tail; my pick). */
    private static final double CLOSE_UP = 0.75;
    /**
     * The raid queue fix (2026-11-06): a marching raider walks to a place at most this far (blocks along the trail) ahead
     * of itself at a time, then the next (a far place across a dead mound's hump found no way, and the ant was sent home
     * after 15 s; my pick, about 8 major lengths).
     */
    private static final double WALK_AHEAD = 4.0;
    /** The raid queue fix: an ant lower than the entrance this near it (flat blocks) is in the chimney's shaft. */
    private static final double CHIMNEY_REACH = 1.5;
    /** The raid queue fix: how far on (blocks walked) a place that snapped back behind its ant looks for one ahead of it. */
    private static final double AHEAD_LOOK = 16.0;
    /** The raid queue fix: an ant is on the trail this near its height there (blocks up or down). */
    private static final double ON_TRAIL_HEIGHT = 1.5;
    /** How often (ticks) the column works out its places. */
    private static final int EVERY = 5;
    /**
     * Raid rework R3: a raider whose place is more than {@link #HEADWAY_FAR} blocks off and that has not got
     * {@link #HEADWAY_MOVE} blocks anywhere for this long (ticks) leaves the column and goes home along the trail, never
     * holding the rest (BUGS 87; my pick, 15 s).
     */
    private static final long NO_HEADWAY = 300;
    private static final double HEADWAY_FAR = 2.5;
    private static final double HEADWAY_MOVE = 1.5;
    /** Raid rework R3: the march has this long at most (ticks), and this much more for each block of it (my picks). */
    private static final long MARCH_BASE = 400;
    private static final long MARCH_PER_BLOCK = 60;
    /** R4: a broken trail point this far ahead of the front (blocks along) is a gap it sees; it stops this far short of it. */
    private static final double GAP_LOOK = 4.0;
    private static final double GAP_STOP = 1.5;
    /** R4: a trail point counts as on the route this near it (blocks, flat). */
    private static final double ON_ROUTE = 2.0;
    /** A raider and its place both this near the trail (blocks) hop along it, not straight ({@link #hopAlong}; my pick). */
    private static final double HOP_OFF = 4.0;
    /** R4: how many ants at the front cast about at a gap (the front and the next ones out). */
    private static final int CASTERS = 3;
    /** R4: the casters turn to a new loop every this many ticks. */
    private static final long CAST_BEAT = 30;

    private final Colony colony;
    private final SavannaNumbers.ColumnNumbers numbers;
    private final SavannaNumbers.CarryNumbers carry;
    private final SavannaNumbers.TrailNumbers trailNumbers;
    private final Raids.Find find;
    /** R4: the scent trail the column walks. */
    private final ScentTrails.Trail trail;
    /** The march out, from the entrance to the gathering place, and the walk home, the other way. */
    private final Route out;
    private final Route back;
    /** The raiders in marching order, the front first. */
    private final List<UUID> members = new ArrayList<>();
    /** The same raiders as a set (the raid code review, finding 9: "is it a raider?" is asked of every ant, often). */
    private final Set<UUID> memberSet = new HashSet<>();
    /**
     * The raid code review, finding 9, kept for one update only: each raider's place along each route (asked many times
     * an update), the stung termites round the spot, and the gap look-up (kept till the trail's points change).
     */
    private final Map<MatabeleEntity, Double> alongOutNow = new java.util.IdentityHashMap<>();
    private final Map<MatabeleEntity, Double> alongBackNow = new java.util.IdentityHashMap<>();
    @Nullable
    private List<AbstractAntEntity> preyNow;
    @Nullable
    private Route gapRoute;
    private int gapShape = -1;
    private double gapTurnedAt = Double.NaN;
    private double[] gapAlong = new double[0];
    private int[] gapIndex = new int[0];
    private UUID leader;
    /** The whole raid's size, real ants and the numbers who go along (the numbers walk in step 4). */
    private final int size;
    private final int numbersAlong;
    private Stage stage = Stage.FORMING;
    private long stageSince;
    private final long startedAt;
    /** Where the front walks to now, and where it is (blocks along the route). */
    private double head;
    private double frontAt;
    /** Each raider's place now, and the raiders sent in to fight. */
    private final Map<UUID, Order> orders = new HashMap<>();
    private final Set<UUID> sentIn = new HashSet<>();
    private long updatedAt = -EVERY;
    /** What happened, for the colony list and the tests. */
    private int cameHome;
    private long attackedAt = -1;
    private int gatheredAtAttack;
    private String ended = "";
    private final Map<String, Integer> lost = new java.util.TreeMap<>();
    /** Step 3c: each prey-taker's termite, the termites nobody found a way to (till when), and what was carried. */
    private final Map<UUID, UUID> taking = new HashMap<>();
    private final Map<UUID, Long> noWay = new HashMap<>();
    private int carried;
    private int carriers;
    private boolean spread;
    /**
     * M8 step 3d: the termites the raiders going as numbers took from under the mud at the attack (taken from the termite
     * colony's numbers, {@link RaidCatch}), brought home with the column, and that termite colony.
     */
    private int hiddenCatch;
    @Nullable
    private UUID hiddenFrom;
    /**
     * The raiders let go of the column out in the field since the colony last asked ({@link #takeLetGo}), and why: the
     * hurt, the stuck, the leftovers when the time at home ran out, a raid lost from sight. The colony's raids send each
     * one home along the trail ({@link WayHome}; the raid code review, finding 2, BUGS 88).
     */
    private final List<LetGo> letGo = new ArrayList<>();

    /** One raider let go of the column, and why. */
    record LetGo(UUID ant, String why) {
    }

    /**
     * The raid code review, finding 1: since when each member has been out of sight (not in the world, or in land that
     * does not tick now). One out of sight is kept in the column for the colony's away grace ({@code AwayLife}, a
     * minute) before it is dropped as lost from sight; a raid whose leader stays out of sight that long ends, lost from
     * sight, and is no empty check of its spot.
     */
    private final Map<UUID, Long> unseenSince = new HashMap<>();
    private boolean lostFromSight;
    /** Whether the raid reached its spot and found nothing there (gathered nothing, spread out and gathered nothing). */
    private boolean foundNothing;
    /** R7: dusk was called (Raids, at 19:30): the column stops where it is, picks up what it stung and walks home. */
    private boolean duskCalled;
    /** R7: dusk has been dealt with (the column turned for home). */
    private boolean duskDone;
    /** R7: sent outside the raid hours by a command: it keeps no hours (no dusk turn). */
    private boolean keepsNoHours;
    /** R7: when a termite was last about the spot in the attack (game time), or -1. */
    private long termiteSeenAt = -1;
    /** R7: when the column last squeaked at each player near it (game time). */
    private final Map<UUID, Long> squeakedAt = new HashMap<>();
    /** R7: the column turned home at the edge of land not ticking (tests and the log). */
    private boolean turnedAtEdge;
    /** The raid code review, finding 4: since when each member is held by another fight order (drafted by the nest's defence). */
    private final Map<UUID, Long> draftedSince = new HashMap<>();
    /** A member drafted away from the column longer than this (ticks) is let go of it (my pick, 30 s). */
    private static final long DRAFT_KEEP = 600;
    /** The column's clocks wait while it was not worked out for longer than this (ticks: the colony out of sight; my pick). */
    private static final long HOLD_AFTER = 100;
    /** A raider farther than this (blocks, flat) from its place round the gathering place walks the trail up to it. */
    private static final double STRAGGLER_FAR = 10.0;
    /** Tests: the members made out of sight by hand. */
    private final Set<UUID> hiddenForTests = new HashSet<>();
    /** Raid rework R1, the raid diary: the world the column last worked in (for the time of day in its log lines), and when the march was last told. */
    @Nullable
    private ServerLevel lastLevel;
    private long marchSaidAt = Long.MIN_VALUE / 2;
    /**
     * The ribbon: each raider's place in the column's order, given as it comes out of the nest (on the way home, by where
     * it stands at the start), and the next to give; kept for the stage, so nobody's place jumps about.
     */
    private final Map<UUID, Integer> rank = new HashMap<>();
    private int nextRank;
    /** The ribbon: the route {@link #hillEndAt} was worked out for, and where its first climbs and drops end (along). */
    @Nullable
    private Route hillRoute;
    private double hillEndAt;
    /** R3: where each raider last got somewhere, and since when (for its headway). */
    private final Map<UUID, BlockPos> anchor = new HashMap<>();
    private final Map<UUID, Long> anchorSince = new HashMap<>();
    /** R4: a gap in the trail: where it starts and where the trail goes on (blocks along the route walked). */
    private record Gap(double near, double far) {
    }

    /** R4: since when the front casts about at a gap (-1 not), that gap, the casters, the time cast this stage in all. */
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
    /** The march and the walk home are told in the log every this long (ticks). */
    private static final long WAIT_SAY_EVERY = 200;
    /** R7, the diary: when the walk home was last told (game time). */
    private long homeSaidAt = Long.MIN_VALUE / 2;
    /** R7: on the way home, the farthest along any ant got, the count home then, and since when (game time). */
    private double homeBest = -1.0;
    private int homeBestCame;
    private long homeBestAt = -1;
    /** R7: the line broke up on the way home (no headway): every ant walks in by its own way. */
    private boolean streamAll;
    /**
     * Each raider's own number for its place round the gathering place and the spot (BUGS 101: given once, so one ant
     * dying or going out of sight moves nobody else's place).
     */
    private final Map<UUID, Integer> formIndex = new HashMap<>();
    /**
     * BUGS 101, the scout recruits in the nest (real Megaponera: the scout home from a find runs into the nest, and the
     * raiders it stirs pour out after it): the room cell it runs to (null for none), whether that was looked for, and when
     * it got there (or gave up), -1 till then. The march starts from then.
     */
    @Nullable
    private BlockPos recruitAt;
    private boolean recruitLooked;
    private long recruitedAt = -1;
    /** The scout's longest run in to recruit (ticks; my pick, 15 s): then the column leaves all the same. */
    private static final long RECRUIT_MOST = 300;

    RaidColumn(ServerLevel level, Colony colony, SavannaNumbers.ColumnNumbers numbers, @Nullable SavannaNumbers.CarryNumbers carry,
               SavannaNumbers.TrailNumbers trailNumbers, Raids.Find find, ScentTrails.Trail scent, List<MatabeleEntity> raiders,
               int numbersAlong, long now) {
        this.colony = colony;
        this.numbers = numbers;
        this.carry = carry != null ? carry : DEFAULT_CARRY;
        this.trailNumbers = trailNumbers;
        this.find = find;
        this.trail = scent;
        this.startedAt = now;
        this.stageSince = now;
        for (MatabeleEntity raider : raiders) {
            members.add(raider.getUUID());
            memberSet.add(raider.getUUID());
            formIndex.put(raider.getUUID(), formIndex.size());
        }
        this.leader = members.get(0);
        this.size = members.size() + numbersAlong;
        this.numbersAlong = numbersAlong;
        // The scent line starts at the entrance itself: points right over it are the shaft (a dead mound's chimney is an
        // open hole, so the ground's height there is the room at its foot), and a route through them dives into the hump
        // (BUGS 85: the column home stuck at the hump's foot).
        // Raid rework R3: the line starts at the first cell to stand in beside the entrance, toward the find, never on the
        // entrance itself (a dead mound's open chimney: a place there is in the air over the hole, BUGS 87).
        // R4: the line is the scent trail on the ground (the scout's line, or a column's trail walked before).
        List<BlockPos> trail = new ArrayList<>(this.trail.points());
        BlockPos start = lineStart(level, colony.getCenter(), trail.isEmpty() ? find.at() : trail.get(trail.size() - 1), trail);
        double startOut = flat(start, colony.getCenter());
        trail.removeIf(p -> flat(p, colony.getCenter()) < Math.max(1.5, startOut + 0.5));
        trail.add(0, start);
        // The gathering place: the last point of the scent line at least stop_short from the find.
        int gather = 0;
        for (int i = 0; i < trail.size(); i++) {
            if (flat(trail.get(i), find.at()) >= numbers.stopShort()) {
                gather = i;
            }
        }
        List<BlockPos> outPoints = new ArrayList<>(trail.subList(0, Math.max(1, gather) + 1));
        this.out = new Route(outPoints);
        List<BlockPos> backPoints = new ArrayList<>(outPoints);
        java.util.Collections.reverse(backPoints);
        this.back = new Route(backPoints);
        this.head = 0.0;
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
     * Raid rework R1, the raid diary: one line in the log, {@code [raid]} first, with the nest, the game time and the
     * time of day, and the stage now ({@code docs/plans/m8-matabele.md}, 3R).
     */
    void say(String what) {
        com.antcolonies.watch.Watch.note(lastLevel, colony, "[raid] " + stage.name().toLowerCase() + ": " + what);
        com.antcolonies.savanna.SavannaMod.LOGGER.info("[raid] nest {} at {} (tick {}), {}: {}", colony.getCenter().toShortString(),
                RaidGround.clock(colony, lastLevel),
                lastLevel == null ? -1 : lastLevel.getGameTime(), stage.name().toLowerCase(), what);
    }

    /** The diary's first line: the raid sets out (said by the colony's raids once the column is made). */
    void sayStart(ServerLevel level) {
        lastLevel = level;
        say("a raid of " + size + " (" + members.size() + " real, " + numbersAlong + " as numbers) to " + find.at().toShortString()
                + ", " + Math.round(flat(find.at(), colony.getCenter())) + " out, worth " + find.worth() + "; the march "
                + Math.round(out.length()) + " blocks along " + out.points.size() + " points to the gathering place "
                + out.end().toShortString() + "; led by " + shortId(leader));
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** One raider in the diary: its id, where it is, how far along the march, and what it is doing. */
    private String who(MatabeleEntity ant, Route route) {
        Order order = orders.get(ant.getUUID());
        return shortId(ant.getUUID()) + " at " + ant.blockPosition().toShortString() + ", " + Math.round(alongOf(route, ant) * 10) / 10.0
                + " along" + (order == null || order.to() == null ? ", no place" : ", its place " + order.to().toShortString()
                + " (" + Math.round(Math.sqrt(ant.blockPosition().distSqr(order.to())) * 10) / 10.0 + " off)")
                + (colony.nest().isInside(ant.level(), ant.blockPosition()) ? ", inside the nest" : "")
                + ", walking " + !ant.getNavigator().isDone() + ", goals " + ant.runningGoals();
    }

    public Stage stage() {
        return stage;
    }

    public Raids.Find find() {
        return find;
    }

    /** R4: the scent trail it walks. */
    public ScentTrails.Trail trail() {
        return trail;
    }

    /** R4: whether this raider casts about at a gap in the trail now. */
    public boolean isCasting(UUID ant) {
        return castingSince >= 0 && casters.contains(ant);
    }

    /** R4: how many gaps in the trail the column crossed, and whether it turned home having lost the trail. */
    public int gapsCrossed() {
        return gapsCrossed;
    }

    public boolean lostTrail() {
        return lostTrail;
    }

    public UUID leader() {
        return leader;
    }

    public List<UUID> members() {
        return List.copyOf(members);
    }

    public boolean has(UUID ant) {
        return memberSet.contains(ant);
    }

    public int size() {
        return size;
    }

    public long startedAt() {
        return startedAt;
    }

    /** The raid test bench (B1): when the stage now began (game time; moved on while the raid's clocks waited). */
    public long stageSince() {
        return stageSince;
    }

    /** The raid test bench (B1): when the column was last worked out (game time). */
    public long updatedAt() {
        return updatedAt;
    }

    /**
     * The raid test bench (B1, the stage-limit rule): the longest the stage now can last by the column's own clocks
     * (ticks), from the species file's numbers: the same table as the whole-chain raid test's, with the scout's
     * recruiting before the forming and the time spent casting about at gaps in the march.
     */
    public long stageMostTicks() {
        return switch (stage) {
            case FORMING -> RECRUIT_MOST + EVERY * 2;
            case MARCHING -> marchLimit() + castTotal + (castingSince >= 0 && lastLevel != null ? lastLevel.getGameTime() - castingSince : 0);
            case GATHERING -> numbers.gatherTicks();
            case ATTACKING -> numbers.fightTicks() + 200; // (the fight's own hard end)
            case COLLECTING -> carry.collectTicks();
            case SPREADING -> carry.spreadTicks() + 200;
            case REGROUPING -> numbers.regroupTicks();
            case HOMING -> numbers.homeTicks();
            case DONE -> Long.MAX_VALUE / 4;
        };
    }

    /** The raid test bench: the order this raider was last given (null: none, or not a raider now). */
    @Nullable
    public Order orderNow(UUID ant) {
        return orders.get(ant);
    }

    /** Tests (the raid test bench): the stage now as if it had begun {@code ticks} earlier, to break the stage-limit rule. */
    public void ageStageForTests(long ticks) {
        stageSince -= ticks;
    }

    /** Test only (the raid test bench): a raid that is never worked out any more, as a hang would leave it. */
    private boolean hungForTests;

    public void hangForTests() {
        hungForTests = true;
    }

    /** When the attack began (game time, -1 not yet), and how many raiders were at the gathering place then. */
    public long attackedAt() {
        return attackedAt;
    }

    public int gatheredAtAttack() {
        return gatheredAtAttack;
    }

    /** How many raiders came home (left the column at the entrance). */
    public int cameHome() {
        return cameHome;
    }

    /** How many stung termites the raiders picked up, and how many raiders carried some (step 3c). */
    public int carried() {
        return carried;
    }

    public int carriers() {
        return carriers;
    }

    /** Whether the raid has spread out round the spot, finding nobody to sting at first. */
    public boolean spread() {
        return spread;
    }

    /** The walk home along the trail (from the gathering place to the entrance): hurt raiders limp home on it. */
    Route back() {
        return back;
    }

    /** The raiders let go of the column since last asked (the colony's raids send them home); the list is emptied. */
    List<LetGo> takeLetGo() {
        List<LetGo> out = List.copyOf(letGo);
        letGo.clear();
        return out;
    }

    /** Whether the raid ended lost from sight (its leader out of sight past the grace): no empty check of the spot. */
    public boolean lostFromSight() {
        return lostFromSight;
    }

    /** Whether the raid reached its spot and found nothing to sting or carry there. */
    public boolean foundNothing() {
        return foundNothing;
    }

    /** Whether this member is out of sight now (tests and the diary). */
    public boolean isUnseen(UUID ant) {
        return unseenSince.containsKey(ant);
    }

    /** Whether this member is held by another job's fight order now (drafted by the nest's defence). */
    public boolean isDrafted(UUID ant) {
        return draftedSince.containsKey(ant);
    }

    /** Tests: this member counts as out of sight from now on (as if its land stopped ticking). */
    public void hideForTests(UUID ant) {
        if (memberSet.contains(ant)) {
            hiddenForTests.add(ant);
        }
    }

    /** Tests: this member is back in sight. */
    public void showForTests(UUID ant) {
        hiddenForTests.remove(ant);
    }

    /**
     * Tests (M8 step 3Q): whether this raider is waiting now, at its place in one of the column's waits (standing while
     * the scout calls at the nest's mouth, bunched short of the termites, waiting out the attack, regrouping), not fighting and not sent anywhere far.
     */
    public boolean waitingForTests(AbstractAntEntity ant) {
        if (stage != Stage.FORMING && stage != Stage.GATHERING && stage != Stage.ATTACKING && stage != Stage.REGROUPING) {
            return false;
        }
        if (ant.getFightSpot() != null) {
            return false;
        }
        Order order = orders.get(ant.getUUID());
        return order != null && order.to() != null && flat(ant.blockPosition(), order.to()) <= 2.5;
    }

    /** The gathering place, a few blocks short of the find. */
    public BlockPos gatheringPlace() {
        return out.end();
    }

    /** The scent line the column walks out on (the entrance end first), as points. */
    public List<BlockPos> route() {
        return out.points;
    }

    /** How far along the march out (blocks from the entrance) this place lies. */
    public double alongOut(BlockPos at) {
        return out.along(at.getX() + 0.5, at.getZ() + 0.5);
    }

    /** How far this place is from the scent line the column walks out on (blocks, flat). */
    public double offTrail(BlockPos at) {
        return out.offBy(at.getX() + 0.5, at.getZ() + 0.5);
    }

    /** The ribbon (tests): how far along the march out an insect stands, from its exact place (blocks, flat). */
    public double alongOut(Vec3 at) {
        return out.along(at.x, at.z);
    }

    /** The ribbon (tests): how far to the right of the scent line out an insect stands (blocks, flat; left negative). */
    public double sideOfTrail(Vec3 at) {
        double along = out.along(at.x, at.z);
        double[] on = out.at(along);
        double[] d = out.direction(along);
        return (at.x - on[0]) * -d[1] + (at.z - on[1]) * d[0];
    }

    /** Where this raider should be now, or null to stand (worked out every few ticks). */
    @Nullable
    public Order orderFor(ServerLevel level, MatabeleEntity ant) {
        RaidCost.begin();
        try {
            update(level);
        } catch (RuntimeException e) {
            // B7: as in the colony's own call (Raids): the raid is given up rather than crash the game or hold its raiders
            com.antcolonies.ColonyFrameworkMod.LOGGER.error("[raid] colony {}: the raid column's update failed; the raid is given up",
                    colony.getId(), e);
            abandon(level, e.toString());
        } finally {
            RaidCost.end();
        }
        return orders.get(ant.getUUID());
    }

    /** It leaves the column (gone home on its own, crippled, dead). */
    void drop(UUID ant) {
        members.remove(ant);
        memberSet.remove(ant);
        orders.remove(ant);
        sentIn.remove(ant);
        taking.remove(ant);
        unseenSince.remove(ant);
        draftedSince.remove(ant);
    }

    /** It leaves the column out in the field: dropped, and handed to the colony's raids to go home along the trail. */
    private void letGo(UUID ant, String why) {
        drop(ant);
        anchor.remove(ant);
        anchorSince.remove(ant);
        letGo.add(new LetGo(ant, why));
    }

    /**
     * The raid code review, finding 1: the column was not worked out for a while (the colony out of sight) or every
     * member is out of sight: its clocks wait that long, so a raid coming back into sight does not end its stages in a
     * burst. A fight under way is sent in again (the fight orders ran out meanwhile).
     */
    private void holdClocks(long by) {
        if (by <= 0) {
            return;
        }
        stageSince += by;
        if (attackedAt >= 0) {
            attackedAt += by;
        }
        if (termiteSeenAt >= 0) {
            termiteSeenAt += by;
        }
        if (castingSince >= 0) {
            castingSince += by;
        }
        anchorSince.replaceAll((id, since) -> since + by);
        draftedSince.replaceAll((id, since) -> since + by);
        if (stage == Stage.ATTACKING) {
            sentIn.clear(); // (sent in again at the spot, with fresh orders)
        }
    }

    /** The column's stages and every raider's place, every {@link #EVERY} ticks (asked by the raiders and the colony). */
    void update(ServerLevel level) {
        long now = level.getGameTime();
        if (now - updatedAt < EVERY || stage == Stage.DONE || hungForTests) {
            return;
        }
        long previous = updatedAt;
        updatedAt = now;
        lastLevel = level;
        RaidCost.updated();
        alongOutNow.clear();
        alongBackNow.clear();
        preyNow = null;
        if (previous >= 0 && now - previous > HOLD_AFTER) {
            say("the raid was not worked out for " + (now - previous) / 20 + " s (the colony's land not ticking): its clocks waited");
            holdClocks(now - previous - EVERY);
        }
        List<MatabeleEntity> ants = new ArrayList<>();
        for (UUID id : List.copyOf(members)) {
            net.minecraft.world.entity.Entity entity = level.getEntity(id);
            boolean seen = entity instanceof MatabeleEntity && level.isPositionEntityTicking(entity.blockPosition()) && !hiddenForTests.contains(id);
            if (!seen && (entity == null || entity.isAlive())) {
                // The raid code review, finding 1: out of sight (its land not loaded or not ticking): kept in the column for
                // the away grace, then lost from sight; never dropped at once as "gone from the world".
                long since = unseenSince.computeIfAbsent(id, key -> {
                    say(shortId(key) + " is out of sight: kept in the column for " + AwayLife.GRACE_TICKS / 20 + " s");
                    return now;
                });
                if (now - since > AwayLife.GRACE_TICKS) {
                    lost.merge("lost from sight", 1, Integer::sum);
                    say(shortId(id) + " leaves the column: lost from sight " + (now - since) / 20 + " s");
                    if (id.equals(leader)) {
                        lostFromSight = true;
                    }
                    letGo(id, "lost from sight"); // (seen again, it goes home along the trail)
                }
                continue;
            }
            if (unseenSince.remove(id) != null) {
                say(shortId(id) + " is in sight again");
            }
            if (entity instanceof MatabeleEntity ant && ant.isAlive() && !ant.fightsNoMore() && !ant.isDowned()) {
                ants.add(ant);
                if (ant.getFightSpot() != null && ant.fightOrderBy() != this) {
                    // The raid code review, finding 4: drafted by the nest's defence (or another job): it stays in the
                    // column, its place kept, and catches up once that is over; held too long, it is let go.
                    long since = draftedSince.computeIfAbsent(id, key -> {
                        say(shortId(key) + " is called away to fight (" + (stage == Stage.FORMING ? "it rejoins the column after" : "it catches up after") + ")");
                        return now;
                    });
                    if (now - since > DRAFT_KEEP && !id.equals(leader)) {
                        lost.merge("kept by another fight", 1, Integer::sum);
                        say(shortId(id) + " leaves the column: called away to fight for " + (now - since) / 20 + " s");
                        ants.remove(ant);
                        letGo(id, "kept by another fight");
                    }
                } else if (draftedSince.remove(id) != null) {
                    say(shortId(id) + " is back with the column after fighting");
                }
            } else {
                // dead, crippled, clamped or stung still: out of the column
                String why = !(entity instanceof MatabeleEntity gone) ? "gone from the world" : !gone.isAlive() ? "dead"
                        : gone.fightsNoMore() ? "fights no more" : "downed";
                lost.merge(why, 1, Integer::sum);
                say(shortId(id) + " leaves the column: " + why);
                if (entity instanceof MatabeleEntity ant && ant.isAlive() && ant.fightsNoMore()) {
                    letGo(id, "hurt: " + why); // step 3c: it limps home along the trail by itself
                } else {
                    drop(id);
                }
            }
        }
        if (lostFromSight) {
            for (UUID id : List.copyOf(members)) {
                letGo(id, "the raid was lost from sight");
            }
            end("lost from sight: its leader out of sight over " + AwayLife.GRACE_TICKS / 20 + " s");
            return;
        }
        if (ants.isEmpty()) {
            if (!members.isEmpty()) {
                holdClocks(EVERY); // (every member out of sight: the raid waits for them)
                return;
            }
            lostFromSight = lost.containsKey("lost from sight") && cameHome == 0;
            end("nobody left in it at " + stage.name().toLowerCase());
            return;
        }
        if (!memberSet.contains(leader)) {
            leader = nextLeader(); // the front fell: the next ant follows the scent on
        }
        orders.clear();
        squeak(level, ants, now); // (R7: a warning to a player near the column, before anyone stings)
        if (duskCalled && !duskDone) {
            dusk(ants, now);
        }
        if (stage == Stage.DONE) {
            return;
        }
        if (stage == Stage.MARCHING && edgeAhead(level, ants)) {
            homeOrder(ants);
            next(Stage.HOMING, now); // (R7: the land ahead is not loaded: home, the find not counted as checked)
        }
        Stage was = stage;
        switch (stage) {
            case FORMING -> form(level, ants, now);
            case MARCHING -> {
                if (march(level, ants, out, now)) {
                    trail.walked(now); // (R4: every raider laid scent on the way out)
                    next(Stage.GATHERING, now);
                } else if (turnBack) {
                    turnBack = false;
                    homeOrder(ants);
                    next(Stage.HOMING, now); // (R4: the trail lost at a wide gap: home on the way they came)
                } else if (now - stageSince - castTotal - (castingSince >= 0 ? now - castingSince : 0) > marchLimit()) {
                    // (R4: time spent casting about at a gap does not count)
                    // R3: no march goes on for ever: the column goes on to the gathering place with whoever is near the
                    // front; the rest follow the scent there by themselves.
                    say("the march's time is up (" + marchLimit() / 20 + " s for " + Math.round(out.length()) + " blocks): on to the gathering place");
                    next(Stage.GATHERING, now);
                }
            }
            case GATHERING -> gather(level, ants, now);
            case ATTACKING -> attack(level, ants, now);
            case COLLECTING -> collect(level, ants, now);
            case SPREADING -> spreadOut(level, ants, now);
            case REGROUPING -> regroup(level, ants, now);
            case HOMING -> home(level, ants, now);
            default -> {
            }
        }
        if (stage == was && (stage == Stage.MARCHING || stage == Stage.HOMING)) {
            headway(level, ants, now);
        }
    }

    /**
     * The raid code review, finding 6: turned home on the march, the column's order is turned round too, the ant nearest
     * home in front (each keeps its gap to the one ahead of it, so with the front farthest out the rest stood waiting for
     * it). The ants out of sight go last.
     */
    private void homeOrder(List<MatabeleEntity> ants) {
        UUID was = sortHomeward(ants);
        if (!leader.equals(was)) {
            say("turned round: " + shortId(leader) + ", nearest home, leads the way back");
        }
    }

    /**
     * R7 (the R1 diary's slow walk home): the column's order by where each ant is along the way home, the nearest home
     * in front and leading. After the attack the ants stand bunched in no order, and each follows the ant ahead of it
     * in the column's order: kept in the order they marched out, the ant "ahead" of most stood beside them at the spot,
     * so only the front few walked and the rest waited (about 3 home every 10 s, 170 s for 17 blocks with 47 ants).
     * Sorted every update on the way home, each follows the one really ahead of it and the whole column flows home.
     * Gives the leader before.
     */
    private UUID sortHomeward(List<MatabeleEntity> ants) {
        Map<UUID, Double> along = new HashMap<>();
        for (MatabeleEntity ant : ants) {
            along.put(ant.getUUID(), alongOf(back, ant));
        }
        members.sort(java.util.Comparator.comparingDouble(id -> -along.getOrDefault(id, -1.0)));
        UUID was = leader;
        leader = members.get(0);
        return was;
    }

    /** The front fell: the next in the ribbon leads on (the one nearest the front), else the first in the raid's order. */
    private UUID nextLeader() {
        return members.stream().filter(rank::containsKey).min(java.util.Comparator.comparingInt(rank::get)).orElse(members.get(0));
    }

    /** R7's numbers (the species file's {@code raids.hours_and_sound}). */
    private SavannaNumbers.HoursSoundNumbers hoursSound() {
        return colony.profile().section(SavannaNumbers.HOURS_SOUND).orElse(SavannaNumbers.HoursSoundNumbers.DEFAULT);
    }

    /**
     * R7, raids end by dusk (the user's answer, 2026-11-04): called by the colony at 19:30 for a raid still out; the
     * column stops where it is at its next update, picks up what it stung and walks home.
     */
    public void callDusk() {
        if (!keepsNoHours) {
            duskCalled = true;
        }
    }

    /** R7: whether dusk turned this raid home. */
    public boolean turnedAtDusk() {
        return duskDone;
    }

    /** R7: sent outside the raid hours by a command (it keeps no hours: no dusk turn). */
    public void keepNoHours() {
        keepsNoHours = true;
        duskCalled = false;
    }

    /** R7: whether it keeps no hours. */
    public boolean keepsNoHours() {
        return keepsNoHours;
    }

    /** R7: whether it turned home at the edge of land not loaded. */
    public boolean turnedAtEdge() {
        return turnedAtEdge;
    }

    /**
     * R7, dusk: still forming at the entrance, the raid is called off (everyone goes in); on the march, it turns round
     * where it is; gathering, it regroups and goes home (nothing stung yet); attacking or spreading out, the fight ends
     * and they pick up what lies stung (no spreading out); collecting, regrouping or on the way home, it carries on (no
     * spreading out). The find is never counted as checked empty for a raid dusk turned home.
     */
    private void dusk(List<MatabeleEntity> ants, long now) {
        duskDone = true;
        say("dusk (" + RaidGround.clock(colony, lastLevel) + "): the raid stops where it is at " + stage.name().toLowerCase()
                + ", picks up what it stung and walks home");
        switch (stage) {
            case FORMING -> end("called off at dusk before it set out");
            case MARCHING -> {
                homeOrder(ants);
                next(Stage.HOMING, now);
            }
            case GATHERING -> next(Stage.REGROUPING, now);
            case ATTACKING, SPREADING -> {
                endFights(ants);
                next(Stage.COLLECTING, now);
            }
            default -> {
            }
        }
    }

    /** Every raider's fight for the column ends now (the attack is over). */
    private void endFights(List<MatabeleEntity> ants) {
        for (MatabeleEntity ant : ants) {
            if (ant.fightOrderBy() == this) {
                ant.endFightOrder(this);
            }
        }
    }

    /**
     * R7: whether the land ahead of the marching front is not loaded (not ticking) within {@code edge_look} blocks along
     * the way out: real ants would walk on, but no ant can walk there till drawn-only ants (M8 step 4), so the column
     * turns home at the edge, and the find is not counted as checked (it was never reached).
     */
    private boolean edgeAhead(ServerLevel level, List<MatabeleEntity> ants) {
        MatabeleEntity front = ants.stream().filter(ant -> ant.getUUID().equals(leader)).findFirst().orElse(ants.get(0));
        double from = alongOf(out, front);
        double look = hoursSound().edgeLook();
        for (double along = from; along <= Math.min(out.length(), from + look); along += 1.0) {
            BlockPos at = point(out, along);
            if (!level.isPositionEntityTicking(at)) {
                turnedAtEdge = true;
                say("the land ahead is not loaded at " + at.toShortString() + " (" + Math.round(along) + " of " + Math.round(out.length())
                        + " along the way out): the raid turns home at its edge");
                return true;
            }
        }
        return false;
    }

    /**
     * R7, the squeak (Hoelldobler et al. 1994: a raid column stridulates as it goes, no message between the ants but a
     * warning to big animals): for each player within {@code squeak_hear} blocks of a raider, the raider nearest them
     * squeaks, louder and more often the nearer the player, before anyone stings (a player standing in the column
     * provokes it: {@code NestDefence}). Spectators hear nothing.
     */
    private void squeak(ServerLevel level, List<MatabeleEntity> ants, long now) {
        SavannaNumbers.HoursSoundNumbers numbers = hoursSound();
        double hear = numbers.squeakHear();
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            MatabeleEntity nearest = null;
            double best = hear * hear;
            for (MatabeleEntity ant : ants) {
                double d = ant.distanceToSqr(player);
                if (d <= best) {
                    best = d;
                    nearest = ant;
                }
            }
            if (nearest == null) {
                continue;
            }
            double distance = Math.sqrt(best);
            Long last = squeakedAt.get(player.getUUID());
            if (last != null && now - last < numbers.everyAt(distance)) {
                continue;
            }
            squeakedAt.put(player.getUUID(), now);
            nearest.warningSqueak(numbers.loudnessAt(distance));
        }
    }

    /**
     * R7: whether a termite still stands about the spot (alive, not stung, within {@code collect_reach} of the find): the
     * attack goes on while one does, and ends {@code quiet_ticks} after the last.
     */
    private boolean termitesAbout(ServerLevel level, List<MatabeleEntity> ants) {
        double reach = carry.collectReach();
        BlockPos at = find.at();
        // The raid and fight review 2, finding 3: only termites the hunters would go for count (as FightGoal picks them):
        // one hidden in its own nest past the feeding ways is left alone by every raider, and one shut in under mud, soil
        // or wood only while a major still breaks the way in (its fight order kept); counting them kept the attack going
        // with nobody fighting, the ants searching round on their own till the attack's longest time (the user's raid:
        // 70 game minutes, till dusk).
        boolean breaking = ants.stream().anyMatch(ant -> ant.breaksIn() && fightsForTheColumn(ant));
        return !level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(at).inflate(reach, 6.0, reach),
                insect -> insect.isAlive() && !insect.isParalysed() && !insect.isDowned() && !(insect instanceof MatabeleEntity)
                        && !colony.getId().equals(insect.getColonyId()) && flat(insect.blockPosition(), at) <= reach
                        && !insect.isSheltered()
                        && (breaking || com.antcolonies.fight.Fights.isInTheOpen(level, insect.blockPosition())
                                || !com.antcolonies.fight.Fights.isShutIn(level, insect.blockPosition()))).isEmpty();
    }

    /** The column's numbers (the species file's {@code raids.column}; the tests check the stages' times by them). */
    public SavannaNumbers.ColumnNumbers columnNumbers() {
        return numbers;
    }

    /** The carrying numbers (the species file's {@code raids.carry}). */
    public SavannaNumbers.CarryNumbers carryNumbers() {
        return carry;
    }

    /** R3: how long the march out may last (ticks): {@link #MARCH_BASE} and {@link #MARCH_PER_BLOCK} a block of it. */
    public long marchLimit() {
        return MARCH_BASE + (long) Math.ceil(out.length() * MARCH_PER_BLOCK);
    }

    /**
     * R3: a raider sent somewhere more than {@link #HEADWAY_FAR} blocks off that got nowhere for {@link #NO_HEADWAY}
     * ticks leaves the column and goes home along the trail (let go, {@link #takeLetGo}); nobody stuck holds the rest.
     * One standing at its place, or told to stand, makes headway enough; so does one standing for a known reason (the
     * raid code review, finding 5): fighting, eating, picking a termite up, casting about at a gap. Only no reason and
     * no progress is stuck.
     */
    private void headway(ServerLevel level, List<MatabeleEntity> ants, long now) {
        for (MatabeleEntity ant : List.copyOf(ants)) {
            UUID id = ant.getUUID();
            BlockPos here = ant.blockPosition();
            BlockPos last = anchor.get(id);
            Order order = orders.get(id);
            boolean far = order != null && order.to() != null && Math.sqrt(here.distSqr(order.to())) > HEADWAY_FAR;
            boolean held = ant.getFightSpot() != null || taking.containsKey(id) || isCasting(id)
                    || ant.runsGoal(com.antcolonies.entity.goal.EatGoal.class);
            if (last == null || !far || held || Math.sqrt(here.distSqr(last)) >= HEADWAY_MOVE) {
                anchor.put(id, here);
                anchorSince.put(id, now);
                continue;
            }
            if (now - anchorSince.getOrDefault(id, now) > NO_HEADWAY && ants.size() > 1) {
                say(shortId(id) + " leaves the column, stuck: no headway for " + NO_HEADWAY / 20 + " s and no reason to stand, and goes home: "
                        + who(ant, stage == Stage.HOMING ? back : out));
                lost.merge("stuck", 1, Integer::sum);
                ants.remove(ant);
                letGo(id, "stuck: no headway"); // (home along the trail by itself, as the hurt do)
            }
        }
    }

    private void end(String why) {
        say("the raid is over: " + why + ", " + cameHome + " came home, " + carried + " termite(s) carried"
                + (hiddenCatch > 0 ? " and " + hiddenCatch + " by the raiders going as numbers" : "")
                + (lost.isEmpty() ? "" : ", lost on the way: " + lost));
        stage = Stage.DONE;
        ended = why;
    }

    /**
     * The raid test bench, B7: the column could not be worked out (its update failed): the raid is given up where it is,
     * every raider let go to walk home along the trail by itself and every fight for the column ended, so no raider is
     * left holding an order nobody updates (one guess at BUGS 111, the attack that never ended).
     */
    void abandon(ServerLevel level, String why) {
        for (UUID id : List.copyOf(members)) {
            if (level.getEntity(id) instanceof MatabeleEntity ant && ant.fightOrderBy() == this) {
                ant.endFightOrder(this);
            }
            letGo(id, "the raid was given up");
        }
        end("given up: " + why);
    }

    /** Why the column ended, and who left it on the way (tests and the log). */
    public String endedWhy() {
        return ended + (lost.isEmpty() ? "" : ", lost on the way: " + lost);
    }

    private void next(Stage to, long now) {
        say("on to " + to.name().toLowerCase() + " after " + (now - stageSince) / 20 + " s, " + members.size() + " real raider(s)"
                + (carried > 0 ? ", " + carried + " termite(s) carried" : ""));
        rank.clear();
        nextRank = 0;
        anchor.clear();
        anchorSince.clear();
        castingSince = -1;
        castGap = null;
        casters.clear();
        castTotal = 0;
        stage = to;
        stageSince = now;
        head = 0.0;
        frontAt = 0.0;
    }

    /**
     * The raid forms: the scout home from its find runs to the nest's mouth (the trail's start, beside the entrance) and
     * calls the raiders out there (real Megaponera recruit with a pheromone at and in the nest: Longhurst and Howse 1979,
     * Hoelldobler et al. 1994; it called deep in the nest until 2026-11-07, and a tall dead mound's chimney left it climbing
     * back out some 20 s while the raiders already out got ahead of it). Once it has called (or could not get there) the
     * march begins at once (the user's pick of 2026-11-07, "unroll out of the nest": "have the scout immediately start
     * walking after he calls for them"): no milling on the mound, no waiting for anyone. The scout walks straight off along
     * its trail and each raider, as it comes out of the nest, takes the next place at the back of the ribbon ({@link #march}).
     */
    private void form(ServerLevel level, List<MatabeleEntity> ants, long now) {
        MatabeleEntity scout = null;
        for (MatabeleEntity ant : ants) {
            if (ant.getUUID().equals(leader)) {
                scout = ant;
            }
        }
        if (!recruitLooked) {
            recruitLooked = true;
            recruitAt = place(level, out, 0.0, 0.0);
        }
        boolean there = scout == null || recruitAt == null || now - stageSince >= RECRUIT_MOST
                || flat(scout.blockPosition(), recruitAt) <= 1.5 && Math.abs(scout.getY() - recruitAt.getY()) <= 1.5;
        if (!there) {
            for (MatabeleEntity ant : ants) {
                orders.put(ant.getUUID(), ant == scout ? new Order(recruitAt, HURRY_PACE, null) : new Order(null, 1.0, null));
            }
            return;
        }
        recruitedAt = now;
        say(scout != null && recruitAt != null && now - stageSince < RECRUIT_MOST
                ? "the scout calls at the nest's mouth after " + (now - stageSince) / 20 + " s and leads off at once: the column unrolls out of the nest behind it"
                : "the scout did not get to the nest's mouth in " + (now - stageSince) / 20 + " s: it leads off all the same, the raiders following it out");
        next(Stage.MARCHING, now);
    }

    /** Tests: when the scout called at the nest's mouth (or gave up getting there), game time; -1 not yet. */
    public long recruitedAt() {
        return recruitedAt;
    }

    /**
     * On the march, out or home: the ribbon (the user's pick of 2026-11-07 from the march viewer, formation B with exit 2,
     * "unroll out of the nest"). The front walks the trail ahead of itself and never stops, a little faster than the
     * column ({@link #FRONT_PACE}), so it stays in front. Every other raider has a place in the ribbon: rows behind the
     * front, {@code spacing} apart, of {@code abreast} side by side and one fewer by turns (3, 2, 3, 2, ...), each ant in
     * its own lane in its row. The places are given in the order the ants come out of the nest (the first out nearest the
     * front), so the column unrolls out of the hole behind its scout; on the way home, in the order they stand along the
     * way home. Each place slides along the trail with the front; an ant behind its place hurries, one ahead of it walks on
     * slower: nobody is ever told to stand while the column marches. The one halt kept: a gap in the scent trail (a player
     * broke it), where the front casts about and the rest close up behind it. A raider still in the nest comes up to the
     * trail's start. At the route's end true (the next stage).
     *
     * <p>In five pieces: who is out and how far each has walked ({@link #walkedAlong}), the order of the line
     * ({@link #rankLine}), the rows and their places ({@link #ribbon}), each raider's order ({@link #raiderOrder}, its lane
     * by {@link #laneOrder}), and the ones still coming out of the nest ({@link #comeOut}).
     */
    private boolean march(ServerLevel level, List<MatabeleEntity> ants, Route route, long now) {
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
        // (off the hump, and a block past its foot, on the way out; the way home has no hump to come down)
        double settle = route == out ? hillEnd(level, route) + 1.0 : 0.0;
        rankLine(route, line, at, settle);
        double spacing = casting ? numbers.spacing() * CLOSE_UP : numbers.spacing();
        double frontPlace = casting ? Math.min(frontW, route.walked(castGap.near() - GAP_STOP)) : frontW;
        MarchStep step = new MarchStep(route, frontW, spacing, casting, settle, now);
        Ribbon ribbon = ribbon(step, line, at, frontPlace);
        BlockPos ahead = point(route, Math.min(route.length(), frontAt + 4.0));
        if (casting) {
            orders.put(front.getUUID(), new Order(castSpot(level, route, 0, now), 1.0, point(route, castGap.far())));
        } else {
            if (frontAt >= head - LEAD_STEP / 2) {
                head = Math.min(route.length(), frontAt + LEAD_STEP);
            }
            orders.put(front.getUUID(), Order.marching(place(level, route, head, 0.0), ribbon.frontPace(), ahead, Vec3.ZERO));
        }
        for (int k = 0; k < line.size(); k++) {
            MatabeleEntity ant = line.get(k);
            orders.put(ant.getUUID(), raiderOrder(level, step, ribbon, ant, k, at.get(ant.getUUID())));
        }
        comeOut(level, route, front, notOut, ahead, now);
        sayMarch(route, front, line, notOut, now);
        return false;
    }

    /** One update of the march: the route, how far the front has walked along it, the gap between rows, whether it is held up at a broken trail, where the hump ends (along; 0 for none), the time. */
    private record MarchStep(Route route, double frontW, double spacing, boolean casting, double settle, long now) {
    }

    /**
     * The ribbon worked out for one update: each raider's row and its side in the row (blocks right of the trail), each
     * row's lead (the one of it farthest along), whether each is off the hump and with its row, where each row really
     * stands and its place, and the front's pace.
     */
    private record Ribbon(int[] rowOf, double[] sideOf, UUID[] leadOf, boolean[] settled, double[] rowAt, double[] rowPlace,
                          double frontPace) {
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
     * The order of the line (sorted in place): each ant out gets the next place at the back as it comes out (several at
     * once: the one farthest along first). Down the hump the ants pass one another (three lanes, some quicker down a step
     * than others), so until each is off it (and a block past its foot) the places among those still on it follow where
     * they really are: in the order they came out, an ant down first had to slow at the foot for the ones it passed, and
     * the foot jammed.
     */
    private void rankLine(Route route, List<MatabeleEntity> line, Map<UUID, Double> at, double settle) {
        line.stream().filter(ant -> !rank.containsKey(ant.getUUID()))
                .sorted(java.util.Comparator.comparingDouble(ant -> -at.get(ant.getUUID())))
                .forEach(ant -> rank.put(ant.getUUID(), nextRank++));
        List<MatabeleEntity> unsettled = line.stream().filter(ant -> alongOf(route, ant) < settle).toList();
        if (unsettled.size() > 1) {
            List<Integer> ranks = unsettled.stream().map(ant -> rank.get(ant.getUUID())).sorted().toList();
            List<MatabeleEntity> byPlace = new ArrayList<>(unsettled);
            byPlace.sort(java.util.Comparator.comparingDouble(ant -> -at.get(ant.getUUID())));
            for (int i = 0; i < byPlace.size(); i++) {
                rank.put(byPlace.get(i).getUUID(), ranks.get(i));
            }
        }
        line.sort(java.util.Comparator.comparingInt(ant -> rank.get(ant.getUUID())));
    }

    /**
     * The rows and their places. Rows of {@code abreast} and one fewer by turns, in the line's order, the last row centred.
     * Each row's place: its own share of the column behind the front, but never more than a row's gap ahead of the row
     * before it as that row really stands (adapting the ribbon to the ground: down a tall dead mound's chimney and wall
     * the raiders come out a second or more apart, and with places counted from the front alone each ran for its own far
     * place and they came strung out one by one; so each row forms round its lead, and the rows close up behind one
     * another, wherever the column's head has got to). The front keeps its little lead over the ribbon, not over the
     * ground: while the ribbon behind it is strung out (the raiders come down a tall dead mound's wall, or a low hump's
     * steps, slower than they walk, a row at a time) it walks on slower, never stopping, so the rows close up behind it
     * into the ribbon instead of each trailing the one before by a block or more (the user, 2026-11-07: "adapt the
     * formations to the different mounds"; on flat ground the ribbon keeps up and it walks at its full pace).
     */
    private Ribbon ribbon(MarchStep step, List<MatabeleEntity> line, Map<UUID, Double> at, double frontPlace) {
        Route route = step.route();
        int wide = Math.max(1, numbers.abreast());
        int[] rowOf = new int[line.size()];
        double[] sideOf = new double[line.size()];
        List<Double> leadAt = new ArrayList<>();
        List<UUID> leadOf = new ArrayList<>();
        int row = -1;
        int rowWidth = 0;
        int inRow = 0;
        for (int k = 0; k < line.size(); k++) {
            if (inRow >= rowWidth) {
                row++;
                inRow = 0;
                rowWidth = Math.min(row % 2 == 0 ? wide : Math.max(1, wide - 1), line.size() - k); // (the last row centred)
                leadAt.add(Double.NEGATIVE_INFINITY);
                leadOf.add(null);
            }
            rowOf[k] = row;
            sideOf[k] = rowWidth == 1 ? 0.0 : (inRow - (rowWidth - 1) / 2.0) * LANE_GAP;
            inRow++;
            UUID id = line.get(k).getUUID();
            if (at.get(id) > leadAt.get(row)) {
                leadAt.set(row, at.get(id));
                leadOf.set(row, id);
            }
        }
        int rows = row + 1;
        // Each row as it really stands: the middle of its ants off the hump within a few blocks of its lead (one farther
        // back is a straggler, not the row). The row walks to its place as one, and the front eases for it.
        double[] rowSum = new double[rows];
        int[] rowCount = new int[rows];
        boolean[] settled = new boolean[line.size()];
        for (int k = 0; k < line.size(); k++) {
            int r = rowOf[k];
            double own = at.get(line.get(k).getUUID());
            settled[k] = alongOf(route, line.get(k)) >= step.settle() && own >= leadAt.get(r) - ROW_REACH;
            if (settled[k]) {
                rowSum[r] += own;
                rowCount[r]++;
            }
        }
        double[] rowAt = new double[rows]; // (the row's middle, or its lead while none of it is off the hump)
        for (int r = 0; r < rows; r++) {
            rowAt[r] = rowCount[r] > 0 ? rowSum[r] / rowCount[r] : leadAt.get(r);
        }
        double hillW = step.settle() > 0.0 ? route.walked(step.settle()) : 0.0;
        // How far the ribbon off the hump trails its places closed up behind the front.
        double stretch = 0.0;
        double whole = frontPlace;
        for (int r = 0; r < rows; r++) {
            whole -= rowGap(step, whole, hillW);
            if (rowCount[r] > 0) {
                stretch = Math.max(stretch, whole - rowAt[r]);
            }
        }
        double nearest = line.stream().mapToDouble(ant -> at.get(ant.getUUID())).max().orElse(0.0);
        double frontPace = Mth.clamp(FRONT_PACE - PACE_PER_BLOCK
                * (Math.max(step.frontW() - nearest - step.spacing(), stretch) - FRONT_KEEP), SLOWEST_PACE, FRONT_PACE);
        double[] rowPlace = new double[rows];
        double before = frontPlace;
        for (int r = 0; r < rows; r++) {
            double gap = rowGap(step, before, hillW);
            rowPlace[r] = before - gap;
            if (r > 0 && !step.casting()) {
                rowPlace[r] = Math.min(rowPlace[r], rowAt[r - 1] - gap); // (a gap behind the row ahead as it really is)
            }
            before = rowPlace[r];
        }
        return new Ribbon(rowOf, sideOf, leadOf.toArray(new UUID[0]), settled, rowAt, rowPlace, frontPace);
    }

    /**
     * The gap behind a row whose place is {@code before} (walked along): the column's spacing, and twice it down the
     * hump's steps (a raider climbs down a step face no faster than it walks a block, three lanes at a time; at the normal
     * gap they reached each step faster than they got down it and bunched on the tread above it), closing up to the
     * ribbon at its foot.
     */
    private static double rowGap(MarchStep step, double before, double hillW) {
        return !step.casting() && before - step.spacing() < hillW ? step.spacing() * HILL_SPACING : step.spacing();
    }

    /**
     * One raider's order on the march. Off the hump every raider walks at the front's pace, faster while its row is
     * behind its place and slower while ahead (so at whatever pace the front walks the rows end up on their places, a gap
     * apart), and more again by how far it is behind or ahead of its row's middle (so the row comes side by side). Still
     * on the hump, at a walk, a little faster or slower by its row's place: the front's easing is for the ribbon, not for
     * the hump (eased there too, they came down it slower and the front eased more). It aims a little past its place, so
     * it walks on as the place moves (never past the front, a few blocks at a time); its pace says whether it closes up
     * or lets the column come up to it. Behind a broken trail it closes up to its place and stands there (never walking
     * back); one of the casters casts about.
     */
    private Order raiderOrder(ServerLevel level, MarchStep step, Ribbon ribbon, MatabeleEntity ant, int k, double own) {
        Route route = step.route();
        UUID id = ant.getUUID();
        if (step.casting() && casters.contains(id)) {
            return new Order(castSpot(level, route, 1 + k, step.now()), 1.0, point(route, castGap.far()));
        }
        int row = ribbon.rowOf()[k];
        double rowAt = ribbon.rowAt()[row];
        double rowPlace = ribbon.rowPlace()[row];
        boolean settled = ribbon.settled()[k];
        double base = !step.casting() && settled ? ribbon.frontPace() : 1.0;
        double place = step.casting() || !settled ? rowPlace : own + (rowPlace - rowAt) + ROW_PULL * (rowAt - own);
        double placeAlong = Math.max(0.0, route.alongAtWalked(Math.max(0.0, place)));
        double sideways = ribbon.sideOf()[k];
        Vec3 shift = sideOf(route, placeAlong, sideways);
        BlockPos look = point(route, Math.min(route.length(), placeAlong + 3.0));
        boolean lead = id.equals(ribbon.leadOf()[row]);
        double pace = Mth.clamp(base + PACE_PER_BLOCK * (place - own), PASSED_PACE, lead && row == 0 ? HURRY_PACE : CATCH_UP_PACE);
        if (step.casting()) {
            double target = Math.min(place, own + WALK_AHEAD);
            return target < own + 0.3 ? new Order(null, 1.0, look)
                    : new Order(placeAhead(level, route, target, 0.0, own), pace, look, null, shift, false);
        }
        if (own > step.frontW() - step.spacing() * 0.5) {
            pace = PASSED_PACE; // (level with the front or past it: it drops back behind it, still walking)
        }
        double target = Math.min(step.frontW() - step.spacing() * 0.5, Math.max(place + CARROT, own + 1.0));
        target = Math.max(own + 0.5, Math.min(target, own + WALK_AHEAD));
        target = Mth.clamp(target, 0.0, route.walkedLength());
        return laneOrder(level, step, ant, own, target, sideways, shift, pace, look);
    }

    /**
     * A marching raider's walk to {@code target} (walked along) in its lane ({@code sideways} blocks right of the trail,
     * {@code shift} the flat offset for its legs). Three cases:
     * <ul>
     * <li>On (or by) a steep hump the lanes are whole blocks, flat treads too, each its own way down, so 3 climb side by
     * side (in lanes inside one block they climbed one after another and the rest queued at the lip, and on a low hump's
     * tread they all came to the same block's edge and stepped down it one at a time); a row of 2 takes the middle block
     * and the one beside it (on blocks either side of the middle they were 2 apart).</li>
     * <li>Its lane is worked out from the middle of each cell its route runs through, so the route must run in the row of
     * blocks the ant is in: a route from the block beside its lane's block to a place in the trail's row ran on in the
     * ant's row, its lane half a block farther out still, and stepped over only at its end, which it never reached as the
     * place moved on (the outer ant of a row of 3 walked a block out of its lane the whole march). So more than a block
     * out of its lane ({@link #OUT_OF_LANE}) it steps straight back over, its place the trail's cell abreast of it; in the
     * block beside its lane's it walks on in its own row of blocks, at the edge nearest its lane, never stopping to step
     * over (ants stepping sideways stopped the ribbon and those behind came up 4 abreast).</li>
     * <li>Otherwise it walks on in its lane.</li>
     * </ul>
     */
    private Order laneOrder(ServerLevel level, MarchStep step, MatabeleEntity ant, double own, double target, double sideways,
                            Vec3 shift, double pace, BlockPos look) {
        Route route = step.route();
        double along = alongOf(route, ant);
        if (steep(route, along) || steep(route, route.alongAtWalked(target)) || route == out && along < step.settle()) {
            double lane = Math.round(sideways / LANE_GAP);
            return Order.marching(placeAhead(level, route, target, lane, own), pace, look, Vec3.ZERO);
        }
        BlockPos abreast = place(level, route, along, 0.0);
        if (Math.abs(sideOfTrail(ant.position()) - sideways) > OUT_OF_LANE) {
            return Order.marching(abreast, pace, look, shift);
        }
        BlockPos laneCell = BlockPos.containing(abreast.getX() + 0.5 + shift.x, abreast.getY(), abreast.getZ() + 0.5 + shift.z);
        BlockPos mine = ant.blockPosition();
        if (mine.getX() != laneCell.getX() || mine.getZ() != laneCell.getZ()) {
            double myRow = sideOfTrail(new Vec3(mine.getX() + 0.5, ant.getY(), mine.getZ() + 0.5));
            double alongTo = Math.max(0.0, route.alongAtWalked(target));
            return Order.marching(placeAhead(level, route, target, Math.round(myRow), own), pace, look,
                    sideOf(route, alongTo, sideways - myRow));
        }
        return Order.marching(placeAhead(level, route, target, 0.0, own), pace, look, shift);
    }

    /** The ribbon: a marching raider more than this far (blocks) from its lane steps straight back over to it. */
    private static final double OUT_OF_LANE = 1.05;

    /**
     * The raiders not yet out of the nest come up to the trail's start: behind the scout while it is still coming out,
     * then hurrying. One still in the nest {@code formTicks} after the column left stays home.
     */
    private void comeOut(ServerLevel level, Route route, MatabeleEntity front, List<MatabeleEntity> notOut, BlockPos ahead, long now) {
        BlockPos lineStart = place(level, route, 0.0, 0.0);
        boolean frontOut = isOut(level, front);
        for (MatabeleEntity ant : notOut) {
            if (route == out && now - stageSince > numbers.formTicks() && colony.nest().isInside(level, ant.blockPosition())) {
                say(shortId(ant.getUUID()) + " stays home, still in the nest " + (now - stageSince) / 20 + " s after the column left: " + who(ant, out));
                drop(ant.getUUID());
                continue;
            }
            orders.put(ant.getUUID(), new Order(lineStart, frontOut ? CATCH_UP_PACE : 1.0, ahead));
        }
    }

    /**
     * The ribbon: where the route's first climbs and drops end (along; 0 for a level start): the last change of the
     * ground's height before 3 level blocks, looked for in its first 24 blocks.
     */
    private double hillEnd(ServerLevel level, Route route) {
        if (hillRoute != route) {
            hillRoute = route;
            double end = 0.0;
            int[] before = laneHeights(level, route, 0.0);
            for (double t = 0.5; t <= Math.min(route.length(), 24.0); t += 0.5) {
                int[] here = laneHeights(level, route, t);
                if (!java.util.Arrays.equals(here, before)) {
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
        return new int[] {place(level, route, along, -1.0).getY(), place(level, route, along, 0.0).getY(),
                place(level, route, along, 1.0).getY()};
    }

    /** The ribbon: whether the trail climbs or drops 2 blocks or more within a block behind or 3 ahead of this place along it. */
    private static boolean steep(Route route, double along) {
        double here = route.at(along)[2];
        for (double k : new double[] {-1.0, 1.0, 2.0, 3.0}) {
            if (Math.abs(route.at(Mth.clamp(along + k, 0.0, route.length()))[2] - here) >= 2.0) {
                return true;
            }
        }
        return false;
    }

    /** The ribbon: the flat offset {@code side} blocks to the right of the trail at this place along it (a lane for the legs). */
    private static Vec3 sideOf(Route route, double along, double side) {
        if (side == 0.0) {
            return Vec3.ZERO;
        }
        double[] d = route.direction(along);
        return new Vec3(-d[1] * side, 0.0, d[0] * side);
    }

    /** The diary: every {@link #WAIT_SAY_EVERY} ticks of the march, where the front is, how many are out and in the ribbon. */
    private void sayMarch(Route route, MatabeleEntity front, List<MatabeleEntity> line, List<MatabeleEntity> notOut, long now) {
        if (now - marchSaidAt < WAIT_SAY_EVERY) {
            return;
        }
        marchSaidAt = now;
        MatabeleEntity hindmost = line.stream().min(java.util.Comparator.comparingDouble(ant -> alongOf(route, ant))).orElse(null);
        say("marching " + (now - stageSince) / 20 + " s: the front " + who(front, route) + "; " + line.size() + " in the ribbon, "
                + notOut.size() + " still coming out; the hindmost " + (hindmost == null ? "-" : who(hindmost, route)));
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
        on.sort(java.util.Comparator.comparingDouble(p -> p[0]));
        gapAlong = new double[on.size()];
        gapIndex = new int[on.size()];
        for (int k = 0; k < on.size(); k++) {
            gapAlong[k] = on.get(k)[0];
            gapIndex[k] = (int) on.get(k)[1];
        }
    }

    /** This raider's place along this route, worked out once an update (finding 9). */
    private double alongOf(Route route, MatabeleEntity ant) {
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
            line.stream().sorted(java.util.Comparator.comparingDouble(ant -> -alongOf(route, ant)))
                    .limit(CASTERS - 1).forEach(ant -> casters.add(ant.getUUID()));
            say("the scent trail is broken ahead, a gap of " + Math.round(width * 10) / 10.0 + " blocks at " + Math.round(gap.near() * 10) / 10.0
                    + " along: the front stops and casts about");
        }
        long cast = now - castingSince;
        boolean pickedUp = width <= trailNumbers.castReach() && cast >= trailNumbers.castTicks() + Math.round(width * trailNumbers.castPerBlock());
        boolean givenUp = !pickedUp && cast >= trailNumbers.castGiveTicks();
        boolean scoutLeads = givenUp && formerScoutIn();
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
            say("the trail is lost at a gap of " + Math.round(width * 10) / 10.0 + " blocks after casting " + cast / 20
                    + " s, and no former scout walks with them: the raid turns home");
            return false;
        }
        int laid = trail.mend(level, p -> {
            double[] near = route.nearest(p.getX() + 0.5, p.getZ() + 0.5);
            return near[1] <= ON_ROUTE && near[0] >= gap.near() - 1.0 && near[0] <= gap.far() + 1.0;
        });
        gapsCrossed++;
        say((pickedUp ? "the trail is picked up past the gap after casting " : givenUp && route == back ? "home past the gap anyway after casting "
                : "a former scout leads on across the gap after casting ") + cast / 20 + " s; new scent laid over " + laid + " point(s)");
        head = Math.min(route.length(), gap.far() + LEAD_STEP);
        return false;
    }

    /** R4: whether a former scout walks in the column: one of the colony's scouts (an ant that really scouted). */
    private boolean formerScoutIn() {
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        return raids != null && members.stream().anyMatch(raids::isVeteran);
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
        return spot != null ? spot : place(level, route, Math.max(0.0, gap.near() - GAP_STOP), 0.0);
    }

    /**
     * R3: whether this raider is out of the nest: not in it, and not down the hole under the entrance (a raider down a
     * dead mound's chimney is right under the line's start, flat, but not out).
     */
    public boolean isOutForTests(ServerLevel level, MatabeleEntity ant) {
        return isOut(level, ant);
    }

    private boolean isOut(ServerLevel level, MatabeleEntity ant) {
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

    /** A little short of the find the column halts and bunches up; once most are there (or after a while), the attack. */
    private void gather(ServerLevel level, List<MatabeleEntity> ants, long now) {
        int there = bunch(level, ants);
        if (there >= Math.ceil(ants.size() * MOST_THERE) || now - stageSince >= numbers.gatherTicks()) {
            next(Stage.ATTACKING, now);
            attackedAt = now;
            gatheredAtAttack = there;
            takeHidden(level);
            for (MatabeleEntity ant : ants) {
                sendIn(ant);
            }
        }
    }

    /**
     * M8 step 3d: as the attack starts, the raiders going as numbers break into the galleries round the spot and take the
     * termites feeding under the mud there ({@link RaidCatch}), if the termite colony still feeds there; brought home with
     * the column ({@code Raids.raidHome}).
     */
    private void takeHidden(ServerLevel level) {
        if (numbersAlong <= 0) {
            return;
        }
        int reach = colony.profile().section(SavannaNumbers.AWAY_RAIDS).orElse(SavannaNumbers.AwayRaidNumbers.DEFAULT).termiteReach();
        Colony termites = RaidCatch.termitesAt(level, find.at(), reach);
        if (termites == null || termites.keepsOff(level, find.at())) {
            say("the " + numbersAlong + " raiders going as numbers find no termites feeding under the mud here"
                    + (termites == null ? " (no termite colony near)" : " (the termites keep off the spot)"));
            return;
        }
        int wanted = RaidCatch.couldCarry(colony, numbersAlong, colony.random());
        int most = RaidCatch.mostToTake(colony, termites);
        hiddenCatch = Math.min(wanted, most);
        hiddenFrom = termites.getId();
        int countLost = RaidCatch.take(level, termites, hiddenCatch);
        say("the " + numbersAlong + " raiders going as numbers take " + hiddenCatch + " termite(s) from under the mud (of " + wanted
                + " they could carry, at most " + most + "); the termite colony's count lost " + countLost + ", each counted termite standing for "
                + termites.population().standsFor());
    }

    /** M8 step 3d: the termites the raiders going as numbers took at the attack (brought home with the column). */
    public int hiddenCatch() {
        return hiddenCatch;
    }

    /** M8 step 3d: the termite colony the raiders going as numbers took termites from, or null. */
    @Nullable
    UUID hiddenFrom() {
        return hiddenFrom;
    }

    /** Everyone to a place round the gathering place, a little back toward home; how many are there now. */
    private int bunch(ServerLevel level, List<MatabeleEntity> ants) {
        BlockPos middle = point(out, Math.max(0.0, out.length() - 1.5));
        int there = 0;
        for (int i = 0; i < ants.size(); i++) {
            MatabeleEntity ant = ants.get(i);
            if (flat(ant.blockPosition(), middle) <= NEAR) {
                there++;
            }
            // Step 3Q, Q5: its place by its own number, given once (as the milling places, BUGS 101), round a ring sized
            // for the whole column: one raider dying, fighting or out of sight moves nobody else's place.
            int n = formIndex.computeIfAbsent(ant.getUUID(), k -> formIndex.size());
            double angle = n * 2.39996;
            double radius = n == 0 ? 0.0 : 1.0 + 1.5 * Math.sqrt(n / (double) Math.max(1, formIndex.size()));
            if (flat(ant.blockPosition(), middle) > STRAGGLER_FAR) {
                // (a straggler far behind, left by the march's time limit or a fight: it walks the trail up, not straight
                // across; the raid code review, findings 2 and 17)
                double along = alongOf(out, ant);
                orders.put(ant.getUUID(), new Order(place(level, out, Math.min(out.length(), along + LEAD_STEP), 0.0), HURRY_PACE, find.at()));
                continue;
            }
            BlockPos spot = standAt(level, middle.getX() + 0.5 + Math.cos(angle) * radius, middle.getZ() + 0.5 + Math.sin(angle) * radius,
                    middle.getY());
            orders.put(ant.getUUID(), new Order(spot, 1.0, find.at()));
        }
        return there;
    }

    /** One raider sent in to fight round the find (step 2's fight: the majors break in, the minors go in after). */
    private void sendIn(MatabeleEntity ant) {
        if (ant.getFightSpot() != null && ant.fightOrderBy() != this) {
            return; // (called away by another fight: sent in once that is over, as a straggler)
        }
        // (the order is the column's own: only the column ends it, the nest's defence keeps off it; finding 4)
        ant.orderToFight(find.at(), attackedAt + numbers.fightTicks(), com.antcolonies.entity.goal.FightGoal.SPOT_REACH, this);
        sentIn.add(ant.getUUID());
    }

    /** Whether this raider holds the column's own fight order now (the nest's defence keeps off it). */
    public boolean fightsForTheColumn(AbstractAntEntity ant) {
        return ant.getFightSpot() != null && ant.fightOrderBy() == this;
    }

    /**
     * The fight: every raider at the gathering place is sent in (stragglers as they arrive); one whose fight is over
     * waits round the gathering place. Once nobody fights any more (or the fight's time is up), they regroup.
     */
    private void attack(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos middle = point(out, Math.max(0.0, out.length() - 1.5));
        boolean fighting = false;
        for (MatabeleEntity ant : ants) {
            if (!sentIn.contains(ant.getUUID()) && flat(ant.blockPosition(), middle) <= NEAR + 2 && now - attackedAt < numbers.fightTicks()) {
                sendIn(ant);
            }
            if (ant.getFightSpot() != null) {
                fighting = true;
            }
        }
        bunch(level, ants); // (the places of those not fighting: stragglers on their way, and those done)
        boolean stragglers = ants.stream().anyMatch(ant -> !sentIn.contains(ant.getUUID()));
        // R7: the attack is over once no termite has been about the spot for quiet_ticks (the R1 diary: each raider
        // searched round on its own for minutes after the last termite, the attack lasting about 4 minutes, 4 game hours)
        if (termitesAbout(level, ants)) {
            termiteSeenAt = now;
        }
        long quietFor = now - Math.max(attackedAt, termiteSeenAt);
        if (quietFor >= hoursSound().quietTicks()) {
            say("no termite about the spot for " + quietFor / 20 + " s: the attack is over");
            endFights(ants);
            next(Stage.COLLECTING, now);
            return;
        }
        if (!fighting && (!stragglers || now - attackedAt >= numbers.fightTicks()) || now - attackedAt >= numbers.fightTicks() + 200) {
            next(Stage.COLLECTING, now); // step 3c: the stung termites gathered first
        }
    }

    // --- step 3c, home with the prey ------------------------------------------------------------------------------

    /**
     * The stung termites lying round the spot (within {@code collect_reach} of the find): paralysed insects not of this
     * colony's kind, the termite workers before the soldiers (a raid is for the workers, Frank 2017), then the nearest to
     * the find first.
     */
    private List<AbstractAntEntity> preyRound(ServerLevel level) {
        if (preyNow == null) {
            preyNow = findPrey(level); // (once an update: the raid code review, finding 9)
        }
        return preyNow;
    }

    private List<AbstractAntEntity> findPrey(ServerLevel level) {
        double reach = carry.collectReach();
        BlockPos at = find.at();
        long now = level.getGameTime();
        return level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(at).inflate(reach, 6.0, reach),
                        insect -> insect.isAlive() && insect.isParalysed() && !(insect instanceof MatabeleEntity)
                                && !insect.isPinned() && flat(insect.blockPosition(), at) <= reach
                                && noWay.getOrDefault(insect.getUUID(), Long.MIN_VALUE) <= now).stream()
                .sorted(java.util.Comparator.<AbstractAntEntity>comparingInt(insect -> insect instanceof TermiteSoldierEntity ? 1 : 0)
                        .thenComparingDouble(insect -> insect.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)))
                .toList();
    }

    /** Whether this raider has room in its jaws for another termite (empty, or holding fewer than it can carry). */
    private static boolean hasRoom(MatabeleEntity ant) {
        int held = MatabeleLoads.termitesHeldBy(ant);
        return held > 0 ? held < ant.termitesItCanCarry() : !ant.hasFullMandibles();
    }

    /**
     * The stung termites gathered: each raider with room is given the nearest one nobody has (it walks there and picks it
     * up, {@link #pickUp}); the rest wait round the gathering place. Done once none is left untaken (or after
     * {@code collect_ticks}): home, or, if nothing was carried and the raid has not spread out yet, out round the spot.
     */
    private void collect(ServerLevel level, List<MatabeleEntity> ants, long now) {
        List<AbstractAntEntity> prey = preyRound(level);
        Map<UUID, AbstractAntEntity> left = new HashMap<>();
        for (AbstractAntEntity insect : prey) {
            left.put(insect.getUUID(), insect);
        }
        taking.entrySet().removeIf(entry -> !left.containsKey(entry.getValue())); // (taken, dead, or no way: free again)
        Set<UUID> taken = new HashSet<>(taking.values());
        bunch(level, ants); // (the places of those with no termite to fetch)
        boolean anyFetching = false;
        for (MatabeleEntity ant : ants) {
            UUID mine = taking.get(ant.getUUID());
            if (mine == null && hasRoom(ant)) {
                AbstractAntEntity nearest = prey.stream().filter(insect -> !taken.contains(insect.getUUID()))
                        .min(java.util.Comparator.comparingDouble(insect -> insect.distanceToSqr(ant))).orElse(null);
                if (nearest != null) {
                    mine = nearest.getUUID();
                    taking.put(ant.getUUID(), mine);
                    taken.add(mine);
                }
            }
            AbstractAntEntity insect = mine == null ? null : left.get(mine); // (the prey looked up once, finding 9)
            if (insect != null) {
                orders.put(ant.getUUID(), new Order(insect.blockPosition(), 1.0, insect.blockPosition(), mine));
                anyFetching = true;
            }
        }
        if (!anyFetching || now - stageSince >= carry.collectTicks()) {
            taking.clear();
            boolean spreadNow = carried == 0 && !spread && !duskDone && now - stageSince < carry.collectTicks();
            foundNothing = carried == 0 && !spreadNow && !duskDone; // (at the spot and nothing to bring: an empty check of it; R7, never at dusk)
            next(spreadNow ? Stage.SPREADING : Stage.REGROUPING, now);
            spread |= spreadNow;
        }
    }

    /**
     * A raider at its termite picks it up: the termite leaves the world (and its colony), one more in the raider's
     * jaws. False if it is not there to take (gone, taken, too far, no room).
     */
    public boolean pickUp(ServerLevel level, MatabeleEntity ant, UUID prey) {
        if (!prey.equals(taking.get(ant.getUUID())) || !(level.getEntity(prey) instanceof AbstractAntEntity insect)
                || !insect.isAlive() || !insect.isParalysed() || insect.distanceTo(ant) > PICK_REACH + 0.5 || !hasRoom(ant)) {
            return false;
        }
        int held = MatabeleLoads.termitesHeldBy(ant);
        insect.carriedOff(level);
        ant.setLoad(MatabeleLoads.termites(held + 1));
        taking.remove(ant.getUUID());
        carried++;
        if (held == 0) {
            carriers++;
        }
        level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 1.6F);
        return true;
    }

    /** How long a raider stands after each pick-up (ticks). */
    public int pickTicks() {
        return carry.pickTicks();
    }

    /** The raider found no way to its termite: someone else may try it, a little later. */
    public void noWayTo(ServerLevel level, MatabeleEntity ant, UUID prey) {
        if (prey.equals(taking.get(ant.getUUID()))) {
            taking.remove(ant.getUUID());
        }
        noWay.put(prey, level.getGameTime() + NO_WAY_TICKS);
    }

    /**
     * Nobody stung at the spot: the raiders spread out round it up to {@code spread_reach} blocks (each to its own place
     * on a ring), and one that sees a termite out in the open within {@code spread_see} blocks goes for it. After
     * {@code spread_ticks}, or once something lies stung and nobody fights, they gather what there is.
     */
    private void spreadOut(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos at = find.at();
        // (the raid code review, finding 9: one look round for the whole raid, each ant then taking those near itself)
        AABB all = null;
        for (MatabeleEntity ant : ants) {
            if (ant.getFightSpot() == null) {
                AABB own = ant.getBoundingBox().inflate(carry.spreadSee(), 3.0, carry.spreadSee());
                all = all == null ? own : all.minmax(own);
            }
        }
        List<AbstractAntEntity> inTheOpen = all == null ? List.of() : level.getEntitiesOfClass(AbstractAntEntity.class, all,
                insect -> insect.isAlive() && !insect.isDowned() && !(insect instanceof MatabeleEntity)
                        && !colony.getId().equals(insect.getColonyId()) && level.canSeeSky(insect.blockPosition()));
        for (int i = 0; i < ants.size(); i++) {
            MatabeleEntity ant = ants.get(i);
            if (ant.getFightSpot() != null) {
                continue; // (going for one)
            }
            AABB sees = ant.getBoundingBox().inflate(carry.spreadSee(), 3.0, carry.spreadSee());
            AbstractAntEntity seen = inTheOpen.stream().filter(insect -> insect.getBoundingBox().intersects(sees))
                    .min(java.util.Comparator.comparingDouble(insect -> insect.distanceToSqr(ant))).orElse(null);
            if (seen != null) {
                ant.orderToFight(seen.blockPosition(), now + carry.spreadTicks(), com.antcolonies.entity.goal.FightGoal.SPOT_REACH, this);
                continue;
            }
            int n = formIndex.computeIfAbsent(ant.getUUID(), k -> formIndex.size()); // (its own number, Q5: places hold still)
            double angle = n * 2.39996;
            double radius = 3.0 + (carry.spreadReach() - 3.0) * ((n * 7) % 10) / 10.0;
            BlockPos spot = standAt(level, at.getX() + 0.5 + Math.cos(angle) * radius, at.getZ() + 0.5 + Math.sin(angle) * radius, at.getY());
            orders.put(ant.getUUID(), new Order(spot, 1.0, at));
        }
        boolean fighting = ants.stream().anyMatch(ant -> ant.getFightSpot() != null);
        boolean stung = !preyRound(level).isEmpty();
        if (!fighting && (stung || now - stageSince >= carry.spreadTicks()) || now - stageSince >= carry.spreadTicks() + 200) {
            foundNothing = !stung && carried == 0;
            next(stung ? Stage.COLLECTING : Stage.REGROUPING, now);
        }
    }

    /** After the fight they gather again, then walk home as a column. */
    private void regroup(ServerLevel level, List<MatabeleEntity> ants, long now) {
        int there = bunch(level, ants);
        if (there >= Math.ceil(ants.size() * MOST_THERE) || now - stageSince >= numbers.regroupTicks()) {
            next(Stage.HOMING, now);
        }
    }

    /** Home along the trail as a column, the scout in front; at the entrance each one leaves the column and goes in. */
    private void home(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos entrance = colony.getCenter();
        for (MatabeleEntity ant : List.copyOf(ants)) {
            if (flat(ant.blockPosition(), entrance) <= HOME && Math.abs(ant.getY() - entrance.getY()) <= HOME
                    || colony.nest().isInside(level, ant.blockPosition())) {
                drop(ant.getUUID());
                ants.remove(ant);
                cameHome++;
            }
        }
        if (ants.isEmpty() || now - stageSince >= numbers.homeTicks()) {
            // (any left out now go home along the trail by themselves: the raid code review, finding 2, BUGS 88)
            trail.walked(now); // (R4: they laid scent on the way home too)
            List<UUID> leftOut = List.copyOf(members);
            end(ants.isEmpty() ? "all home" : "home time up with " + ants.size() + " out, nearest "
                    + ants.stream().mapToDouble(ant -> flat(ant.blockPosition(), entrance)).min().orElse(0) + " flat from the entrance "
                    + entrance.toShortString() + ", one at " + alongOf(back, ants.get(0)) + " of " + back.length() + " along the way home");
            for (UUID id : leftOut) {
                letGo(id, "the column's time home ran out");
            }
            return;
        }
        if (!memberSet.contains(leader)) {
            leader = nextLeader();
        }
        if (rank.isEmpty()) {
            sortHomeward(ants); // (R7: the ant nearest home leads; the ribbon's places then by where each stands, kept)
        }
        ants.sort(java.util.Comparator.comparingInt(ant -> members.indexOf(ant.getUUID())));
        double farthest = ants.stream().mapToDouble(ant -> alongOf(back, ant)).max().orElse(0.0);
        if (homeBestAt < stageSince) {
            homeBest = farthest;
            homeBestCame = cameHome;
            homeBestAt = now;
            streamAll = false;
        } else if (farthest > homeBest + 1.0 || cameHome > homeBestCame) {
            homeBest = Math.max(homeBest, farthest);
            homeBestCame = cameHome;
            homeBestAt = now;
        } else if (!streamAll && now - homeBestAt >= HOME_STALL) {
            streamAll = true;
            say("no headway home for " + (now - homeBestAt) / 20 + " s (the farthest along " + Math.round(farthest) + " of "
                    + Math.round(back.length()) + "): the line breaks up and each walks in by its own way");
        }
        // R7 (the slow walk home): near the nest the column breaks up and every ant streams in by itself, side by side
        // (real raiders crowd into the entrance; kept in the line, they climbed a dead mound's hump one at a time, each
        // waiting for the one ahead at its foot, just outside the home reach).
        // (to the first cell beside the entrance an ant can stand in, as the march's line starts there: in a dead mound
        // the entrance itself is the chimney's open mouth, no place to walk to)
        BlockPos doorstep = place(level, back, back.length(), 0.0);
        List<MatabeleEntity> line = new ArrayList<>();
        for (MatabeleEntity ant : ants) {
            if (streamAll || flat(ant.blockPosition(), entrance) <= HOME + STREAM_IN) {
                orders.put(ant.getUUID(), new Order(doorstep, 1.0, null));
            } else {
                line.add(ant);
            }
        }
        if (!line.isEmpty() && march(level, line, back, now)) {
            for (MatabeleEntity ant : line) {
                orders.put(ant.getUUID(), new Order(entrance, 1.0, null)); // (the front is at the entrance: the rest come in behind it)
            }
        }
        if (carriers > 0) {
            // Step 3c: laden, the column walks home a little slower (my pick).
            orders.replaceAll((id, order) -> order.slower(carry.ladenPace()));
        }
        if (now - homeSaidAt >= WAIT_SAY_EVERY) {
            // R7, the diary: how the walk home goes, every 10 s (the R1 diary found it slow): who is home, who is hindmost
            homeSaidAt = now;
            MatabeleEntity hindmost = ants.stream().min(java.util.Comparator.comparingDouble(ant -> alongOf(back, ant))).orElse(null);
            say("on the way home " + (now - stageSince) / 20 + " s: " + cameHome + " home, " + ants.size() + " out, the front "
                    + ants.stream().filter(ant -> ant.getUUID().equals(leader)).findFirst().map(ant -> who(ant, back)).orElse("?")
                    + "; the hindmost " + (hindmost == null ? "?" : who(hindmost, back)) + "; the way home " + Math.round(back.length()) + " blocks from "
                    + point(back, 0.0).toShortString() + " to " + point(back, back.length()).toShortString() + ", the entrance " + colony.getCenter().toShortString());
        }
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
    private static BlockPos placeAhead(ServerLevel level, Route route, double walked, double side, double own) {
        double end = route.walkedLength();
        BlockPos first = null;
        for (double t = Math.min(walked, end); ; t = Math.min(end, t + 0.5)) {
            BlockPos cell = place(level, route, route.alongAtWalked(t), side);
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

    /** The cell to stand in at this place along a route, {@code side} blocks to its right; the route's own point if none. */
    static BlockPos place(ServerLevel level, Route route, double along, double side) {
        double[] p = route.at(along);
        if (side != 0.0) {
            double[] d = route.direction(along);
            BlockPos beside = standAt(level, p[0] - d[1] * side, p[1] + d[0] * side, (int) Math.round(p[2]));
            if (beside != null) {
                return beside;
            }
        }
        BlockPos on = standAt(level, p[0], p[1], (int) Math.round(p[2]));
        if (on == null) {
            // (nothing near the line's height: the ground's top there, if it is not far off, never a cell in the ground)
            BlockPos top = com.antcolonies.colony.GroundTop.cell(level,
                    BlockPos.containing(p[0], p[2], p[1]));
            on = Math.abs(top.getY() - p[2]) <= 6 ? standAt(level, p[0], p[1], top.getY()) : null;
        }
        for (int step = 1; on == null && step <= 4; step++) {
            // (R3: nothing to stand in here, an open hole say: the nearest ground a little along the line, never the air over it)
            for (int sign : new int[] {1, -1}) {
                double[] q = route.at(Math.max(0.0, Math.min(route.length(), along + sign * step)));
                BlockPos near = standAt(level, q[0], q[1], (int) Math.round(q[2]));
                if (near != null) {
                    on = near;
                    break;
                }
            }
        }
        return on != null ? on : BlockPos.containing(p[0], p[2], p[1]);
    }

    private static BlockPos point(Route route, double along) {
        double[] p = route.at(along);
        return BlockPos.containing(p[0], p[2], p[1]);
    }

    /**
     * The raid code review, finding 17: a hop of {@code hop} blocks from {@code from} toward {@code to} along the
     * column's trail (out and home are one line), when both lie within {@link #HOP_OFF} of it and more than a hop apart
     * along it; null otherwise (the raider walks straight). A raider far behind, or told to the entrance from the far
     * end, so follows the trail's bends instead of cutting across them.
     */
    @Nullable
    public BlockPos hopAlong(ServerLevel level, BlockPos from, BlockPos to, double hop) {
        double fx = from.getX() + 0.5, fz = from.getZ() + 0.5, tx = to.getX() + 0.5, tz = to.getZ() + 0.5;
        if (out.offBy(fx, fz) > HOP_OFF || out.offBy(tx, tz) > HOP_OFF) {
            return null;
        }
        double a = out.along(fx, fz), b = out.along(tx, tz);
        if (Math.abs(b - a) <= hop) {
            return null;
        }
        return place(level, out, a + Math.signum(b - a) * hop, 0.0);
    }

    @Nullable
    private static BlockPos standAt(ServerLevel level, double x, double z, int y) {
        return RaidGround.standAt(level, x, z, y);
    }

    /** The column in words (the colony list). */
    public String describe() {
        StringBuilder line = new StringBuilder("; a raid of ").append(size).append(" (").append(members.size()).append(" real out")
                .append(numbersAlong > 0 ? ", " + numbersAlong + " as numbers" : "").append(") ").append(stage.name().toLowerCase())
                .append(" to ").append(find.at().toShortString());
        if (hiddenCatch > 0) {
            line.append(", ").append(hiddenCatch).append(" termite(s) taken by the numbers");
        }
        if (carried > 0 || spread) {
            line.append(", carrying ").append(carried).append(" termite(s)").append(spread ? ", spread out round the spot" : "");
        }
        if (castingSince >= 0 && castGap != null) {
            line.append(", casting about at a gap in the trail of ").append(Math.round((castGap.far() - castGap.near()) * 10) / 10.0).append(" blocks");
        }
        if (gapsCrossed > 0) {
            line.append(", ").append(gapsCrossed).append(" gap(s) in the trail crossed");
        }
        if (lostTrail) {
            line.append(", turned home having lost the trail");
        }
        if (lastLevel != null) {
            long now = lastLevel.getGameTime();
            line.append(", ").append((now - stageSince) / 20).append(" s in this stage");
        }
        return line.toString();
    }

    private static double flat(BlockPos a, BlockPos b) {
        return RaidGround.flat(a, b);
    }

    /** A line of points (block middles) with the distance walked along it to each (blocks, flat). */
    static final class Route {
        final List<BlockPos> points;
        private final double[] xs;
        private final double[] zs;
        private final double[] ys;
        private final double[] sums;
        /** The distances walked to each point, climbs and drops counted (the raid queue fix). */
        private final double[] walks;

        Route(List<BlockPos> points) {
            this.points = List.copyOf(points);
            int n = points.size();
            xs = new double[n];
            zs = new double[n];
            ys = new double[n];
            sums = new double[n];
            walks = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = points.get(i).getX() + 0.5;
                zs[i] = points.get(i).getZ() + 0.5;
                ys[i] = points.get(i).getY();
                sums[i] = i == 0 ? 0.0 : sums[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
                walks[i] = i == 0 ? 0.0 : walks[i - 1] + Math.hypot(sums[i] - sums[i - 1], ys[i] - ys[i - 1]);
            }
        }

        /**
         * The raid queue fix (2026-11-06): how far an ant walks to this place {@code along} (flat) the line, the climbs
         * and drops counted (down a tall dead mound's hump the trail drops 10 blocks in 3 flat ones: by the flat
         * distance the front, after 14 blocks of climbing down, seemed barely ahead, and the crowd waited behind it).
         * Behind the start and past the end, flat.
         */
        double walked(double along) {
            int n = xs.length;
            if (n == 1 || along <= 0.0) {
                return along;
            }
            if (along >= length()) {
                return walks[n - 1] + along - length();
            }
            int i = pieceAt(along);
            double piece = sums[i] - sums[i - 1];
            double t = piece <= 1.0E-6 ? 1.0 : Mth.clamp((along - sums[i - 1]) / piece, 0.0, 1.0);
            return walks[i - 1] + t * (walks[i] - walks[i - 1]);
        }

        /** The other way round: the flat distance along the line of the place an ant reaches after walking this far. */
        double alongAtWalked(double walked) {
            int n = xs.length;
            if (n == 1 || walked <= 0.0) {
                return walked;
            }
            if (walked >= walks[n - 1]) {
                return length() + walked - walks[n - 1];
            }
            int lo = 1, hi = n - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (walked <= walks[mid]) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            double piece = walks[lo] - walks[lo - 1];
            double t = piece <= 1.0E-6 ? 1.0 : Mth.clamp((walked - walks[lo - 1]) / piece, 0.0, 1.0);
            return sums[lo - 1] + t * (sums[lo] - sums[lo - 1]);
        }

        /** The whole line's length walked, climbs and drops counted. */
        double walkedLength() {
            return walks[walks.length - 1];
        }

        /**
         * How far an ant at (x, y, z) has walked along the line, its height counted (halfway down a wall it is halfway
         * along the drop, which its flat place cannot tell).
         */
        double walkedOf(double x, double y, double z) {
            return nearest3(x, y, z)[0];
        }

        /** How far (blocks, straight, height counted) the point (x, y, z) lies from the line. */
        double awayFrom(double x, double y, double z) {
            return Math.sqrt(nearest3(x, y, z)[1]);
        }

        /** The nearest place on the line in 3D: {walked to it, distance squared}. */
        private double[] nearest3(double x, double y, double z) {
            int n = xs.length;
            if (n == 1) {
                return new double[] {0.0, (x - xs[0]) * (x - xs[0]) + (z - zs[0]) * (z - zs[0]) + (y - ys[0]) * (y - ys[0])};
            }
            double best = Double.MAX_VALUE;
            double bestWalked = 0.0;
            for (int i = 1; i < n; i++) {
                double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1], dy = ys[i] - ys[i - 1];
                double piece = dx * dx + dz * dz + dy * dy;
                double t = piece <= 1.0E-9 ? 0.0
                        : Mth.clamp(((x - xs[i - 1]) * dx + (z - zs[i - 1]) * dz + (y - ys[i - 1]) * dy) / piece, 0.0, 1.0);
                double px = xs[i - 1] + dx * t, pz = zs[i - 1] + dz * t, py = ys[i - 1] + dy * t;
                double distance = (x - px) * (x - px) + (z - pz) * (z - pz) + (y - py) * (y - py);
                if (distance < best) {
                    best = distance;
                    bestWalked = walks[i - 1] + (walks[i] - walks[i - 1]) * t;
                }
            }
            return new double[] {bestWalked, best};
        }

        double length() {
            return sums[sums.length - 1];
        }

        BlockPos end() {
            return points.get(points.size() - 1);
        }

        /**
         * The first piece's end at or past {@code along} (1 to n - 1): a binary search of the distances walked (the raid
         * code review, finding 9; it was a walk from the start every time).
         */
        private int pieceAt(double along) {
            int lo = 1, hi = xs.length - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (along <= sums[mid]) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            return lo;
        }

        /** The place {@code along} blocks along the line: x, z, y. */
        double[] at(double along) {
            int n = xs.length;
            if (n == 1 || along <= 0.0) {
                return new double[] {xs[0], zs[0], ys[0]};
            }
            int i = pieceAt(along);
            double piece = sums[i] - sums[i - 1];
            double t = piece <= 1.0E-6 ? 1.0 : Mth.clamp((along - sums[i - 1]) / piece, 0.0, 1.0);
            return new double[] {Mth.lerp(t, xs[i - 1], xs[i]), Mth.lerp(t, zs[i - 1], zs[i]), Mth.lerp(t, ys[i - 1], ys[i])};
        }

        /** The line's heading at this place along it (a unit x, z). */
        double[] direction(double along) {
            int n = xs.length;
            for (int i = n == 1 ? n : pieceAt(along); i < n; i++) {
                if (along <= sums[i] || i == n - 1) {
                    double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1];
                    double length = Math.hypot(dx, dz);
                    if (length > 1.0E-6) {
                        return new double[] {dx / length, dz / length};
                    }
                }
            }
            return new double[] {1.0, 0.0};
        }

        /** How far along the line the nearest place on it to (x, z) lies. */
        double along(double x, double z) {
            return nearest(x, z)[0];
        }

        /** How far (x, z) is from the line. */
        double offBy(double x, double z) {
            return nearest(x, z)[1];
        }

        /** The nearest place on the line to (x, z): how far along it, and how far off it. */
        double[] nearest(double x, double z) {
            int n = xs.length;
            if (n == 1) {
                return new double[] {0.0, Math.hypot(x - xs[0], z - zs[0])};
            }
            double best = Double.MAX_VALUE;
            double bestAlong = 0.0;
            for (int i = 1; i < n; i++) {
                double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1];
                double piece = dx * dx + dz * dz;
                double t = piece <= 1.0E-9 ? 0.0 : Mth.clamp(((x - xs[i - 1]) * dx + (z - zs[i - 1]) * dz) / piece, 0.0, 1.0);
                double px = xs[i - 1] + dx * t, pz = zs[i - 1] + dz * t;
                double distance = Math.hypot(x - px, z - pz);
                if (distance < best) {
                    best = distance;
                    bestAlong = sums[i - 1] + Math.sqrt(piece) * t;
                }
            }
            return new double[] {bestAlong, best};
        }
    }
}
