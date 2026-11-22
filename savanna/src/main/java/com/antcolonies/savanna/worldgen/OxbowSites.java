package com.antcolonies.savanna.worldgen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Where new land's oxbow lakes go (oxbows and pans rework step 3, 2026-11-22, {@code docs/plans/oxbows-and-pans.md}),
 * from the seed and the land's noise alone like the other river sites ({@link RiverSites}): a chunk is a candidate when
 * its middle is savanna floodplain by the biome noise with the savanna river 14 to 22 blocks from it (none nearer than 10), and no two sites stand nearer than {@link #APART} blocks. The site's
 * chunk then looks for room beside the river and lays the oxbow ({@link OxbowFeature}); a site with no room stays empty
 * (realistic: oxbows form only where the floodplain is wide). Numbers my picks.
 */
public final class OxbowSites {
    /** No two sites nearer than this (blocks; my pick: about one oxbow tried per 250 blocks of each bank). */
    public static final int APART = 112;
    private static final RiverSites SITES = new RiverSites(APART, 0x6F78626F77L, OxbowSites::floodplainChunk);

    private OxbowSites() {
    }

    /** Whether the chunk {@code cx, cz} is an oxbow site. */
    public static boolean site(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return SITES.site(biomes, sampler, seed, cx, cz);
    }

    /** The river must be no nearer a site's middle than this (blocks)... */
    static final int RIVER_NOT_WITHIN = 10;
    /** ...and found at one of these distances: the oxbow's middle lies about 14 to 25 out from the water, so a site's
     *  middle there keeps the whole oxbow inside the 3 by 3 chunks round it (my picks, from the flight runs). */
    static final int[] RIVER_LOOK = {14, 18, 22};

    private static boolean floodplainChunk(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        int x = cx * 16 + 8, z = cz * 16 + 8;
        if (!biome(biomes, sampler, x, z).is(MudSites.FLOODPLAIN)) {
            return false;
        }
        for (int r : new int[] {4, RIVER_NOT_WITHIN}) {
            for (int k = 0; k < 8; k++) {
                if (riverAt(biomes, sampler, x, z, r, k)) {
                    return false;
                }
            }
        }
        for (int r : RIVER_LOOK) {
            for (int k = 0; k < 8; k++) {
                if (riverAt(biomes, sampler, x, z, r, k)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean riverAt(BiomeSource biomes, Climate.Sampler sampler, int x, int z, int r, int k) {
        double a = k * Math.PI / 4;
        return biome(biomes, sampler, x + (int) Math.round(Math.cos(a) * r), z + (int) Math.round(Math.sin(a) * r)).is(RiverZones.RIVER);
    }

    private static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y), QuartPos.fromBlock(z), sampler);
    }
}
