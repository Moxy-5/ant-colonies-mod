package com.antcolonies.savanna.tree;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.block.DoumLeafBaseBlock;
import com.antcolonies.savanna.block.DoumLook;
import com.antcolonies.savanna.block.DoumRootBlock;
import com.antcolonies.savanna.block.DoumWoodBlock;
import com.antcolonies.savanna.block.HangingFruit;
import com.antcolonies.savanna.registry.TreeBlocks;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The doum palm's shapes (floodplain trees step 6, 2026-11-14): the palms the user picked in the model viewer
 * ({@code tools/floodplain_viewer.template.html}, {@code doum()}, {@code deadDoum}, {@code doumSeedling},
 * {@code doumRosette}, {@code doumStem}, {@code doumFork}, {@code doumClump}; shape B and bark A, the user's picks),
 * worked out the same way number for number, so one seed is the one palm seen there. {@code DoumShapeTest} checks it
 * block for block, and every crown, frond, sucker and leaf-base stub, against the viewer's own palms
 * ({@code node tools/doum_shape_export.js} writes them).
 *
 * <p>A real doum palm (Hyphaene thebaica): one of the few palms that branch, its stems splitting evenly in two (a Y),
 * each arm with its own crown of fan leaves; a clump of stems from a swollen foot, old leaf bases clothing the stems.
 * The ages and dead stages are the viewer's: seedling, rosette (no stem yet), young stem, first fork, clump (the old
 * shape A), grown clump (shape B); just died, standing snags, the last snag.
 *
 * <p>The stems are blocks ({@link #cells()}: wood and root steps, living or dead grey). The sculpted pieces are each one
 * block drawing its recipe ({@link DoumLook}): every crown, its fruit bunches, the old fronds lying on the ground, the
 * suckers and the seedling ({@link #draws()}); the leaf-base stubs are small blocks of their own ({@link #stubs()}).
 */
public final class DoumShape {
    /** The ages and dead stages of the viewer, by its names. */
    public enum Age {
        SEEDLING("seed"), ROSETTE("rosette"), STEM("stem"), FORK("fork"), CLUMP("clump"), GROWN("grown"),
        JUST_DIED("dead1"), SNAGS("dead2"), LAST_SNAG("dead3");

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

    /** What a sculpted piece is. */
    public enum DrawKind { CROWN, FROND, ROSETTE, SEEDLING }

    /**
     * A sculpted piece as the viewer drew it: a crown (state the dice where it starts, t the middle of its stem's top,
     * size, its old fronds, dead, how many bunches, -1 the grown palm's 2 or 3; at where each bunch hangs), an old frond
     * lying on the ground (state, tx how far out from the clump, old: bleached grey; at its foot), a sucker or the young
     * palm (state its seed, size, tx and tz how far over it stands) or the seedling (state its seed). bits: how many
     * sculpted boxes it makes.
     */
    public record Draw(DrawKind kind, int state, double tx, double ty, double tz, double size, boolean old, boolean dead, int bunches,
                       int bits, List<double[]> at) {
    }

    /**
     * An old leaf's base on a stem: the stem's block (x, y, z) and the way out from it (d0, d1); oy and sh where the
     * viewer set its two boxes up and to the side, how wide, which boot tint (0 the paler), a dead palm's grey.
     */
    public record Stub(int x, int y, int z, int d0, int d1, double oy, double sh, int wide, int boot, boolean dead) {
        /** The viewer's two boxes: {x, y, z, w, h, d, turn, tint}, the dark sheath then the boot. */
        public double[][] viewerBoxes() {
            double rot = d0 != 0 ? 0 : Math.PI / 2;
            return new double[][] {box(1.5, 0, 3, 6, wide, rot, 0.84), box(2.5, 4, 5, 5, wide - 4, rot, BOOT[boot])};
        }

        private double[] box(double out, double dy, int w, int h, int d, double rot, double tint) {
            return new double[] {x + d0 * (0.5 + out / 16) - d1 * sh, y + oy + dy / 16, z + d1 * (0.5 + out / 16) + d0 * sh, w, h, d, rot, tint};
        }
    }

    private static final double[] BOOT = {1.5, 1.3};

    // The viewer's block shapes: a code is kind << 16 | size << 8 | look (0 the living bark, 1 the dead grey).
    static final int CUBE = 1, SLAB = 3;

    private static int code(int kind, int size, int look) {
        return kind << 16 | size << 8 | look;
    }

    private static int kind(int code) {
        return code >>> 16;
    }

    private static int size(int code) {
        return (code >>> 8) & 0xFF;
    }

    private static int look(int code) {
        return code & 0xFF;
    }

    /** A block's name as the viewer writes it ("cube|bark_doum", "slab5|bark_doumdead"). */
    public static String name(int code) {
        String bark = look(code) == 1 ? "|bark_doumdead" : "|bark_doum";
        return (kind(code) == CUBE ? "cube" : "slab" + size(code)) + bark;
    }

    private static final int WOOD = code(CUBE, 0, 0);

    // ---- The viewer's block map (its class Vox): kept in the order blocks were first put, as a JavaScript Map ----
    private final Long2IntLinkedOpenHashMap m = new Long2IntLinkedOpenHashMap();

    private static long key(int x, int y, int z) {
        return ((long) (x + 1024) * 256 + (y + 64)) * 2048 + (z + 1024);
    }

    private void put(int x, int y, int z, int b) {
        if (y < -1) {
            return;
        }
        long k = key(x, y, z);
        int old = m.get(k);
        if (old == 0 || prio(b) >= prio(old)) {
            m.put(k, b);
        }
    }

    private static int prio(int code) {
        return kind(code) == CUBE ? 5 : 3;
    }

    private int get(int x, int y, int z) {
        return m.get(key(x, y, z));
    }

    private interface Each {
        void at(int x, int y, int z, int b);
    }

    private void each(Each f) {
        for (Long2IntMap.Entry e : m.long2IntEntrySet()) {
            long k = e.getLongKey();
            int z = (int) (k % 2048) - 1024;
            long t = k / 2048;
            f.at((int) (t / 256) - 1024, (int) (t % 256) - 64, z, e.getIntValue());
        }
    }

    // ---- The palm ----
    private final List<Draw> draws = new ArrayList<>();
    private final List<Stub> stubs = new ArrayList<>();
    /** The dice of the palm being made (the crowns and fronds carry it on, as the viewer's one rng). */
    private TreeRandom r;
    /** The palm's noise seed. */
    private int ns = 1;
    /** The seed the palm was made from: its nuts' timetable (step 6b; no dice drawn from it). */
    private int seed;

    private DoumShape() {
    }

    /** The doum of this seed at this age, every block worked out as in the viewer. */
    public static DoumShape make(int seed, Age age) {
        DoumShape d = new DoumShape();
        d.seed = seed;
        switch (age) {
            case SEEDLING -> d.draws.add(new Draw(DrawKind.SEEDLING, seed, 0, 0, 0, 1, false, false, 0, PalmPieces.seedling(seed).size(),
                    List.of()));
            case ROSETTE -> d.rosette(seed, 0.8, 0, 0);
            case STEM -> d.stem(seed);
            case FORK -> d.fork(seed);
            case CLUMP -> d.clump(seed);
            case GROWN -> d.doum(seed, true, 0);
            case JUST_DIED -> d.doum(seed, true, 1);
            case SNAGS -> d.doum(seed, true, 2);
            case LAST_SNAG -> d.doum(seed, true, 3);
        }
        return d;
    }

    /** Every block of the palm's wood as a viewer code, by {@link BlockPos#asLong}, around its foot at (0, 0, 0). */
    public Long2IntLinkedOpenHashMap cells() {
        Long2IntLinkedOpenHashMap out = new Long2IntLinkedOpenHashMap();
        each((x, y, z, b) -> out.put(BlockPos.asLong(x, y, z), b));
        return out;
    }

    /** The sculpted pieces, in the order the viewer drew them. */
    public List<Draw> draws() {
        return draws;
    }

    /** The leaf-base stubs, in the order the viewer made them. */
    public List<Stub> stubs() {
        return stubs;
    }

    // ---- Into the game's blocks ----

    /** The palm as the game's blocks, and the recipes its sculpted pieces draw (by the same keys). */
    public record Placed(Long2ObjectLinkedOpenHashMap<BlockState> blocks, Long2ObjectLinkedOpenHashMap<DoumLook> looks) {
        /**
         * Sets the palm down around its foot (wood first, then what hangs on it, in order), never over a barrier, and gives
         * every sculpted piece its recipe. Returns how many blocks were set.
         */
        public int setDown(net.minecraft.world.level.Level level, BlockPos foot) {
            int set = 0;
            for (var e : blocks.long2ObjectEntrySet()) {
                BlockPos pos = foot.offset(BlockPos.of(e.getLongKey()));
                if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BARRIER)) {
                    continue;
                }
                level.setBlock(pos, e.getValue(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                DoumLook look = looks.get(e.getLongKey());
                if (look != null && level.getBlockEntity(pos) instanceof com.antcolonies.savanna.block.DoumBlockEntity piece) {
                    piece.setLook(look);
                }
                set++;
            }
            return set;
        }
    }

    /** How many sculpted pieces the palm has: every crown and each of its bunches, every frond heap, sucker and seedling. */
    public int pieces() {
        int n = 0;
        for (Draw d : draws) {
            n += d.kind == DrawKind.CROWN ? 1 + d.at.size() : 1;
        }
        return n;
    }

    /**
     * The palm as the game's blocks, around its foot at (0, 0, 0) (y = -1 is the ground): the stems (doum wood, bark all
     * round, living or dead grey), the root steps, then a block for every sculpted piece with its recipe: each crown on
     * top of its stem, each of its fruit bunches where it hangs, the fronds lying on the ground, the suckers, the
     * seedling; then the leaf-base stubs beside the stems. A piece whose place is taken moves a block up or down (my
     * pick) or is left out. Needs the game's blocks (not for plain unit tests).
     */
    public Placed placed() {
        Long2ObjectLinkedOpenHashMap<BlockState> out = new Long2ObjectLinkedOpenHashMap<>();
        Long2ObjectLinkedOpenHashMap<DoumLook> looks = new Long2ObjectLinkedOpenHashMap<>();
        each((x, y, z, b) -> out.put(BlockPos.asLong(x, y, z), state(b)));
        for (Draw d : draws) {
            switch (d.kind) {
                case CROWN -> {
                    // The cap sits half a block over the stem's top, its middle on a block edge (a stem two blocks wide):
                    // the crown's block is one of the four round that edge.
                    int fx = (int) Math.floor(d.tx), fy = (int) Math.round(d.ty) + 1, fz = (int) Math.floor(d.tz);
                    long at = free(out, new int[][] {{fx, fy, fz}, {fx + 1, fy, fz}, {fx, fy, fz + 1}, {fx + 1, fy, fz + 1},
                            {fx, fy + 1, fz}, {fx + 1, fy + 1, fz + 1}});
                    if (at != Long.MIN_VALUE) {
                        out.put(at, TreeBlocks.DOUM_CROWN.get().defaultBlockState());
                        looks.put(at, look(d, at, 0));
                    }
                    for (int j = 0; j < d.at.size(); j++) {
                        double[] p = d.at.get(j);
                        int bx = (int) Math.round(p[0]), by = (int) Math.round(p[1]), bz = (int) Math.round(p[2]);
                        long bt = free(out, new int[][] {{bx, by, bz}, {bx, by - 1, bz}, {bx, by + 1, bz}});
                        if (bt != Long.MIN_VALUE) {
                            out.put(bt, HangingFruit.onTree(TreeBlocks.DOUM_NUT_BUNCH.get().defaultBlockState(), seed));
                            looks.put(bt, look(d, bt, j));
                        }
                    }
                }
                case FROND -> {
                    double[] foot = d.at.get(0);
                    int fx = (int) Math.round(foot[0]), fz = (int) Math.round(foot[2]);
                    long at = free(out, new int[][] {{fx, 0, fz}, {fx, 1, fz}, {fx, 2, fz}});
                    if (at != Long.MIN_VALUE) {
                        out.put(at, TreeBlocks.DOUM_FRONDS.get().defaultBlockState());
                        looks.put(at, look(d, at, 0));
                    }
                }
                case ROSETTE, SEEDLING -> {
                    long at = BlockPos.asLong((int) d.tx, 0, (int) d.tz);
                    if (!out.containsKey(at)) {
                        out.put(at, (d.kind == DrawKind.ROSETTE ? TreeBlocks.DOUM_ROSETTE : TreeBlocks.DOUM_SEEDLING).get().defaultBlockState());
                        looks.put(at, new DoumLook(true, d.state, 0, 0, 0, 0, 0, 0, d.size, false, false, 0, 0));
                    }
                }
            }
        }
        for (Stub s : stubs) {
            long at = BlockPos.asLong(s.x + s.d0, s.y, s.z + s.d1);
            if (out.containsKey(at)) {
                continue;
            }
            out.put(at, TreeBlocks.DOUM_LEAF_BASE.get().defaultBlockState()
                    .setValue(DoumLeafBaseBlock.FACING, Direction.fromDelta(s.d0, 0, s.d1))
                    .setValue(DoumLeafBaseBlock.LOW, s.oy < 0).setValue(DoumLeafBaseBlock.WIDE, s.wide == 14)
                    .setValue(DoumLeafBaseBlock.PALE, s.boot == 0)
                    .setValue(DoumLeafBaseBlock.SHIFT, Math.max(0, Math.min(4, (int) Math.round(s.sh * 16) + 2)))
                    .setValue(DoumLeafBaseBlock.DEAD, s.dead));
        }
        return new Placed(out, looks);
    }

    /** The first of these places not yet taken, or {@link Long#MIN_VALUE}. */
    private static long free(Long2ObjectLinkedOpenHashMap<BlockState> taken, int[][] places) {
        for (int[] p : places) {
            long k = BlockPos.asLong(p[0], p[1], p[2]);
            if (!taken.containsKey(k)) {
                return k;
            }
        }
        return Long.MIN_VALUE;
    }

    private static DoumLook look(Draw d, long at, int bunch) {
        BlockPos c = BlockPos.of(at);
        return new DoumLook(true, d.state, d.tx, d.ty, d.tz, c.getX(), c.getY(), c.getZ(), d.size, d.old, d.dead, d.bunches, bunch);
    }

    private static BlockState state(int code) {
        boolean dead = look(code) == 1;
        if (kind(code) == CUBE) {
            return TreeBlocks.DOUM_WOOD.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
                    .setValue(DoumWoodBlock.DEAD, dead);
        }
        return TreeBlocks.DOUM_ROOT.get().defaultBlockState().setValue(DoumRootBlock.LOW, size(code) < 8).setValue(DoumRootBlock.DEAD, dead);
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

    private static double[] p(double x, double y, double z) {
        return new double[] {x, y, z};
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

    /** A round piece of wood from a to b, radius ra to rb, in full blocks (the viewer's seg). */
    private void seg(double[] a, double[] b, double ra, double rb) {
        double[] ab = sub(b, a);
        double l2 = Math.max(1e-6, ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2]);
        double rr = Math.max(ra, rb) + 1;
        int[] lo = new int[3], hi = new int[3];
        for (int i = 0; i < 3; i++) {
            lo[i] = (int) Math.floor(Math.min(a[i], b[i]) - rr);
            hi[i] = (int) Math.ceil(Math.max(a[i], b[i]) + rr);
        }
        for (int x = lo[0]; x <= hi[0]; x++) {
            for (int y = Math.max(-1, lo[1]); y <= hi[1]; y++) {
                for (int z = lo[2]; z <= hi[2]; z++) {
                    double t = clamp(((x - a[0]) * ab[0] + (y - a[1]) * ab[1] + (z - a[2]) * ab[2]) / l2, 0, 1);
                    if (FigShape.hypot(x - (a[0] + ab[0] * t), y - (a[1] + ab[1] * t), z - (a[2] + ab[2] * t)) <= lerp(ra, rb, t)) {
                        put(x, y, z, WOOD);
                    }
                }
            }
        }
    }

    private static final double[] H = {0.5, 0, 0.5};

    /** Wood along a line, its radius falling evenly from r0 to r1, on the stems' grid between blocks (drawWood, doumWood). */
    private void drawWood(List<double[]> pts, double r0, double r1) {
        int n = pts.size();
        for (int i = 0; i + 1 < n; i++) {
            seg(add(pts.get(i), H), add(pts.get(i + 1), H), lerp(r0, r1, (double) i / (n - 1)), lerp(r0, r1, (double) (i + 1) / (n - 1)));
        }
    }

    /** A young stem as a column two blocks by two at every height, its middle following the line (column2). */
    private void column2(List<double[]> pts) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            for (int y = (int) Math.ceil(a[1]); y <= (int) Math.floor(b[1]); y++) {
                double t = (y - a[1]) / (b[1] - a[1]);
                int cx = round(lerp(a[0], b[0], t)), cz = round(lerp(a[2], b[2], t));
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        put(cx + dx, y, cz + dz, WOOD);
                    }
                }
            }
        }
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Root steps: half-height pieces in the corners of the steps at the palm's foot. */
    private void rootSteps(int span, int top) {
        for (int y = 0; y <= top; y++) {
            for (int x = -span; x <= span; x++) {
                for (int z = -span; z <= span; z++) {
                    if (get(x, y, z) != 0 || (y > 0 && kind(get(x, y - 1, z)) != CUBE)) {
                        continue;
                    }
                    int near = 0;
                    for (int[] d : SIDES) {
                        if (kind(get(x + d[0], y, z + d[1])) == CUBE) {
                            near++;
                        }
                    }
                    if (near == 0 || hash(x, y, z, ns + 2) > (near > 1 ? 0.9 : 0.6)) {
                        continue;
                    }
                    put(x, y, z, code(SLAB, hash(x, y, z, ns + 4) < 0.6 ? 8 : 5, 0));
                }
            }
        }
    }

    /** Old leaf bases: stubs out and up from the stems, more of them toward the crowns (lo at the foot to hi at maxY). */
    private void leafBases(double maxY, double lo, double hi) {
        each((x, y, z, b) -> {
            if (b != WOOD || y < 0 || FigShape.hypot(x, z) > 14) {
                return;
            }
            for (int[] d : SIDES) {
                if (get(x + d[0], y, z + d[1]) != 0) {
                    continue;
                }
                double u = hash(x * 4 + d[0], y, z * 4 + d[1], ns + 1);
                if (u > lerp(lo, hi, clamp(y / maxY, 0, 1))) {
                    continue;
                }
                double oy = (hash(x, y, z, ns + 6) < 0.5 ? 3 : 9) / 16.0 - 0.5, sh = ((u * 97) % 1 - 0.5) * 4 / 16;
                int wide = u * 31 % 1 < 0.5 ? 14 : 10;
                stubs.add(new Stub(x, y, z, d[0], d[1], oy, sh, wide, u * 53 % 1 < 0.5 ? 0 : 1, false));
            }
        });
    }

    /** A crown on its stem's top (the viewer's palmCrown, drawn by {@link PalmPieces}); the dice go on after it. */
    private void crown(double[] t, double size, boolean dead, int bunches) {
        int state = r.state();
        double tx = Math.round(t[0]) + 0.5, ty = Math.round(t[1]), tz = Math.round(t[2]) + 0.5;
        PalmPieces.Made made = PalmPieces.crownMade(state, tx, ty, tz, size, true, dead, bunches);
        r = new TreeRandom(made.end());
        draws.add(new Draw(DrawKind.CROWN, state, tx, ty, tz, size, true, dead, bunches, made.bits().size(), made.at()));
    }

    private interface Bunches {
        int of(int i);
    }

    /** A crown on every stem tip; bunches(i): how many bunches the i-th carries (null: the grown palm's 2 or 3). */
    private void crowns(List<double[]> tips, double size, Bunches bunches) {
        for (int i = 0; i < tips.size(); i++) {
            crown(tips.get(i), size, false, bunches == null ? -1 : bunches.of(i));
        }
    }

    /** Old fronds lying on the ground round the clump, their feet R to R + 2 out; grey: bleached by years. */
    private void groundFronds(int n, double rr, boolean grey) {
        for (int i = 0; i < n; i++) {
            int state = r.state();
            PalmPieces.Made made = PalmPieces.groundFrondMade(state, rr, grey);
            r = new TreeRandom(made.end());
            draws.add(new Draw(DrawKind.FROND, state, rr, 0, 0, 1, grey, true, 0, made.bits().size(), made.at()));
        }
    }

    /** A stemless young palm (or a sucker, smaller), dx, dz blocks over (doumRosette). */
    private void rosette(int seed, double size, int dx, int dz) {
        draws.add(new Draw(DrawKind.ROSETTE, seed, dx, 0, dz, size, false, false, 0, PalmPieces.rosette(seed, size).size(), List.of()));
    }

    private static double maxY(List<double[]> pts) {
        double m = Double.NEGATIVE_INFINITY;
        for (double[] q : pts) {
            m = Math.max(m, q[1]);
        }
        return m;
    }

    // ================= The palms =================
    private record Part(int stem, List<double[]> pts, double r0, double r1) {
    }

    private record Stem(double[] o, double az, double out, double lean, boolean fork, boolean upright) {
    }

    /** The clump (the viewer's doum): shape B (old) four stems, two forked; A three, one forked. dead: the stage. */
    private void doum(int seed, boolean old, int dead) {
        r = new TreeRandom(seed * 15485863L + 3);
        ns = seed * 17 + 5;
        double rb = old ? 2.3 : 2.0, hb = old ? 2.4 : 2.0;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = -1; y <= 4; y++) { // the clump's middle: a low lumpy mound
                    double th = StrictMath.atan2(z, x), wob = 0.8 + 0.4 * vnoise(StrictMath.cos(th) * 1.5, y * 0.5, StrictMath.sin(th) * 1.5, ns);
                    double a = FigShape.hypot(x, z) / (rb * wob), c = Math.max(0, y) / hb;
                    if (a * a + c * c <= 1) {
                        put(x, y, z, WOOD);
                    }
                }
            }
        }
        List<Part> parts = new ArrayList<>();
        List<Stem> stems = new ArrayList<>();
        List<double[]> tips = new ArrayList<>();
        int n = old ? 4 : 3;
        double az0 = r.next() * 6.28;
        int upright = (int) Math.floor(r.next() * n);
        boolean[] forked = new boolean[n];
        forked[(upright + 1 + (int) Math.floor(r.next() * (n - 1))) % n] = true;
        if (old) {
            forked[upright] = true;
        }
        for (int i = 0; i < n; i++) {
            double az = az0 + (double) i / n * 6.28 + (r.next() - 0.5) * 0.5;
            double[] o = {StrictMath.cos(az), 0, StrictMath.sin(az)};
            boolean fork = forked[i];
            double lean = i == upright ? 0.8 + r.next() : (old ? 5.2 : 4.6) + r.next() * 1.6;
            // a forked stem splits about four blocks under where its crown would be, so the arms end near the same height
            double ht = (i == upright ? (old ? 12 : 11) + r.next() : (old ? 9 : 7.6) + r.next() * 2) - (fork ? 3.6 : 0);
            double out = rb * 0.55;
            double[] p0 = add(mul(o, out), p(0, -1, 0)), pa = add(mul(o, out + lean * 0.3), p(0, hb + 0.6, 0));
            double[] p1 = add(mul(o, out + lean * 0.75), p(0, hb + (ht - hb) * 0.45, 0)), p2 = add(mul(o, out + lean * 1), p(0, ht, 0));
            stems.add(new Stem(o, az, out, lean, fork, i == upright));
            parts.add(new Part(i, List.of(p0, pa), old ? 1.5 : 1.35, 0.85));
            parts.add(new Part(i, List.of(pa, p1, p2), 0.85, 0.78));
            if (!fork) {
                tips.add(p2);
                continue;
            }
            int again = old && i == upright && r.next() < 0.6 ? (r.next() < 0.5 ? 1 : -1) : 0;
            for (int sgn : new int[] {1, -1}) {
                double rise = 3.4 + r.next() * 0.8, reach = 2.6 + r.next() * 0.6;
                arm(parts, tips, i, p2, az, sgn, rise, reach, sgn == again);
            }
        }
        if (dead != 0) {
            deadDoum(seed, dead, parts, stems, tips, old, rb, hb);
            return;
        }
        for (Part q : parts) {
            drawWood(q.pts, q.r0, q.r1);
        }
        rootSteps(7, 2);
        leafBases(maxY(tips), 0.18, 0.7);
        crowns(tips, old ? 1.06 : 1, null);
    }

    /** One arm of a fork, leaning out to its side, then up to its crown (again: it forks once more, across the first). */
    private void arm(List<Part> parts, List<double[]> tips, int stem, double[] from, double az, int sgn, double rise, double reach, boolean again) {
        double[] side = {StrictMath.cos(az + sgn * Math.PI / 2), 0, StrictMath.sin(az + sgn * Math.PI / 2)};
        double[] o = {StrictMath.cos(az), 0, StrictMath.sin(az)};
        double[] q1 = add(from, add(mul(side, reach * 0.7), add(mul(o, 0.4), p(0, rise * 0.4, 0))));
        double[] q2 = add(q1, add(mul(side, reach * 0.3), p(0, rise * 0.6, 0)));
        parts.add(new Part(stem, List.of(from, q1, q2), 0.78, 0.78));
        if (!again) {
            tips.add(q2);
            return;
        }
        for (int s2 : new int[] {1, -1}) {
            arm(parts, tips, stem, q2, az + sgn * Math.PI / 2, s2, rise * 0.75, reach * 0.8, false);
        }
    }

    /** A line cut off where it rises past maxY; null when it starts above it (clipY). */
    private static List<double[]> clipY(List<double[]> pts, double maxY) {
        if (pts.get(0)[1] >= maxY) {
            return null;
        }
        List<double[]> out = new ArrayList<>();
        out.add(pts.get(0));
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            if (b[1] <= maxY) {
                out.add(b);
                continue;
            }
            out.add(add(a, mul(sub(b, a), (maxY - a[1]) / (b[1] - a[1]))));
            break;
        }
        return out;
    }

    /** A stem lying on the ground, two blocks thick, from a to b; forked: its two arms spread at the far end (lyingStem). */
    private void lyingStem(double[] a, double[] b, boolean fork) {
        double[] la = {a[0], 0.5, a[2]}, lb = {b[0], 0.5, b[2]};
        drawWood(List.of(la, lb), 0.8, 0.74);
        if (!fork) {
            return;
        }
        double[] d = sub(b, a);
        double az = StrictMath.atan2(d[2], d[0]);
        for (double s : new double[] {0.6, -0.6}) {
            drawWood(List.of(lb, p(b[0] + StrictMath.cos(az + s) * 2.6, 0.5, b[2] + StrictMath.sin(az + s) * 2.6)), 0.74, 0.7);
        }
    }

    /**
     * A dead doum (deadDoum). Stage 1, just died: the stems whole, every crown brown and hanging, no spear, no fruit.
     * Stage 2, standing snags: the crowns gone, the stems topped lower, one snapped halfway and lying beside the clump,
     * few leaf bases left, the bark grey. Stage 3, the last snag: one stem standing, the others broken off above the clump
     * and lying out where they fell.
     */
    private void deadDoum(int seed, int dead, List<Part> parts, List<Stem> stems, List<double[]> tips, boolean old, double rb, double hb) {
        TreeRandom rd = new TreeRandom(seed * 9173L + 29);
        double[] top = new double[stems.size()];
        double maxTop = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < stems.size(); i++) {
            top[i] = Double.NEGATIVE_INFINITY;
            for (Part q : parts) {
                if (q.stem == i) {
                    top[i] = Math.max(top[i], maxY(q.pts));
                }
            }
            maxTop = Math.max(maxTop, top[i]);
        }
        if (dead == 1) {
            for (Part q : parts) {
                drawWood(q.pts, q.r0, q.r1);
            }
            rootSteps(7, 2);
            leafBases(maxTop, 0.18, 0.7);
            for (double[] t : tips) {
                crown(t, old ? 1.06 : 1, true, -1);
            }
            groundFronds(5, rb + 1, false);
            return;
        }
        // where each stem now ends, and which lie on the ground
        List<Integer> others = new ArrayList<>();
        for (int i = 0; i < stems.size(); i++) {
            if (!stems.get(i).upright) {
                others.add(i);
            }
        }
        int broke = others.get((int) Math.floor(rd.next() * others.size()));
        double[] cap = new double[stems.size()];
        for (int i = 0; i < stems.size(); i++) {
            cap[i] = dead == 2 ? (i == broke ? top[i] * 0.55 : top[i] - 1 - rd.next() * 2)
                    : stems.get(i).upright ? 3.5 + rd.next() * 2 : hb + 1 + rd.next();
        }
        for (Part q : parts) {
            List<double[]> c = clipY(q.pts, cap[q.stem]);
            if (c != null && c.size() > 1) {
                drawWood(c, q.r0, q.r1);
            }
        }
        rootSteps(7, 2);
        if (dead == 2) {
            leafBases(maxTop, 0.05, 0.25);
        }
        for (int i = 0; i < stems.size(); i++) { // the stems that fell, lying out along their lean
            Stem st = stems.get(i);
            if (dead == 2 ? i != broke : st.upright) {
                continue;
            }
            double from = st.out + st.lean * 0.6 + 1.6, len = (top[i] - cap[i]) * (dead == 2 ? 0.8 : 0.85);
            double sw = (rd.next() - 0.5) * 0.5;
            double[] d = {StrictMath.cos(st.az + sw), 0, StrictMath.sin(st.az + sw)};
            lyingStem(mul(d, from), mul(d, from + len), st.fork && dead == 3);
        }
        groundFronds(dead == 2 ? 5 : 3, rb + 1, dead == 3);
        // greyBark: every block and stub turned to the dead palm's grey
        for (Long2IntMap.Entry e : m.long2IntEntrySet()) {
            e.setValue(code(kind(e.getIntValue()), size(e.getIntValue()), 1));
        }
        stubs.replaceAll(s -> new Stub(s.x, s.y, s.z, s.d0, s.d1, s.oy, s.sh, s.wide, s.boot, true));
    }

    /** Young stem (doumStem): one column, swollen at the foot, clothed in old leaf bases, not forked, no fruit. */
    private void stem(int seed) {
        r = new TreeRandom(seed * 3571L + 7);
        ns = seed * 17 + 9;
        double az = r.next() * 6.28;
        double[] o = {StrictMath.cos(az), 0, StrictMath.sin(az)};
        double ht = 7 + r.next() * 1.2, lean = 0.6 + r.next() * 0.7;
        drawWood(List.of(p(0, -1, 0), p(0, 1.1, 0)), 1.3, 0.82);
        double[] tip = add(mul(o, lean), p(0, ht, 0));
        column2(List.of(p(0, 0, 0), add(mul(o, lean * 0.35), p(0, ht * 0.5, 0)), tip));
        rootSteps(3, 1);
        leafBases(ht, 0.5, 0.78);
        crowns(List.of(tip), 0.88, i -> 0);
    }

    /** First fork (doumFork): the stem splits evenly in two, a crown on each arm, the first bunch, the first sucker. */
    private void fork(int seed) {
        r = new TreeRandom(seed * 8191L + 23);
        ns = seed * 17 + 11;
        double az = r.next() * 6.28;
        double[] o = {StrictMath.cos(az), 0, StrictMath.sin(az)};
        double ht = 6.4 + r.next() * 1.0, lean = 0.8 + r.next() * 0.8;
        double[] p2 = add(mul(o, lean), p(0, ht, 0));
        drawWood(List.of(p(0, -1, 0), p(0, 1.2, 0)), 1.4, 0.84);
        column2(List.of(p(0, 0, 0), add(mul(o, lean * 0.35), p(0, ht * 0.5, 0)), p2));
        List<double[]> tips = new ArrayList<>();
        for (int sgn : new int[] {1, -1}) {
            double[] side = {StrictMath.cos(az + sgn * Math.PI / 2), 0, StrictMath.sin(az + sgn * Math.PI / 2)};
            double rise = 3 + r.next() * 0.6, reach = 2.3 + r.next() * 0.4;
            double[] q1 = add(p2, add(mul(side, reach * 0.7), add(mul(o, 0.3), p(0, rise * 0.4, 0))));
            double[] q2 = add(q1, add(mul(side, reach * 0.3), p(0, rise * 0.6, 0)));
            drawWood(List.of(p2, q1, q2), 0.92, 0.85);
            tips.add(q2);
        }
        rootSteps(4, 1);
        leafBases(maxY(tips), 0.4, 0.75);
        crowns(tips, 0.95, i -> i == 0 ? 1 : 0);
        rosette(seed + 101, 0.42, -round(o[0] * 3), -round(o[2] * 3)); // the first sucker
    }

    /** Clump (doumClump): the shape A palm, new suckers coming up round its edge. */
    private void clump(int seed) {
        doum(seed, false, 0);
        TreeRandom rr = new TreeRandom(seed * 991L + 29);
        double a0 = rr.next() * 6.28;
        int left = 2;
        for (int i = 0; i < 12 && left > 0; i++) {
            double a = a0 + i * 0.52;
            int x = round(StrictMath.cos(a) * 3.4), z = round(StrictMath.sin(a) * 3.4);
            boolean free = true;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 0; y <= 3; y++) {
                        if (get(x + dx, y, z + dz) != 0) {
                            free = false;
                        }
                    }
                }
            }
            if (!free) {
                continue;
            }
            left--;
            rosette(seed + 200 + i, 0.38 + rr.next() * 0.1, x, z);
        }
    }
}
