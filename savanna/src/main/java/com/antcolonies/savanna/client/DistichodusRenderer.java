package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.DistichodusEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the six-banded distichodus (the river food web's step 5, 2026-11-23; model, paint and clips from
 * {@code tools/distichodus_models.py game}): the body pitched toward where it swims, but never while it leaps (the leap
 * clip pitches it through its arc itself). The death clip rolls it onto its side itself, so Minecraft's tip-over is off.
 */
public class DistichodusRenderer extends GeoEntityRenderer<DistichodusEntity> {
    /** The model is 37 pixels long as made (2.3125 blocks); a grown one is drawn 0.8 blocks (the plan's), its own size on top. */
    static final float SCALE = 0.8F / 2.3125F;

    public DistichodusRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "distichodus")));
        withScale(SCALE);
        shadowRadius = 0.2F;
    }

    @Override
    protected void applyRotations(DistichodusEntity fish, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(fish, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float pitch = fish.swimPitch(partialTick);
        if (pitch != 0) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }

    @Override
    protected float getDeathMaxRotation(DistichodusEntity fish) {
        return 0;
    }

    @Override
    protected float getShadowRadius(DistichodusEntity fish) {
        return super.getShadowRadius(fish) * fish.getScale();
    }
}
