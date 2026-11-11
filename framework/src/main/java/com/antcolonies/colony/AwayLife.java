package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The brain, part 2, step 2a: the colony goes away and comes back. The user's decision (2026-10-14): colonies live while
 * nobody is near, as numbers, and the real insects frozen in unloaded land are counted back into the numbers.
 *
 * <p><b>Going away.</b> When a chunk with one of the colony's insects in it goes out of range, the insect is frozen there
 * by Minecraft; the colony writes down what it was ({@link #freeze}: its age, what it held; an egg's time to hatch, a
 * larva's meals and hunger). Once the nest's middle has been out of range for a minute ({@link #GRACE_TICKS}), the colony
 * is <i>away</i>: everything frozen is folded into the numbers (workers and soldiers into {@link Population}, eggs and
 * larvae into {@link Brood}'s numbers, what they held into what is owed to the nest). The brain then runs the colony as
 * numbers once a second ({@link #tickAway}), and never reads or changes a block of unloaded land.
 *
 * <p><b>Coming back.</b> One source of truth: an insect that was folded in is a number now, so when its chunk loads
 * again it quietly leaves the world on its first tick ({@link #backFromFrozen}; no death, no body) and a fresh one
 * comes out of the nest instead. One frozen for less than a minute just carries on. One frozen longer while the colony
 * was here (a forager left at the edge of the loaded land) has walked home meanwhile: it is folded in then. Once the
 * nest is loaded again, what is owed is put in its place, out of every player's sight ({@link #settle}): the young into
 * the nursery, and through the {@link NestLedger} the bodies into the refuse chamber, carried seeds into the store and
 * bread to the nurses' food (what was carried is counted as soon as it is folded in).
 *
 * <p>Step 2b: while away the colony lives on as numbers ({@link NestLife}, {@link Meals}): the numbers eat and starve,
 * the nurses mill bread and feed the queen and the young, the queen lays, the young grow up, the fungus grows, and the
 * numbers grow old. The clocks of the frozen young go on while they wait to be folded in. Step 2d: the builder numbers'
 * time is counted while away and spent on return, before anything else is put back ({@link BuildLife}).
 *
 * <p>Species-neutral: any insect with a colony and a nest caste, and every egg and larva (or nymph).
 */
public class AwayLife {
    /**
     * How long (ticks) the nest's middle or an insect must have been frozen before it counts as away (a minute; my pick).
     * Public: a species' group job out in the field (a raid column) gives a member out of sight the same grace.
     */
    public static final long GRACE_TICKS = 1200;
    /**
     * Far colonies (the user's pick, 2026-11-04, for speed): a colony whose nest is loaded but has no player within this
     * many blocks (sideways) counts as away, and after the grace minute its real insects in loaded land are folded into
     * the numbers ({@link #foldLoaded}). Beyond 64 blocks a 2-block ant cannot be made out; about 2 to 4 colonies are
     * real at once instead of every loaded one (about 16, 800 real insects).
     */
    public static final int NEAR = 64;
    /** A colony that is here stays here until no player is within this many blocks (so the edge does not flicker). */
    public static final int NEAR_KEEP = 80;
    /** While far and loaded, how often the loaded land is looked over again for real members to fold in (ticks). */
    private static final long FAR_LOOK_TICKS = 400;

    private final Colony colony;
    /** The game time the nest's middle was first seen out of range, or {@link Colony#NOT_SET} while it is here. Saved. */
    private long awaySince = Colony.NOT_SET;
    /** What happened while away, this time or the last time (shown in the colony list). Saved. */
    private final AwayReport report = new AwayReport();
    /** How long the colony was away the last time (ticks; 0 while away or never). Saved. */
    private long lastAwayFor;
    /** When the colony last came back (game time; not saved: a loaded game starts it over), for the watch rules. */
    private long backAt = Colony.NOT_SET;
    /** The colony's insects frozen in unloaded land: id -> what it was. Saved. */
    private final Map<UUID, Frozen> frozen = new LinkedHashMap<>();
    /** Tests only: whether this runs at all (null: always, except on the game test server). Not saved. */
    @Nullable
    private Boolean onForTests;
    /** Tests only: pretend the nest's middle is out of range (null: the real chunks). Not saved. */
    @Nullable
    private Boolean awayForTests;
    /** Tests only: a shorter grace (null: {@link #GRACE_TICKS}). Not saved. */
    @Nullable
    private Long graceForTests;
    /** Tests only: whether the far rule applies (null: in a game only). Not saved. */
    @Nullable
    private Boolean farForTests;
    /** Tests only: pretend a player is near (true) or not (false); null: the real players. Not saved. */
    @Nullable
    private Boolean nearForTests;
    /** When the loaded land was last looked over for real members while far ({@link #foldLoaded}). Not saved. */
    private long lastFarLook = Colony.NOT_SET;

    /** What a frozen insect is. */
    enum Kind {
        ADULT, EGG, LARVA
    }

    /** What a frozen worker held in its mandibles (a soil pellet is just lost, as when it dies). */
    enum Load {
        NONE, SEED, RICH_SEED, NURSE_FOOD, RICH_NURSE_FOOD, EGG, BODY
    }

    /**
     * One frozen insect. Adult: its caste and when it grew up, what it held and when it last ate ({@link Colony#NOT_SET}:
     * it does not eat). Egg: ticks left to hatch ({@code -1}: not put in its place yet). Larva: its meals so far, how long
     * it had gone hungry, and the soldier it grows into. All as when it was frozen.
     */
    record Frozen(Kind kind, String caste, long grownUpAt, Load load, long left, int fed, @Nullable String growsInto,
            long frozenAt, boolean folded, long lastMeal) {
        Frozen fold() {
            return new Frozen(kind, caste, grownUpAt, load, left, fed, growsInto, frozenAt, true, lastMeal);
        }
    }

    /**
     * The soak runs (the raid test bench, round 2, B4): colonies the soak watches live as if a player stood by them,
     * though none is on the server (their land is kept loaded by the soak; unloaded, they still go away).
     */
    private static volatile java.util.function.Predicate<Colony> keptHere = colony -> false;

    /** The soak runs: these colonies are never away by the far rule ({@link #keptHere}). */
    public static void setKeptHere(java.util.function.Predicate<Colony> which) {
        keptHere = which == null ? colony -> false : which;
    }

    AwayLife(Colony colony) {
        this.colony = colony;
    }

    /** Whether the colony goes away and comes back (always in a game; the game tests turn it on where they test it). */
    public boolean isOn(Level level) {
        if (onForTests != null) {
            return onForTests;
        }
        return !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer);
    }

    private long grace() {
        return graceForTests != null ? graceForTests : GRACE_TICKS;
    }

    /** Whether the nest's middle is in range (loaded) now. */
    public boolean isHere(ServerLevel level) {
        if (awayForTests != null) {
            return !awayForTests;
        }
        if (!LandReady.at(level, colony.getCenter())) {
            return false;
        }
        // Far colonies (the user, 2026-11-04): loaded but with no player near, a colony lives as numbers like one in
        // unloaded land. Here once a player comes within NEAR blocks; it stays here until none is within NEAR_KEEP, so
        // walking along the edge does not flip it to and fro.
        return !farCounts(level) || playerNear(level, awaySince == Colony.NOT_SET ? NEAR_KEEP : NEAR);
    }

    /**
     * Whether the far rule applies: in a game, for a species whose file allows it ({@code colony.numbers_when_far}, on
     * unless a species' away life is not finished yet); on the game test server only where a test turns it on.
     */
    private boolean farCounts(Level level) {
        if (farForTests != null) {
            return farForTests;
        }
        if (keptHere.test(colony)) {
            return false;
        }
        // (Never on the game test server by itself, even where a test turns the away system on: those tests mean the
        // colony's own land being unloaded, not the far rule.)
        return !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) && isOn(level)
                && colony.profile().colony().numbersWhenFar();
    }

    /** Whether a watcher ({@link Watchers}: any game mode) is within this many blocks of the nest's middle, sideways. */
    private boolean playerNear(ServerLevel level, int radius) {
        if (nearForTests != null) {
            return nearForTests;
        }
        return Watchers.near(level, colony.getCenter(), radius);
    }

    /**
     * When the colony last came back from being away (game time, {@link Colony#NOT_SET} if not since the game loaded):
     * a watch rule counting how long something was left alone counts only from here (docs/BUGS.md 147).
     */
    public long backAt() {
        return backAt;
    }

    /** Whether the colony is away: its middle has been out of range for the grace minute (its frozen insects are numbers now). */
    public boolean isAway(ServerLevel level) {
        return awaySince != Colony.NOT_SET && level.getGameTime() - awaySince >= grace();
    }

    /** Whether the nest's middle has been out of range and the colony has not settled its coming back yet. */
    public boolean isComingBack() {
        return awaySince != Colony.NOT_SET;
    }

    // --- going away -------------------------------------------------------------------------------------------------

    /**
     * One of this colony's insects is frozen: its chunk went out of range (called when it stops being tracked, before
     * Minecraft unloads it; it may also come back without being unloaded). Writes down what it is now.
     */
    public void freeze(ServerLevel level, Entity entity) {
        if (!isOn(level)) {
            return;
        }
        Frozen what = frozenOf(level, entity);
        if (what != null) {
            frozen.put(entity.getUUID(), what);
            ColonyManager.get(level).markChanged();
        }
    }

    /** What this insect, egg or larva is now, to be written down; null if it is none the colony folds in. */
    @Nullable
    private Frozen frozenOf(ServerLevel level, Entity entity) {
        long now = level.getGameTime();
        Frozen what = null;
        // (A paralysed insect is never folded in: it lies where it was stung and dies there in time, M8 step 2b.)
        if (entity instanceof AbstractAntEntity mob && mob.nestCaste() != null && mob.isAlive() && !mob.isDowned()) {
            long grownUpAt = mob.getGrownUpAt() == AbstractAntEntity.AGE_NOT_SET ? now : mob.getGrownUpAt();
            what = new Frozen(Kind.ADULT, mob.nestCaste(), grownUpAt, loadOf(mob), 0, 0, null, now, false, mob.getLastMeal());
        } else if (entity instanceof AntEggEntity egg && egg.isAlive()) {
            long left = egg.getHatchAt() < 0 ? -1 : Math.max(0, egg.getHatchAt() - now);
            what = new Frozen(Kind.EGG, "", 0, Load.NONE, left, 0, null, now, false, Colony.NOT_SET);
        } else if (entity instanceof AntLarvaEntity larva && larva.isAlive()) {
            long hungry = larva.getLastFed() == Colony.NOT_SET ? 0 : Math.max(0, now - larva.getLastFed());
            what = new Frozen(Kind.LARVA, "", 0, Load.NONE, hungry, larva.getFed(), larva.getGrowsInto(colony), now, false, Colony.NOT_SET);
        }
        return what;
    }

    /**
     * Far colonies: the colony is away while its land is still loaded, so its real workers, soldiers, eggs and larvae
     * there are folded into the numbers at once and leave the world quietly (as a hidden trip's traveller does, with what
     * they carry counted in). The queen and king stay real, as in unloaded land. Looked over once on going away and every
     * {@link #FAR_LOOK_TICKS} after (one walk over the loaded insects, rare).
     */
    private void foldLoaded(ServerLevel level) {
        long now = level.getGameTime();
        if (lastFarLook != Colony.NOT_SET && now - lastFarLook < FAR_LOOK_TICKS || !LandReady.at(level, colony.getCenter())) {
            return;
        }
        lastFarLook = now;
        List<Entity> members = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (colony.getId().equals(ColonyManager.colonyOf(entity))) {
                members.add(entity);
            }
        }
        int folded = 0;
        for (Entity entity : members) {
            Frozen what = frozenOf(level, entity);
            if (what == null) {
                continue;
            }
            Frozen was = frozen.remove(entity.getUUID());
            if (was == null || !was.folded()) {
                foldIn(level, entity.getUUID(), what);
            }
            entity.discard();
            folded++;
        }
        if (folded > 0) {
            ColonyFrameworkMod.LOGGER.info("[away] colony {} is far from every player: {} real insects folded into the numbers",
                    colony.getId(), folded);
            ColonyManager.get(level).markChanged();
        }
    }

    /**
     * An insect that left the world another way while its colony goes out of range (a hidden trip, {@link HiddenTrips}):
     * folded into the numbers at once with what it carries, as if it had walked home.
     */
    public void foldInNow(ServerLevel level, AbstractAntEntity mob) {
        long now = level.getGameTime();
        long grownUpAt = mob.getGrownUpAt() == AbstractAntEntity.AGE_NOT_SET ? now : mob.getGrownUpAt();
        foldIn(level, mob.getUUID(), new Frozen(Kind.ADULT, mob.nestCaste(), grownUpAt, loadOf(mob), 0, 0, null, now, false, mob.getLastMeal()));
    }

    /**
     * The raid test bench, B7 ({@link Strays}): a real insect stuck out of every player's sight is folded into the numbers
     * now, with what it carries, and leaves the world (as if it had found its own way home). False if it is none the colony
     * folds in (the queen, the king, a paralysed or downed one).
     */
    public boolean foldInStray(ServerLevel level, AbstractAntEntity mob) {
        Frozen what = frozenOf(level, mob);
        if (what == null) {
            return false;
        }
        Frozen was = frozen.remove(mob.getUUID());
        if (was == null || !was.folded()) {
            foldIn(level, mob.getUUID(), what);
        }
        mob.discard();
        ColonyManager.get(level).markChanged();
        return true;
    }

    private static Load loadOf(AbstractAntEntity mob) {
        if (mob.isCarryingEgg()) {
            return Load.EGG;
        }
        if (mob.isCarryingCorpse()) {
            return Load.BODY;
        }
        if (mob.isCarryingBread()) {
            return mob.isCarryingRichBread() ? Load.RICH_NURSE_FOOD : Load.NURSE_FOOD;
        }
        if (mob.isCarryingSeed()) {
            return mob.getCarriedSeed().is(Items.PUMPKIN_SEEDS) ? Load.RICH_SEED : Load.SEED;
        }
        return Load.NONE;
    }

    /**
     * Once a second while the nest's middle is out of range (from {@link ColonyManager#tickColonies}). After the grace
     * minute every frozen insect is folded into the numbers, and the colony lives on as numbers: they grow old and eat,
     * and the nest's life goes on ({@link NestLife}). Never touches the world.
     */
    void tickAway(ServerLevel level) {
        if (!isOn(level)) {
            return;
        }
        long now = level.getGameTime();
        if (awaySince == Colony.NOT_SET) {
            awaySince = now;
            report.clear();
            lastAwayFor = 0;
            if (LandReady.at(level, colony.getCenter())) {
                // (Far, its land still loaded: one last look at the nest. A colony new land put in far from every player
                // goes away before its first second here, and the brain would think its queen out of her chamber and its
                // garden empty, and starve the royal pair; docs/BUGS.md 146.)
                colony.life().watch(level);
                for (ColonyHooks.AwayWork look : colony.hooks().all(ColonyHooks.LOOK_ON_LEAVING)) {
                    look.work(level);
                }
            }
            ColonyManager.get(level).markChanged();
        }
        if (isAway(level)) {
            boolean changed = false;
            for (Map.Entry<UUID, Frozen> entry : frozen.entrySet()) {
                if (!entry.getValue().folded()) {
                    foldIn(level, entry.getKey(), entry.getValue());
                    entry.setValue(entry.getValue().fold());
                    changed = true;
                }
            }
            if (changed) {
                ColonyFrameworkMod.LOGGER.info("[away] colony {} is away: {}", colony.getId(), describe(level));
                ColonyManager.get(level).markChanged();
            }
        }
        if (isAway(level) && farCounts(level)) {
            foldLoaded(level);
        }
        if (isAway(level)) {
            colony.tickPartsAway(level);
            ColonyManager.get(level).markChanged();
        }
    }

    /**
     * A frozen insect becomes one of the numbers (it is counted already, so the headcount stays). What it held is
     * counted at once (it walked home with it) and its block owed to the nest. A young one's clocks went on while it was
     * frozen: an egg is that much nearer hatching, a larva that still had to eat that much hungrier.
     */
    private void foldIn(ServerLevel level, UUID id, Frozen what) {
        long frozenFor = Math.max(0, level.getGameTime() - what.frozenAt());
        switch (what.kind()) {
            case ADULT -> {
                colony.population().foldIn(level, what.caste(), what.grownUpAt(), what.lastMeal());
                colony.foodMap().wentHome(id);
                colony.news().tell(level, ColonyNews.INTO_NUMBERS, new ColonyNews.IntoNumbers(id));

                switch (what.load()) {
                    case SEED, RICH_SEED -> {
                        colony.food().countIn(what.load() == Load.RICH_SEED);
                        colony.ledger().addStore(1);
                    }
                    case NURSE_FOOD, RICH_NURSE_FOOD -> {
                        NurseFood food = colony.nurseFood();
                        if (food != null) {
                            food.countedIn(what.load() == Load.RICH_NURSE_FOOD); // (food the ledger counts alone does nothing)
                        }
                        colony.ledger().addNurseFood(1);
                    }
                    case EGG -> colony.brood().addInside(Brood.Young.egg(-1));
                    case BODY -> colony.ledger().addBodies(1);
                    case NONE -> {
                    }
                }
            }
            case EGG -> colony.brood().addInside(Brood.Young.egg(what.left() < 0 ? -1 : Math.max(1, what.left() - frozenFor)));
            case LARVA -> {
                boolean full = what.fed() >= colony.brood().mealFor(what.growsInto());
                long hungry = full || !colony.isHungerOn() ? what.left() : what.left() + frozenFor;
                colony.brood().addInside(Brood.Young.larva(what.fed(), hungry, what.growsInto()));
            }
        }
    }

    // --- coming back ----------------------------------------------------------------------------------------------

    /**
     * Called by each insect of a colony (worker, soldier, egg, larva) on its server ticks: if the colony has it written
     * down as frozen, it has just come back into range. Folded into the numbers already (or frozen for longer than the
     * grace minute, so it has walked home meanwhile): it leaves the world quietly and true is returned (the caller stops).
     * Otherwise it just carries on.
     */
    public static boolean backFromFrozen(ServerLevel level, Entity entity, @Nullable UUID colonyId) {
        if (colonyId == null) {
            return false;
        }
        Colony colony = ColonyManager.get(level).getColony(colonyId);
        return colony != null && colony.away().cameBack(level, entity);
    }

    private boolean cameBack(ServerLevel level, Entity entity) {
        if (frozen.isEmpty()) {
            return false;
        }
        Frozen what = frozen.remove(entity.getUUID());
        if (what == null) {
            return false;
        }
        ColonyManager.get(level).markChanged();
        if (!what.folded()) {
            if (level.getGameTime() - what.frozenAt() < grace()) {
                return false; // only out of range for a moment: it carries on where it was
            }
            foldIn(level, entity.getUUID(), what); // left at the edge of the loaded land: it has walked home
        }
        entity.discard();
        return true;
    }

    /**
     * Once a second while the nest's middle is loaded (from {@link Colony#tick}): the first time after being away, the
     * colony comes back. Then, while the whole nest is loaded, the young that are numbers come out into the nursery and
     * the blocks catch up with the counts ({@link NestLedger}), out of every player's sight; and the brain notes where
     * the queen sits, for the next time away.
     */
    void settle(ServerLevel level) {
        if (awaySince != Colony.NOT_SET) {
            long away = level.getGameTime() - awaySince;
            awaySince = Colony.NOT_SET;
            lastFarLook = Colony.NOT_SET;
            lastAwayFor = away;
            backAt = level.getGameTime();
            ColonyHooks.Underground web = colony.underground();
            if (web != null) {
                web.cameBack(level);
            }
            ColonyFrameworkMod.LOGGER.info("[away] colony {} is back after {} ticks: {}", colony.getId(), away, describe(level));
            ColonyManager.get(level).markChanged();
        }
        colony.life().watch(level);
        if (colony.ledger().isEmpty() && colony.brood().insideCount() == 0 || !nestLoaded(level)) {
            return;
        }
        List<Vec3> eyes = colony.population().viewersInSight(level);
        // The rooms are dug in the slices between ({@link #catchUpSlice}, step 2d): the young, the seeds and the bodies
        // owed then have the rooms dug while away to go to.
        boolean changed = colony.brood().comeOut(level, eyes);
        changed |= colony.ledger().apply(level, eyes);
        if (changed) {
            ColonyManager.get(level).markChanged();
        }
    }

    /** Whether the colony is back (settled) and still owes building from while away: a quick look, every tick. */
    boolean catchingUp() {
        return awaySince == Colony.NOT_SET && colony.building().hasCatchUp();
    }

    /**
     * Every tick of the second but the colony's own brain tick (from {@link ColonyManager#tickColonies}), once the colony
     * is back and while the whole nest is loaded: a slice of the building owed from while away ({@link BuildLife#catchUp}),
     * so the catching up is spread over the second, never piled onto the tick every other part runs in (tick spikes,
     * 2026-11-10: about 200 ms in one tick each time a player walked up to a far colony).
     */
    void catchUpSlice(ServerLevel level) {
        if (!catchingUp() || !nestLoaded(level)) {
            return; // (still away, or settle has not marked it back yet: the slices start the tick after)
        }
        if (colony.building().catchUp(level, colony.population().viewersInSight(level))) {
            ColonyManager.get(level).markChanged();
        }
    }

    /** Whether the chunks of the whole nest (its middle and every room) are loaded. */
    public boolean nestLoaded(ServerLevel level) {
        if (awayForTests != null) {
            return !awayForTests;
        }
        AABB area = colony.nestArea();
        return LandReady.box(level, (int) Math.floor(area.minX), (int) Math.floor(area.minZ), (int) Math.floor(area.maxX), (int) Math.floor(area.maxZ));
    }

    // --- the colony list ------------------------------------------------------------------------------------------

    /** For the colony list and the log: " | away 3000 ticks, frozen 12 (folded 12), owed: 2 bodies, 1 seed; young inside 3". */
    public String describe(ServerLevel level) {
        StringBuilder text = new StringBuilder();
        if (awaySince != Colony.NOT_SET) {
            text.append(" | ").append(isAway(level) ? "away " : "out of range ").append(level.getGameTime() - awaySince).append(" ticks");
        }
        if (!frozen.isEmpty()) {
            long folded = frozen.values().stream().filter(Frozen::folded).count();
            text.append(" | frozen ").append(frozen.size()).append(" (folded in ").append(folded).append(")");
        }
        List<String> owed = new ArrayList<>();
        if (!colony.ledger().isEmpty()) {
            owed.add(colony.ledger().describe());
        }
        if (colony.brood().insideCount() > 0) {
            owed.add(colony.brood().insideCount() + " young");
        }
        if (colony.building().hasWork()) {
            owed.add(colony.building().describe());
        }
        if (!owed.isEmpty()) {
            text.append(" | owed to the nest: ").append(String.join(", ", owed));
        }
        if (awaySince != Colony.NOT_SET && isAway(level)) {
            text.append(" | while away so far: ").append(report.describe());
        } else if (lastAwayFor > 0) {
            String time = lastAwayFor >= 24000 ? String.format(java.util.Locale.ROOT, "%.1f days", lastAwayFor / 24000.0)
                    : String.format(java.util.Locale.ROOT, "%.1f hours", lastAwayFor / 1000.0); // (in-game: 1000 ticks an hour)
            text.append(" | while away (").append(time).append("): ").append(report.describe());
        }
        return text.toString();
    }

    /** Counts something that happened, if the colony is away now (see {@link AwayReport}). */
    public void note(ServerLevel level, AwayReport.What what, int count) {
        if (count > 0 && isOn(level) && isAway(level)) {
            report.add(what, count);
        }
    }

    /** The queen starved while away (for the report). */
    void noteQueenStarved() {
        report.queenStarved();
    }

    /** The king starved while away (for the report). */
    public void noteKingStarved() {
        report.kingStarved();
    }

    /** The founders dug their chamber while away and closed themselves in (for the report). */
    void noteFoundersSealed() {
        report.foundersSealed();
    }

    /** What happened while away, this time or the last time. */
    public AwayReport report() {
        return report;
    }

    // --- tests ----------------------------------------------------------------------------------------------------

    public void setOnForTests(@Nullable Boolean on) {
        onForTests = on;
    }

    /** Tests only: pretend the nest's middle is out of range (true) or in range (false); null: the real chunks. */
    public void setAwayForTests(@Nullable Boolean away) {
        awayForTests = away;
    }

    public void setFarForTests(@Nullable Boolean far) {
        this.farForTests = far;
    }

    /** Tests only: pretend a player is within the far rule's reach (true) or not (false); null: the real players. */
    public void setNearForTests(@Nullable Boolean near) {
        this.nearForTests = near;
    }

    public void setGraceForTests(@Nullable Long ticks) {
        graceForTests = ticks;
    }

    /** Tests only: whether this insect is written down as frozen, and whether it has been folded in. */
    public boolean isFrozen(UUID id) {
        return frozen.containsKey(id);
    }

    public boolean isFoldedIn(UUID id) {
        Frozen what = frozen.get(id);
        return what != null && what.folded();
    }

    public int getBodiesOwed() {
        return colony.ledger().bodies();
    }

    /** Layers of the food store owed to the nest (above 0: to put down). */
    public int getFoodOwed() {
        return colony.ledger().store();
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag away = new CompoundTag();
        away.putLong("since", awaySince);
        away.putLong("lastAwayFor", lastAwayFor);
        report.save(away);
        ListTag list = new ListTag();
        frozen.forEach((id, what) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putString("kind", what.kind().name());
            entry.putString("caste", what.caste());
            entry.putLong("grownUpAt", what.grownUpAt());
            entry.putString("load", what.load().name());
            entry.putLong("left", what.left());
            entry.putInt("fed", what.fed());
            if (what.growsInto() != null) {
                entry.putString("rank", Brood.savedAs(colony, what.growsInto()));
            }
            entry.putLong("frozenAt", what.frozenAt());
            entry.putBoolean("folded", what.folded());
            entry.putLong("lastMeal", what.lastMeal());
            list.add(entry);
        });
        away.put("frozen", list);
        tag.put("away", away);
    }

    void load(CompoundTag tag) {
        frozen.clear();
        if (!tag.contains("away")) {
            return; // an older save: nothing frozen known
        }
        CompoundTag away = tag.getCompound("away");
        awaySince = away.getLong("since"); // (what an older save owed is read by the NestLedger)
        lastAwayFor = away.getLong("lastAwayFor");
        report.load(away);
        ListTag list = away.getList("frozen", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String growsInto = entry.contains("rank") ? Brood.fromSaved(colony, entry.getString("rank")) : null;
            frozen.put(entry.getUUID("id"), new Frozen(Kind.valueOf(entry.getString("kind")), entry.getString("caste"),
                    entry.getLong("grownUpAt"), Load.valueOf(entry.getString("load")), entry.getLong("left"), entry.getInt("fed"),
                    growsInto, entry.getLong("frozenAt"), entry.getBoolean("folded"),
                    entry.contains("lastMeal") ? entry.getLong("lastMeal") : Colony.NOT_SET));
        }
    }
}
