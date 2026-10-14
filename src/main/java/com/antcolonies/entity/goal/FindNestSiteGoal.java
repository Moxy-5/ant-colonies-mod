package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.function.Supplier;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A founding queen landed on uneven ground walks to the level spot she has picked (see {@code NestSite}) and founds her
 * nest there. If she cannot get there, or the walk takes too long, she founds where she is.
 */
public class FindNestSiteGoal extends Goal {
    /** About a minute: a little walk, not a long search. */
    private static final int GIVE_UP_TICKS = 1200;

    private final AbstractAntEntity queen;
    /** The spot she walks to, or null when she is not looking for one. */
    private final Supplier<BlockPos> site;
    /** Founds her colony where she stands. */
    private final Runnable found;
    private int ticks;

    public FindNestSiteGoal(AbstractAntEntity queen, Supplier<BlockPos> site, Runnable found) {
        this.queen = queen;
        this.site = site;
        this.found = found;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return site.get() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return site.get() != null;
    }

    @Override
    public void start() {
        ticks = 0;
        if (!queen.getNavigator().moveTo(site.get(), 1.0)) {
            found.run(); // no way there
        }
    }

    @Override
    public void tick() {
        BlockPos target = site.get();
        if (target == null) {
            return;
        }
        if (++ticks > GIVE_UP_TICKS) {
            found.run();
        } else if (queen.getNavigator().isDone()) {
            double dx = queen.getX() - (target.getX() + 0.5);
            double dz = queen.getZ() - (target.getZ() + 0.5);
            if (dx * dx + dz * dz <= 1.0 || !queen.getNavigator().moveTo(target, 1.0)) {
                found.run();
            }
        }
    }

    @Override
    public void stop() {
        queen.getNavigator().stop();
    }
}
