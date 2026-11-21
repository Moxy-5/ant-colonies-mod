package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.TadpoleEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the bullfrog's tadpole (prey plan step 4, 2026-11-21; {@code tools/tadpole_models.py game}, the user's approved
 * model): one model with the hind legs' bones hidden until day 3 ({@link TadpoleEntity#hasLegs}). The death clip rolls
 * it belly up itself, so Minecraft's own tip-over is off.
 */
public class TadpoleRenderer extends GeoEntityRenderer<TadpoleEntity> {
    /** The model is 18 pixels long as made (1.125 blocks); a grown tadpole is drawn 0.15 blocks (my pick), its life's size on top. */
    static final float SCALE = 0.15F / 1.125F;

    public TadpoleRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(SCALE);
        shadowRadius = 0.05F;
    }

    @Override
    protected float getDeathMaxRotation(TadpoleEntity tadpole) {
        return 0;
    }

    @Override
    protected float getShadowRadius(TadpoleEntity tadpole) {
        return super.getShadowRadius(tadpole) * tadpole.getScale();
    }

    static class Model extends DefaultedEntityGeoModel<TadpoleEntity> {
        Model() {
            super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "tadpole"));
        }

        @Override
        public void setCustomAnimations(TadpoleEntity tadpole, long instanceId, AnimationState<TadpoleEntity> state) {
            super.setCustomAnimations(tadpole, instanceId, state);
            boolean legs = tadpole.hasLegs();
            getBone("leg_left").ifPresent(b -> b.setHidden(!legs));
            getBone("leg_right").ifPresent(b -> b.setHidden(!legs));
        }
    }
}
