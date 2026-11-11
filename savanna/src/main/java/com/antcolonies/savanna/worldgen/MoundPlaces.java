package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

import com.antcolonies.savanna.SavannaMod;

/**
 * Where the termite mounds of new land go (M8 step 3F, F1; {@code docs/plans/m8-matabele.md}): at random with a safety
 * gap, living and dead alike, as real mound fields are spread evenly but not in rows (neighbouring colonies fight over
 * the ground between them).
 *
 * <p><b>The places.</b> Every chunk offers one candidate place (its middle {@link TermiteMoundStructure#middle}) with
 * its own <i>territory</i>, 21 to 41 blocks (my picks): two places stand at least their two territories apart, so two
 * mounds land 42 to 82 blocks apart, mostly round 60 (the clustering the user asked for: a typical spacing with a
 * spread). A big territory is a big mound ({@link #sizeQuantile}): bigger mounds lean to the far end, small young ones to
 * the near end (real: a bigger colony holds more ground). Which candidates win is fixed by the world's seed alone: each
 * has a rank, and a candidate is a place unless a higher-ranked place is too near it (worked out the same everywhere, so a
 * tree or a Matabele colony can ask where the mounds go without making them). The land does not enter the pattern, so
 * the pattern is cheap and the same for every caller.
 *
 * <p><b>The ground's chance.</b> A place holds a mound only by chance, by the ground ({@link #holds}): real mound numbers
 * follow the soil, likely on flat, open, well-drained ground, less likely on a slope or by water; never in water, on a
 * steep slope or outside a savanna. On Minecraft's rolling savanna about 1 place in 3 (the 2 in 3 first planned was a
 * flat-ground guess, BUGS 104; dead mounds are spread out among living ones by {@link #staysDead}). The user's third ground, giant trees, is
 * left out: the trees keep off the mounds' places themselves ({@link TermiteMoundStructure#crowds}), so asking the trees
 * would go round in a circle. Next to an empty place a mound leans bigger (real: its colony takes over the free ground).
 */
public final class MoundPlaces {
    /** The mounds' structure (the server asks for its numbers and biomes, F4). */
    public static final ResourceKey<Structure> STRUCTURE = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termite_mound"));

