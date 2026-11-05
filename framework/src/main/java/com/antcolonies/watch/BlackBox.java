package com.antcolonies.watch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The raid test bench, B2 (the black box, {@code docs/plans/raid-test-bench.md}): one colony's last few minutes, like a
 * plane's flight recorder. It keeps lines with the game tick they were written at (the colony's diary lines, its
 * insects every few seconds, the rules broken) and forgets the oldest: older than {@link #KEEP} ticks, or past
 * {@link #MOST} lines. Kept apart from the world so it can be unit tested ({@code BlackBoxTest}).
 */
public final class BlackBox {
    /** How far back it remembers (ticks): 3 minutes. */
    public static final long KEEP = 3600;
    /** The most lines it keeps (about 3 minutes of 60 insects every 3 seconds, and the diary). */
    public static final int MOST = 6000;

    /** One line and the tick it was written at. */
    public record Line(long tick, String text) {
    }

    private final ArrayDeque<Line> lines = new ArrayDeque<>();
    private final long keep;
    private final int most;

    public BlackBox() {
        this(KEEP, MOST);
    }

    BlackBox(long keep, int most) {
        this.keep = keep;
        this.most = most;
    }

    /** Writes a line at this tick, forgetting what is now too old or too many. */
    public synchronized void add(long tick, String text) {
        lines.addLast(new Line(tick, text));
        trim(tick);
    }

    private void trim(long now) {
        while (!lines.isEmpty() && (lines.size() > most || lines.peekFirst().tick() < now - keep)) {
            lines.removeFirst();
        }
    }

    /** The lines it holds, oldest first. */
    public synchronized List<Line> lines() {
        return new ArrayList<>(lines);
    }

    public synchronized int size() {
        return lines.size();
    }
}
