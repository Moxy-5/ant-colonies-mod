package com.antcolonies.savanna.tree;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.block.HangingFruit;

/**
 * The doum palm's sculpted pieces (floodplain trees step 5, 2026-11-14): a crown of fan fronds with its cap, spear and
 * nut bunches, the seedling and the stemless rosette, made box for box as the model viewer makes them
 * ({@code arcStrip}, {@code frond}, {@code doumNut}, {@code doumBunch}, {@code palmCrown}, {@code deadSkirt},
 * {@code doumSeedling}, {@code doumRosette} in tools/floodplain_viewer.template.html), the same dice in the same order,
 * so the game draws the crown the user picked. A crown is about 1,500 small boxes in odd places, too many and too
 * scattered for blocks, so one block per crown draws them all ({@code block/DoumPieceBlock}, the client's
 * {@code DoumModel}); {@code PalmPiecesTest} checks this against the viewer.
 *
 * <p>Places are the viewer's: a block's middle at whole numbers, so a game block at (x, y, z) is the viewer's cell
 * (x, y, z) shifted by half a block. Colours are the viewer's too, in linear light (three.js turns its colours from
 * sRGB to linear before tinting); the drawing turns them back.
 */
public final class PalmPieces {
    /** What a box is painted with: a plain colour, or the cap the fronds come out of (the stem's bark, lightened). */
    public enum Skin { PLAIN, CAP }

    /**
     * One box: its middle (blocks), its size in pixels (w along x, h up, d along z before turning), turned by rot round
     * the upright (three.js's makeRotationY), its skin and colour (linear), leafy (a frond's), and which fruit bunch of
     * its crown it belongs to (-1: the crown itself).
     */
    public record Bit(double x, double y, double z, int w, int h, int d, double rot, Skin skin, double r, double g, double b,
                      boolean leafy, int bunch) {
    }

    // The viewer's colours (hex through its C(): sRGB to linear).
    private static final double[][] FROND_LIVE = {c("#56702c"), c("#96a440")}, FROND_OLD = {c("#7a7e38"), c("#b4a650")},
            FROND_DEAD = {c("#9c8650"), c("#c4ac70")}, FROND_GREY = {c("#8a8270"), c("#a89e88")};
    private static final double[] PALM_STALK = c("#9a9450");
    private static final double[][] SPEAR = {c("#a8ae58"), c("#c4c674")};
    private static final double[][] DOUM_TINTS = {c("#7e3814"), c("#8a4018"), c("#96491c"), c("#96491c"), c("#a2531f"),
            c("#6c2e12"), c("#b0622a")};
    private static final double[] DOUM_GREEN = c("#76642c");
    private static final double[] CAP_TINT = {1.12, 1.08, 0.9};
    private static final double[][] FINGERS = {{0, 1, 0}, {0.3927, 0.93, 0.12}, {-0.3927, 0.93, 0.12}, {0.7854, 0.76, 0.3},
            {-0.7854, 0.76, 0.3}};

    /** A frond's look: live green, old (yellowing), dead brown, grey (dead for years). */
    private enum Look { LIVE, OLD, DEAD, GREY }

    private final TreeRandom r;
    private final List<Bit> bits = new ArrayList<>();
    /**
     * Which crop the bunches show ({@link HangingFruit}'s stages, floodplain trees step 6b): bare stalks (the nuts
     * fallen), every nut green, half of them still green, or ripe. The same dice are drawn every time, so a bunch keeps
     * its shape as it ripens.
     */
    private final int nuts;
    private int bunch = -1;
    /** The places a maker reports: each fruit bunch's (doumBunch's return), a lying frond's foot. */
    private final List<double[]> at = new ArrayList<>();

    private PalmPieces(long seedOrState, int nuts) {
        this.r = new TreeRandom(seedOrState);
        this.nuts = nuts;
    }

