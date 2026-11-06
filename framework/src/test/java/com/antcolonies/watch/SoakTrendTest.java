package com.antcolonies.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The raid test bench, B5: the 100-day soak's slow things read back from its trend lines ({@link Soak#slowThings}). */
class SoakTrendTest {
    private static String day(int n, double ms, int insects, int saves, int strays, int members) {
        return "day " + n + " | ms " + ms + " | insects " + insects + " | saves " + saves + " kB | strays " + strays
                + " | colonies macrotermes 2 megaponera_analis 1 | 6d630de1 members " + members + " save 12 kB";
    }

    @Test
    void readsTheNumbersOfATrendLine() {
        String row = day(3, 4.25, 120, 300, 0, 450);
        assertEquals(4.25, Soak.number(row, "ms "), 1e-9);
        assertEquals(300, Soak.number(row, "saves "), 1e-9);
        assertEquals(0, Soak.number(row, "nothing "), 1e-9);
        assertEquals(450, Soak.members(row).get("6d630de1"), 1e-9);
        assertEquals(1, Soak.members(row).size());
    }

    @Test
    void aSteadyGameFlagsNothing() {
        List<String> rows = List.of(day(1, 3, 100, 300, 0, 400), day(2, 3.1, 104, 302, 0, 410), day(3, 3.2, 98, 303, 0, 420),
                day(4, 3.0, 101, 304, 0, 415), day(5, 3.1, 99, 305, 0, 430), day(6, 3.2, 103, 306, 0, 425));
        assertFalse(String.join("\n", Soak.slowThings(rows)).contains("LOOK:"));
    }

    @Test
    void slowerTicksGrowingSavesStraysAndADyingColonyAreFlagged() {
        List<String> rows = List.of(day(1, 3, 100, 300, 0, 400), day(2, 3, 100, 330, 0, 300), day(3, 3, 100, 400, 0, 200),
                day(4, 9, 100, 500, 1, 150), day(5, 9, 100, 600, 2, 120), day(6, 9, 100, 700, 2, 100));
        String report = String.join("\n", Soak.slowThings(rows));
        assertTrue(report.contains("LOOK: ms a tick"), report);
        assertTrue(report.contains("LOOK: saves"), report);
        assertTrue(report.contains("LOOK: strays"), report);
        assertTrue(report.contains("LOOK: colony 6d630de1"), report);
        assertFalse(report.contains("LOOK: real insects"), report);
    }

    @Test
    void aColonyGoneIsFlagged() {
        List<String> rows = List.of(day(1, 3, 100, 300, 0, 400), "day 2 | ms 3 | insects 100 | saves 300 kB | strays 0 | colonies macrotermes 2");
        assertTrue(String.join("\n", Soak.slowThings(rows)).contains("LOOK: watched colony 6d630de1 is gone"));
    }
}
