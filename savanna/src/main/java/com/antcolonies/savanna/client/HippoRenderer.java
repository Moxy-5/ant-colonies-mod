package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.HippoEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the hippo (2026-11-21; on a move along the move's path in {@link HippoModel}): one model and look
 * ({@code tools/hippo_models.py game}), drawn at {@link HippoEntity#MODEL_SCALE} of its 5-block model. Its death clip
 * rolls it onto its side, so Minecraft's tip-over is off.
 */
public class HippoRenderer extends GeoEntityRenderer<HippoEntity> {
    public HippoRenderer(EntityRendererProvider.Context context) {
        super(context, new HippoModel());
        withScale(HippoEntity.MODEL_SCALE);
        shadowRadius = 1.3F;
    }

    @Override
    protected float getDeathMaxRotation(HippoEntity hippo) {
        return 0;
    }
}
