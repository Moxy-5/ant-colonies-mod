package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/** Draws the soil pellet (a tiny block) in front of the mandibles while the ant is carrying one. */
public class PelletLayer<T extends AbstractAntEntity> extends BlockAndItemGeoLayer<T> {
    /** The bone the pellet follows (so it moves with the head). Must exist in the model. */
    private static final String HOLD_BONE = "head";
    /** Pellet size as a fraction of a full block. */
    private static final float SIZE = 0.12F;
    /** A carried seed is drawn this big (as a fraction of a full item). */
    private static final float SEED_SIZE = 0.3F;
    // Where the pellet sits, in model pixels (16 = one block): just in front of the mandible tips.
    private static final float X = 0.0F;
    private static final float Y = 2.3F;
    private static final float Z = -6.8F;

    public PelletLayer(GeoRenderer<T> renderer) {
        super(renderer,
                (bone, ant) -> HOLD_BONE.equals(bone.getName()) && ant.isCarryingSeed() ? ant.getCarriedSeed() : null,
                (bone, ant) -> HOLD_BONE.equals(bone.getName()) ? ant.getCarriedBlock() : null);
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T ant,
            MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(X / 16.0F, Y / 16.0F, Z / 16.0F);
        poseStack.scale(SEED_SIZE, SEED_SIZE, SEED_SIZE);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                poseStack, bufferSource, ant.level(), ant.getId());
        poseStack.popPose();
    }

    @Override
    protected void renderBlockForBone(PoseStack poseStack, GeoBone bone, BlockState state, T ant,
            MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(X / 16.0F, Y / 16.0F, Z / 16.0F);
        poseStack.scale(SIZE, SIZE, SIZE);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
