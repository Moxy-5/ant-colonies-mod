package com.antcolonies.savanna.client;

import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CrocodileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The crocodile's model with its body lying on what is under it, on top of its clips (the crocodile's life step 1,
 * 2026-11-15, {@link GroundBend}): over a bank's edge the head tips down to the water while the tail still lies on the
 * bank; coming out the front rises onto the bank with the back half afloat.
 */
public class CrocodileModel extends DefaultedEntityGeoModel<CrocodileEntity> {
    /** The bend only within this many blocks of the camera (farther it is too small to see it; my pick). */
    private static final double NEAR = 64.0;
    /** How quickly a leg pair folds or stands (seconds; my pick). */
    private static final double LEG_EASE = 0.25;
    /** How far under the body's own height a pair's feet still reach the ground (blocks, at size 1), and how deep water they still wade in (my picks). */
    private static final double REACH = 0.6, WADE = 0.5;
    /** How far Minecraft's walk counter runs in one stride of the walk clip (one stride a second at 0.06 blocks a tick, 4 a block). */
    private static final double STRIDE = 4.8;

    public CrocodileModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "crocodile"));
    }

    @Override
    public void setCustomAnimations(CrocodileEntity croc, long instanceId, AnimationState<CrocodileEntity> state) {
        super.setCustomAnimations(croc, instanceId, state);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float pt = state.getPartialTick();
        double x = Mth.lerp(pt, croc.xo, croc.getX()), y = Mth.lerp(pt, croc.yo, croc.getY()), z = Mth.lerp(pt, croc.zo, croc.getZ());
        if (camera.distanceToSqr(x, y, z) > NEAR * NEAR) {
            return;
        }
        GroundBend bend = croc.bend();
        bend.update(croc.level(), x, y, z, Mth.rotLerp(pt, croc.yBodyRotO, croc.yBodyRot), CrocodileEntity.MODEL_SCALE * croc.getScale(),
                croc.isInWater(), croc.floatDepth(), !croc.isDeadOrDying(), System.nanoTime());
        double scale = CrocodileEntity.MODEL_SCALE * croc.getScale();
        GeoBone body = getAnimationProcessor().getBone("body");
        if (body != null && bend.lift() > 1.0E-3) {      // the shoulders lifted onto a bank: the whole body up, in model pixels
            boolean marked = body.hasRotationChanged() || body.hasPositionChanged() || body.hasScaleChanged();
            body.setPosY(body.getPosY() + (float) (bend.lift() * 16.0 / scale));
            if (!marked) {
                body.resetStateChanges();
            }
        }
        int front = bend.frontPieces();
        for (int i = 0; i < CrocodileEntity.BENT_BONES.length; i++) {
            GeoBone bone = getAnimationProcessor().getBone(CrocodileEntity.BENT_BONES[i]);
            if (bone != null) {
                double turn = Math.toRadians(bend.turn(i));
                turnUp(bone, (float) (i < front ? turn : -turn));
            }
        }
        if (!croc.isDeadOrDying()) {
            legs(croc, bend, scale, pt);
        }
    }

    /**
     * The legs pair by pair (the crocodile's life step 2, 2026-11-15): each pair stands where its own feet find a footing
     * and folds back along the body where they do not. Walking into deep water the forelegs tuck in first while the hind
     * legs still push; swimming up to a bank, or onto a level bank, the forelegs reach out and walk while the hind legs
     * stay folded and the tail drives, then the hind legs too. The clips give the whole animal's pose (walking on land,
     * legs folded in the water); this moves each pair from that pose toward the other where its own ground says so.
     */
    private void legs(CrocodileEntity croc, GroundBend bend, double scale, float pt) {
        long now = System.nanoTime();
        double dt = croc.legNanos == 0 ? 1.0 : (now - croc.legNanos) / 1.0E9;
        croc.legNanos = now;
        double k = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / LEG_EASE);
        boolean clipFolds = croc.isInWater() && !croc.isBasking();     // the swim and float clips fold every leg
        double walked = croc.walkAnimation.position(pt) * 2.0 * Math.PI / STRIDE;
        double stepping = Mth.clamp(croc.walkAnimation.speed(pt) / 0.2, 0.0, 1.0);
        for (int pair = 0; pair < CrocodileEntity.LEG_PAIRS.length; pair++) {
            boolean feet = bend.footing(croc.level(), -CrocodileEntity.LEG_PAIRS[pair] * scale / 16.0, REACH * croc.getScale(), WADE);
            croc.legStand[pair] += ((feet ? 1.0 : 0.0) - croc.legStand[pair]) * k;
            double amount = clipFolds ? croc.legStand[pair] : 1.0 - croc.legStand[pair];
            if (amount < 1.0E-3) {
                continue;
            }
            String kind = pair == 0 ? "arm" : "leg";
            for (int s = 1; s >= -1; s -= 2) {
                String side = s > 0 ? "_left" : "_right";
                GeoBone upper = getAnimationProcessor().getBone(kind + "_upper" + side);
                GeoBone lower = getAnimationProcessor().getBone(kind + "_lower" + side);
                GeoBone foot = getAnimationProcessor().getBone(kind + "_foot" + side);
                if (clipFolds) {    // the walk's stride, diagonal pairs, as the walk clip has it (a bone turns as -x, -y, z of the clip's)
                    double ph = walked + (s > 0 ? 0 : Math.PI) + (pair == 1 ? Math.PI : 0);
                    double swing = Math.sin(ph) * stepping, rise = Math.max(0.0, Math.cos(ph)) * stepping;
                    blend(upper, 0, -s * 24 * swing, s * 9 * rise, amount);
                    blend(lower, 0, 0, 0, amount);
                    blend(foot, 0, s * 24 * swing, 0, amount);
                } else {            // folded back along the body, as the swim clip has it
                    blend(upper, 0, -s * (pair == 0 ? 65 : 58), -s * 10, amount);
                    blend(lower, 0, -s * 20, 0, amount);
                    blend(foot, 0, 0, 0, amount);
                }
            }
        }
    }

    /** Moves a bone's turn from its clip's toward this one (degrees), by {@code amount} (0 to 1), leaving the marks as they were. */
    private static void blend(GeoBone bone, double x, double y, double z, double amount) {
        if (bone == null) {
            return;
        }
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setRotX((float) Mth.lerp(amount, bone.getRotX(), Math.toRadians(x)));
        bone.setRotY((float) Mth.lerp(amount, bone.getRotY(), Math.toRadians(y)));
        bone.setRotZ((float) Mth.lerp(amount, bone.getRotZ(), Math.toRadians(z)));
        if (!marked) {
            bone.resetStateChanges();
        }
    }

    /**
     * Turns a bone this much more about X than the clip has it this frame. The model's bones are shared by every
     * crocodile drawn, and GeckoLib puts a bone back to its clip's pose at the next frame only if nothing marked it as
     * moved, so the marks are left as they were (the insects' lesson, {@code BodyLanguage.turnBy}).
     */
    private static void turnUp(GeoBone bone, float x) {
        if (Math.abs(x) < 1.0E-4F) {
            return;
        }
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setRotX(bone.getRotX() + x);
        if (!marked) {
            bone.resetStateChanges();
        }
    }
}
