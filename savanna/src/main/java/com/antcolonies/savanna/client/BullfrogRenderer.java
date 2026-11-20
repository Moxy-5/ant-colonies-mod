package com.antcolonies.savanna.client;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BullfrogEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the African bullfrog (prey plan step 3, 2026-11-20): its model and clips ({@code tools/bullfrog_models.py
 * game}), a male 0.8 blocks long (the model is made 40 pixels, 2.5 blocks), a female smaller through SCALE.
 *
 * <p>The long tongue at flies (the user's 2 blocks) is drawn as a rope, the way Minecraft draws a lead: stretching the
 * model's tongue pieces six times over would smear their pixels into streaks. While it flicks the model's own tongue is
 * hidden, and a pink ribbon (two crossed strips, so it is seen from any side) runs from the mouth to the flies, thinner
 * as it stretches, a broad sticky pad on its end, wobbling like elastic on the way back; the clip keeps the open mouth,
 * the head's tilt and the swallow.
 */
public class BullfrogRenderer extends GeoEntityRenderer<BullfrogEntity> {
    static final float SCALE = 0.8F / 2.5F;
    /** The rope's colours: the tongue's pink, its shaded side, the pad's deeper rose (RGB). */
    private static final int PINK = 0xE07C88, PINK_SHADE = 0xB65866, PAD = 0xC4475E;
    /**
     * Where the rope leaves the mouth, in the mouth floor's frame (model pixels from its hinge; the floor moves with the
     * jaw and head but, unlike the model's tongue, is never stretched by the clip): the tongue's front end at the tip of
     * the lower jaw, where a real frog's tongue is fixed (it flips out from the front).
     */
    private static final float ROOT_AHEAD = -11.5F / 16, ROOT_UP = 0.9F / 16;
    /** The mouth floor as last drawn (GeckoLib follows its place when asked). */
    @Nullable
    private GeoBone mouthFloor;

    public BullfrogRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "bullfrog")));
        withScale(SCALE);
        shadowRadius = 0.32F;
    }

    @Override
    protected float getDeathMaxRotation(BullfrogEntity frog) {
        return 0;       // the death clip rolls it onto its back itself
    }

    @Override
    protected float getShadowRadius(BullfrogEntity frog) {
        return super.getShadowRadius(frog) * frog.getScale();
    }

    /**
     * How far out the tongue is (0 in the mouth, 1 at the flies) this many ticks into the flick: out over the clip's
     * 0.07 to 0.15 s, held to 0.23 s, back in by 0.35 s (the tongue clip's own timing).
     */
    static float reach(float ticks) {
        float t = ticks / 20.0F;
        if (t < 0.067F) {
            return 0;
        }
        if (t < 0.15F) {
            float k = (t - 0.067F) / 0.083F;
            return k * k * (3 - 2 * k);
        }
        if (t < 0.233F) {
            return 1;
        }
        if (t < 0.35F) {
            float k = (t - 0.233F) / 0.117F;
            return 1 - k * k * (3 - 2 * k);
        }
        return 0;
    }

    @Override
    public void preRender(PoseStack poseStack, BullfrogEntity frog, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
            boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, frog, model, buffers, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        boolean rope = frog.action() == BullfrogEntity.TONGUE;
        model.getBone("tongue_1").ifPresent(bone -> bone.setHidden(rope));
        mouthFloor = model.getBone("mouth_floor").orElse(null);
        if (mouthFloor != null) {
            mouthFloor.setTrackingMatrices(true);
        }
    }

    @Override
    public void render(BullfrogEntity frog, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(frog, yaw, partialTick, poseStack, buffers, packedLight);
        if (frog.action() != BullfrogEntity.TONGUE) {
            return;
        }
        float age = frog.clientActionAge(partialTick);
        float out = reach(age);
        if (out <= 0.001F) {
            return;
        }
        double s = frog.getScale();
        Vec3 mouth = mouth(frog, partialTick, s);
        Vec3 drawn = frog.getPosition(partialTick);
        Vec3 target = frog.tongueAt().subtract(drawn);
        Vec3 tip = mouth.add(target.subtract(mouth).scale(out));
        boolean back = age / 20.0F > 0.233F;
        drawRope(poseStack, buffers, mouth, tip, (float) s, back ? (1 - out) : 0, age, packedLight);
    }

    /**
     * The rope's root against the frog's drawn place: the mouth floor as GeckoLib just drew it (so it follows the
     * clip's open jaw and tilted head), else a fixed spot ahead of the body.
     */
    private Vec3 mouth(BullfrogEntity frog, float partialTick, double s) {
        if (mouthFloor != null) {
            Matrix4f m = new Matrix4f(mouthFloor.getLocalSpaceMatrix());
            // GeckoLib 4.9 builds this matrix by adding a plain translation matrix (identity included) to the bone's,
            // so its turn-and-size part carries an extra identity: take it back out
            m.m00(m.m00() - 1).m11(m.m11() - 1).m22(m.m22() - 1);
            Vector3f p = m.transformPosition(new Vector3f(0, ROOT_UP, ROOT_AHEAD));
            if (p.lengthSquared() > 1.0E-6F) {
                return new Vec3(p.x(), p.y(), p.z());
            }
        }
        float bodyYaw = Mth.rotLerp(partialTick, frog.yBodyRotO, frog.yBodyRot) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(bodyYaw) * 0.38 * s, 0.2 * s, Mth.cos(bodyYaw) * 0.38 * s);
    }

    /** The ribbon from the mouth to the tip: two crossed strips of 16 pieces, the pad its last two pieces widened. */
    private static void drawRope(PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 to, float size, float wobble, float age, int light) {
        VertexConsumer strip = buffers.getBuffer(RenderType.leash());
        Matrix4f pose = poseStack.last().pose();
        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 dir = along.scale(1 / length);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        int pieces = 16;
        // thinner as it stretches (my picks: 0.05 blocks wide at rest for a male, at least a third of that)
        float width = size * Mth.clamp(0.05F / (float) Math.max(1.0, length * 1.6), 0.018F, 0.05F);
        for (int pass = 0; pass < 2; pass++) {
            Vec3 across = pass == 0 ? side : up;
            for (int k = 0; k <= pieces; k++) {
                int i = pass == 0 ? k : pieces - k;     // the second strip runs back, as Minecraft's lead does
                float t = i / (float) pieces;
                double wave = wobble * 0.08 * size * Mth.sin((float) Math.PI * t) * Mth.sin(age * 2.2F + t * 9.0F);
                Vec3 p = from.add(along.scale(t)).add(up.scale(wave));
                boolean pad = i >= pieces - 2;      // a rounded blob two pieces long (a flat plate read as a hammer)
                float w = pad ? width * (i == pieces ? 1.4F : 1.8F) : width;
                int rgb = pad ? PAD : pass == 0 ? PINK : PINK_SHADE;
                float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
                strip.addVertex(pose, (float) (p.x + across.x * w), (float) (p.y + across.y * w), (float) (p.z + across.z * w))
                        .setColor(r, g, b, 1.0F).setLight(light);
                strip.addVertex(pose, (float) (p.x - across.x * w), (float) (p.y - across.y * w), (float) (p.z - across.z * w))
                        .setColor(r, g, b, 1.0F).setLight(light);
            }
        }
    }
}
