package com.antcolonies.tree;

import static com.antcolonies.tree.TreeDraw.LIVE;
import static com.antcolonies.tree.TreeDraw.add;
import static com.antcolonies.tree.TreeDraw.clamp;
import static com.antcolonies.tree.TreeDraw.len;
import static com.antcolonies.tree.TreeDraw.lerp;
import static com.antcolonies.tree.TreeDraw.mix;
import static com.antcolonies.tree.TreeDraw.mul;
import static com.antcolonies.tree.TreeDraw.seg;
import static com.antcolonies.tree.TreeDraw.sub;
import static com.antcolonies.tree.TreeDraw.thinLine;

import java.util.ArrayList;
import java.util.List;

/**
 * The new umbrella thorn (the user's redo, 2026-10-18, from two photos of real Vachellia tortilis; only the grown tree so
 * far, on trial in the tree viewer, not in the game yet). A real umbrella thorn: a short thick trunk, often leaning, that
 * splits low into 2 to 4 big limbs; the limbs leave the fork at wide angles and sweep outward in long curves, some nearly
 * to the crown's edge, curling up into it only at their ends, and often split once; the crown is one wide, thin layer,
 * 2.5 to 3 times as wide as tall, its top gently domed, its underside flat and lumpy, the edges drooping, with thorny
 * twigs hanging from the rim. The thin wood all lives inside the crown.
 *
 * <p>The rule for Minecraft (the user: "I don't want the trees to look skinny"): no bare wood under the crown is thinner
 * than round bark about 3 blocks across; the limbs only become the limb pipe and the thin branches once they are in the
 * leaves. Limbs follow smooth curves, so they sweep instead of stepping.
 */
final class UmbrellaShape {
    private static final double TAU = Math.PI * 2;
    /** The thinnest bare limb (radius in blocks): round bark, about 3 across. */
    static final double LIMB_MIN = 1.3;

    /**
     * The versions to pick from (my picks of numbers, from the user's photos).
     *
     * @param height     the crown's top (blocks; a little more by seed)
     * @param fork       the fork's height as a share of the crown's top
     * @param limbsLo    fewest main limbs
     * @param limbsHi    most main limbs
     * @param rise       how steeply the limbs leave the fork (degrees above level)
     * @param sag        how far a limb dips below a straight line before it curls up into the crown (blocks)
     * @param thick      the crown's thickness in the middle
     * @param dome       how much lower the crown's top is at the edge than in the middle
     * @param smooth     a crown filled as one layer (true) or built of clumps with gaps under it (false)
     */
    enum Version {
        N1(23, 0.28, 3, 4, 28, 3, 5, 2, false),
        N2(27, 0.42, 3, 5, 50, 0.5, 4.5, 1.5, false),
        N3(25, 0.34, 3, 4, 36, 2, 5, 2, true);

        final double height, fork, rise, sag, thick, dome;
        final int limbsLo, limbsHi;
        final boolean smooth;

        Version(double height, double fork, int limbsLo, int limbsHi, double rise, double sag, double thick, double dome, boolean smooth) {
            this.height = height;
            this.fork = fork;
            this.limbsLo = limbsLo;
            this.limbsHi = limbsHi;
            this.rise = Math.toRadians(rise);
            this.sag = sag;
            this.thick = thick;
            this.dome = dome;
            this.smooth = smooth;
        }
    }

    private final TreeKind kind;
    private final Version ver;
    private final TreeRandom r;
    private final double height, crownRadius, trunkRadius;

    UmbrellaShape(TreeKind kind, long seed, Version ver) {
        this.kind = kind;
        this.ver = ver;
        r = new TreeRandom(seed * 7919 + kind.salt() + 977);
        height = ver.height + r.next() * 2.5;
        crownRadius = kind.crown().radius() - 1 + r.next() * 3;
        trunkRadius = kind.trunk().radius() + (r.next() - 0.5) * kind.trunk().radiusSpread() * 2;
    }

    /** The crown's top at a distance from the middle. */
    double top(double d) {
        return height - ver.dome * sq(d / crownRadius);
    }

    /** The crown's underside at a distance from the middle. */
    double under(double d) {
        return top(d) - lerp(ver.thick, 2.5, Math.pow(clamp(d / crownRadius, 0, 1), 1.6));
    }

