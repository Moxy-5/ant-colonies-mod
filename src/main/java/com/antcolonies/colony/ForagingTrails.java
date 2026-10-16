package com.antcolonies.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Where the colony's foragers have been finding food lately, and by which way out they went: a first small piece of
 * the colony's food map (see "Smart foraging" in CLAUDE.md). Every time a forager sets out for food (a plant to cut,
 * an item to pick up) or cuts a piece, the spot is remembered for {@link #MEMORY_TICKS}, together with the way out
 * (a foraging hole or the open entrance, see {@link ForagingHoles#waysOut}) that forager last came out by. A
 * <b>trail</b> runs from a way out to the patch most of its remembered spots lie in. The minor soldiers line the
 * trails (see {@link Soldiers}).
 *
 * <p>It also keeps count of the workers outside (once a second), which tells the soldiers when the last of them is
 * back in. The visits are not saved: after a reload the trails form again as the foragers go out.
 *
 * <p><b>The lasting memory</b> (brain step 4a): each way out also keeps a remembered trail to the patch its foragers
 * went to, 1 stronger per visit; unused it fades (the profile's {@code food.trails}: its strength halves every
 * {@code half_life_ticks}) and below {@code fade_below} it is forgotten. It is saved, and lasts while the colony is away
 * (it fades by the clock, nothing needs to run). The ants' own lasting trails over the ground are {@link TrunkTrails}.
 */
public class ForagingTrails {
    /** How long (ticks) a food spot is remembered: 2 minutes. */
    static final int MEMORY_TICKS = 2400;
    /** Spots within this distance (blocks) of each other lie in the same patch. */
    static final double PATCH_RADIUS = 4.0;
    /** At most this many spots are remembered (the oldest are forgotten first). */
    private static final int MAX_VISITS = 128;
    /** A worker first seen outside within this distance (blocks) of a way out came out by it. */
    private static final double EXIT_REACH = 4.0;
    /** The busiest trail stays the busiest until another has this many more spots (so the sentries don't keep moving). */
    private static final int BUSIEST_BONUS = 3;
    /** How often (ticks) the workers are looked at. */
    private static final int LOOK_TICKS = 20;

    /** A food spot a forager went to, the way out it last came out by (null: not known), and when. */
    private record Visit(BlockPos spot, @Nullable BlockPos exit, long time) {
    }

    /** A trail: from a way out (its mouth, see {@link ForagingHoles.WayOut}) to a food patch, and how many spots it has. */
    public record Trail(BlockPos exit, BlockPos patch, int spots) {
    }

    /** A remembered trail: from a way out to a patch, its strength at the game time {@code at}. */
    private record Memory(BlockPos patch, double strength, long at) {
    }

    /** A remembered trail as the colony knows it now: from a way out (its mouth) to a patch, and its strength now. */
    public record Remembered(BlockPos exit, BlockPos patch, double strength) {
    }

    private final Colony colony;
    /** The remembered trail of each way out (its mouth). Saved. */
    private final Map<BlockPos, Memory> memory = new LinkedHashMap<>();
    private final ArrayDeque<Visit> visits = new ArrayDeque<>();
    /** The way out each worker last came out by (its mouth). */
    private final Map<UUID, BlockPos> lastExit = new HashMap<>();
    /** The workers that were outside at the last look. */
    private final Set<UUID> outside = new HashSet<>();
    /** The patch each way out's trail leads to (kept while it stays busy, so the trail doesn't wander). */
    private final Map<BlockPos, BlockPos> patches = new HashMap<>();
    /** The way out of the busiest trail at the last count. */
    @Nullable
    private BlockPos busiest;
    private long lookedAt = Long.MIN_VALUE;

    ForagingTrails(Colony colony) {
        this.colony = colony;
    }

    /** A forager of this colony is going to this food spot, or took a piece there. */
    public void visited(Level level, BlockPos spot, UUID ant) {
        BlockPos exit = lastExit.get(ant);
        visits.addLast(new Visit(spot.immutable(), exit, level.getGameTime()));
        while (visits.size() > MAX_VISITS) {
            visits.removeFirst();
        }
        if (exit == null) {
            exit = nearest(colony.foragingHoles().waysOut(level).stream().map(ForagingHoles.WayOut::mouth).toList(), spot, Double.MAX_VALUE);
        }
        if (exit != null) {
            strengthen(level, exit, spot);
        }
    }

    /** The remembered trail from this way out grows 1 stronger (a new one leads to this spot). */
    private void strengthen(Level level, BlockPos exit, BlockPos spot) {
        com.antcolonies.species.SpeciesProfile.Trails numbers = colony.profile().food().trails();
        long now = level.getGameTime();
        Memory was = memory.get(exit);
        double strength = was == null ? 0.0 : numbers.decayed(was.strength(), now - was.at());
        // It leads to the patch where most of this way out's recent spots lie (the new spot alone if it is new).
        List<BlockPos> spots = new ArrayList<>();
        for (Visit visit : visits) {
            if (exit.equals(visit.exit())) {
                spots.add(visit.spot());
            }
        }
        spots.add(spot.immutable());
        BlockPos patch = was == null || strength < numbers.fadeBelow() ? spot.immutable() : densest(spots, PATCH_RADIUS);
        memory.put(exit.immutable(), new Memory(patch, Math.min(numbers.mostStrength(), strength + 1.0), now));
    }

    /**
     * The remembered trails now, strongest first (faded ones are forgotten): where the foragers of each way out have
     * been going, lasting long after the visits themselves (for the brain's later steps: recruiting along a trail,
     * going back to a known patch).
     */
    public List<Remembered> remembered(Level level) {
        com.antcolonies.species.SpeciesProfile.Trails numbers = colony.profile().food().trails();
        long now = level.getGameTime();
        List<Remembered> list = new ArrayList<>();
        memory.entrySet().removeIf(entry -> numbers.decayed(entry.getValue().strength(), now - entry.getValue().at()) < numbers.fadeBelow());
        memory.forEach((exit, trail) -> list.add(new Remembered(exit, trail.patch(), numbers.decayed(trail.strength(), now - trail.at()))));
        list.sort(Comparator.comparingDouble(Remembered::strength).reversed());
        return list;
    }

    /** For the tests: every remembered trail's last visit was this many ticks earlier. */
    public void ageMemoryForTests(long ticks) {
        memory.replaceAll((exit, trail) -> new Memory(trail.patch(), trail.strength(), trail.at() - ticks));
    }

    void save(net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        memory.forEach((exit, trail) -> {
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putLong("exit", exit.asLong());
            entry.putLong("patch", trail.patch().asLong());
            entry.putDouble("strength", trail.strength());
            entry.putLong("at", trail.at());
            list.add(entry);
        });
        tag.put("trailMemory", list);
    }

    void load(net.minecraft.nbt.CompoundTag tag) {
        memory.clear();
        for (net.minecraft.nbt.Tag t : tag.getList("trailMemory", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag entry = (net.minecraft.nbt.CompoundTag) t;
            memory.put(BlockPos.of(entry.getLong("exit")), new Memory(BlockPos.of(entry.getLong("patch")), entry.getDouble("strength"), entry.getLong("at")));
        }
    }

    /** How many of the colony's workers are outside the nest and the mound (looked at once a second). */
    public int workersOutside(Level level) {
        look(level);
        return outside.size();
    }

    /**
     * Looks at the colony's workers (at most once a second): which of them are outside, and for each that has just
     * come out, the way out it came out by.
     */
    private void look(Level level) {
        long now = level.getGameTime();
        if (lookedAt != Long.MIN_VALUE && now - lookedAt < LOOK_TICKS && now >= lookedAt) {
            return;
        }
        lookedAt = now;
        List<ForagingHoles.WayOut> ways = colony.foragingHoles().waysOut(level);
        double reach = colony.foragingRadius() + 8.0;
        AABB area = new AABB(colony.getCenter()).inflate(reach, 24.0, reach);
        Set<UUID> seen = new HashSet<>();
        for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, area,
                ant -> ant.isAlive() && ant.isColonyWorker() && colony.getId().equals(ant.getColonyId()))) {
            if (colony.nest().isInside(level, ant.blockPosition())) {
                continue;
            }
            seen.add(ant.getUUID());
            if (!outside.contains(ant.getUUID())) {
                BlockPos exit = nearest(ways.stream().map(ForagingHoles.WayOut::mouth).toList(), ant.blockPosition(), EXIT_REACH);
                if (exit != null) {
                    lastExit.put(ant.getUUID(), exit);
                }
            }
        }
        outside.clear();
        outside.addAll(seen);
        if (lastExit.size() > 512) {
            lastExit.clear(); // only if workers keep coming and going for a very long time: they are simply learnt again
        }
    }

    /**
     * The trails now, busiest first: for every way out that is open, the patch where most of the food spots its
     * foragers went to lie (a spot whose forager's way out is not known, or is closed now, goes with the nearest way
     * out). Ways out with no spots have no trail.
     */
    public List<Trail> trails(Level level) {
        look(level);
        long now = level.getGameTime();
        visits.removeIf(visit -> now - visit.time() > MEMORY_TICKS || visit.time() > now);
        List<BlockPos> exits = colony.foragingHoles().waysOut(level).stream().map(ForagingHoles.WayOut::mouth).toList();
        if (exits.isEmpty()) {
            return List.of();
        }
        Map<BlockPos, List<BlockPos>> byExit = new LinkedHashMap<>();
        for (BlockPos exit : exits) {
            byExit.put(exit, new ArrayList<>());
        }
        for (Visit visit : visits) {
            BlockPos exit = visit.exit() != null && byExit.containsKey(visit.exit()) ? visit.exit() : nearest(exits, visit.spot(), Double.MAX_VALUE);
            byExit.get(exit).add(visit.spot());
        }
        List<Trail> trails = new ArrayList<>();
        for (Map.Entry<BlockPos, List<BlockPos>> entry : byExit.entrySet()) {
            List<BlockPos> spots = entry.getValue();
            if (spots.isEmpty()) {
                continue;
            }
            BlockPos patch = densest(spots, PATCH_RADIUS);
            BlockPos was = patches.get(entry.getKey());
            // The trail keeps its patch while that still has at least half as many spots as the busiest patch.
            if (was != null && countNear(spots, was, PATCH_RADIUS) * 2 >= countNear(spots, patch, PATCH_RADIUS)) {
                patch = was;
            }
            patches.put(entry.getKey(), patch);
            Memory remembered = memory.get(entry.getKey());
            if (remembered != null) {
                memory.put(entry.getKey(), new Memory(patch, remembered.strength(), remembered.at()));
            }
            trails.add(new Trail(entry.getKey(), patch, spots.size()));
        }
        patches.keySet().retainAll(byExit.keySet());
        BlockPos wasBusiest = busiest;
        trails.sort(Comparator.comparingInt((Trail trail) -> trail.spots() + (trail.exit().equals(wasBusiest) ? BUSIEST_BONUS : 0)).reversed());
        busiest = trails.isEmpty() ? null : trails.get(0).exit();
        return trails;
    }

    /**
     * The middle of the patch the most spots lie in: the spot with the most others within {@code radius} (the latest one
     * on a tie), and the average of those (rounded to a block).
     */
    static BlockPos densest(List<BlockPos> spots, double radius) {
        BlockPos best = spots.get(0);
        int bestCount = -1;
        for (BlockPos spot : spots) {
            int count = countNear(spots, spot, radius);
            if (count >= bestCount) {
                best = spot;
                bestCount = count;
            }
        }
        double x = 0;
        double y = 0;
        double z = 0;
        int n = 0;
        for (BlockPos spot : spots) {
            if (spot.distSqr(best) <= radius * radius) {
                x += spot.getX();
                y += spot.getY();
                z += spot.getZ();
                n++;
            }
        }
        return new BlockPos((int) Math.floor(x / n + 0.5), (int) Math.floor(y / n + 0.5), (int) Math.floor(z / n + 0.5));
    }

    private static int countNear(List<BlockPos> spots, BlockPos at, double radius) {
        int count = 0;
        for (BlockPos spot : spots) {
            if (spot.distSqr(at) <= radius * radius) {
                count++;
            }
        }
        return count;
    }

    /** The cell in {@code cells} nearest {@code to} sideways, if within {@code reach}; null if none is. */
    @Nullable
    private static BlockPos nearest(List<BlockPos> cells, BlockPos to, double reach) {
        BlockPos best = null;
        double bestDistance = reach * reach;
        for (BlockPos cell : cells) {
            double dx = cell.getX() - to.getX();
            double dz = cell.getZ() - to.getZ();
            double distance = dx * dx + dz * dz;
            if (distance <= bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** For the colony list: " | trails: from 20,12,12 to 20,12,23 (14 spots)", or empty with no trail. */
    public String describe(Level level) {
        List<Trail> trails = trails(level);
        if (trails.isEmpty()) {
            return "";
        }
        StringBuilder line = new StringBuilder(" | trails:");
        for (Trail trail : trails) {
            line.append(" from ").append(trail.exit().toShortString()).append(" to ").append(trail.patch().toShortString())
                    .append(" (").append(trail.spots()).append(" spots)");
        }
        return line.toString();
    }

    /** For the colony list: " | remembered trails: from 20,12,12 to 20,12,23 (strength 12)", or empty. */
    public String describeMemory(Level level) {
        List<Remembered> list = remembered(level);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder line = new StringBuilder(" | remembered trails:");
        for (Remembered trail : list) {
            line.append(" from ").append(trail.exit().toShortString()).append(" to ").append(trail.patch().toShortString())
                    .append(" (strength ").append(Math.round(trail.strength())).append(")");
        }
        return line.toString();
    }
}
