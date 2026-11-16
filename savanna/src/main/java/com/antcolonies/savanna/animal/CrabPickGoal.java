package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.block.ScrapsBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A hungry crab walks to the best food near it and picks at it (M9 river life, 2026-11-15, the user's ask "make it
 * actually pick the algae into its mouth"): remains first, then a scraps film, then algae (the nearest of the best
 * kind). Standing over it, facing it, it plays the pick clip once: the left claw pinches a bit off and carries it to
 * the mouth, then the right; each bit going into the mouth is one bite off the food ({@link #BITE_TICKS}). Then it is
 * fed for 2 to 4 minutes. With nothing to eat near it strolls on: a crab never starves (the balance rule). My picks.
 */
public class CrabPickGoal extends Goal {
    /** How far it smells remains, and looks for scraps and algae (blocks across; up or down). */
    static final double SMELL = 8.0;
    static final int LOOK_ACROSS = 6, LOOK_UP_DOWN = 2;
    /** The pick clip's length, and when each claw's bit goes into the mouth (ticks; {@code crab_models.py} PICK_LEN, PICK_BITE, plus the clip's blend-in). */
    static final int PICK_TICKS = 80;
    static final int[] BITE_TICKS = {37, 65};
    /** How long it may take to walk there before giving up. */
    static final int GIVE_UP_TICKS = 300;
    /** How long until it is hungry again after a meal (ticks; 2 to 4 minutes). */
    static final int FED_MIN = 2400, FED_MORE = 2400;

    private final CrabEntity crab;
    @Nullable
    private RemainsEntity remains;
    @Nullable
    private BlockPos block;
    private int ticks, picking;
    private boolean fed;

    public CrabPickGoal(CrabEntity crab) {
        this.crab = crab;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (crab.hungryIn > 0 || crab.getRandom().nextInt(10) != 0) {
            return false;
        }
        if (!find()) {
            crab.hungryIn = 200;   // nothing near: look again in 10 s, strolling on meanwhile
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !fed && food() != null && (picking > 0 || ticks < GIVE_UP_TICKS);
    }

    @Override
    public void start() {
        ticks = picking = 0;
        fed = false;
    }

    @Override
    public void stop() {
        crab.setDoing(CrabEntity.CALM);
        crab.getNavigation().stop();
        if (!fed) {
            crab.hungryIn = 100 + crab.getRandom().nextInt(200);
        }
        remains = null;
        block = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Vec3 food = food();
        if (food == null) {
            return;
        }
        ticks++;
        double dx = food.x - crab.getX(), dz = food.z - crab.getZ();
        double far = Math.sqrt(dx * dx + dz * dz);
        // its claws reach about a third of a block in front of it; at remains it walks to the nearest part of the fish
        // (food() gives the nearest point along its spine) and stops with its claws over the fish's side
        double reach = 0.45 * crab.getScale() + (remains != null ? 0.1 * remains.size() : 0.0);
        if (picking == 0 && (far > reach + 0.15 || Math.abs(food.y - crab.getY()) > 1.2)) {
            if (far < 1.5) {       // the last stretch: a route counts as walked a little short of its end
                crab.getNavigation().stop();
                crab.getMoveControl().setWantedPosition(food.x, crab.getY(), food.z, 0.6);
            } else if (ticks % 20 == 1 || crab.getNavigation().isDone()) {
                crab.getNavigation().moveTo(food.x, food.y, food.z, 0.8);
            }
            return;
        }
        crab.getNavigation().stop();
        crab.face(food.x, food.z);
        crab.setDoing(remains != null || isScraps() ? CrabEntity.PICK_MEAT : CrabEntity.PICK_ALGAE);
        picking++;
        for (int at : BITE_TICKS) {
            if (picking == at && !bite()) {
                fed = picking > BITE_TICKS[0];
                remains = null;
                block = null;
                return;
            }
        }
        if (picking >= PICK_TICKS) {
            fed = true;
        }
        if (fed) {
            crab.hungryIn = FED_MIN + crab.getRandom().nextInt(FED_MORE);
        }
    }

    private boolean isScraps() {
        return block != null && crab.level().getBlockState(block).is(SavannaBlocks.SCRAPS.get());
    }

    /** One bit into the mouth: a bite off the food. Whether there was anything to take. */
    private boolean bite() {
        if (remains != null) {
            return remains.isAlive() && remains.bite(crab);
        }
        if (block == null) {
            return false;
        }
        return ScrapsBlock.nibble(crab.level(), block) || AlgaeBlock.graze(crab.level(), block);
    }

    /** Where the food is, or null once it is gone. */
    @Nullable
    private Vec3 food() {
        if (remains != null) {
            return remains.isAlive() && remains.bitesLeft() > 0 ? nearestOnSpine(remains) : null;
        }
        if (block == null) {
            return null;
        }
        BlockState state = crab.level().getBlockState(block);
        if (!state.is(SavannaBlocks.SCRAPS.get()) && AlgaeBlock.bitesLeft(state) == 0) {
            return null;
        }
        return new Vec3(block.getX() + 0.5, block.getY(), block.getZ() + 0.5);
    }

    /**
     * The point of the fish's spine nearest the crab (2026-11-15: walking to the remains' middle left it picking at the
     * air beside a long fish). The fish lies along its heading; the spine counted is the part sure to be under meat
     * either way round (half the shorter end's length from the middle, in blocks at size 1; from the remains' models).
     */
    private Vec3 nearestOnSpine(RemainsEntity r) {
        double half = switch (r.kind()) {
            case CATFISH -> 0.8;
            case CATFISH_TORN -> 0.12;
            case TILAPIA -> 0.25;
            case TILAPIA_HALF -> 0.04;
        } * r.size();
        double yaw = Math.toRadians(r.getYRot());
        double ax = -Math.sin(yaw), az = Math.cos(yaw);
        double along = (crab.getX() - r.getX()) * ax + (crab.getZ() - r.getZ()) * az;
        along = Math.max(-half, Math.min(half, along));
        return new Vec3(r.getX() + ax * along, r.getY(), r.getZ() + az * along);
    }

    /** Picks the food: the nearest remains with meat on them, else the nearest scraps, else the nearest algae. */
    private boolean find() {
        remains = null;
        block = null;
        double best = Double.MAX_VALUE;
        for (RemainsEntity r : crab.level().getEntitiesOfClass(RemainsEntity.class, crab.getBoundingBox().inflate(SMELL, 3.0, SMELL),
                r -> r.bitesLeft() > 0)) {
            double d = r.distanceToSqr(crab);
            if (d < best) {
                best = d;
                remains = r;
            }
        }
        if (remains != null) {
            return true;
        }
        BlockPos home = crab.blockPosition();
        boolean scraps = false;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-LOOK_ACROSS, -LOOK_UP_DOWN, -LOOK_ACROSS),
                home.offset(LOOK_ACROSS, LOOK_UP_DOWN, LOOK_ACROSS))) {
            BlockState state = crab.level().getBlockState(at);
            boolean isScraps = state.is(SavannaBlocks.SCRAPS.get());
            if (!isScraps && (scraps || AlgaeBlock.bitesLeft(state) == 0)) {
                continue;
            }
            double d = at.distSqr(home);
            if ((isScraps && !scraps) || d < best) {
                best = d;
                block = at.immutable();
                scraps = isScraps;
            }
        }
        return block != null;
    }
}
