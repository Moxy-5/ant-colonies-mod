package com.antcolonies.tree;

import static com.antcolonies.tree.TreeDraw.DEAD;
import static com.antcolonies.tree.TreeDraw.LIVE;
import static com.antcolonies.tree.TreeDraw.add;
import static com.antcolonies.tree.TreeDraw.clamp;
import static com.antcolonies.tree.TreeDraw.len;
import static com.antcolonies.tree.TreeDraw.lerp;
import static com.antcolonies.tree.TreeDraw.mix;
import static com.antcolonies.tree.TreeDraw.mul;
import static com.antcolonies.tree.TreeDraw.seg;
import static com.antcolonies.tree.TreeDraw.smooth;
import static com.antcolonies.tree.TreeDraw.sub;
import static com.antcolonies.tree.TreeDraw.thinLine;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.tree.TreeDraw.Poly;

/**
 * The shape of an umbrella thorn or a camel thorn (M7.5) at any age and stage, ported from the tree viewer.
 *
 * <p>Real growth (the user's decision, 2026-10-18): the skeleton is the grown tree's, made once from the seed (the same
 * random numbers in the same order as the viewer, so the grown tree is the one the user approved). A younger tree is
 * that skeleton grown only part of the way out from the ground: every piece of wood keeps its place and only lengthens
 * at its tip and thickens, so a tree never moves wood as it ages. A young tree is its leader stem (the trunk and its
 * first limb) with a small crown; its low side branches are shaded out and dropped as the crown climbs.
 *
 * <p>Age: 0 a seedling, 1 grown, up to 1.25 ancient (thicker, the branch tips pushed a little out and up, the heart
 * rotted hollow, often one limb dead).
 */
final class AcaciaShape {
    private static final double TAU = Math.PI * 2;

    private final TreeKind kind;
    private final TreeKind.Shape shape;
    private final long seed;
    private final boolean domed;
    private final double height, radiusGrowth, crownRadius, forkHeight;
    private final double[] fork;
    private final Poly trunk;
    private final List<Limb> limbs = new ArrayList<>();
    private final List<double[]> roots = new ArrayList<>();
    private final double fallAz;
    /** The farthest any wood reaches from the ground, along the tree: the grown tree is this far grown. */
    private final double reach;

    private static final class Limb {
        double az, top;
        Poly line;
        double start, end;
        final List<Branch> branches = new ArrayList<>();
        double brk, deadRoll;
    }

    private static final class Branch {
        double[] from, tip;
        double start, length, brk;
        final List<Twig> twigs = new ArrayList<>();
    }

    private static final class Twig {
        double at, start, length;
        double[] from, to;
    }

