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
 *
 * <p>Two more columns since 2026-11-09 (the drawn-only insects' first measure): the middle drawing time by time (the
 * number to use for a crowd: the plain middle read 0.2 ms with 75 Matabele, the scene's empty first moments), and the
 * average whole frame, from one frame's start to the next one's (the camera turns the screen lock and the frame limit
 * off, so nothing waits for the screen).
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class FilmFrameMeter {
    private static final Logger LOG = LogUtils.getLogger();
    /** Frame times (milliseconds) of each scene so far, in the order filmed. */
    private static final Map<String, List<Float>> times = new LinkedHashMap<>();
    /** Whole-frame times (milliseconds, a frame's start to the next one's) of each scene. */
    private static final Map<String, double[]> whole = new LinkedHashMap<>();
    private static long frameStart;
    private static long lastStart;
    private static String lastScene = "";
    private static int sinceWritten;

    private FilmFrameMeter() {
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        if (FilmDirector.active()) {
            frameStart = System.nanoTime();
            String scene = FilmDirector.sceneName();
            if (lastStart != 0L && scene.equals(lastScene) && Minecraft.getInstance().level != null) {
                double[] sum = whole.computeIfAbsent(scene, name -> new double[2]);
                sum[0] += (frameStart - lastStart) / 1_000_000.0;
                sum[1]++;
            }
            lastStart = frameStart;
            lastScene = scene;
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
        lines.add("# scene frames middle_ms slow_1_in_20_ms slowest_ms middle_by_time_ms whole_frame_average_ms"
                + " (drawing time a frame, without the wait for the screen; the last: a frame's start to the next one's)");
        times.forEach((scene, list) -> {
            float[] sorted = new float[list.size()];
            for (int i = 0; i < sorted.length; i++) {
                sorted[i] = list.get(i);
            }
            java.util.Arrays.sort(sorted);
            double total = 0.0;
            for (float ms : sorted) {
                total += ms;
            }
            // the middle by time, not by frames: the frame time the scene spent half its drawing time at or under.
            // A scene's empty moments before its insects are summoned make thousands of quick frames in a blink
            // (75 Matabele: 1,500 frames of 0.2 ms, then 800 of 16 ms), which would win a count of frames
            double half = 0.0;
            float byTime = sorted[sorted.length - 1];
            for (float ms : sorted) {
                half += ms;
                if (half >= total / 2.0) {
                    byTime = ms;
                    break;
                }
            }
            double[] sum = whole.getOrDefault(scene, new double[] {0.0, 1.0});
            lines.add(String.format(Locale.ROOT, "%s %d %.2f %.2f %.2f %.3f %.3f", scene, sorted.length,
                    sorted[sorted.length / 2], sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.95))],
                    sorted[sorted.length - 1], byTime, sum[0] / Math.max(1.0, sum[1])));
        });
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("frames.txt");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
            if (Boolean.getBoolean("antcolonies.film.rawFrames")) {    // every frame's time in order, to see a pattern
                for (Map.Entry<String, List<Float>> scene : times.entrySet()) {
                    StringBuilder raw = new StringBuilder();
                    for (float ms : scene.getValue()) {
                        raw.append(String.format(Locale.ROOT, "%.2f%n", ms));
                    }
                    Files.writeString(file.resolveSibling("frames_" + scene.getKey() + ".txt"), raw);
                }
            }
        } catch (IOException e) {
            LOG.warn("Film: cannot write {}", file, e);
        }
    }
}
