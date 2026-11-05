package com.antcolonies.client;

import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * The raid test bench, B2: the screenshot in a player's report ({@code /antcolonies report} in single player), taken on
 * the game's drawing thread; it lands in the report folder's {@code screenshots/screenshot.png}. Client only: the
 * report calls it only on the client.
 */
public final class ReportShot {
    private ReportShot() {
    }

    public static void take(Path reportFolder) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Screenshot.grab(reportFolder.toFile(), "screenshot.png", mc.getMainRenderTarget(), message -> {
        }));
    }
}
