package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.worldgen.DeepSoilFeature;
import com.antcolonies.worldgen.GramaPatchFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The harvester ant's land: the deep soil and the grama grass (the split, step S4). */
public class DesertFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, AntColoniesMod.MODID);

    /** Puts Deep Soil under the dirt. Switched on for the biomes by data/antcolonies/neoforge/biome_modifier/deep_soil.json. */
    public static final DeferredHolder<Feature<?>, DeepSoilFeature> DEEP_SOIL =
            FEATURES.register("deep_soil", () -> new DeepSoilFeature(NoneFeatureConfiguration.CODEC));

    /**
     * Lays a stand of grama grass: blue grama meadows on the plains, black grama bunches round dead bushes in the
     * badlands. Switched on by .../biome_modifier/blue_grama.json and black_grama.json.
     */
    public static final DeferredHolder<Feature<?>, GramaPatchFeature> GRAMA_PATCH =
            FEATURES.register("grama_patch", () -> new GramaPatchFeature(GramaPatchFeature.Config.CODEC));
}
