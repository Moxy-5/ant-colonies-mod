package com.antcolonies.savanna.colony;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.antcolonies.savanna.block.DeadCombBlock;

/** The dead garden's days (M8 step 1a): furred, black, rotting layer by layer, gone; never back. */
class DeadMoundsTest {
    @Test
    void theCombGoesFurredBlackRottenGone() {
        assertArrayEquals(new int[] {DeadCombBlock.FURRED, 4}, DeadMounds.stage(0.0, 4));
        assertArrayEquals(new int[] {DeadCombBlock.FURRED, 3}, DeadMounds.stage(0.9, 3));
        assertArrayEquals(new int[] {DeadCombBlock.BLACK, 4}, DeadMounds.stage(1.0, 4));
        assertArrayEquals(new int[] {DeadCombBlock.BLACK, 2}, DeadMounds.stage(3.9, 2));
        assertArrayEquals(new int[] {DeadCombBlock.ROTTEN, 4}, DeadMounds.stage(4.0, 4));
        assertArrayEquals(new int[] {DeadCombBlock.ROTTEN, 1}, DeadMounds.stage(6.4, 4));
        assertNull(DeadMounds.stage(DeadMounds.GONE_DAYS, 4));
        assertNull(DeadMounds.stage(20, 1));
    }

    @Test
    void rottingLosesLayersAndNeverGrowsBack() {
        int last = 4;
        for (double day = DeadMounds.ROTTEN_DAYS; day < DeadMounds.GONE_DAYS; day += 0.05) {
            int[] now = DeadMounds.stage(day, 4);
            assertTrue(now[1] <= last && now[1] >= 1, "day " + day + ": " + now[1] + " layers after " + last);
            last = now[1];
        }
        assertTrue(last == 1, "the last rotting day still has " + last + " layers");
    }
}
