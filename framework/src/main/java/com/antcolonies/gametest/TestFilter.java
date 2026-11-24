package com.antcolonies.gametest;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.antcolonies.ColonyFrameworkMod;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;

/**
 * Runs only some of the game tests, for quick checks while working: start the test server with the environment
 * variable {@code ANTCOLONIES_TESTS} set to a comma-separated list of words. A word can be a category (see
 * {@link #CATEGORIES}, such as {@code termitefood}): every test of it runs. Otherwise the tests whose name (the method's
 * name in lower case, such as {@code termitesroamalloveracathedralmound}) contains the word, or whose class is named by
 * it (such as {@code termiteclimbingtests}), are run. A word starting with {@code =} picks only the test of exactly
 * that name (test revamp R1: the run script reruns a failed test alone, and a plain word would also pick every test
 * whose name holds it). Without it every test runs, as always.
 */
public final class TestFilter {
    /**
     * The categories (the user's idea, 2026-10-14): every test class belongs to exactly one, so a change can be checked
     * by running the category it touches. A new test class must be added to one of them.
     */
    static final Map<String, List<Class<?>>> CATEGORIES = new LinkedHashMap<>();

    private TestFilter() {
    }

    /**
     * Adds test classes to a category (the split, step S4: each species signs up its own, {@code SavannaTests} and
     * {@code DesertTests}; two species may share a category, such as {@code away} or {@code hills}).
     */
    public static synchronized void addCategory(String name, Class<?>... classes) {
        CATEGORIES.computeIfAbsent(name, key -> new java.util.ArrayList<>()).addAll(List.of(classes));
    }

    /**
     * Categories whose tests are made by a generator, not methods (the raid test bench's replays, round 2): a test whose
     * name starts with the prefix belongs to the category.
     */
    static final Map<String, List<String>> PREFIXES = new LinkedHashMap<>();

    /** Tests whose names start with {@code prefix} belong to this category (generated tests, such as the replays). */
    public static synchronized void addCategoryPrefix(String name, String prefix) {
        PREFIXES.computeIfAbsent(name, key -> new java.util.ArrayList<>()).add(prefix);
    }

    /**
     * Tests left out of full runs (the user, 2026-11-23: "the test that is taking the longest, make a rule to remove it from
     * future full sweep tests unless the change directly affected it"): each species signs up its own by name; they still
     * run when their category or their exact name is given ({@code ANTCOLONIES_TESTS}), the run for a change that touches them.
     */
    static final java.util.Set<String> NOT_IN_FULL_RUNS = new java.util.HashSet<>();

