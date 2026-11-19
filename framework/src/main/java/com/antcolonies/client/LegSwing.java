package com.antcolonies.client;

import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A sprawling leg reaching its foot up or down (the Animal Maker Engine step 2, 2026-11-19): the upper leg swings up
 * or down at the shoulder or hip about the body's long axis, the way a lizard or a crocodile lifts a foot onto a ledge
 * (the upper arm sticks out sideways, so raising the elbow raises the foot), and the lower leg turns back by the same
 * so it keeps its slant; the foot moves with the elbow. In place of sliding the whole leg up or down out of its
 * shoulder (the old way, at most a few pixels before it looked broken off). Species-neutral: any animal whose upper
 * leg sticks out to the side and hangs from a bone turning only about its own width (a chest, hips).
 */
public final class LegSwing {
    /** How high above the shoulder the elbow can swing, and how far under it (degrees from level; my picks). */
    public static final double UP = 60.0, DOWN = 75.0;

    private LegSwing() {
    }

    /**
     * Swings the leg so its foot goes {@code up} model pixels higher (down if negative), as far as the swing allows,
     * leaving the bones' marks as they were (the elbow's place is the lower leg's pivot, as the model file has it).
     *
     * @return how far up it moved the foot (model pixels)
     */
    public static double reach(GeoBone upper, GeoBone lower, double up) {
        if (upper == null || lower == null || Math.abs(up) < 1.0E-3) {
            return 0.0;
        }
        Quaternionf was = new Quaternionf().rotationZYX(upper.getRotZ(), upper.getRotY(), upper.getRotX());
        // the shoulder-to-elbow line as the clip has it now, seen along the body (width, height)
        Vector3f arm = was.transform(new Vector3f(lower.getPivotX() - upper.getPivotX(), lower.getPivotY() - upper.getPivotY(),
                lower.getPivotZ() - upper.getPivotZ()));
        double r = Math.hypot(arm.x, arm.y);
        if (r < 1.0E-3) {
            return 0.0;
        }
        double from = Math.atan2(arm.y, arm.x);
        double lift = Mth.clamp((arm.y + up) / r, -Math.sin(Math.toRadians(DOWN)), Math.sin(Math.toRadians(UP)));
        double to = arm.x >= 0 ? Math.asin(lift) : Math.PI - Math.asin(lift);
        float swing = (float) Mth.wrapDegrees(Math.toDegrees(to - from));
        if (Math.abs(swing) < 1.0E-3F) {
            return 0.0;
        }
        Quaternionf turn = new Quaternionf().rotationZ((float) Math.toRadians(swing));
        Quaternionf now = new Quaternionf(turn).mul(was);
        // the lower leg keeps its slant against the body: its turn against the swung upper leg is the old one's undone
        Quaternionf lowerWas = new Quaternionf().rotationZYX(lower.getRotZ(), lower.getRotY(), lower.getRotX());
        Quaternionf lowerNow = new Quaternionf(now).conjugate().mul(was).mul(lowerWas);
        set(upper, now);
        set(lower, lowerNow);
        return r * Math.sin(to) - arm.y;
    }

    private static void set(GeoBone bone, Quaternionf q) {
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        Vector3f e = q.getEulerAnglesZYX(new Vector3f());
        bone.setRotX(e.x);
        bone.setRotY(e.y);
        bone.setRotZ(e.z);
        if (!marked) {
            bone.resetStateChanges();
        }
    }
}
