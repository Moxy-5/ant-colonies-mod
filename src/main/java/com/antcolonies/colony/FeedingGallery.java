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
 * right over its head and eats it from there; the food is its roof. Species-neutral: any rich food that is a whole block,
 * or a branch lying on the ground (the workers case it in mud first, see {@link NestWalls#wallFirst}, so it is a roof
 * too). A block of food eaten away leaves a mud cast (see {@code MudCastBlock}): the gallery keeps its roof.
 *
 * <p>Where the ground falls away beside the food, the open side is walled up with a crust of mud by the workers (see
 * {@link NestWalls}). A block under the food next to water is left out, and so is food that does not lie on soil.
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

    /** At most this many mud casts are joined to the galleries (a fallen tree is about 8 blocks). */
    private static final int MAX_CASTS = 256;

    /** The split, step S2: what the galleries part plugs into the shared nest ({@link ColonyHooks#FEEDING_WAYS}). */
    static final ColonyHooks.FeedingWays WAYS = new ColonyHooks.FeedingWays() {
        @Override
        public Set<BlockPos> foodBlocks(Level level, BlockPos food, java.util.function.Predicate<BlockPos> isFood) {
            return FeedingGallery.foodBlocks(level, food, isFood);
        }

        @Override
        public List<BlockPos> plan(Level level, NestPlan.ForagingTunnel tunnel, BlockPos food, TunnelRoute.Ground ground,
                java.util.function.Predicate<BlockPos> isFood) {
            return FeedingGallery.plan(level, tunnel, food, ground, isFood);
        }

        @Override
        public Map<BlockPos, BlockPos> joined(Level level, List<BlockPos> galleryCells, Set<BlockPos> inside) {
            return casts(level, galleryCells, inside);
        }
    };

    private FeedingGallery() {
    }

    /**
     * The mud casts left of eaten wood joined by their channels to a feeding gallery cell that is {@code inside} (see
     * {@link Nest#isInside}), each with the gallery cell it is joined to: inside too, eaten on from there.
     */
    static Map<BlockPos, BlockPos> casts(Level level, List<BlockPos> galleryCells, Set<BlockPos> inside) {
        Map<BlockPos, BlockPos> casts = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos gallery : galleryCells) {
            if (inside.contains(gallery)) {
                joinCasts(level, gallery, gallery, casts, queue);
            }
        }
        while (!queue.isEmpty() && casts.size() < MAX_CASTS) {
            BlockPos cast = queue.poll();
            joinCasts(level, cast, casts.get(cast), casts, queue);
        }
        return casts;
    }

    /** Adds the mud casts whose channels join the one at {@code from} (a gallery cell or a cast) to {@code casts}. */
    private static void joinCasts(Level level, BlockPos from, BlockPos gallery, Map<BlockPos, BlockPos> casts, ArrayDeque<BlockPos> queue) {
        BlockState fromState = level.getBlockState(from);
        boolean fromCast = com.antcolonies.block.MudCastBlock.isCast(fromState);
        for (Direction side : Direction.values()) {
            BlockPos next = from.relative(side);
            if (casts.containsKey(next) || !level.isLoaded(next)) {
                continue;
            }
            BlockState state = level.getBlockState(next);
            if (com.antcolonies.block.MudCastBlock.isCast(state)
                    && com.antcolonies.nav.Channel.has(com.antcolonies.block.TermiteTunnelBlock.openMask(state), side.getOpposite())
                    && (!fromCast || com.antcolonies.nav.Channel.has(com.antcolonies.block.TermiteTunnelBlock.openMask(fromState), side))) {
                casts.put(next.immutable(), gallery);
                queue.add(next.immutable());
            }
        }
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
        // A cell with open air beside it (the food lies on the edge of a step) is dug too: the opening is then a hole in
        // the nest walls, and the workers pack it with soil (NestWalls), a wall of mud like real termites plaster over an
        // open side. Only a cell beside water is left out.
        Set<BlockPos> wanted = new LinkedHashSet<>();
        for (BlockPos cell : under) {
            boolean dry = true;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                dry &= level.getFluidState(cell.relative(side)).isEmpty();
            }
            if (dry) {
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
    static Set<BlockPos> foodBlocks(Level level, BlockPos food, java.util.function.Predicate<BlockPos> isFood) {
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
