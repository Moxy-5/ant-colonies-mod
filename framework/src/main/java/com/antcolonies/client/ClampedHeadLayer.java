package com.antcolonies.client;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.body.BodyPlans;
import com.antcolonies.entity.body.LostLegs;
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
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws the head of a dead foe locked on to a leg (M8 step 2c, {@link com.antcolonies.entity.Clamp}: a termite
 * soldier killed while clamped on): a small armoured head with its two jaws shut round the leg, at the knee of the leg
 * it holds (most of the way down the shin), moving with the leg, in the look and size its species gives ({@code fight.clamp}).
 */
public class ClampedHeadLayer<T extends AbstractAntEntity> extends GeoRenderLayer<T> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "clamped_head"), "main");
    /** The model is 16 units (one block) long, jaws and all; it is drawn at the head's size. */
    private static final float MODEL_LENGTH = 16.0F;
    /** How far from the knee to the foot the head holds the shin (0 the knee, 1 the foot; my pick). */
    private static final float DOWN_THE_SHIN = 0.7F;

    private final ModelPart model;

    public ClampedHeadLayer(GeoRenderer<T> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.model = context.bakeLayer(LAYER);
    }

    /**
     * The head capsule (10 wide, 7 high, 10 long) behind, along +z its two jaws (7 long) curving in to meet round the
     * leg at the origin; texture (64 x 32, tools/clamped_head_art.py): the head's faces at the top left, the jaws below.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -3.5F, -16.0F, 10.0F, 7.0F, 10.0F), PartPose.ZERO);
        head.addOrReplaceChild("jaw_l", CubeListBuilder.create().texOffs(0, 17).addBox(-0.75F, -0.75F, 0.0F, 1.5F, 1.5F, 7.0F),
                PartPose.offsetAndRotation(2.5F, 0.5F, -6.5F, 0.0F, (float) Math.toRadians(-22.0), 0.0F));
        head.addOrReplaceChild("jaw_r", CubeListBuilder.create().texOffs(0, 17).addBox(-0.75F, -0.75F, 0.0F, 1.5F, 1.5F, 7.0F),
                PartPose.offsetAndRotation(-2.5F, 0.5F, -6.5F, 0.0F, (float) Math.toRadians(22.0), 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void renderForBone(PoseStack poseStack, T ant, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        int leg = ant.clampedHeadLeg();
        if (leg < 0) {
            return;
        }
        BodyPlans.Plan plan = BodyPlans.ofGeo(getGeoModel().getModelResource(ant));
        String role = LostLegs.lowerBone(leg);
        if (!bone.getName().equals(plan != null ? plan.bone(role) : role)) {
            return;
        }
        ResourceLocation look = ResourceLocation.tryParse(ant.clampedHeadLook());
        if (look == null || ant.clampedHeadLook().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        // GeckoLib has already put the pose on this bone (its parents' and its own move, turn and scale round its pivot)
        // before it calls a layer for it; moving and turning it once more turned the leg a second time round the
        // model's middle and flung the head up over the back (docs/BUGS.md 78).
        // Down the shin, most of the way from the knee to the foot (the shin's pivot and its child's; the bones are drawn
        // in the model's own space, in pixels): a soldier bites the lower leg from the ground, and long-legged ants hold
        // their knees up over the back, where a head looked to float (docs/BUGS.md 78). Then the head at its size in
        // blocks (the pose is already scaled to the size the insect is drawn at), jaws round the leg, the head outward
        // (a left leg's head to the left).
        float x = bone.getPivotX();
        float y = bone.getPivotY();
        float z = bone.getPivotZ();
        if (!bone.getChildBones().isEmpty()) {
            GeoBone foot = bone.getChildBones().get(0);
            x += (foot.getPivotX() - x) * DOWN_THE_SHIN;
            y += (foot.getPivotY() - y) * DOWN_THE_SHIN;
            z += (foot.getPivotZ() - z) * DOWN_THE_SHIN;
        }
        poseStack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        float drawn = ant.getDrawScale() > 0.0F ? ant.getDrawScale() : 1.0F;
        float size = ant.clampedHeadSize() / drawn * 16.0F / MODEL_LENGTH;
        poseStack.mulPose(Axis.YP.rotationDegrees(leg % 2 == 0 ? 90.0F : -90.0F));
        poseStack.scale(size, size, size);
        AntCorpseRenderer.renderModel(model, poseStack, bufferSource, packedLight, look);
        poseStack.popPose();
    }
}
