package com.antcolonies.savanna.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The flies of one cloud, moved on the player's computer (fly clouds step 2, 2026-11-20): the preview's motion
 * ({@code tools/fly_cloud.template.html}, approved by the user) carried into the game. Each fly zips to a new spot in
 * the cloud every 0.18 to 0.6 s on a springy dash that overshoots a little, hovers with a wobble and turns on the spot;
 * it leans into its dashes and turns. Now and then one lands on the ground or the water near the cloud for 1.4 to 4.4
 * s, turns about and often rubs its front legs, then takes off (at most half the cloud down at once). Every number is
 * the preview's (my picks, tuned there), the cloud a little wider for the game's 4 to 12 flies.
 */
final class FlySwarm {
    /** The cloud's half sizes round its middle (blocks; my picks). */
    static final double RX = 0.6, RY = 0.3, RZ = 0.6;
    /** How far from the cloud's middle a fly may land (blocks), and how far under it at most. */
    static final double LAND_ACROSS = 1.4, LAND_BELOW = 4.0;
    /** The spring of a dash and of a landing (stiffness, damping), and the fastest a fly goes (blocks a second). */
    static final double DASH_K = 75, DASH_C = 9.5, LAND_K = 30, LAND_C = 9, TOP_SPEED = 3.2;
    /** A step of the spring at most (seconds): long frames are cut into these, so a slow frame never shakes it. */
    static final double STEP = 1.0 / 60.0;

    enum Mode { FLY, LANDING, PERCHED, ON_TONGUE }
    /** A fly on a bullfrog's tongue: a stiff spring to the tongue's pad, and it may go fast (blocks a second). */
    static final double TONGUE_K = 600, TONGUE_C = 45, TONGUE_SPEED = 14;

    static final class Fly {
        final boolean lookB;
        final double seed;
        final int flick;
        Mode mode = Mode.FLY;
        double x, y, z, vx, vy, vz, ax, ay, az;
        /** Flying: the spot in the cloud it dashes to (against the middle); landing: the spot on the ground (world). */
        double tx, ty, tz;
        double timer, yaw, yawT, perch, rub, rubAt;
        /** Drawn this frame: its place (world), lean, and whether its wings are up. */
        double drawX, drawY, drawZ, tiltX, tiltZ;

        Fly(boolean lookB, double seed, int flick) {
            this.lookB = lookB;
            this.seed = seed;
            this.flick = flick;
        }

        boolean flying() {
            return mode != Mode.PERCHED;
        }
    }

    private final Random rng;
    /** Each cloud its own mix of bottle flies and house flies (the share of house flies, 0.2 to 0.8; my pick). */
    private final float shareB;
    final List<Fly> flies = new ArrayList<>();
    private double lastTime = -1;
    /** Frames drawn (the wings swap angles every frame: the buzzing blur). */
    int frame;
    /** The time now (seconds), for the legs' rubbing. */
    double time;
    /** The fly a bullfrog's tongue has, and where the tongue is (world), or none. */
    @javax.annotation.Nullable
    private Fly onTongue;
    @javax.annotation.Nullable
    private Vec3 tongue;

    FlySwarm(long seed) {
        rng = new Random(seed);
        shareB = 0.2F + 0.6F * rng.nextFloat();
    }

