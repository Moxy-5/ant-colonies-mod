package com.antcolonies.savanna.tree;

import static com.antcolonies.savanna.tree.TreeDraw.DEAD;
import static com.antcolonies.savanna.tree.TreeDraw.LIVE;
import static com.antcolonies.savanna.tree.TreeDraw.add;
import static com.antcolonies.savanna.tree.TreeDraw.clamp;
import static com.antcolonies.savanna.tree.TreeDraw.len;
import static com.antcolonies.savanna.tree.TreeDraw.lerp;
import static com.antcolonies.savanna.tree.TreeDraw.mix;
import static com.antcolonies.savanna.tree.TreeDraw.mul;
import static com.antcolonies.savanna.tree.TreeDraw.seg;
import static com.antcolonies.savanna.tree.TreeDraw.smooth;
import static com.antcolonies.savanna.tree.TreeDraw.sub;
import static com.antcolonies.savanna.tree.TreeDraw.thinLine;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.tree.TreeDraw.Poly;

/**
 * The umbrella thorn (the user's redo, 2026-10-18, from two photos of real Vachellia tortilis; version N4 picked by the
 * user: "exactly what I was looking for"). A real umbrella thorn: a thick trunk, often leaning, to a fork about half
 * its height; 3 or 4 big limbs leave the fork low and run far out sideways, curling up into the crown only at their
 * ends, and often split once; the crown is one wide, thin layer, about twice as wide as the tree is tall, its top gently
 * domed, its underside flat and lumpy, the edge drooping, with thorny twigs hanging from the rim. The thin wood all
 * lives inside the crown.
 *
 * <p>The rule for Minecraft (the user: "I don't want the trees to look skinny"): no bare wood under a grown crown is
 * thinner than round bark about 3 blocks across; the limbs only become the limb pipe and the thin branches once they
 * are in the leaves. Limbs follow smooth curves, so they sweep instead of stepping.
 *
 * <p>Real growth (the user's decision): the skeleton is the grown tree's, made once from the seed; a younger tree is that
 * skeleton grown only part of the way out from the ground, every piece of wood keeping its place and only lengthening at
 * its tip and thickening. A young tree is a stem with a small flat crown, its low side twigs shaded out and dropped as it
 * climbs; once the stem reaches the fork the limbs grow out sideways inside their leaves. Age: 0 a seedling, 1 grown, up
 * to 1.25 ancient (thicker, the crown pushed a little out and up, the heart rotted hollow, often one limb dead).
 * Dead trees (snag, mud-climbed, limbs shed, fallen) die the way every acacia does ({@link DeadWood}).
 */
final class UmbrellaShape {
    private static final double TAU = Math.PI * 2;
    /** The thinnest bare limb of a grown tree (radius in blocks): round bark, about 3 across. */
    static final double LIMB_MIN = 1.3;

    // N4's numbers (my picks from the user's photos; the trial versions N1 to N3 are in docs/HISTORY.md).
    /** The crown's top (blocks; up to 2.5 more by seed). */
    private static final double HEIGHT = 25;
    /** The fork's height as a share of the crown's top. */
    private static final double FORK = 0.56;
    private static final int LIMBS_LO = 3, LIMBS_HI = 4;
    /** How steeply the limbs leave the fork (above level). */
    private static final double RISE = Math.toRadians(12);
    /** Where the limbs end, as a share of the crown's radius (up to 0.18 more by limb, at most 0.9). */
    private static final double REACH = 0.75;
    /** How far a limb dips below a straight line before it curls up into the crown (blocks). */
    private static final double SAG = 1;
    /** The crown's thickness in the middle, and how much lower its top is at the edge. */
    private static final double THICK = 5, DOME = 2;
    /**
     * The clumps widening (the user's ask, 2026-10-18): once the limbs have grown RING_FROM of the way out from the
     * fork, each limb's leaves start widening sideways round the crown's circle toward the next limb's, widest at
     * RING_FULL of the way (about age 0.6); each side covers at most RING_WIDEN of the gap, so they never touch (the
     * grown crown joins them).
     */
    /** An ancient crown's full ring of leaves starts this far out (of the crown's radius). */
    private static final double ANCIENT_RING_INNER = 0.4;
    private static final double RING_FROM = 0.03, RING_FULL = 0.35, RING_WIDEN = 0.3;

