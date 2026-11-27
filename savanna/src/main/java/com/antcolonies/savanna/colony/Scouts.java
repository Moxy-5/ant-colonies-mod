package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.colony.Raids.Find;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * The colony's scouts' bookkeeping (the raid rewrite, step 5: moved out of {@link Raids}, which keeps the finds and
 * the raid day): who is out scouting now, whom the scout command sent, the ants that keep the job (real Megaponera
 * scouts are a small set of workers who scout again and again) and their upkeep, each scout's way and reach as the
 * colony last saw them, a scout's trip (planned, set out on, come home from, empty or with a find), the mound clue's
 * state and counts, and the scouts' lines in the raid diary. Owned by the colony's {@link Raids} and reached through
 * {@link Raids#scouts()}; whether a find is worth a raid stays the colony's one rule ({@link Raids#worthARaid}).
 * The walk itself is {@code MatabeleScoutGoal}'s; the scouts out of sight are {@link RaidsAway}'s.
 */
public class Scouts {
    private final Raids raids;
    private final Colony colony;

    Scouts(Raids raids, Colony colony) {
        this.raids = raids;
        this.colony = colony;
    }

    /** One line in the raid diary. */
    private void say(ServerLevel level, String what) {
        raids.diary(level, what);
    }

    /**
     * What a scout does on a trip: recheck a remembered spot ({@code find}), or search in its own direction
     * ({@code heading}, radians): out about {@code out} blocks, then a winding search, never past {@code reach} blocks
     * from the nest (its own reach when it set out).
     */
    public record Trip(@Nullable Find find, double heading, int out, int reach) {
    }
    /**
     * The colony's scouts' favourite directions and reaches, as last seen when they set out or came home (not saved: the
     * ants keep their own, {@code ScoutMemory}; this is the colony's view, to spread new scouts' directions and for the
     * colony list).
     */
    private final Map<UUID, Double> headings = new HashMap<>();
    private final Map<UUID, Integer> reaches = new HashMap<>();
    /**
     * R8's balance pass: a settled colony (one of new land, an old colony that has lived here for months: real colonies
     * of 100 to 2,300 are years old, Yusuf 2026) whose scouts know their whole foraging range: a new scout's reach starts
     * at {@code settled_range}, not {@code first_range} (a colony new to the place, the command's and the tests', and
     * later a split-off daughter, M9). Saved.
     */
    private boolean settled;
    /** R5: finds passed over as too poor since the colony was loaded (not saved; tests and the diary). */
    private int passedOverCount;
    /** The scouts out now (not saved: after a reload they are just ants again). */
    private final Set<UUID> outNow = new HashSet<>();
    /** Ants sent out by the scout command, whatever the hour or hunger (not saved). */
    private final Set<UUID> sent = new HashSet<>();
    /** The colony's scouts, the ones who scouted before, oldest first (saved; at most twice {@code most_scouts}). */
    private final Set<UUID> veterans = new LinkedHashSet<>();
    /** Since when a scout's place has stood open (game time, -1 none): a new ant takes it if no old scout comes soon. */
    private long openSince = -1;
    /** R7: ants the scout command sent with "anytime": their find raids at once, whatever the hour (not saved). */
    private final Set<UUID> sentAnyHour = new HashSet<>();
    /**
     * The raid code review, finding 16: since when each former scout has been missing from the world while the colony's
     * own land ticked (not saved). One missing a whole {@link #VETERAN_GRACE} died or was folded into the numbers out of
     * sight: it no longer counts as an ant that knows the way.
     */
    private final Map<UUID, Long> veteranMissingSince = new HashMap<>();
    /** How long a former scout may be missing before it leaves the job (ticks; a game day, my pick). */
    private static final long VETERAN_GRACE = 24000;
    /** BUGS 89: ants the scout command sent, and when to see whether they set out. */
    private final Map<UUID, Long> sentCheckAt = new HashMap<>();
    /**
     * R6, the mound as a clue: the living termite mounds the scouts ringed lately and found nothing round, by their
     * middle, and when (game time). Not saved (my pick: after a reload a mound may be ringed again, no harm).
     */
    private final Map<BlockPos, Long> moundsRinged = new HashMap<>();
    /** R6: the mounds a scout is ringing now, and who (not saved). */
    private final Map<BlockPos, UUID> moundsTaken = new HashMap<>();
    /** R6, tests and the colony list: mounds noticed, tunnels followed and finds made by the mound clue since loaded (not saved). */
    private int moundsNoticed;
    private int tunnelsFollowed;
    private int clueFinds;

    /**
     * The brain while away, step 4: a scout out as numbers is watched now: one major comes out of the numbers at the
     * ground there, with the way out it had as a number, and is one of the colony's scouts out from this moment (so
     * nothing sends it home before its search starts; {@code MatabeleScoutGoal} takes it within a second and searches
     * on from where it stands). A minor that came out goes straight back into the numbers (scouts are majors). False
     * if the colony wants no more scouts, the ground there is no place to stand, or no major is left inside.
     */
    boolean showScout(ServerLevel level, BlockPos where, double heading) {
        if (raids.numbers() == null || raids.column() != null || raids.waiting() != null || outNow.size() >= scoutsWanted()) {
            return true; // (nothing to show: not a failure)
        }
        BlockPos cell = RaidGround.groundCell(level, where.getX(), where.getZ());
        if (cell == null || !RaidGround.canStand(level, cell)) {
            return true; // (water, a trunk: this scout stays a number; another moment, another place)
        }
        MatabeleEntity scout = null;
        for (int tries = 0; tries < 8 && scout == null; tries++) {
            AbstractAntEntity mob = colony.population().comeOutAt(level, Caste.WORKER, cell);
            if (mob == null) {
                break;
            }
            if (mob instanceof MatabeleEntity ant && ant.hasMajorBody()) {
                scout = ant;
            } else {
                colony.away().foldInNow(level, mob);
            }
        }
        if (scout == null) {
            return false;
        }
        scout.scoutMemory().setHeading(heading);
        headings.put(scout.getUUID(), heading);
        outNow.add(scout.getUUID());
        say(level, "a scout out of sight is watched now: scout " + RaidGround.shortId(scout.getUUID()) + " is a real ant at "
                + cell.toShortString() + ", " + Math.round(RaidGround.flat(cell, colony.getCenter())) + " blocks out toward the "
                + compass(heading) + ", and searches on from there");
        return true;
    }

    /** The ants out scouting now (the raid view). */
    public Set<UUID> scoutsOut() {
        return Set.copyOf(outNow);
    }

    /**
     * The raid code review, finding 16: whether a former scout is gone for good: dead, or missing from the world (died
     * or folded into the numbers out of sight) for {@link #VETERAN_GRACE}. Seen alive, its count starts again.
     */
    private boolean veteranGone(ServerLevel level, UUID id, long now) {
        Entity entity = level.getEntity(id);
        if (entity != null) {
            veteranMissingSince.remove(id);
            return !entity.isAlive();
        }
        long since = veteranMissingSince.computeIfAbsent(id, key -> now);
        if (now - since <= VETERAN_GRACE) {
            return false;
        }
        veteranMissingSince.remove(id);
        say(level, RaidGround.shortId(id) + ", a former scout, has been missing " + VETERAN_GRACE / 1200
                + " minutes: it no longer counts as one that knows the way");
        return true;
    }

    /** BUGS 89: an ant the scout command sent that did not set out two seconds later says why (the goals it runs). */
    void checkSent(ServerLevel level) {
        long now = level.getGameTime();
        sentCheckAt.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            UUID id = entry.getKey();
            if (!outNow.contains(id)) {
                String runs = level.getEntity(id) instanceof AbstractAntEntity ant ? ant.runningGoals() : "(not in the world)";
                say(level, "scout command: " + RaidGround.shortId(id) + " has not set out 2 s later: it runs " + runs
                        + (mayScoutWhy(level, id)));
            }
            return true;
        });
    }

    private String mayScoutWhy(ServerLevel level, UUID id) {
        if (!(level.getEntity(id) instanceof MatabeleEntity ant)) {
            return "";
        }
        return fit(ant) ? "" : ", and it is not fit to scout now";
    }

    /** The scouts' search numbers (the defaults if the species file has none). */
    public SavannaNumbers.SearchNumbers search() {
        return colony.profile().section(SavannaNumbers.SEARCH).orElse(SavannaNumbers.SearchNumbers.DEFAULT);
    }

    /**
     * How far out the colony's scouts search now (blocks from the nest's middle): the farthest reach of its scouts (the
     * colony's reach is what its scouts do), the first range before any scout has been out.
     */
    public int range() {
        SavannaNumbers.ScoutNumbers numbers = raids.numbers();
        int first = numbers == null ? 0 : startReach(numbers);
        return reaches.values().stream().mapToInt(Integer::intValue).max().orElse(first);
    }

    /**
     * R8's balance pass: where a new scout's reach starts: the whole foraging range for a settled colony (its scouts
     * have searched this land for months), the first range for a colony new to the place.
     */
    public int startReach(SavannaNumbers.ScoutNumbers numbers) {
        return settled ? Math.min(numbers.mostRange(), Math.max(numbers.firstRange(), numbers.settledRange())) : numbers.firstRange();
    }

    /** A settled colony: one that has lived here a long time (new land's), its scouts knowing their whole range. */
    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    public boolean isSettled() {
        return settled;
    }

    /** Whether it is the scouting hours at the nest (all day, the user's pick 2026-11-03) and dry. */
    public boolean isScoutingTime(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = raids.numbers();
        return numbers != null && numbers.isScoutHour(colony.timeOfDay(level)) && !colony.rainsNow(level);
    }

    /** The diary: a line about this scout (its walk: the search starting, cut short, ...). */
    public void scoutSays(ServerLevel level, UUID scout, String what) {
        say(level, "scout " + RaidGround.shortId(scout) + " " + what);
    }

    /**
     * How many scouts the colony sends at once: one per {@code workers_per_scout} workers, at least 1, at most
     * {@code most_scouts} (the user's pick); R5, a hungry colony sends more scouts (Frank and Linsenmair 2017): a starving
     * one all of them, a nearly fed one {@code fed_scouts} of them, rounded up (my pick).
     */
    public int scoutsWanted() {
        SavannaNumbers.ScoutNumbers numbers = raids.numbers();
        if (numbers == null) {
            return 0;
        }
        int most = Math.max(1, Math.min(numbers.mostScouts(), colony.getCount(Caste.WORKER) / Math.max(1, numbers.workersPerScout())));
        double share = raids.choosing().fedScouts() + (1.0 - raids.choosing().fedScouts()) * (1.0 - raids.fedShare());
        return Math.max(1, Math.min(most, (int) Math.ceil(most * share - 1e-9)));
    }

    /**
     * Whether this ant may set out scouting now: one the scout command sent; otherwise in the raid hours, dry, the
     * colony hungry and short of scouts, and the ant fit (a major, whole, not fighting or hungry). The colony's old
     * scouts go first: a new ant takes an open place only once no fit old scout is about, or none came for
     * {@link #VETERAN_WAIT} ticks.
     */
    public boolean mayScout(ServerLevel level, MatabeleEntity ant) {
        if (!fit(ant) || raids.isRaider(ant.getUUID()) || raids.isGoingHome(ant.getUUID())) {
            if (outNow.contains(ant.getUUID())) {
                back(ant); // (review R11-16: one shown as a scout that cannot scout after all gives its place up)
            }
            return false;
        }
        if (outNow.contains(ant.getUUID())) {
            return true; // (a scout out of sight just shown as a real ant: counted already, it searches on)
        }
        if (sent.contains(ant.getUUID())) {
            return true;
        }
        if (ant.isBusyWithChore()) {
            return false; // (one feeding the queen or the young, or carrying, finishes first: another goes)
        }
        if (raids.column() != null || raids.waiting() != null || outNow.size() >= scoutsWanted() || !isScoutingTime(level) || !raids.isHungry()) {
            // (with a raid out, or a find waiting for the raid hours, the colony sends no new scouts: my pick)
            return false;
        }
        if (veterans.contains(ant.getUUID())) {
            return true;
        }
        long now = level.getGameTime();
        if (openSince < 0 || now - openSince > VETERAN_WAIT * 4) {
            openSince = now; // (a fresh opening: the old scouts get their chance first)
        }
        return now - openSince >= VETERAN_WAIT || !veteranReady(level);
    }

    /** How long (ticks) a new ant waits for one of the colony's old scouts to take an open scout's place. */
    private static final long VETERAN_WAIT = 200;

    /** Whether one of the colony's old scouts is here, fit and not out already. */
    private boolean veteranReady(ServerLevel level) {
        for (UUID id : veterans) {
            if (!outNow.contains(id) && level.getEntity(id) instanceof MatabeleEntity old && fit(old)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this ant is one of the colony's scouts (it scouted before). */
    public boolean isVeteran(UUID ant) {
        return veterans.contains(ant);
    }

    /**
     * Whether this ant is fit to scout: a major (the scouts in Longhurst and Howse 1979; my reading), whole, not busy
     * fighting, carrying nothing. A hungry one may scout (the raid code review, finding 3: a hungry colony's ants are
     * hungry, and kept in they could never find it food; my pick). Whole is the raid's own rule (review R6-3: one
     * with an open wound, or badly hurt, rests in the nest until it is tended, and another major goes).
     */
    private static boolean fit(MatabeleEntity ant) {
        return ant.hasMajorBody() && Raids.raiderFit(ant);
    }

    /**
     * It sets out on this trip (counted as a scout until {@link #back}), and is one of the colony's scouts from now on;
     * a recheck's spot is marked as on its way (no second scout to it meanwhile; finding 12).
     */
    public void setOut(MatabeleEntity ant, @Nullable Trip trip) {
        if (trip != null && trip.find() != null) {
            trip.find().checking = ant.getUUID();
        }
        if (trip != null) {
            headings.put(ant.getUUID(), ant.scoutMemory().hasHeading() ? ant.scoutMemory().heading() : trip.heading());
            reaches.put(ant.getUUID(), trip.reach());
        }
        if (ant.level() instanceof ServerLevel level) {
            say(level, "scout " + RaidGround.shortId(ant.getUUID()) + " sets out" + (sent.contains(ant.getUUID()) ? " (sent by the command)" : "")
                    + (veterans.contains(ant.getUUID()) ? ", an old scout" : ", a new scout")
                    + (trip == null ? "" : trip.find() != null ? ", to recheck the spot at " + trip.find().at().toShortString()
                    + ", " + Math.round(RaidGround.flat(trip.find().at(), colony.getCenter())) + " blocks out"
                    : ", toward the " + compass(trip.heading()) + " (its own way, " + Math.round(Math.toDegrees(trip.heading()))
                    + " degrees), walking out about " + trip.out() + " blocks, then searching, never past " + trip.reach() + " blocks out"));
        }
        outNow.add(ant.getUUID());
        openSince = -1;
        veterans.remove(ant.getUUID());
        veterans.add(ant.getUUID()); // (to the end: the most recent scout)
        SavannaNumbers.ScoutNumbers numbers = raids.numbers();
        int most = 2 * Math.max(1, numbers == null ? 3 : numbers.mostScouts());
        while (veterans.size() > most) {
            veterans.remove(veterans.iterator().next()); // (the longest unused leaves the job)
        }
    }

    /** It is back, or gave up. */
    public void back(MatabeleEntity ant) {
        back(ant, null);
    }

    /**
     * It is back, or gave up ({@code untold}: a find it never got home to tell, lost with it). One that gave up far from
     * home goes home along a trail by itself (the raid code review, finding 2).
     */
    public void back(MatabeleEntity ant, @Nullable BlockPos untold) {
        UUID id = ant.getUUID();
        for (Find find : raids.finds()) {
            if (id.equals(find.checking)) {
                find.checking = null;
            }
        }
        moundsTaken.values().removeIf(id::equals);
        if (outNow.contains(id) && ant.level() instanceof ServerLevel level) {
            say(level, "scout " + RaidGround.shortId(id) + " is back or gave up, at " + ant.blockPosition().toShortString());
            if (untold != null) {
                say(level, "scout " + RaidGround.shortId(id) + " never got home to tell of its find at " + untold.toShortString()
                        + ": the find is lost");
            }
            if (ant.isAlive() && !raids.isRaider(id) && !WayHome.isHome(level, ant, colony)) {
                raids.sendHome(level, ant, "a scout that gave up on its way home");
            }
        }
        outNow.remove(id);
        sent.remove(id);
    }

    /** Whether this ant is out scouting now. */
    public boolean isScouting(UUID ant) {
        return outNow.contains(ant);
    }

    /**
     * What this scout's trip is: the best remembered spot not checked lately (rechecked first: real scouts return to
     * their colony's good spots and recruit to them); otherwise a search in the scout's own favourite direction (a new
     * scout is given one, {@link #newHeading}), walking out a share of its reach (usually near, now and then far:
     * {@code raids.search}) and searching there, never past its reach. Null if no trip.
     */
    @Nullable
    public Trip plan(ServerLevel level, MatabeleEntity ant) {
        SavannaNumbers.ScoutNumbers numbers = raids.numbers();
        if (numbers == null) {
            return null;
        }
        long now = level.getGameTime();
        ScoutMemoryView memory = new ScoutMemoryView(ant, numbers, startReach(numbers));
        Find best = raids.finds().stream().filter(find -> now - find.checkedAt() >= Raids.RECHECK_AFTER && now - find.pickedAt >= Raids.RECHECK_AFTER
                        && (find.checking == null || !outNow.contains(find.checking)))
                .max(Comparator.comparingInt(Find::worth)).orElse(null);
        if (best != null) {
            // (no second scout to it while one is on its way; its check time is set when the check really happens,
            // finding 12; found empty, the scout searches round the spot)
            best.pickedAt = now;
            int away = (int) Math.round(RaidGround.flat(best.at(), colony.getCenter()));
            return new Trip(best, angleOf(best.at()), away, Math.max(memory.reach(), Math.min(numbers.mostRange(), away + search().findMargin())));
        }
        if (!ant.scoutMemory().hasHeading()) {
            double heading = newHeading(ant.getUUID());
            ant.scoutMemory().setHeading(heading);
            say(level, "scout " + RaidGround.shortId(ant.getUUID()) + " takes its own way out: toward the " + compass(heading)
                    + " (" + Math.round(Math.toDegrees(heading)) + " degrees; the colony's other scouts' ways " + otherWays(ant.getUUID()) + ")");
        }
        int reach = memory.reach();
        SavannaNumbers.SearchNumbers search = search();
        int out = (int) Math.round(reach * search.outShare(colony.random().nextDouble()));
        return new Trip(null, ant.scoutMemory().heading(), Math.max(4, out), reach);
    }

    /** A scout's reach, its own or, before its first trip, the colony's start reach ({@link #startReach}). */
    private record ScoutMemoryView(MatabeleEntity ant, SavannaNumbers.ScoutNumbers numbers, int start) {
        int reach() {
            return Math.min(numbers.mostRange(), Math.max(numbers.firstRange() / 2, ant.scoutMemory().reach(start)));
        }
    }

    /**
     * A new scout's favourite direction: at random, but spread from the colony's other scouts' (the best of
     * {@code spread_tries} random directions, the one farthest from the nearest other; my pick: so the colony's scouts do
     * not all share one side, as different real scouts head different ways).
     */
    private double newHeading(UUID scout) {
        List<Double> others = new ArrayList<>();
        headings.forEach((id, heading) -> {
            if (!id.equals(scout)) {
                others.add(heading);
            }
        });
        int tries = Math.max(1, search().spreadTries());
        double best = colony.random().nextDouble() * Math.PI * 2;
        double bestGap = gapTo(best, others);
        for (int i = 1; i < tries && !others.isEmpty(); i++) {
            double heading = colony.random().nextDouble() * Math.PI * 2;
            double gap = gapTo(heading, others);
            if (gap > bestGap) {
                best = heading;
                bestGap = gap;
            }
        }
        headings.put(scout, best);
        return best;
    }

    /** How far (radians) this direction is from the nearest of these (a full half turn if none). */
    public static double gapTo(double heading, List<Double> others) {
        double gap = Math.PI;
        for (double other : others) {
            gap = Math.min(gap, Math.abs(turnBetween(heading, other)));
        }
        return gap;
    }

    /** The turn (radians, minus a half turn to a half turn) from one direction to another. */
    public static double turnBetween(double from, double to) {
        double turn = (to - from) % (Math.PI * 2);
        if (turn > Math.PI) {
            turn -= Math.PI * 2;
        } else if (turn < -Math.PI) {
            turn += Math.PI * 2;
        }
        return turn;
    }

    /** The diary: the colony's other scouts' ways, as compass words. */
    private String otherWays(UUID scout) {
        List<String> ways = new ArrayList<>();
        headings.forEach((id, heading) -> {
            if (!id.equals(scout)) {
                ways.add(compass(heading));
            }
        });
        return ways.isEmpty() ? "none yet" : String.join(", ", ways);
    }

    /** The colony's scouts' favourite directions it knows of (tests). */
    public Map<UUID, Double> headingsForTests() {
        return Map.copyOf(headings);
    }

    /**
     * A scout is home (or gave up) from a search with nothing worth a raid. Only a trip it really searched counts (at
     * least {@code searched_share} of its trip time searching: the raid rework's search range bug, in its new form: a
     * scout that only set out, or was called home early by the hours, the rain or no way on, learns nothing). Then it
     * goes a bit farther next time (its reach grows {@code range_step}, up to {@code most_range}) and its direction
     * drifts a little; its reach already the most, its direction swings instead. True if it counted.
     */
    public boolean cameHomeEmpty(ServerLevel level, MatabeleEntity ant, Trip trip, int searchTicks, String why) {
        SavannaNumbers.ScoutNumbers numbers = raids.numbers();
        if (numbers == null) {
            return false;
        }
        SavannaNumbers.SearchNumbers search = search();
        String name = "scout " + RaidGround.shortId(ant.getUUID());
        int least = (int) Math.ceil(numbers.tripTicks() * search.searchedShare());
        if (searchTicks < least) {
            say(level, name + " comes home with nothing (" + why + ") after searching only " + searchTicks / 20 + " s of the "
                    + least / 20 + " s it takes to count: its way and reach stay as they were");
            return false;
        }
        ScoutMemoryView memory = new ScoutMemoryView(ant, numbers, startReach(numbers));
        int from = memory.reach();
        double fromHeading = ant.scoutMemory().hasHeading() ? ant.scoutMemory().heading() : trip.heading();
        ant.scoutMemory().setEmptyTrips(ant.scoutMemory().emptyTrips() + 1);
        String change;
        if (from < numbers.mostRange()) {
            int to = Math.min(numbers.mostRange(), from + Math.max(1, numbers.rangeStep()));
            ant.scoutMemory().setReach(to);
            ant.scoutMemory().setHeading(fromHeading + colony.random().nextGaussian() * Math.toRadians(search.drift()));
            change = "its reach grows from " + from + " to " + to + " blocks and its way drifts from the " + compass(fromHeading)
                    + " to the " + compass(ant.scoutMemory().heading()) + " (" + Math.round(Math.toDegrees(fromHeading)) + " to "
                    + Math.round(Math.toDegrees(ant.scoutMemory().heading())) + " degrees)";
        } else {
            ant.scoutMemory().setReach(from);
            ant.scoutMemory().setHeading(fromHeading + colony.random().nextGaussian() * Math.toRadians(search.swing()));
            change = "its reach is already the most, " + from + " blocks, so its way swings from the " + compass(fromHeading) + " to the "
                    + compass(ant.scoutMemory().heading()) + " (" + Math.round(Math.toDegrees(fromHeading)) + " to "
                    + Math.round(Math.toDegrees(ant.scoutMemory().heading())) + " degrees)";
        }
        headings.put(ant.getUUID(), ant.scoutMemory().heading());
        reaches.put(ant.getUUID(), ant.scoutMemory().reach(startReach(numbers)));
        say(level, name + " searched " + searchTicks / 20 + " s and found nothing worth a raid (" + why + ", "
                + ant.scoutMemory().emptyTrips() + " empty trip(s) in a row): " + change);
        return true;
    }

    /**
     * A scout took a find this far out: it keeps to that way (its direction turned to the find: site fidelity) and its
     * trips shrink back (or reach out) to just past the find ({@code find_margin} blocks, never under the first range).
     */
    public void foundBy(ServerLevel level, MatabeleEntity ant, BlockPos at) {
        SavannaNumbers.ScoutNumbers numbers = raids.numbers();
        if (numbers == null) {
            return;
        }
        int away = (int) Math.round(RaidGround.flat(at, colony.getCenter()));
        int from = new ScoutMemoryView(ant, numbers, startReach(numbers)).reach();
        int to = Math.max(numbers.firstRange(), Math.min(numbers.mostRange(), away + search().findMargin()));
        double fromHeading = ant.scoutMemory().hasHeading() ? ant.scoutMemory().heading() : angleOf(at);
        ant.scoutMemory().setReach(to);
        ant.scoutMemory().setHeading(angleOf(at));
        ant.scoutMemory().setEmptyTrips(0);
        headings.put(ant.getUUID(), ant.scoutMemory().heading());
        reaches.put(ant.getUUID(), to);
        say(level, "scout " + RaidGround.shortId(ant.getUUID()) + " found termites " + away + " blocks out toward the " + compass(angleOf(at))
                + ": it keeps to that way (" + Math.round(Math.toDegrees(fromHeading)) + " to " + Math.round(Math.toDegrees(angleOf(at)))
                + " degrees) and its reach " + (to < from ? "shrinks" : to > from ? "grows" : "stays") + " from " + from + " to " + to + " blocks");
    }

    /** The diary: a scout noticed a faint smell of termites and slows to search the place closely (area-restricted search). */
    public void scentNoticed(ServerLevel level, BlockPos at, String what, UUID scout) {
        say(level, "scout " + RaidGround.shortId(scout) + " smells " + what + " near " + at.toShortString() + ", "
                + Math.round(RaidGround.flat(at, colony.getCenter())) + " blocks out: it slows down and turns more, searching there closely");
    }

    /**
     * R5: a scout has looked a place over and reports what it is worth ({@link Raids#worthOf}); the colony decides
     * ({@link Raids#worthARaid}). Worth a raid: true, the scout runs home with it and keeps to that way
     * ({@link #foundBy}). Too poor for a raid this far out with the colony this hungry: false, it passes the place over
     * and searches on; at a remembered spot being rechecked that counts as an empty check.
     */
    public boolean looked(ServerLevel level, MatabeleEntity ant, BlockPos at, int worth, @Nullable Find rechecking) {
        int away = (int) Math.round(RaidGround.flat(at, colony.getCenter()));
        boolean taken = raids.worthARaid(at, worth);
        say(level, "scout " + RaidGround.shortId(ant.getUUID()) + (taken ? " takes the termites at " : " passes over termites at ")
                + at.toShortString() + ", " + away + " blocks out: worth " + worth + ", a raid that far needs "
                + String.format(Locale.ROOT, "%.1f", raids.need(at)) + " with the colony " + Math.round(raids.fedShare() * 100) + " percent fed");
        if (taken) {
            foundBy(level, ant, at); // (its own memory: it keeps to this way, its trips shrink back to the find)
            return true;
        }
        passedOverCount++;
        if (rechecking != null && raids.finds().contains(rechecking)) {
            raids.foundEmpty(level, rechecking);
        }
        return false;
    }

    /** R6: the numbers of the mound as a clue. */
    public SavannaNumbers.MoundClueNumbers moundClue() {
        return colony.profile().section(SavannaNumbers.MOUND_CLUE).orElse(SavannaNumbers.MoundClueNumbers.DEFAULT);
    }

    /**
     * R6: whether a scout may ring this living termite mound (by its middle) now: not ringed with nothing found within
     * {@code fade_ticks}, and no other scout out ringing it.
     */
    public boolean mayRingMound(ServerLevel level, BlockPos mound) {
        Long ringed = moundsRinged.get(mound);
        if (ringed != null && level.getGameTime() - ringed < moundClue().fadeTicks()) {
            return false;
        }
        UUID by = moundsTaken.get(mound);
        return by == null || !outNow.contains(by);
    }

    /** R6, the diary: a scout noticed a living termite mound and searches round it in rings. */
    public void ringsMound(ServerLevel level, BlockPos mound, int reach, int rings, int points, UUID scout) {
        moundsTaken.put(mound.immutable(), scout);
        moundsNoticed++;
        int away = (int) Math.round(RaidGround.flat(mound, colony.getCenter()));
        say(level, "scout " + RaidGround.shortId(scout) + " notices a living termite mound at " + mound.toShortString() + ", "
                + away + " blocks from the nest (its foot " + reach + " blocks from its middle), and searches round it in " + rings
                + " ring(s) out to " + moundClue().pastFoot() + " blocks past its foot (" + points + " points)");
    }

    /** R6, the diary: a scout starts the next ring round a mound. */
    public void ringOn(ServerLevel level, BlockPos mound, int ring, int pastFoot, UUID scout) {
        say(level, "scout " + RaidGround.shortId(scout) + " rings the termite mound at " + mound.toShortString() + ": ring " + (ring + 1)
                + ", " + pastFoot + " blocks past its foot");
    }

    /**
     * R6: a scout is done ringing a mound ({@code found}: it took a find there or on a tunnel from it). Nothing found,
     * the mound is left {@code fade_ticks}.
     */
    public void moundRinged(ServerLevel level, BlockPos mound, UUID scout, boolean found) {
        moundsTaken.remove(mound);
        if (!found) {
            moundsRinged.put(mound.immutable(), level.getGameTime());
            say(level, "scout " + RaidGround.shortId(scout) + " ringed the termite mound at " + mound.toShortString()
                    + " and found nothing worth a raid round it");
        }
    }

    /** R6, the diary: a scout smelt termites going along a tunnel to food and follows it outward. */
    public void followsTunnel(ServerLevel level, BlockPos smelt, BlockPos food, int points, UUID scout) {
        tunnelsFollowed++;
        say(level, "scout " + RaidGround.shortId(scout) + " smells termites going along a tunnel under " + smelt.toShortString()
                + " and follows it out, away from their mound, toward its end by " + food.toShortString() + " (" + points + " points)");
    }

    /** R6, the diary: a scout followed a tunnel to its end and found nothing worth a raid there. */
    public void tunnelEnd(ServerLevel level, BlockPos food, UUID scout) {
        say(level, "scout " + RaidGround.shortId(scout) + " followed the tunnel to its end by " + food.toShortString()
                + ": nothing worth a raid there");
    }

    /** R6, the diary: the find a scout takes came from the mound clue ({@code how}: ringing a mound, following a tunnel). */
    public void clueFind(ServerLevel level, BlockPos at, String how, UUID scout) {
        clueFinds++;
        say(level, "scout " + RaidGround.shortId(scout) + "'s find at " + at.toShortString() + " came from " + how);
    }

    /** R6, tests: mounds noticed, tunnels followed and finds made by the mound clue since the colony was loaded. */
    public int moundsNoticed() {
        return moundsNoticed;
    }

    public int tunnelsFollowed() {
        return tunnelsFollowed;
    }

    public int clueFinds() {
        return clueFinds;
    }

    /** R5: how many finds scouts passed over as too poor since the colony was loaded (tests). */
    public int passedOverCount() {
        return passedOverCount;
    }

    /** The compass word for a direction (the diary; Minecraft: +x east, +z south). */
    public static String compass(double angle) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        return names[Math.floorMod((int) Math.round(Math.toDegrees(angle) / 45.0), 8)];
    }

    public double angleOf(BlockPos at) {
        double angle = Math.atan2(at.getZ() - colony.getCenter().getZ(), at.getX() - colony.getCenter().getX());
        return angle < 0 ? angle + Math.PI * 2 : angle;
    }

    /**
     * The scout command: the fittest real major nearest the entrance sets out now, whatever the hour or the colony's
     * hunger; null if none is fit.
     */
    @Nullable
    public MatabeleEntity sendNow(ServerLevel level) {
        return sendNow(level, false);
    }

    /** Whether this ant was sent by the command (or the soak) "anytime": it keeps no scouting hours on that trip (BUGS 123). */
    public boolean wasSentAnyHour(UUID id) {
        return sentAnyHour.contains(id);
    }

    /**
     * The scout command: as {@link #sendNow(ServerLevel)}; R7: its find keeps the raid hours (after 19:00 it waits for
     * the morning) unless {@code anyHour} (its find raids at once, whatever the hour).
     */
    @Nullable
    public MatabeleEntity sendNow(ServerLevel level, boolean anyHour) {
        BlockPos entrance = colony.getCenter();
        MatabeleEntity ant = colony.population().realMembers(level).stream()
                .filter(member -> member instanceof MatabeleEntity matabele && fit(matabele) && !outNow.contains(member.getUUID())
                        && !raids.isRaider(member.getUUID()) && !raids.isGoingHome(member.getUUID()))
                .map(member -> (MatabeleEntity) member)
                .min(Comparator.comparing(MatabeleEntity::isBusyWithChore)
                        .thenComparingDouble(member -> member.blockPosition().distSqr(entrance))).orElse(null);
        if (ant != null) {
            sent.add(ant.getUUID());
            if (anyHour) {
                sentAnyHour.add(ant.getUUID());
            }
            sentCheckAt.put(ant.getUUID(), level.getGameTime() + 40); // (BUGS 89: two seconds later it should be out)
            say(level, "the scout command sends " + RaidGround.shortId(ant.getUUID()) + " (it runs " + ant.runningGoals() + ")");
        }
        return ant;
    }

    /** Whether the scout command sent this ant (R7: its find keeps the raid hours, whatever the hunger or the day's count). */
    boolean wasSent(UUID id) {
        return sent.contains(id);
    }

    /** Whether the colony still follows this ant as a scout out or one the command sent (the out-of-sight grace). */
    boolean tracks(UUID id) {
        return outNow.contains(id) || sent.contains(id);
    }

    /** The colony went out of sight: its real scouts are numbers now (out of sight the colony's scouts search as numbers). */
    void nobodyOut() {
        outNow.clear();
        sent.clear();
        sentAnyHour.clear();
    }

    /**
     * Once a second: scouts dead or long unseen leave the count (the raid code review, finding 1: one only out of sight
     * is kept a grace first), former scouts gone for good leave the job, and the colony's view of ways and reaches
     * keeps to the ants that have the job.
     */
    void upkeep(ServerLevel level, long now) {
        outNow.removeIf(id -> raids.goneFor(level, id, now));
        sent.removeIf(id -> !outNow.contains(id) && raids.goneFor(level, id, now));
        sentAnyHour.retainAll(sent);
        veterans.removeIf(id -> veteranGone(level, id, now));
        veteranMissingSince.keySet().retainAll(veterans);
        headings.keySet().retainAll(veterans);
        reaches.keySet().retainAll(veterans);
    }

    /** The colony list's words on the mound clue, or nothing before a scout noticed a mound. */
    String describeClue() {
        return moundsNoticed == 0 ? "" : "; mound clue: " + moundsNoticed + " mound(s) ringed, " + tunnelsFollowed
                + " tunnel(s) followed, " + clueFinds + " find(s)";
    }

    void save(CompoundTag tag) {
        ListTag old = new ListTag();
        veterans.forEach(id -> old.add(NbtUtils.createUUID(id)));
        tag.put("raidScouts", old);
        if (settled) {
            tag.putBoolean("raidSettled", true);
        }
    }

    void load(CompoundTag tag) {
        headings.clear();
        reaches.clear();
        settled = tag.getBoolean("raidSettled");
        veterans.clear();
        for (Tag t : tag.getList("raidScouts", Tag.TAG_INT_ARRAY)) {
            veterans.add(NbtUtils.loadUUID(t));
        }
    }

    void fillForTests() {
        veterans.add(new UUID(7, 11));
        veterans.add(new UUID(7, 12));
    }
}
