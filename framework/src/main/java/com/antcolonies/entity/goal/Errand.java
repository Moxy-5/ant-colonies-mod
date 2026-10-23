package com.antcolonies.entity.goal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.AntNavigator;
import com.antcolonies.nav.AntPathFinder;
import com.antcolonies.nav.Foothold;

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
 * <p>A goal keeps one errand, calls {@link #begin} when it starts and {@link #stop} when it stops. To walk:
 * {@link #setOff} starts a walk now; {@link #walkOn} keeps one going (and sets off again once a walk is over);
 * {@link #goTo} is {@code walkOn} to a thing, which with no way there is left alone ({@link #leaveAlone}) and the goal
 * gives up; {@link #setOffFor} sets off to one at once, the same way. A goal's search for work skips what is left alone ({@link #leftAlone}, {@link #isLeftAlone}).
 * To put a load down in a chamber, {@link #setOffToNearest} walks to the nearest free spot it can reach, with one route
 * search for all the spots.
 *
 * @param <K> what a thing found no way to is remembered by (a block, an entity's id)
 */
public final class Errand<K> {
    /** A thing this insect found no way to is left alone for this long (20 seconds), unless the goal says otherwise. */
    public static final int LEAVE_ALONE_TICKS = 400;
    /** Up or down, the body must be less than this far (in blocks) from a thing to reach it. */
    public static final double LEVEL = 1.5;
    /** After this many spots with no way to them in one errand, the insect gives up (see {@link #setOffTo}). */
    public static final int MOST_SPOTS = 4;

    private final AbstractAntEntity ant;
    /** How long a thing found no way to is left alone (ticks). */
    private final int leaveAloneTicks;
    /** Things found no way to: what -> the game time until which they are left alone. */
    private final Map<K, Long> noWay = new HashMap<>();
    /** Spots with no way to them in this errand (see {@link #setOffTo}). */
    private int spotsWithNoWay;
    /** Spots this errand found no good (no way there, or they could not take the load): the next search skips them. */
    private final Set<BlockPos> noGood = new HashSet<>();

    public Errand(AbstractAntEntity ant) {
        this(ant, LEAVE_ALONE_TICKS);
    }

    /** An errand whose things found no way to are left alone for {@code leaveAloneTicks} (a soldier's post: a minute). */
    public Errand(AbstractAntEntity ant, int leaveAloneTicks) {
        this.ant = ant;
        this.leaveAloneTicks = leaveAloneTicks;
    }

    /** A new errand starts (the goal's start). */
    public void begin() {
        spotsWithNoWay = 0;
        noGood.clear();
    }

    /**
     * Stops walking: the errand is over (the goal's stop), or the insect is at the thing and works on it there. An insect
     * on a wall keeps holding on where it is.
     */
    public void stop() {
        ant.getNavigator().stop();
    }

    // --- There yet? ---

    /** Whether the body is within {@code reach} blocks of the thing sideways, and less than {@link #LEVEL} up or down. */
    public boolean within(Entity thing, double reach) {
        return within(thing.getX(), thing.getY(), thing.getZ(), reach, LEVEL, LEVEL);
    }

    /**
     * Whether the body is within {@code reach} blocks of the middle of the spot's cell sideways, and level with its floor
     * (see {@link #LEVEL}). False with no spot.
     */
    public boolean within(@Nullable BlockPos spot, double reach) {
        return spot != null && within(spot, reach, LEVEL, LEVEL);
    }

    /**
     * Like {@link #within(BlockPos, double)}, with the body allowed less than {@code below} blocks under the spot's floor
     * and less than {@code above} over it (a pellet goes onto a hill from a step lower or higher).
     */
    public boolean within(BlockPos spot, double reach, double below, double above) {
        return within(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, reach, below, above);
    }

    private boolean within(double x, double y, double z, double reach, double below, double above) {
        double dx = ant.getX() - x;
        double dz = ant.getZ() - z;
        double dy = ant.getY() - y;
        return dx * dx + dz * dz <= reach * reach && dy > -below && dy < above;
    }

    /** Whether the body is in this very cell (packing a hole from under it, scraping soil in the shaft). */
    public boolean isIn(BlockPos cell) {
        return ant.blockPosition().equals(cell);
    }

    // --- Walking there ---

    /** Whether the legs are still walking the last route (the walk is not over: not arrived, stopped or failed). */
    public boolean isWalking() {
        return !ant.getNavigator().isDone();
    }

    /** Sets off to this cell now, whatever it was doing. False with no cell, or no way there from here. */
    public boolean setOff(@Nullable BlockPos cell, double speed) {
        return cell != null && ant.getNavigator().moveTo(cell, speed);
    }

    /** Like {@link #setOff(BlockPos, double)}, with the mind's own step costs (never through the hole it is about to close). */
    public boolean setOff(BlockPos cell, double speed, AntPathFinder.StepCosts costs) {
        return ant.getNavigator().moveTo(cell, speed, costs);
    }

    /** Sets off now to any foothold {@code where} accepts (somewhere it can work on a thing from), searching towards {@code towards}. */
    public boolean setOff(Predicate<Foothold> where, BlockPos towards, double speed) {
        return ant.getNavigator().moveTo(where, towards, speed);
    }

    /**
     * Like {@link #setOff(Predicate, BlockPos, double)}, where {@code where} accepts no foothold more than {@code reach}
     * blocks from {@code towards} each way: with none there it fails at once, without a route search.
     */
    public boolean setOff(Predicate<Foothold> where, BlockPos towards, int reach, double speed) {
        return ant.getNavigator().moveTo(where, towards, reach, speed);
    }

    /**
     * One tick toward this cell: while a walk is under way it walks on; once it is over (arrived but still not there, or
     * knocked off) it sets off again. False if there is no way there from here.
     */
    public boolean walkOn(BlockPos cell, double speed) {
        return isWalking() || setOff(cell, speed);
    }

    /** Like {@link #walkOn(BlockPos, double)}, with the mind's own step costs. */
    public boolean walkOn(BlockPos cell, double speed, AntPathFinder.StepCosts costs) {
        return isWalking() || setOff(cell, speed, costs);
    }

    /**
     * Like {@link #walkOn(BlockPos, double)}, to the nearest of these cells there is a way to (spots to stand at to reach
     * a thing), with one route search for them all.
     */
    public boolean walkOn(Collection<BlockPos> cells, double speed) {
        return isWalking() || ant.getNavigator().moveTo(cells, speed);
    }

    /** Like {@link #walkOn(BlockPos, double)}, to any foothold {@code where} accepts. */
    public boolean walkOn(Predicate<Foothold> where, BlockPos towards, double speed) {
        return isWalking() || setOff(where, towards, speed);
    }

    /**
     * {@link #walkOn(BlockPos, double)} toward a thing in this cell. False if there is no way there from here: then the
     * thing is left alone for a while ({@link #leftAlone}), and the goal gives up.
     */
    public boolean goTo(K thing, BlockPos cell, double speed) {
        return walkOn(cell, speed) || leaveAlone(thing);
    }

    /** Like {@link #goTo(Object, BlockPos, double)}, to the nearest of these cells there is a way to. */
    public boolean goTo(K thing, Collection<BlockPos> cells, double speed) {
        return walkOn(cells, speed) || leaveAlone(thing);
    }

    /** Like {@link #goTo(Object, BlockPos, double)}, to any foothold {@code where} accepts. */
    public boolean goTo(K thing, Predicate<Foothold> where, BlockPos towards, double speed) {
        return walkOn(where, towards, speed) || leaveAlone(thing);
    }

    /**
     * Sets off now to a thing in this cell, unless it is left alone; with no way there it is left alone for a while. False
     * if it did not set off (a goal that picks its work and sets off in {@code canUse}: the queen down to her room).
     */
    public boolean setOffFor(K thing, BlockPos cell, double speed) {
        return !isLeftAlone(thing) && (setOff(cell, speed) || leaveAlone(thing));
    }

    /**
     * Sets off to a spot (in a chamber, say) at once: to {@code spot}, or, with no way there, to another from
     * {@code another} (which gives null when there is none left), until one has a way. After {@link #MOST_SPOTS} spots
     * with no way in this errand it gives up. The spot it set off to, or null (none left, or it gave up).
     */
    @Nullable
    public BlockPos setOffTo(@Nullable BlockPos spot, Supplier<BlockPos> another, double speed) {
        while (spot != null && !setOff(spot, speed)) {
            if (++spotsWithNoWay >= MOST_SPOTS) {
                return null;
            }
            spot = another.get();
        }
        return spot;
    }

    /**
     * Sets off at once to a spot to put a load down (a free cell of a chamber): to {@code spot} if there is one and a way
     * there, otherwise to the nearest of {@code spots} there is a way to, with one route search for them all (a real ant
     * puts its load down in the first free place it comes to; trying spots one after another cost a search each, and a
     * whole search each when the chamber was cut off). Spots this errand found no good ({@link #notThere}) are skipped.
     * The spot it set off to, or null (no way to any of them, or none left).
     */
    @Nullable
    public BlockPos setOffToNearest(@Nullable BlockPos spot, Supplier<? extends Collection<BlockPos>> spots, double speed) {
        if (spot != null) {
            if (setOff(spot, speed)) {
                return spot;
            }
            notThere(spot);
        }
        List<BlockPos> left = new ArrayList<>();
        for (BlockPos cell : spots.get()) {
            if (!noGood.contains(cell)) {
                left.add(cell);
            }
        }
        if (ant.getNavigator().moveTo(left, speed)) {
            return ant.getNavigator().getGoal();
        }
        if (!left.isEmpty()) {
            spotsWithNoWay++;
        }
        return null;
    }

    /** This spot is no good for this errand (no way there, or it could not take the load): {@link #setOffToNearest} skips it. */
    public void notThere(BlockPos spot) {
        noGood.add(spot.immutable());
        spotsWithNoWay++;
    }

    /** How many spots had no way to them (or could not take the load) in this errand (for the goals' log lines). */
    public int spotsWithNoWay() {
        return spotsWithNoWay;
    }

    // --- Things found no way to ---

    /** There is no way to this thing: it is left alone for a while. Always false (so a goal can write {@code walkOn(..) || leaveAlone(..)}). */
    public boolean leaveAlone(K thing) {
        noWay.put(thing, ant.level().getGameTime() + leaveAloneTicks);
        return false;
    }

    /** The things left alone now, for the goal's search for work to skip (those whose time is up are forgotten first). */
    public Set<K> leftAlone() {
        long now = ant.level().getGameTime();
        noWay.values().removeIf(until -> until <= now || until > now + leaveAloneTicks);
        return noWay.keySet();
    }

    /** Whether this thing is left alone now (this insect found no way to it a little while ago). */
    public boolean isLeftAlone(K thing) {
        return leftAlone().contains(thing);
    }
}