    TreeVoxels build() {
        TreeVoxels v = new TreeVoxels();
        double fh = height * ver.fork;
        double leanA = r.next() * TAU, leanD = 0.5 + r.next() * 1.5;
        double[] fork = {Math.cos(leanA) * leanD, fh, Math.sin(leanA) * leanD};
        double[] mid = {fork[0] * 0.35 + (r.next() - 0.5), fh * 0.5, fork[2] * 0.35 + (r.next() - 0.5)};
        double rFork = trunkRadius * 0.85;
        // The trunk: a flared foot, then up to the fork with a gentle bend.
        seg(v, new double[] {0, -1, 0}, new double[] {0, 1.5, 0}, trunkRadius + 1.4, trunkRadius, LIVE);
        tube(v, new double[][] {{0, -1, 0}, mid, fork}, new double[] {trunkRadius, trunkRadius * 0.93, rFork});
        int roots = 3 + (int) Math.floor(r.next() * 3);
        for (int i = 0; i < roots; i++) {
            double a = r.next() * TAU, l = 4 + r.next() * 5;
            double[] d = {Math.cos(a), 0, Math.sin(a)};
            seg(v, add(mul(d, trunkRadius * 0.7), new double[] {0, -0.2, 0}), add(mul(d, trunkRadius + l), new double[] {0, -0.6, 0}), 1.4, 0.45, LIVE);
        }

        // The big limbs from the fork, each sweeping out under the crown; some split once.
        List<double[]> ends = new ArrayList<>();
        List<Double> endAz = new ArrayList<>();
        int n = ver.limbsLo + (int) Math.floor(r.next() * (ver.limbsHi - ver.limbsLo + 1));
        double phase = r.next() * TAU;
        for (int i = 0; i < n; i++) {
            double az = phase + i * TAU / n + (r.next() - 0.5) * 0.7;
            double reach = crownRadius * (0.6 + r.next() * 0.25);
            double rise = ver.rise + (r.next() - 0.5) * Math.toRadians(14);
            double rStart = rFork * (0.62 + r.next() * 0.12);
            double[] start = add(fork, new double[] {Math.cos(az) * rFork * 0.4, 0, Math.sin(az) * rFork * 0.4});
            double[][] curve = limbCurve(start, az, rise, reach, ver.sag * (0.7 + r.next() * 0.6));
            double split = r.next();
            double splitAt = 0.4 + r.next() * 0.25, splitTurn = (r.next() < 0.5 ? -1 : 1) * (0.35 + r.next() * 0.35);
            double splitReach = Math.min(crownRadius * 0.85, reach * (0.85 + r.next() * 0.25));
            limb(v, curve, rStart, ends, endAz, az);
            if (split < 0.6) {
                double[] from = bezier(curve, splitAt);
                double rHere = Math.max(LIMB_MIN, lerp(rStart, LIMB_MIN, splitAt) * 0.85);
                double az2 = az + splitTurn;
                double[] tangent = sub(bezier(curve, splitAt + 0.05), from);
                double el = Math.atan2(tangent[1], Math.hypot(tangent[0], tangent[2]));
                double[][] c2 = limbCurve(from, az2, Math.max(Math.toRadians(15), el), splitReach - Math.hypot(from[0], from[2]) * 0.0, ver.sag * 0.5);
                limb(v, c2, rHere, ends, endAz, az2);
            }
        }

        // In the crown: branches fan out from every limb end to fill its share of the crown, the first half the limb
        // pipe, then thin twigs; every tip holds a clump of leaves.
        List<double[]> tips = new ArrayList<>();
        double sector = TAU / Math.max(1, ends.size()) * 1.4;
        for (int e = 0; e < ends.size(); e++) {
            double[] end = ends.get(e);
            double endD = Math.hypot(end[0], end[2]);
            int b = 6 + (int) Math.floor(r.next() * 4);
            tips.add(end);
            for (int j = 0; j < b; j++) {
                double a = endAz.get(e) + (r.next() - 0.5) * sector;
                double d = clamp(lerp(endD * 0.45, crownRadius * 0.92, r.next()), 2, crownRadius * 0.92);
                double y = lerp(under(d) + 1, top(d) - 1, 0.25 + r.next() * 0.5);
                double[] tip = {Math.cos(a) * d, y, Math.sin(a) * d};
                double[] m = mix(end, tip, 0.5);
                double md = Math.hypot(m[0], m[2]);
                m[1] = Math.max(m[1], under(md) + 1.2);
                thinLine(v, end, m, TreePart.LIMB);
                thinLine(v, m, tip, TreePart.THIN);
                tips.add(tip);
                tips.add(m);
            }
        }

        // The leaves.
        TreeKind.Crown c = kind.crown();
        double density = c.leafDensity();
        for (double[] cen : tips) {
            double rx = c.padRadius() * (0.6 + 0.4 * TreeRandom.hash((int) Math.round(cen[0]), 3, (int) Math.round(cen[2]), 3));
            double ry = ver.thick * 0.45;
            for (int x = (int) Math.floor(cen[0] - rx); x <= Math.ceil(cen[0] + rx); x++) {
                for (int z = (int) Math.floor(cen[2] - rx); z <= Math.ceil(cen[2] + rx); z++) {
                    double hd = Math.hypot(x, z);
                    if (hd > crownRadius * (0.95 + 0.08 * TreeRandom.hash(x, 1, z, 6))) {
                        continue;
                    }
                    double lumpTop = top(hd) + (TreeRandom.hash(x, 0, z, 5) < 0.3 ? 1 : 0);
                    double lumpLow = under(hd) - (TreeRandom.hash(x, 2, z, 8) < 0.25 ? 1 : 0);
                    for (int y = (int) Math.floor(cen[1] - ry); y <= Math.ceil(cen[1] + ry); y++) {
                        double e = sq((x - cen[0]) / rx) + sq((z - cen[2]) / rx) + sq((y - cen[1]) / ry);
                        if (e > 1 || y > lumpTop || y < lumpLow) {
                            continue;
                        }
                        leaf(v, x, y, z, e, density);
                    }
                }
            }
        }
        if (ver.smooth) {
            // One layer: every place under the top is filled down to the underside (with the leaves' holes).
            int rr = (int) Math.ceil(crownRadius + 1);
            for (int x = -rr; x <= rr; x++) {
                for (int z = -rr; z <= rr; z++) {
                    double hd = Math.hypot(x, z);
                    if (hd > crownRadius * (0.93 + 0.07 * TreeRandom.hash(x, 1, z, 6))) {
                        continue;
                    }
                    for (int y = (int) Math.ceil(under(hd) + 0.5); y <= top(hd); y++) {
                        leaf(v, x, y, z, 0.5, density);
                    }
                }
            }
        }
        // Thorny twigs hanging from the rim of the crown.
        List<int[]> rim = new ArrayList<>();
        v.forEach((x, y, z, p) -> {
            if (p == TreePart.LEAVES && v.get(x, y - 1, z) == null && Math.hypot(x, z) > crownRadius * 0.75
                    && TreeRandom.hash(x, y, z, 92) < 0.2) {
                rim.add(new int[] {x, y - 1, z});
            }
        });
        for (int[] p : rim) {
            v.put(p[0], p[1], p[2], TreePart.THORNS);
        }
        return v;
    }

