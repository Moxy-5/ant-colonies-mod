package com.antcolonies.entity;

import java.util.UUID;

/**
 * Something the nurses bring food to, one piece at a time (see {@code FeedLarvaGoal}): a larva or nymph growing up, or
 * a termite soldier, whose jaws are too big to feed itself. Only one nurse at a time is on its way to it.
 */
public interface FedByNurses {
    /** Whether it wants a piece of food now. */
    boolean wantsFood();

    /** A nurse gives it one piece. */
    void takeFood();

    /** Whether a nurse is already on its way with food for it. */
    boolean hasFeeder(long now);

    /** The nurse {@code nurse} is on its way until the game time {@code until}: the others leave it alone. */
    void claimFeeder(UUID nurse, long until);

    /** The nurse {@code nurse} is no longer on its way (if it was that one). */
    void releaseFeeder(UUID nurse);
}
