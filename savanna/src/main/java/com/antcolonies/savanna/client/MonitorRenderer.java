package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.MonitorEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the Nile monitor (M9 river life, 2026-11-17; head and eyes on a near player in {@link MonitorModel}): one model and look ({@code tools/monitor_models.py game}), drawn
 * at {@link MonitorEntity#MODEL_SCALE} of its 6-block model. Its death clip lays it flat, so Minecraft's tip-over is off.
 */
public class MonitorRenderer extends GeoEntityRenderer<MonitorEntity> {
    public MonitorRenderer(EntityRendererProvider.Context context) {
        super(context, new MonitorModel());
        withScale(MonitorEntity.MODEL_SCALE);
        shadowRadius = 0.55F;
    }

    @Override
    protected float getDeathMaxRotation(MonitorEntity monitor) {
        return 0;
    }
}
