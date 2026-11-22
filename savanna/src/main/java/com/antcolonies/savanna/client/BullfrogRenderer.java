package com.antcolonies.savanna.client;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BullfrogEntity;
import com.antcolonies.savanna.animal.FlyCloudEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animation.AnimationState;
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
 *
 * <p>Aimed at one fly (the polish's step 3, 2026-11-20): the flies fly only on the players' computers, so each one's
 * picks the fly: while the frog aims ({@link BullfrogEntity#AIM}) its eyes are on the fly of the cloud nearest its
 * mouth and its head tips up toward it; the flick's tip follows that fly as it shoots out. A catch (the server's call)
 * sticks the fly to the pad, drawn here at the pad's place each frame (never lagging the tongue) until it is swallowed;
 * a miss and the fly darts away just before the pad arrives, and the pad slaps the air it left.
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
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "bullfrog")) {
            @Override
            public void setCustomAnimations(BullfrogEntity frog, long instanceId, AnimationState<BullfrogEntity> state) {
                super.setCustomAnimations(frog, instanceId, state);
                headOnFly(frog, getAnimationProcessor().getBone("head"));
            }
        });
        addRenderLayer(new com.antcolonies.client.MudCoatLayer<>(this));      // mud on its body (savanna mud step 4)
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

    /** The miss: the fly darts off this many ticks into the flick (the pad arrives at 3: 0.15 s). */
    static final float DODGE_AT = 1.8F;
    /** How far the frog looks for a fly to fix on, against its tongue's reach (a little past it: the fly may come in). */
    static final double PICK_REACH = 1.3;
    /** The head's tip up toward the fly: this share of the angle up to it, at most this (degrees), eased at this rate. */
    static final float HEAD_SHARE = 0.6F, HEAD_MOST = 22.0F, HEAD_RATE = 10.0F;

    /** The fly its eyes are on now, if any (kept on the frog, picked again at each new aim). */
    @Nullable
    private static FlySwarm.Fly fly(BullfrogEntity frog) {
        return frog.clientFly instanceof FlySwarm.Fly f ? f : null;
    }

    private static void letGo(BullfrogEntity frog) {
        FlySwarm.Fly f = fly(frog);
        if (f != null) {
            FlySwarm.release(f, frog);
        }
        frog.clientFly = null;
        frog.clientContact = null;
        frog.clientCaught = false;
        frog.clientFlyFor = -1;
    }

    /** Keeps its eyes on a fly while it aims and flicks: a new fly for each new aim, the same one for the flick after it. */
    private static void watch(BullfrogEntity frog) {
        byte a = frog.action();
        FlyCloudEntity cloud = a == BullfrogEntity.AIM || a == BullfrogEntity.TONGUE ? frog.tongueCloud() : null;
        if (cloud == null) {
            if (frog.clientFly != null || frog.clientFlyFor >= 0) {
                letGo(frog);
            }
            return;
        }
        FlySwarm swarm = FlyCloudRenderer.swarm(cloud);
        int id = frog.actionId();
        if (frog.clientFlyFor != id) {
            if (a == BullfrogEntity.AIM) {
                letGo(frog);            // a new aim, a new fly
            }
            frog.clientFlyFor = id;
            frog.clientContact = null;
            frog.clientCaught = false;
        }
        FlySwarm.Fly f = fly(frog);
        if (f != null && !swarm.has(f)) {
            f = null;
            frog.clientFly = null;
        }
        if (f == null && frog.clientContact == null) {
            frog.clientFly = swarm.pick(frog, frog.mouth(), PICK_REACH * 2.0 * frog.getScale() + 0.3);
        }
    }

    /** The head tipped up toward the fly its eyes are on (eased; only over clips that set the head themselves). */
    private static void headOnFly(BullfrogEntity frog, @Nullable GeoBone head) {
        long now = System.nanoTime();
        float dt = frog.clientHeadTime == 0 ? 0 : Math.min(0.1F, (now - frog.clientHeadTime) / 1.0E9F);
        frog.clientHeadTime = now;
        byte a = frog.action();
        FlySwarm.Fly f = fly(frog);
        float want = 0;
        if (f != null && (a == BullfrogEntity.AIM || a == BullfrogEntity.TONGUE) && !frog.clientCaught) {
            Vec3 m = frog.mouth();
            double dx = f.drawX - m.x, dy = f.drawY - m.y, dz = f.drawZ - m.z;
            want = Mth.clamp((float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))) * HEAD_SHARE, 0, HEAD_MOST);
        }
        frog.clientHeadPitch += (want - frog.clientHeadPitch) * (1 - (float) Math.exp(-HEAD_RATE * dt));
        boolean setsHead = a == BullfrogEntity.AIM || a == BullfrogEntity.TONGUE || a == BullfrogEntity.NONE && (frog.onGround() || frog.isInWater());
        if (head != null && setsHead && frog.clientHeadPitch > 0.05F) {
            head.setRotX(head.getRotX() + frog.clientHeadPitch * Mth.DEG_TO_RAD);     // up (GeckoLib turns X the other way)
        }
    }

    @Override
    public void preRender(PoseStack poseStack, BullfrogEntity frog, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
            boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, frog, model, buffers, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (!isReRender) {
            watch(frog);
        }
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
        Vec3 target = aimAt(frog, age).subtract(drawn);
        Vec3 tip = mouth.add(target.subtract(mouth).scale(out));
        boolean back = age / 20.0F > 0.233F;
        drawRope(poseStack, buffers, mouth, tip, (float) s, back ? (1 - out) : 0, age, packedLight);
        FlySwarm.Fly f = fly(frog);
        FlyCloudEntity cloud = frog.tongueCloud();
        FlySwarm swarm = cloud == null ? null : FlyCloudRenderer.swarm(cloud);
        if (frog.clientCaught && f != null && swarm != null && swarm.has(f)) {
            swarm.hold(f, drawn.add(tip));                  // stuck to the pad, back into the mouth
            FlyCloudRenderer.drawOne(poseStack, buffers, swarm, f, drawn, entityRenderDispatcher.camera.getPosition(), packedLight);
        }
    }

    /**
     * Where the tongue goes this frame (world): the fly its eyes are on while the tongue shoots out; once it has met
     * the fly (a catch: the fly sticks to the pad) or the fly has dodged (a miss), that spot; with no fly, the server's
     * aim at the cloud's edge.
     */
    private static Vec3 aimAt(BullfrogEntity frog, float age) {
        FlySwarm.Fly f = fly(frog);
        FlyCloudEntity cloud = frog.tongueCloud();
        if (frog.clientContact != null) {
            return frog.clientContact;
        }
        if (f == null || cloud == null) {
            return frog.tongueAt();
        }
        Vec3 at = new Vec3(f.drawX, f.drawY, f.drawZ);
        FlySwarm swarm = FlyCloudRenderer.swarm(cloud);
        if (!frog.tongueHits() && age >= DODGE_AT) {
            frog.clientContact = at;                        // it slips away; the pad slaps where it was
            swarm.dodge(f, frog.position());
            FlySwarm.release(f, frog);
            frog.clientFly = null;
        } else if (frog.tongueHits() && reach(age) >= 0.999F) {
            frog.clientContact = at;
            frog.clientCaught = true;
            swarm.hold(f, at);
        }
        return at;
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
