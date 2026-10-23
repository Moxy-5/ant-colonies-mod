package com.antcolonies.desert.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;

/**
 * The worker ant's model and animation files the game window loads (the game tests never load them), moved out of the
 * savanna's ModelFilesTest with the ant (the split, step S7): its model has the empty "carry" bone in its head, where
 * PelletLayer draws a carried load, and its animations read the way GeckoLib reads them and only move bones it has.
 */
class HarvesterModelFilesTest {
    private static final String ASSETS = "/assets/desertcolonies/";

    private static JsonObject json(String path) throws Exception {
        try (InputStream in = HarvesterModelFilesTest.class.getResourceAsStream(ASSETS + path)) {
            assertNotNull(in, "no file " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Bone name to parent name ("" for a bone without a parent). */
    private static Map<String, String> bones(String model) throws Exception {
        Map<String, String> bones = new HashMap<>();
        JsonObject geometry = json("geo/entity/" + model + ".geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            bones.put(bone.get("name").getAsString(), bone.has("parent") ? bone.get("parent").getAsString() : "");
        }
        return bones;
    }

    @Test
    void theAntHoldsItsLoadInItsHead() throws Exception {
        assertEquals("head", bones("worker_ant").get("carry"), "worker_ant has no carry bone in its head");
    }

    @Test
    void theAntAnimationsReadAndMoveOnlyBonesTheAntHas() throws Exception {
        BakedAnimations animations = KeyFramesAdapter.GEO_GSON.fromJson(
                json("animations/entity/worker_ant.animation.json").getAsJsonObject("animations"), BakedAnimations.class);
        Map<String, String> bones = bones("worker_ant");
        for (String name : new String[] {"idle", "walk"}) {
            Animation animation = animations.getAnimation(name);
            assertNotNull(animation, "no " + name + " animation");
            for (var bone : animation.boneAnimations()) {
                assertTrue(bones.containsKey(bone.boneName()), name + " moves a bone the ant does not have: " + bone.boneName());
            }
        }
    }
}
