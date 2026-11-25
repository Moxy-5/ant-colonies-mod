package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import com.antcolonies.colony.GroundTop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A big land animal's way to its stand (lifted out of the Cape buffalo's day when the lions came, 2026-11-25,
 * {@code docs/plans/lion.md}): Minecraft's own routes, a trip longer than a route's search walked in legs of {@link #LEG}
 * (each leg's end on dry ground, turned round water lying across the way), the last {@link #STRAIGHT} blocks straight to
 * it with no route (Minecraft's route-follower calls a short route done while a 2-wide body is still about 2 blocks off:
 * measured 2026-11-25, 2.1 to 2.6 off). One step a tick says how it went; what to do about a place it cannot reach or land
 * not loaded is its owner's.
 */
public final class Trip {
    /** How a tick on the way went. */
    public enum Step {
        ON_WAY, ARRIVED, CANNOT_REACH, UNLOADED
    }

    /** A trip longer than this goes in legs this long, each its own route (blocks; well inside a route's search). */
    public static final double LEG = 20.0;
    /**
     * Where a leg may end, tried in order until one is on dry ground: {degrees turned off the way to its stand, part of a
     * leg}; the first is straight on (the way round a pan lying across its way).
     */
    private static final double[][] LEG_TRIES = {{0, 1}, {30, 1}, {-30, 1}, {60, 1}, {-60, 1}, {0, 0.5}, {30, 0.5}, {-30, 0.5},
            {60, 0.5}, {-60, 0.5}};
    /** A route that brought it less than this much nearer has failed (blocks). */
    public static final double HEADWAY = 2.0;
    /** This near its stand it walks straight to it, no route (blocks); bumping into something this many ticks running, it cannot reach it. */
    public static final double STRAIGHT = 4.0;
    static final int BUMP_LIMIT = 40;

    @Nullable
    private BlockPos stand;
    /** Its route now (a leg, or its stand), and how far it was from its stand when that route began. */
    @Nullable
    private BlockPos routeTo;
    private double routeFrom;
    private int bumped;

    /** Sets off for a new stand. */
    public void to(BlockPos stand) {
        this.stand = stand;
        routeTo = null;
        bumped = 0;
    }

    /** Drops its route (a new route is looked for at the next step). */
    public void drop() {
        routeTo = null;
    }

    @Nullable
    public BlockPos stand() {
        return stand;
    }

    /** Whether it is on a route to its stand itself (not a leg). */
    public boolean routedToStand() {
        return routeTo == null || routeTo.equals(stand);
    }

    /**
     * One tick on its way at this pace: arrived when within {@code arrive} of its stand, or within {@code arriveDone} with
     * its last route done; a route that brought it less than {@link #HEADWAY} nearer, or bumping into something on the
     * last straight blocks, cannot reach it; a leg's end in land not loaded: unloaded.
     */
    public Step step(PathfinderMob mob, double pace, double arrive, double arriveDone) {
        PathNavigation nav = mob.getNavigation();
        double dist = HomeGroup.flat(mob.position(), stand);
        if (dist <= arrive || dist <= arriveDone && nav.isDone() && routedToStand()) {
            nav.stop();
            routeTo = null;
            return Step.ARRIVED;
        }
        if (dist <= STRAIGHT) {
            if (routeTo != null) {
                nav.stop();
                routeTo = null;
            }
            mob.getMoveControl().setWantedPosition(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, pace);
            bumped = mob.horizontalCollision ? bumped + 1 : 0;
            if (bumped > BUMP_LIMIT) {
                bumped = 0;
                return Step.CANNOT_REACH;               // something in the way of the last few blocks
            }
            return Step.ON_WAY;
        }
        if (routeTo == null || nav.isDone()) {
            if (routeTo != null && dist > routeFrom - HEADWAY) {
                routeTo = null;
                return Step.CANNOT_REACH;
            }
            BlockPos to = dist > LEG ? legPoint(mob, stand, dist) : stand;
            if (to == null) {
                return Step.UNLOADED;
            }
            routeTo = to;
            routeFrom = dist;
            if (!nav.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, pace)) {
                routeTo = null;
                return Step.CANNOT_REACH;
            }
            return Step.ON_WAY;
        }
        if (mob.tickCount % 20 == 0) {
            nav.setSpeedModifier(pace);
        }
        return Step.ON_WAY;
    }

    /**
     * Its next leg: {@link #LEG} toward its stand, on dry ground there; null if that land is not loaded. A point in water
     * ({@link GroundTop} counts water as ground) is turned round it: {@link #LEG_TRIES} in order, each still bringing it
     * well nearer its stand; with none dry, the straight one (it wades).
     */
    @Nullable
    static BlockPos legPoint(PathfinderMob mob, BlockPos stand, double dist) {
        Level level = mob.level();
        double ux = (stand.getX() + 0.5 - mob.getX()) / dist, uz = (stand.getZ() + 0.5 - mob.getZ()) / dist;
        BlockPos straight = null;
        for (double[] t : LEG_TRIES) {
            double a = Math.toRadians(t[0]), far = LEG * t[1], cos = Math.cos(a), sin = Math.sin(a);
            double x = mob.getX() + (ux * cos - uz * sin) * far, z = mob.getZ() + (ux * sin + uz * cos) * far;
            BlockPos column = BlockPos.containing(x, mob.getY(), z);
            if (!level.hasChunkAt(column)) {
                if (straight == null) {
                    return null;                        // the way straight on is not loaded: it waits
                }
                continue;
            }
            BlockPos cell = GroundTop.cell(level, column);
            if (straight == null) {
                straight = cell;
            }
            if (HomeGroup.flat(Vec3.atBottomCenterOf(cell), stand) > dist - HEADWAY - 2.0) {
                continue;                               // too little nearer: its route would count as failed
            }
            if (level.getFluidState(cell).isEmpty() && level.getFluidState(cell.below()).isEmpty()) {
                return cell;
            }
        }
        return straight;
    }
}
