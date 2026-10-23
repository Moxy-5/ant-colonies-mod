package com.antcolonies.registry;

import com.antcolonies.ColonyFrameworkMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The framework's tags (its own name since the split, step S6: {@code colonyframework:diggable} and so on). The
 * framework's files under {@code data/colonyframework/tags} list only Minecraft's own things; each species mod adds its
 * blocks and items to the same tags from its own data folder (the game joins the lists).
 */
public class ModTags {
    /** Blocks ants are allowed to dig (data/colonyframework/tags/block/diggable.json). */
    public static final TagKey<Block> DIGGABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "diggable"));

    /** Seeds ants take to the food store (data/colonyframework/tags/item/seeds.json). Will become the food tag of the species profile. */
    public static final TagKey<Item> SEEDS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "seeds"));

    /** Rich seeds: bread milled from them is rich ant bread (data/colonyframework/tags/item/rich_seeds.json). */
    /**
     * What the nurses carry to the queen and the young (data/colonyframework/tags/item/nurse_food.json): ant bread, plain
     * and rich, and the termites' fungus nodules. The split, step S4: the shared insect names no species' food.
     */
    public static final TagKey<Item> NURSE_FOOD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "nurse_food"));

    /** The rich kind of it, that starts a burst of eggs (data/colonyframework/tags/item/rich_nurse_food.json): rich ant bread. */
    public static final TagKey<Item> RICH_NURSE_FOOD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "rich_nurse_food"));

    public static final TagKey<Item> RICH_SEEDS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "rich_seeds"));

    /**
     * Blocks taken as a plug the workers packed besides each species' own plug block (data/colonyframework/tags/block/old_plugs.json):
     * Burrowed Dirt, the plug of older saves.
     */
    public static final TagKey<Block> OLD_PLUGS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "old_plugs"));

    /**
     * A tree's crown or trunk (data/colonyframework/tags/block/tree_crowns.json): the food on the ground under it is looked
     * for too, as a crown hides it from above (every tree's leaves, and the giant savanna trees' wood and leaves).
     */
    public static final TagKey<Block> TREE_CROWNS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "tree_crowns"));
}
