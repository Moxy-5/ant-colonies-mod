package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.savanna.SavannaMod;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;

/**
 * No small savannas (the user, 2026-11-13, "I kinda need every one of them to be big if the ecosystem is supposed to run
 * properly"; {@code docs/plans/big-savannas-big-rivers.md}): TerraBlender splits the world into large zones, each one
 * region's biome table. About 1 zone in 4 was Minecraft's own region ({@code minecraft:overworld}), whose table grows
 * small savannas, and wherever one of those zones met one of ours a big savanna was cut, leaving scraps.
 *
 * <p>One recipe everywhere (the user's OK, 2026-11-16): Minecraft's zones are given to {@link SavannaRegion}, whose
 * weight grows by theirs, so with no other biome mod every zone has the same table and no zone edge cuts a savanna. (From
 * 2026-11-16 until then those zones had {@link #table()}, Minecraft's table with savannas swapped for plains: no small
 * savannas of Minecraft's, but our own still cut at the edges; the table is kept for the biome maps' before picture.)
 *
 * <p>How, read in TerraBlender 4.1.0.8's classes (2026-11-16): TerraBlender never asks the region at index 0 for its
 * biomes; that zone always uses the world preset's own list, Minecraft's table ({@code MixinParameterList
 * .initializeForTerraBlender}). So index 0 keeps the name but becomes an empty region: TerraBlender leaves a region that
 * gives no biomes out of its zone picking ({@code InitialLayer.createEntries}), so no zone is ever Minecraft's own.
 *
 * <p>The index: TerraBlender keeps each region's index from when it was signed up, and a plain re-register under the same
 * name would give "regions minus 1", wrong once any other region exists. So the old one is removed and the empty one put
 * back at position 0 ({@code Regions.register(name, 0, region)}); {@link #indexProblems()} checks every region's index
 * against its place in the list (a game test and the log line {@code [biome regions]}).
 */
public final class MinecraftZones {
    public static final ResourceLocation MINECRAFT = ResourceLocation.withDefaultNamespace("overworld");

    private MinecraftZones() {
    }

    /** True once the zones were replaced (false if another mod had already moved Minecraft's region). */
    private static boolean replaced;

    /** Minecraft's overworld table with its savannas swapped out (the biome maps' before picture). */
    public static List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> table() {
        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out = new ArrayList<>();
        new OverworldBiomeBuilder().addBiomes(pair -> out.add(pair.mapSecond(MinecraftZones::swap)));
        return out;
    }

    static ResourceKey<Biome> swap(ResourceKey<Biome> biome) {
        if (biome == Biomes.SAVANNA || biome == Biomes.SAVANNA_PLATEAU) {
            return Biomes.PLAINS;
        }
        if (biome == Biomes.WINDSWEPT_SAVANNA) {
            return Biomes.WINDSWEPT_HILLS;
        }
        return biome;
    }

    /** Index 0's stand-in: keeps Minecraft's name and index, gives no biomes, so TerraBlender never picks it for a zone. */
    private static final class Empty extends Region {
        Empty(int weight) {
            super(MINECRAFT, RegionType.OVERWORLD, weight);
        }

        @Override
        public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        }
    }

    /**
     * Empties Minecraft's zones (at common set-up, before {@link SavannaRegion} signs up) and returns the weight they had,
     * for the big savannas to take; 0 if another mod had already moved Minecraft's region (its zones then stay its own).
     */
    public static int replace() {
        List<Region> regions = Regions.get(RegionType.OVERWORLD);
        if (regions.isEmpty() || !regions.get(0).getName().equals(MINECRAFT) || Regions.getIndex(RegionType.OVERWORLD, MINECRAFT) != 0) {
            SavannaMod.LOGGER.warn("[biome regions] Minecraft's region is not first ({}): its zones keep their small savannas",
                    regions.stream().map(Region::getName).toList());
            return 0;
        }
        int weight = regions.get(0).getWeight();
        Regions.remove(RegionType.OVERWORLD, MINECRAFT);
        Regions.register(MINECRAFT, 0, new Empty(weight)); // logs "Registered region minecraft:overworld to index 0"
        replaced = true;
        SavannaMod.LOGGER.info("[biome regions] Minecraft's zones (weight {}) given to the big savannas: one biome table everywhere", weight);
        return weight;
    }

    public static boolean replaced() {
        return replaced;
    }

    /**
     * What is wrong with the regions' indices, empty if nothing: every region's index must be its place in the list (the
     * layout looks a zone's region up by index), index 0 must be Minecraft's name with no biomes, and the big savannas must
     * be there.
     */
    public static List<String> indexProblems() {
        List<String> problems = new ArrayList<>();
        List<Region> regions = Regions.get(RegionType.OVERWORLD);
        for (int i = 0; i < regions.size(); i++) {
            int index = Regions.getIndex(RegionType.OVERWORLD, regions.get(i).getName());
            if (index != i) {
                problems.add(regions.get(i).getName() + " is at place " + i + " but has index " + index);
            }
        }
        if (regions.isEmpty() || !regions.get(0).getName().equals(MINECRAFT)) {
            problems.add("index 0 is not minecraft:overworld");
        } else if (!(regions.get(0) instanceof Empty)) {
            problems.add("index 0 is still Minecraft's own region (its zones keep their small savannas)");
        }
        if (regions.stream().noneMatch(r -> r.getName().equals(SavannaRegion.NAME))) {
            problems.add(SavannaRegion.NAME + " is not signed up");
        }
        return problems;
    }

    /** The regions in order with their indices and weights, for the log. */
    public static String describe() {
        StringBuilder out = new StringBuilder();
        for (Region r : Regions.get(RegionType.OVERWORLD)) {
            out.append(String.format(" %s=%d(w%d)", r.getName(), Regions.getIndex(RegionType.OVERWORLD, r.getName()), r.getWeight()));
        }
        return out.toString().trim();
    }
}
