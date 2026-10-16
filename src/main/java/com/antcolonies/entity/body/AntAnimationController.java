package com.antcolonies.entity.body;

import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;

import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.keyframe.event.data.CustomInstructionKeyframeData;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * One of an insect's animation players (a GeckoLib controller; the animation engine, step 1, 2026-10-16). Each insect
 * has three: the body and legs, the head and antennae, and the little extras (see {@code AbstractAntEntity}). Three
 * changes from GeckoLib's own player:
 * <ul>
 * <li><b>Its own clock.</b> The clip's time runs on at a rate that may change every frame without the pose jumping
 * (GeckoLib's player multiplies the whole time since the clip began by the speed, so a new speed jumps to another moment
 * of the clip), and a looping clip wraps round smoothly (GeckoLib's drops the bit past the end: a small stutter each
 * loop).</li>
 * <li><b>Legs matched to the ground.</b> A clip with a stride note (a timeline line {@code stride <model units>}, written
 * by {@code tools/ant_models.py}) plays one cycle for each stride the insect walks, so a foot on the ground never
 * slides, at any speed.</li>
 * <li><b>Its own bones.</b> It moves only the bones it owns, so one clip can walk the legs while another moves the head
 * (when two players move the same bone the later one wins, so each bone belongs to one player; the extras' player owns
 * them all and only moves the bones of the clip it plays).</li>
 * </ul>
 */
public class AntAnimationController<T extends AbstractAntEntity> extends AnimationController<T> {
    /** The head player's bones: the head, the mandibles, the antennae and the carried load's place in the jaws. */
    public static final Predicate<String> HEAD_BONES = bone -> bone.startsWith("head") || bone.startsWith("mandible")
            || bone.startsWith("antenna") || bone.startsWith("carry");
    /** The body player's bones: everything that is not the head's (the body, the forebody, the legs, the waist, the gaster). */
    public static final Predicate<String> BODY_BONES = HEAD_BONES.negate();
    /** The extras' player: every bone (it only moves the bones of the clip it plays). */
    public static final Predicate<String> ALL_BONES = bone -> true;
    /** No clip plays faster than this many times as made (a safety net: a body thrown far in one tick). */
    private static final double MOST_RATE = 20.0;

    private final Predicate<String> owns;
    /** Where in the clip it is (ticks of the clip as made). */
    private double clock;
    /** The game's animation time at the last frame (NaN before the first). */
    private double lastTick = Double.NaN;
    /** Blocks a tick the clip's stride keeps pace with this frame, or NaN to play at {@link #fixedRate}. */
    private double matchSpeed = Double.NaN;
    /** How fast the clip plays when it is not matched to a stride (1 = as made). */
    private double fixedRate = 1.0;
    /** The clip whose stride is known (read once per clip), and its stride in model units (NaN: it has none). */
    @Nullable
    private Animation strideClip;
    private double stride = Double.NaN;

    public AntAnimationController(T insect, String name, int transitionTicks, AnimationStateHandler<T> handler, Predicate<String> owns) {
        super(insect, name, transitionTicks, handler);
        this.owns = owns;
        // The stride notes are read from the clip (strideOf), not played as events: nothing to do when one comes round.
        setCustomInstructionKeyframeHandler(event -> {
        });
    }

    /**
     * This frame: keep the clip's stride in step with the insect moving this fast (blocks a tick), so the feet stay put
     * on the ground; a clip without a stride note plays at {@code rateWithoutStride} instead.
     */
    public void keepPace(double blocksPerTick, double rateWithoutStride) {
        matchSpeed = blocksPerTick;
        fixedRate = rateWithoutStride;
    }

    /** This frame: play the clip at this rate (1 = as made), not tied to walking. */
    public void playAt(double rate) {
        matchSpeed = Double.NaN;
        fixedRate = rate;
    }

    /**
     * How far the insect walks in one play of a clip, in model units (16 to a block before the renderer's scale), from
     * its stride note; NaN if it has none (a clip not made by the motion library, such as today's termite walks).
     */
    public static double strideOf(Animation clip) {
        for (CustomInstructionKeyframeData note : clip.keyFrames().customInstructions()) {
            String text = note.getInstructions().trim();
            if (text.startsWith("stride ")) {
                try {
                    return Double.parseDouble(text.substring("stride ".length()).trim());
                } catch (NumberFormatException e) {
                    return Double.NaN;
                }
            }
        }
        return Double.NaN;
    }

    /** How fast the clip plays now (1 = as made): one cycle a stride walked, or the rate it was given. */
    public double rate() {
        Animation clip = currentAnimation == null ? null : currentAnimation.animation();
        if (clip != strideClip) {
            strideClip = clip;
            stride = clip == null ? Double.NaN : strideOf(clip);
        }
        if (clip != null && !Double.isNaN(matchSpeed) && stride > 0.0) {
            double strideBlocks = stride / 16.0 * animatable.getDrawScale();
            return Math.min(MOST_RATE, Math.max(0.0, matchSpeed) * clip.length() / strideBlocks);
        }
        return fixedRate;
    }

    @Override
    protected double adjustTick(double tick) {
        if (shouldResetTick) {
            if (getAnimationState() != State.STOPPED) {
                tickOffset = tick;
            }
            shouldResetTick = false;
            clock = 0.0;
            lastTick = tick;
            return 0.0;
        }
        if (Double.isNaN(lastTick) || tick < lastTick) {
            lastTick = tick; // the first frame, or the game's time went back (a world loaded again): no jump
        }
        double passed = tick - lastTick;
        if (passed > 0.0) {
            // A change between clips takes the same time at any speed; a clip itself runs at its rate.
            clock += passed * (animationState == State.TRANSITIONING ? 1.0 : rate());
            lastTick = tick;
        }
        if (animationState == State.RUNNING && currentAnimation != null && loops() && currentAnimation.animation().length() > 0.0) {
            clock %= currentAnimation.animation().length();
        }
        return clock;
    }

    /** Whether the clip playing loops (asked for as a loop, or a loop in its file). */
    private boolean loops() {
        Animation.LoopType type = currentAnimation.loopType();
        if (type == Animation.LoopType.DEFAULT) {
            type = currentAnimation.animation().loopType();
        }
        return type == Animation.LoopType.LOOP;
    }

    @Override
    public void process(GeoModel<T> model, AnimationState<T> state, Map<String, GeoBone> bones, Map<String, BoneSnapshot> snapshots,
            double seekTime, boolean crashWhenCantFindBone) {
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        // Let go of the bones this player does not own (GeckoLib makes a fresh set for every bone each frame).
        boneAnimationQueues.keySet().removeIf(owns.negate());
    }
}
