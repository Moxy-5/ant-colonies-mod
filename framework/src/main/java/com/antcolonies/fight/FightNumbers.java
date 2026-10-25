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
 * <li>{@code look_over_ticks}: how long (the shortest and longest, ticks) one of its hunters stands over a foe it has
 * just felled, feeling it with its antennae, before it turns to the next (M8 fight polish, round A); none if empty.</li>
 * </ul>
 * A body's {@code venom_ticks}: how long the venom that fells it takes to work (it staggers meanwhile, then flips onto
 * its back paralysed); 0 at once. Its {@code dodge}: the chance a hunter's grab at it fails (it twists away, M8 fight
 * polish, round B); its {@code bite_back}: the chance that, as the grab fails, it bites the hunter first (a soldier).
 * Its {@code leg_cut}: the chance one of its blows that lands bites a leg off the foe, if the foe's body
 * {@code loses_legs} (M8 step 2d: the termite soldiers' cutting jaws; the Matabele's legs). Its {@code clamp}: the chance
 * one of its blows that lands on such a foe clamps on to a leg instead, riding along ({@link com.antcolonies.entity.Clamp}, M8 step 2c).
 * <ul>
 * <li>{@code give_up_ticks}: how long one of its hunters sent to fight keeps searching round the spot with no foe in
 * reach before the fight is over (round B; 600 if the file says nothing).</li>
 * <li>{@code flee}: which of its bodies run from a hunter they feel close ({@code bodies}), how close
 * ({@code feel_radius}, blocks), how fast ({@code speed}, times its walk) and how long they then stay in
 * ({@code hide_ticks}); none if absent (round B: the termite workers dive down their holes).</li>
 * <li>{@code clamp}: how one of its bodies that clamps on holds ({@link Hold}: its squeeze, and the look of its head left
 * locked on the leg once it dies); none if it never clamps (M8 step 2c).</li>
 * <li>{@code clamped}: what a clamp on one of its own insects does ({@link Clamped}: the slowing, the care in the nest);
 * none if nothing can clamp on it.</li>
 * </ul>
 * Insects of another colony of the same species stay enemies of the alarm (as before), but are not attacked: the
 * neighbours yield to each other ({@code colony.Neighbours}).
 */
