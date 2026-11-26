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

    /**
     * The gait table (the Motion Studio, phase 2, step 2, 2026-11-25): the lion's and buffalo's gaits come from their plans
     * with the very numbers the game kept in Java before (each clip's speed, where the trot and the gallop or charge start,
     * the play-speed limits), so moving them changed nothing in play; the lioness's and cub's stalk speeds are the plans'
     * measured ones (the Java copies were 0.70 and 0.53 blocks a second).
     */
    @Test
    void theLionsAndBuffalosGaitsAreTheOnesTheGameUsedBefore() {
        double tick = 1.0 / 20.0;
        // model: walk, trot, gallop clip speeds (blocks a second at size 1), as LionEntity.Measures had them
        Object[][] lions = {{"lion", 1.228, 2.860, 6.105}, {"lioness", 1.201, 2.828, 5.938}, {"lion_cub", 0.909, 2.022, 3.464}};
        for (Object[] row : lions) {
            Gaits g = BodyPlans.gaitsOf((String) row[0]);
            double walk = (double) row[1] * tick, trot = (double) row[2] * tick, gallop = (double) row[3] * tick;
            assertEquals(walk, g.of("walk").madeFor(), 0.002 * tick, row[0] + " walk");
            assertEquals(trot, g.of("trot").madeFor(), 0.002 * tick, row[0] + " trot");
            assertEquals(gallop, g.of("gallop").madeFor(), 0.002 * tick, row[0] + " gallop");
            assertEquals(walk * 1.6, g.of("trot").from(), 0.004 * tick, row[0] + ": the trot from 1.6 x the walk");
            assertEquals(trot * 1.45, g.of("gallop").from(), 0.004 * tick, row[0] + ": the gallop from 1.45 x the trot");
            assertEquals("walk", g.pick(walk).clip());
            assertEquals("trot", g.pick(walk * 1.7).clip());
            assertEquals("gallop", g.pick(trot * 1.5).clip());
            assertEquals(0.4, g.of("walk").rate(0.0), 1e-9);
            assertEquals(2.0, g.of("walk").rate(walk * 3), 1e-9);
            assertEquals(1.8, g.of("trot").rate(trot * 3), 1e-9);
            assertEquals(0.6, g.of("gallop").rate(0.0), 1e-9);
            assertEquals(1.0, g.of("stalk").rate(g.of("stalk").madeFor()), 1e-9);
            assertEquals(3, g.order().size(), row[0] + ": walk, trot, gallop picked by speed; the stalk only by name");
        }
        Gaits buffalo = BodyPlans.gaitsOf("buffalo");
        assertEquals(0.6076 * tick, buffalo.of("walk").madeFor(), 0.001 * tick);
        assertEquals(1.389 * tick, buffalo.of("trot").madeFor(), 0.001 * tick);
        assertEquals(2.447 * tick, buffalo.of("charge").madeFor(), 0.001 * tick);
        assertEquals(1.25 * tick, buffalo.of("trot").from(), 1e-9);
        assertEquals(2.6 * tick, buffalo.of("charge").from(), 1e-9);
        assertEquals(0.7, buffalo.of("trot").rate(0.0), 1e-9);
        assertEquals(2.5, buffalo.of("charge").rate(1.0), 1e-9);
        assertEquals("charge", buffalo.pick(2.7 * tick).clip());
        assertEquals(buffalo.of("walk").loop(), buffalo.of("walk").loop(), "a gait's loop is the same loop every time");
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
