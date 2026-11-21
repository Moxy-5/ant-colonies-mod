package com.antcolonies.savanna.worldgen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Where new land's crocodile nest sites go (world eggs step 1, 2026-11-20, {@code docs/plans/world-eggs.md}): one site
 * every 250 to 350 blocks of river (the user's numbers), always on a sand bank, as real Nile crocodiles bunch their
 * nests on the few good sandbanks.
 *
 * <p>Worked out from the seed and the land's noise alone, the same whichever chunk is made first (as
 * {@link MoundPlaces}): a chunk is a <i>candidate</i> when its middle is savanna water by the biome noise and a sand bank
 * lies beside it ({@link SandBanks}: the strongest of five readings round its middle at least {@link #SAND}); each
 * candidate has a rank from the seed; a candidate is a <i>site</i> when no site of higher rank stands within
 * {@link #APART} blocks. So sites stand at least that far apart, and a whole bend's sand bank counts once. The chunk
 * itself then finds its dry sand by the water and lays the nests ({@link CrocodileNestFeature}); a site whose chunk holds
 * none stays empty.
 */
public final class NestSites {
    /** No two sites nearer than this (blocks; my pick, measured by {@code crocodileNestSitesComeEvery250To350BlocksOfRiver}). */
    public static final int APART = 150;      // (200 gave one site per 705 blocks of river; 150: one per 306, gaps mostly 196 to 352)
    /** A sand bank's reading that makes a candidate (0 to 1; the bank's heart is 1). */
    static final double SAND = 0.8;
    /** How far round the chunk's middle the sand is read (blocks). */
    static final int READ = 8;

    private static final int CACHE_MOST = 200_000;
    private static final Map<Long, Boolean> CANDIDATES = new ConcurrentHashMap<>(), SITES = new ConcurrentHashMap<>();
    private static volatile long cachedSeed;

    private NestSites() {
    }

    /**
     * Whether the chunk {@code cx, cz} is a crocodile nest site: a candidate with no site of higher rank within
     * {@link #APART} (remembered per seed). (The first try, "no <i>candidate</i> of higher rank within", gave one site
     * per 1,082 blocks of river: a winding river puts dozens of candidates in that circle, so almost none won.)
     */
    public static boolean site(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        if (!candidate(biomes, sampler, seed, cx, cz)) {
            return false;
        }
        long key = (long) cx << 32 ^ (cz & 0xFFFFFFFFL);
        Boolean known = SITES.get(key);
        if (known != null) {
            return known;
        }
        long own = rank(seed, cx, cz);
        int r = (APART + 15) / 16;
        boolean is = true;
        for (int dx = -r; dx <= r && is; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0 || dx * dx + dz * dz > (APART / 16.0) * (APART / 16.0)) {
                    continue;
                }
                // a better-ranked site too near: this bank is not one (ranks only go up, so this always ends)
                if (rank(seed, cx + dx, cz + dz) > own && site(biomes, sampler, seed, cx + dx, cz + dz)) {
                    is = false;
                    break;
                }
            }
        }
        SITES.put(key, is);
        return is;
    }

    /** Whether the chunk is a candidate: savanna water at its middle, a sand bank beside it (remembered per seed). */
    static boolean candidate(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        if (cachedSeed != seed || CANDIDATES.size() > CACHE_MOST) {
            CANDIDATES.clear();
            SITES.clear();
            cachedSeed = seed;
        }
        long key = (long) cx << 32 ^ (cz & 0xFFFFFFFFL);
        Boolean known = CANDIDATES.get(key);
        if (known != null) {
            return known;
        }
        boolean is = work(biomes, sampler, seed, cx, cz);
        CANDIDATES.put(key, is);
        return is;
    }

    private static boolean work(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        int x = cx * 16 + 8, z = cz * 16 + 8;
        if (!biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y), QuartPos.fromBlock(z), sampler)
                .is(RiverBankFeature.WATERS)) {
            return false;
        }
        SandBanks.Chooser sand = SandBanks.over(sampler.weirdness(), sampler.continentalness(), seed, cx * 16, cz * 16);
        int[][] at = {{0, 0}, {READ, 0}, {-READ, 0}, {0, READ}, {0, -READ}};
        for (int[] d : at) {
            if (sand.at(x + d[0], z + d[1]).strength() >= SAND) {
                return true;
            }
        }
        return false;
    }

    /** A chunk's rank among candidates, from the seed alone. */
    static long rank(long seed, int cx, int cz) {
        long h = seed ^ 0x6E657374L ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ h >>> 33;
    }
}
