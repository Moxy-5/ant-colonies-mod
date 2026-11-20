package com.antcolonies.entity.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The dragged tail (the Animal Maker Engine step 2, stage A, 2026-11-19): on flat ground going straight it lies exactly
 * as the model's rest pose; round a turn it trails after the hips and then eases straight; with the hips a block up it
 * drapes down from them, never into the ground, no joint past its bend; raised a block in one go, it hangs from the
 * hips rather than jumping with them, and walked on over the step it slides up and lies on top. The numbers are the
 * monitor's (its tail in model pixels, drawn at three quarters size; places in blocks).
 */
class TailDragTest {
    /** Blocks per model pixel at the monitor's drawn size. */
    private static final double PB = 0.75 / 16.0;
    private static final double[] LENGTH = {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4};
    /** The monitor's rest heights, worked out from its tail bones' rest turns (base 11 px up). */
    private static final double[] REST = {10.36, 9.24, 7.81, 6.25, 4.74, 3.46, 2.59, 2.19, 1.86, 1.53, 1.20, 0.87};
    private static final double[] LOWEST = new double[12];

    static {
        for (int i = 0; i < 12; i++) {
            LOWEST[i] = 4.6 - 4.0 * (i + 1) / 12 + 0.25;
        }
    }

    private static final long FRAME = 50_000_000L;   // a frame every 0.05 s

    private static TailDrag drag() {
        return new TailDrag(LENGTH, REST, LOWEST);
    }

    /** How steep each piece lies (degrees, its far end up positive). */
    private static double[] pitches(TailDrag t) {
        double[] p = new double[t.pieces()];
        for (int i = 1; i <= t.pieces(); i++) {
            double dx = t.x(i) - t.x(i - 1), dy = t.y(i) - t.y(i - 1), dz = t.z(i) - t.z(i - 1);
            p[i - 1] = Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }
        return p;
    }

    /** No joint lower than its thickness over the ground under it, no piece too steep, no joint bent past its most. */
    private static void lies(TailDrag t, TailDrag.Ground ground, String when) {
        double[] p = pitches(t);
        for (int i = 1; i <= t.pieces(); i++) {
            double floor = ground.at(t.x(i), t.z(i)) + Math.min(LOWEST[i - 1], REST[i - 1]) * PB;
            assertTrue(t.y(i) >= floor - 1.0E-9, when + ": joint " + i + " at " + t.y(i) + " under its floor " + floor);
            assertTrue(Math.abs(p[i - 1]) <= TailDrag.MAX_STEEP + 1.0E-6, when + ": piece " + i + " lies " + p[i - 1]);
            if (i > 1) {
                assertTrue(Math.abs(p[i - 1] - p[i - 2]) <= TailDrag.MAX_JOINT + 1.0E-6, when + ": joint " + i + " bends " + (p[i - 1] - p[i - 2]));
            }
        }
    }

    @Test
    void flatAndStraightItLiesAsTheModelHasIt() {
        TailDrag t = drag();
        TailDrag.Ground flat = (x, z) -> 0.0;
        long now = 1;
        for (int f = 0; f < 60; f++, now += FRAME) {
            // walking forward (-z) half a pixel a frame, the tail behind (+z)
            t.update(flat, 0, 11 * PB, -0.5 * PB * f, 0, 1, 0, PB, true, now);
            for (int i = 1; i <= 12; i++) {
                assertEquals(REST[i - 1] * PB, t.y(i), 1.0E-9, "joint " + i + " at frame " + f);
                assertEquals(0, t.x(i), 1.0E-12, "joint " + i + " stays in line");
            }
        }
        assertEquals(1.0, t.weight(), 1.0E-9);
    }

