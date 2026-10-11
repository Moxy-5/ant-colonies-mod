package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.WorkerAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * A hungry worker (it has not eaten for a day) walks to a heap of raw seeds in the food store, takes one seed and eats it
 * on the spot, which takes about a second and a half. If the colony has no seeds the worker stays hungry and tries again
 * a little later.
 */
public class EatSeedGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to the heap. */
    private static final double REACH = 0.9;
    /** After a minute on the way the ant gives up for now. */
    private static final int GIVE_UP_TICKS = 1200;
    /** Time (in ticks) before a hungry ant looks for seeds again after finding none, or no way to them. */
    private static final int RETRY_TICKS = 200;
    /** How long (in ticks) eating one seed takes. */
    private static final int EAT_TICKS = 30;

    private final WorkerAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos pile;
    private int ticks;
    private int eatenFor;
    private boolean eating;
    private boolean done;
    private long nextTry;

    public EatSeedGoal(WorkerAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level) || !ant.isHungry() || ant.hasFullMandibles()
                || level.getGameTime() < nextTry || ant.getRandom().nextInt(10) != 0) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || colony.getFood() <= 0) {
            nextTry = level.getGameTime() + RETRY_TICKS; // nothing to eat yet
            return false;
        }
        pile = colony.findSeedPile(level, ant.getRandom());
        if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
            nextTry = level.getGameTime() + RETRY_TICKS; // no seeds, or no way to them
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && !done && (eating || ticks < GIVE_UP_TICKS);
    }

    @Override
    public void start() {
        ticks = 0;
        eatenFor = 0;
        eating = false;
        done = false;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (eating) {
            finishMeal(); // interrupted with the seed in its mandibles: it counts as eaten
        }
        pile = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || pile == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        if (eating) {
            ant.getLookControl().setLookAt(pile.getX() + 0.5, pile.getY(), pile.getZ() + 0.5);
            if (eatenFor % 10 == 0) {
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ant.getCarriedSeed()), ant.getX(), ant.getY() + 0.1,
                        ant.getZ(), 3, 0.05, 0.03, 0.05, 0.02);
                level.playSound(null, ant.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.15F, 1.8F);
            }
            if (++eatenFor >= EAT_TICKS) {
                finishMeal();
                ColonyManager.get(level).markChanged();
                done = true;
            }
            return;
        }
        ticks++;
        double dx = ant.getX() - (pile.getX() + 0.5);
        double dz = ant.getZ() - (pile.getZ() + 0.5);
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - pile.getY()) < 1.5) {
            ItemStack seed = colony.eatSeedAt(level, pile, ant.getUUID());
            if (!seed.isEmpty()) {
                ant.setCarriedSeed(seed);
                ant.getNavigator().stop();
                eating = true;
                eatenFor = 0;
            } else {
                pile = colony.findSeedPile(level, ant.getRandom()); // somebody else took the last seed there
                if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
                    done = true;
                }
            }
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(pile, 1.0)) {
            done = true;
        }
    }

    /** The seed is eaten: the ant is full for another day. */
    private void finishMeal() {
        ant.setCarriedSeed(ItemStack.EMPTY);
        ant.eat();
        eating = false;
    }
}
