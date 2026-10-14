package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.MudCrustBlock;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
     * answer could differ, because the ground has changed since (seed and bread heaps lie in the rooms). The side rooms
     * and the garden rooms follow from the rest (the profile says which and how many, the direction where), so they are
     * not saved themselves.
     */
    private record Layout(List<NestPlan.Chamber> chambers, List<NestPlan.Chamber> sideRooms, List<NestPlan.Chamber> gardens,
            Set<BlockPos> cells, int shaftDepth, Direction direction, @Nullable NestPlan.Chamber founding) {
        Layout(List<NestPlan.Chamber> chambers, List<NestPlan.Chamber> sideRooms, List<NestPlan.Chamber> gardens, int shaftDepth,
                Direction direction, @Nullable NestPlan.Chamber founding) {
            this(chambers, sideRooms, gardens, cellsOf(chambers, sideRooms, gardens), shaftDepth, direction, founding);
        }

        /** The level or side room used for this purpose (or the founding chamber), or null if the nest has none. */
        @Nullable
        NestPlan.Chamber of(NestPlan.Kind kind) {
            if (kind == NestPlan.Kind.FOUNDING) {
                return founding;
            }
            for (NestPlan.Chamber chamber : chambers) {
                if (chamber.kind() == kind) {
                    return chamber;
                }
            }
            for (NestPlan.Chamber chamber : sideRooms) {
                if (chamber.kind() == kind) {
                    return chamber;
                }
            }
            return null;
        }

        private static Set<BlockPos> cellsOf(List<NestPlan.Chamber> chambers, List<NestPlan.Chamber> sideRooms, List<NestPlan.Chamber> gardens) {
            Set<BlockPos> cells = new HashSet<>();
            for (List<NestPlan.Chamber> list : List.of(chambers, sideRooms, gardens)) {
                for (NestPlan.Chamber chamber : list) {
                    cells.addAll(chamber.cells());
                }
            }
            return cells;
        }
    }

    /**
     * The garden rooms of a nest with these levels (species with a {@code garden} in their profile, whose nest has one
     * level: the royal chamber), as many as the profile allows; none otherwise.
     */
    private List<NestPlan.Chamber> gardens(List<NestPlan.Chamber> chambers, Direction direction) {
        var garden = colony.profile().garden();
        if (garden.isEmpty() || chambers.size() != 1 || chambers.get(0).kind() != NestPlan.Kind.QUEEN) {
            return List.of();
        }
        return NestPlan.gardens(chambers.get(0), direction, garden.get().rooms());
    }

    /** The side rooms of a nest whose shaft ends at {@code bottom} and whose rooms lie toward {@code direction} (from the species profile). */
    private List<NestPlan.Chamber> sideRooms(BlockPos bottom, Direction direction) {
        List<NestPlan.Kind> kinds = new ArrayList<>();
        for (String name : colony.profile().nest().sideRooms()) {
            try {
                kinds.add(NestPlan.Kind.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                AntColoniesMod.LOGGER.warn("Unknown side room kind '{}' in the species profile of {}", name, colony.getSpecies());
            }
        }
        return NestPlan.sideRooms(bottom, direction, kinds);
    }

    @Nullable
    private Layout layout;
    /**
     * How many levels of the nest the workers may dig (nest stages, see {@link #levelsUnlocked}): it only ever goes up.
     * Saved; nests from before nest stages may dig them all.
     */
    private int levelsUnlocked;
    /** The foraging tunnels of {@link #layout}, worked out once for it. */
    private List<NestPlan.ForagingTunnel> foragingTunnels = List.of();
    /**
     * The planned foraging tunnels as fitted to the ground (see {@link #fitToGround}), saved so they never move once
     * worked out (the mound growing over the land later must not change them). Null until worked out.
     */
    @Nullable
    private List<NestPlan.ForagingTunnel> fittedTunnels;
    @Nullable
    private Layout foragingTunnelsFor;
    /** The planned foraging tunnels and the new ones together, worked out again when either changes. */
    private List<NestPlan.ForagingTunnel> allForagingTunnels = List.of();
    private boolean allForagingTunnelsStale = true;
    /**
     * Tunnels dug out to rich food after the nest was planned (new holes near food, see {@link ForagingHoles}), each
     * with the food it was dug for. Saved.
     */
    private final List<NewTunnel> newTunnels = new ArrayList<>();
    /** The holes of new tunnels whose food is gone: plugged for good, never opened again (the tunnel stays). Saved. */
    private final Set<BlockPos> spentHoles = new HashSet<>();
    /** The cells that count as inside (see {@link #isInside}), worked out at most once a tick. */
    private Set<BlockPos> inside = Set.of();
    /** The mound's planned channel cells (tunnels, spire pieces, mouths): inside only where the block is built. */
    private Set<BlockPos> insideChannels = Set.of();
    /** The mud casts joined to a feeding gallery (see {@link #isInside}), each with the gallery cell it is joined to. */
    private Map<BlockPos, BlockPos> castGalleries = Map.of();
    private long insideTick = Long.MIN_VALUE;

    Nest(Colony colony) {
        this.colony = colony;
        this.center = colony.getCenter();
    }

    /**
     * The next block to dig, or null if all the digging is done (or blocked). Order: the founding chamber if the
     * species has one (the shaft down to it, then its tunnel and room: the queen's first home), then the rest of the
     * entrance shaft, then the levels from the top down (the stair, the tunnel and the room of each: seed store,
     * nursery, queen, refuse chamber), then the side rooms (the termites' food store; only once the colony has workers),
     * then the foraging tunnels, and at foraging time a closed foraging hole at the end of one (see {@link ForagingHoles}).
     * While the colony is still founding (the queen alone), only the founding chamber is dug. The shaft is never
     * widened, so its walls stay solid for the ants to climb.
     */
    @Nullable
    public BlockPos findDigTarget(Level level) {
        return findDigTarget(level, null);
    }

    /**
     * Like {@link #findDigTarget(Level)}, for a worker that is about to dig ({@code digger}). Once the nest is dug, a
     * species that builds a mound goes on with the mound's digging (see {@link Mound#findDigTarget}): what has to be
     * cleared out of the mound, and the galleries for its soil.
     */
    @Nullable
    public BlockPos findDigTarget(Level level, @Nullable java.util.UUID digger) {
        // A colony that closed itself in while it was founded opens up again once it has workers: the plug first (a
        // colony that keeps a schedule only at night, see mayOpenEntrance).
        if (isEntrancePlugged(level) && mayOpenEntrance(level)) {
            return center;
        }
        // The rooms are planned before the first block is dug, because the plan decides how deep the shaft goes.
        Layout planned = getLayout(level);
        if (planned != null && planned.founding() != null) {
            List<BlockPos> top = new ArrayList<>();
            for (int i = 1; i <= NestPlan.FOUNDING_DEPTH; i++) {
                top.add(center.below(i));
            }
            BlockPos next = firstDiggable(level, top);
            if (next != null) {
                return next;
            }
            BlockPos shaftCell = center.below(NestPlan.FOUNDING_DEPTH);
            if (!level.isLoaded(shaftCell) || !level.getBlockState(shaftCell).isAir()) {
                return null;
            }
            next = firstDiggable(level, planned.founding().cells());
            if (next != null || colony.isFounding()) {
                return next; // the queen alone digs nothing but her founding chamber
            }
        }
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
        int allowed = levelsUnlocked(level);
        for (int i = 0; i < layout.chambers().size() && i < allowed; i++) {
            next = firstDiggable(level, layout.chambers().get(i).cells());
            if (next != null) {
                return next;
            }
        }
        if (colony.isFounding()) {
            return null; // the founders dig only their own chamber; the side rooms are the workers' job
        }
        for (NestPlan.Chamber chamber : layout.sideRooms()) {
            next = firstDiggable(level, chamber.cells());
            if (next != null) {
                return next;
            }
        }
        // The first garden room before the foraging tunnels (the food goes there), the others as the colony grows.
        List<NestPlan.Chamber> gardens = wantedGardens(layout);
        if (!gardens.isEmpty()) {
            next = firstDiggable(level, gardens.get(0).cells());
            if (next != null) {
                return next;
            }
        }
        for (NestPlan.ForagingTunnel tunnel : getForagingTunnels(level)) {
            next = firstDiggable(level, tunnel.tunnel());
            if (next != null) {
                return next;
            }
        }
        // The feeding galleries up under rich food, once their tunnels are dug (in the order planned: from the tunnel up).
        for (NewTunnel tunnel : newTunnels) {
            if (tunnel.gallery() != null && !isSpent(tunnel.tunnel().hole()) && ForagingHoles.isDug(level, tunnel.tunnel())) {
                next = firstDiggable(level, tunnel.gallery());
                if (next != null) {
                    return next;
                }
            }
        }
        // A closed hole is opened before more garden rooms are dug: a big colony wants all its gardens at once (with the
        // brain its headcount is mostly numbers), and its foragers were shut in until every room was dug.
        BlockPos hole = colony.foragingHoles().holeToOpen(level);
        if (hole != null) {
            return hole;
        }
        for (NestPlan.Chamber chamber : gardens) {
            next = firstDiggable(level, chamber.cells());
            if (next != null) {
                return next;
            }
        }
        return colony.mound().findDigTarget(level, digger);
    }

    /** The garden rooms the colony wants dug now: the first, and one more for every few workers (the profile's {@code garden}). */
    private List<NestPlan.Chamber> wantedGardens(Layout layout) {
        var garden = colony.profile().garden();
        if (garden.isEmpty()) {
            return List.of();
        }
        int wanted = garden.get().roomsFor(colony.getCount(com.antcolonies.entity.WorkerAntEntity.CASTE));
        return layout.gardens().subList(0, Math.min(wanted, layout.gardens().size()));
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
     * and the whole room are dug out). Until then her founding chamber once that is finished, if the nest has one (she
     * digs it herself and moves down when the workers have dug her deep room). In nests planned without one (older
     * saves): the nursery once that is finished (the bread is there), or where the soil is too thin for a nursery,
     * the first finished level from the top. Null if she has no home yet. The refuse chamber is never a home, and
     * neither is the seed store when the plan has a nursery.
     */
    @Nullable
    public NestPlan.Chamber getHomeChamber(Level level) {
        Layout layout = getLayout(level);
        if (layout == null) {
            return null;
        }
        NestPlan.Chamber queen = layout.of(NestPlan.Kind.QUEEN);
        if (queen != null && isShaftOpen(level, layout) && isFinished(level, queen)) {
            return queen;
        }
        if (layout.founding() != null) {
            return isShaftOpen(level, NestPlan.FOUNDING_DEPTH) && isFinished(level, layout.founding()) ? layout.founding() : null;
        }
        if (!isShaftOpen(level, layout)) {
            return null;
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
        return isShaftOpen(level, layout.shaftDepth());
    }

    /** True when the entrance shaft is dug out down to {@code depth} blocks. */
    private boolean isShaftOpen(Level level, int depth) {
        for (int i = 1; i <= depth; i++) {
            if (!level.isLoaded(center.below(i)) || !level.getBlockState(center.below(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The floor cells of the food store that are dug out and can be walked to (the room, without its tunnel): the seed
     * store level of the ant nest, the termites' side room. The room does not have to be finished: every cell that is
     * already dug counts. Empty until the way in is dug.
     */
    public List<BlockPos> getFoodStoreCells(Level level) {
        List<BlockPos> cells = getRoomCells(level, NestPlan.Kind.FOOD);
        List<BlockPos> gardens = getGardenCells(level);
        if (gardens.isEmpty()) {
            return cells;
        }
        // Termites keep their food in the gardens, built into fungus comb.
        List<BlockPos> all = new ArrayList<>(cells);
        all.addAll(gardens);
        return all;
    }

    /**
     * The floor cells of all the garden rooms (termites' fungus gardens) that are dug out and can be walked to, every
     * garden counted on its own (see {@link #getRoomCells}). Empty for a species without gardens.
     */
    public List<BlockPos> getGardenCells(Level level) {
        Layout layout = getLayout(level);
        if (layout == null || layout.gardens().isEmpty()) {
            return List.of();
        }
        List<BlockPos> cells = new ArrayList<>();
        for (NestPlan.Chamber garden : layout.gardens()) {
            cells.addAll(getRoomCells(level, layout, garden));
        }
        return cells;
    }

    /**
     * The dug-out floor cells of every room the nest has (the levels' rooms, side rooms, gardens, the founding chamber),
     * where the colony's insects that are only numbers come out as real mobs (see {@link Population}).
     */
    public List<BlockPos> getAllRoomCells(Level level) {
        Layout layout = getLayout(level);
        if (layout == null) {
            return List.of();
        }
        Set<BlockPos> cells = new java.util.LinkedHashSet<>();
        List<NestPlan.Chamber> rooms = new ArrayList<>(layout.chambers());
        rooms.addAll(layout.sideRooms());
        rooms.addAll(layout.gardens());
        if (layout.founding() != null) {
            rooms.add(layout.founding());
        }
        for (NestPlan.Chamber room : rooms) {
            cells.addAll(getRoomCells(level, layout, room));
        }
        return new ArrayList<>(cells);
    }

    /** The garden rooms of the plan (empty if the nest has none, or no plan yet). For tests and debugging. */
    public List<NestPlan.Chamber> getGardens(Level level) {
        Layout layout = getLayout(level);
        return layout == null ? List.of() : layout.gardens();
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

    /** The bottom block of the entrance shaft (where the first level's tunnel leaves it), or null while there is no plan yet. */
    @Nullable
    public BlockPos getShaftBottom(Level level) {
        Layout planned = getLayout(level);
        return planned == null ? null : center.below(planned.shaftDepth());
    }

    /**
     * Whether the entrance is closed with the soil the founders packed into it while the colony has members to open
     * it again (species with {@code seal_while_founding}).
     */
    public boolean isEntrancePlugged(Level level) {
        return colony.profile().nest().sealWhileFounding() && !colony.isFounding() && level.isLoaded(center)
                && colony.profile().nest().isPlug(level.getBlockState(center));
    }

    /**
     * Whether the workers may bite the plug in the entrance open now: always for a species that keeps no schedule; for
     * one that does (termites) at foraging time, or once the mound covers the entrance (it is then only the way up into
     * the mound's shaft). By day the plug of a young, open mound stays (see {@link ForagingHoles}).
     */
    public boolean mayOpenEntrance(Level level) {
        return !colony.keepsASchedule() || colony.isForagingTime(level) || isEntranceCovered(level);
    }

    /**
     * Whether something stands over the entrance higher up (the mound's roof over its shaft), so it is no longer open
     * to the sky. A plug in the entrance itself does not count.
     */
    public boolean isEntranceCovered(Level level) {
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ()) > center.getY() + 1;
    }

    /**
     * Whether a worker may bite this block: soil (the diggable tag), the plug in its own entrance, or the mound's soil
     * where it must be hollow. A foraging hole only at foraging time (soil or its plug).
     */
    public boolean canDig(Level level, BlockPos pos) {
        if (colony.foragingHoles().isHole(level, pos)) {
            return colony.foragingHoles().mayOpen(level, pos);
        }
        return isSoil(level.getBlockState(pos)) || (pos.equals(center) && isEntrancePlugged(level) && mayOpenEntrance(level))
                || colony.mound().isClearJob(level, pos);
    }

    /**
     * Where a worker can stand to bite this block (see {@link #findStandSpots}): only inside the nest's own space, at
     * the entrance or out in the open, never down a cave or a crack next to the nest (the hole it came in by may be
     * sealed while it is in there, and it would be shut in). The entrance plug is bitten from the top of the shaft
     * under it, clinging to the shaft wall.
     */
    public List<BlockPos> standSpotsFor(Level level, BlockPos target) {
        return standSpotsFor(level, target, nestCells(level));
    }

    private List<BlockPos> standSpotsFor(Level level, BlockPos target, Set<BlockPos> nestCells) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos spot : findStandSpots(level, target)) {
            if (spot.equals(center) || nestCells.contains(spot) || spot.getY() >= center.getY()) { // nest, entrance, or above ground
                spots.add(spot);
            }
        }
        if (target.equals(center)) {
            spots.add(0, center.below());
        }
        // A foraging hole is opened from the end of its tunnel, under it.
        if (colony.foragingHoles().isHole(level, target) && nestCells.contains(target.below())) {
            spots.remove(target.below());
            spots.add(0, target.below());
        }
        // A foraging tunnel climbing a slope is dug upward too, from the tunnel block under it (the stand spots above only
        // look sideways and on top: the last block of a new tunnel up a slope was never dug, the user's bug, 2026-10-14).
        if (nestCells.contains(target.below()) && !spots.contains(target.below()) && isForagingTunnelCell(level, target)) {
            spots.add(target.below());
        }
        // The mound's own soil in a shaft is cleared from below too (the old cap of a chimney that grew).
        if (colony.mound().isClearJob(level, target) && level.getBlockState(target.below()).isAir()) {
            spots.add(target.below());
        }
        return spots;
    }

    /** A cell of a foraging tunnel or of a feeding gallery (both may climb, so they are dug upward too). */
    private boolean isForagingTunnelCell(Level level, BlockPos pos) {
        for (NestPlan.ForagingTunnel tunnel : getForagingTunnels(level)) {
            if (tunnel.tunnel().contains(pos)) {
                return true;
            }
        }
        return galleryCells().contains(pos);
    }

    /** Whether the entrance hole is open: nothing solid in it (grass or snow do not close it). */
    public boolean isEntranceOpen(Level level) {
        BlockState state = level.getBlockState(center);
        return level.isLoaded(center) && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /**
     * Soil a worker may bite: the diggable blocks, and a crust of mud standing where the nest wants open space (the wall of
     * an older gallery in the way of a new one).
     */
    static boolean isSoil(BlockState state) {
        return state.is(ModTags.DIGGABLE) || MudCrustBlock.isCrust(state);
    }

    /** Open, or only a heap of seeds, bread or forage on the floor: the block is dug out. A chunk that is not loaded counts as not dug. */
    public static boolean isDugOut(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getBlock() instanceof SeedPileBlock;
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
        NestPlan.Chamber chamber = layout == null ? null : layout.of(kind);
        return chamber == null ? List.of() : getRoomCells(level, layout, chamber);
    }

    private List<BlockPos> getRoomCells(Level level, Layout layout, NestPlan.Chamber chamber) {
        if (!isShaftOpen(level, chamber.kind() == NestPlan.Kind.FOUNDING ? NestPlan.FOUNDING_DEPTH : layout.shaftDepth())) {
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

    /** The side rooms of the plan (empty if the nest has none, or no plan yet). For tests and debugging. */
    public List<NestPlan.Chamber> getSideRooms(Level level) {
        Layout layout = getLayout(level);
        return layout == null ? List.of() : layout.sideRooms();
    }

    /** The founding chamber of the plan, or null if the nest has none (or no plan yet). For tests and debugging. */
    @Nullable
    public NestPlan.Chamber getFoundingChamber(Level level) {
        Layout layout = getLayout(level);
        return layout == null ? null : layout.founding();
    }

    /** Every block that is part of the nest plan: the shaft, and the rooms once their layout is decided. */
    public Set<BlockPos> nestCells(Level level) {
        Set<BlockPos> cells = new HashSet<>();
        int depth = shaftDepth(level);
        for (int i = 1; i <= depth; i++) {
            cells.add(center.below(i));
        }
        NestPlan.Chamber founding = getFoundingChamber(level);
        BlockPos foundingShaft = center.below(NestPlan.FOUNDING_DEPTH);
        if (founding != null && level.isLoaded(foundingShaft) && level.getBlockState(foundingShaft).isAir()) {
            cells.addAll(founding.cells());
        }
        BlockPos bottom = center.below(depth);
        if (level.isLoaded(bottom) && level.getBlockState(bottom).isAir()) {
            Layout layout = getLayout(level);
            if (layout != null) {
                cells.addAll(layout.cells());
            }
            cells.addAll(colony.mound().getGalleries(level)); // none for species without a mound
        }
        for (NestPlan.ForagingTunnel tunnel : getForagingTunnels(level)) {
            cells.addAll(tunnel.tunnel());
        }
        cells.addAll(galleryCells());
        return cells;
    }

    /**
     * The foraging tunnels of the plan (the profile's {@code nest.foraging_holes}; see {@link NestPlan#foragingTunnels}),
     * none until the plan is made.
     */
    public List<NestPlan.ForagingTunnel> getForagingTunnels(Level level) {
        Layout planned = getLayout(level);
        if (planned == null) {
            return List.of();
        }
        if (planned != foragingTunnelsFor) {
            if (fittedTunnels != null) {
                foragingTunnels = fittedTunnels;
            } else {
                var numbers = colony.profile().nest();
                List<NestPlan.ForagingTunnel> flat = NestPlan.foragingTunnels(center, planned.direction(), numbers.foragingHoles(), numbers.foragingHoleDistance());
                foragingTunnels = fitToGround(level, planned, flat);
            }
            foragingTunnelsFor = planned;
            allForagingTunnelsStale = true;
        }
        if (allForagingTunnelsStale) {
            List<NestPlan.ForagingTunnel> all = new ArrayList<>(foragingTunnels);
            newTunnels.forEach(tunnel -> all.add(tunnel.tunnel()));
            allForagingTunnels = List.copyOf(all);
            allForagingTunnelsStale = false;
        }
        return allForagingTunnels;
    }

    /**
     * The planned foraging tunnels run straight and level out from the shaft. On a hillside the ground over the far end
     * is higher or lower than the plan expects, and the hole was never at the surface (the user's bug of 2026-10-14:
     * workers opened the one block over the tunnel's end, and the soil above it still closed the way out). A tunnel
     * whose hole is not at the surface is planned again to follow the ground ({@link TunnelRoute#straight}, real galleries
     * follow the lie of the land); one that is kept as it is (flat ground, and nests whose holes already work). Kept and
     * saved once every column of them is loaded.
     */
    private List<NestPlan.ForagingTunnel> fitToGround(Level level, Layout planned, List<NestPlan.ForagingTunnel> flat) {
        Set<BlockPos> rooms = new HashSet<>(planned.cells());
        for (int i = 1; i <= planned.shaftDepth(); i++) {
            rooms.add(center.below(i));
        }
        if (planned.founding() != null) {
            rooms.addAll(planned.founding().cells());
        }
        GroundReader ground = new GroundReader(level, colony, rooms);
        List<NestPlan.ForagingTunnel> fitted = new ArrayList<>();
        boolean allLoaded = true;
        for (NestPlan.ForagingTunnel tunnel : flat) {
            BlockPos hole = tunnel.hole();
            if (!level.isLoaded(hole) || !level.isLoaded(hole.relative(tunnel.side(), 2))) {
                allLoaded = false;
                fitted.add(tunnel);
                continue;
            }
            // At the surface: the hole is the top ground block of its column, or it was opened there already (open, the
            // tunnel's end under it dug, solid ground under that). Not on a hillside: soil over it (uphill) or air
            // under it (downhill, the tunnel would run out in the open).
            Integer top = ground.groundTop(hole.getX(), hole.getZ());
            boolean opened = Nest.isDugOut(level, hole) && Nest.isDugOut(level, hole.below())
                    && !level.getBlockState(hole.below(2)).getCollisionShape(level, hole.below(2)).isEmpty();
            boolean atSurface = top == null || top == hole.getY() || opened;
            TunnelRoute.Plan plan = atSurface ? null
                    : TunnelRoute.straight(center.below(NestPlan.FORAGING_TUNNEL_DEPTH), tunnel.side(), tunnel.tunnel().size(), MIN_FITTED_TUNNEL, ground);
            if (plan != null) {
                AntColoniesMod.LOGGER.debug("[ground] the foraging tunnel to the {} follows the ground: {} blocks, hole at {} (was {})",
                        tunnel.side(), plan.cells().size(), plan.hole().toShortString(), hole.toShortString());
            }
            fitted.add(plan == null ? tunnel : new NestPlan.ForagingTunnel(plan.cells(), plan.hole(), tunnel.side()));
        }
        if (allLoaded) {
            fittedTunnels = List.copyOf(fitted);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                ColonyManager.get(server).markChanged();
            }
        }
        return List.copyOf(fitted);
    }

    /** A foraging tunnel fitted to the ground is at least this many columns long (shorter: the level plan is kept). */
    private static final int MIN_FITTED_TUNNEL = 4;

    /**
     * A tunnel dug out to rich food after the nest was planned, and the food it leads to. {@code gallery}: the feeding
     * gallery from the tunnel up under the food (see {@link FeedingGallery}), null until it is planned (once the tunnel
     * is dug), empty if there is none (the food cannot be reached from below).
     */
    public record NewTunnel(NestPlan.ForagingTunnel tunnel, BlockPos food, @Nullable List<BlockPos> gallery) {
        public NewTunnel(NestPlan.ForagingTunnel tunnel, BlockPos food) {
            this(tunnel, food, null);
        }
    }

    /** The feeding gallery of the new tunnel to this food is planned (see {@link NewTunnel#gallery}). */
    void setGallery(BlockPos food, List<BlockPos> gallery) {
        for (int i = 0; i < newTunnels.size(); i++) {
            NewTunnel tunnel = newTunnels.get(i);
            if (tunnel.food().equals(food)) {
                newTunnels.set(i, new NewTunnel(tunnel.tunnel(), tunnel.food(), List.copyOf(gallery)));
            }
        }
        insideTick = Long.MIN_VALUE;
    }

    /**
     * Whether the feeding gallery this cell is part of is finished and hidden (the user's decision, 2026-10-14): every
     * block of it dug out and closed all round (see {@link NestWalls#isClosedRound}), so the termites eat the food over
     * it from inside, unseen. A cell of no gallery (a tunnel beside the food) only needs to be closed round itself.
     */
    boolean isGalleryClosed(Level level, BlockPos cell, Set<BlockPos> nest) {
        List<BlockPos> gallery = List.of(cell);
        for (NewTunnel tunnel : newTunnels) {
            if (tunnel.gallery() != null && tunnel.gallery().contains(cell)) {
                gallery = tunnel.gallery();
                break;
            }
        }
        for (BlockPos part : gallery) {
            if (!level.isLoaded(part)) {
                return false;
            }
            if (!isDugOut(level, part)) {
                if (isSoil(level.getBlockState(part))) {
                    return false; // still being dug
                }
                continue; // never to be dug (a stone found its way in): not part of it
            }
            if (!colony.walls().isClosedRound(level, part, nest)) {
                return false;
            }
        }
        return true;
    }

    /** The cells of every feeding gallery planned (dug or not). */
    List<BlockPos> galleryCells() {
        List<BlockPos> cells = new ArrayList<>();
        for (NewTunnel tunnel : newTunnels) {
            if (tunnel.gallery() != null && !isSpent(tunnel.tunnel().hole())) {
                cells.addAll(tunnel.gallery());
            }
        }
        return cells;
    }

    /** The tunnels dug (or being dug) out to rich food. */
    public List<NewTunnel> getNewTunnels() {
        return List.copyOf(newTunnels);
    }

    /** A new tunnel out to rich food: the workers dig it like the other foraging tunnels. */
    void addNewTunnel(NewTunnel tunnel) {
        newTunnels.add(tunnel);
        allForagingTunnelsStale = true;
        insideTick = Long.MIN_VALUE;
    }

    /** Whether this hole's food is gone: it is plugged for good and never opened again. */
    public boolean isSpent(BlockPos hole) {
        return spentHoles.contains(hole);
    }

    void markSpent(BlockPos hole) {
        spentHoles.add(hole.immutable());
    }

    void unmarkSpent(BlockPos hole) {
        spentHoles.remove(hole);
    }

    /**
     * Whether a cell is inside the colony's home, out of the sun: the entrance, the nest's shaft, rooms, galleries and
     * foraging tunnels, and the hollow shaft, the tunnels, the hollow spire pieces and chimney mouths of the mound (a
     * termite in their channels is inside), but those only once they are built (the plan's empty cell where a tunnel or a
     * spire piece will go is open air: termites stood there by day thinking they were in, the user's report). Not the
     * foraging holes themselves (the way out), not the open air round the mound, and not the mound's hollow shaft while it
     * is still open to the sky (a young mound without its roof).
     */
    public boolean isInside(Level level, BlockPos cell) {
        long now = level.getGameTime();
        if (now != insideTick) {
            Set<BlockPos> cells = nestCells(level);
            cells.add(center);
            // A feeding gallery cell is inside only while it has a roof (the food over it, the mud cast left of it, a
            // branch cased in mud): once the roof is gone it is open to the sky.
            List<BlockPos> galleries = galleryCells();
            for (BlockPos gallery : galleries) {
                if (level.isLoaded(gallery) && level.getBlockState(gallery.above()).getCollisionShape(level, gallery.above()).isEmpty()
                        && !NestWalls.isWalkThroughFood(level, gallery.above())) {
                    cells.remove(gallery);
                }
            }
            Set<BlockPos> channels = new HashSet<>();
            // The mud casts left of eaten wood, joined by their channels to a gallery: inside too (eaten on from there).
            Map<BlockPos, BlockPos> casts = new HashMap<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
            for (BlockPos gallery : galleries) {
                if (cells.contains(gallery)) {
                    joinCasts(level, gallery, gallery, casts, queue);
                }
            }
            while (!queue.isEmpty() && casts.size() < MAX_CASTS) {
                BlockPos cast = queue.poll();
                joinCasts(level, cast, casts.get(cast), casts, queue);
            }
            channels.addAll(casts.keySet());
            castGalleries = casts;
            colony.mound().getParts(level).forEach((pos, part) -> {
                if (part == MoundPlan.Part.OPEN) {
                    // Only once roofed over: a young mound's shaft open to the sky is outside (termites coming home
                    // stopped in it, clinging to its walls above the plug, the user's bug of 2026-10-13).
                    if (level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY() + 1) {
                        cells.add(pos);
                    }
                } else if (part == MoundPlan.Part.MOUTH || part == MoundPlan.Part.TUNNEL || part == MoundPlan.Part.SPIRE) {
                    channels.add(pos);
                }
            });
            inside = cells;
            insideChannels = channels;
            insideTick = now;
        }
        return inside.contains(cell) || (insideChannels.contains(cell) && MoundReach.channelKind(level.getBlockState(cell)) != null);
    }

    /** At most this many mud casts are joined to the galleries (a fallen tree is about 8 blocks). */
    private static final int MAX_CASTS = 256;

    /** Adds the mud casts whose channels join the one at {@code from} (a gallery cell or a cast) to {@code casts}. */
    private static void joinCasts(Level level, BlockPos from, BlockPos gallery, Map<BlockPos, BlockPos> casts,
            java.util.ArrayDeque<BlockPos> queue) {
        BlockState fromState = level.getBlockState(from);
        boolean fromCast = com.antcolonies.block.MudCastBlock.isCast(fromState);
        for (Direction side : Direction.values()) {
            BlockPos next = from.relative(side);
            if (casts.containsKey(next) || !level.isLoaded(next)) {
                continue;
            }
            BlockState state = level.getBlockState(next);
            if (com.antcolonies.block.MudCastBlock.isCast(state)
                    && com.antcolonies.nav.Channel.has(com.antcolonies.block.TermiteTunnelBlock.openMask(state), side.getOpposite())
                    && (!fromCast || com.antcolonies.nav.Channel.has(com.antcolonies.block.TermiteTunnelBlock.openMask(fromState), side))) {
                casts.put(next.immutable(), gallery);
                queue.add(next.immutable());
            }
        }
    }

    /**
     * The feeding gallery cell this inside cell eats from: a mud cast's gallery cell (see {@link #isInside}), or the
     * cell itself.
     */
    BlockPos galleryFor(Level level, BlockPos cell) {
        isInside(level, cell); // brings the casts up to date
        return castGalleries.getOrDefault(cell, cell);
    }

    /** The first block in the list that is diggable, has a place to stand next to it (see {@link #standSpotsFor}), and is safe to open. */
    @Nullable
    BlockPos firstDiggable(Level level, List<BlockPos> cells) {
        Set<BlockPos> nestCells = null;
        for (BlockPos pos : cells) {
            if (!level.isLoaded(pos)) {
                return null;
            }
            if (!isSoil(level.getBlockState(pos))) {
                continue;
            }
            if (nestCells == null) {
                nestCells = nestCells(level);
            }
            if (!standSpotsFor(level, pos, nestCells).isEmpty()
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
    int shaftDepth(Level level) {
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
                List<NestPlan.Chamber> all = new ArrayList<>(NestPlan.chambers(center.below(depth), direction, NestPlan.MAX_LEVELS));
                all.add(NestPlan.foundingChamber(center.below(NestPlan.FOUNDING_DEPTH), direction));
                all.addAll(sideRooms(center.below(depth), direction));
                for (NestPlan.Chamber chamber : all) {
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
            // The depth with the most levels wins, then the one where the side rooms fit too; on a tie the deeper one (it is tried first).
            if (found != null && (best == null || found.chambers().size() > best.chambers().size()
                    || found.chambers().size() == best.chambers().size() && sideRoomsFit(level, found) && !sideRoomsFit(level, best))) {
                best = found;
            }
        }
        return best;
    }

    /** Whether the side rooms of this layout are all soil (or already dug). A nest is still planned where they don't fit; they are then dug as far as the soil goes. */
    private static boolean sideRoomsFit(Level level, Layout layout) {
        for (NestPlan.Chamber chamber : layout.sideRooms()) {
            if (!allSoilOrAir(level, chamber.cells())) {
                return false;
            }
        }
        return true;
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
        boolean withFounding = colony.profile().nest().foundingChamber();
        Layout best = null;
        boolean bestSideFits = false;
        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue((start + i) % 4);
            // The founding chamber (the queen's first home, on the far side of the shaft) has to fit too.
            NestPlan.Chamber founding = withFounding ? NestPlan.foundingChamber(center.below(NestPlan.FOUNDING_DEPTH), direction) : null;
            if (founding != null && (!allSoilOrAir(level, founding.cells()) || !isCovered(level, founding.cells(), shaft))) {
                continue;
            }
            // Side rooms (the termites' food store) don't have to fit, but a direction where they do is better.
            List<NestPlan.Chamber> side = sideRooms(bottom, direction);
            boolean sideFits = true;
            for (NestPlan.Chamber chamber : side) {
                sideFits &= allSoilOrAir(level, chamber.cells());
            }
            // The most levels whose whole plan is soil. A shorter nest uses its levels differently (and its last one is
            // always the small queen's room), so each length is checked as the plan it really is.
            int maxLevels = Math.min(NestPlan.MAX_LEVELS, Math.max(1, colony.profile().nest().maxLevels())); // from the species profile
            for (int levels = maxLevels; levels >= 1; levels--) {
                List<NestPlan.Chamber> plan = NestPlan.chambers(bottom, direction, levels);
                if (best != null && (levels < best.chambers().size() || levels == best.chambers().size() && (bestSideFits || !sideFits))) {
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
                    best = new Layout(plan, side, gardens(plan, direction), depth, direction, founding);
                    bestSideFits = sideFits;
                    break;
                }
            }
        }
        return best;
    }

    /** Soil, or already dug out (a heap of seeds or bread on the floor counts as dug: in an old save the plan is worked out again on a nest in use). */
    /**
     * Whether these cells lie under cover: no block beside them (but the cells themselves and the shaft) is open air or
     * a plant under the sky, or water. On a hillside the queen's chamber must not run out under the slope with its roof or a wall open to
     * the outside (the hill test of 2026-10-14: the side the plan picked changed with each new colony).
     */
    private static boolean isCovered(Level level, List<BlockPos> cells, List<BlockPos> shaft) {
        Set<BlockPos> own = new HashSet<>(cells);
        own.addAll(shaft);
        for (BlockPos pos : cells) {
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (own.contains(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                // Open to the sky (rooms of the nest dug beside it later are underground and fine), or water.
                if (!state.getFluidState().isEmpty() || (state.isAir() || state.canBeReplaced()) && level.canSeeSky(next)) {
                    return false;
                }
            }
        }
        return true;
    }

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
            // Beside it on a heap of food or a fungus comb is fine too (a room partly dug and already in use).
            if (level.isLoaded(pos) && isDugOut(level, pos)) {
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

    /**
     * Nest stages: how many levels (top to bottom: each a stair, a tunnel and a room) the workers may dig now. A level
     * opens once the colony has the workers and the stored food its species profile asks for ({@code nest.level_unlocks}),
     * so a young colony has a small, shallow nest that grows deeper as the colony grows. Once open a level stays open,
     * even if the colony shrinks again.
     */
    public int levelsUnlocked(Level level) {
        int now = colony.profile().nest().levelsFor(colony.getCount(com.antcolonies.entity.WorkerAntEntity.CASTE), colony.food().getFood());
        if (now > levelsUnlocked) {
            levelsUnlocked = now;
        }
        return levelsUnlocked;
    }

    /** Tests only: every level may be dug at once (for tests about the digging itself, not about nest stages). */
    public void unlockAllLevelsForTests() {
        levelsUnlocked = Integer.MAX_VALUE;
    }

    void save(CompoundTag tag) {
        tag.putInt("nestLevelsUnlocked", levelsUnlocked);
        ListTag tunnels = new ListTag();
        for (NewTunnel tunnel : newTunnels) {
            CompoundTag entry = new CompoundTag();
            entry.putLongArray("cells", tunnel.tunnel().tunnel().stream().mapToLong(BlockPos::asLong).toArray());
            entry.putLong("hole", tunnel.tunnel().hole().asLong());
            entry.putInt("side", tunnel.tunnel().side().get3DDataValue());
            entry.putLong("food", tunnel.food().asLong());
            if (tunnel.gallery() != null) {
                entry.putLongArray("gallery", tunnel.gallery().stream().mapToLong(BlockPos::asLong).toArray());
            }
            tunnels.add(entry);
        }
        tag.put("newTunnels", tunnels);
        if (fittedTunnels != null) {
            ListTag fitted = new ListTag();
            for (NestPlan.ForagingTunnel tunnel : fittedTunnels) {
                CompoundTag entry = new CompoundTag();
                entry.putLongArray("cells", tunnel.tunnel().stream().mapToLong(BlockPos::asLong).toArray());
                entry.putLong("hole", tunnel.hole().asLong());
                entry.putInt("side", tunnel.side().get3DDataValue());
                fitted.add(entry);
            }
            tag.put("fittedTunnels", fitted);
        }
        tag.putLongArray("spentHoles", spentHoles.stream().mapToLong(BlockPos::asLong).toArray());
        if (layout != null) {
            tag.putInt("nestDepth", layout.shaftDepth());
            tag.putInt("nestDirection", layout.direction().get2DDataValue());
            tag.putInt("nestLevels", layout.chambers().size());
            tag.putBoolean("nestFounding", layout.founding() != null);
        }
    }

    void load(CompoundTag tag) {
        // A nest saved before nest stages existed digs all its levels, as it did then.
        levelsUnlocked = tag.contains("nestLevelsUnlocked") ? tag.getInt("nestLevelsUnlocked") : Integer.MAX_VALUE;
        newTunnels.clear();
        for (Tag element : tag.getList("newTunnels", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            List<BlockPos> cells = new ArrayList<>();
            for (long cell : entry.getLongArray("cells")) {
                cells.add(BlockPos.of(cell));
            }
            if (!cells.isEmpty()) {
                List<BlockPos> gallery = null;
                if (entry.contains("gallery")) {
                    gallery = new ArrayList<>();
                    for (long cell : entry.getLongArray("gallery")) {
                        gallery.add(BlockPos.of(cell));
                    }
                }
                newTunnels.add(new NewTunnel(new NestPlan.ForagingTunnel(List.copyOf(cells), BlockPos.of(entry.getLong("hole")),
                        Direction.from3DDataValue(entry.getInt("side"))), BlockPos.of(entry.getLong("food")), gallery == null ? null : List.copyOf(gallery)));
            }
        }
        fittedTunnels = null;
        if (tag.contains("fittedTunnels")) {
            List<NestPlan.ForagingTunnel> fitted = new ArrayList<>();
            for (Tag element : tag.getList("fittedTunnels", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) element;
                List<BlockPos> cells = new ArrayList<>();
                for (long cell : entry.getLongArray("cells")) {
                    cells.add(BlockPos.of(cell));
                }
                if (!cells.isEmpty()) {
                    fitted.add(new NestPlan.ForagingTunnel(List.copyOf(cells), BlockPos.of(entry.getLong("hole")), Direction.from3DDataValue(entry.getInt("side"))));
                }
            }
            fittedTunnels = List.copyOf(fitted);
        }
        foragingTunnelsFor = null;
        spentHoles.clear();
        for (long hole : tag.getLongArray("spentHoles")) {
            spentHoles.add(BlockPos.of(hole));
        }
        allForagingTunnelsStale = true;
        // The nest plan it was digging (old saves have none: it is worked out again from the ground).
        if (tag.contains("nestLevels")) {
            int depth = tag.getInt("nestDepth");
            Direction direction = Direction.from2DDataValue(tag.getInt("nestDirection"));
            // Nests planned before founding chambers existed have none (the key is missing: false).
            NestPlan.Chamber founding = tag.getBoolean("nestFounding")
                    ? NestPlan.foundingChamber(center.below(NestPlan.FOUNDING_DEPTH), direction) : null;
            List<NestPlan.Chamber> chambers = NestPlan.chambers(center.below(depth), direction, tag.getInt("nestLevels"));
            layout = new Layout(chambers, sideRooms(center.below(depth), direction), gardens(chambers, direction), depth, direction, founding);
        }
    }
}
