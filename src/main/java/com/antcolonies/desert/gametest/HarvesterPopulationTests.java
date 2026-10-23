package com.antcolonies.desert.gametest;

import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.Population;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.desert.registry.DesertEntities;
import com.antcolonies.species.Species;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * The brain, part 1 ({@link Population}): most of the colony is numbers inside the nest, a few are real mobs. Game tests
 * have no players, so each test says where pretend players' eyes are ({@link Population#setViewersForTests}).
 * The red harvester ant's scenes (the split, step S4; the termites' are in {@link TermitePopulationTests}).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class HarvesterPopulationTests {
    /** An ant colony in this test area with its whole nest dug out at once and the brain on; null if there is none yet. */
    static Colony antColony(GameTestHelper helper) {
        return colony(helper, Species.RED_HARVESTER_ANT);
    }

    /** An ant queen on deep earth; after 10 ticks her colony has its nest dug, no hunger and the brain on, with nobody watching. */
    static void startAntColony(GameTestHelper helper) {
        TestScenes.deepGround(helper);
        helper.spawn(DesertEntities.QUEEN_ANT.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(10, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony != null, "no ant colony");
            colony.setHungerOn(false);
            digNestAtOnce(helper.getLevel(), colony);
            colony.population().setOnForTests(true);
            colony.population().setViewersForTests(List.of());
        });
    }

    /**
     * With a small crew (10, set for the test) and nobody near, 10 real workers come out of the 40 in the numbers, two a
     * second, and the headcount stays 40. A player near (eyes 20 blocks off, above ground): all 40 come out (fewer than
     * the cap of 50). The player gone: they go back in down to the crew again. Then the species' own crew (the whole cap,
     * the user's decision: a loaded colony works as before the brain): all 40 come out with nobody near. Nothing is lost.
     */
    @GameTest(template = "arena", timeoutTicks = 2900)
    public static void aCrewWorksWhileNobodyIsNearAndMoreComeOutWhenAPlayerIs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> {
            antColony(helper).population().setCrewForTests(10);
            antColony(helper).population().addNewMembers(Caste.WORKER, level.getGameTime(), 40);
        });
        helper.runAfterDelay(260, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] nobody near: {}", colony.population().describe(level));
            helper.assertTrue(real == 10, "with nobody near the crew should be out: " + real + " real, "
                    + colony.population().describe(level));
            helper.assertTrue(colony.getCount(Caste.WORKER) == 40 && colony.population().inside(Caste.WORKER) == 30,
                    "40 workers, 30 inside expected: " + colony.getCount(Caste.WORKER) + ", " + colony.population().inside(Caste.WORKER));
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 20, SURFACE + 2, MIDDLE)))));
        });
        helper.runAfterDelay(1000, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] a player near: {}", colony.population().describe(level));
            helper.assertTrue(real == 40 && colony.population().inside(Caste.WORKER) == 0,
                    "with a player near all 40 should be out: " + real + " real, " + colony.population().describe(level));
            colony.population().setViewersForTests(List.of());
        });
        helper.runAfterDelay(2300, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] the player gone: {}", colony.population().describe(level));
            helper.assertTrue(real == 10, "the player gone, back to the crew expected: " + real + " real, "
                    + colony.population().describe(level));
            helper.assertTrue(colony.getCount(Caste.WORKER) == 40
                    && colony.population().inside(Caste.WORKER) + real == 40, "nobody should be lost: " + colony.getCount(Caste.WORKER)
                    + " counted, " + colony.population().inside(Caste.WORKER) + " inside, " + real + " real");
            colony.population().setCrewForTests(null);
        });
        helper.runAfterDelay(2800, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] the species' crew, nobody near: {}", colony.population().describe(level));
            helper.assertTrue(real == 40 && colony.population().inside(Caste.WORKER) == 0 && colony.getCount(Caste.WORKER) == 40,
                    "with the species' crew all 40 should be out with nobody near: " + real + " real, " + colony.population().describe(level));
            helper.succeed();
        });
    }

    /**
     * Insects come out only where no player can see: a player's eyes in the middle of the nest's first room and one up
     * on the surface. Every new real worker must appear out of both players' sight.
     */
    @GameTest(template = "arena", timeoutTicks = 800)
    public static void insectsComeOutOnlyWhereNoPlayerCanSee(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        Set<UUID> seen = new HashSet<>();
        List<Vec3> eyes = new ArrayList<>();
        int[] cameOut = {0};
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            NestPlan.Chamber first = colony.nest().getPlannedChambers(level).get(0);
            eyes.add(Vec3.atCenterOf(first.center()));
            eyes.add(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE + 1, MIDDLE + 3))));
            colony.population().setViewersForTests(eyes);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime(), 20);
        });
        helper.onEachTick(() -> {
            Colony colony = antColony(helper);
            if (colony == null || eyes.isEmpty()) {
                return;
            }
            for (AbstractAntEntity mob : real(helper, colony)) {
                if (seen.add(mob.getUUID())) {
                    cameOut[0]++;
                    helper.assertTrue(Population.outOfSight(level, mob.position().add(0, 0.2, 0), eyes),
                            "a worker came out where a player can see it, at " + mob.blockPosition().toShortString());
                }
            }
        });
        helper.runAfterDelay(700, () -> {
            AntColoniesMod.LOGGER.info("[population-test] {} came out unseen", cameOut[0]);
            helper.assertTrue(cameOut[0] >= 10, "only " + cameOut[0] + " came out");
            helper.succeed();
        });
    }

    /**
     * Workers in the numbers grow old and die like real ones: 20 grew up longer ago than any worker lives, 10 not long
     * ago. Within a few seconds the 20 are dead (the headcount drops to 10, bodies in the refuse chamber), the 10 live on.
     * And the numbers with their ages are saved with the colony.
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void workersInTheNumbersDieOfOldAgeAndAreSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // only the numbers, no swapping
            long lifespan = colony.profile().worker().lifespanTicks();
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - Math.round(lifespan * 1.2), 20);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - 2400, 10);
            Colony copy = Colony.load(colony.save());
            helper.assertTrue(copy.population().inside(Caste.WORKER) == 30 && copy.getCount(Caste.WORKER) == 30,
                    "after a reload the numbers are " + copy.population().inside(Caste.WORKER));
        });
        helper.runAfterDelay(120, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.getCount(Caste.WORKER) == 10 && colony.population().inside(Caste.WORKER) == 10,
                    "the 20 old workers should have died: " + colony.getCount(Caste.WORKER) + " counted, "
                            + colony.population().inside(Caste.WORKER) + " inside");
            helper.assertTrue(!colony.refuse().getRefuseCorpses(level).isEmpty(), "no bodies in the refuse chamber");
            helper.succeed();
        });
    }

    /**
     * A larva that has eaten its fill grows into a worker in the numbers when the colony has all the real workers it
     * wants (a crew of 10, set for the test, nobody near) and nobody can see it; with the brain off it comes out as a real worker as before.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aYoungOneGrowsUpIntoTheNumbersWhenNoMoreRealOnesAreWanted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> {
            antColony(helper).population().setCrewForTests(10);
            antColony(helper).population().addNewMembers(Caste.WORKER, level.getGameTime(), 12);
        });
        helper.runAfterDelay(300, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(real(helper, colony).size() == 10, "the crew is not out: " + colony.population().describe(level));
            BlockPos spot = colony.nest().getNurseryCells(level).get(0);
            AntLarvaEntity larva = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            int before = real(helper, colony).size();
            helper.assertTrue(colony.brood().hatchLarva(level, larva), "the larva did not grow up");
            helper.assertTrue(colony.getCount(Caste.WORKER) == 13 && colony.population().inside(Caste.WORKER) == 3
                    && real(helper, colony).size() == before, "the new worker should be one of the numbers: " + colony.population().describe(level));
            colony.population().setOnForTests(false);
            AntLarvaEntity second = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            helper.assertTrue(colony.brood().hatchLarva(level, second), "the second larva did not grow up");
            helper.assertTrue(real(helper, colony).size() == before + 1 && colony.getCount(Caste.WORKER) == 14,
                    "with the brain off it should come out real: " + real(helper, colony).size() + " real");
            helper.succeed();
        });
    }
}
