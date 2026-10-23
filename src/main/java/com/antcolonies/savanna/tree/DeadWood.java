package com.antcolonies.tree;

import static com.antcolonies.tree.TreeDraw.DEAD;
import static com.antcolonies.tree.TreeDraw.add;
import static com.antcolonies.tree.TreeDraw.len;
import static com.antcolonies.tree.TreeDraw.lerp;
import static com.antcolonies.tree.TreeDraw.mul;
import static com.antcolonies.tree.TreeDraw.seg;
import static com.antcolonies.tree.TreeDraw.sub;
import static com.antcolonies.tree.TreeDraw.thinLine;

import java.util.ArrayList;
import java.util.List;

/**
 * What every dead acacia shares, whatever its shape (M7.5; moved out of {@link AcaciaShape} unchanged so the new
 * umbrella thorn, {@link UmbrellaShape}, dies the same way): bark peeling, termite mud climbing from the ground, fallen
 * pieces round it, and the fallen hollow giant.
 */
final class DeadWood {
    private static final double TAU = Math.PI * 2;

    private DeadWood() {
    }

    /**
     * Which way a dead tree falls (step 5c, the user's pick, "real topple"): toward its heavier side, where most of the
     * wood still on the shed tree hangs (above the stump, from 4 blocks up, mud-cased or bare). A tree whose weight sits right over its
     * stump keeps the way its dice gave.
     */
    static double heavierSide(TreeVoxels shed, double fallback) {
        double[] sum = new double[3];
        shed.forEach((x, y, z, b) -> {
            if (y >= 4 && (b.isWood() || b == TreePart.MUD)) { // wood under termite mud weighs the same
                sum[0] += x;
                sum[1] += z;
                sum[2]++;
            }
        });
        if (sum[2] == 0) {
            return fallback;
        }
        double cx = sum[0] / sum[2], cz = sum[1] / sum[2];
        return cx * cx + cz * cz < 0.25 ? fallback : Math.atan2(cz, cx);
    }

    /** Dead wood: bark peels in patches; termite mud climbs the trunk from the ground. trunkTop: the trunk's tip height. */
    static void weather(TreeVoxels v, long seed, double trunkTop, double r0, TreeStage stage) {
        boolean muddy = stage != TreeStage.SNAG;
        double mudTop = 4 + trunkTop * 0.9;
        v.forEach((x, y, z, b) -> {
            if (b != TreePart.DEAD_BARK) {
                return;
            }
            if (muddy) {
                double a = Math.atan2(z, x);
                double line = mudTop * (0.55 + 0.45 * (0.5 + 0.5 * Math.sin(a * 3 + seed))) + (stage == TreeStage.FALLEN ? -mudTop + 1.6 : 0);
                if (y <= line && TreeRandom.hash(x, y, z, 31) < 0.85) {
                    v.set(x, y, z, TreePart.MUD);
                    v.markOverBark(x, y, z);
                    return;
                }
            }
            if (TreeRandom.hash(x, Math.floorDiv(y, 3), z, 33) < 0.22) {
                v.set(x, y, z, TreePart.PEELING);
            }
        });
        if (muddy && stage != TreeStage.FALLEN) {
            // A collar of mud round the foot, where the termites come up.
            double r = r0 + 1.6;
            for (int x = (int) Math.floor(-r - 1); x <= r + 1; x++) {
                for (int z = (int) Math.floor(-r - 1); z <= r + 1; z++) {
                    if (Math.hypot(x, z) <= r + 0.6 && v.get(x, 0, z) == null && TreeRandom.hash(x, 0, z, 35) < 0.7) {
                        v.put(x, 0, z, TreePart.MUD);
                    }
                }
            }
        }
    }

    /** Fallen limbs round a dead tree: termite food (fallen branches and bigger pieces). k: how grown the tree is. */
    static void scatterFallen(TreeVoxels v, long seed, double k, List<double[]> points) {
        TreeRandom r = new TreeRandom(seed * 31 + 5);
        it.unimi.dsi.fastutil.longs.LongOpenHashSet before = v.keys();
        for (double[] tip : points) {
            double[] at = add(mul(tip, 0.75 + r.next() * 0.2), new double[] {(r.next() - 0.5) * 3, 0, (r.next() - 0.5) * 3});
            double a = r.next() * TAU, l = (3 + r.next() * 4) * Math.max(0.4, k);
            double[] p0 = {at[0], 0, at[2]}, p1 = {at[0] + Math.cos(a) * l, 0, at[2] + Math.sin(a) * l};
            if (r.next() < 0.35 && k > 0.5) {
                seg(v, add(p0, new double[] {0, 0.4, 0}), add(p1, new double[] {0, 0.4, 0}), 1.0, 0.85, DEAD, true, 0);
            } else {
                thinLine(v, p0, p1, TreePart.FALLEN_BRANCH);
            }
        }
        v.markLooseSince(before);
    }

