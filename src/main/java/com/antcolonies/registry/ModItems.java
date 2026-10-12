package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AntColoniesMod.MODID);

    public static final DeferredItem<BlockItem> BURROWED_DIRT = ITEMS.registerSimpleBlockItem(ModBlocks.BURROWED_DIRT);
    public static final DeferredItem<BlockItem> DEEP_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.DEEP_SOIL);
    public static final DeferredItem<BlockItem> SEED_PILE = ITEMS.registerSimpleBlockItem(ModBlocks.SEED_PILE);
    public static final DeferredItem<BlockItem> BREAD_PILE = ITEMS.registerSimpleBlockItem(ModBlocks.BREAD_PILE);

    /** One piece of ant bread: what the ants carry from the mill to the queen, and what a broken bread pile drops. */
    public static final DeferredItem<Item> ANT_BREAD = ITEMS.registerSimpleItem("ant_bread");

    /** Rich ant bread, milled from rich seeds (pumpkin): fed to the queen it starts a burst of eggs. */
    public static final DeferredItem<Item> RICH_ANT_BREAD = ITEMS.registerSimpleItem("rich_ant_bread");

    public static final DeferredItem<DeferredSpawnEggItem> WORKER_ANT_SPAWN_EGG =
            ITEMS.register("worker_ant_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.WORKER_ANT, 0x5A1A0C, 0xD9A441, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> QUEEN_ANT_SPAWN_EGG =
            ITEMS.register("queen_ant_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.QUEEN_ANT, 0x7A2E14, 0x2B1208, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_KING_SPAWN_EGG =
            ITEMS.register("termite_king_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.TERMITE_KING, 0x42210F, 0xC4923F, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_QUEEN_SPAWN_EGG =
            ITEMS.register("termite_queen_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.TERMITE_QUEEN, 0xEEE2C4, 0x42210F, new Item.Properties()));
}
