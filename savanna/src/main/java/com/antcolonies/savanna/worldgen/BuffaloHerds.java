package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.animal.Sex;
import com.antcolonies.savanna.animal.BuffaloEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * New land's Cape buffalo herds (2026-11-25, {@code docs/plans/buffalo.md}): every pan laid ({@link PanFeature}) gets a herd
 * grazing past its trampled ring, laid with the land and kept for good, as the hippo pods are (BUGS 271; the world's
 * spawner never makes buffalo). Pans are about one to every 192 by 192 blocks and keep clear of rivers, so a herd's water
 * always exists. A herd is {@link #FEWEST} to {@link #MOST}: one bull, the rest cows, every member's home the pan's middle
 * and how far across it is.
 *
 * <p>Each member's spot: dry grass past the pan's ring (no pan water within the ring's width and {@link #PAST_RING} more,
 * and not farther than the grazed land round it and {@link #BEYOND_GRAZED} more), its whole body clear (3 by 3 columns, 3
 * high, no water: BUGS 271), off the termite mounds and tree feet the pan keeps off, inside the chunks the pan's feature
 * may write at least {@link #EDGE} from their edge. The bull on one of the spots nearest the water, the cows
 * {@link #NEAREST} to {@link #FARTHEST} from him and {@link #NEAREST} from each other, all facing about one way. Too few
 * spots for {@link #FEWEST}: no herd. All numbers my picks.
 */
public final class BuffaloHerds {
    /** How many in a herd, at random between these. */
    static final int FEWEST = 3, MOST = 5;
    /** Spots keep this far inside the chunks the feature may write (blocks): a body's 3 by 3 columns and some to spare. */
    static final int EDGE = 3;
    /** Cows stand this near the bull and no nearer, and no nearer than {@link #NEAREST} to each other (blocks; a body is 3.8 long). */
    static final int NEAREST = 4, FARTHEST = 9;
    /** Past the ring by this much at least, and past the grazed land round it by this much at most (blocks). */
    static final double PAST_RING = 2.5, BEYOND_GRAZED = 4.0;
    /** The bull's spot is one of this many nearest the water. */
    static final int BULL_FROM = 8;
    /** Each member faces the herd's way give or take this much (degrees). */
    static final float FACING_SPREAD = 40.0F;

    private BuffaloHerds() {
    }

    /** What was laid: how many buffalo, and how many spots there were. */
    public record Herd(int buffalo, int spots) {
    }

    /**
     * Lays a herd beside the pan just laid ({@code pan}), everything read and written inside {@code bounds} ({minX, minZ,
     * maxX, maxZ}, the chunks the pan's feature may touch), off what {@code keepout} keeps off. Also the game tests' way in,
     * on a built pan.
     */
    public static Herd lay(WorldGenLevel level, RandomSource random, PanFeature.Laid pan, int[] bounds, OxbowFeature.Keepout keepout) {
        int low = pan.middle().getY();
        // the pan's water: its top lies at the pan's level
        List<long[]> water = new ArrayList<>();
        for (int x = bounds[0]; x <= bounds[2]; x++) {
            for (int z = bounds[1]; z <= bounds[3]; z++) {
                if (level.getFluidState(new BlockPos(x, low, z)).is(FluidTags.WATER)) {
                    water.add(new long[] {x, z});
                }
            }
        }
        if (water.isEmpty()) {
            return new Herd(0, 0);
        }
        double nearest = pan.ring() + 1.5 + PAST_RING, farthest = pan.ring() + 1.5 + PanFeature.GRAZED + BEYOND_GRAZED;
        List<Spot> spots = new ArrayList<>();
        for (int x = bounds[0] + EDGE; x <= bounds[2] - EDGE; x++) {
            for (int z = bounds[1] + EDGE; z <= bounds[3] - EDGE; z++) {
                double d = Math.sqrt(nearestWater(water, x, z));
                if (d < nearest || d > farthest) {
                    continue;
                }
                BlockPos stand = standSpot(level, x, z, low, keepout);
                if (stand != null) {
                    spots.add(new Spot(stand, d));
                }
            }
        }
        if (spots.size() < FEWEST) {
            return new Herd(0, spots.size());
        }
        // shuffled, then the nearest the water first (ties at random): the bull on one of the nearest
        for (int i = spots.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            spots.set(i, spots.set(j, spots.get(i)));
        }
        spots.sort((a, b) -> Double.compare(a.water(), b.water()));
        Spot bull = spots.remove(random.nextInt(Math.min(BULL_FROM, spots.size())));
        int want = FEWEST + random.nextInt(MOST - FEWEST + 1);
        List<BlockPos> herd = new ArrayList<>(List.of(bull.at()));
        for (int i = spots.size() - 1; i > 0; i--) {          // the cows: anywhere in reach of him, so shuffle again
            int j = random.nextInt(i + 1);
            spots.set(i, spots.set(j, spots.get(i)));
        }
        for (Spot s : spots) {
            if (herd.size() >= want) {
                break;
            }
            double d = Math.sqrt(horizontal(s.at(), bull.at()));
            if (d >= NEAREST && d <= FARTHEST && herd.stream().allMatch(q -> horizontal(s.at(), q) >= NEAREST * NEAREST)) {
                herd.add(s.at());
            }
        }
        if (herd.size() < FEWEST) {
            return new Herd(0, spots.size() + 1);
        }
        float heading = random.nextFloat() * 360.0F;
        int laid = 0;
        for (int i = 0; i < herd.size(); i++) {
            BuffaloEntity b = SavannaEntities.BUFFALO.get().create(level.getLevel());
            if (b == null) {
                continue;
            }
            BlockPos p = herd.get(i);
            float yaw = heading + (random.nextFloat() * 2.0F - 1.0F) * FACING_SPREAD;
            b.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0.0F);
            b.setYBodyRot(yaw);
            b.setYHeadRot(yaw);
            b.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.CHUNK_GENERATION, null);
            b.life().setUp(false, i == 0 ? Sex.MALE : Sex.FEMALE, random);
            b.life().apply();
            b.setHome(pan.middle(), pan.across());
            level.addFreshEntity(b);
            laid++;
        }
        return new Herd(laid, spots.size() + 1);
    }

    /** A spot a buffalo may be laid on, and how far it is from the pan's water (blocks). */
    private record Spot(BlockPos at, double water) {
    }

    /** The squared distance from x, z to the nearest of the pan's water columns. */
    private static double nearestWater(List<long[]> water, int x, int z) {
        double best = Double.MAX_VALUE;
        for (long[] w : water) {
            double dx = w[0] - x, dz = w[1] - z, d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Where a buffalo may stand in the column x, z beside a pan whose water's top is {@code low}: on a grass block (dry
     * grass, past the ring's dirt and mud), its 3 by 3 columns open and dry for 3 blocks over its feet (plants are no
     * matter), none of them kept off. Its feet cell, or null where it does not fit.
     */
    static BlockPos standSpot(WorldGenLevel level, int x, int z, int low, OxbowFeature.Keepout keepout) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (keepout.at(x + dx, z + dz)) {
                    return null;
                }
            }
        }
        int g = RiverBankFeature.ground(level, x, z, low + PanFeature.UNEVEN + 6);
        if (g < low || g > low + PanFeature.UNEVEN + 4) {
            return null;
        }
        BlockPos ground = new BlockPos(x, g, z);
        if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK) || !level.getFluidState(ground.above()).isEmpty()) {
            return null;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos q = new BlockPos(x + dx, g + dy, z + dz);
                    BlockState s = level.getBlockState(q);
                    if (!s.getCollisionShape(level, q).isEmpty() || !s.getFluidState().isEmpty()) {
                        return null;
                    }
                }
            }
        }
        return new BlockPos(x, g + 1, z);
    }

    /** A herd's size as words for the log. */
    static String words(Herd herd) {
        return herd.buffalo() <= 0 ? "no room for a herd (" + herd.spots() + " spots)"
                : "a herd of " + herd.buffalo() + " buffalo (a bull and " + Mth.clamp(herd.buffalo() - 1, 0, MOST) + " cows)";
    }
}
