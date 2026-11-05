package com.antcolonies.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Cleaning a nestmate (M8 step 3Q, allogrooming; {@code docs/DESIGN.md} "Step 3Q"). Real ants lick their nestmates now
 * and then; termites groom each other constantly (it spreads the colony's smell and takes fungus spores and mites off;
 * in Macrotermes a large share of a worker's time in the nest). Here an insect with nothing to do, in its nest, now and
 * then picks a resting nestmate within its species' {@code body_language.allogroom.reach}, walks to its side, and
 * cleans it for a few seconds ({@code ticks}), bowed over it, the jaws nibbling; the nestmate holds still meanwhile
 * ({@link AbstractAntEntity#groomedBy}: no strolls). A species without {@code allogroom} never does it. Every job comes
 * before it (ClampCareGoal's care of a clamped nestmate first of all); the hurt, the alarm, a job or the nestmate
 * walking off end it. Step 5's wound care is to build on it. The legs walk it; this goal only says where.
 */
public class AllogroomGoal extends Goal {
    /** It gives up walking to the nestmate after this long (ticks). */
    private static final int WALK_TICKS = 200;

    private final AbstractAntEntity insect;
    @Nullable
    private AbstractAntEntity mate;
    private int ticksLeft;
    private int walking;
    private int replan;
    /** Not again before this tick: a few seconds between two cleanings (my pick). */
    private int restUntil;

    public AllogroomGoal(AbstractAntEntity insect) {
        this.insect = insect;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (insect.tickCount % 20 != (insect.getId() + 4) % 20 || insect.tickCount < restUntil || !insect.isIdle()
                || !(insect.level() instanceof ServerLevel level) || !mayHere(insect)) {
            return false; // (asked once a second, not on a nap's or a cleaning's tick; an even offset, see GroomGoal)
        }
        SpeciesProfile.Allogroom numbers = numbers();
        if (numbers == null || insect.getRandom().nextFloat() >= numbers.chancePerSecond() * (0.5F + insect.restlessness())) {
            return false;
        }
        Colony colony = insect.getColony();
        if (colony == null || !colony.nest().isInside(level, insect.blockPosition())) {
            return false; // in the nest only
        }
        double reach = numbers.reach();
        mate = level.getEntitiesOfClass(AbstractAntEntity.class, insect.getBoundingBox().inflate(reach),
                        other -> other != insect && other.isAlive() && colony.getId().equals(other.getColonyId())
                                && (other.isIdle() || other.isAsleep()) && other.getActivity() != AbstractAntEntity.ACTIVITY_ALLOGROOM
                                && !other.isBeingGroomed(null) && mayHere(other) && other.distanceToSqr(insect) <= reach * reach
                                && colony.nest().isInside(level, other.blockPosition()))
                .stream().min(Comparator.comparingDouble(other -> other.distanceToSqr(insect))).orElse(null);
        if (mate == null) {
            return false;
        }
        ticksLeft = numbers.ticksFor(insect.getRandom().nextDouble());
        return true;
    }

    @Nullable
    private SpeciesProfile.Allogroom numbers() {
        return insect.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::allogroom).orElse(null);
    }

    /** Whether this insect may clean or be cleaned here: on a floor, jaws empty, unhurt, dry, nothing clamped on, no alarm. */
    private static boolean mayHere(AbstractAntEntity who) {
        if (!who.onGround() || who.isClimbing() || who.hasFullMandibles() || who.hurtTime > 0 || who.isInWater()
                || who.isDowned() || who.clamp().isOn() || who.getNoticing() != AbstractAntEntity.NOTICE_NONE || who.getFightSpot() != null) {
            return false;
        }
        Colony colony = who.getColony();
        return colony == null || !colony.isAlarmed(who.level());
    }

    @Override
    public boolean canContinueToUse() {
        return mate != null && mate.isAlive() && ticksLeft > 0 && walking < WALK_TICKS && mayHere(insect) && mayHere(mate)
                && (mate.isIdle() || mate.isAsleep()) && !mate.isBeingGroomed(insect.getUUID());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true; // it counts its time in ticks
    }

    @Override
    public void start() {
        walking = 0;
        replan = 0;
    }

    @Override
    public void stop() {
        insect.getNavigator().stop();
        insect.setActivity(AbstractAntEntity.ACTIVITY_NONE);
        if (mate != null && mate.isBeingGroomed(null) && !mate.isBeingGroomed(insect.getUUID())) {
            mate.groomedBy(insect.getUUID(), 0); // (only its own hold is let go)
        }
        mate = null;
        restUntil = insect.tickCount + 100;
    }

    @Override
    public void tick() {
        if (mate == null) {
            return;
        }
        double reach = (insect.getBbWidth() + mate.getBbWidth()) * 0.5 + 0.3;
        if (insect.distanceToSqr(mate) > reach * reach) {
            walking++;
            insect.setActivity(AbstractAntEntity.ACTIVITY_NONE);
            if (--replan <= 0 || insect.getNavigator().isDone()) {
                replan = 20;
                insect.getNavigator().moveTo(mate.blockPosition(), 1.0);
            }
            return;
        }
        // At its side: bowed over it, cleaning; the nestmate holds still.
        insect.getNavigator().stop();
        insect.setActivity(AbstractAntEntity.ACTIVITY_ALLOGROOM);
        mate.groomedBy(insect.getUUID(), insect.level().getGameTime() + 10);
        face(mate.getX(), mate.getZ());
        ticksLeft--;
    }

    private void face(double x, double z) {
        double dx = x - insect.getX();
        double dz = z - insect.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        float yaw = Mth.approachDegrees(insect.getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, 20.0F);
        insect.setYRot(yaw);
        insect.setYBodyRot(yaw);
        insect.setYHeadRot(yaw);
    }
}
