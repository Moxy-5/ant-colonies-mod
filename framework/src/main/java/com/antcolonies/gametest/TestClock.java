package com.antcolonies.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.antcolonies.colony.Colony;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A test's own day and night (roadmap step 3a, the user's decisions of 2026-10-16). The tests share one world and its
 * one sun, but each starts at its own moment, so each test that cares about the time of day gets a clock of its own:
 * every colony in the test's arena reads it (a colony founded during the test, or the copy of a "saved and loaded"
 * check, too). A <b>moving</b> clock runs at real speed (a day is 24,000 ticks, as in the game, so the ants' walking and
 * the sun keep their real proportions; a sped-up tick rate speeds both up together); a <b>still</b> clock holds one hour,
 * for the tests that are not about the time (walking, digging, building, the young), so a night never pauses them.
 * Without a clock a test reads a still 7:00 ({@link #DEFAULT}).
 * <p>{@link #skipTo} moves the hour forward, like a player sleeping or typing {@code /time set}: the game time (hunger,
 * ages, trails fading, the door's touches) does not jump, just as in the game. Hours are clock hours (7.0 is 7:00,
 * 18.5 is 18:30); in Minecraft's day time 0 is 6:00 and 1000 ticks are an hour.
 */
public final class TestClock {
    /** What a test without a clock of its own reads: 7:00, standing still (the harvester foragers are out, termites in). */
    static final long DEFAULT = 1000;
    /**
     * Minecraft's sunset (about 18:32) and sunrise (5:28), read from its own sun ({@link Colony#isSunUp}), the very rule
     * the colonies go by. (They were written in as 12550 and 23460, but the sun sets at 12541: for those 9 ticks this
     * clock still said day while the termites were already out, and a "no foraging by day" check failed, 2026-10-16.)
     */
    private static long sunset = -1;
    private static long sunrise = -1;

    /** The parts of a day, as the colony's species keeps them (its {@code food.day_clock}, or day and night). */
    public enum Phase {
        /** A day-clock species (harvester ants): out from {@code out_from} to the midday rest. */
        MORNING,
        /** The midday rest (its length goes by the biome's heat; none in a cool biome). */
        MIDDAY_REST,
        /** Out again after the rest, until {@code in_by}. */
        AFTERNOON,
        /** In again from {@code in_by} until sunset. */
        EVENING,
        /** From sunset: a day-clock species stays in all night; a night species (Macrotermes) forages. */
        NIGHT,
        /** A night species: the end of the night ({@link Colony#HEAD_IN_FROM}), when it heads in, until sunrise. */
        BEFORE_DAWN,
        /** A night species: from sunrise to sunset, all inside. */
        DAY
    }

    private static final List<TestClock> CLOCKS = new ArrayList<>();

    private final GameTestInfo test;
    private final AABB arena;
    private final Level level;
    /** The game time and the day time when the clock was last set; it runs on from there at {@link #speed}. */
    private long setAtGameTime;
    private long setToDayTime;
    private double speed;
    /** The speed before {@link #pause}. */
    private double pausedSpeed;

    private TestClock(GameTestHelper helper, double hour, double speed) {
        test = helper.testInfo;
        arena = helper.getBounds();
        level = helper.getLevel();
        this.speed = speed;
        setDayTime(dayTimeOf(hour));
        CLOCKS.removeIf(clock -> clock.test == test || clock.test.isDone());
        CLOCKS.add(this);
    }

    /** A clock for this test that starts at this hour and runs at real speed. */
    public static TestClock moving(GameTestHelper helper, double hour) {
        return new TestClock(helper, hour, 1.0);
    }

    /** A clock for this test that stands still at this hour (until a test moves it with {@link #skipTo} or {@link #run}). */
    public static TestClock still(GameTestHelper helper, double hour) {
        return new TestClock(helper, hour, 0.0);
    }

    /** Called once when the test server starts: every colony then reads its test's clock ({@link #dayTimeAt}). */
    static void install() {
        Colony.setTestTime(TestClock::dayTimeAt);
    }

    /** The day time a colony at this spot reads: its test's clock, or {@link #DEFAULT}. */
    static long dayTimeAt(Level level, BlockPos pos) {
        CLOCKS.removeIf(clock -> clock.test.isDone());
        for (int i = CLOCKS.size() - 1; i >= 0; i--) {
            TestClock clock = CLOCKS.get(i);
            if (clock.level == level && clock.arena.minX <= pos.getX() && pos.getX() < clock.arena.maxX
                    && clock.arena.minZ <= pos.getZ() && pos.getZ() < clock.arena.maxZ) {
                return clock.dayTime();
            }
        }
        return DEFAULT;
    }

    /** The day time now (Level#getDayTime's scale; it keeps counting past 24000, one day after another). */
    public long dayTime() {
        return setToDayTime + Math.round((level.getGameTime() - setAtGameTime) * speed);
    }

    /** The clock hour now (7.5 is 7:30). */
    public double hour() {
        return (Math.floorMod(dayTime(), 24000L) / 1000.0 + 6.0) % 24.0;
    }

    /** "7:30", for the log. */
    public String describe() {
        return SpeciesProfile.DayClock.clock(dayTime()) + " (day " + Math.floorDiv(dayTime(), 24000L) + (speed == 0 ? ", still)" : ")");
    }

    private void setDayTime(long dayTime) {
        setAtGameTime = level.getGameTime();
        setToDayTime = dayTime;
    }

    /** Minecraft's day time at this clock hour (on day 0). */
    static long dayTimeOf(double hour) {
        return Math.floorMod(Math.round((hour - 6.0) * 1000.0), 24000L);
    }

    /** Forward to the next time the clock shows this hour (never backwards; now, if it shows it now). */
    public TestClock skipTo(double hour) {
        return skipToDayTime(dayTimeOf(hour));
    }

    private TestClock skipToDayTime(long timeOfDay) {
        long now = dayTime();
        setDayTime(now + Math.floorMod(timeOfDay - now, 24000L));
        return this;
    }

    /**
     * Forward to the start of this colony's next {@code phase} (nothing changes if it is in that phase now). A phase the
     * colony's species does not keep (a midday rest in a cool biome, the before-dawn of a day species) is an error.
     */
    public TestClock skipTo(Colony colony, Phase phase) {
        if (phase(colony) == phase) {
            return this;
        }
        Long start = starts(colony).get(phase);
        if (start == null) {
            throw new IllegalArgumentException("a " + colony.getSpecies() + " colony here has no " + phase);
        }
        return skipToDayTime(start);
    }

    /** The part of the day it is now for this colony's species. */
    public Phase phase(Colony colony) {
        Map<Phase, Long> starts = starts(colony);
        long time = Math.floorMod(dayTime(), 24000L);
        Phase now = null;
        long latest = -1;
        Phase last = null;
        long lastStart = -1;
        for (Map.Entry<Phase, Long> start : starts.entrySet()) {
            if (start.getValue() <= time && start.getValue() > latest) {
                latest = start.getValue();
                now = start.getKey();
            }
            if (start.getValue() > lastStart) {
                lastStart = start.getValue();
                last = start.getKey();
            }
        }
        return now != null ? now : last; // before the first start of the day: still the last part of yesterday
    }

    /** When each part of the day begins (day time, 0 to 24000) for this colony's species and biome. */
    private Map<Phase, Long> starts(Colony colony) {
        Map<Phase, Long> starts = new EnumMap<>(Phase.class);
        Optional<SpeciesProfile.DayClock> clock = colony.profile().food().dayClock();
        if (clock.isPresent()) {
            SpeciesProfile.DayClock day = clock.get();
            float heat = colony.heat(level);
            starts.put(Phase.MORNING, (long) day.outFrom());
            if (day.restTicks(heat) > 0) {
                starts.put(Phase.MIDDAY_REST, (long) day.restStart(heat));
            }
            starts.put(Phase.AFTERNOON, (long) (day.restStart(heat) + day.restTicks(heat)));
            starts.put(Phase.EVENING, (long) day.inBy());
            starts.put(Phase.NIGHT, sunset());
        } else {
            starts.put(Phase.DAY, sunrise());
            starts.put(Phase.NIGHT, sunset());
            starts.put(Phase.BEFORE_DAWN, Colony.HEAD_IN_FROM);
        }
        return starts;
    }

    private long sunset() {
        findSunEdges();
        return sunset;
    }

    private long sunrise() {
        findSunEdges();
        return sunrise;
    }

    /** The first day time the sun is down, and the first it is up again after the night (once; every test shares one sun). */
    private void findSunEdges() {
        if (sunset >= 0) {
            return;
        }
        for (long time = 1; time < 24000; time++) {
            boolean up = Colony.isSunUp(level, time);
            if (sunset < 0 && !up && Colony.isSunUp(level, time - 1)) {
                sunset = time;
            } else if (sunset >= 0 && up) {
                sunrise = time;
                return;
            }
        }
    }

    /** Holds the hour (for a check), until {@link #run}. */
    public TestClock pause() {
        if (speed != 0) {
            pausedSpeed = speed;
            setDayTime(dayTime());
            speed = 0;
        }
        return this;
    }

    /** Runs on at real speed (or the speed it had before {@link #pause}). */
    public TestClock run() {
        setDayTime(dayTime());
        speed = pausedSpeed != 0 ? pausedSpeed : 1.0;
        return this;
    }
}
