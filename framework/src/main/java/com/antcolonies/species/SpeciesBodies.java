package com.antcolonies.species;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.registry.ModEntities;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Apex step 9c: the bodies a species' colony makes: what its eggs hatch into, the body a dead member leaves, and the mob
 * each caste grows up into. Each species fills in its row where it registers its mobs ({@code SavannaEntities},
 * {@code DesertEntities}), so the shared brood and population code makes the right mob without asking which species it
 * is. (Not in the species file: a mob type is code, not a number to tweak.) The split, step S3: with the looks
 * (textures) of its young and its dead, so the shared renderers draw each species' own without naming it.
 */
public record SpeciesBodies(Supplier<EntityType<AntLarvaEntity>> young, Supplier<EntityType<AntCorpseEntity>> corpse,
        Map<String, Supplier<? extends EntityType<? extends AbstractAntEntity>>> castes, ResourceLocation youngLook,
        ResourceLocation corpseLook) {
    private static final Map<ResourceLocation, SpeciesBodies> BY_SPECIES = new LinkedHashMap<>();

    /** Fills in a species' row (once, while the mod loads). */
    public static synchronized void register(ResourceLocation species, SpeciesBodies bodies) {
        BY_SPECIES.put(species, bodies);
    }

    /** A species' row; every species with a colony has one. */
    public static SpeciesBodies of(ResourceLocation species) {
        SpeciesBodies bodies = BY_SPECIES.get(species);
        if (bodies == null) {
            throw new IllegalStateException("no bodies registered for the species " + species);
        }
        return bodies;
    }

    /** Every species' row, in the order they were filled in (the renderers of the dead are signed up from it). */
    public static Collection<SpeciesBodies> all() {
        return Collections.unmodifiableCollection(BY_SPECIES.values());
    }

    /** The mob a member of this caste is, or null if the species has none for it (the queen, the king: never made here). */
    @Nullable
    public EntityType<? extends AbstractAntEntity> typeFor(String caste) {
        Supplier<? extends EntityType<? extends AbstractAntEntity>> type = castes.get(caste);
        return type == null ? null : type.get();
    }
}
