package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Walks to a seed lying on the ground near the nest (any item of the species profile's food {@code items}: seeds for
 * the harvester ant, sticks, saplings and wheat for termites) and takes one in its mandibles. {@link StoreSeedGoal}
 * then carries it down to the food store. Only looks for seeds when there is somewhere for them to go: part of the food store is dug out, or the queen wants them.
 * Only at foraging time (see {@code Colony.isForagingTime}); a forager on its way out when it ends turns back.
 */
public class GatherSeedGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to take the seed. */
    private static final double REACH = 0.9;
    /** If the ant can't get there in 30 seconds it leaves the seed to the others. */
    private static final int GIVE_UP_TICKS = 600;
    /** A seed this ant found no way to is left alone for this long. */
    private static final int UNREACHABLE_TICKS = 400;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private ItemEntity seed;
    private int ticks;
    /** Seeds this ant failed to reach: item entity id -> game time until which it leaves them alone. */
    private final Map<Integer, Long> unreachable = new HashMap<>();

    public GatherSeedGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick.
        if (ant.getColonyId() == null || ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0
                || !ant.mayDo(ColonyRole.FORAGER) || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.isForagingTime(level)) {
            return false; // foragers only go out at foraging time (termites: at night and in the rain)
        }
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until <= now);
        seed = colony.food().findSeed(level, ant.blockPosition(), unreachable.keySet());
        if (seed == null) {
            return false;
        }
        colony.food().claimSeed(level, seed);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && seed != null && seed.isAlive() && !seed.getItem().isEmpty()
                && !ant.hasFullMandibles() && ticks < GIVE_UP_TICKS && colony.isForagingTime(ant.level());
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null && seed != null) {
            colony.food().releaseSeed(seed);
        }
        seed = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (seed == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        ant.getLookControl().setLookAt(seed);
        double dx = ant.getX() - seed.getX();
        double dz = ant.getZ() - seed.getZ();
        double dy = ant.getY() - seed.getY();
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(dy) < 1.5) {
            take(level);
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(seed.blockPosition(), 1.0)) {
            unreachable.put(seed.getId(), level.getGameTime() + UNREACHABLE_TICKS);
            ticks = GIVE_UP_TICKS; // no way to get there: let another ant have a go
        }
    }

    /** Takes one seed out of the pile on the ground. */
    private void take(ServerLevel level) {
        ItemStack stack = seed.getItem();
        ItemStack one = stack.split(1);
        if (stack.isEmpty()) {
            seed.discard();
        } else {
            seed.setItem(stack);
        }
        ant.setCarriedSeed(one);
        ant.setSeedForBread(colony.bakery().claimSeedForBread(level, ant.getUUID())); // to the mill if there is no store yet
        level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 1.6F);
    }
}
