# Roadmap step 3a, part 1: a shared test clock for the game tests (plan, 2026-10-16, waiting for the user's OK)

Goal (user's decision, 2026-10-16): one shared day/night clock for the game tests that really moves through the
morning, the midday rest, the afternoon and the night, in place of each test freezing the time and patching around it.
Out of scope: the `AntNavigator` clean-up (3a part 2) and any change to how the game behaves for a player.

## How time works in the tests today

- **The test server's sun moves** (Minecraft's own test server keeps the daylight cycle on and only stops the
  weather). All tests run in one shared world, so they all share that one sun, and each test starts at a different
  moment of it.
- **Harvester ants never see that sun.** `TestFilter.apply` calls `Colony.setTimeOnTestServer(1000L)`: every day clock
  (`food.day_clock`, read through `Colony.timeOfDay`) reads a frozen 7:00 on the test server. A test can set its own
  frozen hour with `colony.setClockForTests(dayTime, heat)`.
- **Termites do see that sun** (`Colony.isForagingTime` uses `level.isDay()` and `isBeforeDawn(level)` for species
  without a day clock), so a termite test that sets nothing gets a day or a night depending on when it happened to
  start in the run. Nearly every termite test therefore forces the answer with `colony.setForagingTimeForTests(true or
  false)` ("night" / "day"), or `colony.setSkyForTests(day, rain)`.
- **Patrols are off on the test server** unless a test turns them on (`patrol().setOnForTests(true)`). Not a clock
  workaround as such (it keeps the older tests free of the 4b patrol wait), so it stays as it is (see "Not changed").

### Every place, and the workaround each needs

| Test (category) | What it does with the time | Workaround |
|---|---|---|
| `NestTests.workersCarrySeedsAndClipGrassIntoTheFoodStore` (antnest) | frozen 7:00; patrol on | `NEXT_ROUND_AFTER` (3000 ticks with nobody out → `patrol().newRoundForTests()`): the next foraging round never comes by itself. Still has the `[seed-test-dbg]` debug lines. |
| `NestTests.workersDigTheWholeNestAndBuildTheMound` (antnest) | `setClockForTests(18000)` once 15 cells are dug, for 1500 ticks, then `setClockForTests(null)` | a made-up 1500-tick "night" (a real one is 13,000 ticks). **Another session is changing this night part now (3000-tick midnight, Loose Soil piles): done last, expect a merge.** |
| `AwayTests.antNumbersForageOnlyOutOfSightAndFromWhatTheyRemember` (away) | `setClockForTests` to midnight, 7:00 + rain, 8:00 in a hot biome, each held 300 ticks; patrol on + `newRoundForTests()` | jumps the hour back and forth (8:00 after midnight on the same "day"); forces a new patrol round. |
| `AwayTests.termiteNumbersForageKnownDryGrassOnlyAtNightOrInTheRain` (away) | `setForagingTimeForTests(false/true/null)`, then `setSkyForTests(day, rain)` | forced day/night. |
| `AwayTests` termite comb, starving pair, and two more (away) | `setForagingTimeForTests(false)` | forced "day" for the whole test. |
| `TrailTests.foragersWearLastingTrunkTrailsThatFadeAndGrowBack` (colony) | patrol on + `newRoundForTests()` (also on the saved-and-loaded copy) | forces a new round. |
| `PopulationTests` 2 termite tests (colony) | `setForagingTimeForTests(false)` | forced "day". |
| `HillTests` 2 tests (hills) | `setForagingTimeForTests(true)` | forced foraging time for the whole test. |
| `TermiteForagingTests.termitesOpenTheirForagingHolesAtNightAndPlugThemByDay` (termitefood) | true → false → true by events ("night" lasts until both holes are open, "day" until all are plugged) | made-up nights and days of any length. |
| `TermiteNewHoleTests` 3 tests (termitefood) | true, then false ("the foraging is over"), then true ("a new night"); the branch/log test flips to "day" by events | made-up nights and days. |
| `TermiteRunwayTests` (termitefood) | true, then false ("day") by events | same. |
| `TermiteFoodTests` garden and hay tests (termitefood) | true ("always night here") / false | forced. |
| `TermiteRepairTests` (mound), `TermiteCathedralTests` 2 tests (mound) | false ("day: everything from inside") / true / a `night` flag | forced. |
| `TermiteSoldierTests` 3 tests, `TermiteAlarmTests` 5 tests (soldiers) | forced true/false, some flipped by events ("then go in last") | forced and made-up. |
| `StressTests.stressTermiteColony` (stress) | flips `setForagingTimeForTests` | forced. |
| `TermiteForagingTests.theNightEndsForTermitesBeforeDawn` | pure checks of `Colony.isBeforeDawn(dayTime)` | none (kept as it is). |

