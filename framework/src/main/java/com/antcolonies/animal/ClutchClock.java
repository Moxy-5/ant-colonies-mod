package com.antcolonies.animal;

/**
 * When a clutch of eggs hatches (the animal life kit, {@code docs/plans/animal-life-cycle.md}), worked out from the
 * game time alone, so a clutch needs nothing ticking while it waits: it hatches once it is old enough <b>and</b> a
 * watcher is near <b>and</b> there is room for its kind ({@link RoomToBreed}), so no young appear in empty land. A
 * clutch {@link #SHELLS_AFTER} old that cannot hatch then has already hatched and gone unseen, or failed: only empty
 * shells are left.
 */
public final class ClutchClock {
    /** A clutch this old that cannot hatch now is left as empty shells (ticks: 10 game days). */
    public static final long SHELLS_AFTER = 10L * LifeNumbers.DAY;

    /** What a clutch does now. */
    public enum Now {
        /** Keeps waiting. */
        WAIT,
        /** Hatches here, now. */
        HATCH,
        /** Is empty shells: hatched and gone, or failed. */
        SHELLS
    }

    private ClutchClock() {
    }

    /**
     * What a clutch laid at {@code laid} does at game time {@code now}, given its hatching time, whether a watcher is
     * near and whether its kind has room.
     */
    public static Now decide(long now, long laid, long hatchTicks, boolean watched, boolean room) {
        long age = now - laid;
        if (age < hatchTicks) {
            return Now.WAIT;
        }
        if (watched && room) {
            return Now.HATCH;
        }
        return age >= SHELLS_AFTER ? Now.SHELLS : Now.WAIT;
    }
}
