package com.antcolonies.tree;

import static com.antcolonies.tree.TreeDraw.LIVE;
import static com.antcolonies.tree.TreeDraw.add;
import static com.antcolonies.tree.TreeDraw.clamp;
import static com.antcolonies.tree.TreeDraw.dir;
import static com.antcolonies.tree.TreeDraw.mul;
import static com.antcolonies.tree.TreeDraw.seg;
import static com.antcolonies.tree.TreeDraw.thinLine;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a baobab (M7.5) at any age and stage, ported from the tree viewer. Real growth: the trunk only ever gets
 * taller and fatter (at every height it is as thick as it has ever been); the short stubby branches on top are dropped
 * and grown anew as the trunk swells past them, so they are marked as blocks the tree drops. Old baobabs (age past 1.1)
 * are hollow while alive, with a door in the side.
 */
final class BaobabShape {
    private static final double TAU = Math.PI * 2;

    private final TreeKind kind;
    private final boolean column;
    private final double heightGrown, radiusGrown, phase, doorAz, fallAz;
    private final List<double[]> branches = new ArrayList<>();
    private final List<List<double[]>> subs = new ArrayList<>();

    BaobabShape(TreeKind kind, long seed) {
        this.kind = kind;
        column = kind.shapeFor(seed).profile().equals("column");
        TreeKind.Trunk t = kind.trunk();
        TreeRandom r = new TreeRandom(seed * 7919 + kind.salt());
        heightGrown = t.height() + (r.next() - 0.5) * t.heightSpread() * 2;
        radiusGrown = t.radius() + (r.next() - 0.5) * t.radiusSpread() * 2;
        phase = r.next() * TAU;
        int lo = kind.crown().limbs().get(0), hi = kind.crown().limbs().get(1);
        int nb = lo + (int) Math.floor(r.next() * (hi - lo + 1));
        for (int i = 0; i < nb; i++) {
            double az = phase + i * TAU / nb + (r.next() - 0.5) * 0.6, el = 0.35 + r.next() * 0.6, l = 5 + r.next() * 5;
            branches.add(new double[] {az, el, l});
            List<double[]> list = new ArrayList<>();
            int ns = 2 + (int) Math.floor(r.next() * 2);
            for (int j = 0; j < ns; j++) {
                list.add(new double[] {az + (r.next() - 0.5) * 1.3, el + (r.next() - 0.5) * 0.6, 2.5 + r.next() * 3});
            }
            subs.add(list);
        }
        doorAz = r.next() * TAU;
        fallAz = r.next() * TAU;
    }

    private static double k(double age) {
        return Math.pow(clamp(age, 0, 1), 1.5);
    }

    private static double old(double age) {
        return clamp((age - 1) / 0.25, 0, 1);
    }

    private double height(double age) {
        return 2 + (heightGrown - 2) * k(age) + 2 * old(age);
    }

    private double radius(double age) {
        return 0.4 + (radiusGrown - 0.4) * k(age) + 1.2 * old(age);
    }

    private double profile(double t) {
        if (column) {
            return 1 - 0.16 * t + (t < 0.1 ? (0.1 - t) * 2.2 : 0);
        }
        return 0.62 + 0.5 * Math.exp(-sq((t - 0.28) / 0.45)) + (t < 0.12 ? (0.12 - t) * 1.5 : 0);
    }

    /** The living trunk's radius at a height: the thickest it has been there at any age up to now. */
    private double trunkRadius(double age, int y) {
        double best = 0;
        for (int j = 1; j <= 8; j++) {
            double a = age * j / 8;
            if (clamp(a, 0, 1) < 0.45 || y > Math.ceil(height(a))) {
                continue;
            }
            best = Math.max(best, radius(a) * profile(clamp(y / height(a), 0, 1)));
        }
        return best;
    }

