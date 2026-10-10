package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

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
    public static final int SHAFT_DEPTH = 8;

    /** The chamber at the bottom of the shaft: these depths, out to this many blocks from the shaft. */
    private static final int CHAMBER_TOP_DEPTH = 6;
    private static final int CHAMBER_RADIUS = 2;

    /** How far from the entrance the mound reaches, in blocks. */
    private static final int MOUND_RADIUS = 3;

    /**
     * Every block the ants dig, in order, as {sideways x, depth below the entrance, sideways z}:
     * the shaft first, then the chamber at its bottom, layer by layer, nearest the shaft first.
     */
    private static final List<int[]> DIG_PLAN = new ArrayList<>();

    static {
        for (int depth = 1; depth <= SHAFT_DEPTH; depth++) {
            DIG_PLAN.add(new int[] {0, depth, 0});
        }
        for (int depth = CHAMBER_TOP_DEPTH; depth <= SHAFT_DEPTH; depth++) {
            for (int ring = 1; ring <= CHAMBER_RADIUS; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                            DIG_PLAN.add(new int[] {dx, depth, dz});
                        }
                    }
                }
            }
        }
    }

    /** Bite progress per block (0..1). Not saved: a half-dug block just restarts after a reload. */
    private final Map<BlockPos, Float> digProgress = new HashMap<>();

    /**
     * The next block to dig, or null if the mound is finished (no more soil needed) or nothing
     * more can be dug. A block is only dug if the ants can reach it from open space next to it.
     */
    @Nullable
    public BlockPos findDigTarget(Level level) {
        if (isMoundComplete(level)) {
            return null;
        }
        for (int[] offset : DIG_PLAN) {
            BlockPos pos = center.offset(offset[0], -offset[1], offset[2]);
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

    /** Open space next to a block where an ant can stand to bite it: above first, then the four sides. */
    @Nullable
    public static BlockPos findStandSpot(Level level, BlockPos target) {
        BlockPos above = target.above();
        if (level.getBlockState(above).isAir()) {
            return above;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = target.relative(side);
            if (level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
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
     * How tall the mound is meant to be in the column dx, dz blocks from the entrance: 3 blocks
     * in the first ring, 2 in the second, 1 in the third, so it slopes down like an ant hill.
     * The hole itself stays open.
     */
    private static int moundHeightAt(int dx, int dz) {
        int distSq = dx * dx + dz * dz;
        if (distSq == 0) {
            return 0;
        } else if (distSq <= 2) {
            return 3;
        } else if (distSq <= 6) {
            return 2;
        } else if (distSq <= 12) {
            return 1;
        }
        return 0;
    }

    /** The first free block above the ground in a column of the mound area. */
    private static BlockPos moundColumnTop(Level level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** True when no column of the mound has room for another pellet. */
    private boolean isMoundComplete(Level level) {
        for (int dx = -MOUND_RADIUS; dx <= MOUND_RADIUS; dx++) {
            for (int dz = -MOUND_RADIUS; dz <= MOUND_RADIUS; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (moundHeightAt(dx, dz) > 0 && level.hasChunk(x >> 4, z >> 4)
                        && isMoundSpot(level, moundColumnTop(level, x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A spot where an ant can drop its soil pellet. The mound fills layer by layer: the pellet
     * goes to a random one of the lowest free spots, so it grows evenly and never as thin towers.
     * Returns null when the mound is finished.
     */
    @Nullable
    public BlockPos pickMoundSpot(Level level, RandomSource random) {
        BlockPos best = null;
        int ties = 0;
        for (int dx = -MOUND_RADIUS; dx <= MOUND_RADIUS; dx++) {
            for (int dz = -MOUND_RADIUS; dz <= MOUND_RADIUS; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (moundHeightAt(dx, dz) == 0 || !level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos pos = moundColumnTop(level, x, z);
                if (!isMoundSpot(level, pos)) {
                    continue;
                }
                if (best == null || pos.getY() < best.getY()) {
                    best = pos;
                    ties = 1;
                } else if (pos.getY() == best.getY() && random.nextInt(++ties) == 0) {
                    best = pos;
                }
            }
        }
        return best;
    }

    /** A pellet may go here if it is free, rests on something solid, and its column isn't tall enough yet. */
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
