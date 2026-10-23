package com.antcolonies.desert.colony;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.desert.DesertMod;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.desert.registry.DesertBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The night soil of a species with a hill (brain step 4b; made visible and cleared first thing each morning after the
 * user's bug of 2026-10-16: the counted heap under the entrance grew to 181 pellets that never reached the hill). Soil
 * dug while the surface is shut lies in piles of Loose Soil on the floors near the top of the nest
 * ({@link Nest#nightSoilCells}); once the surface opens the builders carry it up before they dig again, one carrier
 * to a pile (real Pogonomyrmex: the nest maintenance workers are the first out each morning, carrying out the soil dug
 * inside). While the surface is shut the diggers rest once more waits than their builders could carry out in a
 * morning ({@link #limit}). The piles in the world are the count: nothing of them is saved but the heap of older saves.
 */
public class NightSoil {
    /** How long (ticks) a carrier's claim on a pile lasts (it should have picked its pellet up by then). */
    private static final int CLAIM_TICKS = 400;

    private final Colony colony;
    /** The hill the piles go up onto. */
    private final Hill hill;
    /** Pellets counted under the entrance by older saves, laid out as piles once there is room (see {@link #layOutOldHeap}). */
    private final List<BlockState> oldHeap = new ArrayList<>();
    /** The pile each carrier is on its way to: ant -> pile. Not saved. */
    private final Map<UUID, Claim> carriers = new HashMap<>();

    private record Claim(BlockPos pile, long until) {
    }

    NightSoil(Colony colony, Hill hill) {
        this.colony = colony;
        this.hill = hill;
    }

    /** Where the night soil may lie ({@link Nest#nightSoilCells}, with Loose Soil as its heap). */
    public List<BlockPos> cells(Level level) {
        return colony.nest().nightSoilCells(level, DesertBlocks.LOOSE_SOIL.get());
    }

    private SpeciesProfile.NightSoilNumbers numbers() {
        return colony.profile().nest().nightSoil();
    }

    /** How many pellets wait in the piles now. */
    public int waiting(Level level) {
        int layers = 0;
        for (BlockPos cell : cells(level)) {
            BlockState state = level.getBlockState(cell);
            if (state.is(DesertBlocks.LOOSE_SOIL.get())) {
                layers += state.getValue(SeedPileBlock.LAYERS);
            }
        }
        return layers + oldHeap.size();
    }

    /**
     * The most that may wait: what the builders can carry out in a morning ({@code per_builder} for each real builder,
     * at least one), and never more than the cells hold.
     */
    public int limit(ServerLevel level) {
        int room = cells(level).size() * numbers().layersPerCell();
        return Math.min(room, numbers().perBuilder() * Math.max(1, colony.buildersNow(level)));
    }

    /** Whether the piles are as big as they may get: the diggers rest until the surface opens. */
    public boolean isFull(ServerLevel level) {
        return waiting(level) >= limit(level);
    }

    /**
     * Where a digger puts down a pellet while the surface is shut: the pile with the fewest layers (nearest the shaft
     * first), or null if the piles are full (the pellet is then packed into the walls).
     */
    @Nullable
    public BlockPos pickPile(ServerLevel level) {
        layOutOldHeap(level);
        if (isFull(level)) {
            return null;
        }
        BlockPos best = null;
        int fewest = Integer.MAX_VALUE;
        for (BlockPos cell : cells(level)) {
            int layers = layersAt(level, cell);
            if (layers < numbers().layersPerCell() && layers < fewest) {
                best = cell;
                fewest = layers;
            }
        }
        return best;
    }

    /** One more layer on this pile. False if it may not take one (full, or no longer a pile cell). */
    public boolean addLayer(ServerLevel level, BlockPos cell) {
        if (!cells(level).contains(cell) || isFull(level)) {
            return false;
        }
        int layers = layersAt(level, cell);
        if (layers >= numbers().layersPerCell()) {
            return false;
        }
        level.setBlock(cell, DesertBlocks.LOOSE_SOIL.get().defaultBlockState().setValue(SeedPileBlock.LAYERS, layers + 1), 3);
        return true;
    }

    /** A pile no other carrier is on its way to, claimed for this ant; null if there is none. */
    @Nullable
    public BlockPos claimPile(ServerLevel level, UUID ant) {
        layOutOldHeap(level);
        BlockPos pile = freePile(level, ant);
        if (pile != null) {
            carriers.put(ant, new Claim(pile, level.getGameTime() + CLAIM_TICKS));
        }
        return pile;
    }

    /** Whether some pile waits that no other carrier is on its way to (a builder then carries before it digs). */
    public boolean hasFreePile(ServerLevel level, @Nullable UUID ant) {
        return freePile(level, ant) != null;
    }

    @Nullable
    private BlockPos freePile(ServerLevel level, @Nullable UUID ant) {
        long now = level.getGameTime();
        carriers.values().removeIf(claim -> claim.until <= now);
        List<BlockPos> piles = new ArrayList<>();
        for (BlockPos cell : cells(level)) {
            if (layersAt(level, cell) > 0 && carriers.entrySet().stream()
                    .noneMatch(entry -> !entry.getKey().equals(ant) && entry.getValue().pile.equals(cell))) {
                piles.add(cell);
            }
        }
        // The biggest pile first.
        return piles.stream().max(Comparator.comparingInt(cell -> layersAt(level, cell))).orElse(null);
    }

    /** One pellet picked off this pile by its carrier: the pellet (plain dirt), or null if the pile is gone. */
    @Nullable
    public BlockState takeLayer(ServerLevel level, BlockPos pile, UUID ant) {
        carriers.remove(ant);
        int layers = layersAt(level, pile);
        if (layers <= 0) {
            return null;
        }
        level.setBlock(pile, layers <= 1 ? Blocks.AIR.defaultBlockState()
                : DesertBlocks.LOOSE_SOIL.get().defaultBlockState().setValue(SeedPileBlock.LAYERS, layers - 1), 3);
        return Blocks.DIRT.defaultBlockState();
    }

    /** The carrier gave up: its pile is free for others. */
    public void release(UUID ant) {
        carriers.remove(ant);
    }

    /** The layers of loose soil in this cell (0 if none). */
    public static int layersAt(Level level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.is(DesertBlocks.LOOSE_SOIL.get()) ? state.getValue(SeedPileBlock.LAYERS) : 0;
    }

    /**
     * The counted heap of an older save is laid out as piles as far as they may grow; the rest is packed into the walls
     * (as with soil dug while the piles are full).
     */
    private void layOutOldHeap(ServerLevel level) {
        if (oldHeap.isEmpty() || cells(level).isEmpty()) {
            return;
        }
        int before = oldHeap.size();
        List<BlockState> heap = new ArrayList<>(oldHeap);
        oldHeap.clear();
        int laid = 0;
        for (int i = 0; i < heap.size(); i++) {
            BlockPos pile = pickPile(level);
            if (pile == null || !addLayer(level, pile)) {
                break;
            }
            laid++;
        }
        DesertMod.LOGGER.info("[night-soil] colony {}: the old heap of {} pellets laid out as {} in piles, the rest packed into the walls",
                colony.getId(), before, laid);
        ColonyManager.get(level).markChanged();
    }

    /**
     * Every 20 seconds: once the hill is finished (no room on it) the piles no longer go up; they are packed into the
     * walls, as the soil dug once the hill is finished is (otherwise they would lie in the way for good).
     */
    void tick(ServerLevel level) {
        if (level.getGameTime() % 400 != 0 || hill.hasMoundRoom(level)) {
            return;
        }
        int packed = 0;
        for (BlockPos cell : cells(level)) {
            packed += layersAt(level, cell);
            if (layersAt(level, cell) > 0) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        packed += oldHeap.size();
        oldHeap.clear();
        if (packed > 0) {
            carriers.clear();
            DesertMod.LOGGER.info("[night-soil] colony {}: the hill is finished, {} pellets of night soil packed into the walls",
                    colony.getId(), packed);
            ColonyManager.get(level).markChanged();
        }
    }

    /** Tests only: takes every pile away. */
    public void clearForTests(Level level) {
        oldHeap.clear();
        for (BlockPos cell : cells(level)) {
            if (layersAt(level, cell) > 0) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayKit.everyPartSavesAndLoads}). */
    public void fillForTests(ServerLevel level) {
        oldHeap.add(Blocks.DIRT.defaultBlockState());
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockState pellet : oldHeap) {
            list.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(pellet.getBlock()).toString()));
        }
        tag.put("nightSoil", list);
    }

    void load(CompoundTag tag) {
        oldHeap.clear();
        for (Tag t : tag.getList("nightSoil", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                oldHeap.add(BuiltInRegistries.BLOCK.get(id).defaultBlockState());
            }
        }
    }
}
