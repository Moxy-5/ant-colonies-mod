package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;

import java.util.function.Function;

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
    /** The waist's joints and their shares of the bend (petiole, postpetiole, the gaster's joint; my picks, to confirm). */
    private static final String[] WAIST = {"petiole", "postpetiole", "abdomen"};
    private static final float[] WAIST_SHARE = {0.40F, 0.35F, 0.25F};

    private BodyLanguage() {
    }

    /** Adds the live layer to the bones the clips have just set (called each frame by the model; bones: by name, or null). */
    public static <T extends AbstractAntEntity> void apply(T insect, AnimationState<T> state, Function<String, GeoBone> bones) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (insect.position().distanceToSqr(camera) > NEAR * NEAR) {
            return;
        }
        float partialTick = state.getPartialTick();
        headLook(insect, state, bones, partialTick);
        waist(insect, bones, partialTick);
    }

    private static <T extends AbstractAntEntity> void headLook(T insect, AnimationState<T> state, Function<String, GeoBone> bones, float partialTick) {
        float weight = insect.getLookWeight(partialTick);
        if (weight <= 0.001F) {
            return;
        }
        GeoBone head = bones.apply("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) {
            return;
        }
        // Added to the clip's own head angles each frame (the clip sets them afresh every frame, so nothing builds up).
        head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -MAX_YAW, MAX_YAW) * Mth.DEG_TO_RAD * weight);
        head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -MAX_PITCH, MAX_PITCH) * Mth.DEG_TO_RAD * weight);
    }

    /**
     * The edge curve: the rear still lies the way the front lay a moment ago, so each waist joint takes its share of
     * the difference. A front tipped up past the rear lifts the gaster relative to the body (a positive X in the
     * animation file, which GeckoLib stores negated); a front turned right leaves the gaster pointing a little to the
     * right of the body's line behind (a negative Y in the file, stored negated).
     */
    private static void waist(AbstractAntEntity insect, Function<String, GeoBone> bones, float partialTick) {
        float pitch = insect.getWaistPitch(partialTick);
        float yaw = insect.getWaistYaw(partialTick);
        if (Math.abs(pitch) < 0.05F && Math.abs(yaw) < 0.05F) {
            return;
        }
        for (int i = 0; i < WAIST.length; i++) {
            GeoBone joint = bones.apply(WAIST[i]);
            if (joint == null) {
                continue;
            }
            joint.setRotX(joint.getRotX() - pitch * WAIST_SHARE[i] * Mth.DEG_TO_RAD);
            joint.setRotY(joint.getRotY() + yaw * WAIST_SHARE[i] * Mth.DEG_TO_RAD);
        }
    }
}
