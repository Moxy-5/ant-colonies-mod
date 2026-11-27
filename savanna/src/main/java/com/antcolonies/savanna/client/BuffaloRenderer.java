package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.BuffaloEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.antcolonies.client.BoneMeshRenderer;

/**
 * Draws the Cape buffalo (2026-11-25; its draw forward, trunk tilt and eyes in {@link BuffaloModel}): one model and look
 * (Neon's v12), drawn at {@link BuffaloEntity#MODEL_SCALE} of its 3.8-block model, with mud on its hide when it has been
 * in mud (once its mud masks are made). Its death clip rolls it onto its side, so Minecraft's tip-over is off.
 */
public class BuffaloRenderer extends BoneMeshRenderer<BuffaloEntity> {
    public BuffaloRenderer(EntityRendererProvider.Context context) {
        super(context, new BuffaloModel());
        addRenderLayer(new com.antcolonies.client.MudCoatLayer<>(this));
        withScale(BuffaloEntity.MODEL_SCALE);
        shadowRadius = 1.0F;
    }

    @Override
    protected float getDeathMaxRotation(BuffaloEntity buffalo) {
        return 0;
    }
}
