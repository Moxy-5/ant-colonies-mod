package com.antcolonies.entity.body;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Strikes that carry themselves (the Motion Studio, phase 3, step 4, 2026-11-26; {@code docs/plans/motion-studio-phase3.md}):
 * a leap, a lunge or a pounce made once with its own travel (its clip's "path root" note, made with the motion kit's
 * {@code travel}, {@code path_note} and {@code on_path}), whose share a tick is in the striker's spot table
 * ({@link Spots.Strike}, by {@code tools/spots.py}). The game asks here whether a strike is in reach and places the
 * striker along it: never the move control pushing its box while the clip leaps on the spot (BUGS 375, the lioness).
 * <p>
 * Species-neutral: any striker and prey. The animal keeps the rest (which strike, which spot, the ground under it, when
 * it grips); the lioness on the buffalo first ({@code LionHunt}).
 */
public final class Strikes {
    /**
     * How far (blocks at size 1) a striker's place on its prey may have gone from where it aimed and it still grips at its
     * hit (my pick, 2026-11-26: about a jaw's reach; further, it lands on empty ground and misses).
     */
    public static final double GRIP = 0.6;

    private Strikes() {
    }

    /**
     * The one rule when to strike: its place on the prey, where that will be when it lands (the prey going on as it went
     * this tick), within the strike's reach at the striker's size (flat distances).
     *
     * @param place     where the striker stands to hold the prey's spot now
     * @param preyMoved how far the prey moved this tick (its position less its last)
     * @param size      the striker's size (its model's px times this, over 16, are blocks)
     */
    public static boolean inReach(Vec3 striker, Vec3 place, Vec3 preyMoved, Spots.Strike strike, double size) {
        int lands = strike.landed();
        double x = place.x + preyMoved.x * lands, z = place.z + preyMoved.z * lands;
        return Math.hypot(striker.x - x, striker.z - z) <= strike.reach() * size / 16.0;
    }

    /**
     * A striker on its way to its place on its prey, worked out on the server and on every screen alike: where it set
     * off (the world) and its heading then; on a strike, the share of the strike's travel already gone when it was first
     * placed (a screen that learns of the strike a tick late carries on from where it draws it; below 0: no strike, it
     * settles over a blend), and the ticks it has settled.
     */
    public static final class Ride {
        private final double x, z;
        private final float yaw;
        private final double from;
        private int k;
        @Nullable
        private Vec3 aim;                       // where the strike lands: followed through its coil, fixed once it is off
        private int missed = -1;                // decided at its hit: 1 its place had gone from where it aimed, 0 it grips
        private double gapX, gapZ;              // its aim less its place at the hit (eased away as it grips)
        private int told = -1;                  // a screen: the server's word (-1 not come yet)

        private Ride(double x, double z, float yaw, double from) {
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.from = from;
        }

        /**
         * A ride from where the striker stands now: on a strike, that is where the strike had it a tick ago (its start,
         * on the server's first tick on it); with no strike (a grip), it settles from there.
         *
         * @param sinceStart ticks since the strike started (its doing's), blend included
         */
        public static Ride from(Entity striker, float yaw, @Nullable Spots.Strike strike, int sinceStart) {
            return new Ride(striker.getX(), striker.getZ(), yaw, strike == null ? -1.0 : strike.share(sinceStart - 1));
        }

        /**
         * How far along it is now, 0 where it set off to 1 at its place: on a strike, the strike's share (from where it was
         * first placed); after it, or with none, eased over {@code settleTicks} (each call one tick).
         */
        public double along(@Nullable Spots.Strike strike, int sinceStart, int settleTicks) {
            if (from >= 0.0) {
                double share = strike == null ? 1.0 : strike.share(sinceStart);
                return from >= 1.0 ? 1.0 : Mth.clamp((share - from) / (1.0 - from), 0.0, 1.0);
            }
            double f = k >= settleTicks ? 1.0 : (double) ++k / settleTicks;
            return f * f * (3.0 - 2.0 * f);
        }

        /**
         * Where it heads now (flat). No strike: its place. On a strike it aims, never steers in the air (the hunt film,
         * 2026-11-26: a leap that followed a fleeing calf stretched to 5 blocks at twice the clip's speed): through its
         * coil, at where its place will be when it lands (the prey going on as it goes this tick), never beyond its reach
         * from where it set off; from the first tick off the ground that aim is fixed. At the hit it grips if its place is still within {@link #GRIP} of the aim
         * (at its size), easing onto its place over {@code settleTicks}; otherwise it has missed and lands on the aim.
         *
         * @param along     its share of the way now ({@link #along})
         * @param preyStep  how far the prey moved this tick
         * @param size      the striker's size
         */
        public Vec3 target(@Nullable Spots.Strike strike, int sinceStart, double along, Vec3 place, Vec3 preyStep,
                double size, int settleTicks) {
            if (missed == 1) {
                return aim;
            }
            if (from < 0.0 || strike == null) {
                return place;
            }
            int sinceHit = sinceStart - strike.lead() - strike.hit();
            if (aim == null || along <= 0.0 && sinceHit < 0) {
                int left = Math.max(0, strike.landed() - sinceStart);
                double ax = place.x + preyStep.x * left - x, az = place.z + preyStep.z * left - z;
                double far = Math.hypot(ax, az), reach = strike.reach() * size / 16.0;
                double k = far > reach ? reach / far : 1.0;     // no farther than it can leap: the prey fled, it lands short
                aim = new Vec3(x + ax * k, place.y, z + az * k);
            }
            if (missed < 0 && (sinceHit >= -1 || told >= 0)) {
                // decided the tick before the hit: the striker's own tick (its grip, its bite) comes before its placing
                missed = Math.hypot(place.x - aim.x, place.z - aim.z) > GRIP * size ? 1 : 0;
                gapX = aim.x - place.x;
                gapZ = aim.z - place.z;
            }
            if (told >= 0 && missed >= 0) {
                missed = told;                          // a screen: the server's word stands over its own guess
            }
            if (sinceHit < 0 || missed == 1) {
                return aim;
            }
            // gripping: on its place as the prey moves, the gap left at the hit eased away
            double f = Math.min(1.0, (sinceHit + 1.0) / settleTicks);
            double left = 1.0 - f * f * (3.0 - 2.0 * f);
            return new Vec3(place.x + gapX * left, place.y, place.z + gapZ * left);
        }

        /**
         * A screen is told the server's word on this strike (-1: not come yet; 0 it grips, 1 it missed). Hit or miss is
         * the server's: a screen sees the prey a tick late and smoothed, and a spinning calf had the two disagree (the
         * hunt film, 2026-11-26: the server's lioness missed, the screen's gripped and swung round with the calf).
         */
        public void told(int word) {
            if (word >= 0) {
                told = word;
            }
        }

        /** Its word on the strike: -1 not decided yet, 0 it grips, 1 it missed (the server sends it to the screens). */
        public int word() {
            return missed;
        }

        /** Whether its strike missed (decided at the strike's hit; false before it). */
        public boolean missed() {
            return missed == 1;
        }

        /** Where it is at that share of the way to its place (flat: its height is the animal's to find). */
        public Vec3 at(double along, Vec3 place) {
            return new Vec3(Mth.lerp(along, x, place.x), place.y, Mth.lerp(along, z, place.z));
        }

        /** Its heading at that share of the way, turning from its own to its place's. */
        public float yaw(double along, float placeYaw) {
            return yaw + Mth.wrapDegrees(placeYaw - yaw) * (float) along;
        }
    }
}
