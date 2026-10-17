package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
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

    /** This renderer's climbing lift and push into the wall (a bigger body needs more; see {@link #withClimbPose}). */
    private float climbLift = CLIMB_LIFT;
    private float climbIntoWall = CLIMB_INTO_WALL;

    public AntRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
        addRenderLayer(new YoungPaleLayer<>(this));
        addRenderLayer(new PelletLayer<>(this, context.bakeLayer(AntEggRenderer.LAYER), context.bakeLayer(AntCorpseRenderer.LAYER)));
    }

    /**
     * For a bigger body (a major soldier): how far the model is moved up while climbing and toward the wall (in model
     * units before the renderer's scale), so its feet touch the wall and its tail stays out of the floor.
     */
    public AntRenderer<T> withClimbPose(float lift, float intoWall) {
        this.climbLift = lift;
        this.climbIntoWall = intoWall;
        return this;
    }

    @Override
    public void render(T ant, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight) {
        ant.setDrawScale(scaleWidth); // for the stride: the legs keep pace with the ground at the size it is drawn
        ant.setClimbPose(climbLift * scaleHeight, climbIntoWall * scaleWidth); // for drawing it over an edge
        if (FilmDirector.active()) {
            // The in-game camera: every picture at the in-between time it asks for (a frozen game's own one jitters),
            // the insect's place included (the game put it where its own in-between time says).
            float partial = FilmDirector.drawPartial();
            Vec3 shift = ant.getPosition(partial).subtract(ant.getPosition(partialTick));
            poseStack.pushPose();
            poseStack.translate(shift.x, shift.y, shift.z);
            super.render(ant, entityYaw, partial, poseStack, buffers, packedLight);
            poseStack.popPose();
            FilmDirector.noteRendered(ant, getGeoModel());
            return;
        }
        super.render(ant, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    protected void applyRotations(T ant, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        // Over an outside edge (or just after, fading out): the middle of the feet goes where the body says (world
        // axes, in blocks; the pose here is already scaled to the drawn size).
        Vec3 edge = ant.getEdgeShift(partialTick);
        if (edge != null) {
            poseStack.translate(edge.x / scaleWidth, edge.y / scaleHeight, edge.z / scaleWidth);
        }
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
        if (edge != null) {
            // already put in place above
        } else if (tilt <= 90.0F) {
            float share = tilt / 90.0F;
            poseStack.translate(0.0F, climbLift * share, -(ant.isInChannel() ? CHANNEL_INTO_WALL : climbIntoWall) * share);
        } else {
            // From the wall onto the ceiling: the feet go up to the top of the hitbox, against the ceiling.
            float share = (tilt - 90.0F) / 90.0F;
            float ceiling = ant.getBbHeight() / scaleHeight;
            poseStack.translate(0.0F, climbLift + (ceiling - climbLift) * share, -climbIntoWall * (1.0F - share));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(tilt));
        poseStack.mulPose(Axis.YP.rotationDegrees(ant.getBodyHeading(partialTick)));
    }
}
