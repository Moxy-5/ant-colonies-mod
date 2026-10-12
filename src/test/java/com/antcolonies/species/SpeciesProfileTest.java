package com.antcolonies.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

/** Checks that the species profile files read without errors and hold sensible numbers. */
class SpeciesProfileTest {
    private static SpeciesProfile read(String name) throws Exception {
        try (InputStream in = SpeciesProfileTest.class.getResourceAsStream("/data/antcolonies/species/" + name + ".json")) {
            assertNotNull(in, "no file data/antcolonies/species/" + name + ".json");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return SpeciesProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new AssertionError(name + ": " + error));
        }
    }

    @Test
    void theRedHarvesterAntFileReadsAndItsNumbersMakeSense() throws Exception {
        SpeciesProfile profile = read("red_harvester_ant");
        assertTrue(profile.colony().maxWorkers() > 0);
        assertTrue(profile.queen().meal() > 0 && profile.queen().richMeal() > 0 && profile.brood().larvaMeal() > 0);
        assertTrue(profile.worker().mealIntervalTicks() < profile.worker().slowAfterTicks()
                && profile.worker().slowAfterTicks() < profile.worker().starveAfterTicks(), "a worker must get hungry before it slows down, and slow down before it starves");
        assertTrue(profile.food().radius() > 0 && profile.food().plants().get(0).pieces() > 0 && profile.food().makesBread());
        assertEquals(4, profile.nest().maxLevels());
        assertTrue(profile.queenBody().isEmpty(), "the ant queen does not swell");
        assertTrue(profile.founding().isPresent() && profile.founding().get().eggs() == 3, "the ant queen raises 3 eggs on her own fat");
        assertTrue(profile.nest().sealWhileFounding() && profile.nest().foundingChamber(), "the ant queen seals herself into a founding chamber");
    }

    @Test
    void theMacrotermesFileReadsWithAOneLevelSealedNestAndASwellingQueen() throws Exception {
        SpeciesProfile profile = read("macrotermes");
        assertEquals(1, profile.nest().maxLevels());
        assertTrue(profile.nest().sealWhileFounding());
        assertTrue(!profile.nest().foundingChamber(), "the termites' royal chamber is their founding nest");
        assertTrue(profile.queenBody().isPresent(), "the termite queen should swell");
        assertTrue(profile.queenBody().get().tooBigToWalkAt() > 0 && profile.queenBody().get().tooBigToWalkAt() <= 1);
        assertTrue(profile.founding().isPresent() && profile.founding().get().eggs() > 0, "termites raise a founding batch");
        // Food: dead plant material into a forage store beside the shaft, no bread.
        assertEquals(java.util.List.of("food"), profile.nest().sideRooms(), "termites keep their forage in a side room");
        assertTrue(!profile.food().makesBread(), "termites mill no bread");
        assertEquals("antcolonies:forage_pile", profile.food().pile());
        assertTrue(profile.food().plants().stream().anyMatch(plant -> plant.block().equals("minecraft:hay_block") && plant.foragers() > 1),
                "a hay bale is a big meal several termites cut at");
        assertTrue(profile.food().plants().stream().anyMatch(plant -> plant.block().equals("#minecraft:leaves") && plant.onlyDecaying()),
                "only dying leaves are food");
    }

    @Test
    void nursesAreCountedFromTheHeadcount() {
        SpeciesProfile.ColonyNumbers colony = SpeciesProfile.RED_HARVESTER_ANT.colony();
        assertEquals(0, colony.nurses(2));
        assertEquals(1, colony.nurses(3));
        assertEquals(1, colony.nurses(7));
        assertEquals(2, colony.nurses(8));
        assertEquals(3, colony.nurses(12));
        assertEquals(4, colony.nurses(20));
    }
}
