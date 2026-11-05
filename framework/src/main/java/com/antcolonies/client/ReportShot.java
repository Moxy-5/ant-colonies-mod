package com.antcolonies.client;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.lwjgl.glfw.GLFW;

import com.antcolonies.ColonyFrameworkMod;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * The raid test bench, B2: the screenshot in a player's report, in single player. Two ways in:
 * <ul>
 * <li><b>The report key</b> (F7, changeable under Controls, "Ant Colonies"): the picture is taken the moment the key is
 * pressed, then {@code /antcolonies report} is sent for you. Typing the command took seconds and the report's picture
 * came after the scene had moved on (the user's report, 2026-11-05).</li>
 * <li><b>The typed command:</b> the picture is taken first thing (before the place is copied, which takes a moment).</li>
 * </ul>
 * It lands in the report folder's {@code screenshots/screenshot.png}. Client only: the report calls it only on the client.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class ReportShot {
    /** The report key. */
    public static final KeyMapping REPORT_KEY = new KeyMapping("key.colonyframework.report", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7,
            "key.categories.colonyframework");
    /** Where the key's picture waits for its report (the game folder), and its name there. */
    private static final String WAITING_DIR = "report-shot";
    private static final String WAITING_NAME = "next.png";
    /** A key's picture older than this (ms) is not used: the report will take its own. */
    private static final long FRESH_MS = 20_000;

    /** When the report key took its picture (ms, 0 none waiting). The integrated server reads it in the same game. */
    private static volatile long keyShotAt;

    private ReportShot() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(REPORT_KEY);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (REPORT_KEY.consumeClick()) {
            if (mc.player == null || mc.getSingleplayerServer() == null) {
                continue;
            }
            File dir = new File(mc.gameDirectory, WAITING_DIR);
            File old = new File(new File(dir, "screenshots"), WAITING_NAME);
            old.delete();
            keyShotAt = System.currentTimeMillis();
            Screenshot.grab(dir, WAITING_NAME, mc.getMainRenderTarget(), message -> {
            });
            mc.player.connection.sendCommand("antcolonies report the report key (F7)");
        }
    }

    /**
     * A report is being saved to {@code reportFolder}: the key's picture moved in if one was just taken (waiting up to
     * 3 s for it to be written, it is written in the background), else a picture taken now. Call it first thing.
     * Returns a job to run with the report's files (in the background), or null.
     */
    public static Runnable take(Path reportFolder) {
        Minecraft mc = Minecraft.getInstance();
        long at = keyShotAt;
        if (at > 0 && System.currentTimeMillis() - at < FRESH_MS) {
            keyShotAt = 0;
            Path waiting = mc.gameDirectory.toPath().resolve(WAITING_DIR).resolve("screenshots").resolve(WAITING_NAME);
            return () -> {
                try {
                    for (int i = 0; i < 30 && !Files.exists(waiting); i++) {
                        Thread.sleep(100);
                    }
                    if (Files.exists(waiting)) {
                        Path shots = reportFolder.resolve("screenshots");
                        Files.createDirectories(shots);
                        Files.move(waiting, shots.resolve("screenshot.png"), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    ColonyFrameworkMod.LOGGER.warn("[rule] could not move the report key's picture into {}", reportFolder, e);
                }
            };
        }
        mc.execute(() -> Screenshot.grab(reportFolder.toFile(), "screenshot.png", mc.getMainRenderTarget(), message -> {
        }));
        return null;
    }
}
