package com.antcolonies.savanna.tree;

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
 * The game's doum palm is the viewer's (floodplain trees step 6): for a few seeds at every age and dead stage, every
 * block in the same order, every crown, fruit bunch, frond on the ground, sucker and seedling with the same dice and the
 * same number of sculpted boxes, and every leaf-base stub's two boxes, the same as the model viewer makes them
 * ({@code node tools/doum_shape_export.js} writes the viewer's palms into {@code doum_shapes_viewer.json.gz}).
 */
class DoumShapeTest {
    private static final double EPS = 1e-9;

    @Test
    void doumsMatchTheViewerBlockForBlock() throws Exception {
        JsonObject all;
        try (InputStream in = DoumShapeTest.class.getResourceAsStream("/doum_shapes_viewer.json.gz")) {
            assertNotNull(in, "doum_shapes_viewer.json.gz missing: run node tools/doum_shape_export.js");
            all = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> problems = new ArrayList<>();
        int palms = 0;
        for (String key : all.keySet()) {
            String[] sa = key.split("/");
            DoumShape.Age age = DoumShape.Age.byId(sa[1]);
            assertNotNull(age, key);
            DoumShape d = DoumShape.make(Integer.parseInt(sa[0]), age);
            JsonObject want = all.getAsJsonObject(key);
            compareCells(key, want.getAsJsonArray("cells"), d.cells(), problems);
            compareDraws(key, want.getAsJsonArray("draws"), d.draws(), problems);
            compareStubs(key, want.getAsJsonArray("stubs"), d.stubs(), problems);
            palms++;
        }
        assertTrue(palms >= 45, "only " + palms + " palms in the export");
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
                problems.add(key + ": the game's palm ends at block " + i + " of " + want.size());
                return;
            }
            Long2IntMap.Entry g = it.next();
            BlockPos p = BlockPos.of(g.getLongKey());
            String gotAt = p.getX() + " " + p.getY() + " " + p.getZ(), gotName = DoumShape.name(g.getIntValue());
            if ((!at.equals(gotAt) || !name.equals(gotName)) && shown++ < 5) {
                problems.add(key + " block " + i + ": viewer " + at + " " + name + ", game " + gotAt + " " + gotName);
            }
            i++;
        }
        if (it.hasNext()) {
            problems.add(key + ": the game's palm has " + got.size() + " blocks, the viewer's " + want.size());
        }
    }

    private static boolean near(double a, JsonElement b) {
        return Math.abs(a - b.getAsDouble()) < EPS;
    }

    private static void compareDraws(String key, JsonArray want, List<DoumShape.Draw> got, List<String> problems) {
        if (want.size() != got.size()) {
            problems.add(key + ": the viewer draws " + want.size() + " sculpted pieces, the game " + got.size());
            return;
        }
        for (int i = 0; i < want.size(); i++) {
            JsonArray w = want.get(i).getAsJsonArray();
            DoumShape.Draw g = got.get(i);
            String kind = w.get(0).getAsString();
            boolean same = switch (kind) {
                case "crown" -> g.kind() == DoumShape.DrawKind.CROWN && g.state() == w.get(1).getAsInt() && near(g.tx(), w.get(2))
                        && near(g.ty(), w.get(3)) && near(g.tz(), w.get(4)) && near(g.size(), w.get(5)) && g.old() == w.get(6).getAsBoolean()
                        && g.dead() == w.get(7).getAsBoolean() && g.bunches() == w.get(8).getAsInt() && g.bits() == w.get(9).getAsInt()
                        && sameAt(g.at(), w.get(10).getAsJsonArray());
                case "frond" -> g.kind() == DoumShape.DrawKind.FROND && g.state() == w.get(1).getAsInt() && near(g.tx(), w.get(2))
                        && g.old() == w.get(3).getAsBoolean() && g.bits() == w.get(4).getAsInt() && near(g.at().get(0)[0], w.get(5))
                        && near(g.at().get(0)[2], w.get(6));
                case "rosette" -> g.kind() == DoumShape.DrawKind.ROSETTE && g.state() == w.get(1).getAsInt() && near(g.size(), w.get(2))
                        && near(g.tx(), w.get(3)) && near(g.tz(), w.get(4)) && g.bits() == w.get(5).getAsInt();
                case "seedling" -> g.kind() == DoumShape.DrawKind.SEEDLING && g.state() == w.get(1).getAsInt() && g.bits() == w.get(2).getAsInt();
                default -> false;
            };
            if (!same) {
                problems.add(key + " piece " + i + ": viewer " + w + ", game " + g.kind() + " dice " + g.state() + " t " + g.tx() + " " + g.ty()
                        + " " + g.tz() + " size " + g.size() + " old " + g.old() + " dead " + g.dead() + " bunches " + g.bunches()
                        + " boxes " + g.bits() + " at " + g.at().size());
            }
        }
    }

    private static boolean sameAt(List<double[]> got, JsonArray want) {
        if (got.size() != want.size()) {
            return false;
        }
        for (int j = 0; j < got.size(); j++) {
            JsonArray w = want.get(j).getAsJsonArray();
            for (int k = 0; k < 3; k++) {
                if (!near(got.get(j)[k], w.get(k))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void compareStubs(String key, JsonArray want, List<DoumShape.Stub> got, List<String> problems) {
        if (want.size() != got.size() * 2) {
            problems.add(key + ": the viewer has " + want.size() + " leaf-base boxes, the game " + got.size() * 2);
            return;
        }
        int shown = 0;
        for (int i = 0; i < got.size(); i++) {
            DoumShape.Stub s = got.get(i);
            double[][] boxes = s.viewerBoxes();
            for (int b = 0; b < 2; b++) {
                JsonArray w = want.get(i * 2 + b).getAsJsonArray();
                double[] g = boxes[b];
                boolean same = true;
                for (int k = 0; k < 7; k++) {
                    same &= near(g[k], w.get(k));
                }
                same &= near(g[7], w.get(8)) && w.get(7).getAsString().equals(s.dead() ? "bark_doumdead" : "bark_doum");
                if (!same && shown++ < 5) {
                    problems.add(key + " leaf base " + i + " box " + b + ": viewer " + w + ", game " + java.util.Arrays.toString(g) + " dead " + s.dead());
                }
            }
        }
    }
}
