package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CatfishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.antcolonies.savanna.animal.TilapiaEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the river catfish (M9 river life, 2026-11-14): one model and look A ({@code tools/catfish_models.py game}), the
 * body pitched toward where it swims. The death clip rolls it onto its side itself, so Minecraft's tip-over is off.
 *
 * <p>A caught tilapia (2026-11-15, the user's ask: "the same exact model") is drawn by the tilapia's own renderer, in
 * its own look and size, at the catfish model's {@code prey} bone, which the catch clip tilts between the open jaws and
 * slides in: tail end between the jaws, lying flat (a deep-bodied fish fits the wide flat mouth only on its side;
 * on its right side if the catfish came at it from its right, else on its left: the user, 2026-11-15),
 * heading the way the catfish does (caught from behind). It is drawn after the catfish, from the bone's place noted
 * while the catfish was drawn, so the two models' buffers never mix.
 */
public class CatfishRenderer extends GeoEntityRenderer<CatfishEntity> {
    /** The model is 2.9 blocks long as made (snout to tail fin, 46.4 pixels); a usual catfish is 2 blocks (the user's pick). */
    static final float SCALE = 2.0F / 2.9F;

    public CatfishRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "catfish")));
        withScale(SCALE);
        shadowRadius = 0.5F;
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void renderForBone(PoseStack poseStack, CatfishEntity fish, GeoBone bone, RenderType renderType,
                    MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                if (bone.getName().equals("prey") && bone.getScaleX() > 0.5F) {
                    poseStack.pushPose();
                    poseStack.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F, bone.getPivotZ() / 16.0F);
                    preyPose.set(poseStack.last().pose());
                    preyNormal.set(poseStack.last().normal());
                    preySeen = true;
                    poseStack.popPose();
                }
            }
        });
    }

    /** The tilapia model's tail tip behind its middle and its body's mid line over its feet (model pixels, tilapia_models.py). */
    private static final float TILAPIA_TAIL = 15.6F, TILAPIA_MID = 6.0F;
    /** The tilapia model's length, snout to tail tip (model pixels). */
    private static final float TILAPIA_LENGTH = 27.6F;
    /** How far the mouth's front is from where the held fish's tail tip is at first (catfish model pixels: the hinge to the lips 5, the clip's 2 past the hinge). */
    private static final float IN_THE_MOUTH = 7.0F;
    /** The roll that lays the tilapia on its right side, its back to the catfish's right (degrees). */
    private static final float ON_RIGHT = -90.0F;

    /** Where the prey bone was this frame (the jaw's hinge, in the catfish's drawn space), and whether it was drawn at all. */
    private final Matrix4f preyPose = new Matrix4f();
    private final Matrix3f preyNormal = new Matrix3f();
    private boolean preySeen;

    @Override
    public void render(CatfishEntity fish, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        preySeen = false;
        super.render(fish, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        TilapiaEntity prey = preySeen ? fish.heldDrawn() : null;
        if (prey == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.last().pose().set(preyPose);
        poseStack.last().normal().set(preyNormal);
        float undo = 1.0F / (SCALE * fish.getScale());      // out of the catfish's size, into blocks
        poseStack.scale(undo, undo, undo);
        float drawn = TilapiaRenderer.SCALE * prey.getScale();
        // its tail tip at the hinge, the fish ahead of it; and whatever its size, half of it between the jaws (the clip
        // is made for a grown fish in a usual catfish: a young one would lie wholly inside the mouth, unseen)
        float ahead = IN_THE_MOUTH / 16.0F * SCALE * fish.getScale() - TILAPIA_LENGTH / 16.0F * drawn / 2;
        poseStack.translate(0, 0, -TILAPIA_TAIL / 16.0F * drawn - ahead);
        // lying flat between the jaws, on the side the catfish came at it from
        poseStack.mulPose(Axis.ZP.rotationDegrees(fish.heldOnRight() ? ON_RIGHT : -ON_RIGHT));
        poseStack.translate(0, -TILAPIA_MID / 16.0F * drawn, 0);        // turned about its own mid line
        entityRenderDispatcher.getRenderer(prey).render(prey, 0, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    protected void applyRotations(CatfishEntity fish, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(fish, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float pitch = fish.swimPitch(partialTick);
        if (pitch != 0) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }

    @Override
    protected float getDeathMaxRotation(CatfishEntity fish) {
        return 0;
    }
}
