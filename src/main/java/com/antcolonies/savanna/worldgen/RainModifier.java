package com.antcolonies.worldgen;

import com.antcolonies.registry.SavannaFeatures;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Lets it rain in biomes where Minecraft never does (the user's ask, 2026-10-19). Real African savannas get most of
 * their year's rain in a wet season, often heavy thunderstorms, and the trees, grass, termites and ants all live by it
 * (acacia seedlings come up after the rains, termites forage in the wet); in Minecraft a savanna never rains. Only the
 * rain is switched on: the warmth and the dry look of the grass stay as they are. Used by
 * data/antcolonies/neoforge/biome_modifier/savanna_rain.json. A wet and a dry season come with Serene Seasons later.
 */
public record RainModifier(HolderSet<Biome> biomes) implements BiomeModifier {
    public static final MapCodec<RainModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(RainModifier::biomes)).apply(i, RainModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.MODIFY && biomes.contains(biome)) {
            builder.getClimateSettings().setHasPrecipitation(true);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return SavannaFeatures.RAIN.get();
    }
}
