package com.antcolonies.species;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Brain step 4g, step 4: how likely nestmates find the body of a member lost out of sight (the user's idea). */
class LostNumbersTest {
    private final SpeciesProfile.LostNumbers lost = new SpeciesProfile.LostNumbers(0.1, 0.5, true, true, 6.0, 0.03);

    @Test
    void aBodyNearTheNestIsAlwaysFound() {
        assertEquals(1.0, lost.foundChance(0), 1e-9);
        assertEquals(1.0, lost.foundChance(6), 1e-9);
    }

    @Test
    void eachBlockFartherOutIsLessLikely() {
        assertEquals(0.97, lost.foundChance(7), 1e-9);
        assertEquals(0.88, lost.foundChance(10), 1e-9);
        assertEquals(0.4, lost.foundChance(26), 1e-9);
    }

    @Test
    void farOutABodyIsNeverFound() {
        assertEquals(0.0, lost.foundChance(40), 1e-9);
        assertEquals(0.0, lost.foundChance(96), 1e-9);
    }
}
