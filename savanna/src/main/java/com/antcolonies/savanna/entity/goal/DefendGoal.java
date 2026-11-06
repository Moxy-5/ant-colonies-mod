package com.antcolonies.savanna.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.colony.Alarm;
import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.goal.Errand;
import com.antcolonies.fight.Fights;
import com.antcolonies.savanna.colony.RaidDefence;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A soldier answers the alarm (see {@link Alarm#taskFor}): it goes at an enemy (and bites it, if it is one it may bite:
 * for now only chickens), or it walks to a spot near the trouble (the breach, the nest cell nearest the footsteps) and
 * stands there facing it, drumming once on arrival at footsteps. When the alarm has nothing more for it, it goes back to
 * its post.
 *
 * <p>M8 step 3S, S1 (the user's raid test of 2026-11-05: "the majors ignore them back"): standing at its spot it is no
 * statue. Real Macrotermes soldiers are blind and bite whatever touches them: it snaps at a foe its jaws reach and lunges
 * at one it feels within {@link #LUNGE} blocks, never one farther than {@link Alarm#STAND_LEASH} from the place it
 * guards, then goes back to its spot.
 */
public class DefendGoal extends Goal {
    /** Ticks between two bites. */
    private static final int BITE_INTERVAL = 20;
    /** How often (ticks) it plans a new way to a moving enemy. */
    private static final int CHASE_REPLAN = 10;
    /** How fast (degrees a tick) it turns to face the trouble. */
    private static final float TURN = 15.0F;
    /**
     * An enemy or a spot it found no way to is left alone this long (ticks, 10 s): a blind soldier that has lost touch
     * with a raider goes back to its post instead of searching the whole gallery web for it again and again (the lag
     * spike of 2026-11-05: ten minors chasing Matabeles they could not reach ran 64 failed 6,000-step searches a second).
     */
    private static final int NO_WAY_TICKS = 200;
    /** S1: a standing soldier lunges at a foe it feels this near (blocks; my pick). */
    private static final double LUNGE = 2.0;

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
    /** Enemies (by id) and spots it found no way to, left alone for {@link #NO_WAY_TICKS}; kept from one alarm task to the next. */
    private final Errand<Object> noWay;
    /** The spot it drummed at on arrival (kept while drumming stops this goal, so it drums there only once). */
    @Nullable
    private BlockPos drummedAt;
    /** S1: the foe it lunges at from its spot (looked for every few ticks), or null. */
    @Nullable
    private LivingEntity lungingAt;

    public DefendGoal(TermiteSoldierEntity soldier) {
        this.soldier = soldier;
        this.noWay = new Errand<>(soldier, NO_WAY_TICKS);
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
        task = alarm == null ? null : taskNow(level);
        return task != null && !leftAlone(task);
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && task != null;
    }

    @Override
    public void start() {
        walkingTo = null;
        replan = 0;
        lungingAt = null;
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
        Alarm.Task now = taskNow(level);
        if (now == null || leftAlone(now)) {
            task = null;
            return;
        }
        task = now;
        if (biteCooldown > 0) {
            biteCooldown--;
        }
        if (task.enemy() != null) {
            chase(task.enemy(), true);
        } else if (task.cell() != null) {
            if (lungingAt == null || !lungingAt.isAlive() || (soldier.tickCount + soldier.getId()) % 4 == 0) {
                lungingAt = foeNear(level, task);
            }
            if (lungingAt != null) {
                chase(lungingAt, false);
            } else {
                stand(task.cell(), task.spot(), task.facing(), task.drumOnArrival());
            }
        }
    }

    /** Whether the task's enemy or spot is one it found no way to a little while ago. */
    private boolean leftAlone(Alarm.Task task) {
        return task.enemy() != null ? noWay.isLeftAlone(task.enemy().getUUID())
                : task.cell() != null && noWay.isLeftAlone(task.cell());
    }

    /**
     * The alarm's task for it; with none, a soldier shut out by a seal (M8 step 2f, {@link RaidDefence}) waits by the
     * seal. A soldier inside is not sent at a breach past a seal (it cannot get there).
     */
    @Nullable
    private Alarm.Task taskNow(ServerLevel level) {
        RaidDefence raid = colony.part(SavannaParts.RAID_DEFENCE).orElse(null);
        Alarm.Task now = alarm.taskFor(level, soldier);
        if (now != null && raid != null && now.enemy() == null && now.cell() != null && raid.cutOff(soldier, now.cell())) {
            now = null;
        }
        if (now == null && raid != null) {
            now = raid.waitTaskFor(level, soldier);
        }
        return now;
    }

    /**
     * S1: the nearest foe it may bite within {@link #LUNGE} blocks of it and {@link Alarm#STAND_LEASH} of the place it
     * guards, or null.
     */
    @Nullable
    private LivingEntity foeNear(ServerLevel level, Alarm.Task task) {
        BlockPos guard = task.guard() != null ? task.guard() : task.cell();
        Vec3 guarded = Vec3.atBottomCenterOf(guard);
        double leash = Alarm.STAND_LEASH * Alarm.STAND_LEASH;
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, soldier.getBoundingBox().inflate(LUNGE), LivingEntity::isAlive)) {
            if (foe == soldier || foe.position().distanceToSqr(guarded) > leash || noWay.isLeftAlone(foe.getUUID())
                    || foe instanceof AbstractAntEntity insect && insect.isDowned()
                    || !alarm.isEnemy(foe) || !Fights.mayAttack(soldier.profile(), foe)) {
                continue;
            }
            double distance = foe.distanceToSqr(soldier);
            if (distance < bestDistance) {
                best = foe;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Goes at the enemy; once it touches it, it holds still facing it and bites it now and then (if it may). With no way
     * to it, it leaves it alone a while; {@code dropTask}: and gives up its task (a chase), else it goes back to its spot
     * (a lunge from its spot, S1).
     */
    private void chase(LivingEntity enemy, boolean dropTask) {
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
        // Real sizes, step 2: beside it, its walk over, the last little bit straight at it (see FightGoal).
        if (soldier.closeIn(enemy.position(), CLOSE_IN_NEAR) == AbstractAntEntity.CloseIn.STEPPING) {
            walkingTo = enemy.blockPosition();
            return;
        }
        BlockPos at = enemy.blockPosition();
        if (--replan <= 0 || soldier.getNavigator().isDone() || walkingTo == null || walkingTo.distSqr(at) > 2) {
            replan = CHASE_REPLAN;
            walkingTo = at;
            // No way there (behind a seal, up a wall, on a runway's top): the soldier lets it be for a while.
            if (!soldier.getNavigator().moveTo(at, 1.2)) {
                noWay.leaveAlone(enemy.getUUID());
                if (dropTask) {
                    task = null;
                } else {
                    lungingAt = null;
                    walkingTo = null;
                }
            }
        }
    }

    /**
     * Whether the soldier's jaws reach the enemy (their boxes nearly touch). Real sizes, step 2: a third of its body's
     * length past its box ({@link AbstractAntEntity#touches}; was a fixed 0.3 blocks).
     */
    private boolean reaches(LivingEntity enemy) {
        return soldier.touches(enemy);
    }

    /** Real sizes, step 3: its spot in its cell round a breach, this near sideways (blocks), it steps onto. */
    private static final double SPOT_NEAR = 1.5;

    /** Real sizes, step 2: an enemy this near sideways (blocks), its walk over, it steps straight at. */
    private static final double CLOSE_IN_NEAR = 1.2;

    /**
     * Walks to the cell and stands in it facing the trouble (drumming once on arrival, if asked); with a {@code spot}
     * in the cell (real sizes, step 3: its place round a raid's breach, several to a cell), it steps onto that spot
     * first, so the soldiers pack the opening shoulder to shoulder instead of one inside another.
     */
    private void stand(BlockPos cell, @Nullable Vec3 spot, BlockPos facing, boolean drumOnArrival) {
        if (soldier.blockPosition().equals(cell) && !soldier.isClimbing()) {
            soldier.getNavigator().stop();
            if (spot != null && soldier.closeIn(spot, SPOT_NEAR) == AbstractAntEntity.CloseIn.STEPPING) {
                return;
            }
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
                noWay.leaveAlone(cell);
                alarm.noWayTo(soldier, cell); // (S2: a place round a raid's breach it cannot reach is given to nobody for a while)
                task = null;
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
