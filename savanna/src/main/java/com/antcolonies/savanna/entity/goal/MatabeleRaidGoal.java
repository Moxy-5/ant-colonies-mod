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
 * each raider a fight order at the attack, and this goal stands aside while one holds. Step 3c: after the fight the
 * column gives each raider with room a stung termite to fetch ({@code take}): it walks to it and picks it up; and a
 * raider that left the column hurt (crippled, a soldier clamped on a leg) limps home along the raid's trail with this
 * goal, the colony's raids saying where ({@code Raids.limpOrder}). Mind layer only.
 */
public class MatabeleRaidGoal extends Goal {
    /** The longest hop (blocks) it walks in one go. */
    private static final int HOP = 14;
    /** It walks again once its place moved this far from where it was walking (blocks). */
    private static final double MOVED = 1.5;
    /** It counts as at its place this near it (blocks). */
    private static final double AT = 1.2;
    /** ... and this near it up or down (blocks). */
    private static final double UP_DOWN = 1.5;
    /** After no way to its place it tries again this many ticks later. */
    private static final int RETRY = 20;

    private final MatabeleEntity ant;
    @Nullable
    private RaidColumn column;
    @Nullable
    private BlockPos walkingTo;
    private int waitUntil;
    /** Step 3c: whether it limps home along a raid's trail (else it walks in its column), and whether it stands picking a termite up. */
    private boolean limping;
    private boolean picking;

    public MatabeleRaidGoal(MatabeleEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    private Raids raids() {
        Colony colony = ant.getColony();
        return colony == null ? null : colony.part(SavannaParts.RAIDS).orElse(null);
    }

    @Nullable
    private RaidColumn myColumn() {
        Raids raids = raids();
        RaidColumn found = raids == null ? null : raids.column();
        return found != null && found.has(ant.getUUID()) && found.stage() != RaidColumn.Stage.DONE ? found : null;
    }

    /** Step 3c: whether it limps home from a raid by itself (hurt, out of the column). */
    private boolean limps() {
        Raids raids = raids();
        return raids != null && raids.isLimping(ant.getUUID());
    }

    @Override
    public boolean canUse() {
        if (ant.getFightSpot() != null || ant.isDowned() || !(ant.level() instanceof ServerLevel)) {
            return false;
        }
        column = ant.fightsNoMore() ? null : myColumn();
        limping = column == null && limps();
        return column != null || limping;
    }

    @Override
    public boolean canContinueToUse() {
        if (ant.getFightSpot() != null || ant.isDowned()) {
            return false;
        }
        return limping ? limps() : column != null && !ant.fightsNoMore() && column == myColumn();
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
        limping = false;
        walkingTo = null;
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level)) {
            return;
        }
        RaidColumn.Order order;
        if (limping) {
            Raids raids = raids();
            BlockPos to = raids == null ? null : raids.limpOrder(level, ant);
            if (to == null) {
                return; // (home: it is let go, and goes in as any ant)
            }
            order = new RaidColumn.Order(to, 1.0, null);
        } else {
            order = column == null ? null : column.orderFor(level, ant);
        }
        if (order == null) {
            return;
        }
        if (ant.tickCount < waitUntil && picking) {
            return; // (picking a termite up: it stands)
        }
        picking = false;
        if (order.lookAt() != null && ant.getNavigator().isDone()) {
            ant.getLookControl().setLookAt(order.lookAt().getX() + 0.5, order.lookAt().getY() + 0.3, order.lookAt().getZ() + 0.5);
        }
        BlockPos to = order.to();
        if (to == null) {
            ant.getNavigator().stop(); // the front waits for the line to close up
            walkingTo = null;
            return;
        }
        if (order.take() != null && column != null && level.getEntity(order.take()) instanceof net.minecraft.world.entity.Entity prey
                && prey.distanceTo(ant) <= RaidColumn.PICK_REACH) {
            // Step 3c: at its stung termite: it stops, takes it up in its jaws, and stands a moment.
            ant.getNavigator().stop();
            ant.getLookControl().setLookAt(prey);
            if (column.pickUp(level, ant, order.take())) {
                picking = true;
                waitUntil = ant.tickCount + column.pickTicks();
            }
            walkingTo = null;
            return;
        }
        // At its place counts in height too (R3, BUGS 87: a raider down a dead mound's chimney, right under its place by
        // the entrance, counted as there by the flat distance and stood still 8 blocks down).
        boolean atPlace = flat(ant.blockPosition(), to) <= AT && Math.abs(ant.getY() - to.getY()) <= UP_DOWN;
        boolean moved = walkingTo == null || flat(walkingTo, to) >= MOVED || Math.abs(walkingTo.getY() - to.getY()) > UP_DOWN;
        boolean idleAndOff = ant.getNavigator().isDone() && !atPlace;
        if (!moved && !idleAndOff || ant.tickCount < waitUntil) {
            return;
        }
        if (atPlace && ant.getNavigator().isDone() && order.take() == null) {
            walkingTo = to;
            return;
        }
        if (!walkToward(level, to, order.pace())) {
            waitUntil = ant.tickCount + RETRY; // no way now: a little later (BUGS 76: never searched every tick)
            if (order.take() != null && column != null) {
                column.noWayTo(level, ant, order.take()); // (another may reach it, or this one later)
            }
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
