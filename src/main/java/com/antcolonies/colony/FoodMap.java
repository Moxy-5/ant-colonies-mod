package com.antcolonies.colony;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The food plants the colony knows of: a second small piece of the colony's food map (see "Smart foraging" in
 * CLAUDE.md; the first is {@link ForagingTrails}). Only for species whose profile has a {@code food.find_radius}
 * (termites); the others know every plant in their area at once, as before.
 *
 * <p>Once a second every worker of the colony looks round: a food plant within the find radius of a worker outside is
 * <b>found</b> by that worker. The finder itself may go for it at once, but the colony only <b>knows</b> it once the
 * finder is back inside the nest and has told the others (like real termites laying a trail home and recruiting
 * nestmates to a rich find). From then on any forager may go for it; foragers choose by worth (see
 * {@link FoodStore#findGrass}), so a hay bale draws a line of them. A plant that is used up or gone is forgotten. Known
 * plants are saved; plants found but not yet told of are not.
 */
public class FoodMap {
    /** How often (ticks) the workers look round. */
    private static final int LOOK_TICKS = 20;

    private final Colony colony;
    /** Plants the whole colony knows of. Saved. */
    private final Set<BlockPos> known = new HashSet<>();
    /** Plants a worker has found and not yet told the others of: worker -> plants. Not saved. */
    private final Map<UUID, Set<BlockPos>> found = new HashMap<>();
    private long lookedAt = Long.MIN_VALUE;
    /** In the game tests: scouts only go to spots in this box (the test's arena), and only plants in it are gone for. */
    @Nullable
    private AABB testArea;

    FoodMap(Colony colony) {
        this.colony = colony;
    }

    private SpeciesProfile.FoodNumbers food() {
        return colony.profile().food();
    }

    /** Whether this colony finds its food by looking (its profile has a find radius); otherwise it knows every plant. */
    public boolean findsByLooking() {
        return food().findRadius() > 0;
    }

    /** Whether this worker may go for the plant: the colony knows it, or the worker found it itself. */
    public boolean knows(BlockPos plant, @Nullable UUID worker) {
        if (testArea != null && !testArea.contains(plant.getCenter())) {
            return false; // (a game test: only the food in its own arena, not the tests' next door)
        }
        if (!findsByLooking() || known.contains(plant)) {
            return true;
        }
        Set<BlockPos> own = worker == null ? null : found.get(worker);
        return own != null && own.contains(plant);
    }

    /** How many plants the whole colony knows of. */
    public int knownCount() {
        return known.size();
    }

    /** Whether the whole colony knows this plant (not counting what a worker found and has not told yet). For tests. */
    public boolean isKnown(BlockPos plant) {
        return known.contains(plant);
    }

    /** Whether some worker has found this plant and not yet told the others. For tests. */
    public boolean isFoundButUntold(BlockPos plant) {
        return found.values().stream().anyMatch(plants -> plants.contains(plant));
    }

    /**
     * Once a second (called by the queen): every worker outside finds the food plants within the find radius, and every
     * worker inside tells the colony of the plants it found. Plants that are used up or gone are forgotten.
     */
    public void tick(ServerLevel level) {
        if (!findsByLooking()) {
            return;
        }
        long now = level.getGameTime();
        if (lookedAt != Long.MIN_VALUE && now - lookedAt < LOOK_TICKS && now >= lookedAt) {
            return;
        }
        lookedAt = now;
        List<BlockPos> plants = colony.food().plants(level);
        Set<BlockPos> stillPlants = new HashSet<>(plants);
        known.removeIf(plant -> level.isLoaded(plant) && !stillPlants.contains(plant) && !colony.food().isFoodPlant(level.getBlockState(plant)));
        double see = food().findRadius();
        double reach = food().radius() + 8.0;
        AABB area = new AABB(colony.getCenter()).inflate(reach, 24.0, reach);
        Set<UUID> seen = new HashSet<>();
        for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, area,
                ant -> ant.isAlive() && ant.isColonyWorker() && colony.getId().equals(ant.getColonyId()))) {
            UUID id = ant.getUUID();
            seen.add(id);
            BlockPos at = ant.blockPosition();
            if (colony.nest().isInside(level, at)) {
                Set<BlockPos> news = found.remove(id);
                if (news != null) {
                    news.removeIf(plant -> !colony.food().isFoodPlant(level.getBlockState(plant)));
                    if (!news.isEmpty()) {
                        known.addAll(news);
                        AntColoniesMod.LOGGER.debug("[food] {} told the colony of {} plants", id, news.size());
                    }
                }
                continue;
            }
            for (BlockPos plant : plants) {
                if (!known.contains(plant) && plant.distSqr(at) <= see * see) {
                    found.computeIfAbsent(id, key -> new HashSet<>()).add(plant);
                }
            }
        }
        found.keySet().retainAll(seen);
    }

    /** A worker went into the colony's numbers (it is home, see {@link Population}): it tells the colony of what it found. */
    void wentHome(UUID worker) {
        Set<BlockPos> news = found.remove(worker);
        if (news != null) {
            known.addAll(news);
        }
    }

    /** Whether a scout may go to this spot (always, except in a game test: only in its arena). */
    public boolean mayScout(BlockPos spot) {
        return testArea == null || testArea.contains(spot.getCenter());
    }

    /** The colony knows this plant at once, as if a worker had found it and told of it. For the game tests. */
    public void knowForTests(BlockPos plant) {
        known.add(plant.immutable());
    }

    /** Scouts of this colony only go to spots in this box, and its foragers only go for plants in it. For the game tests. */
    public void setTestArea(@Nullable AABB area) {
        testArea = area;
    }

    /** For the colony list: " | knows 12 food plants (3 found, not yet told)", or empty for a species that knows them all. */
    public String describe() {
        if (!findsByLooking()) {
            return "";
        }
        int untold = 0;
        for (Set<BlockPos> plants : found.values()) {
            untold += plants.size();
        }
        return " | knows " + known.size() + " food plants" + (untold > 0 ? " (" + untold + " found, not yet told)" : "");
    }

    void save(CompoundTag tag) {
        tag.put("knownFood", new LongArrayTag(known.stream().mapToLong(BlockPos::asLong).toArray()));
    }

    void load(CompoundTag tag) {
        known.clear();
        if (tag.contains("knownFood", Tag.TAG_LONG_ARRAY)) {
            for (long pos : tag.getLongArray("knownFood")) {
                known.add(BlockPos.of(pos));
            }
        }
    }
}
