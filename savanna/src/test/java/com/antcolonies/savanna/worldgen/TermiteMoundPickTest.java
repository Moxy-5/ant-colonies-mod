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
        int[] living = new int[5], deadBands = new int[5];
        for (int i = 0; i < n; i++) {
            TermiteMoundStructure.Pick pick = TermiteMoundStructure.pick(TermiteMoundStructure.Numbers.DEFAULT, random);
            assertTrue(pick.height() > 0 && pick.height() <= 1, "height " + pick.height());
            assertTrue(pick.stage() >= 4, "stage " + pick.stage() + ": a first-year colony has no mound to see");
            if (pick.living()) {
                living[band(pick.stage())]++;
                assertEquals(TermiteMoundStructure.NO_WOODS, pick.woods(), "woods on a living mound");
                assertTrue(!pick.burrow() && !pick.grass(), "a burrow or hump grass on a living mound");
                continue;
            }
            dead++;
            deadBands[band(pick.stage())]++;
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
        // The living by the user's stage table (2026-10-24): 4-9 40, 10-14 28, 15-19 17, 20-24 10, 25-32 5 in 100;
        // the dead tilted bigger: 20, 25, 25, 18, 12.
        int alive = n - dead;
        double[] livingShares = {0.40, 0.28, 0.17, 0.10, 0.05}, deadShares = {0.20, 0.25, 0.25, 0.18, 0.12};
        for (int b = 0; b < 5; b++) {
            assertTrue(Math.abs(living[b] / (double) alive - livingShares[b]) < 0.025, "living band " + b + " share " + living[b] / (double) alive);
            assertTrue(Math.abs(deadBands[b] / (double) dead - deadShares[b]) < 0.03, "dead band " + b + " share " + deadBands[b] / (double) dead);
        }
        // Dead at every stage, the old humps the most (45 in 100); of them half wooded, 4 in 10 burrowed.
        assertTrue(Math.abs(oldHumps / (double) dead - 0.45) < 0.03, "old hump share " + oldHumps / (double) dead);
        assertTrue(Math.abs(wooded / (double) oldHumps - 0.5) < 0.04, "wooded share " + wooded / (double) oldHumps);
        assertTrue(Math.abs(burrowed / (double) oldHumps - 0.4) < 0.04, "burrowed share " + burrowed / (double) oldHumps);
    }

    private static int band(int stage) {
        return stage <= 9 ? 0 : stage <= 14 ? 1 : stage <= 19 ? 2 : stage <= 24 ? 3 : 4;
    }

    @Test
    void aColonysAgeAndWorkersFollowItsMound() {
        RandomSource random = RandomSource.create(7L);
        double young = TermiteMoundStructure.ageFor(4, random), old = TermiteMoundStructure.ageFor(32, random);
        assertTrue(young > 8 && young < 25, "age at stage 4: " + young);
        assertTrue(old > 200 && old < 320, "age at stage 32: " + old);
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
