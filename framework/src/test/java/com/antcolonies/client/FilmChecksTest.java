package com.antcolonies.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * The camera's jump check (code review R12-41, 2026-11-26): a part's own big moves in the scene must not hide a pop
 * that comes back, nor a part snapping out and back in a short take, while a leg's quick swing stays no jump. The
 * numbers are a part's moves from one picture to the next (blocks a tick), as {@code FilmChecks.report} measures them
 * (the first is never a move: there is no picture before it).
 */
class FilmChecksTest {
    private static final double SLOW = 0.02, POP = 0.5;

    private static double[] steady(int pictures) {
        double[] moves = new double[pictures];
        Arrays.fill(moves, SLOW);
        return moves;
    }

    private static boolean jump(double[] moves, int i) {
        return FilmChecks.jump(moves, i, FilmChecks.bigMoves(moves));
    }

    /** One pop in a long take: a jump before the change and after it. */
    @Test
    void aSinglePopIsAJump() {
        double[] moves = steady(40);
        moves[20] = POP;
        assertTrue(jump(moves, 20));
        assertFalse(jump(moves, 19));
        assertFalse(jump(moves, 21));
    }

    /** A loop's seam popping every five pictures: 1 move in 5 is the pop, so the part's "big moves" are the pop itself. */
    @Test
    void aPopThatComesBackEveryCycleIsAJump() {
        double[] moves = steady(41);
        for (int i = 5; i < moves.length; i += 5) {
            moves[i] = POP;
        }
        assertTrue(FilmChecks.bigMoves(moves) >= POP, "the scene-wide bar is the pop (what hid it)");
        for (int i = 5; i < moves.length; i += 5) {
            assertTrue(jump(moves, i), "the pop at picture " + i);
        }
        assertFalse(jump(moves, 7));
    }

    /** A part snapping to a wrong pose for one picture and back, in a take of 12: two big moves, the second biggest the bar. */
    @Test
    void outAndBackInAShortTakeIsAJump() {
        double[] moves = steady(12);
        moves[5] = POP;
        moves[6] = POP;
        assertTrue(jump(moves, 5));
        assertTrue(jump(moves, 6));
    }

    /** A leg's quick swing, three pictures long every eight, four times its stance speed: no jump (the old rule's reason). */
    @Test
    void aQuickSwingIsNoJump() {
        double[] moves = steady(48);
        for (int i = 4; i < moves.length - 3; i += 8) {
            moves[i] = moves[i + 1] = moves[i + 2] = 4 * SLOW;
        }
        for (int i = 1; i < moves.length; i++) {
            assertFalse(jump(moves, i), "picture " + i);
        }
    }

    /** Two takes with the game running between them (no move measured): never a jump. */
    @Test
    void aGapBetweenTakesIsNoJump() {
        double[] moves = steady(20);
        moves[10] = Double.NaN;
        assertFalse(jump(moves, 10));
    }

    /**
     * A move is sized by the time between its two pictures (code review R12-44): four pictures a tick and one picture
     * every three ticks of the same steady walk read the same blocks a tick, and pictures of two takes are no move.
     */
    @Test
    void aMoveIsSizedByTheTimeBetweenItsPictures() {
        assertEquals(0.08, FilmChecks.aTick(0.02, 0.25), 1.0e-9);
        assertEquals(0.08, FilmChecks.aTick(0.24, 3.0), 1.0e-9);
        assertTrue(Double.isNaN(FilmChecks.aTick(0.5, FilmChecks.TAKE_GAP + 2)), "another take");
        assertTrue(Double.isNaN(FilmChecks.aTick(0.5, 0.0)), "the same moment");
        assertTrue(Double.isNaN(FilmChecks.aTick(0.5, -3.0)), "an earlier moment");
    }

    /** A pop between pictures three ticks apart is still a jump: its size is not divided away under the least a jump must be. */
    @Test
    void aSmallPopBetweenSparsePicturesIsStillAJump() {
        double[] moves = steady(30);
        for (int i = 0; i < moves.length; i++) {
            moves[i] = 0.002;
        }
        double popped = 0.06;                   // blocks, between two pictures 3 ticks apart: 0.02 a tick
        moves[15] = FilmChecks.aTick(popped, 3.0);
        assertFalse(FilmChecks.jump(moves, 15, FilmChecks.bigMoves(moves)), "by its blocks a tick alone it is under the least");
        assertTrue(FilmChecks.jump(moves, 15, FilmChecks.bigMoves(moves), Math.max(moves[15], popped)));
    }

    /** A point's depth in a box is its distance to the nearest face, nothing outside it (the block-edge test, R12-5). */
    @Test
    void aPointInsideABoxIsAsDeepAsItsNearestFace() {
        double[] lo = {0, 0, 0}, hi = {20, 6, 6};
        assertEquals(2.9, FilmChecks.insideBox(new double[] {10, 2.9, 3}, lo, hi), 1.0e-9);
        assertEquals(0.0, FilmChecks.insideBox(new double[] {10, -0.1, 3}, lo, hi), 1.0e-9);
        assertEquals(0.0, FilmChecks.insideBox(new double[] {21, 3, 3}, lo, hi), 1.0e-9);
    }
}
