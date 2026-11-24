package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A young crocodile snaps up a landed fly (the river food web's step 1, 2026-11-23, {@code docs/plans/river-tie-up.md};
 * animals are fun first, inspired by real life: real hatchlings live mostly on insects). A hatchling or a teen with a fly
 * landed within {@link #REACH} blocks (on the water or the bank, {@code FlyCloudEntity.landedFly}) claims it, creeps to
 * it, turns so the fly lies beside its head and snaps sideways (its snap_left or snap_right clip, the one it uses on
 * fish); one snap in {@link #MISS} misses and the fly zips up. Hungry or not (a fly is a snack: it feeds nothing, so
 * it changes no hunger), never while carried, hunting, startled or courting. Grown crocodiles ignore flies.
 */
final class CrocFlySnapGoal extends Goal {
    /** How far it sees a landed fly (blocks), how often it looks (one check in this many, every 10 ticks), the share of snaps that miss. */
    static final double REACH = 4.0;
    static final int LOOK = 5;
    static final float MISS = 0.25F;
    /** Nearer than this (blocks) it swims or walks straight at the fly, pushed this much a tick (at a grown one's pace). */
    static final double CLOSE = 3.0, CLOSE_PUSH = 0.02;
    /** How long it may take to reach and snap before it gives up (ticks). */
    static final int GIVE_UP = 120;

    private final CrocodileEntity croc;
    @Nullable
    private FlyCloudEntity cloud;
    private Vec3 at = Vec3.ZERO;
    private int t, snapT, look;
    private boolean snapping, right;

    CrocFlySnapGoal(CrocodileEntity croc) {
        this.croc = croc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (++look % LOOK != 0 || croc.life().stage().grown() || croc.isPassenger() || croc.held() != null || croc.hunting
                || croc.startled > 0 || croc.life().partner() != null || croc.carrying() > 0) {
            return false;
        }
        FlyCloudEntity down = FlyCloudEntity.landedNear(croc, REACH, false);
        if (down == null || !down.claimLanded(croc)) {
            return false;
        }
        cloud = down;
        at = down.landedFly();
        return true;
    }

    @Override
    public void start() {
        t = 0;
        snapping = false;
        go();
    }

    /** Toward the fly at its own height (a path to the water's top, a block over a swimmer, finds none). */
    private void go() {
        if (!croc.getNavigation().moveTo(at.x, croc.getY(), at.z, 1.0)) {
            croc.getMoveControl().setWantedPosition(at.x, croc.getY(), at.z, 1.0);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return cloud != null && t < GIVE_UP && (snapping || cloud.claimedBy(croc)) && !croc.isPassenger() && croc.startled <= 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        t++;
        if (snapping) {
            snapT++;
            if (snapT == CrocodileEntity.SNAP_CATCH) {
                croc.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 0.4F, 1.8F);
                if (croc.getRandom().nextFloat() < MISS) {
                    cloud.landedFlyOff(croc);
                } else {
                    cloud.eatLanded(croc);
                    if (croc.level() instanceof ServerLevel server && cloud.landedOnWater()) {
                        server.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.05, at.z, 3, 0.1, 0.0, 0.1, 0.05);
                    }
                }
            }
            if (snapT >= CrocodileEntity.SNAP_TICKS) {
                cloud = null;
            }
            return;
        }
        Vec3 pivot = shoulders();
        double dx = at.x - pivot.x, dz = at.z - pivot.z, dy = at.y - pivot.y;
        double flat = Math.sqrt(dx * dx + dz * dz);
        double off = Mth.wrapDegrees(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG - 90.0 - croc.yBodyRot);
        double headReach = CrocodileEntity.HEAD_REACH * croc.getScale() + 0.2;
        if (croc.isInWater() && dy > 0.3 && flat < headReach + 2.0) {
            // a fly on the surface over it: it rises to the top to snap (it rests on the bed in the shallows)
            croc.setDeltaMovement(croc.getDeltaMovement().add(0, Math.min(0.06, dy * 0.05), 0));
        }
        if (flat < headReach + 0.3 && Math.abs(dy) < 1.2) {
            croc.getNavigation().stop();
            croc.setDeltaMovement(croc.getDeltaMovement().multiply(0.5, 1.0, 0.5));
            if (Math.abs(off) >= CrocodileEntity.SNAP_FROM_NOSE && Math.abs(off) <= 110.0) {
                right = off > 0;
                snapping = true;
                snapT = 0;
                croc.setJaws(right ? CrocodileEntity.JAWS_SNAP_RIGHT : CrocodileEntity.JAWS_SNAP_LEFT);
            } else {
                // the fly near its nose (or behind it): it swings its body round until the fly lies beside its head
                double side = off >= 0 ? 1.0 : -1.0;
                float want = (float) (croc.yBodyRot + off - side * CrocodileEntity.SNAP_SWING);
                float yaw = Mth.approachDegrees(croc.yBodyRot, want, 8.0F);
                croc.setYRot(yaw);
                croc.yBodyRot = yaw;
                croc.yHeadRot = yaw;
            }
        } else if (flat < CLOSE) {
            // the last stretch straight at it (a path this short counts as walked at once, as the barb's does)
            croc.getNavigation().stop();
            double push = CLOSE_PUSH * croc.sizePace() / Math.max(flat, 0.05);
            croc.setDeltaMovement(croc.getDeltaMovement().add(dx * push, 0, dz * push));
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            yaw = Mth.approachDegrees(croc.yBodyRot, yaw, 10.0F);
            croc.setYRot(yaw);
            croc.yBodyRot = yaw;
            croc.yHeadRot = yaw;
        } else if (t % 10 == 1 || croc.getNavigation().isDone()) {
            go();
        }
    }

    @Override
    public void stop() {
        if (croc.jaws() == CrocodileEntity.JAWS_SNAP_LEFT || croc.jaws() == CrocodileEntity.JAWS_SNAP_RIGHT) {
            croc.setJaws(CrocodileEntity.JAWS_NONE);
        }
        croc.getNavigation().stop();
        if (cloud != null) {
            cloud.releaseLanded(croc);
        }
        cloud = null;
        snapping = false;
    }

    /** Where its head swings from: its shoulders (the neck's joint), as its hunt reckons them. */
    private Vec3 shoulders() {
        float yaw = croc.yBodyRot * Mth.DEG_TO_RAD;
        double ahead = CrocodileEntity.NECK_AHEAD * croc.getScale();
        return new Vec3(croc.getX() - Mth.sin(yaw) * ahead, croc.getY() + CrocodileEntity.ROLL_AXIS * croc.getScale(), croc.getZ() + Mth.cos(yaw) * ahead);
    }
}
