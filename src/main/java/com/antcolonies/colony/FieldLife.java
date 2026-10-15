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
            if (plant == null || !colony.foodMap().knows(pos, null)) {
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

    /** The forager numbers' trips this second (see the class comment). */
    private boolean forage(ServerLevel level) {
        out = 0;
        int foragers = foragersInside(level);
        if (foragers <= 0 || known.isEmpty()) {
            progress.clear();
            return false;
        }
        boolean outside = colony.isForagingTime(level);
        if (!outside && (!colony.keepsASchedule() || colony.alarm().keepsEveryoneIn(level))) {
            progress.clear();
            return false; // the alarm keeps everyone in
        }
        FoodStore food = colony.food();
        int room = food.roomKnown();
        if (room <= 0) {
            return false;
        }
        List<SpeciesProfile.PlantFood> kinds = colony.profile().food().plants();
        List<Map.Entry<BlockPos, Plant>> plants = new ArrayList<>();
        Map<BlockPos, Double> worth = new HashMap<>();
        for (Map.Entry<BlockPos, Plant> entry : known.entrySet()) {
            Plant plant = entry.getValue();
            if (plant.kind() < 0 || plant.kind() >= kinds.size() || !outside && !plant.fromInside()
                    || !colony.foodMap().knows(entry.getKey(), null)) {
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
        long now = level.getGameTime();
        double free = foragers;
        boolean changed = false;
        int worked = 0;
        List<BlockPos> usedUp = new ArrayList<>();
        for (Map.Entry<BlockPos, Plant> entry : plants) {
            if (free <= 0 || room <= 0 || worked++ >= PLANTS_PER_SECOND) {
                break;
            }
            BlockPos pos = entry.getKey();
            Plant plant = entry.getValue();
            SpeciesProfile.PlantFood kind = kinds.get(plant.kind());
            int places = Math.max(1, kind.foragers()) - food.foragersAt(pos);
            if (places <= 0) {
                continue;
            }
            if (level.isLoaded(pos) && !Population.outOfSight(level, pos, eyes)) {
                progress.remove(pos); // a player sees it: left to the real foragers
                continue;
            }
            // A forager keeps a place at the plant from setting out until its piece comes off (and a plant one cuts at
            // at a time rests a moment after); the rest of the trip is the walk home. So a place keeps this many busy:
            double trip = TRIP_TICKS + 2.0 * plant.trip() * TICKS_PER_BLOCK;
            double atPlant = plant.trip() * TICKS_PER_BLOCK + CUT_TICKS + (kind.foragers() <= 1 ? REST_TICKS : 0);
            double sent = Math.min(free, places * trip / atPlant);
            free -= sent;
            out += (int) Math.round(sent);
            double got = progress.getOrDefault(pos, 0.0) + sent * 20.0 / trip;
            int left = kind.pieces() - food.getPiecesCut(pos);
            while (got >= 1 && left > 0 && room > 0) {
                got--;
                room--;
                left = kind.pieces() - food.cutByNumbers(level, pos, kind);
                food.countIn(false);
                colony.ledger().addStore(1);
                owed.put(pos, now);
                colony.away().note(level, AwayReport.What.FORAGED, 1);
                changed = true;
            }
            if (left <= 0) {
                usedUp.add(pos);
            } else {
                progress.put(pos, Math.min(got, 1.0));
            }
        }
        for (BlockPos pos : usedUp) {
            known.remove(pos);
            progress.remove(pos);
            colony.foodMap().forget(pos);
            colony.away().note(level, AwayReport.What.PLANTS_USED_UP, 1);
        }
        return changed;
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
