package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.LevelCellMap;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * One colony's "brain" data. Species-neutral: the species is just an id that will point at a
 * species profile later. Member counts are stored per caste (mostly virtual, not real mobs).
 */
public class Colony {
    private final UUID id;
    private final ResourceLocation species;
    private final UUID queenId;
    private final BlockPos center;
    private final Map<String, Integer> casteCounts = new LinkedHashMap<>();

    public Colony(UUID id, ResourceLocation species, UUID queenId, BlockPos center) {
        this.id = id;
        this.species = species;
        this.queenId = queenId;
        this.center = center;
    }

    /** How deep the entrance shaft goes, in blocks (will come from the species profile later). */
    public static final int SHAFT_DEPTH = 3;
    /**
     * Where the soil is too thin or too stony for the rooms at full depth, the whole nest sits one block higher
     * (the shaft is one block shorter). Never shallower than this.
     */
    public static final int MIN_SHAFT_DEPTH = 2;

    /** The mound reaches this far (in blocks, sideways) from the entrance. */
    public static final int MOUND_RADIUS = 3;

    /**
     * How tall the mound is meant to be this far (in whole blocks, sideways and forwards) from the entrance:
     * a round hill that is 3 blocks high in the first ring around the hole, 2 in the second ring, 1 in the
     * third, and nothing further out. The hole itself (0, 0) stays open. Adds up to 64 blocks.
     */
    public static int moundHeightAt(int dx, int dz) {
        int distanceSquared = dx * dx + dz * dz;
        if (distanceSquared == 0) {
            return 0;
        }
        if (distanceSquared <= 2) {
            return 3;
        }
        return distanceSquared <= 5 ? 2 : distanceSquared <= 10 ? 1 : 0;
    }

    /** Bite progress per block (0..1). Not saved: a half-dug block just restarts after a reload. */
    private final Map<BlockPos, Float> digProgress = new HashMap<>();

    /**
     * Where the nest's rooms go, and which of them have been given up. Decided once (see {@link #getLayout}) and
     * saved with the colony, so a nest is never planned differently after a reload.
     */
    @Nullable
    private NestLayout layout;

    /** Tells the colony manager that something worth saving has changed. */
    private Runnable onChange = () -> {
    };

    public void setChangeListener(Runnable onChange) {
        this.onChange = onChange;
    }

    /** How long (in ticks) an ant that went to close a leak counts as working on it, if it never says it has stopped. */
    private static final int LEAK_CLAIM_TICKS = 400;
    /** As many ants as this work on one opening at the same time; more go and help somewhere else. */
    private static final int MAX_WORKERS_PER_LEAK = 8;
    /** An ant takes a free opening over a crowded one that is closer by up to this many blocks per ant already there. */
    private static final double CROWD_PENALTY = 4.0;

    /** Who works on which opening, and how far along it is (shared, so more ants close it sooner). */
    private final SealBoard board = new SealBoard();

    /**
     * An opening in the nest walls: {@code hole} is open space (a cave, a crack, water) right next to a dug-out
     * nest cell, and {@code stand} is a dug-out cell with a floor, no further than one block from the hole, where
     * an ant can stand to close it (null when no ant can get close enough). The entrance itself is never a leak.
     * {@code fluidOnly}: the opening only counts as long as there is water or lava in it (a piece of a stream that
     * has run dry needs no soil).
     */
    public record Leak(BlockPos hole, @Nullable BlockPos stand, boolean fluidOnly) {
        public Leak(BlockPos hole, @Nullable BlockPos stand) {
            this(hole, stand, false);
        }
    }

    /**
     * Water or lava heading for the nest. SURFACE: a running stream on the square of the hill (the ants above go
     * and block its source). INSIDE: it has got into the hole or the nest. A pool next to the hill that is not
     * running into it is not an alert. From the first warning on, the ants below close the doors
     * of the rooms: water and lava are far quicker than an ant, so they must not wait until it is in the nest.
     */
    public enum Alert {
        NONE, SURFACE, INSIDE
    }

    private static final int ALERT_CHECK_TICKS = 5;
    /** How often the rooms are checked for water, lava, and a pool that has got in their way. */
    private static final int ROOM_CHECK_TICKS = 20;
    /** A room that is given up: ants inside get this long to come out before its door is shut. */
    private static final int EVACUATE_TICKS = 100;
    /** If the door of a given-up room is still open after this long, it stops holding up the digging. */
    private static final int GIVEN_UP_PATIENCE_TICKS = 1200;
    /** The doors stay shut this long after the last water or lava was seen (10 seconds), then the ants open them again. */
    private static final int CALM_TICKS = 200;
    /** Soil packed into the hole is bitten out again this far above the entrance cell (the hill is 3 blocks high around it). */
    private static final int ENTRANCE_REOPEN_HEIGHT = 3;
    /** At most this many pieces of a stream are looked at when no source can be reached. */
    private static final int STREAM_PIECES_LOOKED_AT = 40;

