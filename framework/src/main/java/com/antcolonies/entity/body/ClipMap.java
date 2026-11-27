package com.antcolonies.entity.body;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * An animal's clip map, from its body plan's {@code postures} (the Motion Studio, the new phase 2, step 5, idea 52,
 * 2026-11-27): which clips it plays lying, sitting or standing, the clip that takes it down into each from standing and
 * the clips that take it back up, and so what each one-shot ends in. Before, each goal wrote its own order of clips
 * ("lying: up first", "down, then the feed"), one copy a goal, and wherever one forgot a case a clip was skipped or a calm
 * tick played between (BUGS 376). Now a goal asks for the clip it wants ({@link #toward}) or only to be on its feet
 * ({@link #way} to standing), and the map walks the way there: up first, then down, then the clip, each way clip played
 * out, nothing between.
 * <p>
 * The plan's section, one entry a posture ({@code standing} is where every way starts and ends, its {@code hold} the
 * clip it stands in):
 * <pre>
 * "postures": {"standing": {"hold": "idle"},
 *              "lying": {"hold": "rest", "down": "rest_down", "up": ["rest_up", "rest_stretch_up"], "clips": ["groom", "feed"]},
 *              "sitting": {"hold": "sit", "down": "sit_down", "up": ["sit_up"]}}
 * </pre>
 * {@code hold} is the clip it rests in there, {@code down} the way into it from standing, {@code up} the ways back to
 * standing (the first is the map's own; the others a goal picks, as a wake-up's stretch), {@code clips} the others it
 * plays there. A clip the map does not name is played standing. Species-neutral; {@code tools/clip_check.py --switches}
 * checks the clip changes the same map makes ({@code tools/body_plan.py posture_switches}).
 */
public final class ClipMap {
    public static final String STANDING = "standing";

    /** One posture: the clip it rests in, its way down from standing (null: none), its ways back up, its other clips. */
    public record Posture(String name, String hold, @Nullable String down, List<String> up, List<String> clips) {
    }

    private final Map<String, Posture> postures;
    /** Each clip the map names: the posture it is played in (its start) and the one it ends in. */
    private final Map<String, String> startsIn = new HashMap<>(), endsIn = new HashMap<>();

    private ClipMap(Map<String, Posture> postures) {
        this.postures = postures;
        for (Posture p : postures.values()) {
            put(p.hold(), p.name(), p.name());
            p.clips().forEach(c -> put(c, p.name(), p.name()));
            if (p.down() != null) {
                put(p.down(), STANDING, p.name());
            }
        }
        for (Posture p : postures.values()) {
            for (String c : p.up()) {
                if (startsIn.putIfAbsent(c, p.name()) == null) {
                    endsIn.put(c, STANDING);                    // (a way up two postures share, as a buffalo's up from
                } else if (!endsIn.get(c).equals(STANDING)     // lying and from being held down: the first names its start)
                        || startsIn.get(c).equals(STANDING)) {   // (a standing clip, or a way down, is no way up)
                    throw new IllegalStateException("the clip map names " + c + " as a way up and as something else");
                }
            }
        }
    }

    private void put(String clip, String from, String to) {
        if (startsIn.putIfAbsent(clip, from) != null) {
            throw new IllegalStateException("the clip map names " + clip + " twice");
        }
        endsIn.put(clip, to);
    }

    /** The map in a body plan (null: the plan has no {@code postures}). */
    @Nullable
    public static ClipMap read(JsonObject plan) {
        if (!plan.has("postures")) {
            return null;
        }
        Map<String, Posture> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : plan.getAsJsonObject("postures").entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;                                       // ("about")
            }
            JsonObject p = e.getValue().getAsJsonObject();
            if (!p.has("hold")) {
                throw new IllegalStateException("the clip map's posture '" + e.getKey() + "' has no hold (the clip it rests in)");
            }
            out.put(e.getKey(), new Posture(e.getKey(), p.get("hold").getAsString(),
                    p.has("down") ? p.get("down").getAsString() : null, list(p, "up"), list(p, "clips")));
        }
        if (!out.containsKey(STANDING)) {
            throw new IllegalStateException("a clip map with no standing posture");
        }
        return new ClipMap(out);
    }

    private static List<String> list(JsonObject p, String key) {
        List<String> out = new ArrayList<>();
        if (p.has(key)) {
            p.getAsJsonArray(key).forEach(c -> out.add(c.getAsString()));
        }
        return List.copyOf(out);
    }

    public Posture posture(String name) {
        return postures.get(name);
    }

    /** Every posture, standing first as the plan lists them. */
    public java.util.Collection<Posture> postures() {
        return postures.values();
    }

    /** The posture a clip is played in (a way down is played standing; a clip the map does not name too). */
    public String startsIn(String clip) {
        return startsIn.getOrDefault(clip, STANDING);
    }

    /** The posture a clip leaves it in (a way down: the posture it goes down into; a way up: standing). */
    public String endsIn(String clip) {
        return endsIn.getOrDefault(clip, STANDING);
    }

    /** Whether a clip has it in a posture at any moment: played there, or a way into or out of it (a shared way up too). */
    public boolean touches(String clip, String posture) {
        if (startsIn(clip).equals(posture) || endsIn(clip).equals(posture)) {
            return true;
        }
        Posture p = postures.get(posture);
        return p != null && p.up().contains(clip);
    }

    /** The clip a one-shot leads into once it has played out: the clip it rests in where the one-shot left it. */
    public String after(String clip) {
        return postures.get(endsIn(clip)).hold();
    }

    /**
     * The way clip to play now to be in a posture, or null: it is in it now (any clip of it may start). A way playing
     * plays out (down into a posture, or up from one), but a way down is turned back at once by its way up when it is
     * wanted elsewhere (as a lion called up as it lies down: the way up blends from where it got to). With no way there
     * (a posture the map gives no way down into or up from) it is the clip playing: it stays as it is.
     *
     * @param now      the clip playing
     * @param playedOut whether it has played out (a loop never has)
     */
    @Nullable
    public String way(String now, boolean playedOut, String to) {
        String from = startsIn(now), end = endsIn(now);
        if (!from.equals(end) && !playedOut) {
            Posture down = postures.get(end);
            if (from.equals(STANDING) && !end.equals(to) && !down.up().isEmpty()) {
                return down.up().get(0);                        // going down, wanted elsewhere: back up from where it got to
            }
            return now;                                         // on its way: it plays out
        }
        String at = playedOut ? end : from;
        if (at.equals(to)) {
            return null;
        }
        String next = !at.equals(STANDING) ? first(postures.get(at).up()) : postures.get(to).down();
        return next == null ? now : next;                       // up first, then down (no way: it stays, never jumps)
    }

    @Nullable
    private static String first(List<String> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    /** The clip to play now on the way to a clip: the way there ({@link #way}), then the clip itself, nothing between. */
    public String toward(String now, boolean playedOut, String want) {
        String w = way(now, playedOut, startsIn(want));
        return w == null ? want : w;
    }
}
