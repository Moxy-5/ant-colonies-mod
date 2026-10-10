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
 * An ant inside a room the colony has given up (it ran into water or lava) hurries out through the door, before
 * the door is shut for good. Every ant does this, the queen too.
 */
public class EvacuateGoal extends Goal {
    private static final double SPEED = 1.8;

    private final AbstractAntEntity ant;

    public EvacuateGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || (ant.tickCount + ant.getId()) % 4 != 0 || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        Colony colony = ColonyManager.get(level).getColony(ant.getColonyId());
        @Nullable BlockPos exit = colony == null ? null : colony.findEvacuation(level, ant.blockPosition());
        return exit != null && ant.getNavigator().moveTo(exit, SPEED);
    }

    @Override
    public boolean canContinueToUse() {
        return !ant.getNavigator().isDone();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
    }
}
