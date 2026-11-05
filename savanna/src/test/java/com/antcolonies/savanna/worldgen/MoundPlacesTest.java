package com.antcolonies.savanna.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The mound place pattern of new land (M8 step 3F, F1, {@link MoundPlaces}): places 40 to 80 blocks apart, mostly
 * round 60, bigger mounds farther apart, the same answer however it is asked; and {@link TermiteMoundStructure#stageAt}
 * draws the bands' spread.
 */
class MoundPlacesTest {
    private static final MoundPlaces.Numbers N = MoundPlaces.Numbers.DEFAULT;

    @Test
    void placesStandFortyToEightyApartMostlyRoundSixty() {
        long seed = 98765L;
        List<MoundPlaces.Place> places = new ArrayList<>();
        int chunks = 60; // 960 by 960 blocks
        for (int cx = 0; cx < chunks; cx++) {
            for (int cz = 0; cz < chunks; cz++) {
                if (MoundPlaces.isPlace(seed, cx, cz, N)) {
                    places.add(MoundPlaces.candidate(seed, cx, cz, N));
                }
            }
        }
        List<Double> nearest = new ArrayList<>();
        List<Double> big = new ArrayList<>(), small = new ArrayList<>();
        for (MoundPlaces.Place p : places) {
            double best = Double.MAX_VALUE;
            for (MoundPlaces.Place q : places) {
                if (q != p) {
                    best = Math.min(best, Math.sqrt(p.middle().distSqr(q.middle())));
                }
            }
            // (only places well inside, so their neighbours are all counted)
            if (inside(p, chunks)) {
                nearest.add(best);
                (p.size() > 0.75 ? big : p.size() < 0.25 ? small : new ArrayList<Double>()).add(best);
            }
        }
        Collections.sort(nearest);
        assertTrue(nearest.get(0) >= 2 * N.territoryMin(), "two places nearer than the gap: " + nearest.get(0));
        double median = nearest.get(nearest.size() / 2), far = nearest.get((int) (nearest.size() * 0.95));
        assertTrue(median > 52 && median < 68, "the spacing's middle is not round 60: " + median);
        assertTrue(far <= 85, "places too far apart: " + far);
        assertTrue(mean(big) > mean(small) + 5, "big mounds do not stand farther apart: " + mean(big) + " against " + mean(small));
        // About one place to 60 by 60 blocks (1.7 to 2.7 to a 96 by 96 square).
        double perSquare = (double) places.size() / (chunks * chunks * 256.0) * 96 * 96;
        assertTrue(perSquare > 1.4 && perSquare < 2.8, "places per 96 by 96: " + perSquare);
    }

    @Test
    void theAnswerIsTheSameHoweverItIsAsked() {
        long seed = 4242L;
        List<Boolean> first = new ArrayList<>();
        for (int cx = 30; cx > -30; cx--) {
            for (int cz = -30; cz < 30; cz++) {
                first.add(MoundPlaces.isPlace(seed, cx, cz, N));
            }
        }
        // a different seed in between empties the memory
        MoundPlaces.isPlace(seed + 1, 0, 0, N);
        int i = 0;
        for (int cx = 30; cx > -30; cx--) {
            for (int cz = -30; cz < 30; cz++) {
                assertEquals(first.get(i++), MoundPlaces.isPlace(seed, cx, cz, N), "chunk " + cx + ", " + cz);
            }
        }
    }

    @Test
    void stagesAtQuantilesFollowTheBands() {
        List<TermiteMoundStructure.Band> bands = TermiteMoundStructure.Band.LIVING;
        int n = 10000;
        int[] counts = new int[5];
        for (int i = 0; i < n; i++) {
            int stage = TermiteMoundStructure.stageAt(bands, (i + 0.5) / n);
            for (int b = 0; b < bands.size(); b++) {
                if (stage >= bands.get(b).from() && stage <= bands.get(b).to()) {
                    counts[b]++;
                }
            }
        }
        for (int b = 0; b < bands.size(); b++) {
            assertEquals(bands.get(b).weight() / 100.0, counts[b] / (double) n, 0.01, "band " + b);
        }
        assertEquals(4, TermiteMoundStructure.stageAt(bands, 0));
        assertEquals(32, TermiteMoundStructure.stageAt(bands, 0.99999));
    }

    private static boolean inside(MoundPlaces.Place p, int chunks) {
        int edge = 100, size = chunks * 16;
        return p.middle().getX() > edge && p.middle().getX() < size - edge && p.middle().getZ() > edge && p.middle().getZ() < size - edge;
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
