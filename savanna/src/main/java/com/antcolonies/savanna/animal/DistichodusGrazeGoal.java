package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.block.HippoGrassBlock;
import com.antcolonies.savanna.block.HippoGrassClumpBlock;
import com.antcolonies.savanna.block.ReedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The distichodus grazes (the river food web's step 5, 2026-11-23, {@code docs/plans/distichodus.md}; my picks, animals
 * fun first). No hunger: now and then (about every 12 s) it swims to the nearest plant and feeds a few seconds.
 * <ul>
 * <li>Under a living hippo grass mat: nose up, two bites torn off with head shakes (the {@code graze} clip). A bite takes
 * the mat away only by the tilapia's rule ({@link HippoGrassBlock#livingBite}: a patch's outermost mat lying 2 or 3 out,
 * never the ring at the bank, so a clump is never stripped and its cycle sends the mats out again), and each fish takes
 * at most one bite every {@link #BITE_EVERY} ticks; other times it grazes the mat's underside for looks.</li>
 * <li>With no mat near, at a reed column's foot: nose down, three nips (the {@code nibble} clip; looks only, my pick: a
 * reed stalk is no food the game counts, and a shoot nipped back would only fight its growing).</li>
 * <li>With no plants near, at an algae mat, for looks only (the barb's way: the algae stays the tilapia's).</li>
 * </ul>
 * A bigger fish arriving finds a smaller one within {@link #FLARE_NEAR} blocks of its spot: now and then it flares its
 * fins at it first and the smaller one bolts off (looks only).
 */
public class DistichodusGrazeGoal extends Goal {
    /** One check in this many starts a meal (checked every other tick: about every 12 s). */
    static final int CHANCE = 120;
    /** How far it looks for a plant (blocks across, and up or down). */
    static final int LOOK_ACROSS = 8, LOOK_UP_DOWN = 4;
    /** How long it grazes and nibbles (ticks: the graze clip twice, the nibble clip twice). */
    static final int GRAZE_TICKS = 96, NIBBLE_TICKS = 72;
    /** A fish bites a mat away at most once in this many ticks (2 minutes, the plan's). */
    static final int BITE_EVERY = 2400;
    /** The tick of the meal a bite is torn off (the graze clip's first shake, 1.2 s in). */
    static final int BITE_AT = 24;
    /** Nearer its spot than this (blocks) it swims straight at it (a fish's path stops short: the barb's lesson). */
    static final double STRAIGHT = 3.0;
    /** How long it may swim to its spot before it gives up (ticks). */
    static final int GIVE_UP = 140;
    /** A smaller fish this near its spot may be flared at (blocks), one arrival in this many with one near, and how hard it is chased off. */
    static final double FLARE_NEAR = 2.5, CHASE_PUSH = 0.3;
    static final int FLARE_ONE_IN = 2;

    private final DistichodusEntity fish;
    @Nullable
    private Vec3 spot;
    /** The mat it grazes under (null at a reed or algae). */
    @Nullable
    private BlockPos mat;
    private byte kind;
    private int ticks, eating, flaring;

    public DistichodusGrazeGoal(DistichodusEntity fish) {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!fish.isInWater() || fish.isPassenger() || fish.action() != DistichodusEntity.NONE || fish.getRandom().nextInt(CHANCE) != 0) {
            return false;
        }
        return findSpot();
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && fish.isInWater() && !fish.isPassenger() && ticks < GIVE_UP + GRAZE_TICKS + DistichodusEntity.FLARE_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        eating = 0;
        flaring = 0;
        fish.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
    }

    @Override
    public void stop() {
        fish.setAction(DistichodusEntity.NONE);
        fish.getNavigation().stop();
        spot = null;
        mat = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (spot == null) {
            return;
        }
        ticks++;
        if (flaring > 0) {
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.6));
            if (--flaring == 0) {
                fish.setAction(kind);
                eating = 1;
            }
            return;
        }
        if (eating > 0) {
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.6));
            if (eating == BITE_AT && mat != null) {
                bite();
            }
            if (++eating > (kind == DistichodusEntity.GRAZE ? GRAZE_TICKS : NIBBLE_TICKS)) {
                spot = null;
            }
            return;
        }
        double dx = spot.x - fish.getX(), dy = spot.y - fish.getY(), dz = spot.z - fish.getZ();
        double far = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (far < 0.4) {
            fish.getNavigation().stop();
            arrive();
            return;
        }
        if (ticks > GIVE_UP) {
            spot = null;
            return;
        }
        if (far < STRAIGHT) {
            fish.getNavigation().stop();
            double push = 0.025 / Math.max(far, 0.05);
            fish.setDeltaMovement(fish.getDeltaMovement().scale(0.8).add(dx * push, dy * push, dz * push));
            if (dx * dx + dz * dz > 0.01) {
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
                fish.setYRot(Mth.approachDegrees(fish.getYRot(), yaw, 20.0F));
                fish.yBodyRot = fish.getYRot();
            }
        } else if (ticks % 20 == 0 || fish.getNavigation().isDone()) {
            fish.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }
    }

    /** At its spot: a smaller one there is now and then flared at and chased off first; then it feeds. */
    private void arrive() {
        DistichodusEntity smaller = null;
        for (DistichodusEntity other : fish.level().getEntitiesOfClass(DistichodusEntity.class, fish.getBoundingBox().inflate(FLARE_NEAR))) {
            if (other != fish && other.ownSize() < fish.ownSize() * 0.95F && other.isInWater() && !other.isPassenger()) {
                smaller = other;
                break;
            }
        }
        if (smaller != null && fish.getRandom().nextInt(FLARE_ONE_IN) == 0) {
            fish.show(DistichodusEntity.FLARE, DistichodusEntity.FLARE_TICKS);
            fish.getLookControl().setLookAt(smaller);
            Vec3 away = smaller.position().subtract(fish.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) {
                smaller.setDeltaMovement(smaller.getDeltaMovement().add(away.normalize().scale(CHASE_PUSH)));
                smaller.hurtMarked = true;
            }
            smaller.show(DistichodusEntity.BOLT, DistichodusEntity.BOLT_TICKS);
            smaller.getNavigation().stop();
            flaring = DistichodusEntity.FLARE_TICKS;
            return;
        }
        fish.setAction(kind);
        eating = 1;
    }

    /** A bite off the mat it grazes under, if the mat may lose one and the fish has not bitten lately. */
    private void bite() {
        if (!(fish.level() instanceof ServerLevel level) || level.getGameTime() < fish.nextBite) {
            return;
        }
        if (HippoGrassBlock.livingBite(level, mat, level.getBlockState(mat)) && HippoGrassBlock.bitten(level, mat)) {
            fish.nextBite = level.getGameTime() + BITE_EVERY;
        }
    }

    /** Finds where to feed: under the nearest living mat, else at the nearest reed foot, else over the nearest algae. */
    private boolean findSpot() {
        Level level = fish.level();
        BlockPos home = fish.blockPosition();
        BlockPos bestMat = null, bestReed = null, bestAlgae = null;
        double dMat = Double.MAX_VALUE, dReed = Double.MAX_VALUE, dAlgae = Double.MAX_VALUE;
        boolean biteReady = level.getGameTime() >= fish.nextBite;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-LOOK_ACROSS, -LOOK_UP_DOWN, -LOOK_ACROSS), home.offset(LOOK_ACROSS, LOOK_UP_DOWN, LOOK_ACROSS))) {
            BlockState state = level.getBlockState(at);
            double d = at.distSqr(home);
            if (state.getBlock() instanceof HippoGrassBlock) {
                if (state.getValue(HippoGrassBlock.STAGE) == HippoGrassClumpBlock.DYING || !level.getFluidState(at.below()).is(FluidTags.WATER)) {
                    continue;
                }
                // a mat it may bite comes first when a bite is due (counted as nearer), else any living mat for looks
                if (biteReady && HippoGrassBlock.livingBite(level, at, state)) {
                    d *= 0.25;
                }
                if (d < dMat) {
                    bestMat = at.immutable();
                    dMat = d;
                }
            } else if (state.getBlock() instanceof ReedBlock && state.getValue(ReedBlock.WATERLOGGED) && !(level.getBlockState(at.below()).getBlock() instanceof ReedBlock)) {
                if (d < dReed) {
                    bestReed = at.immutable();
                    dReed = d;
                }
            } else if (AlgaeBlock.bitesLeft(state) > 0 && d < dAlgae) {
                bestAlgae = at.immutable();
                dAlgae = d;
            }
        }
        float h = fish.getBbHeight();
        if (bestMat != null) {
            mat = bestMat;
            kind = DistichodusEntity.GRAZE;
            spot = new Vec3(bestMat.getX() + 0.5, bestMat.getY() - 1 + 0.9 - h, bestMat.getZ() + 0.5);      // just under the mat
        } else if (bestReed != null) {
            kind = DistichodusEntity.NIBBLE;
            spot = new Vec3(bestReed.getX() + 0.5, bestReed.getY() + 0.15, bestReed.getZ() + 0.5);           // at the stem's foot
        } else if (bestAlgae != null) {
            kind = DistichodusEntity.NIBBLE;
            spot = new Vec3(bestAlgae.getX() + 0.5, bestAlgae.getY() + 0.25, bestAlgae.getZ() + 0.5);
        }
        return spot != null;
    }
}
