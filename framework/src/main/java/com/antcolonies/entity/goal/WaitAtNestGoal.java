package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * An insect that should be in its nest now (it may not go out, see {@link AbstractAntEntity#mayGoOut}) but is outside
 * and far from it, with no way in yet (a nest of ready spaces still being joined up, M8 step 1d), walks over and waits
 * round the entrance, a few blocks out: the colony gathers at its new home while the way in is dug. Once there is an
 * inside to go to, {@link GoInsideGoal} takes it in.
 */
public class WaitAtNestGoal extends Goal {
    /** It waits within this many blocks of the entrance (my pick), and sets off when farther than {@link #FAR}. */
    static final int NEAR = 3;
    static final int FAR = 5;
    /** After finding no way there, it waits this long (ticks) before looking again. */
    private static final int RETRY_TICKS = 200;

    private final AbstractAntEntity ant;
    private final Errand<BlockPos> errand;
    private long nextTry;

    public WaitAtNestGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (ant.getRandom().nextInt(10) != 0 || ant.mayGoOut()) {
            return false;
        }
        Colony colony = ant.getColony();
        Level level = ant.level();
        if (colony == null || level.getGameTime() < nextTry || colony.nest().isInside(level, ant.blockPosition())) {
            return false;
        }
        BlockPos entrance = colony.getCenter();
        if (distance(ant.blockPosition(), entrance) <= FAR) {
            return false;
        }
        boolean going = errand.setOff(at -> distance(at.cell(), entrance) <= NEAR && !at.cell().equals(entrance), entrance, 1.0);
        if (!going) {
            nextTry = level.getGameTime() + RETRY_TICKS;
        }
        return going;
    }

    private static int distance(BlockPos a, @Nullable BlockPos b) {
        return b == null ? Integer.MAX_VALUE : Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    @Override
    public boolean canContinueToUse() {
        return errand.isWalking();
    }

    @Override
    public void start() {
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
    }
}
