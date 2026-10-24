package com.antcolonies.colony;

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
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ways a nest reaches out to food (termites): the tunnels dug out to rich food after the nest was planned, each
 * with its feeding gallery up under the food ({@link ColonyHooks.FeedingWays}), the holes of those tunnels whose food is gone,
 * and the runways of mud sheeting from a hole to food near it ({@link RunwayRoute}). The colony's foraging holes
 * ({@link ForagingHoles}) decide when to plan and drop them; this only keeps them, and is saved with the {@link Nest}
 * (same keys as when it was part of it). Species-neutral: a species without new holes or sheeting has none.
 */
public class FoodWays {
    private final Colony colony;
    /** Told whenever a tunnel, gallery or runway changes (the nest works out its foraging tunnels and what is inside again). */
    private final Runnable changed;
    /**
     * Tunnels dug out to rich food after the nest was planned (new holes near food, see {@link ForagingHoles}), each
     * with the food it was dug for. Saved.
     */
    private final List<NewTunnel> newTunnels = new ArrayList<>();
    /** The holes of new tunnels whose food is gone: plugged for good, never opened again (the tunnel stays). Saved. */
    private final Set<BlockPos> spentHoles = new HashSet<>();
    /** Runways of mud sheeting from a hole to food near it (see {@link Runway}). Saved. */
    private final List<Runway> runways = new ArrayList<>();
    /** The cells of runways whose food is gone: their mud crumbles away bit by bit (see {@link ForagingHoles}). Saved. */
    private final List<List<BlockPos>> crumblingRunways = new ArrayList<>();

    FoodWays(Colony colony, Runnable changed) {
        this.colony = colony;
        this.changed = changed;
    }

    /**
     * A tunnel dug out to rich food after the nest was planned, and the food it leads to. {@code gallery}: the feeding
     * gallery from the tunnel up under the food (see {@link ColonyHooks.FeedingWays}), null until it is planned (once the tunnel
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
        changed.run();
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
        Runway runway = runwayOf(cell);
        if (runway != null) {
            gallery = runway.cells();
        }
        for (BlockPos part : gallery) {
            if (!level.isLoaded(part)) {
                return false;
            }
            if (runway != null && !Nest.isDugOut(level, part)) {
                return false; // grass still growing in it, or something put in its way
            }
            if (!Nest.isDugOut(level, part)) {
                if (Nest.isSoil(level.getBlockState(part))) {
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

    /** The cells of every feeding gallery planned (dug or not), and of every runway of mud sheeting. */
    List<BlockPos> galleryCells() {
        List<BlockPos> cells = new ArrayList<>();
        for (NewTunnel tunnel : newTunnels) {
            if (tunnel.gallery() != null && !isSpent(tunnel.tunnel().hole())) {
                cells.addAll(tunnel.gallery());
            }
        }
        for (Runway runway : runways) {
            cells.addAll(runway.cells());
        }
        return cells;
    }

    /**
     * A runway of mud sheeting (the user's plan of 2026-10-14): real Macrotermes plaster a covered runway of soil over the
     * ground from a hole out to food near it, and forage under it, unseen, by day too. {@code cells}: the cells a termite
     * walks in, from the one over the {@code hole} to one beside the {@code food} (see {@link RunwayRoute}). They are
     * part of the nest like a feeding gallery: the workers plaster a crust of mud over every open side of them (the
     * leaks, see {@link NestWalls}); once it is closed all round the runway is inside, its hole stays open, and the food
     * at its end is eaten from inside it.
     */
    public record Runway(BlockPos hole, BlockPos food, List<BlockPos> cells) {
    }

    /** The runways of mud sheeting. */
    public List<Runway> getRunways() {
        return List.copyOf(runways);
    }

    void addRunway(Runway runway) {
        runways.add(runway);
        changed.run();
    }

    /** The runway leaving from this hole, or null. */
    @Nullable
    public Runway runwayAt(BlockPos hole) {
        for (Runway runway : runways) {
            if (runway.hole().equals(hole)) {
                return runway;
            }
        }
        return null;
    }

    /** The runway this cell is part of, or null. */
    @Nullable
    Runway runwayOf(BlockPos cell) {
        for (Runway runway : runways) {
            if (runway.cells().contains(cell)) {
                return runway;
            }
        }
        return null;
    }

    /** Whether the runway is finished: every cell of it open and closed all round (see {@link #isGalleryClosed}). */
    public boolean isRunwayClosed(Level level, Runway runway) {
        return isGalleryClosed(level, runway.cells().get(0), colony.nest().nestCells(level));
    }

    /** The runway's food is gone: its mud is left to crumble away (see {@link ForagingHoles}). */
    void dropRunway(Runway runway) {
        runways.remove(runway);
        crumblingRunways.add(new ArrayList<>(runway.cells()));
        changed.run();
    }

    /** The cells of the runways whose mud is crumbling away. */
    List<List<BlockPos>> getCrumblingRunways() {
        return crumblingRunways;
    }

