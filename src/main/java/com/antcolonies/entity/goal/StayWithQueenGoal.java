package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The termite king keeps to his queen: before their chamber is dug he walks back to her when he has strayed more than
 * a few blocks, and once it is dug he settles on the floor right beside the middle of the chamber, where she lies.
 */
public class StayWithQueenGoal extends Goal {
    /** Farther than this from the queen (in blocks), the king walks back to her. */
    private static final double STRAY_DISTANCE = 4.0;

    private final AbstractAntEntity king;
    /**
     * The walk to the queen; a place he found no way to is left alone for a while (docs/BUGS.md 42: without it a cut-
     * off insect searched for a route again every time it looked for work).
     */
    private final Errand<BlockPos> errand;

    public StayWithQueenGoal(AbstractAntEntity king) {
        this.king = king;
        this.errand = new Errand<>(king);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * The cell beside the middle of the chamber (to the side, as seen from its tunnel), or null if there is no chamber
     * yet.
     */
    @Nullable
    public static BlockPos spotBesideQueen(ServerLevel level, Colony colony) {
        NestPlan.Chamber home = colony.nest().getHomeChamber(level);
        if (home == null) {
            return null;
        }
        BlockPos center = home.center();
        BlockPos tunnelEnd = home.tunnelEnd();
        int dx = Integer.signum(center.getX() - tunnelEnd.getX());
        int dz = Integer.signum(center.getZ() - tunnelEnd.getZ());
        return center.offset(-dz, 0, dx);
    }

    @Override
    public boolean canUse() {
        if (king.getColonyId() == null || king.getRandom().nextInt(10) != 0 || !(king.level() instanceof ServerLevel level)) {
            return false;
        }
        Colony colony = ColonyManager.get(level).getColony(king.getColonyId());
        if (colony == null) {
            return false;
        }
        BlockPos spot = spotBesideQueen(level, colony);
        if (spot != null) {
            return !errand.isIn(spot) && errand.setOffFor(spot, spot, 1.0);
        }
        AbstractAntEntity queen = colony.getQueen(level);
        return queen != null && king.distanceTo(queen) > STRAY_DISTANCE
                && errand.setOffFor(queen.blockPosition(), queen.blockPosition(), 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        return errand.isWalking();
    }

    @Override
    public void stop() {
        errand.stop();
    }
}
