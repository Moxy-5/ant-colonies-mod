package com.antcolonies.colony;

/**
 * What a worker mainly does for its colony. The colony hands the roles out (see {@link Colony#roleOf}); a worker with
 * nothing to do in its own role helps with the others. Species-neutral: termites can use the same roles.
 */
public enum ColonyRole {
    /** Digs the nest, seals its walls and carries the soil out. */
    BUILDER,
    /** Goes outside for seeds and brings them to the queen or the food store. */
    FORAGER,
    /** Stays by the queen: feeds her from the food store and carries her eggs to their place. */
    NURSE
}
