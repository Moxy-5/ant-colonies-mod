package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.WorkerFood;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.WorkerHunger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * A hungry worker (it has not eaten for a meal interval) walks to its meal and eats it on the spot, which takes about
 * a second and a half. What and where comes from its colony's diet ({@link WorkerFood}): a harvester ant takes a raw
 * seed off a heap in the food store; a young termite worker a fungus nodule off the comb, an older one a bite of old
 * comb (and either eats the other kind when its own is not there). If there is nothing to eat the worker stays hungry
 * and tries again a little later. Species-neutral.
 */
public class EatGoal extends Goal {
    /** How close (sideways, in blocks) the worker must get to the food. */
    private static final double REACH = 0.9;
    /** After a minute on the way the worker gives up for now. */
    private static final int GIVE_UP_TICKS = 1200;
    /** Time (in ticks) before a hungry worker looks for food again after finding none, or no way to it. */
    private static final int RETRY_TICKS = 200;
    /** How long (in ticks) eating one meal takes. */
    private static final int EAT_TICKS = 30;

    private final AbstractAntEntity ant;
    /** The walk to the meal. */
    private final Errand<BlockPos> errand;
    private final WorkerHunger hunger;
    @Nullable
    private Colony colony;
    @Nullable
    private WorkerFood.Meal meal;
    private int ticks;
    private int eatenFor;
    private boolean eating;
    private boolean done;
    private long nextTry;

    public EatGoal(AbstractAntEntity ant, WorkerHunger hunger) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        this.hunger = hunger;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level) || !hunger.isHungry() || ant.hasFullMandibles()
                || level.getGameTime() < nextTry || ant.getRandom().nextInt(10) != 0) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        meal = colony == null ? null : colony.workerFood().findMeal(level, ant, ant.getRandom());
        if (meal == null || !errand.setOff(meal.spot(), 1.0)) {
            nextTry = level.getGameTime() + RETRY_TICKS; // nothing to eat, or no way to it
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
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        if (eating) {
            finishMeal(); // interrupted with the food in its mandibles: it counts as eaten
        }
        meal = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || meal == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos spot = meal.spot();
        if (eating) {
            ant.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            if (eatenFor % 10 == 0 && !ant.getCarriedSeed().isEmpty()) {
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
        if (errand.within(spot, REACH)) {
            ItemStack food = colony.workerFood().eatAt(level, ant, meal);
            if (!food.isEmpty()) {
                ant.setCarriedSeed(food);
                errand.stop();
                eating = true;
                eatenFor = 0;
            } else {
                meal = colony.workerFood().findMeal(level, ant, ant.getRandom()); // somebody else took the last of it there
                if (meal == null || !errand.setOff(meal.spot(), 1.0)) {
                    done = true;
                }
            }
        } else if (!errand.walkOn(spot, 1.0)) {
            done = true;
        }
    }

    /** The meal is eaten: the worker is full for another meal interval. */
    private void finishMeal() {
        ant.setCarriedSeed(ItemStack.EMPTY);
        hunger.eat();
        eating = false;
    }
}
