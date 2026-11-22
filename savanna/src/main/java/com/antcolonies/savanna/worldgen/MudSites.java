package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.SavannaMudBlock;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Where new land's savanna mud goes (the user, 2026-11-21, "river mud plus dry wallows"; {@code docs/plans/savanna-mud.md},
 * section 5.1), from the seed and the land's noise alone like the other sites ({@link RiverSites}):
 * <ul>
 * <li><b>river mud areas</b>: a site about every 120 blocks of any savanna river (and every hippo pod's pool,
 *     {@link PodSites}); the chunk lays its mud at the waterline ({@link RiverMudFeature});</li>
 * <li><b>dry wallows</b>: a site about every 150 blocks of open savanna land off the river and the floodplain; the chunk
 *     lays a hollow of dry mud if its ground lets it ({@link DryWallowFeature}).</li>
 * </ul>
 * Numbers my picks.
 */
public final class MudSites {
    /**
     * No two river mud sites nearer than this (blocks): with the pods' pools, about one every 120 blocks of river (85 gave
     * one every 92 in the measure, {@code riverMudSitesComeAbout120BlocksApartAlongTheRivers}).
     */
    public static final int RIVER_APART = 110;
    /** No two dry wallow sites nearer than this (blocks): one to a square about 150 on a side (110 gave 106). */
    public static final int DRY_APART = 155;
    public static final ResourceKey<Biome> FLOODPLAIN = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_floodplain"));

    private static final RiverSites RIVER = new RiverSites(RIVER_APART, 0x6D7564726976L, MudSites::riverChunk);
    private static final RiverSites DRY = new RiverSites(DRY_APART, 0x6D7564647279L, MudSites::dryChunk);

    private MudSites() {
    }

    /** Whether the chunk {@code cx, cz} lays a river mud area (its own site, or a hippo pod's). */
    public static boolean river(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return RIVER.site(biomes, sampler, seed, cx, cz) || PodSites.site(biomes, sampler, seed, cx, cz);
    }

    /** Whether the chunk {@code cx, cz} is a dry wallow site. */
    public static boolean dry(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return DRY.site(biomes, sampler, seed, cx, cz);
    }

    /** Whether the land at a spot is savanna river, by the biome noise. */
    public static boolean riverAt(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        return biome(biomes, sampler, x, z).is(RiverZones.RIVER);
    }

    /** Whether the land at a spot is open savanna: savanna, not its river and not the floodplain, by the biome noise. */
    public static boolean dryLandAt(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        return dryLand(biome(biomes, sampler, x, z));
    }

    /** Whether a biome is open savanna land (savanna, not the river, not the floodplain). */
    public static boolean dryLand(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_SAVANNA) && !biome.is(RiverZones.RIVER) && !biome.is(FLOODPLAIN);
    }

    private static Holder<Biome> biome(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SavannaSpawn.SAMPLE_Y), QuartPos.fromBlock(z), sampler);
    }

    private static boolean riverChunk(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return riverAt(biomes, sampler, cx * 16 + 8, cz * 16 + 8);
    }

    private static boolean dryChunk(BiomeSource biomes, Climate.Sampler sampler, long seed, int cx, int cz) {
        return dryLandAt(biomes, sampler, cx * 16 + 8, cz * 16 + 8);
    }

    /**
     * The land's own mud colour away from the river, by how far inland (the sand banks' bands, {@link SandBanks}): red far
     * inland, golden mid inland, pale near the coast.
     */
    public static SavannaMudBlock.Tone landTone(Climate.Sampler sampler, int x, int z) {
        double inland = RiverBends.at(sampler.continentalness(), x, z);
        return inland >= SandBanks.TONE_FROM[1] ? SavannaMudBlock.Tone.RED
                : inland >= SandBanks.TONE_FROM[0] ? SavannaMudBlock.Tone.GOLDEN : SavannaMudBlock.Tone.PALE;
    }
}
