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
    /** Under this far to go (blocks, at a male's size) it crawls instead of hopping. */
    static final double CRAWL_UNDER = 0.8;
    /** The crawl's pace (blocks a tick, at a male's size; my pick: a slow heavy crawl). */
    static final double CRAWL_PACE = 0.012;
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
        if (frog.isInWater()) {
            if (operation == Operation.MOVE_TO && !frog.busy()) {
                swim.setWantedPosition(wantedX, wantedY, wantedZ, speedModifier);
                operation = Operation.WAIT;
            }
            swim.tick();
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
                frog.startHop(dy > 0.5);
            }
            return;
        }
        if (far <= CRAWL_UNDER * size) {
            // the last bit: a slow crawl (Minecraft's walking push, small)
            mob.setSpeed((float) (CRAWL_PACE * size / 2.9));
        } else {
            mob.setZza(0);
            mob.setSpeed(0);
        }
    }
}
