package com.antcolonies.debug;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

import com.antcolonies.ColonyFrameworkMod;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The stopwatch (performance step 1): how much of each server tick our mod uses, split into parts, so we know where the
 * time goes before changing anything. Each part is timed on its own (a route search inside a walk counts as a route
 * search, not as walking). Only the server thread is timed (the client draws its own insects in single player).
 *
 * <p>Every {@link #WINDOW} ticks the totals are folded into a report ({@link #report}), shown by {@code /antcolonies
 * perf} and written to the log. Costs almost nothing itself: two clock readings per timed part.
 */
public final class Perf {
    /** The timed parts. */
    public enum Part {
        INSECTS("insects (vanilla body, goals)"),
        WALKING("walking (navigator)"),
        /** Real sizes step 3: the neighbour grid's lookups and writes ({@code nav/Crowd}), out of walking's time. */
        CROWD("neighbour grid (personal space)"),
        ROUTES("route searches"),
        BRAIN("colony brain"),
        TREES("giant trees (growing, dying, falling, fire)");

        final String label;

        Part(String label) {
            this.label = label;
        }
    }

    /** Ticks in a report (5 seconds). */
    public static final int WINDOW = 100;

    private static final ArrayDeque<long[]> stack = new ArrayDeque<>();
    private static final Map<Part, Long> nanos = new EnumMap<>(Part.class);
    private static long tickNanos;
    private static long worstTickNanos;
    private static long serverNanos;
    private static long worstServerNanos;
    private static long insectTicks;
    private static long searches;
    private static long failedSearches;
    private static long expansions;
    private static long longestSearchNanos;
    private static int ticks;
    private static String last = "No report yet: the stopwatch reports every 5 seconds.";
    /** Who is asking for the route search running now (insect kind and goals), set by the navigator; null = unknown. */
    private static String asker;
    /** Per asker: searches, failed, steps, nanoseconds (this window). */
    private static final Map<String, long[]> askers = new java.util.HashMap<>();

    private Perf() {
    }