    /** A copy of the cells of the runways whose mud is crumbling away (for a species keeping a dead colony's works). */
    public List<List<BlockPos>> crumblingRunwayCells() {
        return crumblingRunways.stream().map(List::copyOf).toList();
    }

    /** Tests only: a runway of mud sheeting as if the colony had planned it (its cells must be laid out by the test). */
    public void addRunwayForTests(Runway runway) {
        addRunway(runway);
    }

    /** Tests only: a new tunnel out to food as if the colony had dug it (its cells must be dug by the test). */
    public void addNewTunnelForTests(NewTunnel tunnel) {
        addNewTunnel(tunnel);
    }

    /**
     * A cell of a runway holding a plant (grass growing where the runway runs): the workers bite it away like soil (real
     * termites cut the grass under their sheeting), at foraging time, once the runway's hole is open. Null if none.
     */
    @Nullable
    BlockPos runwayPlant(Level level) {
        if (!colony.isForagingTime(level)) {
            return null;
        }
        for (Runway runway : runways) {
            if (!ForagingHoles.isOpen(level, runway.hole())) {
                continue;
            }
            for (BlockPos cell : runway.cells()) {
                if (isRunwayPlant(level, cell)) {
                    return cell;
                }
            }
        }
        return null;
    }

    /**
     * Whether this cell of a runway holds a plant to be cut away. Food (dry grass, a dead bush) is cut too, but its
     * pieces are left lying for the foragers ({@code FoodStore.cutAwayForTheForagers}), so no food is thrown away.
     */
    public boolean isRunwayPlant(Level level, BlockPos cell) {
        if (!level.isLoaded(cell)) {
            return false;
        }
        BlockState state = level.getBlockState(cell);
        return !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty() && runwayOf(cell) != null;
    }

    /** The tunnels dug (or being dug) out to rich food. */
    public List<NewTunnel> getNewTunnels() {
        return List.copyOf(newTunnels);
    }

    /** A new tunnel out to rich food: the workers dig it like the other foraging tunnels. */
    void addNewTunnel(NewTunnel tunnel) {
        newTunnels.add(tunnel);
        changed.run();
    }

    /** The tunnel dug out to rich food ending at this hole, or null. */
    @Nullable
    public NewTunnel newTunnelAt(BlockPos hole) {
        for (NewTunnel tunnel : newTunnels) {
            if (tunnel.tunnel().hole().equals(hole)) {
                return tunnel;
            }
        }
        return null;
    }

    /** The tunnel to rich food ending at this hole is gone from the record (its gallery piece was filled in with soil). */
    public void dropNewTunnel(BlockPos hole) {
        if (newTunnels.removeIf(tunnel -> tunnel.tunnel().hole().equals(hole))) {
            spentHoles.remove(hole);
            changed.run();
        }
    }

    /** Whether this hole is the end of a tunnel dug out to rich food whose food is not gone yet. */
    public boolean isLiveNewHole(BlockPos hole) {
        return !isSpent(hole) && newTunnels.stream().anyMatch(t -> t.tunnel().hole().equals(hole));
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

    void save(CompoundTag tag) {
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
        tag.putLongArray("spentHoles", spentHoles.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag runwayList = new ListTag();
        for (Runway runway : runways) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", runway.hole().asLong());
            entry.putLong("food", runway.food().asLong());
            entry.putLongArray("cells", runway.cells().stream().mapToLong(BlockPos::asLong).toArray());
            runwayList.add(entry);
        }
        tag.put("runways", runwayList);
        ListTag crumbling = new ListTag();
        for (List<BlockPos> cells : crumblingRunways) {
            CompoundTag entry = new CompoundTag();
            entry.putLongArray("cells", cells.stream().mapToLong(BlockPos::asLong).toArray());
            crumbling.add(entry);
        }
        tag.put("crumblingRunways", crumbling);
    }

    void load(CompoundTag tag) {
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
        spentHoles.clear();
        for (long hole : tag.getLongArray("spentHoles")) {
            spentHoles.add(BlockPos.of(hole));
        }
        runways.clear();
        for (Tag element : tag.getList("runways", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            List<BlockPos> cells = new ArrayList<>();
            for (long cell : entry.getLongArray("cells")) {
                cells.add(BlockPos.of(cell));
            }
            if (!cells.isEmpty()) {
                runways.add(new Runway(BlockPos.of(entry.getLong("hole")), BlockPos.of(entry.getLong("food")), List.copyOf(cells)));
            }
        }
        crumblingRunways.clear();
        for (Tag element : tag.getList("crumblingRunways", Tag.TAG_COMPOUND)) {
            List<BlockPos> cells = new ArrayList<>();
            for (long cell : ((CompoundTag) element).getLongArray("cells")) {
                cells.add(BlockPos.of(cell));
            }
            crumblingRunways.add(cells);
        }
    }
}
