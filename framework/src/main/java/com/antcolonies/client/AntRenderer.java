package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.body.BodyPlans;
import com.antcolonies.entity.body.LostLegs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
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
    /** How far the model is moved toward the wall inside a narrow channel (a termite tunnel): less, so the body stays in the channel's middle. */
    private static final float CHANNEL_INTO_WALL = 0.08F;

    /**
     * How far the model is moved up while climbing, so the tail doesn't sink into the floor, and toward the wall, so the
     * feet touch it (before the renderer's scale): from the body plan of the model drawn ({@link BodyPlans.Climb}; a
     * bigger body needs more), set each frame.
     */
    private float climbLift;
    private float climbIntoWall;
    /** The geo model last drawn and its body plan (the queen draws the worker's model, so she gets the worker's plan). */
    private ResourceLocation planModel;
    private BodyPlans.Plan plan;
    /** Whether the last insect drawn had a leg hidden (so the next one shows all its legs again). */
    private boolean anyLegHidden;

    public AntRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
        addRenderLayer(new YoungPaleLayer<>(this));
        addRenderLayer(new CarriedLoadLayer<>(this, context));
        addRenderLayer(new ClampedHeadLayer<>(this, context));
    }

    @Override
    public void render(T ant, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight) {
        float drawWidth = drawWidth(ant);
        float drawHeight = drawHeight(ant);
        ant.setDrawScale(drawWidth); // for the stride: the legs keep pace with the ground at the size it is drawn
        BodyPlans.Plan plan = planOf(ant);
        ant.setBodyPlan(plan); // its own waist and feet, for the bend and for drawing it over an edge
        BodyPlans.Climb climb = plan != null ? plan.climb() : BodyPlans.WORKER_CLIMB;
        climbLift = (float) climb.liftFor(ant.getBbHeight(), drawHeight);
        climbIntoWall = (float) climb.intoWallFor(ant.getBbWidth(), drawWidth);
        ant.setClimbPose(climbLift * drawHeight, climbIntoWall * drawWidth); // for drawing it over an edge
        showLegs(ant, plan);
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

    /**
     * Hides the legs it has lost from the knee down (M8 step 2d, {@link LostLegs}), the thigh left as a stump. The model's
     * bones are shared by every insect drawn with it, so each one sets all six before it is drawn.
     */
    private void showLegs(T ant, BodyPlans.Plan plan) {
        int lost = ant.lostLegs();
        if (lost == 0 && !anyLegHidden) {
            return;
        }
        anyLegHidden = lost != 0;
        for (int i = 0; i < LostLegs.LEGS.size(); i++) {
            String role = LostLegs.lowerBone(i);
            boolean gone = LostLegs.isGone(lost, i);
            getGeoModel().getBone(plan != null ? plan.bone(role) : role).ifPresent(bone -> bone.setHidden(gone));
        }
    }

    /**
     * The size it is drawn at: the renderer's own scale times the insect's own size (M8 step 1c: each Matabele ant has
     * its own; 1 for every other insect, which then draws exactly as before). GeckoLib scales the pose by both.
     */
    private float drawWidth(T ant) {
        return scaleWidth * ant.getScale();
    }

    private float drawHeight(T ant) {
        return scaleHeight * ant.getScale();
    }

    private BodyPlans.Plan planOf(T ant) {
        ResourceLocation model = getGeoModel().getModelResource(ant);
        if (!model.equals(planModel)) {
            plan = BodyPlans.ofGeo(model);
            planModel = model;
        }
        return plan;
    }

    @Override
    protected void applyRotations(T ant, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        // Over an outside edge (or just after, fading out): the middle of the feet goes where the body says (world
        // axes, in blocks; the pose here is already scaled to the drawn size).
        Vec3 edge = ant.getEdgeShift(partialTick);
        if (edge != null) {
            poseStack.translate(edge.x / drawWidth(ant), edge.y / drawHeight(ant), edge.z / drawWidth(ant));
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
            float ceiling = ant.getBbHeight() / drawHeight(ant);
            poseStack.translate(0.0F, climbLift + (ceiling - climbLift) * share, -climbIntoWall * (1.0F - share));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(tilt));
        poseStack.mulPose(Axis.YP.rotationDegrees(ant.getBodyHeading(partialTick)));
    }
}
