package com.antcolonies.entity.goal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.player.Player;

/**
 * What blind insects feel through the ground (the animation engine, 2026-10-16): a player jumping or landing shakes it
 * hard (told by {@code ModEvents}, asked by {@link NoticeGoal}). Walking and sprinting are read from the players
 * themselves. Server only.
 */
public final class GroundShakes {
    /** Each player's last jump or landing (game time). */
    private static final Map<UUID, Long> LAST_HARD = new HashMap<>();

    private GroundShakes() {
    }

    /** A player jumped or landed. */
    public static void hard(Player player) {
        LAST_HARD.put(player.getUUID(), player.level().getGameTime());
    }

    /** Whether this player jumped or landed since this game time. */
    public static boolean hardSince(Player player, long since) {
        Long at = LAST_HARD.get(player.getUUID());
        return at != null && at >= since && at <= player.level().getGameTime();
    }
}
