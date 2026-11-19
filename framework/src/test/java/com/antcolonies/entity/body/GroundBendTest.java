package com.antcolonies.entity.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The body lying on what is under it (the crocodile's life step 1, 2026-11-15): flat ground leaves it straight; the head
 * over water a block below the bank tips down while the tail on the bank stays flat; coming out, the front rises onto the
 * bank; no joint ever bends past its reach (the first piece of a chain only by how steep it lies).
 */
class GroundBendTest {
    private static final double[] LENGTHS = {0.75, 0.7, 0.7, 0.7, 0.7};

    private static double[] solve(double anchor, double... targets) {
        double[] out = new double[targets.length];
        GroundBend.solve(anchor, targets, LENGTHS, GroundBend.MAX_JOINT, GroundBend.MAX_STEEP, out);
        return out;
    }

    @Test
    void flatGroundLeavesItStraight() {
        for (double a : solve(64, 64, 64, 64, 64, 64)) {
            assertEquals(0, a, 1.0E-9);
        }
    }

    @Test
    void piecesOverLowerGroundTipDownAndThoseOnTheBankStayFlat() {
        // the first two pieces still on the bank, the rest out over water whose resting line is 1.4 blocks lower
        double[] lie = solve(64, 64, 64, 62.6, 62.6, 62.6);
        assertEquals(0, lie[0], 1.0E-9);
        assertEquals(0, lie[1], 1.0E-9);
        assertTrue(lie[2] < -20, "the piece over the edge tips down: " + lie[2]);
        assertTrue(lie[4] > lie[3], "the pieces beyond level out toward the water: " + lie[3] + " then " + lie[4]);
    }

    @Test
    void comingOutTheFrontRisesOntoTheBank() {
        double[] lie = solve(62.6, 64, 64, 64);
        assertTrue(lie[0] > 0, "it tips up toward the bank: " + lie[0]);
    }

    @Test
    void noJointBendsPastItsReach() {
        double[] lie = solve(64, 70, 50, 70, 50, 70);
        for (int j = 0; j < lie.length; j++) {
            if (j > 0) {
                assertTrue(Math.abs(lie[j] - lie[j - 1]) <= GroundBend.MAX_JOINT + 1.0E-9, "a joint bent " + (lie[j] - lie[j - 1]));
            }
            assertTrue(Math.abs(lie[j]) <= GroundBend.MAX_STEEP + 1.0E-9, "a piece lies at " + lie[j]);
        }
    }

    /**
     * The ground line (the Animal Maker Engine step 2, 2026-11-19): a one-block step becomes a ramp with no jump in it,
     * rising all the way from one level to the other, its middle a little toward the low side so the belly clears the
     * corner, and flat ground stays flat.
     */
    @Test
    void aStepBecomesASmoothRampTowardTheLowSide() {
        int n = 200, edge = 100, widen = 8, ramp = 20;
        double[] read = new double[n], wide = new double[n], sums = new double[n + 1];
        for (int i = 0; i < n; i++) {
            read[i] = i < edge ? 64 : 65;
        }
        GroundBend.line(read, n, widen, wide, sums);
        double before = GroundBend.average(sums, n, 0, ramp);
        assertEquals(64, before, 1.0E-9);
        assertEquals(65, GroundBend.average(sums, n, n - 1, ramp), 1.0E-9);
        for (int i = 1; i < n; i++) {
            double h = GroundBend.average(sums, n, i, ramp);
            assertTrue(h >= before - 1.0E-9, "the ramp only rises: " + h + " after " + before);
            assertTrue(h - before <= 1.0 / (2 * ramp + 1) + 1.0E-9, "no jump bigger than one point's share: " + (h - before));
            before = h;
        }
        assertTrue(GroundBend.average(sums, n, edge, ramp) > 64.6, "at the edge the line is past half way up");
    }
}
