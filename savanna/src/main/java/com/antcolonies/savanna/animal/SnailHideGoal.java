package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * The snail pulls into its shell (M9 river life, 2026-11-16): a player (not creative or spectator) within 3 blocks (1.5
 * crouching), a crab within 2.5, a crocodile within 4, or a hit. It sits tight, not moving, until nothing has scared it
 * for 5 to 10 seconds, then peeks out, eyes first ({@link #PEEK_TICKS}, the clip's length), and carries on; a new
 * scare while peeking pulls it straight back in. My picks.
 */
public class SnailHideGoal extends Goal {
    static final double PLAYER = 3.0, PLAYER_CROUCHING = 1.5, CRAB = 2.5, CROCODILE = 4.0;
    /** How long the peek clip takes ({@code snail_models.py} PEEK_LEN). */
    static final int PEEK_TICKS = 32;
    static final int CALM_MIN = 100, CALM_MORE = 100;

    private final SnailEntity snail;
    private int calm, peeking;

    public SnailHideGoal(SnailEntity snail) {
        this.snail = snail;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return scared();
    }

    @Override
    public boolean canContinueToUse() {
        return snail.isAlive() && (calm > 0 || peeking > 0);
    }

    @Override
    public void start() {
        hideAgain();
    }

    private void hideAgain() {
        snail.setDoing(SnailEntity.HIDDEN);
        snail.getNavigation().stop();
        calm = CALM_MIN + snail.getRandom().nextInt(CALM_MORE);
        peeking = 0;
    }

    @Override
    public void stop() {
        snail.setDoing(SnailEntity.CALM);
        calm = peeking = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        snail.getNavigation().stop();
        if (snail.tickCount % 5 == 0 && scared()) {
            hideAgain();
            return;
        }
        if (calm > 0 && --calm == 0) {
            snail.setDoing(SnailEntity.PEEK);
            peeking = PEEK_TICKS;
        } else if (peeking > 0) {
            peeking--;
        }
    }

    /** Whether anything scares it just now. */
    boolean scared() {
        LivingEntity hit = snail.getLastHurtByMob();
        if (snail.hurtTime > 0 || (hit != null && snail.tickCount - snail.getLastHurtByMobTimestamp() < 20)) {
            return true;
        }
        List<Entity> near = snail.level().getEntities(snail, snail.getBoundingBox().inflate(CROCODILE),
                e -> e instanceof Player || e instanceof CrabEntity || e instanceof CrocodileEntity);
        for (Entity e : near) {
            double d = e.distanceTo(snail);
            if (e instanceof Player p) {
                if (EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) && d < (p.isCrouching() ? PLAYER_CROUCHING : PLAYER)) {
                    return true;
                }
            } else if (d < (e instanceof CrabEntity ? CRAB : CROCODILE)) {
                return true;
            }
        }
        return false;
    }
}
