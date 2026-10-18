package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteSoldierEntity;

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
    /** How long (ticks) the nest's middle or an insect must have been frozen before it counts as away (a minute; my pick). */
    static final long GRACE_TICKS = 1200;

    private final Colony colony;
    /** The game time the nest's middle was first seen out of range, or {@link Colony#NOT_SET} while it is here. Saved. */
    private long awaySince = Colony.NOT_SET;
    /** What happened while away, this time or the last time (shown in the colony list). Saved. */
    private final AwayReport report = new AwayReport();
    /** How long the colony was away the last time (ticks; 0 while away or never). Saved. */
    private long lastAwayFor;
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
    record Frozen(Kind kind, String caste, long grownUpAt, Load load, long left, int fed, @Nullable TermiteSoldierEntity.Rank rank,
            long frozenAt, boolean folded, long lastMeal) {
        Frozen fold() {
            return new Frozen(kind, caste, grownUpAt, load, left, fed, rank, frozenAt, true, lastMeal);
        }
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
        return level.isLoaded(colony.getCenter());
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
        long now = level.getGameTime();
        Frozen what = null;
        if (entity instanceof AbstractAntEntity mob && mob.nestCaste() != null && mob.isAlive()) {
            long grownUpAt = mob.getGrownUpAt() == AbstractAntEntity.AGE_NOT_SET ? now : mob.getGrownUpAt();
            what = new Frozen(Kind.ADULT, mob.nestCaste(), grownUpAt, loadOf(mob), 0, 0, null, now, false, mob.getLastMeal());
        } else if (entity instanceof AntEggEntity egg && egg.isAlive()) {
            long left = egg.getHatchAt() < 0 ? -1 : Math.max(0, egg.getHatchAt() - now);
            what = new Frozen(Kind.EGG, "", 0, Load.NONE, left, 0, null, now, false, Colony.NOT_SET);
        } else if (entity instanceof AntLarvaEntity larva && larva.isAlive()) {
            long hungry = larva.getLastFed() == Colony.NOT_SET ? 0 : Math.max(0, now - larva.getLastFed());
            what = new Frozen(Kind.LARVA, "", 0, Load.NONE, hungry, larva.getFed(), larva.getSoldierRank(), now, false, Colony.NOT_SET);
        }
        if (what != null) {
            frozen.put(entity.getUUID(), what);
            ColonyManager.get(level).markChanged();
        }
    }

    /**
     * An insect that left the world another way while its colony goes out of range (a hidden trip, {@link HiddenTrips}):
     * folded into the numbers at once with what it carries, as if it had walked home.
     */
    void foldInNow(ServerLevel level, AbstractAntEntity mob) {
        long now = level.getGameTime();
        long grownUpAt = mob.getGrownUpAt() == AbstractAntEntity.AGE_NOT_SET ? now : mob.getGrownUpAt();
        foldIn(level, mob.getUUID(), new Frozen(Kind.ADULT, mob.nestCaste(), grownUpAt, loadOf(mob), 0, 0, null, now, false, mob.getLastMeal()));
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
                AntColoniesMod.LOGGER.info("[away] colony {} is away: {}", colony.getId(), describe(level));
                ColonyManager.get(level).markChanged();
            }
        }
        if (isAway(level)) {
            colony.hiddenTrips().tickAway(level);
            colony.population().tickAway(level);
            colony.life().tick(level, true);
            colony.patrol().tick(level, true);
            colony.field().tick(level, true);
            colony.building().tickAway(level);
            colony.trunkTrails().tick(level, true);
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
                colony.spots().intoNumbers(id);

                switch (what.load()) {
                    case SEED, RICH_SEED -> {
                        colony.food().countIn(what.load() == Load.RICH_SEED);
                        colony.ledger().addStore(1);
                    }
                    case NURSE_FOOD, RICH_NURSE_FOOD -> {
                        if (colony.profile().food().makesBread()) {
                            colony.bakery().countIn(what.load() == Load.RICH_NURSE_FOOD);
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
                boolean full = what.fed() >= colony.soldiers().mealFor(what.rank());
                long hungry = full || !colony.isHungerOn() ? what.left() : what.left() + frozenFor;
                colony.brood().addInside(Brood.Young.larva(what.fed(), hungry, what.rank()));
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
            lastAwayFor = away;
            AntColoniesMod.LOGGER.info("[away] colony {} is back after {} ticks: {}", colony.getId(), away, describe(level));
            ColonyManager.get(level).markChanged();
        }
        colony.life().watch(level);
        if (colony.ledger().isEmpty() && colony.brood().insideCount() == 0 && !colony.building().hasWork() && !colony.life().foundedAway()
                || !nestLoaded(level)) {
            return;
        }
        List<Vec3> eyes = colony.population().viewersInSight(level);
        // The rooms first (step 2d): the young, the seeds and the bodies owed then have the rooms dug while away to go to.
        boolean changed = colony.building().catchUp(level, eyes);
        changed |= colony.brood().comeOut(level, eyes);
        changed |= colony.ledger().apply(level, eyes);
        if (changed) {
            ColonyManager.get(level).markChanged();
        }
    }

    /** Whether the chunks of the whole nest (its middle and every room) are loaded. */
    boolean nestLoaded(ServerLevel level) {
        if (awayForTests != null) {
            return !awayForTests;
        }
        AABB area = colony.nestArea();
        return level.hasChunksAt(BlockPos.containing(area.minX, 0, area.minZ), BlockPos.containing(area.maxX, 0, area.maxZ));
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
    void note(ServerLevel level, AwayReport.What what, int count) {
        if (count > 0 && isOn(level) && isAway(level)) {
            report.add(what, count);
        }
    }

    /** The queen starved while away (for the report). */
    void noteQueenStarved() {
        report.queenStarved();
    }

    /** The king starved while away (for the report). */
    void noteKingStarved() {
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
            if (what.rank() != null) {
                entry.putString("rank", what.rank().name());
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
            TermiteSoldierEntity.Rank rank = entry.contains("rank") ? TermiteSoldierEntity.Rank.valueOf(entry.getString("rank")) : null;
            frozen.put(entry.getUUID("id"), new Frozen(Kind.valueOf(entry.getString("kind")), entry.getString("caste"),
                    entry.getLong("grownUpAt"), Load.valueOf(entry.getString("load")), entry.getLong("left"), entry.getInt("fed"),
                    rank, entry.getLong("frozenAt"), entry.getBoolean("folded"),
                    entry.contains("lastMeal") ? entry.getLong("lastMeal") : Colony.NOT_SET));
        }
    }
}
