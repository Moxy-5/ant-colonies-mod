package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/**
 * M8 step 3, the Matabele raids ({@code docs/plans/m8-matabele.md}). Step 3a, the scouts: real Megaponera send lone
 * scouts out to search for termites feeding under cover; a scout that finds them looks the place over and runs home
 * laying a scent line, which the raid column follows out (step 3b). Here the colony keeps who is out scouting, what the
 * scouts have found (the good spots, each with the scent line home, rechecked first by later scouts and forgotten once
 * found empty twice or after a few days: the termites plug their holes and move their feeding away), which slices of
 * the land round the nest were searched empty lately, and how far out the scouts search (the range grows as the near
 * land comes up empty, up to the real ant's foraging range). Scouting is a job a few majors keep (real Megaponera
 * scouts are a small set of workers who scout again and again): the colony remembers its scouts and sends them first.
 * Each find remembers the scout who found it, who leads the raid there (step 3b). The scout's walk itself is
 * {@code MatabeleScoutGoal}.
 *
 * <p>Step 3b, the column: a scout home with a find recruits a raid there ({@link #found}), sized by the find and capped
 * by the colony's size and hunger, and the colony keeps the {@link RaidColumn} while it is out (one at a time).
 * Numbers: the species file's {@code raids.scouting} ({@link SavannaNumbers.ScoutNumbers}) and {@code raids.column}
 * ({@link SavannaNumbers.ColumnNumbers}).
 *
 * <p>Step 3c, home with the prey: a raid home with termites keeps its spot fresh, one home empty counts as an empty
 * check of it ({@link #raidHome}); raiders who left the column hurt limp home along its trail ({@link #limpOrder}).
 * Numbers: {@code raids.carry} ({@link SavannaNumbers.CarryNumbers}).
 *
 * <p>Raid rework R4, the scent trails ({@link ScentTrails}): a scout home with a find lays its line as a trail in the
 * world (unless a trail still smelt leads there already); a raid follows the trail to its find, and the column walking it
 * makes it a column's trail, lasting longer; a trail no longer smelt with its scout gone means nobody knows the way, so
 * no raid (the scouts must find the spot again). The player breaking a trail ({@link #blockChanged}) leaves a gap the
 * column casts about at. Numbers: {@code raids.trails} ({@link SavannaNumbers.TrailNumbers}).
 */
public class Raids {
    /** A good spot a scout found: where, how much termite it smelt there, when found and last checked, empty checks, by whom. */
    public static final class Find {
        private final BlockPos at;
        private int worth;
        private final long foundAt;
        private long checkedAt;
        private int misses;
        /** The scent line home, the nest end first. */
        private List<BlockPos> trail;
        /** The scout who last found it (it leads the raid there, step 3b), or null. */
        @Nullable
        private UUID scout;

        Find(BlockPos at, int worth, long foundAt, long checkedAt, int misses, List<BlockPos> trail, @Nullable UUID scout) {
            this.at = at;
            this.worth = worth;
            this.foundAt = foundAt;
            this.checkedAt = checkedAt;
            this.misses = misses;
            this.trail = trail;
            this.scout = scout;
        }

        public BlockPos at() {
            return at;
        }

        public int worth() {
            return worth;
        }

        public long checkedAt() {
            return checkedAt;
        }

        public int misses() {
            return misses;
        }

        public List<BlockPos> trail() {
            return trail;
        }

        @Nullable
        public UUID scout() {
            return scout;
        }
    }

    /** What a scout does on a trip: recheck a remembered spot ({@code find}), or search a slice ({@code slice}, its middle {@code angle}). */
    public record Trip(@Nullable Find find, int slice, double angle, int range) {
    }

    /** A remembered spot is not rechecked again for this long after a check (ticks: another scout just looked). */
    private static final long RECHECK_AFTER = 2000;

