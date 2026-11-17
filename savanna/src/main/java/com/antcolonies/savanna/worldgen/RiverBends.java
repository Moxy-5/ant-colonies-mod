package com.antcolonies.savanna.worldgen;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * How a savanna river bends, read from the land's own noise (sand banks, 2026-11-16, {@code docs/plans/sand-banks.md}).
 *
 * <p>Our rivers run where Minecraft's "weirdness" noise crosses zero: the land recipe ({@code tools/flat_savannas/
 * flat_offset.py}) cuts the channel and lays the floodplain by "peaks and valleys", which is the weirdness folded, so the
 * river's middle line is the line where weirdness is 0 and each bank is the land a little to one side of it. The bend of
 * that line, and which bank is the inside of it, can be worked out at any point from the noise alone: the same in every
 * chunk, whatever order chunks are made in, and as good on a river 66 blocks wide as on one 20 wide (the old 12-block
 * reading of the water round a column sees only water on broad rivers, and only a short piece of a wide river's bend).
 *
 * <p>The bend is the curvature of the weirdness' level line through the point (one over the bend's radius, in blocks),
 * from the noise's slopes measured {@link #H} blocks apart. A bank is the inside of a bend when the bend's middle lies on
 * its side of the river: real rivers drop their sand there, where the current slows.
 */
public final class RiverBends {
    /** How far apart the noise is read to measure its slopes, in blocks (the river's bends are hundreds of blocks long). */
    public static final int H = 8;

    private RiverBends() {
    }

    /**
     * One reading: the weirdness {@code w} at the point, its slope {@code gx, gz} (per block) and the curvature of its
     * level line (div of the slope's direction; positive when the line curves round the side of lower weirdness).
     */
    public record Reading(double w, double gx, double gz, double curvature) {
        /** How far the point is from the river's middle line, in blocks (negative on the side of negative weirdness). */
        public double offset() {
            double g = Math.hypot(gx, gz);
            return g < 1e-9 ? Double.MAX_VALUE : w / g;
        }

        /**
         * How sharply this point's side of the river is the inside of a bend: one over the bend's radius when it is (the
         * bend's middle on this side), negative when it is the outside, 0 on a straight reach.
         */
        public double inside() {
            return -Math.signum(w) * curvature;
        }
    }

    /** The weirdness noise of the world being made (from the server's own wiring of the land recipe). */
    public static DensityFunction weirdness(WorldGenLevel level) {
        return level.getLevel().getChunkSource().randomState().sampler().weirdness();
    }

    public static Reading read(DensityFunction weird, int x, int z) {
        return read(weird, x, z, H);
    }

    /** A reading with the slopes measured {@code H} blocks apart (wider smooths out the noise's small wobbles). */
    public static Reading read(DensityFunction weird, int x, int z, int H) {
        double c = at(weird, x, z);
        double e = at(weird, x + H, z), w = at(weird, x - H, z), n = at(weird, x, z - H), s = at(weird, x, z + H);
        double gx = (e - w) / (2.0 * H), gz = (s - n) / (2.0 * H);
        double xx = (e - 2 * c + w) / (H * H), zz = (s - 2 * c + n) / (H * H);
        double xz = (at(weird, x + H, z + H) - at(weird, x + H, z - H) - at(weird, x - H, z + H) + at(weird, x - H, z - H)) / (4.0 * H * H);
        double g2 = gx * gx + gz * gz;
        double curvature = g2 < 1e-18 ? 0 : (xx * gz * gz - 2 * gx * gz * xz + zz * gx * gx) / Math.pow(g2, 1.5);
        return new Reading(c, gx, gz, curvature);
    }

    static double at(DensityFunction weird, int x, int z) {
        return weird.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }
}
