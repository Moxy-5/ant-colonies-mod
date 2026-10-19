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

    /** Step 5: each kind's lifespans in the user's ranges (umbrella 150-250, camel 300-500, baobab 1,000-2,000), spread. */
    @Test
    void lifespansByKind() {
        int[][] ranges = {{150, 250}, {300, 500}, {1000, 2000}};
        String[] kinds = {"umbrella_thorn", "camel_thorn", "baobab"};
        for (int i = 0; i < kinds.length; i++) {
            TreeKind kind = TreeKind.load(kinds[i]);
            double lo = Double.MAX_VALUE, hi = 0;
            for (long seed = 1; seed <= 2000; seed++) {
                double life = TreeLife.lifespan(kind, seed);
                lo = Math.min(lo, life);
                hi = Math.max(hi, life);
            }
            String k = kinds[i];
            double l = lo, h = hi;
            int[] r = ranges[i];
            assertTrue(lo >= r[0] && hi <= r[1], () -> k + ": " + l + " to " + h);
            assertTrue(hi - lo > (r[1] - r[0]) * 0.9, () -> k + " not spread: " + l + " to " + h);
        }
    }

    /**
     * Step 5: the dead stages' days (the user's "real, squeezed"): acacias wither the day they die, snag from the next
     * day 2-4 days, mud 3-6, shed 5-10, fallen 11-21 days after death and for good; baobabs slump the next day for 1-2
     * days, heap 3-6, dried heap 10-20, then gone. And a new-land tree in a stage is given a death day inside it.
     */
    @Test
    void deadStagesTakeTheDecidedDays() {
        TreeKind umbrella = TreeKind.load("umbrella_thorn"), baobab = TreeKind.load("baobab");
        for (long seed = 1; seed <= 500; seed++) {
            java.util.List<TreeLife.DeadStage> a = TreeLife.deadStages(umbrella, seed);
            double snag = a.get(2).from() - a.get(1).from(), mud = a.get(3).from() - a.get(2).from(), shed = a.get(4).from() - a.get(3).from();
            long s = seed;
            assertTrue(a.get(1).from() == 1 && snag >= 2 && snag <= 4 && mud >= 3 && mud <= 6 && shed >= 5 && shed <= 10,
                    () -> "seed " + s + ": " + a);
            assertTrue(a.get(4).from() >= 11 && a.get(4).from() <= 21 && a.get(4).stage() == TreeStage.FALLEN, () -> "seed " + s + ": " + a);
            assertTrue(TreeLife.deadStage(umbrella, seed, 0) == TreeStage.LIVING && TreeLife.deadStage(umbrella, seed, 1) == TreeStage.SNAG
                    && TreeLife.deadStage(umbrella, seed, 500) == TreeStage.FALLEN, () -> "seed " + s);
            java.util.List<TreeLife.DeadStage> b = TreeLife.deadStages(baobab, seed);
            double slump = b.get(2).from() - b.get(1).from(), heap = b.get(3).from() - b.get(2).from(), dry = b.get(4).from() - b.get(3).from();
            assertTrue(b.get(1).from() == 1 && slump >= 1 && slump <= 2 && heap >= 3 && heap <= 6 && dry >= 10 && dry <= 20 && b.get(4).stage() == null,
                    () -> "seed " + s + ": " + b);
            assertTrue(TreeLife.deadStage(baobab, seed, 60) == null, () -> "seed " + s + ": a baobab heap still there after 60 days");
            for (TreeStage stage : new TreeStage[] {TreeStage.SNAG, TreeStage.MUD, TreeStage.SHED, TreeStage.FALLEN}) {
                for (double roll : new double[] {0, 0.5, 0.999}) {
                    long d = TreeLife.daysDeadIn(umbrella, seed, stage, roll);
                    assertTrue(TreeLife.deadStage(umbrella, seed, d) == stage, () -> "seed " + s + " " + stage + " roll " + roll + ": " + d);
                }
            }
            for (TreeStage stage : new TreeStage[] {TreeStage.SLUMP, TreeStage.HEAP, TreeStage.HEAP_DRY}) {
                long d = TreeLife.daysDeadIn(baobab, seed, stage, 0.7);
                assertTrue(TreeLife.deadStage(baobab, seed, d) == stage, () -> "seed " + s + " " + stage + ": " + d);
            }
        }
    }
}
