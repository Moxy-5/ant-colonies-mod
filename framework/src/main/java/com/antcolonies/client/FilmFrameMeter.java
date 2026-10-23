package com.antcolonies.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.antcolonies.ColonyFrameworkMod;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The in-game camera's frame-time meter (development only, 2026-10-18, the upgrade review's F1): while filming, how long
 * the game spends drawing each frame (from the start of the frame to its end, so without the wait for the screen or the
 * frame limit), kept per scene and written to {@code run-film/screenshots/frames.txt}: the number of frames, the middle
 * frame time, the time 1 frame in 20 is slower than, and the slowest. The guard films' crowd scene (400 ants) against
 * the empty one before it gives the cost of drawing insects; {@code tools/guard_films.py} reads the file.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class FilmFrameMeter {
    private static final Logger LOG = LogUtils.getLogger();
    /** Frame times (milliseconds) of each scene so far, in the order filmed. */
    private static final Map<String, List<Float>> times = new LinkedHashMap<>();
    private static long frameStart;
    private static int sinceWritten;

    private FilmFrameMeter() {
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        if (FilmDirector.active()) {
            frameStart = System.nanoTime();
        }
    }

    @SubscribeEvent
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        if (!FilmDirector.active() || frameStart == 0L || Minecraft.getInstance().level == null) {
            return;
        }
        float ms = (System.nanoTime() - frameStart) / 1_000_000.0F;
        frameStart = 0L;
        times.computeIfAbsent(FilmDirector.sceneName(), scene -> new ArrayList<>()).add(ms);
        if (++sinceWritten >= 60) {                // the camera quits the game itself, so write as it goes
            sinceWritten = 0;
            write();
        }
    }

    private static void write() {
        List<String> lines = new ArrayList<>();
        lines.add("# scene frames middle_ms slow_1_in_20_ms slowest_ms (drawing time a frame, without the wait for the screen)");
        times.forEach((scene, list) -> {
            float[] sorted = new float[list.size()];
            for (int i = 0; i < sorted.length; i++) {
                sorted[i] = list.get(i);
            }
            java.util.Arrays.sort(sorted);
            lines.add(String.format(Locale.ROOT, "%s %d %.2f %.2f %.2f", scene, sorted.length, sorted[sorted.length / 2],
                    sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.95))], sorted[sorted.length - 1]));
        });
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("frames.txt");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Film: cannot write {}", file, e);
        }
    }
}
