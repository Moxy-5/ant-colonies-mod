package com.antcolonies;

import org.slf4j.Logger;

import com.antcolonies.gametest.TestFilter;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.SpeciesProfiles;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * The framework's start: the shared egg, the test filter and the species files (the split, step S4: each species signs
 * up its own blocks, items, creatures, land and tests in {@link SavannaMod} and {@link DesertMod}; still one mod).
 */
// The value here must match the mod id in gradle.properties / neoforge.mods.toml
@Mod(AntColoniesMod.MODID)
public class AntColoniesMod {
    public static final String MODID = "antcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AntColoniesMod(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        // Only some game tests when ANTCOLONIES_TESTS is set (quick checks while working).
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> TestFilter.apply(event.getServer()));
        // Species profiles (data/<namespace>/species/*.json), loaded with the world's data and on /reload.
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(SpeciesProfiles.INSTANCE));
        LOGGER.info("Ant & Termite Colonies loaded");
    }
}
