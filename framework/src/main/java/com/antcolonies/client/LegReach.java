package com.antcolonies.client;

import java.util.function.Function;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Client, the body only: one leg reaching for its foot's place (the Animal Maker Engine step 2, feet first, stage A,
 * 2026-11-19; plan {@code docs/plans/engine-step2-feet-first.md}). A leg of an upper piece, a lower piece and a foot: the
 * joints are worked out so the wrist (the foot's own joint) is where the foot needs it ({@code entity/body/Footing}
 * holds the foot in the world), as the clip makers do in Python ({@code tools/monitor_models.py}, the walk's legs):
 * <ul>
 * <li>the elbow or knee bends out the way it does at rest (the leg's rest pose sets the side it bends to);</li>
 * <li>the lower piece turns at the elbow or knee as a hinge, about the line the leg bends on at rest, so the knee never
 * twists (the user's report on the monitor: a twisting hind leg looked broken);</li>
 * <li>the foot lies flat as at rest whatever the body above it does (the body swinging, the trunk tipping on a bank),
 * tipped toes-down by a given turn as it swings, and its claws curled by another.</li>
 * </ul>
 * Out of reach, the leg stretches as far as it goes toward the place. The whole sum is GeckoLib's own drawing maths (each
 * bone moved, turned about its pivot Z then Y then X, scaled), read from the bones as they are this frame (the clips and
 * the body's bend already in), so what is worked out is what is drawn.
 * <p>
 * Species-neutral: any leg of two pieces and a foot, named by its bones. One per leg, kept for the model (the rest pose is
 * read once); the bones are shared by every animal drawn with the model, so it is called for each animal every frame.
 */
public final class LegReach {
    private final String upperName, lowerName, footName;
    private final String[] clawNames;
    private boolean ready;
    /** At rest, in the upper piece's own frame: the elbow from the shoulder's pivot; in the lower's, the wrist from the elbow's. */
    private final Vector3f a = new Vector3f(), b0 = new Vector3f();
    private float l1, l2;
    /** The rest turns of the upper, lower and foot pieces, the hinge line in the upper piece's frame, the foot's rest turn in the model. */
    private final Matrix3f restUpper = new Matrix3f(), restLower = new Matrix3f(), footInModel = new Matrix3f();
    private final Vector3f hinge = new Vector3f();
    /** The wrist at rest, in the model (model blocks). */
    private final Vector3f restWrist = new Vector3f();
    private float[] clawRest = new float[0];

    public LegReach(String upper, String lower, String foot, String... claws) {
        this.upperName = upper;
        this.lowerName = lower;
        this.footName = foot;
        this.clawNames = claws;
    }

    /** Where the animal is drawn: its place, its body's turn (degrees, as the renderer has it), its drawn size. */
    public record Placement(double x, double y, double z, float yaw, double scale) {
        Matrix4f matrix() {
            return new Matrix4f().translate((float) x, (float) y, (float) z).rotateY((float) Math.toRadians(180.0 - yaw))
                    .scale((float) scale);
        }
    }

    /** The wrist's place at rest in the model (model blocks, GeckoLib's axes), or null if the model lacks the leg. */
    public Vector3f restWrist(Function<String, GeoBone> bones) {
        return prepare(bones) ? new Vector3f(restWrist) : null;
    }

    /** Where the wrist is drawn now (world), the clips and the bend as they are this frame; null if the model lacks the leg. */
    public Vector3f wristNow(Function<String, GeoBone> bones, Placement at) {
        if (!prepare(bones)) {
            return null;
        }
        GeoBone upper = bones.apply(upperName), lower = bones.apply(lowerName), foot = bones.apply(footName);
        Matrix4f m = at.matrix().mul(chain(upper.getParent(), false)).mul(local(upper, false)).mul(local(lower, false));
        return m.transformPosition(pivot(foot));
    }

    /**
     * Bends the leg so its wrist is at this place (world; the foot's ground spot raised by the wrist's rest height is the
     * caller's), the foot flat but tipped toes-down by {@code tip} and its claws curled by {@code curl} (radians), all
     * by {@code amount} (0 the bones as they were, 1 fully reached).
     */
    public void reach(Function<String, GeoBone> bones, Placement at, float wx, float wy, float wz, float tip, float curl) {
        if (!prepare(bones)) {
            return;
        }
        GeoBone upper = bones.apply(upperName), lower = bones.apply(lowerName), foot = bones.apply(footName);
        Matrix4f parent = chain(upper.getParent(), false);
        // the place in the frame the upper piece hangs in
        Vector3f t = new Matrix4f(at.matrix()).mul(parent).invert().transformPosition(new Vector3f(wx, wy, wz));
        Vector3f shoulder = new Vector3f(-upper.getPosX() / 16f, upper.getPosY() / 16f, upper.getPosZ() / 16f).add(pivot(upper));
        t.sub(shoulder);
        float d = t.length();
        float most = 0.985f * (l1 + l2), least = Math.abs(l1 - l2) * 1.02f + 1.0E-4f;
        if (d < 1.0E-5f) {
            return;
        }
        Vector3f dir = new Vector3f(t).div(d);
        d = Math.max(least, Math.min(most, d));
        // the elbow: on the side it is at rest, where the two pieces meet
        float along = (l1 * l1 - l2 * l2 + d * d) / (2f * d);
        float out = (float) Math.sqrt(Math.max(0.0, l1 * l1 - along * along));
        Vector3f pole = restUpper.transform(new Vector3f(a));
        pole.fma(-pole.dot(dir), dir);
        if (pole.lengthSquared() < 1.0E-8f) {
            pole.set(0, -1, 0).fma(dir.y, dir);
        }
        pole.normalize();
        Vector3f elbow = new Vector3f(dir).mul(along).fma(out, pole);
        Vector3f lowerDir = new Vector3f(dir).mul(d).sub(elbow);
        // the upper piece: its own elbow line onto the elbow, its hinge line onto the line the leg now bends on
        Vector3f bendLine = new Vector3f(elbow).cross(lowerDir);
        if (bendLine.lengthSquared() < 1.0E-10f) {
            return;
        }
        Matrix3f upperTurn = frame(elbow, bendLine).mul(frame(a, hinge).transpose());
        // the lower piece: turned about the hinge from its rest turn until it points at the wrist's place
        Vector3f want = new Matrix3f(upperTurn).transpose().transform(new Vector3f(lowerDir));
        Vector3f rest = restLower.transform(new Vector3f(b0));
        float bend = signedAngle(rest, want, hinge);
        Matrix3f lowerTurn = new Matrix3f().rotation(bend, hinge).mul(restLower);
        // the foot flat as at rest in the model, whatever turns the body above it, then tipped and its claws curled
        Matrix3f body = new Matrix3f(parent);
        Matrix3f footTurn = new Matrix3f(upperTurn).mul(lowerTurn).transpose().mul(body.transpose()).mul(footInModel)
                .rotateX(tip);
        set(upper, upperTurn);
        set(lower, lowerTurn);
        set(foot, footTurn);
        for (int i = 0; i < clawNames.length; i++) {
            GeoBone claw = bones.apply(clawNames[i]);
            if (claw != null) {
                boolean marked = claw.hasRotationChanged() || claw.hasPositionChanged() || claw.hasScaleChanged();
                claw.setRotX(clawRest[i] - curl);
                if (!marked) {
                    claw.resetStateChanges();
                }
            }
        }
    }

    /** Reads the leg's rest pose once; false if the model has no such leg. */
    private boolean prepare(Function<String, GeoBone> bones) {
        if (ready) {
            return true;
        }
        GeoBone upper = bones.apply(upperName), lower = bones.apply(lowerName), foot = bones.apply(footName);
        if (upper == null || lower == null || foot == null || lower.getParent() != upper || foot.getParent() != lower) {
            return false;
        }
        // at rest no bone is moved, so the elbow and wrist are where their pivots are (the clips only turn the legs)
        a.set(pivot(lower)).sub(pivot(upper));
        b0.set(pivot(foot)).sub(pivot(lower));
        l1 = a.length();
        l2 = b0.length();
        restUpper.set(turn(upper.getInitialSnapshot()));
        restLower.set(turn(lower.getInitialSnapshot()));
        hinge.set(a).cross(restLower.transform(new Vector3f(b0)));
        if (hinge.lengthSquared() < 1.0E-10f) {
            hinge.set(1, 0, 0);
        }
        hinge.normalize();
        Matrix3f parentRest = new Matrix3f(chain(upper.getParent(), true));
        footInModel.set(parentRest).mul(restUpper).mul(restLower).mul(turn(foot.getInitialSnapshot()));
        restWrist.set(chain(upper.getParent(), true).mul(local(upper, true)).mul(local(lower, true)).transformPosition(pivot(foot)));
        clawRest = new float[clawNames.length];
        for (int i = 0; i < clawNames.length; i++) {
            GeoBone claw = bones.apply(clawNames[i]);
            clawRest[i] = claw == null ? 0f : claw.getInitialSnapshot().getRotX();
        }
        ready = true;
        return true;
    }

    /** The bone's pivot in model blocks. */
    private static Vector3f pivot(GeoBone bone) {
        return new Vector3f(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()).div(16f);
    }

    /** From the model to this bone's own frame, root first (its own move and turn in): GeckoLib's drawing sum. */
    private static Matrix4f chain(GeoBone bone, boolean rest) {
        if (bone == null) {
            return new Matrix4f();
        }
        return chain(bone.getParent(), rest).mul(local(bone, rest));
    }

    /** One bone's own step: moved, to its pivot, turned Z then Y then X, scaled, back from its pivot (as {@code RenderUtil.prepMatrixForBone}). */
    private static Matrix4f local(GeoBone bone, boolean rest) {
        Matrix4f m = new Matrix4f();
        Vector3f p = pivot(bone);
        if (rest) {
            BoneSnapshot s = bone.getInitialSnapshot();
            m.translate(-s.getOffsetX() / 16f, s.getOffsetY() / 16f, s.getOffsetZ() / 16f).translate(p)
                    .rotateZ(s.getRotZ()).rotateY(s.getRotY()).rotateX(s.getRotX()).scale(s.getScaleX(), s.getScaleY(), s.getScaleZ());
        } else {
            m.translate(-bone.getPosX() / 16f, bone.getPosY() / 16f, bone.getPosZ() / 16f).translate(p)
                    .rotateZ(bone.getRotZ()).rotateY(bone.getRotY()).rotateX(bone.getRotX())
                    .scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
        }
        return m.translate(-p.x, -p.y, -p.z);
    }

    private static Matrix3f turn(BoneSnapshot s) {
        return new Matrix3f().rotationZYX(s.getRotZ(), s.getRotY(), s.getRotX());
    }

    /** A turn whose first axis is along {@code first} and second along {@code second} made square to it. */
    private static Matrix3f frame(Vector3f first, Vector3f second) {
        Vector3f x = new Vector3f(first).normalize();
        Vector3f y = new Vector3f(second).fma(-second.dot(x), x).normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return new Matrix3f(x, y, z);
    }

    /** The angle turning {@code from} onto {@code to} about {@code axis} (both taken square to it). */
    private static float signedAngle(Vector3f from, Vector3f to, Vector3f axis) {
        Vector3f f = new Vector3f(from).fma(-from.dot(axis), axis), g = new Vector3f(to).fma(-to.dot(axis), axis);
        return (float) Math.atan2(axis.dot(new Vector3f(f).cross(g)), f.dot(g));
    }

    /** Sets a bone's turn (GeckoLib's Z, Y, X order) and leaves its marks as they were (the bones are shared). */
    private static void set(GeoBone bone, Matrix3f turn) {
        // the matrix's own angles: JOML 1.10.5's quaternion getEulerAnglesZYX is wrong for big turns (the hind foot's
        // rest turn came back about 117 degrees off, the foot stood on its edge: the user's "back left leg is broken")
        Vector3f e = turn.getEulerAnglesZYX(new Vector3f());
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setRotX(e.x);
        bone.setRotY(e.y);
        bone.setRotZ(e.z);
        if (!marked) {
            bone.resetStateChanges();
        }
    }
}