    AcaciaShape(TreeKind kind, long seed) {
        this.kind = kind;
        this.seed = seed;
        shape = kind.shapeFor(seed);
        TreeKind.Trunk t = kind.trunk();
        TreeKind.Crown c = kind.crown();
        domed = c.dome() > 0;
        TreeRandom r = new TreeRandom(seed * 7919 + kind.salt());
        height = t.height() * shape.heightScale() + (r.next() - 0.5) * t.heightSpread() * 2;
        radiusGrowth = t.radius() - 0.35 + (r.next() - 0.5) * t.radiusSpread() * 2;
        crownRadius = c.radius() * shape.crownScale();
        double ct = c.thickness();
        forkHeight = height * (t.fork().get(0) + r.next() * (t.fork().get(1) - t.fork().get(0)));
        double leanA = r.next() * TAU, leanD = r.next() * 1.6;
        fork = new double[] {Math.cos(leanA) * leanD, forkHeight, Math.sin(leanA) * leanD};
        double[] mid = {(r.next() - 0.5) * 1.4 + fork[0] * 0.4, forkHeight * 0.5, (r.next() - 0.5) * 1.4 + fork[2] * 0.4};
        trunk = new Poly(new double[] {0, -1, 0}, mid, fork);
        int lo = c.limbs().get(0), hi = c.limbs().get(1);
        int n = lo + (int) Math.floor(r.next() * (hi - lo + 1));
        double phase = r.next() * TAU;
        double far = 0;
        for (int i = 0; i < n; i++) {
            Limb limb = new Limb();
            limb.az = phase + i * TAU / n + (r.next() - 0.5) * 0.7;
            int tier = shape.tiers() ? new int[] {0, 2, 1}[i % 3] : 0;
            limb.top = height - new double[] {0, 5, 9}[tier];
            double reachOut = crownRadius * new double[] {1, 0.8, 0.62}[tier];
            double domeTop = limb.top - shape.domeScale() * c.dome() * 0.35;
            List<double[]> pts = new ArrayList<>();
            pts.add(fork);
            for (int b = 1; b <= c.bends(); b++) {
                double f = (double) b / c.bends(), a2 = limb.az + (r.next() - 0.5) * 0.55;
                double rad = reachOut * 0.55 * Math.pow(f, 1.25);
                pts.add(new double[] {fork[0] + Math.cos(a2) * rad, forkHeight + (domeTop - ct * 0.85 - forkHeight) * Math.pow(f, 0.75),
                        fork[2] + Math.sin(a2) * rad});
            }
            limb.line = new Poly(pts.toArray(new double[0][]));
            limb.start = trunk.length;
            limb.end = limb.start + limb.line.length;
            double[] end = pts.get(pts.size() - 1);
            int m = 3 + (int) Math.floor(r.next() * 2);
            for (int j = 0; j < m; j++) {
                Branch br = new Branch();
                double baz = limb.az + (r.next() - 0.5) * 1.5, tr = reachOut * (0.42 + r.next() * 0.5);
                br.from = end;
                br.tip = new double[] {Math.cos(baz) * tr, domeTop - ct * 0.5 + (r.next() - 0.5) * 1.2, Math.sin(baz) * tr};
                br.start = limb.end;
                br.length = len(sub(br.tip, end));
                br.brk = 0.3 + r.next() * 0.4;
                int q = 4 + (int) Math.floor(r.next() * 3);
                for (int w = 0; w < q; w++) {
                    Twig tw = new Twig();
                    double ta = baz + (r.next() - 0.5) * 2.4, tl = (3 + r.next() * 5) + 0.6;
                    tw.at = 0.55 + r.next() * 0.45;
                    double dy = (r.next() - 0.25) * 1.2;
                    tw.from = mix(end, br.tip, tw.at);
                    double[] te = add(tw.from, new double[] {Math.cos(ta) * tl, dy, Math.sin(ta) * tl});
                    double th = Math.hypot(te[0], te[2]), lim = crownRadius * 0.9;
                    if (th > lim) {
                        te[0] *= lim / th;
                        te[2] *= lim / th;
                    }
                    tw.to = te;
                    tw.start = br.start + tw.at * br.length;
                    tw.length = len(sub(te, tw.from));
                    far = Math.max(far, tw.start + tw.length);
                    br.twigs.add(tw);
                }
                far = Math.max(far, br.start + br.length);
                limb.branches.add(br);
            }
            limb.brk = 0.45 + r.next() * 0.4;
            limb.deadRoll = r.next();
            limbs.add(limb);
        }
        int nr = 3 + (int) Math.floor(r.next() * 3);
        for (int i = 0; i < nr; i++) {
            roots.add(new double[] {r.next() * TAU, 4 + r.next() * 5});
        }
        fallAz = r.next() * TAU;
        reach = far;
    }

    /** The tree at an age, grown out along its skeleton. */
    private final class Grown {
        final double g, old, k, s, r0, ct, flat;
        final boolean living;

        Grown(double age, boolean living) {
            this.living = living;
            g = clamp(age, 0, 1);
            old = clamp((age - 1) / 0.25, 0, 1);
            k = Math.pow(g, 1.5);
            s = old > 0 ? reach + 1 : reach * k;
            // The new growth: girth comes sooner than height (a real young tree thickens every year), but a sapling stays slim.
            double kr = TreeDraw.earlyGrowth ? lerp(k, Math.pow(g, 0.7), smooth(0.1, 0.45, g)) : k;
            r0 = 0.35 + radiusGrowth * kr + 0.5 * old;
            ct = lerp(2.5, kind.crown().thickness(), k);
            flat = smooth(0.25, 0.8, g);
        }

        double trunkUpto() {
            return Math.min(s, trunk.length);
        }

        double limbUpto(Limb limb) {
            return clamp(s - limb.start, 0, limb.line.length);
        }

        double branchUpto(Branch br) {
            return clamp(s - br.start, 0, br.length);
        }

        double twigUpto(Twig tw) {
            return clamp(s - tw.start, 0, tw.length);
        }

        boolean deadLimb(Limb limb) {
            return old > 0.5 && limb == limbs.get(0) && limb.deadRoll < 0.7;
        }