    private long alertCheckedAt = -1000;
    private long roomsCheckedAt = -1000;
    private Alert alert = Alert.NONE;
    private long lastAlertAt = -1000000;
    private final Set<BlockPos> hazardCells = new HashSet<>();
    /** Where the water or lava that is about comes from; null while there is none. Worked out with every look. */
    @Nullable
    private FluidTrace trace;

    /**
     * The next block to dig, or null if all the digging is done (or blocked). Order: the entrance
     * shaft, then the queen's tunnel and room, then the nursery, then the food store. The shaft is
     * never widened, so its walls stay solid for the ants to climb.
     */
    @Nullable
    public BlockPos findDigTarget(Level level) {
        // No digging (and so no opening of the doors) while water or lava is about, and for a while after.
        if (isAlarmed(level)) {
            return null;
        }
        // The rooms are planned before the first block is dug, because the plan decides how deep the shaft goes.
        int depth = shaftDepth(level);
        List<BlockPos> shaft = new ArrayList<>();
        // Soil the ants packed into the hole itself (to block a source poured straight in) is bitten out again, top first.
        for (int i = ENTRANCE_REOPEN_HEIGHT; i >= 0; i--) {
            BlockPos pos = center.above(i);
            if (level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.BURROWED_DIRT.get())) {
                shaft.add(pos);
            }
        }
        for (int i = 1; i <= depth; i++) {
            shaft.add(center.below(i));
        }
        BlockPos next = firstDiggable(level, shaft);
        if (next != null) {
            return next;
        }
        // Only start the tunnels once the shaft is completely open down to its bottom.
        BlockPos bottom = center.below(depth);
        if (!level.isLoaded(bottom) || !level.getBlockState(bottom).isAir()) {
            return null;
        }
        NestLayout planned = getLayout(level);
        if (planned == null) {
            return null;
        }
        for (List<BlockPos> cells : planned.digLists()) {
            next = firstDiggable(level, cells);
            if (next != null) {
                return next;
            }
        }
        return null;
    }

    /**
     * The middle of the queen's room, once the shaft and her room (with its tunnel) are dug out; null while
     * there is still digging to do for it (or the chunk is not loaded). The other rooms don't matter here.
     */
    @Nullable
    public BlockPos getFinishedRoomCenter(Level level) {
        NestLayout planned = getLayout(level);
        NestLayout.Room queenRoom = planned == null ? null : planned.activeQueenRoom();
        if (queenRoom == null) {
            return null;
        }
        for (int i = 1; i <= planned.shaftDepth(); i++) {
            if (!level.isLoaded(center.below(i)) || !level.getBlockState(center.below(i)).isAir()) {
                return null;
            }
        }
        NestPlan.Chamber queen = queenRoom.chamber;
        for (BlockPos pos : queen.cells()) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                return null;
            }
        }
        return queen.center();
    }

    /** The rooms this nest is being dug with, queen's room first (empty until the layout is decided). Given-up rooms are not in it. For tests and debugging. */
    public List<NestPlan.Chamber> getPlannedChambers(Level level) {
        NestLayout planned = getLayout(level);
        return planned == null ? List.of() : planned.activeChambers();
    }

    /** The doors of the rooms that were given up (for tests and debugging). */
    public List<BlockPos> getGivenUpDoors() {
        List<BlockPos> doors = new ArrayList<>();
        if (layout != null) {
            for (NestLayout.Room room : layout.rooms()) {
                if (room.ruined) {
                    doors.add(room.door());
                }
            }
        }
        return doors;
    }

    /** Every block that is part of the nest plan: the shaft, and the rooms once their layout is decided. */
    private Set<BlockPos> nestCells(Level level) {
        Set<BlockPos> cells = new HashSet<>();
        int depth = shaftDepth(level);
        for (int i = 1; i <= depth; i++) {
            cells.add(center.below(i));
        }
        BlockPos bottom = center.below(depth);
        if (level.isLoaded(bottom) && level.getBlockState(bottom).isAir()) {
            NestLayout planned = getLayout(level);
            if (planned != null) {
                cells.addAll(planned.cells());
            }
        }
        return cells;
    }

    /**
     * Every opening in the walls of the dug-out nest: any open block (air, water, lava, grass) that touches a dug
     * nest cell but is not itself part of the nest. Caves, cracks and the gaps between rooms all show up here.
     * The entrance hole above the shaft is the one opening that is allowed. For tests, and for {@link #findLeak}.
     */
    public List<Leak> getLeaks(Level level) {
        Set<BlockPos> nest = nestCells(level);
        Set<BlockPos> seen = new HashSet<>();
        List<Leak> leaks = new ArrayList<>();
        for (BlockPos cell : nest) {
            if (!level.isLoaded(cell) || !level.getBlockState(cell).isAir()) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos hole = cell.relative(direction);
                if (nest.contains(hole) || hole.equals(center) || !seen.add(hole) || !isOpen(level, hole)) {
                    continue;
                }
                NestLayout.Room gone = layout == null ? null : layout.givenUpRoomAt(hole);
                if (gone != null && isEvacuating(level, gone)) {
                    continue; // ants are still on their way out of that room
                }
                leaks.add(new Leak(hole, findPlugSpot(level, nest, hole)));
            }
        }
        return leaks;
    }

    /** The current alert level, looked at again every quarter of a second at most. */
    public Alert getAlert(Level level) {
        long now = level.getGameTime();
        if (now - alertCheckedAt >= ALERT_CHECK_TICKS || now < alertCheckedAt) {
            scanHazards(level, now);
        }
        return alert;
    }

    /**
     * The emergency signal: true when the ants must drop what they are doing because water or lava is about, or
     * because a room that was given up still has an open door. Every ant of the colony answers it, wherever it is.
     */
    public boolean isEmergency(Level level) {
        return getAlert(level) != Alert.NONE || hasOpenGivenUpDoor(level);
    }

    /** True during an emergency, and for a few seconds after it (nobody digs then, so the doors stay shut). */
    public boolean isAlarmed(Level level) {
        return isEmergency(level) || level.getGameTime() - lastAlertAt < CALM_TICKS;
    }

    private boolean hasOpenGivenUpDoor(Level level) {
        if (layout == null) {
            return false;
        }
        long now = level.getGameTime();
        for (NestLayout.Room room : layout.rooms()) {
            if (room.ruined && now - room.ruinedAt < GIVEN_UP_PATIENCE_TICKS
                    && isOpen(level, room.door()) && canStandAt(level, room.stand())) {
                return true;
            }
        }
        return false;
    }

    private void scanHazards(Level level, long now) {
        alertCheckedAt = now;
        NestLayout planned = getLayout(level);
        if (planned != null && now - roomsCheckedAt >= ROOM_CHECK_TICKS) {
            roomsCheckedAt = now;
            checkRooms(level, planned, now);
        }
        hazardCells.clear();
        Alert found = Alert.NONE;
        List<BlockPos> inside = new ArrayList<>();
        inside.add(center);
        inside.addAll(nestCells(level));
        if (planned != null) {
            inside.addAll(planned.cells());
        }
        for (BlockPos pos : inside) {
            if (hasFluid(level, pos)) {
                hazardCells.add(pos.immutable());
                found = Alert.INSIDE;
            }
        }
        // On the square of the hill, a stream that is running (flowing water or lava, not a still pool or a lone
        // source block) is on its way to the hole. Anything outside the square, and anything standing still, is ignored.
        for (int dx = -MOUND_RADIUS; dx <= MOUND_RADIUS; dx++) {
            for (int dz = -MOUND_RADIUS; dz <= MOUND_RADIUS; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (hasFluid(level, pos) && !level.getFluidState(pos).isSource() && hazardCells.add(pos)) {
                        found = found == Alert.NONE ? Alert.SURFACE : found;
                    }
                }
            }
        }
        alert = found;
        // Where it comes from: only the sources that feed what is running into the hill, nothing else.
        trace = found == Alert.NONE ? null : FluidTrace.upstream(level, hazardCells, center);
        if (found != Alert.NONE) {
            lastAlertAt = now;
        }
    }

    private static boolean hasFluid(Level level, BlockPos pos) {
        return level.isLoaded(pos) && !level.getFluidState(pos).isEmpty();
    }

    /** The shaft, the entrance and the air above it: soil is never packed in here, or the way out would be shut. */
    private boolean isEntranceColumn(BlockPos pos) {
        return pos.getX() == center.getX() && pos.getZ() == center.getZ() && pos.getY() >= center.getY() - SHAFT_DEPTH;
    }

    // ---- Rooms that run into water or lava

    /** Gives up every room that has been lost to water or lava (see {@link #roomIsLost}) and plans a new one for each. */
    private void checkRooms(Level level, NestLayout planned, long now) {
        Set<BlockPos> entrance = new HashSet<>();
        entrance.add(center);
        for (int i = 1; i <= planned.shaftDepth(); i++) {
            entrance.add(center.below(i));
        }
        for (NestLayout.Room room : planned.rooms()) {
            entrance.addAll(room.trunk());
        }
        List<NestLayout.Room> lost = new ArrayList<>();
        for (NestLayout.Room room : planned.rooms()) {
            if (!room.ruined && roomIsLost(level, planned, room, entrance)) {
                lost.add(room);
            }
        }
        for (NestLayout.Room room : lost) {
            giveUpRoom(level, planned, room, now);
        }
    }

    /**
     * A room is lost when it can never be used: a pool has got in the way of a block that is still to be dug (the
     * ants never open a block that touches water or lava), or water or lava is in it that does not come from the
     * entrance but from a source of its own (a pool the digging ran into). Water that came down the shaft
     * is not a reason: once its source is blocked it drains away and the room is fine again.
     */
    private boolean roomIsLost(Level level, NestLayout planned, NestLayout.Room room, Set<BlockPos> entrance) {
        List<BlockPos> wet = new ArrayList<>();
        for (BlockPos pos : room.beyondDoor()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            if (hasFluid(level, pos)) {
                wet.add(pos);
            } else if (level.getBlockState(pos).is(ModTags.DIGGABLE) && fluidOutsideNest(level, planned, pos)) {
                return true;
            }
        }
        if (wet.isEmpty()) {
            return false;
        }
        FluidTrace found = FluidTrace.upstream(level, wet, center);
        return !found.sources.isEmpty() && !found.touchedAny(entrance);
    }

    /** Water or lava next to this block that is not part of the nest (the nest's own flooding does not count). */
    private boolean fluidOutsideNest(Level level, NestLayout planned, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (hasFluid(level, next) && !planned.cells().contains(next) && !isEntranceColumn(next)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives the room up: its door gets shut for good (the ants inside come out first), nothing in it is dug or
     * checked any more, and a new room of the same kind is planned somewhere that leaves every way in and out of
     * the nest as it was (see {@link NestLayout#planReplacement}).
     */
    private void giveUpRoom(Level level, NestLayout planned, NestLayout.Room room, long now) {
        planned.giveUp(room, now);
        NestLayout.Room replacement = planned.ruinedCount() <= NestLayout.MAX_RUINED
                ? planned.planReplacement(level, center, room) : null;
        if (replacement != null) {
            planned.add(replacement);
        }
        lastAlertAt = now;
        AntColoniesMod.LOGGER.info("Colony {} gives up its {} room (door at {}) and {}", id, room.kind(), room.door().toShortString(),
                replacement == null ? "finds no place for another one"
                        : "plans another one leaving " + replacement.spec.start().toShortString() + " towards " + replacement.spec.forward());
        onChange.run();
    }

    /** True while ants are still inside a room that was just given up (for a few seconds at most). */
    private boolean isEvacuating(Level level, NestLayout.Room room) {
        if (level.getGameTime() - room.ruinedAt >= EVACUATE_TICKS) {
            return false;
        }
        List<BlockPos> zone = room.beyondDoor();
        AABB box = new AABB(zone.get(0));
        for (BlockPos pos : zone) {
            box = box.minmax(new AABB(pos));
        }
        for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, box)) {
            if (id.equals(ant.getColonyId()) && zone.contains(ant.blockPosition())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where an ant standing at {@code pos} must go to get out of a room that was given up (the block in front of its
     * door), or null if it is not in one (or the door is already shut).
     */
    @Nullable
    public BlockPos findEvacuation(Level level, BlockPos pos) {
        if (layout == null) {
            return null;
        }
        NestLayout.Room room = layout.givenUpRoomAt(pos);
        return room != null && isOpen(level, room.door()) ? room.stand() : null;
    }

    // ---- Closing things

    /**
     * What an ant standing at {@code near} should do right now in an emergency, or null if there is nothing it
     * can do. Doors of given-up rooms come first (anywhere in the nest); while water or lava threatens the nest,
     * the ants below shut the doors of all the rooms (so it cannot flood them) and the ants above go for the
     * source of it. {@code avoid}: openings this ant has already failed to reach.
     */
    @Nullable
    public Leak findEmergencyTask(Level level, BlockPos near, Set<BlockPos> avoid) {
        board.expire(level.getGameTime());
        NestLayout planned = layout;
        if (planned != null) {
            List<Leak> doors = new ArrayList<>();
            for (NestLayout.Room room : planned.rooms()) {
                if (room.ruined && isOpen(level, room.door()) && canStandAt(level, room.stand()) && !isEvacuating(level, room)) {
                    doors.add(new Leak(room.door(), room.stand()));
                }
            }
            Leak task = pick(doors, near, avoid);
            if (task != null) {
                return task;
            }
        }
        if (getAlert(level) == Alert.NONE) {
            return null;
        }
        if (planned != null && near.getY() < center.getY()) {
            // Below ground: shut the doors first (while the water or lava is still on its way down), then help up top.
            List<Leak> doors = new ArrayList<>();
            for (NestLayout.Room room : planned.rooms()) {
                if (!room.ruined && isOpen(level, room.door()) && canStandAt(level, room.stand())) {
                    doors.add(new Leak(room.door(), room.stand()));
                }
            }
            Leak task = pick(doors, near, avoid);
            if (task != null) {
                return task;
            }
        }
        return findSourceTask(level, near, avoid);
    }

    /**
     * The source block of the water or lava that is running into the hill or the nest: found by following the
     * stream upstream from where it arrives, so only what really feeds it is ever blocked. If no source can be
     * reached, a piece of the stream above ground is blocked instead; a stream with no source at all is left to run dry.
     */
    @Nullable
    private Leak findSourceTask(Level level, BlockPos near, Set<BlockPos> avoid) {
        FluidTrace found = trace;
        if (found == null) {
            return null;
        }
        // The trace is a few ticks old: only sources that are still there count.
        boolean anySourceLeft = false;
        List<Leak> sources = new ArrayList<>();
        for (BlockPos pos : found.sources) {
            if (!hasFluid(level, pos)) {
                continue;
            }
            anySourceLeft = true;
            Leak leak = fluidTask(level, pos, near, true);
            if (leak != null) {
                sources.add(leak);
            }
        }
        Leak task = pick(sources, near, avoid);
        if (task != null) {
            return task;
        }
        for (Leak leak : sources) {
            if (!avoid.contains(leak.hole())) {
                return null; // a source is in reach, but enough ants are on it already
            }
        }
        if (!anySourceLeft && !found.truncated) {
            return null; // nothing feeds it any more: it runs dry by itself
        }
        List<Leak> pieces = new ArrayList<>();
        int looked = 0;
        for (BlockPos pos : found.stream) {
            if (looked++ >= STREAM_PIECES_LOOKED_AT) {
                break;
            }
            if (pos.getY() < center.getY()) {
                continue; // underground the nest is left alone: soil is never packed into its rooms and tunnels this way
            }
            Leak leak = fluidTask(level, pos, near, false);
            if (leak != null) {
                pieces.add(leak);
            }
        }
        return pick(pieces, near, avoid);
    }

    /**
     * Closing this piece of water or lava, if soil may go there and an ant can get next to it. A source in the hole
     * or the shaft is blocked like any other (the soil is bitten out again once the danger is over, see
     * {@link #findDigTarget}); a mere piece of a stream is never packed into the way out.
     */
    @Nullable
    private Leak fluidTask(Level level, BlockPos pos, BlockPos near, boolean isSource) {
        if (!hasFluid(level, pos) || (isEntranceColumn(pos) && !isSource)) {
            return null;
        }
        // Inside the nest: never close a block that would shut a room off from the way out.
        if (layout != null && layout.cells().contains(pos)
                && !layout.staysConnectedWithout(pos, center.below(layout.shaftDepth()))) {
            return null;
        }
        BlockPos stand = findStandNear(level, pos, near);
        return stand == null ? null : new Leak(pos.immutable(), stand, true);
    }

    /**
     * The best opening to work on: nearest first, but an opening nobody is at yet beats a crowded one that is a
     * little closer, so every opening gets ants before any gets many. Openings with {@link #MAX_WORKERS_PER_LEAK}
     * ants on them are full.
     */
    @Nullable
    private Leak pick(List<Leak> candidates, BlockPos near, Set<BlockPos> avoid) {
        Leak best = null;
        double bestScore = Double.MAX_VALUE;
        for (Leak leak : candidates) {
            if (leak.stand() == null || avoid.contains(leak.hole())) {
                continue;
            }
            int workers = board.workers(leak.hole());
            if (workers >= MAX_WORKERS_PER_LEAK) {
                continue;
            }
            double score = Math.sqrt(leak.stand().distSqr(near)) + workers * CROWD_PENALTY;
            if (score < bestScore) {
                best = leak;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * A free spot next to the target where an ant can hold on to pack it shut: on the floor, or against a wall (an
     * ant climbs and clings to any solid wall, so a source high up a cliff or a tree can be reached too). The
     * closest spot to the target wins, then the one closest to the ant.
     */
    @Nullable
    private static BlockPos findStandNear(Level level, BlockPos target, BlockPos near) {
        LevelCellMap map = new LevelCellMap(level);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos spot = target.offset(dx, dy, dz);
                    if ((dx != 0 || dy != 0 || dz != 0) && canStandAt(map, spot)) {
                        double score = target.distSqr(spot) * 100.0 + spot.distSqr(near) * 0.01;
                        if (score < bestScore) {
                            best = spot;
                            bestScore = score;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean canStandAt(Level level, BlockPos spot) {
        return canStandAt(new LevelCellMap(level), spot);
    }

    /** A free cell (no water or lava in it) with a floor under it or a solid wall beside it. */
    private static boolean canStandAt(LevelCellMap map, BlockPos spot) {
        if (!map.isOpen(spot)) {
            return false;
        }
        if (map.isSolidFace(spot.below(), Direction.UP)) {
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (map.isSolidFace(spot.relative(side), side.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    /** The plain opening in the nest walls closest to {@code near} that an ant can reach, or null. */
    @Nullable
    public Leak findLeak(Level level, BlockPos near, Set<BlockPos> avoid) {
        board.expire(level.getGameTime());
        return pick(getLeaks(level), near, avoid);
    }

    /** Tells the other ants that this one is working on the opening (for a while, or until {@link #releaseLeak}). */
    public void claimLeak(Level level, BlockPos hole, UUID ant) {
        board.claim(hole, ant, level.getGameTime() + LEAK_CLAIM_TICKS);
    }

    public void releaseLeak(BlockPos hole, UUID ant) {
        board.release(hole, ant);
    }

    /** One ant's pack of soil: adds a share of the work to the opening and returns the total (1.0 = enough to close it). */
    public float addSealProgress(BlockPos hole, float amount) {
        return board.addProgress(hole, amount);
    }

    /** True while the opening still needs closing. */
    public boolean isLeakOpen(Level level, Leak leak) {
        return leak.fluidOnly() ? hasFluid(level, leak.hole()) : isOpen(level, leak.hole());
    }

    /** Fills an opening with burrowed dirt. False if it is already closed or someone is standing in it (try again later). */
    public boolean plugLeak(Level level, Leak leak) {
        BlockPos hole = leak.hole();
        // The hole and the shaft are the way out: only a source of water or lava in them is ever blocked (and dug out again later).
        if ((isEntranceColumn(hole) && !leak.fluidOnly()) || !isLeakOpen(level, leak)) {
            return false;
        }
        // Never bury anyone, same as with the hill.
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(hole)).isEmpty()) {
            return false;
        }
        level.setBlock(hole, ModBlocks.BURROWED_DIRT.get().defaultBlockState(), 3);
        board.clear(hole);
        return true;
    }

    /**
     * Open space that soil can be packed into: air, water, lava, grass and the like. Anything else (stone, a
     * chest, a flower someone planted) is left alone. A chunk that is not loaded counts as closed.
     */
    private static boolean isOpen(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    /** The dug-out nest cell with a floor that is closest to the hole (within one block of it, corners included), or null. */
    @Nullable
    private static BlockPos findPlugSpot(Level level, Set<BlockPos> nest, BlockPos hole) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos spot = hole.offset(dx, dy, dz);
                    if (!nest.contains(spot) || !level.isLoaded(spot) || !level.getBlockState(spot).isAir()
                            || !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
                        continue;
                    }
                    double distance = hole.distSqr(spot);
                    if (distance < bestDistance) {
                        best = spot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** The first block in the list that is diggable, can be reached from open space, and is safe to open. */
    @Nullable
    private static BlockPos firstDiggable(Level level, List<BlockPos> cells) {
        for (BlockPos pos : cells) {
            if (!level.isLoaded(pos)) {
                return null;
            }
            if (level.getBlockState(pos).is(ModTags.DIGGABLE)
                    && findStandSpot(level, pos) != null
                    && !touchesFluid(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    /** The layout of the rooms: worked out the first time it is needed, then kept (and saved). Null = wait (chunk not loaded) or no room. */
    @Nullable
    private NestLayout getLayout(Level level) {
        if (layout == null) {
            layout = chooseLayout(level);
            if (layout != null) {
                onChange.run();
            }
        }
        return layout;
    }

    /** How deep the entrance shaft goes for this nest: the depth of the room plan (the normal depth until it is known). */
    private int shaftDepth(Level level) {
        NestLayout planned = getLayout(level);
        return planned == null ? SHAFT_DEPTH : planned.shaftDepth();
    }

    /**
     * Picks how deep the nest sits and which side the main tunnel leaves toward, by looking at the ground: a
     * depth only counts if the whole shaft is soil and the queen's room fits (no stone or water in the way).
     * The depth where the most rooms fit wins, the deeper one on a tie. Null while chunks are missing, or when
     * there is no room at all.
     */
    @Nullable
    private NestLayout chooseLayout(Level level) {
        // Full depth first. If no side has room for the queen there (stone, water), try one block higher.
        for (int depth = SHAFT_DEPTH; depth >= MIN_SHAFT_DEPTH; depth--) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (NestPlan.Chamber chamber : NestPlan.chambers(center.below(depth), direction)) {
                    for (BlockPos pos : chamber.cells()) {
                        if (!level.isLoaded(pos)) {
                            return null;
                        }
                    }
                }
            }
        }
        NestLayout best = null;
        for (int depth = SHAFT_DEPTH; depth >= MIN_SHAFT_DEPTH; depth--) {
            NestLayout found = chooseLayout(level, center.below(depth), depth);
            // The depth with the most rooms wins; on a tie the deeper one (it is tried first).
            if (found != null && (best == null || found.rooms().size() > best.rooms().size())) {
                best = found;
            }
        }
        return best;
    }

    /** The best layout with the shaft {@code depth} blocks deep (all the chunks involved are loaded), or null if the shaft or the queen's room does not fit. */
    @Nullable
    private NestLayout chooseLayout(Level level, BlockPos bottom, int depth) {
        List<BlockPos> shaft = new ArrayList<>();
        for (int i = 1; i <= depth; i++) {
            shaft.add(center.below(i));
        }
        if (!allSoilOrAir(level, shaft)) {
            return null; // stone in the way of the shaft itself
        }
        int start = Math.floorMod(id.hashCode(), 4);
        NestLayout best = null;
        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue((start + i) % 4);
            List<NestPlan.Spec> fitting = new ArrayList<>();
            for (NestPlan.Spec spec : NestPlan.specs(bottom, direction)) {
                NestPlan.Chamber chamber = spec.chamber();
                for (BlockPos pos : chamber.cells()) {
                    if (!level.isLoaded(pos)) {
                        return null;
                    }
                }
                if (allSoilOrAir(level, chamber.cells())) {
                    fitting.add(spec);
                }
            }
            boolean queenFits = !fitting.isEmpty() && fitting.get(0).kind() == NestPlan.Kind.QUEEN;
            if (queenFits && (best == null || fitting.size() > best.rooms().size())) {
                best = new NestLayout(depth, fitting);
            }
        }
        return best;
    }

    static boolean allSoilOrAir(Level level, List<BlockPos> cells) {
        for (BlockPos pos : cells) {
            BlockState state = level.getBlockState(pos);
            if (!(state.is(ModTags.DIGGABLE) || state.isAir()) || touchesFluid(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** Open space next to a block where an ant can stand to bite it: above first, then the four sides. */
    @Nullable
    public static BlockPos findStandSpot(Level level, BlockPos target) {
        List<BlockPos> spots = findStandSpots(level, target);
        return spots.isEmpty() ? null : spots.get(0);
    }

    /**
     * All the open cells next to a block where an ant could stand to bite it, best first (above, then the four
     * sides). Not every one can be walked to: a sealed-off cave pocket next to the block is open space too, so
     * an ant tries them one after the other until it finds a route.
     */
    public static List<BlockPos> findStandSpots(Level level, BlockPos target) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos above = target.above();
        if (level.getBlockState(above).isAir()) {
            spots.add(above);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = target.relative(side);
            if (level.getBlockState(pos).isAir()) {
                spots.add(pos);
            }
        }
        return spots;
    }

    /** Ants never open a block that touches water or lava (it would flood the nest). */
    private static boolean touchesFluid(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A spot on the surface near the entrance where an ant drops its soil pellet, or null when the hill
     * is complete. The hill is built from the bottom up, layer by layer, so every pellet rests on another
     * and the finished mound has exactly the shape of {@link #moundHeightAt}.
     */
    @Nullable
    public BlockPos pickMoundSpot(Level level, RandomSource random) {
        List<BlockPos> spots = openMoundSpots(level);
        if (spots.isEmpty()) {
            return null;
        }
        // Spots nobody is standing in come first (an ant can't put a pellet where someone stands), then the lowest layer.
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos spot : spots) {
            if (level.getEntitiesOfClass(LivingEntity.class, new AABB(spot)).isEmpty()) {
                candidates.add(spot);
            }
        }
        if (candidates.isEmpty()) {
            candidates = spots;
        }
        int lowest = Integer.MAX_VALUE;
        for (BlockPos spot : candidates) {
            lowest = Math.min(lowest, spot.getY());
        }
        List<BlockPos> lowestSpots = new ArrayList<>();
        for (BlockPos spot : candidates) {
            if (spot.getY() == lowest) {
                lowestSpots.add(spot);
            }
        }
        return lowestSpots.get(random.nextInt(lowestSpots.size()));
    }

    /** False once the hill is complete: ants then stop carrying soil out. */
    public boolean hasMoundRoom(Level level) {
        return !openMoundSpots(level).isEmpty();
    }

    /** In every column of the hill that is not full yet: the first free block above the ground. */
    private List<BlockPos> openMoundSpots(Level level) {
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -MOUND_RADIUS; dx <= MOUND_RADIUS; dx++) {
            for (int dz = -MOUND_RADIUS; dz <= MOUND_RADIUS; dz++) {
                if (moundHeightAt(dx, dz) == 0) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (isMoundSpot(level, pos)) {
                    spots.add(pos);
                }
            }
        }
        return spots;
    }

    /** A pellet may go here if it is free, rests on something solid, and the hill is not yet full at this spot. */
    private boolean isMoundSpot(Level level, BlockPos pos) {
        int height = moundHeightAt(pos.getX() - center.getX(), pos.getZ() - center.getZ());
        return pos.getY() < center.getY() + height
                && pos.getY() >= center.getY() - 2
                && level.getBlockState(pos).canBeReplaced()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** Turns a delivered pellet into a real block of the mound. Returns false if the spot is no longer valid. */
    public boolean placePellet(Level level, BlockPos pos) {
        if (!isMoundSpot(level, pos)) {
            return false;
        }
        // Never bury anyone: an ant, the queen, an animal or a player standing in the spot means try elsewhere.
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
            return false;
        }
        // Whatever the ant dug, the mound is built of burrowed dirt (and it never slides away like sand).
        level.setBlock(pos, ModBlocks.BURROWED_DIRT.get().defaultBlockState(), 3);
        return true;
    }

    /** Adds bite progress to a block and returns the new total. Several ants share it. */
    public float addDigProgress(BlockPos pos, float amount) {
        return digProgress.merge(pos.immutable(), amount, Float::sum);
    }

    public void clearDigProgress(BlockPos pos) {
        digProgress.remove(pos);
    }

    public UUID getId() {
        return id;
    }

    public ResourceLocation getSpecies() {
        return species;
    }

    public UUID getQueenId() {
        return queenId;
    }

    public BlockPos getCenter() {
        return center;
    }

    public int getCount(String caste) {
        return casteCounts.getOrDefault(caste, 0);
    }

    public void setCount(String caste, int count) {
        casteCounts.put(caste, Math.max(0, count));
    }

    /** Total members across all castes (the queen is not counted). */
    public int getMemberCount() {
        return casteCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> getCasteCounts() {
        return casteCounts;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("species", species.toString());
        tag.putUUID("queen", queenId);
        tag.putLong("center", center.asLong());
        CompoundTag castes = new CompoundTag();
        casteCounts.forEach(castes::putInt);
        tag.put("castes", castes);
        if (layout != null) {
            tag.put("layout", layout.save());
        }
        return tag;
    }

    public static Colony load(CompoundTag tag) {
        Colony colony = new Colony(
                tag.getUUID("id"),
                ResourceLocation.parse(tag.getString("species")),
                tag.getUUID("queen"),
                BlockPos.of(tag.getLong("center")));
        CompoundTag castes = tag.getCompound("castes");
        for (String key : castes.getAllKeys()) {
            colony.casteCounts.put(key, castes.getInt(key));
        }
        if (tag.contains("layout", Tag.TAG_COMPOUND)) {
            colony.layout = NestLayout.load(tag.getCompound("layout"));
        }
        return colony;
    }
}
