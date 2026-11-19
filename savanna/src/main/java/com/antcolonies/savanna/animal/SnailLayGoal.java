package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.animal.GoExactly;
import com.antcolonies.savanna.block.SnailEggsBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A river snail lays (M9, animal life cycle step 2, 2026-11-19; the plan's picks, fun first): after two snails have met
 * and touched ({@link SnailEntity#mated}), each creeps to the best spot near it and lays a clutch of pearly
 * {@linkplain SnailEggsBlock eggs} stuck to the side of a stone or the bank: best just over the waterline (as real apple
 * snails, out of the fishes' reach), else just under the surface, else anywhere on a side in the water, else on the bed
 * where it is. A snail creeps on the bed and cannot climb, so it takes only spots it can reach from a floor: a side of
 * the water block it stands in or the one over it (so the waterline only where the water is a block deep, the sloping
 * shallows by a bank). It lays with its head down for a few seconds (the graze clip). A snail that cannot reach its spot
 * in a minute lays where it is.
 */
public class SnailLayGoal extends Goal {
    /** How far round it looks for a spot (blocks, sideways and up or down). */
    static final int REACH = 6;
    static final int UP_DOWN = 2;
    /** How near the spot it lays from (blocks, sideways; up or down it reaches {@link #REACH_UP}). */
    static final double LAY_FROM = 1.6;
    static final double REACH_UP = 1.6;
    /** How long it lays (ticks). */
    static final int LAY_TICKS = 60;
    /** Lays where it is after this long creeping (ticks). */
    static final int GIVE_UP = 1200;

    /** A spot to lay at: the eggs' block and the way they face (away from what they stick to). */
    record Spot(BlockPos pos, Direction facing) {
    }

    private final SnailEntity snail;
    @Nullable
    private Spot spot;
    private int crept, laid;

    public SnailLayGoal(SnailEntity snail) {
        this.snail = snail;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return snail.eggsToLay() > 0 && !snail.isHidden();
    }

    @Override
    public boolean canContinueToUse() {
        return snail.eggsToLay() > 0 && !snail.isHidden();
    }

    @Override
    public void start() {
        crept = 0;
        laid = 0;
        spot = findSpot(snail.level(), snail.blockPosition());
    }

    @Override
    public void stop() {
        snail.setDoing(SnailEntity.CALM);
        snail.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (snail.eggsToLay() <= 0) {
            return;                        // laid last tick (Minecraft asks canContinueToUse only every other tick)
        }
        if (spot == null || !stillFree(snail.level(), spot)) {
            spot = findSpot(snail.level(), snail.blockPosition());
            if (spot == null) {
                snail.laidEggs();          // nowhere at all to stick them: the clutch is lost
                return;
            }
        }
        Vec3 at = Vec3.atCenterOf(spot.pos());
        if (!within(snail, at) && crept < GIVE_UP) {
            crept++;
            snail.setDoing(SnailEntity.CALM);
            if (snail.getNavigation().isDone() || crept % 40 == 0) {
                BlockPos to = standAt(snail.level(), spot.pos());
                GoExactly.to(snail, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
            }
            return;
        }
        if (crept >= GIVE_UP && !within(snail, at)) {
            spot = here(snail.level(), snail.blockPosition());
            crept = 0;
            if (spot == null) {
                snail.laidEggs();
                return;
            }
        }
        snail.getNavigation().stop();
        snail.face(at.x, at.z);
        snail.setDoing(SnailEntity.GRAZE);     // head down to the stone, laying
        if (++laid >= LAY_TICKS && snail.level() instanceof ServerLevel server) {
            SnailEggsBlock.lay(server, spot.pos(), spot.facing(), snail.getType(), snail.eggsToLay());
            snail.laidEggs();
        }
    }

    private static boolean within(SnailEntity snail, Vec3 at) {
        double dx = snail.getX() - at.x, dz = snail.getZ() - at.z;
        return dx * dx + dz * dz <= LAY_FROM * LAY_FROM && Math.abs(at.y - (snail.getY() + 0.5)) <= REACH_UP;
    }

    /** Where the snail stands to lay at this spot: in it if it has a floor, else in the water block under it. */
    static BlockPos standAt(Level level, BlockPos spot) {
        return floorUnder(level, spot) ? spot : spot.below();
    }

    private static boolean floorUnder(Level level, BlockPos p) {
        BlockPos below = p.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** A snail can reach it: a floor under it, or under the water block under it. */
    private static boolean reachable(Level level, BlockPos p) {
        if (floorUnder(level, p)) {
            return true;
        }
        BlockState below = level.getBlockState(p.below());
        return below.is(Blocks.WATER) && below.getFluidState().isSource() && floorUnder(level, p.below());
    }

    private static boolean stillFree(Level level, Spot s) {
        BlockState there = level.getBlockState(s.pos());
        return (there.isAir() || there.is(Blocks.WATER) && there.getFluidState().isSource())
                && SnailEggsBlock.sticksAt(level, s.pos(), s.facing());
    }

    /**
     * The best spot near {@code from}: just over the waterline on a side (air over still water, a sturdy side beside
     * it), else the top water block on a side, else any water on a side, else the bed under it; nearest first at each,
     * and only spots it can reach from a floor ({@link #reachable}).
     */
    @Nullable
    static Spot findSpot(Level level, BlockPos from) {
        Spot[] best = new Spot[3];
        double[] bestDistance = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                for (int dy = -UP_DOWN; dy <= UP_DOWN + 1; dy++) {
                    p.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                    int rank = rank(level, p);
                    if (rank < 0) {
                        continue;
                    }
                    double d = p.distSqr(from);
                    if (d >= bestDistance[rank] || !reachable(level, p)) {
                        continue;
                    }
                    Direction side = sideToStickTo(level, p);
                    if (side != null) {
                        best[rank] = new Spot(p.immutable(), side);
                        bestDistance[rank] = d;
                    }
                }
            }
        }
        for (Spot s : best) {
            if (s != null) {
                return s;
            }
        }
        return here(level, from);
    }

    /** 0 just over the waterline, 1 the top water, 2 deeper water, -1 no place for eggs. */
    private static int rank(Level level, BlockPos p) {
        BlockState there = level.getBlockState(p);
        boolean water = there.is(Blocks.WATER) && there.getFluidState().isSource();
        if (there.isAir()) {
            BlockState below = level.getBlockState(p.below());
            return below.is(Blocks.WATER) && below.getFluidState().isSource() ? 0 : -1;
        }
        if (!water) {
            return -1;
        }
        return level.getBlockState(p.above()).isAir() ? 1 : 2;
    }

    /** A side of this spot with a sturdy face to stick eggs to (the eggs facing away from it), or null. */
    @Nullable
    private static Direction sideToStickTo(Level level, BlockPos p) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            if (SnailEggsBlock.sticksAt(level, p, facing)) {
                return facing;
            }
        }
        return null;
    }

    /** The bed under the snail itself, eggs facing up, or null where it cannot stick them. */
    @Nullable
    private static Spot here(Level level, BlockPos at) {
        BlockState there = level.getBlockState(at);
        if ((there.isAir() || there.is(Blocks.WATER) && there.getFluidState().isSource()) && SnailEggsBlock.sticksAt(level, at, Direction.UP)) {
            return new Spot(at, Direction.UP);
        }
        return null;
    }
}
