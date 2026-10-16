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
 * of each filmed insect was really drawn (the corners of every box of the model, in the world), so that Claude only has
 * to look at the pictures where something went wrong:
 * <ul>
 * <li><b>inside</b>: a body part drawn inside a solid block (deeper than {@link #INSIDE} blocks), with the block;</li>
 * <li><b>jump</b>: a body part that moves far more from one picture to the next than just before and after it (a pop:
 * a clip changing at the wrong moment, a bend piling up, a turn snapping), measured against the insect itself, so its
 * walking about does not count.</li>
 * </ul>
 * {@code FilmDirector} calls {@link #record} for each insect drawn in a picture and {@link #report} at the end of each
 * scene, which writes {@code checks.txt} beside the scene's pictures.
 */
final class FilmChecks {
    /** How deep in a block a part may be drawn before it counts (blocks; a foot pressed onto the floor is fine). */
    static final double INSIDE = 0.01;
    /** A jump must be at least this many blocks a tick (the worker is about 0.3 long) ... */
    static final double JUMP_MIN = 0.03;
    /** ... and this many times the usual move of that part in the pictures round it. */
    static final double JUMP_TIMES = 3.0;
    /** How many points along each side of a box are tested for being inside a block (3: corners, edge middles, ...). */
    private static final int GRID = 3;

    /** One insect's part in one picture: its box corners relative to the insect, and how deep it was in a block. */
    private record Sample(String picture, int tick, List<Vec3> corners, double depth, String block, Vec3 at) {
    }

    /** Each insect's parts in picture order (insect id, then bone name), and the moment last recorded for each insect. */
    private final Map<Integer, Map<String, List<Sample>>> tracks = new LinkedHashMap<>();
    private final Map<Integer, String> lastMoment = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    private static final Set<GeoModel<?>> tracked = new HashSet<>();
    private int pictures;

    /**
     * Records where this insect's parts were drawn, just after it was drawn for {@code picture} at that in-between time.
     * The bones are shared by every insect of a model, so this has to run right after each one is drawn.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void record(AbstractAntEntity ant, GeoModel model, float partial, String picture) {
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(ant));
        if (tracked.add(model)) {
            baked.topLevelBones().forEach(FilmChecks::trackMatrices);
            return;                             // the matrices are filled from the next frame on
        }
        String moment = ant.tickCount + "/" + partial;
        if (moment.equals(lastMoment.put(ant.getId(), moment))) {
            return;                             // the same moment seen from another camera
        }
        pictures++;
        names.put(ant.getId(), ant.getType().toShortString() + (ant.getCustomName() == null ? "" : " " + ant.getCustomName().getString()));
        Vec3 origin = ant.getPosition(partial);
        Map<String, List<Sample>> parts = tracks.computeIfAbsent(ant.getId(), id -> new LinkedHashMap<>());
        for (GeoBone bone : baked.topLevelBones()) {
            sample(bone, origin, picture, ant.tickCount, parts);
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
            for (var part : insect.getValue().entrySet()) {
                List<Sample> samples = part.getValue();
                for (Sample s : samples) {
                    if (s.depth() > INSIDE) {
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
                    moves[i] = moved(samples.get(i - 1), samples.get(i)) * framesPerTick;
                }
                for (int i = 1; i < samples.size(); i++) {
                    double usual = usual(moves, i);
                    if (moves[i] > JUMP_MIN && moves[i] > JUMP_TIMES * usual) {
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

    /** The middle of the part's moves in the two pictures before and the two after this one (not counting it). */
    private static double usual(double[] moves, int i) {
        List<Double> round = new ArrayList<>();
        for (int j = i - 2; j <= i + 2; j++) {
            if (j != i && j >= 1 && j < moves.length) {
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
