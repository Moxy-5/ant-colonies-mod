package com.antcolonies.savanna.animal;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;

/**
 * The bullfrog's legs (prey plan step 3, 2026-11-20): in the water it swims as Minecraft's own frog does (its smooth
 * swimming control); on land it does not walk: it turns to where its route goes and hops there, a short crawl only for
 * the last bit under a hop's length ({@link BullfrogEntity#startHop}). A mind only says where to go (the navigation's
 * route); this control turns the route into hops (the CLAUDE.md layers: mind, legs, body).
 */
public class BullfrogMoveControl extends MoveControl {
    /**
     * Under this far to go (blocks, at a male's size) it crawls instead of hopping (the polish's step 1, 2026-11-20: was
     * 0.8, longer than a hop's 0.55, and the crawl hardly moved, so a hop landing short of a route's next point left the
     * frog sitting there for good: the course and aBullfrogHopsWhereItIsSentAndUpAStep).
     */
    static final double CRAWL_UNDER = 0.3;
    /** It hops only once it faces where it goes to within this (degrees). */
    static final float HOP_FACING = 25.0F;

    private final BullfrogEntity frog;
    private final SmoothSwimmingMoveControl swim;

    public BullfrogMoveControl(BullfrogEntity frog) {
        super(frog);
        this.frog = frog;
        swim = new SmoothSwimmingMoveControl(frog, 85, 10, 0.02F, 0.1F, true);
    }

    @Override
    public void tick() {
        if (frog.action() == BullfrogEntity.CLIMB_OUT) {
            return;             // the climb carries it (BullfrogEntity.climbTick)
        }
        if (frog.isInWater()) {
            // at a bank its route climbs onto: it hauls itself out (a move of its own), not glides up
            net.minecraft.core.BlockPos end = frog.getNavigation().getTargetPos();
            boolean leaving = end != null && !mob.level().getFluidState(end).is(net.minecraft.tags.FluidTags.WATER)
                    && end.getY() + 0.2 > mob.getY();
            if (operation == Operation.MOVE_TO && !frog.busy() && leaving) {
                net.minecraft.world.phys.Vec3 onto = frog.bankAhead(wantedX, wantedZ);
                if (onto != null) {
                    operation = Operation.WAIT;
                    frog.climbOut(onto);
                    return;
                }
            }
            if (operation == Operation.MOVE_TO && !frog.busy()) {
                swim.setWantedPosition(wantedX, wantedY, wantedZ, speedModifier);
                operation = Operation.WAIT;
            }
            swim.tick();
            return;
        }
        if (frog.action() == BullfrogEntity.STALK) {
            stalk();
            return;
        }
        if (operation != Operation.MOVE_TO || frog.busy()) {
            mob.setZza(0);
            mob.setSpeed(0);
            return;
        }
        double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ(), dy = wantedY - mob.getY();
        double far = Math.sqrt(dx * dx + dz * dz);
        if (far < 0.2 && Math.abs(dy) < 1.0) {
            operation = Operation.WAIT;
            mob.setZza(0);
            mob.setSpeed(0);
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, 20.0F));
        mob.yBodyRot = mob.getYRot();
        if (!mob.onGround()) {
            return;     // in the air: the hop carries it
        }
        float off = Math.abs(Mth.wrapDegrees(yaw - mob.getYRot()));
        double size = frog.getScale();
        if ((far > CRAWL_UNDER * size || dy > 0.5) && frog.mayHop()) {
            mob.setZza(0);
            mob.setSpeed(0);
            if (off < HOP_FACING) {
                frog.startHop(dy > 0.5, far);
            }
            return;
        }
        if (far <= CRAWL_UNDER * size) {
            // the last bit: a slow crawl at the walk clip's own pace (its planted feet stay put), moved straight (a
            // small walking speed through Minecraft's own push is squared to almost nothing)
            mob.setZza(0);
            mob.setSpeed(0);
            if (off < HOP_FACING) {
                float facing = mob.getYRot() * Mth.DEG_TO_RAD;
                double pace = Math.min(far, BullfrogEntity.WALK_CLIP_PACE * size);
                mob.setDeltaMovement(-Mth.sin(facing) * pace, mob.getDeltaMovement().y, Mth.cos(facing) * pace);
            }
        } else {
            mob.setZza(0);
            mob.setSpeed(0);
        }
    }

    /**
     * The creep toward prey (the polish's step 4; its mind sets where): turned toward it slowly, moved along the way it
     * faces at the stalk clip's own pace, so its planted feet stay put; it waits while it is still turning.
     */
    private void stalk() {
        mob.setZza(0);
        mob.setSpeed(0);
        if (operation != Operation.MOVE_TO || !mob.onGround()) {
            return;
        }
        double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ();
        if (dx * dx + dz * dz < 0.01) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, 6.0F));
        mob.yBodyRot = mob.getYRot();
        mob.yHeadRot = mob.getYRot();
        if (Math.abs(Mth.wrapDegrees(yaw - mob.getYRot())) > 20.0F) {
            return;
        }
        float facing = mob.getYRot() * Mth.DEG_TO_RAD;
        double pace = BullfrogEntity.STALK_CLIP_PACE * frog.getScale();
        mob.setDeltaMovement(-Mth.sin(facing) * pace, mob.getDeltaMovement().y, Mth.cos(facing) * pace);
    }
}
