package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The shape of the underground nest as a list of rooms (it will come from the species profile later).
 *
 * <p>The nest is a stack of up to four levels, joined by a spiral staircase. Each level has one room, 1 block tall,
 * and the levels are {@link #LEVEL_SPACING} blocks apart (the room, then a 1-block wall of soil under it). From top
 * to bottom: the seed store (5x5), the nursery and the bread (5x5), the queen's room (3x3), the refuse chamber
 * (dead ants, 5x5). See {@link #roomSize}.
 *
 * <p>Seen from above, with {@code R} the direction the rooms lie in (hub = where a level's tunnel starts; hub 1 is
 * the bottom of the entrance shaft). The hubs sit on a 2x2 square and the stairs wind round it, a quarter turn each:
 * <pre>
 *        hub 4 ... hub 1 ---- tunnel ---- room (level 1, the top)
 *          |          |
 *        hub 3 ... hub 2 ---- tunnel ---- room (level 2, two blocks lower), and so on
 * </pre>
 * Hub 1 to hub 2 is the first stair, hub 2 to hub 3 the second, hub 3 to hub 4 the third; every hub has its own
 * tunnel and room toward {@code R}, so the rooms lie one above the other with a 1-block wall of soil between them.
 *
 * A stair is three blocks: one step sideways from the hub, then two blocks straight down, which is the next hub.
 * Everything that is not a stair is on the level of its hub and one block tall.
 *
 * <p>A room is as long as its size says (counted from the end of its tunnel) and as wide, with the tunnel entering
 * the middle of its first row. A room with an even width has no middle column, so it gets one more column on the clockwise
 * side of the tunnel than on the other.
 *
 * <p>With less soil there are fewer levels (the plan is cut off after the last level that fits): then the levels are
 * seeds + queen (2), seeds + nursery + queen (3), or only the queen's room (1). The queen's room is the smallest, so a
 * level that is used as the queen's room always lies inside the bigger room it would have been.
 */
public final class NestPlan {
    public enum Kind {
        QUEEN, NURSERY, FOOD, REFUSE
    }

    /**
     * One level: the room and what leads to it. {@code cells} are the blocks to dig for this level, in digging order
     * (each one touches space that is already dug): the stair down from the level above (none for the top level),
     * then the tunnel, then the room. {@code wayIn} are the blocks that must be open to walk there from the bottom of
     * the shaft: every stair so far, then this level's tunnel (its last block is where the room begins).
     * {@code room} is the room itself, and {@code center} its middle.
     */
    public record Chamber(Kind kind, List<BlockPos> cells, BlockPos center, List<BlockPos> wayIn, List<BlockPos> room) {
        /** The last block of the way in: the tunnel's end, right at the edge of the room. */
        public BlockPos tunnelEnd() {
            return wayIn.get(wayIn.size() - 1);
        }
    }

    /** How big a room is: {@code length} blocks along its tunnel and {@code width} blocks across. */
    public record RoomSize(int length, int width) {
        /** How many blocks the room has. */
        public int cells() {
            return length * width;
        }
    }

    /** From its start, each tunnel is this long before the room begins. */
    public static final int TUNNEL_LENGTH = 3;
    /** The most levels a nest has. */
    public static final int MAX_LEVELS = 4;
    /** How far apart (in blocks, floor to floor) two levels are: a 1-block room and a 1-block wall. */
    public static final int LEVEL_SPACING = 2;
    /** A stair is this many blocks: one sideways, then {@link #LEVEL_SPACING} straight down. */
    public static final int STAIR_LENGTH = 1 + LEVEL_SPACING;

    private NestPlan() {
    }

    /** How big the room of this kind is (it will come from the species profile later). */
    public static RoomSize roomSize(Kind kind) {
        return switch (kind) {
            case QUEEN -> new RoomSize(3, 3);
            case NURSERY -> new RoomSize(5, 5);
            case FOOD -> new RoomSize(5, 5);
            case REFUSE -> new RoomSize(5, 5);
        };
    }

    /** What the levels are used for, top to bottom, when the nest has this many of them. */
    public static List<Kind> kindsFor(int levels) {
        return switch (levels) {
            case 1 -> List.of(Kind.QUEEN);
            case 2 -> List.of(Kind.FOOD, Kind.QUEEN);
            case 3 -> List.of(Kind.FOOD, Kind.NURSERY, Kind.QUEEN);
            default -> List.of(Kind.FOOD, Kind.NURSERY, Kind.QUEEN, Kind.REFUSE);
        };
    }

    /**
     * The first {@code levels} levels (1 to {@link #MAX_LEVELS}) of a nest whose shaft ends at {@code bottom} and
     * whose rooms lie toward {@code direction}, top level first, which is also the order they are dug.
     */
    public static List<Chamber> chambers(BlockPos bottom, Direction direction, int levels) {
        List<Kind> kinds = kindsFor(levels);
        List<Chamber> chambers = new ArrayList<>();
        List<BlockPos> stairs = new ArrayList<>();
        BlockPos hub = bottom;
        Direction step = direction.getClockWise();
        for (int level = 0; level < kinds.size(); level++) {
            List<BlockPos> cells = new ArrayList<>();
            if (level > 0) {
                BlockPos top = hub.relative(step);
                for (int i = 0; i < STAIR_LENGTH; i++) {
                    cells.add(top.below(i));
                }
                stairs.addAll(cells);
                hub = top.below(LEVEL_SPACING);
                step = step.getClockWise();
            }
            List<BlockPos> tunnel = new ArrayList<>();
            for (int i = 1; i <= TUNNEL_LENGTH; i++) {
                tunnel.add(hub.relative(direction, i));
            }
            RoomSize size = roomSize(kinds.get(level));
            List<BlockPos> room = room(hub, direction, size);
            cells.addAll(tunnel);
            cells.addAll(room);
            List<BlockPos> wayIn = new ArrayList<>(stairs);
            wayIn.addAll(tunnel);
            BlockPos center = hub.relative(direction, TUNNEL_LENGTH + (size.length() + 1) / 2);
            chambers.add(new Chamber(kinds.get(level), List.copyOf(cells), center, List.copyOf(wayIn), List.copyOf(room)));
        }
        return List.copyOf(chambers);
    }

    /** The blocks of a room that lies after a tunnel leaving {@code start} in direction {@code forward}, in digging order. */
    private static List<BlockPos> room(BlockPos start, Direction forward, RoomSize size) {
        List<BlockPos> cells = new ArrayList<>();
        Direction sideways = forward.getClockWise();
        // Columns left and right of the tunnel's line; an even width gives the extra one to the clockwise side.
        int left = (size.width() - 1) / 2;
        int right = size.width() / 2;
        for (int row = 1; row <= size.length(); row++) {
            BlockPos rowCenter = start.relative(forward, TUNNEL_LENGTH + row);
            // Middle of the row first, then outward, so every block touches already-dug space.
            cells.add(rowCenter);
            for (int offset = 1; offset <= Math.max(left, right); offset++) {
                if (offset <= right) {
                    cells.add(rowCenter.relative(sideways, offset));
                }
                if (offset <= left) {
                    cells.add(rowCenter.relative(sideways, -offset));
                }
            }
        }
        return cells;
    }
}
