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
        // Out at night and in the rain, through two foraging holes; a chimney's mouth on about 1 walk in 10.
        assertEquals(java.util.List.of("night", "rain"), profile.food().forageAt());
        assertEquals(2, profile.nest().foragingHoles());
        assertTrue(profile.nest().foragingHoleDistance() > 7, "the foraging holes must lie beyond the foot of a full-grown mound");
        assertTrue(profile.mound().isPresent() && profile.mound().get().chimneyTripChance() > 0 && profile.mound().get().chimneyTripChance() < 0.5F);
    }

    @Test
    void foragersGoOutWhenTheirProfileSays() {
        SpeciesProfile.FoodNumbers ant = SpeciesProfile.RED_HARVESTER_ANT.food();
        SpeciesProfile.FoodNumbers termite = new SpeciesProfile.FoodNumbers(24, "x", java.util.List.of(), "x", "x", 0, 0, java.util.List.of("night", "rain"));
        for (boolean day : new boolean[] {true, false}) {
            for (boolean rain : new boolean[] {true, false}) {
                assertTrue(ant.foragesNow(day, rain), "the harvester ant forages at any time");
                assertEquals(!day || rain, termite.foragesNow(day, rain), "termites forage at night and in the rain (day " + day + ", rain " + rain + ")");
            }
        }
    }

    @Test
    void theRedHarvesterAntHasNoForagingHolesAndAnyWalkMayUseAChimney() throws Exception {
        SpeciesProfile profile = read("red_harvester_ant");
        assertEquals(0, profile.nest().foragingHoles());
        assertEquals(SpeciesProfile.FoodNumbers.ALWAYS, profile.food().forageAt());
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
