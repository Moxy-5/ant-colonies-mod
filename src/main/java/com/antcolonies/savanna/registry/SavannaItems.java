package com.antcolonies.registry;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColoniesMod;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The savanna's items: its blocks', forage, fungus nodules, the mushrooms and the termites' spawn eggs (the split, step S4). */
public class SavannaItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AntColoniesMod.MODID);

    public static final DeferredItem<BlockItem> FORAGE_PILE = ITEMS.registerSimpleBlockItem(SavannaBlocks.FORAGE_PILE);

    public static final DeferredItem<BlockItem> FUNGUS_COMB = ITEMS.registerSimpleBlockItem(SavannaBlocks.FUNGUS_COMB);

    public static final DeferredItem<BlockItem> TERMITE_MOUND = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_MOUND);

    public static final DeferredItem<BlockItem> TERMITE_HOLE = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_HOLE);

    public static final DeferredItem<BlockItem> TERMITE_SPIRE = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_SPIRE);

    public static final DeferredItem<BlockItem> TERMITE_MOUTH = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_MOUTH);

    public static final DeferredItem<BlockItem> TERMITE_RAMP = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_RAMP);

    public static final DeferredItem<BlockItem> TERMITE_TUNNEL = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_TUNNEL);

    public static final DeferredItem<BlockItem> DRY_GRASS = ITEMS.registerSimpleBlockItem(SavannaBlocks.DRY_GRASS);

    public static final DeferredItem<BlockItem> TALL_DRY_GRASS = ITEMS.registerSimpleBlockItem(SavannaBlocks.TALL_DRY_GRASS);

    public static final DeferredItem<BlockItem> FALLEN_BRANCH = ITEMS.registerSimpleBlockItem(SavannaBlocks.FALLEN_BRANCH);

    public static final DeferredItem<BlockItem> DEAD_WOOD = ITEMS.registerSimpleBlockItem(SavannaBlocks.DEAD_WOOD);

    public static final DeferredItem<BlockItem> MUD_CRUST = ITEMS.registerSimpleBlockItem(SavannaBlocks.MUD_CRUST);

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

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_KING_SPAWN_EGG =
            ITEMS.register("termite_king_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_KING, 0x42210F, 0xC4923F, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_QUEEN_SPAWN_EGG =
            ITEMS.register("termite_queen_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_QUEEN, 0xEEE2C4, 0x42210F, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_WORKER_SPAWN_EGG =
            ITEMS.register("termite_worker_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_WORKER, 0xECE2C8, 0xD4AC60, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_MINOR_SOLDIER_SPAWN_EGG =
            ITEMS.register("termite_minor_soldier_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_MINOR_SOLDIER, 0xECE2C8, 0xB25E24, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_MAJOR_SOLDIER_SPAWN_EGG =
            ITEMS.register("termite_major_soldier_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_MAJOR_SOLDIER, 0xB25E24, 0x18100B, new Item.Properties()));

    /** The items of the giant savanna trees' blocks (M7.5), in creative tab order. */
    public static final List<DeferredItem<BlockItem>> TREE_ITEMS = new ArrayList<>();

    static {
        for (DeferredBlock<? extends Block> block : TreeBlocks.ALL) {
            TREE_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        }
    }
}
