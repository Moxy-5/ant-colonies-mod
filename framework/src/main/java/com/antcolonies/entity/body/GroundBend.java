package com.antcolonies.entity.body;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The body only, for long animals that lie on what is under it (M9, the crocodile's life step 1, 2026-11-15): one rule,
 * "each piece of the body lies on what is under it", in place of a body stiff as a plank. A real crocodile going into the
 * water tips its head down over the edge while its tail still lies on the bank, and coming out rises nose first with its
 * back half still afloat; this gives both, and lying half in and half out, from the one rule.
 * <p>
 * The body is a chain of joints along its middle line, at fixed places in the model (model pixels, 16 a block; the
 * head toward -Z), split at an anchor: a front chain (anchor to the snout) and a rear chain (anchor to the tail tip).
 * <p>
 * <b>One smoothed ground line, in world heights (the Animal Maker Engine step 2, 2026-11-19).</b> Minecraft's ground is
 * one-block steps; a body following every block edge looks jank (the user: "blocks aren't realistic, so we will have to
 * get creative"). So the ground is read along the body's whole length (dry ground where a piece rests; over water it
 * floats, at the animal's own height if it swims, else at its resting depth under the surface) and made into one smooth
 * line: each step becomes a short ramp {@link #RAMP} of the body long, moved a little toward the low side
 * ({@link #CLEAR}) so the belly clears the block's corner. The anchor sits on that line and each piece points from where
 * the piece in front of it ended to the line under its far end (follow the leader), held within a joint's reach. The
 * anchor's height is kept as a height in the world, not against the animal's box: the box jumping a block in a tick or
 * three moves nothing that is drawn (the course's baseline, 2026-11-19: the old shoulder lift, kept against the box,
 * lifted the body early, then again as the box stepped, a 0.6-block bounce). The feet reach for the real ground: the
 * highest their toes find within {@link #TOES} ahead ({@link #groundNear}), so a foot at the foot of a step grips its
 * edge, as a climbing lizard's does.
 * <p>
 * Species-neutral (a hippo, a monitor lizard or a big snake would use the same chain); nothing here decides anything,
 * it only works out the bend. Works on whatever thread draws: it only reads blocks.
 */
public final class GroundBend {
    /** How far one joint bends against the piece in front of it, and how steep a piece lies, at most (degrees; my picks). */
    public static final double MAX_JOINT = 30.0, MAX_STEEP = 50.0;
    /** How quickly a piece eases toward its new lie, and the anchor toward its height (seconds; my pick). */
    private static final double EASE = 0.15;
    /**
     * How long the ramp a block step becomes is, either side of the edge, as a share of the body's length (my pick: about
     * a third of a block either way for the monitor, 0.6 for the crocodile). The first films (0.2 and 0.11, 0.9 and 0.5
     * blocks either way) had the chest rising before the forefeet reached the step, rearing up with its forefeet in the
     * air: the forefeet stand under the chest's front, so the ramp is kept short and each part of the body climbs about
     * when it gets to the edge.
     */
    static final double RAMP = 0.08;
    /** How far the ramp is moved toward the low side, as a share of {@link #RAMP}'s reach (my pick; the belly clears the corner). */
    static final double CLEAR = 0.3;
    /**
     * How far ahead of a foot its toes still find a footing (blocks; my pick, about a monitor's toes). The first try
     * averaged the ground over a quarter block either way: a foot planted at the foot of a step stood on 40% of the
     * step's height, in the air in front of it (the look films, 2026-11-19).
     */
    public static final double TOES = 0.3;
    /** The ground line is read at this many points a block. */
    private static final int GRID = 32;
    /**
     * How far the body is drawn above, and below, the animal's own height at most (blocks): swimming up to a bank the
     * forelegs push the chest up onto it while the hips still float; past a drop the front sinks ahead of the box.
     */
    public static final double MAX_LIFT = 1.6, MAX_DROP = 1.0;

    /** The front chain's joints, the anchor first, then forward to the snout (model z, pixels); likewise the rear chain to the tail tip. */
    private final double[] front, rear;
    /** Each piece's lie now (degrees; its far end up is positive), front chain then rear chain. */
    private final double[] lie;
    /**
     * The trunk: the front chain's first {@code trunkPieces} pieces each bend at most {@code trunk} degrees against the
     * piece behind (the first against the rear chain's first, the hips), so the body curves a little at each joint and the
     * neck and head take the rest of a dip (2026-11-18, the user's reports on the monitor: one chest tipping up to 50
     * degrees off the hips made a peak in its back, and one chest held to a small bend looked rigid); none, as before.
     */
    private final int trunkPieces;
    private final double trunk;
    /** How far the anchor is drawn above the animal's own height now (blocks; under it is negative). */
    private double lift;
    /** The anchor's height in the world, eased (NaN until the first update). */
    private double anchorY = Double.NaN;
    /** The ground line along the body: read, then widened toward the low side, then running totals (reused each frame). */
    private double[] read = new double[0], wide = new double[0], sums = new double[0];
    /** The block columns read this frame and their heights (each column read once). */
    private int[] colX = new int[16], colZ = new int[16];
    private double[] colH = new double[16];
    private int cols;
    private long lastNanos;
    /** Where the animal was and which way it pointed at the last {@link #update} (for {@link #footing}). */
    private double atX, atY, atZ, alongX, alongZ = 1;

    public GroundBend(double[] front, double[] rear) {
        this(front, rear, 0, MAX_JOINT);
    }

    /** As {@link #GroundBend(double[], double[])}, the front chain's first {@code trunkPieces} pieces a trunk (see {@link #trunk}). */
    public GroundBend(double[] front, double[] rear, int trunkPieces, double trunk) {
        this.trunkPieces = trunkPieces;
        this.trunk = trunk;
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
        // the ground line along the body, from behind the tail tip to beyond the snout (along: blocks, forward +)
        double tip = -front[front.length - 1] * perBlock, tail = -rear[rear.length - 1] * perBlock;
        int ramp = Math.max(1, (int) Math.round(RAMP * (tip - tail) * GRID)), widen = (int) Math.round(CLEAR * ramp);
        double from = tail - (double) (ramp + widen + 1) / GRID;
        int n = (int) Math.ceil((tip - from) * GRID) + ramp + widen + 2;
        if (read.length < n) {
            read = new double[n];
            wide = new double[n];
            sums = new double[n + 1];
        }
        cols = 0;
        for (int i = 0; i < n; i++) {
            double a = from + (double) i / GRID;
            read[i] = column(level, x + fx * a, z + fz * a, y, inWater, floatDepth);
        }
        line(read, n, widen, wide, sums);
        // the anchor's height is a height in the world: the box stepping a block moves nothing drawn
        double want = alive ? y + Mth.clamp(lineAt(from, n, ramp, -front[0] * perBlock) - y, -MAX_DROP, MAX_LIFT) : y;
        anchorY = Double.isNaN(anchorY) || Math.abs(want - anchorY) > 3.0 ? want : anchorY + (want - anchorY) * k;
        lift = anchorY - y;
        // the rear chain first, so the chest can be held within the trunk's bend of the hips
        double hips = 0;
        for (int c = 1; c >= 0; c--) {
            double[] chain = c == 0 ? front : rear;
            int i = c == 0 ? 0 : front.length - 1;
            int m = chain.length - 1;
            double[] targets = new double[m];
            double[] lengths = new double[m];
            for (int j = 0; j < m; j++) {
                targets[j] = lineAt(from, n, ramp, -chain[j + 1] * perBlock);
                lengths[j] = Math.abs(chain[j + 1] - chain[j]) * perBlock;
            }
            double[] wantLie = new double[m];
            if (alive) {
                // a straight trunk is its first piece lying at minus the hips' lie (each lie is its far end up)
                boolean stiff = c == 0 && trunkPieces > 0;
                double lo = stiff ? -hips - trunk : -MAX_STEEP, hi = stiff ? -hips + trunk : MAX_STEEP;
                if (c == 0) {
                    hi = Math.max(hi, raise(targets, lengths));
                }
                double[] joints = new double[m];
                for (int j = 0; j < m; j++) {
                    joints[j] = c == 0 && j < trunkPieces ? trunk : MAX_JOINT;
                }
                solve(anchorY, targets, lengths, joints, MAX_STEEP, Math.max(lo, -MAX_STEEP), Math.min(hi, MAX_STEEP), wantLie);
                if (c == 1) {
                    hips = wantLie[0];
                }
            }
            for (int j = 0; j < m; j++, i++) {
                lie[i] += (wantLie[j] - lie[i]) * k;
            }
        }
    }

    /**
     * The torso raised to higher ground ahead (2026-11-19, the user's idea: "the lizard actually raises its torso up to
     * it"): a monitor at a ledge higher than its shoulders rears its whole front up as one piece from the hips, the neck
     * and head in line with it, then sets its forefeet on top, instead of the head and neck curling up first and the
     * chest jerking up after (the side films: a neck pointing at the sky, then a T-rex). The front is laid as a straight
     * line from the anchor at the least angle (at most {@link #MAX_STEEP}) that keeps every joint over the ground line,
     * and level once it reaches the top of what is ahead; the targets are raised to it (the pieces then lie straight,
     * head level over the top). Changes the targets in place.
     *
     * @return the raise (degrees; 0 when nothing ahead is higher)
     */
    private double raise(double[] targets, double[] lengths) {
        double angle = 0, top = Double.NEGATIVE_INFINITY, d = 0;
        for (int j = 0; j < targets.length; j++) {
            d += lengths[j];
            angle = Math.max(angle, Math.asin(Mth.clamp((targets[j] - anchorY) / d, -1.0, 1.0)));
            top = Math.max(top, targets[j]);
        }
        angle = Math.min(angle, Math.toRadians(MAX_STEEP));
        if (angle <= 0) {
            return 0;
        }
        d = 0;
        for (int j = 0; j < targets.length; j++) {
            d += lengths[j];
            targets[j] = Math.max(targets[j], Math.min(anchorY + d * Math.sin(angle), top));
        }
        return Math.toDegrees(angle);
    }

    /**
     * The smoothed line from the heights read along the body (one every 1/{@link #GRID} block): each point is first the
     * highest within {@code widen} points either way (a step's high side reaches out over the low side, so the averaged
     * line clears the corner), then {@code sums} gets the running totals for {@link #average}.
     */
    static void line(double[] read, int n, int widen, double[] wide, double[] sums) {
        for (int i = 0; i < n; i++) {
            double h = read[i];
            for (int j = Math.max(0, i - widen); j <= Math.min(n - 1, i + widen); j++) {
                h = Math.max(h, read[j]);
            }
            wide[i] = h;
        }
        sums[0] = 0;
        for (int i = 0; i < n; i++) {
            sums[i + 1] = sums[i] + wide[i];
        }
    }

    /** The line's height this far along the body (blocks, forward +): the widened heights averaged over {@code ramp} points either way. */
    private double lineAt(double from, int n, int ramp, double along) {
        return average(sums, n, (int) Math.round((along - from) * GRID), ramp);
    }

    /** The average of the widened heights within {@code ramp} points either way of point {@code i} (from the running totals). */
    static double average(double[] sums, int n, int i, int ramp) {
        int lo = Mth.clamp(i - ramp, 0, n - 1), hi = Mth.clamp(i + ramp, 0, n - 1);
        return (sums[hi + 1] - sums[lo]) / (hi - lo + 1);
    }

    /** {@link #support} for the block column under this point, each column read once a frame. */
    private double column(BlockGetter level, double x, double z, double y, boolean inWater, double floatDepth) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int c = 0; c < cols; c++) {
            if (colX[c] == bx && colZ[c] == bz) {
                return colH[c];
            }
        }
        double h = support(level, bx + 0.5, bz + 0.5, y, inWater, floatDepth);
        if (cols == colX.length) {
            colX = java.util.Arrays.copyOf(colX, cols * 2);
            colZ = java.util.Arrays.copyOf(colZ, cols * 2);
            colH = java.util.Arrays.copyOf(colH, cols * 2);
        }
        colX[cols] = bx;
        colZ[cols] = bz;
        colH[cols++] = h;
        return h;
    }

    /**
     * The ground a foot at this point stands on (blocks, its height in the world): the highest top its toes find from
     * under it to {@link #TOES} ahead along the body as worked out at the last {@link #update}, the bottom under water.
     */
    public double groundNear(BlockGetter level, double px, double pz, double y) {
        double best = support(level, px, pz, y, false, 100.0), t = 0;
        while (t < TOES - 1.0E-9) {
            double cx = px + alongX * t, cz = pz + alongZ * t;
            t += Mth.clamp(Math.min(toEdge(cx, alongX), toEdge(cz, alongZ)), 1.0E-4, TOES - t);
            best = Math.max(best, support(level, px + alongX * (t + 1.0E-6), pz + alongZ * (t + 1.0E-6), y, false, 100.0));
        }
        return best;
    }

    /** How far along a direction (its share {@code d} in this axis) from {@code c} to the next block edge in this axis. */
    private static double toEdge(double c, double d) {
        if (Math.abs(d) < 1.0E-9) {
            return Double.MAX_VALUE;
        }
        double edge = d > 0 ? Math.floor(c) + 1 : Math.ceil(c) - 1;
        double gap = (edge - c) / d;
        return gap > 1.0E-9 ? gap : 1.0 / Math.abs(d);
    }

    /**
     * Whether feet this far forward of the animal along its body (blocks; behind it is negative) find a footing, as
     * worked out at the last {@link #update}: ground no more than {@code reach} under the body's own height (the animal's
     * feet, moved with the anchor), with no water over it deeper than {@code wade}. Over deep water, or past a drop,
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

    /**
     * Whether water lies under the point this far along the body (blocks, + forward), from just over the feet down to
     * {@code reach} under them (2026-11-18: the monitor's push into the water starts where its forelegs lose the bank over
     * water, not over a drop).
     */
    public boolean overWater(BlockGetter level, double along, double reach) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(atX + alongX * along), bz = Mth.floor(atZ + alongZ * along);
        double feet = atY + lift;
        for (int c = Mth.floor(feet + 0.3); c >= Mth.floor(feet - reach); c--) {
            if (level.getFluidState(p.set(bx, c, bz)).is(FluidTags.WATER)) {
                return true;
            }
            if (top(level, p) >= 0) {
                return false;
            }
        }
        return false;
    }

    /** How far the anchor is drawn above the animal's own height (blocks; under it negative): the whole body is moved by it before it bends. */
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
        double[] joints = new double[targets.length];
        java.util.Arrays.fill(joints, maxJoint);
        solve(anchorY, targets, lengths, joints, maxSteep, -maxSteep, maxSteep, out);
    }

    /** As above, each joint with its own reach (the first entry unused), the first piece between {@code firstLo} and {@code firstHi}. */
    public static void solve(double anchorY, double[] targets, double[] lengths, double[] maxJoint, double maxSteep,
            double firstLo, double firstHi, double[] out) {
        double h = anchorY;
        double before = 0;
        for (int j = 0; j < targets.length; j++) {
            double angle = Math.toDegrees(Math.asin(Mth.clamp((targets[j] - h) / lengths[j], -1.0, 1.0)));
            if (j > 0) {            // the first piece hangs from the stiff body, which a long neck or a chest turns far on
                angle = Mth.clamp(angle, before - maxJoint[j], before + maxJoint[j]);
            }
            angle = j == 0 ? Mth.clamp(angle, firstLo, firstHi) : Mth.clamp(angle, -maxSteep, maxSteep);
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
