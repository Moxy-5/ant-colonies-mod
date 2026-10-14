package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.Population;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.Species;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The brain, part 1 ({@link Population}): most of the colony is numbers inside the nest, a few are real mobs. Game tests
 * have no players, so each test says where pretend players' eyes are ({@link Population#setViewersForTests}).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class PopulationTests {
    /** An ant colony in this test area with its whole nest dug out at once and the brain on; null if there is none yet. */
    private static Colony antColony(GameTestHelper helper) {
        return colony(helper, Species.RED_HARVESTER_ANT);
    }

    private static Colony colony(GameTestHelper helper, net.minecraft.resources.ResourceLocation species) {
        AABB area = helper.getBounds();
        return ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(c -> area.contains(Vec3.atCenterOf(c.getCenter())) && c.getQueen(helper.getLevel()) != null && c.getSpecies().equals(species))
                .findFirst().orElse(null);
    }

    /** Every block of the shaft and of every room of the plan dug out at once. */
    private static void digNestAtOnce(ServerLevel level, Colony colony) {
        colony.nest().unlockAllLevelsForTests();
        BlockPos bottom = colony.nest().getShaftBottom(level);
        for (BlockPos cell = colony.getCenter().below(); bottom != null && cell.getY() >= bottom.getY(); cell = cell.below()) {
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
        }
        List<NestPlan.Chamber> rooms = new ArrayList<>(colony.nest().getPlannedChambers(level));
        rooms.addAll(colony.nest().getSideRooms(level));
        rooms.addAll(colony.nest().getGardens(level));
        if (colony.nest().getFoundingChamber(level) != null) {
            rooms.add(colony.nest().getFoundingChamber(level));
        }
        for (NestPlan.Chamber room : rooms) {
            for (BlockPos cell : room.cells()) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** This colony's real workers and soldiers that may go into the numbers, in the test area. */
    private static List<AbstractAntEntity> real(GameTestHelper helper, Colony colony) {
        return helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds().inflate(16),
                mob -> mob.isAlive() && colony.getId().equals(mob.getColonyId()) && mob.nestCaste() != null);
    }

    /** An ant queen on deep earth; after 10 ticks her colony has its nest dug, no hunger and the brain on, with nobody watching. */
    private static void startAntColony(GameTestHelper helper) {
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.QUEEN_ANT.get(), MIDDLE, SURFACE, MIDDLE);
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
     * With nobody near, a crew of 10 real workers comes out of the 40 in the numbers, two a second, and the headcount
     * stays 40. A player near (eyes 20 blocks off, above ground): all 40 come out (fewer than the cap of 50). The player
     * gone: they go back in down to the crew again. Nothing is lost on the way.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aCrewWorksWhileNobodyIsNearAndMoreComeOutWhenAPlayerIs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> antColony(helper).population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 40));
        helper.runAfterDelay(260, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] nobody near: {}", colony.population().describe(level));
            helper.assertTrue(real == colony.profile().colony().crew(), "with nobody near the crew should be out: " + real + " real, "
                    + colony.population().describe(level));
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 40 && colony.population().inside(WorkerAntEntity.CASTE) == 30,
                    "40 workers, 30 inside expected: " + colony.getCount(WorkerAntEntity.CASTE) + ", " + colony.population().inside(WorkerAntEntity.CASTE));
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 20, SURFACE + 2, MIDDLE)))));
        });
        helper.runAfterDelay(1000, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] a player near: {}", colony.population().describe(level));
            helper.assertTrue(real == 40 && colony.population().inside(WorkerAntEntity.CASTE) == 0,
                    "with a player near all 40 should be out: " + real + " real, " + colony.population().describe(level));
            colony.population().setViewersForTests(List.of());
        });
        helper.runAfterDelay(2300, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] the player gone: {}", colony.population().describe(level));
            helper.assertTrue(real == colony.profile().colony().crew(), "the player gone, back to the crew expected: " + real + " real, "
                    + colony.population().describe(level));
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 40
                    && colony.population().inside(WorkerAntEntity.CASTE) + real == 40, "nobody should be lost: " + colony.getCount(WorkerAntEntity.CASTE)
                    + " counted, " + colony.population().inside(WorkerAntEntity.CASTE) + " inside, " + real + " real");
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
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 20);
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
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - Math.round(lifespan * 1.2), 20);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 2400, 10);
            Colony copy = Colony.load(colony.save());
            helper.assertTrue(copy.population().inside(WorkerAntEntity.CASTE) == 30 && copy.getCount(WorkerAntEntity.CASTE) == 30,
                    "after a reload the numbers are " + copy.population().inside(WorkerAntEntity.CASTE));
        });
        helper.runAfterDelay(120, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 10 && colony.population().inside(WorkerAntEntity.CASTE) == 10,
                    "the 20 old workers should have died: " + colony.getCount(WorkerAntEntity.CASTE) + " counted, "
                            + colony.population().inside(WorkerAntEntity.CASTE) + " inside");
            helper.assertTrue(!colony.refuse().getRefuseCorpses(level).isEmpty(), "no bodies in the refuse chamber");
            helper.succeed();
        });
    }

    /**
     * A larva that has eaten its fill grows into a worker in the numbers when the colony has all the real workers it
     * wants (the crew, nobody near) and nobody can see it; with the brain off it comes out as a real worker as before.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aYoungOneGrowsUpIntoTheNumbersWhenNoMoreRealOnesAreWanted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> antColony(helper).population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 12));
        helper.runAfterDelay(300, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(real(helper, colony).size() == colony.profile().colony().crew(), "the crew is not out: " + colony.population().describe(level));
            BlockPos spot = colony.nest().getNurseryCells(level).get(0);
            AntLarvaEntity larva = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            int before = real(helper, colony).size();
            helper.assertTrue(colony.brood().hatchLarva(level, larva), "the larva did not grow up");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 13 && colony.population().inside(WorkerAntEntity.CASTE) == 3
                    && real(helper, colony).size() == before, "the new worker should be one of the numbers: " + colony.population().describe(level));
            colony.population().setOnForTests(false);
            AntLarvaEntity second = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            helper.assertTrue(colony.brood().hatchLarva(level, second), "the second larva did not grow up");
            helper.assertTrue(real(helper, colony).size() == before + 1 && colony.getCount(WorkerAntEntity.CASTE) == 14,
                    "with the brain off it should come out real: " + real(helper, colony).size() + " real");
            helper.succeed();
        });
    }

    /**
     * A big termite colony: 200 workers, minors and majors in the numbers, a player near. Real termites come out, never
     * more than the cap of 50, and no more soldiers than they have posts for (plus none outside by day); some of the real
     * ones are termite workers. The headcount stays.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void aBigTermiteColonyShowsNoMoreThanTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        int[] counted = {0};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            digNestAtOnce(level, colony);
            colony.setForagingTimeForTests(false);
            colony.population().setOnForTests(true);
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 20, SURFACE + 2, MIDDLE)))));
            long now = level.getGameTime();
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - 100000, 200);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), now, 15);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), now, 5);
            counted[0] = colony.getMemberCount();
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony != null && counted[0] > 0) {
                int real = real(helper, colony).size();
                helper.assertTrue(real <= colony.profile().colony().realCap(), real + " real termites, more than the cap");
            }
        });
        helper.runAfterDelay(1500, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            List<AbstractAntEntity> real = real(helper, colony);
            AntColoniesMod.LOGGER.info("[population-test] big termite colony: {} | {}", colony.population().describe(level), colony.soldiers().describe(level));
            long workers = real.stream().filter(mob -> mob instanceof TermiteWorkerEntity).count();
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                long soldiers = real.stream().filter(mob -> mob instanceof TermiteSoldierEntity s && s.getRank() == rank).count();
                int posts = colony.soldiers().posts(level, rank).size();
                helper.assertTrue(soldiers <= posts, soldiers + " real " + rank + " soldiers for " + posts + " posts");
            }
            helper.assertTrue(workers >= 20, "only " + workers + " real termite workers came out: " + colony.population().describe(level));
            int inside = colony.population().inside(WorkerAntEntity.CASTE);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == inside + workers, "the headcount " + colony.getCount(WorkerAntEntity.CASTE)
                    + " is not the " + inside + " inside and the " + workers + " real workers");
            helper.succeed();
        });
    }
}
