package com.antcolonies.savanna;

import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.species.SavannaNumbers;
import com.antcolonies.savanna.gametest.SavannaTests;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.registry.SavannaFeatures;
import com.antcolonies.savanna.registry.SavannaItems;
import com.antcolonies.savanna.registry.SavannaStructures;
import com.antcolonies.savanna.registry.TreeBlocks;

import org.slf4j.Logger;

import com.antcolonies.entity.body.BodyPlans;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * The savanna mod's start (the split, steps S3 to S6, {@code docs/plans/mod-split.md}): the termites, the giant trees
 * and (from M8) the Matabele ants sign up their parts, blocks, items, creatures and tests here. Its own mod since S6,
 * on top of the colony framework ({@code ColonyFrameworkMod}); until S7 it still holds the desert's start
 * ({@code DesertMod}) under the same mod name, and the loader runs both starts once.
 */
@Mod(SavannaMod.MODID)
public class SavannaMod {
    /** The savanna mod's name: every termite, mound, garden and tree thing keeps its saved name ({@code antcolonies:...}). */
    public static final String MODID = "antcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SavannaMod(IEventBus modEventBus) {
        // The bodies' plans (bone names, spine, climbing numbers), read by the framework from this mod's files.
        BodyPlans.signUp(SavannaMod.class, "/assets/" + MODID + "/body_plans/");
        SavannaBlocks.BLOCKS.register(modEventBus);
        SavannaFeatures.FEATURES.register(modEventBus);
        SavannaFeatures.BIOME_MODIFIERS.register(modEventBus);
        SavannaStructures.STRUCTURE_TYPES.register(modEventBus);
        SavannaStructures.STRUCTURE_PIECES.register(modEventBus);
        SavannaEntities.ENTITY_TYPES.register(modEventBus);
        SavannaItems.ITEMS.register(modEventBus);
        SavannaNumbers.signUp(); // its own sections of the species files (Apex part 3, T1), before they load
        SavannaParts.signUp();
        SavannaEvents.signUpCreativeTab();
        SavannaTests.signUp();
        // How the giant savanna trees' blocks burn (Minecraft's fire table is filled once, at start).
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(TreeBlocks::setFlammable));
        // In-game tests: only loaded when the game is started in game-test mode.
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(com.antcolonies.savanna.gametest.TermiteTests.class));
        LOGGER.info("Ant & Termite Colonies loaded");
    }
}