    /**
     * A crown on a stem whose top's middle is (tx, ty, tz) (the viewer's T: on a block edge for a stem two blocks wide),
     * drawn with the dice standing at {@code state} as the crown starts. bunches: how many fruit bunches, -1 for the
     * grown palm's 2 or 3 (left to the dice). old false: no hanging old fronds. dead: every frond brown and hanging, no
     * spear, no fruit. nuts: the bunches' stage ({@link HangingFruit#STAGE}).
     */
    public static List<Bit> crown(int state, double tx, double ty, double tz, double size, boolean old, boolean dead, int bunches,
                                  int nuts) {
        PalmPieces p = new PalmPieces(state, nuts);
        p.palmCrown(new double[] {tx, ty, tz}, size, old, dead, bunches);
        return p.bits;
    }

    /**
     * What a crown or a lying frond made, for a growing palm ({@code DoumShape}): its boxes, where the dice stand after
     * it (the tree's next number), and the places it reports: for a crown each fruit bunch's (the viewer's doumBunch
     * returns, under where its stalk splits), for a frond its foot.
     */
    record Made(List<Bit> bits, int end, List<double[]> at) {
    }

    /** {@link #crown}, with where the dice end and where its bunches hang. */
    static Made crownMade(int state, double tx, double ty, double tz, double size, boolean old, boolean dead, int bunches) {
        PalmPieces p = new PalmPieces(state, HangingFruit.RIPE);
        p.palmCrown(new double[] {tx, ty, tz}, size, old, dead, bunches);
        return new Made(p.bits, p.r.state(), p.at);
    }

    /**
     * One old frond lying on the ground round a dead palm (the viewer's groundFronds, one frond of it), its foot r to r + 2
     * from the clump's middle, drawn with the dice at {@code state}; grey: bleached by years (else freshly dead brown).
     */
    public static List<Bit> groundFrond(int state, double r, boolean grey) {
        return groundFrondMade(state, r, grey).bits();
    }

    /** {@link #groundFrond}, with where the dice end and the frond's foot. */
    static Made groundFrondMade(int state, double r, boolean grey) {
        PalmPieces p = new PalmPieces(state, HangingFruit.RIPE);
        p.groundFrond(r, grey);
        return new Made(p.bits, p.r.state(), p.at);
    }

    /** The seedling (a year or two, under a block), from its seed. */
    public static List<Bit> seedling(int seed) {
        PalmPieces p = new PalmPieces(seed * 2741L + 13, HangingFruit.RIPE);
        p.doumSeedling();
        return p.bits;
    }

    /** The rosette (a stemless young palm, about 4 tall at size 0.8; a sucker at 0.38 to 0.48), from its seed. */
    public static List<Bit> rosette(int seed, double size) {
        PalmPieces p = new PalmPieces(seed * 4513L + 17, HangingFruit.RIPE);
        p.palmCrown(new double[] {0, -0.9, 0}, size, false, false, 0);
        p.deadSkirt(3, size);
        return p.bits;
    }

    /**
     * A bunch of nuts on its own (one placed by a player, with no crown above it): its stalk's split moved to the top of
     * the viewer's cell (0, 0, 0), heading az, from the seed.
     */
    public static List<Bit> loneBunch(int seed, double az, int nuts) {
        PalmPieces p = new PalmPieces(seed * 6151L + 31, nuts);
        p.bunch = 0;
        double[] at = p.doumBunch(new double[] {0, 0, 0}, az);
        double dx = -at[0], dy = 0.45 - (at[1] + 1), dz = -at[2];
        List<Bit> out = new ArrayList<>(p.bits.size());
        for (Bit b : p.bits) {
            out.add(new Bit(b.x() + dx, b.y() + dy, b.z() + dz, b.w(), b.h(), b.d(), b.rot(), b.skin(), b.r(), b.g(), b.b(), b.leafy(), 0));
        }
        return out;
    }

    // ---- The viewer's makers, line for line ----

    private void bit(double x, double y, double z, double w, double h, double d, double rot, Skin skin, double[] tint, boolean leafy) {
        bits.add(new Bit(x, y, z, (int) Math.max(1, Math.round(w)), (int) Math.max(1, Math.round(h)), (int) Math.max(1, Math.round(d)),
                rot, skin, tint[0], tint[1], tint[2], leafy, bunch));
    }

