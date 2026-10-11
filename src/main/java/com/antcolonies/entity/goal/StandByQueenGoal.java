package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.QueenAntEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The nurse waits by the queen: walks to her room and stays near her. It keeps going while the queen sits in her room
 * (so the nurse neither wanders off nor takes other work), and the jobs that matter to her (carrying an egg, bringing the
 * queen a meal) come first because they have higher priority.
 */
public class StandByQueenGoal extends Goal {
    /** The nurse tries to stay within this many blocks (sideways) of the queen. */
    private static final double NEAR = 1.6;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    private int repath;

    public StandByQueenGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return ant.getRandom().nextInt(10) == 0 && ant.getColonyId() != null && !ant.hasFullMandibles() && nurseWithHomeQueen();
    }

    @Override
    public boolean canContinueToUse() {
        return !ant.hasFullMandibles() && nurseWithHomeQueen();
    }

    private boolean nurseWithHomeQueen() {
        if (!(ant.level() instanceof ServerLevel level) || ant.getColonyId() == null || !ant.hasRole(ColonyRole.NURSE)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        return colony != null && colony.isQueenHome(level);
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        QueenAntEntity queen = colony.getQueen(level);
        if (queen == null) {
            return;
        }
        ant.getLookControl().setLookAt(queen);
        double dx = ant.getX() - queen.getX();
        double dz = ant.getZ() - queen.getZ();
        boolean near = dx * dx + dz * dz <= NEAR * NEAR && Math.abs(ant.getY() - queen.getY()) < 1.5;
        if (near) {
            ant.getNavigator().stop();
        } else if (ant.getNavigator().isDone() && --repath <= 0) {
            ant.getNavigator().moveTo(queen.blockPosition(), 1.0);
            repath = 40; // not every tick if there is no way
        }
    }
}
