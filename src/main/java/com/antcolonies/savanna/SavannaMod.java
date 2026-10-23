package com.antcolonies.savanna;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.gametest.SavannaTests;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.registry.SavannaFeatures;
import com.antcolonies.savanna.registry.SavannaItems;
import com.antcolonies.savanna.registry.SavannaStructures;
import com.antcolonies.savanna.registry.TreeBlocks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * The savanna's start (the split, steps S3 and S4, {@code docs/plans/mod-split.md}): the termites, the giant trees and
 * (from M8) the Matabele ants sign up their parts, blocks, items, creatures and tests here. Still one mod with the
 * framework ({@link AntColoniesMod}) and the desert ({@link DesertMod}); the loader runs each of the three starts once.
 */
@Mod(AntColoniesMod.MODID)
public class SavannaMod {
    public SavannaMod(IEventBus modEventBus) {
        SavannaBlocks.BLOCKS.register(modEventBus);
        SavannaFeatures.FEATURES.register(modEventBus);
        SavannaFeatures.BIOME_MODIFIERS.register(modEventBus);
        SavannaStructures.STRUCTURE_TYPES.register(modEventBus);
        SavannaStructures.STRUCTURE_PIECES.register(modEventBus);
        SavannaEntities.ENTITY_TYPES.register(modEventBus);
        SavannaItems.ITEMS.register(modEventBus);
        SavannaParts.signUp();
        SavannaEvents.signUpCreativeTab();
        SavannaTests.signUp();
        // How the giant savanna trees' blocks burn (Minecraft's fire table is filled once, at start).
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(TreeBlocks::setFlammable));
        // In-game tests: only loaded when the game is started in game-test mode.
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.antcolonies.savanna.gametest.TermiteTests.class));
    }
}
