package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.CrocodileEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the Nile crocodile (M9 river life, 2026-11-15): one model and look ({@code tools/crocodile_models.py game}).
 * Its body lies on what is under it ({@link CrocodileModel}). The death clip rolls it over itself, so Minecraft's tip-over is off.
 */
public class CrocodileRenderer extends GeoEntityRenderer<CrocodileEntity> {
    public CrocodileRenderer(EntityRendererProvider.Context context) {
        super(context, new CrocodileModel());
        withScale(CrocodileEntity.MODEL_SCALE);
        shadowRadius = 0.9F;
    }

    @Override
    protected float getDeathMaxRotation(CrocodileEntity crocodile) {
        return 0;
    }
}
