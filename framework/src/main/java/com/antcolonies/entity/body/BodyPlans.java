package com.antcolonies.entity.body;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;

/**
 * Body plans (the animation engine upgrade, M3, 2026-10-18): what each model's parts are, read from
 * {@code assets/<mod>/body_plans/<model>.json} (the same files the animation tools read; {@code tools/body_plan.py}
 * explains them). Each species mod signs up its own folder at start ({@link #signUp}; the split, step S6: the framework
 * cannot read another mod's files by itself). The game asks a plan for a part by its role ("head", "carry", the spine's joints, how it climbs) and gets the
 * model's own bone name, so a model with other bone names (the user's Blockbench worker, M2) needs only its plan's
 * rename table changed. Today every bone name is its role name.
 */
public final class BodyPlans {
    private static final Logger LOG = LogUtils.getLogger();

    /**
     * One model's plan: role to bone, the head player's bones, the spine (the pieces of the body behind the thorax, front
     * to back) and how the body climbs.
     */
    public record Plan(String model, Map<String, String> bones, Set<String> headPlayer, List<Piece> spine, Climb climb) {
        /** The model's bone for a role (the role itself if the plan does not name it). */
        public String bone(String role) {
            return bones.getOrDefault(role, role);
        }

        /** The joints of the spine's first piece (the ant's waist), front to back. */
        public List<String> waist() {
            return spine.isEmpty() ? List.of() : spine.get(0).joints();
        }

        /** The share of the first piece's bend each of its joints takes. */
        public float[] waistShare() {
            return spine.isEmpty() ? new float[0] : spine.get(0).shares();
        }
    }

    /**
     * One piece of the spine (climbing done once for every body, 2026-10-18): the joints that bend it and the share of
     * its bend each takes, where it hangs off the piece in front (the hinge) and where it lies behind the body's middle
     * (model units), and how far it bends at most up or down and to the side (degrees). maxUp and maxDown (2026-10-24,
     * the Matabele queen's long gaster): how far it may rise (at an inside corner, a positive bend) and droop (over an
     * outside edge) when the two differ; a plan without them bends maxPitch both ways.
     */
    public record Piece(List<String> joints, float[] shares, double hinge, double from, double to, float maxPitch,
            float maxYaw, float maxUp, float maxDown) {
        public Piece(List<String> joints, float[] shares, double hinge, double from, double to, float maxPitch,
                float maxYaw) {
            this(joints, shares, hinge, from, to, maxPitch, maxYaw, maxPitch, maxPitch);
        }
    }

    /**
     * How a body climbs (model units; measured from its model by {@code tools/body_plan.py measure}): how far its front
     * feet reach ahead of its middle and its hind feet behind, how far its front and rear ends reach, the tightest curve
     * its path takes, and how far its drawn body is lifted up a wall and pushed in toward it (before the drawn scale;
     * NaN when its plan has none, then worked out from its size, see {@link #liftFor} and {@link #intoWallFor}).
     */
    public record Climb(double frontFeet, double hindFeet, double frontEnd, double rearEnd, double tightestCurve,
            double lift, double intoWall) {
        /**
         * The lift up a wall: the drawn body's farther end, less the half of its box the climbing pose already raises it
         * by, plus a margin; worked out so the worker's comes to exactly the 0.25 the user approved in-game (2026-10-16).
         */
        public double liftFor(float boxHeight, float scale) {
            return Double.isNaN(lift) ? Math.max(frontEnd, rearEnd) / 16.0 - boxHeight / 2.0 / scale + LIFT_MARGIN : lift;
        }

        /** The push in toward a wall: the half of its box between it and the wall, less a margin (the worker's 0.17). */
        public double intoWallFor(float boxWidth, float scale) {
            return Double.isNaN(intoWall) ? boxWidth / 2.0 / scale - INTO_WALL_MARGIN : intoWall;
        }
    }

    private static final double LIFT_MARGIN = 0.25 - 5.57 / 16.0 + 0.1 / 0.65;
    private static final double INTO_WALL_MARGIN = 0.15 / 0.65 - 0.17;

    /**
     * The harvester worker's waist and climbing (approved by the user in-game 2026-10-16), for a model with no plan (the
     * same numbers its plan holds).
     */
    public static final Piece WORKER_WAIST = new Piece(List.of("petiole", "postpetiole", "abdomen"),
            new float[] {0.40F, 0.35F, 0.25F}, 0.64, 2.4, 5.2, 60.0F, 25.0F);
    public static final Climb WORKER_CLIMB = new Climb(3.6, 2.85, 5.57, 5.57, 3.0, 0.25, 0.17);

    private static final Map<String, Plan> PLANS = new HashMap<>();
    /** Every bone any plan names, those the head player owns, and those that hold a load. */
    private static final Set<String> KNOWN = new HashSet<>();
    private static final Set<String> HEAD_PLAYER = new HashSet<>();
    private static final Set<String> HOLD = new HashSet<>();

    /** The folders signed up so far ("/assets/antcolonies/body_plans/"), each read once. */
    private static final Set<String> FOLDERS = new HashSet<>();

