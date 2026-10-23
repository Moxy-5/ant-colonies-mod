package com.antcolonies.desert.colony;

import com.antcolonies.colony.Claims;
import com.antcolonies.colony.Colony;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.desert.registry.DesertBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The hill around the entrance, built from the soil the ants dig out: where the next pellet goes, which ant is bringing
 * soil to which spot, and fetching soil to mend the hill when a block of it has been knocked out.
 */
public class Hill {
    /** The mound reaches this far (in blocks, sideways) from the entrance. */
    public static final int MOUND_RADIUS = 3;

    /** Most ants that fetch soil for the hill at the same time (never more than the hill has gaps). */
    private static final int MAX_REPAIRERS = 3;
    /** How long (in ticks) a soil pellet fetched for the hill counts as on its way to the gap. */
    private static final int REPAIR_CLAIM_TICKS = 300;
    /** After a repair pellet could not be delivered (no route to the gap), no more repair soil is fetched for this long (in ticks). */
    private static final int HILL_PAUSE_TICKS = 1200;
    private static final int MOUND_CLAIM_TICKS = 400;

    private final Colony colony;
    private final BlockPos center;

    /** The ants that fetched soil to repair the hill: ant -> game time when the pellet should have arrived. Not saved. */
    private final Claims<UUID> repairers = new Claims<>();
    /** The hill spot each ant with a pellet is on its way to (not saved). */
    private final Map<UUID, MoundClaim> moundClaims = new HashMap<>();

    private record MoundClaim(BlockPos spot, long until) {
    }

    /** No repair soil is fetched before this game time (see {@link #HILL_PAUSE_TICKS}). Not saved. */
    private long hillPausedUntil;

    /** The night soil: piles of loose soil near the top of the nest, carried up first thing each morning. */
    private final NightSoil nightSoil;

    Hill(Colony colony) {
        this.colony = colony;
        this.center = colony.getCenter();
        this.nightSoil = new NightSoil(colony, this);
    }

    /** Its colony (for the parts registry's tick row). */
    Colony colony() {
        return colony;
    }

    /** The soil dug while the surface was shut, waiting near the top of the nest (see {@link NightSoil}). */
    public NightSoil nightSoil() {
        return nightSoil;
    }

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

