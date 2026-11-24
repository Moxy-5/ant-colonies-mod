package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

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
 * The termites' scenes; the crew, coming out unseen, old age and growing up into the numbers were the red harvester
 * ant's tests until it left the mod (2026-11-23) and run on a termite colony since: the rules are the shared brain's.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermitePopulationTests {
    /**
     * A big termite colony: 200 workers, minors and majors in the numbers, a player near. Real termites come out, never
     * more than the cap of 50, and no more soldiers than they have posts for (plus none outside by day); some of the real
     * ones are termite workers. The headcount stays.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void aBigTermiteColonyShowsNoMoreThanTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int[] counted = {0};
        TermiteScenes.colony(helper, List.of(), colony -> {
            helper.assertTrue(colony != null, "no termite colony");
            digNestAtOnce(level, colony);
            TestClock.still(helper, 12.0); // midday: the foragers stay in
            colony.population().setOnForTests(true);
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 20, SURFACE + 2, MIDDLE)))));
            long now = level.getGameTime();
            colony.population().addNewMembers(Caste.WORKER, now - 100000, 200);
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
            SavannaMod.LOGGER.info("[population-test] big termite colony: {} | {}", colony.population().describe(level), colony.part(SavannaParts.SOLDIERS).orElseThrow().describe(level));
            long workers = real.stream().filter(mob -> mob instanceof TermiteWorkerEntity).count();
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                long soldiers = real.stream().filter(mob -> mob instanceof TermiteSoldierEntity s && s.getRank() == rank).count();
                int posts = colony.part(SavannaParts.SOLDIERS).orElseThrow().posts(level, rank).size();
                helper.assertTrue(soldiers <= posts, soldiers + " real " + rank + " soldiers for " + posts + " posts");
            }
            helper.assertTrue(workers >= 20, "only " + workers + " real termite workers came out: " + colony.population().describe(level));
            int inside = colony.population().inside(Caste.WORKER);
            helper.assertTrue(colony.getCount(Caste.WORKER) == inside + workers, "the headcount " + colony.getCount(Caste.WORKER)
                    + " is not the " + inside + " inside and the " + workers + " real workers");
            helper.succeed();
        });
    }

    // --- the shared brain's rules, on a termite colony (the harvester's tests until 2026-11-23) -----------------------

    /** A termite colony with its nest dug at once, no hunger, the brain on and nobody watching, at a still midday (the foragers stay in). */
    private static void dugNest(GameTestHelper helper, java.util.function.Consumer<Colony> then) {
        TermiteScenes.colony(helper, List.of(), colony -> {
            helper.assertTrue(colony != null, "no termite colony");
            digNestAtOnce(helper.getLevel(), colony);
            TestClock.still(helper, 12.0);
            colony.setHungerOn(false);
            colony.population().setOnForTests(true);
            colony.population().setViewersForTests(List.of());
            then.accept(colony);
        });
    }

    /**
     * With a small crew (10, set for the test) and nobody near, 10 real workers come out of the 40 in the numbers, two a
     * second, and the headcount stays 40. A player near (eyes 20 blocks off, above ground): all 40 come out (fewer than
     * the cap of 50). The player gone: they go back in down to the crew again. Then the species' own crew (the whole cap,
     * the user's decision: a loaded colony works as before the brain): all 40 come out with nobody near. Nothing is lost.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aCrewWorksWhileNobodyIsNearAndMoreComeOutWhenAPlayerIs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony[] found = {null};
        long[] start = {-1};
        dugNest(helper, colony -> {
            colony.population().setCrewForTests(10);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime(), 40);
            found[0] = colony;
            start[0] = helper.getTick();
        });
        boolean[] done = new boolean[4];
        helper.onEachTick(() -> {
            Colony colony = found[0];
            if (colony == null) {
                return;
            }
            long since = helper.getTick() - start[0];
            int real = (int) real(helper, colony).stream().filter(mob -> mob instanceof TermiteWorkerEntity).count();
            if (!done[0] && since >= 250) {
                done[0] = true;
                SavannaMod.LOGGER.info("[population-test] nobody near: {}", colony.population().describe(level));
                helper.assertTrue(real == 10, "with nobody near the crew should be out: " + real + " real, " + colony.population().describe(level));
                helper.assertTrue(colony.getCount(Caste.WORKER) == 40 && colony.population().inside(Caste.WORKER) == 30,
                        "40 workers, 30 inside expected: " + colony.getCount(Caste.WORKER) + ", " + colony.population().inside(Caste.WORKER));
                colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 20, SURFACE + 2, MIDDLE)))));
            } else if (!done[1] && since >= 990) {
                done[1] = true;
                SavannaMod.LOGGER.info("[population-test] a player near: {}", colony.population().describe(level));
                helper.assertTrue(real == 40 && colony.population().inside(Caste.WORKER) == 0,
                        "with a player near all 40 should be out: " + real + " real, " + colony.population().describe(level));
                colony.population().setViewersForTests(List.of());
            } else if (!done[2] && since >= 2290) {
                done[2] = true;
                SavannaMod.LOGGER.info("[population-test] the player gone: {}", colony.population().describe(level));
                helper.assertTrue(real == 10, "the player gone, back to the crew expected: " + real + " real, " + colony.population().describe(level));
                helper.assertTrue(colony.getCount(Caste.WORKER) == 40 && colony.population().inside(Caste.WORKER) + real == 40,
                        "nobody should be lost: " + colony.getCount(Caste.WORKER) + " counted, " + colony.population().inside(Caste.WORKER)
                                + " inside, " + real + " real");
                colony.population().setCrewForTests(null);
            } else if (!done[3] && since >= 2790) {
                done[3] = true;
                SavannaMod.LOGGER.info("[population-test] the species' crew, nobody near: {}", colony.population().describe(level));
                helper.assertTrue(real == 40 && colony.population().inside(Caste.WORKER) == 0 && colony.getCount(Caste.WORKER) == 40,
                        "with the species' crew all 40 should be out with nobody near: " + real + " real, " + colony.population().describe(level));
                helper.succeed();
            }
        });
    }

    /**
     * Insects come out only where no player can see: a player's eyes in the middle of the nest's first room and one up
     * on the surface. Every new real worker must appear out of both players' sight.
     */
    @GameTest(template = "arena", timeoutTicks = 900, skyAccess = true)
    public static void insectsComeOutOnlyWhereNoPlayerCanSee(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
        List<Vec3> eyes = new java.util.ArrayList<>();
        int[] cameOut = {0};
        Colony[] found = {null};
        long[] start = {-1};
        dugNest(helper, colony -> {
            com.antcolonies.colony.NestPlan.Chamber first = colony.nest().getPlannedChambers(level).get(0);
            eyes.add(Vec3.atCenterOf(first.center()));
            eyes.add(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE + 1, MIDDLE + 3))));
            colony.population().setViewersForTests(eyes);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime(), 20);
            found[0] = colony;
            start[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            Colony colony = found[0];
            if (colony == null) {
                return;
            }
            for (AbstractAntEntity mob : real(helper, colony)) {
                if (mob instanceof TermiteWorkerEntity && seen.add(mob.getUUID())) {
                    cameOut[0]++;
                    helper.assertTrue(com.antcolonies.colony.Population.outOfSight(level, mob.position().add(0, 0.2, 0), eyes),
                            "a worker came out where a player can see it, at " + mob.blockPosition().toShortString());
                }
            }
            if (helper.getTick() - start[0] >= 700) {
                SavannaMod.LOGGER.info("[population-test] {} came out unseen", cameOut[0]);
                helper.assertTrue(cameOut[0] >= 10, "only " + cameOut[0] + " came out");
                helper.succeed();
            }
        });
    }

    /**
     * Workers in the numbers grow old and die like real ones: 20 grew up longer ago than any worker lives, 10 not long
     * ago. Within a few seconds the 20 are dead (the headcount drops to 10), the 10 live on. And the numbers with their
     * ages are saved with the colony.
     */
    @GameTest(template = "arena", timeoutTicks = 500, skyAccess = true)
    public static void workersInTheNumbersDieOfOldAgeAndAreSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony[] found = {null};
        long[] start = {-1};
        dugNest(helper, colony -> {
            colony.population().setOnForTests(false); // only the numbers, no swapping
            long lifespan = colony.profile().worker().lifespanTicks();
            helper.assertTrue(lifespan > 0, "termite workers should die of old age");
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - Math.round(lifespan * 1.2), 20);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - 2400, 10);
            Colony copy = Colony.load(colony.save());
            helper.assertTrue(copy.population().inside(Caste.WORKER) == 30 && copy.getCount(Caste.WORKER) == 30,
                    "after a reload the numbers are " + copy.population().inside(Caste.WORKER));
            found[0] = colony;
            start[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            Colony colony = found[0];
            if (colony != null && helper.getTick() - start[0] >= 110) {
                helper.assertTrue(colony.getCount(Caste.WORKER) == 10 && colony.population().inside(Caste.WORKER) == 10,
                        "the 20 old workers should have died: " + colony.getCount(Caste.WORKER) + " counted, "
                                + colony.population().inside(Caste.WORKER) + " inside");
                helper.succeed();
            }
        });
    }

    /**
     * A nymph that has eaten its fill grows into a worker in the numbers when the colony has all the real workers it
     * wants (a crew of 10, set for the test, nobody near) and nobody can see it; with the brain off it comes out as a
     * real worker as before.
     */
    @GameTest(template = "arena", timeoutTicks = 700, skyAccess = true)
    public static void aYoungOneGrowsUpIntoTheNumbersWhenNoMoreRealOnesAreWanted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony[] found = {null};
        long[] start = {-1};
        dugNest(helper, colony -> {
            colony.population().setCrewForTests(10);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime(), 12);
            found[0] = colony;
            start[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            Colony colony = found[0];
            if (colony == null || helper.getTick() - start[0] < 290) {
                return;
            }
            java.util.function.IntSupplier realWorkers = () -> (int) real(helper, colony).stream().filter(mob -> mob instanceof TermiteWorkerEntity).count();
            helper.assertTrue(realWorkers.getAsInt() == 10, "the crew is not out: " + colony.population().describe(level));
            BlockPos spot = java.util.Objects.requireNonNull(colony.brood().pickEggSpot(level, level.getRandom()), "no room for a nymph");
            com.antcolonies.entity.AntLarvaEntity larva = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            int before = realWorkers.getAsInt();
            helper.assertTrue(colony.brood().hatchLarva(level, larva), "the nymph did not grow up");
            helper.assertTrue(colony.getCount(Caste.WORKER) == 13 && colony.population().inside(Caste.WORKER) == 3
                    && realWorkers.getAsInt() == before, "the new worker should be one of the numbers: " + colony.population().describe(level));
            colony.population().setOnForTests(false);
            com.antcolonies.entity.AntLarvaEntity second = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            helper.assertTrue(colony.brood().hatchLarva(level, second), "the second nymph did not grow up");
            helper.assertTrue(realWorkers.getAsInt() == before + 1 && colony.getCount(Caste.WORKER) == 14,
                    "with the brain off it should come out real: " + realWorkers.getAsInt() + " real");
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
        TermiteWorkerEntity[] workers = new TermiteWorkerEntity[2]; // young, old
        int[] phase = {0};
        long[] phaseAt = {0};
        int[] before = new int[2];
        com.antcolonies.savanna.entity.TermiteKingEntity[] king = new com.antcolonies.savanna.entity.TermiteKingEntity[1];
        boolean[] sawCrop = {false};
        long[] kingHungrySince = {0};
        BlockPos[] ageing = new BlockPos[1];
        boolean[] sawRipe = {false};
        boolean[] sawOld = {false};
        TermiteScenes.colony(helper, List.of(), colony -> {
            helper.assertTrue(colony != null, "no termite colony");
            TestClock.still(helper, 12.0); // midday: the foragers stay in
            colony.setHungerOn(true);
            TermiteScenes.digNestAtOnce(level, colony);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 2; i++) {
                TermiteWorkerEntity worker = SavannaEntities.TERMITE_WORKER.get().create(level);
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
                for (BlockPos comb : colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getCombs(level)) {
                    // Aged comb: 2 old layers at the bottom, a ripe one, a fresh one on top; 3 nodules.
                    level.setBlock(comb, level.getBlockState(comb).setValue(com.antcolonies.savanna.block.FungusCombBlock.NODULES, 3)
                            .setValue(com.antcolonies.savanna.block.FungusCombBlock.OLD, 2).setValue(com.antcolonies.savanna.block.FungusCombBlock.FRESH, 1), 3);
                }
                helper.assertTrue(colony.food().getFood() >= 24, "only " + colony.food().getFood() + " layers of comb could be put down");
                // The comb's ageing stands still while the eating is checked: in the 10 to 20 seconds the two take to eat,
                // a fresh top sometimes ripened by itself, as real comb does (docs/BUGS.md 27). The ageing is checked below.
                colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(0.0F);
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
                for (BlockPos comb : colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getCombs(level)) {
                    helper.assertTrue(com.antcolonies.savanna.block.FungusCombBlock.fresh(level.getBlockState(comb)) >= 1,
                            "a comb lost its fresh top (old comb is eaten from the bottom): " + level.getBlockState(comb));
                }
                colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(1.0F);
                // The worker numbers: 40 young, 60 old, all due a meal.
                colony.population().addNewMembers(Caste.WORKER, now, 40);
                colony.population().addNewMembers(Caste.WORKER, now - 250000, 60);
                colony.population().setLastAteForTests(level, Caste.WORKER, now - colony.profile().worker().mealIntervalTicks() - 30);
                before[0] = colony.workerFood().getPiecesEaten(food[0]);
                before[1] = colony.workerFood().getPiecesEaten(food[1]);
                phase[0] = 3;
                phaseAt[0] = now;
            } else if (phase[0] == 3 && now - phaseAt[0] >= 60) {
                int comb = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.STORE) - before[0];
                int nodules = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.NURSE_FOOD) - before[1];
                SavannaMod.LOGGER.info("[eat-test] 100 worker numbers ate {} layers of comb and {} nodules; {}", comb, nodules,
                        colony.population().describe(level));
                helper.assertTrue(comb >= 13 && comb <= 16, "the 60 old worker numbers should eat about 15 layers of comb, ate " + comb);
                helper.assertTrue(nodules >= 8 && nodules <= 11, "the 40 young worker numbers should eat about 10 nodules, ate " + nodules);
                helper.assertTrue(colony.population().inside(Caste.WORKER) == 100, "a worker number starved: "
                        + colony.population().inside(Caste.WORKER));
                // Mouth to mouth: the king comes and joins the queen.
                AbstractAntEntity queen = colony.getQueen(level);
                king[0] = SavannaEntities.TERMITE_KING.get().create(level);
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
                        helper.assertTrue(worker.getCarriedSeed().is(com.antcolonies.savanna.registry.SavannaItems.FUNGUS_NODULE.get()),
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
                    com.antcolonies.savanna.block.FungusCombBlock combBlock = com.antcolonies.savanna.registry.SavannaBlocks.FUNGUS_COMB.get();
                    BlockPos aged = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getCombs(level).stream()
                            .filter(pos -> level.getBlockState(pos).getValue(com.antcolonies.block.SeedPileBlock.LAYERS) < 4
                                    && com.antcolonies.savanna.block.FungusCombBlock.old(level.getBlockState(pos)) > 0
                                    && !colony.brood().getBroodCells(level).contains(pos))
                            .findFirst().orElse(null);
                    if (aged != null) {
                        var was = level.getBlockState(aged);
                        // A worker standing in the comb's cell is lifted onto the new layer, never left sunk in the comb
                        // (docs/BUGS.md 48: one was, and could not step up out of it).
                        TermiteWorkerEntity standing = workers[0];
                        standing.setPos(aged.getX() + 0.5, aged.getY() + com.antcolonies.savanna.block.FungusCombBlock.topOf(was), aged.getZ() + 0.5);
                        helper.assertTrue(colony.food().depositSeed(level, aged), "could not put forage on the comb at " + aged);
                        var now2 = level.getBlockState(aged);
                        helper.assertTrue(standing.getY() >= aged.getY() + com.antcolonies.savanna.block.FungusCombBlock.topOf(now2) - 1.0E-6,
                                "a worker on the comb was left sunk in it at " + standing.getY() + ", its top at "
                                        + (aged.getY() + com.antcolonies.savanna.block.FungusCombBlock.topOf(now2)));
                        helper.assertTrue(com.antcolonies.savanna.block.FungusCombBlock.fresh(now2) == com.antcolonies.savanna.block.FungusCombBlock.fresh(was) + 1
                                && com.antcolonies.savanna.block.FungusCombBlock.old(now2) == com.antcolonies.savanna.block.FungusCombBlock.old(was),
                                "new forage should go on top as a fresh layer: " + was + " -> " + now2);
                        // docs/BUGS.md 112: an insect put at the bottom of a comb's cell (as the numbers once came out) stood
                        // inside the comb for ever. The numbers now come out on its top, and one found sunk is lifted.
                        double combTop = aged.getY() + com.antcolonies.savanna.block.FungusCombBlock.topOf(now2);
                        helper.assertTrue(Math.abs(com.antcolonies.colony.NestWalls.lowFloorTop(level, aged) - combTop) < 1.0E-6,
                                "the comb's top as the numbers see it is " + com.antcolonies.colony.NestWalls.lowFloorTop(level, aged)
                                        + ", not " + combTop);
                        standing.setPos(aged.getX() + 0.5, aged.getY(), aged.getZ() + 0.5);
                        standing.liftIfSunk();
                        helper.assertTrue(standing.getY() >= combTop - 1.0E-6, "a worker sunk in the comb was not lifted onto it: "
                                + standing.getY() + ", its top at " + combTop);
                    }
                    // A comb of 4 fresh layers, ageing fast.
                    ageing[0] = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getCombs(level).stream().filter(pos -> !colony.brood().getBroodCells(level).contains(pos))
                            .findFirst().orElseThrow();
                    level.setBlock(ageing[0], combBlock.defaultBlockState().setValue(com.antcolonies.block.SeedPileBlock.LAYERS, 4)
                            .setValue(com.antcolonies.savanna.block.FungusCombBlock.FRESH, 4), 3);
                    colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(400.0F);
                    phase[0] = 6;
                    phaseAt[0] = now;
                }
            } else if (phase[0] == 6) {
                var state = level.getBlockState(ageing[0]);
                boolean comb = state.getBlock() instanceof com.antcolonies.savanna.block.FungusCombBlock;
                if (comb) {
                    sawRipe[0] |= com.antcolonies.savanna.block.FungusCombBlock.ripe(state) > 0;
                    sawOld[0] |= com.antcolonies.savanna.block.FungusCombBlock.old(state) > 0;
                    // The bands stay in order: old at the bottom, fresh on top (never more than the layers).
                    helper.assertTrue(com.antcolonies.savanna.block.FungusCombBlock.old(state) + com.antcolonies.savanna.block.FungusCombBlock.fresh(state)
                            <= com.antcolonies.savanna.block.FungusCombBlock.layers(state), "a comb with more ages than layers: " + state);
                }
                if (!comb || com.antcolonies.savanna.block.FungusCombBlock.layers(state) < 4) {
                    helper.assertTrue(sawRipe[0], "the fresh comb never ripened before it crumbled");
                    helper.assertTrue(sawOld[0], "the comb never went old before it crumbled");
                    colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(1.0F);
                    combWithNoFloorTakesNoForage(helper, level, colony);
                    helper.succeed();
                }
            }
        });
    }

    /**
     * docs/BUGS.md 95: the game crashed when a worker put forage on a comb whose floor was gone (dug away by a block
     * change that told nobody): the new layer's block update took the comb next to it away, which took this one away
     * too, and the squelch read the comb's height off air. Now a comb with no floor takes no forage, and nothing crashes.
     */
    private static void combWithNoFloorTakesNoForage(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
            com.antcolonies.colony.Colony colony) {
        com.antcolonies.savanna.block.FungusCombBlock combBlock = com.antcolonies.savanna.registry.SavannaBlocks.FUNGUS_COMB.get();
        java.util.Set<BlockPos> young = colony.brood().getBroodCells(level);
        BlockPos a = null;
        BlockPos b = null;
        for (BlockPos cell : colony.nest().getGardenCells(level)) {
            if (young.contains(cell) || !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), net.minecraft.core.Direction.UP)) {
                continue;
            }
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos next = cell.relative(side);
                var there = level.getBlockState(next);
                if (!young.contains(next) && (there.isAir() || there.is(combBlock))
                        && level.getBlockState(next.below()).isFaceSturdy(level, next.below(), net.minecraft.core.Direction.UP)) {
                    a = cell;
                    b = next;
                    break;
                }
            }
            if (a != null) {
                break;
            }
        }
        helper.assertTrue(a != null, "no two garden cells side by side to check a comb with no floor");
        int quiet = net.minecraft.world.level.block.Block.UPDATE_CLIENTS; // (a change that tells no neighbour, as some building does)
        var oneLayer = combBlock.defaultBlockState().setValue(com.antcolonies.block.SeedPileBlock.LAYERS, 1);
        var wasA = level.getBlockState(a);
        var wasB = level.getBlockState(b);
        var floorA = level.getBlockState(a.below());
        var floorB = level.getBlockState(b.below());
        level.setBlock(a, oneLayer, quiet);
        level.setBlock(b, oneLayer, quiet);
        level.setBlock(a.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), quiet);
        level.setBlock(b.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), quiet);
        int food = colony.food().getFood();
        boolean put = colony.food().depositSeed(level, a); // (crashed the game before the fix)
        helper.assertTrue(!put, "forage was put on a comb with no floor under it at " + a);
        helper.assertTrue(colony.food().getFood() == food, "forage counted into the store though nothing was put down");
        helper.assertTrue(!colony.food().foodSpots(level, level.getRandom()).contains(a), "a comb with no floor is still offered as a place for forage");
        level.setBlock(a.below(), floorA, quiet);
        level.setBlock(b.below(), floorB, quiet);
        level.setBlock(a, wasA, quiet);
        level.setBlock(b, wasB, quiet);
    }
}
