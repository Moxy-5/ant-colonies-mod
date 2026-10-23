package com.antcolonies.client;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AntEggEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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

/** Draws an ant egg: a tiny pale grain, a box with smaller boxes on both ends. */
public class AntEggRenderer extends EntityRenderer<AntEggEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "ant_egg"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "textures/entity/ant_egg.png");

    private final ModelPart model;

    public AntEggRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = context.bakeLayer(LAYER);
        this.shadowRadius = 0.0F;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("egg", CubeListBuilder.create()
                .texOffs(0, 8).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F)
                .texOffs(0, 0).addBox(-1.5F, 1.0F, -1.5F, 3.0F, 4.0F, 3.0F)
                .texOffs(8, 8).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void render(AntEggEntity egg, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.scale(0.7F, 0.7F, 0.7F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(egg, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AntEggEntity egg) {
        return TEXTURE;
    }
}
