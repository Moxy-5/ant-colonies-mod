package com.antcolonies.tree;

/**
 * The drawing tools of the tree shape maker (M7.5), the same as the tree viewer's (tools/tree_viewer.template.html):
 * points are {x, y, z} in blocks, a round piece of wood is bark outside and wood inside, and a narrow one is a thin
 * line of branch blocks, every step along one axis so the pieces always join face to face.
 */
final class TreeDraw {
    static final TreePart[] LIVE = {TreePart.BARK, TreePart.INNER, TreePart.THIN, TreePart.LIMB};
    static final TreePart[] DEAD = {TreePart.DEAD_BARK, TreePart.DEAD_INNER, TreePart.DEAD_THIN, TreePart.DEAD_LIMB};

    /**
     * How the limbs between the fork and the crown look, on trial in the tree viewer (the user's report 2026-10-18: the
     * 1-block-thick limbs looked stringy; "don't make the trees look skinny"). A is today's; the game keeps it until the
     * user picks. Thicknesses are shares of the trunk's radius.
     *
     * @param limbFrom  a limb's thickness at the fork
     * @param limbTo    a limb's thickness where it reaches the crown
     * @param branchFrom a crown branch's thickness where it leaves its limb
     * @param branchTip a living crown branch's radius in blocks at its tip
     * @param limbBlock wood about a block thick is the limb pipe (10 px), not bark cubes or a 6 px thin branch
     * @param straight  1-block lines in longer straight runs with fewer, bigger steps
     */
    enum LimbLook {
        A(0.72, 0.4, 0.38, 0.35, false, false), B(0.78, 0.6, 0.5, 0.35, false, false), C(0.72, 0.4, 0.38, 0.35, true, false),
        D(0.78, 0.6, 0.5, 0.35, true, false), E(0.78, 0.6, 0.5, 0.35, true, true),
        // The user's ask 2026-10-18: three thicker still.
        F(0.9, 0.75, 0.6, 0.35, true, false), G(1.0, 0.85, 0.7, 0.45, true, false), H(1.0, 0.95, 0.85, 0.6, true, false);

        final double limbFrom, limbTo, branchFrom, branchTip;
        final boolean limbBlock, straight;

        LimbLook(double limbFrom, double limbTo, double branchFrom, double branchTip, boolean limbBlock, boolean straight) {
            this.limbFrom = limbFrom;
            this.limbTo = limbTo;
            this.branchFrom = branchFrom;
            this.branchTip = branchTip;
            this.limbBlock = limbBlock;
            this.straight = straight;
        }
    }

    /**
     * The new growth on trial in the tree viewer (the user's ask 2026-10-18: a young umbrella thorn and the first mature
     * one looked like two different trees): the wood thickens sooner, and the limbs grow out of the fork as soon as the
     * trunk reaches it, clothed in leaves along their way, with no separate young form. Off in the game until the user
     * picks.
     */
    static volatile boolean earlyGrowth = false;

