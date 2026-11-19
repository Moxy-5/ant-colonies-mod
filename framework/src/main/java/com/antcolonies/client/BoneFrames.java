package com.antcolonies.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Client, the body only: GeckoLib's own drawing sums for working out where a bone is drawn (the Animal Maker Engine,
 * 2026-11-19; first written for the feet-first tries' {@code LegReach}, kept on {@code claude/monitor-feet-first-tries}).
 * A bone is moved, taken to its pivot, turned about Z then Y then X, scaled and taken back from its pivot, inside its
 * parent (as {@code RenderUtil.prepMatrixForBone}); the model is drawn at the animal's place, turned by 180 less its
 * body's yaw and scaled. Model units here are blocks (model pixels over 16). Species-neutral.
 */
public final class BoneFrames {
    private BoneFrames() {
    }

    /** From the model to the world: the animal's drawn place, its body's yaw (degrees, as the renderer has it) and drawn size. */
    public static Matrix4f placement(double x, double y, double z, float yaw, double scale) {
        return new Matrix4f().translate((float) x, (float) y, (float) z).rotateY((float) Math.toRadians(180.0 - yaw))
                .scale((float) scale);
    }

    /** The bone's pivot (model blocks). */
    public static Vector3f pivot(GeoBone bone) {
        return new Vector3f(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()).div(16f);
    }

    /** How far the bone is moved now (model blocks, as GeckoLib draws it: its x the other way). */
    public static Vector3f offset(GeoBone bone) {
        return new Vector3f(-bone.getPosX(), bone.getPosY(), bone.getPosZ()).div(16f);
    }

    /** From the model to this bone's own frame, root first: its own move and turn in, as it is now or at rest. */
    public static Matrix4f chain(GeoBone bone, boolean rest) {
        if (bone == null) {
            return new Matrix4f();
        }
        return chain(bone.getParent(), rest).mul(local(bone, rest));
    }

    /** One bone's own step, as it is now or at rest. */
    public static Matrix4f local(GeoBone bone, boolean rest) {
        Vector3f p = pivot(bone);
        Matrix4f m;
        if (rest) {
            BoneSnapshot s = bone.getInitialSnapshot();
            m = new Matrix4f().translate(new Vector3f(-s.getOffsetX(), s.getOffsetY(), s.getOffsetZ()).div(16f)).translate(p)
                    .rotateZ(s.getRotZ()).rotateY(s.getRotY()).rotateX(s.getRotX()).scale(s.getScaleX(), s.getScaleY(), s.getScaleZ());
        } else {
            m = new Matrix4f().translate(offset(bone)).translate(p).rotateZ(bone.getRotZ()).rotateY(bone.getRotY())
                    .rotateX(bone.getRotX()).scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
        }
        return m.translate(-p.x, -p.y, -p.z);
    }

    /** A bone's own step with this move, pivot and turn (radians), unscaled. */
    public static Matrix4f local(Vector3f offset, Vector3f pivot, float rotZ, float rotY, float rotX) {
        return new Matrix4f().translate(offset).translate(pivot).rotateZ(rotZ).rotateY(rotY).rotateX(rotX)
                .translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /** Sets a bone's turn (radians) and leaves its marks as they were (the bones are shared by every animal drawn). */
    public static void setTurn(GeoBone bone, float x, float y, float z) {
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setRotX(x);
        bone.setRotY(y);
        bone.setRotZ(z);
        if (!marked) {
            bone.resetStateChanges();
        }
    }
}
