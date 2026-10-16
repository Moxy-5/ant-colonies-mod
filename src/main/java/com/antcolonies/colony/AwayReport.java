package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;

/**
 * What happened while the colony was away (the user's idea, 2026-10-15): life away from the player cannot be watched,
 * so the brain keeps a tally of it, shown in {@code /antcolonies list}. It starts over each time the colony goes away
 * and is kept after it comes back, until the next time. Only counts; it never changes what the colony does.
 */
public class AwayReport {
    /** The things counted, in the order they are shown. */
    public enum What {
        EGGS_LAID("eggs laid"),
        HATCHED("eggs hatched"),
        GREW_UP("young grown up"),
        QUEEN_FED("pieces fed to the queen"),
        KING_FED("pieces fed to the king"),
        YOUNG_FED("pieces fed to the young"),
        BREAD_MADE("seeds milled into bread"),
        MEALS("meals eaten"),
        FORAGED("pieces of food brought in"),
        PLANTS_USED_UP("plants eaten up"),
        NODULES_GROWN("nodules grown"),
        COMB_RIPENED("comb layers ripened"),
        COMB_AGED("comb layers gone old"),
        DUG("blocks dug"),
        HILL("pellets of soil heaped on the hill"),
        MOUND_BUILT("mound blocks built"),
        OLD_AGE("died of old age"),
        STARVED("starved"),
        YOUNG_STARVED("young starved");

        private final String label;

        What(String label) {
            this.label = label;
        }
    }

    private final Map<What, Integer> counts = new EnumMap<>(What.class);
    /** The queen starved while away. Saved. */
    private boolean queenStarved;
    /** The king starved while away. Saved. */
    private boolean kingStarved;

    void add(What what, int count) {
        if (count > 0) {
            counts.merge(what, count, Integer::sum);
        }
    }

    void queenStarved() {
        queenStarved = true;
    }

    void kingStarved() {
        kingStarved = true;
    }

    void clear() {
        counts.clear();
        queenStarved = false;
        kingStarved = false;
    }

    public boolean isEmpty() {
        return counts.isEmpty() && !queenStarved && !kingStarved;
    }

    /** Whether the king starved while away (for tests). */
    public boolean didKingStarve() {
        return kingStarved;
    }

    /** How many of this were counted (for tests). */
    public int get(What what) {
        return counts.getOrDefault(what, 0);
    }

    /** For example "12 eggs laid, 9 eggs hatched, 300 meals eaten"; "nothing" if nothing happened. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        counts.forEach((what, count) -> parts.add(count + " " + what.label));
        if (queenStarved) {
            parts.add("the queen starved");
        }
        if (kingStarved) {
            parts.add("the king starved");
        }
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    void save(CompoundTag tag) {
        CompoundTag report = new CompoundTag();
        counts.forEach((what, count) -> report.putInt(what.name(), count));
        report.putBoolean("queenStarved", queenStarved);
        report.putBoolean("kingStarved", kingStarved);
        tag.put("awayReport", report);
    }

    void load(CompoundTag tag) {
        clear();
        CompoundTag report = tag.getCompound("awayReport");
        for (What what : What.values()) {
            add(what, report.getInt(what.name()));
        }
        queenStarved = report.getBoolean("queenStarved");
        kingStarved = report.getBoolean("kingStarved");
    }
}
