package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The harvester ant's items: its blocks', the grama seeds, ant bread and its spawn eggs (the split, step S4). */
public class DesertItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AntColoniesMod.MODID);

    public static final DeferredItem<BlockItem> BURROWED_DIRT = ITEMS.registerSimpleBlockItem(DesertBlocks.BURROWED_DIRT);

    public static final DeferredItem<BlockItem> DEEP_SOIL = ITEMS.registerSimpleBlockItem(DesertBlocks.DEEP_SOIL);

    public static final DeferredItem<BlockItem> SEED_PILE = ITEMS.registerSimpleBlockItem(DesertBlocks.SEED_PILE);

    public static final DeferredItem<BlockItem> BREAD_PILE = ITEMS.registerSimpleBlockItem(DesertBlocks.BREAD_PILE);

    /** Blue grama seeds: what a ripe blue grama plant sheds and drops when broken; planted, they grow into blue grama. */
    public static final DeferredItem<ItemNameBlockItem> BLUE_GRAMA_SEEDS = ITEMS.register("blue_grama_seeds",
            () -> new ItemNameBlockItem(DesertBlocks.BLUE_GRAMA.get(), new Item.Properties()));

    /** Black grama seeds, the same for black grama (plantable on sand and red sand too). */
    public static final DeferredItem<ItemNameBlockItem> BLACK_GRAMA_SEEDS = ITEMS.register("black_grama_seeds",
            () -> new ItemNameBlockItem(DesertBlocks.BLACK_GRAMA.get(), new Item.Properties()));

    /** One piece of ant bread: what the ants carry from the mill to the queen, and what a broken bread pile drops. */
    public static final DeferredItem<Item> ANT_BREAD = ITEMS.registerSimpleItem("ant_bread");

    /** Rich ant bread, milled from rich seeds (pumpkin): fed to the queen it starts a burst of eggs. */
    public static final DeferredItem<Item> RICH_ANT_BREAD = ITEMS.registerSimpleItem("rich_ant_bread");

    public static final DeferredItem<DeferredSpawnEggItem> WORKER_ANT_SPAWN_EGG =
            ITEMS.register("worker_ant_spawn_egg",
                    () -> new DeferredSpawnEggItem(DesertEntities.WORKER_ANT, 0x5A1A0C, 0xD9A441, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> QUEEN_ANT_SPAWN_EGG =
            ITEMS.register("queen_ant_spawn_egg",
                    () -> new DeferredSpawnEggItem(DesertEntities.QUEEN_ANT, 0x7A2E14, 0x2B1208, new Item.Properties()));
}
