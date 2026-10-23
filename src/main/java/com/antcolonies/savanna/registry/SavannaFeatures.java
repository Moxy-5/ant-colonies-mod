package com.antcolonies.savanna.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.savanna.worldgen.FallenBranchFeature;
import com.antcolonies.savanna.worldgen.FallenTreeFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The savanna's land: fallen branches and trees, and rain (the split, step S4). */
public class SavannaFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, AntColoniesMod.MODID);

    /** Lays a dead branch on the ground. Switched on for savannas by data/antcolonies/neoforge/biome_modifier/fallen_branch.json. */
    public static final DeferredHolder<Feature<?>, FallenBranchFeature> FALLEN_BRANCH =
            FEATURES.register("fallen_branch", () -> new FallenBranchFeature(NoneFeatureConfiguration.CODEC));

    /** Lays a fallen dead acacia (stump, trunk, branches). Switched on for savannas by .../biome_modifier/fallen_tree.json. */
    public static final DeferredHolder<Feature<?>, FallenTreeFeature> FALLEN_TREE =
            FEATURES.register("fallen_tree", () -> new FallenTreeFeature(NoneFeatureConfiguration.CODEC));

    /** Our own kinds of biome modifier. */
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.world.BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, AntColoniesMod.MODID);

    /** Lets it rain in savannas (data/antcolonies/neoforge/biome_modifier/savanna_rain.json). */
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.world.BiomeModifier>,
            com.mojang.serialization.MapCodec<com.antcolonies.savanna.worldgen.RainModifier>> RAIN =
            BIOME_MODIFIERS.register("rain", () -> com.antcolonies.savanna.worldgen.RainModifier.CODEC);
}