    private final Colony colony;
    private final List<Find> finds = new ArrayList<>();
    /** Slices of the land round the nest searched empty, and when. */
    private final Map<Integer, Long> searched = new HashMap<>();
    /** How far out the scouts search now (blocks from the nest's middle; 0 before the first trip). */
    private int range;
    /** The scouts out now (not saved: after a reload they are just ants again). */
    private final Set<UUID> scouts = new HashSet<>();
    /** Ants sent out by the scout command, whatever the hour or hunger (not saved). */
    private final Set<UUID> sent = new HashSet<>();
    /** The colony's scouts, the ones who scouted before, oldest first (saved; at most twice {@code most_scouts}). */
    private final Set<UUID> veterans = new LinkedHashSet<>();
    /** Since when a scout's place has stood open (game time, -1 none): a new ant takes it if no old scout comes soon. */
    private long openSince = -1;
    /** The raid out now, or null (not saved: after a reload the raiders are just ants outside, and go home). */
    @Nullable
    private RaidColumn column;
    /** The last raid home (tests and the colony list), or null. */
    @Nullable
    private RaidColumn last;
    /** A find a scout brought home between the raid hours: the raid sets out to it when they come, the colony hungry. */
    @Nullable
    private Find waiting;
    /**
     * Step 3c: raiders who left a column hurt, limping home along its trail by themselves (a real Megaponera that loses
     * legs or has a termite clinging falls behind, and makes its own way home on the scent line; carrying the hurt home
     * is step 5). Not saved: after a reload they are just ants outside, and go home.
     */
    private final Map<UUID, Limp> limping = new HashMap<>();
    /** R4: the colony's scent trails in the world (saved). */
    private final ScentTrails trails = new ScentTrails();
    /** Why the last raid could not set out (the raid command's answer), or null. */
    @Nullable
    private String whyNoRaid;

    /** One limping raider: the trail home it follows, and since when. */
    private record Limp(RaidColumn.Route route, long since) {
    }

    /** How far ahead of itself along the trail (blocks) a limper walks to at a time. */
    private static final double LIMP_STEP = 6.0;
    /** A limper is home this near the entrance (blocks; the column's own home line). */
    private static final double LIMP_HOME = 8.0;

