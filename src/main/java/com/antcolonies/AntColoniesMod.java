package com.antcolonies;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// The value here must match the mod id in gradle.properties / neoforge.mods.toml
@Mod(AntColoniesMod.MODID)
public class AntColoniesMod {
    public static final String MODID = "antcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AntColoniesMod(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Ant & Termite Colonies loaded");
    }
}
