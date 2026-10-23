package com.antcolonies.desert;

import com.antcolonies.desert.colony.DesertParts;
import com.antcolonies.desert.gametest.DesertTests;
import com.antcolonies.desert.registry.DesertBlocks;
import com.antcolonies.desert.registry.DesertEntities;
import com.antcolonies.desert.registry.DesertFeatures;
import com.antcolonies.desert.registry.DesertItems;
import com.antcolonies.desert.species.DesertNumbers;
import com.antcolonies.desert.species.HarvesterBuiltIn;

import org.slf4j.Logger;

import com.antcolonies.entity.body.BodyPlans;
import com.antcolonies.species.SpeciesProfiles;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * The desert mod's start ("Desert Colonies", the split, steps S3 to S7, {@code docs/plans/mod-split.md}): the red
 * harvester ant signs up its parts, blocks, items, creatures and tests here, on top of the colony framework. A mod of
 * its own since S7, in the {@code desert} folder; it never sees the savanna mod.
 */
@Mod(DesertMod.MODID)
public class DesertMod {
    /** The desert mod's name (the split, step S7): every harvester thing is saved as {@code desertcolonies:...}. */
    public static final String MODID = "desertcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DesertMod(IEventBus modEventBus) {
        // The bodies' plans (bone names, spine, climbing numbers), read by the framework from this mod's files.
        BodyPlans.signUp(DesertMod.class, "/assets/" + MODID + "/body_plans/");
        DesertBlocks.BLOCKS.register(modEventBus);
        DesertFeatures.FEATURES.register(modEventBus);
        DesertEntities.ENTITY_TYPES.register(modEventBus);
        DesertItems.ITEMS.register(modEventBus);
        DesertNumbers.signUp(); // its own sections of the species file (Apex part 3, T1), before it loads
        DesertParts.signUp();
        // The harvester's numbers built into the code, for when its species file is missing or broken.
        SpeciesProfiles.signUpBuiltIn(DesertSpecies.RED_HARVESTER_ANT, HarvesterBuiltIn.NUMBERS);
        DesertEvents.signUpCreativeTab();
        DesertTests.signUp();
        // In-game tests: only loaded when the game is started in game-test mode.
        modEventBus.addListener((RegisterGameTestsEvent event) -> {
            event.register(com.antcolonies.desert.gametest.NestTests.class);
            event.register(com.antcolonies.desert.gametest.NavigationTests.class);
        });
    }
}