    /**
     * Brings the swarm to this frame: as many flies as the cloud holds, each moved on by the time gone since. A
     * bullfrog's tongue in the cloud takes the fly nearest it; a fly fewer is the one on the tongue (swallowed).
     */
    void update(Level level, Vec3 middle, double now, int count, @javax.annotation.Nullable Vec3 tongueAt) {
        tongue = tongueAt;
        if (tongueAt != null && (onTongue == null || !flies.contains(onTongue))) {
            Fly nearest = null;
            double best = Double.MAX_VALUE;
            for (Fly f : flies) {
                double d = (f.x - tongueAt.x) * (f.x - tongueAt.x) + (f.y - tongueAt.y) * (f.y - tongueAt.y) + (f.z - tongueAt.z) * (f.z - tongueAt.z);
                if (f.mode == Mode.FLY && d < best) {
                    nearest = f;
                    best = d;
                }
            }
            onTongue = nearest;
            if (nearest != null) {
                nearest.mode = Mode.ON_TONGUE;
            }
        } else if (tongueAt == null && onTongue != null) {
            // let go: it zips off into the cloud again
            onTongue.mode = Mode.FLY;
            onTongue.vx += (rng.nextDouble() - 0.5) * 4;
            onTongue.vy += 1.5;
            onTongue.vz += (rng.nextDouble() - 0.5) * 4;
            inCloud(onTongue);
            onTongue = null;
        }
        if (flies.size() > count && onTongue != null) {
            flies.remove(onTongue);         // swallowed
            onTongue = null;
        }
        while (flies.size() < count) {
            Fly f = new Fly(rng.nextFloat() < shareB, flies.size() * 1.7, flies.size() % 2);
            inCloud(f);
            f.x = middle.x + f.tx;
            f.y = middle.y + f.ty;
            f.z = middle.z + f.tz;
            f.yaw = f.yawT = rng.nextDouble() * Mth.TWO_PI;
            f.timer = rng.nextDouble() * 0.4;
            flies.add(f);
        }
        while (flies.size() > count) {
            flies.remove(flies.size() - 1);
        }
        double dt = lastTime < 0 ? 0 : Mth.clamp(now - lastTime, 0, 0.25);
        lastTime = now;
        time = now;
        frame++;
        int down = 0;
        for (Fly f : flies) {
            down += f.flying() && f.mode != Mode.LANDING ? 0 : 1;
        }
        while (dt > 1.0E-6) {
            double step = Math.min(STEP, dt);
            dt -= step;
            for (Fly f : flies) {
                down += move(level, f, middle, step, down) ;
            }
        }
        for (Fly f : flies) {
            pose(f, now);
        }
    }

    /** A new spot in the cloud to dash to (against the middle). */
    private void inCloud(Fly f) {
        double x, y, z;
        do {
            x = rng.nextDouble() * 2 - 1;
            y = rng.nextDouble() * 2 - 1;
            z = rng.nextDouble() * 2 - 1;
        } while (x * x + y * y + z * z > 1);
        f.tx = x * RX;
        f.ty = y * RY;
        f.tz = z * RZ;
        f.timer = 0.18 + rng.nextDouble() * 0.45;
    }

    /** Where it may land near the cloud: the top of the ground or water there, if not too far under the cloud. */
    private boolean landingSpot(Level level, Fly f, Vec3 middle) {
        double x = middle.x + (rng.nextDouble() * 2 - 1) * LAND_ACROSS;
        double z = middle.z + (rng.nextDouble() * 2 - 1) * LAND_ACROSS;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        double y = top;
        if (level.getFluidState(BlockPos.containing(x, top - 1, z)).is(FluidTags.WATER)) {
            y = top - 0.11;       // on the water's surface (a water block's top is not the block's)
        }
        if (middle.y - y > LAND_BELOW || y > middle.y) {
            return false;
        }
        f.tx = x;
        f.ty = y;
        f.tz = z;
        return true;
    }

    /** One step of a fly's flight; returns how many more flies are down (landing or landed) after it (-1, 0 or 1). */
    private int move(Level level, Fly f, Vec3 middle, double dt, int down) {
        int change = 0;
        switch (f.mode) {
            case FLY -> {
                f.timer -= dt;
                if (f.timer <= 0) {
                    if (rng.nextDouble() < 0.06 && down < flies.size() / 2 && landingSpot(level, f, middle)) {
                        f.mode = Mode.LANDING;
                        change = 1;
                    } else {
                        inCloud(f);
                    }
                }
                if (f.mode == Mode.FLY) {
                    spring(f, middle.x + f.tx, middle.y + f.ty, middle.z + f.tz, DASH_K, DASH_C, dt);
                } else {
                    spring(f, f.tx, f.ty, f.tz, LAND_K, LAND_C, dt);
                }
            }
            case LANDING -> {
                spring(f, f.tx, f.ty, f.tz, LAND_K, LAND_C, dt);
                double dx = f.x - f.tx, dy = f.y - f.ty, dz = f.z - f.tz;
                if (dx * dx + dy * dy + dz * dz < 0.008 * 0.008 * 4 && f.vx * f.vx + f.vy * f.vy + f.vz * f.vz < 0.04) {
                    f.mode = Mode.PERCHED;
                    f.x = f.tx;
                    f.y = f.ty;
                    f.z = f.tz;
                    f.vx = f.vy = f.vz = f.ax = f.ay = f.az = 0;
                    f.perch = 1.4 + rng.nextDouble() * 3;
                    f.rubAt = 0.3 + rng.nextDouble() * 0.6;
                    f.rub = rng.nextDouble() < 0.65 ? 0.7 + rng.nextDouble() : 0;
                }
            }
            case ON_TONGUE -> {
                if (tongue != null) {
                    springFast(f, tongue);
                }
            }
            case PERCHED -> {
                f.perch -= dt;
                f.rubAt -= dt;
                if (f.rubAt < 0 && f.rub > 0) {
                    f.rub -= dt;
                }
                if (rng.nextDouble() < dt * 0.7) {
                    f.yawT += (rng.nextDouble() - 0.5) * 1.6;
                }
                if (f.perch <= 0) {
                    f.mode = Mode.FLY;
                    f.vx = rng.nextDouble() - 0.5;
                    f.vy = 1.6;
                    f.vz = rng.nextDouble() - 0.5;
                    inCloud(f);
                    change = -1;
                }
            }
        }
        // heading: into the dash, or turning on the spot while hovering
        double hs = Math.sqrt(f.vx * f.vx + f.vz * f.vz);
        if (f.mode != Mode.PERCHED && hs > 0.3) {
            f.yawT = Math.atan2(-f.vx, -f.vz);
        } else if (f.mode == Mode.FLY && rng.nextDouble() < dt * 1.6) {
            f.yawT += (rng.nextDouble() - 0.5) * 2.2;
        }
        double d = Math.atan2(Math.sin(f.yawT - f.yaw), Math.cos(f.yawT - f.yaw));
        f.yaw += d * Math.min(1, dt * 18);
        return change;
    }

