package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestWatch;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.registry.SavannaEntities;
import java.util.List;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.Route;
import com.antcolonies.nav.StepKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The legs, walked by savanna insects (the harvester removal, 2026-11-23: these were the harvester worker's
 * {@code NavigationTests}; the rules are the shared navigator's and path finder's, so they moved here whole). A Matabele
 * minor walks every scene; where a rule depends on the body's size (pits, edges, walls, ceilings) a termite worker, a
 * third as long, walks it too; the queen's walk is the Matabele queen's, the biggest and slowest body.
 *
 * <p>The ground is a ready-made nest: a 3-deep shaft in the middle of the test area, then four levels joined by a
 * spiral staircase, each a tunnel going east and a room at the end. Over its hole some scenes pile a stepped heap of
 * soil (the shape of the harvester's hill, kept as ground to climb: three blocks high next to the hole, falling to
 * one at its rim).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class LegsTests {
    private static final int SURFACE = 12;
    private static final int MIDDLE = 12;
    /** The stepped heap over the nest's hole reaches this far from it. */
    private static final int HEAP_RADIUS = 3;

    /** How high the heap is this far from the hole (0: no heap there; the hole itself stays open). */
    private static int heapHeightAt(int dx, int dz) {
        int distanceSquared = dx * dx + dz * dz;
        if (distanceSquared == 0) {
            return 0;
        }
        if (distanceSquared <= 2) {
            return 3;
        }
        return distanceSquared <= 5 ? 2 : distanceSquared <= 10 ? 1 : 0;
    }

    /** The queen (bigger and slower than a worker) walks from the seed store up the shaft to the surface and back down. */
    @GameTest(template = "arena", timeoutTicks = 5000, skyAccess = true)
    public static void queenWalksFromTheRoomUpToTheSurfaceAndBackDown(GameTestHelper helper) {
        buildNest(helper);
        AbstractAntEntity ant = helper.spawnWithNoFreeWill(SavannaEntities.MATABELE_QUEEN.get(), MIDDLE + 8, SURFACE - 3, MIDDLE + 2);
        BlockPos up = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 2));
        BlockPos down = helper.absolutePos(new BlockPos(MIDDLE + 8, SURFACE - 3, MIDDLE - 2));
        boolean[] wentUp = {false};
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(up, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> {
            if (!wentUp[0] && helper.getTick() > 20 && ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(up)) {
                wentUp[0] = true;
                ant.getNavigator().moveTo(down, 1.0);
            }
            helper.assertTrue(wentUp[0], "the queen has not reached the surface yet");
            helper.assertTrue(ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(down),
                    "the queen has not gone back down to the room yet");
        });
    }

    /**
     * Regression test for a crash: the route was only the cell the ant already stood in, and the ant then left that cell.
     * And (the navigator tidy-up, 2026-10-16) an ant walking a route a mind handed it ({@code follow}, later a trail or a
     * marching column) that is moved off it gets back onto it where it left it and goes on along it, instead of walking
     * straight to its end: the route goes round three sides of a square, and the ant is moved far off half way along.
     */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void antToldToGoToItsOwnCellSurvivesBeingMovedAway(GameTestHelper helper) {
        antToldToGoToItsOwnCellSurvivesBeingMovedAway(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antToldToGoToItsOwnCellSurvivesBeingMovedAway(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        AbstractAntEntity ant = TestAnts.lone(helper, body, MIDDLE + 5, SURFACE, MIDDLE + 5);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 5, SURFACE, MIDDLE + 5));
        helper.runAfterDelay(10, () -> {
            ant.getNavigator().moveTo(ant.blockPosition(), 1.0);
            ant.setPos(ant.getX() + 1.6, ant.getY(), ant.getZ()); // out of the cell before the first step
        });
        // Three sides of a square: 3 east, 3 south, 3 west.
        int x0 = MIDDLE - 7;
        int z0 = MIDDLE + 6;
        List<Route.Step> steps = new java.util.ArrayList<>();
        steps.add(new Route.Step(Foothold.floor(helper.absolutePos(new BlockPos(x0, SURFACE, z0))), StepKind.START));
        int[][] moves = {{1, 0}, {1, 0}, {1, 0}, {0, 1}, {0, 1}, {0, 1}, {-1, 0}, {-1, 0}, {-1, 0}};
        int x = x0;
        int z = z0;
        for (int[] move : moves) {
            x += move[0];
            z += move[1];
            steps.add(new Route.Step(Foothold.floor(helper.absolutePos(new BlockPos(x, SURFACE, z))), StepKind.WALK));
        }
        Route square = new Route(steps, 9.0);
        BlockPos farCorner = helper.absolutePos(new BlockPos(x0 + 3, SURFACE, z0 + 3));
        AbstractAntEntity follower = TestAnts.lone(helper, body, x0, SURFACE, z0);
        boolean[] movedOff = {false};
        boolean[] atFarCorner = {false};
        helper.runAfterDelay(10, () -> helper.assertTrue(follower.getNavigator().follow(square, 1.0), "no way onto the route " + square));
        helper.onEachTick(() -> {
            if (!movedOff[0] && helper.getTick() > 10 && follower.getNavigator().getNextWaypoint() == 2) {
                // Half way along the first side: put it 4 blocks west of the route's start, nearer the end than the far corner.
                BlockPos off = helper.absolutePos(new BlockPos(x0 - 4, SURFACE, z0 + 2));
                follower.setPos(off.getX() + 0.5, off.getY(), off.getZ() + 0.5);
                movedOff[0] = true;
            }
            if (movedOff[0] && follower.blockPosition().equals(farCorner)) {
                atFarCorner[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                    "the ant has not walked back to its cell yet");
            helper.assertTrue(movedOff[0], "the follower was not moved off its route yet");
            helper.assertTrue(follower.getNavigator().isDone(), "the follower has not finished its route yet");
            helper.assertTrue(!follower.getNavigator().hasFailed() && follower.blockPosition().equals(square.end().cell()),
                    "the follower did not get to the end of its route");
            helper.assertTrue(atFarCorner[0], "the follower walked straight to the end of its route instead of back onto it");
        });
    }

    /**
     * Two one-block pits in the ground: an ant at the bottom of one must climb out over its wall, and an ant that plans
     * its route while still in the air above the other falls in on its way and climbs out too.
     */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void antsClimbOutOfAOneBlockPit(GameTestHelper helper) {
        antsClimbOutOfAOneBlockPit(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    /** The same with a termite worker (a body a third as long: the edges, walls and ceilings must hold for both). */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void antsClimbOutOfAOneBlockPitAsATermiteWorker(GameTestHelper helper) {
        antsClimbOutOfAOneBlockPit(helper, SavannaEntities.TERMITE_WORKER.get());
    }

    private static void antsClimbOutOfAOneBlockPit(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        helper.setBlock(MIDDLE + 6, SURFACE - 1, MIDDLE + 9, Blocks.AIR);
        helper.setBlock(MIDDLE - 6, SURFACE - 1, MIDDLE + 9, Blocks.AIR);
        AbstractAntEntity inPit = TestAnts.lone(helper, body, MIDDLE + 6.5F, (float) (SURFACE - 1), MIDDLE + 9.5F);
        AbstractAntEntity abovePit = TestAnts.lone(helper, body, MIDDLE - 5.5F, (float) SURFACE, MIDDLE + 9.5F);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 9));
        BlockPos otherGoal = helper.absolutePos(new BlockPos(MIDDLE - 3, SURFACE, MIDDLE + 9));
        helper.runAfterDelay(1, () -> abovePit.getNavigator().moveTo(otherGoal, 1.0));
        helper.runAfterDelay(10, () -> inPit.getNavigator().moveTo(goal, 1.0));
        watch(helper, inPit);
        TestWatch.watch(helper, abovePit, false);
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 20, "not started yet");
            helper.assertTrue(inPit.getNavigator().isDone() && !inPit.getNavigator().hasFailed() && inPit.blockPosition().equals(goal),
                    "the ant has not climbed out of the pit yet");
            helper.assertTrue(abovePit.getNavigator().isDone() && !abovePit.getNavigator().hasFailed() && abovePit.blockPosition().equals(otherGoal),
                    "the ant that fell in has not climbed out of the pit yet");
        });
    }

    /**
     * One ant on every surface cell (flat ground and every step of the hill) around the finished hill, each somewhere
     * inside its cell, all sent into the food store at the same time: every one of them must get there, and none may
     * take much longer than the others.
     */
    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true)
    public static void antsFromAllAroundTheHillReachTheFoodStore(GameTestHelper helper) {
        antsFromAllAroundTheHillReachTheFoodStore(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antsFromAllAroundTheHillReachTheFoodStore(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        scan(helper, body, 2L, true, false, false);
    }

    /**
     * The same on bumpy ground (single blocks piled on the grass and single grass blocks missing) with a hill that is
     * only partly built (some blocks of its top layers are missing).
     */
    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true)
    public static void antsOnBumpyGroundOverAHalfBuiltHillReachTheFoodStore(GameTestHelper helper) {
        antsOnBumpyGroundOverAHalfBuiltHillReachTheFoodStore(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antsOnBumpyGroundOverAHalfBuiltHillReachTheFoodStore(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        scan(helper, body, 3L, true, true, true);
    }

    /** The cell an ant stands in on top of the highest solid block in this column. */
    private static int standingY(GameTestHelper helper, int x, int z) {
        for (int y = SURFACE + 4; y > 0; y--) {
            if (!helper.getBlockState(new BlockPos(x, y - 1, z)).getCollisionShape(helper.getLevel(), BlockPos.ZERO).isEmpty()) {
                return y;
            }
        }
        return 1;
    }

    private static void scan(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body, long seed, boolean anywhereInCell, boolean bumpy, boolean gappyHill) {
        helper.getLevel().getServer().tickRateManager().setTickRate(2000.0F);
        buildNest(helper);
        java.util.Random random = new java.util.Random(seed);
        if (bumpy) {
            for (int dx = -11; dx <= 11; dx++) {
                for (int dz = -11; dz <= 11; dz++) {
                    if (Math.abs(dx) <= HEAP_RADIUS + 1 && Math.abs(dz) <= HEAP_RADIUS + 1) {
                        continue; // the hill keeps flat ground around it
                    }
                    double roll = random.nextDouble();
                    if (roll < 0.12) {
                        helper.setBlock(MIDDLE + dx, SURFACE, MIDDLE + dz, Blocks.DIRT);
                    } else if (roll < 0.20) {
                        helper.setBlock(MIDDLE + dx, SURFACE - 1, MIDDLE + dz, Blocks.AIR);
                    }
                }
            }
        }
        buildHill(helper);
        if (gappyHill) {
            for (int dx = -HEAP_RADIUS; dx <= HEAP_RADIUS; dx++) {
                for (int dz = -HEAP_RADIUS; dz <= HEAP_RADIUS; dz++) {
                    int height = heapHeightAt(dx, dz);
                    if (height > 1 && random.nextDouble() < 0.35) {
                        helper.setBlock(MIDDLE + dx, SURFACE + height - 1, MIDDLE + dz, Blocks.AIR);
                    }
                }
            }
        }
        NestPlan.Chamber store = chambers(helper).get(0);
        BlockPos goal = store.center();
        // An insect with a real size keeps a gap to the others (no stacking), so 440 of them cannot all end in the one goal
        // cell: once it is full the navigator ends a walk on the nearest free spot round it (the crowd spills into the
        // room). Arrived is: the walk done, standing in the store room or the end of its tunnel.
        java.util.Set<BlockPos> storeRoom = new java.util.HashSet<>(store.cells());
        storeRoom.addAll(store.room());
        storeRoom.add(store.tunnelEnd());
        List<AbstractAntEntity> ants = new java.util.ArrayList<>();
        List<String> starts = new java.util.ArrayList<>();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // the hole itself
                }
                int y = standingY(helper, MIDDLE + dx, MIDDLE + dz);
                float fx = MIDDLE + dx + (anywhereInCell ? 0.2F + 0.6F * random.nextFloat() : 0.5F);
                float fz = MIDDLE + dz + (anywhereInCell ? 0.2F + 0.6F * random.nextFloat() : 0.5F);
                ants.add(TestAnts.lone(helper, body, fx, (float) y, fz));
                starts.add(dx + "," + dz + " y" + y);
            }
        }
        long[] arrived = new long[ants.size()];
        helper.runAfterDelay(10, () -> {
            for (AbstractAntEntity ant : ants) {
                ant.getNavigator().moveTo(goal, 1.0);
            }
        });
        helper.onEachTick(() -> {
            if (helper.getTick() <= 10) {
                return;
            }
            for (int i = 0; i < ants.size(); i++) {
                AbstractAntEntity ant = ants.get(i);
                if (arrived[i] == 0 && ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && storeRoom.contains(ant.blockPosition())) {
                    arrived[i] = helper.getTick();
                }
            }
        });
        helper.runAfterDelay(3000, () -> {
            long slowest = 0;
            int late = 0;
            for (int i = 0; i < ants.size(); i++) {
                if (arrived[i] == 0 || arrived[i] > 700) {
                    late++;
                    SavannaMod.LOGGER.info("[scan {}] ant from {} {} (arrived at tick {}, now at {}, route failed {})", seed, starts.get(i),
                            arrived[i] == 0 ? "NEVER ARRIVED" : "was slow", arrived[i],
                            ants.get(i).position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))),
                            ants.get(i).getNavigator().hasFailed());
                    var navigator = ants.get(i).getNavigator();
                    SavannaMod.LOGGER.info("[scan {}]   cell {} next waypoint {} climbing {} route {}", seed,
                            ants.get(i).blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString(), navigator.getNextWaypoint(),
                            navigator.isClimbing(), navigator.getRoute() == null ? "none" : relative(helper, navigator.getRoute()));
                }
                slowest = Math.max(slowest, arrived[i]);
            }
            SavannaMod.LOGGER.info("[scan {}] {} ants, {} late or lost, slowest arrival at tick {}", seed, ants.size(), late, slowest);
            helper.assertTrue(late == 0, late + " of " + ants.size() + " ants were late or never arrived");
            helper.succeed();
        });
    }

    /** The complete ant hill around the entrance (the hole at MIDDLE, MIDDLE stays open). */
    private static void buildHill(GameTestHelper helper) {
        for (int dx = -HEAP_RADIUS; dx <= HEAP_RADIUS; dx++) {
            for (int dz = -HEAP_RADIUS; dz <= HEAP_RADIUS; dz++) {
                for (int h = 0; h < heapHeightAt(dx, dz); h++) {
                    helper.setBlock(MIDDLE + dx, SURFACE + h, MIDDLE + dz, Blocks.DIRT);
                }
            }
        }
    }

    private static void buildNest(GameTestHelper helper) {
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        for (int y = SURFACE - 3; y < SURFACE; y++) {
            helper.setBlock(MIDDLE, y, MIDDLE, Blocks.AIR); // shaft
        }
        for (NestPlan.Chamber chamber : chambers(helper)) {
            for (BlockPos cell : chamber.cells()) {
                helper.setBlock(cell.subtract(helper.absolutePos(BlockPos.ZERO)), Blocks.AIR); // tunnels and rooms
            }
        }
    }

    /** The four levels of the ready-made nest: the food store (east of the shaft), the nursery, the queen's room and the refuse chamber. */
    private static List<NestPlan.Chamber> chambers(GameTestHelper helper) {
        BlockPos bottom = helper.absolutePos(new BlockPos(MIDDLE, SURFACE - 3, MIDDLE));
        return NestPlan.chambers(bottom, Direction.EAST, NestPlan.MAX_LEVELS);
    }

    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antWalksFromTheSurfaceDownToTheDeepestRoom(GameTestHelper helper) {
        antWalksFromTheSurfaceDownToTheDeepestRoom(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antWalksFromTheSurfaceDownToTheDeepestRoom(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        AbstractAntEntity ant = TestAnts.lone(helper, body, MIDDLE - 3, SURFACE, MIDDLE + 3);
        BlockPos goal = chambers(helper).get(3).center();
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not reached the deepest room yet"));
    }

    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antWalksFromTheQueensRoomDownToTheRefuseChamberAndBack(GameTestHelper helper) {
        antWalksFromTheQueensRoomDownToTheRefuseChamberAndBack(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antWalksFromTheQueensRoomDownToTheRefuseChamberAndBack(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        BlockPos start = chambers(helper).get(2).center().subtract(helper.absolutePos(BlockPos.ZERO));
        AbstractAntEntity ant = TestAnts.lone(helper, body, start.getX(), start.getY(), start.getZ());
        BlockPos deep = chambers(helper).get(3).center();
        BlockPos back = chambers(helper).get(2).center();
        boolean[] wentDown = {false};
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(deep, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> {
            if (!wentDown[0] && ant.getNavigator().isDone() && ant.blockPosition().equals(deep)) {
                wentDown[0] = true;
                ant.getNavigator().moveTo(back, 1.0);
            }
            helper.assertTrue(wentDown[0] && ant.getNavigator().isDone() && !ant.getNavigator().hasFailed()
                    && ant.blockPosition().equals(back), "the ant has not been down and back yet");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antWalksFromTheDeepestRoomUpToTheSurface(GameTestHelper helper) {
        antWalksFromTheDeepestRoomUpToTheSurface(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antWalksFromTheDeepestRoomUpToTheSurface(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        BlockPos start = chambers(helper).get(3).center().subtract(helper.absolutePos(BlockPos.ZERO));
        AbstractAntEntity ant = TestAnts.lone(helper, body, start.getX(), start.getY(), start.getZ());
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 2));
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not climbed out of the deepest room yet"));
    }

    /**
     * Climbs from the middle of every room out through the finished hill, four ants from each room at once, each to a
     * place on another side of it (so every side the hill can be left over is tried): the whole way up must be steady,
     * with no stalls and no hanging in mid-air.
     */
    @GameTest(template = "arena", timeoutTicks = 2000, skyAccess = true)
    public static void antsClimbFromEveryRoomThroughTheFinishedHill(GameTestHelper helper) {
        antsClimbFromEveryRoomThroughTheFinishedHill(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antsClimbFromEveryRoomThroughTheFinishedHill(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        buildHill(helper);
        int[][] goals = {{5, 0}, {-5, 0}, {0, 5}, {0, -5}};
        List<AbstractAntEntity> ants = new java.util.ArrayList<>();
        List<BlockPos> targets = new java.util.ArrayList<>();
        List<String> rooms = new java.util.ArrayList<>();
        for (int level = 0; level < NestPlan.MAX_LEVELS; level++) {
            BlockPos start = chambers(helper).get(level).center().subtract(helper.absolutePos(BlockPos.ZERO));
            for (int[] goal : goals) {
                ants.add(TestAnts.lone(helper, body, start.getX(), start.getY(), start.getZ()));
                targets.add(helper.absolutePos(new BlockPos(MIDDLE + goal[0], SURFACE, MIDDLE + goal[1])));
                rooms.add("room " + level);
            }
        }
        helper.runAfterDelay(10, () -> {
            for (int i = 0; i < ants.size(); i++) {
                boolean found = ants.get(i).getNavigator().moveTo(targets.get(i), 1.0);
                SavannaMod.LOGGER.info("[nav-test] #{} route found: {} -> {}", ants.get(i).getId(), found,
                        ants.get(i).getNavigator().getRoute() == null ? "none" : relative(helper, ants.get(i).getNavigator().getRoute()));
            }
        });
        for (AbstractAntEntity ant : ants) {
            TestWatch.watch(helper, ant, ant == ants.get(0));
            trackSteadiness(helper, ant);
        }
        helper.succeedWhen(() -> {
            for (int i = 0; i < ants.size(); i++) {
                // Four ants share each place, and an insect with a real size keeps a gap: one that finds the cell full ends
                // its walk on the nearest free spot beside it (the same height, at most one cell off).
                BlockPos at = ants.get(i).blockPosition();
                BlockPos to = targets.get(i);
                helper.assertTrue(ants.get(i).getNavigator().isDone() && !ants.get(i).getNavigator().hasFailed()
                        && at.getY() == to.getY() && Math.abs(at.getX() - to.getX()) <= 1 && Math.abs(at.getZ() - to.getZ()) <= 1 && helper.getTick() > 20,
                        "ant " + i + " (from " + rooms.get(i) + ") has not climbed out yet");
            }
        });
    }

    /**
     * Two ants from the nursery that stay on the shaft wall: one whose walk ends in the shaft (no floor there) hangs on
     * to the wall where it is and must not fall to the bottom; one that is stopped half way up the shaft on its way out
     * (another goal takes over) keeps clinging to the wall.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antsStayOnTheShaftWallWhenTheirWalkEndsOrIsStopped(GameTestHelper helper) {
        antsStayOnTheShaftWallWhenTheirWalkEndsOrIsStopped(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    /** The same with a termite worker (a body a third as long: the edges, walls and ceilings must hold for both). */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antsStayOnTheShaftWallWhenTheirWalkEndsOrIsStoppedAsATermiteWorker(GameTestHelper helper) {
        antsStayOnTheShaftWallWhenTheirWalkEndsOrIsStopped(helper, SavannaEntities.TERMITE_WORKER.get());
    }

    private static void antsStayOnTheShaftWallWhenTheirWalkEndsOrIsStopped(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        buildHill(helper);
        BlockPos start = chambers(helper).get(1).center().subtract(helper.absolutePos(BlockPos.ZERO));
        AbstractAntEntity ant = TestAnts.lone(helper, body, start.getX(), start.getY(), start.getZ());
        AbstractAntEntity stopped = TestAnts.lone(helper, body, start.getX(), start.getY(), start.getZ());
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE, SURFACE - 1, MIDDLE));
        BlockPos out = helper.absolutePos(new BlockPos(MIDDLE - 5, SURFACE, MIDDLE - 2));
        long[] arrived = {-1};
        double[] stoppedAt = {Double.NaN};
        long[] stoppedTick = {0};
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        helper.runAfterDelay(10, () -> stopped.getNavigator().moveTo(out, 1.0));
        TestWatch.watch(helper, ant, false);
        TestWatch.watch(helper, stopped, false);
        helper.onEachTick(() -> {
            double y = stopped.getY() - helper.absolutePos(BlockPos.ZERO).getY();
            if (Double.isNaN(stoppedAt[0]) && stopped.getNavigator().isClimbing() && y > SURFACE - 1.5 && y < SURFACE + 1) {
                stopped.getNavigator().stop();
                stoppedAt[0] = stopped.getY();
                stoppedTick[0] = helper.getTick();
            }
            if (arrived[0] < 0 && ant.getNavigator().isDone() && ant.blockPosition().equals(goal)) {
                arrived[0] = helper.getTick();
            }
            if (!Double.isNaN(stoppedAt[0])) {
                helper.assertTrue(stopped.getY() >= stoppedAt[0] - 0.05, "the stopped ant fell (from " + stoppedAt[0] + " to " + stopped.getY() + ")");
            }
            if (arrived[0] >= 0) {
                helper.assertTrue(ant.getY() >= goal.getY() - 0.01, "the ant fell out of its cell (y " + ant.getY() + ")");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(arrived[0] >= 0, "the ant has not reached the top of the shaft yet");
            helper.assertTrue(!Double.isNaN(stoppedAt[0]), "the other ant has not climbed up the shaft yet");
            helper.assertTrue(helper.getTick() >= arrived[0] + 80 && helper.getTick() >= stoppedTick[0] + 80, "waiting to see whether the ants stay up");
            helper.assertTrue(ant.getNavigator().isClimbing(), "the ant is not clinging to the wall");
        });
    }

    /**
     * Two ants hanging on a wall of the shaft, one at its top and one a cell lower (then the way out starts with a
     * straight climb), each get a new job on the far side (they have to climb out over the opposite wall): they crawl
     * over the walls and round the corners to it, and never let go to drift across the open shaft.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antsHangingInTheShaftCrawlRoundToTheOtherWall(GameTestHelper helper) {
        antsHangingInTheShaftCrawlRoundToTheOtherWall(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antsHangingInTheShaftCrawlRoundToTheOtherWall(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        BlockPos start = chambers(helper).get(1).center().subtract(helper.absolutePos(BlockPos.ZERO));
        int[] depths = {1, 2};
        AbstractAntEntity[] ants = new AbstractAntEntity[depths.length];
        BlockPos[] hangs = new BlockPos[depths.length];
        BlockPos[] goals = new BlockPos[depths.length];
        Direction[] clung = new Direction[depths.length];
        long[] arrived = {-1, -1};
        for (int k = 0; k < depths.length; k++) {
            ants[k] = TestAnts.lone(helper, body, start.getX(), start.getY(), start.getZ());
            hangs[k] = helper.absolutePos(new BlockPos(MIDDLE, SURFACE - depths[k], MIDDLE));
            int which = k;
            // The second waits a little, so the two are not on the same stretch of wall at once.
            helper.runAfterDelay(10 + 100 * k, () -> ants[which].getNavigator().moveTo(hangs[which], 1.0));
            TestWatch.watch(helper, ants[k], k == 0);
        }
        helper.onEachTick(() -> {
            for (int k = 0; k < depths.length; k++) {
                AbstractAntEntity ant = ants[k];
                if (arrived[k] < 0 && helper.getTick() > 12 + 100 * k && ant.getNavigator().isDone() && ant.blockPosition().equals(hangs[k])) {
                    arrived[k] = helper.getTick();
                }
                if (goals[k] == null && arrived[k] >= 0 && helper.getTick() >= arrived[k] + 30) {
                    // The wall it hangs on is the side its body is pushed furthest toward; send it out over the opposite one.
                    double dx = ant.getX() - (hangs[k].getX() + 0.5);
                    double dz = ant.getZ() - (hangs[k].getZ() + 0.5);
                    clung[k] = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                    Direction out = clung[k].getOpposite();
                    goals[k] = helper.absolutePos(new BlockPos(MIDDLE + out.getStepX() * 4, SURFACE, MIDDLE + out.getStepZ() * 4));
                    boolean found = ant.getNavigator().moveTo(goals[k], 1.0);
                    SavannaMod.LOGGER.info("[nav-test] #{} hangs on the {} wall, sent out to the {}: route found {} -> {}", ant.getId(),
                            clung[k], out, found, ant.getNavigator().getRoute() == null ? "none" : relative(helper, ant.getNavigator().getRoute()));
                }
            }
        });
        helper.succeedWhen(() -> {
            for (int k = 0; k < depths.length; k++) {
                helper.assertTrue(goals[k] != null, "ant " + k + " has not reached its spot in the shaft yet");
                helper.assertTrue(ants[k].getNavigator().isDone() && !ants[k].getNavigator().hasFailed() && ants[k].blockPosition().equals(goals[k]),
                        "ant " + k + " (from the " + clung[k] + " wall) has not climbed out over the opposite side yet");
            }
        });
    }

    /**
     * Round the outside edge of a block standing on the ground (like the edge of a hill block), in every direction and
     * both ways round: an ant holding one side of the block goes round its vertical edge onto the next side. It must get
     * there without ever being blocked (no new plans) and without floating.
     */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void antsGoRoundTheOutsideEdgeOfABlock(GameTestHelper helper) {
        antsGoRoundTheOutsideEdgeOfABlock(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    /** The same with a termite worker (a body a third as long: the edges, walls and ceilings must hold for both). */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void antsGoRoundTheOutsideEdgeOfABlockAsATermiteWorker(GameTestHelper helper) {
        antsGoRoundTheOutsideEdgeOfABlock(helper, SavannaEntities.TERMITE_WORKER.get());
    }

    private static void antsGoRoundTheOutsideEdgeOfABlock(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        List<AbstractAntEntity> ants = new java.util.ArrayList<>();
        List<BlockPos> ends = new java.util.ArrayList<>();
        int i = 0;
        for (Direction wall : Direction.Plane.HORIZONTAL) {
            for (Direction side : new Direction[] {wall.getClockWise(), wall.getCounterClockWise()}) {
                BlockPos block = new BlockPos(3 + (i % 4) * 5, SURFACE, 3 + (i / 4) * 16);
                helper.setBlock(block, Blocks.DIRT);
                BlockPos start = block.relative(wall.getOpposite());
                BlockPos end = start.relative(side).relative(wall);
                Route route = new Route(List.of(
                        new Route.Step(new Foothold(helper.absolutePos(start), wall), StepKind.START),
                        new Route.Step(new Foothold(helper.absolutePos(end), side.getOpposite()), StepKind.ROUND_EDGE)), 1.2);
                AbstractAntEntity ant = TestAnts.lone(helper, body, start.getX(), start.getY(), start.getZ());
                helper.runAfterDelay(10, () -> helper.assertTrue(ant.getNavigator().follow(route, 1.0), "no way onto the route " + route));
                TestWatch.watch(helper, ant, false);
                ants.add(ant);
                ends.add(helper.absolutePos(end));
                i++;
            }
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 20, "not started yet");
            for (int k = 0; k < ants.size(); k++) {
                AbstractAntEntity ant = ants.get(k);
                helper.assertTrue(ant.getNavigator().getReplans() == 0, "ant " + k + " had to plan again (it was blocked)");
                helper.assertTrue(ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(ends.get(k)),
                        "ant " + k + " has not gone round the edge yet");
            }
        });
    }

    /**
     * An ant in the corner of the seed store climbs the wall, turns over onto the ceiling and crawls upside down to the
     * middle of the room, where it hangs on (pressed against the ceiling, not falling) after its walk ends.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, skyAccess = true)
    public static void antCrawlsUpsideDownToTheMiddleOfTheRoomCeilingAndHangsThere(GameTestHelper helper) {
        antCrawlsUpsideDownToTheMiddleOfTheRoomCeilingAndHangsThere(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    /** The same with a termite worker (a body a third as long: the edges, walls and ceilings must hold for both). */
    @GameTest(template = "arena", timeoutTicks = 1200, skyAccess = true)
    public static void antCrawlsUpsideDownToTheMiddleOfTheRoomCeilingAndHangsThereAsATermiteWorker(GameTestHelper helper) {
        antCrawlsUpsideDownToTheMiddleOfTheRoomCeilingAndHangsThere(helper, SavannaEntities.TERMITE_WORKER.get());
    }

    private static void antCrawlsUpsideDownToTheMiddleOfTheRoomCeilingAndHangsThere(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        NestPlan.Chamber store = chambers(helper).get(0);
        BlockPos corner = store.room().get(store.room().size() - 1).subtract(helper.absolutePos(BlockPos.ZERO));
        AbstractAntEntity ant = TestAnts.lone(helper, body, corner.getX(), corner.getY(), corner.getZ());
        Foothold goal = Foothold.ceiling(store.center());
        long[] arrived = {-1};
        helper.runAfterDelay(10, () -> helper.assertTrue(ant.getNavigator().moveTo(goal, 1.0), "no route to the ceiling"));
        TestWatch.watch(helper, ant, false);
        helper.succeedWhen(() -> {
            if (arrived[0] < 0 && helper.getTick() > 12 && ant.getNavigator().isDone()) {
                helper.assertTrue(!ant.getNavigator().hasFailed(), "the walk to the ceiling failed");
                arrived[0] = helper.getTick();
            }
            helper.assertTrue(arrived[0] >= 0, "the ant has not reached the middle of the ceiling yet");
            helper.assertTrue(ant.blockPosition().equals(goal.cell()), "the ant is not in the middle of the room");
            helper.assertTrue(ant.getNavigator().getSurface() == Direction.UP, "the ant is not on the ceiling");
            helper.assertTrue(!helper.getLevel().noCollision(ant, ant.getBoundingBox().move(0.0, 0.05, 0.0)),
                    "the ant is not pressed against the ceiling (y " + ant.getY() + ")");
            helper.assertTrue(helper.getTick() >= arrived[0] + 60, "waiting to see whether the ant stays up");
        });
    }

    /**
     * A block sticking out sideways from the top of a two-block pillar, in every direction: an ant on its far face goes
     * down round its bottom edge onto its underside (upside down), and from there round the side edge and up its side
     * face, without being blocked and without floating.
     */
    @GameTest(template = "arena", timeoutTicks = 900, skyAccess = true)
    public static void antsGoUnderAnOverhangAndOutTheOtherSide(GameTestHelper helper) {
        antsGoUnderAnOverhangAndOutTheOtherSide(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    /** The same with a termite worker (a body a third as long: the edges, walls and ceilings must hold for both). */
    @GameTest(template = "arena", timeoutTicks = 900, skyAccess = true)
    public static void antsGoUnderAnOverhangAndOutTheOtherSideAsATermiteWorker(GameTestHelper helper) {
        antsGoUnderAnOverhangAndOutTheOtherSide(helper, SavannaEntities.TERMITE_WORKER.get());
    }

    private static void antsGoUnderAnOverhangAndOutTheOtherSide(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        List<AbstractAntEntity> ants = new java.util.ArrayList<>();
        List<BlockPos> ends = new java.util.ArrayList<>();
        BlockPos[] pillars = {new BlockPos(4, SURFACE, 4), new BlockPos(4, SURFACE, 20), new BlockPos(20, SURFACE, 4), new BlockPos(20, SURFACE, 20)};
        int i = 0;
        for (Direction out : Direction.Plane.HORIZONTAL) {
            BlockPos pillar = pillars[i++];
            BlockPos overhang = pillar.above().relative(out);
            helper.setBlock(pillar, Blocks.DIRT);
            helper.setBlock(pillar.above(), Blocks.DIRT);
            helper.setBlock(overhang, Blocks.DIRT);
            Direction side = out.getClockWise();
            BlockPos start = overhang.relative(out);
            BlockPos end = overhang.relative(side);
            Route route = new Route(List.of(
                    new Route.Step(new Foothold(helper.absolutePos(start), out.getOpposite()), StepKind.START),
                    new Route.Step(Foothold.ceiling(helper.absolutePos(overhang.below())), StepKind.UNDER_EDGE),
                    new Route.Step(new Foothold(helper.absolutePos(end), side.getOpposite()), StepKind.OUT_FROM_UNDER)), 4.0);
            BlockPos ground = start.below();
            AbstractAntEntity ant = TestAnts.lone(helper, body, ground.getX(), ground.getY(), ground.getZ());
            helper.runAfterDelay(10, () -> helper.assertTrue(ant.getNavigator().follow(route, 1.0), "no way onto the route " + route));
            TestWatch.watch(helper, ant, false);
            ants.add(ant);
            ends.add(helper.absolutePos(end));
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 20, "not started yet");
            for (int k = 0; k < ants.size(); k++) {
                AbstractAntEntity ant = ants.get(k);
                helper.assertTrue(ant.getNavigator().getReplans() == 0, "ant " + k + " had to plan again (it was blocked)");
                helper.assertTrue(ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(ends.get(k)),
                        "ant " + k + " has not come out from under the overhang yet");
            }
        });
    }

    /**
     * Two ants hanging on when the world is saved, one on the shaft wall and one from the seed store's ceiling: after it
     * is loaded again each is still where it was, holding on.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antsOnTheShaftWallAndTheCeilingAreStillThereAfterAReload(GameTestHelper helper) {
        antsOnTheShaftWallAndTheCeilingAreStillThereAfterAReload(helper, SavannaEntities.MATABELE_MINOR.get());
    }

    private static void antsOnTheShaftWallAndTheCeilingAreStillThereAfterAReload(GameTestHelper helper, EntityType<? extends AbstractAntEntity> body) {
        buildNest(helper);
        BlockPos start = chambers(helper).get(1).center().subtract(helper.absolutePos(BlockPos.ZERO));
        AbstractAntEntity onWall = TestAnts.lone(helper, body, start.getX(), start.getY(), start.getZ());
        BlockPos wall = helper.absolutePos(new BlockPos(MIDDLE, SURFACE - 1, MIDDLE));
        helper.runAfterDelay(10, () -> onWall.getNavigator().moveTo(wall, 1.0));
        NestPlan.Chamber store = chambers(helper).get(0);
        BlockPos corner = store.room().get(0).subtract(helper.absolutePos(BlockPos.ZERO));
        AbstractAntEntity onCeiling = TestAnts.lone(helper, body, corner.getX(), corner.getY(), corner.getZ());
        Foothold ceiling = Foothold.ceiling(store.center());
        helper.runAfterDelay(10, () -> onCeiling.getNavigator().moveTo(ceiling, 1.0));
        java.util.function.Supplier<String> wallCheck = reloadOnceUp(helper, onWall,
                () -> onWall.getNavigator().isDone() && onWall.blockPosition().equals(wall));
        java.util.function.Supplier<String> ceilingCheck = reloadOnceUp(helper, onCeiling, () -> onCeiling.getNavigator().isDone()
                && onCeiling.blockPosition().equals(ceiling.cell()) && onCeiling.getNavigator().getSurface() == Direction.UP);
        helper.succeedWhen(() -> {
            String wallTrouble = wallCheck.get();
            helper.assertTrue(wallTrouble == null, "on the shaft wall: " + wallTrouble);
            String ceilingTrouble = ceilingCheck.get();
            helper.assertTrue(ceilingTrouble == null, "on the ceiling: " + ceilingTrouble);
        });
    }

    /**
     * Waits until the ant is up ({@code isUp}) and has hung there for 20 ticks, then saves it and puts a copy loaded from
     * that save in its place (what happens when its chunk is saved and loaded again). The copy must not drop. The check
     * it gives back says what is still wrong, or null once the copy has stayed up long enough.
     */
    private static java.util.function.Supplier<String> reloadOnceUp(GameTestHelper helper, AbstractAntEntity ant, java.util.function.BooleanSupplier isUp) {
        long[] upSince = {-1};
        AbstractAntEntity[] copy = {null};
        double[] savedY = {0.0};
        long[] loadedAt = {0};
        TestWatch.watch(helper, ant, false);
        helper.onEachTick(() -> {
            if (copy[0] != null) {
                return;
            }
            if (upSince[0] < 0 && helper.getTick() > 12 && isUp.getAsBoolean()) {
                upSince[0] = helper.getTick();
            }
            if (upSince[0] >= 0 && helper.getTick() >= upSince[0] + 20) {
                net.minecraft.nbt.CompoundTag tag = ant.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                tag.remove("UUID");
                savedY[0] = ant.getY();
                ant.discard();
                AbstractAntEntity loaded = (AbstractAntEntity) ant.getType().create(helper.getLevel());
                loaded.load(tag);
                // Like the ant it replaces: no mind of its own (no job or stroll may move it) and no colony.
                loaded.removeFreeWill();
                loaded.keepOutOfColonies();
                helper.getLevel().addFreshEntity(loaded);
                copy[0] = loaded;
                loadedAt[0] = helper.getTick();
            }
        });
        return () -> {
            if (copy[0] == null) {
                return "the ant has not got up there yet";
            }
            AbstractAntEntity loaded = copy[0];
            if (loaded.getY() < savedY[0] - 0.05) {
                return "the ant dropped after the reload (from " + savedY[0] + " to " + loaded.getY() + ")";
            }
            if (!loaded.getNavigator().isClimbing()) {
                return "the ant let go after the reload";
            }
            return helper.getTick() >= loadedAt[0] + 80 ? null : "waiting to see whether the reloaded ant stays up";
        };
    }

    /** Fails if a climbing ant stands still for 12 ticks or more, or slides sideways away from the wall it hugs. */
    private static void trackSteadiness(GameTestHelper helper, AbstractAntEntity ant) {
        Vec3[] last = {null};
        int[] still = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() < 12 || ant.getNavigator().isDone()) {
                last[0] = null;
                return;
            }
            Vec3 now = ant.position();
            if (last[0] != null && now.distanceToSqr(last[0]) < 0.004 * 0.004) {
                still[0]++;
            } else {
                still[0] = 0;
            }
            last[0] = now;
            helper.assertTrue(still[0] < 12, "the ant stood still for " + still[0] + " ticks at cell "
                    + ant.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
        });
    }

    private static void watch(GameTestHelper helper, AbstractAntEntity ant) {
        TestWatch.watch(helper, ant, true);
    }

    private static List<String> relative(GameTestHelper helper, List<BlockPos> route) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return route.stream().map(pos -> pos.subtract(origin).toShortString()).toList();
    }
}
