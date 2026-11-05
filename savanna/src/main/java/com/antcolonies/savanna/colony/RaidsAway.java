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
import com.antcolonies.savanna.SavannaSpecies;
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
 * off the spot) counts an empty check; a spot a player is near is left for real ants. No raid leaves unless its attack
 * starts before dusk.
 *
 * <p>Out of sight the scouts are numbers too: in the scouting hours a hungry colony that knows no find worth a raid has
 * its scouts (as many as it would send) each find, by chance, one of the termites' feeding places within its reach that
 * it does not know yet (the termite colonies' own record of their ways out to food), scored as a scout would, passed
 * over if too poor. Every raid and find writes a {@code [raid]} diary line and counts in the away report.
 *
 * <p>Never reads or changes a block of land not loaded. A raid out when the colony comes back finishes as numbers on
 * its own time (its raiders walk in out of sight); a real column out when the colony goes away goes on as numbers.
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
    /** The day the raids are counted for ({@code timeOfDay / 24000}) and how many left on it. Saved. */
    private long day = Long.MIN_VALUE;
    private int raidsToday;
    /** When the number scouts next search (game time). Not saved. */
    private long nextScoutAt;

    /** A raid out of sight: where to, how many, when it attacks and is home, the termite colony, and what it took. */
    static final class Out {
        final BlockPos at;
        final int raiders;
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

    /** Whether a raid is out of sight now. */
    public boolean isOut() {
        return out != null;
    }

    /** Raids set out out of sight today (tests). */
    public int raidsToday() {
        return raidsToday;
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
    }

    /** Once a second while the colony is here: a raid still out of sight finishes as numbers on its own time. */
    void tickHere(ServerLevel level) {
        if (out != null) {
            step(level, level.getGameTime());
        }
    }

    private void newDay(ServerLevel level) {
        long today = Math.floorDiv(colony.timeOfDay(level), 24000L);
        if (today != day) {
            day = today;
            raidsToday = 0;
        }
    }

    // --- setting out --------------------------------------------------------------------------------------------------

    private void maySetOut(ServerLevel level, long now) {
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        SavannaNumbers.ScoutNumbers scouting = raids.numbers();
        SavannaNumbers.ColumnNumbers column = raids.columnNumbers();
        if (scouting == null || column == null || raids.column() != null || !raids.isRaidTime(level) || !raids.isHungry()
                || raidsToday >= numbers.mostRaidsADay() || colony.population().inside(Caste.WORKER) <= 0) {
            return;
        }
        for (Raids.Find find : raids.finds()) {
            Colony termites = termitesAt(level, find.at());
            if (termites == null || termites.keepsOff(level, find.at())) {
                raids.diary(level, "out of sight: the scouts find the spot at " + find.at().toShortString() + " empty ("
                        + (termites == null ? "no termite colony there any more" : "the termites keep off it now") + ")");
                raids.foundEmpty(level, find);
                return; // (one check a second)
            }
            if (playerNear(level, find.at(), numbers.playerNear()) || find.worth() < raids.need(find.at())) {
                continue; // (a player there: real ants only; or too poor for the colony now)
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

    private void setOut(ServerLevel level, long now, Raids.Find find, Colony termites, double distance, long outTicks) {
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        SavannaNumbers.ColumnNumbers column = raids.columnNumbers();
        SavannaNumbers.CarryNumbers carry = raids.carryNumbers();
        int size = Math.max(1, Math.min(raids.raidSize(find), colony.population().inside(Caste.WORKER)));
        long attackAt = now + outTicks;
        out = new Out(find.at().immutable(), size, attackAt, attackAt + numbers.fightTicks() + homeTicks(distance, column, carry),
                termites.getId());
        raidsToday++;
        raids.forgetWaiting(find);
        raids.walkTrail(find.at(), now);
        colony.away().note(level, AwayReport.What.RAIDS, 1);
        colony.away().note(level, AwayReport.What.RAIDERS, size);
        raids.diary(level, "out of sight: a raid of " + size + " sets out to " + find.at().toShortString() + " (worth " + find.worth()
                + ", " + Math.round(distance) + " blocks), raid " + raidsToday + " today; the attack in " + outTicks
                + " ticks, home in " + (out.backAt - now));
        ColonyManager.get(level).markChanged();
    }

    /** Regrouping at the spot and the walk home laden, in ticks. */
    private long homeTicks(double distance, @Nullable SavannaNumbers.ColumnNumbers column, @Nullable SavannaNumbers.CarryNumbers carry) {
        double laden = carry == null ? RaidColumn.DEFAULT_CARRY.ladenPace() : carry.ladenPace();
        return (column == null ? 400 : column.regroupTicks())
                + Math.round(distance / Math.max(0.1, numbers().pace() * laden) * 20.0);
    }

    /**
     * A real raid column was out when the colony went away: it goes on as numbers. Not yet at its spot: it attacks
     * after the rest of the march (about half of it, my pick); already attacked: it walks home with what it picked up.
     */
    void fromColumn(ServerLevel level, RaidColumn column) {
        long now = level.getGameTime();
        Raids.Find find = column.find();
        double distance = RaidGround.flat(colony.getCenter(), find.at());
        Colony termites = termitesAt(level, find.at());
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        long home = homeTicks(distance, raids.columnNumbers(), raids.carryNumbers());
        if (column.attackedAt() < 0) {
            long attackAt = now + Math.round(distance / Math.max(0.1, numbers.pace()) * 10.0);
            out = new Out(find.at().immutable(), Math.max(1, column.size()), attackAt, attackAt + numbers.fightTicks() + home,
                    termites == null ? null : termites.getId());
        } else {
            out = new Out(find.at().immutable(), Math.max(1, column.size()), now, now + home, null);
            out.attacked = true;
            out.caught = column.carried(); // (the termites it stung and picked up: taken from the termites as real ones)
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
        if (termites == null || termites.keepsOff(level, out.at)) {
            raids.diary(level, "out of sight: the raid reached " + out.at.toShortString() + " and found nothing to sting");
            if (find != null) {
                raids.foundEmpty(level, find);
            }
        } else {
            int wanted = (int) Math.round(out.raiders * numbers.catchPerRaider() * (0.75 + 0.5 * random.nextDouble()));
            int most = (int) Math.floor(termites.population().inside(Caste.WORKER) * numbers.mostTakeShare());
            int taken = termites.population().loseInside(level, Caste.WORKER, Math.min(wanted, most), AwayReport.What.TAKEN_BY_RAIDERS);
            out.caught += taken;
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
                    + wanted + " the raiders could carry; the termite colony had " + (termites.population().inside(Caste.WORKER) + taken)
                    + " workers inside, at most " + most + " to take)");
        }
        out.lost = count(random, out.raiders, numbers.lostShare());
        out.hurt = count(random, out.raiders - out.lost, numbers.hurtShare());
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

    // --- the scouts as numbers ---------------------------------------------------------------------------------------

    private void scout(ServerLevel level) {
        SavannaNumbers.ScoutNumbers scouting = raids.numbers();
        if (scouting == null || !raids.isScoutingTime(level) || !raids.isHungry() || colony.population().inside(Caste.WORKER) <= 0) {
            return;
        }
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        for (Raids.Find find : raids.finds()) {
            if (find.worth() >= raids.need(find.at()) && !playerNear(level, find.at(), numbers.playerNear())) {
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
            for (BlockPos food : foods) {
                double distance = RaidGround.flat(colony.getCenter(), food);
                if (distance > reach || raids.findAt(food) != null || termites.keepsOff(level, food)
                        || playerNear(level, food, numbers.playerNear())) {
                    continue;
                }
                places.add(food);
                weights.add(1.0 / (distance + 8.0)); // (nearer places are found sooner)
            }
        }
        if (places.isEmpty()) {
            return;
        }
        RandomSource random = colony.random();
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
        Colony best = null;
        for (Colony termites : Raids.termiteColonies(level, at, numbers().termiteReach())) {
            if (SavannaSpecies.MACROTERMES.equals(termites.getSpecies()) && termites.getCount(Caste.WORKER) > 0
                    && (best == null || termites.getCenter().distSqr(at) < best.getCenter().distSqr(at))) {
                best = termites;
            }
        }
        return best;
    }

    /** Whether a player (alive) is within this many blocks of the spot, sideways. */
    private static boolean playerNear(ServerLevel level, BlockPos at, int radius) {
        for (net.minecraft.world.entity.player.Player player : level.players()) {
            if (player.isAlive() && !player.isSpectator()) {
                double dx = player.getX() - (at.getX() + 0.5);
                double dz = player.getZ() - (at.getZ() + 0.5);
                if (dx * dx + dz * dz <= (double) radius * radius) {
                    return true;
                }
            }
        }
        return false;
    }

    /** For the colony list: "; a raid of 120 out of sight to (x, y, z), home in 1200 ticks; 2 raid(s) today out of sight". */
    String describe(ServerLevel level) {
        StringBuilder line = new StringBuilder();
        if (out != null) {
            line.append("; a raid of ").append(out.raiders).append(" out of sight to ").append(out.at.toShortString())
                    .append(out.attacked ? ", home in " + Math.max(0, out.backAt - level.getGameTime()) + " ticks with " + out.caught + " termite(s)"
                            : ", attacking in " + Math.max(0, out.attackAt - level.getGameTime()) + " ticks");
        }
        if (raidsToday > 0) {
            line.append("; ").append(raidsToday).append(" raid(s) out of sight today");
        }
        return line.toString();
    }

    // --- saving -------------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag away = new CompoundTag();
        away.putLong("day", day);
        away.putInt("today", raidsToday);
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
        if (!tag.contains("raidsAway")) {
            return;
        }
        CompoundTag away = tag.getCompound("raidsAway");
        day = away.getLong("day");
        raidsToday = away.getInt("today");
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
        raidsToday = 2;
        out = new Out(colony.getCenter().offset(30, 0, -12), 140, 5000, 9000, new UUID(5, 9));
        out.attacked = true;
        out.caught = 180;
        out.lost = 2;
        out.hurt = 4;
    }
}
