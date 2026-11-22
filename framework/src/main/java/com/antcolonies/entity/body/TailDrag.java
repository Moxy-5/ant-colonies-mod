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
 * <li>Coming down a block (stage C, 2026-11-19), the tail still on top behind the hips (it went through the block):
 * each joint is already high enough that the tail behind it can slant up over the ground behind no steeper than its
 * steepest ({@link #floor}), so it runs up from the hips over the edge; where the ground still forces a piece up, it
 * tips up only as far as it must and the piece after it may fold back down over the top, each bend short of a right
 * angle ({@link #MAX_FOLD}: past it the drawn piece turned over, the user saw the tail "flipping upside down"). Over
 * ground rising behind the hips a joint lies on it, no higher than it would over the ground under the hips (the base's
 * carried rest heights lifted it into an arch); with the hips tipped nose down holding the tail's root up, the root may
 * bend down twice as far (it arched up behind them: the user, "the back popping out when its going down").</li>
 * <li>On flat ground, going straight, every joint lies exactly at its rest place: the tail looks as the clips made it.</li>
 * <li>Over water (a ground that says where it floats, {@link Ground#afloat}; step 4, 2026-11-19) a joint eases to lie along
 * the surface instead: dragged into the water the tail slides off the bank and floats out straight behind, as a swimming
 * tail lies, and coming out it is dragged up the bank from there.</li>
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

        /**
         * Over water, the height a joint of the tail floats at here (blocks): it lies along the surface there instead of
         * resting on the bottom; NaN where there is no water (the Animal Maker Engine step 4, 2026-11-19).
         */
        default double afloat(double x, double z) {
            return Double.NaN;
        }

        /** The level's ground near the animal's own height {@code y}, as {@link GroundBend#support} reads it (water: afloat). */
        static Ground of(BlockGetter level, double y, boolean inWater, double floatDepth) {
            return (x, z) -> GroundBend.support(level, x, z, y, inWater, floatDepth);
        }

        /**
         * The level's ground under any water near the animal's own height {@code y}, and over water the tail afloat, its
         * middle line {@code under} blocks under the surface: a tail dragged into the water slides off the bank and floats.
         */
        static Ground floating(BlockGetter level, double y, double under) {
            return new Ground() {
                @Override
                public double at(double x, double z) {
                    return GroundBend.support(level, x, z, y, false, Double.MAX_VALUE);
                }

                @Override
                public double afloat(double x, double z) {
                    return GroundBend.surface(level, x, z, y) - under;
                }
            };
        }
    }

    /** How far a joint bends up or down against the piece in front, sideways, and how steep a piece lies, at most (degrees; my picks). */
    public static final double MAX_JOINT = 30.0, MAX_SIDE = 20.0, MAX_STEEP = 60.0;
    /**
     * How far a joint bends at most where the ground forces it (up a block's face, back over its top; degrees): short
     * of a right angle, as the drawing turns a piece bent that far against the one in front over (DraggedTail).
     */
    public static final double MAX_FOLD = 85.0;
    /** The rise a block along the steepest a piece lies (tan of {@link #MAX_STEEP}). */
    private static final double SLANT = Math.tan(Math.toRadians(MAX_STEEP));
    /** How quickly a joint eases to its height, the tail eases back to straight, and the drag takes over from the clip or hands back (seconds; my picks). */
    static final double EASE = 0.15, STRAIGHTEN = 3.0, WEIGH = 0.25;
    /** How fast ground past the tail's own reach stops holding a joint up (blocks lower a block further: my pick). */
    static final double BEYOND = 3.0;
    /**
     * How high a lip over the water a dragged tail bridges as a rope, as a share of its whole length: in full up to this,
     * fading out by twice it, over a higher bank it hangs down the face (my pick: about half a block for the monitor).
     */
    static final double BRIDGE = 0.2;
    /** Moved farther than this in a frame (blocks: a teleport, a knockback), or not drawn for half a second: laid out afresh. */
    static final double RESET = 2.0;

    private final int n;
    /** Each piece's length, each joint's height over flat ground at rest, and the least it lies over the ground (model pixels; the joint at a piece's far end). */
    private final double[] lengthPx, restPx, lowestPx;
    /** The root's own height over flat ground at rest (model pixels); NaN: not known (no ceiling from the root). */
    private final double rootPx;
    /** The joints in the world, the base first (blocks). */
    private final double[] x, y, z;
    /** Each joint's sideways bend from this frame's drag (degrees; a scratch row). */
    private final double[] bend;
    private double weight, home;
    private long last;
    private boolean placed;

    /**
     * @param lengthPx each piece's length, base to tip (model pixels)
     * @param restPx   the height of each piece's far end over flat ground in the model's rest pose (model pixels)
     * @param lowestPx the least each piece's far end lies over the ground: its own thickness (model pixels)
     */
    public TailDrag(double[] lengthPx, double[] restPx, double[] lowestPx) {
        this(lengthPx, restPx, lowestPx, Double.NaN);
    }

    /**
     * @param rootPx the root's own height over flat ground at rest (model pixels): over ground, no joint aims higher
     *               than the root now carries it ({@link #update}); NaN for none
     */
    public TailDrag(double[] lengthPx, double[] restPx, double[] lowestPx, double rootPx) {
        this.rootPx = rootPx;
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
        boolean forced = false;
        // the ground under the hips, eased: over ground rising behind them the tail lies on it, no higher than over this
        // (eased, as the root crossing a block's face would move it a block at once and snap the tail)
        double ground0 = ground.at(bx, bz);
        home = fresh ? ground0 : home + (ground0 - home) * k;
        double behind = 0;          // the tail's length behind the joint being placed (blocks)
        for (int i = 0; i < n; i++) {
            behind += lengthPx[i] * perBlock;
        }
        double total = behind;
        for (int i = 1; i <= n; i++) {
            double len = lengthPx[i - 1] * perBlock;
            behind -= len;
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
            double floor = floor(ground, px, pz, ux, uz, lowestPx[i - 1] * perBlock, len, behind);
            // over water it floats along the surface (as the swimming tail lies), else it lies on the ground as at rest
            double wet = ground.afloat(px, pz);
            // (no higher than a little over the hips, as it lies swimming at the surface: an animal swimming under, hiding after a scare, keeps its
            // tail down with it; floated to the surface, the tail zig-zagged up from the sunk hips and snapped, 2026-11-19)
            double rest = Double.isNaN(wet) ? Math.min(under, home) + restPx[i - 1] * perBlock : Math.min(wet, by + 0.25 * lengthPx[0] * perBlock);
            // (and while the tail behind still lies on the bank it came off, no lower than the straight line from the joint
            // in front to the lip, as a rope dragged over an edge: dropped to the surface, it climbed back out over a low
            // bank's lip, a W from the side, 2026-11-22, BUGS 278). Only a low lip is bridged: over a high one the tail
            // hangs down the face as before ({@link #BRIDGE}; laid as a rope up to a bank two blocks high, its end
            // flipped over the top as it slid off).
            if (!Double.isNaN(wet)) {
                double d = lip(ground, x[i - 1], z[i - 1], ux, uz, len + behind);
                if (!Double.isNaN(d)) {
                    double top = ground.at(x[i - 1] + ux * d, z[i - 1] + uz * d) + lowestPx[i - 1] * perBlock;
                    double line = y[i - 1] + (top - y[i - 1]) * Math.min(1.0, reach / d);
                    double bridged = Mth.clamp(2.0 - (top - rest) / (BRIDGE * total), 0.0, 1.0);
                    rest = Math.max(rest, rest + (line - rest) * bridged);
                }
            }
            // (over ground, no higher than the root now carries it: as far under the root as at rest. The hips sinking
            // over a bank's lip into the water, the base pieces stayed at their rest height over the bank and the tail
            // arched up from its own root, 2026-11-22. On flat ground and climbing the root is at or over its rest
            // height, so this never holds a joint lower there.)
            if (Double.isNaN(wet) && !Double.isNaN(rootPx)) {
                rest = Math.min(rest, by - (rootPx - restPx[i - 1]) * perBlock);
            }
            double height = Math.max(y[i] + (rest - y[i]) * k, floor);
            // (after a piece the ground raised, this one may fold back down over the top)
            // (and with the hips tipped nose down, holding its root up, the root may bend down twice as far)
            double down = i == 1 && before > 0 ? 2 * MAX_JOINT : MAX_JOINT;
            double lo = Math.max(before - (forced ? MAX_FOLD : down), -MAX_STEEP), hi = Math.min(before + MAX_JOINT, MAX_STEEP);
            double angle = Mth.clamp(Math.toDegrees(Math.asin(Mth.clamp((height - y[i - 1]) / len, -1.0, 1.0))), lo, hi);
            // never into the ground: no steeper down than the joints after it can level out from, above the ground here
            while (angle < hi && y[i - 1] + len * Math.sin(Math.toRadians(angle)) - levelling(angle, len) < floor) {
                angle = Math.min(hi, angle + 2.0);
            }
            // and where its bend cannot keep it up (the ground rising behind), the ground wins over the bend and the
            // steepest: the piece tips up only as far as it must for its far end, where it really ends up, to lie over
            // the ground there (short of a right angle against the one in front, MAX_FOLD)
            double cap = Math.max(angle, Math.min(before + MAX_FOLD, MAX_FOLD));
            forced = false;
            for (;;) {
                double r = len * Math.cos(Math.toRadians(angle));
                double need = floor(ground, x[i - 1] + ux * r, z[i - 1] + uz * r, ux, uz, lowestPx[i - 1] * perBlock, len, behind);
                if (y[i - 1] + len * Math.sin(Math.toRadians(angle)) >= need - 1.0E-9) {
                    break;
                }
                forced = true;
                floor = need;
                if (angle >= cap) {
                    break;
                }
                angle = Math.min(cap, angle + 2.0);
            }
            double flat = len * Math.cos(Math.toRadians(angle));
            x[i] = x[i - 1] + ux * flat;
            y[i] = y[i - 1] + len * Math.sin(Math.toRadians(angle));
            // raised by the ground (rising, held at the least height the ground behind allows): the next may fold over
            forced |= angle > 0 && y[i] <= floor + 0.05 * len;
            z[i] = z[i - 1] + uz * flat;
            ax = ux;
            az = uz;
            before = angle;
        }
        placed = true;
    }

    /**
     * The least height a joint at (x, z) lies at, its tail going on back along (ux, uz): its thickness over the ground
     * there, and over ground rising behind it (a block it came down off) high enough already that the tail behind can
     * slant up over it no steeper than its steepest, rather than having to stand up the face at the last joint; only
     * ground the tail behind the joint ({@code behind}, blocks) still reaches counts in full, past it less and less
     * ({@link #BEYOND}; pulled off a bank into the water, the tail's end stood propped up against the face for a top it
     * no longer reached, 2026-11-19).
     */
    private static double floor(Ground ground, double x, double z, double ux, double uz, double lowest, double len, double behind) {
        double floor = ground.at(x, z) + lowest;
        // the ground changes only at block borders: read it just past each one the tail crosses within reach
        double most = 3.0 * len, dx = x, dz = z, d = 0;
        while (true) {
            double toX = ux > 1.0E-9 ? (Math.floor(dx) + 1 - dx) / ux : ux < -1.0E-9 ? (Math.ceil(dx) - 1 - dx) / ux : Double.MAX_VALUE;
            double toZ = uz > 1.0E-9 ? (Math.floor(dz) + 1 - dz) / uz : uz < -1.0E-9 ? (Math.ceil(dz) - 1 - dz) / uz : Double.MAX_VALUE;
            double step = Math.min(toX, toZ);
            if (step <= 0) {
                step = 1.0E-6;
            }
            d += step;
            if (d > most) {
                return floor;
            }
            dx = x + ux * (d + 1.0E-6);
            dz = z + uz * (d + 1.0E-6);
            // (ground past what the tail behind reaches counts less and less, smoothly: cut off, the tip snapped)
            floor = Math.max(floor, ground.at(dx, dz) + lowest - d * SLANT - Math.max(0.0, d - behind) * BEYOND);
        }
    }

    /**
     * How far back from (x, z) along (ux, uz) the water ends at dry ground (blocks, just past that block border), within
     * {@code most}; NaN where (x, z) is itself dry or the water goes on past it.
     */
    private static double lip(Ground ground, double x, double z, double ux, double uz, double most) {
        if (Double.isNaN(ground.afloat(x, z))) {
            return Double.NaN;
        }
        double dx = x, dz = z, d = 0;
        while (true) {
            double toX = ux > 1.0E-9 ? (Math.floor(dx) + 1 - dx) / ux : ux < -1.0E-9 ? (Math.ceil(dx) - 1 - dx) / ux : Double.MAX_VALUE;
            double toZ = uz > 1.0E-9 ? (Math.floor(dz) + 1 - dz) / uz : uz < -1.0E-9 ? (Math.ceil(dz) - 1 - dz) / uz : Double.MAX_VALUE;
            d += Math.max(Math.min(toX, toZ), 1.0E-6) + 1.0E-6;
            if (d > most) {
                return Double.NaN;
            }
            dx = x + ux * d;
            dz = z + uz * d;
            if (Double.isNaN(ground.afloat(dx, dz))) {
                return d;
            }
        }
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
