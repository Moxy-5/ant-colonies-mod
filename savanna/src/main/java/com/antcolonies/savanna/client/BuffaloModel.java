package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BuffaloEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
 * {@link StandTilt}): stepping up a block on Minecraft's own step its chest goes up first and its hips follow, instead of its
 * hind legs hanging in the air (the shared {@link StandTilt}).</li>
 * <li>Its eyes and head on what caught its eye ({@link BuffaloEntity#attention}, as the hippo's): each pupil sits at the
 * inner side of its eye looking ahead and slides out across the white to look to its own side; the head turns a little
 * toward it, slowly. Pupils are never moved by the clips.</li>
 * </ul>
 */
public class BuffaloModel extends DefaultedEntityGeoModel<BuffaloEntity> {
    /**
     * Its trunk's tilt on uneven ground ({@link StandTilt}): its front and hind feet from the drawn middle (model px: the
     * geo's 3.2 ahead and 23.4 behind, moved forward by the draw), 5.75 out to the sides; drawn
     * {@link BuffaloEntity#DRAW_AHEAD_PX} toward its head; at most 35 degrees (my pick: a 1-block step under a bull's
     * 1.66-block stance asks 37).
     */
    private static final StandTilt TILT = new StandTilt(3.2 + BuffaloEntity.DRAW_AHEAD_PX, 23.4 - BuffaloEntity.DRAW_AHEAD_PX, 5.75,
            BuffaloEntity.DRAW_AHEAD_PX, 35.0);
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
        TILT.place(buffalo, buffalo.drawn, getAnimationProcessor().getBone("root"), scale, x, y, z, yaw, pt);
        eyes(buffalo, scale, pt, x, z, yaw);
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
        buffalo.attention.aim(buffalo, pt, x - Mth.sin(yaw * Mth.DEG_TO_RAD) * ahead, z + Mth.cos(yaw * Mth.DEG_TO_RAD) * ahead, yaw,
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
        if (neck != null && Math.abs(buffalo.attention.headAim) > 1.0E-3) {     // eased out, never dropped (phase 2, step 5)
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
