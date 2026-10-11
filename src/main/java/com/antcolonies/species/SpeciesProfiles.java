package com.antcolonies.species;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads every species profile from {@code data/<namespace>/species/*.json} when the world starts and on {@code /reload}.
 * {@code data/antcolonies/species/red_harvester_ant.json} becomes the species {@code antcolonies:red_harvester_ant}.
 * Server side only (the client never needs the numbers).
 */
public class SpeciesProfiles extends SimpleJsonResourceReloadListener {
    public static final SpeciesProfiles INSTANCE = new SpeciesProfiles();

    private static volatile Map<ResourceLocation, SpeciesProfile> profiles = Map.of();
    /** Species already warned about (no file), so the log is not flooded. */
    private static final Set<ResourceLocation> warned = new HashSet<>();

    private SpeciesProfiles() {
        super(new Gson(), "species");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, SpeciesProfile> loaded = new HashMap<>();
        files.forEach((id, json) -> SpeciesProfile.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> AntColoniesMod.LOGGER.error("Species profile {} is broken, using the built-in numbers: {}", id, error))
                .ifPresent(profile -> loaded.put(id, profile)));
        profiles = Map.copyOf(loaded);
        synchronized (warned) {
            warned.clear();
        }
        AntColoniesMod.LOGGER.info("Loaded {} species profiles: {}", loaded.size(), loaded.keySet());
    }

    /** The profile of this species, or the red harvester ant's built-in numbers if it has none (logged once). */
    public static SpeciesProfile get(ResourceLocation species) {
        SpeciesProfile profile = profiles.get(species);
        if (profile != null) {
            return profile;
        }
        synchronized (warned) {
            if (warned.add(species)) {
                AntColoniesMod.LOGGER.warn("No species profile for {}, using the red harvester ant's built-in numbers", species);
            }
        }
        return SpeciesProfile.RED_HARVESTER_ANT;
    }
}
