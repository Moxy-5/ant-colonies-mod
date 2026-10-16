package com.antcolonies.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.antcolonies.colony.NestPlan;
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
        assertEquals(NestPlan.RoomSizes.DEFAULT, profile.nest().roomSizes(), "the file lists the room sizes nests have always had");
    }

    @Test
    void theMacrotermesFileReadsWithAOneLevelSealedNestAndASwellingQueen() throws Exception {
        SpeciesProfile profile = read("macrotermes");
        assertEquals(1, profile.nest().maxLevels());
        assertEquals(NestPlan.RoomSizes.DEFAULT, profile.nest().roomSizes(), "no room sizes in the file: the default ones");
        assertTrue(profile.nest().sealWhileFounding());
        assertTrue(!profile.nest().foundingChamber(), "the termites' royal chamber is their founding nest");
        assertTrue(profile.queenBody().isPresent(), "the termite queen should swell");
        assertTrue(profile.queenBody().get().tooBigToWalkAt() > 0 && profile.queenBody().get().tooBigToWalkAt() <= 1);
        assertTrue(profile.founding().isPresent() && profile.founding().get().eggs() > 0, "termites raise a founding batch");
        // Food: dead plant material built into fungus comb in garden rooms round the royal chamber, no bread, no side room.
        assertEquals(java.util.List.of(), profile.nest().sideRooms(), "termites keep their food in the garden rooms, not a side room");
        assertTrue(!profile.food().makesBread(), "termites mill no bread");
        assertEquals("antcolonies:fungus_comb", profile.food().pile());
        assertTrue(profile.garden().isPresent(), "termites keep a fungus garden");
        assertEquals(1, profile.garden().get().roomsFor(1), "the first garden comes with the first worker");
        assertEquals(0, profile.garden().get().roomsFor(0), "no garden while the colony is founding");
        assertTrue(profile.garden().get().roomsFor(1000) <= com.antcolonies.colony.NestPlan.MAX_GARDENS);
        assertFalse(profile.colony().nursesFrom().isEmpty(), "termites have nurses");
        // The queen lays a steady stream, faster as she swells.
        assertTrue(profile.laying().isPresent(), "the termite queen lays a steady stream of eggs");
        SpeciesProfile.Laying laying = profile.laying().get();
        assertTrue(laying.intervalFor(1.0F) < laying.intervalFor(0.0F), "a bigger queen lays faster");
        assertTrue(laying.foodPerEgg() <= profile.queen().meal(), "she must be able to hold the food for one egg");
        assertTrue(profile.food().plants().stream().anyMatch(plant -> plant.block().equals("minecraft:hay_block") && plant.foragers() > 1),
                "a hay bale is a big meal several termites cut at");
        assertTrue(profile.food().plants().stream().anyMatch(plant -> plant.block().equals("#minecraft:leaves") && plant.onlyDecaying()),
                "only dying leaves are food");
        // Out at night and in the rain, through two foraging holes; a chimney's mouth on about 1 walk in 10.
        assertEquals(java.util.List.of("night", "rain"), profile.food().forageAt());
        assertEquals(2, profile.nest().foragingHoles());
        assertTrue(profile.nest().foragingHoleDistance() > 7, "the foraging holes must lie beyond the foot of a full-grown mound");
        assertTrue(profile.mound().isPresent() && profile.mound().get().chimneyTripChance() > 0 && profile.mound().get().chimneyTripChance() < 0.5F);
        // Soldiers: about 1 in 10, 3 minors to a major; a major needs a bigger meal than a minor, a minor more than a worker.
        assertTrue(profile.soldiers().isPresent(), "termites have soldiers");
        SpeciesProfile.SoldierNumbers soldiers = profile.soldiers().get();
        assertTrue(soldiers.majorMeal() > soldiers.minorMeal() && soldiers.minorMeal() >= profile.brood().larvaMeal());
        assertTrue(soldiers.mealIntervalTicks() < soldiers.slowAfterTicks(), "a soldier gets hungry before it slows down");
        // The minors line the trail every few blocks, and wait for the workers well before the holes are plugged (30 seconds after the foraging).
        assertEquals(3, soldiers.sentrySpacing());
        assertTrue(soldiers.escortHomeTicks() > 0 && soldiers.escortHomeTicks() <= 300, "the minors must have time to get in before the holes are plugged");
        // The alarm: four levels, each calming down later than the one below; level 4 keeps everyone in for a whole day.
        SpeciesProfile.AlarmNumbers alarm = profile.alarm().orElseThrow();
        for (int level = 1; level < 4; level++) {
            assertTrue(alarm.calmTicksFor(level) < alarm.calmTicksFor(level + 1), "level " + (level + 1) + " lasts longer than " + level);
        }
        assertEquals(24000, alarm.calmTicksFor(4));
        assertTrue(alarm.breakInsForLevel4() >= 2 && alarm.soldierBoost() > 1.0F);
    }

    /**
     * How many soldiers a colony wants: none for the founding batch (5 workers), the first minor from 7 workers, and with
     * the 20 workers a colony may have today 2 minors and 1 major; about 1 in 10 in a big colony, 3 minors to a major.
     */
    @Test
    void aColonyWantsAboutOneSoldierInTenThreeMinorsToAMajor() throws Exception {
        SpeciesProfile.SoldierNumbers soldiers = read("macrotermes").soldiers().orElseThrow();
        assertEquals(0, soldiers.wantedMinors(5) + soldiers.wantedMajors(5), "no soldier in the founding batch");
        assertEquals(0, soldiers.wantedMinors(6));
        assertEquals(1, soldiers.wantedMinors(7), "the first minor from 7 workers");
        assertEquals(0, soldiers.wantedMajors(19));
        assertEquals(2, soldiers.wantedMinors(20));
        assertEquals(1, soldiers.wantedMajors(20), "the first major at 20 workers");
        assertEquals(75, soldiers.wantedMinors(1000));
        assertEquals(25, soldiers.wantedMajors(1000));
        assertTrue(read("red_harvester_ant").soldiers().isEmpty(), "harvester ants have no soldier caste");
        assertTrue(read("red_harvester_ant").alarm().isEmpty(), "harvester ants raise no alarm yet (their workers are unchanged)");
        assertTrue(read("macrotermes").food().findRadius() > 0, "termites find their food by looking");
        assertTrue(read("red_harvester_ant").food().findRadius() == 0, "harvester ants know every plant round the nest (unchanged)");
    }

    /** Termites eat dead plant material only: Dry Grass once it is dry, never green grass. */
    @Test
    void termitesOnlyEatDryGrass() throws Exception {
        java.util.List<SpeciesProfile.PlantFood> plants = read("macrotermes").food().plants();
        for (SpeciesProfile.PlantFood plant : plants) {
            assertTrue(!plant.block().equals("minecraft:short_grass") && !plant.block().equals("minecraft:tall_grass"),
                    "termites eat green grass: " + plant.block());
        }
        for (String grass : java.util.List.of("antcolonies:dry_grass", "antcolonies:tall_dry_grass")) {
            SpeciesProfile.PlantFood plant = plants.stream().filter(p -> p.block().equals(grass)).findFirst().orElseThrow();
            assertEquals(java.util.Map.of("growth", "dry"), plant.state(), grass + " is eaten before it is dry");
        }
        assertEquals(java.util.Map.of(), read("red_harvester_ant").food().plants().get(0).state(), "the ants' grass has no state to match");
    }

    @Test
    void foragersGoOutWhenTheirProfileSays() {
        SpeciesProfile.FoodNumbers ant = SpeciesProfile.RED_HARVESTER_ANT.food();
        SpeciesProfile.FoodNumbers termite = new SpeciesProfile.FoodNumbers(24, "x", java.util.List.of(), "x", "x", 0, 0, java.util.List.of("night", "rain"), 0, java.util.Optional.empty());
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

    /** Nest stages: the harvester ant's nest grows a level at a time with its workers and stored food; termites dig their one level at once. */
    @Test
    void theNestOpensLevelsAsTheColonyGrows() throws Exception {
        SpeciesProfile.NestNumbers ant = read("red_harvester_ant").nest();
        assertEquals(0, ant.levelsFor(0, 0), "the founding queen alone digs only her founding chamber");
        assertEquals(1, ant.levelsFor(3, 0), "the first workers dig the seed store");
        assertEquals(1, ant.levelsFor(6, 9));
        assertEquals(2, ant.levelsFor(6, 10), "the nursery with 6 workers and 10 seeds");
        assertEquals(2, ant.levelsFor(11, 100));
        assertEquals(3, ant.levelsFor(12, 30));
        assertEquals(4, ant.levelsFor(20, 30));
        assertEquals(4, ant.levelsFor(60, 200));
        assertEquals(1, read("macrotermes").nest().levelsFor(0, 0), "termites have no nest stages");
        // The brain, part 1 (user, 2026-10-14): scaled-down caps, most of them numbers inside the nest.
        assertEquals(3000, read("red_harvester_ant").colony().maxWorkers());
        assertEquals(50000, read("macrotermes").colony().maxWorkers());
        assertEquals(50, read("macrotermes").colony().realCap());
        assertEquals(50, read("red_harvester_ant").colony().crew());
        SpeciesProfile.RefuseNumbers termiteDead = read("macrotermes").refuse();
        assertTrue(termiteDead.eatsOrBuries() && termiteDead.eatFreshTicks().get() < termiteDead.corpseDecayTicks(),
                "termites eat their fresh dead and bury the old ones before they crumble");
        assertTrue(!read("red_harvester_ant").refuse().eatsOrBuries(), "ants carry their dead to the refuse chamber");
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

    @Test
    void theHarvesterAntForagesByDayAndRestsAtMiddayByTheBiomesHeat() throws Exception {
        SpeciesProfile.DayClock clock = read("red_harvester_ant").food().dayClock().orElseThrow();
        float plains = 0.8F, badlands = 2.0F;
        assertTrue(clock.outAt(1000, plains, false), "out at 7:00");
        assertTrue(clock.outAt(10500, plains, false), "out at 16:30");
        assertFalse(clock.outAt(11500, plains, false), "in at 17:30");
        assertFalse(clock.outAt(18000, plains, false), "in at midnight");
        assertFalse(clock.outAt(1000, plains, true), "in while it rains");
        assertFalse(clock.outAt(7000, plains, false), "a short rest at 13:00 in the plains");
        assertTrue(clock.outAt(6000, plains, false) && clock.outAt(8000, plains, false), "only a short rest in the plains");
        assertTrue(clock.restTicks(plains) > 0 && clock.restTicks(plains) < 1500);
        assertEquals(4000, clock.restTicks(badlands));
        assertTrue(clock.outAt(4900, badlands, false), "out at 10:54 in the badlands");
        assertFalse(clock.outAt(5000, badlands, false) || clock.outAt(8999, badlands, false), "in from 11:00 to 15:00 in the badlands");
        assertTrue(clock.outAt(9000, badlands, false), "out again at 15:00 in the badlands");
        assertEquals(0, clock.restTicks(0.3F), "no rest in a cool biome");
        assertEquals("13:00", SpeciesProfile.DayClock.clock(7000));
        assertEquals("5:30", SpeciesProfile.DayClock.clock(23500));
        assertTrue(read("macrotermes").food().dayClock().isEmpty(), "termites keep their night and rain rule");
    }
}
