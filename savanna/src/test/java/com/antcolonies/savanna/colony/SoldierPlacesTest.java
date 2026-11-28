package com.antcolonies.savanna.colony;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * Review R5-57: the real soldier places are shared between the minors and the majors in the colony's own ratio (3 to 1),
 * so the minors' many beats at night, or an alarm calling every soldier, never send every real major into the numbers.
 */
class SoldierPlacesTest {
    @Test
    void theMajorsKeepTheirShareWhenTheMinorsAskForEveryPlace() {
        // A full colony (15 minors, 5 majors) called by an alarm, 12 places: 9 minors and 3 majors.
        assertArrayEquals(new int[] {9, 3}, Soldiers.shareOut(new int[] {15, 5}, 12, 3));
        // At night the minors have 14 beats and the majors 2: the majors keep both, the minors take the rest.
        assertArrayEquals(new int[] {10, 2}, Soldiers.shareOut(new int[] {14, 2}, 12, 3));
    }

    @Test
    void aRankThatNeedsLessLeavesItsPlacesToTheOther() {
        assertArrayEquals(new int[] {4, 5}, Soldiers.shareOut(new int[] {4, 5}, 12, 3));
        assertArrayEquals(new int[] {2, 8}, Soldiers.shareOut(new int[] {2, 8}, 12, 3));
        assertArrayEquals(new int[] {0, 0}, Soldiers.shareOut(new int[] {0, 0}, 12, 3));
        assertArrayEquals(new int[] {12, 0}, Soldiers.shareOut(new int[] {20, 0}, 12, 3));
    }
}
