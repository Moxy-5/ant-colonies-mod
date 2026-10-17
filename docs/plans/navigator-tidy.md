# Tidying the legs (`AntNavigator`), roadmap 3a part 2

Planned 2026-10-16, branch `claude/navigator-tidy-5f5fee` (from `master` at 39d2a81). **Approved by the user
2026-10-16: steps 1 to 5, and step 6 (fix `follow`) too.**

## What the navigator is
`nav/AntNavigator.java` (1,509 lines) is the insects' legs: given "go to this cell", it plans a route with
`AntPathFinder` and walks it step by step: walking, climbing, over edges, ceilings, termite tunnels and chimney mouths.
Every ant and termite uses it. The code-health split of 2026-10-14 left it in one piece on purpose: all the moves
share the same few pieces of memory (where the body is, which wall it holds, which step it is on), and the legs are
where most bugs have been (`docs/BUGS.md` 1 to 9c, 24).

## What I found
- **One very long piece of code.** The part that moves the insect one tick along a step (`advance`) is a single
  200-line block with 14 kinds of move inside it. The tunnel and chimney moves are already their own small pieces;
  the floor, wall and ceiling moves are not.
- **The same lines four times.** The four ways a mind starts a walk (to a cell, to one exact foothold, to "any spot that
  passes a test", along a shared route) each repeat the same 7 lines of setting up.
- **Leftovers.** Three small pieces of code are handed information they never use (a leftover from older versions),
  the "stuck" and "blocked" log messages build the same text twice, and a few distances are bare numbers in the code
  (`1.5`, `1.2`, `9`) instead of named settings like the others at the top of the file.
- **A misleading name.** `isWalkingADoorway` is also true inside a termite tunnel (it means "moving on an exact path").
- **No "deciding what to do" in the legs.** I looked for it on purpose (CLAUDE.md's three layers): the navigator only
  goes where it is told. The insect's walking habits and the mind's costs come in from outside, as they should. Nothing
  to move.
- **One small real quirk (not in the game today):** `follow` (walk a route someone else planned, for trails and
  marching columns later) forgets that route if the insect has to plan again, and just walks straight to its end.
  Only two tests use `follow` today, so no ant in the game is affected.
- **Old names in `docs/BUGS.md`.** Bugs 2 to 5b name pieces of code from before surface walking (`cornerOf`,
  `CIRCLE_TICKS`, `chooseWall`, `holdOnToWall`, `crawlAim`) that no longer exist. I checked each lesson still holds in
  today's code (the walker goes step by step through the route instead of guessing from cells, so bug 2's "back at the
  start" loop cannot happen; a step not done in 6 seconds is planned again; walls are part of each step; the regression
  tests are still there, merged into `NavigationTests`). I would add a line to each saying where the lesson lives now.

## The steps (each one small, built and tested before the next)
"Method" below means a named block of code that does one job.

1. **Split the long move code into one small method per move** (walk, fall, onto a wall, off a wall, climb, turn a
   corner, round an edge, over the top, over an edge, onto / off / along a ceiling, under an edge, out from under),
   like the tunnel moves already are. The code inside each is moved as it is, not rewritten.
   *Risk: very low.* Nothing changes in what runs, only where it is written.
2. **One "start a walk" method** for the four ways a mind starts one. *Risk: very low* (the same 7 lines, written once).
3. **Clear out the leftovers**: the unused information, the twice-built log text, the bare numbers get names. *Risk: none.*
4. **Rename `isWalkingADoorway` to `isMovingExactly`** (used in 2 places in the walking tests). *Risk: none* (the compiler
   catches any place missed).
5. **Order the file into clear sections with a short heading each**: starting a walk, what the body asks, planning,
   one tick of walking, the moves, tunnels, chimney mouths, holding on, reading where the body is, moving the body,
   hugging a narrowing spire, cell geometry. Comments checked against the code as I go. *Risk: none.*
6. *(Optional, your call)* **Fix the `follow` quirk**: keep the shared route when planning again. It changes nothing in
   the game today; it matters once trails or army ant columns use it. *Risk: low*. The two `follow` tests never plan again, so a new check is added to an existing scene.

Not planned: cutting the file into several files. It would make the moves harder to follow (they all share the same
memory), and the 2026-10-14 decision still stands: split it when a new kind of move is added.

**Every bug lesson in `docs/BUGS.md` must still hold.** None of the steps changes how a move works, so they all do;
the `legs` tests (24 walking, climbing and tunnel tests) after each step and the full run at the end check it.

## The quarantined test (fallen tree up a slope)
`aFallenTreeUpASlopeKeepsItsHoleWhenItsLowestBlockIsEaten` fails about 2 runs in 10 beside other tests: the feeding
gallery up under the tree stops at one block, and the workers stand idle in the tunnel.

**My first suspect, from reading the digging code** (`DigGoal`), not proven yet: a digger walks to a stand spot beside
the block, arrives, but is still not counted as "within reach" (for example standing on the top edge of a step in the
tunnel, which counts as the cell above). It walks there again, up to 60 times, gives up, and the colony hands it (or
the next worker) the very same block. That would look exactly like "3 workers in one spot in the tunnel" with no
"no way there" line. The legs would be fine; the mind and the reach rule would be at fault.

**How I'd check:** add one debug line where a digger gives up after arriving out of reach (its cell, the block, the
stand spot), then run the test with its shard neighbours several times until it fails, and read the line. If that is
the cause, fix the game (the rule for "within reach") and make the test check it (the user's rule: fix both). If the
cause turns out to be bigger than a small fix, I'll stop and tell you before changing anything.

## Tests
- After each step: `./gradlew build`, and the `legs` category (`ANTCOLONIES_TESTS=legs tools/run_game_tests.sh 4`).
- At the end: a full run on 8 servers (about 14 minutes), only when no other session is running game tests and
  you are not gaming.
- What you'd see in-game: nothing new. Ants and termites walk exactly as before; that is the point.
