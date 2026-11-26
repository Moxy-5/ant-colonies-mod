package com.antcolonies.species;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.antcolonies.ColonyFrameworkMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads every species profile from {@code data/<namespace>/species/*.json} when the world starts and on {@code /reload}.
 * {@code data/antcolonies/species/macrotermes.json} becomes the species {@code antcolonies:macrotermes}.
 * Server side only (the client never needs the numbers).
 */
public class SpeciesProfiles extends SimpleJsonResourceReloadListener {
    public static final SpeciesProfiles INSTANCE = new SpeciesProfiles();

    private static volatile Map<ResourceLocation, SpeciesProfile> profiles = Map.of();

    private SpeciesProfiles() {
        super(new Gson(), "species");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        profiles = Map.copyOf(read(files, profiles));
        ColonyFrameworkMod.LOGGER.info("Loaded {} species profiles: {}", profiles.size(), profiles.keySet());
    }

    /**
     * The profiles of these files. A file with a mistake never leaves its species without numbers (review R1-2, R1-42:
     * a colony with no profile cannot be loaded or ticked): on {@code /reload} the species keeps the numbers it had
     * ({@code before}) and the mistake is logged; with none to keep (the game starting) the loading stops with the
     * file and its mistake named.
     */
    static Map<ResourceLocation, SpeciesProfile> read(Map<ResourceLocation, JsonElement> files, Map<ResourceLocation, SpeciesProfile> before) {
        Map<ResourceLocation, SpeciesProfile> loaded = new HashMap<>();
        Map<ResourceLocation, String> broken = new TreeMap<>();
        files.forEach((id, json) -> { // the shared numbers, then each species mod's own sections (Apex part 3, T1)
            var read = SpeciesProfile.read(json);
            String error = read.error().map(e -> e.message()).orElse(null);
            SpeciesProfile profile = error == null ? read.result().orElse(null) : before.get(id);
            if (error != null && profile != null) {
                ColonyFrameworkMod.LOGGER.error("Species profile {} is broken, keeping the numbers it had: {}", id, error);
            } else if (profile == null) {
                broken.put(id, error == null ? "nothing could be read" : error);
            }
            if (profile != null) {
                loaded.put(id, profile);
            }
        });
        if (!broken.isEmpty()) {
            throw new IllegalStateException("Broken species profile" + (broken.size() == 1 ? "" : "s") + ", fix the file and start again: " + broken);
        }
        // A part name nothing signed up stops the loading here, with a clear message (Project Apex, colony parts step 2).
        loaded.forEach((id, profile) -> com.antcolonies.colony.ColonyParts.checkSpecies(id, profile.parts()));
        loaded.forEach((id, profile) -> profile.food().checkPile(id)); // and a food store heap that is no heap block (the split, S3)
        return loaded;
    }

    /** Tests only: what {@link #apply} would make of these files, with {@code before} the numbers already loaded. */
    public static Map<ResourceLocation, SpeciesProfile> readForTests(Map<ResourceLocation, JsonElement> files, Map<ResourceLocation, SpeciesProfile> before) {
        return read(files, before);
    }

    /**
     * The profile of this species, from its file. With none (its mod is not installed) this throws: a saved colony of
     * such a species is kept unread by {@code ColonyManager}, not lost.
     */
    public static SpeciesProfile get(ResourceLocation species) {
        SpeciesProfile profile = profiles.get(species);
        if (profile == null) {
            throw new IllegalStateException("No species profile for " + species + "; is its mod installed?");
        }
        return profile;
    }
}
