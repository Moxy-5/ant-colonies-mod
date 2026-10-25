package com.antcolonies.savanna.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.savanna.block.DeadCombBlock;
import com.antcolonies.savanna.block.FungusCombBlock;
import com.antcolonies.savanna.entity.MatabeleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Matabele ant's nest (M8 step 1d, {@code docs/DESIGN.md}, "Matabele colony, details", step 1d): real Megaponera
 * build nothing; they move into spaces other animals made. The user's call (2026-10-24, after trying logs, trees and
 * burrows: a lone log or tree has no room for a whole colony, and getting in under a log was needlessly fiddly): for now
 * a colony lives only in <b>a dead termite mound</b>, the old nest's rooms under the hump, reached down the open chimney.
 * The empty royal chamber is the queen's at once; a garden room still holding its rotting Xylaria comb is taken up once
 * the comb is gone (the user's pick: ants neither eat nor clear that fungus). A mound whose chimney is still capped has
 * no way in yet and is passed over. One colony to a mound, the nearest free one within {@link #LOOK} blocks; nothing is
 * dug. With none near, a queen has no home yet and looks again ({@code MatabeleQueenEntity}). The rooms are handed to the
 * shared nest ({@code Nest.settle}); the queen's home and the numbers coming out of the rooms are the shared code's.
 */
public final class MatabeleNest {
    /** How far round her a queen looks for a dead mound (blocks; my pick). */
    public static final int LOOK = 32;
    /** The words in the colony list for a colony in a dead mound. */
    public static final String IN_A_MOUND = "in a dead termite mound";
    /** How far round a dead mound's middle its old rooms are looked for (the termite gardens lie within about 6). */
    static final int MOUND_REACH = 10;
    /** How deep under a dead mound's entrance its old rooms are looked for. */
    static final int MOUND_DEPTH = 8;
    /** The most rooms a nest takes from a dead mound. */
    static final int MOST_ROOMS = 8;

    /** A site: the entrance (the colony's center: the open cell over the way down) and the rooms laid out there. */
    public record Site(BlockPos entrance, List<NestPlan.Chamber> rooms) {
    }

    private MatabeleNest() {
    }

    /** The nearest free dead mound with a way in within {@link #LOOK} blocks of {@code feet}; null when there is none. */
    @Nullable
    public static Site pick(ServerLevel level, BlockPos feet) {
        return nearestMound(level, feet);
    }

    /** A colony of the Matabele with its queen ({@code queenId}) settled in {@code site}. */
    public static Colony settle(ServerLevel level, Site site, UUID queenId) {
        Colony colony = ColonyManager.get(level).createColony(MatabeleEntity.SPECIES, queenId, site.entrance());
        colony.nest().settle(IN_A_MOUND, site.rooms());
        ColonyManager.get(level).markChanged();
        return colony;
    }

    private static int horizontal(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    private static double distance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Open for an ant to walk through: nothing to bump into, no water. */
    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean isComb(BlockState state) {
        return state.getBlock() instanceof DeadCombBlock || state.getBlock() instanceof FungusCombBlock;
    }

    private static boolean isLoadedRound(ServerLevel level, BlockPos pos, int reach) {
        for (int dx = -reach; dx <= reach; dx += 16) {
            for (int dz = -reach; dz <= reach; dz += 16) {
                if (!level.isLoaded(pos.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return level.isLoaded(pos.offset(reach, 0, reach)) && level.isLoaded(pos.offset(-reach, 0, -reach))
                && level.isLoaded(pos.offset(reach, 0, -reach)) && level.isLoaded(pos.offset(-reach, 0, reach));
    }

    // ----- A dead termite mound -----

    @Nullable
    private static Site nearestMound(ServerLevel level, BlockPos feet) {
        List<DeadMounds.DeadMound> near = new ArrayList<>();
        for (DeadMounds.DeadMound mound : DeadMounds.get(level).all()) {
            if (distance(mound.center, feet) <= LOOK) {
                near.add(mound);
            }
        }
        near.sort(Comparator.comparingDouble(mound -> distance(mound.center, feet)));
        for (DeadMounds.DeadMound mound : near) {
            Site site = fromMound(level, mound.center);
            if (site != null && colonies(level, site.entrance(), 1).isEmpty()) {
                return site; // (one colony to a dead mound: the user, 2026-10-24)
            }
        }
        return null;
    }

    /**
     * The nest in the dead mound whose old colony's entrance was {@code middle}: up the old chimney to the open air over
     * the hump (the entrance), and the old nest's open rooms under the ground, each with its way from the entrance. Null
     * while its land is not loaded, when the chimney is closed, or when no room is found.
     */
    @Nullable
    static Site fromMound(ServerLevel level, BlockPos middle) {
        if (!isLoadedRound(level, middle, MOUND_REACH + 1)) {
            return null;
        }
        // Up the old chimney from the nest's entrance while walled in, to the first cell with open air all round.
        List<BlockPos> up = new ArrayList<>();
        BlockPos at = middle;
        for (int i = 0; i < 48; i++) {
            if (!isOpen(level, at)) {
                return null; // capped: no way in yet
            }
            if (isOutside(level, at)) {
                break;
            }
            up.add(at);
            at = at.above();
        }
        BlockPos entrance = at;
        // The old nest under the ground: every open cell (and comb) joined to the shaft, out of the sky.
        Map<BlockPos, BlockPos> from = new HashMap<>();
        Map<BlockPos, Integer> steps = new HashMap<>();
        Set<BlockPos> comb = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        from.put(middle, null);
        steps.put(middle, 0);
        queue.add(middle);
        while (!queue.isEmpty() && steps.size() < 3000) {
            BlockPos cell = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = cell.relative(side);
                if (steps.containsKey(next) || next.getY() >= middle.getY() || next.getY() < middle.getY() - MOUND_DEPTH
                        || horizontal(next, middle) > MOUND_REACH) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                boolean isComb = isComb(state);
                // (the old chimney's column sees the sky down its open hole: it is the way in, not the open air)
                boolean chimney = next.getX() == middle.getX() && next.getZ() == middle.getZ();
                if (!isComb && !isOpen(level, next) || !chimney && level.canSeeSky(next)) {
                    continue;
                }
                if (isComb) {
                    comb.add(next);
                }
                from.put(next, cell);
                steps.put(next, steps.get(cell) + 1);
                queue.add(next);
            }
        }
        List<List<BlockPos>> found = rooms(steps.keySet());
        if (found.isEmpty()) {
            return null;
        }
        // The way down from the entrance to the old nest's entrance: the chimney, top first.
        List<BlockPos> chimney = new ArrayList<>();
        for (int i = up.size() - 1; i >= 1; i--) {
            chimney.add(up.get(i));
        }
        record Found(List<BlockPos> room, BlockPos door, int steps, boolean combed) {
        }
        List<Found> rooms = new ArrayList<>();
        for (List<BlockPos> room : found) {
            BlockPos door = room.stream().min(Comparator.comparingInt(steps::get)).orElseThrow();
            boolean combed = room.stream().anyMatch(comb::contains);
            for (BlockPos cell = from.get(door); cell != null && !combed; cell = from.get(cell)) {
                combed = comb.contains(cell);
            }
            rooms.add(new Found(room, door, steps.get(door), combed));
        }
        rooms.sort(Comparator.comparingInt(Found::steps));
        if (rooms.size() > MOST_ROOMS) {
            rooms = new ArrayList<>(rooms.subList(0, MOST_ROOMS));
        }
        // The queen's room: the nearest free of comb (the old royal chamber), else the nearest.
        Found queen = rooms.stream().filter(room -> !room.combed()).findFirst().orElse(rooms.get(0));
        List<NestPlan.Kind> others = List.of(NestPlan.Kind.NURSERY, NestPlan.Kind.FOOD, NestPlan.Kind.REFUSE);
        List<NestPlan.Chamber> chambers = new ArrayList<>();
        int next = 0;
        for (Found room : rooms) {
            NestPlan.Kind kind = room == queen ? NestPlan.Kind.QUEEN : others.get(Math.min(next++, others.size() - 1));
            List<BlockPos> path = new ArrayList<>();
            for (BlockPos cell = from.get(room.door()); cell != null; cell = from.get(cell)) {
                path.add(0, cell);
            }
            List<BlockPos> way = new ArrayList<>(chimney);
            way.addAll(path);
            way.remove(entrance);
            chambers.add(chamber(kind, way, room.door(), room.room()));
        }
        // The queen's room first, the others in the order they are reached.
        chambers.sort(Comparator.comparingInt(chamber -> chamber.kind() == NestPlan.Kind.QUEEN ? 0 : 1));
        return new Site(entrance, chambers);
    }

    /** Open air all round: the four sides open (out over the hump, or on open ground). */
    private static boolean isOutside(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!isOpen(level, pos.relative(side))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The rooms among these cells: a cell is in a room when it lies in a 2 x 2 square of the cells on its level (a tunnel
     * is 1 wide), and a room is the cells of that kind joined side by side, at least 4 of them.
     */
    private static List<List<BlockPos>> rooms(Set<BlockPos> cells) {
        Set<BlockPos> roomy = new LinkedHashSet<>();
        for (BlockPos cell : cells) {
            for (int sx = -1; sx <= 0; sx++) {
                for (int sz = -1; sz <= 0; sz++) {
                    BlockPos corner = cell.offset(sx, 0, sz);
                    if (cells.contains(corner) && cells.contains(corner.east()) && cells.contains(corner.south()) && cells.contains(corner.south().east())) {
                        roomy.add(cell);
                    }
                }
            }
        }
        List<List<BlockPos>> rooms = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : roomy) {
            if (!seen.add(start)) {
                continue;
            }
            List<BlockPos> room = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                BlockPos cell = queue.poll();
                room.add(cell);
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos next = cell.relative(side);
                    if (roomy.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (room.size() >= 4) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    /**
     * A room with its way in, as the shared nest wants it: {@code way} every cell from under the entrance to the cell
     * before the door, {@code door} the room cell the way reaches, {@code room} the room's cells. The room's cells go in
     * the order they are reached from the door; if the way comes down onto the door from above, the door counts as part
     * of the way (the shared nest counts a room's cells from the side of its way's last cell).
     */
    private static NestPlan.Chamber chamber(NestPlan.Kind kind, List<BlockPos> way, BlockPos door, List<BlockPos> room) {
        Set<BlockPos> left = new HashSet<>(room);
        List<BlockPos> ordered = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(door));
        left.remove(door);
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            ordered.add(cell);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = cell.relative(side);
                if (left.remove(next)) {
                    queue.add(next);
                }
            }
        }
        ordered.addAll(left); // (none: a room is joined side by side)
        List<BlockPos> wayIn = new ArrayList<>(way);
        BlockPos last = wayIn.isEmpty() ? null : wayIn.get(wayIn.size() - 1);
        if (last == null || last.getY() != door.getY()) {
            wayIn.add(door); // came down onto it: the door is the way's end, the rest of the room beside it
            ordered.remove(door);
            ordered.add(door);
        }
        double cx = room.stream().mapToInt(BlockPos::getX).average().orElse(door.getX());
        double cz = room.stream().mapToInt(BlockPos::getZ).average().orElse(door.getZ());
        BlockPos center = room.stream().min(Comparator.comparingDouble(cell -> (cell.getX() - cx) * (cell.getX() - cx)
                + (cell.getZ() - cz) * (cell.getZ() - cz))).orElse(door);
        List<BlockPos> cells = new ArrayList<>(wayIn);
        for (BlockPos cell : ordered) {
            if (!cells.contains(cell)) {
                cells.add(cell);
            }
        }
        return new NestPlan.Chamber(kind, List.copyOf(cells), center, List.copyOf(wayIn), List.copyOf(ordered));
    }

    /** The Matabele colonies settled in a dead mound whose entrance lies within {@code reach} blocks (level) of {@code anchor}. */
    private static List<Colony> colonies(ServerLevel level, BlockPos anchor, int reach) {
        List<Colony> near = new ArrayList<>();
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (IN_A_MOUND.equals(colony.nest().site()) && horizontal(colony.getCenter(), anchor) <= reach) {
                near.add(colony);
            }
        }
        return near;
    }
}