    /** Leaves these tests (method names) out of full runs; they run only when their category or name is asked for. */
    public static synchronized void skipInFullRuns(String... testNames) {
        for (String name : testNames) {
            NOT_IN_FULL_RUNS.add(name.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * The stress tests (performance, such as {@code TermiteStressTests}) are long and only measure: they run only when named, never in
     * a full run; nor do the tests signed up by {@link #skipInFullRuns}.
     */
    @SuppressWarnings("unchecked")
    private static void skipStressTests(MinecraftServer server) {
        try {
            Field field = GameTestServer.class.getDeclaredField("testFunctions");
            field.setAccessible(true);
            ((Collection<TestFunction>) field.get(server)).removeIf(test -> test.testName().startsWith("stress")
                    || NOT_IN_FULL_RUNS.contains(test.testName().toLowerCase(Locale.ROOT)));
            if (!NOT_IN_FULL_RUNS.isEmpty()) {
                ColonyFrameworkMod.LOGGER.warn("[test-filter] left out of this full run (run their category to check them): {}", NOT_IN_FULL_RUNS);
            }
        } catch (ReflectiveOperationException e) {
            ColonyFrameworkMod.LOGGER.error("[test-filter] could not leave out the stress tests", e);
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
                if (word.startsWith("=")) {
                    named.add(word.substring(1)); // (an exact test name; names are compared in lower case below)
                    continue;
                }
                List<Class<?>> classes = new java.util.ArrayList<>(CATEGORIES.getOrDefault(word, List.of()));
                for (List<Class<?>> category : CATEGORIES.values()) {
                    for (Class<?> testClass : category) {
                        if (word.equals(testClass.getSimpleName().toLowerCase(Locale.ROOT))) {
                            classes.add(testClass);
                        }
                    }
                }
                List<String> prefixes = PREFIXES.getOrDefault(word, List.of());
                for (String prefix : prefixes) {
                    tests.stream().map(TestFunction::testName).filter(name -> name.startsWith(prefix)).forEach(named::add);
                }
                if (classes.isEmpty() && prefixes.isEmpty()) {
                    plainWords.add(word); // part of a test's name
                }
                for (Class<?> testClass : classes) {
                    for (java.lang.reflect.Method method : testClass.getDeclaredMethods()) {
                        named.add(method.getName().toLowerCase(Locale.ROOT));
                    }
                }
            }
            tests.removeIf(test -> !named.contains(test.testName().toLowerCase(Locale.ROOT)) && !named.contains(test.testName()) && plainWords.stream().noneMatch(test.testName().toLowerCase(Locale.ROOT)::contains));
            ColonyFrameworkMod.LOGGER.warn("[test-filter] ONLY running {} of {} game tests (ANTCOLONIES_TESTS={})", tests.size(), before, only);
        } catch (ReflectiveOperationException e) {
            ColonyFrameworkMod.LOGGER.error("[test-filter] could not filter the game tests, running them all", e);
        }
    }

    /**
     * Tests side by side (test health plan, 2026-10-16): {@code tools/run_game_tests.sh} starts several test servers at
     * once, each with the system property {@code antcolonies.shard} set to "i/n", and each keeps only its share of the
     * tests. Every server deals the same way, so each test lands on exactly one.
     *
     * <p>Dealt by length (test speed step 2, docs/plans/test-speed.md, 2026-11-22): a server runs its batches one after
     * another, and the tests of one batch side by side, so the default batch costs about its longest test, while the tests
     * with a batch of their own add up (the world-map tests also add up inside their shared batch: they hold the server
     * while they build land). So a server's cost is the sum of its own-batch tests plus its longest default-batch test,
     * and the tests are dealt longest first, each to the server whose cost it raises least (a tie: the one with fewer
     * tests). But side by side is not free (one server thread ticks them all), so no server takes more than its even
     * share of the default-batch tests: the load the times were measured under (the first try without it put 63 of 80
     * tests on one server, and three of them timed out or failed in the lag). Each test's minutes come from {@code tools/test-times.txt} (system property {@code antcolonies.testTimes},
     * refreshed by the run script); a test not in it counts as {@link #UNKNOWN_MINUTES}. Before, they were dealt by batch
     * and name, blind to length: the 2026-11-22 full run took 40 minutes while the six servers' loads ran 13 to 39.
     */
    @SuppressWarnings("unchecked")
    private static void keepShard(MinecraftServer server) {
        String shard = System.getProperty("antcolonies.shard");
        if (shard == null || shard.isBlank() || "1".equals(System.getenv("ANTCOLONIES_EVERY_SHARD"))) {
            // (ANTCOLONIES_EVERY_SHARD=1: every server runs every chosen test, to repeat a flaky one n times at once)
            return;
        }
        String[] parts = shard.trim().split("/");
        int index;
        int count;
        try {
            index = Integer.parseInt(parts[0]);
            count = Integer.parseInt(parts[1]);
        } catch (RuntimeException e) {
            ColonyFrameworkMod.LOGGER.error("[test-filter] antcolonies.shard should look like 2/5, was {}: running every test", shard);
            return;
        }
        Map<String, Double> minutes = testTimes(System.getProperty("antcolonies.testTimes"));
        try {
            Field field = GameTestServer.class.getDeclaredField("testFunctions");
            field.setAccessible(true);
            Collection<TestFunction> tests = (Collection<TestFunction>) field.get(server);
            java.util.function.ToDoubleFunction<TestFunction> length = test -> minutes.getOrDefault(test.testName().toLowerCase(Locale.ROOT), UNKNOWN_MINUTES);
            List<TestFunction> order = new java.util.ArrayList<>(tests);
            order.sort(java.util.Comparator.comparingDouble(length).reversed().thenComparing(TestFunction::testName));
            double[] ownBatches = new double[count]; // the sum of each server's own-batch tests
            double[] longestDefault = new double[count]; // each server's longest default-batch test
            int[] dealt = new int[count];
            int[] dealtDefault = new int[count];
            int fairShare = (int) Math.ceil(order.stream().filter(test -> test.batchName().equals("defaultBatch")).count() / (double) count);
            java.util.Set<String> kept = new java.util.HashSet<>();
            for (TestFunction test : order) {
                double time = length.applyAsDouble(test);
                boolean inDefault = test.batchName().equals("defaultBatch");
                int best = -1;
                double bestCost = Double.MAX_VALUE;
                for (int k = 0; k < count; k++) {
                    if (inDefault && dealtDefault[k] >= fairShare) {
                        continue;
                    }
                    double cost = inDefault ? ownBatches[k] + Math.max(longestDefault[k], time) : ownBatches[k] + time + longestDefault[k];
                    if (cost < bestCost - 1e-9 || (Math.abs(cost - bestCost) <= 1e-9 && dealt[k] < dealt[best])) {
                        best = k;
                        bestCost = cost;
                    }
                }
                if (inDefault) {
                    longestDefault[best] = Math.max(longestDefault[best], time);
                    dealtDefault[best]++;
                } else {
                    ownBatches[best] += time;
                }
                dealt[best]++;
                if (best == index - 1) {
                    kept.add(test.testName());
                }
            }
            int before = tests.size();
            tests.removeIf(test -> !kept.contains(test.testName()));
            ColonyFrameworkMod.LOGGER.warn("[test-filter] shard {}: running {} of {} game tests (about {} min by tools/test-times.txt; {} of all the chosen tests had no time there): {}",
                    shard, tests.size(), before, String.format(Locale.ROOT, "%.1f", ownBatches[index - 1] + longestDefault[index - 1]),
                    order.stream().filter(test -> !minutes.containsKey(test.testName().toLowerCase(Locale.ROOT))).count(),
                    tests.stream().map(TestFunction::testName).sorted().toList());
        } catch (ReflectiveOperationException e) {
            ColonyFrameworkMod.LOGGER.error("[test-filter] could not split the game tests, running them all", e);
        }
    }

    /** The minutes a test not in {@code tools/test-times.txt} counts as (a new test; most tests take well under one). */
    private static final double UNKNOWN_MINUTES = 1.0;

    /**
     * Each test's minutes from the times file: one "name minutes" line per test, {@code #} starts a comment; a name
     * written twice (two branches' times kept by git's union merge) counts by its last line. No file: an empty map
     * (every test then counts the same, and the deal is still even by count).
     */
    private static Map<String, Double> testTimes(String file) {
        Map<String, Double> minutes = new java.util.HashMap<>();
        if (file == null || file.isBlank()) {
            return minutes;
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(file))) {
                String[] words = line.strip().split("\\s+");
                if (words.length == 2 && !words[0].startsWith("#")) {
                    try {
                        minutes.put(words[0].toLowerCase(Locale.ROOT), Double.parseDouble(words[1]));
                    } catch (NumberFormatException e) {
                        // (not a time: skipped)
                    }
                }
            }
        } catch (java.io.IOException e) {
            ColonyFrameworkMod.LOGGER.warn("[test-filter] no test times in {}: dealing the tests by count", file);
        }
        return minutes;
    }
}
