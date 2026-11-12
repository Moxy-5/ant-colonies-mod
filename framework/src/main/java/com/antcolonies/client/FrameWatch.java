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
    static final long WATCH_FROM_MS = 20;
    static final long LOOK_MS = 5;
    static final int LINES = 30;

    private static volatile Thread renderThread;
    private static volatile long frameStart;
    private static final Object LOCK = new Object();
    private static final List<StackTraceElement[]> looks = new ArrayList<>();
    private static Thread helper;
    private static long gcAtStart;

    /**
     * The stutter count (the user, 2026-11-12: "constant stuttering" while walking, with hardly a frame over 100 ms): a
     * frame of {@link #ROUGH_MS} or more is rough (at 60 frames a second a frame is 17 ms, so one was missed). Every
     * {@link #SUMMARY_MS} the log gets a {@code [frames]} line: how many frames, how many rough, the worst, the
     * memory-clearing time, and what the drawing thread was doing in the rough ones (the deepest game call of each look).
     */
    static final long ROUGH_MS = 25;
    static final long SUMMARY_MS = 10_000;
    private static long summaryStart;
    private static int frames;
    private static int rough;
    private static int veryRough;
    private static long worst;
    private static long gcSum;
    private static int roughLooks;
    private static final java.util.Map<String, Integer> roughWhere = new java.util.HashMap<>();

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
        if (start == 0 || mc.level == null || mc.isPaused()) {
            summaryStart = 0;
            return;
        }
        count(now, ms, gc, taken);
        if (ms < SLOW_MS) {
            return;
        }
        ColonyFrameworkMod.LOGGER.warn("[{}] a frame took {} ms, {} ms of it Java clearing memory; where the drawing"
                + " thread was ({} looks, deepest first):{}", ms >= FREEZE_MS ? "freeze" : "frame", ms, gc, taken.size(),
                taken.isEmpty() ? "" : StallWatch.where(taken, LINES, null));
    }

    private static void count(long now, long ms, long gc, List<StackTraceElement[]> taken) {
        if (summaryStart == 0) {
            summaryStart = now;
            frames = rough = veryRough = roughLooks = 0;
            worst = gcSum = 0;
            roughWhere.clear();
        }
        frames++;
        gcSum += gc;
        worst = Math.max(worst, ms);
        if (ms >= ROUGH_MS) {
            rough++;
            if (ms >= 50) {
                veryRough++;
            }
            for (StackTraceElement[] stack : taken) {
                roughLooks++;
                roughWhere.merge(gameCall(stack), 1, Integer::sum);
            }
        }
        if (now - summaryStart < SUMMARY_MS * 1_000_000) {
            return;
        }
        if (rough > 0) {
            StringBuilder out = new StringBuilder();
            roughWhere.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12).forEach(e ->
                    out.append(String.format("%n    %3d%% %s", e.getValue() * 100 / Math.max(1, roughLooks), e.getKey())));
            ColonyFrameworkMod.LOGGER.info("[frames] last 10 s: {} frames, {} of 25 ms or more, {} of 50 ms or more, the"
                    + " worst {} ms, {} ms Java clearing memory; the drawing thread in the rough ones ({} looks):{}",
                    frames, rough, veryRough, worst, gcSum, roughLooks, out);
        }
        summaryStart = 0;
    }

    /** The deepest call of a look that is the game's or a mod's (Java's own and the libraries' calls skipped). */
    private static String gameCall(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String cls = frame.getClassName();
            if (cls.startsWith("net.minecraft.") || cls.startsWith("com.antcolonies.") || cls.startsWith("com.mojang.")
                    || cls.startsWith("software.bernie.") || cls.startsWith("net.neoforged.") || cls.startsWith("terrablender.")) {
                return cls.substring(cls.lastIndexOf('.') + 1) + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return stack.length > 0 ? stack[0].getClassName() + "." + stack[0].getMethodName() : "?";
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