    /**
     * The pattern's and the ground's numbers (the structure's file, {@code numbers.places}; my picks, to confirm).
     * {@code territoryMin}, {@code territoryMax}: a place's territory (blocks), two places at least their two territories
     * apart. {@code groundChance}: a place's chance of a mound on flat, dry ground. {@code slopeCut}: the chance lost on
     * the steepest ground a mound takes ({@code maxUneven}, the most the ground may rise and fall 8 blocks round the middle). {@code waterCut}: the chance lost
     * with water within {@code waterLook} blocks. {@code emptyLean}: next to each empty place within {@code leanReach},
     * a mound's size quantile's rest is times this (leaning bigger). {@code lowGround}: no mound where the ground stands
     * this many blocks or less over the water (savanna rivers, 2026-11-07, my pick from the real termites: Macrotermes
     * need ground that drains, and the floodplain's lowest ground floods; those near rivers stand on raised ground).
     */
    public record Numbers(double territoryMin, double territoryMax, double groundChance, double slopeCut, double waterCut,
            int waterLook, double emptyLean, int leanReach, int maxUneven, int lowGround) {
        public static final Codec<Numbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("territory_min", 21.0).forGetter(Numbers::territoryMin),
                Codec.DOUBLE.optionalFieldOf("territory_max", 41.0).forGetter(Numbers::territoryMax),
                Codec.DOUBLE.optionalFieldOf("ground_chance", 0.8).forGetter(Numbers::groundChance),
                Codec.DOUBLE.optionalFieldOf("slope_cut", 0.5).forGetter(Numbers::slopeCut),
                Codec.DOUBLE.optionalFieldOf("water_cut", 0.6).forGetter(Numbers::waterCut),
                Codec.INT.optionalFieldOf("water_look", 14).forGetter(Numbers::waterLook),
                Codec.DOUBLE.optionalFieldOf("empty_lean", 0.75).forGetter(Numbers::emptyLean),
                Codec.INT.optionalFieldOf("lean_reach", 90).forGetter(Numbers::leanReach),
                Codec.INT.optionalFieldOf("max_uneven", 6).forGetter(Numbers::maxUneven),
                Codec.INT.optionalFieldOf("low_ground", 2).forGetter(Numbers::lowGround)
        ).apply(i, Numbers::new));

        public static final Numbers DEFAULT = new Numbers(21, 41, 0.8, 0.5, 0.6, 14, 0.75, 90, 6, 2);

        /** These numbers with another {@link #maxUneven} (the survey game test's before and after, BUGS 104). */
        public Numbers withUneven(int uneven) {
            return new Numbers(territoryMin, territoryMax, groundChance, slopeCut, waterCut, waterLook, emptyLean, leanReach, uneven, lowGround);
        }
    }

    /** One place of the pattern: its chunk, middle (no height), territory (blocks), rank and its size quantile (0 to 1). */
    public record Place(ChunkPos chunk, BlockPos middle, double territory, double rank, double size) {
        /** The place's own dice (its mound's pick). */
        public long seed(long worldSeed) {
            return mix(worldSeed, chunk.x, chunk.z, 7);
        }
    }

    /**
     * The land a place's ground is read from: world generation's (a structure being made) or the server's (F4 asking for
     * the living mounds round a dead one). {@code biomes} null: any biome.
     */
    public record Land(long seed, ChunkGenerator generator, LevelHeightAccessor heights, RandomState random, HolderSet<Biome> biomes) {
        public static Land of(ServerLevel level, HolderSet<Biome> biomes) {
            return new Land(level.getSeed(), level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState(), biomes);
        }

        /** The ground's height map at (x, z) (public for the new-land survey test, test revamp R6). */
        public int height(int x, int z, Heightmap.Types type) {
            return GroundHeights.at(generator, x, z, type, heights, random);
        }
    }

    /** Above this many remembered answers the memory is emptied (about 30 MB at most). */
    private static final int MEMORY_CAP = 1 << 19;
    /** A chain of higher-ranked neighbours deeper than this is cut off (never seen: the deepest in the model is about 10). */
    private static final int DEEPEST = 200;
    private static final ConcurrentHashMap<Long, Boolean> ACCEPTED = new ConcurrentHashMap<>();
    private static volatile long memorySeed;
    private static volatile Numbers memoryNumbers;
    /** {@link #staysDead}'s and its first rule's remembered answers, by chunk, for one seed, numbers, dead share and distances. */
    private static final ConcurrentHashMap<Long, Boolean> STAYS_DEAD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> SPREAD = new ConcurrentHashMap<>();
    private static volatile String staysDeadFor = "";

    private MoundPlaces() {
    }

    static long mix(long seed, int cx, int cz, int salt) {
        long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The candidate of a chunk (every chunk has one; most are not places). */
    public static Place candidate(long seed, int cx, int cz, Numbers numbers) {
        ChunkPos chunk = new ChunkPos(cx, cz);
        double size = unit(mix(seed, cx, cz, 3));
        double territory = numbers.territoryMin() + (numbers.territoryMax() - numbers.territoryMin()) * size;
        return new Place(chunk, TermiteMoundStructure.middle(seed, chunk), territory, unit(mix(seed, cx, cz, 4)), size);
    }

    /** Whether a ranks above b (ties, never in practice, by the chunk). */
    private static boolean above(Place a, Place b) {
        if (a.rank() != b.rank()) {
            return a.rank() > b.rank();
        }
        return a.chunk().x != b.chunk().x ? a.chunk().x > b.chunk().x : a.chunk().z > b.chunk().z;
    }

    /** Whether the chunk's candidate is a place of the pattern: no higher-ranked place within their two territories. */
    public static boolean isPlace(long seed, int cx, int cz, Numbers numbers) {
        if (memorySeed != seed || memoryNumbers != numbers) {
            ACCEPTED.clear();
            memorySeed = seed;
            memoryNumbers = numbers;
        }
        if (ACCEPTED.size() > MEMORY_CAP) {
            ACCEPTED.clear();
        }
        return isPlace(seed, cx, cz, numbers, 0);
    }

    private static boolean isPlace(long seed, int cx, int cz, Numbers numbers, int depth) {
        long key = ChunkPos.asLong(cx, cz);
        Boolean known = ACCEPTED.get(key);
        if (known != null) {
            return known;
        }
        Place place = candidate(seed, cx, cz, numbers);
        List<Place> higher = new ArrayList<>();
        int reach = (int) Math.ceil(2 * numbers.territoryMax() / 16) + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Place other = candidate(seed, cx + dx, cz + dz, numbers);
                double apart = place.territory() + other.territory();
                if (above(other, place) && place.middle().distToLowCornerSqr(other.middle().getX(), 0, other.middle().getZ()) < apart * apart) {
                    higher.add(other);
                }
            }
        }
        higher.sort((a, b) -> Double.compare(b.rank(), a.rank()));
        boolean ok = true;
        if (depth < DEEPEST) {
            for (Place other : higher) {
                if (isPlace(seed, other.chunk().x, other.chunk().z, numbers, depth + 1)) {
                    ok = false;
                    break;
                }
            }
        }
        ACCEPTED.put(key, ok);
        return ok;
    }

    /** The places whose middles lie within {@code reach} blocks of (x, z). */
    public static List<Place> near(long seed, int x, int z, int reach, Numbers numbers) {
        List<Place> out = new ArrayList<>();
        for (int cx = (x - reach - 16) >> 4; cx <= (x + reach + 16) >> 4; cx++) {
            for (int cz = (z - reach - 16) >> 4; cz <= (z + reach + 16) >> 4; cz++) {
                Place p = candidate(seed, cx, cz, numbers);
                double dx = p.middle().getX() - x, dz = p.middle().getZ() - z;
                if (dx * dx + dz * dz <= (double) reach * reach && isPlace(seed, cx, cz, numbers)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /**
     * The foot of the mound at {@code place}, or null if it holds none: never in water, on the floodplain's low ground, on a steep slope or outside the
     * land's biomes; otherwise by the ground's chance (flat, dry ground likely; a slope or water near less so).
     */
    public static BlockPos holds(Land land, Place place, Numbers numbers) {
        int x = place.middle().getX(), z = place.middle().getZ();
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        int[][] around = {{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}, {6, 6}, {-6, 6}, {6, -6}, {-6, -6}};
        int look = numbers.waterLook();
        // every column this place reads, worked out side by side first (the same answers, sooner)
        int[] xs = new int[around.length + 4], zs = new int[around.length + 4];
        for (int i = 0; i < around.length; i++) {
            xs[i] = x + around[i][0];
            zs[i] = z + around[i][1];
        }
        int[][] shoreLook = {{look, 0}, {-look, 0}, {0, look}, {0, -look}};
        for (int i = 0; i < 4; i++) {
            xs[around.length + i] = x + shoreLook[i][0];
            zs[around.length + i] = z + shoreLook[i][1];
        }
        GroundHeights.warm(land.generator(), land.heights(), land.random(), xs, zs);
        for (int[] o : around) {
            int surface = land.height(x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG);
            if (surface != land.height(x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG)) {
                return null;
            }
            lowest = Math.min(lowest, surface);
            highest = Math.max(highest, surface);
            if (highest - lowest > numbers.maxUneven()) {
                return null;
            }
        }
        int uneven = highest - lowest;
        BlockPos foot = new BlockPos(x, land.height(x, z, Heightmap.Types.WORLD_SURFACE_WG), z);
        // the floodplain's lowest ground (the foot is the first air, so foot - sea level is the ground over the water)
        if (foot.getY() - land.generator().getSeaLevel() <= numbers.lowGround()) {
            return null;
        }
        if (land.biomes() != null) {
            Holder<Biome> biome = land.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(foot.getY()),
                    QuartPos.fromBlock(z), land.random().sampler());
            if (!land.biomes().contains(biome)) {
                return null;
            }
        }
        double chance = numbers.groundChance() * (1 - numbers.slopeCut() * uneven / numbers.maxUneven());
        int w = numbers.waterLook();
        int[][] shore = {{w, 0}, {-w, 0}, {0, w}, {0, -w}};
        for (int[] o : shore) {
            if (land.height(x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG) != land.height(x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG)) {
                chance *= 1 - numbers.waterCut();
                break;
            }
        }
        return unit(mix(land.seed(), place.chunk().x, place.chunk().z, 5)) < chance ? foot : null;
    }

    /**
     * A mound's size quantile (0 small to 1 big, its stage drawn at it): its place's territory, leaning bigger next to
     * each empty place within {@code leanReach} (its colony took over the free ground).
     */
    public static double sizeQuantile(Land land, Place place, Numbers numbers) {
        int empty = 0;
        for (Place other : near(land.seed(), place.middle().getX(), place.middle().getZ(), numbers.leanReach(), numbers)) {
            if (!other.chunk().equals(place.chunk()) && holds(land, other, numbers) == null) {
                empty++;
            }
        }
        return 1 - (1 - place.size()) * Math.pow(numbers.emptyLean(), empty);
    }

    /** Whether the place's mound is a living colony by its own dice (a dead one too near another dead one lives: {@link #staysDead}). */
    public static boolean living(long seed, Place place, double deadShare) {
        return unit(mix(seed, place.chunk().x, place.chunk().z, 6)) >= deadShare;
    }

    /**
     * Whether the mound at {@code place} (its foot {@code foot}), dead by its dice, stays dead in new land. Two rules, so
     * every dead mound stands alone among living ones (real: Macrotermes mound fields are spread evenly by neighbours
     * fighting over the ground, and a colony that dies leaves living neighbours round its mound):
     * <ol>
     * <li><b>Spread out</b> (the user, 2026-11-07, dead mounds came in clumps of 2 or 3): of two dead by their dice within
     * {@code apart} blocks of each other (feet, sideways), only the one with the higher dead rank stays dead
     * ({@link #spreadStays}). Each mound flipping its own coin clumps: with 1 mound in 4 dead, about 3 dead mounds in 4 had
     * a dead neighbour.</li>
     * <li><b>Among the living</b> (BUGS 104, 162): a dead mound left with no living mound within {@code reach} lives
     * instead. On Minecraft's rolling savanna only about 1 place in 3 holds a mound, and without this nearly half the dead
     * mounds stood alone, empty of Matabele (no termites to raid).</li>
     * </ol>
     * Worked out by the seed and the land alone (the same answer for world making and the server's check); the second rule
     * only reads the first, so nothing goes round in a circle, and every mound it leaves dead has a living one in reach.
     * {@code apart} 0: no spreading.
     */
    public static boolean staysDead(Land land, Place place, BlockPos foot, Numbers numbers, double deadShare, int apart, int reach) {
        String key = land.seed() + "/" + System.identityHashCode(numbers) + "/" + deadShare + "/" + apart + "/" + reach;
        if (!key.equals(staysDeadFor)) {
            SPREAD.clear();
            STAYS_DEAD.clear();
            staysDeadFor = key;
        }
        if (SPREAD.size() > MEMORY_CAP || STAYS_DEAD.size() > MEMORY_CAP) {
            SPREAD.clear();
            STAYS_DEAD.clear();
        }
        Boolean known = STAYS_DEAD.get(place.chunk().toLong());
        if (known != null) {
            return known;
        }
        boolean stays = spreadStays(land, place, foot, numbers, deadShare, apart, 0)
                && livingAfterSpreadingNear(land, place, foot, numbers, deadShare, apart, reach);
        STAYS_DEAD.put(place.chunk().toLong(), stays);
        return stays;
    }

    /**
     * Rule 1 of {@link #staysDead}: no other mound dead by its dice and kept by this rule, with a higher dead rank, stands
     * within {@code apart} of the foot. Down the ranks only, so it never goes round in a circle.
     */
    private static boolean spreadStays(Land land, Place place, BlockPos foot, Numbers numbers, double deadShare, int apart, int depth) {
        if (apart <= 0) {
            return true;
        }
        long key = place.chunk().toLong();
        Boolean known = SPREAD.get(key);
        if (known != null) {
            return known;
        }
        boolean stays = true;
        if (depth < DEEPEST) {
            double rank = deadRank(land.seed(), place);
            for (Place other : near(land.seed(), foot.getX(), foot.getZ(), apart, numbers)) {
                if (other.chunk().equals(place.chunk()) || living(land.seed(), other, deadShare)) {
                    continue;
                }
                double otherRank = deadRank(land.seed(), other);
                boolean higher = otherRank != rank ? otherRank > rank
                        : other.chunk().x != place.chunk().x ? other.chunk().x > place.chunk().x : other.chunk().z > place.chunk().z;
                if (!higher) {
                    continue;
                }
                BlockPos at = holds(land, other, numbers);
                if (at == null) {
                    continue;
                }
                double dx = at.getX() - foot.getX(), dz = at.getZ() - foot.getZ();
                if (dx * dx + dz * dz <= (double) apart * apart && spreadStays(land, other, at, numbers, deadShare, apart, depth + 1)) {
                    stays = false;
                    break;
                }
            }
        }
        SPREAD.put(key, stays);
        return stays;
    }

    /**
     * Rule 2 of {@link #staysDead}: a mound living after rule 1 (living by its dice, or dead by them and given up by the
     * spreading) stands with its foot within {@code reach} of {@code foot}.
     */
    private static boolean livingAfterSpreadingNear(Land land, Place place, BlockPos foot, Numbers numbers, double deadShare, int apart, int reach) {
        for (Place other : near(land.seed(), foot.getX(), foot.getZ(), reach, numbers)) {
            if (other.chunk().equals(place.chunk())) {
                continue;
            }
            BlockPos at = holds(land, other, numbers);
            if (at == null) {
                continue;
            }
            double dx = at.getX() - foot.getX(), dz = at.getZ() - foot.getZ();
            if (dx * dx + dz * dz <= (double) reach * reach
                    && (living(land.seed(), other, deadShare) || !spreadStays(land, other, at, numbers, deadShare, apart, 0))) {
                return true;
            }
        }
        return false;
    }

    /** A place's own rank among dead mounds (its own dice, apart from the pattern's rank, so the two never line up). */
    private static double deadRank(long seed, Place place) {
        return unit(mix(seed, place.chunk().x, place.chunk().z, 8));
    }

    /** About how far a mound's foot reaches from its middle at a stage (a full one about 12, its washed soil 13). */
    public static double footReach(int stage) {
        return 3 + 9.0 * stage / TermiteMoundStructure.FULL_STAGE;
    }
}
