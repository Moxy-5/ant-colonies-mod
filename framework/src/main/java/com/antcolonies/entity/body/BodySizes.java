package com.antcolonies.entity.body;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Real sizes (the user, 2026-11-05; {@code docs/plans/real-sizes.md}, step 1): how big each insect's body is drawn
 * and boxed, as a size factor on the size its model and box were made at. The insect's {@link
 * net.minecraft.world.entity.ai.attributes.Attributes#SCALE} is set to it (times its own size, a Matabele's own length),
 * so the drawing, the box, the climbing body (its plan's numbers are before the drawn scale) and the stride all follow
 * one number. Each species mod signs up its bodies at start ({@link #signUp}); a body not signed up keeps factor 1 (the
 * red harvester ant until its rework). Species-neutral: the framework never names a species' body.
 */
public final class BodySizes {
    private static final Map<ResourceLocation, Float> FACTORS = new ConcurrentHashMap<>();

    private BodySizes() {
    }

    /** Signs up a body's size factor (its mob's id, e.g. {@code antcolonies:termite_worker}). */
    public static void signUp(ResourceLocation body, float factor) {
        if (!(factor > 0.0F)) {
            throw new IllegalArgumentException("a body's size factor must be above 0: " + body + " " + factor);
        }
        FACTORS.put(body, factor);
    }

    /** The size factor of a body (1 if its mod signed none up). */
    public static float of(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? 1.0F : FACTORS.getOrDefault(id, 1.0F);
    }
}
