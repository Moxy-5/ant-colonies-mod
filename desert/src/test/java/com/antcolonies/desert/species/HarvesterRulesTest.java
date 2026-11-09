package com.antcolonies.desert.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.antcolonies.colony.ColonyRole;
import com.antcolonies.colony.QueenCare;
import com.antcolonies.species.SpeciesProfile;
import com.google.gson.JsonParser;

/**
 * Test revamp R5: the red harvester ant's own numbers run through the shared rules with no Minecraft: jobs by age
 * (nurse, then dig, then forage, as real Pogonomyrmex workers move outward with age), worker hunger, and the queen's
 * meals (a plain meal for an egg, rich bread for a burst).
 */
class HarvesterRulesTest {
    private static SpeciesProfile harvester;

    @BeforeAll
    static void readTheFile() throws Exception {
        DesertNumbers.signUp();
        try (InputStream in = HarvesterRulesTest.class.getResourceAsStream("/data/desertcolonies/species/red_harvester_ant.json")) {
            assertNotNull(in, "no harvester species file");
            harvester = SpeciesProfile.read(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .getOrThrow(error -> new AssertionError("red_harvester_ant: " + error));
        }
    }

    @Test
    void aWorkerNursesThenDigsThenForagesAndNeverGoesBack() {
        SpeciesProfile.WorkerNumbers worker = harvester.worker();
        assertEquals(ColonyRole.NURSE, worker.roleForAge(0), "a new worker nurses");
        int last = -1;
        for (long age = 0; age <= worker.lifespanTicks(); age += 1000) {
            int role = switch (worker.roleForAge(age)) {
                case NURSE -> 0;
                case BUILDER -> 1;
                case FORAGER -> 2;
            };
            assertTrue(role >= last, "a worker went back to an earlier job at age " + age);
            last = role;
        }
        assertEquals(ColonyRole.FORAGER, worker.roleForAge(worker.lifespanTicks() - 1), "an old worker forages");
    }

    @Test
    void aWorkerGetsHungryThenSlowsThenStarvesBeforeOldAge() {
        SpeciesProfile.WorkerNumbers worker = harvester.worker();
        assertTrue(worker.eats(), "harvester workers eat");
        assertFalse(worker.hungryAfter(0));
        assertTrue(worker.hungryAfter(worker.mealIntervalTicks()));
        assertTrue(worker.mealIntervalTicks() <= worker.slowAfterTicks() && worker.slowAfterTicks() <= worker.starveAfterTicks(),
                "hungry, slowed and starved come in that order");
        assertTrue(worker.starvedAfter(worker.starveAfterTicks()));
        assertTrue(worker.starveAfterTicks() < worker.lifespanTicks(), "a worker starves well within its life");
    }

    @Test
    void theQueenLaysAnEggForAMealAndABurstForRichBread() {
        SpeciesProfile.QueenNumbers queen = harvester.queen();
        int bread = 0;
        int pieces = 0;
        QueenCare.Meal meal;
        do {
            assertTrue(QueenCare.takesPiece(false, bread, queen), "she refused piece " + (pieces + 1));
            meal = QueenCare.afterPiece(bread, 0, false, queen);
            bread = meal.bread();
            pieces++;
        } while (!meal.eggStarts());
        assertEquals(queen.meal(), pieces, "an egg takes a full meal");
        assertFalse(QueenCare.takesPiece(false, bread, queen), "a full queen takes no more");

        int rich = 0;
        int richPieces = 0;
        do {
            meal = QueenCare.afterPiece(0, rich, true, queen);
            rich = meal.rich();
            richPieces++;
        } while (!meal.burstStarts());
        assertEquals(queen.richMeal(), richPieces);
        assertTrue(queen.burstEggs() > 1, "rich bread brings more than one egg");
        assertTrue(richPieces / (double) queen.burstEggs() < pieces, "rich bread costs fewer pieces an egg than plain bread");
    }
}