    private final TreeKind kind;
    private final long seed;
    private final double height, crownRadius, trunkRadius;
    private final Poly trunk;
    private final List<double[]> roots = new ArrayList<>();
    private final List<Limb> limbs = new ArrayList<>();
    private final double fallAz, deadRoll;
    /** The farthest any wood reaches from the ground, along the tree: the grown tree is this far grown. */
    private final double reach;

    private static final class Limb {
        Poly line;
        double[] radii;
        /** How far along the wood from the ground it starts. */
        double start;
        double az, brk;
        /** The limb it split from (null for a limb from the fork) and how far along that one it starts. */
        Limb parent;
        double alongParent;
        final List<Branch> branches = new ArrayList<>();

        double end() {
            return start + line.length;
        }

        double[] tip() {
            return line.pts[line.pts.length - 1];
        }
    }

    /** A branch in the crown: from a limb's end, the limb pipe to its middle, then a thin twig to its tip. */
    private static final class Branch {
        double[] from, mid, tip;
        double start, l1, l2, brk;
    }

    UmbrellaShape(TreeKind kind, long seed) {
        this.kind = kind;
        this.seed = seed;
        // The random numbers come in the same order as in the shape the user picked in the tree viewer, so the grown
        // tree is that one.
        TreeRandom r = new TreeRandom(seed * 7919 + kind.salt() + 977);
        height = HEIGHT + r.next() * 2.5;
        crownRadius = kind.crown().radius() - 1 + r.next() * 3;
        trunkRadius = kind.trunk().radius() + (r.next() - 0.5) * kind.trunk().radiusSpread() * 2;
        double fh = height * FORK;
        double leanA = r.next() * TAU, leanD = 0.5 + r.next() * 1.5;
        double[] fork = {Math.cos(leanA) * leanD, fh, Math.sin(leanA) * leanD};
        double[] mid = {fork[0] * 0.35 + (r.next() - 0.5), fh * 0.5, fork[2] * 0.35 + (r.next() - 0.5)};
        trunk = new Poly(new double[] {0, -1, 0}, mid, fork);
        double rFork = trunkRadius * 0.85;
        int nr = 3 + (int) Math.floor(r.next() * 3);
        for (int i = 0; i < nr; i++) {
            roots.add(new double[] {r.next() * TAU, 4 + r.next() * 5});
        }

        // The big limbs from the fork, each sweeping out under the crown; some split once.
        int n = LIMBS_LO + (int) Math.floor(r.next() * (LIMBS_HI - LIMBS_LO + 1));
        double phase = r.next() * TAU;
        for (int i = 0; i < n; i++) {
            double az = phase + i * TAU / n + (r.next() - 0.5) * 0.7;
            double limbReach = crownRadius * Math.min(0.9, REACH + r.next() * 0.18);
            double rise = RISE + (r.next() - 0.5) * Math.toRadians(14);
            double rStart = rFork * (0.62 + r.next() * 0.12);
            double[] start = add(fork, new double[] {Math.cos(az) * rFork * 0.4, 0, Math.sin(az) * rFork * 0.4});
            double[][] curve = limbCurve(start, az, rise, limbReach, SAG * (0.7 + r.next() * 0.6));
            double split = r.next();
            double splitAt = 0.4 + r.next() * 0.25, splitTurn = (r.next() < 0.5 ? -1 : 1) * (0.35 + r.next() * 0.35);
            double splitReach = Math.min(crownRadius * 0.85, limbReach * (0.85 + r.next() * 0.25));
            Limb limb = limb(curve, rStart, trunk.length, az);
            limbs.add(limb);
            if (split < 0.6) {
                double[] from = bezier(curve, splitAt);
                double rHere = Math.max(LIMB_MIN, lerp(rStart, LIMB_MIN, splitAt) * 0.85);
                double az2 = az + splitTurn;
                double[] tangent = sub(bezier(curve, splitAt + 0.05), from);
                double el = Math.atan2(tangent[1], Math.hypot(tangent[0], tangent[2]));
                double[][] c2 = limbCurve(from, az2, Math.max(Math.toRadians(15), el), splitReach, SAG * 0.5);
                double along = alongCurve(limb, splitAt);
                Limb child = limb(c2, rHere, limb.start + along, az2);
                child.parent = limb;
                child.alongParent = along;
                limbs.add(child);
            }
        }

        // In the crown: branches fan out from every limb end to fill its share of the crown.
        double sector = TAU / Math.max(1, limbs.size()) * 1.4;
        double far = 0;
        for (Limb limb : limbs) {
            double[] end = limb.tip();
            double endD = Math.hypot(end[0], end[2]);
            int b = 6 + (int) Math.floor(r.next() * 4);
            far = Math.max(far, limb.end());
            for (int j = 0; j < b; j++) {
                double a = limb.az + (r.next() - 0.5) * sector;
                double d = clamp(lerp(endD * 0.45, crownRadius * 0.92, r.next()), 2, crownRadius * 0.92);
                double y = lerp(under(d, 0) + 1, top(d, 0) - 1, 0.25 + r.next() * 0.5);
                Branch br = new Branch();
                br.from = end;
                br.tip = new double[] {Math.cos(a) * d, y, Math.sin(a) * d};
                br.mid = mix(end, br.tip, 0.5);
                double md = Math.hypot(br.mid[0], br.mid[2]);
                br.mid[1] = Math.max(br.mid[1], under(md, 0) + 1.2);
                br.start = limb.end();
                br.l1 = len(sub(br.mid, end));
                br.l2 = len(sub(br.tip, br.mid));
                far = Math.max(far, br.start + br.l1 + br.l2);
                limb.branches.add(br);
            }
        }
        reach = far;

        // What only dead or ancient trees need, from their own dice so the living tree above stays the one picked.
        TreeRandom d = new TreeRandom(seed * 131 + kind.salt() + 7);
        for (Limb limb : limbs) {
            limb.brk = 0.45 + d.next() * 0.4;
            for (Branch br : limb.branches) {
                br.brk = 0.3 + d.next() * 0.4;
            }
        }
        deadRoll = d.next();
        fallAz = d.next() * TAU;
    }

