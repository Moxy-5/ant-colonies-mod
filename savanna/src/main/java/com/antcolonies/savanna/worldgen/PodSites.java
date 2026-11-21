package com.antcolonies.savanna.worldgen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Where new land's hippo pods live (the user, 2026-11-21: pods placed with the land and kept for good, groups of 3 at
 * most; {@code docs/DESIGN.md}, "River zones"): one pool site about every 250 blocks of the river's near and mid-inland
 * stretches (my pick: a bull's stretch of river), none at the coast or up the narrow far-inland rivers, where a pod would
 * block the water. A chunk is a candidate when its middle is savanna river by the biome noise and the hippo's zone share
 * there is above nothing ({@link RiverZones}); sites are spaced the shared way ({@link RiverSites}). The chunk then finds
 * its own deep water and lays the pod ({@link HippoPodFeature}); a site whose chunk holds no pool stays empty.
 */
public final class PodSites {
    /** No two sites nearer than this (blocks; my pick, measured by {@code hippoPodSitesComeAbout250BlocksApartOnTheirRivers}). */
    public static final int APART = 180;

    private static final RiverSites SITES = new RiverSites(APART, 0x6869707070L, PodSites::work);

    private PodSites() {
    }

    /** Whether the chunk {@code cx, cz} is a hippo pod site. */
    public static boolean site(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return SITES.site(biomes, sampler, seed, cx, cz);
    }

    /** Whether the river at a chunk's middle is a hippo's stretch (the zone, from the noise alone). */
    public static boolean hippoRiver(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        var biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y), QuartPos.fromBlock(z), sampler);
        if (!biome.is(RiverZones.RIVER)) {
            return false;
        }
        RiverZones.Zone zone = RiverZones.river(RiverZones.band(RiverBends.at(sampler.continentalness(), x, z)));
        return RiverZones.SHARES.get("hippo")[zone.ordinal()] > 0;
    }

    private static boolean work(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return hippoRiver(biomes, sampler, cx * 16 + 8, cz * 16 + 8);
    }
}
