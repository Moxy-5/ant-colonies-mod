package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.OtterEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the otter (2026-11-22; on a block move along the move's path in {@link OtterModel}): one model and look
 * ({@code tools/otter_models.py game}), drawn at {@link OtterEntity#MODEL_SCALE} of its 2.6-block model, with mud on its
 * fur when it has been in mud. Its death clip lays it on its side, so Minecraft's tip-over is off.
 */
public class OtterRenderer extends GeoEntityRenderer<OtterEntity> {
    public OtterRenderer(EntityRendererProvider.Context context) {
        super(context, new OtterModel());
        addRenderLayer(new com.antcolonies.client.MudCoatLayer<>(this));
        withScale(OtterEntity.MODEL_SCALE);
        shadowRadius = 0.45F;
    }

    @Override
    protected float getDeathMaxRotation(OtterEntity otter) {
        return 0;
    }
}
