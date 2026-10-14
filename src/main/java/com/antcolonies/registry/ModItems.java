package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;

import net.minecraft.world.food.FoodProperties;
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
    public static final DeferredItem<BlockItem> FORAGE_PILE = ITEMS.registerSimpleBlockItem(ModBlocks.FORAGE_PILE);
    public static final DeferredItem<BlockItem> FUNGUS_COMB = ITEMS.registerSimpleBlockItem(ModBlocks.FUNGUS_COMB);
    public static final DeferredItem<BlockItem> TERMITE_MOUND = ITEMS.registerSimpleBlockItem(ModBlocks.TERMITE_MOUND);
    public static final DeferredItem<BlockItem> TERMITE_HOLE = ITEMS.registerSimpleBlockItem(ModBlocks.TERMITE_HOLE);
    public static final DeferredItem<BlockItem> TERMITE_SPIRE = ITEMS.registerSimpleBlockItem(ModBlocks.TERMITE_SPIRE);
    public static final DeferredItem<BlockItem> TERMITE_MOUTH = ITEMS.registerSimpleBlockItem(ModBlocks.TERMITE_MOUTH);
    public static final DeferredItem<BlockItem> TERMITE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.TERMITE_RAMP);
    public static final DeferredItem<BlockItem> TERMITE_TUNNEL = ITEMS.registerSimpleBlockItem(ModBlocks.TERMITE_TUNNEL);
    public static final DeferredItem<BlockItem> DRY_GRASS = ITEMS.registerSimpleBlockItem(ModBlocks.DRY_GRASS);
    public static final DeferredItem<BlockItem> TALL_DRY_GRASS = ITEMS.registerSimpleBlockItem(ModBlocks.TALL_DRY_GRASS);
    public static final DeferredItem<BlockItem> FALLEN_BRANCH = ITEMS.registerSimpleBlockItem(ModBlocks.FALLEN_BRANCH);
    public static final DeferredItem<BlockItem> DEAD_WOOD = ITEMS.registerSimpleBlockItem(ModBlocks.DEAD_WOOD);
    public static final DeferredItem<BlockItem> MUD_CRUST = ITEMS.registerSimpleBlockItem(ModBlocks.MUD_CRUST);

    /** One piece of ant bread: what the ants carry from the mill to the queen, and what a broken bread pile drops. */
    public static final DeferredItem<Item> ANT_BREAD = ITEMS.registerSimpleItem("ant_bread");

    /**
     * A piece of forage: dead plant material a termite cut off a plant (dry grass, a dead bush, dying leaves, a hay
     * bale). What termites carry home to their food store, and what a broken forage pile drops.
     */
    public static final DeferredItem<Item> FORAGE = ITEMS.registerSimpleItem("forage");

    /**
     * A fungus nodule: a small white ball of fungus that grows on the termites' fungus comb. What the nurses feed the
     * termite queen and the nymphs, and what a broken comb with nodules drops.
     */
    public static final DeferredItem<Item> FUNGUS_NODULE = ITEMS.registerSimpleItem("fungus_nodule");

    /**
     * A Termitomyces mushroom, picked round a termite mound after rain: a little food raw (1 drumstick, my pick), a good
     * meal cooked. Not a block item: it is never planted (the mushrooms only come from the termites' gardens).
     */
    public static final DeferredItem<Item> TERMITOMYCES = ITEMS.registerSimpleItem("termitomyces",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build()));

    /** A cooked Termitomyces (furnace, smoker or campfire): 3 drumsticks, about as good as cooked fish (my pick). */
    public static final DeferredItem<Item> COOKED_TERMITOMYCES = ITEMS.registerSimpleItem("cooked_termitomyces",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build()));

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

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_WORKER_SPAWN_EGG =
            ITEMS.register("termite_worker_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.TERMITE_WORKER, 0xECE2C8, 0xD4AC60, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_MINOR_SOLDIER_SPAWN_EGG =
            ITEMS.register("termite_minor_soldier_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.TERMITE_MINOR_SOLDIER, 0xECE2C8, 0xB25E24, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_MAJOR_SOLDIER_SPAWN_EGG =
            ITEMS.register("termite_major_soldier_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.TERMITE_MAJOR_SOLDIER, 0xB25E24, 0x18100B, new Item.Properties()));
}
