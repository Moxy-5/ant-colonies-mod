package com.antcolonies;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Lets the chunks settle before Minecraft saves the world on the way out ({@code docs/BUGS.md} 114: a game test
 * server, about one run in three, never exited, and the side-by-side test run waited for ever).
 *
 * <p>Minecraft's own bug, 1.21.1: when the server stops, {@code MinecraftServer.stopServer} unloads every chunk with
 * no time limit ({@code ChunkMap.processUnloads} with "always more time"). A chunk still lent to a neighbour's
 * generation (its generation count above 0) is put back on the unload queue at once, and the loop takes it off again,
 * for ever, on the server thread; but the neighbour's generation needs that same thread for its last steps (the full
 * chunk), so it never finishes and the count never drops. Seen in six thread dumps of a stuck server, no mod code on
 * the stack. Our tests load wide ground round every arena (galleries, new land, {@code TestIsolation.GROUND_ROUND}),
 * so a chunk is often half made when the last test ends.
 *
 * <p>The fix: when the server is about to stop (last of all the stopping handlers), drop the tickets the same way
 * {@code stopServer} does and run the chunk work in slices of 10 ms, with the server thread free between them for
 * the generation's own steps, until nothing is left (at most {@link #LONGEST_MS}). {@code stopServer} then finds no
 * work and saves at once. Nothing changes in play: it runs only on the way out, and only with no player on.
 *
 * <p>A last safety net for the test servers only: a game test server that has printed its results and is still not
 * gone {@link #TEST_SERVER_GRACE_MS} later is stopped hard with its result as the exit code (its world is thrown away
 * after a run anyway), so a run can never again wait for ever on one server.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID)
public final class ShutdownChunks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The longest the settling may take before handing over to Minecraft anyway (my pick: 60 s). */
    static final long LONGEST_MS = 60_000;
    /** One slice of chunk work, the server thread free for the generation's own tasks between (my pick: 10 ms). */
    private static final long SLICE_NS = 10_000_000L;

    /** How long a game test server may take to shut down before it is stopped hard (my pick: 3 minutes). */
    static final long TEST_SERVER_GRACE_MS = 180_000;

    private ShutdownChunks() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        if (event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer tests) {
            watchTestServer(tests);
        }
        // Only with nobody on (a game test or soak server, an empty dedicated server): with players still in the world,
        // dropping the tickets before stopServer has saved and removed them would unload the ground under them.
        if (event.getServer().getPlayerList().getPlayerCount() == 0) {
            settle(event.getServer());
        }
    }

    /** Runs the chunk work in slices until every level's chunk map is idle, or {@link #LONGEST_MS} has passed. */
    static void settle(MinecraftServer server) {
        long start = System.nanoTime();
        long deadline = start + LONGEST_MS * 1_000_000L;
        int rounds = 0;
        while (busy(server)) {
            if (System.nanoTime() > deadline) {
                LOGGER.warn("Chunks still busy after {} s on the way out; leaving them to Minecraft (docs/BUGS.md 114)", LONGEST_MS / 1000);
                return;
            }
            rounds++;
            for (ServerLevel level : server.getAllLevels()) {
                ServerChunkCache chunks = level.getChunkSource();
                chunks.removeTicketsOnClosing();
                long sliceEnd = System.nanoTime() + SLICE_NS;
                chunks.tick(() -> System.nanoTime() < sliceEnd, false);
            }
            // The generation's own steps queued for the server thread (the full chunk, the light), one level after
            // another, until a slice is used or none is left.
            long sliceEnd = System.nanoTime() + SLICE_NS;
            boolean ran = true;
            while (ran && System.nanoTime() < sliceEnd) {
                ran = false;
                for (ServerLevel level : server.getAllLevels()) {
                    ran |= level.getChunkSource().pollTask();
                }
            }
            if (!ran) {
                Thread.onSpinWait();
            }
        }
        if (rounds > 0) {
            LOGGER.info("Chunks settled on the way out in {} ms ({} rounds)", (System.nanoTime() - start) / 1_000_000L, rounds);
        }
    }

    /** The safety net: a daemon thread that ends the test server's process if it has not ended by itself in time. */
    private static void watchTestServer(net.minecraft.gametest.framework.GameTestServer server) {
        int failed = failedRequired(server);
        Thread watch = new Thread(() -> {
            try {
                Thread.sleep(TEST_SERVER_GRACE_MS);
            } catch (InterruptedException e) {
                return;
            }
            LOGGER.error("The game test server is still shutting down after {} s; stopping it hard with exit code {} (docs/BUGS.md 114)",
                    TEST_SERVER_GRACE_MS / 1000, failed);
            org.apache.logging.log4j.LogManager.shutdown();
            Runtime.getRuntime().halt(failed);
        }, "antcolonies shutdown watch");
        watch.setDaemon(true);
        watch.start();
    }

    /** The test server's count of failed required tests, its own exit code (1 if it cannot be read). */
    private static int failedRequired(net.minecraft.gametest.framework.GameTestServer server) {
        try {
            java.lang.reflect.Field field = net.minecraft.gametest.framework.GameTestServer.class.getDeclaredField("testTracker");
            field.setAccessible(true);
            Object tracker = field.get(server);
            return tracker instanceof net.minecraft.gametest.framework.MultipleTestTracker t ? t.getFailedRequiredCount() : 1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    private static boolean busy(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().chunkMap.hasWork()) {
                return true;
            }
        }
        return false;
    }
}
