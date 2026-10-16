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
import com.antcolonies.entity.AntEggEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Picks up an egg that is not in the nursery (the queen's egg comes out behind her), carries it in its mandibles and
 * puts it down on a free spot in the nursery. Only once the nursery is dug out. An ant that cannot finish the job
 * puts the egg down where it stands, and another ant takes over.
 */
public class CarryEggGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to an egg or to its spot. */
    private static final double REACH = 0.7;
    /** After a minute the ant gives up. */
    private static final int GIVE_UP_TICKS = 1200;
    /** An egg this ant found no way to is left alone for this long. */
    private static final int UNREACHABLE_TICKS = 400;
    /** After this many nursery spots with no route to them, the ant puts the egg down. */
    private static final int MAX_FAILED_ROUTES = 4;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    /** The egg on its way to be picked up; null once the ant carries it. */
    @Nullable
    private AntEggEntity egg;
    /** The nursery spot where the ant puts the egg down. */
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int failedRoutes;
    private boolean done;
    /** Eggs this ant failed to reach: egg id -> game time until which it leaves them alone. */
    private final Map<UUID, Long> unreachable = new HashMap<>();

    public CarryEggGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        if (ant.isCarryingEgg()) {
            // Carrying an egg (something else interrupted the walk): carry on to the nursery.
            colony = ColonyManager.get(level).getColony(ant.getColonyId());
            spot = colony == null ? null : colony.brood().pickEggSpot(level, ant.getRandom());
            egg = null;
            if (spot == null) {
                putDown(level);
                return false;
            }
            return true;
        }
        // Only look for work now and then, so ants don't all think on the same tick (every time just before it would count as idle).
        if (ant.hasFullMandibles() || !ant.looksForWork(20) || !ant.mayDo(ColonyRole.NURSE)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null) {
            return false;
        }
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until <= now);
        egg = colony.brood().findLooseEgg(level, ant.blockPosition(), unreachable.keySet());
        if (egg == null) {
            return false;
        }
        colony.brood().claimEgg(level, egg);
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
        if (egg != null) {
            ant.getNavigator().moveTo(egg.blockPosition(), 1.0);
        } else {
            walkToSpot();
        }
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null && egg != null) {
            colony.brood().releaseEgg(egg);
        }
        if (ant.isCarryingEgg() && (done || ticks >= GIVE_UP_TICKS) && ant.level() instanceof ServerLevel level) {
            putDown(level); // could not finish the job
        }
        egg = null;
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (egg != null) {
            fetchEgg(level);
        } else if (spot != null) {
            bringEgg(level);
        }
    }

    /** First leg: to the egg, which the ant takes in its mandibles. */
    private void fetchEgg(ServerLevel level) {
        if (!egg.isAlive()) {
            egg = null;
            done = true;
            return;
        }
        ant.getLookControl().setLookAt(egg);
        double dx = ant.getX() - egg.getX();
        double dz = ant.getZ() - egg.getZ();
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - egg.getY()) < 1.5) {
            colony.brood().releaseEgg(egg);
            egg.discard();
            egg = null;
            ant.setCarryingEgg(true);
            level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 1.6F);
            spot = colony.brood().pickEggSpot(level, ant.getRandom());
            if (spot == null) {
                done = true; // no room in the nursery after all: stop() puts the egg down
            } else {
                walkToSpot();
            }
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(egg.blockPosition(), 1.0)) {
            unreachable.put(egg.getUUID(), level.getGameTime() + UNREACHABLE_TICKS);
            done = true; // no way to get there: let another ant have a go
        }
    }

    /** Second leg: to a free spot in the nursery, where the ant puts the egg down. */
    private void bringEgg(ServerLevel level) {
        double dx = ant.getX() - (spot.getX() + 0.5);
        double dz = ant.getZ() - (spot.getZ() + 0.5);
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - spot.getY()) < 1.5) {
            if (colony.brood().placeEgg(level, spot)) {
                ant.setCarryingEgg(false);
                done = true;
            } else {
                spot = colony.brood().pickEggSpot(level, ant.getRandom()); // another egg lies there now
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
            spot = colony.brood().pickEggSpot(ant.level(), ant.getRandom());
        }
        if (spot == null) {
            done = true;
        }
    }

    /** The ant puts the egg down where it stands, so another ant can take it from there. */
    private void putDown(ServerLevel level) {
        ant.setCarryingEgg(false);
        if (colony != null) {
            colony.brood().spawnEgg(level, ant.getX(), ant.getY(), ant.getZ());
        }
    }
}
