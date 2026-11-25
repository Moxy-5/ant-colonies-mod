package com.antcolonies.savanna.animal;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Minecraft's move control with its turn held to a most a tick (its own allows 90; made for the Cape buffalo, shared with
 * the lions 2026-11-25): a long heavy body swings round over a few ticks instead of its head jumping blocks in one.
 */
public final class HeavyTurn extends MoveControl {
    private final float maxTurn;

    public HeavyTurn(Mob mob, float maxTurn) {
        super(mob);
        this.maxTurn = maxTurn;
    }

    @Override
    public void tick() {
        float before = mob.getYRot();
        super.tick();
        float turned = Mth.wrapDegrees(mob.getYRot() - before);
        if (Math.abs(turned) > maxTurn) {
            mob.setYRot(before + Math.copySign(maxTurn, turned));
        }
    }
}
