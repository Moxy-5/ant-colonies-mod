package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;
import static com.antcolonies.gametest.PopulationTests.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Brood;
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
 * The brain, part 2 and on: colonies that live while the player is away ({@code AwayLife}, {@code NestLife},
 * {@code Meals}, {@code NestLedger}). Their own category, {@code away} (the user's rule, 2026-10-15): run it after
 * every new feature or change to how colonies live, as well as the category the change touches. The helpers that set
 * up a colony are in {@link PopulationTests}.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class AwayTests {
    /** What a frozen insect looked like on disk: saved before it leaves the world, so it can be loaded again. */
    static CompoundTag freezeAndUnload(Colony colony, ServerLevel level, Entity entity) {
        colony.away().freeze(level, entity); // what the game does when the insect's chunk goes out of range
        CompoundTag tag = new CompoundTag();
        entity.save(tag);
        entity.discard();
        return tag;
    }

    /** The insect's chunk is loaded again: the same insect (same id) is back in the world. */
    static void loadAgain(ServerLevel level, CompoundTag tag) {
        Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);
        if (entity != null) {
            level.addFreshEntity(entity);
        }
    }

    /**
     * Step 2a. The nest goes out of range with 3 real workers (one carrying a seed), an egg and 2 larvae (one fed 3
     * pieces), and 4 old workers in the numbers about to die; the queen just fed. After the grace minute
     * (shortened for the test) the colony is away: the 3 workers are numbers (headcount still 3), the young are numbers,
     * the old ones have died (4 bodies owed), the seed is owed. Back in range, the frozen insects load again and leave
     * the world on their first tick; the queen is alive (the brain looked after her while away, step 2b); the egg and
     * the larvae are real again in the nursery (the fed one still fed 3), the 4 bodies lie in the refuse chamber and the
     * seed is in the store. Then two stragglers frozen at the edge while the colony is here: one back after a moment
     * carries on, one back after more than the grace has walked home (it is one of the numbers).
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aColonyAwayFoldsItsFrozenInsectsIntoTheNumbersAndPutsThingsBackOnReturn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        List<UUID> workers = new ArrayList<>();
        List<UUID> stragglers = new ArrayList<>();
        Map<UUID, CompoundTag> unloaded = new HashMap<>();
        UUID[] queen = new UUID[1];
        int[] foodBefore = new int[1];
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // no swapping: the numbers stay where the test puts them
            colony.setHungerOn(true);
            for (int i = 0; i < 3; i++) {
                WorkerAntEntity worker = helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE + 2 + i, SURFACE, MIDDLE + 2);
                workers.add(worker.getUUID());
            }
            BlockPos eggSpot = colony.brood().pickEggSpot(level, level.getRandom());
            helper.assertTrue(eggSpot != null && colony.brood().placeEgg(level, eggSpot), "no room for the egg");
            for (int i = 0; i < 2; i++) {
                BlockPos spot = colony.brood().pickEggSpot(level, level.getRandom());
                helper.assertTrue(spot != null, "no room for a larva");
                AntLarvaEntity larva = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                if (i == 0) {
                    larva.setFed(3);
                }
            }
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 3, "3 workers expected, " + colony.getCount(WorkerAntEntity.CASTE));
            AbstractAntEntity theQueen = colony.getQueen(level);
            queen[0] = theQueen.getUUID();
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(100L);
            colony.queenCare().setQueenLastFed(level.getGameTime());
            ((WorkerAntEntity) level.getEntity(workers.get(0))).setCarriedSeed(new ItemStack(Items.WHEAT_SEEDS));
            foodBefore[0] = colony.food().getFood();
            // Out of range: every insect of the colony is frozen and unloaded (the queen too: she is no number).
            List<Entity> all = new ArrayList<>();
            for (UUID id : workers) {
                all.add(level.getEntity(id));
            }
            all.addAll(colony.brood().getEggs(level));
            all.addAll(colony.brood().getLarvae(level));
            all.add(theQueen);
            for (Entity entity : all) {
                unloaded.put(entity.getUUID(), freezeAndUnload(colony, level, entity));
            }
            colony.away().setAwayForTests(true);
            long lifespan = colony.profile().worker().lifespanTicks();
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - lifespan * 12 / 10, 4);
        });
        helper.runAfterDelay(200, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] away: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            for (UUID id : unloaded.keySet()) {
                helper.assertTrue(id.equals(queen[0]) != colony.away().isFoldedIn(id), "every frozen insect but the queen should be folded in: " + id);
            }
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 3 && colony.population().inside(WorkerAntEntity.CASTE) == 3,
                    "the 3 frozen workers should be numbers and the 4 old ones dead: " + colony.getCount(WorkerAntEntity.CASTE) + " counted, "
                            + colony.population().inside(WorkerAntEntity.CASTE) + " inside");
            helper.assertTrue(colony.brood().insideCount() == 3, "the egg and 2 larvae should be numbers: " + colony.brood().insideCount());
            helper.assertTrue(colony.away().getBodiesOwed() == 4 && colony.away().getFoodOwed() == 1,
                    "4 bodies and 1 seed owed expected: " + colony.away().describe(level));
            // Back in range: the chunks load again, with everything that was frozen in them.
            colony.away().setAwayForTests(false);
            unloaded.values().forEach(tag -> loadAgain(level, tag));
        });
        helper.runAfterDelay(240, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony != null, "the queen should be back and alive");
            colony.queenCare().setQueenLastFed(level.getGameTime()); // (fed: the rest of the test is not about her)
            for (UUID id : unloaded.keySet()) {
                if (!id.equals(queen[0])) {
                    helper.assertTrue(level.getEntity(id) == null, "a folded insect should leave the world when it loads: " + level.getEntity(id));
                }
            }
            AntColoniesMod.LOGGER.info("[away-test] back: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.brood().countEggs(level) == 1 && colony.brood().countLarvae(level) == 2 && colony.brood().insideCount() == 0,
                    "the egg and the 2 larvae should be real again: " + colony.brood().countEggs(level) + " eggs, " + colony.brood().countLarvae(level)
                            + " larvae, " + colony.brood().insideCount() + " inside");
            helper.assertTrue(colony.brood().getLarvae(level).stream().anyMatch(larva -> larva.getFed() == 3), "the fed larva should still be fed 3");
            helper.assertTrue(colony.refuse().getRefuseCorpses(level).size() == 4, "4 bodies expected in the refuse chamber: "
                    + colony.refuse().getRefuseCorpses(level).size());
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 1, "the carried seed should be in the store: " + colony.food().getFood()
                    + " food, was " + foodBefore[0]);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 3 && colony.population().inside(WorkerAntEntity.CASTE) == 3,
                    "still 3 workers, all numbers: " + colony.getCount(WorkerAntEntity.CASTE) + ", " + colony.population().inside(WorkerAntEntity.CASTE));
            // Two stragglers at the edge of the loaded land while the colony is here.
            for (int i = 0; i < 2; i++) {
                stragglers.add(helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE + 6 + i, SURFACE, MIDDLE - 4).getUUID());
            }
        });
        helper.runAfterDelay(260, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 5, "5 workers expected: " + colony.getCount(WorkerAntEntity.CASTE));
            for (UUID id : stragglers) {
                unloaded.put(id, freezeAndUnload(colony, level, level.getEntity(id)));
            }
        });
        helper.runAfterDelay(280, () -> loadAgain(level, unloaded.get(stragglers.get(0)))); // back after a moment
        helper.runAfterDelay(400, () -> loadAgain(level, unloaded.get(stragglers.get(1)))); // back after more than the grace
        helper.runAfterDelay(440, () -> {
            Colony colony = antColony(helper);
            Entity first = level.getEntity(stragglers.get(0));
            helper.assertTrue(first != null && first.isAlive() && !colony.away().isFrozen(stragglers.get(0)),
                    "the straggler back after a moment should carry on");
            helper.assertTrue(level.getEntity(stragglers.get(1)) == null, "the straggler back after the grace should have walked home");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 5 && colony.population().inside(WorkerAntEntity.CASTE) == 4,
                    "5 workers, 4 of them numbers expected: " + colony.getCount(WorkerAntEntity.CASTE) + ", " + colony.population().inside(WorkerAntEntity.CASTE));
            helper.succeed();
        });
    }

    /**
     * Step 2b. A colony away lives on as numbers: 300 young workers (102 of them nurses), 48 seeds in the store, an egg
     * and 2 hungry larvae as numbers, and the queen in her room carrying an egg (all as if frozen). While away the
     * nurses mill seeds into bread and feed the larvae; the egg hatches and is fed; all 3 grow up into workers (303);
     * the queen's egg comes out as a number and the nurses feed her bread. Then, with 10 seeds left in the store, the
     * workers have gone almost their whole starve time without food: the ones the store can feed eat, the rest starve
     * and leave bodies. Back in range the
     * queen is alive, her egg comes out into the nursery, bodies lie in the refuse chamber, and the blocks have caught up
     * with the counts (nothing owed).
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aColonyAwayEatsMillsBreadAndRaisesItsYoungAsNumbers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        UUID[] queen = new UUID[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        int[] eggsLaid = new int[1];
        int[] foodLeft = new int[1];
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.setHungerOn(true);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the queen's room should be dug");
            colony.getQueen(level).teleportTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            colony.queenCare().setQueenLastFed(level.getGameTime());
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 300);
            List<BlockPos> store = colony.nest().getFoodStoreCells(level);
            for (int i = 0; i < 48; i++) {
                helper.assertTrue(colony.food().depositSeed(level, store.get(i % store.size())), "could not put a seed in the store");
            }
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in her room");
            queen[0] = colony.getQueen(level).getUUID();
            eggsLaid[0] = colony.queenCare().getEggsLaid();
            colony.queenCare().setEggDueForTests(level.getGameTime() + 60);
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
            colony.away().setAwayForTests(true);
            colony.brood().addInsideForTests(Brood.Young.egg(100)); // (frozen young: here they would come out at once)
            colony.brood().addInsideForTests(Brood.Young.larva(0, 0, null));
            colony.brood().addInsideForTests(Brood.Young.larva(0, 0, null));
        });
        helper.runAfterDelay(500, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] 2b away: {}{} | food {}, bread {}, queen bread {}", colony.population().describe(level),
                    colony.away().describe(level), colony.food().getFood(), colony.bakery().getBread(), colony.queenCare().getQueenBread());
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 303 && colony.population().inside(WorkerAntEntity.CASTE) == 303,
                    "the egg and the 2 larvae should have grown up into workers (303): " + colony.getCount(WorkerAntEntity.CASTE));
            helper.assertTrue(colony.queenCare().getEggsLaid() == eggsLaid[0] + 1, "the queen's egg should have come out while away");
            List<Brood.Young> young = colony.brood().youngInsideForTests();
            helper.assertTrue(young.size() == 1 && young.get(0).isEgg(), "her egg should be a number: " + young);
            helper.assertTrue(colony.queenCare().getQueenBread() > 0 || colony.queenCare().isIncubating(),
                    "the nurses should have fed the queen: " + colony.queenCare().getQueenBread());
            helper.assertTrue(colony.food().getFood() < 48 && colony.ledger().store() < 0,
                    "the nurses should have milled seeds into bread: " + colony.food().getFood() + " food, " + colony.ledger().describe());
            AwayReport report = colony.away().report(); // what the player reads in the colony list
            helper.assertTrue(report.get(AwayReport.What.EGGS_LAID) == 1 && report.get(AwayReport.What.HATCHED) == 1
                    && report.get(AwayReport.What.GREW_UP) == 3 && report.get(AwayReport.What.QUEEN_FED) > 0
                    && report.get(AwayReport.What.YOUNG_FED) > 0 && report.get(AwayReport.What.BREAD_MADE) > 0,
                    "the away report should tell what happened: " + report.describe());
            List<BlockPos> store = colony.nest().getFoodStoreCells(level);
            for (int i = 0; i < 10; i++) {
                colony.food().depositSeed(level, store.get(i % store.size())); // (a few seeds left in the store)
            }
            foodLeft[0] = colony.food().getFood();
            // The workers last ate long ago: nearly their whole starve time.
            var worker = colony.profile().worker();
            colony.population().setLastAteForTests(level, WorkerAntEntity.CASTE, level.getGameTime() - worker.starveAfterTicks() + 60);
        });
        helper.runAfterDelay(600, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            int workers = colony.getCount(WorkerAntEntity.CASTE);
            AntColoniesMod.LOGGER.info("[away-test] 2b starving: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(workers > 0 && workers <= foodLeft[0] && workers == colony.population().inside(WorkerAntEntity.CASTE),
                    "only the workers the store could feed (" + foodLeft[0] + ") should be alive: " + workers);
            helper.assertTrue(colony.food().getFood() == 0 || workers == 0, "the store should be eaten empty: " + colony.food().getFood());
            helper.assertTrue(colony.ledger().bodies() >= 303 - foodLeft[0], "the starved workers should leave bodies: " + colony.ledger().describe());
            // Back in range: the queen's chunk loads again.
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
        });
        helper.runAfterDelay(700, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony != null, "the queen should be back and alive");
            AntColoniesMod.LOGGER.info("[away-test] 2b back: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.brood().insideCount() == 0 && colony.brood().countEggs(level) == 1,
                    "her egg should be real in the nursery: " + colony.brood().countEggs(level) + " eggs, " + colony.brood().insideCount() + " inside");
            helper.assertTrue(colony.away().report().get(AwayReport.What.STARVED) >= 250, "the report should count the starved: " + colony.away().report().describe());
            helper.assertTrue(colony.away().describe(level).contains("while away ("), "the report should be kept after coming back: "
                    + colony.away().describe(level));
            helper.assertTrue(colony.ledger().isEmpty(), "the blocks should have caught up with the counts: " + colony.ledger().describe());
            helper.assertTrue(!colony.refuse().getRefuseCorpses(level).isEmpty(), "the starved workers' bodies should be in the refuse chamber");
            helper.succeed();
        });
    }

    /** The comb blocks' layers by age and their nodules, added up: fresh, ripe, old, nodules, layers. */
    static int[] combTotals(ServerLevel level, Colony colony) {
        int[] totals = new int[5];
        for (BlockPos pos : colony.garden().getCombs(level)) {
            var state = level.getBlockState(pos);
            totals[0] += com.antcolonies.block.FungusCombBlock.fresh(state);
            totals[1] += com.antcolonies.block.FungusCombBlock.ripe(state);
            totals[2] += com.antcolonies.block.FungusCombBlock.old(state);
            totals[3] += state.getValue(com.antcolonies.block.FungusCombBlock.NODULES);
            totals[4] += com.antcolonies.block.FungusCombBlock.layers(state);
        }
        return totals;
    }

    /**
     * Step 2b, the termite side. A Macrotermes colony with its nest dug, 100 worker numbers (40 young, 60 old), 2 minor
     * and 1 major soldier numbers, all just fed, and a garden of aged comb (each block: 2 old layers, a ripe one, a fresh
     * one, 3 nodules). The queen, full-grown, lies in the royal chamber; she is frozen and the colony goes away. While
     * away (the ageing sped up 100 times): the fresh layers ripen, ripe layers go old (by themselves and when a nodule
     * grows from them), nodules grow, old layers crumble (out of the food count), all as counts owed to the blocks; the
     * known ages still add up to the layers counted; the nurses feed the queen nodules and she lays her stream. Then the
     * workers' and soldiers' meals come due: the young share eat nodules, the old ones old comb (4 meals a piece), the
     * soldiers a nodule each, and nobody is left hungry. Back in range, the blocks catch up: the ledger empties and the
     * comb blocks hold just the ages and nodules the brain counted.
     */
    @GameTest(template = "arena", timeoutTicks = 1000)
    public static void aTermiteColonyAwayAgesItsCombFeedsItsQueenAndEatsAsNumbers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        var foods = com.antcolonies.species.SpeciesProfile.MealFood.values();
        UUID[] queen = new UUID[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        int[] eggsLaid = new int[1];
        int[] foodBefore = new int[1];
        int[] grownBefore = new int[1];
        int[] lostBefore = new int[1];
        int[] eatenBefore = new int[2];
        int[] expected = new int[5]; // fresh, ripe, old, nodules, layers: what the brain counted when the colony came back
        boolean[] back = {false};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.setForagingTimeForTests(false);
            colony.setHungerOn(true);
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().setViewersForTests(List.of());
            TermiteSoldierTests.digNestAtOnce(level, colony);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the royal chamber should be dug");
            AbstractAntEntity theQueen = colony.getQueen(level);
            theQueen.teleportTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            ((com.antcolonies.entity.TermiteQueenEntity) theQueen).setSize(1.0F); // full-grown: an egg every 15 seconds
            long now = level.getGameTime();
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now, 40);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - 250000, 60);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), now, 2);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), now, 1);
            for (int i = 0; i < 40; i++) {
                BlockPos spot = colony.food().pickFoodSpot(level, level.getRandom());
                if (spot == null) {
                    break;
                }
                colony.food().depositSeed(level, spot);
            }
            for (BlockPos comb : colony.garden().getCombs(level)) {
                if (com.antcolonies.block.FungusCombBlock.layers(level.getBlockState(comb)) < 4) {
                    continue; // (a comb not full yet stays all fresh)
                }
                // Aged comb: 2 old layers at the bottom, a ripe one, a fresh one on top; 3 nodules.
                level.setBlock(comb, level.getBlockState(comb).setValue(com.antcolonies.block.FungusCombBlock.NODULES, 3)
                        .setValue(com.antcolonies.block.FungusCombBlock.OLD, 2).setValue(com.antcolonies.block.FungusCombBlock.FRESH, 1), 3);
            }
            helper.assertTrue(colony.garden().getCombs(level).size() >= 5, "only " + colony.garden().getCombs(level).size() + " comb blocks");
            helper.assertTrue(colony.food().getFood() == combTotals(level, colony)[4], "the food count " + colony.food().getFood()
                    + " is not the " + combTotals(level, colony)[4] + " layers of comb");
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in the royal chamber");
            helper.assertTrue(!colony.isFounding(), "the colony should have workers");
            AbstractAntEntity theQueen = colony.getQueen(level);
            queen[0] = theQueen.getUUID();
            eggsLaid[0] = colony.queenCare().getEggsLaid();
            colony.queenCare().setQueenLastFed(level.getGameTime());
            int[] blocks = combTotals(level, colony);
            helper.assertTrue(colony.garden().freshKnown() == blocks[0] && colony.garden().ripeKnown() == blocks[1]
                    && colony.garden().oldKnown() == blocks[2], "the brain should have seen the comb as it is: " + java.util.Arrays.toString(blocks)
                            + " vs " + colony.garden().freshKnown() + "/" + colony.garden().ripeKnown() + "/" + colony.garden().oldKnown());
            foodBefore[0] = colony.food().getFood();
            grownBefore[0] = colony.garden().getNodulesGrown();
            colony.garden().setAgeingSpeedForTests(100.0F);
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, theQueen);
            colony.away().setAwayForTests(true);
        });
        helper.runAfterDelay(400, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] termites away: {}{} | known comb {}/{}/{}, nodules {}, food {}, queen food {}",
                    colony.population().describe(level), colony.away().describe(level), colony.garden().freshKnown(), colony.garden().ripeKnown(),
                    colony.garden().oldKnown(), colony.life().nurseFoodLeft(level), colony.food().getFood(), colony.queenCare().getQueenBread());
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            var ledger = colony.ledger();
            helper.assertTrue(ledger.ripened() > 0, "fresh comb should have ripened while away: " + ledger.describe());
            helper.assertTrue(ledger.aged() > 0, "ripe comb should have gone old while away: " + ledger.describe());
            helper.assertTrue(colony.garden().getNodulesGrown() > grownBefore[0], "nodules should have grown while away");
            helper.assertTrue(ledger.store() < 0 && colony.food().getFood() == foodBefore[0] + ledger.store(),
                    "old comb should have crumbled, out of the food count: " + colony.food().getFood() + " food, was " + foodBefore[0] + "; "
                            + ledger.describe());
            int known = colony.garden().freshKnown() + colony.garden().ripeKnown() + colony.garden().oldKnown();
            helper.assertTrue(known == colony.food().getFood(), "the known ages (" + known + " layers) should add up to the "
                    + colony.food().getFood() + " counted");
            helper.assertTrue(ledger.nurseFood() < 0 && colony.queenCare().getQueenBread() + 2 * (colony.queenCare().getEggsLaid() - eggsLaid[0]) > 0,
                    "the nurses should have fed the queen nodules: " + ledger.describe() + ", she holds " + colony.queenCare().getQueenBread());
            // The meals come due: the workers' and the soldiers'.
            long due = level.getGameTime() - colony.profile().worker().mealIntervalTicks() - 30;
            colony.population().setLastAteForTests(level, WorkerAntEntity.CASTE, due);
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                colony.population().setLastAteForTests(level, rank.caste(), due);
            }
            eatenBefore[0] = colony.workerFood().getPiecesEaten(foods[0]);
            eatenBefore[1] = colony.workerFood().getPiecesEaten(foods[1]);
        });
        helper.runAfterDelay(460, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            int comb = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.STORE) - eatenBefore[0];
            int nodules = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.NURSE_FOOD) - eatenBefore[1];
            AntColoniesMod.LOGGER.info("[away-test] termites away ate {} layers of comb and {} nodules: {}{}", comb, nodules,
                    colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(comb >= 13 && comb <= 16, "the 60 old worker numbers should eat about 15 layers of old comb, ate " + comb);
            helper.assertTrue(nodules >= 8 && nodules <= 11, "the 40 young worker numbers should eat about 10 nodules, ate " + nodules);
            helper.assertTrue(colony.population().hungry(level) == 0, "every number should have eaten, the soldiers too: "
                    + colony.population().describe(level));
            helper.assertTrue(colony.population().inside(WorkerAntEntity.CASTE) == 100, "a worker number died: "
                    + colony.population().inside(WorkerAntEntity.CASTE));
            helper.assertTrue(colony.queenCare().getEggsLaid() > eggsLaid[0], "the queen should have laid her stream while away");
            helper.assertTrue(colony.brood().youngInsideForTests().stream().anyMatch(Brood.Young::isEgg), "her eggs should be numbers: "
                    + colony.brood().youngInsideForTests());
            int known = colony.garden().freshKnown() + colony.garden().ripeKnown() + colony.garden().oldKnown();
            helper.assertTrue(known == colony.food().getFood(), "after the meals the known ages (" + known + " layers) should still add up to the "
                    + colony.food().getFood() + " counted");
            // Back in range: what the brain counted is what the blocks must show once they have caught up.
            helper.assertTrue(!colony.ledger().isEmpty(), "the blocks should owe something");
            expected[0] = colony.garden().freshKnown();
            expected[1] = colony.garden().ripeKnown();
            expected[2] = colony.garden().oldKnown();
            expected[3] = colony.life().nurseFoodLeft(level);
            expected[4] = colony.food().getFood();
            grownBefore[0] = colony.garden().getNodulesGrown();
            lostBefore[0] = colony.garden().getNodulesLost();
            colony.garden().setAgeingSpeedForTests(0.0F); // (only nodules may still grow: counted below)
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
            back[0] = true;
        });
        helper.onEachTick(() -> {
            if (!back[0]) {
                return;
            }
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || !colony.ledger().isEmpty()) {
                return;
            }
            int[] blocks = combTotals(level, colony);
            int grown = colony.garden().getNodulesGrown() - grownBefore[0]; // (each turned a ripe layer old)
            int lost = colony.garden().getNodulesLost() - lostBefore[0]; // (on comb eaten or crumbled right away)
            AntColoniesMod.LOGGER.info("[away-test] termites back: blocks {} (fresh, ripe, old, nodules, layers), counted {}, {} nodules grown since, {} lost",
                    java.util.Arrays.toString(blocks), java.util.Arrays.toString(expected), grown, lost);
            helper.assertTrue(blocks[4] == expected[4] && blocks[4] == colony.food().getFood(), "the comb should have " + expected[4]
                    + " layers as counted: " + java.util.Arrays.toString(blocks) + ", food " + colony.food().getFood());
            helper.assertTrue(blocks[0] == expected[0] && blocks[1] == expected[1] - grown && blocks[2] == expected[2] + grown,
                    "the comb's ages should be as counted: blocks " + java.util.Arrays.toString(blocks) + ", counted " + java.util.Arrays.toString(expected)
                            + ", " + grown + " nodules grown since");
            helper.assertTrue(blocks[3] == expected[3] + grown - lost, "the nodules should be as counted: blocks " + java.util.Arrays.toString(blocks)
                    + ", counted " + java.util.Arrays.toString(expected) + ", " + grown + " grown since, " + lost + " lost with used-up comb");
            colony.garden().setAgeingSpeedForTests(1.0F);
            helper.succeed();
        });
    }
}
