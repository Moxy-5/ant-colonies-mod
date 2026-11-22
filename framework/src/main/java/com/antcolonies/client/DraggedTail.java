package com.antcolonies.client;

import com.antcolonies.entity.body.TailDrag;

import java.util.function.Function;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Client, the body only: a model's tail bones turned to lie along its {@link TailDrag} (the Animal Maker Engine step 2,
 * stage A, 2026-11-19; plan {@code docs/plans/engine-step2-climb.md}). Each tail bone is turned so its far end is at its
 * dragged joint, worked out with GeckoLib's own drawing sums ({@link BoneFrames}), so what is worked out is what is drawn.
 * The clips' own movement rides on top: each bone keeps its clip's turn, moved by how far the dragged tail lies from
 * the model's rest pose; on flat ground going straight that is nothing, and the tail is exactly the clip's (a walk's side
 * to side wave stays as it was made). The hips' own swing in the clips is left out of the frame the drag is worked out in
 * for the same reason.
 * <p>
 * Species-neutral: any tail of bones each hanging from the one before, named in order from the base. One per model (the
 * rest pose is read once); the bones are shared by every animal drawn with the model, so it is called for each animal.
 */
public final class DraggedTail {
    private final String[] names;
    private boolean ready;
    /** Each bone's pivot and the line from it to its far end at rest, unturned (model blocks); that line's own yaw and pitch. */
    private Vector3f[] pivot, along;
    private float[] alongYaw, alongPitch;
    /** Each bone's rest turn (radians). */
    private float[] restX, restY, restZ;
    private double[] lengthPx, restPx;
    /** The tail root's height over flat ground at rest (model pixels). */
    private double rootPx;

    public DraggedTail(String... names) {
        this.names = names.clone();
    }

    /**
     * A new drag for one animal, its pieces read from the model's rest pose; {@code lowestPx} is the least each joint lies
     * over the ground (model pixels: its own thickness). Null if the model lacks the bones.
     */
    public TailDrag newDrag(Function<String, GeoBone> bones, double[] lowestPx) {
        return prepare(bones) ? new TailDrag(lengthPx, restPx, lowestPx, rootPx) : null;
    }

    /**
     * Drags the tail for this frame and turns its bones by the drag's share.
     *
     * @param parent   from the model to the world for the bone the tail hangs from (the hips), as the body lies on the
     *                 ground, without the clips' own swing and bob
     * @param perBlock blocks per model pixel at the animal's drawn size
     * @param on       whether the drag should have the tail now (else its clip has it; the hand-over is eased)
     */
    public void pose(Function<String, GeoBone> bones, Matrix4f parent, TailDrag drag, TailDrag.Ground ground, double perBlock,
            boolean on, long nanos) {
        if (!prepare(bones) || drag.pieces() != names.length) {
            return;
        }
        GeoBone first = bones.apply(names[0]);
        Vector3f base = parent.transformPosition(new Vector3f(pivot[0]).add(BoneFrames.offset(first)));
        Vector3f back = parent.transformDirection(new Vector3f(along[0])).normalize();
        double pitch = Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, back.y))));
        drag.update(ground, base.x, base.y, base.z, back.x, back.z, pitch, perBlock, on, nanos);
        float w = (float) drag.weight();
        if (w < 1.0E-3f) {
            return;
        }
        Matrix4f m = new Matrix4f(parent);
        Matrix4f inverse = new Matrix4f();
        for (int i = 0; i < names.length; i++) {
            GeoBone bone = bones.apply(names[i]);
            Vector3f offset = BoneFrames.offset(bone);
            // the dragged joint at this bone's far end, in the frame it hangs in, from its pivot
            Vector3f t = m.invert(inverse).transformPosition(new Vector3f((float) drag.x(i + 1), (float) drag.y(i + 1), (float) drag.z(i + 1)))
                    .sub(offset).sub(pivot[i]);
            if (t.lengthSquared() < 1.0E-12f) {
                return;
            }
            t.normalize();
            float yaw = (float) Math.atan2(t.x, t.z) - alongYaw[i];
            float pitchX = (float) -Math.asin(Math.max(-1f, Math.min(1f, t.y))) - alongPitch[i];
            // its clip's turn, moved by how far the drag lies from rest
            BoneFrames.setTurn(bone, bone.getRotX() + w * (pitchX - restX[i]), bone.getRotY() + w * (yaw - restY[i]), bone.getRotZ());
            // the next bone hangs from this one as the drag lies it (without the clip's own wave)
            m.mul(BoneFrames.local(offset, pivot[i], restZ[i], yaw, pitchX));
        }
    }

    /** Reads the rest pose once; false if the model lacks a bone or they do not hang one from the next. */
    private boolean prepare(Function<String, GeoBone> bones) {
        if (ready) {
            return true;
        }
        int n = names.length;
        GeoBone[] b = new GeoBone[n];
        for (int i = 0; i < n; i++) {
            b[i] = bones.apply(names[i]);
            if (b[i] == null || i > 0 && b[i].getParent() != b[i - 1]) {
                return false;
            }
        }
        pivot = new Vector3f[n];
        along = new Vector3f[n];
        alongYaw = new float[n];
        alongPitch = new float[n];
        restX = new float[n];
        restY = new float[n];
        restZ = new float[n];
        lengthPx = new double[n];
        restPx = new double[n];
        for (int i = 0; i < n; i++) {
            pivot[i] = BoneFrames.pivot(b[i]);
        }
        Matrix4f m = BoneFrames.chain(b[0].getParent(), true);
        rootPx = m.transformPosition(new Vector3f(pivot[0])).y * 16.0;
        for (int i = 0; i < n; i++) {
            // the last bone reaches as far past its pivot as the one before it does
            along[i] = i + 1 < n ? new Vector3f(pivot[i + 1]).sub(pivot[i]) : new Vector3f(along[i - 1]);
            float len = along[i].length();
            alongYaw[i] = (float) Math.atan2(along[i].x, along[i].z);
            alongPitch[i] = (float) -Math.asin(along[i].y / len);
            BoneSnapshot s = b[i].getInitialSnapshot();
            restX[i] = s.getRotX();
            restY[i] = s.getRotY();
            restZ[i] = s.getRotZ();
            m.mul(BoneFrames.local(b[i], true));
            Vector3f end = m.transformPosition(new Vector3f(pivot[i]).add(along[i]));
            lengthPx[i] = len * 16.0;
            restPx[i] = end.y * 16.0;
        }
        ready = true;
        return true;
    }
}
