package com.antcolonies.entity.body;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.Entity;

/**
 * The drawing's one clock (the Motion Studio, phase 2, step 4, 2026-11-25; the first phase 2, its record in {@code docs/HISTORY.md}):
 * game time, the animal's own ticks and the in-between time, as nanoseconds (50 ms a tick), for every ease a body draws
 * (the stance's tilt, the eyes and the head, the lion's neck, a move's hand over, the crocodile's, monitor's and hippo's
 * bends, legs and eyes, the frog's head). Before, each eased on the computer's clock ({@code System.nanoTime}): a frozen
 * film stepped a tick a picture never showed a body mid-ease (its pictures a second or more apart, past the half second
 * that snaps an ease), slow motion ({@code /tick rate}) eased at full speed, and a paused game eased on. On game time an
 * ease takes the same seconds in play, stops with the game and steps with it.
 * <p>
 * It never goes back (BUGS 380): a frozen game stepped for the camera draws one frame at the end of the new tick, then
 * the tick's own frames from its start, a tick back; an ease cannot be undone, so those frames hold the furthest time
 * drawn (the camera's pictures are taken at it), or each tick would ease twice. A clock gone back over a tick has been
 * reset (the animal drawn anew): it starts from there.
 * <p>
 * A drop-in for {@code System.nanoTime()} at each of those places (their own easing, tuned in play, unchanged); never 0,
 * which they read as "not drawn yet". Drawing only (the render thread); species-neutral.
 */
public final class DrawClock {
    private static final long TICK = 50_000_000L;
    /** Each animal's furthest time drawn. */
    private static final Map<Entity, Long> FURTHEST = new WeakHashMap<>();

    private DrawClock() {
    }

    /** The animal's game time now, in nanoseconds (its ticks and the in-between time, 50 ms a tick), never going back. */
    public static long nanos(Entity entity, float partialTick) {
        long now = (long) ((entity.tickCount + (double) partialTick) * TICK) + 1L;
        Long far = FURTHEST.get(entity);
        if (far != null && now < far && now >= far - TICK) {
            return far;                         // a stepped tick's frames, drawn after its end: held there
        }
        FURTHEST.put(entity, now);
        return now;
    }
}
