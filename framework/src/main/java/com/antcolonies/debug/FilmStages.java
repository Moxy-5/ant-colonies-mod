package com.antcolonies.debug;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Scenes a species mod builds for the in-game camera's {@code stage} step (development only; the Motion Studio, phase 0,
 * {@code docs/plans/motion-studio.md}): animals set up with their minds on (a hungry pride and a herd with a calf at
 * dusk), so a film shows the real game rather than a clip looping on the spot. A species signs its scenes up by name
 * (the framework never names a species); a scene shared with a game test builds the same animals the test checks.
 */
public final class FilmStages {
    /** A scene: built on the game's side at this spot; it may give back a job run every game tick while it is filmed. */
    @FunctionalInterface
    public interface Stage {
        @Nullable
        Runnable build(ServerLevel level, BlockPos origin);
    }

    private static final Map<String, Stage> STAGES = new ConcurrentHashMap<>();

    private FilmStages() {
    }

    /** Signs a scene up under this name (a species mod, at start). */
    public static void signUp(String name, Stage stage) {
        STAGES.put(name, stage);
    }

    /** The scene of that name, or null. */
    @Nullable
    public static Stage get(String name) {
        return STAGES.get(name);
    }

    /** Every scene's name, for the camera's message when a name is wrong. */
    public static Set<String> names() {
        return new TreeSet<>(STAGES.keySet());
    }
}
