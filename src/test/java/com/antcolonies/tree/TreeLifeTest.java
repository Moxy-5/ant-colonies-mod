package com.antcolonies.tree;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The giant trees' life in numbers (M7.5 step 4, {@link TreeLife}): the speeds the user decided (seedling to young in a
 * few days, grown in 20 to 40, ancient after 100+) and the deaths (about 9 in 10 seedlings never reach young, about
 * half the young never reach grown), over many trees with their own seeds and dice.
 */
class TreeLifeTest {
    @Test
    void speedsAreTheDecidedOnes() {
        for (long seed = 1; seed <= 2000; seed++) {
            double speed = TreeLife.speed(seed);
            int day = 0, young = -1, grown = -1, ancient = -1;
            for (double age = 0; age < TreeShapes.ANCIENT && day < 1000; day++) {
                age = TreeLife.grow(age, speed);
                if (young < 0 && age >= TreeLife.YOUNG) {
                    young = day + 1;
                }
                if (grown < 0 && age >= TreeShapes.GROWN) {
                    grown = day + 1;
                }
                if (ancient < 0 && age >= TreeShapes.ANCIENT) {
                    ancient = day + 1;
                }
            }
            long s = seed;
            int y = young, g = grown, a = ancient;
            assertTrue(y >= 2 && y <= 5, () -> "seed " + s + ": young after " + y + " days");
            assertTrue(g >= 20 && g <= 40, () -> "seed " + s + ": grown after " + g + " days");
            assertTrue(a >= 100, () -> "seed " + s + ": ancient after " + a + " days");
        }
    }

    @Test
    void mostDieYoung() {
        int seedlings = 20000, reachedYoung = 0, reachedGrown = 0;
        Random seeds = new Random(42);
        for (int i = 0; i < seedlings; i++) {
            long seed = seeds.nextLong() & 0xFFFFFFFFL;
            double speed = TreeLife.speed(seed), age = 0;
            boolean young = false;
            for (long day = 1; age < TreeShapes.GROWN; day++) {
                double next = TreeLife.grow(age, speed);
                if (TreeLife.dice(seed, day).nextDouble() < TreeLife.deathChance(age, next)) {
                    break;
                }
                age = next;
                if (!young && age >= TreeLife.YOUNG) {
                    young = true;
                    reachedYoung++;
                }
            }
            if (age >= TreeShapes.GROWN) {
                reachedGrown++;
            }
        }
        double toYoung = reachedYoung / (double) seedlings, youngToGrown = reachedGrown / (double) reachedYoung;
        assertTrue(toYoung > 0.07 && toYoung < 0.13, "seedlings reaching young: " + toYoung);
        assertTrue(youngToGrown > 0.4 && youngToGrown < 0.6, "young reaching grown: " + youngToGrown);
        // High at first, falling: a seedling's day is far riskier than a half-grown tree's.
        assertTrue(TreeLife.deathChance(0, 0.02) > 10 * TreeLife.deathChance(0.5, 0.535), "the risk does not fall with age");
        assertTrue(TreeLife.deathChance(1, 1.01) == 0, "grown trees die here");
    }
}