    /** The crown's top at a distance from the middle (an ancient crown a little higher). */
    private double top(double dist, double old) {
        return height + 1.5 * old - DOME * sq(dist / crownRadius);
    }

    /** The crown's underside at a distance from the middle. */
    private double under(double dist, double old) {
        return top(dist, old) - lerp(THICK, 2.5, Math.pow(clamp(dist / crownRadius, 0, 1), 1.6));
    }

    /** The tree at an age, grown out along its skeleton. */
    private final class Grown {
        final double g, old, k, s, tf;
        final boolean living;

        Grown(double age, boolean living) {
            this.living = living;
            g = clamp(age, 0, 1);
            old = clamp((age - 1) / 0.25, 0, 1);
            k = Math.pow(g, 1.5);
            s = old > 0 || g >= 1 ? reach + 1 : reach * k;
            // Girth comes sooner than height (a real young tree thickens every year), but a sapling stays slim.
            tf = lerp(0.15, 1, Math.pow(g, 0.8));
        }

        double r0() {
            return trunkRadius * tf + 0.5 * old;
        }

        double upto(Limb limb) {
            return clamp(s - limb.start, 0, limb.line.length);
        }

        double[] radii(Limb limb) {
            double[] out = new double[limb.radii.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = limb.radii[i] * tf + 0.5 * old;
            }
            return out;
        }

        /** An ancient tree has often let one limb (and what grew from it) die. */
        boolean deadLimb(Limb limb) {
            Limb main = limb.parent == null ? limb : limb.parent;
            return old > 0.5 && main == limbs.get(0) && deadRoll < 0.7;
        }

        double[] trunkTip() {
            return trunk.at(Math.min(s, trunk.length));
        }

        /** Pushes a crown point out and up as the tree grows ancient. */
        double[] aged(double[] c) {
            if (old <= 0) {
                return c;
            }
            double h = Math.max(1e-6, Math.hypot(c[0], c[2]));
            return new double[] {c[0] + c[0] / h * 3 * old, c[1] + 1.5 * old, c[2] + c[2] / h * 3 * old};
        }
    }

