package com.antcolonies.savanna.worldgen;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Minecraft's own savanna trees in a savanna too small to live in (the user's idea, 2026-11-16: "then it would just look
 * like Minecraft's normal savanna"; {@code docs/plans/big-savannas-big-rivers.md}, step 2d). Our giant trees replaced
 * Minecraft's acacias in every savanna ({@code no_vanilla_savanna_trees.json}); where the gate ({@link BigSavanna}) keeps
 * the ecosystem out, the giant trees stay out too, and this puts Minecraft's back, so a small patch is an ordinary
 * Minecraft savanna and not bare grass.
 *
 * <p>Once a chunk: where the chunk's middle is savanna that does not live, Minecraft's own tree picker for savannas (four
 * acacias in five, else an oak) is run at as many random spots as Minecraft would
 * (1, sometimes 2; windswept 2, sometimes 3), on dry ground in savanna. Minecraft's own placement list cannot be reused:
 * its last step asks whether the biome still lists the feature, and we took it off the list.
 */
public class QuietSavannaTreesFeature extends Feature<NoneFeatureConfiguration> {
    public QuietSavannaTreesFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        BlockPos middle = new BlockPos(x0 + 8, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0 + 8, z0 + 8), z0 + 8);
        Holder<Biome> here = level.getBiome(middle);
        if (!here.is(BiomeTags.IS_SAVANNA) || BigSavanna.lives(level.getLevel(), here, middle.getX(), middle.getZ())) {
            return false;
        }
        boolean windswept = here.is(Biomes.WINDSWEPT_SAVANNA);
        ConfiguredFeature<?, ?> trees = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getOrThrow(VegetationFeatures.TREES_SAVANNA);
        int count = (windswept ? 2 : 1) + (random.nextFloat() < 0.1F ? 1 : 0);
        boolean any = false;
        for (int i = 0; i < count; i++) {
            int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            if (floor != level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)) {
                continue; // water, or something already standing there
            }
            BlockPos at = new BlockPos(x, floor, z);
            if (level.getBiome(at).is(BiomeTags.IS_SAVANNA)) {
                any |= trees.place(level, context.chunkGenerator(), random, at);
            }
        }
        return any;
    }
}
