package com.antcolonies.entity.body;

import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;

/**
 * The body only: where each foot of a walking animal stands, held in the world (the Animal Maker Engine step 2, feet
 * first, stage A, 2026-11-19; plan {@code docs/plans/engine-step2-feet-first.md}). One rule: <b>a foot holds its spot on
 * the ground and picks where it lands next; the body is held up by where the feet are.</b> In place of a walk clip
 * moving every foot on a fixed rhythm as if the ground were flat, and the feet pushed onto the ground after (the first
 * tries: the feet jumped up at a step at the last moment).
 * <p>
 * A planted foot stays exactly where it is in the world while the body moves over it, so it never slides, whatever the
 * speed or a turn on the spot. The stepping rhythm is a clock moved by how far the body goes (and turns), never by time,
 * so standing still the feet stand too, and a change of speed changes only how fast the clock runs (never where it is;
 * the lesson of BUGS 232). Each foot lifts once a stride, at its own place in the clock (the animal's gait, from its walk
 * clip: which feet go together, how long each is down); it swings to a landing chosen on the real ground just ahead of
 * where it will be needed, the landing aimed again each frame as the body moves, and once lifted it finishes its step on
 * its own time, so an animal stopping mid-step puts the foot down rather than freezing it in the air. A foot left more
 * than a stride from where it belongs (a sudden shove, a sharp turn) steps at once.
 * <p>
 * Species-neutral: any number of legs, each given as its spot in the model (model blocks, GeckoLib's own axes, the head
 * toward -Z) and its place in the gait. Works on whatever thread draws: it only reads blocks. Nothing here decides
 * anything; the legs reaching their feet is {@code client/LegReach}.
 */
public final class Footing {
    /** A gap this long between updates (seconds), or a move this far in one (blocks): the feet are put down afresh. */
    private static final double STALE = 0.5, JUMP = 2.0;
    /** How long a step in the air takes at most and at least (seconds; my picks), and when the animal is not moving. */
    private static final double SWING_MAX = 0.45, SWING_MIN = 0.12, SWING_STILL = 0.3;
    /** How quickly the body's measured speed follows the real one (seconds; my pick, smooths frame jitter). */
    private static final double SPEED_EASE = 0.15;
    /** Moving slower than this (blocks a second) counts as standing: a lifted foot lands under its home. */
    private static final double STILL = 0.05;
    /** A planted foot this many strides from its home steps at once (my pick). */
    private static final double STRETCH = 1.1;

    /** One foot: its place in the model and the gait, and where it is now. */
    private static final class Foot {
        final double homeX, frontZ, backZ, offset, lift;
        boolean down = true;
        /** Where the foot is (world; y the ground's top under it, or its height in the air). */
        double x, y, z;
        /** Where its step started, how long it has been in the air and how long the step takes (seconds). */
        double fromX, fromY, fromZ, time, length;
        /** The stride (whole turns of the clock) it last stepped in. */
        long stepped = Long.MIN_VALUE;
        /** How high it is lifted now, 0 to 1 (0 down). */
        double up;

        Foot(double homeX, double frontZ, double backZ, double offset, double lift) {
            this.homeX = homeX;
            this.frontZ = frontZ;
            this.backZ = backZ;
            this.offset = offset;
            this.lift = lift;
        }
    }

    private final Foot[] feet;
    /** The share of a stride each foot stands on the ground (0 to 1). */
    private final double duty;
    /** How far the body goes in one stride (model blocks): a foot goes from front to back while down. */
    private final double stride;
    private double clock;
    private long lastNanos;
    private double lastX, lastZ, lastYaw;
    private double velX, velZ;
    private boolean fresh = true;

    /**
     * @param duty the share of a stride each foot is down
     * @param legs one row a foot (model blocks; the head toward -Z): its middle line x, where it lands (front z) and lifts
     *             (back z) as the body walks over it, its place in the stride (0 to 1), how high it lifts (model blocks)
     */
    public Footing(double duty, double[][] legs) {
        this.duty = duty;
        this.feet = new Foot[legs.length];
        double reach = 0;
        for (int i = 0; i < legs.length; i++) {
            double[] l = legs[i];
            feet[i] = new Foot(l[0], l[1], l[2], l[3], l[4]);
            reach = Math.max(reach, Math.abs(l[2] - l[1]));
        }
        this.stride = reach / duty;
    }

    /** The feet are put down afresh at the next update (the animal was drawn by its clips, out of sight, or far away). */
    public void forget() {
        fresh = true;
    }

