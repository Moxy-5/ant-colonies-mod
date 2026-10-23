# Project Apex, part 3: the termites (planned 2026-10-23)

Parts 1 (the brain) and 2 (the split) are done and merged. Part 3 reshapes the termites' own code in the savanna mod
(and the few termite pieces still in the framework), so the Matabele ants (M8) can be built on it quickly, it runs
fast, and bugs are less likely. **Nothing changes in-game.** Same pace as the split (the user, 2026-10-23: "I'm hoping
this won't take too long"): rounds, one full run each on 6 servers, the next round started while a run goes, **one
in-game test at the end (termites, a new world, about 30 minutes), then one merge into `master`**. About a day.

## What the survey found (2026-10-23, on `master` after the split)
- `framework/.../species/SpeciesProfile.java` (1,563 lines) reads every species' number groups: the termites' mound,
  galleries, garden, laying, queen body, soldiers, alarm, sheeting, new holes, wood call, regrowth; the harvester's
  door, patrol, search, recruits, trunk trails, night soil. A new species' numbers (the Matabele's raid size, scouts)
  would mean an edit in the framework. (`apex.md` step 12.)
- `savanna/.../colony/Galleries.java` (2,236 lines) is one part doing four jobs, with about 20 saved things: the web's
  shape and growth (arms, tips, forks, growing toward food), which holes open each night (turns), upkeep (unused far
  branches filled in, pieces walled off against neighbours), and catching up after coming back. The two old flaky
  termite tests (`docs/BUGS.md` 55 and 62) are both gallery scenes.
- About 100 lines in shared framework files still do termite-only work: the feeding galleries under food in
  `ForagingHoles` (29 lines) and `FoodWays` (21), the gallery and mud cast cells in `Nest` (20), the mound passes in
  `BuildLife` (14), the comb in `FoodStore` (7). The harvester ant uses none of them (the desert mod never names
  `ForagingHoles` or `FoodWays`).
- The shared body (`framework/.../entity/AbstractAntEntity.java`, 1,863 lines) keeps every carried load as its own
  synced flag and its own code (carried block, seed, bread, rich bread, egg, body, ...). The Matabele ants will carry
  dead termites and hurt nestmates: today that is two more flags and two more switches in the framework.
- The termite stress test shows bursts of failed route searches about once a second for a minute (800 to 1,300 steps
  each, up to 27 ms), cause not found (`apex.md`, "Also found 2026-10-20").
- `docs/BUGS.md` 65 (the alarm stuck on after a chicken fell into a hole): fixed in its own session and merged into
  `master` 2026-10-23, merged into this branch before T1.

## The steps
- **T1, each species' numbers read by its own part** (`apex.md` step 12). Each number group's reader moves next to its
  part (termite groups into the savanna mod, harvester groups into the desert mod); a part's row in `ColonyParts`
  brings the reader for its own section of the species file. The shared groups (colony, workers, queen, brood, food,
  nest, danger, neighbours, body language) stay in the framework. The species files keep their exact layout; a wrong
  name still stops the game loading with a message. Check: every number read before and after is the same (a unit
  test reads both species files and compares).
- **T2, the galleries in four parts, and the last termite work out of the framework** (`Galleries` 2,236 lines): the
  web (shape, tips, forks, growth toward food), the holes' turns, the upkeep (filling in, walling off), the catch-up
  after coming back; one small hub keeps today's questions and hooks answered; save names unchanged. With it, as both
  are gallery work: the feeding galleries and mud casts behind the existing `FeedingWays` socket in the savanna mod,
  the mound passes in `BuildLife` through the build hook, the comb's line in `FoodStore` through the garden. Foraging
  holes themselves stay shared (many real species have several entrances: leafcutters, later termites of other
  mods). Check: the gallery proof (400 made-up landscapes), `termitefood,away,hills`, and `grep` finds no
  termite-only word in framework code lines outside comments and the test kit.
