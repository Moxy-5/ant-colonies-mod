package com.antcolonies.entity.body;

import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;

/**
 * The body only: a tail dragged behind its animal (the Animal Maker Engine step 2, stage A, 2026-11-19; plan
 * {@code docs/plans/engine-step2-climb.md}). What lies behind an animal changes (flat ground, a step, a drop, water), so
 * no clip can own its tail: the tail is a chain of joints, each a point held in the world.
 * <ul>
 * <li>The first joint rides on the hips. Each next joint is pulled after the one in front of it to keep its piece's
 * length, from where it was: so the tail trails along the way the hips went (round a turn it sweeps after the body
 * instead of swinging round with it like a stick) and eases slowly back to straight ({@link #STRAIGHTEN}).</li>
 * <li>Each joint's height is a height in the world, eased ({@link #EASE}) toward lying on what is under it as the
 * model's own tail lies on flat ground (its rest heights: the base carried, the rest on the ground), never lower than
 * its lowest (its own thickness) over the ground there, each joint bent at most {@link #MAX_JOINT} up or down and
 * {@link #MAX_SIDE} sideways. Going down, no piece lies steeper than the joints after it can level out from above the
 * ground; where the bends cannot keep a joint out of the ground, the ground wins. When the hips go up a block, the
 * joints behind keep their places and are pulled up after them: the tail hangs from the hips and slides up over the
 * edge; off a drop it drapes from the edge and falls softly.</li>
 * <li>On flat ground, going straight, every joint lies exactly at its rest place: the tail looks as the clips made it.</li>
 * </ul>
 * Species-neutral (any tail of any number of pieces; a lizard's, a crocodile's, later a rat's); it only reads the
 * ground. Nothing here decides anything. One per animal; drawing only.
 */
public final class TailDrag {
    /** What a tail piece rests on. */
    @FunctionalInterface
    public interface Ground {
        /** The height a piece of the tail rests at, at this place (blocks). */
        double at(double x, double z);

        /** The level's ground near the animal's own height {@code y}, as {@link GroundBend#support} reads it (water: afloat). */
        static Ground of(BlockGetter level, double y, boolean inWater, double floatDepth) {
            return (x, z) -> GroundBend.support(level, x, z, y, inWater, floatDepth);
        }
    }

    /** How far a joint bends up or down against the piece in front, sideways, and how steep a piece lies, at most (degrees; my picks). */
    public static final double MAX_JOINT = 30.0, MAX_SIDE = 20.0, MAX_STEEP = 60.0;
    /** How quickly a joint eases to its height, the tail eases back to straight, and the drag takes over from the clip or hands back (seconds; my picks). */
    static final double EASE = 0.15, STRAIGHTEN = 3.0, WEIGH = 0.25;
    /** Moved farther than this in a frame (blocks: a teleport, a knockback), or not drawn for half a second: laid out afresh. */
    static final double RESET = 2.0;

    private final int n;
    /** Each piece's length, each joint's height over flat ground at rest, and the least it lies over the ground (model pixels; the joint at a piece's far end). */
    private final double[] lengthPx, restPx, lowestPx;
    /** The joints in the world, the base first (blocks). */
    private final double[] x, y, z;
    /** Each joint's sideways bend from this frame's drag (degrees; a scratch row). */
    private final double[] bend;
    private double weight;
    private long last;
    private boolean placed;

    /**
     * @param lengthPx each piece's length, base to tip (model pixels)
     * @param restPx   the height of each piece's far end over flat ground in the model's rest pose (model pixels)
     * @param lowestPx the least each piece's far end lies over the ground: its own thickness (model pixels)
     */
    public TailDrag(double[] lengthPx, double[] restPx, double[] lowestPx) {
        n = lengthPx.length;
        if (restPx.length != n || lowestPx.length != n) {
            throw new IllegalArgumentException("a length, a rest height and a lowest height for each piece");
        }
        this.lengthPx = lengthPx.clone();
        this.restPx = restPx.clone();
        this.lowestPx = new double[n];
        for (int i = 0; i < n; i++) {
            this.lowestPx[i] = Math.min(lowestPx[i], restPx[i]);    // at rest it lies where the model has it, never pushed up
        }
        x = new double[n + 1];
        y = new double[n + 1];
        z = new double[n + 1];
        bend = new double[n];
    }

    /** How many pieces. */
    public int pieces() {
        return n;
    }

    /** Joint {@code i} in the world (0 the base on the hips, {@link #pieces()} the tip; blocks). */
    public double x(int i) {
        return x[i];
    }

    public double y(int i) {
        return y[i];
    }

    public double z(int i) {
        return z[i];
    }

    /** How much the drag has the tail now (1) against its clip (0), eased. */
    public double weight() {
        return weight;
    }

