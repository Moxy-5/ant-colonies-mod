package com.antcolonies.entity.body;

/**
 * Drawing only: the game time a block move is drawn at on a player's screen (code review R12-61, 2026-11-26). A move's
 * clip and its drawn place are worked out from the world's game time since the move started
 * ({@link BlockMoves#ticks}). On the player's side that time adds one every tick of the player's own computer and is
 * set straight to the server's by the server's time packet, once every 20 server ticks: with the server running slow
 * or stalled (an autosave), it runs ahead and is then set back, and every animal on a move jumped back along its climb
 * with it. This clock never follows such a rewind: the move stands at the furthest moment it was drawn at until the
 * time has caught up, then goes on.
 * <p>
 * A step back of up to {@link #FOLLOWED_BACK} ticks is followed: a frozen game stepped for the film camera draws one
 * frame at the end of a new tick and then that tick's own frames from its start (BUGS 380, where carrying on instead
 * made every frame film play its clips at twice their speed).
 * <p>
 * One for each animal's moves; species-neutral ({@link MoveRider}, and the Nile monitor's and the hippo's own copies).
 */
public final class MoveClock {
    /** A step back of up to this many ticks is the camera's frame order, further back the server's time packet. */
    static final double FOLLOWED_BACK = 1.0;

    private Object timed;
    private double last, shown, hold = Double.NEGATIVE_INFINITY;

    /**
     * The game time to draw this move at.
     *
     * @param move     the move played (a new one starts the clock afresh; told apart by {@code equals})
     * @param gameTime the player's side's game time now, with the part of a tick drawn
     */
    public double at(Object move, double gameTime) {
        if (!move.equals(timed)) {
            timed = move;
            hold = Double.NEGATIVE_INFINITY;
        } else if (gameTime < last - FOLLOWED_BACK) {
            hold = Math.max(hold, shown);   // set back: it stands where it was drawn last until the time is there again
        }
        last = gameTime;
        shown = Math.max(gameTime, hold);
        return shown;
    }
}
