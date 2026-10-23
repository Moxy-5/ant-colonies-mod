package com.antcolonies.nav;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * How an ant turns and pushes toward the point its navigator gave it.
 *
 * <p>The vanilla move control has a "jumping" state that ignores new aim points until the mob lands.
 * An ant going up a wall never lands, so it kept pushing in its old direction and never turned
 * toward the top of the wall. Ants climb instead of jumping, so this version has no such state.
 */
public class AntMoveControl extends MoveControl {
    public AntMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (operation != Operation.MOVE_TO) {
            mob.setZza(0.0F);
            return;
        }
        // The navigator sets a new aim point every tick, so this only lasts one tick.
        operation = Operation.WAIT;
        double dx = wantedX - mob.getX();
        double dy = wantedY - mob.getY();
        double dz = wantedZ - mob.getZ();
        if (dx * dx + dy * dy + dz * dz < 2.5E-7) {
            mob.setZza(0.0F);
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, 90.0F));
        mob.setSpeed((float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
    }
}
