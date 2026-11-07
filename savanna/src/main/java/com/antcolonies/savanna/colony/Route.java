package com.antcolonies.savanna.colony;

import java.util.List;

import com.antcolonies.colony.GroundTop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * A line of points (block middles) with the distance walked along it to each (blocks, flat): a raid column's way out
 * and home along its scent trail ({@link RaidColumn}), and an ant's walk home along a trail by itself ({@link WayHome}).
 * Taken out of {@code RaidColumn} in the raid tidy-up (2026-11-07; R9's "{@code Route} in its own file"), nothing
 * changed in it: ready to move into the framework with the trail store (R10).
 */
final class Route {
    final List<BlockPos> points;
    private final double[] xs;
    private final double[] zs;
    private final double[] ys;
    private final double[] sums;
    /** The distances walked to each point, climbs and drops counted (the raid queue fix). */
    private final double[] walks;

    Route(List<BlockPos> points) {
        this.points = List.copyOf(points);
        int n = points.size();
        xs = new double[n];
        zs = new double[n];
        ys = new double[n];
        sums = new double[n];
        walks = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i).getX() + 0.5;
            zs[i] = points.get(i).getZ() + 0.5;
            ys[i] = points.get(i).getY();
            sums[i] = i == 0 ? 0.0 : sums[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            walks[i] = i == 0 ? 0.0 : walks[i - 1] + Math.hypot(sums[i] - sums[i - 1], ys[i] - ys[i - 1]);
        }
    }

    /** The cell to stand in at this place along the line, {@code side} blocks to its right; the line's own point if none. */
    BlockPos place(ServerLevel level, double along, double side) {
        double[] p = at(along);
        if (side != 0.0) {
            double[] d = direction(along);
            BlockPos beside = RaidGround.standAt(level, p[0] - d[1] * side, p[1] + d[0] * side, (int) Math.round(p[2]));
            if (beside != null) {
                return beside;
            }
        }
        BlockPos on = RaidGround.standAt(level, p[0], p[1], (int) Math.round(p[2]));
        if (on == null) {
            // (nothing near the line's height: the ground's top there, if it is not far off, never a cell in the ground)
            BlockPos top = GroundTop.cell(level, BlockPos.containing(p[0], p[2], p[1]));
            on = Math.abs(top.getY() - p[2]) <= 6 ? RaidGround.standAt(level, p[0], p[1], top.getY()) : null;
        }
        for (int step = 1; on == null && step <= 4; step++) {
            // (R3: nothing to stand in here, an open hole say: the nearest ground a little along the line, never the air over it)
            for (int sign : new int[] {1, -1}) {
                double[] q = at(Math.max(0.0, Math.min(length(), along + sign * step)));
                BlockPos near = RaidGround.standAt(level, q[0], q[1], (int) Math.round(q[2]));
                if (near != null) {
                    on = near;
                    break;
                }
            }
        }
        return on != null ? on : BlockPos.containing(p[0], p[2], p[1]);
    }

    /** The block this place along the line is in (the line's own point, no ground looked for). */
    BlockPos point(double along) {
        double[] p = at(along);
        return BlockPos.containing(p[0], p[2], p[1]);
    }

    /**
     * The raid queue fix (2026-11-06): how far an ant walks to this place {@code along} (flat) the line, the climbs
     * and drops counted (down a tall dead mound's hump the trail drops 10 blocks in 3 flat ones: by the flat
     * distance the front, after 14 blocks of climbing down, seemed barely ahead, and the crowd waited behind it).
     * Behind the start and past the end, flat.
     */
    double walked(double along) {
        int n = xs.length;
        if (n == 1 || along <= 0.0) {
            return along;
        }
        if (along >= length()) {
            return walks[n - 1] + along - length();
        }
        int i = pieceAt(along);
        double piece = sums[i] - sums[i - 1];
        double t = piece <= 1.0E-6 ? 1.0 : Mth.clamp((along - sums[i - 1]) / piece, 0.0, 1.0);
        return walks[i - 1] + t * (walks[i] - walks[i - 1]);
    }

    /** The other way round: the flat distance along the line of the place an ant reaches after walking this far. */
    double alongAtWalked(double walked) {
        int n = xs.length;
        if (n == 1 || walked <= 0.0) {
            return walked;
        }
        if (walked >= walks[n - 1]) {
            return length() + walked - walks[n - 1];
        }
        int lo = 1, hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (walked <= walks[mid]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        double piece = walks[lo] - walks[lo - 1];
        double t = piece <= 1.0E-6 ? 1.0 : Mth.clamp((walked - walks[lo - 1]) / piece, 0.0, 1.0);
        return sums[lo - 1] + t * (sums[lo] - sums[lo - 1]);
    }

    /** The whole line's length walked, climbs and drops counted. */
    double walkedLength() {
        return walks[walks.length - 1];
    }

    /**
     * How far an ant at (x, y, z) has walked along the line, its height counted (halfway down a wall it is halfway
     * along the drop, which its flat place cannot tell).
     */
    double walkedOf(double x, double y, double z) {
        return nearest3(x, y, z)[0];
    }

    /** How far (blocks, straight, height counted) the point (x, y, z) lies from the line. */
    double awayFrom(double x, double y, double z) {
        return Math.sqrt(nearest3(x, y, z)[1]);
    }

    /** The nearest place on the line in 3D: {walked to it, distance squared}. */
    private double[] nearest3(double x, double y, double z) {
        int n = xs.length;
        if (n == 1) {
            return new double[] {0.0, (x - xs[0]) * (x - xs[0]) + (z - zs[0]) * (z - zs[0]) + (y - ys[0]) * (y - ys[0])};
        }
        double best = Double.MAX_VALUE;
        double bestWalked = 0.0;
        for (int i = 1; i < n; i++) {
            double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1], dy = ys[i] - ys[i - 1];
            double piece = dx * dx + dz * dz + dy * dy;
            double t = piece <= 1.0E-9 ? 0.0
                    : Mth.clamp(((x - xs[i - 1]) * dx + (z - zs[i - 1]) * dz + (y - ys[i - 1]) * dy) / piece, 0.0, 1.0);
            double px = xs[i - 1] + dx * t, pz = zs[i - 1] + dz * t, py = ys[i - 1] + dy * t;
            double distance = (x - px) * (x - px) + (z - pz) * (z - pz) + (y - py) * (y - py);
            if (distance < best) {
                best = distance;
                bestWalked = walks[i - 1] + (walks[i] - walks[i - 1]) * t;
            }
        }
        return new double[] {bestWalked, best};
    }

    double length() {
        return sums[sums.length - 1];
    }

    BlockPos end() {
        return points.get(points.size() - 1);
    }

    /**
     * The first piece's end at or past {@code along} (1 to n - 1): a binary search of the distances walked (the raid
     * code review, finding 9; it was a walk from the start every time).
     */
    private int pieceAt(double along) {
        int lo = 1, hi = xs.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (along <= sums[mid]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    /** The place {@code along} blocks along the line: x, z, y. */
    double[] at(double along) {
        int n = xs.length;
        if (n == 1 || along <= 0.0) {
            return new double[] {xs[0], zs[0], ys[0]};
        }
        int i = pieceAt(along);
        double piece = sums[i] - sums[i - 1];
        double t = piece <= 1.0E-6 ? 1.0 : Mth.clamp((along - sums[i - 1]) / piece, 0.0, 1.0);
        return new double[] {Mth.lerp(t, xs[i - 1], xs[i]), Mth.lerp(t, zs[i - 1], zs[i]), Mth.lerp(t, ys[i - 1], ys[i])};
    }

    /** The line's heading at this place along it (a unit x, z). */
    double[] direction(double along) {
        int n = xs.length;
        for (int i = n == 1 ? n : pieceAt(along); i < n; i++) {
            if (along <= sums[i] || i == n - 1) {
                double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1];
                double length = Math.hypot(dx, dz);
                if (length > 1.0E-6) {
                    return new double[] {dx / length, dz / length};
                }
            }
        }
        return new double[] {1.0, 0.0};
    }

    /** How far along the line the nearest place on it to (x, z) lies. */
    double along(double x, double z) {
        return nearest(x, z)[0];
    }

    /** How far (x, z) is from the line. */
    double offBy(double x, double z) {
        return nearest(x, z)[1];
    }

    /** The nearest place on the line to (x, z): how far along it, and how far off it. */
    double[] nearest(double x, double z) {
        int n = xs.length;
        if (n == 1) {
            return new double[] {0.0, Math.hypot(x - xs[0], z - zs[0])};
        }
        double best = Double.MAX_VALUE;
        double bestAlong = 0.0;
        for (int i = 1; i < n; i++) {
            double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1];
            double piece = dx * dx + dz * dz;
            double t = piece <= 1.0E-9 ? 0.0 : Mth.clamp(((x - xs[i - 1]) * dx + (z - zs[i - 1]) * dz) / piece, 0.0, 1.0);
            double px = xs[i - 1] + dx * t, pz = zs[i - 1] + dz * t;
            double distance = Math.hypot(x - px, z - pz);
            if (distance < best) {
                best = distance;
                bestAlong = sums[i - 1] + Math.sqrt(piece) * t;
            }
        }
        return new double[] {bestAlong, best};
    }
}
