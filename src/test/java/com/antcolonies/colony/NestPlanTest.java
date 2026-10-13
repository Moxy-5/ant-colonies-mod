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

/**
 * Checks the shape of the underground nest: four stacked levels (seed store 5x5, nursery 5x5, queen 3x3, refuse chamber
 * 5x5), a spiral staircase, no overlaps, walls between the levels.
 */
class NestPlanTest {
    private static final BlockPos BOTTOM = new BlockPos(100, 61, -40);

    private static List<NestPlan.Chamber> full(Direction direction) {
        return NestPlan.chambers(BOTTOM, direction, NestPlan.MAX_LEVELS);
    }

    /**
     * The founding chamber (a 3x3 room off the shaft 2 blocks down, on the far side from the rooms) never touches a
     * cell of the levels, not even through a corner of a face: the only way between them is the shaft. Checked with the
     * shaft 3 deep (the levels start one block under it) and 2 deep (the first level is on the same layer).
     */
    /** Manhattan distance: 1 = the two blocks share a face. */
    private static int dist(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    @Test
    void sideRoomsLieBesideTheShaftFootClearOfTheRoomsTheGalleriesAndEachOther() {
        List<NestPlan.Kind> kinds = List.of(NestPlan.Kind.FOOD, NestPlan.Kind.FOOD, NestPlan.Kind.FOOD);
        List<BlockPos> galleries = MoundPlan.galleries(3, 13).stream().map(BOTTOM::offset).toList();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            List<NestPlan.Chamber> levels = NestPlan.chambers(BOTTOM, direction, 1);
            List<NestPlan.Chamber> side = NestPlan.sideRooms(BOTTOM, direction, kinds);
            assertEquals(3, side.size());
            assertEquals(direction.getOpposite(), Direction.getNearest(
                    side.get(0).center().getX() - BOTTOM.getX(), 0, side.get(0).center().getZ() - BOTTOM.getZ()), "the first side room lies across from the rooms");
            for (int i = 0; i < side.size(); i++) {
                NestPlan.Chamber room = side.get(i);
                assertEquals(NestPlan.TUNNEL_LENGTH + 25, room.cells().size(), "a food store side room is a 3-block tunnel and a 5x5 room");
                assertEquals(1, dist(room.wayIn().get(0), BOTTOM), "the tunnel starts at the foot of the shaft");
                Set<BlockPos> dug = new HashSet<>(Set.of(BOTTOM));
                for (BlockPos cell : room.cells()) {
                    assertEquals(BOTTOM.getY(), cell.getY(), "a side room lies on the top level");
                    assertTrue(dug.stream().anyMatch(d -> dist(d, cell) == 1), cell + " is dug before anything next to it");
                    dug.add(cell);
                    for (NestPlan.Chamber level : levels) {
                        for (BlockPos b : level.cells()) {
                            assertTrue(dist(cell, b) > 1, "rooms " + direction + ": side room cell " + cell + " touches the " + level.kind() + " cell " + b);
                        }
                    }
                    for (BlockPos b : galleries) {
                        assertTrue(dist(cell, b) > 1, "rooms " + direction + ": side room cell " + cell + " touches the gallery cell " + b);
                    }
                    for (int k = i + 1; k < side.size(); k++) {
                        for (BlockPos b : side.get(k).cells()) {
                            assertTrue(dist(cell, b) > 1, "rooms " + direction + ": side rooms " + i + " and " + k + " touch at " + cell);
                        }
                    }
                }
            }
        }
    }

