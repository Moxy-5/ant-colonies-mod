package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.SnailEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the river snail (M9 river life, 2026-11-16): the Garden spiral ({@code tools/snail_models.py game}), 0.4 blocks
 * long. The death clip rolls it onto its shell's side itself, so Minecraft's tip-over is off.
 */
public class SnailRenderer extends GeoEntityRenderer<SnailEntity> {
    public SnailRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "snail")));
        withScale(SnailEntity.MODEL_SCALE);
        shadowRadius = 0.15F;
    }

    @Override
    protected float getDeathMaxRotation(SnailEntity snail) {
        return 0;
    }

    /** The shadow follows the animal's own size (a hatchling's is a hatchling's; animal life cycle step 3, 2026-11-19). */
    @Override
    protected float getShadowRadius(SnailEntity animal) {
        return super.getShadowRadius(animal) * animal.getScale();
    }
}
