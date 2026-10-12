package com.antcolonies.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.antcolonies.species.SpeciesProfile.MoundNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The shape of a termite mound at each stage of its growth, and the galleries under it where the soil comes from.
 * Pure geometry, no world: positions are offsets from the entrance (0, 0, 0 is the entrance cell at ground level, right
 * above the nest shaft).
 *
 * <p>Stage {@code h} is a mound {@code h} blocks tall: a round body that is wide at the bottom and narrows quickly into
 * a tall spire (like a real Macrotermes mound), with a hollow chimney shaft up its middle (the nest shaft goes on up
 * through it) capped by a chimney block (the hole block) at the top. From stage {@link #FIRST_CHIMNEY_AT} on, side
 * chimneys rise from the slope, {@link #CHIMNEY_DISTANCE} blocks out, each with its own shaft and cap; a tunnel at
 * ground level joins every side shaft to the middle one and goes on out to an entrance (a hole block) at the foot of
 * the mound. Each stage is worked out on its own; going from one stage to the next, the termites build what is new,
 * clear what is now hollow (the old cap of a chimney that grew, a tunnel cut through the base) and shape the new caps.
 *
 * <p>A block over a tunnel has no floor under it: it is built out from the blocks beside it (see
 * {@code TermiteMoundBlock.addLayer}).
 */
public final class MoundPlan {
    /** What a cell of the mound is: solid mound soil, a chimney block (open only for termites), or hollow (a shaft or tunnel). */
    public enum Part {
        WALL, HOLE, OPEN
    }

    /** The narrowest the mound gets (from the middle of a shaft): a ring of 8 blocks round it, one block thick. */
    static final double TOP_RADIUS = 1.5;
    /** How wide the base of a side chimney is (from its shaft). */
    static final double CHIMNEY_BASE_RADIUS = 2.5;
    /** How far (in blocks) a side chimney's shaft is from the middle of the mound. */
    public static final int CHIMNEY_DISTANCE = 4;
    /** The stage at which the first side chimney starts; each next one starts {@link #CHIMNEY_EVERY} stages later. */
    public static final int FIRST_CHIMNEY_AT = 8;
    static final int CHIMNEY_EVERY = 3;
    /** How much the base widens with each stage (blocks), until it is as wide as the profile allows. */
    static final double BASE_GROWTH = 0.25;

    private MoundPlan() {
    }

    /**
     * The mound at stage {@code h} (1 to the profile's max height). The side chimneys stand toward {@code first}, then
     * clockwise of it, then opposite it (more than 3 side chimneys are not supported).
     */
    public static Map<BlockPos, Part> stage(int h, MoundNumbers numbers, Direction first) {
        Map<BlockPos, Part> parts = new LinkedHashMap<>();
        double base = baseRadius(h, numbers);
        for (int dy = 0; dy < h; dy++) {
            ring(parts, BlockPos.ZERO, dy, radius(base, dy, h));
        }
        List<Direction> sides = chimneyDirections(first, numbers.sideChimneys());
        List<int[]> chimneys = new ArrayList<>(); // {direction index, height}
        for (int i = 0; i < sides.size(); i++) {
            int start = FIRST_CHIMNEY_AT + CHIMNEY_EVERY * i;
            if (h < start) {
                continue;
            }
            int bodyTop = 0;
            while (bodyTop < h && radius(base, bodyTop, h) >= CHIMNEY_DISTANCE) {
                bodyTop++;
            }
            int extra = Math.max(1, Math.min(numbers.sideChimneyHeight() - 2 * i, 1 + (int) ((h - start) * 0.6)));
            int height = Math.max(2, bodyTop + extra);
            BlockPos shaft = BlockPos.ZERO.relative(sides.get(i), CHIMNEY_DISTANCE);
            for (int dy = 0; dy < height; dy++) {
                ring(parts, shaft, dy, radius(CHIMNEY_BASE_RADIUS, dy, height));
            }
            chimneys.add(new int[] {i, height});
        }
        // The hollow parts go over the walls: the middle shaft and its cap.
        for (int dy = 0; dy < h - 1; dy++) {
            parts.put(new BlockPos(0, dy, 0), Part.OPEN);
        }
        parts.put(new BlockPos(0, h - 1, 0), Part.HOLE);
        for (int[] chimney : chimneys) {
            Direction side = sides.get(chimney[0]);
            int height = chimney[1];
            BlockPos shaft = BlockPos.ZERO.relative(side, CHIMNEY_DISTANCE);
            for (int dy = 0; dy < height - 1; dy++) {
                parts.put(shaft.above(dy), Part.OPEN);
            }
            parts.put(shaft.above(height - 1), Part.HOLE);
            // The tunnel from the middle shaft to the side shaft, and on out to an entrance at the foot of the mound.
            for (int k = 1; k < CHIMNEY_DISTANCE; k++) {
                parts.put(BlockPos.ZERO.relative(side, k), Part.OPEN);
            }
            int edge = CHIMNEY_DISTANCE;
            while (parts.get(BlockPos.ZERO.relative(side, edge + 1)) == Part.WALL) {
                edge++;
            }
            for (int k = CHIMNEY_DISTANCE + 1; k < edge; k++) {
                parts.put(BlockPos.ZERO.relative(side, k), Part.OPEN);
            }
            if (edge > CHIMNEY_DISTANCE) {
                parts.put(BlockPos.ZERO.relative(side, edge), Part.HOLE);
            }
        }
        return parts;
    }

    /** How many blocks of mound soil stage {@code h} has. */
    public static int walls(Map<BlockPos, Part> stage) {
        int walls = 0;
        for (Part part : stage.values()) {
            if (part == Part.WALL) {
                walls++;
            }
        }
        return walls;
    }

    /** The sides the chimneys stand toward, in the order they are built. */
    static List<Direction> chimneyDirections(Direction first, int count) {
        List<Direction> all = List.of(first, first.getClockWise(), first.getOpposite(), first.getCounterClockWise());
        return all.subList(0, Math.max(0, Math.min(count, all.size())));
    }

    /** How wide (from the middle) the base of stage {@code h} is. */
    static double baseRadius(int h, MoundNumbers numbers) {
        return Math.min(numbers.baseRadius(), TOP_RADIUS + BASE_GROWTH * h);
    }

    /** How wide a body {@code height} tall with this base is at {@code dy} above the ground: wide at the foot, narrowing fast into a spire. */
    static double radius(double base, int dy, int height) {
        double rest = 1.0 - (double) dy / height;
        return TOP_RADIUS + (base - TOP_RADIUS) * Math.pow(rest, 1.5);
    }

    /** Marks every cell within {@code radius} of {@code middle} (sideways) at height {@code dy} as wall. */
    private static void ring(Map<BlockPos, Part> parts, BlockPos middle, int dy, double radius) {
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    parts.putIfAbsent(new BlockPos(middle.getX() + dx, dy, middle.getZ() + dz), Part.WALL);
                }
            }
        }
    }

    /**
     * The galleries under the mound, as offsets from the bottom of the nest shaft, in the order they are dug (each one
     * touches one dug before it): a shaft straight down from the bottom of the nest shaft, and {@code levels} levels
     * below the nest, 2 blocks apart (a 1-block gallery, a 1-block floor of soil), each with straight galleries out
     * along the four directions and square rings round the shaft at every other distance (3, 5, 7, ...) up to
     * {@code radius} blocks out, so a 1-block wall stands between two rings.
     */
    public static List<BlockPos> galleries(int levels, int radius) {
        Set<BlockPos> cells = new HashSet<>();
        for (int i = 1; i <= 2 * levels; i++) {
            cells.add(new BlockPos(0, -i, 0));
        }
        for (int level = 1; level <= levels; level++) {
            int y = -2 * level;
            for (int k = 1; k <= radius; k++) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    cells.add(new BlockPos(side.getStepX() * k, y, side.getStepZ() * k));
                }
            }
            for (int ring = 3; ring <= radius; ring += 2) {
                for (int a = -ring; a <= ring; a++) {
                    cells.add(new BlockPos(a, y, -ring));
                    cells.add(new BlockPos(a, y, ring));
                    cells.add(new BlockPos(-ring, y, a));
                    cells.add(new BlockPos(ring, y, a));
                }
            }
        }
        // Dug in the order they are reached from the shaft bottom, nearest first.
        List<BlockPos> order = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(BlockPos.ZERO);
        seen.add(BlockPos.ZERO);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            List<BlockPos> next = new ArrayList<>();
            for (Direction direction : Direction.values()) {
                BlockPos n = at.relative(direction);
                if (cells.contains(n) && seen.add(n)) {
                    next.add(n);
                }
            }
            Collections.sort(next); // the same order every time
            for (BlockPos n : next) {
                order.add(n);
                queue.add(n);
            }
        }
        return List.copyOf(order);
    }
}
