package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyNews;

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

    private final AbstractAntEntity ant;
    /** The walk to the seed; a seed it found no way to (by item entity id) is left alone for a while. */
    private final Errand<Integer> errand;
    @Nullable
    private Colony colony;
    @Nullable
    private ItemEntity seed;
    private int ticks;

    public GatherSeedGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick (every time just before it would count as idle).
        if (ant.getColonyId() == null || ant.hasFullMandibles() || !ant.looksForWork(20)
                || !ant.mayDo(ColonyRole.FORAGER) || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.isForagingTime(level)) {
            return false; // foragers only go out at foraging time (termites: at night and in the rain)
        }
        if (colony.mustWait(level, ant)) {
            return false; // waits by the entrance for the homecomers' touches (Gordon's rule, see colony.Door)
        }
        seed = colony.food().findSeed(level, ant.blockPosition(), errand.leftAlone());
        if (seed == null || colony.passesBy(level, ant.getUUID(), seed.blockPosition())) {
            return false; // (a recruit on its way to its patch passes poorer food by)
        }
        colony.food().claimSeed(level, seed, ant.getUUID());
        colony.news().tell(level, ColonyNews.SET_OUT, new ColonyNews.SetOut(ant));
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
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
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
        if (errand.within(seed, REACH)) {
            take(level);
        } else if (!errand.goTo(seed.getId(), seed.blockPosition(), 1.0)) {
            ticks = GIVE_UP_TICKS; // no way to get there: let another ant have a go
        }
    }

    /** Takes one seed out of the pile on the ground. */
    private void take(ServerLevel level) {
        if (!colony.claimPlaceForAPiece(level, ant.getUUID())) {
            ticks = GIVE_UP_TICKS; // nowhere to take it now (the store full, the mill busy): it stays where it lies
            return;
        }
        ItemStack stack = seed.getItem();
        ItemStack one = stack.split(1);
        if (stack.isEmpty()) {
            seed.discard();
        } else {
            seed.setItem(stack);
        }
        ant.setCarriedSeed(one);
        colony.news().tell(level, ColonyNews.FOOD_FOUND, new ColonyNews.FoodFound(ant.getUUID(), seed.blockPosition())); // (its spot now; a rich patch: a scent line home)

        ant.setSeedForBread(colony.pieceGoesToMill(ant.getUUID())); // to the mill if the store has no room
        level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 1.6F);
    }
}
