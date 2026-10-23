package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.antcolonies.species.SpeciesProfile;

/**
 * Brain step 4b: Gordon's door rule for the forager numbers ({@link Door#step}) and the patroller counts (the user's
 * scaling: 2 at 10 workers, 4 at 25, 8 at 50, 16 at 100; later rounds half).
 */
class DoorRuleTest {
    private static final SpeciesProfile.DoorNumbers DOOR = new SpeciesProfile.DoorNumbers(3, 600, 2.0, 10, 3);
    private static final SpeciesProfile.PatrolNumbers PATROL = new SpeciesProfile.PatrolNumbers(0.16F, 1, 10, 0.5F, 4.0, 1.0F, 0.5F);

    /** Runs the numbers for {@code seconds}: each second {@code returnsPerSecond} homecomings with food (a fraction: on average). */
    private static double run(int foragers, double out, double returnsPerSecond, int seconds, boolean small) {
        java.util.ArrayDeque<int[]> window = new java.util.ArrayDeque<>();
        double owed = 0;
        for (int s = 0; s < seconds; s++) {
            owed += returnsPerSecond * Math.max(0.05, Math.min(1.0, out / Math.max(1, foragers)) * foragers / 10.0);
            int returns = (int) owed;
            owed -= returns;
            int touches = 0;
            for (int[] entry : window) {
                touches += entry[1];
            }
            out = Door.step(DOOR, foragers, out, out, returns, touches, small, 1.0F);
            window.add(new int[] {s, returns * DOOR.touchesPerReturn()});
            while (!window.isEmpty() && window.peekFirst()[0] <= s - DOOR.touchMemoryTicks() / 20) {
                window.removeFirst();
            }
        }
        return out;
    }

    @Test
    void quickReturnsBringMoreForagersOutUpToAllOfThem() {
        double out = run(40, 4, 1.0, 120, false);
        assertTrue(out > 20, "with seeds coming back quickly most foragers should be out after two minutes, got " + out);
        assertTrue(out <= 40, "never more out than there are foragers");
    }

    @Test
    void slowReturnsLetTheOutflowDieDown() {
        // Nobody finds anything: the ones out come home empty, touch nobody, and nobody new goes.
        double out = 20;
        for (int s = 0; s < 300; s++) {
            out = Door.step(DOOR, 40, out, 0, 0, 0, false, 1.0F);
        }
        assertTrue(out < 1, "with no food coming back the foragers should all be in after five minutes, got " + out);
        // Sparse returns: one homecoming in 20 seconds is too little to bring a waiting ant out (3 touches in 30 s).
        double sparse = run(40, 4, 0.05, 300, false);
        assertTrue(sparse < 5, "a trickle of returns should not grow the outflow, got " + sparse);
    }

    @Test
    void emptyReturnsDoNotCount() {
        double out = Door.step(DOOR, 40, 10, 0, 0, 90, false, 1.0F);
        assertTrue(out < 10, "empty homecomings bring nobody out: " + out);
    }

    @Test
    void aSmallColonyGoesOutAtOneTouch() {
        assertEquals(5.0, Door.step(DOOR, 5, 0, 0, 1, 0, true, 1.0F), 1e-9, "one patroller home brings every waiting forager of a small colony out");
        assertEquals(1, DOOR.touchesFor(5));
        assertEquals(3, DOOR.touchesFor(10));
    }

    @Test
    void afterDangerOnlyPartOfTheForagersMayBeOut() {
        double out = Door.step(DOOR, 40, 30, 30, 10, 300, false, 0.5F);
        assertTrue(out <= 20 + 1e-9, "after danger at most half are out: " + out);
    }

    @Test
    void thePoissonGuessIsRight() {
        assertEquals(1.0, Door.atLeast(2.0, 0), 1e-12);
        assertEquals(1 - Math.exp(-2.0), Door.atLeast(2.0, 1), 1e-12);
        assertEquals(1 - Math.exp(-2.0) * 3, Door.atLeast(2.0, 2), 1e-12);
    }

    @Test
    void patrollersScaleWithTheColony() {
        assertEquals(2, PATROL.dawnPatrol(10));
        assertEquals(4, PATROL.dawnPatrol(25));
        assertEquals(8, PATROL.dawnPatrol(50));
        assertEquals(16, PATROL.dawnPatrol(100));
        assertEquals(1, PATROL.dawnPatrol(3), "a tiny colony still sends one");
        assertEquals(1, PATROL.dawnPatrol(1));
        assertEquals(0, PATROL.dawnPatrol(0));
        assertEquals(480, PATROL.dawnPatrol(3000));
        assertEquals(4, PATROL.laterPatrol(50), "after the midday rest: half");
        assertEquals(1, PATROL.laterPatrol(10));
        assertEquals(1, PATROL.laterPatrol(3));
    }
}
