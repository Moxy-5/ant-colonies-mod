package com.antcolonies.entity.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * The tidy search ({@link FightGoal#nextSmells}; the user's raid of 2026-11-09: raiders with no termite in reach paced
 * back and forth across the feeding spot, walking to random smells up to 12 blocks apart). A searcher walks to the
 * nearest smell it has not sniffed, remembering its last few places, so it works outward and never goes back.
 */
class TidySearchTest {
    /** Walks a searcher from {@code start} through the smells the way the fight's search does; the places it walks to. */
    private static List<BlockPos> walk(List<BlockPos> scent, BlockPos start, int steps) {
        ArrayDeque<BlockPos> sniffed = new ArrayDeque<>();
        List<BlockPos> walked = new ArrayList<>();
        BlockPos here = start;
        FightGoal.remember(sniffed, start); // (where it stands as it begins)
        for (int i = 0; i < steps; i++) {
            List<BlockPos> next = FightGoal.nextSmells(scent, here, sniffed);
            if (next.isEmpty()) {
                break;
            }
            here = next.get(0);
            FightGoal.remember(sniffed, here);
            walked.add(here);
        }
        return walked;
    }

    @Test
    void fromTheMiddleOfAGalleryItWorksOutToOneEndThenTheOtherWithoutPacing() {
        List<BlockPos> gallery = new ArrayList<>();
        for (int x = -11; x <= 11; x++) {
            gallery.add(new BlockPos(x, 0, 0));
        }
        List<BlockPos> walked = walk(gallery, new BlockPos(0, 1, 0), 40);
        // One way out to its end, then back past the middle once to the other end: it turns round at most once.
        int turns = 0;
        for (int i = 2; i < walked.size(); i++) {
            int before = Integer.signum(walked.get(i - 1).getX() - walked.get(i - 2).getX());
            int now = Integer.signum(walked.get(i).getX() - walked.get(i - 1).getX());
            if (before != 0 && now != 0 && before != now) {
                turns++;
            }
        }
        assertTrue(turns <= 1, "it turned round " + turns + " times along a straight gallery: " + walked);
        assertTrue(walked.stream().anyMatch(cell -> cell.getX() >= 9) && walked.stream().anyMatch(cell -> cell.getX() <= -9),
                "it did not reach both ends of the gallery: " + walked);
        assertTrue(walked.size() <= 8, "it took " + walked.size() + " steps for a 23-block gallery (short hops, many route searches)");
    }

    @Test
    void inAFieldOfSmellsItNeverGoesBackToAPlaceItSniffedAndTakesShortSteps() {
        Random random = new Random(1);
        List<BlockPos> scent = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            scent.add(new BlockPos(random.nextInt(25) - 12, random.nextInt(3) - 1, random.nextInt(25) - 12));
        }
        List<BlockPos> walked = walk(scent, BlockPos.ZERO, FightGoal.SNIFFED_KEPT);
        assertEquals(FightGoal.SNIFFED_KEPT, walked.size(), "it ran out of smells early: " + walked);
        for (int i = 0; i < walked.size(); i++) {
            for (int j = 0; j < i; j++) {
                assertTrue(walked.get(i).distManhattan(walked.get(j)) > FightGoal.SNIFF_REACH,
                        "step " + i + " went back to " + walked.get(i) + ", near step " + j + "'s " + walked.get(j));
            }
        }
        // The old search's random smells were about 10 blocks apart; the nearest one is a short walk.
        double total = 0;
        BlockPos from = BlockPos.ZERO;
        for (BlockPos cell : walked) {
            total += Math.sqrt(cell.distSqr(from));
            from = cell;
        }
        assertTrue(total / walked.size() < 7.0, "its steps averaged " + total / walked.size() + " blocks: " + walked);
    }
}
