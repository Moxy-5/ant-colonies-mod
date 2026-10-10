package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModTags {
    /** Blocks ants are allowed to dig (data/antcolonies/tags/block/diggable.json). */
    public static final TagKey<Block> DIGGABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "diggable"));

    /** Seeds ants take to the food store (data/antcolonies/tags/item/seeds.json). Will become the food tag of the species profile. */
    public static final TagKey<Item> SEEDS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "seeds"));
}
