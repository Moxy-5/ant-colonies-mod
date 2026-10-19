package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.worldgen.DeepSoilFeature;
import com.antcolonies.worldgen.FallenBranchFeature;
import com.antcolonies.worldgen.FallenTreeFeature;
import com.antcolonies.worldgen.GramaPatchFeature;

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

    /** Lays a dead branch on the ground. Switched on for savannas by data/antcolonies/neoforge/biome_modifier/fallen_branch.json. */
    public static final DeferredHolder<Feature<?>, FallenBranchFeature> FALLEN_BRANCH =
            FEATURES.register("fallen_branch", () -> new FallenBranchFeature(NoneFeatureConfiguration.CODEC));

    /** Lays a fallen dead acacia (stump, trunk, branches). Switched on for savannas by .../biome_modifier/fallen_tree.json. */
    public static final DeferredHolder<Feature<?>, FallenTreeFeature> FALLEN_TREE =
            FEATURES.register("fallen_tree", () -> new FallenTreeFeature(NoneFeatureConfiguration.CODEC));

    /**
     * Lays a stand of grama grass: blue grama meadows on the plains, black grama bunches round dead bushes in the
     * badlands. Switched on by .../biome_modifier/blue_grama.json and black_grama.json.
     */
    public static final DeferredHolder<Feature<?>, GramaPatchFeature> GRAMA_PATCH =
            FEATURES.register("grama_patch", () -> new GramaPatchFeature(GramaPatchFeature.Config.CODEC));

    /** Our own kinds of biome modifier. */
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.world.BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, AntColoniesMod.MODID);

    /** Lets it rain in savannas (data/antcolonies/neoforge/biome_modifier/savanna_rain.json). */
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.world.BiomeModifier>,
            com.mojang.serialization.MapCodec<com.antcolonies.worldgen.RainModifier>> RAIN =
            BIOME_MODIFIERS.register("rain", () -> com.antcolonies.worldgen.RainModifier.CODEC);
}
