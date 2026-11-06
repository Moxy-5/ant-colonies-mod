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
    /** Each body's model length front to back at size factor 1 (blocks), for its length in play ({@link #modelLength}). */
    private static final Map<ResourceLocation, Float> MODEL_LENGTHS = new ConcurrentHashMap<>();

    private BodySizes() {
    }

    /** Signs up a body's size factor (its mob's id, e.g. {@code antcolonies:termite_worker}). */
    public static void signUp(ResourceLocation body, float factor) {
        if (!(factor > 0.0F)) {
            throw new IllegalArgumentException("a body's size factor must be above 0: " + body + " " + factor);
        }
        FACTORS.put(body, factor);
    }

    /**
     * Signs up a body's size factor and its model's length front to back at factor 1 (blocks, jaws included): real
     * sizes, step 2, reaches measured in body lengths ({@code AbstractAntEntity.bodyLength}).
     */
    public static void signUp(ResourceLocation body, float factor, float modelLength) {
        if (!(modelLength > 0.0F)) {
            throw new IllegalArgumentException("a body's model length must be above 0: " + body + " " + modelLength);
        }
        signUp(body, factor);
        MODEL_LENGTHS.put(body, modelLength);
    }

    /** The size factor of a body (1 if its mod signed none up). */
    public static float of(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? 1.0F : FACTORS.getOrDefault(id, 1.0F);
    }

    /** A body's model length at size factor 1 (blocks), or 0 if its mod signed none up (the red harvester ant). */
    public static float modelLength(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? 0.0F : MODEL_LENGTHS.getOrDefault(id, 0.0F);
    }
}