    TreeVoxels build(double age, TreeStage stage) {
        TreeVoxels v = new TreeVoxels();
        Grown gr = new Grown(age, stage == TreeStage.LIVING);
        if (gr.g < 0.06) {
            v.put(0, 0, 0, TreePart.SEEDLING);
            return v;
        }
        List<double[]> fallAt = new ArrayList<>();
        if (stage == TreeStage.FALLEN) {
            List<Double> lengths = new ArrayList<>();
            for (Limb limb : limbs) {
                if (limb.parent == null) {
                    lengths.add(gr.upto(limb));
                }
                fallPoints(gr, limb, fallAt);
            }
            DeadWood.fallen(v, seed, gr.r0(), gr.k, gr.g, gr.old, DeadWood.heavierSide(build(age, TreeStage.SHED), fallAz), gr.trunkTip()[1],
                    lengths.stream().mapToDouble(Double::doubleValue).toArray(), fallAt);
            return v;
        }
        TreePart[] blocks = gr.living ? LIVE : DEAD;
        drawTrunk(v, gr, blocks);
        if (gr.living && gr.s < trunk.length) {
            youngTree(v, gr);
            return v;
        }
        List<double[]> grownLeaves = new ArrayList<>(), growingLeaves = new ArrayList<>();
        // Where each limb's leaves start in the two lists (they only grow, in limb order), for the ring between them.
        int[] grownFrom = new int[limbs.size() + 1], growingFrom = new int[limbs.size() + 1];
        for (int li = 0; li < limbs.size(); li++) {
            grownFrom[li] = grownLeaves.size();
            growingFrom[li] = growingLeaves.size();
            Limb limb = limbs.get(li);
            boolean dead = !gr.living || gr.deadLimb(limb);
            TreePart[] b = dead ? DEAD : LIVE;
            double lu = gr.upto(limb);
            double broken = broken(gr, limb, stage, dead);
            if (limb.parent != null) {
                // A split limb grows from where it leaves its parent: none if the parent broke off below it.
                double parentBroken = broken(gr, limb.parent, stage, dead);
                if (parentBroken >= 0 && limb.alongParent > parentBroken) {
                    continue;
                }
            }
            if (broken >= 0) {
                TreeDraw.broken(v, limb.line, gr.radii(limb), lu, broken, b);
                if (stage == TreeStage.SHED) {
                    fallPoints(gr, limb, fallAt);
                }
                continue;
            }
            TreeDraw.growing(v, limb.line, gr.radii(limb), lu, true, b, 0);
            if (lu <= 0) {
                continue;
            }
            if (lu < limb.line.length) {
                // A growing limb: leaves round its tip and all along its upper part, so it grows inside its crown.
                if (gr.living) {
                    growingLeaves.add(limb.line.at(lu));
                    for (double d = lu - 2.5; d > lu * 0.35; d -= 2.5) {
                        growingLeaves.add(limb.line.at(d));
                    }
                }
                continue;
            }
            if (gr.living && gr.g < 1 && gr.s < limb.end() + 6) {
                // Its leaves along the limb stay until its branches have grown out round its end.
                for (double d = lu - 2.5; d > lu * 0.35; d -= 2.5) {
                    growingLeaves.add(limb.line.at(d));
                }
            }
            double[] end = limb.tip();
            if (gr.living) {
                grownLeaves.add(gr.aged(end));
            }
            for (Branch br : limb.branches) {
                double bu = clamp(gr.s - br.start, 0, br.l1 + br.l2);
                if (dead) {
                    bu = Math.min(bu, (br.l1 + br.l2) * br.brk);
                }
                if (bu <= 0) {
                    continue;
                }
                TreePart pipe = dead ? TreePart.DEAD_LIMB : TreePart.LIMB, twig = dead ? TreePart.DEAD_THIN : TreePart.THIN;
                double[] reached;
                if (bu <= br.l1) {
                    reached = mix(end, br.mid, bu / br.l1);
                    thinLine(v, end, br.mid, bu / br.l1, pipe, false);
                } else {
                    thinLine(v, end, br.mid, pipe);
                    reached = bu >= br.l1 + br.l2 ? br.tip : mix(br.mid, br.tip, (bu - br.l1) / br.l2);
                    thinLine(v, br.mid, br.tip, Math.min(1, (bu - br.l1) / br.l2), twig, false);
                }
                if (dead) {
                    continue;
                }
                boolean done = bu >= br.l1 + br.l2;
                if (done && gr.old > 0) {
                    thinLine(v, br.tip, gr.aged(br.tip), TreePart.THIN);
                }
                if (done) {
                    grownLeaves.add(gr.aged(br.tip));
                } else {
                    growingLeaves.add(reached);
                }
                if (bu >= br.l1) {
                    grownLeaves.add(gr.aged(br.mid));
                }
            }
        }
        grownFrom[limbs.size()] = grownLeaves.size();
        growingFrom[limbs.size()] = growingLeaves.size();
        if (gr.living) {
            List<List<double[]>> perLimb = new ArrayList<>();
            for (int li = 0; li < limbs.size(); li++) {
                List<double[]> mine = new ArrayList<>(grownLeaves.subList(grownFrom[li], grownFrom[li + 1]));
                mine.addAll(growingLeaves.subList(growingFrom[li], growingFrom[li + 1]));
                perLimb.add(mine);
            }
            ring(v, gr, perLimb);
            if (gr.s < trunk.length + 6) {
                // The young crown over the stem stays a little while the limbs start out from under it.
                bush(v, gr.trunkTip(), youngCrown(trunk.length), 0.12);
            }
            crown(v, gr, grownLeaves, growingLeaves);
        } else {
            DeadWood.weather(v, seed, gr.trunkTip()[1], gr.r0(), stage);
            if (stage == TreeStage.SHED) {
                DeadWood.scatterFallen(v, seed, gr.k, fallAt);
            }
        }
        return v;
    }