    public Raids(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    public SavannaNumbers.ScoutNumbers numbers() {
        return colony.profile().section(SavannaNumbers.SCOUTING).orElse(null);
    }

    @Nullable
    public SavannaNumbers.ColumnNumbers columnNumbers() {
        return colony.profile().section(SavannaNumbers.COLUMN).orElse(null);
    }

    @Nullable
    public SavannaNumbers.CarryNumbers carryNumbers() {
        return colony.profile().section(SavannaNumbers.CARRY).orElse(null);
    }

    /** R4: the scent trail numbers (the defaults if the species file has none). */
    public SavannaNumbers.TrailNumbers trailNumbers() {
        return colony.profile().section(SavannaNumbers.TRAILS).orElse(SavannaNumbers.TrailNumbers.DEFAULT);
    }

    /** R4: the colony's scent trails. */
    public ScentTrails trails() {
        return trails;
    }

    /** The ants out scouting now (the raid view). */
    public Set<UUID> scoutsOut() {
        return Set.copyOf(scouts);
    }

    /** The raiders limping home by themselves now (the raid view). */
    public Set<UUID> limpers() {
        return Set.copyOf(limping.keySet());
    }

    /** Why the last raid could not set out, or null. */
    @Nullable
    public String whyNoRaid() {
        return whyNoRaid;
    }

    /**
     * R4: a block was dug or put here by a player: the scent on the ground there is gone (the trail broken). How many
     * trail points broke.
     */
    public int blockChanged(ServerLevel level, BlockPos pos) {
        return trails.blockChanged(pos, what -> say(level, what));
    }

    void tick(ServerLevel level) {
        trails.tick(level, level.getGameTime(), trailNumbers(), column == null ? null : column.trail(), what -> say(level, what));
        if (column != null) {
            column.update(level); // (the raiders ask it every few ticks; this keeps it going with none of them loaded)
            for (UUID hurt : column.takeHurt()) {
                limping.put(hurt, new Limp(column.back(), level.getGameTime()));
            }
            if (column.stage() == RaidColumn.Stage.DONE) {
                raidHome(level, column);
                last = column;
                column = null;
            }
        }
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        scouts.removeIf(id -> !(level.getEntity(id) instanceof AbstractAntEntity ant) || !ant.isAlive());
        sent.removeIf(id -> !(level.getEntity(id) instanceof AbstractAntEntity ant) || !ant.isAlive());
        finds.removeIf(find -> now - find.checkedAt > numbers.spotFadeTicks());
        veterans.removeIf(id -> level.getEntity(id) instanceof AbstractAntEntity ant && !ant.isAlive());
        searched.values().removeIf(when -> now - when > numbers.sliceFadeTicks());
        if (waiting != null && !finds.contains(waiting)) {
            waiting = null; // (forgotten meanwhile)
        }
        if (waiting != null && column == null && isRaidTime(level)) {
            if (!isHungry()) {
                waiting = null; // (fed by now: the spot stays remembered for the next hungry spell)
            } else if (startRaid(level, waiting) != null) {
                say(level, "the raid hours: the waiting find at " + column.find().at().toShortString() + " gets its raid");
                waiting = null;
            }
        }
        SavannaNumbers.CarryNumbers carry = carryNumbers();
        int limpTicks = carry == null ? RaidColumn.DEFAULT_CARRY.limpTicks() : carry.limpTicks();
        limping.entrySet().removeIf(entry -> now - entry.getValue().since() > limpTicks
                || !(level.getEntity(entry.getKey()) instanceof AbstractAntEntity ant) || !ant.isAlive());
    }

    /**
     * Step 3c: a raid is home. One that brought termites back keeps its spot fresh (the termites may well be there
     * again); one that came back empty counts as an empty check of the spot, forgotten after {@code spot_misses} such
     * (the next scouts, sent out at once while the colony is hungry, look elsewhere first).
     */
    private void raidHome(ServerLevel level, RaidColumn raid) {
        Raids.Find find = raid.find();
        if (!finds.contains(find)) {
            return;
        }
        if (raid.carried() > 0) {
            find.checkedAt = level.getGameTime();
            find.misses = 0;
        } else if (raid.lostTrail()) {
            // R4: turned home at a gap in the trail: the spot was never reached, so it is no empty check (a scout
            // rechecks it later and lays a new line).
        } else {
            foundEmpty(level, find);
        }
    }

    /** The last raid home, or null (tests and the colony list). */
    @Nullable
    public RaidColumn lastRaid() {
        return last;
    }

    /** Whether this ant limps home from a raid by itself (step 3c). */
    public boolean isLimping(UUID ant) {
        return limping.containsKey(ant);
    }

    /**
     * Where a limping raider should walk now: a little ahead of itself along the raid's trail home; null once it is home
     * (near the entrance or in the nest; it is let go) or not limping.
     */
    @Nullable
    public BlockPos limpOrder(ServerLevel level, MatabeleEntity ant) {
        Limp limp = limping.get(ant.getUUID());
        if (limp == null) {
            return null;
        }
        BlockPos entrance = colony.getCenter();
        if (RaidColumn.flat(ant.blockPosition(), entrance) <= LIMP_HOME && Math.abs(ant.getY() - entrance.getY()) <= LIMP_HOME
                || colony.nest().isInside(level, ant.blockPosition())) {
            limping.remove(ant.getUUID());
            return null;
        }
        double along = limp.route().along(ant.getX(), ant.getZ());
        return along + LIMP_STEP >= limp.route().length() ? entrance : RaidColumn.place(level, limp.route(), along + LIMP_STEP, 0.0);
    }

    /** Tests: this ant limps home along this column's trail. */
    public void limpForTests(MatabeleEntity ant, RaidColumn raid, long now) {
        limping.put(ant.getUUID(), new Limp(raid.back(), now));
    }

    /** How many slices the land round the nest is searched in. */
    private int slices(SavannaNumbers.ScoutNumbers numbers) {
        return Math.max(1, 360 / Math.max(1, numbers.sliceDegrees()));
    }

    /** The search range now (blocks). */
    public int range() {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return range > 0 || numbers == null ? range : numbers.firstRange();
    }

    /**
     * Whether the colony wants food: its prey pile holds less than a day's food (a layer about 10 termites, one termite
     * per ant a day, as the pile is eaten).
     */
    public boolean isHungry() {
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        return pile == null || pile.edible() * 10 < Math.max(1, colony.getCount(Caste.WORKER));
    }

    /** Whether it is the scouting hours at the nest (all day, the user's pick 2026-11-03) and dry. */
    public boolean isScoutingTime(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return numbers != null && numbers.isScoutHour(colony.timeOfDay(level)) && !colony.rainsNow(level);
    }

    /** Whether it is the raid hours at the nest (mornings and evenings, Yusuf 2026) and dry: the hours a raid may leave. */
    public boolean isRaidTime(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return numbers != null && numbers.isRaidHour(colony.timeOfDay(level)) && !colony.rainsNow(level);
    }

    /** The find a scout brought home outside the raid hours, waiting for them; null if none. */
    @Nullable
    public Find waiting() {
        return waiting;
    }

    /** How many scouts the colony sends at once: one per {@code workers_per_scout} workers, at least 1, at most {@code most_scouts}. */
    /** Raid rework R1, the raid diary: one {@code [raid]} line in the log for the colony as a whole (the column says its own). */
    private void say(ServerLevel level, String what) {
        long dayTime = colony.timeOfDay(level) % 24000;
        com.antcolonies.savanna.SavannaMod.LOGGER.info("[raid] nest {} at {} (tick {}): {}", colony.getCenter().toShortString(),
                String.format(java.util.Locale.ROOT, "%02d:%02d", (dayTime / 1000 + 6) % 24, (dayTime % 1000) * 60 / 1000),
                level.getGameTime(), what);
    }

    public int scoutsWanted() {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        return Math.max(1, Math.min(numbers.mostScouts(), colony.getCount(Caste.WORKER) / Math.max(1, numbers.workersPerScout())));
    }

    /**
     * Whether this ant may set out scouting now: one the scout command sent; otherwise in the raid hours, dry, the
     * colony hungry and short of scouts, and the ant fit (a major, whole, not fighting or hungry). The colony's old
     * scouts go first: a new ant takes an open place only once no fit old scout is about, or none came for
     * {@link #VETERAN_WAIT} ticks.
     */
    public boolean mayScout(ServerLevel level, MatabeleEntity ant) {
        if (scouts.contains(ant.getUUID())) {
            return true;
        }
        if (!fit(ant) || isRaider(ant.getUUID())) {
            return false;
        }
        if (sent.contains(ant.getUUID())) {
            return true;
        }
        if (column != null || waiting != null || scouts.size() >= scoutsWanted() || !isScoutingTime(level) || !isHungry()) {
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
            if (!scouts.contains(id) && level.getEntity(id) instanceof MatabeleEntity old && fit(old)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this ant is one of the colony's scouts (it scouted before). */
    public boolean isVeteran(UUID ant) {
        return veterans.contains(ant);
    }

    /** Whether this ant is fit to scout: a major (the scouts in Longhurst and Howse 1979; my reading), whole, not busy fighting, not hungry. */
    private static boolean fit(MatabeleEntity ant) {
        return ant.isAlive() && ant.hasMajorBody() && !ant.isCrippled() && !ant.clamp().isOn() && !ant.isDowned()
                && ant.getFightSpot() == null && !ant.isHungry();
    }

    /** It sets out (counted as a scout until {@link #back}), and is one of the colony's scouts from now on. */
    public void setOut(MatabeleEntity ant) {
        if (ant.level() instanceof ServerLevel level) {
            say(level, "scout " + ant.getUUID().toString().substring(0, 8) + " sets out" + (sent.contains(ant.getUUID()) ? " (sent by the command)" : "")
                    + (veterans.contains(ant.getUUID()) ? ", an old scout" : ", a new scout") + ", search range " + range());
        }
        scouts.add(ant.getUUID());
        openSince = -1;
        veterans.remove(ant.getUUID());
        veterans.add(ant.getUUID()); // (to the end: the most recent scout)
        SavannaNumbers.ScoutNumbers numbers = numbers();
        int most = 2 * Math.max(1, numbers == null ? 3 : numbers.mostScouts());
        while (veterans.size() > most) {
            veterans.remove(veterans.iterator().next()); // (the longest unused leaves the job)
        }
    }

    /** It is back, or gave up. */
    public void back(MatabeleEntity ant) {
        if (scouts.contains(ant.getUUID()) && ant.level() instanceof ServerLevel level) {
            say(level, "scout " + ant.getUUID().toString().substring(0, 8) + " is back or gave up, at " + ant.blockPosition().toShortString());
        }
        scouts.remove(ant.getUUID());
        sent.remove(ant.getUUID());
    }

    /** Whether the scout command sent this ant out (it keeps no hours). */
    public boolean wasSent(UUID ant) {
        return sent.contains(ant);
    }

    /** Whether this ant is out scouting now. */
    public boolean isScouting(UUID ant) {
        return scouts.contains(ant);
    }

    /**
     * What this scout's trip is: the best remembered spot not checked lately (rechecked first: real scouts return to
     * their colony's good spots); otherwise a slice not searched lately, by the colony's dice; with every slice searched
     * empty the range grows ({@code range_step}, up to {@code most_range}) and every slice is fresh again.
     */
    @Nullable
    public Trip plan(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return null;
        }
        long now = level.getGameTime();
        int slices = slices(numbers);
        Find best = finds.stream().filter(find -> now - find.checkedAt >= RECHECK_AFTER)
                .max(Comparator.comparingInt(Find::worth)).orElse(null);
        if (best != null) {
            best.checkedAt = now; // (no second scout to it at once)
            return new Trip(best, sliceOf(best.at, slices), angleOf(best.at), range());
        }
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < slices; i++) {
            if (!searched.containsKey(i)) {
                free.add(i);
            }
        }
        if (free.isEmpty()) {
            if (range() < numbers.mostRange()) {
                range = Math.min(numbers.mostRange(), range() + numbers.rangeStep());
                searched.clear();
                for (int i = 0; i < slices; i++) {
                    free.add(i);
                }
            } else {
                free.add(searched.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0));
            }
        }
        int slice = free.get(colony.random().nextInt(free.size()));
        searched.put(slice, now); // (taken: no two scouts in one slice; found empty, it stays marked)
        double width = Math.toRadians(360.0 / slices);
        return new Trip(null, slice, (slice + 0.5) * width, range());
    }

    private int sliceOf(BlockPos at, int slices) {
        double angle = angleOf(at);
        return Math.floorMod((int) Math.floor(angle / (Math.PI * 2 / slices)), slices);
    }

    private double angleOf(BlockPos at) {
        double angle = Math.atan2(at.getZ() - colony.getCenter().getZ(), at.getX() - colony.getCenter().getX());
        return angle < 0 ? angle + Math.PI * 2 : angle;
    }

    /**
     * A scout is home from a find: the spot remembered (a known one refreshed), with its scent line home (nest end
     * first), how much termite it smelt there and who found it. Its slice no longer counts as searched empty.
     */
    public void found(ServerLevel level, BlockPos at, int worth, List<BlockPos> trail, @Nullable UUID scout) {
        long now = level.getGameTime();
        SavannaNumbers.ScoutNumbers numbers = numbers();
        Find known = finds.stream().filter(find -> find.at.closerThan(at, numbers == null ? 5 : numbers.smell() * 2)).findFirst().orElse(null);
        if (known != null) {
            known.worth = worth;
            known.checkedAt = now;
            known.misses = 0;
            known.trail = List.copyOf(trail);
            known.scout = scout;
        } else {
            finds.add(new Find(at.immutable(), worth, now, now, 0, List.copyOf(trail), scout));
        }
        if (numbers != null) {
            searched.remove(sliceOf(at, slices(numbers)));
        }
        // R4: the scout's line home is a scent trail in the world now, unless a trail still smelt leads there already (the
        // raid follows that one: a column's trail is stronger than one scout's line).
        if (trails.liveTo(at, trailReach(), trailNumbers(), now) == null) {
            trails.lay(level, colony.getCenter(), at, trail, now, false);
        }
        // Step 3b: home with a find, the scout recruits a raid there: in the raid hours, dry, the colony hungry (or the
        // scout command's scout, whatever the hour), and no raid out already. Found between the raid hours (scouts search
        // all day), the find waits for them (tick), the colony hungry then.
        boolean commanded = scout != null && sent.contains(scout);
        Find made = known != null ? known : finds.get(finds.size() - 1);
        if (column == null && !colony.rainsNow(level) && (commanded || isRaidTime(level) && isHungry())) {
            say(level, "a scout (" + (scout == null ? "?" : scout.toString().substring(0, 8)) + ") is home from " + at.toShortString()
                    + ", worth " + worth + (commanded ? ", sent by the command" : "") + ": a raid sets out");
            RaidColumn started = startRaid(level, made);
            if (started == null) {
                say(level, "nobody fit to raid: the find is only remembered");
            } else if (waiting == made) {
                waiting = null;
            }
        } else if (column == null && !commanded && isHungry() && !isRaidTime(level)) {
            say(level, "a scout is home from " + at.toShortString() + ", worth " + worth + ", outside the raid hours: it waits for them");
            waiting = made;
        } else {
            say(level, "a scout is home from " + at.toShortString() + ", worth " + worth + ", no raid: " + (column != null ? "one is out already"
                    : colony.rainsNow(level) ? "it rains" : "the colony is not hungry"));
        }
    }

    /** R4: a trail leads to a find if it ends this near it (blocks: two smelling distances). */
    private double trailReach() {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return numbers == null ? 10.0 : numbers.smell() * 2.0;
    }

    /** The raid out now, or null. */
    @Nullable
    public RaidColumn column() {
        return column;
    }

    /** Whether this ant walks in the raid out now. */
    public boolean isRaider(UUID ant) {
        return column != null && column.has(ant) && column.stage() != RaidColumn.Stage.DONE;
    }

    /**
     * How far out the colony reaches while a raid is out (the find and a little past it, so its raiders out there still
     * count as the colony's real members); 0 with none out.
     */
    int reach() {
        if (column == null) {
            return 0;
        }
        BlockPos at = column.find().at();
        return (int) Math.ceil(Math.hypot(at.getX() - colony.getCenter().getX(), at.getZ() - colony.getCenter().getZ())) + 8;
    }

    /**
     * How many ants a raid to this find takes: {@code ants_per_worth} for each termite smelt there, at least
     * {@code least_raid}, and never more than the colony can spare: a share of its workers from {@code least_share}
     * (well fed) to {@code most_share} (starving), by its hunger (real raids take 30 to 75 percent of a colony, Yusuf 2026).
     */
    public int raidSize(Find find) {
        SavannaNumbers.ColumnNumbers numbers = columnNumbers();
        if (numbers == null) {
            return 0;
        }
        int workers = Math.max(1, colony.getCount(Caste.WORKER));
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        double fed = pile == null ? 0.0 : Math.min(1.0, pile.edible() * 10.0 / workers);
        double share = numbers.leastShare() + (numbers.mostShare() - numbers.leastShare()) * (1.0 - fed);
        int spare = Math.max(1, (int) Math.floor(workers * share));
        return Math.min(spare, Math.max(numbers.leastRaid(), find.worth * numbers.antsPerWorth()));
    }

    /**
     * A raid sets out to this find: the scout leads if it is fit (else the major nearest the entrance), then the real
     * workers nearest the entrance, up to {@code most_real} (majors and minors as they come), and the rest of the raid's
     * size from the numbers inside (they go along as numbers: drawn-only ants, step 4). Null if nobody is fit.
     */
    @Nullable
    private RaidColumn startRaid(ServerLevel level, Find find) {
        SavannaNumbers.ColumnNumbers numbers = columnNumbers();
        if (numbers == null) {
            return null;
        }
        int size = raidSize(find);
        BlockPos entrance = colony.getCenter();
        List<MatabeleEntity> fit = new ArrayList<>(colony.population().realMembers(level).stream()
                .filter(member -> member instanceof MatabeleEntity matabele && raiderFit(matabele)
                        && (!scouts.contains(member.getUUID()) || member.getUUID().equals(find.scout))) // (scouts out searching stay at it)
                .map(member -> (MatabeleEntity) member)
                .sorted(Comparator.comparingDouble(member -> member.blockPosition().distSqr(entrance))).toList());
        MatabeleEntity scoutHere = fit.stream().filter(ant -> ant.getUUID().equals(find.scout)).findFirst().orElse(null);
        MatabeleEntity leader = scoutHere != null ? scoutHere : fit.stream().filter(MatabeleEntity::hasMajorBody).findFirst().orElse(null);
        if (leader == null) {
            whyNoRaid = "nobody fit to raid (real workers only; not hurt, clamped or fighting)";
            return null;
        }
        // R4: the raid follows the scent trail to the find if it is still smelt (whoever leads); with the trail gone only
        // the scout knows the way, and it lays a new one as it leads; with the scout gone too, nobody knows the way.
        long now = level.getGameTime();
        ScentTrails.Trail trail = trails.liveTo(find.at, trailReach(), trailNumbers(), now);
        if (trail == null) {
            if (scoutHere == null) {
                whyNoRaid = "the scent trail to " + find.at.toShortString() + " has faded and its scout is not here to lead: nobody knows the way";
                say(level, "no raid to " + find.at.toShortString() + ": " + whyNoRaid);
                return null;
            }
            trail = trails.lay(level, colony.getCenter(), find.at, find.trail, now, false);
            say(level, "the trail to " + find.at.toShortString() + " has faded: its scout leads from memory, laying a new one");
        }
        fit.remove(leader);
        fit.add(0, leader);
        int real = Math.min(Math.min(size, numbers.mostReal()), fit.size());
        int along = Math.max(0, Math.min(size - real, colony.population().inside(Caste.WORKER)));
        // R3, the marching order (Frank 2017 via AntWiki): the scout in front, the colony's other scouts right behind it,
        // then the rest, the majors at the back as the rear guard.
        List<MatabeleEntity> going = new ArrayList<>(fit.subList(0, real));
        going.sort(Comparator.comparingInt(ant -> ant == leader ? 0 : veterans.contains(ant.getUUID()) ? 1 : ant.hasMajorBody() ? 3 : 2));
        column = new RaidColumn(level, colony, numbers, carryNumbers(), trailNumbers(), find, trail, going, along, now);
        column.sayStart(level);
        whyNoRaid = null;
        find.scout = leader.getUUID(); // (it leads: the one to lead the next raid there too)
        return column;
    }

    /** Whether this ant may go raiding: a worker, whole, not clamped, not downed or fighting. */
    private static boolean raiderFit(MatabeleEntity ant) {
        return ant.isAlive() && ant.isColonyWorker() && !ant.isCrippled() && !ant.clamp().isOn() && !ant.isDowned()
                && !ant.fightsNoMore() && ant.getFightSpot() == null;
    }

    /** The raid command: a raid sets out now to the best known find, whatever the hour or hunger; null if none could. */
    @Nullable
    public RaidColumn raidNow(ServerLevel level) {
        if (column != null || finds.isEmpty()) {
            return null;
        }
        return startRaid(level, finds().get(0));
    }

    /** A raid to this find sets out now (tests: a find put in by hand, with its scent line and scout). */
    @Nullable
    public RaidColumn raidForTests(ServerLevel level, BlockPos at, int worth, List<BlockPos> trail, @Nullable UUID scout) {
        Find find = new Find(at.immutable(), worth, level.getGameTime(), level.getGameTime(), 0, List.copyOf(trail), scout);
        finds.add(find);
        trails.lay(level, colony.getCenter(), at, trail, level.getGameTime(), false); // (R4: the scout's line, laid as it ran home)
        return startRaid(level, find);
    }

    /** A remembered spot was checked and nothing smelt there: forgotten after {@code spot_misses} such checks. */
    public void foundEmpty(ServerLevel level, Find find) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        find.checkedAt = level.getGameTime();
        if (++find.misses >= (numbers == null ? 2 : numbers.spotMisses())) {
            finds.remove(find);
        }
    }