    /** leafless: the dry season, about 8 months of the year (the viewer's switch; the game's seasons come later). */
    TreeVoxels build(double age, TreeStage stage, boolean leafless) {
        TreeVoxels v = new TreeVoxels();
        double g = clamp(age, 0, 1), k = k(age), old = old(age), ht = height(age), r0 = radius(age);
        if (g < 0.06) {
            v.put(0, 0, 0, TreePart.SEEDLING);
            return v;
        }
        if (stage == TreeStage.HEAP || stage == TreeStage.HEAP_DRY) {
            heap(v, r0, stage == TreeStage.HEAP_DRY);
            return v;
        }
        if (g < 0.45 && stage == TreeStage.LIVING) {
            // Young baobab: one straight stem, a few short branches, a small round crown closing over them.
            int top = (int) Math.round(ht);
            double rx = 2 + 2 * g, ry = 2;
            for (int y = 0; y <= top; y++) {
                v.put(0, y, 0, TreePart.BARK);
            }
            v.dropping = true;
            for (int i = 0; i < Math.min(4, branches.size()); i++) {
                double az = branches.get(i)[0];
                thinLine(v, new double[] {0, top, 0}, new double[] {Math.cos(az) * rx * 0.55, top + 1, Math.sin(az) * rx * 0.55}, TreePart.THIN);
            }
            v.dropping = false;
            if (!leafless) {
                for (int x = -(int) Math.ceil(rx); x <= rx; x++) {
                    for (int z = -(int) Math.ceil(rx); z <= rx; z++) {
                        for (int y = top; y <= top + 4; y++) {
                            double e = sq(x / rx) + sq(z / rx) + sq((y - top - 2) / ry);
                            if (e <= 1 && (e < 0.7 || TreeRandom.hash(x, y, z, 93) < 0.55)) {
                                v.put(x, y, z, TreePart.LEAVES);
                            }
                        }
                    }
                }
            }
            return v;
        }
        boolean slump = stage == TreeStage.SLUMP;
        double htDraw = slump ? ht * 0.62 : ht;
        boolean hollow = slump || old >= 0.4;
        for (int y = -1; y <= Math.ceil(htDraw); y++) {
            double base = slump ? r0 * profile(clamp(y / ht, 0, 1)) * (1 + 0.18 * (1 - y / htDraw)) : trunkRadius(age, Math.max(0, y));
            int m = (int) Math.ceil(base * 1.12) + 1;
            for (int x = -m; x <= m; x++) {
                for (int z = -m; z <= m; z++) {
                    double d = Math.hypot(x, z), a = Math.atan2(z, x);
                    double rr = base * (1 + 0.06 * Math.sin(7 * a + phase + y * 0.12) + 0.03 * Math.sin(13 * a));
                    if (d > rr) {
                        continue;
                    }
                    if (hollow && rr > 2.5) {
                        double top = slump ? htDraw + 1 : ht * 0.72;
                        if (d < rr * 0.55 && y >= 1 && y < top) {
                            v.carve(x, y, z);
                            continue;
                        }
                        if (!slump && angleTo(a, doorAz) * rr < 1.3 && y >= 0 && y <= 3) {
                            v.carve(x, y, z);
                            continue;
                        }
                        if (d < rr * 0.55 + 1.15 && y >= 0) {
                            v.put(x, y, z, slump ? TreePart.FIBRE : TreePart.INNER);
                            continue;
                        }
                    }
                    TreePart b = d > rr - 1.15 ? TreePart.BARK : TreePart.INNER;
                    if (slump) {
                        // The soft trunk caving in: the top and one side gone to fibre.
                        if (angleTo(a, fallAz) < 0.9 && y > htDraw * 0.35 + TreeRandom.hash(x, 0, z, 61) * 3) {
                            continue;
                        }
                        if (y > htDraw - 2 - TreeRandom.hash(x, 0, z, 62) * 2) {
                            if (TreeRandom.hash(x, y, z, 63) < 0.6) {
                                b = TreePart.FIBRE;
                            } else {
                                continue;
                            }
                        }
                        if (b == TreePart.BARK && TreeRandom.hash(x, Math.floorDiv(y, 2), z, 64) < 0.35) {
                            b = TreePart.FIBRE;
                        }
                    }
                    v.put(x, y, z, b);
                }
            }
        }
        if (slump) {
            // Fibre slumped out round the foot.
            double big = r0 * 1.5;
            for (int x = (int) Math.floor(-big - 2); x <= big + 2; x++) {
                for (int z = (int) Math.floor(-big - 2); z <= big + 2; z++) {
                    double d = Math.hypot(x, z);
                    if (d > big + 1.5) {
                        continue;
                    }
                    int h = (int) Math.floor((big + 1.5 - d) * 0.6 * TreeRandom.hash(x, 0, z, 65));
                    for (int y = 0; y <= h; y++) {
                        v.put(x, y, z, TreePart.FIBRE);
                    }
                }
            }
            // Pieces of the caving top left hanging have fallen in.
            v.clearLoose();
            return v;
        }
        double topR = r0 * profile(1) * 0.55;
        v.dropping = true;
        for (int i = 0; i < branches.size(); i++) {
            double[] br = branches.get(i);
            double[] s = {Math.cos(br[0]) * topR, ht - 0.5, Math.sin(br[0]) * topR};
            double[] e = add(s, mul(dir(br[0], br[1]), br[2] * k + 1));
            seg(v, s, e, 1.7 * k + 0.35, 0.8 * k + 0.3, LIVE);
            for (double[] sb : subs.get(i)) {
                double[] e2 = add(e, mul(dir(sb[0], sb[1]), sb[2] * k + 0.6));
                seg(v, e, e2, 0.8 * k + 0.3, 0.3, LIVE);
                if (leafless) {
                    continue;
                }
                double rx = 1 + 1.8 * k, ry = 1.4;
                for (int x = (int) Math.floor(e2[0] - rx); x <= e2[0] + rx; x++) {
                    for (int y = (int) Math.floor(e2[1] - ry); y <= e2[1] + ry + 1; y++) {
                        for (int z = (int) Math.floor(e2[2] - rx); z <= e2[2] + rx; z++) {
                            double q = sq((x - e2[0]) / rx) + sq((z - e2[2]) / rx) + sq((y - e2[1] - 0.5) / ry);
                            if (q <= 1 && TreeRandom.hash(x, y, z, 71) < kind.crown().leafDensity()) {
                                v.put(x, y, z, TreePart.LEAVES);
                            }
                        }
                    }
                }
            }
        }
        v.dropping = false;
        return v;
    }

