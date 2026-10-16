package com.antcolonies.entity.goal;

import java.util.EnumSet;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Cleaning the antennae (the animation engine, step 7, 2026-10-16). Real ants groom often, the antennae most of all: an
 * ant stops, draws each antenna through the comb on the front leg of its side (the strigil) a few times, then cleans the
 * comb in its jaws (Farish 1972; Hackmann et al. 2015). Here an insect standing about with nothing to do now and then
 * stops and cleans one antenna, or both one after the other, with its profile's {@code body_language.groom}; a species
 * without it never does (the termites, whose model has no cleaning clips yet). Mind only: it says "stand still and clean
 * this side"; the body's clips do the rest. Every job comes before it, but a clean once begun is finished unless the
 * insect is hurt, gets wet, loses its footing or the alarm goes (a real ant breaks off at once).
 */
public class GroomGoal extends Goal {
    private final AbstractAntEntity insect;
    private byte side;
    private int ticksLeft;
    /** Not again before this tick: at least a second standing between two cleanings (my pick), so each clip plays anew. */
    private int restUntil;

    public GroomGoal(AbstractAntEntity insect) {
        this.insect = insect;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (insect.tickCount % 20 != (insect.getId() + 8) % 20 || insect.tickCount < restUntil || !insect.isIdle() || !mayCleanHere()) {
            return false; // (asked once a second, not on the same tick as a nap; the game only looks for new goals on every other tick, so the offset must be even)
        }
        SpeciesProfile.Groom groom = insect.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::groom).orElse(null);
        if (groom == null) {
            return false;
        }
        // A restless one cleans more often than a calm one (its personality).
        float chance = insect.groomChanceForTests() >= 0.0F ? insect.groomChanceForTests()
                : groom.chancePerSecond() * (0.5F + insect.restlessness());
        if (insect.getRandom().nextFloat() >= chance) {
            return false;
        }
        boolean both = insect.getRandom().nextFloat() < groom.bothShare();
        side = both ? AbstractAntEntity.ACTIVITY_GROOM_BOTH
                : insect.getRandom().nextBoolean() ? AbstractAntEntity.ACTIVITY_GROOM_L : AbstractAntEntity.ACTIVITY_GROOM_R;
        // The clip, the blend into it and a little to settle back.
        ticksLeft = (both ? AbstractAntEntity.GROOM_BOTH_TICKS : AbstractAntEntity.GROOM_ONE_TICKS)
                + AbstractAntEntity.GROOM_SETTLE_TICKS + 2;
        return true;
    }

    /** Whether it may stop and clean here: standing on a floor with empty jaws, awake, unhurt, dry, with no alarm. */
    private boolean mayCleanHere() {
        if (!insect.onGround() || insect.isClimbing() || insect.hasFullMandibles() || insect.hurtTime > 0 || insect.isInWater()
                || insect.isAsleep() || insect.getNoticing() != AbstractAntEntity.NOTICE_NONE) {
            return false;
        }
        Colony colony = insect.getColony();
        return colony == null || colony.alarm().level(insect.level()) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return ticksLeft > 0 && insect.onGround() && insect.hurtTime == 0 && !insect.isInWater()
                && (insect.getColony() == null || insect.getColony().alarm().level(insect.level()) == 0);
    }

    @Override
    public void start() {
        insect.getNavigator().stop();
        insect.setActivity(side);
    }

    @Override
    public void tick() {
        ticksLeft--;
    }

    @Override
    public void stop() {
        insect.setActivity(AbstractAntEntity.ACTIVITY_NONE);
        restUntil = insect.tickCount + 20;
    }
}
