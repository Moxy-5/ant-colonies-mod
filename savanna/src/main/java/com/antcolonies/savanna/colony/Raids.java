package com.antcolonies.savanna.colony;

import com.antcolonies.colony.LandReady;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
 * by the colony's size and hunger, and the colony keeps the {@link Raid} while it is out (one at a time).
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
         * BUGS 360: the id of the scent trail laid from {@link #trail} (0 none): a raid to the find follows this trail, the
         * line its scout laid on its latest walk home, never an older trail to the same spot.
         */
        private int trailId;
        /**
         * The raid code review, finding 12: the scout on its way to recheck it now (not saved), and when it was picked
         * for a trip. {@code checkedAt} is set only when a recheck really happens (found again, or found empty).
         */
        @Nullable
        UUID checking;
        long pickedAt = -RECHECK_AFTER;

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

    /** A remembered spot is not rechecked again for this long after a check (ticks: another scout just looked). */
    static final long RECHECK_AFTER = 2000;

    private final Colony colony;
    private final List<Find> finds = new ArrayList<>();
    /** The raid out now, with bodies or as numbers, or null (saved with the raids out of sight, {@link RaidsAway#save}). */
    @Nullable
    private Raid column;
    /** The last raid home (tests and the colony list), or null. */
    @Nullable
    private Raid last;
    /**
     * The find a scout last brought home, until it gets its raid or is only remembered: every second the one rule of
     * when a raid may leave is asked for it ({@link #whyNoRaidNow}).
     */
    @Nullable
    private Find waiting;
    /** Whose raid the waiting find's would be. */
    private By waitingBy = By.COLONY;
    /** The diary: why the waiting find last could not get its raid (said once, not every second), or null. */
    @Nullable
    private String waitingWhy;

    /**
     * Whose raid it is: the colony's own (in the raid hours, hungry, the day's raids not all made), the scout command's
     * scout's (R7: it keeps the raid hours, whatever the hunger or the day's count), or the command's "anytime" scout's
     * (whatever the hour too).
     */
    private enum By { COLONY, COMMAND, COMMAND_ANY_HOUR }
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
    /** BUGS 89: when to see whether the raiders took up their raid (game time, -1 none). */
    private long raidCheckAt = -1;
    /** R4: the colony's scent trails in the world (saved). */
    private final ScentTrails trails = new ScentTrails();
    /** Why the last raid could not set out (the raid command's answer), or null. */
    @Nullable
    private String whyNoRaid;
    /** The last raid could not set out because a raid out of sight is not home yet (the find waits for it). */
    private boolean raidStillOut;
    /** M8 step 3f: the raids while the colony is away, as numbers (saved). */
    private final RaidsAway away;
    /** The scouts' bookkeeping: who is out, who keeps the job, their trips (step 5 of the raid rewrite). */
    private final Scouts scouts;


    public Raids(Colony colony) {
        this.colony = colony;
        this.away = new RaidsAway(this, colony);
        this.scouts = new Scouts(this, colony);
    }

    /** The colony's scouts: who is out, who keeps the job, their trips and ways. */
    public Scouts scouts() {
        return scouts;
    }

    /** M8 step 3f: the raids out of sight. */
    public RaidsAway away() {
        return away;
    }

    /**
     * M8 step 3f: once a second while the colony is away. The finds fade as at home; a raid out with bodies when the
     * colony went away goes on as numbers, the same raid ({@link Raid#goOnAsNumbers}), unless a watcher is near it
     * ({@link #watchedOut}); the raid day and the scouts out
     * of sight ({@link RaidsAway}); and the raid out is stepped as at home.
     */
    void tickAway(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        finds.removeIf(find -> now - find.checkedAt > numbers.spotFadeTicks());
        if (waiting != null && !finds.contains(waiting)) {
            waitNoMore();
        }
        if (column != null && !column.goesAsNumbers() && !watchedOut(level, column, now)) {
            column.goOnAsNumbers(level);
            raidCheckAt = -1;
        }
        scouts.nobodyOut(); // (the real scouts are numbers now: out of sight the colony's scouts search as numbers)
        away.tick(level);
        stepRaid(level);
    }

    /**
     * BUGS 319: whether a watcher is near this raid's real ants (its front or its tail, within the colony's own "near").
     * Such a raid keeps its bodies though its colony is away (its nest's land went out of range behind it): nobody is
     * folded into numbers in front of a player. Once nobody is near, it goes on as numbers at the next look.
     */
    private boolean watchedOut(ServerLevel level, Raid raid, long now) {
        AwayLife watch = colony.away();
        return watch.watcherNear(level, raid.frontPlace(now), AwayLife.NEAR) || watch.watcherNear(level, raid.tailPlace(now), AwayLife.NEAR);
    }

    /** The raid that last came home, or null (tests: a raid out of sight is gone from {@link #column} once done). */
    @Nullable
    public Raid lastRaid() {
        return last;
    }

    /** The raid out now is this one: one that set out as numbers ({@link RaidsAway}), or one read back after a reload. */
    void putRaid(Raid raid) {
        column = raid;
        whyNoRaid = null;
    }

    /** This find is forgotten: the judge found no termite there now. */
    void forget(Find find) {
        finds.remove(find);
        forgetWaiting(find);
    }

    /** The scent trail to this find: the one still smelt, else a new one laid along the find's own line. */
    ScentTrails.Trail trailFor(ServerLevel level, Find find, long now) {
        ScentTrails.Trail trail = trailOf(find, now);
        return trail != null ? trail : layTrail(level, find, now);
    }

    /**
     * BUGS 360: the trail for a raid read back from the save: the one it was walking (by its saved id) if it is still
     * kept, even if its scout has laid a newer line since; else the find's own ({@link #trailFor}).
     */
    ScentTrails.Trail trailForSaved(ServerLevel level, Find find, int savedId, long now) {
        ScentTrails.Trail walking = trails.byId(savedId);
        return walking != null ? walking : trailFor(level, find, now);
    }

    /** BUGS 360: the find's own trail (laid from its scout's newest line) if it is still smelt; null if not. */
    @Nullable
    private ScentTrails.Trail trailOf(Find find, long now) {
        ScentTrails.Trail trail = trails.byId(find.trailId);
        return trail != null && trail.alive(trailNumbers(), now) ? trail : null;
    }

    /**
     * BUGS 360: the find's scent line laid as a trail in the world, the find's trail from now on; the line it replaces is
     * dropped (unless a raid walks it now: it fades by itself after).
     */
    private ScentTrails.Trail layTrail(ServerLevel level, Find find, long now) {
        ScentTrails.Trail old = trails.byId(find.trailId);
        if (old != null && (column == null || column.trail() != old)) {
            trails.drop(old); // (first: at the most trails kept, laying would drop the oldest, maybe another find's)
        }
        ScentTrails.Trail trail = trails.lay(level, colony.getCenter(), find.at, find.trail, now, false);
        find.trailId = trail.id();
        return trail;
    }

    /** A find as a raid read back after a reload needs it: the remembered one at that spot, else one made for it. */
    Find findFor(BlockPos at, int worth, long now) {
        Find known = finds.stream().filter(find -> find.at.equals(at)).findFirst().orElse(null);
        return known != null ? known : new Find(at.immutable(), worth, now, now, 0, RaidsAway.line(colony.getCenter(), at), null);
    }

    /**
     * The brain while away, step 2: where the colony's real ants are out on a raid's business now: the raiders of the
     * column out, the scouts out, the ants walking home by themselves, and (step 3) the place on its trail of a raid out
     * as numbers. A watcher near any of them watches the colony ({@code AwayLife.isHere}), so a raid a player follows
     * away from the nest stays real, and one out as numbers is shown as a player comes near ({@link RaidsAway#tickHere}).
     */
    List<BlockPos> outThere(ServerLevel level) {
        BlockPos numbersRaid = away.whereNow(level); // (step 3: a raid out as numbers, where it is on its trail now)
        List<BlockPos> places = new ArrayList<>(away.scoutPlaces(level)); // (step 4: the scouts out as numbers)
        if (numbersRaid != null) {
            places.add(numbersRaid);
        }
        if ((column == null || column.stage() == Raid.Stage.DONE) && scouts.scoutsOut().isEmpty() && wayHome.size() == 0) {
            return places;
        }
        if (column != null && column.stage() != Raid.Stage.DONE) {
            addPlaces(level, column.members(), places);
        }
        addPlaces(level, scouts.scoutsOut(), places);
        addPlaces(level, wayHome.ids(), places);
        return places;
    }

    private static void addPlaces(ServerLevel level, java.util.Collection<UUID> ants, List<BlockPos> places) {
        for (UUID id : ants) {
            if (level.getEntity(id) instanceof MatabeleEntity ant && ant.isAlive()) {
                places.add(ant.blockPosition());
            }
        }
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

    /** A raid set out to this find: it no longer waits for the raid hours. */
    void forgetWaiting(Find find) {
        if (waiting == find) {
            waitNoMore();
        }
    }

    private void waitNoMore() {
        waiting = null;
        waitingBy = By.COLONY;
        waitingWhy = null;
    }

    /**
     * M8 step 3f: a number scout out of sight found termites feeding here: the spot remembered, its line home laid as a
     * scent trail only while the nest's land is loaded (no block of unloaded land is read).
     */
    void remember(ServerLevel level, BlockPos at, int worth, List<BlockPos> line) {
        long now = level.getGameTime();
        Find find = new Find(at.immutable(), worth, now, now, 0, List.copyOf(line), null);
        finds.add(find);
        if (LandReady.at(level, colony.getCenter()) && LandReady.at(level, at)) {
            layTrail(level, find, now);
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
            away.tickHere(level); // (a raid out as numbers goes on, and is shown as real ants once a watcher is near it)
            tickTimed(level);
        } finally {
            RaidCost.end();
        }
    }

    private void tickTimed(ServerLevel level) {
        trails.tick(level, level.getGameTime(), trailNumbers(), column == null ? null : column.trail(), what -> say(level, what));
        stepRaid(level);
        scouts.checkSent(level);
        // (once a second: the colony's parts tick once a second; the raid code review, finding 8)
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        // (the raid code review, finding 1: one only out of sight is kept a grace first; only the dead or long unseen go)
        scouts.upkeep(level, now);
        finds.removeIf(find -> now - find.checkedAt > numbers.spotFadeTicks());
        raidTheWaitingFind(level, null);
        SavannaNumbers.CarryNumbers carry = carryNumbers();
        int limpTicks = carry == null ? SavannaNumbers.CarryNumbers.DEFAULT.limpTicks() : carry.limpTicks();
        wayHome.forgetIf(id -> now - wayHome.since(id) > limpTicks || goneFor(level, id, now));
        if (now >= nextSweepAt) {
            nextSweepAt = now + SWEEP_EVERY; // (a clock of its own, no game-time remainder: the raid code review, finding 8)
            sweep(level, now);
        }
        unseenSince.keySet().removeIf(id -> !scouts.tracks(id) && !wayHome.isGoingHome(id));
    }

    /**
     * The raid out is worked out once more (once a second, the colony here or away; the raiders ask it themselves every
     * few ticks): dusk and rain turn it home, the raiders it let go are sent home along the trail, and a raid that is
     * done is handed in ({@link #raidHome}).
     */
    private void stepRaid(ServerLevel level) {
        if (column != null) {
            if (column.stage() != Raid.Stage.DONE && !column.keepsNoHours() && !column.turnedAtDusk()) {
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
            for (Raid.LetGo one : column.takeLetGo()) {
                wayHome.send(one.ant(), column.back(), level.getGameTime(), one.why());
                say(level, RaidGround.shortId(one.ant()) + " goes home along the trail by itself (" + one.why() + ")");
            }
            if (raidCheckAt >= 0 && level.getGameTime() >= raidCheckAt) {
                raidCheckAt = -1;
                checkRaiders(level, column);
            }
            if (column.stage() == Raid.Stage.DONE) {
                raidHome(level, column);
                last = column;
                column = null;
            }
        }
    }

    /**
     * The raid code review, finding 1: whether this ant is gone for the raids: dead, or out of sight (not in the world,
     * or in land not ticking now) longer than the colony's away grace ({@code AwayLife}, a minute). The grace is counted
     * from when the colony first missed it while its own land ticked, so a colony coming back into sight drops nobody
     * at once.
     */
    boolean goneFor(ServerLevel level, UUID id, long now) {
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
            if (scouts.isScouting(id) || isRaider(id) || wayHome.isGoingHome(id) || ant.hasFullMandibles() || ant.mayGoOut()
                    || WayHome.isHome(level, ant, colony) || RaidGround.flat(ant.blockPosition(), colony.getCenter()) <= SWEEP_FROM) {
                continue;
            }
            wayHome.send(id, null, now, "left outside with nothing to do there");
            say(level, RaidGround.shortId(id) + " is outside at " + ant.blockPosition().toShortString()
                    + " with nothing to do there: it goes home along a trail");
        }
    }

    /** BUGS 89: the raiders a moment after the raid was called: one not walking with it says why (the goals it runs). */
    private void checkRaiders(ServerLevel level, Raid raid) {
        for (UUID id : raid.members()) {
            if (level.getEntity(id) instanceof MatabeleEntity ant && ant.getFightSpot() == null
                    && !ant.runsGoal(MatabeleRaidGoal.class)) {
                say(level, RaidGround.shortId(id) + " was called to the raid but is not walking with it a second later: it runs "
                        + ant.runningGoals());
            }
        }
    }

    /**
     * Step 3c: a raid is home. One that brought termites back keeps its spot fresh (the termites may well be there
     * again); one that came back empty counts as an empty check of the spot, forgotten after {@code spot_misses} such
     * (the next scouts, sent out at once while the colony is hungry, look elsewhere first).
     */
    private void raidHome(ServerLevel level, Raid raid) {
        Raids.Find find = raid.find();
        bringHiddenHome(level, raid);
        if (!finds.contains(find)) {
            return;
        }
        if (raid.carried() > 0 || raid.hiddenCatch() > 0) {
            find.checkedAt = level.getGameTime();
            find.misses = 0;
        } else if (raid.spotEmpty()) {
            // The raid rewrite: the column halted at the spot, looked round and nobody was there: forgotten at once.
            finds.remove(find);
            say(level, "the raid to " + find.at.toShortString() + " found no termite there: the find is forgotten");
        } else if (raid.turned() != null || raid.lostTrail()) {
            // Turned home on the way (dusk, rain, the scout lost, a gap in the trail, land not loaded): the spot was
            // never looked over, so it is no empty check (a scout rechecks it later).
            say(level, "the raid to " + find.at.toShortString() + " never looked the spot over ("
                    + (raid.turned() != null ? raid.turned() : "the trail lost") + "): it still counts as a good spot");
        } else if (raid.foundNothing()) {
            foundEmpty(level, find); // (they fought there and brought nothing back)
        }
    }

    /**
     * M8 step 3d: what the raiders going as numbers took under the mud comes home with the column: onto the prey pile (out
     * of sight: they are numbers), and the termite colony remembers the attack (its danger map: it keeps off the spot a
     * few days and plugs the holes there, brain step 4g), as the real raiders' stings already tell it.
     */
    private void bringHiddenHome(ServerLevel level, Raid raid) {
        int hidden = raid.hiddenCatch();
        if (hidden <= 0) {
            return;
        }
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        if (pile != null && colony.away().isHere(level)) {
            pile.bringIn(level, null, hidden);
        } else if (pile != null) {
            pile.bringInCounted(level.getGameTime(), hidden); // (out of sight: counted, no block touched; its layers are laid later)
        }
        colony.away().note(level, com.antcolonies.colony.AwayReport.What.PREY_BROUGHT, hidden);
        Colony termites = raid.hiddenFrom() == null ? null : ColonyManager.get(level).getColony(raid.hiddenFrom());
        if (termites != null) {
            termites.danger().hurt(level, raid.find().at(), SavannaEntities.MATABELE_MAJOR.get().toShortString(), true);
        }
        say(level, "the raiders going as numbers bring " + hidden + " termite(s) home" + (pile == null ? "" : pile.describe(level)));
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
        return raids.scouts.isScouting(id) && !raids.isRaider(id) || raids.column != null && raids.column.fightsForTheColumn(ant);
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

    /**
     * What a scout's look at a place is worth: 3 a termite, of the termites it counted feeding there or, if that is
     * more, of the termites out of sight in the feeding places it smelt there ({@code by_day_termites} a place).
     */
    public int worthOf(int termitesSeen, int feedingPlaces) {
        return 3 * Math.max(termitesSeen, feedingPlaces * choosing().byDayTermites());
    }

    /**
     * <b>The one judge of "worth a raid"</b> (the raid rewrite, step 5): a find of this worth here is worth a raid now
     * if it reaches what the colony needs ({@link #need}: more the farther out, less the hungrier). Asked by a scout
     * reporting its look ({@link #looked}), by the scouts out of sight, and before a raid leaves out of sight
     * ({@link RaidsAway}); nobody else compares a worth with a need.
     */
    public boolean worthARaid(BlockPos at, int worth) {
        return worth > 0 && worth >= need(at);
    }

    /** Whether the colony remembers a find worth a raid now. */
    boolean knowsAFindWorthARaid() {
        return finds.stream().anyMatch(find -> worthARaid(find.at, find.worth));
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
        return pile == null ? 0.0 : fedShare(pile.antsFed(), colony.getCount(Caste.WORKER));
    }

    /**
     * How well fed (0 to 1) a colony of this many workers is with a pile whose good layers feed this many ants for a day
     * ({@code PreyPile.antsFed}; test revamp R5).
     */
    public static double fedShare(int antsFed, int workers) {
        return Math.min(1.0, antsFed / (double) Math.max(1, workers));
    }

    /**
     * M8 step 3e, the raid day: how many raids the colony wants today, by its hunger: 1 when nearly fed up to
     * {@code raids.away.most_raids_a_day} (4) when the pile is empty (real Megaponera raid 1 to 4 times a day, more when
     * hungry; Yusuf 2026, Frank and Linsenmair 2017; the scale between my pick).
     */
    public int raidsWanted() {
        return raidsWanted(away.mostRaidsADay(), fedShare());
    }

    /** How many raids a day a colony this well fed (0 to 1) wants, at most {@code most} (test revamp R5: the rule as a plain method). */
    public static int raidsWanted(int most, double fed) {
        return Math.max(1, Math.min(most, (int) Math.ceil(most * (1.0 - fed))));
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

    /** Tests: every remembered find forgotten (a scene that wants the colony's scouts out, not a raid). */
    public void forgetFindsForTests() {
        finds.clear();
        waitNoMore();
    }

    /** M8 step 3e: raids set out today, near or away. */
    public int raidsToday(ServerLevel level) {
        return away.raidsToday(level);
    }

    /** Whether it is the raid hours at the nest (mornings and evenings, Yusuf 2026) and dry: the hours a raid may leave. */
    public boolean isRaidTime(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return numbers != null && numbers.isRaidHour(colony.timeOfDay(level)) && !colony.rainsNow(level);
    }

    /** The find waiting for its raid (the last a scout brought home); null if none. */
    @Nullable
    public Find waiting() {
        return waiting;
    }

    /** Raid rework R1, the raid diary: one {@code [raid]} line in the log for the colony as a whole (the column says its own). */
    private void say(ServerLevel level, String what) {
        Watch.note(level, colony, "[raid] " + what);
        SavannaMod.LOGGER.info("[raid] nest {} at {} (tick {}): {}", colony.getCenter().toShortString(),
                RaidGround.clock(colony, level), level.getGameTime(), what);
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
        Find find = known != null ? known : finds.get(finds.size() - 1);
        // R4, made newer by BUGS 360: the scout's line home is a scent trail in the world now, and the find's trail: the
        // next raid there follows the line its scout laid on this walk home (as real Megaponera do, the scout leading the
        // column along its fresh trail, Longhurst and Howse 1979), not an older trail the ground may have shut since.
        layTrail(level, find, now);
        // Step 3b: home with a find, the scout recruits a raid there as soon as one may leave (the one rule, asked now
        // and then every second while the find waits).
        waiting = find;
        // R7: the scout command keeps the raid hours (a find it brings home after 19:00 waits for the morning, hungry or
        // not) unless sent with "anytime" (its find raids at once, whatever the hour)
        waitingBy = scout != null && scouts.wasSentAnyHour(scout) ? By.COMMAND_ANY_HOUR
                : scout != null && scouts.wasSent(scout) ? By.COMMAND : By.COLONY;
        waitingWhy = null;
        say(level, "a scout (" + (scout == null ? "?" : RaidGround.shortId(scout)) + ") is home from " + at.toShortString() + ", worth " + worth
                + (waitingBy == By.COMMAND_ANY_HOUR ? ", sent by the command at any hour" : waitingBy == By.COMMAND ? ", sent by the command" : ""));
        raidTheWaitingFind(level, scout);
    }

    /** Not hungry: the one reason that ends a find's wait (the spot stays remembered for the next hungry spell). */
    private static final String FED = "the colony is not hungry";

    /**
     * The one rule of when a raid may leave now, null if it may, else why not (the raid rewrite, part c: a scout coming
     * home and a waiting find were judged in two places, by rules that had drifted apart: a find brought home in the
     * rain waited outside the raid hours and was only remembered inside them). One raid at a time, never in the rain
     * (it washes the scent trail out; real raids are not seen in rain, Yusuf 2026), in the raid hours; the colony's own
     * raid only while it is hungry and has not made its day's raids (M8 step 3e; commands do not count the day, BUGS 172).
     */
    @Nullable
    private String whyNoRaidNow(ServerLevel level, By by) {
        if (column != null) {
            return "one is out already";
        }
        if (colony.rainsNow(level)) {
            return "it rains";
        }
        if (by != By.COMMAND_ANY_HOUR && !isRaidTime(level)) {
            return "outside the raid hours"; // (review R11-14: no clock in it: a new text every second, so a diary line every second)
        }
        if (by != By.COLONY) {
            return null;
        }
        if (!isHungry()) {
            return FED;
        }
        return dayHasRoom(level) ? null
                : "the colony has made its " + raidsToday(level) + " raid(s) today (it wants " + raidsWanted() + " this hungry)";
    }

    /** {@link #whyNoRaidNow} for the colony's own raid, asked for the raids out of sight too ({@link RaidsAway}). */
    @Nullable
    String whyNoOwnRaidNow(ServerLevel level) {
        return whyNoRaidNow(level, By.COLONY);
    }

    /**
     * The waiting find gets its raid if one may leave now ({@link #whyNoRaidNow}); it waits on while the reason passes
     * by itself (a raid out, rain, the hours, the day's count) and is only remembered once the colony is fed or no
     * raid could be made up (nobody fit, no termite there now, nobody who knows the way, its land not loaded: a later
     * scout finds it again). {@code homeFrom}: the scout just home with it, or null.
     */
    private void raidTheWaitingFind(ServerLevel level, @Nullable UUID homeFrom) {
        if (waiting == null) {
            return;
        }
        Find find = waiting;
        if (!finds.contains(find)) {
            waitNoMore(); // (forgotten meanwhile)
            return;
        }
        String why = whyNoRaidNow(level, waitingBy);
        if (why == null) {
            By by = waitingBy;
            Raid started = startRaid(level, find, by == By.COLONY, homeFrom);
            if (started != null) {
                if (by == By.COMMAND_ANY_HOUR && !isRaidTime(level)) {
                    started.keepNoHours();
                }
                waitNoMore();
                return;
            }
            if (!raidStillOut) {
                waitNoMore(); // (the raid's start said why: the find is only remembered, or forgotten)
                return;
            }
            why = whyNoRaid; // (a raid still out of sight: it waits)
        } else if (why == FED) {
            say(level, "no raid to " + find.at.toShortString() + ": " + why + "; the find is only remembered");
            waitNoMore();
            return;
        }
        if (!why.equals(waitingWhy)) {
            waitingWhy = why;
            say(level, "the find at " + find.at.toShortString() + " waits for its raid: " + why);
        }
    }

    /** R4: a trail leads to a find if it ends this near it (blocks: two smelling distances). */
    private double trailReach() {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return numbers == null ? 10.0 : numbers.smell() * 2.0;
    }

    /** The raid out now, or null. */
    @Nullable
    public Raid column() {
        return column;
    }

    /** Whether this ant walks in the raid out now. */
    public boolean isRaider(UUID ant) {
        return column != null && column.has(ant) && column.stage() != Raid.Stage.DONE;
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
        return raidSize(colony.getCount(Caste.WORKER), fedShare(), find.worth, numbers, RaidCatch.numbers(colony).mostRaid());
    }

    /**
     * {@link #raidSize(Find)}'s rule as a plain method (test revamp R5): the ants a colony of this many workers, this well
     * fed (0 to 1), sends to a find of this worth.
     */
    public static int raidSize(int colonyWorkers, double fed, double worth, SavannaNumbers.ColumnNumbers numbers, int mostRaid) {
        int workers = Math.max(1, colonyWorkers);
        double share = numbers.leastShare() + (numbers.mostShare() - numbers.leastShare()) * (1.0 - fed);
        int spare = Math.max(1, (int) Math.floor(workers * share));
        double rich = Math.min(1.0, worth / Math.max(0.1, numbers.richWorth()));
        return Math.min(Math.min(spare, mostRaid), Math.max(numbers.leastRaid(), (int) Math.round(spare * rich)));
    }

    /**
     * A raid sets out to this find: the scout leads if it is fit (else the major nearest the entrance), then the real
     * workers nearest the entrance, up to {@code most_real} (majors and minors as they come), and the rest of the raid's
     * size from the numbers inside (they go along as numbers: drawn-only ants, step 4). Null if nobody is fit.
     * {@code ownRaid}: the colony set it out by itself, so it is one of the day's raids (M8 step 3e); a command's raid
     * (the raid command, the scout command's scout) is not counted (BUGS 172). {@code homeFrom}: the scout just home with
     * this find (still counted among the scouts out until its trip ends), or null: a find's scout out on a new trip stays
     * at it and another leads (BUGS 193).
     */
    @Nullable
    private Raid startRaid(ServerLevel level, Find find, boolean ownRaid, @Nullable UUID homeFrom) {
        SavannaNumbers.ColumnNumbers numbers = columnNumbers();
        raidStillOut = false;
        if (numbers == null) {
            return null;
        }
        if (away.isOut()) {
            // M8 step 3f: a raid out of sight is not home yet: the find waits for it (one raid at a time)
            raidStillOut = true;
            whyNoRaid = "a raid is still out of sight";
            return null;
        }
        if (!level.isPositionEntityTicking(find.at)) {
            // R7: no raid sets out to a spot in land not loaded (no ant can walk there). Review R11-13: and the find
            // does not wait for its land (while a find waits no scout is sent: a hungry colony did nothing for up to
            // three days); it is only remembered, a later scout finds it again
            whyNoRaid = "the spot at " + find.at.toShortString() + " is in land not loaded";
            say(level, "no raid to " + find.at.toShortString() + ": " + whyNoRaid + "; the find is only remembered");
            return null; // (a command answers with it)
        }
        // The raid rewrite: the one judge of "termites there now" is asked before anyone is called. Nobody there (the
        // termites fed and went, or keep off the spot): no raid, and the find is forgotten.
        Raid.Termites counted = Raid.termitesThere(level, colony, find.at);
        if (!counted.any()) {
            whyNoRaid = "no termite there now (the find is forgotten)";
            say(level, "no raid to " + find.at.toShortString() + ": " + whyNoRaid);
            finds.remove(find);
            forgetWaiting(find);
            return null;
        }
        int size = raidSize(find);
        BlockPos entrance = colony.getCenter();
        // (the raid code review, finding 3: ants not busy with a nest chore first, then the nearest the entrance; hungry
        // ones go too, as real raiders do)
        List<MatabeleEntity> fit = new ArrayList<>(colony.population().realMembers(level).stream()
                .filter(member -> member instanceof MatabeleEntity matabele && raiderFit(matabele) && !wayHome.isGoingHome(member.getUUID())
                        && (!scouts.isScouting(member.getUUID()) || member.getUUID().equals(homeFrom))) // (scouts out searching stay at it)
                .map(member -> (MatabeleEntity) member)
                .sorted(Comparator.comparing(MatabeleEntity::isBusyWithChore)
                        .thenComparingDouble(member -> member.blockPosition().distSqr(entrance))).toList());
        MatabeleEntity scoutHere = fit.stream().filter(ant -> ant.getUUID().equals(find.scout)).findFirst().orElse(null);
        MatabeleEntity leader = scoutHere != null ? scoutHere : fit.stream().filter(MatabeleEntity::hasMajorBody).findFirst().orElse(null);
        if (leader == null) {
            whyNoRaid = "nobody fit to raid (real workers only; not hurt, clamped or fighting)";
            say(level, "no raid to " + find.at.toShortString() + ": " + whyNoRaid);
            return null;
        }
        // R4: the raid follows the find's scent trail if it is still smelt (whoever leads); with the trail gone only the
        // scout knows the way, and it lays a new one as it leads; with the scout gone too, nobody knows the way.
        long now = level.getGameTime();
        ScentTrails.Trail trail = trailOf(find, now);
        if (trail == null) {
            if (scoutHere == null) {
                whyNoRaid = "the scent trail to " + find.at.toShortString() + " has faded and its scout is not here to lead: nobody knows the way";
                say(level, "no raid to " + find.at.toShortString() + ": " + whyNoRaid);
                return null;
            }
            trail = layTrail(level, find, now);
            say(level, "the trail to " + find.at.toShortString() + " has faded: its scout leads from memory, laying a new one");
        }
        fit.remove(leader);
        fit.add(0, leader);
        int real = Math.min(Math.min(size, numbers.mostReal()), fit.size());
        int along = Math.max(0, Math.min(size - real, colony.population().inside(Caste.WORKER)));
        // R3, the marching order (Frank 2017 via AntWiki): the scout in front, the colony's other scouts right behind it,
        // then the rest, the majors at the back as the rear guard.
        List<MatabeleEntity> going = new ArrayList<>(fit.subList(0, real));
        going.sort(Comparator.comparingInt(ant -> ant == leader ? 0 : scouts.isVeteran(ant.getUUID()) ? 1 : ant.hasMajorBody() ? 3 : 2));
        column = new Raid(level, colony, numbers, carryNumbers(), trailNumbers(), find, trail, going, along, counted, now);
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
    public Raid raidNow(ServerLevel level) {
        if (column != null || finds.isEmpty()) {
            return null;
        }
        return keepNoHoursOutsideThem(level, startRaid(level, finds().get(0), false, null));
    }

    /** R7: a raid a command (or a test) sets out outside the raid hours keeps no hours: dusk does not turn it home. */
    @Nullable
    private Raid keepNoHoursOutsideThem(ServerLevel level, @Nullable Raid raid) {
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

    /**
     * A raid to this find sets out now (tests: a find put in by hand, with its scent line and scout). It stands for the
     * colony's own raid, so it counts among the day's raids (M8 step 3e).
     */
    @Nullable
    public Raid raidForTests(ServerLevel level, BlockPos at, int worth, List<BlockPos> trail, @Nullable UUID scout) {
        Find find = new Find(at.immutable(), worth, level.getGameTime(), level.getGameTime(), 0, List.copyOf(trail), scout);
        finds.add(find);
        layTrail(level, find, level.getGameTime()); // (R4: the scout's line, laid as it ran home)
        return keepNoHoursOutsideThem(level, startRaid(level, find, true, scout));
    }

    /** Tests (BUGS 172): a raid to this remembered find set out as the raid command sets one out, not counted. */
    @Nullable
    public Raid raidByCommandForTests(ServerLevel level, Find find) {
        return column != null ? null : keepNoHoursOutsideThem(level, startRaid(level, find, false, null));
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
     * The scout raid command (the user, 2026-11-13, to speed up testing): the place is given to the colony as a spot to
     * recheck on this trip (the other spots wait their turn; its worth is the scout's to judge), and a scout sets out to it now; its
     * find raids at once, whatever the hour. The scout still looks the place over itself and leads the raid as always.
     */
    @Nullable
    public MatabeleEntity sendTo(ServerLevel level, BlockPos at) {
        long now = level.getGameTime();
        finds.removeIf(find -> find.at.closerThan(at, 4.0));
        finds.forEach(find -> find.pickedAt = now); // (the other spots wait their turn: this trip is the tip's)
        Find tip = new Find(at.immutable(), 1, now, now - RECHECK_AFTER, 0, RaidsAway.line(colony.getCenter(), at), null);
        finds.add(tip);
        MatabeleEntity ant = scouts.sendNow(level, true);
        if (ant == null) {
            finds.remove(tip);
        } else {
            say(level, "the scout raid command points " + RaidGround.shortId(ant.getUUID()) + " to the termites at " + at.toShortString());
        }
        return ant;
    }

    /** The colony list's line: scouts out, the good spots known, how far they search. */
    public String describe(ServerLevel level) {
        StringBuilder line = new StringBuilder(", raids: ").append(scouts.scoutsOut().size()).append(" scout(s) out, its scouts reach out to ")
                .append(scouts.range()).append(" blocks, ").append(finds.size()).append(" good spot(s)");
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
        line.append(scouts.describeClue());
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
            one.putInt("trailId", find.trailId);
            list.add(one);
        }
        tag.put("raidFinds", list);
        // (scouts search like real ants: no map of searched land; each scout keeps its own way and reach, saved on the
        // ant; R5's "raidSearchedRings" and "raidRange" are no longer read: the user's test worlds may drop them)
        if (waiting != null) {
            tag.putLong("raidWaitingAt", waiting.at.asLong()); // (the find's place, not its place in the list: finding 16)
        }
        scouts.save(tag);
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
            Find find = new Find(at, one.getInt("worth"), one.getLong("foundAt"), one.getLong("checkedAt"), one.getInt("misses"), trail,
                    one.hasUUID("scout") ? one.getUUID("scout") : null);
            find.trailId = one.getInt("trailId"); // (0 in saves from before BUGS 360: the find's line is laid anew when a raid needs it)
            finds.add(find);
        }
        scouts.load(tag);
        waiting = null;
        if (tag.contains("raidWaitingAt")) {
            BlockPos at = BlockPos.of(tag.getLong("raidWaitingAt"));
            waiting = finds.stream().filter(find -> find.at.equals(at)).findFirst().orElse(null);
        }
        trails.load(tag);
        away.load(tag);
    }

    /**
     * Tests: the raids written down and read back as a reload does it. The raid out is dropped from memory and made
     * again from what was written, at the colony's next tick ({@link RaidsAway#restore}).
     */
    public void reloadForTests() {
        CompoundTag tag = new CompoundTag();
        save(tag);
        column = null;
        load(tag);
    }

    void fillForTests(ServerLevel level) {
        BlockPos at = colony.getCenter().offset(20, 0, 6);
        finds.add(new Find(at, 14, 100, 140, 1, List.of(colony.getCenter(), colony.getCenter().east(2), at.west(2)),
                new UUID(7, 11)));
        waiting = finds.get(finds.size() - 1);
        scouts.fillForTests();
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

