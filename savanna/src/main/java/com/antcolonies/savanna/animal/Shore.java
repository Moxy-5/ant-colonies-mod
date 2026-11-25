package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A drinker's own stand on its pan's shore (lifted out of the Cape buffalo's day when the lions came, 2026-11-25,
 * {@code docs/plans/lion.md}): the dry bank stand ({@link Grazing#bankStand}) nearest its own spot round the pan's edge
 * (its order turned by the golden angle), with water one or two blocks straight ahead along a block axis (facing the
 * pan's middle the most of those), clear of where its mates mean to stand.
 *
 * @param stand  where it stands
 * @param facing which way it faces to drink
 * @param spot   its own spot round the pan's edge
 */
public record Shore(BlockPos stand, Direction facing, Vec3 spot) {
    /** The shore stand is looked for this far round its spot on the pan's edge (blocks). */
    static final int SEARCH = 6;

    /** Whether a mate means to stand within its spacing of x, z. */
    public interface Crowded {
        boolean at(double x, double z);
    }

    /** Its shore stand round the pan at {@code home} ({@code across} wide, its water's top at the home's height); null if none (no pan, a dry one). */
    @Nullable
    public static Shore find(Level level, BlockPos home, int across, int rank, Crowded crowded) {
        if (across <= 0) {
            return null;
        }
        int w = home.getY();                            // its pan's water's top
        Vec3 dir = HomeGroup.offset(rank, 1.0);
        double out = across / 2.0 + 1.0;
        Vec3 spot = new Vec3(home.getX() + 0.5 + dir.x * out, w, home.getZ() + 0.5 + dir.z * out);
        BlockPos best = null;
        Direction bestFacing = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                int x = Mth.floor(spot.x) + dx, z = Mth.floor(spot.z) + dz;
                double ex = x + 0.5 - spot.x, ez = z + 0.5 - spot.z, d = ex * ex + ez * ez;
                if (d >= bestD) {
                    continue;
                }
                BlockPos s = Grazing.bankStand(level, x, z, w);
                if (s == null || crowded.at(x + 0.5, z + 0.5)) {
                    continue;
                }
                Direction facing = null;
                double most = -Double.MAX_VALUE;
                for (Direction f : Direction.Plane.HORIZONTAL) {
                    if (water(level, x + f.getStepX(), z + f.getStepZ(), w) || water(level, x + 2 * f.getStepX(), z + 2 * f.getStepZ(), w)) {
                        double toMiddle = f.getStepX() * (home.getX() - x) + f.getStepZ() * (home.getZ() - z);
                        if (toMiddle > most) {
                            most = toMiddle;
                            facing = f;
                        }
                    }
                }
                if (facing != null) {
                    best = s;
                    bestD = d;
                    bestFacing = facing;
                }
            }
        }
        return best == null ? null : new Shore(best, bestFacing, spot);
    }

    private static boolean water(Level level, int x, int z, int w) {
        BlockPos p = new BlockPos(x, w, z);
        return level.isLoaded(p) && level.getFluidState(p).is(FluidTags.WATER);
    }
}
