package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;

/**
 * Brain step 4c: site fidelity ({@link ForagerSpots}: a forager's spot, dropped after two empty trips, moving into the
 * numbers' pool and back out with the ant) and the drift of a search that finds nothing (the range stretches from the
 * ants' own failures, the user's decision).
 */
class SiteFidelityTest {
    private static final SpeciesProfile.SearchNumbers SEARCH = new SpeciesProfile.SearchNumbers(true, 4, 1200, 2, 6000, 1200, 3, 8);

    private static ForagerSpots spots() {
        return new ForagerSpots(() -> Optional.of(SEARCH));
    }

    @Test
    void aFindBecomesTheSpotAndTwoEmptyTripsDropIt() {
        ForagerSpots spots = spots();
        UUID ant = UUID.randomUUID();
        BlockPos tuft = new BlockPos(10, 64, 3);
        spots.found(ant, tuft);
        assertEquals(tuft, spots.spotOf(ant));
        spots.emptyAtSpot(ant);
        assertEquals(tuft, spots.spotOf(ant), "one empty trip: it still goes back there");
        spots.found(ant, tuft);
        spots.emptyAtSpot(ant);
        assertEquals(tuft, spots.spotOf(ant), "a find in between clears the empties");
        spots.emptyAtSpot(ant);
        assertNull(spots.spotOf(ant), "two empty trips in a row: the spot is dropped");
    }

    @Test
    void anEmptyTripIsRememberedSoTheNextSearchGoesAnotherWayUntilAFind() {
        ForagerSpots spots = spots();
        UUID ant = UUID.randomUUID();
        spots.cameHomeEmpty(ant, 1.5);
        assertEquals(1.5, spots.leadOf(ant).angle(), 1e-9, "the way that came home empty (the next search avoids it)");
        spots.found(ant, new BlockPos(17, 64, 0));
        assertNull(spots.leadOf(ant), "a find clears it");
        spots.cameHomeEmpty(ant, 0.5);
        assertEquals(0.5, spots.leadOf(ant).angle(), 1e-9, "only the last empty way is kept");
    }

    @Test
    void theSpotMovesIntoTheNumbersAndBackOutWithTheAnt() {
        ForagerSpots spots = spots();
        UUID ant = UUID.randomUUID();
        BlockPos tuft = new BlockPos(-6, 64, 9);
        spots.found(ant, tuft);
        spots.intoNumbers(ant);
        assertNull(spots.spotOf(ant));
        assertEquals(1, spots.heldByNumbers(), "its spot is held by the numbers now, not lost");
        UUID out = UUID.randomUUID();
        spots.outOfNumbers(out);
        assertEquals(tuft, spots.spotOf(out), "one coming out takes the spot along");
        assertEquals(0, spots.heldByNumbers(), "never doubled");
        spots.died(out);
        assertNull(spots.spotOf(out), "a dead forager's spot is lost with it");
    }

    @Test
    void theNumbersSpotsFollowHowManyForagerNumbersThereAre() {
        ForagerSpots spots = spots();
        spots.addHeld(new BlockPos(5, 64, 5), 3, 0);
        spots.addHeld(new BlockPos(-5, 64, 5), 2, 0);
        spots.addHeld(new BlockPos(5, 64, 5), 1, 0);
        assertEquals(6, spots.heldByNumbers(), "the same spot found again is held by more");
        spots.trimHeld(3);
        assertEquals(3, spots.heldByNumbers(), "no more holders than there are forager numbers");
        ForagerSpots.Held held = spots.held().get(0);
        int holders = held.holders();
        assertEquals(0, spots.emptyHeld(held), "one empty trip: still held");
        assertEquals(holders, spots.emptyHeld(held), "two: dropped, its holders search again");
    }

    @Test
    void aSearchFindingNothingDriftsOutwardUpToTheStretch() {
        assertEquals(10.0, SEARCH.drifted(10, 0, 24), 1e-9);
        assertEquals(10.0, SEARCH.drifted(10, 1199, 24), 1e-9, "nothing moves before a minute is up");
        assertEquals(13.0, SEARCH.drifted(10, 1200, 24), 1e-9, "3 blocks farther out each minute");
        assertEquals(19.0, SEARCH.drifted(10, 3600, 24), 1e-9);
        assertEquals(32.0, SEARCH.drifted(10, 24000, 24), 1e-9, "never past the range plus the stretch of 8");
        assertEquals(35.0, SEARCH.drifted(35, 24000, 24), 1e-9, "a spot already past it is not pulled in");
    }
}
