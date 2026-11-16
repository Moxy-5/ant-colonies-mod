package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CrabEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The river crab's model (M9 river life, 2026-11-15), with two things on top of its clips: the whole body turned
 * sideways to its heading while it walks or stands (a crab walks sideways; it faces what it picks at or threatens), and
 * the bit of food between a claw's fingers shown in the right kind (algae green or meat pink; the pick clip shows and
 * hides it at the right moments).
 */
public class CrabModel extends DefaultedEntityGeoModel<CrabEntity> {
    public CrabModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "crab"));
    }

    @Override
    public void setCustomAnimations(CrabEntity crab, long instanceId, AnimationState<CrabEntity> state) {
        super.setCustomAnimations(crab, instanceId, state);
        GeoBone body = getAnimationProcessor().getBone("body");
        float turn = crab.sideTurn(state.getPartialTick());
        if (body != null && turn != 0.0F) {
            boolean marked = body.hasRotationChanged() || body.hasPositionChanged() || body.hasScaleChanged();
            body.setRotY(body.getRotY() + (float) Math.toRadians(turn));
            if (!marked) {
                body.resetStateChanges();
            }
        }
        byte doing = crab.doing();
        for (String side : new String[] {"left", "right"}) {
            hide("bit_green_" + side, doing != CrabEntity.PICK_ALGAE);
            hide("bit_meat_" + side, doing != CrabEntity.PICK_MEAT);
        }
    }

    private void hide(String name, boolean hidden) {
        GeoBone bone = getAnimationProcessor().getBone(name);
        if (bone != null) {
            bone.setHidden(hidden);
        }
    }
}