public record FightNumbers(Map<String, Body> bodies, List<String> foeTypes, List<ResourceLocation> foeSpecies,
        List<ResourceLocation> prey, int paralysedTicks, List<Integer> lookOverTicks, int giveUpTicks, Optional<Flee> flee,
        Optional<Hold> clamp, Optional<Clamped> clamped) {

    /** How long a hunter searches round its spot with no foe before giving up, if its species file says nothing. */
    public static final int DEFAULT_GIVE_UP_TICKS = 600;

    /** How long a paralysed insect lies before it dies, if its species file says nothing (a day, my pick). */
    public static final int DEFAULT_PARALYSED_TICKS = 24000;

    public static final Codec<FightNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Body.CODEC).fieldOf("bodies").forGetter(FightNumbers::bodies),
            Codec.STRING.listOf().optionalFieldOf("foe_types", List.of()).forGetter(FightNumbers::foeTypes),
            ResourceLocation.CODEC.listOf().optionalFieldOf("foe_species", List.of()).forGetter(FightNumbers::foeSpecies),
            ResourceLocation.CODEC.listOf().optionalFieldOf("prey", List.of()).forGetter(FightNumbers::prey),
            Codec.INT.optionalFieldOf("paralysed_ticks", DEFAULT_PARALYSED_TICKS).forGetter(FightNumbers::paralysedTicks),
            Codec.INT.listOf().optionalFieldOf("look_over_ticks", List.of()).forGetter(FightNumbers::lookOverTicks),
            Codec.INT.optionalFieldOf("give_up_ticks", DEFAULT_GIVE_UP_TICKS).forGetter(FightNumbers::giveUpTicks),
            Flee.CODEC.optionalFieldOf("flee").forGetter(FightNumbers::flee),
            Hold.CODEC.optionalFieldOf("clamp").forGetter(FightNumbers::clamp),
            Clamped.CODEC.optionalFieldOf("clamped").forGetter(FightNumbers::clamped)
    ).apply(i, FightNumbers::new));

    /**
     * How an insect of a species that clamps holds on (M8 step 2c): every {@code squeeze_ticks} it squeezes, for
     * {@code squeeze_share} of its bite's damage (and its {@code leg_cut} chance takes the held leg, the insect dropping
     * off with it); dead, its head stays locked on the leg, drawn with {@code head_look} (a texture) at
     * {@code head_size} times its body's width.
     */
    public record Hold(int squeezeTicks, double squeezeShare, ResourceLocation headLook, double headSize) {
        public static final Codec<Hold> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("squeeze_ticks", 400).forGetter(Hold::squeezeTicks),
                Codec.DOUBLE.optionalFieldOf("squeeze_share", 0.33).forGetter(Hold::squeezeShare),
                ResourceLocation.CODEC.fieldOf("head_look").forGetter(Hold::headLook),
                Codec.DOUBLE.optionalFieldOf("head_size", 0.5).forGetter(Hold::headSize)
        ).apply(i, Hold::new));
    }

    /**
     * What a clamp does to one of a species' insects (M8 step 2c): a living one slows it by {@code slowing} (it fights no
     * more and goes home), a dead head by {@code head_slowing}; back in the nest a nestmate works on it
     * {@code care_ticks}, and the head comes off clean with the chance {@code clean} (else the leg comes off with it).
     */
    public record Clamped(double slowing, double headSlowing, int careTicks, double clean) {
        public static final Codec<Clamped> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("slowing", 0.75).forGetter(Clamped::slowing),
                Codec.DOUBLE.optionalFieldOf("head_slowing", 0.1).forGetter(Clamped::headSlowing),
                Codec.INT.optionalFieldOf("care_ticks", 600).forGetter(Clamped::careTicks),
                Codec.DOUBLE.optionalFieldOf("clean", 0.9).forGetter(Clamped::clean)
        ).apply(i, Clamped::new));
    }

    /** Who of a species runs from a hunter it feels close, how close, how fast, and how long it then stays in. */
    public record Flee(List<String> bodies, double feelRadius, double speed, int hideTicks) {
        public static final Codec<Flee> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("bodies").forGetter(Flee::bodies),
                Codec.DOUBLE.optionalFieldOf("feel_radius", 3.0).forGetter(Flee::feelRadius),
                Codec.DOUBLE.optionalFieldOf("speed", 1.3).forGetter(Flee::speed),
                Codec.INT.optionalFieldOf("hide_ticks", 1200).forGetter(Flee::hideTicks)
        ).apply(i, Flee::new));
    }

    public static final SpeciesSection<FightNumbers> SECTION = SpeciesSection.signUp("fight", CODEC);

    /** One body's numbers. */
    public record Body(double health, double damage, int stingsToFall, int venomTicks, double dodge, double biteBack,
            double legCut, boolean losesLegs, double clamp) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("health").forGetter(Body::health),
                Codec.DOUBLE.fieldOf("damage").forGetter(Body::damage),
                Codec.INT.optionalFieldOf("stings_to_fall", 1).forGetter(Body::stingsToFall),
                Codec.INT.optionalFieldOf("venom_ticks", 0).forGetter(Body::venomTicks),
                Codec.DOUBLE.optionalFieldOf("dodge", 0.0).forGetter(Body::dodge),
                Codec.DOUBLE.optionalFieldOf("bite_back", 0.0).forGetter(Body::biteBack),
                Codec.DOUBLE.optionalFieldOf("leg_cut", 0.0).forGetter(Body::legCut),
                Codec.BOOL.optionalFieldOf("loses_legs", false).forGetter(Body::losesLegs),
                Codec.DOUBLE.optionalFieldOf("clamp", 0.0).forGetter(Body::clamp)
        ).apply(i, Body::new));
    }

    /** Signs the section up (at start, before the species files load). */
    public static void signUp() {
    }

    /** A species' fight numbers, if its file has them. */
    public static Optional<FightNumbers> of(SpeciesProfile profile) {
        return profile.section(SECTION);
    }

    /** How long (ticks) a hunter stands over a foe it has felled, by the dice; 0 for not at all. */
    public int lookOverTicks(net.minecraft.util.RandomSource random) {
        if (lookOverTicks.isEmpty()) {
            return 0;
        }
        int least = lookOverTicks.get(0);
        int most = lookOverTicks.size() > 1 ? Math.max(least, lookOverTicks.get(1)) : least;
        return least + random.nextInt(most - least + 1);
    }

    /** One body's numbers, if the file has them. */
    public Optional<Body> body(String name) {
        return Optional.ofNullable(bodies.get(name));
    }
}
