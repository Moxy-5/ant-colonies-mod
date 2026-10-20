package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.Soldiers;
import com.antcolonies.entity.TermiteSoldierEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A soldier stands guard: it takes a post from its colony (see {@link Soldiers#posts}: majors at the royal chamber's
 * doorway, the way into its tunnel from the shaft and the garden doorways; minors at foraging time beside the ways out
 * and along the foragers' trails, otherwise in the foraging tunnels), walks there, and stands in it facing the way
 * trouble would come from, for as long as it holds the post. Each post has one soldier; when a more important post
 * comes free the soldier moves up to it; one it finds no way to is left alone for a minute.
 */
public class GuardPostGoal extends Goal {
    /** How long (ticks) a soldier with no post waits before it asks again. */
    private static final int RETRY_TICKS = 100;
    /** How long (ticks) it leaves a post alone that it found no way to. */
    private static final int AVOID_TICKS = 1200;
    /** How many times in a row it may fail to plan a way to its post before it gives the post up. */
    private static final int MAX_TRIES = 3;
    /** How fast (degrees a tick) it turns to face out once it stands at its post. */
    private static final float TURN = 12.0F;

    private final TermiteSoldierEntity soldier;
    @Nullable
    private Colony colony;
    @Nullable
    private Soldiers.Post post;
    /** The walk to its post; a post it found no way to is left alone for a minute ({@link #AVOID_TICKS}). */
    private final Errand<BlockPos> errand;
    private long nextTry;
    private int repath;
    private int tries;
    private boolean lost;

    public GuardPostGoal(TermiteSoldierEntity soldier) {
        this.soldier = soldier;
        this.errand = new Errand<>(soldier, AVOID_TICKS);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (soldier.getColonyId() == null || !(soldier.level() instanceof ServerLevel level)) {
            return false;
        }
        // Only now and then, so the soldiers don't all think on the same tick.
        long now = level.getGameTime();
        if (now < nextTry || soldier.getRandom().nextInt(10) != 0) {
            return false;
        }
        colony = soldier.getColony();
        if (colony == null) {
            return false;
        }
        post = colony.soldiers().claimPost(level, soldier, errand.leftAlone());
        if (post == null) {
            nextTry = now + RETRY_TICKS;
            return false;
        }
        if (!atPost() && !errand.setOff(post.cell(), 1.0)) {
            giveUp(level);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && post != null && !lost;
    }

    @Override
    public void start() {
        repath = 0;
        tries = 0;
        lost = false;
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        // Stopped for something else (drumming, being fed): it keeps its post and comes back to it.
        post = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || post == null || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        // Once a second: is it still a post, and still its own?
        if (soldier.tickCount % 20 == soldier.getId() % 20 && !colony.soldiers().holds(level, soldier, post, errand.leftAlone())) {
            lost = true;
            return;
        }
        if (atPost()) {
            errand.stop();
            faceOut();
            tries = 0;
        } else if (!errand.isWalking() && --repath <= 0) {
            repath = 40;
            if (!errand.setOff(post.cell(), 1.0) && ++tries >= MAX_TRIES) {
                giveUp(level);
                lost = true;
            }
        }
    }

    /** Whether it stands in its post's cell, on the floor. */
    private boolean atPost() {
        return post != null && soldier.blockPosition().equals(post.cell()) && !soldier.isClimbing();
    }

    /** Turns the soldier toward the block it faces at its post. */
    private void faceOut() {
        double dx = post.facing().getX() - post.cell().getX();
        double dz = post.facing().getZ() - post.cell().getZ();
        if (dx == 0 && dz == 0) {
            return;
        }
        float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(soldier.getYRot(), target, TURN);
        soldier.setYRot(yaw);
        soldier.setYBodyRot(yaw);
        soldier.setYHeadRot(yaw);
    }

    /** It found no way to its post: it leaves that post alone for a while and gives it up to the others. */
    private void giveUp(ServerLevel level) {
        if (post != null) {
            errand.leaveAlone(post.cell());
        }
        if (colony != null) {
            colony.soldiers().releasePost(soldier.getUUID());
        }
        nextTry = level.getGameTime() + 20;
    }

    /** The post it is guarding or walking to, or null (for tests and debugging). */
    @Nullable
    public Soldiers.Post getPost() {
        return post;
    }
}
