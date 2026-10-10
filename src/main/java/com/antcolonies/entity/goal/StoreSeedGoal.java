package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/** An ant holding a seed carries it to the food store and leaves it on the floor there. */
public class StoreSeedGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get before it puts the seed down. */
    private static final double REACH = 0.9;
    /** If the ant can't get there in a minute it drops the seed where it stands. */
    private static final int GIVE_UP_TICKS = 1200;
    /** After this many spots with no route to them, the ant drops the seed. */
    private static final int MAX_FAILED_ROUTES = 4;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int failedRoutes;

    public StoreSeedGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ant.isCarryingSeed() || ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        spot = colony == null ? null : colony.pickFoodSpot(level, ant.getRandom());
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
        failedRoutes = 0;
        walkToSpot();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        spot = null;
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
        double dx = ant.getX() - (spot.getX() + 0.5);
        double dz = ant.getZ() - (spot.getZ() + 0.5);
        double dy = ant.getY() - spot.getY();
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(dy) < 1.5) {
            colony.depositSeed(level, spot, ant.getCarriedSeed());
            ant.setCarriedSeed(net.minecraft.world.item.ItemStack.EMPTY);
            ColonyManager.get(level).markChanged();
        } else if (ant.getNavigator().isDone()) {
            walkToSpot();
        }
    }

    /** Plans the walk to the spot in the food store. If there is no route, tries other spots, and drops the seed after a few failures. */
    private void walkToSpot() {
        while (spot != null && !ant.getNavigator().moveTo(spot, 1.0)) {
            if (++failedRoutes >= MAX_FAILED_ROUTES) {
                dropSeed();
                return;
            }
            spot = colony.pickFoodSpot(ant.level(), ant.getRandom());
        }
        if (spot == null) {
            dropSeed();
        }
    }

    private void dropSeed() {
        if (ant.isCarryingSeed()) {
            ant.spawnAtLocation(ant.getCarriedSeed());
            ant.setCarriedSeed(net.minecraft.world.item.ItemStack.EMPTY);
        }
    }
}
