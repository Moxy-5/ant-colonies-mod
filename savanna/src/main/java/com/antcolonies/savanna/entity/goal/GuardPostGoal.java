package com.antcolonies.savanna.entity.goal;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.goal.Errand;
import com.antcolonies.fight.Fights;
import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.colony.Soldiers;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
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
    /** M8 step 3S, S1: ticks between two bites at a foe touching it at its post. */
    private static final int BITE_INTERVAL = 20;

    private final TermiteSoldierEntity soldier;
    @Nullable
    private Colony colony;
    /** Its colony's soldiers (asked once in {@link #canUse}: only a species that lists soldiers has posts). */
    @Nullable
    private Soldiers soldiers;
    @Nullable
    private Soldiers.Post post;
    /** The walk to its post; a post it found no way to is left alone for a minute ({@link #AVOID_TICKS}). */
    private final Errand<BlockPos> errand;
    private long nextTry;
    private int repath;
    private int tries;
    private boolean lost;
    private int biteCooldown;

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
        soldiers = colony.part(SavannaParts.SOLDIERS).orElse(null);
        post = soldiers == null ? null : soldiers.claimPost(level, soldier, errand.leftAlone());
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
        return colony != null && soldiers != null && post != null && !lost;
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
        soldiers = null;
    }

    @Override
    public void tick() {
        if (colony == null || soldiers == null || post == null || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        // Once a second: is it still a post, and still its own?
        if (soldier.tickCount % 20 == soldier.getId() % 20 && !soldiers.holds(level, soldier, post, errand.leftAlone())) {
            lost = true;
            return;
        }
        if (biteCooldown > 0) {
            biteCooldown--;
        }
        if (atPost()) {
            errand.stop();
            LivingEntity touching = biteCooldown <= 0 && (soldier.tickCount + soldier.getId()) % 5 == 0 ? touchingFoe(level) : null;
            if (touching != null) {
                // S1: a blind soldier bites whatever touches it, at its post too (it stays where it is).
                soldier.bite(touching);
                biteCooldown = BITE_INTERVAL;
            } else {
                faceOut();
            }
            tries = 0;
        } else if (!errand.isWalking() && --repath <= 0) {
            repath = 40;
            if (!errand.setOff(post.cell(), 1.0) && ++tries >= MAX_TRIES) {
                giveUp(level);
                lost = true;
            }
        }
    }

    /** S1: a foe of the colony its jaws reach (their boxes nearly touch), which it may bite; null for none. */
    @Nullable
    private LivingEntity touchingFoe(ServerLevel level) {
        for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, soldier.getBoundingBox().inflate(0.3, 0.2, 0.3), LivingEntity::isAlive)) {
            if (foe != soldier && !(foe instanceof AbstractAntEntity insect && insect.isDowned())
                    && Fights.isFoe(colony.profile(), colony.getSpecies(), colony.getId(), foe) && Fights.mayAttack(soldier.profile(), foe)) {
                return foe;
            }
        }
        return null;
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
        if (soldiers != null) {
            soldiers.releasePost(soldier.getUUID());
        }
        nextTry = level.getGameTime() + 20;
    }

    /** The post it is guarding or walking to, or null (for tests and debugging). */
    @Nullable
    public Soldiers.Post getPost() {
        return post;
    }
}
