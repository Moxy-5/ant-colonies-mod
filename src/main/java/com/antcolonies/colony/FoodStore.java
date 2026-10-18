package com.antcolonies.colony;

import com.antcolonies.block.FungusCombBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.EatenInStages;
import com.antcolonies.block.MudCastBlock;
import com.antcolonies.block.MudCrustBlock;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.registry.ModBlocks;
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
    /** How long a place in the food store is kept for a seed on its way in (2 minutes, my pick; a long trip home fits). */
    private static final int STORE_PLACE_TICKS = 2400;
    /** A plant only one ant at a time cuts at is left alone this long after a cut (in ticks), so the ants don't all crowd one tuft. */
    private static final int GRASS_REST_TICKS = 200;
    /** The plants around the nest are looked up again after this many ticks (a scan of the whole area). */
    private static final int GRASS_SCAN_TICKS = 100;
    /** How far above or below the nest rich food (a fallen tree, a hay bale) is still seen (other plants: 4 up, 1 down). */
    private static final int RICH_HEIGHT = 24;

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
    /** Whether the store has ever held a piece (a young termite colony's workers only get hungry once it has). Saved. */
    private boolean hadFood;
    /** How many more layers the store's heaps could take the last second the nest was loaded (see {@link #watchRoom}). Saved. */
    private int seenRoom;
    /** Tests only: pretend the store has this much room (null: as seen). Not saved. */
    @org.jetbrains.annotations.Nullable
    private Integer roomForTests;

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
    /**
     * Ants on their way in with a seed for the food store, each holding a place there (so two foragers never count on
     * the same last place, {@code docs/BUGS.md} 44). Not saved.
     */
    private final Claims<UUID> storeBound = new Claims<>();
    /** Eaten grass growing back, and the bare patches round the holes. Saved. */
    private final BarePatches barePatches;

    FoodStore(Colony colony) {
        this.colony = colony;
        this.center = colony.getCenter();
        this.barePatches = new BarePatches(colony);
    }

    /** Eaten grass growing back, and the bare patches round the holes. */
    public BarePatches barePatches() {
        return barePatches;
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

    /** Seeds an ant carried but found no place for and dropped (not saved; for the tests, {@code docs/BUGS.md} 44). */
    private int seedsDropped;

    public void seedDropped() {
        seedsDropped++;
    }

    public int getSeedsDroppedForTests() {
        return seedsDropped;
    }

    /** Whether the store has ever held a piece of food (for a termite colony: its first comb). */
    public boolean hasHadFood() {
        return hadFood || food > 0;
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
        boolean growFirst = pile instanceof FungusCombBlock || random.nextFloat() < 0.75F;
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
        return hasStoreRoom(level) || colony.bakery().needsBread(level);
    }

    /** Whether the food store has a place left that no seed on its way in is holding. */
    public boolean hasStoreRoom(Level level) {
        storeBound.prune(level.getGameTime());
        return roomInStore(level) > storeBound.size();
    }

    /**
     * A forager about to take a seed outside asks for a place in the food store: true (and the place is held for it
     * until it puts the seed down, drops it, or 2 minutes pass) if there is one left; an ant holding one keeps it.
     */
    public boolean claimStorePlace(Level level, UUID ant) {
        storeBound.prune(level.getGameTime());
        if (!storeBound.contains(ant) && roomInStore(level) <= storeBound.size()) {
            return false;
        }
        storeBound.put(ant, level.getGameTime() + STORE_PLACE_TICKS);
        return true;
    }

    /** The ant put its seed down (or lost it): its place in the store is free again. */
    public void releaseStorePlace(UUID ant) {
        storeBound.remove(ant);
    }

    /** How many more seeds the food store takes right now: the empty layers of its heaps and its empty cells. */
    private int roomInStore(Level level) {
        Set<BlockPos> young = colony.brood().getBroodCells(level);
        SeedPileBlock pile = food().pileBlock();
        int room = 0;
        for (BlockPos pos : colony.nest().getFoodStoreCells(level)) {
            if (young.contains(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                room += pile.maxLayers();
            } else if (state.is(pile)) {
                room += Math.max(0, pile.maxLayers() - state.getValue(SeedPileBlock.LAYERS));
            }
        }
        return room;
    }

    /**
     * The seed (the species' food item) on the ground nearest to {@code near} that the ants should fetch, or null: only once the food store
     * has room (or the queen wants seeds), only on the surface (not what is already stored below), within the profile's food radius of the entrance,
     * and not one another ant is on its way to. {@code avoid}: item entity ids this ant could not reach. A species that
     * searches alone ({@link FoodMap#searchesAlone}) only takes a seed the ant has come across: within the find radius
     * of {@code near} (real foragers bump into seeds; nobody tells them where one lies).
     */
    @Nullable
    public ItemEntity findSeed(Level level, BlockPos near, Set<Integer> avoid) {
        if (!(level instanceof ServerLevel serverLevel) || !wantsSeeds(serverLevel)) {
            return null; // no food store yet (and the queen wants nothing), or it is full
        }
        claimedSeeds.prune(level.getGameTime());
        int radius = colony.searchReach(); // (out to where a searcher may get: the trails' tips and its drift)
        var items = food().itemTag();
        AABB area = new AABB(center).inflate(radius, 0, radius).expandTowards(0, 8, 0);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double feel = colony.foodMap().searchesAlone() ? food().findRadius() : Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (!item.isAlive() || !item.getItem().is(items) || item.getY() < center.getY() - 0.5
                    || claimedSeeds.contains(item.getId()) || avoid.contains(item.getId())
                    || !colony.patrol().isOpen(level, item.blockPosition())) { // (only where the patrollers came back from)
                continue;
            }
            double distance = item.distanceToSqr(near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5);
            if (distance > feel * feel) {
                continue; // (searching alone: not come across yet)
            }
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
        return findGrass(level, near, avoid, ant, false);
    }

    /**
     * Like {@link #findGrass(Level, BlockPos, Set, UUID)}; {@code fromInside}: only food that can be eaten from inside
     * the nest (from a feeding gallery under it, see {@link #isFeedableFromInside}), for foragers by day.
     */
    @Nullable
    public BlockPos findGrass(Level level, BlockPos near, Set<BlockPos> avoid, @Nullable UUID ant, boolean fromInside) {
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
            // (a plant the numbers have used up out of sight still stands until it is out of every player's sight: skipped)
            if (plant == null || plant.pieces() - getPiecesCut(pos) <= 0 || busyGrass.contains(pos) || avoid.contains(pos) || foragersAt(pos) >= Math.max(1, plant.foragers())
                    || !colony.foodMap().knows(pos, ant) || fromInside && !isFeedableFromInside(level, pos)
                    || !fromInside && !colony.patrol().isOpen(level, pos)) { // (only where the patrollers came back from)
                continue;
            }
            double trip = tripLength(pos, exits.isEmpty() ? List.of(center) : exits);
            double byTrail = colony.trunkTrails().walkTo(pos); // (a cleared trail walks quicker: worth the walk)
            double worth = worth(plant.pieces() - getPiecesCut(pos), byTrail >= 0 ? Math.min(trip, byTrail) : trip, pos.distSqr(near));
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
     * The food plants the colony's ants can come across (scanned again at most every few seconds: the top block of each
     * column, or the block under it for solid plants like a hay bale, and the food stacked under that: a log under a
     * stump). The list may hold plants that are gone since the last scan. A colony without trunk trails scans the whole
     * square out to its search reach; one with trails (long trails: up to 96 blocks out, too many columns to scan) scans
     * the square out to its young reach plus the stretch, a band along each trail out past its tip, and the ground round
     * each of its real workers out farther (where a searcher off any trail is).
     */
    public List<BlockPos> plants(Level level) {
        long now = level.getGameTime();
        if (now >= grassScanTime || now + GRASS_SCAN_TICKS < grassScanTime) {
            grassScanTime = now + GRASS_SCAN_TICKS;
            grassTufts.clear();
            int radius = colony.searchReach(); // (out to where a searcher's drift may take it)
            int inner = Math.min(radius, food().radius() + food().search().map(SpeciesProfile.SearchNumbers::stretch).orElse(0));
            if (!colony.trunkTrails().keepsTrails()) {
                inner = radius;
            }
            for (int x = center.getX() - inner; x <= center.getX() + inner; x++) {
                for (int z = center.getZ() - inner; z <= center.getZ() + inner; z++) {
                    scanColumn(level, x, z, false);
                }
            }
            if (inner < radius) {
                Set<Long> done = new HashSet<>();
                int side = BAND + food().search().map(SpeciesProfile.SearchNumbers::wander).orElse(0);
                for (int[] column : colony.trunkTrails().columnsNear(side, radius)) {
                    scanOuter(level, center.getX() + column[0], center.getZ() + column[1], inner, done);
                }
                AABB out = new AABB(center).inflate(radius + 2, 16, radius + 2);
                for (com.antcolonies.entity.AbstractAntEntity ant : level.getEntitiesOfClass(com.antcolonies.entity.AbstractAntEntity.class, out,
                        ant -> ant.isAlive() && ant.isColonyWorker() && colony.getId().equals(ant.getColonyId()))) {
                    if (Math.abs(ant.getBlockX() - center.getX()) <= inner - side && Math.abs(ant.getBlockZ() - center.getZ()) <= inner - side) {
                        continue; // (all round it is scanned already)
                    }
                    for (int dx = -side; dx <= side; dx++) {
                        for (int dz = -side; dz <= side; dz++) {
                            scanOuter(level, ant.getBlockX() + dx, ant.getBlockZ() + dz, inner, done);
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

    /** A trail's band reaches this many blocks either side of its line (plus the searchers' wander). */
    private static final int BAND = 4;

    /** A column past the inner square (once only: {@code done}); within the square it was scanned already. */
    private void scanOuter(Level level, int x, int z, int inner, Set<Long> done) {
        if (Math.abs(x - center.getX()) <= inner && Math.abs(z - center.getZ()) <= inner
                || !done.add(((long) x << 32) ^ (z & 0xffffffffL))) {
            return;
        }
        scanColumn(level, x, z, true);
    }

    /**
     * The food in one column. {@code far}: out along a trail or by a worker far out, where the ground may be well above or
     * below the nest (long trails run over hills): any plant counts up to the rich food's height difference.
     */
    private void scanColumn(Level level, int x, int z, boolean far) {
        if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
            return;
        }
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
        // The first air above any block (a thin branch over a gallery dug under it is no surface for MOTION_BLOCKING),
        // and mud plastered over food (a branch's casing, a log's top) does not hide it.
        while (MudCrustBlock.isCrust(level.getBlockState(top.below()))) {
            top = top.below();
        }
        boolean found = false;
        for (int down = 0; down < (found ? STACK_DEPTH : 2); down++) {
            BlockPos pos = top.below(down);
            SpeciesProfile.PlantFood plant = food().plantFor(level.getBlockState(pos));
            int dy = pos.getY() - center.getY();
            // Rich food (a fallen tree, a hay bale) counts up or down a slope too (the user's fallen tree on a
            // terrace above the nest was never seen, 2026-10-14); other plants only near the nest's height.
            if (plant != null && (dy >= -1 && dy <= 4 || (plant.rich() || far) && Math.abs(dy) <= RICH_HEIGHT)) {
                grassTufts.add(pos);
                if (!plant.rich()) {
                    break; // one plant a column (the two halves of tall grass are one plant)
                }
                found = true;
            } else if (found) {
                break;
            }
        }
    }

    /** Food stacked under the top food block of a column (a stump on a log, a stump 2 blocks tall) is seen this deep. */
    private static final int STACK_DEPTH = 4;

    /** The food plants round the nest are looked for again on the next ask (a test that has just put one down). */
    public void rescanPlantsSoon() {
        grassScanTime = Long.MIN_VALUE;
    }

    /** How many ants are on their way to this plant or cutting at it. */
    int foragersAt(BlockPos plant) {
        int count = 0;
        for (PlantClaim claim : plantClaims.values()) {
            if (claim.plant().equals(plant)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether food at this spot can be eaten from inside the nest: a dug-out cell of the nest touches it (a feeding
     * gallery under a fallen log, see {@code FeedingGallery}), or a mud cast joined to one (the wood beside it eaten
     * away), so a termite can eat it by day without going out.
     */
    public boolean isFeedableFromInside(Level level, BlockPos food) {
        Set<BlockPos> nest = null;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos next = food.relative(direction);
            if (colony.nest().isInside(level, next) && (Nest.isDugOut(level, next) || MudCastBlock.isCast(level.getBlockState(next)))) {
                if (nest == null) {
                    nest = colony.nest().nestCells(level);
                }
                if (colony.nest().foodWays().isGalleryClosed(level, colony.nest().galleryFor(level, next), nest)) {
                    return true;
                }
            }
        }
        return false;
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
        colony.spots().found(ant, pos); // it found food here: its spot now (site fidelity, species that search alone)
        colony.recruits().found(level, ant, pos); // a rich patch: it lays a scent line home and calls nestmates out
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
            plaster(level, pos, before);
        }
        if (plant == null || clips >= plant.pieces()) {
            grassClips.remove(pos);
            busyGrass.remove(pos);
            BlockState state = level.getBlockState(pos);
            if (plant != null) {
                SoundType sound = state.getSoundType();
                level.playSound(null, pos, sound.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, sound.getPitch() * 1.2F);
                eatAway(level, pos, state, level.getGameTime(), true);
            }
        } else if (plant.foragers() <= 1) {
            busyGrass.put(pos, level.getGameTime() + GRASS_REST_TICKS);
        }
    }

    /**
     * A plant eaten right down ({@code eatenAt}: when its last piece was cut): it leaves a mud cast where it touched
     * the nest (see {@link #castFor}), otherwise nothing, and grass grows back counted from {@code eatenAt}.
     * {@code loud}: with the crumbs of the last bite (a real forager); without, out of sight (the numbers, see {@link FieldLife}).
     */
    private void eatAway(Level level, BlockPos pos, BlockState state, long eatenAt, boolean loud) {
        BlockState lives = state.getBlock() instanceof EatenInStages eaten ? eaten.afterLastPiece(state) : null;
        if (lives != null) {
            level.setBlock(pos, lives, 3); // a plant that lives on (grama grass: its head cut bare dries off)
            return;
        }
        BlockState cast = castFor(level, pos, state);
        if (cast == null) {
            if (loud) {
                level.destroyBlock(pos, false);
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            barePatches.eatenAway(level, pos, state, eatenAt);
        } else {
            if (loud) {
                level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, pos,
                        net.minecraft.world.level.block.Block.getId(state));
            }
            level.setBlock(pos, cast, 3);
        }
    }

    // --- the numbers out foraging (see FieldLife) -------------------------------------------------------------------

    /**
     * One of the numbers cut a piece off this plant (maybe in unloaded land, so its block is not read unless loaded): one
     * more piece cut, the block catches up later, out of sight ({@link #showCuts}). Returns the pieces cut so far.
     */
    int cutByNumbers(Level level, BlockPos plant, SpeciesProfile.PlantFood food) {
        BlockPos pos = plant.immutable();
        if (!grassClips.containsKey(pos) && level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof EatenInStages eaten) {
            grassClips.put(pos, eaten.eatenAlready(level.getBlockState(pos), food.pieces())); // already gnawed by someone else
        }
        return grassClips.merge(pos, 1, Integer::sum);
    }

    /**
     * The plant at {@code pos} (loaded, out of every player's sight) is made to look as eaten as its pieces cut say: a
     * plant eaten in stages to its stage, a used-up one gone ({@code eatenAt}: when its last piece was cut).
     */
    void showCuts(Level level, BlockPos pos, long eatenAt) {
        BlockState state = level.getBlockState(pos);
        SpeciesProfile.PlantFood plant = food().plantFor(state);
        if (plant == null) {
            grassClips.remove(pos); // gone meanwhile (a player broke it, a real forager finished it)
            return;
        }
        int cut = grassClips.getOrDefault(pos, 0);
        if (state.getBlock() instanceof EatenInStages eaten) {
            cut = Math.max(cut, eaten.eatenAlready(state, plant.pieces()));
        }
        if (cut >= plant.pieces()) {
            grassClips.remove(pos);
            busyGrass.remove(pos);
            eatAway(level, pos, state, eatenAt, false);
        } else if (cut > 0) {
            grassClips.put(pos, cut);
            if (state.getBlock() instanceof EatenInStages eaten) {
                BlockState after = eaten.eatenTo(state, cut, plant.pieces());
                if (after != state) {
                    level.setBlock(pos, after, 3);
                }
            }
        }
    }

    /**
     * Once a second while the whole nest is loaded: the brain notes how many more layers the food store's heaps can
     * take (the empty floor and the heaps not full, not where a young one lies), for when it cannot look ({@link #roomKnown}).
     */
    void watchRoom(Level level) {
        List<BlockPos> cells = colony.nest().getFoodStoreCells(level);
        SeedPileBlock pile = food().pileBlock();
        Set<BlockPos> young = colony.brood().getBroodCells(level);
        int room = 0;
        for (BlockPos pos : cells) {
            if (!level.isLoaded(pos)) {
                return; // (keep what was seen last)
            }
            BlockState state = level.getBlockState(pos);
            if (young.contains(pos)) {
                continue;
            }
            if (state.isAir()) {
                room += pile.maxLayers();
            } else if (state.is(pile)) {
                room += Math.max(0, pile.maxLayers() - state.getValue(SeedPileBlock.LAYERS));
            }
        }
        seenRoom = room;
    }

    /**
     * While away, the brain counts the food store room as dug out (the builder numbers have had the time, see
     * {@link NestLife}): it can take as much as an empty room of that many floor cells, until the brain sees the real one.
     */
    void storeDugAway(int floorCells) {
        seenRoom = Math.max(seenRoom, floorCells * food().pileBlock().maxLayers());
    }

    /** How many more pieces the food store can take as far as the brain knows: the room it last saw, less what is owed to it. */
    int roomKnown() {
        return (roomForTests != null ? roomForTests : seenRoom) - colony.ledger().store();
    }

    /** Tests only: pretend the store had this much room the last time the brain looked (null: as seen). */
    public void setRoomForTests(@org.jetbrains.annotations.Nullable Integer room) {
        roomForTests = room;
    }

    /**
     * A food plant in the way of a wall or a runway is cut down by the builders: what is left of it (its pieces not cut
     * yet) is left lying on the spot as pieces for the foragers to carry in, so no food is lost, and the grass grows
     * back later like grass eaten.
     */
    public void cutAwayForTheForagers(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        SpeciesProfile.PlantFood plant = food().plantFor(state);
        if (plant == null) {
            return;
        }
        int cut = grassClips.getOrDefault(pos, state.getBlock() instanceof EatenInStages eaten ? eaten.eatenAlready(state, plant.pieces()) : 0);
        int left = Math.max(0, plant.pieces() - cut);
        grassClips.remove(pos);
        busyGrass.remove(pos);
        level.destroyBlock(pos, false);
        barePatches.eatenAway(level, pos, state);
        if (left > 0) {
            ItemStack pieces = cutPiece();
            pieces.setCount(left);
            BlockPos drop = dropSpotBeside(level, pos);
            ItemEntity item = new ItemEntity(level, drop.getX() + 0.5, drop.getY() + 0.1, drop.getZ() + 0.5, pieces);
            item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            item.setUnlimitedLifetime(); // cut grass does not vanish: it lies until a forager carries it in
            level.addFreshEntity(item);
        }
    }

    /**
     * Where the pieces of a plant cut out of a wall's or a runway's way are left: an open spot beside it on the ground,
     * outside the nest and the runways (the plant's own spot gets the wall, and pieces in it could not be reached),
     * else the spot itself.
     */
    private BlockPos dropSpotBeside(Level level, BlockPos pos) {
        Set<BlockPos> nest = colony.nest().nestCells(level);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (!level.isLoaded(next) || nest.contains(next) || colony.nest().foodWays().getRunways().stream().anyMatch(r -> r.cells().contains(next))) {
                continue;
            }
            BlockState state = level.getBlockState(next);
            if (state.getCollisionShape(level, next).isEmpty() && state.getFluidState().isEmpty()
                    && level.getBlockState(next.below()).isFaceSturdy(level, next.below(), net.minecraft.core.Direction.UP)) {
                return next;
            }
        }
        return pos;
    }

    /**
     * What is left of a food block eaten away by a species that plasters mud over what it eats (the profile's
     * {@code nest.crust_walls}: termites), or null for nothing: a whole block of wood leaves a mud cast (the user's
     * choice, 2026-10-14; see {@link MudCastBlock}), and so does a branch cased in mud over a feeding gallery. The cast's
     * channel opens toward the nest it was eaten from and joins the casts beside it.
     */
    @Nullable
    private BlockState castFor(Level level, BlockPos pos, BlockState food) {
        if (!colony.profile().nest().crustWalls() || !(food.getBlock() instanceof EatenInStages)) {
            return null;
        }
        List<net.minecraft.core.Direction> open = new java.util.ArrayList<>();
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            BlockPos next = pos.relative(side);
            if (colony.nest().isInside(level, next) && Nest.isDugOut(level, next)) {
                open.add(side);
            }
        }
        if (open.isEmpty() && !food.isCollisionShapeFullBlock(level, pos)) {
            return null; // a branch eaten out in the open leaves nothing
        }
        return ModBlocks.MUD_CAST.get().stateFor(level, pos, 0, open);
    }

    /**
     * Mud over the top of a block of wood being eaten (a species with {@code nest.crust_walls}): real termites plaster
     * the wood they eat, so they stay covered. The top gets a crust of mud if it is open air nobody stands in and not
     * one of the colony's own spaces; it crumbles away once the wood under it is a cast.
     */
    private void plaster(Level level, BlockPos wood, BlockState state) {
        BlockPos top = wood.above();
        if (!colony.profile().nest().crustWalls() || !state.isCollisionShapeFullBlock(level, wood) || !level.isLoaded(top)) {
            return;
        }
        BlockState above = level.getBlockState(top);
        if (!above.isAir() && !MudCrustBlock.isCrust(above) || MudCrustBlock.hasFace(above, net.minecraft.core.Direction.DOWN)
                || colony.nest().nestCells(level).contains(top) || colony.foragingHoles().isHole(level, top)
                || !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(top)).isEmpty()) {
            return;
        }
        level.setBlock(top, ModBlocks.MUD_CRUST.get().withFace(above, net.minecraft.core.Direction.DOWN), 3);
    }

    /** Forgets the cuts made at this plant: it is counted by how eaten it looks again. For tests. */
    public void forgetCutsForTests(BlockPos plant) {
        grassClips.remove(plant);
    }

    /** Pretends this many pieces have been cut off the plant at this spot already. For tests. */
    public void setPiecesCutForTests(BlockPos plant, int pieces) {
        grassClips.put(plant.immutable(), pieces);
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
        if (!addLayerAt(level, spot)) {
            return false;
        }
        countIn(rich);
        if (level instanceof net.minecraft.server.level.ServerLevel server && isComb()) {
            // Fresh comb laid: a soft wet squelch and a few dark specks.
            server.playSound(null, spot, net.minecraft.sounds.SoundEvents.MUD_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.25F, 1.6F);
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    level.getBlockState(spot)), spot.getX() + 0.5, spot.getY() + FungusCombBlock.topOf(level.getBlockState(spot)),
                    spot.getZ() + 0.5, 4, 0.2, 0.02, 0.2, 0.0);
        }
        return true;
    }

    /** Whether the store is fungus comb (it ages: see {@link FungusCombBlock}). */
    private boolean isComb() {
        return food().pileBlock() instanceof FungusCombBlock;
    }

    /**
     * One layer off the heap at {@code pile} (whose state is {@code state}). A fungus comb eaten right down leaves no
     * block, and its nodules go with it ({@link FungusGarden#loseNodules}).
     */
    private void takeOneLayer(Level level, BlockPos pile, BlockState state) {
        BlockState less = oneLayerLess(state);
        level.setBlock(pile, less, 3);
        if (less.isAir() && state.getBlock() instanceof FungusCombBlock) {
            colony.garden().loseNodules(state.getValue(FungusCombBlock.NODULES));
        }
    }

    /** The heap with one layer less (fungus comb: off its bottom, an old one first), or air if it was the last. */
    private BlockState oneLayerLess(BlockState state) {
        if (state.getBlock() instanceof FungusCombBlock) {
            BlockState less = FungusCombBlock.withoutBottomLayer(state);
            return less == null ? Blocks.AIR.defaultBlockState() : less;
        }
        int layers = state.getValue(SeedPileBlock.LAYERS);
        return layers <= 1 ? Blocks.AIR.defaultBlockState() : state.setValue(SeedPileBlock.LAYERS, layers - 1);
    }

    /**
     * The heap at {@code spot} grows by one layer (or a new one starts), without counting it: for a piece already
     * counted (see {@link NestLedger}). False if that spot is full, or an egg or a young one lies there. Anyone standing
     * in the cell is lifted onto the new layer, never left sunk in the heap (docs/BUGS.md 48).
     */
    boolean addLayerAt(Level level, BlockPos spot) {
        BlockState state = level.getBlockState(spot);
        SeedPileBlock pile = food().pileBlock();
        if (colony.brood().getBroodCells(level).contains(spot)) {
            return false; // an egg or a young one was put there meanwhile: the heap would bury it
        }
        if (state.isAir()) {
            level.setBlock(spot, isComb() ? FungusCombBlock.newComb(pile.defaultBlockState()) : pile.defaultBlockState(), 3);
        } else if (state.is(pile) && state.getValue(SeedPileBlock.LAYERS) < pile.maxLayers()) {
            // (fungus comb: the new layer goes on top, fresh)
            level.setBlock(spot, isComb() ? FungusCombBlock.withLayerOnTop(state)
                    : state.setValue(SeedPileBlock.LAYERS, state.getValue(SeedPileBlock.LAYERS) + 1), 3);
        } else {
            return false;
        }
        double top = spot.getY() + level.getBlockState(spot).getCollisionShape(level, spot).max(net.minecraft.core.Direction.Axis.Y);
        for (net.minecraft.world.entity.LivingEntity living : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(spot))) {
            if (living.getBoundingBox().minY < top) {
                Mound.liftOnto(level, living, spot, top);
            }
        }
        return true;
    }

    /** One more piece in the store, counted now; its layer is put down later (see {@link NestLedger}). */
    void countIn(boolean rich) {
        food++;
        hadFood = true;
        if (rich) {
            richSeeds++;
        }
    }

    /**
     * The heaps a layer can be taken off without counting it (for a piece already taken out of the count, see
     * {@link NestLedger}): every seed heap; fungus comb only where it has an old layer (the old workers eat old comb,
     * from the bottom), and never under an egg or a nymph lying on it.
     */
    List<BlockPos> layersToTake(Level level) {
        List<BlockPos> heaps = new ArrayList<>();
        SeedPileBlock pile = food().pileBlock();
        boolean comb = isComb();
        Set<BlockPos> young = comb ? colony.brood().getBroodCells(level) : Set.of();
        for (BlockPos pos : colony.nest().getFoodStoreCells(level)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(pile) && (!comb || FungusCombBlock.old(state) > 0 && !young.contains(pos))) {
                heaps.add(pos);
            }
        }
        return heaps;
    }

    /** One layer comes off the heap at {@code pile}, without counting it (see {@link #layersToTake}). */
    boolean removeLayerAt(Level level, BlockPos pile) {
        BlockState state = level.getBlockState(pile);
        if (!state.is(food().pileBlock())) {
            return false;
        }
        takeOneLayer(level, pile, state);
        return true;
    }

    /**
     * A piece is taken out of the count (by the numbers: a meal, or a seed for the mill); its layer comes off a heap
     * later, out of sight ({@link NestLedger}). A rich seed if {@code wantRich} and there is one, or if only rich ones are
     * left. Returns whether it was a rich one. Call only while {@link #getFood} is above 0.
     */
    boolean takeCounted(boolean wantRich) {
        richSeeds = Math.min(richSeeds, food);
        boolean rich = richSeeds > 0 && (wantRich || food <= richSeeds);
        food = Math.max(0, food - 1);
        if (rich) {
            richSeeds--;
        }
        colony.ledger().addStore(-1);
        return rich;
    }

    /** One of the colony's numbers eats a piece from the store (plain seeds first). False if the store is empty. */
    boolean eatForNumbers() {
        if (food <= 0) {
            return false;
        }
        takeCounted(false);
        seedsEaten++;
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
        takeOneLayer(level, pile, state);
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
     * A heap a hungry worker can eat a piece of, or null if there is none: any seed heap; fungus comb only where it has
     * an old layer (see {@link #layersToTake}).
     */
    @Nullable
    public BlockPos findMealPile(Level level, RandomSource random) {
        List<BlockPos> piles = layersToTake(level);
        return piles.isEmpty() ? null : piles.get(random.nextInt(piles.size()));
    }

    /**
     * A hungry worker takes one raw seed off the heap at {@code pile} to eat it: plain seeds first (a rich one only if
     * nothing else is left); or a layer of old comb off the bottom of a comb. Returns the seed or piece, or an empty
     * stack if the heap is gone (or the comb has no old layer left).
     */
    public ItemStack eatSeedAt(Level level, BlockPos pile, UUID ant) {
        BlockState state = level.getBlockState(pile);
        if (state.getBlock() instanceof FungusCombBlock && FungusCombBlock.old(state) <= 0) {
            return ItemStack.EMPTY;
        }
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
        tag.putBoolean("hadFood", hasHadFood());
        tag.putInt("seenRoom", seenRoom);
        CompoundTag clips = new CompoundTag();
        grassClips.forEach((pos, count) -> clips.putInt(Long.toString(pos.asLong()), count));
        tag.put("grassClips", clips);
        barePatches.save(tag);
    }

    void load(CompoundTag tag) {
        food = tag.getInt("food");
        seedsEaten = tag.getInt("seedsEaten");
        richSeeds = tag.getInt("richSeeds");
        hadFood = tag.contains("hadFood") ? tag.getBoolean("hadFood") : food > 0 || seedsEaten > 0; // (older saves)
        seenRoom = tag.getInt("seenRoom");
        CompoundTag clips = tag.getCompound("grassClips");
        for (String key : clips.getAllKeys()) {
            grassClips.put(BlockPos.of(Long.parseLong(key)), clips.getInt(key));
        }
        barePatches.load(tag);
    }
}