    /** The good spots known now, best first. */
    public List<Find> finds() {
        return finds.stream().sorted(Comparator.comparingInt(Find::worth).reversed()).toList();
    }

    /**
     * The scout command: the fittest real major nearest the entrance sets out now, whatever the hour or the colony's
     * hunger; null if none is fit.
     */
    @Nullable
    public MatabeleEntity sendNow(ServerLevel level) {
        BlockPos entrance = colony.getCenter();
        MatabeleEntity ant = colony.population().realMembers(level).stream()
                .filter(member -> member instanceof MatabeleEntity matabele && fit(matabele) && !scouts.contains(member.getUUID()))
                .map(member -> (MatabeleEntity) member)
                .min(Comparator.comparingDouble(member -> member.blockPosition().distSqr(entrance))).orElse(null);
        if (ant != null) {
            sent.add(ant.getUUID());
        }
        return ant;
    }

    /** The colony list's line: scouts out, the good spots known, how far they search. */
    public String describe(ServerLevel level) {
        StringBuilder line = new StringBuilder(", raids: ").append(scouts.size()).append(" scout(s) out, search range ")
                .append(range()).append(" blocks, ").append(finds.size()).append(" good spot(s)");
        List<Find> best = finds();
        for (int i = 0; i < Math.min(3, best.size()); i++) {
            Find find = best.get(i);
            int away = (int) Math.round(Math.hypot(find.at.getX() - colony.getCenter().getX(), find.at.getZ() - colony.getCenter().getZ()));
            line.append(i == 0 ? " (" : "; ").append(find.at.toShortString()).append(", ").append(away).append(" out, worth ")
                    .append(find.worth).append(", line of ").append(find.trail.size());
        }
        if (!best.isEmpty()) {
            line.append(")");
        }
        if (column == null && last != null) {
            line.append("; the last raid brought home ").append(last.carried()).append(" termite(s)");
        }
        if (!limping.isEmpty()) {
            line.append("; ").append(limping.size()).append(" hurt raider(s) limping home");
        }
        line.append(trails.describe(trailNumbers(), level.getGameTime()));
        if (column != null) {
            line.append(column.describe());
        }
        return line.toString();
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Find find : finds) {
            CompoundTag one = new CompoundTag();
            one.put("at", NbtUtils.writeBlockPos(find.at));
            one.putInt("worth", find.worth);
            one.putLong("foundAt", find.foundAt);
            one.putLong("checkedAt", find.checkedAt);
            one.putInt("misses", find.misses);
            one.put("trail", new LongArrayTag(find.trail.stream().mapToLong(BlockPos::asLong).toArray()));
            if (find.scout != null) {
                one.putUUID("scout", find.scout);
            }
            list.add(one);
        }
        tag.put("raidFinds", list);
        CompoundTag slices = new CompoundTag();
        searched.forEach((slice, when) -> slices.putLong(Integer.toString(slice), when));
        tag.put("raidSearched", slices);
        tag.putInt("raidRange", range);
        tag.putInt("raidWaiting", waiting == null ? -1 : finds.indexOf(waiting));
        ListTag old = new ListTag();
        veterans.forEach(id -> old.add(NbtUtils.createUUID(id)));
        tag.put("raidScouts", old);
        trails.save(tag);
    }

    void load(CompoundTag tag) {
        finds.clear();
        for (Tag t : tag.getList("raidFinds", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            BlockPos at = NbtUtils.readBlockPos(one, "at").orElse(null);
            if (at == null) {
                continue;
            }
            List<BlockPos> trail = new ArrayList<>();
            for (long point : one.getLongArray("trail")) {
                trail.add(BlockPos.of(point));
            }
            finds.add(new Find(at, one.getInt("worth"), one.getLong("foundAt"), one.getLong("checkedAt"), one.getInt("misses"), trail,
                    one.hasUUID("scout") ? one.getUUID("scout") : null));
        }
        searched.clear();
        CompoundTag slices = tag.getCompound("raidSearched");
        for (String key : slices.getAllKeys()) {
            try {
                searched.put(Integer.parseInt(key), slices.getLong(key));
            } catch (NumberFormatException ignored) {
                // (an odd key: skipped)
            }
        }
        range = tag.getInt("raidRange");
        int wait = tag.contains("raidWaiting") ? tag.getInt("raidWaiting") : -1;
        waiting = wait >= 0 && wait < finds.size() ? finds.get(wait) : null;
        veterans.clear();
        for (Tag t : tag.getList("raidScouts", Tag.TAG_INT_ARRAY)) {
            veterans.add(NbtUtils.loadUUID(t));
        }
        trails.load(tag);
    }

    void fillForTests(ServerLevel level) {
        BlockPos at = colony.getCenter().offset(20, 0, 6);
        finds.add(new Find(at, 14, 100, 140, 1, List.of(colony.getCenter(), colony.getCenter().east(2), at.west(2)),
                new UUID(7, 11)));
        searched.put(2, 120L);
        range = 48;
        veterans.add(new UUID(7, 11));
        veterans.add(new UUID(7, 12));
        trails.fillForTests(colony.getCenter());
    }

    /** Whether a termite there counts as feeding (out of its mound): not within its colony's mound and a little round it. */
    public static boolean outOfMound(ServerLevel level, BlockPos at, List<MoundAt> mounds) {
        for (MoundAt mound : mounds) {
            if (mound.center.closerThan(at, mound.reach + 2.0)) {
                return false;
            }
        }
        return true;
    }

    /** R4: a block dug or put here: every Matabele colony within 128 blocks hears of it (its scent trails may break). */
    public static void blockChangedNear(ServerLevel level, BlockPos pos) {
        for (Colony other : com.antcolonies.colony.ColonyManager.get(level).getColonies()) {
            if (MatabeleEntity.SPECIES.equals(other.getSpecies()) && other.getCenter().closerThan(pos, 128)) {
                other.part(SavannaParts.RAIDS).ifPresent(raids -> raids.blockChanged(level, pos));
            }
        }
    }

    /** A living termite mound near: its middle and how far its foot reaches (blocks). */
    public record MoundAt(BlockPos center, int reach) {
    }

    /** The living termite colonies within {@code within} blocks of {@code at}, with their mounds. */
    public static List<Colony> termiteColonies(ServerLevel level, BlockPos at, double within) {
        List<Colony> near = new ArrayList<>();
        for (Colony other : com.antcolonies.colony.ColonyManager.get(level).getColonies()) {
            if (SavannaSpecies.MACROTERMES.equals(other.getSpecies()) && other.getCenter().closerThan(at, within)) {
                near.add(other);
            }
        }
        return near;
    }
}