        /** Where the trunk ends now (the fork once it is grown). */
        double[] trunkTip() {
            return trunk.at(trunkUpto());
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
        if (stage == TreeStage.FALLEN) {
            buildFallen(v, gr);
            return v;
        }
        TreePart[] blocks = gr.living ? LIVE : DEAD;
        // The young form: a stem with a bush on top. With the new growth only until the trunk reaches the fork, then
        // every limb grows from there inside its leaves.
        boolean young = TreeDraw.earlyGrowth ? gr.s < trunk.length + 0.5 : gr.g < 0.45;
        drawTrunk(v, gr, blocks);
        if (young && gr.living) {
            youngTree(v, gr);
            return v;
        }
        List<double[]> fallAt = new ArrayList<>();
        for (Limb limb : limbs) {
            boolean dead = !gr.living || gr.deadLimb(limb);
            TreePart[] b = dead ? DEAD : LIVE;
            double grown = gr.limbUpto(limb);
            if (stage == TreeStage.SHED) {
                double broken = limb.line.length * limb.brk * 0.6;
                TreeDraw.growing(v, limb.line, limbRadii(gr, limb), Math.min(grown, broken), grown < broken, b, 0);
                limbFallPoints(gr, limb, fallAt);
                continue;
            }
            if (dead && gr.living) {
                double broken = limb.line.length * 0.85;
                TreeDraw.growing(v, limb.line, limbRadii(gr, limb), Math.min(grown, broken), grown < broken, b, 0);
                continue;
            }
            TreeDraw.growing(v, limb.line, limbRadii(gr, limb), grown, true, b, 0);
            limbFallPoints(gr, limb, fallAt);
            if (grown < limb.line.length) {
                continue;
            }
            double[] end = limb.line.pts[limb.line.pts.length - 1];
            for (Branch br : limb.branches) {
                double upto = gr.branchUpto(br);
                if (dead) {
                    upto = Math.min(upto, br.length * br.brk);
                }
                if (upto <= 0) {
                    continue;
                }
                if (tapered()) {
                    taperedBranch(v, gr, end, br, upto, dead, b);
                } else {
                    Poly line = new Poly(end, br.tip);
                    TreeDraw.growing(v, line, new double[] {gr.r0 * TreeDraw.look.branchFrom, dead ? gr.r0 * 0.3 : TreeDraw.look.branchTip}, upto, !dead, b, 0);
                }
                if (dead) {
                    continue;
                }
                if (gr.old > 0) {
                    thinLine(v, br.tip, gr.aged(br.tip), TreePart.THIN);
                }
                for (Twig tw : br.twigs) {
                    double tu = gr.twigUpto(tw);
                    if (tu <= 0) {
                        continue;
                    }
                    // Along the branch to the twig's foot first (empty blocks only), so the twig is joined to it.
                    thinLine(v, end, tw.from, TreePart.THIN, true);
                    double[] to = mix(tw.from, tw.to, tu / tw.length);
                    thinLine(v, tw.from, to, TreePart.THIN);
                    if (gr.old > 0) {
                        thinLine(v, tw.to, gr.aged(tw.to), TreePart.THIN);
                    }
                }
            }
        }
        if (gr.living) {
            crown(v, gr);
            if (kind.crown().pods()) {
                hangPods(v);
            }
        } else {
            weather(v, gr, stage);
            if (stage == TreeStage.SHED) {
                scatterFallen(v, gr, fallAt);
            }
        }
        return v;
    }

    private double[] limbRadii(Grown gr, Limb limb) {
        int count = limb.line.pts.length;
        double[] out = new double[count];
        double from = tapered() ? TAPER_LIMB_FROM : TreeDraw.look.limbFrom, to = tapered() ? TAPER_LIMB_TO : TreeDraw.look.limbTo;
        for (int i = 0; i < count; i++) {
            out[i] = gr.r0 * lerp(from, to, (double) i / (count - 1));
        }
        return out;
    }

    /*
     * The tapering wood (the user's idea, 2026-10-18, for the camel thorn: "the branches once they start that's the
     * thickest possible part, then it gets a bit smaller each 2 blocks after until the skinniest part is not exposed at
     * all and it's in the tree's leaves"). Real branches are thickest where they leave the limb and thinner after every
     * stretch (the pipe model), and the thinnest wood is out among the leaves. So the limbs stay thick (round bark) up
     * to the crown; each branch starts as thick as the limb's end allows and loses a little every 2 blocks, never thinner
     * than the slimmest round bark, until it is inside the leaf pad round its tip, where it goes on as a thin branch.
     */
    private static final double TAPER_LIMB_FROM = 0.95, TAPER_LIMB_TO = 0.75;
    /** A branch's radius where it leaves its limb (of the trunk's), what it loses every 2 blocks, the slimmest bark. */
    private static final double TAPER_BRANCH_FROM = 0.6, TAPER_STEP = 0.12, TAPER_MIN = 0.8;
    /** How far in from a branch's tip it is thin (of the leaf pad's radius): that much is inside the leaves. */
    private static final double TAPER_THIN_IN = 0.6;

