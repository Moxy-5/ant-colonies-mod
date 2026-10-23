package com.antcolonies.savanna.client;

import com.antcolonies.client.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.antcolonies.nav.DoorwayMotion;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;

/**
 * The termites' animation file reads the way GeckoLib reads it in the game (the game tests never load it: it is only
 * used by the game window), and its look round out of a chimney lasts exactly as long as the termite holds still for it.
 */
class TermiteAnimationsTest {
    private static BakedAnimations read(String name) throws Exception {
        try (InputStream in = TermiteAnimationsTest.class.getResourceAsStream("/assets/antcolonies/animations/entity/" + name)) {
            assertNotNull(in, "no animation file " + name);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return KeyFramesAdapter.GEO_GSON.fromJson(json.getAsJsonObject("animations"), BakedAnimations.class);
        }
    }

    @Test
    void theTermiteAnimationsReadAndThePeekIsAsLongAsTheTermiteHoldsStill() throws Exception {
        BakedAnimations animations = read("termite_worker.animation.json");
        for (String name : new String[] {"idle", "walk", "peek"}) {
            assertNotNull(animations.getAnimation(name), "no " + name + " animation");
        }
        Animation peek = animations.getAnimation("peek");
        assertEquals(DoorwayMotion.PEEK_TICKS, peek.length(), 0.01, "the look round is " + peek.length() + " ticks long");
        // The head turns with easing (it slows into each stop), not at a steady rate.
        var head = Arrays.stream(peek.boneAnimations()).filter(bone -> bone.boneName().equals("head")).findFirst().orElseThrow();
        assertTrue(head.rotationKeyFrames().yKeyframes().stream().anyMatch(frame -> frame.easingType() == EasingType.EASE_IN_OUT_SINE),
                "the head's turns are not eased");
    }
}
