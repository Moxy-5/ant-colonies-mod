package com.antcolonies.savanna.gametest;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.worldgen.MoundPlaces;
import com.antcolonies.savanna.worldgen.FloodplainPoolFeature;
import com.antcolonies.savanna.worldgen.FloodplainTreePiece;
import com.antcolonies.savanna.worldgen.FloodplainTreeStructure;
import com.antcolonies.savanna.worldgen.MinecraftZones;
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
     * No small savannas (2026-11-16, {@code docs/plans/big-savannas-big-rivers.md}, steps 2 and 2a): every zone has the
     * big savannas' table (one recipe everywhere), so no zone edge cuts a savanna; what is left are thin climate slivers
     * and ragged edges. Measured on the 4 maps: 637 savannas under {@link BiomeMaps#SMALL} blocks across, 5.2 percent of
     * the savanna's area (919 and 14.7 while Minecraft's zones had their own table); 327 of them "lonely" (no big savanna
     * within {@link BiomeMaps#NEAR_BIG} cells), 3.6 percent of the area (584 and 10.9). The bands sit a little above that
     * (my pick), to catch a change that makes more. {@link #OLD_SHARE}: our share of the zones until then, with no other
     * biome mod (weight 30 against Minecraft's 10), for the pretend maps of before.
     */
    static final int SMALL_MOST = 720;
    static final double SMALL_SHARE_MOST = 0.07;
    static final double LONELY_SHARE_MOST = 0.05;
    static final double OLD_SHARE = 0.75;
    /** The gate's own check: savannas under this many blocks across (well under the room the gate asks for). */
    static final int TINY = 500;

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
        // no small savannas (2026-11-16): Minecraft's zones replaced by its table without savannas, at the right index
        List<String> indexProblems = MinecraftZones.indexProblems();
        helper.assertTrue(indexProblems.isEmpty(), "TerraBlender's regions: " + indexProblems + " (" + MinecraftZones.describe() + ")");
        List<Integer> typical = new ArrayList<>();
        int biggest = 0, big = 0, tooFar = 0, small = 0, smallPatches = 0, smallBefore = 0;
        double smallShare = 0, lonelyShare = 0;
        int lonely = 0, lonelyBefore = 0, lonelyAfterPretend = 0;
        int[] otherKinds = new int[BiomeMaps.PLAINS + 1];
        // the gate (worldgen/BigSavanna): savanna cells in big patches and in small ones, and how many of each are quiet
        long bigCells = 0, bigQuiet = 0, smallCells = 0, smallQuiet = 0, tinyCells = 0, tinyQuiet = 0, gateNanos = 0;
        double landShare = 0;
        int land = 0;
        StringBuilder report = new StringBuilder();
        for (long seed : SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            byte[] ours = BiomeMaps.map(source, sampler);
            Climate.TargetPoint[] climates = BiomeMaps.climates(sampler);
            byte[] vanilla = BiomeMaps.pretendMap(registries, seed, climates, 0, SavannaRegion.Shape.VANILLA);
            // how it was before 2026-11-16 (a pretend layout: our share of the zones, Minecraft's own table in the rest)
            byte[] lastWeek = BiomeMaps.pretendMap(registries, seed, climates, OLD_SHARE, SavannaRegion.SHAPE, false);
            BiomeMaps.Patches savanna = BiomeMaps.patches(ours, BiomeMaps.SAVANNA);
            BiomeMaps.Patches before = BiomeMaps.patches(vanilla, BiomeMaps.SAVANNA);
            BiomeMaps.Patches beforeNoSmall = BiomeMaps.patches(lastWeek, BiomeMaps.SAVANNA);
            // the same pretend layout with Minecraft's zones swapped (a fair before and after: the same zones)
            BiomeMaps.Patches afterPretend = BiomeMaps.patches(
                    BiomeMaps.pretendMap(registries, seed, climates, OLD_SHARE, SavannaRegion.SHAPE, true), BiomeMaps.SAVANNA);
            BiomeMaps.Patches desert = BiomeMaps.patches(ours, BiomeMaps.DESERT);
            report.append(String.format("%n  seed %d: savanna %s%n    (Minecraft's own: %s; desert now: %s)", seed, savanna, before, desert));
            report.append(String.format("%n    small savannas: now %d (%.2f%% of area), %d lonely (%.2f%%) %s;"
                    + " pretend layout before %d / %d lonely (%.2f%%), after %d / %d lonely (%.2f%%)",
                    savanna.small(), 100 * savanna.smallShare(), savanna.lonely(), 100 * savanna.lonelyShare(), savanna.lonelySizes(),
                    beforeNoSmall.small(), beforeNoSmall.lonely(), 100 * beforeNoSmall.lonelyShare(),
                    afterPretend.small(), afterPretend.lonely(), 100 * afterPretend.lonelyShare()));
            smallPatches += savanna.small();
            smallBefore += beforeNoSmall.small();
            smallShare += savanna.smallShare();
            lonely += savanna.lonely();
            lonelyBefore += beforeNoSmall.lonely();
            lonelyAfterPretend += afterPretend.lonely();
            lonelyShare += savanna.lonelyShare();
            for (byte b : ours) {
                otherKinds[b]++;
            }
            typical.add(savanna.typical());
            biggest = Math.max(biggest, savanna.biggest());
            big += savanna.bigOnes();
            for (byte b : ours) {
                if (b != BiomeMaps.OCEAN) {
                    land++;
                }
            }
            landShare += savanna.coverage();
            byte[] gated = ours.clone();
            int[] across = BiomeMaps.acrossOfCells(ours, BiomeMaps.SAVANNA);
            long gateStart = System.nanoTime();
            for (int c = 0; c < ours.length; c++) {
                if (ours[c] != BiomeMaps.SAVANNA) {
                    continue;
                }
                boolean lives = com.antcolonies.savanna.worldgen.BigSavanna.livesAt(source, sampler,
                        -BiomeMaps.HALF + (c / BiomeMaps.SIDE) * BiomeMaps.STEP, -BiomeMaps.HALF + (c % BiomeMaps.SIDE) * BiomeMaps.STEP);
                if (!lives) {
                    gated[c] = BiomeMaps.QUIET;
                }
                if (across[c] >= BiomeMaps.SMALL) {
                    bigCells++;
                    bigQuiet += lives ? 0 : 1;
                } else {
                    smallCells++;
                    smallQuiet += lives ? 0 : 1;
                    if (across[c] < TINY) {
                        tinyCells++;
                        tinyQuiet += lives ? 0 : 1;
                    }
                }
            }
            gateNanos += System.nanoTime() - gateStart;
            try {
                BiomeMaps.draw(FMLPaths.GAMEDIR.get().resolve("biome-maps").resolve("seed_" + seed + ".png"), vanilla, ours);
                BiomeMaps.draw(FMLPaths.GAMEDIR.get().resolve("biome-maps").resolve("gate_seed_" + seed + ".png"), gated);
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
        // no small savannas: few small patches left (only our own savannas cut at a zone's edge and thin climate slivers)
        smallShare /= SEEDS.length;
        lonelyShare /= SEEDS.length;
        String smallLine = String.format("small savannas (under %d across) on %d maps: %d, %.2f%% of savanna area (before 2026-11-16, pretend: %d);"
                + " lonely (no big savanna within %d blocks): %d, %.2f%% of savanna area (pretend layout: %d before, %d after)",
                BiomeMaps.SMALL, SEEDS.length, smallPatches, 100 * smallShare, smallBefore, BiomeMaps.NEAR_BIG * BiomeMaps.STEP,
                lonely, 100 * lonelyShare, lonelyBefore, lonelyAfterPretend);
        SavannaMod.LOGGER.info("[biome maps] {}", smallLine);
        helper.assertTrue(smallPatches <= SMALL_MOST && smallShare <= SMALL_SHARE_MOST && lonelyShare <= LONELY_SHARE_MOST,
                "too many small savannas: " + smallLine + all);
        // the gate: nearly all of a big savanna lives, most small-savanna ground is quiet
        String gateLine = String.format("the gate: quiet %.2f%% of big savannas' ground (%d of %d cells), %.2f%% of small savannas' (%d of %d),"
                        + " %.2f%% of savannas under %d across (%d of %d); %d ms for %d maps",
                100.0 * bigQuiet / Math.max(1, bigCells), bigQuiet, bigCells, 100.0 * smallQuiet / Math.max(1, smallCells), smallQuiet, smallCells,
                100.0 * tinyQuiet / Math.max(1, tinyCells), TINY, tinyQuiet, tinyCells, gateNanos / 1_000_000, SEEDS.length);
        SavannaMod.LOGGER.info("[biome maps] {}", gateLine);
        // measured 2026-11-16: 6.8 percent of big savannas' ground quiet (thin tips), 81 percent of savannas under 500 across
        // (the rest are frayed bits beside a big savanna, which live on purpose); bands my picks, a little wider
        helper.assertTrue(bigQuiet <= 0.10 * bigCells, "the gate silences too much of the big savannas: " + gateLine);
        helper.assertTrue(tinyQuiet >= 0.70 * tinyCells, "the gate lets too many small savannas live: " + gateLine);
        // and the rest of the world still turns up
        int cells = SEEDS.length * BiomeMaps.SIDE * BiomeMaps.SIDE;
        StringBuilder kinds = new StringBuilder();
        for (byte k : new byte[] {BiomeMaps.DESERT, BiomeMaps.JUNGLE, BiomeMaps.PLAINS, BiomeMaps.BADLANDS, BiomeMaps.FOREST, BiomeMaps.COLD}) {
            kinds.append(String.format(" %d:%.2f%%", k, 100.0 * otherKinds[k] / cells));
        }
        SavannaMod.LOGGER.info("[biome maps] other ground (desert 4, jungle 6, plains 10, badlands 9, forest 5, cold 7):{}", kinds);
        for (byte k : new byte[] {BiomeMaps.DESERT, BiomeMaps.JUNGLE, BiomeMaps.PLAINS, BiomeMaps.BADLANDS, BiomeMaps.FOREST, BiomeMaps.COLD}) {
            helper.assertTrue(otherKinds[k] >= cells / 1000, "ground kind " + k + " (almost) gone:" + kinds);
        }
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
                        && BiomeMaps.riverBeside(map, c, BiomeMaps.SAVANNA)) {
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
                boolean bySavanna = BiomeMaps.riverBeside(map, c, BiomeMaps.SAVANNA);
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

    /**
     * How wide the savanna rivers' water is (the user, 2026-11-14: rivers to be made bigger before the river animals;
     * measure first), read from the noise on {@link #GROUND_SEEDS}: at river spots with savanna beside them and water
     * there, the run of water through the spot along four lines (east-west, north-south and the two diagonals, a
     * diagonal step counted as 1.41 blocks); the shortest of the four is the river's width across. Only measures: no limit
     * but enough spots found.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "savanna_maps")
    public static void riverWidthIsMeasured(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        List<Integer> widths = new ArrayList<>();
        List<List<Integer>> byBand = new ArrayList<>();
        for (int b = 0; b < INLAND_NAMES.length; b++) {
            byBand.add(new ArrayList<>());
        }
        long[] savannaCells = new long[INLAND_NAMES.length], riverCells = new long[INLAND_NAMES.length];
        List<List<Integer>> depthByBand = new ArrayList<>(), shallowByBand = new ArrayList<>();
        for (int b = 0; b < INLAND_NAMES.length; b++) {
            depthByBand.add(new ArrayList<>());
            shallowByBand.add(new ArrayList<>());
        }
        int spots = 0;
        StringBuilder report = new StringBuilder();
        int[][] lines = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        int qy = QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y);
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, helper.getLevel());
            byte[] map = BiomeMaps.map(source, sampler);
            int side = BiomeMaps.SIDE;
            List<Integer> cells = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                int i = c / side, j = c % side;
                if (map[c] == BiomeMaps.SAVANNA) {
                    savannaCells[inlandBand(sampler, -BiomeMaps.HALF + i * BiomeMaps.STEP, qy, -BiomeMaps.HALF + j * BiomeMaps.STEP)]++;
                }
                if (map[c] != BiomeMaps.RIVER || i == 0 || j == 0 || i == side - 1 || j == side - 1) {
                    continue;
                }
                // the valley's own line only (Minecraft's river band of weirdness), as before the river's biome widened:
                // a wide stretch is met more often than a narrow one, the yardstick the widths were agreed by
                int px = -BiomeMaps.HALF + i * BiomeMaps.STEP, pz = -BiomeMaps.HALF + j * BiomeMaps.STEP;
                float weird = Climate.unquantizeCoord(sampler.sample(QuartPos.fromBlock(px), qy, QuartPos.fromBlock(pz)).weirdness());
                if (Math.abs(weird) < 0.05F && BiomeMaps.riverBeside(map, c, BiomeMaps.SAVANNA)) {
                    cells.add(c);
                    riverCells[inlandBand(sampler, -BiomeMaps.HALF + i * BiomeMaps.STEP, qy, -BiomeMaps.HALF + j * BiomeMaps.STEP)]++;
                }
            }
            java.util.Collections.shuffle(cells, new java.util.Random(seed));
            List<Integer> seedWidths = new ArrayList<>();
            for (int c : cells) {
                if (seedWidths.size() >= RIVER_WIDTH_SPOTS) {
                    break;
                }
                int x = -BiomeMaps.HALF + (c / side) * BiomeMaps.STEP, z = -BiomeMaps.HALF + (c % side) * BiomeMaps.STEP;
                if (ground.height(x, z) > SEA_TOP) {
                    continue; // the river's biome over dry ground
                }
                double across = Double.MAX_VALUE;
                int deepest = 0, shallow = 0, wet = 1;
                for (int[] d : lines) {
                    int run = 1, deep = SEA_TOP + 1 - ground.height(x, z), low = deep <= 3 ? 1 : 0;
                    for (int sign : new int[] {1, -1}) {
                        for (int k = 1; k <= WIDTH_LOOK; k++) {
                            int h = ground.height(x + sign * d[0] * k, z + sign * d[1] * k);
                            if (h > SEA_TOP) {
                                break;
                            }
                            run++;
                            deep = Math.max(deep, SEA_TOP + 1 - h);
                            low += SEA_TOP + 1 - h <= 3 ? 1 : 0;
                        }
                    }
                    double length = run * (d[0] != 0 && d[1] != 0 ? Math.sqrt(2) : 1);
                    if (length < across) {
                        across = length;
                        deepest = deep;
                        shallow = low;
                        wet = run;
                    }
                }
                seedWidths.add((int) Math.round(across));
                int band = inlandBand(sampler, x, qy, z);
                byBand.get(band).add((int) Math.round(across));
                depthByBand.get(band).add(deepest);
                shallowByBand.get(band).add((int) Math.round(100.0 * shallow / wet));
            }
            spots += seedWidths.size();
            widths.addAll(seedWidths);
            report.append(String.format("%n  seed %d: %d spots, median %d, 10%% %d, 90%% %d, widest %d", seed, seedWidths.size(),
                    middle(seedWidths, 0.5), middle(seedWidths, 0.1), middle(seedWidths, 0.9), middle(seedWidths, 1.0)));
        }
        String all = String.format("%d spots; river water width across median %d, 10%% at most %d, 25%% %d, 75%% %d, 90%% %d, widest %d%s",
                spots, middle(widths, 0.5), middle(widths, 0.1), middle(widths, 0.25), middle(widths, 0.75), middle(widths, 0.9),
                middle(widths, 1.0), report);
        StringBuilder bands = new StringBuilder();
        long allSavanna = java.util.Arrays.stream(savannaCells).sum(), allRiver = java.util.Arrays.stream(riverCells).sum();
        for (int b = 0; b < INLAND_NAMES.length; b++) {
            List<Integer> w = byBand.get(b);
            bands.append(String.format("%n  %s: savanna %.0f%%, river beside savanna %.0f%%, %d spots, width median %d, 25%% %d, 75%% %d, 90%% %d, deepest across median %d (25%% %d, 90%% %d), shallows (3 deep or less) %d%% of the way across",
                    INLAND_NAMES[b], allSavanna == 0 ? 0 : 100.0 * savannaCells[b] / allSavanna, allRiver == 0 ? 0 : 100.0 * riverCells[b] / allRiver,
                    w.size(), middle(w, 0.5), middle(w, 0.25), middle(w, 0.75), middle(w, 0.9),
                    middle(depthByBand.get(b), 0.5), middle(depthByBand.get(b), 0.25), middle(depthByBand.get(b), 0.9),
                    middle(shallowByBand.get(b), 0.5)));
        }
        all += String.format("%n by how far inland (Minecraft's continentalness bands):%s", bands);
        SavannaMod.LOGGER.info("[biome maps] river width: {}", all);
        helper.assertTrue(spots >= 30, "too few savanna river spots found to measure: " + all);
        helper.succeed();
    }

    /** Oxbows and pans rework, step 1: river banks per seed, dry spots per seed, floodplain spots per seed. */
    static final int POOL_BANKS = 60, PAN_SPOTS = 100, POOL_TRIES = 80;
    static final int[] POOL_FLOOD = {32, 48, 64, 96, 128};
    static final int[] PAN_RADII = {10, 20, 30, 40};

    /**
     * Oxbows and pans rework, step 1 (the user, 2026-11-18: "there more like tiny ponds", "start measuring"; plan
     * {@code docs/plans/oxbows-and-pans.md}), read from the noise on {@link #GROUND_SEEDS}. Only measures, no limit but
     * enough spots:
     * <ul>
     * <li><b>room for oxbows</b>: from savanna river banks, by how far inland, how far the floodplain biome reaches out
     * from the water's edge, and how far from the edge the ground stays within one block (flat enough for a lake);</li>
     * <li><b>room for pans</b>: at dry savanna spots off the floodplain, how often the ground within 10, 20, 30, 40 blocks
     * stays within 1, 2, 3 blocks of height, how often no termite mound place ({@link MoundPlaces}) lies that near, and
     * how far the nearest river is;</li>
     * <li><b>today's pools</b>: at floodplain spots, how often today's oxbow and pan (sizes and rules of
     * {@code FloodplainPoolFeature}, a square of their usual size) would fit: level within a block, above the water, no
     * water within 8 blocks.</li>
     * </ul>
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "savanna_maps")
    public static void oxbowAndPanRoomIsMeasured(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        int bands = INLAND_NAMES.length, qy = QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y);
        List<List<Integer>> floodBy = new ArrayList<>(), flatBy = new ArrayList<>();
        for (int b = 0; b < bands; b++) {
            floodBy.add(new ArrayList<>());
            flatBy.add(new ArrayList<>());
        }
        int panSpots = 0;
        int[][] panRelief = new int[PAN_RADII.length][4]; // within 1, 2, 3 blocks; clear of mound places
        int[] panFlatAndClear = new int[PAN_RADII.length];
        List<Integer> riverFar = new ArrayList<>();
        int tries = 0, oxbowFits = 0, panFits = 0, oxbowLevel = 0, panLevel = 0;
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, helper.getLevel());
            byte[] map = BiomeMaps.map(source, sampler);
            int side = BiomeMaps.SIDE;
            List<Integer> rivers = new ArrayList<>(), dry = new ArrayList<>(), flood = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                int i = c / side, j = c % side;
                if (i < 2 || j < 2 || i >= side - 2 || j >= side - 2) {
                    continue;
                }
                int x = -BiomeMaps.HALF + i * BiomeMaps.STEP, z = -BiomeMaps.HALF + j * BiomeMaps.STEP;
                if (map[c] == BiomeMaps.RIVER && BiomeMaps.riverBeside(map, c, BiomeMaps.SAVANNA)) {
                    rivers.add(c);
                } else if (map[c] == BiomeMaps.SAVANNA) {
                    (floodplain(source, sampler, x, z) ? flood : dry).add(c);
                }
            }
            java.util.Random dice = new java.util.Random(seed);
            java.util.Collections.shuffle(rivers, dice);
            java.util.Collections.shuffle(dry, dice);
            java.util.Collections.shuffle(flood, dice);
            // room for oxbows
            int seedBanks = 0;
            for (int c : rivers) {
                if (seedBanks >= POOL_BANKS) {
                    break;
                }
                int x = -BiomeMaps.HALF + (c / side) * BiomeMaps.STEP, z = -BiomeMaps.HALF + (c % side) * BiomeMaps.STEP;
                if (ground.height(x, z) > SEA_TOP) {
                    continue;
                }
                int band = inlandBand(sampler, x, qy, z);
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int edge = -1;
                    for (int k = 0; k <= 64; k++) {
                        if (ground.height(x + d[0] * k, z + d[1] * k) > SEA_TOP) {
                            edge = k;
                            break;
                        }
                    }
                    if (edge < 0 || !source.getNoiseBiome(QuartPos.fromBlock(x + d[0] * (edge + 300)), qy,
                            QuartPos.fromBlock(z + d[1] * (edge + 300)), sampler).is(BiomeTags.IS_SAVANNA)) {
                        continue;
                    }
                    seedBanks++;
                    int start = 0, width = 0;
                    while (start < 64 && source.getNoiseBiome(QuartPos.fromBlock(x + d[0] * (edge + start)), qy,
                            QuartPos.fromBlock(z + d[1] * (edge + start)), sampler).is(BiomeTags.IS_RIVER)) {
                        start += 4;
                    }
                    for (int k = start; k <= FLOOD_LOOK; k += 4) {
                        if (!floodplain(source, sampler, x + d[0] * (edge + k), z + d[1] * (edge + k))) {
                            break;
                        }
                        width = k + 4;
                    }
                    // how far out the ground stays within a block (and dry)
                    int first = ground.height(x + d[0] * edge, z + d[1] * edge), lo = first, hi = first, flat = 0;
                    for (int k = 2; k <= 160; k += 2) {
                        int h = ground.height(x + d[0] * (edge + k), z + d[1] * (edge + k));
                        lo = Math.min(lo, h);
                        hi = Math.max(hi, h);
                        if (hi - lo > 1 || h <= SEA_TOP) {
                            break;
                        }
                        flat = k;
                    }
                    floodBy.get(band).add(width);
                    flatBy.get(band).add(flat);
                }
            }
            // room for pans
            int seedPans = 0;
            for (int c : dry) {
                if (seedPans >= PAN_SPOTS) {
                    break;
                }
                int i = c / side, j = c % side;
                int x = -BiomeMaps.HALF + i * BiomeMaps.STEP, z = -BiomeMaps.HALF + j * BiomeMaps.STEP;
                int mid = ground.height(x, z);
                if (mid <= SEA_TOP + 2) {
                    continue;
                }
                seedPans++;
                int[] lo = new int[PAN_RADII.length], hi = new int[PAN_RADII.length];
                java.util.Arrays.fill(lo, mid);
                java.util.Arrays.fill(hi, mid);
                int far = PAN_RADII[PAN_RADII.length - 1];
                for (int dx = -far; dx <= far; dx += 5) {
                    for (int dz = -far; dz <= far; dz += 5) {
                        double r = Math.sqrt(dx * dx + dz * dz);
                        if (r > far) {
                            continue;
                        }
                        int h = ground.height(x + dx, z + dz);
                        for (int k = 0; k < PAN_RADII.length; k++) {
                            if (r <= PAN_RADII[k]) {
                                lo[k] = Math.min(lo[k], h);
                                hi[k] = Math.max(hi[k], h);
                            }
                        }
                    }
                }
                List<MoundPlaces.Place> mounds = MoundPlaces.near(seed, x, z, far + 24, MoundPlaces.Numbers.DEFAULT);
                for (int k = 0; k < PAN_RADII.length; k++) {
                    int relief = hi[k] - lo[k];
                    for (int t = 1; t <= 3; t++) {
                        panRelief[k][t - 1] += relief <= t ? 1 : 0;
                    }
                    final int reach = PAN_RADII[k] + 8;
                    boolean clear = mounds.stream()
                            .noneMatch(p -> Math.hypot(p.middle().getX() - x, p.middle().getZ() - z) <= reach);
                    panRelief[k][3] += clear ? 1 : 0;
                    panFlatAndClear[k] += clear && relief <= 2 ? 1 : 0;
                }
                int near = -1;
                for (int r = 0; r <= 40 && near < 0; r++) {
                    for (int a = -r; a <= r && near < 0; a++) {
                        for (int b : new int[] {-r, r}) {
                            int[][] cells = {{i + a, j + b}, {i + b, j + a}};
                            for (int[] q : cells) {
                                if (q[0] >= 0 && q[1] >= 0 && q[0] < side && q[1] < side && map[q[0] * side + q[1]] == BiomeMaps.RIVER) {
                                    near = r * BiomeMaps.STEP;
                                }
                            }
                        }
                    }
                }
                riverFar.add(near < 0 ? 9999 : near);
            }
            panSpots += seedPans;
            // today's pools
            int seedTries = 0;
            for (int c : flood) {
                if (seedTries >= POOL_TRIES) {
                    break;
                }
                seedTries++;
                int x = -BiomeMaps.HALF + (c / side) * BiomeMaps.STEP, z = -BiomeMaps.HALF + (c % side) * BiomeMaps.STEP;
                boolean oxLevel = todayLevel(ground, x, z, 20), pLevel = todayLevel(ground, x, z, 9);
                oxbowLevel += oxLevel ? 1 : 0;
                panLevel += pLevel ? 1 : 0;
                oxbowFits += oxLevel && todayDry(ground, x, z, 20) ? 1 : 0;
                panFits += pLevel && todayDry(ground, x, z, 9) ? 1 : 0;
            }
            tries += seedTries;
        }
        StringBuilder out = new StringBuilder("room for oxbows (floodplain width from the water's edge, and how far the ground stays within a block):");
        for (int b = 0; b < bands; b++) {
            List<Integer> w = floodBy.get(b), f = flatBy.get(b);
            out.append(String.format("%n  %s: %d banks, floodplain median %d (25%% %d, 75%% %d, 90%% %d), none %.0f%%;", INLAND_NAMES[b], w.size(),
                    middle(w, 0.5), middle(w, 0.25), middle(w, 0.75), middle(w, 0.9), 100.0 * (1 - share(w, 4))));
            for (int t : POOL_FLOOD) {
                out.append(String.format(" %d+ %.0f%%", t, 100.0 * share(w, t)));
            }
            out.append(String.format("; flat median %d (75%% %d, 90%% %d)", middle(f, 0.5), middle(f, 0.75), middle(f, 0.9)));
        }
        out.append(String.format("%nroom for pans (%d dry savanna spots off the floodplain; ground within r stays within 1 / 2 / 3 blocks; "
                + "no mound place within r+8; both within 2 and clear):", panSpots));
        for (int k = 0; k < PAN_RADII.length; k++) {
            out.append(String.format("%n  r %d: %.0f%% / %.0f%% / %.0f%%; clear of mounds %.0f%%; flat and clear %.0f%%", PAN_RADII[k],
                    pct(panRelief[k][0], panSpots), pct(panRelief[k][1], panSpots), pct(panRelief[k][2], panSpots),
                    pct(panRelief[k][3], panSpots), pct(panFlatAndClear[k], panSpots)));
        }
        out.append(String.format("%n  nearest river (on the 32-block map) median %d, 25%% %d, 75%% %d", middle(riverFar, 0.5),
                middle(riverFar, 0.25), middle(riverFar, 0.75)));
        out.append(String.format("%ntoday's pools at %d floodplain spots: an oxbow (20 square) level %.0f%%, fits %.0f%%; a pan (9 square) level %.0f%%, fits %.0f%%",
                tries, pct(oxbowLevel, tries), pct(oxbowFits, tries), pct(panLevel, tries), pct(panFits, tries)));
        SavannaMod.LOGGER.info("[biome maps] oxbow and pan room: {}", out);
        helper.assertTrue(floodBy.stream().mapToInt(List::size).sum() >= 30 && panSpots >= 30, "too few spots found to measure: " + out);
        helper.succeed();
    }

    private static double pct(int part, int all) {
        return all == 0 ? 0 : 100.0 * part / all;
    }

    /** Today's pool rule: the ground over a square of {@code size} within a block, above the river's water, at most 4 over it. */
    private static boolean todayLevel(BiomeMaps.Ground ground, int x, int z, int size) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int dx = -size / 2 - 1; dx <= size / 2 + 1; dx += 3) {
            for (int dz = -size / 2 - 1; dz <= size / 2 + 1; dz += 3) {
                int h = ground.height(x + dx, z + dz);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
        }
        return hi - lo <= 1 && lo > SEA_TOP && lo <= SEA_TOP + 4;
    }

    /** Today's pool rule: no water within 8 blocks of the square (read every 4 blocks). */
    private static boolean todayDry(BiomeMaps.Ground ground, int x, int z, int size) {
        int reach = size / 2 + 1 + 8;
        for (int dx = -reach; dx <= reach; dx += 4) {
            for (int dz = -reach; dz <= reach; dz += 4) {
                if (ground.height(x + dx, z + dz) <= SEA_TOP) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Sand banks step 1 (2026-11-16, {@code docs/plans/sand-banks.md}): how the savanna rivers bend, on {@link #GROUND_SEEDS}.
     * From river spots beside savanna, the river's middle line (weirdness 0) is walked both ways in {@link #BEND_STEP}-block
     * steps while it stays in savanna or its river. The real bend at a step is the walked line's turn over a window either
     * side ({@link #BEND_WINDOWS}, blocks each way); the report gives, for each window and radius limit, how often a bend
     * comes, by how far inland; and how often the noise read at one point ({@link com.antcolonies.savanna.worldgen.RiverBends},
     * slopes {@link #BEND_SPANS} apart) puts the inside on the same side as the walked line. The noise only (no ground
     * heights: an earlier version that also asked the water took 11 minutes). Only measures.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "savanna_maps")
    public static void riverBendsAreMeasured(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        int[] limits = {40, 60, 80, 110, 150};
        int[] windows = BEND_WINDOWS, spans = BEND_SPANS;
        int bands = INLAND_NAMES.length;
        double[] length = new double[bands];
        int[][][] bends = new int[windows.length][limits.length][bands];
        List<List<List<Integer>>> bendLengths = new ArrayList<>();
        for (int w = 0; w < windows.length; w++) {
            bendLengths.add(new ArrayList<>());
            for (int L = 0; L < limits.length; L++) {
                bendLengths.get(w).add(new ArrayList<>());
            }
        }
        // agreement of the point reading with the walked turn (when the walked radius is under 120), and of the water
        int[][] pointSame = new int[windows.length][spans.length], pointAll = new int[windows.length][spans.length];
        int walks = 0;
        // sand banks ({@code worldgen/SandBanks}): runs of steps where a bank 12 blocks off the middle line is sand
        int[] sandBanks = new int[bands];
        List<Integer> sandLengths = new ArrayList<>();
        long start = System.nanoTime();
        int qy = QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y);
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            var weird = sampler.weirdness();
            byte[] map = BiomeMaps.map(source, sampler);
            int side = BiomeMaps.SIDE;
            List<Integer> starts = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                int i = c / side, j = c % side;
                if (map[c] == BiomeMaps.RIVER && i > 0 && j > 0 && i < side - 1 && j < side - 1 && BiomeMaps.riverBeside(map, c, BiomeMaps.SAVANNA)) {
                    starts.add(c);
                }
            }
            java.util.Collections.shuffle(starts, new java.util.Random(seed));
            java.util.Set<Long> walked = new java.util.HashSet<>();
            int seedWalks = 0;
            for (int c : starts) {
                if (seedWalks >= BEND_WALKS) {
                    break;
                }
                double[] p = {-BiomeMaps.HALF + (c / side) * BiomeMaps.STEP, -BiomeMaps.HALF + (c % side) * BiomeMaps.STEP};
                if (!toMiddle(weird, p) || walked.contains(cellKey(p))) {
                    continue;
                }
                walks++;
                seedWalks++;
                java.util.Set<Long> mine = new java.util.HashSet<>();
                // the walked line, both ways joined: x, z, band, then the point reading's curvature for each span
                java.util.ArrayDeque<double[]> line = new java.util.ArrayDeque<>();
                for (int dir : new int[] {1, -1}) {
                    double[] q = p.clone();
                    for (int s = 0; s < BEND_WALK / BEND_STEP; s++) {
                        int ci = (int) Math.floor((q[0] + BiomeMaps.HALF) / BiomeMaps.STEP + 0.5), cj = (int) Math.floor((q[1] + BiomeMaps.HALF) / BiomeMaps.STEP + 0.5);
                        if (ci < 0 || cj < 0 || ci >= side || cj >= side) {
                            break;
                        }
                        int cc = ci * side + cj;
                        if (map[cc] != BiomeMaps.SAVANNA && !(map[cc] == BiomeMaps.RIVER && BiomeMaps.riverBeside(map, cc, BiomeMaps.SAVANNA))) {
                            break;
                        }
                        long key = cellKey(q);
                        if (walked.contains(key) && !mine.contains(key)) {
                            break; // another walk's river
                        }
                        mine.add(key);
                        int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[1]);
                        double[] row = new double[6 + spans.length];
                        row[0] = q[0];
                        row[1] = q[1];
                        row[2] = inlandBand(sampler, x, qy, z);
                        var r = com.antcolonies.savanna.worldgen.RiverBends.read(weird, x, z);
                        double g = Math.hypot(r.gx(), r.gz());
                        row[3] = g < 1e-12 ? 0 : r.gx() / g;
                        row[4] = g < 1e-12 ? 0 : r.gz() / g;
                        for (int h = 0; h < spans.length; h++) {
                            row[5 + h] = com.antcolonies.savanna.worldgen.RiverBends.read(weird, x, z, spans[h]).curvature();
                        }
                        row[5 + spans.length] = sandSide(sampler, seed, x, z, row[3], row[4]);
                        if (s > 0 || dir == 1) {
                            if (dir == 1) {
                                line.addLast(row);
                            } else {
                                line.addFirst(row);
                            }
                        }
                        double tx = -r.gz() * dir, tz = r.gx() * dir, tl = Math.hypot(tx, tz);
                        if (tl < 1e-12) {
                            break;
                        }
                        q[0] += BEND_STEP * tx / tl;
                        q[1] += BEND_STEP * tz / tl;
                        if (!toMiddle(weird, q)) {
                            break;
                        }
                    }
                }
                walked.addAll(mine);
                double[][] pts = line.toArray(new double[0][]);
                int n = pts.length;
                for (double[] pt : pts) {
                    length[(int) pt[2]] += BEND_STEP;
                }
                if (n < 3) {
                    continue;
                }
                int sandRun = 0, sandSide = 0;
                for (int i = 0; i <= n; i++) {
                    int here = i < n ? (int) pts[i][5 + spans.length] : 0;
                    if (here != 0 && here == sandSide) {
                        sandRun++;
                        continue;
                    }
                    if (sandSide != 0) {
                        sandLengths.add(sandRun * BEND_STEP);
                    }
                    sandSide = here;
                    sandRun = here != 0 ? 1 : 0;
                    if (here != 0) {
                        sandBanks[(int) pts[i][2]]++;
                    }
                }
                // the walked direction at each step
                double[][] dirs = new double[n][2];
                for (int i = 0; i < n; i++) {
                    int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
                    double dx = pts[b][0] - pts[a][0], dz = pts[b][1] - pts[a][1], dl = Math.hypot(dx, dz);
                    dirs[i][0] = dl < 1e-9 ? 0 : dx / dl;
                    dirs[i][1] = dl < 1e-9 ? 0 : dz / dl;
                }
                for (int w = 0; w < windows.length; w++) {
                    int m = windows[w] / BEND_STEP;
                    // each step's walked bend: radius and the inside's weirdness side (+1: the positive side)
                    double[] radius = new double[n];
                    int[] inside = new int[n];
                    for (int i = 0; i < n; i++) {
                        int a = i - m, b = i + m;
                        if (a < 0 || b >= n) {
                            radius[i] = Double.MAX_VALUE;
                            continue;
                        }
                        double ddx = dirs[b][0] - dirs[a][0], ddz = dirs[b][1] - dirs[a][1], d = Math.hypot(ddx, ddz);
                        double turn = 2 * Math.asin(Math.min(1, d / 2));
                        radius[i] = turn < 1e-6 ? Double.MAX_VALUE : 2.0 * m * BEND_STEP / turn;
                        inside[i] = ddx * pts[i][3] + ddz * pts[i][4] > 0 ? 1 : -1;
                        if (radius[i] < 120) {
                            for (int h = 0; h < spans.length; h++) {
                                double k = pts[i][5 + h];
                                if (Math.abs(k) > 1.0 / 400) {
                                    pointAll[w][h]++;
                                    pointSame[w][h] += (k > 0 ? -1 : 1) == inside[i] ? 1 : 0;
                                }
                            }
                        }
                    }
                    for (int L = 0; L < limits.length; L++) {
                        int run = 0, runSide = 0;
                        for (int i = 0; i <= n; i++) {
                            boolean holds = i < n && radius[i] < limits[L];
                            if (holds && (runSide == 0 || runSide == inside[i])) {
                                if (runSide == 0) {
                                    bends[w][L][(int) pts[i][2]]++;
                                }
                                runSide = inside[i];
                                run++;
                            } else {
                                if (runSide != 0) {
                                    bendLengths.get(w).get(L).add(run * BEND_STEP);
                                }
                                runSide = holds ? inside[i] : 0;
                                run = holds ? 1 : 0;
                                if (holds) {
                                    bends[w][L][(int) pts[i][2]]++;
                                }
                            }
                        }
                    }
                }
            }
        }
        double all = java.util.Arrays.stream(length).sum();
        StringBuilder report = new StringBuilder(String.format("%d walks, %.0f blocks of river; %d ms", walks, all, (System.nanoTime() - start) / 1_000_000));
        for (int h = 0; h < spans.length; h++) {
            report.append(String.format("%n point reading, slopes %d apart:", spans[h]));
            for (int w = 0; w < windows.length; w++) {
                report.append(String.format(" the walked line over %d agrees in %d of %d;", windows[w], pointSame[w][h], pointAll[w][h]));
            }
        }
        for (int w = 0; w < windows.length; w++) {
            report.append(String.format("%n walked line, turn over %d blocks each way:", windows[w]));
            for (int L = 0; L < limits.length; L++) {
                int nb = java.util.Arrays.stream(bends[w][L]).sum();
                report.append(String.format("%n  radius under %d: a bend every %.0f blocks of river (bends %d, length median %d, 90%% %d);",
                        limits[L], nb == 0 ? 0 : all / nb, nb, middle(bendLengths.get(w).get(L), 0.5), middle(bendLengths.get(w).get(L), 0.9)));
                for (int b = 0; b < bands; b++) {
                    report.append(String.format(" %s %s", INLAND_NAMES[b], bends[w][L][b] == 0 ? "-" : String.format("%.0f", length[b] / bends[w][L][b])));
                }
            }
        }
        for (int b = 0; b < bands; b++) {
            report.append(String.format("%n  %s: %.0f blocks walked", INLAND_NAMES[b], length[b]));
        }
        int banks = java.util.Arrays.stream(sandBanks).sum();
        double sandEvery = banks == 0 ? Double.MAX_VALUE : all / banks;
        report.append(String.format("%n sand banks: one every %.0f blocks of river (%d banks, length along the river median %d, 90%% %d);",
                sandEvery, banks, middle(sandLengths, 0.5), middle(sandLengths, 0.9)));
        for (int b = 0; b < bands; b++) {
            report.append(String.format(" %s %s", INLAND_NAMES[b], sandBanks[b] == 0 ? "-" : String.format("%.0f", length[b] / sandBanks[b])));
        }
        SavannaMod.LOGGER.info("[biome maps] river bends: {}", report);
        helper.assertTrue(walks >= 20 && all > 5000, "too little savanna river walked to measure: " + report);
        helper.assertTrue(sandEvery >= SAND_EVERY[0] && sandEvery <= SAND_EVERY[1], "sand banks do not come about every 80 to 120 blocks: " + report);
        helper.succeed();
    }

    /** How often a sand bank must come along the savanna's rivers, in blocks of river (the plan's 80 to 120, with room for chance). */
    static final double[] SAND_EVERY = {70, 140};

    /**
     * Which bank of the river is sand at a point on its middle line: +1 the bank on the side of positive weirdness, -1 the
     * other, 0 neither ({@code worldgen/SandBanks}, read 12 blocks out each way along the slope {@code nx, nz}).
     */
    private static int sandSide(Climate.Sampler sampler, long seed, int x, int z, double nx, double nz) {
        for (int sign : new int[] {1, -1}) {
            int bx = (int) Math.floor(x + sign * 12 * nx), bz = (int) Math.floor(z + sign * 12 * nz);
            var chooser = com.antcolonies.savanna.worldgen.SandBanks.over(sampler.weirdness(), sampler.continentalness(), seed, bx & ~15, bz & ~15);
            if (chooser.at(bx, bz).strength() > 0.5) {
                return sign;
            }
        }
        return 0;
    }

    /** Walks along a river's middle line a seed and how far each walk goes each way, in blocks; the step. */
    static final int BEND_WALKS = 25, BEND_WALK = 2400, BEND_STEP = 4;
    /** How far each way along the walked line its turn is measured, in blocks. */
    static final int[] BEND_WINDOWS = {32, 64};
    /** The slope spacings the point reading is tried at. */
    static final int[] BEND_SPANS = {16, 32, 64};

    /**
     * World eggs step 1 (2026-11-20, {@code docs/plans/world-eggs.md}): crocodile nest sites
     * ({@link com.antcolonies.savanna.worldgen.NestSites}) come about every 250 to 350 blocks of savanna river (the user's
     * numbers), never two close together. On {@link #GROUND_SEEDS} the rivers' middle lines are walked as the bend survey
     * walks them (from river spots beside savanna, both ways, {@link #BEND_STEP}-block steps while in savanna or its
     * river); every site chunk within two chunks of the line (wide rivers' far banks) is met once; reported: blocks of river per site and the gaps
     * between one site and the next along a walk. Fails when a site comes on average less than every 230 or more than
     * every 400 blocks, or one gap in 10 is under 150.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "savanna_maps")
    public static void crocodileNestSitesComeEvery250To350BlocksOfRiver(GameTestHelper helper) {
        List<Integer> gaps = new ArrayList<>();
        double[] r = walkSites(helper, com.antcolonies.savanna.worldgen.NestSites::site, (source, sampler, x, z) -> true, gaps);
        double length = r[0], every = r[1] == 0 ? Double.MAX_VALUE : length / r[1];
        int shortGaps = (int) gaps.stream().filter(g -> g < 150).count();
        SavannaMod.LOGGER.info("[nest sites] {} blocks of river walked, {} sites: one every {} blocks; gaps along a walk (n {}): 10% {}, 25% {}, middle {}, 75% {}, 90% {}; under 150: {} ({} s)",
                (int) length, (int) r[1], String.format("%.0f", every), gaps.size(), pct(gaps, 10), pct(gaps, 25), pct(gaps, 50), pct(gaps, 75), pct(gaps, 90),
                shortGaps, String.format("%.0f", r[2]));
        helper.assertTrue(every >= 230 && every <= 400, String.format("a crocodile nest site every %.0f blocks of river (250 to 350 wanted)", every));
        helper.assertTrue(shortGaps * 10 <= gaps.size(), shortGaps + " of " + gaps.size() + " gaps between sites under 150 blocks");
        helper.succeed();
    }

    /**
     * Hippo pod sites ({@link com.antcolonies.savanna.worldgen.PodSites}, 2026-11-21) come about every 250 blocks of the
     * near and mid-inland rivers (my pick: a bull's stretch), walked as the nest sites are and counted along the hippos'
     * stretches only. Fails when a site comes on average less than every 180 or more than every 380 blocks of those
     * rivers, or one gap in 10 is under 120.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "savanna_maps")
    public static void hippoPodSitesComeAbout250BlocksApartOnTheirRivers(GameTestHelper helper) {
        List<Integer> gaps = new ArrayList<>();
        double[] r = walkSites(helper, com.antcolonies.savanna.worldgen.PodSites::site, com.antcolonies.savanna.worldgen.PodSites::hippoRiver, gaps);
        double length = r[0], every = r[1] == 0 ? Double.MAX_VALUE : length / r[1];
        int shortGaps = (int) gaps.stream().filter(g -> g < 120).count();
        SavannaMod.LOGGER.info("[pod sites] {} blocks of hippo river walked, {} sites: one every {} blocks; gaps along a walk (n {}): 10% {}, 25% {}, middle {}, 75% {}, 90% {}; under 120: {} ({} s)",
                (int) length, (int) r[1], String.format("%.0f", every), gaps.size(), pct(gaps, 10), pct(gaps, 25), pct(gaps, 50), pct(gaps, 75), pct(gaps, 90),
                shortGaps, String.format("%.0f", r[2]));
        helper.assertTrue(every >= 180 && every <= 380, String.format("a hippo pod site every %.0f blocks of their river (about 250 wanted)", every));
        helper.assertTrue(shortGaps * 10 <= gaps.size(), shortGaps + " of " + gaps.size() + " gaps between pod sites under 120 blocks");
        helper.succeed();
    }

    /** A kind of river site's test of a chunk, and the stretches of river its sites are counted along. */
    private interface SiteTest {
        boolean site(MultiNoiseBiomeSource source, Climate.Sampler sampler, long seed, int cx, int cz);
    }

    private interface Stretch {
        boolean counts(MultiNoiseBiomeSource source, Climate.Sampler sampler, int x, int z);
    }

    /**
     * Walks the rivers' middle lines on {@link #GROUND_SEEDS} (as the bend survey walks them) and meets every site chunk
     * within two chunks of the line once; returns the blocks of river walked where {@code stretch} counts and the sites
     * met, and fills {@code gaps} with the blocks along a walk from one site to the next (sorted).
     */
    private static double[] walkSites(GameTestHelper helper, SiteTest test, Stretch stretch, List<Integer> gaps) {
        var registries = helper.getLevel().registryAccess();
        double length = 0;
        int sites = 0;
        long start = System.nanoTime();
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            var weird = sampler.weirdness();
            byte[] map = BiomeMaps.map(source, sampler);
            int side = BiomeMaps.SIDE;
            List<Integer> starts = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                int i = c / side, j = c % side;
                if (map[c] == BiomeMaps.RIVER && i > 0 && j > 0 && i < side - 1 && j < side - 1 && BiomeMaps.riverBeside(map, c, BiomeMaps.SAVANNA)) {
                    starts.add(c);
                }
            }
            java.util.Collections.shuffle(starts, new java.util.Random(seed));
            java.util.Set<Long> walked = new java.util.HashSet<>(), met = new java.util.HashSet<>();
            int seedWalks = 0;
            for (int c : starts) {
                if (seedWalks >= BEND_WALKS) {
                    break;
                }
                double[] p = {-BiomeMaps.HALF + (c / side) * BiomeMaps.STEP, -BiomeMaps.HALF + (c % side) * BiomeMaps.STEP};
                if (!toMiddle(weird, p) || walked.contains(cellKey(p))) {
                    continue;
                }
                seedWalks++;
                java.util.Set<Long> mine = new java.util.HashSet<>();
                for (int dir : new int[] {1, -1}) {
                    double[] q = p.clone();
                    double along = 0, lastSite = -1;
                    for (int s = 0; s < BEND_WALK / BEND_STEP; s++) {
                        int ci = (int) Math.floor((q[0] + BiomeMaps.HALF) / BiomeMaps.STEP + 0.5), cj = (int) Math.floor((q[1] + BiomeMaps.HALF) / BiomeMaps.STEP + 0.5);
                        if (ci < 0 || cj < 0 || ci >= side || cj >= side) {
                            break;
                        }
                        int cc = ci * side + cj;
                        if (map[cc] != BiomeMaps.SAVANNA && !(map[cc] == BiomeMaps.RIVER && BiomeMaps.riverBeside(map, cc, BiomeMaps.SAVANNA))) {
                            break;
                        }
                        long key = cellKey(q);
                        if (walked.contains(key) && !mine.contains(key)) {
                            break;      // another walk's river
                        }
                        mine.add(key);
                        if ((s > 0 || dir == 1) && stretch.counts(source, sampler, (int) Math.floor(q[0]), (int) Math.floor(q[1]))) {
                            length += BEND_STEP;
                        }
                        int cx = Math.floorDiv((int) Math.floor(q[0]), 16), cz = Math.floorDiv((int) Math.floor(q[1]), 16);
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                long site = ((long) (cx + dx) << 32) ^ ((cz + dz) & 0xffffffffL);
                                if (!met.contains(site) && test.site(source, sampler, seed, cx + dx, cz + dz)) {
                                    met.add(site);
                                    sites++;
                                    if (lastSite >= 0) {
                                        gaps.add((int) (along - lastSite));
                                    }
                                    lastSite = along;
                                }
                            }
                        }
                        along += BEND_STEP;
                        var r = com.antcolonies.savanna.worldgen.RiverBends.read(weird, (int) Math.floor(q[0]), (int) Math.floor(q[1]));
                        double tx = -r.gz() * dir, tz = r.gx() * dir, tl = Math.hypot(tx, tz);
                        if (tl < 1e-12) {
                            break;
                        }
                        q[0] += BEND_STEP * tx / tl;
                        q[1] += BEND_STEP * tz / tl;
                        if (!toMiddle(weird, q)) {
                            break;
                        }
                    }
                }
                walked.addAll(mine);
            }
        }
        java.util.Collections.sort(gaps);
        return new double[] {length, sites, (System.nanoTime() - start) / 1e9};
    }

    private static int pct(List<Integer> sorted, int p) {
        return sorted.isEmpty() ? -1 : sorted.get(Math.min(sorted.size() - 1, sorted.size() * p / 100));
    }

    /** Moves the point onto the weirdness' zero line (a few Newton steps); false if it found none near. */
    private static boolean toMiddle(net.minecraft.world.level.levelgen.DensityFunction weird, double[] p) {
        for (int k = 0; k < 6; k++) {
            var r = com.antcolonies.savanna.worldgen.RiverBends.read(weird, (int) Math.floor(p[0]), (int) Math.floor(p[1]));
            double g2 = r.gx() * r.gx() + r.gz() * r.gz();
            if (g2 < 1e-18) {
                return false;
            }
            double sx = r.w() * r.gx() / g2, sz = r.w() * r.gz() / g2;
            if (Math.hypot(sx, sz) > 64) {
                return false;
            }
            p[0] -= sx;
            p[1] -= sz;
            if (Math.hypot(sx, sz) < 1) {
                return true;
            }
        }
        return Math.abs(com.antcolonies.savanna.worldgen.RiverBends.read(weird, (int) Math.floor(p[0]), (int) Math.floor(p[1])).offset()) < 3;
    }

    private static long cellKey(double[] p) {
        return ((long) Math.floor(p[0] / 16) << 32) ^ ((long) Math.floor(p[1] / 16) & 0xffffffffL);
    }

    /** How many blocks of water from a spot along a direction (at most 80). */
    private static int waterRun(BiomeMaps.Ground ground, int x, int z, double dx, double dz) {
        for (int k = 1; k <= 80; k++) {
            if (ground.height((int) Math.floor(x + 0.5 + dx * k), (int) Math.floor(z + 0.5 + dz * k)) > SEA_TOP) {
                return k - 1;
            }
        }
        return 80;
    }

    /** How far each way along a line the river's water is followed, in blocks. */
    static final int WIDTH_LOOK = 120;
    /** River spots measured a seed (more than {@link #RIVER_BANKS}, so each inland band gets enough). */
    static final int RIVER_WIDTH_SPOTS = 300;

    /**
     * How far inland a spot is (big savannas, big rivers, step 1, 2026-11-16, {@code docs/plans/big-savannas-big-rivers.md}):
     * Minecraft's own continentalness bands ({@code OverworldBiomeBuilder}): the ocean and the coast together (rivers
     * reach the sea there), near inland, mid inland, far inland.
     */
    static final String[] INLAND_NAMES = {"coast", "near inland", "mid inland", "far inland"};
    static final float[] INLAND_FROM = {-0.11F, 0.03F, 0.3F};

    static int inlandBand(Climate.Sampler sampler, int x, int qy, int z) {
        float c = Climate.unquantizeCoord(sampler.sample(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z)).continentalness());
        int band = 0;
        while (band < INLAND_FROM.length && c >= INLAND_FROM[band]) {
            band++;
        }
        return band;
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

    /** Chunks of each kind (floodplain, dry savanna) the dry trees test plans its trees in, per seed. */
    static final int DRY_TREE_CHUNKS = 120;

    /**
     * Floodplain trees step 2 (the user, 2026-11-13: "dry savanna trees no longer can grow on the greener dirt"): new land's
     * two giant tree structures, planned as world generation plans them (no chunks made) in chunks on the floodplain and in
     * chunks of dry savanna on the survey seeds, never put a dry tree's foot on floodplain ground, and still put trees in
     * the dry savanna.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "savanna_maps")
    public static void noDryTreeStandsOnTheFloodplain(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var structures = registries.registryOrThrow(Registries.STRUCTURE);
        List<net.minecraft.world.level.levelgen.structure.Structure> trees = new ArrayList<>();
        for (String name : new String[] {"big_savanna_tree", "young_savanna_tree"}) {
            var structure = structures.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, name));
            if (structure == null) {
                throw new GameTestAssertException("no structure " + name);
            }
            trees.add(structure);
        }
        int floodChunks = 0, dryChunks = 0, floodTrees = 0, dryTrees = 0, onFlood = 0;
        String first = "";
        long start = System.nanoTime();
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, level);
            byte[] map = BiomeMaps.map(source, sampler);
            List<net.minecraft.world.level.ChunkPos> flood = new ArrayList<>(), dry = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                if (map[c] != BiomeMaps.SAVANNA) {
                    continue;
                }
                int x = -BiomeMaps.HALF + (c / BiomeMaps.SIDE) * BiomeMaps.STEP, z = -BiomeMaps.HALF + (c % BiomeMaps.SIDE) * BiomeMaps.STEP;
                (floodplain(source, sampler, x, z) ? flood : dry).add(new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4));
            }
            for (int kind = 0; kind < 2; kind++) {
                List<net.minecraft.world.level.ChunkPos> chunks = kind == 0 ? flood : dry;
                int every = Math.max(1, chunks.size() / DRY_TREE_CHUNKS); // spread over the whole map
                for (int i = 0; i < chunks.size(); i += every) {
                    if (kind == 0) {
                        floodChunks++;
                    } else {
                        dryChunks++;
                    }
                    for (var structure : trees) {
                        var context = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(registries, ground.generator(),
                                source, ground.random(), level.getStructureManager(), seed, chunks.get(i), level, structure.biomes()::contains);
                        var stub = structure.findValidGenerationPoint(context);
                        if (stub.isEmpty()) {
                            continue;
                        }
                        BlockPos foot = stub.get().position();
                        if (kind == 0) {
                            floodTrees++;
                        } else {
                            dryTrees++;
                        }
                        if (source.getNoiseBiome(QuartPos.fromBlock(foot.getX()), QuartPos.fromBlock(foot.getY()), QuartPos.fromBlock(foot.getZ()),
                                ground.random().sampler()).is(SavannaRegion.SAVANNA_FLOODPLAIN) && onFlood++ == 0) {
                            first = "seed " + seed + " at " + foot.toShortString();
                        }
                    }
                }
            }
        }
        String report = String.format("%d floodplain chunks planned %d dry trees (moved off it), %d dry savanna chunks %d trees; %d feet"
                + " on the floodplain%s; %d ms", floodChunks, floodTrees, dryChunks, dryTrees, onFlood, first.isEmpty() ? "" : ", the first " + first,
                (System.nanoTime() - start) / 1_000_000);
        SavannaMod.LOGGER.info("[biome maps] dry trees and the floodplain: {}", report);
        helper.assertTrue(onFlood == 0, "dry trees stand on the floodplain: " + report);
        helper.assertTrue(dryTrees >= dryChunks / 4, "too few trees in the dry savanna: " + report);
        helper.succeed();
    }

    /** Floodplain chunks per seed the floodplain trees test plans its trees round. */
    static final int FLOODPLAIN_TREE_CHUNKS = 150;

    /**
     * Floodplain trees step 7 (the user, 2026-11-14: "put the trees into new land so they naturally spawn in"): the
     * sycamore figs and doum groves, planned as world generation plans them (no chunks made) round floodplain chunks of
     * the survey seeds, stand only on the floodplain, on dry ground a block or more over the water (never in it), a grove's
     * palms 8 or more apart; about 1 in 8 dead; never two big figs (mature, grown or dead) within 40 (every fig place
     * round each big one planned too); and figs and groves both turn up.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "savanna_maps")
    public static void floodplainTreesStandOnTheFloodplainAndKeepApart(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var structures = registries.registryOrThrow(Registries.STRUCTURE);
        var sets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        String[] kinds = {"fig", "doum"};
        net.minecraft.world.level.levelgen.structure.Structure[] structure = new net.minecraft.world.level.levelgen.structure.Structure[2];
        net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement[] grid =
                new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement[2];
        for (int k = 0; k < 2; k++) {
            structure[k] = structures.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "floodplain_" + kinds[k]));
            var set = sets.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "floodplain_" + kinds[k] + "s"));
            if (structure[k] == null || set == null) {
                throw new GameTestAssertException("no structure or set for " + kinds[k]);
            }
            grid[k] = (net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement) set.placement();
        }
        int figs = 0, bigFigs = 0, groves = 0, palms = 0, dead = 0, wrong = 0, closeFigs = 0, closePalms = 0;
        String firstWrong = "", firstClose = "";
        long start = System.nanoTime();
        for (long seed : GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, level);
            byte[] map = BiomeMaps.map(source, sampler);
            List<net.minecraft.world.level.ChunkPos> flood = new ArrayList<>();
            for (int c = 0; c < map.length; c++) {
                int x = -BiomeMaps.HALF + (c / BiomeMaps.SIDE) * BiomeMaps.STEP, z = -BiomeMaps.HALF + (c % BiomeMaps.SIDE) * BiomeMaps.STEP;
                if (map[c] == BiomeMaps.SAVANNA && floodplain(source, sampler, x, z)) {
                    flood.add(new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4));
                }
            }
            int every = Math.max(1, flood.size() / FLOODPLAIN_TREE_CHUNKS);
            for (int k = 0; k < 2; k++) {
                java.util.Set<Long> seen = new java.util.HashSet<>();
                for (int i = 0; i < flood.size(); i += every) {
                    int s = grid[k].spacing();
                    var chunk = grid[k].getPotentialStructureChunk(seed, Math.floorDiv(flood.get(i).x, s) * s, Math.floorDiv(flood.get(i).z, s) * s);
                    if (!seen.add(chunk.toLong())) {
                        continue;
                    }
                    List<FloodplainTreeStructure.Tree> trees = planned(structure[k], registries, ground, source, level, seed, chunk);
                    if (trees.isEmpty()) {
                        continue;
                    }
                    if (k == 0) {
                        figs++;
                    } else {
                        groves++;
                        palms += trees.size();
                    }
                    for (int t = 0; t < trees.size(); t++) {
                        var tree = trees.get(t);
                        if (tree.age().startsWith("dead")) {
                            dead++;
                        }
                        String why = badGround(tree.foot(), source, ground, level);
                        if (why != null && wrong++ == 0) {
                            firstWrong = kinds[k] + " seed " + seed + " at " + tree.foot().toShortString() + ": " + why;
                        }
                        for (int u = 0; u < t; u++) {
                            if (flat(tree.foot(), trees.get(u).foot()) < FloodplainTreeStructure.PALM_APART && closePalms++ == 0 && firstClose.isEmpty()) {
                                firstClose = "palms at " + tree.foot().toShortString() + " and " + trees.get(u).foot().toShortString();
                            }
                        }
                    }
                    if (k == 0 && bigFig(trees.get(0))) {
                        bigFigs++;
                        // every fig place round it, planned too: none may be another big fig within 40
                        BlockPos foot = trees.get(0).foot();
                        int fs = grid[0].spacing();
                        int rx = Math.floorDiv(foot.getX() >> 4, fs), rz = Math.floorDiv(foot.getZ() >> 4, fs);
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                var other = grid[0].getPotentialStructureChunk(seed, (rx + dx) * fs, (rz + dz) * fs);
                                if (other.equals(chunk)) {
                                    continue;
                                }
                                for (var near : planned(structure[0], registries, ground, source, level, seed, other)) {
                                    if (bigFig(near) && flat(near.foot(), foot) < FloodplainTreeStructure.BIG_FIG_APART && closeFigs++ == 0) {
                                        firstClose = "big figs at " + foot.toShortString() + " and " + near.foot().toShortString();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        int trees = figs + palms;
        String report = String.format("%d figs (%d big), %d groves of %d palms; %d dead (1 in %.1f); %d on wrong ground%s;"
                + " %d big figs within %d, %d palms within %d%s; %d ms", figs, bigFigs, groves, palms, dead,
                dead == 0 ? 0.0 : trees / (double) dead, wrong, firstWrong.isEmpty() ? "" : ", the first " + firstWrong,
                closeFigs / 2, FloodplainTreeStructure.BIG_FIG_APART, closePalms, FloodplainTreeStructure.PALM_APART,
                firstClose.isEmpty() ? "" : ", " + firstClose, (System.nanoTime() - start) / 1_000_000);
        SavannaMod.LOGGER.info("[biome maps] floodplain trees: {}", report);
        helper.assertTrue(wrong == 0, "floodplain trees off the floodplain or in water: " + report);
        helper.assertTrue(closeFigs == 0 && closePalms == 0, "floodplain trees too close: " + report);
        helper.assertTrue(figs >= 20 && groves >= 20, "too few floodplain trees: " + report);
        // 1 in 8 by the dice; over 100+ trees 1 in 14 to 1 in 5 leaves room for chance
        helper.assertTrue(dead * 14 >= trees && dead * 5 <= trees, "the dead share is off 1 in 8: " + report);
        helper.succeed();
    }

    /** The trees a floodplain tree structure plans in a chunk (none if it plans nothing there). */
    private static List<FloodplainTreeStructure.Tree> planned(net.minecraft.world.level.levelgen.structure.Structure structure,
            net.minecraft.core.RegistryAccess registries, BiomeMaps.Ground ground, MultiNoiseBiomeSource source,
            net.minecraft.server.level.ServerLevel level, long seed, net.minecraft.world.level.ChunkPos chunk) {
        var context = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(registries, ground.generator(),
                source, ground.random(), level.getStructureManager(), seed, chunk, level, structure.biomes()::contains);
        List<FloodplainTreeStructure.Tree> out = new ArrayList<>();
        structure.findValidGenerationPoint(context).ifPresent(stub -> {
            for (var piece : stub.getPiecesBuilder().build().pieces()) {
                out.add(((FloodplainTreePiece) piece).tree());
            }
        });
        return out;
    }

    private static boolean bigFig(FloodplainTreeStructure.Tree tree) {
        return tree.kind().equals("fig") && com.antcolonies.savanna.tree.FigShape.Age.byId(tree.age()).ordinal()
                >= com.antcolonies.savanna.tree.FigShape.Age.MATURE.ordinal();
    }

    /** Why a floodplain tree's foot is on the wrong ground (off the floodplain, in water, on the strip flush with it), or null. */
    private static String badGround(BlockPos foot, MultiNoiseBiomeSource source, BiomeMaps.Ground ground, net.minecraft.server.level.ServerLevel level) {
        if (!source.getNoiseBiome(QuartPos.fromBlock(foot.getX()), QuartPos.fromBlock(foot.getY()), QuartPos.fromBlock(foot.getZ()),
                ground.random().sampler()).is(SavannaRegion.SAVANNA_FLOODPLAIN)) {
            return "not floodplain";
        }
        int surface = com.antcolonies.savanna.worldgen.GroundHeights.at(ground.generator(), foot.getX(), foot.getZ(),
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, ground.random());
        int floor = com.antcolonies.savanna.worldgen.GroundHeights.at(ground.generator(), foot.getX(), foot.getZ(),
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, ground.random());
        if (surface != floor) {
            return "in water";
        }
        return surface <= ground.generator().getSeaLevel() ? "flush with the water" : null;
    }

    private static double flat(BlockPos a, BlockPos b) {
        return Math.sqrt((double) (a.getX() - b.getX()) * (a.getX() - b.getX()) + (double) (a.getZ() - b.getZ()) * (a.getZ() - b.getZ()));
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

    /**
     * Sand banks (2026-11-16, {@code docs/plans/sand-banks.md}) as the river banks' decoration lays them, on the river of
     * the test above, with the sand put on the inside of its bends by the test's own rule (the world's rule reads the land's
     * noise, measured in {@link #riverBendsAreMeasured}): sand on the inside banks and none on the outside, wet sand at the
     * waterline, dry sand over the plain past the low strip a block up, a crocodile slide or more by the deep water, and
     * nothing farther out than the widest bank.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "savanna_rivers")
    public static void sandBanksLieOnTheInsideOfBends(GameTestHelper helper) {
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
        int ox = a.getX() - 1, oz = a.getZ() - 1;
        // the inside of the bend is the side the river curves round (z'' = -5/49 sin(x/7)); a coast bank, the widest
        com.antcolonies.savanna.worldgen.SandBanks.Chooser rule = (x, z) -> {
            double curve = -Math.sin((x - ox) / 7.0);
            boolean inside = Math.abs(curve) > 0.5 && Math.signum(z - oz - mid.applyAsInt(x - ox)) == Math.signum(curve);
            return new com.antcolonies.savanna.worldgen.SandBanks.Choice(inside ? 1 : 0, 0, 0);
        };
        RiverBankFeature.shape(level, a.getX(), a.getZ(), 63, 63, a.getY(), p -> true, rule);
        var tone = SavannaBlocks.RED_SAND;
        java.util.function.Predicate<BlockState> sand = st -> tone.all().stream().anyMatch(b -> st.is(b.get()));
        int inner = 0, innerSand = 0, outerSand = 0, wet = 0, waterline = 0, raised = 0, past = 0, tooFar = 0, slides = 0;
        for (int x = 14; x < 50; x++) {
            int m = mid.applyAsInt(x);
            double curve = -Math.sin(x / 7.0);
            for (int z = 30; z < 64; z++) {
                int out = Math.abs(z - m) - 3;
                if (out <= 0) {
                    continue;
                }
                BlockState low = helper.getBlockState(new BlockPos(x, water, z)), high = helper.getBlockState(new BlockPos(x, plain, z));
                boolean any = sand.test(low) || sand.test(high);
                boolean inside = Math.signum(z - m) == Math.signum(curve);
                if (!inside) {
                    outerSand += any ? 1 : 0;
                    continue;
                }
                if (Math.abs(curve) > 0.8 && out <= 8) {
                    inner++;
                    innerSand += any ? 1 : 0;
                    if (out == 1) {
                        waterline++;
                        wet += low.is(tone.wet().get()) || low.getBlock() instanceof com.antcolonies.savanna.block.RiverSand.Slide ? 1 : 0;
                    } else if (out >= 6) {
                        past++;
                        raised += high.is(tone.dry().get()) ? 1 : 0;
                    }
                }
                if (out >= 13) {
                    tooFar += any ? 1 : 0;
                }
            }
        }
        for (int x = 1; x < 64; x++) { // slides are rare (about 1 waterline column of a bank in 12): counted over the whole river
            for (int z = 1; z < 64; z++) {
                slides += helper.getBlockState(new BlockPos(x, water, z)).getBlock() instanceof com.antcolonies.savanna.block.RiverSand.Slide ? 1 : 0;
            }
        }
        String report = String.format("inside sharp bends %d of %d columns sand, %d sand columns outside; wet sand on %d of %d waterline"
                + " columns, dry sand a block up on %d of %d past the strip, %d slides, %d sand columns too far out", innerSand, inner,
                outerSand, wet, waterline, raised, past, slides, tooFar);
        SavannaMod.LOGGER.info("[sand banks] {}", report);
        helper.assertTrue(innerSand >= inner * 0.8, "the inside of the bends is not sand: " + report);
        helper.assertTrue(outerSand == 0, "sand on the outside of the bends: " + report);
        helper.assertTrue(wet >= waterline * 0.8, "no wet sand at the waterline: " + report);
        helper.assertTrue(raised >= past * 0.6, "no dry sand over the plain past the strip: " + report);
        helper.assertTrue(slides >= 1, "no crocodile slide: " + report);
        helper.assertTrue(tooFar == 0, "sand farther out than the widest bank: " + report);
        helper.succeed();
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
