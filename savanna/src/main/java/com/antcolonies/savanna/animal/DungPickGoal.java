package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Small fish crowd round a hippo's dung in the water (the river food web's step 6, 2026-11-23; my picks, fun first;
 * real: hippo dung feeds African rivers, and fish gather where hippos rest). A barb or tilapia within
 * {@link HippoEntity#DUNG_FISH_REACH} blocks of a fresh dung cloud ({@link HippoEntity#dungNear}) swims to it and picks
 * at it from a few spots round it, a couple of seconds each, while the cloud lasts. Looks only, like the barbs' sips: no
 * hunger is fed and nothing is eaten, so it changes nothing else in the river. One goal for both fish: how a fish shows
 * its picking (the barb's nibble clip, the tilapia's graze) and when it is free to go are handed in.
 */
public class DungPickGoal extends Goal {
    /** How often it looks for a dung cloud (one check in this many; Minecraft checks a goal every other tick: about every second). */
    static final int LOOK_EVERY = 10;
    /** Picks at a cloud: at most this many spots, each picked this long (ticks, the shortest and the most added). */
    static final int BOUTS = 3, PICK_MIN = 30, PICK_MORE = 30;
    /** Its spots lie this far round the cloud's middle (blocks across, and up or down). */
    static final double ROUND = 1.1, ROUND_UP = 0.35;
    /** Nearer its spot than this (blocks) it swims straight at it; it gives up a spot it cannot reach in this long (ticks). */
    static final double STRAIGHT = 3.0;
    static final int GIVE_UP = 100;
    /** After a cloud it does not look for another for this many checks (every other tick: about 20 s). */
    static final int REST = 200;

    private final PathfinderMob fish;
    private final Consumer<Boolean> picking;
    private final BooleanSupplier free;
    @Nullable
    private Vec3 cloud, spot;
    private int bouts, ticks, pickFor, picked, look, rest;

    public DungPickGoal(PathfinderMob fish, Consumer<Boolean> picking, BooleanSupplier free) {
        this.fish = fish;
        this.picking = picking;
        this.free = free;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (rest > 0) {
            rest--;
            return false;
        }
        if (++look % LOOK_EVERY != 0 || !fish.isInWater() || fish.getVehicle() != null || !free.getAsBoolean()) {
            return false;
        }
        cloud = HippoEntity.dungNear(fish, HippoEntity.DUNG_FISH_REACH);
        return cloud != null;
    }

    @Override
    public boolean canContinueToUse() {
        return cloud != null && fish.isInWater() && free.getAsBoolean();
    }

    @Override
    public void start() {
        bouts = 0;
        nextSpot();
    }

    @Override
    public void stop() {
        picking.accept(false);
        fish.getNavigation().stop();
        cloud = null;
        spot = null;
        rest = REST;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** The next spot round the cloud (in water), or the end of its meal after its last bout or once the cloud is gone. */
    private void nextSpot() {
        if (bouts++ >= BOUTS || HippoEntity.dungNear(fish, HippoEntity.DUNG_FISH_REACH + ROUND) == null) {
            cloud = null;
            return;
        }
        Vec3 at = cloud;
        for (int tries = 0; tries < 4; tries++) {
            Vec3 p = cloud.add((fish.getRandom().nextDouble() * 2 - 1) * ROUND, (fish.getRandom().nextDouble() * 2 - 1) * ROUND_UP,
                    (fish.getRandom().nextDouble() * 2 - 1) * ROUND);
            if (fish.level().getFluidState(BlockPos.containing(p)).is(FluidTags.WATER)) {
                at = p;
                break;
            }
        }
        spot = at;
        ticks = 0;
        picked = 0;
        pickFor = PICK_MIN + fish.getRandom().nextInt(PICK_MORE);
        picking.accept(false);
        fish.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
    }

    @Override
    public void tick() {
        if (cloud == null || spot == null) {
            return;
        }
        ticks++;
        fish.getLookControl().setLookAt(cloud.x, cloud.y, cloud.z);
        double dx = spot.x - fish.getX(), dy = spot.y - fish.getY(), dz = spot.z - fish.getZ();
        double far = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (picked > 0 || far < 0.4) {
            // picking: held near the spot, nose to the cloud
            fish.getNavigation().stop();
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.6));
            picking.accept(true);
            if (++picked > pickFor) {
                nextSpot();
            }
            return;
        }
        if (ticks > GIVE_UP) {
            nextSpot();
            return;
        }
        if (far < STRAIGHT) {
            // the last stretch, nudged straight there (a fish's path stops short of its end, as in BarbFeedGoal)
            fish.getNavigation().stop();
            double push = 0.02 / Math.max(far, 0.05);
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.8).add(dx * push, dy * push, dz * push));
            if (dx * dx + dz * dz > 0.01) {
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
                fish.setYRot(Mth.approachDegrees(fish.getYRot(), yaw, 20.0F));
                fish.yBodyRot = fish.getYRot();
            }
        } else if (ticks % 20 == 0 || fish.getNavigation().isDone()) {
            fish.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }
    }

    /** Whether it is picking at a dung cloud now (for the tests). */
    public boolean picking() {
        return spot != null && picked > 0;
    }
}
