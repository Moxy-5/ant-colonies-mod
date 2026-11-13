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
    /**
     * The measuring runs only ({@code watch/Flight}'s stand, far colonies' speed, 2026-11-09): the eyes of a pretend
     * player on a headless server, who counts as watching like a real one. Empty in a game.
     */
    private static volatile java.util.List<net.minecraft.world.phys.Vec3> pretend = java.util.List.of();

    private Watchers() {
    }

    /** The measuring runs only: a pretend player's eyes are here (an empty list: nobody). */
    public static void setPretend(java.util.List<net.minecraft.world.phys.Vec3> eyes) {
        pretend = java.util.List.copyOf(eyes);
    }

    /** The pretend players' eyes (empty in a game). */
    public static java.util.List<net.minecraft.world.phys.Vec3> pretend() {
        return pretend;
    }

    /** Whether this player watches: alive and in the world, in any game mode. */
    public static boolean watches(Player player) {
        return player.isAlive() && !player.isRemoved();
    }

    /**
     * Whether what appears or vanishes in front of this player must be hidden from it: every watcher but a spectator
     * (the user, 2026-11-13: raids looked broken watched in spectator mode). A spectator sees through every block, so
     * nothing is ever out of its sight and no insect could come out of the numbers near it; it is a camera for looking
     * at the colonies, so insects and blocks may appear and vanish in front of it. It still watches ({@link #watches}):
     * colonies near it stay real.
     */
    public static boolean hiddenFrom(Player player) {
        return watches(player) && !player.isSpectator();
    }

    /** Whether a watcher things must be hidden from ({@link #hiddenFrom}) is within {@code radius} blocks of this place, sideways. */
    public static boolean seerNear(ServerLevel level, BlockPos at, int radius) {
        return near(level, at, radius, true);
    }

    /** Whether a watcher is within {@code radius} blocks of this place, sideways. */
    public static boolean near(ServerLevel level, BlockPos at, int radius) {
        return near(level, at, radius, false);
    }

    private static boolean near(ServerLevel level, BlockPos at, int radius, boolean seersOnly) {
        for (net.minecraft.world.phys.Vec3 eye : pretend) {
            double dx = eye.x - (at.getX() + 0.5);
            double dz = eye.z - (at.getZ() + 0.5);
            if (dx * dx + dz * dz <= (double) radius * radius) {
                return true;
            }
        }
        for (Player player : level.players()) {
            if (seersOnly ? hiddenFrom(player) : watches(player)) {
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
