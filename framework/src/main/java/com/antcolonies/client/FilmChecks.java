package com.antcolonies.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.RenderUtil;

/**
 * The in-game camera's automatic checks (development only, 2026-10-16): for every picture it takes, where each body part
 * of each filmed insect (and, since 2026-11-25, any animal drawn with GeckoLib) was really drawn (the corners of every box of the model, in the world), so that Claude only has
 * to look at the pictures where something went wrong:
 * <ul>
 * <li><b>inside</b>: a body part drawn inside a solid block (deeper than {@link #INSIDE} blocks), with the block; or
 * a block's jutting edge (a step's or a bank's lip) drawn inside a body part, which no point of the part's own shows
 * (code review R12-5, 2026-11-27);</li>
 * <li><b>jump</b>: a body part that moves far more from one picture to the next than just before and after it, and
 * than its own big moves in the rest of the scene (a pop:
 * a clip changing at the wrong moment, a bend piling up, a turn snapping), measured against the insect itself, so its
 * walking about does not count.</li>
 * </ul>
 * {@code FilmDirector} calls {@link #record} for each insect drawn in a picture and {@link #report} at the end of each
 * scene, which writes {@code checks.txt} beside the scene's pictures.
 */
final class FilmChecks {
    /**
     * How deep in a block a part may be drawn before it counts (blocks; a foot pressed onto the floor is fine), or one
     * pixel of the animal's model as drawn, if more (2026-11-25: a lion's paw a pixel into the grass, as the offline
     * checker allows a foot, is not a fault; an insect's pixel is far under this).
     */
    static final double INSIDE = 0.01;
    /** A jump must be at least this many blocks a tick (the worker is about 0.3 long) ... */
    static final double JUMP_MIN = 0.03;
    /** ... and this many times the usual move of that part in the pictures round it. */
    static final double JUMP_TIMES = 3.0;
    /**
     * ... and this many times the part's own big moves in the whole scene (the move 9 in 10 are below): a leg starting
     * its quick swing moves far more than in the picture before, but no more than in its other swings.
     */
    static final double ABOVE_BIG = 1.5;
    /**
     * ... or, whatever its big moves are, this many times its usual move round it (code review R12-41; the review's
     * pick): the part's big moves are no bar for a pop that comes back (a loop's seam, a clip change at every stride:
     * once more than 1 move in 10 is such a pop, the bar is the pop) nor in a short take (under 20 pictures the bar is
     * its second biggest move, and a part snapping out and back makes two).
     */
    static final double SURE_TIMES = 6.0;
    /**
     * Two pictures more than this many game ticks apart are not one take (2026-11-25: a scene's bursts with the game
     * running between them read as jumps of whole blocks): no move is measured between them.
     */
    static final int TAKE_GAP = 8;
    /** How many points along each side of a box are tested for being inside a block (3: corners, edge middles, ...). */
    private static final int GRID = 3;
    /** How many points along a block's jutting edge are tested for being inside a box. */
    private static final int LIP_POINTS = 9;
    /** A box reaching over more blocks than this is not tested against their edges (a guard, never met by a model). */
    private static final int LIP_BLOCKS = 512;

    /**
     * One insect's part in one picture: its box corners relative to the insect, how deep it was in a block, and the
     * moment it was drawn at (game ticks, the in-between time included).
     */
    private record Sample(String picture, int tick, double time, List<Vec3> corners, double depth, String block, Vec3 at) {
    }

    /** Each insect's parts in picture order (insect id, then bone name), and the moment last recorded for each insect. */
    private final Map<Integer, Map<String, List<Sample>>> tracks = new LinkedHashMap<>();
    private final Map<Integer, String> lastMoment = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    /** Each animal's inside limit (blocks): {@link #INSIDE} or a pixel of its model as drawn. */
    private final Map<Integer, Double> limits = new HashMap<>();
    private static final Set<BakedGeoModel> tracked = new HashSet<>();
    private int pictures;

