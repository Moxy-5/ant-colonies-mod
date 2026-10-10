package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Checks the shape of the underground nest: three rooms, no overlaps, and a shaft bottom that keeps its walls. */
class NestPlanTest {
    private static final BlockPos BOTTOM = new BlockPos(100, 61, -40);

    @Test
    void thereAreThreeRoomsInOrderQueenNurseryFood() {
        List<NestPlan.Chamber> chambers = NestPlan.chambers(BOTTOM, Direction.EAST);
        assertEquals(3, chambers.size());
        assertEquals(NestPlan.Kind.QUEEN, chambers.get(0).kind());
        assertEquals(NestPlan.Kind.NURSERY, chambers.get(1).kind());
        assertEquals(NestPlan.Kind.FOOD, chambers.get(2).kind());
    }

    @Test
    void everyRoomHasATunnelAndA5x5RoomAndNothingOverlaps() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> all = new HashSet<>();
            for (NestPlan.Chamber chamber : NestPlan.chambers(BOTTOM, direction)) {
                assertEquals(3 + 25, chamber.cells().size());
                for (BlockPos cell : chamber.cells()) {
                    assertEquals(BOTTOM.getY(), cell.getY(), "everything is on the level of the shaft bottom");
                    assertTrue(all.add(cell), "cell " + cell + " is in two places (" + direction + ")");
                }
                assertTrue(chamber.cells().contains(chamber.center()), "the room's middle is part of the room");
            }
            assertEquals(3 * (3 + 25), all.size());
        }
    }

    @Test
    void everyBlockTouchesSpaceThatIsAlreadyDug() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> dug = new HashSet<>();
            dug.add(BOTTOM);
            for (NestPlan.Chamber chamber : NestPlan.chambers(BOTTOM, direction)) {
                for (BlockPos cell : chamber.cells()) {
                    boolean touches = false;
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        touches |= dug.contains(cell.relative(side));
                    }
                    assertTrue(touches, cell + " is dug before anything next to it is open (" + direction + ")");
                    dug.add(cell);
                }
            }
        }
    }

    @Test
    void theShaftBottomHasOnlyOneOpenSideSoItsWallsStayClimbable() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int touching = 0;
            for (NestPlan.Chamber chamber : NestPlan.chambers(BOTTOM, direction)) {
                for (BlockPos cell : chamber.cells()) {
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        if (cell.equals(BOTTOM.relative(side))) {
                            touching++;
                            assertEquals(direction, side);
                        }
                    }
                }
            }
            assertEquals(1, touching);
        }
    }

    @Test
    void differentRoomsNeverTouchEachOtherExceptWhereTheSideTunnelsJoinTheMainTunnel() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            List<NestPlan.Chamber> chambers = NestPlan.chambers(BOTTOM, direction);
            for (int a = 0; a < chambers.size(); a++) {
                for (int b = a + 1; b < chambers.size(); b++) {
                    for (int i = 0; i < chambers.get(a).cells().size(); i++) {
                        for (int j = 0; j < chambers.get(b).cells().size(); j++) {
                            BlockPos cellA = chambers.get(a).cells().get(i);
                            BlockPos cellB = chambers.get(b).cells().get(j);
                            // Allowed: the first block of a side tunnel (index 0) next to the main tunnel's second block (index 1).
                            boolean junction = a == 0 && i == NestPlan.BRANCH_AT - 1 && j == 0;
                            if (!junction) {
                                assertFalse(cellA.distManhattan(cellB) <= 1,
                                        "rooms " + a + " and " + b + " touch at " + cellA + " / " + cellB + " (" + direction + ")");
                            }
                        }
                    }
                }
            }
        }
    }
}
