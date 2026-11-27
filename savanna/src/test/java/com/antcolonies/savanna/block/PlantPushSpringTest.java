package com.antcolonies.savanna.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

/**
 * Which pushed plants the client gives a spring (code review R4-12, 2026-11-26). The server marks every plant in a
 * block a pusher's box overlaps and hides its plain model; the client draws only plants with a spring. So every such
 * plant must get one ({@link PlantPush#spring} at 0 or more), or it is drawn by nobody. The fault: a spring only for
 * plants whose middle lay within a circle round the box's middle, so the plant at the corner of a walker's box vanished.
 */
class PlantPushSpringTest {
    private static final double REACH = 0.5;

    /** A player's box (0.6 wide) straddling the corner where four blocks meet, its middle in block (1, 1). */
    private static final AABB ON_A_CORNER = new AABB(0.99, 64.0, 0.99, 1.59, 65.8, 1.59);

    @Test
    void everyBlockTheBoxOverlapsGetsASpring() {
        int[] blocks = {0};
        PlantPush.eachBlock(ON_A_CORNER, at -> {
            if (at.getY() != 64) {
                return;
            }
            blocks[0]++;
            double close = PlantPush.spring(ON_A_CORNER, at.getX(), at.getZ(), at.getX() + 0.5, at.getZ() + 0.5, true, REACH);
            assertTrue(close >= 0.0, "the marked plant in block " + at.getX() + ", " + at.getZ() + " under the box gets no spring: nobody draws it");
        });
        assertEquals(4, blocks[0], "the box overlaps four blocks");
    }

    /** The review's own case: block (0, 0), its middle 1.12 from the box's, further than half the box and the reach. */
    @Test
    void thePlantAtTheBoxsCornerStandsUprightAndTheOneUnderItsMiddleLeans() {
        assertEquals(0.0, PlantPush.spring(ON_A_CORNER, 0, 0, 0.5, 0.5, true, REACH), "the corner plant");
        assertTrue(PlantPush.spring(ON_A_CORNER, 1, 1, 1.5, 1.5, true, REACH) > 0.5, "the plant by the box's middle hardly leans");
    }

    @Test
    void aPlantBesideTheBoxGetsASpringOnlyWhenTheServerMarksIt() {
        assertEquals(0.0, PlantPush.spring(ON_A_CORNER, 2, 1, 2.5, 1.5, true, REACH), "marked beside the box: upright");
        assertTrue(PlantPush.spring(ON_A_CORNER, 2, 1, 2.5, 1.5, false, REACH) < 0.0, "not marked beside the box: its plain model is drawn");
    }
}
