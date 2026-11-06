package com.antcolonies.nav;

import java.util.ArrayList;
import java.util.List;

/**
 * The exact way an insect comes out of a doorway's mouth (the spire on top of a termite chimney block, see
 * {@link Doorway}) and goes back in, as a list of points the legs walk one after the other ({@link AntNavigator}).
 * Pure numbers, so it can be checked and drawn without the game ({@code DoorwayMotionTest}, {@code tools/chimney_preview.py}).
 *
 * <p>Coming out: up the middle of the spire, slowly until the head is out, a look round (the body holds still while the
 * head turns left and right, the "peek" animation), a step up to grip the rim, over it (the body tips forward and
 * lies across the top), head first down the outside of the spire, and off onto the block beside. The body turns slowly
 * while it goes over the rim, so it never flips. Going in is the same backwards, without the look round.
 *
 * <p>Every point is where the insect's position (its hitbox, at its feet) goes: {@code out} blocks from the doorway
 * block's middle toward the side it leaves by (the exit side; for going in, the side it comes from), and {@code y}
 * blocks up from the block's bottom. On a wall the body is drawn {@link #DRAWN_INTO_WALL} toward the wall from there
 * (the renderer's push into a wall), so the points allow for that. The numbers are worked out for a termite worker's
 * body at its drawn size: about 0.44 blocks long, its neck about 0.28 above its feet when it climbs head up, the tip of
 * its tail about 0.33 above them when it climbs head down. Every length of the body scales with the insect's size, its
 * game scale (real sizes, 2026-11-06, {@code docs/BUGS.md} 131: a real-size termite worker, less than half as big,
 * looked round from deep inside the spire and came down its outside floating off the face); the spire's own lengths
 * (its rim, its mouth, the block) do not.
 */
public final class DoorwayMotion {
    /** What the body holds while it walks to a point. */
    public enum On {
        /** Inside the spire, the wall on the exit side: climbing up the inside, head up. */
        INSIDE,
        /** The spire's outside face on the exit side: the body faces back toward the spire, head down. */
        OUTSIDE,
        /** Lying on top of something (the rim, the block beside). */
        TOP
    }

    /**
     * One point of the motion: walk there holding {@code on} at {@code speed} times the climbing speed, then stay
     * {@code hold} ticks (looking round if {@code peek}). {@code slowTurn}: the body tips over to this surface slowly.
     */
    public record Key(double out, double y, On on, double speed, int hold, boolean peek, boolean slowTurn) {
    }

    /**
     * On a wall the body is drawn this far toward the wall from the hitbox's middle (the renderer's push into the wall,
     * 0.17, at a termite's 3/4 size), at size 1; times the insect's size ({@link #drawnIntoWall}).
     */
    public static final double DRAWN_INTO_WALL = 0.13;
    /** How long the look round takes (ticks): the length of the "peek" animation, 2.8 seconds. */
    public static final int PEEK_TICKS = 56;
    /** While it looks round, its feet are this far below the rim: the head, the front legs and the front of the thorax are out, the rest inside (the head has room to tip forward, level, over the rim). At size 1; see {@link #peekDepth}. */
    public static final double PEEK_DEPTH = 0.2;
    /** Where the drawn belly is inside the spire, from its middle toward the exit side (on the inside of the wall there). */
    private static final double INSIDE_WALL = 0.05;
    /** Gripping the rim: the drawn belly this much farther toward the exit side than inside (at size 1). */
    private static final double TO_THE_RIM = 0.08;
    /** Going down the outside: the gap between the spire's face and the drawn feet (at size 1). */
    private static final double OFF_THE_FACE = 0.03;
    /** On a wall the body is also drawn this much higher than its feet (the renderer's lift, 0.25, at 3/4 size; at size 1). */
    private static final double DRAWN_LIFT = 0.19;
    /**
     * How fast it moves while it tips over the edge head first (times the climbing speed): its middle then stays at the
     * rim's edge while the body turns round it (the body tips slowly: 90 degrees in 6 ticks).
     */
    private static final double TIPPING_SPEED = 0.7;
    /**
     * Climbing up the outside, it starts to tip over the edge while its middle is still this far below the rim, and
     * rolls up over it (its front would otherwise stick up into the air beside the top first). At size 1.
     */
    private static final double ROLL_OVER = 0.08;
    /** Before the slow last bit to the look round: the tips of the antennae this far under the rim (at size 1). */
    private static final double ANTENNAE = 0.3;
    /** Gripping the rim: the feet this far under it (at size 1). */
    private static final double GRIP = 0.18;
    /** Kept between the body and the faces it holds (as everywhere in the legs). */
    private static final double EPS = 0.002;

    /** {@link #DRAWN_INTO_WALL} for an insect of this size (its game scale: 1 the termite worker the numbers were made for). */
    public static double drawnIntoWall(double size) {
        return DRAWN_INTO_WALL * size;
    }

    /** {@link #PEEK_DEPTH} for an insect of this size. */
    public static double peekDepth(double size) {
        return PEEK_DEPTH * size;
    }

