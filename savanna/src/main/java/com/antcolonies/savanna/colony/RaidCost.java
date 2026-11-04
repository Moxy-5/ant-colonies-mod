package com.antcolonies.savanna.colony;

/**
 * The raid code review, finding 9: the raids' own stopwatch. How long the colony's raids (the trails, the column, the
 * scouts' bookkeeping) take on the server, in all and per raid update, so a speed change is measured before and after.
 * Read by the raid tests and the raid view ({@code /antcolonies raid show}); costs two clock readings a timed call.
 */
public final class RaidCost {
    private static long nanos;
    private static long calls;
    private static long updates;
    private static long worstNanos;
    private static int depth;
    private static long startedAt;

    private RaidCost() {
    }

    /** Starts timing (nested calls count once, in the outermost). */
    static void begin() {
        if (depth++ == 0) {
            startedAt = System.nanoTime();
        }
    }

    /** Ends what {@link #begin} started. */
    static void end() {
        if (--depth == 0) {
            long took = System.nanoTime() - startedAt;
            nanos += took;
            calls++;
            worstNanos = Math.max(worstNanos, took);
        }
    }

    /** One column update worked out (the stages and every raider's place). */
    static void updated() {
        updates++;
    }

    /** Starts the count again. */
    public static void reset() {
        nanos = 0;
        calls = 0;
        updates = 0;
        worstNanos = 0;
    }

    /** The total so far, in milliseconds. */
    public static double totalMillis() {
        return nanos / 1_000_000.0;
    }

    /** The count in words: the total, per column update, and the worst single call. */
    public static String report() {
        return String.format(java.util.Locale.ROOT, "%.2f ms in %d calls, %d column updates (%.3f ms an update), worst call %.3f ms",
                nanos / 1_000_000.0, calls, updates, updates == 0 ? 0.0 : nanos / 1_000_000.0 / updates, worstNanos / 1_000_000.0);
    }
}
