package com.antcolonies.tree;

/**
 * The drawing tools of the tree shape maker (M7.5), the same as the tree viewer's (tools/tree_viewer.template.html):
 * points are {x, y, z} in blocks, a round piece of wood is bark outside and wood inside, and a narrow one is a thin
 * line of branch blocks, every step along one axis so the pieces always join face to face.
 */
final class TreeDraw {
    static final TreePart[] LIVE = {TreePart.BARK, TreePart.INNER, TreePart.THIN};
    static final TreePart[] DEAD = {TreePart.DEAD_BARK, TreePart.DEAD_INNER, TreePart.DEAD_THIN};

    private TreeDraw() {
    }

    static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double smooth(double a, double b, double x) {
        double t = clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] mul(double[] a, double s) {
        return new double[] {a[0] * s, a[1] * s, a[2] * s};
    }

    static double[] mix(double[] a, double[] b, double t) {
        return add(a, mul(sub(b, a), t));
    }

    static double len(double[] a) {
        return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }

    static double[] dir(double az, double el) {
        return new double[] {Math.cos(az) * Math.cos(el), Math.sin(el), Math.sin(az) * Math.cos(el)};
    }

    private static void put(TreeVoxels v, int x, int y, int z, TreePart part, boolean fillOnly) {
        if (fillOnly) {
            if (v.get(x, y, z) == null) {
                v.put(x, y, z, part);
            }
        } else {
            v.put(x, y, z, part);
        }
    }

    /** A thin branch from a to b, one block wide. With fillOnly it only fills empty blocks (a core line inside wood). */
    static void thinLine(TreeVoxels v, double[] a, double[] b, TreePart part, boolean fillOnly) {
        int[] p = {(int) Math.round(a[0]), (int) Math.round(a[1]), (int) Math.round(a[2])};
        put(v, p[0], p[1], p[2], part, fillOnly);
        double[] ab = sub(b, a);
        int n = (int) Math.ceil(len(ab) * 3) + 1;
        for (int i = 1; i <= n; i++) {
            double f = (double) i / n;
            int[] q = {(int) Math.round(a[0] + ab[0] * f), (int) Math.round(a[1] + ab[1] * f), (int) Math.round(a[2] + ab[2] * f)};
            while (p[0] != q[0] || p[1] != q[1] || p[2] != q[2]) {
                int axis = 0, best = -1;
                for (int j = 0; j < 3; j++) {
                    int d = Math.abs(q[j] - p[j]);
                    if (d > best) {
                        best = d;
                        axis = j;
                    }
                }
                p[axis] += Integer.signum(q[axis] - p[axis]);
                put(v, p[0], p[1], p[2], part, fillOnly);
            }
        }
    }

    static void thinLine(TreeVoxels v, double[] a, double[] b, TreePart part) {
        thinLine(v, a, b, part, false);
    }

