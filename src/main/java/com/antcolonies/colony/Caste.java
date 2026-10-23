package com.antcolonies.colony;

/**
 * Apex step 9c: the caste names every species shares, so the shared colony code can count its members without naming
 * a species' body ({@code WorkerAntEntity}, {@code TermiteWorkerEntity}). A species' own castes (the termite king, the
 * soldiers) stay with its bodies and reach the shared code through {@link ColonyHooks}.
 */
public final class Caste {
    /** The workers, ants and termites alike: the caste the brood grows into unless a species hook says otherwise. */
    public static final String WORKER = "worker";

    private Caste() {
    }
}
