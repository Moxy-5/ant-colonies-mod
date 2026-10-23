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
 * The colony framework's start (its own mod since the split, step S6, {@code docs/plans/mod-split.md}): the shared
 * egg, the test filter and the species files. Each species mod signs up its own blocks, items, creatures, land and
 * tests in its own start; the framework knows none of them.
 */
// The value here must match the mod id in gradle.properties / neoforge.mods.toml
@Mod(ColonyFrameworkMod.MODID)
public class ColonyFrameworkMod {
    public static final String MODID = "colonyframework";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColonyFrameworkMod(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        // Only some game tests when ANTCOLONIES_TESTS is set (quick checks while working).
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> TestFilter.apply(event.getServer()));
        // Species profiles (data/<namespace>/species/*.json), loaded with the world's data and on /reload.
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(SpeciesProfiles.INSTANCE));
        LOGGER.info("Colony Framework loaded");
    }
}
