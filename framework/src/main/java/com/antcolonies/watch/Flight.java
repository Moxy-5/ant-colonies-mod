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
    /**
     * The stand (far colonies' speed, 2026-11-09, {@code tools/flight.sh ... stand}): in place of flying, the pretend
     * player waits at the spawn until the land in view is made, walks to the colony nearest the spawn and stands there
     * (counting as a watcher, {@link com.antcolonies.colony.Watchers#setPretend}); once that land is made and the far
     * colonies have had {@link #SETTLE} seconds to go over to numbers, the stopwatch's numbers are averaged for the
     * run's seconds and written to the report ({@link com.antcolonies.debug.Perf#sessionReport}).
     */
    static final boolean STAND = Boolean.getBoolean("antcolonies.flight.stand");
    /** The stand: seconds between the land round the colony being made and the measuring (the far rule's minute and a bit). */
    static final int SETTLE = Integer.getInteger("antcolonies.flight.settle", 100);
    /** The stand: at most this long (seconds) is waited for land to be made before going on anyway. */
    static final int LAND_WAIT = 600;
    private static int standStage;
    private static double stageSince;
    private static BlockPos standAt;

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
        if (STAND) {
            stand(level, t);
            return;
        }
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

    private static void hold(ServerLevel level, ChunkPos at) {
        if (!at.equals(held)) {
            if (held != null) {
                level.getChunkSource().removeRegionTicket(TicketType.PLAYER, held, VIEW, held);
            }
            level.getChunkSource().addRegionTicket(TicketType.PLAYER, at, VIEW, at);
            held = at;
        }
    }

    /** The stand's stages (see {@link #STAND}): 0 the spawn's land, 1 the colony's land, 2 settling, 3 measuring. */
    private static void stand(ServerLevel level, double t) {
        BlockPos at = standAt == null ? start : standAt;
        hold(level, new ChunkPos(at));
        level.resetEmptyTime(); // (a world with no real player stops ticking its mobs after 15 s: the insects must live here)
        // (Only asked while waiting for land: asked every tick it would be part of what is measured.)
        boolean made = standStage <= 1 && (shown(level, held) >= 1.0 || t - stageSince > LAND_WAIT);
        if (standStage == 0 && made) {
            com.antcolonies.colony.Colony nearest = null;
            for (com.antcolonies.colony.Colony colony : com.antcolonies.colony.ColonyManager.get(level).getColonies()) {
                if (nearest == null || colony.getCenter().distSqr(start) < nearest.getCenter().distSqr(start)) {
                    nearest = colony;
                }
            }
            if (nearest == null && t - stageSince < 60) {
                return; // (the new land's colonies are put in a moment after their land)
            }
            standAt = nearest == null ? start : nearest.getCenter();
            // Six blocks from the nest's middle, a little above the ground: a player standing by the mound.
            int px = standAt.getX() + 6;
            int pz = standAt.getZ();
            int py = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, px, pz) + 2;
            com.antcolonies.colony.Watchers.setPretend(List.of(new net.minecraft.world.phys.Vec3(px + 0.5, py, pz + 0.5)));
            ColonyFrameworkMod.LOGGER.info("[flight] stand: at the {} colony at {} ({} colonies known), after {} s",
                    nearest == null ? "no" : nearest.getSpecies(), standAt.toShortString(),
                    com.antcolonies.colony.ColonyManager.get(level).getColonies().size(), (int) t);
            standStage = 1;
            stageSince = t;
        } else if (standStage == 1 && made) {
            ColonyFrameworkMod.LOGGER.info("[flight] stand: the land in view is made after {} s; settling {} s", (int) t, SETTLE);
            standStage = 2;
            stageSince = t;
        } else if (standStage == 2 && t - stageSince >= SETTLE) {
            ColonyFrameworkMod.LOGGER.info("[flight] stand: measuring for {} s", SECONDS);
            com.antcolonies.debug.Perf.startSession();
            tickNanosSum = 0;
            ticks = 0;
            worstTickNanos = 0;
            standStage = 3;
            stageSince = t;
        } else if (standStage == 3 && t - stageSince < SECONDS) {
            if (VISITS) {
                visit(t - stageSince);
            }
        } else if (standStage == 3) {
            done = true;
            List<String> out = new ArrayList<>();
            out.addAll(visits);
            out.add(String.format("Stand: %d s at the colony at %s, view %d; %d colonies known in the world", SECONDS,
                    standAt.toShortString(), VIEW, com.antcolonies.colony.ColonyManager.get(level).getColonies().size()));
            out.add(String.format("Server tick: %.2f ms on average, worst %.0f ms (%d ticks)", tickNanosSum / 1e6 / Math.max(1, ticks),
                    worstTickNanos / 1e6, ticks));
            out.add(com.antcolonies.debug.Perf.sessionReport().replace("\n", System.lineSeparator()));
            write(out);
        }
    }

    /**
     * The visits ({@code tools/flight.sh ... visits}; tick spikes, the coming-back tick): while measuring, the pretend
     * player is gone for {@link #VISIT_AWAY} s (the colony goes over to numbers) and back for {@link #VISIT_HERE} s, over
     * and over; each return's worst server tick in its first 5 s is noted, so the first return (code never run before)
     * can be told from the ones after.
     */
    static final boolean VISITS = Boolean.getBoolean("antcolonies.flight.visits");
    static final int VISIT_AWAY = 40;
    static final int VISIT_HERE = 20;
    private static final List<String> visits = new ArrayList<>();
    private static List<net.minecraft.world.phys.Vec3> visitEyes;
    private static boolean visitHere = true;
    private static double visitBackAt = -1;
    private static long visitWorst;
    private static int visitTicks;

    private static void visit(double since) {
        if (visitEyes == null) {
            visitEyes = com.antcolonies.colony.Watchers.pretend();
        }
        boolean here = since % (VISIT_AWAY + VISIT_HERE) >= VISIT_AWAY;
        if (here != visitHere) {
            visitHere = here;
            com.antcolonies.colony.Watchers.setPretend(here ? visitEyes : List.of());
            if (here) {
                visitBackAt = since;
                visitWorst = 0;
                visitTicks = 0;
            }
        }
        if (visitBackAt >= 0) {
            long[] times = server.getTickTimesNanos();
            visitWorst = Math.max(visitWorst, times[Math.floorMod(server.getTickCount() - 1, times.length)]);
            visitTicks++;
            if (since - visitBackAt >= 5) {
                String line = String.format("[flight] visit %d: back at %.0f s, worst tick of the next 5 s %.1f ms (%d ticks)",
                        visits.size() + 1, visitBackAt, visitWorst / 1e6, visitTicks);
                visits.add(line);
                ColonyFrameworkMod.LOGGER.info(line);
                visitBackAt = -1;
            }
        }
    }

    private static void write(List<String> out) {
        String text = String.join(System.lineSeparator(), out);
        ColonyFrameworkMod.LOGGER.info("[flight] done{}{}", System.lineSeparator(), text);
        try {
            Files.writeString(server.getServerDirectory().resolve("flight-report.txt"), text + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ColonyFrameworkMod.LOGGER.warn("[flight] could not write the report", e);
        }
        server.halt(false);
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
