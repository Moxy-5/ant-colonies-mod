package com.antcolonies.entity.body;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;

/**
 * The body only: a clip player whose play speed changes how fast its clip plays from now on, never where in the clip it
 * is (BUGS 232, 2026-11-19). GeckoLib's own player works out how far into a clip an animal is as "speed x time since the
 * clip started", so each change of {@link #setAnimationSpeed} rescales all the time already played and the clip jumps to
 * another moment; an animal whose walk follows its own speed (which wobbles every tick a position comes from the server
 * late or early) stuttered. This one moves the clip on by speed x time passed, frame by frame.
 * <p>
 * A clip may also be played on a clock of the animal's own ({@link #clock}): a move whose place in the world the game
 * works out from its start (the Nile monitor's climb, the Animal Maker Engine step 2) is drawn at the same moment of its
 * clip as its place, whenever the clip started playing on this player's screen.
 * <p>
 * Species-neutral: any animal's clip player, made as GeckoLib's ({@code new SteadyController<>(this, "body", 4, this::animate)}).
 */
public class SteadyController<T extends GeoAnimatable> extends AnimationController<T> {
    /** A clip's own clock: how far into this clip the animal is (ticks of the clip), or below 0 to let it play on its own. */
    @FunctionalInterface
    public interface Clock<T> {
        double ticks(T animatable, String clip, double tick);
    }

    /** How far into the clip it is (ticks of the clip), and the animal's tick it was worked out at. */
    private double played, at = Double.NaN;
    private Clock<T> clock;

    public SteadyController(T animatable, String name, int transitionTicks, AnimationStateHandler<T> handler) {
        super(animatable, name, transitionTicks, handler);
    }

    /** Plays the clips this clock answers for at its time instead of their own. */
    public SteadyController<T> clock(Clock<T> clock) {
        this.clock = clock;
        return this;
    }

    /** How far into its clip it is now (ticks of the clip; while it eases into a clip, the ticks of easing). */
    public double clipTicks() {
        return played;
    }

    @Override
    protected double adjustTick(double tick) {
        // (a clip held on its last frame is "paused" between frames: still on its clock)
        if (clock != null && (animationState == State.RUNNING || animationState == State.PAUSED) && currentAnimation != null) {
            double fixed = clock.ticks(animatable, currentAnimation.animation().name(), tick);
            if (fixed >= 0) {                   // played on its own clock (GeckoLib's start bookkeeping done here)
                shouldResetTick = false;
                tickOffset = tick;
                played = fixed;
                at = tick;
                return fixed;
            }
        }
        if (shouldResetTick) {                  // a clip (or its turn) starts: GeckoLib's own start, at nothing played
            played = 0;
            at = tick;
            return super.adjustTick(tick);
        }
        if (Double.isNaN(at) || tick < at) {    // first asked, or the clock went back (a reload): carry on from here
            at = tick;
        }
        played += animationSpeedModifier.apply(animatable) * (tick - at);
        at = tick;
        return played;
    }
}
