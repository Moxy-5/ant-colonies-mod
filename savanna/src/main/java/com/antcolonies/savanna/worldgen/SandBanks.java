package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.block.RiverSand;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Which river banks are sand (sand banks, 2026-11-16, {@code docs/plans/sand-banks.md}): the inside of the sharp bends,
 * some of them, about one bank every 80 to 120 blocks of river; everywhere else the banks stay silt and mud
 * ({@link RiverBankFeature}). Real rivers drop their sand on the inside of a bend, where the current slows; the outside is
 * cut steep, so a sand bank lies on one side only and the side swaps from bend to bend.
 *
 * <p>Worked out from the land's own noise alone ({@link RiverBends}), so every chunk agrees whatever order they are made in:
 * <ul>
 * <li>the bend: the curvature of the river's middle line read with the noise's slopes {@link #SPAN} blocks apart (step 1's
 *     measure: at 64 apart the reading agrees with the river's walked line over 64 blocks 94.5 times in 100), read at the
 *     chunk's corners and blended across it, so neighbours share the corners;</li>
 * <li>the side: the bank on the side of the bend's middle (the weirdness' sign at the column, one reading);</li>
 * <li>the pick: a smooth noise about {@link #PICK_CELL} blocks across keeps some of the sharp bends, so not every one is
 *     sand; where it or the bend fades the sand mixes into silt over a few blocks, as real bars grade from sand to silt.</li>
 * </ul>
 * The tone comes from how far inland (the sand viewer's three tones: red-ochre far inland, golden mid inland, pale near
 * the coast), blended over a long stretch.
 */
public final class SandBanks {
    /** How far apart the noise's slopes are read for the bend (step 1's pick, 64 blocks). */
    public static final int SPAN = 64;
    /** A bend sharper than this radius may be a sand bank (blocks; my pick from step 1's measure). */
    public static final double RADIUS = 190;
    /** The radius at which a bend is fully sand (sharper bends, wider bars). */
    public static final double FULL_RADIUS = 90;
    /** The pick's noise cell, in blocks (a whole bar shares its pick). */
    public static final double PICK_CELL = 48;
    /** The pick's noise must pass this (0 to 1) for sand; about nine sharp bends in ten keep it (my pick). */
    public static final double PICK = 0.1;
    /** How far inland, from Minecraft's continentalness: pale below the first, golden below the second, red above. */
    static final double[] TONE_FROM = {0.03, 0.3};
    /** The widest bank by how far inland (coast, near, mid, far inland), in blocks out from the water (my picks). */
    static final double[] WIDEST = {10, 9, 7, 6};
    /** The narrowest bank, the low strip's own width. */
    static final double NARROWEST = RiverBankFeature.STRIP;
    /** Pebbles on a bank and on the river bed, by how far inland (coast, near, mid, far): thicker far inland (my picks). */
    static final double[] BANK_PEBBLES = {0.01, 0.02, 0.04, 0.08};
    static final double[] BED_PEBBLES = {0.003, 0.006, 0.012, 0.025};
    /** Farther than this from the river's middle line (blocks) no column is sand (the bend of the noise far from a river means nothing). */
    static final double NEAR_LINE = 70;

    private SandBanks() {
    }

    /**
     * A column's sand: {@code strength} 0 (none) to 1 (the heart of a bank), the {@code tone} (0 red, 1 golden, 2 pale)
     * and how far inland {@code band} (0 coast to 3 far inland; -1 where nothing is known, so no pebbles either).
     */
    public record Choice(double strength, int tone, int band) {
        public static final Choice NONE = new Choice(0, 1, -1);

        /** How far out from the water this column's bank reaches when it is sand. */
        public double width() {
            double widest = WIDEST[Math.max(0, band)];
            return NARROWEST + (widest - NARROWEST) * Math.min(1, strength);
        }

        public RiverSand.Tone sand() {
            return switch (tone) {
                case 0 -> SavannaBlocks.RED_SAND;
                case 2 -> SavannaBlocks.PALE_SAND;
                default -> SavannaBlocks.GOLDEN_SAND;
            };
        }

        public double bankPebbles() {
            return band < 0 ? 0 : BANK_PEBBLES[band];
        }

        public double bedPebbles() {
            return band < 0 ? 0 : BED_PEBBLES[band];
        }
    }

    /** What decides each column's sand (the world's noise, or a game test's own rule). */
    public interface Chooser {
        Choice at(int x, int z);
    }

    /** No sand and no pebbles (the banks as before; game tests of the silt). */
    public static final Chooser NONE = (x, z) -> Choice.NONE;

    /** The chooser for one chunk of the world being made, from the land's noise. */
    public static Chooser forChunk(WorldGenLevel level, int x0, int z0) {
        var sampler = level.getLevel().getChunkSource().randomState().sampler();
        return new World(sampler.weirdness(), sampler.continentalness(), level.getSeed(), x0, z0);
    }

    /** The chooser over the land's noise for the chunk at {@code x0, z0} (a test can give its own noise). */
    public static Chooser over(DensityFunction weirdness, DensityFunction continentalness, long seed, int x0, int z0) {
        return new World(weirdness, continentalness, seed, x0, z0);
    }

    private static final class World implements Chooser {
        private final DensityFunction weird, cont;
        private final long seed;
        private final int x0, z0;
        private final double[] bend = new double[4], inland = new double[4], slope = new double[4];
        private final boolean anyBend;

        World(DensityFunction weird, DensityFunction cont, long seed, int x0, int z0) {
            this.weird = weird;
            this.cont = cont;
            this.seed = seed;
            this.x0 = x0;
            this.z0 = z0;
            boolean any = false;
            for (int c = 0; c < 4; c++) {
                int x = x0 + (c & 1) * 16, z = z0 + (c >> 1) * 16;
                RiverBends.Reading r = RiverBends.read(weird, x, z, SPAN);
                bend[c] = r.curvature();
                slope[c] = Math.hypot(r.gx(), r.gz());
                inland[c] = RiverBends.at(cont, x, z);
                any |= Math.abs(bend[c]) > 0.8 / RADIUS;
            }
            anyBend = any;
        }

        private double blend(double[] corners, int x, int z) {
            double u = Math.max(0, Math.min(1, (x - x0) / 16.0)), v = Math.max(0, Math.min(1, (z - z0) / 16.0));
            return corners[0] * (1 - u) * (1 - v) + corners[1] * u * (1 - v) + corners[2] * (1 - u) * v + corners[3] * u * v;
        }

        @Override
        public Choice at(int x, int z) {
            double c = blend(inland, x, z);
            int band = RiverZones.band(c);       // the river zones' one reading of how far inland
            double toned = c + 0.035 * (2 * noise(x / 40.0, z / 40.0, seed ^ 0x51DEL) - 1); // tones blend over a long stretch
            int tone = toned >= TONE_FROM[1] ? 0 : toned >= TONE_FROM[0] ? 1 : 2;
            if (!anyBend) {
                return new Choice(0, tone, band);
            }
            double k = blend(bend, x, z);
            if (Math.abs(k) < 1 / RADIUS) {
                return new Choice(0, tone, band);
            }
            double w = RiverBends.at(weird, x, z), g = blend(slope, x, z);
            double inside = -Math.signum(w) * k;
            if (inside < 1 / RADIUS || g < 1e-9 || Math.abs(w) / g > NEAR_LINE) {
                return new Choice(0, tone, band);
            }
            double pick = noise(x / PICK_CELL, z / PICK_CELL, seed ^ 0x5A4DBA4CL);
            double strength = smooth((inside - 1 / RADIUS) / (1 / FULL_RADIUS - 1 / RADIUS)) * smooth((pick - PICK) / 0.15);
            return new Choice(strength, tone, band);
        }
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Smooth value noise, 0 to 1, one hashed value per whole-number lattice point. */
    static double noise(double x, double z, long seed) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        double a = lattice(ix, iz, seed), b = lattice(ix + 1, iz, seed), c = lattice(ix, iz + 1, seed), d = lattice(ix + 1, iz + 1, seed);
        return (a * (1 - sx) + b * sx) * (1 - sz) + (c * (1 - sx) + d * sx) * sz;
    }

    private static double lattice(int x, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) / (double) (1L << 53);
    }
}
