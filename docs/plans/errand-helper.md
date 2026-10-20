# The errand helper: one shared "walk to work" for every goal

Planned 2026-10-19 from the review of `AntNavigator` and the goals (the user: "start the errand helper"). Branch
`errand-helper` (from `master` at e3aea64).

## Why
The legs (`AntNavigator`) are fine; what makes a new job slow to add is that every goal writes its own walking code.
Counted on `master` 2026-10-19 across the 33 goals:
- 28 hand-written "close enough" checks in 19 goals (nearly all the same rule: within reach sideways, under 1.5 blocks
  up or down);
- 23 copies of "the walk is over and there is no route: give up";
- 6 copies of "a thing found no way to is left alone for a while", each written differently, and most goals that walk
  to work have none (`docs/BUGS.md` 42: `DigGoal` had none, and one cut-off insect cost a route search a second, up to
  29 ms a tick);
- 5 copies of "no way to this spot: pick another, up to 4 times".

## What it is
`entity/goal/Errand` (mind layer: it only tells the legs "go to this cell", never steers the body). A goal keeps one
and says what it goes to and what counts as there; the errand:
- walks there, planning a new route only once the last walk is over;
- with no way there, remembers the thing for 400 ticks (20 seconds) and says so, so the goal gives up; the goal's
  search for work skips what is remembered;
- tries other spots when the first has no way, giving up after 4;
- answers "within reach of it?" by the shared rule.
A smarter mind later uses it as its "act" building block (go, get there, or learn there is no way).

## Steps
1. **The helper, and the first three goals moved onto it** (`GatherSeedGoal`, `CarryCorpseGoal`, `DigGoal`).
   Nothing changes in the game: the same rules, written once. *Risk: low.* Tests: `./gradlew build`, then a full run
   (digging is a shared system everything uses, CLAUDE.md's rule).
2. **The other goals that walk to work** (carrying eggs, pellets and seeds, eating, feeding, milling, clipping,
   night soil, the patrol, standing by the queen, ...). Those without a "no way, leave it alone" memory get one: a
   small change in the game (an insect cut off from its work stops searching for a route every second), as the
   lesson of `docs/BUGS.md` 42 asks. Tests: the categories each goal touches, plus `away`.
3. **One route search for several spots** (speed fix): a digger's stand spots and the carriers' spots searched in one
   go instead of one after another; the insect goes to the nearest spot it can reach, as a real ant would. A small
   change in the game. Measured with `/antcolonies perf` before and after.
4. **`ScoutGoal` in three** (termites scouting and telling, harvester ants searching alone, recruits following a scent
   line), each small on the helper; scent lines walked with the legs' `follow` (one route for the whole line, ready for
   the Matabele raid columns in M8). To be planned with the user first.

Each step its own commit; the user OKs the next.
