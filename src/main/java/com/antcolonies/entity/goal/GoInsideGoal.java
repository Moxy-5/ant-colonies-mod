package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * An insect whose colony keeps a schedule (termites: out only at night and in the rain) and that is still outside when
 * it is time to stay in goes back inside: to the nearest spot inside the nest or the mound past the doorway (through a
 * foraging hole while one is open, otherwise any way in there is, such as a chimney), never stopping in the doorway itself.
 */
public class GoInsideGoal extends Goal {
    /** If it is not inside after this long (ticks) it stops trying for now (it tries again a little later). */
    private static final int GIVE_UP_TICKS = 1200;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    private int ticks;

    public GoInsideGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (ant.getRandom().nextInt(10) != 0 || ant.mayGoOut()) {
            return false;
        }
        colony = ant.getColony();
        if (colony == null) {
            return false;
        }
        Level level = ant.level();
        if (colony.nest().isInside(level, ant.blockPosition())) {
            return false;
        }
        // Not to a doorway (the entrance or a foraging hole): one that stops there blocks it from being plugged.
        Colony home = colony;
        boolean going = ant.getNavigator().moveTo(at -> home.nest().isInside(level, at.cell()) && !home.foragingHoles().isDoorway(level, at.cell()),
                colony.getCenter().below(), 1.0);
        if (!going) {
            com.antcolonies.AntColoniesMod.LOGGER.debug("[go-inside] {} at {} ({}) found no way in", ant.getId(), ant.blockPosition().toShortString(),
                    ant.position());
        }
        return going;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && !ant.getNavigator().isDone() && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        colony = null;
    }
}