    /** How far out a limb still holds (-1: whole): a shed snag's limbs broke off, an ancient tree's dead limb lost its tip. */
    private static double broken(Grown gr, Limb limb, TreeStage stage, boolean dead) {
        if (stage == TreeStage.SHED) {
            return limb.line.length * limb.brk * 0.6;
        }
        return gr.deadLimb(limb) ? limb.line.length * 0.85 : -1;
    }

    private void drawTrunk(TreeVoxels v, Grown gr, TreePart[] blocks) {
        double r0 = gr.r0();
        double hollow = gr.old > 0.5 ? 0.35 * gr.old : 0;
        // The flared foot.
        if (gr.k > 0.25) {
            seg(v, new double[] {0, -1, 0}, new double[] {0, 1.5, 0}, r0 + 1.4 * smooth(0.25, 0.6, gr.k), r0, blocks, false, hollow);
        }
        double tf = gr.tf, add = 0.5 * gr.old;
        TreeDraw.growing(v, trunk, new double[] {trunkRadius * tf + add, trunkRadius * 0.93 * tf + add, trunkRadius * 0.85 * tf + add},
                Math.min(gr.s, trunk.length), true, blocks, hollow);
        if (gr.k > 0.2) {
            for (double[] rt : roots) {
                double[] d = {Math.cos(rt[0]), 0, Math.sin(rt[0])};
                seg(v, add(mul(d, r0 * 0.7), new double[] {0, -0.2, 0}), add(mul(d, r0 + rt[1] * gr.k), new double[] {0, -0.6, 0}),
                        0.5 + 0.9 * gr.k, 0.45, blocks);
            }
        }
    }

    /** How wide the young crown is when the stem has grown this far. */
    private double youngCrown(double s) {
        return 1.6 + (crownRadius - 1.6) * clamp(s / reach, 0, 1) * 0.9;
    }

    /** The young tree: the stem with a small flat crown at its tip, and a few low twigs that are shaded out and dropped. */
    private void youngTree(TreeVoxels v, Grown gr) {
        double[] tip = gr.trunkTip();
        double rc = youngCrown(gr.s);
        double ry = Math.max(1.3, rc * 0.38);
        double baseAt = Math.max(1, gr.s - ry * 1.6);
        double[] base = trunk.at(baseAt), corner = trunk.cornerBefore(baseAt);
        thinLine(v, corner, base, TreePart.THIN, true);
        v.dropping = true;
        for (int i = 0; i < Math.min(3, limbs.size()); i++) {
            double az = limbs.get(i).az;
            thinLine(v, base, new double[] {base[0] + Math.cos(az) * rc * 0.5, tip[1] + 1 - ry * 0.8, base[2] + Math.sin(az) * rc * 0.5}, TreePart.THIN);
        }
        v.dropping = false;
        bush(v, tip, rc, 0.12);
    }

