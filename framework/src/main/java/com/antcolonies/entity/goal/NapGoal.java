package com.antcolonies.entity.goal;

import java.util.EnumSet;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A short nap (the animation engine, 2026-10-16). Real ants sleep in many short naps (Cassill et al. 2009, fire ants:
 * workers about 250 naps a day of about a minute each, queens fewer and longer). Here an insect with nothing to do,
 * standing on a floor inside its nest (anywhere while it has no colony), now and then falls asleep where it stands, for
 * its profile's {@code body_language.naps}; a species without them never naps (the termites, for now). Every job comes
 * before this goal, so any job wakes it; so do being hurt and the colony's alarm. Mind only: it says "hold still and
 * sleep"; the body's clips do the rest (lying still, with bouts of antenna twitches).
 */
public class NapGoal extends Goal {
    private final AbstractAntEntity insect;
    /** A queen's naps are longer (the profile's {@code queen_ticks}). */
    private final boolean queen;
    private int ticksLeft;

    public NapGoal(AbstractAntEntity insect, boolean queen) {
        this.insect = insect;
        this.queen = queen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (insect.tickCount % 20 != insect.getId() % 20 || !insect.isIdle() || !mayRestHere()) {
            return false; // (asked once a second)
        }
        SpeciesProfile.Naps naps = insect.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::naps).orElse(null);
        // A restless one naps half as often as most, a calm one half as often again (its personality).
        float chance = insect.napChanceForTests() >= 0.0F ? insect.napChanceForTests()
                : naps == null ? 0.0F : naps.chancePerSecond() * (1.5F - insect.restlessness());
        if (naps == null || insect.getRandom().nextFloat() >= chance) {
            return false;
        }
        ticksLeft = naps.ticksFor(queen, insect.getRandom().nextDouble());
        return ticksLeft > 0;
    }

    /** Whether it may sleep here now: standing on a floor with empty jaws, inside its nest, unhurt, with no alarm. */
    private boolean mayRestHere() {
        if (!insect.onGround() || insect.isClimbing() || insect.hasFullMandibles() || insect.hurtTime > 0 || insect.isInWater()) {
            return false;
        }
        Colony colony = insect.getColony();
        return colony == null || colony.nest().isInside(insect.level(), insect.blockPosition()) && !colony.isAlarmed(insect.level());
    }

    @Override
    public boolean canContinueToUse() {
        return ticksLeft > 0 && mayRestHere();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true; // it counts its time in ticks (the game ticks other goals only every other tick)
    }

    @Override
    public void start() {
        insect.getNavigator().stop();
        insect.setAsleep(true);
    }

    @Override
    public void tick() {
        ticksLeft--;
    }

    @Override
    public void stop() {
        insect.setAsleep(false);
    }
}
