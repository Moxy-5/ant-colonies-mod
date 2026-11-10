package com.antcolonies.client;

import java.util.Collection;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * A far insect's last pose, kept for a moment (2026-11-09, the drawn-only insects' step 3): working out an insect's
 * clips is about half of what drawing it costs ({@code tools/film_profile.py}: GeckoLib searches every bone's list of
 * keyframes from its start, every frame), so an insect small on the screen keeps the pose it was last given, every
 * bone's turn, shift and size, and is drawn with it again on the next frame: its legs move on every other frame while
 * its place still moves every frame. Half the far insects are worked out on one frame and half on the next (by their
 * number), so every frame costs the same. A near insect is worked out every frame as before, and so is every insect
 * once the frames come slower than 50 a second (then nothing is reused, so no insect's legs move under 25 times a
 * second).
 *
 * <p>The pose is kept after the live layer ({@link BodyLanguage}), so the head's turn and the waist's bend are in it.
 * Films work every pose out (their pictures must not depend on the clock), but for {@code -Dantcolonies.poseReuse=half}:
 * every other frame at any distance, to measure it; {@code never} turns it off.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class PoseMemory {
    private static final String MODE = System.getProperty("antcolonies.poseReuse", "");
    /** Poses are used again only while a frame takes less than this (a frame at 60 a second is 16.7 ms). */
    private static final long QUICK_FRAME_NANOS = 20_000_000L;
    /** The frame being drawn, counted up, when it began and how long the one before took. */
    private static long frame;
    private static long frameStart;
    private static long lastFrameNanos = Long.MAX_VALUE;
    /** Poses are kept past this many box widths from the camera, and never nearer than {@link #NEAREST} blocks. */
    private static final double WIDTHS = 20.0;
    private static final double NEAREST = 3.0;
    /** A bone's numbers: its turn, shift and size, three each. */
    private static final int BONE = 9;

    /** The model the pose is of (the full look and the far look have their own bones). */
    private Object model;
    private float[] values;
    /** The frame the pose was worked out on. */
    private long madeOn;

    private PoseMemory() {
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        long now = System.nanoTime();
        lastFrameNanos = frameStart == 0L ? Long.MAX_VALUE : now - frameStart;
        frameStart = now;
        frame++;
    }

    /** Whether this insect's poses are kept just now: far enough to be small on the screen. */
    static boolean far(AbstractAntEntity insect) {
        if (MODE.equals("never")) {
            return false;
        }
        if (MODE.equals("half")) {
            return true;
        }
        if (FilmDirector.active()) {
            return false;
        }
        double edge = Math.max(NEAREST, WIDTHS * insect.getBbWidth());
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(insect.position())
                > edge * edge;
    }

    /** Gives the bones the insect's kept pose, if it has a fresh one of this model; false: work it out. */
    static boolean reuse(AbstractAntEntity insect, Object model, Collection<GeoBone> bones) {
        if (!(insect.getDrawMemory() instanceof PoseMemory memory) || memory.model != model
                || memory.values.length != bones.size() * BONE) {
            return false;
        }
        // its turn to be worked out (half the insects each frame), or its pose is not the last frame's
        if ((frame + insect.getId() & 1L) == 0L || memory.madeOn != frame - 1L) {
            return false;
        }
        if (!MODE.equals("half") && lastFrameNanos > QUICK_FRAME_NANOS) {
            return false;
        }
        float[] v = memory.values;
        int i = 0;
        for (GeoBone bone : bones) {
            bone.updateRotation(v[i], v[i + 1], v[i + 2]);
            bone.updatePosition(v[i + 3], v[i + 4], v[i + 5]);
            bone.updateScale(v[i + 6], v[i + 7], v[i + 8]);
            bone.resetStateChanges();           // as GeckoLib leaves a bone once an insect's clips are worked out
            i += BONE;
        }
        return true;
    }

    /** Keeps the pose the bones were just given. */
    static void keep(AbstractAntEntity insect, Object model, Collection<GeoBone> bones) {
        PoseMemory memory = insect.getDrawMemory() instanceof PoseMemory kept ? kept : new PoseMemory();
        if (memory.values == null || memory.values.length != bones.size() * BONE) {
            memory.values = new float[bones.size() * BONE];
        }
        float[] v = memory.values;
        int i = 0;
        for (GeoBone bone : bones) {
            v[i] = bone.getRotX();
            v[i + 1] = bone.getRotY();
            v[i + 2] = bone.getRotZ();
            v[i + 3] = bone.getPosX();
            v[i + 4] = bone.getPosY();
            v[i + 5] = bone.getPosZ();
            v[i + 6] = bone.getScaleX();
            v[i + 7] = bone.getScaleY();
            v[i + 8] = bone.getScaleZ();
            i += BONE;
        }
        memory.model = model;
        memory.madeOn = frame;
        insect.setDrawMemory(memory);
    }
}
