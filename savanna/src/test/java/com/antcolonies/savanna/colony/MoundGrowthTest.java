package com.antcolonies.savanna.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.LongConsumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.antcolonies.savanna.colony.MoundPlan.Part;
import com.antcolonies.savanna.colony.MoundPlan.Shape;
import com.antcolonies.savanna.species.SavannaNumbers.MoundNumbers;

import net.minecraft.core.BlockPos;

/**
 * The "can it be built" test (tunnel plan step 4, the user's request): every stage of many mounds is built in a
 * pretend world by the termites' own rules ({@link MoundGrowth}), from inside only (the shaft and the tunnels), and must
 * be finished without ever getting stuck: stage after stage (as the game does: one stage after the other), with the
 * jobs done in a random order, and with surprises on the way (some blocks of every stage cannot be worked on, and the
 * termites move on without them). A stuck block fails the test and is named. Also: every outside block can be mended
 * from inside, and tunnels, once there, stay.
 *
 * <p>Each check runs its colonies side by side on all the computer's threads (test speed step 1, 2026-11-22: one after
 * another the class took about 2.5 minutes of every build). Growing a mound touches nothing shared, so the colonies cannot
 * get in each other's way; a failure still names its seed.
 */
class MoundGrowthTest {
    private static final MoundNumbers NUMBERS = new MoundNumbers(32, 2.0F, 0.5F, 3, 13);
    /**
     * The usual six, and more colonies picked at random once (fixed, so a failure can be looked at again), and seven a
     * sweep of thousands found stuck or not mendable (docs/BUGS.md 82: a new spire starting on old soil, a lump of soil
     * over a gap).
     */
    private static final long[] SEEDS = {1, 7, 42, 2024, -987654321L, 31337, 5, 99, 123456789L, -42, 777, 2718281828L, 31415926L,
            -1L, 8675309L, 4242, 1999, 65536, -7777777L, 13, 6746626102635787809L, -2256830643955015227L, -8951133505651067036L,
            997394442843989895L, 4619323497592152298L, -3070628618211979387L, -7929930569154781686L};
    /** How many more colonies, picked at random (fixed), the sweep grows (side by side; about 1 in 80 was stuck before BUGS 82). */
    private static final int SWEEP = 200;

    /**
     * Each colony's stages, grown once for all the tests here (growing takes a moment). One task per colony, so two
     * colonies grow side by side and a second test asking for a colony being grown waits for that one only.
     */
    private static final Map<Long, FutureTask<List<Shape>>> GROWN = new ConcurrentHashMap<>();

    private static List<Shape> grown(long seed) {
        FutureTask<List<Shape>> task = GROWN.computeIfAbsent(seed, s -> new FutureTask<>(() -> MoundPlan.growth(NUMBERS, s)));
        task.run(); // grows it the first time; does nothing once grown or while another thread is growing it
        try {
            return task.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Runs the check for every colony of {@link #SEEDS} side by side; the first failure stops the test and names its seed. */
    private static void eachSeed(LongConsumer check) {
        Arrays.stream(SEEDS).parallel().forEach(check);
    }

    private static List<Shape> allStages(long seed) {
        return grown(seed).subList(1, NUMBERS.maxHeight() + 1);
    }

    @Test
    void everyStageOfManyMoundsCanBeGrownFromInside() {
        eachSeed(seed -> {
            List<String> problems = MoundGrowth.problems(NUMBERS.maxHeight(), h -> MoundPlan.shapeOf(h, NUMBERS, seed));
            assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems);
        });
    }

    /** Slow (31 s on all threads, so it cannot get faster): left out of the everyday build, run by every full run ({@code -PslowTests}). */
    @Test
    @Tag("slow")
    void manyMoreMoundsCanBeGrownFromInside() {
        Random random = new Random(82);
        long[] seeds = java.util.stream.LongStream.range(0, SWEEP).map(i -> random.nextLong()).toArray();
        List<String> problems = java.util.Arrays.stream(seeds).parallel()
                .mapToObj(seed -> MoundGrowth.problems(NUMBERS.maxHeight(), h -> MoundPlan.shapeOf(h, NUMBERS, seed)).stream().map(p -> "seed " + seed + ": " + p).toList())
                .flatMap(List::stream).toList();
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void stageAfterStageTheTunnelsAreEnough() {
        eachSeed(seed -> {
            List<MoundGrowth.Stuck> stuck = MoundGrowth.check(allStages(seed), null);
            assertTrue(stuck.isEmpty(), "seed " + seed + ": stuck at " + stuck);
        });
    }

    @Test
    void inAnyOrder() {
        eachSeed(seed -> {
            List<MoundGrowth.Stuck> stuck = MoundGrowth.check(allStages(seed), new Random(seed));
            assertTrue(stuck.isEmpty(), "seed " + seed + ": stuck at " + stuck);
        });
    }

    @Test
    void withSurprisesOnTheWay() {
        eachSeed(seed -> {
            Random random = new Random(seed * 31 + 1);
            for (double share : new double[] {0.01, 0.05}) {
                List<MoundGrowth.Stuck> stuck = MoundGrowth.checkWithSurprises(allStages(seed), random, share);
                assertTrue(stuck.isEmpty(), "seed " + seed + ", " + share + " of every stage blocked: stuck at " + stuck);
            }
        });
    }

    @Test
    void everyOutsideBlockCanBeMendedFromInside() {
        eachSeed(seed -> {
            for (int h : new int[] {8, 20, 32}) {
                List<BlockPos> missing = MoundGrowth.unreachableOutside(grown(seed).subList(1, h + 1));
                assertTrue(missing.isEmpty(), "seed " + seed + ", stage " + h + ": no termite inside could mend " + missing);
            }
        });
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
                    boolean soilKept = (was == Part.SPIRE || was == Part.MOUTH) && (entry.getValue() == Part.WALL || entry.getValue() == Part.TUNNEL);
                    assertTrue(entry.getValue() == was || entry.getValue() == Part.TUNNEL && was == Part.WALL || soilKept,
                            "seed " + seed + ", stage " + h + ": " + entry.getKey() + " was " + was + " and is " + entry.getValue());
                }
            }
        }
    }
}
