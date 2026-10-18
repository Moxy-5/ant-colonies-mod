package com.antcolonies.entity.body;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The body plans (M3, 2026-10-18): the game reads every model's plan, every bone a plan names is in its model, and the
 * plans give exactly the answers the old bone-name rules gave (the head player's bones, the load's bone, the waist), so
 * nothing in the game changes until a model's bones are renamed.
 */
class BodyPlansTest {
    private static final String[] MODELS = {"worker_ant", "termite_worker", "termite_king", "termite_queen",
            "termite_minor_soldier", "termite_major_soldier"};

    @Test
    void everyModelHasAPlanThatGivesTheOldAnswers() throws Exception {
        for (String model : MODELS) {
            BodyPlans.Plan plan = BodyPlans.of(model);
            assertNotNull(plan, "no body plan for " + model);
            List<String> bones = geoBones(model);
            for (String bone : plan.bones().values()) {
                assertTrue(bones.contains(bone), model + ": the plan names bone " + bone + ", the model has none");
            }
            for (String bone : bones) {
                boolean oldHead = bone.startsWith("head") || bone.startsWith("mandible") || bone.startsWith("antenna")
                        || bone.startsWith("carry");
                assertEquals(oldHead, BodyPlans.isHeadPlayerBone(bone), model + ": " + bone + " changed player");
                assertEquals("carry".equals(bone), BodyPlans.isHoldBone(bone), model + ": " + bone + " as the load's place");
            }
        }
        BodyPlans.Plan worker = BodyPlans.of("worker_ant");
        assertEquals(List.of("petiole", "postpetiole", "abdomen"), worker.waist());
        assertArrayEquals(new float[] {0.40F, 0.35F, 0.25F}, worker.waistShare(), 1e-6F);
        assertEquals("head", worker.bone("head"));
    }

    private static List<String> geoBones(String model) throws Exception {
        try (InputStream in = BodyPlansTest.class.getResourceAsStream("/assets/antcolonies/geo/entity/" + model + ".geo.json")) {
            assertNotNull(in, "no " + model + ".geo.json");
            JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> out = new ArrayList<>();
            for (JsonElement bone : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
                out.add(bone.getAsJsonObject().get("name").getAsString());
            }
            return out;
        }
    }
}
