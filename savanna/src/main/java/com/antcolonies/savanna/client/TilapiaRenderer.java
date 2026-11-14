package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.TilapiaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the Nile tilapia (M9 river life, 2026-11-14): one model ({@code tools/tilapia_models.py game}) in three textures,
 * young, everyday or breeding by {@link TilapiaEntity#look()}; the body pitched toward where it swims. The death clip
 * rolls it onto its side itself, so Minecraft's own tip-over is off.
 */
public class TilapiaRenderer extends GeoEntityRenderer<TilapiaEntity> {
    /**
     * The model is about 1.72 blocks long as made (its boxes in pixels); a grown fish is 0.6 blocks (my pick, bigger than life
     * so it is seen: the user, 2026-11-14), its own
     * size and youth on top through the SCALE attribute.
     */
    static final float SCALE = 0.6F / 1.725F;

    private static final ResourceLocation[] TEXTURES = {
            texture("tilapia_young"), texture("tilapia"), texture("tilapia_breeding")};

    public TilapiaRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(SCALE);
        shadowRadius = 0.2F;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    protected void applyRotations(TilapiaEntity fish, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(fish, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float pitch = fish.swimPitch(partialTick);
        if (pitch != 0) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }

    @Override
    protected float getDeathMaxRotation(TilapiaEntity fish) {
        return 0;
    }

    /** The tilapia's model, clips and its look's texture. */
    static class Model extends DefaultedEntityGeoModel<TilapiaEntity> {
        Model() {
            super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "tilapia"));
        }

        @Override
        public ResourceLocation getTextureResource(TilapiaEntity fish) {
            int look = fish.look();
            return TEXTURES[look >= 0 && look < TEXTURES.length ? look : TilapiaEntity.EVERYDAY];
        }
    }
}
