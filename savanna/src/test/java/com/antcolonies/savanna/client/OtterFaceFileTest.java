package com.antcolonies.savanna.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.antcolonies.client.FaceLayer;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.OtterEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The otter's face file (2026-11-22, {@code python tools/otter_models.py game}) is read as the game reads it, has a face
 * for every mood the otter can be in, and a standing clip for every mood but calm in its clip file (the model plays
 * {@code stand_<mood>} when it stands still in a mood).
 */
class OtterFaceFileTest {
    @Test
    void everyMoodHasAFaceAndAStance() throws Exception {
        FaceLayer face = FaceLayer.of(SavannaMod.class, SavannaMod.MODID, "otter");
        JsonObject clips;
        try (InputStream in = SavannaMod.class.getResourceAsStream("/assets/antcolonies/animations/entity/otter.animation.json")) {
            clips = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
        }
        for (String mood : OtterEntity.MOODS) {
            assertTrue(face.has(mood), "no face for " + mood);
            if (!mood.equals("calm")) {
                assertTrue(clips.has("stand_" + mood), "no standing clip for " + mood);
            }
        }
    }
}
