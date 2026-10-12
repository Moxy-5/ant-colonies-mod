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
 * 5x5 room at the end of the tunnel. Everything is a full solid block or open air, except blocks in
 * {@link #noSides}, whose sides are not solid (nothing to climb on them, like a fence).
 */
class AntPathFinderTest {
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final BlockPos SHAFT_BOTTOM = CENTER.below(3);

    private final Set<BlockPos> solid = new HashSet<>();
    private final Set<BlockPos> noSides = new HashSet<>();
    private final CellMap map = new CellMap() {
        @Override
        public boolean isOpen(BlockPos pos) {
            return !solid.contains(pos);
        }

        @Override
        public boolean isSolidFace(BlockPos pos, Direction face) {
            return solid.contains(pos) && (face.getAxis().isVertical() || !noSides.contains(pos));
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

    private Route route(BlockPos from, BlockPos to) {
        return AntPathFinder.find(map, from, to, AntPathFinder.DEFAULT_MAX_EXPANSIONS);
    }

    /** Every step of a route must be a move the rules allow, onto a surface that can be held. */
    private void assertLegal(Route route) {
        for (int i = 0; i + 1 < route.size(); i++) {
            Foothold from = route.step(i).to();
            Route.Step step = route.step(i + 1);
            assertTrue(step.to().isValid(map), "nothing to hold on to at " + step.to());
            List<Route.Step> allowed = new ArrayList<>();
            AntPathFinder.neighbours(map, from, (to, kind, cost) -> allowed.add(new Route.Step(to, kind)));
            assertTrue(allowed.contains(step), "illegal step " + from + " -> " + step + " in " + route);
        }
    }

    private static boolean has(Route route, StepKind kind) {
        return route.steps().stream().anyMatch(step -> step.kind() == kind);
    }

    @Test
    void routeFromTheFarCornerOfTheRoomToTheSurface() {
        BlockPos room = SHAFT_BOTTOM.east(8).south(2);
        BlockPos moundSpot = new BlockPos(3, 64, 2);
        Route route = route(room, moundSpot);
        assertNotNull(route, "the ant must find its way out of the room");
        assertEquals(Foothold.floor(room), route.start());
        assertEquals(Foothold.floor(moundSpot), route.end());
        assertLegal(route);
        // It has to go through the bottom of the shaft and up all three shaft cells, climbing, and out over the top.
        List<BlockPos> cells = route.cells();
        assertTrue(cells.contains(SHAFT_BOTTOM));
        assertTrue(cells.contains(CENTER.below(2)));
        assertTrue(cells.contains(CENTER.below(1)));
        assertTrue(has(route, StepKind.CLIMB_UP) && has(route, StepKind.OVER_TOP), route.toString());
        assertFalse(route.hasFall());
    }

    @Test
    void routeFromTheSurfaceDownToTheRoomClimbsDownHeadFirst() {
        BlockPos surface = new BlockPos(2, 64, -3);
        BlockPos room = SHAFT_BOTTOM.east(8).north(2);
        Route route = route(surface, room);
        assertNotNull(route, "the ant must find its way down into the room");
        assertLegal(route);
        assertTrue(route.cells().contains(SHAFT_BOTTOM));
        assertTrue(has(route, StepKind.OVER_EDGE) && has(route, StepKind.CLIMB_DOWN), route.toString());
        assertFalse(route.hasFall(), "it must climb down, not drop: " + route);
        assertEquals(Foothold.floor(room), route.end());
    }

    @Test
    void anAntClimbsDownTheShaftAndStepsOntoItsFloor() {
        Route route = route(new BlockPos(1, 64, 0), SHAFT_BOTTOM);
        assertNotNull(route);
        assertLegal(route);
        assertFalse(route.hasFall());
        assertEquals(Foothold.floor(SHAFT_BOTTOM), route.end(), "a goal cell with a floor is reached on its floor");
        assertEquals(StepKind.OVER_EDGE, route.step(1).kind());
        assertEquals(StepKind.OFF_WALL, route.step(route.size() - 1).kind());
    }

    @Test
    void routeAlongTheTunnelStandsOnTheFloorTheWholeWay() {
        Route route = route(SHAFT_BOTTOM, SHAFT_BOTTOM.east(3));
        assertNotNull(route);
        assertEquals(4, route.size());
        for (Route.Step step : route.steps()) {
            assertTrue(step.to().isFloor());
            assertEquals(SHAFT_BOTTOM.getY(), step.to().cell().getY());
        }
    }

    @Test
    void routeAcrossFlatGroundIsStraight() {
        Route route = route(new BlockPos(-5, 64, 0), new BlockPos(-5, 64, 6));
        assertNotNull(route);
        assertEquals(7, route.size());
        assertLegal(route);
    }

    @Test
    void anAntClimbsOverAOneBlockStep() {
        solid.add(new BlockPos(5, 64, 5));
        Route route = route(new BlockPos(4, 64, 5), new BlockPos(6, 64, 5));
        // Either around the step or over it: both are fine, as long as there is a legal route.
        assertNotNull(route);
        assertLegal(route);
    }

    @Test
    void anAntOnOneShaftWallGetsToTheOtherSideOverTheWalls() {
        // Hanging on the west wall at the top of the shaft, it has to climb out over the east side.
        Foothold west = new Foothold(CENTER.below(), Direction.WEST);
        Route route = AntPathFinder.find(map, west, new BlockPos(3, 64, 0), AntPathFinder.DEFAULT_MAX_EXPANSIONS);
        assertNotNull(route);
        assertLegal(route);
        assertTrue(has(route, StepKind.TURN_CORNER), "it turns the corners onto the east wall: " + route);
        assertFalse(route.hasFall());
    }

    @Test
    void anAntGoesRoundTheOutsideEdgeOfAPillar() {
        // A pillar two blocks high on flat ground; the ant holds its north face and must reach its east face, one up.
        BlockPos pillar = new BlockPos(10, 64, 10);
        solid.add(pillar);
        solid.add(pillar.above());
        Foothold north = new Foothold(pillar.north().above(), Direction.SOUTH);
        Route route = AntPathFinder.find(map, List.of(north), at -> at.equals(new Foothold(pillar.east().above(), Direction.WEST)),
                pillar.east().above(), AntPathFinder.DEFAULT_MAX_EXPANSIONS, AntPathFinder.StepCosts.NONE);
        assertNotNull(route);
        assertLegal(route);
        assertTrue(has(route, StepKind.ROUND_EDGE), route.toString());
    }

    @Test
    void anAntOnlyFallsWhereThereIsNothingToClimbDown() {
        // A platform of blocks with no sides to climb (like fences), two blocks above the ground.
        for (int x = 5; x <= 6; x++) {
            BlockPos block = new BlockPos(x, 65, -10);
            solid.add(block);
            noSides.add(block);
        }
        Route route = route(new BlockPos(5, 66, -10), new BlockPos(5, 64, -12));
        assertNotNull(route);
        assertLegal(route);
        assertTrue(route.hasFall(), route.toString());
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
    void aSharedRouteKeepsItsSteps() {
        Route route = route(SHAFT_BOTTOM.east(5), new BlockPos(4, 64, 4));
        assertNotNull(route);
        assertEquals(StepKind.START, route.step(0).kind());
        assertEquals(route.size(), route.steps().size());
        assertTrue(route.cost() > 0);
    }
}
