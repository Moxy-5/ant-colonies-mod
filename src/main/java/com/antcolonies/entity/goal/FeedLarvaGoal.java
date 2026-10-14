package com.antcolonies.entity.goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.FedByNurses;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.colony.Brood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Feeds the larvae: a nurse takes one piece of ant bread off a heap (a termite: a fungus nodule off the comb, see
 * {@code Colony.nurseFood}), carries it to a hungry larva (or nymph) and gives it to it.
 * After the species profile's larva_meal pieces the larva grows into a worker. If the larva needs no more by the time the nurse
 * arrives, the bread goes back on a heap. (An ant that is interrupted while carrying bread is picked up by
 * {@link FeedQueenGoal}, which takes it to the queen.) Termite nurses feed the soldiers the same way (their jaws are
 * too big to feed themselves, see {@link FedByNurses}): a hungry soldier is picked like a hungry nymph, and the nurse
 * follows it if it moves.
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
    /** The larva, nymph or soldier this nurse feeds (always a {@link FedByNurses}). */
    @Nullable
    private Entity larva;
    /** Where the nurse last planned to walk to reach it (a soldier may move). */
    @Nullable
    private BlockPos larvaCell;
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
        if (colony == null || colony.nurseFood().available(level) <= 0) {
            return false;
        }
        larva = claimHungry(level);
        if (larva == null) {
            return false;
        }
        pile = colony.nurseFood().findFood(level, ant.getRandom());
        if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
            fed().releaseFeeder(ant.getUUID());
            nextTry = level.getGameTime() + RETRY_TICKS; // no bread, or no way to it
            return false;
        }
        leg = Leg.TO_PILE;
        return true;
    }

    /**
     * A larva or soldier that wants food and that no other nurse is on its way to, picked at random (so neither the
     * young nor the soldiers are always last), or null. This nurse is entered as its feeder.
     */
    @Nullable
    private Entity claimHungry(ServerLevel level) {
        long now = level.getGameTime();
        List<Entity> hungry = new ArrayList<>();
        for (AntLarvaEntity young : colony.brood().getLarvae(level)) {
            if (young.wantsFood() && !young.hasFeeder(now)) {
                hungry.add(young);
            }
        }
        for (TermiteSoldierEntity soldier : colony.soldiers().getSoldiers(level)) {
            if (soldier.wantsFood() && !soldier.hasFeeder(now)) {
                hungry.add(soldier);
            }
        }
        if (hungry.isEmpty()) {
            return null;
        }
        Entity chosen = hungry.get(level.getRandom().nextInt(hungry.size()));
        ((FedByNurses) chosen).claimFeeder(ant.getUUID(), now + Brood.LARVA_CLAIM_TICKS);
        return chosen;
    }

    private FedByNurses fed() {
        return (FedByNurses) larva;
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
            fed().releaseFeeder(ant.getUUID());
        }
        larva = null;
        larvaCell = null;
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
            ItemStack taken = colony.nurseFood().takeFoodAt(level, pile, false); // plain bread; rich only if nothing else is left
            if (!taken.isEmpty()) {
                ant.setCarriedSeed(taken);
                ColonyManager.get(level).markChanged();
                leg = Leg.TO_LARVA;
                ant.getNavigator().stop();
            } else {
                pile = colony.nurseFood().findFood(level, ant.getRandom()); // somebody else took the last piece there
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
        if (larva == null || !larva.isAlive() || !fed().wantsFood()) {
            putBack(level);
            return;
        }
        ant.getLookControl().setLookAt(larva);
        double dx = ant.getX() - larva.getX();
        double dz = ant.getZ() - larva.getZ();
        // A big soldier is reached a little farther from its middle.
        double reach = REACH + Math.max(0.0, larva.getBbWidth() / 2.0 - 0.15);
        if (dx * dx + dz * dz <= reach * reach && Math.abs(ant.getY() - larva.getY()) < 1.5) {
            fed().takeFood();
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ant.getCarriedSeed().copy()), larva.getX(),
                    larva.getY() + 0.1, larva.getZ(), 3, 0.05, 0.03, 0.05, 0.02);
            level.playSound(null, ant.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.15F, 1.8F);
            ant.setCarriedSeed(ItemStack.EMPTY);
            ColonyManager.get(level).markChanged();
            done = true;
        } else if ((ant.getNavigator().isDone() || !larva.blockPosition().equals(larvaCell) && ticks % 20 == 0)
                && !walkToLarva()) {
            putBack(level);
        }
    }

    /** Plans the walk to where the larva (or soldier) is now. */
    private boolean walkToLarva() {
        larvaCell = larva.blockPosition();
        return ant.getNavigator().moveTo(larvaCell, 1.0);
    }

    /** The larva is gone or full: the bread goes back on a heap. */
    private void putBack(ServerLevel level) {
        pile = colony.nurseFood().pickReturnSpot(level, ant.getRandom());
        if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
            colony.nurseFood().putDownWhereItStands(level, ant); // nowhere to put it
            ant.setCarriedSeed(ItemStack.EMPTY);
            done = true;
        } else {
            leg = Leg.BACK_TO_PILE;
        }
    }

    /** Third leg, only if the larva did not need the bread: back to a bread heap. */
    private void walkBack(ServerLevel level) {
        if (near(pile)) {
            if (!colony.nurseFood().putBack(level, pile, ant.isCarryingRichBread())) {
                pile = colony.nurseFood().pickReturnSpot(level, ant.getRandom());
                if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
                    colony.nurseFood().putDownWhereItStands(level, ant);
                    ant.setCarriedSeed(ItemStack.EMPTY);
                    done = true;
                }
                return;
            }
            ant.setCarriedSeed(ItemStack.EMPTY);
            ColonyManager.get(level).markChanged();
            done = true;
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(pile, 1.0)) {
            colony.nurseFood().putDownWhereItStands(level, ant);
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
