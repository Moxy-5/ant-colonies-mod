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
