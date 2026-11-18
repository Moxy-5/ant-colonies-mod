package com.antcolonies.savanna.client;

import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.MonitorEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Nile monitor's model with its head and eyes on a near player (2026-11-17, the user's ask): resting, basking or
 * watching ({@link MonitorEntity#WATCH}), it turns its neck and head toward the nearest player within
 * {@link #LOOK_REACH} blocks, the turn shared along the neck, and its pupils slide round to them, on top of its clips.
 * With nobody near the head eases back and the clips' own glances play.
 * <p>
 * Its body lies on what is under it (2026-11-18, the user's ask, {@link GroundBend} as the crocodile's): over a step
 * the chest and neck tip down or up while the hips and tail stay on the ground behind, and going into the river the
 * head and chest dip over the bank's edge first, the forelegs fold back as their feet lose the ground while the hind
 * legs still walk, and the tail slides in last; climbing out the forelegs reach for the bank first.
 */
public class MonitorModel extends DefaultedEntityGeoModel<MonitorEntity> {
    /** Who it looks at: a player within this many blocks (my pick, a little past the watch goal's reach). */
    private static final double LOOK_REACH = 16.0;
    /** Drawn only within this many blocks of the camera (farther the head turn and bend are too small to see; my pick). */
    private static final double NEAR = 48.0;
    /** How far forward of its middle the head is (blocks at its usual size), and how far the head turns at most (degrees; my picks). */
    private static final double HEAD_AHEAD = 1.4, HEAD_TURN = 60.0;
    /** How quickly the eyes and the head follow (seconds; my picks: the eyes flick, the head turns smoothly). */
    private static final double EYE_EASE = 0.08, HEAD_EASE = 0.45;
    /** The share of the head's turn at each neck bone and the head (as the clips share it). */
    private static final String[] TURNING = {"neck1", "neck2", "head"};
    private static final double[] SHARE = {0.4, 0.3, 0.3};

    /** How quickly a leg pair folds or stands (seconds; my pick). */
    private static final double LEG_EASE = 0.2;
    /**
     * How far under the body's own height a pair's feet still reach the ground, and how deep water they still wade in
     * (blocks, at size 1; my picks). The reach is more than a block, so a pair stepping down off a block stands on (its
     * legs fold back only over deep water, never over a drop on land, where folded forelegs stuck up like spikes).
     */
    private static final double REACH = 1.3, WADE = 0.4;
    /** How far a standing leg reaches down, or draws up, to keep its foot on the ground as the body bends (model pixels; my pick). */
    private static final double PLANT = 4.0;
    /** How far out from the middle line each pair's feet stand (model pixels; from the leg plans). */
    private static final double FOOT_OUT = 11.0;
    /** The body's bend pivot (model pixels: height, length), where chest and hips turn. */
    private static final double PIVOT_Y = 11.0, PIVOT_Z = -13.0;
    /**
     * The swim clip's legs folded back along the body (degrees, the left side as the clip file has them: upper, lower,
     * foot; the right side mirrors y and z), for a pair whose feet lose the ground while the rest still walks. From
     * {@code tools/monitor_models.py} (swim B, the hind feet held out wide); kept in step with it by hand.
     */
    private static final double[][][] FOLD = {
            {{-16.981, -61.241, 1.022}, {121.979, 45.687, 27.192}, {-107.544, -55.625, 158.836}},
            {{56.749, -52.764, -1.292}, {0.0, 0.0, 19.231}, {-56.661, 24.419, -131.013}}};
    private static final String[] PIECES = {"upper", "lower", "foot"};

    public MonitorModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "monitor"));
    }

    @Override
    public void setCustomAnimations(MonitorEntity monitor, long instanceId, AnimationState<MonitorEntity> state) {
        super.setCustomAnimations(monitor, instanceId, state);
        float pt = state.getPartialTick();
        double x = Mth.lerp(pt, monitor.xo, monitor.getX()), y = Mth.lerp(pt, monitor.yo, monitor.getY());
        double z = Mth.lerp(pt, monitor.zo, monitor.getZ());
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(x, y, z) > NEAR * NEAR) {
            monitor.lookNanos = 0;
            monitor.headAim = 0;
            return;
        }
        float yaw = Mth.rotLerp(pt, monitor.yBodyRotO, monitor.yBodyRot);
        double scale = MonitorEntity.MODEL_SCALE * monitor.getScale();
        GroundBend bend = monitor.bend();
        bend.update(monitor.level(), x, y, z, yaw, scale, monitor.isInWater(), monitor.floatDepth(), !monitor.isDeadOrDying(),
                System.nanoTime());
        GeoBone body = getAnimationProcessor().getBone("body");
        if (body != null && bend.lift() > 1.0E-3) {      // the shoulders lifted onto a bank: the whole body up, in model pixels
            boolean marked = body.hasRotationChanged() || body.hasPositionChanged() || body.hasScaleChanged();
            body.setPosY(body.getPosY() + (float) (bend.lift() * 16.0 / scale));
            if (!marked) {
                body.resetStateChanges();
            }
        }
        int front = bend.frontPieces();
        for (int i = 0; i < MonitorEntity.BENT_BONES.length; i++) {
            GeoBone bone = getAnimationProcessor().getBone(MonitorEntity.BENT_BONES[i]);
            if (bone != null) {
                double turn = Math.toRadians(bend.turn(i));
                turnBy(bone, (float) (i < front ? turn : -turn));
            }
        }
        byte doing = monitor.doing();
        if (!monitor.isDeadOrDying() && (doing == MonitorEntity.CALM || doing == MonitorEntity.WATCH)) {
            legs(monitor, bend, scale, x, y, z, yaw);
        }
        look(monitor, pt, x, z, yaw);
    }

    /**
     * The legs pair by pair (2026-11-18): each pair stands where its own feet find a footing and folds back along the
     * body where they do not, so walking into deep water the forelegs tuck in first while the hind legs still push, and
     * swimming up to a bank the forelegs reach out onto it while the hind legs stay folded. The clips give the whole
     * animal's pose (walking, or folded to swim); this moves each pair toward the other where its own ground says so,
     * and keeps a standing pair's feet on the ground as the body bends over a step.
     */
    private void legs(MonitorEntity monitor, GroundBend bend, double scale, double x, double y, double z, float yaw) {
        long now = System.nanoTime();
        double dt = monitor.legNanos == 0 ? 1.0 : (now - monitor.legNanos) / 1.0E9;
        monitor.legNanos = now;
        double k = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / LEG_EASE);
        boolean clipFolds = monitor.isInWater() && !monitor.onGround();      // the swim clip folds every leg
        double perBlock = scale / 16.0;
        for (int pair = 0; pair < MonitorEntity.LEG_PAIRS.length; pair++) {
            double along = MonitorEntity.LEG_PAIRS[pair];
            boolean feet = bend.footing(monitor.level(), -along * perBlock, REACH * monitor.getScale(), WADE * monitor.getScale());
            monitor.legStand[pair] += ((feet ? 1.0 : 0.0) - monitor.legStand[pair]) * k;
            double amount = clipFolds ? monitor.legStand[pair] : 1.0 - monitor.legStand[pair];
            String kind = pair == 0 ? "arm" : "leg";
            for (int s = 1; s >= -1; s -= 2) {
                String side = s > 0 ? "_left" : "_right";
                if (amount > 1.0E-3) {
                    for (int p = 0; p < PIECES.length; p++) {
                        GeoBone bone = getAnimationProcessor().getBone(kind + "_" + PIECES[p] + side);
                        double[] fold = FOLD[pair][p];
                        // standing is the leg's rest; folded is the swim clip's turn on top of it (a bone turns as -x, -y, z
                        // of a clip's; the right side's clip mirrors y and z)
                        double cx = clipFolds ? 0 : -fold[0], cy = clipFolds ? 0 : -s * fold[1], cz = clipFolds ? 0 : s * fold[2];
                        blendToRest(bone, cx, cy, cz, amount);
                    }
                }
                if (monitor.legStand[pair] > 1.0E-3 && !clipFolds) {
                    plant(monitor, bend, pair, kind + "_upper" + side, s, monitor.legStand[pair], scale, x, y, z, yaw);
                }
            }
        }
    }

    /**
     * Keeps a standing foot on the ground as the body bends (2026-11-18): the clips set the feet on level ground, and
     * when the chest or hips tip over a step, or the shoulders lift onto a bank, the feet would come off it or sink in;
     * the leg reaches down or draws up (at most {@link #PLANT} pixels) until the sole rests on the ground under it again.
     * Only what the bend added is worked out (the clips' own steps and bobbing are left alone), in the side view.
     */
    private void plant(MonitorEntity monitor, GroundBend bend, int pair, String upperName, int side, double amount, double scale,
            double x, double y, double z, float yaw) {
        GeoBone upper = getAnimationProcessor().getBone(upperName);
        if (upper == null) {
            return;
        }
        int piece = pair == 0 ? 0 : bend.frontPieces();          // the chest, or the hips
        double a = Math.toRadians(bend.turn(piece)) * (pair == 0 ? 1 : -1);
        double dy = -PIVOT_Y, dz = MonitorEntity.LEG_PAIRS[pair] - PIVOT_Z;
        double perBlock = scale / 16.0;
        double sy = PIVOT_Y + dy * Math.cos(a) - dz * Math.sin(a) + bend.lift() / perBlock;
        double sz = PIVOT_Z + dy * Math.sin(a) + dz * Math.cos(a);
        // the ground under the foot (the drawn model turned by 180 less its yaw, as GeckoLib draws it)
        double turn = Math.toRadians(180.0 - yaw), mx = side * FOOT_OUT;
        double wx = x + (mx * Math.cos(turn) + sz * Math.sin(turn)) * perBlock;
        double wz = z + (-mx * Math.sin(turn) + sz * Math.cos(turn)) * perBlock;
        double ground = (GroundBend.support(monitor.level(), wx, wz, y + sy * perBlock, false, 100.0) - y) / perBlock;
        double reach = Mth.clamp(sy - ground, -PLANT, PLANT) * amount / Math.max(0.5, Math.cos(a));
        if (Math.abs(reach) < 1.0E-3) {
            return;
        }
        boolean marked = upper.hasRotationChanged() || upper.hasPositionChanged() || upper.hasScaleChanged();
        upper.setPosY((float) (upper.getPosY() - reach));
        if (!marked) {
            upper.resetStateChanges();
        }
    }

    /** Moves a bone's turn from its clip's toward its rest turn plus this one (degrees), by {@code amount} (0 to 1), leaving the marks as they were. */
    private static void blendToRest(GeoBone bone, double x, double y, double z, double amount) {
        if (bone == null) {
            return;
        }
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        BoneSnapshot rest = bone.getInitialSnapshot();
        bone.setRotX((float) Mth.lerp(amount, bone.getRotX(), rest.getRotX() + Math.toRadians(x)));
        bone.setRotY((float) Mth.lerp(amount, bone.getRotY(), rest.getRotY() + Math.toRadians(y)));
        bone.setRotZ((float) Mth.lerp(amount, bone.getRotZ(), rest.getRotZ() + Math.toRadians(z)));
        if (!marked) {
            bone.resetStateChanges();
        }
    }

    /** Turns a bone this much more about X than its clip has it this frame, leaving the marks as they were (shared bones; the crocodile's lesson). */
    private static void turnBy(GeoBone bone, float x) {
        if (Math.abs(x) < 1.0E-4F) {
            return;
        }
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setRotX(bone.getRotX() + x);
        if (!marked) {
            bone.resetStateChanges();
        }
    }

    /** The head and eyes on a near player (2026-11-17). */
    private void look(MonitorEntity monitor, float pt, double x, double z, float yaw) {
        Player on = watched(monitor);
        double want = 0.0, rel = 0.0;
        if (on != null) {
            double ahead = HEAD_AHEAD * monitor.ownSize();
            double hx = x - Mth.sin(yaw * Mth.DEG_TO_RAD) * ahead, hz = z + Mth.cos(yaw * Mth.DEG_TO_RAD) * ahead;
            double tx = Mth.lerp(pt, on.xo, on.getX()), tz = Mth.lerp(pt, on.zo, on.getZ());
            rel = Mth.wrapDegrees(Math.toDegrees(Math.atan2(tz - hz, tx - hx)) - 90.0 - yaw);
            want = Math.abs(rel) < 150.0 ? Mth.clamp(rel, -HEAD_TURN, HEAD_TURN) : 0.0;
        }
        long now = System.nanoTime();
        double dt = monitor.lookNanos == 0 ? 1.0 : (now - monitor.lookNanos) / 1.0E9;
        monitor.lookNanos = now;
        double eyeK = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / EYE_EASE);
        double headK = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / HEAD_EASE);
        monitor.headAim += (want - monitor.headAim) * headK;
        if (on != null) {
            monitor.eyeAim = Mth.wrapDegrees(monitor.eyeAim + Mth.wrapDegrees(rel - monitor.headAim - monitor.eyeAim) * eyeK);
            // a pupil sits at the front of its eye looking ahead (-0.25) and slides back to look aside (+0.1) and behind (+0.45)
            double slide = 0.1 - 0.35 * Math.cos(Math.toRadians(monitor.eyeAim));
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
        } else {
            monitor.eyeAim = 0;
        }
        if (Math.abs(monitor.headAim) < 1.0E-3) {
            return;
        }
        for (int i = 0; i < TURNING.length; i++) {
            GeoBone bone = getAnimationProcessor().getBone(TURNING[i]);
            if (bone != null) {
                boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
                bone.setRotY(bone.getRotY() - (float) Math.toRadians(monitor.headAim * SHARE[i]));
                if (!marked) {
                    bone.resetStateChanges();
                }
            }
        }
    }

    /** The nearest player it would look at: alive, resting, basking or watching, on land, and the player not a spectator. */
    private static Player watched(MonitorEntity monitor) {
        byte doing = monitor.doing();
        if (!monitor.isAlive() || monitor.isInWater()
                || doing != MonitorEntity.CALM && doing != MonitorEntity.BASK && doing != MonitorEntity.WATCH) {
            return null;
        }
        Player best = null;
        double bestD = LOOK_REACH * LOOK_REACH;
        for (Player p : monitor.level().players()) {
            double d = p.distanceToSqr(monitor);
            if (!p.isSpectator() && p.isAlive() && d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }
}
