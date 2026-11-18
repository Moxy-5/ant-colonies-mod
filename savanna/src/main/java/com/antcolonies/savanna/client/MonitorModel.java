package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.MonitorEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Nile monitor's model with its head and eyes on a near player (2026-11-17, the user's ask): resting, basking or
 * watching ({@link MonitorEntity#WATCH}), it turns its neck and head toward the nearest player within
 * {@link #LOOK_REACH} blocks, the turn shared along the neck, and its pupils slide round to them, on top of its clips.
 * With nobody near the head eases back and the clips' own glances play.
 */
public class MonitorModel extends DefaultedEntityGeoModel<MonitorEntity> {
    /** Who it looks at: a player within this many blocks (my pick, a little past the watch goal's reach). */
    private static final double LOOK_REACH = 16.0;
    /** Drawn only within this many blocks of the camera (farther the head turn is too small to see; my pick). */
    private static final double NEAR = 48.0;
    /** How far forward of its middle the head is (blocks at its usual size), and how far the head turns at most (degrees; my picks). */
    private static final double HEAD_AHEAD = 1.4, HEAD_TURN = 60.0;
    /** How quickly the eyes and the head follow (seconds; my picks: the eyes flick, the head turns smoothly). */
    private static final double EYE_EASE = 0.08, HEAD_EASE = 0.45;
    /** The share of the head's turn at each neck bone and the head (as the clips share it). */
    private static final String[] TURNING = {"neck1", "neck2", "head"};
    private static final double[] SHARE = {0.4, 0.3, 0.3};

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
