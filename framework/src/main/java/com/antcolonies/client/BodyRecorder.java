package com.antcolonies.client;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.body.SteadyController;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.util.RenderUtil;

/**
 * The measuring course's recorder (the Animal Maker Engine, phase 1 step 1, 2026-11-19; {@code docs/ANIMAL-MAKER-ENGINE.md}):
 * while the camera's {@code record on} step is in force, every frame the walker is drawn (any GeckoLib animal or insect,
 * up to {@code n} moments a game tick) goes into {@code screenshots/<scene>/body.jsonl.gz}, one line a moment: where it
 * was drawn and how it was moving, its clips (with each clip's play speed and how far into it), what its mind was doing
 * on the game's side ({@link com.antcolonies.debug.FilmFacts}), and every bone's place as the game drew it. The first
 * line holds what stays the same (each bone's parent, pivot and boxes, the blocks round it). It judges nothing:
 * {@code tools/body_course.py} and {@code tools/pair_report.py} read the files and do the checks (the game is the one
 * truth; Java records, Python judges). Development only, like the rest of the camera.
 * <p>
 * {@code record on n all} (the Motion Studio, phase 0, 2026-11-25, {@code docs/plans/motion-studio.md}): every animal
 * drawn is recorded, each into its own file beside the walker's, {@code body_<its name or kind and number>.jsonl.gz}
 * (a lion and the buffalo it holds, the herd round them). Lines are written as they come, so a long hunt never piles up
 * in memory. An animal is recorded only while it is drawn (in the camera's view).
 * <p>
 * The blocks are written wherever the animal goes (code review R12-48, 2026-11-27): the first line has those round
 * its first place, and each time it has gone half that far a line {@code {"more":1,"blocks":[...],"water":[...]}}
 * adds the ones round its new place not yet written (a lioness first drawn 40 blocks from the kill had no ground
 * under the hold). An animal drawn by its holder (a fish in a jaw) is written where it was really drawn, in its
 * holder's frame, its lines marked {@code "held":1} (R12-45).
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class BodyRecorder {
    private static final Logger LOG = LogUtils.getLogger();
    /** How far round the animal the blocks are written down (blocks, each way; up and down less) ... */
    private static final int AROUND = 16, BELOW = 5, ABOVE = 10;
    /** ... and how far it goes from where they were last written before they are written again (sideways; up or down). */
    private static final int AGAIN = AROUND / 2, AGAIN_UP = 3;

    /** One animal's file: open while recording, its moments written so far. */
    private static final class Track {
        final Path file;
        final Writer out;
        final Set<String> moments = new HashSet<>();
        /** The blocks written so far, and the middle of the last writing. */
        final Set<Long> ground = new HashSet<>();
        BlockPos groundAt;
        int lines;

        Track(Path file) throws IOException {
            this.file = file;
            Files.createDirectories(file.getParent());
            out = new java.io.BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file), 1 << 16),
                    StandardCharsets.UTF_8), 1 << 16);
        }
    }

    /** The scene's folder while recording (null when not), how many moments a tick, and whether every animal is. */
    private static Path folder;
    private static int perTick;
    private static boolean all;
    private static final Map<Integer, Track> tracks = new java.util.HashMap<>();
    private static final Set<String> fileNames = new HashSet<>();
    private static final Set<BakedGeoModel> tracked = new HashSet<>();

    private BodyRecorder() {
    }

    /**
     * The "record on n [all]" step: from now each moment the walker is drawn (at most n a tick) goes into
     * {@code body.jsonl.gz} in this folder; with {@code all}, every other animal drawn into a file of its own too.
     */
    static void start(Path into, int samplesPerTick, boolean everyAnimal) {
        stop();
        folder = into;
        perTick = Math.max(1, samplesPerTick);
        all = everyAnimal;
        LOG.info("Film: recording {} into {} ({} a tick)", all ? "every animal drawn" : "the walker's body", into, perTick);
    }

    static boolean recording() {
        return folder != null;
    }

    /** The "record off" step (or the scene's end): every file closed. */
    static void stop() {
        if (folder == null) {
            return;
        }
        for (Track t : tracks.values()) {
            try {
                t.out.close();
                LOG.info("Film: {} body moments written to {}", Math.max(0, t.lines - 1), t.file);
            } catch (IOException e) {
                LOG.warn("Film: cannot write {}", t.file, e);
            }
        }
        tracks.clear();
        fileNames.clear();
        folder = null;
    }

    @SubscribeEvent
    public static void onDrawn(GeoRenderEvent.Entity.Post event) {
        if (folder == null) {
            return;
        }
        Entity entity = event.getEntity();
        boolean walker = FilmDirector.isWalker(entity);
        if (!walker && !all) {
            return;
        }
        BakedGeoModel model = event.getModel();
        if (tracked.add(model)) {
            model.topLevelBones().forEach(BodyRecorder::track);
            return;                             // the bones' matrices are filled from the next frame on
        }
        Track track = tracks.get(entity.getId());
        if (track == null) {
            try {
                track = new Track(folder.resolve(fileName(entity, walker)));
            } catch (IOException e) {
                LOG.warn("Film: cannot record {}", entity, e);
                return;
            }
            tracks.put(entity.getId(), track);
        }
        float partial = event.getPartialTick();
        // the n-th part of a tick the moment falls in, counted from the game's start so that a tick's end and the next
        // tick's start are one moment (the camera's "frames n" draws an animal at 1/n, 2/n ... 1, and between two takes
        // holds it at the last one, which is the next tick's 0: that frame took the next take's first place)
        String moment = Long.toString((long) Math.ceil((entity.tickCount + (double) partial) * perTick - 1.0e-4));
        if (!track.moments.add(moment)) {
            return;
        }
        List<GeoBone> bones = new ArrayList<>();
        model.topLevelBones().forEach(bone -> collect(bone, bones));
        try {
            if (track.lines == 0) {
                track.out.write(header(entity, bones, track));
                track.out.write('\n');
                track.lines++;
            }
            BlockPos at = entity.blockPosition();
            if (Math.abs(at.getX() - track.groundAt.getX()) > AGAIN || Math.abs(at.getZ() - track.groundAt.getZ()) > AGAIN
                    || Math.abs(at.getY() - track.groundAt.getY()) > AGAIN_UP) {
                track.out.write("{\"more\":1," + ground(at, track) + "}");
                track.out.write('\n');
            }
            track.out.write(moment(entity, partial, bones, BoneMeshRenderer.heldFrame(), BoneMeshRenderer.heldAt()));
            track.out.write('\n');
            track.lines++;
        } catch (IOException e) {
            LOG.warn("Film: cannot write {}", track.file, e);
        }
    }

    /** The walker's file is body.jsonl.gz (the course reads it); any other animal's body_<its name, or kind and number>. */
    private static String fileName(Entity entity, boolean walker) {
        if (walker) {
            fileNames.add("body");
            return "body.jsonl.gz";
        }
        String label = entity.getCustomName() != null
                ? entity.getCustomName().getString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_")
                : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath() + "_" + entity.getId();
        String name = "body_" + label;
        if (!fileNames.add(name)) {
            name = name + "_" + entity.getId();         // two of one name
            fileNames.add(name);
        }
        return name + ".jsonl.gz";
    }

    private static void track(GeoBone bone) {
        bone.setTrackingMatrices(true);
        bone.getChildBones().forEach(BodyRecorder::track);
    }

    private static void collect(GeoBone bone, List<GeoBone> out) {
        out.add(bone);
        bone.getChildBones().forEach(child -> collect(child, out));
    }

    /**
     * The first line: the animal's kind, each bone (its parent, and its boxes as 8 corners in the bone's own frame:
     * each box's own turn already in), and the blocks round it (their collision boxes and water heights).
     */
    private static String header(Entity entity, List<GeoBone> bones, Track track) {
        StringBuilder s = new StringBuilder("{\"header\":1,\"entity\":\"")
                .append(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())).append('"');
        s.append(",\"id\":").append(entity.getId()).append(",\"uuid\":\"").append(entity.getUUID()).append('"');
        if (entity.getCustomName() != null) {
            s.append(",\"name\":\"").append(entity.getCustomName().getString().replace("\"", "'")).append('"');
        }
        s.append(",\"width\":").append(num(entity.getBbWidth())).append(",\"height\":").append(num(entity.getBbHeight()));
        s.append(",\"scale\":").append(num(entity instanceof LivingEntity living ? living.getScale() : 1.0F));
        s.append(",\"bones\":[");
        for (int i = 0; i < bones.size(); i++) {
            GeoBone bone = bones.get(i);
            s.append(i > 0 ? "," : "").append("{\"name\":\"").append(bone.getName()).append('"');
            if (bone.getParent() != null) {
                s.append(",\"parent\":\"").append(bone.getParent().getName()).append('"');
            }
            // its pivot in the bone's own frame (the frame its boxes' corners are in): an empty bone's place (a jaw's grip)
            s.append(",\"pivot\":[").append(num(bone.getPivotX() / 16.0)).append(',').append(num(bone.getPivotY() / 16.0))
                    .append(',').append(num(bone.getPivotZ() / 16.0)).append(']');
            s.append(",\"cubes\":[");
            boolean first = true;
            for (GeoCube cube : bone.getCubes()) {
                float[] box = bounds(cube);
                if (box == null) {
                    continue;
                }
                PoseStack pose = new PoseStack();
                RenderUtil.translateToPivotPoint(pose, cube);
                RenderUtil.rotateMatrixAroundCube(pose, cube);
                RenderUtil.translateAwayFromPivotPoint(pose, cube);
                s.append(first ? "" : ",").append('[');
                first = false;
                for (int c = 0; c < 8; c++) {
                    Vector4f p = pose.last().pose().transform(new Vector4f(
                            box[(c & 1) == 0 ? 0 : 3], box[(c & 2) == 0 ? 1 : 4], box[(c & 4) == 0 ? 2 : 5], 1.0F));
                    s.append(c > 0 ? "," : "").append(num(p.x())).append(',').append(num(p.y())).append(',').append(num(p.z()));
                }
                s.append(']');
            }
            s.append("]}");
        }
        s.append("],").append(ground(entity.blockPosition(), track)).append('}');
        return s.toString();
    }

    /**
     * The blocks round this place not yet written for this animal, as {@code "blocks":[...],"water":[...]}: their
     * collision boxes and water heights.
     */
    private static String ground(BlockPos at, Track track) {
        track.groundAt = at.immutable();
        StringBuilder s = new StringBuilder("\"blocks\":[");
        var level = Minecraft.getInstance().level;
        boolean first = true;
        StringBuilder water = new StringBuilder();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-AROUND, -BELOW, -AROUND), at.offset(AROUND, ABOVE, AROUND))) {
            if (!track.ground.add(pos.asLong())) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            float fluid = state.getFluidState().is(FluidTags.WATER) ? state.getFluidState().getHeight(level, pos) : 0.0F;
            if (fluid > 0.0F) {
                water.append(water.isEmpty() ? "" : ",").append('[').append(pos.getX()).append(',').append(pos.getY())
                        .append(',').append(pos.getZ()).append(',').append(num(fluid)).append(']');
            }
            if (state.isAir()) {
                continue;
            }
            List<AABB> boxes = state.getCollisionShape(level, pos).toAabbs();
            if (boxes.isEmpty()) {
                continue;
            }
            s.append(first ? "" : ",").append('[').append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
            first = false;
            for (AABB box : boxes) {
                s.append(",[").append(num(box.minX)).append(',').append(num(box.minY)).append(',').append(num(box.minZ)).append(',')
                        .append(num(box.maxX)).append(',').append(num(box.maxY)).append(',').append(num(box.maxZ)).append(']');
            }
            s.append(']');
        }
        s.append("],\"water\":[").append(water).append(']');
        return s.toString();
    }

    /** A box's corners as its quads have them (lowest x y z, then highest), or null for a box with nothing drawn. */
    private static float[] bounds(GeoCube cube) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (var quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (var vertex : quad.vertices()) {
                box[0] = Math.min(box[0], vertex.position().x);
                box[1] = Math.min(box[1], vertex.position().y);
                box[2] = Math.min(box[2], vertex.position().z);
                box[3] = Math.max(box[3], vertex.position().x);
                box[4] = Math.max(box[4], vertex.position().y);
                box[5] = Math.max(box[5], vertex.position().z);
            }
        }
        return box[0] > box[3] ? null : box;
    }

    /**
     * One moment: the tick and in-between time, where it was drawn, its body's turn, how it moved, on the ground or in
     * water, its clips, then each bone's matrix (12 numbers, the rows of a 3x4: a point in the bone's frame times it is
     * the point's place relative to where the animal was drawn), or 0 for a bone not drawn. An animal drawn by its
     * holder ({@link BoneMeshRenderer#heldFrame}) is written from where the holder was drawn, in the holder's frame.
     */
    private static String moment(Entity entity, float partial, List<GeoBone> bones, Matrix4f heldFrame, Vec3 heldAt) {
        Vec3 at = heldAt != null ? heldAt : entity.getPosition(partial);
        Vec3 v = entity.getDeltaMovement();
        float bodyYaw = entity instanceof LivingEntity living ? living.getPreciseBodyRotation(partial) : entity.getYRot();
        StringBuilder s = new StringBuilder("{\"t\":").append(entity.tickCount).append(",\"pt\":").append(num(partial));
        if (heldAt != null) {
            s.append(",\"held\":1");
        }
        s.append(",\"pos\":[").append(num(at.x)).append(',').append(num(at.y)).append(',').append(num(at.z)).append(']');
        s.append(",\"yaw\":").append(num(bodyYaw)).append(",\"pitch\":").append(num(entity.getViewXRot(partial)));
        s.append(",\"v\":[").append(num(v.x)).append(',').append(num(v.y)).append(',').append(num(v.z)).append(']');
        s.append(",\"ground\":").append(entity.onGround() ? 1 : 0).append(",\"water\":").append(entity.isInWater() ? 1 : 0);
        s.append(",\"fluid\":").append(num(entity.getFluidHeight(FluidTags.WATER)));
        s.append(",\"clips\":{");
        // each clip player's play speed and how far into its clip it is (clip ticks; only a SteadyController knows it)
        StringBuilder play = new StringBuilder();
        if (entity instanceof GeoAnimatable animatable) {
            boolean first = true;
            var manager = animatable.getAnimatableInstanceCache().getManagerForId(entity.getId());
            for (Map.Entry<String, AnimationController<GeoAnimatable>> e : manager.getAnimationControllers().entrySet()) {
                AnimationController<?> controller = e.getValue();
                String clip = controller.getCurrentAnimation() == null ? "-" : controller.getCurrentAnimation().animation().name();
                s.append(first ? "" : ",").append('"').append(e.getKey()).append("\":\"").append(clip).append(' ')
                        .append(controller.getAnimationState().name().toLowerCase(Locale.ROOT)).append('"');
                play.append(first ? "" : ",").append('"').append(e.getKey()).append("\":[").append(num(controller.getAnimationSpeed()))
                        .append(',').append(controller instanceof SteadyController<?> steady ? num(steady.clipTicks()) : "null").append(']');
                first = false;
            }
        }
        s.append("},\"play\":{").append(play).append('}');
        Map<String, Object> facts = FilmFactsBoard.of(entity.getUUID());
        if (!facts.isEmpty()) {
            s.append(",\"facts\":").append(FilmFactsBoard.json(facts)).append(",\"ft\":").append(FilmFactsBoard.tick());
        }
        s.append(",\"b\":[");
        for (int i = 0; i < bones.size(); i++) {
            GeoBone bone = bones.get(i);
            s.append(i > 0 ? "," : "");
            if (bone.isHidden()) {
                s.append('0');
                continue;
            }
            PoseStack pose = new PoseStack();
            // GeckoLib's "local space" matrix has the do-nothing matrix added to it, taken back off (as FilmChecks)
            Matrix4f local = new Matrix4f(bone.getLocalSpaceMatrix()).sub(new Matrix4f());
            pose.last().pose().set(heldFrame == null ? local : new Matrix4f(heldFrame).mul(local));
            RenderUtil.translateAwayFromPivotPoint(pose, bone);
            Matrix4f m = pose.last().pose();
            s.append('[').append(num(m.m00())).append(',').append(num(m.m10())).append(',').append(num(m.m20())).append(',').append(num(m.m30()))
                    .append(',').append(num(m.m01())).append(',').append(num(m.m11())).append(',').append(num(m.m21())).append(',').append(num(m.m31()))
                    .append(',').append(num(m.m02())).append(',').append(num(m.m12())).append(',').append(num(m.m22())).append(',').append(num(m.m32()))
                    .append(']');
        }
        s.append("]}");
        return s.toString();
    }

    private static String num(double value) {
        String s = String.format(Locale.ROOT, "%.5f", value);
        int end = s.length();
        while (end > 1 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (s.charAt(end - 1) == '.') {
            end--;
        }
        s = s.substring(0, end);
        return s.equals("-0") ? "0" : s;
    }
}
