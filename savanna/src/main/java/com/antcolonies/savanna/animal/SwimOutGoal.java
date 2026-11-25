package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.colony.GroundTop;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * In water too deep to stand in, a big land animal swims straight for the nearest dry ground and heaves itself out (made
 * for the Cape buffalo, shared with the lions 2026-11-25, {@code docs/plans/lion.md}). It has no swim route: Minecraft's
 * route search finds none from deep water for a 2-wide body, and floating low its feet are more than a step under a flush
 * bank, so against the bank it pushes itself up.
 */
public final class SwimOutGoal extends Goal {
    /** How far round it looks for dry ground (blocks), and how often anew (ticks). */
    static final int SHORE_REACH = 12, LOOK_EVERY = 20;
    /** How hard it heaves itself up against the bank (blocks a tick, upward). */
    static final double HEAVE = 0.3;
    /**
     * Its paddle toward the shore (blocks a tick, 1 a second; my pick): Minecraft moves a mob in water at about a twentieth of
     * its walk (a lion crossed 4 blocks in a minute, 2026-11-25), so the swim is driven here, not by its move control's pace.
     */
    static final double PADDLE = 0.05;
    private final PathfinderMob mob;
    private final double pace;
    @Nullable
    private Vec3 shore;
    private int lookedAt = -LOOK_EVERY;

    /** Swims out at this pace (its move control's share of its speed). */
    public SwimOutGoal(PathfinderMob mob, double pace) {
        this.mob = mob;
        this.pace = pace;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.isAlive() && mob.isInWater() && !mob.onGround() && mob.getFluidHeight(FluidTags.WATER) > 0.5 * mob.getScale();
    }

    /** Once swimming it keeps on until out (heaved up against the bank it is shallow for a moment; stopping there it slid back, 2026-11-25). */
    @Override
    public boolean canContinueToUse() {
        return mob.isAlive() && mob.isInWater() && !mob.onGround();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        shore = null;
        lookedAt = mob.tickCount - LOOK_EVERY;
    }

    @Override
    public void tick() {
        if (mob.tickCount - lookedAt >= LOOK_EVERY) {
            lookedAt = mob.tickCount;
            shore = nearestShore();
        }
        if (shore == null) {
            return;                                         // nothing dry in reach: it floats (the float goal keeps it up)
        }
        mob.getMoveControl().setWantedPosition(shore.x, shore.y, shore.z, pace);   // turns it to the shore and plays its walk
        double dx = shore.x - mob.getX(), dz = shore.z - mob.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        Vec3 v = mob.getDeltaMovement();
        double along = d < 1.0E-3 ? PADDLE : (v.x * dx + v.z * dz) / d;
        if (d >= 1.0E-3 && along < PADDLE) {
            mob.setDeltaMovement(v.x + dx / d * (PADDLE - along), v.y, v.z + dz / d * (PADDLE - along));
        }
        if (mob.horizontalCollision) {
            Vec3 now = mob.getDeltaMovement();
            mob.setDeltaMovement(now.x, Math.max(now.y, HEAVE), now.z); // against the bank: up, then its step takes it out
        }
    }

    /** The nearest dry ground within {@link #SHORE_REACH}, its standing point; null if none. */
    @Nullable
    private Vec3 nearestShore() {
        Level level = mob.level();
        BlockPos at = mob.blockPosition();
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -SHORE_REACH; dx <= SHORE_REACH; dx++) {
            for (int dz = -SHORE_REACH; dz <= SHORE_REACH; dz++) {
                BlockPos column = at.offset(dx, 0, dz);
                if (!level.isLoaded(column)) {
                    continue;
                }
                BlockPos cell = GroundTop.cell(level, column);
                if (!level.getFluidState(cell).isEmpty() || !level.getFluidState(cell.below()).isEmpty()
                        || cell.getY() > at.getY() + 3) {
                    continue;                               // water, or a cliff it could never climb
                }
                double d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = Vec3.atBottomCenterOf(cell);
                }
            }
        }
        return best;
    }
}
