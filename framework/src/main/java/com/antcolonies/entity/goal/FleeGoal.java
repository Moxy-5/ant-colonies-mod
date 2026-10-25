package com.antcolonies.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.fight.FightNumbers;
import com.antcolonies.fight.Fights;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * Running from a hunter (M8 fight polish, round B, an early piece of step 2f, {@code docs/plans/m8-matabele.md}): an
 * insect of a body its species file lists under {@code fight.flee} that feels a hunter close (an insect whose species
 * hunts its own, within {@code feel_radius}), or a nestmate felled or held in a hunter's jaws that close, stands
 * startled a moment ({@link #STARTLE_LEAST} to {@link #STARTLE_MOST} ticks), then drops what it is doing and runs for the nearest way into its nest (an open foraging hole, else any way in) at {@code speed} times
 * its walk, and goes down. It then stays in for {@code hide_ticks} ({@link AbstractAntEntity#mayGoOut}); once inside the
 * nest, past its feeding ways, the hunters leave it ({@link AbstractAntEntity#isSheltered}). Real Macrotermes workers are blind: they feel the raiders'
 * steps and the alarm scent of a stung nestmate only close by, and run for the galleries while the soldiers stay out.
 * The legs walk it ({@code AntNavigator}); this goal only says where. Species-neutral: any prey species can list bodies.
 */
public class FleeGoal extends Goal {
    /** How often (ticks) it feels for hunters. */
    private static final int FEEL_EVERY = 5;
    /** If it is not inside after this long (ticks) it gives up this flight (it may feel the hunter again and run on). */
    private static final int GIVE_UP_TICKS = 400;
    /** How long (ticks, the shortest and longest) it stands startled when it first feels the danger, before it runs. */
    private static final int STARTLE_LEAST = 5;
    private static final int STARTLE_MOST = 15;
    /** Felt nothing this long (ticks), and the next danger startles it afresh. */
    private static final int FORGET_TICKS = 40;

    private final AbstractAntEntity ant;
    private final Errand<BlockPos> errand;
    @Nullable
    private Colony colony;
    private int ticks;
    /** When it first felt the danger it now feels, how long it stands startled, and when it last felt it. */
    private long feltAt = Long.MIN_VALUE;
    private int startle;
    private long lastFelt = Long.MIN_VALUE;

    public FleeGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if ((ant.tickCount + ant.getId()) % FEEL_EVERY != 0 || !(ant.level() instanceof ServerLevel level)
                || ant.isDowned() || ant.isPinned()) {
            return false;
        }
        FightNumbers.Flee flee = flee();
        if (flee == null || !flee.bodies().contains(ant.fightBody())) {
            return false;
        }
        colony = ant.getColony();
        if (colony == null || colony.nest().isInside(level, ant.blockPosition()) || !feelsDanger(level, flee)) {
            return false;
        }
        // Startled first: it stands a moment, antennae up, before it bolts (real insects take a beat to react).
        long now = level.getGameTime();
        if (now - lastFelt > FORGET_TICKS) {
            feltAt = now;
            startle = STARTLE_LEAST + ant.getRandom().nextInt(STARTLE_MOST - STARTLE_LEAST + 1);
        }
        lastFelt = now;
        if (now - feltAt < startle) {
            return false;
        }
        ant.hideUntil(level.getGameTime() + flee.hideTicks());
        Colony home = colony;
        BlockPos towards = colony.foragingHoles().waysOut(level).stream().map(ForagingHoles.WayOut::mouth)
                .min(Comparator.comparingDouble(mouth -> mouth.distSqr(ant.blockPosition()))).orElse(colony.getCenter().below());
        return errand.setOff(at -> home.nest().isInside(level, at.cell()) && !home.foragingHoles().isDoorway(level, at.cell()),
                towards, flee.speed());
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && errand.isWalking() && ticks < GIVE_UP_TICKS && !ant.isDowned() && !ant.isPinned();
    }

    @Override
    public void start() {
        ticks = 0;
        errand.begin();
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void stop() {
        errand.stop();
        colony = null;
    }

    /** Whether it feels a hunter close, or a nestmate felled or held by one. */
    private boolean feelsDanger(ServerLevel level, FightNumbers.Flee flee) {
        AABB near = ant.getBoundingBox().inflate(flee.feelRadius());
        return !level.getEntitiesOfClass(AbstractAntEntity.class, near, other -> other != ant && other.isAlive()
                && (!other.isDowned() && Fights.hunts(other.profile(), ant)
                        || ant.getColonyId() != null && ant.getColonyId().equals(other.getColonyId()) && (other.isDowned() || other.isPinned())))
                .isEmpty();
    }

    @Nullable
    private FightNumbers.Flee flee() {
        return FightNumbers.of(ant.profile()).flatMap(FightNumbers::flee).orElse(null);
    }
}
