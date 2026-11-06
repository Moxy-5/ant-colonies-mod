package com.antcolonies.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.watch.Replay;
import com.antcolonies.watch.Watch;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.server.level.ServerLevel;

/**
 * The raid test bench, round 2, B3 ({@code docs/plans/raid-test-bench.md}): a saved report run as a game test. The
 * report's place, insects and colonies are put back at their own coordinates ({@link Replay}), the game's clock at the
 * report's moment, and the colonies run with every rule watching for a while; then all is taken away and put back
 * again with fresh dice, {@code runs} times in all. The test fails if any rule broke for the report's colonies in any
 * run, naming the run, the rule and the place (each breach also saves a report of its own, so a failure brings its
 * black box with it).
 *
 * <p>Each replay test has a batch of its own (nothing else runs beside it), as the game's clock is set to the
 * report's moment and back: the test's own count of ticks is kept straight across the jumps ({@link #shiftTestClock}),
 * and at the end the clock goes on from where it was before the test, plus the ticks the test ran.
 */
public final class ReplayRun {
    /** How fast the replay runs (the raid tests' own speed). */
    static final float TICK_RATE = 2000.0F;

    private ReplayRun() {
    }

    /** The timeout a replay of so many runs of so many ticks needs (ticks). */
    public static int timeoutFor(int runs, int ticksPerRun) {
        return runs * (ticksPerRun + 40) + 200;
    }

    /** Runs the replay as this test (see the class comment). */
    public static void run(GameTestHelper helper, Supplier<Replay> source, int runs, int ticksPerRun) {
        run(helper, source, runs, ticksPerRun, java.util.Set.of());
    }

