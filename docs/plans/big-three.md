# The three big jobs (the user, 2026-11-23: "a lot more compute for the project")

1. **Take the red harvester ant out of the mod.** Plan: `docs/plans/harvester-removal.md`.
2. **Rewrite the Matabele raids from scratch.** Plan: `docs/plans/raid-rewrite.md`.
3. **Review the whole mod's code, system by system.** Only the code that plays in the mod (the user, 2026-11-23): not
   the model and animation making, the tools, the filming code or the tests. Plan: `docs/plans/code-review.md`.

## The way of working from 2026-11-24 (the user; this replaces the order table below where they differ)

**Why it changed:** the first night ran six sessions at once (the removal, the raids, four review readers) and used
the user's allowance up in hours; 119 findings arrived at once, too many to follow. The aim behind all three jobs
(the user, 2026-11-24): a mod to play with friends, maybe public one day, whose code is not a bloated mess. What a
normal player notices first is tick spikes, loading time and memory.

**Where the first night left things:** the harvester removal is finished and proven on
`claude/harvester-removal-49898c` (full run, all guard films, a 2-day soak); the raid rewrite has steps 1 and 2 on
`claude/raid-rewrite-edbbde`; review rounds 7 to 10 are read (119 findings, 28 of grade A, none fixed), each on its
own `claude/review-read-...` branch.

**The order now:**

1. **The raid rewrite** (steps 3 to 6), first.
2. **The harvester removal merges** into `master` once Neon's lion work in `C:\Ant Mod` is committed, at the user's
   word.
3. **System by system, one at a time.** For each system: (a) its review findings (read the round if it is not read
   yet); (b) the user plays that part with the perf command and reports the tick spikes, which is the measurement the
   "to measure" findings wait for; (c) fixes for what both point at, grade A first; (d) that system's tests cleaned.
   Each system ends with before and after numbers: lines, tick time, loading time, memory, tests passing.
4. **Two players:** a server test with a stand-in second player holding a second area loaded, ticks measured with both
   alive; then the user's friend for the feel of it. After the optimizing, not before (the user).

**Rules:**

- **One session at a time.** No reading rounds beside other work unless the user asks. Review rounds 1 to 6 and 11 are
  read only when step 3 reaches their system.
- **The user never judges code.** Findings are sorted three ways: bugs seen in the game (fixed in small batches, with
  what to look at); faults nobody sees, lag and tidy-ups (Claude decides; tests and tick numbers prove them); vision
  calls (brought to the user as game questions, one or two at a time).
- **Only grade A findings are fixed now.** B and C stay written down and are fixed when that system is next worked on.
- **Tests are Claude's job.** The user hears about a test only when it found a real bug in the game. Chance failures
  are handled and noted without asking; each system's pass finds the cause of its chance failures so the list shrinks
  to none, and looks at its slow tests. No check that once caught a real bug is deleted.
- **Memory is measured as a normal player has it:** the game set to 4 GB, the mod's share read as the F3 "Mem" line
  with the mod against plain NeoForge in the same world. (`runClient` sets no limit today, so on the user's 64 GB PC
  Java allows itself 16 GB and fills several before cleaning: the 6 GB seen in Task Manager is not the mod's need.)
- **How code gets cleaner, in this order:** delete first; rewrite a part that is past patching (tests first); write a
  thing once and share it (the animals share little today: the otter 2,900 lines, the crocodile 2,700, the shared
  animal kit 1,700); lean on the tests; one part at a time, with numbers.

**Measured 2026-11-24 on `master`, the harvester mod not counted:** 158,000 lines of Java in the two mods; about
35,000 are tests, the test kit and debug commands; about 123,000 play in the mod, about 93,000 of them real code (the
rest comments and blank lines).

## The order, and why (the first plan, 2026-11-23)

| When | What runs | Why |
|---|---|---|
| **First** | The harvester removal, alone on the framework. Beside it: the raid rewrite's steps 1 and 2 (find the causes, write the tests) and the review's reading rounds for trees, world and animals. | The removal is the shortest job and takes 13,100 lines and 66 tests away, so both other jobs then work on less. The things beside it touch none of its files. |
| **Second** | The raid rewrite's steps 3 to 6. The review reads the framework, the termites and the Matabele (not the raids), and its fix sessions follow. | The framework is clean of harvester leftovers by now. The review skips the old raid code: reviewing code about to be deleted is wasted. |
| **Last** | The review reads the new raid code before it merges. The closing full run, soak and speed numbers. | A fresh pair of eyes on the rewrite. |

## Rules across all three

- **One writer per set of files.** Each job has its own branch and worktree; reading rounds change no code, so any
  number of them can run at once. Framework changes go one at a time.
- **Nothing the user confirmed in-game changes** without asking.
- **Merges into `master` one at a time,** each after a full run, at the user's word; `C:\Ant Mod` is switched after
  each (the main-folder rule). `C:\Ant Mod` holds Neon's uncommitted lion work today: a merge waits until that is
  committed.
- **Neon's lane** (`tools/`, `references/`, model files) is touched only as each plan says, with a line on
  `docs/HANDOFF.md`.
- The animals (M9) can go on beside all this, in their own sessions, as long as they stay out of the framework while
  the removal and the framework fixes are running.
