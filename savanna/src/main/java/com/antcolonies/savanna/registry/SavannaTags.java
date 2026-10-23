package com.antcolonies.savanna.registry;

import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** The savanna's own tags (the split, step S6: moved out of the framework's {@code ModTags}). */
public class SavannaTags {
    /** Creatures that walk through the termite hole block (data/antcolonies/tags/entity_type/passes_termite_holes.json). */
    public static final TagKey<EntityType<?>> PASSES_TERMITE_HOLES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "passes_termite_holes"));

    /**
     * Creatures small enough to crawl through the 6-pixel channel of a termite tunnel, a hollow spire piece or a chimney
     * mouth's tube (data/antcolonies/tags/entity_type/fits_termite_tunnels.json): termite workers, not soldiers.
     */
    public static final TagKey<EntityType<?>> FITS_TERMITE_TUNNELS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "fits_termite_tunnels"));
}