    /**
     * Drags the tail after its base for this frame.
     *
     * @param bx       the base joint, on the hips (world, blocks)
     * @param hx       which way the hips point backward, along the ground (any length; the tail's straight line)
     * @param pitch    how the hips lie toward the tail (degrees, the tail end up positive)
     * @param perBlock blocks per model pixel at the animal's drawn size
     * @param on       whether the drag should have the tail (else its clip has it)
     */
    public void update(Ground ground, double bx, double by, double bz, double hx, double hz, double pitch, double perBlock, boolean on,
            long nanos) {
        double dt = last == 0 ? 1.0 : (nanos - last) / 1.0E9;
        last = nanos;
        double moved = (bx - x[0]) * (bx - x[0]) + (by - y[0]) * (by - y[0]) + (bz - z[0]) * (bz - z[0]);
        boolean fresh = !placed || dt > 0.5 || moved > RESET * RESET;
        double k = fresh ? 1.0 : ease(dt, EASE), straighten = fresh ? 1.0 : ease(dt, STRAIGHTEN);
        double want = on ? 1.0 : 0.0;
        weight = fresh ? want : weight + (want - weight) * ease(dt, WEIGH);
        double h = Math.sqrt(hx * hx + hz * hz);
        if (h < 1.0E-9) {
            hx = 0;
            hz = 1;
        } else {
            hx /= h;
            hz /= h;
        }
        if (fresh) {        // straight back at the base's height; the joints settle onto their ground below at once (k = 1)
            x[0] = bx;
            y[0] = by;
            z[0] = bz;
            double along = 0;
            for (int i = 1; i <= n; i++) {
                along += lengthPx[i - 1] * perBlock;
                x[i] = bx + hx * along;
                y[i] = by;
                z[i] = bz + hz * along;
            }
        }
        // the drag, along the ground: each joint pulled after the one in front of it from where it was, keeping its
        // piece's length along the ground, bent at most MAX_SIDE. Worked out on its own first, so that easing the bends
        // straight below does not itself drag the joints behind (which only moved the curl down the tail).
        double fx = bx, fz = bz, ax = hx, az = hz;
        for (int i = 1; i <= n; i++) {
            double rx = x[i] - x[i - 1], rz = z[i] - z[i - 1], r = Math.sqrt(rx * rx + rz * rz);
            double dx = x[i] - fx, dz = z[i] - fz;
            double side = dx * dx + dz * dz < 1.0E-18 ? 0.0
                    : Mth.clamp(Math.toDegrees(Math.atan2(ax * dz - az * dx, ax * dx + az * dz)), -MAX_SIDE, MAX_SIDE);
            bend[i - 1] = side;
            double c = Math.cos(Math.toRadians(side)), s = Math.sin(Math.toRadians(side));
            double ux = ax * c - az * s, uz = ax * s + az * c;
            fx += ux * r;
            fz += uz * r;
            ax = ux;
            az = uz;
        }
        x[0] = bx;
        y[0] = by;
        z[0] = bz;
        ax = hx;
        az = hz;
        double before = Mth.clamp(pitch, -MAX_STEEP, MAX_STEEP);
        for (int i = 1; i <= n; i++) {
            double len = lengthPx[i - 1] * perBlock;
            // its bend from the drag, eased a little back toward straight
            double side = bend[i - 1] * (1.0 - straighten);
            double c = Math.cos(Math.toRadians(side)), s = Math.sin(Math.toRadians(side));
            double ux = ax * c - az * s, uz = ax * s + az * c;
            // its height: eased toward lying on what is under it as at rest, never into the ground
            double rise = y[i] - y[i - 1];
            double reach = Math.sqrt(Math.max(0.0, len * len - rise * rise));
            double px = x[i - 1] + ux * reach, pz = z[i - 1] + uz * reach, spread = 0.5 * len;
            double here = ground.at(px, pz);
            double under = Math.max(here, Math.max(ground.at(px + ux * spread, pz + uz * spread), ground.at(px - ux * spread, pz - uz * spread)));
            double floor = here + lowestPx[i - 1] * perBlock;
            double height = Math.max(y[i] + (under + restPx[i - 1] * perBlock - y[i]) * k, floor);
            double lo = Math.max(before - MAX_JOINT, -MAX_STEEP), hi = Math.min(before + MAX_JOINT, MAX_STEEP);
            double angle = Mth.clamp(Math.toDegrees(Math.asin(Mth.clamp((height - y[i - 1]) / len, -1.0, 1.0))), lo, hi);
            // never into the ground: no steeper down than the joints after it can level out from, above the ground here
            while (angle < hi && y[i - 1] + len * Math.sin(Math.toRadians(angle)) - levelling(angle, len) < floor) {
                angle = Math.min(hi, angle + 2.0);
            }
            // and where its bend cannot keep it up (the ground rising behind), the ground wins over the bend
            if (y[i - 1] + len * Math.sin(Math.toRadians(angle)) < floor) {
                angle = Math.min(MAX_STEEP, Math.toDegrees(Math.asin(Mth.clamp((floor - y[i - 1]) / len, -1.0, 1.0))));
            }
            double flat = len * Math.cos(Math.toRadians(angle));
            x[i] = x[i - 1] + ux * flat;
            y[i] = y[i - 1] + len * Math.sin(Math.toRadians(angle));
            z[i] = z[i - 1] + uz * flat;
            ax = ux;
            az = uz;
            before = angle;
        }
        placed = true;
    }

    /** How much lower the tail goes after a piece lying {@code angle} down before it lies level again, each joint after it bending its most. */
    private static double levelling(double angle, double len) {
        double drop = 0;
        for (double a = angle + MAX_JOINT; a < 0; a += MAX_JOINT) {
            drop -= len * Math.sin(Math.toRadians(a));
        }
        return drop;
    }

    private static double ease(double dt, double time) {
        return dt <= 0 ? 0.0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / time);
    }
}
