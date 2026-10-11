package com.antcolonies.client;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.Brood;
import com.antcolonies.entity.AntLarvaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws an ant larva: a pale grub made of five boxes in a row, bigger the more bread it has eaten. */
public class AntLarvaRenderer extends EntityRenderer<AntLarvaEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "ant_larva"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "textures/entity/ant_larva.png");

    private final ModelPart model;

    public AntLarvaRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = context.bakeLayer(LAYER);
        this.shadowRadius = 0.0F;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("larva", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, 0.0F, -5.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(0, 0).addBox(-1.5F, 0.0F, -3.0F, 3.0F, 3.0F, 2.0F)
                .texOffs(0, 0).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 3.5F, 2.0F)
                .texOffs(0, 0).addBox(-1.5F, 0.0F, 1.0F, 3.0F, 3.0F, 2.0F)
                .texOffs(0, 0).addBox(-1.0F, 0.0F, 3.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void render(AntLarvaEntity larva, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - larva.getYRot()));
        float size = 0.45F + 0.4F * Math.min(larva.getFed(), Brood.LARVA_MEAL) / Brood.LARVA_MEAL;
        pose.scale(size, size, size);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(larva, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AntLarvaEntity larva) {
        return TEXTURE;
    }
}
