package com.antcolonies.savanna.tree;

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
        assertTrue(toYoung > 0.16 && toYoung < 0.24, "seedlings reaching young: " + toYoung);
        assertTrue(youngToGrown > 0.4 && youngToGrown < 0.6, "young reaching grown: " + youngToGrown);
        // High at first, falling: a seedling's day is far riskier than a half-grown tree's.
        assertTrue(TreeLife.deathChance(0, 0.02) > 10 * TreeLife.deathChance(0.5, 0.535), "the risk does not fall with age");
        assertTrue(TreeLife.deathChance(1, 1.01) == 0, "grown trees die here");
    }

    /** Step 5: each kind's lifespans in the user's ranges (halved 2026-10-25: umbrella 75-125, camel 150-250, baobab 500-1,000), spread. */
    @Test
    void lifespansByKind() {
        int[][] ranges = {{75, 125}, {150, 250}, {500, 1000}};
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
        // New land's old trees never start past their lifespan (an ancient umbrella thorn grew 130 days, it lives 75-125),
        // and a young one has lived just the days its growth took.
        TreeKind umbrella = TreeKind.load("umbrella_thorn");
        for (long seed = 1; seed <= 2000; seed++) {
            double life = TreeLife.lifespan(umbrella, seed), start = TreeLife.livedAtStart(umbrella, seed, TreeShapes.ANCIENT);
            long s = seed;
            assertTrue(start >= life * 0.6 && start < life * 0.96, () -> "seed " + s + ": an ancient tree starts at " + start + " of " + life);
            assertTrue(TreeLife.livedAtStart(umbrella, seed, 0.5) == TreeLife.daysAt(0.5), () -> "seed " + s + ": a young tree's days");
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
    /**
     * Step 5b: the slow changes start at nothing when their stage starts, only go forward, and are done before the next
     * stage (the leaves all dropped before the snag, the mud up and the limbs down before the next stage, the heap
     * shrunk as it goes); other stages have none.
     */
    @Test
    void deadStepsGoForwardWithinTheirStage() {
        for (String name : new String[] {"umbrella_thorn", "baobab"}) {
            TreeKind kind = TreeKind.load(name);
            for (long seed = 1; seed <= 100; seed++) {
                java.util.List<TreeLife.DeadStage> stages = TreeLife.deadStages(kind, seed);
                for (int i = 0; i + 1 < stages.size(); i++) {
                    TreeStage stage = stages.get(i).stage();
                    double from = stages.get(i).from(), to = stages.get(i + 1).from();
                    boolean slow = stage == TreeStage.LIVING || stage == TreeStage.MUD || stage == TreeStage.SHED || stage == TreeStage.HEAP_DRY;
                    int last = -1;
                    for (int k = 0; k < 100; k++) {
                        double d = from + (to - from) * k / 100.0;
                        int step = TreeLife.deadStep(kind, seed, d);
                        long s = seed;
                        int before = last;
                        assertTrue(step >= last && step >= 0 && step <= TreeLife.STEPS, () -> name + " seed " + s + " " + stage + " at " + d + ": " + step + " after " + before);
                        assertTrue(slow || step == 0, () -> name + " " + stage + " has steps");
                        last = step;
                    }
                    assertTrue(TreeLife.deadStep(kind, seed, from) == 0, name + " " + stage + " does not start at 0");
                    if (slow && stage != TreeStage.HEAP_DRY) {
                        assertTrue(TreeLife.deadStep(kind, seed, to - 0.05) == TreeLife.STEPS, name + " seed " + seed + " " + stage + " not done before the next stage");
                    }
                }
            }
        }
    }

    /**
     * Step 5d to 5f, the odds by the tree's own dice: fire kills 1 in 10 just grown, 6 in 10 ancient, twice as many
     * hollowed by termites (at most 9 in 10); lightning sets a fire 1 time in 2 and kills 1 in 3, 2 in 3 with termites.
     */
    @Test
    void fireAndLightningOdds() {
        int n = 20_000;
        double grown = TreeShapes.GROWN, ancient = TreeShapes.ANCIENT;
        double[][] cases = {{grown, 0, 0.1}, {ancient, 0, 0.6}, {grown, 1, 0.2}, {ancient, 1, 0.9}};
        for (double[] c : cases) {
            int dead = 0;
            for (long seed = 1; seed <= n; seed++) {
                if (TreeLife.fireKills(seed, seed % 97, c[0], c[1] > 0)) {
                    dead++;
                }
            }
            double share = dead / (double) n;
            assertTrue(Math.abs(share - c[2]) < 0.02, "fire at age " + c[0] + " termites " + c[1] + ": " + share);
        }
        int lit = 0, killed = 0, killedHollow = 0;
        for (long seed = 1; seed <= n; seed++) {
            TreeLife.Strike s = TreeLife.strike(seed, seed % 89);
            lit += s.lit() ? 1 : 0;
            killed += s.kills() ? 1 : 0;
            killedHollow += TreeLife.strike(seed, seed % 89, true).kills() ? 1 : 0;
        }
        assertTrue(Math.abs(lit / (double) n - 0.5) < 0.02, "lightning fires " + lit);
        assertTrue(Math.abs(killed / (double) n - 1 / 3.0) < 0.02, "lightning kills " + killed);
        assertTrue(Math.abs(killedHollow / (double) n - 2 / 3.0) < 0.02, "lightning kills hollow trees " + killedHollow);
        assertTrue(!TreeLife.smoulders(grown) && TreeLife.smoulders(ancient), "only old trunks smoulder");
    }
}
