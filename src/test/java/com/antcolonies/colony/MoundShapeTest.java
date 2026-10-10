package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Checks the planned shape of the ant hill. */
class MoundShapeTest {
    @Test
    void theHillIsThreeTwoOneBlocksHighAndAddsUpTo64Blocks() {
        int total = 0;
        for (int dx = -Colony.MOUND_RADIUS - 2; dx <= Colony.MOUND_RADIUS + 2; dx++) {
            for (int dz = -Colony.MOUND_RADIUS - 2; dz <= Colony.MOUND_RADIUS + 2; dz++) {
                total += Colony.moundHeightAt(dx, dz);
            }
        }
        assertEquals(64, total);
    }

    @Test
    void theHoleStaysOpenAndTheRingsStepDown() {
        assertEquals(0, Colony.moundHeightAt(0, 0));
        // First ring: the eight blocks touching the hole.
        assertEquals(3, Colony.moundHeightAt(1, 0));
        assertEquals(3, Colony.moundHeightAt(0, -1));
        assertEquals(3, Colony.moundHeightAt(1, 1));
        // Second ring.
        assertEquals(2, Colony.moundHeightAt(2, 0));
        assertEquals(2, Colony.moundHeightAt(-1, 2));
        // Third ring.
        assertEquals(1, Colony.moundHeightAt(3, 0));
        assertEquals(1, Colony.moundHeightAt(2, 2));
        // Outside the hill.
        assertEquals(0, Colony.moundHeightAt(4, 0));
        assertEquals(0, Colony.moundHeightAt(3, 2));
    }

    @Test
    void theHillIsTheSameOnAllFourSides() {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                assertEquals(Colony.moundHeightAt(dx, dz), Colony.moundHeightAt(-dx, dz));
                assertEquals(Colony.moundHeightAt(dx, dz), Colony.moundHeightAt(dx, -dz));
                assertEquals(Colony.moundHeightAt(dx, dz), Colony.moundHeightAt(dz, dx));
            }
        }
    }
}
