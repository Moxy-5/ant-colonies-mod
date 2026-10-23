package com.antcolonies.savanna.entity.body;

import com.antcolonies.entity.body.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    /**
     * Climbing done once for every body (2026-10-18): the worker climbs exactly as the user approved it in-game, every
     * other body with its own measured numbers, and the lift and push worked out from a body's size give the worker's
     * approved ones for the worker's size.
     */
    @Test
    void everyBodyClimbsWithItsOwnNumbers() {
        BodyPlans.Plan worker = BodyPlans.of("worker_ant");
        assertEquals(BodyPlans.WORKER_CLIMB, worker.climb());
        BodyPlans.Piece waist = worker.spine().get(0);
        assertEquals(BodyPlans.WORKER_WAIST.joints(), waist.joints());
        assertArrayEquals(BodyPlans.WORKER_WAIST.shares(), waist.shares());
        assertEquals(BodyPlans.WORKER_WAIST.hinge(), waist.hinge());
        assertEquals(BodyPlans.WORKER_WAIST.from(), waist.from());
        assertEquals(BodyPlans.WORKER_WAIST.to(), waist.to());
        assertEquals(BodyPlans.WORKER_WAIST.maxPitch(), waist.maxPitch());
        assertEquals(BodyPlans.WORKER_WAIST.maxYaw(), waist.maxYaw());
        BodyPlans.Climb sized = new BodyPlans.Climb(3.6, 2.85, 5.57, 5.57, 3.0, Double.NaN, Double.NaN);
        assertEquals(0.25, sized.liftFor(0.2F, 0.65F), 1e-6);
        assertEquals(0.17, sized.intoWallFor(0.3F, 0.65F), 1e-6);
        for (String model : MODELS) {
            BodyPlans.Plan plan = BodyPlans.of(model);
            assertTrue(!plan.spine().isEmpty(), model + ": no spine");
            if (!model.equals("worker_ant")) {
                assertTrue(plan.climb().hindFeet() != BodyPlans.WORKER_CLIMB.hindFeet(), model + ": the worker's feet");
            }
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
