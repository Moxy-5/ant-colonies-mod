package com.antcolonies.gametest;

import static com.antcolonies.gametest.AwayKit.*;
import static com.antcolonies.gametest.HarvesterPopulationTests.*;
import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Brood;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.DesertParts;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.DesertEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * The brain, part 2 and on: colonies that live while the player is away ({@code AwayLife}, {@code NestLife},
 * {@code Meals}, {@code NestLedger}). Their own category, {@code away} (the user's rule, 2026-10-15): run it after
 * every new feature or change to how colonies live, as well as the category the change touches. The shared helpers are
 * in {@link AwayKit} and {@link TestScenes}.
 * The red harvester ant's scenes (the split, step S4; the termites' are in {@link TermiteAwayTests}).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class HarvesterAwayTests {
    /** How many blocks of the hill (burrowed dirt) stand round the entrance. */
    static int hillBlocks(ServerLevel level, Colony colony) {
        int count = 0;
        int r = com.antcolonies.colony.Hill.MOUND_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(colony.getCenter().offset(-r, 0, -r), colony.getCenter().offset(r, 4, r))) {
            if (level.getBlockState(pos).is(com.antcolonies.registry.DesertBlocks.BURROWED_DIRT.get())) {
                count++;
            }
        }
        return count;
    }

    /** Brain step 4b: out of the harvester ants' foraging hours the numbers bring nothing in from the remembered tufts. */
    private static void checkNothingForaged(GameTestHelper helper, Colony colony, List<BlockPos> tufts, String when) {
        for (BlockPos tuft : tufts) {
            helper.assertTrue(colony.food().getPiecesCut(tuft) == preCut(colony), "no tuft should be foraged " + when + ": " + tuft + " cut " + colony.food().getPiecesCut(tuft));
        }
        helper.assertTrue(colony.away().report().get(AwayReport.What.FORAGED) == 0, "nothing should be brought in " + when + ": " + colony.away().report().describe());
    }

    /** The pieces a test tuft of short grass has cut already: all but 2 (none since short grass is one take, 2026-10-17). */
    private static int preCut(Colony colony) {
        return Math.max(0, colony.food().piecesOf(Blocks.SHORT_GRASS.defaultBlockState()) - 2);
    }

    /** The pieces left on such a tuft. */
    private static int leftOn(Colony colony) {
        return colony.food().piecesOf(Blocks.SHORT_GRASS.defaultBlockState()) - preCut(colony);
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
                WorkerAntEntity worker = helper.spawn(DesertEntities.WORKER_ANT.get(), MIDDLE + 2 + i, SURFACE, MIDDLE + 2);
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
            helper.assertTrue(colony.getCount(Caste.WORKER) == 3, "3 workers expected, " + colony.getCount(Caste.WORKER));
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
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - lifespan * 12 / 10, 4);
        });
        helper.runAfterDelay(200, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] away: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            for (UUID id : unloaded.keySet()) {
                helper.assertTrue(id.equals(queen[0]) != colony.away().isFoldedIn(id), "every frozen insect but the queen should be folded in: " + id);
            }
            helper.assertTrue(colony.getCount(Caste.WORKER) == 3 && colony.population().inside(Caste.WORKER) == 3,
                    "the 3 frozen workers should be numbers and the 4 old ones dead: " + colony.getCount(Caste.WORKER) + " counted, "
                            + colony.population().inside(Caste.WORKER) + " inside");
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
            helper.assertTrue(colony.getCount(Caste.WORKER) == 3 && colony.population().inside(Caste.WORKER) == 3,
                    "still 3 workers, all numbers: " + colony.getCount(Caste.WORKER) + ", " + colony.population().inside(Caste.WORKER));
            // Two stragglers at the edge of the loaded land while the colony is here.
            for (int i = 0; i < 2; i++) {
                stragglers.add(helper.spawn(DesertEntities.WORKER_ANT.get(), MIDDLE + 6 + i, SURFACE, MIDDLE - 4).getUUID());
            }
        });
        helper.runAfterDelay(260, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.getCount(Caste.WORKER) == 5, "5 workers expected: " + colony.getCount(Caste.WORKER));
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
            helper.assertTrue(colony.getCount(Caste.WORKER) == 5 && colony.population().inside(Caste.WORKER) == 4,
                    "5 workers, 4 of them numbers expected: " + colony.getCount(Caste.WORKER) + ", " + colony.population().inside(Caste.WORKER));
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
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime(), 300);
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
                    colony.away().describe(level), colony.food().getFood(), colony.part(DesertParts.BAKERY).orElseThrow().getBread(), colony.queenCare().getQueenBread());
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            helper.assertTrue(colony.getCount(Caste.WORKER) == 303 && colony.population().inside(Caste.WORKER) == 303,
                    "the egg and the 2 larvae should have grown up into workers (303): " + colony.getCount(Caste.WORKER));
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
            colony.population().setLastAteForTests(level, Caste.WORKER, level.getGameTime() - worker.starveAfterTicks() + 60);
        });
        helper.runAfterDelay(600, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            int workers = colony.getCount(Caste.WORKER);
            AntColoniesMod.LOGGER.info("[away-test] 2b starving: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(workers > 0 && workers <= foodLeft[0] && workers == colony.population().inside(Caste.WORKER),
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

    /**
     * Step 2d (the user's decision: building while away is applied when the player returns). A harvester ant colony with
     * its last two rooms (the queen's and the refuse chamber) not dug yet and no hill, and 40 middle-aged workers in
     * the numbers (about 26 of them builders). While away nothing changes in the ground (its land is not loaded); the brain counts the
     * builders' time. Back in range with a pretend player looking at the hill: nothing is dug yet (the soil would have to
     * go up onto the hill in plain sight). The player gone: the two rooms are dug, the soil is heaped on the hill, the
     * away report counts both, and the time left over is dropped (nothing more may be dug).
     */
    @GameTest(template = "arena", timeoutTicks = 900, skyAccess = true)
    public static void anAntColonyAwayDigsItsRoomsAndHeapsItsHillOutOfSightOnReturn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        List<BlockPos> undug = new ArrayList<>();
        UUID[] queen = new UUID[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            List<NestPlan.Chamber> rooms = colony.nest().getPlannedChambers(level);
            helper.assertTrue(rooms.size() >= 3, "the nest should have at least 3 levels: " + rooms.size());
            for (NestPlan.Chamber room : rooms.subList(rooms.size() - 2, rooms.size())) {
                for (BlockPos cell : room.cells()) {
                    level.setBlock(cell, Blocks.DIRT.defaultBlockState(), 3);
                    undug.add(cell);
                }
            }
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - 100000, 40); // middle-aged: builders
            helper.assertTrue(hillBlocks(level, colony) == 0, "no hill yet");
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = antColony(helper);
            queen[0] = colony.getQueen(level).getUUID();
            colony.queenCare().setQueenLastFed(level.getGameTime());
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
            colony.away().setAwayForTests(true);
        });
        helper.runAfterDelay(400, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] 2d ants away: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            helper.assertTrue(undug.stream().allMatch(cell -> level.getBlockState(cell).is(Blocks.DIRT)) && hillBlocks(level, colony) == 0,
                    "nothing should be dug while away: the land is not loaded");
            int builders = colony.building().buildersInside(level);
            long seconds = (400 - 40 - 20) / 20; // (away once the grace is over)
            // (the colony shares out its jobs: a third of the 40 go foraging, see Colony.shareOut)
            helper.assertTrue(builders >= 20 && colony.building().work() >= builders * 20L * (seconds - 2),
                    "the builders' time should be counted while away: " + builders + " builders, " + colony.building().work() + " ticks");
            colony.building().setWorkForTests(colony.building().work() + 50000); // (longer away than the test waits)
            // Back in range, with a pretend player looking at the hill from 5 blocks off.
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(colony.getCenter().offset(5, 2, 0))));
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
        });
        helper.runAfterDelay(480, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony != null, "the queen should be back and alive");
            AntColoniesMod.LOGGER.info("[away-test] 2d ants back, watched: {}", colony.away().describe(level));
            helper.assertTrue(undug.stream().allMatch(cell -> level.getBlockState(cell).is(Blocks.DIRT)) && hillBlocks(level, colony) == 0
                    && colony.building().hasWork(), "nothing should be dug while a player looks at the hill: its soil goes up in plain sight");
            colony.population().setViewersForTests(List.of());
        });
        helper.runAfterDelay(640, () -> {
            Colony colony = antColony(helper);
            AwayReport report = colony.away().report();
            int hill = hillBlocks(level, colony);
            AntColoniesMod.LOGGER.info("[away-test] 2d ants back: hill {}, {}", hill, colony.away().describe(level));
            List<BlockPos> left = undug.stream().filter(cell -> !level.getBlockState(cell).isAir()).toList();
            helper.assertTrue(left.isEmpty(), "both rooms should be dug: " + left.size() + " of " + undug.size() + " left, " + left);
            helper.assertTrue(hill > 0 && report.get(AwayReport.What.HILL) == hill, "the soil should be heaped on the hill: " + hill
                    + " blocks, report " + report.describe());
            helper.assertTrue(report.get(AwayReport.What.DUG) == undug.size(), "the report should count the " + undug.size() + " blocks dug: "
                    + report.describe());
            helper.assertTrue(!colony.building().hasWork(), "the time left over should be dropped once nothing more may be dug: "
                    + colony.building().work());
            helper.succeed();
        });
    }

    /**
     * Step 2c, the harvester ant (the user's decision: the numbers forage always, but only out of every player's sight).
     * 60 old workers (foragers) in the numbers, no real workers, and 8 tufts of short grass, each with all but 2 of its seeds (since 2026-10-17 short grass is one take, so none)
     * cut already: 4 in the west corner more than 64 blocks from the pretend player's eyes, 4 in the east corner in plain
     * sight. While here the numbers eat the hidden tufts up (they vanish, out of sight; their seeds in the store) and leave
     * the seen ones alone. Then the colony goes away (nobody near): from what the brain remembers they eat the other 4 up
     * too, but not a tuft that grew after the brain last looked; the away report counts it all.
     * <p>Brain step 4b, in the same scene: away, the numbers keep the harvester ants' day clock: nothing is foraged at
     * midnight, nor at 7:00 in the rain; on a dry morning in a hot biome they go out and eat the tufts up.
     * <p>Brain step 4b parts 2 and 3, in the same scene: on that morning the patrollers are on: the numbers send a patrol
     * first (as numbers: no real worker is loaded), nothing is foraged before one is back, and the door then lets the
     * foragers out by the homecomings; the away report counts the patrol.
     */
    @GameTest(template = "arena", timeoutTicks = 5000, skyAccess = true)
    public static void antNumbersForageOnlyOutOfSightAndFromWhatTheyRemember(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        List<BlockPos> hidden = new ArrayList<>();
        List<BlockPos> seen = new ArrayList<>();
        BlockPos late = helper.absolutePos(new BlockPos(7, SURFACE, 1));
        UUID[] queen = new UUID[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        int[] foodBefore = new int[1];
        boolean[] patrolOn = {false};
        boolean[] foragedBeforePatrol = {false};
        TestClock clock = TestClock.moving(helper, 7.0);
        helper.onEachTick(() -> noRealWorkers(helper));
        helper.onEachTick(() -> {
            if (helper.getTick() % 200 == 0 && queen[0] == null && helper.getTick() > 15) {
                Colony colony = antColony(helper);
                AntColoniesMod.LOGGER.info("[away-test] 2c tick {}: food {}, cut {} {} {}{}{}", helper.getTick(), colony.food().getFood(),
                        hidden.stream().map(colony.food()::getPiecesCut).toList(), seen.stream().map(colony.food()::getPiecesCut).toList(),
                        colony.away().report().describe(), colony.part(DesertParts.FORAGER_SPOTS).orElseThrow().describe(), colony.field().describe());
            }
        });
        helper.onEachTick(() -> {
            if (!patrolOn[0]) {
                return;
            }
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElse(null);
            if (colony != null && colony.part(DesertParts.PATROL).orElseThrow().backThisRound() == 0 && colony.away().report().get(AwayReport.What.FORAGED) > 0) {
                foragedBeforePatrol[0] = true;
            }
        });
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            long old = level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1200;
            colony.population().addNewMembers(Caste.WORKER, old, 60);
            for (int i = 0; i < 4; i++) {
                hidden.add(helper.absolutePos(new BlockPos(1 + 2 * (i % 2), SURFACE, 1 + 2 * (i / 2))));
                seen.add(helper.absolutePos(new BlockPos(21 + 2 * (i % 2), SURFACE, 21 + 2 * (i / 2))));
            }
            List<BlockPos> all = new ArrayList<>(hidden);
            all.addAll(seen);
            for (BlockPos tuft : all) {
                level.setBlock(tuft, Blocks.SHORT_GRASS.defaultBlockState(), 3);
                colony.food().setPiecesCutForTests(tuft, preCut(colony));
            }
            // The pretend player stands far to the south-east: the east tufts are 54 blocks off in plain sight, the west ones 82.
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(60, SURFACE + 2, 60)))));
            colony.food().rescanPlantsSoon();
            foodBefore[0] = colony.food().getFood();
            colony.field().setOnForTests(true);
        });
        helper.runAfterDelay(2000, () -> {
            Colony colony = antColony(helper);
            AntColoniesMod.LOGGER.info("[away-test] 2c ants here: food {}{}{}", colony.food().getFood(), colony.population().describe(level),
                    colony.field().describe());
            for (BlockPos tuft : hidden) {
                helper.assertTrue(level.getBlockState(tuft).isAir(), "a tuft out of sight should be eaten up and gone: " + tuft);
                helper.assertTrue(!colony.field().remembers(tuft), "an eaten-up tuft should be forgotten: " + tuft);
            }
            for (BlockPos tuft : seen) {
                helper.assertTrue(level.getBlockState(tuft).is(Blocks.SHORT_GRASS) && colony.food().getPiecesCut(tuft) == preCut(colony),
                        "a tuft in the player's sight should be left alone: " + tuft + " cut " + colony.food().getPiecesCut(tuft));
                helper.assertTrue(colony.field().remembers(tuft), "the brain should remember the tuft in sight: " + tuft);
            }
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 4 * leftOn(colony), "the seeds left on the 4 tufts should be in the store: " + colony.food().getFood()
                    + ", was " + foodBefore[0]);
            helper.assertTrue(colony.away().report().isEmpty(), "nothing counts as away while the colony is here: " + colony.away().report().describe());
            // Away, nobody near; a new tuft grows meanwhile, which the brain never saw.
            queen[0] = colony.getQueen(level).getUUID();
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
            colony.away().setAwayForTests(true);
            colony.population().setViewersForTests(List.of());
            level.setBlock(late, Blocks.SHORT_GRASS.defaultBlockState(), 3);
            colony.food().setPiecesCutForTests(late, preCut(colony));
            clock.skipTo(0.0); // midnight: the foragers stay in
        });
        helper.runAfterDelay(2300, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            checkNothingForaged(helper, colony, seen, "at midnight");
            clock.skipTo(7.0); // 7:00 the next morning, but raining on the nest
            colony.setRainForTests(true);
        });
        helper.runAfterDelay(2600, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            checkNothingForaged(helper, colony, seen, "in the rain");
            clock.skipTo(8.0); // 8:00 in a hot biome, dry: out (the midday rest is 11:00 to 15:00)
            colony.setHeatForTests(2.0F);
            colony.setRainForTests(false);
            colony.part(DesertParts.PATROL).orElseThrow().setOnForTests(true); // brain step 4b: a patrol first (the round begins as the patrol sees the day clock open)
            patrolOn[0] = true;
        });
        helper.runAfterDelay(2660, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] 4b: {}{}", colony.part(DesertParts.PATROL).orElseThrow().describe(level), colony.part(DesertParts.DOOR).orElseThrow().describe(level));
            helper.assertTrue(colony.part(DesertParts.PATROL).orElseThrow().isRoundOn() && colony.part(DesertParts.PATROL).orElseThrow().numbersOut() > 0 && colony.part(DesertParts.PATROL).orElseThrow().realOut().isEmpty(),
                    "the numbers should send a patrol of numbers first: " + colony.part(DesertParts.PATROL).orElseThrow().describe(level));
            helper.assertTrue(colony.part(DesertParts.PATROL).orElseThrow().sentThisRound() == colony.profile().food().patrol().orElseThrow().dawnPatrol(colony.getCount(Caste.WORKER)),
                    "the dawn patrol should be the share of the workers: " + colony.part(DesertParts.PATROL).orElseThrow().describe(level));
        });
        helper.runAfterDelay(4600, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] 2c ants away: food {}{}{}", colony.food().getFood(), colony.away().describe(level),
                    colony.field().describe());
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            for (BlockPos tuft : seen) {
                helper.assertTrue(colony.food().getPiecesCut(tuft) == 0 && !colony.field().remembers(tuft),
                        "while away the remembered tuft should be eaten up: " + tuft + " cut " + colony.food().getPiecesCut(tuft));
            }
            helper.assertTrue(colony.food().getPiecesCut(late) == preCut(colony) && level.getBlockState(late).is(Blocks.SHORT_GRASS),
                    "a tuft the brain never saw should not be foraged while away: cut " + colony.food().getPiecesCut(late));
            AwayReport report = colony.away().report();
            int milled = report.get(AwayReport.What.BREAD_MADE); // (the nurses mill some into bread for the queen)
            helper.assertTrue(colony.food().getFood() + milled == foodBefore[0] + 8 * leftOn(colony), "the seeds left on all 8 tufts should have come in: " + colony.food().getFood()
                    + " in the store and " + milled + " milled, was " + foodBefore[0]);
            helper.assertTrue(report.get(AwayReport.What.FORAGED) == 4 * leftOn(colony) && report.get(AwayReport.What.PLANTS_USED_UP) == 4,
                    "the away report should count the food brought in and the tufts eaten up: " + report.describe());
            helper.assertTrue(!foragedBeforePatrol[0], "nothing should be foraged before a patroller is back");
            helper.assertTrue(report.get(AwayReport.What.PATROLS) >= 1, "the away report should count the patrol: " + report.describe());
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
        });
        helper.runAfterDelay(4660, () -> {
            helper.assertTrue(antColony(helper) != null, "the queen should be back and alive");
            helper.succeed();
        });
    }

    /**
     * Brain step 4g, step 4, the harvester ants' patrol numbers (the user's decisions of 2026-10-18). 100 old worker
     * numbers, no real workers, the queen frozen and the colony away. Remembered attacks 9 blocks out in all 8 compass
     * directions, each with a husk really standing there out of sight. The dawn patrol of numbers checks its ways: some
     * never come back (each way through danger, up to half), the headcount drops by as many, their ways shut for the
     * day, and the away report counts them. The husks gone, the next morning's patrol all comes back.
     */
    @GameTest(template = "arena", timeoutTicks = 3600, skyAccess = true)
    public static void antPatrolNumbersAreLostOutOfSightOnWaysThroughDanger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        UUID[] queen = new UUID[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        List<net.minecraft.world.entity.Mob> husks = new ArrayList<>();
        int[] before = new int[1];
        int[] lostFirst = new int[1];
        TestClock clock = TestClock.moving(helper, 0.0);
        helper.onEachTick(() -> noRealWorkers(helper));
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1200, 100);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                int dx = (int) Math.round(Math.cos(angle) * 9);
                int dz = (int) Math.round(Math.sin(angle) * 9);
                BlockPos at = helper.absolutePos(new BlockPos(MIDDLE + dx, SURFACE, MIDDLE + dz));
                colony.danger().hurt(level, at, "husk", true);
                colony.danger().hurt(level, at, "husk", true);
                net.minecraft.world.entity.Mob husk = helper.spawn(EntityType.HUSK, MIDDLE + dx, SURFACE, MIDDLE + dz);
                husk.setNoAi(true);
                husk.setPersistenceRequired();
                husks.add(husk);
            }
            queen[0] = colony.getQueen(level).getUUID();
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
            colony.away().setAwayForTests(true);
            before[0] = colony.getCount(Caste.WORKER);
        });
        helper.runAfterDelay(60, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            clock.skipTo(8.0); // a dry morning in a hot biome: the patrol goes out
            colony.setHeatForTests(2.0F);
            colony.setRainForTests(false);
            colony.part(DesertParts.PATROL).orElseThrow().setOnForTests(true);
        });
        helper.runAfterDelay(1500, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            int lost = colony.danger().lostOutOfSight();
            AwayReport report = colony.away().report();
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4 patrol: {} lost, {}{}{}", lost, colony.part(DesertParts.PATROL).orElseThrow().describe(level), report.describe(),
                    colony.danger().describe(level));
            helper.assertTrue(colony.part(DesertParts.PATROL).orElseThrow().sentThisRound() > 0, "the dawn patrol should have gone out: " + colony.part(DesertParts.PATROL).orElseThrow().describe(level));
            helper.assertTrue(lost >= 1, "some patrol numbers should be lost on ways through danger: " + colony.part(DesertParts.PATROL).orElseThrow().describe(level));
            helper.assertTrue(report.get(AwayReport.What.LOST_IN_DANGER) == lost && report.get(AwayReport.What.PATROLLERS_LOST) >= lost,
                    "the away report should count the lost: " + report.describe());
            helper.assertTrue(colony.getCount(Caste.WORKER) == before[0] - lost, "the headcount should drop by the lost: "
                    + colony.getCount(Caste.WORKER) + ", was " + before[0]);
            helper.assertTrue(colony.part(DesertParts.PATROL).orElseThrow().dangerToday(), "a lost patroller should count as danger met today");
            lostFirst[0] = lost;
            husks.forEach(net.minecraft.world.entity.Entity::discard);
            clock.skipTo(0.0); // midnight: the round ends
        });
        helper.runAfterDelay(1560, () -> clock.skipTo(8.0)); // the next morning: a new day, a new patrol
        helper.runAfterDelay(3000, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4 patrol, the next morning: {}{}", colony.part(DesertParts.PATROL).orElseThrow().describe(level), colony.danger().describe(level));
            helper.assertTrue(colony.part(DesertParts.PATROL).orElseThrow().sentThisRound() > 0 && colony.part(DesertParts.PATROL).orElseThrow().backThisRound() > 0, "the next patrol should come back: "
                    + colony.part(DesertParts.PATROL).orElseThrow().describe(level));
            helper.assertTrue(colony.danger().lostOutOfSight() == lostFirst[0], "nobody should be lost once the husks are gone: "
                    + colony.danger().describe(level));
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
            helper.succeed();
        });
    }

    /**
     * Brain part 3, the ant colony away: everything at once, through a save and load. A harvester ant colony with 300
     * worker numbers of every age (nurses, builders, foragers, as the real division of labour), 48 seeds in the store,
     * its refuse chamber not dug yet, the queen in her room about to lay; 4 tufts of grass (2 seeds left on each) the
     * brain remembers, seen by a pretend player while the colony is here. It goes away (an egg and 2 hungry larvae as
     * numbers too). Partway the game is saved and loaded again, and the loaded colony lives on: the nurses mill seeds
     * into bread and feed the queen and the young, the queen's egg comes out, the 3 young grow up into workers (and hers too, in time),
     * the foragers bring in the seeds left on the remembered tufts (none were cut while the player watched), and the
     * builders' time is counted (nothing dug while away); the away report counts all of it, from before the save and
     * after. Back in range, out of sight: the refuse chamber is dug, its soil heaped on the hill, the eaten tufts are
     * gone, the young not grown up yet lie in the nursery, and the blocks have caught up with the counts.
     */
    @GameTest(template = "arena", timeoutTicks = 2200, skyAccess = true)
    public static void anAntColonyAwayLivesOnThroughASaveAndLoadAndCatchesUpOnReturn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        List<BlockPos> undug = new ArrayList<>();
        List<BlockPos> tufts = new ArrayList<>();
        UUID[] id = new UUID[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        int[] foodBefore = new int[1];
        int[] eggsLaid = new int[1];
        helper.onEachTick(() -> noRealWorkers(helper));
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            id[0] = colony.getId();
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.setHungerOn(true);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the queen's room should be dug");
            colony.getQueen(level).teleportTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            colony.queenCare().setQueenLastFed(level.getGameTime());
            NestPlan.Chamber refuse = colony.nest().getPlannedChambers(level).stream().filter(c -> c.kind() == NestPlan.Kind.REFUSE)
                    .findFirst().orElse(null);
            helper.assertTrue(refuse != null, "the nest should plan a refuse chamber");
            for (BlockPos cell : refuse.room()) {
                level.setBlock(cell, Blocks.DIRT.defaultBlockState(), 3);
                undug.add(cell);
            }
            long now = level.getGameTime();
            var worker = colony.profile().worker();
            colony.population().addNewMembers(Caste.WORKER, now, 100); // young: nurses
            colony.population().addNewMembers(Caste.WORKER, now - worker.nurseUntilTicks() - 1200, 100); // builders
            colony.population().addNewMembers(Caste.WORKER, now - worker.builderUntilTicks() - 1200, 100); // foragers
            List<BlockPos> store = colony.nest().getFoodStoreCells(level);
            for (int i = 0; i < 48; i++) {
                helper.assertTrue(colony.food().depositSeed(level, store.get(i % store.size())), "could not put a seed in the store");
            }
            for (int i = 0; i < 4; i++) {
                BlockPos tuft = helper.absolutePos(new BlockPos(1 + 2 * (i % 2), SURFACE, 1 + 2 * (i / 2)));
                level.setBlock(tuft, Blocks.SHORT_GRASS.defaultBlockState(), 3);
                colony.food().setPiecesCutForTests(tuft, preCut(colony));
                tufts.add(tuft);
            }
            // A pretend player looks at the tufts while the colony is here: the numbers leave them to the real foragers.
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, SURFACE + 2, 6)))));
            colony.food().rescanPlantsSoon();
            colony.field().setOnForTests(true);
        });
        helper.runAfterDelay(160, () -> {
            Colony colony = byId(helper, id[0]);
            for (BlockPos tuft : tufts) {
                helper.assertTrue(colony.field().remembers(tuft) && colony.food().getPiecesCut(tuft) == preCut(colony),
                        "the brain should remember the tuft and leave it while it is seen: " + tuft + " cut " + colony.food().getPiecesCut(tuft));
            }
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in her room");
            eggsLaid[0] = colony.queenCare().getEggsLaid();
            foodBefore[0] = colony.food().getFood();
            colony.queenCare().setEggDueForTests(level.getGameTime() + 60);
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
            colony.away().setAwayForTests(true);
            colony.population().setViewersForTests(List.of()); // the player has gone far away
            colony.brood().addInsideForTests(Brood.Young.egg(100));
            colony.brood().addInsideForTests(Brood.Young.larva(0, 0, null));
            colony.brood().addInsideForTests(Brood.Young.larva(0, 0, null));
        });
        helper.runAfterDelay(400, () -> {
            Colony colony = byId(helper, id[0]);
            AntColoniesMod.LOGGER.info("[away-test] part 3 ants, before the save: {}{}{}", colony.population().describe(level),
                    colony.away().describe(level), colony.field().describe());
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            helper.assertTrue(!colony.away().report().isEmpty() && colony.building().hasWork(),
                    "something should have happened before the save: " + colony.away().report().describe() + ", building " + colony.building().work());
            // The game is saved and loaded again: the loaded colony takes over (the test switches are set again).
            Colony copy = saveAndLoad(helper, colony);
            copy.away().setOnForTests(true);
            copy.away().setGraceForTests(20L);
            copy.away().setAwayForTests(true);
            copy.population().setOnForTests(false);
            copy.population().setViewersForTests(List.of());
            copy.field().setOnForTests(true);
            copy.setHungerOn(true);
            helper.assertTrue(copy.getCount(Caste.WORKER) == colony.getCount(Caste.WORKER)
                    && copy.brood().insideCount() == colony.brood().insideCount() && copy.building().work() == colony.building().work()
                    && copy.away().report().describe().equals(colony.away().report().describe())
                    && copy.ledger().describe().equals(colony.ledger().describe()),
                    "the loaded colony should be the same: " + copy.population().describe(level) + copy.away().describe(level));
        });
        helper.runAfterDelay(1600, () -> {
            Colony colony = byId(helper, id[0]);
            AntColoniesMod.LOGGER.info("[away-test] part 3 ants, after the save: food {}{}{}{}", colony.food().getFood(),
                    colony.population().describe(level), colony.away().describe(level), colony.field().describe());
            helper.assertTrue(colony.away().isAway(level), "the colony should still be away");
            AwayReport report = colony.away().report();
            // (the queen's own egg may have grown up too by now)
            int grew = report.get(AwayReport.What.GREW_UP);
            helper.assertTrue(grew >= 3 && colony.getCount(Caste.WORKER) == 300 + grew && colony.population().inside(Caste.WORKER) == 300 + grew,
                    "the egg and the 2 larvae should have grown up into workers (300 and those grown up): "
                            + colony.getCount(Caste.WORKER) + ", " + report.describe());
            helper.assertTrue(colony.queenCare().getEggsLaid() == eggsLaid[0] + 1 && report.get(AwayReport.What.EGGS_LAID) == 1,
                    "the queen's egg should have come out while away (before the save) and be counted: " + report.describe());
            helper.assertTrue(report.get(AwayReport.What.QUEEN_FED) > 0 && report.get(AwayReport.What.YOUNG_FED) > 0
                    && report.get(AwayReport.What.BREAD_MADE) > 0, "the nurses should mill bread and feed the queen and the young: " + report.describe());
            helper.assertTrue(report.get(AwayReport.What.FORAGED) == 4 * leftOn(colony) && report.get(AwayReport.What.PLANTS_USED_UP) == 4,
                    "the foragers should bring in the seeds left on the remembered tufts: " + report.describe());
            helper.assertTrue(colony.food().getFood() + report.get(AwayReport.What.BREAD_MADE) == foodBefore[0] + 4 * leftOn(colony),
                    "every seed brought in should be in the store or milled: " + colony.food().getFood() + " food, " + report.describe()
                            + ", was " + foodBefore[0]);
            helper.assertTrue(undug.stream().allMatch(cell -> level.getBlockState(cell).is(Blocks.DIRT)) && hillBlocks(level, colony) == 0
                    && colony.building().work() > 0, "nothing should be dug while away, but the builders' time counted: " + colony.building().work());
            colony.building().setWorkForTests(colony.building().work() + 50000); // (longer away than the test waits)
            // Back in range, nobody near.
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
        });
        helper.runAfterDelay(1900, () -> {
            Colony colony = byId(helper, id[0]);
            AwayReport report = colony.away().report();
            AntColoniesMod.LOGGER.info("[away-test] part 3 ants back: hill {}, {}", hillBlocks(level, colony), colony.away().describe(level));
            helper.assertTrue(colony.getQueen(level) != null, "the queen should be back and alive");
            List<BlockPos> left = undug.stream().filter(cell -> !level.getBlockState(cell).isAir()).toList();
            helper.assertTrue(left.isEmpty() && report.get(AwayReport.What.DUG) == undug.size(), "the refuse chamber should be dug: "
                    + left.size() + " of " + undug.size() + " left, " + report.describe());
            helper.assertTrue(hillBlocks(level, colony) > 0 && report.get(AwayReport.What.HILL) == hillBlocks(level, colony),
                    "its soil should be heaped on the hill: " + hillBlocks(level, colony) + " blocks, " + report.describe());
            for (BlockPos tuft : tufts) {
                helper.assertTrue(level.getBlockState(tuft).isAir(), "an eaten-up tuft should be gone once out of sight: " + tuft);
            }
            int young = 4 - report.get(AwayReport.What.GREW_UP); // (the 3 put in and the queen's egg, less those grown up)
            int real = colony.brood().countEggs(level) + colony.brood().countLarvae(level);
            helper.assertTrue(colony.brood().insideCount() == 0 && real == young, "the young not grown up yet (" + young
                    + ") should lie in the nursery: " + real + " real, " + colony.brood().insideCount() + " inside");
            helper.assertTrue(colony.ledger().isEmpty(), "the blocks should have caught up with the counts: " + colony.ledger().describe());
            helper.succeed();
        });
    }

    /**
     * Brain part 3, starving while away by the real rules. A harvester ant colony with 30 worker numbers (just fed), no
     * seeds and no bread at all, 3 hungry larvae as numbers (2 of them have gone almost their whole starve time without
     * food), and the queen in her room, long unfed and wanting food. While away the nurses have nothing to give: the 2
     * long-hungry larvae starve and leave bodies, the third lives on; the queen's hunger clock runs out and she starves
     * frozen in her room; the workers live. The report says so. Back in range she dies as her land loads, the colony
     * carries on without her (queenless), and the young's bodies lie in the refuse chamber.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aStarvingAntColonyAwayLosesItsYoungAndItsQueen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        UUID[] id = new UUID[1];
        UUID[] queen = new UUID[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            id[0] = colony.getId();
            colony.population().setOnForTests(false);
            colony.setHungerOn(true);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the queen's room should be dug");
            colony.getQueen(level).teleportTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime(), 30);
            helper.assertTrue(colony.food().getFood() == 0 && colony.part(DesertParts.BAKERY).orElseThrow().getBread() == 0, "the colony should have no food");
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = byId(helper, id[0]);
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in her room");
            queen[0] = colony.getQueenId();
            colony.queenCare().setEggDueForTests(-1); // (she carries no egg: she wants food)
            colony.queenCare().setQueenLastFed(level.getGameTime() - colony.profile().queen().starveTicks() + 200);
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
            colony.away().setAwayForTests(true);
            long starve = colony.profile().brood().larvaStarveTicks();
            colony.brood().addInsideForTests(Brood.Young.larva(0, starve - 100, null));
            colony.brood().addInsideForTests(Brood.Young.larva(0, starve - 100, null));
            colony.brood().addInsideForTests(Brood.Young.larva(0, 0, null));
        });
        helper.runAfterDelay(500, () -> {
            Colony colony = byId(helper, id[0]);
            AwayReport report = colony.away().report();
            AntColoniesMod.LOGGER.info("[away-test] part 3 starving: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            helper.assertTrue(report.get(AwayReport.What.YOUNG_STARVED) == 2 && colony.ledger().bodies() == 2,
                    "the 2 long-hungry larvae should starve and leave bodies: " + report.describe() + ", " + colony.ledger().describe());
            List<Brood.Young> young = colony.brood().youngInsideForTests();
            helper.assertTrue(young.size() == 1 && !young.get(0).isEgg(), "the third larva should live on: " + young);
            helper.assertTrue(report.describe().contains("the queen starved"), "the queen should starve while away: " + report.describe());
            helper.assertTrue(report.get(AwayReport.What.QUEEN_FED) == 0 && report.get(AwayReport.What.YOUNG_FED) == 0,
                    "there was nothing to feed anyone: " + report.describe());
            helper.assertTrue(colony.getCount(Caste.WORKER) == 30 && report.get(AwayReport.What.STARVED) == 0,
                    "the workers, just fed, should live: " + colony.getCount(Caste.WORKER));
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
        });
        helper.runAfterDelay(650, () -> {
            Colony colony = byId(helper, id[0]);
            Entity theQueen = level.getEntity(queen[0]);
            AntColoniesMod.LOGGER.info("[away-test] part 3 starving, back: queen {}, {}{}", theQueen, colony.population().describe(level),
                    colony.away().describe(level));
            helper.assertTrue(theQueen == null || !theQueen.isAlive(), "the queen who starved while away should die as her land loads");
            helper.assertTrue(colony.isQueenless(), "the colony should carry on without her");
            helper.assertTrue(colony.getCount(Caste.WORKER) == 30, "the workers should carry on: " + colony.getCount(Caste.WORKER));
            helper.assertTrue(colony.refuse().getRefuseCorpses(level).size() >= 2, "the young's bodies should lie in the refuse chamber: "
                    + colony.refuse().getRefuseCorpses(level).size() + ", " + colony.ledger().describe());
            helper.assertTrue(colony.away().report().describe().contains("the queen starved"), "the report should still say so after coming back");
            helper.succeed();
        });
    }

    /**
     * Brain part 3: a lone harvester ant queen raises her first workers while away, and they dig the food store, forage
     * and live on past their starve time (see {@link #foundsWhileAway}).
     */
    @GameTest(template = "arena", timeoutTicks = 160000, skyAccess = true)
    public static void aFoundingAntQueenRaisesHerFirstWorkersWhileAway(GameTestHelper helper) {
        foundsWhileAway(helper, new AwayKit.Founders(com.antcolonies.species.Species.RED_HARVESTER_ANT,
                h -> h.spawn(DesertEntities.QUEEN_ANT.get(), MIDDLE, SURFACE, MIDDLE), null, null, false));
    }
}
