package com.antcolonies.desert.entity.body;

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

import com.antcolonies.desert.DesertMod;
import com.antcolonies.entity.body.BodyPlans;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The worker ant's body plan (M3, 2026-10-18), moved out of the savanna's BodyPlansTest with the ant (the split, step
 * S7): the game reads it, every bone it names is in the model, it gives exactly the answers the old bone-name rules
 * gave, and the worker climbs exactly as the user approved it in-game.
 */
class HarvesterBodyPlansTest {
    @BeforeAll
    static void signUp() {
        // as the desert mod does at start
        BodyPlans.signUp(DesertMod.class, "/assets/" + DesertMod.MODID + "/body_plans/");
    }

    @Test
    void theWorkerHasAPlanThatGivesTheOldAnswers() throws Exception {
        BodyPlans.Plan worker = BodyPlans.of("worker_ant");
        assertNotNull(worker, "no body plan for worker_ant");
        List<String> bones = geoBones("worker_ant");
        for (String bone : worker.bones().values()) {
            assertTrue(bones.contains(bone), "worker_ant: the plan names bone " + bone + ", the model has none");
        }
        for (String bone : bones) {
            boolean oldHead = bone.startsWith("head") || bone.startsWith("mandible") || bone.startsWith("antenna")
                    || bone.startsWith("carry");
            assertEquals(oldHead, BodyPlans.isHeadPlayerBone(bone), "worker_ant: " + bone + " changed player");
            assertEquals("carry".equals(bone), BodyPlans.isHoldBone(bone), "worker_ant: " + bone + " as the load's place");
        }
        assertEquals(List.of("petiole", "postpetiole", "abdomen"), worker.waist());
        assertArrayEquals(new float[] {0.40F, 0.35F, 0.25F}, worker.waistShare(), 1e-6F);
        assertEquals("head", worker.bone("head"));
    }

    @Test
    void theWorkerClimbsAsApproved() {
        BodyPlans.Plan worker = BodyPlans.of("worker_ant");
        assertEquals(BodyPlans.WORKER_CLIMB, worker.climb());
        assertTrue(!worker.spine().isEmpty(), "worker_ant: no spine");
        BodyPlans.Piece waist = worker.spine().get(0);
        assertEquals(BodyPlans.WORKER_WAIST.joints(), waist.joints());
        assertArrayEquals(BodyPlans.WORKER_WAIST.shares(), waist.shares());
        assertEquals(BodyPlans.WORKER_WAIST.hinge(), waist.hinge());
        assertEquals(BodyPlans.WORKER_WAIST.from(), waist.from());
        assertEquals(BodyPlans.WORKER_WAIST.to(), waist.to());
        assertEquals(BodyPlans.WORKER_WAIST.maxPitch(), waist.maxPitch());
        assertEquals(BodyPlans.WORKER_WAIST.maxYaw(), waist.maxYaw());
    }

    private static List<String> geoBones(String model) throws Exception {
        try (InputStream in = HarvesterBodyPlansTest.class.getResourceAsStream("/assets/" + DesertMod.MODID + "/geo/entity/" + model + ".geo.json")) {
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
