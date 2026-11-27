package com.antcolonies.entity.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * A strike's hit or miss is the server's word on every screen (code review R4-1, 2026-11-26): a screen guesses by
 * itself a tick before the hit, from a prey it sees late and smoothed, and the server's word must stand over that
 * guess whichever of the two comes first. The fault: a screen that guessed "missed" never looked at the word again, so
 * a lioness that gripped was drawn standing on the ground while the buffalo carried her off.
 */
class StrikesRideTest {
    private static final int SETTLE = 5;
    /** A leap of 3 blocks: off at once, landing and hitting 6 ticks after its start. */
    private static final Spots.Strike LEAP = new Spots.Strike(6, 12, 0, 48.0,
            new double[] {0.0, 0.2, 0.4, 0.6, 0.8, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0});
    private static final Vec3 STILL = Vec3.ZERO;
    private static final Vec3 NEAR = new Vec3(2.0, 0.0, 0.0);
    /** The prey's spot a block on: further than {@link Strikes#GRIP} from where the leap aimed. */
    private static final Vec3 GONE = new Vec3(3.0, 0.0, 0.0);

    private static Vec3 tick(Strikes.Ride ride, int sinceStart, Vec3 place) {
        double along = ride.along(LEAP, sinceStart, SETTLE);
        return ride.target(LEAP, sinceStart, along, place, STILL, 1.0, SETTLE);
    }

    /** The ride flown up to the tick before the hit, the prey's spot where the leap aimed. */
    private static Strikes.Ride flown() {
        Strikes.Ride ride = Strikes.Ride.from(0.0, 0.0, 0.0F, LEAP, 1);
        for (int since = 1; since <= 4; since++) {
            assertEquals(NEAR, tick(ride, since, NEAR), "in the air it heads for its aim");
            assertEquals(-1, ride.word(), "nothing is decided before the hit");
        }
        return ride;
    }

    @Test
    void theServerDecidesByHowFarItsPlaceHasGone() {
        Strikes.Ride grips = flown();
        tick(grips, 5, NEAR);
        assertEquals(0, grips.word());
        Strikes.Ride misses = flown();
        assertEquals(NEAR, tick(misses, 5, GONE));
        assertEquals(1, misses.word());
        assertEquals(NEAR, tick(misses, 9, GONE), "a miss lands on its aim and stays there");
    }

    /** The fault itself: the screen's own "missed" came first, then the server's "it grips". */
    @Test
    void aScreenThatGuessedMissedTakesTheServersGrip() {
        Strikes.Ride ride = flown();
        tick(ride, 5, GONE);
        assertTrue(ride.missed(), "the screen guesses a miss: its prey's spot is a block from the aim");
        assertEquals(NEAR, tick(ride, 6, GONE));
        ride.told(0);
        Vec3 first = tick(ride, 7, GONE);
        assertFalse(ride.missed(), "told it grips, the screen still shows a miss");
        assertTrue(first.x > NEAR.x && first.x < GONE.x, "it does not ease from its aim onto its place, it is at " + first);
        Vec3 last = first;
        for (int since = 8; since <= 7 + SETTLE; since++) {
            Vec3 next = tick(ride, since, GONE);
            assertTrue(next.x >= last.x, "it eases back toward the aim");
            last = next;
        }
        assertEquals(GONE.x, last.x, 1e-9, "after its settling it is not on its place on the prey");
    }

    @Test
    void aScreenThatGuessedGrippedTakesTheServersMiss() {
        Strikes.Ride ride = flown();
        tick(ride, 5, NEAR);
        assertFalse(ride.missed());
        ride.told(1);
        assertEquals(NEAR, tick(ride, 6, GONE), "told it missed, it stays on its aim while the prey goes on");
        assertTrue(ride.missed());
    }

    @Test
    void aWordThatComesBeforeTheScreensGuessIsTaken() {
        Strikes.Ride ride = flown();
        ride.told(0);
        tick(ride, 5, GONE);
        assertFalse(ride.missed(), "the word had come: the screen has no guess of its own to make");
        Strikes.Ride other = flown();
        other.told(1);
        tick(other, 5, NEAR);
        assertTrue(other.missed());
    }
}
