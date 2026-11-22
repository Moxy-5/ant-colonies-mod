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

1. **Slow unit tests out of the everyday build.** Tag `MoundGrowthTest`, `TreeShapeTest`, `GalleryProofTest`,
   `MoundPlanTest`, `TreeShapeExport` as slow (JUnit tag); `build` skips them; a `slowTest` task runs them, and the
   full run and any change to mound, tree or gallery code run it. Never delete a check. Goal: an animal change builds
   in about 1 min instead of 4. Check: time `./gradlew build` after a one-line savanna change, before and after.
2. **Split tests by length.** The runs already log `[test-time] <name> ... ms`; keep a small file of each test's last
   time (written by `run_game_tests.sh`), and `keepShard` deals the longest first to the least-loaded server (unknown
   tests count as average). Goal: full run about 23 min instead of 40; `river,worldgen` about 9 min instead of 16.
   Check: one full run before and after, compare the busiest and idlest server.
3. **Later, if wanted:** use `film.sh watch` by default for film work (saves about 40 s a film); overnight full run and
   soak with a short morning report.
