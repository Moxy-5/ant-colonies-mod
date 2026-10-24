package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.block.FacedWall;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.colony.Nest;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.savanna.block.DeadWoodBlock;
import com.antcolonies.savanna.block.FallenBranchBlock;
import com.antcolonies.savanna.block.MudCastBlock;
import com.antcolonies.savanna.block.TermiteMoundBlock;
import com.antcolonies.savanna.registry.TreeBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The works a dead termite colony leaves out round its mound (M8 step 1a part 3, the user's yes of 2026-10-24): its
 * foraging holes, the mud sheeting over its runways, the mud over the wood it was eating and the casts left of it, and
 * its galleries under the ground. Nobody repairs them any more, so the rain takes them in the real order, sped up like
 * the mound ({@link MoundWear}): the thin mud on the surface first, then the holes silt up and grass closes over them,
 * while the galleries under the ground last much longer (the far ends first) and slowly fill in with washed-in soil.
 *
 * <p>Written down when the colony ends ({@link #record}: only the places, what stands there is looked at when each one's
 * day comes), worked by the dead mound book ({@link DeadMounds}) by the same rules as the mound: the world's clock, only
 * forward, catching up when the land loads, a few blocks a look. Only the termites' own blocks are taken (mud crust, mud
 * casts, the soil plug in a hole, an open hole or gallery); anything else found there is left alone and forgotten. The
 * nest itself (its shaft and rooms) and the mound are not part of it: they stay, the dead colony's home for the
 * Matabele ants.
 */
final class DeadWorks {
    /** A place whose day has come but that cannot change yet (an animal stands in it): tried again next look. */
    private static final int WAIT = -1;

    /** The foraging holes: silt up and grass over ({@link MoundWear#holeDay}). */
    final List<BlockPos> holes = new ArrayList<>();
    /** The cells under the mud sheeting of the runways: the mud round them crumbles ({@link MoundWear#surfaceMudDay}). */
    final List<BlockPos> sheets = new ArrayList<>();
    /** The food the feeding galleries were dug under: the mud over it and the casts left of it crumble. */
    final List<BlockPos> spots = new ArrayList<>();
    /** The gallery cells, for their walls of mud under the open sky (on a hillside, at a feeding spot): crumble. */
    final List<BlockPos> walls = new ArrayList<>();
    /** The gallery cells, to fill in with washed-in soil ({@link MoundWear#galleryDay}). */
    final List<BlockPos> galleries = new ArrayList<>();
    /** How far out (blocks) the galleries reached from the nest: the far ends fill in first. */
    int reach;

    /** The colony has died out ({@code ColonyNews.COLONY_ENDED}, heard by its galleries): its works go into the dead mound book. */
    static void colonyEnded(ServerLevel level, Colony colony, BlockPos center) {
        Set<BlockPos> mound = colony.part(SavannaParts.MOUND).map(part -> part.getParts(level).keySet()).orElse(Set.of());
        DeadMounds.get(level).addWorks(level, colony.getId(), center, record(level, colony, mound));
    }

    boolean isEmpty() {
        return holes.isEmpty() && sheets.isEmpty() && spots.isEmpty() && walls.isEmpty() && galleries.isEmpty();
    }

    /**
     * What the colony had built out round its nest when it died: every foraging hole (the nest's own, along the galleries,
     * by the food), the galleries and tunnels under the ground, the feeding galleries and the food over them, the runways
     * of sheeting, those crumbling too. {@code mound}: the mound's cells, left to its own wear.
     */
    static DeadWorks record(ServerLevel level, Colony colony, Set<BlockPos> mound) {
        Nest nest = colony.nest();
        BlockPos center = colony.getCenter();
        Set<BlockPos> home = new HashSet<>(mound);
        home.add(center);
        for (int i = 1; i <= nest.shaftDepth(level); i++) {
            home.add(center.below(i));
        }
        List<NestPlan.Chamber> rooms = new ArrayList<>(nest.getPlannedChambers(level));
        rooms.addAll(nest.getGardens(level));
        if (nest.getFoundingChamber(level) != null) {
            rooms.add(nest.getFoundingChamber(level));
        }
        for (NestPlan.Chamber room : rooms) {
            home.addAll(room.cells());
        }
        Set<BlockPos> holes = new LinkedHashSet<>(), sheets = new LinkedHashSet<>(), spots = new LinkedHashSet<>();
        Set<BlockPos> galleries = new LinkedHashSet<>();
        for (NestPlan.ForagingTunnel tunnel : nest.getForagingTunnels(level)) {
            holes.add(tunnel.hole());
            galleries.addAll(tunnel.tunnel());
        }
        for (FoodWays.NewTunnel tunnel : nest.foodWays().getNewTunnels()) {
            holes.add(tunnel.tunnel().hole());
            galleries.addAll(tunnel.tunnel().tunnel());
            if (tunnel.gallery() != null && !tunnel.gallery().isEmpty()) {
                galleries.addAll(tunnel.gallery());
                spots.add(tunnel.food());
            }
        }
        for (FoodWays.Runway runway : nest.foodWays().getRunways()) {
            holes.add(runway.hole());
            sheets.addAll(runway.cells());
        }
        for (List<BlockPos> cells : nest.foodWays().crumblingRunwayCells()) {
            sheets.addAll(cells);
        }
        holes.removeAll(home);
        sheets.removeAll(home);
        galleries.removeAll(home);
        galleries.removeAll(holes);
        galleries.removeAll(sheets);
        DeadWorks works = new DeadWorks();
        holes.forEach(pos -> works.holes.add(pos.immutable()));
        sheets.forEach(pos -> works.sheets.add(pos.immutable()));
        spots.forEach(pos -> works.spots.add(pos.immutable()));
        galleries.forEach(pos -> works.galleries.add(pos.immutable()));
        works.walls.addAll(works.galleries);
        for (BlockPos cell : works.galleries) {
            works.reach = Math.max(works.reach, (int) Math.ceil(flat(cell, center)));
        }
        return works;
    }

    /**
     * The works brought up to their day ({@code days} since the colony died), changing at most {@code budget} blocks:
     * the surface mud, then the holes, then the galleries. {@code dirty} is told whenever anything changes. Returns how
     * many blocks it changed.
     */
    int weather(ServerLevel level, BlockPos center, double days, int budget, Runnable dirty) {
        int used = 0;
        used += pass(level, sheets, pos -> MoundWear.surfaceMudDay(MoundWear.jitter(pos.asLong())), days, budget - used, dirty,
                (lvl, pos) -> crumbleAround(lvl, pos, false));
        used += pass(level, spots, pos -> MoundWear.surfaceMudDay(MoundWear.jitter(pos.asLong())), days, budget - used, dirty,
                DeadWorks::crumbleSpot);
        used += pass(level, walls, pos -> MoundWear.surfaceMudDay(MoundWear.jitter(pos.asLong())), days, budget - used, dirty,
                (lvl, pos) -> crumbleAround(lvl, pos, true));
        used += pass(level, holes, pos -> MoundWear.holeDay(MoundWear.jitter(pos.asLong())), days, budget - used, dirty,
                DeadWorks::closeHole);
        used += pass(level, galleries, pos -> MoundWear.galleryDay(flat(pos, center), reach, MoundWear.jitter(pos.asLong() * 7 + 1)),
                days, budget - used, dirty, DeadWorks::fillIn);
        return used;
    }

    private interface Step {
        /** What happens at a place whose day has come: how many blocks it changed (it is then done), or {@link #WAIT}. */
        int at(ServerLevel level, BlockPos pos);
    }

    /** Every place of the list whose day has come, in loaded land, while the budget lasts. */
    private static int pass(ServerLevel level, List<BlockPos> places, java.util.function.ToDoubleFunction<BlockPos> dayOf, double days,
            int budget, Runnable dirty, Step step) {
        int used = 0;
        for (Iterator<BlockPos> it = places.iterator(); it.hasNext() && used < budget; ) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos) || days < dayOf.applyAsDouble(pos)) {
                continue;
            }
            int changed = step.at(level, pos);
            if (changed == WAIT) {
                continue;
            }
            used += changed;
            it.remove();
            dirty.run();
        }
        return used;
    }

    /**
     * The plates of mud walling off this cell crumble: every one round it ({@code openSkyOnly} false: the sheeting of a
     * runway), or only those out under the open sky (a gallery's: its walls under the ground stay until it fills in, then
     * crumble by themselves, as nothing is left to wall off).
     */
    private static int crumbleAround(ServerLevel level, BlockPos cell, boolean openSkyOnly) {
        int changed = 0;
        for (Direction side : Direction.values()) {
            BlockPos wall = cell.relative(side);
            if (!level.isLoaded(wall)) {
                continue;
            }
            BlockState state = level.getBlockState(wall);
            if (FacedWall.hasFace(state, side.getOpposite()) && (!openSkyOnly || underTheSky(level, wall))) {
                BlockState less = FacedWall.withoutFace(state, side.getOpposite());
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, wall, Block.getId(state));
                if (FacedWall.hasNoFace(less)) {
                    level.removeBlock(wall, false);
                } else {
                    level.setBlock(wall, less, Block.UPDATE_ALL);
                }
                changed++;
            }
        }
        return changed;
    }

    /**
     * Whether a plate of mud here stands out under the open sky, nothing over it (by the height map, right after the land
     * loads too, before its light is worked out).
     */
    private static boolean underTheSky(ServerLevel level, BlockPos wall) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, wall.getX(), wall.getZ()) <= wall.getY() + 1;
    }

    /** At a feeding spot: the mud casts left of the eaten wood crumble away, and so does the mud plastered over the wood. */
    private static int crumbleSpot(ServerLevel level, BlockPos food) {
        int changed = 0;
        for (BlockPos piece : FeedingGallery.foodBlocks(level, food, pos -> isWoodOrCast(level.getBlockState(pos)))) {
            BlockState state = level.getBlockState(piece);
            if (MudCastBlock.isCast(state)) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, piece, Block.getId(state));
                level.removeBlock(piece, false);
                changed++;
            }
            changed += crumbleAround(level, piece, false);
        }
        return changed;
    }

    /** The wood a colony eats under mud, or a cast left of it. */
    private static boolean isWoodOrCast(BlockState state) {
        return MudCastBlock.isCast(state) || state.getBlock() instanceof DeadWoodBlock || state.getBlock() instanceof FallenBranchBlock
                || state.is(TreeBlocks.TERMITE_WOOD) || state.is(Blocks.HAY_BLOCK);
    }

    /**
     * A foraging hole silts up: its plug of soil, or the open hole, turns to grass (to soil where something covers it, an
     * open one only: a plug under cover is buried already). Anything else there (grass grown over, a player's block) is
     * left alone.
     */
    private static int closeHole(ServerLevel level, BlockPos hole) {
        BlockState state = level.getBlockState(hole);
        boolean plug = state.getBlock() instanceof TermiteMoundBlock;
        boolean open = state.canBeReplaced() && state.getFluidState().isEmpty();
        if (!plug && !open) {
            return 0;
        }
        if (open && !nobodyIn(level, hole)) {
            return WAIT;
        }
        BlockPos up = hole.above();
        boolean covered = level.getBlockState(up).isCollisionShapeFullBlock(level, up);
        if (covered && plug) {
            return 0;
        }
        level.setBlock(hole, covered ? GalleryUpkeep.soilFor(level, hole) : Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        return 1;
    }

    /** A gallery cell fills in with washed-in soil (the ground's own, as the living colony packs a gallery it gives up). */
    private static int fillIn(ServerLevel level, BlockPos cell) {
        if (!Nest.isDugOut(level, cell)) {
            return 0; // filled already, flooded, or something put in it
        }
        if (!nobodyIn(level, cell)) {
            return WAIT; // never bury an animal
        }
        level.setBlock(cell, GalleryUpkeep.soilFor(level, cell), Block.UPDATE_ALL);
        return 1;
    }

    private static boolean nobodyIn(ServerLevel level, BlockPos cell) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(cell), LivingEntity::isAlive).isEmpty();
    }

    /** The distance across the ground (not counting height) from the nest. */
    private static double flat(BlockPos pos, BlockPos center) {
        double dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** For the command: what is left. */
    String describe() {
        return "holes " + holes.size() + ", mud " + (sheets.size() + spots.size() + walls.size()) + ", galleries " + galleries.size();
    }

    // --- saving ---------------------------------------------------------------------------------------------------

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("holes", longs(holes));
        tag.putLongArray("sheets", longs(sheets));
        tag.putLongArray("spots", longs(spots));
        tag.putLongArray("walls", longs(walls));
        tag.putLongArray("galleries", longs(galleries));
        tag.putInt("reach", reach);
        return tag;
    }

    static DeadWorks load(CompoundTag tag) {
        DeadWorks works = new DeadWorks();
        read(tag.getLongArray("holes"), works.holes);
        read(tag.getLongArray("sheets"), works.sheets);
        read(tag.getLongArray("spots"), works.spots);
        read(tag.getLongArray("walls"), works.walls);
        read(tag.getLongArray("galleries"), works.galleries);
        works.reach = tag.getInt("reach");
        return works;
    }

    private static long[] longs(List<BlockPos> places) {
        return places.stream().mapToLong(BlockPos::asLong).toArray();
    }

    private static void read(long[] from, List<BlockPos> into) {
        for (long pos : from) {
            into.add(BlockPos.of(pos));
        }
    }
}
