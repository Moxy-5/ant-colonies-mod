package com.antcolonies.entity.goal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.AntNavigator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * The walking part of a job, shared by every goal that walks to work: to a seed, a body, a block to dig, a spot in a
 * chamber. The goal says what it goes to and what counts as there; the errand walks there with the insect's legs
 * ({@link AntNavigator}), plans a new route only once the last walk is over, and when there is no way to a thing
 * remembers it for a while ({@link #LEAVE_ALONE_TICKS}), so a cut-off insect does not search for a route again and
 * again (docs/BUGS.md 42: a goal without that memory cost a route search a second, up to 29 ms a tick). Every goal used
 * to have its own copy of these lines (docs/plans/errand-helper.md).
 *
 * <p>Mind layer: it only tells the legs "go to this cell", never steers the body. A smarter mind can use it as its
 * "act" step: go there, get there, or learn that there is no way.
 *
 * <p>A goal keeps one errand, calls {@link #begin} when it starts and {@link #stop} when it stops.
 *
 * @param <K> what a thing found no way to is remembered by (a block, an entity's id)
 */
public final class Errand<K> {
    /** A thing this insect found no way to is left alone for this long (20 seconds). */
    public static final int LEAVE_ALONE_TICKS = 400;
    /** Up or down, the body must be less than this far (in blocks) from a thing to reach it. */
    public static final double LEVEL = 1.5;
    /** After this many spots with no way to them in one errand, the insect gives up (see {@link #setOffTo}). */
    public static final int MOST_SPOTS = 4;

    private final AbstractAntEntity ant;
    /** Things found no way to: what -> the game time until which they are left alone. */
    private final Map<K, Long> noWay = new HashMap<>();
    /** Spots with no way to them in this errand (see {@link #setOffTo}). */
    private int spotsWithNoWay;

    public Errand(AbstractAntEntity ant) {
        this.ant = ant;
    }

    /** A new errand starts (the goal's start). */
    public void begin() {
        spotsWithNoWay = 0;
    }

    /**
     * Stops walking: the errand is over (the goal's stop), or the insect is at the thing and works on it there. An insect
     * on a wall keeps holding on where it is.
     */
    public void stop() {
        ant.getNavigator().stop();
    }

    // --- Within reach of a thing ---

    /** Whether the body is within {@code reach} blocks of the thing sideways, and less than {@link #LEVEL} up or down. */
    public boolean within(Entity thing, double reach) {
        return within(thing.getX(), thing.getY(), thing.getZ(), reach);
    }

    /** Whether the body is within {@code reach} blocks of the middle of the spot's cell sideways, and level with its floor (see {@link #LEVEL}). */
    public boolean within(BlockPos spot, double reach) {
        return within(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, reach);
    }

    private boolean within(double x, double y, double z, double reach) {
        double dx = ant.getX() - x;
        double dz = ant.getZ() - z;
        return dx * dx + dz * dz <= reach * reach && Math.abs(ant.getY() - y) < LEVEL;
    }

    // --- Walking there ---

    /** Whether the legs are still walking the last route (the walk is not over: not arrived, stopped or failed). */
    public boolean isWalking() {
        return !ant.getNavigator().isDone();
    }

    /**
     * One tick toward a thing in this cell: while a walk is under way it walks on; once it is over (arrived but still not
     * there, or knocked off) it sets off again. False if there is no way there from here: then the thing is left alone
     * for a while ({@link #leftAlone}), and the goal gives up.
     */
    public boolean goTo(K thing, BlockPos cell, double speed) {
        return goTo(thing, List.of(cell), speed);
    }

    /** Like {@link #goTo(Object, BlockPos, double)}, to the first of these cells there is a way to (spots to stand at to reach the thing). */
    public boolean goTo(K thing, List<BlockPos> cells, double speed) {
        AntNavigator legs = ant.getNavigator();
        if (!legs.isDone()) {
            return true;
        }
        for (BlockPos cell : cells) {
            if (legs.moveTo(cell, speed)) {
                return true;
            }
        }
        noWay.put(thing, ant.level().getGameTime() + LEAVE_ALONE_TICKS);
        return false;
    }

    /**
     * Sets off to a spot (in a chamber, say) at once: to {@code spot}, or, with no way there, to another from
     * {@code another} (which gives null when there is none left), until one has a way. After {@link #MOST_SPOTS} spots
     * with no way in this errand it gives up. The spot it set off to, or null (none left, or it gave up).
     */
    @Nullable
    public BlockPos setOffTo(@Nullable BlockPos spot, Supplier<BlockPos> another, double speed) {
        while (spot != null && !ant.getNavigator().moveTo(spot, speed)) {
            if (++spotsWithNoWay >= MOST_SPOTS) {
                return null;
            }
            spot = another.get();
        }
        return spot;
    }

    // --- Things found no way to ---

    /** The things left alone now, for the goal's search for work to skip (those whose time is up are forgotten first). */
    public Set<K> leftAlone() {
        long now = ant.level().getGameTime();
        noWay.values().removeIf(until -> until <= now || until > now + LEAVE_ALONE_TICKS);
        return noWay.keySet();
    }

    /** Whether this thing is left alone now (this insect found no way to it a little while ago). */
    public boolean isLeftAlone(K thing) {
        return leftAlone().contains(thing);
    }
}
