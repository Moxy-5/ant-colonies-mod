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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Matabele ant's shallow nest (M8 step 1d, {@code docs/DESIGN.md}, "Matabele colony, details", step 1d): real
 * Megaponera build nothing; they move into spaces other animals made. The user's choice (R1, 2026-10-24, a known departure
 * from the real ants, on purpose): under wood they dig one small room of their own; a dead mound is used as it is, nothing
 * dug. A colony picks its site when
 * it starts, within {@link #LOOK} blocks, the best kind first (the user's pick, 2026-10-24), nearer first among the same kind:
 * <ol>
 * <li><b>A dead termite mound</b> (the favourite): the old nest's rooms under the hump, reached down the open chimney.
 * The empty royal chamber is the queen's at once; a garden room still holding its rotting Xylaria comb is taken up once
 * the comb is gone (the user's pick: ants neither eat nor clear that fungus). A mound whose chimney is still capped has
 * no way in yet and is passed over.</li>
 * <li><b>Under a fallen log</b> (the user, 2026-10-24, from the real ants, which nest in the shallow ground under dead wood
 * and build nothing): a log 3 or more blocks across and at least twice as long is a roof; a small room, 3 by 3 blocks, is
 * dug one block down in the soil under it, the log its ceiling, the entrance beside the log. A long log has room for two
 * colonies, one to every 4 blocks of its length, at most 2.</li>
 * <li><b>At the foot of a tree</b>: the same small room dug one block down at the foot of a standing trunk 3 or more blocks
 * across, dead or living (the user, 2026-10-24: real nests lie "under trees", Yusuf 2026); one colony to a tree.</li>
 * <li><b>The ground</b>: no site without cover is real; this stands in for an animal's burrow (aardvark, rodent) the ants
 * would move into: a tunnel sloping in to one room 3 blocks down, kept so a queen put down in bare land has a home.</li>
 * </ol>
 * The rooms are handed to the shared nest ({@code Nest.settle}); the digging, the queen's home and the numbers coming
 * out of the rooms are the shared code's.
 */
public final class MatabeleNest {
    /** How far round her a queen looks for a site (blocks; my pick). */
    public static final int LOOK = 32;
    /**
     * No burrow this near another colony's entrance (blocks; the user, 2026-10-24: real nests lie as close as 2 m), and no
     * burrow room within a block of another colony's rooms (a wall of soil between them always).
     */
    static final int TAKEN = 3;
    /** The most colonies under one big dead fallen log, one to every {@link #LOG_SLOT} blocks of its length (the user, 2026-10-24). */
    static final int LOG_COLONIES = 2;
    static final int LOG_SLOT = 4;
    /** How far round a dead mound's middle its old rooms are looked for (the termite gardens lie within about 6). */
    static final int MOUND_REACH = 10;
    /** How deep under a dead mound's entrance its old rooms are looked for. */
    static final int MOUND_DEPTH = 8;
    /** The most rooms a nest takes from a dead mound. */
    static final int MOST_ROOMS = 8;
    /** A log is a roof only when this many blocks across (the user, 2026-10-24), and at least twice as long as it is wide. */
    static final int LOG_WIDTH = 3;
    /** The room dug under dead wood: this many blocks square, one block down. */
    static final int WOOD_ROOM = 3;
    /** How far from the queen open ground is looked for when her own spot will not take a nest (blocks; my pick). */
    static final int GROUND_LOOK = 8;
    /** How deep the burrow's room lies (blocks under the entrance; my pick: 2 blocks of soil over it, so the slope is roofed). */
    static final int BURROW_DEPTH = 3;

    /** The kinds of site, best first. */
    public enum Kind {
        MOUND("in a dead termite mound"), LOG("under a fallen log"), STANDING("at the foot of a tree"), GROUND("in the ground");

        /** The words in the colony list. */
        public final String words;

        Kind(String words) {
            this.words = words;
        }

        /** The kind by its command word ({@code mound}, {@code log}, {@code standing}, {@code ground}), or null. */
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
        List<BlockPos> wood = woodBases(level, feet);
        if (only == null || only == Kind.LOG) {
            Site site = nearestLog(level, wood);
            if (site != null || only == Kind.LOG) {
                return site;
            }
        }
        if (only == null || only == Kind.STANDING) {
            Site site = nearestStanding(level, wood);
            if (site != null || only == Kind.STANDING) {
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

    /** Whether any cell of {@code rooms} lies next to (or in) a cell of another colony's nest near {@code entrance}. */
    private static boolean touchesAnother(ServerLevel level, BlockPos entrance, List<NestPlan.Chamber> rooms) {
        Set<BlockPos> mine = new HashSet<>();
        for (NestPlan.Chamber room : rooms) {
            mine.addAll(room.cells());
        }
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (horizontal(colony.getCenter(), entrance) > 24) {
                continue;
            }
            for (NestPlan.Chamber room : colony.nest().getPlannedChambers(level)) {
                for (BlockPos cell : room.cells()) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (mine.contains(cell.offset(dx, dy, dz))) {
                                    return true;
                                }
                            }
                        }
                    }
                }
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
            if (site != null && colonies(level, Kind.MOUND, site.entrance(), 1).isEmpty()) {
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

    // ----- Under wood: a fallen log, the foot of a tree -----

    /** The Matabele colonies settled {@code kind} whose entrance lies within {@code reach} blocks (level) of {@code anchor}. */
    private static List<Colony> colonies(ServerLevel level, Kind kind, BlockPos anchor, int reach) {
        List<Colony> near = new ArrayList<>();
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (kind.words.equals(colony.nest().site()) && horizontal(colony.getCenter(), anchor) <= reach) {
                near.add(colony);
            }
        }
        return near;
    }

    /** The lowest wood blocks near {@code feet}: one per column, a dead wood block resting on soil, nearest first. */
    private static List<BlockPos> woodBases(ServerLevel level, BlockPos feet) {
        List<BlockPos> bases = new ArrayList<>();
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
                // A wood block of the column resting on the ground, near the queen's height.
                for (int y = feet.getY() + 6; y >= feet.getY() - 8; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(SavannaTags.MATABELE_NEST_LOGS) && level.getBlockState(pos.below()).is(ModTags.DIGGABLE)) {
                        bases.add(pos);
                        break;
                    }
                }
            }
        }
        bases.sort(Comparator.comparingDouble(pos -> distance(pos, feet)));
        return bases;
    }

    @Nullable
    private static Site nearestLog(ServerLevel level, List<BlockPos> wood) {
        Set<BlockPos> tried = new HashSet<>();
        for (BlockPos log : wood) {
            if (!tried.add(log)) {
                continue;
            }
            Site site = fromLog(level, log, tried);
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    @Nullable
    private static Site nearestStanding(ServerLevel level, List<BlockPos> wood) {
        Set<BlockPos> tried = new HashSet<>();
        for (BlockPos base : wood) {
            if (!tried.add(base)) {
                continue;
            }
            Site site = fromStanding(level, base, tried);
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    /** How many wood blocks lie in a row from {@code pos} toward {@code side} (not counting {@code pos}), at the same height. */
    private static int run(ServerLevel level, BlockPos pos, Direction side) {
        int n = 0;
        BlockPos at = pos.relative(side);
        while (n < 32 && level.getBlockState(at).is(SavannaTags.MATABELE_NEST_LOGS)) {
            n++;
            at = at.relative(side);
        }
        return n;
    }

    /** {@code pos} moved along {@code axis}' line to the middle of the wood's row through it. */
    private static BlockPos centred(ServerLevel level, BlockPos pos, Direction axis) {
        return pos.relative(axis, (run(level, pos, axis) - run(level, pos, axis.getOpposite())) / 2);
    }

    /**
     * The nest under the log lying on the ground at {@code log} (one of its lowest blocks): the log must be at least
     * {@link #LOG_WIDTH} across and twice as long as wide (else it is no roof, or no log: a standing trunk's foot is as
     * wide as it is long). It has room for one colony to every {@link #LOG_SLOT} blocks of its length, at most
     * {@link #LOG_COLONIES}; the first free place gets the small room under the trunk (one block down, the log its roof)
     * and the entrance beside the log. Null when the wood is no such log, its places are all taken, or the ground will
     * not take the room. {@code tried}: the log's blocks are added (each log is tried once).
     */
    @Nullable
    static Site fromLog(ServerLevel level, BlockPos log, Set<BlockPos> tried) {
        int alongX = run(level, log, Direction.EAST) + run(level, log, Direction.WEST) + 1;
        int alongZ = run(level, log, Direction.SOUTH) + run(level, log, Direction.NORTH) + 1;
        Direction along = alongX >= alongZ ? Direction.EAST : Direction.SOUTH;
        Direction across = along.getClockWise();
        int length = Math.max(alongX, alongZ);
        int width = Math.min(alongX, alongZ);
        // (the log's blocks count as tried even when it is no roof)
        int back = run(level, log, along.getOpposite());
        BlockPos end = log.relative(along.getOpposite(), back);
        for (int i = 0; i < length; i++) {
            tried.add(end.relative(along, i));
        }
        if (width < LOG_WIDTH || length < 2 * width || length < LOG_SLOT) {
            return null;
        }
        BlockPos start = centred(level, end, across);
        int slots = Math.min(LOG_COLONIES, length / LOG_SLOT);
        BlockPos middle = start.relative(along, length / 2);
        for (int slot = 0; slot < slots; slot++) {
            int a = (int) ((slot + 0.5) * length / slots);
            BlockPos centre = start.relative(along, a);
            boolean taken = false;
            for (Colony colony : colonies(level, Kind.LOG, middle, length / 2 + width + 3)) {
                int offset = Math.abs((colony.getCenter().getX() - centre.getX()) * along.getStepX()
                        + (colony.getCenter().getZ() - centre.getZ()) * along.getStepZ());
                taken |= offset <= LOG_SLOT / 2;
            }
            if (taken) {
                continue;
            }
            Site site = roomUnder(level, Kind.LOG, centre, List.of(across, across.getOpposite()));
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    /**
     * The nest at the foot of a standing tree, dead or living, whose lowest block is {@code base}: a trunk at least {@link #LOG_WIDTH}
     * across both ways, not twice as long as wide (that is a lying log) and at least 4 blocks tall; the small room under
     * the middle of its foot, one block down; one colony to a tree.
     */
    @Nullable
    static Site fromStanding(ServerLevel level, BlockPos base, Set<BlockPos> tried) {
        int alongX = run(level, base, Direction.EAST) + run(level, base, Direction.WEST) + 1;
        int alongZ = run(level, base, Direction.SOUTH) + run(level, base, Direction.NORTH) + 1;
        if (Math.min(alongX, alongZ) < LOG_WIDTH || Math.max(alongX, alongZ) >= 2 * Math.min(alongX, alongZ)
                || !level.getBlockState(base.above(3)).is(SavannaTags.MATABELE_NEST_LOGS)) {
            return null;
        }
        BlockPos centre = centred(level, centred(level, base, Direction.EAST), Direction.SOUTH);
        for (int dx = -alongX; dx <= alongX; dx++) {
            for (int dz = -alongZ; dz <= alongZ; dz++) {
                tried.add(centre.offset(dx, 0, dz));
            }
        }
        if (!colonies(level, Kind.STANDING, centre, Math.max(alongX, alongZ) + 3).isEmpty()) {
            return null;
        }
        return roomUnder(level, Kind.STANDING, centre, List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
    }

    /**
     * The small room dug one block down under the dead wood whose middle (on its lowest row) is {@code centre}: 3 by 3,
     * every cell under wood (the wood is the roof), the entrance beside the wood on the first of {@code sides} where the
     * ground takes it, a short way in at the room's level. Null when no side does.
     */
    @Nullable
    private static Site roomUnder(ServerLevel level, Kind kind, BlockPos centre, List<Direction> sides) {
        int y1 = centre.getY() - 1;
        int reach = WOOD_ROOM / 2;
        List<BlockPos> room = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                BlockPos cell = centre.offset(dx, 0, dz).atY(y1);
                if (!level.getBlockState(cell.above()).is(SavannaTags.MATABELE_NEST_LOGS)) {
                    return null; // the wood is not over the whole room: no roof
                }
                room.add(cell);
            }
        }
        if (!fits(level, room) || !roofed(level, room)) {
            return null;
        }
        for (Direction side : sides) {
            int out = run(level, centre, side) + 1;
            BlockPos entrance = centre.relative(side, out);
            if (out < reach + 1 || !isOpen(level, entrance) || !level.getBlockState(entrance.below()).is(ModTags.DIGGABLE)) {
                continue;
            }
            List<BlockPos> way = new ArrayList<>(List.of(entrance.below()));
            for (int j = out - 1; j > reach; j--) {
                way.add(centre.relative(side, j).atY(y1));
            }
            if (!fits(level, way)) {
                continue;
            }
            BlockPos door = centre.relative(side, reach).atY(y1);
            return new Site(kind, entrance, List.of(chamber(NestPlan.Kind.QUEEN, way, door, room)));
        }
        return null;
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
     * A stand-in for an animal's burrow (the user, 2026-10-24: no real site is without cover): a tunnel sloping in like an
     * aardvark's or a rodent's ({@link #burrow}, the user's ask, 2026-10-24: no straight shaft as the harvester digs) to one
     * room {@link #BURROW_DEPTH} blocks down, at the queen's own spot if the soil takes it, else at the nearest spot within
     * {@link #GROUND_LOOK} that does; at her spot anyway when nowhere does (they dig what soil there is).
     */
    static Site ground(ServerLevel level, BlockPos feet) {
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
        for (BlockPos spot : spots) {
            if (isTaken(level, spot)) {
                continue;
            }
            List<NestPlan.Chamber> rooms = groundRooms(spot, level);
            if (rooms != null) {
                return new Site(Kind.GROUND, spot, rooms);
            }
        }
        BlockPos top = groundTop(level, feet.getX(), feet.getZ(), feet.getY());
        BlockPos spot = top != null ? top.above() : GroundReader.surface(level, feet.getX(), feet.getZ()).above();
        return new Site(Kind.GROUND, spot, burrow(spot, Direction.from2DDataValue(Math.floorMod(spot.hashCode(), 4))));
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

    /**
     * The rooms of a ground nest at {@code entrance} that fit the soil there and keep a block of soil from every other
     * colony's rooms, the first side that does; null if none does.
     */
    @Nullable
    private static List<NestPlan.Chamber> groundRooms(BlockPos entrance, ServerLevel level) {
        int first = Math.floorMod(entrance.hashCode(), 4);
        for (int i = 0; i < 4; i++) {
            List<NestPlan.Chamber> rooms = burrow(entrance, Direction.from2DDataValue((first + i) % 4));
            boolean fits = true;
            for (NestPlan.Chamber room : rooms) {
                fits &= fits(level, room.cells()) && roofed(level, room.room());
            }
            if (fits && !touchesAnother(level, entrance, rooms)) {
                return rooms;
            }
        }
        return null;
    }

    /**
     * The burrow at {@code entrance} sloping in toward {@code direction}: a stair tunnel one block wide going one block down
     * for every block forward (the mouth an open groove, then roofed by the soil over it), to one room
     * {@link #WOOD_ROOM} blocks square {@link #BURROW_DEPTH} blocks down, the queen's.
     */
    private static List<NestPlan.Chamber> burrow(BlockPos entrance, Direction direction) {
        List<BlockPos> way = new ArrayList<>(List.of(entrance.below()));
        for (int k = 1; k < BURROW_DEPTH; k++) {
            BlockPos step = entrance.relative(direction, k);
            way.add(step.below(k));
            way.add(step.below(k + 1));
        }
        BlockPos door = entrance.relative(direction, BURROW_DEPTH).below(BURROW_DEPTH);
        int reach = WOOD_ROOM / 2;
        BlockPos middle = door.relative(direction, reach);
        Direction side = direction.getClockWise();
        List<BlockPos> room = new ArrayList<>();
        for (int a = -reach; a <= reach; a++) {
            for (int b = -reach; b <= reach; b++) {
                room.add(middle.relative(direction, a).relative(side, b));
            }
        }
        return List.of(chamber(NestPlan.Kind.QUEEN, way, door, room));
    }
}
