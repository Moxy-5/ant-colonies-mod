package com.antcolonies.desert.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.antcolonies.colony.NestPlan;
import com.antcolonies.species.SpeciesProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Checks that the red harvester ant's species file reads without errors and holds sensible numbers (moved out of the
 * savanna's SpeciesProfileTest with the file, the split, step S7; every check kept).
 */
class HarvesterProfileTest {
    private static JsonElement json(String name) throws Exception {
        try (InputStream in = HarvesterProfileTest.class.getResourceAsStream("/data/desertcolonies/species/" + name + ".json")) {
            assertNotNull(in, "no file data/desertcolonies/species/" + name + ".json");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static SpeciesProfile read(String name) throws Exception {
        DesertNumbers.signUp(); // its own sections are read too (Apex part 3, T1)
        return SpeciesProfile.read(json(name)).getOrThrow(error -> new AssertionError(name + ": " + error));
    }

    @Test
    void theHarvesterAntListsItsOwnColonyParts() throws Exception {
        // Project Apex, colony parts step 2: the parts besides the shared ones (the names are checked against the parts
        // registry when the game loads the files).
        assertEquals(java.util.List.of("hill", "bakery", "trunk_trails", "patrol", "forager_spots", "door", "recruits"),
                read("red_harvester_ant").parts());
        assertEquals(read("red_harvester_ant").parts(), HarvesterBuiltIn.NUMBERS.parts(),
                "the built-in harvester numbers (used when its file is broken) have the same parts as its file");
    }

    /** Apex part 3, T1: the desert mod reads its own sections of the file. */
    @Test
    void theDesertModReadsItsOwnSectionsOfTheFile() throws Exception {
        SpeciesProfile profile = read("red_harvester_ant");
        for (com.antcolonies.species.SpeciesSection<?> section : java.util.List.of(DesertNumbers.DOOR, DesertNumbers.PATROL,
                DesertNumbers.TRUNK, DesertNumbers.NIGHT_SOIL)) {
            assertTrue(profile.section(section).isPresent(), "the harvester file's " + section + " is read");
        }
        assertTrue(profile.gardenRooms().isEmpty(), "harvester ants grow no fungus");
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
        assertFalse(json("red_harvester_ant").getAsJsonObject().has("queen_body"), "the ant queen does not swell");
        assertTrue(profile.founding().isPresent() && profile.founding().get().eggs() == 3, "the ant queen raises 3 eggs on her own fat");
        assertTrue(profile.nest().sealWhileFounding() && profile.nest().foundingChamber(), "the ant queen seals herself into a founding chamber");
        assertEquals(NestPlan.RoomSizes.DEFAULT, profile.nest().roomSizes(), "the file lists the room sizes nests have always had");
    }

    @Test
    void harvesterAntsHaveNoSoldiersAndSearchAlone() throws Exception {
        assertFalse(json("red_harvester_ant").getAsJsonObject().has("soldiers"), "harvester ants have no soldier caste");
        assertFalse(json("red_harvester_ant").getAsJsonObject().has("alarm"), "harvester ants raise no alarm yet (their workers are unchanged)");
        // Brain step 4c (the user's decision): harvester ants no longer know every plant round the nest; each searches alone.
        assertTrue(read("red_harvester_ant").food().findRadius() > 0, "harvester ants find their food by looking too");
        assertTrue(read("red_harvester_ant").food().search().map(search -> search.alone()).orElse(false), "harvester ants search alone");
    }

    @Test
    void harvesterAntsCutGramaOnlyWhenRipe() throws Exception {
        java.util.List<SpeciesProfile.PlantFood> plants = read("red_harvester_ant").food().plants();
        for (String grama : java.util.List.of("desertcolonies:blue_grama", "desertcolonies:black_grama")) {
            SpeciesProfile.PlantFood plant = plants.stream().filter(p -> p.block().equals(grama)).findFirst().orElseThrow();
            assertEquals(java.util.Map.of("stage", "ripe"), plant.state(), grama + " is cut before it is ripe");
            assertTrue(plant.pieces() > plants.get(0).pieces(), grama + " is richer than a tuft of short grass");
            assertEquals(10, plant.pieces(), grama + " lasts 10 takes (the user's decision, 2026-10-17)");
        }
        assertEquals(java.util.Map.of(), plants.get(0).state(), "the ants' grass has no state to match");
    }

    /** Long trails (the user's decisions of 2026-10-17): a trail reaches as far as its traffic holds, 96 blocks at most. */
    @Test
    void aTrailReachesAsFarAsItsTrafficHolds() throws Exception {
        SpeciesProfile profile = read("red_harvester_ant");
        SpeciesProfile.Trails trails = profile.food().trails();
        DesertNumbers.TrunkNumbers trunk = profile.section(DesertNumbers.TRUNK).orElseThrow();
        assertEquals(12, profile.food().radius(), "a young colony reaches about 12 blocks");
        assertEquals(96, trunk.mostLength());
        assertEquals(trunk.start(), trunk.holds(0, trails.halfLifeTicks()), 1e-9, "an unused trail holds nothing past its start");
        // A trail walked r times a day settles near a strength of r / ln 2 (half-life a day): it holds 6 blocks a trip a day.
        for (int tripsADay : new int[] {1, 5, 10}) {
            double strength = tripsADay / Math.log(2) * trails.halfLifeTicks() / 24000.0;
            assertEquals(trunk.start() + 6.0 * tripsADay, trunk.holds(strength, trails.halfLifeTicks()), 1e-6, tripsADay + " trips a day");
        }
        assertTrue(trunk.holds(trails.mostStrength(), trails.halfLifeTicks()) == 96, "a busy trail stops at 96 blocks");
        assertTrue(trunk.holds(5, trails.halfLifeTicks()) < 30, "a weak trail (a few trips a day) stays short");
    }

    @Test
    void aRichPatchIsWorthAWalkThatGrowsWithItsSize() throws Exception {
        SpeciesProfile profile = read("red_harvester_ant");
        SpeciesProfile.RecruitNumbers recruit = profile.food().search().orElseThrow().recruit().orElseThrow();
        // Value against distance (long trails step 3): 15 pieces near home (1.5 grama plants), about 30 at 60 blocks, about 40 at 96.
        assertEquals(15, recruit.needed(5), 1e-9, "near the nest");
        assertEquals(15, recruit.needed(20), 1e-9);
        assertEquals(30, recruit.needed(60), 1.0);
        assertEquals(40, recruit.needed(96), 1.0);
        assertTrue(recruit.needed(40) > recruit.needed(30), "farther needs more");
        // How many are called out goes by the size of the patch (the user's decision): 1 per 10 pieces (a grama plant), 1 to 8.
        assertEquals(1, recruit.recruitsFor(15));
        assertEquals(2, recruit.recruitsFor(20));
        assertEquals(4, recruit.recruitsFor(40), "four ripe blue grama plants");
        assertEquals(8, recruit.recruitsFor(500), "never more than 8 a call");
        // Grama counts, short grass does not (a tuft is not a patch).
        SpeciesProfile.PlantFood grass = profile.food().plants().stream().filter(p -> p.block().equals("minecraft:short_grass")).findFirst().orElseThrow();
        SpeciesProfile.PlantFood grama = profile.food().plants().stream().filter(p -> p.block().equals("desertcolonies:blue_grama")).findFirst().orElseThrow();
        assertTrue(recruit.counts(grama));
        assertTrue(!recruit.counts(grass));
    }

    @Test
    void foragersGoOutWhenTheirProfileSays() {
        SpeciesProfile.FoodNumbers ant = HarvesterBuiltIn.NUMBERS.food();
        SpeciesProfile.FoodNumbers termite = new SpeciesProfile.FoodNumbers(24, "x", java.util.List.of(), "x", "x", 0, 0, java.util.List.of("night", "rain"), 0);
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

    /** Nest stages: the harvester ant's nest grows a level at a time with its workers and stored food. */
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
        // The brain, part 1 (user, 2026-10-14): scaled-down caps, most of them numbers inside the nest.
        assertEquals(3000, read("red_harvester_ant").colony().maxWorkers());
        assertEquals(50, read("red_harvester_ant").colony().crew());
        assertTrue(!read("red_harvester_ant").refuse().eatsOrBuries(), "ants carry their dead to the refuse chamber");
    }

    @Test
    void nursesAreCountedFromTheHeadcount() {
        SpeciesProfile.ColonyNumbers colony = HarvesterBuiltIn.NUMBERS.colony();
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
    }
}
