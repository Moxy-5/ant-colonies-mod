package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The underground nest of one colony: where its shaft and rooms go (the plan, see {@link NestPlan}), the next block to
 * dig, and which floor cells of each room are already dug out. Species-neutral: the plan will come from the species profile.
 */
public class Nest {
    /** How deep the entrance shaft goes, in blocks (will come from the species profile later). */
    public static final int SHAFT_DEPTH = 3;
    /**
     * Where the soil is too thin or too stony for the rooms at full depth, the whole nest sits one block higher
     * (the shaft is one block shorter). Never shallower than this.
     */
    public static final int MIN_SHAFT_DEPTH = 2;

    private final Colony colony;
    private final BlockPos center;

    /** Bite progress per block (0..1). Not saved: a half-dug block just restarts after a reload. */
    private final Map<BlockPos, Float> digProgress = new HashMap<>();

    /**
     * Where the nest's rooms go. Decided once (see {@link #getLayout}) and saved: worked out again after a reload, the
     * answer could differ, because the ground has changed since (seed and bread heaps lie in the rooms).
     */
    private record Layout(List<NestPlan.Chamber> chambers, Set<BlockPos> cells, int shaftDepth, Direction direction) {
        Layout(List<NestPlan.Chamber> chambers, int shaftDepth, Direction direction) {
            this(chambers, cellsOf(chambers), shaftDepth, direction);
        }

