package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.worldgen.DeepSoilFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, AntColoniesMod.MODID);

    /** Puts Deep Soil under the dirt. Switched on for the biomes by data/antcolonies/neoforge/biome_modifier/deep_soil.json. */
    public static final DeferredHolder<Feature<?>, DeepSoilFeature> DEEP_SOIL =
            FEATURES.register("deep_soil", () -> new DeepSoilFeature(NoneFeatureConfiguration.CODEC));
}
