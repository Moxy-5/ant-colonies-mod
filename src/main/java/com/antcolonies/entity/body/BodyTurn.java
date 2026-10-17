package com.antcolonies.entity.body;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

/**
 * The body, how it turns after the head (2026-10-16, found by the in-game camera's checks). The game's own rule lets
 * the head of a standing mob turn up to 75 degrees off its body and then, each time the head has gone 15 degrees on,
 * snaps the body round after it: with a head turning 10 degrees a tick the body jumped 20 degrees every other tick, and
 * an ant turning on the spot stuttered. A real ant's neck turns only a little; to face something it pivots its whole body
 * on its legs, a steady swing with the head just leading. So here a standing insect's body turns toward where its head
 * points every tick, a share of the way at a time up to a top speed, which is smooth at any head speed and settles with
 * the head straight ahead. Walking, the body faces the way it walks (as the game has it).
 */
public final class BodyTurn extends BodyRotationControl {
    /** The share of the way to the head's direction the body turns each tick (my pick, to confirm). */
    private static final float SHARE = 0.3F;
    /** Its top speed (degrees a tick; 300 a second, about a running Formica's quick turn; my pick, to confirm). */
    private static final float MAX_TURN = 15.0F;
    /** How far it counts as moving (the game's own: squared blocks a tick). */
    private static final double MOVING = 2.5E-7;

    private final Mob mob;

    public BodyTurn(Mob mob) {
        super(mob);
        this.mob = mob;
    }

    @Override
    public void clientTick() {
        double dx = mob.getX() - mob.xo;
        double dz = mob.getZ() - mob.zo;
        if (dx * dx + dz * dz > MOVING) {
            mob.yBodyRot = mob.getYRot();
            mob.yHeadRot = Mth.rotateIfNecessary(mob.yHeadRot, mob.yBodyRot, mob.getMaxHeadYRot());
            return;
        }
        float off = Mth.wrapDegrees(mob.yHeadRot - mob.yBodyRot);
        mob.yBodyRot += Mth.clamp(off * SHARE, -MAX_TURN, MAX_TURN);
        // and never further off the head than the game allows
        mob.yBodyRot = Mth.rotateIfNecessary(mob.yBodyRot, mob.yHeadRot, mob.getMaxHeadYRot());
    }
}
