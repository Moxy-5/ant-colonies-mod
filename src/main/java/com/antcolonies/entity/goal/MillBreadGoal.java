package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Bakery;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyParts;
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
 * there for the species profile's mill_ticks (3 seconds) and the spot gets one more piece of bread. A worker that already
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
    /** The walks to the seeds and to the mill. */
    private final Errand<BlockPos> errand;
    @Nullable
    private Colony colony;
    /** Its colony's bakery (asked with the colony: only a species that lists the bakery mills bread). */
    @Nullable
    private Bakery bakery;
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
        this.errand = new Errand<>(ant);
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
            bakery = colony == null ? null : colony.part(ColonyParts.BAKERY).orElse(null);
            target = bakery == null ? null : bakery.pickBreadSpot(level, ant.getRandom());
            if (!errand.setOff(target, 1.0)) {
                if (bakery != null) {
                    bakery.releaseMilling(ant.getUUID());
                }
                ant.setSeedForBread(false); // StoreSeedGoal takes it from here
                return false;
            }
            leg = Leg.TO_SPOT;
            return true;
        }
        // Only look for work now and then, so ants don't all think on the same tick (every time just before it would count as idle).
        if (ant.hasFullMandibles() || !ant.looksForWork(20) || !ant.mayDo(ColonyRole.NURSE)
                || level.getGameTime() < nextTry) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        bakery = colony == null ? null : colony.part(ColonyParts.BAKERY).orElse(null);
        if (bakery == null || colony.food().getFood() <= 0 || !bakery.claimMilling(level, ant.getUUID())) {
            return false;
        }
        target = colony.food().findSeedPile(level, ant.getRandom());
        if (!errand.setOff(target, 1.0)) {
            bakery.releaseMilling(ant.getUUID());
            nextTry = level.getGameTime() + RETRY_TICKS; // no seeds, or no way to them
            return false;
        }
        leg = Leg.TO_PILE;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && bakery != null && !done && (leg == Leg.MILLING || ticks < GIVE_UP_TICKS);
    }

    @Override
    public void start() {
        ticks = 0;
        milled = 0;
        done = false;
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        if (bakery != null) {
            bakery.releaseMilling(ant.getUUID());
        }
        // A seed that did not become bread goes back to the food store.
        ant.setSeedForBread(false);
        target = null;
        colony = null;
        bakery = null;
    }

    @Override
    public void tick() {
        if (colony == null || bakery == null || !(ant.level() instanceof ServerLevel level)) {
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
        if (errand.within(target, REACH)) {
            ItemStack taken = colony.food().takeSeedAt(level, target, ant.getUUID(), bakery.wantsRichBread());
            if (!taken.isEmpty()) {
                ant.setCarriedSeed(taken);
                ant.setSeedForBread(true);
                ColonyManager.get(level).markChanged();
                target = bakery.pickBreadSpot(level, ant.getRandom());
                leg = Leg.TO_SPOT;
                if (!errand.setOff(target, 1.0)) {
                    done = true; // the seed goes back to the store
                }
            } else {
                target = colony.food().findSeedPile(level, ant.getRandom()); // somebody else took the last seed there
                if (!errand.setOff(target, 1.0)) {
                    done = true;
                }
            }
        } else if (!errand.walkOn(target, 1.0)) {
            done = true;
        }
    }

    /** Second leg: to a spot in the nursery, where the ant starts milling. */
    private void walkToSpot(ServerLevel level) {
        if (errand.within(target, REACH)) {
            errand.stop();
            milled = 0;
            leg = Leg.MILLING;
        } else if (!errand.walkOn(target, 1.0)) {
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
        if (++milled < colony.profile().food().millTicks()) {
            return;
        }
        if (bakery.depositBread(level, target, ant.getCarriedSeed().is(com.antcolonies.registry.ModTags.RICH_SEEDS))) {
            ant.setCarriedSeed(ItemStack.EMPTY);
            ant.setSeedForBread(false);
            ColonyManager.get(level).markChanged();
            done = true;
        } else {
            // That heap filled up meanwhile: walk to another spot.
            target = bakery.pickBreadSpot(level, ant.getRandom());
            if (!errand.setOff(target, 1.0)) {
                done = true; // the seed goes back to the store
            } else {
                leg = Leg.TO_SPOT;
            }
        }
    }
}
