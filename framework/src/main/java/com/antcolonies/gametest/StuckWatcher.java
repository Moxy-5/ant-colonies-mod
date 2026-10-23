package com.antcolonies.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.AntNavigator;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The termites-underground safety net, step 0 (the user's plan of 2026-10-17, {@code docs/plans/termites-underground.md}):
 * fails a game test as soon as one of the colony's insects is stuck under the ground: it is walking a route (its
 * navigator is not done) out of sight of the sky, and for {@link #STUCK_TICKS} it has not got {@link #MOVED} of a block
 * from where it was (planning a new route again and again on the spot is stuck too). An insect standing still on
 * purpose (digging, eating, plugging a hole, resting) has no route, so it is never taken for stuck. The failure says
 * where, and what its route was. The rule itself is {@link Tracker} (unit tested, {@code StuckWatcherTest}).
 */
public final class StuckWatcher {
    /** How long (ticks) an insect may walk a route under the ground without getting anywhere: 20 seconds. */
    public static final int STUCK_TICKS = 400;
    /** Moving less than this (blocks) is not moving. */
    static final double MOVED = 0.3;

    private StuckWatcher() {
    }

    /**
     * The rule, kept apart from the world so it can be tested: each tick it is told, for each insect walking a route
     * under the ground, where it is; it answers how long that insect has been within {@link #MOVED} of one spot.
     */
    public static final class Tracker {
        private record Seen(Vec3 pos, long since) {
        }

        private Map<UUID, Seen> seen = new HashMap<>();
        private Map<UUID, Seen> thisTick = new HashMap<>();
        private long watchedTicks;

        /** One insect walking under the ground this tick: how long (ticks) it has not moved. */
        public long observe(UUID insect, Vec3 pos, long now) {
            watchedTicks++;
            Seen before = seen.get(insect);
            if (before == null || before.pos().distanceTo(pos) > MOVED) {
                before = new Seen(pos, now);
            }
            thisTick.put(insect, before);
            return now - before.since();
        }

        /** The tick is over: an insect not walking under the ground this tick starts afresh next time. */
        public void endTick() {
            seen = thisTick;
            thisTick = new HashMap<>();
        }

        /** How many insect-ticks of walking under the ground were watched (a test can check the watcher saw anything). */
        public long watchedTicks() {
            return watchedTicks;
        }
    }

    /** Watches the colony's insects every tick for the rest of the test ({@code colony} may give null before it is founded). */
    public static Tracker watch(GameTestHelper helper, Supplier<Colony> colony) {
        Tracker tracker = new Tracker();
        helper.onEachTick(() -> {
            Colony watched = colony.get();
            if (watched == null) {
                return;
            }
            ServerLevel level = helper.getLevel();
            long now = level.getGameTime();
            for (AbstractAntEntity insect : TestIsolation.insectsOf(level, watched, AbstractAntEntity.class)) {
                AntNavigator legs = insect.getNavigator();
                if (legs.isDone() || level.canSeeSky(insect.blockPosition())) {
                    continue;
                }
                long still = tracker.observe(insect.getUUID(), insect.position(), now);
                if (still > STUCK_TICKS) {
                    helper.fail(insect.getName().getString() + " " + insect.getId() + " is stuck under the ground at "
                            + helper.relativePos(insect.blockPosition()) + " for " + still + " ticks: step "
                            + legs.getNextWaypoint() + " of its route " + legs.getRoute() + ", " + legs.getReplans() + " replans; "
                            + describeSpot(level, insect, legs));
                }
            }
            tracker.endTick();
        });
        return tracker;
    }

    /** Everything at the spot a stuck insect is in, for the failure (the lesson of docs/BUGS.md: log the spot, the cell, the legs' state). */
    private static String describeSpot(ServerLevel level, AbstractAntEntity insect, AntNavigator legs) {
        StringBuilder out = new StringBuilder();
        out.append("at ").append(insect.position()).append(", on the ground ").append(insect.onGround())
                .append(", bumping sideways ").append(insect.horizontalCollision).append(", up/down ").append(insect.verticalCollision)
                .append(", step height ").append(insect.maxUpStep()).append(", hold ").append(legs.getHold())
                .append(", in a channel ").append(legs.isInChannel()).append(", climbing ").append(legs.isClimbing());
        net.minecraft.core.BlockPos cell = insect.blockPosition();
        out.append("; its cell ").append(blockAt(level, cell)).append(", under ").append(blockAt(level, cell.below()))
                .append(", over ").append(blockAt(level, cell.above()));
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            out.append(", ").append(side).append(' ').append(blockAt(level, cell.relative(side)));
        }
        java.util.List<net.minecraft.core.BlockPos> route = legs.getRoute();
        if (route != null && legs.getNextWaypoint() < route.size()) {
            net.minecraft.core.BlockPos next = route.get(legs.getNextWaypoint());
            out.append("; the next step's cell ").append(next).append(' ').append(blockAt(level, next))
                    .append(", under it ").append(blockAt(level, next.below())).append(", over it ").append(blockAt(level, next.above()));
        }
        return out.toString();
    }

    private static String blockAt(ServerLevel level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, pos);
        return state + (shape.isEmpty() ? " (open)" : " (top " + shape.max(net.minecraft.core.Direction.Axis.Y) + ")");
    }
}
