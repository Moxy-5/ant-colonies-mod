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

    /** Each animal's facts at the last game tick (made new each tick: never changed once out). */
    private static volatile Map<UUID, Map<String, Object>> board = Map.of();
    /** The game tick the board is from. */
    private static volatile long boardTick;
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
                now.put(entity.getUUID(), java.util.Collections.unmodifiableMap(out));   // its own order kept
            }
        }
        board = now;
        boardTick = event.getServer().getTickCount();
    }

    /** This animal's facts at the last game tick, or an empty map. */
    static Map<String, Object> of(UUID id) {
        return board.getOrDefault(id, Map.of());
    }

    static long tick() {
        return boardTick;
    }

    /**
     * The first animal (by id, so the same one every run) whose fact {@code key} reads {@code value} (as written, any case),
     * or null.
     */
    @Nullable
    static UUID find(String key, String value) {
        UUID best = null;
        for (Map.Entry<UUID, Map<String, Object>> e : board.entrySet()) {
            Object v = e.getValue().get(key);
            if (v != null && String.valueOf(v).toLowerCase(Locale.ROOT).equals(value.toLowerCase(Locale.ROOT))
                    && (best == null || e.getKey().compareTo(best) < 0)) {
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
