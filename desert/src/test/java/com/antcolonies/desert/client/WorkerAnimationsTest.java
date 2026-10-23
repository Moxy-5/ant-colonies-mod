package com.antcolonies.desert.client;

import com.antcolonies.client.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.antcolonies.entity.AbstractAntEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;

/**
 * The worker ant's animation file (made by tools/ant_models.py) reads the way GeckoLib reads it in the game, and each
 * piece of body work lasts exactly as long as the insect holds still for it (the game tests never load this file).
 */
class WorkerAnimationsTest {
    @Test
    void theCleaningClipsAreAsLongAsTheAntHoldsStill() throws Exception {
        BakedAnimations animations;
        try (InputStream in = WorkerAnimationsTest.class.getResourceAsStream("/assets/desertcolonies/animations/entity/worker_ant.animation.json")) {
            assertNotNull(in, "no worker_ant.animation.json");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            animations = KeyFramesAdapter.GEO_GSON.fromJson(json.getAsJsonObject("animations"), BakedAnimations.class);
        }
        for (String name : new String[] {"idle", "walk", "sleep", "sleep_twitch", "idle_hot", "walk_hot", "look", "look_wary", "dig", "carry"}) {
            assertNotNull(animations.getAnimation(name), "no " + name + " animation");
        }
        check(animations, "groom_antenna_l", AbstractAntEntity.GROOM_ONE_TICKS);
        check(animations, "groom_antenna_r", AbstractAntEntity.GROOM_ONE_TICKS);
        check(animations, "groom_antenna_both", AbstractAntEntity.GROOM_BOTH_TICKS);
    }

    private static void check(BakedAnimations animations, String name, int ticks) {
        Animation clip = animations.getAnimation(name);
        assertNotNull(clip, "no " + name + " animation");
        assertEquals(ticks, clip.length(), 0.01, name + " is " + clip.length() + " ticks long");
    }
}
