package com.antcolonies.desert;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.desert.colony.DesertParts;
import com.antcolonies.desert.gametest.DesertTests;
import com.antcolonies.desert.registry.DesertBlocks;
import com.antcolonies.desert.registry.DesertEntities;
import com.antcolonies.desert.registry.DesertFeatures;
import com.antcolonies.desert.registry.DesertItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * The desert's start (the split, steps S3 and S4, {@code docs/plans/mod-split.md}): the red harvester ant signs up its
 * parts, blocks, items, creatures and tests here. Still one mod with the framework ({@link AntColoniesMod}) and the
 * savanna ({@link SavannaMod}); the loader runs each of the three starts once.
 */
@Mod(AntColoniesMod.MODID)
public class DesertMod {
    public DesertMod(IEventBus modEventBus) {
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