    /**
     * A round piece of wood from a to b, radius ra to rb: bark outside, wood inside; a thin line when narrow. flat: flat
     * (broken) ends instead of round joints; hollow: the share of the radius rotted out (lined with rotten wood). A solid
     * piece also gets a one-block core line along its axis (filling only empty blocks), so a piece narrowing to a point
     * never leaves bits of wood joined to the rest only by an edge.
     */
    static void seg(TreeVoxels v, double[] a, double[] b, double ra, double rb, TreePart[] blocks, boolean flat, double hollow) {
        TreePart outer = blocks[0], inner = blocks[1], thin = blocks[2];
        if (Math.max(ra, rb) < 0.8) {
            thinLine(v, a, b, thin);
            return;
        }
        double[] ab = sub(b, a);
        double l2 = Math.max(1e-6, ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2]);
        double reach = Math.max(ra, rb) + 1;
        int[] lo = new int[3], hi = new int[3];
        for (int i = 0; i < 3; i++) {
            lo[i] = (int) Math.floor(Math.min(a[i], b[i]) - reach);
            hi[i] = (int) Math.ceil(Math.max(a[i], b[i]) + reach);
        }
        for (int x = lo[0]; x <= hi[0]; x++) {
            for (int y = Math.max(-1, lo[1]); y <= hi[1]; y++) {
                for (int z = lo[2]; z <= hi[2]; z++) {
                    double raw = ((x - a[0]) * ab[0] + (y - a[1]) * ab[1] + (z - a[2]) * ab[2]) / l2;
                    if (flat && (raw < 0 || raw > 1)) {
                        continue;
                    }
                    double t = clamp(raw, 0, 1);
                    double dx = x - (a[0] + ab[0] * t), dy = y - (a[1] + ab[1] * t), dz = z - (a[2] + ab[2] * t);
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz), r = lerp(ra, rb, t);
                    if (d > r) {
                        continue;
                    }
                    if (hollow > 0 && d < r * hollow) {
                        if (y >= 0) {
                            v.carve(x, y, z);
                        }
                        continue;
                    }
                    if (d > r - 1.15) {
                        v.put(x, y, z, outer);
                        continue;
                    }
                    v.put(x, y, z, hollow > 0 && d < r * hollow + 1.15 ? TreePart.ROTTEN : inner);
                }
            }
        }
        if (hollow <= 0) {
            thinLine(v, a, b, thin, true);
        }
    }

    static void seg(TreeVoxels v, double[] a, double[] b, double ra, double rb, TreePart[] blocks) {
        seg(v, a, b, ra, rb, blocks, false, 0);
    }

    /** A line of points (a trunk, a limb) with its length along the way. */
    static final class Poly {
        final double[][] pts;
        final double[] cum;
        final double length;

        Poly(double[]... pts) {
            this.pts = pts;
            cum = new double[pts.length];
            for (int i = 1; i < pts.length; i++) {
                cum[i] = cum[i - 1] + len(sub(pts[i], pts[i - 1]));
            }
            length = cum[pts.length - 1];
        }

        /** The point d along the line (clamped to its ends). */
        double[] at(double d) {
            if (d <= 0) {
                return pts[0];
            }
            for (int i = 1; i < pts.length; i++) {
                if (d <= cum[i] || i == pts.length - 1) {
                    double segLen = cum[i] - cum[i - 1];
                    return mix(pts[i - 1], pts[i], segLen <= 0 ? 1 : clamp((d - cum[i - 1]) / segLen, 0, 1));
                }
            }
            return pts[pts.length - 1];
        }

        /** The last corner of the line at or before d. */
        double[] cornerBefore(double d) {
            for (int i = pts.length - 1; i > 0; i--) {
                if (cum[i] <= d) {
                    return pts[i];
                }
            }
            return pts[0];
        }
    }

    /**
     * Draws a piece of a growing tree: the line up to `upto` along it, its radius at each corner given (the grown
     * piece's). A piece still growing (taper) narrows to a thin tip; a broken one ends blunt. The line is drawn in short
     * stretches fixed along the grown piece, so a longer, older piece always holds all the wood of a younger one (wood
     * never moves, the user's real growth).
     */
    static void growing(TreeVoxels v, Poly p, double[] radii, double upto, boolean taper, TreePart[] blocks, double hollow) {
        if (upto <= 1e-6) {
            return;
        }
        boolean complete = upto >= p.length - 1e-9;
        for (int i = 0; i + 1 < p.pts.length; i++) {
            double d0 = p.cum[i], segLen = p.cum[i + 1] - d0;
            if (d0 >= upto) {
                break;
            }
            if (segLen <= 1e-9) {
                continue;
            }
            int pieces = Math.max(1, (int) Math.ceil(segLen / 2));
            for (int q = 0; q < pieces; q++) {
                double f0 = (double) q / pieces, f1 = (double) (q + 1) / pieces;
                double e0 = d0 + segLen * f0;
                if (e0 >= upto) {
                    break;
                }
                double e1 = d0 + segLen * f1;
                if (e1 > upto) {
                    f1 = (upto - d0) / segLen;
                    e1 = upto;
                }
                double r0 = lerp(radii[i], radii[i + 1], f0), r1 = lerp(radii[i], radii[i + 1], f1);
                if (!complete && taper) {
                    r0 = Math.min(r0, lerp(radii[0], 0.35, e0 / upto));
                    r1 = Math.min(r1, lerp(radii[0], 0.35, e1 / upto));
                }
                seg(v, mix(p.pts[i], p.pts[i + 1], f0), mix(p.pts[i], p.pts[i + 1], f1), r0, r1, blocks, false, hollow);
            }
        }
    }
}
