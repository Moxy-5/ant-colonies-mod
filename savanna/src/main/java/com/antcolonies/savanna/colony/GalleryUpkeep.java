package com.antcolonies.savanna.colony;

import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.Nest;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.Population;
import com.antcolonies.colony.TunnelRoute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/**
 * The galleries' upkeep (Apex part 3, T2, one of the four parts of {@link Galleries}): which pieces lead to food (their
 * last use), far pieces that led nowhere for {@code fill_days} days packed with soil (step 3), pieces walled off against
 * neighbours (brain step 4g, step 5), and the holes the brain remembers as dug (step 4, picked from while away).
 */
final class GalleryUpkeep {
    /** How often (ticks) the pieces' use is looked at and unused far branches filled in. */
    private static final int UPKEEP_TICKS = 200;

    /** Game ticks in a day. */
    private static final long DAY = 24000L;

    /** When each piece last led to food (game time, by its hole). Saved. */
    final Map<BlockPos, Long> lastUsed = new HashMap<>();

    /** Blocks of gallery filled in and not grown again since: only growth toward food may use them. Saved. */
    int filledLength;

    /** How many pieces were walled off against neighbours (step 5). Saved. */
    private int walledOff;

    /** How many pieces were filled in, and how many blocks (for the colony list). Saved. */
    int fills;

    int filledBlocks;

    long upkeptAt = Long.MIN_VALUE;

    /**
     * The pieces whose branch beyond was filled in: that way led nowhere, so they no longer spread at random (real
     * termites do not dig again where they backfilled); food may still draw them on. Saved.
     */
    final Set<BlockPos> backfilled = new HashSet<>();

    /**
     * Step 4: the holes last seen dug (looked at while here), from which the night's holes are picked while away (the
     * brain never reads unloaded land). Saved.
     */
    final Set<BlockPos> dugHoles = new HashSet<>();

    /** Step 4: when the numbers last brought food in by each hole (a piece they forage from counts as used). Saved. */
    private final Map<BlockPos, Long> foragedFrom = new HashMap<>();

    final Galleries g;
    private final Colony colony;

    GalleryUpkeep(Galleries g) {
        this.g = g;
        this.colony = g.colony;
    }

    /** How many blocks of filled-in gallery only growth toward food may use again. */
    public int filledLength() {
        return filledLength;
    }

    /** How many pieces were filled in so far. */
    public int fills() {
        return fills;
    }

    /** When the piece ending at this hole last led to food, or {@link Colony#NOT_SET}. */
    public long lastUsed(BlockPos hole) {
        return lastUsed.getOrDefault(hole, Colony.NOT_SET);
    }

    /** Tests only: every piece's last use moved this many ticks back, as if that long had gone by unused. */
    public void ageForTests(long ticks) {
        lastUsed.replaceAll((hole, at) -> at - ticks);
        foragedFrom.replaceAll((hole, at) -> at - ticks);
        upkeptAt = Long.MIN_VALUE;
    }

    /** The holes the brain remembers as dug (it picks the night's holes from these while away). For tests. */
    public Set<BlockPos> dugHolesForTests() {
        return java.util.Collections.unmodifiableSet(dugHoles);
    }

    /** Step 4: the numbers brought food in by this hole now (the piece it lies on counts as used). */
    public void noteForaged(BlockPos hole, long now) {
        foragedFrom.put(hole.immutable(), now);
    }

    /** How many gallery pieces were walled off against neighbours (for the tests and the list). */
    public int walledOff() {
        return walledOff;
    }

