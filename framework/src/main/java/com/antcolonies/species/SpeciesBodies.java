package com.antcolonies.species;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.registry.ModEntities;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

/**
 * Apex step 9c: the bodies a species' colony makes: what its eggs hatch into, the body a dead member leaves, and the mob
 * each caste grows up into. Each species fills in its row where it registers its mobs ({@code SavannaEntities},
 * {@code DesertEntities}), so the shared brood and population code makes the right mob without asking which species it
 * is. (Not in the species file: a mob type is code, not a number to tweak.) The split, step S3: with the looks
 * (textures) of its young and its dead, so the shared renderers draw each species' own without naming it.
 *
 * <p>M8 step 1c: {@code pickers}, for a caste whose members come in more than one body (the Matabele ant's workers, one
 * size spread drawn with a major's or a minor's model): picks the body of each new member by the dice; a caste without
 * one always gets its {@code castes} body. {@code drawSize}: how big the shared renderers draw this species' young and
 * dead (1 = the size they were made for, the small harvester ant and termite).
 */
public record SpeciesBodies(Supplier<EntityType<AntLarvaEntity>> young, Supplier<EntityType<AntCorpseEntity>> corpse,
        Map<String, Supplier<? extends EntityType<? extends AbstractAntEntity>>> castes, ResourceLocation youngLook,
        ResourceLocation corpseLook, Map<String, Function<RandomSource, ? extends EntityType<? extends AbstractAntEntity>>> pickers,
        float drawSize) {
    private static final Map<ResourceLocation, SpeciesBodies> BY_SPECIES = new LinkedHashMap<>();

    /** A species whose every caste has one body, its young and dead drawn at the usual size. */
    public SpeciesBodies(Supplier<EntityType<AntLarvaEntity>> young, Supplier<EntityType<AntCorpseEntity>> corpse,
            Map<String, Supplier<? extends EntityType<? extends AbstractAntEntity>>> castes, ResourceLocation youngLook,
            ResourceLocation corpseLook) {
        this(young, corpse, castes, youngLook, corpseLook, Map.of(), 1.0F);
    }

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

    /** Like {@link #typeFor(String)}, for a new member: a caste with a picker gets the body the dice pick. */
    @Nullable
    public EntityType<? extends AbstractAntEntity> typeFor(String caste, RandomSource random) {
        Function<RandomSource, ? extends EntityType<? extends AbstractAntEntity>> picker = pickers.get(caste);
        return picker != null ? picker.apply(random) : typeFor(caste);
    }
}
