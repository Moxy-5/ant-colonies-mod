package com.antcolonies.gametest;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.antcolonies.AntColoniesMod;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;

/**
 * Runs only some of the game tests, for quick checks while working: start the test server with the environment
 * variable {@code ANTCOLONIES_TESTS} set to a comma-separated list of words, and only the tests whose name (the
 * method's name in lower case, such as {@code termitesroamalloveracathedralmound}) contains one of them, or whose class
 * is named by one of them (such as {@code termiteclimbingtests}), are run. Without it every test runs, as always.
 */
public final class TestFilter {
    /** Every class with game tests (a test's own name does not say its class). */
    private static final Class<?>[] CLASSES = {NestTests.class, NavigationTests.class, TermiteTests.class, TermiteClimbingTests.class,
            TermiteFoodTests.class, TermiteGardenTests.class, TermiteForagingTests.class, TermiteMoundTests.class, TermiteTunnelTests.class, TermiteCathedralTests.class,
            TermiteRepairTests.class, JobsByAgeTests.class, TermitomycesTests.class, TermiteSoldierTests.class,
            TermiteAlarmTests.class, PopulationTests.class, TermiteNewHoleTests.class, StressTests.class, HillTests.class};

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
            for (Class<?> testClass : CLASSES) {
                if (words.contains(testClass.getSimpleName().toLowerCase(Locale.ROOT))) {
                    for (java.lang.reflect.Method method : testClass.getDeclaredMethods()) {
                        named.add(method.getName().toLowerCase(Locale.ROOT));
                    }
                }
            }
            tests.removeIf(test -> !named.contains(test.testName()) && words.stream().noneMatch(test.testName().toLowerCase(Locale.ROOT)::contains));
            AntColoniesMod.LOGGER.warn("[test-filter] ONLY running {} of {} game tests (ANTCOLONIES_TESTS={})", tests.size(), before, only);
        } catch (ReflectiveOperationException e) {
            AntColoniesMod.LOGGER.error("[test-filter] could not filter the game tests, running them all", e);
        }
    }
}