    /**
     * Reads the plans of a species mod's models (the split, step S6): {@code folder} is the mod's
     * {@code /assets/<mod>/body_plans/} with its {@code index.json}, read through {@code anchor}, a class of that mod.
     * Signing the same folder up again does nothing.
     */
    public static synchronized void signUp(Class<?> anchor, String folder) {
        if (!FOLDERS.add(folder)) {
            return;
        }
        for (String model : readIndex(anchor, folder)) {
            Plan plan = read(anchor, folder, model);
            if (plan != null) {
                PLANS.put(model, plan);
                KNOWN.addAll(plan.bones().values());
                HEAD_PLAYER.addAll(plan.headPlayer());
            }
        }
        LOG.info("Body plans: read {} from {} ({})", PLANS.size(), folder, PLANS.keySet());
    }

    private BodyPlans() {
    }

    /** A model's plan (by its geo file's name, e.g. worker_ant), or null if it has none. */
    @Nullable
    public static Plan of(String model) {
        return PLANS.get(model);
    }

    /** The plan of a geo model by its file (geo/entity/worker_ant.geo.json), or null if it has none. */
    @Nullable
    public static Plan ofGeo(ResourceLocation geo) {
        String path = geo.getPath();
        return of(path.substring(path.lastIndexOf('/') + 1).replace(".geo.json", ""));
    }

    /**
     * Whether the head's player owns a bone: the plans' head players (in any model; no plan gives one name two jobs).
     * A bone no plan names falls back to the placeholder models' rule (head, mandible, antenna, carry).
     */
    public static boolean isHeadPlayerBone(String bone) {
        if (KNOWN.contains(bone)) {
            return HEAD_PLAYER.contains(bone);
        }
        return bone.startsWith("head") || bone.startsWith("mandible") || bone.startsWith("antenna") || bone.startsWith("carry");
    }

    /** Whether a bone marks where a load is held in the jaws (the plans' "carry" socket; "carry" with no plan). */
    public static boolean isHoldBone(String bone) {
        return KNOWN.contains(bone) ? HOLD.contains(bone) : "carry".equals(bone);
    }

    private static List<String> readIndex(Class<?> anchor, String folder) {
        JsonObject index = json(anchor, folder, "index.json");
        List<String> out = new ArrayList<>();
        if (index != null) {
            index.getAsJsonArray("plans").forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    @Nullable
    private static Plan read(Class<?> anchor, String folder, String model) {
        JsonObject json = json(anchor, folder, model + ".json");
        if (json == null) {
            return null;
        }
        Map<String, String> bones = new LinkedHashMap<>();
        json.getAsJsonObject("bones").entrySet().forEach(e -> bones.put(e.getKey(), e.getValue().getAsString()));
        Set<String> head = new HashSet<>();
        if (json.has("head_player")) {
            json.getAsJsonArray("head_player").forEach(e -> head.add(bones.getOrDefault(e.getAsString(), e.getAsString())));
        }
        JsonObject headPart = json.has("head") ? json.getAsJsonObject("head") : new JsonObject();
        if (headPart.has("sockets")) {
            JsonObject sockets = headPart.getAsJsonObject("sockets");
            if (sockets.has("carry")) {
                HOLD.add(bones.getOrDefault(sockets.get("carry").getAsString(), sockets.get("carry").getAsString()));
            }
        }
        List<Piece> spine = new ArrayList<>();
        if (json.has("spine")) {
            for (JsonElement e : json.getAsJsonArray("spine")) {
                JsonObject piece = e.getAsJsonObject();
                List<String> joints = new ArrayList<>();
                JsonArray list = piece.getAsJsonArray("joints");
                float[] shares = new float[list.size()];
                for (int i = 0; i < shares.length; i++) {
                    JsonObject joint = list.get(i).getAsJsonObject();
                    String role = joint.get("bone").getAsString();
                    joints.add(bones.getOrDefault(role, role));
                    shares[i] = joint.get("share").getAsFloat();
                }
                float maxPitch = piece.get("max_pitch").getAsFloat();
                spine.add(new Piece(List.copyOf(joints), shares, piece.get("hinge").getAsDouble(),
                        piece.get("from").getAsDouble(), piece.get("to").getAsDouble(), maxPitch,
                        piece.get("max_yaw").getAsFloat(),
                        piece.has("max_up") ? piece.get("max_up").getAsFloat() : maxPitch,
                        piece.has("max_down") ? piece.get("max_down").getAsFloat() : maxPitch));
            }
        }
        Climb climb = WORKER_CLIMB;
        if (json.has("climb")) {
            JsonObject c = json.getAsJsonObject("climb");
            climb = new Climb(c.get("front_feet").getAsDouble(), c.get("hind_feet").getAsDouble(),
                    c.get("front_end").getAsDouble(), c.get("rear_end").getAsDouble(),
                    c.get("tightest_curve").getAsDouble(), c.has("lift") ? c.get("lift").getAsDouble() : Double.NaN,
                    c.has("into_wall") ? c.get("into_wall").getAsDouble() : Double.NaN);
        }
        return new Plan(model, Collections.unmodifiableMap(bones), Collections.unmodifiableSet(head), List.copyOf(spine), climb);
    }

    @Nullable
    private static JsonObject json(Class<?> anchor, String folder, String file) {
        try (InputStream in = anchor.getResourceAsStream(folder + file)) {
            if (in == null) {
                LOG.warn("Body plans: no {}{} (the old bone-name rules are used)", folder, file);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            LOG.warn("Body plans: cannot read {}{} (the old bone-name rules are used)", folder, file, e);
            return null;
        }
    }
}
