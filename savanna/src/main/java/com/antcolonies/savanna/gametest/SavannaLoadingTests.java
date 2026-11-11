package com.antcolonies.savanna.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.antcolonies.savanna.SavannaConfig;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.worldgen.SavannaSpawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Savanna loading, round 2 ({@code docs/plans/savanna-loading-2.md}): how long the land takes to work out, and that a
 * speed-up never changes it.
 *
 * <p>Before a chunk is made, Minecraft works out a single column's height (our giant trees and mound places ask that
 * hundreds of times a second) or a single biome sample (the spawn search, structures) straight from the world's shape
 * maths, with none of the keeping it does while it builds a whole chunk. This test times exactly those questions on the
 * real world builder for {@link SavannaWorldTests#GROUND_SEEDS}, in savanna and outside it, and the spawn search, and
 * writes the land's fingerprint ({@code land-check/seed_<seed>.txt} in the test server's folder): at {@link #COLUMNS}
 * columns a seed the ground's height, the biome and the climate numbers at three heights, and for every fourth column
 * every block of it, from the bottom of the world to the top. If the mod holds a saved fingerprint
 * ({@code data/antcolonies/land_check/}), every column must match it exactly: the land must not move.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class SavannaLoadingTests {
    /** Columns a seed, spread at random over this many blocks round the middle. */
    static final int COLUMNS = 2000, SPREAD = 3000;
    /** Every n-th column is checked block for block too (the whole column is slow to make). */
    static final int WHOLE_EVERY = 4;
    /** The heights the climate is read at (the deep, the surface and the hills). */
    static final int[] CLIMATE_Y = {-40, SavannaSpawn.SAMPLE_Y, 160};
    static final String CHECK_FOLDER = "land_check";

    @GameTest(template = "arena", timeoutTicks = 100, batch = "savanna_loading")
    public static void theLandIsQuickToWorkOutAndNeverMoves(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        warmUp(helper);
        StringBuilder report = new StringBuilder();
        report.append(whereTheTimeGoes(helper));
        List<String> mismatches = new ArrayList<>();
        boolean anySaved = false;
        double[] allHeight = new double[2];
        int[] allCount = new int[2];
        for (long seed : SavannaWorldTests.GROUND_SEEDS) {
            Climate.Sampler sampler = BiomeMaps.sampler(registries, seed);
            MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, seed);
            BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, seed, helper.getLevel());
            Random dice = new Random(seed);
            List<String> lines = new ArrayList<>();
            long[] heightNanos = new long[2];
            int[] heightCount = new int[2];
            long biomeNanos = 0;
            long wholeNanos = 0;
            int wholeCount = 0;
            int qy = QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y);
            for (int i = 0; i < COLUMNS; i++) {
                int x = dice.nextInt(2 * SPREAD + 1) - SPREAD;
                int z = dice.nextInt(2 * SPREAD + 1) - SPREAD;
                long t0 = System.nanoTime();
                Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z), sampler);
                long t1 = System.nanoTime();
                int height = ground.generator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ground.level(), ground.random());
                long t2 = System.nanoTime();
                if (i % 4 == 0) {
                    // Loading time, 2026-11-10: the remembered ground heights (both kinds from one look down the column)
                    // must be exactly Minecraft's own answers.
                    int keptTop = com.antcolonies.savanna.worldgen.GroundHeights.at(ground.generator(), x, z, Heightmap.Types.WORLD_SURFACE_WG, ground.level(), ground.random());
                    int keptBed = com.antcolonies.savanna.worldgen.GroundHeights.at(ground.generator(), x, z, Heightmap.Types.OCEAN_FLOOR_WG, ground.level(), ground.random());
                    int bed = ground.generator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ground.level(), ground.random());
                    if ((keptTop != height || keptBed != bed) && mismatches.size() < 8) {
                        mismatches.add("seed " + seed + " column " + x + ", " + z + ": the remembered heights " + keptTop + " and " + keptBed
                                + " are not Minecraft's " + height + " and " + bed);
                    }
                }
                int kind = biome.is(BiomeTags.IS_SAVANNA) ? 1 : 0;
                biomeNanos += t1 - t0;
                heightNanos[kind] += t2 - t1;
                heightCount[kind]++;
                long climate = 17;
                for (int y : CLIMATE_Y) {
                    Climate.TargetPoint p = sampler.sample(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
                    climate = climate * 31 + Arrays.hashCode(new long[] {p.temperature(), p.humidity(), p.continentalness(), p.erosion(), p.depth(), p.weirdness()});
                }
                String whole = "-";
                if (i % WHOLE_EVERY == 0) {
                    long t3 = System.nanoTime();
                    var column = ground.generator().getBaseColumn(x, z, ground.level(), ground.random());
                    wholeNanos += System.nanoTime() - t3;
                    wholeCount++;
                    long hash = 17;
                    for (int y = ground.level().getMinBuildHeight(); y < ground.level().getMaxBuildHeight(); y++) {
                        BlockState state = column.getBlock(y);
                        hash = hash * 31 + Block.getId(state);
                    }
                    whole = Long.toHexString(hash);
                }
                String name = biome.unwrapKey().map(k -> k.location().getPath()).orElse("?");
                lines.add(x + " " + z + " " + height + " " + name + " " + Long.toHexString(climate) + " " + whole);
            }
            write(seed, lines);
            List<String> saved = saved(seed);
            if (saved != null) {
                anySaved = true;
                int wrong = 0;
                for (int i = 0; i < lines.size(); i++) {
                    String was = i < saved.size() ? saved.get(i) : "(none)";
                    if (!was.equals(lines.get(i))) {
                        if (wrong++ < 5) {
                            mismatches.add("seed " + seed + " column " + i + ": was [" + was + "], now [" + lines.get(i) + "]");
                        }
                    }
                }
                if (wrong > 5) {
                    mismatches.add("seed " + seed + ": " + wrong + " columns differ in all");
                }
            }
            long spawnStart = System.nanoTime();
            BlockPos vanilla = sampler.findSpawnPosition();
            long vanillaDone = System.nanoTime();
            var found = source.findBiomeHorizontal(vanilla.getX(), SavannaSpawn.SAMPLE_Y, vanilla.getZ(), SavannaConfig.SPAWN_SEARCH_RADIUS.getDefault(), 32,
                    b -> b.is(BiomeTags.IS_SAVANNA), RandomSource.create(seed), true, sampler);
            long foundDone = System.nanoTime();
            if (found != null) {
                SavannaSpawn.deepInside(source, sampler, found.getFirst());
            }
            long spawnDone = System.nanoTime();
            report.append(String.format("%n  seed %d: a ground height %.2f ms in savanna (%d), %.2f ms outside (%d); a biome sample %.3f ms;"
                            + " a whole column %.2f ms; the spawn search %.1f s (Minecraft's spawn %.1f, the nearest savanna %.1f, its deep inside %.1f)",
                    seed, ms(heightNanos[1]) / Math.max(1, heightCount[1]), heightCount[1], ms(heightNanos[0]) / Math.max(1, heightCount[0]),
                    heightCount[0], ms(biomeNanos) / COLUMNS, ms(wholeNanos) / Math.max(1, wholeCount), (spawnDone - spawnStart) / 1e9,
                    (vanillaDone - spawnStart) / 1e9, (foundDone - vanillaDone) / 1e9, (spawnDone - foundDone) / 1e9));
            for (int k = 0; k < 2; k++) {
                allHeight[k] += ms(heightNanos[k]);
                allCount[k] += heightCount[k];
            }
        }
        String all = String.format("a ground height %.2f ms in savanna, %.2f ms outside%s%s",
                allHeight[1] / Math.max(1, allCount[1]), allHeight[0] / Math.max(1, allCount[0]), report,
                anySaved ? (mismatches.isEmpty() ? "\n  the land matches the saved fingerprint exactly" : "") : "\n  no saved fingerprint in the mod: nothing compared");
        SavannaMod.LOGGER.info("[land speed] {}", all);
        helper.assertTrue(mismatches.isEmpty(), "the land moved: " + String.join("; ", mismatches) + "\n" + all);
        helper.succeed();
    }

    /** A few hundred columns of another seed first, so Java has compiled the shape maths before the clock runs. */
    private static void warmUp(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        Climate.Sampler sampler = BiomeMaps.sampler(registries, 99L);
        MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, 99L);
        BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, 99L, helper.getLevel());
        Random dice = new Random(99L);
        for (int i = 0; i < 300; i++) {
            int x = dice.nextInt(2 * SPREAD + 1) - SPREAD;
            int z = dice.nextInt(2 * SPREAD + 1) - SPREAD;
            source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y), QuartPos.fromBlock(z), sampler);
            ground.height(x, z);
        }
    }

    /**
     * Where a ground height's time goes (step L1): 300 heights of another seed while a watcher thread looks at what this
     * thread is doing every half millisecond; the methods seen most (counted once a look, wherever in the call chain),
     * as a share of the looks. A poor man's profiler, but it needs nothing outside the game.
     */
    private static String whereTheTimeGoes(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        MultiNoiseBiomeSource source = BiomeMaps.bigSavannaSource(registries, 7L);
        BiomeMaps.Ground ground = BiomeMaps.ground(registries, source, 7L, helper.getLevel());
        Thread me = Thread.currentThread();
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        int[] looks = {0};
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        Thread watcher = new Thread(() -> {
            while (!done.get()) {
                StackTraceElement[] stack = me.getStackTrace();
                java.util.Set<String> once = new java.util.HashSet<>();
                for (StackTraceElement e : stack) {
                    String c = e.getClassName();
                    once.add(c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName());
                }
                synchronized (seen) {
                    looks[0]++;
                    for (String s : once) {
                        seen.merge(s, 1, Integer::sum);
                    }
                }
                java.util.concurrent.locks.LockSupport.parkNanos(500_000);
            }
        }, "land speed watcher");
        watcher.setDaemon(true);
        Random dice = new Random(7L);
        long start = System.nanoTime();
        watcher.start();
        for (int i = 0; i < 300; i++) {
            ground.height(dice.nextInt(2 * SPREAD + 1) - SPREAD, dice.nextInt(2 * SPREAD + 1) - SPREAD);
        }
        done.set(true);
        long took = System.nanoTime() - start;
        try {
            watcher.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        StringBuilder out = new StringBuilder(String.format("%n  where a height's time goes (%.2f ms each, %d looks):", ms(took) / 300, looks[0]));
        synchronized (seen) {
            seen.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(45)
                    .forEach(e -> out.append(String.format("%n    %3.0f%% %s", 100.0 * e.getValue() / Math.max(1, looks[0]), e.getKey())));
        }
        return out.toString();
    }

    private static double ms(long nanos) {
        return nanos / 1e6;
    }

    private static void write(long seed, List<String> lines) {
        try {
            Path folder = FMLPaths.GAMEDIR.get().resolve("land-check");
            Files.createDirectories(folder);
            Files.write(folder.resolve("seed_" + seed + ".txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            SavannaMod.LOGGER.warn("[land speed] could not write the fingerprint of seed {}: {}", seed, e.toString());
        }
    }

    /** The fingerprint kept in the mod ({@code data/antcolonies/land_check/seed_<seed>.txt}), or null if none. */
    private static List<String> saved(long seed) {
        try {
            Path file = ModList.get().getModFileById(SavannaMod.MODID).getFile()
                    .findResource("data", SavannaMod.MODID, CHECK_FOLDER, "seed_" + seed + ".txt");
            return file != null && Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
