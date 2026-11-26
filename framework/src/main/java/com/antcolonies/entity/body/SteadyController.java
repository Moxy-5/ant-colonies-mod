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
 * A blend into a new clip runs on real time, whatever the clip's play speed (BUGS 372, 2026-11-25): GeckoLib's blend
 * clock ran at the play speed, so a clip asked for at speed 0 (a stalking lion still between steps) kept the clock at
 * exactly 0 for two frames, and GeckoLib, which takes the next clip off its queue on any frame the blend clock reads 0,
 * took twice and was left with no clip at all (the lioness drawn standing, gliding, until her next freeze). A blend is
 * the controller's transition length in ticks at any speed, as the offline checker ({@code tools/clip_check.py}) has
 * always played it.
 * <p>
 * The clip follows the animal's clock both ways (BUGS 380, 2026-11-25): it went on from where the clock went back, so a
 * frozen game stepped for the camera (which draws a frame at the end of a tick, then the tick's frames from its start)
 * counted each tick twice, and every frame film since BUGS 232 showed clips at twice their speed. In play the clock only
 * goes forward. A clock gone back over a tick has been reset: the clip carries on from there.
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
        if (Double.isNaN(at) || tick < at - 1.0) {  // first asked, or the clock reset (over a tick back): carry on from here
            at = tick;
        }
        // the clip follows the clock both ways (BUGS 380): a frozen game stepped for the camera draws one frame at the end
        // of the new tick, then the tick's own frames from its start, a tick back; going back carried on from there and
        // counted the tick twice, every frame film showing clips at twice their speed. A blend is a length of time; the
        // play speed is the clip's (BUGS 372)
        double rate = animationState == State.TRANSITIONING ? 1.0 : animationSpeedModifier.apply(animatable);
        played += rate * (tick - at);
        at = tick;
        // (a moment before a loop's start, drawn by such a frame just after it began again, is drawn at its start; never
        // while easing into a clip, where a clip time of exactly 0 at another clock reading makes GeckoLib take again)
        return animationState == State.RUNNING ? Math.max(0.0, played) : played;
    }
}
