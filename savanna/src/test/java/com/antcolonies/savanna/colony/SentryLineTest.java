package com.antcolonies.savanna.colony;

import com.antcolonies.colony.ForagingTrails;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * Where the minor soldiers stand along a foragers' trail ({@link SentryLine}), and how the trail's food patch is found
 * ({@link ForagingTrails#densest}).
 */
class SentryLineTest {
    /**
     * A trail running straight east, 14 blocks: a sentry every 3 blocks (3, 6, 9, 12; the next would stand among the
     * foragers at the patch), right beside the trail, first on its left (north, seen walking east), then on its right,
     * each facing straight away from the trail.
     */
    @Test
    void aStraightTrailIsLinedOnBothSidesFacingOut() {
        List<SentryLine.Spot> spots = SentryLine.along(0, 0, 14, 0, 3);
        assertEquals(List.of(
                new SentryLine.Spot(3, -1, 3, -4, true),
                new SentryLine.Spot(6, 1, 6, 4, false),
                new SentryLine.Spot(9, -1, 9, -4, true),
                new SentryLine.Spot(12, 1, 12, 4, false)), spots);
    }

    /** A patch right by the way out, or no spacing: no line (the sentry beside the way out is enough). */
    @Test
    void aShortTrailHasNoLine() {
        assertTrue(SentryLine.along(0, 0, 4, 0, 3).isEmpty());
        assertTrue(SentryLine.along(5, 5, 5, 5, 3).isEmpty());
        assertTrue(SentryLine.along(0, 0, 20, 0, 0).isEmpty());
    }

    /**
     * A slanting trail (every direction tried): the sentries stand right beside it (their cell's middle between half a
     * block and a little over two blocks from the trail's middle line, never on it), alternate sides, are in order along it, stop short
     * of the patch, and each faces away from the trail.
     */
    @Test
    void aSlantingTrailIsLinedBesideItNeverOnIt() {
        for (int angle = 0; angle < 360; angle += 15) {
            int toX = (int) Math.round(Math.cos(Math.toRadians(angle)) * 16);
            int toZ = (int) Math.round(Math.sin(Math.toRadians(angle)) * 16);
            List<SentryLine.Spot> spots = SentryLine.along(0, 0, toX, toZ, 3);
            assertTrue(spots.size() >= 4, "angle " + angle + ": only " + spots.size() + " sentries");
            double length = Math.hypot(toX, toZ);
            double ux = toX / length;
            double uz = toZ / length;
            double along = 0;
            for (int i = 0; i < spots.size(); i++) {
                SentryLine.Spot spot = spots.get(i);
                assertEquals(i % 2 == 0, spot.left(), "angle " + angle + ": sentries should alternate sides");
                double x = spot.x() + 0.5 - 0.5;
                double z = spot.z() + 0.5 - 0.5;
                double side = x * uz - z * ux; // > 0 on the left
                double forward = x * ux + z * uz;
                assertTrue(Math.abs(side) >= 0.5 && Math.abs(side) <= 2.1, "angle " + angle + ": sentry " + i + " stands " + side + " from the trail");
                assertTrue(spot.left() == side > 0, "angle " + angle + ": sentry " + i + " on the wrong side");
                assertTrue(forward > along && forward < length - 0.5, "angle " + angle + ": sentry " + i + " out of order or at the patch");
                along = forward;
                double faceSide = (spot.faceX() - spot.x()) * uz - (spot.faceZ() - spot.z()) * ux;
                assertTrue(Math.signum(faceSide) == Math.signum(side) && Math.abs(faceSide) >= 1.5,
                        "angle " + angle + ": sentry " + i + " does not face away from the trail");
            }
        }
    }

    /** The patch is the middle of where most spots lie, not of all of them (a single stray spot does not pull the trail). */
    @Test
    void theTrailLeadsToTheBusiestPatch() {
        List<BlockPos> spots = List.of(new BlockPos(20, 64, 20), new BlockPos(21, 64, 20), new BlockPos(20, 64, 22), new BlockPos(22, 64, 21),
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(-14, 64, 30));
        BlockPos patch = ForagingTrails.densest(spots, ForagingTrails.PATCH_RADIUS);
        assertTrue(patch.closerThan(new BlockPos(21, 64, 21), 1.5), "the patch is at " + patch);
        assertEquals(new BlockPos(5, 64, 5), ForagingTrails.densest(List.of(new BlockPos(5, 64, 5)), ForagingTrails.PATCH_RADIUS));
    }
}
