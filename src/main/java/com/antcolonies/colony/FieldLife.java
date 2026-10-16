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

import com.antcolonies.entity.WorkerAntEntity;
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
    static final int TRIP_TICKS = 200;
    /** How long (ticks) a forager takes for each block of the way, there or back (about the real foragers' pace with the turns; my pick). */
    static final int TICKS_PER_BLOCK = 8;
    /** How long (ticks) a forager cuts at a plant before the piece comes off (as {@code ClipGrassGoal}). */
    private static final int CUT_TICKS = 50;
    /** How long (ticks) a plant only one forager cuts at rests after a cut (as {@link FoodStore}'s real foragers). */
    private static final int REST_TICKS = 200;
    /** How often (ticks) the brain looks at the plants it can see. */
    private static final int LOOK_TICKS = 100;
    /** At most this many plants are worked or shown a second (a big colony far from its food does not cost too much). */
    private static final int PLANTS_PER_SECOND = 200;

    /** A plant the brain remembers: its kind (its place in the profile's {@code food.plants}), the walk, whether it can be eaten from inside. */
    record Plant(int kind, float trip, boolean fromInside) {
    }

    private final Colony colony;
    /** The food plants the brain remembers. Saved. */
    private final Map<BlockPos, Plant> known = new HashMap<>();
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
        }
        boolean changed = showCuts(level);
        changed |= forage(level);
        if (changed) {
            ColonyManager.get(level).markChanged();
        }
        return changed;
    }

    // --- what the brain knows -------------------------------------------------------------------------------------

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
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : food.plants(level)) {
            SpeciesProfile.PlantFood plant = numbers.plantFor(level.getBlockState(pos));
            // (searching alone the brain remembers every plant as its picture of the land: what is there, not what the
            // colony knows; its numbers still only go for plants they find by searching, see forageAlone)
            if (plant == null || !colony.foodMap().knows(pos, null) && !colony.foodMap().searchesAlone()) {
                continue;
            }
            BlockPos at = pos.immutable();
            seen.add(at);
            boolean fromInside = schedule && plant.rich() && food.isFeedableFromInside(level, at);
            known.put(at, new Plant(numbers.plants().indexOf(plant), (float) walk(at, exits), fromInside));
        }
        known.keySet().removeIf(pos -> !seen.contains(pos) && level.isLoaded(pos) && numbers.plantFor(level.getBlockState(pos)) == null);
        progress.keySet().retainAll(known.keySet());
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

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // --- foraging -------------------------------------------------------------------------------------------------

    /**
     * How many of the worker numbers are foragers: the old ones, at least a third of those that are not nurses (the
     * colony moves the next oldest over when it is short, as with the real workers, see {@link Colony#shareOut}).
     */
    int foragersInside(ServerLevel level) {
        int workers = colony.population().inside(WorkerAntEntity.CASTE);
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
        colony.door().numbersSecond(level, foragers, returnedNow, placedNow);
        return changed;
    }

    /** Pieces the numbers brought home this second, and how many of them were at work on a plant (for the door). */
    private int returnedNow;
    private double placedNow;

    private boolean forageWith(ServerLevel level, int foragers) {
        out = 0;
        boolean alone = colony.foodMap().searchesAlone();
        if (alone) {
            colony.spots().trimHeld(foragers); // (fewer forager numbers than spots held: some died or came out as real ones)
        }
        if (foragers <= 0 || known.isEmpty()) {
            progress.clear();
            return false;
        }
        boolean outside = colony.isForagingTime(level);
        if (!outside && (alone || !colony.keepsASchedule() || colony.alarm().keepsEveryoneIn(level))) {
            progress.clear();
            return false; // the alarm keeps everyone in (searching alone: nothing to eat from inside)
        }
        roomNow = colony.food().roomKnown();
        if (roomNow <= 0) {
            return false;
        }
        changedNow = false;
        if (alone) {
            forageAlone(level, foragers);
            return changedNow;
        }
        FoodStore food = colony.food();
        List<SpeciesProfile.PlantFood> kinds = colony.profile().food().plants();
        List<Map.Entry<BlockPos, Plant>> plants = new ArrayList<>();
        Map<BlockPos, Double> worth = new HashMap<>();
        for (Map.Entry<BlockPos, Plant> entry : known.entrySet()) {
            Plant plant = entry.getValue();
            if (plant.kind() < 0 || plant.kind() >= kinds.size() || !outside && !plant.fromInside()
                    || !colony.foodMap().knows(entry.getKey(), null) || !colony.patrol().isOpen(level, entry.getKey())) {
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
        double free = colony.door().numbersFree(level, foragers);
        int worked = 0;
        List<BlockPos> usedUp = new ArrayList<>();
        for (Map.Entry<BlockPos, Plant> entry : plants) {
            if (free <= 0 || roomNow <= 0 || worked++ >= PLANTS_PER_SECOND) {
                break;
            }
            free -= work(level, entry.getKey(), entry.getValue(), free, eyes, usedUp);
        }
        forgetUsedUp(level, usedUp);
        return changedNow;
    }

    /** Room left in the food store this second (as last seen), and whether anything changed this second. */
    private int roomNow;
    private boolean changedNow;

    /**
     * Up to {@code free} forager numbers work this plant this second (no more than may cut at it at once, less the real
     * foragers there; a plant a player can see is left to the real ones). Pieces they bring home are counted into the
     * store. Returns how many were sent.
     */
    private double work(ServerLevel level, BlockPos pos, Plant plant, double free, List<Vec3> eyes, List<BlockPos> usedUp) {
        FoodStore food = colony.food();
        SpeciesProfile.PlantFood kind = colony.profile().food().plants().get(plant.kind());
        int places = Math.max(1, kind.foragers()) - food.foragersAt(pos);
        if (places <= 0) {
            return 0;
        }
        if (level.isLoaded(pos) && !Population.outOfSight(level, pos, eyes)) {
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
        double got = progress.getOrDefault(pos, 0.0) + sent * 20.0 / trip;
        int left = kind.pieces() - food.getPiecesCut(pos);
        long now = level.getGameTime();
        while (got >= 1 && left > 0 && roomNow > 0) {
            got--;
            roomNow--;
            left = kind.pieces() - food.cutByNumbers(level, pos, kind);
            food.countIn(false);
            colony.ledger().addStore(1);
            returnedNow++;
            colony.trunkTrails().tripByNumbers(level, pos); // their trip out and back wears the trail too
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

    private void forgetUsedUp(ServerLevel level, List<BlockPos> usedUp) {
        for (BlockPos pos : usedUp) {
            known.remove(pos);
            progress.remove(pos);
            colony.foodMap().forget(pos);
            colony.away().note(level, AwayReport.What.PLANTS_USED_UP, 1);
        }
    }

    // --- searching alone (brain step 4c) ----------------------------------------------------------------------------

    /** What a search of the numbers costs besides the walk out and back (ticks): out of the nest, the search itself, in again (my pick). */
    static final int SEARCH_TICKS = 400;
    /** At most this many searches of the numbers end a second. */
    private static final int SEARCHES_PER_SECOND = 8;
    /** The remembered plants by 8 x 8 column cells, for the searches (built again each second while searching alone). Not saved. */
    private final Map<Long, List<BlockPos>> byCell = new HashMap<>();
    /** A search on its way to its end (a fraction carried over to the next second). Not saved. */
    private double searchProgress;

    /**
     * The forager numbers of a species that searches alone ({@code food.search}, see {@link ForagerSpots}): those that
     * hold a spot work the remembered plants near it, by the real foragers' trip rules; a spot with nothing left near it
     * is found empty. The rest search the land as last seen (it is their walk, not the colony's knowledge): a search
     * ends after the walk out to its centre and {@link #SEARCH_TICKS}; a plant with food within {@code wander} of the
     * centre makes a new spot for one of them, nothing there and the next searches start farther out (the drift). While
     * searching they are out and bring nothing home (they come home with food, not empty: the user's decision).
     */
    private void forageAlone(ServerLevel level, int foragers) {
        SpeciesProfile.SearchNumbers search = colony.spots().settings().orElse(null);
        if (search == null) {
            return;
        }
        indexPlants();
        List<SpeciesProfile.PlantFood> kinds = colony.profile().food().plants();
        FoodStore food = colony.food();
        List<Vec3> eyes = colony.population().viewersInSight(level);
        double free = colony.door().numbersFree(level, foragers);
        int worked = 0;
        int wander = search.wander();
        List<BlockPos> usedUp = new ArrayList<>();
        for (ForagerSpots.Held spot : new ArrayList<>(colony.spots().held())) {
            if (free <= 0 || roomNow <= 0) {
                break;
            }
            if (!colony.patrol().isOpen(level, spot.pos())) {
                continue; // that way is shut today: its holders stay in
            }
            List<BlockPos> near = new ArrayList<>();
            for (BlockPos pos : plantsNear(spot.pos(), wander)) {
                Plant plant = known.get(pos);
                if (plant != null && plant.kind() >= 0 && plant.kind() < kinds.size() && kinds.get(plant.kind()).pieces() - food.getPiecesCut(pos) > 0) {
                    near.add(pos);
                }
            }
            if (near.isEmpty()) {
                colony.spots().emptyHeld(spot); // nothing left there: its holders search again
                continue;
            }
            near.sort(Comparator.comparingDouble(pos -> -FoodStore.worth(kinds.get(known.get(pos).kind()).pieces() - food.getPiecesCut(pos),
                    known.get(pos).trip(), pos.distSqr(spot.pos()))));
            double spotFree = Math.min(free, spot.holders());
            for (BlockPos pos : near) {
                if (spotFree <= 0 || roomNow <= 0 || worked++ >= PLANTS_PER_SECOND) {
                    break;
                }
                double sent = work(level, pos, known.get(pos), spotFree, eyes, usedUp);
                spotFree -= sent;
                free -= sent;
            }
        }
        forgetUsedUp(level, usedUp);
        int searchers = (int) Math.floor(Math.min(free, Math.max(0, foragers - colony.spots().holders())));
        if (searchers <= 0) {
            searchProgress = 0;
            return;
        }
        placedNow += searchers; // (out searching: they come home with food, not empty)
        out += searchers;
        int range = colony.foragingRadius();
        double distance = Math.max(colony.spots().numbersStartOut(), range / 3.0);
        searchProgress += searchers * 20.0 / (SEARCH_TICKS + 2.0 * distance * TICKS_PER_BLOCK);
        for (int searches = 0; searchProgress >= 1 && searches < SEARCHES_PER_SECOND; searches++) {
            searchProgress--;
            searchOnce(level, search, range);
        }
        searchProgress = Math.min(searchProgress, 1.0);
    }

    /**
     * One search of the numbers ends (see {@link #forageAlone}): out along a trunk trail by strength (one search in 5 a
     * new way, as the real ones); the remembered plant with food nearest its centre within {@code wander} becomes a
     * spot. How far out: anywhere from near the hill (or the trail's end) out to {@code numbersStartOut}, the band the
     * numbers' failed searches have pushed out to. They are many ants searching each their own way, so the band grows a
     * block with each search that finds nothing and shrinks {@code drift_blocks} with each find (my picks): in rich land
     * it stays near home, where food runs out it stretches to the range plus the stretch, and near food is still found.
     */
    private void searchOnce(ServerLevel level, SpeciesProfile.SearchNumbers search, int range) {
        BlockPos middle = colony.getCenter();
        var random = level.getRandom();
        TrunkTrails trails = colony.trunkTrails();
        TrunkTrails.Trail trail = trails.keepsTrails() && random.nextInt(5) != 0 ? trails.pickForSearch(level, random) : null;
        double angle;
        double near;
        double far;
        if (trail != null) {
            angle = trail.angle() + (random.nextDouble() - 0.5) * 0.5;
            near = Math.max(6, trails.reachBlocks(trail) - 3);
            far = Math.max(near, trails.reachBlocks(trail));
        } else {
            angle = random.nextDouble() * Math.PI * 2.0;
            near = 6;
            far = Math.max(near, range / 2.0); // the inner half of the range
        }
        int most = range + search.stretch();
        far = Math.min(Math.max(far, colony.spots().numbersStartOut()), most);
        near = Math.min(near, far);
        double distance = near + random.nextDouble() * (far - near);
        BlockPos centre = new BlockPos(middle.getX() + (int) Math.floor(Math.cos(angle) * distance), middle.getY(),
                middle.getZ() + (int) Math.floor(Math.sin(angle) * distance));
        List<SpeciesProfile.PlantFood> kinds = colony.profile().food().plants();
        FoodStore food = colony.food();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : plantsNear(centre, search.wander())) {
            Plant plant = known.get(pos);
            if (plant == null || plant.kind() < 0 || plant.kind() >= kinds.size() || kinds.get(plant.kind()).pieces() - food.getPiecesCut(pos) <= 0
                    || !colony.patrol().isOpen(level, pos) || !colony.foodMap().mayScout(pos)) {
                continue;
            }
            double away = flat(pos, centre);
            if (away < bestDistance) {
                best = pos;
                bestDistance = away;
            }
        }
        int band = Math.max(colony.spots().numbersStartOut(), range / 2);
        if (best != null) {
            colony.spots().addHeld(best, 1, 0);
            colony.spots().setNumbersStartOut(band - search.driftBlocks()); // a find: the band settles back
        } else {
            colony.spots().setNumbersStartOut(Math.min(band + 1, most)); // nothing: the band reaches a little farther
        }

    }

    /** The remembered plants within {@code reach} blocks (sideways) of {@code at}. */
    private List<BlockPos> plantsNear(BlockPos at, int reach) {
        List<BlockPos> near = new ArrayList<>();
        for (int cx = (at.getX() - reach) >> 3; cx <= (at.getX() + reach) >> 3; cx++) {
            for (int cz = (at.getZ() - reach) >> 3; cz <= (at.getZ() + reach) >> 3; cz++) {
                List<BlockPos> cell = byCell.get(cellKey(cx, cz));
                if (cell == null) {
                    continue;
                }
                for (BlockPos pos : cell) {
                    if (Math.abs(pos.getX() - at.getX()) <= reach && Math.abs(pos.getZ() - at.getZ()) <= reach) {
                        near.add(pos);
                    }
                }
            }
        }
        return near;
    }

    private void indexPlants() {
        byCell.clear();
        for (BlockPos pos : known.keySet()) {
            byCell.computeIfAbsent(cellKey(pos.getX() >> 3, pos.getZ() >> 3), key -> new ArrayList<>()).add(pos);
        }
    }

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
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
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (eyes == null) {
                eyes = colony.population().viewersInSight(level);
            }
            if (!Population.outOfSight(level, pos, eyes)) {
                continue;
            }
            colony.food().showCuts(level, pos, entry.getValue());
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

    void save(CompoundTag tag) {
        CompoundTag field = new CompoundTag();
        ListTag plants = new ListTag();
        known.forEach((pos, plant) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("kind", plant.kind());
            entry.putFloat("trip", plant.trip());
            entry.putBoolean("inside", plant.fromInside());
            plants.add(entry);
        });
        field.put("plants", plants);
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
        owed.clear();
        CompoundTag field = tag.getCompound("field");
        ListTag plants = field.getList("plants", Tag.TAG_COMPOUND);
        for (int i = 0; i < plants.size(); i++) {
            CompoundTag entry = plants.getCompound(i);
            known.put(BlockPos.of(entry.getLong("pos")), new Plant(entry.getInt("kind"), entry.getFloat("trip"), entry.getBoolean("inside")));
        }
        ListTag cuts = field.getList("owed", Tag.TAG_COMPOUND);
        for (int i = 0; i < cuts.size(); i++) {
            CompoundTag entry = cuts.getCompound(i);
            owed.put(BlockPos.of(entry.getLong("pos")), entry.getLong("at"));
        }
    }
}
