package com.antcolonies.savanna.entity.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.antcolonies.entity.body.BlockMoves;

/**
 * The shared move player reads the Nile monitor's move file (the Animal Maker Engine step B, 2026-11-20) as the game
 * does, from the mod's own files: every move with one clip and path a size, the paths ending where the moves end (a
 * block up, a block down), and the turn-square rule.
 */
class BlockMovesFileTest {
    private static final BlockMoves MOVES = BlockMoves.of(com.antcolonies.savanna.SavannaMod.class, "antcolonies", "monitor");

    @Test
    void theMonitorsMovesAreAllThere() {
        assertEquals(8, MOVES.count());
        assertEquals(5, MOVES.sizes().length);
        String[] names = {"up", "down", "in", "drop", "drop2", "dash", "out", "out_up"};
        int[] ticks = {66, 69, 126, 102, 92, 38, 92, 90};
        for (int i = 0; i < names.length; i++) {
            assertEquals(i, MOVES.index(names[i]));
            assertEquals(ticks[i], MOVES.move(i).ticks);
        }
        assertEquals("climb_up_1000", MOVES.move(0).clip(2));
        assertEquals("water_outup_850", MOVES.move(7).clip(0));
        assertEquals(2, MOVES.nearest(1.0));
        assertEquals(0, MOVES.nearest(0.5));
        assertEquals(50.0, MOVES.move(0).aheadPx);
        assertEquals(57, MOVES.move(2).wetTick);
        assertTrue(MOVES.move(2).endsAfloat && MOVES.move(2).goesDown && !MOVES.move(2).startsAfloat);
        assertTrue(MOVES.move(6).startsAfloat && !MOVES.move(6).goesDown);
    }

    @Test
    void theClimbsEndABlockUpAndABlockDown() {
        for (int size = 0; size < 5; size++) {
            assertEquals(1.0, MOVES.move(0).rise(size, 66), 0.02, "climb up, size " + size);
            assertEquals(-1.0, MOVES.move(1).rise(size, 69), 0.02, "climb down, size " + size);
            // held before the clip and after it
            assertEquals(0.0, MOVES.move(0).rise(size, -5), 1.0E-6);
            assertEquals(MOVES.move(0).rise(size, 66), MOVES.move(0).rise(size, 80), 1.0E-6);
        }
        assertEquals(1.0, MOVES.move(0).endLevel);
        assertEquals(-1.0, MOVES.move(1).endLevel);
    }

    @Test
    void itTurnsSquareFirstOnlyWhenFarOff() {
        assertEquals(0, BlockMoves.turnFor(10.0F, 0.0F));
        assertEquals(0, BlockMoves.turnFor(-30.0F, 0.0F));
        assertEquals(8, BlockMoves.turnFor(45.0F, 0.0F));
        assertEquals(15, BlockMoves.turnFor(270.0F, 0.0F));
        assertEquals(-1, BlockMoves.turnFor(180.0F, 0.0F));
    }
}
