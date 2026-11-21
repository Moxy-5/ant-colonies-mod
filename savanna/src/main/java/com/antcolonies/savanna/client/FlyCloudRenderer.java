package com.antcolonies.savanna.client;

import javax.annotation.Nullable;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.FlyCloudEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Draws a fly cloud (fly clouds step 2, 2026-11-20): every fly from the fly model ({@code tools/fly_models.py game},
 * read from GeckoLib's own loaded copy so its turns and mirroring are GeckoLib's), the cheap way: no GeckoLib animation,
 * each fly posed here from its {@link FlySwarm} state. Bodies in one pass and the see-through wings in another (the
 * wings' texture is mostly clear; a pass each so the game never holds two buffers at once). The wings swap between two
 * angles every frame while flying (the buzzing blur) and rest folded back in a V when landed; the legs hang in flight,
 * stand when landed, the front pair rubbing now and then.
 *
 * <p>Cost by distance (my picks): legs only within {@link #LEGS_WITHIN} blocks; nothing past {@link #DRAWN_WITHIN} (a
 * 0.1-block fly is about 3 screen pixels long there; the plan's first guess of 12 blocks was wrong, a fly is still about
 * 6 pixels there). A fly is 23 boxes close up, 11 without legs.
 */
public class FlyCloudRenderer extends EntityRenderer<FlyCloudEntity> {
    static final double LEGS_WITHIN = 4.0, DRAWN_WITHIN = 24.0;
    /** The model is 16 pixels long as made (1 block); a fly is drawn 0.1 blocks (my pick, beside the 0.8-block bullfrog). */
    static final float SCALE = 0.1F;
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "geo/entity/fly.geo.json");
    private static final ResourceLocation BOTTLE = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/fly_bottle.png");
    private static final ResourceLocation HOUSE = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/fly_house.png");
    private static final int SOLID = 0xFFFFFFFF, WINGS_FLYING = 0xB8FFFFFF;

    public FlyCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(FlyCloudEntity cloud) {
        return BOTTLE;
    }

    @Nullable
    private static BakedGeoModel model() {
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(MODEL);
        if (model != null && model != restKept) {
            // GeckoLib records a model's rest pose only when it animates it; this model it never animates
            for (GeoBone bone : model.topLevelBones()) {
                keepRest(bone);
            }
            restKept = model;
        }
        return model;
    }

    /** The model whose bones' rest poses are kept (a new one comes with each resource reload). */
    @Nullable
    private static BakedGeoModel restKept;

    private static void keepRest(GeoBone bone) {
        if (bone.getInitialSnapshot() == null) {
            bone.saveInitialSnapshot();
        }
        for (GeoBone child : bone.getChildBones()) {
            keepRest(child);
        }
    }

    static FlySwarm swarm(FlyCloudEntity cloud) {
        if (cloud.clientSwarm instanceof FlySwarm swarm) {
            return swarm;
        }
        FlySwarm swarm = new FlySwarm(cloud.getUUID().getLeastSignificantBits());
        cloud.clientSwarm = swarm;
        return swarm;
    }

    @Override
    public void render(FlyCloudEntity cloud, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        BakedGeoModel model = model();
        if (model == null) {
            return;
        }
        Vec3 middle = cloud.getPosition(partialTick);
        FlySwarm swarm = swarm(cloud);
        swarm.update(cloud.level(), middle, (cloud.tickCount + partialTick) / 20.0, cloud.flies());
        Vec3 eye = entityRenderDispatcher.camera.getPosition();
        // bodies, then wings, each look in turn
        for (int pass = 0; pass < 4; pass++) {
            boolean wings = pass >= 2, lookB = pass % 2 == 1;
            VertexConsumer buffer = null;
            for (FlySwarm.Fly f : swarm.flies) {
                if (f.lookB != lookB || f.mode == FlySwarm.Mode.ON_TONGUE) {
                    continue;           // a fly on a bullfrog's tongue is drawn by the frog, at the pad
                }
                double far = eye.distanceToSqr(f.drawX, f.drawY, f.drawZ);
                if (far > DRAWN_WITHIN * DRAWN_WITHIN) {
                    continue;
                }
                if (buffer == null) {
                    ResourceLocation texture = lookB ? HOUSE : BOTTLE;
                    buffer = buffers.getBuffer(wings ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture));
                }
                poseStack.pushPose();
                poseStack.translate(f.drawX - middle.x, f.drawY - middle.y, f.drawZ - middle.z);
                poseStack.mulPose(Axis.YP.rotation((float) f.yaw));
                poseStack.mulPose(Axis.XP.rotation((float) f.tiltX));
                poseStack.mulPose(Axis.ZP.rotation((float) f.tiltZ));
                poseStack.scale(SCALE, SCALE, SCALE);
                poseFly(model, f, swarm);
                boolean legs = far < LEGS_WITHIN * LEGS_WITHIN;
                int colour = wings && f.flying() ? WINGS_FLYING : SOLID;
                for (GeoBone bone : model.topLevelBones()) {
                    drawBone(poseStack, bone, buffer, wings, legs, colour, packedLight);
                }
                poseStack.popPose();
            }
        }
        super.render(cloud, yaw, partialTick, poseStack, buffers, packedLight);
    }

    /**
     * One fly drawn by someone else (a bullfrog draws the fly on its tongue's pad, at the pad's place this frame, so it
     * never lags behind the tongue): its body and its wings, posed as in the cloud, the poseStack at {@code origin}.
     */
    static void drawOne(PoseStack poseStack, MultiBufferSource buffers, FlySwarm swarm, FlySwarm.Fly f, Vec3 origin, Vec3 eye, int light) {
        BakedGeoModel model = model();
        if (model == null) {
            return;
        }
        double far = eye.distanceToSqr(f.drawX, f.drawY, f.drawZ);
        if (far > DRAWN_WITHIN * DRAWN_WITHIN) {
            return;
        }
        ResourceLocation texture = f.lookB ? HOUSE : BOTTLE;
        for (int pass = 0; pass < 2; pass++) {
            boolean wings = pass == 1;
            VertexConsumer buffer = buffers.getBuffer(wings ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture));
            poseStack.pushPose();
            poseStack.translate(f.drawX - origin.x, f.drawY - origin.y, f.drawZ - origin.z);
            poseStack.mulPose(Axis.YP.rotation((float) f.yaw));
            poseStack.scale(SCALE, SCALE, SCALE);
            poseFly(model, f, swarm);
            for (GeoBone bone : model.topLevelBones()) {
                drawBone(poseStack, bone, buffer, wings, far < LEGS_WITHIN * LEGS_WITHIN, wings ? WINGS_FLYING : SOLID, light);
            }
            poseStack.popPose();
        }
    }

    /**
     * Turns the wings and legs for this fly (the preview's poses, written in the model maker's degrees and turned into
     * GeckoLib's: it flips the X and Y turns as it loads a model).
     */
    private static void poseFly(BakedGeoModel model, FlySwarm.Fly f, FlySwarm swarm) {
        boolean up = (swarm.frame + f.flick) % 2 == 0;
        for (String side : new String[] {"left", "right"}) {
            int sg = side.equals("left") ? 1 : -1;
            model.getBone("wing_" + side).ifPresent(wing -> {
                if (f.flying()) {
                    turn(wing, 0, sg * (up ? 74 : 86), up ? -sg * 48 : sg * 22);
                } else {
                    rest(wing);
                }
            });
            for (int i = 1; i <= 3; i++) {
                int leg = i;
                model.getBone("leg_" + side + "_" + i).ifPresent(bone -> {
                    float restY = -bone.getInitialSnapshot().getRotY() * Mth.RAD_TO_DEG;      // back into the maker's degrees
                    if (f.flying()) {
                        turn(bone, 0, restY * 0.4F - sg * 18, sg * 42);
                    } else if (leg == 1 && f.rub > 0 && f.rubAt < 0) {
                        turn(bone, 0, (float) (sg * (80 + 12 * Math.sin(26 * swarm.time))), -sg * 34);
                    } else {
                        rest(bone);
                    }
                });
            }
        }
    }

    /** A bone's turn in the model maker's degrees (Blockbench's), as GeckoLib holds it. */
    private static void turn(GeoBone bone, float x, float y, float z) {
        bone.updateRotation(-x * Mth.DEG_TO_RAD, -y * Mth.DEG_TO_RAD, z * Mth.DEG_TO_RAD);
    }

    private static void rest(GeoBone bone) {
        var rest = bone.getInitialSnapshot();
        bone.updateRotation(rest.getRotX(), rest.getRotY(), rest.getRotZ());
    }

    /** A bone and its children, as GeckoLib draws them: only the wings' boxes in the wing pass, the rest in the other. */
    private static void drawBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, boolean wingPass, boolean legs, int colour, int light) {
        String name = bone.getName();
        if (!legs && name.startsWith("leg")) {
            return;
        }
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        if (name.startsWith("wing") == wingPass) {
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normals = poseStack.last().normal();
            for (GeoCube cube : bone.getCubes()) {
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) {
                        continue;
                    }
                    Vector3f n = normals.transform(new Vector3f(quad.normal()));
                    for (GeoVertex v : quad.vertices()) {
                        Vector3f p = pose.transformPosition(new Vector3f(v.position()));
                        buffer.addVertex(p.x(), p.y(), p.z(), colour, v.texU(), v.texV(), OverlayTexture.NO_OVERLAY, light, n.x(), n.y(), n.z());
                    }
                }
            }
        }
        for (GeoBone child : bone.getChildBones()) {
            drawBone(poseStack, child, buffer, wingPass, legs, colour, light);
        }
        poseStack.popPose();
    }
}
