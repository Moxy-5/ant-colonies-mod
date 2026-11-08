package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.registry.SavannaFeatures;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Gives biomes another water colour (savanna rivers, 2026-11-07, {@code docs/plans/savanna-rivers.md}): real savanna pools
 * and pans are murky and green with algae, so the savannas' own still water takes the pool colour the user picked
 * ({@code #3E5F33}, underwater fog {@code #111C0D}); the rivers have their own biome, {@code antcolonies:savanna_river},
 * olive with silt. Used by data/antcolonies/neoforge/biome_modifier/savanna_pool_water.json. Single-biome savanna worlds
 * have no river biome, so all their water is this green.
 */
public record WaterColourModifier(HolderSet<Biome> biomes, int waterColor, int waterFogColor) implements BiomeModifier {
    public static final MapCodec<WaterColourModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(WaterColourModifier::biomes),
            com.mojang.serialization.Codec.INT.fieldOf("water_color").forGetter(WaterColourModifier::waterColor),
            com.mojang.serialization.Codec.INT.fieldOf("water_fog_color").forGetter(WaterColourModifier::waterFogColor))
            .apply(i, WaterColourModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.MODIFY && biomes.contains(biome)) {
            builder.getSpecialEffects().waterColor(waterColor).waterFogColor(waterFogColor);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return SavannaFeatures.WATER_COLOUR.get();
    }
}