    /**
     * Step 5, in the upkeep: a piece at the web's edge within a piece's length of another colony's gallery is a meeting
     * (once a minute; real termites sense a neighbour's tunnels through the soil, so a young colony stopped short of
     * them keeps meeting them until it yields); a piece (not one of the
     * 4 arms, nothing growing from it) whose tip this colony yields at is walled off: packed with soil back to its fork,
     * as real neighbouring Macrotermes seal their galleries where they come close, by the same rules as filling in (out
     * of sight, nobody inside). Returns whether a piece was walled off.
     */
    private boolean wallOffNeighbours(ServerLevel level, Map<BlockPos, BlockPos> holeOf) {
        SpeciesProfile.NeighbourNumbers numbers = colony.neighbours().numbers();
        if (numbers == null || !numbers.wallGalleries()) {
            return false;
        }
        List<Colony> others = g.web.neighbourColonies(level);
        if (others.isEmpty()) {
            return false;
        }
        for (NestPlan.ForagingTunnel arm : List.copyOf(g.web.arms)) {
            if (!g.web.isLeaf(arm.hole())) {
                continue;
            }
            List<BlockPos> own = g.web.ownCells(arm);
            // (a tip stopped short of the neighbour still senses its tunnels through the soil, as far as its next piece would reach)
            double sense = g.numbers().pieceLength() + TunnelRoute.HOLE_REACH + numbers.within();
            for (Colony other : others) {
                BlockPos[] closest = GalleryWeb.closestTo(other, own, sense);
                if (closest == null || !g.web.galleriesMet(level, other, closest[0], closest[1], arm.hole())) {
                    continue;
                }
                BlockPos theirs = closest[1];
                if (g.web.base.contains(arm.hole()) || colony.nest().foodWays().isLiveNewHole(arm.hole())) {
                    continue; // (the 4 arms and a way to food in use are only stopped, never filled)
                }
                if (fillIn(level, arm, holeOf)) {
                    walledOff++;
                    SavannaMod.LOGGER.info("[neighbours] colony {}: the gallery to {} is walled off with soil (colony {}'s gallery at {})",
                            colony.getId(), arm.hole().toShortString(), other.getId(), theirs.toShortString());
                    return true;
                }
            }
        }
        return false;
    }

    // --- step 3: using and filling in -------------------------------------------------------------------------------

    /**
     * Every few seconds: each piece that leads to food now (a feeding spot whose food lasts, a runway from one of its
     * holes, known food or a remembered trail to food at one of its holes) is used, and so is every piece it grew from;
     * then one far piece unused for {@code fill_days} days is filled in, if it may be now.
     */
    void upkeep(ServerLevel level) {
        long now = level.getGameTime();
        FoodWays ways = colony.nest().foodWays();
        Map<BlockPos, BlockPos> holeOf = new HashMap<>(); // each hole (the ends and the holes along) to its piece
        for (NestPlan.ForagingTunnel arm : g.web.arms) {
            holeOf.put(arm.hole(), arm.hole());
            lastUsed.putIfAbsent(arm.hole().immutable(), now); // (an older save: counted from now)
            Long at = lastUsed.get(arm.hole());
            if (at > now) {
                lastUsed.put(arm.hole().immutable(), now); // (the game's clock was set back)
            }
        }
        Map<BlockPos, BlockPos> cellOf = new HashMap<>();
        for (NestPlan.ForagingTunnel arm : g.web.arms) {
            g.web.ownCells(arm).forEach(cell -> cellOf.putIfAbsent(cell, arm.hole()));
        }
        for (NestPlan.ForagingTunnel along : g.turns.holes) {
            BlockPos piece = cellOf.get(along.hole().below());
            if (piece != null) {
                holeOf.put(along.hole(), piece);
            }
        }
        Map<BlockPos, Double> food = g.turns.foodNear(level, new ArrayList<>(holeOf.keySet()), g.numbers().foodRadius());
        Set<BlockPos> used = new HashSet<>();
        holeOf.forEach((hole, piece) -> {
            if (ways.isLiveNewHole(hole) || ways.runwayAt(hole) != null || food.getOrDefault(hole, 0.0) > 0) {
                used.add(piece);
            }
        });
        for (BlockPos piece : used) {
            markUsed(piece, now);
        }
        // Step 4: a piece the numbers foraged from (while away too) was in use then.
        foragedFrom.keySet().removeIf(hole -> !holeOf.containsKey(hole));
        foragedFrom.forEach((hole, at) -> markUsed(holeOf.get(hole), Math.min(at, now)));
        rememberDugHoles(level);
        if (wallOffNeighbours(level, holeOf)) {
            return; // (one piece at a time)
        }
        if (g.numbers().fillDays() <= 0) {
            return;
        }
        List<NestPlan.ForagingTunnel> due = new ArrayList<>();
        for (NestPlan.ForagingTunnel arm : g.web.arms) {
            if (isDue(arm.hole(), now)) {
                due.add(arm);
            }
        }
        // The farthest first: the web is filled in from its far ends back.
        BlockPos center = colony.getCenter();
        due.sort(Comparator.comparingDouble((NestPlan.ForagingTunnel arm) -> -Galleries.flat(arm.hole(), center)));
        boolean catchingUp = g.catchUp.isCatchingUp(now);
        int filled = 0;
        for (NestPlan.ForagingTunnel piece : due) {
            if (g.web.pieceAt(piece.hole()) == null) {
                continue; // (filled in just now)
            }
            int blocks = filledBlocks;
            if (fillIn(level, piece, holeOf)) {
                if (!catchingUp) {
                    return; // one piece at a time
                }
                // Just back (step 4): every branch due is filled in at once, as it would have been while away.
                colony.away().report().add(AwayReport.What.FILLED_IN, 1);
                colony.away().report().add(AwayReport.What.FILLED_BLOCKS, filledBlocks - blocks);
                if (++filled >= GalleryCatchUp.CATCH_UP_FILLS) {
                    return;
                }
            }
        }
    }