    /** A small flat-topped bush of leaves over a point (a young crown, a growing limb's tip), thorny under its rim. */
    private static void bush(TreeVoxels v, double[] at, double rc, double thorns) {
        int top = (int) Math.round(at[1]) + 1;
        double ry = Math.max(1.3, rc * 0.38), cy = top - ry + 0.5;
        List<int[]> placed = new ArrayList<>();
        for (int x = (int) Math.floor(at[0] - rc - 1); x <= at[0] + rc + 1; x++) {
            for (int z = (int) Math.floor(at[2] - rc - 1); z <= at[2] + rc + 1; z++) {
                for (int y = (int) Math.floor(cy - ry); y <= top; y++) {
                    double e = sq((x - at[0]) / rc) + sq((z - at[2]) / rc) + sq((y - cy) / ry);
                    if (e > 1 || y < 1) {
                        continue;
                    }
                    if (e > 0.7 && TreeRandom.hash(x, y, z, 91) > 0.55) {
                        continue;
                    }
                    v.put(x, y, z, TreePart.LEAVES);
                    placed.add(new int[] {x, y, z});
                }
            }
        }
        for (int[] p : placed) {
            int x = p[0], y = p[1], z = p[2];
            if (v.get(x, y - 1, z) == null && Math.hypot(x - at[0], z - at[2]) > rc * 0.6 && TreeRandom.hash(x, y, z, 92) < thorns) {
                v.put(x, y - 1, z, TreePart.THORNS);
            }
        }
    }

