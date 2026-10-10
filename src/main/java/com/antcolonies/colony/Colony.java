package com.antcolonies.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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

    /** Where the nest's rooms go. Decided once (see {@link #getLayout}); not saved, it comes out the same after a reload. */
    private record Layout(List<NestPlan.Chamber> chambers, Set<BlockPos> cells, int shaftDepth) {
        Layout(List<NestPlan.Chamber> chambers, int shaftDepth) {
            this(chambers, cellsOf(chambers), shaftDepth);
        }

        private static Set<BlockPos> cellsOf(List<NestPlan.Chamber> chambers) {
            Set<BlockPos> cells = new HashSet<>();
            for (NestPlan.Chamber chamber : chambers) {
                cells.addAll(chamber.cells());
            }
            return cells;
        }
    }

    @Nullable
    private Layout layout;

    /** How long (in ticks) an ant that went to close a leak keeps the other ants away from it. */
    private static final int LEAK_CLAIM_TICKS = 600;

    /** Leaks an ant is already taking care of: hole -> game time when the claim runs out. Not saved. */
    private final Map<BlockPos, Long> claimedLeaks = new HashMap<>();

    /**
     * An opening in the nest walls: {@code hole} is open space (a cave, a crack, water) right next to a dug-out
     * nest cell, and {@code stand} is a dug-out cell with a floor, no further than one block from the hole, where
     * an ant can stand to close it (null when no ant can get close enough). The entrance itself is never a leak.
     */
    public record Leak(BlockPos hole, @Nullable BlockPos stand) {
    }

    /**
     * Water or lava near the nest. SURFACE: it is close to the entrance (the ants above go and block its source).
     * INSIDE: it has got into the hole or the nest. From the first warning on, the ants below close the doors
     * of the rooms: water and lava are far quicker than an ant, so they must not wait until it is in the nest.
     */
    public enum Alert {
        NONE, SURFACE, INSIDE
    }

    private static final int ALERT_CHECK_TICKS = 10;
    /** The doors stay shut this long after the last water or lava was seen (10 seconds), then the ants open them again. */
    private static final int CALM_TICKS = 200;
    /** Only water and lava this close to the entrance is dealt with, and no more than this many blocks of one stream. */
    private static final int HAZARD_RANGE = 8;
    private static final int HAZARD_BODY_LIMIT = 600;

    private long alertCheckedAt = -1000;
    private Alert alert = Alert.NONE;
    private long lastAlertAt = -1000000;
    private final Set<BlockPos> hazardCells = new HashSet<>();

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
        Layout layout = getLayout(level);
        if (layout == null) {
            return null;
        }
        for (NestPlan.Chamber chamber : layout.chambers()) {
            next = firstDiggable(level, chamber.cells());
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
        Layout layout = getLayout(level);
        if (layout == null) {
            return null;
        }
        for (int i = 1; i <= layout.shaftDepth(); i++) {
            if (!level.isLoaded(center.below(i)) || !level.getBlockState(center.below(i)).isAir()) {
                return null;
            }
        }
        NestPlan.Chamber queen = layout.chambers().get(0);
        for (BlockPos pos : queen.cells()) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                return null;
            }
        }
        return queen.center();
    }

    /** The rooms this nest is being dug with (empty until the layout is decided). For tests and debugging. */
    public List<NestPlan.Chamber> getPlannedChambers(Level level) {
        Layout layout = getLayout(level);
        return layout == null ? List.of() : layout.chambers();
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
            Layout layout = getLayout(level);
            if (layout != null) {
                cells.addAll(layout.cells());
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
                leaks.add(new Leak(hole, findPlugSpot(level, nest, hole)));
            }
        }
        return leaks;
    }

    /** The current alert level, looked at again every half second at most. */
    public Alert getAlert(Level level) {
        long now = level.getGameTime();
        if (now - alertCheckedAt >= ALERT_CHECK_TICKS || now < alertCheckedAt) {
            scanHazards(level, now);
        }
        return alert;
    }

    /** True while water or lava is near, and for a few seconds after it has gone. */
    public boolean isAlarmed(Level level) {
        return getAlert(level) != Alert.NONE || level.getGameTime() - lastAlertAt < CALM_TICKS;
    }

    private void scanHazards(Level level, long now) {
        alertCheckedAt = now;
        hazardCells.clear();
        Alert found = Alert.NONE;
        List<BlockPos> inside = new ArrayList<>();
        inside.add(center);
        inside.addAll(nestCells(level));
        Layout planned = getLayout(level);
        if (planned != null) {
            inside.addAll(planned.cells());
        }
        for (BlockPos pos : inside) {
            if (hasFluid(level, pos)) {
                hazardCells.add(pos.immutable());
                found = Alert.INSIDE;
            }
        }
        // Around the hole, where a stream would run into it.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (hasFluid(level, pos) && hazardCells.add(pos)) {
                        found = found == Alert.NONE ? Alert.SURFACE : found;
                    }
                }
            }
        }
        alert = found;
        if (found != Alert.NONE) {
            lastAlertAt = now;
        }
    }

    private static boolean hasFluid(Level level, BlockPos pos) {
        return level.isLoaded(pos) && !level.getFluidState(pos).isEmpty();
    }

    /**
     * What an ant standing at {@code near} should close next: while water or lava threatens the nest, the ants
     * below shut the doors of the rooms and the ants above go for the source of it; otherwise (and when there is
     * nothing of that left to do) a plain opening in the walls. Null if there is nothing to close.
     */
    @Nullable
    public Leak findSealTask(Level level, BlockPos near) {
        long now = level.getGameTime();
        claimedLeaks.values().removeIf(until -> until <= now);
        Alert current = getAlert(level);
        if (current != Alert.NONE) {
            Leak task = null;
            if (near.getY() < center.getY()) {
                // Below ground: shut the doors first (while the water or lava is still on its way down), then help up top.
                task = findDoorTask(level, near);
            }
            if (task == null) {
                task = findSourceTask(level, near);
            }
            if (task != null) {
                return task;
            }
        }
        return findLeak(level, near);
    }

    /** The door of a room that is still open: the last block of its tunnel. An ant plugs it from the block before. */
    @Nullable
    private Leak findDoorTask(Level level, BlockPos near) {
        Layout planned = getLayout(level);
        if (planned == null) {
            return null;
        }
        Leak best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NestPlan.Chamber chamber : planned.chambers()) {
            BlockPos door = chamber.cells().get(NestPlan.TUNNEL_LENGTH - 1);
            BlockPos stand = chamber.cells().get(NestPlan.TUNNEL_LENGTH - 2);
            if (!isOpen(level, door) || claimedLeaks.containsKey(door) || !canStandAt(level, stand)) {
                continue;
            }
            double distance = stand.distSqr(near);
            if (distance < bestDistance) {
                best = new Leak(door, stand);
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The nearest source block of the water or lava that is running towards the hole (found by following the
     * stream from where it reaches the nest). If no source is in reach, a piece of the stream is blocked instead.
     */
    @Nullable
    private Leak findSourceTask(Level level, BlockPos near) {
        Set<BlockPos> body = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : hazardCells) {
            if (body.add(start)) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty() && body.size() < HAZARD_BODY_LIMIT) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (Math.abs(next.getX() - center.getX()) <= HAZARD_RANGE && Math.abs(next.getZ() - center.getZ()) <= HAZARD_RANGE
                        && Math.abs(next.getY() - center.getY()) <= HAZARD_RANGE && hasFluid(level, next) && body.add(next)) {
                    queue.add(next);
                }
            }
        }
        Leak bestSource = null;
        double bestSourceDistance = Double.MAX_VALUE;
        Leak bestStream = null;
        double bestStreamDistance = Double.MAX_VALUE;
        for (BlockPos pos : body) {
            if (pos.equals(center) || claimedLeaks.containsKey(pos)) {
                continue;
            }
            BlockPos stand = findStandNear(level, pos);
            if (stand == null) {
                continue;
            }
            double distance = stand.distSqr(near);
            if (level.getFluidState(pos).isSource()) {
                if (distance < bestSourceDistance) {
                    bestSource = new Leak(pos.immutable(), stand);
                    bestSourceDistance = distance;
                }
            } else if (distance < bestStreamDistance) {
                bestStream = new Leak(pos.immutable(), stand);
                bestStreamDistance = distance;
            }
        }
        return bestSource != null ? bestSource : bestStream;
    }

    /** A free block with a floor next to (or above or below) the target, not in water, where an ant can stand to pack it shut. */
    @Nullable
    private static BlockPos findStandNear(Level level, BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos spot = target.offset(dx, dy, dz);
                    double distance = target.distSqr(spot);
                    if (distance > 0 && distance < bestDistance && canStandAt(level, spot)) {
                        best = spot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static boolean canStandAt(Level level, BlockPos spot) {
        return level.isLoaded(spot) && level.getBlockState(spot).isAir() && level.getFluidState(spot).isEmpty()
                && level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP);
    }

    /** The leak closest to {@code near} that an ant can reach and no other ant is on its way to, or null. */
    @Nullable
    public Leak findLeak(Level level, BlockPos near) {
        long now = level.getGameTime();
        claimedLeaks.values().removeIf(until -> until <= now);
        Leak best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Leak leak : getLeaks(level)) {
            if (leak.stand() == null || claimedLeaks.containsKey(leak.hole())) {
                continue;
            }
            double distance = leak.stand().distSqr(near);
            if (distance < bestDistance) {
                best = leak;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Tells the other ants that one of them is on its way to close this leak (for a while). */
    public void claimLeak(Level level, BlockPos hole) {
        claimedLeaks.put(hole.immutable(), level.getGameTime() + LEAK_CLAIM_TICKS);
    }

    /** True while this block is still open space (so the leak is still there). */
    public boolean isLeakOpen(Level level, BlockPos hole) {
        return isOpen(level, hole);
    }

    /** Fills a leak with burrowed dirt. False if it is already closed or someone is standing in it (try again later). */
    public boolean plugLeak(Level level, BlockPos hole) {
        if (hole.equals(center) || !isOpen(level, hole)) {
            return false;
        }
        // Never bury anyone, same as with the hill.
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(hole)).isEmpty()) {
            return false;
        }
        level.setBlock(hole, ModBlocks.BURROWED_DIRT.get().defaultBlockState(), 3);
        claimedLeaks.remove(hole);
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

    /** The layout of the rooms: worked out the first time it is needed, then kept. Null = wait (chunk not loaded) or no room. */
    @Nullable
    private Layout getLayout(Level level) {
        if (layout == null) {
            layout = chooseLayout(level);
        }
        return layout;
    }

    /** How deep the entrance shaft goes for this nest: the depth of the room plan (the normal depth until it is known). */
    private int shaftDepth(Level level) {
        Layout planned = getLayout(level);
        return planned == null ? SHAFT_DEPTH : planned.shaftDepth();
    }

    /**
     * Picks how deep the nest sits and which side the main tunnel leaves toward, by looking at the ground: a
     * depth only counts if the whole shaft is soil and the queen's room fits (no stone or water in the way).
     * The depth where the most rooms fit wins, the deeper one on a tie. Null while chunks are missing, or when
     * there is no room at all.
     */
    @Nullable
    private Layout chooseLayout(Level level) {
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
        Layout best = null;
        for (int depth = SHAFT_DEPTH; depth >= MIN_SHAFT_DEPTH; depth--) {
            Layout found = chooseLayout(level, center.below(depth), depth);
            // The depth with the most rooms wins; on a tie the deeper one (it is tried first).
            if (found != null && (best == null || found.chambers().size() > best.chambers().size())) {
                best = found;
            }
        }
        return best;
    }

    /** The best layout with the shaft {@code depth} blocks deep (all the chunks involved are loaded), or null if the shaft or the queen's room does not fit. */
    @Nullable
    private Layout chooseLayout(Level level, BlockPos bottom, int depth) {
        List<BlockPos> shaft = new ArrayList<>();
        for (int i = 1; i <= depth; i++) {
            shaft.add(center.below(i));
        }
        if (!allSoilOrAir(level, shaft)) {
            return null; // stone in the way of the shaft itself
        }
        int start = Math.floorMod(id.hashCode(), 4);
        Layout best = null;
        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue((start + i) % 4);
            List<NestPlan.Chamber> fitting = new ArrayList<>();
            for (NestPlan.Chamber chamber : NestPlan.chambers(bottom, direction)) {
                for (BlockPos pos : chamber.cells()) {
                    if (!level.isLoaded(pos)) {
                        return null;
                    }
                }
                if (allSoilOrAir(level, chamber.cells())) {
                    fitting.add(chamber);
                }
            }
            boolean queenFits = !fitting.isEmpty() && fitting.get(0).kind() == NestPlan.Kind.QUEEN;
            if (queenFits && (best == null || fitting.size() > best.chambers().size())) {
                best = new Layout(fitting, depth);
            }
        }
        return best;
    }

    private static boolean allSoilOrAir(Level level, List<BlockPos> cells) {
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
        return colony;
    }
}
