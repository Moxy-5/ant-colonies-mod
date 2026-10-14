package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.worldgen.DeepSoilFeature;
import com.antcolonies.worldgen.FallenBranchFeature;
import com.antcolonies.worldgen.FallenTreeFeature;

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
}