    @Test
    void roundATurnItTrailsThenStraightens() {
        TailDrag t = drag();
        TailDrag.Ground flat = (x, z) -> 0.0;
        long now = 1;
        t.update(flat, 0, 11 * PB, 0, 0, 1, 0, PB, true, now);
        double tipX = t.x(12);
        // the hips turn a right angle on the spot: backward is now +x
        t.update(flat, 0, 11 * PB, 0, 1, 0, 0, PB, true, now += FRAME);
        double base = Math.toDegrees(Math.atan2(t.z(1) - t.z(0), t.x(1) - t.x(0)));
        assertTrue(Math.abs(base) <= TailDrag.MAX_SIDE + 1.0E-6, "the base follows the hips within a joint's reach: " + base);
        assertTrue(t.z(12) > 25 * PB, "the tip still lies back along the old line: z " + t.z(12) / PB + " px");
        assertTrue(Math.abs(t.x(12) - tipX) < t.x(6), "the tip has hardly moved, the base most");
        lies(t, flat, "just turned");
        for (int f = 0; f < 400; f++) {
            t.update(flat, 0, 11 * PB, 0, 1, 0, 0, PB, true, now += FRAME);
        }
        assertTrue(t.x(12) > 40 * PB && Math.abs(t.z(12)) < 1.5 * PB,
                "after 20 s standing it lies straight back: " + t.x(12) / PB + ", " + t.z(12) / PB + " px");
    }

    @Test
    void withTheHipsABlockUpItDrapesDownNeverIntoTheGround() {
        TailDrag t = drag();
        // a block under the hips, its edge 3 px behind them; low ground behind
        TailDrag.Ground step = (x, z) -> z < 3 * PB ? 1.0 : 0.0;
        long now = 1;
        for (int f = 0; f < 40; f++, now += FRAME) {
            t.update(step, 0, 1 + 11 * PB, 0, 0, 1, 0, PB, true, now);
            lies(t, step, "frame " + f);
        }
        assertEquals(REST[11] * PB, t.y(12), 0.1 * PB, "the tip lies on the low ground as at rest");
        assertTrue(pitches(t)[2] < -20, "a piece over the edge tips down: " + pitches(t)[2]);
    }

    @Test
    void raisedABlockAtOnceItHangsFromTheHipsThenSlidesUpOverTheEdge() {
        TailDrag t = drag();
        // a block ahead: its face 12 px in front of the tail's base (the box steps up onto it with its middle still short of it)
        TailDrag.Ground step = (x, z) -> z < -12 * PB ? 1.0 : 0.0;
        long now = 1;
        t.update(step, 0, 11 * PB, 0, 0, 1, 0, PB, true, now);
        // the hips are a block up in one frame (the box stepping up), the tail still behind on the low ground
        t.update(step, 0, 1 + 11 * PB, -0.5 * PB, 0, 1, 0, PB, true, now += FRAME);
        assertTrue(t.y(12) < 2 * PB, "the tip has not jumped up a block with the hips: " + t.y(12) / PB + " px");
        assertTrue(pitches(t)[0] < -25, "the base hangs down from the hips: " + pitches(t)[0]);
        lies(t, step, "just stepped up");
        // walking on, half a pixel a frame, until the whole tail is over the block
        for (int f = 2; f < 200; f++) {
            t.update(step, 0, 1 + 11 * PB, -0.5 * PB * f, 0, 1, 0, PB, true, now += FRAME);
            for (int i = 1; i <= 12; i++) {
                assertTrue(t.y(i) >= step.at(t.x(i), t.z(i)), "frame " + f + ": joint " + (i) + " inside the block");
            }
        }
        for (int i = 1; i <= 12; i++) {
            assertEquals(1 + REST[i - 1] * PB, t.y(i), 0.02 * PB, "joint " + i + " lies on top as at rest");
        }
    }

