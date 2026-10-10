package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.QueenAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Feeds the queen: the worker takes one seed off a heap in the food store, carries it to the queen in her room and
 * gives it to her. After {@link Colony#QUEEN_MEAL} seeds she carries an egg. If something goes wrong on the way, the
 * seed is not lost: {@link StoreSeedGoal} puts it back in the food store.
 */
public class FeedQueenGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to a heap or to the queen. */
    private static final double REACH = 0.9;
    /** After a minute the ant gives up. */
    private static final int GIVE_UP_TICKS = 1200;
    /** Time (in ticks) before this ant looks for the queen's food again after finding no way to a heap. */
    private static final int RETRY_TICKS = 200;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    /** The heap to take a seed from; null once the ant has its seed. */
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
        // Only look for work now and then, so ants don't all think on the same tick.
        if (ant.getColonyId() == null || ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0
                || !(ant.level() instanceof ServerLevel level) || level.getGameTime() < nextTry) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.claimFeeding(level, ant.getUUID())) {
            return false;
        }
        pile = colony.findSeedPile(level, ant.getRandom());
        if (pile == null || !ant.getNavigator().moveTo(pile, 1.0)) {
            colony.releaseFeeding(ant.getUUID());
            nextTry = level.getGameTime() + RETRY_TICKS; // no seeds, or no way to them
            return false;
        }
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
            colony.releaseFeeding(ant.getUUID());
        }
        // A seed that did not reach the queen goes back to the food store.
        ant.setSeedForQueen(false);
        pile = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (pile != null) {
            walkToPile(level);
        } else {
            walkToQueen(level);
        }
    }

    /** First leg: to a heap in the food store, where the ant takes a seed. */
    private void walkToPile(ServerLevel level) {
        double dx = ant.getX() - (pile.getX() + 0.5);
        double dz = ant.getZ() - (pile.getZ() + 0.5);
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - pile.getY()) < 1.5) {
            if (colony.takeSeedAt(level, pile)) {
                ant.setCarriedSeed(new ItemStack(Items.WHEAT_SEEDS));
                ant.setSeedForQueen(true);
                ColonyManager.get(level).markChanged();
                pile = null;
                ant.getNavigator().stop();
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

    /** Second leg: to the queen, who takes the seed. */
    private void walkToQueen(ServerLevel level) {
        QueenAntEntity queen = colony.getQueen(level);
        if (queen == null) {
            done = true;
            return;
        }
        ant.getLookControl().setLookAt(queen);
        double dx = ant.getX() - queen.getX();
        double dz = ant.getZ() - queen.getZ();
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(ant.getY() - queen.getY()) < 1.5) {
            if (colony.feedQueen(level)) {
                ant.setCarriedSeed(ItemStack.EMPTY);
                ant.setSeedForQueen(false);
                ColonyManager.get(level).markChanged();
            }
            done = true; // (if she took no more, stop() lets the ant put the seed back)
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(queen.blockPosition(), 1.0)) {
            done = true;
        }
    }
}
