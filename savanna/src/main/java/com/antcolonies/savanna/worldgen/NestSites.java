package com.antcolonies.savanna.worldgen;

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

    /** The sites, worked out the shared way ({@link RiverSites}). */
    private static final RiverSites SITES = new RiverSites(APART, 0x6E657374L, NestSites::work);

    private NestSites() {
    }

    /** Whether the chunk {@code cx, cz} is a crocodile nest site: a candidate with no site of higher rank within {@link #APART}. */
    public static boolean site(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return SITES.site(biomes, sampler, seed, cx, cz);
    }

    /** Whether the chunk is a candidate: savanna water at its middle, a sand bank beside it. */
    static boolean candidate(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return SITES.candidate(biomes, sampler, seed, cx, cz);
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
}
