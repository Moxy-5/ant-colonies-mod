package com.antcolonies.savanna.colony;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The raid code review, finding 2 (and BUGS 88): one "go home along the trail" errand for every insect a group job lets
 * go out in the field: a raider crippled or clamped (step 3c's limpers), one stuck on the march, the column's leftovers
 * when its time at home ran out, a raid lost from sight, a scout that gave up on its way home, a carrier with no way to
 * the prey pile. Real Megaponera that fall behind make their own way home on the scent trail. Each one walks a little
 * ahead of itself along a known way home at a time ({@link #STEP}): the raid's own route back if it had one, else the
 * nearest scent trail of its colony still smelt that passes near it, else straight for the entrance; the last stretch,
 * within {@link #HOME} of the entrance, is the insect's own going in (a whole route search, {@code GoInsideGoal}).
 *
 * <p>Species-neutral in shape: it knows only insects' ids, routes and its colony's entrance (it lives with the raids for
 * now; it moves to the framework when a second species needs it). Not saved: after a reload the insects outside are
 * found by the raids' sweep and sent home again.
 */
public final class WayHome {
    /** How far ahead of itself along the way home (blocks) it walks to at a time (finding 17: along the trail, not straight). */
    static final double STEP = 6.0;
    /** It is home this near the entrance (blocks, flat and up or down; the column's own home line). */
    static final double HOME = 8.0;
    /** A scent trail passing this near it (blocks, flat) is a way home it takes (my pick). */
    private static final double TRAIL_NEAR = 12.0;

    /** One insect on its way home: the way it follows (null: none yet), since when, and why it was sent. */
    private static final class Going {
        @Nullable
        RaidColumn.Route route;
        final long since;
        final String why;
        /** Whether a trail near it was looked for already (once: a straight walk after that). */
        boolean looked;

        Going(@Nullable RaidColumn.Route route, long since, String why) {
            this.route = route;
            this.since = since;
            this.why = why;
        }
    }

    private final Map<UUID, Going> going = new HashMap<>();

    /** It goes home: along this route if given (one it walked out on, nest end last), else the nearest way home. */
    public void send(UUID ant, @Nullable RaidColumn.Route route, long now, String why) {
        Going before = going.get(ant);
        going.put(ant, new Going(route != null ? route : before == null ? null : before.route, before == null ? now : before.since,
                before == null ? why : before.why));
    }

    public boolean isGoingHome(UUID ant) {
        return going.containsKey(ant);
    }

    /** Why it was sent home (the diary and the tests), or null. */
    @Nullable
    public String why(UUID ant) {
        Going one = going.get(ant);
        return one == null ? null : one.why;
    }

    public Set<UUID> ids() {
        return Set.copyOf(going.keySet());
    }

    public int size() {
        return going.size();
    }

    public void forget(UUID ant) {
        going.remove(ant);
    }

    /** Drops every insect this says to (gone, given up, ...). */
    void forgetIf(Predicate<UUID> gone) {
        going.keySet().removeIf(gone);
    }

    /** Since when it is on its way home (game time), or -1. */
    long since(UUID ant) {
        Going one = going.get(ant);
        return one == null ? -1 : one.since;
    }

    /**
     * Where it should walk now: {@link #STEP} along its way home past the nearest place on it to the insect (the entrance
     * once that runs past the way's end, or with no way known); null once it is home (near the entrance or in the nest:
     * it is let go and goes in by itself) or not on its way home.
     */
    @Nullable
    public BlockPos orderFor(ServerLevel level, AbstractAntEntity ant, Colony colony, ScentTrails trails, SavannaNumbers.TrailNumbers numbers) {
        Going one = going.get(ant.getUUID());
        if (one == null) {
            return null;
        }
        BlockPos entrance = colony.getCenter();
        if (isHome(level, ant, colony)) {
            going.remove(ant.getUUID());
            return null;
        }
        if (one.route == null && !one.looked) {
            one.looked = true;
            ScentTrails.Trail trail = trails.nearestLive(ant.blockPosition(), TRAIL_NEAR, numbers, level.getGameTime());
            if (trail != null && trail.points().size() >= 2) {
                java.util.List<BlockPos> home = new java.util.ArrayList<>(trail.points());
                java.util.Collections.reverse(home); // (the trail runs from the nest out: home is its start)
                one.route = new RaidColumn.Route(home);
            }
        }
        if (one.route == null) {
            return entrance; // (no way known: straight for the entrance, hop by hop)
        }
        double along = one.route.along(ant.getX(), ant.getZ());
        return along + STEP >= one.route.length() ? entrance : RaidColumn.place(level, one.route, along + STEP, 0.0);
    }

    /** Whether this insect is home: within {@link #HOME} of the entrance (flat and up or down), or in the nest. */
    static boolean isHome(ServerLevel level, AbstractAntEntity ant, Colony colony) {
        BlockPos entrance = colony.getCenter();
        return RaidGround.flat(ant.blockPosition(), entrance) <= HOME && Math.abs(ant.getY() - entrance.getY()) <= HOME
                || colony.nest().isInside(level, ant.blockPosition());
    }
}
