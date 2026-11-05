package com.antcolonies.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The raid test bench, B2: the black box keeps the last few minutes in order and forgets the oldest, by age and by
 * count ({@link BlackBox}).
 */
class BlackBoxTest {
    @Test
    void keepsLinesInOrder() {
        BlackBox box = new BlackBox();
        box.add(10, "a");
        box.add(11, "b");
        box.add(11, "c");
        assertEquals(List.of("a", "b", "c"), box.lines().stream().map(BlackBox.Line::text).toList());
    }

    @Test
    void forgetsLinesOlderThanItKeeps() {
        BlackBox box = new BlackBox(100, 1000);
        box.add(0, "old");
        box.add(50, "middle");
        box.add(120, "new");
        assertEquals(List.of("middle", "new"), box.lines().stream().map(BlackBox.Line::text).toList());
        box.add(1000, "newest");
        assertEquals(List.of("newest"), box.lines().stream().map(BlackBox.Line::text).toList());
    }

    @Test
    void forgetsTheOldestPastItsMost() {
        BlackBox box = new BlackBox(100000, 3);
        for (int i = 0; i < 5; i++) {
            box.add(i, "line " + i);
        }
        assertEquals(3, box.size());
        assertEquals("line 2", box.lines().get(0).text());
    }
}
