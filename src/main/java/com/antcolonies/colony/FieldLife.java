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

import com.antcolonies.AntColoniesMod;
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
        boolean picture = keepsAPicture();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : food.plants(level)) {
            SpeciesProfile.PlantFood plant = numbers.plantFor(level.getBlockState(pos));
            if (plant == null) {
                continue;
            }
            BlockPos at = pos.immutable();
            // (searching alone the brain remembers every plant as its picture of the land: what is there, not what the
            // colony knows; its numbers still only go for plants they find by searching, see forageAlone)
            if (!colony.foodMap().knows(pos, null) && !colony.foodMap().searchesAlone()) {
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
        known.keySet().removeIf(pos -> !seen.contains(pos) && level.isLoaded(pos) && numbers.plantFor(level.getBlockState(pos)) == null);
        unknown.keySet().removeIf(pos -> known.containsKey(pos) || !picture
                || !seen.contains(pos) && level.isLoaded(pos) && numbers.plantFor(level.getBlockState(pos)) == null);
        progress.keySet().retainAll(known.keySet());
    }

    /** Whether the brain keeps a picture of the plants round the galleries that the colony does not know yet (termites underground, step 4). */
    private boolean keepsAPicture() {
        return colony.foodMap().findsByLooking() && !colony.foodMap().searchesAlone() && colony.underground() != null;
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
            AntColoniesMod.LOGGER.debug("[away] colony {}: tonight's holes found {} plants", colony.getId(), found);
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
        colony.news().tell(level, ColonyNews.NUMBERS_FORAGED, new ColonyNews.NumbersForaged(foragers, returnedNow, placedNow)); // (the door, Gordon's rule)
        return changed;
    }

    /** How many of the {@code foragers} may be out now: all of them, unless a part's rule holds some in (the harvester's door, Gordon's rule). */
    public double foragersFree(ServerLevel level, int foragers) {
        ColonyHooks.DoorRule door = colony.hooks().first(ColonyHooks.DOOR_RULE);
        return door == null ? foragers : door.free(level, foragers);
    }

    /** Pieces the numbers brought home this second, and how many of them were at work on a plant (for the door). */
    private int returnedNow;
    private double placedNow;

    private boolean forageWith(ServerLevel level, int foragers) {
        out = 0;
        boolean alone = colony.foodMap().searchesAlone();
        if (alone) {
            colony.news().tell(level, ColonyNews.FORAGER_NUMBERS, new ColonyNews.ForagerNumbers(foragers)); // (fewer forager numbers than spots held: some died or came out as real ones)
        }
        if (foragers <= 0 || known.isEmpty()) {
            progress.clear();
            return false;
        }
        boolean outside = colony.isForagingTime(level);
        if (!outside && (alone || !colony.keepsASchedule() || colony.keepsEveryoneIn(level))) {
            progress.clear();
            return false; // the alarm keeps everyone in (searching alone: nothing to eat from inside)
        }
        roomNow = colony.food().roomKnown();
        if (roomNow <= 0) {
            return false;
        }
        changedNow = false;
        if (alone) {
            // A species that searches alone (brain step 4c): its own way, plugged in by its part (the harvester's forager spots).
            ColonyHooks.NumbersSearch search = colony.hooks().first(ColonyHooks.NUMBERS_SEARCH);
            if (search != null) {
                search.forage(level, this, foragers);
            }
            return changedNow;
        }
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
        double free = foragersFree(level, foragers);
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
        return changedNow;
    }

    /**
     * Termites underground step 4: the open hole (the night's turns, a runway's hole, a hole by rich food) nearest the
     * plant within the graze ({@code food_radius}) that the numbers go out of, or null: no hole open near it, so the
     * plant waits for a night its hole has its turn (as the real foragers graze only round the holes they opened).
     */
    @Nullable
    private BlockPos grazedFrom(BlockPos plant, List<BlockPos> holes) {
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

    /** {@code foragers} more of the numbers out this second, placed at work (for the door and the colony list): out searching, they come home with food, not empty. */
    public void sendSearching(int foragers) {
        placedNow += foragers;
        out += foragers;
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
        if (level.isLoaded(pos) && colony.profile().food().plantFor(level.getBlockState(pos)) == null) {
            return 0; // no plant there any more (the next look forgets it): nothing to cut
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
            colony.news().tell(level, ColonyNews.NUMBERS_TRIP, new ColonyNews.NumbersTrip(pos)); // their trip out and back wears a trail too
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
        ListTag pictureList = new ListTag();
        unknown.forEach((pos, plant) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos.asLong());
            entry.putInt("kind", plant.kind());
            entry.putFloat("trip", plant.trip());
            pictureList.add(entry);
        });
        field.put("unknown", pictureList);
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
        ListTag plants = field.getList("plants", Tag.TAG_COMPOUND);
        for (int i = 0; i < plants.size(); i++) {
            CompoundTag entry = plants.getCompound(i);
            known.put(BlockPos.of(entry.getLong("pos")), new Plant(entry.getInt("kind"), entry.getFloat("trip"), entry.getBoolean("inside")));
        }
        unknown.clear();
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
}
