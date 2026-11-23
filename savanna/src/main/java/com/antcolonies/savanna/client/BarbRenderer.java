package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BarbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the river barb (M9 river life, 2026-11-20): one model ({@code tools/barb_models.py game}) in two textures, the
 * everyday look and the flash (the flank bright silver) while it turns hard or darts off ({@link BarbEntity#flashing});
 * the body pitched toward where it swims. The death clip rolls it onto its side itself, so Minecraft's own tip-over is off.
 */
public class BarbRenderer extends GeoEntityRenderer<BarbEntity> {
    /** The model is 30 pixels long as made (1.875 blocks); a grown barb is drawn 0.25 blocks (my pick), its own size on top. */
    static final float SCALE = 0.25F / 1.875F;

    private static final ResourceLocation EVERYDAY = texture("barb");
    private static final ResourceLocation FLASH = texture("barb_flash");

    public BarbRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(SCALE);
        shadowRadius = 0.08F;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    protected void applyRotations(BarbEntity fish, PoseStack poseStack, float ageInTicks, float rotationYaw,
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
    public boolean shouldRender(BarbEntity fish, net.minecraft.client.renderer.culling.Frustum camera, double x, double y, double z) {
        return !(fish.getVehicle() instanceof com.antcolonies.savanna.animal.OtterEntity) && super.shouldRender(fish, camera, x, y, z);
    }

    @Override
    protected float getDeathMaxRotation(BarbEntity fish) {
        return 0;
    }

    @Override
    protected float getShadowRadius(BarbEntity fish) {
        return super.getShadowRadius(fish) * fish.getScale();
    }

    static class Model extends DefaultedEntityGeoModel<BarbEntity> {
        Model() {
            super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "barb"));
        }

        @Override
        public ResourceLocation getTextureResource(BarbEntity fish) {
            return fish.flashing() ? FLASH : EVERYDAY;
        }
    }
}
