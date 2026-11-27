# Round 5a, reader X: near against away, save and reload (the mound)

Read at commit `4dcd1fe6` in `C:\Ant Mod` (branch `claude/motion-glue`). No code changed, nothing run, nothing
measured. Short paths: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`. Builds on round 1's findings on the away life (R1-17, R1-26 to R1-29,
R1-35 to R1-37, R1-40, R1-43 to R1-45) and does not write them again.

The question followed: does a termite mound and its colony come out the same whether a player is watching or not,
and does everything live through a save, the land unloading, and time jumping?

Some of what this thread found, other readers of this round found too, each from their own side. Where that is so
the finding says it (`F-1`, `M-1`, `D-3`, `G-2`, `F-5`, `F-2`, `M-2`, `M-5`, `D-14` are their numbers), so the
merge can join them. Two readers reaching the same fault by different roads is some proof of it.

Words used: **near** (or **here**): the colony's middle is in loaded land and a player is within 64 blocks (80 to
stay). **Away**: anything else; the colony then lives as numbers in the brain and no block is touched. **Random
tick**: Minecraft's own slow clock for blocks, which picks a few blocks of each chunk at random every tick (crops
grow by it). **Game time**: the count of ticks the world has run, which never jumps. **Day clock**: the time of
day, which a bed and `/time set` move.

## X-1 (A, the path proven; the size of the freeze from the code's own notes, to measure): asking a far mound for its stage still grows its whole shape on the game's own thread, about a second a mound

- **What is wrong:** growing a mound's plan (every stage, about 15,000 cells) takes about a second. On 2026-11-10 the
  code was changed so that a far colony's stage is worked out from a few saved numbers and "never grows its shape
  again". But `Mound.getStage` does one more thing: when the colony has earned a taller stage than the one it builds,
  it asks "is this stage finished?", and that question needs the full plan. With nobody near, the plan is not held, so
  it is grown on the spot, on the server thread. And in land that is not loaded the answer to "is it finished?" is
  always yes (no cell can be read, so no cell has work left), so the stage also climbs by one each time it is asked.
- **What the player would see:** a freeze of about a second for each far termite colony asked, with no cause in
  sight. The worst caller walks every termite colony of the world: each time new land puts in a dead mound that could
  hold Matabele ants, every living colony that has earned a stage since it was last asked costs one growth. Flying
  over new land in a world with many colonies: stutters of several seconds. Second: a mound in land nobody has loaded
  has its building stage moved on with nothing built, so when the player comes it starts stages above what stands.
- **Where:** `savanna/colony/Mound.java:345-369` (`getStage`), `410-417` (`isStageDone`), `443-459` (`getParts`),
  `278-285` (`stages`, `stage`), `243-257` (`footReach`); `savanna/colony/MoundPlan.java:133-156` (`growth`); callers
  for far colonies: `savanna/colony/NewLandMounds.java:358-366` (`livingMoundNear`, no distance check before
  `getStage`), `savanna/colony/FeedingSpots.java:193-199` from `savanna/entity/goal/MatabeleScoutGoal.java:305-308`.
- **The proof:**
  1. `NewLandMounds.putWithAnts` (323-333) calls `livingMoundNear` for a dead mound of new land whose dice say ants
     would settle. `livingMoundNear` (360-366) loops over every colony of the world and calls `mound.getStage(level)`
     on each Macrotermes one, before any distance is looked at.
  2. `getStage` (345): `getEarnedStage` (322-336) knows the soil counts from the save (`savedWalls`, 220-222: this is
     the 2026-11-10 fix), so it returns the real earned stage. The allowance grows with the game's time while away
     (306-319: 2 blocks a day plus 0.25 a worker, `macrotermes.json:232-233`; 200 workers earn 52 blocks a day), so a
     colony left alone for a day or two has `workStage < earned`.
  3. Line 359: `workStage < earned` and 100 ticks since the last look (`stageCheckedAt` starts at 0 after a load):
     line 361 calls `isStageDone` (the stall clock was set to "now" two lines above, so the first half is false).
  4. `isStageDone` (411) calls `getParts`. `getParts` (448-450): the stage differs from `cachedStage` (0 after a
     load, or the stage before the last climb), so it calls `stage(h, numbers)`, which calls `stages` (279):
     `held != null ? held : MoundPlan.growth(...)`. `held` is null for a colony with no watcher within 128 blocks
     (`keepShapes`, 292-299). `MoundPlan.growth` (133-156) returns a kept plan only for the last 24 mounds asked for
     (`KEPT`, 105); otherwise it calls `grow` on the calling thread: "about a second" (`MoundPlan.java:101`), "1 to 3
     s" (`Mound.java:240`), "a 1.3-second freeze" (`NewLandMounds.java:217`).
  5. Back in `isStageDone` (412): every cell is skipped where `LandReady.at` is false, so in unloaded land the loop
     finds no job and returns true. Line 362: `workStage++`. The next ask, 100 ticks or more later, finds a new stage,
     and if more than 24 mounds were asked in between, grows the plan again.
  6. The second caller: a Matabele scout's `lookRound` asks `FeedingSpots.moundReach` for every termite colony within
     its trip's reach plus 32 blocks; `footReach` (248) calls the same `getStage`. This is the very path the
     2026-11-10 note on `footReach` says was fixed.
- **Same root, other readers:** `M-1` (the list command, the near tick's `join`, the Matabele bite check) and `D-14`
  (the same `livingMoundNear` loop, marked "likely" there). This finding adds the walked path through `getStage` and
  the stage that climbs in unloaded land.
- **No test:** nothing asks a far colony's stage with its plan not held and checks that no plan is grown, or that the
  stage stays put in unloaded land.
- **Unchecked link:** how long one growth takes was not measured here (the three notes in the code say 1 to 3 s);
  how many colonies a real world holds with a stage earned and not yet built.
- **The fix proposed:** one rule in place of two: `getStage` only reads; it never moves the stage on. The stage
  moves on in the two places that really build: the near tick (`Mound.tick`, where the plan is held) and the away
  builder (`MoundAwayBuilding`, which already calls `nextStage`). Then no caller far away can reach `isStageDone` or
  the plan, and a stage can no longer climb in land nobody has loaded. `isStageDone` should also answer "not known"
  (false) when any cell of the stage is in land not loaded.

## X-2 (A, the sums proven from the code; how soon it bites, to measure with a soak): while away, the fungus garden only "sees" new comb that is left over after eating, so nodules stop growing in a colony left alone for long, and the queen goes unfed

- **What is wrong:** real comb is a conveyor belt: forage goes on top fresh, ripens, grows a nodule, goes old, is
  eaten from the bottom. Near a player the blocks do exactly this. Away, the brain keeps one number for the food
  store, "layers owed" (`NestLedger.store`): every piece the foragers bring adds 1, every layer the old workers eat
  or that crumbles takes 1 off. The garden's away sums work out the fresh comb as "fresh as last seen, plus the
  layers owed if above zero, less what has ripened". So a fresh piece and an eaten old layer cancel each other: the
  brain thinks the old layer is still there and the fresh one never came. Fresh comb is counted only as far as the
  store grows, and the store can only grow until the garden's room is full (`FoodStore.roomKnown`). After that, in
  a colony that brings in about what it eats, nothing fresh is ever seen again: nothing ripens, no nodule grows.
- **What the player would see:** a termite colony the player has not been near for a long time (my rough sum: one
  to three game weeks, to measure) comes back with its queen dead of hunger ("the queen starved while the colony was
  away"), no young, a garden of all-old comb, and then dwindles to a dead mound. Colonies near the player are fine,
  so it looks like far colonies dying for no reason. Shorter absences: the comb comes back older than it should be
  (more old layers, no fresh ones) and no nodules grow for half a day after the return.
- **Where:** `savanna/colony/FungusGarden.java:323-336` (`freshKnown`, `ripeKnown`, `oldKnown`);
  `savanna/colony/GardenAway.java:45-67`; `framework/colony/NestLedger.java:34`, `47-49`;
  `framework/colony/FieldLife.java:284-291`, `554-567` (`addStore(1)` for each piece brought in);
  `framework/colony/FoodStore.java:1014-1023` (`takeCounted`: `addStore(-1)` for each piece eaten), `757-760`
  (`roomKnown`); `framework/colony/NestLife.java:314-338` (the nurses feed the queen only while `nurseFoodLeft > 0`);
  `framework/colony/QueenCare.java:377-436` (she starves away after `queen.starve_ticks`, 120,000, 5 days:
  `macrotermes.json:72`).
- **The proof:**
  1. `freshKnown = max(0, seenFresh + max(0, ledger.store()) - ledger.ripened())` (325). `ledger.store()` is the net
     of pieces in and layers out. Bring 20 pieces in and eat 20 old layers: `store` is 0, so `freshKnown` has not
     moved, while 20 fresh layers really came. `oldKnown = seenOld + aged + min(0, store)` (335): the 20 eaten layers
     are not taken off either.
  2. `GardenAway.grow` ripens `freshKnown * 20 / ripen_ticks` layers a second (45-51) and counts each in
     `ledger.ripened`, which only ever grows while away. So over a whole absence the layers ripened can never be
     more than `seenFresh` plus the highest `store` ever reached.
  3. `store` can never pass the garden's room: the forager numbers bring a piece only while `roomNow > 0`
     (`FieldLife:284`, `554`), and `roomKnown = seenRoom - ledger.store()` (`FoodStore:759`). A comb block holds 4
     layers (`FungusCombBlock.MAX_COMB_LAYERS`), so a garden of 54 comb blocks (the size in BUGS 146) can never show
     more than about 216 layers ripened in one absence, however long it is.
  4. A nodule needs a ripe layer: `canGrow = min(seenCombs, ripeKnown, room)` (`GardenAway:54`), and `ripeKnown =
     seenRipe + ripened - aged`. So the nodules grown in one absence are capped by a fixed number (ripe and fresh as
     seen, plus the garden's room), not by the time away, while the eating goes on by the clock: the queen (a meal of
     6), the king, the young (5 pieces each, `larva_meal`), the young workers (a nodule for 4).
  5. When the nodules are gone, `nurseFoodLeft` is 0, the nurse numbers feed nobody (`NestLife:321-333`), the young
     starve after 48,000 ticks and the queen after 120,000 (`QueenCare:430`); she dies when the colony is loaded again.
  6. Near, the same flows do not cancel: the ledger is put onto the blocks every second (`AwayLife.settle` 576,
     `NestLedger.apply`), a fresh layer goes on top and an old one comes off the bottom as they happen, and
     `FungusGarden.grow` reads the real blocks.
  7. The test for this path, `TermiteAwayTests.aTermiteColonyAwayAgesItsCombFeedsItsQueenAndEatsAsNumbers` (856),
     sets "midday: the foragers stay in" (878), so no piece comes in while away and the two flows never meet. Its
     check that the known ages add up to the food count (957-959, again 989-991) holds with the fault too (fresh,
     ripe and old still add up; only the mix is wrong).
- **Same root, other reader:** `F-1` reached the same fault from the garden's side.
- **Unchecked link:** how many nodules a day a grown colony's numbers eat (my sum of 25 to 60 is from the species
  file, not measured), so how many days it takes. A 30-day soak of one far colony, or the away test with forage
  coming in, would settle it.
- **The fix proposed:** keep two counts in the ledger in place of one net number: layers put on (fresh) and layers
  taken off (old). `freshKnown` adds the first, `oldKnown` takes off the second, and on return `addToStore` and
  `takeFromStore` each work their own count (they already run in that order). No new rule, one number split in two.
  Add to the away test: forage coming in while old comb is eaten, and nodules still growing after the garden is full.

## X-3 (B, likely; A for a player who watches in spectator if the unchecked link holds): the mound's soil sets and dries only on Minecraft's random ticks, so it stands still whenever those do: while the colony is away, and (likely) while the only player near is a spectator

- **What is wrong:** every other clock of the colony runs on game time and catches up after an absence. The soil's
  drying does not: a finished mound block grows one age older (fresh, setting, dry, old) only when Minecraft's random
  tick lands on it. Random ticks only happen in land that is loaded and has a player close by. So the drying clock
  stops whenever the player leaves, and nothing pays it back. Two things hang on that clock. One is looks and
  hardness (fresh soil is dark and breaks like dirt, old soil is pale and breaks like stone). The other is the
  user's building rule: real termites may put a new block only on soil that has set (age 1 or more). The builder
  numbers do not wait for it: the blocks they put in are set from the start.
- **What the player would see:**
  1. A mound left alone for 100 days comes back with every block the real termites laid before the player left
     exactly as wet as it was: still dark, still soft, while the blocks the numbers built in those 100 days are set
     or older (the plan's own age). A mound of mixed, wrong ages; and the Matabele, who bite only through soft soil,
     find old walls still soft.
  2. (If the unchecked link holds.) With only a spectator near, the colony counts as near (a spectator is a watcher,
     `framework/colony/Watchers.java`), the real termites build, but no block they lay ever sets. They cannot stack on
     their own fresh soil, so each column stops after one block until the "stuck" clocks of `M-2` fire: building from
     outside at night, the stage moving on unfinished, the builder numbers filling in. The user watches in spectator
     mode, so this would be the usual way he sees a mound grow.
  3. With the game rule `randomTickSpeed` at 0 the real termites' mound never rises past the first block of a column.
- **Where:** `savanna/block/MoundSoil.java:26-30`, `45-50` (the drying, one chance in 20, 45, 130 a random tick);
  `savanna/block/TermiteMoundBlock.java:79-84` (`canBuildOn`: `AGE >= 1`), `92-103` (`canStartAt`), `196-203` (random
  tick only); `savanna/block/MoundPieceBlock.java:48-55` (the pieces, the same); `savanna/colony/MoundAwayBuilding.java:121`,
  `133`, `164-169` (the numbers' blocks go in at the plan's age, at least 1).
- **The proof:** walked in this code: the only place that raises `AGE` on a standing block is `MoundSoil.age`, called
  only from `randomTick` (grep for `AGE_CHANCE`, `MoundSoil.age`: two callers, both `randomTick`). No part of the
  colony's away life or its return (`AwayLife.settle`, `BuildLife.catchUp`, `MoundAwayBuilding`) touches the age of a
  block that already stands. `canBuildOn` (81) refuses a finished block of age 0.
- **Unchecked link:** Minecraft's own rule for which chunks get random ticks. From memory of 1.21.1
  (`ServerChunkCache.tickChunks`): a chunk's blocks are ticked only when a player who is not a spectator is within
  128 blocks of it, the same gate as mob spawning (which this project has met: "Minecraft spawns nothing near a
  spectator"). Minecraft's code could not be read here. Point 1 does not hang on this link (unloaded land gets no
  ticks of any kind); point 2 does. A two-minute check in the game settles it: in spectator, `/tick rate 100`, and
  see whether a fresh mound block ever changes colour.
- **No test:** `TermiteMoundTests.aMoundBlockIsBuiltInLayersAndMustSetBeforeMoreIsStackedOnIt` (205) checks the
  stacking rule, with the age set by hand. No test lets time pass with nobody near and looks at the soil's age.
- **The fix proposed:** one clock in place of two. The plan already gives every cell an age (`Shape.age()`); the
  colony's own clock (game time) is what everything else runs on. On return, the catch-up pass that already walks
  the mound's cells (`MoundAwayBuilding.buildMound`, bounded at 200 blocks a second) also dries each standing block
  by the time away. For the setting step that building waits on, do not lean on random ticks at all: let the mound's
  once-a-second tick set fresh blocks after their time (it holds the cell list already). Then the drying is the same
  near, away, in spectator and at any `randomTickSpeed`.
- **Owner's call:** today the mound's colours and hardness move only while a (non-spectator) player is near. My pick:
  drying by the colony's clock, so a mound left for a season comes back sun-baked, as a real one would.

## X-4 (B, the path proven; how it looks, to measure): the wear a dead mound owes from a long absence is paid in plain sight, with breaking sounds, when its land loads

- **What is wrong:** a dead mound wears by a clock, and when its land was not loaded it catches up afterwards: every
  100 ticks it changes up to 256 blocks (for all dead mounds of the world together) until it is where its day says.
  That is right for lag. But the catch-up has no rule about being seen, and it breaks each block the loud way
  (`level.destroyBlock`: the breaking sound and crumbs). The living colony's catch-up, by contrast, only works out
  of sight (`BuildLife.freeAndHidden`). New land's dead mounds already have the quiet way (`agedForNewLand`, written
  because "thousands at once flooded the sound player").
- **What the player would see:** coming back to a mound that died long ago: it stands as it was left, then crumbles
  spire by spire with a rattle of breaking sounds, 256 blocks every 5 seconds, until 100 days of wear are done in a
  minute or so. The land loads at the view distance, so part of it is over before a walking player arrives; a flying
  player, or one who logs in beside it, sees all of it. With several dead mounds in loaded land they share the one
  budget in the map's order, so the second waits for the first.
- **Where:** `savanna/colony/DeadMounds.java:250-252` (the look, the shared budget, only `LandReady.at(center)` asked),
  `294-300` (`crumble`: `destroyBlock` unless `quiet`), `351-358` (`update`), `276-285` (`agedForNewLand`, the quiet
  way, new land only); no call to `Population.outOfSight`, `Watchers` or any viewer check in `DeadMounds.java` or
  `DeadWorks.java` (grep: none).
- **The proof:** `tick` runs `update` for every changing mound whose middle is loaded, with `days()` from the clock;
  `wear` (401-470) takes every piece and column whose day has come, each guarded only by `LandReady.at`. Nothing
  compares "how far behind" with "who is looking".
- **Unchecked link:** how many block changes 100 days of wear are for a full mound (not counted), so how long the
  show lasts.
- **No test:** `TermiteMoundTests.aDeadMoundWearsDownToAGrassyHump` (372) wears a mound with time moved by hand; no
  test loads a long-dead mound beside a watcher.
- **The fix proposed:** reuse what is there, no new number: when a mound is behind by more than the wear of one look,
  take the quiet way (`quiet` around the same budgeted `update`), as new land does. The lag bound stays.
- **Owner's call:** today a long-dead mound collapses in front of the returning player. My pick: quiet catch-up (the
  mound is simply found worn); a block or two crumbling with a sound stays for a mound the player lives beside.

## X-5 (B, proven; the same as `D-3`): the dead mounds run on the day clock, every living colony on game time, so `/time set` and a stopped day stop the dead mounds only

- **What is wrong:** `DeadMounds` notes a mound's death and counts its days with `level.getDayTime()`. Everything a
  living colony does (hunger, comb, the mound's allowance, bare patches) uses `level.getGameTime()`. `/time set day`
  sets the day clock to a small number, far below `diedAt`, so `days()` is 0 for every dead mound until the day clock
  has climbed past `diedAt` again; with the game rule `doDaylightCycle` off the dead mounds never wear. The wear only
  moves forward, so nothing is undone, it just stops. A bed moves the day clock forward: the dead mound wears a
  night's worth at once (harmless) while the living colony beside it does not jump.
- **What the player would see:** after one `/time set day` in a world 60 days old, no dead mound wears or rots for 60
  days of play.
- **Where:** `savanna/colony/DeadMounds.java:99` (`days()`), `184` (`died`), `279` (`agedForNewLand`).
- **The fix proposed:** game time in those three places. One clock for living and dead.
- **Owner's call (small):** whether a slept night should wear a dead mound. With game time it does not; nothing else
  in a colony jumps with a bed either, except the forage of the slept night (R1-35's fix).

## X-6 (B, proven for the manager's path; the termite side of R1-27): a colony that dies out while away leaves no dead mound until a player comes near, and its mound starts to wear only then

- **What is wrong:** a colony is ended (its parts told, its dead mound written down) only from the near tick or by
  its queen's body dying. Away, the manager only runs the numbers. So a far colony whose queen starved and whose
  workers all died of age is still a "living colony" in the book for as long as nobody goes there: 100 days if need
  be. The day a player comes near, it ends, and the dead mound's clock starts from that day.
- **What the player would see:** a mound whose colony has been dead for 100 days stands fresh, with every spire, and
  only begins to wear on arrival. Until then the dead colony counts as living wherever living colonies are asked
  for: new land's "is there a living mound in reach" for placing Matabele ants (`NewLandMounds.livingMoundNear`), and
  the Matabele scouts' search for termites, which walk out to a mound with nobody in it.
- **Where:** `framework/colony/ColonyManager.java:95-105` (away: `tickAway` and no more), `116-128` (only the near
  `colony.tick` returning false ends a colony), `58-69` (`queenDied`: ends at once only if nobody is left);
  `savanna/colony/SavannaParts.java:64-65` (`COLONY_ENDED` writes the dead mound); `savanna/colony/DeadMounds.java:184`
  (the death is dated "now"). `colonyEnded` has four callers in all (grep): lines 67, 127, 169 (the kill command) and
  `NewLandMounds.java:617` (a dead mound of new land); none is on the away path.
- **Unchecked link:** none for the path. How often a far colony dies out depends on `X-2`: with that fault, often.
- **The fix proposed:** let the away tick end a colony whose numbers have reached nobody (queen dead, no members, no
  young): tell `COLONY_ENDED` with the game time it happened. `DeadMounds.addMound` already copes with land that is
  not loaded (unloaded cells count as standing, `203`), and the wear already waits for the land. The dead mound's
  clock then starts on the day the colony really died. This is R1-27's fix; the note here is that the termites' parts
  are ready for it.

## X-7 (C, proven): the mound's allowance for a whole absence is paid at the head count on the day of return

- **What is wrong:** the mound earns blocks by the day: 2, plus 0.25 for each worker. `getAllowance` multiplies the
  whole time since it was last asked by the worker count at the moment it is asked. Near, it is asked every second
  and a half, so it follows the colony as it grows. Away, nothing asks it (the mound's away tick is `keepShapes`
  only), so the whole absence is paid in one sum when the colony comes back, or when a far caller asks (`X-1`). A
  colony that left with 20 workers and comes back with 200 after 30 days is paid 30 days at 200; one that shrank
  from 200 to 5 is paid 30 days at 5. The builders' time (`BuildLife.work`) is counted second by second while away,
  so the two budgets of the same mound are worked out in two ways.
- **Where:** `savanna/colony/Mound.java:306-319`; `savanna/colony/SavannaParts.java` (the mound's away tick:
  `keepShapes`); `savanna/colony/Mound.java:292-299`.
- **What the player would see:** a far mound that grew a little too fast or too slow for its history; bounded (the
  allowance only caps the stage), so small.
- **The fix proposed:** the mound's away tick also calls `getAllowance` (one multiplication a second). Needs `X-1`'s
  fix first, so that asking never reaches the plan.

## X-8 (C, to measure): bare patches catch up all in one tick on return

- **What is wrong:** eaten grass tufts grow back after 3 days (`food.regrowth.ticks` 72,000, `macrotermes.json:109`).
  The part that regrows them ticks only near. After a long absence every tuft whose time has come (up to 4,096 are
  kept) grows back in the first look after the return, in one tick: for each, two block changes with neighbour
  updates (`setBlock(…, 3)`), an entity search in its cell, and a walk over the colony's runways. Near, the same
  tufts come back one at a time as their day comes. The catch-up of the mound (200 blocks a second) and the ledger
  (40 a second) are bounded; this one is not.
- **Where:** `savanna/colony/BarePatches.java:109-140` (`tick`), `143-154` (`takenByTheColony`), `36` (`MAX_TUFTS`).
- **What the player would see:** perhaps a hitch on walking up to a big old colony, and all the grass round its
  holes popping up at the same moment, in view (there is no sight check here either). Nothing measured.
- **No test:** `TermiteBarePatchTests.dryGrassEatenByAHoleLeavesBareGroundAndGrowsBack` (39) grows one patch back
  near; nothing comes back to many due tufts.
- **The fix proposed:** a budget per look (say the ledger's own 40 a second), the rest left on the list for the next
  look. The list is already walked in order.

## X-9 (C, proven; beside `F-6`): mushrooms come up only for a colony that was near at the moment the rain stopped

- **What is wrong:** the mushroom flush is set off by "it was raining at my last tick and now it is not". The part
  ticks only near and `wasRaining` is not saved. Rain that ends while the colony is away, or a player who arrives a
  minute after the rain, gives no flush, though real Termitomycetes stand for days after rain.
- **Where:** `savanna/colony/Mushrooms.java:40`, `57-70`.
- **The fix proposed:** ask one shared place "when did the rain last end here" (the same kind of shared source the
  season rule asks for) and flush on arrival if it was within a mushroom's life. No per-colony flag.
- **Owner's call:** today, mushrooms only if you were there when the rain stopped. My pick: also when you arrive
  within about a day of it.

## X-10 (C): small leftovers on this thread

- **The plan's memory is never cleared between worlds.** `MoundPlan`'s four static maps (`GROWN`, `GROWING`, `WALLS`,
  `FOOT_REACH`, `MoundPlan.java:106-116`) are keyed by `max_height` and seed only and are not in
  `SavannaEvents.onServerStopped` (`savanna/SavannaEvents.java:240-246`, which clears four other books). `WALLS` and
  `FOOT_REACH` keep one small entry for every mound ever grown in the sitting. The plan is worked out from the seed
  and the numbers alone, so a second world gets the same answer unless the species file changed in between (`M-5`,
  `M-6` have the staleness).
- **What the away garden has part-grown is lost at a reload.** `GardenAway`'s four running fractions (`noduleGrowth`,
  `ripening`, `ageing`, `crumbling`) are not saved: at most one layer or nodule of progress of each kind per reload.
- **The saved soil counts have no round-trip test.** The shared check `AwayKit.everyPartSavesAndLoads` fills the
  mound's allowance, clock and stage (`Mound.fillForTests`, `Mound.java:1017-1021`) but not `moundWalls` or
  `moundReach`, the two arrays the 2026-11-10 fix leans on.
- **Two raw reads of land that may not be loaded, reachable only by the kill command.** `FungusGarden.getCombs` and
  `DeadMounds.addCombs` (`DeadMounds.java:233-242`) call `level.getBlockState` with no `LandReady` check when a colony
  ends. Every real ending happens near (see `X-6`), where the garden is loaded; `/antcolonies kill` on a far colony
  would make Minecraft load the land on the spot. With `X-6`'s fix this becomes a real path and needs the guard.
- **Seen by others, on this thread too:** a window a builder bit open stays open when the builder is folded into the
  numbers or its land unloads (`G-2`; the goal's `stop()` never runs and `windowIn` is not saved,
  `BuildMoundGoal.java:71-75`, `156-166`); the queen does not swell while away (`F-5`); a king outlives his colony
  (`F-2`).

## Point 5: what each part keeps through a save and reload

"Rebuilt" means worked out again from what is saved or from the blocks. "Lost" means gone, with what goes wrong.

| Part | Field | Saved, rebuilt or lost | What goes wrong if lost |
| --- | --- | --- | --- |
| `Mound` | `allowance`, `updatedAt`, `workStage` | saved (`moundAllowance`, `moundUpdated`, `moundWorkStage`) | an old save with no stage starts at stage 1 and climbs (written in the code) |
| `Mound` | `wallsAt` / `savedWalls`, `reachAt` | saved (`moundWalls`, `moundReach`) once the plan was grown | none saved (old save): worked out in the background; trusted for good once saved (`M-5`); no round-trip test (`X-10`) |
| `Mound` | `lastProgress`, `stageCheckedAt` | lost (start at 0) | the "stuck" clocks start again: fallbacks and a stalled stage wait their full time once more; the first far ask after a load checks the stage at once (`X-1`) |
| `Mound` | `buildClaims`, `soilClaims`, `unreachable` | lost | nothing: claims lapse, the insects (whose goals also start again) claim anew |
| `Mound` | `pelletsTaken` | lost | a half-dug block gives a few pellets too many (written in the code) |
| `Mound` | `held` (the plan), `cachedStage`, `cachedParts`, `cachedShape`, `galleries`, `spots` | rebuilt | the plan is grown again (a second of work, in the background if asked the right way: `X-1`, `M-1`); the leftover-crumbling pass runs once more after a load, harmless |
| `MoundPlan` | everything | rebuilt from the seed and the species numbers | the standing blocks and the plan disagree if the numbers or the growing code changed since they were built (`M-5`, `M-6`) |
| `MoundGrowth`, `MoundReach`, `MoundWear` | (no state of their own found; `MoundGrowth` and `MoundReach` read by search only) | - | - |
| `MoundShaping` | `carveClaims` | lost | nothing: claims lapse |
| `FungusGarden` | the comb as last seen (combs, fresh, ripe, old, nodules) | saved (`gardenSeen`; an older save's `spare` is read as ripe) | - |
| `FungusGarden` | counters for tests, the test speed | lost | nothing in play |
| `GardenAway` | the four running fractions | lost | under one layer or nodule each (`X-10`) |
| `Mushrooms` | `wasRaining`, `lastFlush` | lost (the part is marked `notSaved`) | a flush is missed only if the game is closed in rain and opened after it (and see `X-9`) |
| `KingCare` | `kingLastFed`, `kingStarvedAway` | saved (checked by the away test, `TermiteAwayTests.java:1023`) | - |
| `BuildLife` (framework) | `work`, `soil` | saved (checked, `TermiteAwayTests.java:1004`) | - |
| `NestLife` (framework) | `queenSize` | saved | - |
| `DeadMounds` | every mound: death day, columns, pieces, shaft, ways, highest, settled, combs, works | saved | - |
| `DeadMounds` | `byOffset` | rebuilt | - |
| `DeadMounds` | `quiet` (static) | set and put back in a `finally` (`280-284`) | - |
| `DeadWorks` | holes, sheets, spots, walls, galleries, reach | saved | - |
| `BarePatches` | the tufts (`regrowth`) | saved | - |
| `BarePatches` | `nextCheck` | lost (0) | nothing: it looks at once |
| `NewLandMounds` | places waiting, trees waiting, the counts | saved; the mounds still on their way are handed to the book as the world closes (R8-11, test `aMoundHandedOverAsTheWorldClosesIsNotLost`) | - |
| Queen (entity) | `Size` | saved | - |
| Queen (entity) | `colonyChecked` | lost | on her first tick she makes a new colony only if hers is not in the book; right as written |
| King (entity) | `feeder`, `feederUntil` | lost | nothing: the nurse's claim lapses |
| King (entity) | his hunger | saved with the colony (`KingCare`), not on his body | he outlives a colony that is gone (`F-2`) |
| Worker (entity) | hunger, what it carries | saved (test `whatATermiteCarriesIsKeptAfterAReload`) | - |
| Worker (entity) | its building goal's state (the spot, the open window) | lost | the window stays open (`G-2`) |

## What is solid

- **The away builder cannot build a block twice or miss one for good.** `MoundAwayBuilding.buildMound` reads the world
  for every cell (`mound.jobAt`), so what the real termites built before leaving, or build beside the numbers near,
  is simply found done. It asks `LandReady.at` for each cell, so a half-loaded mound is built where it is loaded and
  the rest waits. It is bounded (the builders' time, 200 blocks a second, 8 passes a second).
- **The brain's last look is never old.** Near, the garden counts its comb every second and keeps the count; on
  leaving with the land still loaded it looks once more (`AwayLife.tickAway` 405-414, BUGS 146). So a player who
  teleports away leaves the brain with a picture at most a second old.
- **Folding and coming back is tested end to end**, with a save and load in the middle
  (`aTermiteColonyAwayAgesItsCombFeedsItsQueenAndEatsAsNumbers`, `...FoldsItsFrozenInsectsIntoTheNumbersAndPutsThingsBackOnReturn`):
  comb ages, nodules, food, eggs, young, meals, building time and the king's last meal are compared before and
  after.
- **A slept night is foraged by the numbers** (`FieldLife.nightJumpedOver`, 372-384; test
  `aNightSleptThroughIsForagedByTheTermiteNumbers`), so a bed no longer starves the colonies; hunger and the mound's
  allowance are on game time and do not jump.
- **`/tick rate 100`** speeds game time, the once-a-second brain and random ticks together; nothing on this thread
  counts real seconds.
- **The dead mounds' book** saves everything, only ever moves forward, guards each block with `LandReady`, and has a
  hard budget a look. A reload in the middle of the wear picks up where it was.
- **More than one player:** nothing on this thread asks for "the player". `Watchers.near` and the sight checks walk
  every player of the dimension; the plan is held while any watcher is within 128 blocks; the hooks objects
  (`GardenAway`, `MoundAwayBuilding`) are made once for each colony, not for each player or tick. The one shared thing
  is the dead mounds' budget of 256 blocks a look for the whole dimension (`X-4`).
- **New land's queue** is kept apart for each world and cleared when the server stops (R8-11).
- **The colony's own cell list is cached once a tick** (`framework/colony/Nest.java:853-864`), so the many askers on
  return do not each rebuild it.

## What was read

Read in full: `savanna/colony/Mound.java`, `MoundAwayBuilding.java`, `GardenAway.java`, `FungusGarden.java`,
`SavannaParts.java`, `DeadMounds.java`, `DeadWorks.java`, `MoundWear.java`, `BarePatches.java`, `Mushrooms.java`,
`KingCare.java`, `MoundShaping.java`, `NewLandMounds.java`; `savanna/entity/TermiteQueenEntity.java`,
`TermiteKingEntity.java`, `TermiteWorkerEntity.java` (each from line 40, the class itself);
`savanna/entity/goal/BuildMoundGoal.java`, `ShapeMoundGoal.java`; `savanna/SavannaEvents.java`;
`savanna/block/MoundSoil.java`; `framework/colony/ColonyPart.java`, `AwayLife.java`, `ColonyManager.java`,
`BuildLife.java`, `NestLedger.java`, `WorkerFood.java`, `Watchers.java`, `LandReady.java`.

Read in part: `savanna/colony/MoundPlan.java` (84-258); `savanna/block/TermiteMoundBlock.java` (60-226),
`TermiteTunnelBlock.java` (36-244); `savanna/colony/FeedingSpots.java` (150-209);
`savanna/entity/MatabeleEntity.java` (555-584, 800-829); `savanna/entity/goal/MatabeleScoutGoal.java` (285-323);
`framework/colony/Colony.java` (225-260, 590-830, 940-1027), `NestLife.java` (60-404), `FieldLife.java` (255-294,
352-401, 535-574), `FoodStore.java` (the store's room and counted eating), `Nest.java` (853-883);
`savanna/gametest/TermiteAwayTests.java` (840-1025, and the list of its tests); `macrotermes.json` (the numbers
named above); round 1's file (the findings named at the top); the headings, and `M-2`, of the other readers' files of
this round.

By search only, not read through: `savanna/colony/MoundGrowth.java`, `MoundReach.java`, `framework/colony/QueenCare.java`,
`Population.java`, `savanna/block/MoundPieceBlock.java`.

Not read: the other random-ticking blocks (`MudCrustBlock`, `MudCastBlock`, `TermiteRemainsBlock`, `TermitomycesBlock`,
`GiantTermitomycesBlock`), so whether they too stand still while away is not checked; `TermiteSoldierEntity`; the
galleries, foraging holes, feeding, alarm and raids (the second half's); `docs/DESIGN.md` (so whether the dead
mounds' day clock, `X-5`, is a recorded choice was not checked); Minecraft's own code (the unchecked link of `X-3`).
Path 4's partial unload (a colony near, with part of its mound's land not loaded or not ticking its insects) was
walked only as far as the builder's `LandReady` guards; the frozen-insect side is round 1's (R1-43 to R1-45).
