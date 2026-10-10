package com.antcolonies.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Tests the route planner on a pretend nest: flat ground (surface cells at y = 64, ground blocks at
 * y = 63 and below), a 3-deep shaft at x = 0, z = 0, a tunnel going east from its bottom and a
 * 5x5 room at the end of the tunnel. Everything is a full solid block or open air.
 */
class AntPathFinderTest {
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final BlockPos SHAFT_BOTTOM = CENTER.below(3);

    private final Set<BlockPos> solid = new HashSet<>();
    private final CellMap map = new CellMap() {
        @Override
        public boolean isOpen(BlockPos pos) {
            return !solid.contains(pos);
        }

        @Override
        public boolean isSolidFace(BlockPos pos, Direction face) {
            return solid.contains(pos);
        }
    };

    @BeforeEach
    void buildNest() {
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                for (int y = 40; y <= 63; y++) {
                    solid.add(new BlockPos(x, y, z));
                }
            }
        }
        for (int i = 1; i <= 3; i++) {
            solid.remove(CENTER.below(i)); // shaft
        }
        for (int i = 1; i <= 3; i++) {
            solid.remove(SHAFT_BOTTOM.east(i)); // tunnel
        }
        for (int row = 1; row <= 5; row++) {
            for (int side = -2; side <= 2; side++) {
                solid.remove(SHAFT_BOTTOM.east(3 + row).south(side)); // room
            }
        }
    }

    private List<BlockPos> route(BlockPos from, BlockPos to) {
        return AntPathFinder.find(map, from, to, AntPathFinder.DEFAULT_MAX_EXPANSIONS);
    }

    /** Every step of a route must be a move the rules allow. */
    private void assertLegal(List<BlockPos> route) {
        for (int i = 0; i + 1 < route.size(); i++) {
            BlockPos from = route.get(i);
            BlockPos to = route.get(i + 1);
            List<BlockPos> allowed = new ArrayList<>();
            AntPathFinder.neighbours(map, from, (cell, cost) -> allowed.add(cell));
            assertTrue(allowed.contains(to), "illegal step " + from + " -> " + to);
        }
    }

    @Test
    void routeFromTheFarCornerOfTheRoomToTheSurface() {
        BlockPos room = SHAFT_BOTTOM.east(8).south(2);
        BlockPos moundSpot = new BlockPos(3, 64, 2);
        List<BlockPos> route = route(room, moundSpot);
        assertNotNull(route, "the ant must find its way out of the room");
        assertEquals(room, route.get(0));
        assertEquals(moundSpot, route.get(route.size() - 1));
        assertLegal(route);
        // It has to go through the bottom of the shaft and up all three shaft cells.
        assertTrue(route.contains(SHAFT_BOTTOM));
        assertTrue(route.contains(CENTER.below(2)));
        assertTrue(route.contains(CENTER.below(1)));
    }

    @Test
    void routeFromTheSurfaceDownToTheRoom() {
        BlockPos surface = new BlockPos(2, 64, -3);
        BlockPos room = SHAFT_BOTTOM.east(8).north(2);
        List<BlockPos> route = route(surface, room);
        assertNotNull(route, "the ant must find its way down into the room");
        assertLegal(route);
        assertTrue(route.contains(SHAFT_BOTTOM));
    }

    @Test
    void routeAlongTheTunnelStandsOnTheFloorTheWholeWay() {
        List<BlockPos> route = route(SHAFT_BOTTOM, SHAFT_BOTTOM.east(3));
        assertNotNull(route);
        assertEquals(4, route.size());
        for (BlockPos pos : route) {
            assertEquals(SHAFT_BOTTOM.getY(), pos.getY());
        }
    }

    @Test
    void routeAcrossFlatGroundIsStraight() {
        List<BlockPos> route = route(new BlockPos(-5, 64, 0), new BlockPos(-5, 64, 6));
        assertNotNull(route);
        assertEquals(7, route.size());
        assertLegal(route);
    }

    @Test
    void anAntClimbsOverAOneBlockStep() {
        solid.add(new BlockPos(5, 64, 5));
        List<BlockPos> route = route(new BlockPos(4, 64, 5), new BlockPos(6, 64, 5));
        // Either around the step or over it: both are fine, as long as there is a legal route.
        assertNotNull(route);
        assertLegal(route);
    }

    @Test
    void aSealedPocketOfAirCannotBeReached() {
        BlockPos pocket = new BlockPos(10, 50, 10);
        solid.remove(pocket);
        assertNull(route(new BlockPos(0, 64, 5), pocket));
    }

    @Test
    void theGoalMustBeOpenSpace() {
        assertNull(route(new BlockPos(0, 64, 5), new BlockPos(0, 63, 5)));
    }

    @Test
    void anAntDropsDownTheShaftWithoutClimbing() {
        List<BlockPos> route = route(CENTER, SHAFT_BOTTOM);
        assertNotNull(route);
        assertLegal(route);
        assertFalse(route.isEmpty());
        assertEquals(4, route.size()); // center, then three cells straight down
    }
}
