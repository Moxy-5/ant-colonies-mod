# Test health: faster runs and tests that break less (plan, 2026-10-16, OK'd by the user the same day)

The problem (user, 2026-10-16): every new feature costs about 40 minutes of game tests, and the tests break whenever a
feature lands, often for no real bug. Comes before the fights (M8), next to roadmap 3a.

## Why the tests break and run long

- **The pile is upside down.** About 110 game tests (each a real headless Minecraft with a living colony, 41 minutes for
  a full run) and only 4 quick tests (no Minecraft, seconds). Professionals keep many quick tests for the rules and
  few whole-game tests for the scenes (the "test pyramid").
- **Exact numbers from a living colony.** "Expected [3, 2, 1] jobs", "expected 35 seeds": a whole colony is chaotic,
  so any new feature (night soil, the clock) changes these numbers even when the rule being checked is fine.
- **Every test runs everything.** A job test also runs hunger, night soil, digging, the clock, and first waits for the
  whole nest to be dug.
- **One test server runs everything on one thread.** In the quick run of 2026-10-16, 50 colonies ran side by side in
  one batch on one server thread, after the seed test's own 11-minute batch. The PC has 20 threads and 64 GB.
- **99 "ForTests" switches** in the game code: the code reaches out for the time, the weather and the sky itself,
  where it could be handed them.

## The 4-box rule (what to do when a game test fails)

1. **A real bug:** fix the game, add the lesson to `docs/BUGS.md`.
2. **The behaviour changed on purpose:** update the test (say so in `docs/HISTORY.md`).
3. **The test was too strict** (an exact number from a living colony): loosen the check into the rule it means
   (a range, "no seed vanishes", "more food, more foragers").
4. **Flaky or a neighbour:** passes alone, fails side by side or 1 run in n. Put it on the quarantine list in
   `docs/BUGS.md` with what is known; it does not block a merge, and gets fixed later.

## Order (user's pick, 2026-10-16: the first four now, "good for now")

Done 2026-10-16 (steps 1 to 3; details in `docs/HISTORY.md`, "Test health, part 1", and `docs/BUGS.md` 32):

1. **The 5 failures of the quick run** (`antnest,away,colony,hills`, 2026-10-16): run each alone, sort them into the
   4 boxes, fix. Found: all 5 passed alone; three leaks between tests (shared dice, leftover colonies, counting
   boxes), fixed in `gametest/TestIsolation`.
2. **Tests side by side:** split the game tests over several test servers at once (shards), each in its own folder
   and world, with one script that starts them, waits, and lists every failure. Long tests with their own batch are
   dealt out first so they don't all land on one server.
3. **Every test on GitHub:** GitHub's computers run all the game tests when a branch is pushed (CI, "continuous
   integration"), so the full run no longer needs the user's PC. Private repositories get about 2,000 free minutes a
   month.
4. **Later (not started, ask first):** the quick-test kit for the colony's rules (start with the ones that broke:
   jobs, feeding the queen, the seed count, the numbers foraging), the scene kit ("a colony with 6 workers and the
   nest already dug"), fixed dice (seeded randomness), loosening exact numbers, and handing the clock and weather in
   instead of the ForTests switches.
