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
 * <li><b>inside</b>: a body part drawn inside a solid block (deeper than {@link #INSIDE} blocks), with the block;</li>
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

    /** One insect's part in one picture: its box corners relative to the insect, and how deep it was in a block. */
    private record Sample(String picture, int tick, List<Vec3> corners, double depth, String block, Vec3 at) {
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
        record(ant, model.getBakedModel(model.getModelResource(ant)), partial, picture);
    }

    /**
     * The same for any animal drawn with GeckoLib (the Motion Studio, phase 0, 2026-11-25: until then only the insects
     * were checked, the land and river animals filmed unchecked): called right after it is drawn, at the in-between
     * time it was drawn at.
     */
    void record(Entity animal, BakedGeoModel baked, float partial, String picture) {
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
        Vec3 origin = animal.getPosition(partial);
        Map<String, List<Sample>> parts = tracks.computeIfAbsent(animal.getId(), id -> new LinkedHashMap<>());
        if (!baked.topLevelBones().isEmpty()) {
            // a model pixel as drawn: the root bone's scale (its matrix's first column) over 16
            Matrix4f root = new Matrix4f(baked.topLevelBones().get(0).getLocalSpaceMatrix()).sub(new Matrix4f());   // (GeckoLib adds the do-nothing matrix)
            double px = Math.sqrt(root.m00() * root.m00() + root.m01() * root.m01() + root.m02() * root.m02()) / 16.0;
            limits.put(animal.getId(), Math.max(INSIDE, Double.isFinite(px) ? px : 0.0));
        }
        for (GeoBone bone : baked.topLevelBones()) {
            sample(bone, origin, picture, animal.tickCount, parts);
        }
    }

    private static void trackMatrices(GeoBone bone) {
        bone.setTrackingMatrices(true);
        bone.getChildBones().forEach(FilmChecks::trackMatrices);
    }

    private void sample(GeoBone bone, Vec3 origin, String picture, int tick, Map<String, List<Sample>> parts) {
        if (!bone.isHidden() && !bone.getCubes().isEmpty()) {
            List<Vec3> corners = new ArrayList<>();
            double depth = 0.0;
            String block = null;
            Vec3 at = null;
            for (GeoCube cube : bone.getCubes()) {
                PoseStack pose = new PoseStack();
                // GeckoLib's own "local space" matrix has the do-nothing matrix added to it (its translateMatrix adds a
                // whole matrix, not just the shift), so it is taken back off
                pose.last().pose().set(new Matrix4f(bone.getLocalSpaceMatrix()).sub(new Matrix4f()));
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
                    .add(new Sample(picture, tick, corners, depth, block, at));
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                sample(child, origin, picture, tick, parts);
            }
        }
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
     * The scene's findings, one line each, then a summary line; framesPerTick is how many pictures were taken each game
     * tick (a jump's size is counted per tick).
     */
    List<String> report(int framesPerTick) {
        List<String> lines = new ArrayList<>();
        int inside = 0, jumps = 0;
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
                double[] moves = new double[samples.size()];
                for (int i = 1; i < samples.size(); i++) {
                    int gap = samples.get(i).tick() - samples.get(i - 1).tick();
                    moves[i] = gap < 0 || gap > TAKE_GAP ? Double.NaN : moved(samples.get(i - 1), samples.get(i)) * framesPerTick;
                }
                double big = bigMoves(moves);
                for (int i = 1; i < samples.size(); i++) {
                    double usual = usual(moves, i);
                    if (jump(moves, i, big)) {
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
        lines.add(String.format(Locale.ROOT, "summary: %d moments of %d insect(s) checked; %d part(s) inside blocks (worst %s); %d jump(s) (worst %s)",
                pictures, tracks.size(), inside, worstInside, jumps, worstJump));
        return lines;
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
        double move = moves[i];
        if (Double.isNaN(move) || move <= JUMP_MIN) {
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
