package com.antcolonies.savanna.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.goal.Errand;
import com.antcolonies.entity.load.CarriedLoad;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleLoads;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * M8 step 1e: a Matabele worker carries the colony's termite waste out of the nest: a rotten layer off the prey pile, or
 * the leftovers of the eaten ones (heads, legs), up and out to the heap beside the entrance ({@link PreyPile}). Real
 * Megaponera nests are known by such middens at the entrance. A carrier that cannot finish puts its load down for the
 * numbers to finish (its layer is laid on the heap out of sight).
 */
public class CarryRemainsGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to the pile or the heap. */
    private static final double REACH = 0.9;
    /** Up out of the nest and back is a long way: after two minutes the ant gives up. */
    private static final int GIVE_UP_TICKS = 2400;
    /** Time (in ticks) before this ant looks for a load again after finding no way to one. */
    private static final int RETRY_TICKS = 200;

    private final AbstractAntEntity ant;
    /** The walks to the pile and out to the heap. */
    private final Errand<BlockPos> errand;
    @Nullable
    private PreyPile pile;
    /** Where it walks now: the pile (to take a load) or the heap (to lay it). */
    @Nullable
    private BlockPos target;
    private int ticks;
    private boolean done;
    private long nextTry;

    public CarryRemainsGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level) || level.getGameTime() < nextTry) {
            return false;
        }
        boolean holding = ant.isCarrying(MatabeleLoads.TERMITE_REMAINS);
        if (!holding && (ant.hasFullMandibles() || !ant.looksForWork(20))) {
            return false;
        }
        Colony colony = ColonyManager.get(level).getColony(ant.getColonyId());
        pile = colony == null ? null : colony.part(SavannaParts.PREY_PILE).orElse(null);
        if (pile == null) {
            if (holding) {
                ant.setLoad(CarriedLoad.NONE); // (no colony or no pile any more: nowhere for it)
            }
            return false;
        }
        if (holding) {
            target = pile.heapSpot(level); // carrying one already (something else interrupted the walk): on to the heap
        } else if (pile.claimLoad(level, ant.getUUID())) {
            target = pile.loadSpot(level, ant.getRandom());
        } else {
            return false;
        }
        if (target == null || !errand.setOff(target, 1.0)) {
            giveUp(level);
            nextTry = level.getGameTime() + RETRY_TICKS;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return pile != null && !done && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        if (ant.level() instanceof ServerLevel level && (done || ticks >= GIVE_UP_TICKS)) {
            giveUp(level);
        }
        pile = null;
        target = null;
    }

    @Override
    public void tick() {
        if (pile == null || target == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (!errand.within(target, REACH)) {
            if (!errand.walkOn(target, 1.0)) {
                done = true; // no way: another ant (or the numbers) takes it
            }
            return;
        }
        if (!ant.isCarrying(MatabeleLoads.TERMITE_REMAINS)) {
            // At the pile: a rotten layer or the leftovers into the jaws, then up and out.
            if (!pile.pickUpLoad(level, target, ant.getUUID())) {
                done = true; // somebody took it first
                return;
            }
            ant.setLoad(CarriedLoad.of(MatabeleLoads.TERMITE_REMAINS));
            level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 0.8F);
            target = pile.heapSpot(level);
            if (target == null || !errand.setOff(target, 1.0)) {
                done = true;
            } else {
                errand.begin();
            }
            return;
        }
        // At the heap: laid on it.
        if (pile.layOnHeap(level, target)) {
            ant.setLoad(CarriedLoad.NONE);
            level.playSound(null, target, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.2F, 1.4F);
            ColonyManager.get(level).markChanged();
            done = true;
        } else {
            target = pile.heapSpot(level); // full or taken meanwhile: the next spot
            if (target == null || !errand.setOff(target, 1.0)) {
                done = true;
            } else {
                errand.begin();
            }
        }
    }

    /** Could not finish: its claim goes, and a load in its jaws is left to the numbers. */
    private void giveUp(ServerLevel level) {
        if (pile == null) {
            return;
        }
        pile.releaseLoad(ant.getUUID());
        if (ant.isCarrying(MatabeleLoads.TERMITE_REMAINS)) {
            ant.setLoad(CarriedLoad.NONE);
            pile.loadDropped();
        }
    }
}
