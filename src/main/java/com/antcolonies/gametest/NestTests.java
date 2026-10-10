package com.antcolonies.gametest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automatic in-game tests (run with ./gradlew runGameTestServer, no window needed). They build a small
 * patch of ground, spawn real ants, and check what the ants actually do.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class NestTests {
    /** Height (inside the test area) of the first open cell above the ground. */
    private static final int SURFACE = 8;
    private static final int MIDDLE = 12;
    /** The whole nest: a 3-deep shaft, then three rooms (queen, nursery, food store), each a 3-long tunnel and a 5x5 room. */
    private static final int NEST_CELLS = 3 + 3 * (3 + 25);

    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void workersDigTheWholeNestAndBuildTheMound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Run the game faster than normal so the test doesn't take minutes.
        level.getServer().tickRateManager().setTickRate(2000.0F);

        startColony(helper);

        int[] lastAirCount = {0};
        helper.onEachTick(() -> {
            int air = airBelowSurface(helper);
            if (air != lastAirCount[0]) {
                lastAirCount[0] = air;
                AntColoniesMod.LOGGER.info("[nest-test] tick {}: {} of {} nest cells dug", helper.getTick(), air, NEST_CELLS);
            }
            if (helper.getTick() % 400 == 0) {
                AntColoniesMod.LOGGER.info("[nest-test] tick {}: {}", helper.getTick(), describe(helper));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no colony yet");
            helper.assertTrue(airBelowSurface(helper) == NEST_CELLS,
                    "nest not finished: " + airBelowSurface(helper) + " of " + NEST_CELLS + " cells dug. " + describe(helper));
            helper.assertTrue(colony.findDigTarget(level) == null, "something is still left to dig");
            var rooms = colony.getPlannedChambers(level);
            helper.assertTrue(rooms.size() == 3, "expected 3 rooms (queen, nursery, food store) but the nest has " + rooms.size());
            BlockPos room = colony.getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the room is not finished");
            var queens = level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds());
            helper.assertTrue(!queens.isEmpty(), "the queen is gone");
            helper.assertTrue(queens.get(0).blockPosition().equals(room), "the queen is not in the middle of the room yet: " + queens.get(0).blockPosition().subtract(room));
            String hill = hillProblems(helper);
            helper.assertTrue(hill.isEmpty(), "the mound is not the planned hill yet: " + hill);
            AntColoniesMod.LOGGER.info("[nest-test] finished at tick {}: {}", helper.getTick(), describe(helper));
        });
    }

    /**
     * The nest has holes in its walls: cave pockets next to the shaft and the rooms, a crack from the queen's room
     * up to the open air, a pit under a corner of the nursery, and a gap that links the queen's room with the
     * nursery. The ants must close every one of them with soil, so that the only way in is the entrance hole and
     * the rooms are joined only by their own tunnels.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void workersCloseEveryHoleInTheNestWalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        // The holes are cut as soon as the layout is known (before any digging: the ants start with the shaft).
        List<BlockPos> holes = new ArrayList<>();
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (!holes.isEmpty() || colony == null) {
                return;
            }
            var rooms = colony.getPlannedChambers(level);
            if (rooms.isEmpty()) {
                return;
            }
            BlockPos bottom = colony.getCenter().below(Colony.SHAFT_DEPTH);
            Direction tunnel = Direction.fromDelta(
                    rooms.get(0).cells().get(0).getX() - bottom.getX(), 0, rooms.get(0).cells().get(0).getZ() - bottom.getZ());
            BlockPos queenRoom = rooms.get(0).center();
            int groundTop = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
            for (int y = queenRoom.getY() + 1; y <= groundTop; y++) {
                holes.add(new BlockPos(queenRoom.getX(), y, queenRoom.getZ()));
            }
            holes.add(bottom.relative(tunnel, 4).relative(tunnel.getClockWise(), 3));
            holes.add(rooms.get(1).cells().get(rooms.get(1).cells().size() - 1).below());
            holes.add(bottom.above().relative(tunnel.getOpposite()));
            for (BlockPos hole : holes) {
                level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
            }
            AntColoniesMod.LOGGER.info("[seal-test] cut {} holes: {}", holes.size(), holes);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() % 400 == 0) {
                Colony colony = colony(helper);
                AntColoniesMod.LOGGER.info("[seal-test] tick {}: {} open leaks, {} open cells under the surface. {}",
                        helper.getTick(), colony == null ? -1 : colony.getLeaks(level).size(), airBelowSurface(helper), describe(helper));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && !holes.isEmpty(), "no colony or holes yet");
            helper.assertTrue(colony.findDigTarget(level) == null, "something is still left to dig");
            var leaks = colony.getLeaks(level);
            helper.assertTrue(leaks.isEmpty(), "the nest still has openings: " + leaks);
            String reach = openSpaceProblems(helper, colony);
            helper.assertTrue(reach.isEmpty(), "open space joined to the nest that is not part of it: " + reach);
            AntColoniesMod.LOGGER.info("[seal-test] finished at tick {}", helper.getTick());
        });
    }

    /**
     * Thin soil: only the top three blocks of the ground are dirt, everything under them is stone (like a plains
     * world where the stone starts right below the grass). The rooms do not fit at the usual depth, so the whole
     * nest must sit one block higher, and the ants must still dig all of it and never touch the stone.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void nestSitsOneBlockHigherWhenTheStoneIsTooClose(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);
        int stoneTop = SURFACE - Colony.SHAFT_DEPTH; // the layer the rooms would normally be on
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y <= stoneTop; y++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }

        helper.onEachTick(() -> {
            if (helper.getTick() % 400 == 0) {
                AntColoniesMod.LOGGER.info("[thin-soil-test] tick {}: {} cells dug. {}", helper.getTick(), airBelowSurface(helper), describe(helper));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no colony yet");
            int expected = NEST_CELLS - 1; // the shaft is one block shorter
            helper.assertTrue(airBelowSurface(helper) == expected,
                    "nest not finished: " + airBelowSurface(helper) + " of " + expected + " cells dug. " + describe(helper));
            helper.assertTrue(colony.findDigTarget(level) == null, "something is still left to dig");
            helper.assertTrue(colony.getPlannedChambers(level).size() == 3, "expected 3 rooms");
            BlockPos room = colony.getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the room is not finished");
            var queens = level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds());
            helper.assertTrue(!queens.isEmpty() && queens.get(0).blockPosition().equals(room), "the queen is not in the middle of the room yet");
            for (int x = 0; x < 25; x++) {
                for (int z = 0; z < 25; z++) {
                    helper.assertTrue(!helper.getBlockState(new BlockPos(x, stoneTop, z)).isAir(), "an ant dug into the stone layer at " + x + "," + z);
                }
            }
            var leaks = colony.getLeaks(level);
            helper.assertTrue(leaks.isEmpty(), "the nest still has openings: " + leaks);
            AntColoniesMod.LOGGER.info("[thin-soil-test] finished at tick {}", helper.getTick());
        });
    }

    /**
     * A queen on her own (no workers), as when she founds a nest: she digs the shaft, the tunnel and her own room
     * herself, then settles in the middle of it. She cannot place blocks, so no hill is built.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void aloneQueenDigsHerOwnRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper, 0);

        helper.onEachTick(() -> {
            if (helper.getTick() % 400 == 0) {
                AntColoniesMod.LOGGER.info("[queen-dig-test] tick {}: {} cells dug. {}", helper.getTick(), airBelowSurface(helper), describe(helper));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no colony yet");
            BlockPos room = colony.getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "her room is not finished: " + airBelowSurface(helper) + " cells dug");
            var queens = level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds());
            helper.assertTrue(!queens.isEmpty() && queens.get(0).blockPosition().equals(room), "the queen is not in the middle of her room yet");
            // Shaft + the queen's tunnel and room only: the workers do the nursery and the food store.
            int expected = Colony.SHAFT_DEPTH + colony.getPlannedChambers(level).get(0).cells().size();
            helper.assertTrue(airBelowSurface(helper) == expected, "dug " + airBelowSurface(helper) + " cells but expected " + expected);
            helper.assertTrue(moundBlocks(helper) == 0, "the queen placed soil");
            AntColoniesMod.LOGGER.info("[queen-dig-test] finished at tick {}", helper.getTick());
        });
    }

    /**
     * Seeds lying on the ground around a finished nest (two single ones and a pile of three) are fetched one by one:
     * every one ends up on the floor of the food store, the colony counts them, and nothing is left outside.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void workersCarrySeedsToTheFoodStore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] dropped = {false};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (dropped[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            dropped[0] = true;
            dropSeeds(helper, MIDDLE + 6, MIDDLE, 1);
            dropSeeds(helper, MIDDLE - 6, MIDDLE + 2, 1);
            dropSeeds(helper, MIDDLE + 1, MIDDLE - 7, 3);
            AntColoniesMod.LOGGER.info("[seed-test] tick {}: 5 seeds dropped around the hill", helper.getTick());
        });
        helper.onEachTick(() -> {
            if (dropped[0] && helper.getTick() % 200 == 0) {
                Colony colony = colony(helper);
                AntColoniesMod.LOGGER.info("[seed-test] tick {}: food {}, seeds in the store {}, seeds anywhere {}",
                        helper.getTick(), colony.getFood(), seedsIn(level, storeBox(level, colony)), seedsIn(level, helper.getBounds()));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(dropped[0] && colony != null, "no seeds have been dropped yet");
            // The workers also feed the queen from the store: count what she has eaten too.
            int total = seedsAccountedFor(colony);
            helper.assertTrue(total == 5, "the colony has " + colony.getFood() + " food and the queen ate " + colony.getQueenSeeds()
                    + " (+ " + colony.getEggsLaid() + " eggs laid), expected 5 seeds in all");
            helper.assertTrue(seedsIn(level, storeBox(level, colony)) == colony.getFood(), "only " + seedsIn(level, storeBox(level, colony)) + " seeds are in the food store");
            helper.assertTrue(seedsIn(level, helper.getBounds()) == colony.getFood(), "some seeds are still lying around outside the store");
            for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds())) {
                helper.assertTrue(!ant.isCarryingSeed(), "an ant is still carrying a seed");
            }
        });
    }

    /**
     * Three tufts of short grass around a finished nest: the workers clip ten seeds off each (then the tuft breaks),
     * carry every seed to the food store, and what the colony counts is exactly what lies in the store.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void workersClipSeedsFromGrass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);
        int[][] tufts = {{MIDDLE + 6, MIDDLE}, {MIDDLE - 6, MIDDLE + 2}, {MIDDLE + 1, MIDDLE - 7}};

        boolean[] planted = {false};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (planted[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            planted[0] = true;
            for (int[] tuft : tufts) {
                helper.setBlock(tuft[0], SURFACE, tuft[1], Blocks.SHORT_GRASS);
            }
            AntColoniesMod.LOGGER.info("[grass-test] tick {}: 3 tufts of grass planted", helper.getTick());
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(planted[0] && colony != null, "no grass has been planted yet");
            int expected = tufts.length * Colony.SEEDS_PER_GRASS;
            for (int[] tuft : tufts) {
                helper.assertBlock(new BlockPos(tuft[0], SURFACE, tuft[1]), block -> block == Blocks.AIR, "a tuft of grass is still there");
            }
            // The workers also feed the queen from the store: count what she has eaten too.
            int total = seedsAccountedFor(colony);
            helper.assertTrue(total == expected, "the colony has " + colony.getFood() + " food and the queen ate " + colony.getQueenSeeds()
                    + " (+ " + colony.getEggsLaid() + " eggs laid), expected " + expected + " seeds in all");
            helper.assertTrue(seedsIn(level, storeBox(level, colony)) == colony.getFood(),
                    "only " + seedsIn(level, storeBox(level, colony)) + " seeds are in the store");
            for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds())) {
                helper.assertTrue(!ant.isCarryingSeed(), "an ant is still carrying a seed");
            }
        });
    }

    /**
     * In a finished nest with 25 seeds in the food store the workers feed the queen 20 of them. She then carries an egg
     * for 3 minutes (3600 ticks), the egg comes out behind her in her room, and a worker carries it into the nursery.
     * After that she is fed the 5 seeds that are left.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void queenIsFedAndHerEggGoesToTheNursery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] stocked = {false};
        long[] incubationStart = {-1};
        long[] laidAt = {-1};
        boolean[] cameOutBehindHer = {false};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (stocked[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            stocked[0] = true;
            var store = colony.getFoodStoreCells(level);
            for (int i = 0; i < 25; i++) {
                colony.depositSeed(level, store.get(i / 8));
            }
            AntColoniesMod.LOGGER.info("[egg-test] tick {}: 25 seeds put in the food store", helper.getTick());
        });
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (!stocked[0] || colony == null) {
                return;
            }
            if (incubationStart[0] < 0 && colony.isIncubating()) {
                incubationStart[0] = level.getGameTime();
                AntColoniesMod.LOGGER.info("[egg-test] tick {}: the queen is full and carries an egg, food {}", helper.getTick(), colony.getFood());
            }
            if (laidAt[0] < 0 && colony.getEggsLaid() > 0) {
                laidAt[0] = level.getGameTime();
                // The egg must lie in her room, behind her (she looks the other way), and not in the nursery yet.
                var queens = level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds());
                var eggs = colony.getLooseEggs(level);
                if (queens.size() == 1 && eggs.size() == 1) {
                    var queen = queens.get(0);
                    Vec3 away = eggs.get(0).position().subtract(queen.position());
                    Vec3 facing = Vec3.directionFromRotation(0.0F, queen.getYRot());
                    BlockPos room = colony.getFinishedRoomCenter(level);
                    cameOutBehindHer[0] = away.dot(facing) <= 0.001 && room != null
                            && Math.abs(eggs.get(0).getX() - (room.getX() + 0.5)) <= 2.5 && Math.abs(eggs.get(0).getZ() - (room.getZ() + 0.5)) <= 2.5;
                    AntColoniesMod.LOGGER.info("[egg-test] tick {}: egg laid {} from the queen, facing {}", helper.getTick(), away, facing);
                }
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[egg-test] tick {}: food {}, queen fed {}, eggs laid {}, eggs {} (nursery {})", helper.getTick(),
                        colony.getFood(), colony.getQueenSeeds(), colony.getEggsLaid(), colony.countEggs(level), colony.getNurseryEggs(level).size());
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(stocked[0] && colony != null, "the store has not been stocked yet");
            helper.assertTrue(colony.getEggsLaid() == 1, "the queen has laid " + colony.getEggsLaid() + " eggs, expected 1");
            long carried = laidAt[0] - incubationStart[0];
            helper.assertTrue(carried >= Colony.INCUBATION_TICKS && carried <= Colony.INCUBATION_TICKS + 100,
                    "the egg came out after " + carried + " ticks, expected " + Colony.INCUBATION_TICKS);
            helper.assertTrue(cameOutBehindHer[0], "the egg did not come out behind the queen in her room");
            var eggs = colony.getEggs(level);
            helper.assertTrue(eggs.size() == 1 && colony.getNurseryEggs(level).size() == 1,
                    "expected 1 egg, in the nursery, but there are " + eggs.size() + " (" + colony.getNurseryEggs(level).size() + " in the nursery)");
            helper.assertTrue(level.getEntitiesOfClass(com.antcolonies.entity.AntEggEntity.class, helper.getBounds()).size() == 1,
                    "there is an egg somewhere else");
            helper.assertTrue(colony.getQueenSeeds() == 5, "the queen was fed " + colony.getQueenSeeds() + " seeds for the next egg, expected the 5 that were left");
            helper.assertTrue(colony.getFood() == 0 && seedsIn(level, storeBox(level, colony)) == 0, "seeds are left in the store");
            for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds())) {
                helper.assertTrue(!ant.hasFullMandibles(), "an ant is still carrying something");
            }
        });
    }

    /**
     * Three eggs put in the nursery of a finished nest hatch after 2 minutes: each becomes a real worker of the same
     * colony, and the colony counts them.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void eggsInTheNurseryHatchIntoWorkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] placed = {false};
        int[] workersBefore = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (placed[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            placed[0] = true;
            workersBefore[0] = colony.getCount(WorkerAntEntity.CASTE);
            var cells = colony.getNurseryCells(level);
            for (int i = 0; i < 3; i++) {
                colony.placeEgg(level, cells.get(i * 7));
            }
            AntColoniesMod.LOGGER.info("[hatch-test] tick {}: 3 eggs put in the nursery, {} workers", helper.getTick(), workersBefore[0]);
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(placed[0] && colony != null, "no eggs have been put in the nursery yet");
            helper.assertTrue(colony.countEggs(level) == 0, colony.countEggs(level) + " eggs are still there");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == workersBefore[0] + 3,
                    "the colony counts " + colony.getCount(WorkerAntEntity.CASTE) + " workers, expected " + (workersBefore[0] + 3));
            var workers = level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds());
            helper.assertTrue(workers.size() == workersBefore[0] + 3, "there are " + workers.size() + " worker ants, expected " + (workersBefore[0] + 3));
            for (var worker : workers) {
                helper.assertTrue(colony.getId().equals(worker.getColonyId()), "a worker belongs to no colony or the wrong one");
            }
        });
    }

    /**
     * When the colony already has one worker less than the most it can have, three eggs in the nursery make exactly one
     * worker; the other two stay eggs.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void eggsWaitWhenTheColonyHasEnoughWorkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] placed = {false};
        long[] placedAt = {0};
        int[] workersBefore = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (placed[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            placed[0] = true;
            placedAt[0] = level.getGameTime();
            workersBefore[0] = colony.getCount(WorkerAntEntity.CASTE);
            colony.setCount(WorkerAntEntity.CASTE, Colony.MAX_WORKERS - 1);
            var cells = colony.getNurseryCells(level);
            for (int i = 0; i < 3; i++) {
                colony.placeEgg(level, cells.get(i * 7));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(placed[0] && colony != null, "no eggs have been put in the nursery yet");
            // Wait well past the hatching time, so the two eggs had every chance to hatch too.
            helper.assertTrue(level.getGameTime() >= placedAt[0] + Colony.HATCH_TICKS + 400, "still waiting for the eggs to have hatched");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == Colony.MAX_WORKERS,
                    "the colony counts " + colony.getCount(WorkerAntEntity.CASTE) + " workers, expected " + Colony.MAX_WORKERS);
            helper.assertTrue(colony.countEggs(level) == 2, colony.countEggs(level) + " eggs are left, expected 2");
            helper.assertTrue(level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds()).size() == workersBefore[0] + 1,
                    "expected exactly one new worker");
        });
    }

    /** The seeds the colony can account for: in the store, eaten by the queen for her next egg, or used for the eggs she has laid. */
    private static int seedsAccountedFor(Colony colony) {
        return colony.getFood() + colony.getQueenSeeds() + Colony.QUEEN_MEAL * colony.getEggsLaid();
    }

    private static void dropSeeds(GameTestHelper helper, int x, int z, int count) {
        BlockPos at = helper.absolutePos(new BlockPos(x, SURFACE, z));
        var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, count));
        item.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(item);
    }

    /** A box around the floor cells of the colony's food store. */
    private static AABB storeBox(ServerLevel level, Colony colony) {
        AABB box = null;
        for (BlockPos pos : colony.getFoodStoreCells(level)) {
            box = box == null ? new AABB(pos) : box.minmax(new AABB(pos));
        }
        return box == null ? new AABB(0, 0, 0, 0, 0, 0) : box;
    }

    /** How many seeds lie in the box: every layer of a seed pile block counts one, and so does every seed lying as an item. */
    private static int seedsIn(ServerLevel level, AABB box) {
        int seeds = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX + 0.01, box.minY + 0.01, box.minZ + 0.01),
                BlockPos.containing(box.maxX - 0.01, box.maxY - 0.01, box.maxZ - 0.01))) {
            var state = level.getBlockState(pos);
            if (state.is(ModBlocks.SEED_PILE.get())) {
                seeds += state.getValue(com.antcolonies.block.SeedPileBlock.LAYERS);
            }
        }
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
            seeds += item.getItem().getCount();
        }
        return seeds;
    }

    /** Dirt ground with a grass top across the whole test area, a queen at the entrance spot and six workers. */
    private static void startColony(GameTestHelper helper) {
        startColony(helper, 6);
    }

    private static void startColony(GameTestHelper helper, int workers) {
        // Ground: dirt with a grass top, SURFACE blocks deep, across the whole test area.
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }

        helper.spawn(ModEntities.QUEEN_ANT.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < workers; i++) {
                helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE - 2 + i % 3 * 2, SURFACE, MIDDLE - 3 + i / 3 * 6);
            }
        });
    }

    /**
     * Follows the open space from the bottom of the shaft through the underground (stopping at the surface
     * layer, where the entrance hole opens into the sky) and lists every open block it reaches that is not part
     * of the nest plan. Empty text = the nest is sealed and its rooms are joined only by their own tunnels.
     */
    private static String openSpaceProblems(GameTestHelper helper, Colony colony) {
        ServerLevel level = helper.getLevel();
        Set<BlockPos> planned = new HashSet<>();
        for (int i = 1; i <= Colony.SHAFT_DEPTH; i++) {
            planned.add(colony.getCenter().below(i));
        }
        for (var chamber : colony.getPlannedChambers(level)) {
            planned.addAll(chamber.cells());
        }
        int ceiling = colony.getCenter().getY() - 1; // the shaft's top block: nothing above it counts
        AABB area = helper.getBounds();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = colony.getCenter().below(Colony.SHAFT_DEPTH);
        seen.add(start);
        queue.add(start);
        StringBuilder problems = new StringBuilder();
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!planned.contains(pos)) {
                problems.append(pos.toShortString()).append("; ");
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (next.getY() <= ceiling && area.contains(Vec3.atCenterOf(next))
                        && level.getBlockState(next).isAir() && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (seen.size() != planned.size()) {
            problems.append("(reached ").append(seen.size()).append(" blocks, the plan has ").append(planned.size()).append(")");
        }
        return problems.toString();
    }

    /**
     * Compares the mound with the planned hill, column by column around the entrance (the queen is spawned at
     * MIDDLE, SURFACE, MIDDLE, so that is the entrance). Empty text = exactly the planned hill.
     */
    private static String hillProblems(GameTestHelper helper) {
        StringBuilder problems = new StringBuilder();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int built = 0;
                for (int y = SURFACE; y < SURFACE + 6; y++) {
                    if (helper.getBlockState(new BlockPos(MIDDLE + dx, y, MIDDLE + dz)).is(ModBlocks.BURROWED_DIRT.get())) {
                        built++;
                    }
                }
                int planned = Colony.moundHeightAt(dx, dz);
                if (built != planned) {
                    problems.append(String.format("(%d,%d) has %d of %d; ", dx, dz, built, planned));
                }
            }
        }
        return problems.toString();
    }

    /** The colony inside this test's area (a test world can still hold colonies from earlier runs). */
    private static Colony colony(GameTestHelper helper) {
        AABB area = helper.getBounds();
        return ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(colony -> area.contains(Vec3.atCenterOf(colony.getCenter())))
                .findFirst().orElse(null);
    }

    /** How many open (air) cells there are under the surface of the test area. */
    private static int airBelowSurface(GameTestHelper helper) {
        int air = 0;
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE; y++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        air++;
                    }
                }
            }
        }
        return air;
    }

    private static int moundBlocks(GameTestHelper helper) {
        int count = 0;
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = SURFACE; y < SURFACE + 4; y++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.BURROWED_DIRT.get())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** A one-line summary of what everyone is doing, for the log. */
    private static String describe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO));
        Colony colony = colony(helper);
        StringBuilder text = new StringBuilder();
        if (colony != null) {
            BlockPos spot = colony.pickMoundSpot(level, level.random);
            text.append("mound spot ").append(spot == null ? "none" : spot.subtract(BlockPos.containing(origin)).toShortString());
            if (spot != null) {
                BlockPos bottom = colony.getCenter().below(3);
                var route = com.antcolonies.nav.AntPathFinder.find(new com.antcolonies.nav.LevelCellMap(level), bottom, spot, 1500);
                text.append(" route-from-shaft-bottom ").append(route == null ? "NONE" : route.size() + " steps");
            }
            BlockPos c = colony.getCenter();
            int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + 2, c.getZ());
            text.append(" [center ").append(c.toShortString()).append(" origin ").append(BlockPos.containing(origin).toShortString())
                    .append(" height@+2 ").append(h)
                    .append(" hasChunk ").append(level.hasChunk((c.getX() + 2) >> 4, c.getZ() >> 4))
                    .append(" target ").append(colony.findDigTarget(level) == null ? "none" : colony.findDigTarget(level).toShortString())
                    .append("]; ");
        }
        text.append("next target ")
                .append(colony == null || colony.findDigTarget(level) == null
                        ? "none" : colony.findDigTarget(level).subtract(BlockPos.containing(origin)).toShortString())
                .append(", mound blocks ").append(moundBlocks(helper)).append(". Ants:");
        List<AbstractAntEntity> ants = level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds().inflate(10));
        for (AbstractAntEntity ant : ants) {
            Vec3 at = ant.position().subtract(origin);
            text.append(String.format(" [#%d %.1f,%.1f,%.1f%s%s%s]", ant.getId(), at.x, at.y, at.z,
                    ant.isCarrying() ? " carrying" : "",
                    ant.getNavigator().isDone() ? (ant.getNavigator().hasFailed() ? " nav-failed" : " idle") : " walking",
                    ant.isClimbing() ? " climbing" : ""));
        }
        return text.toString();
    }
}
