package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.body.BodyPlans;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The model of an ant or termite with the live layer on top of its clips ({@link BodyLanguage}): the head turned toward
 * what it looks at (the animation engine, step 6) and the waist bending over edges and round turns (step 9). The bones
 * it bends come from the model's body plan ({@link BodyPlans}).
 */
public class InsectModel<T extends AbstractAntEntity> extends DefaultedEntityGeoModel<T> {
    /** The geo model last drawn and its body plan (the queen draws the worker's model, so she gets the worker's plan). */
    private ResourceLocation planModel;
    private BodyPlans.Plan plan;

    /** The model its body plan is kept under, when that is not the model drawn (a far look: the full model's). */
    private ResourceLocation planFrom;

    public InsectModel(ResourceLocation assetSubpath) {
        super(assetSubpath);
    }

    /**
     * The far look of the model at {@code assetSubpath} (2026-11-09, the drawn-only insects' step 2): the same clips and
     * body plan, the model and texture of {@code farSubpath}, which has the same bones and far fewer boxes. Drawing an
     * insect costs about a millionth of a second a box (measured: {@code tools/perf_scenes.py}), and the Matabele's
     * full model has 227. A model of its own, drawn by a renderer of its own ({@code AntRenderer}'s far renderer):
     * GeckoLib restarts an insect's clips whenever one model object is switched from one model file to another.
     */
    public static <T extends AbstractAntEntity> InsectModel<T> farLook(ResourceLocation assetSubpath, ResourceLocation farSubpath) {
        InsectModel<T> far = new InsectModel<>(assetSubpath);
        far.planFrom = far.buildFormattedModelPath(assetSubpath);
        far.withAltModel(farSubpath).withAltTexture(farSubpath);
        return far;
    }

    /** The model its body plan is kept under: the full one, whichever look is drawn (the bones are the same). */
    public ResourceLocation planResource(T insect) {
        return planFrom != null ? planFrom : getModelResource(insect);
    }

    /** A far insect is drawn with its last pose again while that is fresh ({@link PoseMemory}); else worked out. */
    @Override
    public void handleAnimations(T insect, long instanceId, AnimationState<T> state, float partialTick) {
        if (!PoseMemory.far(insect)) {
            super.handleAnimations(insect, instanceId, state, partialTick);
            return;
        }
        var bones = getAnimationProcessor().getRegisteredBones();
        if (!PoseMemory.reuse(insect, this, bones)) {
            super.handleAnimations(insect, instanceId, state, partialTick);
            PoseMemory.keep(insect, this, getAnimationProcessor().getRegisteredBones());
        }
    }

    @Override
    public void setCustomAnimations(T insect, long instanceId, AnimationState<T> state) {
        super.setCustomAnimations(insect, instanceId, state);
        BodyLanguage.apply(insect, state, name -> getAnimationProcessor().getBone(name), planOf(insect));
    }

    private BodyPlans.Plan planOf(T insect) {
        ResourceLocation model = planResource(insect);
        if (!model.equals(planModel)) {
            plan = BodyPlans.ofGeo(model);
            planModel = model;
        }
        return plan;
    }
}
