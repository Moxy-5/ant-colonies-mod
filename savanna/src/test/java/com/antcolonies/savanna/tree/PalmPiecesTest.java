package com.antcolonies.savanna.tree;

import com.antcolonies.savanna.block.HangingFruit;

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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The game's doum palm pieces are the viewer's (floodplain trees step 5): every crown kind the palm grows, the seedling
 * and the rosette, for a few seeds, box for box, in the same order, the same size, place, turn and colour, and the same
 * fruit bunch ({@code node tools/doum_pieces_export.js} writes the viewer's into {@code doum_pieces_viewer.json.gz}).
 */
class PalmPiecesTest {
    private static final double CLOSE = 1e-9;

    @Test
    void doumPiecesMatchTheViewerBoxForBox() throws Exception {
        JsonObject all;
        try (InputStream in = PalmPiecesTest.class.getResourceAsStream("/doum_pieces_viewer.json.gz")) {
            assertNotNull(in, "doum_pieces_viewer.json.gz missing: run node tools/doum_pieces_export.js");
            all = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> problems = new ArrayList<>();
        int pieces = 0;
        for (String key : all.keySet()) {
            String[] k = key.split("/");
            List<PalmPieces.Bit> got = switch (k[0]) {
                case "crown" -> crown(k[1], Integer.parseInt(k[2]));
                case "seedling" -> PalmPieces.seedling(Integer.parseInt(k[1]));
                case "rosette" -> PalmPieces.rosette(Integer.parseInt(k[2]), Double.parseDouble(k[1]));
                default -> throw new IllegalStateException(key);
            };
            compare(key, all.getAsJsonArray(key), got, problems);
            pieces++;
        }
        assertTrue(pieces >= 50, "only " + pieces + " pieces in the export");
        assertTrue(problems.isEmpty(), () -> problems.size() + " differences from the viewer, the first:\n  "
                + String.join("\n  ", problems.subList(0, Math.min(25, problems.size()))));
    }

    /** The crowns the export draws (tools/doum_pieces_export.js, CROWNS). */
    private static List<PalmPieces.Bit> crown(String name, int seed) {
        return switch (name) {
            case "grown" -> PalmPieces.crown(seed, 3.5, 14, -2.5, 1, true, false, -1, HangingFruit.RIPE);
            case "old" -> PalmPieces.crown(seed, -6.5, 17, 4.5, 1.06, true, false, -1, HangingFruit.RIPE);
            case "stem" -> PalmPieces.crown(seed, 0.5, 9, 0.5, 0.88, true, false, 0, HangingFruit.RIPE);
            case "fork1" -> PalmPieces.crown(seed, 5.5, 12, 7.5, 0.95, true, false, 1, HangingFruit.RIPE);
            case "fork0" -> PalmPieces.crown(seed, -2.5, 13, -9.5, 0.95, true, false, 0, HangingFruit.RIPE);
            case "dead" -> PalmPieces.crown(seed, 1.5, 15, 2.5, 1, true, true, -1, HangingFruit.RIPE);
            case "young" -> PalmPieces.crown(seed, 0.5, 6, 0.5, 1, false, false, -1, HangingFruit.RIPE);
            default -> throw new IllegalStateException(name);
        };
    }

    private static void compare(String key, JsonArray want, List<PalmPieces.Bit> got, List<String> problems) {
        if (want.size() != got.size()) {
            problems.add(key + ": " + got.size() + " boxes in the game, " + want.size() + " in the viewer");
        }
        for (int i = 0; i < Math.min(want.size(), got.size()); i++) {
            JsonArray w = want.get(i).getAsJsonArray();
            PalmPieces.Bit b = got.get(i);
            String skin = w.get(7).getAsString().isEmpty() ? "PLAIN" : "CAP";
            boolean same = near(w.get(0).getAsDouble(), b.x()) && near(w.get(1).getAsDouble(), b.y()) && near(w.get(2).getAsDouble(), b.z())
                    && w.get(3).getAsInt() == b.w() && w.get(4).getAsInt() == b.h() && w.get(5).getAsInt() == b.d()
                    && near(w.get(6).getAsDouble(), b.rot()) && skin.equals(b.skin().name())
                    && near(w.get(8).getAsDouble(), b.r()) && near(w.get(9).getAsDouble(), b.g()) && near(w.get(10).getAsDouble(), b.b())
                    && w.get(11).getAsBoolean() == b.leafy() && w.get(12).getAsInt() == b.bunch();
            if (!same) {
                problems.add(key + " box " + i + ": viewer " + w + ", game " + b);
                return;
            }
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= CLOSE * Math.max(1, Math.abs(a));
    }
}
