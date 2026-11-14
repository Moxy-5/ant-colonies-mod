package com.antcolonies.savanna.worldgen;

import java.util.function.Predicate;

import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Lays river algae ({@link AlgaeBlock}) on the beds of the savanna's waters in new land (M9 river life, 2026-11-14): the
 * river, and the pans and oxbows of the floodplain. How thick by the water's depth, with patches (my picks): in 1 or 2
 * blocks of water mostly lush, in 3 or 4 thick or thin, deeper thin or bare and here and there none (the channel).
 * Runs after the banks and the pools ({@code vegetal_decoration}), only on its own chunk's columns, reading only them.
 */
public class RiverAlgaeFeature extends Feature<NoneFeatureConfiguration> {
    /** How far over sea level a water surface is looked for (the pans and oxbows lie a block or two up on the plain). */
    static final int ABOVE_SEA = 6;
    /** Water deeper than this is left bare (a lake's depths, not a river). */
    static final int DEEPEST = 12;

    public RiverAlgaeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        return lay(level, x0, z0, 16, 16, level.getSeaLevel() - 1, at -> level.getBiome(at).is(RiverBankFeature.WATERS)) > 0;
    }

    /**
     * Lays algae in the columns {@code x0..x0+w-1, z0..z0+d-1} under water whose surface is at most {@link #ABOVE_SEA}
     * over {@code sea} (sea level in the world; any height in a game test), only columns {@code ours} says yes to. How
     * many algae it laid.
     */
    public static int lay(WorldGenLevel level, int x0, int z0, int w, int d, int sea, Predicate<BlockPos> ours) {
        int laid = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                at.set(x, sea, z);
                if (!ours.test(at)) {
                    continue;
                }
                // the water's surface: the highest water with air over it, from a little over sea level down
                int y = sea + ABOVE_SEA;
                while (y > sea - 2 && !(isWater(level.getBlockState(at.set(x, y, z))) && level.getBlockState(at.set(x, y + 1, z)).isAir())) {
                    y--;
                }
                if (y <= sea - 2) {
                    continue;
                }
                int depth = 0;
                while (depth <= DEEPEST && isWater(level.getBlockState(at.set(x, y - depth, z)))) {
                    depth++;
                }
                if (depth == 0 || depth > DEEPEST) {
                    continue;
                }
                BlockPos cell = new BlockPos(x, y - depth + 1, z);
                if (!level.getBlockState(cell).is(Blocks.WATER)) {
                    continue;
                }
                int growth = growthFor(depth, x, z);
                if (growth < 0) {
                    continue;
                }
                BlockState algae = SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, growth);
                if (algae.canSurvive(level, cell)) {
                    level.setBlock(cell, algae, Block.UPDATE_CLIENTS);
                    laid++;
                }
            }
        }
        return laid;
    }

    /** Still water: a source block (algae counts as water too, so chunks made in any order agree). */
    private static boolean isWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource();
    }

    /**
     * How thick the mat starts in water this deep at this column (-1 for none): the depth's most, sometimes a stage
     * less where a smooth patchiness says so, so the bed is patchy and never one flat carpet.
     */
    static int growthFor(int depth, int x, int z) {
        int most = AlgaeBlock.mostFor(depth);
        double patch = RiverBankFeature.wobble(x * 0.35, z * 0.35) + 0.35 * (RiverBankFeature.hash(x, z) - 0.5);
        if (most == AlgaeBlock.THIN) {
            return patch > 0.15 ? AlgaeBlock.THIN : patch > -0.45 ? AlgaeBlock.BARE : -1;
        }
        return patch > -0.2 ? most : most - 1;
    }
}
