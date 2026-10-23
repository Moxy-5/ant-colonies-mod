package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntCorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Clears away the dead: picks up the body of a dead nestmate in its mandibles, carries it down to the refuse chamber and
 * sets it down on a free spot there (ants, once the refuse chamber is dug out). Termites, which have no refuse chamber,
 * carry a fresh body into a fungus garden, where it is eaten (a layer of comb), and wall an old one up with soil where it
 * lies (see {@code Refuse}). An insect that cannot finish the job puts the body down where it stands, and another takes
 * over. The job of the builders (and of any worker with nothing to do).
 */
public class CarryCorpseGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to a body or to its spot. */
    private static final double REACH = 0.7;
    /** The way to the bottom of the nest is long: after two minutes the ant gives up. */
    private static final int GIVE_UP_TICKS = 2400;
    /** How long (in ticks) a termite scrapes soil over an old body to bury it. */
    private static final int BURY_TICKS = 60;

    private final AbstractAntEntity ant;
    /**
     * The walk to the body and on to its spot; a body it found no way to (by its id) is left alone for a while, and after
     * a few chamber spots with no way to them the ant puts the body down.
     */
    private final Errand<UUID> errand;
    @Nullable
    private Colony colony;
    /** The body on its way to be picked up; null once the ant carries it. */
    @Nullable
    private AntCorpseEntity corpse;
    /** The refuse chamber spot where the ant puts the body down. */
    @Nullable
    private BlockPos spot;
    private int ticks;
    /** Ticks spent burying the body (0: not burying). */
    private int burying;
    private boolean done;

    public CarryCorpseGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        if (ant.isCarryingCorpse()) {
            // Carrying a body (something else interrupted the walk): carry on to the refuse chamber.
            colony = ColonyManager.get(level).getColony(ant.getColonyId());
            spot = null; // (start() sets off to the nearest free spot)
            corpse = null;
            if (colony == null || colony.refuse().destinations(level, ant.getRandom()).isEmpty()) {
                putDown(level);
                return false;
            }
            return true;
        }
        // Only look for work now and then, so ants don't all think on the same tick (every time just before it would count as idle).
        if (ant.hasFullMandibles() || !ant.looksForWork(20) || !ant.mayDo(ColonyRole.BUILDER)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null) {
            return false;
        }
        corpse = colony.refuse().findLooseCorpse(level, ant.blockPosition(), errand.leftAlone(), ant::mayBeAt);
        if (corpse == null) {
            return false;
        }
        colony.refuse().claimCorpse(level, corpse);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && !done && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        burying = 0;
        done = false;
        errand.begin();
        if (corpse == null) {
            walkToSpot(); // (to a body: it sets off on its first tick, this same tick)
        }
    }

    @Override
    public void stop() {
        errand.stop();
        if (colony != null && corpse != null) {
            colony.refuse().releaseCorpse(corpse);
        }
        if (ant.isCarryingCorpse() && (done || ticks >= GIVE_UP_TICKS) && ant.level() instanceof ServerLevel level) {
            putDown(level); // could not finish the job
        }
        corpse = null;
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (corpse != null) {
            fetchCorpse(level);
        } else if (spot != null) {
            bringCorpse(level);
        }
    }

    /** First leg: to the body, which the ant takes in its mandibles. */
    private void fetchCorpse(ServerLevel level) {
        if (!corpse.isAlive()) {
            corpse = null;
            done = true;
            return;
        }
        ant.getLookControl().setLookAt(corpse);
        boolean there = errand.within(corpse, REACH);
        if (there && colony.refuse().eatsOrBuries() && (burying > 0 || !colony.refuse().isFresh(corpse))) {
            // An old body: scraped over with soil where it lies.
            errand.stop();
            if (++burying % 15 == 0) {
                level.playSound(null, corpse.blockPosition(), SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.2F, 1.5F);
            }
            if (burying >= BURY_TICKS) {
                colony.refuse().bury(level, corpse);
                corpse = null;
                done = true;
            }
        } else if (there) {
            colony.refuse().releaseCorpse(corpse);
            corpse.discard();
            corpse = null;
            ant.setCarryingCorpse(true);
            level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 0.8F);
            walkToSpot(); // (no room after all: stop() puts the body down)
        } else if (!errand.goTo(corpse.getUUID(), corpse.blockPosition(), 1.0)) {
            done = true; // no way to get there: let another ant have a go
        }
    }

    /** Second leg: down to a free spot in the refuse chamber, where the ant puts the body down (termites: onto the comb, where it is eaten). */
    private void bringCorpse(ServerLevel level) {
        if (errand.within(spot, REACH)) {
            if (colony.refuse().setDown(level, spot)) {
                ant.setCarryingCorpse(false);
                done = true;
            } else {
                errand.notThere(spot); // another body lies there now (or the comb is full)
                spot = null;
                walkToSpot();
            }
        } else if (!errand.isWalking()) {
            walkToSpot();
        }
    }

    /**
     * Sets off to the spot; without one, or with no way there, to the nearest free spot there is a way to (the refuse
     * heap grows from the chamber's door inward). Gives up with none left.
     */
    private void walkToSpot() {
        spot = errand.setOffToNearest(spot, () -> colony.refuse().destinations(ant.level(), ant.getRandom()), 1.0);
        if (spot == null) {
            done = true;
        }
    }

    /** The ant puts the body down where it stands, so another ant can take it from there. */
    private void putDown(ServerLevel level) {
        ant.setCarryingCorpse(false);
        if (colony != null) {
            colony.refuse().spawnCorpse(level, ant.getX(), ant.getY(), ant.getZ());
        }
    }
}
