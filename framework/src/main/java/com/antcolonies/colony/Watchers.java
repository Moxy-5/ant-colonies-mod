package com.antcolonies.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Who is watching (the user, 2026-11-08): one shared rule for what a player may see. A player in any game mode,
 * spectators included, watches: no colony goes over to numbers, folds a real insect away, raids out of sight or lets a
 * tree catch up on its days where a watcher is near. Before, a few places skipped spectators, so a raid happened as
 * numbers and a scout vanished right in front of a player watching in spectator mode.
 *
 * <p>Only for what is shown. Whether insects feel, fear or fight a player (noticing, the alarm, stings, danger) stays
 * with each of those, and there a spectator is not there at all.
 */
public final class Watchers {
    private Watchers() {
    }

    /** Whether this player watches: alive and in the world, in any game mode. */
    public static boolean watches(Player player) {
        return player.isAlive() && !player.isRemoved();
    }

    /** Whether a watcher is within {@code radius} blocks of this place, sideways. */
    public static boolean near(ServerLevel level, BlockPos at, int radius) {
        for (Player player : level.players()) {
            if (watches(player)) {
                double dx = player.getX() - (at.getX() + 0.5);
                double dz = player.getZ() - (at.getZ() + 0.5);
                if (dx * dx + dz * dz <= (double) radius * radius) {
                    return true;
                }
            }
        }
        return false;
    }
}
