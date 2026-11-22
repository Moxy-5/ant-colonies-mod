# Test speed (measured 2026-11-22, the user: "put your findings in Claude as the next part of the plan")

Why: the user gets about a month of a bigger Claude plan; testing is what slows each new addition. Measured from
about 90 past test runs (`run-test-shards/*/logs`, archived logs included), 177 film launches since 2026-11-15
(`run-film/logs`) and one timed `./gradlew build`. The measuring script was a one-off; rebuild it from the method below if needed.

## Findings

| What | Time | How often |
|---|---|---|
| `./gradlew build` after a savanna change | about 4 min (3 m 51 s measured) | every change |
| Game tests for one category (`river`, `mud`, ...) | about 1 min, about 30 s of it server start | most changes |
| A film launch (`runFilm`) | about 1 min, about 40 s before the first film line | about 25 a day |
| Full run (289 tests, 6 servers) | 40 min | every 3 plan steps, before merges |

1. **The build is the biggest cost per change.** Of its ~4 min, about 3.5 min is the savanna unit tests, and they rerun
   after any savanna change (Gradle reruns tests when the classpath changes). `MoundGrowthTest` alone is 147 s (8
   tests), `TreeShapeTest` 24 s, `GalleryProofTest` 15 s, `MoundPlanTest` 9 s, `TreeShapeExport` 5 s; every other unit
   test class together is a few seconds. An animal change waits about 3 min on termite and tree checks it cannot move.
2. **Full runs are unevenly split.** `TestFilter.keepShard` deals tests out by batch, then class and name, not by
   length. Last full run (2026-11-22 10:27): servers busy 1.5, 23.4, 17.1, 28.7, 39.2 and 17.1 min (46% idle). Split
   by length it would finish in about 22-25 min (the longest single tests are about 12-13 min:
   `termitesopentheirforagingholesatnightandplugthembyday`, `afallenlogandthestumponitareeatenfrombelowbyday`,
   `workerscloseeveryholeinthenestwallsandmenddamage`, `riverwidthismeasured`). Category runs have the same problem:
   `river,worldgen` took 16 min because one server got `riverwidthismeasured` (8.8 min) while the others finished in 2-6.
3. **Server and game start-up**: about 30 s per test server and about 40 s per film launch. `tools/film.sh watch`
   (camera kept open) already avoids the film one but is little used.
4. **The rest is the number of tries, not the speed of each** (e.g. the mud tests ran about 10 times in one evening).
   That is normal work; parallel sessions in separate areas are the answer there.

## Steps (next, before or at the start of the bigger plan)

**Rethought 2026-11-22 after a closer look at the logs (the user: "think about the fix for both").**

**Steps 1 and 2 built 2026-11-22** (details and numbers in `docs/HISTORY.md`): the build after a savanna change 50 s (step 1 changed in building: one test program with the classes side by side, not 4 programs, which clashed on `config/fml.toml`); `river,worldgen` 11 min 20 s (step 2 changed in building: each server takes at most its even share of the default-batch tests, as side by side is not free). The full run's time is still to be seen.

1. **Unit tests side by side, not skipped.** Each slow test is a loop over many seeds done one at a time
   (`MoundGrowthTest.withSurprisesOnTheWay` 52 s, `everyStageOfManyMoundsCanBeGrownFromInside` 38 s,
   `TreeShapeTest.everyLimbLookStandsAndGrows` 21 s, `GalleryProofTest.wholeWebs...` 10 s), while the PC has 20 threads.
   Run each seed loop on all cores (as `manyMoreMoundsCanBeGrownFromInside` already does), swap the mound cache's
   one big lock for a per-seed one, and let JUnit run the test classes side by side. Every check still runs in
   every build, so there is no "remember to run the slow tests" rule. Only the 200-random-seed sweep
   (`manyMoreMounds...`, 31 s and already on all cores, so it cannot get faster) gets a `slow` tag: the everyday
   build skips it and `run_game_tests.sh` runs it by itself in every full run (the 20 fixed seeds still check
   every stage in every build). Expected (a guess until timed): the test part about 15 s instead of 3.3 min, the
   build about 45 s to 1 min instead of 4. Check: time `./gradlew build` after a one-line savanna change, before and after.
2. **Split game tests by length, with the right sum.** Found in the 10:27 full run: a server runs its batches one
   after another, and inside a batch the tests run side by side, so the default batch costs only its longest test
   (server 4's 38 default tests took 13 min, its longest 12.7). The world-map tests (`savanna_maps`) block the server
   while they build land, so they add up (server 5: `riverwidthismeasured` 10.8 + `oxbowandpanroomismeasured` 7.0
   + 2.6 = its 20-minute batch; that pair on one server made the run 40 min). So a server's cost is: the sum of
   its own-batch tests plus the longest of its default tests. `keepShard` deals the longest test first to the server
   whose cost it raises least (ties: fewer tests). Times from a small committed file `tools/test-times.txt`
   (one "name minutes" line per test), refreshed by `run_game_tests.sh` from the `[test-time]` lines of each run;
   `merge=union` so branches never clash, the reader takes the last line for a name; an unknown test counts
   as 1 minute. Simulated on the 10:27 run's own times: every server 18.6 to 19.8 min, so about 21 min with
   start-up instead of 40 (floor: the longest test, 12.7 min). Check: one full run, compare with the simulation.
   Note: server 1's "1.5 min busy" in that run was the raid crash (BUGS 281, also 198), not idle time; it lost
   21 tests. Fixing it is its own work.
3. **Later, if wanted:** use `film.sh watch` by default for film work (saves about 40 s a film); overnight full run and
   soak with a short morning report.
