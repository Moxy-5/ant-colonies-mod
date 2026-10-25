package com.antcolonies.client;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.registry.ModEffects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The pain of a sting on the screen (M8 step 2e, {@code fight.Pain}): while {@link ModEffects#STING_PAIN} lasts the
 * screen's edge pulses red, a throb about once a second, fading out over the last second.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class StingPainOverlay {
    /** How many bands the red edge is drawn in (each fainter toward the middle). */
    private static final int BANDS = 10;

    private StingPainOverlay() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "sting_pain"),
                StingPainOverlay::draw);
    }

    private static void draw(GuiGraphics graphics, DeltaTracker delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        MobEffectInstance pain = player.getEffect(ModEffects.STING_PAIN);
        if (pain == null) {
            return;
        }
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float throb = 0.55F + 0.45F * (float) Math.sin(time * 2 * Math.PI / 22.0);
        float fade = pain.isInfiniteDuration() ? 1.0F : Math.min(1.0F, pain.getDuration() / 20.0F);
        float strength = throb * fade;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int edge = Math.max(8, Math.min(width, height) / 6);
        int step = Math.max(1, edge / BANDS);
        for (int band = 0; band < BANDS; band++) {
            int alpha = (int) (110 * strength * (1.0F - band / (float) BANDS));
            if (alpha <= 0) {
                continue;
            }
            int color = (alpha << 24) | 0x9E1B14;
            int in = band * step;
            graphics.fill(in, in, width - in, in + step, color);                          // top
            graphics.fill(in, height - in - step, width - in, height - in, color);        // bottom
            graphics.fill(in, in + step, in + step, height - in - step, color);           // left
            graphics.fill(width - in - step, in + step, width - in, height - in - step, color); // right
        }
    }
}
