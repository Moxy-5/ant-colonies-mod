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
    /** A fly dodging a bullfrog's tongue: its burst of speed (blocks a second; my pick, a fly's fastest dart). */
    static final double DODGE_SPEED = 3.2;
    /** A fly held on a tongue whose frog stopped drawing it is let go after this long (seconds; the frog went away). */
    static final double HELD_LONGEST = 1.5;

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
        /** The bullfrog whose eyes are on it (no other frog picks it), and when it was last held on a tongue (seconds). */
        @javax.annotation.Nullable
        Object hunter;
        double heldAt;

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

    FlySwarm(long seed) {
        rng = new Random(seed);
        shareB = 0.2F + 0.6F * rng.nextFloat();
    }

    /**
     * Brings the swarm to this frame: as many flies as the cloud holds, each moved on by the time gone since. A fly
     * fewer is first one a bullfrog's tongue holds (swallowed).
     */
    void update(Level level, Vec3 middle, double now, int count) {
        while (flies.size() > count) {
            Fly held = null;
            for (Fly f : flies) {
                if (f.mode == Mode.ON_TONGUE) {
                    held = f;
                    break;
                }
            }
            if (held == null) {
                break;
            }
            flies.remove(held);         // swallowed
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
                if (time - f.heldAt > HELD_LONGEST) {
                    letGo(f);           // its frog stopped holding it (gone, or out of sight)
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

    // --- a bullfrog's tongue (the polish's step 3, 2026-11-20) -------------------------------------------------------

    /**
     * The fly a bullfrog fixes its eyes on: the one nearest its mouth within this reach (flying, or landed on the ground
     * or the water), none another frog has its eyes on; or none.
     */
    @javax.annotation.Nullable
    Fly pick(Object frog, Vec3 mouth, double reach) {
        Fly best = null;
        double bestD = reach * reach;
        for (Fly f : flies) {
            if (f.mode == Mode.ON_TONGUE || f.hunter != null && f.hunter != frog && !gone(f.hunter)) {
                continue;
            }
            double d = mouth.distanceToSqr(f.drawX, f.drawY, f.drawZ);
            if (d < bestD) {
                best = f;
                bestD = d;
            }
        }
        if (best != null) {
            best.hunter = frog;
        }
        return best;
    }

    private static boolean gone(Object hunter) {
        return hunter instanceof net.minecraft.world.entity.Entity e && (e.isRemoved() || !e.isAlive());
    }

    /** Whether this fly is still one of the swarm's. */
    boolean has(Fly f) {
        return flies.contains(f);
    }

    /** The frog lets its eyes go of a fly (the flick is over, or it gave up). */
    static void release(Fly f, Object frog) {
        if (f.hunter == frog) {
            f.hunter = null;
        }
    }

    /** A fly caught on a tongue's pad: held at the pad (the frog draws it there; the cloud no longer does). */
    void hold(Fly f, Vec3 at) {
        if (f.mode == Mode.PERCHED || f.mode == Mode.LANDING) {
            f.perch = 0;
        }
        f.mode = Mode.ON_TONGUE;
        f.x = f.drawX = at.x;
        f.y = f.drawY = at.y;
        f.z = f.drawZ = at.z;
        f.vx = f.vy = f.vz = f.ax = f.ay = f.az = 0;
        f.heldAt = time;
    }

    /** A fly slips the tongue: it darts off sideways and up, away from where the pad is coming from. */
    void dodge(Fly f, Vec3 from) {
        boolean down = f.mode == Mode.PERCHED || f.mode == Mode.LANDING;
        f.mode = Mode.FLY;
        double dx = f.x - from.x, dz = f.z - from.z;
        double flat = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
        double side = rng.nextBoolean() ? 1 : -1;       // sideways across the tongue's line
        f.vx = (-dz / flat * side * 0.8 + dx / flat * 0.3) * DODGE_SPEED;
        f.vy = DODGE_SPEED * 0.55;
        f.vz = (dx / flat * side * 0.8 + dz / flat * 0.3) * DODGE_SPEED;
        inCloud(f);
        if (down) {
            f.rub = 0;
        }
    }

    /** Let go of a held fly: it zips off into the cloud again. */
    private void letGo(Fly f) {
        f.mode = Mode.FLY;
        f.hunter = null;
        f.vx = (rng.nextDouble() - 0.5) * 4;
        f.vy = 1.5;
        f.vz = (rng.nextDouble() - 0.5) * 4;
        inCloud(f);
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
