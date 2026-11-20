package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.AlgaeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A barb's little meals, for looks only (M9 river life, 2026-11-20; my picks; no hunger, nothing eaten, so barbs can
 * never strip a river): at dawn and dusk it now and then rises to sip at the surface (a gulp, a tiny ring of bubbles
 * on the water), and at other times it now and then noses down to pick at an algae mat a few seconds.
 */
public class BarbFeedGoal extends Goal {
    /** One check in this many starts a meal (checked every other tick: about every 10 s at dawn and dusk, every 20 s else). */
    static final int SIP_CHANCE = 100, NIBBLE_CHANCE = 200;
    /** How far it looks for the surface above it and for an algae mat (blocks). */
    static final int SURFACE_ABOVE = 3, LOOK_ACROSS = 4, LOOK_DOWN = 3;
    /** How long a sip and a nibble last (ticks: the sip clip once, the nibble clip two or three times). */
    static final int SIP_TICKS = 28, NIBBLE_MIN = 36, NIBBLE_MORE = 36;
    /** How long it may swim to its spot before it gives up (ticks). */
    static final int GIVE_UP = 100;

    private final BarbEntity fish;
    @Nullable
    private Vec3 spot;
    private byte kind;
    private int ticks, eating, eatFor;

    public BarbFeedGoal(BarbEntity fish) {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Dawn (5:00 to 7:30) or dusk (17:30 to 20:00): the hours small fish rise to the surface. */
    static boolean dawnOrDusk(Level level) {
        long time = level.getDayTime() % 24000L;
        return time >= 23000L || time < 1500L || time >= 11500L && time < 14000L;
    }

    @Override
    public boolean canUse() {
        if (!fish.isInWater() || fish.getVehicle() != null) {
            return false;
        }
        if (dawnOrDusk(fish.level())) {
            if (fish.getRandom().nextInt(SIP_CHANCE) != 0) {
                return false;
            }
            spot = surfaceAbove();
            kind = BarbEntity.SIP;
        } else {
            if (fish.getRandom().nextInt(NIBBLE_CHANCE) != 0) {
                return false;
            }
            spot = algaeNear();
            kind = BarbEntity.NIBBLE;
        }
        return spot != null;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && fish.isInWater() && ticks < GIVE_UP + eatFor;
    }

    @Override
    public void start() {
        ticks = 0;
        eating = 0;
        eatFor = kind == BarbEntity.SIP ? SIP_TICKS : NIBBLE_MIN + fish.getRandom().nextInt(NIBBLE_MORE);
        fish.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
    }

    @Override
    public void stop() {
        fish.setAction(BarbEntity.NONE);
        fish.getNavigation().stop();
        spot = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (spot == null) {
            return;
        }
        ticks++;
        if (eating > 0) {
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.6));
            if (kind == BarbEntity.SIP && eating == 10 && fish.level() instanceof ServerLevel server) {
                // the gulp: a tiny ring on the water
                server.sendParticles(ParticleTypes.BUBBLE_POP, spot.x, Math.floor(spot.y) + 0.95, spot.z, 4, 0.12, 0.0, 0.12, 0.0);
            }
            if (++eating > eatFor) {
                spot = null;
            }
            return;
        }
        double dx = spot.x - fish.getX(), dy = spot.y - fish.getY(), dz = spot.z - fish.getZ();
        double far = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (far < 0.35) {
            fish.getNavigation().stop();
            fish.setAction(kind);
            eating = 1;
            return;
        }
        if (ticks > GIVE_UP) {
            spot = null;
            return;
        }
        if (far < 1.5) {
            // the last stretch, nudged straight there (Minecraft's fish swim only along a path, which stops short)
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

    /** Just under the water's top above it (the top water block with air over it), or none within reach. */
    @Nullable
    private Vec3 surfaceAbove() {
        Level level = fish.level();
        BlockPos at = fish.blockPosition();
        for (int up = 0; up <= SURFACE_ABOVE; up++) {
            BlockPos p = at.above(up);
            if (!level.getFluidState(p).is(FluidTags.WATER)) {
                return null;
            }
            if (level.getBlockState(p.above()).isAir()) {
                return new Vec3(fish.getX(), p.getY() + 0.85 - fish.getBbHeight(), fish.getZ());
            }
        }
        return null;
    }

    /** Just over the nearest algae mat with strands on it, or none. */
    @Nullable
    private Vec3 algaeNear() {
        Level level = fish.level();
        BlockPos home = fish.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-LOOK_ACROSS, -LOOK_DOWN, -LOOK_ACROSS), home.offset(LOOK_ACROSS, 1, LOOK_ACROSS))) {
            if (AlgaeBlock.bitesLeft(level.getBlockState(at)) <= 0) {
                continue;
            }
            double d = at.distSqr(home);
            if (d < bestDist) {
                best = at.immutable();
                bestDist = d;
            }
        }
        return best == null ? null : new Vec3(best.getX() + 0.5, best.getY() + 0.2, best.getZ() + 0.5);
    }
}
