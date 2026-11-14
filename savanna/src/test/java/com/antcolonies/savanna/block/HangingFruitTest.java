package com.antcolonies.savanna.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The fruit's calendar (floodplain trees steps 4b and 6b): a tree in each phase starts a new crop for its bud days once
 * every cycle, and the phases start at different times, so a riverbank never fruits all at once.
 */
class HangingFruitTest {
    private static final long DAY = 24000;

    @Test
    void eachPhaseBudsForItsBudDaysOnceACycle() {
        for (HangingFruit.Fruit fruit : HangingFruit.Fruit.values()) {
            long cycle = (long) (fruit.cycleDays * DAY);
            Set<Long> starts = new HashSet<>();
            for (int phase = 0; phase < 8; phase++) {
                long budding = 0, start = -1;
                boolean was = HangingFruit.cropStarts(fruit, phase, cycle - 100);
                for (long t = 0; t < cycle; t += 100) {
                    boolean now = HangingFruit.cropStarts(fruit, phase, t);
                    if (now) {
                        budding += 100;
                    }
                    if (now && !was) {
                        start = t;
                    }
                    was = now;
                }
                assertEquals(fruit.budDays * DAY, budding, 100, fruit + " phase " + phase + " buds " + budding / DAY + " days a cycle");
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
