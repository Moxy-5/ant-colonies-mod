package com.antcolonies.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

/**
 * The rooms of one nest, and what has happened to them. A room can be given up ("ruined", for example when it
 * runs into a lava pool): its door is shut for good, nothing in it is dug or checked any more, and a new room of
 * the same kind is planned somewhere else. The short tunnel in front of a given-up room stays part of the nest,
 * because other rooms may branch off it and the way out must never be cut. This is saved with the colony.
 *
 * <p>Nothing here is specific to ants.
 */
final class NestLayout {
    /** At most this many rooms are ever given up; after that the colony makes do with what it has. */
    static final int MAX_RUINED = 4;

    /** One room of the nest and whether it has been given up. */
    static final class Room {
        final NestPlan.Spec spec;
        final NestPlan.Chamber chamber;
        boolean ruined;
        /** Game time when it was given up (not saved: after a reload nobody is waited for). */
        long ruinedAt = -1_000_000L;

        Room(NestPlan.Spec spec) {
            this.spec = spec;
            this.chamber = spec.chamber();
        }

        NestPlan.Kind kind() {
            return spec.kind();
        }

        /** The last block of the tunnel: shutting it shuts the room off. */
        BlockPos door() {
            return chamber.cells().get(NestPlan.TUNNEL_LENGTH - 1);
        }

        /** The block in front of the door, where an ant stands to shut it. */
        BlockPos stand() {
            return chamber.cells().get(NestPlan.TUNNEL_LENGTH - 2);
        }

        /** The tunnel in front of the door: stays part of the nest even when the room is given up. */
        List<BlockPos> trunk() {
            return chamber.cells().subList(0, NestPlan.TUNNEL_LENGTH - 1);
        }

        /** The door and the room itself: all of it is given up with the room. */
        List<BlockPos> beyondDoor() {
            return chamber.cells().subList(NestPlan.TUNNEL_LENGTH - 1, chamber.cells().size());
        }
    }

    private final int shaftDepth;
    private final List<Room> rooms = new ArrayList<>();
    private Set<BlockPos> cells = Set.of();

    NestLayout(int shaftDepth, List<NestPlan.Spec> specs) {
        this.shaftDepth = shaftDepth;
        for (NestPlan.Spec spec : specs) {
            rooms.add(new Room(spec));
        }
        refresh();
    }

    int shaftDepth() {
        return shaftDepth;
    }

    List<Room> rooms() {
        return rooms;
    }

    int ruinedCount() {
        int count = 0;
        for (Room room : rooms) {
            count += room.ruined ? 1 : 0;
        }
        return count;
    }

    /** The rooms in use, queen's room first. */
    List<NestPlan.Chamber> activeChambers() {
        List<NestPlan.Chamber> chambers = new ArrayList<>();
        for (Room room : rooms) {
            if (!room.ruined && room.kind() == NestPlan.Kind.QUEEN) {
                chambers.add(room.chamber);
            }
        }
        for (Room room : rooms) {
            if (!room.ruined && room.kind() != NestPlan.Kind.QUEEN) {
                chambers.add(room.chamber);
            }
        }
        return chambers;
    }

    @Nullable
    Room activeQueenRoom() {
        for (Room room : rooms) {
            if (!room.ruined && room.kind() == NestPlan.Kind.QUEEN) {
                return room;
            }
        }
        return null;
    }

    /** The blocks to dig, one list per room, in the order to dig them (a given-up room only keeps a tunnel that other rooms branch off). */
    List<List<BlockPos>> digLists() {
        List<List<BlockPos>> lists = new ArrayList<>();
        for (Room room : rooms) {
            if (!room.ruined && room.kind() == NestPlan.Kind.QUEEN) {
                lists.add(room.chamber.cells());
            }
        }
        for (Room room : rooms) {
            if (room.ruined) {
                // Its tunnel is only still dug if another room branches off it (the main tunnel to the queen's room does).
                if (isBranchedFrom(room)) {
                    lists.add(room.trunk());
                }
            } else if (room.kind() != NestPlan.Kind.QUEEN) {
                lists.add(room.chamber.cells());
            }
        }
        return lists;
    }

    private boolean isBranchedFrom(Room room) {
        for (Room other : rooms) {
            if (other != room && room.trunk().contains(other.spec.start())) {
                return true;
            }
        }
        return false;
    }

    /** Every block of the nest that is still in use (not the shaft): all of the rooms in use and the tunnel in front of the given-up ones. */
    Set<BlockPos> cells() {
        return cells;
    }

