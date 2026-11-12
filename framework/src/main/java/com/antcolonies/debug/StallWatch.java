package com.antcolonies.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.antcolonies.ColonyFrameworkMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The stall watch (savanna loading, round 2, step L1, {@code docs/plans/savanna-loading-2.md}): when one server tick runs
 * longer than {@link #STALL_MS} (the game freezes: "Can't keep up!"), a helper thread looks at what the server thread is
 * doing every {@link #LOOK_MS} ms until the tick ends, then the log says how long it took and where the time went
 * ({@code [stall]} lines: the calls seen in most looks, deepest first). It names the code that froze the game, ours or
 * Minecraft's (for example a tree update reaching into land not made yet, which makes the server build it on the spot).
 *
 * <p>Always on, in the game and the test servers; it costs nothing until a tick is already slow.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID)
public final class StallWatch {
    /** A tick this long is a stall (my pick: a tick should take 50 ms; 300 is a freeze anyone sees). */
    public static final long STALL_MS = 300;
    /**
     * A tick this long is a spike (tick spikes, 2026-11-10; the user feels them from about 70 ms): it gets a shorter
     * {@code [spike]} record of the same kind.
     */
    public static final long SPIKE_MS = 70;
    /** The helper starts looking once a tick has run this long (a tick should take 50 ms). */
    static final long WATCH_FROM_MS = 50;
    /** At most this many lines of calls in a spike's record. */
    static final int SPIKE_LINES = 14;
    /** How often the helper looks during a slow tick. */
    static final long LOOK_MS = 10;
    /** At most this many lines of calls in a stall's report. */
    static final int LINES = 40;

    private static volatile Thread serverThread;
    private static volatile long tickStart;
    private static final Object LOCK = new Object();
    private static final List<StackTraceElement[]> looks = new ArrayList<>();
    private static Thread helper;

    /** Counts for a summary (the flight run): stalls, their total and longest time (ms). */
    public static final AtomicLong STALLS = new AtomicLong();
    public static final AtomicLong STALL_TOTAL_MS = new AtomicLong();
    public static final AtomicLong STALL_LONGEST_MS = new AtomicLong();
    /** The stalls of this run, one line each: how long and the first of our calls seen (or Minecraft's top). */
    public static final List<String> SEEN = java.util.Collections.synchronizedList(new ArrayList<>());

    private static long gcAtStart;

    /** The time Java has spent in memory-clearing pauses so far (ms); a pause stops every thread, the server's too. */
    public static long gcMillis() {
        long sum = 0;
        for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!bean.getName().contains("Concurrent")) {
                sum += Math.max(0, bean.getCollectionTime());
            }
        }
        return sum;
    }

    private StallWatch() {
    }

    @SubscribeEvent
    public static void onTickStart(ServerTickEvent.Pre event) {
        if (serverThread == null) {
            serverThread = Thread.currentThread();
            startHelper();
        }
        gcAtStart = gcMillis();
        tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void onTickEnd(ServerTickEvent.Post event) {
        long start = tickStart;
        tickStart = 0;
        List<StackTraceElement[]> taken;
        synchronized (LOCK) {
            if (looks.isEmpty()) {
                return;
            }
            taken = new ArrayList<>(looks);
            looks.clear();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (ms >= SPIKE_MS) {
            report(ms, gcMillis() - gcAtStart, taken);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        serverThread = null;
        tickStart = 0;
        Thread h = helper;
        helper = null;
        if (h != null) {
            h.interrupt();
        }
        synchronized (LOCK) {
            looks.clear();
        }
    }

    private static synchronized void startHelper() {
        if (helper != null) {
            return;
        }
        Thread h = new Thread(StallWatch::watch, "Ant colonies stall watch");
        h.setDaemon(true);
        helper = h;
        h.start();
    }

    private static void watch() {
        while (helper == Thread.currentThread()) {
            try {
                Thread.sleep(LOOK_MS);
            } catch (InterruptedException e) {
                return;
            }
            long start = tickStart;
            Thread server = serverThread;
            if (start == 0 || server == null || System.nanoTime() - start < WATCH_FROM_MS * 1_000_000) {
                continue;
            }
            StackTraceElement[] stack = server.getStackTrace();
            synchronized (LOCK) {
                if (tickStart == start) {
                    looks.add(stack);
                }
            }
        }
    }

    private static void report(long ms, long gc, List<StackTraceElement[]> taken) {
        boolean stall = ms >= STALL_MS;
        if (stall) {
            STALLS.incrementAndGet();
            STALL_TOTAL_MS.addAndGet(ms);
            STALL_LONGEST_MS.accumulateAndGet(ms, Math::max);
        }
        String[] ours = new String[1];
        String out = where(taken, stall ? LINES : SPIKE_LINES, ours);
        StackTraceElement[] typical = taken.get(taken.size() / 2);
        String top = typical.length > 0 ? name(typical[0]) : "?";
        if (stall) {
            SEEN.add(ms + " ms, " + (ours[0] != null ? ours[0] : "Minecraft: " + top));
        }
        ColonyFrameworkMod.LOGGER.warn("[{}] a server tick took {} ms, {} ms of it Java clearing memory; where the time went ({} looks,"
                + " deepest first):{}",
                stall ? "stall" : "spike", ms, gc, taken.size(), out);
    }

    /**
     * Where a thread's time went, from looks at it: the calls of the middle look seen in at least half the looks, deepest
     * first, at most {@code maxLines} lines (shared with the frame watch). {@code ours}, if given, gets the first of our
     * own calls.
     */
    public static String where(List<StackTraceElement[]> taken, int maxLines, String[] ours) {
        Map<String, Integer> counts = new HashMap<>();
        for (StackTraceElement[] stack : taken) {
            Set<String> once = new LinkedHashSet<>();
            for (StackTraceElement frame : stack) {
                once.add(name(frame));
            }
            for (String name : once) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        StackTraceElement[] typical = taken.get(taken.size() / 2);
        StringBuilder out = new StringBuilder();
        int lines = 0;
        Set<String> shown = new LinkedHashSet<>();
        for (StackTraceElement frame : typical) {
            String name = name(frame);
            int n = counts.getOrDefault(name, 0);
            if (n * 2 < taken.size() || !shown.add(name) || boilerplate(name)) {
                continue;
            }
            if (ours != null && ours[0] == null && frame.getClassName().startsWith("com.antcolonies")
                    && !frame.getClassName().contains(".debug.")) {
                ours[0] = name + ":" + frame.getLineNumber();
            }
            if (lines++ < maxLines) {
                out.append(String.format("%n    %3d%% %s:%d", n * 100 / taken.size(), name, frame.getLineNumber()));
            }
        }
        return out.toString();
    }

    private static String name(StackTraceElement frame) {
        String cls = frame.getClassName();
        return cls.substring(cls.lastIndexOf('.') + 1) + "." + frame.getMethodName();
    }

    /** The calls every tick passes through, left out of the report. */
    private static boolean boilerplate(String name) {
        return name.startsWith("MinecraftServer.") || name.startsWith("Thread.") || name.startsWith("DedicatedServer.")
                || name.startsWith("IntegratedServer.") || name.startsWith("GameTestServer.");
    }
}
