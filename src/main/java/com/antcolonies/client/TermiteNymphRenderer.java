package com.antcolonies.client;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
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

/**
 * Draws a termite nymph: a small, very pale termite (head, thorax, soft grey abdomen, six stubby legs and two
 * antennae) lying still, bigger the more it has been fed. Real nymphs look like little pale workers. One growing into a
 * soldier becomes a "presoldier" once it has eaten half its meal, as in real termites: a big pale head with soft pale
 * jaws (they harden and darken when it grows up); one growing into a major grows bigger.
 * Texture: four colour bands made by tools/termite_models.py (head, body, abdomen, legs).
 */
public class TermiteNymphRenderer extends EntityRenderer<AntLarvaEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "termite_nymph"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "textures/entity/termite_nymph.png");

    private final ModelPart model;

    public TermiteNymphRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = context.bakeLayer(LAYER);
        this.shadowRadius = 0.0F;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder body = CubeListBuilder.create()
                // head (band 1), antennae (band 4)
                .texOffs(0, 0).addBox(-0.9F, 0.5F, -3.6F, 1.8F, 1.4F, 1.4F)
                .texOffs(0, 24).addBox(0.4F, 1.3F, -4.7F, 0.3F, 0.3F, 1.2F)
                .texOffs(0, 24).addBox(-0.7F, 1.3F, -4.7F, 0.3F, 0.3F, 1.2F)
                // thorax (band 2)
                .texOffs(0, 8).addBox(-0.7F, 0.5F, -2.2F, 1.4F, 1.1F, 1.6F)
                // abdomen (band 3)
                .texOffs(0, 16).addBox(-1.0F, 0.4F, -0.6F, 2.0F, 1.4F, 2.8F)
                .texOffs(0, 16).addBox(-0.75F, 0.5F, 2.2F, 1.5F, 1.1F, 0.8F);
        // six stubby legs (band 4)
        for (float z : new float[] {-2.0F, -1.4F, -0.8F}) {
            body.texOffs(0, 24).addBox(0.6F, 0.0F, z, 0.9F, 0.6F, 0.25F);
            body.texOffs(0, 24).addBox(-1.5F, 0.0F, z, 0.9F, 0.6F, 0.25F);
        }
        root.addOrReplaceChild("nymph", body, PartPose.ZERO);
        // The presoldier's big head (band 1) and soft jaws (band 4), drawn over the small head.
        root.addOrReplaceChild("soldier_head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.15F, 0.45F, -4.3F, 2.3F, 1.7F, 2.1F)
                .texOffs(0, 24).addBox(0.2F, 0.75F, -5.4F, 0.35F, 0.3F, 1.1F)
                .texOffs(0, 24).addBox(-0.55F, 0.8F, -5.4F, 0.35F, 0.3F, 1.1F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(AntLarvaEntity nymph, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - nymph.getYRot()));
        float grown = (float) Math.min(nymph.getFed(), nymph.getMeal()) / nymph.getMeal();
        TermiteSoldierEntity.Rank rank = TermiteSoldierEntity.Rank.byId(nymph.getGrowsIntoNumber()); // (the soldiers' caste numbers, 1 and 2)
        float size = 0.55F + (rank == TermiteSoldierEntity.Rank.MAJOR ? 0.6F : 0.35F) * grown;
        pose.scale(size, size, size);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.getChild("nymph").render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        if (rank != null && grown >= 0.5F) {
            model.getChild("soldier_head").render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        super.render(nymph, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AntLarvaEntity nymph) {
        return TEXTURE;
    }
}
