package com.antcolonies.entity.body;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where one animal touches another (the Motion Studio, phase 3, step 1, 2026-11-26; {@code docs/plans/motion-studio-phase3.md}):
 * an animal's spot table, {@code assets/<mod>/spots/<animal>.json}, written by {@code tools/spots.py} from its body plan
 * and the game's own model and clip files, never by hand. The game never poses a model itself: it reads here where a
 * prey's spots are at each tick of the clips it plays while held, and where a holder's jaw is at its bite.
 * <p>
 * All numbers in the file are model pixels at size 1 in the geo file's frame: x to the animal's left, y up, z toward its
 * tail. {@link #world} and {@link #holderAt} turn them into the world with where each animal's model is drawn (its
 * own "drawn ahead" pixels) and its size: the same sums as the tool's report.
 * <p>
 * Species-neutral: any prey with spots, any holder with holds (the lion and the buffalo first).
 */
public final class Spots {
    private static final Map<String, Spots> LOADED = new ConcurrentHashMap<>();

    /** A holder's grip: the tick of its clip it bites at, and its jaw then (model px). */
    public record Hold(int hit, double x, double y, double z) {
    }

    private final List<String> names = new ArrayList<>();
    private final List<Double> outs = new ArrayList<>();
    /** Each clip's rows: one a tick, the x, y, z of every spot in order. */
    private final Map<String, double[][]> clips = new HashMap<>();
    /** Each clip's middle row (a loop's: the game cannot know a loop's phase at the speed it is drawn). */
    private final Map<String, double[]> middles = new HashMap<>();
    private final Map<String, Hold> holds = new HashMap<>();

    private Spots(Class<?> owner, String path) {
        try (InputStream in = owner.getResourceAsStream(path)) {
            if (in == null) {
                return;                                 // none: no spots, no holds
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("spots")) {
                JsonObject spots = json.getAsJsonObject("spots");
                for (JsonElement n : spots.getAsJsonArray("names")) {
                    names.add(n.getAsString());
                    outs.add(spots.getAsJsonObject("at").getAsJsonObject(n.getAsString()).get("out").getAsDouble());
                }
                for (Map.Entry<String, JsonElement> clip : spots.getAsJsonObject("clips").entrySet()) {
                    JsonArray rows = clip.getValue().getAsJsonArray();
                    double[][] out = new double[rows.size()][];
                    double[] middle = new double[names.size() * 3];
                    for (int t = 0; t < rows.size(); t++) {
                        JsonArray row = rows.get(t).getAsJsonArray();
                        out[t] = new double[row.size()];
                        for (int k = 0; k < row.size(); k++) {
                            out[t][k] = row.get(k).getAsDouble();
                            middle[k] += out[t][k] / rows.size();
                        }
                    }
                    clips.put(clip.getKey(), out);
                    middles.put(clip.getKey(), middle);
                }
            }
            if (json.has("holds")) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("holds").entrySet()) {
                    JsonObject h = e.getValue().getAsJsonObject();
                    JsonArray jaw = h.getAsJsonArray("jaw");
                    holds.put(e.getKey(), new Hold(h.get("hit").getAsInt(), jaw.get(0).getAsDouble(), jaw.get(1).getAsDouble(),
                            jaw.get(2).getAsDouble()));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot read the spot table " + path, e);
        }
    }

    /**
     * An animal's spot table, read once from its mod's own files.
     *
     * @param owner a class of the animal's own mod (its jar is where the file is read from)
     */
    public static Spots of(Class<?> owner, String modId, String animal) {
        String path = "/assets/" + modId + "/spots/" + animal + ".json";
        return LOADED.computeIfAbsent(path, p -> new Spots(owner, p));
    }

    // --- a prey's spots ----------------------------------------------------------------------------------------------

    public int count() {
        return names.size();
    }

    public String name(int spot) {
        return names.get(spot);
    }

    public int index(String name) {
        return names.indexOf(name);
    }

    /** The way out of its body at this spot (degrees from straight ahead, + to its left): a holder faces in along it. */
    public double out(int spot) {
        return outs.get(spot);
    }

    /**
     * The spot's place (model px) in this clip: a one-shot's at its tick (held at its first and last rows outside them),
     * a loop's its middle; a clip not in the table, the idle's middle.
     */
    public Vec3 at(int spot, String clip, int tick, boolean loop) {
        double[] row;
        double[][] rows = clips.get(clip);
        if (rows == null) {
            row = middles.getOrDefault("idle", new double[names.size() * 3]);
        } else {
            row = loop ? middles.get(clip) : rows[Mth.clamp(tick, 0, rows.length - 1)];
        }
        return new Vec3(row[3 * spot], row[3 * spot + 1], row[3 * spot + 2]);
    }

    /**
     * A model point (px) in the world: from where the animal stands, its heading (Minecraft's yaw), its size and how far
     * toward its head its model is drawn (px).
     */
    public static Vec3 world(Vec3 px, Vec3 feet, float yaw, double size, double drawnAhead) {
        double r = yaw * Mth.DEG_TO_RAD;
        double fx = -Math.sin(r), fz = Math.cos(r), lx = Math.cos(r), lz = Math.sin(r);
        double ahead = (-px.z + drawnAhead) * size / 16.0, left = px.x * size / 16.0;
        return new Vec3(feet.x + fx * ahead + lx * left, feet.y + px.y * size / 16.0, feet.z + fz * ahead + lz * left);
    }

    /** The heading (Minecraft's yaw) of a holder at a spot: facing in along the spot's way out. */
    public float holderYaw(int spot, float preyYaw) {
        return Mth.wrapDegrees(preyYaw - (float) out(spot) - 180.0F);
    }

    // --- a holder's holds --------------------------------------------------------------------------------------------

    @Nullable
    public Hold hold(String clip) {
        return holds.get(clip);
    }

    /**
     * Where a holder stands (flat; its feet's height is the ground's there) so its jaw at its bite meets a point, facing
     * this way (Minecraft's yaw): the point less its jaw's reach ahead and to the side.
     */
    public static Vec3 holderAt(Vec3 point, float yaw, Hold hold, double size, double drawnAhead) {
        Vec3 jaw = world(new Vec3(hold.x(), 0.0, hold.z()), Vec3.ZERO, yaw, size, drawnAhead);
        return new Vec3(point.x - jaw.x, point.y, point.z - jaw.z);
    }
}
