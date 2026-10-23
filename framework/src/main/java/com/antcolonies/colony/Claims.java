package com.antcolonies.colony;

import java.util.HashMap;
import java.util.Map;

/**
 * "Who is already on the way": things ants have claimed (a seed, an egg, a job), each until a game time when the claim
 * runs out by itself, so a claim of an ant that died or forgot is never stuck for good. Not saved: after a reload the
 * ants simply claim again.
 */
public final class Claims<K> {
    private final Map<K, Long> until = new HashMap<>();

    /** Forgets the claims that have run out by {@code now}. */
    public void prune(long now) {
        until.values().removeIf(time -> time <= now);
    }

    /** Claims {@code key} until the game time {@code time} (replacing an older claim on it). */
    public void put(K key, long time) {
        until.put(key, time);
    }

    public void remove(K key) {
        until.remove(key);
    }

    public boolean contains(K key) {
        return until.containsKey(key);
    }

    /** How many claims there are (call {@link #prune} first to leave out the ones that ran out). */
    public int size() {
        return until.size();
    }
}