    /**
     * The termites' garden rooms (six 3x3 rooms round the royal chamber) lie on its level, are dug from their doorway
     * inward, each opens only into the royal chamber or an earlier garden through its doorway, and never touch another
     * room, the royal chamber's tunnel, the shaft, a gallery or a foraging tunnel. Checked with the shaft 3 and 2 deep.
     */
    @Test
    void gardenRoomsLieRoundTheRoyalChamberAndOnlyOpenThroughTheirDoorways() {
        for (int depth = 2; depth <= 3; depth++) {
            BlockPos entrance = BOTTOM.above(depth);
            List<BlockPos> galleries = MoundPlan.galleries(3, 13).stream().map(BOTTOM::offset).toList();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                NestPlan.Chamber royal = NestPlan.chambers(BOTTOM, direction, 1).get(0);
                List<NestPlan.Chamber> gardens = NestPlan.gardens(royal, direction, 10);
                assertEquals(NestPlan.MAX_GARDENS, gardens.size(), "at most " + NestPlan.MAX_GARDENS + " gardens");
                assertEquals(2, NestPlan.gardens(royal, direction, 2).size());
                Set<BlockPos> other = new HashSet<>(royal.cells());
                for (int k = 1; k <= depth; k++) {
                    other.add(BOTTOM.above(k - 1));
                }
                other.addAll(galleries);
                for (NestPlan.ForagingTunnel tunnel : NestPlan.foragingTunnels(entrance, direction, 2, 8)) {
                    other.addAll(tunnel.tunnel());
                }
                Set<BlockPos> dug = new HashSet<>(royal.cells());
                dug.add(BOTTOM);
                for (int i = 0; i < gardens.size(); i++) {
                    NestPlan.Chamber garden = gardens.get(i);
                    BlockPos door = garden.cells().get(0);
                    assertEquals(10, garden.cells().size(), "a garden is a doorway and a 3x3 room");
                    assertEquals(9, garden.room().size());
                    assertEquals(door, garden.tunnelEnd(), "the way in ends at the doorway");
                    assertTrue(garden.wayIn().containsAll(royal.room()) && garden.wayIn().containsAll(royal.wayIn()), "the way in goes through the royal chamber");
                    for (BlockPos cell : garden.cells()) {
                        assertEquals(BOTTOM.getY(), cell.getY(), "a garden lies on the royal chamber's level");
                        assertTrue(dug.stream().anyMatch(d -> dist(d, cell) == 1), cell + " is dug before anything next to it");
                        dug.add(cell);
                    }
                    for (BlockPos cell : garden.room()) {
                        for (BlockPos b : other) {
                            assertTrue(dist(cell, b) > 1, "rooms " + direction + ", shaft " + depth + ": garden " + i + " cell " + cell + " touches " + b);
                        }
                    }
                    // The doorway touches its own room and the room it opens into, nothing else.
                    for (BlockPos b : other) {
                        assertTrue(dist(door, b) > 1 || royal.room().contains(b) || gardens.subList(0, i).stream().anyMatch(g -> g.room().contains(b)),
                                "rooms " + direction + ": garden " + i + "'s doorway " + door + " touches " + b);
                    }
                    other.addAll(garden.cells());
                }
            }
        }
    }

    /**
     * The foraging tunnels (termites: two, 8 blocks out) leave the shaft 2 blocks under the entrance, run straight out
     * just under the surface to the sides the rooms and the food store leave free, never touch a room, its tunnel, a
     * gallery or each other, and end under a hole in the surface beyond the foot of a full-grown mound. Checked with
     * the shaft 3 and 2 deep.
     */
    @Test
    void foragingTunnelsRunOutUnderTheSurfaceClearOfTheRooms() {
        int distance = 8;
        for (int depth = 2; depth <= 3; depth++) {
            BlockPos entrance = BOTTOM.above(depth);
            List<BlockPos> galleries = MoundPlan.galleries(3, 13).stream().map(entrance.below(depth)::offset).toList();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                List<NestPlan.Chamber> rooms = new java.util.ArrayList<>(NestPlan.chambers(entrance.below(depth), direction, 1));
                rooms.addAll(NestPlan.sideRooms(entrance.below(depth), direction, List.of(NestPlan.Kind.FOOD)));
                List<NestPlan.ForagingTunnel> tunnels = NestPlan.foragingTunnels(entrance, direction, 2, distance);
                assertEquals(2, tunnels.size());
                assertEquals(2, NestPlan.foragingTunnels(entrance, direction, 5, distance).size(), "at most two foraging tunnels");
                for (int i = 0; i < tunnels.size(); i++) {
                    NestPlan.ForagingTunnel tunnel = tunnels.get(i);
                    assertEquals(distance, tunnel.tunnel().size());
                    assertEquals(1, dist(tunnel.tunnel().get(0), entrance.below(NestPlan.FORAGING_TUNNEL_DEPTH)), "the tunnel starts at the shaft");
                    assertTrue(tunnel.side().getAxis() != direction.getAxis(), "a foraging tunnel runs toward a room or the food store");
                    assertEquals(tunnel.end().above(), tunnel.hole());
                    assertEquals(entrance.getY() - 1, tunnel.hole().getY(), "the hole is in the top block of the ground");
                    assertTrue(Math.abs(tunnel.hole().getX() - entrance.getX()) + Math.abs(tunnel.hole().getZ() - entrance.getZ()) > 6.5 + 1,
                            "the hole lies under the foot of a full-grown mound");
                    BlockPos previous = entrance.below(NestPlan.FORAGING_TUNNEL_DEPTH);
                    for (BlockPos cell : tunnel.tunnel()) {
                        assertEquals(entrance.getY() - NestPlan.FORAGING_TUNNEL_DEPTH, cell.getY(), "the tunnel runs just under the surface");
                        assertEquals(1, dist(previous, cell), cell + " is dug before anything next to it");
                        previous = cell;
                        for (NestPlan.Chamber room : rooms) {
                            for (BlockPos b : room.cells()) {
                                assertTrue(dist(cell, b) > 1, "depth " + depth + ", rooms " + direction + ": tunnel cell " + cell + " touches the " + room.kind() + " cell " + b);
                            }
                        }
                        for (BlockPos b : galleries) {
                            assertTrue(dist(cell, b) > 1, "depth " + depth + ": tunnel cell " + cell + " touches the gallery cell " + b);
                        }
                        for (int k = i + 1; k < tunnels.size(); k++) {
                            for (BlockPos b : tunnels.get(k).tunnel()) {
                                assertTrue(dist(cell, b) > 1, "the two foraging tunnels touch at " + cell);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void theFoundingChamberStaysClearOfTheLevels() {
        for (int depth = 2; depth <= 3; depth++) {
            BlockPos entrance = BOTTOM.above(depth);
            BlockPos shaftCell = entrance.below(NestPlan.FOUNDING_DEPTH);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                NestPlan.Chamber founding = NestPlan.foundingChamber(shaftCell, direction);
                assertEquals(NestPlan.Kind.FOUNDING, founding.kind());
                assertEquals(1 + 9, founding.cells().size());
                assertTrue(founding.room().contains(founding.center()));
                Set<BlockPos> shaft = new HashSet<>();
                for (int i = 1; i <= depth; i++) {
                    shaft.add(entrance.below(i));
                }
                for (NestPlan.Chamber level : NestPlan.chambers(entrance.below(depth), direction, NestPlan.MAX_LEVELS)) {
                    for (BlockPos a : founding.cells()) {
                        for (BlockPos b : level.cells()) {
                            int dist = Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
                            assertTrue(dist > 1, "depth " + depth + ", rooms " + direction + ": founding cell " + a
                                    + " touches " + level.kind() + " cell " + b);
                        }
                    }
                }
                // It joins the shaft only through its tunnel.
                BlockPos tunnel = founding.wayIn().get(0);
                assertTrue(shaft.contains(tunnel.relative(direction)), "the tunnel does not start at the shaft");
            }
        }
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
            // A shorter nest has the same stairs and tunnels as the full one. Its levels are used differently, and the
            // last one is the small queen's room, which lies inside the room it replaces.
            List<NestPlan.Chamber> fullPlan = full(Direction.NORTH);
            for (int i = 0; i < levels; i++) {
                assertEquals(fullPlan.get(i).wayIn(), chambers.get(i).wayIn(), "level " + (i + 1) + " of " + levels);
                if (i < levels - 1) {
                    assertEquals(fullPlan.get(i).cells(), chambers.get(i).cells());
                } else {
                    assertTrue(fullPlan.get(i).room().containsAll(chambers.get(i).room()),
                            "the queen's room of a " + levels + "-level nest lies inside the room of the full nest");
                }
            }
        }
    }

    @Test
    void roomSizesAre5x5SeedsAndNursery3x3Queen5x5Refuse() {
        assertEquals(new NestPlan.RoomSize(5, 5), NestPlan.roomSize(NestPlan.Kind.FOOD));
        assertEquals(new NestPlan.RoomSize(5, 5), NestPlan.roomSize(NestPlan.Kind.NURSERY));
        assertEquals(new NestPlan.RoomSize(3, 3), NestPlan.roomSize(NestPlan.Kind.QUEEN));
        assertEquals(new NestPlan.RoomSize(5, 5), NestPlan.roomSize(NestPlan.Kind.REFUSE));
    }

    @Test
    void everyLevelIsOneFlatRoomOfItsOwnSizeTwoBlocksUnderTheLevelAbove() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> all = new HashSet<>();
            List<NestPlan.Chamber> chambers = full(direction);
            int rooms = 0;
            for (int level = 0; level < chambers.size(); level++) {
                NestPlan.Chamber chamber = chambers.get(level);
                NestPlan.RoomSize size = NestPlan.roomSize(chamber.kind());
                rooms += size.cells();
                int stairs = level == 0 ? 0 : NestPlan.STAIR_LENGTH;
                assertEquals(stairs + 3 + size.cells(), chamber.cells().size());
                assertEquals(size.cells(), chamber.room().size());
                int floor = BOTTOM.getY() - NestPlan.LEVEL_SPACING * level;
                for (BlockPos cell : chamber.room()) {
                    assertEquals(floor, cell.getY(), "the room is flat and 1 block tall");
                }
                assertEquals(floor, chamber.center().getY());
                assertEquals(floor, chamber.tunnelEnd().getY());
                assertTrue(chamber.room().contains(chamber.center()), "the room's middle is part of the room");
                // The room is a full rectangle of the right size, and the tunnel enters the middle of its first row.
                boolean alongX = direction.getAxis() == Direction.Axis.X;
                int[] along = {Integer.MAX_VALUE, Integer.MIN_VALUE};
                int[] across = {Integer.MAX_VALUE, Integer.MIN_VALUE};
                for (BlockPos cell : chamber.room()) {
                    int a = alongX ? cell.getX() : cell.getZ();
                    int c = alongX ? cell.getZ() : cell.getX();
                    along[0] = Math.min(along[0], a);
                    along[1] = Math.max(along[1], a);
                    across[0] = Math.min(across[0], c);
                    across[1] = Math.max(across[1], c);
                }
                assertEquals(size.length(), along[1] - along[0] + 1, chamber.kind() + " length");
                assertEquals(size.width(), across[1] - across[0] + 1, chamber.kind() + " width");
                assertTrue(chamber.room().contains(chamber.tunnelEnd().relative(direction)), "the tunnel opens into the room");
                int tunnelAcross = alongX ? chamber.tunnelEnd().getZ() : chamber.tunnelEnd().getX();
                assertTrue(Math.abs((tunnelAcross - across[0]) - (across[1] - tunnelAcross)) <= 1,
                        "the tunnel enters the middle of the room (within one block when the width is even)");
                for (BlockPos cell : chamber.cells()) {
                    assertTrue(all.add(cell), "cell " + cell + " is in two places (" + direction + ")");
                }
            }
            assertEquals(3 * NestPlan.STAIR_LENGTH + 4 * 3 + rooms, all.size());
            assertEquals(3 * 3 + 4 * 3 + 25 + 25 + 9 + 25, all.size());
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
