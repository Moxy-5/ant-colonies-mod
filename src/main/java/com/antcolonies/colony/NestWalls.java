package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The walls of the nest: every block that touches a dug-out nest cell must be solid, except the entrance hole. Finds the
 * openings (caves, cracks, water, a block a player broke) and keeps track of the ants packing them shut.
 */
public class NestWalls {
    /** How long (in ticks) an ant that went to close a leak counts as working on it. */
    private static final int LEAK_CLAIM_TICKS = 600;
    /** At most this many ants work on one leak at the same time; an opening nobody is at yet beats a crowded one. */
    private static final int MAX_WORKERS_PER_LEAK = 4;
    /** How much closer (in blocks) an empty opening counts than a crowded one, per ant already on it. */
    private static final double CROWD_PENALTY = 6.0;

    private final Colony colony;
    private final BlockPos center;

    /** The ants working on each leak: hole -> (ant -> game time when its claim runs out). Not saved. */
    private final Map<BlockPos, Map<UUID, Long>> claimedLeaks = new HashMap<>();
    /** How far each leak is closed (1.0 = shut): every ant adds its share, so more ants close it sooner. Not saved. */
    private final Map<BlockPos, Float> sealProgress = new HashMap<>();

    /**
     * An opening in the nest walls: {@code hole} is open space (a cave, a crack, water) right next to a dug-out
     * nest cell, and {@code stand} is a dug-out cell with a floor, no further than one block from the hole, where
     * an ant can stand to close it (null when no ant can get close enough). The entrance itself is never a leak.
     */
    public record Leak(BlockPos hole, @Nullable BlockPos stand) {
    }

    NestWalls(Colony colony) {
        this.colony = colony;
        this.center = colony.getCenter();
    }

    /**
     * Every opening in the walls of the dug-out nest: any open block (air, water, lava, grass) that touches a dug
     * nest cell joined to the shaft but is not itself part of the nest. Caves, cracks and the gaps between rooms all show up here.
     * The entrance hole above the shaft is the one opening that is allowed. For tests, and for {@link #findLeak}.
     */
    public List<Leak> getLeaks(Level level) {
        Set<BlockPos> nest = colony.nest().nestCells(level);
        Set<BlockPos> seen = new HashSet<>();
        List<Leak> leaks = new ArrayList<>();
        for (BlockPos cell : joinedCells(level, nest)) {
            for (Direction direction : Direction.values()) {
                BlockPos hole = cell.relative(direction);
                // The foraging holes open and close on their own schedule (see ForagingHoles), they are no leak.
                if (nest.contains(hole) || hole.equals(center) || !seen.add(hole) || !isOpen(level, hole)
                        || colony.foragingHoles().isHole(level, hole)) {
                    continue;
                }
                leaks.add(new Leak(hole, findPlugSpot(level, nest, hole)));
            }
        }
        return leaks;
    }

