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
 * The queen walks from her room to a free spot in the nursery and lays an egg there, at the price of a few seeds from
 * the food store. Only when her room is finished, the nursery is dug out, the colony has the seeds and the nursery
 * is not full of eggs. Afterwards {@link QueenToRoomGoal} takes her back to her room.
 */
public class LayEggGoal extends Goal {
    /** How close (sideways, in blocks) the queen must get before she lays. */
    private static final double REACH = 0.7;
    /** Time (in ticks) between two eggs: one minute. */
    private static final int LAY_INTERVAL = 1200;
    /** Time (in ticks) before she tries again after something went wrong (no seeds, no way to the nursery). */
    private static final int RETRY_TICKS = 400;
    private static final int GIVE_UP_TICKS = 1200;

    private final AbstractAntEntity queen;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos spot;
    private int ticks;
    /** Game time before which she lays no egg. Not saved. */
    private long nextEgg;

    public LayEggGoal(AbstractAntEntity queen) {
        this.queen = queen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (queen.getColonyId() == null || queen.getRandom().nextInt(40) != 0 || !(queen.level() instanceof ServerLevel level)
                || level.getGameTime() < nextEgg) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(queen.getColonyId());
        if (colony == null || colony.getFinishedRoomCenter(level) == null) {
            return false;
        }
        spot = colony.pickEggSpot(level, queen.getRandom());
        if (spot == null) {
            return false;
        }
        if (!queen.getNavigator().moveTo(spot, 0.8)) {
            nextEgg = level.getGameTime() + RETRY_TICKS; // no way to the nursery just now
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && spot != null && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        queen.getNavigator().stop();
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (spot == null || colony == null || !(queen.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        double dx = queen.getX() - (spot.getX() + 0.5);
        double dz = queen.getZ() - (spot.getZ() + 0.5);
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(queen.getY() - spot.getY()) < 1.5) {
            if (colony.layEgg(level, spot)) {
                nextEgg = level.getGameTime() + LAY_INTERVAL;
                ColonyManager.get(level).markChanged();
            } else {
                nextEgg = level.getGameTime() + RETRY_TICKS; // the seeds were used up on the way
            }
            ticks = GIVE_UP_TICKS;
        } else if (queen.getNavigator().isDone() && !queen.getNavigator().moveTo(spot, 0.8)) {
            nextEgg = level.getGameTime() + RETRY_TICKS;
            ticks = GIVE_UP_TICKS;
        }
    }
}
