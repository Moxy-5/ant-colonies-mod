package com.antcolonies.savanna.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.GroundReader;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.registry.ModTags;
import com.antcolonies.savanna.block.DeadCombBlock;
import com.antcolonies.savanna.block.FungusCombBlock;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.registry.SavannaTags;
import com.antcolonies.species.SpeciesProfiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Matabele ant's shallow nest (M8 step 1d, {@code docs/DESIGN.md}, "Matabele colony, details", step 1d): real
 * Megaponera build nothing; they move into spaces already there and dig only to join them. A colony picks its site when
 * it starts, within {@link #LOOK} blocks, the best kind first (the user's pick, 2026-10-24), nearer first among the same kind:
 * <ol>
 * <li><b>A dead termite mound</b> (the favourite): the old nest's rooms under the hump, reached down the open chimney.
 * The empty royal chamber is the queen's at once; a garden room still holding its rotting Xylaria comb is taken up once
 * the comb is gone (the user's pick: ants neither eat nor clear that fungus). A mound whose chimney is still capped has
 * no way in yet and is passed over.</li>
 * <li><b>Under a fallen log</b>: a room dug in the soil right under the trunk (the log its roof), the entrance beside it,
 * and a second room under that one, 4 blocks down (the user's pick: rooms under the log, as the real ants nest in the
 * soil beneath dead wood).</li>
 * <li><b>The ground</b>: a shaft and two rooms, 3 and 5 blocks down (the plan's 2 to 5).</li>
 * </ol>
 * The rooms are handed to the shared nest ({@code Nest.settle}); the digging, the queen's home and the numbers coming
 * out of the rooms are the shared code's.
 */
public final class MatabeleNest {
    /** How far round her a queen looks for a site (blocks; my pick). */
    public static final int LOOK = 32;
    /** No site this near another colony's entrance (blocks; my pick): one colony to an old mound. */
    static final int TAKEN = 10;
    /** How far round a dead mound's middle its old rooms are looked for (the termite gardens lie within about 6). */
    static final int MOUND_REACH = 10;
    /** How deep under a dead mound's entrance its old rooms are looked for. */
    static final int MOUND_DEPTH = 8;
    /** The most rooms a nest takes from a dead mound. */
    static final int MOST_ROOMS = 8;
    /** A lying log: at least this many blocks long on the ground, and at least twice as long as it is wide. */
    static final int LOG_LENGTH = 4;
    /** The rooms under a log: as long as the log, at most this (blocks), 3 wide. */
    static final int LOG_ROOM_LENGTH = 5;
    /** How far from the queen open ground is looked for when her own spot will not take a nest (blocks; my pick). */
    static final int GROUND_LOOK = 8;

    /** The kinds of site, best first. */
    public enum Kind {
        MOUND("in a dead termite mound"), LOG("under a fallen log"), GROUND("in the ground");

        /** The words in the colony list. */
        public final String words;

        Kind(String words) {
            this.words = words;
        }

        /** The kind by its command word ({@code mound}, {@code log}, {@code ground}), or null. */
        @Nullable
        public static Kind byWord(String word) {
            try {
                return valueOf(word.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** A site: its kind, the entrance (the colony's center: the open cell over the way down) and the rooms laid out there. */
    public record Site(Kind kind, BlockPos entrance, List<NestPlan.Chamber> rooms) {
    }

    private MatabeleNest() {
    }

    /**
     * The site a colony starting at {@code feet} settles in: the best kind within {@link #LOOK} blocks, or only the kind
     * asked for ({@code only}, the command's). Null only when the kind asked for is not found (open ground always is).
     */
    @Nullable
    public static Site pick(ServerLevel level, BlockPos feet, @Nullable Kind only) {
        if (only == null || only == Kind.MOUND) {
            Site site = nearestMound(level, feet);
            if (site != null || only == Kind.MOUND) {
                return site;
            }
        }
        if (only == null || only == Kind.LOG) {
            Site site = nearestLog(level, feet);
            if (site != null || only == Kind.LOG) {
                return site;
            }
        }
        return ground(level, feet);
    }

    /** A colony of the Matabele with its queen ({@code queenId}) settled in {@code site}. */
    public static Colony settle(ServerLevel level, Site site, UUID queenId) {
        Colony colony = ColonyManager.get(level).createColony(MatabeleEntity.SPECIES, queenId, site.entrance());
        colony.nest().settle(site.kind().words, site.rooms());
        ColonyManager.get(level).markChanged();
        return colony;
    }

    /** Whether another colony's entrance is within {@link #TAKEN} blocks of this one. */
    private static boolean isTaken(ServerLevel level, BlockPos entrance) {
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (colony.getCenter().distManhattan(entrance) <= TAKEN * 2 && horizontal(colony.getCenter(), entrance) <= TAKEN) {
                return true;
            }
        }
        return false;
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
            if (site != null && !isTaken(level, site.entrance())) {
                return site;
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
        return new Site(Kind.MOUND, entrance, chambers);
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

    // ----- Under a fallen log -----

    @Nullable
    private static Site nearestLog(ServerLevel level, BlockPos feet) {
        List<BlockPos> logs = new ArrayList<>();
        for (int dx = -LOOK; dx <= LOOK; dx++) {
            for (int dz = -LOOK; dz <= LOOK; dz++) {
                if (dx * dx + dz * dz > LOOK * LOOK) {
                    continue;
                }
                int x = feet.getX() + dx;
                int z = feet.getZ() + dz;
                if (!level.isLoaded(new BlockPos(x, feet.getY(), z))) {
                    continue;
                }
                // A log block of the column lying on the ground, near the queen's height.
                for (int y = feet.getY() + 6; y >= feet.getY() - 8; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(SavannaTags.MATABELE_NEST_LOGS) && level.getBlockState(pos.below()).is(ModTags.DIGGABLE)) {
                        logs.add(pos);
                        break;
                    }
                }
            }
        }
        logs.sort(Comparator.comparingDouble(pos -> distance(pos, feet)));
        Set<BlockPos> tried = new HashSet<>();
        for (BlockPos log : logs) {
            if (!tried.add(log)) {
                continue;
            }
            Site site = fromLog(level, log, tried);
            if (site != null && !isTaken(level, site.entrance())) {
                return site;
            }
        }
        return null;
    }

    /** How many log blocks lie on the ground in a row from {@code pos} toward {@code side} (not counting {@code pos}). */
    private static int run(ServerLevel level, BlockPos pos, Direction side) {
        int n = 0;
        BlockPos at = pos.relative(side);
        while (n < 32 && level.getBlockState(at).is(SavannaTags.MATABELE_NEST_LOGS)) {
            n++;
            at = at.relative(side);
        }
        return n;
    }

    /**
     * The nest under the log lying on the ground at {@code log} (one of its lowest blocks): a room right under the
     * trunk, 2 blocks down, as long as the log (at most {@link #LOG_ROOM_LENGTH}) and 3 wide, with a short shaft down
     * from beside the log; under it a second room 4 blocks down, down a stair at its far end. Null when the wood is no
     * lying log (a standing trunk's foot is as wide as it is long) or the ground will not take the rooms.
     * {@code tried}: the log's blocks are added (each log is tried once).
     */
    @Nullable
    static Site fromLog(ServerLevel level, BlockPos log, Set<BlockPos> tried) {
        int alongX = run(level, log, Direction.EAST) + run(level, log, Direction.WEST) + 1;
        int alongZ = run(level, log, Direction.SOUTH) + run(level, log, Direction.NORTH) + 1;
        Direction along = alongX >= alongZ ? Direction.EAST : Direction.SOUTH;
        int length = Math.max(alongX, alongZ);
        int width = Math.min(alongX, alongZ);
        if (length < LOG_LENGTH || length < 2 * width) {
            return null;
        }
        // The middle of the lying row.
        int back = run(level, log, along.getOpposite());
        BlockPos start = log.relative(along.getOpposite(), back);
        for (int i = 0; i < length; i++) {
            tried.add(start.relative(along, i));
        }
        BlockPos middle = start.relative(along, length / 2);
        int roomLength = Math.min(LOG_ROOM_LENGTH, length);
        int half = roomLength / 2;
        for (Direction side : List.of(along.getClockWise(), along.getCounterClockWise())) {
            int beside = run(level, middle, side); // how far the log reaches out this side from its middle row
            int out = Math.max(beside + 1, 2);
            BlockPos entrance = middle.relative(side, out);
            if (!isOpen(level, entrance) || !level.getBlockState(entrance.below()).is(ModTags.DIGGABLE)) {
                continue;
            }
            int y1 = middle.getY() - 2;
            List<BlockPos> way = new ArrayList<>(List.of(entrance.below(), entrance.below(2)));
            for (int j = out - 1; j >= 2; j--) {
                way.add(middle.relative(side, j).below(2));
            }
            List<BlockPos> room = new ArrayList<>();
            for (int a = -half; a <= roomLength - 1 - half; a++) {
                for (int j = -1; j <= 1; j++) {
                    room.add(new BlockPos(middle.relative(along, a).relative(side, j)).atY(y1));
                }
            }
            BlockPos door = middle.relative(side, 1).atY(y1);
            NestPlan.Chamber upper = chamber(NestPlan.Kind.FOOD, way, door, room);
            // The deeper room, under the first, down a stair past its far end.
            BlockPos end = middle.relative(along, roomLength - 1 - half).atY(y1);
            BlockPos step = end.relative(along);
            List<BlockPos> stair = List.of(step, step.below(), step.below(2));
            List<BlockPos> lower = new ArrayList<>();
            for (int a = roomLength - 1 - half - 2; a <= roomLength - 1 - half; a++) {
                for (int j = -1; j <= 1; j++) {
                    lower.add(middle.relative(along, a).relative(side, j).atY(y1 - 2));
                }
            }
            List<BlockPos> lowerWay = new ArrayList<>(upper.wayIn());
            lowerWay.addAll(pathAcross(upper.room(), door, end));
            lowerWay.addAll(stair);
            NestPlan.Chamber deep = chamber(NestPlan.Kind.QUEEN, lowerWay, end.atY(y1 - 2), lower);
            if (!fits(level, upper.cells()) || !roofed(level, upper.room())) {
                continue;
            }
            List<NestPlan.Chamber> rooms = new ArrayList<>();
            if (fits(level, deep.cells()) && roofed(level, lower)) {
                rooms.add(upper);
                rooms.add(deep);
            } else {
                rooms.add(new NestPlan.Chamber(NestPlan.Kind.QUEEN, upper.cells(), upper.center(), upper.wayIn(), upper.room()));
            }
            return new Site(Kind.LOG, entrance, rooms);
        }
        return null;
    }

    /** The cells of a room walked from {@code door} to {@code to} (a straight line along the room, then across). */
    private static List<BlockPos> pathAcross(List<BlockPos> room, BlockPos door, BlockPos to) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos at = door;
        path.add(at);
        Set<BlockPos> cells = new HashSet<>(room);
        for (int i = 0; i < 32 && !at.equals(to); i++) {
            BlockPos next = at.getX() != to.getX() ? at.relative(at.getX() < to.getX() ? Direction.EAST : Direction.WEST)
                    : at.relative(at.getZ() < to.getZ() ? Direction.SOUTH : Direction.NORTH);
            if (!cells.contains(next)) {
                break;
            }
            at = next;
            path.add(at);
        }
        return path;
    }

    /** Every cell is soil to dig or already open under the ground (out of the sky), none beside water. */
    private static boolean fits(ServerLevel level, List<BlockPos> cells) {
        for (BlockPos pos : cells) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.is(ModTags.DIGGABLE) || isOpen(level, pos) && !level.canSeeSky(pos))) {
                return false;
            }
            for (Direction side : Direction.values()) {
                if (!level.getFluidState(pos.relative(side)).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The room has a roof: no cell over it is open to the sky. */
    private static boolean roofed(ServerLevel level, List<BlockPos> room) {
        for (BlockPos pos : room) {
            BlockPos above = pos.above();
            if (isOpen(level, above) && level.canSeeSky(above)) {
                return false;
            }
        }
        return true;
    }

    // ----- The ground -----

    /**
     * A small nest in the ground (a shaft and two rooms, 3 and 5 blocks down, as the shared plan lays them out), at the
     * queen's own spot if the soil takes it, else at the nearest spot within {@link #GROUND_LOOK} that does; one room 2
     * down where none takes two; at her spot anyway when nowhere does (they dig what soil there is).
     */
    static Site ground(ServerLevel level, BlockPos feet) {
        NestPlan.RoomSizes sizes = SpeciesProfiles.get(MatabeleEntity.SPECIES).nest().roomSizes();
        List<BlockPos> spots = new ArrayList<>();
        for (int r = 0; r <= GROUND_LOOK; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && level.isLoaded(feet.offset(dx, 0, dz))) {
                        BlockPos top = groundTop(level, feet.getX() + dx, feet.getZ() + dz, feet.getY());
                        if (top != null) {
                            spots.add(top.above());
                        }
                    }
                }
            }
        }
        for (int[] plan : new int[][] {{3, 2}, {2, 1}}) {
            for (BlockPos spot : spots) {
                if (isTaken(level, spot)) {
                    continue;
                }
                List<NestPlan.Chamber> rooms = groundRooms(spot, plan[0], plan[1], sizes, level);
                if (rooms != null) {
                    return new Site(Kind.GROUND, spot, rooms);
                }
            }
        }
        BlockPos top = groundTop(level, feet.getX(), feet.getZ(), feet.getY());
        BlockPos spot = top != null ? top.above() : GroundReader.surface(level, feet.getX(), feet.getZ()).above();
        return new Site(Kind.GROUND, spot, laidOut(spot, 3, 2, Direction.from2DDataValue(Math.floorMod(spot.hashCode(), 4)), sizes));
    }

    /**
     * The top ground block of a column near the height {@code y} (3 above to 12 below, so a roof over the land, a
     * cave's or a test arena's, is never taken for the ground): solid, with open air over it; plants count as air.
     */
    @Nullable
    private static BlockPos groundTop(ServerLevel level, int x, int z, int y) {
        for (int at = y + 3; at >= y - 12; at--) {
            BlockPos pos = new BlockPos(x, at, z);
            BlockState state = level.getBlockState(pos);
            if (!state.canBeReplaced() && !isOpen(level, pos) && isOpen(level, pos.above())) {
                return pos;
            }
        }
        return null;
    }

    /** The rooms of a ground nest at {@code entrance} that fit the soil there, the first side that does; null if none does. */
    @Nullable
    private static List<NestPlan.Chamber> groundRooms(BlockPos entrance, int depth, int levels, NestPlan.RoomSizes sizes, ServerLevel level) {
        int first = Math.floorMod(entrance.hashCode(), 4);
        for (int i = 0; i < 4; i++) {
            List<NestPlan.Chamber> rooms = laidOut(entrance, depth, levels, Direction.from2DDataValue((first + i) % 4), sizes);
            boolean fits = true;
            for (NestPlan.Chamber room : rooms) {
                fits &= fits(level, room.cells()) && roofed(level, room.room());
            }
            if (fits) {
                return rooms;
            }
        }
        return null;
    }

    /** The shared plan's levels under {@code entrance}, each with the shaft put in front of its way (a ready nest has no shaft of its own). */
    private static List<NestPlan.Chamber> laidOut(BlockPos entrance, int depth, int levels, Direction direction, NestPlan.RoomSizes sizes) {
        List<BlockPos> shaft = new ArrayList<>();
        for (int i = 1; i <= depth; i++) {
            shaft.add(entrance.below(i));
        }
        List<NestPlan.Chamber> rooms = new ArrayList<>();
        for (NestPlan.Chamber level : NestPlan.chambers(entrance.below(depth), direction, levels, sizes)) {
            List<BlockPos> cells = new ArrayList<>(shaft);
            cells.addAll(level.cells());
            List<BlockPos> wayIn = new ArrayList<>(shaft);
            wayIn.addAll(level.wayIn());
            rooms.add(new NestPlan.Chamber(level.kind(), List.copyOf(cells), level.center(), List.copyOf(wayIn), level.room()));
        }
        return rooms;
    }
}
