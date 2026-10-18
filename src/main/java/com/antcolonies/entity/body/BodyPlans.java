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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

/**
 * Body plans (the animation engine upgrade, M3, 2026-10-18): what each model's parts are, read from
 * {@code assets/antcolonies/body_plans/<model>.json} (the same files the animation tools read; {@code tools/body_plan.py}
 * explains them). The game asks a plan for a part by its role ("head", "carry", the waist's joints) and gets the
 * model's own bone name, so a model with other bone names (the user's Blockbench worker, M2) needs only its plan's
 * rename table changed. Today every bone name is its role name.
 */
public final class BodyPlans {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FOLDER = "/assets/antcolonies/body_plans/";

    /** One model's plan: role to bone, the head player's bones, the waist's joints and their shares of the bend. */
    public record Plan(String model, Map<String, String> bones, Set<String> headPlayer, List<String> waist, float[] waistShare) {
        /** The model's bone for a role (the role itself if the plan does not name it). */
        public String bone(String role) {
            return bones.getOrDefault(role, role);
        }
    }

    private static final Map<String, Plan> PLANS = new HashMap<>();
    /** Every bone any plan names, those the head player owns, and those that hold a load. */
    private static final Set<String> KNOWN = new HashSet<>();
    private static final Set<String> HEAD_PLAYER = new HashSet<>();
    private static final Set<String> HOLD = new HashSet<>();

    static {
        for (String model : readIndex()) {
            Plan plan = read(model);
            if (plan != null) {
                PLANS.put(model, plan);
                KNOWN.addAll(plan.bones().values());
                HEAD_PLAYER.addAll(plan.headPlayer());
            }
        }
        LOG.info("Body plans: read {} ({})", PLANS.size(), PLANS.keySet());
    }

    private BodyPlans() {
    }

    /** A model's plan (by its geo file's name, e.g. worker_ant), or null if it has none. */
    @Nullable
    public static Plan of(String model) {
        return PLANS.get(model);
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

    private static List<String> readIndex() {
        JsonObject index = json("index.json");
        List<String> out = new ArrayList<>();
        if (index != null) {
            index.getAsJsonArray("plans").forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    @Nullable
    private static Plan read(String model) {
        JsonObject json = json(model + ".json");
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
        List<String> waist = new ArrayList<>();
        List<Float> shares = new ArrayList<>();
        if (json.has("waist")) {
            for (JsonElement e : json.getAsJsonArray("waist")) {
                JsonObject joint = e.getAsJsonObject();
                String role = joint.get("bone").getAsString();
                waist.add(bones.getOrDefault(role, role));
                shares.add(joint.get("share").getAsFloat());
            }
        }
        float[] share = new float[shares.size()];
        for (int i = 0; i < share.length; i++) {
            share[i] = shares.get(i);
        }
        return new Plan(model, Collections.unmodifiableMap(bones), Collections.unmodifiableSet(head), List.copyOf(waist), share);
    }

    @Nullable
    private static JsonObject json(String file) {
        try (InputStream in = BodyPlans.class.getResourceAsStream(FOLDER + file)) {
            if (in == null) {
                LOG.warn("Body plans: no {}{} (the old bone-name rules are used)", FOLDER, file);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            LOG.warn("Body plans: cannot read {}{} (the old bone-name rules are used)", FOLDER, file, e);
            return null;
        }
    }
}
