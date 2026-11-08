package com.antcolonies.fight;

import com.antcolonies.colony.LandReady;
import com.antcolonies.colony.GroundTop;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import com.antcolonies.block.FacedWall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
        return mayAttack(profile, entity, null);
    }

    /**
     * As {@link #mayAttack(SpeciesProfile, Entity)}, for this insect: also a foe that provoked its colony (M8 step 2e, a
     * player who broke into the nest, hit a member, ...: the colony's question {@code PROVOKED_BY}).
     */
    public static boolean mayAttack(AbstractAntEntity insect, Entity entity) {
        return mayAttack(insect.profile(), entity, insect);
    }

    private static boolean mayAttack(SpeciesProfile profile, Entity entity, @Nullable AbstractAntEntity asker) {
        if (asker != null && entity instanceof net.minecraft.world.entity.player.Player && entity.isAlive()) {
            com.antcolonies.colony.Colony colony = asker.getColonyId() == null ? null : asker.getColony();
            return colony != null && colony.questions().ask(asker.level(), com.antcolonies.colony.ColonyQuestions.PROVOKED_BY,
                    new com.antcolonies.colony.ColonyQuestions.Provoked(asker, entity));
        }
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
        if (foe instanceof net.minecraft.world.entity.player.Player player) {
            // M8 step 2e: a sting on a player, by the species' defence numbers (2 hearts before armour, and the pain).
            DefenceNumbers defence = DefenceNumbers.of(striker.profile()).orElse(null);
            if (defence != null) {
                boolean stung = player.hurt(striker.damageSources().mobAttack(striker), defence.sting().damage());
                if (stung && player.isAlive()) {
                    Pain.stung(player, defence.sting());
                }
                return stung;
            }
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

    /** How many cells {@link #isShutIn} walks before it calls a place shut in. */
    private static final int SHUT_IN_SEARCH = 40;

    /**
     * Whether this cell is out in the open (M8 step 2g): nothing solid over it (the ground's top, leaves of any tree aside,
     * is at or below it). A termite under a log, under its mud or in a gallery under the ground is not.
     */
    public static boolean isInTheOpen(Level level, BlockPos cell) {
        // BUGS 113, 116: the ground's top with every tree's leaves passed through (the giant savanna trees' crowns are
        // no roof: under one 40 blocks wide every cell looked covered and a Matabele on open grass counted itself shut in).
        return cell.getY() >= GroundTop.y(level, cell.getX(), cell.getZ());
    }

    /**
     * Whether this cell is shut in (M8 step 2g): no way out of it into the open within {@value #SHUT_IN_SEARCH} cells,
     * through cells that are not solid and past no plate of termite mud ({@link FacedWall}). A termite eating under a
     * log, in a closed feeding gallery or under its sheeting is shut in, so a hunter does not plan a walk to it that it
     * cannot find (the user's report of 2026-10-25: 600 failed route searches in 2 minutes): the way in must be opened
     * first. A cheap check only; the route search still has the last word.
     */
    /**
     * How many steps this cell lies from a cell in the open ({@link #isInTheOpen}), through cells that are not solid and
     * past no plate of termite mud ({@link FacedWall}), as {@link #isShutIn} walks; 0 in the open itself, -1 if more than
     * {@code most}. Cells {@code skip} says yes to are never walked through (a colony's own built home: its mound). The
     * raid queue and openings fix (2026-11-06): a member that close to an opening is at it, reachable.
     */
    public static int stepsToTheOpen(Level level, BlockPos cell, int most, java.util.function.Predicate<BlockPos> skip) {
        List<BlockPos> ring = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ring.add(cell.immutable());
        seen.add(cell.immutable());
        for (int steps = 0; steps <= most && !ring.isEmpty(); steps++) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos at : ring) {
                if (!LandReady.at(level, at)) {
                    continue;
                }
                if (isInTheOpen(level, at)) {
                    return steps;
                }
                if (steps == most) {
                    continue;
                }
                BlockState here = level.getBlockState(at);
                for (Direction side : Direction.values()) {
                    BlockPos to = at.relative(side);
                    if (seen.contains(to) || FacedWall.hasFace(here, side) || skip.test(to)) {
                        continue;
                    }
                    BlockState there = level.getBlockState(to);
                    if (there.isCollisionShapeFullBlock(level, to) || FacedWall.hasFace(there, side.getOpposite())) {
                        continue;
                    }
                    seen.add(to);
                    next.add(to);
                }
            }
            ring = next;
        }
        return -1;
    }

    public static boolean isShutIn(Level level, BlockPos cell) {
        return isShutIn(level, cell, SHUT_IN_SEARCH);
    }

    /**
     * {@link #isShutIn(Level, BlockPos)} walking up to {@code most} cells. BUGS 185: on a giant baobab's trunk under its
     * bulge, 40 cells never reached the open a few steps away and a Matabele scout took itself for sealed in, so the
     * bite-out reflex asks with a wider look. Each column's ground top is read once; unloaded land is never walked into.
     */
    public static boolean isShutIn(Level level, BlockPos cell, int most) {
        java.util.Map<Long, Integer> tops = new java.util.HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(cell.immutable());
        seen.add(cell.immutable());
        while (!queue.isEmpty() && seen.size() <= most) {
            BlockPos at = queue.poll();
            int top = tops.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(at.getX(), at.getZ()),
                    column -> GroundTop.y(level, at.getX(), at.getZ()));
            if (at.getY() >= top) {
                return false; // in the open, as isInTheOpen
            }
            BlockState here = level.getBlockState(at);
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (seen.contains(next) || FacedWall.hasFace(here, side) || !LandReady.at(level, next)) {
                    continue;
                }
                BlockState there = level.getBlockState(next);
                if (there.isCollisionShapeFullBlock(level, next) || FacedWall.hasFace(there, side.getOpposite())) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        return true;
    }
}