The code behind it (all in `colony/Colony.java`): `timeForTests`, `timeOnTestServer` / `setTimeOnTestServer`,
`setClockForTests(dayTime, heat)`, `foragingTimeForTests` / `setForagingTimeForTests`, `dayForTests` and `rainForTests`
/ `setSkyForTests`. Everything else in the game asks `Colony.isForagingTime` or `Colony.timeOfDay` (the day clock,
`Patrol`'s rounds and day count, `Door`, `FieldLife`, `ForagingHoles`, `Soldiers`, `Mound`, `Nest`, the goals), so one
change there reaches all of it.

## The shared clock

A new helper `gametest/TestClock`. Each test gets its own clock (tests share one world but each starts at a different
moment, so they cannot share Minecraft's one sun). Every colony inside a test's arena reads that test's clock, found by
where the colony is: so a colony founded during the test, or the copy made by a "saved and loaded" check, reads it too
without being told.

```java
TestClock clock = TestClock.moving(helper, 7.0);  // 7:00 and running at real speed
TestClock clock = TestClock.still(helper, 0.0);   // midnight, standing still (for tests not about the time)
clock.skipTo(19.0);                               // forward to the next 19:00 (never backwards), like sleeping in a bed
clock.skipTo(colony, TestClock.Phase.MIDDAY_REST);// forward to the start of the next midday rest of this colony
clock.hour();  clock.phase(colony);               // for checks and the log
clock.pause(); clock.run();                       // hold the hour for a check, then go on
```

- **Hours** are written as clock hours (7.0 is 7:00, 18.5 is 18:30) and turned into Minecraft day time (0 is 6:00,
  1000 ticks an hour), so tests read like the notes.
- **Phases** come from the colony's own species file, so they are right for any species: a day-clock species
  (harvester ants) has `MORNING` (out_from to the rest), `MIDDAY_REST` (its length goes by the biome's heat, as in the
  game), `AFTERNOON`, `EVENING` (in_by to sunset) and `NIGHT`; a night species (Macrotermes) has `DAY`, `NIGHT`
  (Minecraft's sunset, about 18:30) and `BEFORE_DAWN` (`Colony.HEAD_IN_FROM`, 4:00).
- **Speed:** the clock counts game ticks (`level.getGameTime()`), like the sun in the real game. The sped-up tick rates
  the tests use (`setTickRate(2000)`) make the ants and the sun go faster together, so they stay in step. Real speed:
  one day is 24,000 ticks (20 minutes of normal play); a test may choose another speed, but the plan is real speed
  everywhere (see question 1).
- **Skipping** moves only the hour, like a player sleeping or typing `/time set`: game time (hunger, ages, trail
  fading, the door's touch memory) does not jump. The game already has to cope with that, so it is a fair thing for a
  test to do.
- **Tests without a clock** read a clock standing still at 7:00 (as the harvester ants do today), for termites too: a
  termite test no longer gets a random day or night from the shared sun.
- A clock is dropped when its test passes or fails (a listener on the test), so a later test in the same spot never
  reads an old clock.

### Changes in the game code (tests only; a player's game reads exactly what it reads today)

- `Colony` gets one test hook in place of the five: the test server installs a "what time is it here" lookup (from
  `TestFilter.apply`, where `setTimeOnTestServer` is now). Without it (any normal game) `timeOfDay` is
  `level.getDayTime()` and day/night is `level.isDay()`, unchanged.
- With the hook, a night species' day or night comes from that hour by Minecraft's own sun maths (the same formula as
  `Level.updateSkyBrightness`, without the weather), and "before dawn" from that hour too.
- `setClockForTests`, `setForagingTimeForTests` and the "day" half of `setSkyForTests` go. What stays: rain per colony
  (renamed `setRainForTests`; the test world's real rain would reach every test) and a biome heat for the midday rest's
  length (`setHeatForTests`; the arenas are all plains).

## Moving the tests over (one category at a time, its tests run after each)

1. `TestClock`, the `Colony` hook, the still-7:00 default; `./gradlew build`; run `antnest` and `away` (nothing should
   change for them yet).
2. **antnest:** the seed test gets a moving clock from 7:00; `NEXT_ROUND_AFTER` and its `newRoundForTests()` go (the next
   round comes after the real midday rest, 12:36 to 13:24 in the plains, or the next morning); the `[seed-test-dbg]`
   lines go once it passes. The dig test's night: last of all, after the other session's change is merged.
3. **away:** the 4b checks skip forward (midnight, the next 7:00 in the rain, 8:00 in a hot biome) instead of setting
   the hour back; the patrol starts its round when it sees the clock open, so `newRoundForTests()` should not be
   needed. The termite numbers test walks a real night and a rainy day.
4. **colony:** the trail test skips to the next morning for its new round; the termite tests get a still day.
5. **termitefood, mound, soldiers, hills, stress:** "night" and "day" become the clock. The event-driven nights (holes,
   new holes, runways, soldiers going in last) become real nights and days (question 3).
6. Remove the old test switches from `Colony` once nothing uses them; `./gradlew build`.
7. Full run of all tests (only after asking the user that they are not playing). Then the notes: `docs/HISTORY.md`,
   `docs/DESIGN.md` (testing section), CLAUDE.md "Current status" and "Testing tips".

Every check stays: only how the time is set changes.

## Not changed (noticed, for later)

- Patrols stay off on the test server unless a test turns them on.
- The other test-only switches (`FoodMap.setTestArea`, `Alarm.setTestArea`, `knowForTests`, the "alone" batches,
  `keepOutOfColonies`) are about neighbouring tests, not the time.

## Questions for the user

1. **Speed:** real speed (a day is 24,000 ticks, the ants' walking and the sun in true proportion; recommended), or a
   faster day in the tests.
2. **Which tests get a moving clock:** the tests about the time of day (foraging, patrols, the door, the holes, the
   night soil, life while away) get a moving clock; the rest (walking, digging, building, brood) get the same clock
   standing still at the hour they need (recommended), or every test runs on a moving clock.
3. **The made-up nights:** several termite tests make "night" last until the job is done. With a real clock a night is
   about 9,450 ticks (18:33 to 4:00) and a big job takes several nights, as for real termites; the tests get longer
   timeouts (recommended). Or a test may skip ahead to the next night once everybody is in.
