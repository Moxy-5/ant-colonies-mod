package com.antcolonies.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.Wounds;
import com.antcolonies.fight.FightNumbers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Wound care in the nest (M8 step 5, {@code docs/DESIGN.md} "Step 5, rescue and wound care"). Real (Frank et al. 2018;
 * Frank et al. 2023): nestmates lick a hurt ant's fresh wound, and treat an infected one, which they tell by its changed
 * smell, with the antimicrobial secretion of their metapleural gland, the infected first. An insect in its nest with
 * nothing else to do now and then looks round {@link #LOOK_REACH} blocks for a nestmate in the nest whose wound wants
 * care ({@link Wounds#wantsCare}), an infected one first, walks to it, holds it still and licks the wound
 * ({@code rescue.lick_ticks}, the cleaning clip) or dabs gland secretion on it ({@code rescue.dab_ticks}, the
 * {@code gland_dab} clip). The legs walk it; this goal only says where.
 */
public class WoundCareGoal extends Goal {
    private static final double LOOK_REACH = 8.0;
    /** One look in this many chances (every 2 s) when it may. */
    private static final int ONE_IN = 2;
    private static final int WALK_TICKS = 400;

    private final AbstractAntEntity ant;
    @Nullable
    private AbstractAntEntity patient;
    private boolean dabbing;
    private int walking;
    private int working;
    private int replan;

    public WoundCareGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if ((ant.tickCount + ant.getId()) % 40 != 0 || !(ant.level() instanceof ServerLevel level)
                || ant.getRandom().nextInt(ONE_IN) != 0 || !RescueGoal.mayHelp(ant) || ant.wounds().wantsCare()) {
            return false;
        }
        Colony colony = ant.getColony();
        if (colony == null || Wounds.numbers(ant) == null || !colony.nest().isInside(level, ant.blockPosition())) {
            return false;
        }
        // The infected first (they smell of it), then the nearest.
        patient = level.getEntitiesOfClass(AbstractAntEntity.class, ant.getBoundingBox().inflate(LOOK_REACH),
                        other -> other != ant && other.isAlive() && colony.getId().equals(other.getColonyId())
                                && other.wounds().wantsCare() && other.tender() == null && other.careTaker(AbstractAntEntity.CareJob.WOUND) == null
                                && other.restsForCare(level, colony))
                .stream().min(Comparator.<AbstractAntEntity>comparingInt(other -> other.wounds().wantsDab() ? 0 : 1)
                        .thenComparingDouble(other -> other.distanceToSqr(ant))).orElse(null);
        // BUGS 349: one carer at a time, claimed when picked; only one come to rest in the rooms (AbstractAntEntity#restsForCare)
        return patient != null && patient.claimCare(AbstractAntEntity.CareJob.WOUND, ant.getUUID(), level.getGameTime() + AbstractAntEntity.CARE_CLAIM_TICKS);
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && patient.isAlive() && !patient.isPassenger() && RescueGoal.mayHelp(ant) && walking < WALK_TICKS
                && (dabbing ? patient.wounds().wantsDab() : patient.wounds().wantsLicking())
                && ant.getUUID().equals(patient.careTaker(AbstractAntEntity.CareJob.WOUND));
    }

    @Override
    public void start() {
        dabbing = patient != null && patient.wounds().wantsDab();
        walking = 0;
        working = 0;
        replan = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        ant.setActivity(AbstractAntEntity.ACTIVITY_NONE);
        if (patient != null) {
            patient.releaseCare(AbstractAntEntity.CareJob.WOUND, ant.getUUID());
        }
        patient = null;
    }

    @Override
    public void tick() {
        Colony colony = ant.getColony();
        if (!(ant.level() instanceof ServerLevel level) || patient == null || colony == null) {
            return;
        }
        patient.claimCare(AbstractAntEntity.CareJob.WOUND, ant.getUUID(), level.getGameTime() + AbstractAntEntity.CARE_CLAIM_TICKS);
        double reach = (ant.getBbWidth() + patient.getBbWidth()) * 0.5 + 0.2;
        if (ant.distanceToSqr(patient) > reach * reach) {
            walking++;
            working = 0;
            ant.setActivity(AbstractAntEntity.ACTIVITY_NONE);
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
        ant.setActivity(dabbing ? AbstractAntEntity.ACTIVITY_GLAND_DAB : AbstractAntEntity.ACTIVITY_ALLOGROOM);
        patient.tendedBy(ant.getUUID(), level.getGameTime() + 20);
        face(patient.getX(), patient.getZ());
        ant.getLookControl().setLookAt(patient);
        FightNumbers.Rescue n = Wounds.numbers(patient);
        if (n == null) {
            patient = null;
            return;
        }
        if (++working >= (dabbing ? n.dabTicks() : n.lickTicks())) {
            if (dabbing) {
                patient.wounds().dab();
            } else {
                patient.wounds().licked();
            }
            patient.releaseCare(AbstractAntEntity.CareJob.WOUND, ant.getUUID());
            patient = null; // one lick or one dab a time; another may come next
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
