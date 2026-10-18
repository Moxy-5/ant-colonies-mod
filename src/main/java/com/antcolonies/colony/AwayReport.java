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
        YOUNG_STARVED("young starved"),
        SECOND_BATCH("second founding batches begun (the first workers all died)"),
        PATROLS("patrols sent"),
        PATROLLERS_LOST("patrollers lost"),
        NO_FORAGING_DAYS("days with no foraging (no patroller came back)"),
        FORAGING_NIGHTS("foraging nights"),
        HOLES_OPENED("holes opened"),
        FOUND_FROM_HOLES("food plants found from the holes"),
        GALLERY_PIECES("gallery pieces grown"),
        GALLERY_TO_FOOD("gallery pieces grown toward food"),
        GALLERY_BLOCKS("blocks of gallery grown"),
        FILLED_IN("gallery branches filled in"),
        FILLED_BLOCKS("blocks of gallery packed with soil"),
        DANGER_FORGOTTEN("dangerous places forgotten");

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
    /** The founders dug their chamber while away and closed themselves in. Saved. */
    private boolean foundersSealed;

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

    void foundersSealed() {
        foundersSealed = true;
    }

    /** Whether the founders dug their chamber while away and closed themselves in (for tests). */
    public boolean didFoundersSeal() {
        return foundersSealed;
    }

    void clear() {
        counts.clear();
        queenStarved = false;
        kingStarved = false;
        foundersSealed = false;
    }

    public boolean isEmpty() {
        return counts.isEmpty() && !queenStarved && !kingStarved && !foundersSealed;
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
        if (foundersSealed) {
            parts.add(0, "the founders dug their chamber and closed themselves in");
        }
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    void save(CompoundTag tag) {
        CompoundTag report = new CompoundTag();
        counts.forEach((what, count) -> report.putInt(what.name(), count));
        report.putBoolean("queenStarved", queenStarved);
        report.putBoolean("kingStarved", kingStarved);
        report.putBoolean("foundersSealed", foundersSealed);
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
        foundersSealed = report.getBoolean("foundersSealed");
    }
}
