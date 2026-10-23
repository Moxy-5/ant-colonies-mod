package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModTags {
    /** Blocks ants are allowed to dig (data/antcolonies/tags/block/diggable.json). */
    public static final TagKey<Block> DIGGABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "diggable"));

    /** Seeds ants take to the food store (data/antcolonies/tags/item/seeds.json). Will become the food tag of the species profile. */
    public static final TagKey<Item> SEEDS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "seeds"));

    /** Creatures that walk through the termite hole block (data/antcolonies/tags/entity_type/passes_termite_holes.json). */
    public static final TagKey<EntityType<?>> PASSES_TERMITE_HOLES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "passes_termite_holes"));

    /**
     * Creatures small enough to crawl through the 6-pixel channel of a termite tunnel, a hollow spire piece or a chimney
     * mouth's tube (data/antcolonies/tags/entity_type/fits_termite_tunnels.json): termite workers, not soldiers.
     */
    public static final TagKey<EntityType<?>> FITS_TERMITE_TUNNELS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "fits_termite_tunnels"));

    /** Rich seeds: bread milled from them is rich ant bread (data/antcolonies/tags/item/rich_seeds.json). */
    /**
     * What the nurses carry to the queen and the young (data/antcolonies/tags/item/nurse_food.json): ant bread, plain
     * and rich, and the termites' fungus nodules. The split, step S4: the shared insect names no species' food.
     */
    public static final TagKey<Item> NURSE_FOOD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "nurse_food"));

    /** The rich kind of it, that starts a burst of eggs (data/antcolonies/tags/item/rich_nurse_food.json): rich ant bread. */
    public static final TagKey<Item> RICH_NURSE_FOOD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "rich_nurse_food"));

    public static final TagKey<Item> RICH_SEEDS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "rich_seeds"));

    /**
     * Blocks taken as a plug the workers packed besides each species' own plug block (data/antcolonies/tags/block/old_plugs.json):
     * Burrowed Dirt, the plug of older saves.
     */
    public static final TagKey<Block> OLD_PLUGS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "old_plugs"));

    /**
     * A tree's crown or trunk (data/antcolonies/tags/block/tree_crowns.json): the food on the ground under it is looked
     * for too, as a crown hides it from above (every tree's leaves, and the giant savanna trees' wood and leaves).
     */
    public static final TagKey<Block> TREE_CROWNS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "tree_crowns"));
}