    /**
     * Come down a block (stage C, 2026-11-19): the hips go down off the edge with the tail still on top behind them. The
     * tail slants up from the hips over the edge and lies on the top, never through the block (as it went, the game films
     * showed), never arched up over it, and never bent a right angle or more at a joint (the drawing then turns a piece
     * over: the user saw it "flipping upside down"), and no joint snapping round from one frame to the next; then it
     * slides off as it walks on and lies on the low ground as at rest. Played at 20 and at 60 frames a second (the game
     * draws at its own rate), the hips tipped nose down as they go over the edge as in the climb down clip.
     */
    @Test
    void comeDownABlockItSlantsOverTheEdgeNeverThroughTheBlock() {
        comeDown(20);
        comeDown(60);
    }

    private static void comeDown(int fps) {
        TailDrag t = drag();
        // the block it came off behind it: its face (on a block border, as in the game) 10 px behind where the tail's
        // base ends up
        double off = 1.0 - 10 * PB;
        TailDrag.Ground step = (x, z) -> z > 1.0 ? 1.0 : 0.0;
        long frame = 1_000_000_000L / fps, now = 1;
        double[] bends = new double[12];
        // the hips walk forward 20 px and down a block in 2 s (the climb down), then on along the low ground at 10 px a second
        for (int f = 0; f <= 12 * fps; f++, now += frame) {
            double s = (double) f / fps, along = Math.min(s, 2.0) / 2.0;
            double bz = off + 20 * PB * (1 - along) - 10 * PB * Math.max(0, s - 2.0), by = 11 * PB + (1 - along);
            double pitch = 45 * Math.sin(Math.PI * Math.min(1, s / 2.2));     // the hips' tilt, the tail end up
            t.update(step, 0, by, bz, 0, 1, pitch, PB, true, now);
            String when = fps + " fps, " + String.format("%.2f", s) + " s";
            double[] p = pitches(t);
            for (int i = 1; i <= 12; i++) {
                double bend = p[i - 1] - (i > 1 ? p[i - 2] : pitch);
                if (fps == 60 && f > 0) {
                    assertTrue(Math.abs(bend - bends[i - 1]) < 25, when + ": joint " + i + " snaps from " + bends[i - 1] + " to " + bend);
                }
                bends[i - 1] = bend;
            }
            for (int i = 1; i <= 12; i++) {
                assertTrue(t.y(i) >= step.at(t.x(i), t.z(i)) - 1.0E-9, when + ": joint " + i + " inside the block");
                double my = (t.y(i) + t.y(i - 1)) / 2, mz = (t.z(i) + t.z(i - 1)) / 2;
                assertTrue(my >= step.at(0, mz) - 0.5 * PB, when + ": piece " + i + " through the block");
                assertTrue(Math.abs(bends[i - 1]) < TailDrag.MAX_FOLD + 1.0E-6, when + ": joint " + i + " bent " + bends[i - 1]);
                // down, what still lies on the block lies on it, not arched up over it
                if (s >= 2.0 && t.z(i) > 1.0 + PB) {
                    assertTrue(t.y(i) <= 1 + (LOWEST[i - 1] + 1.5) * PB, when + ": joint " + i + " arched "
                            + (t.y(i) - 1) / PB + " px over the block");
                }
            }
        }
        for (int i = 1; i <= 12; i++) {
            assertEquals(REST[i - 1] * PB, t.y(i), 0.02 * PB, fps + " fps: joint " + i + " lies on the low ground as at rest");
        }
    }

    @Test
    void handedBackToTheClipItEasesOver() {
        TailDrag t = drag();
        TailDrag.Ground flat = (x, z) -> 0.0;
        long now = 1;
        t.update(flat, 0, 11 * PB, 0, 0, 1, 0, PB, true, now);
        t.update(flat, 0, 11 * PB, 0, 0, 1, 0, PB, false, now += FRAME);
        assertTrue(t.weight() > 0.5 && t.weight() < 1.0, "a moment later still mostly dragged: " + t.weight());
        for (int f = 0; f < 40; f++) {
            t.update(flat, 0, 11 * PB, 0, 0, 1, 0, PB, false, now += FRAME);
        }
        assertEquals(0.0, t.weight(), 1.0E-3);
    }
}