    private DoorwayMotion() {
    }

    /**
     * Coming out of the mouth of a spire whose rim is {@code rim} blocks above the block's bottom and whose mouth is
     * {@code mouthRadius} blocks wide on each side of the middle, onto the top of the block beside on the exit side.
     * {@code halfWidth}: half the width of the insect's hitbox; {@code overTheTop}: how far onto the block beside it
     * walks (as at the end of any climb over the top).
     */
    public static List<Key> comeOut(double rim, double mouthRadius, double halfWidth, double overTheTop) {
        return comeOut(rim, mouthRadius, halfWidth, overTheTop, 1.0);
    }

    /** {@link #comeOut} for an insect of {@code size} (its game scale). */
    public static List<Key> comeOut(double rim, double mouthRadius, double halfWidth, double overTheTop, double size) {
        double drawn = drawnIntoWall(size);
        double inside = INSIDE_WALL - drawn;
        double outside = mouthRadius + drawn + OFF_THE_FACE * size;
        double onTop = 1.0 - (0.5 - halfWidth - overTheTop);
        double peek = peekDepth(size);
        List<Key> keys = new ArrayList<>();
        // Inside the block (hidden), over to the middle under the mouth.
        keys.add(new Key(inside, 0.5, On.INSIDE, 1.0, 0, false, false));
        // Up the spire until the tips of the antennae are just under the rim.
        keys.add(new Key(inside, rim - peek - ANTENNAE * size, On.INSIDE, 1.0, 0, false, false));
        // Slowly, the head comes out; then it holds still and looks round.
        keys.add(new Key(inside, rim - peek, On.INSIDE, 0.3, PEEK_TICKS, true, false));
        // Up a little and over to the exit side: the front legs grip the rim.
        keys.add(new Key(inside + TO_THE_RIM * size, rim - GRIP * size, On.INSIDE, 0.5, 4, false, false));
        // Over the rim: the body tips forward and lies across the top, its front over the edge.
        keys.add(new Key(outside - drawn, rim + EPS, On.TOP, 0.5, 0, false, true));
        // Over the edge: it tips head first onto the outside, turning round the rim's edge (the drawn body's middle
        // stays there: the hitbox moves out by the push into the wall and down by the lift while the body tips).
        keys.add(new Key(outside, rim + EPS - DRAWN_LIFT * size, On.OUTSIDE, TIPPING_SPEED, 0, false, true));
        // Down the outside of the spire to the block's top, and off onto the block beside.
        keys.add(new Key(outside, 1.0 + EPS, On.OUTSIDE, 1.0, 0, false, false));
        keys.add(new Key(onTop, 1.0 + EPS, On.TOP, 1.0, 0, false, false));
        return List.copyOf(keys);
    }

    /**
     * Going in by the mouth, from the top of the block beside (the side it comes from is the "exit side" here): to the
     * foot of the spire, up its outside until the body's middle is at the rim, over the edge onto the top (tipping
     * round the edge, slowly), across the top to the middle of the mouth, head first down into it (tipping round the
     * mouth's middle) and down inside into the block. It ends in the middle under the mouth (the legs then go on to the
     * wall the route goes on from). No look round.
     */
    public static List<Key> goIn(double rim, double mouthRadius, double halfWidth, double overTheTop) {
        return goIn(rim, mouthRadius, halfWidth, overTheTop, 1.0);
    }

    /** {@link #goIn} for an insect of {@code size} (its game scale). */
    public static List<Key> goIn(double rim, double mouthRadius, double halfWidth, double overTheTop, double size) {
        double drawn = drawnIntoWall(size);
        double inside = INSIDE_WALL - drawn;
        double outside = mouthRadius + drawn + OFF_THE_FACE * size;
        double lift = DRAWN_LIFT * size;
        List<Key> keys = new ArrayList<>();
        // Along the top of the block beside to the foot of the spire.
        keys.add(new Key(outside, 1.0 + EPS, On.TOP, 1.0, 0, false, false));
        // Up the outside until the drawn body's middle is just under the rim.
        keys.add(new Key(outside, rim + EPS - lift - ROLL_OVER * size, On.OUTSIDE, 1.0, 0, false, false));
        // Over the edge onto the top: it tips forward and rolls up over the edge (its middle ends at the edge).
        keys.add(new Key(outside - drawn, rim + EPS, On.TOP, TIPPING_SPEED, 0, false, true));
        // Across the top until its middle is over the middle of the mouth.
        keys.add(new Key(inside + drawn, rim + EPS, On.TOP, 0.5, 0, false, false));
        // Head first down into the mouth, turning round its middle.
        keys.add(new Key(inside, rim + EPS - lift, On.INSIDE, TIPPING_SPEED, 0, false, true));
        // Down inside the spire into the block.
        keys.add(new Key(inside, 0.5, On.INSIDE, 1.0, 0, false, false));
        return List.copyOf(keys);
    }
}