    private static boolean onServerThread() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread();
    }

    /** Starts timing a part; must be followed by {@link #end} (in a finally). Returns whether it is timed. */
    public static boolean begin(Part part) {
        if (!onServerThread()) {
            return false;
        }
        stack.push(new long[] {part.ordinal(), System.nanoTime(), 0L});
        return true;
    }

    /** Ends the part begun last (only call when {@link #begin} returned true). */
    public static void end() {
        long[] frame = stack.pop();
        long elapsed = System.nanoTime() - frame[1];
        Part part = Part.values()[(int) frame[0]];
        nanos.merge(part, elapsed - frame[2], Long::sum);
        if (stack.isEmpty()) {
            tickNanos += elapsed;
        } else {
            stack.peek()[2] += elapsed;
        }
        if (part == Part.INSECTS) {
            insectTicks++;
        }
    }

    /** Per colony part (brain): times ticked, nanoseconds, longest single tick (this window). */
    private static final Map<String, long[]> brainParts = new java.util.HashMap<>();

    /** One colony part's once-a-second tick took this long (only counted on the server thread). */
    public static void brainPart(String name, long tookNanos) {
        if (!onServerThread()) {
            return;
        }
        long[] mine = brainParts.computeIfAbsent(name, k -> new long[3]);
        mine[0]++;
        mine[1] += tookNanos;
        mine[2] = Math.max(mine[2], tookNanos);
    }

    /** Per insect kind: ticks, nanoseconds (with its walking and route searches) this window. */
    private static final Map<String, long[]> kinds = new java.util.HashMap<>();

    /** One insect's tick took this long (its walking and route searches included). */
    public static void insectKind(String kind, long tookNanos) {
        long[] mine = kinds.computeIfAbsent(kind, k -> new long[2]);
        mine[0]++;
        mine[1] += tookNanos;
    }

    /** Who asks for the next route searches (the navigator sets it before planning, clears it with null after). */
    public static void asking(String who) {
        asker = who;
    }

    /** A finished route search: how many search steps it took and whether it found a route. */
    public static void search(int steps, boolean found, long tookNanos) {
        if (!onServerThread()) {
            return;
        }
        long[] mine = askers.computeIfAbsent(asker == null ? "(no insect)" : asker, k -> new long[4]);
        mine[0]++;
        mine[1] += found ? 0 : 1;
        mine[2] += steps;
        mine[3] += tookNanos;
        searches++;
        expansions += steps;
        if (!found) {
            failedSearches++;
        }
        longestSearchNanos = Math.max(longestSearchNanos, tookNanos);
    }

    /** At the end of every server tick: closes the tick and, every {@link #WINDOW} ticks, makes a report. */
    public static void endOfTick(MinecraftServer server) {
        stack.clear(); // (never left open, but never let a mistake grow it)
        worstTickNanos = Math.max(worstTickNanos, tickNanos);
        tickNanos = 0;
        long[] times = server.getTickTimesNanos();
        long serverTick = times[Math.floorMod(server.getTickCount() - 1, times.length)]; // the last finished tick
        serverNanos += serverTick;
        worstServerNanos = Math.max(worstServerNanos, serverTick);
        if (++ticks < WINDOW) {
            return;
        }
        last = buildReport();
        ColonyFrameworkMod.LOGGER.info("[perf]\n{}", last);
        nanos.clear();
        worstTickNanos = 0;
        serverNanos = 0;
        worstServerNanos = 0;
        insectTicks = 0;
        searches = 0;
        failedSearches = 0;
        expansions = 0;
        longestSearchNanos = 0;
        askers.clear();
        brainParts.clear();
        kinds.clear();
        ticks = 0;
    }

    private static String buildReport() {
        long ours = nanos.values().stream().mapToLong(Long::longValue).sum();
        double insects = (double) insectTicks / ticks;
        StringBuilder out = new StringBuilder();
        out.append(String.format("Last 5 s: %.0f insects ticking (real mobs, all colonies)\n", insects));
        out.append(String.format("Whole server: %.2f ms a tick (worst %.1f), out of 50\n", ms(serverNanos) / ticks, ms(worstServerNanos)));
        out.append(String.format("Our mod: %.2f ms a tick (worst tick %.1f ms)\n", ms(ours) / ticks, ms(worstTickNanos)));
        for (Part part : Part.values()) {
            out.append(String.format("  %s: %.2f ms\n", part.label, ms(nanos.getOrDefault(part, 0L)) / ticks));
        }
        if (insects > 0) {
            out.append(String.format("  per insect: %.3f ms\n", ms(ours - nanos.getOrDefault(Part.BRAIN, 0L) - nanos.getOrDefault(Part.TREES, 0L)) / ticks / insects));
        }
        out.append(String.format("Route searches: %.1f a second, %d failed, %.0f steps each, longest %.1f ms",
                searches / (ticks / 20.0), failedSearches, searches == 0 ? 0.0 : (double) expansions / searches, ms(longestSearchNanos)));
        reportKinds(out);
        reportBrainParts(out);
        reportAskers(out);
        reportNewLand(out);
        return out.toString();
    }

    /** The insects by kind: how many tick on average, and their time (walking and route searches included). */
    private static void reportKinds(StringBuilder out) {
        if (kinds.isEmpty()) {
            return;
        }
        out.append("\nInsects by kind (how many, ms a tick with their walking and searches):");
        kinds.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1])).forEach(e -> {
            long[] v = e.getValue();
            out.append(String.format("\n  %.0f  %.2f ms  %s", (double) v[0] / ticks, ms(v[1]) / ticks, e.getKey()));
        });
    }

    /** The colony brain's costliest parts, most time first (with the longest single tick of one colony's part). */
    private static void reportBrainParts(StringBuilder out) {
        if (brainParts.isEmpty()) {
            return;
        }
        out.append("\nColony brain by part (ms a tick, longest single ms, times):");
        brainParts.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1])).limit(8).forEach(e -> {
            long[] v = e.getValue();
            out.append(String.format("\n  %.2f ms  longest %.1f  x%d  %s", ms(v[1]) / ticks, ms(v[2]), v[0], e.getKey()));
        });
    }

    /** The route searches' costliest askers (insect kind and running goals), most time first. */
    private static void reportAskers(StringBuilder out) {
        if (askers.isEmpty()) {
            return;
        }
        out.append("\nRoute searches by who asked (ms a tick, a second: searches / failed, steps each):");
        askers.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[3], a.getValue()[3])).limit(10).forEach(e -> {
            long[] v = e.getValue();
            out.append(String.format("\n  %.2f ms  %.1f / %.1f  %.0f  %s", ms(v[3]) / ticks, v[0] / (ticks / 20.0),
                    v[1] / (ticks / 20.0), (double) v[2] / v[0], e.getKey()));
        });
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    /**
     * New land (M7.5 step 7): the giant trees' share of world generation. It runs on the world's builder threads, not in
     * the server tick, so it is counted apart (any thread, safely) and shown as its own lines: planning a tree (picking
     * it and working out its shape, once per tree) and putting it down (once per chunk it reaches). Slow here means
     * new land loads slowly, not a slow tick. The ground's height before a chunk is made is worked out from the world's
     * shape (slow, docs/plans/savanna-loading.md), so how often that is done and how often a remembered answer serves are
     * counted too.
     */
    public enum NewLand {
        PLANNING("planned"),
        PLACING("put down, chunk by chunk"),
        GROUND("ground heights worked out from the world's shape"),
        GROUND_KEPT("ground heights remembered");

        final String label;
        final java.util.concurrent.atomic.LongAdder count = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.LongAdder total = new java.util.concurrent.atomic.LongAdder();
        final java.util.concurrent.atomic.AtomicLong longest = new java.util.concurrent.atomic.AtomicLong();

        NewLand(String label) {
            this.label = label;
        }
    }

    /** Counts one piece of new-land work that took this long (from any thread). */
    public static void newLand(NewLand part, long tookNanos) {
        part.count.increment();
        part.total.add(tookNanos);
        part.longest.accumulateAndGet(tookNanos, Math::max);
    }

    private static void reportNewLand(StringBuilder out) {
        boolean any = false;
        for (NewLand part : NewLand.values()) {
            long n = part.count.sumThenReset();
            long total = part.total.sumThenReset();
            long longest = part.longest.getAndSet(0);
            if (n == 0) {
                continue;
            }
            if (!any) {
                out.append("\nNew land, giant trees (world builder threads, not in the tick):");
                any = true;
            }
            out.append(total == 0 ? String.format("\n  %d %s", n, part.label)
                    : String.format("\n  %d %s, %.3f ms each, longest %.1f ms", n, part.label, ms(total) / n, ms(longest)));
        }
    }

    /** The last report (every 5 seconds). */
    public static String report() {
        return last;
    }
}
