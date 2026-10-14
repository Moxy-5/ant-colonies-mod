package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.EatenInStages;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The colony's raw food: what the workers fetch from the ground and cut off plants around the nest, kept as heaps in
 * the food store, and taken from there to be milled or eaten. What the food is comes from the species profile
 * ({@code colony.profile().food()}): seeds and grass tufts for the harvester ant (a seed store), dead plant material
 * for termites (forage). Counts how many pieces (and rich seeds) are stored. The methods and fields still say "seed"
 * and "grass", the harvester ant's food: read them as "a piece of food" and "a plant".
 */
public class FoodStore {
    // How far the workers look for food, what they gather and what one plant gives come from the species profile:
    // colony.profile().food().

    /** An ant that went for a seed keeps the others away from it this long (in ticks). */
    private static final int SEED_CLAIM_TICKS = 600;
    /** A plant only one ant at a time cuts at is left alone this long after a cut (in ticks), so the ants don't all crowd one tuft. */
    private static final int GRASS_REST_TICKS = 200;
    /** The plants around the nest are looked up again after this many ticks (a scan of the whole area). */
    private static final int GRASS_SCAN_TICKS = 100;
    /** How far above or below the nest rich food (a fallen tree, a hay bale) is still seen (other plants: 4 up, 1 down). */
    private static final int RICH_HEIGHT = 12;

    private final Colony colony;
    private final BlockPos center;

    /** Plants that rest after a cut: position -> game time until which nobody cuts at them. Not saved. */
    private final Claims<BlockPos> busyGrass = new Claims<>();
    /** Ants on their way to a plant or cutting at it: ant -> the plant and the game time when the claim runs out. Not saved. */
    private final Map<UUID, PlantClaim> plantClaims = new HashMap<>();
    /** The food plants found at the last scan, and when the next scan is due. Not saved. */
    private final List<BlockPos> grassTufts = new ArrayList<>();
    private long grassScanTime;
    /** How many pieces have been cut off each plant so far (only plants that were cut at least once). Saved. */
    private final Map<BlockPos, Integer> grassClips = new HashMap<>();

    private record PlantClaim(BlockPos plant, long until) {
    }

    /** How much food the colony has stored (one per piece in the food store). Saved. */
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

