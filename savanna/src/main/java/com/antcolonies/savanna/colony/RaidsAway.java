package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * M8 step 3f, out of sight ({@code docs/plans/m8-matabele.md}): the Matabele raid while their colony is away (its land
 * not loaded, or loaded but with no player within the far rule's reach, {@code AwayLife}), as numbers, by the same rules
 * as the real column ({@link Raids}, {@link RaidColumn}). Real Megaponera raid 1 to 4 times a day, in the morning and
 * evening raid hours, a hungry colony more (Yusuf 2026; Frank and Linsenmair 2017), so a colony nobody watches must too:
 * without it a far colony starved on an empty prey pile.
 *
 * <p>One raid at a time. In the raid hours, dry, the colony hungry and short of its day's raids, a raid sets out to the
 * best remembered find still worth a raid ({@link Raids#need}), its size as the real column's ({@link Raids#raidSize}),
 * from the numbers inside. Out of sight the numbers know the way to every find the colony remembers (its scouts are
 * among them). The time comes from the distance: mustering at the entrance, the march out ({@code pace}), the attack,
 * regrouping and the walk home laden. At the attack the raiders take termites from the termite colony there (the workers
 * feeding under the mud are numbers in its galleries, M8 step 3d's rule as a simple fair loss): {@code catch_per_raider}
 * a raider, never more than {@code most_take_share} of the termite colony's workers inside; the termites remember the
 * attack (their danger map: they keep off the spot for a few days and plug the holes there, brain step 4g), so the
 * spot dries up and the scouts must find another. A few raiders die and a few come home hurt; the catch goes onto the
 * prey pile, counted (its layers laid out of sight later). A find whose termites have gone (the colony dead, or keeping
 * off the spot) counts an empty check. No raid leaves unless its attack starts before dusk. (A spot a player stood at
 * was left "for real ants" until the brain while away, step 3: now the raid goes there and is shown as the player
 * comes near it, {@link #show}.)
 *
 * <p>Out of sight the scouts are numbers too: in the scouting hours a hungry colony that knows no find worth a raid has
 * its scouts (as many as it would send) each find, by chance, one of the termites' feeding places within its reach that
 * it does not know yet (the termite colonies' own record of their ways out to food), scored as a scout would, passed
 * over if too poor. Every raid and find writes a {@code [raid]} diary line and counts in the away report.
 *
 * <p>Never reads or changes a block of land not loaded. A raid out when a watcher comes near it is shown as real ants
 * on its trail ({@link #show}; else it finishes as numbers on its own time, its raiders walking in out of sight); a real
 * column out when the colony goes away goes on as numbers.
 * Numbers: {@code raids.away} ({@link SavannaNumbers.AwayRaidNumbers}). Every number my pick, to confirm.
 */
public final class RaidsAway {
    /** How often the number scouts search (ticks: once a game hour). */
    private static final long SCOUT_EVERY = 1000;

    private final Raids raids;
    private final Colony colony;
    /** The raid out of sight now, or null. Saved. */
    @Nullable
    private Out out;
    /**
     * The day the raids are counted for ({@code timeOfDay / 24000}) and how many left on it, near or away (M8 step 3e: one
     * count for the colony's whole day, so a colony raiding near the player and out of sight on the same day still makes
     * at most {@code most_raids_a_day}), and how many of them left out of sight (the away report). Saved.
     */
    private long day = Long.MIN_VALUE;
    private int raidsToday;
    private int awayToday;
    /** When the number scouts next search (game time). Not saved. */
    private long nextScoutAt;

    /** A raid out of sight: where to, how many, when it attacks and is home, the termite colony, and what it took. */
    static final class Out {
        final BlockPos at;
        int raiders;
        final long attackAt;
        long backAt;
        @Nullable
        UUID termites;
        boolean attacked;
        int caught;
        int lost;
        int hurt;

        Out(BlockPos at, int raiders, long attackAt, long backAt, @Nullable UUID termites) {
            this.at = at;
            this.raiders = raiders;
            this.attackAt = attackAt;
            this.backAt = backAt;
            this.termites = termites;
        }
    }

    RaidsAway(Raids raids, Colony colony) {
        this.raids = raids;
        this.colony = colony;
    }

    private SavannaNumbers.AwayRaidNumbers numbers() {
        return colony.profile().section(SavannaNumbers.AWAY_RAIDS).orElse(SavannaNumbers.AwayRaidNumbers.DEFAULT);
    }

    /** M8 step 3e: the most raids a starving colony sends in a day ({@code raids.away.most_raids_a_day}; near or away). */
    int mostRaidsADay() {
        return Math.max(1, numbers().mostRaidsADay());
    }

    /** Whether the raid out of sight has attacked (tests). */
    public boolean hasAttacked() {
        return out != null && out.attacked;
    }

    /** Whether a raid is out of sight now. */
    public boolean isOut() {
        return out != null;
    }

    /** Raids set out today, near or away (tests; as last counted). */
    public int raidsToday() {
        return raidsToday;
    }

    /** M8 step 3e: raids set out today, near or away (the day turned over first). */
    int raidsToday(ServerLevel level) {
        newDay(level);
        return raidsToday;
    }

    /** M8 step 3e: a raid with real ants set out near the player: one more of the day's raids. */
    void countRaid(ServerLevel level) {
        newDay(level);
        raidsToday++;
    }

    /** Once a second while the colony is away: the raid out goes on; else one may set out; the number scouts search. */
    void tick(ServerLevel level) {
        long now = level.getGameTime();
        newDay(level);
        if (out != null) {
            step(level, now);
        } else {
            maySetOut(level, now);
        }
        if (out == null && now >= nextScoutAt) {
            nextScoutAt = now + SCOUT_EVERY;
            scout(level);
        }
        scoutsAsNumbers = true;
    }

    /**
     * Once a second while the colony is here: a raid out as numbers goes on by its own times, and (the brain while away,
     * step 3) is shown as real ants once a watcher is near it ({@link #show}).
     */
    void tickHere(ServerLevel level) {
        if (out != null) {
            step(level, level.getGameTime());
        }
        if (out != null) {
            show(level);
        }
        if (scoutsAsNumbers) {
            scoutsAsNumbers = false; // (the colony is here: from now on its scouts are real ants, from the nest or shown here)
            showScouts(level);
        }
    }

    // --- step 3 of the brain while away: where the raid is, and showing it ---------------------------------------------------

    /**
     * Just past how far insects are drawn (48 blocks, real sizes step 1): the raiders are put on the trail only where
     * no watcher is within this of them, so nobody sees them appear.
     */
    private static final int SHOW_PAST = 50;
    /** A raid this near its nest (blocks along its way) is not put on the trail: not attacked yet, it forms in the nest; nearly home, it walks in unseen. */
    private static final double BY_THE_NEST = 8.0;

    /** Where a raid out as numbers is: still mustering in the nest, how much of the way out it is from the nest (0 to 1), and whether it walks home. */
    private record Where(boolean mustering, double share, boolean homeward) {
    }

    /**
     * Where the raid is now, from its own times (the march out ends at {@code attackAt}; the walk home ends at
     * {@code backAt}; before the walk home it is at the spot, fighting and regrouping).
     */
    private Where where(long now) {
        double distance = Math.max(1.0, RaidGround.flat(colony.getCenter(), out.at));
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        if (!out.attacked) {
            double walk = distance / Math.max(0.1, numbers.pace()) * 20.0;
            double left = out.attackAt - now;
            return left > walk ? new Where(true, 0.0, false) : new Where(false, Math.max(0.0, 1.0 - left / walk), false);
        }
        SavannaNumbers.CarryNumbers carry = raids.carryNumbers();
        double laden = carry == null ? SavannaNumbers.CarryNumbers.DEFAULT.ladenPace() : carry.ladenPace();
        double walk = distance / Math.max(0.1, numbers.pace() * laden) * 20.0;
        double left = out.backAt - now;
        return left > walk ? new Where(false, 1.0, false) : new Where(false, Math.max(0.0, left / walk), true);
    }

    /** The place this share of the way out from the nest to the raid's spot, along the way the raid walks. */
    private BlockPos placeAt(double share, long now) {
        List<BlockPos> way = raids.wayTo(out.at, now);
        return way.get((int) Math.round(Math.max(0.0, Math.min(1.0, share)) * (way.size() - 1)));
    }

    /** Where the raid out as numbers is on its trail now (a watcher near it watches the colony, {@code Raids.outThere}); null if none is out. */
    @Nullable
    BlockPos whereNow(ServerLevel level) {
        long now = level.getGameTime();
        return out == null ? null : placeAt(where(now).share(), now);
    }

    /**
     * The raid out as numbers is shown as real ants once a watcher is within the far rule's reach of where it is now
     * (the user, 2026-11-11: raids that are out when the player comes near should show): its raiders come out of the
     * numbers onto the trail there, in the stage it is in (marching out; regrouping at the spot or walking home with the
     * catch it already made as numbers in their jaws), and it goes on as a real column ({@code Raids.showRaid}). Only
     * where no watcher could see them appear ({@link #SHOW_PAST}) and the land ticks; else it goes on as numbers and
     * is tried again next second. One that has only just left forms in the nest as any raid does; one nearly home
     * walks in unseen.
     */
    private void show(ServerLevel level) {
        long now = level.getGameTime();
        Where where = where(now);
        BlockPos front = placeAt(where.share(), now);
        com.antcolonies.colony.AwayLife watch = colony.away();
        if (!watch.watcherNear(level, front, com.antcolonies.colony.AwayLife.NEAR)) {
            return; // (the colony is watched somewhere else: this raid goes on as numbers)
        }
        double distance = Math.max(1.0, RaidGround.flat(colony.getCenter(), out.at));
        boolean byTheNest = where.mustering() || where.share() * distance < BY_THE_NEST;
        if (byTheNest && out.attacked) {
            return; // (nearly home: they walk in out of sight)
        }
        SavannaNumbers.ColumnNumbers column = raids.columnNumbers();
        double spacing = column == null ? 0.3 : column.spacing();
        // (the column's other end: behind the front toward the nest on the way out, toward the spot on the way home)
        double lengthShare = out.raiders * spacing / distance;
        BlockPos back = placeAt(where.share() + (where.homeward() ? lengthShare : -lengthShare), now);
        if (!byTheNest && (watch.seerNear(level, front, SHOW_PAST) || watch.seerNear(level, back, SHOW_PAST)
                || !level.isPositionEntityTicking(front) || !level.isPositionEntityTicking(back))) {
            return; // (they would be seen appearing, or the land there does not tick)
        }
        if (out.attacked && out.lost + out.hurt > 0) {
            // (what the fight cost, counted now as it would be at home)
            int lost = colony.population().loseInside(level, Caste.WORKER, out.lost, AwayReport.What.RAIDERS_LOST);
            colony.away().note(level, AwayReport.What.RAIDERS_HURT, out.hurt);
            out.raiders = Math.max(1, out.raiders - lost);
            out.lost = 0;
            out.hurt = 0;
        }
        if (raids.showRaid(level, out.at, front, out.raiders, byTheNest, out.attacked, where.share(), out.caught)) {
            out = null;
        }
        ColonyManager.get(level).markChanged();
    }

    /** The raiders out as numbers now (they are not in the nest: the colony keeps so many fewer real ants at home). */
    int outAsNumbers() {
        return out == null ? 0 : out.raiders;
    }

    private void newDay(ServerLevel level) {
        long today = Math.floorDiv(colony.timeOfDay(level), 24000L);
        if (today != day) {
            day = today;
            raidsToday = 0;
            awayToday = 0;
        }
    }

    // --- setting out --------------------------------------------------------------------------------------------------

    private void maySetOut(ServerLevel level, long now) {
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        SavannaNumbers.ScoutNumbers scouting = raids.numbers();
        SavannaNumbers.ColumnNumbers column = raids.columnNumbers();
        if (scouting == null || column == null || raids.column() != null || !raids.isRaidTime(level) || !raids.isHungry()
                || !raids.dayHasRoom(level) || colony.population().inside(Caste.WORKER) <= 0) {
            return;
        }
        boolean byTheLand = noTermitesOnRecord(level);
        for (Raids.Find find : raids.finds()) {
            Colony termites = termitesAt(level, find.at());
            if (termites == null && byTheLand) { // (a termite colony that owns the spot is always raided by the real rules)
                if (find.worth() < raids.need(find.at())) {
                    continue;
                }
                double distance = RaidGround.flat(colony.getCenter(), find.at());
                setOut(level, now, find, null, distance, column.formTicks() + Math.round(distance / Math.max(0.1, numbers.pace()) * 20.0));
                return;
            }
            if (termites == null || termites.keepsOff(level, find.at())) {
                raids.diary(level, "out of sight: the scouts find the spot at " + find.at().toShortString() + " empty ("
                        + (termites == null ? "no termite colony there any more" : "the termites keep off it now") + ")");
                raids.foundEmpty(level, find);
                return; // (one check a second)
            }
            if (find.worth() < raids.need(find.at())) {
                continue; // (too poor for the colony now)
            }
            double distance = RaidGround.flat(colony.getCenter(), find.at());
            long outTicks = column.formTicks() + Math.round(distance / Math.max(0.1, numbers.pace()) * 20.0);
            long dayTime = colony.timeOfDay(level);
            SavannaNumbers.HoursSoundNumbers hours = colony.profile().section(SavannaNumbers.HOURS_SOUND)
                    .orElse(SavannaNumbers.HoursSoundNumbers.DEFAULT);
            if (hours.isDusk(dayTime + outTicks, scouting)
                    || SavannaNumbers.ScoutNumbers.hourOf(dayTime + outTicks) < SavannaNumbers.ScoutNumbers.hourOf(dayTime)) {
                raids.diary(level, "out of sight: no raid to " + find.at().toShortString() + ", it would reach the termites after dusk");
                return;
            }
            setOut(level, now, find, termites, distance, outTicks);
            return;
        }
    }

    private void setOut(ServerLevel level, long now, Raids.Find find, @Nullable Colony termites, double distance, long outTicks) {
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        SavannaNumbers.ColumnNumbers column = raids.columnNumbers();
        SavannaNumbers.CarryNumbers carry = raids.carryNumbers();
        int size = Math.max(1, Math.min(raids.raidSize(find), colony.population().inside(Caste.WORKER)));
        long attackAt = now + outTicks;
        out = new Out(find.at().immutable(), size, attackAt, attackAt + numbers.fightTicks() + homeTicks(distance, column, carry),
                termites == null ? null : termites.getId());
        raidsToday++;
        awayToday++;
        raids.forgetWaiting(find);
        raids.walkTrail(find.at(), now);
        colony.away().note(level, AwayReport.What.RAIDS, 1);
        colony.away().note(level, AwayReport.What.RAIDERS, size);
        raids.diary(level, "out of sight: a raid of " + size + " sets out to " + find.at().toShortString() + " (worth " + find.worth()
                + ", " + Math.round(distance) + " blocks), raid " + raidsToday + " today (near or away); the attack in " + outTicks
                + " ticks, home in " + (out.backAt - now));
        ColonyManager.get(level).markChanged();
    }

    /** Regrouping at the spot and the walk home laden, in ticks. */
    private long homeTicks(double distance, @Nullable SavannaNumbers.ColumnNumbers column, @Nullable SavannaNumbers.CarryNumbers carry) {
        double laden = carry == null ? SavannaNumbers.CarryNumbers.DEFAULT.ladenPace() : carry.ladenPace();
        return (column == null ? 400 : column.regroupTicks())
                + Math.round(distance / Math.max(0.1, numbers().pace() * laden) * 20.0);
    }

    /**
     * A real raid column was out when the colony went away: it goes on as numbers from where it is
     * ({@link RaidColumn#shareOut}; step 3 of the brain while away, it was "about half the march" before, and a column
     * on its way home started the whole walk home again). Not yet at its spot: it attacks after the rest of the march;
     * already attacked: it walks the rest of the way home with what it picked up (from the spot, it regroups first).
     */
    void fromColumn(ServerLevel level, RaidColumn column) {
        long now = level.getGameTime();
        Raids.Find find = column.find();
        double distance = RaidGround.flat(colony.getCenter(), find.at());
        Colony termites = termitesAt(level, find.at());
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        SavannaNumbers.ColumnNumbers columnNumbers = raids.columnNumbers();
        SavannaNumbers.CarryNumbers carry = raids.carryNumbers();
        double share = column.shareOut();
        if (column.attackedAt() < 0) {
            long attackAt = now + Math.round(distance * (1.0 - share) / Math.max(0.1, numbers.pace()) * 20.0);
            out = new Out(find.at().immutable(), Math.max(1, column.size()), attackAt,
                    attackAt + numbers.fightTicks() + homeTicks(distance, columnNumbers, carry), termites == null ? null : termites.getId());
        } else {
            long home = column.stage() == RaidColumn.Stage.HOMING ? homeTicks(distance * share, columnNumbers, carry) - (columnNumbers == null ? 400 : columnNumbers.regroupTicks())
                    : homeTicks(distance, columnNumbers, carry);
            out = new Out(find.at().immutable(), Math.max(1, column.size()), now, now + Math.max(0, home), null);
            out.attacked = true;
            // (the termites it stung and picked up, and what its raiders going as numbers took under the mud, M8 step 3d)
            out.caught = column.carried() + column.hiddenCatch();
        }
        raids.diary(level, "the colony is out of sight with its raid to " + find.at().toShortString() + " out: it goes on as "
                + out.raiders + " numbers, home in " + (out.backAt - now) + " ticks");
        ColonyManager.get(level).markChanged();
    }

    // --- the attack and home -------------------------------------------------------------------------------------------

    private void step(ServerLevel level, long now) {
        if (!out.attacked && now >= out.attackAt) {
            attack(level, now);
        }
        if (out.attacked && now >= out.backAt) {
            home(level, now);
        }
    }

    private void attack(ServerLevel level, long now) {
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        RandomSource random = colony.random();
        out.attacked = true;
        Colony termites = out.termites == null ? null : ColonyManager.get(level).getColony(out.termites);
        Raids.Find find = raids.findAt(out.at);
        int answering = 0; // (M8 step 3S: the termite soldiers that come to the break, as near)
        if (termites == null && noTermitesOnRecord(level)) {
            // By the land (see noTermitesOnRecord): the catch a raid this size carries, no colony's count to take it from.
            int taken = Math.min(RaidCatch.couldCarry(colony, out.raiders, random), Math.max(1, out.raiders / 10));
            out.caught += taken;
            if (find != null) {
                raids.checked(find, now);
            }
            raids.diary(level, "out of sight: the raid attacks at " + out.at.toShortString() + " by the land (no termite colony on record in its reach yet): "
                    + taken + " termite(s) stung and taken");
        } else if (termites == null || termites.keepsOff(level, out.at)) {
            raids.diary(level, "out of sight: the raid reached " + out.at.toShortString() + " and found nothing to sting");
            if (find != null) {
                raids.foundEmpty(level, find);
            }
        } else {
            // M8 step 3d: the termites feeding under the mud there, each counted one standing for many real ones
            int wanted = RaidCatch.couldCarry(colony, out.raiders, random);
            int most = RaidCatch.mostToTake(colony, termites);
            int taken = Math.min(wanted, most);
            int countLost = RaidCatch.take(level, termites, taken);
            out.caught += taken;
            // The alarm rework, step 5: as near a player now, the soldiers within earshot come to the break (measured
            // near: 12 to 17 of a full colony's 20 or so; here 6 in 10, at most 12) and hold the gallery from inside,
            // so only about a quarter of them are lost (the ones that clamp on to a leg and are dragged out, or are
            // caught on the way to their place) and carried home with the workers; the count loses them as the workers (3d)
            String minor = TermiteSoldierEntity.Rank.MINOR.caste();
            String major = TermiteSoldierEntity.Rank.MAJOR.caste();
            int soldiers = termites.getCount(minor) + termites.getCount(major);
            answering = soldiers <= 0 ? 0 : Math.max(1, Math.min(12, (int) Math.round(soldiers * ANSWERING_SHARE)));
            int stung = count(random, answering, STUNG_SOLDIER_SHARE);
            if (stung > 0) {
                termites.population().loseToHunters(level, termites.population().inside(minor) > 0 ? minor : major, stung,
                        AwayReport.What.TAKEN_BY_RAIDERS);
                out.caught += stung;
            }
            // the termites remember the attack: they keep off the spot a few days and plug the holes there (brain step 4g)
            termites.danger().hurt(level, out.at, SavannaEntities.MATABELE_MAJOR.get().toShortString(), true);
            if (find != null) {
                if (taken > 0) {
                    raids.checked(find, now);
                } else {
                    raids.foundEmpty(level, find);
                }
            }
            raids.diary(level, "out of sight: the raid attacks at " + out.at.toShortString() + ": " + taken + " termite(s) stung and taken (of "
                    + wanted + " the raiders could carry, at most " + most + " to take; the termite colony's count lost " + countLost
                    + ", each counted termite standing for " + termites.population().standsFor() + ", " + termites.population().inside(Caste.WORKER)
                    + " workers inside now; " + answering + " soldier(s) came to the break)");
        }
        // M8 step 3S: each soldier that came may kill a raider or send one home hurt, on top of the raid's own losses
        out.lost = Math.min(out.raiders, count(random, out.raiders, numbers.lostShare()) + count(random, answering, SOLDIER_KILLS));
        out.hurt = Math.min(out.raiders - out.lost, count(random, out.raiders - out.lost, numbers.hurtShare()) + count(random, answering, SOLDIER_HURTS));
        ColonyManager.get(level).markChanged();
    }

    private void home(ServerLevel level, long now) {
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        if (pile != null && out.caught > 0) {
            pile.bringInCounted(now, out.caught);
        }
        int lost = colony.population().loseInside(level, Caste.WORKER, out.lost, AwayReport.What.RAIDERS_LOST);
        colony.away().note(level, AwayReport.What.PREY_BROUGHT, out.caught);
        colony.away().note(level, AwayReport.What.RAIDERS_HURT, out.hurt);
        raids.walkTrail(out.at, now);
        raids.diary(level, "out of sight: the raid to " + out.at.toShortString() + " is home with " + out.caught + " termite(s), "
                + lost + " raider(s) lost, " + out.hurt + " home hurt" + (pile == null ? "" : pile.describe(level)));
        out = null;
        ColonyManager.get(level).markChanged();
    }

    /**
     * Soldiers at a raid out of sight (my picks, set to what the measuring scene of the alarm rework's step 5 gave near a
     * player: of a raid of 20 that broke in, 0 to 6 raiders dead, 1 to 14 hurt, 1 to 6 soldiers lost): the share of the
     * colony's soldiers that comes to the break, and of those the share that each kill a raider, send one home hurt,
     * are stung and carried home.
     */
    private static final double ANSWERING_SHARE = 0.6;
    private static final double SOLDIER_KILLS = 0.12;
    private static final double SOLDIER_HURTS = 0.3;
    private static final double STUNG_SOLDIER_SHARE = 0.25;

    /** How many of {@code of} by a chance {@code share} each (a coin per ant, at most 2,500 ants). */
    private static int count(RandomSource random, int of, double share) {
        int hit = 0;
        for (int i = 0; i < of; i++) {
            if (random.nextDouble() < share) {
                hit++;
            }
        }
        return hit;
    }

    // --- step 4 of the brain while away: where the scouts out as numbers are, and showing them -----------------------------

    /** Whether the colony's scouts are numbers now (it was away at its last tick): only then have they places of their own. */
    private boolean scoutsAsNumbers;
    /** Nearer the nest than this (blocks) a scout out as numbers has no place of its own: the nest's own rule covers it. */
    private static final double SCOUT_FROM = 16.0;
    /** After a scout could not be shown (no major left inside), none is tried again before this (game time). */
    private long noScoutShownBefore;

    /** A scout out as numbers: which of the colony's scouts, its own way out (radians), and where it is now. */
    record ScoutAt(int index, double heading, BlockPos at) {
    }

    /**
     * Where the colony's scouts out as numbers are now (the brain while away, step 4). The dice of {@link #scout} say
     * what they find; this says where they are, so a watcher who comes near one sees a real scout there
     * ({@link #showScouts}). Each scout keeps its own way out, as real scouts do (route fidelity, {@code ScoutMemory}),
     * and walks out to the colony's reach and home again at the numbers' pace, one trip after another, each scout at
     * its own point of its trip. Worked out from the game time alone: nothing is kept or saved. Only places where a
     * real scout could be put unseen count: none nearer the nest than {@link #SCOUT_FROM}, none a watcher is within
     * {@link #SHOW_PAST} of (it would be seen appearing; and the colony must not be held "watched" by a scout that can
     * never be shown), none in land that does not tick. Empty when the colony would send no scouts
     * (the same rules as {@link #scout}).
     */
    List<ScoutAt> scoutsNow(ServerLevel level) {
        SavannaNumbers.ScoutNumbers scouting = raids.numbers();
        long now = level.getGameTime();
        if (!scoutsAsNumbers || out != null || scouting == null || now < noScoutShownBefore || !raids.isScoutingTime(level)
                || !raids.isHungry() || colony.population().inside(Caste.WORKER) <= 0) {
            return List.of();
        }
        for (Raids.Find find : raids.finds()) {
            if (find.worth() >= raids.need(find.at())) {
                return List.of(); // (it knows a find worth a raid: no scout is out)
            }
        }
        int reach = raids.range();
        long trip = Math.max(200L, Math.round(2.0 * reach / Math.max(0.1, numbers().pace()) * 20.0));
        BlockPos nest = colony.getCenter();
        List<ScoutAt> places = new ArrayList<>();
        int wanted = raids.scoutsWanted();
        for (int i = 0; i < wanted; i++) {
            long seed = net.minecraft.util.Mth.murmurHash3Mixer((int) colony.getId().getLeastSignificantBits() + i * 0x9E3779B9);
            double heading = (seed & 0xFFFF) / 65536.0 * Math.PI * 2;
            double phase = Math.floorMod(now + Math.floorMod(seed >> 16, trip), trip) / (double) trip;
            double far = reach * (phase < 0.5 ? phase * 2 : (1.0 - phase) * 2);
            if (far < SCOUT_FROM) {
                continue;
            }
            BlockPos at = BlockPos.containing(nest.getX() + 0.5 + Math.cos(heading) * far, nest.getY(), nest.getZ() + 0.5 + Math.sin(heading) * far);
            if (!level.isPositionEntityTicking(at) || colony.away().seerNear(level, at, SHOW_PAST)) {
                continue;
            }
            places.add(new ScoutAt(i, heading, at));
        }
        return places;
    }

    /** The places of {@link #scoutsNow}: a watcher near one watches the colony ({@code Raids.outThere}). */
    public List<BlockPos> scoutPlaces(ServerLevel level) {
        return scoutsNow(level).stream().map(ScoutAt::at).toList();
    }

    /**
     * The colony is here again: each scout out as numbers that a watcher is near (the far rule's reach, and out of
     * sight, {@link #scoutsNow}) is a real scout from now on, standing where it was, its own way out kept; it goes on
     * with the real search from there ({@code MatabeleScoutGoal}), so what it finds is what is really there, not the
     * dice. The others stay in the nest's numbers; real scouts leave from the nest as always.
     */
    private void showScouts(ServerLevel level) {
        scoutsAsNumbers = true; // (asked as it was a moment ago, while its scouts were numbers)
        List<ScoutAt> places = scoutsNow(level);
        scoutsAsNumbers = false;
        for (ScoutAt one : places) {
            if (!colony.away().watcherNear(level, one.at(), com.antcolonies.colony.AwayLife.NEAR)) {
                continue;
            }
            if (!raids.showScout(level, one.at(), one.heading())) {
                noScoutShownBefore = level.getGameTime() + 2400; // (no major to show: not asked again for two minutes)
                return;
            }
            ColonyManager.get(level).markChanged();
        }
    }

    // --- the scouts as numbers ---------------------------------------------------------------------------------------

    private void scout(ServerLevel level) {
        SavannaNumbers.ScoutNumbers scouting = raids.numbers();
        if (scouting == null || !raids.isScoutingTime(level) || !raids.isHungry() || colony.population().inside(Caste.WORKER) <= 0) {
            return;
        }
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        for (Raids.Find find : raids.finds()) {
            if (find.worth() >= raids.need(find.at())) {
                return; // (it knows a find worth a raid: the raid hours will take it)
            }
        }
        int reach = raids.range();
        List<BlockPos> places = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Colony termites : Raids.termiteColonies(level, colony.getCenter(), reach + numbers.termiteReach())) {
            if (termites.getCount(Caste.WORKER) <= 0) {
                continue;
            }
            FoodWays ways = termites.nest().foodWays();
            List<BlockPos> foods = new ArrayList<>();
            for (FoodWays.NewTunnel tunnel : ways.getNewTunnels()) {
                if (!ways.isSpent(tunnel.tunnel().hole())) {
                    foods.add(tunnel.food());
                }
            }
            for (FoodWays.Runway runway : ways.getRunways()) {
                foods.add(runway.food());
            }
            if (foods.isEmpty()) {
                // A termite colony nobody has been near has no tunnel or runway on record yet (they are planned near a
                // player), but its numbers feed all the same, under the dead wood it knows: one place per 8-block
                // square of that wood (the brain while away, 2026-11-11: far Matabele found nothing and starved).
                List<com.antcolonies.species.SpeciesProfile.PlantFood> kinds = termites.profile().food().plants();
                java.util.Set<Long> squares = new java.util.HashSet<>();
                for (java.util.Map.Entry<BlockPos, com.antcolonies.colony.FieldLife.Plant> plant : termites.field().known().entrySet()) {
                    int kind = plant.getValue().kind();
                    BlockPos pos = plant.getKey();
                    if (kind >= 0 && kind < kinds.size() && kinds.get(kind).rich()
                            && squares.add(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 3, pos.getZ() >> 3))) {
                        foods.add(pos);
                    }
                }
                if (foods.isEmpty()) {
                    // (its brain has no picture of its land either: it forages by the land round its mound,
                    // FieldLife.forageByTheLand, so the scouts find it feeding a dozen blocks out on their side)
                    BlockPos mound = termites.getCenter();
                    double dx = colony.getCenter().getX() - mound.getX();
                    double dz = colony.getCenter().getZ() - mound.getZ();
                    double far = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
                    foods.add(BlockPos.containing(mound.getX() + dx / far * 12 + 0.5, mound.getY(), mound.getZ() + dz / far * 12 + 0.5));
                }
            }
            for (BlockPos food : foods) {
                double distance = RaidGround.flat(colony.getCenter(), food);
                if (distance > reach || raids.findAt(food) != null || termites.keepsOff(level, food)) {
                    continue;
                }
                places.add(food);
                weights.add(1.0 / (distance + 8.0)); // (nearer places are found sooner)
            }
        }
        RandomSource random = colony.random();
        if (places.isEmpty() && noTermitesOnRecord(level)) {
            // By the land: a place half the scouts' reach out, any way.
            double way = random.nextDouble() * Math.PI * 2;
            double far = Math.max(16, reach / 2.0);
            places.add(BlockPos.containing(colony.getCenter().getX() + Math.cos(way) * far, colony.getCenter().getY(), colony.getCenter().getZ() + Math.sin(way) * far));
            weights.add(1.0);
        }
        if (places.isEmpty()) {
            return;
        }
        for (int scout = 0; scout < raids.scoutsWanted() && !places.isEmpty(); scout++) {
            if (random.nextDouble() >= numbers.scoutFindChance()) {
                continue;
            }
            int pick = pickWeighted(random, weights);
            BlockPos food = places.remove(pick);
            weights.remove(pick);
            int worth = numbers.scoutWorthLeast() + random.nextInt(Math.max(1, numbers.scoutWorthMost() - numbers.scoutWorthLeast() + 1));
            if (worth < raids.need(food)) {
                raids.diary(level, "out of sight: a scout passes over termites at " + food.toShortString() + " (worth " + worth
                        + ", " + Math.round(RaidGround.flat(colony.getCenter(), food)) + " blocks out: too poor)");
                continue;
            }
            raids.remember(level, food, worth, line(colony.getCenter(), food));
            colony.away().note(level, AwayReport.What.RAID_FINDS, 1);
            raids.diary(level, "out of sight: a scout finds termites feeding at " + food.toShortString() + ", worth " + worth + ", "
                    + Math.round(RaidGround.flat(colony.getCenter(), food)) + " blocks out");
        }
    }

    /**
     * The brain while away (2026-11-11, my pick): whether no living termite colony's mound is on record within the
     * scouts' reach. Matabele only live where living termites are near (new land's rule), so this means the termites' land has
     * not been made yet: their colony is put in when a player first comes near their mound. Until then the scouts and
     * raids out of sight hunt by the land, as the far termites forage by it ({@code FieldLife.forageByTheLand}): a raid
     * brings home its catch with no colony's count to take it from. Without this a Matabele colony at the rim of the
     * land a player has seen found nothing and starved in four days (the soaks of 2026-11-11, 3 far colonies of 8).
     */
    private boolean noTermitesOnRecord(ServerLevel level) {
        for (Colony termites : Raids.termiteColonies(level, colony.getCenter(), raids.range())) {
            if (termites.getCount(Caste.WORKER) > 0) {
                return false;
            }
        }
        return true;
    }

    private static int pickWeighted(RandomSource random, List<Double> weights) {
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return i;
            }
        }
        return weights.size() - 1;
    }

    /** A scout's line home out of sight: straight, a point every 3 blocks, its height between the nest's and the spot's. */
    static List<BlockPos> line(BlockPos from, BlockPos to) {
        double distance = Math.max(1.0, RaidGround.flat(from, to));
        int steps = Math.max(1, (int) Math.ceil(distance / 3.0));
        List<BlockPos> points = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            points.add(BlockPos.containing(from.getX() + (to.getX() - from.getX()) * t + 0.5, from.getY() + (to.getY() - from.getY()) * t,
                    from.getZ() + (to.getZ() - from.getZ()) * t + 0.5));
        }
        return points;
    }

    // --- whose termites, who is near ------------------------------------------------------------------------------------

    /** The living termite colony whose mound's middle is nearest this spot, within {@code termite_reach}; null if none. */
    @Nullable
    private Colony termitesAt(ServerLevel level, BlockPos at) {
        return RaidCatch.termitesAt(level, at, numbers().termiteReach());
    }

    /** For the colony list: "; a raid of 120 out of sight to (x, y, z), home in 1200 ticks; 2 raid(s) today out of sight". */
    String describe(ServerLevel level) {
        StringBuilder line = new StringBuilder();
        if (out != null) {
            line.append("; a raid of ").append(out.raiders).append(" out of sight to ").append(out.at.toShortString())
                    .append(out.attacked ? ", home in " + Math.max(0, out.backAt - level.getGameTime()) + " ticks with " + out.caught + " termite(s)"
                            : ", attacking in " + Math.max(0, out.attackAt - level.getGameTime()) + " ticks");
        }
        if (awayToday > 0) {
            line.append("; ").append(awayToday).append(" raid(s) out of sight today");
        }
        return line.toString();
    }

    // --- saving -------------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag away = new CompoundTag();
        away.putLong("day", day);
        away.putInt("today", raidsToday);
        away.putInt("awayToday", awayToday);
        if (out != null) {
            CompoundTag raid = new CompoundTag();
            raid.putLong("at", out.at.asLong());
            raid.putInt("raiders", out.raiders);
            raid.putLong("attackAt", out.attackAt);
            raid.putLong("backAt", out.backAt);
            if (out.termites != null) {
                raid.putUUID("termites", out.termites);
            }
            raid.putBoolean("attacked", out.attacked);
            raid.putInt("caught", out.caught);
            raid.putInt("lost", out.lost);
            raid.putInt("hurt", out.hurt);
            away.put("out", raid);
        }
        tag.put("raidsAway", away);
    }

    void load(CompoundTag tag) {
        out = null;
        day = Long.MIN_VALUE;
        raidsToday = 0;
        awayToday = 0;
        if (!tag.contains("raidsAway")) {
            return;
        }
        CompoundTag away = tag.getCompound("raidsAway");
        day = away.getLong("day");
        raidsToday = away.getInt("today");
        awayToday = away.contains("awayToday") ? away.getInt("awayToday") : raidsToday; // (older saves counted only these)
        if (away.contains("out")) {
            CompoundTag raid = away.getCompound("out");
            out = new Out(BlockPos.of(raid.getLong("at")), raid.getInt("raiders"), raid.getLong("attackAt"), raid.getLong("backAt"),
                    raid.hasUUID("termites") ? raid.getUUID("termites") : null);
            out.attacked = raid.getBoolean("attacked");
            out.caught = raid.getInt("caught");
            out.lost = raid.getInt("lost");
            out.hurt = raid.getInt("hurt");
        }
    }

    void fillForTests(ServerLevel level) {
        day = 3;
        raidsToday = 3;
        awayToday = 2;
        out = new Out(colony.getCenter().offset(30, 0, -12), 140, 5000, 9000, new UUID(5, 9));
        out.attacked = true;
        out.caught = 180;
        out.lost = 2;
        out.hurt = 4;
    }
}
