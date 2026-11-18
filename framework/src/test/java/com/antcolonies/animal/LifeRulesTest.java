package com.antcolonies.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/** The animal life kit's rules with no Minecraft: a life file read, growing up by age, and a clutch's clock. */
class LifeRulesTest {
    private static LifeNumbers file(String json) {
        return LifeNumbers.read(JsonParser.parseString(json));
    }

    @Test
    void aFileNamesOnlyWhatItChanges() {
        LifeNumbers n = file("{\"size\": [0.8, 1.1], \"max_size\": 1.1}");
        assertEquals(0.8F, n.maleSize()[0]);
        assertEquals(1.1F, n.femaleSize()[1]);
        assertEquals(1.1F, n.maxSize());
        assertFalse(n.breeds(), "breeding is off unless a file says so");
        assertEquals(0, n.grownAge(), "born grown with no young days");
        assertEquals(LifeStage.GROWN, n.stageAt(0));
        assertEquals(1.0F, n.grownShare(0));
    }

    @Test
    void theTilapiaGrowsFromHalfToWholeInThreeDays() {
        LifeNumbers n = file("{\"young_size\": 0.5, \"young_days\": 3}");
        int day = LifeNumbers.DAY;
        assertEquals(3 * day, n.grownAge());
        assertEquals(0.5F, n.grownShare(0), 1e-6);
        assertEquals(0.75F, n.grownShare(3 * day / 2), 1e-6);
        assertEquals(1.0F, n.grownShare(3 * day), 1e-6);
        assertEquals(LifeStage.YOUNG, n.stageAt(3 * day - 1));
        assertEquals(LifeStage.GROWN, n.stageAt(3 * day));
    }

    @Test
    void aTeenStageSitsBetweenYoungAndGrown() {
        LifeNumbers n = file("{\"young_size\": 0.1, \"young_days\": 2, \"teen_size\": 0.4, \"teen_days\": 4}");
        int day = LifeNumbers.DAY;
        assertEquals(0.4F, n.grownShare(2 * day), 1e-6, "the young grow to the teen's start");
        assertEquals(LifeStage.TEEN, n.stageAt(2 * day));
        assertEquals(0.7F, n.grownShare(4 * day), 1e-6);
        assertEquals(LifeStage.GROWN, n.stageAt(6 * day));
    }

    @Test
    void snailsAreBothSexesAndWrongWordsStopTheLoad() {
        assertFalse(file("{\"sexes\": \"both\"}").twoSexes());
        assertThrows(IllegalArgumentException.class, () -> file("{\"sexes\": \"three\"}"));
        assertThrows(IllegalArgumentException.class, () -> file("{\"size\": [1.2, 0.8]}"));
        assertThrows(IllegalArgumentException.class, () -> file("{\"seasons\": [\"winter\"]}"));
        assertThrows(IllegalArgumentException.class, () -> file("{\"care\": \"hug\"}"));
    }

    @Test
    void aClutchHatchesOnlyOldWatchedAndWithRoom() {
        long hatch = 2L * LifeNumbers.DAY;
        assertEquals(ClutchClock.Now.WAIT, ClutchClock.decide(hatch - 1, 0, hatch, true, true), "too young");
        assertEquals(ClutchClock.Now.HATCH, ClutchClock.decide(hatch, 0, hatch, true, true));
        assertEquals(ClutchClock.Now.WAIT, ClutchClock.decide(hatch, 0, hatch, false, true), "nobody near");
        assertEquals(ClutchClock.Now.WAIT, ClutchClock.decide(hatch, 0, hatch, true, false), "no room");
        assertEquals(ClutchClock.Now.SHELLS, ClutchClock.decide(ClutchClock.SHELLS_AFTER, 0, hatch, false, true),
                "long past hatching unseen: only shells");
        assertTrue(ClutchClock.SHELLS_AFTER > hatch);
    }
}
