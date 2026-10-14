package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** The tunnel route planner for new holes near food ({@link TunnelRoute}), on pretend ground. */
class TunnelRouteTest {
    /** Pretend ground: the top ground block of each column from {@code height}; columns in {@code stone} cannot be dug. */
    private static TunnelRoute.Ground ground(IntBinaryOperator height, Set<Long> stone) {
        return new TunnelRoute.Ground() {
            @Override
            public Integer groundTop(int x, int z) {
                return height.applyAsInt(x, z);
            }

            @Override
            public boolean mayDig(BlockPos cell) {
                return cell.getY() <= height.applyAsInt(cell.getX(), cell.getZ()) && !stone.contains(column(cell.getX(), cell.getZ()));
            }

            @Override
            public boolean mayOpenHole(BlockPos hole) {
                return hole.getY() == height.applyAsInt(hole.getX(), hole.getZ()) && mayDig(hole);
            }
        };
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Every block touches the one before (the first touches the start), and every block is under the ground. */
    private static void assertJoinedAndUnderground(BlockPos start, TunnelRoute.Plan plan, IntBinaryOperator height) {
        BlockPos before = start;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos cell : plan.cells()) {
            assertEquals(1, before.distManhattan(cell), "a tunnel block does not touch the one before: " + before + " to " + cell);
            assertTrue(cell.getY() < height.applyAsInt(cell.getX(), cell.getZ()), "a tunnel block is not under the ground: " + cell);
            assertTrue(seen.add(cell), "a block is dug twice: " + cell);
            before = cell;
        }
        assertEquals(before.above(), plan.hole(), "the hole is not over the tunnel's end");
    }

    @Test
    void onFlatGroundTheTunnelRunsStraightToBesideTheFood() {
        IntBinaryOperator flat = (x, z) -> 64;
        BlockPos start = new BlockPos(0, 63, 0);
        BlockPos food = new BlockPos(20, 65, 0);
        TunnelRoute.Plan plan = TunnelRoute.plan(start, food, ground(flat, Set.of()), 60);
        assertNotNull(plan);
        assertJoinedAndUnderground(start, plan, flat);
        assertEquals(18, plan.cells().size(), "the shortest way to 2 blocks short of the food");
        assertEquals(new BlockPos(18, 64, 0), plan.hole());
    }

    @Test
    void theTunnelFollowsTheGroundUpAndDown() {
        // A hill: the ground rises a block every 3 columns up to 6 blocks, then falls again.
        IntBinaryOperator hill = (x, z) -> 64 + Math.max(0, 6 - Math.abs(x - 18) / 3);
        BlockPos start = new BlockPos(0, hill.applyAsInt(0, 0) - 1, 0);
        BlockPos food = new BlockPos(36, hill.applyAsInt(36, 0) + 1, 0);
        TunnelRoute.Plan plan = TunnelRoute.plan(start, food, ground(hill, Set.of()), 80);
        assertNotNull(plan);
        assertJoinedAndUnderground(start, plan, hill);
    }

    @Test
    void stoneIsGoneRoundAndAWallOfItBlocksTheWay() {
        IntBinaryOperator flat = (x, z) -> 64;
        Set<Long> stone = new HashSet<>();
        for (int z = -3; z <= 3; z++) {
            stone.add(column(10, z));
        }
        BlockPos start = new BlockPos(0, 63, 0);
        BlockPos food = new BlockPos(20, 65, 0);
        TunnelRoute.Plan plan = TunnelRoute.plan(start, food, ground(flat, stone), 60);
        assertNotNull(plan, "round the stone");
        assertJoinedAndUnderground(start, plan, flat);
        assertTrue(plan.cells().size() > 18, "it went round");
        for (int z = -40; z <= 40; z++) {
            stone.add(column(10, z));
        }
        assertNull(TunnelRoute.plan(start, food, ground(flat, stone), 60), "a wall of stone longer than the tunnel may be");
    }

    @Test
    void aBoxedInStartIsPassedOverForAnotherOne() {
        IntBinaryOperator flat = (x, z) -> 64;
        Set<Long> stone = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x != 0 || z != 0) {
                    stone.add(column(x, z)); // (like the mound round the shaft)
                }
            }
        }
        BlockPos boxedIn = new BlockPos(0, 63, 0);
        BlockPos free = new BlockPos(0, 63, 8);
        BlockPos food = new BlockPos(20, 65, 0);
        assertNull(TunnelRoute.plan(boxedIn, food, ground(flat, stone), 60));
        TunnelRoute.Plan plan = TunnelRoute.plan(List.of(boxedIn, free), food, ground(flat, stone), 60);
        assertNotNull(plan);
        assertJoinedAndUnderground(free, plan, flat);
    }

    @Test
    void aCliffTooHighIsNotDugUp() {
        IntBinaryOperator cliff = (x, z) -> x >= 10 ? 70 : 64;
        BlockPos start = new BlockPos(0, 63, 0);
        assertNull(TunnelRoute.plan(start, new BlockPos(20, 71, 0), ground(cliff, Set.of()), 80));
    }

    @Test
    void aStepIsDugUnderground() {
        assertEquals(List.of(new BlockPos(1, 63, 0), new BlockPos(1, 64, 0)), TunnelRoute.step(new BlockPos(0, 63, 0), new BlockPos(1, 64, 0)));
        assertEquals(List.of(new BlockPos(0, 62, 0), new BlockPos(1, 62, 0)), TunnelRoute.step(new BlockPos(0, 63, 0), new BlockPos(1, 62, 0)));
    }
}
