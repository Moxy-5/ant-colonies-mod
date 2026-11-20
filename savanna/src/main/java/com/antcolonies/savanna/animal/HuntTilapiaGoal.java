package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A hungry catfish hunts a tilapia (M9 river life, 2026-11-14, my picks): it picks the nearest wild tilapia it can see in
 * the water near it, glides after it, puts on a burst of speed for the last few blocks, then lunges with its mouth
 * thrown open ({@link CatfishEntity#strike}) and catches the fish if it is still in front of its mouth when the
 * mouth snaps: it is held in the open mouth, shaken and gulped down ({@link CatfishEntity#hold}); the school round it scatters. Then it is fed for 4 to 8 minutes. A chase that takes longer than
 * 30 s is given up for a minute. A tilapia from a bucket or with a name is never hunted (the player's own fish).
 *
 * <p>Balance (the rule that food chains must not crash): a few catfish round a player eat one tilapia each every 4 to 8
 * minutes, while the river fish group of 60 refills from natural spawning; a catfish with nothing to hunt just swims on
 * and never starves.
 */
public class HuntTilapiaGoal extends Goal {
    /** How far it looks for a tilapia (blocks across and up or down). */
    static final double LOOK_ACROSS = 14, LOOK_UP_DOWN = 5;
    /** Gliding speed, and the burst for the last {@link #DASH_FROM} blocks (FishMoveControl's speed factors). */
    static final double STALK_SPEED = 1.0, DASH_SPEED = 2.2, DASH_FROM = 4.0;
    /** It lunges once the tilapia is this near its mouth (blocks). */
    static final double LUNGE_FROM = 1.3;
    /** The lunge's push toward the prey (blocks a tick, added once). */
    static final double LUNGE_PUSH = 0.35;
    /** The tick of the strike the mouth snaps shut on the prey (the strike clip's mouth is widest at 0.2 s). */
    static final int SNAP_AT = 5;
    /** The prey is swallowed if it is still this near the mouth when it snaps shut (blocks). */
    static final double SWALLOW_WITHIN = 1.6;
    /** How long a chase may last before it gives up (ticks), and how long it then waits before hunting again. */
    static final int GIVE_UP_TICKS = 600, REST_AFTER_GIVING_UP = 1200;
    /** How long a meal lasts (ticks; 4 to 8 minutes). */
    static final int FED_MIN = 4800, FED_MORE = 4800;
    /** How far the school round a strike scatters from (blocks), and how hard. */
    static final double SCATTER_WITHIN = 6, SCATTER_PUSH = 0.4;

    private final CatfishEntity fish;
    @Nullable
    private TilapiaEntity prey;
    private int ticks, strikeTick = -1;

    public HuntTilapiaGoal(CatfishEntity fish) {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // the young never hunt (they hide in the shallows); a female scattering her eggs or courting does not either
        if (!fish.isHungry() || !fish.isInWater() || fish.life().isYoung() || fish.eggsToScatter() > 0
                || fish.life().partner() != null || fish.getRandom().nextInt(20) != 0) {
            return false;
        }
        prey = findPrey();
        if (prey == null) {
            fish.hungryIn = 200;    // nothing near: look again in 10 s, swimming on meanwhile
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return prey != null && ticks < GIVE_UP_TICKS && fish.isInWater() && huntable(prey);
    }

    @Override
    public void start() {
        ticks = 0;
        strikeTick = -1;
    }

    @Override
    public void stop() {
        if (prey != null && fish.hungryIn <= 0) {
            fish.hungryIn = REST_AFTER_GIVING_UP;     // gave up or lost it: rest a minute
        }
        prey = null;
        fish.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        TilapiaEntity target = prey;
        if (target == null) {
            return;
        }
        fish.getLookControl().setLookAt(target, 30, 30);
        double near = fish.mouth().distanceTo(target.position().add(0, target.getBbHeight() / 2, 0));
        if (strikeTick >= 0) {
            if (++strikeTick == SNAP_AT) {
                if (near <= SWALLOW_WITHIN) {
                    swallow(target);
                } else {
                    strikeTick = -1;    // missed: chase on
                }
            }
            return;
        }
        if (near <= LUNGE_FROM) {
            lunge(target);
            return;
        }
        if (ticks % 10 == 1 || fish.getNavigation().isDone()) {
            fish.getNavigation().moveTo(target, near < DASH_FROM ? DASH_SPEED : STALK_SPEED);
        }
    }

    private void lunge(TilapiaEntity target) {
        strikeTick = 0;
        fish.strike();
        fish.getNavigation().stop();
        Vec3 push = target.position().subtract(fish.position()).normalize().scale(LUNGE_PUSH);
        fish.setDeltaMovement(fish.getDeltaMovement().add(push));
        scatter();
    }

    private void scatter() {
        scatter(fish, prey);
    }

    /** The school round a strike darts away from the hunter (the catfish's, and the crocodile's), all but the fish struck at. */
    static void scatter(net.minecraft.world.entity.Entity hunter, @Nullable net.minecraft.world.entity.Entity struckAt) {
        Vec3 at = hunter.position();
        List<TilapiaEntity> school = hunter.level().getEntitiesOfClass(TilapiaEntity.class, hunter.getBoundingBox().inflate(SCATTER_WITHIN));
        for (TilapiaEntity other : school) {
            if (other == struckAt) {
                continue;
            }
            Vec3 away = other.position().subtract(at);
            if (away.lengthSqr() < 1.0E-4) {
                continue;
            }
            other.setDeltaMovement(other.getDeltaMovement().add(away.normalize().scale(SCATTER_PUSH)));
        }
    }

    private void swallow(TilapiaEntity target) {
        if (fish.level() instanceof ServerLevel level) {
            Vec3 m = fish.mouth();
            level.sendParticles(ParticleTypes.BUBBLE, m.x, m.y, m.z, 12, 0.3, 0.2, 0.3, 0.05);
        }
        fish.hold(target);      // shown in its mouth, shaken and gulped down; the gulp is heard then
        target.discard();
        fish.hungryIn = FED_MIN + fish.getRandom().nextInt(FED_MORE);
        prey = null;
    }

    @Nullable
    private TilapiaEntity findPrey() {
        AABB around = fish.getBoundingBox().inflate(LOOK_ACROSS, LOOK_UP_DOWN, LOOK_ACROSS);
        TilapiaEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (TilapiaEntity t : fish.level().getEntitiesOfClass(TilapiaEntity.class, around, this::huntable)) {
            double d = t.distanceToSqr(fish);
            if (d < bestDist && fish.hasLineOfSight(t)) {
                best = t;
                bestDist = d;
            }
        }
        return best;
    }

    private boolean huntable(TilapiaEntity t) {
        return t.isAlive() && t.isInWater() && !t.fromBucket() && !t.hasCustomName()
                && t.distanceToSqr(fish) < (LOOK_ACROSS + 4) * (LOOK_ACROSS + 4);
    }
}
