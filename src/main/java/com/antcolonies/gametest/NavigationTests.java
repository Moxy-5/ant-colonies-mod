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
    private static final int SURFACE = 8;
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

    /** The three rooms of the ready-made nest: the queen's room (east of the shaft), the nursery and the food store. */
    private static List<NestPlan.Chamber> chambers(GameTestHelper helper) {
        BlockPos bottom = helper.absolutePos(new BlockPos(MIDDLE, SURFACE - 3, MIDDLE));
        return NestPlan.chambers(bottom, Direction.EAST);
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
    public static void antWalksFromTheFoodStoreUpToTheSurface(GameTestHelper helper) {
        buildNest(helper);
        BlockPos start = chambers(helper).get(2).center().subtract(helper.absolutePos(BlockPos.ZERO));
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), start.getX(), start.getY(), start.getZ());
        BlockPos goal = helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE, MIDDLE + 2));
        helper.runAfterDelay(10, () -> ant.getNavigator().moveTo(goal, 1.0));
        watch(helper, ant);
        helper.succeedWhen(() -> helper.assertTrue(
                ant.getNavigator().isDone() && !ant.getNavigator().hasFailed() && ant.blockPosition().equals(goal) && helper.getTick() > 20,
                "the ant has not climbed out of the food store yet"));
    }

    /** Logs the ant's position and what its walker is doing every 4 ticks. */
    /** Most climbing ticks in a row an ant may hang in mid-air, touching nothing (a short moment at the very top is fine). */
    private static final int MAX_FLOATING_TICKS = 6;

    private static void watch(GameTestHelper helper, AbstractAntEntity ant) {
        Vec3 origin = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO));
        int[] floating = {0};
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
