package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Brood;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyParts;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    static Colony antColony(GameTestHelper helper) {
        return colony(helper, Species.RED_HARVESTER_ANT);
    }

    static Colony colony(GameTestHelper helper, net.minecraft.resources.ResourceLocation species) {
        AABB area = helper.getBounds();
        return ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(c -> area.contains(Vec3.atCenterOf(c.getCenter())) && c.getQueen(helper.getLevel()) != null && c.getSpecies().equals(species))
                .findFirst().orElse(null);
    }

    /** Every block of the shaft and of every room of the plan dug out at once. */
    static void digNestAtOnce(ServerLevel level, Colony colony) {
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
    static List<AbstractAntEntity> real(GameTestHelper helper, Colony colony) {
        return helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds().inflate(16),
                mob -> mob.isAlive() && colony.getId().equals(mob.getColonyId()) && mob.nestCaste() != null);
    }

    /** An ant queen on deep earth; after 10 ticks her colony has its nest dug, no hunger and the brain on, with nobody watching. */
    static void startAntColony(GameTestHelper helper) {
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
            antColony(helper).population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 40);
        });
        helper.runAfterDelay(260, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] nobody near: {}", colony.population().describe(level));
            helper.assertTrue(real == 10, "with nobody near the crew should be out: " + real + " real, "
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
            helper.assertTrue(real == 10, "the player gone, back to the crew expected: " + real + " real, "
                    + colony.population().describe(level));
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 40
                    && colony.population().inside(WorkerAntEntity.CASTE) + real == 40, "nobody should be lost: " + colony.getCount(WorkerAntEntity.CASTE)
                    + " counted, " + colony.population().inside(WorkerAntEntity.CASTE) + " inside, " + real + " real");
            colony.population().setCrewForTests(null);
        });
        helper.runAfterDelay(2800, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] the species' crew, nobody near: {}", colony.population().describe(level));
            helper.assertTrue(real == 40 && colony.population().inside(WorkerAntEntity.CASTE) == 0 && colony.getCount(WorkerAntEntity.CASTE) == 40,
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
     * wants (a crew of 10, set for the test, nobody near) and nobody can see it; with the brain off it comes out as a real worker as before.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aYoungOneGrowsUpIntoTheNumbersWhenNoMoreRealOnesAreWanted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> {
            antColony(helper).population().setCrewForTests(10);
            antColony(helper).population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 12);
        });
        helper.runAfterDelay(300, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(real(helper, colony).size() == 10, "the crew is not out: " + colony.population().describe(level));
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
            TestClock.still(helper, 12.0); // midday: the foragers stay in
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
            AntColoniesMod.LOGGER.info("[population-test] big termite colony: {} | {}", colony.population().describe(level), colony.part(ColonyParts.SOLDIERS).orElseThrow().describe(level));
            long workers = real.stream().filter(mob -> mob instanceof TermiteWorkerEntity).count();
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                long soldiers = real.stream().filter(mob -> mob instanceof TermiteSoldierEntity s && s.getRank() == rank).count();
                int posts = colony.part(ColonyParts.SOLDIERS).orElseThrow().posts(level, rank).size();
                helper.assertTrue(soldiers <= posts, soldiers + " real " + rank + " soldiers for " + posts + " posts");
            }
            helper.assertTrue(workers >= 20, "only " + workers + " real termite workers came out: " + colony.population().describe(level));
            int inside = colony.population().inside(WorkerAntEntity.CASTE);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == inside + workers, "the headcount " + colony.getCount(WorkerAntEntity.CASTE)
                    + " is not the " + inside + " inside and the " + workers + " real workers");
            helper.succeed();
        });
    }

    // --- termite workers eating (the step after 2b) -----------------------------------------------------------------

    /**
     * Real Macrotermes worker eating. A young termite worker (nurse age) and an old one, both near starving: while the
     * colony has had no comb their hunger clocks do not run, so neither starves. Then comb with nodules in the garden
     * and both hungry (the comb's ageing standing still meanwhile): the young one eats a fungus nodule, the old one a
     * layer of old comb off the bottom (never the fresh top or the comb's bed). Then
     * 100 worker numbers (40 young, 60 old) whose meal is due: the young share eat nodules, the old ones comb, one piece
     * making 4 meals, so about 10 nodules and 15 layers, and nobody starves. Then mouth to mouth: a hungry king; a nurse
     * eats a nodule at the comb and carries it in its crop (nothing in its jaws), walks to him and feeds him head to head.
     * Then the comb's ages (the conveyor), sped up: forage put on an aged comb goes on top as a fresh layer; a comb of 4
     * fresh layers ripens, goes old (nodules grow from its ripe layers) and crumbles away from the bottom.
     */
    @GameTest(template = "arena", timeoutTicks = 4800)
    public static void termiteWorkersEatNodulesWhenYoungAndOldCombWhenOlder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        TermiteWorkerEntity[] workers = new TermiteWorkerEntity[2]; // young, old
        int[] phase = {0};
        long[] phaseAt = {0};
        int[] before = new int[2];
        com.antcolonies.entity.TermiteKingEntity[] king = new com.antcolonies.entity.TermiteKingEntity[1];
        boolean[] sawCrop = {false};
        long[] kingHungrySince = {0};
        BlockPos[] ageing = new BlockPos[1];
        boolean[] sawRipe = {false};
        boolean[] sawOld = {false};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            TestClock.still(helper, 12.0); // midday: the foragers stay in
            colony.setHungerOn(true);
            TermiteSoldierTests.digNestAtOnce(level, colony);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 2; i++) {
                TermiteWorkerEntity worker = ModEntities.TERMITE_WORKER.get().create(level);
                BlockPos cell = room.get(room.size() - 1 - i);
                worker.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0.0F, 0.0F);
                level.addFreshEntity(worker);
                workers[i] = worker;
            }
        });
        helper.runAfterDelay(15, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            long now = level.getGameTime();
            workers[0].setGrownUpAt(now);
            workers[1].setGrownUpAt(now - 250000);
            for (TermiteWorkerEntity worker : workers) {
                helper.assertTrue(colony.getId().equals(worker.getColonyId()), "a worker did not join the colony");
                worker.setLastAte(now - colony.profile().worker().starveAfterTicks() + 40); // two seconds from starving
            }
            helper.assertTrue(!colony.food().hasHadFood(), "the new colony should have had no comb yet");
            phase[0] = 1;
            phaseAt[0] = now;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            long now = level.getGameTime();
            var food = com.antcolonies.species.SpeciesProfile.MealFood.values();
            if (phase[0] == 1 && now - phaseAt[0] >= 80) {
                for (TermiteWorkerEntity worker : workers) {
                    helper.assertTrue(worker.isAlive() && !worker.isHungry(), "with no comb yet the workers should not get hungry");
                }
                // The garden gets comb (4 layers a block) with 3 nodules on each.
                for (int i = 0; i < 40; i++) {
                    BlockPos spot = colony.food().pickFoodSpot(level, level.getRandom());
                    if (spot == null) {
                        break;
                    }
                    colony.food().depositSeed(level, spot);
                }
                for (BlockPos comb : colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getCombs(level)) {
                    // Aged comb: 2 old layers at the bottom, a ripe one, a fresh one on top; 3 nodules.
                    level.setBlock(comb, level.getBlockState(comb).setValue(com.antcolonies.block.FungusCombBlock.NODULES, 3)
                            .setValue(com.antcolonies.block.FungusCombBlock.OLD, 2).setValue(com.antcolonies.block.FungusCombBlock.FRESH, 1), 3);
                }
                helper.assertTrue(colony.food().getFood() >= 24, "only " + colony.food().getFood() + " layers of comb could be put down");
                // The comb's ageing stands still while the eating is checked: in the 10 to 20 seconds the two take to eat,
                // a fresh top sometimes ripened by itself, as real comb does (docs/BUGS.md 27). The ageing is checked below.
                colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(0.0F);
                for (TermiteWorkerEntity worker : workers) {
                    worker.setLastAte(now - colony.profile().worker().mealIntervalTicks() - 5);
                }
                before[0] = colony.workerFood().getPiecesEaten(food[0]);
                before[1] = colony.workerFood().getPiecesEaten(food[1]);
                phase[0] = 2;
                phaseAt[0] = now;
            } else if (phase[0] == 2 && !workers[0].isHungry() && !workers[1].isHungry()) {
                helper.assertTrue(workers[0].isAlive() && workers[1].isAlive(), "a worker died");
                int combEaten = colony.workerFood().getPiecesEaten(food[0]) - before[0];
                int nodulesEaten = colony.workerFood().getPiecesEaten(food[1]) - before[1];
                helper.assertTrue(combEaten == 1 && nodulesEaten == 1, "one nodule and one layer of comb should have been started on: "
                        + nodulesEaten + " nodules, " + combEaten + " comb");
                for (BlockPos comb : colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getCombs(level)) {
                    helper.assertTrue(com.antcolonies.block.FungusCombBlock.fresh(level.getBlockState(comb)) >= 1,
                            "a comb lost its fresh top (old comb is eaten from the bottom): " + level.getBlockState(comb));
                }
                colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(1.0F);
                // The worker numbers: 40 young, 60 old, all due a meal.
                colony.population().addNewMembers(WorkerAntEntity.CASTE, now, 40);
                colony.population().addNewMembers(WorkerAntEntity.CASTE, now - 250000, 60);
                colony.population().setLastAteForTests(level, WorkerAntEntity.CASTE, now - colony.profile().worker().mealIntervalTicks() - 30);
                before[0] = colony.workerFood().getPiecesEaten(food[0]);
                before[1] = colony.workerFood().getPiecesEaten(food[1]);
                phase[0] = 3;
                phaseAt[0] = now;
            } else if (phase[0] == 3 && now - phaseAt[0] >= 60) {
                int comb = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.STORE) - before[0];
                int nodules = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.NURSE_FOOD) - before[1];
                AntColoniesMod.LOGGER.info("[eat-test] 100 worker numbers ate {} layers of comb and {} nodules; {}", comb, nodules,
                        colony.population().describe(level));
                helper.assertTrue(comb >= 13 && comb <= 16, "the 60 old worker numbers should eat about 15 layers of comb, ate " + comb);
                helper.assertTrue(nodules >= 8 && nodules <= 11, "the 40 young worker numbers should eat about 10 nodules, ate " + nodules);
                helper.assertTrue(colony.population().inside(WorkerAntEntity.CASTE) == 100, "a worker number starved: "
                        + colony.population().inside(WorkerAntEntity.CASTE));
                // Mouth to mouth: the king comes and joins the queen.
                AbstractAntEntity queen = colony.getQueen(level);
                king[0] = ModEntities.TERMITE_KING.get().create(level);
                king[0].moveTo(queen.getX(), queen.getY(), queen.getZ(), 0.0F, 0.0F);
                level.addFreshEntity(king[0]);
                phase[0] = 4;
                phaseAt[0] = now;
            } else if (phase[0] == 4 && colony.getId().equals(king[0].getColonyId()) && now - phaseAt[0] >= 40) {
                for (TermiteWorkerEntity worker : workers) {
                    worker.setLastAte(now); // (they ate: so nothing but the feeding goes on)
                }
                kingHungrySince[0] = now - colony.profile().worker().mealIntervalTicks() - 5;
                king[0].setLastFed(kingHungrySince[0]);
                phase[0] = 5;
                phaseAt[0] = now;
            } else if (phase[0] == 5) {
                for (TermiteWorkerEntity worker : workers) {
                    if (worker.isFoodInCrop()) {
                        sawCrop[0] = true;
                        helper.assertTrue(worker.getCarriedSeed().is(com.antcolonies.registry.ModItems.FUNGUS_NODULE.get()),
                                "a nurse has something else than a nodule in its crop: " + worker.getCarriedSeed());
                    }
                }
                if (king[0].getLastFed() > kingHungrySince[0]) {
                    helper.assertTrue(sawCrop[0], "the king was fed, but no nurse carried a nodule in its crop");
                    double nearest = Double.MAX_VALUE;
                    for (TermiteWorkerEntity worker : workers) {
                        nearest = Math.min(nearest, worker.headPoint().distanceTo(king[0].headPoint()));
                    }
                    helper.assertTrue(nearest < 0.9, "the nurse fed the king, but not head to head: its head was " + nearest + " from his");
                    // The conveyor: forage on an aged comb goes on top, fresh.
                    com.antcolonies.block.FungusCombBlock combBlock = com.antcolonies.registry.ModBlocks.FUNGUS_COMB.get();
                    BlockPos aged = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getCombs(level).stream()
                            .filter(pos -> level.getBlockState(pos).getValue(com.antcolonies.block.SeedPileBlock.LAYERS) < 4
                                    && com.antcolonies.block.FungusCombBlock.old(level.getBlockState(pos)) > 0
                                    && !colony.brood().getBroodCells(level).contains(pos))
                            .findFirst().orElse(null);
                    if (aged != null) {
                        var was = level.getBlockState(aged);
                        // A worker standing in the comb's cell is lifted onto the new layer, never left sunk in the comb
                        // (docs/BUGS.md 48: one was, and could not step up out of it).
                        TermiteWorkerEntity standing = workers[0];
                        standing.setPos(aged.getX() + 0.5, aged.getY() + com.antcolonies.block.FungusCombBlock.topOf(was), aged.getZ() + 0.5);
                        helper.assertTrue(colony.food().depositSeed(level, aged), "could not put forage on the comb at " + aged);
                        var now2 = level.getBlockState(aged);
                        helper.assertTrue(standing.getY() >= aged.getY() + com.antcolonies.block.FungusCombBlock.topOf(now2) - 1.0E-6,
                                "a worker on the comb was left sunk in it at " + standing.getY() + ", its top at "
                                        + (aged.getY() + com.antcolonies.block.FungusCombBlock.topOf(now2)));
                        helper.assertTrue(com.antcolonies.block.FungusCombBlock.fresh(now2) == com.antcolonies.block.FungusCombBlock.fresh(was) + 1
                                && com.antcolonies.block.FungusCombBlock.old(now2) == com.antcolonies.block.FungusCombBlock.old(was),
                                "new forage should go on top as a fresh layer: " + was + " -> " + now2);
                    }
                    // A comb of 4 fresh layers, ageing fast.
                    ageing[0] = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getCombs(level).stream().filter(pos -> !colony.brood().getBroodCells(level).contains(pos))
                            .findFirst().orElseThrow();
                    level.setBlock(ageing[0], combBlock.defaultBlockState().setValue(com.antcolonies.block.SeedPileBlock.LAYERS, 4)
                            .setValue(com.antcolonies.block.FungusCombBlock.FRESH, 4), 3);
                    colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(400.0F);
                    phase[0] = 6;
                    phaseAt[0] = now;
                }
            } else if (phase[0] == 6) {
                var state = level.getBlockState(ageing[0]);
                boolean comb = state.getBlock() instanceof com.antcolonies.block.FungusCombBlock;
                if (comb) {
                    sawRipe[0] |= com.antcolonies.block.FungusCombBlock.ripe(state) > 0;
                    sawOld[0] |= com.antcolonies.block.FungusCombBlock.old(state) > 0;
                    // The bands stay in order: old at the bottom, fresh on top (never more than the layers).
                    helper.assertTrue(com.antcolonies.block.FungusCombBlock.old(state) + com.antcolonies.block.FungusCombBlock.fresh(state)
                            <= com.antcolonies.block.FungusCombBlock.layers(state), "a comb with more ages than layers: " + state);
                }
                if (!comb || com.antcolonies.block.FungusCombBlock.layers(state) < 4) {
                    helper.assertTrue(sawRipe[0], "the fresh comb never ripened before it crumbled");
                    helper.assertTrue(sawOld[0], "the comb never went old before it crumbled");
                    colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(1.0F);
                    helper.succeed();
                }
            }
        });
    }
}
