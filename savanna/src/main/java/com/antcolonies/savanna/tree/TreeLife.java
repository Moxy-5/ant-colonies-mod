package com.antcolonies.savanna.tree;

import java.util.List;
import java.util.Random;

/**
 * How fast a giant savanna tree grows and how likely a little one is to die, day by day (M7.5 step 4, the user's
 * decisions, docs/DESIGN.md "Growing in play"). Pure numbers, no world: {@link Forest} uses them in play and the checks
 * in {@code ./gradlew test} prove the totals.
 *
 * <p>Ages are the shape maker's ({@link TreeShapes}): below {@link #YOUNG} a seedling, then the young thorny tree up to
 * {@link #YOUNG_END}, grown at 1, ancient at 1.25. Speeds (my picks, to confirm, inside the ranges the user decided):
 * a seedling becomes a young tree in about 3 in-game days, a young tree is grown about 30 days after it came up (23 to
 * 40 by its seed), and ancient 100 to 170 days after it came up. Real umbrella thorns take 20 to 50 years to full size; the
 * game squeezes years into days, and a tree's own seed makes it a little faster or slower.
 *
 * <p>Deaths (real: nearly every acacia seedling is browsed, burned or dries out, which keeps the savanna open; my picks,
 * to confirm): about 9 in 10 seedlings never reach young, about half the young never reach grown. The daily chance is
 * high at first and falls as the tree grows (the risk in each bit of growth falls toward nothing at grown), so it does
 * not depend on how fast the tree grows. Grown trees do not die here (old age, fire and lightning are step 5).
 */
public final class TreeLife {
    /** Where the seedling ends and the young tree's look starts (the shape makers draw a seedling below it). */
    public static final double YOUNG = 0.06;
    /** The end of the young look: bone meal grows a tree no further. */
    public static final double YOUNG_END = 0.45;
    /** Days a seedling takes to become a young tree, at normal speed. */
    static final double SEEDLING_DAYS = 3;
    /** Days a young tree takes to be grown, at normal speed. */
    static final double YOUNG_DAYS = 27;
    /** Days a grown tree takes to be ancient, at normal speed. */
    static final double GROWN_DAYS = 100;
    /** Of the seedlings, how many live to be young (my pick: 1 in 5; was 1 in 10 before the dead wood for the raids). */
    static final double SEEDLING_SURVIVAL = 0.2;
    /** Of the young, how many live to be grown (my pick: about half). */
    static final double YOUNG_SURVIVAL = 0.5;

    private static final double SEEDLING_RISK = -Math.log(SEEDLING_SURVIVAL) / YOUNG;
    /** The young risk falls in a straight line to nothing at grown: risk(age) = c (1 - age), summed over the young look. */
    private static final double YOUNG_RISK = -Math.log(YOUNG_SURVIVAL) / ((1 - YOUNG) * (1 - YOUNG) / 2);

    private TreeLife() {
    }

    /** How fast this tree grows, by its seed: from 0.75 to 1.3 times the normal speed. */
    public static double speed(long seed) {
        return 0.75 + 0.55 * new TreeRandom(seed * 7919 + 101).next();
    }

    /** The age after one more day of growing (a day that crosses into the next stretch goes on at that stretch's pace). */
    public static double grow(double age, double speed) {
        return Math.min(TreeShapes.ANCIENT, ageAt(daysAt(age) + speed));
    }

    /** How many days at normal speed it takes to reach this age. */
    public static double daysAt(double age) {
        if (age < YOUNG) {
            return age / YOUNG * SEEDLING_DAYS;
        }
        if (age < TreeShapes.GROWN) {
            return SEEDLING_DAYS + (age - YOUNG) / (TreeShapes.GROWN - YOUNG) * YOUNG_DAYS;
        }
        return SEEDLING_DAYS + YOUNG_DAYS + (age - TreeShapes.GROWN) / (TreeShapes.ANCIENT - TreeShapes.GROWN) * GROWN_DAYS;
    }

