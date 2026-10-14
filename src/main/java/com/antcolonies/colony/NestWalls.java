package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.MudCrustBlock;

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
                if (nest.contains(hole) || hole.equals(center) || !isOpenToward(level, hole, direction.getOpposite())
                        || colony.foragingHoles().isHole(level, hole) || !seen.add(hole)) {
                    continue;
                }
                leaks.add(new Leak(hole, findPlugSpot(level, nest, hole)));
            }
            // Food the insects can walk into over a gallery (a fallen branch) is cased in mud: a gap in its casing is
            // a hole in the gallery's roof, closed again with a plate of mud.
            for (Direction direction : Direction.values()) {
                BlockPos food = cell.relative(direction);
                if (nest.contains(food) || !isWalkThroughFood(level, food)) {
                    continue;
                }
                for (BlockPos gap : casingGaps(level, food, direction.getOpposite(), nest)) {
                    if (seen.add(gap)) {
                        leaks.add(new Leak(gap, findPlugSpot(level, nest, gap)));
                    }
                }
            }
        }
        return leaks;
    }

    /**
     * The dug-out nest cells joined to the top of the shaft through other dug-out nest cells. A planned cell that is open
     * on its own (a cave crossing a gallery nobody has dug yet) is not part of the nest yet, so the caves round it are
     * not leaks: otherwise the workers would go sealing caves far from where they live.
     */
    Set<BlockPos> joinedCells(Level level, Set<BlockPos> nest) {
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

    /**
     * True while the leak is still there: the block is still open space, or a crust of mud without a plate toward one
     * of the dug-out nest cells beside it.
     */
    public boolean isLeakOpen(Level level, BlockPos hole) {
        if (!level.isLoaded(hole) || !MudCrustBlock.isCrust(level.getBlockState(hole))) {
            return isOpen(level, hole);
        }
        Set<BlockPos> nest = colony.nest().nestCells(level);
        for (Direction side : Direction.values()) {
            BlockPos cell = hole.relative(side);
            if ((nest.contains(cell) && Nest.isDugOut(level, cell) || isWalkThroughFood(level, cell)) && isOpenToward(level, hole, side)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this block is food the insects can walk into (a fallen branch: thin, nothing bumps into it), which the
     * colony eats in stages. Over a feeding gallery it is cased in mud ({@link #wallFirst}), so the gallery has a roof.
     */
    public static boolean isWalkThroughFood(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof com.antcolonies.block.EatenInStages && state.getCollisionShape(level, pos).isEmpty();
    }

    /** One of the colony's own openings (a foraging hole), or the open air right over one, which is never walled up. */
    private boolean isColonyOpening(Level level, BlockPos pos) {
        return colony.foragingHoles().isHole(level, pos) || colony.foragingHoles().isHole(level, pos.below());
    }

    /**
     * The gaps in the mud casing of this walk-through food over the gallery on side {@code gallery}: every block beside
     * it (but the gallery) that leaves it open to the outside. Other food beside it, the nest and the colony's own holes
     * are no gap.
     */
    private List<BlockPos> casingGaps(Level level, BlockPos food, Direction gallery, Set<BlockPos> nest) {
        List<BlockPos> gaps = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos next = food.relative(side);
            if (side == gallery || nest.contains(next) || next.equals(center) || !level.isLoaded(next) || isWalkThroughFood(level, next)
                    || isColonyOpening(level, next) || !level.getFluidState(next).isEmpty() || !isOpenToward(level, next, side.getOpposite())) {
                continue;
            }
            gaps.add(next.immutable());
        }
        return gaps;
    }

    /**
     * Whether this block leaves the cell on side {@code toward} of it open: open space (see {@link #isOpen}), or a crust
     * of mud with no plate on that side.
     */
    public static boolean isOpenToward(Level level, BlockPos hole, Direction toward) {
        if (!level.isLoaded(hole)) {
            return false;
        }
        BlockState state = level.getBlockState(hole);
        return MudCrustBlock.isCrust(state) ? !MudCrustBlock.hasFace(state, toward) : isOpen(level, hole);
    }

    /**
     * Whether the dug-out cell is closed all round: every block beside it that is not part of the nest walls it off
     * (soil, wood, a crust of mud with a plate toward it). A feeding gallery is only fed from once it is closed round.
     */
    public boolean isClosedRound(Level level, BlockPos cell, Set<BlockPos> nest) {
        for (Direction side : Direction.values()) {
            BlockPos next = cell.relative(side);
            if (nest.contains(next)) {
                continue;
            }
            if (isWalkThroughFood(level, next) ? !casingGaps(level, next, side.getOpposite(), nest).isEmpty()
                    : isOpenToward(level, next, side.getOpposite())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Wall first (the user's decision, 2026-10-14; species whose profile has {@code nest.crust_walls}: termites): just
     * before the last bite opens this nest cell, a crust of mud is plastered over every side of it that would open to
     * the outside (open air, a cave, grass), so the gallery is never open, not even for a moment, as real termites
     * plaster over any side of a gallery that breaks through. The colony's own spaces (the nest, the holes, the
     * entrance, the mound) are left open, and so is water (no gallery is planned beside it).
     */
    public void wallFirst(Level level, BlockPos target) {
        if (!colony.profile().nest().crustWalls()) {
            return;
        }
        Set<BlockPos> nest = colony.nest().nestCells(level);
        Map<BlockPos, ?> mound = colony.mound().getParts(level);
        if (!nest.contains(target) || target.equals(center) || mound.containsKey(target) || colony.foragingHoles().isHole(level, target)
                || colony.mound().getGalleries(level).contains(target)) {
            return;
        }
        for (Direction side : Direction.values()) {
            BlockPos next = target.relative(side);
            if (nest.contains(next) || next.equals(center) || mound.containsKey(next) || !level.isLoaded(next)
                    || colony.foragingHoles().isHole(level, next) || !level.getFluidState(next).isEmpty()
                    || !isOpenToward(level, next, side.getOpposite())) {
                continue;
            }
            level.setBlock(next, crust().withFace(level.getBlockState(next), side.getOpposite()), 3);
        }
        // Food over it that the insects could walk into (a fallen branch) is cased in mud on every other side, so the
        // gallery has a roof (real termites cover a branch in sheeting and eat it from underneath). The mud holds it up
        // once the soil under it is dug (FallenBranchBlock).
        for (Direction side : Direction.values()) {
            BlockPos food = target.relative(side);
            if (nest.contains(food) || !isWalkThroughFood(level, food)) {
                continue;
            }
            for (BlockPos gap : casingGaps(level, food, side.getOpposite(), nest)) {
                if (!mound.containsKey(gap)) {
                    Direction toward = Direction.getNearest(food.getX() - gap.getX(), food.getY() - gap.getY(), food.getZ() - gap.getZ());
                    level.setBlock(gap, crust().withFace(level.getBlockState(gap), toward), 3);
                }
            }
        }
    }

    private static MudCrustBlock crust() {
        return com.antcolonies.registry.ModBlocks.MUD_CRUST.get();
    }

    /**
     * What a leak is closed with: the plug block, packed full (a whole missing block is rebuilt whole, the user's
     * decision of 2026-10-14). A crust of mud missing a plate toward a dug-out nest cell beside it (a gallery dug on
     * past an older wall) only gets the plates it lacks.
     */
    private BlockState wallFor(Level level, BlockPos hole) {
        BlockState state = level.getBlockState(hole);
        Set<BlockPos> nest = colony.nest().nestCells(level);
        boolean besideNest = false;
        for (Direction side : Direction.values()) {
            BlockPos cell = hole.relative(side);
            besideNest |= nest.contains(cell) && Nest.isDugOut(level, cell);
        }
        if (!MudCrustBlock.isCrust(state) && (besideNest || !colony.profile().nest().crustWalls())) {
            return colony.profile().nest().plugState();
        }
        // A crust gets the plates it lacks; a gap in a branch's casing (nothing of the nest right beside it) a new plate.
        BlockState wall = state;
        for (Direction side : Direction.values()) {
            BlockPos cell = hole.relative(side);
            if (nest.contains(cell) && Nest.isDugOut(level, cell) || isWalkThroughFood(level, cell)) {
                wall = crust().withFace(wall, side);
            }
        }
        return MudCrustBlock.isCrust(wall) ? wall : colony.profile().nest().plugState();
    }

    /**
     * Fills a leak with the species' plug block (Burrowed Dirt, mound soil). False if it is already closed or someone is
     * standing in it (try again later). A soldier of the colony standing in it (guarding the breach) does not stop it: it
     * is pushed out to the far side, shut outside, like real termites sealing a breach behind their soldiers (it comes
     * back in later by a foraging hole); if the hole leads nowhere open, it is pushed back into the nest.
     */
    public boolean plugLeak(Level level, BlockPos hole) {
        if (hole.equals(center) || !isLeakOpen(level, hole)) {
            return false;
        }
        // Never bury anyone, same as with the hill.
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, new AABB(hole));
        if (!inside.isEmpty()) {
            BlockPos out = inside.stream().allMatch(this::isOwnSoldier) ? outsideOf(level, hole) : null;
            if (out == null) {
                return false;
            }
            for (LivingEntity soldier : inside) {
                soldier.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
                if (soldier instanceof com.antcolonies.entity.AbstractAntEntity insect) {
                    insect.getNavigator().stop();
                }
            }
        }
        level.setBlock(hole, wallFor(level, hole), 3);
        claimedLeaks.remove(hole);
        sealProgress.remove(hole);
        return true;
    }

    private boolean isOwnSoldier(LivingEntity entity) {
        return entity instanceof com.antcolonies.entity.TermiteSoldierEntity soldier && colony.getId().equals(soldier.getColonyId());
    }

    /** Where a soldier in the hole is pushed to: an open cell beside it away from the nest (one to stand on first), else back into the nest. */
    @Nullable
    private BlockPos outsideOf(Level level, BlockPos hole) {
        Set<BlockPos> nest = colony.nest().nestCells(level);
        for (Direction side : Direction.values()) {
            BlockPos cell = hole.relative(side);
            BlockPos floor = cell.below();
            // First straight through the hole: the nest on one side, out on the other.
            if (nest.contains(hole.relative(side.getOpposite())) && !nest.contains(cell) && level.isLoaded(cell) && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                    && level.getBlockState(cell).getFluidState().isEmpty() && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                return cell;
            }
        }
        // Then any open cell outside; and if the hole leads nowhere (a cut wall with soil behind it), back into the nest.
        for (boolean intoTheNest : new boolean[] {false, true}) {
            for (Direction side : Direction.values()) {
                BlockPos cell = hole.relative(side);
                if (nest.contains(cell) == intoTheNest && level.isLoaded(cell) && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                        && level.getBlockState(cell).getFluidState().isEmpty()) {
                    return cell;
                }
            }
        }
        return null;
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
