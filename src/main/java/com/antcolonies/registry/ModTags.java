package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModTags {
    /** Blocks ants are allowed to dig (data/antcolonies/tags/block/diggable.json). */
    public static final TagKey<Block> DIGGABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "diggable"));
}
