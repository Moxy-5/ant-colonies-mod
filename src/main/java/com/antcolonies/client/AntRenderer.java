package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renderer for every ant. When the ant climbs a wall, the whole model is turned upright so its
 * belly and legs face the wall and its head points up (a spider would face INTO the wall instead).
 */
public class AntRenderer<T extends AbstractAntEntity> extends GeoEntityRenderer<T> {
    /** How far the model is moved up while climbing, so the tail doesn't sink into the floor. */
    private static final float CLIMB_LIFT = 0.25F;
    /** How far the model is moved toward the wall, so the feet touch it. */
    private static final float CLIMB_INTO_WALL = 0.17F;

    public AntRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
        addRenderLayer(new PelletLayer<>(this));
    }

    @Override
    protected void applyRotations(T ant, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(ant, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        if (ant.isClimbing()) {
            // The ant faces the wall. Slide it against the wall, then tip its head up 90 degrees.
            poseStack.translate(0.0F, CLIMB_LIFT, -CLIMB_INTO_WALL);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
    }
}
