package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The brain, part 2, step 2c: the foragers among the numbers. The user's decision (2026-10-15): like the eating, the
 * numbers forage always, whether the player is near or away, but only from plants no player can see right then. A
 * real colony sends about a quarter of its workers out; only a few of them are real mobs, the rest are numbers here.
 *
 * <p><b>What they know.</b> While its land is loaded the brain remembers every food plant the colony knows (harvester
 * ants: every plant in their area; termites: the ones their workers found and told of, see {@link FoodMap}): where it
 * is, what plant it is, how far the walk is (from the nearest way out to it, and from that way out to the nest's
 * middle), and for a species that keeps a schedule whether it can be eaten from inside (a feeding gallery under it).
 * In unloaded land it stays as last seen; the pieces cut off it are {@link FoodStore}'s count.
 *
 * <p><b>Foraging.</b> Once a second, by the real foragers' rules: only at foraging time (termites by day only food they
 * can eat from inside), never while the alarm keeps everyone in, and only while the food store has room (as last seen,
 * {@link FoodStore#roomKnown}). The forager numbers go to the plants worth the most ({@link FoodStore#worth}), no more
 * at a plant than may cut at it at once (less the real foragers there), each bringing one piece a trip
 * ({@link #TRIP_TICKS} and {@link #TICKS_PER_BLOCK} there and back). A piece is counted into the store at once (its
 * layer is put down later, {@link NestLedger}).
 *
 * <p><b>Out of sight.</b> A plant a player can see is left to the real foragers. The plant's block catches up with its
 * cuts later, once it is loaded and out of every player's sight ({@link #showCuts}): eaten in stages, or gone; grass
 * eaten away grows back counted from when it was eaten.
 *
 * <p>Species-neutral: any colony with food plants in its profile.
 */
public class FieldLife {
    /** What every trip costs besides the walk out there and back (ticks): out of the nest, cutting, in to the store (10 seconds; my pick). */
    public static final int TRIP_TICKS = 200;
    /** How long (ticks) a forager takes for each block of the way, there or back (about the real foragers' pace with the turns; my pick). */
    public static final int TICKS_PER_BLOCK = 8;
    /** How long (ticks) a forager cuts at a plant before the piece comes off (as {@code ClipGrassGoal}). */
    private static final int CUT_TICKS = 50;
    /** How long (ticks) a plant only one forager cuts at rests after a cut (as {@link FoodStore}'s real foragers). */
    private static final int REST_TICKS = 200;
    /** How often (ticks) the brain looks at the plants it can see. */
    private static final int LOOK_TICKS = 100;
    /** At most this many plants are worked or shown a second (a big colony far from its food does not cost too much). */
    public static final int PLANTS_PER_SECOND = 200;

    /** A plant the brain remembers: its kind (its place in the profile's {@code food.plants}), the walk, whether it can be eaten from inside. */
    public record Plant(int kind, float trip, boolean fromInside) {
    }

    private final Colony colony;
    /** The food plants the brain remembers. Saved. */
    private final Map<BlockPos, Plant> known = new HashMap<>();
    /**
     * Termites underground step 4: the plants the brain last saw round the galleries' holes that the colony does not know
     * yet (its picture of the land); a hole opened while away finds those round it ({@link #findRound}). Saved.
     */
    private final Map<BlockPos, Plant> unknown = new HashMap<>();
    /** Plants the numbers cut at whose blocks do not show it yet: plant -> when it was last cut. Saved. */
    private final Map<BlockPos, Long> owed = new LinkedHashMap<>();
    /** A piece on its way from each plant (a fraction of a trip carried over to the next second). Not saved. */
    private final Map<BlockPos, Double> progress = new HashMap<>();
    private long lookedAt = Long.MIN_VALUE;
    /** How many forager numbers were out the last second (for the colony list). Not saved. */
    private int out;
    /** Tests only: whether this runs at all (null: always, except on the game test server). Not saved. */
    @Nullable
    private Boolean onForTests;

    FieldLife(Colony colony) {
        this.colony = colony;
    }

    /** Whether the numbers forage (always in a game; the game tests turn it on where they test it). */
    public boolean isOn(Level level) {
        if (onForTests != null) {
            return onForTests;
        }
        return !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer);
    }

    /**
     * Once a second, here ({@code away} false, from {@link Colony#tick}) or away (from {@link AwayLife#tickAway}): while
     * here the brain looks at the plants and the store; then the plants' blocks catch up out of sight, and the forager
     * numbers forage. True if anything changed.
     */
    boolean tick(ServerLevel level, boolean away) {
        if (!isOn(level)) {
            return false;
        }
        if (!away) {
            look(level);
            if (colony.away().nestLoaded(level)) {
                colony.food().watchRoom(level);
            }
        } else {
            lookAway(level);
        }
        awayNow = away;
        boolean changed = showCuts(level);
        changed |= forageUnseenTime(level);
        changed |= forage(level);
        if (changed) {
            ColonyManager.get(level).markChanged();
        }
        return changed;
    }

    // --- what the brain knows -------------------------------------------------------------------------------------

    /**
     * The colony goes away with its land still loaded ({@link AwayLife#tickAway}): one last look at the plants and the
     * store's room. A colony new land put in far from every player is never "here", so without this look its brain
     * remembered no plant at all and its forager numbers never went out: it ate its comb in three days and starved
     * (found in the soak of 2026-11-11, every far termite colony).
     */
    void lookOnLeaving(ServerLevel level) {
        if (!isOn(level)) {
            return;
        }
        lookedAt = Long.MIN_VALUE;
        look(level);
        if (colony.away().nestLoaded(level)) {
            colony.food().watchRoom(level);
        }
    }

    /** Every few seconds while here: the known plants in loaded land are remembered as they are; gone ones are forgotten. */
    private void look(ServerLevel level) {
        long now = level.getGameTime();
        if (lookedAt != Long.MIN_VALUE && now - lookedAt < LOOK_TICKS && now >= lookedAt) {
            return;
        }
        lookedAt = now;
        FoodStore food = colony.food();
        SpeciesProfile.FoodNumbers numbers = colony.profile().food();
        List<BlockPos> exits = exits(level);
        boolean schedule = colony.keepsASchedule();
        boolean picture = keepsAPicture();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : food.plants(level)) {
            // (the list is up to 5 seconds old: its land may have gone meanwhile, review R1-47)
            SpeciesProfile.PlantFood plant = LandReady.at(level, pos) ? numbers.plantFor(level.getBlockState(pos)) : null;
            if (plant == null) {
                continue;
            }
            BlockPos at = pos.immutable();
            if (!colony.foodMap().knows(pos, null)) {
                if (picture) {
                    seen.add(at);
                    unknown.put(at, new Plant(numbers.plants().indexOf(plant), (float) walk(at, exits), false));
                }
                continue;
            }
            seen.add(at);
            unknown.remove(at);
            boolean fromInside = schedule && plant.rich() && food.isFeedableFromInside(level, at);
            known.put(at, new Plant(numbers.plants().indexOf(plant), (float) walk(at, exits), fromInside));
        }
        known.keySet().removeIf(pos -> !seen.contains(pos) && LandReady.at(level, pos) && numbers.plantFor(level.getBlockState(pos)) == null);
        unknown.keySet().removeIf(pos -> known.containsKey(pos) || !picture
                || !seen.contains(pos) && LandReady.at(level, pos) && numbers.plantFor(level.getBlockState(pos)) == null);
        progress.keySet().retainAll(known.keySet());
    }

    /** Whether the brain keeps a picture of the plants round the galleries that the colony does not know yet (termites underground, step 4). */
    private boolean keepsAPicture() {
        return colony.foodMap().findsByLooking() && colony.underground() != null;
    }

    /**
     * Termites underground step 4, while away: the holes opened tonight find the plants the brain last saw within
     * {@code radius} blocks of them (a forager out of that hole would have come across them). The colony knows them
     * now. Returns how many were found.
     */
    public int findRound(ServerLevel level, java.util.Collection<BlockPos> holes, int radius) {
        if (holes.isEmpty() || unknown.isEmpty()) {
            return 0;
        }
        int found = 0;
        for (Iterator<Map.Entry<BlockPos, Plant>> it = unknown.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Plant> entry = it.next();
            BlockPos pos = entry.getKey();
            boolean near = false;
            for (BlockPos hole : holes) {
                if (flat(pos, hole) <= radius && Math.abs(pos.getY() - hole.getY()) <= radius) {
                    near = true;
                    break;
                }
            }
            if (near) {
                colony.foodMap().learn(pos);
                known.put(pos, entry.getValue());
                it.remove();
                found++;
            }
        }
        if (found > 0) {
            ColonyFrameworkMod.LOGGER.debug("[away] colony {}: tonight's holes found {} plants", colony.getId(), found);
            ColonyManager.get(level).markChanged();
        }
        return found;
    }

    /** The plants the brain remembers that the colony knows (for picking the night's holes while away). */
    public List<BlockPos> knownPlants() {
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : known.keySet()) {
            if (colony.foodMap().knows(pos, null)) {
                list.add(pos);
            }
        }
        return list;
    }

    /** The colony's ways out, open or not: every foraging hole of the plan and the entrance. */
    private List<BlockPos> exits(Level level) {
        List<BlockPos> exits = new ArrayList<>();
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            exits.add(tunnel.hole());
        }
        exits.add(colony.nest().getEntrance());
        return exits;
    }

    /** The walk (blocks, sideways) from the nest's middle to the plant, by the way out that makes it shortest. */
    private double walk(BlockPos plant, List<BlockPos> exits) {
        BlockPos center = colony.getCenter();
        double best = Double.MAX_VALUE;
        for (BlockPos exit : exits) {
            best = Math.min(best, flat(plant, exit) + flat(exit, center));
        }
        return best;
    }

    public static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Whether the colony is away this second (set in {@link #tick}). Not saved. */
    private boolean awayNow;

    /** Pieces the forager numbers are part-way to bringing in from land the brain has no picture of ({@link #forageByTheLand}). Not saved. */
    private double landGot;

    /**
     * The brain while away (2026-11-11; brain step 4f's idea in its smallest form, my pick): a colony away whose forager
     * numbers found no plant to work this second forages by the land. Far from every player the brain's picture is
     * thin: the plants it remembers lie out of reach of any hole (holes are laid out along tunnels real workers dig, so
     * a colony nobody has been near has none), are eaten up and never seen to grow back, or were never seen at all. So
     * every far colony brought in nothing, ate its comb in three days and starved, queen and king too (the soaks of
     * 2026-11-11; the 100-day soak of 2026-11-06 shows the same). Now each free forager brings a piece a trip of the
     * graze's length, into the room the store has, with no block changed: the land out of sight is taken to feed the
     * colony that lives on it. A known departure: this food is not cut from real plants.
     */
    private void forageByTheLand(ServerLevel level, double free) {
        ColonyHooks.Underground web = colony.underground();
        int walk = web != null ? web.grazeRadius() : colony.profile().food().radius();
        landGot += free * 20.0 * seconds / (TRIP_TICKS + 2.0 * walk * TICKS_PER_BLOCK);
        placedNow += free;
        out += (int) Math.round(free);
        while (landGot >= 1 && roomNow > 0) {
            landGot--;
            roomNow--;
            colony.food().countIn(false);
            colony.ledger().addStore(1);
            returnedNow++;
            colony.away().note(level, AwayReport.What.FORAGED, 1);
            changedNow = true;
        }
        landGot = Math.min(landGot, 1.0);
    }

    /** How often (ticks) an away colony looks for known plants it does not remember yet ({@link #lookAway}). */
    private static final int LOOK_AWAY_TICKS = 600;

    /**
     * While away, now and then: the plants the colony knows that the brain does not remember yet (taught after its last
     * look, or in land that was not loaded then) are remembered once their land is loaded, read where they stand. The
     * last look on leaving alone missed them for most colonies of new land (their land comes in piece by piece), and
     * forager numbers with nothing remembered never go out (the soak of 2026-11-11). Reads loaded land only.
     */
    private void lookAway(ServerLevel level) {
        long now = level.getGameTime();
        if (lookedAt != Long.MIN_VALUE && now - lookedAt < LOOK_AWAY_TICKS && now >= lookedAt) {
            return;
        }
        lookedAt = now;
        SpeciesProfile.FoodNumbers numbers = colony.profile().food();
        List<BlockPos> exits = null;
        if (known.size() >= colony.foodMap().knownCount()) {
            return;
        }
        for (BlockPos pos : colony.foodMap().knownPlants()) {
            if (known.containsKey(pos) || !LandReady.at(level, pos)) {
                continue;
            }
            SpeciesProfile.PlantFood plant = numbers.plantFor(level.getBlockState(pos));
            if (plant == null) {
                continue;
            }
            if (exits == null) {
                exits = exits(level);
            }
            known.put(pos.immutable(), new Plant(numbers.plants().indexOf(plant), (float) walk(pos, exits), false));
        }
    }

    // --- foraging -------------------------------------------------------------------------------------------------

    /**
     * How many of the worker numbers are foragers: the old ones, at least a third of those that are not nurses (the
     * colony moves the next oldest over when it is short, as with the real workers, see {@link Colony#shareOut}).
     */
    public int foragersInside(ServerLevel level) {
        int workers = colony.population().inside(Caste.WORKER);
        if (workers <= 0) {
            return 0;
        }
        int[] ages = colony.population().insideByAge(level.getGameTime());
        return Colony.shareOut(workers, colony.profile().colony().nurses(workers), ages)[ColonyRole.FORAGER.ordinal()];
    }

    /**
     * The forager numbers' trips this second (see the class comment). How many may be out is the door's (Gordon's rule:
     * the homecomings with food bring more out, see {@link Door}), and only to plants the patrollers opened ({@link Patrol}).
     */
    private boolean forage(ServerLevel level) {
        int foragers = foragersInside(level);
        returnedNow = 0;
        placedNow = 0;
        boolean changed = forageWith(level, foragers);
        return changed;
    }

    // --- a night the day clock jumped over (a player slept) ----------------------------------------------------------

    /** The day clock and the game clock at the last tick, to see the day clock jump ({@link #nightJumpedOver}). Not saved. */
    private long lastDayTime = Long.MIN_VALUE;
    private long lastGameTime = Long.MIN_VALUE;
    /** How many seconds this pass of {@link #forageWith} stands for (1, or a whole jumped night's), and whether it counts as night. */
    private double seconds = 1.0;
    private boolean jumpedNight;

    /** Tests only: whether the day clock counts as standing still (null: as it is; never under a test's own clock). */
    @Nullable
    private Boolean clockStandsForTests;
    /** The share of a day that is foraging time, while the day clock stands still (below 0: not worked out). Not saved. */
    private double nightShare = -1;
    /** When the holes were last picked for a foraging time nobody saw, while the day clock stands still. Not saved. */
    private long unseenRoundAt = Long.MIN_VALUE;

    /**
     * The foraging time the day clock took from the colony since the last tick, foraged by the numbers (review R5-52:
     * the species forages by the day clock and gets hungry by game time, which never jumps or stops). One rule for
     * every way the two clocks part:
     * <ul>
     * <li>the day clock jumped, forward or back (a bed, {@code /time set day} in the evening, {@code /time add}): the
     * time of day went from the old hour to the new one, and the foraging time between the two, going forward (less
     * than a day), is foraged in one pass ({@link #clockJump});
     * <li>the day clock stands still outside foraging time (the daylight cycle switched off at noon): every second
     * the numbers forage a night's share of it, so a world of endless day does not starve the colony.
     * </ul>
     * Both are nights foraged unseen (my pick): the termites did forage; the player did not see it. Nothing for a
     * species that keeps no schedule.
     */
    private boolean forageUnseenTime(ServerLevel level) {
        long day = level.getDayTime();
        long game = level.getGameTime();
        long wasDay = lastDayTime;
        long wasGame = lastGameTime;
        lastDayTime = day;
        lastGameTime = game;
        if (wasGame == Long.MIN_VALUE || game <= wasGame || !colony.keepsASchedule()) {
            return false;
        }
        long[] jump = clockJump(wasDay, wasGame, day, game);
        if (jump != null) {
            long seconds = colony.foragingTicksBetween(level, jump[0], jump[1]) / 20;
            if (seconds <= 0) {
                return false;
            }
            if (colony.foragingTicksBetween(level, jump[0] - 100, jump[0]) == 0) {
                pickUnseenHoles(level); // (that foraging time had not begun: no holes were picked for it)
            }
            return forageSkippedNight(level, seconds);
        }
        boolean stands = clockStandsForTests != null ? clockStandsForTests : day == wasDay && !Colony.hasTestClock();
        if (!stands) {
            nightShare = -1;
            return false;
        }
        if (colony.isForagingTime(level)) {
            return false; // (stopped at night, or it rains: they are out for real)
        }
        if (nightShare < 0) {
            nightShare = colony.foragingTicksBetween(level, 0, 24000) / 24000.0;
        }
        if (unseenRoundAt == Long.MIN_VALUE || game - unseenRoundAt >= 24000) {
            unseenRoundAt = game;
            pickUnseenHoles(level); // (a new night's holes once a day of game time)
        }
        return nightShare > 0 && forageUnseen(level, nightShare * (game - wasGame) / 20.0);
    }

    /**
     * The span of the day clock ({@code from} to {@code to}, on its own scale) that it jumped over between two ticks:
     * from where it would have been by now to the hour it shows instead, going forward (less than a day). Null when it
     * did not jump (less than 10 seconds off). A jump back counts too: {@code /time set day} at night sets the clock to
     * the first day's morning, many days back, and the rest of that night is gone all the same (R5-52).
     */
    public static long[] clockJump(long wasDay, long wasGame, long day, long game) {
        long expected = wasDay + (game - wasGame);
        long jumped = day - expected;
        if (Math.abs(jumped) < 200) {
            return null;
        }
        return new long[] {expected, expected + Math.floorMod(jumped, 24000L)};
    }

    /** A foraging time nobody saw has no holes picked yet: the species' web picks them (the numbers graze only round them). */
    private void pickUnseenHoles(ServerLevel level) {
        ColonyHooks.Underground web = colony.underground();
        if (web != null) {
            web.foragingTimeUnseen(level, awayNow);
        }
    }

    /** Tests only: the day clock counts as standing still (or not) whatever it does; null, as it is. */
    public void setClockStandsForTests(@Nullable Boolean stands) {
        clockStandsForTests = stands;
    }

    /**
     * A night the player slept through is foraged by the numbers (the user's call, 2026-11-25; review R1-35: a bed
     * took the termites' whole foraging time while their hunger ran on, so a player who slept every night starved the
     * grass-fed colonies of the whole world). The termites did forage that night; the player did not see it. One pass
     * standing for the night's seconds, by the rules of any night: the forager numbers, the known plants and the
     * holes in turn, the room in the store, plants in a player's sight left alone. True if anything changed.
     */
    public boolean forageSkippedNight(ServerLevel level, long nightSeconds) {
        return forageUnseen(level, nightSeconds);
    }

    /** One pass of the numbers standing for this many seconds of foraging time nobody saw (see {@link #forageSkippedNight}). */
    private boolean forageUnseen(ServerLevel level, double nightSeconds) {
        seconds = nightSeconds;
        jumpedNight = true;
        try {
            returnedNow = 0;
            placedNow = 0;
            return forageWith(level, foragersInside(level));
        } finally {
            seconds = 1.0;
            jumpedNight = false;
        }
    }

    /** Pieces the numbers brought home this second, and how many of them were at work on a plant (for the door). */
    private int returnedNow;
    private double placedNow;

    private boolean forageWith(ServerLevel level, int foragers) {
        out = 0;
        ColonyHooks.Underground web0 = colony.underground();
        if (foragers <= 0 || known.isEmpty() && !(awayNow && web0 != null)) { // (away with nothing remembered: by the land, below)
            progress.clear();
            return false;
        }
        boolean outside = jumpedNight || colony.isForagingTime(level);
        if (!outside && !colony.keepsASchedule()) {
            progress.clear();
            return false;
        }
        roomNow = colony.food().roomKnown(level.getGameTime());
        if (roomNow <= 0) {
            return false;
        }
        changedNow = false;
        FoodStore food = colony.food();
        List<SpeciesProfile.PlantFood> kinds = colony.profile().food().plants();
        List<Map.Entry<BlockPos, Plant>> plants = new ArrayList<>();
        Map<BlockPos, Double> worth = new HashMap<>();
        // Termites underground step 4 (the user's pick, 2026-10-17): with the galleries the numbers graze only round the
        // holes open tonight, near or away, and eat from inside under the feeding spots.
        ColonyHooks.Underground web = colony.underground();
        List<BlockPos> galleries = web != null ? web.holesInTurn(level) : null;
        for (Map.Entry<BlockPos, Plant> entry : known.entrySet()) {
            Plant plant = entry.getValue();
            if (plant.kind() < 0 || plant.kind() >= kinds.size() || !outside && !plant.fromInside()
                    || !colony.foodMap().knows(entry.getKey(), null) || !colony.mayForageAt(level, entry.getKey())) {
                continue;
            }
            int left = kinds.get(plant.kind()).pieces() - food.getPiecesCut(entry.getKey());
            if (left > 0) {
                plants.add(entry);
                worth.put(entry.getKey(), FoodStore.worth(left, plant.trip(), 0));
            }
        }
        plants.sort(Comparator.comparingDouble((Map.Entry<BlockPos, Plant> entry) -> -worth.get(entry.getKey())));
        List<Vec3> eyes = colony.population().viewersInSight(level);
        double free = foragers;
        int worked = 0;
        List<BlockPos> usedUp = new ArrayList<>();
        for (Map.Entry<BlockPos, Plant> entry : plants) {
            if (free <= 0 || roomNow <= 0 || worked++ >= PLANTS_PER_SECOND) {
                break;
            }
            BlockPos hole = null;
            if (galleries != null && !entry.getValue().fromInside()) {
                hole = grazedFrom(entry.getKey(), galleries);
                if (hole == null) {
                    continue; // no hole open near it tonight
                }
            }
            int before = returnedNow;
            free -= work(level, entry.getKey(), entry.getValue(), free, eyes, usedUp);
            if (hole != null && returnedNow > before) {
                web.noteForaged(hole, level.getGameTime());
            }
        }
        forgetUsedUp(level, usedUp);
        if (awayNow && web != null && outside && placedNow == 0 && free > 0) { // (a species with galleries: the termites)
            forageByTheLand(level, free);
        }
        return changedNow;
    }

    /**
     * Termites underground step 4: the open hole (the night's turns, a runway's hole, a hole by rich food) nearest the
     * plant within the graze ({@code food_radius}) that the colony goes out of, or null: no hole open near it, so the
     * plant waits for a night its hole has its turn (as the real foragers graze only round the holes they opened).
     * <b>The one rule of where a colony with galleries grazes</b> (review R5-61): the numbers ask it here, the real
     * foragers' pick ({@link FoodStore#findGrass}) and the scouts' spot ({@code ScoutGoal}) ask it too; before, only the
     * numbers kept to it, and the real ones were sent 20 or 40 blocks over open ground once the tufts by the open holes
     * were taken. Null with no web (a species without galleries grazes anywhere in its reach).
     */
    @Nullable
    public BlockPos grazedFrom(BlockPos plant, List<BlockPos> holes) {
        ColonyHooks.Underground web = colony.underground();
        int radius = web == null ? 0 : web.grazeRadius();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos hole : holes) {
            double away = flat(plant, hole);
            if (away <= radius + 0.5 && Math.abs(plant.getY() - hole.getY()) <= radius && away < bestDistance) {
                best = hole;
                bestDistance = away;
            }
        }
        return best;
    }

    /** Room left in the food store this second (as last seen), and whether anything changed this second. */
    private int roomNow;
    private boolean changedNow;

    // --- for a species' own way of foraging (ColonyHooks.NumbersSearch: the harvester ant searching alone) -------------

    /** The colony foraging. */
    Colony colony() {
        return colony;
    }

    /** The food plants the brain remembers (only to read). */
    public Map<BlockPos, Plant> known() {
        return known;
    }

    /** Room left in the food store this second, as last seen. */
    public int roomNow() {
        return roomNow;
    }

    /**
     * Up to {@code free} forager numbers work this plant this second (no more than may cut at it at once, less the real
     * foragers there; a plant a player can see is left to the real ones). Pieces they bring home are counted into the
     * store. Returns how many were sent.
     */
    public double work(ServerLevel level, BlockPos pos, Plant plant, double free, List<Vec3> eyes, List<BlockPos> usedUp) {
        FoodStore food = colony.food();
        SpeciesProfile.PlantFood kind = colony.profile().food().plants().get(plant.kind());
        int places = Math.max(1, kind.foragers()) - food.foragersAt(pos);
        if (places <= 0) {
            return 0;
        }
        if (LandReady.at(level, pos) && colony.profile().food().plantFor(level.getBlockState(pos)) == null) {
            return 0; // no plant there any more (the next look forgets it): nothing to cut
        }
        if (LandReady.at(level, pos) && !Population.outOfSight(level, pos, eyes)) {
            progress.remove(pos); // a player sees it: left to the real foragers
            return 0;
        }
        // A forager keeps a place at the plant from setting out until its piece comes off (and a plant one cuts at
        // at a time rests a moment after); the rest of the trip is the walk home. So a place keeps this many busy:
        double trip = TRIP_TICKS + 2.0 * plant.trip() * TICKS_PER_BLOCK;
        double atPlant = plant.trip() * TICKS_PER_BLOCK + CUT_TICKS + (kind.foragers() <= 1 ? REST_TICKS : 0);
        double sent = Math.min(free, places * trip / atPlant);
        placedNow += sent;
        out += (int) Math.round(sent);
        double got = progress.getOrDefault(pos, 0.0) + sent * 20.0 * seconds / trip;
        int left = kind.pieces() - food.getPiecesCut(pos);
        long now = level.getGameTime();
        // Brain step 4g, step 4: a trip to food near danger can be lost out of sight (species whose forager numbers go there).
        double lossChance = 0;
        DangerMap.Place[] lostTo = new DangerMap.Place[1];
        SpeciesProfile.LostNumbers lost = colony.danger().lostNumbers();
        if (got >= 1 && lost != null && lost.foragers() && !colony.danger().isEmpty()) {
            lossChance = colony.danger().lossChance(level, pos, lostTo);
        }
        while (got >= 1 && left > 0 && roomNow > 0) {
            got--;
            if (lossChance > 0 && colony.random().nextDouble() < lossChance && lostTo[0] != null && colony.danger().lose(level, lostTo[0])) {
                changedNow = true;
                continue; // never came back: no piece
            }
            roomNow--;
            left = kind.pieces() - food.cutByNumbers(level, pos, kind);
            food.countIn(false);
            colony.ledger().addStore(1);
            returnedNow++;
            owed.put(pos, now);
            colony.away().note(level, AwayReport.What.FORAGED, 1);
            changedNow = true;
        }
        if (left <= 0) {
            usedUp.add(pos);
        } else {
            progress.put(pos, Math.min(got, 1.0));
        }
        return sent;
    }

    public void forgetUsedUp(ServerLevel level, List<BlockPos> usedUp) {
        for (BlockPos pos : usedUp) {
            known.remove(pos);
            progress.remove(pos);
            colony.foodMap().forget(pos);
            colony.away().note(level, AwayReport.What.PLANTS_USED_UP, 1);
        }
    }

    // --- the plants' blocks catch up --------------------------------------------------------------------------------

    /** The plants the numbers cut at, loaded and out of every player's sight, are made to look as eaten as they are. */
    private boolean showCuts(ServerLevel level) {
        if (owed.isEmpty()) {
            return false;
        }
        List<Vec3> eyes = null;
        boolean changed = false;
        int done = 0;
        for (Iterator<Map.Entry<BlockPos, Long>> it = owed.entrySet().iterator(); it.hasNext() && done < PLANTS_PER_SECOND; ) {
            Map.Entry<BlockPos, Long> entry = it.next();
            BlockPos pos = entry.getKey();
            if (!LandReady.at(level, pos)) {
                continue;
            }
            if (eyes == null) {
                eyes = colony.population().viewersInSight(level);
            }
            if (!Population.outOfSight(level, pos, eyes)) {
                continue;
            }
            colony.food().showCuts(level, pos, entry.getValue());
            if (colony.profile().food().plantFor(level.getBlockState(pos)) == null) {
                // Eaten away just now: forgotten at once. (The brain's look, earlier this second, still saw the plant and
                // remembered it again with its cuts already cleared, so the numbers cut one more piece off thin air: an
                // extra seed, the away test 2c, 2026-10-17.)
                known.remove(pos);
                progress.remove(pos);
            }
            it.remove();
            done++;
            changed = true;
        }
        return changed;
    }

    // --- the colony list and tests ----------------------------------------------------------------------------------

    /** For the colony list: " | foragers out as numbers 120 (remembers 340 plants, 12 to show)", or empty. */
    public String describe() {
        if (known.isEmpty() && owed.isEmpty() && out == 0) {
            return "";
        }
        return " | foragers out as numbers " + out + " (remembers " + known.size() + " plants" + (owed.isEmpty() ? "" : ", " + owed.size() + " to show") + ")";
    }

    public void setOnForTests(@Nullable Boolean on) {
        onForTests = on;
    }

    /** Whether the brain has this plant in its picture of the land round the holes, not yet known to the colony. For tests. */
    public boolean seesUnknown(BlockPos plant) {
        return unknown.containsKey(plant);
    }

    /** Whether the brain remembers a plant here. For tests. */
    public boolean remembers(BlockPos plant) {
        return known.containsKey(plant);
    }

    /** The walk (blocks) the brain remembers to this plant, by the way out that makes it shortest; -1 if it does not remember it. For tests. */
    public float walkTo(BlockPos plant) {
        Plant known = this.known.get(plant);
        return known == null ? -1 : known.trip();
    }

    /** Whether the brain remembers this plant as one that can be eaten from inside the nest (by day too). For tests. */
    public boolean remembersFromInside(BlockPos plant) {
        Plant known = this.known.get(plant);
        return known != null && known.fromInside();
    }

    /** How many plants the numbers have cut at whose blocks do not show it yet. For tests. */
    public int owedCount() {
        return owed.size();
    }

    /** How many forager numbers were out the last second. For tests. */
    public int foragersOut() {
        return out;
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayKit.everyPartSavesAndLoads}). */
    public void fillForTests(ServerLevel level) {
        known.put(new BlockPos(11, 60, 0), new Plant(1, 2.5F, true));
        unknown.put(new BlockPos(0, 60, 12), new Plant(0, 1.5F, false));
        owed.put(new BlockPos(-9, 60, -9), level.getGameTime() + 300);
    }

    void save(CompoundTag tag) {
        CompoundTag field = new CompoundTag();
        pack(field, "plant", known);
        pack(field, "unknown", unknown);
        ListTag cuts = new ListTag();
        owed.forEach((pos, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putLong("at", at);
            cuts.add(entry);
        });
        field.put("owed", cuts);
        tag.put("field", field);
    }

    void load(CompoundTag tag) {
        known.clear();
        unknown.clear();
        owed.clear();
        CompoundTag field = tag.getCompound("field");
        unpack(field, "plant", known);
        unpack(field, "unknown", unknown);
        // Worlds saved before 2026-11-26 kept each plant as a record of its own.
        ListTag plants = field.getList("plants", Tag.TAG_COMPOUND);
        for (int i = 0; i < plants.size(); i++) {
            CompoundTag entry = plants.getCompound(i);
            known.put(BlockPos.of(entry.getLong("pos")), new Plant(entry.getInt("kind"), entry.getFloat("trip"), entry.getBoolean("inside")));
        }
        ListTag pictureList = field.getList("unknown", Tag.TAG_COMPOUND);
        for (int i = 0; i < pictureList.size(); i++) {
            CompoundTag entry = pictureList.getCompound(i);
            unknown.put(BlockPos.of(entry.getLong("pos")), new Plant(entry.getInt("kind"), entry.getFloat("trip"), false));
        }
        ListTag cuts = field.getList("owed", Tag.TAG_COMPOUND);
        for (int i = 0; i < cuts.size(); i++) {
            CompoundTag entry = cuts.getCompound(i);
            owed.put(BlockPos.of(entry.getLong("pos")), entry.getLong("at"));
        }
    }

    /**
     * A map of plants written as four rows of numbers (places, kinds, trips, from inside) instead of a record for each
     * plant (review R1-44: the plants were half of the colonies' file, 45 bytes each; this way 14, and the file is built
     * and written on the server's own thread at every autosave and pause).
     */
    static void pack(CompoundTag field, String name, Map<BlockPos, Plant> plants) {
        long[] places = new long[plants.size()];
        byte[] kinds = new byte[plants.size()];
        int[] trips = new int[plants.size()];
        byte[] inside = new byte[plants.size()];
        int i = 0;
        for (Map.Entry<BlockPos, Plant> entry : plants.entrySet()) {
            places[i] = entry.getKey().asLong();
            kinds[i] = (byte) entry.getValue().kind();
            trips[i] = Float.floatToIntBits(entry.getValue().trip());
            inside[i++] = (byte) (entry.getValue().fromInside() ? 1 : 0);
        }
        field.putLongArray(name + "Places", places);
        field.putByteArray(name + "Kinds", kinds);
        field.putIntArray(name + "Trips", trips);
        field.putByteArray(name + "Inside", inside);
    }

    static void unpack(CompoundTag field, String name, Map<BlockPos, Plant> plants) {
        long[] places = field.getLongArray(name + "Places");
        byte[] kinds = field.getByteArray(name + "Kinds");
        int[] trips = field.getIntArray(name + "Trips");
        byte[] inside = field.getByteArray(name + "Inside");
        if (kinds.length != places.length || trips.length != places.length || inside.length != places.length) {
            return; // a broken record: these plants are found again when the land is looked at
        }
        for (int i = 0; i < places.length; i++) {
            plants.put(BlockPos.of(places[i]), new Plant(kinds[i], Float.intBitsToFloat(trips[i]), inside[i] != 0));
        }
    }
}
