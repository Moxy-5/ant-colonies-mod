package com.antcolonies.savanna.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.core.BlockPos;

/**
 * The game's sycamore fig is the viewer's (floodplain trees step 4): for a few seeds at every age and dead stage, every
 * block, in the same order, and every fig bunch, bark fig, bracket and seedling, the same as the model viewer makes them
 * ({@code node tools/fig_shape_export.js} writes the viewer's trees into {@code fig_shapes_viewer.json.gz}).
 */
class FigShapeTest {
    @Test
    void figsMatchTheViewerBlockForBlock() throws Exception {
        JsonObject all;
        try (InputStream in = FigShapeTest.class.getResourceAsStream("/fig_shapes_viewer.json.gz")) {
            assertNotNull(in, "fig_shapes_viewer.json.gz missing: run node tools/fig_shape_export.js");
            all = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> problems = new ArrayList<>();
        int trees = 0;
        for (String key : all.keySet()) {
            String[] sa = key.split("/");
            int seed = Integer.parseInt(sa[0]);
            FigShape.Age age = FigShape.Age.byId(sa[1]);
            assertNotNull(age, key);
            FigShape f = FigShape.make(seed, age);
            JsonObject want = all.getAsJsonObject(key);
            compareCells(key, want.getAsJsonArray("cells"), f.cells(), problems);
            compareMarks(key, want.getAsJsonArray("marks"), f.marks(), problems);
            trees++;
        }
        assertTrue(trees >= 40, "only " + trees + " trees in the export");
        assertTrue(problems.isEmpty(), () -> problems.size() + " differences from the viewer, the first:\n  "
                + String.join("\n  ", problems.subList(0, Math.min(25, problems.size()))));
    }

    private static void compareCells(String key, JsonArray want, Long2IntLinkedOpenHashMap got, List<String> problems) {
        int i = 0, shown = 0;
        var it = got.long2IntEntrySet().iterator();
        for (JsonElement e : want) {
            JsonArray c = e.getAsJsonArray();
            String at = c.get(0).getAsInt() + " " + c.get(1).getAsInt() + " " + c.get(2).getAsInt(), name = c.get(3).getAsString();
            if (!it.hasNext()) {
                problems.add(key + ": the game's tree ends at block " + i + " of " + want.size());
                return;
            }
            Long2IntMap.Entry g = it.next();
            BlockPos p = BlockPos.of(g.getLongKey());
            String gotAt = p.getX() + " " + p.getY() + " " + p.getZ(), gotName = FigShape.name(g.getIntValue());
            if ((!at.equals(gotAt) || !name.equals(gotName)) && shown++ < 5) {
                problems.add(key + " block " + i + ": viewer " + at + " " + name + ", game " + gotAt + " " + gotName);
            }
            i++;
        }
        if (it.hasNext()) {
            problems.add(key + ": the game's tree has " + got.size() + " blocks, the viewer's " + want.size());
        }
    }

    private static void compareMarks(String key, JsonArray want, List<FigShape.Mark> got, List<String> problems) {
        if (want.size() != got.size()) {
            problems.add(key + ": " + got.size() + " small blocks in the game, " + want.size() + " in the viewer");
        }
        for (int i = 0; i < Math.min(want.size(), got.size()); i++) {
            JsonArray w = want.get(i).getAsJsonArray();
            FigShape.Mark m = got.get(i);
            String wantS = w.get(0).getAsString() + " " + w.get(1).getAsInt() + " " + w.get(2).getAsInt() + " " + w.get(3).getAsInt() + " "
                    + w.get(4).getAsInt() + " " + w.get(5).getAsInt() + " " + w.get(6).getAsBoolean();
            String gotS = m.kind().name().toLowerCase(java.util.Locale.ROOT) + " " + m.x() + " " + m.y() + " " + m.z() + " " + m.dx() + " "
                    + m.dz() + " " + m.big();
            if (!wantS.equals(gotS)) {
                problems.add(key + " small block " + i + ": viewer " + wantS + ", game " + gotS);
                return;
            }
        }
    }
}
