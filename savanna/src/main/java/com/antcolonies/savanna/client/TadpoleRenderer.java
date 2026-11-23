package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.TadpoleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the bullfrog's tadpole (the river tie-up, 2026-11-23; {@code tools/tadpole_models.py game}): the body pitched
 * toward where it swims, its hind leg buds hidden until day 3 ({@link TadpoleEntity#hasLegs}). The death clip rolls it
 * belly up itself, so Minecraft's own tip-over is off.
 */
public class TadpoleRenderer extends GeoEntityRenderer<TadpoleEntity> {
    /** The model is 18 pixels long as made (1.125 blocks); a tadpole is drawn 0.15 blocks (the plan's pick). */
    static final float SCALE = 0.15F / 1.125F;

    public TadpoleRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(SCALE);
        shadowRadius = 0.0F;
    }

    @Override
    protected void applyRotations(TadpoleEntity tadpole, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(tadpole, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float pitch = tadpole.swimPitch(partialTick);
        if (pitch != 0) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }

    @Override
    protected float getDeathMaxRotation(TadpoleEntity tadpole) {
        return 0;
    }

    static class Model extends DefaultedEntityGeoModel<TadpoleEntity> {
        Model() {
            super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "tadpole"));
        }

        @Override
        public void setCustomAnimations(TadpoleEntity tadpole, long instanceId, AnimationState<TadpoleEntity> state) {
            super.setCustomAnimations(tadpole, instanceId, state);
            boolean legs = tadpole.hasLegs();
            for (String name : new String[] {"leg_left", "leg_right"}) {
                GeoBone bone = getAnimationProcessor().getBone(name);
                if (bone != null) {
                    bone.setHidden(!legs);
                }
            }
        }
    }
}
