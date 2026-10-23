package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the minor soldiers stand along a foragers' trail (pure geometry, so it can be unit-tested): from the way out
 * (the column an insect steps out into) straight toward the food patch, one sentry every {@code spacing} blocks, on
 * alternate sides of the trail (its left first), right beside it, each facing away from the trail. The line stops
 * {@link #SHORT_OF_PATCH} blocks before the patch, so the sentries do not stand among the foragers at work. Real
 * Macrotermes minors line both sides of a foraging column like this, facing out.
 */
public final class SentryLine {
    /** How far to the side of the trail's middle (blocks) a sentry stands: right beside the foragers. */
    static final double SIDE = 1.3;
    /** The line stops this far (blocks) short of the patch. */
    static final double SHORT_OF_PATCH = 1.5;
    /** How far out (blocks) the point a sentry faces lies (only its direction matters). */
    private static final double FACE = 3.0;

    /**
     * One sentry's place: the column it stands in ({@code x}, {@code z}), a point it faces ({@code faceX},
     * {@code faceZ}), and whether it stands on the trail's left (seen walking out).
     */
    public record Spot(int x, int z, int faceX, int faceZ, boolean left) {
    }

    private SentryLine() {
    }

    /** The sentries' places along the trail from column ({@code fromX}, {@code fromZ}) to ({@code toX}, {@code toZ}), nearest the way out first. */
    public static List<Spot> along(int fromX, int fromZ, int toX, int toZ, int spacing) {
        List<Spot> spots = new ArrayList<>();
        double startX = fromX + 0.5;
        double startZ = fromZ + 0.5;
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (spacing <= 0 || length < 1.0E-6) {
            return spots;
        }
        dx /= length;
        dz /= length;
        // The trail's left, seen walking out along it: (dz, -dx) (x runs east, z south).
        Set<Long> used = new HashSet<>();
        boolean left = true;
        for (int i = 1; i * spacing <= length - SHORT_OF_PATCH; i++) {
            double side = left ? SIDE : -SIDE;
            double x = startX + dx * i * spacing + dz * side;
            double z = startZ + dz * i * spacing - dx * side;
            int cellX = (int) Math.floor(x);
            int cellZ = (int) Math.floor(z);
            if (used.add(((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL))) {
                double out = side / SIDE * FACE;
                spots.add(new Spot(cellX, cellZ, (int) Math.floor(x + dz * out), (int) Math.floor(z - dx * out), left));
            }
            left = !left;
        }
        return spots;
    }
}
