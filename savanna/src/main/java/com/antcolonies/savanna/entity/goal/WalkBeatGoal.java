package com.antcolonies.savanna.entity.goal;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.goal.Errand;
import com.antcolonies.fight.Fights;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.colony.Soldiers;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A soldier keeps watch on its beat (the termite alarm rework, step 2; it was {@code GuardPostGoal}, a soldier
 * standing still in one cell): it takes a beat from its colony (see {@link Soldiers#posts}: majors at the royal
 * chamber's doorway, the way into its tunnel from the shaft, a feeding place and the garden doorways; minors at the
 * feeding places the workers eat at by day, at foraging time beside the ways out and along the foragers' trails,
 * otherwise in the foraging tunnels), walks there, and then walks it: a few steps to another cell of the beat (within
 * {@link Soldiers#beatReach} of its middle, found by walking out from where it stands, so always a short way it can
 * really go), a pause looking roughly the way trouble would come from (a little to one side or the other each time),
 * and on. As real Macrotermes soldiers shift about among the workers they guard. A blind soldier bites whatever foe
 * touches it. Each beat has one soldier; when a more important beat comes free the soldier moves up to it; one it finds
 * no way to is left alone for a minute.
 */
public class WalkBeatGoal extends Goal {
    /** How long (ticks) a soldier with no beat waits before it asks again. */
    private static final int RETRY_TICKS = 100;
    /** How long (ticks) it leaves a beat alone that it found no way to. */
    private static final int AVOID_TICKS = 1200;
    /** How many times in a row it may fail to plan a way to its beat before it gives the beat up. */
    private static final int MAX_TRIES = 3;
    /** How fast (degrees a tick) it turns to look out when it pauses. */
    private static final float TURN = 12.0F;
    /** M8 step 3S, S1: ticks between two bites at a foe touching it on its beat. */
    private static final int BITE_INTERVAL = 20;
    /** A pause on the beat lasts this long (ticks), at random. My picks: 2 to 6 seconds. */
    private static final int PAUSE_MIN = 40, PAUSE_MAX = 120;
    /** How far (degrees) to either side of straight out it may look in a pause. */
    private static final float LOOK_SPREAD = 60.0F;
    /** How fast it walks along its beat (a share of its walk: an unhurried patrol). */
    private static final double BEAT_SPEED = 0.7;

    private final TermiteSoldierEntity soldier;
    @Nullable
    private Colony colony;
    /** Its colony's soldiers (asked once in {@link #canUse}: only a species that lists soldiers has beats). */
    @Nullable
    private Soldiers soldiers;
    @Nullable
    private Soldiers.Post post;
    /** The walk to its beat and along it; a beat it found no way to is left alone for a minute ({@link #AVOID_TICKS}). */
    private final Errand<BlockPos> errand;
    private long nextTry;
    private int repath;
    private int tries;
    private boolean lost;
    private int biteCooldown;
    /** Ticks left of the pause it is in (0: walking, or about to take its next steps). */
    private int pause;
    /** Where it looks in this pause: degrees off straight out. */
    private float lookOff;

    public WalkBeatGoal(TermiteSoldierEntity soldier) {
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
        if (!onBeat() && !errand.setOff(post.cell(), 1.0)) {
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
        newPause();
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        // Stopped for something else (drumming, being fed): it keeps its beat and comes back to it.
        post = null;
        colony = null;
        soldiers = null;
    }

    @Override
    public void tick() {
        if (colony == null || soldiers == null || post == null || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        // Once a second: is it still a beat, and still its own?
        if (soldier.tickCount % 20 == soldier.getId() % 20 && !soldiers.holds(level, soldier, post, errand.leftAlone())) {
            lost = true;
            return;
        }
        if (biteCooldown > 0) {
            biteCooldown--;
        }
        if (onBeat()) {
            tries = 0;
            LivingEntity touching = biteCooldown <= 0 && (soldier.tickCount + soldier.getId()) % 5 == 0 ? touchingFoe(level) : null;
            if (touching != null) {
                // S1: a blind soldier bites whatever touches it, on its beat too (it stays where it is).
                errand.stop();
                soldier.bite(touching);
                biteCooldown = BITE_INTERVAL;
                pause = Math.max(pause, BITE_INTERVAL);
            } else if (errand.isWalking()) {
                // (walking its beat)
            } else if (pause > 0) {
                pause--;
                lookOut();
            } else {
                // The next few steps; with nowhere to step to (a beat of one cell) it looks another way instead.
                BlockPos next = nextCell(level);
                if (next != null) {
                    errand.setOff(next, BEAT_SPEED);
                }
                newPause();
            }
        } else if (!errand.isWalking() && --repath <= 0) {
            repath = 40;
            if (!errand.setOff(post.cell(), 1.0) && ++tries >= MAX_TRIES) {
                giveUp(level);
                lost = true;
            }
        }
    }

    private void newPause() {
        pause = PAUSE_MIN + soldier.getRandom().nextInt(PAUSE_MAX - PAUSE_MIN + 1);
        lookOff = (soldier.getRandom().nextFloat() * 2.0F - 1.0F) * LOOK_SPREAD;
    }

    /**
     * Another cell of its beat to walk to, at random: found by walking out from where it stands, a block at a time
     * (level, or a block up or down), through cells of its beat only, so the way there is short and certain (no route
     * search that could fail). Null if there is none.
     */
    @Nullable
    private BlockPos nextCell(ServerLevel level) {
        BlockPos here = soldier.blockPosition();
        BlockPos middle = post.cell();
        int reach = Soldiers.beatReach(post.kind());
        Map<BlockPos, Integer> steps = new HashMap<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        steps.put(here, 0);
        open.add(here);
        List<BlockPos> cells = new ArrayList<>();
        while (!open.isEmpty()) {
            BlockPos at = open.poll();
            int walked = steps.get(at);
            if (walked >= reach * 2) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = at.relative(side).above(dy);
                    if (steps.containsKey(next) || Math.abs(next.getX() - middle.getX()) > reach || Math.abs(next.getZ() - middle.getZ()) > reach
                            || Math.abs(next.getY() - middle.getY()) > 1 || !soldiers.isBeatCell(level, post.kind(), next)) {
                        continue;
                    }
                    steps.put(next, walked + 1);
                    open.add(next);
                    cells.add(next);
                }
            }
        }
        return cells.isEmpty() ? null : cells.get(soldier.getRandom().nextInt(cells.size()));
    }

    /**
     * S1: a foe of the colony its jaws reach (their boxes nearly touch), which it may bite; null for none. Real sizes,
     * step 2: a third of its body's length past its box ({@link AbstractAntEntity#touches}; was a fixed 0.3 blocks).
     */
    @Nullable
    private LivingEntity touchingFoe(ServerLevel level) {
        double reach = soldier.jawReach();
        for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, soldier.getBoundingBox().inflate(reach, 0.2, reach), LivingEntity::isAlive)) {
            if (foe != soldier && !(foe instanceof AbstractAntEntity insect && insect.isDowned())
                    && Fights.isFoe(colony.profile(), colony.getSpecies(), colony.getId(), foe) && Fights.mayAttack(soldier.profile(), foe)) {
                return foe;
            }
        }
        return null;
    }

    /** Whether it is on its beat: within the beat's reach of its middle, on a floor. */
    private boolean onBeat() {
        if (post == null || soldier.isClimbing()) {
            return false;
        }
        BlockPos here = soldier.blockPosition();
        int reach = Soldiers.beatReach(post.kind());
        return Math.abs(here.getX() - post.cell().getX()) <= reach && Math.abs(here.getZ() - post.cell().getZ()) <= reach
                && Math.abs(here.getY() - post.cell().getY()) <= 1;
    }

    /** Turns the soldier to look the way trouble would come from, a little to the side picked for this pause. */
    private void lookOut() {
        double dx = post.facing().getX() - post.cell().getX();
        double dz = post.facing().getZ() - post.cell().getZ();
        if (dx == 0 && dz == 0) {
            return;
        }
        float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F + lookOff;
        float yaw = Mth.approachDegrees(soldier.getYRot(), target, TURN);
        soldier.setYRot(yaw);
        soldier.setYBodyRot(yaw);
        soldier.setYHeadRot(yaw);
    }

    /** It found no way to its beat: it leaves that beat alone for a while and gives it up to the others. */
    private void giveUp(ServerLevel level) {
        if (post != null) {
            errand.leaveAlone(post.cell());
        }
        if (soldiers != null) {
            soldiers.releasePost(soldier.getUUID());
        }
        nextTry = level.getGameTime() + 20;
    }

    /** The beat it is walking or walking to, or null (for tests and debugging). */
    @Nullable
    public Soldiers.Post getPost() {
        return post;
    }
}
