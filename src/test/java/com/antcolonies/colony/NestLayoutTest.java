package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Checks what happens to the plan of the nest when a room is given up. */
class NestLayoutTest {
    private static final BlockPos BOTTOM = new BlockPos(100, 61, -40);

    private static NestLayout layout() {
        return new NestLayout(3, NestPlan.specs(BOTTOM, Direction.EAST));
    }

    @Test
    void aGivenUpRoomKeepsItsTunnelButNotItsDoorOrRoom() {
        NestLayout layout = layout();
        NestLayout.Room nursery = layout.rooms().get(1);
        int before = layout.cells().size();
        layout.giveUp(nursery, 5);
        assertEquals(before - (nursery.chamber.cells().size() - 2), layout.cells().size());
        assertTrue(layout.cells().containsAll(nursery.trunk()));
        assertFalse(layout.cells().contains(nursery.door()));
        assertEquals(0, layout.digLists().stream().filter(list -> list.size() == 2).count(), "nothing branches off the nursery, so its tunnel is not dug any more");
        assertEquals(2, layout.activeChambers().size());
        assertEquals(nursery, layout.givenUpRoomAt(nursery.door()));
        assertEquals(nursery, layout.givenUpRoomAt(nursery.chamber.center()));
    }

    @Test
    void theQueensRoomComesFirstAndCanBeReplaced() {
        NestLayout layout = layout();
        NestLayout.Room queen = layout.rooms().get(0);
        layout.giveUp(queen, 5);
        assertEquals(null, layout.activeQueenRoom());
        NestLayout.Room replacement = new NestLayout.Room(new NestPlan.Spec(NestPlan.Kind.QUEEN, BOTTOM, Direction.WEST));
        layout.add(replacement);
        assertEquals(replacement, layout.activeQueenRoom());
        assertEquals(replacement.chamber, layout.activeChambers().get(0));
        // The tunnel of the given-up queen's room is still there (and still dug): the nursery and the food store branch off it.
        assertTrue(layout.cells().containsAll(queen.trunk()));
        assertEquals(1, layout.digLists().stream().filter(list -> list.equals(queen.trunk())).count());
    }

    @Test
    void closingATunnelBlockThatLeadsToARoomWouldShutTheRoomOff() {
        NestLayout layout = layout();
        BlockPos mainTunnelBranch = layout.rooms().get(0).chamber.cells().get(NestPlan.BRANCH_AT - 1);
        assertFalse(layout.staysConnectedWithout(mainTunnelBranch, BOTTOM), "the nursery and the food store branch off here");
        assertFalse(layout.staysConnectedWithout(layout.rooms().get(1).door(), BOTTOM));
        assertTrue(layout.staysConnectedWithout(layout.rooms().get(1).chamber.center(), BOTTOM), "a block in the middle of a room can go");
        // Once the nursery is given up, the end of its stub is no longer needed by anything.
        NestLayout.Room nursery = layout.rooms().get(1);
        layout.giveUp(nursery, 5);
        assertTrue(layout.staysConnectedWithout(nursery.stand(), BOTTOM));
        assertFalse(layout.staysConnectedWithout(mainTunnelBranch, BOTTOM));
    }

    @Test
    void whichRoomsAreGivenUpIsSaved() {
        NestLayout layout = layout();
        layout.giveUp(layout.rooms().get(2), 5);
        layout.add(new NestLayout.Room(new NestPlan.Spec(NestPlan.Kind.FOOD, BOTTOM, Direction.WEST)));
        NestLayout loaded = NestLayout.load(layout.save());
        assertEquals(layout.shaftDepth(), loaded.shaftDepth());
        assertEquals(layout.rooms().size(), loaded.rooms().size());
        for (int i = 0; i < layout.rooms().size(); i++) {
            assertEquals(layout.rooms().get(i).spec, loaded.rooms().get(i).spec);
            assertEquals(layout.rooms().get(i).ruined, loaded.rooms().get(i).ruined);
        }
        assertEquals(layout.cells(), loaded.cells());
    }
}
