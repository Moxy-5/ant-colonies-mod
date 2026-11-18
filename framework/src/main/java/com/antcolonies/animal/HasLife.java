package com.antcolonies.animal;

import net.minecraft.world.entity.Mob;

/**
 * An animal on the life kit ({@code docs/plans/animal-life-cycle.md}): it holds an {@link AnimalLife}, and may plug its
 * own special behaviour into the shared mating ({@link SeekMateGoal}) through these hooks. Each hook does nothing unless
 * the animal says otherwise.
 */
public interface HasLife {
    /** Its life (sex, stage, age, own size, readiness to breed). */
    AnimalLife life();

    /** Called each tick of the pair's courtship, beside its mate, {@code ticks} into it (a species' own courtship). */
    default void courting(Mob mate, int ticks) {
    }

    /** Called once on each of the pair when they have mated (where a species lays, starts carrying, digs its nest). */
    default void mated(Mob mate) {
    }
}
