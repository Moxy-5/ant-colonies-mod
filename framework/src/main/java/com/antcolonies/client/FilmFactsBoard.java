package com.antcolonies.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.debug.FilmFacts;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The game side's facts for the in-game camera (development only; the Motion Studio, phase 0,
 * {@code docs/plans/motion-studio.md}): at the end of every game tick while filming, each animal's {@link FilmFacts}
 * written down here, so the drawing side can read them without reaching into the game's thread (the recorder writes
 * them beside each drawn moment; the {@code until} and {@code aim} steps ask them). Also runs the filmed scene's own job
 * each tick (a {@code stage} that keeps its herd grazing, as its game test does).
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
final class FilmFactsBoard {
    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Each animal's facts at the last game tick (made new each tick: never changed once out) and that tick, the
     * overworld's game time, which goes on only on ticks that run (not while the game is frozen, but on each tick a
     * frozen step runs): one object, so a reader never pairs one tick's facts with another tick (the Motion Studio,
     * phase 1, 2026-11-27; it was the server's tick count, which goes on while frozen, written beside the facts).
     */
    record Board(Map<UUID, Map<String, Object>> facts, long tick) {
    }

    private static volatile Board board = new Board(Map.of(), 0);
    /** The filmed scene's job, run each game tick (null when none). */
    private static volatile Runnable stageJob;

    private FilmFactsBoard() {
    }

    /** The scene's job from now (null: none). */
    static void setStageJob(@Nullable Runnable job) {
        stageJob = job;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!FilmDirector.active()) {
            return;
        }
        Runnable job = stageJob;
        if (job != null) {
            try {
                job.run();
            } catch (RuntimeException e) {
                LOG.warn("Film: the scene's job failed and stops", e);
                stageJob = null;
            }
        }
        Map<UUID, Map<String, Object>> now = new HashMap<>();
        for (Entity entity : event.getServer().overworld().getAllEntities()) {
            if (entity instanceof FilmFacts facts) {
                Map<String, Object> out = new LinkedHashMap<>();
                try {
                    facts.filmFacts(out);
                } catch (RuntimeException e) {
                    out.put("facts_failed", e.toString());
                }
                out.values().removeIf(java.util.Objects::isNull);
                if (entity.getCustomName() != null) {
                    out.putIfAbsent("name", entity.getCustomName().getString());   // ("until doing=feed,name=film-lioness")
                }
                now.put(entity.getUUID(), java.util.Collections.unmodifiableMap(out));   // its own order kept
            }
        }
        board = new Board(now, event.getServer().overworld().getGameTime());
        String[][] when = freezeWhen;
        if (when != null && find(when) != null) {
            // the "until ... freeze" step: the game stopped on the very tick its facts first read so, here on the game's
            // side (the Motion Studio, the new phase 2, step 8): frozen from the drawing side it ran on a few ticks (60
            // with a backlog, BUGS 449), so a take meant to start at a clip change started after it
            freezeWhen = null;
            event.getServer().tickRateManager().setFrozen(true);
            if (alreadySo) {
                LOG.warn("Film: frozen at game tick {}, but {} already read so when the step was reached: the take is not "
                        + "at its start (an earlier take ran past it)", board.tick(), describe(when));
            } else {
                LOG.info("Film: frozen at game tick {} as {} first read so", board.tick(), describe(when));
            }
            FilmStep.holdNow(event.getServer());   // (still from here on: no empty tick before the take's first step)
        }
    }

    /** The facts the game is to be frozen at the tick they first read so (null: none). */
    private static volatile String[][] freezeWhen;
    /** Whether they read so already when the step asked (the freeze then comes at once, and says so). */
    private static volatile boolean alreadySo;

    /**
     * Freezes the game on the first tick some animal's facts read so ({@link #find(String[][])}); null: none (a new
     * scene and a stopped film clear it, so a freeze left over never stops a later one).
     */
    static void freezeWhen(@Nullable String[][] when) {
        alreadySo = when != null && find(when) != null;
        freezeWhen = when;
    }

    /** "key=value,key=value" as pairs. */
    static String[][] conditions(String text) {
        String[] parts = text.split(",");
        String[][] out = new String[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            out[i] = new String[] {kv[0], kv.length > 1 ? kv[1] : "true"};
        }
        return out;
    }

    static String describe(String[][] when) {
        StringBuilder s = new StringBuilder();
        for (String[] kv : when) {
            s.append(s.length() > 0 ? "," : "").append(kv[0]).append('=').append(kv[1]);
        }
        return s.toString();
    }

    /** This animal's facts at the last game tick, or an empty map. */
    static Map<String, Object> of(UUID id) {
        return board.facts().getOrDefault(id, Map.of());
    }

    /** The last game tick's facts and the tick, together. */
    static Board snapshot() {
        return board;
    }

    static long tick() {
        return board.tick();
    }

    /**
     * The first animal (by id, so the same one every run) whose fact {@code key} reads {@code value} (as written, any case),
     * or null.
     */
    @Nullable
    static UUID find(String key, String value) {
        return find(new String[][] {{key, value}});
    }

    /** The first animal (by id) whose facts read all of these (key, value) pairs, or null. */
    @Nullable
    static UUID find(String[][] when) {
        UUID best = null;
        for (Map.Entry<UUID, Map<String, Object>> e : board.facts().entrySet()) {
            boolean all = true;
            for (String[] kv : when) {
                Object v = e.getValue().get(kv[0]);
                all &= v != null && String.valueOf(v).toLowerCase(Locale.ROOT).equals(kv[1].toLowerCase(Locale.ROOT));
            }
            if (all && (best == null || e.getKey().compareTo(best) < 0)) {
                best = e.getKey();
            }
        }
        return best;
    }

    /** Facts as a JSON object (numbers as numbers, true and false as they are, the rest as text). */
    static String json(Map<String, Object> facts) {
        StringBuilder s = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : facts.entrySet()) {
            s.append(first ? "" : ",").append('"').append(e.getKey()).append("\":");
            first = false;
            Object v = e.getValue();
            if (v instanceof Number n) {
                double d = n.doubleValue();
                s.append(Double.isFinite(d) ? (d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d)
                        : String.format(Locale.ROOT, "%.4f", d)) : "null");
            } else if (v instanceof Boolean b) {
                s.append(b);
            } else {
                s.append('"').append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return s.append('}').toString();
    }
}
