package com.antcolonies.savanna.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BullSharkEntity;
import com.antcolonies.savanna.animal.CatfishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import com.antcolonies.client.BoneMeshRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws the bull shark (M9 river life, 2026-11-22): Neon's approved v14 model ({@code tools/bull_shark_models.py game},
 * 5.5 blocks long as made, drawn at each shark's own size through its scale), look A ({@code bull_shark.png}) or, from 6.0
 * blocks long, the scarred old bull (look B, {@code bull_shark_scarred.png}). The body tips toward where it swims; the
 * death clip rolls it belly up itself, so Minecraft's tip-over is off.
 *
 * <p>A catfish held in its jaws is drawn by the catfish's own renderer (the very same model, at the caught one's size,
 * wriggling with its out-of-water clip) at the model's {@code prey} bone, which rides the head through the thrash: lying
 * across the jaws, its middle between the teeth. It is drawn after the shark, from the bone's place noted while the
 * shark was drawn, so the two models' buffers never mix (as {@code CatfishRenderer} draws its tilapia).
 */
public class BullSharkRenderer extends BoneMeshRenderer<BullSharkEntity> {
    /** The catfish model's body middle over its feet (blocks at its size 1; its mouth is 0.17 up, CatfishEntity.mouth). */
    private static final float CATFISH_MID = 0.17F;

    private final Matrix4f preyPose = new Matrix4f();
    private final Matrix3f preyNormal = new Matrix3f();
    private boolean preySeen;

    public BullSharkRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = 0.9F;
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void renderForBone(PoseStack poseStack, BullSharkEntity shark, GeoBone bone, RenderType renderType,
                    MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                if (bone.getName().equals("prey") && shark.isHolding()) {
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

    /** The model, its clips, and its look: the scarred old bull from 6.0 blocks long. */
    static class Model extends DefaultedEntityGeoModel<BullSharkEntity> {
        private static final ResourceLocation SCARRED = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/bull_shark_scarred.png");

        Model() {
            super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "bull_shark"));
        }

        @Override
        public ResourceLocation getTextureResource(BullSharkEntity shark) {
            return shark.isScarred() ? SCARRED : super.getTextureResource(shark);
        }
    }

    @Override
    public void render(BullSharkEntity shark, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        preySeen = false;
        super.render(shark, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        CatfishEntity prey = preySeen ? shark.heldDrawn() : null;
        if (prey == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.last().pose().set(preyPose);
        poseStack.last().normal().set(preyNormal);
        float undo = 1.0F / shark.getScale();          // out of the shark's size, into blocks
        poseStack.scale(undo, undo, undo);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));                  // across the jaws
        poseStack.translate(0, -CATFISH_MID * prey.getScale(), 0);        // its middle between the teeth
        entityRenderDispatcher.getRenderer(prey).render(prey, 0, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    protected void applyRotations(BullSharkEntity shark, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(shark, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float pitch = shark.swimPitch(partialTick);
        if (pitch != 0) {
            // tipped about its spine (16 model pixels up), not its belly; GeckoLib has already scaled to its size here
            poseStack.translate(0, 1.0, 0);
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.translate(0, -1.0, 0);
        }
    }

    @Override
    protected float getDeathMaxRotation(BullSharkEntity shark) {
        return 0;
    }

    @Override
    protected float getShadowRadius(BullSharkEntity shark) {
        return super.getShadowRadius(shark) * shark.getScale();
    }
}
