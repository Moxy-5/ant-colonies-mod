package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.LionEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
}
