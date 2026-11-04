package com.antcolonies.savanna.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.RaidColumn;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * M8 step 3b, a raider walking in its colony's raid column ({@link RaidColumn}). The column (the colony's brain) says
 * where this ant should be now: round the entrance while the raid forms, its place in the line on the march, bunched
 * up short of the termites, home again after the fight. This goal only walks there through the ant's navigator, hop by
 * hop for a far place, and faces where the column is heading. The fight itself is {@code FightGoal}'s: the column gives
 * each raider a fight order at the attack, and this goal stands aside while one holds. Mind layer only.
 */
public class MatabeleRaidGoal extends Goal {
    /** The longest hop (blocks) it walks in one go. */
    private static final int HOP = 14;
    /** It walks again once its place moved this far from where it was walking (blocks). */
    private static final double MOVED = 1.5;
    /** It counts as at its place this near it (blocks). */
    private static final double AT = 1.2;
    /** After no way to its place it tries again this many ticks later. */
    private static final int RETRY = 20;

    private final MatabeleEntity ant;
    @Nullable
    private RaidColumn column;
    @Nullable
    private BlockPos walkingTo;
    private int waitUntil;

    public MatabeleRaidGoal(MatabeleEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    private RaidColumn myColumn() {
        Colony colony = ant.getColony();
        Raids raids = colony == null ? null : colony.part(SavannaParts.RAIDS).orElse(null);
        RaidColumn found = raids == null ? null : raids.column();
        return found != null && found.has(ant.getUUID()) && found.stage() != RaidColumn.Stage.DONE ? found : null;
    }

    @Override
    public boolean canUse() {
        if (ant.getFightSpot() != null || ant.fightsNoMore() || ant.isDowned() || !(ant.level() instanceof ServerLevel)) {
            return false;
        }
        column = myColumn();
        return column != null;
    }

    @Override
    public boolean canContinueToUse() {
        return column != null && ant.getFightSpot() == null && !ant.fightsNoMore() && !ant.isDowned() && column == myColumn();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        walkingTo = null;
        waitUntil = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        column = null;
        walkingTo = null;
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level) || column == null) {
            return;
        }
        RaidColumn.Order order = column.orderFor(level, ant);
        if (order == null) {
            return;
        }
        if (order.lookAt() != null && ant.getNavigator().isDone()) {
            ant.getLookControl().setLookAt(order.lookAt().getX() + 0.5, order.lookAt().getY() + 0.3, order.lookAt().getZ() + 0.5);
        }
        BlockPos to = order.to();
        if (to == null) {
            ant.getNavigator().stop(); // the front waits for the line to close up
            walkingTo = null;
            return;
        }
        double off = flat(ant.blockPosition(), to);
        boolean moved = walkingTo == null || flat(walkingTo, to) >= MOVED;
        boolean idleAndOff = ant.getNavigator().isDone() && off > AT;
        if (!moved && !idleAndOff || ant.tickCount < waitUntil) {
            return;
        }
        if (off <= AT && ant.getNavigator().isDone()) {
            walkingTo = to;
            return;
        }
        if (!walkToward(level, to, order.pace())) {
            waitUntil = ant.tickCount + RETRY; // no way now: a little later (BUGS 76: never searched every tick)
        }
    }

    /** Straight to its place when near, else a hop of {@link #HOP} blocks its way; false if no way was found. */
    private boolean walkToward(ServerLevel level, BlockPos to, double pace) {
        BlockPos from = ant.blockPosition();
        double distance = flat(from, to);
        if (distance <= HOP) {
            if (ant.getNavigator().moveTo(to, pace)) {
                walkingTo = to;
                return true;
            }
            return false;
        }
        double share = HOP / distance;
        double x = from.getX() + 0.5 + (to.getX() - from.getX()) * share;
        double z = from.getZ() + 0.5 + (to.getZ() - from.getZ()) * share;
        int y = Mth.floor(Mth.lerp(share, from.getY(), to.getY()));
        BlockPos hop = RaidColumn.standAt(level, x, z, y);
        if (hop != null && ant.getNavigator().moveTo(hop, pace)) {
            walkingTo = to;
            return true;
        }
        return false;
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