        /** The level used for this purpose, or null if the nest has none. */
        @Nullable
        NestPlan.Chamber of(NestPlan.Kind kind) {
            for (NestPlan.Chamber chamber : chambers) {
                if (chamber.kind() == kind) {
                    return chamber;
                }
            }
            return null;
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

    Nest(Colony colony) {
        this.colony = colony;
        this.center = colony.getCenter();
    }

    /**
     * The next block to dig, or null if all the digging is done (or blocked). Order: the entrance
     * shaft, then the levels from the top down (the stair, the tunnel and the room of each: seed store,
     * nursery, queen, refuse chamber). The shaft is never widened, so its walls stay solid for the ants to climb.
     */
    @Nullable
    public BlockPos findDigTarget(Level level) {
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
     * The middle of the room the queen lives in, or null if there is none yet (or the chunk is not loaded). That is
     * her own room (the queen level) once it is finished; until then the nursery, where the bread is, once that is
     * finished (see {@link #getHomeChamber}). The other rooms don't matter here.
     */
    @Nullable
    public BlockPos getFinishedRoomCenter(Level level) {
        NestPlan.Chamber home = getHomeChamber(level);
        return home == null ? null : home.center();
    }

    /**
     * The level the queen lives in: the queen level once it is finished (the shaft, every stair down to it, its tunnel
     * and the whole room are dug out), until then the nursery once that is finished (the bread is there). Where the soil
     * is too thin for a nursery, the first finished level from the top. Null if she has no home yet. The refuse chamber
     * is never a home, and neither is the seed store when the plan has a nursery.
     */
    @Nullable
    public NestPlan.Chamber getHomeChamber(Level level) {
        Layout layout = getLayout(level);
        if (layout == null || !isShaftOpen(level, layout)) {
            return null;
        }
        NestPlan.Chamber queen = layout.of(NestPlan.Kind.QUEEN);
        if (queen != null && isFinished(level, queen)) {
            return queen;
        }
        NestPlan.Chamber nursery = layout.of(NestPlan.Kind.NURSERY);
        if (nursery != null) {
            return isFinished(level, nursery) ? nursery : null;
        }
        for (NestPlan.Chamber chamber : layout.chambers()) {
            if (chamber.kind() != NestPlan.Kind.REFUSE && isFinished(level, chamber)) {
                return chamber;
            }
        }
        return null;
    }

    /** True once everything needed to live in this level is dug out: the way in and the whole room (bread may lie on its floor). */
    private static boolean isFinished(Level level, NestPlan.Chamber chamber) {
        for (BlockPos pos : chamber.wayIn()) {
            if (!isDugOut(level, pos)) {
                return false;
            }
        }
        for (BlockPos pos : chamber.room()) {
            if (!isDugOut(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** True when the whole entrance shaft is dug out. */
    private boolean isShaftOpen(Level level, Layout layout) {
        for (int i = 1; i <= layout.shaftDepth(); i++) {
            if (!level.isLoaded(center.below(i)) || !level.getBlockState(center.below(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The floor cells of the food store that are dug out and can be walked to (the room, without its tunnel). The
     * room does not have to be finished: every cell that is already dug counts. Empty until the way in is dug.
     */
    public List<BlockPos> getFoodStoreCells(Level level) {
        return getRoomCells(level, NestPlan.Kind.FOOD);
    }

    /** The floor cells of the nursery that are dug out and can be walked to (the room does not have to be finished). */
    public List<BlockPos> getNurseryCells(Level level) {
        return getRoomCells(level, NestPlan.Kind.NURSERY);
    }

    /** The floor cells of the queen's room that are dug out and can be walked to. */
    public List<BlockPos> getQueenRoomCells(Level level) {
        return getRoomCells(level, NestPlan.Kind.QUEEN);
    }

    /** The floor cells of the refuse chamber that are dug out and can be walked to (the room does not have to be finished). */
    public List<BlockPos> getRefuseCells(Level level) {
        return getRoomCells(level, NestPlan.Kind.REFUSE);
    }

    /** The floor cells of the room the queen lives in now (see {@link #getHomeChamber}); empty if she has none yet. */
    public List<BlockPos> getHomeRoomCells(Level level) {
        NestPlan.Chamber home = getHomeChamber(level);
        return home == null ? List.of() : getRoomCells(level, home.kind());
    }

    /**
     * The block right above the top of the shaft, at ground level: the hole a founding pair closes from inside (see
     * the species profile's {@code seal_while_founding}). It is not part of the plan, so nobody digs it open again.
     */
    public BlockPos getEntrance() {
        return center;
    }

    /** Whether the entrance hole is open: nothing solid in it (grass or snow do not close it). */
    public boolean isEntranceOpen(Level level) {
        BlockState state = level.getBlockState(center);
        return level.isLoaded(center) && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** Open, or only a heap of seeds or bread on the floor: the block is dug out. A chunk that is not loaded counts as not dug. */
    static boolean isDugOut(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(ModBlocks.SEED_PILE.get()) || state.is(ModBlocks.BREAD_PILE.get());
    }

    private static boolean touchesSideways(BlockPos a, BlockPos b) {
        return a.getY() == b.getY() && Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) == 1;
    }

    /**
     * The dug-out floor cells of one room. They count once the shaft, the stairs down to its level and the tunnel to
     * the room are dug; after that every cell that is dug and joins up with the tunnel (or with another counted cell)
     * is in.
     */
    private List<BlockPos> getRoomCells(Level level, NestPlan.Kind kind) {
        Layout layout = getLayout(level);
        if (layout == null || !isShaftOpen(level, layout)) {
            return List.of();
        }
        NestPlan.Chamber chamber = layout.of(kind);
        if (chamber == null) {
            return List.of();
        }
        for (BlockPos pos : chamber.wayIn()) {
            if (!isDugOut(level, pos)) {
                return List.of();
            }
        }
        BlockPos tunnelEnd = chamber.tunnelEnd();
        List<BlockPos> reached = new ArrayList<>();
        for (BlockPos pos : chamber.room()) {
            if (!isDugOut(level, pos)) {
                continue;
            }
            boolean joined = touchesSideways(pos, tunnelEnd);
            for (int k = 0; k < reached.size() && !joined; k++) {
                joined = touchesSideways(pos, reached.get(k));
            }
            if (joined) {
                reached.add(pos);
            }
        }
        return reached;
    }

    /** The rooms this nest is being dug with (empty until the layout is decided). For tests and debugging. */
    public List<NestPlan.Chamber> getPlannedChambers(Level level) {
        Layout layout = getLayout(level);
        return layout == null ? List.of() : layout.chambers();
    }

    /** Every block that is part of the nest plan: the shaft, and the rooms once their layout is decided. */
    Set<BlockPos> nestCells(Level level) {
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
     * Picks how deep the nest sits, which side the rooms lie toward and how many levels it has, by looking at the
     * ground: a depth only counts if the whole shaft is soil, and a nest only counts if every stair, tunnel and
     * room of its plan is soil (no stone or water in the way). The nest with the most levels wins, the deeper one on
     * a tie. Null while chunks are missing, or when there is no room at all.
     */
    @Nullable
    private Layout chooseLayout(Level level) {
        // Everything the plan could touch must be loaded, or the answer would change later.
        for (int depth = SHAFT_DEPTH; depth >= MIN_SHAFT_DEPTH; depth--) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (NestPlan.Chamber chamber : NestPlan.chambers(center.below(depth), direction, NestPlan.MAX_LEVELS)) {
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
            // The depth with the most levels wins; on a tie the deeper one (it is tried first).
            if (found != null && (best == null || found.chambers().size() > best.chambers().size())) {
                best = found;
            }
        }
        return best;
    }

    /** The best layout with the shaft {@code depth} blocks deep (all the chunks involved are loaded), or null if the shaft or the first level does not fit. */
    @Nullable
    private Layout chooseLayout(Level level, BlockPos bottom, int depth) {
        List<BlockPos> shaft = new ArrayList<>();
        for (int i = 1; i <= depth; i++) {
            shaft.add(center.below(i));
        }
        if (!allSoilOrAir(level, shaft)) {
            return null; // stone in the way of the shaft itself
        }
        int start = Math.floorMod(colony.getId().hashCode(), 4);
        Layout best = null;
        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue((start + i) % 4);
            // The most levels whose whole plan is soil. A shorter nest uses its levels differently (and its last one is
            // always the small queen's room), so each length is checked as the plan it really is.
            int maxLevels = Math.min(NestPlan.MAX_LEVELS, Math.max(1, colony.profile().nest().maxLevels())); // from the species profile
            for (int levels = maxLevels; levels >= 1; levels--) {
                List<NestPlan.Chamber> plan = NestPlan.chambers(bottom, direction, levels);
                if (best != null && levels <= best.chambers().size()) {
                    break; // not better than what is already found
                }
                boolean fits = true;
                for (NestPlan.Chamber chamber : plan) {
                    if (!allSoilOrAir(level, chamber.cells())) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    best = new Layout(plan, depth, direction);
                    break;
                }
            }
        }
        return best;
    }

    /** Soil, or already dug out (a heap of seeds or bread on the floor counts as dug: in an old save the plan is worked out again on a nest in use). */
    private static boolean allSoilOrAir(Level level, List<BlockPos> cells) {
        for (BlockPos pos : cells) {
            BlockState state = level.getBlockState(pos);
            if (!(state.is(ModTags.DIGGABLE) || isDugOut(level, pos)) || touchesFluid(level, pos)) {
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

    /** Adds bite progress to a block and returns the new total. Several ants share it. */
    public float addDigProgress(BlockPos pos, float amount) {
        return digProgress.merge(pos.immutable(), amount, Float::sum);
    }

    public void clearDigProgress(BlockPos pos) {
        digProgress.remove(pos);
    }

    void save(CompoundTag tag) {
        if (layout != null) {
            tag.putInt("nestDepth", layout.shaftDepth());
            tag.putInt("nestDirection", layout.direction().get2DDataValue());
            tag.putInt("nestLevels", layout.chambers().size());
        }
    }

    void load(CompoundTag tag) {
        // The nest plan it was digging (old saves have none: it is worked out again from the ground).
        if (tag.contains("nestLevels")) {
            int depth = tag.getInt("nestDepth");
            Direction direction = Direction.from2DDataValue(tag.getInt("nestDirection"));
            layout = new Layout(NestPlan.chambers(center.below(depth), direction, tag.getInt("nestLevels")), depth, direction);
        }
    }
}
