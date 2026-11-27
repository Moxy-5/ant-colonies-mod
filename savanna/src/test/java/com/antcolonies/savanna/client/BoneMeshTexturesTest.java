package com.antcolonies.savanna.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Every model's own pictures are solid on every box face (code review R4-18, 2026-11-26). Every insect and animal is
 * drawn through {@code BoneMesh}, which leaves out a face buried inside another box of the same bone; that is only
 * right while no box has see-through pixels on a face, or the hole would show nothing behind it. A picture belongs to
 * the model whose name is the longest start of its own ({@code lioness_elder} to {@code lioness},
 * {@code crocodile_hatchling} to {@code crocodile}). Mud coats are drawn over the solid picture and may have holes; the
 * fly is not drawn through {@code BoneMesh}.
 */
class BoneMeshTexturesTest {
    /** Alpha below this is thrown away by the entity cutout drawing (a tenth). */
    private static final int SEE_THROUGH = 26;
    /** Models not drawn through BoneMesh. */
    private static final Set<String> NOT_MESHED = Set.of("fly");

    /** The mod's own folder of model or picture files (the tests run in {@code build/minecraft-junit}; the files on the class path may sit in a jar). */
    private static File folder(String inAssets, String aFile) {
        for (java.nio.file.Path at = java.nio.file.Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
            File folder = at.resolve("src/main/resources/assets/antcolonies").resolve(inAssets).toFile();
            if (new File(folder, aFile).isFile()) {
                return folder;
            }
        }
        assertNotNull(null, "no " + inAssets + "/" + aFile + " above " + java.nio.file.Path.of("").toAbsolutePath());
        return null;
    }

    /** A box's faces as {u, v, width, height} on the model's texture sheet. */
    private static List<double[]> faces(JsonObject cube) {
        List<double[]> faces = new ArrayList<>();
        JsonElement uv = cube.get("uv");
        if (uv == null) {
            return faces;
        }
        if (uv.isJsonObject()) {
            for (Map.Entry<String, JsonElement> side : uv.getAsJsonObject().entrySet()) {
                JsonObject face = side.getValue().getAsJsonObject();
                JsonArray at = face.getAsJsonArray("uv");
                JsonArray size = face.has("uv_size") ? face.getAsJsonArray("uv_size") : null;
                double w = size == null ? 0 : size.get(0).getAsDouble(), h = size == null ? 0 : size.get(1).getAsDouble();
                double u = at.get(0).getAsDouble(), v = at.get(1).getAsDouble();
                faces.add(new double[] {Math.min(u, u + w), Math.min(v, v + h), Math.abs(w), Math.abs(h)});
            }
            return faces;
        }
        // box UV: the six faces unfolded from the corner (u, v), by the box's whole-number sizes
        JsonArray at = uv.getAsJsonArray(), size = cube.getAsJsonArray("size");
        double u = at.get(0).getAsDouble(), v = at.get(1).getAsDouble();
        double w = Math.floor(size.get(0).getAsDouble()), h = Math.floor(size.get(1).getAsDouble()), d = Math.floor(size.get(2).getAsDouble());
        faces.add(new double[] {u + d, v, w, d});
        faces.add(new double[] {u + d + w, v, w, d});
        faces.add(new double[] {u, v + d, d, h});
        faces.add(new double[] {u + d, v + d, w, h});
        faces.add(new double[] {u + d + w, v + d, d, h});
        faces.add(new double[] {u + d + w + d, v + d, w, h});
        return faces;
    }

    @Test
    void everyModelsOwnPicturesAreSolidOnEveryBoxFace() throws Exception {
        File geos = folder("geo/entity", "tilapia.geo.json");
        File pictures = folder("textures/entity", "tilapia.png");
        List<String> models = new ArrayList<>();
        for (File geo : geos.listFiles((dir, name) -> name.endsWith(".geo.json"))) {
            models.add(geo.getName().substring(0, geo.getName().length() - ".geo.json".length()));
        }
        models.sort(Comparator.comparingInt(String::length).reversed());   // the longest start wins
        int checked = 0;
        List<String> faults = new ArrayList<>();
        File[] files = pictures.listFiles((dir, name) -> name.endsWith(".png") && !name.contains("_mud_"));
        Arrays.sort(files);
        for (File picture : files) {
            String name = picture.getName().substring(0, picture.getName().length() - 4);
            String model = models.stream().filter(m -> name.equals(m) || name.startsWith(m + "_")).findFirst().orElse(null);
            if (model == null || NOT_MESHED.contains(model)) {
                continue;
            }
            JsonObject geometry;
            try (FileReader in = new FileReader(new File(geos, model + ".geo.json"), StandardCharsets.UTF_8)) {
                geometry = JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            }
            JsonObject about = geometry.getAsJsonObject("description");
            double sheetW = about.get("texture_width").getAsDouble(), sheetH = about.get("texture_height").getAsDouble();
            BufferedImage image = ImageIO.read(picture);
            double sx = image.getWidth() / sheetW, sy = image.getHeight() / sheetH;
            int holes = 0;
            String first = null;
            for (JsonElement b : geometry.getAsJsonArray("bones")) {
                JsonObject bone = b.getAsJsonObject();
                if (!bone.has("cubes")) {
                    continue;
                }
                for (JsonElement c : bone.getAsJsonArray("cubes")) {
                    for (double[] face : faces(c.getAsJsonObject())) {
                        if (face[2] == 0 || face[3] == 0) {
                            continue;
                        }
                        int x1 = Math.min(image.getWidth(), (int) Math.ceil((face[0] + face[2]) * sx));
                        int y1 = Math.min(image.getHeight(), (int) Math.ceil((face[1] + face[3]) * sy));
                        for (int y = Math.max(0, (int) Math.floor(face[1] * sy)); y < y1; y++) {
                            for (int x = Math.max(0, (int) Math.floor(face[0] * sx)); x < x1; x++) {
                                if ((image.getRGB(x, y) >>> 24) < SEE_THROUGH) {
                                    holes++;
                                    if (first == null) {
                                        first = "bone " + bone.get("name").getAsString() + " at pixel " + x + ", " + y;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            checked++;
            if (holes > 0) {
                faults.add(picture.getName() + " on " + model + ": " + holes + " see-through pixels on box faces, the first on " + first);
            }
        }
        assertTrue(checked >= 60, "only " + checked + " pictures found their model: the folders were not read right");
        assertTrue(faults.isEmpty(), "BoneMesh leaves out faces hidden inside another box, so a model's picture must be solid on"
                + " every box face (paint the pixels, or keep the model off BoneMesh): " + faults);
    }
}
