package com.antcolonies.savanna.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.antcolonies.savanna.block.BracketFungusBlock;
import com.antcolonies.savanna.block.FigBark;
import com.antcolonies.savanna.block.FigBranchBlock;
import com.antcolonies.savanna.block.FigLeavesBlock;
import com.antcolonies.savanna.block.FigRootBlock;
import com.antcolonies.savanna.block.TreeBranchBlock;
import com.antcolonies.savanna.block.HangingFruit;
import com.antcolonies.savanna.block.TrunkFigsBlock;
import com.antcolonies.savanna.registry.TreeBlocks;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sycamore fig's shapes (floodplain trees step 4, 2026-11-13): the trees the user picked in the model viewer
 * ({@code tools/floodplain_viewer.template.html}, {@code fig()}, {@code figSeedling}, {@code figSapling},
 * {@code deadFig}, {@code figFallen}, {@code finishLeaves}; shape B and bark A, the user's picks), worked out the same
 * way number for number, so one seed is the one tree seen there. {@code FigShapeTest} checks it block for block against
 * the viewer's own trees ({@code node tools/fig_shape_export.js} writes them).
 *
 * <p>A real sycamore fig (Ficus sycomorus): a short fluted trunk on buttress roots, spoke limbs sweeping out and up,
 * flat pads of leaves, figs in bunches straight off the limbs and the trunk. The ages and dead stages are the viewer's:
 * seedling, sapling, young tree, mature (the old shape A), grown giant (shape B); just died, breaking up, fallen.
 *
 * <p>The viewer's blocks are kept as small codes ({@link #cells()}), the way it names them, and turned into the game's
 * fig blocks by {@link #states()}; its sculpted pieces become the fig's small blocks ({@link Mark}): a fig bunch under a
 * limb, figs on the bark, a bracket fungus on dead wood, the seedling.
 *
 * <p>The sums use {@link StrictMath} and the browser's own way of working out a distance ({@link #hypot}), so the
 * game's numbers round to the same blocks as the viewer's.
 */
public final class FigShape {
    /** The ages and dead stages of the viewer, by its names. */
    public enum Age {
        SEEDLING("seed"), SAPLING("sapling"), YOUNG("young"), MATURE("mature"), GROWN("grown"),
        JUST_DIED("dead1"), BREAKING_UP("dead2"), FALLEN("dead3");

        public final String id;

        Age(String id) {
            this.id = id;
        }

        public static Age byId(String id) {
            for (Age a : values()) {
                if (a.id.equals(id)) {
                    return a;
                }
            }
            return null;
        }
    }

    /** A sculpted piece the viewer draws as small boxes, which the game makes a small block. */
    public enum MarkKind { BUNCH, BARK_FIGS, BRACKET, SEEDLING }

    /**
     * A small block: at (x, y, z); (dx, dz) the way to the wood it grows on (bark figs, bracket); big for a bracket in
     * three tiers.
     */
    public record Mark(MarkKind kind, int x, int y, int z, int dx, int dz, boolean big) {
    }

    // The viewer's block shapes: a code is kind << 16 | size << 8 | look (bark tile or leaf tone).
    static final int CUBE = 1, BRANCH = 2, SLAB = 3, LEAF = 4, CROSS = 5;

    static int code(int kind, int size, int look) {
        return kind << 16 | size << 8 | look;
    }

    static int kind(int code) {
        return code >>> 16;
    }

    static int size(int code) {
        return (code >>> 8) & 0xFF;
    }

    static int look(int code) {
        return code & 0xFF;
    }

    private static int prio(int code) {
        return switch (kind(code)) {
            case CUBE -> 5;
            case BRANCH -> 4;
            case SLAB -> 3;
            case LEAF -> 2;
            default -> 1;
        };
    }

    private static boolean isWood(int code) {
        return kind(code) == CUBE || kind(code) == BRANCH;
    }

    private static boolean hasBark(int code) {
        return kind(code) == CUBE || kind(code) == BRANCH || kind(code) == SLAB;
    }

    /** A block's name as the viewer writes it ("cube|bark_fig:3", "b10|bark_fig:0", "leaf|leaves_fig_2"...). */
    public static String name(int code) {
        return switch (kind(code)) {
            case CUBE -> "cube|bark_fig:" + look(code);
            case BRANCH -> "b" + size(code) + "|bark_fig:" + look(code);
            case SLAB -> "slab" + size(code) + "|bark_fig:" + look(code);
            case LEAF -> "leaf|leaves_fig_" + look(code);
            default -> "cross|sprig_fig";
        };
    }

    // ---- The viewer's block map (its class Vox): kept in the order blocks were first put, as a JavaScript Map ----
    private static final class Vox {
        final Long2IntLinkedOpenHashMap m = new Long2IntLinkedOpenHashMap();

        static long key(int x, int y, int z) {
            return ((long) (x + 1024) * 256 + (y + 64)) * 2048 + (z + 1024);
        }

        void put(int x, int y, int z, int b) {
            if (y < -1) {
                return;
            }
            long k = key(x, y, z);
            int old = m.get(k);
            if (old == 0 || prio(b) >= prio(old)) {
                m.put(k, b);
            }
        }

        int get(int x, int y, int z) {
            return m.get(key(x, y, z));
        }

        void del(int x, int y, int z) {
            m.remove(key(x, y, z));
        }

        void set(int x, int y, int z, int b) {
            m.put(key(x, y, z), b);
        }

        interface Each {
            void at(int x, int y, int z, int b);
        }

        void each(Each f) {
            for (Long2IntMap.Entry e : m.long2IntEntrySet()) {
                long k = e.getLongKey();
                int z = (int) (k % 2048) - 1024;
                long t = k / 2048;
                f.at((int) (t / 256) - 1024, (int) (t % 256) - 64, z, e.getIntValue());
            }
        }
    }

    // ---- The tree ----
    private final Vox v = new Vox();
    private final List<Mark> marks = new ArrayList<>();
    /** The tree being made: its noise seed, where its limbs leave the trunk, how far up the young bark starts, its dead stage. */
    private int ns = 1, figFy = 7, figDead;
    /** The seed the tree was made from: its fruit's timetable (step 4b; no dice drawn from it). */
    private int seed;
    private double figOldBelow = Double.POSITIVE_INFINITY;

    private FigShape() {
    }

    /** The fig of this seed at this age, every block worked out as in the viewer. */
    public static FigShape make(int seed, Age age) {
        FigShape f = new FigShape();
        f.seed = seed;
        switch (age) {
            case SEEDLING -> f.marks.add(new Mark(MarkKind.SEEDLING, 0, 0, 0, 0, 0, false));
            case SAPLING -> f.sapling(seed);
            case YOUNG -> f.fig(seed, true, true, 0);
            case MATURE -> f.fig(seed, false, false, 0);
            case GROWN -> f.fig(seed, true, false, 0);
            case JUST_DIED -> f.fig(seed, true, false, 1);
            case BREAKING_UP -> f.fig(seed, true, false, 2);
            case FALLEN -> f.fallen(seed);
        }
        f.finishLeaves();
        return f;
    }

    /** Every block of the tree as a viewer code, by {@link BlockPos#asLong}, around its foot at (0, 0, 0). */
    public Long2IntLinkedOpenHashMap cells() {
        Long2IntLinkedOpenHashMap out = new Long2IntLinkedOpenHashMap();
        v.each((x, y, z, b) -> out.put(BlockPos.asLong(x, y, z), b));
        return out;
    }

    /** The small blocks: fig bunches, figs on the bark, bracket fungi, the seedling. */
    public List<Mark> marks() {
        return marks;
    }

    // ---- Into the game's blocks ----
    private static final FigBark[] BARK_OF_TILE = {FigBark.MOTTLED, FigBark.PATCH, FigBark.PALE, FigBark.MOTTLED, FigBark.YOUNG,
            FigBark.DEAD, FigBark.BARE};

    /**
     * The tree as the game's blocks, around its foot at (0, 0, 0) (y = -1 is the ground): fig wood (bark on every
     * side, as drawn), fig branches by thickness and joined to the wood and leaves they touch, root steps, the leaves in
     * their three tones, the leaf sprays, and the small blocks of {@link #marks()} (bunches hang under the limb above,
     * bark figs and brackets face out from their wood). Needs the game's blocks (not for plain unit tests).
     */
    public Long2ObjectLinkedOpenHashMap<BlockState> states() {
        Long2ObjectLinkedOpenHashMap<BlockState> out = new Long2ObjectLinkedOpenHashMap<>();
        v.each((x, y, z, b) -> out.put(BlockPos.asLong(x, y, z), state(b)));
        for (Long2ObjectMap.Entry<BlockState> e : out.long2ObjectEntrySet()) {
            BlockState s = e.getValue();
            if (s.getBlock() instanceof FigBranchBlock) {
                long k = e.getLongKey();
                for (Direction d : Direction.values()) {
                    BlockState n = out.get(BlockPos.offset(k, d));
                    s = s.setValue(TreeBranchBlock.PROPERTY_BY_DIRECTION.get(d), n != null && TreeBranchBlock.joins(n));
                }
                e.setValue(s);
            }
        }
        for (Mark mk : marks) {
            long k = BlockPos.asLong(mk.x, mk.y, mk.z);
            BlockState s = switch (mk.kind) {
                case BUNCH -> HangingFruit.onTree(TreeBlocks.FIG_BUNCH.get().defaultBlockState(), seed);
                case SEEDLING -> TreeBlocks.FIG_SEEDLING.get().defaultBlockState();
                case BARK_FIGS -> HangingFruit.onTree(TreeBlocks.TRUNK_FIGS.get().defaultBlockState(), seed)
                        .setValue(TrunkFigsBlock.FACING, Direction.fromDelta(-mk.dx, 0, -mk.dz));
                // My pick: the viewer's two-tier brackets are grown ones, its three-tier ones tiered.
                case BRACKET -> TreeBlocks.BRACKET_FUNGUS.get().defaultBlockState()
                        .setValue(BracketFungusBlock.FACING, Direction.fromDelta(-mk.dx, 0, -mk.dz))
                        .setValue(BracketFungusBlock.AGE, BracketFungusBlock.GROWN).setValue(BracketFungusBlock.TIERS, mk.big);
            };
            if (!out.containsKey(k)) {
                out.put(k, s);
            }
        }
        return out;
    }

    private static BlockState state(int code) {
        return switch (kind(code)) {
            case CUBE -> TreeBlocks.FIG_WOOD.get().defaultBlockState().setValue(FigBark.BARK, BARK_OF_TILE[look(code)]);
            case BRANCH -> TreeBlocks.FIG_BRANCH.get().defaultBlockState().setValue(FigBark.BARK, BARK_OF_TILE[look(code)])
                    .setValue(FigBranchBlock.STEP, FigBranchBlock.levelFor(size(code)) * 2);
            case SLAB -> TreeBlocks.FIG_ROOT.get().defaultBlockState().setValue(FigBark.BARK, BARK_OF_TILE[look(code)])
                    .setValue(FigRootBlock.LOW, size(code) < 8);
            case LEAF -> TreeBlocks.FIG_LEAVES.get().defaultBlockState().setValue(FigLeavesBlock.TONE, look(code));
            default -> TreeBlocks.FIG_LEAF_SPRAY.get().defaultBlockState();
        };
    }

    // ================= The viewer's helpers =================
    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] mul(double[] a, double s) {
        return new double[] {a[0] * s, a[1] * s, a[2] * s};
    }

    private static double len(double[] a) {
        return hypot(a[0], a[1], a[2]);
    }

    /** The browser's own Math.hypot (V8: scaled by the largest, summed with Kahan's compensation), so distances round the same. */
    static double hypot(double a, double b) {
        a = Math.abs(a);
        b = Math.abs(b);
        double max = Math.max(a, b);
        if (max == Double.POSITIVE_INFINITY) {
            return max;
        }
        if (max == 0) {
            return 0;
        }
        double sum = 0, comp = 0;
        double n = a / max, summand = n * n - comp, pre = sum + summand;
        comp = (pre - sum) - summand;
        sum = pre;
        n = b / max;
        summand = n * n - comp;
        pre = sum + summand;
        sum = pre;
        return Math.sqrt(sum) * max;
    }

    static double hypot(double a, double b, double c) {
        a = Math.abs(a);
        b = Math.abs(b);
        c = Math.abs(c);
        double max = Math.max(a, Math.max(b, c));
        if (max == Double.POSITIVE_INFINITY) {
            return max;
        }
        if (max == 0) {
            return 0;
        }
        double sum = 0, comp = 0;
        for (double x : new double[] {a, b, c}) {
            double n = x / max, summand = n * n - comp, pre = sum + summand;
            comp = (pre - sum) - summand;
            sum = pre;
        }
        return Math.sqrt(sum) * max;
    }

    private static int round(double x) {
        return (int) Math.round(x);
    }

    private static double hash(int x, int y, int z, int s) {
        return TreeRandom.hash(x, y, z, s);
    }

    private static double sm(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Smooth noise in space, 0..1 (the viewer's vnoise). */
    private static double vnoise(double x, double y, double z, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = sm(x - xi), fy = sm(y - yi), fz = sm(z - zi);
        double v = 0;
        for (int i = 0; i < 8; i++) {
            int dx = i & 1, dy = (i >> 1) & 1, dz = (i >> 2) & 1;
            v += hash(xi + dx, yi + dy, zi + dz, s) * (dx != 0 ? fx : 1 - fx) * (dy != 0 ? fy : 1 - fy) * (dz != 0 ? fz : 1 - fz);
        }
        return v;
    }

    private static final double[] DEAD_BARE = {0, 0.16, 0.45, 0.62};

    /** The fig's bark tile for a block (0 and 3 the base bark, 1 patch, 2 pale, 4 young, 5 dead, 6 bare). */
    private int figTile(int x, int y, int z) {
        if (figDead != 0) {
            double d = vnoise(x / 2.2 + 5.5, y / 2.6, z / 2.2 - 1.3, ns + 21);
            if (d < DEAD_BARE[figDead]) {
                return 6;
            }
            if (figDead > 1 || d < 0.48) {
                return 5;
            }
        }
        if (y > figOldBelow + (vnoise(x / 1.7 + 3.1, y / 2.0, z / 1.7 - 6.2, ns + 13) - 0.5) * 3) {
            return 4;
        }
        double n = vnoise(x / 2.0 + 9.3, y / 2.5, z / 2.0 - 4.1, ns), m = vnoise(x / 1.8 - 31.7, y / 2.2 + 5.5, z / 1.8 + 12.9, ns + 7);
        double calm = clamp((y - figFy + 1) / 5.0, 0, 1) * 0.13;
        if (n > 0.635 + calm) {
            return 1;
        }
        if (m > 0.655 + calm * 0.6) {
            return 2;
        }
        return hash(x, y, z, ns + 9) < 0.5 ? 0 : 3;
    }

    private int cube(int x, int y, int z) {
        return code(CUBE, 0, figTile(x, y, z));
    }

    /** The cells from a to b, every step along one axis only, so the pieces always join face to face. */
    private static List<int[]> cellsOf(double[] a, double[] b) {
        int[] p = {round(a[0]), round(a[1]), round(a[2])};
        List<int[]> out = new ArrayList<>();
        out.add(p.clone());
        double[] ab = sub(b, a);
        int n = (int) Math.ceil(len(ab) * 3) + 1;
        for (int i = 1; i <= n; i++) {
            double f = (double) i / n;
            int[] q = {round(a[0] + ab[0] * f), round(a[1] + ab[1] * f), round(a[2] + ab[2] * f)};
            while (p[0] != q[0] || p[1] != q[1] || p[2] != q[2]) {
                int ax = 0, best = -1;
                for (int j = 0; j < 3; j++) {
                    int d = Math.abs(q[j] - p[j]);
                    if (d > best) {
                        best = d;
                        ax = j;
                    }
                }
                p[ax] += Integer.signum(q[ax] - p[ax]);
                out.add(p.clone());
            }
        }
        return out;
    }

    /** A round piece of wood from a to b, radius ra to rb, in full blocks. */
    private void seg(double[] a, double[] b, double ra, double rb) {
        double[] ab = sub(b, a);
        double l2 = Math.max(1e-6, ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2]);
        double r = Math.max(ra, rb) + 1;
        int[] lo = new int[3], hi = new int[3];
        for (int i = 0; i < 3; i++) {
            lo[i] = (int) Math.floor(Math.min(a[i], b[i]) - r);
            hi[i] = (int) Math.ceil(Math.max(a[i], b[i]) + r);
        }
        for (int x = lo[0]; x <= hi[0]; x++) {
            for (int y = Math.max(-1, lo[1]); y <= hi[1]; y++) {
                for (int z = lo[2]; z <= hi[2]; z++) {
                    double t = clamp(((x - a[0]) * ab[0] + (y - a[1]) * ab[1] + (z - a[2]) * ab[2]) / l2, 0, 1);
                    if (hypot(x - (a[0] + ab[0] * t), y - (a[1] + ab[1] * t), z - (a[2] + ab[2] * t)) <= lerp(ra, rb, t)) {
                        v.put(x, y, z, cube(x, y, z));
                    }
                }
            }
        }
    }

    private static final int[] TAPER = {14, 12, 10, 8, 6, 4};

    /** Wood along a bent line, its radius falling from r0 to r1: round while thick, then a block, then the tapering branch. */
    private void woodPath(List<double[]> pts, double r0, double r1, double e) {
        double[] ls = new double[pts.size() - 1];
        double total = 0, s = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            ls[i] = len(sub(pts.get(i + 1), pts.get(i)));
            total += ls[i];
        }
        for (int i = 0; i + 1 < pts.size(); i++) {
            int n = Math.max(1, (int) Math.ceil(ls[i] / 1.1));
            double[] d = sub(pts.get(i + 1), pts.get(i));
            for (int j = 0; j < n; j++) {
                double[] a = add(pts.get(i), mul(d, (double) j / n)), b = add(pts.get(i), mul(d, (double) (j + 1) / n));
                double ra = lerp(r0, r1, StrictMath.pow(s / total, e)), rb = lerp(r0, r1, StrictMath.pow((s + ls[i] / n) / total, e));
                double rm = (ra + rb) / 2;
                s += ls[i] / n;
                if (rm >= 0.85) {
                    seg(a, b, ra, rb);
                    continue;
                }
                double px = rm * 32;
                int t = 14;
                for (int x : TAPER) {
                    if (Math.abs(x - px) < Math.abs(t - px)) {
                        t = x;
                    }
                }
                for (int[] c : cellsOf(a, b)) {
                    v.put(c[0], c[1], c[2], code(rm >= 0.5 ? CUBE : BRANCH, rm >= 0.5 ? 0 : t, figTile(c[0], c[1], c[2])));
                }
            }
        }
    }

    private void woodPath(List<double[]> pts, double r0, double r1) {
        woodPath(pts, r0, r1, 1);
    }

    private static double[] p(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    // ================= Sycamore fig =================
    /** A fig bunch hanging under a limb (the viewer's figBunch: its dice drawn the same, its look left to the block). */
    private void figBunch(int x, int y, int z, TreeRandom r) {
        r.next();
        int levels = 3 + (r.next() < 0.65 ? 1 : 0);
        for (int level = 0; level < levels; level++) {
            int cnt = level == levels - 1 ? 2 : level == 0 ? 3 : 4;
            for (int j = 0; j < cnt; j++) {
                r.next();
                r.next();
                r.next();
            }
        }
        marks.add(new Mark(MarkKind.BUNCH, x, y, z, 0, 0, false));
    }

    /** Figs on the bark at (x, y, z), the air beside it; (dx, dz) the way to the bark (the viewer's figCluster). */
    private void figCluster(int x, int y, int z, int dx, int dz, TreeRandom r) {
        int n = 4 + (int) Math.floor(r.next() * 3);
        for (int i = 0; i < n * 5; i++) {
            r.next();
        }
        marks.add(new Mark(MarkKind.BARK_FIGS, x, y, z, dx, dz, false));
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Root steps: half-height pieces in the corners of the steps at a tree's foot. */
    private void rootSteps(int span, int top) {
        for (int y = 0; y <= top; y++) {
            for (int x = -span; x <= span; x++) {
                for (int z = -span; z <= span; z++) {
                    if (v.get(x, y, z) != 0 || (y > 0 && kind(v.get(x, y - 1, z)) != CUBE)) {
                        continue;
                    }
                    int near = 0;
                    for (int[] d : SIDES) {
                        if (kind(v.get(x + d[0], y, z + d[1])) == CUBE) {
                            near++;
                        }
                    }
                    if (near == 0 || hash(x, y, z, ns + 2) > (near > 1 ? 0.9 : 0.6)) {
                        continue;
                    }
                    v.put(x, y, z, code(SLAB, hash(x, y, z, ns + 4) < 0.6 ? 8 : 5, figTile(x, y, z)));
                }
            }
        }
    }

    /** The trunk: fluted, the ridges running down into buttress roots that step out along the ground. */
    private void figTrunk(double rw, int fy, int nb, double ph, double flareP, double buttP, int roots) {
        int span = (int) Math.ceil(rw * 3.2);
        for (int y = -1; y <= fy + 1; y++) {
            int yy = Math.max(0, y);
            double flare = flareP * 1.25 * StrictMath.exp(-yy / 1.5), neck = flareP * 0.55 * clamp((yy - (fy - 3)) / 3.0, 0, 1);
            for (int x = -span; x <= span; x++) {
                for (int z = -span; z <= span; z++) {
                    double lobe = StrictMath.cos(nb * StrictMath.atan2(z, x) + ph + yy * 0.12), butt = Math.max(0, lobe - 0.35) / 0.65;
                    double r = (rw + flare + neck) * (1 + 0.07 * lobe) + buttP * butt * Math.max(0, 3.2 - yy) * 0.62 * rw / 2.4;
                    if (hypot(x, z) <= r) {
                        v.put(x, y, z, cube(x, y, z));
                    }
                }
            }
        }
        rootSteps(span + 1, roots);
    }

    /** A pad of leaves, its flat bottom at c: a lumpy dome, h layers at its thickest, its rim lobed, thin and ragged. */
    private void pad(double[] c, double r, int h) {
        int y0 = round(c[1]);
        int n = (int) Math.ceil(r * 1.25);
        for (int x = (int) Math.floor(c[0] - n); x <= (int) Math.ceil(c[0] + n); x++) {
            for (int z = (int) Math.floor(c[2] - n); z <= (int) Math.ceil(c[2] + n); z++) {
                double d = hypot(x - c[0], z - c[2]), th = StrictMath.atan2(z - c[2], x - c[0]);
                double edge = r * (0.78 + 0.44 * vnoise(StrictMath.cos(th) * 1.5 + c[0] * 0.37, 0.5, StrictMath.sin(th) * 1.5 + c[2] * 0.37, ns + 3));
                if (d > edge) {
                    continue;
                }
                boolean rim = d > edge - 1.5;
                double lump = 0.5 + 1.0 * vnoise(x / 2.3 + 17.2, y0 * 0.31, z / 2.3 - 8.4, ns + 4);
                if (rim && hash(x, y0, z, ns + 6) < 0.16) {
                    continue;
                }
                double q = d / edge;
                int top = Math.max(1, round(h * Math.sqrt(1 - q * q) * lump));
                int from = rim && hash(x, y0, z, ns + 5) < 0.45 ? 1 : 0;
                for (int dy = from; dy <= top; dy++) {
                    v.put(x, y0 + dy, z, code(LEAF, 0, 1));
                }
            }
        }
    }

    private record Pad(double[] c, double r, int h) {
    }

    private record Fallen(double[] at, List<double[]> tail, double r) {
    }

    private record Cut(List<double[]> head, List<double[]> tail, double[] at) {
    }

    /** The first share f of a bent line by its length, the rest, and the point between. */
    private static Cut cutLine(List<double[]> pts, double f) {
        double[] ls = new double[pts.size() - 1];
        double total = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            ls[i] = len(sub(pts.get(i + 1), pts.get(i)));
            total += ls[i];
        }
        double want = total * f;
        List<double[]> head = new ArrayList<>();
        head.add(pts.get(0));
        for (int i = 0; i < ls.length; i++) {
            if (want <= ls[i]) {
                double[] at = add(pts.get(i), mul(sub(pts.get(i + 1), pts.get(i)), want / ls[i]));
                head.add(at);
                List<double[]> tail = new ArrayList<>();
                tail.add(at);
                tail.addAll(pts.subList(i + 1, pts.size()));
                return new Cut(head, tail, at);
            }
            want -= ls[i];
            head.add(pts.get(i + 1));
        }
        double[] last = pts.get(pts.size() - 1);
        return new Cut(head, List.<double[]>of(last), last);
    }

    /**
     * The fig (the viewer's fig()): big for shape B, young for the young tree, dead its dead stage (0 alive, 1 just
     * died, 2 breaking up, 3 the fallen tree's stage before it falls).
     */
    private void fig(int seed, boolean shapeB, boolean young, int dead) {
        TreeRandom r = new TreeRandom(seed * 104729L + 5), rd = new TreeRandom(seed * 7727L + 41);
        boolean big = shapeB && !young;
        double S = young ? 0.6 : big ? 1.36 : 1;
        List<Fallen> fallen = new ArrayList<>();
        ns = seed * 13 + 1;
        figOldBelow = young ? 2.5 : Double.POSITIVE_INFINITY;
        figDead = dead;
        // a limb: whole while the tree lives or has just died; broken off past `keep` of its length once it breaks up,
        // the broken-off rest remembered to lie on the ground under it
        interface Limb {
            void draw(List<double[]> pts, double r0, double r1, double e, double keep);
        }
        Limb limb = (pts, r0, r1, e, keep) -> {
            if (dead < 2) {
                woodPath(pts, r0, r1, e);
                return;
            }
            if (keep <= 0) {
                return;
            }
            Cut cut = cutLine(pts, keep);
            double rc = lerp(r0, r1, StrictMath.pow(keep, e));
            woodPath(cut.head, r0, rc, 1);
            fallen.add(new Fallen(cut.at, cut.tail, rc));
        };
        int H = round((20 + r.next() * 2) * S), fy = round(7.6 * S);
        double rw = young ? 1.25 : big ? 3.15 : 2.4;
        figFy = fy;
        int nb = (big ? 6 : 5) + (r.next() < 0.5 ? 1 : 0);
        double ph = r.next() * 6.28;
        figTrunk(rw, fy, nb, ph, young ? 0.4 : 1, young ? 0.35 : 1, young ? 1 : 3);
        List<Pad> pads = new ArrayList<>();
        double[] fork = {0, fy - 0.5, 0};
        double thick = young ? 0.98 : big ? 1.55 : 1.3;
        int padH = young ? 1 : 0;
        // the outer limbs: spokes sweeping out and up, each with a higher side branch
        int nOut = (young ? 5 : big ? 8 : 7) + (r.next() < 0.4 ? 1 : 0);
        double az0 = r.next() * 6.28;
        int low = (int) Math.floor(r.next() * nOut);
        for (int i = 0; i < nOut; i++) {
            double az = az0 + (double) i / nOut * 6.28 + (r.next() - 0.5) * 0.4;
            double[] o = {StrictMath.cos(az), 0, StrictMath.sin(az)}, side = {-o[2], 0, o[0]};
            double far = r.next(), reach = (9.5 + far * 3.5) * S;
            double el = i == low ? 0.3 + r.next() * 0.08 : 0.66 - far * 0.2 + (i % 2 != 0 ? 0.13 : -0.05) + (r.next() - 0.5) * 0.08;
            double[] start = add(fork, mul(o, rw * 0.5)), end = {o[0] * reach, fy + (reach - rw) * StrictMath.tan(el), o[2] * reach};
            double[] up = {0, 0.9 + r.next() * 0.8, 0};
            double[] mid = add(add(mul(add(start, end), 0.5), up), mul(side, (r.next() - 0.5) * 2.4));
            limb.draw(List.of(start, mid, end), thick, 0.2, 1.7, i == low ? 0.12 : 0.35 + rd.next() * 0.3); // one limb gone to a stub
            pads.add(new Pad(end, (3.7 + r.next() * 1.1) * S, 3 - padH));
            if (big || r.next() < 0.85) {
                double sign = r.next() < 0.5 ? 1 : -1;
                double az2 = az + sign * (0.42 + r.next() * 0.2), d2 = reach * (0.52 + r.next() * 0.16);
                double[] from = add(start, mul(sub(mid, start), 0.8)), e2 = {StrictMath.cos(az2) * d2, end[1] + 2 + r.next() * 2.5, StrictMath.sin(az2) * d2};
                limb.draw(List.of(from, add(mul(add(from, e2), 0.5), p(0, 0.8, 0)), e2), young ? 0.62 : 0.84, 0.16, 1.5,
                        rd.next() < 0.5 ? 0 : 0.3 + rd.next() * 0.2);
                pads.add(new Pad(e2, (3.3 + r.next() * 1.0) * S, 3 - padH));
            }
        }
        // the inner limbs: steep, carrying the top of the crown
        int nIn = young ? 2 : big ? 4 : 3;
        for (int i = 0; i < nIn; i++) {
            double az = az0 + 0.5 + (double) i / nIn * 6.28 + (r.next() - 0.5) * 0.5;
            double[] o = {StrictMath.cos(az), 0, StrictMath.sin(az)};
            double d = (3.2 + r.next() * 2.6) * S;
            double[] start = add(fork, mul(o, rw * 0.3)), end = {o[0] * d, H - 4 - r.next() * 1.5, o[2] * d};
            limb.draw(List.of(start, add(mul(add(start, end), 0.5), mul(o, 0.9)), end), thick * 0.9, 0.18, 1.6, 0.4 + rd.next() * 0.3);
            pads.add(new Pad(end, (4.2 + r.next() * 1.2) * S, 4 - padH));
        }
        if (dead != 0) {
            deadFig(dead, pads, fallen, fy, rw, r, rd);
            return;
        }
        for (Pad pd : pads) {
            for (int j = 0; j < 3; j++) { // short twigs spreading under the pad, seen from below
                double a = r.next() * 6.28, l = pd.r * (0.4 + r.next() * 0.2);
                double[] from = {round(pd.c[0]), round(pd.c[1]), round(pd.c[2])};
                double up = r.next() < 0.5 ? 0 : 1;
                woodPath(List.of(from, p(from[0] + StrictMath.cos(a) * l, from[1] + up, from[2] + StrictMath.sin(a) * l)), 0.3, 0.17);
            }
            pad(pd.c, pd.r, pd.h);
        }
        // figs: bunches hanging under the limbs, and clusters on the bark of the trunk
        List<int[]> under = new ArrayList<>(), onBark = new ArrayList<>();
        double rwF = rw;
        v.each((x, y, z, b) -> {
            double dist = hypot(x, z);
            if (dist > 30 * S || !isWood(b)) {
                return;
            }
            boolean thickWood = kind(b) == CUBE || (kind(b) == BRANCH && (size(b) == 14 || size(b) == 12));
            if (y >= fy - 1 && thickWood && v.get(x, y - 1, z) == 0 && v.get(x, y - 2, z) == 0 && dist > rwF + 1.5) {
                under.add(new int[] {x, y - 1, z});
            }
            if (kind(b) == CUBE && y >= 2 && y <= fy + 2 && dist < rwF + 2.2) {
                for (int[] dd : SIDES) {
                    if (v.get(x + dd[0], y, z + dd[1]) == 0) {
                        onBark.add(new int[] {x + dd[0], y, z + dd[1], -dd[0], -dd[1]});
                    }
                }
            }
        });
        List<int[]> taken = new ArrayList<>();
        int left = young ? 3 : big ? 22 : 14;
        for (int[] q : order(under, ns + 11)) {
            if (left > 0 && far(taken, q, 3.2)) {
                taken.add(q);
                left--;
                figBunch(q[0], q[1], q[2], r);
            }
        }
        left = young ? 2 : big ? 8 : 5;
        for (int[] q : order(onBark, ns + 11)) {
            if (left > 0 && far(taken, q, 3)) {
                taken.add(q);
                left--;
                figCluster(q[0], q[1], q[2], q[3], q[4], r);
            }
        }
    }

    /** The places sorted by a hash of where they are (a stable sort, as the browser's). */
    private static List<int[]> order(List<int[]> all, int salt) {
        List<double[]> keyed = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            int[] q = all.get(i);
            keyed.add(new double[] {hash(q[0], q[1], q[2], salt), i});
        }
        keyed.sort(Comparator.comparingDouble(k -> k[0]));
        List<int[]> out = new ArrayList<>();
        for (double[] k : keyed) {
            out.add(all.get((int) k[1]));
        }
        return out;
    }

    private static boolean far(List<int[]> taken, int[] q, double gap) {
        for (int[] t : taken) {
            if (hypot(q[0] - t[0], q[1] - t[1], q[2] - t[2]) < gap) {
                return false;
            }
        }
        return true;
    }

    /** The sapling, a few years old (the viewer's figSapling). */
    private void sapling(int seed) {
        TreeRandom r = new TreeRandom(seed * 6007L + 19);
        ns = seed * 13 + 3;
        figOldBelow = -99;
        figFy = 3;
        figDead = 0;
        int top = 3 + (int) Math.floor(r.next() * 2);
        for (int y = -1; y <= top; y++) {
            v.put(0, y, 0, cube(0, y, 0));
        }
        rootSteps(1, 0);
        List<Pad> pads = new ArrayList<>();
        int n = 3 + (r.next() < 0.5 ? 1 : 0);
        double az0 = r.next() * 6.28;
        for (int i = 0; i < n; i++) {
            double az = az0 + (double) i / n * 6.28 + (r.next() - 0.5) * 0.5;
            double[] o = {StrictMath.cos(az), 0, StrictMath.sin(az)};
            double d = 2.2 + r.next() * 1.4;
            double[] from = {0, top - 0.6 - (i % 2) * 0.8, 0}, end = {o[0] * d, top + 1 + r.next() * 1.1, o[2] * d};
            woodPath(List.of(from, add(mul(add(from, end), 0.5), mul(o, 0.4)), end), 0.62, 0.2);
            pads.add(new Pad(end, 1.6 + r.next() * 0.6, 2));
        }
        double[] lead = {0.3, top + 3 + r.next() * 0.6, -0.2};
        woodPath(List.of(p(0, top, 0), lead), 0.55, 0.2);
        pads.add(new Pad(lead, 1.9, 2));
        for (Pad pd : pads) {
            pad(pd.c, pd.r, pd.h);
        }
    }

    /** Spots on the outside of dead wood: air beside a block of wood between heights lo and hi, chosen by hash, kept gap apart. */
    private List<int[]> barkSpots(int lo, int hi, int n, double gap, int salt) {
        List<int[]> all = new ArrayList<>();
        v.each((x, y, z, b) -> {
            if (y < lo || y > hi || kind(b) != CUBE) {
                return;
            }
            for (int[] d : SIDES) {
                if (v.get(x + d[0], y, z + d[1]) == 0) {
                    all.add(new int[] {x + d[0], y, z + d[1], -d[0], -d[1]});
                }
            }
        });
        List<int[]> out = new ArrayList<>();
        for (int[] q : order(all, ns + salt)) {
            if (out.size() < n && far(out, q, gap)) {
                out.add(q);
            }
        }
        return out;
    }

    private void brackets(List<int[]> spots) {
        for (int i = 0; i < spots.size(); i++) {
            int[] q = spots.get(i);
            marks.add(new Mark(MarkKind.BRACKET, q[0], q[1], q[2], q[3], q[4], i % 2 == 0));
        }
    }

    /** A dead fig (the viewer's deadFig): 1 just died, 2 breaking up, 3 ready to fall (hollowed lower). */
    private void deadFig(int dead, List<Pad> pads, List<Fallen> fallen, int fy, double rw, TreeRandom r, TreeRandom rd) {
        if (dead == 1) {
            for (Pad pd : pads) {
                for (int j = 0; j < 5; j++) { // the bare twigs that held the leaves
                    double a = r.next() * 6.28, l = pd.r * (0.45 + r.next() * 0.3);
                    double[] from = {round(pd.c[0]), round(pd.c[1]), round(pd.c[2])};
                    double tipY = from[1] + 0.6 + r.next() * 1.4; // summed in the viewer's order, so it rounds the same
                    woodPath(List.of(from, p(from[0] + StrictMath.cos(a) * l, tipY, from[2] + StrictMath.sin(a) * l)), 0.3, 0.17);
                }
            }
            return;
        }
        if (dead < 3) {
            for (Fallen f : fallen) { // the broken-off limbs lying under where they broke; the thinnest rotted away
                if (f.r < 0.34 || rd.next() < 0.25) {
                    continue;
                }
                double[] t = f.tail.get(f.tail.size() - 1);
                double dx = t[0] - f.at[0], dz = t[2] - f.at[2], l = hypot(dx, dz);
                if (l == 0) {
                    l = 1;
                }
                double L = Math.min(len(sub(t, f.at)) * (0.5 + rd.next() * 0.3), 9), h = Math.max(0, f.r - 0.5), sw = (rd.next() - 0.5) * 0.8;
                double a = StrictMath.atan2(dz, dx) + sw;
                double[] p0 = {f.at[0] + dx / l, h, f.at[2] + dz / l};
                woodPath(List.of(p0, p(p0[0] + StrictMath.cos(a) * L, Math.max(0, f.r * 0.6 - 0.5), p0[2] + StrictMath.sin(a) * L)), f.r, f.r * 0.6);
            }
        }
        for (int y = dead == 3 ? 2 : fy - 3; y <= fy + 2; y++) { // hollow from the fork down
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (hypot(x, z) < rw - 1.1) {
                        v.del(x, y, z);
                    }
                }
            }
        }
        if (dead == 3) {
            return;
        }
        brackets(barkSpots(2, fy, 6, 3.5, 31));
    }

    /** The fallen fig (the viewer's figFallen): the hollow trunk snapped a few blocks up, the top lying beside the stump. */
    private void fallen(int seed) {
        FigShape t = new FigShape();
        t.fig(seed, true, false, 3);
        ns = t.ns;
        figFy = t.figFy;
        figDead = 3;
        figOldBelow = t.figOldBelow;
        TreeRandom rd = new TreeRandom(seed * 523L + 61);
        double rw = 3.15;
        int snap = 3 + (int) Math.floor(rd.next() * 3);
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int[] e = dirs[(int) Math.floor(rd.next() * 4)], pp = {-e[1], e[0]};
        int h0 = round(rw);
        t.v.each((x, y, z, b) -> {
            int top = snap + (int) Math.floor(hash(x, 0, z, ns + 33) * 3);
            int retiled = hasBark(b) ? (b & ~0xFF) | figTile(x, y, z) : b;
            if (y <= top) {
                v.put(x, y, z, retiled);
                return;
            }
            if (y == top + 1) {
                return; // splinters, gone
            }
            int a = x * e[0] + z * e[1], c = x * pp[0] + z * pp[1], u = y - snap + h0, hh = h0 - a;
            if (hh < 0) {
                return; // the limbs it fell on, snapped off
            }
            v.put(u * e[0] + c * pp[0], hh, u * e[1] + c * pp[1], retiled);
        });
        for (int y = 0; y <= snap + 3; y++) { // the stump, hollow
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (hypot(x, z) < rw - 1.1) {
                        v.del(x, y, z);
                    }
                }
            }
        }
        brackets(barkSpots(1, snap + 2, 6, 3, 35));
    }

    /** Leaves lit where the sky is over them and dark on a pad's underside, and sprays hanging under the rims. */
    private void finishLeaves() {
        List<int[]> edits = new ArrayList<>();
        v.each((x, y, z, b) -> {
            if (kind(b) != LEAF) {
                return;
            }
            int over = v.get(x, y + 1, z), below = v.get(x, y - 1, z);
            double u = hash(x, y, z, 77);
            boolean open = over == 0 || kind(over) != LEAF, bottom = below == 0 || kind(below) != LEAF;
            int tone = open ? (u < 0.68 ? 0 : 1) : bottom ? (u < 0.7 ? 2 : 1) : (u < 0.2 ? 0 : u < 0.72 ? 1 : 2);
            edits.add(new int[] {x, y, z, code(LEAF, 0, tone)});
            if (below == 0 && v.get(x, y - 2, z) == 0 && hash(x, y, z, 78) < 0.2) {
                edits.add(new int[] {x, y - 1, z, code(CROSS, 0, 0)});
            }
        });
        for (int[] e : edits) {
            v.set(e[0], e[1], e[2], e[3]);
        }
    }
}
