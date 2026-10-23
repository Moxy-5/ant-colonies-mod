package com.antcolonies.desert;

import com.antcolonies.desert.colony.DesertParts;
import com.antcolonies.desert.gametest.DesertTests;
import com.antcolonies.desert.registry.DesertBlocks;
import com.antcolonies.desert.registry.DesertEntities;
import com.antcolonies.desert.registry.DesertFeatures;
import com.antcolonies.desert.registry.DesertItems;

import org.slf4j.Logger;

import com.antcolonies.entity.body.BodyPlans;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * The desert's start (the split, steps S3 to S6, {@code docs/plans/mod-split.md}): the red harvester ant signs up its
 * parts, blocks, items, creatures and tests here, on top of the colony framework. Until S7 (the desert mod of its own)
 * it sits in the savanna folder under the savanna's mod name; the loader runs each start once.
 */
@Mod(DesertMod.MODID)
public class DesertMod {
    /** The desert's mod name: still the savanna's until S7, when it becomes {@code desertcolonies}. */
    public static final String MODID = "antcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DesertMod(IEventBus modEventBus) {
        // The bodies' plans (bone names, spine, climbing numbers), read by the framework from this mod's files.
        BodyPlans.signUp(DesertMod.class, "/assets/" + MODID + "/body_plans/");
        DesertBlocks.BLOCKS.register(modEventBus);
        DesertFeatures.FEATURES.register(modEventBus);
        DesertEntities.ENTITY_TYPES.register(modEventBus);
        DesertItems.ITEMS.register(modEventBus);
        DesertParts.signUp();
        DesertEvents.signUpCreativeTab();
        DesertTests.signUp();
        // In-game tests: only loaded when the game is started in game-test mode.
        modEventBus.addListener((RegisterGameTestsEvent event) -> {
            event.register(com.antcolonies.desert.gametest.NestTests.class);
            event.register(com.antcolonies.desert.gametest.NavigationTests.class);
        });
    }
}
