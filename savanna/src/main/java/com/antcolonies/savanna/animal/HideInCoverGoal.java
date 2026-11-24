package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A scared barb or tilapia hides in the reeds (the food web's step 4, 2026-11-23, {@code docs/plans/river-tie-up.md};
 * my picks, animals fun first): after a strike near it ({@link RiverCover#scare}, from every hunter's strike) or with a
 * player swimming near, it dashes to the nearest cover within 8 blocks ({@link RiverCover#nearest}: among reed stalks or
 * under a hippo grass mat) and keeps still there 5 to 10 s, then comes out and swims on with its school. While it keeps
 * still there a hunter sees it only from close by ({@link RiverCover#hiddenFrom}). With no cover near it flees as before
 * (the scatter, Minecraft's own swim away from players).
 */
public class HideInCoverGoal extends Goal {
    /** How long a strike keeps a fish scared enough to look for cover (ticks; 3 s). */
    static final int SCARED_TICKS = 60;
    /** A player swimming this near scares it into cover (blocks; inside Minecraft's own 8 for swimming away). */
    static final double SWIMMER_NEAR = 5.0;
    /** The dash's speed (FishMoveControl's factor), and how long the dash may take before it gives up (ticks). */
    static final double DASH_SPEED = 1.8;
    static final int DASH_TICKS = 100;
    /** How long it keeps still in cover (ticks; 5 to 10 s). */
    static final int STILL_MIN = 100, STILL_MORE = 100;
    /** After a look found no cover, no new look for this long (ticks), so a fish in open water does not search every tick. */
    static final int NO_LOOK_TICKS = 100;

    private final AbstractFish fish;
    private final RiverCover.Hiding hiding;
    /** Whether it may hide now (a tilapia guarding a nest or her fry stands her ground). */
    private final BooleanSupplier mayHide;
    @Nullable
    private BlockPos cover;
    private int dash, still;

    public <F extends AbstractFish & RiverCover.Hider> HideInCoverGoal(F fish, BooleanSupplier mayHide) {
        this.fish = fish;
        this.hiding = fish.hiding();
        this.mayHide = mayHide;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        long now = fish.level().getGameTime();
        if (!fish.isInWater() || fish.isPassenger() || now < hiding.noLookUntil || !mayHide.getAsBoolean()) {
            return false;
        }
        if (now >= hiding.scaredUntil && !swimmerNear()) {
            return false;
        }
        cover = RiverCover.nearest(fish.level(), fish.blockPosition());
        if (cover == null) {
            hiding.noLookUntil = now + NO_LOOK_TICKS;
            return false;
        }
        return true;
    }

    private boolean swimmerNear() {
        if (fish.getRandom().nextInt(5) != 0) {
            // (about every 10 ticks: goals are checked every other tick, odd or even by the creature's id, so a
            // "tickCount % 10" would never come round for half the fish, the otter's lesson)
            return false;
        }
        Player p = fish.level().getNearestPlayer(fish, SWIMMER_NEAR);
        return p != null && !p.isSpectator() && p.isInWater();
    }

    @Override
    public void start() {
        dash = 0;
        still = 0;
        hiding.scaredUntil = 0;
        go(DASH_SPEED);
    }

    @Override
    public boolean canContinueToUse() {
        return cover != null && fish.isInWater() && !fish.isPassenger() && (still > 0 || dash < DASH_TICKS);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cover == null) {
            return;                     // came out (the goal stops at the next check)
        }
        boolean inside = RiverCover.isCover(fish.level(), fish.blockPosition());
        if (still > 0) {
            if (--still == 0) {
                cover = null;           // out it comes
                return;
            }
            hiding.hidden = inside;
            fish.getNavigation().stop();
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.6));      // keeping still among the stalks
            if (!inside) {
                nudge();                // drifted out: back in
            }
            return;
        }
        dash++;
        if (inside) {
            still = STILL_MIN + fish.getRandom().nextInt(STILL_MORE);
            fish.getNavigation().stop();
            hiding.hidden = true;
            return;
        }
        if (fish.getNavigation().isDone() && fish.position().distanceTo(Vec3.atCenterOf(cover)) < 2.5) {
            nudge();        // the route ends at the bed's edge (a fish steers only along a route): the last bit straight in
        } else if (dash % 10 == 1 || fish.getNavigation().isDone()) {
            go(DASH_SPEED);
        }
    }

    /** How hard it pushes itself the last bit into cover (blocks a tick, added each tick; my pick). */
    static final double NUDGE = 0.04;

    /** A push straight toward the middle of its cover. */
    private void nudge() {
        Vec3 to = Vec3.atCenterOf(cover).subtract(fish.position().add(0, fish.getBbHeight() / 2, 0));
        if (to.lengthSqr() > 1.0E-4) {
            fish.setDeltaMovement(fish.getDeltaMovement().add(to.normalize().scale(NUDGE)));
            fish.getLookControl().setLookAt(Vec3.atCenterOf(cover));
        }
    }

    private void go(double speed) {
        fish.getNavigation().moveTo(cover.getX() + 0.5, cover.getY() + 0.3, cover.getZ() + 0.5, speed);
    }

    @Override
    public void stop() {
        hiding.hidden = false;
        cover = null;
        still = 0;
        fish.getNavigation().stop();
    }
}
