# Full code review, round 1: the colony brain (reading round, 2026-11-25)

Read on branch `claude/gait-rule` at commit `4deaeb78` (it holds `claude/review-fixes` and so `master`). Rule book:
`docs/plans/code-review.md`. No code was changed, no test was run and nothing was measured in the game, so no lag
finding here has a number yet.

**What was read:** the framework's `colony/`, `species/` and `season/`, 46 files, about 16,600 lines.

**Who read it (the user's setup, 2026-11-25):** four Opus readers, every line, one slice each with its own files (the
colony core; the nest; food and field; young, danger and species), and two Fable readers following a thread across the
whole brain (the colony's tick near and away; save, reload and lifetimes). The main session then checked each grade A
finding against the lines it names; those say "checked again". The others carry their reader's proof only.

**How to read a finding.** Rating: **A** the player can meet it, **B** it will cost us when building on this,
**C** a tidy-up. Proof: **proven** (the path was walked through the code, lines named), **likely** (one link, named in
the finding, was not checked), **to measure** (a lag finding with no number yet). A finding rated A gets its
`docs/BUGS.md` number when a fix session takes it up. Findings keep their numbers for good (numbered in the order the
slices came in, not by grade); a fix session adds a status line under a finding and never rewrites it.

**Count:** 50 findings: 15 A, 20 B, 15 C. All six readers are in; the round is read.

## The short of it

The brain's skeleton is sound: the parts and their sign-up, the tick slots, the claims, the season and species-name
rules, the headcount when insects die, grow old or starve, nothing left over between worlds, and no away path that
loads land by itself. What is wrong sits in four places:

1. **The edges of loaded land.** A family of block reads guards the cell but not its neighbour, or reads first and
   asks after (R1-7, R1-8, R1-18, R1-47): freezes near big mounds, in reach at the user's view distance of 10.
2. **The change between bodies and numbers.** An insect folded twice (R1-43, with the drift it leaves visible in the
   user's own saves), a body left behind (R1-45), loads lost (R1-28), insects in limbo (R1-29), a Matabele queen who
   lays only as a number (R1-1).
3. **Near and away living at different speeds, and the clock.** A slept night costs the termites their foraging
   (R1-35), builders that are numbers work only away (R1-36), all numbers eat and starve in the same second (R1-37).
4. **The save.** One typo in a species file empties the world's colonies (R1-42); the colony file is rebuilt whole at
   every autosave and grows with the land explored (R1-44); colonies are never ended while away (R1-27).

Plus plain lag in the nest: every builder scanning the whole nest for leaks (R1-16), `nestCells` built anew on every
call (R1-21), a second's worth of block changes in one tick on return (R1-17).

## The ones that matter most

1. **R1-42:** a typo in a species file, and the world opens with every colony emptied, and saves it so.
2. **R1-1:** a Matabele queen never lays while a player is near (since M8).
3. **R1-35:** sleeping every night starves grass-fed termite colonies across the world. **The user's call.**
4. **R1-43:** insects, food and young counted twice at the edge of loaded land; seen in the user's saves.
5. **R1-16 and R1-21:** the leak scan and `nestCells`, lag that grows with the mound.
6. **R1-8, R1-18, R1-47:** reads that load land on the spot.
7. **R1-19:** a foraging hole left under the soil on a hillside.
8. **R1-45:** extra Matabele minors each time a far scout is shown.

## For the user to decide

- **R1-35 (sleeping):** recommended: a skipped night is foraged as numbers (the termites foraged, the player slept).
- **R1-36 (builders near):** recommended: builder numbers work near a player too, out of sight, so a watched mound
  grows at its full pace. It makes watched mounds grow about four times faster than today.

## What this round did not do

Nothing was measured in the game: every lag finding is "what runs, how often, over how many things" (the plan asks for
numbers; a fix session measures before and after). No test was run. The species side was read only where the brain
calls it. `docs/BUGS.md` was searched by class name, not read whole.

---

## Slice: young, danger and species

`Population`, `Brood`, `QueenCare`, `Refuse`, `DangerMap`, `Neighbours`, `species/*`, `season/Seasons`. Every line read.

### R1-1 (A, proven; checked again): a Matabele queen never lays while a player is near

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 392 (`QueenCare.layCarriedEgg`; test `aFedMatabeleQueenLaysHerEggWhileAPlayerIsNear`).**

- **What is wrong:** the workers feed the queen (`FeedQueenGoal`, added in `MatabeleEntity:203`); a full meal sets
  `eggDue` (`QueenCare.feedQueen`, 135 to 155). The only code that lets a carried egg out is `eggCameOut`, private,
  called once, inside `liveAway` (`QueenCare:395-397`), which runs only while the colony is away.
  `megaponera_analis.json` has no `laying` section, so the termite queen's stream (`layInStream`, `layStreamEgg`) is
  not hers either. `MatabeleQueenEntity` (155 lines) calls `queenCare()` once, for `queenStarves` (line 123).
- **Checked again:** grep finds no other caller of `eggCameOut` or `isEggDue`. The reader blamed the harvester
  removal; the history says otherwise: at the tag `harvester-last` the public `layEgg` was called only by the desert
  queen (`QueenAntEntity:114`), never by the Matabele queen. So she has not laid near a player since M8.
  `docs/DESIGN.md` has no note that Matabele young are meant to be numbers only.
- **Also:** while the egg stays due, `needsFeeding` fails on `eggDue >= 0` and `hungerRunsOut` counts her as fed: a
  watched queen cannot starve.
- **What the player sees:** a Matabele colony you stay beside never gets new eggs or larvae and only shrinks; one you
  leave grows, and has young when you come back.
- **Unchecked link:** that `isQueenHome` is true in a Matabele nest (the same check is used away, where she does lay).
- **Fix proposed:** a public "the carried egg comes out here" in `QueenCare` (`eggCameOut` and `brood().spawnEgg`),
  called once a second by `MatabeleQueenEntity` when `isEggDue && isQueenHome`, as the termite queen does for her
  stream. A game test: a fed queen near a player has an egg after the incubation time.

### R1-2 (A, proven; checked again): a broken species file crashes the game while the log says "using the built-in numbers"

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 387 (with R1-42).**

- **What is wrong:** `SpeciesProfiles.builtIn` (line 33) is only ever read (66 to 68); its writer went with the
  harvester (commit `20cc23b4`). A file that fails to read is logged as "is broken, using the built-in numbers"
  (line 43) and left out; then every `SpeciesProfiles.get` throws "No species profile for ... and no built-in numbers
  to fall back on" (line 70). `Colony.profile()` is asked every tick.
- **Checked again:** grep finds no `builtIn.put` anywhere.
- **What the player sees:** the user is meant to tweak numbers in these files. One typo, or a missing required field,
  and the world crashes on load or on the first colony tick; a `/reload` with a typo crashes a running world.
- **Fix proposed:** on a broken file at `/reload` keep the last good profile; on first load stop the loading with a
  message naming the file and the field. Delete `builtIn` and put the log line and the comments right.

### R1-3 (B, proven by its reader): a young colony that once yielded to a neighbour yields again after one meeting

- **Where:** `Neighbours.java:166-173` (`yields`), 132 to 137 (`met`).
- **What is wrong:** `meeting.yielded` is set true once the strength reaches the meetings its age needs and is never
  set back, not even when the strength has faded under half. Macrotermes: 4 meetings for a young colony, forgotten
  under 0.5, half-life 2 days. A place faded under 2 lingers about 4 more days with `yielded` still true; one new
  meeting then makes the colony turn away at once.
- **What the player sees:** young termite colonies push on only the first time, then back off at the first touch (the
  opposite of the young-colony rule, Gordon and Kulig).
- **Fix proposed:** in `yields`, clear `yielded` once the strength is under half of what is needed.

### R1-4 (B, proven by its reader for one field): some numbers in the species file have no range check, and a 0 crashes

- **Where:** `QueenCare.java:224` and `371` (`now % founding.feedIntervalTicks() < 20`); `SpeciesProfile.java:902-908`.
- **What is wrong:** `founding.feed_interval_ticks: 0` passes the reader; `now % 0` throws once a second.
- **Fix proposed:** `Codec.intRange(1, ...)` on the interval and count fields, so a bad value stops the loading with a
  clear message (it meets R1-2: a clear stop instead of a crash).

### R1-5 (C, proven by its reader): the founding egg's spot is rolled with the queen's own dice

- **Where:** `QueenCare.java:216`, `colony.brood().pickEggSpot(level, queen.getRandom())`.
- **Fix proposed:** `colony.random()` (the mod's own rule: colony dice from `Colony.random()`).

### R1-6 (C): comments that still describe the harvester

- **Where:** `DangerMap.java:31-35`, `301`; `Neighbours.java:25-27`, `207-208`; `SpeciesProfiles.java:55-58`;
  `SpeciesProfile.java:35-36`, `1155`; `SpeciesBodies.java:24`; `SpeciesSection.java:19-20`.
- **Fix proposed:** reword when these files are next touched.

### Solid in this slice (do not "fix")

- `Population`: the headcount holds when mobs fold in and come out; counts cannot go negative; old age the same near
  and away; at most `swaps_per_second` swaps; going in only out of sight; save and load complete.
- `Brood`: young as numbers saved with the soldier rank; `comeOut` removes through the iterator; `isFull` counts real
  and number young together.
- `QueenCare`: the termite stream and the founding batch use one clock near and away; save and load complete.
- `Refuse`: claims run out; searches bounded to the colony's ground.
- `DangerMap`: fades by the game clock, also away; `seenAt` and `wounded` kept under 256; pruned once a second.
- `Neighbours`: `metAt` capped at 256; meetings with dead colonies fade away by themselves.
- `SpeciesProfile`: required and optional fields match both savanna files; a malformed optional value is an error.
- `Seasons`: one source, a fixed default; nothing in this slice reads the date, rain or heat.
- **Not proven, so not a finding:** whether `Population.goIn` can fold a marching raider or a scout into the numbers
  (`mayGoIntoNest` does not ask about raid or scout jobs).

---

## Slice: food and field

`FoodStore`, `FoodWays`, `FoodMap`, `Meals`, `WorkerFood`, `NurseFood`, `ForagingHoles`, `ForagingTrails`, `FieldLife`,
`RunwayRoute`. Every line read.

### R1-7 (A, proven; checked again): the new-holes check reads a far food block in land that is not loaded

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 390.**

- **What is wrong:** `ForagingHoles.tick` (every 200 ticks, line 375) walks every new tunnel. Lines 387 and 397 guard
  their reads with `LandReady.at(level, tunnel.food())`, but when the land is not loaded the code falls through to
  line 406, `isSmall(level, tunnel.food(), numbers)` (Macrotermes has `small_pieces` 8, so it always runs). `isSmall`
  starts with `pieces(level, pos)`, and `pieces` (line 875) is `plantFor(level.getBlockState(pos))` with no check;
  only the second read (866) is guarded. The same family as BUGS 316, at other lines.
- **What the player sees:** food lies up to the galleries' reach (50) from the nest. A colony at the edge of the
  loaded land loads (or makes) that chunk on the spot every 10 seconds for each such tunnel: a freeze each time.
- **Unchecked link:** how much land round the nest must be loaded for this part to tick at all (the tick reader's
  thread); with a smaller need than 50 blocks the path is open.
- **Fix proposed:** in the loop, skip a tunnel whose food is not loaded before line 406.

### R1-8 (A, proven; checked again): `ForagingHoles.isOpen` reads the block before it asks whether the land is loaded

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 390.**

- **What is wrong:** line 201 `BlockState state = level.getBlockState(hole);` and only then line 202
  `return LandReady.at(level, hole) && ...`: the guard comes after the read it is there to prevent. Callers with no
  guard of their own: `FoodWays.runwayPlant` (207, each time a worker asks what to dig next in foraging time),
  `NestWalls:111`, the savanna `Soldiers:234`. `touchesFluid` (936 to 943) reads six neighbours' fluids with no check
  at all (from `mayOpen`, 225, and `RunwayLand.isFree`, 634).
- **What the player sees:** a chunk loaded on the spot whenever a runway's hole lies in land that is not loaded.
- **Unchecked link:** that a runway's hole can be in unloaded land while its colony ticks.
- **Fix proposed:** swap the two lines in `isOpen`; give `touchesFluid` the guard (unloaded land counts as no fluid).

### R1-9 (A, proven; checked again): "is anyone still outside" looks only 32 blocks round the nest

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 393 (no test of its own).**

- **What is wrong:** `wantsPlugging` (254 to 264) plugs the holes when the foraging time is over and either 600 ticks
  have passed or `!isAnyoneOutside`. `isAnyoneOutside` (276 to 285) searches `colony.nestArea().inflate(8)`;
  `nestArea` (`Colony:736-739`) is `max(24, food radius)` sideways, 24 for termites, so 32 blocks. Foraging holes
  along the galleries lie out to 50. With every forager past 32 blocks the answer is "nobody outside", the grace is
  skipped and every hole is plugged at once. `ForagingTrails.look` (183 to 184) counts the workers outside over
  `foragingRadius() + 8`: two places judging the same thing over two areas.
- **What the player sees:** at dawn the holes shut while the far foragers are still walking home; termites left on
  the surface by day.
- **Fix proposed:** one judge: `isAnyoneOutside` asks the trails' count (or uses its area). The trails count workers
  only and this check any member, so soldiers need a look first.

### R1-10 (B, proven by its reader; to measure): a colony with no food plants still scans about 2,400 columns every 5 seconds

- **Where:** `FieldLife.java:104-109`, `145-157`; `FoodStore.java:343-359`, `441-471`, `485-512`;
  `megaponera_analis.json:57` (`"plants": []`).
- **What is wrong:** `FieldLife` is a shared part, so it ticks for Matabele colonies too; `look` asks
  `food.plants(level)`, a 49 by 49 scan (a heightmap read and up to 2 block reads a column, up to 48 more under a tree
  crown), all of it thrown away since no block can match.
- **Fix proposed:** `plants()` returns the empty list at once when the species has no food plants.

### R1-11 (B, proven by its reader; to measure): once a second every outside worker is compared with every plant

- **Where:** `FoodMap.java:117`, `125-156` (the loop at 151 to 155).
- **What is wrong:** workers times plants; dry grass is termite food, so thousands of plants in a savanna. About 50
  workers times 3,000 plants on one tick each second, for each colony.
- **Fix proposed:** the cheap distance test first; better, a map by column built once a scan.

### R1-12 (B, proven by its reader; to measure): each forager's plant search counts every claim for every plant

- **Where:** `FoodStore.java:283`, `286-300`, `536-544`; `ForagingHoles.java:105-114`, `190-197`.
- **What is wrong:** `findGrass` (every forager, every 40 ticks) first runs `waysOut`, an `isDug` over every tunnel
  cell (galleries up to 600 long), then for nearly every plant `foragersAt`, a walk over all claims.
- **Fix proposed:** count the claims into a map once a call; keep `waysOut` for the tick, as `hasFinishedRunway` does.

### R1-13 (B, proven paths, how often unchecked): the store's room is judged in three places by three rules

- **Where:** `FoodStore.java:170-173`, `194-213`, `705-725`, `736-738`; `NestLedger.java:148-163`;
  `FieldLife.java:369`, `501-511`.
- **What is wrong:** `roomInStore` (real foragers) skips cells whose floor holds no heap (BUGS 95); `watchRoom` (the
  numbers) does not. The real foragers' room ignores layers the numbers have counted in but not put down; the numbers'
  room ignores places real carriers hold. So the numbers cut plants for room that is not there, and
  `NestLedger.addToStore`, finding no spot, drops the piece (`store--; food.used(1)`).
- **What the player sees:** dead wood and grass eaten away out of sight while the comb does not grow.
- **Fix proposed:** one method answers "room in the store" for both, taking off both the owed layers and the held
  places, with the floor check.

### R1-14 (B, likely): a found plant far out is read with no load check once its finder is home

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 390.**

- **Where:** `FoodMap.java:141-143` (`news.removeIf(... level.getBlockState(plant) ...)`); the same file guards the
  matching read at 119.
- **Unchecked link:** whether the plant's chunk can unload while the worker walks home.
- **Fix proposed:** `LandReady.at(level, plant) &&` in front.

### R1-15 (C, proven by its reader): tidy-ups in the food store

- `FoodStore.load` (1077 to 1080) adds to `grassClips` without clearing it first (every other part's `load` clears).
- `seedsDropped` is counted (`StoreFoodGoal:121`) and never read.
- The rich-seed and pumpkin-seed path and the comments at 34 to 40, 111 to 115, 216 to 218 describe the harvester.

### Solid in this slice (do not "fix")

- The bookkeeping between the count, the ledger and the world's layers: `countIn`, `takeCounted`, `takeSeedAt`,
  `depositSeed`, `NestLedger`'s add and take.
- Real foragers and the numbers share the pieces-cut count and the claims: no plant is counted twice.
- `FieldLife.work` and `showCuts`, `FoodStore.showCuts`, `eatAway` and `isFeedableFromInside` guard every block read
  (BUGS 316); the BUGS 40 fix is in place.
- `Meals` (hungriest first, starving, `Colony.random()`, saved); `WorkerFood` (a started piece shared by real workers
  and numbers, saved).
- `FoodWays`: save and load complete and clearing first; the ticks walk copies.
- `ForagingHoles`: plug claims, the per-tick runway cache, the pruning of `noRouteUntil`, `crumble`, and `plug()`
  refusing to bury anything not of the colony.
- `RunwayRoute`: a bounded search (4,000). `ForagingTrails`: visits capped at 128, `lastExit` at 512.
- No species named, no `level.getRandom()` for colony dice, no season read.

---

## Slice: the nest

`Nest`, `NestWalls`, `NestPlan`, `NestLife`, `NestLayout`, `NestLedger`, `TunnelRoute`, `BuildLife`, `GroundReader`,
`GroundTop`. Every line read.

### R1-16 (A, proven; checked again; to measure): every builder looking for work scans the whole nest for leaks

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 394 (not measured).**

- **What is wrong:** `SealGoal.canUse` (62 to 70) runs for every builder each time `looksForWork(20)` lets it and calls
  `colony.walls().findLeak`, which calls `NestWalls.getLeaks` (61 to 90) with nothing kept between calls. `getLeaks`
  builds `nestCells` anew, flood-fills every joined cell, and for each cell and each of its 6 sides reads a block and
  asks `foragingHoles().isHole`, itself a walk over every tunnel (`ForagingHoles:128-135`); a second 6-side loop looks
  for walk-through food; every leak found runs `findPlugSpot`. So about cells times 6 times holes, and every builder
  gets the same answer. `canContinueToUse` adds `isLeakOpen` every tick for each sealer.
- **What the player sees:** tick cost that grows with the mound and with the number of real builders; with 15 to 20
  builders about one whole scan a tick.
- **Fix proposed:** keep the colony's leak list, worked out at most once every 20 ticks (as `isInside` keeps its cells
  for the tick); only the cheap claim and score loop runs for each ant.

### R1-17 (A, proven; checked again; to measure): on return the nest ledger does a whole second's changes in one tick

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 395 (one budget of 40 a second; not measured).**

- **What is wrong:** `AwayLife.settle` (once a second, line 514) calls `colony.ledger().apply`. `apply`
  (`NestLedger:94-103`) runs `applyBodies`, `addToStore`, `applyNurseFood`, `applyAges` and `takeFromStore`, each up
  to `PER_SECOND = 200` changes, all in that one tick, each try working its room list out again. `BuildLife` has
  `PER_TICK = 20` for exactly this ("a colony coming back froze the game about 200 ms once", 2026-11-10); the ledger
  never got it.
- **What the player sees:** a freeze when walking up to a colony that was away for long.
- **Fix proposed:** the same slice of about 20 changes a tick, run from `catchUpSlice`; each room list worked out once
  a slice.

### R1-18 (A, the reads proven and checked again; how often, likely): reading a cell's neighbours can load land that is not ready

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 390 (one `Nest.touchesFluid`).**

- **What is wrong:** `GroundReader.mayDig` and `isDug` (78 to 95) check `LandReady.at` for the cell itself and then
  call `touchesFluid` (97 to 104), which reads the fluid of all 6 neighbours with no check. `TunnelRoute.plan` searches
  up to 6,000 columns outward; its edge stops at the last loaded column, whose sideways neighbour is in the next
  chunk. The same unguarded neighbour read: `Nest.touchesFluid` (every `firstDiggable`, 1084 to 1086, 1144 to 1151),
  `NestLayout.allSoilOrAir` (248) and `isCovered` (234, 236), `NestWalls.hasWallToClingTo` (688). Together with R1-8's
  `ForagingHoles.touchesFluid` this is one family: the cell is guarded, its neighbours are not.
- **What the player sees:** stalls of half a second or more when a colony near the edge of loaded land plans a
  gallery or digs at a chunk border.
- **Fix proposed:** one shared "touches fluid" helper in which a neighbour that is not `LandReady.at` gives the
  careful answer (fluid, not covered, no wall), used by all five places.

### R1-19 (A, proven in `Nest`; checked again; how often, likely): a foraging tunnel not fitted to a hillside the first time is never fitted again

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 391 (test in `TermiteHillTests`).**

- **What is wrong:** `getForagingTunnels` (860 to 875) runs `fitToGround` when `planned != foragingTunnelsFor` and then
  sets `foragingTunnelsFor = planned` whether or not the fit finished. In `fitToGround` (907 to 947) a tunnel whose
  hole's chunk is not ready is added flat and `allLoaded = false`, so `fittedTunnels` stays null; the layout object
  does not change in a sitting, so the fit is not tried again until a reload. Termite holes are 8 blocks out, often in
  the next chunk, and `nestCells` asks for the tunnels from the colony's first tick. After a reload `opened` (929 to
  931) keeps the flat tunnel if its hole was dug meanwhile.
- **What the player sees:** on a slope, a foraging hole under the soil the termites cannot get out of (the bug of
  2026-10-14 again), for colonies made as their land loads.
- **Fix proposed:** set `foragingTunnelsFor` only when the fit finished.

### R1-20 (A, likely; checked again): while no nest plan fits, the room planner runs on every call and a shaft is dug anyway

**Status (2026-11-25, `claude/review-fix-1`): half fixed, BUGS 396 (the planner waits 40 ticks; the shaft with no plan is left).**

**Status (2026-11-26, `claude/review-a-left`): fixed: with no nest plan no shaft is dug (`Nest.findDigTarget`); test `whereNoNestFitsTheFoundersDigNoShaft` (one block of soil over rock: the founders dig nothing).**

- **What is wrong:** `getLayout` (1097 to 1106): `if (layout == null) layout = new NestLayout.Planner(...).choose(level)`.
  `choose` gives null when nothing fits (and when land is not loaded); nothing is remembered, so every call plans
  again: about 1,000 `LandReady` checks, then 2 depths times 4 directions of rooms, each cell with its 6 fluid reads.
  `getLayout` is called many times a tick for each colony (`nestCells` alone 3 times). Meanwhile `findDigTarget`
  (237 to 245) digs `shaftDepth(level)`, the default 3, with no plan.
- **Unchecked link:** whether a colony can stand where no plan fits (a queen put on thin soil over stone or beside
  water; ground that turns bad later).
- **What the player sees:** such a colony lags the server and sits at the foot of a 3-block hole for ever.
- **Fix proposed:** remember "no plan" with the game time and try again every few seconds; no shaft before a plan.

### R1-21 (B, proven by its reader; to measure): `nestCells` is rebuilt on every call

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 394.**

- **Where:** `Nest.java:826-854`, `977-1021` (998 to 1000), `702`, `1081-1083`; `FoodWays.java:166-167`;
  `NestWalls.java:62`, `176`, `295`, `410`, `478`, `540`, `587`.
- **What is wrong:** a new set each call (shaft, layout, the mound's tunnels, every foraging tunnel, the galleries).
  `isInside` keeps its answer for the tick, but working it out asks `isRunwayClosed` for each runway, one more whole
  rebuild a runway, every tick.
- **Fix proposed:** keep `nestCells` like `inside`: once a game tick, cleared by `waysChanged()` and a layout change.
  This also takes most of R1-16's cost.

### R1-22 (B, proven by its reader): the catch-up digs the founders' chamber by weaker rules than the real diggers

- **Where:** `BuildLife.java:279-301`; `Nest.java:605-639` (630 to 637); against `Nest.java:1084-1086`.
- **What is wrong:** real diggers take a block only through `firstDiggable` (a place to stand, no fluid beside it);
  `foundersDigLeft` lists every soil cell with neither check and `settleFounders` digs them all. Two places judging
  "may this block be dug". Water come beside the chamber since the plan floods it on return.
- **Fix proposed:** `foundersDigLeft` leaves out (or stops at) cells that touch fluid.

### R1-23 (C, proven by its reader): progress on a leak or a dug block is cleared only when the job finishes

- **Where:** `NestWalls.java:41`, `164-166`, `462-463`; `SealGoal.java:129`; `Nest.java:44`, `1153-1160`.
- **What the player sees:** a breach bitten open again is sealed by one pack instead of several.
- **Fix proposed:** drop `sealProgress` entries no longer open, in the pruning `findLeak` already does.

### R1-24 (C, proven by its reader): `Nest.isEntranceOpen` reads the block before asking whether the land is ready

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 390.**

- **Where:** `Nest.java:743-746` (the same slip as R1-8). **Fix proposed:** `LandReady.at` first.

### R1-25 (C): two comments sit above the wrong method

- **Where:** `Nest.java:953-964` (`isInside`'s comment above `isFeedingWay`); `NestWalls.java:575-586` (`findPlugSpot`'s
  comment and `@Nullable` above `plugSpots`, which never gives null).

### Solid in this slice (do not "fix")

- `NestPlan`: the geometry of rooms, stairs, gardens, side rooms and foraging tunnels, the digging order, room sizes.
- `TunnelRoute`: the search, the hole pre-check, the per-plan memory of tops and diggable cells, `isWalledIn`.
- `GroundTop`: reads only its own column.
- `Nest` save and load: nothing found doubled or lost. `isInside`: the once-a-tick answer and the gallery roof rule.
- `getRoomCells`, `isFinished`, `getHomeChamber`, `isShaftOpen`: all guarded with `LandReady`.
- `NestWalls`: `wallFirst`, `plate`, `nudgeOut` (never over a stranger), `plugLeak` with `shutsInANestmate`,
  `casingGaps` (capped at 12), `isOpen` (never replaces a player's solid blocks).
- `BuildLife`: its limits a tick and a second, the out-of-sight checks, save and load.
- `NestLife`: no world writes; reads only in `watch` while the queen is loaded.
- No species named; no `level.getRandom()`.

---

## Slice: the colony core

`Colony`, `ColonyManager`, `ColonyPart`, `ColonyParts`, `ColonyNews`, `ColonyQuestions`, `ColonyHooks`, `AwayLife`,
`AwayReport`, `Watchers`, `Strays`, `Claims`, `ColonyRole`, `Caste`, `LandReady`. Every line read. No grade A here: no
work every tick that need not be, no read that loads land (every land check goes through `LandReady`), no list changed
while walked. The faults are in the away life's bookkeeping. R1-26, R1-27, R1-30 and R1-31 were checked again by the
main session against the lines named.

**A note on the unloaded-land findings (R1-7, R1-8, R1-14, R1-18), from this slice's `AwayLife.isHere` (157 to 163):**
a colony ticks its parts "here" only with its middle loaded and a watcher within 64 blocks (80 to stay). Food and
galleries reach 50 further, so about 130 blocks from the player at most: inside the loaded land at a view distance of
9 chunks or more, outside it under that (and whenever something else holds the colony here: an `OUT_THERE` place such
as a raid a player follows, or the far rule switched off). So those findings bite on small view distances and
servers, not in the user's usual game. They stay A: a freeze is a freeze, and the fix is one guard each.

### R1-26 (B, proven; checked again): the far rule removes loaded insects without asking whether a player can see them

- **Where:** `AwayLife.java:163`, `189-204`, `287-317` (the `discard` at 309), `404-406`; `SavannaParts.java:302`
  (only the raids sign up `OUT_THERE`).
- **What is wrong:** "here" is judged from the nest's middle (and `OUT_THERE` places, which termites have none of).
  With no watcher within 80 blocks and the minute of grace over, `foldLoaded` walks `level.getAllEntities()` and
  discards every loaded member wherever it stands, with no `Population.outOfSight` or `Watchers` check (`Strays:77`
  has one). Termite foragers and soldiers at gallery holes stand up to about 56 blocks out, so 24 blocks or more from
  a player just past the 80. The sweep runs again every 400 ticks.
- **What the player sees:** termites on the surface towards the player vanish at once (the same kind as BUGS 319).
  Small insects at 24 blocks and more, so easy to miss: B.
- **Fix proposed:** `foldLoaded` skips a member a watcher can see; the next sweep takes it.

### R1-27 (B, proven; checked again): a colony that dies out while away is not ended until a player comes near

- **Where:** `ColonyManager.java:89-98` (the away branch calls only `tickAway`); `Colony.java:675-678` (the one
  `isQueenless() && isEmpty` check, in the near tick); `QueenCare.java:401-403` (a queen starved away is only a flag).
- **What is wrong:** the empty record ticks as numbers every second and sends no `COLONY_ENDED` news until a player is
  within 64 blocks; then it ends on the spot.
- **What the player sees:** a dead mound's wear starts when the player arrives, not when the colony died; till then
  the colony counts as living to anything that walks `getColonies()` (raids as numbers choosing a colony to take from).
- **Unchecked link:** what the species code does with such an empty colony.
- **Fix proposed:** the same end in the away branch, with an emptiness check that reads only numbers.

### R1-28 (B, the fold proven; how often, unchecked): folding an insect into the numbers loses a species load it carries

- **Where:** `AwayLife.java:348-362` (`loadOf` knows only egg, corpse, bread and seed), `418-441`; callers
  `Strays:82`, `AwayLife:307`, `394`, `481`; `MatabeleLoads.java:24-33`; `AbstractAntEntity.java:574-580`.
- **What is wrong:** any other load becomes `Load.NONE` and is gone with the body. A death sends the same load through
  `load.kind().drop(...)`; a fold never asks the load's kind. A raid covers its own members (its `jaws` map), but
  strays, the far sweep and the unloaded-land freeze also fold carriers.
- **What the player sees:** nothing directly; Matabele colonies quietly lose prey and heap layers.
- **Fix proposed:** a "folded" hook on `LoadKind` (by default the death drop done as numbers), called from `foldIn`,
  in place of the framework's own list in `loadOf`. Fewer rules: one place knows what a load becomes.

### R1-29 (B, proven in `AwayLife`; the `Population` side likely): an insect frozen while its colony is here waits in limbo

- **Where:** `AwayLife.java:251-260`, `390-398`, `468-485`; `Population.java:179-182`, `700`.
- **What is wrong:** a frozen insect is folded only when the colony goes away or when that same body ticks again.
  With the player staying by the nest, one frozen at the edge of loaded land stays in the headcount, neither real nor
  inside: it does not eat, does not age and cannot be swapped back. The comment at 40 to 41 says it "has walked home
  meanwhile"; nothing does that.
- **Fix proposed:** `settle` (once a second while here) folds frozen entries older than the grace.

### R1-30 (B, likely; the path checked again): one colony that fails to load wipes every colony of the dimension

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 387.**

- **Where:** `ColonyManager.java:232-240`; `Colony.java:910-929`.
- **What is wrong:** `ColonyManager.load` loops `Colony.load` with no try. A part's `load` that throws leaves the
  loop; Minecraft logs it and gives no data; a new, empty manager is made and written over the file at the next save.
- **Unchecked link:** no throw was found today (it needs a bad enum name or a key a part takes for granted).
- **What the player sees:** every colony gone after opening the world. Rare, and the worst thing in this file.
- **Fix proposed:** a try round each `Colony.load`: log it, keep the raw record and write it back unchanged.

### R1-31 (B, likely; checked again; to measure): every colony ever made ticks once a second for ever and marks the file changed

- **Where:** `ColonyManager.java:81-98`; `AwayLife.java:407-410` (`markChanged()` every away second, changed or not).
- **What is wrong:** no distance limit and no slower clock for far colonies. New land has a colony about every 96
  blocks, so the cost a second and the size of the colony file (written whole at every autosave) grow with all the
  land ever explored.
- **What the player sees:** slowly rising tick time and longer autosaves in big worlds.
- **Fix proposed:** measure first (`Perf` already files "land not loaded"); if it counts, colonies far from every
  player tick every N seconds with N times the step, and the file is marked only when something changed.

### R1-32 (C, proven by its reader): a trip out of range shorter than the grace wipes the last away report

- **Where:** `AwayLife.java:374-377`, `493-505`. Out of range clears the report at once; back within the minute it
  reads "while away (0.0 hours): nothing" and the galleries' catch-up starts over, though nothing was folded.
- **Fix proposed:** clear the report and call `cameBack` only once the colony is really away.

### R1-33 (C, proven by its reader): a colony's dice start the same run after every reload

- **Where:** `Colony.java:490-496` (seeded from the colony's id, the state not saved).
- **Fix proposed:** in a real game mix the game time at first use into the seed; the tests' seeds stay as they are.

### R1-34 (C): comments from before the harvester left

- **Where:** `ColonyParts.java:27-28`, `34-35`, `37-40` (names `Colony#mound()`, which is gone); `Colony.java:34`,
  `413-415`, `425-428`; `ColonyHooks.java:354`; `AwayLife.java:358-359`.

### Solid in this slice (do not "fix")

- `ColonyPart`, `ColonyParts` and the check when a colony is made: each part saved or not on purpose, no tick place or
  name used twice.
- `ColonyNews`, `ColonyQuestions`, `ColonyHooks`: copy-on-write, called only when someone signed up.
- `LandReady`: `getChunkNow` on the server thread never loads land.
- `ColonyManager`'s tick spreading: a copy before the loop, the 20 slots kept, the catch-up spread over the second.
- The guards against folding twice; `freeze` ignoring dead, killed and changed-dimension removals; the queen and king
  never frozen.
- `AwayLife`'s save and load of frozen entries, the report and `awaySince`.
- `Colony.roleOf` and `workOutRoles` (at most once a second); `Strays`; `Claims` (all 9 users prune it).
- The static test hooks are set only by tests, soaks and flights (the watcher overlap is BUGS 338).
- No species named; dice from `Colony.random()`; no season read.

---

## Thread: the colony's tick, near and away (a Fable reader, across all the parts)

Followed the paths; not every file read whole (not read: `NestWalls`, `Refuse`, most of `ForagingHoles`,
`ForagingTrails`, `Neighbours`, `NestPlan`, `TunnelRoute`, `RunwayRoute`, `FoodWays`, `GroundReader`, the species
readers; the line-by-line slices above cover those).

### The tick, in short

1. `ModEvents.onLevelTick` calls `ColonyManager.tickColonies(level, gameTime % 20)`: each colony's brain runs once a
   second on its own slot (from its id), so colonies are spread over the second; on the other 19 ticks a colony gets
   only a catch-up slice, and only if it owes building.
2. **Not here** (land not loaded, or no watcher within 64 blocks, 80 to stay): `AwayLife.tickAway`. The first minute
   nothing runs. Then frozen insects are folded into the numbers, loaded strays every 400 ticks, and the away parts
   run in order: mound shapes 5, hidden trips 10, galleries 20, population 30, nest life 40, field life 60, build
   life 70, prey pile 85, raids 87; then the save is marked changed.
3. **Here but the queen's body not loaded:** nothing runs that second.
4. **Here:** `Colony.tick` runs the near parts in order: settle 10, fungus garden 20, mound 30, mushrooms 50, alarm 60,
   raid defence 65, food map 70, foraging holes 80, galleries 90, hidden trips 100, bare patches 110, prey pile 115,
   raids 117, population 120, nest defence 125, nest life 130, danger map 170, closed holes 180, neighbours 190,
   field life 200, strays 215.
5. **Order:** the population eats (120) before the young are fed (130) and the field brings food in (200), so food
   gathered this second is eaten the next; the same order away (30, 40, 60). Harmless.
6. Nothing in the brain grows with the square of the number of colonies. The widest loops: `foldLoaded` over every
   entity, and the walks over every known plant.

### R1-35 (A, the path proven and checked again; how hard it bites, to measure): sleeping in a bed takes the termites' foraging night away, while their hunger runs on

**Status (2026-11-25, `claude/review-fix-1`): open: the user's call.**

**Status (2026-11-25, `claude/review-fix-1-calls`): fixed as recommended, the user's call (BUGS 397): a slept night is foraged by the numbers.**

- **What is wrong:** termites forage outside at night or in rain (`macrotermes.json:103`, `"forage_at": ["night",
  "rain"]`; `Colony.isForagingTime`, 433 to 449, by `level.isDay()`). A bed works only at night and jumps the day
  clock to morning; the game time does not jump, and the hunger clocks run on game time (`Meals:145`, a meal every
  24,000, starving after 72,000; `docs/DESIGN.md:716` says the same of the test clock, "like sleeping in a bed; hunger
  and ages do not jump"). Nothing in the mod listens for sleeping (grep: no `SleepFinished`, no sleep event).
  Near: real foragers stop (`GatherFoodGoal:56`) and the holes stay shut (`ForagingHoles:221`). Away, for every colony
  of the world, since the day clock is one: `FieldLife:384` skips every plant unless it is fed from inside, and 417
  never forages by the land. What is left by day is only rich food (dead wood, hay) a closed gallery or runway
  reaches; dry grass is never rich.
- **The other side of the same root:** the savanna's `RaidsAway:224-231` counts the raid day by the day clock, so with
  slept nights the Matabele get a full day's raids in about half the game time.
- **Unchecked links:** Minecraft's bed rule is from memory, not read in its code (the main session agrees with it:
  the day time is set forward, the game time is not); whether the by-day income is enough to live on needs a soak.
- **What the player sees:** a player who sleeps every night never sees termites at their holes; colonies living on
  grass shrink and starve all over the world, those with dead wood live on; the balance tips to the ants. The user
  tests in creative and seldom sleeps, which is why it has not shown.
- **The user's call (it changes how the game plays). Recommended:** a skipped night is foraged as numbers. When the
  day clock jumps, each colony's field life (the same numbers that forage for an away colony) is given the skipped
  foraging hours at once, near or away: the termites did forage that night, the player slept through it. One rule,
  with machinery that exists; no new number. The raid day then needs no change.

### R1-36 (B, proven; checked again): builder numbers do nothing near a player and bank work without end while away

**Status (2026-11-25, `claude/review-fix-1`): open: the user's call.**

**Status (2026-11-25, `claude/review-fix-1-calls`): fixed as recommended, the user's call (BUGS 398): builder numbers work near, nothing banked while waiting, a waiting slice tried once a second. Banked work from while away is still not capped (it is dropped once nothing is left to build).**

- **Where:** `ColonyParts.java:136-138` (`BUILD_LIFE` has only `.ticksAway(70, ...)`, no near tick);
  `BuildLife.java:94-107`, `148-166`; `NestLife.java:162`.
- **What is wrong:** near, with 200 workers and 50 real, about three quarters of the builders are numbers and build
  nothing (forager numbers do work near: `FieldLife` ticks both ways). Away, `work += builders * 20L` every second
  with no cap. Back, it is spent out of sight at up to 200 blocks a second, and dropped only when nothing is left to
  do and nothing waited on a player's eyes; while a player looks at the next block the slice is tried again on 19 of
  every 20 ticks, each try a scan of the whole nest plan and up to 8 mound passes a second.
- **What the player sees:** beside a mound it grows at about a quarter of its pace; leave and come back and it has
  jumped. The balance goal "the mound keeps growing over many hours of play" then hangs on where the player stands.
- **Fix proposed:** one judge of the building pace: builder numbers add `work` near too, spent by the same
  out-of-sight catch-up; `work` capped at what the colony can still build, so nothing is retried every tick.
  **This changes how fast a watched mound grows: the user's call.**

### R1-37 (B, the mechanism proven; checked again): all the numbers of a caste share one meal minute

- **Where:** `Meals.java:124-126`, `154-158`, `185-196`; against `Population.java:224-259` (old age is spread).
- **What is wrong:** every number with no known meal gets the same minute (`times.merge(minute(now), want - have)`); a
  colony put in by new land gets all its workers at once. While hunger is off (termites: until the first comb) the
  clocks are all set to now again. A day later all are due in one second: 200 workers at 4 a piece take 50 layers of
  comb in one tick, once a day. With no food, one pass starves every minute older than `starveAfter` at once.
- **What the player sees:** a comb untouched all day that loses a block's worth in a moment; a cut-off colony that
  looks full for three days and is empty the next second, instead of dwindling.
- **Fix proposed:** numbers with no known meal are spread over the last meal interval (as `growOld` spreads deaths);
  while hunger is off, time is simply not counted, in place of setting every clock to now.

### R1-38 (C, proven): two judges of "it rains"

- **Where:** `Colony.java:436` (a species with a day clock asks `rainsOnNest(level)`) and `439` (one without asks
  `level.isRaining()`, the whole world). Termites take the second. Harmless in the savanna today.
- **Fix proposed:** `rainsOnNest(level)` on line 439 too.

### R1-39 (C, proven; to measure): the away brain asks the world for eggs and larvae several times a second

- **Where:** `Brood.java:83-85`, `311-313`; callers `QueenCare.java:247-248`, `265`, `338`, `382`, `429`;
  `AwayLife.java:287-317`.
- **What is wrong:** `Brood.isFull` counts real egg and larva bodies in the nest's box, up to five times an away
  second, though the away brain keeps its young as numbers. `foldLoaded` walks every entity of the world every 400
  ticks for each colony that is far with its land loaded.
- **Fix proposed:** away, `isFull` answers from the numbers' own young; `foldLoaded` asks the colony's own box.

### R1-40 (C, proven): "is the colony here" is worked out again inside parts in the middle of a tick

- **Where:** `Population.java:364`; savanna `FungusGarden.java:341`; `FieldLife.java:104` takes it as a parameter, the
  right pattern.
- **What is wrong:** `isHere` is no stored answer: it reads the land, the players and the hooks each call. In the
  grace minute and at the change-over it can differ from which brain is ticking the part, so a part can read the
  world's comb while ticked as numbers: where a double count at the change-over would come from.
- **Fix proposed:** the tick stores "here or away" on the colony once; the parts read that.

### R1-41 (C, likely): the real queen's founding batch has no away guard, unlike her stream laying

- **Where:** `QueenCare.java:202-234` (`raiseFoundingBatch`, no guard) against `281-286` (`layInStream`, guarded);
  caller savanna `TermiteQueenEntity.java:164`.
- **Unchecked link:** whether a real queen ticks long enough while her colony counts as away to matter.
- **Fix proposed:** the same guard at the top of `raiseFoundingBatch`.

### Seen by two readers (this thread and a slice)

- **R1-27 and R1-31** (a dead colony never ended away; every colony ticking for ever): found again here, with what
  one away colony costs a second: `isHere` (a player scan and the hooks), the population's age groups and meals, the
  nest life with R1-39's world queries, the field life walking every entry of `owed` and every known plant, the build
  life, the species' away parts; and `tickColonies` copies the whole colony list 20 times a second. This reader's fix
  adds: a colony in land not loaded ticks its away parts on a 10-second beat with the seconds handed in (its clocks
  run on game time, so nothing is lost).
- **R1-13** (two judges of room in the food store): found again; rated C here, the loss "a few pieces".
- **R1-28** (a species load lost at the fold): found again, with a different weight: the raid reads what its ants
  hold before it folds them (savanna `Raid:941-947`), so this reader found no loss today and rates it C now, B when
  the next carrying species comes. The two readers agree on the fix (the species owns its load both ways).
- **R1-5** (the queen's own dice): found again.

### Solid on this thread (do not "fix")

- Tick slots: two parts on one place throw at sign-up; every part is made before any signs up.
- No away path followed loads a chunk.
- The fold is guarded against counting an insect twice; a frozen insect that comes back after being replaced is
  folded, not doubled.
- The numbers' clocks all run on game time: `/tick rate` and long absences need no loop "once for each missed second"
  for ages, meals or laying.
- The block catch-up on return is bounded: 200 blocks a second, 20 a tick, 8 mound passes, 4 gallery pieces.
- Old-age deaths are spread; the queen's stream uses one clock near and away, guarded so both never lay.
- The season rule and the species-name rule hold across `colony/`, `species/` and `season/` (names only in comments).
- `crew` equal to `real_cap` makes `watch_radius` do nothing; the species file says that is the user's pick.

---

## Thread: save, reload and lifetimes (a Fable reader, across all the parts)

Followed a colony's data through its life. Minecraft's own behaviour was read in the NeoForge sources jar, not from
memory. Save sizes were measured by reading the user's worlds in `run/saves` (read only). Not looked at: the species
side's parts' save and load (galleries, raids, scouts, hidden trips), client-only classes on a dedicated server,
whether new-land mound code reaches the brain from a world-making thread.

### R1-42 (A, proven; checked again): one typo in a species file empties every colony of the world (R1-2 and R1-30 joined)

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 387 (test `aColonyThatCannotBeReadIsKeptAndABrokenSpeciesFileKeepsItsNumbers`).**

- **What is wrong:** the chain, each link read:
  1. A species file with a mistake is dropped (`SpeciesProfiles:43`) and there are no built-in numbers (R1-2).
  2. `ColonyManager.load` (232 to 240) calls `Colony.load` for each colony with no try (R1-30). `Colony.load` makes the
     colony, and the constructor's line 126 asks `profile().parts()`, which is `SpeciesProfiles.get`: it throws.
  3. Minecraft's `DimensionDataStorage.readSavedData` catches every exception, logs "Error loading saved data" and
     gives nothing; a new, empty manager is made.
  4. Every loaded termite queen then founds a new empty colony (savanna `TermiteQueenEntity:149-155`), which marks the
     store changed: the next autosave or pause writes the empty list over the old file.
- **Other throws on the same path:** `Brood.fromSaved` (an unknown caste word), `AwayLife.load` (`Kind.valueOf`,
  `Load.valueOf`), `FoodStore.load` (`Long.parseLong`), a missing `id` or `queen`.
- **What the player sees:** after a typo in `macrotermes.json` (the file the user is told to tweak), or a species mod
  taken out, the world opens with every mound an empty shell: queens alone, no workers, no food, no fungus, and it is
  saved that way. One line in the log.
- **This raises R1-30 from "likely, no throw found" to proven:** the throw is R1-2.
- **Fix proposed:** both halves. `ColonyManager.load` catches for each colony, logs it and keeps the colony's raw
  record to write back unchanged; `SpeciesProfiles` keeps the last good profile when a file fails at `/reload` and
  stops the loading with the file's name at first load.

### R1-43 (A, proven in code; checked again; the link to the drift in the saves, likely): an insect already folded into the numbers is folded a second time

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 388.**

- **What is wrong:** `AwayLife.freeze` (251 to 260) writes `frozen.put(entity.getUUID(), what)` with a new record,
  `folded` false, never looking at a record already there.
  1. An insect's chunk leaves range: a record, not folded. The colony is away past the grace: `tickAway` folds it
     (numbers inside +1) and marks the record folded. Right so far.
  2. The player passes: the chunk is loaded again but in the outer ring, where entities do not tick. The insect is in
     the world but never ticks, so `cameBack` (called from its own tick, 468 to 485) cannot remove the record and the
     body. `foldLoaded` would, but only every 400 ticks and only with the nest's middle loaded.
  3. The chunk leaves range again: Minecraft posts the leave event (`UNLOADED_TO_CHUNK`), `ModEvents.onLeaveLevel`
     calls `freeze`, and the folded record is replaced by an unfolded one.
  4. The next second `tickAway` folds it again: inside +1 a second time, and what it carried counted again (seed,
     nurse food, egg, body). Eggs and larvae double the same way. The headcount is not raised.
- **Evidence in the user's saves:** numbers inside above the headcount in 4 colonies of 3 worlds (worker 72 counted,
  73 inside; minor soldier 5 and 6; Matabele worker 76 and 77; termite minor soldier 15 and 16; worker 200 and 210).
  Not replayed in the game; this is the only path found in code that gives it for termites (R1-45 gives it for
  Matabele).
- **What the player sees:** colonies slowly gaining insects, food and young they never raised when the player, above
  all flying in spectator, skirts the edge of their loaded land.
- **Fix proposed:** `freeze` keeps a record that is already folded.

### R1-44 (A, what runs proven, sizes measured; milliseconds to measure): every autosave and every pause rebuilds and copies every colony's whole record

**Status (2026-11-25, `claude/review-fix-1`): open: to measure first; marking the file changed less often gains little, since an away colony's numbers change every second; the size (`FieldLife`'s plants) is the lever.**

**Status (2026-11-26, `claude/review-a-left`): fixed: measured first (31,760 plants, as the 76-colony world had): building and copying the plants' records on the server's own thread took 6.3 ms, packed into four rows of numbers 0.55 ms (`FieldLife.pack`, `FieldSaveTest`); the writing itself is done on a worker thread (NeoForge's `SavedData.save`), so it never was part of the hitch. Saves from before still load. Not packed: the rest of the file (`insideNest` and the others, about as many bytes again, a few ms at 76 colonies); no single tick comes near 50 ms from it.**

- **What is wrong:** R1-31's `markChanged()` every away second keeps the store always dirty. Minecraft's
  `SavedData.save` builds the whole record and copies it on the server thread before the write goes to a worker.
- **Measured:** the world "New World (227)", 76 colonies (75 away): 2,928,376 bytes uncompressed; a colony 21 KB at
  the median, 190 KB the largest. By key: `field` 1,429,762 (`FieldLife` writes each plant as its own record: 24,627
  plants and 7,133 unknown), `insideNest` 520,963, `knownFood` 323,696, `galleries` 260,983. The current world: 22
  colonies, 1.1 MB. It grows with every mound new land places.
- **What the player sees:** a hitch at every autosave and every Esc pause, growing with the land explored.
- **Fix proposed:** mark changed from the away tick only when a part's saved numbers changed; `FieldLife`'s plants as
  packed arrays (one of positions, one of kinds and trips).

### R1-45 (A, proven; checked again): a minor that comes out when a number scout is shown goes back into the numbers, but its body stays

**Status (2026-11-25, `claude/review-fix-1`): fixed, BUGS 389.**

- **Where:** savanna `Scouts.java:125-134`; `AwayLife.java:323-327` (`foldInNow`); `Population.java:637-638`.
- **What is wrong:** `Population.comeOut` takes one from the numbers and adds the body to the world. `Scouts.showScout`
  wants a major; for a body that is no major it calls `colony.away().foldInNow(level, mob)` and tries again, up to 8
  times. `foldInNow` only adds the number; unlike `foldInStray` (343) it never discards the body, and nothing else
  does here. Its other two callers are right (a hidden trip's traveller has left the world; `carriedOff` discards).
- **What the player sees:** extra minors standing where a scout appears, each also alive as a number: a Matabele
  colony growing by itself each time a far scout is watched.
- **Fix proposed:** `foldInNow` discards a body still in the world (or this caller uses `foldInStray`).
- **The caller is raid-rewrite code** (round 11's ground); it belongs with the raid fixes.

### R1-46 (B, proven by its reader): a colony whose queen cannot be found is skipped for ever while near

- **Where:** `ColonyManager.java:100-103`; `Colony.java:661-679`; `ModEvents.java:130-137`.
- **What is wrong:** `if (!colony.isQueenless() && colony.getQueen(level) == null) { ... continue; }`. A queen gone
  without `die()` (discarded, lost with a chunk, through a portal, R1-49) leaves a colony never ticked near and never
  ended; away it lives on as numbers (the away half is R1-27).
- **What the player sees:** a mound standing frozen: nothing happens, and it never dies out.
- **Fix proposed:** a queen missing for some time with her chamber loaded takes the queen-died path.

### R1-47 (B, likely): more reads one step past the guarded cell, and on a list up to 5 seconds old

**Status (2026-11-25, `claude/review-fix-1`): mostly fixed, BUGS 390 (`FoodMap`'s 27 cells round each ant and `isEntranceCovered` left: the ant's own land is loaded).**

- **Where (beyond R1-18's places):** `FieldLife.java:158` and `FoodStore.java:287` (`plantFor(level.getBlockState(pos))`
  on `FoodStore.plants()`, rescanned every 100 ticks); `Nest.java:1130`; `FoodMap.tick` (the 27 cells round each
  ant); `Nest.isEntranceCovered` into `GroundTop.isRoofed`; `GroundReader.mayOpenHole` (85 to 88, reads `hole.above()`
  before `mayDig`'s guard).
- **Unchecked link:** how often a colony's cells lie on the outer ring of loaded land while it is here.
- **How far out, corrected:** this reader measured the reach further than the note in the core slice: a colony is
  here with a player up to 80 blocks from its middle, and runway holes lie up to about 82 blocks out, so a hole can
  be 162 blocks from the player: outside the loaded land at a view distance of 10 chunks (the user's) and plainly so
  at 8 or less. So R1-8 can bite in the user's own game.
- **Fix proposed:** one guarded read helper (the block's state, or nothing when the land is not ready), used for
  neighbours and remembered positions; with R1-18's shared helper this closes the whole family.

### R1-48 (B, likely; to measure): the "no route to this food" memory is not saved

- **Where:** `ForagingHoles.java:73`, `87` (`noRouteUntil`, `noRunwayUntil`, 6,000 ticks); the part is signed up as
  not saved.
- **What the player sees:** stutter in the first seconds after opening a world beside grown mounds: every colony
  plans again each tunnel and runway route it had given up on.
- **Fix proposed:** save the two maps (small, and they run out by themselves).

### R1-49 (C, likely; not tried): an insect or queen that changes dimension is never taken off its colony

- **Where:** `ModEvents.java:45`; savanna `TermiteWorkerEntity.java:197-207`, `TermiteQueenEntity.java:149-155`. No
  `canUsePortal` override was found.
- **What the player sees:** only with a portal by a nest: a headcount one too high for ever, or a second colony in
  the Nether.
- **Fix proposed:** colony insects do not use portals.

### R1-50 (C, proven by its reader): small things that reset or never clear

- `Nest.digProgress` is not saved: half-dug blocks start over after a reload. `Colony.skippedLogged` is static and
  never cleared (it only stops a log line repeating). The dice: R1-33.

### Saved or not, part by part

| Part | Saved | Not saved, and does it matter |
|---|---|---|
| `Colony` itself | id, species, queen, middle, castes, `queenDiedAt`, `bornAt` | roles, `grownUp`, heat, the dice (R1-33): worked out again |
| `Population`, `Meals` | yes, keys and defaults match | |
| `Brood` | yes | throws on an unknown caste word (R1-42) |
| `QueenCare` | yes | |
| `FoodStore` | `knownFood`, `grassClips` | the plant list is scanned again; `Long.parseLong` can throw |
| `FoodMap`, `FoodWays` | yes | |
| `FieldLife` | yes | by far the biggest (R1-44) |
| `Nest` | yes; the only load that survives a bad enum word | `digProgress` (R1-50) |
| `NestLife`, `NestLedger`, `BuildLife`, `WorkerFood` | yes | |
| `DangerMap`, `Neighbours` | yes | fade by the clock, pruned |
| `ForagingTrails` | yes | |
| `AwayLife`, `AwayReport` | `since`, the frozen records | `Kind` and `Load` words can throw; frozen records for chunks never seen again stay (210 to 429 a world measured; bounded by the real-mob cap a colony) |
| `ForagingHoles` | no, on purpose | loses the no-route memory (R1-48), claims, `closedSince` |
| `NestWalls`, `Refuse`, `Strays`, `NestDefence` | no, on purpose | built again from the world |

### Seen by two readers (this thread and a slice)

- **R1-8 and R1-24** (the read before the guard): found again, with the reach above.
- **R1-27** (a colony never ended away) and **R1-31** (marked changed every second; R1-44 adds the measured sizes).

### Solid on this thread (do not "fix")

- **Leftovers between worlds: clean.** No per-world state in statics of `colony/`, `species/`, `season/` (test hooks,
  registries, name caches only); the species profiles are filled again by the reload listener; one `ColonyManager` a
  dimension; `ModEvents.onServerStopped` clears the watchers and labels.
- **Threads:** `LandReady` answers from another thread without waiting; `ColonyParts.register` and `all` are
  synchronized; no brain code was found reached from world making inside these folders.
- `Colony.skipUnused` (a part the species no longer lists is skipped and logged once); `ColonyPart.check` (every part
  says saved, or why not).
- Headcount paths checked, count and inside changing together: `growOld`, `starved`, `loseInside`, `loseToHunters`,
  `goIn`, `addNewMembers`, `carriedOff`, each caste's `die()`.
- Closing the world freezes no insect (Minecraft unloads with no leave event).
- `foldLoaded` and `foldInStray` remove the frozen record before folding: no double there.
- `getEntitiesOfClass` and `getAllEntities` never load land.
