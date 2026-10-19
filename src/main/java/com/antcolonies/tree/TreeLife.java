package com.antcolonies.tree;

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
    /** Of the seedlings, how many live to be young (my pick: 1 in 10). */
    static final double SEEDLING_SURVIVAL = 0.1;
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
    static double daysAt(double age) {
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

    /** The shape's age for a tree's age: to the hundredth, so the shapes are worked out a few times, not every day. */
    public static double shapeAge(double age) {
        return Math.round(age * 100) / 100.0;
    }

    /** One day's own dice for a tree: the same every time for the same tree and day (tests come out the same). */
    public static Random dice(long seed, long day) {
        return new Random(seed * 0x9E3779B97F4A7C15L + day * 0xC2B2AE3D27D4EB4FL + 12345);
    }
}
