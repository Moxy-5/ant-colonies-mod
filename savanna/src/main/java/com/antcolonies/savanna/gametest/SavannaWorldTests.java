package com.antcolonies.savanna.gametest;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.worldgen.FloodplainPoolFeature;
import com.antcolonies.savanna.worldgen.RiverBankFeature;
import com.antcolonies.savanna.worldgen.SavannaRegion;
import com.antcolonies.savanna.worldgen.SavannaSpawn;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
        int biggest = 0, big = 0, tooFar = 0, small = 0;
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
                BlockPos inside = SavannaSpawn.spawnPlace(source, sampler, BlockPos.ZERO, found.getFirst(), 6400);
                int depth = SavannaSpawn.depth(source, sampler, inside);
                // BUGS 209: the spawn's savanna must be a big one (the nearest was often one of Minecraft's small patches)
                boolean isBig = SavannaSpawn.bigSavanna(source, sampler, BlockPos.ZERO, 6400) != null;
                small += isBig ? 0 : 1;
                report.append(isBig ? "" : "\n    spawn: NO big savanna within 6,400 blocks");
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
        helper.assertTrue(small == 0, "the spawn is not in a big savanna for " + small + " seeds: " + all);
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
        int hollow = 0, deep = 0;
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, helper.getLevel());
            byte[] map = BiomeMaps.map(source, sampler);
            // the spots in one fixed order for the seed, whatever the map (BUGS 181: shuffling only the savanna cells let a
            // change of the biome map swing which spots were read, 5 or 16 caves out of the same land)
            List<Integer> cells = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                cells.add(c);
            }
            java.util.Collections.shuffle(cells, new java.util.Random(seed));
            List<Integer> seedClimbs = new ArrayList<>();
            List<Integer> seedReliefs = new ArrayList<>();
            int[] seedHollow = {0, 0};
            for (int c : cells) {
                if (seedClimbs.size() >= GROUND_SPOTS) {
                    break;
                }
                if (map[c] != BiomeMaps.SAVANNA) {
                    continue;
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
                int air = ground.airUnder(x, z, DEEP_CAVE_LOOK);
                if (air > 0 && air <= HOLLOW_DEPTH) {
                    seedHollow[0]++;
                } else if (air > 0) {
                    seedHollow[1]++;
                }
            }
            report.append(String.format("%n  seed %d: %s, %d with a cave or pit within %d blocks under the ground, %d with a closed cave's roof %d to %d under",
                    seed, groundLine(seedClimbs, seedReliefs), seedHollow[0], HOLLOW_DEPTH, seedHollow[1], HOLLOW_DEPTH + 1, DEEP_CAVE_LOOK));
            hollow += seedHollow[0];
            deep += seedHollow[1];
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
        String all = groundLine(climbs, reliefs) + String.format(", %d with a cave or pit within %d blocks under, %d with a closed cave's roof %d to %d under",
                hollow, HOLLOW_DEPTH, deep, HOLLOW_DEPTH + 1, DEEP_CAVE_LOOK) + report;
        SavannaMod.LOGGER.info("[biome maps] savanna ground: {}", all);
        double steep = share(climbs, STEEP_CLIMB);
        double rugged = share(reliefs, RUGGED_RELIEF);
        helper.assertTrue(steep <= STEEP_MOST, "too much steep savanna ground: " + all);
        helper.assertTrue(rugged <= RUGGED_MOST, "too much rugged savanna ground: " + all);
        // the user, 2026-11-07: "random dips in the ground and ravines and caves, I want to get rid of all of that"
        helper.assertTrue(hollow <= HOLLOW_MOST * climbs.size(), "caves or pits under the savanna's ground: " + all);
        helper.succeed();
    }

    /**
     * Savanna rivers (the user, 2026-11-07, {@code docs/plans/savanna-rivers.md}: "a river right up to the savannah ... like
     * the nile river ... no drop off at all"): read from the noise on {@link #GROUND_SEEDS}, river spots with savanna round
     * them, and from each the ground walked out from the water's edge in the four directions that end in savanna: the
     * height above the water at each distance, how many banks drop more than {@link #BANK_DROP} blocks within 4 of the
     * water, ponds met on the floodplain, and the river's depth. The real thing: big savanna rivers run through a wide flat
     * floodplain a block or two above the dry-season water (the Nile, the Okavango, the Luangwa). The step down to the
     * low strip by the water ({@code docs/plans/savanna-rivers.md}, 4 blocks back) is cut later in real chunks
     * ({@code worldgen/RiverBankFeature}), not in the noise, so here the floodplain should lie 1 block above the water.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "savanna_maps")
    public static void savannaRiversRunThroughFlatLand(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        int[] offsets = RIVER_OFFSETS;
        List<List<Integer>> above = new ArrayList<>();
        for (int o : offsets) {
            above.add(new ArrayList<>());
        }
        List<Integer> depths = new ArrayList<>();
        int banks = 0, drops = 0, ponds = 0;
        StringBuilder report = new StringBuilder();
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, helper.getLevel());
            byte[] map = BiomeMaps.map(source, sampler);
            List<Integer> cells = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                int i = c / BiomeMaps.SIDE, j = c % BiomeMaps.SIDE;
                if (map[c] == BiomeMaps.RIVER && i > 0 && j > 0 && i < BiomeMaps.SIDE - 1 && j < BiomeMaps.SIDE - 1
                        && (map[c - 1] == BiomeMaps.SAVANNA || map[c + 1] == BiomeMaps.SAVANNA
                        || map[c - BiomeMaps.SIDE] == BiomeMaps.SAVANNA || map[c + BiomeMaps.SIDE] == BiomeMaps.SAVANNA)) {
                    cells.add(c);
                }
            }
            java.util.Collections.shuffle(cells, new java.util.Random(seed));
            int seedBanks = 0;
            for (int c : cells) {
                if (seedBanks >= RIVER_BANKS) {
                    break;
                }
                int x = -BiomeMaps.HALF + (c / BiomeMaps.SIDE) * BiomeMaps.STEP;
                int z = -BiomeMaps.HALF + (c % BiomeMaps.SIDE) * BiomeMaps.STEP;
                if (ground.height(x, z) > SEA_TOP) {
                    continue; // the river's own biome is a little wider than its water
                }
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int edge = -1, deepest = SEA_TOP;
                    for (int k = 0; k <= 48; k++) {
                        int h = ground.height(x + d[0] * k, z + d[1] * k);
                        if (h > SEA_TOP) {
                            edge = k;
                            break;
                        }
                        deepest = Math.min(deepest, h);
                    }
                    int far = edge + offsets[offsets.length - 1];
                    if (edge < 0 || !source.getNoiseBiome(QuartPos.fromBlock(x + d[0] * far), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y),
                            QuartPos.fromBlock(z + d[1] * far), sampler).is(BiomeTags.IS_SAVANNA)) {
                        continue;
                    }
                    seedBanks++;
                    depths.add(SEA_TOP + 1 - deepest);
                    boolean drop = false, pond = false;
                    for (int n = 0; n < offsets.length; n++) {
                        int k = edge + offsets[n];
                        int a = ground.height(x + d[0] * k, z + d[1] * k) - (SEA_TOP + 1);
                        above.get(n).add(a);
                        drop |= offsets[n] <= 4 && a > BANK_DROP;
                        pond |= offsets[n] >= 2 && a < 0;
                    }
                    drops += drop ? 1 : 0;
                    ponds += pond ? 1 : 0;
                }
            }
            banks += seedBanks;
            report.append(String.format("%n  seed %d: %d banks", seed, seedBanks));
        }
        StringBuilder profile = new StringBuilder();
        for (int n = 0; n < offsets.length; n++) {
            profile.append(String.format(" %d:%d(%d)", offsets[n], middle(above.get(n), 0.5), middle(above.get(n), 0.9)));
        }
        String all = String.format("%d banks; height above the water by distance from its edge, median (90%% at most):%s; "
                + "%.0f%% drop more than %d within 4 of the water; %.0f%% meet a pond on the floodplain; river depth median %d, 10%% at most %d%s",
                banks, profile, banks == 0 ? 0 : 100.0 * drops / banks, BANK_DROP, banks == 0 ? 0 : 100.0 * ponds / banks,
                middle(depths, 0.5), middle(depths, 0.1), report);
        SavannaMod.LOGGER.info("[biome maps] savanna rivers: {}", all);
        helper.assertTrue(banks >= 30, "too few savanna river banks found to measure: " + all);
        helper.assertTrue(drops <= RIVER_DROPS_MOST * banks, "savanna rivers still sit in sloped valleys: " + all);
        helper.assertTrue(ponds <= RIVER_PONDS_MOST * banks, "ponds on the savanna's floodplain: " + all);
        helper.assertTrue(middle(depths, 0.5) >= RIVER_DEPTH_LEAST, "savanna rivers too shallow: " + all);
        for (int n = 0; n < offsets.length; n++) {
            if (offsets[n] >= 2 && offsets[n] <= FLOODPLAIN_OUT) {
                helper.assertTrue(middle(above.get(n), 0.5) <= 1 && middle(above.get(n), 0.9) <= 2,
                        "the floodplain is not a block above the water " + offsets[n] + " blocks out: " + all);
            }
        }
        helper.succeed();
    }

    /** Distances out from the water's edge the ground is measured at, banks measured a seed, and the limits. */
    static final int[] RIVER_OFFSETS = {0, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32};
    /** How far out the floodplain must lie a block above the water (median 1, nine in ten at most 2). */
    static final int RIVER_BANKS = 80, BANK_DROP = 2, RIVER_DEPTH_LEAST = 3, FLOODPLAIN_OUT = 12;
    /**
     * How much floodplain the savanna rivers have (floodplain trees, step 1, 2026-11-13, {@code docs/plans/floodplain-trees.md}),
     * read from the noise on {@link #GROUND_SEEDS}, so the trees' spacing fits the real land: from river banks with
     * savanna behind them, how far the floodplain biome ({@link SavannaRegion#SAVANNA_FLOODPLAIN}) reaches out from the
     * water's edge (read every 4 blocks, the biome's own grain), and how much of that is low ground (2 blocks or less over
     * the water, where mounds keep off); on the map, the floodplain's share of the savanna, how many blocks of it a block
     * of savanna river has, and how much lies far from any river (a dry valley).
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "savanna_maps")
    public static void floodplainWidthIsMeasured(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        List<Integer> widths = new ArrayList<>(), lows = new ArrayList<>(), riverBiomeOver = new ArrayList<>();
        int banks = 0, none = 0;
        long floodCells = 0, savannaCells = 0, riverCells = 0, farCells = 0;
        StringBuilder report = new StringBuilder();
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, helper.getLevel());
            byte[] map = BiomeMaps.map(source, sampler);
            int side = BiomeMaps.SIDE;
            boolean[] flood = new boolean[map.length];
            for (int c = 0; c < map.length; c++) {
                if (map[c] != BiomeMaps.SAVANNA) {
                    continue;
                }
                savannaCells++;
                int x = -BiomeMaps.HALF + (c / side) * BiomeMaps.STEP, z = -BiomeMaps.HALF + (c % side) * BiomeMaps.STEP;
                flood[c] = floodplain(source, sampler, x, z);
                floodCells += flood[c] ? 1 : 0;
            }
            List<Integer> cells = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                int i = c / side, j = c % side;
                if (map[c] != BiomeMaps.RIVER || i == 0 || j == 0 || i == side - 1 || j == side - 1) {
                    continue;
                }
                boolean bySavanna = map[c - 1] == BiomeMaps.SAVANNA || map[c + 1] == BiomeMaps.SAVANNA
                        || map[c - side] == BiomeMaps.SAVANNA || map[c + side] == BiomeMaps.SAVANNA;
                if (bySavanna) {
                    riverCells++;
                    cells.add(c);
                }
            }
            for (int c = 0; c < map.length; c++) {
                if (!flood[c]) {
                    continue;
                }
                boolean near = false;
                int i = c / side, j = c % side;
                for (int di = -8; di <= 8 && !near; di++) {
                    for (int dj = -8; dj <= 8 && !near; dj++) {
                        int a = i + di, b = j + dj;
                        near = a >= 0 && b >= 0 && a < side && b < side && map[a * side + b] == BiomeMaps.RIVER;
                    }
                }
                farCells += near ? 0 : 1;
            }
            java.util.Collections.shuffle(cells, new java.util.Random(seed));
            int seedBanks = 0;
            for (int c : cells) {
                if (seedBanks >= RIVER_BANKS) {
                    break;
                }
                int x = -BiomeMaps.HALF + (c / side) * BiomeMaps.STEP, z = -BiomeMaps.HALF + (c % side) * BiomeMaps.STEP;
                if (ground.height(x, z) > SEA_TOP) {
                    continue;
                }
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int edge = -1;
                    for (int k = 0; k <= 48; k++) {
                        if (ground.height(x + d[0] * k, z + d[1] * k) > SEA_TOP) {
                            edge = k;
                            break;
                        }
                    }
                    if (edge < 0 || !source.getNoiseBiome(QuartPos.fromBlock(x + d[0] * (edge + 300)), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y),
                            QuartPos.fromBlock(z + d[1] * (edge + 300)), sampler).is(BiomeTags.IS_SAVANNA)) {
                        continue; // only banks with savanna behind them
                    }
                    seedBanks++;
                    int width = 0, low = 0, start = 0;
                    while (start < 64 && source.getNoiseBiome(QuartPos.fromBlock(x + d[0] * (edge + start)), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y),
                            QuartPos.fromBlock(z + d[1] * (edge + start)), sampler).is(BiomeTags.IS_RIVER)) {
                        start += 4; // the river's biome reaches a little past its water
                    }
                    riverBiomeOver.add(start);
                    for (int k = start; k <= FLOOD_LOOK; k += 4) {
                        int bx = x + d[0] * (edge + k), bz = z + d[1] * (edge + k);
                        if (!floodplain(source, sampler, bx, bz)) {
                            break;
                        }
                        width = k + 4;
                        if (ground.height(bx, bz) - (SEA_TOP + 1) <= 1) {
                            low = k + 4; // the ground's top block 2 or less over the water's top
                        }
                    }
                    widths.add(width);
                    lows.add(low);
                    none += width == 0 ? 1 : 0;
                }
            }
            banks += seedBanks;
            report.append(String.format("%n  seed %d: %d banks", seed, seedBanks));
        }
        String all = String.format("%d banks; floodplain width from the water's edge median %d, 10%% %d, 90%% %d (none on %.0f%%, %d or more on %.0f%%); "
                + "low ground (2 or less over the water) median %d, 90%% %d; the floodplain %.1f%% of the savanna, %.0f blocks of it a block of "
                + "savanna river, %.0f%% of it more than 256 blocks from a river; the river's biome reaches past its water median %d, 90%% %d%s",
                banks, middle(widths, 0.5), middle(widths, 0.1), middle(widths, 0.9), banks == 0 ? 0 : 100.0 * none / banks,
                FLOOD_LOOK, banks == 0 ? 0 : 100.0 * widths.stream().filter(w -> w > FLOOD_LOOK).count() / banks,
                middle(lows, 0.5), middle(lows, 0.9), savannaCells == 0 ? 0 : 100.0 * floodCells / savannaCells,
                riverCells == 0 ? 0 : (double) floodCells * BiomeMaps.STEP / riverCells, floodCells == 0 ? 0 : 100.0 * farCells / floodCells,
                middle(riverBiomeOver, 0.5), middle(riverBiomeOver, 0.9), report);
        SavannaMod.LOGGER.info("[biome maps] floodplain: {}", all);
        helper.assertTrue(banks >= 30, "too few savanna river banks found to measure: " + all);
        helper.assertTrue(middle(widths, 0.5) >= FLOOD_WIDTH_LEAST && none <= FLOOD_NONE_MOST * banks,
                "the floodplain too narrow for its trees: " + all);
        double share = savannaCells == 0 ? 0 : (double) floodCells / savannaCells;
        helper.assertTrue(share >= FLOOD_SHARE[0] && share <= FLOOD_SHARE[1], "the floodplain's share of the savanna out of its band: " + all);
        helper.succeed();
    }

    /** How far out from the water the floodplain is followed, in blocks. */
    static final int FLOOD_LOOK = 400;
    /**
     * Measured 2026-11-13 on {@link #GROUND_SEEDS}: width median 20 (10 in 100 banks 0, 90 in 100 at most 76), none on
     * 19 percent of banks, the floodplain 22.4 percent of the savanna; the floodplain trees' spacing was picked for it, so
     * these limits flag a change in the land that would leave them no room (or the dry trees too little).
     */
    static final int FLOOD_WIDTH_LEAST = 12;
    static final double FLOOD_NONE_MOST = 0.35;
    static final double[] FLOOD_SHARE = {0.10, 0.35};

    private static boolean floodplain(MultiNoiseBiomeSource source, Climate.Sampler sampler, int x, int z) {
        return source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y), QuartPos.fromBlock(z), sampler)
                .is(SavannaRegion.SAVANNA_FLOODPLAIN);
    }

    /**
     * Before the floodplain (2026-11-07) the land rose 3 to 7 blocks within 16 of the water and banks dropped; after it
     * none dropped and 4 to 6 percent met a pond (a low dip of the lumps), so the limits leave room for chance.
     */
    static final double RIVER_DROPS_MOST = 0.03, RIVER_PONDS_MOST = 0.12;

    /**
     * The banks and pools as the game's own decoration makes them ({@code worldgen/RiverBankFeature},
     * {@code worldgen/FloodplainPoolFeature}), on a river built in the arena: a plain a block over the water, the river
     * winding through it. The land test above reads only the noise; this checks the user's picks: the low strip flush with
     * the water 4 blocks back, the plain behind it untouched, silt and mud far more on the inside of the bends than the
     * outside, the bed silt, a pan and an oxbow up on the plain holding their water, and no pool beside the river.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "savanna_rivers")
    public static void riverBanksGetTheirStripSiltAndMud(GameTestHelper helper) {
        var level = helper.getLevel();
        int plain = 6, water = plain - 1;
        java.util.function.IntUnaryOperator mid = x -> (int) Math.round(50 + 5 * Math.sin(x / 7.0));
        for (int x = 1; x < 64; x++) {
            for (int z = 1; z < 64; z++) {
                boolean river = Math.abs(z - mid.applyAsInt(x)) <= 3; // 7 wide, 3 deep
                for (int y = 1; y <= plain; y++) {
                    Block b = river && y >= water - 2 ? (y <= water ? Blocks.WATER : Blocks.AIR) : y == plain ? Blocks.GRASS_BLOCK : Blocks.DIRT;
                    helper.setBlock(x, y, z, b);
                }
            }
        }
        BlockPos a = helper.absolutePos(new BlockPos(1, water, 1));
        int changed = RiverBankFeature.shape(level, a.getX(), a.getZ(), 63, 63, a.getY(), p -> true);
        int flush = 0, near = 0, farTouched = 0, inner = 0, outer = 0, mud = 0, bedSilt = 0, bed = 0;
        for (int x = 14; x < 50; x++) { // away from the arena's ends, where the river stops
            int m = mid.applyAsInt(x);
            // the inside of the bend is the side the river curves round (z'' = -5/49 sin(x/7))
            double curve = -Math.sin(x / 7.0);
            for (int z = 30; z < 64; z++) {
                int out = Math.abs(z - m) - 3;
                if (out <= 0) {
                    bed++;
                    bedSilt += helper.getBlockState(new BlockPos(x, water - 3, z)).is(SavannaBlocks.SILT.get()) ? 1 : 0;
                    continue;
                }
                boolean open = helper.getBlockState(new BlockPos(x, plain, z)).isAir();
                BlockState top = helper.getBlockState(new BlockPos(x, water, z));
                if (out <= 3) {
                    near++;
                    flush += open ? 1 : 0;
                    if (!top.is(Blocks.GRASS_BLOCK) && top.getFluidState().isEmpty() && Math.abs(curve) > 0.5) {
                        if (Math.signum(z - m) == Math.signum(curve)) {
                            inner++;
                        } else {
                            outer++;
                        }
                    }
                    mud += top.is(SavannaBlocks.RIVER_MUD.get()) ? 1 : 0;
                } else if (out >= 7 && (open || !helper.getBlockState(new BlockPos(x, plain, z)).is(Blocks.GRASS_BLOCK))) {
                    farTouched++;
                }
            }
        }
        String report = String.format("%d columns changed; %d of %d within 3 of the water flush, %d far columns changed, silt or mud"
                + " on %d inside bends and %d outside, %d mud, %d of %d bed columns silt", changed, flush, near, farTouched, inner, outer,
                mud, bedSilt, bed);
        SavannaMod.LOGGER.info("[river banks] {}", report);
        helper.assertTrue(flush >= near * 0.95, "the low strip is not flush with the water: " + report);
        helper.assertTrue(farTouched == 0, "the plain behind the strip was changed: " + report);
        helper.assertTrue(inner >= 2 * outer + 10, "the silt bars are not on the inside of the bends: " + report);
        helper.assertTrue(mud >= 5, "no trampled mud by the water: " + report);
        helper.assertTrue(bedSilt >= bed * 0.9, "the river bed is not silt: " + report);
        // the pools: a pan and an oxbow up on the plain, well away from the river; none within 8 blocks of it
        RandomSource random = RandomSource.create(7);
        boolean pan = false, oxbow = false;
        for (int t = 0; t < 8 && !pan; t++) {
            pan = FloodplainPoolFeature.place(level, helper.absolutePos(new BlockPos(14, plain, 14)), random, false, a.getY());
        }
        for (int t = 0; t < 8 && !oxbow; t++) {
            oxbow = FloodplainPoolFeature.place(level, helper.absolutePos(new BlockPos(46, plain, 16)), random, true, a.getY());
        }
        boolean beside = FloodplainPoolFeature.place(level, helper.absolutePos(new BlockPos(30, plain, 34)), random, false, a.getY());
        helper.assertTrue(pan && oxbow, "a pan (" + pan + ") or an oxbow (" + oxbow + ") did not fit on the open plain");
        helper.assertFalse(beside, "a pool was put beside the river");
        int poolWater = poolWater(helper, plain);
        helper.assertTrue(poolWater >= 15, "the pools hold only " + poolWater + " blocks of water");
        helper.runAfterDelay(60, () -> {
            int spilled = 0;
            for (int x = 1; x < 64; x++) {
                for (int z = 1; z < 36; z++) {
                    var f = helper.getBlockState(new BlockPos(x, plain, z)).getFluidState();
                    spilled += (!f.isEmpty() && !f.isSource()) || !helper.getBlockState(new BlockPos(x, plain + 1, z)).getFluidState().isEmpty() ? 1 : 0;
                }
            }
            int still = poolWater(helper, plain);
            helper.assertTrue(spilled == 0 && still == poolWater, "the pools spilled: " + spilled + " flowing, " + still + " of " + poolWater + " still");
            helper.succeed();
        });
    }

    /** Still water blocks at the plain's top in the arena's northern part, where the test puts its pools. */
    private static int poolWater(GameTestHelper helper, int plain) {
        int n = 0;
        for (int x = 1; x < 64; x++) {
            for (int z = 1; z < 36; z++) {
                n += helper.getBlockState(new BlockPos(x, plain, z)).getFluidState().isSource() ? 1 : 0;
            }
        }
        return n;
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
     * dips, ravines or caves in the savanna). The edges of the climate mask may keep a few. 8 blocks, not 16 (BUGS 181):
     * what is left under the plain is Minecraft's deep cheese caves (big round caverns), whose roofs lie 14 to 16 blocks
     * under the grass, below the sea, closed over by solid ground; they only begin where the ground's noise is deep enough
     * (about 13 blocks down on the flat plain), so they cannot come up to the grass. A dip, pit or cave a player would see
     * from the savanna, or meet in a shallow dig, starts within 8. The deeper roofs are counted in the report, not limited.
     */
    static final int HOLLOW_DEPTH = 8;
    static final double HOLLOW_MOST = 0.01;
    /** How far down the report looks for a closed cave's roof (counted only). */
    static final int DEEP_CAVE_LOOK = 16;

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
