package com.antcolonies.entity;

import com.antcolonies.colony.Colony;

/**
 * Apex step 9c: a mob a colony raises from its own young (a worker, a soldier): it belongs to the colony from the start.
 * The shared brood code grows a young one into any of them without naming its species.
 */
public interface ColonyBorn {
    /** It hatched in this colony's nest: it belongs to it from the start (and is counted under its caste). */
    void joinColony(Colony colony);

    /** It has just grown up at {@code now} after its last meal as a young one (a soldier, fed by nurses, is full). */
    default void lastMealAsYoung(long now) {
    }
}