    private void bit(double x, double y, double z, double w, double h, double d, double rot, double[] tint) {
        bit(x, y, z, w, h, d, rot, Skin.PLAIN, tint, false);
    }

    /** arcStrip: a strip of flat plates arching from p; returns {end x, y, z, the last climb}. */
    private double[] arcStrip(double[] p, double az, double phi, double length, double curv, double w0, double w1, double[] c0,
                              double[] c1, boolean leafy) {
        int n = (int) Math.max(1, Math.round(length * 4));
        double ca = StrictMath.cos(az), sa = StrictMath.sin(az);
        double jit = 0.92 + TreeRandom.hash((int) Math.round(p[0] * 16), (int) Math.round(az * 100), (int) Math.round(p[2] * 16), n) * 0.16;
        double h = 0, y = p[1], a = phi;
        double py = Math.round(p[1] * 16);
        for (int i = 0; i < n; i++) {
            a = Math.max(-1.45, phi - curv * (i + 0.5) * 0.25);
            double h0 = h;
            h += StrictMath.cos(a) * 0.25;
            y += StrictMath.sin(a) * 0.25;
            double ny = Math.round(y * 16), t = n > 1 ? (double) i / (n - 1) : 0;
            double len = clamp(Math.round(Math.abs(h - h0) * 16) + 1, 2, 5), thick = clamp(Math.abs(ny - py) + 2, 2, 6);
            double[] tint = mixC(c0, c1, t);
            for (int k = 0; k < 3; k++) {
                tint[k] = tint[k] * jit;
            }
            bit(p[0] + ca * (h0 + h) / 2, (ny + py) / 32, p[2] + sa * (h0 + h) / 2, len, thick, Math.round(lerp(w0, w1, t)), -az,
                    Skin.PLAIN, tint, leafy);
            py = ny;
        }
        return new double[] {p[0] + ca * h, y, p[2] + sa * h, a};
    }

    private void frond(double[] p, double az, double el, double pet, double blade, double curv, Look look) {
        double[][] cc = switch (look) {
            case LIVE -> FROND_LIVE;
            case OLD -> FROND_OLD;
            case DEAD -> FROND_DEAD;
            case GREY -> FROND_GREY;
        };
        double[] c0 = cc[0], c1 = cc[1];
        double[] stalk = arcStrip(p, az, el, pet, 0.12, 3, 3, look == Look.DEAD || look == Look.GREY ? c0 : PALM_STALK, c0, true);
        double[] end = {stalk[0], stalk[1], stalk[2]};
        for (double[] f : FINGERS) {
            double length = blade * f[1] * (0.92 + r.next() * 0.16);
            double bend = curv * (1 + r.next() * 0.25);
            arcStrip(end, az + f[0], stalk[3] - f[2], length, bend, 6, 2, c0, c1, true);
        }
    }

    private void doumNut(double x, double y, double z, double rot, double[] tint, boolean big) {
        x = q(x);
        y = q(y);
        z = q(z);
        int w = big ? 7 : 6, n = w - 2;
        double[] hi = new double[3], lo = new double[3], tip = new double[3];
        for (int k = 0; k < 3; k++) {
            hi[k] = Math.min(1, tint[k] * 1.45 + 0.03);
            lo[k] = tint[k] * 0.68;
            tip[k] = lo[k] * 0.7;
        }
        bit(x, y - 0.5 / 16, z, 3, 1, 3, rot, PALM_STALK);
        bit(x, y - 2.0 / 16, z, n, 2, n, rot, hi);
        bit(x, y - 5.5 / 16, z, w, 5, n, rot, tint);
        bit(x, y - 5.5 / 16, z, n, 5, w, rot, tint);
        bit(x, y - 9.0 / 16, z, n, 2, n, rot, lo);
        bit(x, y - 10.5 / 16, z, 2, 1, 2, rot, tip);
    }

