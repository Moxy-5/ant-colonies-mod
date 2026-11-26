package com.antcolonies.savanna.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The fruit's calendar (floodplain trees steps 4b and 6b; review R7-32): a tree in each phase goes green, turning, ripe
 * and bare once every cycle, a new crop's number starting with the green, and the phases start at different times, so
 * a riverbank never fruits all at once.
 */
class HangingFruitTest {
    private static final long DAY = 24000;

    @Test
    void eachPhaseBearsOneCropACycleInOrder() {
        for (HangingFruit.Fruit fruit : HangingFruit.Fruit.values()) {
            long cycle = (long) (fruit.cycleDays * DAY);
            Set<Long> starts = new HashSet<>();
            for (int phase = 0; phase < 8; phase++) {
                long start = -1;
                int changes = 0;
                int was = HangingFruit.cropStage(fruit, phase, -100), crop = HangingFruit.cropNow(fruit, phase, -100);
                for (long t = 0; t < cycle; t += 100) {
                    int now = HangingFruit.cropStage(fruit, phase, t);
                    if (now != was) {
                        changes++;
                        assertEquals(was == HangingFruit.RIPE ? HangingFruit.BARE : was + 1, now, fruit + " phase " + phase + " went from stage " + was + " to " + now);
                    }
                    boolean newCrop = HangingFruit.cropNow(fruit, phase, t) != crop;
                    assertEquals(now == HangingFruit.GREEN && was == HangingFruit.BARE, newCrop, fruit + " phase " + phase + ": the crop's number must change with the green, at " + t);
                    if (newCrop) {
                        start = t;
                        crop = HangingFruit.cropNow(fruit, phase, t);
                    }
                    was = now;
                }
                assertEquals(4, changes, fruit + " phase " + phase + " changed stage " + changes + " times in a cycle");
                assertTrue(start >= 0, fruit + " phase " + phase + " never starts a crop");
                starts.add(start);
            }
            assertEquals(8, starts.size(), fruit + ": phases share their start " + starts);
        }
    }

    @Test
    void phasesSpreadOverEveryPlace() {
        int[] seen = new int[8];
        for (long seed = 0; seed < 8000; seed++) {
            seen[HangingFruit.phaseOf(seed)]++;
        }
        for (int phase = 0; phase < 8; phase++) {
            assertTrue(seen[phase] > 800 && seen[phase] < 1200, "phase " + phase + " came " + seen[phase] + " times in 8000");
        }
    }
}
