package com.antcolonies.savanna.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

/** The dice of new land's termite mounds (M8 step 1b): the user's spreads come out of {@link TermiteMoundStructure#pick}. */
class TermiteMoundPickTest {
    @Test
    void theMoundsComeOutInTheRealSpreads() {
        RandomSource random = RandomSource.create(42L);
        int n = 20000, dead = 0, oldHumps = 0, wooded = 0, burrowed = 0;
        int[] living = new int[3];
        for (int i = 0; i < n; i++) {
            TermiteMoundStructure.Pick pick = TermiteMoundStructure.pick(TermiteMoundStructure.Numbers.DEFAULT, random);
            assertTrue(pick.height() > 0 && pick.height() <= 1, "height " + pick.height());
            if (pick.living()) {
                living[pick.size()]++;
                assertEquals(TermiteMoundStructure.NO_WOODS, pick.woods(), "woods on a living mound");
                assertTrue(!pick.burrow() && !pick.grass(), "a burrow or hump grass on a living mound");
                continue;
            }
            dead++;
            if (pick.isOldHump()) {
                oldHumps++;
                wooded += pick.woods() != TermiteMoundStructure.NO_WOODS ? 1 : 0;
                burrowed += pick.burrow() ? 1 : 0;
                assertTrue(pick.grass(), "an old hump without its grass");
            } else {
                assertTrue(pick.woods() == TermiteMoundStructure.NO_WOODS && !pick.burrow(), "woods or a burrow on a mound not yet a hump");
            }
        }
        // About 1 in 3 dead (the user's pick).
        assertTrue(Math.abs(dead / (double) n - 0.33) < 0.02, "dead share " + dead / (double) n);
        // The living by the real age mix: mostly full, some half grown, a few young (6 : 3 : 1).
        int alive = n - dead;
        assertTrue(Math.abs(living[0] / (double) alive - 0.6) < 0.03, "full share " + living[0] / (double) alive);
        assertTrue(Math.abs(living[1] / (double) alive - 0.3) < 0.03, "half share " + living[1] / (double) alive);
        assertTrue(Math.abs(living[2] / (double) alive - 0.1) < 0.02, "young share " + living[2] / (double) alive);
        // Dead at every stage, the old humps the most (45 in 100); of them half wooded, 4 in 10 burrowed.
        assertTrue(Math.abs(oldHumps / (double) dead - 0.45) < 0.03, "old hump share " + oldHumps / (double) dead);
        assertTrue(Math.abs(wooded / (double) oldHumps - 0.5) < 0.04, "wooded share " + wooded / (double) oldHumps);
        assertTrue(Math.abs(burrowed / (double) oldHumps - 0.4) < 0.04, "burrowed share " + burrowed / (double) oldHumps);
    }

    @Test
    void aMoundsMiddleIsFixedByTheSeedAndTheChunkAlone() {
        for (int x = -20; x <= 20; x += 7) {
            for (int z = -20; z <= 20; z += 5) {
                ChunkPos chunk = new ChunkPos(x, z);
                BlockPos a = TermiteMoundStructure.middle(1234L, chunk), b = TermiteMoundStructure.middle(1234L, chunk);
                assertEquals(a, b);
                int inX = a.getX() - chunk.getMinBlockX(), inZ = a.getZ() - chunk.getMinBlockZ();
                assertTrue(inX >= 3 && inX <= 12 && inZ >= 3 && inZ <= 12, "middle " + inX + ", " + inZ + " in its chunk");
            }
        }
    }
}
