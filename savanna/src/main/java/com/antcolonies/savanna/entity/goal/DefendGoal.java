package com.antcolonies.savanna.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.colony.Alarm;
import com.antcolonies.colony.Colony;
import com.antcolonies.fight.Fights;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A soldier answers the alarm (see {@link Alarm#taskFor}): it goes at an enemy (and bites it, if it is one it may bite:
 * for now only chickens), or it walks to a spot near the trouble (the breach, the nest cell nearest the footsteps) and
 * stands there facing it, drumming once on arrival at footsteps. When the alarm has nothing more for it, it goes back to
 * its post.
 */
public class DefendGoal extends Goal {
    /** Ticks between two bites. */
    private static final int BITE_INTERVAL = 20;
    /** How often (ticks) it plans a new way to a moving enemy. */
    private static final int CHASE_REPLAN = 10;
    /** How fast (degrees a tick) it turns to face the trouble. */
    private static final float TURN = 15.0F;

    private final TermiteSoldierEntity soldier;
    @Nullable
    private Colony colony;
    /** Its colony's alarm (asked once in {@link #canUse}: only a species that lists the alarm has one). */
    @Nullable
    private Alarm alarm;
    @Nullable
    private Alarm.Task task;
    @Nullable
    private BlockPos walkingTo;
    private int biteCooldown;
    private int replan;
    private int failures;
    /** The spot it drummed at on arrival (kept while drumming stops this goal, so it drums there only once). */
    @Nullable
    private BlockPos drummedAt;

    public DefendGoal(TermiteSoldierEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (soldier.getColonyId() == null || soldier.isDrumming() || (soldier.tickCount + soldier.getId()) % 5 != 0
                || !(soldier.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = soldier.getColony();
        alarm = colony == null ? null : colony.part(SavannaParts.ALARM).orElse(null);
        task = alarm == null ? null : alarm.taskFor(level, soldier);
        return task != null;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && task != null && failures < 5;
    }

    @Override
    public void start() {
        walkingTo = null;
        replan = 0;
        failures = 0;
    }

    @Override
    public void stop() {
        soldier.getNavigator().stop();
        colony = null;
        alarm = null;
        task = null;
    }

    @Override
    public void tick() {
        if (colony == null || alarm == null || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        Alarm.Task now = alarm.taskFor(level, soldier);
        if (now == null) {
            task = null;
            return;
        }
        task = now;
        if (biteCooldown > 0) {
            biteCooldown--;
        }
        if (task.enemy() != null) {
            chase(task.enemy());
        } else if (task.cell() != null) {
            stand(task.cell(), task.facing(), task.drumOnArrival());
        }
    }

    /** Goes at the enemy; once it touches it, it holds still facing it and bites it now and then (if it may). */
    private void chase(LivingEntity enemy) {
        if (reaches(enemy)) {
            soldier.getNavigator().stop();
            walkingTo = null;
            face(enemy.getX(), enemy.getZ());
            if (biteCooldown <= 0 && Fights.mayAttack(soldier.profile(), enemy)) {
                soldier.bite(enemy);
                biteCooldown = BITE_INTERVAL;
            }
            return;
        }
        BlockPos at = enemy.blockPosition();
        if (--replan <= 0 || soldier.getNavigator().isDone() || walkingTo == null || walkingTo.distSqr(at) > 2) {
            replan = CHASE_REPLAN;
            walkingTo = at;
            if (!soldier.getNavigator().moveTo(at, 1.2)) {
                failures++;
            } else {
                failures = 0;
            }
        }
    }

    /** Whether the soldier's jaws reach the enemy (their boxes nearly touch). */
    private boolean reaches(LivingEntity enemy) {
        return soldier.getBoundingBox().inflate(0.3, 0.2, 0.3).intersects(enemy.getBoundingBox());
    }

    /** Walks to the cell and stands in it facing the trouble (drumming once on arrival, if asked). */
    private void stand(BlockPos cell, BlockPos facing, boolean drumOnArrival) {
        if (soldier.blockPosition().equals(cell) && !soldier.isClimbing()) {
            soldier.getNavigator().stop();
            face(facing.getX() + 0.5, facing.getZ() + 0.5);
            if (drumOnArrival && !cell.equals(drummedAt)) {
                drummedAt = cell;
                soldier.drum(Alarm.DRUM_ON_HEARING);
            }
            return;
        }
        if (soldier.getNavigator().isDone() || !cell.equals(walkingTo)) {
            walkingTo = cell;
            if (!soldier.getNavigator().moveTo(cell, 1.2)) {
                failures++;
            }
        }
    }

    private void face(double x, double z) {
        double dx = x - soldier.getX();
        double dz = z - soldier.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(soldier.getYRot(), target, TURN);
        soldier.setYRot(yaw);
        soldier.setYBodyRot(yaw);
        soldier.setYHeadRot(yaw);
    }
}