    /**
     * Moves the feet on to now. Returns true if they were put down afresh, each under its home (the caller may then
     * {@link #place} them where its clips had them, so nothing jumps).
     *
     * @param scale model blocks to world blocks (the model's drawn size)
     */
    public boolean update(BlockGetter level, double x, double y, double z, float yaw, double scale, long nanos) {
        double turn = Math.toRadians(180.0 - yaw), cos = Math.cos(turn), sin = Math.sin(turn);
        double dt = (nanos - lastNanos) / 1.0E9;
        double dx = x - lastX, dz = z - lastZ;
        double turned = Math.abs(Mth.wrapDegrees(yaw - lastYaw)) * Mth.DEG_TO_RAD;
        lastNanos = nanos;
        lastX = x;
        lastZ = z;
        lastYaw = yaw;
        if (fresh || dt > STALE || dt < 0 || dx * dx + dz * dz > JUMP * JUMP) {
            fresh = false;
            velX = velZ = 0;
            for (Foot f : feet) {
                double mz = (f.frontZ + f.backZ) / 2;
                f.x = x + (f.homeX * cos + mz * sin) * scale;
                f.z = z + (-f.homeX * sin + mz * cos) * scale;
                f.y = GroundBend.support(level, f.x, f.z, y, false, 100.0);
                f.down = true;
                f.up = 0;
                // a foot whose turn to step has already come this stride waits for the next one (no feet all lifting at once)
                double place = clock - f.offset;
                long round = (long) Math.floor(place);
                f.stepped = place - round >= duty ? round : Long.MIN_VALUE;
            }
            return true;
        }
        if (dt <= 0) {
            return false;
        }
        double k = 1.0 - Math.exp(-dt / SPEED_EASE);
        velX += (dx / dt - velX) * k;
        velZ += (dz / dt - velZ) * k;
        double speed = Math.sqrt(velX * velX + velZ * velZ);
        // the clock moves with the distance gone, a turn counting as the feet's way round the middle
        double radius = 0;
        for (Foot f : feet) {
            radius += Math.hypot(f.homeX, (f.frontZ + f.backZ) / 2);
        }
        radius = radius / feet.length * scale;
        double strideWorld = stride * scale;
        clock += Math.min(0.25, (Math.sqrt(dx * dx + dz * dz) + turned * radius) / strideWorld);
        double turnSpeed = turned / dt * radius;
        for (Foot f : feet) {
            double place = clock - f.offset;
            long round = (long) Math.floor(place);
            double u = place - round;
            double midZ = (f.frontZ + f.backZ) / 2;
            if (f.down) {
                double hx = x + (f.homeX * cos + midZ * sin) * scale, hz = z + (-f.homeX * sin + midZ * cos) * scale;
                boolean stretched = Math.hypot(f.x - hx, f.z - hz) > STRETCH * Math.abs(f.backZ - f.frontZ) * scale;
                if (u >= duty && f.stepped != round || stretched) {
                    f.down = false;
                    f.stepped = round;
                    f.fromX = f.x;
                    f.fromY = f.y;
                    f.fromZ = f.z;
                    f.time = 0;
                    double going = speed + turnSpeed;
                    f.length = going < STILL ? SWING_STILL
                            : Mth.clamp((1.0 - duty) * strideWorld / going, SWING_MIN, SWING_MAX);
                }
            }
            if (!f.down) {
                f.time += dt;
                double s = Math.min(1.0, f.time / f.length), left = Math.max(0.0, f.length - f.time);
                // it lands at the front of its stride, where the body will have carried that place by then; standing,
                // under its home
                boolean still = speed + turnSpeed < STILL;
                double lz = still ? midZ : f.frontZ;
                double tx = x + (f.homeX * cos + lz * sin) * scale + (still ? 0 : velX * left);
                double tz = z + (-f.homeX * sin + lz * cos) * scale + (still ? 0 : velZ * left);
                double ty = GroundBend.support(level, tx, tz, y, false, 100.0);
                double e = s * s * (3.0 - 2.0 * s);
                f.up = Math.sin(Math.PI * s);
                f.x = f.fromX + (tx - f.fromX) * e;
                f.z = f.fromZ + (tz - f.fromZ) * e;
                f.y = f.fromY + (ty - f.fromY) * e + f.lift * scale * f.up;
                if (s >= 1.0) {
                    f.down = true;
                    f.up = 0;
                    f.x = tx;
                    f.y = ty;
                    f.z = tz;
                }
            }
        }
        return false;
    }

    /** Puts a foot down here (world), on the ground under it. */
    public void place(BlockGetter level, int foot, double x, double y, double z) {
        Foot f = feet[foot];
        f.x = x;
        f.z = z;
        f.y = GroundBend.support(level, x, z, y, false, 100.0);
        f.down = true;
        f.up = 0;
    }

    public int count() {
        return feet.length;
    }

    /** Where the foot is (world): x, the ground's top under it (or its height in the air), z. */
    public double x(int foot) {
        return feet[foot].x;
    }

    public double y(int foot) {
        return feet[foot].y;
    }

    public double z(int foot) {
        return feet[foot].z;
    }

    /** How high the foot is lifted in its step now, 0 (down) to 1 (top of the step). */
    public double up(int foot) {
        return feet[foot].up;
    }

    /** Whether the foot stands on the ground now. */
    public boolean down(int foot) {
        return feet[foot].down;
    }
}
