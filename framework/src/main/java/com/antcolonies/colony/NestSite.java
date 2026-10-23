package com.antcolonies.colony;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * How good a spot is for a founding queen to dig her nest (species-neutral; for now only how level the ground is, the
 * user's decision of 2026-10-14 for hillsides: harvester ant queens walk a little to flatter ground, termites build
 * where they land). Later (M9, flying queens) the soil, food and other colonies are scored here too.
 */
public final class NestSite {
    /** The square round the spot that should be about level: the shaft, the founding chamber and the hill round it. */
    static final int AREA = 2;
    /** Columns of that square that may be off (a burrow, a stone, a dip) without the spot counting as uneven. */
    static final int ALLOWED_OFF = 4;

    private NestSite() {
    }

    /**
     * How many columns of the square round {@code ground} (a top ground block) are more than {@code maxUnevenness}
     * blocks higher or lower than it; a column not loaded counts as off.
     */
    public static int offColumns(Level level, BlockPos ground, int maxUnevenness) {
        int off = 0;
        for (int dx = -AREA; dx <= AREA; dx++) {
            for (int dz = -AREA; dz <= AREA; dz++) {
                int x = ground.getX() + dx;
                int z = ground.getZ() + dz;
                if (!level.hasChunkAt(new BlockPos(x, ground.getY(), z))
                        || Math.abs(GroundReader.surface(level, x, z).getY() - ground.getY()) > maxUnevenness) {
                    off++;
                }
            }
        }
        return off;
    }

    /** About level round {@code ground} (see {@link #offColumns}). */
    public static boolean isLevel(Level level, BlockPos ground, int maxUnevenness) {
        return offColumns(level, ground, maxUnevenness) <= ALLOWED_OFF;
    }

    /**
     * Where a queen standing at {@code feet} on uneven ground should walk to found her nest: the nearest level spot
     * within {@code radius} blocks (a real queen settles on the first good spot she comes to, not the best one), or if
     * none is level the least uneven one, on soil she can dig and dry; null when her own spot is level already, or no spot near her is more level than hers (she founds where she
     * is).
     */
    @Nullable
    public static BlockPos findLevelSpot(Level level, BlockPos feet, int radius, int maxUnevenness) {
        BlockPos here = feet.below();
        int hereOff = offColumns(level, here, maxUnevenness);
        if (hereOff <= ALLOWED_OFF) {
            return null;
        }
        BlockPos best = null;
        int bestOff = hereOff;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distance = dx * dx + dz * dz;
                if (distance > radius * radius || !level.hasChunkAt(feet.offset(dx, 0, dz))) {
                    continue;
                }
                BlockPos ground = GroundReader.surface(level, feet.getX() + dx, feet.getZ() + dz);
                if (!level.getBlockState(ground).is(ModTags.DIGGABLE) || !level.getFluidState(ground.above()).isEmpty()) {
                    continue;
                }
                int off = Math.max(ALLOWED_OFF, offColumns(level, ground, maxUnevenness)); // every level spot is as good
                if (off < bestOff || off == bestOff && best != null && distance < bestDistance) {
                    best = ground.above();
                    bestOff = off;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
