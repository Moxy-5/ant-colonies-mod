package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.LionEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the lion, the lioness and the cub (2026-11-25; its form, coat, tilt and head in {@link LionModel}). Its death clip
 * lays it down itself, so Minecraft's tip-over is off.
 */
public class LionRenderer extends GeoEntityRenderer<LionEntity> {
    public LionRenderer(EntityRendererProvider.Context context) {
        super(context, new LionModel());
        shadowRadius = 0.8F;
    }

    @Override
    protected float getDeathMaxRotation(LionEntity lion) {
        return 0;
    }

    /**
     * Drawn at its own heading, also while riding its prey (a hold, phase 3). GeckoLib, like Minecraft, draws a rider
     * turned to its mount's heading (within 50 degrees exactly the mount's), so a lioness the game faced in at a calf's
     * flank was drawn along the calf, her jaw 1.4 to 1.8 blocks off its body (the hunt film, 2026-11-26).
     */
    @Override
    protected void applyRotations(LionEntity lion, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        float yaw = lion.isPassenger() ? Mth.rotLerp(partialTick, lion.yBodyRotO, lion.yBodyRot) : rotationYaw;
        super.applyRotations(lion, pose, ageInTicks, yaw, partialTick, nativeScale);
    }
}
