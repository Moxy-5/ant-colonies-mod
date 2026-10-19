package com.antcolonies.tree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;

/**
 * Writes the game's tree shapes to build/tree_shapes.json, for the tree viewer's "game shapes" mode
 * (python tools/model_viewer.py trees): a few seeds of every kind at the viewer's ages and stages. Each tree is its
 * blocks as 4 bytes (x, y, z, part), base64.
 */
class TreeShapeExport {
    static final String[][] KINDS = {{"umbrella", "umbrella_thorn"}, {"camel", "camel_thorn"}, {"baobab", "baobab"}};
    static final double[] AGES = {0.03, 0.15, 0.3, 0.45, 0.6, 0.8, 1, 1.25};
    static final double[] DEAD_AGES = {0.6, 1, 1.25};
    static final int SEEDS = 4;

    @Test
    void writeShapesForTheViewer() throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        String[] parts = new String[TreePart.values().length];
        for (TreePart p : TreePart.values()) {
            parts[p.ordinal()] = p.id;
        }
        out.put("parts", parts);
        out.put("ages", AGES);
        out.put("deadAges", DEAD_AGES);
        out.put("seeds", SEEDS);
        Map<String, String> trees = new LinkedHashMap<>();
        Map<String, String> shapes = new LinkedHashMap<>();
        for (String[] k : KINDS) {
            TreeKind kind = TreeKind.load(k[1]);
            for (int seed = 1; seed <= SEEDS; seed++) {
                shapes.put(k[0] + "|" + seed, kind.shapeFor(seed).name());
                for (TreeStage stage : TreeShapes.stages(kind)) {
                    double[] ages = stage == TreeStage.LIVING ? AGES : DEAD_AGES;
                    for (double age : ages) {
                        trees.put(k[0] + "|" + seed + "|" + num(age) + "|" + stage.id, encode(TreeShapes.build(kind, seed, age, stage)));
                        if (kind.isBaobab() && stage == TreeStage.LIVING) {
                            trees.put(k[0] + "|" + seed + "|" + num(age) + "|living-dry", encode(TreeShapes.build(kind, seed, age, stage, true)));
                        }
                    }
                }
            }
        }
        out.put("shapes", shapes);
        out.put("trees", trees);
        Gson gson = new GsonBuilder().create();
        // The tests run in build/minecraft-junit: walk up to the project folder.
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        Path file = (root == null ? Path.of("") : root).resolve("build").resolve("tree_shapes.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, gson.toJson(out), StandardCharsets.UTF_8);
        System.out.println("wrote " + file.toAbsolutePath() + " (" + Files.size(file) / 1024 + " KB, " + trees.size() + " trees)");
    }

    /** A number written the way the viewer's JavaScript writes it (1, not 1.0). */
    private static String num(double x) {
        return x == Math.rint(x) ? String.valueOf((long) x) : String.valueOf(x);
    }

    private static String encode(TreeVoxels v) {
        byte[] bytes = new byte[v.size() * 4];
        int i = 0;
        for (Long2ObjectMap.Entry<TreePart> e : v.entries()) {
            long k = e.getLongKey();
            bytes[i++] = (byte) BlockPos.getX(k);
            bytes[i++] = (byte) BlockPos.getY(k);
            bytes[i++] = (byte) BlockPos.getZ(k);
            bytes[i++] = (byte) e.getValue().ordinal();
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}