    private static void leaf(TreeVoxels v, int x, int y, int z, double e, double density) {
        if (TreeRandom.hash(x, y, z, 7) > density * (1 - 0.25 * e)) {
            return;
        }
        v.put(x, y, z, TreePart.LEAVES);
    }

    /**
     * A limb's curve (a cubic Bezier): out of its start at an angle above level, sagging on the way out, its end just
     * inside the crown's underside, reach from the middle.
     */
    private double[][] limbCurve(double[] start, double az, double rise, double reach, double sag) {
        double[] dir = {Math.cos(az), 0, Math.sin(az)};
        double startD = start[0] * dir[0] + start[2] * dir[2];
        double outD = Math.max(3, reach - startD);
        double[] end = {Math.cos(az) * reach, 0, Math.sin(az) * reach};
        end[0] += start[0] - dir[0] * startD;
        end[2] += start[2] - dir[2] * startD;
        end[1] = under(Math.hypot(end[0], end[2])) + 1;
        double up = end[1] - start[1];
        double l1 = Math.hypot(outD, up) * 0.4;
        double[] p1 = add(start, new double[] {dir[0] * Math.cos(rise) * l1, Math.sin(rise) * l1, dir[2] * Math.cos(rise) * l1});
        double[] p2 = mix(start, end, 0.72);
        p2[1] = Math.min(p2[1], end[1] - sag);
        p2[1] = Math.max(p2[1], p1[1] - 1);
        return new double[][] {start, p1, p2, end};
    }

    private static double[] bezier(double[][] c, double t) {
        t = clamp(t, 0, 1);
        double u = 1 - t;
        double[] p = new double[3];
        for (int i = 0; i < 3; i++) {
            p[i] = u * u * u * c[0][i] + 3 * u * u * t * c[1][i] + 3 * u * t * t * c[2][i] + t * t * t * c[3][i];
        }
        return p;
    }

    /** Draws a limb along its curve, round bark tapering to LIMB_MIN at its end; remembers the end. */
    private void limb(TreeVoxels v, double[][] curve, double rStart, List<double[]> ends, List<Double> endAz, double az) {
        double length = 0;
        double[] prev = curve[0];
        for (int i = 1; i <= 20; i++) {
            double[] p = bezier(curve, i / 20.0);
            length += len(sub(p, prev));
            prev = p;
        }
        int steps = Math.max(2, (int) Math.ceil(length / 1.5));
        double[][] pts = new double[steps + 1][];
        double[] radii = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            pts[i] = bezier(curve, t);
            radii[i] = Math.max(LIMB_MIN, lerp(rStart, LIMB_MIN, Math.pow(t, 0.8)));
        }
        tube(v, pts, radii);
        ends.add(curve[3]);
        endAz.add(az);
    }

    /** Round bark along a line of points, radius given at each. */
    private static void tube(TreeVoxels v, double[][] pts, double[] radii) {
        for (int i = 0; i + 1 < pts.length; i++) {
            double l = len(sub(pts[i + 1], pts[i]));
            int pieces = Math.max(1, (int) Math.ceil(l / 2));
            for (int q = 0; q < pieces; q++) {
                double f0 = (double) q / pieces, f1 = (double) (q + 1) / pieces;
                seg(v, mix(pts[i], pts[i + 1], f0), mix(pts[i], pts[i + 1], f1), lerp(radii[i], radii[i + 1], f0),
                        lerp(radii[i], radii[i + 1], f1), LIVE);
            }
        }
    }

    private static double sq(double x) {
        return x * x;
    }
}
