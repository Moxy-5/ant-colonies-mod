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
    private static final ResourceLocation CARCASS_POSE = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "animations/entity/buffalo_carcass.animation.json");
    private static final ResourceLocation[][] MODELS = new ResourceLocation[RemainsEntity.Kind.values().length][4];
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[RemainsEntity.Kind.values().length][4];
    /** A tilapia's remains in the young fish's and the breeding male's colours (by kind, look word, stage); the everyday fish wears {@link #TEXTURES}. */
    private static final ResourceLocation[][][] LOOKS = new ResourceLocation[RemainsEntity.Kind.values().length][2][4];

    static {
        for (RemainsEntity.Kind kind : RemainsEntity.Kind.values()) {
            for (int stage = 1; stage <= 4; stage++) {
                if (kind == RemainsEntity.Kind.BUFFALO) {
                    // a carcass: the buffalo's own model and coat in its death pose (lion step 2; bones later, Neon)
                    MODELS[kind.ordinal()][stage - 1] = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "geo/entity/buffalo.geo.json");
                    TEXTURES[kind.ordinal()][stage - 1] = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/buffalo.png");
                    continue;
                }
                String name = "remains_" + kind.name + "_" + stage;
                MODELS[kind.ordinal()][stage - 1] = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "geo/entity/" + name + ".geo.json");
                TEXTURES[kind.ordinal()][stage - 1] = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/" + name + ".png");
                LOOKS[kind.ordinal()][0][stage - 1] = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/" + name + "_young.png");
                LOOKS[kind.ordinal()][1][stage - 1] = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/" + name + "_breeding.png");
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
                boolean tilapia = remains.kind() == RemainsEntity.Kind.TILAPIA || remains.kind() == RemainsEntity.Kind.TILAPIA_HALF;
                int look = remains.look();
                if (tilapia && look != com.antcolonies.savanna.animal.TilapiaEntity.EVERYDAY) {
                    return LOOKS[remains.kind().ordinal()][look == com.antcolonies.savanna.animal.TilapiaEntity.YOUNG ? 0 : 1][remains.stage() - 1];
                }
                return TEXTURES[remains.kind().ordinal()][remains.stage() - 1];
            }

            @Override
            public ResourceLocation getAnimationResource(RemainsEntity remains) {
                return remains.kind() == RemainsEntity.Kind.BUFFALO ? CARCASS_POSE : ANIMATIONS;
            }

            @Override
            public void setCustomAnimations(RemainsEntity remains, long instanceId, software.bernie.geckolib.animation.AnimationState<RemainsEntity> state) {
                super.setCustomAnimations(remains, instanceId, state);
                if (remains.kind() == RemainsEntity.Kind.BUFFALO) {
                    var root = getAnimationProcessor().getBone("root");
                    if (root != null) {
                        boolean marked = root.hasRotationChanged() || root.hasPositionChanged() || root.hasScaleChanged();
                        root.setPosZ((float) (root.getPosZ() - com.antcolonies.savanna.animal.BuffaloEntity.DRAW_AHEAD_PX));  // drawn as the living one
                        if (!marked) {
                            root.resetStateChanges();
                        }
                    }
                }
            }
        });
        shadowRadius = 0.0F;
    }

    @Override
    protected void applyRotations(RemainsEntity remains, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(remains, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        // lying along its own heading (2026-11-15: remains are no living thing, so the renderer turned them all to face
        // south whatever way they were left; a bitten-off half fell away turned across the fish it came from)
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-remains.getYRot()));
        float scale = (remains.kind() == RemainsEntity.Kind.BUFFALO ? com.antcolonies.savanna.animal.BuffaloEntity.MODEL_SCALE
                : remains.kind() == RemainsEntity.Kind.TILAPIA || remains.kind() == RemainsEntity.Kind.TILAPIA_HALF ? TilapiaRenderer.SCALE : CatfishRenderer.SCALE) * remains.size();
        poseStack.scale(remains.onOtherSide() ? -scale : scale, scale, scale);      // mirrored: lying on its other side
    }
}