    /**
     * The fallen hollow giant: the stump, the trunk lying beside it with its limb stubs, rotted out, with galleries.
     *
     * @param r0         the trunk's radius
     * @param k          how grown the tree is (0 to 1)
     * @param g          its age up to grown (0 to 1)
     * @param old        how far past grown it is (0 to 1, ancient)
     * @param trunkTop   the height of the trunk's tip
     * @param limbLength the grown length of each of its first limbs (at most 3 are kept as stubs)
     * @param fallPoints where its branches end, for the pieces round it
     */
    static void fallen(TreeVoxels v, long seed, double r0, double k, double g, double old, double fallAz, double trunkTop,
            double[] limbLength, List<double[]> fallPoints) {
        TreeRandom r = new TreeRandom(seed * 977 + 13);
        double hf = g < 0.5 ? 0 : 0.4 + 0.25 * old;
        double[] d = {Math.cos(fallAz), 0, Math.sin(fallAz)};
        double stumpH = 1.5 + 2 * r.next();
        seg(v, new double[] {0, -1, 0}, new double[] {0, 1.5, 0}, r0 + (k > 0.25 ? 1.5 : 0.2), r0, DEAD);
        seg(v, new double[] {0, -1, 0}, new double[] {0, stumpH + 2, 0}, r0, r0 * 0.97, DEAD, true, hf * 0.8);
        v.forEach((x, y, z, b) -> {
            if (y > stumpH + TreeRandom.hash(x, 0, z, 41) * 2.2) {
                v.set(x, y, z, null);
            }
        });
        v.markStump();
        double length = Math.max(3, trunkTop + 1);
        double lift = r0 - 0.75;
        double[] a0 = add(mul(d, r0 + 1.6), new double[] {0, lift, 0}), b0 = add(a0, mul(d, length));
        List<double[][]> logs = new ArrayList<>();
        logs.add(new double[][] {a0, b0, {r0 * 0.97, r0 * 0.85}});
        double[] side = {-d[2], 0, d[0]};
        int keep = Math.min(3, limbLength.length);
        for (int i = 0; i < keep; i++) {
            double spread = (i - (keep - 1) / 2.0) * 0.55 + (r.next() - 0.5) * 0.3;
            double[] ld = add(mul(d, Math.cos(spread)), mul(side, Math.sin(spread)));
            double ll = limbLength[i] * (0.6 + 0.3 * r.next()), ra = r0 * 0.7, rb = r0 * 0.5;
            double[] s = sub(b0, new double[] {0, lift, 0}), e = add(s, mul(ld, ll));
            logs.add(new double[][] {add(s, new double[] {0, Math.max(0.2, ra - 0.75), 0}), add(e, new double[] {0, Math.max(0.2, rb - 0.75), 0}), {ra, rb}});
        }
        for (double[][] log : logs) {
            seg(v, log[0], log[1], log[2][0], log[2][1], DEAD, true, Math.max(log[2][0], log[2][1]) > 1.6 ? hf : 0);
            v.addLog(log[0][0], log[0][2], log[1][0], log[1][2]);
        }
        // Galleries: beetle larvae and termites, out from the hollow through the wood, a few right through the bark.
        if (k > 0.3) {
            for (int i = 0; i < 10; i++) {
                double[][] log = logs.get(i % logs.size());
                double t = r.next();
                double[] axis = sub(log[1], log[0]), c = add(log[0], mul(axis, t));
                double rr = lerp(log[2][0], log[2][1], t);
                if (rr < 1.4) {
                    continue;
                }
                double ang = r.next() * TAU;
                double[] u = {-axis[2], 0, axis[0]};
                double ul = len(u) == 0 ? 1 : len(u);
                double[] out = add(mul(u, Math.cos(ang) / ul), new double[] {0, Math.sin(ang), 0});
                double[] p = add(c, mul(out, rr * hf * 0.9));
                long steps = Math.round(rr * (0.6 + r.next() * 1.0));
                for (int st = 0; st < steps; st++) {
                    p = add(p, add(mul(out, 0.9), new double[] {(r.next() - 0.5) * 0.9, (r.next() - 0.5) * 0.6, (r.next() - 0.5) * 0.9}));
                    int qx = (int) Math.round(p[0]), qy = (int) Math.round(p[1]), qz = (int) Math.round(p[2]);
                    if (qy < 0) {
                        break;
                    }
                    int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                    for (int[] sd : sides) {
                        if (v.get(qx + sd[0], qy + sd[1], qz + sd[2]) == TreePart.DEAD_INNER) {
                            v.set(qx + sd[0], qy + sd[1], qz + sd[2], TreePart.ROTTEN);
                        }
                    }
                    v.carve(qx, qy, qz);
                }
            }
        }
        // Frass on every floor of the hollow and the galleries.
        List<int[]> floors = new ArrayList<>();
        for (long hole : v.holes()) {
            int x = net.minecraft.core.BlockPos.getX(hole), y = net.minecraft.core.BlockPos.getY(hole), z = net.minecraft.core.BlockPos.getZ(hole);
            TreePart below = v.get(x, y - 1, z);
            if (below != null && below != TreePart.FRASS && TreeRandom.hash(x, y, z, 51) < 0.85) {
                floors.add(new int[] {x, y, z});
            }
        }
        for (int[] p : floors) {
            v.set(p[0], p[1], p[2], TreePart.FRASS);
        }
        weather(v, seed, trunkTop, r0, TreeStage.FALLEN);
        scatterFallen(v, seed, k, fallPoints);
        // The galleries can cut a crumb of rotten wood loose at a log's end: it has crumbled away.
        v.clearLoose();
    }
}
