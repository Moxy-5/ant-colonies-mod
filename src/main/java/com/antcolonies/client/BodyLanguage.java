package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.body.BodyPlans;

import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * The live layer (the animation engine, 2026-10-16): what is added on top of the clips every frame, worked out from
 * what the insect is doing right then. Body only: it never decides anything, it only bends the bones.
 * <ul>
 * <li>the head look: the head turned toward what the insect looks at ({@code NoticeGoal}), within a real ant's neck;
 * <li>the edge curve: the waist bent by how far the front has turned ahead of the rear (over an edge, onto a wall or a
 * ceiling, up a ramp, round a turn; see {@link AbstractAntEntity#getWaistPitch}).
 * </ul>
 * Insects farther than {@link #NEAR} blocks from the camera skip it (too small to see; saves time with many ants).
 */
public final class BodyLanguage {
    /** How far the head turns sideways and up or down on its neck, at most (degrees; my picks, to confirm). */
    private static final float MAX_YAW = 40.0F;
    private static final float MAX_PITCH = 25.0F;
    /** The live layer only within this many blocks of the camera (my pick, to confirm). */
    public static final double NEAR = 32.0;
    /**
     * The waist's joints and their shares of the bend (petiole, postpetiole, the gaster's joint; my picks, to confirm)
     * for a model with no body plan; a plan names its own ("waist", {@link BodyPlans}).
     */
    private static final List<String> WAIST = List.of("petiole", "postpetiole", "abdomen");
    private static final float[] WAIST_SHARE = {0.40F, 0.35F, 0.25F};

    private BodyLanguage() {
    }

    /**
     * Adds the live layer to the bones the clips have just set (called each frame by the model; bones: by name, or null;
     * plan: the model's body plan, or null).
     */
    public static <T extends AbstractAntEntity> void apply(T insect, AnimationState<T> state, Function<String, GeoBone> bones,
            @Nullable BodyPlans.Plan plan) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (insect.position().distanceToSqr(camera) > NEAR * NEAR) {
            return;
        }
        float partialTick = state.getPartialTick();
        headLook(insect, state, bones, plan, partialTick);
        waist(insect, bones, plan, partialTick);
    }

    private static <T extends AbstractAntEntity> void headLook(T insect, AnimationState<T> state, Function<String, GeoBone> bones,
            @Nullable BodyPlans.Plan plan, float partialTick) {
        float weight = insect.getLookWeight(partialTick);
        if (weight <= 0.001F) {
            return;
        }
        GeoBone head = bones.apply(plan != null ? plan.bone("head") : "head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) {
            return;
        }
        turnBy(head, Mth.clamp(data.headPitch(), -MAX_PITCH, MAX_PITCH) * Mth.DEG_TO_RAD * weight,
                Mth.clamp(data.netHeadYaw(), -MAX_YAW, MAX_YAW) * Mth.DEG_TO_RAD * weight);
    }

    /**
     * Turns a bone this much more than the clip has it this frame (radians). GeckoLib puts a bone back to the clip's
     * pose (or its resting pose) at the start of each frame only if nothing marked it as moved, and setting a turn
     * marks it; so a bone the playing clip does not move (the postpetiole in the walk, the waist in the dig) kept this
     * frame's bend and got the next one added on top, frame after frame, and the model's bones are shared, so the next
     * ant drawn got it too (2026-10-16: the gaster messed up while climbing, seen in game by the user). So the marks
     * are put back as they were.
     */
    private static void turnBy(GeoBone bone, float x, float y) {
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setRotX(bone.getRotX() + x);
        bone.setRotY(bone.getRotY() + y);
        if (!marked) {
            bone.resetStateChanges();
        }
    }

    /**
     * The edge curve: the rear still lies the way the front lay a moment ago, so each waist joint takes its share of
     * the difference. A front tipped up past the rear lifts the gaster relative to the body (a positive X in the
     * animation file, which GeckoLib stores negated); a front turned right leaves the gaster pointing a little to the
     * right of the body's line behind (a negative Y in the file, stored negated).
     */
    private static void waist(AbstractAntEntity insect, Function<String, GeoBone> bones, @Nullable BodyPlans.Plan plan,
            float partialTick) {
        float pitch = insect.getWaistPitch(partialTick);
        float yaw = insect.getWaistYaw(partialTick);
        if (Math.abs(pitch) < 0.05F && Math.abs(yaw) < 0.05F) {
            return;
        }
        List<String> waist = plan != null ? plan.waist() : WAIST;
        float[] shares = plan != null ? plan.waistShare() : WAIST_SHARE;
        for (int i = 0; i < waist.size(); i++) {
            GeoBone joint = bones.apply(waist.get(i));
            if (joint == null) {
                continue;
            }
            turnBy(joint, -pitch * shares[i] * Mth.DEG_TO_RAD, yaw * shares[i] * Mth.DEG_TO_RAD);
        }
    }
}
