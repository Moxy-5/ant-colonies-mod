package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.Sex;
import com.antcolonies.savanna.animal.HippoEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * New land's hippo pods (the user, 2026-11-21: "place with land", groups of 3 at most): on a pod site's chunk
 * ({@link PodSites}) a pod rests in the chunk's deepest pool, kept for good like Minecraft's villagers (never despawned,
 * so the pod a player left is there when they come back). A pod is a lone bull 1 time in 4 ({@link #LONE}), else a bull
 * with one or two cows (my picks; real pods are bigger, fun first: the user's 3 at most). The bull takes the deepest
 * spot, the cows spots {@link #NEAREST} to {@link #FARTHEST} blocks from him, and the whole pod shares his pool. Every
 * spot is checked for the hippo's whole body ({@link #standSpot}): its 3 by 3 columns open for two blocks over its feet,
 * the water open to the sky. Hippos are never spawned by the world's spawner (BUGS 271). All numbers my picks.
 */
public class HippoPodFeature extends Feature<NoneFeatureConfiguration> {
    /** A lone bull, not a pod: 1 site in 4. */
    static final float LONE = 0.25F;
    /** Pod mates rest this near the bull and no nearer, and no nearer than {@link #NEAREST} to each other (blocks). */
    static final int NEAREST = 3, FARTHEST = 7;

    public HippoPodFeature(Codec<NoneFeatureConfiguration> codec) {
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
        if (!PodSites.site(source.getGenerator().getBiomeSource(), source.randomState().sampler(), level.getSeed(), x0 >> 4, z0 >> 4)) {
            return false;
        }
        int laid = lay(level, context.random(), x0, z0, level.getSeaLevel() - 1);
        // one line a site (one per about 250 blocks of the hippos' rivers): where, and how many hippos its pool held
        com.antcolonies.savanna.SavannaMod.LOGGER.info("[world pods] pod site at {} {}: {} hippos", x0 + 8, z0 + 8, laid);
        return laid > 0;
    }

    /**
     * Lays a pod in the deepest pool of the 16 by 16 columns from {@code x0, z0}, in water whose top is {@code top}; how
     * many hippos it laid (0 when no spot there fits a hippo). Also the game tests' way in, on a built pool.
     */
    public static int lay(WorldGenLevel level, RandomSource random, int x0, int z0, int top) {
        List<BlockPos> spots = new ArrayList<>();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                BlockPos at = standSpot(level, x, z, top);
                if (at != null) {
                    spots.add(at);
                }
            }
        }
        if (spots.isEmpty()) {
            return 0;
        }
        // shuffled, then the deepest first (ties at random): the bull's spot
        for (int i = spots.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            spots.set(i, spots.set(j, spots.get(i)));
        }
        spots.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        BlockPos bull = spots.get(0);
        int want = random.nextFloat() < LONE ? 1 : 2 + random.nextInt(2);
        List<BlockPos> pod = new ArrayList<>(List.of(bull));
        for (int i = spots.size() - 1; i > 0; i--) {         // the cows: anywhere in reach of him, so shuffle again
            int j = 1 + random.nextInt(i);
            spots.set(i, spots.set(j, spots.get(i)));
        }
        for (BlockPos p : spots.subList(1, spots.size())) {
            if (pod.size() >= want) {
                break;
            }
            double d = Math.sqrt(horizontal(p, bull));
            if (d >= NEAREST && d <= FARTHEST && pod.stream().allMatch(q -> horizontal(p, q) >= NEAREST * NEAREST)) {
                pod.add(p);
            }
        }
        int laid = 0;
        for (int i = 0; i < pod.size(); i++) {
            HippoEntity h = SavannaEntities.HIPPO.get().create(level.getLevel());
            if (h == null) {
                continue;
            }
            BlockPos p = pod.get(i);
            h.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            h.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.CHUNK_GENERATION, null);
            h.life().setUp(false, i == 0 ? Sex.MALE : Sex.FEMALE, random);
            h.life().apply();
            h.joinPod(bull);
            level.addFreshEntity(h);
            laid++;
        }
        return laid;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Where a hippo may rest in the column {@code x, z}: its feet on the bed under water at least
     * {@link HippoEntity#POOL_DEPTH} deep whose top is {@code top}, open to the sky, and room for its whole body: the 3 by
     * 3 columns round it open (water or air) at its feet and a block over them, so no part of it is in the ground. Null
     * where it does not fit.
     */
    @Nullable
    public static BlockPos standSpot(LevelReader level, int x, int z, int top) {
        BlockPos surface = new BlockPos(x, top, z);
        if (!level.getFluidState(surface).is(FluidTags.WATER) || level.getFluidState(surface.above()).is(FluidTags.WATER)
                || !RiverZones.openToSky(level, surface)) {
            return null;
        }
        BlockPos.MutableBlockPos p = surface.mutable();
        while (level.getFluidState(p.below()).is(FluidTags.WATER) && p.getY() > level.getMinBuildHeight() + 1) {
            p.move(0, -1, 0);
        }
        BlockPos feet = p.immutable();
        if (top - feet.getY() + 1 < HippoEntity.POOL_DEPTH || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
            return null;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos q = feet.offset(dx, dy, dz);
                    if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
                        return null;
                    }
                }
            }
        }
        return feet;
    }
}
