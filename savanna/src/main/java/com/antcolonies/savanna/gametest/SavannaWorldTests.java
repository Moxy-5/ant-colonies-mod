package com.antcolonies.savanna.gametest;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.worldgen.SavannaRegion;
import com.antcolonies.savanna.worldgen.SavannaSpawn;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Big savannas in normal worlds (the user, 2026-11-06; {@code docs/DESIGN.md}, "Big savannas in normal worlds with
 * TerraBlender"): the overworld's biomes for a few seeds, read from the noise with TerraBlender's regions and ours,
 * measured against the user's target, and drawn ({@code biome-maps/} in the test server's folder: Minecraft's own map
 * on the left, with big savannas on the right, 16,384 blocks square, savanna in gold).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class SavannaWorldTests {
    static final long[] SEEDS = {-3635740773639857461L, 1L, 20261106L, 424242L};

    /**
     * The target, agreed by the user 2026-11-06: a typical savanna about 2,000 blocks across, the biggest about 4,000,
     * about as easy to find as a desert (a big one every 2,000 to 3,000 blocks flown). The band the maps must land in
     * (my pick, a little wider than the target, as each seed differs): typical 1,300 to 3,000 across, the biggest at
     * least 3,000, a big savanna (1,000 or more across) in every 4,500 blocks square of land or less, and savanna no
     * more than 30 percent of the land.
     */
    static final int TYPICAL_LEAST = 1500, TYPICAL_MOST = 3000, BIGGEST_LEAST = 3000, SPACING_MOST = 4500;
    static final double COVERAGE_MOST = 0.30;

    /**
     * The real thing: TerraBlender's layout with our region for each seed, measured and drawn beside Minecraft's own; then
     * the spawn search (spawn in the savanna) from the middle of each map finds a place well inside a savanna.
     * Its own batch: it holds the server some seconds reading the noise.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "savanna_maps")
    public static void bigSavannasHaveTheAgreedSizeAndRarity(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        MultiNoiseBiomeSource vanillaSource = MultiNoiseBiomeSource.createFromPreset(registries.lookupOrThrow(
                Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        List<Integer> typical = new ArrayList<>();
        int biggest = 0, big = 0, tooFar = 0;
        double landShare = 0;
        int land = 0;
        StringBuilder report = new StringBuilder();
        for (long seed : SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            byte[] ours = BiomeMaps.map(source, sampler);
            byte[] vanilla = BiomeMaps.pretendMap(registries, seed, BiomeMaps.climates(sampler), 0, SavannaRegion.Shape.VANILLA);
            BiomeMaps.Patches savanna = BiomeMaps.patches(ours, BiomeMaps.SAVANNA);
            BiomeMaps.Patches before = BiomeMaps.patches(vanilla, BiomeMaps.SAVANNA);
            BiomeMaps.Patches desert = BiomeMaps.patches(ours, BiomeMaps.DESERT);
            report.append(String.format("%n  seed %d: savanna %s%n    (Minecraft's own: %s; desert now: %s)", seed, savanna, before, desert));
            typical.add(savanna.typical());
            biggest = Math.max(biggest, savanna.biggest());
            big += savanna.bigOnes();
            for (byte b : ours) {
                if (b != BiomeMaps.OCEAN) {
                    land++;
                }
            }
            landShare += savanna.coverage();
            try {
                BiomeMaps.draw(FMLPaths.GAMEDIR.get().resolve("biome-maps").resolve("seed_" + seed + ".png"), vanilla, ours);
            } catch (java.io.IOException e) {
                SavannaMod.LOGGER.warn("[biome maps] could not draw seed {}: {}", seed, e.toString());
            }
            // the spawn search from the map's middle (Minecraft's spawn is near 0, 0)
            var found = source.findBiomeHorizontal(0, SavannaSpawn.SAMPLE_Y, 0, 6400, 32, b -> b.is(BiomeTags.IS_SAVANNA),
                    RandomSource.create(seed), true, sampler);
            if (found == null) {
                tooFar++;
                report.append("\n    spawn: no savanna within 6,400 blocks");
            } else {
                BlockPos inside = SavannaSpawn.deepInside(source, sampler, found.getFirst());
                int depth = SavannaSpawn.depth(source, sampler, inside);
                int dist = (int) Math.sqrt(inside.distSqr(BlockPos.ZERO.atY(inside.getY())));
                report.append(String.format("%n    spawn: nearest savanna %d blocks out, the spawn %d blocks out, %d blocks in from the edge",
                        (int) Math.sqrt(found.getFirst().distSqr(BlockPos.ZERO.atY(found.getFirst().getY()))), dist, depth));
                if (depth < SavannaSpawn.WANT_INSIDE / 2) {
                    tooFar++;
                }
            }
        }
        double area = land * (double) BiomeMaps.STEP * BiomeMaps.STEP;
        int spacing = big == 0 ? Integer.MAX_VALUE : (int) Math.sqrt(area / big);
        double coverage = landShare / SEEDS.length;
        typical.sort(Integer::compare);
        int middle = (typical.get(typical.size() / 2 - 1) + typical.get(typical.size() / 2)) / 2;
        String all = String.format("typical %d blocks across (seeds %s), biggest %d, a big one per %d blocks square, %.1f%% of land savanna%s",
                middle, typical, biggest, spacing, 100 * coverage, report);
        SavannaMod.LOGGER.info("[biome maps] big savannas: {}", all);
        helper.assertTrue(middle >= TYPICAL_LEAST && middle <= TYPICAL_MOST, "typical savanna size outside the band: " + all);
        helper.assertTrue(biggest >= BIGGEST_LEAST, "no savanna big enough: " + all);
        helper.assertTrue(spacing <= SPACING_MOST, "big savannas too rare: " + all);
        helper.assertTrue(coverage <= COVERAGE_MOST, "too much savanna: " + all);
        helper.assertTrue(tooFar == 0, "the spawn search found no place well inside a savanna for every seed: " + all);
        helper.succeed();
    }

    /**
     * How steep savanna ground is (the user, 2026-11-07: "the savanna has so many hills and climbs"), read straight from
     * the noise on {@link #GROUND_SEEDS}: at {@link #GROUND_SPOTS} random dry savanna spots of each seed, the climb over 8
     * blocks (the steeper of east and south) and the relief over 64 blocks (highest minus lowest of 9 spots 32 apart).
     * Real savanna rises about 1 block in 20 to 50 (a catena from crest to drainage line; kopjes and river banks the
     * exceptions), so over 8 blocks mostly 0 or 1. A picture of the ground round the spawn search's savanna spot goes to
     * {@code biome-maps/ground_seed_<seed>.png} (1,024 blocks square, hill-shaded, a darker line every 5 blocks of height),
     * the first seed's only, and one of a single-biome savanna world ({@code ground_single_biome_seed_1.png}); before and
     * after pictures in {@code references/flat_savannas/}. The flattening itself: {@code worldgen/FlatSavanna}.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "savanna_maps")
    public static void savannaGroundIsGentle(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        List<Integer> climbs = new ArrayList<>();
        List<Integer> reliefs = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        int hollow = 0;
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, helper.getLevel());
            byte[] map = BiomeMaps.map(source, sampler);
            List<Integer> cells = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                if (map[c] == BiomeMaps.SAVANNA) {
                    cells.add(c);
                }
            }
            java.util.Collections.shuffle(cells, new java.util.Random(seed));
            List<Integer> seedClimbs = new ArrayList<>();
            List<Integer> seedReliefs = new ArrayList<>();
            int[] seedHollow = {0};
            for (int c : cells) {
                if (seedClimbs.size() >= GROUND_SPOTS) {
                    break;
                }
                int x = -BiomeMaps.HALF + (c / BiomeMaps.SIDE) * BiomeMaps.STEP;
                int z = -BiomeMaps.HALF + (c % BiomeMaps.SIDE) * BiomeMaps.STEP;
                int h = ground.height(x, z);
                if (h <= SEA_TOP) {
                    continue; // a pond or river in the savanna
                }
                seedClimbs.add(Math.max(Math.abs(ground.height(x + 8, z) - h), Math.abs(ground.height(x, z + 8) - h)));
                int low = h, high = h;
                for (int dx = -32; dx <= 32; dx += 32) {
                    for (int dz = -32; dz <= 32; dz += 32) {
                        int k = ground.height(x + dx, z + dz);
                        low = Math.min(low, k);
                        high = Math.max(high, k);
                    }
                }
                seedReliefs.add(high - low);
                if (ground.hollowUnder(x, z, HOLLOW_DEPTH)) {
                    seedHollow[0]++;
                }
            }
            report.append(String.format("%n  seed %d: %s, %d with a cave or pit within %d blocks under the ground", seed,
                    groundLine(seedClimbs, seedReliefs), seedHollow[0], HOLLOW_DEPTH));
            hollow += seedHollow[0];
            climbs.addAll(seedClimbs);
            reliefs.addAll(seedReliefs);
            var found = seed != GROUND_SEEDS[0] ? null : source.findBiomeHorizontal(0, SavannaSpawn.SAMPLE_Y, 0, 6400, 32,
                    b -> b.is(BiomeTags.IS_SAVANNA), RandomSource.create(seed), true, sampler);
            if (found != null) { // one picture, the first seed's (each takes about two minutes)
                try {
                    drawGround(FMLPaths.GAMEDIR.get().resolve("biome-maps").resolve("ground_seed_" + seed + ".png"), ground,
                            SavannaSpawn.deepInside(source, sampler, found.getFirst()));
                } catch (java.io.IOException e) {
                    SavannaMod.LOGGER.warn("[biome maps] could not draw the ground of seed {}: {}", seed, e.toString());
                }
            }
        }
        // a single-biome savanna world (the user's test worlds) is flattened everywhere: its picture round the middle
        com.antcolonies.savanna.worldgen.FlatSavanna.setWholeWorld(true);
        try {
            net.minecraft.world.level.biome.BiomeSource single = new net.minecraft.world.level.biome.FixedBiomeSource(
                    registries.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA));
            drawGround(FMLPaths.GAMEDIR.get().resolve("biome-maps").resolve("ground_single_biome_seed_1.png"),
                    BiomeMaps.ground(registries, single, 1L, helper.getLevel()), BlockPos.ZERO);
        } catch (java.io.IOException e) {
            SavannaMod.LOGGER.warn("[biome maps] could not draw the single-biome ground: {}", e.toString());
        } finally {
            com.antcolonies.savanna.worldgen.FlatSavanna.setWholeWorld(false);
        }
        String all = groundLine(climbs, reliefs) + String.format(", %d with a cave or pit within %d blocks under", hollow, HOLLOW_DEPTH) + report;
        SavannaMod.LOGGER.info("[biome maps] savanna ground: {}", all);
        double steep = share(climbs, STEEP_CLIMB);
        double rugged = share(reliefs, RUGGED_RELIEF);
        helper.assertTrue(steep <= STEEP_MOST, "too much steep savanna ground: " + all);
        helper.assertTrue(rugged <= RUGGED_MOST, "too much rugged savanna ground: " + all);
        // the user, 2026-11-07: "random dips in the ground and ravines and caves, I want to get rid of all of that"
        helper.assertTrue(hollow <= HOLLOW_MOST * climbs.size(), "caves or pits under the savanna's ground: " + all);
        helper.succeed();
    }

    static final long[] GROUND_SEEDS = {SEEDS[0], SEEDS[1], SEEDS[2]};
    static final int GROUND_SPOTS = 200;
    /** The top of the sea: ground at or below it is under water. */
    static final int SEA_TOP = 62;
    /** A hillside: a climb of 3 or more over 8 blocks (about 1 in 2.7, 20 degrees). */
    static final int STEEP_CLIMB = 3;
    /** Rugged: 16 or more blocks between the highest and lowest of 64 blocks of ground. */
    static final int RUGGED_RELIEF = 16;
    /**
     * The most savanna allowed on a hillside or rugged (flat savannas, 2026-11-07; measured on 3 seeds, 600 spots): Minecraft's
     * own savanna land was 40 percent hillside and 61 percent rugged, the flat savannas 21 and 20 (what is left is mostly
     * river banks and coasts). The limits fail the old land by far and leave the new room for chance.
     */
    static final double STEEP_MOST = 0.30, RUGGED_MOST = 0.30;
    /**
     * Caves and pits: how deep under the ground is looked at, and the most spots allowed one (the user, 2026-11-07: no
     * dips, ravines or caves in the savanna). The edges of the climate mask may keep a few.
     */
    static final int HOLLOW_DEPTH = 16;
    static final double HOLLOW_MOST = 0.01;

    private static double share(List<Integer> values, int atLeast) {
        return values.isEmpty() ? 0 : values.stream().filter(v -> v >= atLeast).count() / (double) values.size();
    }

    private static int middle(List<Integer> values, double at) {
        if (values.isEmpty()) {
            return -1;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compare);
        return sorted.get(Math.min(sorted.size() - 1, (int) (at * sorted.size())));
    }

    private static String groundLine(List<Integer> climbs, List<Integer> reliefs) {
        return String.format("%d spots; climb over 8 blocks: median %d, 90%% at most %d, %.0f%% hillside (%d+); relief over 64 blocks: median %d, 90%% at most %d, %.0f%% rugged (%d+)",
                climbs.size(), middle(climbs, 0.5), middle(climbs, 0.9), 100 * share(climbs, STEEP_CLIMB), STEEP_CLIMB,
                middle(reliefs, 0.5), middle(reliefs, 0.9), 100 * share(reliefs, RUGGED_RELIEF), RUGGED_RELIEF);
    }

    /** A hill-shaded picture of the ground, 1,024 blocks square round a spot, a pixel every 4 blocks. */
    private static void drawGround(java.nio.file.Path file, BiomeMaps.Ground ground, BlockPos middle) throws java.io.IOException {
        int side = 256, step = 4;
        int[][] h = new int[side][side];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                h[i][j] = ground.height(middle.getX() + (i - side / 2) * step, middle.getZ() + (j - side / 2) * step);
            }
        }
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int here = h[i][j];
                if (here <= SEA_TOP) {
                    image.setRGB(i, j, 0x2f5fa8);
                    continue;
                }
                int west = h[Math.max(0, i - 1)][j], north = h[i][Math.max(0, j - 1)];
                double light = Math.max(0.35, Math.min(1.25, 0.85 + 0.09 * (west - here) + 0.09 * (north - here)));
                boolean contour = (here / 5) != (west / 5) || (here / 5) != (north / 5);
                double l = contour ? light * 0.8 : light;
                int r = (int) Math.min(255, 0xc8 * l), g = (int) Math.min(255, 0xae * l), b = (int) Math.min(255, 0x5a * l);
                image.setRGB(i, j, (r << 16) | (g << 8) | b);
            }
        }
        for (int x = 8; x < 8 + 100 / step; x++) { // a 100-block scale bar
            image.setRGB(x, side - 8, 0);
            image.setRGB(x, side - 9, 0);
        }
        java.nio.file.Files.createDirectories(file.getParent());
        javax.imageio.ImageIO.write(image, "png", file.toFile());
    }

    /**
     * Tuning (only when named: {@code ANTCOLONIES_TESTS=stresssavannashapes}): pretend layouts for several region shares and
     * table shapes, measured on the same seeds, to pick {@link SavannaRegion#WEIGHT} and {@link SavannaRegion#SHAPE}.
     * The share is ours of all zones: vanilla's weight is 10, so weight 30 alone is 0.75; Biomes O' Plenty's regions
     * (about 20 more) bring it to about 0.5.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "savanna_maps")
    public static void stressSavannaShapes(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        double[] shares = {0.5, 0.67, 0.75, 0.86, 1.0};
        List<SavannaRegion.Shape> shapes = List.of(SavannaRegion.Shape.VANILLA, new SavannaRegion.Shape(0.2F, 0.55F, 0.1F),
                new SavannaRegion.Shape(0.1F, 0.6F, 0.15F), new SavannaRegion.Shape(0.05F, 0.6F, 0.3F), new SavannaRegion.Shape(0.15F, 0.6F, 0.2F),
                new SavannaRegion.Shape(0.0F, 0.65F, 0.3F));
        List<Pair<Long, Climate.TargetPoint[]>> climates = new ArrayList<>();
        for (long seed : SEEDS) {
            climates.add(Pair.of(seed, BiomeMaps.climates(BiomeMaps.sampler(registries, seed))));
        }
        StringBuilder out = new StringBuilder("\nshare  shape(warmFrom,warmTo,dryTo)  typical/seed  biggest  bigOnes  spacing  coverage  desert-typical");
        for (SavannaRegion.Shape shape : shapes) {
            for (double share : shares) {
                List<Integer> typical = new ArrayList<>();
                List<Integer> desertTypical = new ArrayList<>();
                int biggest = 0, big = 0;
                double coverage = 0;
                long land = 0;
                for (Pair<Long, Climate.TargetPoint[]> c : climates) {
                    byte[] map = BiomeMaps.pretendMap(registries, c.getFirst(), c.getSecond(), share, shape);
                    BiomeMaps.Patches p = BiomeMaps.patches(map, BiomeMaps.SAVANNA);
                    typical.add(p.typical());
                    desertTypical.add(BiomeMaps.patches(map, BiomeMaps.DESERT).typical());
                    biggest = Math.max(biggest, p.biggest());
                    big += p.bigOnes();
                    coverage += p.coverage();
                    for (byte b : map) {
                        if (b != BiomeMaps.OCEAN) {
                            land++;
                        }
                    }
                }
                int spacing = big == 0 ? -1 : (int) Math.sqrt(land * (double) BiomeMaps.STEP * BiomeMaps.STEP / big);
                out.append(String.format("%n%.2f  (%.2f,%.2f,%.2f)  %s  %d  %d  %d  %.1f%%  %s", share, shape.warmFrom(), shape.warmTo(),
                        shape.dryTo(), typical, biggest, big, spacing, 100 * coverage / climates.size(), desertTypical));
            }
        }
        SavannaMod.LOGGER.info("[biome maps] tuning:{}", out);
        // pictures of a few, for the eye
        try {
            long seed = SEEDS[0];
            Climate.TargetPoint[] c = climates.get(0).getSecond();
            BiomeMaps.draw(FMLPaths.GAMEDIR.get().resolve("biome-maps").resolve("tuning_seed_" + seed + ".png"),
                    BiomeMaps.pretendMap(registries, seed, c, 0, SavannaRegion.Shape.VANILLA),
                    BiomeMaps.pretendMap(registries, seed, c, 0.75, new SavannaRegion.Shape(0.1F, 0.6F, 0.15F)),
                    BiomeMaps.pretendMap(registries, seed, c, 0.75, new SavannaRegion.Shape(0.05F, 0.6F, 0.3F)));
        } catch (java.io.IOException e) {
            throw new GameTestAssertException("could not draw: " + e);
        }
        helper.succeed();
    }
}
