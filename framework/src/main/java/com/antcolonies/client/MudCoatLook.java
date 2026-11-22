package com.antcolonies.client;

import com.antcolonies.coat.MudCoat;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntBinaryOperator;

/**
 * How a mud coat ({@link MudCoat}) is drawn (savanna mud step 4; Neon's mud studio, {@code docs/handoff/savanna-mud.md}):
 * two grey masks a model, {@code <texture>_mud_low.png} (feet and belly) and {@code <texture>_mud_full.png} (the whole
 * body but its eyes and mouth), made by {@code tools/mud_masks.py}, laid over the body in the coat's soil colour, see-through
 * by how much mud it wears (Neon's table): coverage 1 and 2 the low mask at 0.58 and 0.94; 3 and 4 the low at 0.95 and the
 * full at 0.42 and 0.72; 5 to 7 the full at 0.86, 0.96 and 1. Dry mud (6 and 7) is a little thinner (0.84). Not drawn
 * past {@link #FAR} blocks, nor on a creature with no masks.
 */
public final class MudCoatLook {
    /** Past this many blocks from the camera a coat is not drawn. */
    public static final double FAR = 56.0;
    /** The colour (0xRRGGBB) of a coat of this tone at this dryness (0 wet to 7 dry): the species mod's. */
    public static IntBinaryOperator tint = (tone, dryness) -> 0x6B5A40;

    private static final Map<ResourceLocation, Boolean> EXISTS = new ConcurrentHashMap<>();

    private MudCoatLook() {
    }

    /** The low and full masks' see-through (0 to 1) for this coverage and dryness: {low, full}. */
    public static float[] alphas(int coverage, int dryness) {
        float[] a = switch (coverage) {
            case 1 -> new float[] {0.58F, 0};
            case 2 -> new float[] {0.94F, 0};
            case 3 -> new float[] {0.95F, 0.42F};
            case 4 -> new float[] {0.95F, 0.72F};
            case 5 -> new float[] {0, 0.86F};
            case 6 -> new float[] {0, 0.96F};
            case 7 -> new float[] {0, 1.0F};
            default -> new float[] {0, 0};
        };
        if (dryness >= 6) {
            a[0] *= 0.84F;
            a[1] *= 0.84F;
        }
        return a;
    }

    /** Whether a coat on this creature is drawn now (it wears one and is near enough the camera). */
    public static boolean shows(Entity entity) {
        if (MudCoat.coverage(entity) == 0) {
            return false;
        }
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return camera.getPosition().distanceToSqr(entity.position()) <= FAR * FAR;
    }

    /** A mask for a texture ({@code <texture>_mud_<kind>.png}), or null if the model has none. */
    public static ResourceLocation mask(ResourceLocation texture, String kind) {
        ResourceLocation mask = texture.withPath(path -> path.replace(".png", "_mud_" + kind + ".png"));
        return EXISTS.computeIfAbsent(mask, m -> Minecraft.getInstance().getResourceManager().getResource(m).isPresent()) ? mask : null;
    }

    /** A colour with this see-through, for drawing ({@code alpha} 0 to 1 over the coat's tint). */
    public static int argb(Entity entity, float alpha) {
        int rgb = tint.applyAsInt(MudCoat.tone(entity), MudCoat.dryness(entity)) & 0xFFFFFF;
        return Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24 | rgb;
    }
}