    /**
     * Runs the replay as this test, counting only rules broken by colonies of {@code species} (their ids' paths, such as
     * {@code megaponera_analis}; empty: every colony). A replay made for one bug names its species, so another colony's
     * own bug in the same place (BUGS 122 in the bug 121 replay) is logged but does not fail it.
     */
    public static void run(GameTestHelper helper, Supplier<Replay> source, int runs, int ticksPerRun, java.util.Set<String> species) {
        ServerLevel level = helper.getLevel();
        Replay replay;
        try {
            replay = source.get();
        } catch (RuntimeException e) {
            helper.fail("the replay could not be read: " + e.getMessage());
            return;
        }
        level.getServer().tickRateManager().setTickRate(TICK_RATE);
        long homeGameTime = level.getGameTime();
        long startTick = helper.getTick();
        List<String> broken = new ArrayList<>();
        int[] run = {0};
        long[] runStartedAt = {-1};
        Replay.Put[] put = {null};
        boolean[] done = {false};
        int[] nearEdge = {0};
        List<String> others = new ArrayList<>();
        ColonyFrameworkMod.LOGGER.info("[replay] {}: {} runs of {} s, the report: {}", replay.name(), runs, ticksPerRun / 20, replay.why());
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            if (put[0] == null) {
                long before = level.getGameTime();
                put[0] = replay.put(level, 0x5EED_0000L + run[0] * 0x9E3779B97F4A7C15L);
                shiftTestClock(helper.testInfo, level.getGameTime() - before);
                TestClock.movingOver(helper, replay.area(), replay.dayTime());
                for (UUID id : put[0].colonies()) {
                    Colony colony = ColonyManager.get(level).getColony(id);
                    if (colony != null) {
                        Watch.watchForTests(colony);
                    }
                }
                runStartedAt[0] = helper.getTick();
                return;
            }
            if (helper.getTick() - runStartedAt[0] < ticksPerRun) {
                return;
            }
            // The run is over: what broke, then all taken away.
            for (UUID id : put[0].colonies()) {
                Colony colony = ColonyManager.get(level).getColony(id);
                if (colony == null) {
                    continue;
                }
                boolean counts = species.isEmpty() || species.contains(colony.getSpecies().getPath());
                for (Watch.Breach breach : Watch.breaches(colony)) {
                    if (!counts) {
                        others.add(colony.getSpecies().getPath() + " " + breach.rule() + " at " + breach.where().toShortString());
                        continue;
                    }
                    if (!inside(replay, breach.where()) || (WALLED_IN.equals(breach.rule()) && pocketReachesEdge(level, replay, breach.where()))) {
                        nearEdge[0]++;
                        continue;
                    }
                    broken.add("run " + (run[0] + 1) + " of " + runs + ", " + (breach.tick() - replay.gameTime()) / 20 + " s in: "
                            + breach.rule() + " at " + breach.where().toShortString() + ": " + breach.text()
                            + (breach.report() == null ? "" : " (" + breach.report() + ")"));
                }
            }
            replay.clear(level, put[0]);
            put[0] = null;
            run[0]++;
            if (run[0] < runs) {
                return;
            }
            done[0] = true;
            long before = level.getGameTime();
            level.getServer().getWorldData().overworldData().setGameTime(homeGameTime + (helper.getTick() - startTick));
            shiftTestClock(helper.testInfo, level.getGameTime() - before);
            level.setWeatherParameters(24000, 0, false, false);
            if (nearEdge[0] > 0) {
                ColonyFrameworkMod.LOGGER.info("[replay] {}: {} rule(s) broken within {} blocks of the saved box's edge, not counted (the land past it is made up)",
                        replay.name(), nearEdge[0], EDGE);
            }
            if (!others.isEmpty()) {
                ColonyFrameworkMod.LOGGER.warn("[replay] {}: {} rule(s) broken by other colonies, not this replay's bug, not counted: {}",
                        replay.name(), others.size(), String.join("; ", others.subList(0, Math.min(6, others.size()))));
            }
            if (broken.isEmpty()) {
                ColonyFrameworkMod.LOGGER.info("[replay] {}: {} runs, no rule broken", replay.name(), runs);
                helper.succeed();
            } else {
                helper.fail(broken.size() + " rule(s) broken replaying " + replay.name() + ": " + String.join("; ", broken.subList(0, Math.min(6, broken.size())))
                        + (broken.size() > 6 ? "; ..." : ""));
            }
        });
    }

    /**
     * Whether a rule broken here counts: inside the saved box, at least {@link #EDGE} blocks from its sides (and its
     * top and bottom). Past the box the land is made up (plain ground), so tunnels and nests that ran on past its edge
     * are cut off there, and an insect near the edge may be stuck or walled in only for that.
     */
    static boolean inside(Replay replay, net.minecraft.core.BlockPos where) {
        net.minecraft.world.phys.AABB box = replay.box().deflate(EDGE, Math.min(EDGE, replay.box().getYsize() / 4), EDGE);
        return box.contains(where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5);
    }

    /** The general rule an insect shut in a closed pocket breaks ({@code GeneralRules}). */
    static final String WALLED_IN = "not-walled-in";

    /**
     * Whether the open cells round {@code where} (those an insect could stand in) reach the saved box's edge: a pocket
     * that does was a tunnel running on past the box, closed only by the made-up land there.
     */
    static boolean pocketReachesEdge(ServerLevel level, Replay replay, net.minecraft.core.BlockPos where) {
        net.minecraft.world.phys.AABB inner = replay.box().deflate(1);
        java.util.ArrayDeque<net.minecraft.core.BlockPos> todo = new java.util.ArrayDeque<>();
        java.util.Set<net.minecraft.core.BlockPos> seen = new java.util.HashSet<>();
        todo.add(where);
        seen.add(where);
        while (!todo.isEmpty() && seen.size() < 20000) {
            net.minecraft.core.BlockPos at = todo.poll();
            if (!inner.contains(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5)) {
                return true;
            }
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                net.minecraft.core.BlockPos next = at.relative(side);
                if (seen.size() < 20000 && !seen.contains(next) && !level.getBlockState(next).isCollisionShapeFullBlock(level, next)) {
                    seen.add(next);
                    todo.add(next);
                }
            }
        }
        return false;
    }

    /** Blocks from the saved box's edge where rules broken are not counted ({@link #inside}; my pick). */
    static final int EDGE = 8;

    /**
     * The game's clock jumped by {@code delta} ticks: the test's start moves with it, so its own count of ticks (and its
     * timeout, and its waits) runs on unbroken ({@code GameTestInfo} counts its ticks from the game's clock).
     */
    static void shiftTestClock(GameTestInfo test, long delta) {
        if (delta == 0) {
            return;
        }
        try {
            Field field = GameTestInfo.class.getDeclaredField("startTick");
            field.setAccessible(true);
            field.setLong(test, field.getLong(test) + delta);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not keep the test's clock straight across the replay's jump", e);
        }
    }
}
