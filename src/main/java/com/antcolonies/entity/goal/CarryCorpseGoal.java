package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntCorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Clears away the dead: picks up the body of a dead nestmate in its mandibles, carries it down to the refuse chamber and
 * sets it down on a free spot there. Only once the refuse chamber is dug out. An ant that cannot finish the job puts the
 * body down where it stands, and another ant takes over. The job of the builders (and of any ant with nothing to do).
 */
public class CarryCorpseGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to a body or to its spot. */
    private static final double REACH = 0.7;
    /** The way to the bottom of the nest is long: after two minutes the ant gives up. */
    private static final int GIVE_UP_TICKS = 2400;
    /** A body this ant found no way to is left alone for this long. */
    private static final int UNREACHABLE_TICKS = 400;
    /** After this many chamber spots with no route to them, the ant puts the body down. */
    private static final int MAX_FAILED_ROUTES = 4;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    /** The body on its way to be picked up; null once the ant carries it. */
    @Nullable
    private AntCorpseEntity corpse;
    /** The refuse chamber spot where the ant puts the body down. */
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int failedRoutes;
    private boolean done;
    /** Bodies this ant failed to reach: corpse id -> game time until which it leaves them alone. */
    private final Map<UUID, Long> unreachable = new HashMap<>();

    public CarryCorpseGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        if (ant.isCarryingCorpse()) {
            // Carrying a body (something else interrupted the walk): carry on to the refuse chamber.
            colony = ColonyManager.get(level).getColony(ant.getColonyId());
            spot = colony == null ? null : colony.refuse().pickCorpseSpot(level, ant.getRandom());
            corpse = null;
            if (spot == null) {
                putDown(level);
                return false;
            }
            return true;
        }
        // Only look for work now and then, so ants don't all think on the same tick.
        if (ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0 || !ant.mayDo(ColonyRole.BUILDER)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null) {
            return false;
        }
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until <= now);
        corpse = colony.refuse().findLooseCorpse(level, ant.blockPosition(), unreachable.keySet());
        if (corpse == null) {
            return false;
        }
        colony.refuse().claimCorpse(level, corpse);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && !done && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        failedRoutes = 0;
        done = false;
        if (corpse != null) {
            ant.getNavigator().moveTo(corpse.blockPosition(), 1.0);
        } else {
            walkToSpot();
        }
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null && corpse != null) {
            colony.refuse().releaseCorpse(corpse);
        }
        if (ant.isCarryingCorpse() && (done || ticks >= GIVE_UP_TICKS) && ant.level() instanceof ServerLevel level) {
            putDown(level); // could not finish the job
        }
        corpse = null;
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (corpse != null) {
            fetchCorpse(level);
        } else if (spot != null) {
            bringCorpse(level);
        }
    }

    /** First leg: to the body, which the ant takes in its mandibles. */
    private void fetchCorpse(ServerLevel level) {
        if (!corpse.isAlive()) {
            corpse = null;
            done = true;
            return;
        }
        ant.getLookControl().setLookAt(corpse);
        double dx = ant.getX() - corpse.getX();
        double dz = ant.getZ() - corpse.getZ();
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - corpse.getY()) < 1.5) {
            colony.refuse().releaseCorpse(corpse);
            corpse.discard();
            corpse = null;
            ant.setCarryingCorpse(true);
            level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 0.8F);
            spot = colony.refuse().pickCorpseSpot(level, ant.getRandom());
            if (spot == null) {
                done = true; // no room in the refuse chamber after all: stop() puts the body down
            } else {
                walkToSpot();
            }
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(corpse.blockPosition(), 1.0)) {
            unreachable.put(corpse.getUUID(), level.getGameTime() + UNREACHABLE_TICKS);
            done = true; // no way to get there: let another ant have a go
        }
    }

    /** Second leg: down to a free spot in the refuse chamber, where the ant puts the body down. */
    private void bringCorpse(ServerLevel level) {
        double dx = ant.getX() - (spot.getX() + 0.5);
        double dz = ant.getZ() - (spot.getZ() + 0.5);
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - spot.getY()) < 1.5) {
            if (colony.refuse().placeCorpse(level, spot)) {
                ant.setCarryingCorpse(false);
                done = true;
            } else {
                spot = colony.refuse().pickCorpseSpot(level, ant.getRandom()); // another body lies there now
                if (spot == null) {
                    done = true;
                } else {
                    walkToSpot();
                }
            }
        } else if (ant.getNavigator().isDone()) {
            walkToSpot();
        }
    }

    /** Plans the walk to the spot. If there is no route, tries other spots, and gives up after a few failures. */
    private void walkToSpot() {
        while (spot != null && !ant.getNavigator().moveTo(spot, 1.0)) {
            if (++failedRoutes >= MAX_FAILED_ROUTES) {
                done = true;
                return;
            }
            spot = colony.refuse().pickCorpseSpot(ant.level(), ant.getRandom());
        }
        if (spot == null) {
            done = true;
        }
    }

    /** The ant puts the body down where it stands, so another ant can take it from there. */
    private void putDown(ServerLevel level) {
        ant.setCarryingCorpse(false);
        if (colony != null) {
            colony.refuse().spawnCorpse(level, ant.getX(), ant.getY(), ant.getZ());
        }
    }
}
