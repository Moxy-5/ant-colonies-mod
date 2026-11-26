package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.SavannaMudBlock;
import com.antcolonies.savanna.block.SavannaMudBlock.Tone;
import com.antcolonies.savanna.block.SavannaMudBlock.Wetness;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * New land's river mud areas (the user, 2026-11-21: "special mud areas along the river"; {@code docs/plans/savanna-mud.md},
 * section 5.1): on a river mud site ({@link MudSites#river}), after the banks are shaped ({@link RiverBankFeature}), a
 * strip of olive mud at the waterline, where animals come out of the water: {@link #LONG_MIN} to {@link #LONG_MAX} blocks
 * along the river, {@link #BACK_MIN} to {@link #BACK_MAX} back from it (no further than the low strip, which is flush with
 * the water), sticky round its edge and soupy inside, darker toward its middle. Every block lies within reach of the river
 * ({@link SavannaMudBlock#WATER_REACH}), so it never dries (until seasons lower the river). Never on a sand bank: only the
 * bank's grass, soil, silt and river mud become mud. Kept to its own chunk, whose banks are shaped already (a neighbour's
 * are cut when that chunk is made). Numbers my picks.
 */
public class RiverMudFeature extends Feature<NoneFeatureConfiguration> {
    static final int LONG_MIN = 8, LONG_MAX = 16, BACK_MIN = 3, BACK_MAX = 6;
    /** How far a column may lie from the water and still be on the waterline the strip is measured from. */
    private static final double WATERLINE = 1.5;
    /** How far round a column the water is looked for (the strip's back and a block). */
    private static final int LOOK = BACK_MAX + 1;

    public RiverMudFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        BlockPos middle = new BlockPos(x0 + 8, level.getSeaLevel(), z0 + 8);
        if (!BigSavanna.lives(level.getLevel(), level.getBiome(middle), x0 + 8, z0 + 8)) {
            return false;       // the gate: no river life in a savanna too small to live in
        }
        var source = level.getLevel().getChunkSource();
        if (!MudSites.river(source.getGenerator().getBiomeSource(), source.randomState().sampler(), level.getSeed(), x0 >> 4, z0 >> 4)) {
            return false;
        }
        int laid = lay(level, context.random(), x0, z0, level.getSeaLevel() - 1);
        SavannaMod.LOGGER.info("[world mud] river mud site at {} {}: {} blocks", x0 + 8, z0 + 8, laid);
        return laid > 0;
    }

    /**
     * Lays a river mud area in the 16 by 16 columns from {@code x0, z0} beside water whose top is {@code top}; how many
     * mud blocks it laid (0 when the chunk has no bank to lay it on). Also the game tests' way in.
     */
    public static int lay(WorldGenLevel level, RandomSource random, int x0, int z0, int top) {
        RiverBankFeature.Waters waters = new RiverBankFeature.Waters(level, x0 - LOOK - 1, z0 - LOOK - 1, 16 + 2 * (LOOK + 1), top);
        // the middle: the waterline column nearest the chunk's middle
        BlockPos centre = null;
        double best = Double.MAX_VALUE;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                if (bank(level, x, z, top) && waters.distance(x, z, 2) <= WATERLINE) {
                    double d = (x - x0 - 7.5) * (x - x0 - 7.5) + (z - z0 - 7.5) * (z - z0 - 7.5);
                    if (d < best) {
                        best = d;
                        centre = new BlockPos(x, top, z);
                    }
                }
            }
        }
        if (centre == null) {
            return 0;
        }
        double along = (LONG_MIN + random.nextInt(LONG_MAX - LONG_MIN + 1)) / 2.0;
        double back = BACK_MIN + random.nextInt(BACK_MAX - BACK_MIN + 1);
        int laid = 0;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                double r = Math.sqrt(centre.distSqr(new BlockPos(x, top, z)));
                if (r > along + 0.5 * RiverBankFeature.wobble(x * 0.9, z * 0.9) || !bank(level, x, z, top)) {
                    continue;
                }
                double out = waters.distance(x, z, LOOK) - 0.5;      // from the water's edge
                if (out >= back || out > SavannaMudBlock.WATER_REACH - 0.5) {
                    continue;
                }
                // blocks in from the area's edge: sticky round it, soupy and darker toward the middle
                double in = Math.min(back - out, along - r);
                int depth = Math.max(1, Math.min(4, 1 + (int) Math.floor(in)));
                Wetness wetness = depth == 1 ? Wetness.STICKY : Wetness.SOUPY;
                BlockPos at = new BlockPos(x, top, z);
                clearPlants(level, at.above());
                // (laid as the block's own rule keeps it: the rim farther from the water is drying; review R8-14)
                level.setBlock(at, SavannaMudBlock.settled(level, at, SavannaBlocks.SAVANNA_MUD.get().with(wetness, depth, Tone.OLIVE)), Block.UPDATE_CLIENTS);
                laid++;
            }
        }
        return laid;
    }

    /**
     * Whether a column is the low bank beside the water: its top at the water's level ({@code top}) is grass, soil, silt
     * or river mud (never sand, never rock) with open air (or a plant) over it.
     */
    static boolean bank(WorldGenLevel level, int x, int z, int top) {
        BlockState ground = level.getBlockState(new BlockPos(x, top, z));
        boolean soil = ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT)
                || ground.is(SavannaBlocks.SILT.get()) || ground.is(SavannaBlocks.WET_SILT.get()) || ground.is(SavannaBlocks.RIVER_MUD.get());
        BlockState over = level.getBlockState(new BlockPos(x, top + 1, z));
        return soil && (over.isAir() || over.canBeReplaced() && over.getFluidState().isEmpty());
    }

    /** Takes a plant (grass, a flower) off the top of mud being laid. */
    static void clearPlants(WorldGenLevel level, BlockPos over) {
        BlockState state = level.getBlockState(over);
        if (!state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()) {
            level.setBlock(over, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockPos higher = over.above();
            if (level.getBlockState(higher).canBeReplaced() && !level.getBlockState(higher).isAir() && level.getBlockState(higher).getFluidState().isEmpty()) {
                level.setBlock(higher, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);      // a tall plant's top half
            }
        }
    }
}
