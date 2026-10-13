package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Feeds the queen: the worker takes one piece of ant bread off a heap in the nursery (a termite: a fungus nodule off
 * the comb in a garden, see {@code Colony.nurseFood}), carries it to the queen in her room and gives it to her. After
 * the species profile's queen meal she carries an egg (a termite queen lays a steady stream while she is fed). If the queen does not want the
 * bread after all, the worker puts it back on a bread heap.
 */
public class FeedQueenGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to a heap or to the queen. */
    private static final double REACH = 0.9;
    /** After a minute the ant gives up. */
    private static final int GIVE_UP_TICKS = 1200;
    /** Time (in ticks) before this ant looks for the queen's food again after finding no way to a heap. */
    private static final int RETRY_TICKS = 200;

    private enum Leg { TO_PILE, TO_QUEEN, BACK_TO_PILE }

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    private Leg leg = Leg.TO_PILE;
    /** The heap to take bread from, or to put it back on. */
    @Nullable
    private BlockPos pile;
    private int ticks;
    private boolean done;
    /** Game time before which this ant does not try again. Not saved. */
    private long nextTry;

    public FeedQueenGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        // An ant that holds bread (something interrupted the walk) carries on to the queen.
        if (ant.isCarryingBread()) {
            colony = ColonyManager.get(level).getColony(ant.getColonyId());
            leg = Leg.TO_QUEEN;
            pile = null;
            return colony != null;
        }
        // Only look for work now and then, so ants don't all think on the same tick.
        if (ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0 || !ant.mayDo(ColonyRole.NURSE)
                || level.getGameTime() < nextTry) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || colony.nurseFood().available(level) <= 0 || !colony.queenCare().claimFeeding(level, ant.getUUID())) {
            return false;
        }
        pile = colony.nurseFood().findFood(level, ant.getRandom());
        if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
            colony.queenCare().releaseFeeding(ant.getUUID());
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
        if (colony != null) {
            colony.queenCare().releaseFeeding(ant.getUUID());
        }
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
            case TO_QUEEN -> walkToQueen(level);
            case BACK_TO_PILE -> walkBack(level);
        }
    }

    /** First leg: to a heap of bread in the nursery, where the ant takes a piece. */
    private void walkToPile(ServerLevel level) {
        if (near(pile)) {
            ItemStack taken = colony.nurseFood().takeFoodAt(level, pile, true); // she gets the rich bread first
            if (!taken.isEmpty()) {
                ant.setCarriedSeed(taken);
                ColonyManager.get(level).markChanged();
                leg = Leg.TO_QUEEN;
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

    /** Second leg: to the queen, who takes the bread. */
    private void walkToQueen(ServerLevel level) {
        AbstractAntEntity queen = colony.getQueen(level);
        if (queen == null) {
            done = true;
            return;
        }
        ant.getLookControl().setLookAt(queen);
        double dx = ant.getX() - queen.getX();
        double dz = ant.getZ() - queen.getZ();
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - queen.getY()) < 1.5) {
            if (colony.queenCare().feedQueen(level, ant.isCarryingRichBread())) {
                ant.setCarriedSeed(ItemStack.EMPTY);
                ColonyManager.get(level).markChanged();
                done = true;
            } else {
                // She wants no more right now: the bread goes back on a heap.
                pile = colony.nurseFood().pickReturnSpot(level, ant.getRandom());
                if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
                    colony.nurseFood().putDownWhereItStands(level, ant); // nowhere to put it
                    ant.setCarriedSeed(ItemStack.EMPTY);
                    done = true;
                } else {
                    leg = Leg.BACK_TO_PILE;
                }
            }
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(queen.blockPosition(), 1.0)) {
            done = true;
        }
    }

    /** Third leg, only if the queen did not want the bread: back to a bread heap. */
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
