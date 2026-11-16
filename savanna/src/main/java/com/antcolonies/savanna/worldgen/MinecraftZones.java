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
 * properly"; {@code docs/plans/no-small-savannas.md}): TerraBlender splits the world into large zones, each one region's
 * biome table. About 1 zone in 4 was Minecraft's own region ({@code minecraft:overworld}), whose table grows small
 * savannas, savanna plateaus and windswept savannas (the user's small spawn savanna, BUGS 209, was one). Here those zones
 * get Minecraft's table with the savannas swapped out: savanna and savanna plateau become plains, windswept savanna
 * windswept hills (my picks, in the plan). Every other biome of those zones stays Minecraft's own, point for point.
 *
 * <p>How, read in TerraBlender 4.1.0.8's classes (2026-11-16): TerraBlender never asks the region at index 0 for its
 * biomes; that zone always uses the world preset's own list, Minecraft's table ({@code MixinParameterList
 * .initializeForTerraBlender}). So swapping the region object under the name {@code minecraft:overworld} alone would change
 * nothing. Instead index 0 keeps the name but becomes an empty region: TerraBlender leaves a region that gives no biomes
 * out of its zone picking ({@code InitialLayer.createEntries}), so no zone is ever Minecraft's own. Our region
 * {@link #NAME} then carries Minecraft's table without savannas, at the weight Minecraft's region had (the player's
 * {@code vanilla_overworld_region_weight}, default 10), so the zones keep their shares and sizes: only what grows in
 * Minecraft's zones changes.
 *
 * <p>The index: TerraBlender keeps each region's index from when it was signed up, and a plain re-register under the same
 * name would give "regions minus 1", wrong once any other region exists. So the old one is removed and the empty one put
 * back at position 0 ({@code Regions.register(name, 0, region)}); {@link #indexProblems()} checks every region's index
 * against its place in the list (a game test and the log line {@code [biome regions]}).
 */
public final class MinecraftZones extends Region {
    public static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "minecraft_without_savannas");
    public static final ResourceLocation MINECRAFT = ResourceLocation.withDefaultNamespace("overworld");

    /** True once the zones were replaced (false if another mod had already moved Minecraft's region). */
    private static boolean replaced;

    public MinecraftZones(int weight) {
        super(NAME, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        table().forEach(mapper);
    }

    /** Minecraft's overworld table with its savannas swapped out. */
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

    /** Replaces Minecraft's zones (at common set-up, before {@link SavannaRegion} signs up). */
    public static void replace() {
        List<Region> regions = Regions.get(RegionType.OVERWORLD);
        if (regions.isEmpty() || !regions.get(0).getName().equals(MINECRAFT) || Regions.getIndex(RegionType.OVERWORLD, MINECRAFT) != 0) {
            SavannaMod.LOGGER.warn("[biome regions] Minecraft's region is not first ({}): its zones keep their small savannas",
                    regions.stream().map(Region::getName).toList());
            return;
        }
        int weight = regions.get(0).getWeight();
        Regions.remove(RegionType.OVERWORLD, MINECRAFT);
        Regions.register(MINECRAFT, 0, new Empty(weight)); // logs "Registered region minecraft:overworld to index 0"
        Regions.register(new MinecraftZones(weight));
        replaced = true;
        SavannaMod.LOGGER.info("[biome regions] Minecraft's zones replaced by {} (weight {}), no small savannas there", NAME, weight);
    }

    public static boolean replaced() {
        return replaced;
    }

    /**
     * What is wrong with the regions' indices, empty if nothing: every region's index must be its place in the list (the
     * layout looks a zone's region up by index), index 0 must be Minecraft's name with no biomes, and our region must be
     * there.
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
        if (regions.stream().noneMatch(r -> r.getName().equals(NAME))) {
            problems.add(NAME + " is not signed up");
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
