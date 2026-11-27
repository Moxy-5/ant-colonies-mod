# Round 5a, reader G: how the mound grows and is built

Read at commit `4dcd1fe6` in `C:\Ant Mod`. No code changed, nothing run or measured.
Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`.

## G-1 (A, likely): shaping hands every builder only the one lowest job; when that one cannot be reached from inside, no termite carves or reshapes anything

- **What is wrong:** the list of shaping work (tunnels to carve, pieces to reshape) is searched for the single lowest
  job, and that one job is handed out. If no termite can get beside it from inside (a tunnel cell that must be carved
  from the next tunnel cell, which is not carved yet), the builder leaves it alone and gives up looking. It never asks
  for the next job. Every other builder is handed the same job and gives up the same way. So one unreachable low job
  stops all shaping on the mound, the jobs that would open the way to it included.
- **What the player would see:** builders standing idle while the mound has tunnels still to carve and spire pieces
  still to thicken; spires that stay thin at the bottom; a stage that only moves on after the 5-minute stall
  (`Mound.STALL_TICKS`), and the same job still lowest on the next stage, so it can last for the mound's life.
- **Where:** `savanna/colony/MoundShaping.java:48-70` (`pickShapeJob`: one `best`, the lowest, skipping only cells
  claimed by others); `savanna/entity/goal/ShapeMoundGoal.java:62-79` (`canUse`: left alone or no way, so release and
  return false, no second pick); `framework/entity/goal/Errand.java:38` (its own rule: "a goal's search for work skips
  what is left alone", `leftAlone()` 328-332) never passed to `pickShapeJob`; `savanna/colony/MoundGrowth.java:46-48`
  (the proof: "the order the termites pick does not matter").
- **The proof:** `pickShapeJob` walks every cell and keeps the lowest job (`pos.getY() >= best.getY()` skips the rest);
  ties at one height go to the first cell in the plan's own order, not the nearest to a channel. `ShapeMoundGoal.canUse`
  then either finds it left alone (`errand.isLeftAlone`, 400 ticks per insect) or `walkThere` fails at once (reach 1,
  no foothold beside it passes `MoundReach.canCarveFrom`), and returns false. Next look, the same cell comes back. The
  pretend mound that proves the stages buildable does every job it *can* do in a pass and loops (`MoundGrowth.java:590-
  624`), so it never meets this: the game's picker is not one of the orders the proof tries.
- **Unchecked link:** whether a real stage has, at some moment, a lowest carve job with no channel or shaft beside it.
  `docs/plans/apex-termites.md:55-57` (T4) says it did: "a tunnel cell planned at the mound's foot ... not yet joined to
  anything inside, searched for by every builder in turn every 20 s" (T4 made the failure cheap, not the builders move
  on). It is most likely when a new stage begins: `MoundGrowth.tunnelToTheOutside` carves new tunnels through soil built
  on earlier stages, so a whole chain turns into carve jobs at the same moment, and only its end beside a channel can be
  done first. On a mound built from nothing the build order (nearest spots first) mostly carves the near end first,
  which may be why `TermiteCathedralTests.termitesBuildTheYoungCathedralFromInside` passes.
- **Also on this path:** the builder numbers' catch-up out of sight carves with no reach rules
  (`MoundAwayBuilding.java:138-145`, near a player too since R1-36), so a colony with more workers than real ones may
  carve the stuck cell when nobody is looking. A small colony, all real, has no such help.
- **The fix proposed:** no new rule: one memory of "no way there" for the mound, used by both goals. `Mound` already
  keeps one for soil spots (`unreachable`, `markUnreachable`, 10 s, skipped by `pickBuildSpot`); `pickShapeJob` should
  skip those cells too and hand out the lowest job that is not marked, and `ShapeMoundGoal` should mark its failed job
  there instead of in its own errand. A test: a stage where the lowest carve cell is two cells from the shaft along a
  tunnel chain of finished soil; the builders carve the chain within a minute.

## G-2 (A, the path proven; how often likely): a window a builder bites in a tunnel stays open for ever if the builder dies, is folded into the numbers, or its land unloads while it works through it

- **What is wrong:** the window (the tunnel block's side opened to the outside air) is opened by the builder's goal and
  closed only by that goal's own `stop` or after the soil goes in. Minecraft does not call a goal's `stop` when the mob
  is removed (killed, unloaded with its land, or discarded when folded into the numbers). A tunnel block never closes a
  side toward open air by itself. So the window stays open in the world and in the save, and nothing on a living mound
  ever plugs it.
- **What the player would see:** a hole in the mound's skin that never closes; termites (only they fit the channel) can
  walk out of the mound through it by day, past the rule that they go out only by the foraging holes. The window can be
  held open a long time: a builder waits with it open as long as a creature stands in its spot (up to the 600-tick give
  up), so a player standing on a building spot at night and then leaving makes it likely.
- **Where:** `savanna/entity/goal/BuildMoundGoal.java:232-268` (`place`: opens with `TermiteTunnelBlock.setWindow`,
  waits `WINDOW_TICKS`, waits more while a creature stands in the spot, 267), `:156-166` and `:271-283` (only `stop`,
  give-up and success close it); `framework/entity/AbstractAntEntity.java:513-527` (`remove` stops no goals);
  `savanna/block/TermiteTunnelBlock.java:191-196` (`updateShape` keeps a side toward air as it is). The only other
  closers: `DeadMounds.java:552` (a dead mound) and the player's shovel.
- **The proof:** remove → `super.remove` → no `Goal.stop`; the window state is a block state, saved with the chunk.
  `MoundShaping.isCarveJob` (88-104) opens doors into the shaft but never closes a side toward outside air;
  `Mound.jobAt` sees a tunnel block in a TUNNEL cell as done.
- **Test gap:** `TermiteCathedralTests.openWindows` counts open windows at the end of a build, but no test kills or
  unloads a builder in the middle of a window.
- **The fix proposed:** let the mound, not the goal, own the windows: on the plan walk it already makes every 100 ticks
  (`Mound.isStageDone`, or `getBuildSpots`), plug any tunnel side open to air outside the plan's shaft that no builder
  with a live claim is working through. One check in a walk that runs anyway, no new number, and it also heals windows
  already in saved worlds. A test: a builder killed while its window is open; the window is plugged within 5 s.

## G-3 (A, to measure; a new side of R2-39 and R2-16): the soil carrier walks by its own rules, with up to 12 route searches in one tick and no shortcut for a spot it cannot reach

- **What is wrong:** `BuildMoundGoal` does not use the shared `Errand` the other goals walk with. In one tick it tries up
  to 4 spots, each from up to 3 kinds of place (inside, outside, 2 blocks away), and gives the legs no reach, so the legs'
  "no foothold near it, no search" shortcut never applies, and the legs' "asked the same in vain just now" memory skips
  every search with a test (all of these). R2-16 measured a failed search at about 20 ms. Three more holes in the same
  walk: when the legs give up a walk (stuck), the goal plans a new one at once (R2-2's loop, which `Errand.walkOn` fixed
  for the others); a spot given up after 30 s (`GIVE_UP_TICKS`) is not marked unreachable, so the next look (about a
  second) may pick the same nearest spot again; and arriving is judged by another test than planning for the outside
  and far reaches (`ant.blockPosition()` against the foothold's cell).
- **What the player would see:** short freezes beside a mound whose building has stalled (a player broke the tunnels, odd
  ground), because the fallbacks open exactly then: `Mound.mayUseFallbacks` (756-759) after a minute with nothing built.
  At worst 4 x 3 searches of up to two passes each (R2-16) in one tick for one carrier.
- **Where:** `savanna/entity/goal/BuildMoundGoal.java:206-230` (`walkToSpot`), `:168-190` (`tick`: navigator done and
  cannot work, so `walkToSpot` again), `:173-178` (give up, no mark), `:193-200` (`canWorkHere`);
  `framework/nav/AntNavigator.java:353-355` (the search without a reach) against `:363-365` (with one), `:904-916`
  (`askedInVainJustNow` returns false for any test), `:936-938` (`mayHaveGoal` needs a reach).
- **The proof:** every reach's test only accepts footholds next to the spot (`MoundReach.canBuildFrom` needs
  `MoundGrowth.isNear`; `withinReach` 26 cells; `FAR` 2 blocks), so a reach of 1, 1 and 2 is known and could be given.
  Not passed, so `mayHaveGoal` returns true and a full search runs. `failedRoutes` is reset in `start`, so the 4-spot
  limit is per start, and `markUnreachable` lasts 200 ticks while a look comes about every 20.
- **The fix proposed:** remove the goal's own walking and put it on `Errand` like `ShapeMoundGoal`: `setOff(where, spot,
  reach, speed)` with the reach of each kind, one spot tried per tick, `walkOn`'s handling of a walk the legs gave up,
  and the given-up spot into `Mound.markUnreachable` (the one memory of G-1). Arrival then judged by the same foothold
  test as planning. Measure the stalled-mound scene before and after.

## G-4 (B, to measure): the whole stage's plan is walked over and over by several askers

- **What is wrong:** `Mound.getBuildSpots` walks every cell of the stage (land check, block reads, `spotIn`, then a sort
  whose comparison reads a block each time) once a tick, and again after every layer, piece or shaping (`spotsTick` is
  reset by `addLayer` and `shaped`), so several placements in one tick mean several full walks. It is asked by each
  carrier's look, by `isBuildSpot` every tick a builder waits at its spot, and by `wantsSoil` on each digger's look and
  each bite. `MoundShaping.pickShapeJob` walks the whole plan again for each builder's look (every 40 ticks, every
  2 ticks in an insect's last 2 s before it counts as idle, `AbstractAntEntity.looksForWork`), with no memory that there
  was nothing to shape; for each cell it calls `getParts` two or three times, and each `getParts` works out the stage
  again (`getStage`, `getEarnedStage`, `getAllowance`). `isStageDone` walks it every 100 ticks.
- **What the player would see:** nothing alone; part of the tick time of a grown mound with 50 real workers.
- **Where:** `savanna/colony/Mound.java:578-603`, `:661-663`, `:799-810`, `:410-417`, `:443-459`;
  `savanna/colony/MoundShaping.java:48-70`, `:77-85`, `:88-117`.
- **The fix proposed:** the same once-a-tick list for shaping jobs as for build spots (lowest first, handed out by
  claims and skipping the "no way" memory: it is G-1's fix too), and the stage looked up once per walk, not per cell.
  Measure first at stage 32 with the real cap of workers: if the walks cost well under 0.5 ms a tick, leave it.

## G-5 (B, likely): the mound's growth rate was set for 60 workers; with today's 200 a full mound takes about a third of the 20 hours the user asked for

- **What is wrong:** the earned blocks per day are `blocks_per_day` 2 plus `blocks_per_day_per_worker` 0.25 for each
  worker (`Mound.getAllowance`, 306-319). `docs/HISTORY.md:1359` set 0.25 "to keep the user's 'a full mound takes 20+
  hours' ... a full mound in about 20 hours with 60 workers" (17 blocks a day). `macrotermes.json` `colony.max_workers`
  is 200 since the honest sizes (2026-11-11, `docs/HISTORY.md:636`), which did not touch the mound numbers: 52 blocks a
  day, so the same full mound in about 20 in-game days, about 6.5 hours, once the colony is full.
- **What the player would see:** a big colony's mound shoots up to full height in an evening; the design asks for a mound
  that keeps growing over many hours (CLAUDE.md "the mound keeps growing over many hours of play", `docs/DESIGN.md:25`).
- **Unchecked link:** the soil count of a full stage-32 mound is not written anywhere I found; the 20 hours of HISTORY
  imply about 1,000 blocks. Not measured.
- **The fix proposed:** a number only. **Owner's call:** today a full colony earns 52 blocks a day (full mound in about
  6.5 hours at 200 workers); my pick: `blocks_per_day_per_worker` 0.075 (17 a day at 200 workers, the old 20 hours),
  which keeps "weak early, scaling hard" (a young colony of 20 builds 3.5 a day). Real mounds grow over years, so any
  game number is a speed-up; the realism here is only that more workers build faster.

## G-6 (B, the path proven): reshaping a soil cell with insects in it leaves a tunnel block where the plan says soil, for good

- **What is wrong:** when a piece is filled up into soil and a termite stands in the cell, a tunnel block is put there
  instead of soil (so the termite keeps a channel). The plan still says soil (WALL), and `Mound.jobAt` finds no job for
  a tunnel block in a WALL cell, so it stays a tunnel. It is not in the nest's list of the mound's channels
  (`Mound.addInside` lists only TUNNEL, MOUTH and SPIRE cells), so a termite in it counts as outside.
- **What the player would see:** rarely, a stray channel through the mound; a termite crawling in it by day may be sent
  home as if it were outside.
- **Where:** `savanna/colony/MoundShaping.java:152-155`; `savanna/colony/Mound.java:519` (WALL: a tunnel block is
  neither empty, wet, crust nor a piece), `:428-440`.
- **The fix proposed:** the reshaping waits (returns false, as it does for a player or an animal in the cell) while a
  termite is in a cell the plan has as soil; only a TUNNEL cell is filled with a tunnel block. One branch less.

## G-7 (B, proven): the proof and the game judge "what job is here" in two places that already differ

- **What is wrong:** `MoundGrowth.PretendMound.jobAt` and `hasSupport` (419-460) and the game's `Mound.jobAt`,
  `spotIn`, `hasPieceSupport` (512-525, 607-658) are written apart "by the same rules". They differ already: the game's
  carve job also covers a tunnel's door into the shaft, wet and mud-crust cells count as empty, and a cell holding water
  is no job at all in the game but is "empty" in the pretend mound; and the game's job order (G-1) is not the proof's.
  `MoundGrowthTest` checks only the pretend mound.
- **What it costs:** the "never gets stuck" promise (the user's main worry, `docs/DESIGN.md:44`) rests on the two staying
  the same, with nothing to catch them drifting.
- **The fix proposed:** a unit or game test that builds a stage in the game by `Mound.jobAt` in a random order of the
  jobs it may do now, the way the proof does, and compares with the plan; or one job rule both call (the game's reading a
  block, the pretend mound's reading its map) behind a small "what stands here" question.

## G-8 (C, proven): the "clear a piece" branches of shaping are dead, and the comments still promise them

- `Mound.jobAt` gives CLEAR only for OPEN cells (518), and `MoundShaping.shapeJobAt` drops OPEN cells (80), so the
  `else` branch of `MoundShaping.shape` (168-170, `destroyBlock`) and the non-OPEN branch of `MoundAwayBuilding`'s CLEAR
  (154-156) never run. The class comment (`MoundShaping.java:27-32`, "pieces cleared away where the plan has nothing")
  and `pickShapeJob`'s (45-46, "a piece to clear away where soil goes") describe work done elsewhere
  (`Mound.crumbleLeftovers`) or done by filling up. **Fix:** remove the branches, put the comments right.

## G-9 (C, proven): comments in `BuildMoundGoal` disagree with the code

- `MAX_LOOKS_WITHOUT_SPOT` (45-49) says a look comes "every half second or so, so about 15 seconds": `canUse` runs every
  2 ticks and passes 1 time in 10, so a look is about once a second and the pellet is dropped after about 30 s.
- The class comment (37-38) says "with no spot free it keeps its pellet until one is"; it drops it after those 30 looks
  (`leaveSoilBehind`, 135-142). **Fix:** the comments.

## G-10 (C, proven): log lines at INFO level in normal play

- `BuildMoundGoal.java:174-175` (every give-up) and `:215-216` (every walk built from outside or far) write an INFO line;
  on a stalled mound that is a line per carrier per walk. `docs/plans/code-review.md` 1j. **Fix:** DEBUG, as line 224.

## G-11 (C, proven): reshaping "through a window" never bites one

- `ShapeMoundGoal` reshapes from a channel by `MoundReach.canBuildFrom` with windows (86-87) but never opens a window
  (no `setWindow`), unlike `BuildMoundGoal`; the design's look (`docs/DESIGN.md:45`: bite a hole, shape the ramp beside
  it, plug it) shows only for soil. Looks only. **Fix:** if wanted, the same window as G-2's mound-owned windows.

## G-12 (C, likely): a termite in a spire's channel is buried when the piece is reshaped into a solid one

- `MoundShaping.shape` (161-166) does not lift an insect that is in a channel, on the idea that the new piece keeps a
  channel. A spire piece reshaped into the tip (thickness 4, `TermiteSpireBlock` has no channel there) has none: the
  termite is left inside solid soil. Needs a spire whose top moves (a leaning spire), so rare. **Fix:** treat "the new
  piece has no channel" as "lift it" (`Channel.canJoin` of the new state).

## G-13 (C, proven): small block leftovers

- `TermiteTunnelBlock.useItemOn` (225-243): any player's shovel opens or closes a window, "for testing by hand", also in
  survival. It lets a player open a way out for the termites. **Fix:** creative only, or remove.
- `TermiteTunnelBlock` has no `rotate`/`mirror` for its six open sides (`TermiteRampBlock` has them): a structure placed
  rotated would get its channels facing the wrong way. Harmless while mounds are put up by code.

## G-14 (C, to measure; a side of M-1): where the second of growing a mound goes

- `MoundGrowth.PretendMound.build` (590-624) works out the whole reachable set again after every carve, clear or
  reshape (`reachable()`, a full walk), and `tunnelToTheOutside` (724-749) works out every outside block's reach again
  (a sorted walk of every cell, 27 checks each) after every single tunnel it carves, up to 2,000 rounds, trying a
  cheapest-path search for each missing block until one carves. That is likely most of the 1.2 to 1.8 s that reader M's
  M-1 finds run on the game's thread. **Fix:** only if M-1 is not fixed by keeping the growth off the game's thread:
  grow the reachable set on from the new tunnel instead of anew, and carve for every missing block in one round.

## What is solid

- **The building rules and their proof:** `MoundGrowth` grows every stage in a pretend mound with the termites' own
  rules, carves only where they would get stuck, and keeps tunnels under the skin; the loop always ends (every job moves
  a cell one way only, carving and kept soil are finite). The shared rule pieces (`canJoin`, `canOpenWindow`,
  `windowPast`, `isNear`) are used by both the proof and `MoundReach`, so the windows and reach are one rule.
- **Claims:** both claim maps are keyed by the insect, expire on their own (600 ticks) and are pruned on each pick, so a
  dead or unloaded builder never holds a spot for long; `releaseBuildSpot`/`releaseShapeJob` on `stop`. Unreachable
  soil spots are shared by the whole colony and pruned.
- **Never burying anyone:** `addLayer` and the reshape refuse a cell with a player or another creature in it and lift
  termites onto the new layer; the away building checks `freeAndHidden` (nobody in the cell, nobody looking).
- **Day and night:** windows only at foraging time (night, rain), by day only work reachable from the shaft
  (`isWorkableNow`), the same test for picking spots, wanting soil and shaping: real Macrotermes build mostly at night
  and after rain, from inside, with soil pellets, as here. Soil kept in step: one dug block gives one mound block, near
  (`takePellet`) and away (`dug`, `takeSoil`), and diggers stop when no spot can take soil.
- **The away build** follows the plan lowest first, a stage finished before the next, inside the second's limits on
  changes and plan walks (`takeMoundPass`, 8 a second), and waits for unseen, unoccupied cells.
- **The blocks:** the tunnel's shapes are worked out once (64 masks); it joins like a fence and never opens to air on its
  own; termites alone fit the channels (entity tags); ramps join like stairs with vanilla's rules, shared with the plan;
  pieces age with random ticks only until old; drops have loot tables for every piece.
- **Layering:** both goals only name a foothold test and a cell to the legs; no vanilla navigation or move control.

Read in full: `MoundGrowth.java` (1-784), `MoundShaping.java` (1-176), `MoundAwayBuilding.java` (1-189),
`BuildMoundGoal.java` (1-284), `ShapeMoundGoal.java` (1-157), `TermiteTunnelBlock.java` (1-244), `TermiteRampBlock.java`
(1-149), `TermiteMouthBlock.java` (1-144), `TermiteSpireBlock.java` (1-135), `MoundPieceBlock.java`, `MoundReach.java`.
Read in part: `Mound.java` (60-1019), `MoundPlan.java` (120-210, 310-370), `BuildLife.java` (the hook and limits),
`Errand.java` (1-80, 184-232, 322-337), `AntNavigator.java` (310-365, 469-529, 904-943), `TermiteWorkerEntity.java`
(goal table), `TermiteCathedralTests.java` (34-243), `macrotermes.json` (mound section), `docs/DESIGN.md` (12-61).
Not read: `TermiteMoundBlock` (its `canStartAt`, `addLayer`, `isRoomForSoil` taken on trust), `MoundSoil`, the other
game tests named in the brief beyond their names, `MoundGrowthTest`.