    private void refresh() {
        Set<BlockPos> all = new HashSet<>();
        for (Room room : rooms) {
            all.addAll(room.ruined ? room.trunk() : room.chamber.cells());
        }
        cells = all;
    }

    void giveUp(Room room, long now) {
        room.ruined = true;
        room.ruinedAt = now;
        refresh();
    }

    void add(Room room) {
        rooms.add(room);
        refresh();
    }

    /** The given-up room whose door or inside this block is, or null. */
    @Nullable
    Room givenUpRoomAt(BlockPos pos) {
        for (Room room : rooms) {
            if (room.ruined && room.beyondDoor().contains(pos)) {
                return room;
            }
        }
        return null;
    }

    /**
     * True if the nest stays in one piece without this block: every other block still joins up with the bottom of
     * the shaft. Closing a block that fails this would shut some room off from the way out.
     */
    boolean staysConnectedWithout(BlockPos block, BlockPos bottom) {
        Set<BlockPos> reached = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        reached.add(bottom);
        queue.add(bottom);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!next.equals(block) && cells.contains(next) && reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (BlockPos cell : cells) {
            if (!cell.equals(block) && !reached.contains(cell)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Plans a new room of the same kind as the one that was given up. It leaves the bottom of the shaft or the
     * side of a tunnel (the same way the first rooms do), and it must not overlap or touch any other room, given-up
     * ones included, nor the shaft, nor any water or lava, so the ways in and out of the nest stay exactly as
     * they were. Null if there is no such place.
     */
    @Nullable
    Room planReplacement(Level level, BlockPos center, Room gone) {
        BlockPos bottom = center.below(shaftDepth);
        Set<BlockPos> existing = new HashSet<>();
        for (int i = 1; i <= shaftDepth; i++) {
            existing.add(center.below(i));
        }
        for (Room room : rooms) {
            existing.addAll(room.chamber.cells());
        }
        Direction main = rooms.get(0).spec.forward();
        List<Direction> order = List.of(main.getOpposite(), main.getClockWise(), main.getCounterClockWise(), main);
        for (Direction direction : order) {
            Room found = tryRoom(level, gone.kind(), bottom, direction, bottom, existing);
            if (found != null) {
                return found;
            }
        }
        // Then off the side of a tunnel, the way the nursery and the food store do.
        for (Room room : rooms) {
            BlockPos branch = room.chamber.cells().get(NestPlan.BRANCH_AT - 1);
            for (Direction direction : List.of(room.spec.forward().getClockWise(), room.spec.forward().getCounterClockWise())) {
                Room found = tryRoom(level, gone.kind(), branch, direction, bottom, existing);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Room tryRoom(Level level, NestPlan.Kind kind, BlockPos start, Direction direction, BlockPos bottom, Set<BlockPos> existing) {
        NestPlan.Spec spec = new NestPlan.Spec(kind, start, direction);
        List<BlockPos> cells = spec.chamber().cells();
        for (BlockPos pos : cells) {
            if (!level.isLoaded(pos)) {
                return null;
            }
        }
        if (!Colony.allSoilOrAir(level, cells)) {
            return null;
        }
        BlockPos first = cells.get(0);
        for (BlockPos pos : cells) {
            if (existing.contains(pos)) {
                return null;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                // Only the first block of the tunnel may touch the nest, and only the block it leaves from.
                if (existing.contains(next) && !(pos.equals(first) && next.equals(start))) {
                    return null;
                }
            }
        }
        return new Room(spec);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("depth", shaftDepth);
        ListTag list = new ListTag();
        for (Room room : rooms) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("kind", room.kind().ordinal());
            entry.putLong("start", room.spec.start().asLong());
            entry.putInt("dir", room.spec.forward().get2DDataValue());
            entry.putBoolean("ruined", room.ruined);
            list.add(entry);
        }
        tag.put("rooms", list);
        return tag;
    }

    static NestLayout load(CompoundTag tag) {
        ListTag list = tag.getList("rooms", Tag.TAG_COMPOUND);
        List<NestPlan.Spec> specs = new ArrayList<>();
        List<Boolean> ruined = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            specs.add(new NestPlan.Spec(NestPlan.Kind.values()[entry.getInt("kind")],
                    BlockPos.of(entry.getLong("start")), Direction.from2DDataValue(entry.getInt("dir"))));
            ruined.add(entry.getBoolean("ruined"));
        }
        NestLayout layout = new NestLayout(tag.getInt("depth"), specs);
        for (int i = 0; i < ruined.size(); i++) {
            layout.rooms.get(i).ruined = ruined.get(i);
        }
        layout.refresh();
        return layout;
    }
}
