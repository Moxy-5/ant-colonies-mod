package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The shape of the underground nest as a list of rooms (it will come from the species profile later).
 *
 * <p>Seen from above, with the shaft at the bottom and the main tunnel going right:
 * <pre>
 *            nursery room
 *                 |
 *   shaft --- tunnel --- tunnel --- tunnel --- queen room
 *                 |
 *            food store room
 * </pre>
 * The queen's room is at the end of the main tunnel (the deepest, safest place). The nursery and the food
 * store each have their own short tunnel that leaves the main tunnel to one side. Everything is on the
 * level of the shaft bottom and one block tall.
 */
public final class NestPlan {
    public enum Kind {
        QUEEN, NURSERY, FOOD
    }

    /**
     * One room and the tunnel that leads to it. {@code cells} are the blocks to dig, in digging order (each
     * one touches space that is already dug), and {@code center} is the middle of the room.
     */
    public record Chamber(Kind kind, List<BlockPos> cells, BlockPos center) {
    }

    /** From its start, each tunnel is this long before the room begins. */
    public static final int TUNNEL_LENGTH = 3;
    /** A room is this many blocks long (along its tunnel) and 2 * ROOM_HALF_WIDTH + 1 wide. */
    public static final int ROOM_LENGTH = 5;
    public static final int ROOM_HALF_WIDTH = 2;
    /** The nursery and food store tunnels leave from this block of the main tunnel (counted from the shaft). */
    public static final int BRANCH_AT = 2;

    private NestPlan() {
    }

    /**
     * All the rooms for a nest whose shaft ends at {@code bottom} and whose main tunnel leaves in
     * {@code direction}, in the order they are dug. The queen's room (with the main tunnel) is always first.
     */
    public static List<Chamber> chambers(BlockPos bottom, Direction direction) {
        BlockPos branch = bottom.relative(direction, BRANCH_AT);
        return List.of(
                chamber(Kind.QUEEN, bottom, direction),
                chamber(Kind.NURSERY, branch, direction.getClockWise()),
                chamber(Kind.FOOD, branch, direction.getCounterClockWise()));
    }

    /** A tunnel leaving {@code start} in direction {@code forward}, then a room at the end of it. */
    private static Chamber chamber(Kind kind, BlockPos start, Direction forward) {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 1; i <= TUNNEL_LENGTH; i++) {
            cells.add(start.relative(forward, i));
        }
        Direction sideways = forward.getClockWise();
        for (int row = 1; row <= ROOM_LENGTH; row++) {
            BlockPos rowCenter = start.relative(forward, TUNNEL_LENGTH + row);
            // Middle of the row first, then outward, so every block touches already-dug space.
            cells.add(rowCenter);
            for (int offset = 1; offset <= ROOM_HALF_WIDTH; offset++) {
                cells.add(rowCenter.relative(sideways, offset));
                cells.add(rowCenter.relative(sideways, -offset));
            }
        }
        return new Chamber(kind, List.copyOf(cells), start.relative(forward, TUNNEL_LENGTH + (ROOM_LENGTH + 1) / 2));
    }
}
