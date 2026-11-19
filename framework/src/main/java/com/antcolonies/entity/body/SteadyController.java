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
 * Species-neutral: any animal's clip player, made as GeckoLib's ({@code new SteadyController<>(this, "body", 4, this::animate)}).
 */
public class SteadyController<T extends GeoAnimatable> extends AnimationController<T> {
    /** How far into the clip it is (ticks of the clip), and the animal's tick it was worked out at. */
    private double played, at = Double.NaN;

    public SteadyController(T animatable, String name, int transitionTicks, AnimationStateHandler<T> handler) {
        super(animatable, name, transitionTicks, handler);
    }

    @Override
    protected double adjustTick(double tick) {
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