    /** The age reached after so many days at normal speed. */
    static double ageAt(double days) {
        if (days < SEEDLING_DAYS) {
            return days / SEEDLING_DAYS * YOUNG;
        }
        if (days < SEEDLING_DAYS + YOUNG_DAYS) {
            return YOUNG + (days - SEEDLING_DAYS) / YOUNG_DAYS * (TreeShapes.GROWN - YOUNG);
        }
        return TreeShapes.GROWN + (days - SEEDLING_DAYS - YOUNG_DAYS) / GROWN_DAYS * (TreeShapes.ANCIENT - TreeShapes.GROWN);
    }

    /** The chance a tree dies while growing from age {@code from} to age {@code to} (one day). */
    public static double deathChance(double from, double to) {
        double risk = 0;
        // The seedling's part of the day.
        double a = Math.min(from, YOUNG), b = Math.min(to, YOUNG);
        risk += SEEDLING_RISK * (b - a);
        // The young part: the integral of c (1 - age).
        a = Math.max(Math.min(from, TreeShapes.GROWN), YOUNG);
        b = Math.max(Math.min(to, TreeShapes.GROWN), YOUNG);
        risk += YOUNG_RISK * ((b - a) - (b * b - a * a) / 2);
        return 1 - Math.exp(-risk);
    }

    // ---------------------------------------------------------------- step 5: old age and the dead stages

    /**
     * How many days this tree lives after it came up (the user's decision, step 5: real ratios, each tree its own
     * number from its kind's range by its seed; halved 2026-10-25 for the dead wood the raids need, heart rot to do the
     * rest: umbrella thorn 75 to 125, camel thorn 150 to 250, baobab 500 to 1,000).
     */
    public static double lifespan(TreeKind kind, long seed) {
        List<Integer> range = kind.lifespan();
        double from = range.get(0), to = range.get(range.size() - 1);
        return from + (to - from) * new TreeRandom(seed * 4513 + 29).next();
    }

    /**
     * The days a tree of new land has already lived when it is made at this age: the days its growth took, but never
     * past its lifespan. An old tree of new land whose growth alone took longer than its life (a near-ancient umbrella
     * thorn, since the lifespans were halved) has lived 60 to 95 percent of it instead, by its seed (my pick), so new
     * land's old trees die over the following days and weeks, not all at once the day they are first seen.
     */
    public static double livedAtStart(TreeKind kind, long seed, double age) {
        double grew = daysAt(Math.min(age, TreeShapes.ANCIENT));
        double life = lifespan(kind, seed);
        return grew < life ? grew : life * (0.6 + 0.35 * new TreeRandom(seed * 6271 + 13).next());
    }

    /**
     * The days after its death each dead stage starts, by the tree's own dice (the user's decisions, "real, squeezed";
     * my picks inside them, to confirm). The day it dies its leaves wither on the living shape (the first entry, LIVING).
     * Acacias: the bare snag from the next day for 2 to 4 days, termite mud climbing 3 to 6 days, the limbs breaking off
     * 5 to 10 days, then the fall (11 to 21 days after death), the fallen log for good. Baobabs: standing dead under a day,
     * slumping 1 to 2 days, a wet fibre heap 3 to 6, a dried heap shrinking 10 to 20, then gone (the last entry, null).
     */
    public static List<DeadStage> deadStages(TreeKind kind, long seed) {
        Random r = dice(seed, -5);
        if (kind.isBaobab()) {
            double slump = 1, heap = slump + 1 + r.nextDouble(), dry = heap + 3 + 3 * r.nextDouble(), gone = dry + 10 + 10 * r.nextDouble();
            return List.of(new DeadStage(TreeStage.LIVING, 0), new DeadStage(TreeStage.SLUMP, slump), new DeadStage(TreeStage.HEAP, heap),
                    new DeadStage(TreeStage.HEAP_DRY, dry), new DeadStage(null, gone));
        }
        double snag = 1, mud = snag + 2 + 2 * r.nextDouble(), shed = mud + 3 + 3 * r.nextDouble(), fall = shed + 5 + 5 * r.nextDouble();
        return List.of(new DeadStage(TreeStage.LIVING, 0), new DeadStage(TreeStage.SNAG, snag), new DeadStage(TreeStage.MUD, mud),
                new DeadStage(TreeStage.SHED, shed), new DeadStage(TreeStage.FALLEN, fall));
    }

