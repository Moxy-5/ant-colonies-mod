package com.antcolonies.savanna.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.entity.TermiteWorkerEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.pathfinder.Path;

/**
 * A chicken hunts termite workers out on the surface, like real birds eating insects (step 6c, a simple first version;
 * the user's idea, "Chickens peck at workers" in CLAUDE.md): it walks up to the nearest worker it can reach within
 * {@link #SEE} blocks and pecks it ({@link #PECK_DAMAGE} a peck, a worker has 3 health). Once it has eaten one it is full
 * for {@link #FULL_TICKS}. A pecked termite raises its colony's alarm, and the minor soldiers come and bite the chicken
 * (which then panics and runs, as chickens do when hurt). Termites only for now (ants come with M8). My picks.
 */
public class HuntTermitesGoal extends Goal {
    /** How far (blocks) a chicken sees a termite. */
    private static final double SEE = 8.0;
    /** Damage of one peck. */
    private static final float PECK_DAMAGE = 1.0F;
    /** Ticks between two pecks. */
    private static final int PECK_INTERVAL = 20;
    /** How long (ticks) a chicken is full after eating a termite (30 seconds). */
    private static final int FULL_TICKS = 600;
    /** It gives up a chase after this long (ticks). */
    private static final int GIVE_UP_TICKS = 400;

    private final Chicken chicken;
    @Nullable
    private TermiteWorkerEntity prey;
    private long fullUntil;
    private int ticks;
    private int peckCooldown;
    private int replan;

    public HuntTermitesGoal(Chicken chicken) {
        this.chicken = chicken;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (chicken.isBaby() || chicken.getRandom().nextInt(10) != 0 || !(chicken.level() instanceof ServerLevel level)
                || level.getGameTime() < fullUntil) {
            return false;
        }
        prey = level.getEntitiesOfClass(TermiteWorkerEntity.class, chicken.getBoundingBox().inflate(SEE, 4.0, SEE), this::huntable)
                .stream().min(Comparator.comparingDouble(chicken::distanceToSqr)).orElse(null);
        if (prey == null) {
            return false;
        }
        Path path = chicken.getNavigation().createPath(prey, 0);
        if (path == null || !path.canReach()) {
            prey = null;
            return false;
        }
        chicken.getNavigation().moveTo(path, 1.0);
        return true;
    }

    /** A termite worker a chicken can get at: alive, out in the open (not inside a tunnel block), not up a wall. */
    private boolean huntable(TermiteWorkerEntity termite) {
        return termite.isAlive() && !termite.isInChannel() && !termite.isClimbing();
    }

    @Override
    public boolean canContinueToUse() {
        return prey != null && prey.isAlive() && ticks < GIVE_UP_TICKS && huntable(prey) && chicken.distanceToSqr(prey) < (SEE + 4) * (SEE + 4);
    }

    @Override
    public void start() {
        ticks = 0;
        peckCooldown = 10;
        replan = 0;
    }

    @Override
    public void stop() {
        chicken.getNavigation().stop();
        prey = null;
    }

    @Override
    public void tick() {
        if (prey == null || !(chicken.level() instanceof ServerLevel world)) {
            return;
        }
        ticks++;
        chicken.getLookControl().setLookAt(prey, 30.0F, 60.0F);
        double dx = prey.getX() - chicken.getX();
        double dz = prey.getZ() - chicken.getZ();
        double distance = dx * dx + dz * dz;
        boolean level = Math.abs(prey.getY() - chicken.getY()) <= 1.0;
        if (distance > 0.8 * 0.8 || !level) {
            if (distance <= 2.0 * 2.0 && level) {
                // Near: the last steps straight at it (the path ends at the middle of its block, a termite is small).
                chicken.getNavigation().stop();
                chicken.getMoveControl().setWantedPosition(prey.getX(), prey.getY(), prey.getZ(), 1.0);
            } else if (--replan <= 0) {
                replan = 10;
                chicken.getNavigation().moveTo(prey, 1.0);
            }
            return;
        }
        chicken.getNavigation().stop();
        if (--peckCooldown > 0) {
            return;
        }
        peckCooldown = PECK_INTERVAL;
        prey.hurt(chicken.damageSources().mobAttack(chicken), PECK_DAMAGE);
        chicken.playSound(SoundEvents.CHICKEN_STEP, 0.6F, 1.6F);
        if (!prey.isAlive()) {
            fullUntil = world.getGameTime() + FULL_TICKS; // eaten: full for a while
            chicken.playSound(SoundEvents.CHICKEN_AMBIENT, 1.0F, 1.0F);
            prey = null;
        }
    }
}
