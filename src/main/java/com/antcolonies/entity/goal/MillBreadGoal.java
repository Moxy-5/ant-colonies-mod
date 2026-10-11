package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Makes ant bread: the nurse takes one seed off a heap in the food store, carries it to a spot in the nursery, mills it
 * there for {@link Colony#MILL_TICKS} (3 seconds) and the spot gets one more piece of bread. A worker that already
 * holds a seed meant for the mill (a forager that found one before there was a food store) carries on from the
 * carrying leg, whatever its role. If the trip is called off the seed goes back to the food store ({@link StoreSeedGoal}).
 */
public class MillBreadGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to a heap or to its spot. */
    private static final double REACH = 0.9;
    /** After a minute the ant gives up (not counting the milling itself). */
    private static final int GIVE_UP_TICKS = 1200;
    /** Time (in ticks) before this ant looks for a seed to mill again after finding none, or no way to it. */
    private static final int RETRY_TICKS = 200;

    private enum Leg { TO_PILE, TO_SPOT, MILLING }

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    private Leg leg = Leg.TO_PILE;
    /** The seed heap to take from, or the nursery spot to mill at. */
    @Nullable
    private BlockPos target;
    private int ticks;
    private int milled;
    private boolean done;
    private long nextTry;

    public MillBreadGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        // Holding a seed for the mill (a forager's, or this goal was interrupted): carry on to a spot.
        if (ant.isCarryingSeed() && ant.isSeedForBread()) {
            colony = ColonyManager.get(level).getColony(ant.getColonyId());
            target = colony == null ? null : colony.pickBreadSpot(level, ant.getRandom());
            if (target == null || !ant.getNavigator().moveTo(target, 1.0)) {
                if (colony != null) {
                    colony.releaseMilling(ant.getUUID());
                }
                ant.setSeedForBread(false); // StoreSeedGoal takes it from here
                return false;
            }
            leg = Leg.TO_SPOT;
            return true;
        }
        // Only look for work now and then, so ants don't all think on the same tick.
        if (ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0 || !ant.mayDo(ColonyRole.NURSE)
                || level.getGameTime() < nextTry) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || colony.getFood() <= 0 || !colony.claimMilling(level, ant.getUUID())) {
            return false;
        }
        target = colony.findSeedPile(level, ant.getRandom());
        if (target == null || !ant.getNavigator().moveTo(target, 1.0)) {
            colony.releaseMilling(ant.getUUID());
            nextTry = level.getGameTime() + RETRY_TICKS; // no seeds, or no way to them
            return false;
        }
        leg = Leg.TO_PILE;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && !done && (leg == Leg.MILLING || ticks < GIVE_UP_TICKS);
    }

    @Override
    public void start() {
        ticks = 0;
        milled = 0;
        done = false;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null) {
            colony.releaseMilling(ant.getUUID());
        }
        // A seed that did not become bread goes back to the food store.
        ant.setSeedForBread(false);
        target = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        switch (leg) {
            case TO_PILE -> {
                ticks++;
                walkToPile(level);
            }
            case TO_SPOT -> {
                ticks++;
                walkToSpot(level);
            }
            case MILLING -> mill(level);
        }
    }

    /** First leg: to a heap of seeds in the food store, where the ant takes one. */
    private void walkToPile(ServerLevel level) {
        if (near(target)) {
            ItemStack taken = colony.takeSeedAt(level, target, ant.getUUID(), colony.wantsRichBread());
            if (!taken.isEmpty()) {
                ant.setCarriedSeed(taken);
                ant.setSeedForBread(true);
                ColonyManager.get(level).markChanged();
                target = colony.pickBreadSpot(level, ant.getRandom());
                leg = Leg.TO_SPOT;
                if (target == null || !ant.getNavigator().moveTo(target, 1.0)) {
                    done = true; // the seed goes back to the store
                }
            } else {
                target = colony.findSeedPile(level, ant.getRandom()); // somebody else took the last seed there
                if (target == null || !ant.getNavigator().moveTo(target, 1.0)) {
                    done = true;
                }
            }
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(target, 1.0)) {
            done = true;
        }
    }

    /** Second leg: to a spot in the nursery, where the ant starts milling. */
    private void walkToSpot(ServerLevel level) {
        if (near(target)) {
            ant.getNavigator().stop();
            milled = 0;
            leg = Leg.MILLING;
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(target, 1.0)) {
            done = true;
        }
    }

    /** Third leg: the ant stands at its spot and grinds the seed for 3 seconds, then there is one more piece of bread. */
    private void mill(ServerLevel level) {
        ant.getLookControl().setLookAt(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (milled % 10 == 0) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ant.getCarriedSeed()), ant.getX(), ant.getY() + 0.1,
                    ant.getZ(), 3, 0.1, 0.05, 0.1, 0.02);
            level.playSound(null, ant.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.NEUTRAL, 0.2F, 1.6F);
        }
        if (++milled < Colony.MILL_TICKS) {
            return;
        }
        if (colony.depositBread(level, target, ant.getCarriedSeed().is(com.antcolonies.registry.ModTags.RICH_SEEDS))) {
            ant.setCarriedSeed(ItemStack.EMPTY);
            ant.setSeedForBread(false);
            ColonyManager.get(level).markChanged();
            done = true;
        } else {
            // That heap filled up meanwhile: walk to another spot.
            target = colony.pickBreadSpot(level, ant.getRandom());
            if (target == null || !ant.getNavigator().moveTo(target, 1.0)) {
                done = true; // the seed goes back to the store
            } else {
                leg = Leg.TO_SPOT;
            }
        }
    }

    private boolean near(@Nullable BlockPos spot) {
        if (spot == null) {
            return false;
        }
        double dx = ant.getX() - (spot.getX() + 0.5);
        double dz = ant.getZ() - (spot.getZ() + 0.5);
        return dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - spot.getY()) < 1.5;
    }
}
