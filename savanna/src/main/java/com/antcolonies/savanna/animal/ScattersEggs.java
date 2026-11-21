package com.antcolonies.savanna.animal;

/**
 * A fish that scatters its eggs over the algae mats after mating ({@link ScatterEggsGoal}): the river catfish and the
 * barb (2026-11-21).
 */
public interface ScattersEggs {
    /** Eggs she still carries to scatter (0 for a male, or once she has laid). */
    int eggsToScatter();

    /** She has left (or lost) this many. */
    void scattered(int eggs);
}
