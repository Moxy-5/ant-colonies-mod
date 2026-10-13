package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.antcolonies.species.SpeciesProfile;

/** Jobs by age (step 5b): how a colony shares its workers out between nursing, building and foraging. */
class JobsByAgeTest {
    /** Counts in the order nurses, builders, foragers. */
    private static int[] share(int nursesWanted, int nurses, int builders, int foragers) {
        int[] natural = new int[ColonyRole.values().length];
        natural[ColonyRole.NURSE.ordinal()] = nurses;
        natural[ColonyRole.BUILDER.ordinal()] = builders;
        natural[ColonyRole.FORAGER.ordinal()] = foragers;
        int[] counts = Colony.shareOut(nurses + builders + foragers, nursesWanted, natural);
        return new int[] {counts[ColonyRole.NURSE.ordinal()], counts[ColonyRole.BUILDER.ordinal()], counts[ColonyRole.FORAGER.ordinal()]};
    }

    @Test
    void workersDoTheJobOfTheirAgeWhenEveryJobHasEnough() {
        assertArrayEquals(new int[] {3, 4, 5}, share(2, 3, 4, 5));
    }

    @Test
    void aBatchThatGrewUpTogetherIsSpreadOverTheJobs() {
        // The termites' founding batch: 5 workers, all young. One nurse is wanted; the rest must build and forage.
        assertArrayEquals(new int[] {1, 2, 2}, share(1, 5, 0, 0));
        // The ants' founding batch of 3.
        assertArrayEquals(new int[] {1, 1, 1}, share(1, 3, 0, 0));
    }

    @Test
    void anOldColonyStillHasNursesAndBuilders() {
        assertArrayEquals(new int[] {4, 6, 10}, share(4, 0, 0, 20));
    }

    @Test
    void spareYoungWorkersStayNursesAndBuildersComeFromTheJobWithMostToSpare() {
        assertArrayEquals(new int[] {5, 5, 6}, share(3, 10, 0, 6));
    }

    @Test
    void veryFewWorkersBuildFirst() {
        assertArrayEquals(new int[] {0, 1, 0}, share(0, 0, 0, 1));
        assertArrayEquals(new int[] {0, 1, 1}, share(0, 2, 0, 0));
    }

    @Test
    void theStagesFollowEachOther() {
        SpeciesProfile.WorkerNumbers numbers = new SpeciesProfile.WorkerNumbers(24000, 36000, 72000, 72000, 192000);
        assertEquals(ColonyRole.NURSE, numbers.roleForAge(0));
        assertEquals(ColonyRole.NURSE, numbers.roleForAge(71999));
        assertEquals(ColonyRole.BUILDER, numbers.roleForAge(72000));
        assertEquals(ColonyRole.BUILDER, numbers.roleForAge(191999));
        assertEquals(ColonyRole.FORAGER, numbers.roleForAge(192000));
    }
}
