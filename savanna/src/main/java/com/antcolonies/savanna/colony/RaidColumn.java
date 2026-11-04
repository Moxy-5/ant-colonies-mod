package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

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

/**
 * M8 step 3b, one raid column ({@link Raids}; {@code docs/DESIGN.md}, "Step 3b, the column"). Real Megaponera
 * (Longhurst and Howse 1979, Hoelldobler et al. 1994, Frank et al. 2017-2018, Yusuf 2026): a scout home from a find
 * recruits nestmates at the entrance; they pour out and mill round it, then follow the scout out along its scent line,
 * single file near the nest and wider in the open, the column moving in surges as the front waits for the ants behind;
 * a little short of the termites it halts and bunches up, then they rush in all at once (the majors bite the
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
 * handed to the colony's raids to limp home along the trail ({@link #takeHurt}). Numbers: {@code raids.carry}
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
     * the stung termite it is to pick up there (step 3c), null for none.
     */
    public record Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt, @Nullable UUID take) {
        public Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt) {
            this(to, pace, lookAt, null);
        }
    }

    /** The carry numbers when a species file has none (the same as Megaponera's). */
    public static final SavannaNumbers.CarryNumbers DEFAULT_CARRY =
            new SavannaNumbers.CarryNumbers(4, 10, 1, 3, 10.0, 1200, 10, 600, 8.0, 5.0, 0.85, 10, 2400, 6000);
    /** A raider picks up a termite this near it (blocks). */
    public static final double PICK_REACH = 1.6;
    /** A termite a raider found no way to is left to the others this long (ticks; BUGS 76: never searched every tick). */
    private static final int NO_WAY_TICKS = 200;

    /** The front's pace: a little slower than the rest, so the ants behind catch up (my pick). */
    private static final double LEAD_PACE = 0.9;
    /** A raider this far (blocks along the trail) behind its place hurries (my pick). */
    private static final double HURRY_BEHIND = 4.0;
    private static final double HURRY_PACE = 1.15;
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
    /** How often (ticks) the column works out its places. */
    private static final int EVERY = 5;
    /**
     * Raid rework R3: the front waits for the line at most this long at a time (ticks), then walks on at least
     * {@link #LEAD_STEP} before it may wait again: real columns halt in surges and move on (my pick, a few seconds).
     */
    private static final long FRONT_PAUSE = 80;
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
    /** The raiders who left the column hurt since the colony last asked ({@link #takeHurt}). */
    private final List<UUID> hurt = new ArrayList<>();
    /**
     * Raid rework R1, the raid diary: the world the column last worked in (for the time of day in its log lines), since
     * when the front has waited for the line to close up (game time, -1 not waiting), and when it last said so.
     */
    @Nullable
    private ServerLevel lastLevel;
    private long waitingSince = -1;
    private long waitSaidAt = -1;
    /** R3: since when the front has paused for the line now (-1 not), and how far along it must walk before it may again. */
    private long pausedSince = -1;
    private double noPauseUntil = -1.0;
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
    /** The front's wait is told in the log after this long, and again every this long while it lasts (ticks). */
    private static final long WAIT_SAY_EVERY = 200;

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
        long dayTime = lastLevel == null ? -1 : colony.timeOfDay(lastLevel) % 24000;
        String clock = dayTime < 0 ? "?" : String.format(java.util.Locale.ROOT, "%02d:%02d", (dayTime / 1000 + 6) % 24,
                (dayTime % 1000) * 60 / 1000);
        com.antcolonies.savanna.SavannaMod.LOGGER.info("[raid] nest {} at {} (tick {}), {}: {}", colony.getCenter().toShortString(), clock,
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
        return shortId(ant.getUUID()) + " at " + ant.blockPosition().toShortString() + ", " + Math.round(route.along(ant.getX(), ant.getZ()) * 10) / 10.0
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
        return members.contains(ant);
    }

    public int size() {
        return size;
    }

    public long startedAt() {
        return startedAt;
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

    /** The raiders who left the column hurt since last asked (the colony's raids lead them home); the list is emptied. */
    List<UUID> takeHurt() {
        List<UUID> out = List.copyOf(hurt);
        hurt.clear();
        return out;
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

    /** Where this raider should be now, or null to stand (worked out every few ticks). */
    @Nullable
    public Order orderFor(ServerLevel level, MatabeleEntity ant) {
        update(level);
        return orders.get(ant.getUUID());
    }

    /** It leaves the column (gone home on its own, crippled, dead). */
    void drop(UUID ant) {
        members.remove(ant);
        orders.remove(ant);
        sentIn.remove(ant);
        taking.remove(ant);
    }

    /** The column's stages and every raider's place, every {@link #EVERY} ticks (asked by the raiders and the colony). */
    void update(ServerLevel level) {
        long now = level.getGameTime();
        if (now - updatedAt < EVERY || stage == Stage.DONE) {
            return;
        }
        updatedAt = now;
        lastLevel = level;
        List<MatabeleEntity> ants = new ArrayList<>();
        for (UUID id : List.copyOf(members)) {
            if (level.getEntity(id) instanceof MatabeleEntity ant && ant.isAlive() && !ant.fightsNoMore() && !ant.isDowned()) {
                ants.add(ant);
            } else {
                // dead, crippled, clamped, stung still, or out of the loaded world: out of the column
                String why = !(level.getEntity(id) instanceof MatabeleEntity gone) ? "gone from the world" : !gone.isAlive() ? "dead"
                        : gone.fightsNoMore() ? "fights no more" : "downed";
                lost.merge(why, 1, Integer::sum);
                say(shortId(id) + " leaves the column: " + why);
                if (level.getEntity(id) instanceof MatabeleEntity ant && ant.isAlive() && ant.fightsNoMore()) {
                    hurt.add(id); // step 3c: it limps home along the trail by itself
                }
                drop(id);
            }
        }
        if (ants.isEmpty()) {
            end("nobody left in it at " + stage.name().toLowerCase());
            return;
        }
        if (!members.contains(leader)) {
            leader = members.get(0); // the front fell: the next ant follows the scent on
        }
        orders.clear();
        Stage was = stage;
        switch (stage) {
            case FORMING -> form(level, ants, now);
            case MARCHING -> {
                if (march(level, ants, out, now)) {
                    trail.walked(now); // (R4: every raider laid scent on the way out)
                    next(Stage.GATHERING, now);
                } else if (turnBack) {
                    turnBack = false;
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

    /** R3: how long the march out may last (ticks): {@link #MARCH_BASE} and {@link #MARCH_PER_BLOCK} a block of it. */
    long marchLimit() {
        return MARCH_BASE + (long) Math.ceil(out.length() * MARCH_PER_BLOCK);
    }

    /**
     * R3: a raider sent somewhere more than {@link #HEADWAY_FAR} blocks off that got nowhere for {@link #NO_HEADWAY}
     * ticks leaves the column and goes home along the trail (with the hurt, {@link #takeHurt}); nobody stuck holds the
     * rest. One standing at its place, or told to stand, makes headway enough.
     */
    private void headway(ServerLevel level, List<MatabeleEntity> ants, long now) {
        for (MatabeleEntity ant : ants) {
            UUID id = ant.getUUID();
            BlockPos here = ant.blockPosition();
            BlockPos last = anchor.get(id);
            Order order = orders.get(id);
            boolean far = order != null && order.to() != null && Math.sqrt(here.distSqr(order.to())) > HEADWAY_FAR;
            if (last == null || !far || Math.sqrt(here.distSqr(last)) >= HEADWAY_MOVE) {
                anchor.put(id, here);
                anchorSince.put(id, now);
                continue;
            }
            if (now - anchorSince.getOrDefault(id, now) > NO_HEADWAY && ants.size() > 1) {
                say(shortId(id) + " leaves the column, no headway for " + NO_HEADWAY / 20 + " s, and goes home: " + who(ant, stage == Stage.HOMING ? back : out));
                lost.merge("no headway", 1, Integer::sum);
                hurt.add(id); // (home along the trail by itself, as the hurt do)
                drop(id);
                anchor.remove(id);
                anchorSince.remove(id);
            }
        }
    }

    private void end(String why) {
        say("the raid is over: " + why + ", " + cameHome + " came home, " + carried + " termite(s) carried"
                + (lost.isEmpty() ? "" : ", lost on the way: " + lost));
        stage = Stage.DONE;
        ended = why;
    }

    /** Why the column ended, and who left it on the way (tests and the log). */
    public String endedWhy() {
        return ended + (lost.isEmpty() ? "" : ", lost on the way: " + lost);
    }

    private void next(Stage to, long now) {
        say("on to " + to.name().toLowerCase() + " after " + (now - stageSince) / 20 + " s, " + members.size() + " real raider(s)"
                + (carried > 0 ? ", " + carried + " termite(s) carried" : ""));
        waitingSince = -1;
        pausedSince = -1;
        noPauseUntil = -1.0;
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

    /** The recruits come out and mill round the entrance; the scout waits at the head of its line. */
    private void form(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos entrance = colony.getCenter();
        int outside = 0;
        for (int i = 0; i < ants.size(); i++) {
            MatabeleEntity ant = ants.get(i);
            if (flat(ant.blockPosition(), entrance) <= NEAR && ant.getY() >= entrance.getY() - 1) {
                outside++;
            }
            BlockPos spot;
            if (ant.getUUID().equals(leader)) {
                spot = place(level, this.out, Math.min(2.0, this.out.length()), 0.0);
            } else {
                double angle = i * 2.39996; // (the golden angle: spread evenly round the entrance)
                double radius = 1.5 + 1.5 * ((i * 7) % 10) / 10.0;
                spot = standAt(level, entrance.getX() + 0.5 + Math.cos(angle) * radius, entrance.getZ() + 0.5 + Math.sin(angle) * radius,
                        entrance.getY());
            }
            orders.put(ant.getUUID(), new Order(spot, 1.0, point(this.out, 4.0)));
        }
        long since = now - stageSince;
        if (outside >= ants.size() && since >= numbers.leastFormTicks() || since >= numbers.formTicks()) {
            // Off they go; the recruits not out by now stay home.
            for (MatabeleEntity ant : ants) {
                if (!ant.getUUID().equals(leader) && !(flat(ant.blockPosition(), entrance) <= NEAR + 2 && ant.getY() >= entrance.getY() - 2)) {
                    say(shortId(ant.getUUID()) + " stays home, not out at the entrance in time: " + who(ant, out));
                    drop(ant.getUUID());
                }
            }
            next(Stage.MARCHING, now);
        }
    }

    /**
     * On the march (raid rework R3, trail-following): the front walks the trail ahead of itself and waits for the line
     * to close up, but never more than {@link #FRONT_PAUSE} at a time (then it walks on at least {@link #LEAD_STEP}: the
     * column moves in surges). Every other raider out of the nest follows the trail and keeps a gap of {@code spacing}
     * to the ant ahead of it in the marching order ({@code abreast} side by side further out, so the one a row ahead),
     * walking up to {@link #LEAD_STEP} ahead of itself at a time; it never waits on anyone behind it. A raider still in
     * the nest comes up to the line's start; only raiders out count for the line the front waits for. At the route's end
     * true (the next stage).
     */
    private boolean march(ServerLevel level, List<MatabeleEntity> ants, Route route, long now) {
        MatabeleEntity front = ants.stream().filter(ant -> ant.getUUID().equals(leader)).findFirst().orElse(ants.get(0));
        frontAt = route.along(front.getX(), front.getZ());
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
        Map<UUID, Double> at = new HashMap<>();
        for (MatabeleEntity ant : line) {
            at.put(ant.getUUID(), route.along(ant.getX(), ant.getZ()));
        }
        int rows = (int) Math.ceil(line.size() / (double) Math.max(1, numbers.abreast()));
        double length = rows * numbers.spacing();
        double rear = frontAt;
        MatabeleEntity hindmost = null;
        for (MatabeleEntity ant : line) {
            double along = at.get(ant.getUUID());
            if (along >= frontAt - length - numbers.stretch() * 2.0 && along < rear) {
                rear = along; // (stragglers far behind are not waited for: they follow the scent on their own)
                hindmost = ant;
            }
        }
        boolean strung = frontAt - rear > length + numbers.stretch();
        boolean waits = false;
        if (casting) {
            pausedSince = -1; // (stopped at the gap: no surge counted)
        } else if (strung && frontAt >= noPauseUntil) {
            if (pausedSince < 0) {
                pausedSince = now;
            }
            waits = now - pausedSince < FRONT_PAUSE;
            if (!waits) {
                noPauseUntil = frontAt + LEAD_STEP; // (on again: no new halt till it has walked a stretch)
                pausedSince = -1;
                head = Math.min(route.length(), frontAt + LEAD_STEP);
            }
        } else if (!strung) {
            pausedSince = -1;
        }
        sayWait(route, front, waits, hindmost);
        if (!waits && !casting && frontAt >= head - LEAD_STEP / 2) {
            head = Math.min(route.length(), frontAt + LEAD_STEP);
        }
        BlockPos ahead = point(route, Math.min(route.length(), frontAt + 4.0));
        if (casting) {
            orders.put(front.getUUID(), new Order(castSpot(level, route, 0, now), 1.0, point(route, castGap.far())));
        } else {
            orders.put(front.getUUID(), waits ? new Order(null, LEAD_PACE, ahead) : new Order(place(level, route, head, 0.0), LEAD_PACE, ahead));
        }
        // The others, each behind the one ahead of it in the marching order.
        for (int k = 0; k < line.size(); k++) {
            MatabeleEntity ant = line.get(k);
            double own = at.get(ant.getUUID());
            int lanes = lanesAt(route, own);
            double aheadAt = k - lanes < 0 ? frontAt : at.get(line.get(k - lanes).getUUID());
            double target = Math.max(own, Math.min(aheadAt - numbers.spacing(), own + LEAD_STEP));
            target = Math.max(0.0, Math.min(route.length(), target));
            if (casting && casters.contains(ant.getUUID())) {
                orders.put(ant.getUUID(), new Order(castSpot(level, route, 1 + k, now), 1.0, point(route, castGap.far())));
                continue;
            }
            if (casting) {
                target = Math.min(target, castGap.near() - GAP_STOP); // (the rest wait behind the gap)
                target = Math.max(0.0, target);
            }
            double side = lanes == 1 ? 0.0 : k % lanes - (lanes - 1) / 2.0; // (a block apart side by side)
            double pace = own < target - HURRY_BEHIND ? HURRY_PACE : 1.0;
            orders.put(ant.getUUID(), new Order(place(level, route, target, side), pace, point(route, Math.min(route.length(), target + 3.0))));
        }
        BlockPos lineStart = place(level, route, 0.0, 0.0);
        for (MatabeleEntity ant : notOut) {
            orders.put(ant.getUUID(), new Order(lineStart, HURRY_PACE, ahead)); // (up out of the nest to the line)
        }
        return false;
    }

    /**
     * R4: the first gap in the scent trail ahead of the front on this route: its first broken point at least a block
     * behind the front and at most {@link #GAP_LOOK} ahead of it, and the first whole point past the broken run (or the
     * route's end); null if none. Trail points off the route are not counted.
     */
    @Nullable
    private Gap gapAhead(Route route, double frontAt) {
        List<double[]> on = new ArrayList<>();
        trail.forEachPoint((index, at, broken) -> {
            double x = at.getX() + 0.5, z = at.getZ() + 0.5;
            double along = route.along(x, z);
            // (on the way home after turning back, the gap turned back at lies behind: the casters' loops may have
            // carried the front a little into it, so it is skipped by where it was, not by where the front is)
            boolean turnedGap = route == back && turnedAt >= 0.0 && along <= route.length() - turnedAt + 0.5;
            if (route.offBy(x, z) <= ON_ROUTE && !turnedGap) {
                on.add(new double[] {along, broken ? 1.0 : 0.0});
            }
        });
        on.sort(java.util.Comparator.comparingDouble(p -> p[0]));
        Double near = null;
        for (double[] p : on) {
            if (near == null) {
                if (p[1] > 0.5 && p[0] >= frontAt - 1.0) {
                    if (p[0] > frontAt + GAP_LOOK) {
                        return null;
                    }
                    near = p[0];
                }
            } else if (p[1] < 0.5 && p[0] > near) {
                return new Gap(near, p[0]);
            }
        }
        return near == null ? null : new Gap(near, route.length());
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
            line.stream().sorted(java.util.Comparator.comparingDouble(ant -> -route.along(ant.getX(), ant.getZ())))
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
            double x = p.getX() + 0.5, z = p.getZ() + 0.5;
            double along = route.along(x, z);
            return route.offBy(x, z) <= ON_ROUTE && along >= gap.near() - 1.0 && along <= gap.far() + 1.0;
        });
        gapsCrossed++;
        say((pickedUp ? "the trail is picked up past the gap after casting " : givenUp && route == back ? "home past the gap anyway after casting "
                : "a former scout leads on across the gap after casting ") + cast / 20 + " s; new scent laid over " + laid + " point(s)");
        head = Math.min(route.length(), gap.far() + LEAD_STEP);
        noPauseUntil = gap.far();
        pausedSince = -1;
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
    private boolean isOut(ServerLevel level, MatabeleEntity ant) {
        BlockPos entrance = colony.getCenter();
        if (colony.nest().isInside(level, ant.blockPosition())) {
            return false;
        }
        return !(flat(ant.blockPosition(), entrance) <= NEAR && ant.getY() < entrance.getY() - 1.5);
    }

    /**
     * The diary: while the front waits for the line to close up, every {@link #WAIT_SAY_EVERY} ticks who it waits for
     * (the hindmost ant it counts), and when a long wait ends, how long it lasted.
     */
    private void sayWait(Route route, MatabeleEntity front, boolean waits, @Nullable MatabeleEntity hindmost) {
        long now = lastLevel == null ? 0 : lastLevel.getGameTime();
        if (!waits) {
            if (waitingSince >= 0 && now - waitingSince >= WAIT_SAY_EVERY) {
                say("the front goes on after waiting " + (now - waitingSince) / 20 + " s");
            }
            waitingSince = -1;
            return;
        }
        if (waitingSince < 0) {
            waitingSince = now;
            waitSaidAt = now;
            return;
        }
        if (now - waitSaidAt < WAIT_SAY_EVERY) {
            return;
        }
        waitSaidAt = now;
        say("the front (" + who(front, route) + ") has waited " + (now - waitingSince) / 20 + " s for the line, its hindmost "
                + (hindmost == null ? "?" : who(hindmost, route)));
    }

    /** How many side by side at this place along a route: single file near the entrance, wider further out. */
    private int lanesAt(Route route, double along) {
        double fromNest = route == out ? along : route.length() - along;
        if (fromNest < numbers.singleFile()) {
            return 1;
        }
        return fromNest < numbers.singleFile() * 2.0 ? Math.min(2, numbers.abreast()) : Math.max(1, numbers.abreast());
    }

    /** A little short of the find the column halts and bunches up; once most are there (or after a while), the attack. */
    private void gather(ServerLevel level, List<MatabeleEntity> ants, long now) {
        int there = bunch(level, ants);
        if (there >= Math.ceil(ants.size() * MOST_THERE) || now - stageSince >= numbers.gatherTicks()) {
            next(Stage.ATTACKING, now);
            attackedAt = now;
            gatheredAtAttack = there;
            for (MatabeleEntity ant : ants) {
                sendIn(ant);
            }
        }
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
            double angle = i * 2.39996;
            double radius = i == 0 ? 0.0 : 1.0 + 1.5 * Math.sqrt(i / (double) ants.size());
            BlockPos spot = standAt(level, middle.getX() + 0.5 + Math.cos(angle) * radius, middle.getZ() + 0.5 + Math.sin(angle) * radius,
                    middle.getY());
            orders.put(ant.getUUID(), new Order(spot, 1.0, find.at()));
        }
        return there;
    }

    /** One raider sent in to fight round the find (step 2's fight: the majors break in, the minors go in after). */
    private void sendIn(MatabeleEntity ant) {
        ant.orderToFight(find.at(), attackedAt + numbers.fightTicks());
        sentIn.add(ant.getUUID());
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
        Set<UUID> left = new HashSet<>();
        for (AbstractAntEntity insect : prey) {
            left.add(insect.getUUID());
        }
        taking.entrySet().removeIf(entry -> !left.contains(entry.getValue())); // (taken, dead, or no way: free again)
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
            if (mine != null && level.getEntity(mine) instanceof AbstractAntEntity insect) {
                orders.put(ant.getUUID(), new Order(insect.blockPosition(), 1.0, insect.blockPosition(), mine));
                anyFetching = true;
            }
        }
        if (!anyFetching || now - stageSince >= carry.collectTicks()) {
            taking.clear();
            boolean spreadNow = carried == 0 && !spread && now - stageSince < carry.collectTicks();
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
        for (int i = 0; i < ants.size(); i++) {
            MatabeleEntity ant = ants.get(i);
            if (ant.getFightSpot() != null) {
                continue; // (going for one)
            }
            AbstractAntEntity seen = level.getEntitiesOfClass(AbstractAntEntity.class,
                            ant.getBoundingBox().inflate(carry.spreadSee(), 3.0, carry.spreadSee()),
                            insect -> insect.isAlive() && !insect.isDowned() && !(insect instanceof MatabeleEntity)
                                    && !colony.getId().equals(insect.getColonyId()) && level.canSeeSky(insect.blockPosition()))
                    .stream().min(java.util.Comparator.comparingDouble(insect -> insect.distanceToSqr(ant))).orElse(null);
            if (seen != null) {
                ant.orderToFight(seen.blockPosition(), now + carry.spreadTicks());
                continue;
            }
            double angle = i * 2.39996;
            double radius = 3.0 + (carry.spreadReach() - 3.0) * ((i * 7) % 10) / 10.0;
            BlockPos spot = standAt(level, at.getX() + 0.5 + Math.cos(angle) * radius, at.getZ() + 0.5 + Math.sin(angle) * radius, at.getY());
            orders.put(ant.getUUID(), new Order(spot, 1.0, at));
        }
        boolean fighting = ants.stream().anyMatch(ant -> ant.getFightSpot() != null);
        boolean stung = !preyRound(level).isEmpty();
        if (!fighting && (stung || now - stageSince >= carry.spreadTicks()) || now - stageSince >= carry.spreadTicks() + 200) {
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
            // (any left out now go in by themselves)
            trail.walked(now); // (R4: they laid scent on the way home too)
            end(ants.isEmpty() ? "all home" : "home time up with " + ants.size() + " out, nearest "
                    + ants.stream().mapToDouble(ant -> flat(ant.blockPosition(), entrance)).min().orElse(0) + " flat from the entrance "
                    + entrance.toShortString() + ", one at " + back.along(ants.get(0).getX(), ants.get(0).getZ()) + " of " + back.length() + " along the way home");
            return;
        }
        if (!members.contains(leader)) {
            leader = members.get(0);
        }
        if (march(level, ants, back, now)) {
            for (MatabeleEntity ant : ants) {
                orders.put(ant.getUUID(), new Order(entrance, 1.0, null)); // (the front is at the entrance: the rest come in behind it)
            }
        }
        if (carriers > 0) {
            // Step 3c: laden, the column walks home a little slower (my pick).
            orders.replaceAll((id, order) -> new Order(order.to(), order.pace() * carry.ladenPace(), order.lookAt(), order.take()));
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
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
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

    /** A cell an ant can stand in at this column of the world near height {@code y}: free, dry, with a floor; null if none. */
    @Nullable
    public static BlockPos standAt(ServerLevel level, double x, double z, int y) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            BlockPos cell = new BlockPos(bx, y + dy, bz);
            if (!level.hasChunkAt(cell)) {
                return null;
            }
            BlockPos below = cell.below();
            if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() && level.getFluidState(cell).isEmpty()
                    && level.getFluidState(below).isEmpty() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    /** The column in words (the colony list). */
    public String describe() {
        StringBuilder line = new StringBuilder("; a raid of ").append(size).append(" (").append(members.size()).append(" real out")
                .append(numbersAlong > 0 ? ", " + numbersAlong + " as numbers" : "").append(") ").append(stage.name().toLowerCase())
                .append(" to ").append(find.at().toShortString());
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
            if (waitingSince >= 0) {
                line.append(", the front waiting ").append((now - waitingSince) / 20).append(" s for the line");
            }
        }
        return line.toString();
    }

    static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A line of points (block middles) with the distance walked along it to each (blocks, flat). */
    static final class Route {
        final List<BlockPos> points;
        private final double[] xs;
        private final double[] zs;
        private final double[] ys;
        private final double[] sums;

        Route(List<BlockPos> points) {
            this.points = List.copyOf(points);
            int n = points.size();
            xs = new double[n];
            zs = new double[n];
            ys = new double[n];
            sums = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = points.get(i).getX() + 0.5;
                zs[i] = points.get(i).getZ() + 0.5;
                ys[i] = points.get(i).getY();
                sums[i] = i == 0 ? 0.0 : sums[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            }
        }

        double length() {
            return sums[sums.length - 1];
        }

        BlockPos end() {
            return points.get(points.size() - 1);
        }

        /** The place {@code along} blocks along the line: x, z, y. */
        double[] at(double along) {
            int n = xs.length;
            if (n == 1 || along <= 0.0) {
                return new double[] {xs[0], zs[0], ys[0]};
            }
            for (int i = 1; i < n; i++) {
                if (along <= sums[i] || i == n - 1) {
                    double piece = sums[i] - sums[i - 1];
                    double t = piece <= 1.0E-6 ? 1.0 : Mth.clamp((along - sums[i - 1]) / piece, 0.0, 1.0);
                    return new double[] {Mth.lerp(t, xs[i - 1], xs[i]), Mth.lerp(t, zs[i - 1], zs[i]), Mth.lerp(t, ys[i - 1], ys[i])};
                }
            }
            return new double[] {xs[n - 1], zs[n - 1], ys[n - 1]};
        }

        /** The line's heading at this place along it (a unit x, z). */
        double[] direction(double along) {
            int n = xs.length;
            for (int i = 1; i < n; i++) {
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

        private double[] nearest(double x, double z) {
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
