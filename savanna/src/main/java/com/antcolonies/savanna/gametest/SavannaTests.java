package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.AwayKit;
import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.antcolonies.colony.Brood;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.savanna.colony.Mound;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.species.SpeciesBodies;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;

/**
 * The savanna's game tests (the split, step S4): its test classes in their categories, its own checks of every colony's
 * parts, and its lone insects for a test to walk around by hand.
 */
public final class SavannaTests {
    private SavannaTests() {
    }

    /** Signs up the savanna's tests (once, from {@code SavannaMod}). */
    public static void signUp() {
        // The legs: climbing, crawling through termite tunnels, reloads of climbing insects, the Matabele up a pillar.
        TestFilter.addCategory("legs", TermiteClimbingTests.class, TermiteTunnelTests.class, MatabeleTests.class);
        // The termite mound: its blocks, the cathedral, doorways and chimney mouths, mending the nest.
        TestFilter.addCategory("mound", TermiteMoundTests.class, TermiteCathedralTests.class, TermiteRepairTests.class, TermiteNewLandTests.class);
        // Termite food: foraging and its holes, the garden, new holes, dead wood, runways, bare patches, mushrooms.
        TestFilter.addCategory("termitefood", TermiteFoodTests.class, TermiteForagingTests.class, TermiteNewHoleTests.class,
                TermiteRunwayTests.class, TermiteBarePatchTests.class, TermitomycesTests.class);
        // Soldiers and the alarm.
        TestFilter.addCategory("soldiers", TermiteSoldierTests.class, TermiteAlarmTests.class);
        // The colony: founding, jobs by age, the virtual population, neighbours.
        TestFilter.addCategory("colony", TermiteTests.class, TermiteJobsByAgeTests.class, TermitePopulationTests.class,
                TermiteNeighbourTests.class, MatabeleColonyTests.class, MatabeleNestTests.class);
        // Colonies that live while the player is away: the termites' life while away.
        TestFilter.addCategory("away", TermiteAwayTests.class);
        // Hills and uneven ground: the side tunnels, the mound on a slope.
        TestFilter.addCategory("hills", TermiteHillTests.class);
        // The body (the animation engine): what the server decides about how a termite holds itself.
        TestFilter.addCategory("body", TermiteBodyTests.class);
        // The giant savanna trees (M7.5): their blocks, shapes, growth and fall.
        TestFilter.addCategory("trees", TreeTests.class, TreeGrowthTests.class);
        // Performance (only when named, never in a full run).
        TestFilter.addCategory("stress", TermiteStressTests.class);

        AwayKit.addPartsCheck(SavannaTests::partsAreTheSavannas);
    }

