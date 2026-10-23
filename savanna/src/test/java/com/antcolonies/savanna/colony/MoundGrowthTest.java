package com.antcolonies.savanna.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.antcolonies.savanna.colony.MoundPlan.Part;
import com.antcolonies.savanna.colony.MoundPlan.Shape;
import com.antcolonies.species.SpeciesProfile.MoundNumbers;

import net.minecraft.core.BlockPos;

/**
 * The "can it be built" test (tunnel plan step 4, the user's request): every stage of many mounds is built in a
 * pretend world by the termites' own rules ({@link MoundGrowth}), from inside only (the shaft and the tunnels), and must
 * be finished without ever getting stuck: stage after stage (as the game does: one stage after the other), with the
 * jobs done in a random order, and with surprises on the way (some blocks of every stage cannot be worked on, and the
 * termites move on without them). A stuck block fails the test and is named. Also: every outside block can be mended
 * from inside, and tunnels, once there, stay.
 */
class MoundGrowthTest {
    private static final MoundNumbers NUMBERS = new MoundNumbers(32, 2.0F, 0.5F, 3, 13);
    /** The usual six, and more colonies picked at random once (fixed, so a failure can be looked at again). */
    private static final long[] SEEDS = {1, 7, 42, 2024, -987654321L, 31337, 5, 99, 123456789L, -42, 777, 2718281828L, 31415926L,
            -1L, 8675309L, 4242, 1999, 65536, -7777777L, 13};

    /** Each colony's stages, grown once for all the tests here (growing takes a moment). */
    private static final Map<Long, List<Shape>> GROWN = new java.util.HashMap<>();

    private static synchronized List<Shape> grown(long seed) {
        return GROWN.computeIfAbsent(seed, s -> MoundPlan.growth(NUMBERS, s));
    }

    private static List<Shape> allStages(long seed) {
        return grown(seed).subList(1, NUMBERS.maxHeight() + 1);
    }

    @Test
    void everyStageOfManyMoundsCanBeGrownFromInside() {
        for (long seed : SEEDS) {
            List<String> problems = MoundGrowth.problems(NUMBERS.maxHeight(), h -> MoundPlan.shapeOf(h, NUMBERS, seed));
            assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems);
        }
    }

    @Test
    void stageAfterStageTheTunnelsAreEnough() {
        for (long seed : SEEDS) {
            List<MoundGrowth.Stuck> stuck = MoundGrowth.check(allStages(seed), null);
            assertTrue(stuck.isEmpty(), "seed " + seed + ": stuck at " + stuck);
        }
    }

    @Test
    void inAnyOrder() {
        for (long seed : SEEDS) {
            List<MoundGrowth.Stuck> stuck = MoundGrowth.check(allStages(seed), new Random(seed));
            assertTrue(stuck.isEmpty(), "seed " + seed + ": stuck at " + stuck);
        }
    }

    @Test
    void withSurprisesOnTheWay() {
        for (long seed : SEEDS) {
            Random random = new Random(seed * 31 + 1);
            for (double share : new double[] {0.01, 0.05}) {
                List<MoundGrowth.Stuck> stuck = MoundGrowth.checkWithSurprises(allStages(seed), random, share);
                assertTrue(stuck.isEmpty(), "seed " + seed + ", " + share + " of every stage blocked: stuck at " + stuck);
            }
        }
    }

    @Test
    void everyOutsideBlockCanBeMendedFromInside() {
        for (long seed : SEEDS) {
            for (int h : new int[] {8, 20, 32}) {
                List<BlockPos> missing = MoundGrowth.unreachableOutside(grown(seed).subList(1, h + 1));
                assertTrue(missing.isEmpty(), "seed " + seed + ", stage " + h + ": no termite inside could mend " + missing);
            }
        }
    }

    @Test
    void tunnelsStayFromStageToStage() {
        for (long seed : SEEDS) {
            List<Shape> stages = grown(seed);
            for (int h = 1; h < NUMBERS.maxHeight(); h++) {
                Map<BlockPos, Part> now = stages.get(h).parts();
                Map<BlockPos, Part> next = stages.get(h + 1).parts();
                for (Map.Entry<BlockPos, Part> entry : now.entrySet()) {
                    Part later = next.get(entry.getKey());
                    if (entry.getValue() == Part.TUNNEL && (later == Part.WALL || later == Part.TUNNEL)) {
                        assertEquals(Part.TUNNEL, later, "seed " + seed + ": the tunnel at " + entry.getKey() + " of stage " + h + " is soil again at stage " + (h + 1));
                    }
                }
            }
            Shape full = stages.get(NUMBERS.maxHeight());
            long tunnels = full.parts().values().stream().filter(part -> part == Part.TUNNEL).count();
            System.out.println("[mound-growth] seed " + seed + ": " + tunnels + " tunnel blocks of " + MoundPlan.walls(full) + " in the full mound");
        }
    }

    @Test
    void theShapeIsTheSameWithOrWithoutTunnels() {
        for (long seed : new long[] {1, 7}) {
            for (int h = 1; h <= NUMBERS.maxHeight(); h++) {
                Map<BlockPos, Part> plain = MoundPlan.shapeOf(h, NUMBERS, seed).parts();
                Map<BlockPos, Part> withTunnels = MoundPlan.stage(h, NUMBERS, seed).parts();
                assertEquals(plain.keySet(), withTunnels.keySet(), "seed " + seed + ", stage " + h);
                for (Map.Entry<BlockPos, Part> entry : withTunnels.entrySet()) {
                    Part was = plain.get(entry.getKey());
                    assertTrue(entry.getValue() == was || entry.getValue() == Part.TUNNEL && was == Part.WALL,
                            "seed " + seed + ", stage " + h + ": " + entry.getKey() + " was " + was + " and is " + entry.getValue());
                }
            }
        }
    }
}
