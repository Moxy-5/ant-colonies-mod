package com.antcolonies.entity.body;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The time a block move is drawn at (code review R12-61, 2026-11-26): it follows the player's side's game time, and a
 * small step back (the film camera's frame order in a frozen game), but never the server setting the time back: then
 * the move stands at the furthest moment it was drawn at until the time is there again.
 */
class MoveClockTest {
    @Test
    void itFollowsTheGameTime() {
        MoveClock clock = new MoveClock();
        for (double t = 100.0; t < 120.0; t += 0.25) {
            assertEquals(t, clock.at("move", t));
        }
    }

    /** The camera in a frozen game: a frame at the end of the new tick, then the tick's own frames from its start. */
    @Test
    void theCamerasStepBackWithinATickIsFollowed() {
        MoveClock clock = new MoveClock();
        assertEquals(100.0, clock.at("move", 100.0));
        assertEquals(101.0, clock.at("move", 101.0));
        assertEquals(100.25, clock.at("move", 100.25));
        assertEquals(100.5, clock.at("move", 100.5));
        assertEquals(100.75, clock.at("move", 100.75));
        assertEquals(102.0, clock.at("move", 102.0));
    }

    /** A server at 15 ticks a second: the player's time ran 5 ticks ahead and is set back; the move stands, then goes on. */
    @Test
    void theServersRewindIsNotFollowed() {
        MoveClock clock = new MoveClock();
        double shown = 0.0;
        for (double t = 100.0; t <= 120.0; t += 0.5) {
            shown = clock.at("move", t);
        }
        assertEquals(120.0, shown);
        double before = shown;
        for (double t = 115.0; t <= 126.0; t += 0.5) {     // set back 5 ticks, running on
            shown = clock.at("move", t);
            assertEquals(Math.max(120.0, t), shown, "at " + t);
            assertEquals(true, shown >= before, "it went back at " + t);
            before = shown;
        }
    }

    @Test
    void aNewMoveStartsAfresh() {
        MoveClock clock = new MoveClock();
        clock.at("first", 500.0);
        clock.at("first", 480.0);                           // held at 500
        assertEquals(481.0, clock.at("second", 481.0));
    }
}
