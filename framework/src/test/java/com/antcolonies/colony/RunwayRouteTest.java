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

/** The runway planner for mud sheeting ({@link RunwayRoute}), on pretend ground. */
class RunwayRouteTest {
    /** Pretend ground: the top ground block of each column from {@code height}; cells in {@code blocked} are taken. */
    private static RunwayRoute.Ground ground(IntBinaryOperator height, Set<BlockPos> blocked) {
        return new RunwayRoute.Ground() {
            @Override
            public Integer groundTop(int x, int z) {
                return height.applyAsInt(x, z);
            }

            @Override
            public boolean isFree(BlockPos cell) {
                return cell.getY() > height.applyAsInt(cell.getX(), cell.getZ()) && !blocked.contains(cell);
            }
        };
    }

    /** Every cell touches the one before face to face, none twice, and each is just over the ground or a climb cell. */
    private static void assertJoinedOverGround(BlockPos start, List<BlockPos> cells, IntBinaryOperator height) {
        assertEquals(start, cells.get(0), "the runway does not start over the hole");
        Set<BlockPos> seen = new HashSet<>();
        BlockPos before = null;
        for (BlockPos cell : cells) {
            if (before != null) {
                assertEquals(1, before.distManhattan(cell), "a runway cell does not touch the one before: " + before + " to " + cell);
            }
            int over = cell.getY() - height.applyAsInt(cell.getX(), cell.getZ());
            assertTrue(over == 1 || over == 2, "a runway cell floats: " + cell);
            assertTrue(seen.add(cell), "a cell twice: " + cell);
            before = cell;
        }
    }

    @Test
    void onFlatGroundTheRunwayRunsStraightToTheFood() {
        IntBinaryOperator flat = (x, z) -> 64;
        BlockPos start = new BlockPos(0, 65, 0);
        Set<BlockPos> log = Set.of(new BlockPos(6, 65, 0), new BlockPos(7, 65, 0));
        List<BlockPos> cells = RunwayRoute.plan(start, log, ground(flat, Set.of()), 12);
        assertNotNull(cells);
        assertJoinedOverGround(start, cells, flat);
        assertEquals(6, cells.size(), "the shortest way to beside the log");
        assertEquals(new BlockPos(5, 65, 0), cells.get(cells.size() - 1));
    }

    @Test
    void theRunwayClimbsAStepInsideItself() {
        // The ground is a block higher from x = 3 on.
        IntBinaryOperator step = (x, z) -> x >= 3 ? 65 : 64;
        BlockPos start = new BlockPos(0, 65, 0);
        Set<BlockPos> log = Set.of(new BlockPos(6, 66, 0));
        List<BlockPos> cells = RunwayRoute.plan(start, log, ground(step, Set.of()), 12);
        assertNotNull(cells);
        assertJoinedOverGround(start, cells, step);
        assertTrue(cells.contains(new BlockPos(2, 66, 0)), "no climb cell over the lower step: " + cells);
        assertEquals(new BlockPos(5, 66, 0), cells.get(cells.size() - 1));
    }

    @Test
    void theRunwayGoesRoundWhatIsInTheWayButNotTooFar() {
        IntBinaryOperator flat = (x, z) -> 64;
        BlockPos start = new BlockPos(0, 65, 0);
        Set<BlockPos> log = Set.of(new BlockPos(6, 65, 0));
        // A wall of taken cells across the straight way, 2 to each side.
        Set<BlockPos> wall = new HashSet<>();
        for (int z = -2; z <= 2; z++) {
            wall.add(new BlockPos(3, 65, z));
            wall.add(new BlockPos(3, 66, z));
        }
        List<BlockPos> cells = RunwayRoute.plan(start, log, ground(flat, wall), 12);
        assertNotNull(cells);
        assertJoinedOverGround(start, cells, flat);
        for (BlockPos cell : cells) {
            assertTrue(!wall.contains(cell), "the runway runs through a taken cell: " + cell);
        }
        // Too short a runway allowed: none.
        assertNull(RunwayRoute.plan(start, log, ground(flat, wall), 8));
    }

    @Test
    void aCliffTooHighIsNotClimbed() {
        // The ground is 2 blocks higher from x = 3 on: no runway climbs that.
        IntBinaryOperator cliff = (x, z) -> x >= 3 ? 66 : 64;
        BlockPos start = new BlockPos(0, 65, 0);
        Set<BlockPos> log = Set.of(new BlockPos(6, 67, 0));
        assertNull(RunwayRoute.plan(start, log, ground(cliff, Set.of()), 12));
    }
}
