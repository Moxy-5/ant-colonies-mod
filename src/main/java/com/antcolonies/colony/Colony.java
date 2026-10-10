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

    /** From the bottom of the shaft a tunnel this long leads sideways to a room. */
    private static final int TUNNEL_LENGTH = 3;

    /** The room is this many blocks long (along the tunnel) and 2 * ROOM_HALF_WIDTH + 1 wide, one block tall. */
    private static final int ROOM_LENGTH = 5;
    private static final int ROOM_HALF_WIDTH = 2;

    /** The mound is at most this many blocks tall above the entrance's ground level. */
    public static final int MOUND_HEIGHT = 3;

    /** Bite progress per block (0..1). Not saved: a half-dug block just restarts after a reload. */
    private final Map<BlockPos, Float> digProgress = new HashMap<>();

    /**
     * The next block to dig, or null if all the digging is done (or blocked). Order: the entrance
     * shaft, then the tunnel off its bottom, then the room at the end of the tunnel. The shaft is
     * never widened, so its walls stay solid for the ants to climb.
     */
    @Nullable
    public BlockPos findDigTarget(Level level) {
        List<BlockPos> shaft = new ArrayList<>();
        for (int i = 1; i <= SHAFT_DEPTH; i++) {
            shaft.add(center.below(i));
        }
        BlockPos next = firstDiggable(level, shaft);
        if (next != null) {
            return next;
        }
        // Only start the tunnel once the shaft is completely open down to its bottom.
        BlockPos bottom = center.below(SHAFT_DEPTH);
        if (!level.isLoaded(bottom) || !level.getBlockState(bottom).isAir()) {
            return null;
        }
        Direction direction = chooseTunnelDirection(level, bottom);
        return direction == null ? null : firstDiggable(level, tunnelAndRoomCells(bottom, direction));
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

    /** Every block of the tunnel and room, in digging order, for a tunnel leaving the shaft bottom in this direction. */
    private static List<BlockPos> tunnelAndRoomCells(BlockPos bottom, Direction direction) {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 1; i <= TUNNEL_LENGTH; i++) {
            cells.add(bottom.relative(direction, i));
        }
        Direction sideways = direction.getClockWise();
        for (int row = 1; row <= ROOM_LENGTH; row++) {
            BlockPos rowCenter = bottom.relative(direction, TUNNEL_LENGTH + row);
            // Middle of the row first, then outward, so every block touches already-dug space.
            cells.add(rowCenter);
            for (int offset = 1; offset <= ROOM_HALF_WIDTH; offset++) {
                cells.add(rowCenter.relative(sideways, offset));
                cells.add(rowCenter.relative(sideways, -offset));
            }
        }
        return cells;
    }

    /**
     * Picks which of the four sides the tunnel leaves toward: the first (starting from a side
     * that is fixed per colony) where everything to dig is soil. Null = wait (chunk not loaded) or no room.
     */
    @Nullable
    private Direction chooseTunnelDirection(Level level, BlockPos bottom) {
        int start = Math.floorMod(id.hashCode(), 4);
        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue((start + i) % 4);
            boolean fits = true;
            for (BlockPos pos : tunnelAndRoomCells(bottom, direction)) {
                if (!level.isLoaded(pos)) {
                    return null;
                }
                BlockState state = level.getBlockState(pos);
                if (!(state.is(ModTags.DIGGABLE) || state.isAir()) || touchesFluid(level, pos)) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                return direction;
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
     * A spot on the surface near the entrance where an ant drops its soil pellet. Random each
     * time, so pellets spread into a ring around the hole (step 3 turns them into a mound).
     */
    @Nullable
    public BlockPos pickMoundSpot(Level level, RandomSource random) {
        BlockPos best = null;
        // Try a few random spots in a ring around the entrance and take the lowest one,
        // so the mound fills in evenly instead of growing into thin towers.
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 2.0 + random.nextDouble() * 1.5;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            // The first free block above the ground at that column.
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (isMoundSpot(level, pos) && (best == null || pos.getY() < best.getY())) {
                best = pos;
            }
        }
        return best;
    }

    /** A pellet may go here if it is free, rests on something solid, and the mound isn't too tall yet. */
    private boolean isMoundSpot(Level level, BlockPos pos) {
        return pos.getY() < center.getY() + MOUND_HEIGHT
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
