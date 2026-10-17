package com.antcolonies.block;

import com.antcolonies.block.GramaGrassBlock.Stage;

/**
 * The life of a grama plant (see {@link GramaGrassBlock}), worked out without the world so it can be tested alone: each
 * stage counts from 0 to 7 on random ticks with a chance per stage and moves on after 8 counts; grown leaves flower at
 * once in rain. The stages' lengths are my picks, to confirm (docs/DESIGN.md "Custom plants").
 */
public final class GramaLife {
    /** Random ticks are about this many game ticks apart for one block (4096 blocks a section, 3 random ticks a section a tick). */
    public static final double TICKS_PER_RANDOM_TICK = 4096.0 / 3.0;
    /** Counts in one stage (0 to 7). */
    public static final int COUNTS = 8;

    /** Where a plant is: its stage and its count in the stage. */
    public record Step(Stage stage, int age) {
    }

    private GramaLife() {
    }

    /**
     * How long (game days, on average) a plant stays in each stage: young 1 day, flowering 2 days (rain to ripe), ripe 3
     * days (holding seed, shedding), dried 2 days, grown leaves at most 5 days without rain (then they seed anyway).
     */
    public static double days(Stage stage) {
        return switch (stage) {
            case YOUNG -> 1.0;
            case LEAVES -> 5.0;
            case FLOWERING -> 2.0;
            case RIPE -> 3.0;
            case DRIED -> 2.0;
        };
    }

    /** The chance per random tick that a plant in this stage counts on. */
    public static float stepChance(Stage stage) {
        return (float) Math.min(1.0, COUNTS / (days(stage) * 24000.0 / TICKS_PER_RANDOM_TICK));
    }

    /** The stage after this one (dried goes back to leaves). */
    public static Stage after(Stage stage) {
        return switch (stage) {
            case YOUNG -> Stage.LEAVES;
            case LEAVES -> Stage.FLOWERING;
            case FLOWERING -> Stage.RIPE;
            case RIPE -> Stage.DRIED;
            case DRIED -> Stage.LEAVES;
        };
    }

    /** One random tick: {@code raining}, whether it rains in the world; {@code roll}, a random number from 0 to 1. */
    public static Step step(Stage stage, int age, boolean raining, float roll) {
        if (stage == Stage.LEAVES && raining) {
            return new Step(Stage.FLOWERING, 0);
        }
        if (roll >= stepChance(stage)) {
            return new Step(stage, age);
        }
        return age < COUNTS - 1 ? new Step(stage, age + 1) : new Step(after(stage), 0);
    }
}
