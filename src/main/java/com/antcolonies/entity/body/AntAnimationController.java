package com.antcolonies.entity.body;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;

import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.keyframe.event.data.CustomInstructionKeyframeData;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * One of an insect's animation players (a GeckoLib controller; the animation engine, step 1, 2026-10-16). Each insect
 * has three: the body and legs, the head and antennae, and the little extras (see {@code AbstractAntEntity}). Four
 * changes from GeckoLib's own player:
 * <ul>
 * <li><b>Its own clock.</b> The clip's time runs on at a rate that may change every frame without the pose jumping
 * (GeckoLib's player multiplies the whole time since the clip began by the speed, so a new speed jumps to another moment
 * of the clip), and a looping clip wraps round smoothly (GeckoLib's drops the bit past the end: a small stutter each
 * loop).</li>
 * <li><b>Legs matched to the ground.</b> A clip with a stride note (a timeline line {@code stride <model units>}, written
 * by {@code tools/ant_models.py}) plays one cycle for each stride the insect walks, so a foot on the ground never
 * slides, at any speed. A turning clip ({@code turn <degrees>}) plays one cycle for each that many degrees the body
 * turns.</li>
 * <li><b>Walks change in step</b> (2026-10-16). Clips with a swap note ({@code swap <0 to 1>}: the walks for each
 * speed, the turns) change from one to another at the moment of the cycle the note names (and half a cycle later), the
 * moment when they look alike, going on from the same moment of the new clip: the legs carry on stepping with no blend
 * and no jump.</li>
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
    /**
     * The longest a change between walks waits for its moment (ticks): an insect that has almost stopped would take too
     * long to reach it, so it blends over instead (my pick, to confirm).
     */
    private static final double MOST_WAIT = 5.0;
    /** A walk changes to the next speed's walk only when the speed is this much past its band (so it never flickers). */
    private static final double BAND_MARGIN = 1.1;

    private final Predicate<String> owns;
    /** Where in the clip it is (ticks of the clip as made). */
    private double clock;
    /** Where it was at the frame before. */
    private double clockBefore;
    /** The game's animation time at the last frame (NaN before the first). */
    private double lastTick = Double.NaN;
    /** Blocks a tick the clip's stride keeps pace with this frame, or NaN to play at {@link #fixedRate}. */
    private double matchSpeed = Double.NaN;
    /** Degrees a tick a turning clip keeps pace with this frame, or NaN. */
    private double matchTurn = Double.NaN;
    /** How fast the clip plays when it is not matched to a stride (1 = as made). */
    private double fixedRate = 1.0;
    /** The clip whose stride and turn are known (read once per clip), its stride in model units and its turn in degrees (NaN: none). */
    @Nullable
    private Animation pacedClip;
    private double stride = Double.NaN;
    private double turn = Double.NaN;
    /** The clip it is changing to at the next swap moment (see {@link #setAnimation}), its queue, and since when (animation time). */
    @Nullable
    private RawAnimation nextClip;
    @Nullable
    private Queue<AnimationProcessor.QueuedAnimation> nextQueue;
    private double waitingSince;

    public AntAnimationController(T insect, String name, int transitionTicks, AnimationStateHandler<T> handler, Predicate<String> owns) {
        super(insect, name, transitionTicks, handler);
        this.owns = owns;
        // The notes are read from the clip (noteOf), not played as events: nothing to do when one comes round.
        setCustomInstructionKeyframeHandler(event -> {
        });
    }

    /**
     * This frame: keep the clip's stride in step with the insect moving this fast (blocks a tick), so the feet stay put
     * on the ground; a clip without a stride note plays at {@code rateWithoutStride} instead.
     */
    public void keepPace(double blocksPerTick, double rateWithoutStride) {
        matchSpeed = blocksPerTick;
        matchTurn = Double.NaN;
        fixedRate = rateWithoutStride;
    }

    /** This frame: keep a turning clip's steps in step with the body turning this fast (degrees a tick, either way). */
    public void keepTurn(double degreesPerTick) {
        matchSpeed = Double.NaN;
        matchTurn = Math.abs(degreesPerTick);
        fixedRate = 1.0;
    }

    /** This frame: play the clip at this rate (1 = as made), not tied to walking. */
    public void playAt(double rate) {
        matchSpeed = Double.NaN;
        matchTurn = Double.NaN;
        fixedRate = rate;
    }

    /**
     * The numbers of a clip's note (a timeline line {@code <name> <numbers>}, written by {@code tools/ant_models.py}), or
     * null if it has none (a clip not made by the motion library, such as today's termite walks).
     */
    @Nullable
    public static double[] notesOf(Animation clip, String name) {
        String start = name + " ";
        for (CustomInstructionKeyframeData note : clip.keyFrames().customInstructions()) {
            String text = note.getInstructions().trim();
            if (text.startsWith(start)) {
                try {
                    String[] words = text.substring(start.length()).trim().split("\\s+");
                    double[] numbers = new double[words.length];
                    for (int i = 0; i < words.length; i++) {
                        numbers[i] = Double.parseDouble(words[i]);
                    }
                    return numbers;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** The first number of a clip's note, or NaN if it has none. */
    public static double noteOf(Animation clip, String name) {
        double[] numbers = notesOf(clip, name);
        return numbers == null || numbers.length == 0 ? Double.NaN : numbers[0];
    }

    /**
     * How far the insect walks in one play of a clip, in model units (16 to a block before the renderer's scale), from
     * its stride note; NaN if it has none.
     */
    public static double strideOf(Animation clip) {
        return noteOf(clip, "stride");
    }

    /** How fast the clip plays now (1 = as made): one cycle a stride walked, one a turn turned, or the rate it was given. */
    public double rate() {
        Animation clip = currentAnimation == null ? null : currentAnimation.animation();
        if (clip != pacedClip) {
            pacedClip = clip;
            stride = clip == null ? Double.NaN : strideOf(clip);
            turn = clip == null ? Double.NaN : Math.abs(noteOf(clip, "turn"));
        }
        if (clip != null && !Double.isNaN(matchSpeed) && stride > 0.0) {
            double strideBlocks = stride / 16.0 * animatable.getDrawScale();
            return Math.min(MOST_RATE, Math.max(0.0, matchSpeed) * clip.length() / strideBlocks);
        }
        if (clip != null && !Double.isNaN(matchTurn) && turn > 0.0) {
            return Math.min(MOST_RATE, matchTurn * clip.length() / turn);
        }
        return fixedRate;
    }

    /**
     * Of walks made for different speeds ({@code walks}, slowest first, each with a {@code speeds <low> <high>} note in
     * model units a second), the one for walking this fast (blocks a tick): the one playing (or being changed to) until
     * the speed is a tenth past its band, then the next one up or down; starting to walk, the one whose band holds the
     * speed. {@code plain} if the model has no such walks (today's termites).
     */
    public RawAnimation gearFor(List<RawAnimation> walks, RawAnimation plain, double blocksPerTick) {
        if (lastModel == null) {
            return plain;
        }
        double speed = blocksPerTick * 20.0 * 16.0 / animatable.getDrawScale();
        int gear = walks.indexOf(nextClip != null ? nextClip : currentRawAnimation);
        if (gear < 0) {
            RawAnimation pick = null;
            for (RawAnimation walk : walks) {
                double[] band = bandOf(walk);
                if (band != null && (pick == null || speed >= band[0])) {
                    pick = walk;
                }
            }
            return pick != null ? pick : plain;
        }
        double[] band = bandOf(walks.get(gear));
        if (band == null) {
            return plain;
        }
        if (speed > band[1] * BAND_MARGIN && gear < walks.size() - 1 && bandOf(walks.get(gear + 1)) != null) {
            gear++;
        } else if (speed < band[0] / BAND_MARGIN && gear > 0 && bandOf(walks.get(gear - 1)) != null) {
            gear--;
        }
        return walks.get(gear);
    }

    /** A walk's speed band (model units a second), or null if the model has no such clip or it has no band. */
    @Nullable
    private double[] bandOf(RawAnimation walk) {
        Animation clip = lastModel.getAnimation(animatable, walk.getAnimationStages().get(0).animationName());
        double[] band = clip == null ? null : notesOf(clip, "speeds");
        return band != null && band.length >= 2 ? band : null;
    }

    /**
     * Changing clips. From one clip with a swap note to another (a walk to another speed's walk, a walk to a turn), the
     * old one plays on until its swap moment and the new one goes on from its own (see {@link #adjustTick}); waiting
     * longer than {@link #MOST_WAIT}, or for any other change, GeckoLib's blend.
     */
    @Override
    public void setAnimation(RawAnimation next) {
        if (next != null && next.equals(nextClip)) {
            if (lastTick - waitingSince <= MOST_WAIT) {
                return;                                    // still waiting for the moment
            }
        } else if (next != null && !next.equals(currentRawAnimation) && animationState == State.RUNNING && !needsAnimationReload
                && currentAnimation != null && lastModel != null && !Double.isNaN(noteOf(currentAnimation.animation(), "swap"))) {
            Queue<AnimationProcessor.QueuedAnimation> queue = lastModel.getAnimationProcessor().buildAnimationQueue(animatable, next);
            if (queue != null && !queue.isEmpty() && !Double.isNaN(noteOf(queue.peek().animation(), "swap"))) {
                nextClip = next;
                nextQueue = queue;
                waitingSince = lastTick;
                return;
            }
        }
        nextClip = null;
        nextQueue = null;
        super.setAnimation(next);
    }

    @Override
    protected double adjustTick(double tick) {
        if (shouldResetTick) {
            if (getAnimationState() != State.STOPPED) {
                tickOffset = tick;
            }
            shouldResetTick = false;
            clock = 0.0;
            clockBefore = 0.0;
            lastTick = tick;
            return 0.0;
        }
        if (Double.isNaN(lastTick) || tick < lastTick) {
            lastTick = tick; // the first frame, or the game's time went back (a world loaded again): no jump
        }
        clockBefore = clock;
        double passed = tick - lastTick;
        if (passed > 0.0) {
            // A change between clips takes the same time at any speed; a clip itself runs at its rate.
            clock += passed * (animationState == State.TRANSITIONING ? 1.0 : rate());
            lastTick = tick;
        }
        if (nextQueue != null && animationState == State.RUNNING && currentAnimation != null) {
            swapIfDue();
        }
        if (animationState == State.RUNNING && currentAnimation != null && loops() && currentAnimation.animation().length() > 0.0) {
            clock %= currentAnimation.animation().length();
        }
        return clock;
    }

    /**
     * The change waiting in {@link #nextClip}: if the clip passed one of its swap moments this frame, the new clip takes
     * over from its own swap moment (as far past it as the old one had gone, in ground walked).
     */
    private void swapIfDue() {
        Animation old = currentAnimation.animation();
        double length = old.length();
        double swap = noteOf(old, "swap");
        if (length <= 0.0 || Double.isNaN(swap)) {
            return;
        }
        double before = clockBefore / length;
        double moved = (clock - clockBefore) / length;
        for (int half = 0; half < 2; half++) {
            double ahead = ((swap + 0.5 * half - before) % 1.0 + 1.0) % 1.0;
            if (moved <= 0.0 || moved < ahead) {
                continue;
            }
            AnimationProcessor.QueuedAnimation fresh = nextQueue.poll();
            Animation clip = fresh.animation();
            double past = moved - ahead;
            double oldStride = strideOf(old);
            double newStride = strideOf(clip);
            if (oldStride > 0.0 && newStride > 0.0) {
                past *= oldStride / newStride;             // the same ground walked, in the new clip's cycles
            }
            double phase = noteOf(clip, "swap") + 0.5 * half + past;
            currentAnimation = fresh;
            animationQueue = nextQueue;
            currentRawAnimation = nextClip;
            clock = (phase % 1.0) * clip.length();
            nextClip = null;
            nextQueue = null;
            return;
        }
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
