package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.CellMap;
import com.antcolonies.nav.LevelCellMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;

/** Idle wandering: now and then walks to a random nearby spot, through tunnels too. */
public class AntWanderGoal extends Goal {
    private final AbstractAntEntity ant;
    private final double speed;
    /** On average one stroll starts every this many goal checks. */
    private final int interval;
    private final int radius;
    private final BooleanSupplier allowed;

    public AntWanderGoal(AbstractAntEntity ant, double speed, int interval, int radius, BooleanSupplier allowed) {
        this.allowed = allowed;
        this.ant = ant;
        this.speed = speed;
        this.interval = interval;
        this.radius = radius;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (ant.getRandom().nextInt(interval) != 0 || !allowed.getAsBoolean()) {
            return false;
        }
        CellMap map = new LevelCellMap(ant.level());
        BlockPos here = ant.blockPosition();
        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = ant.getRandom().nextInt(radius * 2 + 1) - radius;
            int dz = ant.getRandom().nextInt(radius * 2 + 1) - radius;
            BlockPos spot = findFloor(map, here.offset(dx, 0, dz));
            if (spot != null && ant.getNavigator().moveTo(spot, speed)) {
                return true;
            }
        }
        return false;
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
    private static BlockPos findFloor(CellMap map, BlockPos column) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = column.above(dy);
            if (map.isOpen(pos) && map.isSolidFace(pos.below(), Direction.UP)) {
                return pos;
            }
        }
        return null;
    }
}
