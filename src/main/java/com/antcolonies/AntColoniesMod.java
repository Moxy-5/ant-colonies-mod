package com.antcolonies;

import org.slf4j.Logger;

import com.antcolonies.gametest.NavigationTests;
import com.antcolonies.gametest.NestTests;
import com.antcolonies.gametest.TermiteTests;
import com.antcolonies.gametest.TestFilter;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.registry.ModFeatures;
import com.antcolonies.registry.ModItems;
import com.antcolonies.registry.TreeBlocks;
import com.antcolonies.species.SpeciesProfiles;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

// The value here must match the mod id in gradle.properties / neoforge.mods.toml
@Mod(AntColoniesMod.MODID)
public class AntColoniesMod {
    public static final String MODID = "antcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AntColoniesMod(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        // How the giant savanna trees' blocks burn (Minecraft's fire table is filled once, at start).
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(TreeBlocks::setFlammable));
        // In-game tests: only loaded when the game is started in game-test mode.
        modEventBus.addListener((RegisterGameTestsEvent event) -> {
            event.register(NestTests.class);
            event.register(NavigationTests.class);
            event.register(TermiteTests.class);
        });
        // Only some game tests when ANTCOLONIES_TESTS is set (quick checks while working).
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> TestFilter.apply(event.getServer()));
        // Species profiles (data/<namespace>/species/*.json), loaded with the world's data and on /reload.
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(SpeciesProfiles.INSTANCE));
        LOGGER.info("Ant & Termite Colonies loaded");
    }
}