    /**
     * Records where this insect's parts were drawn, just after it was drawn for {@code picture} at that in-between time.
     * The bones are shared by every insect of a model, so this has to run right after each one is drawn.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void record(AbstractAntEntity ant, GeoModel model, float partial, String picture) {
        record(ant, model.getBakedModel(model.getModelResource(ant)), partial, picture, null, null);
    }

    /**
     * The same for any animal drawn with GeckoLib (the Motion Studio, phase 0, 2026-11-25: until then only the insects
     * were checked, the land and river animals filmed unchecked): called right after it is drawn, at the in-between
     * time it was drawn at. An animal drawn by its holder (a fish in a jaw) comes with its frame in the holder's and
     * where the holder was drawn ({@link BoneMeshRenderer#heldFrame}; code review R12-45: GeckoLib's own matrices
     * leave the jaw out, so it was checked upright at its own place); both null for any other.
     */
    void record(Entity animal, BakedGeoModel baked, float partial, String picture, Matrix4f heldFrame, Vec3 heldAt) {
        if (tracked.add(baked)) {
            baked.topLevelBones().forEach(FilmChecks::trackMatrices);
            return;                             // the matrices are filled from the next frame on
        }
        String moment = animal.tickCount + "/" + partial;
        if (moment.equals(lastMoment.put(animal.getId(), moment))) {
            return;                             // the same moment seen from another camera
        }
        pictures++;
        names.put(animal.getId(), animal.getType().toShortString() + (animal.getCustomName() == null ? "" : " " + animal.getCustomName().getString()));
        Vec3 origin = heldAt != null ? heldAt : animal.getPosition(partial);
        double time = animal.tickCount + partial;
        Map<String, List<Sample>> parts = tracks.computeIfAbsent(animal.getId(), id -> new LinkedHashMap<>());
        if (!baked.topLevelBones().isEmpty()) {
            // a model pixel as drawn: the root bone's scale (its matrix's first column) over 16
            Matrix4f root = drawn(baked.topLevelBones().get(0), heldFrame);
            double px = Math.sqrt(root.m00() * root.m00() + root.m01() * root.m01() + root.m02() * root.m02()) / 16.0;
            limits.put(animal.getId(), Math.max(INSIDE, Double.isFinite(px) ? px : 0.0));
        }
        for (GeoBone bone : baked.topLevelBones()) {
            sample(bone, heldFrame, origin, picture, animal.tickCount, time, limits.getOrDefault(animal.getId(), INSIDE), parts);
        }
    }

    /**
     * A bone's matrix as drawn, relative to where the animal (or its holder) was drawn. GeckoLib's own "local space"
     * matrix has the do-nothing matrix added to it (its translateMatrix adds a whole matrix, not just the shift), so
     * it is taken back off.
     */
    private static Matrix4f drawn(GeoBone bone, Matrix4f heldFrame) {
        Matrix4f local = new Matrix4f(bone.getLocalSpaceMatrix()).sub(new Matrix4f());
        return heldFrame == null ? local : new Matrix4f(heldFrame).mul(local);
    }

    private static void trackMatrices(GeoBone bone) {
        bone.setTrackingMatrices(true);
        bone.getChildBones().forEach(FilmChecks::trackMatrices);
    }

