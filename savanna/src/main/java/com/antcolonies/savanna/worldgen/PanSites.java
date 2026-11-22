package com.antcolonies.savanna.worldgen;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Where new land's pans go (oxbows and pans rework step 4, 2026-11-22, {@code docs/plans/oxbows-and-pans.md}), from the
 * seed and the land's noise alone like the other sites ({@link RiverSites}): a chunk is a candidate when its middle is
 * open savanna (not the river, not the floodplain) in a big savanna ({@link BigSavanna}) with no river or floodplain
 * within {@link #FROM_RIVER} blocks (real pans are rain-filled hollows, often far from any river), and no two sites stand
 * nearer than {@link #APART} blocks. The site's chunk then lays the pan if its ground lets it ({@link PanFeature}).
 * Numbers my picks.
 */
public final class PanSites {
    /** No two sites nearer than this (blocks; my pick: with about half the sites holding a pan, about one pan per 192 by 192). */
    public static final int APART = 160;
    /** No river or floodplain this close to a site's middle (blocks; the plan's pick). */
    public static final int FROM_RIVER = 48;
    private static final RiverSites SITES = new RiverSites(APART, 0x70616E73L, PanSites::openChunk);

    private PanSites() {
    }

    /** Whether the chunk {@code cx, cz} is a pan site. */
    public static boolean site(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return SITES.site(biomes, sampler, seed, cx, cz);
    }

    private static boolean openChunk(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        int x = cx * 16 + 8, z = cz * 16 + 8;
        if (!MudSites.dryLandAt(biomes, sampler, x, z) || !BigSavanna.livesAt(biomes, sampler, x, z)) {
            return false;
        }
        for (int r = 16; r <= FROM_RIVER; r += 16) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                int px = x + (int) Math.round(Math.cos(a) * r), pz = z + (int) Math.round(Math.sin(a) * r);
                if (MudSites.riverAt(biomes, sampler, px, pz) || floodplainAt(biomes, sampler, px, pz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean floodplainAt(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        return biomes.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y),
                net.minecraft.core.QuartPos.fromBlock(z), sampler).is(MudSites.FLOODPLAIN);
    }
}
