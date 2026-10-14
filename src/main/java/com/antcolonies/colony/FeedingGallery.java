package com.antcolonies.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A feeding gallery (the user's idea of 2026-10-14): real termites reach a fallen log through the soil and eat it from
 * underneath, covered, so they can feed by day as well as by night. Once a new tunnel to rich food is dug, the workers
 * dig a short gallery from it up under the food: the ground block under each block of the food (a whole fallen tree
 * lying on the ground, its stump, a hay bale), joined to the tunnel underground. A termite in the gallery has the food
 * right over its head and eats it from there; the food is its roof. Species-neutral: any rich food that is a whole block.
 *
 * <p>A block under the food is left out if digging it would open the gallery to the outside (the food spans a dip, or
 * the ground falls away beside it), and so is food that does not lie on soil.
 */
final class FeedingGallery {
    /** The food blocks looked at: within this many blocks of the food the tunnel was dug for. */
    private static final int FOOD_SPREAD = 6;
    /** At most this many food blocks (a fallen tree with its stump is at most about 8). */
    private static final int MAX_FOOD_BLOCKS = 32;
    /** A joining passage is at most this long. */
    private static final int MAX_PASSAGE = 10;
    /** At most this many cells are looked at for each joining passage. */
    private static final int MAX_SEARCH = 3000;

    private FeedingGallery() {
    }

    /**
     * The gallery's cells in the order to dig them (each touches one dug before it or the tunnel), or an empty list if
     * the food cannot be reached from below.
     *
     * @param isFood whether a block is rich food that is a whole block
     */
    static List<BlockPos> plan(Level level, NestPlan.ForagingTunnel tunnel, BlockPos food, TunnelRoute.Ground ground,
            java.util.function.Predicate<BlockPos> isFood) {
        Set<BlockPos> pieces = foodBlocks(level, food, isFood);
        // Under each piece: the ground block it lies on, if it may be dug.
        Set<BlockPos> under = new LinkedHashSet<>();
        for (BlockPos piece : pieces) {
            BlockPos cell = piece.below();
            if (!pieces.contains(cell) && ground.mayDig(cell)) {
                under.add(cell);
            }
        }
        // Left out: a cell with open air beside it (it would open the gallery to the outside).
        Set<BlockPos> wanted = new LinkedHashSet<>();
        for (BlockPos cell : under) {
            boolean closed = true;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = cell.relative(side);
                BlockState state = level.getBlockState(next);
                closed &= under.contains(next) || !state.getCollisionShape(level, next).isEmpty() && state.getFluidState().isEmpty();
            }
            if (closed) {
                wanted.add(cell);
            }
        }
        // Joined up one by one: each time the nearest wanted cell, by a passage underground from what is joined already.
        Set<BlockPos> joined = new HashSet<>(tunnel.tunnel());
        List<BlockPos> gallery = new ArrayList<>();
        while (!wanted.isEmpty()) {
            List<BlockPos> passage = passage(joined, wanted, ground);
            if (passage == null) {
                break;
            }
            for (BlockPos cell : passage) {
                if (joined.add(cell)) {
                    gallery.add(cell);
                }
                wanted.remove(cell);
            }
        }
        return gallery;
    }

    /** The food blocks joined to {@code food} (face to face), near it. */
    private static Set<BlockPos> foodBlocks(Level level, BlockPos food, java.util.function.Predicate<BlockPos> isFood) {
        Set<BlockPos> pieces = new LinkedHashSet<>();
        if (!level.isLoaded(food) || !isFood.test(food)) {
            return pieces;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        pieces.add(food);
        queue.add(food);
        while (!queue.isEmpty() && pieces.size() < MAX_FOOD_BLOCKS) {
            BlockPos at = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (next.distManhattan(food) <= FOOD_SPREAD && !pieces.contains(next) && level.isLoaded(next) && isFood.test(next)) {
                    pieces.add(next);
                    queue.add(next);
                }
            }
        }
        return pieces;
    }

    /**
     * The shortest passage (face to face) from a cell of {@code joined} to one of {@code wanted}, through soil that may
     * be dug and has soil over it (so the passage stays underground), ending with the wanted cell; null if there is none
     * within {@link #MAX_PASSAGE}.
     */
    private static List<BlockPos> passage(Set<BlockPos> joined, Set<BlockPos> wanted, TunnelRoute.Ground ground) {
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> steps = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : joined) {
            steps.put(start, 0);
            queue.add(start);
        }
        int looked = 0;
        while (!queue.isEmpty() && looked++ < MAX_SEARCH) {
            BlockPos at = queue.poll();
            int step = steps.get(at);
            if (step >= MAX_PASSAGE) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (steps.containsKey(next)) {
                    continue;
                }
                boolean goal = wanted.contains(next);
                if (!goal && !isUnderground(next, ground)) {
                    continue;
                }
                steps.put(next, step + 1);
                cameFrom.put(next, at);
                if (goal) {
                    List<BlockPos> path = new ArrayList<>();
                    for (BlockPos cell = next; !joined.contains(cell); cell = cameFrom.get(cell)) {
                        path.add(0, cell);
                    }
                    return path;
                }
                queue.add(next);
            }
        }
        return null;
    }

    /** Soil that may be dug, with more soil over it (digging it opens nothing to the sky). */
    private static boolean isUnderground(BlockPos cell, TunnelRoute.Ground ground) {
        Integer top = ground.groundTop(cell.getX(), cell.getZ());
        return top != null && cell.getY() < top && (ground.mayDig(cell) || ground.isDug(cell));
    }
}
