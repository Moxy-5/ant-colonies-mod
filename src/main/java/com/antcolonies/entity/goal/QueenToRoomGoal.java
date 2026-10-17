package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/** Once the workers have finished the room, the queen walks down into it and settles in the middle. */
public class QueenToRoomGoal extends Goal {
    private final AbstractAntEntity queen;

    public QueenToRoomGoal(AbstractAntEntity queen) {
        this.queen = queen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * Whether a queen (or a termite king) digs now: only while she founds her colony and her room is not finished yet.
     * A real Pogonomyrmex queen, and a real Macrotermes royal pair, dig only their founding chamber; once the first
     * workers are out they never dig again (the full run of 2026-10-16: a king in the user's mound, with workers round
     * him and no finished room, tried to dig the shaft for ever and never got there, 29 ms a tick of route searches).
     */
    public static boolean mayDig(AbstractAntEntity royal) {
        Colony colony = royal.getColony();
        return colony != null && colony.isFounding() && findFinishedRoom(royal) == null;
    }

    /** The middle of her colony's finished room, or null if there is none yet. */
    @Nullable
    public static BlockPos findFinishedRoom(AbstractAntEntity queen) {
        if (queen.getColonyId() == null || !(queen.level() instanceof ServerLevel level)) {
            return null;
        }
        Colony colony = ColonyManager.get(level).getColony(queen.getColonyId());
        return colony == null ? null : colony.nest().getFinishedRoomCenter(level);
    }

    @Override
    public boolean canUse() {
        if (queen.getRandom().nextInt(20) != 0) {
            return false;
        }
        BlockPos room = findFinishedRoom(queen);
        if (room == null) {
            return false;
        }
        // Already sitting in the middle of the room: nothing to do.
        double dx = queen.getX() - (room.getX() + 0.5);
        double dz = queen.getZ() - (room.getZ() + 0.5);
        if (queen.blockPosition().equals(room) && dx * dx + dz * dz < 0.25) {
            return false;
        }
        return queen.getNavigator().moveTo(room, 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        return !queen.getNavigator().isDone();
    }

    @Override
    public void stop() {
        queen.getNavigator().stop();
    }
}
