package com.antcolonies.savanna.entity.body;

import com.antcolonies.entity.body.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
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
    @BeforeAll
    static void signUp() {
        // as the savanna mod does at start (the split, step S6)
        BodyPlans.signUp(com.antcolonies.savanna.SavannaMod.class, "/assets/antcolonies/body_plans/");
    }

    /** The savanna's models (the worker ant's plan is checked by the desert mod's HarvesterBodyPlansTest, step S7). */
    private static final String[] MODELS = {"termite_worker", "termite_king", "termite_queen",
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
    }

    /**
     * Climbing done once for every body (2026-10-18): the worker climbs exactly as the user approved it in-game, every
     * other body with its own measured numbers, and the lift and push worked out from a body's size give the worker's
     * approved ones for the worker's size.
     */
    @Test
    void everyBodyClimbsWithItsOwnNumbers() {
        BodyPlans.Climb sized = new BodyPlans.Climb(3.6, 2.85, 5.57, 5.57, 3.0, Double.NaN, Double.NaN);
        assertEquals(0.25, sized.liftFor(0.2F, 0.65F), 1e-6);
        assertEquals(0.17, sized.intoWallFor(0.3F, 0.65F), 1e-6);
        for (String model : MODELS) {
            BodyPlans.Plan plan = BodyPlans.of(model);
            assertTrue(!plan.spine().isEmpty(), model + ": no spine");
            assertTrue(plan.climb().hindFeet() != BodyPlans.WORKER_CLIMB.hindFeet(), model + ": the worker's feet");
        }
        BodyPlans.Plan major = BodyPlans.of("termite_major_soldier");
        assertEquals(0.4, major.climb().liftFor(0.3F, 0.75F), 1e-9);
        assertEquals(0.27, major.climb().intoWallFor(0.45F, 0.75F), 1e-9);
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
