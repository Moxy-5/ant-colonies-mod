package com.antcolonies.fight;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The shared fight rules (M8 step 2a, {@code docs/plans/m8-matabele.md}): who is an enemy, who may be attacked, and what
 * one blow does. Every species asks here, so a defender of any kind (the termite soldiers today, the harvester ants
 * against their neighbours later) and a hunter (the Matabele) fight by the same rules; the numbers come from each
 * species' {@code fight} object ({@link FightNumbers}).
 *
 * <p>A sting on prey (an insect of a species in the striker's {@code prey}) does not kill: it pricks (half a point,
 * never the last, so the prey's colony feels the attack) and gives one dose of venom; with {@code stings_to_fall} doses
 * the prey falls paralysed ({@link AbstractAntEntity#takeSting}). Every other blow does the striker's damage, and may
 * bite a leg off ({@code leg_cut}, M8 step 2d), or clamp on to one instead ({@code clamp}, M8 step 2c,
 * {@link com.antcolonies.entity.Clamp}).
 */
public final class Fights {
    /** What a sting on prey does to its health (it is the venom that counts). */
    private static final float STING_PRICK = 0.5F;

    private Fights() {
    }

    /**
     * Whether {@code entity} is an enemy of an insect of this species and colony (the alarm rises at it, defenders go at
     * it): an animal of the species' {@code foe_types}, an insect of another species (listed as a foe or prey, or of any
     * colony), or an insect of another colony of the same species. Never a felled insect, never anything dead.
     */
    public static boolean isFoe(SpeciesProfile profile, ResourceLocation species, @Nullable UUID colony, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        FightNumbers numbers = FightNumbers.of(profile).orElse(null);
        if (isFoeType(numbers, entity)) {
            return true;
        }
        if (!(entity instanceof AbstractAntEntity insect) || insect.isDowned()) {
            return false;
        }
        if (!insect.species().equals(species)) {
            return listed(numbers, insect.species()) || insect.getColonyId() != null;
        }
        return colony != null && insect.getColonyId() != null && !colony.equals(insect.getColonyId());
    }

    /**
     * Whether an insect of this species may attack {@code entity}: an animal of its {@code foe_types} or an insect of a
     * species it lists as a foe or prey. Not the neighbours of its own species (they yield to each other), not an
     * insect already felled (the venom working in it, or lying paralysed).
     */
    public static boolean mayAttack(SpeciesProfile profile, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        FightNumbers numbers = FightNumbers.of(profile).orElse(null);
        if (isFoeType(numbers, entity)) {
            return true;
        }
        return entity instanceof AbstractAntEntity insect && !insect.isDowned() && listed(numbers, insect.species());
    }

    /** Whether this species hunts the insect: its stings paralyse it instead of hurting it. */
    public static boolean hunts(SpeciesProfile profile, Entity entity) {
        return entity instanceof AbstractAntEntity insect
                && FightNumbers.of(profile).map(numbers -> numbers.prey().contains(insect.species())).orElse(false);
    }

    /**
     * One blow from {@code striker} on {@code foe}: on prey a paralysing sting (see the class comment), on anything else
     * the striker's damage. True if the foe was hit (alive and not already felled, for a sting). Nothing is hit by a
     * striker not standing on a floor (the user's rule, M8 fight polish, round A: {@link AbstractAntEntity#standsToFight}):
     * an insect on a wall or a ceiling, or falling, never grabs, stings or bites.
     */
    public static boolean strike(AbstractAntEntity striker, LivingEntity foe) {
        if (!foe.isAlive() || !striker.standsToFight()) {
            return false;
        }
        if (hunts(striker.profile(), foe)) {
            AbstractAntEntity prey = (AbstractAntEntity) foe;
            if (prey.isDowned()) {
                return false;
            }
            float prick = Math.min(STING_PRICK, prey.getHealth() - STING_PRICK);
            if (prick > 0.0F) {
                prey.hurt(striker.damageSources().mobAttack(striker), prick);
            }
            prey.takeSting();
            return true;
        }
        boolean hit = foe.hurt(striker.damageSources().mobAttack(striker), (float) striker.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (hit && foe.isAlive() && foe instanceof AbstractAntEntity insect && !com.antcolonies.entity.Clamp.tryClampOn(striker, insect)) {
            cutLeg(striker, insect);
        }
        return hit;
    }

    /**
     * M8 step 2c: a foe clamped on to an insect's leg squeezes: {@code share} of its bite's damage, and its
     * {@code leg_cut} chance takes the held leg off (it drops off with it).
     */
    public static void squeeze(AbstractAntEntity holder, AbstractAntEntity insect, double share) {
        float damage = (float) (holder.getAttributeValue(Attributes.ATTACK_DAMAGE) * share);
        if (damage > 0.0F) {
            insect.hurt(holder.damageSources().mobAttack(holder), damage);
        }
        double chance = holder.legCut();
        if (insect.isAlive() && chance > 0.0 && insect.canLoseLegs() && holder.getRandom().nextDouble() < chance) {
            insect.loseLeg(insect.clamp().leg());
        }
    }

    /**
     * M8 step 2d: a blow that landed may bite a leg off: with the striker's body's {@code leg_cut} chance, if the foe's
     * body {@code loses_legs} (the termite soldiers' cutting jaws on the Matabele's legs).
     */
    private static void cutLeg(AbstractAntEntity striker, AbstractAntEntity foe) {
        double chance = striker.legCut();
        if (chance > 0.0 && foe.canLoseLegs() && striker.getRandom().nextDouble() < chance) {
            foe.loseLeg();
        }
    }

    private static boolean isFoeType(@Nullable FightNumbers numbers, Entity entity) {
        return numbers != null && numbers.foeTypes().contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    private static boolean listed(@Nullable FightNumbers numbers, ResourceLocation species) {
        return numbers != null && (numbers.foeSpecies().contains(species) || numbers.prey().contains(species));
    }
}
