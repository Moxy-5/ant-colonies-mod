package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Tilapia fry stay round their mother's head (M9, animal life cycle step 2, 2026-11-19; the plan's picks): for their
 * first days ({@code care_days} in the tilapia's life file) a fry with a mother swims in a little cloud just in front
 * of her face, each at its own place round it, and follows her wherever she goes. When danger comes she takes them back
 * into her mouth ({@link TilapiaEntity#tick}); after those days they school on their own as any young tilapia. A fry
 * whose mother is gone or far for 10 seconds is on its own.
 */
public class TilapiaFryGoal extends Goal {
    /** How far from its mother a fry still finds her (blocks). */
    static final double FIND_WITHIN = 24;
    /** How long a fry waits for a mother it cannot find before it is on its own (ticks). */
    static final int LOST_AFTER = 200;

    private final TilapiaEntity fry;
    @Nullable
    private TilapiaEntity mother;
    private int lost;

    public TilapiaFryGoal(TilapiaEntity fry) {
        this.fry = fry;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!fry.withMother()) {
            return false;
        }
        mother = findMother();
        if (mother == null) {
            if (++lost > LOST_AFTER) {
                fry.loseMother();
            }
            return false;
        }
        lost = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return fry.withMother() && mother != null && mother.isAlive() && !mother.isRemoved()
                && fry.distanceToSqr(mother) < FIND_WITHIN * FIND_WITHIN;
    }

    @Override
    public void stop() {
        mother = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (mother == null) {
            return;
        }
        Vec3 spot = placeByHer(mother, fry.getId(), fry.tickCount);
        double d = fry.position().distanceToSqr(spot);
        if (d > 0.15 * 0.15 && (fry.getNavigation().isDone() || fry.tickCount % 8 == 0)) {
            fry.getNavigation().moveTo(spot.x, spot.y, spot.z, d > 4 ? 1.6 : 1.0);
        }
    }

    /** A fry's own place in the cloud in front of its mother's face, drifting slowly round. */
    static Vec3 placeByHer(TilapiaEntity mother, int id, int ticks) {
        Vec3 face = mother.facePoint();
        double a = id * 2.4 + ticks * 0.02;
        double r = 0.25 + (id % 3) * 0.08;
        return face.add(Math.cos(a) * r, Math.sin(a * 0.7) * 0.12, Math.sin(a) * r);
    }

    @Nullable
    private TilapiaEntity findMother() {
        if (!(fry.level() instanceof ServerLevel server) || fry.motherId() == null) {
            return null;
        }
        Entity e = server.getEntity(fry.motherId());
        return e instanceof TilapiaEntity m && m.isAlive() && fry.distanceToSqr(m) < FIND_WITHIN * FIND_WITHIN ? m : null;
    }
}
