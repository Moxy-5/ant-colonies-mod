package com.antcolonies.debug;

import java.util.Map;

/**
 * What an animal is doing in its own mind's words, for the in-game camera (development only; the Motion Studio, phase 0,
 * {@code docs/plans/motion-studio.md}): the facts kept on the game's side that the drawing side never hears (a lion's
 * hunt stage, whether it holds its prey, its hunger), written into the camera's recordings beside every bone, and asked
 * by the camera's {@code until} and {@code aim} steps. Read on the game's side once a tick while the camera films, never
 * in a real game. Species-neutral: each animal says what matters about itself.
 */
public interface FilmFacts {
    /** Its facts now: short names and plain values (numbers, words, true or false). */
    void filmFacts(Map<String, Object> out);
}