    /**
     * Whether the piece ending at this hole is due to be filled in: a far piece no other grows from, not one of the 4
     * arms nor a tunnel out to food that lasts, that led to no food for {@code fill_days} days.
     */
    boolean isDue(BlockPos hole, long now) {
        Long at = lastUsed.get(hole);
        return g.numbers().fillDays() > 0 && at != null && g.web.pieceAt(hole) != null && !g.web.base.contains(hole) && g.web.isLeaf(hole)
                && !colony.nest().foodWays().isLiveNewHole(hole) && now - at >= g.numbers().fillDays() * DAY;
    }

    /** The piece ending at this hole and every piece it grew from were in use at {@code at}. */
    private void markUsed(@Nullable BlockPos piece, long at) {
        for (int up = 0; piece != null && up < 1000; up++) {
            if (lastUsed.getOrDefault(piece, Long.MIN_VALUE) < at) {
                lastUsed.put(piece.immutable(), at);
            }
            piece = g.web.parents.get(piece);
        }
    }

    /** The holes whose tunnels are dug, as seen now (loaded ones only), for picking the night's holes while away. */
    private void rememberDugHoles(ServerLevel level) {
        Set<BlockPos> all = new HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            BlockPos hole = tunnel.hole();
            all.add(hole);
            if (HoleTurns.allLoaded(level, tunnel)) {
                if (ForagingHoles.isDug(level, tunnel)) {
                    dugHoles.add(hole.immutable());
                } else {
                    dugHoles.remove(hole);
                }
            }
        }
        dugHoles.retainAll(all);
    }

    /**
     * Packs one unused far piece with soil and takes it out of the web for good (step 3, the user's decision: real
     * termites backfill galleries that lead nowhere), if it may be now: every block loaded, its holes shut, nobody in it
     * or travelling unseen through it, and no player able to see any of it (it is done out of sight, all at once).
     */
    private boolean fillIn(ServerLevel level, NestPlan.ForagingTunnel piece, Map<BlockPos, BlockPos> holeOf) {
        BlockPos end = piece.hole();
        List<BlockPos> holesOfPiece = new ArrayList<>();
        holeOf.forEach((hole, of) -> {
            if (of.equals(end)) {
                holesOfPiece.add(hole);
            }
        });
        List<BlockPos> cells = new ArrayList<>(g.web.ownCells(piece));
        FoodWays.NewTunnel toFood = colony.nest().foodWays().newTunnelAt(end);
        if (toFood != null && toFood.gallery() != null) {
            cells.addAll(toFood.gallery());
        }
        // Blocks another tunnel (the nest's own, another piece, another feeding gallery) runs through stay open.
        Set<BlockPos> others = new HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : colony.nest().ownForagingTunnels(level)) {
            others.addAll(tunnel.tunnel());
        }
        for (NestPlan.ForagingTunnel arm : g.web.arms) {
            if (!arm.hole().equals(end)) {
                others.addAll(g.web.ownCells(arm));
            }
        }
        for (FoodWays.NewTunnel tunnel : colony.nest().foodWays().getNewTunnels()) {
            if (!tunnel.tunnel().hole().equals(end)) {
                others.addAll(tunnel.tunnel().tunnel());
                if (tunnel.gallery() != null) {
                    others.addAll(tunnel.gallery());
                }
            }
        }
        cells.removeIf(others::contains);
        if (cells.isEmpty()) {
            return false;
        }
        for (BlockPos cell : cells) {
            if (!level.isLoaded(cell)) {
                return false;
            }
        }
        for (BlockPos hole : holesOfPiece) {
            if (!level.isLoaded(hole) || ForagingHoles.isOpen(level, hole)) {
                return false; // (shut tonight, or once the foragers are in)
            }
        }
        Set<BlockPos> inside = new HashSet<>(cells);
        inside.addAll(holesOfPiece);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos cell : inside) {
            minX = Math.min(minX, cell.getX());
            minY = Math.min(minY, cell.getY());
            minZ = Math.min(minZ, cell.getZ());
            maxX = Math.max(maxX, cell.getX());
            maxY = Math.max(maxY, cell.getY());
            maxZ = Math.max(maxZ, cell.getZ());
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(1);
        for (net.minecraft.world.entity.LivingEntity mob : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                net.minecraft.world.entity.LivingEntity::isAlive)) {
            BlockPos at = mob.blockPosition();
            if (inside.contains(at) || inside.contains(at.above()) || inside.contains(at.below())) {
                return false; // never wall an insect in (docs/BUGS.md 8, 9, 24, 42)
            }
        }
        for (ColonyHooks.HiddenAway away : colony.hooks().all(ColonyHooks.HIDDEN_AWAY)) {
            if (away.anyWalksThrough(inside)) {
                return false; // (an insect on a hidden trip walks through there)
            }
        }
        List<net.minecraft.world.phys.Vec3> eyes = colony.population().viewersInSight(level);
        if (!eyes.isEmpty()) {
            for (BlockPos cell : inside) {
                if (!Population.outOfSight(level, cell, eyes)) {
                    return false;
                }
            }
        }
        int filled = 0;
        for (BlockPos cell : cells) {
            if (Nest.isDugOut(level, cell)) {
                level.setBlockAndUpdate(cell, soilFor(level, cell));
                filled++;
            }
        }
        int own = ownCellsCount(piece);
        removePiece(level, piece, holesOfPiece);
        filledLength += own;
        fills++;
        filledBlocks += filled;
        SavannaMod.LOGGER.debug("[galleries] the piece to {} led to no food for {} days: filled in ({} blocks packed with soil)",
                end.toShortString(), g.numbers().fillDays(), filled);
        return true;
    }

    /** The soil a filled-in block is packed with: the ground round it where that is soil the termites dig, else dirt. */
    private static net.minecraft.world.level.block.state.BlockState soilFor(ServerLevel level, BlockPos cell) {
        for (Direction side : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos next = cell.relative(side);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(next);
            if (state.is(com.antcolonies.registry.ModTags.DIGGABLE) && state.isCollisionShapeFullBlock(level, next) && !state.hasBlockEntity()) {
                return state.getBlock() == net.minecraft.world.level.block.Blocks.GRASS_BLOCK
                        ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : state.getBlock().defaultBlockState();
            }
        }
        return net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
    }

    private int ownCellsCount(NestPlan.ForagingTunnel piece) {
        return piece.tunnel().size() - Math.min(piece.tunnel().size(), g.web.newFrom.getOrDefault(piece.hole(), 0));
    }

    /** A filled-in piece leaves the web with its holes (they are never opened again); the piece it grew from may grow on. */
    private void removePiece(ServerLevel level, NestPlan.ForagingTunnel piece, List<BlockPos> holesOfPiece) {
        BlockPos end = piece.hole();
        List<NestPlan.ForagingTunnel> all = new ArrayList<>(g.web.arms);
        all.remove(piece);
        g.web.arms = List.copyOf(all);
        g.web.newFrom.remove(end);
        lastUsed.remove(end);
        g.web.tipFails.remove(end);
        g.web.tips.removeIf(tip -> tip.way().hole().equals(end));
        g.turns.laidOut.remove(end);
        g.turns.holes.removeIf(along -> holesOfPiece.contains(along.hole()));
        for (BlockPos hole : holesOfPiece) {
            g.turns.holesAlongSet.remove(hole);
            g.turns.lastTurn.remove(hole);
            g.turns.tonight.remove(hole);
        }
        colony.nest().foodWays().dropNewTunnel(end);
        BlockPos parent = g.web.parents.remove(end);
        if (parent != null && g.web.tipsMade && g.web.isLeaf(parent) && g.web.tips.stream().noneMatch(tip -> tip.way().hole().equals(parent))) {
            NestPlan.ForagingTunnel of = g.web.pieceAt(parent);
            if (of != null) {
                g.web.tips.add(new GalleryWeb.Tip(of, GalleryWeb.bearing(colony.getCenter(), parent), 0, GalleryWeb.forkAt(g.numbers(), colony.random())));
            }
        }
        if (parent != null) {
            backfilled.add(parent);
        }
        backfilled.remove(end);
        g.changed.run();
        ColonyManager.get(level).markChanged();
    }

    /** Whether the upkeep is due now (every {@link #UPKEEP_TICKS}, and at every chance while catching up); noted as done. */
    boolean isTime(long now) {
        if (upkeptAt == Long.MIN_VALUE || now - upkeptAt >= UPKEEP_TICKS || now < upkeptAt || g.catchUp.isCatchingUp(now)) {
            upkeptAt = now;
            return true;
        }
        return false;
    }

    /** The upkeep's part of the galleries' save (the names as before the split). */
    void save(CompoundTag out) {
        ListTag usedList = new ListTag();
        lastUsed.forEach((hole, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", hole.asLong());
            entry.putLong("at", at);
            usedList.add(entry);
        });
        out.put("lastUsed", usedList);
        out.putInt("filledLength", filledLength);
        out.putInt("fills", fills);
        out.putInt("walledOff", walledOff);
        out.putInt("filledBlocks", filledBlocks);
        out.putLongArray("backfilled", backfilled.stream().mapToLong(BlockPos::asLong).toArray());
        out.putLongArray("dugHoles", dugHoles.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag foraged = new ListTag();
        foragedFrom.forEach((hole, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", hole.asLong());
            entry.putLong("at", at);
            foraged.add(entry);
        });
        out.put("foragedFrom", foraged);
    }

    void load(CompoundTag in) {
        lastUsed.clear();
        for (Tag element : in.getList("lastUsed", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            lastUsed.put(BlockPos.of(entry.getLong("hole")), entry.getLong("at"));
        }
        filledLength = in.getInt("filledLength");
        fills = in.getInt("fills");
        walledOff = in.getInt("walledOff");
        filledBlocks = in.getInt("filledBlocks");
        backfilled.clear();
        for (long hole : in.getLongArray("backfilled")) {
            backfilled.add(BlockPos.of(hole));
        }
        dugHoles.clear();
        for (long hole : in.getLongArray("dugHoles")) {
            dugHoles.add(BlockPos.of(hole));
        }
        foragedFrom.clear();
        for (Tag element : in.getList("foragedFrom", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            foragedFrom.put(BlockPos.of(entry.getLong("hole")), entry.getLong("at"));
        }
        upkeptAt = Long.MIN_VALUE;
    }
}
