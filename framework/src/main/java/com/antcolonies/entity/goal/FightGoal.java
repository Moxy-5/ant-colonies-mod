package com.antcolonies.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.fight.FightNumbers;
import com.antcolonies.fight.Fights;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * The shared fight (M8 step 2b, {@code docs/plans/m8-matabele.md}): an insect sent to fight round a spot (the skirmish
 * command; step 4's raids later) goes there and attacks the foes it may attack within {@link #SPOT_REACH} of it (by the
 * shared rules, {@link Fights}), nearest first; one that was hurt fights back at whoever hurt it, if it may attack
 * that. At a foe it can reach it strikes again and again ({@link AbstractAntEntity#strike}: on prey, the paralysing
 * sting); one it cannot reach (in a closed gallery, under mud) it may open the way to
 * ({@link AbstractAntEntity#openWayTo}: a Matabele major bites the mud open), else it leaves it for a while. With no foe
 * left round the spot for {@link #DONE_TICKS} the order ends and it goes back to its usual work (home, for one with a
 * colony). The legs walk it ({@code AntNavigator}); this goal only says where.
 *
 * <p>M8 fight polish, round A: one foe at a time, each blow finished. It strikes only standing on a floor
 * ({@link AbstractAntEntity#standsToFight}: never from a wall or a ceiling) and facing the foe; the blow's clip plays to
 * its end ({@link AbstractAntEntity#isInABlow}) while it holds still, and a foe it has felled it stands over a moment,
 * feeling it with its antennae (the species file's {@code fight.look_over_ticks}), before it turns to the next. A foe
 * another hunter holds in its jaws is left to that one.
 */
public class FightGoal extends Goal {
    /** How far (blocks) round the spot it looks for foes. */
    public static final double SPOT_REACH = 12.0;
    /** How far (blocks) it follows one that hurt it. */
    private static final double FIGHT_BACK_REACH = 8.0;
    /** How far off (degrees) its heading may be from the foe for it to strike: it turns to face it first. */
    private static final float FACING = 30.0F;
    /** How often (ticks) it plans a new way to a moving foe. */
    private static final int CHASE_REPLAN = 10;
    /** A foe it found no way to is left alone this long (ticks). */
    private static final int UNREACHABLE_TICKS = 200;
    /** With no foe round the spot this long (ticks), the fight is over. */
    private static final int DONE_TICKS = 600;
    /** How fast (degrees a tick) it turns to face its foe. */
    private static final float TURN = 20.0F;

    private final AbstractAntEntity ant;
    @Nullable
    private LivingEntity foe;
    @Nullable
    private BlockPos walkingTo;
    /** The foe of its last blow (to stand over it once felled), and how long it still stands over one. */
    @Nullable
    private LivingEntity struck;
    private int lookingOver;
    private int replan;
    private int idleTicks;
    /** Foes it found no way to, and until when it leaves each alone. */
    private final Map<UUID, Long> unreachable = new HashMap<>();

    public FightGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if ((ant.tickCount + ant.getId()) % 5 != 0 || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        foe = pickFoe(level);
        return foe != null || ant.getFightSpot() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return foe != null && foe.isAlive() || ant.getFightSpot() != null || ant.isInABlow() || lookingOver > 0;
    }

    @Override
    public void start() {
        walkingTo = null;
        replan = 0;
        idleTicks = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        foe = null;
        struck = null;
        lookingOver = 0;
        walkingTo = null;
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level)) {
            return;
        }
        // A blow under way plays to its end; it holds still, facing its foe.
        if (ant.isInABlow()) {
            ant.getNavigator().stop();
            LivingEntity target = ant.blowTarget();
            if (target != null) {
                face(target.getX(), target.getZ());
            }
            return;
        }
        // After the blow: a foe it felled it stands over a moment, its antennae on it, before it turns to the next.
        if (struck != null) {
            if (struck instanceof AbstractAntEntity prey && prey.isDowned()) {
                lookingOver = FightNumbers.of(ant.profile()).map(numbers -> numbers.lookOverTicks(ant.getRandom())).orElse(0);
            }
            if (lookingOver > 0) {
                ant.getLookControl().setLookAt(struck);
            }
            struck = null;
        }
        if (lookingOver > 0) {
            lookingOver--;
            ant.getNavigator().stop();
            return;
        }
        if (foe == null || !Fights.mayAttack(ant.profile(), foe) || (ant.tickCount + ant.getId()) % 20 == 0) {
            foe = pickFoe(level);
        }
        if (foe != null) {
            idleTicks = 0;
            fight(level, foe);
            return;
        }
        BlockPos spot = ant.getFightSpot();
        if (spot == null) {
            return;
        }
        // No foe in reach: to the spot, and once there, wait a while for more before the fight is over.
        if (ant.blockPosition().distSqr(spot) > 4.0) {
            if (ant.getNavigator().isDone() || !spot.equals(walkingTo)) {
                walkingTo = spot;
                ant.getNavigator().moveTo(spot, 1.2);
            }
        }
        if (++idleTicks >= DONE_TICKS) {
            ant.endFightOrder();
        }
    }

    /** At the foe: strikes it now and then; otherwise goes at it, or opens the way to it, or leaves it for a while. */
    private void fight(ServerLevel level, LivingEntity target) {
        // Only from a floor (the user's rule): reaching it from a wall or a ceiling, it goes on to the floor first.
        if (ant.reachesInFight(target) && ant.standsToFight()) {
            ant.getNavigator().stop();
            walkingTo = null;
            if (face(target.getX(), target.getZ()) && ant.strike(target)) {
                struck = target;
            }
            return;
        }
        BlockPos at = target.blockPosition();
        if (--replan <= 0 || ant.getNavigator().isDone() || walkingTo == null || walkingTo.distSqr(at) > 2) {
            replan = CHASE_REPLAN;
            walkingTo = at;
            if (!ant.getNavigator().moveTo(at, 1.2) && !ant.openWayTo(level, target)) {
                unreachable.put(target.getUUID(), level.getGameTime() + UNREACHABLE_TICKS);
                foe = null;
            }
        }
    }

    /**
     * The foe to fight: the one that hurt it (if it may attack it and it is near), else the nearest it may attack round
     * its fight spot. Null for none.
     */
    @Nullable
    private LivingEntity pickFoe(ServerLevel level) {
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until <= now);
        LivingEntity hurtBy = ant.getLastHurtByMob();
        if (hurtBy != null && hurtBy.distanceToSqr(ant) <= FIGHT_BACK_REACH * FIGHT_BACK_REACH && usable(hurtBy)) {
            return hurtBy;
        }
        BlockPos spot = ant.getFightSpot();
        if (spot == null) {
            return null;
        }
        AABB area = new AABB(spot).inflate(SPOT_REACH);
        return level.getEntitiesOfClass(LivingEntity.class, area, this::usable).stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(ant))).orElse(null);
    }

    private boolean usable(LivingEntity candidate) {
        return candidate != ant && !unreachable.containsKey(candidate.getUUID()) && Fights.mayAttack(ant.profile(), candidate)
                && !(candidate instanceof AbstractAntEntity insect && insect.isPinned() && candidate != ant.blowTarget());
    }

    /** Turns toward the point; true once it faces it (within {@link #FACING} degrees). */
    private boolean face(double x, double z) {
        double dx = x - ant.getX();
        double dz = z - ant.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return true;
        }
        float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(ant.getYRot(), target, TURN);
        ant.setYRot(yaw);
        ant.setYBodyRot(yaw);
        ant.setYHeadRot(yaw);
        return Math.abs(Mth.wrapDegrees(target - yaw)) <= FACING;
    }

}
