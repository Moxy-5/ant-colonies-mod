package com.antcolonies.gametest;

import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.QueenAntEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Watches one ant at a time walk through a ready-made nest: a 3-deep shaft in the middle of the
 * test area, a tunnel going east from its bottom and a 5x5 room at the end.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class NavigationTests {
    private static final int SURFACE = 12;
    private static final int MIDDLE = 12;

    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antWalksFromTheRoomUpTheShaftToTheSurface(GameTestHelper helper) {
        buildNest(helper);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE + 8, SURFACE - 3, MIDDLE + 2);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 2));
        helper.runAfterDelay(10, () -> {
            boolean found = ant.getNavigator().moveTo(goal, 1.0);
            AntColoniesMod.LOGGER.info("[nav-test] #{} route found: {} -> {}", ant.getId(), found,
                    ant.getNavigator().getRoute() == null ? "none" : relative(helper, ant.getNavigator().getRoute()));
        });
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed()
                        && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not reached the surface yet"));
    }

    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antWalksFromTheSurfaceDownToTheRoom(GameTestHelper helper) {
        buildNest(helper);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE + 2, SURFACE, MIDDLE - 3);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 8, SURFACE - 3, MIDDLE - 2));
        helper.runAfterDelay(10, () -> {
            boolean found = ant.getNavigator().moveTo(goal, 1.0);
            AntColoniesMod.LOGGER.info("[nav-test] #{} route found: {} -> {}", ant.getId(), found,
                    ant.getNavigator().getRoute() == null ? "none" : relative(helper, ant.getNavigator().getRoute()));
        });
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed()
                        && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not reached the room yet"));
    }

    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void queenWalksFromTheRoomUpTheShaftToTheSurface(GameTestHelper helper) {
        buildNest(helper);
        QueenAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.QUEEN_ANT.get(), MIDDLE + 8, SURFACE - 3, MIDDLE + 2);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 2));
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the queen has not reached the surface yet"));
    }

    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void queenWalksFromTheSurfaceDownToTheRoom(GameTestHelper helper) {
        buildNest(helper);
        QueenAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.QUEEN_ANT.get(), MIDDLE + 2, SURFACE, MIDDLE - 3);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 8, SURFACE - 3, MIDDLE - 2));
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the queen has not reached the room yet"));
    }

    /** Regression test for a crash: the route was only the cell the ant already stood in, and the ant then left that cell. */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void antToldToGoToItsOwnCellSurvivesBeingMovedAway(GameTestHelper helper) {
        buildNest(helper);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE + 5, SURFACE, MIDDLE + 5);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 5, SURFACE, MIDDLE + 5));
        helper.runAfterDelay(10, () -> {
            ant.getNavigator().moveTo(ant.blockPosition(), 1.0);
            ant.setPos(ant.getX() + 1.6, ant.getY(), ant.getZ()); // out of the cell before the first step
        });
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not walked back to its cell yet"));
    }

    /** With the finished hill around the entrance the hole is a funnel 3 blocks deeper: the ant must still climb out of it. */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antClimbsOutOfTheHoleThroughTheFinishedHill(GameTestHelper helper) {
        buildNest(helper);
        buildHill(helper);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE + 8, SURFACE - 3, MIDDLE + 2);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE - 5, SURFACE, MIDDLE - 2));
        helper.runAfterDelay(10, () -> {
            boolean found = ant.getNavigator().moveTo(goal, 1.0);
            AntColoniesMod.LOGGER.info("[nav-test] #{} route found: {} -> {}", ant.getId(), found,
                    ant.getNavigator().getRoute() == null ? "none" : relative(helper, ant.getNavigator().getRoute()));
        });
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed()
                        && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not climbed out over the hill yet"));
    }

    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antWalksDownIntoTheHoleThroughTheFinishedHill(GameTestHelper helper) {
        buildNest(helper);
        buildHill(helper);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE - 5, SURFACE, MIDDLE - 2);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 8, SURFACE - 3, MIDDLE - 2));
        helper.runAfterDelay(10, () -> {
            boolean found = ant.getNavigator().moveTo(goal, 1.0);
            AntColoniesMod.LOGGER.info("[nav-test] #{} route found: {} -> {}", ant.getId(), found,
                    ant.getNavigator().getRoute() == null ? "none" : relative(helper, ant.getNavigator().getRoute()));
        });
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed()
                        && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not gone down into the room yet"));
    }

    /** A one-block pit in the ground: an ant at the bottom must climb out over its wall. */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void antClimbsOutOfAOneBlockPit(GameTestHelper helper) {
        buildNest(helper);
        helper.setBlock(MIDDLE + 6, SURFACE - 1, MIDDLE + 9, Blocks.AIR);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE + 6.5F, (float) (SURFACE - 1), MIDDLE + 9.5F);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 9));
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not climbed out of the pit yet"));
    }

    /** The same, but the route is planned while the ant is still in the air above the pit (it falls in on its way). */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void antPlanningAboveAPitFallsInAndClimbsOut(GameTestHelper helper) {
        buildNest(helper);
        helper.setBlock(MIDDLE + 6, SURFACE - 1, MIDDLE + 9, Blocks.AIR);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE + 6.5F, (float) SURFACE, MIDDLE + 9.5F);
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 9));
        helper.runAfterDelay(1, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not climbed out of the pit yet"));
    }

    /**
     * One ant on every surface cell (flat ground and every step of the hill) around the finished hill, all sent into the
     * food store at the same time: every one of them must get there, and none may take much longer than the others.
     */
    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true)
    public static void antsFromAllAroundTheHillReachTheFoodStore(GameTestHelper helper) {
        scan(helper, 1L, false, false, false);
    }

    /** The same, but every ant starts somewhere inside its cell instead of in the middle. */
    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true)
    public static void antsStartingAnywhereInTheirCellReachTheFoodStore(GameTestHelper helper) {
        scan(helper, 2L, true, false, false);
    }

    /** The same on bumpy ground: single blocks piled on the grass and single grass blocks missing. */
    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true)
    public static void antsOnBumpyGroundReachTheFoodStore(GameTestHelper helper) {
        scan(helper, 3L, true, true, false);
    }

    /** The same with a hill that is only partly built: some blocks of its top layers are missing. */
    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true)
    public static void antsOverAHalfBuiltHillReachTheFoodStore(GameTestHelper helper) {
        scan(helper, 4L, true, false, true);
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

    private static void scan(GameTestHelper helper, long seed, boolean anywhereInCell, boolean bumpy, boolean gappyHill) {
        helper.getLevel().getServer().tickRateManager().setTickRate(2000.0F);
        buildNest(helper);
        java.util.Random random = new java.util.Random(seed);
        if (bumpy) {
            for (int dx = -11; dx <= 11; dx++) {
                for (int dz = -11; dz <= 11; dz++) {
                    if (Math.abs(dx) <= Colony.MOUND_RADIUS + 1 && Math.abs(dz) <= Colony.MOUND_RADIUS + 1) {
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
            for (int dx = -Colony.MOUND_RADIUS; dx <= Colony.MOUND_RADIUS; dx++) {
                for (int dz = -Colony.MOUND_RADIUS; dz <= Colony.MOUND_RADIUS; dz++) {
                    int height = Colony.moundHeightAt(dx, dz);
                    if (height > 1 && random.nextDouble() < 0.35) {
                        helper.setBlock(MIDDLE + dx, SURFACE + height - 1, MIDDLE + dz, Blocks.AIR);
                    }
                }
            }
        }
        BlockPos goal = chambers(helper).get(0).center();
        List<WorkerAntEntity> ants = new java.util.ArrayList<>();
        List<String> starts = new java.util.ArrayList<>();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // the hole itself
                }
                int y = standingY(helper, MIDDLE + dx, MIDDLE + dz);
                float fx = MIDDLE + dx + (anywhereInCell ? 0.2F + 0.6F * random.nextFloat() : 0.5F);
                float fz = MIDDLE + dz + (anywhereInCell ? 0.2F + 0.6F * random.nextFloat() : 0.5F);
                ants.add(helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), fx, (float) y, fz));
                starts.add(dx + "," + dz + " y" + y);
            }
        }
        long[] arrived = new long[ants.size()];
        helper.runAfterDelay(10, () -> {
            for (WorkerAntEntity ant : ants) {
                ant.getNavigator().moveTo(goal, 1.0);
            }
        });
        helper.onEachTick(() -> {
            if (helper.getTick() <= 10) {
                return;
            }
            for (int i = 0; i < ants.size(); i++) {
                WorkerAntEntity ant = ants.get(i);
                if (arrived[i] == 0 && ant.getNavigator().isDone() && ant.blockPosition().equals(goal)) {
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
                    AntColoniesMod.LOGGER.info("[scan {}] ant from {} {} (arrived at tick {}, now at {}, route failed {})", seed, starts.get(i),
                            arrived[i] == 0 ? "NEVER ARRIVED" : "was slow", arrived[i],
                            ants.get(i).position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))),
                            ants.get(i).getNavigator().hasFailed());
                    var navigator = ants.get(i).getNavigator();
                    AntColoniesMod.LOGGER.info("[scan {}]   cell {} next waypoint {} climbing {} route {}", seed,
                            ants.get(i).blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString(), navigator.getNextWaypoint(),
                            navigator.isClimbing(), navigator.getRoute() == null ? "none" : relative(helper, navigator.getRoute()));
                }
                slowest = Math.max(slowest, arrived[i]);
            }
            AntColoniesMod.LOGGER.info("[scan {}] {} ants, {} late or lost, slowest arrival at tick {}", seed, ants.size(), late, slowest);
            helper.assertTrue(late == 0, late + " of " + ants.size() + " ants were late or never arrived");
            helper.succeed();
        });
    }

    /** The complete ant hill around the entrance (the hole at MIDDLE, MIDDLE stays open). */
    private static void buildHill(GameTestHelper helper) {
        for (int dx = -Colony.MOUND_RADIUS; dx <= Colony.MOUND_RADIUS; dx++) {
            for (int dz = -Colony.MOUND_RADIUS; dz <= Colony.MOUND_RADIUS; dz++) {
                for (int h = 0; h < Colony.moundHeightAt(dx, dz); h++) {
                    helper.setBlock(MIDDLE + dx, SURFACE + h, MIDDLE + dz, ModBlocks.BURROWED_DIRT.get());
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
    public static void antWalksFromTheSurfaceDownToTheNursery(GameTestHelper helper) {
        buildNest(helper);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE - 3, SURFACE, MIDDLE + 3);
        BlockPos goal = chambers(helper).get(1).center();
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not reached the nursery yet"));
    }

    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antWalksFromTheSurfaceDownToTheDeepestRoom(GameTestHelper helper) {
        buildNest(helper);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), MIDDLE - 3, SURFACE, MIDDLE + 3);
        BlockPos goal = chambers(helper).get(3).center();
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not reached the deepest room yet"));
    }

    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void antWalksFromTheQueensRoomDownToTheRefuseChamberAndBack(GameTestHelper helper) {
        buildNest(helper);
        BlockPos start = chambers(helper).get(2).center().subtract(helper.absolutePos(BlockPos.ZERO));
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), start.getX(), start.getY(), start.getZ());
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
        buildNest(helper);
        BlockPos start = chambers(helper).get(3).center().subtract(helper.absolutePos(BlockPos.ZERO));
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), start.getX(), start.getY(), start.getZ());
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 2));
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not climbed out of the deepest room yet"));
    }

    /** Logs the ant's position and what its walker is doing every 4 ticks. */
    /** Most climbing ticks in a row an ant may hang in mid-air, touching nothing (a short moment at the very top is fine). */
    private static final int MAX_FLOATING_TICKS = 6;
    /** Most ticks in a row an ant may lean on a wall while standing on the floor without touching it (a moment at the very top is fine). */
    private static final int MAX_LEANING_TICKS = 8;

    private static void watch(GameTestHelper helper, AbstractAntEntity ant) {
        Vec3 origin = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO));
        int[] floating = {0};
        int[] leaning = {0};
        helper.onEachTick(() -> {
            // An ant standing on the floor away from any wall must walk, not lean against the wall: that looks like hovering.
            boolean leansOnTheFloor = ant.getNavigator().isClimbing() && ant.onGround() && !ant.horizontalCollision;
            leaning[0] = leansOnTheFloor ? leaning[0] + 1 : 0;
            helper.assertTrue(leaning[0] <= MAX_LEANING_TICKS, "the ant leans against a wall it has not reached");
        });
        helper.onEachTick(() -> {
            boolean hanging = ant.getNavigator().isClimbing()
                    && helper.getLevel().noCollision(ant, ant.getBoundingBox().inflate(0.08));
            floating[0] = hanging ? floating[0] + 1 : 0;
            helper.assertTrue(floating[0] <= MAX_FLOATING_TICKS, "the ant is floating while climbing");
        });
        helper.onEachTick(() -> {
            if (helper.getTick() % 4 == 0 && helper.getTick() >= 8) {
                Vec3 at = ant.position().subtract(origin);
                List<BlockPos> route = ant.getNavigator().getRoute();
                String next = route == null || ant.getNavigator().getNextWaypoint() >= route.size()
                        ? "-" : route.get(ant.getNavigator().getNextWaypoint()).subtract(BlockPos.containing(origin)).toShortString();
                AntColoniesMod.LOGGER.info("[nav-test] #{} t{} pos {},{},{} cell {} next {} climbing={} collided={} done={} failed={}",
                        ant.getId(), helper.getTick(), r(at.x), r(at.y), r(at.z),
                        ant.blockPosition().subtract(BlockPos.containing(origin)).toShortString(), next,
                        ant.getNavigator().isClimbing(), ant.horizontalCollision,
                        ant.getNavigator().isDone(), ant.getNavigator().hasFailed());
            }
        });
    }

    private static String r(double value) {
        return String.format("%.3f", value);
    }

    private static List<String> relative(GameTestHelper helper, List<BlockPos> route) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return route.stream().map(pos -> pos.subtract(origin).toShortString()).toList();
    }
}
