package com.antcolonies;

import org.slf4j.Logger;

import com.antcolonies.registry.ModEntities;
import com.antcolonies.registry.ModItems;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

// The value here must match the mod id in gradle.properties / neoforge.mods.toml
@Mod(AntColoniesMod.MODID)
public class AntColoniesMod {
    public static final String MODID = "antcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AntColoniesMod(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        LOGGER.info("Ant & Termite Colonies loaded");
    }
}
