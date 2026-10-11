package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.Brood;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Feeds the larvae: a nurse takes one piece of ant bread off a heap, carries it to a hungry larva and gives it to it.
 * After {@link Brood#LARVA_MEAL} pieces the larva grows into a worker. If the larva needs no more by the time the nurse
 * arrives, the bread goes back on a heap. (An ant that is interrupted while carrying bread is picked up by
 * {@link FeedQueenGoal}, which takes it to the queen.)
 */
public class FeedLarvaGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to a heap or to the larva. */
    private static final double REACH = 0.9;
    /** After a minute the ant gives up. */
    private static final int GIVE_UP_TICKS = 1200;
    /** Time (in ticks) before this ant looks for a hungry larva again after finding none, or no way to it. */
    private static final int RETRY_TICKS = 200;

    private enum Leg { TO_PILE, TO_LARVA, BACK_TO_PILE }

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    private Leg leg = Leg.TO_PILE;
    @Nullable
    private AntLarvaEntity larva;
    /** The heap to take bread from, or to put it back on. */
    @Nullable
    private BlockPos pile;
    private int ticks;
    private boolean done;
    private long nextTry;

    public FeedLarvaGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        // Only look for work now and then, so ants don't all think on the same tick.
        if (ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0 || !ant.mayDo(ColonyRole.NURSE)
                || level.getGameTime() < nextTry) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || colony.bakery().getBread() <= 0) {
            return false;
        }
        larva = colony.brood().claimHungryLarva(level, ant.getUUID());
        if (larva == null) {
            return false;
        }
        pile = colony.bakery().findBreadPile(level, ant.getRandom());
        if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
            larva.release(ant.getUUID());
            nextTry = level.getGameTime() + RETRY_TICKS; // no bread, or no way to it
            return false;
        }
        leg = Leg.TO_PILE;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && !done && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (larva != null) {
            larva.release(ant.getUUID());
        }
        larva = null;
        pile = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        switch (leg) {
            case TO_PILE -> walkToPile(level);
            case TO_LARVA -> walkToLarva(level);
            case BACK_TO_PILE -> walkBack(level);
        }
    }

    /** First leg: to a heap of bread, where the ant takes a piece. */
    private void walkToPile(ServerLevel level) {
        if (near(pile)) {
            ItemStack taken = colony.bakery().takeBreadAt(level, pile, false); // plain bread; rich only if nothing else is left
            if (!taken.isEmpty()) {
                ant.setCarriedSeed(taken);
                ColonyManager.get(level).markChanged();
                leg = Leg.TO_LARVA;
                ant.getNavigator().stop();
            } else {
                pile = colony.bakery().findBreadPile(level, ant.getRandom()); // somebody else took the last piece there
                if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
                    done = true;
                }
            }
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(pile, 1.0)) {
            done = true;
        }
    }

    /** Second leg: to the larva, which eats the bread. */
    private void walkToLarva(ServerLevel level) {
        if (larva == null || !larva.isAlive() || larva.isFull()) {
            putBack(level);
            return;
        }
        ant.getLookControl().setLookAt(larva);
        double dx = ant.getX() - larva.getX();
        double dz = ant.getZ() - larva.getZ();
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - larva.getY()) < 1.5) {
            larva.feed();
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.ANT_BREAD.get())), larva.getX(),
                    larva.getY() + 0.1, larva.getZ(), 3, 0.05, 0.03, 0.05, 0.02);
            level.playSound(null, ant.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.15F, 1.8F);
            ant.setCarriedSeed(ItemStack.EMPTY);
            ColonyManager.get(level).markChanged();
            done = true;
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(larva.blockPosition(), 1.0)) {
            putBack(level);
        }
    }

    /** The larva is gone or full: the bread goes back on a heap. */
    private void putBack(ServerLevel level) {
        pile = colony.bakery().pickBreadSpot(level, ant.getRandom());
        if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
            ant.setCarriedSeed(ItemStack.EMPTY); // nowhere to put it
            done = true;
        } else {
            leg = Leg.BACK_TO_PILE;
        }
    }

    /** Third leg, only if the larva did not need the bread: back to a bread heap. */
    private void walkBack(ServerLevel level) {
        if (near(pile)) {
            if (!colony.bakery().depositBread(level, pile, ant.isCarryingRichBread())) {
                pile = colony.bakery().pickBreadSpot(level, ant.getRandom());
                if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
                    ant.setCarriedSeed(ItemStack.EMPTY);
                    done = true;
                }
                return;
            }
            ant.setCarriedSeed(ItemStack.EMPTY);
            ColonyManager.get(level).markChanged();
            done = true;
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(pile, 1.0)) {
            ant.setCarriedSeed(ItemStack.EMPTY);
            done = true;
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
