package com.antcolonies.savanna.client;

import com.antcolonies.client.BoneFrames;
import com.antcolonies.client.DraggedTail;
import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.entity.body.TailDrag;
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

import java.util.function.Function;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
 * <p>
 * Fixes from the user's reports (2026-11-18): the chest and hips stay closed where they meet, the chest turning on the
 * joint's outer edge (the top as the body crests, the bottom in a dip) and moved so that edge stays on the hips' own
 * (turning both on the middle of the body's height opened a dark wedge across the shoulders, and each on its own edge
 * pushed the front of the hips up out of the back going down a bank); a leg folds or unfolds along the shortest turn
 * between its two poses, the swim clip starting only once both pairs are folded ({@link MonitorEntity#swimClip}); and
 * where the forelegs lose the bank over water while the hind legs stand it plays the going-in clip, leaning out and
 * shoving off into the swim ({@link MonitorEntity#entering}).
 * <p>
 * Its tail is dragged behind it (the Animal Maker Engine step 2, stage A, 2026-11-19, {@link TailDrag}): walking and
 * standing on land, each joint is held in the world and pulled after the one in front, so round a turn the tail sweeps
 * after the body and over a step it drapes from the edge and is pulled up after the hips. The other clips (bask, hiss,
 * bite, eat, swim, going in, death) keep their own tail with the body's lie as before, eased over.
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
    /** The body's bend pivot (model pixels: height, length), where chest and hips meet; its height as the model has it. */
    private static final double PIVOT_Y = 11.0, PIVOT_Z = -13.0;
    /** The body boxes' top and bottom (model pixels): the chest turns on the joint's outer edge, one or the other. */
    private static final double BODY_TOP = 16.0, BODY_BOTTOM = 5.0;
    /**
     * The swim clip's legs folded back along the body (degrees, the left side as the clip file has them: upper, lower,
     * foot; the right side mirrors y and z), for a pair whose feet lose the ground while the rest still walks. From
     * {@code tools/monitor_models.py} (swim B, the hind feet held out wide); kept in step with it by hand.
     */
    private static final double[][][] FOLD = {
            {{-16.981, -61.241, 1.022}, {121.979, 45.687, 27.192}, {-107.544, -55.625, 158.836}},
            {{56.749, -52.764, -1.292}, {0.0, 0.0, 19.231}, {-56.661, 24.419, -131.013}}};
    private static final String[] PIECES = {"upper", "lower", "foot"};
    /** The tail, base to tip, dragged ({@link TailDrag}). */
    private static final DraggedTail TAIL = new DraggedTail("tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail7",
            "tail8", "tail9", "tail10", "tail11", "tail12");

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
        GeoBone waist = getAnimationProcessor().getBone("waist"), hips = getAnimationProcessor().getBone("hips");
        GeoBone[] spine = new GeoBone[MonitorEntity.TRUNK_PIECES];
        for (int i = 0; i < spine.length; i++) {
            spine[i] = getAnimationProcessor().getBone(MonitorEntity.BENT_BONES[i]);
        }
        // the bones are shared by every monitor drawn: put the hinges back before each one
        for (GeoBone bone : spine) {
            hinge(bone, PIVOT_Y);
        }
        hinge(hips, PIVOT_Y);
        if (camera.distanceToSqr(x, y, z) > NEAR * NEAR) {
            monitor.lookNanos = 0;
            monitor.headAim = 0;
            monitor.legNanos = 0;
            double afloat = monitor.isInWater() && !monitor.onGround() ? 0 : 1;
            monitor.legStand[0] = afloat;
            monitor.legStand[1] = afloat;
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
        // the tail bends with the body's lie only as far as the drag does not have it (TailDrag; the hand-over eased)
        double dragged = monitor.tail == null ? 0.0 : monitor.tail.weight();
        for (int i = 0; i < MonitorEntity.BENT_BONES.length; i++) {
            GeoBone bone = getAnimationProcessor().getBone(MonitorEntity.BENT_BONES[i]);
            if (bone != null) {
                double turn = Math.toRadians(bend.turn(i)) * (i > front ? 1.0 - dragged : 1.0);
                turnBy(bone, (float) (i < front ? turn : -turn));
            }
        }
        closeJoint(waist, hips);
        // the trunk's other joints turn on their outer edge too (each piece hangs from the one behind, so that edge stays put)
        for (int i = 1; i < spine.length; i++) {
            if (spine[i] != null) {
                hinge(spine[i], outerEdge(spine[i].getRotX()));
            }
        }
        byte doing = monitor.doing();
        if (!monitor.isDeadOrDying() && (doing == MonitorEntity.CALM || doing == MonitorEntity.WATCH)) {
            legs(monitor, bend, scale, x, y, z, yaw);
        }
        tail(monitor, bend, scale, x, y, z, yaw);
        look(monitor, pt, x, z, yaw);
    }

    /**
     * The tail dragged behind it ({@link TailDrag}): walking or standing on land (the walk and idle clips) the drag has it;
     * in the other clips and in the water the clip and the body's lie have it, as before (the engine's step 4 redoes the
     * water). The frame it hangs from is the hips as the body lies on the ground, lifted with the shoulders, without the
     * clips' own swing and bob, so on flat ground the tail is exactly the clip's.
     */
    private void tail(MonitorEntity monitor, GroundBend bend, double scale, double x, double y, double z, float yaw) {
        Function<String, GeoBone> bones = getAnimationProcessor()::getBone;
        GeoBone hips = bones.apply("hips");
        if (hips == null) {
            return;
        }
        if (monitor.tail == null) {
            monitor.tail = TAIL.newDrag(bones, MonitorEntity.TAIL_LOWEST);
            if (monitor.tail == null) {
                return;
            }
        }
        byte doing = monitor.doing();
        boolean on = !monitor.isDeadOrDying() && (doing == MonitorEntity.CALM || doing == MonitorEntity.WATCH)
                && !monitor.isInWater() && !monitor.swimClip() && !monitor.entering();
        Matrix4f parent = BoneFrames.placement(x, y, z, yaw, scale).translate(0f, (float) (bend.lift() / scale), 0f)
                .mul(BoneFrames.local(hips, false));
        TAIL.pose(bones, parent, monitor.tail, TailDrag.Ground.of(monitor.level(), y, monitor.isInWater(), monitor.floatDepth()),
                scale / 16.0, on, System.nanoTime());
    }

    /**
     * The legs pair by pair (2026-11-18): each pair stands where its own feet find a footing and folds back along the
     * body where they do not, so walking into deep water the forelegs tuck in first while the hind legs still push, and
     * swimming up to a bank the forelegs reach out onto it while the hind legs stay folded. Until both pairs are folded
     * the walk or idle clip plays ({@link MonitorEntity#swimClip}) and this turns each pair from the clip's pose toward
     * the swim clip's folded pose, along the shortest turn between them; once the swim clip plays, its legs are that
     * folded pose already. A standing pair's feet are kept on the ground as the body bends over a step.
     */
    private void legs(MonitorEntity monitor, GroundBend bend, double scale, double x, double y, double z, float yaw) {
        long now = System.nanoTime();
        double dt = monitor.legNanos == 0 ? 1.0 : (now - monitor.legNanos) / 1.0E9;
        monitor.legNanos = now;
        double k = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / LEG_EASE);
        double perBlock = scale / 16.0;
        boolean[] feet = new boolean[MonitorEntity.LEG_PAIRS.length];
        for (int pair = 0; pair < feet.length; pair++) {
            feet[pair] = bend.footing(monitor.level(), -MonitorEntity.LEG_PAIRS[pair] * perBlock, REACH * monitor.getScale(),
                    WADE * monitor.getScale());
        }
        boolean swimming = monitor.swimClip();
        // walking off the bank into the water: the forelegs over water, the hind legs still on the bank
        if (!swimming && !monitor.entering() && monitor.onGround() && monitor.walking() && !feet[0] && feet[1]
                && monitor.legStand[1] > 0.5
                && bend.overWater(monitor.level(), -MonitorEntity.LEG_PAIRS[0] * perBlock, REACH * monitor.getScale())) {
            monitor.enterTick = monitor.tickCount;
        }
        boolean entering = monitor.entering();
        for (int pair = 0; pair < feet.length; pair++) {
            // going in, both pairs count as folding (the clip folds them), so the swim clip follows it at once
            double want = feet[pair] && !entering ? 1.0 : 0.0;
            monitor.legStand[pair] += (want - monitor.legStand[pair]) * k;
            if (swimming || entering) {              // those clips set every leg themselves
                continue;
            }
            double fold = 1.0 - monitor.legStand[pair];
            String kind = pair == 0 ? "arm" : "leg";
            for (int s = 1; s >= -1; s -= 2) {
                String side = s > 0 ? "_left" : "_right";
                if (fold > 1.0E-3) {
                    for (int p = 0; p < PIECES.length; p++) {
                        GeoBone bone = getAnimationProcessor().getBone(kind + "_" + PIECES[p] + side);
                        double[] f = FOLD[pair][p];
                        // folded is the swim clip's turn on top of the leg's rest (a bone turns as -x, -y, z of a clip's;
                        // the right side's clip mirrors y and z)
                        turnToward(bone, -f[0], -s * f[1], s * f[2], fold);
                    }
                }
                if (monitor.legStand[pair] > 1.0E-3) {
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
        double hipsTurn = -Math.toRadians(bend.turn(bend.frontPieces()));
        // where the leg's root is once the bend has turned the trunk or hips (as the drawing does): the forelegs ride on
        // the chest, turned at each trunk joint from the front one back to the waist's on the hips
        double ry = 0, rz = MonitorEntity.LEG_PAIRS[pair], a = hipsTurn;
        if (pair == 0) {
            a = 0;
            for (int i = MonitorEntity.TRUNK_PIECES - 1; i >= 0; i--) {
                double t = Math.toRadians(bend.turn(i));
                a += t;
                double e = outerEdge(t), j = MonitorEntity.FRONT_JOINTS[i];
                double[] at = i == 0 ? jointCorner(t, hipsTurn) : new double[] {e, j, e, j};
                double dy = ry - at[0], dz = rz - at[1];
                ry = at[2] + dy * Math.cos(t) - dz * Math.sin(t);
                rz = at[3] + dy * Math.sin(t) + dz * Math.cos(t);
            }
        } else {
            double dy = -PIVOT_Y, dz = rz - PIVOT_Z;
            ry = PIVOT_Y + dy * Math.cos(a) - dz * Math.sin(a);
            rz = PIVOT_Z + dy * Math.sin(a) + dz * Math.cos(a);
        }
        double perBlock = scale / 16.0;
        double sy = ry + bend.lift() / perBlock;
        double sz = rz;
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

    /**
     * Turns a bone from its clip's pose toward its rest pose plus this turn (degrees), by {@code amount} (0 to 1), along
     * the shortest turn between the two (2026-11-18: easing each of the three angles on its own swung the folding legs
     * through poses that are neither, a thigh up over the back), leaving the marks as they were. GeckoLib turns a bone
     * about Z, then Y, then X, as JOML's ZYX order does.
     */
    private static void turnToward(GeoBone bone, double x, double y, double z, double amount) {
        if (bone == null) {
            return;
        }
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        BoneSnapshot rest = bone.getInitialSnapshot();
        Quaternionf from = new Quaternionf().rotationZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX());
        Quaternionf to = new Quaternionf().rotationZYX(rest.getRotZ() + (float) Math.toRadians(z),
                rest.getRotY() + (float) Math.toRadians(y), rest.getRotX() + (float) Math.toRadians(x));
        Vector3f turn = from.slerp(to, (float) Mth.clamp(amount, 0.0, 1.0)).getEulerAnglesZYX(new Vector3f());
        bone.setRotX(turn.x);
        bone.setRotY(turn.y);
        bone.setRotZ(turn.z);
        if (!marked) {
            bone.resetStateChanges();
        }
    }

    /**
     * Where the chest turns and where that point is carried to (model pixels: y, z, then y, z), for the chest and hips
     * turned this much (radians, about X as GeckoLib turns them): the joint's outer edge (the top when the chest's front
     * is lower than the hips would carry it, a crest; the bottom in a dip), taken where the hips' own turn about the
     * body's pivot puts that edge. Turning the chest about it and moving it there keeps the two boxes closed along it
     * whatever each turns; when both turn alike it is the same as turning both about the pivot, so the choice of edge
     * never jumps.
     */
    private static double[] jointCorner(double chestTurn, double hipsTurn) {
        double cy = chestTurn - hipsTurn > 0 ? BODY_BOTTOM : BODY_TOP;
        double dy = cy - PIVOT_Y, cos = Math.cos(hipsTurn), sin = Math.sin(hipsTurn);
        return new double[] {cy, PIVOT_Z, PIVOT_Y + dy * cos, PIVOT_Z + dy * sin};
    }

    /** The edge a trunk piece hanging from another turns on (model pixels high) for its turn: the bottom turning up, else the top. */
    private static double outerEdge(double turn) {
        return turn > 0 ? BODY_BOTTOM : BODY_TOP;
    }

    /** Keeps the waist closed on the hips (the drawn turns, clips and bend together); see {@link #jointCorner}. */
    private static void closeJoint(GeoBone chest, GeoBone hips) {
        if (chest == null || hips == null) {
            return;
        }
        double[] c = jointCorner(chest.getRotX(), hips.getRotX());
        boolean marked = chest.hasRotationChanged() || chest.hasPositionChanged() || chest.hasScaleChanged();
        chest.setPivotY((float) c[0]);
        chest.setPosY((float) (chest.getPosY() + hips.getPosY() + c[2] - c[0]));
        chest.setPosZ((float) (chest.getPosZ() + hips.getPosZ() + c[3] - c[1]));
        if (!marked) {
            chest.resetStateChanges();
        }
    }

    /** Sets where a body bone turns (model pixels high); its length place stays. */
    private static void hinge(GeoBone bone, double y) {
        if (bone != null) {
            bone.setPivotY((float) y);
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
