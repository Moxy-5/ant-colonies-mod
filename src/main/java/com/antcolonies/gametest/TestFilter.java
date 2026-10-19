package com.antcolonies.gametest;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.antcolonies.AntColoniesMod;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;

/**
 * Runs only some of the game tests, for quick checks while working: start the test server with the environment
 * variable {@code ANTCOLONIES_TESTS} set to a comma-separated list of words. A word can be a category (see
 * {@link #CATEGORIES}, such as {@code termitefood}): every test of it runs. Otherwise the tests whose name (the method's
 * name in lower case, such as {@code termitesroamalloveracathedralmound}) contains the word, or whose class is named by
 * it (such as {@code termiteclimbingtests}), are run. Without it every test runs, as always.
 */
public final class TestFilter {
    /**
     * The categories (the user's idea, 2026-10-14): every test class belongs to exactly one, so a change can be checked
     * by running the category it touches. A new test class must be added to one of them.
     */
    static final Map<String, List<Class<?>>> CATEGORIES = new LinkedHashMap<>();
    static {
        // The legs: walking, climbing, ceilings, crawling through termite tunnels, reloads of climbing insects.
        CATEGORIES.put("legs", List.of(NavigationTests.class, TermiteClimbingTests.class, TermiteTunnelTests.class));
        // The harvester ant's nest: digging, the hill, seeds, bread, eggs and larvae, hunger, the dead.
        CATEGORIES.put("antnest", List.of(NestTests.class));
        // The termite mound: its blocks, the cathedral, doorways and chimney mouths, mending the nest.
        CATEGORIES.put("mound", List.of(TermiteMoundTests.class, TermiteCathedralTests.class, TermiteRepairTests.class));
        // Termite food: foraging and its holes, the garden, new holes, dead wood, runways, bare patches, mushrooms.
        CATEGORIES.put("termitefood", List.of(TermiteFoodTests.class, TermiteForagingTests.class, TermiteNewHoleTests.class,
                TermiteRunwayTests.class, TermiteBarePatchTests.class, TermitomycesTests.class));
        // Soldiers and the alarm.
        CATEGORIES.put("soldiers", List.of(TermiteSoldierTests.class, TermiteAlarmTests.class));
        // The colony: founding, jobs by age, the virtual population, the lasting trails (brain part 4).
        CATEGORIES.put("colony", List.of(TermiteTests.class, JobsByAgeTests.class, PopulationTests.class, TrailTests.class, NeighbourTests.class));
        // Colonies that live while the player is away (the brain, part 2 on): going away and coming back, eating and
        // raising the young as numbers, the termites' life while away.
        CATEGORIES.put("away", List.of(AwayTests.class));
        // Hills and uneven ground, for every planner.
        CATEGORIES.put("hills", List.of(HillTests.class));
        // The body (the animation engine): naps and the other things the server decides about how an insect holds itself.
        CATEGORIES.put("body", List.of(BodyTests.class));
        // The giant savanna trees (M7.5): their blocks, later their shapes, growth and fall.
        CATEGORIES.put("trees", List.of(TreeTests.class, TreeGrowthTests.class));
        // Performance (only when named, never in a full run).
        CATEGORIES.put("stress", List.of(StressTests.class));
    }

    private TestFilter() {
    }

    /**
     * The stress tests (performance, {@link StressTests}) are long and only measure: they run only when named, never in
     * a full run.
     */
    @SuppressWarnings("unchecked")
    private static void skipStressTests(MinecraftServer server) {
        try {
            Field field = GameTestServer.class.getDeclaredField("testFunctions");
            field.setAccessible(true);
            ((Collection<TestFunction>) field.get(server)).removeIf(test -> test.testName().startsWith("stress"));
        } catch (ReflectiveOperationException e) {
            AntColoniesMod.LOGGER.error("[test-filter] could not leave out the stress tests", e);
        }
    }

    /** Called when the server is about to start, before the test server groups its tests into batches. */
    public static void apply(MinecraftServer server) {
        String only = System.getenv("ANTCOLONIES_TESTS");
        if (!(server instanceof GameTestServer)) {
            return;
        }
        TestClock.install(); // every colony reads its test's own clock (a still 7:00 if the test has none)
        TestIsolation.install(); // each colony its own dice; a test's colonies go when it is done
        if (only == null || only.isBlank()) {
            skipStressTests(server);
        } else {
            onlyNamed(server, only);
        }
        keepShard(server);
    }

