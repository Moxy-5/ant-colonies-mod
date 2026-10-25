package com.antcolonies.client;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.LostLegEntity;
import com.antcolonies.species.SpeciesBodies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a piece of leg bitten off in a fight (M8 step 2d): the shin and the foot bent at the ankle, lying on the ground,
 * in its species' look of the dead ({@link SpeciesBodies#corpseLook}), as long as the insect's was.
 */
public class LostLegRenderer extends EntityRenderer<LostLegEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "lost_leg"), "main");
    private static final ResourceLocation NO_LOOK =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/black_concrete.png");
    /** The model is 16 units (one block) long; it is drawn at the piece's length. */
    private static final float MODEL_LENGTH = 16.0F;

    private final ModelPart model;

    public LostLegRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = context.bakeLayer(LAYER);
        this.shadowRadius = 0.0F;
    }

    /** The shin along +z (12 units), the foot (4 units) bent 35 degrees off its end, both lying on the ground. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition shin = root.addOrReplaceChild("shin",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 12.0F), PartPose.ZERO);
        shin.addOrReplaceChild("foot",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.35F, 0.0F, 0.0F, 0.7F, 0.7F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 12.0F, 0.0F, (float) Math.toRadians(35.0), 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(LostLegEntity leg, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - leg.getYRot()));
        // The model's units are sixteenths of a block, so it is one block long: drawn at the leg's length, centred on
        // where it lies.
        float size = leg.length() * 16.0F / MODEL_LENGTH;
        pose.scale(size, size, size);
        pose.translate(0.0F, 0.0F, -0.5F);
        if (leg.isLeft()) {
            pose.scale(-1.0F, 1.0F, 1.0F); // a left leg's foot bends the other way
        }
        AntCorpseRenderer.renderModel(model, pose, buffers, light, getTextureLocation(leg));
        pose.popPose();
        super.render(leg, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LostLegEntity leg) {
        try {
            return SpeciesBodies.of(leg.species()).corpseLook();
        } catch (IllegalStateException | net.minecraft.ResourceLocationException e) {
            return NO_LOOK;
        }
    }
}
