package com.antcolonies.colony;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * How many foragers go out (brain step 4b, part 2: Deborah Gordon's rule; the user's decisions in {@code docs/DESIGN.md},
 * "4b parts 2 and 3"). No ant is in charge. Real red harvester ants (<i>Pogonomyrmex barbatus</i>): the foragers not out
 * wait just inside the entrance; a forager coming home with a seed (or a patroller home from its round) touches the
 * waiting ants it passes with its antennae, and one touched often enough in a short time goes out (Greene and Gordon:
 * about one contact every 10 seconds). So food found quickly brings more foragers out, and when the seeds get scarce
 * the returns slow, the touches fade and fewer go. Empty returns do not count.
 *
 * <p><b>Real ants</b> count their own touches ({@link #mayGo}); <b>the numbers</b> (see {@link FieldLife}) are counted
 * the same way: each homecoming touches {@code touches_per_return} of the waiting ones, and a waiting one goes once it
 * has had enough (worked out from the touches of the last {@code touch_memory_ticks}, see {@link #numbersSecond}).
 *
 * <p>The door opens only once a patroller is back ({@link Patrol#mayForage}) and shuts with each round. In a small colony
 * (under {@code small_colony_foragers}: the user's rule, so a young colony is not slowed) one touch is enough, a
 * homecomer touches every ant waiting by the entrance, and a forager's own seed counts as its touch.
 *
 * <p>Species-neutral: any colony whose profile has {@code food.door} (only the harvester ant now); without one, or with
 * the patrollers off, every forager may go.
 */
public class Door {
    /** A real forager out of sight this long (its land unloaded, or it went into the numbers) is no longer counted out. */
    private static final int OUT_OF_SIGHT_TICKS = 2400;
    /** A trip this long is over, whatever became of it (a search lasts up to 5 minutes, plus the walks out and home). */
    private static final int LONGEST_TRIP_TICKS = 9000;
    /**
     * How many waiting foragers crowd the entrance chamber at a time: the ones the homecomers meet (as each goes out,
     * the next moves up). So a few quick homecomings bring the first ones out, however many wait deeper in. My pick.
     */
    static final int AT_THE_DOOR = 10;
    /** A patroller home touches the ants it meets by the door for this long. */
    static final int PATROLLER_TOUCH_TICKS = 200;
    /** The colony list counts the seeds brought home in this long. */
    private static final int LAST_MINUTE = 1200;

    /** A real forager out (since it set out). */
    private static final class Trip {
        final long since;
        long seen;
        boolean wentOut;

        Trip(long since) {
            this.since = since;
            this.seen = since;
        }
    }

    private final Colony colony;
    // --- real ants (not saved: a touch counts for half a minute, and trips are a few minutes) ---
    /** Each waiting real ant's touches: the game times they came. */
    private final Map<UUID, ArrayDeque<Long>> touches = new HashMap<>();
    /** Real foragers home with a seed of their own (small colony: it counts as their touch). */
    private final Set<UUID> ownSeed = new HashSet<>();
    /** Real foragers out. */
    private final Map<UUID, Trip> trips = new HashMap<>();
    /** Homecomers on their way in now, and the ants each has touched this trip (one touch each). */
    private final Map<UUID, Set<UUID>> touching = new HashMap<>();
    /** When each homecomer without a seed (a patroller) came in: it touches for a little while, then is done. */
    private final Map<UUID, Long> patrollerSince = new HashMap<>();
    /** The real foragers of the colony, counted each second (for the small-colony rule and the cap after danger). */
    private int realForagers;
    // --- the numbers (saved) ---
    /** How many forager numbers are out now (a fraction: a share of a forager's time). */
    private double numbersOut;
    /** Homecomings since the last second (patrollers, real seed carriers) that touch the waiting numbers. */
    private int pendingTouches;
    /** The touches of the last {@code touch_memory_ticks}: {game time, how many}. */
    private final ArrayDeque<long[]> window = new ArrayDeque<>();
    /** The seeds brought home (real and numbers) in the last minute: {game time, how many}. */
    private final ArrayDeque<long[]> returns = new ArrayDeque<>();
    /** Tests only: whether this runs at all (null: whenever the patrollers do). Not saved. */
    @Nullable
    private Boolean onForTests;

    Door(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    private SpeciesProfile.DoorNumbers numbers() {
        return colony.profile().food().door().orElse(null);
    }

    /** Whether the door holds the foragers back (with the patrollers only: they open it each round). */
    public boolean isOn(Level level) {
        if (numbers() == null || !colony.patrol().isOn(level)) {
            return false;
        }
        return onForTests == null || onForTests;
    }

    public void setOnForTests(@Nullable Boolean on) {
        onForTests = on;
    }

    /** All the foragers, real and numbers. */
    private int foragers(ServerLevel level) {
        return realForagers + colony.field().foragersInside(level);
    }

    private boolean isSmall(ServerLevel level, SpeciesProfile.DoorNumbers numbers) {
        return foragers(level) < numbers.smallColonyForagers();
    }

    /** Whether the colony has few foragers (under {@code small_colony_foragers}): the small-colony rules apply. */
    public boolean isSmallColony(ServerLevel level) {
        return numbers() != null && smallNow;
    }

    /** Whether the colony was small at the last second's count (worked out once a second: asked for every plant). */
    private boolean smallNow = true;

    // --- real ants --------------------------------------------------------------------------------------------------

    /**
     * Whether this real forager may set out now: the patrol is back, not too many are out (after danger), and it has
     * been touched often enough lately by homecomers (small colony: once, or its own seed). An ant already out may
     * carry on.
     */
    public boolean mayGo(ServerLevel level, AbstractAntEntity ant) {
        SpeciesProfile.DoorNumbers numbers = numbers();
        if (numbers == null || !isOn(level)) {
            return true;
        }
        if (!colony.patrol().mayForage(level)) {
            return false;
        }
        UUID id = ant.getUUID();
        if (trips.containsKey(id)) {
            return true;
        }
        float share = colony.patrol().foragerShareToday(level);
        if (share < 1.0F && trips.size() >= Math.max(1, Math.round(realForagers * share))) {
            return false;
        }
        if (colony.recruits().isRecruited(id)) {
            return true; // called out by a forager home from a rich patch (see colony.Recruits): it follows the scent line
        }
        boolean small = isSmall(level, numbers);
        if (small && ownSeed.contains(id)) {
            return true;
        }
        ArrayDeque<Long> times = touches.get(id);
        int had = times == null ? 0 : times.size();
        return had >= (small ? 1 : numbers.touchesFor(foragers(level)));
    }

    /** This real forager sets out (it found its food and claimed it): its touches are used up. */
    public void setOut(ServerLevel level, AbstractAntEntity ant) {
        if (!isOn(level)) {
            return;
        }
        UUID id = ant.getUUID();
        touches.remove(id);
        ownSeed.remove(id);
        trips.computeIfAbsent(id, key -> new Trip(level.getGameTime()));
    }

    /**
     * A homecomer inside by the entrance (called every few ticks while it walks in): a forager with a seed it brought
     * from outside ({@code withFood}), or a patroller back safely. The first call ends its trip; each call touches the
     * waiting ants it passes, once each this trip.
     */
    public void homecomer(ServerLevel level, AbstractAntEntity ant, boolean withFood) {
        SpeciesProfile.DoorNumbers numbers = numbers();
        if (numbers == null || !isOn(level)) {
            return;
        }
        UUID id = ant.getUUID();
        Set<UUID> touched = touching.get(id);
        long now = level.getGameTime();
        if (touched == null) {
            if (withFood) {
                if (trips.remove(id) == null) {
                    return; // a seed carried about inside: not a homecoming
                }
                ownSeed.add(id);
                returns.add(new long[] {now, 1});
            }
            pendingTouches++;
            touched = new HashSet<>();
            touching.put(id, touched);
            if (!withFood) {
                patrollerSince.put(id, now);
            }
        }
        // Small colony: every ant waiting by the entrance. Otherwise the ones it brushes past (within touch_reach), and
        // the nearest of those crowding the entrance chamber, touches_per_return in all this trip at least.
        boolean small = isSmall(level, numbers);
        BlockPos entrance = colony.nest().getEntrance();
        double door = Math.max(numbers.touchReach(), colony.profile().worker().restNearEntrance());
        Set<UUID> already = touched;
        List<AbstractAntEntity> waiting = new java.util.ArrayList<>(level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(entrance).inflate(door),
                other -> other != ant && other.isAlive() && other.isColonyWorker() && colony.getId().equals(other.getColonyId())
                        && !trips.containsKey(other.getUUID()) && !already.contains(other.getUUID())));
        waiting.sort(java.util.Comparator.comparingDouble(other -> other.distanceToSqr(ant)));
        double reach = numbers.touchReach() * numbers.touchReach();
        for (AbstractAntEntity other : waiting) {
            if (small || other.distanceToSqr(ant) <= reach || touched.size() < Math.max(1, numbers.touchesPerReturn())) {
                touched.add(other.getUUID());
                touches.computeIfAbsent(other.getUUID(), key -> new ArrayDeque<>()).add(now);
            }
        }
    }

    /** This real forager gave up its search and goes home empty: its trip is over (an empty return touches nobody). */
    public void endTrip(AbstractAntEntity ant) {
        trips.remove(ant.getUUID());
    }

    /** The homecomer has put its load down (or the patroller its round): its touching is over. */

    public void doneCarrying(AbstractAntEntity ant) {
        touching.remove(ant.getUUID());
        patrollerSince.remove(ant.getUUID());
    }

    /** Whether this real ant is counted out on a trip now (for tests). */
    public boolean isOut(UUID ant) {
        return trips.containsKey(ant);
    }

    /** How many touches this real ant has had lately (for tests). */
    public int touchesOf(UUID ant) {
        ArrayDeque<Long> times = touches.get(ant);
        return times == null ? 0 : times.size();
    }

    /** A recruiter called out {@code count} forager numbers to its patch (see {@link Recruits}): they go out now. */
    void recruitedNumbers(ServerLevel level, int count) {
        if (isOn(level) && colony.patrol().mayForage(level)) {
            numbersOut += count;
        }
    }

    /** Number patrollers home safely: each touches the waiting ones like a real one. */
    void patrollerNumbersHome(int count) {
        pendingTouches += count;
    }

    /** A new round, or the clock shut: nobody is out, every touch is forgotten. */
    void shut() {
        touches.clear();
        ownSeed.clear();
        trips.clear();
        touching.clear();
        patrollerSince.clear();
        numbersOut = 0;
        pendingTouches = 0;
        window.clear();
    }

    /** Once a second (here only): trips that are over, old touches gone, the real foragers counted. */
    void tick(ServerLevel level) {
        SpeciesProfile.DoorNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        if (!isOn(level)) {
            countForagers(level, numbers); // (a small colony's searchers spread out every way, door or no door: ScoutGoal)
            return;
        }
        long now = level.getGameTime();
        while (!returns.isEmpty() && returns.peekFirst()[0] <= now - LAST_MINUTE) {
            returns.removeFirst();
        }
        for (Iterator<ArrayDeque<Long>> it = touches.values().iterator(); it.hasNext(); ) {
            ArrayDeque<Long> times = it.next();
            while (!times.isEmpty() && (times.peekFirst() <= now - numbers.touchMemoryTicks() || times.peekFirst() > now)) {
                times.removeFirst();
            }
            if (times.isEmpty()) {
                it.remove();
            }
        }
        if (!colony.away().nestLoaded(level)) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Trip>> it = trips.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Trip> entry = it.next();
            Trip trip = entry.getValue();
            if (now - trip.since >= LONGEST_TRIP_TICKS || now < trip.since) {
                it.remove();
                continue;
            }
            if (!(level.getEntity(entry.getKey()) instanceof AbstractAntEntity ant) || !ant.isAlive()) {
                if (now - trip.seen >= OUT_OF_SIGHT_TICKS) {
                    it.remove();
                }
                continue;
            }
            trip.seen = now;
            boolean inside = colony.nest().isInside(level, ant.blockPosition());
            if (!inside) {
                trip.wentOut = true;
            } else if (trip.wentOut && !ant.isCarryingSeed()) {
                it.remove(); // home empty-handed: it touches nobody and waits for touches like the rest
            }
        }
        // A seed carrier is done once its seed is down, a patroller after its little while by the door.
        touching.keySet().removeIf(id -> {
            Long since = patrollerSince.get(id);
            boolean done = !(level.getEntity(id) instanceof AbstractAntEntity ant) || !ant.isAlive()
                    || (since == null ? !ant.isCarryingSeed() : now - since >= PATROLLER_TOUCH_TICKS || now < since);
            if (done) {
                patrollerSince.remove(id);
            }
            return done;
        });
        countForagers(level, numbers);
    }

    /** The real foragers counted, and whether the colony is small (under {@code small_colony_foragers}). */
    private void countForagers(ServerLevel level, SpeciesProfile.DoorNumbers numbers) {
        int count = 0;
        BlockPos center = colony.getCenter();
        int radius = colony.searchReach();
        for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(center).inflate(radius, 16, radius),
                ant -> ant.isAlive() && ant.isColonyWorker() && colony.getId().equals(ant.getColonyId()))) {
            if (colony.roleOf(level, ant) == ColonyRole.FORAGER) {
                count++;
            }
        }
        realForagers = count;
        smallNow = isSmall(level, numbers);
    }

    // --- the numbers ------------------------------------------------------------------------------------------------

    /** How many of the {@code foragers} numbers may be out this second (all of them without the door). */
    double numbersFree(ServerLevel level, int foragers) {
        if (!isOn(level)) {
            return foragers;
        }
        if (!colony.patrol().mayForage(level)) {
            return 0;
        }
        return Math.min(foragers, numbersOut);
    }

    /**
     * After the numbers' second of foraging: {@code returned} pieces came home (each touches the waiting ones),
     * {@code placed} of the free numbers were at work on a plant; the rest found nothing and come home empty a trip later
     * (they touch nobody). New ones go out by the touches, up to the day's share of the {@code foragers}.
     */
    void numbersSecond(ServerLevel level, int foragers, int returned, double placed) {
        SpeciesProfile.DoorNumbers numbers = numbers();
        if (numbers == null || !isOn(level)) {
            numbersOut = 0;
            return;
        }
        long now = level.getGameTime();
        if (!colony.patrol().mayForage(level)) {
            numbersOut = 0;
            pendingTouches = 0;
            window.clear();
            return;
        }
        if (returned > 0) {
            returns.add(new long[] {now, returned});
        }
        numbersOut = step(numbers, foragers, numbersOut, placed, returned + pendingTouches, windowTouches(numbers, now),
                isSmall(level, numbers), colony.patrol().foragerShareToday(level));
        int touchesNow = (returned + pendingTouches) * Math.max(1, numbers.touchesPerReturn());
        if (touchesNow > 0) {
            window.add(new long[] {now, touchesNow});
        }
        // The numbers' homecomings touch the real ants waiting by the entrance too.
        if (returned > 0 && colony.away().nestLoaded(level)) {
            touchRealWaiting(level, numbers, returned, foragers);
        }
        pendingTouches = 0;
    }

    /**
     * One second of the numbers' outflow (the maths, kept apart so the unit tests can check it): {@code placed} of the
     * {@code out} were at work, the rest come home empty at a trip's pace; then each of the {@code homecomings}
     * touches {@code touches_per_return} waiting ones, and a touched one goes if that touch makes enough within the
     * memory (the touches of the window spread over the waiting ones, a Poisson guess). A small colony: every waiting
     * one goes at any homecoming.
     */
    static double step(SpeciesProfile.DoorNumbers numbers, int foragers, double out, double placed, int homecomings,
            int windowTouches, boolean small, float share) {
        double idle = Math.max(0, Math.min(foragers, out) - placed);
        out = Math.max(0, out - idle * 20.0 / FieldLife.TRIP_TICKS);
        double waiting = Math.max(0, foragers - out);
        if (homecomings > 0 && waiting > 0) {
            double departures;
            if (small) {
                departures = waiting;
            } else {
                int touchesNow = homecomings * Math.max(1, numbers.touchesPerReturn());
                double had = windowTouches / Math.min(waiting, AT_THE_DOOR); // touches each one by the door has had lately
                departures = Math.min(waiting, touchesNow * atLeast(had, numbers.touchesFor(foragers) - 1));
            }
            out += departures;
        }
        return Math.min(out, foragers * (double) share);
    }

    /** The chance that a Poisson count with this mean is at least {@code count}. */
    static double atLeast(double mean, int count) {
        if (count <= 0) {
            return 1.0;
        }
        double term = Math.exp(-mean);
        double below = 0;
        for (int i = 0; i < count; i++) {
            below += term;
            term *= mean / (i + 1);
        }
        return Math.max(0, 1.0 - below);
    }

    private int windowTouches(SpeciesProfile.DoorNumbers numbers, long now) {
        while (!window.isEmpty() && (window.peekFirst()[0] <= now - numbers.touchMemoryTicks() || window.peekFirst()[0] > now)) {
            window.removeFirst();
        }
        int sum = 0;
        for (long[] entry : window) {
            sum += (int) entry[1];
        }
        return sum;
    }

    /** Each real ant waiting by the entrance is touched by the numbers' homecomings in its share. */
    private void touchRealWaiting(ServerLevel level, SpeciesProfile.DoorNumbers numbers, int returned, int foragers) {
        BlockPos entrance = colony.nest().getEntrance();
        List<AbstractAntEntity> waiting = level.getEntitiesOfClass(AbstractAntEntity.class,
                new AABB(entrance).inflate(Math.max(2, colony.profile().worker().restNearEntrance())),
                ant -> ant.isAlive() && ant.isColonyWorker() && colony.getId().equals(ant.getColonyId()) && !trips.containsKey(ant.getUUID()));
        if (waiting.isEmpty()) {
            return;
        }
        double chance = Math.min(1.0, (double) returned * Math.max(1, numbers.touchesPerReturn())
                / Math.max(1.0, foragers - numbersOut + waiting.size()));
        long now = level.getGameTime();
        for (AbstractAntEntity ant : waiting) {
            if (colony.random().nextDouble() < chance) {
                touches.computeIfAbsent(ant.getUUID(), key -> new ArrayDeque<>()).add(now);
            }
        }
    }

    // --- the colony list and tests ----------------------------------------------------------------------------------

    /** The seeds brought home (real and numbers) in the last minute. */
    public int seedsLastMinute(Level level) {
        long now = level.getGameTime();
        int sum = 0;
        for (long[] entry : returns) {
            if (entry[0] > now - LAST_MINUTE) {
                sum += (int) entry[1];
            }
        }
        return sum;
    }

    /** How many foragers are out now, real and numbers. */
    public int outNow() {
        return trips.size() + (int) Math.round(numbersOut);
    }

    /** For the colony list: " | foragers out 14 of 40 (12 back with seeds in the last minute)", or empty. */
    public String describe(ServerLevel level) {
        if (!isOn(level)) {
            return "";
        }
        return " | foragers out " + outNow() + " of " + foragers(level) + " (" + seedsLastMinute(level) + " back with seeds in the last minute)";
    }

    /** How many forager numbers are out now (for tests). */
    public double numbersOutNow() {
        return numbersOut;
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        all.putDouble("numbersOut", numbersOut);
        all.putInt("pendingTouches", pendingTouches);
        all.put("window", pairs(window));
        all.put("returns", pairs(returns));
        tag.put("door", all);
    }

    void load(CompoundTag tag) {
        shut();
        returns.clear();
        if (!tag.contains("door")) {
            return;
        }
        CompoundTag all = tag.getCompound("door");
        numbersOut = all.getDouble("numbersOut");
        pendingTouches = all.getInt("pendingTouches");
        readPairs(all.getList("window", Tag.TAG_COMPOUND), window);
        readPairs(all.getList("returns", Tag.TAG_COMPOUND), returns);
    }

    private static ListTag pairs(ArrayDeque<long[]> from) {
        ListTag list = new ListTag();
        for (long[] entry : from) {
            CompoundTag pair = new CompoundTag();
            pair.putLong("at", entry[0]);
            pair.putLong("n", entry[1]);
            list.add(pair);
        }
        return list;
    }

    private static void readPairs(ListTag list, ArrayDeque<long[]> into) {
        for (Tag t : list) {
            CompoundTag pair = (CompoundTag) t;
            into.add(new long[] {pair.getLong("at"), pair.getLong("n")});
        }
    }

}
