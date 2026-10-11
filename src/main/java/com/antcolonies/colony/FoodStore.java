package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The colony's raw food: seeds fetched from the ground and clipped from grass around the nest, kept as heaps in the
 * seed store, and taken from there to be milled or eaten. Counts how many seeds (and rich seeds) are stored.
 */
public class FoodStore {
    // How far the workers look for food and how many seeds a tuft of grass gives come from the species profile:
    // colony.profile().food().

    /** An ant that went for a seed keeps the others away from it this long (in ticks). */
    private static final int SEED_CLAIM_TICKS = 600;
    /** A tuft of grass an ant just clipped is left alone this long (in ticks), so the ants don't all crowd one tuft. */
    private static final int GRASS_REST_TICKS = 200;
    /** The tufts of grass around the nest are looked up again after this many ticks (a scan of the whole area). */
    private static final int GRASS_SCAN_TICKS = 100;

    private final Colony colony;
    private final BlockPos center;

    /** Grass tufts an ant is on its way to, or just clipped: position -> game time until which the others leave it alone. Not saved. */
    private final Claims<BlockPos> busyGrass = new Claims<>();
    /** The tufts of short grass found at the last scan, and when the next scan is due. Not saved. */
    private final List<BlockPos> grassTufts = new ArrayList<>();
    private long grassScanTime;
    /** How many seeds have been clipped from each tuft of grass so far (only tufts that were clipped at least once). Saved. */
    private final Map<BlockPos, Integer> grassClips = new HashMap<>();

    /** How much food the colony has stored (one per seed in the food store). Saved. */
    private int food;
    /** How many of the seeds in the food store are rich ones (never more than {@link #food}). Saved. */
    private int richSeeds;
    /** How many seeds the workers have eaten in all. Saved. */
    private int seedsEaten;
    /** Seeds an ant is on its way to: item entity id -> game time when the claim runs out. Not saved. */
    private final Claims<Integer> claimedSeeds = new Claims<>();

    FoodStore(Colony colony) {
        this.colony = colony;
        this.center = colony.getCenter();
    }

    public int getFood() {
        return food;
    }

    public int getRichSeeds() {
        return Math.min(richSeeds, food);
    }

    public int getSeedsEaten() {
        return seedsEaten;
    }

