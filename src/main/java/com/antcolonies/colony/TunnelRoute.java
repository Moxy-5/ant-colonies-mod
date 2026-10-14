package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Plans a foraging tunnel out to food (new holes near rich food, the user's decision of 2026-10-14): a route through
 * the ground, one block wide, just under the surface (one block of soil over it, like the nest's first foraging
 * tunnels), from a block of a tunnel already dug to a column beside the food, where a hole opens up from its end.
 * Real Macrotermes dig such galleries far out under the savanna and open foraging holes where the food is.
 *
 * <p>The route follows the ground up and down: each column's tunnel block is one under its top ground block. Where the
 * ground steps up or down (at most {@link #MAX_STEP} blocks between columns) the tunnel climbs or drops under the
 * ground (see {@link #step}), always underground, never opening to the air. A column that cannot be dug (stone, water near, sand
 * over it, a cave) is gone round; if no route is found within {@code maxLength} blocks there is none. Pure route
 * finding (the world is asked through {@link Ground}), so it is unit tested.
 */
public final class TunnelRoute {
    /** The most the ground may step up or down between two columns the tunnel runs through. */
    static final int MAX_STEP = 2;
    /** How far (blocks, sideways from the food) the hole may be from it: next to it, or one further. */
    static final int HOLE_REACH = 2;
    /** The route search gives up after looking at this many columns. */
    private static final int MAX_COLUMNS = 6000;

    private TunnelRoute() {
    }

    /** What the route planner needs to know about the world. */
    public interface Ground {
        /** The height of the top ground block of this column (not counting plants on it), or null if it is not known. */
        @Nullable
        Integer groundTop(int x, int z);

        /** Whether a tunnel block may be dug here (soil, nothing that falls over it, no water next to it, not open). */
        boolean mayDig(BlockPos cell);

        /** Whether a hole may be opened here (the top ground block of a column: soil, open air above it). */
        boolean mayOpenHole(BlockPos hole);
    }

    /** A planned tunnel: its blocks in digging order (the first touches {@code start}) and the hole over its end. */
    public record Plan(List<BlockPos> cells, BlockPos hole) {
    }

    /** {@link #plan(List, BlockPos, Ground, int)} from one block. */
    @Nullable
    public static Plan plan(BlockPos start, BlockPos food, Ground ground, int maxLength) {
        return plan(List.of(start), food, ground, maxLength);
    }

    /**
     * A tunnel from one of {@code starts} (blocks of dug tunnels) to a hole within {@link #HOLE_REACH} of {@code food}
     * (sideways), at most {@code maxLength} blocks long; null if there is none. The shortest one is taken (each block a
     * step, climbing and dropping included), from whichever start gives it: the start nearest the food can be boxed in
     * (beside the mound, which is not dug through).
     */
    @Nullable
    public static Plan plan(List<BlockPos> starts, BlockPos food, Ground ground, int maxLength) {
        record Node(long column, int y, int cost, int guess) {
        }
        Map<Long, Integer> best = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Integer> heightOf = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Integer.compare(a.cost + a.guess, b.cost + b.guess));
        java.util.Set<Long> firsts = new java.util.HashSet<>();
        for (BlockPos start : starts) {
            long first = column(start.getX(), start.getZ());
            if (firsts.add(first)) {
                best.put(first, 0);
                heightOf.put(first, start.getY());
                open.add(new Node(first, start.getY(), 0, guess(start.getX(), start.getZ(), food)));
            }
        }
        int looked = 0;
        while (!open.isEmpty() && looked++ < MAX_COLUMNS) {
            Node node = open.poll();
            if (node.cost > best.getOrDefault(node.column, Integer.MAX_VALUE)) {
                continue;
            }
            int x = columnX(node.column);
            int z = columnZ(node.column);
            if (!firsts.contains(node.column) && isBesideFood(x, z, food)) {
                BlockPos hole = new BlockPos(x, node.y + 1, z);
                if (ground.mayOpenHole(hole)) {
                    return new Plan(route(firsts, node.column, cameFrom, heightOf), hole);
                }
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                int nx = x + side.getStepX();
                int nz = z + side.getStepZ();
                if (nx == food.getX() && nz == food.getZ()) {
                    continue; // not under the food itself
                }
                Integer top = ground.groundTop(nx, nz);
                if (top == null) {
                    continue;
                }
                int ny = top - 1;
                if (Math.abs(ny - node.y) > MAX_STEP) {
                    continue;
                }
                List<BlockPos> step = step(new BlockPos(x, node.y, z), new BlockPos(nx, ny, nz));
                boolean diggable = true;
                for (BlockPos cell : step) {
                    diggable &= ground.mayDig(cell);
                }
                int cost = node.cost + step.size();
                long next = column(nx, nz);
                if (!diggable || cost > maxLength || cost >= best.getOrDefault(next, Integer.MAX_VALUE) || firsts.contains(next)) {
                    continue;
                }
                best.put(next, cost);
                cameFrom.put(next, node.column);
                heightOf.put(next, ny);
                open.add(new Node(next, ny, cost, guess(nx, nz, food)));
            }
        }
        return null;
    }

    /**
     * The blocks dug to go from the tunnel block {@code from} into the next column's tunnel block {@code to}: on the
     * level, just {@code to}; up, the next column from {@code from}'s height up to {@code to}; down, from {@code from}
     * straight down to {@code to}'s height, then {@code to}. Each touches the one before, all underground.
     */
    static List<BlockPos> step(BlockPos from, BlockPos to) {
        List<BlockPos> cells = new ArrayList<>();
        if (to.getY() > from.getY()) {
            for (int y = from.getY(); y < to.getY(); y++) {
                cells.add(new BlockPos(to.getX(), y, to.getZ()));
            }
        } else {
            for (int y = from.getY() - 1; y >= to.getY(); y--) {
                cells.add(new BlockPos(from.getX(), y, from.getZ()));
            }
        }
        cells.add(to);
        return cells;
    }

    private static List<BlockPos> route(java.util.Set<Long> firsts, long last, Map<Long, Long> cameFrom, Map<Long, Integer> heightOf) {
        List<Long> columns = new ArrayList<>();
        long at = last;
        while (!firsts.contains(at)) {
            columns.add(0, at);
            at = cameFrom.get(at);
        }
        columns.add(0, at);
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 1; i < columns.size(); i++) {
            long a = columns.get(i - 1);
            long b = columns.get(i);
            cells.addAll(step(new BlockPos(columnX(a), heightOf.get(a), columnZ(a)), new BlockPos(columnX(b), heightOf.get(b), columnZ(b))));
        }
        return cells;
    }

    private static boolean isBesideFood(int x, int z, BlockPos food) {
        int dx = Math.abs(x - food.getX());
        int dz = Math.abs(z - food.getZ());
        return Math.max(dx, dz) <= HOLE_REACH && (dx != 0 || dz != 0);
    }

    private static int guess(int x, int z, BlockPos food) {
        return Math.max(0, Math.abs(x - food.getX()) + Math.abs(z - food.getZ()) - HOLE_REACH);
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int columnX(long column) {
        return (int) (column >> 32);
    }

    private static int columnZ(long column) {
        return (int) column;
    }
}
