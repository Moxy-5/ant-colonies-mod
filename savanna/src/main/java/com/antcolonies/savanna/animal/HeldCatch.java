package com.antcolonies.savanna.animal;

import com.antcolonies.savanna.SavannaMod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A catch in an otter's jaws (2026-11-22, the user: "the positioning of the fish in their mouth matters"): how it is
 * gripped, read from the same body plan numbers the Animal Maker Engine draws it from offline
 * ({@code python tools/clip_check.py otter --hold tilapia:held}): the otter's {@code checks.carry} and each prey's
 * {@code checks.held} ({@code assets/antcolonies/body_plans/}). One place for both, so what is judged offline is what is
 * drawn in the game.
 * <p>
 * The grip comes from the angle it was caught at (the fish's heading against the otter's, + to its right): side on,
 * gripped across its middle; caught from behind, near its tail; head on, near its head; the long end swept back along
 * the otter's cheek. It lies on its side across the jaws, its flanks between the teeth, and the jaw opens to its
 * thickness. A small fish is mostly swallowed at once.
 */
public final class HeldCatch {
    private HeldCatch() {
    }

    /** A prey's measures (model pixels, and blocks a pixel on a grown one). */
    public record Fish(double px, double middle, double centreZ, double half, double thick) {
    }

    /** The otter's grip (model pixels and degrees). */
    public record Carry(double gripAhead, double gripReach, double slant, double jawFill, double jawMin, double jawMax,
            double jawOther, double swallowUnder, double swallowChance, double eatAhead) {
    }

    /** Where along the catch it is gripped and how it lies across the jaws. */
    public record Grip(double along, double turn) {
    }

    /** Each prey kind's measures, read once; empty for a kind with none (a crab, a bullfrog: kept too, code review R12-63). */
    private static final Map<String, Optional<Fish>> FISH = new HashMap<>();
    @Nullable
    private static Carry carry;

    private static synchronized JsonObject checks(String animal) {
        String path = "/assets/" + SavannaMod.MODID + "/body_plans/" + animal + ".json";
        try (InputStream in = HeldCatch.class.getResourceAsStream(path)) {
            if (in == null) {
                return new JsonObject();
            }
            JsonObject plan = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return plan.has("checks") ? plan.getAsJsonObject("checks") : new JsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read the body plan " + path, e);
        }
    }

    /** The otter's grip numbers. */
    public static synchronized Carry carry() {
        if (carry == null) {
            JsonObject c = checks("otter").getAsJsonObject("carry");
            carry = new Carry(c.get("grip_ahead").getAsDouble(), c.get("grip_reach").getAsDouble(), c.get("slant").getAsDouble(),
                    c.get("jaw_fill").getAsDouble(), c.get("jaw_min").getAsDouble(), c.get("jaw_max").getAsDouble(),
                    c.get("jaw_other").getAsDouble(), c.get("swallow_under").getAsDouble(), c.get("swallow_chance").getAsDouble(),
                    c.getAsJsonObject("ground_clips").get("eat").getAsDouble());
        }
        return carry;
    }

    /** A prey's measures, or null for one drawn riding as before (a crab, a bullfrog). */
    @Nullable
    public static synchronized Fish fish(@Nullable Entity e) {
        if (e == null) {
            return null;
        }
        String name = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        return FISH.computeIfAbsent(name, n -> {
            JsonObject h = checks(n).getAsJsonObject("held");
            return h == null ? Optional.<Fish>empty() : Optional.of(new Fish(h.get("px").getAsDouble(), h.get("middle").getAsDouble(),
                    h.get("centre_z").getAsDouble(), h.get("half").getAsDouble(), h.get("thick").getAsDouble()));
        }).orElse(null);
    }

    private static double size(Entity e) {
        return e instanceof LivingEntity l ? l.getScale() : 1.0;
    }

    /** How thick it is drawn (blocks, flank to flank). */
    public static double thickness(Entity e) {
        Fish f = fish(e);
        return f == null ? 0.0 : f.thick() * f.px() * size(e);
    }

    /** How long it is drawn (blocks), 0 for one with no measures. */
    public static double length(Entity e) {
        Fish f = fish(e);
        return f == null ? 0.0 : 2.0 * f.half() * f.px() * size(e);
    }

    /** A small fish is mostly swallowed at once rather than carried to the bank. */
    public static boolean swallowed(Entity e, RandomSource random) {
        double len = length(e);
        return len > 0.0 && len < carry().swallowUnder() && random.nextDouble() < carry().swallowChance();
    }

    /** How wide the jaw opens round it (radians), for an otter this big (its drawn size). */
    public static double jawOpen(@Nullable Entity e, double otterSize) {
        Carry c = carry();
        Fish f = fish(e);
        if (f == null) {
            return Math.toRadians(c.jawOther());
        }
        double thick = f.thick() * f.px() * size(e) * 16.0 / otterSize;      // in the otter's model pixels
        return Mth.clamp(Math.atan(c.jawFill() * thick / c.gripAhead()), Math.toRadians(c.jawMin()), Math.toRadians(c.jawMax()));
    }

    /**
     * How far the catch rides down with the opening jaw (radians, turned about the jaw's hinge, with the jaw's own sign),
     * for the jaw open this far now (its live turn, whatever opens it: the hold, a clip, the face): halfway while the
     * jaw grips it, so it sits between the teeth; once the jaw opens wider than the catch is thick, lying on the lower
     * jaw (2026-11-22, Neon's diagnosis: a gulp opened the jaw and left the fish at the upper lip).
     */
    public static double ride(@Nullable Entity e, double otterSize, double jaw) {
        Fish f = fish(e);
        double thick = f == null ? 0.0 : f.thick() * f.px() * size(e) * 16.0 / otterSize;
        double open = Math.abs(jaw), onLower = open - Math.atan(thick * 0.5 / carry().gripAhead());
        return Math.copySign(Math.max(open * 0.5, onLower), jaw);
    }

    /**
     * The grip for a catch at this angle: how far from its middle toward its head it is gripped (model pixels of the
     * fish, toward its head, which is -z) and how it is turned across the jaws (degrees about the otter's up, its head
     * toward the side it was heading).
     */
    public static Grip grip(Fish f, float caughtAt) {
        Carry c = carry();
        double side = caughtAt >= 0 ? 1.0 : -1.0;
        double g = Mth.clamp((Math.abs(Mth.wrapDegrees(caughtAt)) / 180.0 - 0.5) * 2.0, -1.0, 1.0);
        return new Grip(f.centreZ() - g * c.gripReach() * f.half(), side * (-90.0 + c.slant() * g));
    }
}