    /** How many steps a dead tree's gradual changes go through (step 5b, my pick: 8 in a stage's time). */
    public static final int STEPS = 8;

    /**
     * How far along a dead tree is in the slow change of the stage it stands in, from 0 to {@link #STEPS} (step 5b, the
     * user's decisions): the day it dies the leaves turn brown and drop bit by bit, done within 9 tenths of the day;
     * the termites' mud climbs the snag over the first 6 tenths of its mud stage; the limbs break off one by one over the
     * first 8 tenths of the shedding stage; a baobab's dried heap shrinks over its whole stage. Other stages: 0.
     */
    public static int deadStep(TreeKind kind, long seed, double daysDead) {
        List<DeadStage> stages = deadStages(kind, seed);
        for (int i = stages.size() - 1; i >= 0; i--) {
            DeadStage s = stages.get(i);
            if (daysDead < s.from() || s.stage() == null) {
                continue;
            }
            double over = slowPart(s.stage());
            if (over == 0 || i + 1 >= stages.size()) {
                return 0;
            }
            double f = (daysDead - s.from()) / ((stages.get(i + 1).from() - s.from()) * over);
            return (int) Math.max(0, Math.min(STEPS, Math.floor(f * STEPS)));
        }
        return 0;
    }

    /** How much of a dead stage's time its slow change takes (0: none). */
    public static double slowPart(TreeStage stage) {
        return switch (stage) {
            case LIVING -> 0.9;
            case MUD -> 0.6;
            case SHED -> 0.8;
            case HEAP_DRY -> 1.0;
            default -> 0;
        };
    }

    /**
     * The step at which a stage's shape is the stage's own finished shape (the snag fully mud-climbed, every limb down);
     * 0 where the stage starts from its own shape (a living tree before it withers, a dried heap before it shrinks).
     */
    public static int fullStep(TreeStage stage) {
        return stage == TreeStage.MUD || stage == TreeStage.SHED ? STEPS : 0;
    }

    /**
     * How long a dead acacia due to fall may stand waiting for a storm (step 5c, the user's pick, "real topple": rotten
     * giants mostly come down in the wind and wet of a storm); after that it falls on a still day too.
     */
    public static final double RAIN_WAIT = 3;

    /** How old a grown tree is: 0 just grown, 1 ancient (an acacia over 0.5 is hollow inside). */
    public static double old(double age) {
        return Math.max(0, Math.min(1, (age - TreeShapes.GROWN) / (TreeShapes.ANCIENT - TreeShapes.GROWN)));
    }

    /**
     * Step 5d: the chance a fire on a grown tree's trunk kills it, rolled at most once a day (my picks): 1 in 10 just
     * grown, 6 in 10 ancient. Real: thick bark lets a sound acacia live through a grass fire with a scar at its foot; in an
     * old hollow trunk the fire gets inside and burns it out like a chimney.
     */
    public static double fireDeathChance(double age) {
        return 0.1 + 0.5 * old(age);
    }

    /** Whether the fire on a grown tree's trunk this day kills it (the tree's own dice for the day). */
    public static boolean fireKills(long seed, long day, double age) {
        return fireKills(seed, day, age, false);
    }

