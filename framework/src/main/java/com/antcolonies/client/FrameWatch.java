package com.antcolonies.client;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.debug.StallWatch;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The frame watch (2026-11-12, the user's report: the screen pauses for about a second while walking into new land): the
 * stall watch's twin for the drawing side. {@link StallWatch} only sees the server's ticks, and a slow server tick does
 * not stop the picture; a frozen screen is the drawing thread standing still, or the whole game stopped while Java
 * clears memory (a garbage collection pause). When one frame, from its start to the next one's, takes
 * {@link #SLOW_MS} or more, the log gets a {@code [frame]} line ({@code [freeze]} from {@link #FREEZE_MS}): how long,
 * how much of it Java spent clearing memory, and where the drawing thread was (looked at every {@link #LOOK_MS} ms
 * by a helper thread). Few looks for a long frame mean the whole game stood still, helper and all.
 *
 * <p>Always on in the game; it costs nothing until a frame is already slow. Not while the game is paused or no world
 * is open.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class FrameWatch {
    /** A frame this long is written down (10 frames a second for a moment; my pick). */
    public static final long SLOW_MS = 100;
    /** A frame this long is a freeze anyone sees. */
    public static final long FREEZE_MS = 300;
    /** The helper starts looking once a frame has run this long. */
    static final long WATCH_FROM_MS = 40;
    static final long LOOK_MS = 10;
    static final int LINES = 30;

    private static volatile Thread renderThread;
    private static volatile long frameStart;
    private static final Object LOCK = new Object();
    private static final List<StackTraceElement[]> looks = new ArrayList<>();
    private static Thread helper;
    private static long gcAtStart;

    private FrameWatch() {
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        long now = System.nanoTime();
        long start = frameStart;
        long gcNow = StallWatch.gcMillis();
        List<StackTraceElement[]> taken;
        synchronized (LOCK) {
            taken = looks.isEmpty() ? List.of() : new ArrayList<>(looks);
            looks.clear();
            frameStart = now;
        }
        long gc = gcNow - gcAtStart;
        gcAtStart = gcNow;
        if (renderThread == null) {
            renderThread = Thread.currentThread();
            startHelper();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long ms = (now - start) / 1_000_000;
        if (start == 0 || ms < SLOW_MS || mc.level == null || mc.isPaused()) {
            return;
        }
        ColonyFrameworkMod.LOGGER.warn("[{}] a frame took {} ms, {} ms of it Java clearing memory; where the drawing"
                + " thread was ({} looks, deepest first):{}", ms >= FREEZE_MS ? "freeze" : "frame", ms, gc, taken.size(),
                taken.isEmpty() ? "" : StallWatch.where(taken, LINES, null));
    }

    private static synchronized void startHelper() {
        if (helper != null) {
            return;
        }
        Thread h = new Thread(FrameWatch::watch, "Ant colonies frame watch");
        h.setDaemon(true);
        helper = h;
        h.start();
    }

    private static void watch() {
        while (true) {
            try {
                Thread.sleep(LOOK_MS);
            } catch (InterruptedException e) {
                return;
            }
            long start = frameStart;
            Thread render = renderThread;
            if (start == 0 || render == null || System.nanoTime() - start < WATCH_FROM_MS * 1_000_000) {
                continue;
            }
            StackTraceElement[] stack = render.getStackTrace();
            synchronized (LOCK) {
                if (frameStart == start && looks.size() < 400) {
                    looks.add(stack);
                }
            }
        }
    }
}
