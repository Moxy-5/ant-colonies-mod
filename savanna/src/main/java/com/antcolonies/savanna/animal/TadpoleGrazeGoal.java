package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.AlgaeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A tadpole grazes the algae (the river tie-up, 2026-11-23; the plan's link algae to tadpole to frog; my picks): now
 * and then it noses down onto the nearest mat with strands and rasps at it a few seconds; one graze in
 * {@link #BITE_ONE_IN} takes a bite off the mat ({@link AlgaeBlock#graze}), so a school of ten never strips it (the
 * tilapia are the mats' big eaters). No hunger: a tadpole with no algae near swims on and still grows.
 */
public class TadpoleGrazeGoal extends Goal {
    /** One check in this many starts a graze (checked every other tick: about every 6 s). */
    static final int CHANCE = 60;
    /** How far it looks for a mat (blocks), how long it grazes (ticks), how long it may swim there first. */
    static final int LOOK_ACROSS = 4, LOOK_DOWN = 2, GRAZE_MIN = 40, GRAZE_MORE = 40, GIVE_UP = 80;
    /** One graze in this many takes a bite off the mat. */
    static final int BITE_ONE_IN = 6;

    private final TadpoleEntity tadpole;
    @Nullable
    private BlockPos mat;
    @Nullable
    private Vec3 spot;
    private int ticks, grazing, grazeFor;

    public TadpoleGrazeGoal(TadpoleEntity tadpole) {
        this.tadpole = tadpole;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!tadpole.isInWater() || tadpole.getVehicle() != null || tadpole.getRandom().nextInt(CHANCE) != 0) {
            return false;
        }
        mat = matNear();
        if (mat == null) {
            return false;
        }
        double r = 0.3;
        spot = new Vec3(mat.getX() + 0.5 + (tadpole.getRandom().nextDouble() - 0.5) * r * 2, mat.getY() + 0.25,
                mat.getZ() + 0.5 + (tadpole.getRandom().nextDouble() - 0.5) * r * 2);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && tadpole.isInWater() && ticks < GIVE_UP + grazeFor;
    }

    @Override
    public void start() {
        ticks = 0;
        grazing = 0;
        grazeFor = GRAZE_MIN + tadpole.getRandom().nextInt(GRAZE_MORE);
        tadpole.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
    }

    @Override
    public void stop() {
        tadpole.setAction(TadpoleEntity.NONE);
        tadpole.getNavigation().stop();
        spot = null;
        mat = null;
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
        if (grazing > 0) {
            tadpole.setDeltaMovement(tadpole.getDeltaMovement().scale(0.5));
            if (++grazing > grazeFor) {
                if (mat != null && tadpole.getRandom().nextInt(BITE_ONE_IN) == 0 && AlgaeBlock.bitesLeft(tadpole.level().getBlockState(mat)) > 0) {
                    AlgaeBlock.graze(tadpole.level(), mat);
                }
                spot = null;
            }
            return;
        }
        double dx = spot.x - tadpole.getX(), dy = spot.y - tadpole.getY(), dz = spot.z - tadpole.getZ();
        double far = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (far < 0.25) {
            tadpole.getNavigation().stop();
            tadpole.setAction(TadpoleEntity.GRAZE);
            grazing = 1;
            return;
        }
        if (ticks > GIVE_UP) {
            spot = null;
            return;
        }
        if (far < 1.5) {
            // the last stretch nudged straight there (Minecraft's fish swim only along a path, which stops short)
            tadpole.getNavigation().stop();
            double push = 0.015 / Math.max(far, 0.05);
            tadpole.setDeltaMovement(tadpole.getDeltaMovement().scale(0.8).add(dx * push, dy * push, dz * push));
            if (dx * dx + dz * dz > 0.01) {
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
                tadpole.setYRot(Mth.approachDegrees(tadpole.getYRot(), yaw, 20.0F));
                tadpole.yBodyRot = tadpole.getYRot();
            }
        } else if (ticks % 20 == 0 || tadpole.getNavigation().isDone()) {
            tadpole.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }
    }

    /** The nearest algae mat with strands on it, or none. */
    @Nullable
    private BlockPos matNear() {
        Level level = tadpole.level();
        BlockPos home = tadpole.blockPosition();
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
        return best;
    }
}