    /** The fibre heap a dead baobab ends as (dried: shrunk, later gone). */
    private void heap(TreeVoxels v, double r0, boolean dried) {
        double rh = r0 * (dried ? 1.45 : 1.7), hh = r0 * (dried ? 0.45 : 0.9) + 0.5;
        for (int x = (int) Math.floor(-rh - 2); x <= rh + 2; x++) {
            for (int z = (int) Math.floor(-rh - 2); z <= rh + 2; z++) {
                double d = Math.hypot(x, z) * (1 + 0.15 * Math.sin(Math.atan2(z, x) * 3 + phase));
                if (d > rh) {
                    continue;
                }
                double h = hh * (1 - sq(d / rh)) * (0.8 + 0.4 * TreeRandom.hash(x, 0, z, 81));
                for (int y = 0; y <= h; y++) {
                    double roll = TreeRandom.hash(x, y, z, 82);
                    v.put(x, y, z, roll < (dried ? 0.12 : 0.18) ? TreePart.BARK : roll < 0.28 ? TreePart.ROTTEN : TreePart.FIBRE);
                }
            }
        }
    }

    private static double angleTo(double a, double b) {
        double da = Math.abs(a - b) % TAU;
        return da > Math.PI ? TAU - da : da;
    }

    private static double sq(double x) {
        return x * x;
    }
}