    /**
     * A spot on the surface near the entrance where an ant drops its soil pellet, or null when the hill
     * is complete. The hill is built from the bottom up, layer by layer, so every pellet rests on another
     * and the finished mound has exactly the shape of {@link #moundHeightAt}.
     */
    @Nullable
    public BlockPos pickMoundSpot(Level level, RandomSource random, @Nullable UUID ant) {
        long now = level.getGameTime();
        moundClaims.values().removeIf(claim -> claim.until <= now);
        List<BlockPos> spots = openMoundSpots(level);
        // One ant to a spot: ants don't push each other, so a crowd in one cell could never place the pellet (nobody may stand in it).
        spots.removeIf(spot -> moundClaims.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(ant) && entry.getValue().spot.equals(spot)));
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
        BlockPos chosen = lowestSpots.get(random.nextInt(lowestSpots.size()));
        if (ant != null) {
            moundClaims.put(ant, new MoundClaim(chosen.immutable(), now + MOUND_CLAIM_TICKS));
        }
        return chosen;
    }

    /** The ant has put its pellet down or given up: its hill spot is free for others again. */
    public void releaseMoundSpot(UUID ant) {
        moundClaims.remove(ant);
    }

    /** False once the hill is complete: ants then stop carrying soil out. */
    public boolean hasMoundRoom(Level level) {
        return !openMoundSpots(level).isEmpty();
    }

    /**
     * The morning clear-out (real Pogonomyrmex: the nest maintenance workers carry out the soil dug in the night first
     * thing each morning, before they dig on): while the surface is open, a builder carries a waiting pile up before it
     * digs ({@link com.antcolonies.desert.entity.goal.CarryNightSoilGoal}); while it is shut, the diggers rest once the piles
     * are as big as a morning's carrying ({@link NightSoil#isFull}). The entrance (a plug, a plant in it) is always
     * bitten: that is how the surface opens. Only while the hill has room.
     */
    public boolean soilWaits(ServerLevel level, AbstractAntEntity digger) {
        if (colony.buildsMound() || !hasMoundRoom(level)) {
            return false;
        }
        boolean plugged = colony.nest().isEntrancePlugged(level);
        if (colony.nest().isPlantInEntrance(level) || plugged && colony.nest().mayOpenEntrance(level)) {
            return false; // the entrance comes first
        }
        if (digger.mayGoOut() && !plugged) {
            return nightSoil.hasFreePile(level, digger.getUUID());
        }
        return nightSoil.isFull(level);
    }

    /**
     * Whether a worker should fetch soil to mend the hill now: the nest is dug (until then the digging makes the soil),
     * the hill has a gap that no pellet is on its way to, and a repair has not just failed.
     */
    public boolean needsHillRepair(ServerLevel level) {
        long now = level.getGameTime();
        repairers.prune(now);
        if (now < hillPausedUntil || repairers.size() >= MAX_REPAIRERS) {
            return false;
        }
        int gaps = openMoundSpots(level).size();
        return gaps > repairers.size() && colony.nest().findDigTarget(level) == null;
    }

    /** A worker has set out for repair soil: it counts as a pellet on its way until it should have arrived. */
    public void claimHillRepair(ServerLevel level, UUID ant) {
        repairers.put(ant, level.getGameTime() + REPAIR_CLAIM_TICKS);
    }

    /** A soil pellet could not be brought to the hill (no route to the gap): wait a while before fetching more for repairs. */
    public void pauseHillRepair(Level level) {
        hillPausedUntil = level.getGameTime() + HILL_PAUSE_TICKS;
    }

    void save(CompoundTag tag) {
        nightSoil.save(tag);
    }

    void load(CompoundTag tag) {
        nightSoil.load(tag);
    }

    /** Where repair soil is scraped up: inside the entrance shaft, right under the hole. */
    public BlockPos getRepairSoilSpot() {
        return center.below();
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
                // A block knocked out from the middle of a column leaves a gap under the blocks that are still there.
                for (int y = pos.getY() - 1; y >= center.getY(); y--) {
                    BlockPos gap = new BlockPos(x, y, z);
                    if (isMoundSpot(level, gap) && isCoveredByHill(level, gap, moundHeightAt(dx, dz))) {
                        spots.add(gap);
                    }
                }
            }
        }
        return spots;
    }

    /** True if a block of the hill is still standing above this gap (so it is a gap in the hill, not just open ground). */
    private boolean isCoveredByHill(Level level, BlockPos gap, int height) {
        for (int y = gap.getY() + 1; y < center.getY() + height; y++) {
            if (level.getBlockState(new BlockPos(gap.getX(), y, gap.getZ())).is(DesertBlocks.BURROWED_DIRT.get())) {
                return true;
            }
        }
        return false;
    }

    /** A pellet may go here if it is free, rests on something solid, and the hill is not yet full at this spot. */
    private boolean isMoundSpot(Level level, BlockPos pos) {
        int height = moundHeightAt(pos.getX() - center.getX(), pos.getZ() - center.getZ());
        return pos.getY() < center.getY() + height
                && pos.getY() >= center.getY() - 2
                && level.getBlockState(pos).canBeReplaced()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** Moves an ant out of a cell that is about to be filled: on top of it if that is free, else to a free cell beside it. */
    private static void nudgeOut(Level level, LivingEntity ant, BlockPos pos) {
        List<BlockPos> targets = new ArrayList<>();
        targets.add(pos.above());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            targets.add(pos.relative(side));
            targets.add(pos.relative(side).above());
        }
        for (BlockPos target : targets) {
            if (level.getBlockState(target).getCollisionShape(level, target).isEmpty()) {
                ant.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
                ant.setDeltaMovement(0, 0, 0);
                if (ant instanceof AbstractAntEntity abstractAnt) {
                    abstractAnt.getNavigator().stop();
                }
                return;
            }
        }
    }

    /** For the log: is a pellet still allowed at this spot? */
    public boolean isMoundSpotValid(Level level, BlockPos pos) {
        return isMoundSpot(level, pos);
    }

    /** Turns a delivered pellet into a real block of the mound. Returns false if the spot is no longer valid. */
    public boolean placePellet(Level level, BlockPos pos) {
        if (!isMoundSpot(level, pos)) {
            return false;
        }
        // Never bury anyone: an animal or a player standing in the spot means try elsewhere. Ants do not push each other
        // and the spot is where the whole traffic of the hole passes, so the soil nudges them out onto the new block.
        List<LivingEntity> inSpot = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos));
        for (LivingEntity living : inSpot) {
            if (!(living instanceof AbstractAntEntity)) {
                return false;
            }
        }
        for (LivingEntity ant : inSpot) {
            nudgeOut(level, ant, pos);
        }
        // Whatever the ant dug, the mound is built of burrowed dirt (and it never slides away like sand).
        level.setBlock(pos, DesertBlocks.BURROWED_DIRT.get().defaultBlockState(), 3);
        return true;
    }
}