    /** Pieces in the store were used up where they lie (the fungus garden's comb turned into nodules): the count goes down. */
    void used(int pieces) {
        food = Math.max(0, food - pieces);
        richSeeds = Math.min(richSeeds, food);
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
        SeedPileBlock pile = food().pileBlock();
        for (BlockPos pos : colony.nest().getFoodStoreCells(level)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                if (!young.contains(pos)) {
                    empty.add(pos);
                }
            } else if (state.is(pile) && state.getValue(SeedPileBlock.LAYERS) < pile.maxLayers() && !young.contains(pos)) {
                growing.add(pos); // (never under an egg or a nymph lying on a fungus comb)
            }
        }
        // Fungus comb is always built onto comb that has room first (the fungus only grows where the comb is thick enough).
        boolean growFirst = pile instanceof com.antcolonies.block.FungusCombBlock || random.nextFloat() < 0.75F;
        List<BlockPos> from = !growing.isEmpty() && (empty.isEmpty() || growFirst) ? growing : empty;
        return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
    }

    /** The food numbers of this colony's species: what it gathers and where it keeps it. */
    private SpeciesProfile.FoodNumbers food() {
        return colony.profile().food();
    }

    /**
     * Whether it is worth fetching a seed from outside: the food store has room for it, or bread still has to be milled
     * (the ant then brings the seed straight to the mill, so nothing is put down without a store).
     */
    public boolean wantsSeeds(ServerLevel level) {
        return pickFoodSpot(level, level.getRandom()) != null || colony.bakery().needsBread(level);
    }

    /**
     * The seed (the species' food item) on the ground nearest to {@code near} that the ants should fetch, or null: only once the food store
     * has room (or the queen wants seeds), only on the surface (not what is already stored below), within the profile's food radius of the entrance,
     * and not one another ant is on its way to. {@code avoid}: item entity ids this ant could not reach.
     */
    @Nullable
    public ItemEntity findSeed(Level level, BlockPos near, Set<Integer> avoid) {
        if (!(level instanceof ServerLevel serverLevel) || !wantsSeeds(serverLevel)) {
            return null; // no food store yet (and the queen wants nothing), or it is full
        }
        claimedSeeds.prune(level.getGameTime());
        int radius = food().radius();
        var items = food().itemTag();
        AABB area = new AABB(center).inflate(radius, 0, radius).expandTowards(0, 8, 0);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (!item.isAlive() || !item.getItem().is(items) || item.getY() < center.getY() - 0.5
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

    /** This ant is on its way to this item: no other ant goes for it for a while. */
    public void claimSeed(Level level, ItemEntity seed, UUID ant) {
        claimedSeeds.put(seed.getId(), level.getGameTime() + SEED_CLAIM_TICKS);
        colony.trails().visited(level, seed.blockPosition(), ant);
    }

    public void releaseSeed(ItemEntity seed) {
        claimedSeeds.remove(seed.getId());
    }

    /**
     * A food plant near the nest (the profile's {@code plants}: a tuft of short grass for the harvester ant, dead bushes,
     * dry grass, dying leaves and hay bales for termites) that an ant could cut a piece off, or null: only once the food
     * store is ready and not full. Only plants the colony knows of, or this ant found itself (see {@link FoodMap}; a
     * species without a find radius knows them all). The plant worth the most is taken ({@link #worth}: how much food
     * is left on it against how far it is from the way out), so a hay bale beats a tuft of grass nearer by. A plant
     * with as many ants on their way to it as may cut at it at once, or one resting after a cut, is skipped, and so is
     * one in {@code avoid} (this ant found no way to it).
     */
    @Nullable
    public BlockPos findGrass(Level level, BlockPos near, Set<BlockPos> avoid, @Nullable UUID ant) {
        if (!(level instanceof ServerLevel serverLevel) || !wantsSeeds(serverLevel)) {
            return null;
        }
        long now = level.getGameTime();
        busyGrass.prune(now);
        plantClaims.values().removeIf(claim -> claim.until() <= now);
        List<BlockPos> exits = colony.foragingHoles().waysOut(level).stream().map(ForagingHoles.WayOut::mouth).toList();
        BlockPos best = null;
        double bestWorth = -1;
        for (BlockPos pos : plants(level)) {
            SpeciesProfile.PlantFood plant = food().plantFor(level.getBlockState(pos));
            if (plant == null || busyGrass.contains(pos) || avoid.contains(pos) || foragersAt(pos) >= Math.max(1, plant.foragers())
                    || !colony.foodMap().knows(pos, ant)) {
                continue;
            }
            double worth = worth(plant.pieces() - getPiecesCut(pos), tripLength(pos, exits.isEmpty() ? List.of(center) : exits), pos.distSqr(near));
            if (worth > bestWorth) {
                best = pos;
                bestWorth = worth;
            }
        }
        return best;
    }

    /**
     * What a plant is worth going for: the food left on it (to the power 0.75: twice the food is worth a bit less than
     * twice the walk) against the walk from the way out, plus a few blocks every trip costs anyway. A full hay bale (48
     * pieces) is worth going about 28 blocks for rather than a tuft of grass (4 pieces) right by the hole, so within a
     * colony's food radius a hay bale always draws them; a hay bale nearly used up is worth no more than grass. On a tie
     * the plant nearer the ant ({@code fromAnt}, squared) wins. My pick.
     */
    static double worth(int piecesLeft, double trip, double fromAnt) {
        return Math.pow(Math.max(1, piecesLeft), 0.75) / (trip + 4.0) - fromAnt * 1.0E-7;
    }

    /** How far (blocks, sideways) the plant is from the nearest of these ways out. */
    private static double tripLength(BlockPos plant, List<BlockPos> exits) {
        double best = Double.MAX_VALUE;
        for (BlockPos exit : exits) {
            double dx = exit.getX() - plant.getX();
            double dz = exit.getZ() - plant.getZ();
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    /**
     * The food plants within the profile's food radius of the entrance (scanned again at most every few seconds: the top
     * block of each column, and the block under it for solid plants like a hay bale). The list may hold plants that are
     * gone since the last scan.
     */
    public List<BlockPos> plants(Level level) {
        long now = level.getGameTime();
        if (now >= grassScanTime || now + GRASS_SCAN_TICKS < grassScanTime) {
            grassScanTime = now + GRASS_SCAN_TICKS;
            grassTufts.clear();
            int radius = colony.foragingRadius();
            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
                        continue;
                    }
                    BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                    for (BlockPos pos : List.of(top, top.below())) {
                        SpeciesProfile.PlantFood plant = food().plantFor(level.getBlockState(pos));
                        int dy = pos.getY() - center.getY();
                        // Rich food (a fallen tree, a hay bale) counts up or down a slope too (the user's fallen tree on a
                        // terrace above the nest was never seen, 2026-10-14); other plants only near the nest's height.
                        if (plant != null && (dy >= -1 && dy <= 4 || plant.rich() && Math.abs(dy) <= RICH_HEIGHT)) {
                            grassTufts.add(pos);
                            break;
                        }
                    }
                }
            }
            // Forget the cuts of plants that are gone (eaten by something else, broken by a player), so a new tuft
            // growing in the same spot starts whole.
            grassClips.keySet().removeIf(pos -> level.hasChunkAt(pos) && food().plantFor(level.getBlockState(pos)) == null);
        }
        return grassTufts;
    }

    /** The food plants round the nest are looked for again on the next ask (a test that has just put one down). */
    public void rescanPlantsSoon() {
        grassScanTime = Long.MIN_VALUE;
    }

    /** How many ants are on their way to this plant or cutting at it. */
    private int foragersAt(BlockPos plant) {
        int count = 0;
        for (PlantClaim claim : plantClaims.values()) {
            if (claim.plant().equals(plant)) {
                count++;
            }
        }
        return count;
    }

    /** Whether this block is one of the plants the colony cuts food off. */
    public boolean isFoodPlant(BlockState state) {
        return food().plantFor(state) != null;
    }

    /** This ant is on its way to this plant: no more ants than may cut at it at once go there for a while. */
    public void claimGrass(Level level, BlockPos grass, UUID ant) {
        plantClaims.put(ant, new PlantClaim(grass.immutable(), level.getGameTime() + SEED_CLAIM_TICKS));
        colony.trails().visited(level, grass, ant);
    }

    /** The ant gave up on its plant, or is done with it. */
    public void releaseGrass(UUID ant) {
        plantClaims.remove(ant);
    }

    /**
     * The ant cut a piece off the plant and is done with it. A plant that takes one ant at a time rests for a moment;
     * after the profile's pieces for that plant it is used up and breaks (without dropping anything).
     */
    public void clippedGrass(Level level, BlockPos grass, UUID ant) {
        BlockPos pos = grass.immutable();
        plantClaims.remove(ant);
        colony.trails().visited(level, pos, ant);
        BlockState before = level.getBlockState(pos);
        SpeciesProfile.PlantFood plant = food().plantFor(before);
        if (plant != null && before.getBlock() instanceof EatenInStages eaten) {
            grassClips.putIfAbsent(pos, eaten.eatenAlready(before, plant.pieces())); // already gnawed by someone else
        }
        int clips = grassClips.merge(pos, 1, Integer::sum);
        if (plant != null && clips < plant.pieces() && before.getBlock() instanceof EatenInStages eaten) {
            BlockState after = eaten.eatenTo(before, clips, plant.pieces());
            if (after != before) {
                level.setBlock(pos, after, 3); // it looks more eaten
            }
        }
        if (plant == null || clips >= plant.pieces()) {
            grassClips.remove(pos);
            busyGrass.remove(pos);
            BlockState state = level.getBlockState(pos);
            if (plant != null) {
                SoundType sound = state.getSoundType();
                level.playSound(null, pos, sound.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, sound.getPitch() * 1.2F);
                level.destroyBlock(pos, false);
            }
        } else if (plant.foragers() <= 1) {
            busyGrass.put(pos, level.getGameTime() + GRASS_REST_TICKS);
        }
    }

    /** How many pieces have been cut off the plant at this spot so far (0 once it is used up and gone). For tests. */
    public int getPiecesCut(BlockPos plant) {
        return grassClips.getOrDefault(plant, 0);
    }

    /** How many pieces this plant gives in all (0 if it is not one of the colony's food plants). For tests. */
    public int piecesOf(BlockState state) {
        SpeciesProfile.PlantFood plant = food().plantFor(state);
        return plant == null ? 0 : plant.pieces();
    }

    /** What one piece cut off a plant is: a wheat seed for the harvester ant, forage for termites. */
    public ItemStack cutPiece() {
        return new ItemStack(food().pieceItem());
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
        SeedPileBlock pile = food().pileBlock();
        if (colony.brood().getBroodCells(level).contains(spot)) {
            return false; // an egg or a young one was put there meanwhile: the heap would bury it
        }
        if (state.isAir()) {
            level.setBlock(spot, pile.defaultBlockState(), 3);
        } else if (state.is(pile) && state.getValue(SeedPileBlock.LAYERS) < pile.maxLayers()) {
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
        SeedPileBlock pile = food().pileBlock();
        for (BlockPos pos : colony.nest().getFoodStoreCells(level)) {
            if (level.getBlockState(pos).is(pile)) {
                piles.add(pos);
            }
        }
        return piles.isEmpty() ? null : piles.get(random.nextInt(piles.size()));
    }

    /**
     * A worker takes one seed off the heap at {@code pile}: the heap gets a layer lower and the colony counts one food
     * less. Returns the seed (a pumpkin seed if it is a rich one, else the species' piece: a wheat seed), or an empty stack if the heap is
     * gone. A rich seed is taken if {@code wantRich} and the colony has one (or if every seed in the store is a rich one).
     */
    public ItemStack takeSeedAt(Level level, BlockPos pile, UUID ant, boolean wantRich) {
        BlockState state = level.getBlockState(pile);
        if (!state.is(food().pileBlock())) {
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
        return rich ? new ItemStack(Items.PUMPKIN_SEEDS) : cutPiece();
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
