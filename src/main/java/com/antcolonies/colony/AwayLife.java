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
 * the nursery, the bodies of the old into the refuse chamber, carried seeds into the store and bread to the nurses' food.
 *
 * <p>Not yet (step 2b): while away, nobody eats and the young do not grow; the queen's and the young's clocks stop (the
 * queen's are moved on by the time away when the colony comes back). Old age goes on in the numbers.
 *
 * <p>Species-neutral: any insect with a colony and a nest caste, and every egg and larva (or nymph).
 */
public class AwayLife {
    /** How long (ticks) the nest's middle or an insect must have been frozen before it counts as away (a minute; my pick). */
    static final long GRACE_TICKS = 1200;

    private final Colony colony;
    /** The game time the nest's middle was first seen out of range, or {@link Colony#NOT_SET} while it is here. Saved. */
    private long awaySince = Colony.NOT_SET;
    /** The colony's insects frozen in unloaded land: id -> what it was. Saved. */
    private final Map<UUID, Frozen> frozen = new LinkedHashMap<>();
    /** What is owed to the nest when it is loaded again (bodies for the refuse chamber, carried food). Saved. */
    private int bodiesOwed;
    private int seedsOwed;
    private int richSeedsOwed;
    private int nurseFoodOwed;
    private int richNurseFoodOwed;
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
     * One frozen insect. Adult: its caste and when it grew up, and what it held. Egg: ticks left to hatch ({@code -1}:
     * not put in its place yet). Larva: its meals so far, how long it had gone hungry, and the soldier it grows into.
     */
    record Frozen(Kind kind, String caste, long grownUpAt, Load load, long left, int fed, @Nullable TermiteSoldierEntity.Rank rank,
            long frozenAt, boolean folded) {
        Frozen fold() {
            return new Frozen(kind, caste, grownUpAt, load, left, fed, rank, frozenAt, true);
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
            what = new Frozen(Kind.ADULT, mob.nestCaste(), grownUpAt, loadOf(mob), 0, 0, null, now, false);
        } else if (entity instanceof AntEggEntity egg && egg.isAlive()) {
            long left = egg.getHatchAt() < 0 ? -1 : Math.max(0, egg.getHatchAt() - now);
            what = new Frozen(Kind.EGG, "", 0, Load.NONE, left, 0, null, now, false);
        } else if (entity instanceof AntLarvaEntity larva && larva.isAlive()) {
            long hungry = larva.getLastFed() == Colony.NOT_SET ? 0 : Math.max(0, now - larva.getLastFed());
            what = new Frozen(Kind.LARVA, "", 0, Load.NONE, hungry, larva.getFed(), larva.getSoldierRank(), now, false);
        }
        if (what != null) {
            frozen.put(entity.getUUID(), what);
            ColonyManager.get(level).markChanged();
        }
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
     * minute every frozen insect is folded into the numbers; the numbers grow old. Never touches the world.
     */
    void tickAway(ServerLevel level) {
        if (!isOn(level)) {
            return;
        }
        long now = level.getGameTime();
        if (awaySince == Colony.NOT_SET) {
            awaySince = now;
            ColonyManager.get(level).markChanged();
        }
        if (isAway(level)) {
            boolean changed = false;
            for (Map.Entry<UUID, Frozen> entry : frozen.entrySet()) {
                if (!entry.getValue().folded()) {
                    foldIn(entry.getKey(), entry.getValue());
                    entry.setValue(entry.getValue().fold());
                    changed = true;
                }
            }
            if (changed) {
                AntColoniesMod.LOGGER.info("[away] colony {} is away: {}", colony.getId(), describe(level));
                ColonyManager.get(level).markChanged();
            }
        }
        colony.population().growOld(level);
    }

    /** A frozen insect becomes one of the numbers (it is counted already, so the headcount stays), and what it held is owed. */
    private void foldIn(UUID id, Frozen what) {
        switch (what.kind()) {
            case ADULT -> {
                colony.population().foldIn(what.caste(), what.grownUpAt());
                colony.foodMap().wentHome(id);
                switch (what.load()) {
                    case SEED -> seedsOwed++;
                    case RICH_SEED -> richSeedsOwed++;
                    case NURSE_FOOD -> nurseFoodOwed++;
                    case RICH_NURSE_FOOD -> richNurseFoodOwed++;
                    case EGG -> colony.brood().addInside(Brood.Young.egg(-1));
                    case BODY -> bodiesOwed++;
                    case NONE -> {
                    }
                }
            }
            case EGG -> colony.brood().addInside(Brood.Young.egg(what.left()));
            case LARVA -> colony.brood().addInside(Brood.Young.larva(what.fed(), what.left(), what.rank()));
        }
    }

    /** Old workers of the numbers died while away: their bodies are laid in the refuse chamber when the nest is loaded again. */
    void oweBodies(int count) {
        bodiesOwed += count;
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
            foldIn(entity.getUUID(), what); // left at the edge of the loaded land: it has walked home
        }
        entity.discard();
        return true;
    }

    /**
     * Once a second while the nest's middle is loaded (from {@link Colony#tick}): the first time after being away, the
     * colony comes back (the queen's clocks are moved on by the time away: she was frozen too). Then, while the whole nest
     * is loaded, what is owed is put in its place, out of every player's sight.
     */
    void settle(ServerLevel level) {
        if (awaySince != Colony.NOT_SET) {
            long away = level.getGameTime() - awaySince;
            awaySince = Colony.NOT_SET;
            colony.queenCare().pause(away);
            AntColoniesMod.LOGGER.info("[away] colony {} is back after {} ticks: {}", colony.getId(), away, describe(level));
            ColonyManager.get(level).markChanged();
        }
        if (!owesAnything() || !isOn(level) || !nestLoaded(level)) {
            return;
        }
        List<Vec3> eyes = colony.population().viewersInSight(level);
        boolean changed = colony.brood().comeOut(level, eyes);
        for (int tries = 0; bodiesOwed > 0 && tries < 200; tries++) {
            if (colony.refuse().eatsOrBuries()) {
                bodiesOwed = 0; // termites ate their dead in the garden
                changed = true;
                break;
            }
            BlockPos spot = colony.refuse().pickCorpseSpot(level, level.getRandom());
            if (spot == null) {
                bodiesOwed = 0; // the refuse chamber is full (or not dug): the rest were carried out of the nest
                changed = true;
                break;
            }
            if (!Population.outOfSight(level, Vec3.atBottomCenterOf(spot).add(0, 0.2, 0), eyes)) {
                break; // a player can see in: later
            }
            if (colony.refuse().placeCorpse(level, spot)) {
                bodiesOwed--;
                changed = true;
            }
        }
        changed |= storeSeeds(level, eyes, false);
        changed |= storeSeeds(level, eyes, true);
        changed |= storeNurseFood(level, eyes, false);
        changed |= storeNurseFood(level, eyes, true);
        if (changed) {
            ColonyManager.get(level).markChanged();
        }
    }

    private boolean owesAnything() {
        return bodiesOwed + seedsOwed + richSeedsOwed + nurseFoodOwed + richNurseFoodOwed > 0 || colony.brood().insideCount() > 0;
    }

    /** Whether the chunks of the whole nest (its middle and every room) are loaded. */
    private boolean nestLoaded(ServerLevel level) {
        if (awayForTests != null) {
            return !awayForTests;
        }
        AABB area = colony.nestArea();
        return level.hasChunksAt(BlockPos.containing(area.minX, 0, area.minZ), BlockPos.containing(area.maxX, 0, area.maxZ));
    }

    /** Carried seeds owed go into the store (the species' food store: seeds, or forage built into fungus comb). */
    private boolean storeSeeds(ServerLevel level, List<Vec3> eyes, boolean rich) {
        boolean changed = false;
        for (int tries = 0; (rich ? richSeedsOwed > 0 : seedsOwed > 0) && tries < 200; tries++) {
            BlockPos spot = colony.food().pickFoodSpot(level, level.getRandom());
            if (spot != null && !Population.outOfSight(level, Vec3.atCenterOf(spot), eyes)) {
                break; // a player can see in: later
            }
            if (spot != null && !colony.food().depositSeed(level, spot, rich)) {
                continue;
            }
            // Stored, or the store is full (or not dug): it was eaten or put somewhere else.
            if (rich) {
                richSeedsOwed--;
            } else {
                seedsOwed--;
            }
            changed = true;
        }
        return changed;
    }

    /** Carried nurse food owed (a piece of ant bread, a fungus nodule) is put back where the nurses keep it. */
    private boolean storeNurseFood(ServerLevel level, List<Vec3> eyes, boolean rich) {
        boolean changed = false;
        for (int tries = 0; (rich ? richNurseFoodOwed > 0 : nurseFoodOwed > 0) && tries < 200; tries++) {
            BlockPos spot = colony.nurseFood().pickReturnSpot(level, level.getRandom());
            if (spot != null && !Population.outOfSight(level, Vec3.atCenterOf(spot), eyes)) {
                break;
            }
            if (spot != null && !colony.nurseFood().putBack(level, spot, rich)) {
                continue;
            }
            if (rich) {
                richNurseFoodOwed--;
            } else {
                nurseFoodOwed--;
            }
            changed = true;
        }
        return changed;
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
        if (bodiesOwed > 0) {
            owed.add(bodiesOwed + " bodies");
        }
        if (seedsOwed + richSeedsOwed > 0) {
            owed.add((seedsOwed + richSeedsOwed) + " food");
        }
        if (nurseFoodOwed + richNurseFoodOwed > 0) {
            owed.add((nurseFoodOwed + richNurseFoodOwed) + " nurse food");
        }
        if (colony.brood().insideCount() > 0) {
            owed.add(colony.brood().insideCount() + " young");
        }
        if (!owed.isEmpty()) {
            text.append(" | owed to the nest: ").append(String.join(", ", owed));
        }
        return text.toString();
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
        return bodiesOwed;
    }

    public int getFoodOwed() {
        return seedsOwed + richSeedsOwed;
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag away = new CompoundTag();
        away.putLong("since", awaySince);
        away.putInt("bodies", bodiesOwed);
        away.putInt("seeds", seedsOwed);
        away.putInt("richSeeds", richSeedsOwed);
        away.putInt("nurseFood", nurseFoodOwed);
        away.putInt("richNurseFood", richNurseFoodOwed);
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
        awaySince = away.getLong("since");
        bodiesOwed = away.getInt("bodies");
        seedsOwed = away.getInt("seeds");
        richSeedsOwed = away.getInt("richSeeds");
        nurseFoodOwed = away.getInt("nurseFood");
        richNurseFoodOwed = away.getInt("richNurseFood");
        ListTag list = away.getList("frozen", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            TermiteSoldierEntity.Rank rank = entry.contains("rank") ? TermiteSoldierEntity.Rank.valueOf(entry.getString("rank")) : null;
            frozen.put(entry.getUUID("id"), new Frozen(Kind.valueOf(entry.getString("kind")), entry.getString("caste"),
                    entry.getLong("grownUpAt"), Load.valueOf(entry.getString("load")), entry.getLong("left"), entry.getInt("fed"),
                    rank, entry.getLong("frozenAt"), entry.getBoolean("folded")));
        }
    }
}