    /** The tapering wood is the camel thorn's in the game (limb look A); the viewer's other limb looks keep theirs. */
    private boolean tapered() {
        return TreeDraw.look == TreeDraw.LimbLook.A;
    }

    /** A branch from its limb's end to its tip, tapering every 2 blocks, thin only inside the leaves. */
    private void taperedBranch(TreeVoxels v, Grown gr, double[] end, Branch br, double upto, boolean dead, TreePart[] b) {
        double thinFrom = Math.max(Math.min(2, br.length), br.length - kind.crown().padRadius() * TAPER_THIN_IN);
        double[] dir = mul(sub(br.tip, end), 1 / Math.max(1e-6, br.length));
        int n = Math.max(1, (int) Math.ceil(thinFrom / 2));
        double[][] pts = new double[n + 1][];
        double[] radii = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double d = thinFrom * i / n;
            pts[i] = add(end, mul(dir, d));
            radii[i] = Math.max(TAPER_MIN, gr.r0 * TAPER_BRANCH_FROM - TAPER_STEP * d / 2);
        }
        TreeDraw.growing(v, new Poly(pts), radii, Math.min(upto, thinFrom), !dead, b, 0);
        if (upto > thinFrom) {
            thinLine(v, pts[n], add(end, mul(dir, upto)), dead ? TreePart.DEAD_THIN : TreePart.THIN);
        }
    }

    private void drawTrunk(TreeVoxels v, Grown gr, TreePart[] blocks) {
        double hollow = gr.old > 0.5 ? 0.35 * gr.old : 0;
        if (gr.k > 0.25) {
            seg(v, new double[] {0, -1, 0}, new double[] {0, 1.5, 0}, gr.r0 + 1.5 * smooth(0.25, 0.6, gr.k), gr.r0, blocks, false, hollow);
        }
        TreeDraw.growing(v, trunk, new double[] {gr.r0, gr.r0 * 0.95, gr.r0 * 0.85}, gr.trunkUpto(), true, blocks, hollow);
        if (gr.k > 0.2) {
            for (double[] rt : roots) {
                double[] d = {Math.cos(rt[0]), 0, Math.sin(rt[0])};
                seg(v, add(mul(d, gr.r0 * 0.7), new double[] {0, -0.2, 0}), add(mul(d, gr.r0 + rt[1] * gr.k), new double[] {0, -0.6, 0}),
                        0.5 + 0.9 * gr.k, 0.45, blocks);
            }
        }
    }

    /** The young tree: the leader stem (the trunk, then the first limb) with a small crown at its tip. */
    private void youngTree(TreeVoxels v, Grown gr) {
        Limb leader = limbs.get(0);
        double lu = gr.limbUpto(leader);
        TreeDraw.growing(v, leader.line, limbRadii(gr, leader), lu, true, LIVE, 0);
        double[] tip = lu > 0 ? leader.line.at(lu) : gr.trunkTip();
        double rc = 1.6 + (crownRadius - 1.6) * gr.k;
        int top = (int) Math.round(tip[1]) + 1;
        double ry = Math.max(1.3, rc * (domed ? 0.6 : 0.38)), cy = top - ry + 0.5;
        // A few low side branches under the crown: shaded out and dropped as the tree grows (self-pruning).
        double baseAt = Math.max(1, gr.s - ry * 1.6);
        double[] base = baseAt <= trunk.length ? trunk.at(baseAt) : leader.line.at(baseAt - trunk.length);
        double[] corner = baseAt <= trunk.length ? trunk.cornerBefore(baseAt) : leader.line.cornerBefore(baseAt - trunk.length);
        thinLine(v, corner, base, TreePart.THIN, true);
        v.dropping = true;
        for (int i = 0; i < Math.min(3, limbs.size()); i++) {
            double az = limbs.get(i).az;
            thinLine(v, base, new double[] {base[0] + Math.cos(az) * rc * 0.5, top - ry * 0.8, base[2] + Math.sin(az) * rc * 0.5}, TreePart.THIN);
        }
        v.dropping = false;
        for (int x = (int) Math.floor(tip[0] - rc - 1); x <= tip[0] + rc + 1; x++) {
            for (int z = (int) Math.floor(tip[2] - rc - 1); z <= tip[2] + rc + 1; z++) {
                for (int y = (int) Math.floor(cy - ry); y <= top; y++) {
                    double e = sq((x - tip[0]) / rc) + sq((z - tip[2]) / rc) + sq((y - cy) / ry);
                    if (e > 1 || y < 1) {
                        continue;
                    }
                    if (e > 0.7 && TreeRandom.hash(x, y, z, 91) > 0.55) {
                        continue;
                    }
                    v.put(x, y, z, TreePart.LEAVES);
                }
            }
        }
        List<int[]> rim = new ArrayList<>();
        v.forEach((x, y, z, b) -> {
            if (b == TreePart.LEAVES && v.get(x, y - 1, z) == null && Math.hypot(x - tip[0], z - tip[2]) > rc * 0.6
                    && TreeRandom.hash(x, y, z, 92) < 0.12) {
                rim.add(new int[] {x, y - 1, z});
            }
        });
        for (int[] p : rim) {
            v.put(p[0], p[1], p[2], TreePart.THORNS);
        }
    }

