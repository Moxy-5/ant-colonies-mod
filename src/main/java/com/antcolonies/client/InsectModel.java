package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * The model of an ant or termite that can turn its head toward what it looks at (the animation engine, step 6,
 * 2026-10-16). While it looks at a player ({@code NoticeGoal}), its head turns toward the player on top of whatever its
 * clip does with the head (the look clip's small tilts and the antennae go on), easing in and out with
 * {@link AbstractAntEntity#getLookWeight}. Only as far as a real ant's head turns on its neck: the rest of the turn is
 * the whole body pivoting to face the player (the game turns a standing mob's body toward where its head looks).
 */
public class InsectModel<T extends AbstractAntEntity> extends DefaultedEntityGeoModel<T> {
    /** How far the head turns sideways and up or down on its neck, at most (degrees; my picks, to confirm). */
    private static final float MAX_YAW = 40.0F;
    private static final float MAX_PITCH = 25.0F;

    public InsectModel(ResourceLocation assetSubpath) {
        super(assetSubpath);
    }

    @Override
    public void setCustomAnimations(T insect, long instanceId, AnimationState<T> state) {
        super.setCustomAnimations(insect, instanceId, state);
        float weight = insect.getLookWeight(state.getPartialTick());
        if (weight <= 0.001F) {
            return;
        }
        GeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) {
            return;
        }
        // Added to the clip's own head angles each frame (the clip sets them afresh every frame, so nothing builds up).
        head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -MAX_YAW, MAX_YAW) * Mth.DEG_TO_RAD * weight);
        head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -MAX_PITCH, MAX_PITCH) * Mth.DEG_TO_RAD * weight);
    }
}
