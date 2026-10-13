package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renderer for every ant and termite. On a wall the whole model lies on the wall: it faces the wall, is tipped upright
 * so its belly and legs are against it, and is turned within the wall so its head points the way it goes (up, down
 * head first, or sideways). On a ceiling it is tipped on over, upside down with its feet on the ceiling, and turned
 * the way it goes. On a slope (a ramp) it tilts with the slope. The angles come from the insect and turn smoothly (see
 * {@code AbstractAntEntity.turnBody} and {@code tiltOnSlope}).
 */
public class AntRenderer<T extends AbstractAntEntity> extends GeoEntityRenderer<T> {
    /** How far the model is moved up while climbing, so the tail doesn't sink into the floor. */
    private static final float CLIMB_LIFT = 0.25F;
    /** How far the model is moved toward the wall, so the feet touch it. */
    private static final float CLIMB_INTO_WALL = 0.17F;
    /** The same inside a narrow channel (a termite tunnel): less, so the body stays in the channel's middle. */
    private static final float CHANNEL_INTO_WALL = 0.08F;

    public AntRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
        addRenderLayer(new PelletLayer<>(this, context.bakeLayer(AntEggRenderer.LAYER), context.bakeLayer(AntCorpseRenderer.LAYER)));
    }

    @Override
    protected void applyRotations(T ant, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        if (!ant.isInWallPose()) {
            super.applyRotations(ant, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
            // On a slope (a ramp): tilt with it, head up going uphill, leaning across it.
            float pitch = ant.getSlopePitch(partialTick);
            float roll = ant.getSlopeRoll(partialTick);
            if (pitch != 0.0F || roll != 0.0F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
            }
            return;
        }
        // Face the wall, slide against it, tip upright (head up) or on over (upside down), then turn to the heading.
        super.applyRotations(ant, poseStack, ageInTicks, ant.getWallYaw(partialTick), partialTick, nativeScale);
        float tilt = ant.getBodyTilt(partialTick);
        if (tilt <= 90.0F) {
            float share = tilt / 90.0F;
            poseStack.translate(0.0F, CLIMB_LIFT * share, -(ant.isInChannel() ? CHANNEL_INTO_WALL : CLIMB_INTO_WALL) * share);
        } else {
            // From the wall onto the ceiling: the feet go up to the top of the hitbox, against the ceiling.
            float share = (tilt - 90.0F) / 90.0F;
            float ceiling = ant.getBbHeight() / scaleHeight;
            poseStack.translate(0.0F, CLIMB_LIFT + (ceiling - CLIMB_LIFT) * share, -CLIMB_INTO_WALL * (1.0F - share));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(tilt));
        poseStack.mulPose(Axis.YP.rotationDegrees(ant.getBodyHeading(partialTick)));
    }
}
