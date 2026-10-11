package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.QueenAntEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.registry.ModItems;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
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
    /**
     * Where the soil is too thin or too stony for the rooms at full depth, the whole nest sits one block higher
     * (the shaft is one block shorter). Never shallower than this.
     */
    public static final int MIN_SHAFT_DEPTH = 2;

    /** The mound reaches this far (in blocks, sideways) from the entrance. */
    public static final int MOUND_RADIUS = 3;

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

    /** Bite progress per block (0..1). Not saved: a half-dug block just restarts after a reload. */
    private final Map<BlockPos, Float> digProgress = new HashMap<>();

    /** Where the nest's rooms go. Decided once (see {@link #getLayout}); not saved, it comes out the same after a reload. */
    private record Layout(List<NestPlan.Chamber> chambers, Set<BlockPos> cells, int shaftDepth) {
        Layout(List<NestPlan.Chamber> chambers, int shaftDepth) {
            this(chambers, cellsOf(chambers), shaftDepth);
        }

        /** The level used for this purpose, or null if the nest has none. */
        @Nullable
        NestPlan.Chamber of(NestPlan.Kind kind) {
            for (NestPlan.Chamber chamber : chambers) {
                if (chamber.kind() == kind) {
                    return chamber;
                }
            }
            return null;
        }

        private static Set<BlockPos> cellsOf(List<NestPlan.Chamber> chambers) {
            Set<BlockPos> cells = new HashSet<>();
            for (NestPlan.Chamber chamber : chambers) {
                cells.addAll(chamber.cells());
            }
            return cells;
        }
    }

    @Nullable
    private Layout layout;

    /** Seeds lying within this many blocks (sideways) of the entrance are fetched by the workers. */
    public static final int FOOD_RADIUS = 24;
    /** An ant that went for a seed keeps the others away from it this long (in ticks). */
    private static final int SEED_CLAIM_TICKS = 600;

    /** A tuft of grass an ant just clipped is left alone this long (in ticks), so the ants don't all crowd one tuft. */
    private static final int GRASS_REST_TICKS = 200;
    /** A tuft of grass gives this many seeds; after the last one it is used up and breaks. */
    public static final int SEEDS_PER_GRASS = 10;
    /** The tufts of grass around the nest are looked up again after this many ticks (a scan of the whole area). */
    private static final int GRASS_SCAN_TICKS = 100;

    /** Grass tufts an ant is on its way to, or just clipped: position -> game time until which the others leave it alone. Not saved. */
    private final Map<BlockPos, Long> busyGrass = new HashMap<>();
    /** The tufts of short grass found at the last scan, and when the next scan is due. Not saved. */
    private final List<BlockPos> grassTufts = new ArrayList<>();
    private long grassScanTime;

    /** How many seeds have been clipped from each tuft of grass so far (only tufts that were clipped at least once). Saved. */
    private final Map<BlockPos, Integer> grassClips = new HashMap<>();

    /** How much food the colony has stored (one per seed in the food store). Saved. */
    private int food;
    /** Seeds an ant is on its way to: item entity id -> game time when the claim runs out. Not saved. */
    private final Map<Integer, Long> claimedSeeds = new HashMap<>();

    /** How long (in ticks) an ant that went to close a leak counts as working on it. */
    private static final int LEAK_CLAIM_TICKS = 600;
    /** At most this many ants work on one leak at the same time; an opening nobody is at yet beats a crowded one. */
    private static final int MAX_WORKERS_PER_LEAK = 4;
    /** How much closer (in blocks) an empty opening counts than a crowded one, per ant already on it. */
    private static final double CROWD_PENALTY = 6.0;

    /** Most ants that fetch soil for the hill at the same time (never more than the hill has gaps). */
    private static final int MAX_REPAIRERS = 3;
    /** How long (in ticks) a soil pellet fetched for the hill counts as on its way to the gap. */
    private static final int REPAIR_CLAIM_TICKS = 300;
    /** After a repair pellet could not be delivered (no route to the gap), no more repair soil is fetched for this long (in ticks). */
    private static final int HILL_PAUSE_TICKS = 1200;

    /** The ants that fetched soil to repair the hill: ant -> game time when the pellet should have arrived. Not saved. */
    private final Map<UUID, Long> repairers = new HashMap<>();
    /** No repair soil is fetched before this game time (see {@link #HILL_PAUSE_TICKS}). Not saved. */
    private long hillPausedUntil;

    /** The ants working on each leak: hole -> (ant -> game time when its claim runs out). Not saved. */
    private final Map<BlockPos, Map<UUID, Long>> claimedLeaks = new HashMap<>();
    /** How far each leak is closed (1.0 = shut): every ant adds its share, so more ants close it sooner. Not saved. */
    private final Map<BlockPos, Float> sealProgress = new HashMap<>();

    /**
     * An opening in the nest walls: {@code hole} is open space (a cave, a crack, water) right next to a dug-out
     * nest cell, and {@code stand} is a dug-out cell with a floor, no further than one block from the hole, where
     * an ant can stand to close it (null when no ant can get close enough). The entrance itself is never a leak.
     */
    public record Leak(BlockPos hole, @Nullable BlockPos stand) {
    }

    /**
     * The next block to dig, or null if all the digging is done (or blocked). Order: the entrance
     * shaft, then the levels from the top down (the stair, the tunnel and the room of each: seed store,
     * nursery, queen, refuse chamber). The shaft is never widened, so its walls stay solid for the ants to climb.
     */
    @Nullable
    public BlockPos findDigTarget(Level level) {
        // The rooms are planned before the first block is dug, because the plan decides how deep the shaft goes.
        int depth = shaftDepth(level);
        List<BlockPos> shaft = new ArrayList<>();
        for (int i = 1; i <= depth; i++) {
            shaft.add(center.below(i));
        }
        BlockPos next = firstDiggable(level, shaft);
        if (next != null) {
            return next;
        }
        // Only start the tunnels once the shaft is completely open down to its bottom.
        BlockPos bottom = center.below(depth);
        if (!level.isLoaded(bottom) || !level.getBlockState(bottom).isAir()) {
            return null;
        }
        Layout layout = getLayout(level);
        if (layout == null) {
            return null;
        }
        for (NestPlan.Chamber chamber : layout.chambers()) {
            next = firstDiggable(level, chamber.cells());
            if (next != null) {
                return next;
            }
        }
        return null;
    }

    /**
     * The middle of the room the queen lives in, or null if there is none yet (or the chunk is not loaded). That is
     * her own room (the queen level) once it is finished; until then the nursery, where the bread is, once that is
     * finished (see {@link #getHomeChamber}). The other rooms don't matter here.
     */
    @Nullable
    public BlockPos getFinishedRoomCenter(Level level) {
        NestPlan.Chamber home = getHomeChamber(level);
        return home == null ? null : home.center();
    }

    /**
     * The level the queen lives in: the queen level once it is finished (the shaft, every stair down to it, its tunnel
     * and the whole room are dug out), until then the nursery once that is finished (the bread is there). Where the soil
     * is too thin for a nursery, the first finished level from the top. Null if she has no home yet. The refuse chamber
     * is never a home, and neither is the seed store when the plan has a nursery.
     */
    @Nullable
    public NestPlan.Chamber getHomeChamber(Level level) {
        Layout layout = getLayout(level);
        if (layout == null || !isShaftOpen(level, layout)) {
            return null;
        }
        NestPlan.Chamber queen = layout.of(NestPlan.Kind.QUEEN);
        if (queen != null && isFinished(level, queen)) {
            return queen;
        }
        NestPlan.Chamber nursery = layout.of(NestPlan.Kind.NURSERY);
        if (nursery != null) {
            return isFinished(level, nursery) ? nursery : null;
        }
        for (NestPlan.Chamber chamber : layout.chambers()) {
            if (chamber.kind() != NestPlan.Kind.REFUSE && isFinished(level, chamber)) {
                return chamber;
            }
        }
        return null;
    }

    /** True once everything needed to live in this level is dug out: the way in and the whole room (bread may lie on its floor). */
    private static boolean isFinished(Level level, NestPlan.Chamber chamber) {
        for (BlockPos pos : chamber.wayIn()) {
            if (!isDugOut(level, pos)) {
                return false;
            }
        }
        for (BlockPos pos : chamber.room()) {
            if (!isDugOut(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** True when the whole entrance shaft is dug out. */
    private boolean isShaftOpen(Level level, Layout layout) {
        for (int i = 1; i <= layout.shaftDepth(); i++) {
            if (!level.isLoaded(center.below(i)) || !level.getBlockState(center.below(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    // ---- Food

    public int getFood() {
        return food;
    }

    /**
     * The floor cells of the food store that are dug out and can be walked to (the room, without its tunnel). The
     * room does not have to be finished: every cell that is already dug counts. Empty until the way in is dug.
     */
    public List<BlockPos> getFoodStoreCells(Level level) {
        return getRoomCells(level, NestPlan.Kind.FOOD);
    }

    /** The floor cells of the nursery that are dug out and can be walked to (the room does not have to be finished). */
    public List<BlockPos> getNurseryCells(Level level) {
        return getRoomCells(level, NestPlan.Kind.NURSERY);
    }

    /** The floor cells of the queen's room that are dug out and can be walked to. */
    public List<BlockPos> getQueenRoomCells(Level level) {
        return getRoomCells(level, NestPlan.Kind.QUEEN);
    }

    /** The floor cells of the room the queen lives in now (see {@link #getHomeChamber}); empty if she has none yet. */
    public List<BlockPos> getHomeRoomCells(Level level) {
        NestPlan.Chamber home = getHomeChamber(level);
        return home == null ? List.of() : getRoomCells(level, home.kind());
    }

    private static boolean isDugOut(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(ModBlocks.SEED_PILE.get()) || state.is(ModBlocks.BREAD_PILE.get());
    }

    private static boolean touchesSideways(BlockPos a, BlockPos b) {
        return a.getY() == b.getY() && Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) == 1;
    }

    /**
     * The dug-out floor cells of one room. They count once the shaft, the stairs down to its level and the tunnel to
     * the room are dug; after that every cell that is dug and joins up with the tunnel (or with another counted cell)
     * is in.
     */
    private List<BlockPos> getRoomCells(Level level, NestPlan.Kind kind) {
        Layout layout = getLayout(level);
        if (layout == null || !isShaftOpen(level, layout)) {
            return List.of();
        }
        NestPlan.Chamber chamber = layout.of(kind);
        if (chamber == null) {
            return List.of();
        }
        for (BlockPos pos : chamber.wayIn()) {
            if (!isDugOut(level, pos)) {
                return List.of();
            }
        }
        BlockPos tunnelEnd = chamber.tunnelEnd();
        List<BlockPos> reached = new ArrayList<>();
        for (BlockPos pos : chamber.room()) {
            if (!isDugOut(level, pos)) {
                continue;
            }
            boolean joined = touchesSideways(pos, tunnelEnd);
            for (int k = 0; k < reached.size() && !joined; k++) {
                joined = touchesSideways(pos, reached.get(k));
            }
            if (joined) {
                reached.add(pos);
            }
        }
        return reached;
    }

    /**
     * A spot on the floor of the food store that can take one more seed, or null if the store is not ready or full.
     * Most of the time it is a heap that already exists (so the heaps grow, not a thin layer everywhere).
     */
    @Nullable
    public BlockPos pickFoodSpot(Level level, RandomSource random) {
        List<BlockPos> growing = new ArrayList<>();
        List<BlockPos> empty = new ArrayList<>();
        Set<BlockPos> young = getBroodCells(level); // eggs and larvae may lie in the seed store where it is the queen's home (no nursery)
        for (BlockPos pos : getFoodStoreCells(level)) {
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
     * The seed on the ground nearest to {@code near} that the ants should fetch, or null: only once the food store
     * has room (or the queen wants seeds), only on the surface (not what is already stored below), within {@link #FOOD_RADIUS} of the entrance,
     * and not one another ant is on its way to. {@code avoid}: item entity ids this ant could not reach.
     */
    @Nullable
    public ItemEntity findSeed(Level level, BlockPos near, Set<Integer> avoid) {
        if (!(level instanceof ServerLevel serverLevel) || !wantsSeeds(serverLevel)) {
            return null; // no food store yet (and the queen wants nothing), or it is full
        }
        long now = level.getGameTime();
        claimedSeeds.values().removeIf(until -> until <= now);
        AABB area = new AABB(center).inflate(FOOD_RADIUS, 0, FOOD_RADIUS).expandTowards(0, 8, 0);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (!item.isAlive() || !item.getItem().is(ModTags.SEEDS) || item.getY() < center.getY() - 0.5
                    || claimedSeeds.containsKey(item.getId()) || avoid.contains(item.getId())) {
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

    /**
     * A tuft of short grass near the nest that an ant could clip seeds from, or null: only once the food store is
     * ready and not full. Scans the area within {@link #FOOD_RADIUS} of the entrance (at most every few seconds) and takes
     * the tuft nearest to {@code near}. A tuft another ant is on its way to, or clipped lately, is skipped, and so is one in
     * {@code avoid} (this ant found no way to it).
     */
    @Nullable
    public BlockPos findGrass(Level level, BlockPos near, Set<BlockPos> avoid) {
        if (!(level instanceof ServerLevel serverLevel) || !wantsSeeds(serverLevel)) {
            return null;
        }
        long now = level.getGameTime();
        busyGrass.values().removeIf(until -> until <= now);
        if (now >= grassScanTime) {
            grassScanTime = now + GRASS_SCAN_TICKS;
            grassTufts.clear();
            for (int x = center.getX() - FOOD_RADIUS; x <= center.getX() + FOOD_RADIUS; x++) {
                for (int z = center.getZ() - FOOD_RADIUS; z <= center.getZ() + FOOD_RADIUS; z++) {
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
            if (busyGrass.containsKey(pos) || avoid.contains(pos) || !level.getBlockState(pos).is(Blocks.SHORT_GRASS)) {
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
     * The ant clipped its seed. The tuft rests for a moment; after {@link #SEEDS_PER_GRASS} seeds it is used up and
     * breaks (without dropping anything).
     */
    public void clippedGrass(Level level, BlockPos grass) {
        BlockPos pos = grass.immutable();
        int clips = grassClips.merge(pos, 1, Integer::sum);
        if (clips >= SEEDS_PER_GRASS) {
            grassClips.remove(pos);
            busyGrass.remove(pos);
            level.destroyBlock(pos, false);
        } else {
            busyGrass.put(pos, level.getGameTime() + GRASS_REST_TICKS);
        }
    }

    public void claimSeed(Level level, ItemEntity seed) {
        claimedSeeds.put(seed.getId(), level.getGameTime() + SEED_CLAIM_TICKS);
    }

    public void releaseSeed(ItemEntity seed) {
        claimedSeeds.remove(seed.getId());
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

    // ---- Roles

    /** From this many workers on, one of them is the nurse who stays by the queen. */
    public static final int NURSE_FROM_WORKERS = 3;
    /** From this many workers on, two of them are nurses. */
    public static final int SECOND_NURSE_FROM_WORKERS = 8;
    /** From this many workers on, three of them are nurses. */
    public static final int THIRD_NURSE_FROM_WORKERS = 12;
    /** From this many workers on, four of them are nurses. */
    public static final int FOURTH_NURSE_FROM_WORKERS = 20;

    /**
     * The role of this worker. Roles are shared out evenly: one nurse (from {@link #NURSE_FROM_WORKERS} workers on, two from {@link #SECOND_NURSE_FROM_WORKERS}, three from {@link #THIRD_NURSE_FROM_WORKERS}, four from {@link #FOURTH_NURSE_FROM_WORKERS}),
     * the rest half builders and half foragers (builders first). A worker keeps its role until it is one too many for
     * it, so roles only change when workers are born or die.
     */
    public ColonyRole roleOf(ServerLevel level, UUID ant) {
        roles.keySet().removeIf(id -> !id.equals(ant) && !(level.getEntity(id) instanceof WorkerAntEntity worker && worker.isAlive()));
        int[] others = new int[ColonyRole.values().length];
        int total = 1;
        for (Map.Entry<UUID, ColonyRole> entry : roles.entrySet()) {
            if (!entry.getKey().equals(ant)) {
                others[entry.getValue().ordinal()]++;
                total++;
            }
        }
        int nurses = total >= FOURTH_NURSE_FROM_WORKERS ? 4 : total >= THIRD_NURSE_FROM_WORKERS ? 3
                : total >= SECOND_NURSE_FROM_WORKERS ? 2 : total >= NURSE_FROM_WORKERS ? 1 : 0;
        int[] target = new int[ColonyRole.values().length];
        target[ColonyRole.NURSE.ordinal()] = nurses;
        target[ColonyRole.BUILDER.ordinal()] = (total - nurses + 1) / 2;
        target[ColonyRole.FORAGER.ordinal()] = (total - nurses) / 2;
        ColonyRole current = roles.get(ant);
        if (current == null || others[current.ordinal()] >= target[current.ordinal()]) {
            current = ColonyRole.BUILDER;
            for (ColonyRole role : new ColonyRole[] {ColonyRole.NURSE, ColonyRole.FORAGER, ColonyRole.BUILDER}) {
                if (others[role.ordinal()] < target[role.ordinal()]) {
                    current = role;
                    break;
                }
            }
            roles.put(ant, current);
        }
        return current;
    }

    // ---- The queen and her eggs

    /** The workers must feed the queen this many pieces of ant bread before she carries an egg. */
    public static final int QUEEN_MEAL = 20;
    /** This many pieces of rich ant bread (made from rich seeds, see {@code antcolonies:rich_seeds}) fed to the queen start a burst of eggs. */
    public static final int RICH_MEAL = 5;
    /** In a burst she lays this many eggs ... */
    public static final int BURST_EGGS = 5;
    /** ... one every minute at normal speed (an egg is still held back while the colony has {@link #MAX_EGGS} young). */
    public static final int BURST_INTERVAL_TICKS = 1200;
    /** How long (in ticks) she carries the egg before it comes out behind her: 3 minutes at normal speed. */
    public static final int INCUBATION_TICKS = 3600;
    /** An egg hatches into a larva this many ticks after it was put in the nursery: 1 minute at normal speed. */
    public static final int HATCH_TICKS = 1200;
    /** A larva needs this many pieces of ant bread, fed by the nurses, before it grows into a worker. */
    public static final int LARVA_MEAL = 5;
    /** Larvae grow into workers only while the colony has fewer than this many workers; the other larvae wait in the nursery. */
    public static final int MAX_WORKERS = 20;
    /** How long (in ticks) a nurse on its way to a larva keeps the other nurses away from it. */
    public static final int LARVA_CLAIM_TICKS = 600;
    /** At most this many young (eggs and larvae, in the nursery or lying about) at the same time; while there are that many the queen is not fed. */
    public static final int MAX_EGGS = 10;
    /** How long (in ticks) a worker on its way to feed the queen counts as feeding her. */
    private static final int FEED_CLAIM_TICKS = 1300;
    /** How long (in ticks) a worker on its way to an egg keeps the others away from it. */
    private static final int EGG_CLAIM_TICKS = 600;

    /** How many pieces of bread the workers have fed the queen for her next egg (QUEEN_MEAL = full). Saved. */
    private int queenBread;
    /** The game time when the egg she carries comes out (-1 = she carries none). During a burst: when the next egg is due. Saved. */
    private long eggDue = -1;
    /** How many of the seeds in the food store are rich ones (never more than {@link #food}). Saved. */
    private int richSeeds;
    /** How many of the pieces of bread in the nest are rich ant bread (never more than {@link #bread}). Saved. */
    private int richBread;
    /** How many pieces of rich bread the workers have fed the queen towards a burst ({@link #RICH_MEAL} = it starts). Saved. */
    private int queenRich;
    /** Eggs the queen still has to lay in her burst (0 = no burst going on). Saved. */
    private int burstLeft;
    /** The workers carrying a rich seed to the mill or milling it. Not saved. */
    private final Set<UUID> richMillers = new HashSet<>();
    /** How many eggs the queen has laid in all. Saved. */
    private int eggsLaid;
    /** The role of each worker: ant -> role. Not saved: after a reload the workers ask again and get a balanced share. */
    private final Map<UUID, ColonyRole> roles = new HashMap<>();
    /** The workers on their way to feed the queen: ant -> game time when the claim runs out. Not saved. */
    private final Map<UUID, Long> feeders = new HashMap<>();
    /** The eggs a worker is on its way to: egg -> game time when the claim runs out. Not saved. */
    private final Map<UUID, Long> claimedEggs = new HashMap<>();
    /** The dead ants a worker is on its way to: corpse -> game time when the claim runs out. Not saved. */
    private final Map<UUID, Long> claimedCorpses = new HashMap<>();

    public int getQueenBread() {
        return queenBread;
    }

    public int getQueenRich() {
        return queenRich;
    }

    /** Eggs left in the queen's burst (0 = none going on). */
    public int getBurstLeft() {
        return burstLeft;
    }

    public int getRichSeeds() {
        return Math.min(richSeeds, food);
    }

    public int getRichBread() {
        return Math.min(richBread, bread);
    }

    /** Whether the queen carries an egg right now. */
    public boolean isIncubating() {
        return eggDue >= 0;
    }

    /** The game time when the egg she carries comes out (-1 if she carries none). */
    public long getEggDue() {
        return eggDue;
    }

    public int getEggsLaid() {
        return eggsLaid;
    }

    /** The queen, if she is loaded and alive. */
    @Nullable
    public QueenAntEntity getQueen(ServerLevel level) {
        return level.getEntity(queenId) instanceof QueenAntEntity queen && queen.isAlive() ? queen : null;
    }

    /** True when the queen sits in her finished room (the workers feed her there, and her egg comes out there). */
    public boolean isQueenHome(ServerLevel level) {
        BlockPos room = getFinishedRoomCenter(level);
        QueenAntEntity queen = room == null ? null : getQueen(level);
        return queen != null && Math.abs(queen.getX() - (room.getX() + 0.5)) <= 2.5
                && Math.abs(queen.getZ() - (room.getZ() + 0.5)) <= 2.5 && Math.abs(queen.getY() - room.getY()) < 1.5;
    }

    /**
     * Whether the queen can take more bread: she is home and not carrying an egg, there are not yet {@link #MAX_EGGS}
     * eggs, and the workers already on their way with bread would not fill her.
     */
    public boolean needsFeeding(ServerLevel level) {
        long now = level.getGameTime();
        feeders.values().removeIf(until -> until <= now);
        return eggDue < 0 && queenBread + feeders.size() < QUEEN_MEAL && isQueenHome(level)
                && countBrood(level) < MAX_EGGS;
    }

    /**
     * Whether it is worth fetching a seed from outside: the food store has room for it, or bread still has to be milled
     * (the ant then brings the seed straight to the mill, so nothing is put down without a store).
     */
    public boolean wantsSeeds(ServerLevel level) {
        return pickFoodSpot(level, level.getRandom()) != null || needsBread(level);
    }

    /** A worker sets out to bring the queen bread. False if she needs no more food right now. */
    public boolean claimFeeding(ServerLevel level, UUID ant) {
        if (!needsFeeding(level)) {
            return false;
        }
        feeders.put(ant, level.getGameTime() + FEED_CLAIM_TICKS);
        return true;
    }

    /** The worker has fed her, or gave up. */
    public void releaseFeeding(UUID ant) {
        feeders.remove(ant);
    }

    /** A heap of seeds in the food store to take one seed from, or null if there is none. */
    @Nullable
    public BlockPos findSeedPile(Level level, RandomSource random) {
        List<BlockPos> piles = new ArrayList<>();
        for (BlockPos pos : getFoodStoreCells(level)) {
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
            richMillers.add(ant);
        }
        return new ItemStack(rich ? Items.PUMPKIN_SEEDS : Items.WHEAT_SEEDS);
    }

    // ---- Ant bread

    /** The nurses keep this much bread ready for the queen (one meal). */
    public static final int BREAD_STOCK = QUEEN_MEAL;
    /** Milling one seed into a piece of bread takes this long: 3 seconds. */
    public static final int MILL_TICKS = 60;
    /** How long (in ticks) a worker on its way to mill a seed counts as making bread. */
    private static final int MILL_CLAIM_TICKS = 1300;

    /** The pieces of bread lying in bread piles in the nest. Saved. */
    private int bread;
    /** The workers carrying a seed to the mill or milling it: ant -> game time when the claim runs out. Not saved. */
    private final Map<UUID, Long> millers = new HashMap<>();

    public int getBread() {
        return bread;
    }

    /** How many seeds are being milled right now (on their way to the mill, or in it). */
    public int getMilling() {
        return millers.size();
    }

    /**
     * Whether more bread should be milled: there is a place for it (the nursery, or the queen's room while there is no
     * nursery), and the pile plus the seeds already being milled are under what is wanted: one meal for the queen (while
     * she has room for more young) plus what the hungry larvae still have to eat.
     */
    public boolean needsBread(ServerLevel level) {
        long now = level.getGameTime();
        millers.values().removeIf(until -> until <= now);
        int wanted = (countBrood(level) < MAX_EGGS ? BREAD_STOCK : 0) + larvaHunger(level);
        return bread + millers.size() < wanted && pickBreadSpot(level, level.getRandom()) != null;
    }

    /** A worker sets out to mill a seed. False if no more bread is needed right now. */
    public boolean claimMilling(ServerLevel level, UUID ant) {
        if (!needsBread(level)) {
            return false;
        }
        millers.put(ant, level.getGameTime() + MILL_CLAIM_TICKS);
        return true;
    }

    /** The worker has made its bread, or gave up. */
    public void releaseMilling(UUID ant) {
        millers.remove(ant);
        richMillers.remove(ant);
    }

    /**
     * Whether the next seed to be milled should be a rich one: rich bread is wanted (the queen is not in a burst and
     * has not enough rich bread fed or ready yet) and the store has a rich seed.
     */
    public boolean wantsRichBread() {
        return burstLeft == 0 && eggDue < 0 && Math.min(richSeeds, food) > 0
                && queenRich + getRichBread() + richMillers.size() < RICH_MEAL;
    }

    /**
     * A worker that has just picked up a seed outside decides where it goes: to the food store if that has room,
     * otherwise (no store yet, or full) to the mill if bread is needed. True = the mill; the worker is then counted.
     */
    public boolean claimSeedForBread(ServerLevel level, UUID ant) {
        return pickFoodSpot(level, level.getRandom()) == null && claimMilling(level, ant);
    }

    /**
     * A spot for one more piece of bread, or null if there is none: a growing heap or an empty cell of the nursery, and
     * while the nursery has none, the same in the queen's room. Cells with an egg are left out.
     */
    @Nullable
    public BlockPos pickBreadSpot(Level level, RandomSource random) {
        Set<BlockPos> eggs = getBroodCells(level);
        for (List<BlockPos> cells : List.of(getNurseryCells(level), getHomeRoomCells(level))) {
            List<BlockPos> growing = new ArrayList<>();
            List<BlockPos> empty = new ArrayList<>();
            for (BlockPos pos : cells) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() && !eggs.contains(pos)) {
                    empty.add(pos);
                } else if (state.is(ModBlocks.BREAD_PILE.get()) && state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS) {
                    growing.add(pos);
                }
            }
            List<BlockPos> from = !growing.isEmpty() && (empty.isEmpty() || random.nextFloat() < 0.75F) ? growing : empty;
            if (!from.isEmpty()) {
                return from.get(random.nextInt(from.size()));
            }
        }
        return null;
    }

    /** A worker puts its freshly milled bread at {@code spot}: the heap grows by a layer (or a new one starts). False if that spot is full. */
    public boolean depositBread(Level level, BlockPos spot, boolean rich) {
        BlockState state = level.getBlockState(spot);
        if (state.isAir()) {
            level.setBlock(spot, ModBlocks.BREAD_PILE.get().defaultBlockState(), 3);
        } else if (state.is(ModBlocks.BREAD_PILE.get()) && state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS) {
            level.setBlock(spot, state.setValue(SeedPileBlock.LAYERS, state.getValue(SeedPileBlock.LAYERS) + 1), 3);
        } else {
            return false;
        }
        bread++;
        if (rich) {
            richBread++;
        }
        return true;
    }

    /**
     * A heap of bread to take a piece from, or null if there is none. Every room is searched, because bread made while
     * the queen lived in another room stays there when she moves down.
     */
    @Nullable
    public BlockPos findBreadPile(Level level, RandomSource random) {
        List<BlockPos> piles = new ArrayList<>();
        for (List<BlockPos> cells : List.of(getNurseryCells(level), getHomeRoomCells(level), getFoodStoreCells(level), getQueenRoomCells(level))) {
            for (BlockPos pos : cells) {
                if (level.getBlockState(pos).is(ModBlocks.BREAD_PILE.get())) {
                    piles.add(pos);
                }
            }
        }
        return piles.isEmpty() ? null : piles.get(random.nextInt(piles.size()));
    }

    /**
     * A worker takes one piece of bread off the heap at {@code pile}. Returns the piece (rich ant bread or plain ant
     * bread), or an empty stack if the heap is gone. Rich bread is taken if {@code wantRich} (for the queen) and the
     * colony has some, or if all the bread in the nest is rich.
     */
    public ItemStack takeBreadAt(Level level, BlockPos pile, boolean wantRich) {
        BlockState state = level.getBlockState(pile);
        if (!state.is(ModBlocks.BREAD_PILE.get())) {
            return ItemStack.EMPTY;
        }
        int layers = state.getValue(SeedPileBlock.LAYERS);
        level.setBlock(pile, layers <= 1 ? Blocks.AIR.defaultBlockState() : state.setValue(SeedPileBlock.LAYERS, layers - 1), 3);
        richBread = Math.min(richBread, bread);
        boolean rich = richBread > 0 && (wantRich || bread <= richBread);
        bread = Math.max(0, bread - 1);
        if (rich) {
            richBread--;
        }
        return new ItemStack(rich ? ModItems.RICH_ANT_BREAD.get() : ModItems.ANT_BREAD.get());
    }

    /**
     * A worker gives the queen one piece of bread. With {@link #QUEEN_MEAL} pieces she starts carrying an egg, which
     * comes out {@link #INCUBATION_TICKS} later. False if she takes no more right now (the worker keeps the bread).
     */
    public boolean feedQueen(ServerLevel level, boolean rich) {
        if (eggDue >= 0 || queenBread >= QUEEN_MEAL) {
            return false;
        }
        if (rich) {
            // Rich bread: with RICH_MEAL pieces a burst of eggs starts, one every BURST_INTERVAL_TICKS, and she needs no more food for it.
            if (++queenRich >= RICH_MEAL) {
                queenRich = 0;
                queenBread = 0;
                burstLeft = BURST_EGGS;
                eggDue = level.getGameTime() + BURST_INTERVAL_TICKS;
            }
            return true;
        }
        queenBread++;
        if (queenBread >= QUEEN_MEAL) {
            eggDue = level.getGameTime() + INCUBATION_TICKS;
        }
        return true;
    }

    /** Whether the egg the queen carries is ready to come out. */
    public boolean isEggDue(ServerLevel level) {
        if (eggDue < 0 || level.getGameTime() < eggDue) {
            return false;
        }
        return burstLeft == 0 || countBrood(level) < MAX_EGGS; // a burst egg waits while the nursery is full of young
    }

    /** The egg comes out of the queen at this spot; she starts over with an empty stomach. */
    public void layEgg(ServerLevel level, double x, double y, double z) {
        eggsLaid++;
        if (burstLeft > 0) {
            // A burst egg: the next one is due a minute later, until the burst is over.
            burstLeft--;
            eggDue = burstLeft > 0 ? level.getGameTime() + BURST_INTERVAL_TICKS : -1;
        } else {
            queenBread = 0;
            eggDue = -1;
        }
        spawnEgg(level, x, y, z);
    }

    /** Puts an egg of this colony into the world. */
    public AntEggEntity spawnEgg(Level level, double x, double y, double z) {
        AntEggEntity egg = new AntEggEntity(ModEntities.ANT_EGG.get(), level);
        egg.setColonyId(id);
        egg.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(egg);
        return egg;
    }

    /** The area (around the nest, down to the refuse chamber) where the colony's eggs, larvae and dead ants are looked for. */
    private AABB nestArea() {
        return new AABB(center).inflate(FOOD_RADIUS, 0, FOOD_RADIUS).expandTowards(0, 8, 0).expandTowards(0, -12, 0);
    }

    /** All the eggs of this colony around the nest: in the nursery, lying where the queen laid them, or dropped on the way. */
    public List<AntEggEntity> getEggs(Level level) {
        return level.getEntitiesOfClass(AntEggEntity.class, nestArea(), egg -> egg.isAlive() && id.equals(egg.getColonyId()));
    }

    public int countEggs(Level level) {
        return getEggs(level).size();
    }

    /** The eggs lying in the nursery. */
    public List<AntEggEntity> getNurseryEggs(Level level) {
        Set<BlockPos> cells = new HashSet<>(getNurseryCells(level));
        return getEggs(level).stream().filter(egg -> cells.contains(egg.blockPosition())).toList();
    }

    /** The eggs that no worker has put in its place yet (not in the nursery and not set down by a worker). */
    public List<AntEggEntity> getLooseEggs(Level level) {
        Set<BlockPos> cells = new HashSet<>(getNurseryCells(level));
        return getEggs(level).stream().filter(egg -> !egg.isPlaced() && !cells.contains(egg.blockPosition())).toList();
    }

    /** Whether this block is a floor cell of the nursery (so an egg lying there is being kept warm and will hatch). */
    public boolean isInNursery(Level level, BlockPos pos) {
        return getNurseryCells(level).contains(pos);
    }

    /** The egg hatches: a larva takes its place (in the same spot). */
    public boolean hatchEgg(ServerLevel level, AntEggEntity egg) {
        AntLarvaEntity larva = new AntLarvaEntity(ModEntities.ANT_LARVA.get(), level);
        larva.setColonyId(id);
        larva.moveTo(egg.getX(), egg.getY(), egg.getZ(), egg.getYRot(), 0.0F);
        level.addFreshEntity(larva);
        egg.discard();
        return true;
    }

    /** Puts a larva of this colony into the world (used by the tests). */
    public AntLarvaEntity spawnLarva(Level level, double x, double y, double z) {
        AntLarvaEntity larva = new AntLarvaEntity(ModEntities.ANT_LARVA.get(), level);
        larva.setColonyId(id);
        larva.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(larva);
        return larva;
    }

    /** All the larvae of this colony around the nest. */
    public List<AntLarvaEntity> getLarvae(Level level) {
        return level.getEntitiesOfClass(AntLarvaEntity.class, nestArea(), larva -> larva.isAlive() && id.equals(larva.getColonyId()));
    }

    public int countLarvae(Level level) {
        return getLarvae(level).size();
    }

    /** Eggs and larvae together. */
    public int countBrood(Level level) {
        return countEggs(level) + countLarvae(level);
    }

    /** The cells that eggs and larvae lie in (nothing else is put there). */
    private Set<BlockPos> getBroodCells(Level level) {
        Set<BlockPos> cells = new HashSet<>();
        for (AntEggEntity egg : getEggs(level)) {
            cells.add(egg.blockPosition());
        }
        for (AntLarvaEntity larva : getLarvae(level)) {
            cells.add(larva.blockPosition());
        }
        return cells;
    }

    /** How many more pieces of bread all the larvae together still have to eat. */
    public int larvaHunger(Level level) {
        int hunger = 0;
        for (AntLarvaEntity larva : getLarvae(level)) {
            hunger += Math.max(0, LARVA_MEAL - larva.getFed());
        }
        return hunger;
    }

    /**
     * A larva that still has to eat and that no other nurse is on its way to, or null. The nurse {@code ant} is entered
     * as its feeder.
     */
    @Nullable
    public AntLarvaEntity claimHungryLarva(ServerLevel level, UUID ant) {
        long now = level.getGameTime();
        List<AntLarvaEntity> hungry = new ArrayList<>();
        for (AntLarvaEntity larva : getLarvae(level)) {
            if (!larva.isFull() && !larva.hasFeeder(now)) {
                hungry.add(larva);
            }
        }
        if (hungry.isEmpty()) {
            return null;
        }
        AntLarvaEntity larva = hungry.get(level.getRandom().nextInt(hungry.size()));
        larva.claim(ant, now + LARVA_CLAIM_TICKS);
        return larva;
    }

    /**
     * The larva has eaten enough: a new worker takes its place and joins the colony. False (the larva stays) while the
     * colony has {@link #MAX_WORKERS} workers.
     */
    public boolean hatchLarva(ServerLevel level, AntLarvaEntity larva) {
        if (getCount(WorkerAntEntity.CASTE) >= MAX_WORKERS) {
            return false;
        }
        WorkerAntEntity worker = ModEntities.WORKER_ANT.get().create(level);
        if (worker == null) {
            return false;
        }
        worker.moveTo(larva.getX(), larva.getY(), larva.getZ(), larva.getYRot(), 0.0F);
        worker.joinColony(this);
        level.addFreshEntity(worker);
        larva.discard();
        return true;
    }

    /**
     * A free spot for an egg, or null if there is none: in the nursery if any of it is dug out, otherwise (or when the
     * nursery is full) on the floor of the queen's room. The nursery is the preferred place, not a must.
     */
    @Nullable
    public BlockPos pickEggSpot(Level level, RandomSource random) {
        Set<BlockPos> taken = getBroodCells(level);
        for (List<BlockPos> cells : List.of(getNurseryCells(level), getHomeRoomCells(level))) {
            List<BlockPos> free = new ArrayList<>();
            for (BlockPos pos : cells) {
                if (!taken.contains(pos) && level.getBlockState(pos).isAir()) {
                    free.add(pos);
                }
            }
            if (!free.isEmpty()) {
                return free.get(random.nextInt(free.size()));
            }
        }
        return null;
    }

    /** A worker puts its egg down at {@code spot}. False if another egg lies there (the worker looks for another spot). */
    public boolean placeEgg(Level level, BlockPos spot) {
        if (!level.getBlockState(spot).isAir()) {
            return false;
        }
        if (getBroodCells(level).contains(spot)) {
            return false;
        }
        spawnEgg(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5).setPlaced(true);
        return true;
    }

    /**
     * The egg nearest to {@code near} that a worker should carry to its place, or null: only when there is a free spot
     * for it, not one that already lies in its place, not one another worker is on its way to. {@code avoid}: eggs
     * this worker found no way to.
     */
    @Nullable
    public AntEggEntity findLooseEgg(Level level, BlockPos near, Set<UUID> avoid) {
        if (pickEggSpot(level, level.getRandom()) == null) {
            return null;
        }
        long now = level.getGameTime();
        claimedEggs.values().removeIf(until -> until <= now);
        AntEggEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AntEggEntity egg : getLooseEggs(level)) {
            if (claimedEggs.containsKey(egg.getUUID()) || avoid.contains(egg.getUUID())) {
                continue;
            }
            double distance = egg.distanceToSqr(near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5);
            if (distance < bestDistance) {
                best = egg;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A worker is on its way to this egg: the others leave it alone for a while. */
    public void claimEgg(Level level, AntEggEntity egg) {
        claimedEggs.put(egg.getUUID(), level.getGameTime() + EGG_CLAIM_TICKS);
    }

    public void releaseEgg(AntEggEntity egg) {
        claimedEggs.remove(egg.getUUID());
    }

    // ---- Dead ants and the refuse chamber

    /** A dead ant that no worker brings to the refuse chamber crumbles to dust after this many ticks (5 minutes at normal speed). */
    public static final int CORPSE_DECAY_TICKS = 6000;
    /** How long (in ticks) a worker on its way to a dead ant keeps the others away from it. */
    private static final int CORPSE_CLAIM_TICKS = 1200;

    /** The floor cells of the refuse chamber that are dug out and can be walked to (the room does not have to be finished). */
    public List<BlockPos> getRefuseCells(Level level) {
        return getRoomCells(level, NestPlan.Kind.REFUSE);
    }

    /** Puts the body of a dead ant of this colony into the world, where it lies until a worker carries it away. */
    public AntCorpseEntity spawnCorpse(Level level, double x, double y, double z) {
        AntCorpseEntity corpse = new AntCorpseEntity(ModEntities.ANT_CORPSE.get(), level);
        corpse.setColonyId(id);
        corpse.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(corpse);
        return corpse;
    }

    /** All the dead ants of this colony around the nest: in the refuse chamber, or lying where they died. */
    public List<AntCorpseEntity> getCorpses(Level level) {
        return level.getEntitiesOfClass(AntCorpseEntity.class, nestArea(), corpse -> corpse.isAlive() && id.equals(corpse.getColonyId()));
    }

    /** The dead ants that lie in the refuse chamber. */
    public List<AntCorpseEntity> getRefuseCorpses(Level level) {
        Set<BlockPos> cells = new HashSet<>(getRefuseCells(level));
        return getCorpses(level).stream().filter(corpse -> cells.contains(corpse.blockPosition())).toList();
    }

    /** Whether this block is a floor cell of the refuse chamber (so a body lying there is where it belongs and stays). */
    public boolean isInRefuseChamber(Level level, BlockPos pos) {
        return getRefuseCells(level).contains(pos);
    }

    /** A free floor cell of the refuse chamber (one body to a cell), or null if there is none (not dug yet, or full). */
    @Nullable
    public BlockPos pickCorpseSpot(Level level, RandomSource random) {
        Set<BlockPos> taken = new HashSet<>();
        for (AntCorpseEntity corpse : getCorpses(level)) {
            taken.add(corpse.blockPosition());
        }
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos pos : getRefuseCells(level)) {
            if (!taken.contains(pos) && level.getBlockState(pos).isAir()) {
                free.add(pos);
            }
        }
        return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
    }

    /** A worker sets its dead nestmate down at {@code spot}. False if another body lies there (the worker looks for another spot). */
    public boolean placeCorpse(Level level, BlockPos spot) {
        if (!level.getBlockState(spot).isAir()) {
            return false;
        }
        for (AntCorpseEntity corpse : getCorpses(level)) {
            if (corpse.blockPosition().equals(spot)) {
                return false;
            }
        }
        spawnCorpse(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5).setPlaced(true);
        return true;
    }

    /**
     * The dead ant nearest to {@code near} that a worker should carry to the refuse chamber, or null: only when there is
     * a free spot for it, not one that lies in the chamber already, not one another worker is on its way to.
     * {@code avoid}: bodies this worker found no way to.
     */
    @Nullable
    public AntCorpseEntity findLooseCorpse(Level level, BlockPos near, Set<UUID> avoid) {
        if (pickCorpseSpot(level, level.getRandom()) == null) {
            return null;
        }
        long now = level.getGameTime();
        claimedCorpses.values().removeIf(until -> until <= now);
        Set<BlockPos> chamber = new HashSet<>(getRefuseCells(level));
        AntCorpseEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AntCorpseEntity corpse : getCorpses(level)) {
            if (corpse.isPlaced() || chamber.contains(corpse.blockPosition()) || claimedCorpses.containsKey(corpse.getUUID())
                    || avoid.contains(corpse.getUUID())) {
                continue;
            }
            double distance = corpse.distanceToSqr(near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5);
            if (distance < bestDistance) {
                best = corpse;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A worker is on its way to this dead ant: the others leave it alone for a while. */
    public void claimCorpse(Level level, AntCorpseEntity corpse) {
        claimedCorpses.put(corpse.getUUID(), level.getGameTime() + CORPSE_CLAIM_TICKS);
    }

    public void releaseCorpse(AntCorpseEntity corpse) {
        claimedCorpses.remove(corpse.getUUID());
    }

    /** The rooms this nest is being dug with (empty until the layout is decided). For tests and debugging. */
    public List<NestPlan.Chamber> getPlannedChambers(Level level) {
        Layout layout = getLayout(level);
        return layout == null ? List.of() : layout.chambers();
    }

    /** Every block that is part of the nest plan: the shaft, and the rooms once their layout is decided. */
    private Set<BlockPos> nestCells(Level level) {
        Set<BlockPos> cells = new HashSet<>();
        int depth = shaftDepth(level);
        for (int i = 1; i <= depth; i++) {
            cells.add(center.below(i));
        }
        BlockPos bottom = center.below(depth);
        if (level.isLoaded(bottom) && level.getBlockState(bottom).isAir()) {
            Layout layout = getLayout(level);
            if (layout != null) {
                cells.addAll(layout.cells());
            }
        }
        return cells;
    }

    /**
     * Every opening in the walls of the dug-out nest: any open block (air, water, lava, grass) that touches a dug
     * nest cell but is not itself part of the nest. Caves, cracks and the gaps between rooms all show up here.
     * The entrance hole above the shaft is the one opening that is allowed. For tests, and for {@link #findLeak}.
     */
    public List<Leak> getLeaks(Level level) {
        Set<BlockPos> nest = nestCells(level);
        Set<BlockPos> seen = new HashSet<>();
        List<Leak> leaks = new ArrayList<>();
        for (BlockPos cell : nest) {
            if (!isDugOut(level, cell)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos hole = cell.relative(direction);
                if (nest.contains(hole) || hole.equals(center) || !seen.add(hole) || !isOpen(level, hole)) {
                    continue;
                }
                leaks.add(new Leak(hole, findPlugSpot(level, nest, hole)));
            }
        }
        return leaks;
    }

    /**
     * The leak an ant at {@code near} should work on, or null: the closest one that can be reached, but one nobody
     * is at yet beats a crowded one a little closer, so every leak gets an ant before any gets many.
     */
    @Nullable
    public Leak findLeak(Level level, BlockPos near) {
        long now = level.getGameTime();
        for (Map<UUID, Long> workers : claimedLeaks.values()) {
            workers.values().removeIf(until -> until <= now);
        }
        claimedLeaks.values().removeIf(Map::isEmpty);
        Leak best = null;
        double bestScore = Double.MAX_VALUE;
        for (Leak leak : getLeaks(level)) {
            int workers = claimedLeaks.getOrDefault(leak.hole(), Map.of()).size();
            if (leak.stand() == null || workers >= MAX_WORKERS_PER_LEAK) {
                continue;
            }
            double score = Math.sqrt(leak.stand().distSqr(near)) + workers * CROWD_PENALTY;
            if (score < bestScore) {
                best = leak;
                bestScore = score;
            }
        }
        return best;
    }

    /** Tells the other ants that this one is working on the leak (for a while, or until {@link #releaseLeak}). */
    public void claimLeak(Level level, BlockPos hole, UUID ant) {
        claimedLeaks.computeIfAbsent(hole.immutable(), key -> new HashMap<>()).put(ant, level.getGameTime() + LEAK_CLAIM_TICKS);
    }

    /** The ant stops working on the leak (finished, or gave up). */
    public void releaseLeak(BlockPos hole, UUID ant) {
        Map<UUID, Long> workers = claimedLeaks.get(hole);
        if (workers != null) {
            workers.remove(ant);
        }
    }

    /** One ant's pack of soil: adds its share to the leak and returns the total so far (1.0 or more = enough to close it). */
    public float addSealProgress(BlockPos hole, float amount) {
        return sealProgress.merge(hole.immutable(), amount, Float::sum);
    }

    /** True while this block is still open space (so the leak is still there). */
    public boolean isLeakOpen(Level level, BlockPos hole) {
        return isOpen(level, hole);
    }

    /** Fills a leak with burrowed dirt. False if it is already closed or someone is standing in it (try again later). */
    public boolean plugLeak(Level level, BlockPos hole) {
        if (hole.equals(center) || !isOpen(level, hole)) {
            return false;
        }
        // Never bury anyone, same as with the hill.
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(hole)).isEmpty()) {
            return false;
        }
        level.setBlock(hole, ModBlocks.BURROWED_DIRT.get().defaultBlockState(), 3);
        claimedLeaks.remove(hole);
        sealProgress.remove(hole);
        return true;
    }

    /**
     * Open space that soil can be packed into: air, water, lava, grass and the like. Anything else (stone, a
     * chest, a flower someone planted) is left alone. A chunk that is not loaded counts as closed.
     */
    private static boolean isOpen(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    /**
     * The dug-out nest cell closest to the hole (within one block of it, corners included) where an ant can work on it,
     * or null. A cell with a floor is preferred; failing that, a cell with a solid wall next to it that the ant can
     * cling to (the shaft and the middle of a stair have no floor), so a hole next to the top of the shaft can be closed too.
     */
    @Nullable
    private static BlockPos findPlugSpot(Level level, Set<BlockPos> nest, BlockPos hole) {
        BlockPos onFloor = closestPlugSpot(level, nest, hole, true);
        return onFloor != null ? onFloor : closestPlugSpot(level, nest, hole, false);
    }

    @Nullable
    private static BlockPos closestPlugSpot(Level level, Set<BlockPos> nest, BlockPos hole, boolean needFloor) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos spot = hole.offset(dx, dy, dz);
                    if (!nest.contains(spot) || !level.isLoaded(spot) || !level.getBlockState(spot).isAir()
                            || !(needFloor ? hasFloor(level, spot) : hasWallToClingTo(level, spot))) {
                        continue;
                    }
                    double distance = hole.distSqr(spot);
                    if (distance < bestDistance) {
                        best = spot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static boolean hasFloor(Level level, BlockPos spot) {
        return level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP);
    }

    private static boolean hasWallToClingTo(Level level, BlockPos spot) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos wall = spot.relative(side);
            if (level.getBlockState(wall).isFaceSturdy(level, wall, side.getOpposite())) {
                return true;
            }
        }
        return false;
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

    /** The layout of the rooms: worked out the first time it is needed, then kept. Null = wait (chunk not loaded) or no room. */
    @Nullable
    private Layout getLayout(Level level) {
        if (layout == null) {
            layout = chooseLayout(level);
        }
        return layout;
    }

    /** How deep the entrance shaft goes for this nest: the depth of the room plan (the normal depth until it is known). */
    private int shaftDepth(Level level) {
        Layout planned = getLayout(level);
        return planned == null ? SHAFT_DEPTH : planned.shaftDepth();
    }

    /**
     * Picks how deep the nest sits, which side the rooms lie toward and how many levels it has, by looking at the
     * ground: a depth only counts if the whole shaft is soil, and a nest only counts if every stair, tunnel and
     * room of its plan is soil (no stone or water in the way). The nest with the most levels wins, the deeper one on
     * a tie. Null while chunks are missing, or when there is no room at all.
     */
    @Nullable
    private Layout chooseLayout(Level level) {
        // Everything the plan could touch must be loaded, or the answer would change later.
        for (int depth = SHAFT_DEPTH; depth >= MIN_SHAFT_DEPTH; depth--) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (NestPlan.Chamber chamber : NestPlan.chambers(center.below(depth), direction, NestPlan.MAX_LEVELS)) {
                    for (BlockPos pos : chamber.cells()) {
                        if (!level.isLoaded(pos)) {
                            return null;
                        }
                    }
                }
            }
        }
        Layout best = null;
        for (int depth = SHAFT_DEPTH; depth >= MIN_SHAFT_DEPTH; depth--) {
            Layout found = chooseLayout(level, center.below(depth), depth);
            // The depth with the most levels wins; on a tie the deeper one (it is tried first).
            if (found != null && (best == null || found.chambers().size() > best.chambers().size())) {
                best = found;
            }
        }
        return best;
    }

    /** The best layout with the shaft {@code depth} blocks deep (all the chunks involved are loaded), or null if the shaft or the first level does not fit. */
    @Nullable
    private Layout chooseLayout(Level level, BlockPos bottom, int depth) {
        List<BlockPos> shaft = new ArrayList<>();
        for (int i = 1; i <= depth; i++) {
            shaft.add(center.below(i));
        }
        if (!allSoilOrAir(level, shaft)) {
            return null; // stone in the way of the shaft itself
        }
        int start = Math.floorMod(id.hashCode(), 4);
        Layout best = null;
        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.from2DDataValue((start + i) % 4);
            // The most levels whose whole plan is soil. A shorter nest uses its levels differently (and its last one is
            // always the small queen's room), so each length is checked as the plan it really is.
            for (int levels = NestPlan.MAX_LEVELS; levels >= 1; levels--) {
                List<NestPlan.Chamber> plan = NestPlan.chambers(bottom, direction, levels);
                if (best != null && levels <= best.chambers().size()) {
                    break; // not better than what is already found
                }
                boolean fits = true;
                for (NestPlan.Chamber chamber : plan) {
                    if (!allSoilOrAir(level, chamber.cells())) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    best = new Layout(plan, depth);
                    break;
                }
            }
        }
        return best;
    }

    private static boolean allSoilOrAir(Level level, List<BlockPos> cells) {
        for (BlockPos pos : cells) {
            BlockState state = level.getBlockState(pos);
            if (!(state.is(ModTags.DIGGABLE) || state.isAir()) || touchesFluid(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** Open space next to a block where an ant can stand to bite it: above first, then the four sides. */
    @Nullable
    public static BlockPos findStandSpot(Level level, BlockPos target) {
        List<BlockPos> spots = findStandSpots(level, target);
        return spots.isEmpty() ? null : spots.get(0);
    }

    /**
     * All the open cells next to a block where an ant could stand to bite it, best first (above, then the four
     * sides). Not every one can be walked to: a sealed-off cave pocket next to the block is open space too, so
     * an ant tries them one after the other until it finds a route.
     */
    public static List<BlockPos> findStandSpots(Level level, BlockPos target) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos above = target.above();
        if (level.getBlockState(above).isAir()) {
            spots.add(above);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = target.relative(side);
            if (level.getBlockState(pos).isAir()) {
                spots.add(pos);
            }
        }
        return spots;
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
     * A spot on the surface near the entrance where an ant drops its soil pellet, or null when the hill
     * is complete. The hill is built from the bottom up, layer by layer, so every pellet rests on another
     * and the finished mound has exactly the shape of {@link #moundHeightAt}.
     */
    @Nullable
    public BlockPos pickMoundSpot(Level level, RandomSource random) {
        List<BlockPos> spots = openMoundSpots(level);
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
        return lowestSpots.get(random.nextInt(lowestSpots.size()));
    }

    /** False once the hill is complete: ants then stop carrying soil out. */
    public boolean hasMoundRoom(Level level) {
        return !openMoundSpots(level).isEmpty();
    }

    /**
     * Whether a worker should fetch soil to mend the hill now: the nest is dug (until then the digging makes the soil),
     * the hill has a gap that no pellet is on its way to, and a repair has not just failed.
     */
    public boolean needsHillRepair(ServerLevel level) {
        long now = level.getGameTime();
        repairers.values().removeIf(until -> until <= now);
        if (now < hillPausedUntil || repairers.size() >= MAX_REPAIRERS) {
            return false;
        }
        int gaps = openMoundSpots(level).size();
        return gaps > repairers.size() && findDigTarget(level) == null;
    }

    /** A worker has set out for repair soil: it counts as a pellet on its way until it should have arrived. */
    public void claimHillRepair(ServerLevel level, UUID ant) {
        repairers.put(ant, level.getGameTime() + REPAIR_CLAIM_TICKS);
    }

    /** A soil pellet could not be brought to the hill (no route to the gap): wait a while before fetching more for repairs. */
    public void pauseHillRepair(Level level) {
        hillPausedUntil = level.getGameTime() + HILL_PAUSE_TICKS;
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
            if (level.getBlockState(new BlockPos(gap.getX(), y, gap.getZ())).is(ModBlocks.BURROWED_DIRT.get())) {
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
        tag.putInt("food", food);
        tag.putInt("queenBread", queenBread);
        tag.putInt("bread", bread);
        tag.putInt("richSeeds", richSeeds);
        tag.putInt("richBread", richBread);
        tag.putInt("queenRich", queenRich);
        tag.putInt("burstLeft", burstLeft);
        tag.putLong("eggDue", eggDue);
        tag.putInt("eggsLaid", eggsLaid);
        CompoundTag clips = new CompoundTag();
        grassClips.forEach((pos, count) -> clips.putInt(Long.toString(pos.asLong()), count));
        tag.put("grassClips", clips);
        return tag;
    }

    public static Colony load(CompoundTag tag) {
        Colony colony = new Colony(
                tag.getUUID("id"),
                ResourceLocation.parse(tag.getString("species")),
                tag.getUUID("queen"),
                BlockPos.of(tag.getLong("center")));
        colony.food = tag.getInt("food");
        colony.queenBread = tag.contains("queenBread") ? tag.getInt("queenBread") : tag.getInt("queenSeeds"); // (old saves: seeds)
        colony.bread = tag.getInt("bread");
        colony.richSeeds = tag.getInt("richSeeds");
        colony.richBread = tag.getInt("richBread");
        colony.queenRich = tag.getInt("queenRich");
        colony.burstLeft = tag.getInt("burstLeft");
        colony.eggDue = tag.contains("eggDue") ? tag.getLong("eggDue") : -1;
        colony.eggsLaid = tag.getInt("eggsLaid");
        CompoundTag clips = tag.getCompound("grassClips");
        for (String key : clips.getAllKeys()) {
            colony.grassClips.put(BlockPos.of(Long.parseLong(key)), clips.getInt(key));
        }
        CompoundTag castes = tag.getCompound("castes");
        for (String key : castes.getAllKeys()) {
            colony.casteCounts.put(key, castes.getInt(key));
        }
        return colony;
    }
}
