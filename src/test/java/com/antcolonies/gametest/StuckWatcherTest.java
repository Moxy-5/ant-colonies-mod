package com.antcolonies.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

/** The stuck watcher's rule ({@link StuckWatcher.Tracker}): a safety net must be shown to catch what it is for. */
class StuckWatcherTest {
    private static final UUID TERMITE = UUID.randomUUID();

    @Test
    void anInsectThatStaysPutWhileWalkingIsCaughtAfterTheLimit() {
        StuckWatcher.Tracker tracker = new StuckWatcher.Tracker();
        long still = 0;
        for (long tick = 0; tick <= StuckWatcher.STUCK_TICKS + 1; tick++) {
            // Jiggling on the spot (a body turning, sliding back and forth) is not getting anywhere.
            still = tracker.observe(TERMITE, new Vec3(0.1 * (tick % 2), 0, 0), tick);
            tracker.endTick();
        }
        assertTrue(still > StuckWatcher.STUCK_TICKS, "stuck for " + still);
    }

    @Test
    void anInsectWalkingOnIsNeverCaught() {
        StuckWatcher.Tracker tracker = new StuckWatcher.Tracker();
        for (long tick = 0; tick < 5 * StuckWatcher.STUCK_TICKS; tick++) {
            // A slow walk: a block every 50 ticks.
            long still = tracker.observe(TERMITE, new Vec3(tick / 50.0, 0, 0), tick);
            tracker.endTick();
            assertTrue(still < StuckWatcher.STUCK_TICKS, "a walking insect taken for stuck at tick " + tick);
        }
        assertEquals(5 * StuckWatcher.STUCK_TICKS, tracker.watchedTicks());
    }

    @Test
    void stoppingToWorkStartsTheClockAfresh() {
        StuckWatcher.Tracker tracker = new StuckWatcher.Tracker();
        Vec3 spot = Vec3.ZERO;
        for (long tick = 0; tick < StuckWatcher.STUCK_TICKS - 10; tick++) {
            tracker.observe(TERMITE, spot, tick);
            tracker.endTick();
        }
        tracker.endTick(); // a tick with no route (it stopped to dig)
        assertEquals(0, tracker.observe(TERMITE, spot, StuckWatcher.STUCK_TICKS), "a new walk from the same spot starts afresh");
    }
}
