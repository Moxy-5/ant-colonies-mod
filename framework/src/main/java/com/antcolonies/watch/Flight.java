package com.antcolonies.watch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.debug.StallWatch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The flight run (savanna loading, round 2, step L1, {@code docs/plans/savanna-loading-2.md}; {@code tools/flight.sh}): a
 * headless server makes a new normal world (the savanna spawn search and all), then a pretend player flies straight east
 * from the spawn at creative flying speed, loading land round it as a player at view distance {@code view} would, for
 * {@code antcolonies.flight} seconds of real time. It writes {@code flight-report.txt}: the stalls ({@link StallWatch}),
 * how much of the land in view was made by each second (the slow loading the user saw), and the server's tick time;
 * then it stops the server. The same seed gives the same land and flight, so two runs compare code before and after.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID)
public final class Flight {
    /** Creative flying speed, blocks a second (not sprinting). */
    static final double SPEED = Double.parseDouble(System.getProperty("antcolonies.flight.speed", "10.9"));
    static final int VIEW = Integer.getInteger("antcolonies.flight.view", 12);
    static final int SECONDS = Integer.getInteger("antcolonies.flight", 0);

    private static MinecraftServer server;
    private static BlockPos start;
    private static long startNanos;
    private static ChunkPos held;
    private static int lastSecond = -1;
    private static final List<String> seconds = new ArrayList<>();
    private static double shownSum;
    private static double shownLeast = 1;
    private static long tickNanosSum;
    private static long ticks;
    private static long worstTickNanos;
    private static boolean done;

    private Flight() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (SECONDS <= 0) {
            return;
        }
        server = event.getServer();
        ServerLevel level = server.overworld();
        start = level.getSharedSpawnPos();
        startNanos = System.nanoTime();
        ColonyFrameworkMod.LOGGER.info("[flight] from {} east at {} blocks a second, view {}, for {} s", start.toShortString(), SPEED, VIEW, SECONDS);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (server == null || done) {
            return;
        }
        long[] times = server.getTickTimesNanos();
        long last = times[Math.floorMod(server.getTickCount() - 1, times.length)];
        tickNanosSum += last;
        ticks++;
        worstTickNanos = Math.max(worstTickNanos, last);
        double t = (System.nanoTime() - startNanos) / 1e9;
        ServerLevel level = server.overworld();
        int x = start.getX() + (int) Math.round(SPEED * t);
        ChunkPos at = new ChunkPos(x >> 4, start.getZ() >> 4);
        if (!at.equals(held)) {
            if (held != null) {
                level.getChunkSource().removeRegionTicket(TicketType.PLAYER, held, VIEW, held);
            }
            level.getChunkSource().addRegionTicket(TicketType.PLAYER, at, VIEW, at);
            held = at;
        }
        int second = (int) t;
        if (second != lastSecond) {
            lastSecond = second;
            double shown = shown(level, at);
            shownSum += shown;
            shownLeast = Math.min(shownLeast, shown);
            seconds.add(String.format("  %3d s: x %d, %.0f%% of the land in view made, stalls so far %d", second, x, shown * 100,
                    StallWatch.STALLS.get()));
        }
        if (t >= SECONDS) {
            finish(level, x);
        }
    }

    /** The share of the chunks in view (round the pretend player, as far as it sees) that are made and loaded. */
    private static double shown(ServerLevel level, ChunkPos at) {
        int all = 0;
        int made = 0;
        for (int dx = -VIEW; dx <= VIEW; dx++) {
            for (int dz = -VIEW; dz <= VIEW; dz++) {
                all++;
                if (com.antcolonies.colony.LandReady.chunk(level, at.x + dx, at.z + dz)) {
                    made++;
                }
            }
        }
        return made / (double) all;
    }

    private static void finish(ServerLevel level, int x) {
        done = true;
        List<String> out = new ArrayList<>();
        out.add(String.format("Flight: %d s east from %s at %.1f blocks a second, view %d: %d blocks flown",
                SECONDS, start.toShortString(), SPEED, VIEW, x - start.getX()));
        out.add(String.format("Land in view made: %.0f%% on average, %.0f%% at the least", shownSum / Math.max(1, seconds.size()) * 100,
                shownLeast * 100));
        out.add(String.format("Server tick: %.1f ms on average, worst %.0f ms (%d ticks; 50 ms is the game's own pace)",
                tickNanosSum / 1e6 / Math.max(1, ticks), worstTickNanos / 1e6, ticks));
        out.add(String.format("Stalls (a tick over %d ms): %d, %d ms in all, longest %d ms", StallWatch.STALL_MS,
                StallWatch.STALLS.get(), StallWatch.STALL_TOTAL_MS.get(), StallWatch.STALL_LONGEST_MS.get()));
        synchronized (StallWatch.SEEN) {
            for (String s : StallWatch.SEEN) {
                out.add("  " + s);
            }
        }
        out.add("Each second:");
        out.addAll(seconds);
        String text = String.join(System.lineSeparator(), out);
        ColonyFrameworkMod.LOGGER.info("[flight] done{}{}", System.lineSeparator(), text);
        try {
            Files.writeString(server.getServerDirectory().resolve("flight-report.txt"), text + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ColonyFrameworkMod.LOGGER.warn("[flight] could not write the report", e);
        }
        server.halt(false);
    }
}
