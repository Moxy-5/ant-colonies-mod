package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.RemainsEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws a fish's remains (M9 river life, 2026-11-15): the model of its kind and stage, made by
 * {@code tools/remains_models.py game} already laid on its side with its lowest point at its feet, at the size of the
 * fish's own renderer times the fish's size.
 */
public class RemainsRenderer extends GeoEntityRenderer<RemainsEntity> {
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "animations/entity/remains.animation.json");
    private static final ResourceLocation[][] MODELS = new ResourceLocation[RemainsEntity.Kind.values().length][4];
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[RemainsEntity.Kind.values().length][4];

    static {
        for (RemainsEntity.Kind kind : RemainsEntity.Kind.values()) {
            for (int stage = 1; stage <= 4; stage++) {
                String name = "remains_" + kind.name + "_" + stage;
                MODELS[kind.ordinal()][stage - 1] = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "geo/entity/" + name + ".geo.json");
                TEXTURES[kind.ordinal()][stage - 1] = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/" + name + ".png");
            }
        }
    }

    public RemainsRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(RemainsEntity remains) {
                return MODELS[remains.kind().ordinal()][remains.stage() - 1];
            }

            @Override
            public ResourceLocation getTextureResource(RemainsEntity remains) {
                return TEXTURES[remains.kind().ordinal()][remains.stage() - 1];
            }

            @Override
            public ResourceLocation getAnimationResource(RemainsEntity remains) {
                return ANIMATIONS;
            }
        });
        shadowRadius = 0.0F;
    }

    @Override
    protected void applyRotations(RemainsEntity remains, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(remains, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float scale = (remains.kind() == RemainsEntity.Kind.TILAPIA || remains.kind() == RemainsEntity.Kind.TILAPIA_HALF ? TilapiaRenderer.SCALE : CatfishRenderer.SCALE) * remains.size();
        poseStack.scale(scale, scale, scale);
    }
}