    /** doumBunch: returns the viewer's {base x, base y - 1, base z}. */
    private double[] doumBunch(double[] top, double az) {
        double[] s = arcStrip(new double[] {top[0] + StrictMath.cos(az) * 0.95, top[1] - 0.2, top[2] + StrictMath.sin(az) * 0.95}, az,
                0.25, 0.95, 1.7, 3, 2, PALM_STALK, PALM_STALK, false);
        double b0 = s[0], b1 = s[1], b2 = s[2];
        int strands = 3 + (r.next() < 0.5 ? 1 : 0);
        double spin = az + Math.PI / 2, rot = -az;
        double[] ripe = DOUM_TINTS[(int) Math.floor(r.next() * DOUM_TINTS.length)];
        bit(b0, b1 - 1.5 / 16, b2, 3, 3, 3, rot, PALM_STALK);
        double half = (strands - 1) / 2.0;
        for (int k = 0; k < strands; k++) {
            double off = (k - half) * 4.5 / 16, back = (r.next() - 0.5) * 3 / 16;
            double sx = b0 + StrictMath.cos(spin) * off + StrictMath.cos(az) * back, sz = b2 + StrictMath.sin(spin) * off + StrictMath.sin(az) * back;
            double middle = 1 - Math.abs(k - half) / strands;
            long onStrand = 3 + Math.round(middle * 3 + r.next() * 1.5);
            double length = 4 + onStrand * 5.2;
            if (off != 0) {
                bit((b0 + sx) / 2, b1 - 3.0 / 16, (b2 + sz) / 2, Math.max(1, Math.abs(off) * 16), 1, 1, -spin, PALM_STALK);
            }
            bit(sx, b1 - (3 + length / 2) / 16, sz, 1, length, 1, rot, PALM_STALK);
            for (int j = 0; j < onStrand; j++) {
                double a = spin + (j % 2 != 0 ? 0 : Math.PI) + k * 0.8 + (r.next() - 0.5) * 0.7, out = 3.4 / 16;
                boolean green = r.next() < 0.08;
                double[] tint = green ? DOUM_GREEN : mixC(ripe, DOUM_TINTS[(int) Math.floor(r.next() * DOUM_TINTS.length)], 0.5);
                if (this.nuts == HangingFruit.GREEN || this.nuts == HangingFruit.TURNING && (k + j) % 2 == 0) {
                    tint = DOUM_GREEN; // unripe, or every other nut of a ripening bunch
                }
                double ny = b1 - (4 + j * 5.2 + r.next()) / 16;
                double nrot = rot + (r.next() - 0.5) * 0.5;
                boolean big = r.next() < 0.35; // drawn even for a bare stalk, so the dice stay the viewer's
                if (this.nuts != HangingFruit.BARE) {
                    doumNut(sx + StrictMath.cos(a) * out, ny, sz + StrictMath.sin(a) * out, nrot, tint, big);
                }
            }
        }
        return new double[] {b0, b1 - 1, b2};
    }

    private double turn;

    private void palmCrown(double[] t, double size, boolean old, boolean dead, int bunches) {
        double[] top = {t[0], t[1] + 0.5, t[2]};
        double cs = Math.min(1, size);
        bit(top[0], top[1] + 3.0 / 16, top[2], Math.round(28 * cs), 6, Math.round(28 * cs), 0, Skin.CAP, CAP_TINT, false);
        bit(top[0], top[1] + 8.0 / 16, top[2], Math.round(18 * cs), 5, Math.round(18 * cs), 0, PALM_STALK);
        turn = r.next() * 6.28;
        tier(top, size, dead, 3, 1.35, 1.2, 3.1, 0.6, 0.5, Look.LIVE);
        tier(top, size, dead, 5, 0.85, 1.75, 4.0, 0.5, 0.35, Look.LIVE);
        tier(top, size, dead, 5, 0.3, 2.0, 4.2, 0.4, 0.2, Look.LIVE);
        if (old) {
            tier(top, size, dead, 4, -0.35, 1.6, 3.5, 0.24, 0.05, Look.OLD);
        }
        if (dead) {
            return; // no spear (the growing bud is what died), no fruit
        }
        arcStrip(new double[] {top[0], top[1] + 0.6, top[2]}, r.next() * 6.28, 1.5, 2.2 * size, 0.06, 3, 2, SPEAR[0], SPEAR[1], true);
        int n = bunches >= 0 ? bunches : 2 + (r.next() < 0.5 ? 1 : 0);
        double a0 = r.next() * 6.28;
        for (int i = 0; i < n; i++) {
            double az = a0 + (double) i / n * 6.28 + (r.next() - 0.5) * 0.6;
            bunch = i;
            at.add(doumBunch(top, az));
            bunch = -1;
        }
    }

