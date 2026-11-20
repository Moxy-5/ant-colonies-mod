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
 * hides it at the right moments), and the snail shell in its claws only while it cracks one (2026-11-16).
 */
public class CrabModel extends DefaultedEntityGeoModel<CrabEntity> {
    public CrabModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "crab"));
    }

    /**
     * Which look a crab wears (animal life cycle step 3, 2026-11-19; {@code tools/crab_models.py game}): the young the
     * usual crab; a male his big claws and narrow flap; a female her small claws and broad flap, with the orange egg
     * mass under it while she carries eggs. Each grown look plays clips re-aimed for its own claws.
     */
    private static String look(CrabEntity crab) {
        if (crab.life().isYoung()) {
            return "crab";
        }
        if (crab.life().isMale()) {
            return "crab_male";
        }
        return crab.isBerried() ? "crab_berried" : "crab_female";
    }

    private static ResourceLocation file(String path) {
        return ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, path);
    }

    @Override
    public ResourceLocation getModelResource(CrabEntity crab) {
        return file("geo/entity/" + look(crab) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CrabEntity crab) {
        return file("textures/entity/" + look(crab) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(CrabEntity crab) {
        String look = look(crab);
        return file("animations/entity/" + (look.equals("crab_berried") ? "crab_female" : look) + ".animation.json");
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
            hide("bit_meat_" + side, doing != CrabEntity.PICK_MEAT && doing != CrabEntity.CRACK);
        }
        hide("held_shell", doing != CrabEntity.CRACK);
    }

    private void hide(String name, boolean hidden) {
        GeoBone bone = getAnimationProcessor().getBone(name);
        if (bone != null) {
            bone.setHidden(hidden);
        }
    }
}
