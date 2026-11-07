package com.antcolonies.savanna.gametest;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import javax.imageio.ImageIO;

import com.antcolonies.savanna.worldgen.SavannaRegion;
import com.antcolonies.savanna.worldgen.SavannaSpawn;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import terrablender.api.RegionType;
import terrablender.worldgen.IExtendedParameterList;
import terrablender.worldgen.noise.Area;
import terrablender.worldgen.noise.LayeredNoiseUtil;

/**
 * The biome maps of big savannas (2026-11-06): the overworld's biomes for a seed read straight from the noise (no
 * chunks), on a grid, measured (how much is savanna, how big each savanna is, how far apart the big ones lie) and drawn
 * as pictures. Used by {@code SavannaWorldTests} to pick and guard {@link SavannaRegion}'s numbers.
 */
public final class BiomeMaps {
    /** Half the side of a map and its grid, in blocks: 16,384 blocks square, a cell every 32. */
    public static final int HALF = 8192, STEP = 32, SIDE = 2 * HALF / STEP;

    /** Kinds of ground on a map. */
    static final byte OCEAN = 0, RIVER = 1, LAND = 2, SAVANNA = 3, DESERT = 4, FOREST = 5, JUNGLE = 6, COLD = 7, HILLS = 8, BADLANDS = 9, PLAINS = 10;

    private BiomeMaps() {
    }

