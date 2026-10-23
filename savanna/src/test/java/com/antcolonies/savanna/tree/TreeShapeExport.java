package com.antcolonies.savanna.tree;

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
        String[] looks = new String[TreeDraw.LimbLook.values().length];
        for (TreeDraw.LimbLook look : TreeDraw.LimbLook.values()) {
            looks[look.ordinal()] = look.name();
        }
        out.put("limbLooks", looks);
        try {
            for (boolean early : new boolean[] {false, true}) {
                TreeDraw.earlyGrowth = early;
                for (TreeDraw.LimbLook look : TreeDraw.LimbLook.values()) {
                    TreeDraw.look = look;
                    // Look A (today's) keeps the old keys; the others end in "|look"; the new growth adds "|g2". Each
                    // only where it differs from what the viewer falls back to (look A of the same growth, else today's).
                    String suffix = (look == TreeDraw.LimbLook.A ? "" : "|" + look.name()) + (early ? "|g2" : "");
                    addTrees(trees, shapes, suffix, early && look != TreeDraw.LimbLook.A ? "|g2" : "");
                }
            }
        } finally {
            TreeDraw.look = TreeDraw.LimbLook.A;
            TreeDraw.earlyGrowth = false;
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

    private static void addTrees(Map<String, String> trees, Map<String, String> shapes, String suffix, String fallback) {
        for (String[] k : KINDS) {
            TreeKind kind = TreeKind.load(k[1]);
            if (kind.isUmbrella() && !suffix.isEmpty()) {
                continue; // its own shape has no limb looks or growths to pick
            }
            for (int seed = 1; seed <= SEEDS; seed++) {
                shapes.put(k[0] + "|" + seed, kind.shapeFor(seed).name());
                for (TreeStage stage : TreeShapes.stages(kind)) {
                    double[] ages = stage == TreeStage.LIVING ? AGES : DEAD_AGES;
                    for (double age : ages) {
                        String key = k[0] + "|" + seed + "|" + num(age) + "|";
                        put(trees, key + stage.id, suffix, fallback, encode(TreeShapes.build(kind, seed, age, stage)));
                        if (kind.isBaobab() && stage == TreeStage.LIVING) {
                            put(trees, key + "living-dry", suffix, fallback, encode(TreeShapes.build(kind, seed, age, stage, true)));
                        }
                    }
                }
            }
        }
    }

    /** Keeps a tree only where it differs from the one the viewer falls back to (key + fallback, else key). */
    private static void put(Map<String, String> trees, String key, String suffix, String fallback, String tree) {
        String back = trees.getOrDefault(key + fallback, trees.get(key));
        if (suffix.isEmpty() || !tree.equals(back)) {
            trees.put(key + suffix, tree);
        }
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
