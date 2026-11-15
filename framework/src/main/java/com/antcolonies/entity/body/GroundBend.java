package com.antcolonies.entity.body;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The body only, for long animals that lie on what is under them (M9, the crocodile's life step 1, 2026-11-15): one rule,
 * "each piece of the body lies on what is under it", in place of a body stiff as a plank. A real crocodile going into the
 * water tips its head down over the edge while its tail still lies on the bank, and coming out rises nose first with its
 * back half still afloat; this gives both, and lying half in and half out, from the one rule.
 * <p>
 * The body is a chain of joints along its middle line, at fixed places in the model (model pixels, 16 a block; the
 * head toward -Z), split at an anchor that stays with the animal's own position: a front chain (anchor to the snout)
 * and a rear chain (anchor to the tail tip). Under each joint the ground is looked at (its own block column, so a bank,
 * a step, the water): dry ground is where that piece rests; over water the piece floats at the animal's own height if
 * it swims, else at its resting depth under the surface (shallow water: on the bottom). Each piece then points from
 * where the piece in front of it ended to the height under its far end (follow the leader), held within a joint's reach,
 * and eases there over about 0.15 s so a block's edge never snaps it; the shoulders rise onto ground higher than the
 * animal stands (a bank met from the water).
 * <p>
 * Species-neutral (a hippo, a monitor lizard or a big snake would use the same chain); nothing here decides anything,
 * it only works out the bend. Works on whatever thread draws: it only reads blocks.
 */
public final class GroundBend {
    /** How far one joint bends against the piece in front of it, and how steep a piece lies, at most (degrees; my picks). */
    public static final double MAX_JOINT = 30.0, MAX_STEEP = 50.0;
    /**
     * The ground under a joint is read this far in front of and behind it too, and the highest taken: a piece never sinks
     * into a step's edge it has not quite passed (first film: the snout dipped into the bank's grass before it was over
     * the water) (blocks).
     */
    private static final double SPREAD = 0.4;
    /** How quickly a piece eases toward its new lie (seconds; my pick). */
    private static final double EASE = 0.15;
    /**
     * How far the shoulders rise onto ground higher than the animal stands, at most (blocks): swimming up to a bank, the
     * forelegs push the chest up onto it while the hips still float (first film: with the shoulders held at the animal's
     * own height, the head pushed into the bank).
     */
    public static final double MAX_LIFT = 1.6;

    /** The front chain's joints, the anchor first, then forward to the snout (model z, pixels); likewise the rear chain to the tail tip. */
    private final double[] front, rear;
    /** Each piece's lie now (degrees; its far end up is positive), front chain then rear chain. */
    private final double[] lie;
    /** How far the shoulders are lifted now (blocks). */
    private double lift;
    private long lastNanos;
    /** Where the animal was and which way it pointed at the last {@link #update} (for {@link #footing}). */
    private double atX, atY, atZ, alongX, alongZ = 1;

    public GroundBend(double[] front, double[] rear) {
        this.front = front.clone();
        this.rear = rear.clone();
        this.lie = new double[(front.length - 1) + (rear.length - 1)];
    }

    /** How many pieces the front chain has (its pieces come first in {@link #turn}). */
    public int frontPieces() {
        return front.length - 1;
    }

    /** How many pieces the rear chain has. */
    public int rearPieces() {
        return rear.length - 1;
    }

    /**
     * Works out the body's lie for this frame.
     *
     * @param x           the animal's position (blocks; the drawn, in-between one)
     * @param y           the height of what it lies on (its feet)
     * @param z           its position
     * @param yawDeg      which way its body points (Minecraft's degrees)
     * @param scale       how long one model pixel's sixteenth is drawn: model pixels times scale over 16 is blocks
     * @param inWater     whether the animal itself is in the water (swimming: floating pieces at its own height)
     * @param floatDepth  how deep under the surface its feet hang when it rests afloat (blocks)
     * @param alive       false for a dying animal: its body straightens (its death clip turns it over)
     */
    public void update(BlockGetter level, double x, double y, double z, float yawDeg, double scale, boolean inWater,
            double floatDepth, boolean alive, long nanos) {
        double dt = lastNanos == 0 ? 1.0 : (nanos - lastNanos) / 1.0E9;
        lastNanos = nanos;
        double k = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / EASE);
        double fx = -Mth.sin(yawDeg * Mth.DEG_TO_RAD), fz = Mth.cos(yawDeg * Mth.DEG_TO_RAD);
        double perBlock = scale / 16.0;
        atX = x;
        atY = y;
        atZ = z;
        alongX = fx;
        alongZ = fz;
        // the shoulders (the anchor) lie on what is under them, never lower than the animal itself stands
        double wantLift = alive ? Mth.clamp(under(level, x, z, fx, fz, -front[0] * perBlock, y, inWater, floatDepth) - y, 0, MAX_LIFT) : 0;
        lift += (wantLift - lift) * k;
        int i = 0;
        for (double[] chain : new double[][] {front, rear}) {
            int n = chain.length - 1;
            double[] targets = new double[n];
            double[] lengths = new double[n];
            for (int j = 0; j < n; j++) {
                targets[j] = under(level, x, z, fx, fz, -chain[j + 1] * perBlock, y, inWater, floatDepth);
                lengths[j] = Math.abs(chain[j + 1] - chain[j]) * perBlock;
            }
            double[] want = new double[n];
            if (alive) {
                solve(y + wantLift, targets, lengths, MAX_JOINT, MAX_STEEP, want);
            }
            for (int j = 0; j < n; j++, i++) {
                lie[i] += (want[j] - lie[i]) * k;
            }
        }
    }

    /** The highest resting height round a point this far forward of the animal along its body (blocks). */
    private static double under(BlockGetter level, double x, double z, double fx, double fz, double along, double y, boolean inWater,
            double floatDepth) {
        double h = Double.NEGATIVE_INFINITY;
        for (int s = -1; s <= 1; s++) {
            double a = along + s * SPREAD;
            h = Math.max(h, support(level, x + fx * a, z + fz * a, y, inWater, floatDepth));
        }
        return h;
    }

    /**
     * Whether feet this far forward of the animal along its body (blocks; behind it is negative) find a footing, as
     * worked out at the last {@link #update}: ground no more than {@code reach} under the body's own height (the animal's
     * feet, lifted with the shoulders), with no water over it deeper than {@code wade}. Over deep water, or past a drop,
     * there is nothing to stand on.
     */
    public boolean footing(BlockGetter level, double along, double reach, double wade) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(atX + alongX * along), bz = Mth.floor(atZ + alongZ * along);
        double feet = atY + lift;
        int by = Mth.floor(feet + 0.3);
        for (int up = 0; up < 2 && top(level, p.set(bx, by, bz)) > 0.5; up++) {
            by++;
        }
        double surface = Double.NaN;
        for (int c = by; c >= Mth.floor(feet - reach); c--) {
            p.set(bx, c, bz);
            FluidState fluid = level.getFluidState(p);
            if (Double.isNaN(surface) && fluid.is(FluidTags.WATER)) {
                surface = c + fluid.getHeight(level, p);
            }
            double t = top(level, p);
            if (t >= 0) {
                double ground = c + t;
                return ground >= feet - reach && (Double.isNaN(surface) || surface - ground <= wade);
            }
        }
        return false;
    }

    /** How far the shoulders are lifted above the animal's own height (blocks): the whole body is raised by it before it bends. */
    public double lift() {
        return lift;
    }

    /**
     * Each bone's own turn up (degrees) against the bone it hangs from, piece by piece: front chain then rear chain. For
     * the first piece of a chain that is its whole lie; for the rest, how far it lies steeper than the piece before.
     * (In a GeckoLib model a turn of +a about X lifts a bone that points to -Z, the head's way, and lowers one that
     * points to +Z, the tail's: so front bones take +turn, rear bones -turn, in radians.)
     */
    public double turn(int piece) {
        int f = frontPieces();
        boolean first = piece == 0 || piece == f;
        return first ? lie[piece] : lie[piece] - lie[piece - 1];
    }

    /**
     * Follow the leader: from the anchor's height, each piece tips so that its far end comes to the height wanted under
     * it (as near as its length allows), from where the piece before it ended, within a joint's reach of that piece (the first piece of a chain, hanging from the stiff
     * body, only by the steepness) and never steeper than {@code maxSteep}.
     *
     * @param out each piece's lie (degrees, its far end up is positive)
     */
    public static void solve(double anchorY, double[] targets, double[] lengths, double maxJoint, double maxSteep, double[] out) {
        double h = anchorY;
        double before = 0;
        for (int j = 0; j < targets.length; j++) {
            double angle = Math.toDegrees(Math.asin(Mth.clamp((targets[j] - h) / lengths[j], -1.0, 1.0)));
            if (j > 0) {            // the first piece hangs from the stiff body, which a long neck or a chest turns far on
                angle = Mth.clamp(angle, before - maxJoint, before + maxJoint);
            }
            angle = Mth.clamp(angle, -maxSteep, maxSteep);
            out[j] = angle;
            h += lengths[j] * Math.sin(Math.toRadians(angle));
            before = angle;
        }
    }

    /**
     * The height a piece of the body rests at in this block column, near the height {@code y} the animal itself lies at:
     * the top of the ground under it (climbing out of a bank higher than the body, at most 3 blocks, and looking down at
     * most 5); over water it floats, at the animal's own height if the animal swims, else at {@code floatDepth} under the
     * surface; never under the ground beneath the water.
     */
    public static double support(BlockGetter level, double x, double z, double y, boolean selfInWater, double floatDepth) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int by = Mth.floor(y + 0.3);
        for (int up = 0; up < 3 && top(level, p.set(bx, by, bz)) > 0.5; up++) {
            by++;
        }
        double ground = by - 5;
        double surface = Double.NaN;
        for (int c = by; c > by - 5; c--) {
            p.set(bx, c, bz);
            FluidState fluid = level.getFluidState(p);
            if (Double.isNaN(surface) && fluid.is(FluidTags.WATER)) {
                surface = c + fluid.getHeight(level, p);
            }
            double t = top(level, p);
            if (t >= 0) {
                ground = c + t;
                break;
            }
        }
        if (Double.isNaN(surface)) {
            return ground;
        }
        return Math.max(ground, selfInWater ? y : surface - floatDepth);
    }

    /** The top of what one stands on in this block (0 to 1), or -1 if nothing. */
    private static double top(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() ? -1 : shape.max(net.minecraft.core.Direction.Axis.Y);
    }
}
