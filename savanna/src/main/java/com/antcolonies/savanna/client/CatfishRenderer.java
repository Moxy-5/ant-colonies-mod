package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CatfishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the river catfish (M9 river life, 2026-11-14): one model and look A ({@code tools/catfish_models.py game}), the
 * body pitched toward where it swims. The death clip rolls it onto its side itself, so Minecraft's tip-over is off.
 */
public class CatfishRenderer extends GeoEntityRenderer<CatfishEntity> {
    /** The model is 2.9 blocks long as made (snout to tail fin, 46.4 pixels); a usual catfish is 2 blocks (the user's pick). */
    static final float SCALE = 2.0F / 2.9F;

    public CatfishRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "catfish")));
        withScale(SCALE);
        shadowRadius = 0.5F;
    }

    @Override
    protected void applyRotations(CatfishEntity fish, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(fish, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float pitch = fish.swimPitch(partialTick);
        if (pitch != 0) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }

    @Override
    protected float getDeathMaxRotation(CatfishEntity fish) {
        return 0;
    }
}
