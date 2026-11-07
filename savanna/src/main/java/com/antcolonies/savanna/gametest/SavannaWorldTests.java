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
