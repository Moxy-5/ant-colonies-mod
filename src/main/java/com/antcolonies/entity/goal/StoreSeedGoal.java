package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * An ant holding a seed (or any piece of its species' food, such as a termite's forage or a stick it picked up) carries
 * it to the food store and adds it to a heap on the floor there (a seed pile, a forage pile: the species profile's pile).
 */
public class StoreSeedGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get before it puts the seed down. */
    private static final double REACH = 0.9;
    /** If the ant can't get there in a minute it drops the seed where it stands. */
    private static final int GIVE_UP_TICKS = 1200;

    private final AbstractAntEntity ant;
    /** The walk home to the store (after a few spots with no way to them, the ant drops the seed). */
    private final Errand<BlockPos> errand;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos spot;
    private int ticks;
    /**
     * Where the forager joins the trunk trail it came out by, on its way home (harvester ants; see
     * {@code TrunkTrails}); null once it is on it, or with no trail that way.
     */
    @Nullable
    private BlockPos via;

    public StoreSeedGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // A seed on its way to the mill is not stored (unless the trip is called off: then MillBreadGoal lets go of it).
        if (!ant.isCarryingSeed() || ant.isSeedForBread() || ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        spot = colony == null ? null : colony.food().pickFoodSpot(level, ant.getRandom());
        if (spot == null) {
            // Colony gone, or the food store is not usable (any more): nowhere to put it.
            dropSeed();
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && spot != null && ant.isCarryingSeed();
    }

    @Override
    public void start() {
        ticks = 0;
        errand.begin();
        via = ant.level() instanceof ServerLevel level && colony != null ? colony.trunkTrails().wayHome(level, ant.blockPosition()) : null;
        if (!errand.setOff(via, 1.0)) {
            via = null;
            walkToSpot();
        }
    }

    @Override
    public void stop() {
        errand.stop();
        spot = null;
        via = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (spot == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        if (++ticks >= GIVE_UP_TICKS) {
            dropSeed();
            return;
        }
        if (via != null) {
            // Back to the trunk trail first, then home along it.
            BlockPos at = ant.blockPosition();
            if (Math.abs(at.getX() - via.getX()) <= 1 && Math.abs(at.getZ() - via.getZ()) <= 1 && Math.abs(at.getY() - via.getY()) <= 2) {
                via = null;
                walkToSpot();
            } else if (!errand.walkOn(via, 1.0)) {
                via = null;
                walkToSpot();
            }
            return;
        }
        if (errand.within(spot, REACH)) {
            if (colony.food().depositSeed(level, spot, ant.getCarriedSeed().is(com.antcolonies.registry.ModTags.RICH_SEEDS))) {
                ant.setCarriedSeed(net.minecraft.world.item.ItemStack.EMPTY);
                colony.food().releaseStorePlace(ant.getUUID());
                ColonyManager.get(level).markChanged();
            } else {
                errand.notThere(spot); // that heap is full
                spot = colony.food().pickFoodSpot(level, ant.getRandom());
                walkToSpot();
            }
        } else if (!errand.isWalking()) {
            walkToSpot();
        }
    }

    /**
     * Sets off to the spot in the food store; with no way there, to the nearest other spot there is a way to. Drops the
     * seed with none left. (The spot itself is picked at random round the store, not the nearest: seed heaps grow to a
     * whole block, and heaps filling from the door inward would stand in the way.)
     */
    private void walkToSpot() {
        spot = errand.setOffToNearest(spot, () -> colony.food().foodSpots(ant.level(), ant.getRandom()), 1.0);
        if (spot == null) {
            dropSeed();
        }
    }

    private void dropSeed() {
        if (ant.isCarryingSeed()) {
            com.antcolonies.AntColoniesMod.LOGGER.info("[seed] ant {} could not take its seed to the food store and dropped it at {} (after {} ticks, {} failed routes)",
                    ant.getId(), ant.blockPosition().toShortString(), ticks, errand.spotsWithNoWay());
            ant.spawnAtLocation(ant.getCarriedSeed());
            ant.setCarriedSeed(net.minecraft.world.item.ItemStack.EMPTY);
            Colony mine = ant.getColonyId() != null && ant.level() instanceof ServerLevel level ? ColonyManager.get(level).getColony(ant.getColonyId()) : null;
            if (mine != null) {
                mine.food().releaseStorePlace(ant.getUUID());
                mine.food().seedDropped();
            }
        }
    }
}
