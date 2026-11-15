package com.antcolonies.savanna.client;

import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CrocodileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
    /** How far a standing leg reaches down, or draws up, to set its foot on the ground (model pixels; my pick: farther and it would come away from the body). */
    private static final double PLANT = 5.0;
    /** How far forward of the body's middle its eyes are (model pixels), how far a pupil slides along its eye (pixels), and how far the head turns toward what it watches (degrees; my picks). */
    private static final double EYES_AHEAD = 24.0, PUPIL_SLIDE = 0.84, HEAD_TURN = 14.0;
    /** How quickly the eyes jump to a new sight and the head follows (seconds; my picks: a crocodile's eyes flick, its head turns slowly). */
    private static final double EYE_EASE = 0.05, HEAD_EASE = 0.6;

    public CrocodileModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "crocodile"));
    }

    /** Which way the body bone's turn goes against the world's turn round the spine (see {@link CrocodileRollClient#ROLL_SIGN}; the crocodile's own roll is as it was). */
    static final float BODY_ROLL_SIGN = -1.0F;

    @Override
    public void setCustomAnimations(CrocodileEntity croc, long instanceId, AnimationState<CrocodileEntity> state) {
        super.setCustomAnimations(croc, instanceId, state);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float pt = state.getPartialTick();
        float roll = croc.rollAngle(pt);
        GeoBone rolled = roll != 0.0F ? getAnimationProcessor().getBone("body") : null;
        if (rolled != null) {         // the death roll: the whole body turns round its spine (the body bone's pivot)
            boolean marked = rolled.hasRotationChanged() || rolled.hasPositionChanged() || rolled.hasScaleChanged();
            rolled.setRotZ(rolled.getRotZ() + CrocodileRollClient.ROLL_SIGN * BODY_ROLL_SIGN * (float) Math.toRadians(roll));
            if (!marked) {
                rolled.resetStateChanges();
            }
        }
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
            float yaw = Mth.rotLerp(pt, croc.yBodyRotO, croc.yBodyRot);
            legs(croc, bend, scale, pt, x, y, z, yaw);
            eyes(croc, scale, pt, x, z, yaw);
        }
    }

    /**
     * The eyes and head on what caught its eye (2026-11-15, the user's ask; {@link CrocodileEntity#eyesOn()}): each slit
     * pupil slides along its eye, forward to look ahead, back to look behind, jumping there as a crocodile's eyes do,
     * and the head turns a little toward it, slowly. With nothing to watch the eyes wander now and then.
     */
    private void eyes(CrocodileEntity croc, double scale, float pt, double x, double z, float yaw) {
        double rel = croc.glance();
        Entity on = croc.eyesOn();
        if (on != null) {
            double ahead = EYES_AHEAD * scale / 16.0;
            double hx = x - Mth.sin(yaw * Mth.DEG_TO_RAD) * ahead, hz = z + Mth.cos(yaw * Mth.DEG_TO_RAD) * ahead;
            double tx = Mth.lerp(pt, on.xo, on.getX()), tz = Mth.lerp(pt, on.zo, on.getZ());
            rel = Mth.wrapDegrees(Math.toDegrees(Math.atan2(tz - hz, tx - hx)) - 90.0 - yaw);
        }
        long now = System.nanoTime();
        double dt = croc.eyeNanos == 0 ? 1.0 : (now - croc.eyeNanos) / 1.0E9;
        croc.eyeNanos = now;
        double eyeK = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / EYE_EASE);
        double headK = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / HEAD_EASE);
        croc.eyeAim = Mth.wrapDegrees(croc.eyeAim + Mth.wrapDegrees(rel - croc.eyeAim) * eyeK);
        double headWant = Math.abs(rel) < 120.0 ? Mth.clamp(rel, -HEAD_TURN, HEAD_TURN) : 0.0;
        croc.headAim += (headWant - croc.headAim) * headK;
        double slide = -PUPIL_SLIDE * Math.cos(Math.toRadians(croc.eyeAim));
        for (String name : new String[] {"pupil_left", "pupil_right"}) {
            GeoBone pupil = getAnimationProcessor().getBone(name);
            if (pupil != null) {
                boolean marked = pupil.hasRotationChanged() || pupil.hasPositionChanged() || pupil.hasScaleChanged();
                pupil.setPosZ((float) slide);
                if (!marked) {
                    pupil.resetStateChanges();
                }
            }
        }
        GeoBone neck = getAnimationProcessor().getBone("neck");
        if (neck != null && Math.abs(croc.headAim) > 1.0E-3) {
            boolean marked = neck.hasRotationChanged() || neck.hasPositionChanged() || neck.hasScaleChanged();
            neck.setRotY(neck.getRotY() - (float) Math.toRadians(croc.headAim));
            if (!marked) {
                neck.resetStateChanges();
            }
        }
    }

    /**
     * The legs pair by pair (the crocodile's life step 2, 2026-11-15): each pair stands where its own feet find a footing
     * and folds back along the body where they do not. Walking into deep water the forelegs tuck in first while the hind
     * legs still push; swimming up to a bank, or onto a level bank, the forelegs reach out and walk while the hind legs
     * stay folded and the tail drives, then the hind legs too. The clips give the whole animal's pose (walking on land,
     * legs folded in the water); this moves each pair from that pose toward the other where its own ground says so.
     */
    private void legs(CrocodileEntity croc, GroundBend bend, double scale, float pt, double x, double y, double z, float yaw) {
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
            String kind = pair == 0 ? "arm" : "leg";
            for (int s = 1; s >= -1; s -= 2) {
                if (amount < 1.0E-3) {
                    continue;
                }
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
            if (croc.legStand[pair] > 1.0E-3) {
                for (String side : new String[] {"_left", "_right"}) {
                    plant(croc, pair == 0 ? new String[] {"body", "chest"} : new String[] {"body", "chest", "hips"}, kind + side,
                            croc.legStand[pair], scale, x, y, z, yaw);
                }
            }
        }
    }

    /**
     * Sets a standing foot flat on the ground (2026-11-15, the user's report: basking, the feet stayed slanted and off
     * the ground): the body and the leg tip with the clips and with the body lying on what is under it, and the foot
     * tipped with them; here the foot is turned back level, and the leg reaches down or draws up (at most
     * {@link #PLANT} pixels) until the sole rests on the ground under it. Worked out in the side view (model height and
     * length) through the bones the leg hangs from, by {@code amount} (how much the pair stands).
     */
    private void plant(CrocodileEntity croc, String[] parents, String leg, double amount, double scale, double x, double y, double z, float yaw) {
        GeoBone upper = getAnimationProcessor().getBone(leg.replace("_", "_upper_"));
        GeoBone lower = getAnimationProcessor().getBone(leg.replace("_", "_lower_"));
        GeoBone foot = getAnimationProcessor().getBone(leg.replace("_", "_foot_"));
        if (upper == null || lower == null || foot == null) {
            return;
        }
        GeoBone[] chain = new GeoBone[parents.length + 2];
        for (int i = 0; i < parents.length; i++) {
            chain[i] = getAnimationProcessor().getBone(parents[i]);
            if (chain[i] == null) {
                return;
            }
        }
        chain[parents.length] = upper;
        chain[parents.length + 1] = lower;
        // the foot's pivot carried out through each bone it hangs from, innermost first (a bone turns about its pivot, then moves)
        double py = foot.getPivotY() + foot.getPosY(), pz = foot.getPivotZ() + foot.getPosZ();
        double tilt = 0, above = 0;
        for (int i = chain.length - 1; i >= 0; i--) {
            GeoBone b = chain[i];
            double a = b.getRotX(), dy = py - b.getPivotY(), dz = pz - b.getPivotZ();
            py = b.getPivotY() + dy * Math.cos(a) - dz * Math.sin(a) + b.getPosY();
            pz = b.getPivotZ() + dy * Math.sin(a) + dz * Math.cos(a) + b.getPosZ();
            tilt += a;
            if (i < parents.length) {
                above += a;
            }
        }
        // level the foot
        boolean marked = foot.hasRotationChanged() || foot.hasPositionChanged() || foot.hasScaleChanged();
        foot.setRotX((float) (foot.getRotX() - tilt * amount));
        if (!marked) {
            foot.resetStateChanges();
        }
        // the ground under the foot (the drawn model turned by 180 less its yaw, as GeckoLib draws it)
        double turn = Math.toRadians(180.0 - yaw), mx = foot.getPivotX(), perBlock = scale / 16.0;
        double wx = x + (mx * Math.cos(turn) + pz * Math.sin(turn)) * perBlock;
        double wz = z + (-mx * Math.sin(turn) + pz * Math.cos(turn)) * perBlock;
        double sole = py - foot.getPivotY();          // the sole lies at the model's floor, under the foot's pivot
        double ground = (GroundBend.support(croc.level(), wx, wz, y + sole * perBlock, false, 100.0) - y) / perBlock;
        double reach = Mth.clamp(sole - ground, -PLANT, PLANT) * amount / Math.max(0.5, Math.cos(above));
        if (Math.abs(reach) < 1.0E-3) {
            return;
        }
        marked = upper.hasRotationChanged() || upper.hasPositionChanged() || upper.hasScaleChanged();
        upper.setPosY((float) (upper.getPosY() - reach));
        if (!marked) {
            upper.resetStateChanges();
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
