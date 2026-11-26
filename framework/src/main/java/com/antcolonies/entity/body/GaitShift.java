package com.antcolonies.entity.body;

import javax.annotation.Nullable;

import software.bernie.geckolib.animation.AnimationController;

/**
 * A gait changes on the matching footfall (the Motion Studio, phase 2, step 3, 2026-11-25;
 * {@code docs/plans/motion-studio-phase2.md}). GeckoLib blends from wherever the old clip is into the new clip's first
 * frame, so a walk left at any moment swung its legs through each other into the trot (up to 140 degrees apart in 4
 * ticks). A real animal changes gait at a set point of its stride: here each gait change waits until the old clip comes
 * to the moment whose pose is nearest the new clip's first frame (its plan's {@code gaits.<old>.change.<new>}, found by
 * {@code tools/clip_check.py --gait-changes}), at most one loop on; meanwhile the old clip plays at the speed its stride
 * needs, within its limits. A gait change from any other clip (standing, a doing) is at once.
 * <p>
 * The clip's time is followed as played, both ways and across its loop's restarts (a frozen film's stepped tick draws a
 * frame a tick back, BUGS 380: a step back is not a new loop), and the change comes when it reaches the first time the
 * moment comes round after the change was wanted.
 * <p>
 * Drawing only (one per animal, asked from its clip player's handler each frame); species-neutral.
 */
public final class GaitShift {
    /** The gait drawn now, or null before the first. */
    @Nullable
    private Gaits.Gait shown;
    /** The gait a change is waited for (null: none), the drawn clip's time last asked (clip ticks; NaN: not followed). */
    @Nullable
    private Gaits.Gait waitingFor;
    private double last = Double.NaN;
    /** The drawn clip's time played since followed, its loops unrolled, and the time the change comes at. */
    private double played, changeAt;

    /** The gait to draw: the wanted one, or the drawn one until its clip comes to its moment for the change. */
    public Gaits.Gait shown(Gaits.Gait wanted, AnimationController<?> player) {
        if (shown == null || wanted == shown || !(player instanceof SteadyController<?> steady)) {
            return show(wanted);
        }
        var playing = player.getCurrentAnimation();
        boolean onShown = playing != null && playing.animation().name().equals(shown.clip());
        if (!onShown || player.getAnimationState() == AnimationController.State.STOPPED) {
            return show(wanted);                    // from another clip: at once
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
                moved += length;                    // the loop began again
            } else if (moved > length / 2) {
                moved -= length;                    // stepped back over its start
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
