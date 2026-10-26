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
 *
 * <p>M8 step 2f, a species that carries its dead out (termites): an old body, or a stranger's (a dead raider), is dragged
 * along the floor to the nearest open hole and left beside it to fade, in place of being walled up; and a nestmate left
 * lying paralysed by a hunter's sting is carried in and eaten on the comb like the fresh dead, once no foe is near.
 */
public class CarryCorpseGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to a body or to its spot. */
    private static final double REACH = 0.7;
    /** The way to the bottom of the nest is long: after two minutes the ant gives up. */
    private static final int GIVE_UP_TICKS = 2400;
    /** How long (in ticks) a termite scrapes soil over an old body to bury it. */
    private static final int BURY_TICKS = 60;
    /** How fast it walks dragging a body (a raider's is bigger than the termite). */
    private static final double DRAG_SPEED = 0.8;

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
    /** The body it drags out to a hole (a carry-out species); null when not dragging. */
    @Nullable
    private AntCorpseEntity dragging;
    /** The paralysed nestmate on its way to be picked up; null once the ant carries it (or for none). */
    @Nullable
    private AbstractAntEntity paralysed;
    /** The refuse chamber spot where the ant puts the body down (or, dragging, the hole's mouth it leaves it by). */
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
            paralysed = colony.refuse().findParalysed(level, ant.blockPosition(), errand.leftAlone(), ant::mayBeAt);
            if (paralysed == null) {
                return false;
            }
            colony.refuse().claimBody(level, paralysed.getUUID());
            return true;
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
        if (corpse == null && paralysed == null) {
            walkToSpot(); // (to a body: it sets off on its first tick, this same tick)
        }
    }

    @Override
    public void stop() {
        errand.stop();
        if (colony != null && corpse != null) {
            colony.refuse().releaseCorpse(corpse);
        }
        if (colony != null && dragging != null) {
            colony.refuse().releaseCorpse(dragging); // (left where it is: another may take it on)
        }
        if (colony != null && paralysed != null) {
            colony.refuse().releaseBody(paralysed.getUUID());
        }
        if (ant.isCarryingCorpse() && (done || ticks >= GIVE_UP_TICKS) && ant.level() instanceof ServerLevel level) {
            putDown(level); // could not finish the job
        }
        corpse = null;
        dragging = null;
        paralysed = null;
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
        } else if (paralysed != null) {
            fetchParalysed(level);
        } else if (dragging != null) {
            dragOut(level);
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
        if (there && colony.refuse().mustCarryOut(corpse)) {
            // An old body or a stranger's: taken in the jaws and dragged out to a hole.
            dragging = corpse;
            corpse = null;
            level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 0.6F);
            walkOut();
        } else if (there && (burying > 0 || colony.refuse().mustBury(corpse))) {
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

    /**
     * First leg for a paralysed nestmate: to it, which the ant takes in its jaws (it is eaten on the comb like the fresh
     * dead); a foe coming near again, or a hunter taking it, ends the job.
     */
    private void fetchParalysed(ServerLevel level) {
        if (!paralysed.isAlive() || !paralysed.isParalysed() || paralysed.isPinned() || colony.refuse().foeNear(level, paralysed.blockPosition())) {
            paralysed = null;
            done = true;
            return;
        }
        ant.getLookControl().setLookAt(paralysed);
        if (errand.within(paralysed, REACH)) {
            colony.refuse().releaseBody(paralysed.getUUID());
            paralysed.discard();
            paralysed = null;
            ant.setCarryingCorpse(true);
            level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 0.8F);
            walkToSpot();
        } else if (!errand.goTo(paralysed.getUUID(), paralysed.blockPosition(), 1.0)) {
            done = true;
        }
    }

    /** Dragging a body out: it is pulled along in front of the jaws to the hole's mouth and left beside the hole. */
    private void dragOut(ServerLevel level) {
        if (!dragging.isAlive()) {
            dragging = null;
            done = true;
            return;
        }
        float yaw = ant.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        double ahead = ant.getBbWidth() * 0.6;
        dragging.dragTo(ant.position().add(-net.minecraft.util.Mth.sin(yaw) * ahead, 0, net.minecraft.util.Mth.cos(yaw) * ahead));
        if (errand.within(spot, REACH)) {
            colony.refuse().setOut(level, dragging, spot);
            dragging = null;
            done = true;
        } else if (!errand.isWalking()) {
            walkOut();
        }
    }

    /** Sets off dragging to the nearest open hole's mouth there is a way to; with none, it lets go (the body stays). */
    private void walkOut() {
        spot = errand.setOffToNearest(spot, () -> colony.refuse().wayOutMouths(ant.level()), DRAG_SPEED);
        if (spot == null) {
            done = true;
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
