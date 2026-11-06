package com.antcolonies.nav;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.antcolonies.debug.Perf;
import com.antcolonies.entity.AbstractAntEntity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Real sizes, step 3, personal space ({@code docs/plans/real-sizes.md}): where the insects stood last tick, in a small
 * grid of one block a cell, so the legs ({@link AntNavigator}) can keep a body from walking or standing inside another
 * without asking every insect about every other. Not Minecraft's pushing (costly with many mobs, it fights the climbing
 * bodies and would jam the one-block tunnels): each insect's own legs step aside, wait or pick a free spot.
 *
 * <p>Every insect with a real size ({@link #keepsSpace}) writes itself in once a tick while it is on a floor (not on a
 * wall, a ceiling or in a channel, where it moves exactly and others squeeze past it); the others read last tick's
 * grid, so the order the insects tick in never matters. One grid per world, on the server thread only. Species-neutral:
 * any body with a size signed up ({@link com.antcolonies.entity.body.BodySizes}) keeps its space; the red harvester ant
 * (no size until its rework) walks as before.
 */
public final class Crowd {
    /** One body as it stood last tick: where, how big (its personal radius, its height), how it moved, whether it stood. */
    public record Body(AbstractAntEntity ant, double x, double y, double z, double radius, double height, double vx, double vz,
            boolean standing, long standSince) {
    }

    /**
     * A body's personal radius is this share of its length (my pick: two bodies keep their middles 0.8 of a body length
     * apart, so ants nose to tail in a column just touch, as in a real Megaponera column, and side by side keep a
     * small gap). Never less than half its box.
     */
    private static final double RADIUS_SHARE = 0.4;
    /** Bodies more than this far apart up or down (blocks) are on different floors. */
    public static final double SAME_FLOOR = 0.5;
    /**
     * Two bodies whose middles are closer than this share of their personal distance stand inside one another (the
     * tests and the raid test bench ask; my pick: a third of a body length into the other).
     */
    public static final double INSIDE = 0.6;

    private static final Map<Level, Crowd> CROWDS = new WeakHashMap<>();

    private Long2ObjectOpenHashMap<List<Body>> reading = new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<List<Body>> writing = new Long2ObjectOpenHashMap<>();
    private long writingTick = Long.MIN_VALUE;
    private final List<Body> found = new ArrayList<>();

    private Crowd() {
    }

    /** The grid of this world (made on first use). */
    public static Crowd of(Level level) {
        return CROWDS.computeIfAbsent(level, l -> new Crowd());
    }

    /** Whether this insect keeps its personal space: any body with a real size signed up. */
    public static boolean keepsSpace(AbstractAntEntity ant) {
        return ant.bodyLength() > 0.0;
    }

    /** Its personal radius (blocks): {@link #RADIUS_SHARE} of its length, at least half its box. */
    public static double radius(AbstractAntEntity ant) {
        return Math.max(ant.getBbWidth() / 2.0, RADIUS_SHARE * ant.bodyLength());
    }

    /**
     * Whether two insects stand inside one another now: on one floor, their middles closer than {@link #INSIDE} of their
     * personal distance (for the tests and the rules; the legs keep them a whole personal distance apart where they can).
     */
    public static boolean inside(AbstractAntEntity a, AbstractAntEntity b) {
        if (Math.abs(a.getY() - b.getY()) > SAME_FLOOR) {
            return false;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        double need = (radius(a) + radius(b)) * INSIDE;
        return dx * dx + dz * dz < need * need;
    }

    /** Writes a body in for this tick (read by the others from the next tick on). */
    void note(Body body, long now) {
        boolean timed = Perf.begin(Perf.Part.CROWD);
        try {
            roll(now);
            writing.computeIfAbsent(key(body.x(), body.y(), body.z()), k -> new ArrayList<>(2)).add(body);
        } finally {
            if (timed) {
                Perf.end();
            }
        }
    }

    /**
     * The bodies that stood within {@code reach} blocks sideways of the point last tick, on its floor or within
     * {@link #SAME_FLOOR} up or down, other than {@code self}. The list is reused by the next call: read it at once.
     */
    List<Body> near(AbstractAntEntity self, double x, double y, double z, double reach, long now) {
        boolean timed = Perf.begin(Perf.Part.CROWD);
        try {
            return lookUp(self, x, y, z, reach, now);
        } finally {
            if (timed) {
                Perf.end();
            }
        }
    }

    private List<Body> lookUp(AbstractAntEntity self, double x, double y, double z, double reach, long now) {
        roll(now);
        found.clear();
        if (reading.isEmpty()) {
            return found;
        }
        int r = Mth.ceil(reach);
        int bx = Mth.floor(x);
        int by = Mth.floor(y);
        int bz = Mth.floor(z);
        double most = reach * reach;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    List<Body> bodies = reading.get(BlockPos.asLong(bx + dx, by + dy, bz + dz));
                    if (bodies == null) {
                        continue;
                    }
                    for (Body body : bodies) {
                        if (body.ant() == self || Math.abs(body.y() - y) > SAME_FLOOR) {
                            continue;
                        }
                        double ox = body.x() - x;
                        double oz = body.z() - z;
                        if (ox * ox + oz * oz <= most) {
                            found.add(body);
                        }
                    }
                }
            }
        }
        return found;
    }

    /** The nearest gap (blocks) between this point and a standing body's personal circle, for a body of radius {@code radius}; negative inside one. */
    double clearance(AbstractAntEntity self, Vec3 point, double radius, long now) {
        boolean timed = Perf.begin(Perf.Part.CROWD);
        try {
            return gapTo(self, point, radius, now);
        } finally {
            if (timed) {
                Perf.end();
            }
        }
    }

    private double gapTo(AbstractAntEntity self, Vec3 point, double radius, long now) {
        double best = Double.MAX_VALUE;
        for (Body body : lookUp(self, point.x, point.y, point.z, radius + 0.6, now)) {
            if (!body.standing()) {
                continue;
            }
            double gap = Math.sqrt(sq(body.x() - point.x) + sq(body.z() - point.z)) - radius - body.radius();
            best = Math.min(best, gap);
        }
        return best;
    }

    /** A new tick: last tick's writing is read now (if it was last tick's: a gap of a tick or more reads as nobody). */
    private void roll(long now) {
        if (now == writingTick) {
            return;
        }
        Long2ObjectOpenHashMap<List<Body>> old = reading;
        reading = writingTick == now - 1 ? writing : new Long2ObjectOpenHashMap<>();
        old.clear();
        writing = old;
        writingTick = now;
    }

    private static long key(double x, double y, double z) {
        return BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    private static double sq(double v) {
        return v * v;
    }
}
