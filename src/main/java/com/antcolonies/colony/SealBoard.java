package com.antcolonies.colony;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;

/**
 * Who is working on which opening, and how far along it is. Any number of ants can work on the same opening:
 * every pack of soil adds a share of the progress, so the more ants there are, the sooner it is closed.
 * Not saved (a half-closed hole just starts again after a reload).
 */
final class SealBoard {
    private final Map<BlockPos, Map<UUID, Long>> claims = new HashMap<>();
    private final Map<BlockPos, Float> progress = new HashMap<>();

    /** Forgets the claims of ants that stopped without saying so (they ran out of time). */
    void expire(long now) {
        claims.values().forEach(ants -> ants.values().removeIf(until -> until <= now));
        claims.values().removeIf(Map::isEmpty);
    }

    int workers(BlockPos hole) {
        Map<UUID, Long> ants = claims.get(hole);
        return ants == null ? 0 : ants.size();
    }

    void claim(BlockPos hole, UUID ant, long until) {
        claims.computeIfAbsent(hole.immutable(), key -> new HashMap<>()).put(ant, until);
    }

    void release(BlockPos hole, UUID ant) {
        Map<UUID, Long> ants = claims.get(hole);
        if (ants != null) {
            ants.remove(ant);
            if (ants.isEmpty()) {
                claims.remove(hole);
            }
        }
    }

    /** Adds a share of work to an opening and returns the total (1.0 = closed). */
    float addProgress(BlockPos hole, float amount) {
        return progress.merge(hole.immutable(), amount, Float::sum);
    }

    void clear(BlockPos hole) {
        progress.remove(hole);
        claims.remove(hole);
    }
}
