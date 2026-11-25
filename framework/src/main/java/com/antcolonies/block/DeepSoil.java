package com.antcolonies.block;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.ModEvents;
import com.antcolonies.worldgen.DeepSoilFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Deep Soil: the darker soil under the topsoil, laid by world generation under every dirt surface ({@link DeepSoilFeature}),
 * so the ground is soft 8 to 11 blocks down and not only vanilla's 3 or 4. Shared ground for every digging species (in
 * {@code colonyframework:diggable}): the termites' rooms, galleries and foraging tunnels run through it. It behaves like
 * dirt and is dug out whole (no ramp of its own). Until 2026-11-23 it was the desert mod's block
 * ({@code desertcolonies:deep_soil}); it moved here when the harvester ant was taken out, because the savanna's
 * insects had been digging in it all along.
 */
public final class DeepSoil {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ColonyFrameworkMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ColonyFrameworkMod.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, ColonyFrameworkMod.MODID);

    public static final DeferredBlock<Block> BLOCK = BLOCKS.registerSimpleBlock("deep_soil",
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).mapColor(MapColor.COLOR_BROWN).strength(0.8F));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK);
    /** Puts Deep Soil under the dirt (switched on by data/colonyframework/neoforge/biome_modifier/deep_soil.json). */
    public static final DeferredHolder<Feature<?>, DeepSoilFeature> FEATURE =
            FEATURES.register("deep_soil", () -> new DeepSoilFeature(NoneFeatureConfiguration.CODEC));

    private DeepSoil() {
    }

    /** Called once while the framework loads. */
    public static void signUp(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        FEATURES.register(modEventBus);
        ModEvents.addToCreativeTab(CreativeModeTabs.NATURAL_BLOCKS, 10, event -> event.accept(ITEM));
    }
}
