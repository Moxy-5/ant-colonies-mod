package com.antcolonies.entity.body;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * An animal's gaits from its body plan, the one place they are written (the Motion Studio, phase 2, step 2, 2026-11-25;
 * the first phase 2, its record in {@code docs/HISTORY.md}): each gait clip's own speed (the plan's {@code checks.walks}, which the
 * offline checker reads too), the speed it starts at and its play-speed limits (the plan's {@code gaits}). The game picks
 * the gait and plays its clip at the speed its stride needs, within the limits; before, the game kept copies of the
 * clips' speeds and the switch points in Java, and two had drifted from the plans.
 * <p>
 * Speeds are model pixels a second at size 1 in the plan (as {@code walks}); here blocks a tick at size 1.
 * Species-neutral: any animal whose plan has a {@code gaits} section.
 */
public final class Gaits {
    /**
     * A gait: its clip, the speed its clip is made for, the speed it starts at, its play-speed limits, the moment of its
     * clip (clip ticks) it changes into each other gait at ({@link GaitShift}), its clip as a loop to play (equal to
     * any other loop of the same clip, so asking for it again never restarts it), and the young's own clip of it with
     * the size it is played under (null and 0: none; the Motion Studio, the new phase 2, step 6).
     */
    public record Gait(String clip, double madeFor, double from, double min, double max, Map<String, Double> changeAt,
                       RawAnimation loop, @Nullable Gait young, double youngBelow) {
        /** The clip's play speed for this speed (blocks a tick at size 1): as the stride needs, within the limits. */
        public double rate(double speed) {
            return Mth.clamp(speed / madeFor, min, max);
        }

        /**
         * The gait as an animal of this size plays it: under its young size the young's own clip (a calf's short legs
         * stepping quicker than a grown one's played small), else this. Picked by the grown gait's start (one pace).
         */
        public Gait at(double scale) {
            return young != null && scale < youngBelow ? young : this;
        }
    }

    /** The gaits slowest first. */
    private final List<Gait> order;
    private final Map<String, Gait> byClip = new LinkedHashMap<>();

    private Gaits(List<Gait> order) {
        this.order = List.copyOf(order);
        for (Gait g : order) {
            byClip.put(g.clip(), g);
        }
    }

    /**
     * The speed a gait is judged by: blocks a tick at size 1 (a calf's short stride is drawn as a grown one's at its own
     * size). Sizes under 0.3 count as 0.3, as before.
     */
    public static double perSize(double stride, double scale) {
        return stride / Math.max(0.3, scale);
    }

    /** The gait for this speed (blocks a tick at size 1): the fastest whose start it has reached. */
    public Gait pick(double speed) {
        Gait best = order.get(0);
        for (Gait g : order) {
            if (speed > g.from()) {
                best = g;
            }
        }
        return best;
    }

    /** The gait of that clip (a gait the plan lists), or null. */
    @Nullable
    public Gait get(String clip) {
        return byClip.get(clip);
    }

    /** The gait of that clip; the plan must list it (an animal's code asks only for its own gaits). */
    public Gait of(String clip) {
        Gait g = byClip.get(clip);
        if (g == null) {
            throw new IllegalArgumentException("no gait '" + clip + "' in the body plan (" + byClip.keySet() + ")");
        }
        return g;
    }

    public List<Gait> order() {
        return order;
    }

    /**
     * Reads a plan's gaits, or null when it has no {@code gaits} section. {@code gaits.order} names the moving gaits slowest
     * first; each gait's {@code from} (px a second at size 1; the first needs none), {@code min} and {@code max}, and
     * {@code change} (seconds of its clip, written by {@code tools/clip_check.py --gait-changes --write}); a gait outside
     * the order (a lion's stalk) is read too and used by name. Its clip's speed comes from {@code checks.walks}.
     */
    @Nullable
    static Gaits read(JsonObject plan) {
        if (!plan.has("gaits")) {
            return null;
        }
        JsonObject gaits = plan.getAsJsonObject("gaits");
        JsonObject walks = plan.getAsJsonObject("checks").getAsJsonObject("walks");
        double toBlocksATick = 1.0 / 16.0 / 20.0;
        List<String> names = new ArrayList<>();
        gaits.getAsJsonArray("order").forEach(e -> names.add(e.getAsString()));
        for (String key : gaits.keySet()) {
            if (!key.equals("order") && !key.equals("about") && !names.contains(key)) {
                names.add(key);                         // by name only (never picked by speed)
            }
        }
        List<Gait> order = new ArrayList<>();
        List<Gait> others = new ArrayList<>();
        for (String name : names) {
            JsonObject g = gaits.getAsJsonObject(name);
            if (!walks.has(name)) {
                throw new IllegalStateException("gait '" + name + "' has no clip speed in checks.walks");
            }
            Gait young = null;
            double below = 0.0;
            if (g.has("young")) {
                // a young clip of the gait (gaits.<gait>.young: its clip, the size it is played under; its own min, max
                // and change moments, else the grown gait's limits and no moments); its clip speed in checks.walks too
                JsonObject y = g.getAsJsonObject("young");
                String clip = y.get("clip").getAsString();
                if (!walks.has(clip)) {
                    throw new IllegalStateException("young clip '" + clip + "' of gait '" + name + "' has no clip speed in checks.walks");
                }
                below = y.get("below").getAsDouble();
                young = new Gait(clip, walks.get(clip).getAsDouble() * toBlocksATick, 0.0,
                        (y.has("min") ? y : g).get("min").getAsDouble(), (y.has("max") ? y : g).get("max").getAsDouble(),
                        changes(y), RawAnimation.begin().thenLoop(clip), null, 0.0);
            }
            Gait gait = new Gait(name, walks.get(name).getAsDouble() * toBlocksATick,
                    g.has("from") ? g.get("from").getAsDouble() * toBlocksATick : 0.0,
                    g.get("min").getAsDouble(), g.get("max").getAsDouble(), changes(g), RawAnimation.begin().thenLoop(name),
                    young, below);
            (order.size() < gaits.getAsJsonArray("order").size() ? order : others).add(gait);
        }
        Gaits out = new Gaits(order);
        others.forEach(g -> out.byClip.put(g.clip(), g));
        return out;
    }

    /** A gait's change moments (clip ticks; the plan's seconds of its clip), by the clip it changes into. */
    private static Map<String, Double> changes(JsonObject g) {
        Map<String, Double> changeAt = new LinkedHashMap<>();
        if (g.has("change")) {
            g.getAsJsonObject("change").entrySet().forEach(e -> changeAt.put(e.getKey(), e.getValue().getAsDouble() * 20.0));
        }
        return Map.copyOf(changeAt);
    }
}
