package com.antcolonies.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.Wounds;
import com.antcolonies.entity.load.FrameworkLoads;
import com.antcolonies.fight.FightNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Rescue (M8 step 5, {@code docs/DESIGN.md} "Step 5, rescue and wound care"). Real (Frank et al. 2017, "Saving the
 * injured"): a Megaponera hurt in a fight calls for help with a smell from its head; a nestmate near it comes, pulls off
 * the termites clinging to it and carries it home in its jaws; at home it is set down and tended. Two jobs in one goal:
 * <ul>
 * <li>with a hurt nestmate in its jaws ({@link FrameworkLoads#HURT}) and nothing else leading it (a raid column takes its
 * raiders home itself), it walks it to the room the hurt rest in ({@code Nest.getHurtRoomCells}) and sets it down;</li>
 * <li>free, it hears a nestmate of its colony calling within its species' {@code rescue.help_reach}, walks to it, pulls a
 * clamped foe off ({@code field_pull_ticks}, a living one killed first) and, if it has legs gone, takes it up.</li>
 * </ul>
 * The legs walk it; this goal only says where.
 */
public class RescueGoal extends Goal {
    /** It gives up walking to a hurt nestmate after this long (ticks). */
    private static final int WALK_TICKS = 400;
    /** It gives up carrying one to the hurt room after this long (ticks), setting it down where it is. */
    private static final int CARRY_TICKS = 2400;

    private final AbstractAntEntity ant;
    @Nullable
    private AbstractAntEntity patient;
    private int walking;
    private int working;
    private int replan;
    @Nullable
    private BlockPos room;

    public RescueGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Whether this insect may help a hurt nestmate: whole, not fighting, jaws empty, not itself carried or in a raid's care. */
    public static boolean mayHelp(AbstractAntEntity ant) {
        return ant.isAlive() && !ant.isDowned() && !ant.fightsNoMore() && !ant.clamp().isOn() && ant.clampingOn() == null
                && ant.getFightSpot() == null && ant.getLoad().isEmpty() && !ant.isPassenger() && !ant.isVehicle()
                && !ant.needsRescue();
    }

    /** The calling nestmates of its colony within its species' help reach, nearest first. */
    public static List<AbstractAntEntity> callers(ServerLevel level, AbstractAntEntity ant) {
        Colony colony = ant.getColony();
        FightNumbers.Rescue n = Wounds.numbers(ant);
        if (colony == null || n == null) {
            return List.of();
        }
        return level.getEntitiesOfClass(AbstractAntEntity.class, ant.getBoundingBox().inflate(n.helpReach()),
                        other -> other != ant && colony.getId().equals(other.getColonyId()) && other.isCallingForHelp()
                                && other.tender() == null)
                .stream().sorted(Comparator.comparingDouble(other -> other.distanceToSqr(ant))).toList();
    }

    @Override
    public boolean canUse() {
        if (!(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        if (ant.isCarrying(FrameworkLoads.HURT)) {
            return ant.carriedNestmate() != null;
        }
        if ((ant.tickCount + ant.getId()) % 20 != 0 || !mayHelp(ant)) {
            return false;
        }
        List<AbstractAntEntity> calling = callers(level, ant);
        patient = calling.isEmpty() ? null : calling.get(0);
        return patient != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (ant.isCarrying(FrameworkLoads.HURT)) {
            return ant.carriedNestmate() != null && walking < CARRY_TICKS;
        }
        return patient != null && patient.isAlive() && patient.needsRescue() && mayHelp(ant) && walking < WALK_TICKS
                && (patient.tender() == null || patient.tender().equals(ant.getUUID()));
    }

    @Override
    public void start() {
        walking = 0;
        working = 0;
        replan = 0;
        room = null;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (ant.isCarrying(FrameworkLoads.HURT) && walking >= CARRY_TICKS) {
            ant.setDownHurt(); // it could not get it to the room: it sets it down where it is
        }
        patient = null;
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level)) {
            return;
        }
        if (ant.isCarrying(FrameworkLoads.HURT)) {
            carryHome(level);
            return;
        }
        if (patient == null) {
            return;
        }
        double reach = (ant.getBbWidth() + patient.getBbWidth()) * 0.5 + 0.3;
        if (ant.distanceToSqr(patient) > reach * reach) {
            walking++;
            working = 0;
            if (--replan <= 0 || ant.getNavigator().isDone()) {
                replan = 20;
                ant.getNavigator().moveTo(patient.blockPosition(), 1.2); // it hurries to the call
            }
            return;
        }
        ant.getNavigator().stop();
        face(patient.getX(), patient.getZ());
        ant.getLookControl().setLookAt(patient);
        FightNumbers.Rescue n = Wounds.numbers(patient);
        if (patient.clamp().isOn()) {
            // Pulling the clinging foe off: the hurt one holds still for it.
            patient.tendedBy(ant.getUUID(), level.getGameTime() + 40);
            if (++working >= (n != null ? n.fieldPullTicks() : 200)) {
                working = 0;
                patient.clamp().takeOff(level); // a living one is killed first, its head then pulled off
            }
            return;
        }
        if (patient.isCarriable() && ant.pickUpHurt(patient)) {
            walking = 0;
            patient = null;
            return;
        }
        patient.callForHelp(0); // free of the foe and fit to walk: it walks home itself
        patient = null;
    }

    /** Walks the nestmate it carries to the hurt room and sets it down there (anywhere in the nest if it has none). */
    private void carryHome(ServerLevel level) {
        walking++;
        Colony colony = ant.getColony();
        if (colony == null) {
            ant.setDownHurt();
            return;
        }
        if (room == null) {
            List<BlockPos> cells = colony.nest().getHurtRoomCells(level);
            room = cells.isEmpty() ? colony.nest().getEntrance() : cells.get(ant.getRandom().nextInt(cells.size()));
        }
        boolean inRoom = ant.blockPosition().distManhattan(room) <= 1
                || colony.nest().isInside(level, ant.blockPosition()) && ant.getNavigator().isDone() && ant.blockPosition().distManhattan(room) <= 3;
        if (inRoom) {
            ant.getNavigator().stop();
            ant.setDownHurt();
            return;
        }
        if (--replan <= 0 || ant.getNavigator().isDone()) {
            replan = 40;
            ant.getNavigator().moveTo(room, 1.0);
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
