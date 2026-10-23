package com.antcolonies.tree;

import static com.antcolonies.tree.TreeDraw.DEAD;
import static com.antcolonies.tree.TreeDraw.seg;
import static com.antcolonies.tree.TreeDraw.thinLine;

/**
 * The dead wood lying round the trees of new land (M7.5 step 6c, user, 2026-10-19). Real savannas: under nearly every
 * old acacia lie a few fallen branches and often a broken-off limb; dead young trees knocked over by fire or browsing lie
 * scattered in the grass; the open grass between trees has little dead wood. It is not part of a tree's own shape (the
 * tree book never moves or removes it, and termites eat it like any dead wood): new land puts it down once, with the
 * tree ({@code worldgen.GiantTreePiece}). Every block is a loose piece, lying on the land under it. The numbers are my
 * picks, to confirm.
 */
public final class Litter {
    private static final double TAU = Math.PI * 2;
    /** The share of the crown's reach (from the trunk) where its dead wood lies. */
    private static final double UNDER_CROWN = 0.8;
    /** Of the young trees in new land: how many are instead a dead young tree lying in the grass (my pick). */
    public static final double FALLEN_YOUNG_SHARE = 0.15;

    private Litter() {
    }

    /**
     * The dead wood under a living tree of new land: 1 to 3 fallen branches under its crown, and for an old one often a
     * fallen giant limb (from {@link #limbChance}). {@code reach}: how far the crown reaches from the trunk; {@code r0}:
     * the trunk's radius (nothing lies inside it). A baobab drops only branches (its soft wood rots away in months).
     */
    public static TreeVoxels underTree(long seed, double age, double reach, double r0, boolean baobab) {
        TreeVoxels v = new TreeVoxels();
        if (age < TreeShapes.GROWN * 0.6) {
            return v;
        }
        TreeRandom r = new TreeRandom(seed * 7349 + 61);
        double out = Math.max(r0 + 3, reach * UNDER_CROWN);
        if (!baobab && r.next() < limbChance(age)) {
            limb(v, r, r0, out);
        }
        int branches = 1 + (int) (r.next() * 3);
        for (int i = 0; i < branches; i++) {
            double a = r.next() * TAU, d = r0 + 2 + r.next() * (out - r0 - 2);
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            int length = 2 + (int) (r.next() * 2);
            boolean alongX = r.next() < 0.5;
            thinLine(v, new double[] {x, 0, z}, new double[] {x + (alongX ? length - 1 : 0), 0, z + (alongX ? 0 : length - 1)},
                    TreePart.FALLEN_BRANCH, true);
        }
        v.markLooseSince(new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
        return v;
    }

    /** The chance a living tree of new land has a fallen giant limb under it: none before grown, often when old. */
    public static double limbChance(double age) {
        return age < TreeShapes.GROWN ? 0 : 0.25 + 0.6 * TreeLife.old(age);
    }

    /**
     * A fallen giant limb: a thick dead limb (2 to 3 blocks across, 6 to 12 long) broken off near the trunk and lying out
     * under the crown, its end splintered into a few branches.
     */
    private static void limb(TreeVoxels v, TreeRandom r, double r0, double out) {
        double a = r.next() * TAU;
        double ra = 1.0 + r.next() * 0.5, rb = ra * 0.7;
        double length = 6 + r.next() * 6;
        double start = r0 + 2 + r.next() * Math.max(0, out - r0 - 2 - length * 0.6);
        double turn = (r.next() - 0.5) * 1.2; // it lies across the way out, more or less
        double[] d = {Math.cos(a + turn), 0, Math.sin(a + turn)};
        double[] p0 = {Math.cos(a) * start, ra - 0.75, Math.sin(a) * start};
        double[] p1 = {p0[0] + d[0] * length, rb - 0.75, p0[2] + d[2] * length};
        seg(v, p0, p1, ra, rb, DEAD, true, 0);
        // The splintered end: two or three branches on along it.
        int twigs = 2 + (int) (r.next() * 2);
        for (int i = 0; i < twigs; i++) {
            double ta = a + turn + (r.next() - 0.5) * 1.4, tl = 2 + r.next() * 2;
            double[] t0 = {p1[0] + d[0], 0, p1[2] + d[2]};
            thinLine(v, t0, new double[] {t0[0] + Math.cos(ta) * tl, 0, t0[2] + Math.sin(ta) * tl}, TreePart.FALLEN_BRANCH, true);
        }
    }

    /**
     * A dead young tree knocked over in the grass: its stem a block thick, 5 to 8 long, from a little root plate at its
     * foot (the place it stood) to its crown end, where its dead side branches splay on the ground.
     */
    public static TreeVoxels fallenYoung(long seed) {
        TreeVoxels v = new TreeVoxels();
        TreeRandom r = new TreeRandom(seed * 4421 + 7);
        double a = r.next() * TAU, length = 5 + r.next() * 3;
        double[] d = {Math.cos(a), 0, Math.sin(a)};
        double[] tip = {d[0] * length, 0, d[2] * length};
        // The root plate torn up with it: a block or two of dead wood at the foot.
        seg(v, new double[] {0, 0, 0}, new double[] {d[0], 0, d[2]}, 0.75, 0.6, DEAD, true, 0);
        thinLine(v, new double[] {0, 0, 0}, tip, TreePart.DEAD_LIMB, true);
        int twigs = 3 + (int) (r.next() * 2);
        for (int i = 0; i < twigs; i++) {
            double at = 0.55 + r.next() * 0.45, ta = a + (i % 2 == 0 ? 1 : -1) * (0.5 + r.next() * 0.7), tl = 2 + r.next() * 2;
            double[] t0 = {d[0] * length * at - Math.sin(ta - a) * d[2], 0, d[2] * length * at + Math.sin(ta - a) * d[0]};
            thinLine(v, t0, new double[] {t0[0] + Math.cos(ta) * tl, 0, t0[2] + Math.sin(ta) * tl}, TreePart.FALLEN_BRANCH, true);
        }
        v.markLooseSince(new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
        return v;
    }
}