- **T3, one "what I carry" slot in the shared body:** every carried load (soil pellet, seed, bread, egg, body, forage,
  nodule, ...) one row in a list the species fill, one synced slot, one save; the renderer draws the row's look. A
  new load (a dead termite, a hurt nestmate) is then a new row in the species mod. Save names kept so a carried load
  survives a reload. Check: the guard films (15 of 15 picture for picture the same) and `legs,soldiers,termitefood`.
  **BUILT 2026-10-23** (branch `claude/apex-t3-t4-2ba1d3`): `entity/load/LoadKind` (the rows), `CarriedLoad` (the one
  slot), `FrameworkLoads` (the framework's five), `client/LoadLooks` and `CarriedLoadLayer` (the looks); save words kept.
- **T4, the route search bursts:** find why the termite stress test searches and fails in bursts, and fix it the way
  `docs/BUGS.md` 42 did (remember a target with no way and leave it alone for a while). Speed measured with
  `/antcolonies perf` and the stress test, before and after (6 runs a side). If the fix would change anything you
  could see (an insect giving up on work it does today), I stop and ask first.
  **BUILT 2026-10-23** (same branch): the main source was `ShapeMoundGoal`, a tunnel cell at the mound's foot that no
  termite can carve from inside, searched for by every builder in turn every 20 s; now the legs know how close the
  work places lie (`AntNavigator.moveTo` with a reach) and skip a search that cannot succeed, same result, no change
  in behaviour. **Found, not fixed (your call below):** a nurse cut off from the queen (`StandByQueenGoal`) tries
  again every 2 seconds, four failed full searches each time; the bursts left in the stress test are mostly hers.
- **T5, the full check, with the two old gallery flakes** (`docs/BUGS.md` 55 and 62): with the galleries split, find
  why they fail beside other tests (box 4 of the 4-box rule: a leak between tests, not a loosened check), so every
  full run passes clean with no reruns alone; then the full run on 6 servers, the stress test and `/antcolonies perf`
  against `master`, guard films, anim lab, the notes; then the user's in-game test and the merge.

**Rounds** (the user, 2026-10-23: fewer steps where two quick ones go together; 7 steps folded into 5): 1 T1 + T2;
2 T3 + T4; 3 T5. One full run per round.

**Where each step runs (the user, 2026-10-23):** T3 and T4 in a linked session on their own branch (`apex-t3-t4`), merged into `claude/apex-termites` before T5; T1, T2 and T5 here.

**T1 BUILT 2026-10-23** (`SpeciesSection`, `SavannaNumbers`, `DesertNumbers`; every number the same before and after).
**T2 BUILT 2026-10-23** (`Galleries` a hub of 455 lines, with `GalleryWeb`, `HoleTurns`, `GalleryUpkeep`, `GalleryCatchUp`; the feeding galleries, casts, mound building and comb were already behind sockets since the split, two termite names renamed; king, mound and sheeting kept as shared words, real in other species too).
**T3 + T4 merged 2026-10-23** (from `claude/apex-t3-t4-2ba1d3`). **T5 BUILT 2026-10-23**: BUGS 55 fixed (a young colony that yields walls its piece off), BUGS 62 and the new 68 now name why when they fail; full run 142 of 143 (68); speed the same as `master`, failed route searches halved; guard films and anim lab unchanged. Waiting for the user's in-game test, then the merge.

**Not in part 3:** the shared fight system and a shared "defender" (M8 builds it with the Matabele ant as the first
fighter; the termite soldiers join it then); `ScoutGoal` in three (errand helper step 4, part 4 or M8's scouts); the
legs tests on a savanna body (`apex.md` step 13, before the harvester ant leaves); new termite behaviour of any kind.

## My picks (code-only; you may skip this list)
- Rounds as in the split: one full run per round, one in-game test at the end, one merge; fastest without leaving a
  half-done branch.
- 3 rounds, not 4: one full run fewer (about 20 minutes saved); each round still small enough to find a failure's
  cause.
- T1 first: it is the step the split left for part 3, and every later step's numbers then sit in the right mod.
- Galleries in four parts by job, not by size, so each part has one reason to change.
- Foraging holes stay in the framework (several entrances is common to many real species); only the termite-only
  feeding galleries and mud casts move.
- The carried-load slot keeps today's save words, so nothing carried is lost on a reload.
- The mound family (3,206 lines over 8 files) left as it is: already split by job in the code review, and nothing in
  M8 touches it except a dead mound (a new block state, not a mound rework).

## Your calls
- **A nurse cut off from her queen (found in T4, 2026-10-23):** today she tries to walk back every 2 seconds, and
  when there is no way each try is costly (the bursts left in the stress test). The fix used for other work since
  `docs/BUGS.md` 42 would make her try every 20 seconds instead, so once a way opens again she could take up to 18
  seconds longer to come back to the queen. Not built; yours to decide.
- **In-game:** nothing should change. T4 is the only step that could (if the fix makes a termite give up sooner on
  work it can't reach); if so I stop and ask before building it.
- **Future features:** T1 and T3 make the Matabele ants easier (their numbers and their loads, dead termites and hurt
  nestmates, go in the savanna mod with no framework edit). Nothing here limits a future species.
- **Your time:** one in-game test of about 30 minutes at the end (termites, a new world), same launch command as
  always (`./gradlew runClient` in `C:\Ant Mod`). Full runs take as long as today.
