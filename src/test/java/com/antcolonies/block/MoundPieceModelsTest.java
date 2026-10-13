package com.antcolonies.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * The cathedral pieces' model files (drawn by tools/termite_blocks.py) look the way the game's code shapes them, and
 * every block state has a model whose files exist (the game tests never load models).
 */
class MoundPieceModelsTest {
    private static final String ASSETS = "/assets/antcolonies/";
    private static final String[] AGES = {"fresh", "setting", "dry", "old"};

    private static JsonObject json(String path) throws Exception {
        try (InputStream in = MoundPieceModelsTest.class.getResourceAsStream(ASSETS + path)) {
            assertNotNull(in, "no file " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static boolean exists(String path) {
        return MoundPieceModelsTest.class.getResource(ASSETS + path) != null;
    }

    /** The top of the model's soil at the middle of 2-pixel column (u, w), in 2-pixel layers. */
    private static int heightAt(JsonObject model, int u, int w) {
        double x = u * 2 + 1;
        double z = w * 2 + 1;
        double top = 0;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            double[] from = numbers(element, "from");
            double[] to = numbers(element, "to");
            if (from[0] <= x && x <= to[0] && from[2] <= z && z <= to[2]) {
                top = Math.max(top, to[1]);
            }
        }
        return (int) Math.round(top / 2);
    }

    /** The width (pixels) of the model's column at height y (pixels). */
    private static double widthAt(JsonObject model, double y) {
        double width = 0;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            double[] from = numbers(element, "from");
            double[] to = numbers(element, "to");
            if (from[1] <= y && y <= to[1]) {
                width = Math.max(width, to[0] - from[0]);
            }
        }
        return width;
    }

    private static double[] numbers(JsonObject element, String key) {
        var array = element.getAsJsonArray(key);
        return new double[] {array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    @Test
    void rampModelsRiseLikeTheRampBlock() throws Exception {
        String[] models = {"straight", "outer", "inner"};
        StairsShape[] shapes = {StairsShape.STRAIGHT, StairsShape.OUTER_RIGHT, StairsShape.INNER_RIGHT};
        for (int i = 0; i < models.length; i++) {
            JsonObject model = json("models/block/termite_ramp_" + models[i] + "_setting.json");
            for (int u = 0; u < 8; u++) {
                for (int w = 0; w < 8; w++) {
                    assertEquals(TermiteRampBlock.height(Direction.EAST, shapes[i], u, w), heightAt(model, u, w),
                            models[i] + " ramp at column " + u + ", " + w);
                }
            }
        }
    }

    @Test
    void spireModelsNarrowLikeTheSpireBlock() throws Exception {
        String[] names = {"base", "thick", "middle", "thin", "tip"};
        int[][] widths = {{16, 12}, {12, 10}, {10, 8}, {8, 6}, {6, 0}};
        for (int t = 0; t < names.length; t++) {
            JsonObject model = json("models/block/termite_spire_" + names[t] + "_dry.json");
            for (int v = 0; v < 8; v++) {
                assertEquals(MoundPieceBlock.taper(widths[t][0], widths[t][1], v, 8), widthAt(model, v * 2 + 1), 1e-9,
                        names[t] + " spire piece at layer " + v);
            }
            if (t < names.length - 1) {
                assertEquals(widths[t][1], widths[t + 1][0], "the " + names[t + 1] + " piece does not start where the " + names[t] + " ends");
            }
        }
    }

    @Test
    void everyStateHasAModelWithItsTextures() throws Exception {
        int states = 0;
        for (String block : new String[] {"termite_spire", "termite_mouth", "termite_ramp", "termite_mound"}) {
            JsonObject variants = json("blockstates/" + block + ".json").getAsJsonObject("variants");
            for (String key : variants.keySet()) {
                JsonElement value = variants.get(key);
                for (JsonElement option : value.isJsonArray() ? value.getAsJsonArray() : java.util.List.of(value)) {
                    String model = option.getAsJsonObject().get("model").getAsString().replace("antcolonies:", "");
                    JsonObject file = json("models/" + model + ".json");
                    for (var texture : file.getAsJsonObject("textures").entrySet()) {
                        String path = texture.getValue().getAsString().replace("antcolonies:", "textures/") + ".png";
                        assertTrue(exists(path), model + " uses a missing texture " + path);
                    }
                }
                states++;
            }
        }
        // spire 5 x 4 ages, mouth 2 x 4, ramp 4 facings x 5 shapes x 4 ages, mound 5 layers x 4 ages
        assertEquals(5 * 4 + 2 * 4 + 4 * 5 * 4 + 5 * 4, states);
        for (String age : AGES) {
            assertTrue(exists("textures/block/termite_mouth_" + age + "_top.png"), "no mouth texture for " + age);
        }
    }
}