    /**
     * The savanna's checks of a colony's parts (every colony saved and loaded in a test, of any species), as they were
     * in the shared away tests before the split: its nurses' food, mound, alarm, king, soldiers, bodies and crust are
     * today's.
     */
    private static void partsAreTheSavannas(GameTestHelper helper, com.antcolonies.colony.Colony colony) {
        // Step 8b: the nurses' food is today's (a fungus garden's nodules first).
        colony.part(SavannaParts.FUNGUS_GARDEN).ifPresent(garden -> helper.assertTrue(colony.nurseFood() == garden,
                "a " + colony.getSpecies() + " colony's nurses' food should be its " + garden.getClass().getSimpleName() + ", not " + colony.nurseFood()));
        // Step 8a: the answers are today's (the mound's own checks).
        helper.assertTrue(colony.buildsMound() == colony.part(SavannaParts.MOUND).map(mound -> mound.numbers() != null).orElse(false)
                && colony.buildsOverNestNow() == colony.part(SavannaParts.MOUND).map(Mound::isActive).orElse(false),
                "a " + colony.getSpecies() + " colony should build a mound exactly when its mound part says so");
        // Step 9a: the answers and sockets are today's (the alarm's level, the king beside the queen, the soldiers fed by nurses).
        helper.assertTrue(colony.isAlarmed(helper.getLevel())
                == colony.part(SavannaParts.ALARM).map(alarm -> alarm.level(helper.getLevel()) != 0).orElse(false),
                "a " + colony.getSpecies() + " colony should be alarmed exactly when its alarm's level is above 0");
        ColonyHooks.CoFounders founder = colony.hooks().first(ColonyHooks.CO_FOUNDERS);
        helper.assertTrue(colony.has(SavannaParts.KING_CARE) == (founder != null)
                && (founder == null || founder.caste().equals(TermiteKingEntity.CASTE)
                        && founder.count() == (colony.part(SavannaParts.KING_CARE).orElseThrow().isAlive() ? 1 : 0)),
                "a " + colony.getSpecies() + " colony's founder beside the queen should be the termite king exactly when it cares for one");
        List<Entity> fed = new ArrayList<>();
        colony.hooks().all(ColonyHooks.CASTES).forEach(castes -> fed.addAll(castes.fedByNurses(helper.getLevel())));
        helper.assertTrue(fed.equals(colony.part(SavannaParts.SOLDIERS).map(soldiers -> soldiers.getSoldiers(helper.getLevel()))
                .orElse(List.of())), "a " + colony.getSpecies() + " colony's nurses should feed exactly its soldiers besides the queen and king");
        // Step 9c: each species' bodies come from its table, as they did when the shared code asked "is this Macrotermes?".
        boolean termites = colony.getSpecies().equals(SavannaSpecies.MACROTERMES);
        SpeciesBodies bodies = SpeciesBodies.of(colony.getSpecies());
        if (termites) {
            helper.assertTrue(bodies.typeFor(Caste.WORKER) == SavannaEntities.TERMITE_WORKER.get()
                    && bodies.corpse().get() == SavannaEntities.TERMITE_CORPSE.get()
                    && bodies.young().get() == SavannaEntities.TERMITE_NYMPH.get(),
                    "a " + colony.getSpecies() + " colony's worker, body and young should be the species' own");
        }
        for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
            String caste = rank.caste();
            helper.assertTrue(colony.isOwnCaste(caste) == termites
                    && bodies.typeFor(caste) == (!termites ? null : rank == TermiteSoldierEntity.Rank.MINOR
                            ? SavannaEntities.TERMITE_MINOR_SOLDIER.get() : SavannaEntities.TERMITE_MAJOR_SOLDIER.get())
                    && (!termites || Brood.savedAs(colony, caste).equals(rank.name()) // (saved as before)
                            && Brood.fromSaved(colony, rank.name()).equals(caste)),
                    "a " + colony.getSpecies() + " colony's " + caste + " should be its own caste, body and save word exactly for termites");
        }
        // Step 10: the soldiers' caste numbers as synced and saved before (1 minor, 2 major); a species without, 0.
        for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
            helper.assertTrue(colony.casteNumber(rank.caste()) == (termites ? rank.id() : 0)
                    && Objects.equals(colony.casteOfNumber(rank.id()), termites ? rank.caste() : null),
                    "a " + colony.getSpecies() + " colony's " + rank.caste() + " number should be " + (termites ? rank.id() : 0)
                            + ", not " + colony.casteNumber(rank.caste()));
        }
        // Step 9c: the crust a colony plasters with is the mud crust, for the species that build a mound.
        helper.assertTrue(colony.hooks().first(ColonyHooks.CRUST) == (colony.has(SavannaParts.MOUND) ? SavannaBlocks.MUD_CRUST.get() : null),
                "a " + colony.getSpecies() + " colony should plaster with the mud crust exactly when it builds a mound");
    }

    /** A termite worker with no mind of its own and no colony, for a test to walk around by hand ({@link TestAnts#lone}). */
    static TermiteWorkerEntity loneTermite(GameTestHelper helper, int x, int y, int z) {
        return TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x, y, z);
    }

    /** A termite soldier of this kind with no colony, with its own mind (it wanders, drums when told), for a test to watch. */
    static TermiteSoldierEntity loneSoldier(GameTestHelper helper, boolean major, int x, int y, int z) {
        return TestAnts.loneWithAMind(helper, major ? SavannaEntities.TERMITE_MAJOR_SOLDIER.get() : SavannaEntities.TERMITE_MINOR_SOLDIER.get(), x, y, z);
    }
}
