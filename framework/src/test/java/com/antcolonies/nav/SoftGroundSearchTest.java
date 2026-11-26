package com.antcolonies.nav;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Review R2-18: what soft ground may cost. "A cost, never a wall" only holds while the route search can afford the
 * cost: it looks at every foothold cheaper than the real route first, so a band that must be crossed makes it flood the
 * open land on the near side, and past the game's limit ({@link AntPathFinder#DEFAULT_MAX_EXPANSIONS}) it answers "no
 * way" instead. The scene: open flat land without end, the goal in the middle of a ring of soft ground 8 blocks wide
 * (no way round it), the start 25 blocks from the goal. Measured 2026-11-26: at 12 a step (wet mud's cost until then)
 * the search fails; at 4 it crosses.
 */
class SoftGroundSearchTest {
    private static final BlockPos GOAL = new BlockPos(0, 64, 0);
    /** The ring of soft ground round the goal: from this far out to this far out (blocks, the bigger of x and z). */
    private static final int RING_FROM = 3, RING_TO = 10;

    private static CellMap land(double softCost) {
        return new CellMap() {
            @Override
            public boolean isOpen(BlockPos pos) {
                return pos.getY() >= 64;
            }

            @Override
            public boolean isSolidFace(BlockPos pos, Direction face) {
                return pos.getY() < 64;
            }

            @Override
            public double footCost(Foothold at) {
                int out = Math.max(Math.abs(at.cell().getX()), Math.abs(at.cell().getZ()));
                return at.isFloor() && out >= RING_FROM && out <= RING_TO ? softCost : 0.0;
            }
        };
    }

    private static Route across(double softCost) {
        return AntPathFinder.find(land(softCost), new BlockPos(25, 64, 0), GOAL, AntPathFinder.DEFAULT_MAX_EXPANSIONS);
    }

    @Test
    void softGroundAtFourAStepIsCrossedWithinTheGamesSearchLimit() {
        Route route = across(4.0);
        assertNotNull(route, "a band of soft ground 8 wide that must be crossed was a wall at 4 a step");
        assertTrue(route.size() <= 30, "the way across a ring 8 wide is " + route.size() + " steps long");
    }

    @Test
    void softGroundAtTwelveAStepIsAWallToTheGamesSearchLimit() {
        // (not a rule to keep: the measure that says why wet mud costs 4, not 12. If the search gets cleverer and this
        // starts finding a way, delete this test.)
        assertNull(across(12.0), "at 12 a step the search now crosses a band 8 wide: the cost could be raised again");
    }
}
