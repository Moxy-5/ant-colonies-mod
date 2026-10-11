package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Checks the shape of the underground nest: four stacked levels, a spiral staircase, no overlaps, walls between the levels. */
class NestPlanTest {
    private static final BlockPos BOTTOM = new BlockPos(100, 61, -40);

    private static List<NestPlan.Chamber> full(Direction direction) {
        return NestPlan.chambers(BOTTOM, direction, NestPlan.MAX_LEVELS);
    }

    @Test
    void fourLevelsInOrderSeedsNurseryQueenRefuse() {
        List<NestPlan.Chamber> chambers = full(Direction.EAST);
        assertEquals(4, chambers.size());
        assertEquals(NestPlan.Kind.FOOD, chambers.get(0).kind());
        assertEquals(NestPlan.Kind.NURSERY, chambers.get(1).kind());
        assertEquals(NestPlan.Kind.QUEEN, chambers.get(2).kind());
        assertEquals(NestPlan.Kind.REFUSE, chambers.get(3).kind());
    }

    @Test
    void fewerLevelsAlwaysEndInTheQueensRoom() {
        for (int levels = 1; levels <= NestPlan.MAX_LEVELS; levels++) {
            List<NestPlan.Chamber> chambers = NestPlan.chambers(BOTTOM, Direction.NORTH, levels);
            assertEquals(levels, chambers.size());
            int queens = 0;
            for (NestPlan.Chamber chamber : chambers) {
                if (chamber.kind() == NestPlan.Kind.QUEEN) {
                    queens++;
                }
            }
            assertEquals(1, queens, levels + " levels");
            // The shorter nests are the top of the full one: the same cells, only the use of the levels differs.
            for (int i = 0; i < levels; i++) {
                assertEquals(full(Direction.NORTH).get(i).cells(), chambers.get(i).cells());
            }
        }
    }

    @Test
    void everyLevelIsOneRoomOf5x5OnItsOwnFloorTwoBlocksUnderTheLevelAbove() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> all = new HashSet<>();
            List<NestPlan.Chamber> chambers = full(direction);
            for (int level = 0; level < chambers.size(); level++) {
                NestPlan.Chamber chamber = chambers.get(level);
                int stairs = level == 0 ? 0 : NestPlan.STAIR_LENGTH;
                assertEquals(stairs + 3 + 25, chamber.cells().size());
                assertEquals(25, chamber.room().size());
                int floor = BOTTOM.getY() - NestPlan.LEVEL_SPACING * level;
                for (BlockPos cell : chamber.room()) {
                    assertEquals(floor, cell.getY(), "the room is flat and 1 block tall");
                }
                assertEquals(floor, chamber.center().getY());
                assertEquals(floor, chamber.tunnelEnd().getY());
                assertTrue(chamber.room().contains(chamber.center()), "the room's middle is part of the room");
                for (BlockPos cell : chamber.cells()) {
                    assertTrue(all.add(cell), "cell " + cell + " is in two places (" + direction + ")");
                }
            }
            assertEquals(3 * NestPlan.STAIR_LENGTH + 4 * (3 + 25), all.size());
            assertTrue(!all.contains(BOTTOM), "the shaft bottom is not part of any level");
        }
    }

    @Test
    void everyBlockTouchesSpaceThatIsAlreadyDug() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> dug = new HashSet<>();
            dug.add(BOTTOM);
            for (NestPlan.Chamber chamber : full(direction)) {
                for (BlockPos cell : chamber.cells()) {
                    boolean touches = false;
                    for (Direction side : Direction.values()) {
                        touches |= dug.contains(cell.relative(side));
                    }
                    assertTrue(touches, cell + " is dug before anything next to it is open (" + direction + ")");
                    dug.add(cell);
                }
            }
        }
    }

    @Test
    void theWayInIsAWalkableChainFromTheShaftBottomToTheRoom() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (NestPlan.Chamber chamber : full(direction)) {
                Set<BlockPos> open = new HashSet<>(chamber.wayIn());
                open.add(BOTTOM);
                // flood fill from the shaft bottom through the way in: it must reach the end of the tunnel
                Set<BlockPos> seen = new HashSet<>();
                List<BlockPos> queue = new java.util.ArrayList<>(List.of(BOTTOM));
                seen.add(BOTTOM);
                for (int i = 0; i < queue.size(); i++) {
                    for (Direction side : Direction.values()) {
                        BlockPos next = queue.get(i).relative(side);
                        if (open.contains(next) && seen.add(next)) {
                            queue.add(next);
                        }
                    }
                }
                assertTrue(seen.contains(chamber.tunnelEnd()), chamber.kind() + " cannot be reached through its way in (" + direction + ")");
            }
        }
    }

    @Test
    void theStairsWindRoundOneQuarterTurnEachAndNeverPointAtTheRooms() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            List<NestPlan.Chamber> chambers = full(direction);
            BlockPos hub = BOTTOM;
            Direction expected = direction.getClockWise();
            for (int level = 1; level < chambers.size(); level++) {
                BlockPos first = chambers.get(level).cells().get(0);
                assertEquals(hub.relative(expected), first, "stair " + level + " leaves the hub of the level above");
                assertTrue(expected != direction, "a stair never leaves toward the rooms");
                BlockPos bottom = chambers.get(level).cells().get(NestPlan.STAIR_LENGTH - 1);
                assertEquals(first.below(NestPlan.LEVEL_SPACING), bottom);
                hub = bottom;
                expected = expected.getClockWise();
            }
        }
    }

    @Test
    void theShaftBottomKeepsAtLeastTwoSolidWallsSoItsWallsStayClimbable() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> cells = new HashSet<>();
            for (NestPlan.Chamber chamber : full(direction)) {
                cells.addAll(chamber.cells());
            }
            int open = 0;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (cells.contains(BOTTOM.relative(side))) {
                    open++;
                }
            }
            assertEquals(2, open, "the tunnel and the first stair");
            assertTrue(!cells.contains(BOTTOM.below()), "nothing is dug under the shaft");
        }
    }

    @Test
    void differentLevelsOnlyTouchWhereAStairJoinsThem() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            List<NestPlan.Chamber> chambers = full(direction);
            Map<BlockPos, Integer> owner = new HashMap<>();
            owner.put(BOTTOM, -1); // the shaft
            for (int level = 0; level < chambers.size(); level++) {
                for (BlockPos cell : chambers.get(level).cells()) {
                    owner.put(cell, level);
                }
            }
            int crossings = 0;
            for (Map.Entry<BlockPos, Integer> entry : owner.entrySet()) {
                for (Direction side : Direction.values()) {
                    BlockPos neighbour = entry.getKey().relative(side);
                    Integer other = owner.get(neighbour);
                    if (other == null || other.equals(entry.getValue()) || entry.getValue() > other) {
                        continue; // count every touching pair once, from its upper level
                    }
                    crossings++;
                    // The upper cell must be the hub (the shaft bottom, or the end of the stair above), and the lower one
                    // the first block of the next stair or the first block of the top tunnel.
                    BlockPos hub = entry.getValue() < 1 ? BOTTOM : chambers.get(entry.getValue()).cells().get(NestPlan.STAIR_LENGTH - 1);
                    assertEquals(hub, entry.getKey(), "levels " + entry.getValue() + " and " + other + " touch at " + entry.getKey() + " (" + direction + ")");
                }
            }
            // shaft bottom + first tunnel block, shaft bottom + first block of stair 1, and one more stair for each deeper level
            assertEquals(1 + 3, crossings, direction.toString());
        }
    }
}