    /** On the tongue: held to its pad (it moves far faster than a fly flies). */
    private void springFast(Fly f, Vec3 at) {
        double dt = STEP;
        f.ax = (at.x - f.x) * TONGUE_K - f.vx * TONGUE_C;
        f.ay = (at.y - f.y) * TONGUE_K - f.vy * TONGUE_C;
        f.az = (at.z - f.z) * TONGUE_K - f.vz * TONGUE_C;
        f.vx += f.ax * dt;
        f.vy += f.ay * dt;
        f.vz += f.az * dt;
        double sp = Math.sqrt(f.vx * f.vx + f.vy * f.vy + f.vz * f.vz);
        if (sp > TONGUE_SPEED) {
            f.vx *= TONGUE_SPEED / sp;
            f.vy *= TONGUE_SPEED / sp;
            f.vz *= TONGUE_SPEED / sp;
        }
        f.x += f.vx * dt;
        f.y += f.vy * dt;
        f.z += f.vz * dt;
    }

    private static void spring(Fly f, double tx, double ty, double tz, double k, double c, double dt) {
        f.ax = (tx - f.x) * k - f.vx * c;
        f.ay = (ty - f.y) * k - f.vy * c;
        f.az = (tz - f.z) * k - f.vz * c;
        f.vx += f.ax * dt;
        f.vy += f.ay * dt;
        f.vz += f.az * dt;
        double sp = Math.sqrt(f.vx * f.vx + f.vy * f.vy + f.vz * f.vz);
        if (sp > TOP_SPEED) {
            f.vx *= TOP_SPEED / sp;
            f.vy *= TOP_SPEED / sp;
            f.vz *= TOP_SPEED / sp;
        }
        f.x += f.vx * dt;
        f.y += f.vy * dt;
        f.z += f.vz * dt;
    }

    /** Its place and lean to draw this frame: a little hover wobble in the air; nose down speeding up, rolled into a turn. */
    private static void pose(Fly f, double t) {
        double fx = -Math.sin(f.yaw), fz = -Math.cos(f.yaw), lx = Math.cos(f.yaw), lz = -Math.sin(f.yaw);
        double fwd = f.ax * fx + f.az * fz, lat = f.ax * lx + f.az * lz;
        boolean perched = f.mode == Mode.PERCHED;
        f.tiltX = perched ? 0 : Mth.clamp(-fwd * 0.012, -0.4, 0.4);
        f.tiltZ = perched ? 0 : Mth.clamp(-lat * 0.01, -0.5, 0.5);
        f.drawX = f.x;
        f.drawY = f.y;
        f.drawZ = f.z;
        if (f.mode == Mode.FLY) {
            double s = f.seed;
            f.drawX += 0.011 * Math.sin(11 * t + s);
            f.drawY += 0.008 * Math.sin(14 * t + 2 * s);
            f.drawZ += 0.011 * Math.sin(9 * t + 3 * s);
        }
    }
}
