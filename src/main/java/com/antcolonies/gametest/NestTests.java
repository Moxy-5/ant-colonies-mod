package com.antcolonies.gametest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.Hill;
import com.antcolonies.colony.Nest;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.QueenAntEntity;
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
    private static final int SURFACE = 12;
    private static final int MIDDLE = 12;
    /**
     * The whole nest: a 3-deep shaft, then four levels (food store 5x5, nursery 5x5, queen 3x3, refuse chamber 5x5),
     * each a 3-long tunnel and its room, and a 3-block stair down to every level but the first.
     */
    /** The shaft, the founding chamber (1-block tunnel, 3x3 room), 4 tunnels, the 4 rooms and 3 stairs. */
    private static final int FOUNDING_CELLS = 1 + 9;
    private static final int NEST_CELLS = 3 + FOUNDING_CELLS + 4 * 3 + (25 + 25 + 9 + 25) + 3 * NestPlan.STAIR_LENGTH;

    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
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
            helper.assertTrue(colony.nest().findDigTarget(level) == null, "something is still left to dig");
            var rooms = colony.nest().getPlannedChambers(level);
            helper.assertTrue(rooms.size() == 4, "expected 4 levels (food store, nursery, queen, refuse chamber) but the nest has " + rooms.size());
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
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
     * Nest stages: six workers with no stored food dig only the first level (the seed store); the nursery, the queen's
     * deep room and the refuse chamber wait. Once the store holds 10 seeds (with 6 workers) the second level, the
     * nursery, is dug too, and the third still waits (it needs 12 workers).
     * <p>Between the two, with nothing to do, every worker goes inside to rest by the entrance (none strolls about
     * outside; the user's report 2026-10-15).
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void theNestGrowsDeeperOnlyAsTheColonyGrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper, 6, false);
        int[] phase = {0};
        long[] idleFrom = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null || helper.getTick() < 20) {
                return;
            }
            List<NestPlan.Chamber> levels = colony.nest().getPlannedChambers(level);
            if (levels.size() < 3) {
                helper.fail("expected the full nest plan, but it has " + levels.size() + " levels");
            }
            if (phase[0] == 0 && colony.nest().findDigTarget(level) == null && dugOut(level, levels.get(0))) {
                helper.assertTrue(colony.nest().levelsUnlocked(level) == 1, "with 6 workers and no food only 1 level should be open, not "
                        + colony.nest().levelsUnlocked(level));
                helper.assertTrue(noneDug(level, levels.get(1)), "the nursery was dug before the colony had the food for it");
                idleFrom[0] = helper.getTick();
                phase[0] = 2;
            } else if (phase[0] == 2) {
                // Nothing to do (nothing to dig, no young, no seeds about): the spare workers go in and rest by the
                // entrance, as real harvester ants do; they do not stroll about outside.
                List<WorkerAntEntity> outside = level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds(),
                        w -> w.isAlive() && !colony.nest().isInside(level, w.blockPosition()));
                if (!outside.isEmpty() || helper.getTick() < idleFrom[0] + 600) {
                    helper.assertTrue(helper.getTick() < idleFrom[0] + 8000, "with nothing to do every worker should be inside, but "
                            + outside.size() + " are out, e.g. at " + (outside.isEmpty() ? "-" : outside.get(0).blockPosition().toShortString()));
                    return;
                }
                // Ten seeds in the store.
                List<BlockPos> store = colony.nest().getFoodStoreCells(level);
                for (int i = 0; i < 10; i++) {
                    helper.assertTrue(colony.food().depositSeed(level, store.get(i % store.size())), "could not put a seed in the store");
                }
                phase[0] = 1;
            } else if (phase[0] == 1 && colony.nest().findDigTarget(level) == null && dugOut(level, levels.get(1))) {
                helper.assertTrue(noneDug(level, levels.get(2)), "the queen's deep room was dug with only 6 workers");
                helper.succeed();
            }
        });
    }

    private static boolean dugOut(ServerLevel level, NestPlan.Chamber chamber) {
        return chamber.cells().stream().allMatch(pos -> Nest.isDugOut(level, pos));
    }

    private static boolean noneDug(ServerLevel level, NestPlan.Chamber chamber) {
        // Only its room: the stair down to it starts in the level above.
        return chamber.room().stream().noneMatch(pos -> Nest.isDugOut(level, pos));
    }

    /**
     * The nest has holes in its walls: cave pockets next to the shaft and the rooms, a crack from the queen's room
     * up to the open air, a pit under a corner of the nursery, and a gap that links the nursery with the
     * queen's room under it. The ants must close every one of them with soil, so that the only way in is the entrance hole and
     * the rooms are joined only by their own tunnels.
     * <p>
     * Then damage to the finished nest: nine blocks of the hill are knocked out (a top block, a block from the middle of a
     * column with the top still hanging over the gap, whole columns, the single block of the outer ring), and the nest
     * walls are opened in five places (three beside the top and the middle of the shaft, one next to the nursery, and
     * one between the nursery and the queen's room). The workers must mend all of it: the hill is the planned hill again,
     * and the nest has no opening but the entrance hole.
     */
    @GameTest(template = "arena", timeoutTicks = 100000, skyAccess = true)
    public static void workersCloseEveryHoleInTheNestWallsAndMendDamage(GameTestHelper helper) {
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
            var rooms = colony.nest().getPlannedChambers(level);
            if (rooms.isEmpty()) {
                return;
            }
            BlockPos bottom = colony.getCenter().below(Nest.SHAFT_DEPTH);
            Direction tunnel = Direction.fromDelta(
                    rooms.get(0).cells().get(0).getX() - bottom.getX(), 0, rooms.get(0).cells().get(0).getZ() - bottom.getZ());
            BlockPos queenRoom = rooms.get(2).center();
            int groundTop = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
            for (int y = queenRoom.getY() + 1; y <= groundTop; y++) {
                holes.add(new BlockPos(queenRoom.getX(), y, queenRoom.getZ()));
            }
            holes.add(bottom.relative(tunnel, 4).relative(tunnel.getClockWise(), 3)); // next to the corner of the 5x5 seed store
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
                        helper.getTick(), colony == null ? -1 : colony.walls().getLeaks(level).size(), airBelowSurface(helper), describe(helper));
            }
        });

        boolean[] damaged = {false};
        logStalls(helper);
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (damaged[0] || colony == null || holes.isEmpty() || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty() || !colony.walls().getLeaks(level).isEmpty() || !openSpaceProblems(helper, colony).isEmpty()) {
                return;
            }
            damaged[0] = true;
            AntColoniesMod.LOGGER.info("[seal-test] every hole closed at tick {}", helper.getTick());
            // The hill: {column dx, column dz, first layer, last layer} of the blocks that are knocked out.
            int[][] hill = {{1, 0, 2, 2}, {0, -1, 1, 1}, {-1, -1, 0, 2}, {2, 1, 1, 1}, {3, 0, 0, 0}, {-2, 0, 0, 1}};
            for (int[] column : hill) {
                for (int layer = column[2]; layer <= column[3]; layer++) {
                    helper.setBlock(MIDDLE + column[0], SURFACE + layer, MIDDLE + column[1], Blocks.AIR);
                }
            }
            // The walls: next to the top and the middle of the shaft, next to the nursery, between the nursery and the queen's room.
            var rooms = colony.nest().getPlannedChambers(level);
            BlockPos bottom = colony.getCenter().below(Nest.SHAFT_DEPTH);
            Direction tunnel = Direction.fromDelta(
                    rooms.get(0).cells().get(0).getX() - bottom.getX(), 0, rooms.get(0).cells().get(0).getZ() - bottom.getZ());
            List<BlockPos> damage = List.of(
                    colony.getCenter().below(1).east(),
                    colony.getCenter().below(2).west(),
                    rooms.get(1).center().relative(tunnel.getClockWise(), 3),
                    rooms.get(2).center().above(),
                    colony.getCenter().below(1).north());
            for (BlockPos hole : damage) {
                level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
            }
            AntColoniesMod.LOGGER.info("[mend-test] tick {}: hill and walls damaged, {} nest openings: {}",
                    helper.getTick(), colony.walls().getLeaks(level).size(), damage);
        });
        helper.onEachTick(() -> {
            if (damaged[0] && helper.getTick() % 200 == 0) {
                Colony colony = colony(helper);
                AntColoniesMod.LOGGER.info("[mend-test] tick {}: hill problems: {} | open leaks {}", helper.getTick(),
                        hillProblems(helper), colony == null ? -1 : colony.walls().getLeaks(level).size());
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(damaged[0] && colony != null, "the holes are not all closed yet (so the nest has not been damaged yet)");
            String hill = hillProblems(helper);
            helper.assertTrue(hill.isEmpty(), "the hill is not mended yet: " + hill);
            helper.assertTrue(colony.nest().findDigTarget(level) == null, "something is still left to dig");
            var leaks = colony.walls().getLeaks(level);
            helper.assertTrue(leaks.isEmpty(), "the nest still has openings: " + leaks);
            String reach = openSpaceProblems(helper, colony);
            helper.assertTrue(reach.isEmpty(), "open space joined to the nest that is not part of it: " + reach);
            AntColoniesMod.LOGGER.info("[mend-test] finished at tick {}", helper.getTick());
        });
    }

    /**
     * Many ants with a soil pellet each    /**
     * Two jams the user saw in the hill. First: a gap in the hill where the whole traffic of the hole passes, so ants
     * always stood in it and the pellet could never be set. Now the pellet goes in and the ants standing there are nudged
     * out onto the new block; a player or an animal in the spot still stops it. Then: many ants with a soil pellet each
     * and only ONE gap left in the hill (they jammed in the shaft): the first pellet fills the gap, all the others must
     * let go of theirs, and nobody may stay stuck with soil in the mandibles.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void manyAntsWithPelletsAndOneGapDoNotJam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper, 12);

        int[] stage = {0};
        long[] startedAt = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (stage[0] != 0 || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty() || !colony.walls().getLeaks(level).isEmpty()) {
                return;
            }
            stage[0] = 1;
            startedAt[0] = helper.getTick();
            pelletGoesInAmongAnts(helper, level, colony);
            // One gap in the first ring of the hill: the top two layers of one column (the user's picture), so the spot is one block up.
            helper.setBlock(MIDDLE + 1, SURFACE + 1, MIDDLE, Blocks.AIR);
            helper.setBlock(MIDDLE + 1, SURFACE + 2, MIDDLE, Blocks.AIR);
            Vec3 shaft = Vec3.atBottomCenterOf(colony.getCenter().below(2));
            for (var worker : colonyWorkers(helper, colony)) {
                worker.moveTo(shaft.x, shaft.y, shaft.z); // all of them come up the shaft with soil, as in the report
                worker.setCarriedBlock(ModBlocks.BURROWED_DIRT.get().defaultBlockState());
            }
            AntColoniesMod.LOGGER.info("[jam-test] tick {}: one gap, every worker has a pellet", helper.getTick());
        });
        helper.onEachTick(() -> {
            if (stage[0] == 1 && helper.getTick() % 100 == 0) {
                Colony colony = colony(helper);
                long carrying = colonyWorkers(helper, colony).stream().filter(w -> w.isCarrying()).count();
                AntColoniesMod.LOGGER.info("[jam-test] tick {}: {} ants still carry soil, hill problems: {}", helper.getTick(), carrying,
                        hillProblems(helper));
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(stage[0] == 1 && colony != null, "the gap has not been made yet");
            helper.assertTrue(helper.getTick() - startedAt[0] > 100, "too early");
            helper.assertTrue(hillProblems(helper).isEmpty(), "the gap is not filled: " + hillProblems(helper));
            long carrying = colonyWorkers(helper, colony).stream().filter(w -> w.isCarrying()).count();
            helper.assertTrue(carrying == 0, carrying + " ants still carry soil");
        });
    }

    /**
     * The first jam of {@link #manyAntsWithPelletsAndOneGapDoNotJam}: a pellet set in a gap of the hill with five frozen
     * ants standing in it goes in (they are nudged out on top), one with a sheep in the gap does not.
     */
    private static void pelletGoesInAmongAnts(GameTestHelper helper, ServerLevel level, Colony colony) {
        helper.setBlock(MIDDLE + 1, SURFACE + 2, MIDDLE, Blocks.AIR);
        BlockPos gap = helper.absolutePos(new BlockPos(MIDDLE + 1, SURFACE + 2, MIDDLE));
        // Freeze the ants so they stay in the gap, and put a sheep in as well: the sheep must stop the pellet.
        var crowd = new java.util.ArrayList<WorkerAntEntity>();
        for (int i = 0; i < 5; i++) {
            WorkerAntEntity ant = ModEntities.WORKER_ANT.get().create(level);
            ant.moveTo(gap.getX() + 0.5, gap.getY(), gap.getZ() + 0.5);
            ant.setNoAi(true);
            level.addFreshEntity(ant);
            crowd.add(ant);
        }
        var sheep = net.minecraft.world.entity.EntityType.SHEEP.create(level);
        sheep.moveTo(gap.getX() + 0.5, gap.getY(), gap.getZ() + 0.5);
        sheep.setNoAi(true);
        level.addFreshEntity(sheep);
        helper.assertTrue(!colony.hill().placePellet(level, gap), "the pellet was set on top of a sheep");
        sheep.discard();
        helper.assertTrue(colony.hill().placePellet(level, gap), "the pellet was not set with ants standing in the gap");
        helper.assertTrue(level.getBlockState(gap).is(ModBlocks.BURROWED_DIRT.get()), "no block was placed");
        for (WorkerAntEntity ant : crowd) {
            helper.assertTrue(ant.getY() >= gap.getY() + 1 - 0.01, "an ant is still inside the new block (y " + ant.getY() + ")");
            ant.discard();
        }
    }

    /**
     * Thin soil: stone starts right under    /**
     * Thin soil: stone starts right under the third level. The rooms do not fit at the usual depth (only two levels
     * would), so the whole nest must sit one block higher and have three levels (seed store, nursery, queen), and the
     * ants must still dig all of it and never touch the stone.
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void nestSitsOneBlockHigherWhenTheStoneIsTooClose(GameTestHelper helper) {
        // Stone up to the floor layer of the third level at the usual depth (SURFACE - 3 - 4 = 5): two levels fit there,
        // but one block higher the third level fits and the fourth does not.
        thinSoil(helper, SURFACE - Nest.SHAFT_DEPTH - 2 * NestPlan.LEVEL_SPACING, 3, Nest.SHAFT_DEPTH - 1);
    }

    /** Even thinner soil: only the first level fits, so the nest is just the queen's room. */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void theNestHasFewerLevelsWhenTheSoilIsThin(GameTestHelper helper) {
        thinSoil(helper, SURFACE - Nest.SHAFT_DEPTH - NestPlan.LEVEL_SPACING + 1, 1, Nest.SHAFT_DEPTH);
    }

    /** Stone fills the ground up to and including layer {@code stoneTop}; the nest must have {@code levels} levels and a shaft {@code shaft} deep. */
    private static void thinSoil(GameTestHelper helper, int stoneTop, int levels, int shaft) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);
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
            var rooms = colony.nest().getPlannedChambers(level);
            helper.assertTrue(rooms.size() == levels, "the nest has " + rooms.size() + " levels, expected " + levels);
            helper.assertTrue(rooms.get(rooms.size() - 1).kind() == NestPlan.Kind.QUEEN, "the last level is not the queen's room");
            int expected = shaft + FOUNDING_CELLS;
            for (var room : rooms) {
                expected += room.cells().size();
            }
            helper.assertTrue(airBelowSurface(helper) == expected,
                    "nest not finished: " + airBelowSurface(helper) + " of " + expected + " cells dug. " + describe(helper));
            helper.assertTrue(colony.nest().findDigTarget(level) == null, "something is still left to dig");
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the room is not finished");
            var queens = level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds());
            helper.assertTrue(!queens.isEmpty() && queens.get(0).blockPosition().equals(room), "the queen is not in the middle of the room yet");
            for (int x = 0; x < 25; x++) {
                for (int z = 0; z < 25; z++) {
                    helper.assertTrue(!helper.getBlockState(new BlockPos(x, stoneTop, z)).isAir(), "an ant dug into the stone layer at " + x + "," + z);
                }
            }
            var leaks = colony.walls().getLeaks(level);
            helper.assertTrue(leaks.isEmpty(), "the nest still has openings: " + leaks);
            AntColoniesMod.LOGGER.info("[thin-soil-test] finished at tick {}", helper.getTick());
        });
    }

    /**
     * A queen on her own (no workers), as when she founds a colony: she digs the shaft 2 blocks down and her small
     * founding chamber off it, and nothing else; she settles in it, closes the entrance from inside, lays her 3 eggs and
     * feeds the larvae on her own fat until they are her first 3 workers. They open the entrance again. She cannot
     * place blocks, so no hill is built while she is alone. Then (the user's decision, 2026-10-15) her first workers all
     * die: she closes herself in again and raises a second, smaller batch of 2 from the fat she has left; when those die
     * too she is spent, gets hungry and starves.
     */
    @GameTest(template = "arena", timeoutTicks = 180000, skyAccess = true)
    public static void aLoneQueenFoundsHerColonyAndRaisesHerFirstWorkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper, 0);
        int[] dugWhenSealed = {-1};
        int[] moundWhenSealed = {-1};
        BlockPos entrance = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        int[] phase = {0}; // 0 the first batch, 1 the second batch, 2 spent, 3 starving
        java.util.UUID[] queenId = new java.util.UUID[1];

        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null) {
                return;
            }
            if (dugWhenSealed[0] < 0 && level.getBlockState(entrance).is(ModBlocks.BURROWED_DIRT.get())) {
                dugWhenSealed[0] = airBelowSurface(helper);
                moundWhenSealed[0] = moundBlocks(helper);
                AntColoniesMod.LOGGER.info("[founding-test] tick {}: sealed in, {} cells dug. {}", helper.getTick(), dugWhenSealed[0], describe(helper));
            }
            if (helper.getTick() % 400 == 0) {
                AntColoniesMod.LOGGER.info("[founding-test] tick {}: eggs laid {}, eggs {}, larvae {}, workers {}, {} cells dug",
                        helper.getTick(), colony.queenCare().getEggsLaid(), colony.brood().countEggs(level), colony.brood().countLarvae(level),
                        colony.getCount(WorkerAntEntity.CASTE), airBelowSurface(helper));
            }
        });

        helper.succeedWhen(() -> {
            if (phase[0] == 3) {
                // The spent queen starves: she dies and the colony ends.
                net.minecraft.world.entity.Entity queen = level.getEntity(queenId[0]);
                helper.assertTrue(queen == null || !queen.isAlive(), "the spent queen should have starved");
                helper.assertTrue(colony(helper) == null, "the colony should be gone with her");
                return;
            }
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no colony yet");
            if (phase[0] > 0) {
                laterBatches(helper, colony, phase, queenId);
                return;
            }
            helper.assertTrue(dugWhenSealed[0] >= 0, "she has not sealed herself in yet: " + airBelowSurface(helper) + " cells dug");
            // Shaft 2 down + the founding chamber only: the workers dig the rest.
            int expected = NestPlan.FOUNDING_DEPTH + FOUNDING_CELLS;
            helper.assertTrue(dugWhenSealed[0] == expected, "she had dug " + dugWhenSealed[0] + " cells when she sealed herself in, expected " + expected);
            // The only Burrowed Dirt above ground is the plug in the entrance.
            helper.assertTrue(moundWhenSealed[0] == 1, "the queen placed soil (" + moundWhenSealed[0] + " blocks above ground, only the plug expected)");
            NestPlan.Chamber founding = colony.nest().getFoundingChamber(level);
            helper.assertTrue(founding != null, "the nest has no founding chamber");
            helper.assertTrue(colony.queenCare().getEggsLaid() == 3, colony.queenCare().getEggsLaid() + " eggs laid, expected 3");
            helper.assertTrue(colony.brood().countBrood(level) == 0, "still " + colony.brood().countBrood(level) + " eggs and larvae");
            var workers = level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds(), w -> colony.getId().equals(w.getColonyId()));
            helper.assertTrue(workers.size() == 3 && colony.getCount(WorkerAntEntity.CASTE) == 3,
                    workers.size() + " workers, the colony counts " + colony.getCount(WorkerAntEntity.CASTE) + ", expected 3");
            helper.assertTrue(colony.nest().isEntranceOpen(level), "the entrance is still closed");
            helper.assertTrue(airBelowSurface(helper) > expected, "the workers have not started on the rest of the nest");
            AntColoniesMod.LOGGER.info("[founding-test] first workers out at tick {}; now they all die", helper.getTick());
            helper.assertTrue(!colony.queenCare().isOnSecondBatch(), "no second batch while her first workers live");
            queenId[0] = colony.getQueenId();
            workers.forEach(WorkerAntEntity::kill);
            phase[0] = 1;
            helper.fail("(the second batch comes next)");
        });
    }

    /** {@link #aLoneQueenFoundsHerColonyAndRaisesHerFirstWorkers}, once her first workers have died. */
    private static void laterBatches(GameTestHelper helper, Colony colony, int[] phase, java.util.UUID[] queenId) {
        ServerLevel level = helper.getLevel();
        var workers = level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds(), w -> w.isAlive() && colony.getId().equals(w.getColonyId()));
        if (phase[0] == 1) {
            helper.assertTrue(colony.queenCare().isOnSecondBatch(), "her first workers are dead: she should start a second batch");
            helper.assertTrue(colony.queenCare().getEggsLaid() <= 5, "the second batch is 2 eggs: " + colony.queenCare().getEggsLaid() + " laid in all");
            helper.assertTrue(!colony.queenCare().isSpent(level) || colony.queenCare().getEggsLaid() < 5, "she is not spent while raising her second batch");
            helper.assertTrue(colony.queenCare().getEggsLaid() == 5 && colony.brood().countBrood(level) == 0 && workers.size() == 2
                    && colony.getCount(WorkerAntEntity.CASTE) == 2, "the second batch should grow into 2 workers: eggs laid "
                    + colony.queenCare().getEggsLaid() + ", brood " + colony.brood().countBrood(level) + ", workers " + workers.size());
            AntColoniesMod.LOGGER.info("[founding-test] second batch out at tick {}; now they die too", helper.getTick());
            workers.forEach(WorkerAntEntity::kill);
            phase[0] = 2;
            helper.fail("(she is spent next)");
        }
        // Spent: nothing more to lay, nobody to feed her; her hunger clock runs.
        helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 0 && colony.queenCare().isSpent(level), "she should be spent with no workers left");
        helper.assertTrue(colony.queenCare().getEggsLaid() == 5, "no third batch: " + colony.queenCare().getEggsLaid() + " laid");
        colony.setHungerOn(true); // (off in the nest tests)
        colony.queenCare().setQueenLastFed(level.getGameTime() - colony.profile().queen().starveTicks() + 100);
        phase[0] = 3;
        helper.fail("(she starves next)");
    }

    /**
     * Seeds lying on the ground around a finished nest (two single ones and a pile of three) are fetched one by one,
     * and three tufts of short grass are clipped (ten seeds off each, then the tuft breaks): every seed ends up on the
     * floor of the food store, the colony counts them, and nothing is left outside.
     * It runs in a batch of its own: its foragers fetch seeds from up to 24 blocks away, so seeds or grass in a
     * neighbouring test's arena were brought in too (it once counted 7 or 8 seeds instead of 5).
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true, batch = "seeds_alone")
    public static void workersCarrySeedsAndClipGrassIntoTheFoodStore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);
        int[][] tufts = {{MIDDLE + 5, MIDDLE + 6}, {MIDDLE - 6, MIDDLE - 4}, {MIDDLE - 2, MIDDLE + 7}};

        boolean[] dropped = {false};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (dropped[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            dropped[0] = true;
            dropSeeds(helper, MIDDLE + 6, MIDDLE, 1);
            dropSeeds(helper, MIDDLE - 6, MIDDLE + 2, 1);
            dropSeeds(helper, MIDDLE + 1, MIDDLE - 7, 3);
            for (int[] tuft : tufts) {
                helper.setBlock(tuft[0], SURFACE, tuft[1], Blocks.SHORT_GRASS);
            }
            AntColoniesMod.LOGGER.info("[seed-test] tick {}: 5 seeds dropped and 3 tufts of grass planted around the hill", helper.getTick());
        });
        helper.onEachTick(() -> {
            if (dropped[0] && helper.getTick() % 200 == 0) {
                Colony colony = colony(helper);
                AntColoniesMod.LOGGER.info("[seed-test] tick {}: food {}, seeds in the store {}, seeds anywhere {}",
                        helper.getTick(), colony.food().getFood(), seedsIn(level, storeBox(level, colony)), seedsIn(level, helper.getBounds()));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(dropped[0] && colony != null, "no seeds have been dropped yet");
            for (int[] tuft : tufts) {
                helper.assertBlock(new BlockPos(tuft[0], SURFACE, tuft[1]), block -> block == Blocks.AIR, "a tuft of grass is still there");
            }
            int expected = 5 + tufts.length * colony.food().piecesOf(Blocks.SHORT_GRASS.defaultBlockState());
            // The workers also feed the queen from the store: count what she has eaten too.
            int total = seedsAccountedFor(colony);
            helper.assertTrue(total == expected, "the colony has " + colony.food().getFood() + " food and the queen ate " + colony.queenCare().getQueenBread()
                    + " (+ " + colony.queenCare().getEggsLaid() + " eggs laid), expected " + expected + " seeds in all; holding: " + carriers(helper));
            helper.assertTrue(seedsIn(level, storeBox(level, colony)) == colony.food().getFood(), "only " + seedsIn(level, storeBox(level, colony)) + " seeds are in the food store");
            helper.assertTrue(seedsIn(level, helper.getBounds()) == colony.food().getFood(), "some seeds are still lying around outside the store");
            for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds())) {
                helper.assertTrue(!ant.isCarryingSeed(), "an ant is still carrying a seed");
            }
        });
    }

    /**
     * In a finished nest with 25 seeds in the food store the nurse mills them into ant bread in the nursery and the
     * workers feed the queen 20 pieces. She then carries an egg for 3 minutes (3600 ticks), the egg comes out behind her
     * in her room, and a worker carries it into the nursery. After that she is fed the 5 pieces that are left.
     * Milling: the first seed is gone from the store as the nurse takes it, and only becomes bread (on the nursery floor)
     * after the milling time.
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void queenIsFedAndHerEggGoesToTheNursery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] stocked = {false};
        long[] incubationStart = {-1};
        long[] laidAt = {-1};
        boolean[] cameOutBehindHer = {false};
        boolean[] breadInNursery = {false};
        long[] seedTakenAt = {-1};
        long[] breadAt = {-1};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (stocked[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            stocked[0] = true;
            var store = colony.nest().getFoodStoreCells(level);
            for (int i = 0; i < 25; i++) {
                colony.food().depositSeed(level, store.get(i / 8));
            }
            AntColoniesMod.LOGGER.info("[egg-test] tick {}: 25 seeds put in the food store", helper.getTick());
        });
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (!stocked[0] || colony == null) {
                return;
            }
            if (seedTakenAt[0] < 0 && colony.food().getFood() < 25) {
                seedTakenAt[0] = level.getGameTime();
            }
            if (breadAt[0] < 0 && colony.bakery().getBread() + colony.queenCare().getQueenBread() >= 1) {
                breadAt[0] = level.getGameTime();
                AntColoniesMod.LOGGER.info("[egg-test] tick {}: the first bread ({} seconds after the first seed was taken)", helper.getTick(),
                        (breadAt[0] - seedTakenAt[0]) / 20.0);
            }
            if (!breadInNursery[0] && colony.bakery().getBread() > 0) {
                for (BlockPos pos : colony.nest().getNurseryCells(level)) {
                    breadInNursery[0] |= level.getBlockState(pos).is(ModBlocks.BREAD_PILE.get());
                }
            }
            if (incubationStart[0] < 0 && colony.queenCare().isIncubating()) {
                incubationStart[0] = level.getGameTime();
                AntColoniesMod.LOGGER.info("[egg-test] tick {}: the queen is full and carries an egg, food {}", helper.getTick(), colony.food().getFood());
            }
            if (laidAt[0] < 0 && colony.queenCare().getEggsLaid() > 0) {
                laidAt[0] = level.getGameTime();
                // The egg must lie in her room, behind her (she looks the other way), and not in the nursery yet.
                var queens = level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds());
                var eggs = colony.brood().getLooseEggs(level);
                if (queens.size() == 1 && eggs.size() == 1) {
                    var queen = queens.get(0);
                    Vec3 away = eggs.get(0).position().subtract(queen.position());
                    Vec3 facing = Vec3.directionFromRotation(0.0F, queen.getYRot());
                    BlockPos room = colony.nest().getFinishedRoomCenter(level);
                    cameOutBehindHer[0] = away.dot(facing) <= 0.001 && room != null
                            && Math.abs(eggs.get(0).getX() - (room.getX() + 0.5)) <= 2.5 && Math.abs(eggs.get(0).getZ() - (room.getZ() + 0.5)) <= 2.5;
                    AntColoniesMod.LOGGER.info("[egg-test] tick {}: egg laid {} from the queen, facing {}", helper.getTick(), away, facing);
                } else if (queens.size() == 1 && eggs.isEmpty() && level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds())
                        .stream().anyMatch(AbstractAntEntity::isCarryingEgg)) {
                    // A nurse standing next to her picked the egg up in the very tick it came out.
                    cameOutBehindHer[0] = true;
                }
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[egg-test] tick {}: food {}, queen fed {}, eggs laid {}, eggs {} (nursery {})", helper.getTick(),
                        colony.food().getFood(), colony.queenCare().getQueenBread(), colony.queenCare().getEggsLaid(), colony.brood().countEggs(level), colony.brood().getNurseryEggs(level).size());
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(stocked[0] && colony != null, "the store has not been stocked yet");
            long milling = breadAt[0] - seedTakenAt[0];
            helper.assertTrue(breadAt[0] >= 0 && milling >= colony.profile().food().millTicks(),
                    "the first bread was ready " + milling + " ticks after the first seed was taken, milling takes " + colony.profile().food().millTicks());
            helper.assertTrue(milling <= colony.profile().food().millTicks() + 600, "milling took " + milling + " ticks, far longer than " + colony.profile().food().millTicks());
            helper.assertTrue(colony.queenCare().getEggsLaid() == 1, "the queen has laid " + colony.queenCare().getEggsLaid() + " eggs, expected 1");
            long carried = laidAt[0] - incubationStart[0];
            helper.assertTrue(carried >= colony.profile().queen().incubationTicks() && carried <= colony.profile().queen().incubationTicks() + 100,
                    "the egg came out after " + carried + " ticks, expected " + colony.profile().queen().incubationTicks());
            helper.assertTrue(cameOutBehindHer[0], "the egg did not come out behind the queen in her room");
            var eggs = colony.brood().getEggs(level);
            helper.assertTrue(eggs.size() == 1 && colony.brood().getNurseryEggs(level).size() == 1,
                    "expected 1 egg, in the nursery, but there are " + eggs.size() + " (" + colony.brood().getNurseryEggs(level).size() + " in the nursery)");
            helper.assertTrue(level.getEntitiesOfClass(com.antcolonies.entity.AntEggEntity.class, helper.getBounds()).size() == 1,
                    "there is an egg somewhere else");
            helper.assertTrue(breadInNursery[0], "no bread was ever seen on the nursery floor");
            helper.assertTrue(colony.queenCare().getQueenBread() == 5, "the queen was fed " + colony.queenCare().getQueenBread() + " pieces of bread for the next egg, expected the 5 that were left");
            helper.assertTrue(colony.food().getFood() == 0 && seedsIn(level, storeBox(level, colony)) == 0, "seeds are left in the store");
            helper.assertTrue(colony.bakery().getBread() == 0 && breadIn(level, helper.getBounds()) == 0, "bread is left in the nursery");
            for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds())) {
                helper.assertTrue(!ant.hasFullMandibles(), "an ant is still carrying something");
            }
        });
    }

    /**
     * Five rich seeds (pumpkin) in the food store: the nurses mill them into rich ant bread and feed it to the queen, and
     * the fifth piece starts a burst. She then lays one egg a minute for five minutes without being fed any more.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void richBreadStartsABurstOfEggs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] stocked = {false};
        long[] burstStart = {-1};
        List<Long> layTimes = new ArrayList<>();
        int[] lastLaid = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null) {
                return;
            }
            if (!stocked[0]) {
                if (airBelowSurface(helper) == NEST_CELLS && colony.nest().findDigTarget(level) == null && hillProblems(helper).isEmpty()) {
                    stocked[0] = true;
                    for (int i = 0; i < colony.profile().queen().richMeal(); i++) {
                        colony.food().depositSeed(level, colony.nest().getFoodStoreCells(level).get(0), true);
                    }
                    AntColoniesMod.LOGGER.info("[burst-test] tick {}: {} rich seeds put in the food store", helper.getTick(), colony.food().getRichSeeds());
                }
                return;
            }
            if (burstStart[0] < 0 && colony.queenCare().getBurstLeft() > 0) {
                burstStart[0] = level.getGameTime();
                AntColoniesMod.LOGGER.info("[burst-test] tick {}: the burst starts", helper.getTick());
            }
            if (colony.queenCare().getEggsLaid() != lastLaid[0]) {
                lastLaid[0] = colony.queenCare().getEggsLaid();
                layTimes.add(level.getGameTime());
                AntColoniesMod.LOGGER.info("[burst-test] tick {}: egg {} laid, {} left in the burst", helper.getTick(), lastLaid[0], colony.queenCare().getBurstLeft());
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(stocked[0] && colony != null && burstStart[0] >= 0, "the burst has not started yet");
            helper.assertTrue(layTimes.size() >= colony.profile().queen().burstEggs(), layTimes.size() + " of " + colony.profile().queen().burstEggs() + " burst eggs laid so far");
            helper.assertTrue(layTimes.size() == colony.profile().queen().burstEggs(), layTimes.size() + " eggs laid, expected exactly " + colony.profile().queen().burstEggs());
            long previous = burstStart[0];
            for (long laid : layTimes) {
                long gap = laid - previous;
                helper.assertTrue(gap >= colony.profile().queen().burstIntervalTicks() && gap <= colony.profile().queen().burstIntervalTicks() + 60,
                        "an egg came " + gap + " ticks after the one before, expected " + colony.profile().queen().burstIntervalTicks());
                previous = laid;
            }
            helper.assertTrue(colony.queenCare().getBurstLeft() == 0 && !colony.queenCare().isIncubating(), "the burst is not over");
            helper.assertTrue(colony.queenCare().getQueenRich() == 0 && colony.bakery().getRichBread() == 0 && colony.food().getRichSeeds() == 0,
                    "rich food is left: queen " + colony.queenCare().getQueenRich() + ", bread " + colony.bakery().getRichBread() + ", seeds " + colony.food().getRichSeeds());
        });
    }

    /**
     * Three eggs put in the nursery of a finished nest hatch after 1 minute into larvae. The nurses feed each larva 5
     * pieces of bread (35 seeds are in the store: 20 for the queen, 15 for the larvae), and each then becomes a real
     * worker of the same colony, which the colony counts.
     */
    @GameTest(template = "arena", timeoutTicks = 80000, skyAccess = true)
    public static void eggsBecomeLarvaeAndNursesFeedThemIntoWorkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] placed = {false};
        int[] workersBefore = {0};
        int[] antsBefore = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (placed[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            placed[0] = true;
            workersBefore[0] = colony.getCount(WorkerAntEntity.CASTE);
            antsBefore[0] = colonyWorkers(helper, colony).size();
            var cells = colony.nest().getNurseryCells(level);
            for (int i = 0; i < 3; i++) {
                colony.brood().placeEgg(level, cells.get(i * 7));
            }
            var store = colony.nest().getFoodStoreCells(level);
            for (int i = 0; i < 35; i++) {
                colony.food().depositSeed(level, store.get(i / 8));
            }
            AntColoniesMod.LOGGER.info("[hatch-test] tick {}: 3 eggs put in the nursery, 35 seeds in the store, {} workers", helper.getTick(), workersBefore[0]);
        });
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (placed[0] && colony != null && helper.getTick() % 500 == 0) {
                AntColoniesMod.LOGGER.info("[hatch-test] tick {}: eggs {}, larvae {} (hunger {}), bread {}, food {}, workers {}", helper.getTick(),
                        colony.brood().countEggs(level), colony.brood().countLarvae(level), colony.brood().larvaHunger(level), colony.bakery().getBread(), colony.food().getFood(),
                        colony.getCount(WorkerAntEntity.CASTE));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(placed[0] && colony != null, "no eggs have been put in the nursery yet");
            helper.assertTrue(colony.brood().countLarvae(level) == 0, colony.brood().countLarvae(level) + " larvae are still there");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == workersBefore[0] + 3,
                    "the colony counts " + colony.getCount(WorkerAntEntity.CASTE) + " workers, expected " + (workersBefore[0] + 3));
            var workers = colonyWorkers(helper, colony);
            helper.assertTrue(workers.size() == antsBefore[0] + 3, "there are " + workers.size() + " worker ants, expected " + (antsBefore[0] + 3));
            for (var worker : workers) {
                helper.assertTrue(colony.getId().equals(worker.getColonyId()), "a worker belongs to no colony or the wrong one");
            }
        });
    }

    /**
     * When the colony already has one worker less than the most it can have, three fully fed larvae in the nursery make
     * exactly one worker; the other two stay larvae.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void larvaeWaitWhenTheColonyHasEnoughWorkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] placed = {false};
        long[] placedAt = {0};
        int[] workersBefore = {0};
        int[] antsBefore = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (placed[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            placed[0] = true;
            placedAt[0] = level.getGameTime();
            workersBefore[0] = colony.getCount(WorkerAntEntity.CASTE);
            antsBefore[0] = colonyWorkers(helper, colony).size();
            colony.setCount(WorkerAntEntity.CASTE, colony.profile().colony().maxWorkers() - 1);
            var cells = colony.nest().getNurseryCells(level);
            for (int i = 0; i < 3; i++) {
                BlockPos cell = cells.get(i * 7);
                colony.brood().spawnLarva(level, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5).setFed(colony.profile().brood().larvaMeal());
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(placed[0] && colony != null, "no larvae have been put in the nursery yet");
            // Wait well past the moment the first one grows up, so the other two had every chance to grow up too.
            helper.assertTrue(level.getGameTime() >= placedAt[0] + 600, "still waiting for the larvae to have grown up");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == colony.profile().colony().maxWorkers(),
                    "the colony counts " + colony.getCount(WorkerAntEntity.CASTE) + " workers, expected " + colony.profile().colony().maxWorkers());
            helper.assertTrue(colony.brood().countLarvae(level) == 2, colony.brood().countLarvae(level) + " larvae are left, expected 2");
            helper.assertTrue(colonyWorkers(helper, colony).size() == antsBefore[0] + 1,
                    "expected exactly one new worker, but there are " + colonyWorkers(helper, colony).size() + " workers and there were " + antsBefore[0]);
        });
    }

    /**
     * A worker that has not eaten for a day goes to the food store, takes one seed and eats it: the seed is gone from the
     * store, the colony counts it as eaten, and the worker is not hungry any more. The other workers eat nothing.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void aHungryWorkerEatsOneSeedFromTheFoodStore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] started = {false};
        WorkerAntEntity[] hungry = {null};
        int[] foodBefore = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (started[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            started[0] = true;
            var store = colony.nest().getFoodStoreCells(level);
            for (int i = 0; i < 40; i++) {
                colony.food().depositSeed(level, store.get(i / 8));
            }
            foodBefore[0] = colony.food().getFood();
            colony.setHungerOn(true);
            for (WorkerAntEntity worker : colonyWorkers(helper, colony)) {
                worker.setLastAte(level.getGameTime());
            }
            hungry[0] = colonyWorkers(helper, colony).get(0);
            hungry[0].setLastAte(level.getGameTime() - colony.profile().worker().mealIntervalTicks() - 5);
            AntColoniesMod.LOGGER.info("[hunger-test] tick {}: worker #{} is hungry, {} seeds in the store", helper.getTick(), hungry[0].getId(), foodBefore[0]);
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(started[0] && colony != null, "the nest is not finished yet");
            helper.assertTrue(colony.food().getSeedsEaten() >= 1, "nobody has eaten a seed yet");
            helper.assertTrue(colony.food().getSeedsEaten() == 1, colony.food().getSeedsEaten() + " seeds were eaten, expected exactly 1");
            helper.assertTrue(hungry[0].isAlive() && !hungry[0].isHungry(), "the worker is still hungry");
        });
    }

    /**
     * A worker with no food: after a day and a half without a meal it slows down to half speed, and after three days it
     * starves and leaves its body behind.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aStarvingWorkerSlowsDownAndDies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startColony(helper, 1);

        WorkerAntEntity[] worker = {null};
        double[] normalSpeed = {0};
        boolean[] slowed = {false};
        helper.runAfterDelay(30, () -> {
            Colony colony = colony(helper);
            colony.setHungerOn(true);
            worker[0] = colonyWorkers(helper, colony).get(0);
            normalSpeed[0] = worker[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            worker[0].setLastAte(level.getGameTime() - colony.profile().worker().slowAfterTicks() - 5);
        });
        helper.runAfterDelay(80, () -> {
            double speed = worker[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(speed - normalSpeed[0] * 0.5) < 0.001, "the starving worker's speed is " + speed + ", expected half of " + normalSpeed[0]);
            slowed[0] = true;
            worker[0].setLastAte(level.getGameTime() - colony(helper).profile().worker().starveAfterTicks() + 20);
        });
        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(slowed[0], "not slowed down yet");
            helper.assertTrue(!worker[0].isAlive(), "the worker has not starved yet");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 0, "the colony still counts " + colony.getCount(WorkerAntEntity.CASTE) + " workers");
            helper.assertTrue(colony.refuse().getCorpses(level).size() == 1, colony.refuse().getCorpses(level).size() + " bodies, expected 1");
        });
    }

    /**
     * A larva that goes two days without bread starves and leaves a body; a larva that was just fed lives on, and so does
     * a full one that waits to grow up. In a batch of its own. A body a worker is carrying to the refuse chamber counts
     * too (it failed in full runs with "0 bodies": the colony's own worker had picked it up).
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true, batch = "larva_alone")
    public static void aLarvaThatIsNotFedForTwoDaysStarves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startColony(helper, 1);

        AntLarvaEntity[] larvae = new AntLarvaEntity[3];
        helper.runAfterDelay(30, () -> {
            Colony colony = colony(helper);
            colony.setHungerOn(true);
            long now = level.getGameTime();
            BlockPos base = helper.absolutePos(new BlockPos(MIDDLE + 4, SURFACE, MIDDLE + 4));
            for (int i = 0; i < 3; i++) {
                larvae[i] = colony.brood().spawnLarva(level, base.getX() + 0.5 + i, base.getY(), base.getZ() + 0.5);
                larvae[i].setLastFed(now - colony.profile().brood().larvaStarveTicks() + 60); // three seconds from starving
            }
            larvae[1].feed(); // fed just now: starts over
            larvae[2].setFed(colony.profile().brood().larvaMeal()); // full: nothing to starve of (it waits for a place as a worker, or hatches)
        });
        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(larvae[0] != null && !larvae[0].isAlive(), "the unfed larva has not starved yet");
            helper.assertTrue(larvae[1].isAlive(), "the larva that was just fed starved");
            helper.assertTrue(!larvae[2].isAlive() || larvae[2].isFull(), "the full larva is not full");
            // The body lies where the larva died, or the worker has already picked it up to take it to the refuse
            // chamber (in full runs it sometimes got there first, and a carried body is not one lying about).
            int carried = level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds(),
                    worker -> colony.getId().equals(worker.getColonyId()) && worker.isCarryingCorpse()).size();
            int bodies = colony.refuse().getCorpses(level).size() + carried;
            helper.assertTrue(bodies == 1, bodies + " bodies (" + carried + " carried), expected 1");
        });
    }

    /**
     * A queen who gets no food for five days starves. Her colony carries on without her, queenless (no new eggs), until
     * its last worker dies of old age: then it ends, and the nest itself stays behind.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aQueenThatIsNotFedForFiveDaysStarvesAndHerColonyDwindles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startColony(helper, 1);

        com.antcolonies.entity.AbstractAntEntity[] queen = {null};
        Colony[] colony = {null};
        int[] phase = {0};
        helper.runAfterDelay(30, () -> {
            colony[0] = colony(helper);
            helper.assertTrue(colony[0] != null, "no colony");
            colony[0].setHungerOn(true);
            queen[0] = colony[0].getQueen(level);
            colony[0].queenCare().setQueenLastFed(level.getGameTime() - colony[0].profile().queen().starveTicks() + 60); // three seconds from starving
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            if (phase[0] == 1 && !queen[0].isAlive()) {
                ColonyManager manager = ColonyManager.get(level);
                helper.assertTrue(manager.getColony(colony[0].getId()) == colony[0] && colony[0].isQueenless(),
                        "the colony should carry on without its queen");
                List<WorkerAntEntity> workers = level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds(),
                        worker -> colony[0].getId().equals(worker.getColonyId()));
                helper.assertTrue(workers.size() == 1, "expected the one worker, found " + workers.size());
                // No young left to grow up (this test is about the workers), and the worker is at the end of its life.
                colony[0].brood().getEggs(level).forEach(net.minecraft.world.entity.Entity::discard);
                colony[0].brood().getLarvae(level).forEach(net.minecraft.world.entity.Entity::discard);
                workers.get(0).setGrownUpAt(level.getGameTime() - colony[0].profile().worker().lifespanTicks() * 2L);
                phase[0] = 2;
            } else if (phase[0] == 2 && ColonyManager.get(level).getColony(colony[0].getId()) == null) {
                helper.succeed(); // the last worker died of old age: the colony has died out
            }
        });
    }

    /**
     * After a save and reload the colony keeps the nest it was digging: with seeds lying in the seed store and bread in
     * the nursery, the reloaded colony plans exactly the same rooms (it must not start a new nest in another direction).
     */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void theNestPlanIsTheSameAfterAReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startColony(helper, 0);

        helper.runAfterDelay(20, () -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no colony");
            List<NestPlan.Chamber> before = colony.nest().getPlannedChambers(level);
            helper.assertTrue(before.size() == NestPlan.MAX_LEVELS, "the nest has " + before.size() + " levels, expected " + NestPlan.MAX_LEVELS);
            digNestAtOnce(level, colony);
            // A heap of seeds in the far corner of the seed store, a heap of bread in the far corner of the nursery.
            for (NestPlan.Chamber chamber : before) {
                BlockPos corner = chamber.room().get(chamber.room().size() - 1);
                if (chamber.kind() == NestPlan.Kind.FOOD) {
                    level.setBlock(corner, ModBlocks.SEED_PILE.get().defaultBlockState(), 3);
                } else if (chamber.kind() == NestPlan.Kind.NURSERY) {
                    level.setBlock(corner, ModBlocks.BREAD_PILE.get().defaultBlockState(), 3);
                }
            }
            // An old save has no nest plan stored: worked out again from the ground, the heaps count as dug out.
            var oldSave = colony.save();
            oldSave.remove("nestDepth");
            oldSave.remove("nestDirection");
            oldSave.remove("nestLevels");
            oldSave.remove("nestFounding");
            List<NestPlan.Chamber> fromOldSave = Colony.load(oldSave).nest().getPlannedChambers(level);
            helper.assertTrue(fromOldSave.equals(before), "from an old save the nest plan changed: " + describePlan(before, colony)
                    + " became " + describePlan(fromOldSave, colony));
            // A torch a player put on the floor of the refuse chamber: only a saved plan gets past that.
            BlockPos torch = before.get(before.size() - 1).room().get(0);
            level.setBlock(torch, Blocks.TORCH.defaultBlockState(), 3);
            // A reload: the colony is written to the save and read back as a new object.
            Colony reloaded = Colony.load(colony.save());
            List<NestPlan.Chamber> after = reloaded.nest().getPlannedChambers(level);
            helper.assertTrue(after.equals(before), "after the reload the nest plan changed: " + describePlan(before, colony)
                    + " became " + describePlan(after, colony));
            helper.assertTrue(colony.nest().getFoundingChamber(level) != null
                    && colony.nest().getFoundingChamber(level).equals(reloaded.nest().getFoundingChamber(level)),
                    "the founding chamber changed after the reload");
            helper.succeed();
        });
    }

    /**
     * What an ant holds in its mandibles survives a save and reload: soil, a seed for the mill, bread, an egg and a dead
     * ant. And a worker that was reloaded while carrying an egg still takes it to the nursery afterwards.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void whatAnAntCarriesIsKeptAfterAReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startColony(helper, 1);

        WorkerAntEntity[] reloaded = {null};
        helper.runAfterDelay(20, () -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no colony");
            WorkerAntEntity worker = colonyWorkers(helper, colony).get(0);

            // Every kind of load, one at a time, written to the save and read back into a new ant.
            worker.setCarriedBlock(Blocks.COARSE_DIRT.defaultBlockState());
            helper.assertTrue(Blocks.COARSE_DIRT.defaultBlockState().equals(reload(level, worker).getCarriedBlock()), "the soil pellet was lost");
            worker.setCarriedBlock(null);
            worker.setCarriedSeed(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PUMPKIN_SEEDS));
            worker.setSeedForBread(true);
            WorkerAntEntity copy = reload(level, worker);
            helper.assertTrue(copy.getCarriedSeed().is(net.minecraft.world.item.Items.PUMPKIN_SEEDS) && copy.isSeedForBread(), "the seed for the mill was lost");
            worker.setSeedForBread(false);
            worker.setCarriedSeed(new net.minecraft.world.item.ItemStack(com.antcolonies.registry.ModItems.RICH_ANT_BREAD.get()));
            helper.assertTrue(reload(level, worker).isCarryingRichBread(), "the rich bread was lost");
            worker.setCarriedSeed(net.minecraft.world.item.ItemStack.EMPTY);
            worker.setCarryingCorpse(true);
            helper.assertTrue(reload(level, worker).isCarryingCorpse(), "the dead ant was lost");
            worker.setCarryingCorpse(false);
            helper.assertTrue(!reload(level, worker).hasFullMandibles(), "an ant with empty mandibles came back holding something");

            // The real thing: the worker is reloaded while it carries an egg, in a dug-out nest.
            digNestAtOnce(level, colony);
            worker.setCarryingEgg(true);
            reloaded[0] = reload(level, worker);
            reloaded[0].setUUID(java.util.UUID.randomUUID());
            worker.discard();
            level.addFreshEntity(reloaded[0]);
        });
        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(reloaded[0] != null, "not reloaded yet");
            helper.assertTrue(!reloaded[0].isCarryingEgg(), "the reloaded worker still holds the egg");
            helper.assertTrue(colony.brood().getEggs(level).size() == 1, colony.brood().getEggs(level).size() + " eggs, expected 1");
            helper.assertTrue(colony.brood().getNurseryEggs(level).size() == 1, "the egg is not in the nursery");
        });
    }

    /** A new worker read back from this worker's save, as after a reload (not put into the world). */
    private static WorkerAntEntity reload(ServerLevel level, WorkerAntEntity worker) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        worker.saveWithoutId(tag);
        WorkerAntEntity copy = ModEntities.WORKER_ANT.get().create(level);
        copy.load(tag);
        return copy;
    }

    /** Digs the colony's whole planned nest (shaft, stairs, tunnels, rooms) out at once. */
    private static void digNestAtOnce(ServerLevel level, Colony colony) {
        for (int i = 1; i <= Nest.SHAFT_DEPTH; i++) {
            level.setBlock(colony.getCenter().below(i), Blocks.AIR.defaultBlockState(), 3);
        }
        List<NestPlan.Chamber> all = new java.util.ArrayList<>(colony.nest().getPlannedChambers(level));
        if (colony.nest().getFoundingChamber(level) != null) {
            all.add(colony.nest().getFoundingChamber(level));
        }
        for (NestPlan.Chamber chamber : all) {
            for (BlockPos pos : chamber.cells()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** The plan in a few words for the log: how many levels and which way the first tunnel leaves the shaft. */
    private static String describePlan(List<NestPlan.Chamber> plan, Colony colony) {
        if (plan.isEmpty()) {
            return "no plan";
        }
        BlockPos room = plan.get(0).center();
        return plan.size() + " levels, rooms toward " + Direction.getNearest(room.getX() - colony.getCenter().getX(), 0,
                room.getZ() - colony.getCenter().getZ());
    }

    /**
     * Dead ants are carried down to the refuse chamber: three bodies lying on the ground around the hill, one lying in
     * the nursery, and a worker that is killed (it leaves its body behind where it died). Every body ends up in the
     * refuse chamber, one to a cell, and none is left lying about or in an ant's mandibles.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void workersCarryTheDeadToTheRefuseChamber(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] placed = {false};
        int[] workersBefore = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (placed[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            placed[0] = true;
            for (int[] spot : new int[][] {{5, 1}, {-4, 4}, {1, -6}}) {
                BlockPos pos = helper.absolutePos(new BlockPos(MIDDLE + spot[0], SURFACE, MIDDLE + spot[1]));
                colony.refuse().spawnCorpse(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }
            BlockPos nursery = colony.nest().getNurseryCells(level).get(3);
            colony.refuse().spawnCorpse(level, nursery.getX() + 0.5, nursery.getY(), nursery.getZ() + 0.5);
            var workers = colonyWorkers(helper, colony);
            workersBefore[0] = workers.size();
            workers.get(0).hurt(level.damageSources().genericKill(), 1000.0F);
            AntColoniesMod.LOGGER.info("[refuse-test] tick {}: 4 bodies put out, one worker killed ({} workers before)", helper.getTick(), workersBefore[0]);
        });
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (placed[0] && colony != null && helper.getTick() % 500 == 0) {
                AntColoniesMod.LOGGER.info("[refuse-test] tick {}: {} bodies, {} in the refuse chamber ({} cells)", helper.getTick(),
                        colony.refuse().getCorpses(level).size(), colony.refuse().getRefuseCorpses(level).size(), colony.nest().getRefuseCells(level).size());
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(placed[0] && colony != null, "no dead ants have been put out yet");
            var all = colony.refuse().getCorpses(level);
            helper.assertTrue(all.size() == 5, "expected 5 bodies (4 put out and the killed worker), found " + all.size());
            var inChamber = colony.refuse().getRefuseCorpses(level);
            helper.assertTrue(inChamber.size() == 5, inChamber.size() + " of 5 bodies are in the refuse chamber");
            Set<BlockPos> cells = new HashSet<>();
            for (var corpse : inChamber) {
                cells.add(corpse.blockPosition());
                helper.assertTrue(corpse.isPlaced(), "a body in the refuse chamber is not marked as put there");
            }
            helper.assertTrue(cells.size() == 5, "two bodies lie in the same cell");
            var workers = colonyWorkers(helper, colony);
            helper.assertTrue(workers.size() == workersBefore[0] - 1, "expected " + (workersBefore[0] - 1) + " workers but found " + workers.size());
            for (var worker : workers) {
                helper.assertTrue(!worker.isCarryingCorpse(), "an ant is still carrying a body");
            }
        });
    }

    /**
     * Where the soil is so thin that the nest has no refuse chamber, nobody can carry a dead ant anywhere: it stays where
     * it lies for the five minutes of the species profile's corpse_decay_ticks and then crumbles to dust.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void deadAntsNobodyCanCarryAwayCrumbleToDust(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);
        int stoneTop = SURFACE - Nest.SHAFT_DEPTH - NestPlan.LEVEL_SPACING + 1; // only the queen's room fits (see theNestHasFewerLevelsWhenTheSoilIsThin)
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y <= stoneTop; y++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }

        com.antcolonies.entity.AntCorpseEntity[] corpse = {null};
        helper.runAfterDelay(20, () -> {
            BlockPos pos = helper.absolutePos(new BlockPos(MIDDLE + 5, SURFACE, MIDDLE + 5));
            corpse[0] = colony(helper).refuse().spawnCorpse(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(corpse[0] != null && !corpse[0].isAlive(), "the body is still there");
            if (corpse[0].tickCount < colony(helper).profile().refuse().corpseDecayTicks()) {
                helper.fail("the body was gone after " + corpse[0].tickCount + " ticks, but nobody could have carried it away");
            }
        });
    }

    /**
     * The seeds the colony can account for: in the store, milled into bread (waiting in the nursery or eaten by the queen
     * for her next egg), or used for the eggs she has laid. A seed on its way or in the mill is not counted yet.
     */
    /** The real workers of this colony: also the ones that have wandered a little outside the test area. */
    private static List<WorkerAntEntity> colonyWorkers(GameTestHelper helper, Colony colony) {
        return helper.getLevel().getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds().inflate(20.0)).stream()
                .filter(worker -> colony.getId().equals(worker.getColonyId())).toList();
    }

    /**
     * Debug aid: logs every moment an ant that is on its way somewhere has not moved for 10 ticks (where it is, what it
     * carries, which waypoint it is heading for), and when it moves again.
     */
    private static void logStalls(GameTestHelper helper) {
        java.util.Map<Integer, Vec3> lastPos = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> still = new java.util.HashMap<>();
        helper.onEachTick(() -> {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            for (AbstractAntEntity ant : helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds())) {
                var nav = ant.getNavigator();
                Vec3 before = lastPos.put(ant.getId(), ant.position());
                if (nav.isDone() || before == null) {
                    still.put(ant.getId(), 0);
                    continue;
                }
                int count = ant.position().distanceToSqr(before) < 0.004 * 0.004 ? still.merge(ant.getId(), 1, Integer::sum) : 0;
                still.put(ant.getId(), count);
                if (count == 10) {
                    var route = nav.getRoute();
                    String next = route == null || nav.getNextWaypoint() >= route.size() ? "-"
                            : route.get(nav.getNextWaypoint()).subtract(origin).toShortString();
                    String goal = route == null ? "-" : route.get(route.size() - 1).subtract(origin).toShortString();
                    String from = route == null || nav.getNextWaypoint() < 1 ? "-" : route.get(nav.getNextWaypoint() - 1).subtract(origin).toShortString();
                    String whole = route == null ? "-" : route.stream().map(c -> c.subtract(origin).toShortString().replace(", ", ",")).toList().toString();
                    AntColoniesMod.LOGGER.info("[stall] from {} route {}", from, whole);
                    AntColoniesMod.LOGGER.info("[stall] tick {} ant #{} at {} ({},{},{}) next {} goal {} climbing={} onGround={} carrying={} collided={}",
                            helper.getTick(), ant.getId(), ant.blockPosition().subtract(origin).toShortString(),
                            String.format("%.2f", ant.getX() - origin.getX()), String.format("%.2f", ant.getY() - origin.getY()),
                            String.format("%.2f", ant.getZ() - origin.getZ()), next, goal, nav.isClimbing(), ant.onGround(),
                            ant.isCarrying(), ant.horizontalCollision);
                }
            }
        });
    }

    /** Every ant in the test area that holds a seed or bread, with where it is: to find a seed that went missing. */
    private static String carriers(GameTestHelper helper) {
        List<String> out = new java.util.ArrayList<>();
        for (AbstractAntEntity ant : helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds().inflate(64))) {
            if (!ant.getCarriedSeed().isEmpty()) {
                out.add("#" + ant.getId() + " " + ant.getCarriedSeed().getItem() + " at "
                        + ant.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()
                        + (ant.isClimbing() ? " on " + ant.getSurface() : "") + (ant.getNavigator().isDone() ? " standing" : " walking"));
            }
        }
        for (var item : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(64))) {
            out.add("an item " + item.getItem() + " lying at " + item.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
        }
        return out.isEmpty() ? "nobody" : String.join(", ", out);
    }

    private static int seedsAccountedFor(Colony colony) {
        return colony.food().getFood() + colony.bakery().getBread() + colony.queenCare().getQueenBread() + colony.profile().queen().meal() * colony.queenCare().getEggsLaid() + colony.food().getSeedsEaten();
    }

    private static void dropSeeds(GameTestHelper helper, int x, int z, int count) {
        BlockPos at = helper.absolutePos(new BlockPos(x, SURFACE, z));
        var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, count));
        item.setDeltaMovement(0, 0, 0);
        item.setUnlimitedLifetime(); // the seeds must not despawn (5 minutes) while the slow ants take their time
        helper.getLevel().addFreshEntity(item);
    }

    /**
     * The queen lives in her founding chamber until her own deep room is dug (here it never is: it is turned to stone).
     * Seeds lie on the ground; the workers store them in the seed store, the nurse mills bread in the nursery and feeds
     * her 20 pieces up in the founding chamber, and her egg is carried down to the nursery. Every seed is accounted for.
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void queenLivesInTheFoundingChamberUntilHerOwnRoomIsDug(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] blocked = {false};
        boolean[] dropped = {false};
        boolean[] fedAtHome = {false};
        boolean[] eggInNursery = {false};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null) {
                return;
            }
            var rooms = colony.nest().getPlannedChambers(level);
            if (!blocked[0] && !rooms.isEmpty()) {
                blocked[0] = true;
                for (BlockPos pos : rooms.get(2).cells()) { // the stair and the queen's room: nothing deeper can be dug
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                }
            }
            if (!dropped[0] && colony.nest().getFinishedRoomCenter(level) != null) {
                dropped[0] = true;
                dropSeeds(helper, MIDDLE + 6, MIDDLE, 10);
                dropSeeds(helper, MIDDLE - 6, MIDDLE + 2, 10);
                dropSeeds(helper, MIDDLE + 1, MIDDLE - 7, 10);
                AntColoniesMod.LOGGER.info("[early-feed-test] tick {}: the founding chamber is finished, 30 seeds dropped", helper.getTick());
            }
            if (dropped[0] && colony.queenCare().getQueenBread() > 0 && colony.nest().getQueenRoomCells(level).isEmpty()) {
                fedAtHome[0] = true;
            }
            if (dropped[0] && colony.queenCare().getEggsLaid() > 0) {
                for (var egg : colony.brood().getEggs(level)) {
                    eggInNursery[0] |= colony.nest().getNurseryCells(level).contains(egg.blockPosition());
                }
                for (var larva : colony.brood().getLarvae(level)) {
                    eggInNursery[0] |= colony.nest().getNurseryCells(level).contains(larva.blockPosition());
                }
            }
            if (dropped[0] && helper.getTick() % 400 == 0) {
                AntColoniesMod.LOGGER.info("[early-feed-test] tick {}: queen fed {}, bread {} (milling {}), food {}, eggs laid {}, eggs {}, larvae {}, seeds on the ground {}",
                        helper.getTick(), colony.queenCare().getQueenBread(), colony.bakery().getBread(), colony.bakery().getMilling(), colony.food().getFood(), colony.queenCare().getEggsLaid(),
                        colony.brood().countEggs(level), colony.brood().countLarvae(level), seedsIn(level, helper.getBounds()) - colony.food().getFood());
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(dropped[0] && colony != null, "no seeds have been dropped yet");
            helper.assertTrue(fedAtHome[0], "the queen was not fed in her founding chamber");
            helper.assertTrue(colony.queenCare().getEggsLaid() >= 1 && eggInNursery[0], "no egg has been put down in the nursery yet");
            helper.assertTrue(colony.nest().getFinishedRoomCenter(level).equals(colony.nest().getFoundingChamber(level).center()),
                    "the queen's home is not the founding chamber");
            helper.assertTrue(colony.getQueen(level).blockPosition().equals(colony.nest().getFoundingChamber(level).center()),
                    "the queen is not in her founding chamber");
            for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds())) {
                helper.assertTrue(!ant.isCarryingSeed(), "an ant is still carrying a seed");
            }
            helper.assertTrue(seedsIn(level, helper.getBounds()) == colony.food().getFood(), "some seeds are still lying around outside the store");
        });
    }

    /**
     * A nursery that cannot be dug (turned to stone) does not stop the colony: the egg is set down on the floor of the
     * queen's room by a worker, and it hatches there into a larva.
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void eggsGoToTheQueensRoomWhenThereIsNoNursery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        boolean[] ready = {false};
        int[] workersBefore = {0};
        int[] antsBefore = {0};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (ready[0] || colony == null || airBelowSurface(helper) != NEST_CELLS || colony.nest().findDigTarget(level) != null
                    || !hillProblems(helper).isEmpty()) {
                return;
            }
            ready[0] = true;
            antsBefore[0] = level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds()).size();
            for (WorkerAntEntity w : level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds().inflate(60))) {
                AntColoniesMod.LOGGER.info("[room-egg-test] worker {} at {} colony {} (ours {}) alive {}", w.getId(), w.blockPosition(),
                        w.getColonyId(), colony.getId(), w.isAlive());
            }
            for (var chamber : colony.nest().getPlannedChambers(level)) {
                if (chamber.kind() == com.antcolonies.colony.NestPlan.Kind.NURSERY) {
                    for (BlockPos pos : chamber.room()) { // not the stairs: they lead on to the queen's room
                        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
            workersBefore[0] = colony.getCount(WorkerAntEntity.CASTE);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            colony.queenCare().layEgg(level, room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            AntColoniesMod.LOGGER.info("[room-egg-test] tick {}: nursery turned to stone, egg laid, {} workers counted, {} real", helper.getTick(), workersBefore[0], antsBefore[0]);
        });
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (ready[0] && colony != null && helper.getTick() % 400 == 0) {
                var eggs = colony.brood().getEggs(level);
                AntColoniesMod.LOGGER.info("[room-egg-test] tick {}: {} eggs (placed {}), {} workers", helper.getTick(), eggs.size(),
                        eggs.stream().filter(com.antcolonies.entity.AntEggEntity::isPlaced).count(), colony.getCount(WorkerAntEntity.CASTE));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(ready[0] && colony != null, "the egg has not been laid yet");
            helper.assertTrue(colony.nest().getNurseryCells(level).isEmpty(), "the nursery is usable after all");
            helper.assertTrue(colony.brood().countEggs(level) == 0, "an egg is left");
            helper.assertTrue(colony.brood().countLarvae(level) == 1, colony.brood().countLarvae(level) + " larvae, expected 1");
            helper.assertTrue(colony.nest().getQueenRoomCells(level).contains(colony.brood().getLarvae(level).get(0).blockPosition()),
                    "the larva is not in the queen's room");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == workersBefore[0], "the colony has a new worker already");
        });
    }

    /**
     * With six workers the colony hands out one nurse, three builders and two foragers. Once the nest is finished the nurse
     * waits next to the queen.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void workersShareTheJobsAndTheNurseStaysByTheQueen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && airBelowSurface(helper) == NEST_CELLS && colony.nest().findDigTarget(level) == null
                    && hillProblems(helper).isEmpty(), "the nest is not finished yet");
            var queens = level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds());
            helper.assertTrue(queens.size() == 1, "no queen");
            int[] roles = new int[com.antcolonies.colony.ColonyRole.values().length];
            int nursesNearQueen = 0;
            for (WorkerAntEntity worker : level.getEntitiesOfClass(WorkerAntEntity.class, helper.getBounds())) {
                var role = colony.roleOf(level, worker.getUUID());
                roles[role.ordinal()]++;
                double dx = worker.getX() - queens.get(0).getX();
                double dz = worker.getZ() - queens.get(0).getZ();
                if (role == com.antcolonies.colony.ColonyRole.NURSE && dx * dx + dz * dz <= 3.0 * 3.0) {
                    nursesNearQueen++;
                }
            }
            helper.assertTrue(roles[com.antcolonies.colony.ColonyRole.NURSE.ordinal()] == 1
                    && roles[com.antcolonies.colony.ColonyRole.BUILDER.ordinal()] == 3
                    && roles[com.antcolonies.colony.ColonyRole.FORAGER.ordinal()] == 2,
                    "roles (builder, forager, nurse): " + java.util.Arrays.toString(roles) + ", expected [3, 2, 1]");
            helper.assertTrue(nursesNearQueen == 1, "the nurse is not next to the queen");
        });
    }

    /** How many pieces of bread lie in the box (every layer of a bread pile block counts one). */
    private static int breadIn(ServerLevel level, AABB box) {
        int bread = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX + 0.01, box.minY + 0.01, box.minZ + 0.01),
                BlockPos.containing(box.maxX - 0.01, box.maxY - 0.01, box.maxZ - 0.01))) {
            var state = level.getBlockState(pos);
            if (state.is(ModBlocks.BREAD_PILE.get())) {
                bread += state.getValue(com.antcolonies.block.SeedPileBlock.LAYERS);
            }
        }
        return bread;
    }

    /** A box around the floor cells of the colony's food store. */
    private static AABB storeBox(ServerLevel level, Colony colony) {
        AABB box = null;
        for (BlockPos pos : colony.nest().getFoodStoreCells(level)) {
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
        startColony(helper, workers, true);
    }

    /** {@code unlockAllLevels}: dig the whole nest at once, whatever the colony's size (false: nest stages as in the game). */
    private static void startColony(GameTestHelper helper, int workers, boolean unlockAllLevels) {
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
        // The tests run far faster than a day: the workers of their colonies do not get hungry (the hunger tests turn it back on).
        // And they dig the whole nest at once, whatever the colony's size (nest stages have their own test).
        helper.runAfterDelay(10, () -> {
            Colony colony = colony(helper);
            if (colony != null) {
                colony.setHungerOn(false);
                if (unlockAllLevels) {
                    colony.nest().unlockAllLevelsForTests();
                }
            }
        });
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
        for (int i = 1; i <= Nest.SHAFT_DEPTH; i++) {
            planned.add(colony.getCenter().below(i));
        }
        for (var chamber : colony.nest().getPlannedChambers(level)) {
            planned.addAll(chamber.cells());
        }
        if (colony.nest().getFoundingChamber(level) != null) {
            planned.addAll(colony.nest().getFoundingChamber(level).cells());
        }
        int ceiling = colony.getCenter().getY() - 1; // the shaft's top block: nothing above it counts
        AABB area = helper.getBounds();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = colony.getCenter().below(Nest.SHAFT_DEPTH);
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
                int planned = Hill.moundHeightAt(dx, dz);
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
        // A colony left over from an earlier test in this spot has no living queen (the test cleanup removed her): skip it.
        return ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(colony -> area.contains(Vec3.atCenterOf(colony.getCenter())) && colony.getQueen(helper.getLevel()) != null)
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
            BlockPos spot = colony.hill().pickMoundSpot(level, level.random, null);
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
                    .append(" target ").append(colony.nest().findDigTarget(level) == null ? "none" : colony.nest().findDigTarget(level).toShortString())
                    .append("]; ");
        }
        text.append("next target ")
                .append(colony == null || colony.nest().findDigTarget(level) == null
                        ? "none" : colony.nest().findDigTarget(level).subtract(BlockPos.containing(origin)).toShortString())
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
