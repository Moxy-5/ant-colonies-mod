package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.CellMap;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.LevelCellMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Idle wandering: now and then walks to a random nearby spot, through tunnels too. Underground some insects now and
 * then pick a spot on the ceiling instead and hang there upside down for a while, like real ants in their nest
 * ({@link #hangsFromCeilings}). An insect that stays in (termites by day, see {@link AbstractAntEntity#mayGoOut}) only
 * strolls inside; one that keeps to the rooms ({@link AbstractAntEntity#restsInRooms}) strolls only in them.
 */
public class AntWanderGoal extends Goal {
    private final AbstractAntEntity ant;
    private final double speed;
    /** On average one stroll starts every this many goal checks. */
    private final int interval;
    private final int radius;
    private final BooleanSupplier allowed;
    /** The chance (0 to 1) that a stroll underground ends on the ceiling instead of the floor. */
    private float ceilingChance;

    public AntWanderGoal(AbstractAntEntity ant, double speed, int interval, int radius, BooleanSupplier allowed) {
        this.allowed = allowed;
        this.ant = ant;
        this.speed = speed;
        this.interval = interval;
        this.radius = radius;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Underground, this share of strolls (0 to 1) goes up a wall and ends hanging from the ceiling. */
    public AntWanderGoal hangsFromCeilings(float chance) {
        this.ceilingChance = chance;
        return this;
    }

    @Override
    public boolean canUse() {
        if (ant.getRandom().nextInt(interval) != 0 || !allowed.getAsBoolean()) {
            return false;
        }
        CellMap map = new LevelCellMap(ant.level(), ant);
        BlockPos here = ant.blockPosition();
        int rest = ant.restsNearEntrance();
        Colony colony = ant.restsInRooms() ? ant.getColony() : null;
        java.util.Set<BlockPos> rooms = colony == null ? java.util.Set.of() : new java.util.HashSet<>(colony.nest().getAllRoomCells(ant.level()));
        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = ant.getRandom().nextInt(radius * 2 + 1) - radius;
            int dz = ant.getRandom().nextInt(radius * 2 + 1) - radius;
            BlockPos spot = findFloor(map, here.offset(dx, 0, dz));
            if (spot == null || !ant.mayBeAt(spot)) {
                continue; // by day a termite only strolls inside
            }
            if (rest > 0 && !restsAt(spot, here, rest)) {
                continue; // a harvester ant with nothing to do stays in, up by the entrance
            }
            if (!rooms.isEmpty() && !rooms.contains(spot)) {
                continue; // a Matabele ant stays in the rooms, with the queen
            }
            if (ceilingChance > 0.0F && ant.getRandom().nextFloat() < ceilingChance && !ant.level().canSeeSky(spot)
                    && Foothold.ceiling(spot).isValid(map)
                    && ant.getNavigator().moveTo(Foothold.ceiling(spot), speed)) {
                return true;
            }
            if (ant.getNavigator().moveTo(spot, speed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * For an insect that rests near the entrance ({@link AbstractAntEntity#restsNearEntrance}): a spot inside the nest,
     * within {@code rest} blocks of the entrance, or, for one deeper down, nearer to it than where it is now (it drifts up).
     */
    private boolean restsAt(BlockPos spot, BlockPos here, int rest) {
        Colony colony = ant.getColony();
        if (colony == null || !colony.nest().isInside(ant.level(), spot)) {
            return colony == null;
        }
        BlockPos entrance = colony.nest().getEntrance();
        double limit = (double) rest * rest;
        return spot.distSqr(entrance) <= limit || spot.distSqr(entrance) < here.distSqr(entrance);
    }

    @Override
    public boolean canContinueToUse() {
        return !ant.getNavigator().isDone();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
    }

    /** The first open cell with a floor in this column, looking a few blocks up and down from the ant's level. */
    @Nullable
    static BlockPos findFloor(CellMap map, BlockPos column) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = column.above(dy);
            if (map.isOpen(pos) && map.isSolidFace(pos.below(), Direction.UP)) {
                return pos;
            }
        }
        return null;
    }
}
