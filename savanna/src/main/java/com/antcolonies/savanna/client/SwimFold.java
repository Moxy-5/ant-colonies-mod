package com.antcolonies.savanna.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A swim clip's legs folded back along the body, read from the clip itself (code review R12-70, 2026-11-26): the first
 * pose of each leg bone, both sides as the clip file has them. {@link MonitorModel} folds a pair of legs toward it when
 * their feet lose the ground over water and no block move fits.
 * <p>
 * Before, the model kept a hand-copied table of the left legs' angles and made the right side by mirroring them. That
 * was true of the arms; the hind legs' lower leg and foot carry the same angles on both sides in the clip (its maker
 * solves each foot to a mirrored place and lands on another set of angles for it), so the mirrored right hind foot was
 * drawn turned round, up to a block from where the swim clip puts it. Nothing is copied by hand now, so nothing can
 * fall out of step.
 * <p>
 * Read once when the model is made (a resource reload makes the model anew).
 */
final class SwimFold {
    static final String[] KINDS = {"arm", "leg"};
    static final String[] PIECES = {"upper", "lower", "foot"};
    static final String[] SIDES = {"_left", "_right"};

    private SwimFold() {
    }

    /**
     * The fold: for each pair ({@link #KINDS}), each side ({@link #SIDES}) and each piece ({@link #PIECES}) the clip's
     * first turn (degrees: x, y, z as the file has them).
     *
     * @param file the animation file among the mod's own files ("/assets/antcolonies/animations/entity/monitor.animation.json")
     */
    static double[][][][] read(String file, String clip) {
        try (InputStream in = SwimFold.class.getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("no file " + file);
            }
            JsonObject bones = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("animations").getAsJsonObject(clip).getAsJsonObject("bones");
            double[][][][] fold = new double[KINDS.length][SIDES.length][PIECES.length][];
            for (int pair = 0; pair < KINDS.length; pair++) {
                for (int side = 0; side < SIDES.length; side++) {
                    for (int piece = 0; piece < PIECES.length; piece++) {
                        String bone = KINDS[pair] + "_" + PIECES[piece] + SIDES[side];
                        if (!bones.has(bone) || !bones.getAsJsonObject(bone).has("rotation")) {
                            throw new IllegalStateException("the clip " + clip + " turns no " + bone);
                        }
                        fold[pair][side][piece] = first(bones.getAsJsonObject(bone).get("rotation"));
                    }
                }
            }
            return fold;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read the fold from " + clip + " in " + file, e);
        }
    }

    /** A bone's first turn: the one turn of a bone held still, or its earliest key's (a key's "post" or "vector" if it has them). */
    private static double[] first(JsonElement rotation) {
        JsonElement at = rotation;
        if (rotation.isJsonObject() && !rotation.getAsJsonObject().has("vector") && !rotation.getAsJsonObject().has("post")) {
            double earliest = Double.MAX_VALUE;
            for (Map.Entry<String, JsonElement> key : rotation.getAsJsonObject().entrySet()) {
                double time = Double.parseDouble(key.getKey());
                if (time < earliest) {
                    earliest = time;
                    at = key.getValue();
                }
            }
        }
        if (at.isJsonObject()) {
            JsonObject key = at.getAsJsonObject();
            at = key.has("vector") ? key.get("vector") : key.get("post");
            if (at.isJsonObject()) {
                at = at.getAsJsonObject().get("vector");
            }
        }
        JsonArray v = at.getAsJsonArray();
        return new double[] {v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble()};
    }
}
