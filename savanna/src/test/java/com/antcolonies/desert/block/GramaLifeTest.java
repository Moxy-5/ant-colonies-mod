package com.antcolonies.desert.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.antcolonies.desert.block.GramaGrassBlock.Stage;

/** Grama's life (the user's decisions, 2026-10-16): seeding after rain, a stand ripening together, back to leaves. */
class GramaLifeTest {
    private static final double DAY_IN_RANDOM_TICKS = 24000.0 / GramaLife.TICKS_PER_RANDOM_TICK;

    @Test
    void grownLeavesFlowerAtOnceInRainAndNotBefore() {
        assertEquals(new GramaLife.Step(Stage.FLOWERING, 0), GramaLife.step(Stage.LEAVES, 3, true, 0.99F), "rain sends up seed stalks");
        assertEquals(new GramaLife.Step(Stage.LEAVES, 3), GramaLife.step(Stage.LEAVES, 3, false, 0.99F), "no rain, no stalks (this tick)");
        assertEquals(new GramaLife.Step(Stage.YOUNG, 0), GramaLife.step(Stage.YOUNG, 0, true, 0.99F), "a young plant is not grown yet");
        assertEquals(new GramaLife.Step(Stage.DRIED, 2), GramaLife.step(Stage.DRIED, 2, true, 0.99F), "dried stalks do not flower again");
    }

    @Test
    void aStandThatFloweredTogetherRipensTogetherAboutTwoDaysLater() {
        Random random = new Random(1);
        int plants = 400;
        double sum = 0, sumSquares = 0;
        for (int i = 0; i < plants; i++) {
            double ticks = ticksIn(Stage.FLOWERING, random);
            sum += ticks;
            sumSquares += ticks * ticks;
        }
        double mean = sum / plants / DAY_IN_RANDOM_TICKS;
        double spread = Math.sqrt(sumSquares / plants - (sum / plants) * (sum / plants)) / DAY_IN_RANDOM_TICKS;
        assertTrue(Math.abs(mean - 2.0) < 0.15, "ripe about 2 days after rain, was " + mean);
        assertTrue(spread < 0.8, "a stand ripens within a day or so of itself, spread " + spread + " days");
    }

    @Test
    void ripeHeadsHoldSeedAboutThreeDaysThenDryAndGoBackToLeaves() {
        Random random = new Random(2);
        double sum = 0;
        for (int i = 0; i < 200; i++) {
            sum += ticksIn(Stage.RIPE, random);
        }
        assertTrue(Math.abs(sum / 200 / DAY_IN_RANDOM_TICKS - 3.0) < 0.25, "ripe about 3 days");
        assertEquals(Stage.DRIED, GramaLife.after(Stage.RIPE));
        assertEquals(Stage.LEAVES, GramaLife.after(Stage.DRIED), "dried stalks go back to leaves, the plant lives on");
        assertEquals(Stage.LEAVES, GramaLife.after(Stage.YOUNG));
    }

    @Test
    void withNoRainGrownLeavesSeedAnywayAfterAboutFiveDays() {
        Random random = new Random(3);
        double sum = 0;
        for (int i = 0; i < 200; i++) {
            sum += ticksIn(Stage.LEAVES, random);
        }
        assertTrue(Math.abs(sum / 200 / DAY_IN_RANDOM_TICKS - 5.0) < 0.4, "seeds anyway after about 5 days without rain");
    }

    /** How many random ticks a plant spends in this stage (no rain). */
    private static int ticksIn(Stage stage, Random random) {
        GramaLife.Step step = new GramaLife.Step(stage, 0);
        int ticks = 0;
        while (step.stage() == stage) {
            step = GramaLife.step(step.stage(), step.age(), false, random.nextFloat());
            ticks++;
        }
        return ticks;
    }
}
