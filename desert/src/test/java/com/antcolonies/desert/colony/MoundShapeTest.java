package com.antcolonies.desert.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Checks the planned shape of the ant hill. */
class MoundShapeTest {
    @Test
    void theHillIsThreeTwoOneBlocksHighAndAddsUpTo64Blocks() {
        int total = 0;
        for (int dx = -Hill.MOUND_RADIUS - 2; dx <= Hill.MOUND_RADIUS + 2; dx++) {
            for (int dz = -Hill.MOUND_RADIUS - 2; dz <= Hill.MOUND_RADIUS + 2; dz++) {
                total += Hill.moundHeightAt(dx, dz);
            }
        }
        assertEquals(64, total);
    }

    @Test
    void theHoleStaysOpenAndTheRingsStepDown() {
        assertEquals(0, Hill.moundHeightAt(0, 0));
        // First ring: the eight blocks touching the hole.
        assertEquals(3, Hill.moundHeightAt(1, 0));
        assertEquals(3, Hill.moundHeightAt(0, -1));
        assertEquals(3, Hill.moundHeightAt(1, 1));
        // Second ring.
        assertEquals(2, Hill.moundHeightAt(2, 0));
        assertEquals(2, Hill.moundHeightAt(-1, 2));
        // Third ring.
        assertEquals(1, Hill.moundHeightAt(3, 0));
        assertEquals(1, Hill.moundHeightAt(2, 2));
        // Outside the hill.
        assertEquals(0, Hill.moundHeightAt(4, 0));
        assertEquals(0, Hill.moundHeightAt(3, 2));
    }

    @Test
    void theHillIsTheSameOnAllFourSides() {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                assertEquals(Hill.moundHeightAt(dx, dz), Hill.moundHeightAt(-dx, dz));
                assertEquals(Hill.moundHeightAt(dx, dz), Hill.moundHeightAt(dx, -dz));
                assertEquals(Hill.moundHeightAt(dx, dz), Hill.moundHeightAt(dz, dx));
            }
        }
    }
}
