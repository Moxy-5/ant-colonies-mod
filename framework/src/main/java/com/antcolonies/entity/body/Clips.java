package com.antcolonies.entity.body;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import software.bernie.geckolib.animation.RawAnimation;

/**
 * An animal's clips as its clip file says them: each clip's length and whether it goes round and round (the Motion
 * Studio, the new phase 2, step 3, idea 53, 2026-11-27). Before, each animal's Java kept its own table of clip lengths
 * typed by hand from the files ({@code LionEntity.DOING_TICKS}, the buffalo's), a copy that falls behind the first time
 * a clip is remade; the game now reads them from the file the clip is drawn from, the one place they are written.
 * <p>
 * Read once from the animal's own mod's jar on both sides (a dedicated server has no resource packs; as the body plans,
 * spot tables and move files are read), as a stream: of each clip only its {@code animation_length} and {@code loop},
 * never its bones (the lion's file is 11 MB). Species-neutral: any animal's file.
 */
public final class Clips {
    /** One clip: its length (game ticks, rounded) and whether it loops (else it plays once, held on its last frame). */
    public record Clip(int ticks, boolean loops) {
    }

    private static final Map<String, Clips> LOADED = new ConcurrentHashMap<>();

    private final Class<?> owner;
    private final String path;
    /** Not final: {@link #reloadAll} swaps in the clips read again. */
    private volatile Map<String, Clip> clips;
    private final Map<String, RawAnimation> anims = new ConcurrentHashMap<>();

    private Clips(Class<?> owner, String path) {
        this.owner = owner;
        this.path = path;
        this.clips = read(owner, path);
    }

    /**
     * An animal's clips, read once from its mod's own clip file {@code assets/<modId>/animations/entity/<file>.animation.json}.
     *
     * @param owner a class of the animal's own mod (its jar is where the file is read from)
     */
    public static Clips of(Class<?> owner, String modId, String file) {
        String path = "/assets/" + modId + "/animations/entity/" + file + ".animation.json";
        return LOADED.computeIfAbsent(path, p -> new Clips(owner, p));
    }

    /** Reads every clip file again (the film camera's watch mode, after a maker rewrote one: no relaunch). */
    public static void reloadAll() {
        for (Clips c : LOADED.values()) {
            c.clips = read(c.owner, c.path);
            c.anims.clear();
        }
    }

    private static Map<String, Clip> read(Class<?> owner, String path) {
        Map<String, Clip> out = new HashMap<>();
        try (InputStream in = owner.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no clip file " + path);
            }
            JsonReader json = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            json.beginObject();
            while (json.hasNext()) {
                if (!json.nextName().equals("animations")) {
                    json.skipValue();
                    continue;
                }
                json.beginObject();
                while (json.hasNext()) {
                    String name = json.nextName();
                    double length = -1;
                    boolean loops = false;
                    json.beginObject();
                    while (json.hasNext()) {
                        String key = json.nextName();
                        if (key.equals("animation_length")) {
                            length = json.nextDouble();
                        } else if (key.equals("loop") && json.peek() == JsonToken.BOOLEAN) {
                            loops = json.nextBoolean();         // ("hold_on_last_frame", a string: not a loop)
                        } else {
                            json.skipValue();
                        }
                    }
                    json.endObject();
                    if (length < 0) {
                        throw new IllegalStateException("clip " + name + " in " + path + " has no animation_length");
                    }
                    out.put(name, new Clip((int) Math.round(length * 20.0), loops));
                }
                json.endObject();
            }
            json.endObject();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read the clip file " + path, e);
        }
        return Map.copyOf(out);
    }

    public boolean has(String clip) {
        return clips.containsKey(clip);
    }

    /** The clip's length (game ticks); a clip the file lacks has none (0). */
    public int ticks(String clip) {
        Clip c = clips.get(clip);
        return c == null ? 0 : c.ticks();
    }

    /** Whether the file has the clip go round and round. */
    public boolean loops(String clip) {
        Clip c = clips.get(clip);
        return c != null && c.loops();
    }

    /**
     * The clip as GeckoLib plays it, the way its file says: round and round, or once and held on its last frame (the
     * pose stays until the next clip; "once" and "held" are both "not a loop" to every checking tool). The same object
     * each time, so asking for it again never restarts it.
     */
    public RawAnimation anim(String clip) {
        return anims.computeIfAbsent(clip, c -> loops(c) ? RawAnimation.begin().thenLoop(c) : RawAnimation.begin().thenPlayAndHold(c));
    }
}
