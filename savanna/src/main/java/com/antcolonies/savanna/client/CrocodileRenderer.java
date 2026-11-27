package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.CrocodileEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.antcolonies.client.BoneMeshRenderer;

/**
 * Draws the Nile crocodile (M9 river life, 2026-11-15): one model and look ({@code tools/crocodile_models.py game}).
 * Its body lies on what is under it ({@link CrocodileModel}). The death clip rolls it over itself, so Minecraft's tip-over is off.
 */
public class CrocodileRenderer extends BoneMeshRenderer<CrocodileEntity> {
    public CrocodileRenderer(EntityRendererProvider.Context context) {
        super(context, new CrocodileModel());
        addRenderLayer(new com.antcolonies.client.MudCoatLayer<>(this));      // mud on its body (savanna mud step 4)
        withScale(CrocodileEntity.MODEL_SCALE);
        shadowRadius = 1.1F;
    }

    @Override
    protected float getDeathMaxRotation(CrocodileEntity crocodile) {
        return 0;
    }

    /** A hatchling's or a teen's shadow follows its size (animal life cycle step 4, 2026-11-20); a grown one's is as before. */
    @Override
    protected float getShadowRadius(CrocodileEntity crocodile) {
        return super.getShadowRadius(crocodile) * Math.min(1.0F, crocodile.getScale() / 0.7F);
    }
}
