package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * A crab with a player right on top of it, or one that was just hit, stands its ground (M9 river life, 2026-11-15):
 * it turns to face them, rears up with both claws raised and snapping (the threat clip), and pinches whoever stays in
 * reach, half a heart at most every second and a half. It lets go once they back off, and after 20 s at most, so it can
 * scuttle away again. A player farther off it simply runs from ({@code CrabEntity}'s avoid goal). My picks.
 */
public class CrabThreatGoal extends Goal {
    /** A player this near makes it stand its ground; it calms once they are this far (blocks). */
    static final double TOO_NEAR = 2.0, BACKED_OFF = 3.5;
    /** Its pinch reaches this far (blocks, from its middle), at most once in this many ticks. */
    static final double PINCH_REACH = 1.2;
    static final int PINCH_EVERY = 30;
    /** The least and the most it holds the threat (ticks). */
    static final int LEAST = 40, MOST = 400;

    private final CrabEntity crab;
    @Nullable
    private LivingEntity foe;
    private int ticks, pinchIn;

    public CrabThreatGoal(CrabEntity crab) {
        this.crab = crab;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity hitBy = crab.getLastHurtByMob();
        if (hitBy != null && hitBy.isAlive() && crab.distanceToSqr(hitBy) < BACKED_OFF * BACKED_OFF && crab.tickCount - crab.getLastHurtByMobTimestamp() < 100) {
            foe = hitBy;
            return true;
        }
        Player player = crab.level().getNearestPlayer(crab, TOO_NEAR);
        if (player != null && !player.isCreative() && !player.isSpectator()) {
            foe = player;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return foe != null && foe.isAlive() && ticks < MOST && (ticks < LEAST || crab.distanceToSqr(foe) < BACKED_OFF * BACKED_OFF);
    }

    @Override
    public void start() {
        ticks = 0;
        pinchIn = 10;
        crab.getNavigation().stop();
    }

    @Override
    public void stop() {
        crab.setDoing(CrabEntity.CALM);
        foe = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (foe == null) {
            return;
        }
        ticks++;
        crab.getNavigation().stop();
        crab.face(foe.getX(), foe.getZ());
        crab.setDoing(CrabEntity.THREAT);
        if (--pinchIn <= 0 && crab.distanceToSqr(foe) < PINCH_REACH * PINCH_REACH
                && !(foe instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            foe.hurt(crab.damageSources().mobAttack(crab), (float) crab.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            pinchIn = PINCH_EVERY;
        }
    }
}
