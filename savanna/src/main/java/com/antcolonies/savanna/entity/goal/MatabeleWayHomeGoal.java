package com.antcolonies.savanna.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.RaidGround;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * An ant going home from the field by itself (M8 step 3c and the raid code review, finding 2): a real Megaponera that
 * loses legs, has a termite clinging or falls behind makes its own way home on the scent line. Here: a raider that left
 * its raid hurt, one a stage's deadline let go, a scout that gave up, an ant found outside with nothing to do. The
 * colony's raids say where ({@code Raids.wayHomeOrder}: a little ahead of it along a trail, or the entrance); this goal
 * walks there hop by hop, and alone out there it tries harder for a way than a raider in its column does (a shorter
 * hop, hops slanting off to either side, any floor nearer home: BUGS 121, 140). Until the raid rewrite's part c this
 * was a second job of {@link MatabeleRaidGoal}. Mind layer only.
 */
public class MatabeleWayHomeGoal extends Goal {
    /** The longest hop (blocks) it walks in one go. */
    private static final int HOP = 14;
    /** It walks again once its place moved this far from where it was walking (blocks). */
    private static final double MOVED = 1.5;
    /** It counts as at its place this near it (blocks), and this near it up or down. */
    private static final double AT = 1.2;
    private static final double UP_DOWN = 1.5;
    /**
     * After no way at all it tries again this many ticks later (5 s: it tried five ways; B7: a stuck one costs a few
     * searches every 5 s, and is folded in out of sight after minutes; BUGS 76: never searched every tick).
     */
    private static final int RETRY = 100;
    /** BUGS 140: on a trail, after this many tries in a row with no way it leaves the trail for the entrance (my pick: 3, 15 s). */
    private static final int LEAVE_TRAIL_AFTER = 3;
    /** B7: the turns (radians) a blocked hop tries, after a half hop straight: 45 degrees, then 90, each side. */
    private static final double[] SIDE_TURNS = {0.0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2};

    private final MatabeleEntity ant;
    @Nullable
    private BlockPos walkingTo;
    private int waitUntil;
    /** The tries in a row that found no way. */
    private int fails;
    private final Headway headway = new Headway();

    public MatabeleWayHomeGoal(MatabeleEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    private Raids raids() {
        Colony colony = ant.getColony();
        return colony == null ? null : colony.part(SavannaParts.RAIDS).orElse(null);
    }

    /** Sent home by itself, and not walking in a raid (a raider with fight left in it walks with its raid first). */
    private boolean goesHome() {
        Raids raids = raids();
        return raids != null && raids.isGoingHome(ant.getUUID()) && (ant.fightsNoMore() || !raids.isRaider(ant.getUUID()));
    }

    @Override
    public boolean canUse() {
        return ant.getFightSpot() == null && !ant.isDowned() && ant.level() instanceof ServerLevel && goesHome();
    }

    @Override
    public boolean canContinueToUse() {
        return ant.getFightSpot() == null && !ant.isDowned() && goesHome();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        walkingTo = null;
        waitUntil = 0;
        fails = 0;
        headway.reset();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        walkingTo = null;
    }

    @Override
    public void tick() {
        Raids raids = raids();
        if (raids == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos to = raids.wayHomeOrder(level, ant);
        if (to == null) {
            return; // (home: it is let go, and goes in as any ant)
        }
        if (headway.lost(ant)) {
            ant.getNavigator().stop();
            walkingTo = to;
            waitUntil = ant.tickCount + RETRY;
            fails++;
            return;
        }
        // (at its place counts in height too, BUGS 87; one standing where it last walked to stays while its place shifts
        // less than MOVED)
        boolean atPlace = flat(ant.blockPosition(), to) <= AT && Math.abs(ant.getY() - to.getY()) <= UP_DOWN;
        boolean moved = walkingTo == null || flat(walkingTo, to) >= MOVED || Math.abs(walkingTo.getY() - to.getY()) > UP_DOWN;
        boolean settled = walkingTo != null && flat(ant.blockPosition(), walkingTo) <= AT
                && Math.abs(ant.getY() - walkingTo.getY()) <= UP_DOWN;
        boolean idleAndOff = ant.getNavigator().isDone() && !atPlace && !settled;
        if (!moved && !idleAndOff || ant.tickCount < waitUntil) {
            return;
        }
        if (atPlace && ant.getNavigator().isDone()) {
            walkingTo = to;
            return;
        }
        boolean walked = walkToward(level, to);
        fails = walked ? 0 : fails + 1;
        if (fails >= LEAVE_TRAIL_AFTER && raids.leaveTrailHome(level, ant)) {
            fails = 0;
            return; // (the entrance next tick, hop by hop)
        }
        if (!walked) {
            waitUntil = ant.tickCount + RETRY;
        }
    }

    /**
     * Straight to its place when near, else a hop of {@link #HOP} blocks its way. With no way (a cliff, a pond, a thorn
     * tree's trunk in the way: a scout far from home once stood for hours where every straight hop home failed, BUGS
     * 121) it tries a shorter hop, then hops slanting off to either side; last, the legs themselves look for any floor
     * some blocks nearer home (BUGS 140: on a ravine's lip by a lake every hop's landing lay in the water, over the drop
     * or inside the hill): round the lake, over the hill, whatever way there is. False if no way was found.
     */
    private boolean walkToward(ServerLevel level, BlockPos to) {
        BlockPos from = ant.blockPosition();
        double distance = flat(from, to);
        if (distance <= HOP ? ant.getNavigator().moveTo(to, 1.0) : hop(level, from, to, HOP, 0.0)) {
            walkingTo = to;
            return true;
        }
        for (double turn : SIDE_TURNS) {
            if (hop(level, from, to, turn == 0.0 ? HOP / 2.0 : HOP * 0.7, turn)) {
                walkingTo = to;
                return true;
            }
        }
        double nearer = Math.max(2.0, Math.min(HOP / 2.0, distance - AT));
        if (ant.getNavigator().moveTo(hold -> hold.isFloor() && flat(hold.cell(), to) <= distance - nearer, to, 1.0)) {
            walkingTo = to;
            return true;
        }
        return false;
    }

    private boolean hop(ServerLevel level, BlockPos from, BlockPos to, double length, double turn) {
        BlockPos cell = RaidGround.hopCell(level, from, to, length, turn);
        return cell != null && ant.getNavigator().moveTo(cell, 1.0);
    }

    private static double flat(BlockPos a, BlockPos b) {
        return RaidGround.flat(a, b);
    }
}