    private void sample(GeoBone bone, Matrix4f heldFrame, Vec3 origin, String picture, int tick, double time, double limit,
            Map<String, List<Sample>> parts) {
        if (!bone.isHidden() && !bone.getCubes().isEmpty()) {
            List<Vec3> corners = new ArrayList<>();
            double depth = 0.0;
            String block = null;
            Vec3 at = null;
            for (GeoCube cube : bone.getCubes()) {
                PoseStack pose = new PoseStack();
                pose.last().pose().set(drawn(bone, heldFrame));
                RenderUtil.translateAwayFromPivotPoint(pose, bone);
                RenderUtil.translateToPivotPoint(pose, cube);
                RenderUtil.rotateMatrixAroundCube(pose, cube);
                RenderUtil.translateAwayFromPivotPoint(pose, cube);
                double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
                double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
                for (var quad : cube.quads()) {
                    if (quad == null) {
                        continue;
                    }
                    for (var vertex : quad.vertices()) {
                        lo[0] = Math.min(lo[0], vertex.position().x);
                        lo[1] = Math.min(lo[1], vertex.position().y);
                        lo[2] = Math.min(lo[2], vertex.position().z);
                        hi[0] = Math.max(hi[0], vertex.position().x);
                        hi[1] = Math.max(hi[1], vertex.position().y);
                        hi[2] = Math.max(hi[2], vertex.position().z);
                    }
                }
                if (lo[0] > hi[0]) {
                    continue;
                }
                Lip lip = lipInside(pose.last().pose(), lo, hi, origin);
                // (counted from twice as deep as a part's own points: the camera does not know a foot from a belly,
                // and a foot stands on a step's lip up to about two model pixels deep; the course, which knows the
                // animal's feet, judges body parts from one)
                if (lip != null && lip.depth() > Math.max(depth, 2.0 * limit)) {
                    depth = lip.depth();
                    block = "the edge of " + Minecraft.getInstance().level.getBlockState(lip.pos()).getBlock().getName().getString()
                            + " at " + lip.pos().getX() + " " + lip.pos().getY() + " " + lip.pos().getZ();
                    at = lip.at();
                }
                for (int i = 0; i < GRID; i++) {
                    for (int j = 0; j < GRID; j++) {
                        for (int k = 0; k < GRID; k++) {
                            Vector4f p = pose.last().pose().transform(new Vector4f(
                                    (float) (lo[0] + (hi[0] - lo[0]) * i / (GRID - 1)),
                                    (float) (lo[1] + (hi[1] - lo[1]) * j / (GRID - 1)),
                                    (float) (lo[2] + (hi[2] - lo[2]) * k / (GRID - 1)), 1.0F));
                            Vec3 relative = new Vec3(p.x(), p.y(), p.z());
                            boolean corner = i != 1 && j != 1 && k != 1;
                            if (corner) {
                                corners.add(relative);
                            }
                            Vec3 world = origin.add(relative);
                            BlockPos pos = BlockPos.containing(world);
                            double deep = depthIn(pos, world);
                            if (deep > depth) {
                                depth = deep;
                                block = Minecraft.getInstance().level.getBlockState(pos).getBlock().getName().getString()
                                        + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
                                at = world;
                            }
                        }
                    }
                }
            }
            parts.computeIfAbsent(bone.getName(), name -> new ArrayList<>())
                    .add(new Sample(picture, tick, time, corners, depth, block, at));
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                sample(child, heldFrame, origin, picture, tick, time, limit, parts);
            }
        }
    }

    /** A block's jutting edge found inside a box: how deep, the block's place and the point. */
    private record Lip(double depth, BlockPos pos, Vec3 at) {
    }

    /**
     * The deepest point of any block's jutting edge inside this box (null when none is): the box is lo to hi in its
     * own frame, {@code pose} takes that frame to places relative to {@code origin}. A box's own points (the grid)
     * cannot see this: a belly riding over a step's lip takes the lip into its underside while every one of its
     * points stays outside the step.
     */
    private static Lip lipInside(Matrix4f pose, double[] lo, double[] hi, Vec3 origin) {
        var level = Minecraft.getInstance().level;
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int c = 0; c < 8; c++) {
            Vector4f p = pose.transform(new Vector4f((float) ((c & 1) == 0 ? lo[0] : hi[0]),
                    (float) ((c & 2) == 0 ? lo[1] : hi[1]), (float) ((c & 4) == 0 ? lo[2] : hi[2]), 1.0F));
            double[] w = {origin.x + p.x(), origin.y + p.y(), origin.z + p.z()};
            for (int k = 0; k < 3; k++) {
                min[k] = Math.min(min[k], w[k]);
                max[k] = Math.max(max[k], w[k]);
            }
        }
        BlockPos from = BlockPos.containing(min[0], min[1], min[2]), to = BlockPos.containing(max[0], max[1], max[2]);
        long blocks = (long) (to.getX() - from.getX() + 1) * (to.getY() - from.getY() + 1) * (to.getZ() - from.getZ() + 1);
        if (blocks > LIP_BLOCKS || !Double.isFinite(min[0] + max[0])) {
            return null;
        }
        Matrix4f back = new Matrix4f(pose).invert();
        // blocks to one unit of the box's own frame
        double scale = Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01() + pose.m02() * pose.m02());
        Lip best = null;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            for (AABB box : state.getCollisionShape(level, pos).toAabbs()) {
                double[][] ends = {{box.minX, box.minY, box.minZ}, {box.maxX, box.maxY, box.maxZ}};
                for (int along = 0; along < 3; along++) {
                    int u = (along + 1) % 3, v = (along + 2) % 3;
                    for (int side = 0; side < 4; side++) {
                        int su = side & 1, sv = side >> 1;
                        // an edge juts out where both faces meeting in it are open (a flat floor's seams are not edges)
                        if (covered(pos, u, su, ends) || covered(pos, v, sv, ends)) {
                            continue;
                        }
                        for (int i = 0; i < LIP_POINTS; i++) {
                            double[] w = new double[3];
                            w[along] = ends[0][along] + (ends[1][along] - ends[0][along]) * i / (LIP_POINTS - 1);
                            w[u] = ends[su][u];
                            w[v] = ends[sv][v];
                            Vec3 world = new Vec3(pos.getX() + w[0], pos.getY() + w[1], pos.getZ() + w[2]);
                            Vector4f p = back.transform(new Vector4f((float) (world.x - origin.x),
                                    (float) (world.y - origin.y), (float) (world.z - origin.z), 1.0F));
                            double deep = insideBox(new double[] {p.x(), p.y(), p.z()}, lo, hi) * scale;
                            if (deep > 0.0 && (best == null || deep > best.depth())) {
                                best = new Lip(deep, pos.immutable(), world);
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Whether the face of this box on that side of an axis lies on its block's own face with a whole block against it. */
    private static boolean covered(BlockPos pos, int axis, int side, double[][] ends) {
        if (Math.abs(ends[side][axis] - side) > 1.0e-6) {
            return false;
        }
        var level = Minecraft.getInstance().level;
        Direction.Axis a = Direction.Axis.values()[axis];
        BlockPos next = pos.relative(Direction.fromAxisAndDirection(a,
                side == 1 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE));
        return level.getBlockState(next).isCollisionShapeFullBlock(level, next);
    }

    /** How far inside the box lo to hi this point is, in the box's own units (0 when it is not in it): to its nearest face. */
    static double insideBox(double[] p, double[] lo, double[] hi) {
        double best = Double.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            best = Math.min(best, Math.min(p[k] - lo[k], hi[k] - p[k]));
        }
        return Math.max(0.0, best);
    }

    /** How far inside the block at pos this point is (0 when it is not in it): its distance to the nearest face. */
    private static double depthIn(BlockPos pos, Vec3 point) {
        var level = Minecraft.getInstance().level;
        BlockState state = level.getBlockState(pos);
        double best = 0.0;
        for (AABB box : state.getCollisionShape(level, pos).toAabbs()) {
            AABB b = box.move(pos);
            if (b.contains(point)) {
                double d = Math.min(Math.min(Math.min(point.x - b.minX, b.maxX - point.x),
                        Math.min(point.y - b.minY, b.maxY - point.y)), Math.min(point.z - b.minZ, b.maxZ - point.z));
                best = Math.max(best, d);
            }
        }
        return best;
    }

    boolean isEmpty() {
        return pictures == 0;
    }

    /**
     * The scene's findings, one line each, then a summary line. A move is sized by the time between its two pictures
     * (code review R12-44: it was sized by the scene's last "frames" number, whatever each take had, and not at all
     * by the ticks between two pictures); pictures more than {@link #TAKE_GAP} ticks apart are not compared, and the
     * summary says how many were not, so a take too sparse to judge does not read as "0 jumps".
     */
    List<String> report() {
        List<String> lines = new ArrayList<>();
        int inside = 0, jumps = 0, compared = 0, apart = 0;
        String worstInside = "none", worstJump = "none";
        double worstDepth = 0.0, worstMove = 0.0;
        for (var insect : tracks.entrySet()) {
            String who = names.getOrDefault(insect.getKey(), "?") + " #" + insect.getKey();
            double limit = limits.getOrDefault(insect.getKey(), INSIDE);
            for (var part : insect.getValue().entrySet()) {
                List<Sample> samples = part.getValue();
                for (Sample s : samples) {
                    if (s.depth() > limit) {
                        inside++;
                        lines.add(String.format(Locale.ROOT, "inside %s tick %d %s: %s %.3f deep in %s (%.2f %.2f %.2f)",
                                s.picture(), s.tick(), who, part.getKey(), s.depth(), s.block(), s.at().x, s.at().y, s.at().z));
                        if (s.depth() > worstDepth) {
                            worstDepth = s.depth();
                            worstInside = String.format(Locale.ROOT, "%s %.3f in %s", part.getKey(), s.depth(), s.picture());
                        }
                    }
                }
                double[] moves = new double[samples.size()], far = new double[samples.size()];
                for (int i = 1; i < samples.size(); i++) {
                    moves[i] = aTick(moved(samples.get(i - 1), samples.get(i)), samples.get(i).time() - samples.get(i - 1).time());
                    // how far it went, for the least a jump must be: that is a size, which more ticks between two
                    // pictures do not make smaller (several pictures a tick count it a tick, as they always did)
                    far[i] = Math.max(moves[i], moved(samples.get(i - 1), samples.get(i)));
                    if (Double.isNaN(moves[i])) {
                        apart++;
                    } else {
                        compared++;
                    }
                }
                double big = bigMoves(moves);
                for (int i = 1; i < samples.size(); i++) {
                    double usual = usual(moves, i);
                    if (jump(moves, i, big, far[i])) {
                        jumps++;
                        lines.add(String.format(Locale.ROOT, "jump %s tick %d %s: %s moved %.3f a tick (usually %.3f), from %s",
                                samples.get(i).picture(), samples.get(i).tick(), who, part.getKey(), moves[i], usual,
                                samples.get(i - 1).picture()));
                        if (moves[i] > worstMove) {
                            worstMove = moves[i];
                            worstJump = String.format(Locale.ROOT, "%s %.3f in %s", part.getKey(), moves[i], samples.get(i).picture());
                        }
                    }
                }
            }
        }
        String off = apart == 0 ? "" : String.format(Locale.ROOT, compared == 0
                ? "; JUMP CHECK OFF: every picture more than %d ticks from the one before"
                : "; no jump check for %2$d of %3$d moves (pictures more than %1$d ticks apart)", TAKE_GAP, apart, apart + compared);
        lines.add(String.format(Locale.ROOT, "summary: %d moments of %d insect(s) checked; %d part(s) inside blocks (worst %s); %d jump(s) (worst %s)%s",
                pictures, tracks.size(), inside, worstInside, jumps, worstJump, off));
        return lines;
    }

    /**
     * A move as blocks a tick: the move between two pictures over the game ticks between them (the in-between time
     * counted), or no move at all (NaN) for pictures not of one take: the same moment or an earlier one, or more than
     * {@link #TAKE_GAP} ticks on.
     */
    static double aTick(double moved, double ticksApart) {
        return ticksApart < 0.01 || ticksApart > TAKE_GAP ? Double.NaN : moved / ticksApart;
    }

    /** How far the part moved between two pictures: the most any of its corners moved, relative to the insect. */
    private static double moved(Sample a, Sample b) {
        double most = 0.0;
        for (int i = 0; i < Math.min(a.corners().size(), b.corners().size()); i++) {
            most = Math.max(most, a.corners().get(i).distanceTo(b.corners().get(i)));
        }
        return most;
    }

    /**
     * Whether the part's move into picture {@code i} is a jump: far enough, far more than its moves round it, and
     * either more than its big moves in the whole scene or so far over the moves round it that those are no excuse.
     */
    static boolean jump(double[] moves, int i, double big) {
        return jump(moves, i, big, moves[i]);
    }

    /** The same, {@code far} being how far the part went for the least a jump must be ({@link #JUMP_MIN}). */
    static boolean jump(double[] moves, int i, double big, double far) {
        double move = moves[i];
        if (Double.isNaN(move) || !(far > JUMP_MIN)) {
            return false;
        }
        double usual = usual(moves, i);
        return move > JUMP_TIMES * usual && (move > ABOVE_BIG * big || move > SURE_TIMES * usual);
    }

    /** The move 9 in 10 of the part's moves are below (for a short film: its second biggest). */
    static double bigMoves(double[] moves) {
        if (moves.length < 3) {
            return 0.0;
        }
        double[] sorted = java.util.Arrays.stream(moves, 1, moves.length).filter(m -> !Double.isNaN(m)).sorted().toArray();
        if (sorted.length < 2) {
            return 0.0;
        }
        return sorted[Math.min(sorted.length - 2, (int) (sorted.length * 0.9))];
    }

    /** The middle of the part's moves in the two pictures before and the two after this one (not counting it). */
    static double usual(double[] moves, int i) {
        List<Double> round = new ArrayList<>();
        for (int j = i - 2; j <= i + 2; j++) {
            if (j != i && j >= 1 && j < moves.length && !Double.isNaN(moves[j])) {
                round.add(moves[j]);
            }
        }
        if (round.isEmpty()) {
            return Double.MAX_VALUE;            // a single move: nothing to compare it with
        }
        round.sort(null);
        return round.size() % 2 == 1 ? round.get(round.size() / 2)
                : (round.get(round.size() / 2 - 1) + round.get(round.size() / 2)) / 2.0;
    }
}
