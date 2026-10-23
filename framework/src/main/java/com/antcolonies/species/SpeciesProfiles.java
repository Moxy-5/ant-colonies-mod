package com.antcolonies.species;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.antcolonies.ColonyFrameworkMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads every species profile from {@code data/<namespace>/species/*.json} when the world starts and on {@code /reload}.
 * {@code data/desertcolonies/species/red_harvester_ant.json} becomes the species {@code desertcolonies:red_harvester_ant}.
 * Server side only (the client never needs the numbers).
 */
public class SpeciesProfiles extends SimpleJsonResourceReloadListener {
    public static final SpeciesProfiles INSTANCE = new SpeciesProfiles();

    private static volatile Map<ResourceLocation, SpeciesProfile> profiles = Map.of();
    /** Species already warned about (no file), so the log is not flooded. */
    private static final Set<ResourceLocation> warned = new HashSet<>();
    /**
     * Each species' numbers as built into its mod's code ({@link #signUpBuiltIn}), for when its file is missing or broken,
     * in the order signed up. Guarded by itself: species mods start side by side ({@code docs/BUGS.md} 64).
     */
    private static final Map<ResourceLocation, SpeciesProfile> builtIn = new LinkedHashMap<>();

    private SpeciesProfiles() {
        super(new Gson(), "species");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, SpeciesProfile> loaded = new HashMap<>();
        files.forEach((id, json) -> SpeciesProfile.read(json) // the shared numbers, then each species mod's own sections (Apex part 3, T1)
                .resultOrPartial(error -> ColonyFrameworkMod.LOGGER.error("Species profile {} is broken, using the built-in numbers: {}", id, error))
                .ifPresent(profile -> loaded.put(id, profile)));
        // A part name nothing signed up stops the loading here, with a clear message (Project Apex, colony parts step 2).
        loaded.forEach((id, profile) -> com.antcolonies.colony.ColonyParts.checkSpecies(id, profile.parts()));
        loaded.forEach((id, profile) -> profile.food().checkPile(id)); // and a food store heap that is no heap block (the split, S3)
        profiles = Map.copyOf(loaded);
        synchronized (warned) {
            warned.clear();
        }
        ColonyFrameworkMod.LOGGER.info("Loaded {} species profiles: {}", loaded.size(), loaded.keySet());
    }

    /**
     * A species mod's numbers built into its code (signed up at start), used when its file is missing or broken. The
     * framework holds none of its own (the split's clean-up, 2026-10-23: it held the red harvester ant's).
     */
    public static void signUpBuiltIn(ResourceLocation species, SpeciesProfile numbers) {
        synchronized (builtIn) {
            builtIn.put(species, numbers);
        }
    }

    /**
     * The profile of this species: its file, else its own built-in numbers, else the first built-in numbers signed up
     * (as before the clean-up, when every species without a good file got the red harvester ant's; logged once). With
     * no built-in numbers at all the game stops with a message.
     */
    public static SpeciesProfile get(ResourceLocation species) {
        SpeciesProfile profile = profiles.get(species);
        if (profile != null) {
            return profile;
        }
        ResourceLocation from;
        synchronized (builtIn) {
            from = builtIn.containsKey(species) ? species : builtIn.keySet().stream().findFirst().orElse(null);
            profile = from == null ? null : builtIn.get(from);
        }
        if (profile == null) {
            throw new IllegalStateException("No species profile for " + species + " and no built-in numbers to fall back on; is its mod installed?");
        }
        synchronized (warned) {
            if (warned.add(species)) {
                ColonyFrameworkMod.LOGGER.warn("No species profile for {}, using the built-in numbers of {}", species, from);
            }
        }
        return profile;
    }
}
