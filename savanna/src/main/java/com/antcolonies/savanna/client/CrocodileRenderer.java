package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CrocodileEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the Nile crocodile (M9 river life, 2026-11-15): one model and look ({@code tools/crocodile_models.py game}).
 * The death clip rolls it over itself, so Minecraft's tip-over is off.
 */
public class CrocodileRenderer extends GeoEntityRenderer<CrocodileEntity> {
    /**
     * The model is 6.75 blocks long as made (snout to tail tip, 108 pixels) and drawn at 10/11 of that: a usual crocodile is
     * about 6.1 blocks long (5 until the user asked for a longer tail, 2026-11-15; the body kept its size).
     */
    static final float SCALE = 5.0F / 5.5F;

    public CrocodileRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "crocodile")));
        withScale(SCALE);
        shadowRadius = 0.9F;
    }

    @Override
    protected float getDeathMaxRotation(CrocodileEntity crocodile) {
        return 0;
    }
}
