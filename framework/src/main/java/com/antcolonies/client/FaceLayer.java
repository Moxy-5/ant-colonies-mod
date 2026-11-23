package com.antcolonies.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.cache.object.GeoBone;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * An animal's face that shows its mood, drawn on top of whatever clip plays (the Animal Maker Engine's "live layer for
 * animals", the otter first, 2026-11-22; plan {@code docs/plans/otter-play-and-fish.md}, section 6c). Species-neutral:
 * an animal's maker writes {@code assets/<mod>/faces/<animal>.json} (the otter's: {@code tools/otter_models.py game},
 * numbers from its {@code FACES}, picked by the user on the faces page), with its moods by name and its face bones.
 *
 * <p>Each mood is a set of offsets in the clip files' own numbers (degrees, model pixels), added to the bones after the
 * clip has posed them, as the head look is: ears tipped forward or laid back, turned out, folded flat; eyelids down;
 * eyes a touch bigger; whiskers forward or back; jaw open; tongue out; the head tilted and nodded; and, while it moves,
 * the tail raised, curled and swishing. A face's own little life comes on top: blinks at the mood's pace, ear flicks,
 * ears turning about, whiskers quivering. Changing mood eases over about half a second (each number on its own).
 *
 * <p>Clip wins: a clip that closes the eyelids (resting) keeps them closed (the lids go to the further down of the
 * two), and the caller says when the jaw is the clip's (biting, eating) and how much tail to add (none while a
 * standing clip of the mood's own carries the tail).
 */
public final class FaceLayer {
    private static final Map<String, FaceLayer> LOADED = new HashMap<>();
    /** The numbers a mood holds, in this order. */
    private static final String[] KEYS = {"ear_fwd", "ear_out", "ear_flat", "lids", "eyes", "whiskers", "jaw", "tongue",
            "tilt", "nod", "blink", "flick", "scan", "quiver", "tail_lift", "tail_curl", "tail_swish", "tail_speed"};
    private static final int EAR_FWD = 0, EAR_OUT = 1, EAR_FLAT = 2, LIDS = 3, EYES = 4, WHISKERS = 5, JAW = 6, TONGUE = 7,
            TILT = 8, NOD = 9, BLINK = 10, FLICK = 11, SCAN = 12, QUIVER = 13, TAIL_LIFT = 14, TAIL_CURL = 15,
            TAIL_SWISH = 16, TAIL_SPEED = 17;
    /** How quickly a new mood's numbers are reached (seconds to about two thirds of the way; my pick: done in half a second). */
    private static final double EASE = 0.16;

    private final Map<String, float[]> moods = new LinkedHashMap<>();
    private final Map<String, String> bones = new HashMap<>();
    private final String[] tail;
    private final double cycle, lidDrop, earFlick, whiskerRoll, blinkTime, flickTime;
    private final Map<Entity, State> states = new WeakHashMap<>();

    /** One animal's drawn face now (the client keeps one per animal it draws). */
    private static final class State {
        float[] now;
        double last = Double.NaN;
        final double seed;

        State(double seed) {
            this.seed = seed;
        }
    }

    /** What the caller tells the face this frame. */
    public record Frame(String mood, double seconds, boolean jawFree, double tail) {
    }

