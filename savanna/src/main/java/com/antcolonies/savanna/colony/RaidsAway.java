package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The Matabele colony's raiding while nobody is near it (its land not loaded, or loaded but with no player within the
 * far rule's reach, {@code AwayLife}): what is not a raid. Real Megaponera raid 1 to 4 times a day, in the morning and
 * evening raid hours, a hungry colony more (Yusuf 2026; Frank and Linsenmair 2017), so a colony nobody watches must
 * too: without it a far colony starved on an empty prey pile.
 *
 * <p><b>A raid out of sight is the same {@link Raid}</b> as a watched one, stepped without bodies (the raid rewrite,
 * step 4): this class holds no stage, time or catch of its own. It keeps:
 * <ul>
 * <li><b>The raid day</b>, near or away: how many raids left today. In the raid hours, dry, the colony hungry and
 * short of its day's raids, a raid sets out to the best remembered find still worth one ({@link Raids#need}), if the
 * one judge finds termites there now ({@link Raid#termitesThere}; none: no raid, the find forgotten, as near), its
 * size as a watched raid's ({@link Raids#raidSize}), every raider a number. Out of sight the numbers know the way to
 * every find the colony remembers (its scouts are among them).</li>
 * <li><b>Showing</b> ({@link #show}): a raid going as numbers gets bodies once a watcher is near where it is, put
 * where nobody sees them appear; the raid itself places them for its stage ({@link Raid#takeBodies}).</li>
 * <li><b>The scouts as numbers</b>: in the scouting hours a hungry colony that knows no find worth a raid has its
 * scouts (as many as it would send) each find, by chance, one of the termites' feeding places within its reach that
 * it does not know yet (the termite colonies' own record of their ways out to food), scored as a scout would, passed
 * over if too poor; and a scout out as numbers is shown as a real one when a watcher comes near its place.</li>
 * <li><b>Saving</b>: the raid day, and the raid out ({@link Raid#save}), read back at the colony's first tick after
 * a reload.</li>
 * </ul>
 * Never reads or changes a block of land not loaded. Numbers: {@code raids.away}
 * ({@link SavannaNumbers.AwayRaidNumbers}). Every number my pick, to confirm.
 */
public final class RaidsAway {
    /** How often the number scouts search (ticks: once a game hour). */
    private static final long SCOUT_EVERY = 1000;

    private final Raids raids;
    private final Colony colony;
    /**
     * A raid read from the save and not yet made (it needs the world: made at the colony's first tick after the load,
     * {@link #restore}); written back as it is if the colony is saved again before that.
     */
    @Nullable
    private CompoundTag saved;
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

    /** The colony's raid if it goes as numbers now (nobody in it has a body), else null. */
    @Nullable
    private Raid asNumbers() {
        Raid raid = raids.column();
        return saved == null && raid != null && raid.goesAsNumbers() ? raid : null;
    }

    /** Whether the raid going as numbers has attacked (tests). */
    public boolean hasAttacked() {
        Raid raid = asNumbers();
        return raid != null && raid.attackedAt() >= 0;
    }

    /** Whether a raid is out as numbers now (or one read from the save waits to be made). */
    public boolean isOut() {
        return saved != null || asNumbers() != null;
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

    /**
     * Once a second while the colony is away: a raid may set out as numbers, and the number scouts search. (The raid
     * out is stepped by the colony's raids, here or away, {@code Raids.stepRaid}.)
     */
    void tick(ServerLevel level) {
        long now = level.getGameTime();
        restore(level);
        newDay(level);
        if (raids.column() == null) {
            maySetOut(level, now);
        }
        if (raids.column() == null && now >= nextScoutAt) {
            nextScoutAt = now + SCOUT_EVERY;
            scout(level);
        }
        scoutsAsNumbers = true;
    }

    /**
     * Once a second while the colony is here: a raid going as numbers gets bodies once a watcher is near it
     * ({@link #show}).
     */
    void tickHere(ServerLevel level) {
        restore(level);
        Raid raid = asNumbers();
        if (raid != null) {
            show(level, raid);
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

    /** Where the raid going as numbers is on its trail now (a watcher near it watches the colony, {@code Raids.outThere}); null if none is. */
    @Nullable
    BlockPos whereNow(ServerLevel level) {
        Raid raid = asNumbers();
        return raid == null ? null : raid.frontPlace(level.getGameTime());
    }

    /**
     * The raid going as numbers gets bodies once a watcher is within the far rule's reach of where it is now (the user,
     * 2026-11-11: raids that are out when the player comes near should show): so many workers come out of the colony's
     * numbers and the same raid goes on with them from its stage ({@link Raid#takeBodies}). Only where no watcher could
     * see them appear ({@link #SHOW_PAST}, its front and its tail) and the land ticks; else it goes on as numbers and
     * is tried again next second. One mustering or only just out of the nest gets its bodies in the nest's rooms, out
     * of sight, and musters at the entrance as any raid does; one nearly home walks in unseen.
     */
    private void show(ServerLevel level, Raid raid) {
        long now = level.getGameTime();
        BlockPos front = raid.frontPlace(now);
        com.antcolonies.colony.AwayLife watch = colony.away();
        if (!watch.watcherNear(level, front, com.antcolonies.colony.AwayLife.NEAR)) {
            return; // (the colony is watched somewhere else: this raid goes on as numbers)
        }
        boolean homeward = raid.stage() == Raid.Stage.MARCH_HOME;
        boolean byTheNest = raid.stage() == Raid.Stage.MUSTER || raid.shareOut(now) * raid.wayBlocks() < BY_THE_NEST;
        if (byTheNest && homeward) {
            return; // (nearly home: they walk in out of sight)
        }
        BlockPos tail = raid.tailPlace(now);
        if (!byTheNest && (watch.seerNear(level, front, SHOW_PAST) || watch.seerNear(level, tail, SHOW_PAST)
                || !level.isPositionEntityTicking(front) || !level.isPositionEntityTicking(tail))) {
            return; // (they would be seen appearing, or the land there does not tick)
        }
        SavannaNumbers.ColumnNumbers column = raids.columnNumbers();
        int real = Math.min(Math.min(raid.size(), column == null ? 0 : column.mostReal()), colony.population().inside(Caste.WORKER));
        List<BlockPos> cells = !byTheNest ? List.of(front) : colony.nest().getAllRoomCells(level).stream()
                .filter(cell -> RaidGround.canStand(level, cell)).toList();
        List<MatabeleEntity> ants = new ArrayList<>();
        for (int i = 0; i < real && !cells.isEmpty(); i++) {
            if (colony.population().comeOutAt(level, Caste.WORKER, cells.get(colony.random().nextInt(cells.size()))) instanceof MatabeleEntity ant) {
                ants.add(ant);
            }
        }
        if (ants.isEmpty()) {
            return; // (no room cell, nobody inside to come out: it goes on as numbers)
        }
        // (the marching order: a major leads as the scout would, the other majors at the back, the rear guard)
        MatabeleEntity leader = ants.stream().filter(MatabeleEntity::hasMajorBody).findFirst().orElse(ants.get(0));
        ants.sort(Comparator.comparingInt(ant -> ant == leader ? 0 : ant.hasMajorBody() ? 2 : 1));
        raid.takeBodies(level, ants, byTheNest, now);
        raids.diary(level, "the raid out of sight to " + raid.find().at().toShortString() + " is watched now: " + ants.size() + " real raider(s) "
                + (byTheNest ? "muster in the nest" : "by " + front.toShortString()));
        ColonyManager.get(level).markChanged();
    }

    /** The raiders out as numbers now (they are not in the nest: the colony keeps so many fewer real ants at home). */
    int outAsNumbers() {
        Raid raid = raids.column();
        return raid == null ? 0 : raid.numbersAlong();
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

    /**
     * In the raid hours, dry, hungry and with room in the day: the best remembered find still worth a raid is asked
     * about (the one judge, {@link Raid#termitesThere}); termites there, a raid sets out as numbers; none, the find is
     * forgotten, as near. One find a second.
     */
    private void maySetOut(ServerLevel level, long now) {
        SavannaNumbers.ColumnNumbers column = raids.columnNumbers();
        // (the one rule of when the colony's own raid may leave, the same near and away: Raids.whyNoOwnRaidNow)
        if (raids.numbers() == null || column == null || raids.whyNoOwnRaidNow(level) != null || colony.population().inside(Caste.WORKER) <= 0) {
            return;
        }
        for (Raids.Find find : raids.finds()) {
            if (!raids.worthARaid(find.at(), find.worth())) {
                continue; // (too poor for the colony now)
            }
            Raid.Termites counted = Raid.termitesThere(level, colony, find.at());
            if (!counted.any()) {
                raids.diary(level, "no raid to " + find.at().toShortString() + ": no termite there now (the find is forgotten)");
                raids.forget(find);
                return;
            }
            int size = Math.max(1, Math.min(raids.raidSize(find), colony.population().inside(Caste.WORKER)));
            Raid raid = new Raid(level, colony, column, raids.carryNumbers(), raids.trailNumbers(), find, raids.trailFor(level, find, now),
                    List.of(), size, counted, now);
            raids.putRaid(raid);
            raidsToday++;
            awayToday++;
            raids.forgetWaiting(find);
            colony.away().note(level, AwayReport.What.RAIDS, 1);
            colony.away().note(level, AwayReport.What.RAIDERS, size);
            raid.sayStart(level);
            raids.diary(level, "out of sight: a raid of " + size + " sets out to " + find.at().toShortString() + " (worth " + find.worth()
                    + "), raid " + raidsToday + " today (near or away)");
            ColonyManager.get(level).markChanged();
            return;
        }
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
        if (!scoutsAsNumbers || isOut() || scouting == null || now < noScoutShownBefore || !raids.scouts().isScoutingTime(level)
                || !raids.isHungry() || colony.population().inside(Caste.WORKER) <= 0) {
            return List.of();
        }
        if (raids.knowsAFindWorthARaid()) {
            return List.of(); // (no scout is out)
        }
        int reach = raids.scouts().range();
        long trip = Math.max(200L, Math.round(2.0 * reach / Math.max(0.1, numbers().pace()) * 20.0));
        BlockPos nest = colony.getCenter();
        List<ScoutAt> places = new ArrayList<>();
        int wanted = raids.scouts().scoutsWanted();
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
            if (!raids.scouts().showScout(level, one.at(), one.heading())) {
                noScoutShownBefore = level.getGameTime() + 2400; // (no major to show: not asked again for two minutes)
                return;
            }
            ColonyManager.get(level).markChanged();
        }
    }

    // --- the scouts as numbers ---------------------------------------------------------------------------------------

    private void scout(ServerLevel level) {
        SavannaNumbers.ScoutNumbers scouting = raids.numbers();
        if (scouting == null || !raids.scouts().isScoutingTime(level) || !raids.isHungry() || colony.population().inside(Caste.WORKER) <= 0) {
            return;
        }
        SavannaNumbers.AwayRaidNumbers numbers = numbers();
        if (raids.knowsAFindWorthARaid()) {
            return; // (the raid hours will take it)
        }
        int reach = raids.scouts().range();
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
        if (places.isEmpty() && RaidCatch.noTermitesOnRecord(level, colony)) {
            // By the land: a place half the scouts' reach out, any way.
            double way = random.nextDouble() * Math.PI * 2;
            double far = Math.max(16, reach / 2.0);
            places.add(BlockPos.containing(colony.getCenter().getX() + Math.cos(way) * far, colony.getCenter().getY(), colony.getCenter().getZ() + Math.sin(way) * far));
            weights.add(1.0);
        }
        if (places.isEmpty()) {
            return;
        }
        for (int scout = 0; scout < raids.scouts().scoutsWanted() && !places.isEmpty(); scout++) {
            if (random.nextDouble() >= numbers.scoutFindChance()) {
                continue;
            }
            int pick = pickWeighted(random, weights);
            BlockPos food = places.remove(pick);
            weights.remove(pick);
            int worth = numbers.scoutWorthLeast() + random.nextInt(Math.max(1, numbers.scoutWorthMost() - numbers.scoutWorthLeast() + 1));
            if (!raids.worthARaid(food, worth)) {
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

    /** For the colony list: "; 2 raid(s) out of sight today" (the raid out says itself, {@link Raid#describe}). */
    String describe(ServerLevel level) {
        return (saved != null ? "; a raid read from the save, not made yet" : "") + (awayToday > 0 ? "; " + awayToday + " raid(s) out of sight today" : "");
    }

    // --- saving -------------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag away = new CompoundTag();
        away.putLong("day", day);
        away.putInt("today", raidsToday);
        away.putInt("awayToday", awayToday);
        Raid raid = raids.column();
        if (saved != null) {
            away.put("raid", saved.copy());
        } else if (raid != null && raid.stage() != Raid.Stage.DONE) {
            CompoundTag one = new CompoundTag();
            raid.save(one);
            away.put("raid", one);
        }
        tag.put("raidsAway", away);
    }

    /** (Saves from before the rewrite's step 4 kept a raid out of sight as "out", times and all: not read, that raid is dropped.) */
    void load(CompoundTag tag) {
        saved = null;
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
        if (away.contains("raid")) {
            saved = away.getCompound("raid").copy();
        }
    }

    /**
     * The raid read from the save is made, at the colony's first tick after the load, here or away (it needs the world:
     * its trail and its march). It goes on from its stage; one that cannot be made (its numbers gone from the species
     * file, what was written no raid) is dropped with a line in the log, never a crash.
     */
    private void restore(ServerLevel level) {
        if (saved == null) {
            return;
        }
        CompoundTag tag = saved;
        saved = null;
        SavannaNumbers.ColumnNumbers column = raids.columnNumbers();
        if (column == null || raids.column() != null) {
            return;
        }
        try {
            long now = level.getGameTime();
            Raids.Find find = raids.findFor(Raid.savedSpot(tag), Raid.savedWorth(tag), now);
            Raid raid = Raid.load(level, colony, column, raids.carryNumbers(), raids.trailNumbers(), find, raids.trailFor(level, find, now), tag);
            if (raid != null) {
                raids.putRaid(raid);
            }
        } catch (RuntimeException e) {
            com.antcolonies.savanna.SavannaMod.LOGGER.error("[raid] colony {}: the raid read from the save could not be made; it is dropped", colony.getId(), e);
        }
    }

    void fillForTests(ServerLevel level) {
        day = 3;
        raidsToday = 3;
        awayToday = 2;
        saved = new CompoundTag();
        saved.putString("stage", Raid.Stage.MARCH_HOME.name());
        saved.putLong("at", colony.getCenter().offset(30, 0, -12).asLong());
        saved.putInt("numbersAlong", 140);
    }
}