    /**
     * The dug-out nest cells joined to the top of the shaft through other dug-out nest cells. A planned cell that is open
     * on its own (a cave crossing a gallery nobody has dug yet) is not part of the nest yet, so the caves round it are
     * not leaks: otherwise the workers would go sealing caves far from where they live.
     */
    private Set<BlockPos> joinedCells(Level level, Set<BlockPos> nest) {
        Set<BlockPos> joined = new HashSet<>();
        BlockPos start = center.below();
        if (!nest.contains(start) || !Nest.isDugOut(level, start)) {
            return joined;
        }
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        joined.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = cell.relative(direction);
                if (nest.contains(next) && !joined.contains(next) && level.isLoaded(next) && Nest.isDugOut(level, next)) {
                    joined.add(next);
                    queue.add(next);
                }
            }
        }
        return joined;
    }

    /**
     * The leak an ant at {@code near} should work on, or null: the closest one that can be reached, but one nobody
     * is at yet beats a crowded one a little closer, so every leak gets an ant before any gets many.
     */
    @Nullable
    public Leak findLeak(Level level, BlockPos near) {
        long now = level.getGameTime();
        for (Map<UUID, Long> workers : claimedLeaks.values()) {
            workers.values().removeIf(until -> until <= now);
        }
        claimedLeaks.values().removeIf(Map::isEmpty);
        Leak best = null;
        double bestScore = Double.MAX_VALUE;
        for (Leak leak : getLeaks(level)) {
            int workers = claimedLeaks.getOrDefault(leak.hole(), Map.of()).size();
            if (leak.stand() == null || workers >= MAX_WORKERS_PER_LEAK) {
                continue;
            }
            double score = Math.sqrt(leak.stand().distSqr(near)) + workers * CROWD_PENALTY;
            if (score < bestScore) {
                best = leak;
                bestScore = score;
            }
        }
        return best;
    }

    /** Tells the other ants that this one is working on the leak (for a while, or until {@link #releaseLeak}). */
    public void claimLeak(Level level, BlockPos hole, UUID ant) {
        claimedLeaks.computeIfAbsent(hole.immutable(), key -> new HashMap<>()).put(ant, level.getGameTime() + LEAK_CLAIM_TICKS);
    }

    /** The ant stops working on the leak (finished, or gave up). */
    public void releaseLeak(BlockPos hole, UUID ant) {
        Map<UUID, Long> workers = claimedLeaks.get(hole);
        if (workers != null) {
            workers.remove(ant);
        }
    }

    /** One ant's pack of soil: adds its share to the leak and returns the total so far (1.0 or more = enough to close it). */
    public float addSealProgress(BlockPos hole, float amount) {
        return sealProgress.merge(hole.immutable(), amount, Float::sum);
    }

    /** True while this block is still open space (so the leak is still there). */
    public boolean isLeakOpen(Level level, BlockPos hole) {
        return isOpen(level, hole);
    }

    /** Fills a leak with the species' plug block (Burrowed Dirt, mound soil). False if it is already closed or someone is standing in it (try again later). */
    public boolean plugLeak(Level level, BlockPos hole) {
        if (hole.equals(center) || !isOpen(level, hole)) {
            return false;
        }
        // Never bury anyone, same as with the hill.
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(hole)).isEmpty()) {
            return false;
        }
        level.setBlock(hole, colony.profile().nest().plugState(), 3);
        claimedLeaks.remove(hole);
        sealProgress.remove(hole);
        return true;
    }

    /**
     * Open space that soil can be packed into: air, water, lava, grass and the like. Anything else (stone, a
     * chest, a flower someone planted) is left alone. A chunk that is not loaded counts as closed.
     */
    private static boolean isOpen(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    /**
     * The dug-out nest cell closest to the hole (within one block of it, corners included) where an ant can work on it,
     * or null. A cell with a floor is preferred; failing that, a cell with a solid wall next to it that the ant can
     * cling to (the shaft and the middle of a stair have no floor), so a hole next to the top of the shaft can be closed too.
     */
    @Nullable
    /**
     * Every nest cell a hole could be closed from, best first: cells with a floor, then cells with a wall to cling to,
     * each closest first. The sealer takes the first it can reach without passing through the hole (from the far side
     * it would shut itself in).
     */
    public List<BlockPos> plugSpots(Level level, BlockPos hole) {
        Set<BlockPos> nest = colony.nest().nestCells(level);
        List<BlockPos> spots = new ArrayList<>();
        for (boolean needFloor : new boolean[] {true, false}) {
            List<BlockPos> found = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos spot = hole.offset(dx, dy, dz);
                        if (nest.contains(spot) && !spots.contains(spot) && level.isLoaded(spot) && level.getBlockState(spot).isAir()
                                && (needFloor ? hasFloor(level, spot) : hasWallToClingTo(level, spot))) {
                            found.add(spot);
                        }
                    }
                }
            }
            found.sort(java.util.Comparator.comparingDouble(hole::distSqr));
            spots.addAll(found);
        }
        return spots;
    }

    private static BlockPos findPlugSpot(Level level, Set<BlockPos> nest, BlockPos hole) {
        BlockPos onFloor = closestPlugSpot(level, nest, hole, true);
        return onFloor != null ? onFloor : closestPlugSpot(level, nest, hole, false);
    }

    @Nullable
    private static BlockPos closestPlugSpot(Level level, Set<BlockPos> nest, BlockPos hole, boolean needFloor) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos spot = hole.offset(dx, dy, dz);
                    if (!nest.contains(spot) || !level.isLoaded(spot) || !level.getBlockState(spot).isAir()
                            || !(needFloor ? hasFloor(level, spot) : hasWallToClingTo(level, spot))) {
                        continue;
                    }
                    double distance = hole.distSqr(spot);
                    if (distance < bestDistance) {
                        best = spot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static boolean hasFloor(Level level, BlockPos spot) {
        return level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP);
    }

    private static boolean hasWallToClingTo(Level level, BlockPos spot) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos wall = spot.relative(side);
            if (level.getBlockState(wall).isFaceSturdy(level, wall, side.getOpposite())) {
                return true;
            }
        }
        return false;
    }
}
