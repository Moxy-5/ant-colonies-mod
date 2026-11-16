package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.AlgaeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A hungry snail creeps to the nearest algae and rasps at it with its head down (M9 river life, 2026-11-16): the graze
 * clip plays for {@link #GRAZE_TICKS}, and partway through it takes one bite off the mat ({@link AlgaeBlock#graze}, the
 * same bite a tilapia takes). Then it is fed for 1.5 to 3 minutes. Nothing near, it looks again in 15 s, creeping on
 * meanwhile: a snail never starves. My picks.
 */
public class SnailGrazeGoal extends Goal {
    static final int LOOK_ACROSS = 6, LOOK_UP_DOWN = 2;
    static final int GRAZE_TICKS = 100, BITE_AT = 70;
    static final int GIVE_UP_TICKS = 900;
    static final int FED_MIN = 1800, FED_MORE = 1800;

    private final SnailEntity snail;
    @Nullable
    private BlockPos algae;
    private int ticks, grazing;
    private boolean fed;

    public SnailGrazeGoal(SnailEntity snail) {
        this.snail = snail;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (snail.hungryIn > 0 || snail.getRandom().nextInt(10) != 0) {
            return false;
        }
        algae = find();
        if (algae == null) {
            snail.hungryIn = 300;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !fed && algae != null && (grazing > 0 ? grazing < GRAZE_TICKS + 1 : AlgaeBlock.bitesLeft(snail.level().getBlockState(algae)) > 0)
                && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = grazing = 0;
        fed = false;
    }

    @Override
    public void stop() {
        if (snail.doing() == SnailEntity.GRAZE) {
            snail.setDoing(SnailEntity.CALM);
        }
        snail.getNavigation().stop();
        if (!fed) {
            snail.hungryIn = 200 + snail.getRandom().nextInt(200);
        }
        algae = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (algae == null) {
            return;
        }
        ticks++;
        double x = algae.getX() + 0.5, z = algae.getZ() + 0.5;
        double dx = x - snail.getX(), dz = z - snail.getZ();
        double far = Math.sqrt(dx * dx + dz * dz);
        if (grazing == 0 && (far > 0.4 || Math.abs(algae.getY() - snail.getY()) > 1.2)) {
            // Minecraft's routes stop a block short of the mat (it counts as no floor to walk onto), so the last bit is a
            // straight creep: close by, or once the route has run out
            if (far < 2.5 || (ticks > 1 && snail.getNavigation().isDone())) {
                snail.getNavigation().stop();
                snail.getMoveControl().setWantedPosition(x, snail.getY(), z, 1.0);
            } else if (ticks % 20 == 1) {
                snail.getNavigation().moveTo(x, algae.getY(), z, 1.0);
            }
            return;
        }
        snail.getNavigation().stop();
        if (far > 0.05) {
            snail.face(x, z);
        }
        snail.setDoing(SnailEntity.GRAZE);
        grazing++;
        if (grazing == BITE_AT) {
            AlgaeBlock.graze(snail.level(), algae);
        }
        if (grazing >= GRAZE_TICKS) {
            fed = true;
            snail.hungryIn = FED_MIN + snail.getRandom().nextInt(FED_MORE);
        }
    }

    @Nullable
    private BlockPos find() {
        BlockPos home = snail.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-LOOK_ACROSS, -LOOK_UP_DOWN, -LOOK_ACROSS),
                home.offset(LOOK_ACROSS, LOOK_UP_DOWN, LOOK_ACROSS))) {
            if (AlgaeBlock.bitesLeft(snail.level().getBlockState(at)) == 0) {
                continue;
            }
            double d = at.distSqr(home);
            if (d < bestD) {
                bestD = d;
                best = at.immutable();
            }
        }
        return best;
    }
}
