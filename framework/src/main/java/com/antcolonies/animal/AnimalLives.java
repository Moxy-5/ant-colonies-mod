package com.antcolonies.animal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.antcolonies.ColonyFrameworkMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

/**
 * Loads every animal's life file from {@code data/<namespace>/animal_life/*.json} when the world starts and on
 * {@code /reload} (the animal life kit, {@code docs/plans/animal-life-cycle.md}). The file is named after the animal:
 * {@code data/antcolonies/animal_life/tilapia.json} holds the numbers of {@code antcolonies:tilapia}. Server side only:
 * the client is told an animal's sex and stage by the animal itself.
 */
public class AnimalLives extends SimpleJsonResourceReloadListener {
    public static final AnimalLives INSTANCE = new AnimalLives();

    private static volatile Map<ResourceLocation, LifeNumbers> lives = Map.of();
    /** Animals already warned about (no file), so the log is not flooded. */
    private static final Set<ResourceLocation> warned = new HashSet<>();

    private AnimalLives() {
        super(new Gson(), "animal_life");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, LifeNumbers> loaded = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id, LifeNumbers.read(json));
            } catch (RuntimeException e) {
                ColonyFrameworkMod.LOGGER.error("Animal life file {} is broken, using the default numbers: {}", id, e.getMessage());
            }
        });
        lives = Map.copyOf(loaded);
        synchronized (warned) {
            warned.clear();
        }
        ColonyFrameworkMod.LOGGER.info("Loaded {} animal life files: {}", loaded.size(), loaded.keySet());
    }

    /** Whether this animal has a life file that loaded. */
    public static boolean has(EntityType<?> type) {
        return lives.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /**
     * The life numbers of this animal: its file, else {@link LifeNumbers#DEFAULT} (logged once on the server; the client
     * never has the files and always gets the default, which it only uses until the animal tells it its own).
     */
    public static LifeNumbers get(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        LifeNumbers numbers = lives.get(id);
        if (numbers != null) {
            return numbers;
        }
        if (!lives.isEmpty()) {
            synchronized (warned) {
                if (warned.add(id)) {
                    ColonyFrameworkMod.LOGGER.warn("No animal life file for {}, using the default numbers", id);
                }
            }
        }
        return LifeNumbers.DEFAULT;
    }
}
