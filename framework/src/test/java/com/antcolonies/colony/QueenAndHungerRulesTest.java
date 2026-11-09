package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.antcolonies.species.SpeciesProfile;

/**
 * Test revamp R5: quick rule tests with no Minecraft for feeding the queen, worker hunger and the job share-out (each
 * rule a plain method the game code calls, so these tests check the game's own rule).
 */
class QueenAndHungerRulesTest {
    /** A queen needing 20 pieces for an egg, 5 rich pieces for a burst of 5 eggs (the harvester's numbers). */
    private static final SpeciesProfile.QueenNumbers QUEEN = new SpeciesProfile.QueenNumbers(20, 5, 5, 1200, 3600, 120000);

    @Test
    void aFullPlainMealStartsAnEggAndNotBefore() {
        int bread = 0;
        for (int piece = 1; piece < 20; piece++) {
            QueenCare.Meal meal = QueenCare.afterPiece(bread, 0, false, QUEEN);
            assertFalse(meal.eggStarts(), "an egg after only " + piece + " pieces");
            assertFalse(meal.burstStarts());
            bread = meal.bread();
            assertEquals(piece, bread);
        }
        QueenCare.Meal last = QueenCare.afterPiece(bread, 0, false, QUEEN);
        assertTrue(last.eggStarts(), "the 20th piece starts an egg");
        assertEquals(20, last.bread());
    }

    @Test
    void theQueenTakesNoBreadWhileCarryingAnEggOrOnceFull() {
        assertTrue(QueenCare.takesPiece(false, 0, QUEEN));
        assertTrue(QueenCare.takesPiece(false, 19, QUEEN));
        assertFalse(QueenCare.takesPiece(false, 20, QUEEN), "a full meal takes no more");
        assertFalse(QueenCare.takesPiece(true, 0, QUEEN), "a queen carrying an egg takes no more");
    }

    @Test
    void aFullRichMealStartsABurstAndBothMealsStartOver() {
        QueenCare.Meal meal = new QueenCare.Meal(7, 0, false, false);
        for (int piece = 1; piece < 5; piece++) {
            meal = QueenCare.afterPiece(meal.bread(), meal.rich(), true, QUEEN);
            assertFalse(meal.burstStarts(), "a burst after only " + piece + " rich pieces");
            assertEquals(7, meal.bread(), "rich pieces leave the plain meal alone");
        }
        meal = QueenCare.afterPiece(meal.bread(), meal.rich(), true, QUEEN);
        assertTrue(meal.burstStarts(), "the 5th rich piece starts a burst");
        assertFalse(meal.eggStarts());
        assertEquals(0, meal.bread());
        assertEquals(0, meal.rich());
    }

    @Test
    void richBreadMeansFewerPiecesPerEgg() {
        // Rich bread (M6.5): 5 rich pieces bring 5 eggs, a plain meal 20 pieces one: rich bread is 20 times the eggs.
        double plainPiecesPerEgg = QUEEN.meal();
        double richPiecesPerEgg = QUEEN.richMeal() / (double) QUEEN.burstEggs();
        assertTrue(richPiecesPerEgg < plainPiecesPerEgg);
    }

    @Test
    void hungerSlowsThenStarvesInOrder() {
        SpeciesProfile.WorkerNumbers worker = new SpeciesProfile.WorkerNumbers(24000, 36000, 72000, 72000, 192000);
        assertFalse(worker.hungryAfter(23999));
        assertTrue(worker.hungryAfter(24000), "hungry after a day without a meal");
        assertFalse(worker.slowedAfter(35999));
        assertTrue(worker.slowedAfter(36000));
        assertFalse(worker.starvedAfter(71999));
        assertTrue(worker.starvedAfter(72000), "starved after 3 days");
        for (long t = 0; t <= 100000; t += 500) {
            assertTrue(!worker.slowedAfter(t) || worker.hungryAfter(t), "slowed before hungry at " + t);
            assertTrue(!worker.starvedAfter(t) || worker.slowedAfter(t), "starved before slowed at " + t);
        }
    }

    @Test
    void theShareOutKeepsEveryWorkerAndFillsEveryJobItCan() {
        // Over many made-up colonies: nobody lost or made up, the wanted nurses always there (as far as the workers
        // go), at least a third of the rest building, and someone building and foraging once 2 or more are left.
        Random random = new Random(1);
        for (int run = 0; run < 2000; run++) {
            int[] natural = new int[ColonyRole.values().length];
            int total = 0;
            for (int i = 0; i < natural.length; i++) {
                natural[i] = random.nextInt(15);
                total += natural[i];
            }
            int nursesWanted = random.nextInt(Math.max(1, total / 3 + 1));
            int[] counts = Colony.shareOut(total, nursesWanted, natural);
            int sum = 0;
            for (int count : counts) {
                assertTrue(count >= 0);
                sum += count;
            }
            String what = "natural " + java.util.Arrays.toString(natural) + ", nurses wanted " + nursesWanted + ": "
                    + java.util.Arrays.toString(counts);
            assertEquals(total, sum, "workers lost or made up, " + what);
            int rest = total - Math.min(total, nursesWanted);
            assertTrue(counts[ColonyRole.NURSE.ordinal()] >= Math.min(total, nursesWanted), "too few nurses, " + what);
            assertTrue(counts[ColonyRole.BUILDER.ordinal()] >= (rest + 2) / 3 || counts[ColonyRole.BUILDER.ordinal()] == rest,
                    "too few builders (a third of the rest, builders first), " + what);
            if (rest >= 2) {
                assertTrue(counts[ColonyRole.BUILDER.ordinal()] >= 1 && counts[ColonyRole.FORAGER.ordinal()] >= 1,
                        "builders or foragers missing, " + what);
            }
        }
        assertArrayEquals(new int[] {0, 0, 0}, Colony.shareOut(0, 0, new int[3]));
    }
}
