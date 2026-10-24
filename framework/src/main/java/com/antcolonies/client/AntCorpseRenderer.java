package com.antcolonies.client;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AntCorpseEntity;
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
 * Draws a dead ant (or termite, in its species' look): a dried-out body lying on its back with the six legs curled up in the air. The same model is
 * drawn smaller in the mandibles of the worker that carries it (see {@link CarriedLoadLayer}).
 */
public class AntCorpseRenderer extends EntityRenderer<AntCorpseEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "ant_corpse"), "main");
    /** The corpse is drawn at the size of a living worker (the worker model is drawn at 0.65). */
    private static final float SIZE = 0.65F;

    private final ModelPart model;
    private final ResourceLocation texture;
    private final float size;

    /** {@code texture}: the species' look of its dead ({@link com.antcolonies.species.SpeciesBodies#corpseLook}). */
    public AntCorpseRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        this(context, texture, 1.0F);
    }

    /** {@code drawSize}: how much bigger than a small worker's body this species' dead are drawn ({@link com.antcolonies.species.SpeciesBodies#drawSize}). */
    public AntCorpseRenderer(EntityRendererProvider.Context context, ResourceLocation texture, float drawSize) {
        super(context);
        this.model = context.bakeLayer(LAYER);
        this.texture = texture;
        this.size = SIZE * drawSize;
        this.shadowRadius = 0.0F;
    }

    /** The body lies on its back, the head toward -z, like the worker model; the feet of the legs point to the sky. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, 0.0F, 0.5F, 4.0F, 3.0F, 5.0F)    // abdomen
                .texOffs(0, 0).addBox(-1.5F, 0.0F, -2.5F, 3.0F, 2.5F, 3.0F)   // thorax
                .texOffs(0, 0).addBox(-1.5F, 0.0F, -5.0F, 3.0F, 2.5F, 2.5F);  // head
        for (float z : new float[] {-2.2F, -1.0F, 0.2F}) {
            for (float side : new float[] {-1.0F, 1.0F}) {
                // A leg: up from the thorax, the tip curled in over the body.
                body.texOffs(0, 0).addBox(side * 2.0F - 0.3F, 2.5F, z, 0.6F, 2.5F, 0.6F)
                        .texOffs(0, 0).addBox(side * 1.4F - 0.3F, 4.4F, z, 0.6F, 0.6F, 0.6F);
            }
        }
        root.addOrReplaceChild("corpse", body, PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Draws the corpse model with the pose already set, in the species' look (also used for the one a worker carries). */
    public static void renderModel(ModelPart model, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation texture) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public void render(AntCorpseEntity corpse, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - corpse.getYRot()));
        pose.scale(size, size, size);
        renderModel(model, pose, buffers, light, texture);
        pose.popPose();
        super.render(corpse, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AntCorpseEntity corpse) {
        return texture;
    }
}
