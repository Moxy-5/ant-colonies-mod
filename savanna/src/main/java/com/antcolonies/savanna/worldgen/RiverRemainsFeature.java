package com.antcolonies.savanna.worldgen;

import java.util.function.Predicate;

import com.antcolonies.savanna.animal.RemainsEntity;
import com.antcolonies.savanna.animal.TilapiaEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A few old fish remains already lying on the beds of the savanna's waters in new land (the user, 2026-11-15: "not a
 * crazy amount though, just to where it looks good and cool"): about 1 chunk of river in 5 has some, now and then two
 * (my picks). Any of the four kinds (a dead catfish or tilapia, a crocodile's torn catfish, a catfish's bitten-off
 * tilapia), mostly well picked over: 1 in 10 fresh, 2 fed on, 3 picked over, 4 bare bones, so the bed looks lived on
 * rather than littered. Once a player is near they rot and crumble like any remains ({@link RemainsEntity}), each at its
 * own pace. Runs after the algae, in the same step, only on its own chunk's columns.
 */
public class RiverRemainsFeature extends Feature<NoneFeatureConfiguration> {
    private static final RemainsEntity.Kind[] FISH = {RemainsEntity.Kind.CATFISH, RemainsEntity.Kind.CATFISH_TORN,
            RemainsEntity.Kind.TILAPIA, RemainsEntity.Kind.TILAPIA_HALF};

    /** The share of river chunks with remains, and of those with a second (my picks). */
    static final float CHANCE = 0.2F, SECOND = 0.25F;
    /** Remains lie only in water this deep or less (blocks; deeper beds are out of sight anyway). */
    static final int DEEPEST = 8;

    public RiverRemainsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        if (random.nextFloat() >= CHANCE) {
            return false;
        }
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        if (!BigSavanna.lives(level.getLevel(), level.getBiome(new net.minecraft.core.BlockPos(x0 + 8, level.getSeaLevel(), z0 + 8)), x0 + 8, z0 + 8)) {
            return false; // the gate: no river life in a savanna too small to live in
        }
        int count = random.nextFloat() < SECOND ? 2 : 1;
        return scatter(level, random, x0, z0, 16, 16, level.getSeaLevel() - 1, count,
                at -> level.getBiome(at).is(RiverBankFeature.WATERS)) > 0;
    }

    /**
     * Lays up to {@code count} remains on the bed somewhere in the columns {@code x0..x0+w-1, z0..z0+d-1}, under water
     * whose surface is near {@code sea} (as the algae finds it, {@link RiverAlgaeFeature#ABOVE_SEA}), only in columns
     * {@code ours} says yes to. How many it laid.
     */
    public static int scatter(WorldGenLevel level, RandomSource random, int x0, int z0, int w, int d, int sea, int count, Predicate<BlockPos> ours) {
        int laid = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int tries = 0; tries < 12 * count && laid < count; tries++) {
            int x = x0 + random.nextInt(w), z = z0 + random.nextInt(d);
            if (!ours.test(at.set(x, sea, z))) {
                continue;
            }
            int y = sea + RiverAlgaeFeature.ABOVE_SEA;
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
            at.set(x, y - depth, z);
            if (!level.getBlockState(at).isFaceSturdy(level, at, Direction.UP)) {
                continue;
            }
            RemainsEntity remains = SavannaEntities.REMAINS.get().create(level.getLevel());
            if (remains == null) {
                continue;
            }
            RemainsEntity.Kind kind = FISH[random.nextInt(FISH.length)];     // fish only (a buffalo's carcass is the lions')
            boolean catfish = kind == RemainsEntity.Kind.CATFISH || kind == RemainsEntity.Kind.CATFISH_TORN;
            float size = catfish ? 0.85F + 0.3F * random.nextFloat() : 0.7F + 0.3F * random.nextFloat();
            // a tilapia's colours as the living ones come: about 1 in 4 young, 1 in 4 of the grown a breeding male (my picks)
            int look = catfish ? TilapiaEntity.EVERYDAY
                    : random.nextInt(4) == 0 ? TilapiaEntity.YOUNG : random.nextInt(4) == 0 ? TilapiaEntity.BREEDING : TilapiaEntity.EVERYDAY;
            if (look == TilapiaEntity.YOUNG) {
                size *= 0.6F;
            }
            remains.setUp(kind, size, look, stageFor(random), random.nextBoolean(), random);
            remains.moveTo(x + 0.2 + 0.6 * random.nextDouble(), y - depth + 1, z + 0.2 + 0.6 * random.nextDouble(), random.nextFloat() * 360.0F, 0.0F);
            remains.setYHeadRot(remains.getYRot());
            level.addFreshEntity(remains);
            laid++;
        }
        return laid;
    }

    /** The look's stage, mostly old: fresh 1 in 10, fed on 2, picked over 3, bones 4 (my picks). */
    static int stageFor(RandomSource random) {
        int roll = random.nextInt(10);
        return roll < 1 ? 1 : roll < 3 ? 2 : roll < 6 ? 3 : 4;
    }

    private static boolean isWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource();
    }
}
