package com.antcolonies.worldgen;

import com.antcolonies.registry.ModBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Lays Deep Soil under the dirt: in every column of the chunk whose surface is dirt (grass, dirt, podzol, mud...), the
 * dirt is made {@link #MIN_DIRT} to {@link #MAX_DIRT} blocks thick (plain stone under it turns to dirt), and the
 * next {@link #DEEP_LAYERS} blocks of plain stone become Deep Soil. The dirt thickness is decided per biome (by the
 * world seed), so each biome has its own. Columns with a sand, stone or snow
 * surface are left alone, so only biomes with dirt get it.
 */
public class DeepSoilFeature extends Feature<NoneFeatureConfiguration> {
    /** The dirt is made at least this thick (counted from the surface, grass included): 6 to 7 per biome. */
    public static final int MIN_DIRT = 6;
    public static final int MAX_DIRT = 7;
    /** Deep Soil layers under the dirt, so dirt and Deep Soil together are 10 to 11 blocks. */
    public static final int DEEP_LAYERS = 4;
    /** How far below the surface the dirt is followed before giving up. */
    private static final int MAX_DIRT_DEPTH = 12;

    public DeepSoilFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BlockState deepSoil = ModBlocks.DEEP_SOIL.get().defaultBlockState();
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
                // follow the dirt down, turning plain stone into dirt until the dirt is thick enough
                int dirt = dirtFor(level, pos.set(x, top, z));
                int y = top;
                while (y > top - MAX_DIRT_DEPTH) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.is(BlockTags.DIRT)) {
                        y--;
                    } else if (top - y < dirt && state.is(BlockTags.BASE_STONE_OVERWORLD)) {
                        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
                        placed = true;
                        y--;
                    } else {
                        break;
                    }
                }
                for (int i = 0; i < DEEP_LAYERS; i++) {
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

    /** The dirt thickness for the biome at this spot: the same everywhere in that biome, different between biomes and seeds. */
    private static int dirtFor(WorldGenLevel level, BlockPos pos) {
        long key = level.getBiome(pos).unwrapKey().map(k -> (long) k.location().hashCode()).orElse(0L);
        RandomSource random = RandomSource.create(level.getSeed() ^ (key * 0x9E3779B97F4A7C15L));
        return MIN_DIRT + random.nextInt(MAX_DIRT - MIN_DIRT + 1);
    }
}
