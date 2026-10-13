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
 * {@link #noSides}, whose sides are not solid (nothing to climb on them, like a fence), and the straight ramps in
 * {@link #ramps}, rising toward their direction (slopes, as the termite mound's ramp block).
 */
class AntPathFinderTest {
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final BlockPos SHAFT_BOTTOM = CENTER.below(3);

    private final Set<BlockPos> solid = new HashSet<>();
    private final Set<BlockPos> noSides = new HashSet<>();
    private final java.util.Map<BlockPos, Direction> ramps = new java.util.HashMap<>();
    private final CellMap map = new CellMap() {
        @Override
        public boolean isOpen(BlockPos pos) {
            return !solid.contains(pos) && !ramps.containsKey(pos);
        }

        @Override
        public boolean isSolidFace(BlockPos pos, Direction face) {
            if (ramps.containsKey(pos)) {
                return face == Direction.DOWN || face == ramps.get(pos); // the bottom and the full high side
            }
            return solid.contains(pos) && (face.getAxis().isVertical() || !noSides.contains(pos));
        }

        @Override
        public int slopeHeight(BlockPos cell, int u, int w) {
            Direction high = ramps.get(cell);
            if (high == null) {
                return -1;
            }
            return 2 * switch (high) {
                case EAST -> u + 1;
                case WEST -> 8 - u;
                case SOUTH -> w + 1;
                default -> 8 - w;
            };
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

    /** The room's middle cell, one row in from the tunnel: a ceiling spot far from every wall. */
    private static final BlockPos ROOM_MIDDLE = SHAFT_BOTTOM.east(6);

    private Route routeTo(Foothold from, Foothold to) {
        return AntPathFinder.find(map, List.of(from), to::equals, to.cell(), AntPathFinder.DEFAULT_MAX_EXPANSIONS, AntPathFinder.StepCosts.NONE);
    }

    @Test
    void anAntClimbsUpTheRoomWallAndCrawlsUpsideDownToTheMiddleOfTheCeiling() {
        Route route = routeTo(Foothold.floor(SHAFT_BOTTOM.east(8).south(2)), Foothold.ceiling(ROOM_MIDDLE));
        assertNotNull(route);
        assertLegal(route);
        assertTrue(has(route, StepKind.ONTO_WALL) && has(route, StepKind.ONTO_CEILING) && has(route, StepKind.CRAWL_CEILING), route.toString());
        assertFalse(route.hasFall());
        assertEquals(Foothold.ceiling(ROOM_MIDDLE), route.end());
    }

    @Test
    void anAntHangingFromTheCeilingGetsBackToTheFloor() {
        Route route = routeTo(Foothold.ceiling(ROOM_MIDDLE), Foothold.floor(SHAFT_BOTTOM.east(8).north(2)));
        assertNotNull(route);
        assertLegal(route);
        assertEquals(Foothold.floor(SHAFT_BOTTOM.east(8).north(2)), route.end());
    }

    @Test
    void floorsBeatCeilingsWhenBothGoTheSameWay() {
        Route route = route(SHAFT_BOTTOM.east(4).south(2), SHAFT_BOTTOM.east(8).north(2));
        assertNotNull(route);
        for (Route.Step step : route.steps()) {
            assertTrue(step.to().isFloor(), "a plain walk across the room stays on the floor: " + route);
        }
    }

    @Test
    void anAntGoesUnderAnOverhangingBlockAndOutTheOtherSide() {
        // A block hanging in the air two blocks above the ground: from its north face, round its bottom edge onto its
        // underside, and from there round the far bottom edge and up its south face.
        BlockPos block = new BlockPos(10, 66, 10);
        solid.add(block);
        Foothold north = new Foothold(block.north(), Direction.SOUTH);
        Foothold under = Foothold.ceiling(block.below());
        Foothold south = new Foothold(block.south(), Direction.NORTH);
        Route in = routeTo(north, under);
        assertNotNull(in);
        assertLegal(in);
        assertTrue(has(in, StepKind.UNDER_EDGE), in.toString());
        Route out = routeTo(under, south);
        assertNotNull(out);
        assertLegal(out);
        assertTrue(has(out, StepKind.OUT_FROM_UNDER), out.toString());
        assertFalse(out.hasFall());
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

    // --- Slopes (ramps) ---

    /** A row of ground at z = 5: a step {@code height} blocks tall at x = 6, and a ramp in front of it at x = 5 rising east. */
    private void stepWithARamp(int height) {
        for (int y = 0; y < height; y++) {
            solid.add(new BlockPos(6, 64 + y, 5));
        }
        ramps.put(new BlockPos(5, 64, 5), Direction.EAST);
    }

    private static boolean onlyWalks(Route route) {
        return route.steps().stream().allMatch(step -> step.kind() == StepKind.START || step.kind() == StepKind.WALK);
    }

    @Test
    void upARampOntoAStepAndBackDownWithoutClimbing() {
        stepWithARamp(1);
        Route up = route(new BlockPos(2, 64, 5), new BlockPos(6, 65, 5));
        assertNotNull(up, "no way up the ramp");
        assertLegal(up);
        assertTrue(onlyWalks(up), "up a ramp is walking: " + up);
        assertTrue(up.cells().contains(new BlockPos(5, 64, 5)), "it does not use the ramp: " + up);
        Route down = route(new BlockPos(6, 65, 5), new BlockPos(2, 64, 5));
        assertNotNull(down, "no way down the ramp");
        assertLegal(down);
        assertTrue(onlyWalks(down), "down a ramp is walking: " + down);
    }

    @Test
    void aRampIsNotWalkedOntoFromItsSteepSide() {
        stepWithARamp(1);
        List<Route.Step> moves = new ArrayList<>();
        AntPathFinder.neighbours(map, Foothold.floor(new BlockPos(5, 64, 6)), (to, kind, cost) -> moves.add(new Route.Step(to, kind)));
        assertFalse(moves.contains(new Route.Step(Foothold.floor(new BlockPos(5, 64, 5)), StepKind.WALK)),
                "a ramp's side rises a whole block: it is no step to walk onto");
        moves.clear();
        AntPathFinder.neighbours(map, Foothold.floor(new BlockPos(4, 64, 5)), (to, kind, cost) -> moves.add(new Route.Step(to, kind)));
        assertTrue(moves.contains(new Route.Step(Foothold.floor(new BlockPos(5, 64, 5)), StepKind.WALK)), "a ramp's low side is walked onto");
    }

    @Test
    void fromARampOntoTheWallAboveItsHighSideAndBack() {
        stepWithARamp(2);
        Route up = route(new BlockPos(2, 64, 5), new BlockPos(6, 66, 5));
        assertNotNull(up, "no way up past the ramp");
        assertLegal(up);
        assertTrue(up.steps().contains(new Route.Step(new Foothold(new BlockPos(5, 65, 5), Direction.EAST), StepKind.ONTO_WALL)),
                "it does not step from the ramp onto the wall above it: " + up);
        assertFalse(up.hasFall());
        Route down = route(new BlockPos(6, 66, 5), new BlockPos(2, 64, 5));
        assertNotNull(down, "no way down onto the ramp");
        assertLegal(down);
        assertTrue(down.steps().contains(new Route.Step(Foothold.floor(new BlockPos(5, 64, 5)), StepKind.OFF_WALL)),
                "it does not step off the wall onto the ramp: " + down);
        assertFalse(down.hasFall(), "it must climb down onto the ramp, not drop: " + down);
    }

    @Test
    void overAWallOntoARampsLowSideButNeverItsHighSide() {
        solid.add(new BlockPos(5, 64, 5));
        BlockPos onTop = new BlockPos(5, 65, 5);
        Foothold wall = new Foothold(new BlockPos(4, 64, 5), Direction.EAST);
        Route.Step over = new Route.Step(Foothold.floor(onTop), StepKind.OVER_TOP);
        ramps.put(onTop, Direction.EAST); // low side toward the climber
        List<Route.Step> moves = new ArrayList<>();
        AntPathFinder.neighbours(map, wall, (to, kind, cost) -> moves.add(new Route.Step(to, kind)));
        assertTrue(moves.contains(over), "it does not climb over onto the ramp's low side");
        ramps.put(onTop, Direction.WEST); // its steep high side toward the climber
        moves.clear();
        AntPathFinder.neighbours(map, wall, (to, kind, cost) -> moves.add(new Route.Step(to, kind)));
        assertFalse(moves.contains(over), "it climbs over onto the ramp's steep high side");
    }

    @Test
    void somethingDroppedOnARampLandsOnIt() {
        stepWithARamp(1);
        assertEquals(Foothold.floor(new BlockPos(5, 64, 5)), AntPathFinder.landing(map, new BlockPos(5, 67, 5)));
    }
}
