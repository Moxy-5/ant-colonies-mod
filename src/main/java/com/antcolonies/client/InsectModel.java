package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The model of an ant or termite with the live layer on top of its clips ({@link BodyLanguage}): the head turned toward
 * what it looks at (the animation engine, step 6) and the waist bending over edges and round turns (step 9).
 */
public class InsectModel<T extends AbstractAntEntity> extends DefaultedEntityGeoModel<T> {
    public InsectModel(ResourceLocation assetSubpath) {
        super(assetSubpath);
    }

    @Override
    public void setCustomAnimations(T insect, long instanceId, AnimationState<T> state) {
        super.setCustomAnimations(insect, instanceId, state);
        BodyLanguage.apply(insect, state, name -> getAnimationProcessor().getBone(name));
    }
}