    /** Leaves only the tests named by the words of {@code ANTCOLONIES_TESTS} (see the class comment). */
    @SuppressWarnings("unchecked")
    private static void onlyNamed(MinecraftServer server, String only) {
        List<String> words = Arrays.stream(only.toLowerCase(Locale.ROOT).split(",")).map(String::trim).filter(w -> !w.isEmpty()).toList();
        try {
            Field field = GameTestServer.class.getDeclaredField("testFunctions");
            field.setAccessible(true);
            Collection<TestFunction> tests = (Collection<TestFunction>) field.get(server);
            int before = tests.size();
            java.util.Set<String> named = new java.util.HashSet<>();
            List<String> plainWords = new java.util.ArrayList<>();
            for (String word : words) {
                List<Class<?>> classes = new java.util.ArrayList<>(CATEGORIES.getOrDefault(word, List.of()));
                for (List<Class<?>> category : CATEGORIES.values()) {
                    for (Class<?> testClass : category) {
                        if (word.equals(testClass.getSimpleName().toLowerCase(Locale.ROOT))) {
                            classes.add(testClass);
                        }
                    }
                }
                if (classes.isEmpty()) {
                    plainWords.add(word); // part of a test's name
                }
                for (Class<?> testClass : classes) {
                    for (java.lang.reflect.Method method : testClass.getDeclaredMethods()) {
                        named.add(method.getName().toLowerCase(Locale.ROOT));
                    }
                }
            }
            tests.removeIf(test -> !named.contains(test.testName()) && plainWords.stream().noneMatch(test.testName().toLowerCase(Locale.ROOT)::contains));
            AntColoniesMod.LOGGER.warn("[test-filter] ONLY running {} of {} game tests (ANTCOLONIES_TESTS={})", tests.size(), before, only);
        } catch (ReflectiveOperationException e) {
            AntColoniesMod.LOGGER.error("[test-filter] could not filter the game tests, running them all", e);
        }
    }

    /**
     * Tests side by side (test health plan, 2026-10-16): {@code tools/run_game_tests.sh} starts several test servers at
     * once, each with the system property {@code antcolonies.shard} set to "i/n", and each keeps only its share of the
     * tests. Tests with a batch of their own (the long whole-colony ones) are dealt out first, then the rest class by
     * class, so the long ones and each class's tests spread over all the servers.
     */
    @SuppressWarnings("unchecked")
    private static void keepShard(MinecraftServer server) {
        String shard = System.getProperty("antcolonies.shard");
        if (shard == null || shard.isBlank()) {
            return;
        }
        String[] parts = shard.trim().split("/");
        int index;
        int count;
        try {
            index = Integer.parseInt(parts[0]);
            count = Integer.parseInt(parts[1]);
        } catch (RuntimeException e) {
            AntColoniesMod.LOGGER.error("[test-filter] antcolonies.shard should look like 2/5, was {}: running every test", shard);
            return;
        }
        Map<String, String> classOf = new java.util.HashMap<>();
        for (List<Class<?>> category : CATEGORIES.values()) {
            for (Class<?> testClass : category) {
                for (java.lang.reflect.Method method : testClass.getDeclaredMethods()) {
                    classOf.put(method.getName().toLowerCase(Locale.ROOT), testClass.getSimpleName());
                }
            }
        }
        try {
            Field field = GameTestServer.class.getDeclaredField("testFunctions");
            field.setAccessible(true);
            Collection<TestFunction> tests = (Collection<TestFunction>) field.get(server);
            List<TestFunction> order = new java.util.ArrayList<>(tests);
            order.sort(java.util.Comparator.comparing((TestFunction test) -> test.batchName().equals("defaultBatch") ? 1 : 0)
                    .thenComparing(test -> classOf.getOrDefault(test.testName(), ""))
                    .thenComparing(TestFunction::testName));
            java.util.Set<String> kept = new java.util.HashSet<>();
            for (int i = index - 1; i < order.size(); i += count) {
                kept.add(order.get(i).testName());
            }
            int before = tests.size();
            tests.removeIf(test -> !kept.contains(test.testName()));
            AntColoniesMod.LOGGER.warn("[test-filter] shard {}: running {} of {} game tests: {}", shard, tests.size(), before,
                    tests.stream().map(TestFunction::testName).sorted().toList());
        } catch (ReflectiveOperationException e) {
            AntColoniesMod.LOGGER.error("[test-filter] could not split the game tests, running them all", e);
        }
    }
}
