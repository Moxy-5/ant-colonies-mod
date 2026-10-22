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
                    colony.away().describe(level), colony.food().getFood(), colony.part(ColonyParts.BAKERY).orElseThrow().getBread(), colony.queenCare().getQueenBread());
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

    /** How many blocks of the hill (burrowed dirt) stand round the entrance. */
    static int hillBlocks(ServerLevel level, Colony colony) {
        int count = 0;
        int r = com.antcolonies.colony.Hill.MOUND_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(colony.getCenter().offset(-r, 0, -r), colony.getCenter().offset(r, 4, r))) {
            if (level.getBlockState(pos).is(com.antcolonies.registry.ModBlocks.BURROWED_DIRT.get())) {
                count++;
            }
        }
        return count;
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
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 100000, 40); // middle-aged: builders
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

    /** Every real worker of the test's colonies leaves the world: only the numbers may forage in these tests. */
    static void noRealWorkers(GameTestHelper helper) {
        for (AbstractAntEntity worker : helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds(),
                mob -> mob instanceof WorkerAntEntity || mob instanceof TermiteWorkerEntity)) {
            worker.discard();
        }
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
                        colony.away().report().describe(), colony.part(ColonyParts.FORAGER_SPOTS).orElseThrow().describe(), colony.field().describe());
            }
        });
        helper.onEachTick(() -> {
            if (!patrolOn[0]) {
                return;
            }
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElse(null);
            if (colony != null && colony.part(ColonyParts.PATROL).orElseThrow().backThisRound() == 0 && colony.away().report().get(AwayReport.What.FORAGED) > 0) {
                foragedBeforePatrol[0] = true;
            }
        });
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            long old = level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1200;
            colony.population().addNewMembers(WorkerAntEntity.CASTE, old, 60);
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
            colony.part(ColonyParts.PATROL).orElseThrow().setOnForTests(true); // brain step 4b: a patrol first (the round begins as the patrol sees the day clock open)
            patrolOn[0] = true;
        });
        helper.runAfterDelay(2660, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] 4b: {}{}", colony.part(ColonyParts.PATROL).orElseThrow().describe(level), colony.part(ColonyParts.DOOR).orElseThrow().describe(level));
            helper.assertTrue(colony.part(ColonyParts.PATROL).orElseThrow().isRoundOn() && colony.part(ColonyParts.PATROL).orElseThrow().numbersOut() > 0 && colony.part(ColonyParts.PATROL).orElseThrow().realOut().isEmpty(),
                    "the numbers should send a patrol of numbers first: " + colony.part(ColonyParts.PATROL).orElseThrow().describe(level));
            helper.assertTrue(colony.part(ColonyParts.PATROL).orElseThrow().sentThisRound() == colony.profile().food().patrol().orElseThrow().dawnPatrol(colony.getCount(WorkerAntEntity.CASTE)),
                    "the dawn patrol should be the share of the workers: " + colony.part(ColonyParts.PATROL).orElseThrow().describe(level));
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

    /** Brain step 4b: out of the harvester ants' foraging hours the numbers bring nothing in from the remembered tufts. */
    private static void checkNothingForaged(GameTestHelper helper, Colony colony, List<BlockPos> tufts, String when) {
        for (BlockPos tuft : tufts) {
            helper.assertTrue(colony.food().getPiecesCut(tuft) == preCut(colony), "no tuft should be foraged " + when + ": " + tuft + " cut " + colony.food().getPiecesCut(tuft));
        }
        helper.assertTrue(colony.away().report().get(AwayReport.What.FORAGED) == 0, "nothing should be brought in " + when + ": " + colony.away().report().describe());
    }

    /**
     * Step 2c, the termites. 60 old workers in the numbers, no real workers, and 4 tufts of dry grass round the mound,
     * each with 2 of its 4 pieces cut already; the colony knows 3 of them (the 4th nobody has found). By day nothing is
     * foraged (dry grass cannot be eaten from inside). At night the numbers eat the 3 known tufts up (6 pieces into the
     * comb's count); they vanish out of sight and are remembered to grow back 3 days after they were eaten; the unknown
     * tuft is left.
     * <p>Brain part 3, in the same scene: then the species' own rule decides (the profile's {@code forage_at}, with a
     * test clock and a pretend rain): on a dry day 2 more known tufts are left; in the rain by day they are eaten (real Macrotermes forage
     * in the wet); on a dry night with the alarm at 2 nothing is foraged; the alarm calmed but the store full (as the
     * brain last saw it), nothing either; with room again, 2 more tufts are eaten up.
     */
    @GameTest(template = "arena", timeoutTicks = 4900, skyAccess = true)
    public static void termiteNumbersForageKnownDryGrassOnlyAtNightOrInTheRain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        List<BlockPos> tufts = new ArrayList<>();
        int[] foodBefore = new int[1];
        long[] nightFrom = new long[1];
        List<BlockPos> more = new ArrayList<>(); // (brain part 3: the tufts of the later phases)
        TestClock clock = TestClock.moving(helper, 12.0); // midday
        helper.onEachTick(() -> noRealWorkers(helper));
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().setViewersForTests(List.of());
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 250000, 60);
            // Step 2c's rule with the 2 holes of before (the galleries' night holes have their own scene, step 4 below).
            colony.nest().galleries().setSwitchForTests(false);
            int[][] spots = {{MIDDLE + 6, MIDDLE}, {MIDDLE - 6, MIDDLE}, {MIDDLE, MIDDLE + 6}, {MIDDLE, MIDDLE - 6}};
            for (int[] spot : spots) {
                BlockPos tuft = helper.absolutePos(new BlockPos(spot[0], SURFACE, spot[1]));
                level.setBlock(tuft.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(tuft, com.antcolonies.registry.ModBlocks.DRY_GRASS.get().defaultBlockState(), 3);
                colony.food().setPiecesCutForTests(tuft, 2);
                tufts.add(tuft);
            }
            for (int i = 0; i < 3; i++) {
                colony.foodMap().knowForTests(tufts.get(i));
            }
            colony.food().rescanPlantsSoon();
            foodBefore[0] = colony.food().getFood();
            colony.field().setOnForTests(true);
        });
        helper.runAfterDelay(500, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            AntColoniesMod.LOGGER.info("[away-test] 2c termites by day: food {}{}", colony.food().getFood(), colony.field().describe());
            for (BlockPos tuft : tufts) {
                helper.assertTrue(colony.food().getPiecesCut(tuft) == 2, "nothing should be foraged by day: " + tuft + " cut "
                        + colony.food().getPiecesCut(tuft));
            }
            helper.assertTrue(colony.field().remembers(tufts.get(0)) && !colony.field().remembers(tufts.get(3)),
                    "the brain should remember the known tufts only");
            helper.assertTrue(colony.food().getFood() == foodBefore[0], "no food by day: " + colony.food().getFood());
            clock.skipTo(colony, TestClock.Phase.NIGHT); // sunset
            nightFrom[0] = level.getGameTime();
        });
        helper.runAfterDelay(1500, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            AntColoniesMod.LOGGER.info("[away-test] 2c termites at night: food {}{}", colony.food().getFood(), colony.field().describe());
            long regrowth = colony.profile().food().regrowth().orElseThrow().ticks();
            for (int i = 0; i < 3; i++) {
                BlockPos tuft = tufts.get(i);
                helper.assertTrue(level.getBlockState(tuft).isAir(), "a known tuft should be eaten up at night and gone: " + tuft);
                long growsAt = colony.food().barePatches().growsAtForTests(tuft);
                helper.assertTrue(growsAt > nightFrom[0] + regrowth && growsAt <= level.getGameTime() + regrowth,
                        "the tuft should grow back 3 days after it was eaten: " + growsAt);
                helper.assertTrue(!colony.foodMap().isKnown(tuft), "an eaten-up tuft should be forgotten: " + tuft);
            }
            helper.assertTrue(level.getBlockState(tufts.get(3)).is(com.antcolonies.registry.ModBlocks.DRY_GRASS.get())
                    && colony.food().getPiecesCut(tufts.get(3)) == 2, "the tuft nobody found should be left: cut " + colony.food().getPiecesCut(tufts.get(3)));
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 6, "6 pieces should have come in: " + colony.food().getFood()
                    + ", was " + foodBefore[0]);
            // Brain part 3: the species' own rule with rain too: a dry day first (the next sunrise), 2 more known tufts.
            clock.skipTo(colony, TestClock.Phase.DAY);
            colony.setRainForTests(false);
            more.add(knownDryTuft(helper, colony, MIDDLE + 6, MIDDLE + 6));
            more.add(knownDryTuft(helper, colony, MIDDLE - 6, MIDDLE - 6));
        });
        helper.runAfterDelay(1800, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(!colony.isForagingTime(level), "a dry day should be no foraging time for termites");
            for (BlockPos tuft : more) {
                helper.assertTrue(colony.field().remembers(tuft) && colony.food().getPiecesCut(tuft) == 2,
                        "a known tuft should be remembered and left on a dry day: " + tuft + " cut " + colony.food().getPiecesCut(tuft));
            }
            colony.setRainForTests(true); // rain by day: real Macrotermes forage in the wet too
            foodBefore[0] = colony.food().getFood();
        });
        helper.runAfterDelay(2900, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            AntColoniesMod.LOGGER.info("[away-test] part 3 termites in the rain: food {}{}", colony.food().getFood(), colony.field().describe());
            helper.assertTrue(colony.isForagingTime(level), "rain should be foraging time for termites, by day too");
            for (BlockPos tuft : more) {
                helper.assertTrue(level.getBlockState(tuft).isAir(), "a known tuft should be eaten up in the rain: " + tuft);
            }
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 4, "4 pieces should have come in during the rain: "
                    + colony.food().getFood() + ", was " + foodBefore[0]);
            // A dry night, but an enemy at the foragers: the alarm at 2 keeps everyone in.
            more.clear();
            more.add(knownDryTuft(helper, colony, MIDDLE + 6, MIDDLE - 6));
            more.add(knownDryTuft(helper, colony, MIDDLE - 6, MIDDLE + 6));
            clock.skipTo(colony, TestClock.Phase.NIGHT); // a dry night
            colony.setRainForTests(false);
            colony.part(ColonyParts.ALARM).orElseThrow().raise(level, 2, colony.getCenter(), null);
            foodBefore[0] = colony.food().getFood();
        });
        helper.runAfterDelay(3200, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.part(ColonyParts.ALARM).orElseThrow().keepsEveryoneIn(level), "the alarm should still keep everyone in");
            for (BlockPos tuft : more) {
                helper.assertTrue(colony.field().remembers(tuft) && colony.food().getPiecesCut(tuft) == 2,
                        "nothing should be foraged while the alarm keeps everyone in: " + tuft + " cut " + colony.food().getPiecesCut(tuft));
            }
            // The alarm calms, but the food store is full (as the brain last saw it).
            colony.part(ColonyParts.ALARM).orElseThrow().calmForTests();
            colony.food().setRoomForTests(0);
        });
        helper.runAfterDelay(3500, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.isForagingTime(level), "a calm night should be foraging time");
            for (BlockPos tuft : more) {
                helper.assertTrue(colony.food().getPiecesCut(tuft) == 2, "nothing should be foraged while the store has no room: " + tuft
                        + " cut " + colony.food().getPiecesCut(tuft));
            }
            helper.assertTrue(colony.food().getFood() == foodBefore[0], "no food with the store full: " + colony.food().getFood());
            colony.food().setRoomForTests(null); // room again
        });
        helper.runAfterDelay(4600, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            for (BlockPos tuft : more) {
                helper.assertTrue(level.getBlockState(tuft).isAir(), "with room again the tuft should be eaten up: " + tuft);
            }
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 4, "4 pieces should have come in: " + colony.food().getFood()
                    + ", was " + foodBefore[0]);
            helper.succeed();
        });
    }

    /**
     * Termites underground step 4, away and speed (the user's answers, 2026-10-17). A Macrotermes colony with its nest
     * and its 4 arms of gallery dug, 38 worker numbers (30 old enough to forage), the galleries reaching 26 blocks. By
     * the farthest hole: a dry tuft the colony knows with 1 piece left, and one it does not know (the brain sees it
     * round the hole: its picture of the land). Far from every hole: another known tuft. The queen is frozen and the
     * colony goes away.
     * <p>A night away: the night's holes are picked from the holes the brain remembers dug, the one by known food among
     * them, the others from the outer half of the web first (the far holes); the numbers graze only round them (the far
     * tuft is left, the one by the hole eaten up); the unknown tuft by the hole is found; the report counts the night,
     * the holes and the find. Then 600 young builders join, and hay the colony knows lies far from every hole.
     * <p>Back: the builders' time grows the galleries out of sight: a piece out to the hay first, then more, each dug,
     * within the workers' budget and the reach; the report counts them.
     * <p>Away again: 11 days go by, a known tuft with 1 piece left lies by a grown far piece, and at night the numbers
     * eat it up from that piece's hole. Back: every far piece that led to no food is filled in at once (not one every 10
     * seconds), the piece foraged from while away stays, and the report counts the fills.
     */
    @GameTest(template = "arena", timeoutTicks = 9000, skyAccess = true)
    public static void aTermiteColonyAwayForagesFromTheNightsHolesAndGrowsItsGalleriesOnReturn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int mid = 32;
        int reach = 26;
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), mid, SURFACE, mid);
        TestClock clock = TestClock.still(helper, 12); // midday: the foragers stay in
        helper.onEachTick(() -> noRealWorkers(helper));
        Colony[] the = {null};
        CompoundTag[] queenOnDisk = {null};
        int[] stage = {0};
        long[] since = {0};
        BlockPos[] far = {null}; // the farthest hole
        BlockPos[] spot = new BlockPos[5]; // the known tuft by it, the unknown tuft by it, the far known tuft, the hay, the tuft by a grown piece
        BlockPos[] grown = {null}; // the grown far piece foraged from while away
        int[] dugBefore = {0};
        int[] lastLength = {-1};
        long[] agedAt = {0};
        int[] fillsBefore = {0};
        Set<BlockPos> piecesBefore = new HashSet<>();
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            the[0] = colony;
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().setViewersForTests(List.of());
            long now = level.getGameTime();
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - colony.profile().worker().builderUntilTicks() - 1000, 30);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - colony.profile().worker().nurseUntilTicks() - 1000, 8);
            TermiteSoldierTests.digNestAtOnce(level, colony); // (the nest of a colony with workers, not the founders' chamber)
            colony.nest().galleries().setSwitchForTests(true);
            colony.nest().galleries().setReachForTests(reach);
            colony.field().setOnForTests(true);
            stage[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = the[0];
            if (colony == null || stage[0] == 0) {
                return;
            }
            com.antcolonies.colony.Galleries galleries = colony.nest().galleries();
            List<NestPlan.ForagingTunnel> tunnels = colony.foragingHoles().tunnels(level);
            BlockPos center = colony.getCenter();
            int graze = colony.profile().nest().galleries().orElseThrow().foodRadius();
            int pieceLength = colony.profile().nest().galleries().orElseThrow().pieceLength();
            List<BlockPos> holes = tunnels.stream().map(NestPlan.ForagingTunnel::hole).distinct().toList();
            AwayReport report = colony.away().report();
            long tick = helper.getTick();
            if (tick % 200 == 0) {
                AntColoniesMod.LOGGER.info("[step4-test] tick {}: stage {}{} | {}", tick, stage[0], galleries.describe(level), report.describe());
            }
            switch (stage[0]) {
                case 1 -> { // the arms planned and dug (by the test, at once): then the tufts
                    for (NestPlan.ForagingTunnel tunnel : tunnels) {
                        for (BlockPos cell : galleries.digCells(tunnel)) {
                            if (!com.antcolonies.colony.Nest.isDugOut(level, cell)) {
                                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                    if (galleries.tipCount() < 3 || !galleries.dugHolesForTests().containsAll(holes)) {
                        helper.assertTrue(tick < 1500, "the arms were not planned and dug, or the brain does not remember their holes dug:"
                                + galleries.describe(level) + " remembered " + galleries.dugHolesForTests().size() + " of " + holes.size());
                        since[0] = 0;
                        return;
                    }
                    if (++since[0] < 60) {
                        return;
                    }
                    // The farthest hole with room beside it for a tuft no other hole grazes.
                    for (BlockPos hole : holes.stream().sorted(java.util.Comparator.comparingDouble(hole -> -flat(hole, center))).toList()) {
                        spot[0] = groundSpot(helper, colony, hole, 2, 4,
                                at -> holes.stream().allMatch(other -> other.equals(hole) || flat(other, at) > graze + 0.5));
                        if (spot[0] != null && flat(hole, center) >= galleries.reach() / 2.0) {
                            far[0] = hole;
                            break;
                        }
                    }
                    helper.assertTrue(far[0] != null, "no room for a tuft by a far hole:" + galleries.describe(level));
                    spot[1] = groundSpot(helper, colony, far[0], 2, 4, at -> flat(at, spot[0]) >= 2);
                    spot[2] = groundSpot(helper, colony, center, 9, 20, at -> holes.stream().allMatch(hole -> flat(hole, at) > graze + 2.5));
                    helper.assertTrue(spot[1] != null && spot[2] != null, "no room for the other tufts");
                    for (int i = 0; i < 3; i++) {
                        level.setBlock(spot[i], com.antcolonies.registry.ModBlocks.DRY_GRASS.get().defaultBlockState(), 3);
                        colony.food().setPiecesCutForTests(spot[i], i == 0 ? 3 : 2);
                        if (i != 1) {
                            colony.foodMap().knowForTests(spot[i]);
                        }
                    }
                    colony.food().rescanPlantsSoon();
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: far hole {}, tufts {}", tick, helper.relativePos(far[0]),
                            java.util.Arrays.stream(spot, 0, 3).map(helper::relativePos).toList());
                    since[0] = 0;
                    stage[0] = 2;
                }
                case 2 -> { // the brain has looked: then away
                    if (!colony.field().remembers(spot[0]) || !colony.field().remembers(spot[2]) || !colony.field().seesUnknown(spot[1])) {
                        helper.assertTrue(++since[0] < 600, "the brain did not see the tufts (known " + colony.field().remembers(spot[0]) + "/"
                                + colony.field().remembers(spot[2]) + ", the unknown one in its picture " + colony.field().seesUnknown(spot[1]) + ")");
                        return;
                    }
                    helper.assertTrue(!colony.foodMap().isKnown(spot[1]), "the colony should not know the tuft nobody found");
                    colony.away().setOnForTests(true);
                    colony.away().setGraceForTests(20L);
                    queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
                    colony.away().setAwayForTests(true);
                    since[0] = 0;
                    stage[0] = 3;
                }
                case 3 -> { // away: a night
                    if (!colony.away().isAway(level)) {
                        helper.assertTrue(++since[0] < 400, "the colony did not go away");
                        return;
                    }
                    clock.skipTo(colony, TestClock.Phase.NIGHT);
                    lastLength[0] = galleries.length();
                    since[0] = 0;
                    stage[0] = 4;
                }
                case 4 -> { // the night's holes, the graze round them, the find
                    // (the tuft eaten up takes the numbers a while: 400 ticks were not always enough, 2 runs in 4 cut 3 of
                    // its 4 pieces by then, docs/BUGS.md 50; they get up to 2000, as in stage 9)
                    boolean tuftEaten = colony.food().getPiecesCut(spot[0]) >= 4 || level.getBlockState(spot[0]).isAir();
                    if (++since[0] < 400 || !tuftEaten && since[0] < 2000) {
                        return;
                    }
                    Set<BlockPos> tonight = galleries.tonight();
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: tonight's holes {}, field {}", tick, tonight.stream().map(helper::relativePos).toList(),
                            colony.field().describe());
                    helper.assertTrue(!tonight.isEmpty() && galleries.dugHolesForTests().containsAll(tonight),
                            "the night's holes while away should be picked from the holes remembered dug: " + tonight);
                    helper.assertTrue(tonight.contains(far[0]), "the hole by the known tuft should open first: " + tonight);
                    List<BlockPos> unfed = tonight.stream().filter(hole -> flat(hole, spot[0]) > graze && flat(hole, spot[2]) > graze).toList();
                    boolean outerLeft = holes.stream().anyMatch(hole -> !hole.equals(far[0]) && flat(hole, center) >= galleries.reach() / 2.0);
                    helper.assertTrue(unfed.isEmpty() || !outerLeft || unfed.stream().anyMatch(hole -> flat(hole, center) >= galleries.reach() / 2.0),
                            "the holes opened to explore should come from the outer half first: " + unfed.stream().map(helper::relativePos).toList());
                    // (eaten up: every piece cut; the block goes once the land is shown again)
                    helper.assertTrue(colony.food().getPiecesCut(spot[0]) >= 4 || level.getBlockState(spot[0]).isAir(),
                            "the known tuft by the open hole should be eaten up: cut " + colony.food().getPiecesCut(spot[0]));
                    helper.assertTrue(colony.field().remembers(spot[2]) && colony.food().getPiecesCut(spot[2]) == 2,
                            "the known tuft far from every hole should be left (no hole open near it): cut " + colony.food().getPiecesCut(spot[2]));
                    helper.assertTrue(!colony.field().seesUnknown(spot[1]) && (colony.foodMap().isKnown(spot[1]) || level.getBlockState(spot[1]).isAir()
                            || colony.food().getPiecesCut(spot[1]) > 2), "the tuft by the open hole should have been found");
                    helper.assertTrue(report.get(AwayReport.What.FORAGING_NIGHTS) >= 1 && report.get(AwayReport.What.HOLES_OPENED) >= tonight.size()
                            && report.get(AwayReport.What.FOUND_FROM_HOLES) >= 1, "the report should count the night, its holes and the find: "
                            + report.describe());
                    // 600 young builders join, and hay the colony knows lies far from every hole.
                    clock.skipTo(colony, TestClock.Phase.DAY);
                    colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000, 600);
                    spot[3] = groundSpot(helper, colony, center, 17, 22, at -> holes.stream().allMatch(hole -> flat(hole, at) > 12) && flat(at, spot[2]) > 10);
                    helper.assertTrue(spot[3] != null, "no room for the hay far from every hole");
                    level.setBlock(spot[3], Blocks.HAY_BLOCK.defaultBlockState(), 3);
                    colony.foodMap().knowForTests(spot[3]);
                    since[0] = 0;
                    stage[0] = 5;
                }
                case 5 -> { // the builders work the time away: then back
                    if (++since[0] < 400) {
                        return;
                    }
                    helper.assertTrue(galleries.length() == lastLength[0], "the galleries grew while away (the land is not loaded then):"
                            + galleries.describe(level));
                    dugBefore[0] = dugPieces(level, galleries);
                    lastLength[0] = galleries.length();
                    colony.away().setAwayForTests(false);
                    loadAgain(level, queenOnDisk[0]);
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: back, {} pieces dug", tick, dugBefore[0]);
                    since[0] = 0;
                    stage[0] = 6;
                }
                case 6 -> { // the galleries grow out of sight with the builders' time
                    if (galleries.length() != lastLength[0]) {
                        lastLength[0] = galleries.length();
                        since[0] = 0;
                        return;
                    }
                    if (++since[0] < 300) {
                        return;
                    }
                    int pieces = report.get(AwayReport.What.GALLERY_PIECES);
                    helper.assertTrue(pieces >= 2 && report.get(AwayReport.What.GALLERY_BLOCKS) >= pieces,
                            "the builders' time should have grown the galleries on return: " + report.describe() + galleries.describe(level));
                    helper.assertTrue(report.get(AwayReport.What.GALLERY_TO_FOOD) >= 1, "a piece should have grown out to the known hay first: "
                            + report.describe());
                    com.antcolonies.colony.FoodWays.NewTunnel toHay = colony.nest().foodWays().getNewTunnels().stream()
                            .filter(tunnel -> tunnel.food().equals(spot[3])).findFirst().orElse(null);
                    helper.assertTrue(toHay != null && galleries.hasPiece(toHay.tunnel().hole()), "no gallery piece out to the hay:"
                            + galleries.describe(level));
                    helper.assertTrue(galleries.digCells(galleries.pieceAt(toHay.tunnel().hole())).stream()
                            .allMatch(cell -> com.antcolonies.colony.Nest.isDugOut(level, cell)), "the piece out to the hay should be dug out of sight");
                    int dug = dugPieces(level, galleries);
                    helper.assertTrue(dug >= dugBefore[0] + pieces - 1, "the pieces grown should be dug out of sight: " + dug + " dug, "
                            + dugBefore[0] + " before, " + pieces + " grown");
                    helper.assertTrue(galleries.length() <= galleries.lengthAllowed() + 2 * (pieceLength + 3),
                            "the galleries grew past what the workers allow:" + galleries.describe(level));
                    for (NestPlan.ForagingTunnel tunnel : tunnels) {
                        helper.assertTrue(flat(tunnel.hole(), center) <= reach + 2.5, "a hole at " + helper.relativePos(tunnel.hole())
                                + " lies past the reach of " + reach);
                    }
                    // Away again: a known tuft with 1 piece left by the farthest grown piece that leads on from no other, dug out
                    // (one grown piece may still wait to be dug, see above, and a hole not dug never opens).
                    for (BlockPos piece : galleries.pieceHoles()) {
                        piecesBefore.add(piece);
                        boolean pieceDug = galleries.digCells(galleries.pieceAt(piece)).stream().allMatch(cell -> com.antcolonies.colony.Nest.isDugOut(level, cell));
                        if (pieceDug && !galleries.isFirstArm(piece) && galleries.isLeaf(piece) && !piece.equals(toHay.tunnel().hole())
                                && (grown[0] == null || flat(piece, center) > flat(grown[0], center))) {
                            grown[0] = piece;
                        }
                    }
                    helper.assertTrue(grown[0] != null, "no grown far piece:" + galleries.describe(level));
                    spot[4] = groundSpot(helper, colony, grown[0], 2, 3.5, at -> true);
                    helper.assertTrue(spot[4] != null, "no room for a tuft by the grown piece " + helper.relativePos(grown[0]));
                    level.setBlock(spot[4], com.antcolonies.registry.ModBlocks.DRY_GRASS.get().defaultBlockState(), 3);
                    colony.food().setPiecesCutForTests(spot[4], 3);
                    colony.foodMap().knowForTests(spot[4]);
                    colony.food().rescanPlantsSoon();
                    colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1000, 300);
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: grown {}; a tuft by the piece to {}", tick, report.describe(), helper.relativePos(grown[0]));
                    since[0] = 0;
                    stage[0] = 7;
                }
                case 7 -> { // the brain has seen the tuft: away again
                    if (!colony.field().remembers(spot[4])) {
                        helper.assertTrue(++since[0] < 600, "the brain did not see the tuft by the grown piece");
                        return;
                    }
                    queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
                    colony.away().setAwayForTests(true);
                    since[0] = 0;
                    stage[0] = 8;
                }
                case 8 -> { // away: 11 days go by, then a night
                    if (!colony.away().isAway(level)) {
                        helper.assertTrue(++since[0] < 400, "the colony did not go away again");
                        return;
                    }
                    galleries.ageForTests(11 * 24000L);
                    agedAt[0] = level.getGameTime();
                    fillsBefore[0] = galleries.fills();
                    clock.skipTo(colony, TestClock.Phase.NIGHT);
                    since[0] = 0;
                    stage[0] = 9;
                }
                case 9 -> { // the numbers eat the tuft from the grown piece's hole (the hay first, it is richer): then back
                    boolean eaten = colony.food().getPiecesCut(spot[4]) >= 4 || level.getBlockState(spot[4]).isAir();
                    if (++since[0] < 100 || !eaten && since[0] < 2000) {
                        return;
                    }
                    helper.assertTrue(galleries.tonight().contains(grown[0]), "the grown piece's hole by known food should open: "
                            + galleries.tonight().stream().map(helper::relativePos).toList());
                    helper.assertTrue(colony.food().getPiecesCut(spot[4]) >= 4 || level.getBlockState(spot[4]).isAir(),
                            "the tuft by the grown piece should be eaten up: cut " + colony.food().getPiecesCut(spot[4]));
                    helper.assertTrue(galleries.fills() == fillsBefore[0], "nothing should be filled in while away (the land waits for the return)");
                    clock.skipTo(colony, TestClock.Phase.DAY);
                    colony.away().setAwayForTests(false);
                    loadAgain(level, queenOnDisk[0]);
                    since[0] = 0;
                    stage[0] = 10;
                }
                case 10 -> { // back: every branch due filled in at once
                    if (++since[0] < 200) {
                        return;
                    }
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: back again, {} filled in{} | {}", tick, galleries.fills() - fillsBefore[0],
                            galleries.describe(level), report.describe());
                    helper.assertTrue(galleries.hasPiece(grown[0]), "the piece the numbers foraged from while away was filled in");
                    int filled = galleries.fills() - fillsBefore[0];
                    helper.assertTrue(filled >= 1 && report.get(AwayReport.What.FILLED_IN) == filled && report.get(AwayReport.What.FILLED_BLOCKS) > 0,
                            "the branches due should be filled in on return and counted: " + filled + " filled; " + report.describe());
                    for (BlockPos piece : galleries.pieceHoles()) {
                        helper.assertTrue(!piecesBefore.contains(piece) || galleries.isFirstArm(piece) || galleries.lastUsed(piece) >= agedAt[0],
                                "the piece to " + helper.relativePos(piece) + " led to no food for 11 days and was not filled in at once:"
                                        + galleries.describe(level));
                    }
                    helper.succeed();
                }
                default -> {
                }
            }
        });
    }

    private static double flat(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** How many gallery pieces are dug all through. */
    private static int dugPieces(ServerLevel level, com.antcolonies.colony.Galleries galleries) {
        int dug = 0;
        for (BlockPos piece : galleries.pieceHoles()) {
            if (galleries.digCells(galleries.pieceAt(piece)).stream().allMatch(cell -> com.antcolonies.colony.Nest.isDugOut(level, cell))) {
                dug++;
            }
        }
        return dug;
    }

    /**
     * An open spot on the ground (air over a grass block, off every hole and tunnel, inside the arena) {@code minOut} to
     * {@code maxOut} blocks from {@code from} that {@code ok} accepts, or null.
     */
    private static BlockPos groundSpot(GameTestHelper helper, Colony colony, BlockPos from, double minOut, double maxOut,
            java.util.function.Predicate<BlockPos> ok) {
        ServerLevel level = helper.getLevel();
        int y = helper.absolutePos(new BlockPos(0, SURFACE, 0)).getY();
        Set<Long> columns = new HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            columns.add(BlockPos.asLong(tunnel.hole().getX(), 0, tunnel.hole().getZ()));
            tunnel.tunnel().forEach(cell -> columns.add(BlockPos.asLong(cell.getX(), 0, cell.getZ())));
        }
        AABB inside = helper.getBounds().inflate(-3, 0, -3);
        for (double r = minOut; r <= maxOut; r += 0.5) {
            for (int a = 0; a < 360; a += 7) {
                BlockPos at = new BlockPos((int) Math.round(from.getX() + r * Math.cos(Math.toRadians(a))), y,
                        (int) Math.round(from.getZ() + r * Math.sin(Math.toRadians(a))));
                double out = flat(at, from);
                if (out >= minOut && out <= maxOut && inside.contains(at.getX() + 0.5, inside.minY + 1, at.getZ() + 0.5)
                        && !columns.contains(BlockPos.asLong(at.getX(), 0, at.getZ())) && level.getBlockState(at).isAir()
                        && level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) && ok.test(at)) {
                    return at;
                }
            }
        }
        return null;
    }

    /**
     * Brain step 4g, step 4, harm out of sight (the user's decisions of 2026-10-18), the termites. 60 old worker numbers,
     * galleries off, and a place 10 blocks east where hostiles were seen 6 times (strength 6: not an attack, so no hole
     * closes and the termites still graze there). At night, with no hostile really there (loaded land), the numbers eat
     * two known tufts by it and nobody is lost: nobody dies of a mere memory. Then a husk stands there (out of sight):
     * at the next tufts some numbers are lost (no piece from those trips), the headcount drops by as many, and a body
     * found by nestmates (likely 10 blocks out) counts in the memory as a death. With the husk gone, the chance is 0 again.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void termiteNumbersAreLostOutOfSightOnlyWhereAHostileReallyIs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        BlockPos place = helper.absolutePos(new BlockPos(MIDDLE + 10, SURFACE, MIDDLE));
        List<BlockPos> first = new ArrayList<>();
        List<BlockPos> second = new ArrayList<>();
        int[] before = new int[2];
        net.minecraft.world.entity.Mob[] husk = new net.minecraft.world.entity.Mob[1];
        TestClock clock = TestClock.moving(helper, 12.0);
        helper.onEachTick(() -> noRealWorkers(helper));
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().setViewersForTests(List.of());
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 250000, 60);
            colony.nest().galleries().setSwitchForTests(false);
            for (int i = 0; i < 6; i++) {
                colony.danger().seen(level, place, UUID.randomUUID(), "husk"); // seen only: not an attack
            }
            first.add(knownDryTuft(helper, colony, MIDDLE + 10, MIDDLE - 3));
            first.add(knownDryTuft(helper, colony, MIDDLE + 10, MIDDLE + 3));
            colony.field().setOnForTests(true);
            before[0] = colony.getCount(WorkerAntEntity.CASTE);
            clock.skipTo(colony, TestClock.Phase.NIGHT);
        });
        helper.runAfterDelay(1100, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4, no hostile there: {}{}", colony.field().describe(), colony.danger().describe(level));
            for (BlockPos tuft : first) {
                helper.assertTrue(level.getBlockState(tuft).isAir(), "the tufts by the remembered place should be eaten (no hole closes for a sighting): " + tuft);
            }
            helper.assertTrue(colony.danger().lostOutOfSight() == 0 && colony.getCount(WorkerAntEntity.CASTE) == before[0],
                    "nobody should be lost where no hostile really is: " + colony.danger().describe(level));
            helper.assertTrue(colony.danger().lossChance(level, place, null) == 0, "no loss chance without a hostile there");
            // Now a husk really stands there, out of sight.
            husk[0] = helper.spawn(EntityType.HUSK, MIDDLE + 12, SURFACE, MIDDLE);
            husk[0].setNoAi(true);
            husk[0].setPersistenceRequired();
            helper.assertTrue(colony.danger().lossChance(level, place, null) >= 0.5, "a hostile there should make the full loss chance: "
                    + colony.danger().lossChance(level, place, null));
            int[][] spots = {{MIDDLE + 9, MIDDLE - 1}, {MIDDLE + 9, MIDDLE + 1}, {MIDDLE + 11, MIDDLE - 1}, {MIDDLE + 11, MIDDLE + 1}, {MIDDLE + 8, MIDDLE + 4}};
            for (int[] spot : spots) {
                second.add(knownDryTuft(helper, colony, spot[0], spot[1]));
            }
            before[1] = colony.getCount(WorkerAntEntity.CASTE);
        });
        helper.runAfterDelay(2400, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            int lost = colony.danger().lostOutOfSight();
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4, a husk there: {} lost, {}{}", lost, colony.field().describe(), colony.danger().describe(level));
            helper.assertTrue(lost >= 1, "some numbers should be lost where a hostile really is: " + colony.danger().describe(level));
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == before[1] - lost, "the headcount should drop by the lost: "
                    + colony.getCount(WorkerAntEntity.CASTE) + ", was " + before[1] + ", lost " + lost);
            boolean found = colony.danger().places().stream().anyMatch(p -> p.deaths() > 0);
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4: a body {}", found ? "was found (a death remembered)" : "was never found");
            husk[0].discard();
            helper.assertTrue(colony.danger().lossChance(level, place, null) == 0, "with the husk gone nobody should be lost there");
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
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1200, 100);
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
            before[0] = colony.getCount(WorkerAntEntity.CASTE);
        });
        helper.runAfterDelay(60, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            clock.skipTo(8.0); // a dry morning in a hot biome: the patrol goes out
            colony.setHeatForTests(2.0F);
            colony.setRainForTests(false);
            colony.part(ColonyParts.PATROL).orElseThrow().setOnForTests(true);
        });
        helper.runAfterDelay(1500, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            int lost = colony.danger().lostOutOfSight();
            AwayReport report = colony.away().report();
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4 patrol: {} lost, {}{}{}", lost, colony.part(ColonyParts.PATROL).orElseThrow().describe(level), report.describe(),
                    colony.danger().describe(level));
            helper.assertTrue(colony.part(ColonyParts.PATROL).orElseThrow().sentThisRound() > 0, "the dawn patrol should have gone out: " + colony.part(ColonyParts.PATROL).orElseThrow().describe(level));
            helper.assertTrue(lost >= 1, "some patrol numbers should be lost on ways through danger: " + colony.part(ColonyParts.PATROL).orElseThrow().describe(level));
            helper.assertTrue(report.get(AwayReport.What.LOST_IN_DANGER) == lost && report.get(AwayReport.What.PATROLLERS_LOST) >= lost,
                    "the away report should count the lost: " + report.describe());
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == before[0] - lost, "the headcount should drop by the lost: "
                    + colony.getCount(WorkerAntEntity.CASTE) + ", was " + before[0]);
            helper.assertTrue(colony.part(ColonyParts.PATROL).orElseThrow().dangerToday(), "a lost patroller should count as danger met today");
            lostFirst[0] = lost;
            husks.forEach(net.minecraft.world.entity.Entity::discard);
            clock.skipTo(0.0); // midnight: the round ends
        });
        helper.runAfterDelay(1560, () -> clock.skipTo(8.0)); // the next morning: a new day, a new patrol
        helper.runAfterDelay(3000, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4 patrol, the next morning: {}{}", colony.part(ColonyParts.PATROL).orElseThrow().describe(level), colony.danger().describe(level));
            helper.assertTrue(colony.part(ColonyParts.PATROL).orElseThrow().sentThisRound() > 0 && colony.part(ColonyParts.PATROL).orElseThrow().backThisRound() > 0, "the next patrol should come back: "
                    + colony.part(ColonyParts.PATROL).orElseThrow().describe(level));
            helper.assertTrue(colony.danger().lostOutOfSight() == lostFirst[0], "nobody should be lost once the husks are gone: "
                    + colony.danger().describe(level));
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
            helper.succeed();
        });
    }

    /** A tuft of dry grass (on a grass block) with 2 of its 4 pieces cut already, which the colony knows. */
    static BlockPos knownDryTuft(GameTestHelper helper, Colony colony, int x, int z) {
        BlockPos tuft = helper.absolutePos(new BlockPos(x, SURFACE, z));
        helper.getLevel().setBlock(tuft.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        helper.getLevel().setBlock(tuft, com.antcolonies.registry.ModBlocks.DRY_GRASS.get().defaultBlockState(), 3);
        colony.food().setPiecesCutForTests(tuft, 2);
        colony.foodMap().knowForTests(tuft);
        colony.food().rescanPlantsSoon();
        return tuft;
    }

    /** The comb blocks' layers by age and their nodules, added up: fresh, ripe, old, nodules, layers. */
    static int[] combTotals(ServerLevel level, Colony colony) {
        int[] totals = new int[5];
        for (BlockPos pos : colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getCombs(level)) {
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
     * soldiers a nodule each, and nobody is left hungry. The king lies beside the queen, a day and more since his last
     * nodule (brain part 4, the king fix): the nurse numbers feed him too, and he lives. Then (brain part 3) the game is
     * saved and loaded again while away, and the loaded copy carries on. Back in range, the blocks catch up: the ledger empties and the comb blocks
     * hold just the ages and nodules the brain counted.
     * <p>Step 2d in the same scene: the colony has earned the mound's second stage and nothing of it is built. While away
     * nothing is built (the land is not loaded), the builders' time is counted. Back in range a pretend player looks at the
     * mound's place: nothing goes up while it is seen. The player gone: the mound goes up out of sight, every block of it
     * finished and set (it had time to dry), its soil dug from the galleries under it, and the report counts it.
     */
    @GameTest(template = "arena", timeoutTicks = 1600)
    public static void aTermiteColonyAwayAgesItsCombFeedsItsQueenAndEatsAsNumbers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(2, () -> helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 1, SURFACE, MIDDLE));
        var foods = com.antcolonies.species.SpeciesProfile.MealFood.values();
        UUID[] queen = new UUID[1];
        UUID[] king = new UUID[1];
        long[] kingHungrySince = new long[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        CompoundTag[] kingOnDisk = new CompoundTag[1];
        int[] eggsLaid = new int[1];
        int[] foodBefore = new int[1];
        int[] grownBefore = new int[1];
        int[] lostBefore = new int[1];
        int[] eatenBefore = new int[2];
        int[] expected = new int[5]; // fresh, ripe, old, nodules, layers: what the brain counted when the colony came back
        boolean[] back = {false};
        long[] phase = {0, 0}; // step 2d after the comb: 1 watched, 2 the player gone; when it started
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            TestClock.still(helper, 12.0); // midday: the foragers stay in
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
            for (BlockPos comb : colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getCombs(level)) {
                if (com.antcolonies.block.FungusCombBlock.layers(level.getBlockState(comb)) < 4) {
                    continue; // (a comb not full yet stays all fresh)
                }
                // Aged comb: 2 old layers at the bottom, a ripe one, a fresh one on top; 3 nodules.
                level.setBlock(comb, level.getBlockState(comb).setValue(com.antcolonies.block.FungusCombBlock.NODULES, 3)
                        .setValue(com.antcolonies.block.FungusCombBlock.OLD, 2).setValue(com.antcolonies.block.FungusCombBlock.FRESH, 1), 3);
            }
            helper.assertTrue(colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getCombs(level).size() >= 5, "only " + colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getCombs(level).size() + " comb blocks");
            helper.assertTrue(colony.food().getFood() == combTotals(level, colony)[4], "the food count " + colony.food().getFood()
                    + " is not the " + combTotals(level, colony)[4] + " layers of comb");
        });
        helper.runAfterDelay(60, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in the royal chamber");
            helper.assertTrue(!colony.isFounding(), "the colony should have workers");
            AbstractAntEntity theQueen = colony.getQueen(level);
            queen[0] = theQueen.getUUID();
            // The king, a day and more since his last nodule: he wants one.
            var theKing = level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, helper.getBounds(),
                    mob -> colony.getId().equals(mob.getColonyId())).stream().findFirst().orElse(null);
            helper.assertTrue(theKing != null && colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) == 1, "the king should have joined");
            theKing.teleportTo(theQueen.getX() + 1.0, theQueen.getY(), theQueen.getZ());
            king[0] = theKing.getUUID();
            kingHungrySince[0] = level.getGameTime() - colony.profile().worker().mealIntervalTicks() - 100;
            colony.part(ColonyParts.KING_CARE).orElseThrow().setLastFed(kingHungrySince[0]);
            helper.assertTrue(colony.part(ColonyParts.KING_CARE).orElseThrow().wantsFood(level), "the king should want a nodule");
            eggsLaid[0] = colony.queenCare().getEggsLaid();
            colony.queenCare().setQueenLastFed(level.getGameTime());
            int[] blocks = combTotals(level, colony);
            helper.assertTrue(colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().freshKnown() == blocks[0] && colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() == blocks[1]
                    && colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().oldKnown() == blocks[2], "the brain should have seen the comb as it is: " + java.util.Arrays.toString(blocks)
                            + " vs " + colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().freshKnown() + "/" + colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() + "/" + colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().oldKnown());
            foodBefore[0] = colony.food().getFood();
            grownBefore[0] = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getNodulesGrown();
            colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(100.0F);
            colony.part(ColonyParts.MOUND).orElseThrow().setStage(level, 2);
            helper.assertTrue(colony.part(ColonyParts.MOUND).orElseThrow().countFinished(level) == 0, "no mound built yet");
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, theQueen);
            kingOnDisk[0] = freezeAndUnload(colony, level, theKing);
            colony.away().setAwayForTests(true);
        });
        helper.runAfterDelay(420, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] termites away: {}{} | known comb {}/{}/{}, nodules {}, food {}, queen food {}",
                    colony.population().describe(level), colony.away().describe(level), colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().freshKnown(), colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().ripeKnown(),
                    colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().oldKnown(), colony.life().nurseFoodLeft(level), colony.food().getFood(), colony.queenCare().getQueenBread());
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            var ledger = colony.ledger();
            helper.assertTrue(ledger.ripened() > 0, "fresh comb should have ripened while away: " + ledger.describe());
            helper.assertTrue(ledger.aged() > 0, "ripe comb should have gone old while away: " + ledger.describe());
            helper.assertTrue(colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getNodulesGrown() > grownBefore[0], "nodules should have grown while away");
            helper.assertTrue(ledger.store() < 0 && colony.food().getFood() == foodBefore[0] + ledger.store(),
                    "old comb should have crumbled, out of the food count: " + colony.food().getFood() + " food, was " + foodBefore[0] + "; "
                            + ledger.describe());
            int known = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().freshKnown() + colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() + colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().oldKnown();
            helper.assertTrue(known == colony.food().getFood(), "the known ages (" + known + " layers) should add up to the "
                    + colony.food().getFood() + " counted");
            helper.assertTrue(ledger.nurseFood() < 0 && colony.queenCare().getQueenBread() + 2 * (colony.queenCare().getEggsLaid() - eggsLaid[0]) > 0,
                    "the nurses should have fed the queen nodules: " + ledger.describe() + ", she holds " + colony.queenCare().getQueenBread());
            helper.assertTrue(colony.away().report().get(AwayReport.What.KING_FED) >= 1 && colony.part(ColonyParts.KING_CARE).orElseThrow().getLastFed() > kingHungrySince[0]
                    && !colony.part(ColonyParts.KING_CARE).orElseThrow().wantsFood(level), "the nurses should have fed the king a nodule while away: " + colony.away().report().describe());
            helper.assertTrue(!colony.away().report().didKingStarve(), "the king should not starve");
            // The meals come due: the workers' and the soldiers'.
            long due = level.getGameTime() - colony.profile().worker().mealIntervalTicks() - 30;
            colony.population().setLastAteForTests(level, WorkerAntEntity.CASTE, due);
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                colony.population().setLastAteForTests(level, rank.caste(), due);
            }
            eatenBefore[0] = colony.workerFood().getPiecesEaten(foods[0]);
            eatenBefore[1] = colony.workerFood().getPiecesEaten(foods[1]);
        });
        helper.runAfterDelay(480, () -> {
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
            int known = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().freshKnown() + colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() + colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().oldKnown();
            helper.assertTrue(known == colony.food().getFood(), "after the meals the known ages (" + known + " layers) should still add up to the "
                    + colony.food().getFood() + " counted");
            // Brain part 3: the game is saved and loaded again while away; the loaded colony comes back below (the comb
            // blocks must then show what the copy counted, and the mound must go up from the copy's building time).
            Colony original = colony;
            colony = saveAndLoad(helper, colony);
            colony.setHungerOn(true);
            colony.population().setOnForTests(false);
            colony.population().setViewersForTests(List.of());
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            colony.away().setAwayForTests(true);
            helper.assertTrue(colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().freshKnown() == original.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().freshKnown() && colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() == original.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().ripeKnown()
                    && colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().oldKnown() == original.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().oldKnown() && colony.life().nurseFoodLeft(level) == original.life().nurseFoodLeft(level)
                    && colony.food().getFood() == original.food().getFood() && colony.building().work() == original.building().work()
                    && colony.queenCare().getEggsLaid() == original.queenCare().getEggsLaid()
                    && colony.brood().insideCount() == original.brood().insideCount() && colony.population().hungry(level) == 0,
                    "the loaded colony should know its comb, nodules, food, eggs, young, meals and building time as before the save");
            // Back in range: what the brain counted is what the blocks must show once they have caught up.
            helper.assertTrue(!colony.ledger().isEmpty(), "the blocks should owe something");
            helper.assertTrue(colony.part(ColonyParts.MOUND).orElseThrow().countFinished(level) == 0 && colony.building().hasWork(),
                    "nothing of the mound should be built while away, but the builders' time counted: " + colony.building().work());
            colony.building().setWorkForTests(colony.building().work() + 100000); // (longer away than the test waits)
            // A pretend player looks at the mound's place from 7 blocks off.
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(colony.getCenter().offset(7, 2, 0))));
            expected[0] = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().freshKnown();
            expected[1] = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().ripeKnown();
            expected[2] = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().oldKnown();
            expected[3] = colony.life().nurseFoodLeft(level);
            expected[4] = colony.food().getFood();
            grownBefore[0] = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getNodulesGrown();
            lostBefore[0] = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getNodulesLost();
            colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(0.0F); // (only nodules may still grow: counted below)
            helper.assertTrue(colony.part(ColonyParts.KING_CARE).orElseThrow().getLastFed() == original.part(ColonyParts.KING_CARE).orElseThrow().getLastFed(), "the loaded colony should know when the king ate");
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
            loadAgain(level, kingOnDisk[0]);
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
            if (phase[0] > 0) {
                moundOnReturn(helper, colony, phase);
                return;
            }
            int[] blocks = combTotals(level, colony);
            int grown = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getNodulesGrown() - grownBefore[0]; // (each turned a ripe layer old)
            int lost = colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().getNodulesLost() - lostBefore[0]; // (on comb eaten or crumbled right away)
            AntColoniesMod.LOGGER.info("[away-test] termites back: blocks {} (fresh, ripe, old, nodules, layers), counted {}, {} nodules grown since, {} lost",
                    java.util.Arrays.toString(blocks), java.util.Arrays.toString(expected), grown, lost);
            helper.assertTrue(blocks[4] == expected[4] && blocks[4] == colony.food().getFood(), "the comb should have " + expected[4]
                    + " layers as counted: " + java.util.Arrays.toString(blocks) + ", food " + colony.food().getFood());
            helper.assertTrue(blocks[0] == expected[0] && blocks[1] == expected[1] - grown && blocks[2] == expected[2] + grown,
                    "the comb's ages should be as counted: blocks " + java.util.Arrays.toString(blocks) + ", counted " + java.util.Arrays.toString(expected)
                            + ", " + grown + " nodules grown since");
            helper.assertTrue(blocks[3] == expected[3] + grown - lost, "the nodules should be as counted: blocks " + java.util.Arrays.toString(blocks)
                    + ", counted " + java.util.Arrays.toString(expected) + ", " + grown + " grown since, " + lost + " lost with used-up comb");
            Entity theKing = level.getEntity(king[0]);
            helper.assertTrue(theKing != null && theKing.isAlive() && colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) == 1,
                    "the king, fed while away, should be alive when his land loads: " + theKing);
            colony.part(ColonyParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(1.0F);
            phase[0] = 1;
            phase[1] = level.getGameTime();
        });
    }

    /** Step 2d in the termite scene (see above): nothing while the player looks, then the mound out of sight. */
    private static void moundOnReturn(GameTestHelper helper, Colony colony, long[] phase) {
        ServerLevel level = helper.getLevel();
        long since = level.getGameTime() - phase[1];
        AwayReport report = colony.away().report();
        if (phase[0] == 1 && since >= 60) {
            AntColoniesMod.LOGGER.info("[away-test] 2d termites back, watched: mound {}, {}", colony.part(ColonyParts.MOUND).orElseThrow().countFinished(level),
                    colony.away().describe(level));
            helper.assertTrue(colony.part(ColonyParts.MOUND).orElseThrow().countFinished(level) == 0 && report.get(AwayReport.What.MOUND_BUILT) == 0,
                    "nothing of the mound should go up while a player looks: " + colony.part(ColonyParts.MOUND).orElseThrow().countFinished(level) + " built");
            colony.population().setViewersForTests(List.of());
            phase[0] = 2;
            phase[1] = level.getGameTime();
        } else if (phase[0] == 2 && (!colony.building().hasWork() || since >= 300)) {
            int finished = colony.part(ColonyParts.MOUND).orElseThrow().countFinished(level);
            AntColoniesMod.LOGGER.info("[away-test] 2d termites back: mound {} blocks, stage {}, {}", finished, colony.part(ColonyParts.MOUND).orElseThrow().getStage(level),
                    colony.away().describe(level));
            helper.assertTrue(finished > 0 && report.get(AwayReport.What.MOUND_BUILT) > 0, "the mound should have gone up out of sight: "
                    + finished + " blocks, " + report.describe());
            for (BlockPos pos : colony.part(ColonyParts.MOUND).orElseThrow().getParts(level).keySet()) {
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof com.antcolonies.block.TermiteMoundBlock) {
                    helper.assertTrue(com.antcolonies.block.TermiteMoundBlock.isFinished(state)
                            && state.getValue(com.antcolonies.block.TermiteMoundBlock.AGE) >= 1, "a mound block built while away should be finished and set: "
                                    + pos + " " + state);
                }
            }
            helper.assertTrue(report.get(AwayReport.What.DUG) >= finished, "its soil should have been dug (the galleries under it): "
                    + report.describe());
            helper.succeed();
        }
    }

    // --- brain part 3: the brain's own tests (the user's plan, 2026-10-15) ----------------------------------------

    /** The save as text with every list sorted and every key in order, so two saves of the same colony read the same. */
    static String canon(net.minecraft.nbt.Tag tag) {
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            java.util.Collections.sort(keys);
            StringBuilder text = new StringBuilder("{");
            for (String key : keys) {
                text.append(key).append(':').append(canon(compound.get(key))).append(',');
            }
            return text.append('}').toString();
        }
        if (tag instanceof net.minecraft.nbt.ListTag list) {
            List<String> items = new ArrayList<>();
            list.forEach(item -> items.add(canon(item)));
            java.util.Collections.sort(items); // (a map saved as a list: its order may change from one load to the next)
            return items.toString();
        }
        return tag.toString();
    }

    /**
     * The game is saved and loaded again while the colony is away (the player quit far from it and came back another
     * day): the colony is written to the save, read back as a new copy, and the copy takes its place. Checked: the copy
     * writes exactly the same save (nothing saved is lost or read back wrong); the parts that differ are named. The test
     * switches (never saved) must be set again on the copy by the caller.
     */
    static Colony saveAndLoad(GameTestHelper helper, Colony colony) {
        partsAreTheSpecies(helper, colony);
        CompoundTag saved = colony.save();
        Colony copy = Colony.load(saved);
        CompoundTag again = copy.save();
        List<String> differ = new ArrayList<>();
        Set<String> keys = new HashSet<>(saved.getAllKeys());
        keys.addAll(again.getAllKeys());
        for (String key : keys) {
            net.minecraft.nbt.Tag before = saved.get(key);
            net.minecraft.nbt.Tag after = again.get(key);
            if (before == null || after == null || !canon(before).equals(canon(after))) {
                differ.add(key + ": " + before + " -> " + after);
            }
        }
        helper.assertTrue(differ.isEmpty(), "the colony loaded from its save should save the same: " + differ);
        everyPartSavesAndLoads(helper, saved);
        ColonyManager.get(helper.getLevel()).replaceForTests(copy);
        return copy;
    }

    /**
     * Project Apex, colony parts steps 2 and 3: the colony has the shared parts and the parts its species file lists, no
     * others, and asking for a part gives one exactly when the colony has it (nothing is made for a part it does not list).
     */
    static void partsAreTheSpecies(GameTestHelper helper, Colony colony) {
        Set<String> wanted = new HashSet<>(colony.profile().parts());
        com.antcolonies.colony.ColonyParts.all().stream().filter(com.antcolonies.colony.ColonyParts.Type::shared)
                .forEach(type -> wanted.add(type.name()));
        Set<String> has = new HashSet<>();
        colony.parts().forEach(part -> has.add(part.name()));
        helper.assertTrue(has.equals(wanted), "a " + colony.getSpecies() + " colony should have the shared parts and the ones its species lists: has "
                + has + ", wanted " + wanted);
        for (com.antcolonies.colony.ColonyParts.Type<?> type : com.antcolonies.colony.ColonyParts.all()) {
            helper.assertTrue(colony.part(type).isPresent() == wanted.contains(type.name()) && colony.has(type) == wanted.contains(type.name()),
                    "a " + colony.getSpecies() + " colony answers wrongly when asked for the part " + type.name());
        }
        // Colony parts step 4, the colony news: each kind is heard by the parts the colony has that used to be called by
        // hand, in the order they were called; a kind nobody hears is not sent at all.
        Map<com.antcolonies.colony.ColonyNews.Kind<?>, List<String>> order = Map.of(
                com.antcolonies.colony.ColonyNews.MEMBER_HURT, List.of("alarm", "patrol", "danger_map", "forager_spots"),
                com.antcolonies.colony.ColonyNews.MEMBER_DIED, List.of("patrol", "forager_spots", "recruits"),
                com.antcolonies.colony.ColonyNews.FOOD_FOUND, List.of("forager_spots", "recruits"),
                com.antcolonies.colony.ColonyNews.NEIGHBOUR_MET, List.of("neighbours"));
        for (Map.Entry<com.antcolonies.colony.ColonyNews.Kind<?>, List<String>> kind : order.entrySet()) {
            List<String> hearing = kind.getValue().stream().filter(has::contains).toList();
            helper.assertTrue(colony.news().listenersOf(kind.getKey()).equals(hearing) && colony.news().listens(kind.getKey()) == !hearing.isEmpty(),
                    "a " + colony.getSpecies() + " colony's news of " + kind.getKey() + " should be heard by " + hearing + ", not "
                            + colony.news().listenersOf(kind.getKey()));
        }
        // Colony parts steps 6 and 7, the colony questions: each kind is answered by the parts the colony has that used
        // to be asked by name, in the order they were asked; a kind nobody answers is "no" at once.
        Map<com.antcolonies.colony.ColonyQuestions.Kind<?>, List<String>> asked = Map.of(
                com.antcolonies.colony.ColonyQuestions.KEEP_OFF, List.of("neighbours", "closed_holes", "danger_map", "trunk_trails"),
                com.antcolonies.colony.ColonyQuestions.NOT_CLEARED, List.of("patrol"),
                com.antcolonies.colony.ColonyQuestions.HOLE_KEPT_SHUT, List.of("closed_holes"),
                com.antcolonies.colony.ColonyQuestions.ANY_HOLE_KEPT_SHUT, List.of("closed_holes"),
                com.antcolonies.colony.ColonyQuestions.NO_GALLERY_THROUGH, List.of("closed_holes"));
        for (Map.Entry<com.antcolonies.colony.ColonyQuestions.Kind<?>, List<String>> kind : asked.entrySet()) {
            List<String> answering = kind.getValue().stream().filter(has::contains).toList();
            helper.assertTrue(colony.questions().answerersOf(kind.getKey()).equals(answering) && colony.questions().answers(kind.getKey()) == !answering.isEmpty(),
                    "a " + colony.getSpecies() + " colony's question " + kind.getKey() + " should be answered by " + answering + ", not "
                            + colony.questions().answerersOf(kind.getKey()));
        }
    }

    /** The parts of a colony saved into a tag of their own each, by name (the parts that are not saved left out). */
    static Map<String, String> partSaves(Colony colony) {
        Map<String, String> saves = new java.util.LinkedHashMap<>();
        for (com.antcolonies.colony.ColonyPart<?> part : colony.parts()) {
            if (part.isSaved()) {
                CompoundTag own = new CompoundTag();
                part.save(own);
                saves.put(part.name(), canon(own));
            }
        }
        return saves;
    }

    /**
     * Project Apex, colony parts step 1: every part is saved and loaded, with something in it. A copy of the colony is
     * loaded from its save (the live colony is left alone) and each part's test sample is put in, for the parts the
     * scene leaves empty ({@code ColonyPart.sample}, in {@code ColonyParts}); then no saved part may be empty (as a
     * new colony's), and each part of the copy, saved and loaded again, must save exactly the same. A part saving
     * nothing, or reading back something else, is named.
     */
    static void everyPartSavesAndLoads(GameTestHelper helper, CompoundTag saved) {
        Colony scratch = Colony.load(saved);
        for (com.antcolonies.colony.ColonyPart<?> part : scratch.parts()) {
            part.fillForTests(helper.getLevel());
        }
        Map<String, String> blank = partSaves(new Colony(scratch.getId(), scratch.getSpecies(), scratch.getQueenId(), scratch.getCenter()));
        Map<String, String> mine = partSaves(scratch);
        List<String> empty = new ArrayList<>();
        mine.forEach((name, save) -> {
            if (save.equals(blank.get(name))) {
                empty.add(name);
            }
        });
        helper.assertTrue(empty.isEmpty(), "every saved colony part should have something in it for the save and load check (give it a "
                + "sample in ColonyParts): empty " + empty);
        Map<String, String> again = partSaves(Colony.load(scratch.save()));
        List<String> differ = new ArrayList<>();
        mine.forEach((name, save) -> {
            if (!save.equals(again.get(name))) {
                differ.add(name + ": " + save + " -> " + again.get(name));
            }
        });
        helper.assertTrue(differ.isEmpty(), "every colony part loaded from its save should save the same: " + differ);
        AntColoniesMod.LOGGER.info("[parts-test] {}: all {} saved parts saved and loaded the same", scratch.getSpecies(), mine.size());
    }

    /** The colony with this id (also while its queen is frozen, or dead). */
    static Colony byId(GameTestHelper helper, UUID id) {
        Colony colony = ColonyManager.get(helper.getLevel()).getColony(id);
        helper.assertTrue(colony != null, "the colony is gone");
        return colony;
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
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now, 100); // young: nurses
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - worker.nurseUntilTicks() - 1200, 100); // builders
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - worker.builderUntilTicks() - 1200, 100); // foragers
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
            helper.assertTrue(copy.getCount(WorkerAntEntity.CASTE) == colony.getCount(WorkerAntEntity.CASTE)
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
            helper.assertTrue(grew >= 3 && colony.getCount(WorkerAntEntity.CASTE) == 300 + grew && colony.population().inside(WorkerAntEntity.CASTE) == 300 + grew,
                    "the egg and the 2 larvae should have grown up into workers (300 and those grown up): "
                            + colony.getCount(WorkerAntEntity.CASTE) + ", " + report.describe());
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
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 30);
            helper.assertTrue(colony.food().getFood() == 0 && colony.part(ColonyParts.BAKERY).orElseThrow().getBread() == 0, "the colony should have no food");
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
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 30 && report.get(AwayReport.What.STARVED) == 0,
                    "the workers, just fed, should live: " + colony.getCount(WorkerAntEntity.CASTE));
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
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 30, "the workers should carry on: " + colony.getCount(WorkerAntEntity.CASTE));
            helper.assertTrue(colony.refuse().getRefuseCorpses(level).size() >= 2, "the young's bodies should lie in the refuse chamber: "
                    + colony.refuse().getRefuseCorpses(level).size() + ", " + colony.ledger().describe());
            helper.assertTrue(colony.away().report().describe().contains("the queen starved"), "the report should still say so after coming back");
            helper.succeed();
        });
    }

    /**
     * Brain part 4, the king fix: a starving termite royal pair while away (real Macrotermes kings and queens are fed
     * mouth to mouth and die without it). A Macrotermes colony with its nest dug, 20 worker numbers (just fed), a comb
     * of fresh forage but not one nodule, the queen in the royal chamber and the king beside her, both long unfed. While
     * away the nurses have nothing to give them: the queen starves, and the king too by her rule (his hunger clock is
     * the brain's, so it runs on while he is frozen); the workers live. The report says so. Back in range both die as
     * their land loads and the colony carries on queenless.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aStarvingTermitePairAwayDieWhenTheirLandLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(2, () -> helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 1, SURFACE, MIDDLE));
        UUID[] id = new UUID[1];
        UUID[] royals = new UUID[2];
        Map<UUID, CompoundTag> unloaded = new HashMap<>();
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            id[0] = colony.getId();
            TestClock.still(helper, 12.0); // midday: the foragers stay in
            colony.setHungerOn(true);
            colony.population().setOnForTests(false);
            colony.population().setViewersForTests(List.of());
            TermiteSoldierTests.digNestAtOnce(level, colony);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the royal chamber should be dug");
            colony.getQueen(level).teleportTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 20);
            BlockPos spot = colony.food().pickFoodSpot(level, level.getRandom());
            helper.assertTrue(spot != null, "no spot for comb in the garden");
            colony.food().depositSeed(level, spot); // (fresh forage: the colony has had its first comb, so its royals get hungry)
        });
        helper.runAfterDelay(60, () -> {
            Colony colony = byId(helper, id[0]);
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in the royal chamber");
            helper.assertTrue(colony.life().nurseFoodLeft(level) == 0, "there should be no nodule: " + colony.life().nurseFoodLeft(level));
            AbstractAntEntity theQueen = colony.getQueen(level);
            var theKing = level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, helper.getBounds(),
                    mob -> colony.getId().equals(mob.getColonyId())).stream().findFirst().orElse(null);
            helper.assertTrue(theKing != null, "the king should have joined");
            theKing.teleportTo(theQueen.getX() + 1.0, theQueen.getY(), theQueen.getZ());
            royals[0] = theQueen.getUUID();
            royals[1] = theKing.getUUID();
            long now = level.getGameTime();
            colony.queenCare().setQueenLastFed(now - colony.profile().queen().starveTicks() + 200);
            colony.part(ColonyParts.KING_CARE).orElseThrow().setLastFed(now - colony.profile().worker().mealIntervalTicks() - colony.profile().queen().starveTicks() + 300);
            helper.assertTrue(!colony.part(ColonyParts.KING_CARE).orElseThrow().starves(level), "the king should not have starved yet");
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            unloaded.put(royals[0], freezeAndUnload(colony, level, theQueen));
            unloaded.put(royals[1], freezeAndUnload(colony, level, theKing));
            colony.away().setAwayForTests(true);
        });
        helper.runAfterDelay(500, () -> {
            Colony colony = byId(helper, id[0]);
            AwayReport report = colony.away().report();
            AntColoniesMod.LOGGER.info("[away-test] king fix, starving pair: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            helper.assertTrue(report.describe().contains("the queen starved") && report.didKingStarve() && report.describe().contains("the king starved"),
                    "the queen and the king should starve while away: " + report.describe());
            helper.assertTrue(report.get(AwayReport.What.QUEEN_FED) == 0 && report.get(AwayReport.What.KING_FED) == 0,
                    "there was nothing to feed them: " + report.describe());
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 20 && report.get(AwayReport.What.STARVED) == 0,
                    "the workers, just fed, should live: " + colony.getCount(WorkerAntEntity.CASTE));
            colony.away().setAwayForTests(false);
            unloaded.values().forEach(tag -> loadAgain(level, tag));
        });
        helper.runAfterDelay(650, () -> {
            Colony colony = byId(helper, id[0]);
            Entity theQueen = level.getEntity(royals[0]);
            Entity theKing = level.getEntity(royals[1]);
            AntColoniesMod.LOGGER.info("[away-test] king fix, starving pair back: queen {}, king {}, {}", theQueen, theKing, colony.away().describe(level));
            helper.assertTrue(theQueen == null || !theQueen.isAlive(), "the queen who starved while away should die as her land loads");
            helper.assertTrue(theKing == null || !theKing.isAlive(), "the king who starved while away should die as his land loads");
            helper.assertTrue(colony.isQueenless() && colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) == 0,
                    "the colony should carry on without them: kings " + colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE));
            helper.succeed();
        });
    }

    /**
     * Brain part 3, founding while away (species-neutral): a lone harvester ant queen, or a termite royal pair, digs the
     * founding chamber and closes the entrance (real insects, as in the founding tests). Then the colony goes away: the
     * queen lays the rest of her founding batch as numbers, the founders feed the young from their own fat, and they
     * grow up into the first workers, all while away; the queen does not starve (she lives on her fat). Back in range the
     * first workers come out as real ones and dig the entrance open, as in a real claustral founding.
     * <p>Grass in the entrance (the user's bug, 2026-10-15): the founders start on a tuft of short grass. The ant queen is
     * then put two blocks off it, with the grass grown back (before the fix she never dug: nobody could stand over the
     * shaft's first block), and she has to bite it away and dig as before.
     * <p>Founding while away (the user's bug, 2026-10-15: a founding nest dug nothing while away): the termite pair go
     * away at once, before they have dug anything. While away they dig their chamber and close themselves in at the real
     * pace (the report says so), and the queen lays her batch; back home they are in their chamber, the grass is gone.
     */
    private static void foundsWhileAway(GameTestHelper helper, boolean termites) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.setBlock(MIDDLE, SURFACE, MIDDLE, net.minecraft.world.level.block.Blocks.SHORT_GRASS);
        if (termites) {
            helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        } else {
            helper.spawn(ModEntities.QUEEN_ANT.get(), MIDDLE, SURFACE, MIDDLE);
        }
        if (termites) {
            helper.runAfterDelay(5, () -> helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 2, SURFACE, MIDDLE + 1));
        }
        UUID[] id = new UUID[1];
        long[] at = {0, 0}; // when sealed in; when away (or back)
        int[] phase = {0}; // 0 founding (here), 1 away, 3 still away, living on (ants), 2 back
        int[] eggsBefore = new int[1];
        Map<UUID, CompoundTag> unloaded = new HashMap<>();
        helper.onEachTick(() -> {
            if (id[0] == null) {
                Colony colony = colony(helper, termites ? Species.MACROTERMES : Species.RED_HARVESTER_ANT);
                if (colony == null) {
                    return;
                }
                id[0] = colony.getId();
                colony.away().setOnForTests(true); // (here: the nest's chunks are loaded)
                colony.population().setOnForTests(true);
                colony.population().setViewersForTests(List.of());
                if (!termites) {
                    // She steps off her spot and the grass is still there (or grows back).
                    AbstractAntEntity queen = colony.getQueen(level);
                    BlockPos aside = helper.absolutePos(new BlockPos(MIDDLE + 2, SURFACE, MIDDLE));
                    queen.moveTo(aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0F, 0.0F);
                    level.setBlock(colony.getCenter(), net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(), 3);
                    helper.assertTrue(colony.nest().isPlantInEntrance(level), "the grass should be in the entrance");
                    // Grass round the nest the brain sees while here, for the first workers to forage while away.
                    for (int i = 0; i < 12; i++) {
                        BlockPos tuft = helper.absolutePos(new BlockPos(3 + 3 * (i % 4), SURFACE, 3 + 8 * (i / 4) - (i / 4 == 1 ? 1 : 0)));
                        if (tuft.distManhattan(colony.getCenter()) > 3) {
                            level.setBlock(tuft, net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(), 3);
                        }
                    }
                    colony.food().rescanPlantsSoon();
                    colony.field().setOnForTests(true);
                }
            }
            Colony colony = byId(helper, id[0]);
            int eggs = colony.profile().founding().orElseThrow().eggs();
            if (phase[0] == 0 && termites) {
                // The pair go away at once: the king has joined, the brain has seen what they have to dig.
                if (colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) == 0 || colony.life().foundingWorkLeft() <= 0) {
                    return;
                }
                helper.assertTrue(colony.isFounding() && colony.nest().getHomeChamber(level) == null, "the pair should not have dug their chamber yet");
                AntColoniesMod.LOGGER.info("[away-test] founding: the pair go away at tick {} with {} ticks of work left", helper.getTick(),
                        colony.life().foundingWorkLeft());
                List<Entity> all = new ArrayList<>();
                all.add(colony.getQueen(level));
                all.addAll(level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, helper.getBounds(),
                        king -> colony.getId().equals(king.getColonyId())));
                for (Entity entity : all) {
                    unloaded.put(entity.getUUID(), freezeAndUnload(colony, level, entity));
                }
                colony.away().setGraceForTests(20L);
                colony.away().setAwayForTests(true);
                at[1] = helper.getTick();
                phase[0] = 1;
                return;
            }
            if (phase[0] == 0) {
                boolean sealed = !colony.nest().isEntranceOpen(level) && colony.queenCare().isQueenHome(level);
                if (!sealed) {
                    at[0] = 0;
                    return;
                }
                if (at[0] == 0) {
                    at[0] = helper.getTick();
                    AntColoniesMod.LOGGER.info("[away-test] founding: sealed in at tick {}", helper.getTick());
                }
                if (helper.getTick() - at[0] < 60) {
                    return; // (the brain notes once a second that she is sealed in her room)
                }
                helper.assertTrue(colony.queenCare().getEggsLaid() < eggs, "the whole batch was laid before the colony went away");
                // Out of range: the founders and any eggs or young already laid are frozen.
                eggsBefore[0] = colony.queenCare().getEggsLaid();
                List<Entity> all = new ArrayList<>();
                all.add(colony.getQueen(level));
                all.addAll(level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, helper.getBounds(),
                        king -> colony.getId().equals(king.getColonyId())));
                all.addAll(colony.brood().getEggs(level));
                all.addAll(colony.brood().getLarvae(level));
                for (Entity entity : all) {
                    unloaded.put(entity.getUUID(), freezeAndUnload(colony, level, entity));
                }
                colony.away().setGraceForTests(20L);
                colony.away().setAwayForTests(true);
                at[1] = helper.getTick();
                phase[0] = 1;
                return;
            }
            if (phase[0] == 1) {
                AwayReport report = colony.away().report();
                if (helper.getTick() % 400 == 0) {
                    AntColoniesMod.LOGGER.info("[away-test] founding away: eggs laid {}, {}{}", colony.queenCare().getEggsLaid(),
                            colony.population().describe(level), colony.away().describe(level));
                }
                helper.assertTrue(helper.getTick() - at[1] < 15000, "the founding batch should grow up while away: eggs laid "
                        + colony.queenCare().getEggsLaid() + ", " + colony.population().describe(level) + colony.away().describe(level));
                if (colony.getCount(WorkerAntEntity.CASTE) < eggs || colony.brood().insideCount() > 0) {
                    return;
                }
                helper.assertTrue(colony.queenCare().getEggsLaid() == eggs && report.get(AwayReport.What.EGGS_LAID) == eggs - eggsBefore[0],
                        "the queen should lay the rest of her batch while away: " + colony.queenCare().getEggsLaid() + " laid, " + report.describe());
                helper.assertTrue(report.get(AwayReport.What.GREW_UP) == eggs && report.get(AwayReport.What.YOUNG_FED) > 0,
                        "the founders should feed the young from their fat until they grow up: " + report.describe());
                helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == eggs && colony.population().inside(WorkerAntEntity.CASTE) == eggs,
                        "the first " + eggs + " workers should be numbers: " + colony.population().describe(level));
                helper.assertTrue(!report.describe().contains("the queen starved") && !colony.isFounding(), "the queen should live on her fat: "
                        + report.describe());
                helper.assertTrue(!termites || report.didFoundersSeal(), "the pair should have dug their chamber and closed themselves in while away: "
                        + report.describe());
                if (!termites) {
                    // The user's bug (2026-10-15): the first workers starved while away, as the brain saw no food store to
                    // forage into. They stay away past their starve time: they dig the store, forage and eat.
                    helper.assertTrue(colony.life().storeWorkLeft() > 0, "the brain should know the food store is not dug yet");
                    at[1] = helper.getTick();
                    phase[0] = 3;
                    return;
                }
                colony.away().setAwayForTests(false);
                unloaded.values().forEach(tag -> loadAgain(level, tag));
                at[1] = helper.getTick();
                phase[0] = 2;
                return;
            }
            if (phase[0] == 3) {
                AwayReport report = colony.away().report();
                long starve = colony.profile().worker().starveAfterTicks();
                if (helper.getTick() % 2000 == 0) {
                    AntColoniesMod.LOGGER.info("[away-test] founding, living on: store work left {}, food {}{}{}", colony.life().storeWorkLeft(),
                            colony.food().getFood(), colony.away().describe(level), colony.field().describe());
                }
                helper.assertTrue(report.get(AwayReport.What.STARVED) == 0 && colony.getCount(WorkerAntEntity.CASTE) >= eggs,
                        "the first workers should not starve while away: " + report.describe() + ", store work left "
                                + colony.life().storeWorkLeft() + colony.field().describe());
                if (helper.getTick() - at[1] < starve + 12000) {
                    return;
                }
                helper.assertTrue(colony.life().storeWorkLeft() == 0, "the workers should have dug the food store while away: "
                        + colony.life().storeWorkLeft() + " ticks of work left");
                helper.assertTrue(report.get(AwayReport.What.FORAGED) > 0, "the first workers should forage while away: " + report.describe());
                colony.away().setAwayForTests(false);
                unloaded.values().forEach(tag -> loadAgain(level, tag));
                at[1] = helper.getTick();
                phase[0] = 2;
                return;
            }
            // Back: the first workers come out as real ones and open the entrance.
            int real = level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds(),
                    mob -> mob.isAlive() && colony.getId().equals(mob.getColonyId()) && WorkerAntEntity.CASTE.equals(mob.nestCaste())).size();
            if (colony.getQueen(level) != null && real == eggs && colony.nest().isEntranceOpen(level)) {
                helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == eggs, "still " + eggs + " workers: " + colony.getCount(WorkerAntEntity.CASTE));
                helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in her chamber: " + colony.getQueen(level).blockPosition());
                helper.assertTrue(!colony.nest().isPlantInEntrance(level), "the grass in the entrance should be gone");
                if (termites) {
                    AbstractAntEntity queen = colony.getQueen(level);
                    helper.assertTrue(!level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, queen.getBoundingBox().inflate(2.0),
                            king -> king.isAlive() && colony.getId().equals(king.getColonyId())).isEmpty(), "the king should be with the queen");
                }
                helper.succeed();
            }
            helper.assertTrue(helper.getTick() - at[1] < 15000, "back: " + real + " real workers, entrance open " + colony.nest().isEntranceOpen(level)
                    + ", queen " + colony.getQueen(level) + colony.population().describe(level));
        });
    }

    /**
     * Brain part 3: a lone harvester ant queen raises her first workers while away, and they dig the food store, forage
     * and live on past their starve time (see {@link #foundsWhileAway}).
     */
    @GameTest(template = "arena", timeoutTicks = 160000, skyAccess = true)
    public static void aFoundingAntQueenRaisesHerFirstWorkersWhileAway(GameTestHelper helper) {
        foundsWhileAway(helper, false);
    }

    /** Brain part 3: a termite royal pair raise their first workers while away (see {@link #foundsWhileAway}). */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void aFoundingTermitePairRaiseTheirFirstWorkersWhileAway(GameTestHelper helper) {
        foundsWhileAway(helper, true);
    }

    /** The pieces a test tuft of short grass has cut already: all but 2 (none since short grass is one take, 2026-10-17). */
    private static int preCut(Colony colony) {
        return Math.max(0, colony.food().piecesOf(Blocks.SHORT_GRASS.defaultBlockState()) - 2);
    }

    /** The pieces left on such a tuft. */
    private static int leftOn(Colony colony) {
        return colony.food().piecesOf(Blocks.SHORT_GRASS.defaultBlockState()) - preCut(colony);
    }
}
