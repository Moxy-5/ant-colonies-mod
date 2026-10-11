package com.antcolonies.client;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Draws what the ant holds in its mandibles: the soil pellet (a tiny block), a seed, an egg (the same grain the
 * nursery shows, lying across the mandibles) or a dead nestmate (the same body the refuse chamber shows, smaller).
 */
public class PelletLayer<T extends AbstractAntEntity> extends BlockAndItemGeoLayer<T> {
    /** The bone the pellet follows (so it moves with the head). Must exist in the model. */
    private static final String HOLD_BONE = "head";
    /** Pellet size as a fraction of a full block. */
    private static final float SIZE = 0.12F;
    /** A carried seed is drawn this big (as a fraction of a full item). */
    private static final float SEED_SIZE = 0.3F;
    /** A carried egg is drawn at this fraction of the size it has in the nursery. */
    private static final float EGG_SIZE = 0.6F;
    /** Stands in for the egg: the layer asks for an item per bone, so the egg is passed as this one and drawn differently. */
    private static final ItemStack EGG_MARKER = new ItemStack(Items.EGG);
    /** The same for a dead ant. */
    private static final ItemStack CORPSE_MARKER = new ItemStack(Items.BONE);
    /** A carried dead ant is drawn at this fraction of the size it has on the floor. */
    private static final float CORPSE_SIZE = 0.6F;
    private static final ResourceLocation EGG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "textures/entity/ant_egg.png");
    // Where the pellet sits, in model pixels (16 = one block): just in front of the mandible tips.
    private static final float X = 0.0F;
    private static final float Y = 2.3F;
    private static final float Z = -6.8F;

    private final ModelPart eggModel;
    private final ModelPart corpseModel;

    public PelletLayer(GeoRenderer<T> renderer, ModelPart eggModel, ModelPart corpseModel) {
        super(renderer,
                (bone, ant) -> !HOLD_BONE.equals(bone.getName()) ? null
                        : ant.isCarryingCorpse() ? CORPSE_MARKER : ant.isCarryingEgg() ? EGG_MARKER : !ant.getCarriedSeed().isEmpty() ? ant.getCarriedSeed() : null,
                (bone, ant) -> HOLD_BONE.equals(bone.getName()) ? ant.getCarriedBlock() : null);
        this.eggModel = eggModel;
        this.corpseModel = corpseModel;
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T ant,
            MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(X / 16.0F, Y / 16.0F, Z / 16.0F);
        if (stack == CORPSE_MARKER) {
            // The dead ant hangs from the mandibles by its head and trails out in front of the carrier, legs up.
            poseStack.translate(0.0F, 0.0F, -3.0F / 16.0F);
            poseStack.scale(CORPSE_SIZE, CORPSE_SIZE, CORPSE_SIZE);
            AntCorpseRenderer.renderModel(corpseModel, poseStack, bufferSource, packedLight);
        } else if (stack == EGG_MARKER) {
            // The egg model stands on its foot point and is 6 pixels tall: lay it along the ant and centre it.
            poseStack.scale(EGG_SIZE, EGG_SIZE, EGG_SIZE);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.translate(0.0F, -3.0F / 16.0F, 0.0F);
            eggModel.render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(EGG_TEXTURE)), packedLight,
                    OverlayTexture.NO_OVERLAY);
        } else {
            poseStack.scale(SEED_SIZE, SEED_SIZE, SEED_SIZE);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                    poseStack, bufferSource, ant.level(), ant.getId());
        }
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
