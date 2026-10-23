package com.antcolonies.gametest;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyQuestions;
import com.antcolonies.colony.DesertParts;
import com.antcolonies.colony.TrunkTrails;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.DesertEntities;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesBodies;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The desert's game tests (the split, step S4): its test classes in their categories, its own checks of every colony's
 * parts, its fence round each colony in a test's arena, and its lone ants for a test to walk around by hand.
 */
public final class DesertTests {
    private DesertTests() {
    }

    /** Signs up the desert's tests (once, from {@code DesertMod}). */
    public static void signUp() {
        // The legs: walking, climbing, ceilings, reloads of climbing ants.
        TestFilter.addCategory("legs", NavigationTests.class);
        // The harvester ant's nest: digging, the hill, seeds, bread, eggs and larvae, hunger, the dead.
        TestFilter.addCategory("antnest", NestTests.class);
        // The colony: the virtual population, the lasting trails (brain part 4), neighbours.
        TestFilter.addCategory("colony", HarvesterPopulationTests.class, HarvesterTrailTests.class, HarvesterNeighbourTests.class);
        // Colonies that live while the player is away: going away and coming back, eating and raising the young as numbers.
        TestFilter.addCategory("away", HarvesterAwayTests.class);
        // Hills and uneven ground: the queen's level spot, the hill on a step.
        TestFilter.addCategory("hills", HarvesterHillTests.class);
        // The body (the animation engine): naps, cleaning, noticing the player.
        TestFilter.addCategory("body", HarvesterBodyTests.class);

        AwayKit.addPartsCheck(DesertTests::partsAreTheDeserts);
        // A trunk trail can reach 96 blocks and the arenas are about 45 apart: each colony's trails stop at its own
        // arena's edge.
        TestIsolation.addFence((colony, arena) -> colony.part(DesertParts.TRUNK_TRAILS).ifPresent(trunk -> trunk.setTestFence(arena)));
    }

    /**
     * The desert's checks of a colony's parts (every colony saved and loaded in a test, of any species), as they were in
     * the shared away tests before the split: its nurses' food, trails and bodies are today's.
     */
    private static void partsAreTheDeserts(GameTestHelper helper, com.antcolonies.colony.Colony colony) {
        // Step 8b: the nurses' food is today's (the bread, when nothing comes before it).
        colony.part(DesertParts.BAKERY).filter(bakery -> colony.hooks().pluggedInto(ColonyHooks.NURSE_FOOD).get(0).equals("bakery"))
                .ifPresent(bakery -> helper.assertTrue(colony.nurseFood() == bakery, "a " + colony.getSpecies() + " colony's nurses' food should be its "
                        + bakery.getClass().getSimpleName() + ", not " + colony.nurseFood()));
        // Step 8a: the answers are today's (the trunk trails' own switch).
        helper.assertTrue(colony.questions().ask(helper.getLevel(), ColonyQuestions.KEEPS_TRAILS, null)
                == colony.part(DesertParts.TRUNK_TRAILS).map(TrunkTrails::keepsTrails).orElse(false),
                "a " + colony.getSpecies() + " colony should keep trails exactly when its trunk trails say so");
        // Step 9c: each species' bodies come from its table, as they did when the shared code asked "is this Macrotermes?".
        if (colony.getSpecies().equals(Species.RED_HARVESTER_ANT)) {
            SpeciesBodies bodies = SpeciesBodies.of(colony.getSpecies());
            helper.assertTrue(bodies.typeFor(Caste.WORKER) == DesertEntities.WORKER_ANT.get()
                    && bodies.corpse().get() == DesertEntities.ANT_CORPSE.get()
                    && bodies.young().get() == DesertEntities.ANT_LARVA.get(),
                    "a " + colony.getSpecies() + " colony's worker, body and young should be the species' own");
        }
    }

    /** A worker ant with no mind of its own and no colony, for a test to walk around by hand ({@link TestAnts#lone}). */
    static WorkerAntEntity loneAnt(GameTestHelper helper, int x, int y, int z) {
        return TestAnts.lone(helper, DesertEntities.WORKER_ANT.get(), x, y, z);
    }

    /** The same at an exact spot (not the middle of a block). */
    static WorkerAntEntity loneAnt(GameTestHelper helper, float x, float y, float z) {
        return TestAnts.lone(helper, DesertEntities.WORKER_ANT.get(), x, y, z);
    }
}
