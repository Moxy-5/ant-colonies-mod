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

**Status: being written piece by piece.** Slices in so far: young, danger and species; food and field.

---

## Slice: young, danger and species

`Population`, `Brood`, `QueenCare`, `Refuse`, `DangerMap`, `Neighbours`, `species/*`, `season/Seasons`. Every line read.

### R1-1 (A, proven; checked again): a Matabele queen never lays while a player is near

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

- **What is wrong:** line 201 `BlockState state = level.getBlockState(hole);` and only then line 202
  `return LandReady.at(level, hole) && ...`: the guard comes after the read it is there to prevent. Callers with no
  guard of their own: `FoodWays.runwayPlant` (207, each time a worker asks what to dig next in foraging time),
  `NestWalls:111`, the savanna `Soldiers:234`. `touchesFluid` (936 to 943) reads six neighbours' fluids with no check
  at all (from `mayOpen`, 225, and `RunwayLand.isFree`, 634).
- **What the player sees:** a chunk loaded on the spot whenever a runway's hole lies in land that is not loaded.
- **Unchecked link:** that a runway's hole can be in unloaded land while its colony ticks.
- **Fix proposed:** swap the two lines in `isOpen`; give `touchesFluid` the guard (unloaded land counts as no fluid).

### R1-9 (A, proven; checked again): "is anyone still outside" looks only 32 blocks round the nest

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
