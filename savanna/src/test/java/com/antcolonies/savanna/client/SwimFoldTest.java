package com.antcolonies.savanna.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Nile monitor's leg fold is the swim clip's own first pose, both sides as the clip has them (code review R12-70,
 * 2026-11-26): the arms are mirror images of each other in the clip, the hind legs' lower leg and foot are not (the
 * same angles on both sides), which the old hand-copied table with its "the right side mirrors y and z" got wrong.
 */
class SwimFoldTest {
    private static final double[][][][] FOLD = SwimFold.read("/assets/antcolonies/animations/entity/monitor.animation.json", "swim");
    private static final int ARM = 0, LEG = 1, LEFT = 0, RIGHT = 1, UPPER = 0, LOWER = 1, FOOT = 2;

    @Test
    void everyLegPieceOfBothSidesIsRead() {
        for (double[][][] pair : FOLD) {
            for (double[][] side : pair) {
                assertEquals(3, side.length);
                for (double[] piece : side) {
                    assertEquals(3, piece.length);
                }
            }
        }
        // folded back: the lower arm is bent far round, the hind foot turned back along the tail
        assertTrue(Math.abs(FOLD[ARM][LEFT][LOWER][0]) > 60.0, "the lower arm is not folded");
        assertTrue(Math.abs(FOLD[LEG][LEFT][FOOT][2]) > 60.0, "the hind foot is not turned back");
    }

    /** What the old rule assumed, and where it holds: the arms. */
    @Test
    void theArmsAreMirrorImages() {
        for (int piece = 0; piece < 3; piece++) {
            assertMirrored(FOLD[ARM][LEFT][piece], FOLD[ARM][RIGHT][piece], "arm piece " + piece);
        }
        assertMirrored(FOLD[LEG][LEFT][UPPER], FOLD[LEG][RIGHT][UPPER], "upper hind leg");
    }

    /**
     * Where it does not: the hind lower leg and foot carry the same angles on both sides in the clip. Should the maker
     * one day write them mirrored, this test fails and only says so; the game reads the clip either way.
     */
    @Test
    void theHindLowerLegAndFootAreTheClipsOwnNotAMirror() {
        for (int piece : new int[] {LOWER, FOOT}) {
            double[] left = FOLD[LEG][LEFT][piece], right = FOLD[LEG][RIGHT][piece];
            assertEquals(left[1], right[1], 0.01, "hind piece " + piece + " y");
            assertEquals(left[2], right[2], 0.01, "hind piece " + piece + " z");
            assertTrue(Math.abs(left[2]) > 5.0, "hind piece " + piece + " turns about z: a mirror would turn it the other way");
        }
    }

    private static void assertMirrored(double[] left, double[] right, String what) {
        assertEquals(left[0], right[0], 0.01, what + " x");
        assertEquals(left[1], -right[1], 0.01, what + " y");
        assertEquals(left[2], -right[2], 0.01, what + " z");
    }
}
