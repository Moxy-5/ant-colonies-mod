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

import com.antcolonies.colony.AwayLife;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.goal.MatabeleRaidGoal;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.species.SavannaNumbers;
import com.antcolonies.watch.Watch;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * M8 step 3, the Matabele raids ({@code docs/plans/m8-matabele.md}). Step 3a, the scouts: real Megaponera send lone
 * scouts out to search for termites feeding under cover; a scout that finds them looks the place over and runs home
 * laying a scent line, which the raid column follows out (step 3b). Here the colony keeps who is out scouting, what the
 * scouts have found (the good spots, each with the scent line home, rechecked first by later scouts and forgotten once
 * found empty twice or after a few days: the termites plug their holes and move their feeding away). It keeps no map
 * of the land searched: each scout remembers its own direction and how far out it searches ({@code ScoutMemory}, raid
 * scouts search like real ants, 2026-11-04), and the colony's reach is what its scouts do. Scouting is a job a few majors keep (real Megaponera
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
 * check of it ({@link #raidHome}); ants let go out in the field go home along a trail by themselves ({@link #wayHomeOrder}).
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
        /**
         * The raid code review, finding 12: the scout on its way to recheck it now (not saved), and when it was picked
         * for a trip. {@code checkedAt} is set only when a recheck really happens (found again, or found empty).
         */
        @Nullable
        private UUID checking;
        private long pickedAt = -RECHECK_AFTER;

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

    /**
     * What a scout does on a trip: recheck a remembered spot ({@code find}), or search in its own direction
     * ({@code heading}, radians): out about {@code out} blocks, then a winding search, never past {@code reach} blocks
     * from the nest (its own reach when it set out).
     */
    public record Trip(@Nullable Find find, double heading, int out, int reach) {
    }

    /** A remembered spot is not rechecked again for this long after a check (ticks: another scout just looked). */
    private static final long RECHECK_AFTER = 2000;

    private final Colony colony;
    private final List<Find> finds = new ArrayList<>();
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
    /** R7: the waiting find was brought home by the scout command's scout (it waits for the raid hours, hungry or not). */
    private boolean waitingCommanded;
    /** R7: ants the scout command sent with "anytime": their find raids at once, whatever the hour (not saved). */
    private final Set<UUID> sentAnyHour = new HashSet<>();
    /**
     * Step 3c and the raid code review, finding 2: ants let go out in the field going home along a trail by themselves
     * (a real Megaponera that loses legs, has a termite clinging or falls behind makes its own way home on the scent
     * line; carrying the hurt home is step 5): the hurt, the stuck, the column's leftovers, a raid lost from sight, a
     * scout that gave up. Not saved: after a reload the sweep ({@link #SWEEP_EVERY}) finds them outside and sends them home.
     */
    private final WayHome wayHome = new WayHome();
    /** The raid code review, finding 1: since when scouts and ants going home have been out of sight (kept a grace first). */
    private final Map<UUID, Long> unseenSince = new HashMap<>();
    /** Every this many ticks the colony's real ants left outside with nothing to do there are sent home (my pick, 5 s). */
    private static final long SWEEP_EVERY = 100;
    /** When the next sweep is due (game time; not saved: the first comes at once after a load). */
    private long nextSweepAt;
    /**
     * The raid code review, finding 16: since when each former scout has been missing from the world while the colony's
     * own land ticked (not saved). One missing a whole {@link #VETERAN_GRACE} died or was folded into the numbers out of
     * sight: it no longer counts as an ant that knows the way.
     */
    private final Map<UUID, Long> veteranMissingSince = new HashMap<>();
    /** How long a former scout may be missing before it leaves the job (ticks; a game day, my pick). */
    private static final long VETERAN_GRACE = 24000;
    /** BUGS 89: when to see whether the raiders took up their raid (game time, -1 none). */
    private long raidCheckAt = -1;
    /** BUGS 89: ants the scout command sent, and when to see whether they set out. */
    private final Map<UUID, Long> sentCheckAt = new HashMap<>();
    /** R4: the colony's scent trails in the world (saved). */
    private final ScentTrails trails = new ScentTrails();
    /** Why the last raid could not set out (the raid command's answer), or null. */
    @Nullable
    private String whyNoRaid;
    /** R7: the last raid could not set out because its spot is in land not loaded (the find waits). */
    private boolean unloadedFind;
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
    /** M8 step 3f: the raids while the colony is away, as numbers (saved). */
    private final RaidsAway away;


    public Raids(Colony colony) {
        this.colony = colony;
        this.away = new RaidsAway(this, colony);
    }

    /** M8 step 3f: the raids out of sight. */
    public RaidsAway away() {
        return away;
    }

    /**
     * M8 step 3f: once a second while the colony is away. The finds fade as at home; a real raid column out when the
     * colony went away goes on as numbers; then the raids out of sight ({@link RaidsAway}).
     */
    void tickAway(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        finds.removeIf(find -> now - find.checkedAt > numbers.spotFadeTicks());
        if (waiting != null && !finds.contains(waiting)) {
            waiting = null;
        }
        if (column != null) {
            if (column.stage() != RaidColumn.Stage.DONE) {
                away.fromColumn(level, column);
            }
            last = column;
            column = null;
            raidCheckAt = -1;
        }
        scouts.clear(); // (the real scouts are numbers now: out of sight the colony's scouts search as numbers)
        sent.clear();
        sentAnyHour.clear();
        away.tick(level);
    }

    /** The diary for {@link RaidsAway}. */
    void diary(ServerLevel level, String what) {
        say(level, what);
    }

    /** The remembered find at this spot (within the trail reach), or null. */
    @Nullable
    Find findAt(BlockPos at) {
        double within = trailReach();
        return finds.stream().filter(find -> find.at.closerThan(at, within)).findFirst().orElse(null);
    }

    /** A remembered find was raided and termites were there: it counts as checked now. */
    void checked(Find find, long now) {
        find.checkedAt = now;
        find.misses = 0;
    }

    /** A raid set out to this find: it no longer waits for the raid hours. */
    void forgetWaiting(Find find) {
        if (waiting == find) {
            waiting = null;
            waitingCommanded = false;
        }
    }

    /** A raid walked the scent trail to this spot: it is fresh again (if one is still smelt). */
    void walkTrail(BlockPos at, long now) {
        ScentTrails.Trail trail = trails.liveTo(at, trailReach(), trailNumbers(), now);
        if (trail != null) {
            trail.walked(now);
        }
    }

    /**
     * M8 step 3f: a number scout out of sight found termites feeding here: the spot remembered, its line home laid as a
     * scent trail only while the nest's land is loaded (no block of unloaded land is read).
     */
    void remember(ServerLevel level, BlockPos at, int worth, List<BlockPos> line) {
        long now = level.getGameTime();
        finds.add(new Find(at.immutable(), worth, now, now, 0, List.copyOf(line), null));
        if (level.hasChunkAt(colony.getCenter()) && level.hasChunkAt(at) && trails.liveTo(at, trailReach(), trailNumbers(), now) == null) {
            trails.lay(level, colony.getCenter(), at, line, now, false);
        }
    }

    /** M8 step 3f, tests: a find remembered by hand (a scout's find, no trail laid). */
    public void rememberForTests(BlockPos at, int worth, long now) {
        finds.add(new Find(at.immutable(), worth, now, now, 0, RaidsAway.line(colony.getCenter(), at), null));
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

    /** The ants going home by themselves now (the raid view). */
    public Set<UUID> limpers() {
        return wayHome.ids();
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
        RaidCost.begin();
        try {
            away.tickHere(level); // (M8 step 3f: a raid out of sight when the colony came back finishes as numbers)
            tickTimed(level);
        } finally {
            RaidCost.end();
        }
    }

    private void tickTimed(ServerLevel level) {
        trails.tick(level, level.getGameTime(), trailNumbers(), column == null ? null : column.trail(), what -> say(level, what));
        if (column != null) {
            if (column.stage() != RaidColumn.Stage.DONE && !column.keepsNoHours() && !column.turnedAtDusk()) {
                if (isDusk(level)) {
                    column.callDusk(); // (R7: raids end by dusk: it stops where it is, picks up what it stung and walks home)
                } else if (colony.rainsNow(level)) {
                    // M8 step 3e: rain turns a raid home the same way (it washes the scent trail out; real raids are not
                    // seen in rain, Yusuf 2026; turning home mid-raid my pick)
                    column.callRain();
                }
            }
            try {
                column.update(level); // (the raiders ask it every few ticks; this keeps it going with none of them loaded)
            } catch (RuntimeException e) {
                // B7 (nothing waits for ever): a column that cannot be worked out would hold its raiders for ever; it is
                // given up and everyone walks home, and the error goes to the log for the bug list.
                SavannaMod.LOGGER.error("[raid] colony {}: the raid column's update failed; the raid is given up",
                        colony.getId(), e);
                say(level, "the raid's update failed (" + e + "): the raid is given up, everyone walks home");
                column.abandon(level, e.toString());
            }
            for (RaidColumn.LetGo one : column.takeLetGo()) {
                wayHome.send(one.ant(), column.back(), level.getGameTime(), one.why());
                say(level, RaidGround.shortId(one.ant()) + " goes home along the trail by itself (" + one.why() + ")");
            }
            if (raidCheckAt >= 0 && level.getGameTime() >= raidCheckAt) {
                raidCheckAt = -1;
                checkRaiders(level, column);
            }
            if (column.stage() == RaidColumn.Stage.DONE) {
                raidHome(level, column);
                last = column;
                column = null;
            }
        }
        checkSent(level);
        // (once a second: the colony's parts tick once a second; the raid code review, finding 8)
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        // (the raid code review, finding 1: one only out of sight is kept a grace first; only the dead or long unseen go)
        scouts.removeIf(id -> goneFor(level, id, now));
        sent.removeIf(id -> !scouts.contains(id) && goneFor(level, id, now));
        sentAnyHour.retainAll(sent);
        finds.removeIf(find -> now - find.checkedAt > numbers.spotFadeTicks());
        veterans.removeIf(id -> veteranGone(level, id, now));
        veteranMissingSince.keySet().retainAll(veterans);
        headings.keySet().retainAll(veterans);
        reaches.keySet().retainAll(veterans);
        if (waiting != null && !finds.contains(waiting)) {
            waiting = null; // (forgotten meanwhile)
        }
        if (waiting != null && column == null && isRaidTime(level)) {
            if (!isHungry() && !waitingCommanded) {
                waiting = null; // (fed by now: the spot stays remembered for the next hungry spell)
            } else if (!waitingCommanded && !dayHasRoom(level)) {
                // (M8 step 3e: the day's raids are made; the find waits for tomorrow's raid hours)
            } else if (startRaid(level, waiting, !waitingCommanded) != null) {
                say(level, "the raid hours: the waiting find at " + column.find().at().toShortString() + " gets its raid"
                        + (waitingCommanded ? " (brought home by the scout command's scout)" : ""));
                waiting = null;
                waitingCommanded = false;
            }
        }
        SavannaNumbers.CarryNumbers carry = carryNumbers();
        int limpTicks = carry == null ? SavannaNumbers.CarryNumbers.DEFAULT.limpTicks() : carry.limpTicks();
        wayHome.forgetIf(id -> now - wayHome.since(id) > limpTicks || goneFor(level, id, now));
        if (now >= nextSweepAt) {
            nextSweepAt = now + SWEEP_EVERY; // (a clock of its own, no game-time remainder: the raid code review, finding 8)
            sweep(level, now);
        }
        unseenSince.keySet().removeIf(id -> !scouts.contains(id) && !sent.contains(id) && !wayHome.isGoingHome(id));
    }

    /**
     * The raid code review, finding 1: whether this ant is gone for the raids: dead, or out of sight (not in the world,
     * or in land not ticking now) longer than the colony's away grace ({@code AwayLife}, a minute). The grace is counted
     * from when the colony first missed it while its own land ticked, so a colony coming back into sight drops nobody
     * at once.
     */
    private boolean goneFor(ServerLevel level, UUID id, long now) {
        Entity entity = level.getEntity(id);
        if (entity != null && !entity.isAlive()) {
            unseenSince.remove(id);
            return true;
        }
        if (entity != null && level.isPositionEntityTicking(entity.blockPosition())) {
            unseenSince.remove(id);
            return false;
        }
        long since = unseenSince.computeIfAbsent(id, key -> now);
        return now - since > AwayLife.GRACE_TICKS;
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

    /**
     * The sweep only sends home ants out in the field, farther than this from the entrance (blocks, flat; my pick): nearer,
     * an ant is in or round its own nest (a dead mound's rooms lie a few blocks off its chimney) and finds its own way in.
     */
    private static final double SWEEP_FROM = 16.0;

    /**
     * The raid code review, finding 2: the colony's real ants left outside, far from the entrance, with no job out there
     * any more (a raid that ended while they were out of sight, a reload, a goal that let go) are sent home along a trail.
     */
    private void sweep(ServerLevel level, long now) {
        for (AbstractAntEntity member : colony.population().realMembers(level)) {
            if (!(member instanceof MatabeleEntity ant) || !ant.isAlive() || ant.isDowned() || ant.getFightSpot() != null
                    || !level.isPositionEntityTicking(ant.blockPosition())) {
                continue;
            }
            UUID id = ant.getUUID();
            if (scouts.contains(id) || isRaider(id) || wayHome.isGoingHome(id) || ant.hasFullMandibles() || ant.mayGoOut()
                    || WayHome.isHome(level, ant, colony) || RaidGround.flat(ant.blockPosition(), colony.getCenter()) <= SWEEP_FROM) {
                continue;
            }
            wayHome.send(id, null, now, "left outside with nothing to do there");
            say(level, RaidGround.shortId(id) + " is outside at " + ant.blockPosition().toShortString()
                    + " with nothing to do there: it goes home along a trail");
        }
    }

    /** BUGS 89: the raiders a moment after the raid was called: one not walking with it says why (the goals it runs). */
    private void checkRaiders(ServerLevel level, RaidColumn raid) {
        for (UUID id : raid.members()) {
            if (level.getEntity(id) instanceof MatabeleEntity ant && ant.getFightSpot() == null
                    && !ant.runsGoal(MatabeleRaidGoal.class)) {
                say(level, RaidGround.shortId(id) + " was called to the raid but is not walking with it a second later: it runs "
                        + ant.runningGoals());
            }
        }
    }

    /** BUGS 89: an ant the scout command sent that did not set out two seconds later says why (the goals it runs). */
    private void checkSent(ServerLevel level) {
        long now = level.getGameTime();
        sentCheckAt.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            UUID id = entry.getKey();
            if (!scouts.contains(id)) {
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

    /**
     * Step 3c: a raid is home. One that brought termites back keeps its spot fresh (the termites may well be there
     * again); one that came back empty counts as an empty check of the spot, forgotten after {@code spot_misses} such
     * (the next scouts, sent out at once while the colony is hungry, look elsewhere first).
     */
    private void raidHome(ServerLevel level, RaidColumn raid) {
        Raids.Find find = raid.find();
        bringHiddenHome(level, raid);
        if (!finds.contains(find)) {
            return;
        }
        if (raid.carried() > 0 || raid.hiddenCatch() > 0) {
            find.checkedAt = level.getGameTime();
            find.misses = 0;
        } else if (raid.lostTrail() || raid.lostFromSight()) {
            // R4: turned home at a gap in the trail, or (the raid code review, finding 1) lost from sight: the spot was
            // never looked over, so it is no empty check (a scout rechecks it later and lays a new line).
            say(level, "the raid to " + find.at.toShortString() + " never looked the spot over (" + (raid.lostFromSight() ? "lost from sight" : "the trail lost")
                    + "): it still counts as a good spot");
        } else if (raid.foundNothing()) {
            foundEmpty(level, find); // (they were there and found nothing)
        }
    }

    /**
     * M8 step 3d: what the raiders going as numbers took under the mud comes home with the column: onto the prey pile (out
     * of sight: they are numbers), and the termite colony remembers the attack (its danger map: it keeps off the spot a
     * few days and plugs the holes there, brain step 4g), as the real raiders' stings already tell it.
     */
    private void bringHiddenHome(ServerLevel level, RaidColumn raid) {
        int hidden = raid.hiddenCatch();
        if (hidden <= 0) {
            return;
        }
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        if (pile != null) {
            pile.bringIn(level, null, hidden);
        }
        Colony termites = raid.hiddenFrom() == null ? null : ColonyManager.get(level).getColony(raid.hiddenFrom());
        if (termites != null) {
            termites.danger().hurt(level, raid.find().at(), SavannaEntities.MATABELE_MAJOR.get().toShortString(), true);
        }
        say(level, "the raiders going as numbers bring " + hidden + " termite(s) home" + (pile == null ? "" : pile.describe(level)));
    }

    /** The last raid home, or null (tests and the colony list). */
    @Nullable
    public RaidColumn lastRaid() {
        return last;
    }

    /** The raid test bench (the raid-lists rule): how many finds are remembered, and how many ants go home by themselves. */
    public int findsCount() {
        return finds.size();
    }

    public int goingHomeCount() {
        return wayHome.size();
    }

    /** The raid test bench: the ants going home by themselves now. */
    public Set<UUID> goingHomeIds() {
        return Set.copyOf(wayHome.ids());
    }

    /** Whether this ant goes home along a trail by itself (step 3c's limpers and every ant let go out in the field). */
    public boolean isGoingHome(UUID ant) {
        return wayHome.isGoingHome(ant);
    }

    /** Step 3c's name for {@link #isGoingHome}. */
    public boolean isLimping(UUID ant) {
        return isGoingHome(ant);
    }

    /** Why this ant was sent home by itself (the diary and the tests), or null. */
    @Nullable
    public String whyGoingHome(UUID ant) {
        return wayHome.why(ant);
    }

    /**
     * Where an ant going home by itself should walk now: a little ahead of itself along its way home ({@link WayHome});
     * null once it is home (near the entrance or in the nest; it is let go) or not going home.
     */
    @Nullable
    public BlockPos wayHomeOrder(ServerLevel level, AbstractAntEntity ant) {
        return wayHome.orderFor(level, ant, colony, trails, trailNumbers());
    }

    /** BUGS 140: this ant going home by itself found no way to the next place on its trail: straight home from now on. */
    public boolean leaveTrailHome(ServerLevel level, AbstractAntEntity ant) {
        if (!wayHome.onTrail(ant.getUUID())) {
            return false;
        }
        wayHome.leaveTrail(ant.getUUID());
        say(level, RaidGround.shortId(ant.getUUID()) + " leaves its trail home (no way along it) and makes straight for the entrance");
        return true;
    }

    /** Tests: this ant limps home along this column's trail. */
    public void limpForTests(MatabeleEntity ant, RaidColumn raid, long now) {
        wayHome.send(ant.getUUID(), raid.back(), now, "hurt (test)");
    }

    /** A carrier (or any ant) far out with no way to where it was going goes home along a trail by itself. */
    public void sendHome(ServerLevel level, AbstractAntEntity ant, String why) {
        if (!wayHome.isGoingHome(ant.getUUID()) && !WayHome.isHome(level, ant, colony)) {
            wayHome.send(ant.getUUID(), null, level.getGameTime(), why);
            say(level, RaidGround.shortId(ant.getUUID()) + " goes home along a trail by itself (" + why + ")");
        }
    }

    /**
     * The raid code review, finding 4 (the colony question {@code keep_from_defence}): a scout out searching is never
     * called to defend the nest, nor a raider on the column's own fight; raiders milling at the entrance may be (they
     * rejoin the column, or catch it up along the trail).
     */
    static boolean keptFromDefence(Raids raids, Level level, AbstractAntEntity ant) {
        UUID id = ant.getUUID();
        return raids.scouts.contains(id) && !raids.isRaider(id) || raids.column != null && raids.column.fightsForTheColumn(ant);
    }

    /** R5, the choosing numbers (the defaults if the species file has none). */
    public SavannaNumbers.ChooseNumbers choosing() {
        return colony.profile().section(SavannaNumbers.CHOOSING).orElse(SavannaNumbers.ChooseNumbers.DEFAULT);
    }

    /**
     * R5, choosiness (Frank and Linsenmair 2017): the least worth a find here must have to be worth a raid now: more the
     * farther it is from the nest, less the hungrier the colony.
     */
    public double need(BlockPos at) {
        return choosing().need(RaidGround.flat(at, colony.getCenter()), fedShare());
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
        SavannaNumbers.ScoutNumbers numbers = numbers();
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

    /**
     * Whether the colony wants food: its prey pile holds less than a day's food (one termite per ant a day, as the pile
     * is eaten; {@link #fedShare}).
     */
    public boolean isHungry() {
        return fedShare() < 1.0;
    }

    /**
     * How well fed the colony is, 0 (nothing on the pile) to 1 (a day's food or more): the termites in the pile's good
     * layers ({@code termites_per_layer} a layer) against one termite per worker a day. The raid code review, finding
     * 11: once, for the hunger and the raid's size.
     */
    public double fedShare() {
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        return pile == null ? 0.0 : Math.min(1.0, pile.edibleTermites() / (double) Math.max(1, colony.getCount(Caste.WORKER)));
    }

    /**
     * M8 step 3e, the raid day: how many raids the colony wants today, by its hunger: 1 when nearly fed up to
     * {@code raids.away.most_raids_a_day} (4) when the pile is empty (real Megaponera raid 1 to 4 times a day, more when
     * hungry; Yusuf 2026, Frank and Linsenmair 2017; the scale between my pick).
     */
    public int raidsWanted() {
        int most = away.mostRaidsADay();
        return Math.max(1, Math.min(most, (int) Math.ceil(most * (1.0 - fedShare()))));
    }

    /** M8 step 3e: whether the colony may still send a raid today (near or away; commands do not ask). */
    public boolean dayHasRoom(ServerLevel level) {
        return away.raidsToday(level) < raidsWanted();
    }

    /** M8 step 3e, tests: today's raids all made (the count raised to what the colony wants). */
    public void fillDayForTests(ServerLevel level) {
        while (away.raidsToday(level) < raidsWanted()) {
            away.countRaid(level);
        }
    }

    /** M8 step 3e: raids set out today, near or away. */
    public int raidsToday(ServerLevel level) {
        return away.raidsToday(level);
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

    /** The diary: a line about this scout (its walk: the search starting, cut short, ...). */
    public void scoutSays(ServerLevel level, UUID scout, String what) {
        say(level, "scout " + RaidGround.shortId(scout) + " " + what);
    }

    /** Raid rework R1, the raid diary: one {@code [raid]} line in the log for the colony as a whole (the column says its own). */
    private void say(ServerLevel level, String what) {
        Watch.note(level, colony, "[raid] " + what);
        SavannaMod.LOGGER.info("[raid] nest {} at {} (tick {}): {}", colony.getCenter().toShortString(),
                RaidGround.clock(colony, level), level.getGameTime(), what);
    }

    /**
     * How many scouts the colony sends at once: one per {@code workers_per_scout} workers, at least 1, at most
     * {@code most_scouts} (the user's pick); R5, a hungry colony sends more scouts (Frank and Linsenmair 2017): a starving
     * one all of them, a nearly fed one {@code fed_scouts} of them, rounded up (my pick).
     */
    public int scoutsWanted() {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        int most = Math.max(1, Math.min(numbers.mostScouts(), colony.getCount(Caste.WORKER) / Math.max(1, numbers.workersPerScout())));
        double share = choosing().fedScouts() + (1.0 - choosing().fedScouts()) * (1.0 - fedShare());
        return Math.max(1, Math.min(most, (int) Math.ceil(most * share - 1e-9)));
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
        if (!fit(ant) || isRaider(ant.getUUID()) || wayHome.isGoingHome(ant.getUUID())) {
            return false;
        }
        if (sent.contains(ant.getUUID())) {
            return true;
        }
        if (ant.isBusyWithChore()) {
            return false; // (one feeding the queen or the young, or carrying, finishes first: another goes)
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

    /**
     * Whether this ant is fit to scout: a major (the scouts in Longhurst and Howse 1979; my reading), whole, not busy
     * fighting, carrying nothing. A hungry one may scout (the raid code review, finding 3: a hungry colony's ants are
     * hungry, and kept in they could never find it food; my pick).
     */
    private static boolean fit(MatabeleEntity ant) {
        return ant.isAlive() && ant.hasMajorBody() && !ant.isCrippled() && !ant.clamp().isOn() && !ant.isDowned()
                && ant.getFightSpot() == null && !ant.hasFullMandibles();
    }

    /** It sets out (counted as a scout until {@link #back}), and is one of the colony's scouts from now on. */
    public void setOut(MatabeleEntity ant) {
        setOut(ant, null);
    }

    /** It sets out on this trip; a recheck's spot is marked as on its way (no second scout to it meanwhile; finding 12). */
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
        back(ant, null);
    }

    /**
     * It is back, or gave up ({@code untold}: a find it never got home to tell, lost with it). One that gave up far from
     * home goes home along a trail by itself (the raid code review, finding 2).
     */
    public void back(MatabeleEntity ant, @Nullable BlockPos untold) {
        UUID id = ant.getUUID();
        for (Find find : finds) {
            if (id.equals(find.checking)) {
                find.checking = null;
            }
        }
        moundsTaken.values().removeIf(id::equals);
        if (scouts.contains(id) && ant.level() instanceof ServerLevel level) {
            say(level, "scout " + RaidGround.shortId(id) + " is back or gave up, at " + ant.blockPosition().toShortString());
            if (untold != null) {
                say(level, "scout " + RaidGround.shortId(id) + " never got home to tell of its find at " + untold.toShortString()
                        + ": the find is lost");
            }
            if (ant.isAlive() && !isRaider(id) && !WayHome.isHome(level, ant, colony)) {
                sendHome(level, ant, "a scout that gave up on its way home");
            }
        }
        scouts.remove(id);
        sent.remove(id);
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
     * their colony's good spots and recruit to them); otherwise a search in the scout's own favourite direction (a new
     * scout is given one, {@link #newHeading}), walking out a share of its reach (usually near, now and then far:
     * {@code raids.search}) and searching there, never past its reach. Null if no trip.
     */
    @Nullable
    public Trip plan(ServerLevel level, MatabeleEntity ant) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return null;
        }
        long now = level.getGameTime();
        ScoutMemoryView memory = new ScoutMemoryView(ant, numbers, startReach(numbers));
        Find best = finds.stream().filter(find -> now - find.checkedAt >= RECHECK_AFTER && now - find.pickedAt >= RECHECK_AFTER
                        && (find.checking == null || !scouts.contains(find.checking)))
                .max(Comparator.comparingInt(Find::worth)).orElse(null);
        if (best != null) {
            // (no second scout to it while one is on its way; its check time is set when the check really happens,
            // finding 12; found empty, the scout searches round the spot)
            best.pickedAt = now;
            int away = (int) Math.round(RaidGround.flat(best.at, colony.getCenter()));
            return new Trip(best, angleOf(best.at), away, Math.max(memory.reach(), Math.min(numbers.mostRange(), away + search().findMargin())));
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
        SavannaNumbers.ScoutNumbers numbers = numbers();
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
        SavannaNumbers.ScoutNumbers numbers = numbers();
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
        ant.scoutMemory().setLastFind(away);
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
     * R5: a scout found termites but too few for a raid this far out with the colony this hungry, and passes the place
     * over (it searches on). At a remembered spot being rechecked, it counts as an empty check.
     */
    public void passedOver(ServerLevel level, BlockPos at, int worth, @Nullable Find rechecking, UUID scout) {
        int away = (int) Math.round(RaidGround.flat(at, colony.getCenter()));
        say(level, "scout " + RaidGround.shortId(scout) + " passes over termites at " + at.toShortString() + ", " + away
                + " blocks out: worth " + worth + ", a raid that far needs " + String.format(Locale.ROOT, "%.1f", need(at))
                + " with the colony " + Math.round(fedShare() * 100) + " percent fed");
        passedOverCount++;
        if (rechecking != null && finds.contains(rechecking)) {
            foundEmpty(level, rechecking);
        }
    }

    /** R5, the diary: a scout found termites worth a raid this far out with the colony this hungry, and runs home. */
    public void takes(ServerLevel level, BlockPos at, int worth, UUID scout) {
        int away = (int) Math.round(RaidGround.flat(at, colony.getCenter()));
        say(level, "scout " + RaidGround.shortId(scout) + " takes the termites at " + at.toShortString() + ", " + away
                + " blocks out: worth " + worth + ", a raid that far needs " + String.format(Locale.ROOT, "%.1f", need(at))
                + " with the colony " + Math.round(fedShare() * 100) + " percent fed");
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
        return by == null || !scouts.contains(by);
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
     * A scout is home from a find: the spot remembered (a known one refreshed), with its scent line home (nest end
     * first), how much termite it smelt there and who found it.
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
        // R4: the scout's line home is a scent trail in the world now, unless a trail still smelt leads there already (the
        // raid follows that one: a column's trail is stronger than one scout's line).
        if (trails.liveTo(at, trailReach(), trailNumbers(), now) == null) {
            trails.lay(level, colony.getCenter(), at, trail, now, false);
        }
        // Step 3b: home with a find, the scout recruits a raid there: in the raid hours, dry, the colony hungry (or the
        // scout command's scout, whatever the hour), and no raid out already. Found between the raid hours (scouts search
        // all day), the find waits for them (tick), the colony hungry then.
        boolean commanded = scout != null && sent.contains(scout);
        // R7: the scout command keeps the raid hours (a find it brings home after 19:00 waits for the morning, hungry or
        // not) unless sent with "anytime" (its find raids at once, whatever the hour)
        boolean anyHour = scout != null && sentAnyHour.contains(scout);
        Find made = known != null ? known : finds.get(finds.size() - 1);
        boolean dayFull = !commanded && !anyHour && !dayHasRoom(level); // (M8 step 3e: commands do not count the day)
        if (column == null && !colony.rainsNow(level) && (anyHour || isRaidTime(level) && (commanded || isHungry() && !dayFull))) {
            say(level, "a scout (" + (scout == null ? "?" : RaidGround.shortId(scout)) + ") is home from " + at.toShortString()
                    + ", worth " + worth + (anyHour ? ", sent by the command at any hour" : commanded ? ", sent by the command" : "") + ": a raid sets out");
            RaidColumn started = startRaid(level, made, !commanded && !anyHour);
            if (started == null) {
                if (unloadedFind) {
                    say(level, "no raid: " + whyNoRaid + "; the find waits");
                    waiting = made;
                    waitingCommanded = commanded;
                } else {
                    say(level, "nobody fit to raid: the find is only remembered");
                }
            } else {
                if (anyHour && !isRaidTime(level)) {
                    started.keepNoHours();
                }
                if (waiting == made) {
                    waiting = null;
                    waitingCommanded = false;
                }
            }
        } else if (column == null && (commanded || isHungry()) && !isRaidTime(level)) {
            say(level, "a scout is home from " + at.toShortString() + ", worth " + worth + ", outside the raid hours ("
                    + RaidGround.clock(colony, level) + "): it waits for them" + (commanded ? " (sent by the command, which keeps the hours)" : ""));
            waiting = made;
            waitingCommanded = commanded;
        } else if (column == null && isHungry() && dayFull && !colony.rainsNow(level)) {
            say(level, "a scout is home from " + at.toShortString() + ", worth " + worth + ": the colony has made its "
                    + raidsToday(level) + " raid(s) today (it wants " + raidsWanted() + " this hungry); the find waits for tomorrow");
            waiting = made;
            waitingCommanded = false;
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
     * How many ants a raid to this find takes (M8 step 3d): the share of its workers the colony can spare, from
     * {@code least_share} (well fed) to {@code most_share} (starving), by its hunger (real raids take 30 to 75 percent of a
     * colony, Yusuf 2026); all of it for a find worth {@code rich_worth} or more, less for a poorer one; at least
     * {@code least_raid}, at most {@code raids.catch.most_raid} (real raids 150 to 550 ants).
     */
    public int raidSize(Find find) {
        SavannaNumbers.ColumnNumbers numbers = columnNumbers();
        if (numbers == null) {
            return 0;
        }
        int workers = Math.max(1, colony.getCount(Caste.WORKER));
        double fed = fedShare();
        double share = numbers.leastShare() + (numbers.mostShare() - numbers.leastShare()) * (1.0 - fed);
        int spare = Math.max(1, (int) Math.floor(workers * share));
        double rich = Math.min(1.0, find.worth / Math.max(0.1, numbers.richWorth()));
        int mostRaid = RaidCatch.numbers(colony).mostRaid();
        return Math.min(Math.min(spare, mostRaid), Math.max(numbers.leastRaid(), (int) Math.round(spare * rich)));
    }

    /**
     * A raid sets out to this find: the scout leads if it is fit (else the major nearest the entrance), then the real
     * workers nearest the entrance, up to {@code most_real} (majors and minors as they come), and the rest of the raid's
     * size from the numbers inside (they go along as numbers: drawn-only ants, step 4). Null if nobody is fit.
     * {@code ownRaid}: the colony set it out by itself, so it is one of the day's raids (M8 step 3e); a command's raid
     * (the raid command, the scout command's scout) is not counted (BUGS 172).
     */
    @Nullable
    private RaidColumn startRaid(ServerLevel level, Find find, boolean ownRaid) {
        SavannaNumbers.ColumnNumbers numbers = columnNumbers();
        unloadedFind = false;
        if (numbers == null) {
            return null;
        }
        if (away.isOut()) {
            // M8 step 3f: a raid out of sight is not home yet: the find waits for it (one raid at a time)
            unloadedFind = true;
            whyNoRaid = "a raid is still out of sight";
            return null;
        }
        if (!level.isPositionEntityTicking(find.at)) {
            // R7: no raid sets out to a spot in land not loaded (no ant can walk there till drawn-only ants, M8 step 4)
            unloadedFind = true;
            whyNoRaid = "the spot at " + find.at.toShortString() + " is in land not loaded";
            say(level, "no raid to " + find.at.toShortString() + ": " + whyNoRaid);
            return null;
        }
        int size = raidSize(find);
        BlockPos entrance = colony.getCenter();
        // (the raid code review, finding 3: ants not busy with a nest chore first, then the nearest the entrance; hungry
        // ones go too, as real raiders do)
        List<MatabeleEntity> fit = new ArrayList<>(colony.population().realMembers(level).stream()
                .filter(member -> member instanceof MatabeleEntity matabele && raiderFit(matabele) && !wayHome.isGoingHome(member.getUUID())
                        && (!scouts.contains(member.getUUID()) || member.getUUID().equals(find.scout))) // (scouts out searching stay at it)
                .map(member -> (MatabeleEntity) member)
                .sorted(Comparator.comparing(MatabeleEntity::isBusyWithChore)
                        .thenComparingDouble(member -> member.blockPosition().distSqr(entrance))).toList());
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
        if (ownRaid) {
            away.countRaid(level); // (M8 step 3e: one of the day's raids; BUGS 172: commands do not count)
        }
        column.sayStart(level);
        say(level, ownRaid ? "raid " + away.raidsToday(level) + " today (the colony wants " + raidsWanted() + " this hungry)"
                : "set out by a command: not counted among the day's raids (" + away.raidsToday(level) + " today)");
        raidCheckAt = now + 20; // (BUGS 89: a second later, every raider should be walking with it)
        whyNoRaid = null;
        find.scout = leader.getUUID(); // (it leads: the one to lead the next raid there too)
        return column;
    }

    /** Whether this ant may go raiding: a worker, whole, not clamped, not downed or fighting, carrying nothing (finding 3). */
    private static boolean raiderFit(MatabeleEntity ant) {
        return ant.isAlive() && ant.isColonyWorker() && !ant.isCrippled() && !ant.clamp().isOn() && !ant.isDowned()
                && !ant.fightsNoMore() && ant.getFightSpot() == null && !ant.hasFullMandibles()
                && !ant.wounds().isOpen() && !ant.needsRescue(); // (M8 step 5: the hurt rest until the wound closes)
    }

    /** The raid command: a raid sets out now to the best known find, whatever the hour or hunger; null if none could. */
    @Nullable
    public RaidColumn raidNow(ServerLevel level) {
        if (column != null || finds.isEmpty()) {
            return null;
        }
        return keepNoHoursOutsideThem(level, startRaid(level, finds().get(0), false));
    }

    /** R7: a raid a command (or a test) sets out outside the raid hours keeps no hours: dusk does not turn it home. */
    @Nullable
    private RaidColumn keepNoHoursOutsideThem(ServerLevel level, @Nullable RaidColumn raid) {
        if (raid != null && !isRaidTime(level)) {
            raid.keepNoHours();
            say(level, "set out by a command outside the raid hours (" + RaidGround.clock(colony, level) + "): it keeps no hours");
        }
        return raid;
    }

    /** R7: whether it is dusk for the raids now (from {@code raids.hours_and_sound.dusk}, 19:30, till the morning's first raid hour). */
    public boolean isDusk(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return numbers != null && colony.profile().section(SavannaNumbers.HOURS_SOUND).orElse(SavannaNumbers.HoursSoundNumbers.DEFAULT)
                .isDusk(colony.timeOfDay(level), numbers);
    }

    /** R7, tests: whether a find waits for the raid hours. */
    public boolean hasWaitingFind() {
        return waiting != null;
    }

    /**
     * A raid to this find sets out now (tests: a find put in by hand, with its scent line and scout). It stands for the
     * colony's own raid, so it counts among the day's raids (M8 step 3e).
     */
    @Nullable
    public RaidColumn raidForTests(ServerLevel level, BlockPos at, int worth, List<BlockPos> trail, @Nullable UUID scout) {
        Find find = new Find(at.immutable(), worth, level.getGameTime(), level.getGameTime(), 0, List.copyOf(trail), scout);
        finds.add(find);
        trails.lay(level, colony.getCenter(), at, trail, level.getGameTime(), false); // (R4: the scout's line, laid as it ran home)
        return keepNoHoursOutsideThem(level, startRaid(level, find, true));
    }

    /** Tests (BUGS 172): a raid to this remembered find set out as the raid command sets one out, not counted. */
    @Nullable
    public RaidColumn raidByCommandForTests(ServerLevel level, Find find) {
        return column != null ? null : keepNoHoursOutsideThem(level, startRaid(level, find, false));
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
                .filter(member -> member instanceof MatabeleEntity matabele && fit(matabele) && !scouts.contains(member.getUUID())
                        && !isRaider(member.getUUID()) && !wayHome.isGoingHome(member.getUUID()))
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

    /** The colony list's line: scouts out, the good spots known, how far they search. */
    public String describe(ServerLevel level) {
        StringBuilder line = new StringBuilder(", raids: ").append(scouts.size()).append(" scout(s) out, its scouts reach out to ")
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
        if (wayHome.size() > 0) {
            line.append("; ").append(wayHome.size()).append(" raider(s) going home by themselves");
        }
        if (moundsNoticed > 0) {
            line.append("; mound clue: ").append(moundsNoticed).append(" mound(s) ringed, ").append(tunnelsFollowed)
                    .append(" tunnel(s) followed, ").append(clueFinds).append(" find(s)");
        }
        line.append(trails.describe(trailNumbers(), level.getGameTime()));
        line.append(away.describe(level));
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
        // (scouts search like real ants: no map of searched land; each scout keeps its own way and reach, saved on the
        // ant; R5's "raidSearchedRings" and "raidRange" are no longer read: the user's test worlds may drop them)
        if (waiting != null) {
            tag.putLong("raidWaitingAt", waiting.at.asLong()); // (the find's place, not its place in the list: finding 16)
        }
        ListTag old = new ListTag();
        veterans.forEach(id -> old.add(NbtUtils.createUUID(id)));
        tag.put("raidScouts", old);
        if (settled) {
            tag.putBoolean("raidSettled", true);
        }
        trails.save(tag);
        away.save(tag);
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
        headings.clear();
        reaches.clear();
        settled = tag.getBoolean("raidSettled");
        waiting = null;
        if (tag.contains("raidWaitingAt")) {
            BlockPos at = BlockPos.of(tag.getLong("raidWaitingAt"));
            waiting = finds.stream().filter(find -> find.at.equals(at)).findFirst().orElse(null);
        }
        veterans.clear();
        for (Tag t : tag.getList("raidScouts", Tag.TAG_INT_ARRAY)) {
            veterans.add(NbtUtils.loadUUID(t));
        }
        trails.load(tag);
        away.load(tag);
    }

    void fillForTests(ServerLevel level) {
        BlockPos at = colony.getCenter().offset(20, 0, 6);
        finds.add(new Find(at, 14, 100, 140, 1, List.of(colony.getCenter(), colony.getCenter().east(2), at.west(2)),
                new UUID(7, 11)));
        waiting = finds.get(finds.size() - 1);
        veterans.add(new UUID(7, 11));
        veterans.add(new UUID(7, 12));
        trails.fillForTests(colony.getCenter());
        away.fillForTests(level);
    }

    /** Whether a termite there counts as feeding (out of its mound): not within its colony's mound and a little round it. */
    public static boolean outOfMound(BlockPos at, List<MoundAt> mounds) {
        for (MoundAt mound : mounds) {
            if (mound.center.closerThan(at, mound.reach + 2.0)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether termites here are far enough from their mound to be a raid's prey (the user's report, 2026-11-05): not on
     * the mound's own ground, its foot plus {@link #MOUND_DOOR} blocks, where the nest's own holes open and its soldiers
     * stand guard (real Megaponera raid the termites' feeding places, not the mound's doorstep: there the termites are
     * in at the first alarm). A raid sent there by a night's foragers found the holes plugged and nobody out.
     */
    public static boolean awayFromMoundDoor(BlockPos at, List<MoundAt> mounds) {
        for (MoundAt mound : mounds) {
            if (mound.center.closerThan(at, mound.reach + MOUND_DOOR)) {
                return false;
            }
        }
        return true;
    }

    /** Blocks past a living mound's foot still its own ground for {@link #awayFromMoundDoor} (my pick). */
    public static final double MOUND_DOOR = 4.0;

    /** R4: a block dug or put here: every Matabele colony within 128 blocks hears of it (its scent trails may break). */
    public static void blockChangedNear(ServerLevel level, BlockPos pos) {
        for (Colony other : ColonyManager.get(level).getColonies()) {
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
        for (Colony other : ColonyManager.get(level).getColonies()) {
            if (SavannaSpecies.MACROTERMES.equals(other.getSpecies()) && other.getCenter().closerThan(at, within)) {
                near.add(other);
            }
        }
        return near;
    }
}