    /** The crown: flat pads of leaves round the branch tips and twig ends (domed for a camel thorn). */
    private void crown(TreeVoxels v, Grown gr) {
        TreeKind.Crown c = kind.crown();
        double young = 1 - smooth(0.15, 0.6, gr.g);
        double padBase = lerp(1.6, c.padRadius(), gr.k);
        List<List<double[]>> centresByLimb = new ArrayList<>();
        double maxH = 0;
        boolean all = true;
        for (Limb limb : limbs) {
            List<double[]> centres = new ArrayList<>();
            centresByLimb.add(centres);
            if (gr.deadLimb(limb)) {
                continue;
            }
            double lu = gr.limbUpto(limb);
            if (lu < limb.line.length) {
                all = false;
                if (lu > 0) {
                    // A growing limb: its tip, and a fan of young leafy shoots toward where its branches will be (thin
                    // twigs shed later, as the real branches grow out).
                    double[] tip = limb.line.at(lu), end = limb.line.pts[limb.line.pts.length - 1];
                    centres.add(tip);
                    if (TreeDraw.earlyGrowth) {
                        // Leaves all along the young limb's upper part, so it grows inside its crown.
                        for (double d = lu - 2.5; d > lu * 0.35; d -= 2.5) {
                            centres.add(limb.line.at(d));
                        }
                    }
                    double out = Math.min(padBase * 0.8, 1 + 6 * lu / limb.line.length);
                    v.dropping = true;
                    for (Branch br : limb.branches) {
                        double dx = br.tip[0] - end[0], dz = br.tip[2] - end[2], h = Math.max(1e-6, Math.hypot(dx, dz));
                        double[] shoot = {tip[0] + dx / h * out, tip[1] + 0.5, tip[2] + dz / h * out};
                        thinLine(v, tip, shoot, TreePart.THIN);
                        centres.add(shoot);
                    }
                    v.dropping = false;
                }
            } else {
                double[] end = limb.line.pts[limb.line.pts.length - 1];
                for (Branch br : limb.branches) {
                    double bu = gr.branchUpto(br);
                    if (bu < br.length) {
                        all = false;
                    }
                    centres.add(bu >= br.length ? gr.aged(br.tip) : mix(end, br.tip, bu / br.length));
                    for (Twig tw : br.twigs) {
                        double tu = gr.twigUpto(tw);
                        if (tu < tw.length) {
                            all = false;
                        }
                        if (tu > 0) {
                            centres.add(tu >= tw.length ? gr.aged(tw.to) : mix(tw.from, tw.to, tu / tw.length));
                        }
                    }
                }
            }
            for (double[] p : centres) {
                maxH = Math.max(maxH, Math.hypot(p[0], p[2]));
            }
        }
        double rcFull = crownRadius + 3 * gr.old;
        double rc = all ? rcFull : Math.min(rcFull, maxH + padBase + 1);
        for (int li = 0; li < limbs.size(); li++) {
            Limb limb = limbs.get(li);
            List<double[]> centres = centresByLimb.get(li);
            if (centres.isEmpty()) {
                continue;
            }
            double top = limb.top + 1.5 * gr.old;
            if (gr.s < limb.branches.stream().mapToDouble(b -> b.twigs.stream().mapToDouble(t -> t.start + t.length).max().orElse(0)).max().orElse(0)) {
                double maxY = centres.stream().mapToDouble(p -> p[1]).max().orElse(0);
                top = Math.min(top, maxY + gr.ct * 0.5 + 0.5);
            }
            for (double[] cen : centres) {
                double rx = padBase * (0.8 + 0.4 * TreeRandom.hash((int) Math.round(cen[0]), 0, (int) Math.round(cen[2]), 3));
                double ry = lerp(rx * 0.75, gr.ct * 0.5, gr.flat);
                double clipBase = top + (1 - gr.flat) * 12;
                double cy = gr.flat > 0.5 ? (domed ? cen[1] + 0.5 : top - ry + 0.7) : cen[1] + 0.6;
                for (int x = (int) Math.floor(cen[0] - rx); x <= Math.ceil(cen[0] + rx); x++) {
                    for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
                        for (int z = (int) Math.floor(cen[2] - rx); z <= Math.ceil(cen[2] + rx); z++) {
                            double e = sq((x - cen[0]) / rx) + sq((z - cen[2]) / rx) + sq((y - cy) / ry);
                            if (e > 1) {
                                continue;
                            }
                            double hd = Math.hypot(x, z);
                            if (hd > rc * (0.95 + 0.08 * TreeRandom.hash(x, 1, z, 6))) {
                                continue;
                            }
                            double clip = clipBase + (TreeRandom.hash(x, 0, z, 5) < 0.3 ? 1 : 0);
                            if (domed) {
                                clip = top - sq(hd / rc) * c.dome() * shape.domeScale() * gr.k + (TreeRandom.hash(x, 0, z, 5) < 0.4 ? 1 : 0);
                            }
                            if (y > clip || y < 1) {
                                continue;
                            }
                            double h = TreeRandom.hash(x, y, z, 7);
                            if (h > c.leafDensity() * (1 - 0.25 * e)) {
                                continue;
                            }
                            boolean edge = hd > 0.82 * rc && y < top - gr.ct * 0.35;
                            v.put(x, y, z, (young > 0.5 && h < 0.2) || (edge && h < 0.3) ? TreePart.THORNS : TreePart.LEAVES);
                        }
                    }
                }
            }
        }
    }

    private static void hangPods(TreeVoxels v) {
        List<int[]> pods = new ArrayList<>();
        v.forEach((x, y, z, b) -> {
            if (b == TreePart.LEAVES && v.get(x, y - 1, z) == null && TreeRandom.hash(x, y, z, 21) < 0.05) {
                pods.add(new int[] {x, y - 1, z});
            }
        });
        for (int[] p : pods) {
            v.put(p[0], p[1], p[2], TreePart.PODS);
        }
    }

    /** Dead wood: bark peels in patches; termite mud climbs the trunk from the ground. */
    private void weather(TreeVoxels v, Grown gr, TreeStage stage) {
        DeadWood.weather(v, seed, gr.trunkTip()[1], gr.r0, stage);
    }

    /** Where a limb's branches end now (or the limb's own tip while it has none yet): where its pieces fall. */
    private void limbFallPoints(Grown gr, Limb limb, List<double[]> out) {
        double lu = gr.limbUpto(limb);
        double[] end = limb.line.pts[limb.line.pts.length - 1];
        for (Branch br : limb.branches) {
            double bu = gr.branchUpto(br);
            out.add(bu > 0 ? mix(end, br.tip, bu / br.length) : limb.line.at(lu));
        }
    }

    /** Fallen limbs round a dead tree: termite food (fallen branches and bigger pieces). */
    private void scatterFallen(TreeVoxels v, Grown gr, List<double[]> points) {
        DeadWood.scatterFallen(v, seed, gr.k, points);
    }

    /** The fallen hollow giant: the stump, the trunk lying beside it with its limb stubs, rotted out, with galleries. */
    private void buildFallen(TreeVoxels v, Grown gr) {
        double[] lengths = new double[limbs.size()];
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < limbs.size(); i++) {
            lengths[i] = gr.limbUpto(limbs.get(i));
            limbFallPoints(gr, limbs.get(i), points);
        }
        DeadWood.fallen(v, seed, gr.r0, gr.k, gr.g, gr.old, fallAz, gr.trunkTip()[1], lengths, points);
    }

    private static double sq(double x) {
        return x * x;
    }
}
