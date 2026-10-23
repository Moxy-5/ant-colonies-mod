package com.antcolonies.savanna.species;

import com.antcolonies.species.*;

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

/**
 * Checks that the savanna's species profile files read without errors and hold sensible numbers (the red harvester
 * ant's checks moved to the desert mod's HarvesterProfileTest with its file, the split, step S7).
 */
class SpeciesProfileTest {
    private static JsonElement json(String name) throws Exception {
        try (InputStream in = SpeciesProfileTest.class.getResourceAsStream("/data/antcolonies/species/" + name + ".json")) {
            assertNotNull(in, "no file data/antcolonies/species/" + name + ".json");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static SpeciesProfile read(String name) throws Exception {
        SavannaNumbers.signUp(); // its own sections are read too (Apex part 3, T1)
        return SpeciesProfile.read(json(name)).getOrThrow(error -> new AssertionError(name + ": " + error));
    }

    @Test
    void eachSpeciesListsItsOwnColonyParts() throws Exception {
        // Project Apex, colony parts step 2: the parts besides the shared ones (the names are checked against the parts
        // registry when the game loads the files).
        assertEquals(java.util.List.of("king_care", "mound", "galleries", "closed_holes", "hidden_trips", "fungus_garden", "mushrooms", "soldiers", "alarm", "bare_patches"),
                read("macrotermes").parts());
    }

    /** Apex part 3, T1: the savanna mod reads its own sections of the file, and a broken one breaks the whole file. */
    @Test
    void theSavannaModReadsItsOwnSectionsOfTheFile() throws Exception {
        SpeciesProfile profile = read("macrotermes");
        for (SpeciesSection<?> section : java.util.List.of(SavannaNumbers.QUEEN_BODY, SavannaNumbers.MOUND, SavannaNumbers.GARDEN,
                SavannaNumbers.SOLDIERS, SavannaNumbers.ALARM, SavannaNumbers.GALLERIES, SavannaNumbers.REGROWTH)) {
            assertTrue(profile.section(section).isPresent(), "the termite file's " + section + " is read");
        }
        assertEquals(profile.section(SavannaNumbers.GARDEN).orElseThrow().rooms(), profile.gardenRooms().orElseThrow().rooms(),
                "the framework's garden rooms and the savanna's garden read the same object");
        JsonElement broken = JsonParser.parseString(new com.google.gson.Gson().toJson(json("macrotermes")));
        broken.getAsJsonObject().getAsJsonObject("mound").addProperty("max_height", "tall");
        assertTrue(SpeciesProfile.read(broken).error().isPresent(), "a broken mound section breaks the file, as before");
    }

    @Test
    void theMacrotermesFileReadsWithAOneLevelSealedNestAndASwellingQueen() throws Exception {
        SpeciesProfile profile = read("macrotermes");
        assertEquals(1, profile.nest().maxLevels());
        assertEquals(NestPlan.RoomSizes.DEFAULT, profile.nest().roomSizes(), "no room sizes in the file: the default ones");
        assertTrue(profile.nest().sealWhileFounding());
        assertTrue(!profile.nest().foundingChamber(), "the termites' royal chamber is their founding nest");
        assertTrue(profile.section(SavannaNumbers.QUEEN_BODY).isPresent(), "the termite queen should swell");
        assertTrue(profile.section(SavannaNumbers.QUEEN_BODY).get().tooBigToWalkAt() > 0 && profile.section(SavannaNumbers.QUEEN_BODY).get().tooBigToWalkAt() <= 1);
        assertTrue(profile.founding().isPresent() && profile.founding().get().eggs() > 0, "termites raise a founding batch");
        // Food: dead plant material built into fungus comb in garden rooms round the royal chamber, no bread, no side room.
        assertEquals(java.util.List.of(), profile.nest().sideRooms(), "termites keep their food in the garden rooms, not a side room");
        assertTrue(!profile.food().makesBread(), "termites mill no bread");
        assertEquals("antcolonies:fungus_comb", profile.food().pile());
        assertTrue(profile.section(SavannaNumbers.GARDEN).isPresent(), "termites keep a fungus garden");
        assertEquals(1, profile.section(SavannaNumbers.GARDEN).get().roomsFor(1), "the first garden comes with the first worker");
        assertEquals(0, profile.section(SavannaNumbers.GARDEN).get().roomsFor(0), "no garden while the colony is founding");
        assertTrue(profile.section(SavannaNumbers.GARDEN).get().roomsFor(1000) <= com.antcolonies.colony.NestPlan.MAX_GARDENS);
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
        assertTrue(profile.section(SavannaNumbers.MOUND).isPresent() && profile.section(SavannaNumbers.MOUND).get().chimneyTripChance() > 0 && profile.section(SavannaNumbers.MOUND).get().chimneyTripChance() < 0.5F);
        // Soldiers: about 1 in 10, 3 minors to a major; a major needs a bigger meal than a minor, a minor more than a worker.
        assertTrue(profile.section(SavannaNumbers.SOLDIERS).isPresent(), "termites have soldiers");
        SavannaNumbers.SoldierNumbers soldiers = profile.section(SavannaNumbers.SOLDIERS).get();
        assertTrue(soldiers.majorMeal() > soldiers.minorMeal() && soldiers.minorMeal() >= profile.brood().larvaMeal());
        assertTrue(soldiers.mealIntervalTicks() < soldiers.slowAfterTicks(), "a soldier gets hungry before it slows down");
        // The minors line the trail every few blocks, and wait for the workers well before the holes are plugged (30 seconds after the foraging).
        assertEquals(3, soldiers.sentrySpacing());
        assertTrue(soldiers.escortHomeTicks() > 0 && soldiers.escortHomeTicks() <= 300, "the minors must have time to get in before the holes are plugged");
        // The alarm: four levels, each calming down later than the one below; level 4 keeps everyone in for a whole day.
        SavannaNumbers.AlarmNumbers alarm = profile.section(SavannaNumbers.ALARM).orElseThrow();
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
        SavannaNumbers.SoldierNumbers soldiers = read("macrotermes").section(SavannaNumbers.SOLDIERS).orElseThrow();
        assertEquals(0, soldiers.wantedMinors(5) + soldiers.wantedMajors(5), "no soldier in the founding batch");
        assertEquals(0, soldiers.wantedMinors(6));
        assertEquals(1, soldiers.wantedMinors(7), "the first minor from 7 workers");
        assertEquals(0, soldiers.wantedMajors(19));
        assertEquals(2, soldiers.wantedMinors(20));
        assertEquals(1, soldiers.wantedMajors(20), "the first major at 20 workers");
        assertEquals(75, soldiers.wantedMinors(1000));
        assertEquals(25, soldiers.wantedMajors(1000));
        assertTrue(read("macrotermes").food().findRadius() > 0, "termites find their food by looking");
        assertTrue(read("macrotermes").food().search().isEmpty(), "termites still tell the colony what they find");
        assertTrue(read("macrotermes").food().dayClock().isEmpty(), "termites keep their night and rain rule");
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
    }

    /** Grama is the harvester ants' food (their checks: the desert mod's HarvesterProfileTest); termites never eat it green. */
    @Test
    void termitesNeverEatGrama() throws Exception {
        for (SpeciesProfile.PlantFood plant : read("macrotermes").food().plants()) {
            assertTrue(!plant.block().contains("grama"), "termites eat green grama");
        }
    }

    /** Nest stages: termites dig their one level at once (the harvester ant's levels: the desert mod's HarvesterProfileTest). */
    @Test
    void termitesDigTheirOneLevelAtOnce() throws Exception {
        assertEquals(1, read("macrotermes").nest().levelsFor(0, 0), "termites have no nest stages");
        // The brain, part 1 (user, 2026-10-14): scaled-down caps, most of them numbers inside the nest.
        assertEquals(50000, read("macrotermes").colony().maxWorkers());
        assertEquals(50, read("macrotermes").colony().realCap());
        SpeciesProfile.RefuseNumbers termiteDead = read("macrotermes").refuse();
        assertTrue(termiteDead.eatsOrBuries() && termiteDead.eatFreshTicks().get() < termiteDead.corpseDecayTicks(),
                "termites eat their fresh dead and bury the old ones before they crumble");
    }

}
