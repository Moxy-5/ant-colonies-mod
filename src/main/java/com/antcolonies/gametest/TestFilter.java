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
        CATEGORIES.put("colony", List.of(TermiteTests.class, JobsByAgeTests.class, PopulationTests.class, TrailTests.class));
        // Colonies that live while the player is away (the brain, part 2 on): going away and coming back, eating and
        // raising the young as numbers, the termites' life while away.
        CATEGORIES.put("away", List.of(AwayTests.class));
        // Hills and uneven ground, for every planner.
        CATEGORIES.put("hills", List.of(HillTests.class));
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
    @SuppressWarnings("unchecked")
    public static void apply(MinecraftServer server) {
        String only = System.getenv("ANTCOLONIES_TESTS");
        if (!(server instanceof GameTestServer)) {
            return;
        }
        if (only == null || only.isBlank()) {
            skipStressTests(server);
            return;
        }
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
}
