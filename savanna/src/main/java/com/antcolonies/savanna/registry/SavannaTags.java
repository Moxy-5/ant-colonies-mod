package com.antcolonies.savanna.registry;

import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

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

    /**
     * Lying wood a Matabele colony nests under (M8 step 1d; data/antcolonies/tags/block/matabele_nest_logs.json): the dead
     * trees' logs, and any log, so a player can lay one down.
     */
    public static final TagKey<Block> MATABELE_NEST_LOGS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_nest_logs"));

    /**
     * What a Matabele major bites open to reach termites (M8 step 2b; data/antcolonies/tags/block/matabele_breaks_open.json):
     * the termites' mud (sheeting, casts) and thin soil over a gallery, never the mound.
     */
    public static final TagKey<Block> MATABELE_BREAKS_OPEN = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_breaks_open"));
}
