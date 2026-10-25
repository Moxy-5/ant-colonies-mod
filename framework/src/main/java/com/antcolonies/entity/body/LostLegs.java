package com.antcolonies.entity.body;

import java.util.ArrayList;
import java.util.List;

/**
 * Legs bitten off in a fight (M8 step 2d, {@code docs/DESIGN.md} "Step 2d, lost legs"): which of an insect's six legs
 * are gone, as six bits in the gait maker's order (l1 r1 l2 r2 l3 r3: left and right, pair 1 the front legs), what
 * it walks with, and how much it is slowed.
 *
 * <p>The real animal (Frank et al. 2017, "Saving the injured"): a Matabele that has just lost a leg runs about 30
 * percent slower; within about a day it has learned to walk on the legs left and runs nearly as fast as a healthy one,
 * and it raids again. So the slowing starts at {@link #FRESH_SLOWING} and fades over {@link #RECOVERY_TICKS} to a small
 * lasting cost per leg ({@link #SETTLED_SLOWING_PER_LEG}, my pick). With {@link #CRIPPLED} or more gone it fights no
 * more and limps home, slowed by {@link #CRIPPLED_SLOWING} for good (step 5's rescue carries it, later).
 *
 * <p>The leg goes from the knee down: the model hides the leg's {@link #lowerBone lower bone} (the shin and the foot),
 * the thigh left as a stump. The walk is the model's {@link #walkClip walk_lost_...} clip, one for every way of losing
 * 1 or 2 legs (tools/matabele_anims.py); with more gone, the one of the first two lost.
 */
public final class LostLegs {
    /** The six legs, in the order of their bits. */
    public static final List<String> LEGS = List.of("l1", "r1", "l2", "r2", "l3", "r3");
    /** How many legs gone before it stops fighting and limps home. */
    public static final int CRIPPLED = 3;
    /** How much slower it walks just after losing a leg (real: about 30 percent). */
    public static final double FRESH_SLOWING = 0.30;
    /** How much slower it walks for each leg gone once it has learned to walk without them (my pick: nearly as fast). */
    public static final double SETTLED_SLOWING_PER_LEG = 0.04;
    /** How long it takes to learn to walk without the leg (real: about a day). */
    public static final int RECOVERY_TICKS = 24000;
    /** How much slower one with {@link #CRIPPLED} or more legs gone walks (my pick). */
    public static final double CRIPPLED_SLOWING = 0.5;

    private LostLegs() {
    }

    /** How many legs are gone. */
    public static int count(int lost) {
        return Integer.bitCount(lost & 0x3F);
    }

    /** Whether this leg (its index in {@link #LEGS}) is gone. */
    public static boolean isGone(int lost, int leg) {
        return (lost & (1 << leg)) != 0;
    }

    /** The legs gone, in order. */
    public static List<String> gone(int lost) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < LEGS.size(); i++) {
            if (isGone(lost, i)) {
                out.add(LEGS.get(i));
            }
        }
        return out;
    }

    /** The role name (body plan) of the part that goes when this leg is bitten off: the shin, the foot with it. */
    public static String lowerBone(int leg) {
        return "leg_" + LEGS.get(leg) + "_lower";
    }

    /** The walk for these legs gone ({@code walk_lost_l2}, {@code walk_lost_l1_r3}), or null with none gone. */
    public static String walkClip(int lost) {
        List<String> gone = gone(lost);
        if (gone.isEmpty()) {
            return null;
        }
        return "walk_lost_" + String.join("_", gone.subList(0, Math.min(2, gone.size())));
    }

    /**
     * How much slower than a healthy insect it walks (0 to 1) with these legs gone, {@code sinceLast} ticks after the
     * last was lost: fresh, {@link #FRESH_SLOWING}, fading over {@link #RECOVERY_TICKS} to the settled cost.
     */
    public static double slowing(int lost, long sinceLast) {
        int n = count(lost);
        if (n == 0) {
            return 0.0;
        }
        if (n >= CRIPPLED) {
            return CRIPPLED_SLOWING;
        }
        double settled = SETTLED_SLOWING_PER_LEG * n;
        double share = Math.min(1.0, Math.max(0.0, sinceLast / (double) RECOVERY_TICKS));
        return FRESH_SLOWING + (settled - FRESH_SLOWING) * share;
    }
}