    private FaceLayer(JsonObject o) {
        cycle = o.get("cycle").getAsDouble();
        lidDrop = o.get("lid_drop").getAsDouble();
        earFlick = o.get("ear_flick").getAsDouble();
        whiskerRoll = o.get("whisker_roll").getAsDouble();
        blinkTime = o.get("blink_time").getAsDouble();
        flickTime = o.get("flick_time").getAsDouble();
        JsonObject b = o.getAsJsonObject("bones");
        for (Map.Entry<String, JsonElement> e : b.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                bones.put(e.getKey(), e.getValue().getAsString());
            }
        }
        JsonArray t = b.getAsJsonArray("tail");
        tail = new String[t.size()];
        for (int i = 0; i < tail.length; i++) {
            tail[i] = t.get(i).getAsString();
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("moods").entrySet()) {
            JsonObject m = e.getValue().getAsJsonObject();
            float[] v = new float[KEYS.length];
            for (int i = 0; i < KEYS.length; i++) {
                v[i] = m.has(KEYS[i]) ? m.get(KEYS[i]).getAsFloat() : 0.0F;
            }
            moods.put(e.getKey(), v);
        }
        if (moods.isEmpty()) {
            throw new IllegalStateException("a face file with no moods");
        }
    }

    /**
     * An animal's face, read once from its mod's own files ({@code assets/<modId>/faces/<animal>.json}).
     *
     * @param owner a class of the animal's own mod (its jar is where the file is read from)
     */
    public static synchronized FaceLayer of(Class<?> owner, String modId, String animal) {
        String path = "/assets/" + modId + "/faces/" + animal + ".json";
        return LOADED.computeIfAbsent(path, p -> {
            try (InputStream in = owner.getResourceAsStream(p)) {
                if (in == null) {
                    throw new IllegalStateException("no face file " + p + " in " + owner.getName() + "'s mod");
                }
                return new FaceLayer(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                throw new IllegalStateException("cannot read the face file " + p, e);
            }
        });
    }

    /** Whether the file has this mood. */
    public boolean has(String mood) {
        return moods.containsKey(mood);
    }

    /** Puts the mood's face on the bones the clip has posed (call from the model's setCustomAnimations). */
    public void draw(Entity animal, Function<String, GeoBone> bone, Frame f) {
        float[] want = moods.getOrDefault(f.mood(), moods.values().iterator().next());
        State s = states.computeIfAbsent(animal, a -> new State((a.getUUID().getLeastSignificantBits() & 0xFFFF) / 6553.6));
        if (s.now == null || Double.isNaN(s.last)) {
            s.now = want.clone();
        } else {
            double dt = Math.max(0.0, Math.min(0.5, f.seconds() - s.last));
            double k = 1.0 - Math.exp(-dt / EASE);
            for (int i = 0; i < want.length; i++) {
                s.now[i] += (float) ((want[i] - s.now[i]) * k);
            }
        }
        s.last = f.seconds();
        float[] v = s.now;
        double t = f.seconds() + s.seed;
        double w = 2.0 * Math.PI * t / cycle;

        // eyelids: the further down of the clip's and the mood's (with its blinks)
        double blink = pulse(t, Math.max(0.3, v[BLINK]), blinkTime);
        double closed = Math.max(v[LIDS], blink);
        for (String side : new String[]{"lid_left", "lid_right"}) {
            GeoBone lid = bone(bone, side);
            if (lid != null) {
                change(lid, () -> lid.setPosY((float) Math.min(lid.getPosY(), lidDrop * closed)));
            }
        }
        for (String side : new String[]{"eye_left", "eye_right"}) {
            GeoBone eye = bone(bone, side);
            if (eye != null && Math.abs(v[EYES] - 1.0F) > 1.0E-3) {
                change(eye, () -> {
                    eye.setScaleX(eye.getScaleX() * v[EYES]);
                    eye.setScaleY(eye.getScaleY() * v[EYES]);
                    eye.setScaleZ(eye.getScaleZ() * v[EYES]);
                });
            }
        }
        // ears: tipped, turned out (and turning about to listen), folded flat; now and then a flick
        double flickRate = v[FLICK] > 0.05 ? 10.0 / v[FLICK] : 0.0;
        double fl = flickRate > 0 ? pulse(t + 1.3, flickRate, flickTime) : 0.0;
        double fr = flickRate > 0 ? pulse(t + 3.7 + 0.37 * flickRate, flickRate, flickTime) : 0.0;
        double scan = v[SCAN] * Math.sin(2.0 * w);
        turn(bone(bone, "ear_left"), v[EAR_FWD] + earFlick * fl, v[EAR_OUT] + scan, v[EAR_FLAT]);
        turn(bone(bone, "ear_right"), v[EAR_FWD] + earFlick * fr, -(v[EAR_OUT] - scan), -v[EAR_FLAT]);
        double wk = v[WHISKERS] + v[QUIVER] * Math.sin(9.0 * w);
        turn(bone(bone, "whisker_left"), 0.0, wk, wk * whiskerRoll);
        turn(bone(bone, "whisker_right"), 0.0, -wk, -wk * whiskerRoll);
        if (f.jawFree()) {
            GeoBone jaw = bone(bone, "jaw");
            if (jaw != null && Math.abs(v[JAW]) > 1.0E-3) {
                // never shut past closed (the clip files' jaw: 0 shut, more open)
                change(jaw, () -> jaw.setRotX((float) Math.min(0.0, jaw.getRotX() - Math.toRadians(v[JAW]))));
            }
            GeoBone tongue = bone(bone, "tongue");
            if (tongue != null && Math.abs(v[TONGUE]) > 1.0E-3) {
                change(tongue, () -> tongue.setPosZ(tongue.getPosZ() - v[TONGUE]));
            }
        }
        turn(bone(bone, "head"), -v[NOD], 0.0, v[TILT]);
        // the tail, while a moving clip carries it (raised at its base, curled, swishing)
        if (f.tail() > 1.0E-3) {
            for (int i = 0; i < tail.length; i++) {
                double swish = v[TAIL_SWISH] * (0.4 + 0.3 * i) * Math.sin(w * v[TAIL_SPEED] - 0.6 * i);
                turn(bone.apply(tail[i]), f.tail() * (i == 0 ? v[TAIL_LIFT] : v[TAIL_CURL]), f.tail() * swish, 0.0);
            }
        }
    }

    /** 0..1: a short bump of this length, once every period seconds. */
    private static double pulse(double t, double period, double length) {
        double u = (t % period) / length;
        return u < 1.0 ? Math.sin(Math.PI * u) : 0.0;
    }

    private GeoBone bone(Function<String, GeoBone> bone, String role) {
        String name = bones.get(role);
        return name == null ? null : bone.apply(name);
    }

    /**
     * Turns a bone by these numbers more, in the clip files' own degrees (GeckoLib reads a clip's x and y turned the
     * other way), keeping its marks as they were (the bones are shared between the animals drawn).
     */
    private static void turn(GeoBone b, double x, double y, double z) {
        if (b == null || Math.abs(x) + Math.abs(y) + Math.abs(z) < 1.0E-4) {
            return;
        }
        change(b, () -> {
            b.setRotX(b.getRotX() - (float) Math.toRadians(x));
            b.setRotY(b.getRotY() - (float) Math.toRadians(y));
            b.setRotZ(b.getRotZ() + (float) Math.toRadians(z));
        });
    }

    private static void change(GeoBone b, Runnable r) {
        boolean marked = b.hasRotationChanged() || b.hasPositionChanged() || b.hasScaleChanged();
        r.run();
        if (!marked) {
            b.resetStateChanges();
        }
    }
}
