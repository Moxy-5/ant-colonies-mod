package com.antcolonies.fight;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesSection;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * The fight numbers of a species (M8 step 2a, the shared fight system, {@code docs/plans/m8-matabele.md}), the
 * {@code fight} object of its species file:
 * <ul>
 * <li>{@code bodies}: each body's health and damage (a body is what {@code AbstractAntEntity.fightBody()} names: a
 * termite's worker, minor and major soldier, king and queen; a Matabele's major, minor and queen), and how many stings
 * of a hunter it takes to fall paralysed ({@code stings_to_fall}, for a species others hunt).</li>
 * <li>{@code foe_types}: animals it takes for enemies (the chickens that peck termites).</li>
 * <li>{@code foe_species}: species whose insects it fights wherever they come from, with or without a colony.</li>
 * <li>{@code prey}: species it hunts: its stings paralyse their insects instead of hurting them (the Matabele and
 * Macrotermes).</li>
 * <li>{@code paralysed_ticks}: how long one of its own insects lies paralysed before it dies, if nobody takes it.</li>
 * </ul>
 * Insects of another colony of the same species stay enemies of the alarm (as before), but are not attacked: the
 * neighbours yield to each other ({@code colony.Neighbours}).
 */
public record FightNumbers(Map<String, Body> bodies, List<String> foeTypes, List<ResourceLocation> foeSpecies,
        List<ResourceLocation> prey, int paralysedTicks) {

    /** How long a paralysed insect lies before it dies, if its species file says nothing (a day, my pick). */
    public static final int DEFAULT_PARALYSED_TICKS = 24000;

    public static final Codec<FightNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Body.CODEC).fieldOf("bodies").forGetter(FightNumbers::bodies),
            Codec.STRING.listOf().optionalFieldOf("foe_types", List.of()).forGetter(FightNumbers::foeTypes),
            ResourceLocation.CODEC.listOf().optionalFieldOf("foe_species", List.of()).forGetter(FightNumbers::foeSpecies),
            ResourceLocation.CODEC.listOf().optionalFieldOf("prey", List.of()).forGetter(FightNumbers::prey),
            Codec.INT.optionalFieldOf("paralysed_ticks", DEFAULT_PARALYSED_TICKS).forGetter(FightNumbers::paralysedTicks)
    ).apply(i, FightNumbers::new));

    public static final SpeciesSection<FightNumbers> SECTION = SpeciesSection.signUp("fight", CODEC);

    /** One body's numbers. */
    public record Body(double health, double damage, int stingsToFall) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("health").forGetter(Body::health),
                Codec.DOUBLE.fieldOf("damage").forGetter(Body::damage),
                Codec.INT.optionalFieldOf("stings_to_fall", 1).forGetter(Body::stingsToFall)
        ).apply(i, Body::new));
    }

    /** Signs the section up (at start, before the species files load). */
    public static void signUp() {
    }

    /** A species' fight numbers, if its file has them. */
    public static Optional<FightNumbers> of(SpeciesProfile profile) {
        return profile.section(SECTION);
    }

    /** One body's numbers, if the file has them. */
    public Optional<Body> body(String name) {
        return Optional.ofNullable(bodies.get(name));
    }
}
