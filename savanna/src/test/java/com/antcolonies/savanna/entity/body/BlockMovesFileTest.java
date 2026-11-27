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
        // (the walk-in remade 2026-11-22; the same day every way in drifts and folds slowly at its end, 2 s + 2 s longer)
        int[] ticks = {66, 69, 177, 161, 151, 103, 92, 90};
        for (int i = 0; i < names.length; i++) {
            assertEquals(i, MOVES.index(names[i]));
            assertEquals(ticks[i], MOVES.move(i).ticks(0));    // (the smallest size's length)
        }
        // review R9-38: a bigger monitor's walk-in is as long as its own clip, not the smallest size's
        assertEquals(213, MOVES.move(2).ticks(4));
        assertEquals("climb_up_1000", MOVES.move(0).clip(2));
        assertEquals("water_outup_850", MOVES.move(7).clip(0));
        assertEquals(2, MOVES.nearest(1.0));
        assertEquals(0, MOVES.nearest(0.5));
        assertEquals(50.0, MOVES.move(0).aheadPx);
        assertEquals(61, MOVES.move(2).wetTick);
        assertEquals(50.0, MOVES.move(2).aheadPx);         // (walking in from about two blocks back)
        assertTrue(MOVES.move(2).endsAfloat && MOVES.move(2).goesDown && !MOVES.move(2).startsAfloat);
        assertTrue(MOVES.move(6).startsAfloat && !MOVES.move(6).goesDown);
    }

    /**
     * Code review R12-72: every animal's move file loads as the game loads it and holds whole moves (only the monitor's
     * was read here; the otter's and hippo's were checked by nothing but the game starting, the lions' and the
     * buffalo's by nothing at all until the day they are wired in).
     */
    @Test
    void everyAnimalsMoveFileLoads() {
        for (String animal : new String[] {"monitor", "otter", "hippo", "buffalo", "lion", "lioness", "lion_cub"}) {
            BlockMoves moves = BlockMoves.of(com.antcolonies.savanna.SavannaMod.class, "antcolonies", animal);
            assertTrue(moves.count() > 0 && moves.sizes().length > 0, animal + ": no moves or no sizes");
            for (int i = 0; i < moves.count(); i++) {
                for (int size = 0; size < moves.sizes().length; size++) {
                    int ticks = moves.move(i).ticks(size);
                    assertTrue(ticks > 0, animal + ", move " + i + ", size " + size + ": no ticks");
                    assertTrue(moves.move(i).clip(size) != null && !moves.move(i).clip(size).isEmpty(), animal + ", move " + i + ": no clip");
                    assertTrue(Double.isFinite(moves.move(i).rise(size, ticks)) && Math.abs(moves.move(i).rise(size, ticks)) < 4.0,
                            animal + ", move " + i + ", size " + size + ": its path ends at " + moves.move(i).rise(size, ticks));
                }
            }
        }
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

    /**
     * Code review R12-58: after its clip a move that ends on land stands where its path ends (through its rest the box
     * and the drawing slid on at the move's end speed, the otter about 0.9 blocks after a step down, its legs still);
     * a move that ends afloat glides on at its last speed into the swim, as before.
     */
    @Test
    void afterItsClipALandMoveStandsAndAWaterMoveGlidesOn() {
        BlockMoves otter = BlockMoves.of(com.antcolonies.savanna.SavannaMod.class, "antcolonies", "otter");
        BlockMoves.Move down = otter.move(otter.index("down"));
        assertTrue(!down.endsAfloat && down.endSpeed(0) > 1.0, "the otter's step down ends on land, still going");
        int end = down.ticks(0);
        assertEquals(down.reach(0), down.along(0, end + otter.rest()), 1.0E-6, "it stands through its rest");
        BlockMoves.Play play = new BlockMoves.Play(0.5, 64.0, 0.5, 0.0F, 0.0F, 1000L, 0, otter.index("down"), 0);
        assertEquals(otter.drawnAt(play, end, 1 / 16.0), otter.drawnAt(play, end + otter.rest(), 1 / 16.0), "and is drawn standing");
        BlockMoves.Move in = MOVES.move(MOVES.index("in"));
        assertTrue(in.endsAfloat);
        assertEquals(in.reach(0) + 8 * in.endSpeed(0), in.along(0, in.ticks(0) + 8), 1.0E-4, "the walk into the water glides on");
    }

    /**
     * Code review R12-59: where a move is drawn comes from the move's own time since it started, whatever its clip
     * player is doing (an animal first looked at mid-move was drawn back at the move's start while its clip eased in).
     */
    @Test
    void aMoveIsDrawnAtItsOwnTime() {
        BlockMoves.Play play = new BlockMoves.Play(0.5, 64.0, 0.5, 0.0F, 0.0F, 1000L, 0, MOVES.index("up"), 0);
        com.antcolonies.entity.body.MoveClock clock = new com.antcolonies.entity.body.MoveClock();
        assertEquals(0.0, MOVES.drawnTicks(play, clock, 1000L, 0.5), "easing in: at its start");
        assertEquals(30.5, MOVES.drawnTicks(play, clock, 1030L + MOVES.lead(), 0.5), 1.0E-9);
        // a frozen game draws with a whole tick: the tick's start, as the clip's own clock reads it
        assertEquals(31.0, MOVES.drawnTicks(play, clock, 1031L + MOVES.lead(), 1.0), 1.0E-9);
        // the clip player's tick (the animal's age and the part of a tick): only the part of a tick is read
        assertEquals(32.25, MOVES.drawnTicks(play, clock, 1032L + MOVES.lead(), 8041.25), 1.0E-9);
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
