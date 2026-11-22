package com.antcolonies.savanna.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

/**
 * Plants that bend when something big goes through them (2026-11-22, {@code docs/plans/papyrus-and-hippo-grass.md}
 * step 3; the user: "we're gonna need to add animations for them when a big animal or the player goes through them"):
 * the reeds ({@link ReedBlock}) and the hippo grass mats ({@link HippoGrassBlock}).
 *
 * <p>Block models can only tilt in 22.5 degree steps and every block change redraws its chunk section, so the smooth bend
 * is drawn by the client ({@code client/PlantPushClient}). The server only marks a pushed plant with {@link #PUSH}: 0
 * still (the plain model drawn), {@link #PUSHED} while a pusher touches it, {@link #SETTLING} for the spring back after it
 * leaves; while it is not 0 the plain model is hidden and the client draws the bending one. So a push costs two or three
 * block changes, and a reed bed nobody walks through costs nothing.
 *
 * <p>A pusher is a player or an animal at least {@link #BIG} wide (hippo, crocodile, monitor, otter, a player); a push
 * starts only when it moves (a hippo resting in reeds does not set them going), and lasts while it touches the plant.
 */
public final class PlantPush {
    public static final IntegerProperty PUSH = IntegerProperty.create("push", 0, 2);
    public static final int STILL = 0, PUSHED = 1, SETTLING = 2;
    /** The narrowest box that pushes (blocks; a player's 0.6, my pick: smaller animals slip between the stems). */
    public static final float BIG = 0.6F;
    /** How often a pushed plant looks whether its pusher is still there (ticks). */
    public static final int CHECK = 10;
    /** How long a plant stays marked after its pusher leaves, for the client's spring back to settle (ticks; the spring takes about 20). */
    public static final int SETTLE = 30;

    private PlantPush() {
    }

    /** Whether {@code entity} is big enough to push plants aside. */
    public static boolean pusher(Entity entity) {
        return entity.getBbWidth() >= BIG && !entity.isSpectator() && entity.isAlive();
    }

    /** Whether {@code entity} moved sideways since its last tick (the start of a push). */
    public static boolean moving(Entity entity) {
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        return dx * dx + dz * dz > 1.0E-4 || entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
    }

    /** Whether a pusher's box touches {@code box}. */
    public static boolean touched(Level level, AABB box) {
        return !level.getEntitiesOfClass(Entity.class, box, PlantPush::pusher).isEmpty();
    }

    /** The next step of a pushed plant's scheduled tick: what {@link #PUSH} becomes and when to look again (0 for never). */
    public record Next(int push, int delay) {
    }

    /** Where a plant marked {@code push} goes on its scheduled tick, touched or not. */
    public static Next next(int push, boolean touched) {
        if (touched) {
            return new Next(PUSHED, CHECK);
        }
        return push == PUSHED ? new Next(SETTLING, SETTLE) : new Next(STILL, 0);
    }

    /** Schedules {@code pos}'s next look after {@code delay} ticks. */
    static void lookAgain(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block block, int delay) {
        if (delay > 0) {
            level.scheduleTick(pos, block, delay);
        }
    }
}
