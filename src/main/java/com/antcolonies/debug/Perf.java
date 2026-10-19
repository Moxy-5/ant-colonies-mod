package com.antcolonies.debug;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

import com.antcolonies.AntColoniesMod;

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

    /** A finished route search: how many search steps it took and whether it found a route. */
    public static void search(int steps, boolean found, long tookNanos) {
        if (!onServerThread()) {
            return;
        }
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
        AntColoniesMod.LOGGER.info("[perf]\n{}", last);
        nanos.clear();
        worstTickNanos = 0;
        serverNanos = 0;
        worstServerNanos = 0;
        insectTicks = 0;
        searches = 0;
        failedSearches = 0;
        expansions = 0;
        longestSearchNanos = 0;
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
        return out.toString();
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    /** The last report (every 5 seconds). */
    public static String report() {
        return last;
    }
}