    /**
     * The crown: a flattened clump of leaves round every grown branch tip and middle and limb end, clipped between the
     * domed top and the flat underside; smaller young bushes round the wood still growing; thorny twigs under the rim.
     */
    private void crown(TreeVoxels v, Grown gr, List<double[]> grownLeaves, List<double[]> growingLeaves) {
        TreeKind.Crown c = kind.crown();
        double density = c.leafDensity(), old = gr.old;
        double rc = crownRadius + 3 * old;
        double pad = lerp(0.5, 1, gr.k);
        for (double[] cen : grownLeaves) {
            double rx = pad * c.padRadius() * (0.6 + 0.4 * TreeRandom.hash((int) Math.round(cen[0]), 3, (int) Math.round(cen[2]), 3));
            double ry = THICK * 0.45;
            for (int x = (int) Math.floor(cen[0] - rx); x <= Math.ceil(cen[0] + rx); x++) {
                for (int z = (int) Math.floor(cen[2] - rx); z <= Math.ceil(cen[2] + rx); z++) {
                    double hd = Math.hypot(x, z);
                    if (hd > rc * (0.95 + 0.08 * TreeRandom.hash(x, 1, z, 6))) {
                        continue;
                    }
                    // An ancient crown: the same shape pushed out at the edge and up.
                    double d0 = Math.max(0, hd - 3 * old);
                    double lumpTop = top(d0, old) + (TreeRandom.hash(x, 0, z, 5) < 0.3 ? 1 : 0);
                    double lumpLow = under(d0, old) - (TreeRandom.hash(x, 2, z, 8) < 0.25 ? 1 : 0);
                    for (int y = (int) Math.floor(cen[1] - ry); y <= Math.ceil(cen[1] + ry); y++) {
                        double e = sq((x - cen[0]) / rx) + sq((z - cen[2]) / rx) + sq((y - cen[1]) / ry);
                        if (e > 1 || y > lumpTop || y < lumpLow) {
                            continue;
                        }
                        if (TreeRandom.hash(x, y, z, 7) > density * (1 - 0.25 * e)) {
                            continue;
                        }
                        v.put(x, y, z, TreePart.LEAVES);
                    }
                }
            }
        }
        if (old > 0) {
            // An ancient crown (the user's ask, 2026-10-18): its clumps have long since joined into one full, dense
            // ring round the crown, also over a dead limb's gap, the neighbours' twigs grown into it.
            int r = (int) Math.ceil(rc + 1);
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    double hd = Math.hypot(x, z);
                    if (hd < rc * ANCIENT_RING_INNER || hd > rc * (0.95 + 0.08 * TreeRandom.hash(x, 1, z, 6))) {
                        continue;
                    }
                    double d0 = Math.max(0, hd - 3 * old);
                    double lumpTop = top(d0, old) + (TreeRandom.hash(x, 0, z, 5) < 0.3 ? 1 : 0);
                    double lumpLow = under(d0, old) - (TreeRandom.hash(x, 2, z, 8) < 0.25 ? 1 : 0);
                    for (int y = (int) Math.ceil(lumpLow); y <= lumpTop; y++) {
                        if (TreeRandom.hash(x, y, z, 7) < density) {
                            v.put(x, y, z, TreePart.LEAVES);
                        }
                    }
                }
            }
        }
        double small = lerp(2, c.padRadius() * 0.7, gr.k);
        for (double[] cen : growingLeaves) {
            bush(v, cen, small, 0);
        }
        // Thorny twigs hanging from the rim of the crown (a growing crown's rim is where its leaves reach now).
        double[] far = {0};
        if (gr.g < 1) {
            v.forEach((x, y, z, p) -> {
                if (p == TreePart.LEAVES) {
                    far[0] = Math.max(far[0], Math.hypot(x, z));
                }
            });
        }
        double rim = gr.g < 1 ? far[0] * 0.7 : crownRadius * 0.75 + 3 * old;
        List<int[]> twigs = new ArrayList<>();
        v.forEach((x, y, z, p) -> {
            if (p == TreePart.LEAVES && v.get(x, y - 1, z) == null && Math.hypot(x, z) > rim && TreeRandom.hash(x, y, z, 92) < 0.2) {
                twigs.add(new int[] {x, y - 1, z});
            }
        });
        for (int[] p : twigs) {
            v.put(p[0], p[1], p[2], TreePart.THORNS);
        }
    }

    /**
     * The clumps widening toward a ring (the user's ask, 2026-10-18): a real umbrella thorn's limbs spread their leafy
     * twigs sideways until the clumps join into one flat crown. From when the limbs reach out, each limb's clump widens
     * round the crown's circle toward its neighbours', a little more every year, but stays apart from them: it only
     * shows the crown will join up later.
     */
    private void ring(TreeVoxels v, Grown gr, List<List<double[]>> perLimb) {
        double limbsEnd = 0;
        for (Limb limb : limbs) {
            limbsEnd = Math.max(limbsEnd, limb.end());
        }
        double out = (gr.s - trunk.length) / (limbsEnd - trunk.length); // how far the limbs have grown out
        double fill = clamp((out - RING_FROM) / (RING_FULL - RING_FROM), 0, 1);
        if (fill <= 0 || gr.g >= 1) {
            return;
        }
        // Each leafy limb: {middle angle round the trunk, lowest angle, highest angle, distance out, height}.
        List<double[]> arms = new ArrayList<>();
        for (List<double[]> mine : perLimb) {
            if (mine.isEmpty()) {
                continue;
            }
            double sx = 0, sz = 0, dist = 0, y = 0;
            for (double[] c : mine) {
                double h = Math.max(1e-6, Math.hypot(c[0], c[2]));
                sx += c[0] / h;
                sz += c[2] / h;
                dist += h;
                y += c[1];
            }
            dist /= mine.size();
            if (dist < 3) {
                continue; // leaves still round the stem: nothing to spread round yet
            }
            double mid = Math.atan2(sz, sx), lo = 0, hi = 0;
            for (double[] c : mine) {
                double a = wrap(Math.atan2(c[2], c[0]) - mid);
                lo = Math.min(lo, a);
                hi = Math.max(hi, a);
            }
            arms.add(new double[] {mid, mid + lo, mid + hi, dist, y / mine.size()});
        }
        if (arms.size() < 2) {
            return;
        }
        Integer[] order = new Integer[arms.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (p, q) -> Double.compare(arms.get(p)[0], arms.get(q)[0]));
        double size = lerp(2, kind.crown().padRadius() * 0.7, gr.k);
        for (int n = 0; n < order.length; n++) {
            int i = order[n], j = order[(n + 1) % order.length];
            double[] a = arms.get(i), b = arms.get(j);
            double gap = ((b[1] - a[2]) % TAU + TAU) % TAU;
            if (gap > TAU * 0.9 || gap < 0.15) {
                continue; // overlapping already, or one clump wrapping round
            }
            widen(v, a[2], +1, gap, a, b, fill, size);
            widen(v, b[1], -1, gap, b, a, fill, size);
        }
    }

    /**
     * One limb's clump widening round the crown's circle from its side edge (at angle a0) toward the next clump:
     * overlapping bushes, smaller toward the front, so the wider clump stays one piece on the tree.
     */
    private static void widen(TreeVoxels v, double a0, int way, double gap, double[] me, double[] other, double fill, double size) {
        double reach = gap * RING_WIDEN * fill;
        int steps = (int) Math.ceil(reach * me[3] / 1.5);
        for (int s = 1; s <= steps; s++) {
            double t = reach * s / steps, f = t / (gap * RING_WIDEN); // f: how far out it widens, 0 to 1
            double az = a0 + way * t;
            double dist = lerp(me[3], other[3], t / gap), y = lerp(me[4], other[4], t / gap);
            bush(v, new double[] {Math.cos(az) * dist, y, Math.sin(az) * dist}, size * lerp(0.85, 0.5, f), 0);
        }
    }

    private static double wrap(double a) {
        while (a > Math.PI) {
            a -= TAU;
        }
        while (a < -Math.PI) {
            a += TAU;
        }
        return a;
    }

    /** Where a limb's branches end now (or the limb's own tip while it has none yet): where its pieces fall. */
    private void fallPoints(Grown gr, Limb limb, List<double[]> out) {
        double lu = gr.upto(limb);
        if (lu <= 0) {
            return;
        }
        for (Branch br : limb.branches) {
            double bu = clamp(gr.s - br.start, 0, br.l1 + br.l2);
            out.add(bu <= 0 ? limb.line.at(lu) : bu <= br.l1 ? mix(br.from, br.mid, bu / br.l1) : mix(br.mid, br.tip, Math.min(1, (bu - br.l1) / br.l2)));
        }
    }

    /**
     * A limb's curve (a cubic Bezier): out of its start at an angle above level, sagging on the way out, its end just
     * inside the crown's underside, limbReach from the middle.
     */
    private double[][] limbCurve(double[] start, double az, double rise, double limbReach, double sag) {
        double[] dir = {Math.cos(az), 0, Math.sin(az)};
        double startD = start[0] * dir[0] + start[2] * dir[2];
        double outD = Math.max(3, limbReach - startD);
        double[] end = {Math.cos(az) * limbReach, 0, Math.sin(az) * limbReach};
        end[0] += start[0] - dir[0] * startD;
        end[2] += start[2] - dir[2] * startD;
        end[1] = under(Math.hypot(end[0], end[2]), 0) + 1;
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

    /** A limb along its curve: round bark tapering to LIMB_MIN at its end, in pieces about 1.5 blocks long. */
    private static Limb limb(double[][] curve, double rStart, double start, double az) {
        double length = 0;
        double[] prev = curve[0];
        for (int i = 1; i <= 20; i++) {
            double[] p = bezier(curve, i / 20.0);
            length += len(sub(p, prev));
            prev = p;
        }
        int steps = Math.max(2, (int) Math.ceil(length / 1.5));
        double[][] pts = new double[steps + 1][];
        Limb limb = new Limb();
        limb.radii = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            pts[i] = bezier(curve, t);
            limb.radii[i] = Math.max(LIMB_MIN, lerp(rStart, LIMB_MIN, Math.pow(t, 0.8)));
        }
        limb.line = new Poly(pts);
        limb.start = start;
        limb.az = az;
        return limb;
    }

    /** How far along a limb's line the point at curve parameter t is (its points are evenly spaced in t). */
    private static double alongCurve(Limb limb, double t) {
        int steps = limb.line.pts.length - 1;
        double i = clamp(t, 0, 1) * steps;
        int lo = (int) Math.floor(i), hi = Math.min(steps, lo + 1);
        return lerp(limb.line.cum[lo], limb.line.cum[hi], i - lo);
    }

    private static double sq(double x) {
        return x * x;
    }
}
