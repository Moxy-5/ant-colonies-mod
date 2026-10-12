package com.antcolonies.nav;

/**
 * The kinds of move in a route. Each one says how the insect gets from one foothold to the next, so the walker never
 * has to guess (and a mind can read a route: how much climbing, any falls).
 */
public enum StepKind {
    /** Where the route starts (the first step of every route). */
    START,
    /** Along a floor to the next cell. */
    WALK,
    /** From the floor up onto a wall of the same cell (the foot of the wall, an inside corner). */
    ONTO_WALL,
    /** From a wall down onto the floor of the same cell (at the foot of the wall). */
    OFF_WALL,
    /** Up a wall, head up. */
    CLIMB_UP,
    /** Down a wall, head first (as real ants do). */
    CLIMB_DOWN,
    /** Sideways along a wall to the next cell. */
    CRAWL_SIDEWAYS,
    /** Round an inside corner from one wall of a cell onto the next wall of the same cell. */
    TURN_CORNER,
    /** Round an outside edge: the wall ends, and the insect goes round the block's vertical edge onto its side face. */
    ROUND_EDGE,
    /** Up a wall and over its top edge onto the top of the block. */
    OVER_TOP,
    /** Off the edge of a floor and down the side of the block, head first. */
    OVER_EDGE,
    /** From the top of a wall onto the ceiling of the same cell (an inside corner): from now on upside down. */
    ONTO_CEILING,
    /** From the ceiling onto a wall of the same cell, head first down it. */
    OFF_CEILING,
    /** Upside down along the ceiling to the next cell. */
    CRAWL_CEILING,
    /** Down a wall and round the block's bottom edge onto its underside (the ceiling of the cell beside), upside down. */
    UNDER_EDGE,
    /** Along a ceiling to where it ends and round the block's bottom edge up onto its side face. */
    OUT_FROM_UNDER,
    /** Lets go and drops to the floor below: only where there is nothing to climb down (or off a ceiling). */
    FALL
}
