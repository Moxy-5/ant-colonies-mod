package com.antcolonies.species;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.entity.AntLarvaEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Apex step 9c: the bodies a species' colony makes: what its eggs hatch into, the body a dead member leaves, and the mob
 * each caste grows up into. Each species fills in its row where it registers its mobs ({@code ModEntities}), so the
 * shared brood and population code makes the right mob without asking which species it is. (Not in the species file:
 * a mob type is code, not a number to tweak.)
 */
public record SpeciesBodies(Supplier<EntityType<AntLarvaEntity>> young, Supplier<EntityType<AntCorpseEntity>> corpse,
        Map<String, Supplier<? extends EntityType<? extends AbstractAntEntity>>> castes) {
    private static final Map<ResourceLocation, SpeciesBodies> BY_SPECIES = new HashMap<>();

    /** Fills in a species' row (once, while the mod loads). */
    public static void register(ResourceLocation species, SpeciesBodies bodies) {
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

    /** The mob a member of this caste is, or null if the species has none for it (the queen, the king: never made here). */
    @Nullable
    public EntityType<? extends AbstractAntEntity> typeFor(String caste) {
        Supplier<? extends EntityType<? extends AbstractAntEntity>> type = castes.get(caste);
        return type == null ? null : type.get();
    }
}
