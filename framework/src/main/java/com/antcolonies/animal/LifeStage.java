package com.antcolonies.animal;

/**
 * An animal's stage of life (the animal life kit). Small animals skip {@link #TEEN} (their life file has no teen days);
 * {@link #OLD} is for the few whose life has one (the old giant crocodile), kept by {@link AnimalLife}, not by age.
 */
public enum LifeStage {
    YOUNG, TEEN, GROWN, OLD;

    /** Whether it can breed at this stage. */
    public boolean grown() {
        return this == GROWN || this == OLD;
    }
}
