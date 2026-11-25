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

    /**
     * Called when its life's clock drew its size or stage anew (growing up, and the moment it is grown): where an animal
     * changes its own look or box with it. The clock itself is counted for every animal with a life from one place
     * ({@link AnimalLife#tickEvery}), so no animal can forget it.
     */
    default void grew() {
    }

    /** Called each tick of the pair's courtship, beside its mate, {@code ticks} into it (a species' own courtship). */
    default void courting(Mob mate, int ticks) {
    }

    /** Called once on each of the pair when they have mated (where a species lays, starts carrying, digs its nest). */
    default void mated(Mob mate) {
    }

    /**
     * Whether it goes looking for a mate now ({@link SeekMateGoal}). False for one that waits for its mate to come (a
     * tilapia male waits on his nest pit).
     */
    default boolean seeksMate() {
        return true;
    }

    /** Whether a mate looking round may pick it now (a tilapia male only while he holds a nest pit; a female never). */
    default boolean takesMate() {
        return true;
    }
}