    /** The look in use: A in the game; the viewer's export draws every look in turn. */
    static volatile LimbLook look = LimbLook.A;
    /** With the limb pipe: narrower than this is a thin branch, wider than LIMB_MAX round bark (my picks). */
    static final double LIMB_MIN = 0.45, LIMB_MAX = 1.25;

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
        thinLine(v, a, b, 1, part, fillOnly);
    }

    /**
     * The first {@code upto} (0 to 1) of the one-block line from a to b: the same blocks as the whole line up to there, so
     * a branch growing out or broken off is always part of its whole line (wood never moves, step 5).
     */
    static void thinLine(TreeVoxels v, double[] a, double[] b, double upto, TreePart part, boolean fillOnly) {
        int[] p = {(int) Math.round(a[0]), (int) Math.round(a[1]), (int) Math.round(a[2])};
        put(v, p[0], p[1], p[2], part, fillOnly);
        double[] ab = sub(b, a);
        int n = (int) Math.ceil(len(ab) * 3) + 1;
        for (int i = 1; i <= n; i++) {
            double f = (double) i / n;
            if (f > upto + 1e-9) {
                break;
            }
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
     * A one-block line from a to b in long straight runs (limb look E): it runs along its main axis and lets the other
     * axes drift up to 1.5 blocks off the true line before stepping 2 at once, so a slanted line has half as many steps,
     * each 2 high. Face to face all the way, like thinLine.
     */
    static void straightLine(TreeVoxels v, double[] a, double[] b, TreePart part) {
        double[] ab = sub(b, a);
        int m = 0;
        for (int j = 1; j < 3; j++) {
            if (Math.abs(ab[j]) > Math.abs(ab[m])) {
                m = j;
            }
        }
        int[] p = {(int) Math.round(a[0]), (int) Math.round(a[1]), (int) Math.round(a[2])};
        int[] end = {(int) Math.round(b[0]), (int) Math.round(b[1]), (int) Math.round(b[2])};
        v.put(p[0], p[1], p[2], part);
        int sm = Integer.signum(end[m] - p[m]);
        while (p[m] != end[m]) {
            p[m] += sm;
            v.put(p[0], p[1], p[2], part);
            double t = ab[m] == 0 ? 1 : clamp((p[m] - a[m]) / ab[m], 0, 1);
            for (int j = 0; j < 3; j++) {
                if (j == m) {
                    continue;
                }
                double off = a[j] + ab[j] * t - p[j];
                if (Math.abs(off) >= 1.5) {
                    int s = (int) Math.signum(off);
                    for (int k = 0; k < 2; k++) {
                        p[j] += s;
                        v.put(p[0], p[1], p[2], part);
                    }
                }
            }
        }
        for (int j = 0; j < 3; j++) {
            while (p[j] != end[j]) {
                p[j] += Integer.signum(end[j] - p[j]);
                v.put(p[0], p[1], p[2], part);
            }
        }
    }

    /**
     * A round piece of wood from a to b, radius ra to rb: bark outside, wood inside; a thin line when narrow. flat: flat
     * (broken) ends instead of round joints; hollow: the share of the radius rotted out (lined with rotten wood). A solid
     * piece also gets a one-block core line along its axis (filling only empty blocks), so a piece narrowing to a point
     * never leaves bits of wood joined to the rest only by an edge.
     */
    static void seg(TreeVoxels v, double[] a, double[] b, double ra, double rb, TreePart[] blocks, boolean flat, double hollow) {
        TreePart outer = blocks[0], inner = blocks[1], thin = blocks[2];
        if (look.limbBlock && Math.max(ra, rb) < LIMB_MAX) {
            boolean pipe = Math.max(ra, rb) >= LIMB_MIN;
            if (pipe && look.straight) {
                straightLine(v, a, b, blocks[3]);
            } else {
                thinLine(v, a, b, pipe ? blocks[3] : thin);
            }
            return;
        }
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
        growing(v, p, radii, upto, upto, taper, blocks, hollow);
    }

    /**
     * A limb grown out to {@code grown} and broken off at {@code broken} (a shed snag, an ancient tree's dead limb): the
     * part of the limb as it grew, cut, never thicker than the whole (wood never jumps, step 5).
     */
    static void broken(TreeVoxels v, Poly p, double[] radii, double grown, double broken, TreePart[] blocks) {
        growing(v, p, radii, Math.min(grown, broken), grown, true, blocks, 0);
    }

    /** As {@link #growing}, tapering as if grown out to {@code taperOver} but only drawn up to {@code upto}. */
    static void growing(TreeVoxels v, Poly p, double[] radii, double upto, double taperOver, boolean taper, TreePart[] blocks, double hollow) {
        if (upto <= 1e-6) {
            return;
        }
        boolean complete = taperOver >= p.length - 1e-9;
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
                    r0 = Math.min(r0, lerp(radii[0], 0.35, e0 / taperOver));
                    r1 = Math.min(r1, lerp(radii[0], 0.35, e1 / taperOver));
                }
                seg(v, mix(p.pts[i], p.pts[i + 1], f0), mix(p.pts[i], p.pts[i + 1], f1), r0, r1, blocks, false, hollow);
            }
        }
    }
}
