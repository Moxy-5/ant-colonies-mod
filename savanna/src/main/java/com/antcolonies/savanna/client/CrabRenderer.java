package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.CrabEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the river crab (M9 river life, 2026-11-15): one model and look ({@code tools/crab_models.py game}). The death
 * clip turns it onto its back itself, so Minecraft's tip-over is off.
 */
public class CrabRenderer extends GeoEntityRenderer<CrabEntity> {
    public CrabRenderer(EntityRendererProvider.Context context) {
        super(context, new CrabModel());
        addRenderLayer(new com.antcolonies.client.MudCoatLayer<>(this));      // mud on its body (savanna mud step 4)
        withScale(CrabEntity.MODEL_SCALE);
        shadowRadius = 0.3F;
    }

    @Override
    protected float getDeathMaxRotation(CrabEntity crab) {
        return 0;
    }

    /** The shadow follows the animal's own size (a hatchling's is a hatchling's; animal life cycle step 3, 2026-11-19). */
    @Override
    protected float getShadowRadius(CrabEntity animal) {
        return super.getShadowRadius(animal) * animal.getScale();
    }
}
