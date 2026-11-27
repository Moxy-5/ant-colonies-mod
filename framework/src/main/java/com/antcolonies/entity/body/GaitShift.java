package com.antcolonies.entity.body;

import javax.annotation.Nullable;

import software.bernie.geckolib.animation.AnimationController;

/**
 * A gait changes on the matching footfall while the old gait can carry the speed, and at once when it cannot (the Motion
 * Studio, phase 2, step 3, 2026-11-25; its rule rethought for BUGS 384; the first phase 2, its record in {@code docs/HISTORY.md}).
 * GeckoLib blends from wherever the old clip is into the new clip's first frame, so a walk left at any moment swung its
 * legs through each other into the trot (up to 140 degrees apart in 4 ticks). A real animal changes gait at a set point
 * of its stride: here a change waits until the old clip comes to the moment whose pose is nearest the new clip's first
 * frame (its plan's {@code gaits.<old>.change.<new>}, found by {@code tools/clip_check.py --gait-changes}), at most one
 * loop on, the old clip meanwhile playing at the speed its stride needs, within its limits.
 * <p>
 * It waits only while the old gait's feet stay right at the animal's speed: played within its limits, its feet off the
 * ground's speed by no more than a fifth either way ({@link #FEET_RIGHT}, the pace report's own line,
 * {@code tools/pace_report.py}'s SLIDE). A gentle speed-up or slow-down across a gait's start crosses a band where the old
 * gait still carries the speed, and changes on the footfall there; a start from standing (a run reached in three ticks),
 * a burst of speed, or a turn on the spot after a run is past what the old gait can carry, and changes at once, a few
 * ticks' blend, as a real animal launches straight into its run. (The first rule waited always: at a start a buffalo
 * walked with its feet sliding four times over while it ran, BUGS 384.) A change from any other clip (standing, a doing)
 * is at once too.
 * <p>
 * The clip's time is followed as played, both ways and across its loop's restarts (a frozen film's stepped tick draws a
 * frame a tick back, BUGS 380: a small step back is not a new loop), and the change comes when it reaches the first time
 * the moment comes round after the change was wanted.
 * <p>
 * Drawing only (one per animal, asked from its clip player's handler each frame); species-neutral.
 */
public final class GaitShift {
    /** How far the feet may be off the ground's speed, either way, and still look planted: a fifth (the pace report's). */
    static final double FEET_RIGHT = 0.2;

    /** The gait drawn now, or null before the first. */
    @Nullable
    private Gaits.Gait shown;
    /** The gait a change is waited for (null: none), the drawn clip's time last asked (clip ticks; NaN: not followed). */
    @Nullable
    private Gaits.Gait waitingFor;
    private double last = Double.NaN;
    /** The drawn clip's time played since followed, its loops unrolled, and the time the change comes at. */
    private double played, changeAt;

    /**
     * The gait to draw: the wanted one, or the drawn one until its clip comes to its moment for the change while it can
     * carry {@code speed} (the speed the clip is played for: blocks a tick at size 1, {@link Gaits#perSize}).
     */
    public Gaits.Gait shown(Gaits.Gait wanted, double speed, AnimationController<?> player) {
        if (shown == null || wanted == shown || !(player instanceof SteadyController<?> steady)) {
            return show(wanted);
        }
        var playing = player.getCurrentAnimation();
        boolean onShown = playing != null && playing.animation().name().equals(shown.clip());
        if (!onShown || player.getAnimationState() == AnimationController.State.STOPPED || !carries(shown, speed)) {
            return show(wanted);                    // from another clip, or past what the drawn gait can carry: at once
        }
        if (player.getAnimationState() == AnimationController.State.TRANSITIONING) {
            last = Double.NaN;                      // still easing into the drawn gait: its stride has not begun
            waitingFor = null;
            return shown;
        }
        Double at = shown.changeAt().get(wanted.clip());
        double length = playing.animation().length();
        if (at == null || length <= 0) {
            return show(wanted);
        }
        double now = steady.clipTicks();
        if (Double.isNaN(last)) {
            played = now;                           // followed from here
        } else {
            double moved = now - last;
            if (moved < -length / 2) {
                moved += length;                    // the loop began again (a step back over its start is a small one)
            }
            played += moved;
        }
        last = now;
        if (waitingFor != wanted) {                 // the change wanted from here: the moment's next coming round
            waitingFor = wanted;
            changeAt = played + positive(at - now, length);
        }
        return played >= changeAt ? show(wanted) : shown;
    }

    /** Whether the gait's feet stay right at this speed: its play speed, within its limits, off the stride by a fifth at most. */
    public static boolean carries(Gaits.Gait gait, double speed) {
        double needed = speed / gait.madeFor();
        double drawn = gait.rate(speed);
        return needed >= drawn * (1.0 - FEET_RIGHT) && needed <= drawn * (1.0 + FEET_RIGHT);
    }

    /** a modulo b, from 0 up to b. */
    private static double positive(double a, double b) {
        double m = a % b;
        return m < 0 ? m + b : m;
    }

    private Gaits.Gait show(Gaits.Gait gait) {
        shown = gait;
        waitingFor = null;
        last = Double.NaN;
        return gait;
    }
}