    /**
     * As {@link #fireKills(long, long, double)}; a tree hollowed by termites (step 5f) dies twice as surely (my pick, at
     * most 9 in 10): the fire gets into its hollow.
     */
    public static boolean fireKills(long seed, long day, double age, boolean termites) {
        double chance = termites ? Math.min(0.9, 2 * fireDeathChance(age)) : fireDeathChance(age);
        return dice(seed, day * 7919 + 5).nextDouble() < chance;
    }

    /**
     * Step 5e: what a lightning strike does to a big tree, by its dice for the day: the side its scar runs down (an
     * angle), whether it sets the trunk on fire (1 in 2) and whether it kills a living tree (1 in 3; my picks). Real: many
     * struck savanna trees live on with a long scar down the trunk where the bark burst off.
     */
    public record Strike(double side, boolean lit, boolean kills) {
    }

    public static Strike strike(long seed, long day) {
        return strike(seed, day, false);
    }

    /** As {@link #strike(long, long)}; a tree hollowed by termites (step 5f) is killed 2 in 3 (my pick). */
    public static Strike strike(long seed, long day, boolean termites) {
        Random r = dice(seed, day * 104729 + 11);
        return new Strike(r.nextDouble() * Math.PI * 2, r.nextInt(2) == 0, r.nextInt(3) < (termites ? 2 : 1));
    }

    /** Whether a grown tree killed by fire has a hollow trunk that smoulders (my pick: from half way to ancient). */
    public static boolean smoulders(double age) {
        return old(age) >= 0.5;
    }

    /** The day after death a dead stage starts (-1: the kind has no such stage). */
    public static double stageFrom(TreeKind kind, long seed, TreeStage stage) {
        for (DeadStage s : deadStages(kind, seed)) {
            if (s.stage() == stage) {
                return s.from();
            }
        }
        return -1;
    }

    /** A dead stage and the day after death it starts (stage null: nothing is left). */
    public record DeadStage(TreeStage stage, double from) {
    }

    /** The stage a tree is in so many days after its death (null: a baobab heap gone). */
    public static TreeStage deadStage(TreeKind kind, long seed, double daysDead) {
        TreeStage at = TreeStage.LIVING;
        for (DeadStage s : deadStages(kind, seed)) {
            if (daysDead >= s.from()) {
                at = s.stage();
            } else {
                break;
            }
        }
        return at;
    }

    /**
     * How many days ago a tree of new land that stands in this dead stage died: somewhere inside the stage after its
     * slow change is done (new land shows the stage's finished shape), by the world's dice (a fallen log: just fallen,
     * as nothing changes on it after).
     */
    public static long daysDeadIn(TreeKind kind, long seed, TreeStage stage, double roll) {
        List<DeadStage> stages = deadStages(kind, seed);
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).stage() == stage) {
                double start = stages.get(i).from();
                double end = i + 1 < stages.size() ? stages.get(i + 1).from() : start + 1;
                // Whole days only (the book counts days), every one of them inside the stage.
                double done = fullStep(stage) > 0 ? start + (end - start) * slowPart(stage) : start;
                long lo = (long) Math.ceil(done), hi = Math.max(lo, (long) Math.ceil(end) - 1);
                return lo + (long) Math.floor(roll * (hi - lo + 1));
            }
        }
        return 0;
    }

    /** Where a stage comes in its kind's life (living 0, then the dead stages in order). */
    public static int stageIndex(TreeKind kind, TreeStage stage) {
        TreeStage[] stages = TreeShapes.stages(kind);
        for (int i = 0; i < stages.length; i++) {
            if (stages[i] == stage) {
                return i;
            }
        }
        return 0;
    }

    /** The shape's age for a tree's age: to the hundredth, so the shapes are worked out a few times, not every day. */
    public static double shapeAge(double age) {
        return Math.round(age * 100) / 100.0;
    }

    /** One day's own dice for a tree: the same every time for the same tree and day (tests come out the same). */
    public static Random dice(long seed, long day) {
        return new Random(seed * 0x9E3779B97F4A7C15L + day * 0xC2B2AE3D27D4EB4FL + 12345);
    }
}
