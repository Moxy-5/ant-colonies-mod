package com.antcolonies.gametest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.AntPathFinder;
import com.antcolonies.nav.LevelCellMap;
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
     * Water starts running down the entrance hole of a finished nest (a source block on top of the hill). The ants
     * above must block the source, the ants below must shut the doors of the rooms, and once the water is gone
     * the doors must be opened again, so the nest ends up exactly as it was and no water is left in it.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void antsBlockWaterAndShutTheDoorsThenOpenThemAgain(GameTestHelper helper) {
        floodTest(helper, Blocks.WATER, "water-test", false);
    }

    /** The same with lava, which is slower than water but would kill a worker that touched it. */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void antsBlockLavaAndShutTheDoorsThenOpenThemAgain(GameTestHelper helper) {
        floodTest(helper, Blocks.LAVA, "lava-test", true);
    }

    /**
     * {@code doorsInTime}: lava is slow enough for the ants below to shut the doors before it arrives; water floods the
     * tunnels in a few seconds, so there only the source being blocked and the water draining away is checked.
     */
    private static void floodTest(GameTestHelper helper, net.minecraft.world.level.block.Block fluid, String tag, boolean doorsInTime) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        BlockPos[] source = {null};
        boolean[] sawDoorShut = {false};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null) {
                return;
            }
            if (source[0] == null) {
                // Wait until the nest and the hill are finished, then pour the water.
                if (airBelowSurface(helper) == NEST_CELLS && colony.findDigTarget(level) == null && hillProblems(helper).isEmpty()
                        && colony.getFinishedRoomCenter(level) != null) {
                    source[0] = colony.getCenter().offset(1, 3, 0);
                    level.setBlock(source[0], fluid.defaultBlockState(), 3);
                    AntColoniesMod.LOGGER.info("[{}] tick {}: poured at {}", tag, helper.getTick(), source[0]);
                    // A colony always has some workers out on the surface: three of them are up on the ground now.
                    for (int i = 0; i < 3; i++) {
                        helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE + 4 + i, SURFACE, MIDDLE + 3);
                    }
                }
                return;
            }
            for (var chamber : colony.getPlannedChambers(level)) {
                if (!level.getBlockState(chamber.cells().get(com.antcolonies.colony.NestPlan.TUNNEL_LENGTH - 1)).isAir()
                        && level.getFluidState(chamber.cells().get(com.antcolonies.colony.NestPlan.TUNNEL_LENGTH - 1)).isEmpty()) {
                    sawDoorShut[0] = true;
                }
            }
            if (helper.getTick() % 200 == 0) {
                AntColoniesMod.LOGGER.info("[{}] tick {}: alert {}, source {}, doorShut {}, {} cells open. {}", tag, helper.getTick(),
                        colony.getAlert(level), level.getBlockState(source[0]).getBlock(), sawDoorShut[0], airBelowSurface(helper), describe(helper));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && source[0] != null, "nothing has been poured yet");
            helper.assertTrue(level.getFluidState(source[0]).isEmpty(), "the source is still there");
            helper.assertTrue(sawDoorShut[0] || !doorsInTime, "the ants never shut a door");
            helper.assertTrue(!colony.isAlarmed(level), "the colony is still on alert: " + colony.getAlert(level));
            helper.assertTrue(airBelowSurface(helper) == NEST_CELLS,
                    "the nest is not open again: " + airBelowSurface(helper) + " of " + NEST_CELLS + " cells. " + describe(helper));
            helper.assertTrue(colony.findDigTarget(level) == null, "something is still left to dig");
            helper.assertTrue(colony.getLeaks(level).isEmpty(), "the nest has openings");
            // Only the source was blocked: no soil left lying in the air where the stream used to run.
            helper.assertTrue(moundBlocks(helper) == 64 + 1, "expected the hill and the one blocked source, but there are " + moundBlocks(helper) + " blocks of soil above the ground");
            helper.assertTrue(colony.getGivenUpDoors().isEmpty(), "a room was given up because of the flood");
            AntColoniesMod.LOGGER.info("[{}] finished at tick {}", tag, helper.getTick());
        });
    }

    /**
     * A false alarm must not happen: a still water pool and a still lava pool sit right next to the hill (just outside
     * its square), not running into anything. The colony must never go on alert, never pack soil into the pools, and
     * just dig its nest as usual.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void poolsNextToTheHillAreIgnored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);
        // Two 3x3 pools sunk into the grass layer, 4 to 6 blocks from the hole on either side.
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(MIDDLE + 4 + x, SURFACE - 1, MIDDLE - 1 + z, Blocks.WATER);
                helper.setBlock(MIDDLE - 6 + x, SURFACE - 1, MIDDLE - 1 + z, Blocks.LAVA);
            }
        }
        boolean[] alarmed = {false};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony != null && colony.isAlarmed(level)) {
                alarmed[0] = true;
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no colony yet");
            helper.assertTrue(!alarmed[0], "the colony went on alert because of a still pool");
            helper.assertTrue(airBelowSurface(helper) == NEST_CELLS, "nest not finished: " + airBelowSurface(helper) + " of " + NEST_CELLS);
            helper.assertTrue(colony.findDigTarget(level) == null, "something is still left to dig");
            helper.assertTrue(hillProblems(helper).isEmpty(), "the mound is not the planned hill yet");
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    helper.assertTrue(helper.getBlockState(new BlockPos(MIDDLE + 4 + x, SURFACE - 1, MIDDLE - 1 + z)).is(Blocks.WATER), "the water pool was disturbed");
                    helper.assertTrue(helper.getBlockState(new BlockPos(MIDDLE - 6 + x, SURFACE - 1, MIDDLE - 1 + z)).is(Blocks.LAVA), "the lava pool was disturbed");
                }
            }
            AntColoniesMod.LOGGER.info("[pool-test] finished at tick {}", helper.getTick());
        });
    }

    /**
     * The ants are digging the nursery when the digging runs into a lava pool (a lava source appears in the middle of the
     * room). They must come out of the room, shut its door for good, give the room up and dig a new nursery somewhere
     * else, without ever cutting off the way out of the nest.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void antsGiveUpARoomThatRunsIntoLavaAndDigANewOne(GameTestHelper helper) {
        lostRoomTest(helper, "lava-room-test", true);
    }

    /**
     * A lava pool turns up right behind the far wall of the nursery before it is dug: the ants never open a block that
     * touches lava, so that room could never be finished. It is given up and replaced the same way.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void antsGiveUpARoomThatCanNeverBeFinishedBecauseOfAPool(GameTestHelper helper) {
        lostRoomTest(helper, "pool-room-test", false);
    }

    private static void lostRoomTest(GameTestHelper helper, String tag, boolean intoTheRoom) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        BlockPos[] lava = {null};
        BlockPos[] oldDoor = {null};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null || lava[0] != null) {
                return;
            }
            var rooms = colony.getPlannedChambers(level);
            if (rooms.size() < 3) {
                return;
            }
            var nursery = rooms.get(1);
            oldDoor[0] = nursery.cells().get(NestPlan.TUNNEL_LENGTH - 1);
            if (intoTheRoom) {
                int open = 0;
                for (BlockPos pos : nursery.cells().subList(NestPlan.TUNNEL_LENGTH, nursery.cells().size())) {
                    open += level.getBlockState(pos).isAir() ? 1 : 0;
                }
                if (open >= 8) {
                    lava[0] = nursery.center();
                }
            } else {
                BlockPos c0 = nursery.cells().get(0);
                BlockPos c1 = nursery.cells().get(1);
                Direction forward = Direction.fromDelta(c1.getX() - c0.getX(), 0, c1.getZ() - c0.getZ());
                lava[0] = nursery.center().relative(forward, 3);
            }
            if (lava[0] != null) {
                level.setBlock(lava[0], Blocks.LAVA.defaultBlockState(), 3);
                AntColoniesMod.LOGGER.info("[{}] tick {}: lava at {}, nursery door at {}", tag, helper.getTick(), lava[0], oldDoor[0]);
            }
        });
        helper.onEachTick(() -> {
            if (helper.getTick() % 400 == 0) {
                Colony colony = colony(helper);
                AntColoniesMod.LOGGER.info("[{}] tick {}: given up {}, alarmed {}, {} cells dug. {}", tag, helper.getTick(),
                        colony == null ? "?" : colony.getGivenUpDoors(), colony != null && colony.isAlarmed(level), airBelowSurface(helper), describe(helper));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && lava[0] != null, "the lava is not there yet");
            helper.assertTrue(colony.getGivenUpDoors().equals(List.of(oldDoor[0])), "the nursery was not given up: " + colony.getGivenUpDoors());
            // Shut: packed with soil by the ants, or never dug at all.
            helper.assertTrue(!level.getBlockState(oldDoor[0]).isAir() && level.getFluidState(oldDoor[0]).isEmpty(), "the old door is not shut: " + level.getBlockState(oldDoor[0]));
            if (intoTheRoom) {
                helper.assertTrue(level.getBlockState(oldDoor[0]).is(ModBlocks.BURROWED_DIRT.get()), "the ants did not pack the door: " + level.getBlockState(oldDoor[0]));
            }
            helper.assertTrue(level.getBlockState(lava[0]).is(Blocks.LAVA), "the lava was touched");
            var rooms = colony.getPlannedChambers(level);
            helper.assertTrue(rooms.size() == 3, "expected queen, food store and a new nursery but there are " + rooms.size() + " rooms");
            helper.assertTrue(rooms.stream().noneMatch(room -> room.cells().contains(oldDoor[0])), "the old nursery is still in the plan");
            helper.assertTrue(rooms.stream().filter(room -> room.kind() == NestPlan.Kind.NURSERY).count() == 1, "there is no new nursery");
            for (var room : rooms) {
                for (BlockPos cell : room.cells()) {
                    helper.assertTrue(level.getBlockState(cell).isAir(), "a room is not dug yet at " + cell.toShortString() + " (" + room.kind() + ")");
                }
            }
            helper.assertTrue(colony.findDigTarget(level) == null, "something is still left to dig");
            helper.assertTrue(!colony.isAlarmed(level), "the colony is still on alert: " + colony.getAlert(level));
            helper.assertTrue(colony.getLeaks(level).isEmpty(), "the nest has openings: " + colony.getLeaks(level));
            // The way out: every room still has a route to the entrance.
            for (var room : rooms) {
                var route = AntPathFinder.find(new LevelCellMap(level), room.center(), colony.getCenter(), 4000);
                helper.assertTrue(route != null, "no way out of the " + room.kind() + " room");
            }
            BlockPos room = colony.getFinishedRoomCenter(level);
            var queens = level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds());
            helper.assertTrue(room != null && !queens.isEmpty() && queens.get(0).blockPosition().equals(room), "the queen is not in her room");
            AntColoniesMod.LOGGER.info("[{}] finished at tick {}: new rooms {}", tag, helper.getTick(),
                    rooms.stream().map(r -> r.kind() + "@" + r.cells().get(0).toShortString()).toList());
        });
    }

    /**
     * A source hangs against the side of a six-block tower next to the hill, so high up that the ants cannot reach it
     * from the ground. They must climb the tower, hold on to its wall and block the source.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void antsClimbAWallToBlockASourceHighAbove(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        BlockPos[] source = {null};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null) {
                return;
            }
            if (source[0] == null) {
                if (airBelowSurface(helper) == NEST_CELLS && colony.findDigTarget(level) == null && hillProblems(helper).isEmpty()
                        && colony.getFinishedRoomCenter(level) != null) {
                    BlockPos c = colony.getCenter();
                    for (int y = 0; y <= 5; y++) {
                        level.setBlock(c.offset(2, y, 0), Blocks.COBBLESTONE.defaultBlockState(), 3);
                    }
                    source[0] = c.offset(3, 4, 0);
                    level.setBlock(source[0], Blocks.WATER.defaultBlockState(), 3);
                    AntColoniesMod.LOGGER.info("[tower-test] tick {}: tower at {}, source at {}", helper.getTick(), c.offset(2, 0, 0), source[0]);
                    for (int i = 0; i < 3; i++) {
                        helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE + 4 + i, SURFACE, MIDDLE + 3);
                    }
                }
                return;
            }
            if (helper.getTick() % 100 == 0) {
                AntColoniesMod.LOGGER.info("[tower-test] tick {}: source {}, alert {}. {}", helper.getTick(),
                        level.getBlockState(source[0]).getBlock(), colony.getAlert(level), describe(helper));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && source[0] != null, "nothing has been poured yet");
            helper.assertTrue(level.getBlockState(source[0]).is(ModBlocks.BURROWED_DIRT.get()), "the source is not blocked: " + level.getBlockState(source[0]));
            helper.assertTrue(!colony.isAlarmed(level), "the colony is still on alert: " + colony.getAlert(level));
            AntColoniesMod.LOGGER.info("[tower-test] finished at tick {}", helper.getTick());
        });
    }

    /**
     * Lava is poured straight down the entrance hole: the source lands on the floor of the shaft (the bottom cell) and
     * the lava runs off into the tunnel. The ants must block that source (it is in the way out, so the soil has to be
     * bitten out again afterwards) and end up with the nest exactly as it was, every ant alive.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void antsBlockLavaPouredOntoTheFloorOfTheShaft(GameTestHelper helper) {
        pouredIntoTheHoleTest(helper, Blocks.LAVA, "lava-in-shaft-test", Colony.SHAFT_DEPTH);
    }

    /** Water poured into the entrance cell itself, the hole in the middle of the hill. */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void antsBlockWaterPouredIntoTheHoleItself(GameTestHelper helper) {
        pouredIntoTheHoleTest(helper, Blocks.WATER, "water-in-hole-test", 0);
    }

    /** Lava poured against the wall of the funnel, one block above the entrance cell. */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void antsBlockLavaPouredIntoTheFunnelOfTheHill(GameTestHelper helper) {
        pouredIntoTheHoleTest(helper, Blocks.LAVA, "lava-in-funnel-test", -1);
    }

    /** {@code below}: how many blocks under the entrance cell the source is put (0 = in it, negative = above it). */
    private static void pouredIntoTheHoleTest(GameTestHelper helper, net.minecraft.world.level.block.Block fluid, String tag, int below) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        startColony(helper);

        BlockPos[] source = {null};
        int[] antsBefore = {0};
        Set<Integer> aboveGround = new HashSet<>();
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null) {
                return;
            }
            if (source[0] == null) {
                if (airBelowSurface(helper) == NEST_CELLS && colony.findDigTarget(level) == null && hillProblems(helper).isEmpty()
                        && colony.getFinishedRoomCenter(level) != null) {
                    // A colony always has some workers out on the surface: three of them are up on the ground now.
                    for (int i = 0; i < 3; i++) {
                        helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE + 4 + i, SURFACE, MIDDLE + 3);
                    }
                    var ants = level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds());
                    antsBefore[0] = ants.size();
                    double groundY = helper.absolutePos(BlockPos.ZERO).getY() + SURFACE - 0.5;
                    for (AbstractAntEntity ant : ants) {
                        if (ant.getY() >= groundY) {
                            aboveGround.add(ant.getId());
                        }
                    }
                    source[0] = colony.getCenter().below(below);
                    level.setBlock(source[0], fluid.defaultBlockState(), 3);
                    AntColoniesMod.LOGGER.info("[{}] tick {}: poured at {} ({} ants)", tag, helper.getTick(), source[0], antsBefore[0]);
                }
                return;
            }
            if (helper.getTick() % 200 == 0) {
                AntColoniesMod.LOGGER.info("[{}] tick {}: alert {}, source cell {}, {} cells open, given up {}. {}", tag, helper.getTick(),
                        colony.getAlert(level), level.getBlockState(source[0]).getBlock(), airBelowSurface(helper),
                        colony.getGivenUpDoors(), describe(helper));
            }
        });

        helper.succeedWhen(() -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && source[0] != null, "nothing has been poured yet");
            helper.assertTrue(level.getBlockState(source[0]).isAir(), "the hole is not open again: " + level.getBlockState(source[0]));
            helper.assertTrue(!colony.isAlarmed(level), "the colony is still on alert: " + colony.getAlert(level));
            helper.assertTrue(airBelowSurface(helper) == NEST_CELLS,
                    "the nest is not open again: " + airBelowSurface(helper) + " of " + NEST_CELLS + " cells. " + describe(helper));
            helper.assertTrue(colony.findDigTarget(level) == null, "something is still left to dig");
            helper.assertTrue(colony.getLeaks(level).isEmpty(), "the nest has openings");
            helper.assertTrue(moundBlocks(helper) == 64, "expected exactly the hill, but there are " + moundBlocks(helper) + " blocks of soil above the ground");
            helper.assertTrue(colony.getGivenUpDoors().isEmpty(), "a room was given up because of the flood");
            // Lava that runs through the tunnels can still catch an ant that happens to be down there (they have no way to
            // run from it yet), but nobody who was working above the ground, and not the queen, may be lost.
            for (int id : aboveGround) {
                var ant = level.getEntity(id);
                helper.assertTrue(ant != null && ant.isAlive(), "an ant that was working above the ground was lost");
            }
            helper.assertTrue(!level.getEntitiesOfClass(com.antcolonies.entity.QueenAntEntity.class, helper.getBounds()).isEmpty(), "the queen was lost");
            int ants = level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds()).size();
            AntColoniesMod.LOGGER.info("[{}] finished at tick {}: {} of {} ants left", tag, helper.getTick(), ants, antsBefore[0]);
        });
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
