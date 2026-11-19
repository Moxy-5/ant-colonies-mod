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
 * was drawn and how it was moving, its clips, and every bone's place as the game drew it. The first line holds what
 * stays the same (each bone's parent and boxes, the blocks round it). It judges nothing: {@code tools/body_course.py}
 * reads the file and does the checks (the game is the one truth; Java records, Python judges). Development only, like
 * the rest of the camera.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class BodyRecorder {
    private static final Logger LOG = LogUtils.getLogger();
    /** How far round the walker the blocks are written down when recording starts (blocks, each way; up and down less). */
    private static final int AROUND = 16, BELOW = 5, ABOVE = 10;

    private static Path file;
    private static int perTick;
    private static final List<String> lines = new ArrayList<>();
    private static final Set<String> moments = new HashSet<>();
    private static final Set<BakedGeoModel> tracked = new HashSet<>();
    private static boolean header;

    private BodyRecorder() {
    }

    /** The "record on n" step: from now each moment the walker is drawn (at most n a tick) goes into this file. */
    static void start(Path into, int samplesPerTick) {
        stop();
        file = into;
        perTick = Math.max(1, samplesPerTick);
        header = false;
        LOG.info("Film: recording the walker's body into {} ({} a tick)", into, perTick);
    }

    static boolean recording() {
        return file != null;
    }

    /** The "record off" step (or the scene's end): the file written. */
    static void stop() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    out.write(line);
                    out.write('\n');
                }
            }
            LOG.info("Film: {} body moments written to {}", Math.max(0, lines.size() - 1), file);
        } catch (IOException e) {
            LOG.warn("Film: cannot write {}", file, e);
        }
        file = null;
        lines.clear();
        moments.clear();
    }

    @SubscribeEvent
    public static void onDrawn(GeoRenderEvent.Entity.Post event) {
        if (file == null || !FilmDirector.isWalker(event.getEntity())) {
            return;
        }
        Entity entity = event.getEntity();
        BakedGeoModel model = event.getModel();
        if (tracked.add(model)) {
            model.topLevelBones().forEach(BodyRecorder::track);
            return;                             // the bones' matrices are filled from the next frame on
        }
        float partial = event.getPartialTick();
        String moment = entity.tickCount + "/" + (int) Math.min(perTick - 1, partial * perTick);
        if (!moments.add(moment)) {
            return;
        }
        List<GeoBone> bones = new ArrayList<>();
        model.topLevelBones().forEach(bone -> collect(bone, bones));
        if (!header) {
            header = true;
            lines.add(header(entity, bones));
        }
        lines.add(moment(entity, partial, bones));
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
    private static String header(Entity entity, List<GeoBone> bones) {
        StringBuilder s = new StringBuilder("{\"header\":1,\"entity\":\"")
                .append(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())).append('"');
        s.append(",\"width\":").append(num(entity.getBbWidth())).append(",\"height\":").append(num(entity.getBbHeight()));
        s.append(",\"scale\":").append(num(entity instanceof LivingEntity living ? living.getScale() : 1.0F));
        s.append(",\"bones\":[");
        for (int i = 0; i < bones.size(); i++) {
            GeoBone bone = bones.get(i);
            s.append(i > 0 ? "," : "").append("{\"name\":\"").append(bone.getName()).append('"');
            if (bone.getParent() != null) {
                s.append(",\"parent\":\"").append(bone.getParent().getName()).append('"');
            }
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
        s.append("],\"blocks\":[");
        var level = Minecraft.getInstance().level;
        BlockPos at = entity.blockPosition();
        boolean first = true;
        StringBuilder water = new StringBuilder();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-AROUND, -BELOW, -AROUND), at.offset(AROUND, ABOVE, AROUND))) {
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
        s.append("],\"water\":[").append(water).append("]}");
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
     * the point's place relative to where the animal was drawn), or 0 for a bone not drawn.
     */
    private static String moment(Entity entity, float partial, List<GeoBone> bones) {
        Vec3 at = entity.getPosition(partial);
        Vec3 v = entity.getDeltaMovement();
        float bodyYaw = entity instanceof LivingEntity living ? living.getPreciseBodyRotation(partial) : entity.getYRot();
        StringBuilder s = new StringBuilder("{\"t\":").append(entity.tickCount).append(",\"pt\":").append(num(partial));
        s.append(",\"pos\":[").append(num(at.x)).append(',').append(num(at.y)).append(',').append(num(at.z)).append(']');
        s.append(",\"yaw\":").append(num(bodyYaw)).append(",\"pitch\":").append(num(entity.getViewXRot(partial)));
        s.append(",\"v\":[").append(num(v.x)).append(',').append(num(v.y)).append(',').append(num(v.z)).append(']');
        s.append(",\"ground\":").append(entity.onGround() ? 1 : 0).append(",\"water\":").append(entity.isInWater() ? 1 : 0);
        s.append(",\"fluid\":").append(num(entity.getFluidHeight(FluidTags.WATER)));
        s.append(",\"clips\":{");
        if (entity instanceof GeoAnimatable animatable) {
            boolean first = true;
            var manager = animatable.getAnimatableInstanceCache().getManagerForId(entity.getId());
            for (Map.Entry<String, AnimationController<GeoAnimatable>> e : manager.getAnimationControllers().entrySet()) {
                AnimationController<?> controller = e.getValue();
                String clip = controller.getCurrentAnimation() == null ? "-" : controller.getCurrentAnimation().animation().name();
                s.append(first ? "" : ",").append('"').append(e.getKey()).append("\":\"").append(clip).append(' ')
                        .append(controller.getAnimationState().name().toLowerCase(Locale.ROOT)).append('"');
                first = false;
            }
        }
        s.append("},\"b\":[");
        for (int i = 0; i < bones.size(); i++) {
            GeoBone bone = bones.get(i);
            s.append(i > 0 ? "," : "");
            if (bone.isHidden()) {
                s.append('0');
                continue;
            }
            PoseStack pose = new PoseStack();
            // GeckoLib's "local space" matrix has the do-nothing matrix added to it, taken back off (as FilmChecks)
            pose.last().pose().set(new Matrix4f(bone.getLocalSpaceMatrix()).sub(new Matrix4f()));
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
