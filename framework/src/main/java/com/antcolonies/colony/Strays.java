package com.antcolonies.colony;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.watch.Watch;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The raid test bench, B7, "nothing waits for ever" (docs/plans/raid-test-bench.md): the safety net under every way an
 * insect can get stuck that nobody has found yet. A real member out of its nest that keeps trying to walk (a route
 * planned, or its last route search failed) but has not moved {@link #MOVED} blocks in {@link #STRAY_TICKS}, and that no
 * player can see, is folded into the colony's numbers with what it carries: it found its own way home out of sight, as a
 * lost ant does. Every species gets it (shared part); the queen and king never (they stay real, as in unloaded land).
 *
 * <p>The rules (the raid test bench's not-stuck, raider-stuck, home-by-night) break long before this (30 to 60 s), so a
 * soak still reports every stuck insect; this only stops one standing there for hours. One in sight of a player is left:
 * vanishing before their eyes would be worse than standing (and the player then sees the bug). Not saved: after a load
 * the count starts again.
 */
public final class Strays {
    /** Stuck this long (ticks) before it is folded in: 3 minutes (my pick: well past every rule and every wait's own limit). */
    public static final long STRAY_TICKS = 3600;
    /** Moving less than this (blocks) counts as not moving. */
    static final double MOVED = 1.5;

    private record Spot(Vec3 at, long since) {
    }

    private final Colony colony;
    private final Map<UUID, Spot> spots = new HashMap<>();
    private int folded;

    public Strays(Colony colony) {
        this.colony = colony;
    }

    /** How many strays it folded in since the game started (the colony list and the soak). */
    public int folded() {
        return folded;
    }

    /** Once a second while the colony is here. */
    void tick(ServerLevel level) {
        if (!colony.population().isOn(level)) {
            spots.clear();
            return;
        }
        long now = level.getGameTime();
        List<Vec3> eyes = null;
        Set<UUID> seen = new HashSet<>();
        for (AbstractAntEntity insect : colony.population().realMembers(level)) {
            UUID id = insect.getUUID();
            if (!insect.isAlive() || !level.isPositionEntityTicking(insect.blockPosition()) || !trying(level, insect)) {
                continue;
            }
            seen.add(id);
            Spot spot = spots.get(id);
            if (spot == null || spot.at().distanceTo(insect.position()) > MOVED) {
                spots.put(id, new Spot(insect.position(), now));
                continue;
            }
            if (now - spot.since() < STRAY_TICKS) {
                continue;
            }
            if (eyes == null) {
                eyes = colony.population().viewersInSight(level);
            }
            if (!Population.outOfSight(level, insect.getEyePosition(), eyes)) {
                continue; // (a player sees it: it is left, stuck as it is)
            }
            String what = insect.getType().toShortString() + " " + id.toString().substring(0, 8) + " at " + insect.blockPosition().toShortString()
                    + ", stuck " + (now - spot.since()) / 20 + " s (" + insect.runningGoals() + ")";
            if (colony.away().foldInStray(level, insect)) {
                folded++;
                spots.remove(id);
                ColonyFrameworkMod.LOGGER.info("[stray] colony {}: {} found its own way home out of sight (folded into the numbers)",
                        colony.getId().toString().substring(0, 8), what);
                Watch.note(level, colony, "[stray] " + what + ": folded into the numbers out of sight");
            }
        }
        spots.keySet().retainAll(seen);
    }

    /**
     * Whether it is out of its nest and trying to walk: a route planned, or its last search found none. One standing
     * by choice (at a post, waiting its turn, resting) plans no route and is never a stray; neither is one paralysed,
     * downed, carried or riding.
     */
    private boolean trying(ServerLevel level, AbstractAntEntity insect) {
        if (insect.isParalysed() || insect.isDowned() || insect.isPassenger() || insect.isVehicle()
                || insect.getUUID().equals(colony.getQueenId())) {
            return false;
        }
        boolean walking = !insect.getNavigator().isDone() || insect.getNavigator().hasFailed();
        return walking && !colony.nest().isInside(level, insect.blockPosition());
    }
}
