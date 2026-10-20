package com.antcolonies.entity.goal;

import java.util.EnumSet;
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

    private final AbstractAntEntity ant;
    /**
     * The walk to the egg and on to its spot; an egg it found no way to (by its id) is left alone for a while, and after
     * a few nursery spots with no way to them the ant puts the egg down.
     */
    private final Errand<UUID> errand;
    @Nullable
    private Colony colony;
    /** The egg on its way to be picked up; null once the ant carries it. */
    @Nullable
    private AntEggEntity egg;
    /** The nursery spot where the ant puts the egg down. */
    @Nullable
    private BlockPos spot;
    private int ticks;
    private boolean done;

    public CarryEggGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
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
        egg = colony.brood().findLooseEgg(level, ant.blockPosition(), errand.leftAlone());
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
        done = false;
        errand.begin();
        if (egg == null) {
            walkToSpot(); // (to an egg: it sets off on its first tick, this same tick)
        }
    }

    @Override
    public void stop() {
        errand.stop();
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
        if (errand.within(egg, REACH)) {
            colony.brood().releaseEgg(egg);
            egg.discard();
            egg = null;
            ant.setCarryingEgg(true);
            level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 1.6F);
            spot = colony.brood().pickEggSpot(level, ant.getRandom());
            walkToSpot(); // (no room in the nursery after all: stop() puts the egg down)
        } else if (!errand.goTo(egg.getUUID(), egg.blockPosition(), 1.0)) {
            done = true; // no way to get there: let another ant have a go
        }
    }

    /** Second leg: to a free spot in the nursery, where the ant puts the egg down. */
    private void bringEgg(ServerLevel level) {
        if (errand.within(spot, REACH)) {
            if (colony.brood().placeEgg(level, spot)) {
                ant.setCarryingEgg(false);
                done = true;
            } else {
                spot = colony.brood().pickEggSpot(level, ant.getRandom()); // another egg lies there now
                walkToSpot();
            }
        } else if (!errand.isWalking()) {
            walkToSpot();
        }
    }

    /** Sets off to the spot. If there is no way there, tries other spots, and gives up after a few (or with none left). */
    private void walkToSpot() {
        spot = errand.setOffTo(spot, () -> colony.brood().pickEggSpot(ant.level(), ant.getRandom()), 1.0);
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
