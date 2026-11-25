package com.antcolonies.savanna.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** BUGS 356: a route leaves out the loops of the line it is made from, so "how far along" has one answer on every cell of it. */
class RouteTest {
    /** The soak's own line (2026-11-24): east, a spur two blocks north and back to the same cell, then on east. */
    private static final List<BlockPos> SPUR = List.of(new BlockPos(2062, 72, 2147), new BlockPos(2063, 72, 2146), new BlockPos(2064, 71, 2146),
            new BlockPos(2065, 71, 2145), new BlockPos(2066, 71, 2145), new BlockPos(2066, 71, 2144), new BlockPos(2066, 71, 2143),
            new BlockPos(2066, 71, 2144), new BlockPos(2066, 71, 2145), new BlockPos(2067, 71, 2145), new BlockPos(2068, 71, 2146),
            new BlockPos(2069, 71, 2146));

    @Test
    void aSpurOutAndBackIsLeftOut() {
        Route route = new Route(SPUR);
        assertEquals(List.of(new BlockPos(2062, 72, 2147), new BlockPos(2063, 72, 2146), new BlockPos(2064, 71, 2146), new BlockPos(2065, 71, 2145),
                new BlockPos(2066, 71, 2145), new BlockPos(2067, 71, 2145), new BlockPos(2068, 71, 2146), new BlockPos(2069, 71, 2146)), route.points);
    }

    /** What stopped the column: standing on the place a step ahead, an ant must count as that far along, on every cell of the line. */
    @Test
    void anAntOnAPlaceAheadCountsAsThatFarAlong() {
        Route route = new Route(SPUR);
        for (double along = 0.0; along <= route.length(); along += 0.5) {
            BlockPos place = route.point(along);
            double counted = route.along(place.getX() + 0.5, place.getZ() + 0.5);
            assertTrue(Math.abs(counted - along) <= 1.0, "standing on the place " + along + " along counts as " + counted + " along");
        }
    }

    @Test
    void aLineWithNoLoopIsKeptAsItIs() {
        List<BlockPos> bend = List.of(new BlockPos(0, 70, 0), new BlockPos(1, 70, 0), new BlockPos(2, 70, 0), new BlockPos(2, 70, 1),
                new BlockPos(2, 71, 2), new BlockPos(3, 71, 3));
        assertEquals(bend, new Route(bend).points);
        List<BlockPos> two = List.of(new BlockPos(0, 70, 0), new BlockPos(0, 70, 1));
        assertEquals(two, new Route(two).points);
        // (a bridge over its own line, more than a block higher, is no loop)
        List<BlockPos> over = List.of(new BlockPos(0, 70, 0), new BlockPos(1, 70, 0), new BlockPos(2, 71, 0), new BlockPos(2, 72, 1),
                new BlockPos(1, 73, 1), new BlockPos(0, 73, 0), new BlockPos(-1, 73, 0));
        assertEquals(over, new Route(over).points);
    }
}
