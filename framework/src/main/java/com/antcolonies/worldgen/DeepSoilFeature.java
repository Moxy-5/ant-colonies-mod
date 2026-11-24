package com.antcolonies.worldgen;

import com.antcolonies.block.DeepSoil;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Lays Deep Soil under the dirt: in every column of the chunk whose surface is dirt (grass, dirt, podzol, mud...), the
 * first {@link #MIN_LAYERS} to {@link #MAX_LAYERS} blocks of plain stone right under the dirt become Deep Soil. How many
 * is decided per biome (by the world seed), so each biome has its own thickness. Columns with a sand, stone or snow
 * surface are left alone, so only biomes with dirt get it.
 *
 * <p>Shared ground (it came in with the harvester ant and lived in its desert mod until that was taken out, 2026-11-23):
 * vanilla dirt is 3 or 4 blocks thick, and every digging species, the termites' galleries and rooms first, needs the
 * 5 to 7 soft layers under it. Switched on for every overworld biome by
 * {@code data/colonyframework/neoforge/biome_modifier/deep_soil.json}.
 */
public class DeepSoilFeature extends Feature<NoneFeatureConfiguration> {
    public static final int MIN_LAYERS = 5;
    public static final int MAX_LAYERS = 7;
    /** How far below the surface the dirt is followed before giving up. */
    private static final int MAX_DIRT_DEPTH = 12;

    public DeepSoilFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BlockState deepSoil = DeepSoil.BLOCK.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                pos.set(x, top, z);
                if (!level.getBlockState(pos).is(BlockTags.DIRT)) {
                    continue;
                }
                // follow the dirt down to the stone under it
                int y = top;
                while (y > top - MAX_DIRT_DEPTH && level.getBlockState(pos.set(x, y, z)).is(BlockTags.DIRT)) {
                    y--;
                }
                int layers = layersFor(level, pos.set(x, top, z));
                for (int i = 0; i < layers; i++) {
                    pos.set(x, y - i, z);
                    if (!level.getBlockState(pos).is(BlockTags.BASE_STONE_OVERWORLD)) {
                        break;
                    }
                    level.setBlock(pos, deepSoil, 2);
                    placed = true;
                }
            }
        }
        return placed;
    }

    /** The thickness for the biome at this spot: the same everywhere in that biome, different between biomes and seeds. */
    private static int layersFor(WorldGenLevel level, BlockPos pos) {
        long key = level.getBiome(pos).unwrapKey().map(k -> (long) k.location().hashCode()).orElse(0L);
        RandomSource random = RandomSource.create(level.getSeed() ^ (key * 0x9E3779B97F4A7C15L));
        return MIN_LAYERS + random.nextInt(MAX_LAYERS - MIN_LAYERS + 1);
    }
}
