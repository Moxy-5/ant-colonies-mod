package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLives;
import com.antcolonies.animal.EggClutchBlockEntity;
import com.antcolonies.animal.LifeNumbers;
import com.antcolonies.savanna.animal.CrocodileEntity;
import com.antcolonies.savanna.block.CrocodileNestBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * New land's crocodile nests (world eggs step 1, 2026-11-20, {@code docs/plans/world-eggs.md}; the user: a site every 250
 * to 350 blocks of river): on a nest site's chunk ({@link NestSites}) the covered nests on its dry river sand, as real
 * Nile crocodiles bunch their nests on a good sandbank. Most sites are a nesting beach of 2 to 4 nests at least
 * {@link #APART} blocks apart (as many as the chunk's sand holds), 1 site in 4 a lone nest; each a clutch of the
 * crocodile's own number of eggs, laid up to {@link #OLDEST_DAYS} days ago, so a site's nests hatch on different days
 * after a player finds them. At about half the nests ({@link #MOTHER}) the mother lies in the water close by, guarding
 * it (she stays until her care ends, as any nesting mother); the others are unguarded, their mothers off in the river.
 * Runs in the decoration step after the banks are shaped, only on its own chunk's columns. All numbers my picks.
 */
public class CrocodileNestFeature extends Feature<NoneFeatureConfiguration> {
    /** Nests at least this far apart (blocks: a nesting female keeps this far from another nest, {@code CrocodileBreeding}). */
    static final int APART = 6;
    /** A nest stands this near the water and no nearer (blocks; the plan's 2 to 6). */
    static final int WATER_NEAREST = 2, WATER_FARTHEST = 6;
    /** A lone nest, not a beach: 1 site in 4. */
    static final float LONE = 0.25F;
    /** The share of nests with their mother by them. */
    static final float MOTHER = 0.5F;
    /** How long ago a nest may have been laid (game days; under the crocodile's 4 days to hatch, so none hatches at once). */
    static final double OLDEST_DAYS = 3.0;

    public CrocodileNestFeature(Codec<NoneFeatureConfiguration> codec) {
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
        if (!NestSites.site(source.getGenerator().getBiomeSource(), source.randomState().sampler(), level.getSeed(), x0 >> 4, z0 >> 4)) {
            return false;
        }
        int laid = lay(level, context.random(), x0, z0, level.getSeaLevel() - 1, level.getLevel().getGameTime());
        // one line a site (one per 250 to 350 blocks of river): where, and how many nests its sand held (0: none by the water)
        com.antcolonies.savanna.SavannaMod.LOGGER.info("[world nests] nest site at {} {}: {} nests", x0 + 8, z0 + 8, laid);
        return laid > 0;
    }

    /**
     * Lays a nest site's nests on the dry river sand of the 16 by 16 columns from {@code x0, z0}, by water whose top is
     * {@code sea}, as if laid before {@code now}; how many it laid. (Also the game test's way in, on a built bank.)
     */
    public static int lay(WorldGenLevel level, RandomSource random, int x0, int z0, int sea, long now) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int y = sea + 4; y >= sea + 1; y--) {      // a bank a little over the water, up to 3 blocks
                    at.set(x, y, z);
                    if (fits(level, at)) {
                        spots.add(at.immutable());
                        break;
                    }
                }
            }
        }
        if (spots.isEmpty()) {
            return 0;
        }
        // shuffled, then taken while they keep apart: a beach of 2 to 4, or one alone
        for (int i = spots.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            spots.set(i, spots.set(j, spots.get(i)));
        }
        int want = random.nextFloat() < LONE ? 1 : 2 + random.nextInt(3);
        List<BlockPos> nests = new ArrayList<>();
        for (BlockPos p : spots) {
            if (nests.size() >= want) {
                break;
            }
            if (nests.stream().allMatch(n -> n.distSqr(p) >= APART * APART)) {
                nests.add(p);
            }
        }
        var kind = SavannaEntities.CROCODILE.get();
        LifeNumbers life = AnimalLives.get(kind);
        int laid = 0;
        for (BlockPos p : nests) {
            BlockState nest = SavannaBlocks.CROCODILE_NEST.get().defaultBlockState().setValue(CrocodileNestBlock.OPEN, false)
                    .setValue(CrocodileNestBlock.TONE, CrocodileNestBlock.toneUnder(level, p));
            level.setBlock(p, nest, Block.UPDATE_CLIENTS);
            if (!(level.getBlockEntity(p) instanceof EggClutchBlockEntity clutch)) {
                continue;
            }
            int eggs = life.clutchMin() + random.nextInt(Math.max(1, life.clutchMax() - life.clutchMin() + 1));
            clutch.lay(kind, eggs, now - (long) (random.nextDouble() * OLDEST_DAYS * LifeNumbers.DAY));
            laid++;
            if (random.nextFloat() < MOTHER) {
                mother(level, random, p);
            }
        }
        return laid;
    }

    /** Dry river sand with room over it, not beside the water, the water {@link #WATER_NEAREST} to {@link #WATER_FARTHEST} off. */
    static boolean fits(WorldGenLevel level, BlockPos p) {
        BlockPos below = p.below();
        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !CrocodileEntity.isRiverSand(level.getBlockState(below))
                || !level.getFluidState(below).isEmpty() || !level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
            return false;
        }
        return water(level, p, WATER_NEAREST - 1) == null && water(level, p, WATER_FARTHEST) != null;
    }

    /** The nearest water top within this many blocks (level with the spot's ground or up to 3 under it), or none. */
    @Nullable
    static BlockPos water(WorldGenLevel level, BlockPos p, int reach) {
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-reach, -4, -reach), p.offset(reach, -1, reach))) {
            int d = (q.getX() - p.getX()) * (q.getX() - p.getX()) + (q.getZ() - p.getZ()) * (q.getZ() - p.getZ());
            if (d <= reach * reach && d < bestD && level.getFluidState(q).is(FluidTags.WATER) && level.getBlockState(q.above()).isAir()) {
                best = q.immutable();
                bestD = d;
            }
        }
        return best;
    }

    /** The nest's mother, a grown female, in the water nearest it, guarding it. */
    private static void mother(WorldGenLevel level, RandomSource random, BlockPos nest) {
        BlockPos water = water(level, nest, WATER_FARTHEST + 2);
        CrocodileEntity croc = SavannaEntities.CROCODILE.get().create(level.getLevel());
        if (water == null || croc == null) {
            return;
        }
        float yaw = (float) (Mth.atan2(nest.getZ() - water.getZ(), nest.getX() - water.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        croc.moveTo(water.getX() + 0.5, water.getY() + 0.3, water.getZ() + 0.5, yaw, 0.0F);     // facing her nest
        croc.finalizeSpawn(level, level.getCurrentDifficultyAt(water), MobSpawnType.CHUNK_GENERATION, null);
        croc.life().setUp(false, com.antcolonies.animal.Sex.FEMALE, random);     // female first, so her size is a female's
        croc.guardNest(nest);
        level.addFreshEntity(croc);
    }
}
