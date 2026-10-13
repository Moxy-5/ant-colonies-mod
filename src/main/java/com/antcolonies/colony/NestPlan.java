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
 * <p>Species whose queen founds alone (the profile's {@code nest.founding_chamber}) also get a small founding chamber
 * just under the surface, dug first, on the far side of the shaft from the rooms: see {@link #foundingChamber}.
 *
 * <p>With less soil there are fewer levels (the plan is cut off after the last level that fits): then the levels are
 * seeds + queen (2), seeds + nursery + queen (3), or only the queen's room (1). The queen's room is the smallest, so a
 * level that is used as the queen's room always lies inside the bigger room it would have been.
 */
public final class NestPlan {
    public enum Kind {
        QUEEN, NURSERY, FOOD, REFUSE,
        /** The small room a queen digs on her own when she founds the colony (not one of the levels, see {@link #foundingChamber}). */
        FOUNDING,
        /** A termite fungus garden room round the royal chamber (see {@link #gardens}): fungus comb, eggs and nymphs. */
        GARDEN
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
    /**
     * How deep the founding chamber lies: this many blocks of the entrance shaft, so 1 block of soil is its roof. It is
     * dug first, by the queen alone (species with a founding chamber in their profile), and the shaft goes on down
     * to the levels from there.
     */
    public static final int FOUNDING_DEPTH = 2;
    /** The founding chamber's tunnel is this short: one block from the shaft, then the room. */
    public static final int FOUNDING_TUNNEL_LENGTH = 1;
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
            case FOUNDING -> new RoomSize(3, 3);
            case GARDEN -> new RoomSize(3, 3);
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
            List<BlockPos> room = room(hub, direction, size, TUNNEL_LENGTH);
            cells.addAll(tunnel);
            cells.addAll(room);
            List<BlockPos> wayIn = new ArrayList<>(stairs);
            wayIn.addAll(tunnel);
            BlockPos center = hub.relative(direction, TUNNEL_LENGTH + (size.length() + 1) / 2);
            chambers.add(new Chamber(kinds.get(level), List.copyOf(cells), center, List.copyOf(wayIn), List.copyOf(room)));
        }
        return List.copyOf(chambers);
    }

    /**
     * The founding chamber of a nest whose levels lie toward {@code direction}: a 3x3 room on the opposite side of the
     * shaft, off the shaft's block {@link #FOUNDING_DEPTH} deep ({@code shaftCell}) through a 1-block tunnel. That side
     * is clear of the levels' tunnels and rooms, and of the first stair (it steps off clockwise of {@code direction});
     * the stairs that pass under it lie at least one block of soil lower. Its wayIn is its tunnel only (the shaft above
     * is not part of a chamber).
     */
    public static Chamber foundingChamber(BlockPos shaftCell, Direction direction) {
        Direction away = direction.getOpposite();
        List<BlockPos> tunnel = new ArrayList<>();
        for (int i = 1; i <= FOUNDING_TUNNEL_LENGTH; i++) {
            tunnel.add(shaftCell.relative(away, i));
        }
        RoomSize size = roomSize(Kind.FOUNDING);
        List<BlockPos> room = room(shaftCell, away, size, FOUNDING_TUNNEL_LENGTH);
        List<BlockPos> cells = new ArrayList<>(tunnel);
        cells.addAll(room);
        BlockPos center = shaftCell.relative(away, FOUNDING_TUNNEL_LENGTH + (size.length() + 1) / 2);
        return new Chamber(Kind.FOUNDING, List.copyOf(cells), center, List.copyOf(tunnel), List.copyOf(room));
    }

    /**
     * Side rooms (the profile's {@code nest.side_rooms}, such as the termites' food store): more rooms on the top level,
     * each at the end of its own {@link #TUNNEL_LENGTH}-block tunnel leaving the foot of the shaft ({@code hub}) toward
     * another side than the levels' rooms ({@code direction}): the first straight across from them, the next to their
     * left, the third to their right (at most three). They are as big as their kind says ({@link #roomSize}), and two
     * of them never touch (a 5x5 room across and one to the left keep two blocks of soil between their corners). Only
     * for nests with one level and no founding chamber, like the termites': the first stair steps off to the right,
     * and a founding chamber lies across from the rooms one block higher.
     */
    public static List<Chamber> sideRooms(BlockPos hub, Direction direction, List<Kind> kinds) {
        Direction[] sides = {direction.getOpposite(), direction.getCounterClockWise(), direction.getClockWise()};
        List<Chamber> rooms = new ArrayList<>();
        for (int i = 0; i < kinds.size() && i < sides.length; i++) {
            Direction side = sides[i];
            List<BlockPos> tunnel = new ArrayList<>();
            for (int k = 1; k <= TUNNEL_LENGTH; k++) {
                tunnel.add(hub.relative(side, k));
            }
            RoomSize size = roomSize(kinds.get(i));
            List<BlockPos> room = room(hub, side, size, TUNNEL_LENGTH);
            List<BlockPos> cells = new ArrayList<>(tunnel);
            cells.addAll(room);
            BlockPos center = hub.relative(side, TUNNEL_LENGTH + (size.length() + 1) / 2);
            rooms.add(new Chamber(kinds.get(i), List.copyOf(cells), center, List.copyOf(tunnel), List.copyOf(room)));
        }
        return List.copyOf(rooms);
    }

    /**
     * Where the garden rooms lie, in the order they are dug: {@code row} and {@code side} are the middle of the room,
     * counted from the royal chamber's middle (rows along the rooms' direction, sides clockwise of it); the doorway is
     * the block between the room and the room it opens into ({@code parent}: the royal chamber, -1, or an earlier
     * garden). Seen from above, with the royal chamber {@code Q} at the bottom middle and its tunnel coming up from the
     * shaft below it:
     * <pre>
     *            6
     *            |
     *      4 --  1  -- 5        (4 opens into 2, 5 into 3, 6 into 1)
     *      |     |     |
     *      2 --  Q  -- 3
     *            |
     *          tunnel
     * </pre>
     * Every room is 3x3 with a wall of one block of soil to the next, crossed only by its doorway.
     */
    private record GardenSpot(int row, int side, int parent) {
    }

    private static final GardenSpot[] GARDEN_SPOTS = {
            new GardenSpot(4, 0, -1), new GardenSpot(0, -4, -1), new GardenSpot(0, 4, -1),
            new GardenSpot(4, -4, 1), new GardenSpot(4, 4, 2), new GardenSpot(8, 0, 0)};

    /** The most garden rooms a nest can have (see {@link #gardens}). */
    public static final int MAX_GARDENS = GARDEN_SPOTS.length;

    /**
     * The garden rooms (termites' fungus gardens, at most {@link #MAX_GARDENS}) round the royal chamber {@code royal}
     * (a 3x3 room at the end of its tunnel, the nest's only level) of a nest whose rooms lie toward {@code direction}:
     * on the same level, beside and beyond it (see {@link GardenSpot}), each joined to the royal chamber or to the garden
     * before it by a doorway. Real Macrotermes keep their fungus gardens in many small chambers all round the royal
     * cell. A garden's cells are its doorway, then its room; its way in is the royal chamber's way in, the royal chamber
     * itself, the rooms and doorways in between and its own doorway, so it counts once all of those are dug.
     */
    public static List<Chamber> gardens(Chamber royal, Direction direction, int count) {
        Direction sideways = direction.getClockWise();
        List<Chamber> gardens = new ArrayList<>();
        for (int i = 0; i < count && i < GARDEN_SPOTS.length; i++) {
            GardenSpot spot = GARDEN_SPOTS[i];
            Chamber parent = spot.parent() < 0 ? royal : gardens.get(spot.parent());
            BlockPos center = royal.center().relative(direction, spot.row()).relative(sideways, spot.side());
            // The doorway lies between this room's middle and the parent room's middle, one block outside this room.
            BlockPos parentCenter = parent.center();
            int stepX = Integer.signum(parentCenter.getX() - center.getX());
            int stepZ = Integer.signum(parentCenter.getZ() - center.getZ());
            BlockPos door = center.offset(2 * stepX, 0, 2 * stepZ);
            List<BlockPos> room = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    room.add(center.offset(dx, 0, dz));
                }
            }
            // Dug from the doorway inward: nearest first, so every block touches space already dug.
            room.sort(java.util.Comparator.comparingInt((BlockPos pos) -> pos.distManhattan(door)).thenComparing(BlockPos::compareTo));
            List<BlockPos> cells = new ArrayList<>();
            cells.add(door);
            cells.addAll(room);
            List<BlockPos> wayIn = new ArrayList<>(parent.wayIn());
            wayIn.addAll(parent.room());
            wayIn.add(door);
            gardens.add(new Chamber(Kind.GARDEN, List.copyOf(cells), center, List.copyOf(wayIn), List.copyOf(room)));
        }
        return List.copyOf(gardens);
    }

    /**
     * A foraging tunnel: {@code tunnel} its blocks in digging order (each touches the one before; the first touches the
     * entrance shaft), {@code hole} the block above its far end, the foraging hole in the ground that is opened when
     * the workers go out to forage and plugged with soil when they stay in, and {@code side} the way it runs.
     */
    public record ForagingTunnel(List<BlockPos> tunnel, BlockPos hole, Direction side) {
        /** The last block of the tunnel, right under the hole: where a worker stands to open or plug it. */
        public BlockPos end() {
            return tunnel.get(tunnel.size() - 1);
        }
    }

    /** How deep (in blocks under the entrance cell) a foraging tunnel runs: just under the surface, its roof the top block of the ground. */
    public static final int FORAGING_TUNNEL_DEPTH = 2;

    /**
     * The foraging tunnels (the profile's {@code nest.foraging_holes}, at most 2) of a nest whose rooms lie toward
     * {@code direction}: each leaves the entrance shaft {@link #FORAGING_TUNNEL_DEPTH} blocks under the entrance cell
     * ({@code entrance}) and runs straight out, just under the surface, {@code distance} blocks to the side (to the left
     * of the rooms, then to the right), and a hole opens from its end up to the surface. Real Macrotermes forage through
     * such shallow tunnels that open far from the mound. They run to the sides a nest with one level and one side room
     * (the termites': the rooms ahead, the food store behind) leaves free, one block above the rooms' level, so they
     * never touch a room or its tunnel; a nest with more side rooms has no room for them.
     */
    public static List<ForagingTunnel> foragingTunnels(BlockPos entrance, Direction direction, int count, int distance) {
        Direction[] sides = {direction.getCounterClockWise(), direction.getClockWise()};
        BlockPos start = entrance.below(FORAGING_TUNNEL_DEPTH);
        List<ForagingTunnel> tunnels = new ArrayList<>();
        for (int i = 0; i < count && i < sides.length && distance > 0; i++) {
            List<BlockPos> tunnel = new ArrayList<>();
            for (int k = 1; k <= distance; k++) {
                tunnel.add(start.relative(sides[i], k));
            }
            tunnels.add(new ForagingTunnel(List.copyOf(tunnel), tunnel.get(tunnel.size() - 1).above(), sides[i]));
        }
        return List.copyOf(tunnels);
    }

    /** The blocks of a room that lies after a tunnel ({@code tunnelLength} long) leaving {@code start} in direction {@code forward}, in digging order. */
    private static List<BlockPos> room(BlockPos start, Direction forward, RoomSize size, int tunnelLength) {
        List<BlockPos> cells = new ArrayList<>();
        Direction sideways = forward.getClockWise();
        // Columns left and right of the tunnel's line; an even width gives the extra one to the clockwise side.
        int left = (size.width() - 1) / 2;
        int right = size.width() / 2;
        for (int row = 1; row <= size.length(); row++) {
            BlockPos rowCenter = start.relative(forward, tunnelLength + row);
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
