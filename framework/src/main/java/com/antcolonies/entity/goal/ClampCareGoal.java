package com.antcolonies.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.fight.FightNumbers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Taking a clamped foe off a nestmate's leg in the nest (M8 step 2c, {@code docs/DESIGN.md} "Step 2c, clamping"). Real
 * (Frank et al. 2017, "Saving the injured"): back in the nest, nestmates removed the termites clinging to a raider's
 * legs in 9 cases out of 10 within a day, the leg kept. An insect in its nest, with nothing else to do, now and then
 * looks round {@link #LOOK_REACH} blocks for a nestmate in the nest with a foe clamped on
 * ({@link com.antcolonies.entity.Clamp}), walks to it, holds it still and works on it for its species'
 * {@code fight.clamped.care_ticks}: a living foe is killed first (its head stays on, the work starts over), then the
 * head comes off ({@link com.antcolonies.entity.Clamp#takeOff}). Carrying the hurt home and pulling clamps off on the
 * field are step 5's. The legs walk it; this goal only says where. BUGS 349: one carer at a time (it claims its
 * nestmate when it picks it, {@link AbstractAntEntity#claimCare}), and only once the nestmate has come to rest in the
 * rooms ({@link AbstractAntEntity#restsForCare}), never on its way in.
 */
public class ClampCareGoal extends Goal {
    /** How far (blocks) it looks for a clamped nestmate. */
    private static final double LOOK_REACH = 8.0;
    /** One look in this many chances (every 2 s) when it may: a clamped nestmate is found within a minute or so. */
    private static final int ONE_IN = 3;
    /** It gives up walking to its nestmate after this long (ticks). */
    private static final int WALK_TICKS = 400;

    private final AbstractAntEntity ant;
    @Nullable
    private AbstractAntEntity patient;
    private int walking;
    private int working;
    private int replan;

    public ClampCareGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if ((ant.tickCount + ant.getId()) % 40 != 0 || !(ant.level() instanceof ServerLevel level)
                || ant.getRandom().nextInt(ONE_IN) != 0 || !free()) {
            return false;
        }
        Colony colony = ant.getColony();
        if (colony == null || !colony.nest().isInside(level, ant.blockPosition())) {
            return false;
        }
        patient = level.getEntitiesOfClass(AbstractAntEntity.class, ant.getBoundingBox().inflate(LOOK_REACH),
                        other -> other != ant && other.isAlive() && colony.getId().equals(other.getColonyId())
                                && other.clamp().isOn() && other.careTaker(AbstractAntEntity.CareJob.CLAMP) == null && other.restsForCare(level, colony))
                .stream().min(Comparator.comparingDouble(other -> other.distanceToSqr(ant))).orElse(null);
        return patient != null && patient.claimCare(AbstractAntEntity.CareJob.CLAMP, ant.getUUID(), level.getGameTime() + AbstractAntEntity.CARE_CLAIM_TICKS);
    }

    /** Whether it may tend a nestmate: not fighting, holding nothing, nothing clamped on itself, not downed. */
    private boolean free() {
        return ant.isAlive() && !ant.isDowned() && !ant.fightsNoMore() && !ant.clamp().isOn() && ant.clampingOn() == null
                && ant.getFightSpot() == null && ant.getLoad().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && patient.isAlive() && patient.clamp().isOn() && free() && walking < WALK_TICKS
                && ant.getUUID().equals(patient.careTaker(AbstractAntEntity.CareJob.CLAMP));
    }

    @Override
    public void start() {
        walking = 0;
        working = 0;
        replan = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (patient != null) {
            patient.releaseCare(AbstractAntEntity.CareJob.CLAMP, ant.getUUID());
        }
        patient = null;
    }

    @Override
    public void tick() {
        Colony colony = ant.getColony();
        if (!(ant.level() instanceof ServerLevel level) || patient == null || colony == null) {
            return;
        }
        patient.claimCare(AbstractAntEntity.CareJob.CLAMP, ant.getUUID(), level.getGameTime() + AbstractAntEntity.CARE_CLAIM_TICKS);
        double reach = (ant.getBbWidth() + patient.getBbWidth()) * 0.5 + 0.4;
        if (ant.distanceToSqr(patient) > reach * reach) {
            walking++;
            working = 0;
            if (--replan <= 0 || ant.getNavigator().isDone()) {
                replan = 20;
                ant.getNavigator().moveTo(patient.blockPosition(), 1.0);
            }
            return;
        }
        ant.getNavigator().stop();
        if (working == 0 && !patient.restsForCare(level, colony)) {
            walking++; // (at its side, its nestmate still walking on in the rooms: it waits, and gives up in time)
            face(patient.getX(), patient.getZ());
            return;
        }
        // At its side: it holds the nestmate still and bites at the clamp, its antennae on it.
        patient.clamp().holdStillFor(ant.getUUID(), level.getGameTime() + 40);
        face(patient.getX(), patient.getZ());
        ant.getLookControl().setLookAt(patient);
        int careTicks = FightNumbers.of(patient.profile()).flatMap(FightNumbers::clamped)
                .map(FightNumbers.Clamped::careTicks).orElse(600);
        if (++working >= careTicks) {
            working = 0;
            if (patient.clamp().takeOff(level)) {
                patient.releaseCare(AbstractAntEntity.CareJob.CLAMP, ant.getUUID());
                patient = null; // done: nothing left on the leg
            }
        }
    }

    private void face(double x, double z) {
        double dx = x - ant.getX();
        double dz = z - ant.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        float yaw = Mth.approachDegrees(ant.getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, 20.0F);
        ant.setYRot(yaw);
        ant.setYBodyRot(yaw);
        ant.setYHeadRot(yaw);
    }
}