    /** Minecraft's overworld biome source for a seed with TerraBlender's regions (ours among them) laid out for that seed. */
    public static MultiNoiseBiomeSource bigSavannaSource(RegistryAccess registries, long seed) {
        Climate.ParameterList<Holder<Biome>> preset = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD).value().parameters();
        Climate.ParameterList<Holder<Biome>> copy = new Climate.ParameterList<>(preset.values());
        @SuppressWarnings("unchecked")
        IExtendedParameterList<Holder<Biome>> extended = (IExtendedParameterList<Holder<Biome>>) copy;
        extended.initializeForTerraBlender(registries, RegionType.OVERWORLD, seed);
        return MultiNoiseBiomeSource.createFromList(copy);
    }

    public static Climate.Sampler sampler(RegistryAccess registries, long seed) {
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), seed).sampler();
    }

    /** The climate at every cell of a map (worked out once per seed; the slow part). */
    public static Climate.TargetPoint[] climates(Climate.Sampler sampler) {
        Climate.TargetPoint[] out = new Climate.TargetPoint[SIDE * SIDE];
        int qy = QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y);
        for (int i = 0; i < SIDE; i++) {
            for (int j = 0; j < SIDE; j++) {
                out[i * SIDE + j] = sampler.sample(QuartPos.fromBlock(-HALF + i * STEP), qy, QuartPos.fromBlock(-HALF + j * STEP));
            }
        }
        return out;
    }

    /** The map of a real biome source (TerraBlender's own layout). */
    public static byte[] map(MultiNoiseBiomeSource source, Climate.Sampler sampler) {
        int qy = QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y);
        byte[] out = new byte[SIDE * SIDE];
        for (int i = 0; i < SIDE; i++) {
            for (int j = 0; j < SIDE; j++) {
                out[i * SIDE + j] = kind(source.getNoiseBiome(QuartPos.fromBlock(-HALF + i * STEP), qy, QuartPos.fromBlock(-HALF + j * STEP), sampler));
            }
        }
        return out;
    }

    /**
     * A pretend layout for tuning: vanilla's table where TerraBlender's zones (made by its own zooming, our share of them
     * {@code share}) are not ours, the stretched table where they are.
     */
    public static byte[] pretendMap(RegistryAccess registries, long seed, Climate.TargetPoint[] climates, double share, SavannaRegion.Shape shape) {
        var biomes = registries.registryOrThrow(Registries.BIOME);
        Climate.ParameterList<Holder<Biome>> vanilla = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD).value().parameters();
        Climate.ParameterList<Holder<Biome>> ours = new Climate.ParameterList<>(SavannaRegion.table(shape).stream()
                .map(p -> p.mapSecond(key -> (Holder<Biome>) biomes.getHolderOrThrow(key))).toList());
        int scale = 1000;
        int mine = (int) Math.round(share * scale);
        Area zones = LayeredNoiseUtil.createZoomedArea(seed, 3, (context, x, y) -> context.nextRandom(scale) < mine ? 1 : 0);
        byte[] out = new byte[SIDE * SIDE];
        for (int i = 0; i < SIDE; i++) {
            for (int j = 0; j < SIDE; j++) {
                Climate.TargetPoint t = climates[i * SIDE + j];
                boolean isOurs = zones.get(QuartPos.fromBlock(-HALF + i * STEP), QuartPos.fromBlock(-HALF + j * STEP)) == 1;
                // (findValue is Minecraft's own search: TerraBlender changes only the positional one)
                out[i * SIDE + j] = kind((isOurs ? ours : vanilla).findValue(t));
            }
        }
        return out;
    }

    static byte kind(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return SAVANNA;
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            return OCEAN;
        }
        if (biome.is(BiomeTags.IS_RIVER)) {
            return RIVER;
        }
        if (biome.is(Biomes.DESERT)) {
            return DESERT;
        }
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return BADLANDS;
        }
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return JUNGLE;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
            return HILLS;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) {
            return biome.value().coldEnoughToSnow(net.minecraft.core.BlockPos.ZERO.above(64)) ? COLD : FOREST;
        }
        if (biome.value().coldEnoughToSnow(net.minecraft.core.BlockPos.ZERO.above(64))) {
            return COLD;
        }
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == Biomes.PLAINS || key == Biomes.SUNFLOWER_PLAINS || key == Biomes.MEADOW) {
            return PLAINS;
        }
        return LAND;
    }

    /** What a map holds of one kind of ground. */
    public record Patches(double coverage, int typical, int biggest, int bigOnes, int spacing, List<Integer> sizes) {
        @Override
        public String toString() {
            return String.format("%.1f%% of land, typical %d blocks across, biggest %d, %d of 1000+ (one per %d blocks square)",
                    100 * coverage, typical, biggest, bigOnes, spacing);
        }
    }

    /**
     * The patches of one kind on a map: each patch's size as the width of a circle of its area ("blocks across"),
     * rivers inside a patch counted as joining it (not as its area). Typical: the size of the patch a random spot of that
     * kind lies in (the middle of them, by area). Spacing: the side of the square of land holding one patch of 1,000 or
     * more blocks across.
     */
    public static Patches patches(byte[] map, byte kindOf) {
        boolean[] seen = new boolean[map.length];
        List<Integer> areas = new ArrayList<>();
        int land = 0, total = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int c = 0; c < map.length; c++) {
            if (map[c] != OCEAN) {
                land++;
            }
            if (map[c] != kindOf || seen[c]) {
                continue;
            }
            int area = 0;
            seen[c] = true;
            queue.add(c);
            while (!queue.isEmpty()) {
                int at = queue.poll();
                if (map[at] == kindOf) {
                    area++;
                }
                int i = at / SIDE, j = at % SIDE;
                int[][] next = {{i + 1, j}, {i - 1, j}, {i, j + 1}, {i, j - 1}};
                for (int[] n : next) {
                    if (n[0] < 0 || n[1] < 0 || n[0] >= SIDE || n[1] >= SIDE) {
                        continue;
                    }
                    int k = n[0] * SIDE + n[1];
                    if (!seen[k] && (map[k] == kindOf || (map[k] == RIVER && map[at] == kindOf))) {
                        seen[k] = true;
                        queue.add(k);
                    }
                }
            }
            areas.add(area);
            total += area;
        }
        IntFunction<Integer> across = cells -> (int) Math.round(2 * Math.sqrt(cells * (double) STEP * STEP / Math.PI));
        List<Integer> sizes = areas.stream().sorted((a, b) -> b - a).map(across::apply).toList();
        int typical = 0;
        int half = total / 2, run = 0;
        for (int a : areas.stream().sorted((a, b) -> b - a).toList()) {
            run += a;
            if (run >= half) {
                typical = across.apply(a);
                break;
            }
        }
        int big = (int) sizes.stream().filter(s -> s >= 1000).count();
        double landArea = land * (double) STEP * STEP;
        return new Patches(land == 0 ? 0 : total / (double) land, typical, sizes.isEmpty() ? 0 : sizes.get(0), big,
                big == 0 ? -1 : (int) Math.sqrt(landArea / big), sizes);
    }

    private static final int[] COLOURS = {
            0x2f5fa8, // ocean
            0x6fa8e8, // river
            0x7ea35a, // other land
            0xd8b13a, // savanna
            0xf2e3a6, // desert
            0x2f7a35, // forest
            0x1f5a1a, // jungle
            0xe8eef0, // cold
            0x8a8a80, // hills and mountains
            0xc8642c, // badlands
            0x9cc56a, // plains
    };

    /** Draws maps side by side (one pixel a cell, a 16-pixel gap) into a picture. */
    public static void draw(Path file, byte[]... maps) throws java.io.IOException {
        int gap = 16;
        BufferedImage image = new BufferedImage(maps.length * SIDE + (maps.length - 1) * gap, SIDE, BufferedImage.TYPE_INT_RGB);
        for (int m = 0; m < maps.length; m++) {
            for (int x = 0; x < SIDE; x++) {
                for (int y = 0; y < SIDE; y++) {
                    image.setRGB(m * (SIDE + gap) + x, y, COLOURS[maps[m][x * SIDE + y]]);
                }
            }
            for (int x = 0; x < SIDE; x++) { // a 1,000-block scale bar at the bottom left
                if (x >= 8 && x < 8 + 1000 / STEP) {
                    image.setRGB(m * (SIDE + gap) + x, SIDE - 8, 0x000000);
                    image.setRGB(m * (SIDE + gap) + x, SIDE - 9, 0x000000);
                }
            }
        }
        for (int m = 1; m < maps.length; m++) {
            for (int x = 0; x < gap; x++) {
                for (int y = 0; y < SIDE; y++) {
                    image.setRGB(m * (SIDE + gap) - gap + x, y, 0xffffff);
                }
            }
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }
}
