package com.antcolonies.gametest;

import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.QueenAntEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
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
            AntColoniesMod.LOGGER.info("[nav-test] route found: {} -> {}", found,
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
            AntColoniesMod.LOGGER.info("[nav-test] route found: {} -> {}", found,
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

    private static void buildNest(GameTestHelper helper) {
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        int bottom = SURFACE - 3;
        for (int y = bottom; y < SURFACE; y++) {
            helper.setBlock(MIDDLE, y, MIDDLE, Blocks.AIR); // shaft
        }
        for (int i = 1; i <= 3; i++) {
            helper.setBlock(MIDDLE + i, bottom, MIDDLE, Blocks.AIR); // tunnel
        }
        for (int row = 1; row <= 5; row++) {
            for (int side = -2; side <= 2; side++) {
                helper.setBlock(MIDDLE + 3 + row, bottom, MIDDLE + side, Blocks.AIR); // room
            }
        }
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
                AntColoniesMod.LOGGER.info("[nav-test] t{} pos {},{},{} cell {} next {} climbing={} collided={} done={} failed={}",
                        helper.getTick(), r(at.x), r(at.y), r(at.z),
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
