package com.antcolonies.savanna.client;

import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BuffaloEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Cape buffalo's model (2026-11-25, {@code docs/plans/buffalo.md}): Neon's clips as made, and three things on top of
 * them, all drawn only:
 * <ul>
 * <li>Drawn {@link BuffaloEntity#DRAW_AHEAD_PX} toward its head: the model's origin is at its front legs, so the middle
 * between its front and hind feet comes onto its box's middle (its box then covers its feet, not its front legs and belly).</li>
 * <li>Its trunk tilts to the ground under its front and hind feet (two ground heights, each eased over about 0.2 s,
 * {@link #EASE}): stepping up a block on Minecraft's own step its chest goes up first and its hips follow, instead of its
 * hind legs hanging in the air. The ground under each pair of feet is read as the shared {@link GroundBend#support} reads
 * it (a standing body tilts whole; GroundBend's chain of joints is for a body lying on what is under it).</li>
 * <li>Its eyes and head on what caught its eye ({@link BuffaloEntity#attention}, as the hippo's): each pupil sits at the
 * inner side of its eye looking ahead and slides out across the white to look to its own side; the head turns a little
 * toward it, slowly. Pupils are never moved by the clips.</li>
 * </ul>
 */
public class BuffaloModel extends DefaultedEntityGeoModel<BuffaloEntity> {
    /** Its front and hind feet, from the drawn middle (model px: the geo's 3.2 ahead and 23.4 behind, moved forward by the draw), and how far out to the sides. */
    private static final double FRONT_FEET = 3.2 + BuffaloEntity.DRAW_AHEAD_PX, HIND_FEET = 23.4 - BuffaloEntity.DRAW_AHEAD_PX, FEET_OUT = 5.75;
    /**
     * The ground is read under the inner half of each hoof (this much nearer the middle, model px): inside its box's span,
     * where nothing can be higher than the ground its box stands on, so only an edge its box hangs over tilts it (a wall or
     * a ledge in front of it never does).
     */
    private static final double READ_IN = 3.0;
    /** The ground under a pair of feet is taken at most this far under its box (blocks): past a drop it leans as down a step, then falls. */
    private static final double MOST_DOWN = 1.0;
    /** The trunk tilts at most this much (degrees; my pick: a 1-block step under a bull's 1.66-block stance asks 37). */
    private static final double MAX_TILT = 35.0;
    /** How quickly the ground under its feet is eased toward (seconds; settled in about 0.2 s). */
    private static final double EASE = 0.07;
    /**
     * How far forward of its middle its eyes are (model px, drawn), how far a pupil slides out across the white at most
     * (px; the white is 0.88 wide, the pupil 1.46), the eye's plane turned this far back toward its side (degrees, the
     * geo's), and how far the head turns toward what it watches (degrees). My picks.
     */
    private static final double EYES_AHEAD = 24.8 + BuffaloEntity.DRAW_AHEAD_PX, PUPIL_SLIDE = 0.7, EYE_PLANE = 32.0, HEAD_TURN = 20.0;

    public BuffaloModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "buffalo"));
    }

    @Override
    public void setCustomAnimations(BuffaloEntity buffalo, long instanceId, AnimationState<BuffaloEntity> state) {
        super.setCustomAnimations(buffalo, instanceId, state);
        float pt = state.getPartialTick();
        double x = Mth.lerp(pt, buffalo.xo, buffalo.getX()), y = Mth.lerp(pt, buffalo.yo, buffalo.getY());
        double z = Mth.lerp(pt, buffalo.zo, buffalo.getZ());
        float yaw = Mth.rotLerp(pt, buffalo.yBodyRotO, buffalo.yBodyRot);
        double scale = BuffaloEntity.MODEL_SCALE * buffalo.getScale();
        place(buffalo, scale, x, y, z, yaw);
        eyes(buffalo, scale, pt, x, z, yaw);
    }

    /**
     * Where its body is drawn: {@link BuffaloEntity#DRAW_AHEAD_PX} toward its head, its trunk tilted about the middle
     * between its feet to the ground under its front and hind feet, and raised or lowered so both pairs stand on it.
     */
    private void place(BuffaloEntity buffalo, double scale, double x, double y, double z, float yaw) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double perPx = scale / 16.0;
        Level level = buffalo.level();
        boolean swims = buffalo.isInWater();
        double frontWant = y, hindWant = y;
        if (buffalo.isAlive()) {
            frontWant = Mth.clamp(groundUnder(level, x, z, fx, fz, (FRONT_FEET - READ_IN) * perPx, FEET_OUT * perPx, y, swims), y - MOST_DOWN, y);
            hindWant = Mth.clamp(groundUnder(level, x, z, fx, fz, -(HIND_FEET - READ_IN) * perPx, FEET_OUT * perPx, y, swims), y - MOST_DOWN, y);
        }
        // eased in world heights, so its box stepping up a block (all at once) never jolts the drawing
        long now = System.nanoTime();
        double dt = buffalo.drawnNanos == 0 ? 1.0 : (now - buffalo.drawnNanos) / 1.0E9;
        buffalo.drawnNanos = now;
        if (dt > 0.5 || Math.abs(buffalo.drawnFrontY - frontWant) > 3.0 || Math.abs(buffalo.drawnHindY - hindWant) > 3.0) {
            buffalo.drawnFrontY = frontWant;
            buffalo.drawnHindY = hindWant;
        } else if (dt > 0) {
            double k = 1.0 - Math.exp(-dt / EASE);
            buffalo.drawnFrontY += (frontWant - buffalo.drawnFrontY) * k;
            buffalo.drawnHindY += (hindWant - buffalo.drawnHindY) * k;
        }
        double front = buffalo.drawnFrontY - y, hind = buffalo.drawnHindY - y;
        double stance = (FRONT_FEET + HIND_FEET) * perPx;
        double maxSin = Math.sin(Math.toRadians(MAX_TILT));
        double tilt = Math.asin(Mth.clamp((front - hind) / stance, -maxSin, maxSin));        // its head end up is positive
        double liftPx = (front + hind) * 0.5 / perPx;
        // the turn about the feet's middle (model px M, DRAW_AHEAD behind the origin at the ground) and the draw forward:
        // a point p goes to R(p - M) + lift, so the root moves by lift - R M (GeckoLib turns a bone's +x about X head up)
        double m = BuffaloEntity.DRAW_AHEAD_PX;
        GeoBone root = getAnimationProcessor().getBone("root");
        if (root == null) {
            return;
        }
        boolean marked = root.hasRotationChanged() || root.hasPositionChanged() || root.hasScaleChanged();
        root.setRotX((float) (root.getRotX() + tilt));
        root.setPosY((float) (root.getPosY() + m * Math.sin(tilt) + liftPx));
        root.setPosZ((float) (root.getPosZ() - m * Math.cos(tilt)));
        if (!marked) {
            root.resetStateChanges();
        }
    }

    /**
     * The ground under a pair of feet this far {@code along} its body from its middle (blocks, + forward), each foot
     * {@code out} to its side: the higher of the two, as {@link GroundBend#support} reads a column (in the water, never
     * under its own height: it swims level).
     */
    private static double groundUnder(Level level, double x, double z, double fx, double fz, double along, double out, double y, boolean swims) {
        double cx = x + fx * along, cz = z + fz * along;
        double left = GroundBend.support(level, cx - fz * out, cz + fx * out, y, swims, 0.0);
        double right = GroundBend.support(level, cx + fz * out, cz - fx * out, y, swims, 0.0);
        return Math.max(left, right);
    }

    /**
     * The eyes and head on what caught its eye: the pupil on the side it looks to slides out across its eye's white,
     * along the eye's plane (turned {@link #EYE_PLANE} back toward its side), darting there; the head turns a little
     * toward it, slowly (not in the clips that carry the head: the hits, lying down and getting up, dying).
     */
    private void eyes(BuffaloEntity buffalo, double scale, float pt, double x, double z, float yaw) {
        byte d = buffalo.doing();
        boolean turnHead = buffalo.isAlive() && d != BuffaloEntity.HEADBUTT && d != BuffaloEntity.GORE && d != BuffaloEntity.CHARGE_HEADBUTT
                && d != BuffaloEntity.REST_DOWN && d != BuffaloEntity.REST_UP;
        double ahead = EYES_AHEAD * scale / 16.0;
        buffalo.attention.aim(pt, x - Mth.sin(yaw * Mth.DEG_TO_RAD) * ahead, z + Mth.cos(yaw * Mth.DEG_TO_RAD) * ahead, yaw,
                turnHead ? HEAD_TURN : 0.0);
        if (!buffalo.isAlive()) {
            return;
        }
        double aim = buffalo.attention.eyeAim;          // degrees off ahead, its right positive
        double cos = Math.cos(Math.toRadians(EYE_PLANE)), sin = Math.sin(Math.toRadians(EYE_PLANE));
        // its left eye is the model's +x (a bone's x move is drawn the model's way); out to the side is back along the plane
        slide(getAnimationProcessor().getBone("pupil_left"), PUPIL_SLIDE * Mth.clamp(-aim / 90.0, 0.0, 1.0), cos, sin, 1);
        slide(getAnimationProcessor().getBone("pupil_right"), PUPIL_SLIDE * Mth.clamp(aim / 90.0, 0.0, 1.0), cos, sin, -1);
        GeoBone neck = getAnimationProcessor().getBone("neck");
        if (turnHead && neck != null && Math.abs(buffalo.attention.headAim) > 1.0E-3) {
            boolean marked = neck.hasRotationChanged() || neck.hasPositionChanged() || neck.hasScaleChanged();
            neck.setRotY(neck.getRotY() - (float) Math.toRadians(buffalo.attention.headAim));
            if (!marked) {
                neck.resetStateChanges();
            }
        }
    }

    /** Slides a pupil this far (model px) out along its eye's plane, toward its own side ({@code side} +1 left, -1 right). */
    private static void slide(GeoBone pupil, double by, double cos, double sin, int side) {
        if (pupil == null) {
            return;
        }
        boolean marked = pupil.hasRotationChanged() || pupil.hasPositionChanged() || pupil.hasScaleChanged();
        pupil.setPosX((float) (side * by * cos));
        pupil.setPosZ((float) (by * sin));
        if (!marked) {
            pupil.resetStateChanges();
        }
    }
}
