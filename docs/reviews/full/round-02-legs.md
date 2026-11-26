# Full code review, round 2: the legs (reading round, 2026-11-25)

Read on branch `claude/review-fix-1-calls` at commit `d67f55ae` (round 1's fixes and the user's two calls). Rule book:
`docs/plans/code-review.md`. No code was changed, no test was run and nothing was measured in the game, so no lag
finding here has a number of its own (R2-16 leans on the mod's earlier measurement, named there).

**What was read:** the framework's `nav/` (17 files, about 4,200 lines: `AntNavigator` 2,579, `AntPathFinder` 690,
`Crowd`, `DoorwayMotion`, `LevelCellMap`, `Foothold`, `Route` and the small ones) and the walking half of
`entity/AbstractAntEntity.java` (every place the body meets the legs).

**Who read it:** four Opus readers, every line, each with its own files (the navigator's first half; its second
half; the route planner and the small files; the body's walking half with the move control and the doorway motion),
and one Fable reader following one thread across all of it: what happens when the world changes under a walking
insect. The main session then checked each grade A finding against the lines it names (those say "checked again"),
and two B findings that cross two readers' halves (R2-9, R2-22). The others carry their reader's proof only.

**How to read a finding.** Rating: **A** the player can meet it, **B** it will cost us when building on this,
**C** a tidy-up. Proof: **proven** (the path was walked through the code, lines named), **likely** (one link, named in
the finding, was not checked), **to measure** (a lag finding with no number yet). A finding rated A gets its
`docs/BUGS.md` number when a fix session takes it up. Findings keep their numbers for good; a fix session adds a
status line under a finding and never rewrites it.

**Count:** 43 findings: 4 A, 18 B, 21 C. All five readers are in; the round is read.

## The short of it

The legs are in better shape than their history suggests. The readers found **no crash path, no read of land that is
not loaded from the planner, no corner cutting, nothing lost on reload but a heading**, and every old trap in
`docs/BUGS.md` they looked at (1, 6e, 9c, 24, 53, 129, 135, 144, 148, 156) still guarded. The navigator is **not** a
candidate for a rewrite. What is wrong sits in four places:

1. **A search that finds no way is the legs' one big cost, and nothing rations it.** It runs to 6,000 steps (about
   20 ms by the mod's own earlier measurement), twice in one tick for most termite walks, with no limit on how many
   run in a tick (R2-16); the legs remember nothing about a failed search, so every mind must bring its own "wait
   before asking again" clock (R2-1), and five goals forgot theirs (R2-37); a mind can also pay for the same failure
   again a tick later (R2-39). The same blocks are read 50 to 60 times per search step (R2-17).
2. **"Can it stand here?" is judged by five rules.** The planner wants a full, sturdy top face; the body and the
   crowd shuffle accept anything with a collision box. So an insect can end up where the planner has no start for
   it: on a path block, on farmland, on a slab or carpet, or in water with a low bank, and there it stays (R2-38,
   R2-21).
3. **"Is it stuck?" is judged by many clocks that cover each other.** The step clock counts time spent waiting
   politely for the crowd (R2-9); three new plans are allowed per walk however far it got, and the legs' "I give up"
   is heard by no mind (R2-2); above the legs each goal has its own clock again.
4. **While an insect is frozen (paralysed, gripped, calling for help, carried) the legs do not run, but the body
   still believes it is on a wall** (R2-28): it slides down like on a ladder and is drawn as climbing.

Plus one leftover between worlds: two static maps keep a closed world in memory (R2-22).

## The ones that matter most

1. **R2-16:** a route search that finds no way costs about 20 ms and nothing limits how many run in a tick; most
   termite walks run it twice. The likeliest cause of short freezes near a mound.
2. **R2-37:** five care and rescue goals ask for a route again every tick after "no way", up to 400 times in a row.
3. **R2-1 (the cure for both, and for the next goal that forgets):** let the legs remember a failed search for half
   a second. One rule in the legs instead of a clock in every goal (BUGS 76, 317 and 354 were the same mistake).
4. **R2-38 with R2-21:** an insect on a village path, farmland, a slab, a carpet, or in a pond with a low bank can
   never plan a route again; a path is a wall to a raid. **Part of it is the user's call.**
5. **R2-28:** a frozen insect on a wall slides down it and is drawn as climbing, also in a rescuer's jaws.
6. **R2-9 and R2-2:** insects in a jam plan again and give up their errand, because the stuck clock counts polite
   waiting and three plans are all a walk gets.
7. **R2-22:** each world closed with insects out stays in memory until the game is closed.

## For the user to decide

- **R2-38 / R2-21 (paths, farmland, slabs, carpets, stairs as ground):** today the legs treat these as "not ground":
  a village path or a player's slab floor is a wall to every insect, and one that lands on it is stuck. Recommended:
  **yes, make them plain ground** (real ants walk over anything; the fix is one shared floor rule with its own small
  plan and tests). The small half, getting a stuck insect off again, needs no decision and is Claude's.
- **R2-30 (a hit never knocks an insect off a wall):** a player's hit, water or a blast moves an insect on a floor
  but never one on a wall or ceiling: it twitches and snaps back. Recommended: **keep it and write it down as the
  rule** (real ants and termites grip hard; a Matabele holds many times its weight), with one exception worth a
  thought later: an explosion.

## What this round did not do

- **Nothing was measured and nothing was run.** Every lag finding is "what runs, how often, over how many things".
  R2-16's 20 ms is from `docs/HISTORY.md` (2026-10-14 and 2026-10-23), before real sizes; a fix session measures
  first with `/antcolonies perf` in the termite stress scene.
- **Three facts about Minecraft itself are from memory, not from its source:** a frozen mob skips its mind but still
  moves and still counts as on a ladder (R2-28); a dirt path's and farmland's top is not a "sturdy" face (R2-38);
  every living mob pushes its neighbours each tick (R2-31). I am confident of the first two.
- **One Fable reader, not two.** The second thread ("a route from plan to last step") was covered by the two
  navigator readers and by the main session's checks, not by a reader of its own.
- **The goals that call the legs** were read only where the thread reached them (the five in R2-37, `Errand`,
  `BuildMoundGoal`). They are round 3's and round 5's. Whether an idle insect whose cell a builder fills is lifted
  out first is round 5's question.
- **The drawing side** (`EdgeOver`, the renderer's push into a wall) is round 4's; `HiddenTrips` is round 5's.
- The main session checked the four A findings and two B findings itself; the other 37 carry their reader's proof.
- `docs/BUGS.md` was searched by class and method name, not read whole.
- Cost: about 1.04 million subagent tokens (round 1: about 1.2 million).

---

## Slice: the navigator, first half

`AntNavigator.java` lines 1 to 1300 (starting a walk, planning, planning again, one tick of walking, hidden trips,
arriving, holding on, the walk along a floor). Every line read.

### R2-1 (B, proven): the legs never remember a failed search, so every mind must bring its own retry clock

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 400 (`AntNavigator.askedInVainJustNow`: the same walk asked again from the same cell within 10 ticks of "no way" is answered without a search; test `askingAgainAfterNoWayCostsNoSearch`). The goals' own clocks are left in place.**

- **Where:** `AntNavigator.begin` 403 to 421 (always ends in a full `plan()`), `plan` 583 to 661.
- **What is wrong:** asking again right after a failed search searches again, and asking for the same goal while it
  is being walked throws the route away and plans from scratch. Each goal guards this on its own (a half-second
  clock in the scout, a 1.5-block rule in the way home, `Errand`'s 20 s, `FightGoal`'s `unreachable`: about nine
  places), so the same thing is judged in many places and a goal written without a clock is a lag spike. It has
  happened three times (BUGS 76; BUGS 317, about 150 failed plans a second; BUGS 354, 16,693 new walks in 5
  minutes) and is happening now in five goals (R2-37).
- **What the player sees:** nothing by itself; it is the root under R2-37 and the next one like it.
- **Fix proposed:** in the legs, keep the last goal that found no way with its start cell and game time: the same
  question within about 10 ticks (my pick, BUGS 317's half second) is answered "no" without a search; the same goal
  asked while still walking it keeps the walk. The goals' own clocks can then go one by one.

### R2-2 (B, proven; how often, unchecked): three new plans are all a walk gets, however far it got, and nobody hears the give-up

**Status (2026-11-26, `claude/review-fix-2`): half fixed, BUGS 404: new plans are counted since the last finished step. Open: `Errand` still does not read `hasFailed()` (a change to what `walkOn` means for every goal; left for round 3's fixes), and the landing check still uses `floorUnder`.**

- **Where:** `MAX_REPLANS` 60, `replan` 749 to 766, `begin` 413 (the only reset), `tick` 799 to 802;
  `hasFailed` 457 to 460; `entity/goal/Errand.java` 179 to 181, 215 to 217. Seen by two readers.
- **What is wrong:** (a) a walk is given up at its fourth new plan, counted over its whole length. A termite going
  25 blocks up a mound that is being built (BUGS 122's scene) meets "the way changed" for four harmless reasons and
  gives up while making steady progress. (b) The other way round: after a give-up the mind's next tick starts a
  fresh walk with the count at 0, because `Errand` looks at `isDone()` and never at `hasFailed()`; a route the
  planner believes in but the body cannot walk is tried for ever, one round about every 24 s. This is the root
  under BUGS 122, 141 and 313, each of which got its own clock in a goal. (c) A small extra: an insect that planned
  in the air and lands at a hole's rim is read by `floorUnder` (2206) as on the bad floor over the hole while
  `plan`'s `currentFoothold` reads it rightly, and one plan is spent on "landed somewhere else".
- **What the player sees:** an insect that stops part way and starts the same trip again; the known "walking in
  place for a long time".
- **Fix proposed:** count new plans since the last finished step (set `replans` back where a step completes, 831 to
  834), and have `Errand` leave a thing alone after a walk to it ended in `hasFailed()`; use `currentFoothold` for
  the landing check at 799.

### R2-3 (B, likely; not seen in play): an insect falling more than 12 blocks beside a wall is taken for "shut in" and snapped onto the wall

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 403: the beside starts are taken only when its own cell or the cell under it is closed (first tried with its own cell only: a raider bobbing at a pond's surface then never got out, the pond raid test failed 3 of 3, so the cell under it counts too). No test of its own (it needs a 13-block drop beside a wall).**

- **Where:** `plan` 588 to 617, `stepOutTo` 668 to 692; `AntPathFinder.landing` 345 to 360.
- **What is wrong:** the "start beside it" branch is meant for an insect inside a block (a mud plate, BUGS 24; a
  covered ramp, BUGS 148). It is taken whenever `landing` gives nothing, and `landing` also gives nothing in open
  air over a drop deeper than 12, or over water. A player knocks an insect off a tall mound or tree; past 3 blocks
  it plans again in mid-air ("knocked off the route", 822); the cells along the wall it falls past give wall
  footholds; a route is found and `stepOutTo` moves the falling body up to a block sideways onto that wall.
- **What the player sees:** a falling insect jumps a block sideways in mid-air and clings to a wall it never touched.
- **Fix proposed:** take the beside starts only when the insect's own cell is not open to it (or it stands on a
  covered slope); in open air with no landing, plan nothing until it lands (the `FALL_TICKS` wait is already there).

### R2-4 (C, proven): the shared-route code is used by nothing in play

- **Where:** `follow` 339 to 341; `shared`, `sharedFrom`, `sharedOffset` 133 to 139; `search` 726 to 746; `replan`
  759 to 762; `plan` 646 to 648. `.follow(` is called only in `LegsTests`.
- **What is wrong:** about 40 lines of route joining no mind uses since the raids walk their own way (`RaidMarch`).
  The arithmetic is right. One waste waits in it: when the step to rejoin at has been built over, the first search
  can only fail and runs to the cap before "any later step" is tried.
- **Fix proposed (my pick):** keep it as the ready socket for trails, say in its comment that play does not use it,
  and check the join step is still a foothold before the first search.

### R2-5 (C, proven): log and stopwatch texts are built on every plan, wanted or not

- **Where:** `plan` 625 to 626 (`ant.runningGoals()` streams every goal into a text); `replan`'s callers 819, 838,
  841, 844 (`describe()`, `String.format`, `motion.toString()` before `LOGGER.debug` drops them).
- **Fix proposed:** build the asker's name once per walk in `begin`, and only while the stopwatch is on; check
  `LOGGER.isDebugEnabled()` first.

### R2-6 (C, proven): after a reload a held insect's heading, ceiling side and channel wall are gone

**Status (2026-11-26, `claude/review-fix-2`): the heading fixed (saved with the hold as `HoldHeading`); the ceiling side and the channel wall are still not saved.**

**Status (2026-11-26, the list's wording): half fixed: the heading is saved; the ceiling side and the channel wall are not.**

- **Where:** `restoreHold` 529 to 538 (`heading = 0`); `AbstractAntEntity` 2905 to 2910, 2941 to 2944 (only cell,
  surface and "in a channel" are saved). Seen by three readers.
- **What the player sees:** an insect saved head down or sideways on a wall faces head up until it walks; one
  resting on the wall of an upright tunnel is moved to the tube's middle. A look for a moment, nothing lost. (Also
  not saved, and rightly rebuilt: the route, the step clocks, the doorway's point.)
- **Fix proposed:** save the heading (one number) and the ceiling side with the hold.

### R2-7 (C, proven): `mayHaveGoal` asks the mind's test about twice as often as it needs

**Status (2026-11-26, `claude/review-fix-2`): fixed with R2-40.**

- **Where:** 703 to 709: a channel foothold is tried on all six surfaces though it only ever has one.
- **Fix proposed:** `Foothold.channel(cell)` once per cell plus the six plain surfaces (with R2-40).

### Solid in this slice (do not "fix")

- **A route of one step (BUGS 1):** `tick` 807 sends it straight to `arrive`; nothing reads past a route's end.
- **A block changing under a step (BUGS 6e):** `whatChanged` is asked every tick before any move (817 to 821).
- **Shut in by a plate or a covered ramp (BUGS 24, 148):** the beside starts and `stepOutTo` do what they say for
  an insect really inside a block (R2-3 is the one case they catch that they should not).
- **Holding on after a reload:** the hold is checked on the first tick and let go if its block is gone.
- **Hidden trips:** `goUnder` and `cameUp` check the route is still the one it went under on.
- **Block reads:** every read through `LevelCellMap` asks `LandReady` first; the navigator's own checks here are
  within a block of the body.
- **The slow-step standing rule (BUGS 122)** ignores crowd waits, so it does not fight personal space (the main
  step clock does: R2-9).
- **The lane point** is kept once a step; `avoid` entries run out and are cleared on each plan.
- **Layers kept:** nothing here decides what to do; no species is named.

---

## Slice: the navigator, second half

`AntNavigator.java` lines 1300 to 2579 (steering round the crowd, the standing shuffle, free spots, climbing, edges,
doorways and mouths, channels, where it stands now, hugging a column). Every line read.

### R2-8 (B, proven; to measure): the standing shuffle's search for a free spot can run every tick for good

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 405: one look every 10 ticks whether it found a spot or not, the found spot kept and walked to in between. The two numbers for "too close" are left as they are. No test of its own; not measured.**

- **Where:** `keepApart` 1392 to 1462 (the wait is set only at 1450, when both searches fail; the "free spot here"
  way out at 1455); `spaceFor` 1468 to 1470; `freeNear` 1496 to 1526; the "pressed" test at 1411.
- **What is wrong:** two numbers judge "too close". Pressed: the gap is under 0.85 of both radii together. Clear:
  the gap is at least 0.7 of mine plus the other's. They agree only for two bodies of one size. A bigger insect
  beside a smaller one that stood there first, in the thin band between (about 0.012 blocks for a major beside a
  minor), counts as pressed while its own spot counts as clear: the search runs, finds "right here", leaves without
  setting the wait, and runs again next tick, for as long as both stand. The same happens for any push that points
  into a wall. A second case: an insect still pressed while walking to a far free spot runs both searches again
  each tick (about 450 points, each a crowd lookup, the clear ones up to three collision checks). The comment at
  238 says the look was meant to run at most every 10 ticks.
- **What the player sees:** nothing on screen; server time in mixed-size crowds that stand still (a raid milling on
  a dead mound's hump, a nest with majors and minors). The reader's guess is 10 to 100 times an insect's budget for
  each one caught; not measured.
- **Fix proposed:** one rule for "too close" (the pressed test from the same clearance measure), and set the wait
  after every search, not only a failed one.

### R2-9 (B, likely; checked again): the stuck clock counts polite waiting, so a walker in a jam plans again and gives up

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 404 and 409: a tick held for the crowd is not counted by the step clock. Fixing it showed that "held for the crowd" was true on every tick for an insect never yet held (BUGS 409), so the standing-too-long rule had never fired for those. No test of its own.**

- **Where:** the waits in `steer` 1300 to 1305 and 1329 to 1336; the step clock at 840; `standsTooLong` 975 to 986.
- **What is wrong:** while `steer` holds a walker behind another body, `++stepTicks` still runs toward 120. The
  slow-step rule already leaves crowd waits out (`heldForCrowd()`); the main clock does not: one question, two
  rules. A walker behind a stopped body waits up to 70 ticks and squeezes for 20; two bodies in one step pass 120,
  it plans again "stuck" (a full search), and after three (R2-2) the walk fails.
- **Checked again:** line 840 counts on every tick `advance` is false and the insect is not "blocked"; a held
  walker is neither. Whether real jams last 6 s on one step was not checked in the game.
- **What the player sees:** in a crowded tunnel or at a nest door, insects that stop, turn onto a new route and
  sometimes give up their errand.
- **Fix proposed:** do not count a tick the legs held the insect for the crowd (the same `heldForCrowd()` test).

### R2-10 (C, proven mismatch): the shuffle can move an insect 1.5 blocks, its comment says 0.7; its wall check looks at one point

- **Where:** comment 1386 to 1391; `FREE_REACH_FAR` at 1446 to 1448; the midpoint check at 1511.
- **What is wrong:** a mind that checks the distance to its place can walk back into the packed spot and be
  shuffled out again. The "no spot past a wall" test looks only at the middle of the line, so a spot across a
  one-block hole is taken and the insect walks into the hole.
- **Fix proposed:** keep the standing shuffle within 0.7 (the walk-in spill already covers overflow) or put the
  comment right; check the ground along the line.

### R2-11 (C, proven): the "no mouth any more" checks in the mouth moves can never fire, and would do the wrong thing

- **Where:** 1956 to 1958, 1984 to 1986, against `whatChanged` 663 to 668.
- **What is wrong:** `whatChanged` runs first on the same tick with a stricter test, so these are never reached; if
  they were, `return true` would mark the step done with the body still in the tube.
- **Fix proposed:** delete both, or return false so the step clock catches it.

### R2-12 (C, proven; whether it shows, unchecked): a sideways crawl ends over a tunnel opening

- **Where:** `climb` 1598 to 1600 against `wallPoint` and `besideOpening` 2493 to 2513.
- **What is wrong:** `wallPoint` puts the hold on the solid rim beside an opening (BUGS 135's rule); the sideways
  crawl then overwrites the sideways place with the cell's middle, right over the opening; the next step pulls it
  back about a quarter block.
- **Fix proposed:** pass the crawl's end through `besideOpening` too.

### R2-13 (C, proven): worked out again every tick: the doorway's point list, and up to 64 slope reads

- **Where:** `outOfMouth` 1961, `intoMouth` 1989, `comeOutOf` 2012, `goInto` 2024 (`DoorwayMotion.comeOut` and
  `goIn` build a list of 6 to 8 points; a peek alone lasts 56 ticks); `overTop` 1648 (`lipWithin` on a slope).
  Seen by two readers.
- **Fix proposed:** work them out once when the step starts and keep them, as the lane point is kept.

### R2-14 (C, unsure it can be reached): `unsqueeze` can divide by a column width of 0

**Status (2026-11-26, `claude/review-fix-2`): fixed (a width of 0 never divides).**

- **Where:** `pos()` 2453 to 2458, `unsqueeze` 2473, `widestBetween` 2410 to 2430.
- **What is wrong:** a spire tip's top layers are 0 wide; 0 divided by 0 gives a broken number, and a broken
  position makes an insect fail to load. The reader found no way to reach it (the tip cannot be climbed).
- **Fix proposed:** never return a width under one pixel.

### R2-15 (C, unsure it can be reached): tiny correction moves never count as blocked

- **Where:** `moveAttached` 2259 to 2265, `small` 2276 to 2278, against `isBlocked` 1008.
- **What is wrong:** a correction of 0.001 to 0.03 that collision stops repeats every tick; only the 6 s clock
  notices, and while idle nothing does.
- **Fix proposed:** let `isBlocked` count them too.

### Solid in this slice (do not "fix")

- **Hugging a column** (`moveAttached`, `hugAlong`, `drawIn`, `squeeze`, `unsqueeze`): the sums agree with each
  other; `unsqueeze` undoes `squeeze` exactly.
- **The doorway's point lists** are always 7 and 6 long at any size and every caller checks before reading: no
  reading past the end, even if a door appears or goes mid-step.
- **The channel moves** match the planner's step makers cell for cell and side for side.
- **`roundEdge`** always gets a one-block step from the planner.
- **`currentFoothold`:** its order of checks is careful and each branch is tied to a bug. Leave it.
- **`ceilingPoint` and `besideOpening` (BUGS 135, 144)** are right for holds and climbs (R2-12 is the one bypass).
- **The crowd's shared list** is never used while another is being walked.
- **`offWall`'s walk out from the wall's foot** stays in its cell and cannot float over an edge.
- **No direct block read** here is more than a block or two from the body.

---

## Slice: the route planner and the small files

`AntPathFinder`, `Route`, `Foothold`, `StepKind`, `CellMap`, `LevelCellMap`, `Crowd`, `Slope`, `SoftGround`,
`ClimbShape`, `HiddenWays`, `Channel`; the unit test `AntPathFinderTest` for gaps. Every line read.

### R2-16 (A, the path proven and checked again; the time is the mod's earlier measurement, to measure anew): a search that finds no way costs about 20 ms, most termite walks run it twice, and nothing limits how many run in a tick

**Status (2026-11-26, `claude/review-fix-2`): fixed in part, BUGS 399: no second search when no habit had a say in the first (test `askingAgainAfterNoWayCostsNoSearch`); with R2-1, R2-20 and R2-17. Measured in that test, a search that finds no way on open flat ground: 19.5 ms before, 13.3 ms with R2-17. No budget per tick was added (to see first whether it is still needed).**

- **Where:** `AntPathFinder.java` 73 to 74 (`DEFAULT_MAX_EXPANSIONS = 6000`, with the comment "an unreachable goal
  never costs much time"), 279 (the loop runs to the cap); `AntNavigator.plan` 627 to 632; `AbstractAntEntity.
  walkHabits` 2272 to 2280; `Doorway.avoidMouths` 25 to 30. Seen by three readers.
- **What is wrong:** a goal in reachable-looking land with no way to it is given up only after 6,000 search steps.
  `docs/HISTORY.md` measured that at "one 21.5 ms route search" (2026-10-14) and "four failed full searches of about
  20 ms a try" (2026-10-23). A termite takes the habit "not through a chimney mouth" on 9 walks in 10; `plan`
  searches with the habit first and, finding nothing, searches again without it. When there is no way at all the
  second search must fail too (a habit only forbids steps), so the same answer is paid for twice in one tick: about
  40 of a tick's 50 ms. Nothing counts searches per tick, so two insects doing this on one tick go past 50, and a
  new plan during a walk (up to three) pays it again.
- **Checked again:** lines 629 to 632 are as the readers say (`search(... costs.and(habits))`, then `search(...
  costs)` when that is null); `walkHabits` returns the mouth habit unless a roll under the colony's
  `mouthTripChance` says otherwise; no per-tick budget exists in `AntNavigator` or `AntPathFinder`. One thing
  softens it: a goal shut in a small closed space runs out of cells long before 6,000, and `mayHaveGoal` skips the
  search for a mind that gave a reach (today only `ShapeMoundGoal` gains, R2-40). The cap is hit when a lot of land
  can be reached and the goal cannot: a sealed chamber, a hole just plugged, a food spot cut off, a nurse cut off
  from the queen (the case `docs/HISTORY.md` left open on 2026-10-23).
- **What the player sees:** a short freeze now and then near a grown mound, most at foraging time and after alarms.
- **Fix proposed:** (1) let the search say whether any step was ruled out by a habit; if none was, skip the second
  search. (2) R2-17, fewer block reads per step. (3) R2-1, the legs' memory of a failed search. (4) Only if the
  numbers still ask for it: a shared budget of searches per tick. Measure before and after in the termite stress
  scene. Also put the comment at 73 right.

### R2-17 (B, proven by counting calls; to measure): one search reads the same blocks 50 to 60 times per step

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 399 (`SearchMemo`, made round the map for one search). Measured: 19.5 ms to 13.3 ms for a search that finds no way on open flat ground, a third less, not the 3 to 5 times guessed.**

- **Where:** `LevelCellMap.java` 38 to 106 (each call: the land-ready check, `getBlockState`, shape work,
  `HeldCells.barred`); `AntPathFinder.neighbours` 363 to 474, `intoChannels` 532 to 564; `Foothold.isValid` 70 to 75.
- **What is wrong:** from one floor foothold on flat ground the planner asks "is this cell open" four times, the
  neighbour's twice, "is it a slope" twice a side and again in every `edgesMeet`, and the channel sides and mouths
  of eight cells round it. Most are repeats of reads made a moment before for the neighbouring footholds.
- **What the player sees:** nothing alone; it is where R2-16's time goes.
- **Fix proposed:** a small memory for one search (answers keyed by the cell), made around the map at the start of
  a search and thrown away at its end. Safe: blocks do not change during one search. The reader's guess is 3 to 5
  times fewer reads; measure it.

### R2-18 (B, the sums sure, how often unchecked): big step costs fight the fixed cap, and the mud test hides it

- **Where:** `AntPathFinder.java` 297 (`map.footCost(to)`), 74; `SavannaMudBlock.WET_INSECT_COST = 12.0`;
  `AntNavigator.java` 622 (a blocked step costs 50 more); `MudTests.java` 231, 236, 244 (search with 20,000).
- **What is wrong:** the search must look at every foothold cheaper than the real route. A band of wet mud 8 wide
  that must be crossed adds about 96, so the search floods thousands of cells before it accepts the crossing, and
  with the game's 6,000 it fails instead: "a cost, never a wall" becomes a wall and a full failed search (R2-16).
  The same for the 50 on a blocked step. The only test of the mud rule uses 20,000, so the game's real cap is
  never tried.
- **What the player sees:** near the never-drying river mud, an insect whose only way crosses a wide wet patch
  stands with "no route".
- **Fix proposed:** run `MudTests` with the game's cap and add a wider band; then lower the costs (wet mud about 3
  to 4, my pick: enough to go round a small wallow). Fewer rules than a second search.

### R2-19 (B, proven in the planner; what the body then does, two readers disagree): a tunnel window sealed in front of an insect is not seen by the every-tick check

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 407: `whatChanged` checks the very opening for the steps into and out of a channel. No test of its own yet.**

- **Where:** `AntPathFinder.whatChanged` 648 to 677 (only `CRAWL_CHANNEL` checks the sides, 669 to 675);
  `Foothold.isValid` 63 to 66 (a channel foothold is good while any side is open).
- **What is wrong:** for the steps into and out of a channel the check is only "the channel still opens somewhere".
  If the termites seal the very window the step goes through, the check says nothing changed. The planner's reader
  holds the body is then moved through what is now solid soil; the thread's reader holds the "blocked" rule catches
  it a second later and plans round. Neither ran it. The method's own comment (642 to 646) says a closed window is
  caught.
- **What the player sees:** at worst a termite slipping through a freshly sealed window, most likely in an alarm.
- **Fix proposed:** work out the side from the two cells, as the `CRAWL_CHANNEL` branch does, and check it; a unit
  test beside `aClosedChannelIsNoLongerAWay`.

### R2-20 (B, proven; how often minds ask, unchecked): a goal cell with nothing to hold in it is searched to the cap

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 399 (`AntPathFinder.hasHold`; checked in `askingAgainAfterNoWayCostsNoSearch`).**

- **Where:** `AntPathFinder.java` 187 to 194, 203 to 222.
- **What is wrong:** only "open, a slope or a channel" is asked before searching. A cell in mid-air, or one whose
  faces cannot be held, passes; then the search can only fail, after all 6,000 steps. In the many-goals search such
  cells also stretch the box the search aims at.
- **What the player sees:** part of R2-16's freezes, for a mind that names a cell in the air (a place over a drop,
  a target's cell while it climbs).
- **Fix proposed:** keep a goal only if it has a foothold (six cheap checks); answer "no" at once when none is left.

### R2-21 (B, proven for slabs and carpets, likely for paths and farmland): common part-blocks are neither ground nor passable

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 412, the user's call ("Yes, plain ground"): a flat part-block is a flat slope to the planner (`LevelCellMap.lowTop`). Stairs are left out.**

- **Where:** `LevelCellMap.isSolidFace` 60 to 69 (`isFaceSturdy`), `isOpen` 39 to 56; `Foothold.isValid` 74;
  `AntPathFinder.landing` 345 to 360.
- **What is wrong:** the cell over a bottom slab, a carpet, a stair, a dirt path or farmland has no floor (the top
  is not a full sturdy face) and the block's own cell is not open: a line of them is a gap no insect can cross or
  drop into. BUGS 362 lists these blocks as a raid's trouble; it is the legs' one floor rule.
- **What the player sees:** a raid or a forager whose way crosses a village path or a player's slab path finds no
  way; ants set on a slab floor stand for good (R2-38 is what happens to one that gets there).
- **Fix proposed:** a floor is any block whose top, for this insect, lies within half a block of the cell's bottom
  (the half-block rule `edgesMeet` already uses). It touches the one floor rule everything shares: its own small
  plan and tests. **The user's call** (see the top).

### R2-22 (B, proven; checked again; how much memory, to measure): two static maps keep a closed world in memory

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 406: both maps are cleared when the server stops. `Body` still holds its insect; `HeldCells` is not swept in a running world.**

- **Where:** `Crowd.java` 49 (`WeakHashMap<Level, Crowd>`), 31 to 33 (a `Body` holds its insect), 175 to 184;
  `fight/HeldCells.java` 30. Seen by two readers.
- **What is wrong:** such a map lets a world go only when nothing else holds it, but here the map's own value does:
  the grid holds last tick's bodies, each body its insect, each insect its world. A world the player leaves never
  ticks again, so its last bodies are never cleared and the whole world stays. `HeldCells` has the same shape and
  also keeps cells nobody asks about again.
- **Checked again:** the chain is as said, and nothing clears `CROWDS` (no call from `ModEvents.onServerStopped`
  or anywhere else).
- **What the player sees:** memory climbing by a world's worth each time they quit to the title and open a world
  without closing the game; in the end long pauses. One world a sitting never meets it. (Round 1 found nothing left
  over between worlds in the brain; this is the legs' one.)
- **Fix proposed:** clear both on the server-stopped event; keep the insect's id and numbers in `Body`, not the
  insect; sweep `HeldCells` of run-out holds now and then.

### R2-23 (C, proven): the search's guess of the distance left can be too high, so a route is not always the cheapest

- **Where:** `AntPathFinder.java` 309 to 311, the comment at 227, the moves at 393, 410, 452, 459.
- **What is wrong:** going over an edge changes two coordinates for a cost under 2. Routes stay legal, sometimes a
  little dearer, and the many-goals search can pick a spot that is not the nearest.
- **Fix proposed:** keep the guess (it is fast) and put the comments right.

### R2-24 (C, proven): comments that disagree with the code

**Status (2026-11-26, `claude/review-fix-2`): the fall's cost and the harvester line put right; the comment at 73 was rewritten with R2-16; the one at 642 to 646 is true now (R2-19).**

**Status (2026-11-26, the list's wording): fixed: every comment named is put right.**

- `AntPathFinder.java` 73: "an unreachable goal never costs much time" (it costs the most, R2-16); 42: a fall costs
  "3 + the height" (the code adds 1 more, 687); 642 to 646: a closed window is caught (only between two channels).
- `Crowd.java` 26 to 27 still speaks of the red harvester ant.

### R2-25 (C, proven; to measure): throwaway objects in the search's inner loop

- **Where:** `neighbours` (a new foothold and cells per candidate), `search` 289 and 301, `StepCosts.forbid` 105 to
  107, `floorEdge` 592 to 607. A full failed search makes about 100,000 small objects.
- **Fix proposed:** only after R2-17, and only if the stopwatch shows pauses.

### R2-26 (C): small tidy-ups

- `Route.hasFall` is used only by tests; `CEILING_COST` and `CHANNEL_COST` are public but used only inside.
- `CellMap.isOpen`'s comment leaves out sweet berry bushes, powder snow and held cells. Magma and campfire tops are
  walkable floors: harmless in the savanna today.
- The legs' map asks the fight package (`HeldCells.barred`) on every "is it open": fine as one rule with two
  readers; its cost belongs in R2-17's memory.

### R2-27 (C): what no unit test covers in the planner

**Status (2026-11-26, `claude/review-fix-2`): one gap closed: `aWindowSealedInFrontOfAnInsectIsNoLongerAWayInOrOut` (R2-19). The others are open.**

- A search stopping at the cap; `whatChanged` for the steps into and out of a channel (R2-19) and for the edge
  moves with the corner cell filled (right today, guarded by nothing); that `passesThrough` names exactly the cells
  the planner checked; soft ground at the game's cap (R2-18); a goal equal to the start; a fall of exactly 12 and
  of 13; a fall over water.

### Solid in this slice (do not "fix")

- **The planner never reads land that is not ready.** Every `LevelCellMap` method asks `LandReady` first, and
  unready land answers "closed": a route never goes there and nothing is made to load.
- **No corner cutting.** Every move was walked: each needs its corner cell open, and `passesThrough` names exactly
  those cells, so the every-tick check agrees with the planner.
- **The slope rules** agree between `neighbours`, `landing` and `Foothold.isValid`.
- **Falls:** never into water, lava or fire, never more than 12 blocks.
- **Crash safety:** a route always starts with its start step; a goal equal to the start, no starts, a closed goal
  are all answered without a crash.
- **The many-goals search** (one search for all spots) is a real saving. Keep it.
- **`Crowd`:** a one-block grid kept for one tick; no insect is compared with every other; ticking order does not
  matter. Fine for 50 to 60 real insects.
- **Wet mud** is stood on, its cost is its only effect. Nothing here names a species.

---

## Slice: the body's walking half

The walking half of `AbstractAntEntity` (the close-in walk, the tick that runs the legs and sets the motion, the
surface and heading sent to the drawing, saving and restoring the hold, every `navigator.stop()`), `AntMoveControl`,
`Doorway`, `DoorwayMotion` and its unit test. Every line of those parts read.

### R2-28 (A, the mod's side proven and checked again; what Minecraft does, from memory; not seen in play): a frozen insect on a wall slides down it and is drawn as climbing

**Status (2026-11-26, `claude/review-fix-2`): fixed, BUGS 402 (`AntNavigator.letGo`, called from the body's tick while it is frozen; one rule: a frozen body lets go and falls; test `anInsectParalysedOnAWallLetsGoAndFalls`). The first build of it crashed a full run (BUGS 410): fixed.**

- **Where:** `AbstractAntEntity.java` 1501 to 1505 (`isImmobile`), 2311 to 2353 (`customServerAiStep`), 2418 to
  2426 (the surface sent to the drawing each tick), 2597 to 2600 (`onClimbable`), 1287 to 1290 (calling for help),
  1446 to 1459 (`fallParalysed`), 1220 to 1231 (picked up); `AntNavigator.stop` 386 to 396.
- **What is wrong:** an insect is "immobile" while paralysed, pinned, clamped, held for care, tended, calling for
  help or carried. Minecraft then skips its mind, and with it `customServerAiStep`: the legs do not tick and nothing
  sets the climbing motion. But the legs' `attached` and `surface` keep their last answers, so `onClimbable()` stays
  true and Minecraft moves the body as on a ladder: it sinks at up to 0.15 blocks a tick. The surface sent to the
  drawing stays "wall", so it is drawn tipped up against the wall while it slides, at the foot, and in a rescuer's
  jaws for the whole carry. Freed after sliding more than 1.5 blocks, the "just let go" branch (2341 to 2347) uses
  a motion saved before the freeze.
- **Checked again:** `isImmobile` and `onClimbable` read as said; `navigator.stop()` says itself "an insect on a
  wall keeps holding on where it is" and touches neither `surface` nor `attached`; `fallParalysed`, `pickUpHurt`
  and the call for help (1287, no surface check) only call `stop()` or nothing. The comment at 1498 says a
  paralysed insect "still falls": the intent is a fall, the code gives a ladder slide in a wall pose. Unchecked:
  whether the drawing shows the paralysed pose over the wall pose.
- **What the player sees:** a hurt Matabele limping home up a dead mound's face stops to call for help, slides down
  the face without moving its legs and stands at the foot drawn against the wall; a stung termite low on a mound
  wall the same. Rare (it must be on a wall at the moment it is frozen), plain when it happens.
- **Fix proposed:** one "hold still" tick of the legs for a frozen body, called from the body's own tick: keep a
  good wall hold with no motion (calling for help, clamped), or let go cleanly (paralysed, carried: surface down,
  not attached). Clear the saved climb motion on any tick the legs did not run.

### R2-29 (B, likely; which castes take a mouth, unchecked): the way through a chimney mouth still uses one body's lengths

- **Where:** `DoorwayMotion.java` 44 to 76; `AntNavigator.java` 1961, 1989, 2012, 2024 pass `ant.getScale()`.
- **What is wrong:** BUGS 131 made every length scale with the game scale, but they are still the lengths of one
  model (one comment cites 0.17, the old harvester worker's number). The renderer's real push into a wall is worked
  out per model. So a soldier, the king or any later species through a doorway gets offsets made for another body;
  BUGS 131's own lesson ("a motion tuned for one body must name its body's lengths") is half kept.
- **What the player sees:** today, if only termite workers take mouths, about nothing; any other body would peek
  from too deep or hang off the spire's face.
- **Fix proposed:** feed the mouth motion the body's own climb numbers (a copy the server can read, signed up like
  the body sizes), and a test that they equal what the renderer uses for the termite worker.

### R2-30 (B, proven on the mod's side): nothing can knock an insect off a wall, and nowhere says that is meant

- **Where:** `AbstractAntEntity.java` 2337 to 2340 (the climb motion replaces the body's motion every tick on a
  wall), 1716 to 1721 (`knockback`).
- **What is wrong:** a hit, flowing water or a blast only sets the motion; on a wall or ceiling the next tick puts
  the legs' motion back and `hold()` pulls the body to its hold. On a floor the same hit works. It may well be the
  right look, but it is a second rule beside `knockback`'s with no word written about it.
- **What the player sees:** hitting an insect on a wall makes it twitch out and snap back.
- **Fix proposed:** **the user's call** (see the top). Recommended: keep it and write it down in `docs/DESIGN.md`.

### R2-31 (C, likely): every insect pushes the mobs round it every tick, though nothing can push it

**Status (2026-11-26, `claude/review-fix-2`): fixed: `pushEntities()` does nothing.**

- **Where:** `AbstractAntEntity.java` 2953 to 2957 (`isPushable` false; `pushEntities` not overridden).
- **What is wrong:** one search for mobs in its box per insect per tick for nothing, and insects shoving a player
  or an animal they overlap.
- **Fix proposed:** make `pushEntities()` do nothing.

### R2-32 (C, unsure it is ever asked): `AntMoveControl` turns to face east when the point is straight above or below

**Status (2026-11-26, `claude/review-fix-2`): fixed: the flat distance is tested.**

- **Where:** `AntMoveControl.java` 28 to 37 ("arrived" uses the distance in all three directions, the turn only two).
- **Fix proposed:** test the flat distance and keep the heading when it is tiny.

### R2-33 (C, unsure it shows): "stepped up close" outlives `stop()`

**Status (2026-11-26, `claude/review-fix-2`): not a fault to fix this way: the care goals call `stop()` on every tick they stand at their patient, so clearing "stepped up close" there would let the crowd shuffle a carer away. Left as it is.**

- **Where:** `AntNavigator.java` 265, 377, 386 to 396, 419; read by `keepApart` 1393.
- **What is wrong:** an insect that closed in on something and then just stands is never shuffled aside for the
  crowd until its next walk.
- **Fix proposed:** clear it in `stop()`.

### R2-34 (C, proven, harmless): on the server "is it climbing" reads the value sent to the drawing, a tick late

- **Where:** `AbstractAntEntity.java` 2597 to 2600, 2586 to 2588; used by `standsToFight` 1519, `tickAttack` 1658.
- **Fix proposed:** on the server ask the legs; keep the sent value for the client.

### R2-35 (C): comments in the wrong place or saying the wrong thing

- `AbstractAntEntity.java` 2287 (a line about `customServerAiStep` above `liftIfSunk`), 2420 (above the three
  lines that send values).
- `DoorwayMotion.java` 152 ("over the middle of the mouth": it is 0.05 to one side, on purpose), 45 to 46 (R2-29).

### R2-36 (C): gaps in `DoorwayMotionTest`

- "Drawn within the mouth" is checked for every point only at the worker's size; nothing ties the motion's numbers
  to the renderer's (R2-29); nothing checks the points make an unbroken path; one "test" only writes files.

### Solid in this slice (do not "fix")

- **Nothing is sent to players for a value that did not change** (Minecraft compares before sending).
- **The hand-off when it lets go of a wall (BUGS 53)** works: one collision check on the let-go tick. Keep it.
- **`AntMoveControl`** has no jump of its own and each aim lasts one tick: right for climbers.
- **The close-in walk** (`closeIn`, `stepToward`) starts fresh when the target moves, gives up after a second
  without getting nearer, and never steps off its own cell or floor.
- **Saving the hold:** written, read, and checked on the first tick before the body moves; unload, reload and a
  change of dimension all go through it.
- **No fall damage** (on purpose, the deep shaft); insects do not fling insects.
- **A royal resting while its colony is far** must be on the ground, so never rests mid-wall.

---

## Thread: the world changes under a walking insect (a Fable reader, across all the files)

**How a stale route is noticed.** Only the legs look, once a tick, and only at the one step being walked: (1)
`whatChanged` asks the world whether the foothold ahead and behind, the cells between and any mouth or channel are
still as planned, and plans again on that tick; (2) more than 3 blocks from the step: "knocked off", plans again;
(3) pushing 20 ticks without moving: "blocked", that foothold is avoided 20 s; (4) a step not done in 6 s (a fall
10 s, a doorway 20 s): "stuck". Steps farther on are not looked at until it gets there, which is right and cheap.
After one change only the insects whose step touches it plan on the next tick; the rest find out one by one, and
each pays its own search (a plugged hole is found again by every forager that walks up to it).

| What changes | What the insect does | Ends |
|---|---|---|
| A block put in the next cell of its route | Seen the next tick, plans round it | Well |
| The block it climbs is removed mid-move | Lets go, plans from where it will land, falls, walks on | Well |
| The wall or ceiling an idle insect holds goes | `hold` lets go, it falls | Well |
| A mouth reshaped mid-move (BUGS 6e) | Seen each tick; no crash path found | Well |
| Its own cell is filled (a mud plate, a layer on its ramp) | The next plan starts beside it and hops it out | Well, with a hop of up to a block |
| The goal cell is filled | Found on arriving; the new plan fails at once with no search | Well and cheap |
| The only way is shut | Found on arriving; one or two full searches, then the mind's own rule | R2-16, R2-37, R2-39 |
| It ends up on a path, farmland, or in a pond with a low bank | No start for any route | **Badly: R2-38** |
| A hit on a floor | Seen past 3 blocks, else by the 6 s clock | Well, late (R2-43) |
| A hit, a blast or a piston on a wall | It does not budge | R2-30 |
| Its chunk unloads while it walks a wall | Saved without its hold | Drops after the reload (R2-41) |
| Saved and reopened while it holds a wall | Hold restored and checked on the first tick | Well |
| Another world opened in the same sitting | The old world stays in memory | R2-22 |

### R2-37 (A, the loop proven and checked again; how dear each search is, to measure): five goals ask the legs for a route on every tick after "no way"

**Status (2026-11-26, `claude/review-fix-2`): fixed by R2-1, BUGS 400: the five goals still ask every tick, and get "no" without a search for half a second after each search.**

- **Where:** `entity/goal/WoundCareGoal.java` 103 to 106; `RescueGoal.java` 130 to 133 and 177 to 180;
  `ClampCareGoal.java` 104 to 107; `AllogroomGoal.java` 124 to 127 (this one ticks every tick, 94); likely also
  `FightGoal.java` 320 to 323 while a major bites a cover open. The legs' side: `AntNavigator.begin` 403 to 421.
- **What is wrong:** the four share the line `if (--replan <= 0 || navigator.isDone()) { replan = 20; moveTo(...) }`
  and throw `moveTo`'s answer away. When the search finds no way the legs are "done" at once, so `isDone()` is true
  on the next goal tick and the 20-tick clock never gets a say: a new search every goal tick until the goal's own
  give-up, 400 goal ticks (200 for grooming). BUGS 76 and 317 again, in five more places.
- **Checked again:** the four lines read as quoted; `isDone()` is `route == null`, and a failed `plan` leaves the
  route null. Each ask costs nearly nothing when the patient's own cell is closed to the carer (in water, in a
  termite tunnel block) or the pocket is small, and a whole search (R2-16) when the cell is open but cut off: a
  hurt ant behind a mud seal the termites shut again, BUGS 361's raiders caught in termite tunnels. Unchecked: how
  often that happens in play (BUGS 349 keeps care to nestmates at rest in a room, which makes it rarer there; the
  rescue goal works outside the nest).
- **What the player sees:** tick time jumping for 20 to 40 seconds while a hurt or clamped ant lies where its
  nestmates cannot get to it, most likely in a raid; the carers stand still.
- **Fix proposed:** R2-1, the memory in the legs: then every mind keeps the rule whether it remembered to or not.
  Recommended over patching five goals; a test that a carer with no way asks the legs' search once a half second
  at most.

### R2-38 (A, the legs' side proven and checked again; two facts about Minecraft from memory; not run): an insect on a path block, on farmland or in a pond with a low bank can never plan a route again

**Status (2026-11-26, `claude/review-fix-2`): small half fixed, BUGS 401: a start one step up beside it, corners too, and the shuffle no longer moves an insect onto ground the planner refuses (test `anInsectOnAPathBlockWalksOffIt`). The path half fixed after, BUGS 412 (R2-21). Open: an insect in a pond more than a block from its bank still has no start (BUGS 411).**

- **Where:** `AntNavigator.plan` 583 to 661, `currentFoothold` 2099 to 2148, `floorBeside` 2179; `AntPathFinder.
  landing` 345 to 360; `Foothold.isValid` 70 to 75; `LevelCellMap.isOpen` 39 to 56, `isSolidFace` 60 to 69. How it
  gets there by itself: `standable` 1485 to 1489, used by `keepApart` and `freeNear`.
- **What is wrong:** a player turns the grass under an ant into a path with a shovel (or hoes it; or a hit knocks
  an ant onto a village path; or it lands in a pond). The path is 15 pixels tall, so the ant's feet are in the path
  block's own cell, and that cell is not open. Then `currentFoothold` finds no floor (its cell is closed), no floor
  beside at its level (the neighbours are solid ground) and no wall; `landing` gives nothing (the cell is closed);
  the "shut in" branch looks at the five cells touching it: four are solid ground, the one above is air over a top
  that is not a sturdy face. No start, so `plan` fails without a search, and so does every later walk. The close-in
  step cannot leave the cell; `liftIfSunk` does nothing (it is on the block, not sunk in it). The same in still
  water level with its bank (the mod's own river and sand bank edges): the water cell is closed, the bank beside is
  solid. A bank two blocks high gives a wall and it climbs out.
- **It needs no player:** the crowd shuffle moves a standing insect to any spot with a collision box within 0.2
  below it (`standable`); a path's top is 0.06 below. A crowd waiting beside a village path can shuffle one of its
  own onto it. Savanna villages have such paths.
- **Checked again:** each lookup reads as said: `isOpen` wants an empty collision shape and no fluid; a floor wants
  its own cell open and the top below it sturdy; `landing` stops at the first closed cell; `floorBeside` looks only
  at the same height; the shut-in branch never looks one step up beside. From memory, not checked: that a path's
  and farmland's top is not "sturdy", and that a floating mob is not pushed sideways by itself.
- **What the player sees:** an ant or termite standing on a path or a field, or bobbing in a pond, for as long as
  it is watched (`Strays` folds a stuck insect into the numbers only unseen, after 3 minutes). Its goals fail
  cheaply, so no lag.
- **Fix proposed:** small and Claude's: the shut-in branch also takes the floors one step up beside it (the grass
  by the path, the bank), `stepOutTo` already hops; and `standable` asks the planner's question. Bigger and **the
  user's call** (R2-21): let the planner stand on any top the body really rests on.

### R2-39 (B, proven; to measure): a mind pays for the same "no way" again a tick later, and a builder can ask 24 times in one tick

**Status (2026-11-26, `claude/review-fix-2`): covered in part by R2-1 (BUGS 400): `Errand`'s second ask a tick later costs no search when the walk had no costs of the mind's own. `BuildMoundGoal`'s many asks in one tick are open (round 5).**

- **Where:** `entity/goal/Errand.java` 215 to 243 (`walkOn`, `goTo`); `savanna/.../BuildMoundGoal.java` 207 to 229.
- **What is wrong:** when the way changes under a walking termite and the new plan fails, the legs set `failed`;
  one tick later `Errand.walkOn` sees only "not walking" and sets off again (one or two more searches), and only
  that second failure makes it leave the thing alone: up to four full searches in two ticks for each termite
  arriving at one plugged hole. `BuildMoundGoal.walkToSpot` tries, in one tick, up to 4 spots, each from up to 3
  kinds of place, each plan up to 2 searches: at worst 24 for one cut-off builder (BUGS 148's builder that closed
  its own way up), and it gives no reach, so the "no foothold, no search" shortcut never helps it.
- **What the player sees:** maybe a stutter when a foraging hole is plugged with termites still out, or when
  builders are cut off on a mound.
- **Fix proposed:** `Errand.walkOn` treats a walk that ended in `hasFailed()` as "no way"; R2-1 covers the rest.
  `BuildMoundGoal` is round 5's: one spot a tick.

### R2-40 (B, proven): the "no foothold in reach, no search" shortcut helps one caller only

**Status (2026-11-26, `claude/review-fix-2`): fixed (no bug number, a B): `mayHaveGoal` asks only footholds that are really there.**

- **Where:** `AntNavigator.mayHaveGoal` 699 to 711; callers `FightGoal.java` 245, 272, 377; `ShapeMoundGoal` 99.
- **What is wrong:** it runs the mind's test on bare cells, not on real footholds. A test that only measures
  distance (all three in `FightGoal`) says yes to the first cell, so no search is ever saved there. The test is
  also run with no check that the land is ready: safe for a mound job near the insect, a trap for a far one.
- **Fix proposed:** ask `Foothold.isValid(map)` as well as the test (the map is guarded by `LandReady`).

### R2-41 (B, the read proven; what it does at unload reasoned, not run): saving an insect that is walking a wall reads the world and loses its hold

- **Where:** `AbstractAntEntity.java` 2905; `AntNavigator.getHold` 520 to 523, `pos()` 2444 to 2459,
  `widestBetween` 2405 to 2416 (a plain `getBlockState`, no `LandReady` check).
- **What is wrong:** with a route under way the hold is worked out from the world at save time. When a chunk is
  going, Minecraft saves its insects after it has stopped being a full chunk: the guarded reads answer "not ready",
  no hold is found and the insect is saved without one; and `pos()` still reads the wall's block straight from the
  world, which may bring the chunk back up for a tick.
- **What the player sees:** insects that were climbing when the player walked away are at the wall's foot on the
  way back; maybe a hitch when leaving a busy mound.
- **Fix proposed:** do not read the world to save: keep the hold of the step under way in a field as the walk goes
  and write that. Route the legs' seven direct block reads (1956, 1984, 2085, 2345, 2415, 2503, 2536) through one
  guarded helper.

### R2-42 (B, proven): four tests of "is this a doorway for this insect"

- **Where:** `LevelCellMap.isMouth` 95; `AntNavigator.doorwayAt` 2084 to 2088; `outOfMouth` 1956 and `intoMouth`
  1984; `Doorway.avoidMouths`; `isDoorwayStep` 994 to 998 mixes two of them.
- **What is wrong:** BUGS 6e was two of these disagreeing for one tick. Today they agree for the termite blocks. A
  new doorway block, or a mouth that lets one caste through and not another, will part them again, and `overTop`
  and `overEdge` (1642, 1670) switch between the doorway's motion and the plain one in the middle of a step.
- **Fix proposed:** one question on `CellMap`, asked by all; when its answer changes during a step, plan again.

### R2-43 (B, likely): knocked into a cell near its route, an insect finds out only after 6 seconds

- **Where:** `AntNavigator.java` 822 (the 3-block test), 840, `walk` 1086 to 1100.
- **What is wrong:** a hit that moves a walker less than 3 blocks (into a dip beside its way) is not noticed:
  `whatChanged` asks about the world, not the body. It runs at the wall for 6 s, then plans from where it is.
- **What the player sees:** an ant knocked into a dip pushes at its side for some seconds before it climbs out.
- **Fix proposed:** on a floor step, when the body stands on a floor that is neither the step's start nor its end,
  plan again at once (a check of two cells; no search unless it is true).

### The same question judged in more than one place (the thread's list)

- **"Can it stand here?"** the planner (`Foothold.isValid`); `standsOnFloor` 2201; `standable` 1485 (the shuffle);
  `stepToward` 363; and Minecraft's own collision. The shuffle's rule and Minecraft's collision accept ground the
  planner refuses: the way into R2-38.
- **"Is this step still good?"** `whatChanged`; the move's own code; "blocked" (20 ticks); the step clock (6, 10,
  20 s); "standing too long"; three plans a walk; and above the legs each mind's clock (`Headway`, the raid stages,
  `Errand`'s 20 s). Clocks covering clocks: R2-2, R2-9.
- **"Is this a doorway?"** R2-42. **"Is there a way?"** each mind with its own memory, or none: R2-1, R2-37.
- **"Is it in a channel?"** four places; no case found where they part.

### Solid on this thread (do not "fix")

- **A hold whose block is gone is let go, never floated on,** every tick and on the first tick after a reload.
- **On a wall the legs own the body:** the motion is set every tick after the goals and before Minecraft moves it.
- **Letting go is clean:** the motion is zeroed, it falls, takes no damage, and walks on once landed.
- **A filled cell under an insect** is left by a hop, walls included (BUGS 148).
- **The crowd grid survives gaps:** a missed tick reads as "nobody", so reloaded insects never steer round ghosts.
- **The legs themselves never search every tick:** at most three new plans a walk, none while idle.
- **Insects never choose to walk into water, fire, webs or powder snow,** and a shuffle never goes off an edge.