    /**
     * A spot on the floor of the food store that can take one more seed, or null if the store is not ready or full.
     * Most of the time it is a heap that already exists (so the heaps grow, not a thin layer everywhere).
     */
    @Nullable
    public BlockPos pickFoodSpot(Level level, RandomSource random) {
        List<BlockPos> growing = new ArrayList<>();
        List<BlockPos> empty = new ArrayList<>();
        Set<BlockPos> young = colony.brood().getBroodCells(level); // eggs and larvae may lie in the seed store where it is the queen's home (no nursery)
        for (BlockPos pos : colony.nest().getFoodStoreCells(level)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                if (!young.contains(pos)) {
                    empty.add(pos);
                }
            } else if (state.is(ModBlocks.SEED_PILE.get()) && state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS) {
                growing.add(pos);
            }
        }
        List<BlockPos> from = !growing.isEmpty() && (empty.isEmpty() || random.nextFloat() < 0.75F) ? growing : empty;
        return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
    }

    /**
     * Whether it is worth fetching a seed from outside: the food store has room for it, or bread still has to be milled
     * (the ant then brings the seed straight to the mill, so nothing is put down without a store).
     */
    public boolean wantsSeeds(ServerLevel level) {
        return pickFoodSpot(level, level.getRandom()) != null || colony.bakery().needsBread(level);
    }

    /**
     * The seed on the ground nearest to {@code near} that the ants should fetch, or null: only once the food store
     * has room (or the queen wants seeds), only on the surface (not what is already stored below), within the profile's food radius of the entrance,
     * and not one another ant is on its way to. {@code avoid}: item entity ids this ant could not reach.
     */
    @Nullable
    public ItemEntity findSeed(Level level, BlockPos near, Set<Integer> avoid) {
        if (!(level instanceof ServerLevel serverLevel) || !wantsSeeds(serverLevel)) {
            return null; // no food store yet (and the queen wants nothing), or it is full
        }
        claimedSeeds.prune(level.getGameTime());
        int radius = colony.profile().food().radius();
        AABB area = new AABB(center).inflate(radius, 0, radius).expandTowards(0, 8, 0);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (!item.isAlive() || !item.getItem().is(ModTags.SEEDS) || item.getY() < center.getY() - 0.5
                    || claimedSeeds.contains(item.getId()) || avoid.contains(item.getId())) {
                continue;
            }
            double distance = item.distanceToSqr(near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5);
            if (distance < bestDistance) {
                best = item;
                bestDistance = distance;
            }
        }
        return best;
    }

    public void claimSeed(Level level, ItemEntity seed) {
        claimedSeeds.put(seed.getId(), level.getGameTime() + SEED_CLAIM_TICKS);
    }

    public void releaseSeed(ItemEntity seed) {
        claimedSeeds.remove(seed.getId());
    }

    /**
     * A tuft of short grass near the nest that an ant could clip seeds from, or null: only once the food store is
     * ready and not full. Scans the area within the profile's food radius of the entrance (at most every few seconds) and takes
     * the tuft nearest to {@code near}. A tuft another ant is on its way to, or clipped lately, is skipped, and so is one in
     * {@code avoid} (this ant found no way to it).
     */
    @Nullable
    public BlockPos findGrass(Level level, BlockPos near, Set<BlockPos> avoid) {
        if (!(level instanceof ServerLevel serverLevel) || !wantsSeeds(serverLevel)) {
            return null;
        }
        long now = level.getGameTime();
        busyGrass.prune(now);
        if (now >= grassScanTime) {
            grassScanTime = now + GRASS_SCAN_TICKS;
            grassTufts.clear();
            int radius = colony.profile().food().radius();
            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                    if (pos.getY() >= center.getY() - 1 && pos.getY() <= center.getY() + 4 && level.getBlockState(pos).is(Blocks.SHORT_GRASS)) {
                        grassTufts.add(pos);
                    }
                }
            }
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : grassTufts) {
            if (busyGrass.contains(pos) || avoid.contains(pos) || !level.getBlockState(pos).is(Blocks.SHORT_GRASS)) {
                continue;
            }
            double distance = pos.distSqr(near);
            if (distance < bestDistance) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** An ant is on its way to this tuft of grass: the others look elsewhere for a while. */
    public void claimGrass(Level level, BlockPos grass) {
        busyGrass.put(grass.immutable(), level.getGameTime() + SEED_CLAIM_TICKS);
    }

    /** The ant gave up on this tuft. */
    public void releaseGrass(BlockPos grass) {
        busyGrass.remove(grass);
    }

    /**
     * The ant clipped its seed. The tuft rests for a moment; after the profile's seeds per tuft it is used up and
     * breaks (without dropping anything).
     */
    public void clippedGrass(Level level, BlockPos grass) {
        BlockPos pos = grass.immutable();
        int clips = grassClips.merge(pos, 1, Integer::sum);
        if (clips >= colony.profile().food().seedsPerGrass()) {
            grassClips.remove(pos);
            busyGrass.remove(pos);
            level.destroyBlock(pos, false);
        } else {
            busyGrass.put(pos, level.getGameTime() + GRASS_REST_TICKS);
        }
    }

    /**
     * An ant puts its seed down at {@code spot} in the food store: the heap there grows by one layer (or a new heap
     * starts) and the colony counts one more food. False if that spot is full (the ant looks for another).
     */
    public boolean depositSeed(Level level, BlockPos spot) {
        return depositSeed(level, spot, false);
    }

    /** As above; {@code rich}: the seed is a rich one (pumpkin), which the mill keeps for the queen's rich bread. */
    public boolean depositSeed(Level level, BlockPos spot, boolean rich) {
        BlockState state = level.getBlockState(spot);
        if (state.isAir()) {
            level.setBlock(spot, ModBlocks.SEED_PILE.get().defaultBlockState(), 3);
        } else if (state.is(ModBlocks.SEED_PILE.get()) && state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS) {
            level.setBlock(spot, state.setValue(SeedPileBlock.LAYERS, state.getValue(SeedPileBlock.LAYERS) + 1), 3);
        } else {
            return false;
        }
        food++;
        if (rich) {
            richSeeds++;
        }
        return true;
    }

    /** A heap of seeds in the food store to take one seed from, or null if there is none. */
    @Nullable
    public BlockPos findSeedPile(Level level, RandomSource random) {
        List<BlockPos> piles = new ArrayList<>();
        for (BlockPos pos : colony.nest().getFoodStoreCells(level)) {
            if (level.getBlockState(pos).is(ModBlocks.SEED_PILE.get())) {
                piles.add(pos);
            }
        }
        return piles.isEmpty() ? null : piles.get(random.nextInt(piles.size()));
    }

    /**
     * A worker takes one seed off the heap at {@code pile}: the heap gets a layer lower and the colony counts one food
     * less. Returns the seed (a pumpkin seed if it is a rich one, else a wheat seed), or an empty stack if the heap is
     * gone. A rich seed is taken if {@code wantRich} and the colony has one (or if every seed in the store is a rich one).
     */
    public ItemStack takeSeedAt(Level level, BlockPos pile, UUID ant, boolean wantRich) {
        BlockState state = level.getBlockState(pile);
        if (!state.is(ModBlocks.SEED_PILE.get())) {
            return ItemStack.EMPTY;
        }
        int layers = state.getValue(SeedPileBlock.LAYERS);
        level.setBlock(pile, layers <= 1 ? Blocks.AIR.defaultBlockState() : state.setValue(SeedPileBlock.LAYERS, layers - 1), 3);
        richSeeds = Math.min(richSeeds, food);
        boolean rich = richSeeds > 0 && (wantRich || food <= richSeeds);
        food = Math.max(0, food - 1);
        if (rich) {
            richSeeds--;
            colony.bakery().addRichMiller(ant);
        }
        return new ItemStack(rich ? Items.PUMPKIN_SEEDS : Items.WHEAT_SEEDS);
    }

    /**
     * A hungry worker takes one raw seed off the heap at {@code pile} to eat it: plain seeds first (a rich one only if
     * nothing else is left). Returns the seed, or an empty stack if the heap is gone.
     */
    public ItemStack eatSeedAt(Level level, BlockPos pile, UUID ant) {
        ItemStack seed = takeSeedAt(level, pile, ant, false);
        colony.bakery().removeRichMiller(ant); // eaten, not milled
        if (!seed.isEmpty()) {
            seedsEaten++;
        }
        return seed;
    }

    void save(CompoundTag tag) {
        tag.putInt("food", food);
        tag.putInt("seedsEaten", seedsEaten);
        tag.putInt("richSeeds", richSeeds);
        CompoundTag clips = new CompoundTag();
        grassClips.forEach((pos, count) -> clips.putInt(Long.toString(pos.asLong()), count));
        tag.put("grassClips", clips);
    }

    void load(CompoundTag tag) {
        food = tag.getInt("food");
        seedsEaten = tag.getInt("seedsEaten");
        richSeeds = tag.getInt("richSeeds");
        CompoundTag clips = tag.getCompound("grassClips");
        for (String key : clips.getAllKeys()) {
            grassClips.put(BlockPos.of(Long.parseLong(key)), clips.getInt(key));
        }
    }
}