    private void tier(double[] top, double size, boolean dead, int n, double el, double pet, double blade, double curv, double lift, Look look) {
        if (dead) { // a dead crown: every frond brown and hanging
            el -= 1.1;
            look = Look.DEAD;
            if (r.next() < 0.3) {
                turn += 0.7;
                return;
            }
        }
        for (int i = 0; i < n; i++) {
            double az = snap(turn + (double) i / n * 6.28 + (r.next() - 0.5) * 0.3);
            Look how = look == Look.OLD && r.next() < 0.3 ? Look.DEAD : look;
            double e = el + (r.next() - 0.5) * 0.24;
            double b = blade * size * (0.9 + r.next() * 0.2);
            frond(new double[] {top[0] + StrictMath.cos(az) * 0.8, top[1] + lift, top[2] + StrictMath.sin(az) * 0.8}, az, e, pet * size, b,
                    curv / size, how);
        }
        turn += 0.7;
    }

    private void deadSkirt(int n, double size) {
        for (int i = 0; i < n; i++) {
            double az = r.next() * 6.28;
            double blade = 3.2 * size * (0.85 + r.next() * 0.3);
            frond(new double[] {StrictMath.cos(az) * 0.6, -0.42, StrictMath.sin(az) * 0.6}, az, 0.08, 1.2 * size, blade, 0.05, Look.DEAD);
        }
    }

    /** groundFronds, one frond: lying flat, its foot r to r + 2 out. */
    private void groundFrond(double rr, boolean grey) {
        double az = r.next() * 6.28, d = rr + r.next() * 2;
        double[] p = {StrictMath.cos(az) * d, -0.42, StrictMath.sin(az) * d};
        at.add(p);
        frond(p, az + (r.next() - 0.5) * 1.2, 0.06, 1.3, 3.2 * (0.85 + r.next() * 0.3), 0.04, grey ? Look.GREY : Look.DEAD);
    }

    private void doumSeedling() {
        bit(0, -0.5 + 1.5 / 16, 0, 3, 3, 3, 0, PALM_STALK);
        double az = r.next() * 6.28;
        for (int i = 0; i < 2; i++) {
            double phi = 1.25 + r.next() * 0.15, length = 0.9 + r.next() * 0.3;
            arcStrip(new double[] {0, -0.35, 0}, az, phi, length, 0.45, 4, 2, FROND_LIVE[0], FROND_LIVE[1], true);
            az += 2.6 + r.next() * 0.6;
        }
        frond(new double[] {0, -0.3, 0}, az, 1.15, 0.45, 0.75, 0.7, Look.LIVE);
    }

    // ---- Small helpers, as the viewer's ----

    private static double snap(double a) {
        return Math.round(a / (Math.PI / 8)) * (Math.PI / 8);
    }

    private static double q(double a) {
        return Math.round(a * 16) / 16.0;
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double[] mixC(double[] a, double[] b, double t) {
        return new double[] {lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
    }

    /** The viewer's C(): a hex colour from sRGB into linear light, as three.js r128 does it. */
    static double[] c(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new double[] {lin(((v >> 16) & 255) / 255.0), lin(((v >> 8) & 255) / 255.0), lin((v & 255) / 255.0)};
    }

    private static double lin(double c) {
        return c < 0.04045 ? c * 0.0773993808 : Math.pow(c * 0.9478672986 + 0.0521327014, 2.4);
    }

    /** Linear light back to sRGB, 0 to 1 (what the drawing gives each box). */
    public static double srgb(double c) {
        c = Math.max(0, Math.min(1, c));
        return c < 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 0.41666) - 0.055;
    }
}
