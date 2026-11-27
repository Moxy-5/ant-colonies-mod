package com.antcolonies.savanna.client;

import com.antcolonies.entity.body.DrawClock;
import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.savanna.animal.DrawnGround;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * A long standing body drawn on uneven ground (made for the Cape buffalo, shared with the lions 2026-11-25,
 * {@code docs/plans/lion.md}): drawn {@code drawAheadPx} toward its head (so the middle between its feet sits on its box's
 * middle), its trunk tilted to the ground under its front and hind feet (two ground heights, each eased over about 0.2 s,
 * {@link #EASE}): stepping up a block on Minecraft's own step its chest goes up first and its hips follow, instead of its
 * hind legs hanging in the air. The ground under each pair of feet is read as the shared {@link GroundBend#support} reads
 * it (a standing body tilts whole; GroundBend's chain of joints is for a body lying on what is under it).
 *
 * @param frontFeetPx how far ahead of its drawn middle its front feet are (model px)
 * @param hindFeetPx  how far behind it its hind feet are (model px)
 * @param feetOutPx   how far out to the sides its feet are (model px)
 * @param drawAheadPx how far toward its head it is drawn (model px; negative toward its tail)
 * @param maxTilt     the most the trunk tilts (degrees)
 */
public record StandTilt(double frontFeetPx, double hindFeetPx, double feetOutPx, double drawAheadPx, double maxTilt) {
    /**
     * The ground is read under the inner half of each foot (this much nearer the middle, model px): inside its box's span,
     * where nothing can be higher than the ground its box stands on, so only an edge its box hangs over tilts it.
     */
    private static final double READ_IN = 3.0;
    /** The ground under a pair of feet is taken at most this far under its box (blocks): past a drop it leans as down a step, then falls. */
    private static final double MOST_DOWN = 1.0;
    /** How quickly the ground under its feet is eased toward (seconds; settled in about 0.2 s). */
    private static final double EASE = 0.07;

    /**
     * Where its body is drawn ({@code root}, after its clips): drawn ahead, its trunk tilted about the middle between its
     * feet to the ground under its front and hind feet, and raised or lowered so both pairs stand on it.
     */
    public void place(LivingEntity animal, DrawnGround drawn, GeoBone root, double scale, double x, double y, double z, float yaw, float pt) {
        place(animal, drawn, root, scale, x, y, z, yaw, pt, false);
    }

    /**
     * @param level drawn level about its box, the ground not read (eased there as onto any ground): an animal held by
     *              another, or holding one. A holder is set on a spot of its prey's body as the prey's clips alone pose
     *              it ({@code Spots}, and the offline pair check measures her jaw against that same body), so a tilt of
     *              either would move the hide from under the jaw, a rump spot up to 10 px on a slope (code review R12-71)
     */
    public void place(LivingEntity animal, DrawnGround drawn, GeoBone root, double scale, double x, double y, double z, float yaw, float pt,
            boolean level) {
        if (root == null) {
            return;
        }
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double perPx = scale / 16.0;
        Level world = animal.level();
        boolean swims = animal.isInWater();
        double frontWant = y, hindWant = y;
        if (animal.isAlive() && !level) {
            frontWant = Mth.clamp(groundUnder(world, x, z, fx, fz, (frontFeetPx - READ_IN) * perPx, feetOutPx * perPx, y, swims), y - MOST_DOWN, y);
            hindWant = Mth.clamp(groundUnder(world, x, z, fx, fz, -(hindFeetPx - READ_IN) * perPx, feetOutPx * perPx, y, swims), y - MOST_DOWN, y);
        }
        // eased in world heights, so its box stepping up a block (all at once) never jolts the drawing
        long now = DrawClock.nanos(animal, pt);           // game time (the Motion Studio, phase 2, step 4)
        double dt = drawn.nanos == 0 ? 1.0 : (now - drawn.nanos) / 1.0E9;
        drawn.nanos = now;
        if (dt > 0.5 || Math.abs(drawn.frontY - frontWant) > 3.0 || Math.abs(drawn.hindY - hindWant) > 3.0) {
            drawn.frontY = frontWant;
            drawn.hindY = hindWant;
        } else if (dt > 0) {
            double k = 1.0 - Math.exp(-dt / EASE);
            drawn.frontY += (frontWant - drawn.frontY) * k;
            drawn.hindY += (hindWant - drawn.hindY) * k;
        }
        double front = drawn.frontY - y, hind = drawn.hindY - y;
        double stance = (frontFeetPx + hindFeetPx) * perPx;
        double maxSin = Math.sin(Math.toRadians(maxTilt));
        double tilt = Math.asin(Mth.clamp((front - hind) / stance, -maxSin, maxSin));        // its head end up is positive
        double liftPx = (front + hind) * 0.5 / perPx;
        // the turn about the feet's middle (model px M, drawAhead behind the origin at the ground) and the draw forward:
        // a point p goes to R(p - M) + lift, so the root moves by lift - R M (GeckoLib turns a bone's +x about X head up)
        double m = drawAheadPx;
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
}
