package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.TilapiaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import com.antcolonies.client.BoneMeshRenderer;

/**
 * Draws the Nile tilapia (M9 river life, 2026-11-14): one model ({@code tools/tilapia_models.py game}) in three textures,
 * young, everyday or breeding by {@link TilapiaEntity#look()}, and a female's swollen throat with her mouth full; the body pitched toward where it swims. The death clip
 * rolls it onto its side itself, so Minecraft's own tip-over is off.
 */
public class TilapiaRenderer extends BoneMeshRenderer<TilapiaEntity> {
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
        if (fish.getVehicle() instanceof com.antcolonies.savanna.animal.OtterEntity) {
            // held, the otter places and turns it (OtterRenderer): none of its own turn, nor GeckoLib's turn for a rider
            // (which follows the vehicle and adds 22 degrees for a rider facing across it: the tilted fish, Neon 2026-11-22)
            return;
        }
        super.applyRotations(fish, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float pitch = fish.swimPitch(partialTick);
        if (pitch != 0) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }

    /** In an otter's jaws it is drawn by the otter, at its teeth ({@link OtterRenderer#render}), not here. */
    @Override
    public boolean shouldRender(TilapiaEntity fish, net.minecraft.client.renderer.culling.Frustum camera, double x, double y, double z) {
        return !(fish.getVehicle() instanceof com.antcolonies.savanna.animal.OtterEntity) && super.shouldRender(fish, camera, x, y, z);
    }

    @Override
    protected float getDeathMaxRotation(TilapiaEntity fish) {
        return 0;
    }

    /** A female with her mouth full of eggs or fry (animal life cycle step 2): the swollen throat, its own texture. */
    private static final ResourceLocation BROODING_MODEL = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "geo/entity/tilapia_brooding.geo.json");
    private static final ResourceLocation BROODING_TEXTURE = texture("tilapia_brooding");

    /** The tilapia's model (the brooding one with her mouth full), clips and its look's texture. */
    static class Model extends DefaultedEntityGeoModel<TilapiaEntity> {
        Model() {
            super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "tilapia"));
        }

        @Override
        public ResourceLocation getModelResource(TilapiaEntity fish) {
            return fish.mouthFull() ? BROODING_MODEL : super.getModelResource(fish);
        }

        @Override
        public ResourceLocation getTextureResource(TilapiaEntity fish) {
            if (fish.mouthFull()) {
                return BROODING_TEXTURE;
            }
            int look = fish.look();
            return TEXTURES[look >= 0 && look < TEXTURES.length ? look : TilapiaEntity.EVERYDAY];
        }
    }

    /** The shadow follows the animal's own size (a hatchling's is a hatchling's; animal life cycle step 3, 2026-11-19). */
    @Override
    protected float getShadowRadius(TilapiaEntity animal) {
        return super.getShadowRadius(animal) * animal.getScale();
    }
}
