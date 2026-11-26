package com.antcolonies.savanna.worldgen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Sites along the savanna's rivers spaced apart, worked out from the seed and the land's noise alone, the same whichever
 * chunk is made first (as {@link MoundPlaces}): the crocodiles' nest sites ({@link NestSites}) and the hippos' pools
 * ({@link PodSites}) are each one of these. A chunk is a <i>candidate</i> by the kind's own test of the noise; each
 * candidate has a rank from the seed; a candidate is a <i>site</i> when no site of higher rank stands within the kind's
 * {@code apart} blocks. (The first try for the nests, "no <i>candidate</i> of higher rank within", gave one site per
 * 1,082 blocks of river: a winding river puts dozens of candidates in that circle, so almost none won.)
 */
public final class RiverSites {
    /** A kind's test of a chunk from the noise alone. */
    @FunctionalInterface
    public interface Candidate {
        boolean test(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz);
    }

    private static final int CACHE_MOST = 200_000;
    private final int apart;
    private final long salt;
    private final Candidate candidate;
    /**
     * One world's answers: the seed, and the biome source and climate reader it was asked with, by identity (as
     * {@link BigSavanna}; review R8-1: remembered by the seed alone, a second world with the same seed but other land,
     * opened in the same sitting, got the first world's sites).
     */
    private record Store(BiomeSource biomes, Climate.Sampler sampler, long seed, Map<Long, Boolean> candidates, Map<Long, Boolean> sites) {
    }

    private volatile Store store;
    private static final java.util.List<RiverSites> ALL = new java.util.concurrent.CopyOnWriteArrayList<>();

    public RiverSites(int apart, long salt, Candidate candidate) {
        this.apart = apart;
        this.salt = salt;
        this.candidate = candidate;
        ALL.add(this);
    }

    /** The server has stopped: no world's answers are kept for the next one opened. */
    public static void forgetAll() {
        for (RiverSites sites : ALL) {
            sites.store = null;
        }
    }

    private Store store(BiomeSource biomes, Climate.Sampler sampler, long seed) {
        Store s = store;
        if (s == null || s.biomes != biomes || s.sampler != sampler || s.seed != seed || s.candidates.size() > CACHE_MOST) {
            s = new Store(biomes, sampler, seed, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
            store = s;
        }
        return s;
    }

    /** Whether the chunk {@code cx, cz} is a site: a candidate with no site of higher rank within {@code apart} (remembered per seed). */
    public boolean site(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        if (!candidate(biomes, sampler, seed, cx, cz)) {
            return false;
        }
        Map<Long, Boolean> sites = store(biomes, sampler, seed).sites();
        long key = (long) cx << 32 ^ (cz & 0xFFFFFFFFL);
        Boolean known = sites.get(key);
        if (known != null) {
            return known;
        }
        long own = rank(seed, cx, cz);
        int r = (apart + 15) / 16;
        boolean is = true;
        for (int dx = -r; dx <= r && is; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0 || dx * dx + dz * dz > (apart / 16.0) * (apart / 16.0)) {
                    continue;
                }
                // a better-ranked site too near: this one is not (ranks only go up, so this always ends)
                if (rank(seed, cx + dx, cz + dz) > own && site(biomes, sampler, seed, cx + dx, cz + dz)) {
                    is = false;
                    break;
                }
            }
        }
        sites.put(key, is);
        return is;
    }

    /** Whether the chunk is a candidate (remembered per seed). */
    public boolean candidate(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        Map<Long, Boolean> candidates = store(biomes, sampler, seed).candidates();
        long key = (long) cx << 32 ^ (cz & 0xFFFFFFFFL);
        Boolean known = candidates.get(key);
        if (known != null) {
            return known;
        }
        boolean is = candidate.test(biomes, sampler, seed, cx, cz);
        candidates.put(key, is);
        return is;
    }

    /** A chunk's rank among candidates, from the seed alone. */
    long rank(long seed, int cx, int cz) {
        long h = seed ^ salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ h >>> 33;
    }
}
