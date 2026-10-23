package com.antcolonies.savanna.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.antcolonies.savanna.block.TermiteRampBlock;
import com.antcolonies.savanna.block.TermiteSpireBlock;
import com.antcolonies.savanna.species.SavannaNumbers.MoundNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * The shape of a termite mound at each stage of its growth (the cathedral mound, the user's design from the concept
 * pictures drawn by {@code tools/mound_concept.py}), and the galleries under it where the soil comes from. Pure
 * geometry, no world: positions are offsets from the entrance (0, 0, 0 is the entrance cell at ground level, right
 * above the nest shaft).
 *
 * <p>A mound is a heavy dome with buttresses (ridges running from high up down to the foot), a tall main spire with
 * vertical ridges and grooves (flutes) and a cluster of up to 6 lower spires of different heights, the same sums as the
 * concept. Every spire ends in a column of spire pieces narrowing to a point; the main spire and the two tallest side
 * spires end in a chimney mouth instead. Ramps cover the steps of the sides. A hollow shaft runs from the entrance up
 * the middle of the main spire (the nest shaft goes on up through it), closed at the top like a real mound. Stage
 * {@code h} of the profile's {@code max_height} stages is the mound grown to {@code h / max_height} (the full mound is
 * about {@code max_height} blocks tall); a young mound is a low dome with a small, stubby spire.
 *
 * <p>Every colony's mound is a little different ({@code seed}): the whole layout is turned, and the spires lean and the
 * buttresses run a little differently. Each stage's shape is worked out on its own; going from one stage to the next,
 * the termites build what is new and clear what is now hollow.
 *
 * <p>Inside, some of the soil is termite tunnel ({@link Part#TUNNEL}): the network the termites build the mound from
 * (tunnel plan step 4). It is worked out by growing the mound stage by stage with the termites' own building rules
 * ({@link MoundGrowth}), so every stage can be built from inside, and kept from stage to stage.
 */
public final class MoundPlan {
    /**
     * What a cell of the mound is: mound soil, hollow (the shaft), a chimney mouth, a spire piece, a ramp, or mound soil
     * with a termite tunnel through it (built as soil, then carved).
     */
    public enum Part {
        WALL, OPEN, MOUTH, SPIRE, RAMP, TUNNEL
    }

    /**
     * One stage of the mound: what every cell is, how thick each spire piece is, which way each ramp rises and the
     * corner it takes, and the age its soil has when the whole mound is put up at once (old and pale low down, in
     * streaks that run down the mound like rain, with patches of fresh soil).
     */
    public record Shape(Map<BlockPos, Part> parts, Map<BlockPos, Integer> thickness, Map<BlockPos, Direction> facing,
            Map<BlockPos, StairsShape> corner, Map<BlockPos, Integer> age) {
    }

    /** How far out (blocks) the mound can reach from its middle. */
    static final int REACH = 13;
    /** A spire is solid soil where it is at least this wide (from its middle); above that its pieces narrow it to a point. */
    private static final double SOLID_RADIUS = 0.75;
    /** The most spire pieces on top of a spire (and on a spire with a mouth: base, thick, then the mouth). */
    private static final int MOST_PIECES = 5;
    private static final int MOUTH_PIECES = 3;

    /** The side spires: angle round the middle, distance, height as a share of the mound's, base radius, grows from. */
    private static final double[][] SIDE_SPIRES = {
            {0.35, 3.0, 0.74, 2.1, 0.3}, {2.25, 3.3, 0.58, 1.9, 0.45}, {3.95, 2.9, 0.84, 2.2, 0.55},
            {5.15, 3.6, 0.47, 1.7, 0.7}, {1.35, 4.6, 0.36, 1.5, 0.85}, {2.95, 5.0, 0.3, 1.3, 0.92}};
    /** Side spires at least this tall (share of the mound) get a chimney mouth: the two tallest. */
    private static final double MOUTH_SHARE = 0.7;
    private static final int MOST_BUTTRESSES = 9;

    private MoundPlan() {
    }

    /**
     * The mound at stage {@code h} (1 to the profile's {@code max_height}) of the colony with this {@code seed}, with its
     * tunnels (see {@link #growth}).
     */
    public static Shape stage(int h, MoundNumbers numbers, long seed) {
        List<Shape> stages = growth(numbers, seed);
        return stages.get(Math.max(1, Math.min(h, stages.size() - 1)));
    }

    /** The shape alone at stage {@code h}: no tunnels yet (they come from growing it, see {@link #growth}). */
    public static Shape shapeOf(int h, MoundNumbers numbers, long seed) {
        return new Builder(h, numbers.maxHeight(), seed).build();
    }

    /** The grown mounds of the last few colonies asked for (growing one takes a moment). */
    private static final Map<String, List<Shape>> GROWN = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Shape>> eldest) {
            return size() > 8;
        }
    };

    /**
     * Every stage of the mound of the colony with this seed, with its tunnels: index {@code h} is stage {@code h}
     * (index 0 is unused). Grown once with the termites' building rules ({@link MoundGrowth#grow}) and kept.
     */
    public static synchronized List<Shape> growth(MoundNumbers numbers, long seed) {
        String key = numbers.maxHeight() + "/" + seed;
        List<Shape> stages = GROWN.get(key);
        if (stages == null) {
            stages = MoundGrowth.grow(numbers.maxHeight(), h -> shapeOf(h, numbers, seed));
            GROWN.put(key, stages);
        }
        return stages;
    }

    /** How many blocks of mound soil a stage has (plain soil and soil with a tunnel through it). */
    public static int walls(Shape stage) {
        int walls = 0;
        for (Part part : stage.parts().values()) {
            if (part == Part.WALL || part == Part.TUNNEL) {
                walls++;
            }
        }
        return walls;
    }

    // ---- the shape ----

    /** A spire: where it stands, how tall and wide, how it leans, and its flutes. */
    private record Spire(double x, double z, double h, double r, double leanX, double leanZ, double lean, double power,
            boolean mouth, int flutes, double phase) {
        /** The middle of the spire at height y (it leans more the higher up). */
        double[] centre(double y) {
            double k = lean * Math.pow(Math.max(0, y) / Math.max(h, 1), 1.6) * h;
            return new double[] {x + leanX * k, z + leanZ * k};
        }

        double radius(double y) {
            return y >= h ? 0 : r * Math.pow(1 - y / h, power);
        }
    }

    /** Smooth random bumps (value noise), the same as the concept's. */
    private static final class Noise {
        private static final int N = 32;
        private final double[] v = new double[N * N * N];

        Noise(long seed) {
            Random random = new Random(seed);
            for (int i = 0; i < v.length; i++) {
                v[i] = random.nextDouble();
            }
        }

        private double g(int a, int b, int c) {
            return v[(Math.floorMod(a, N) * N + Math.floorMod(b, N)) * N + Math.floorMod(c, N)];
        }

        double at(double x, double y, double z) {
            int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
            double fx = smooth(x - xi), fy = smooth(y - yi), fz = smooth(z - zi);
            double x0 = g(xi, yi, zi) + (g(xi + 1, yi, zi) - g(xi, yi, zi)) * fx;
            double x1 = g(xi, yi + 1, zi) + (g(xi + 1, yi + 1, zi) - g(xi, yi + 1, zi)) * fx;
            double x2 = g(xi, yi, zi + 1) + (g(xi + 1, yi, zi + 1) - g(xi, yi, zi + 1)) * fx;
            double x3 = g(xi, yi + 1, zi + 1) + (g(xi + 1, yi + 1, zi + 1) - g(xi, yi + 1, zi + 1)) * fx;
            double y0 = x0 + (x1 - x0) * fy;
            double y1 = x2 + (x3 - x2) * fy;
            return y0 + (y1 - y0) * fz;
        }

        private static double smooth(double t) {
            return t * t * (3 - 2 * t);
        }
    }

    private static final class Builder {
        private final double height;
        private final int top;
        private final Noise bumps;
        private final Noise streaks;
        private final Noise patches;
        private final List<Spire> spires = new ArrayList<>();
        private final double domeRadius;
        private final double domeHeight;
        /** Buttresses: {direction x, direction z, length, height at the middle}. */
        private final List<double[]> buttresses = new ArrayList<>();

        private final Set<BlockPos> full = new HashSet<>();
        private final Map<BlockPos, Part> parts = new LinkedHashMap<>();
        private final Map<BlockPos, Integer> thickness = new HashMap<>();
        private final Map<BlockPos, Direction> facing = new HashMap<>();

        Builder(int h, int stages, long seed) {
            double g = Math.max(1.0 / stages, Math.min(1.0, (double) h / stages));
            double sg = Math.sqrt(g);
            Random random = new Random(mix(seed));
            // Everything random is drawn first, the same at every stage, so the mound keeps its look as it grows.
            double turn = random.nextDouble() * 2 * Math.PI;
            double mainLean = random.nextDouble() * 2 * Math.PI;
            double[][] sideRandom = new double[SIDE_SPIRES.length][];
            for (int i = 0; i < sideRandom.length; i++) {
                sideRandom[i] = new double[] {uniform(random, -0.4, 0.4), uniform(random, 0, 6.3)};
            }
            double[][] buttressRandom = new double[MOST_BUTTRESSES][];
            for (int i = 0; i < MOST_BUTTRESSES; i++) {
                buttressRandom[i] = new double[] {uniform(random, -0.15, 0.15), uniform(random, -1, 1.2), uniform(random, 0.38, 0.55)};
            }
            bumps = new Noise(random.nextLong());
            streaks = new Noise(random.nextLong());
            patches = new Noise(random.nextLong());

            height = 4 + (stages - 4) * g;
            top = stages + 4;
            // A young mound's spire is small: a short stub on a low dome, catching up by about half-grown.
            double mainHeight = height * Math.min(1.0, 0.15 + g * 1.9);
            spires.add(new Spire(0, 0, mainHeight, 3.6 * sg + 0.6, Math.cos(mainLean), Math.sin(mainLean), 0.04,
                    g > 0.3 ? 0.8 : 1.4, true, 7, 0));
            for (int i = 0; i < SIDE_SPIRES.length; i++) {
                double[] s = SIDE_SPIRES[i];
                double angle = s[0] + turn;
                double share = s[2];
                if (g < s[4]) {
                    continue;
                }
                double grow = Math.min(1.0, (g - s[4]) / 0.25 + 0.35);
                double d = s[1] * sg;
                double lean = angle + sideRandom[i][0];
                spires.add(new Spire(Math.cos(angle) * d, Math.sin(angle) * d, height * share * grow, s[3] * sg * (0.6 + 0.4 * grow),
                        Math.cos(lean), Math.sin(lean), 0.05, 0.85, share > MOUTH_SHARE, 5, sideRandom[i][1]));
            }
            domeRadius = 6.8 * sg + 0.8;
            domeHeight = 5.5 * sg + 1.0;
            int count = g > 0.5 ? MOST_BUTTRESSES : g > 0.25 ? 6 : 0;
            for (int k = 0; k < count; k++) {
                double angle = k * 2 * Math.PI / count + turn + buttressRandom[k][0];
                buttresses.add(new double[] {Math.cos(angle), Math.sin(angle), (8.5 + buttressRandom[k][1]) * sg,
                        height * buttressRandom[k][2]});
            }
        }

        /** Spreads out seeds that are close together (java.util.Random starts nearly the same for them). */
        private static long mix(long seed) {
            long z = seed + 0x9E3779B97F4A7C15L;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        private static double uniform(Random random, double from, double to) {
            return from + (to - from) * random.nextDouble();
        }

        /** Is the middle of cell (x, y, z) inside the mound (the spires' thin tops are left to the spire pieces)? */
        private boolean solid(int x, int y, int z) {
            double cx = x, cy = y + 0.5, cz = z;
            double bump = (bumps.at(cx * 0.35, cy * 0.25, cz * 0.35) - 0.5) * 0.9;
            if (cy < domeHeight) {
                double r = domeRadius * Math.pow(1 - cy / domeHeight, 1.3);
                if (Math.hypot(cx, cz) < r + bump) {
                    return true;
                }
            }
            for (Spire s : spires) {
                double r = s.radius(cy);
                if (r < SOLID_RADIUS) {
                    continue;
                }
                double[] c = s.centre(cy);
                if (r > 1.1) { // vertical flutes: ridges and grooves running up the spire
                    r *= 1 + 0.24 * Math.cos(s.flutes * Math.atan2(cz - c[1], cx - c[0]) + s.phase);
                }
                if (Math.hypot(cx - c[0], cz - c[1]) < r + bump * 0.6) {
                    return true;
                }
            }
            for (double[] b : buttresses) {
                double along = cx * b[0] + cz * b[1];
                if (along < 0 || along > b[2]) {
                    continue;
                }
                double across = Math.abs(-cx * b[1] + cz * b[0]);
                if (across > 0.62 + 0.25 * (1 - along / b[2])) {
                    continue;
                }
                if (cy < b[3] * Math.pow(1 - along / b[2], 1.5) + bump) {
                    return true;
                }
            }
            return false;
        }

        Shape build() {
            for (int y = 0; y < top; y++) {
                for (int x = -REACH; x <= REACH; x++) {
                    for (int z = -REACH; z <= REACH; z++) {
                        if (solid(x, y, z)) {
                            full.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
            addShaft();
            dropHangingSoil();
            fillDiagonalGaps();
            for (Spire spire : spires) {
                addSpireTop(spire);
            }
            for (BlockPos pos : full) {
                parts.putIfAbsent(pos, Part.WALL);
            }
            addRamps();
            Map<BlockPos, StairsShape> corners = new HashMap<>();
            facing.forEach((pos, high) -> corners.put(pos, TermiteRampBlock.shapeFor(high, side -> facing.get(pos.relative(side)))));
            Map<BlockPos, Integer> ages = new HashMap<>();
            parts.forEach((pos, part) -> {
                if (part != Part.OPEN) {
                    ages.put(pos, ageAt(pos));
                }
            });
            return new Shape(parts, thickness, facing, corners, ages);
        }

        /** The spire pieces (and mouth) stacked straight up on the spire's top block of soil, narrowing to a point. */
        private void addSpireTop(Spire spire) {
            int y = 0;
            while (y < top && spire.radius(y + 0.5) >= SOLID_RADIUS) {
                y++;
            }
            if (y == 0) {
                return; // too small to have any soil yet
            }
            double[] c = spire.centre(y - 0.5);
            BlockPos best = null;
            double bestDistance = 0;
            for (int bx = (int) Math.round(c[0]) - 1; bx <= Math.round(c[0]) + 1; bx++) {
                for (int bz = (int) Math.round(c[1]) - 1; bz <= Math.round(c[1]) + 1; bz++) {
                    if (full.contains(new BlockPos(bx, y - 1, bz))) {
                        double d = Math.hypot(bx - c[0], bz - c[1]);
                        if (best == null || d < bestDistance) {
                            best = new BlockPos(bx, y - 1, bz);
                            bestDistance = d;
                        }
                    }
                }
            }
            if (best == null) {
                return;
            }
            while (full.contains(best.above())) {
                best = best.above(); // the dome or a bump reaches higher here: the pieces go on top of it
            }
            int n = 0;
            while (spire.radius(y + n) > 0.05 && y + n < top) {
                n++;
            }
            n = Math.max(spire.r < 2 ? 2 : 3, Math.min(6, n + 1));
            int pieces = spire.mouth ? MOUTH_PIECES : Math.min(n, MOST_PIECES);
            for (int i = 0; i < pieces; i++) {
                BlockPos pos = best.above(1 + i);
                if (full.contains(pos) || parts.containsKey(pos)) {
                    break;
                }
                if (spire.mouth && i == pieces - 1) {
                    parts.put(pos, Part.MOUTH);
                } else {
                    parts.put(pos, Part.SPIRE);
                    thickness.put(pos, spire.mouth ? i : pieceThickness(i, pieces));
                }
            }
        }

        /**
         * How thick piece {@code i} of a column of {@code pieces} is: the piece whose foot is nearest the width the
         * concept gives it there (narrowing evenly from a full block to a point), the last one the tip.
         */
        private static int pieceThickness(int i, int pieces) {
            if (i == pieces - 1) {
                return TermiteSpireBlock.TIP;
            }
            double wanted = 16 * (1 - 7.0 * i / (8 * pieces));
            int best = TermiteSpireBlock.BASE;
            for (int t = TermiteSpireBlock.BASE; t < TermiteSpireBlock.TIP; t++) {
                if (Math.abs(TermiteSpireBlock.bottomWidth(t) - wanted) < Math.abs(TermiteSpireBlock.bottomWidth(best) - wanted)) {
                    best = t;
                }
            }
            return best;
        }

        /**
         * The hollow shaft from the entrance up the middle of the main spire, following it as it leans (a step sideways
         * at most one block at a time). It goes up as long as every cell of it has soil on all sides and over it, so it is
         * always closed at the top. The entrance cell itself is always open.
         */
        private void addShaft() {
            Spire main = spires.get(0);
            BlockPos at = BlockPos.ZERO;
            full.remove(at);
            parts.put(at, Part.OPEN);
            for (int y = 1; y < top; y++) {
                List<BlockPos> level = new ArrayList<>();
                BlockPos up = at.above();
                level.add(up);
                double[] c = main.centre(y + 0.5);
                int dx = (int) Math.round(c[0]) - up.getX();
                int dz = (int) Math.round(c[1]) - up.getZ();
                if (dx != 0 || dz != 0) {
                    BlockPos step = Math.abs(dx) >= Math.abs(dz) ? up.offset(Integer.signum(dx), 0, 0) : up.offset(0, 0, Integer.signum(dz));
                    level.add(step);
                }
                for (BlockPos cell : level) {
                    if (!enclosed(cell, level)) {
                        return;
                    }
                }
                for (BlockPos cell : level) {
                    full.remove(cell);
                    parts.put(cell, Part.OPEN);
                }
                at = level.get(level.size() - 1);
            }
        }

        /**
         * Takes away soil that would hang in the air with nothing under it or beside it (over the open entrance of a
         * mound too small for a shaft, or a lone bump), from the bottom up.
         */
        private void dropHangingSoil() {
            List<BlockPos> cells = new ArrayList<>(full);
            cells.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY()) : a.compareTo(b));
            for (BlockPos cell : cells) {
                if (cell.getY() == 0 || full.contains(cell.below())) {
                    continue;
                }
                boolean beside = false;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    beside |= full.contains(cell.relative(side));
                }
                if (!beside) {
                    full.remove(cell);
                }
            }
        }

        /**
         * Where two blocks of soil in a layer only touch at an edge (a buttress running diagonally), one of the two cells
         * between them is filled (the one nearer the middle), so the soil is joined side to side: a tunnel can then reach
         * every block of it from inside (see {@link MoundGrowth}). The buttress becomes a little staircase.
         */
        private void fillDiagonalGaps() {
            List<BlockPos> cells = new ArrayList<>(full);
            Collections.sort(cells);
            for (BlockPos cell : cells) {
                for (int dx : new int[] {1, -1}) {
                    for (int dz : new int[] {1, -1}) {
                        BlockPos a = cell.offset(dx, 0, 0);
                        BlockPos b = cell.offset(0, 0, dz);
                        if (full.contains(cell.offset(dx, 0, dz)) && !full.contains(a) && !full.contains(b)) {
                            BlockPos fill = Math.hypot(a.getX(), a.getZ()) <= Math.hypot(b.getX(), b.getZ()) ? a : b;
                            if (parts.containsKey(fill)) {
                                fill = fill == a ? b : a; // not into the shaft
                            }
                            if (!parts.containsKey(fill)) {
                                full.add(fill);
                            }
                        }
                    }
                }
            }
        }

        /**
         * A shaft cell must be soil now, with soil on every side (but the rest of the shaft) and over it, and the soil
         * over it must have soil beside it (a roof can only be built out from the walls round it).
         */
        private boolean enclosed(BlockPos cell, List<BlockPos> shaft) {
            if (!full.contains(cell) || !full.contains(cell.above())) {
                return false;
            }
            boolean roofHeld = false;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = cell.relative(side);
                if (!shaft.contains(beside) && !full.contains(beside)) {
                    return false;
                }
                BlockPos roofSide = cell.above().relative(side);
                roofHeld |= !shaft.contains(roofSide.below()) && full.contains(roofSide);
            }
            return roofHeld;
        }

        /**
         * Ramps over the steps of the sides: an empty cell on a block of soil, with soil beside it at its own height,
         * rises toward that soil (or toward the corner, where soil only stands diagonally beside it). Its corner comes
         * from the ramps beside it, as with stairs. A cell with soil on 3 or 4 sides is a dent: it is filled with soil.
         */
        private void addRamps() {
            List<BlockPos> bases = new ArrayList<>(full);
            Collections.sort(bases); // the same order every time
            Direction[] order = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
            for (BlockPos base : bases) {
                BlockPos cell = base.above();
                if (parts.containsKey(cell)) {
                    continue;
                }
                List<Direction> sides = new ArrayList<>();
                for (Direction side : order) {
                    if (full.contains(cell.relative(side))) {
                        sides.add(side);
                    }
                }
                if (sides.size() >= 3) {
                    parts.put(cell, Part.WALL);
                    continue;
                }
                Direction high = sides.isEmpty() ? null : sides.get(0);
                if (high == null) {
                    for (int dx : new int[] {1, -1}) {
                        for (int dz : new int[] {1, -1}) {
                            if (high == null && full.contains(cell.offset(dx, 0, dz)) && full.contains(base.offset(dx, 0, 0))
                                    && full.contains(base.offset(0, 0, dz))) {
                                high = dx > 0 ? Direction.EAST : Direction.WEST;
                            }
                        }
                    }
                }
                if (high != null) {
                    parts.put(cell, Part.RAMP);
                    facing.put(cell, high);
                }
            }
        }

        /**
         * The age (0 fresh to 3 old) of the soil in a cell when the whole mound is put up at once: older and paler low
         * down, but in streaks that run down the mound (rain) and with patches of fresh soil where termites build, as in
         * the concept pictures.
         */
        private int ageAt(BlockPos pos) {
            double x = pos.getX() * 8 + 4, y = pos.getY() * 8 + 4, z = pos.getZ() * 8 + 4;
            double t = y / (height * 8);
            double streak = streaks.at(x / 1.6, y / 40.0, z / 1.6) - 0.5;
            double patch = patches.at(x / 18.0, y / 14.0, z / 18.0);
            double a = t + streak * 0.9 - 0.1;
            if (patch > 0.74) {
                a += (patch - 0.74) * 3.0;
            }
            return a < 0.19 ? 3 : a < 0.54 ? 2 : a < 0.85 ? 1 : 0;
        }
    }

    // ---- the galleries ----

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
