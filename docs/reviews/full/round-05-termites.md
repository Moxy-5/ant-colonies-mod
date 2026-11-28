# Full code review, round 5: the termites (reading round)

The round is read in two halves (the user, 2026-11-27: "split this up, I'm expecting a lot of bugs"). **Part A, the
mound itself, is first (read 2026-11-27).** **Part B, outside the mound (galleries, hidden trips, foraging holes,
feeding, alarm, soldiers), follows it (read 2026-11-27) and carries on the numbers from R5-52.** The whole round: 97
findings, 16 of grade A, 44 B, 37 C; nine wait for the owner's yes.

# Part A: the mound (2026-11-27)

Read in `C:\Ant Mod` on branch `claude/motion-glue` at commit `4dcd1fe6`, by a session that did not write the code.
Rule book: `docs/plans/code-review.md`. No code was changed; no gradle, game, game test or film was run, so **nothing
here is measured**: every lag finding is "to measure" unless the path through the code proves its cost or an earlier
measure is named.

**What was read, every line** (about 9,100 lines): in `savanna/.../colony/`: `Mound`, `MoundPlan`, `MoundReach`,
`MoundGrowth`, `MoundShaping`, `MoundAwayBuilding`, `MoundWear`, `NewLandMounds`, `DeadMounds`, `DeadWorks`,
`BarePatches`, `FungusGarden`, `GardenAway`, `Mushrooms`, `KingCare`; in `savanna/.../entity/`: `TermiteQueenEntity`,
`TermiteKingEntity`; in `entity/goal/`: `BuildMoundGoal`, `ShapeMoundGoal`; in `block/`: `TermiteMoundBlock`,
`MoundPieceBlock`, `MoundSoil`, `TermiteTunnelBlock`, `TermiteRampBlock`, `TermiteMouthBlock`, `TermiteSpireBlock`,
`FungusCombBlock`, `DeadCombBlock`, `TermitomycesBlock`, `GiantTermitomycesBlock`; the species file
`macrotermes.json`; and the calls out of these into the framework as far as each finding needed. Each reader's file
ends with its exact list and with what it did not finish.

**Who read it.** Six readers. Four with a slice each: **M** the mound's plan, **G** how the mound grows and is built,
**D** mounds in new land, dead mounds and wear, **F** the fungus garden and the royal pair (with the food sums). Two
across all the files on one thread each: **X** near against away, and what lives through a save and reload; **Y**
every block the mound places or removes (its file starts with a table of all 50 places). Their own files are kept in
`docs/reviews/full/round-05a-readers/` (their findings carry the letters M, G, D, F, X and Y there, with the full
walk of every proof). The lead session then read the lines of every grade A finding itself (each says "checked again
by the lead"), read Minecraft's own 1.21.1 code for the three links the readers could only give from memory (random
ticks near a spectator, a block read in land not loaded, `/time set`), merged what two or three readers found from
different sides (67 findings in the readers' files, 51 here), and changed four grades: X-3 is grade A here (its open
link holds, R5-3); F-2 and Y-1 are grade B (R5-18, R5-25); F-6 is grade C (R5-29).

**Not written again here:** what rounds 1, 2, 6, 8 and 11 already list. Where a finding is a new side of one of
theirs it says so (R1-4, R1-17, R1-27, R2-16, R2-39, R6-28).

**How to read a finding.** Rating: **A** the player can meet it, **B** it will cost us when building on this,
**C** a tidy-up. Proof: **proven** (the path was walked through the code, lines named), **likely** (one link, named,
was not checked), **to measure** (a lag finding with no number yet). A finding's `R` number is its only number and it
keeps it for good; a fix session adds a status line under a finding and never rewrites it. Each finding names the
readers' findings it comes from: the full proof is there. Paths are short: `savanna/...` is
`savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is `framework/src/main/java/com/antcolonies/...`.

**Count:** 51 findings: 8 A, 20 B, 23 C. Four of them wait for the owner's yes (R5-9, R5-11, R5-17, R5-22).

## The short of it

**The mound works while it is watched by a player in survival or creative, and goes wrong in three other
situations:**

1. **Watched in spectator (R5-3).** Fresh mound soil only "sets" on Minecraft's random ticks, and Minecraft gives no
   random ticks near a spectator. The user's own rule says termites may only build on soil that has set. So with only
   a spectator near, the real termites can never stack a second block on their own soil; the mound then grows by its
   "stuck" clocks and by the builder numbers instead (R5-9). The user watches in spectator.
2. **Left alone (R5-2, R5-24, R5-16).** A far colony's fungus garden stops growing nodules once the colony eats about
   what it brings in, so a colony left for weeks loses its young and then its queen. Two readers found this from two
   sides. A colony that dies while away is not ended until a player comes near. The queen does not swell while away.
3. **Asked from far (R5-1).** Several far callers still make a mound grow its whole plan on the game's own thread, a
   freeze of one to two seconds a mound: the colony list command, new land looking for living mounds, a Matabele
   scout's look round, and the first tick of a colony the player is beside.

**The building walk is patchy, not a rewrite.** Shaping hands every builder the one lowest job (R5-4), the soil
carrier walks by its own rules with up to 12 route searches in a tick (R5-8), and a window bitten in a tunnel is owned
by the builder's goal, so it stays open when the builder goes (R5-5). One shared "no way there" memory for the mound
and putting `BuildMoundGoal` on the shared `Errand` mend the first two; the mound owning its windows mends the third.

**Dead mounds and new land:** the wear writes blocks at the edge of loaded land (R5-6), a mound of new land goes in
during one tick (R5-7), the dead mounds count on a different clock from everything else (R5-19), and a long absence
is paid back in front of the player with breaking sounds (R5-23).

**No part is a candidate for a rewrite.** The plan and its proof (`MoundPlan`, `MoundGrowth`) are sound and pure. One
thing would remove a whole family of findings: a single shared question, "may this colony change this block now?"
(R5-26), in place of about a dozen sets of checks that each forgot something different (R5-6, R5-21, R5-25, R5-27).

**The food sums close near a player** (reader F worked them through with the file's numbers): one piece of forage
gives one nodule and four worker meals, a full colony needs about 50 pieces a day, and the two streams match the two
needs. After its first days a colony grows as fast as its forage, not its queen, and stops at the cap of 200 workers.

## The four that matter most

- **R5-3:** mound soil never sets near a spectator, so the termites the user watches cannot build by their own rule.
- **R5-2:** far colonies stop growing nodules and lose their queen (found twice, from two sides).
- **R5-1:** asking a far mound for its stage or cells freezes the game for a second or two.
- **R5-4 with R5-8:** one job nobody can reach stops all shaping, and the soil carrier's walk has no cheap "no way".

## Grade A

### R5-1 (A, proven): asking a far mound for its stage or its cells still grows its whole plan on the game's own thread, a freeze of one to two seconds a mound

**In play (2026-11-27):** A. A freeze of one to two seconds, with no cause in sight, for each far termite colony asked: on `/antcolonies list`, when new land puts in a dead mound, when a Matabele scout looks round, and on the first tick of a colony the player is beside when the world opens.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: `Mound.getStage` only reads; the stage moves in the near tick (`moveStageOn`) and with the builder numbers (`nextStage`), and never while the plan is missing or a cell lies in land not loaded (`stageKnown`). `getParts` never grows a plan: it asks in the background and answers with what it has; `partsNow` waits where the cells are needed at once (a colony's end, a mound of new land, a mound put up by command). `Mound.tick` returns while the plan is on its way; the colony list line shows `?` for soil counts not known yet; `livingMoundNear` measures the distance first. Test `aMoundAskedWithNoPlanKeptNeverGrowsItOnTheGamesOwnThread`. Not measured in a world with far mounds after a restart.**

**Checked again by the lead.** Read `Mound.getStage` (`savanna/colony/Mound.java:345-369`), `isStageDone`
(`:410-417`), `getParts` (`:443-459`), `stages`/`stage` (`:278-285`), `tick` (`:465-476`), `keepShapes` (`:292-299`),
`MoundPlan.growth` (`savanna/colony/MoundPlan.java:133-156`), `growthSoon` (`:198`), and
`NewLandMounds.livingMoundNear` (`savanna/colony/NewLandMounds.java:358-366`). It holds on all four paths.

- **What is wrong:** growing a mound's plan (every cell of every stage) takes "about a second" by the code's own
  notes (1.2 to 1.8 s in the logs of 2026-11-10). The 2026-11-10 fix made the stage answerable from a few saved
  numbers. But `getParts` still falls back on `MoundPlan.growth`, which grows the plan on the calling thread, or waits
  (`join`) for the background job, whenever the plan is not held (no player within 128 blocks) and not among the 24
  last grown. And `getStage` is not a plain lookup: when the colony has earned a taller stage it asks "is this stage
  finished?", which needs the cells. In land that is not loaded that question always answers yes (every cell is
  skipped), so the stage also climbs by one each time a far mound is asked.
- **The four paths:** (1) `/antcolonies list`: every colony's line counts its finished blocks through `getParts`
  (`Mound.java:153-160`, `:541-549`); after a restart every far termite colony freezes the game in turn.
  (2) `livingMoundNear` calls `getStage` on every termite colony of the world before it looks at the distance, each
  time new land puts in a dead mound that could hold ants. (3) A Matabele scout's look round asks the foot reach of
  every termite colony in its trip's reach (`FeedingSpots.java:193-199` from `MatabeleScoutGoal.java:305-308`), the
  very path the 2026-11-10 note calls fixed. (4) Near: `Mound.tick` starts the background job and on its next line
  calls `getParts`, which waits for that same job; so the "known leftover" is not a lost race, the tick waits the whole
  growing time whenever the plan is not kept yet.
- **Also:** a mound in land nobody has loaded has its building stage moved on with nothing built.
- **The fix proposed:** one rule in place of two. `getStage` only reads and never moves the stage on (the stage moves
  in the two places that build: the near tick and the away builder). `getParts` never grows: with no plan kept it asks
  for it in the background and returns what it has; `tick` returns while the plan is still on its way. `isStageDone`
  answers "not known" when any cell is in land not loaded. `livingMoundNear` measures the distance first. A test: a
  colony with no watcher and a dropped plan; `getStage`, `getParts` and the list line return within a few
  milliseconds and the stage stays put.
- **From:** M-1, X-1, D-14, G-14 (where the second of growing goes).

### R5-2 (A, the sums proven; how soon it bites to measure with a soak): while a colony is away its fungus garden only "sees" the comb left over after eating, so nodules stop growing, the young starve and then the queen

**In play (2026-11-27):** A. A termite colony the player has not been near for a long time (a rough sum: one to three game weeks) comes back with no young, a garden of all-old comb and its queen dead of hunger, and dwindles to a dead mound; colonies near the player are fine.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: `NestLedger` keeps the layers that cancel in what the blocks are owed (`turnover`, saved; `storePutOn`, `storeTakenOff`), so the garden knows every piece brought in as fresh comb and every layer eaten as an old one gone (`FungusGarden.freshKnown`, `oldKnown`); the blocks are still owed only the net, and the pairs are dropped when the blocks catch up. Pieces laid away count as comb with no comb block seen (`combsKnown`), so the second door is shut too. Test `aGardenAwayKnowsEveryPieceBroughtInAsFreshCombThoughAsManyOldLayersAreEaten`. Left: the 30-day soak of a far colony (how the sums run over weeks is not measured).**

**Checked again by the lead.** Read `FungusGarden.freshKnown`, `ripeKnown`, `oldKnown`
(`savanna/colony/FungusGarden.java:323-336`), `GardenAway.grow` (`savanna/colony/GardenAway.java:37-85`) and the
writers of the one number (`framework/colony/FoodStore.java:1014-1023` eating, `FieldLife.java:288`, `:563` and
`AwayLife.java:455` forage in). The sums hold. Two readers reached it by different roads (F from the garden, X from
the away life).

- **What is wrong:** real comb is a conveyor: forage goes on top fresh, ripens, grows a nodule, goes old, is eaten
  from the bottom. Near a player the blocks do exactly this. Away, the brain keeps one signed number, "layers owed"
  (`NestLedger.store`): a piece brought in adds 1, a layer eaten or crumbled takes 1 off. The garden reads that one
  number two ways: only the part above 0 counts as fresh, only the part below 0 takes away old. So a fresh piece and
  an eaten old layer cancel: bring 20 in and eat 20, and the brain has seen no fresh comb at all. Fresh comb is only
  counted as far as the store grows, and the store cannot grow past the garden's room. After that nothing ripens and
  no nodule grows, while the queen, the king, the young and the soldiers go on eating nodules by the clock.
- **A second door to the same end:** `GardenAway.grow` returns at once when the colony left with no comb block
  (`:39`), so a pair that was put down and left never grows a nodule away, however much forage comes in.
- **Why no test caught it:** `TermiteAwayTests.aTermiteColonyAwayAgesItsCombFeedsItsQueenAndEatsAsNumbers` (`:856`)
  keeps the foragers in, so the two flows never meet, and its check that the known ages add up holds with the wrong
  split too.
- **Unchecked link:** how many days it takes in play. A 30-day soak of one far colony, or the away test with forage
  coming in, settles it.
- **The fix proposed:** split the one number in two: layers put on (fresh) and layers taken off (old); the ledger
  still owes the blocks only the net. No new rule. Count a piece laid away as a comb owed, so the "no comb seen" guard
  can go. A test: away, eat all the old comb, then bring 6 pieces in: 6 fresh layers known, and nodules grow.
- **From:** F-1, X-2.

### R5-3 (A, proven): mound soil sets and dries only on Minecraft's random ticks, so it stands still near a spectator, while the colony is away, and at random tick speed 0

**In play (2026-11-27):** A. Watching a mound in spectator, no block the termites lay ever sets, so they cannot stack soil on their own soil; the mound grows only when the "stuck" clocks fire (building from outside at night, stages moving on unfinished) and by the builder numbers.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: `MoundDrying`: once a second, while the mound holds its plan and its land is loaded, the mound gives its own cells (and the footing under its foot) the random ticks Minecraft withholds (no player but a spectator within 128 blocks, or the game rule at 0), at Minecraft's own pace, so the average 23 minutes to set is unchanged; after a pause (the colony left alone, the game closed) the whole mound dries by the time that passed, 400 cells a second, starting when a watcher comes within 128 blocks (`MoundSoil.agedBy`; the time is saved). Near a survival player nothing changes. Test `moundSoilSetsWhereMinecraftGivesNoRandomTicks`. Left: a mound between 64 and 128 blocks from a survival player for a long time dries a little fast (the game's ticks and the pause's both count); dead mounds and soil outside a living mound's plan still dry on random ticks only; the other random-ticking termite blocks (mud crust, casts, mushrooms) are not looked at.**

**Checked again by the lead, with Minecraft's own code.** In the mod: the only place that raises a standing block's
age is `MoundSoil.age` (`savanna/block/MoundSoil.java:45-50`), called only from the blocks' `randomTick`;
`TermiteMoundBlock.canBuildOn` (`savanna/block/TermiteMoundBlock.java:79-84`) refuses soil of age 0. In Minecraft
1.21.1 (read from the build's own source jar): `ServerChunkCache.tickChunks` (`:367-374`) runs a chunk's random ticks
only when `anyPlayerCloseEnoughForSpawning`, and `ChunkMap.playerIsCloseEnoughForSpawning` (`:983-990`) answers no
for a spectator and for any player 128 blocks or more away. So the reader's open link holds: this is the same gate
as "Minecraft spawns nothing near a spectator".

- **What is wrong:** every other clock of the colony runs on game time and catches up after an absence. The soil's
  drying does not. A spectator counts as a watcher for the colony (`framework/colony/Watchers.java`), so the colony is
  real and its termites build, but nothing they lay sets. The builder numbers do not wait for it: their blocks go in
  already set (`MoundAwayBuilding.java:121`, `:133`, `:164-169`).
- **What the player would see, beside the above:** a mound left for 100 days comes back with every block the real
  termites laid exactly as wet as it was, among set blocks the numbers built: mixed, wrong ages, and old walls still
  soft for the Matabele, who bite only soft soil. With the game rule `randomTickSpeed` at 0 no column rises past its
  first block.
- **The fix proposed:** one clock in place of two. The mound's once-a-second tick sets fresh blocks after their time
  (it holds the cell list already), and the catch-up pass that walks the mound on return also dries each standing
  block by the time away. Then drying is the same near, away, in spectator and at any random tick speed. My pick for
  the time: the same average as today (about 23 minutes for fresh soil to set), so only the cases above change.
  A mound left for a season then comes back sun-baked, as a real one would.
- **Also to check in part B:** the other random-ticking blocks of the termites (mud crust, mud casts, remains) and
  the two mushroom blocks stand still near a spectator the same way.
- **From:** X-3 (grade B there with this link open), M's balance note on setting, R5-9.

### R5-4 (A, likely): shaping hands every builder only the one lowest job; when no termite can reach that one from inside, no termite carves or reshapes anything

**In play (2026-11-27):** B. Builders stand idle while the mound has tunnels still to carve and spire pieces still to thicken; the stage only moves on after the 5-minute stall, and the same job is still lowest on the next stage.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: a carve or reshape job no builder found a way to is set aside like an unreachable soil spot (`Mound.markUnreachable`, `isUnreachable`; `MoundShaping.pickShapeJob` passes over it) and the builder is handed the next lowest at once (`ShapeMoundGoal.canUse`, two jobs a look), so one job out of reach no longer idles every builder. No test of its own (the mound tests pass).**

**Checked again by the lead.** Read `MoundShaping.pickShapeJob` (`savanna/colony/MoundShaping.java:48-70`: one
`best`, the lowest, skipping only cells claimed by others) and `ShapeMoundGoal.canUse` and `walkThere`
(`savanna/entity/goal/ShapeMoundGoal.java:62-100`: a job left alone or with no way there is released and the goal
answers no, with no second pick). The path holds.

- **What is wrong:** the picker returns the single lowest job. A builder that cannot get beside it leaves it alone
  for itself and gives up looking; it never asks for the next job, and every other builder is handed the same one.
  The pretend mound that proves the stages buildable does every job it can in a pass (`MoundGrowth.java:590-624`), so
  the proof never meets the game's picking order.
- **Unchecked link (why "likely"):** whether a real stage has, at some moment, a lowest carve job with no channel
  beside it. `docs/plans/apex-termites.md:55-57` (T4) says one did; it is most likely when a new stage carves new
  tunnels through old soil, so a whole chain turns into carve jobs at once and only its near end can be done first.
- **The fix proposed:** no new rule: one memory of "no way there" for the mound, used by both goals. `Mound` already
  keeps one for soil spots (`markUnreachable`); the picker skips marked cells and hands out the lowest job that is
  not marked. A test: a stage whose lowest carve cell is two cells along a chain of finished soil; the builders carve
  the chain within a minute.
- **From:** G-1.

### R5-5 (A, the path proven; how often likely): a window a builder bites in a tunnel stays open for ever if the builder dies, is folded into the numbers, or its land unloads while it works through it

**In play (2026-11-27):** B. A hole in the mound's skin that never closes; termites can walk out through it by day, past the rule that they go out only by the foraging holes.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: the mound keeps the windows its builders hold open (`Mound.windowHeld`, `windowLetGo`, from `BuildMoundGoal`), and every 5 seconds shuts any side of a tunnel block that opens to the outside and is held by nobody (`plugStrayWindows`). A window left by a builder that died, went into the numbers or was unloaded closes within seconds. No test of its own.**

**Checked again by the lead.** Read `BuildMoundGoal.place`, `closeWindow` and `stop`
(`savanna/entity/goal/BuildMoundGoal.java:156-166`, `:232-283`) and `AbstractAntEntity.remove`
(`framework/entity/AbstractAntEntity.java:513-527`): the window is closed only by the goal's own `stop`, give-up or
success, and removing the insect stops no goal. The window is a block state, saved with the land.

- **What is wrong:** the goal owns the window. A tunnel block never closes a side toward open air by itself
  (`TermiteTunnelBlock.java:191-196`) and nothing on a living mound plugs a stray window. The window is held open
  about 12 ticks for each pellet, and as long as a creature stands in the building spot (up to 600 ticks), so a
  player standing on a spot at night and then leaving makes it likely. Each one is for good, so they add up.
- **The fix proposed:** the mound owns the windows: on a plan walk it already makes, plug any tunnel side open to
  the outside air that no builder with a live claim is working through. No new number, and it heals windows already
  in saved worlds. A test: a builder killed with its window open; the window is plugged within 5 s.
- **From:** G-2, X-10, Y-8.

### R5-6 (A, the mechanism proven; how often and how long to measure): dead mounds are worn right at the edge of loaded land with only each block's own chunk checked, and every change makes Minecraft read the blocks beside it, which loads the next chunk on the spot

**In play (2026-11-27):** B. A short hitch now and then while walking or flying through savanna with dead mounds that still have wear to do, most of all on coming back to one left for days. Its length is not measured.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: a dead mound is worn only when all the land its works reach is loaded (`DeadMounds.tick`: `LandReady.round(center, reach + 1)`, `DeadMound.reach`); a bare patch asks for its tuft's neighbours too (`BarePatches.tick`), and mound soil looks only at neighbours in loaded land (`TermiteMoundBlock.besideSetSoil`). No block is read past the edge of loaded land. Not measured in a flight.**

**Checked again by the lead, with Minecraft's own code.** In the mod: `DeadMounds.tick`
(`savanna/colony/DeadMounds.java:245-259`) works a dead mound whenever its middle's chunk is loaded, no player needed
near, and each write is guarded by its own cell's chunk only; the writes use `UPDATE_ALL` or `destroyBlock`
(`:294-300`, `:452`). In Minecraft 1.21.1: `Level.getBlockState` (`:406-413`) fetches the chunk through
`getChunk(x, z)`, which asks for the full chunk and waits for it (`:201-203`), and a change with these flags reads
each neighbour that way. `docs/BUGS.md` 183 records the same mechanism (8 to 13 s there, for land still to be made).

- **What is wrong:** a full mound's foot is up to 27 blocks across and its works reach 50 blocks, so nearly every
  dead mound lies across several chunks. A cell in the last loaded row is worn, and Minecraft then fetches the chunk
  beyond it. The framework already learnt this for the nest (`Nest.touchesFluid`, R1-8 and R1-18: "the cell was
  guarded, its neighbours were not").
- **Where else:** `DeadWorks.java:160-273`, `BarePatches.java:120-137`, and one read of the mod's own with the same
  gap, `TermiteMoundBlock.besideSetSoil` (`:109-118`). The living mound is exposed only at a view distance of 2 to 4
  chunks; new land is safe (everything 24 blocks round is ready first).
- **Unchecked link:** the cost of one fetch. The land beyond has almost always been made before, so it should be a
  load from disk (milliseconds), not new land; that is an expectation, not a measure. To measure: a flight run over
  land with dead mounds whose clocks were set back, then look for `[stall]` lines naming `DeadMounds`.
- **The fix proposed:** one check per dead mound per look: the whole box of the mound and its works plus one block
  is loaded; the per-cell checks can then go. Best as part of the shared question of R5-26.
- **From:** D-1, Y-2.

### R5-7 (A, proven by an earlier measure, not measured again): putting one mound of new land in takes up to 95 ms in a single tick, and its test allows 400 ms

**In play (2026-11-27):** B. A short hitch as each mound of new land appears while exploring, worst for big living mounds and for old dead ones; one can go in every half second while places wait.

**Status (2026-11-27, `claude/review-fix-5a`): half fixed: measured part by part, and the log line now says what each part took (`[new-land] ... in N ms (members, cleared, mound, nest, comb, pair / ended, worn)`). The second mound of a sitting: a full living mound 28 ms (the mound's blocks 21), an old dead hump 61 to 77 ms (mound 17 to 25, ended 4 to 15, worn 10 to 20, its trees and burrow about 22); after many, a dead mound takes about 15 ms. The first mound of a sitting takes 300 to 400 ms, nearly all of it Java loading and warming the code. So the dig's neighbour updates are not the cost (the nest is 1 ms warm) and that change was not made. Tests: `aFullLivingMoundOfNewLandGoesInQuickly`, `anOldDeadHumpOfNewLandGoesInQuickly` (a first mound not timed, the second under 200 ms beside five other servers). Left: an old hump built straight at its worn heights (it would take its 61 to 77 ms to about 40), and the first mound's one-time cost.**

**Checked again by the lead.** Read `NewLandMounds.tick` (`savanna/colony/NewLandMounds.java:184-207`: one whole
mound a look, `return` after it), `DeadMounds.agedForNewLand` (`savanna/colony/DeadMounds.java:274-288`: the wear
with no block budget) and `docs/plans/savanna-loading.md:43` ("putting a mound in (6 to 95 ms, once)"). The round's
rule: a single tick past 50 ms is a grade A finding. That plan judged it "not the problem" for loading new land,
which was right for its question; it is still a hitch.

- **What is wrong:** the members, the mound built block by block, the nest dug, the gardens and the royal pair all
  go in during one tick. An old dead mound is built as a full cathedral of thousands of blocks and then worn down
  nine tenths in the same tick. The test `aQueuedMoundIsGrownAsideThenPutInQuickly`
  (`TermiteNewLandTests.java:221-239`) times a half mound and passes up to 400 ms.
- **The fix proposed:** measure part by part first. Then two removals, no new number: a dead mound of new land is
  built straight at its worn heights instead of built whole and crumbled; and the dig changes blocks the way world
  making does (no neighbour updates: nobody has seen the land). If it is still over 50 ms, split a put into its steps
  over successive looks. The test should time a full mound and an old hump against the tick's budget.
- **From:** D-2, Y-4's side note.

### R5-8 (A, to measure; a new side of R2-39 and R2-16): the soil carrier walks by its own rules, with up to 12 route searches in one tick and no cheap "no way there"

**In play (2026-11-27):** B. Short freezes beside a mound whose building has stalled (a player broke the tunnels, odd ground, or a spectator watching, see R5-3), because the fallbacks open exactly then. Not measured.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: the soil carrier walks by the shared legs' own planning, one spot a tick (`BuildMoundGoal.walkToSpot`: one route search a tick, was up to 12), a spot with no way to it is marked unreachable at once and another is picked (`noWay`), and a walk that ends where it cannot work from, or runs past 30 seconds, marks the spot too. Not measured beside a stalled mound.**

**Checked again by the lead.** Read `BuildMoundGoal.tick`, `canWorkHere` and `walkToSpot`
(`savanna/entity/goal/BuildMoundGoal.java:168-230`): the loop tries up to 4 spots, each from up to 3 kinds of place,
with `moveTo` given a foothold test but no reach. The reader's reading of the legs (`AntNavigator.java:353-365`,
`:904-916`, `:936-938`: the "nothing near it, no search" shortcut needs a reach, the "asked in vain just now" memory
skips searches that carry a test) was not read again by the lead; R2-16 measured a failed search at about 20 ms.

- **What is wrong:** `BuildMoundGoal` does not use the shared `Errand` the other goals walk with. Three more holes in
  the same walk: when the legs give up a walk, the goal plans a new one at once; a spot given up after 30 s is not
  marked unreachable, so the next look may pick it again; and arriving is judged by another test than planning for
  the outside and far reaches.
- **The fix proposed:** remove the goal's own walking and put it on `Errand` like `ShapeMoundGoal`, with the reach of
  each kind (1, 1 and 2 blocks are known), one spot tried a tick, and the given-up spot into the mound's one "no way
  there" memory (R5-4's fix). Measure the stalled-mound scene before and after.
- **From:** G-3.

## Grade B

### R5-9 (B, likely): three "the termites are stuck" judges all fire long before a fresh layer of soil has set

**In play (2026-11-27):** B. Termites building on the mound's face from outside at night as a matter of course, and a mound whose plan runs ahead of what is built.

- **What is wrong:** fresh soil takes about 23 minutes on average to set (R5-3). Three judges of "stuck" are far
  shorter and none knows a cell is only waiting for the soil under it: after 1 minute with nothing built the builders
  may build from outside (`Mound.mayUseFallbacks`, which its comment says "the plan never needs"); after 5 minutes the
  stage moves on unfinished (`getStage`); and near a player a builder-number pass that builds nothing moves the stage
  on at once (`MoundAwayBuilding.buildMound`, `stalled = true`). So stages and fallbacks keep pace with drying, not
  with being stuck. Each stage change also crumbles the spire pieces the new stage has no cell for.
- **Where:** `savanna/colony/Mound.java:66-78`, `:345-369`, `:392-402`, `:756-759`;
  `savanna/colony/MoundAwayBuilding.java:87-95`; `framework/colony/BuildLife.java:115-132`.
- **Unchecked link:** how often a whole stage has nothing to build, carve or clear for a minute (carving does not
  wait for setting and feeds the clocks). The game tests switch the near builder numbers off.
- **The fix proposed:** fewer rules: "stuck" means "a job is left that nobody can reach" (the mound's one "no way
  there" memory of R5-4), never "nothing was built lately". One judge for the real builders and the numbers; the
  numbers' `stalled` flag goes.
- **From:** M-2.

**Your call (2026-11-27):** should a mound's stage wait for its soil to set before the next stage starts? Today the stage moves on after 1 and 5 idle minutes whether or not anything is stuck, so drying soil sets the pace and termites build from outside most nights. My pick: yes, the stage waits (your own rule that soil must set first), and building from outside comes only when a spot is truly cut off; a watched mound's stages may then move on more slowly.

### R5-10 (B, to measure): the whole stage's plan is walked over and over, by the build spots, the "does the mound want soil" check and the shaping picks

**In play (2026-11-27):** C. Part of the tick time beside a big mound with many real workers, most by day. Not measured.

- **What is wrong:** `getBuildSpots` walks every cell of the stage once a tick and again after every layer.
  `wantsSoil` counts every build spot workable now, by day checking each spot's 26 neighbours, and each check works
  the stage out again through `getParts`; it is asked by every idle digger about once a second and on every bite.
  `pickShapeJob` walks the whole plan for each builder's look with no memory that there was nothing to shape.
- **Where:** `savanna/colony/Mound.java:578-603`, `:683-698`, `:706-732`, `:799-810`;
  `savanna/colony/MoundShaping.java:48-117`; `savanna/colony/MoundReach.java:77-81`.
- **The fix proposed:** one list a tick: the spots workable now worked out together with `getBuildSpots`, the same
  for shaping jobs (it is R5-4's fix too), and the stage looked up once a walk, not once a cell. Measure first at the
  top stage with the real cap of workers; if the walks cost well under 0.5 ms a tick, leave them.
- **From:** M-3, G-4.

### R5-11 (B, likely): the mound's growth rate was set for 60 workers; with today's 200 a full mound takes about a third of the 20 hours asked for

- **What is wrong:** a colony earns 2 blocks a day plus 0.25 for each worker. `docs/HISTORY.md:1359` set 0.25 for "a
  full mound in about 20 hours with 60 workers" (17 blocks a day). The honest sizes of 2026-11-11 raised the cap to
  200 workers and did not touch the mound numbers: 52 blocks a day, the full mound in about 20 game days, some 6.5
  hours, once the colony is full.
- **Where:** `savanna/colony/Mound.java:306-319`; `macrotermes.json` (`blocks_per_day_per_worker`, `max_workers`).
- **Unchecked link:** the soil count of a full mound (about 1,050 blocks by `docs/HISTORY.md`; not counted here).
- **From:** G-5, M's balance note.

**Your call (2026-11-27):** how long should a full colony take to raise a full mound? Today a colony of 200 workers earns 52 blocks a day, so the full mound stands after about 6.5 hours of play. My pick: 0.075 blocks a day for each worker (17 a day at 200 workers, the 20 hours you asked for; a young colony of 20 builds 3.5 a day), which keeps "weak early, growing over many hours".

### R5-12 (B, the path proven): reshaping a soil cell with a termite in it leaves a tunnel block where the plan says soil, for good

**In play (2026-11-27):** C. Rarely, a stray channel through the mound; a termite crawling in it by day may be sent home as if it were outside.

- **What is wrong:** a tunnel block is put in so the termite keeps a channel; the plan still says soil, no job is
  found for a tunnel block in a soil cell, and the cell is not in the nest's list of channels.
- **Where:** `savanna/colony/MoundShaping.java:152-155`; `savanna/colony/Mound.java:428-440`, `:519`.
- **The fix proposed:** the reshaping waits while a termite is in a cell the plan has as soil, as it does for a
  player or an animal. One branch less.
- **From:** G-6.

### R5-13 (B, proven): the proof and the game judge "what job is here" in two places that already differ

- **What is wrong:** the pretend mound's rules (`MoundGrowth.java:419-460`) and the game's (`Mound.java:512-525`,
  `:607-658`) are written apart "by the same rules". They differ already: a tunnel's door into the shaft, wet and
  crust cells, a cell holding water, and the order jobs are picked in (R5-4). The "never gets stuck" promise rests on
  the two staying the same, and `MoundGrowthTest` checks only the pretend mound.
- **The fix proposed:** a test that builds a stage in the game by `Mound.jobAt` in a random order of the jobs it may
  do now and compares with the plan; or one job rule both call behind a small "what stands here" question.
- **From:** G-7.

### R5-14 (B, proven): the colony's food count and the comb in the world are two truths that drift apart, and nothing brings them back together

**In play (2026-11-27):** B. After a player digs into a garden the colony list keeps showing comb that is gone, and the nest unlocks levels from food it no longer has; away, workers can starve beside old comb.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: while the whole nest is loaded the blocks are the truth: once a second (`FoodStore.watchRoom`) the count is set to the layers on the store's heaps plus what the ledger still owes them (`storedLayers`); a store with no cell counted (its shaft or way in shut for now) keeps its count. A player breaking or putting comb, a comb losing its floor, now reach the count within a second. No test of its own. Left: away the numbers still eat only while both the count and the seen comb say yes.**

- **What is wrong:** the food store's count changes only by the colony's own doings. A player breaking comb, a comb
  losing its floor, or a player placing a fungus comb item (an uncounted ripe layer that grows nodules) never reaches
  it. Away, the numbers eat only while both the count and the seen comb say yes.
- **Where:** `framework/colony/FoodStore.java:79-80`, `:123-126`, `:1014-1053`; `framework/colony/WorkerFood.java:127`;
  `savanna/colony/FungusGarden.java:144-161`.
- **The fix proposed:** while the nest is loaded the blocks are the truth: the garden already counts every comb layer
  once a second; set the count to that plus what the ledger still owes.
- **From:** F-3.

### R5-15 (B, the path proven; lag to measure; a new side of R1-17): the comb's catch-up on return can do three times the ledger's 40 block changes a second, each try walking every comb with a sight check

**In play (2026-11-27):** C. A small stutter on coming back to a big mound after a long time away. Not measured.

- **What is wrong:** the garden's share (`FungusGarden.age`) is handed what is left of the budget, does up to that
  many ripenings and that many ageings, and its changes are never taken off the budget; each try walks every comb and
  runs a sight ray from each player's eyes. Worst case about 120 changes and some 4,300 rays in one tick.
- **Where:** `savanna/colony/FungusGarden.java:100-141`; `framework/colony/NestLedger.java:100-127`.
- **The fix proposed:** pick the out-of-sight combs once a call and take the garden's changes off the same budget.
- **From:** F-4.

### R5-16 (B, proven): the queen does not swell while her colony is away, so a colony that grows away keeps a small, slow-laying queen

**In play (2026-11-27):** C. A colony founded and grown far from the player keeps the founding queen's 10 eggs a day instead of reaching 80; far colonies grow more slowly than watched ones, by days.

- **What is wrong:** the swelling lives in her body's tick (`TermiteQueenEntity.java:176-183`). Away, the brain lays
  at the interval for the size it saw when the colony left (`NestLife.java:72`).
- **The fix proposed:** move the swelling rule into the colony, ticked here and away; the body only draws the number.
- **From:** F-5.

### R5-17 (B, real biology): the foragers lay the comb themselves; real comb is built by the young workers from their droppings

- **What is wrong:** a forager carries its piece to the garden and lays it as a comb layer
  (`framework/entity/goal/StoreFoodGoal.java:86`). In real Macrotermes, and in `docs/DESIGN.md:51` ("the young workers
  make comb from it"), old workers bring the plant matter in, young workers eat it, pass it quickly through the gut
  with the fungus's spores, and lay it as fresh comb.
- **The fix proposed (only if wanted):** foragers drop the forage at the garden room's doorway, and a nurse-age
  worker eats a piece and lays it as a fresh layer. One hand-off, no new number; away the numbers stay as they are.
- **From:** F-7.

**Your call (2026-11-27):** who should build the fungus comb? Today the old foragers walk down into the garden rooms and lay the comb themselves, and the young nurses never build it. My pick: the young workers lay it, as the design says and as real Macrotermes do; it is a real detail the player can watch.

### R5-18 (B, proven): a termite king outlives his colony for ever

**In play (2026-11-27):** C. A live king in the royal chamber of a mound whose colony has died, for good; if a queen with no king is ever within 32 blocks he walks over and joins her.

- **The lead's grade: B, not the reader's A.** It is the king's side of R6-2, but unlike a Matabele queen he holds
  no mound: the colony ends and its dead mound is free. The player meets him only by digging into a dead mound.
  Checked by the lead: `KingCare.updateClock` (`savanna/colony/KingCare.java:101-105`) asks only
  `QueenCare.royalsGetHungry`, not the "spent" rule the queen's own clock uses
  (`framework/colony/QueenCare.java:454-464`); `Colony.isEmpty` counts no co-founder (`Colony.java:731-734`), so the
  colony ends with him alive; he never despawns and with no colony never starves (`TermiteKingEntity.java:133-169`).
- **The fix proposed (my pick, as R6-2's call was made):** one judge of "are the royals hungry now" in `QueenCare`
  that `KingCare` asks; and a king whose colony is gone dies with it (real royals cannot feed themselves). A king
  from a spawn egg who never had a colony keeps the join.
- **From:** F-2.

### R5-19 (B, proven): dead mounds count their days on the day clock, everything else on game time, so `/time set day` stops every dead mound from rotting and wearing

**In play (2026-11-27):** B. After one `/time set day` in a world 60 days old, no dead mound wears and no dead comb rots for 60 days of play; with the daylight cycle off they never do.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: a dead mound keeps its game time of death beside the day clock (`DeadMound.diedAtGame`, saved; a mound saved before works its own out at its first look) and its age is the larger of the two, so `/time add` still moves the wear on and `/time set day` no longer stops it. No test of its own. Left: the trees count their days the same way (`tree/Forest.java`), not changed here.**

- **Checked by the lead with Minecraft's code:** `TimeCommand.setTime` (`:64-71`) sets every level's day time to the
  number given, so `/time set day` puts the clock back to 1000. `DeadMounds.days` (`savanna/colony/DeadMounds.java:98-100`)
  then reads 0 for every mound that died later (`:184`, `:279`).
- **The fix proposed (my pick):** keep the game time of death beside the day clock and use the larger age: `/time add`
  still moves the wear on (it is used to watch it), and `/time set` back no longer stops it. The trees count their
  days the same way (`tree/Forest.java:361`, `:670`); the same fix fits there.
- **From:** D-3, X-5, Y-6.

### R5-20 (B, proven): when a termite colony dies, the bare earth round its holes stays bare for good

**In play (2026-11-27):** B. Round every mound that died in play, little patches of coarse dirt round the old foraging holes never green over, while the holes themselves and the mound beside them grass over.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: the bare patches hear the colony's end (after the galleries) and hand the ground they made bare to the dead mound's works (`BarePatches.colonyEnded`, `DeadMounds.addBare`, `DeadWorks.bare`, saved with the bare block's name), which turn it back to grass on the day its holes grass over (`DeadWorks.greenOver`); at the cap of 4,096 tufts the oldest is un-bared before it is dropped. No test of its own.**

- **What is wrong:** the bare patches are a part of the living colony with no "colony ended" handler, and the dead
  mound's works do not take them over; Minecraft grass does not spread onto coarse dirt. A rarer way to the same end:
  at 4,096 remembered tufts the oldest is dropped without growing back.
- **Where:** `savanna/colony/BarePatches.java:65-91`, `:157-165`; `savanna/colony/SavannaParts.java:275-280`;
  `savanna/colony/DeadWorks.java:80-132`.
- **The fix proposed:** the bare patches hear `COLONY_ENDED` and hand their spots to the dead mound's works as one
  more list that grows back on a day; at the cap, un-bare the oldest before dropping it.
- **From:** D-4.

### R5-21 (B, likely): the nest of a mound of new land is dug through water and lava, which the termites themselves never do

**In play (2026-11-27):** B. Now and then a mound of new land whose rooms are flooded (a river bank, a buried pool, a cave), or lava running into a nest.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: before anything is built, a mound of new land checks every cell of its nest's shaft and rooms with the framework's one judge (`Nest.touchesFluid`, now public); a nest that would touch water or lava (or land not loaded) skips the place like a place on water, and the colony made for it is taken away (`NewLandMounds.raise` returns false, `wet`). No test of its own.**

- **What is wrong:** `digNest` cuts every cell "through anything but bedrock" with full updates, so water or lava
  beside a cell flows in; the framework's one judge (`Nest.touchesFluid`) is not asked, and the place check looks only
  at the middle cell and the one under it. `clearForMound` likewise clears every block in the plan's cells.
- **Where:** `savanna/colony/NewLandMounds.java:280-288`, `:508-545`.
- **Unchecked link:** how deep the nest goes and so how often it meets water in real savanna land.
- **The fix proposed:** a place whose nest would touch water or lava is skipped like a place on water. A test: a
  mound put beside a buried pool leaves the pool where it was.
- **From:** D-5, Y-8.

### R5-22 (B, balance and realism): the dead mounds near the player all turn into humps within about a month of play, while new land keeps showing standing ones

- **What is wrong (two numbers that were never set against each other):** new land places dead mounds mostly freshly
  dead (nine in ten under 12 days), "so about 1 mound in 3 reads as dead". The wear then runs on at the sped-up rate
  (spires gone in about 5 days, a 4-block hump by day 26 to 35, `MoundWear.java:24-66`). Nothing makes new dead mounds
  near the player, so after about 30 game days every dead mound where the player lives is a low hump, and a Matabele
  colony in a dead mound sees its home slump within a month (BUGS 358 is one face of this).
- **Real:** a Macrotermes mound outlives its colony by years to decades; Megaponera favour standing dead mounds.
- **From:** D-6.

**Your call (2026-11-27):** how fast should a dead mound slump? Today spires go in about 5 days and the whole mound is a 4-block grassy hump after about a month of play, so the land you live in ends up with only humps. My pick: spires and comb as today, but the body's slump about ten times slower, so a standing dead cathedral stays a landmark and a Matabele home for a long game, as real ones do for years.

### R5-23 (B, the path proven; the look to be seen in play): the wear a dead mound owes from a long absence is paid in front of the returning player, 256 blocks at a stroke, each with its breaking sound

**In play (2026-11-27):** B. Coming back to a mound that died long ago: it stands as it was left, then drops a slice every 5 seconds, top first, with a burst of breaking sounds, until weeks of wear are done in a minute or two.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: a dead mound more than a day behind its day (`DeadMounds.CATCH_UP_DAYS`) is catching up: it is worn the quiet way (no breaking sounds or crumbs, as new land's dead mounds are) and every tick until it has caught up, so weeks of wear go in a second or two without a burst of sounds; only the wear of the last day is seen as it happens. Not looked at in play.**

- **What is wrong:** the catch-up has no rule about being seen and breaks each block the loud way. The living
  colony's catch-up works only out of sight, and new land's dead mounds already have the quiet way (written because
  "thousands at once flooded the sound player"). All dead mounds share the one budget, in the book's order.
- **Where:** `savanna/colony/DeadMounds.java:63-69`, `:245-259`, `:274-300`.
- **The fix proposed (my pick):** reuse what is there: wear that is more than a day behind is catch-up, done the
  quiet way; what the player watches is only today's wear, a few blocks with their sound. To see it today: stand by
  a dead mound a few days old and type `/time add 240000`.
- **From:** X-4, Y-4.

### R5-24 (B, proven for the manager's path; the termite side of R1-27): a colony that dies out while away leaves no dead mound until a player comes near, and counts as living until then

**In play (2026-11-27):** B. A mound whose colony has been dead for 100 days stands fresh and only begins to wear on arrival; until then Matabele scouts walk out to a mound with nobody in it.

**Status (2026-11-27, `claude/review-fix-b8`): fixed (also R1-27's away half and R5-50): the manager ends a colony away whose queen is dead or starved and whose numbers hold nobody (`Colony.diedOutAway`, `ColonyManager.tickColonies`), and keeps its id (`endedAway`, saved); an insect of such a colony frozen in land not loaded leaves the world when it loads (`AwayLife.backFromFrozen`), and a queen or king dies there (`AbstractAntEntity.backFromFrozen`; a dying queen founds no new colony). The garden's comb in land not loaded is written down unread and read when it loads (`FungusGarden.combsAtEnd`, `DeadMounds.UNREAD`). No test of its own.**

- **What is wrong:** a colony is ended (its dead mound written down) only from the near tick or by its queen's body
  dying; away, the manager only runs the numbers (`framework/colony/ColonyManager.java:95-128`). With R5-2, far
  colonies dying out is not rare.
- **The fix proposed:** R1-27's: the away tick ends a colony whose numbers have reached nobody, with the game time it
  happened. The termites' parts are ready for it (`DeadMounds.addMound` copes with land not loaded), but two raw block
  reads then need a guard (R5-50).
- **From:** X-6.

### R5-25 (B, proven; how often not measured): the soil that washes out round a dead mound can appear inside a standing ant or animal, because it is the one placement that never looks who is in the cell

**In play (2026-11-27):** C. Rarely, an ant or small animal standing at a dead mound's foot is suddenly inside a block of soil and takes "stuck in a wall" damage; the likeliest victims are Matabele ants living in a mound that died less than about 14 days before.

- **The lead's grade: B, not the reader's A.** Checked by the lead: the write at
  `savanna/colony/DeadMounds.java:452` follows four checks of the cell and the block under it and none of who is in
  it. It holds; but each of the ring's about 200 cells gets its block once, in one look every 5 seconds, so a creature
  must stand in that cell at that moment.
- **The fix proposed:** the rule the neighbours use: if somebody is in the cell, leave the column for the next look.
- **From:** Y-1.

### R5-26 (B, proven): "may the colony change this block?" is answered in about a dozen places, each a little differently

- **What is wrong:** every block-changing place has its own set of checks (reader Y's table lists all 50). They
  differ on five questions: is the cell free (for a wall cell with water in it, real workers pack soil into it, the
  away builders never do, and the stage is called finished with the water still there); is somebody in it (R5-25);
  is the land there, and the land beside it (R5-6); whose block is it (R5-27, R5-21); and the game rule `mobGriefing`,
  which the animals ask and the colony code nowhere. R5-6, R5-21 and R5-25 are each "one place did not ask one of the
  questions", and nuptial flights, floods, seasons and later species will add more such places.
- **Where:** `savanna/colony/Mound.java:514-519`, `:564-570`, `:612-648`, `:765-794`;
  `savanna/block/TermiteMoundBlock.java:128-130`; `savanna/colony/MoundAwayBuilding.java:108-124`;
  `savanna/colony/DeadMounds.java:450`; `savanna/colony/NewLandMounds.java:508-545`; `framework/colony/LandReady.java:44-60`.
- **The fix proposed:** one shared question in the framework, beside `LandReady` and `BuildLife.freeAndHidden`, that
  answers all five in one place and that every row of the table calls. It removes checks and adds none. My picks for
  the two answers it needs: water and lava are filled with soil everywhere (it is what the player already sees near a
  mound, and it lets a colony mend a mound someone poured water on); and the colony's own building and wear keep
  ignoring `mobGriefing` (servers switch it off to stop creepers, not mounds), said out loud in `docs/DESIGN.md`.
- **From:** Y-3.

### R5-27 (B, likely): a mound block has no owner, so a second colony, a dead mound's wear and a player's own mound blocks all read it as "mine"

**In play (2026-11-27):** C. Today only with a spawn egg: a queen put beside a living mound, or on a mound that died a week ago, and the two fight over the same cells, block out, block in; on the dead mound the new colony's first comb turns black.

- **What is wrong:** every rule that asks "is this the mound's?" asks only what kind of block it is
  (`Mound.isMoundBlock`, `savanna/colony/Mound.java:914-917`). Two living colonies close together undo each other
  (`:519`, `:865`); a dead mound's book wears and rots whatever stands at its remembered places, a living colony's
  blocks and comb included (`savanna/colony/DeadMounds.java:368-381`, `:422-428`).
- **Unchecked link:** the framework's founding and nest site rules were not read; new land keeps mounds 40 apart.
- **The fix proposed:** one answer to "whose block?" inside R5-26's question: a colony changes a mound block only
  inside its own plan and where no other living colony's plan holds the cell; a dead mound's book gives up a place
  the moment a living colony covers it. Whether a new colony may found on a dead mound at all (real Macrotermes do
  take old mounds over) is a founding decision for M9.5, not for this round.
- **From:** Y-5.

### R5-28 (B, proven): what the player sees and no test checks

- Forage arriving while away after the numbers have eaten, and a colony away with no comb yet (R5-2).
- A far colony asked for its stage or cells with its plan not kept, and its stage in land not loaded (R5-1).
- Time passing with nobody near, or a spectator near, and the soil's age (R5-3).
- A builder killed or unloaded with its window open (R5-5); a stage whose lowest job cannot be reached (R5-4).
- A dead mound wearing with part of it in land not loaded (R5-6); a long-dead mound loaded beside a watcher (R5-23).
- The put of a full living mound and of an old hump against the tick's budget (R5-7).
- A colony ending with its king alive (R5-18); the bare patches after the colony dies (R5-20); a new-land nest
  beside water (R5-21); `/time set` back and dead mounds (R5-19).
- Comb broken by a player against the food count (R5-14); the queen's size after a long time away (R5-16).
- The saved soil counts `moundWalls` and `moundReach` have no save-and-load check (`AwayKit.everyPartSavesAndLoads`
  fills the mound's other numbers, not these two, which the 2026-11-10 fix leans on).
- The mushrooms test sets the colony's own rain, so it would still pass if the world's rain never reached the mound.
- **From:** F-8, D's and X's lists, G-2, G-7.

## Grade C

### R5-29 (C, proven; the lead's grade, the reader's was B): the mushrooms go by a second judge of rain, come up only for a colony that was near when the rain stopped, and have no socket for the season

- `Mushrooms.tick` reads the world's weather (`savanna/colony/Mushrooms.java:57-70`); the colony's own judge
  (`Colony.rainsNow`) also asks whether the biome has rain, and the savanna has none (the mod's floodplain file says
  so; vanilla savanna from memory). So mushrooms come up "after the rain" on a dry day. `wasRaining` is not saved and
  the part ticks only near, so a player who arrives a minute after the rain gets no flush.
- **My pick (the lead's):** keep the world's weather as the savanna's rain until seasons come: it is the only rain
  signal a savanna has, and the termites' foraging "in the rain" goes by it too. Put it behind one shared "did it
  rain here, and when did it stop" question that later asks the season source (`Seasons.now`), and flush on arrival
  within about a day of the rain. Nothing changes in play today but the late arrival.
- **From:** F-6, F-10, X-9.

### R5-30 (C, realism): the mound's building pace ignores rain and the season, and leaves no socket for the season source

- The colony earns blocks at one steady rate (`savanna/colony/Mound.java:301-319`). Real Macrotermes build mostly
  when the soil is wet, after rains and in the rainy season (the direction is sure, no number is). **Fix:** route the
  pace through one place that later asks the shared season and weather source (a factor of 1 today), so seasons need
  no mound edit. Nothing changes in play now.
- **From:** M-4.

### R5-31 (C, proven): the saved soil counts are trusted for good, even after the mound's plan has changed

- After a mod update that changes the plan (it has happened: BUGS 82), an old colony keeps its old counts: stages
  earned at the old thresholds, cells from the new plan (`savanna/colony/Mound.java:215-235`, `:1023-1043`).
  **Fix:** prefer the grown counts whenever they are known and drop the saved ones then.
- **From:** M-5.

### R5-32 (C, proven): after `/reload` with a new `max_height`, a watched mound keeps its old plan

- The held plan is never compared with the numbers it was made for (`savanna/colony/Mound.java:278-299`,
  `:465-469`). Only the owner tuning the species file beside a mound meets it. **Fix:** keep the plan under its
  numbers and let it go when they differ.
- **From:** M-6.

### R5-33 (C, likely): a mound plan that fails to grow is retried every second with no log line, and the plan stores are never cleared between worlds

- `MoundPlan.grownSoon` drops a failed background job without a word (`savanna/colony/MoundPlan.java:213-230`); a
  tick waiting on that job would throw inside the colony's tick. No path that throws is known today. The four static
  stores of `MoundPlan` (24 kept plans, "some 2 MB" each) and the dead tree shapes of places that were skipped
  (`NewLandMounds.java:198`, `:765`, `:870-877`) stay in memory after the player leaves a world.
  **Fix:** log a failure once and remember it; clear the stores when the server stops; drop a skipped place's shapes.
- **From:** M-7, X-10, D-8.

### R5-34 (C, likely; a new side of R1-4): `max_height` 0 in the species file crashes the colony's tick, and a species with a garden part and no garden numbers crashes the colony list

- The mound numbers have no range check (`savanna/species/SavannaNumbers.java:165-172`; `Mound.java:256`, `:271`);
  the exact place it breaks was not walked. `FungusGarden.describeMushrooms:83` calls `.get()` on a section that may
  be missing. **Fix:** the range check R1-4 proposes, failing the file load with a message.
- **From:** M-8, F-12.

### R5-35 (C, proven): comments and notes that disagree with the code

- `TermiteMoundBlock.java:120-131` (the comment of `addLayer` sits over `isRoomForSoil`); `Mound.java:73-77` ("the
  plan never needs the fallbacks", see R5-9); `MoundPlan.java:101-104` ("a mound every 80 blocks"; places are 42 to
  82 apart and about 1 in 3 holds one).
- `BuildMoundGoal.java:37-49`: a look comes about once a second and the pellet is dropped after about 30 s, not
  "every half second, about 15 seconds", and it does not keep its pellet "until a spot is free".
- `FungusGarden.java:164`, `Mushrooms.java:56`, `TermiteQueenEntity.java:43`: the garden and mushrooms are ticked by
  the colony, not by the queen. `KingCare.java:40`: nothing reads an old body tag.
- `MoundShaping.java:27-32`, `:45-46` promise pieces "cleared away" that are cleared elsewhere (R5-36).
- `docs/DESIGN.md:167` gives new land's dead mounds as 25, 55, 12, 8; the file and the code say 30, 60, 6, 4. The
  code's default `dead_share` is 0.75 against the file's 0.55 (`TermiteMoundStructure.java:79`, `:91`).
- **From:** M-9, G-9, F-11, D-12, G-8.

### R5-36 (C, proven): the "clear a piece" branches of shaping can never run

- `Mound.jobAt` gives "clear" only for shaft cells and `shapeJobAt` drops shaft cells, so the last branch of
  `MoundShaping.shape` (`:168-170`) and the matching one in `MoundAwayBuilding` (`:154-156`) are dead; they read as a
  second place that clears the shaft. **Fix:** remove them.
- **From:** G-8, Y-8.

### R5-37 (C, proven): log lines at INFO level in normal play

- `BuildMoundGoal.java:174-175` (every give-up) and `:215-216` (every walk built from outside or far): on a stalled
  mound a line per carrier per walk. **Fix:** DEBUG, as line 224.
- **From:** G-10.

### R5-38 (C, proven; looks only): reshaping "through a window" never bites one

- `ShapeMoundGoal` reshapes from a channel by the window rule but never opens a window, unlike `BuildMoundGoal`; the
  design's look (bite a hole, shape the ramp beside it, plug it) shows only for soil. **Fix:** if wanted, with the
  mound-owned windows of R5-5.
- **From:** G-11.

### R5-39 (C, likely): a termite in a spire's channel is buried when the piece is reshaped into a solid tip

- `MoundShaping.shape` (`:161-166`) does not lift an insect that is in a channel, on the idea that the new piece
  keeps one; a spire tip has none. Needs a spire whose top moves, so rare. **Fix:** "the new piece has no channel"
  counts as "lift it".
- **From:** G-12.

### R5-40 (C, proven): small block leftovers

- Any player's shovel opens or closes a tunnel window, "for testing by hand", also in survival
  (`TermiteTunnelBlock.java:225-243`). **Fix:** creative only, or remove.
- The tunnel block has no `rotate`/`mirror` for its six open sides (harmless while mounds are put up by code).
- The mound blocks' loot files have no "survives explosion" condition (the hole's has), and the blocks can be pushed
  by pistons; the plan simply builds the cells again. What an explosion really drops was not checked.
- The dead comb "drops nothing" only because its loot file is missing; dug out from under, it hangs in the air (the
  living comb falls).
- **From:** G-13, Y-8, F-12.

### R5-41 (C, to measure): every nurse's "is there food" rebuilds the garden's cell list and reads every comb, and looks for the king by a scan for entities

- `FungusGarden.java:144-152`, `:401-441`; `framework/colony/Nest.java:465-475`, `:804-828`;
  `framework/entity/goal/FeedLarvaGoal.java:87-125`; `framework/colony/Colony.java:615-626`. Once a second per nurse.
  **Fix:** the garden keeps the comb cells it found this second; `KingCare` remembers the king's id.
- **From:** F-9.

### R5-42 (C, proven): small things in the garden

- `GardenAway`'s four running fractions are not saved: under one layer or nodule of each lost a reload.
- Near, a comb under an egg or nymph does not crumble; away every old layer can.
- A comb of 4 layers drops one fungus comb item; placed, it is one ripe layer that grows nodules uncounted (R5-14).
- **From:** F-12, X-10.

### R5-43 (C, to measure; a new side of R6-28): a settled dead mound keeps every column of its wear for good, and the whole book is written at every save while any mound changes

- About 650 columns of 7 numbers each a mound, read by nothing once the mound has settled
  (`savanna/colony/DeadMounds.java:128-170`, `:474-478`, `:648-718`). A rough size: about 0.3 MB per square kilometre
  of savanna explored, before compression. **Fix:** when a mound settles, drop its columns and pieces.
- **From:** D-7.

### R5-44 (C, proven): the "already put in" check of new land can never be reached

- `put` tests "another colony within 40" first, and a mound already put in for this place stands at the same middle,
  so that answers first; the log and the skipped count say the wrong reason (`NewLandMounds.java:284-292`).
  **Fix:** test "already put in" first, or drop it.
- **From:** D-9.

### R5-45 (C, proven): a dead colony of new land is given its days since death as its age

- `NewLandMounds.java:479-486`. Harmless today (the colony ends in the next line). **Fix:** the age its stage gives,
  or a comment.
- **From:** D-10.

### R5-46 (C, likely): the dead works' list of "the nest, never filled" leaves out the side rooms

- `DeadWorks.record` (`savanna/colony/DeadWorks.java:80-95`) builds its own list of the nest's cells, a second judge
  beside the framework's (`Nest.java:870-898`), without the side rooms. If a gallery cell ever lies in a side room it
  would be filled with soil in a mound the Matabele live in; whether one can was not traced. **Fix:** take the
  layout's own cell set.
- **From:** D-11.

### R5-47 (C, proven): the new-land tests set a static fence and read the world's "last" things, the leak BUGS 363 traced a flake to

**In play (2026-11-27):** flake. A failed check leaves `NewLandMounds.fence` set, and a test beside it in the same batch gets its dead tree turned down.

- `TermiteNewLandTests.java:48-120`, `IdleTests.java:89-92`; the book's `lastDeadTree`, `lastAnts`, `lastBurrow`,
  `lastTrees`, `learntWood`, `lastMillis`. **Fix:** clear the fence in a `finally` or key it to the test's own box;
  give the tests their own colony's facts.
- **From:** D-13.

### R5-48 (C, proven): the mound's allowance for a whole absence is paid at the head count on the day of return

- Away nothing asks `getAllowance`, so the whole absence is one sum at the worker count of the moment it is next
  asked (`savanna/colony/Mound.java:306-319`); the builders' time is counted second by second. Bounded, so small.
  **Fix:** the mound's away tick also asks it, after R5-1's fix.
- **From:** X-7.

### R5-49 (C, to measure): the bare patches catch up all in one tick on return

- After a long absence every tuft whose time has come (up to 4,096) grows back in the first look, each with two
  block changes, an entity search and a walk over the runways, in view (`savanna/colony/BarePatches.java:109-154`).
  **Fix:** a budget per look, the rest next look.
- **From:** X-8.

### R5-50 (C, proven): two block reads with no loaded-land check, reachable today only by the kill command

**Status (2026-11-27, `claude/review-fix-b8`): fixed with R5-24: at a colony's end the garden passes its cells in land not loaded unread (`FungusGarden.combsAtEnd`) and `DeadMounds.addCombs` reads only loaded ones; the rest are read when their land loads.**

- `FungusGarden.getCombs` (`:144-152`) and `DeadMounds.addCombs` (`savanna/colony/DeadMounds.java:233-242`) read
  blocks when a colony ends. Every real ending happens near, where the garden is loaded; `/antcolonies kill` on a far
  colony makes Minecraft load the land. With R5-24's fix this becomes a real path and needs the guard.
- **From:** X-10, Y-3.

### R5-51 (C, the paths proven; no change proposed): three places where the mound meets the player's own digging and building

- Fifteen to 45 days after a colony dies each remembered gallery cell that is air is filled with soil, once: a cellar
  a player dug along the old galleries gets soil in those cells (`DeadWorks.fillIn`, `:264-273`). Real galleries do
  silt up.
- A player's block in the mound's shaft is never removed (right for a termite).
- A broken mound block is built again, so a mound is a slow, endless source of its own block; the colony pays in
  work. The lead's pick for all three: leave them; listed so nobody is surprised.
- **From:** Y-7.

## What is solid (so no later session "fixes" what works)

The readers' files have the full lists with lines; the main points:

- **The plan is pure and the same every time** (`MoundPlan`): it takes only the colony's seed and `max_height`, never
  touches the world, and is safe to grow on background threads. The stage itself needs no plan (the 2026-11-10 fix
  holds for the plain stage and foot reach; R5-1 is the side door).
- **The building rules and their proof** (`MoundGrowth`): every stage is grown in a pretend mound with the termites'
  own rules, the loop always ends, and the window and reach rules are one rule shared with the game.
- **Nobody is buried by the living mound:** adding a layer, reshaping, the away builders, the dead works and the bare
  patches all look who is in the cell first (R5-25 and R5-39 are the two exceptions).
- **The living mound does not destroy a player's build:** soil goes only into replaceable cells; only the mound's
  own blocks are reshaped or crumbled; the dead wear keeps the whole column under a player's block.
- **The away builder cannot build a block twice or miss one for good:** it reads the world for every cell, is
  bounded (200 blocks a second, out of sight), and waits for land that is not loaded.
- **Claims run out by themselves** (600 ticks), are pruned on each pick, and losing them on a reload costs nothing.
- **Day and night:** windows only at foraging time, by day only work reachable from the shaft, as real Macrotermes
  build mostly at night and after rain, from inside, with soil pellets.
- **The comb's conveyor near a player** is right in every state, and the near and away rates use the same numbers
  and game-time clocks. The R6-2 fault does not hit the termite queen (only the king, R5-18).
- **The hand-over from world making** to `NewLandMounds`: world making places no block of the mound, only a
  concurrent queue is touched from its threads, one mound a look, no mound is put in twice after a reload.
- **`MoundWear` is pure numbers,** the same after a reload; the dead mounds' book saves everything, only moves
  forward and has a hard budget a look.
- **Dice:** no `level.getRandom()` behind a colony's choice was found in this half; new land rolls its place's seed.
- **More than one player:** nothing on the away thread asks for "the player".
- **Layering:** both building goals only name a foothold test and a cell to the legs.

## Real biology, checked (no finding)

- **Scale:** at the mod's insect scale a 32-block mound is about 1 m tall; real mature mounds are 2 to 3 m
  (M. michaelseni) and up to 6 to 9 m (M. bellicosus). A young real mound at insect scale; nothing to change.
- **Shape:** closed at the top with a hollow shaft and buttresses is true to M. michaelseni and M. bellicosus; the
  open chimney mouths are closer to M. subhyalinus and M. jeanneli. The owner chose them.
- **Numbers no paper was checked for:** the queen's laying (thousands of eggs a day in life; 10 to 80 here, scaled to
  a colony of 200), comb turnover (weeks in life, days here). Scaled to a 20-minute day on purpose.
- **Late game:** the cap of 200 workers is reached in perhaps 2 to 3 weeks of good foraging; after that nothing
  scales. Not a bug; for the balance work.

# Part B: outside the mound (2026-11-27)

Galleries, foraging holes, hidden trips, feeding, the food blocks, the alarm and the soldiers. Read in `C:\Ant Mod` on
branch `claude/motion-glue` at commit `a7c0a9f0` (the termite code is the same as at part A's commit), by a session
that did not write the code. Rule book: `docs/plans/code-review.md`. No code was changed; no gradle, game, game test or
film was run, so **nothing here is measured**: every lag finding is "to measure" unless the path through the code
proves its cost or an earlier measure is named.

**What was read, every line** (about 10,200 lines): in `savanna/.../colony/`: `GalleryWeb`, `Galleries`,
`GalleryUpkeep`, `GalleryCatchUp`, `SavannaParts`, `HoleTurns`, `ClosedHoles`, `HiddenTrips`, `FeedingGallery`,
`FeedingSpots`, `DeadWoodCall`, `BarePatches`, `Alarm`, `Soldiers`, `SentryLine`, `RaidDefence` (round 11 read it as
raid code; here for where the alarm and the soldiers hand over to it); `SavannaEvents`; in `entity/`:
`TermiteWorkerEntity`, `TermiteSoldierEntity`; in `entity/goal/`: `PlugHoleGoal`, `DefendGoal`, `WalkBeatGoal`,
`RetreatGoal`; in `block/`: `TermiteHoleBlock`, `MudCrustBlock`, `MudCastBlock`, `DryGrassBlock`, `TallDryGrassBlock`,
`FallenBranchBlock`, `PulpHeapBlock`, `DeadWoodBlock`, `DeadTreeWoodBlock`, `SoftDeadWoodBlock`, `BleachedBranchBlock`,
`FrassBlock`; `command/TermiteCommands`; the termite records of `species/SavannaNumbers`; the species file
`macrotermes.json`; and the framework's side as far as each finding needed (`ForagingHoles`, `FieldLife`, `FoodStore`,
`FoodMap`, `BuildLife`, `AwayLife`, `Nest`, `Population`, `DangerMap`, `FleeGoal`, `GoInsideGoal`, `DigGoal`,
`ScoutGoal`, `ClipGrassGoal`, the navigator's hidden-trip part). Each reader's file ends with its exact list and with
what it did not finish.

**Who read it.** Six readers. Four with a slice each: **W** the gallery web and the termites' wiring, **H** the
foraging holes, hidden trips, the worker and the mud, **E** feeding, the food blocks and the numbers (with the
foraging side of the food sums), **S** the alarm and the soldiers (with the soldier's goal table). Two across all the
files on one thread each: **P** one piece of food and one forager through a whole day and night, watched or not; **Q**
trouble at the colony from the first sign to "all quiet", and its seam with the Matabele raids (with the tables "who
has the termite"). Their own files are kept in `docs/reviews/full/round-05b-readers/` (their findings carry the letters
W, H, E, S, P and Q there, with the full walk of every proof). The run was cut once by the usage limit; every reader
had saved its file and carried on from it. The lead session then read the lines of every grade A finding itself (each
says "checked again by the lead"), merged what two readers found from different sides (68 findings in the readers'
files, 46 here), and changed two grades: Q-3 is grade B here (a narrow case, R5-65) and S-5 is part of the grade A
finding R5-55.

**Not finished by the readers** (said in their files): reader Q walked a raid from the scout's find to the regroup,
not the march home, and did not walk a raid cut short (dusk, rain, a reload, the land unloading) or a colony going
away in the middle of a trouble; an alarm during a hidden trip was walked by nobody. The game tests were read by
name and scene, a few line by line.

**Not written again here:** what rounds 1, 3, 6, 8 and 11 and part A already list. Where a finding is a new side of
one of theirs it says so (R1-35, R1-36, R1-38, R2-2, R5-2, R5-3, R5-5, R5-6, R5-8, R5-11, R5-18, R5-19, R5-26, R6-2,
R6-7, R11-17).

**Count:** 46 findings: 8 A, 24 B, 14 C. Five of them wait for the owner's yes (R5-56, R5-62, R5-66, R5-67, R5-87).
With part A the round has 97 findings: 16 A, 44 B, 37 C.

## The short of it (part B)

**The foraging works beside a player in survival or creative who lets the nights pass. It goes wrong on three
clocks the termites do not own:**

1. **The day clock (R5-52).** Termites forage by the time of day and get hungry by game time. A night skipped with
   `/time set day`, or the daylight cycle switched off, is a night (or every night) nobody forages, for every termite
   colony of the world at once. The fix made for a night slept in a bed does not see either.
2. **Random ticks (R5-53, R5-80).** Eaten grass comes back green and only Minecraft's random ticks dry it into food
   again, and Minecraft gives none near a spectator or far from every player (part A's R5-3). So beside a spectator a
   colony eats the grass round its holes once. Mud crust, mud casts, pulp heaps and remains stand still the same way.
3. **Whose eyes count (R5-62).** A plant a player can see is left to the real foragers, but a spectator does not
   count as seeing. Beside a spectator the counted foragers cut the grass in plain view, faster than the real ones
   reach it.

**Watched and unwatched colonies live by different rules.** Real foragers and scouts roam the whole range where the
counted ones keep to the holes open that night (R5-61); a far colony is fed "by the land" with no limit from the land
(R5-73); real holes are opened one at a time at the bottom of the dig list (R5-63) and a far colony's hole blocks
stay as they were left (R5-81); a raid meets up to 12 soldiers unwatched and one to three watched, and the
termites remember the two differently (R5-68).

**Trouble at the colony: every part ends by itself and nothing is walled in, but the parts do not agree.** "Is a foe
near", "is this termite safe", "does the drumming reach" and "is the trouble over" are each judged in three to five
places with different distances and clocks (R5-70). What a player can meet of it: an alarm that never ends while one
enemy insect stays within 4 blocks, and walks along with it (R5-55); workers in a raided feeding gallery that do not
run (R5-56); the minors taking every real soldier place so the majors vanish at a raid (R5-57); soldiers called out of
the numbers that never hear the alarm (R5-67).

**Leads for the open raid bugs** (BUGS 202, 361, 452; the raids themselves were rounds 6 and 11): the colony's by-day
record names where termites *could* feed and the raid stands termites also in cells no raider can get at (R5-64); the
raid asks "do the termites keep off this spot?" and the alarm of the raid's own arrival answers yes (R5-65); a stuck
raider keeps the termites round it shut in for as long as it lives (R5-55); a raid can reach a colony that is still
only numbers (R5-65, narrow).

**The gallery web's planners are sound and pure; one gate is not.** Every gallery block must be dug before any tip
may grow, and a block the diggers will never dig (anything that is not soil) stops the whole web for good (R5-54).
"Is this gallery dug?" reads a piece's whole way from the shaft each time, and three places ask it for every piece or
every hole (R5-59, R5-79, R5-92).

**No part is a candidate for a rewrite.** Two shared things would remove whole families of findings: one small record
per trouble that the alarm keeps and the seal, the flight and the raid's end all ask (R5-70; it mends R5-55, R5-65,
R5-67 and R5-68 together), and one question "may the colony take this plant tonight?" for real and counted foragers
alike (R5-61).

**The foraging sums** (reader E with the file's numbers, reader P along the loop; the worldgen amounts are estimates):
a full colony needs about 50 pieces a day (part A). Beside a survival or creative player its band of grass (about 250
to 300 tuft spots round up to 200 blocks of gallery) gives about 175 to 200 pieces a day once regrowth runs, so grass
alone feeds it for good; but eaten grass is food again after about 6 days, not the 3 part A used. Beside a spectator
nothing regrows: the band's 900 to 1,600 pieces last about 3 to 5 weeks, then the colony lives on fallen branches and
dead wood, or shrinks. Away, the land sets no limit at all. The walking is never the narrow place (about 1,800 pieces
a night could be carried); the plants standing dry, the store's room and regrowth are. A young colony gets going
easily. Hay bales are eaten (the design's "jackpot": a barn or a village's hay within about 50 blocks of a mound);
planks, logs and living trees never are.

## The five that matter most (part B)

- **R5-52:** skip a night by command, or stop the sun, and no termite colony forages.
- **R5-53:** beside a spectator eaten grass never becomes food again (the owner watches in spectator).
- **R5-56 with R5-64 and R5-65:** the termites' side of a raid: workers that do not run, termites stood where no
  raider can reach, and the raid misreading the alarm as "nobody feeds here".
- **R5-55:** an alarm that never ends while an enemy stays close, the termites' side of BUGS 361.
- **R5-57:** no major soldier is left real at night or at a raid that reaches the nest.

## Grade A (part B)

### R5-52 (A, proven; a new side of R1-35): a night skipped with `/time set day`, or a day clock that stands still, takes the termites' foraging, and the fix for the slept night does not see it

**In play (2026-11-27):** A. In a world where the night is skipped by `/time set day` each evening, or the sun is held still with the daylight cycle off, no termite colony ever comes out; they eat their comb, the workers starve after about 3 days without a meal and the queens after 5, near and far alike. A bed, `/time add` and `/tick rate` are fine.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: `FieldLife.forageUnseenTime` and `clockJump`: any jump of the day clock of 10 seconds or more, forward or back, is read as the time of day going forward from the old hour to the new one, and the foraging time between is foraged by the numbers in one pass; while the day clock stands still outside foraging time the numbers forage a night's share of every second. For a foraging time that never began for real the web picks its holes (`Underground.foragingTimeUnseen`, `HoleTurns.unseenRound`; once a game day while the clock stands). Test: `aNightSleptThroughIsForagedByTheTermiteNumbers` now checks the jump's reading (a bed, `/time set day` at night, a running clock) and a clock standing at midday. Left: a clock stopped at night keeps one night's pick of holes for good (the termites forage all the time there, so nothing starves); a rewind of the clock gives the numbers one free night.**

**Checked again by the lead.** Read `FieldLife.nightJumpedOver` (`framework/colony/FieldLife.java:372-384`), its only
caller `tick` (`:104-127`), `Colony.isForagingTime` and `isDay` (`framework/colony/Colony.java:433-470`). Minecraft's
`TimeCommand.setTime` was read for part A (R5-19): it sets the plain number, so "day" is 1000. It holds.

- **What is wrong:** the termites forage by the day clock but get hungry by game time, which never stops or jumps.
  Round 1's fix (R1-35) lets the counted foragers forage a night the player slept through. It sees a jump only when
  the day clock goes **forward** by 200 ticks or more (`jumped < 200 ? 0`). (1) `/time set day` at night sets the day
  clock to 1000; on any day but the world's first that is a jump back by many days, the sum comes out far below zero
  and the answer is "nothing jumped". The rest of that night is gone. (2) With `doDaylightCycle` off the day clock
  never moves: stopped by day it is never night again (the termites forage only while the weather is rain); stopped
  at night the one night's pick of holes is kept for ever (a new pick is made only when a foraging time starts,
  `savanna/colony/HoleTurns.java:100-110`).
- **Where:** as above; `macrotermes.json` (`forage_at`, the starve times).
- **Unchecked link:** how many days the comb carries a full colony before the starving shows.
- **The test would still pass:** `aNightSleptThroughIsForagedByTheTermiteNumbers`
  (`savanna/gametest/TermiteAwayTests.java:273-319`) calls `forageSkippedNight` itself and never moves the day clock;
  it would pass if `nightJumpedOver` always answered 0.
- **The fix proposed:** make the one rule whole, no second rule: any jump of the day clock, forward or back, counts
  as "the time of day went from the old hour to the new one" and the foraging time between the two, going forward (at
  most one night), is foraged by the numbers, as for a bed. When the day clock has not moved for a whole day of game
  time, the colony forages by game time (a night's share of every 24,000 ticks) until it moves again. My pick: both
  count as nights foraged unseen, so a builder's "always noon" world does not starve the termites (they then feed out
  of sight). A test that sets the day clock back and one with the cycle off.
- **From:** P-1.

### R5-53 (A, proven; a new side of R5-3): eaten dry grass grows back green and only dries on Minecraft's random ticks, so near a spectator, and far from every player, a colony's grass never becomes food again

**In play (2026-11-27):** A. Watching in spectator, a colony strips the dry grass round its holes once and then walks past rings of green tufts for good; after about 3 to 5 weeks it lives on what fallen branches and dead wood it reaches, or shrinks. Near a survival player eaten grass is food again after about 6 days.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: `BarePatches` keeps an eaten tuft until it is food again: grown back green after 3 days as before, it dries a step every day and a half by the part's own clock (`DryGrassBlock.STEP_TICKS`, `dryOneStep`), and once dry the colony is told of it (`FoodMap.learn`). So eaten grass is food again 6 days after, whoever watches (my pick, as the finding proposed); near a survival player random ticks may dry it a little sooner. Test: `dryGrassEatenByAHoleLeavesBareGroundAndGrowsBack` walks it round (green, drying, dry, known again) with no random tick. Left: grass the colony never ate still dries on random ticks only, so beside a spectator the world's own green tufts stay green.**

**Checked again by the lead.** Read `DryGrassBlock` (`savanna/block/DryGrassBlock.java:61-100`: drying only in
`randomTick`, 1 time in 27), `BarePatches.tick` (`savanna/colony/BarePatches.java:109-140`: puts back the species
file's `grows_back` state) and `macrotermes.json:84-85` (only `growth: dry` is food) and `:105-111` (grows back
`growth: green` after 72,000 ticks). The random tick rule was read in Minecraft's own code for R5-3. It holds.

- **What is wrong:** a tuft the termites eat is put back green after three days by the bare patches part, on game
  time. From green it becomes food only by drying two steps, and each step happens only on a random tick. So: (1)
  near a spectator every eaten tuft stays green for good; (2) the world's own green and drying tufts (new land puts
  4 tufts in 9 down green or drying) never become food there either; (3) near a survival or creative player one step
  takes about 1.5 game days on average, so **from eaten to food again is about 6 days, not 3** (part A's sums used 3).
- **One more clock in the row** (reader P): a tuft that is dry again is not known to the colony. The food map forgot
  the spot when the plant went (`framework/colony/FoodMap.java:119`), and near a player only a real worker passing
  within 5 blocks outside finds it again (`:151-157`). So "eaten, green, dry, eaten again" hangs on three clocks in a
  row, and no test walks it round once.
- **Where:** as above; `savanna/block/TallDryGrassBlock.java:55-71`; `framework/species/SpeciesProfile.java:619-628`.
- **Unchecked link:** how many tufts a chunk really gets (the sums use an estimate).
- **The fix proposed:** one clock in place of two. The bare patches part already keeps every eaten tuft with its own
  game-time clock; let it keep the tuft until it is dry (green at 3 days, drying and dry at its own later times, in
  loaded land) and tell the food map when it is. Grass the colony never ate keeps drying by random ticks (it is the
  world's). My pick: green after 3 days, dry 3 days later, whoever watches. A test with `randomTickSpeed` 0: eat a
  tuft, push the clock, and the colony eats it again. It is also the socket for the season later (R5-96).
- **From:** E-1, P (its "already written" part), reader F's sums of part A corrected.

### R5-54 (A, the path proven; how often likely): one gallery block the termites cannot dig stops the whole web from growing, for good

**In play (2026-11-27):** B. A player digs into a gallery to watch, leaves a torch or a cobblestone in it and fills the hole; from then on that colony's galleries never spread again, however big it grows, and no piece out to food is planned from the pieces behind that block.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: the gate asks the diggers' own question (`GalleryWeb.diggingLeft`: `Nest.firstDiggable` over the web's own blocks), so a block no termite digs no longer stops the web; the tip of a way that is not dug through (blocked) is not led on (`growTips`, `ForagingHoles.isDug`). Test: `TermiteNewLandTests.aGalleryBlockNoTermiteDigsDoesNotStopTheWeb` (cobblestone and glass in one piece; three more pieces grow). Left: the blocked piece itself is never dropped or planned round, and no piece to food starts from behind it.**

**Checked again by the lead.** Read the gate in `GalleryWeb.growTips` (`savanna/colony/GalleryWeb.java:406-415`),
`Nest.isDugOut` (`framework/colony/Nest.java:781-787`: air, a seed heap or a soil ramp), `Nest.firstDiggable`
(`:1133-1151`: passes over anything that is not soil, and soil touching water) and `GalleryCatchUp.growNow`
(`savanna/colony/GalleryCatchUp.java:52-74`: the same gate for the builder numbers). It holds.

- **What is wrong:** before any tip may grow, every block of every piece must be "dug out". The diggers skip every
  block that is not soil. So a gallery block holding anything else (a torch, a block a player placed, water from a
  bucket, a block that fell in) is never dug and never counts as dug: the gate fails every second for the life of the
  colony. Nothing notices, plans round it or drops the piece. The pieces grown from the bad one never count as dug
  either (a piece's tunnel holds its parent's blocks), so no piece out to food starts from them
  (`GalleryWeb.pieceTo`, `:765-781`). A raid's seal blocks the web the same way, but only until it is opened at night.
- **Unchecked link:** whether a hole's opening also waits for the whole tunnel to count as dug (likely).
- **The fix proposed:** no new rule; let the gate ask the diggers' own question. Today two judges answer "is there
  digging left on the web?": the gate (`isDugOut` on every block) and the diggers (`firstDiggable`). The gate
  becomes "the diggers have nothing left to dig in the web". A test: a web with a cobblestone and a water block put
  into one piece; the next tip still grows.
- **From:** W-1.

### R5-55 (A, proven as code; its meeting with BUGS 361 likely): a termite alarm never ends while one living enemy insect stays within 4 blocks of it, and it walks along with that enemy

**In play (2026-11-27):** B. After a raid has gone home, the termites round that feeding spot do not go back to work while a stuck or crippled raider is alive there: no worker comes out within 16 blocks, the soldiers stand at their places. A column walking home drags the alarm with it across the termites' land, and before a cover breaks the soldiers keep being sent to new places as the trouble jumps from raider to raider.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: an enemy that only goes on standing by keeps the alarm up no longer than a seal stays shut (`Alarm.stillFelt`, `standingBy`, `RaidDefence.SEAL_MOST`, one game day); it counts anew once it has been gone as long as a foe's trouble lasts, and one that hurts a termite raises the alarm again at once (`memberHurt`). A trouble insects made stays where it began, as a breach does (`Alarm.trouble`), so it no longer jumps from raider to raider. Test: `foragersHurryInThroughAYoungMoundsOpenShaftAtAFoe` goes on with the foe still standing there. Left: a trouble made by another animal or a player still moves with it; R5-70's one record per trouble.**

**Checked again by the lead.** Read `Alarm.tick` (`savanna/colony/Alarm.java:334-370`: an enemy within
`FOE_AT_TROUBLE`, 4 blocks, of any trouble that is not footsteps feeds it, line 362) and `Alarm.trouble`
(`:206-250`: strength back to full at 233-234; a foe's trouble moved to the enemy's own place at 246-248). It holds.

- **What is wrong:** once a second each enemy insect near a foe's or a breach's trouble fills it up to full strength,
  and a foe's trouble is moved to where that enemy now stands. Nothing limits how long or how far. (1) An enemy that
  cannot leave (BUGS 361's raiders stuck in termite holes, 13 at once in the soak; a raider with legs cut off) holds a
  16-block trouble at full strength for as long as it lives. (2) An enemy walking slower than 4 blocks a second
  carries the trouble with it, to the edge of the area the alarm looks at (the foraging range plus 8). (3) With 20
  raiders round a feeding place the trouble ends each second at whichever raider the entity list gives last; the
  places round it are worked out again (a walk over the whole nest, R5-83) and every soldier whose cell is no longer
  in the ring gets a new one.
- **The seal already has the cap the alarm lacks:** `RaidDefence.tick` (`savanna/colony/RaidDefence.java:211-212`,
  "a foe stuck by the break for ever no longer keeps it shut for ever").
- **Where:** as above; `Alarm.keepsIn` (`:441-443`); `framework/entity/AbstractAntEntity.java:2234` (`mayGoOut`);
  `savanna/colony/Soldiers.java:577-584` (a trouble keeps soldiers real unwatched).
- **Unchecked link:** that BUGS 361's stuck raiders sit within 4 blocks of a live trouble (likely: they are stuck in
  the gallery they fought in); how far apart the raiders round one feeding place stand.
- **No test** has a foe that stays; `TermiteAlarmTests` checks only that the alarm is calm once the foe is gone.
- **The fix proposed:** treat a foe's trouble as a breach is treated: it stays where it began while it is fed. Only a
  foe the termites can still feel at their own cells (within 4 blocks of a gallery or nest cell, or of a termite)
  keeps it up, and no longer than the seal's own longest. My pick: keep "shut in while an enemy is at the galleries",
  end it for one walking away or shut in a pocket. This does not mend BUGS 361, but 361 stops freezing the termites.
  Best built as part of R5-70's one record per trouble.
- **From:** Q-1, S-5.

### R5-56 (A, proven): workers in a raided feeding gallery do not run down it, because three rules disagree on what "safe inside" means

**In play (2026-11-27):** B. At a raided feeding spot the termite workers stay in the roofed part of the gallery beside the break, some still feeding, and are stung and carried off one by one; they do not pour back down the gallery while the soldiers hold the opening, as real Macrotermes workers do.

**Status (2026-11-27, `claude/review-fix-5a`): half fixed: a worker under a roof the hunters can still reach (a feeding gallery or runway, a step in from an opening: not `isSheltered`) that feels a hunter within 3 blocks, or a nestmate felled or held, now flees, to a cell inside that is no doorway and no feeding way, down the gallery into the nest (`FleeGoal.canUse`, `start`, `feelsHunter`); with no way there, under any roof as before. The alarm alone still sends in only those outside. Left: `GoInsideGoal` and `RetreatGoal` keep their own judges; no raid soak was run, so how many termites a raid still brings home is not known.**

**Checked again by the lead.** Read `FleeGoal.canUse` and `start` (`framework/entity/goal/FleeGoal.java:57-104`: no
flight when `Nest.isInside`, line 67; the place to run to is any "inside" cell that is no doorway, line 101),
`Nest.isInside` (`framework/colony/Nest.java:1038-1088`: every roofed feeding gallery cell is inside) and
`AbstractAntEntity.isSheltered` (`framework/entity/AbstractAntEntity.java:1899-1904`: not in a feeding way). The
comment on `Raid.bringOutFeeders` says "they feed, feel the raiders and flee" (`savanna/colony/Raid.java:1791-1792`).
It holds.

- **What is wrong:** the hunters ask "is it sheltered" (in its nest, not in a feeding gallery or runway, no opening
  near): by that rule a termite in a feeding gallery is fair prey. The worker's own flight and its walk home
  (`GoInsideGoal`, line 58) ask the nest "is this cell inside", and the nest counts every roofed feeding gallery cell
  as inside. So a worker in the very gallery being raided is told "you are already inside": it does not flee, and one
  whose roof was just bitten off flees only to the next cell that still has a roof. It may even keep feeding
  (`ClipGrassGoal`, line 82). The only rule that sends workers deeper is `RetreatGoal`, for those within about 4.5
  blocks of a break that lies within 2.5 blocks of a tunnel.
- **The tests would still pass** with workers that never run: `aWorkerAtABreaksMouthIsStungAndOneDeeperInIsLeft` and
  `theTermitesFeedingUnderTheMudStandInTheirGalleryWhenRaidersBreakIn` (`TermiteRaidDefenceTests`).
- **Where:** as above; `savanna/colony/RaidDefence.java:137-189`; `savanna/colony/Raid.java:393-397`, `:1821-1854`.
- **The fix proposed:** one shared judge, "out of the hunters' reach", asked by the flight, the walk home and the
  hunters alike; the hunters' `isSheltered` is the right one, because it decides who is stung. `RetreatGoal`'s list
  then shrinks to its real job (who packs the seal). It is the same one rule to tune for BUGS 452, this finding's
  mirror, so it belongs in the raid work and is measured with the soaks (how many termites a raid still brings home).
- **From:** Q-2.

**Your call (2026-11-27):** should termite workers run down the gallery when ants break into their feeding spot? Today they mostly stay in the roofed part by the break and are taken one by one. Real workers run while the soldiers hold the opening, and the ants catch the slow ones and the soldiers. My pick: yes, they run, built together with the raid work and checked with a soak, because watched raids already catch little (13 of 27 stung nothing in the last soak) and this could make it less.

**Call made (2026-11-27):** yes, they run (the user: "go with the other 11", taken as the yes to my pick). Built without the soak; to be checked with the next raid soak.

### R5-57 (A, the sums proven; how often it shows to see in play): the minors take every real soldier place first, so at night beside a full colony, and whenever a raid's drumming reaches the nest, every real major is sent into the numbers

**In play (2026-11-27):** B. Near a grown colony at night no major soldier is to be seen: the royal chamber's and the gardens' doorways stand unguarded. When a raid reaches the nest or the player breaks into the mound, the majors walking their beats vanish just as the alarm starts.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: the real soldier places are shared between minors and majors in the colony's own ratio when they ask for more than there are, what one rank does not need going to the other (`Soldiers.shareOut`, `minors_per_major`: of 12 places 9 and 3). Test: `SoldierPlacesTest` (the sums). Not watched in play at night.**

**Checked again by the lead.** Read `Soldiers.wantReal` (`savanna/colony/Soldiers.java:571-589`): the real places are
`cap / 4` and are handed out in the order of the ranks, minors first, each rank taking `min(posts, count, what is
left)`; while a trouble reaches the nest `posts` is the rank's whole count. `macrotermes.json:8` has `real_cap` 50, so
12 places; a full colony has 15 minors and 5 majors. It holds.

- **What is wrong:** at foraging time the minors' beats include every post outside (one by each way out, a sentry
  every 3 blocks along each trail, `Soldiers.java:222-241`, `SentryLine.java:49`), easily 12 or more, so the majors
  get 0. When a foe's or a breach's drumming reaches the nest the minors ask for all 15 and again take all 12. The
  population keeper then sends real majors out of a player's sight into the numbers, 2 a second
  (`framework/colony/Population.java:493-576`). These are the ones BUGS 210 put first in line against insects.
- **Unchecked link:** how many outside posts a grown colony really has at night. By day with fewer than 12 minor
  beats the majors get the rest and nothing shows.
- **The fix proposed:** no new number: share the quarter between the ranks in the colony's own ratio (the species
  file's `minors_per_major`, 3 to 1: about 9 minors and 3 majors real) before filling either. My pick, from the real
  split of the castes. A test: a full colony watched at night with a long trail keeps a real major on its beat; with a
  breach reaching the nest no real major goes into the numbers.
- **From:** S-1.

### R5-58 (A, proven): a creature in a gallery puts the hidden termites back, and they go under again on the very next tick, so they blink and slip past it unseen

**In play (2026-11-27):** B. Termites in a dug-open or raided gallery blink into view for a tick each second and are gone again, and walk "through" a raider or a player standing in the gallery; the rule "if anything breaks into a gallery, every insect travelling in it shows up as a real one" holds for one tick only.

**Status (2026-11-27, `claude/review-fix-5a`): fixed: a stretch a creature was found standing in is kept as met until a look finds it clear (`HiddenTrips.met`, `creatureIn`), and the legs are told its cells are not hidden (`isHidden`), so a termite put back walks for real past the creature. Test: `hiddenTripsGoUnseenAndShowUpWhereTheyAreWhenTheGalleryIsBrokenInto` sets a chicken into the gallery: nobody travels unseen through its cell.**

**Checked again by the lead.** Read `HiddenTrips.isHidden` (`savanna/colony/HiddenTrips.java:171-175`: blocks and the
closed-for-danger cells only), `brokenInto` (`:260-285`: the only place that looks for a creature), `comeOut`
(`:314-338`), and the navigator's `followRoute` (`framework/nav/AntNavigator.java:1073-1076`), `goUnder`
(`:1173-1212`) and `cameUp` (`:1228-1246`, which sets the step clock to 0 so the next tick starts a step). It holds.

- **What is wrong:** once a second the hidden trips look for a break-in; a creature that is not the colony's own
  standing in the stretch puts each traveller back where its clock says it is. But the legs' question "is this cell
  hidden?" looks only at blocks. So a termite put back because a raider stands in the gallery goes under again on its
  next step, and a second later is put back again a little farther on, until its clock-based place is past the
  creature. Each blink is an entity removed and added again, for every traveller in that gallery, every second.
- **Unchecked link:** how many steps a termite usually has left past the creature (with fewer than 12 it stays out).
- **The test allows it:** `hiddenTripsGoUnseenAndShowUpWhereTheyAreWhenTheGalleryIsBrokenInto`
  (`TermiteNewHoleTests.java:342-498`) breaks in only by opening a roof block, and its last stage lets a termite put
  back start a new hidden trip at once.
- **The fix proposed:** one judge, not two: the once-a-second look keeps the cells it found broken into until the
  next look finds them clear, and `isHidden` answers no for those cells, as it already does for the cells closed for
  danger. A termite put back then walks for real past the creature and meets it. A chicken set into the gallery of
  the existing scene tests it.
- **From:** H-1.

### R5-59 (A, to measure; a new side of R5-8 and R1-36): beside a grown colony the "new holes to food" check, with up to 6 route searches, runs every second instead of every 10 seconds

**In play (2026-11-27):** B. A server that gets slower beside grown termite mounds whose web has used up its length while known logs or hay lie farther out, the usual state of a grown colony. Not measured.

**Status (2026-11-27, `claude/review-fix-5a`): half fixed: a food the workers do not allow a piece to yet is remembered for the check's own 10 seconds (`ForagingHoles.notAllowed`), as a food with no route is, so the same routes are no longer planned through the ground every second. Left: the check's cheap part (the runways, the food list) still runs with the builders' time every second, because the galleries' growth needs food to come first (`GalleryCatchUp.growNow`); not measured.**

**Checked again by the lead.** Read `BuildLife.tickNear`, `slice` and `growGalleries`
(`framework/colony/BuildLife.java:124-132`, `:187-205`, `:285-304`), `GalleryCatchUp.growNow`
(`savanna/colony/GalleryCatchUp.java:52-74`, line 70: `checkNewHolesNow`, "food first") and `ForagingHoles`
(`framework/colony/ForagingHoles.java:386-389`, `:396-483`). The path holds; its cost is not measured.

- **What is wrong:** the check that plans gallery pieces out to rich food has its own clock (every 200 ticks). Since
  R1-36 the builder numbers also work beside a player: every second their time is added, and once the nest and the
  mound are done the galleries step runs, which starts the food check by hand and resets its clock. When nothing
  grows the time is dropped and the next second starts again; while pieces grow it runs up to 4 times a second. Each
  run tries up to 6 foods and each try plans a route through the ground (up to 6,000 columns). A food with no route
  is remembered for a while; a food the workers "do not allow yet" is not, so the same routes are planned on every
  run. Before each route `pieceTo` reads every block of every piece's whole way (about 2,000 block reads on a full
  web, R5-92).
- **Unchecked link:** how long one run takes on a grown web, and how often a grown colony's nest and mound are both
  "done" (a mound still growing keeps the galleries step from running).
- **The fix proposed:** remove the second caller, not add a limit: `growNow` grows tips only; the brain's own check
  every 10 seconds already plans the food pieces. If food must come first on return, run the check once on coming
  back. And remember "not allowed yet" foods as "no route" foods are remembered, until the workers or the web's
  length change.
- **From:** W-2.

## Grade B (part B)

### R5-60 (B, proven; a new side of R1-38): "it rains" is judged two ways along the same night, and in a savanna the two never agree

**In play (2026-11-27):** B. On a grey day with no rain falling in the savanna the foraging holes open and termites walk out at noon, where the Matabele's day raids and the player find them; and there is never a rainy night with more holes open.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: one judge, `Colony.rainsNow` (the world's weather is rain), for the foraging time, the night's holes and the raids (my pick, as the finding proposed). So in rain the savanna termites forage by day as before, the night's holes are half as many again, and a raid turns home and none sets out (M8 step 3e, which never ran in a savanna before). No test of its own (the rain tests pretend rain).**

- **What is wrong:** "is it foraging time" asks whether the **world's weather** is rain (`level.isRaining()`). "How
  many holes tonight" asks whether rain **falls on the nest** (`Colony.rainsNow`: the weather is rain and the nest's
  biome has rain). No savanna has rain: Minecraft's own (read in `OverworldBiomes.java:327`) and the mod's floodplain
  (`savanna_floodplain.json:89`). So (1) when the weather turns to rain by day the termites of every savanna come out
  under a dry sky; (2) the rule "half as many holes again in the rain" (`rain_more` 0.5) never runs in play, only in
  tests that pretend rain; (3) round 1's fix for R1-38 as written (ask "rain on the nest" in both) would switch the
  termites' rain foraging off for good. Also: a foraging time that starts on a rainy afternoon and runs into the
  night is one round with one pick of holes, so that night brings no new holes.
- **Where:** `framework/colony/Colony.java:433-440`, `:541-548`; `savanna/colony/HoleTurns.java:100-110`, `:324-325`;
  `savanna/species/SavannaNumbers.java:395-408`.
- **Unchecked link:** a mound on a river bank (the mod's `savanna_river` has rain) would get rule 2.
- **The fix proposed:** one judge, `Colony.rainsNow`, for both steps. My pick for a dry biome: it says "wet weather"
  when the world's weather is rain (real Macrotermes do come out on wet, overcast days, and the sky does darken), so
  the daytime foraging stays and the extra holes start to work; the dry-sky look is for the seasons work to make
  right. The two tests named in P-3 pretend rain and see none of this.
- **From:** P-3.

### R5-61 (B, proven in the code; how often it shows not measured): the night's holes bind the counted foragers but not the real ones, so the watched colony and the unwatched one forage by two rules

**In play (2026-11-27):** B. At night beside a grown mound termites trail far across open ground away from any open hole, and lone scouts wander tens of blocks out, where the design means a tight ring of grazers round each open hole; these far walkers are the ones caught out at dawn.

**Status (2026-11-27, `claude/review-fix-b9`): fixed: one rule of where a colony with galleries grazes, `FieldLife.grazedFrom` (a hole open to the foragers tonight within the graze, 6 blocks): the numbers kept to it already; now the real foragers' pick (`FoodStore.findGrass`: outside, only a plant one of tonight's holes grazes, or food eaten from inside) and the scouts' spot (`ScoutGoal.pickSpot`: within the graze of one of tonight's holes picked at random, so the explore holes send real scouts out too) ask it. No new number. No test of its own (`termitefood` and `away` pass with it).**

- **What is wrong:** the design (termites underground, step 4) is that termites graze only round the holes opened
  that night. The numbers obey it (a plant is worked only if a hole in turn lies within 6.5 blocks,
  `FieldLife.grazedFrom`). The real ones do not: (1) a real forager takes the known plant worth the most, the walk
  counted from the nearest open way out with no limit, and the plants it may pick are scanned round every dug hole,
  closed ones too; once the tufts by the open holes are taken the next forager is sent 20 or 40 blocks over open
  ground. (2) A scout walks to a random spot anywhere in the whole range (up to 56 blocks), and one ask in six scouts
  even with food known. (3) The two "explore" holes of a night explore only for the numbers: near, nothing sends a
  real termite out of one.
- **Where:** `framework/colony/FieldLife.java:431-495`; `framework/colony/FoodStore.java:291-330`, `:371-428`;
  `framework/entity/goal/ScoutGoal.java:58-89`; `savanna/colony/GalleryWeb.java:342-358`;
  `savanna/colony/HoleTurns.java:339-355`.
- **Unchecked link:** how many far walks a night really happen (a watched night with a grown web).
- **The fix proposed:** one shared question in place of two rules: "may the colony take this plant tonight?" = a hole
  in turn (or a runway's hole, or a hole by rich food) within the graze of it. `FieldLife` already has it; the real
  forager's pick and the scout's spot ask the same, the explore holes giving the new ground. No new number. My pick,
  as the design already says and as real Macrotermes keep to the ground by their holes.
  `holesTakeTurnsNearFoodFirstAndMoreInTheRain` checks which holes open, never where a real forager goes.
- **From:** P-2.

### R5-62 (B, proven in the code; how it looks to see in play): beside a spectator the counted foragers cut the grass in plain view, so most of a watched night's foraging is done by termites that are not there

**In play (2026-11-27):** B. Watching in spectator at dusk, the holes open, a few termites come out, and the grass round the holes shrinks and vanishes by itself faster than they reach it; the termites that are seen walk far off across open ground (R5-61).

- **What is wrong:** "a plant a player can see is left to the real foragers" (`framework/colony/FieldLife.java:533-536`)
  asks for eyes that leave a spectator out (`Population.viewersInSight`, `Watchers.hiddenFrom`: the call of
  2026-11-13, made for the raids). So beside a spectator every plant counts as out of sight: the numbers work every
  tuft in the graze of the night's holes within the first minutes, the cuts are shown at once, and the real foragers
  find the near tufts taken.
- **Where:** `framework/colony/FieldLife.java:448`, `:523-575`, `:589-621`; `framework/colony/Population.java:413-450`;
  `framework/colony/Watchers.java:41-50`.
- **Unchecked link:** how many of the foragers are real bodies at a full colony beside a spectator.
- **The fix proposed:** keep the 2026-11-13 call for what it was made for and give the forager numbers the other list
  for this one question (`Population.viewers`, which counts spectators): one word in `forageWith` and `showCuts`.
- **From:** P-6.

**Your call (2026-11-27):** in spectator, should grass be cut by termites that are not there? Today the counted foragers eat the grass in front of a spectator's camera, because on 2026-11-13 you said things may appear in front of a spectator (for the raids). My pick: in front of any player, spectator too, only real termites cut grass; the numbers keep to what nobody looks at. Insects appearing for a raid in front of a spectator stay as you chose.

### R5-63 (B, likely; the size of it to measure): the night's holes are opened last, one at a time, from one fixed list

**In play (2026-11-27):** B. At dusk by a growing colony few or no holes open for minutes while the builders dig; by a grown one the holes open one after another across the land instead of together; and a hole no builder can reach leaves the holes after it shut all night, with builders standing idle.

**Status (2026-11-27, `claude/review-fix-b9`): fixed: opening the night's holes is its own job, the shape the plugging has (`ForagingHoles.pickHoleToOpen`, asked first by `DigGoal` of every worker that digs): each worker takes the nearest closed hole in turn nobody else has taken and that it has not found out of reach, for 30 seconds; the hole is off the builders' one work list (`Nest.findDigTarget`). Test: `holesTakeTurnsNearFoodFirstAndMoreInTheRain` now asks that two workers get two holes of the night's and that one that found its hole out of reach gets a third.**

- **What is wrong:** biting a hole open has no owner. It is the last-but-one entry of the builders' one work list
  (`Nest.findDigTarget`), which hands every builder the same single block. (1) Every gallery block still to dig comes
  before any hole, and the web plans its next piece within a second of the last block dug, so while a web grows a
  hole can be opened only in the second between two pieces. (2) `holeToOpen` returns the first closed hole in turn in
  list order to every builder that asks; the night's 10 to 15 holes lie spread over the whole web. (3) It does not
  ask whether a builder can stand under the hole; a builder that finds no way leaves it alone for 400 ticks, and its
  next ask gets the same hole. The holes after it, the garden rooms and the mound's digging wait until the night ends.
  The numbers wait for none of it: they graze round a hole in turn whether its block is open or not.
- **Where:** `framework/colony/Nest.java:213-337`; `framework/colony/ForagingHoles.java:236-258`;
  `framework/entity/goal/DigGoal.java:63-81`, `:119-147`; `savanna/colony/GalleryWeb.java:391-468`.
- **Unchecked link:** how long the gaps between pieces are in play, and whether a hole with a dug tunnel can in fact
  be out of reach (sand fallen in, a piece walled off against a neighbour).
- **The fix proposed:** fewer rules: opening the night's holes becomes its own small job in the shape the plugging
  already has (`ForagingHoles.pickHoleToPlug`: each builder takes the nearest one nobody has claimed, a hole it found
  no way to is skipped by that builder), before new gallery pieces at the start of a foraging time. The long test
  `termitesOpenTheirForagingHolesAtNightAndPlugThemByDay` waits any number of nights and would not notice.
- **From:** P-4.

### R5-64 (B, proven for the record; its part in BUGS 452 likely): by day the colony's record names where termites could feed, not where any termite is, and the raid stands termites also in cells no raider can get at

**In play (2026-11-27):** B. A raid at a log where the termites had stopped feeding still finds termites; at a spot with one open cell most of the termites brought out stand shut in under the wood, counted as there, with raiders pacing above them (the picture of BUGS 452).

**Status (2026-11-27, `claude/review-fix-b8`): fixed (its first half): the raid's feeders stand only in the cells of the by-day places that a raider can get into (`Raid.feedersOut`, `MatabeleEntity.wayInto`), the nearest the spot first; none are shut in under whole wood. Test `theTermitesFeedingUnderTheMudStandInTheirGalleryWhenRaidersBreakIn` now also calls feeders before the mud is put on, with no way in, and gets none (with the mud open the whole gallery joined to it is reachable, and three come out as before). Left: one judge of "termites feed here now" owned by the termite colony, and Q-9's tenth taken twice.**

- **What is wrong:** by day three questions have three answers. Who really feeds: nobody, when the store is full (the
  usual state of a well-fed colony), the food cannot be eaten from inside or no pieces are left. What the record says
  (`FeedingSpots.byDayPlaces`): every feeding gallery and closed runway that is built right, a question about blocks.
  What the raid is told (`Raid.byRecord`): a by-day place within reach with a way in counts as "termites there"; then
  `bringOutFeeders` takes workers out of the termites' numbers and `feedersOut` stands them in that place's cells,
  those with a way in first and **then the others** (the sort only orders them).
- **Where:** `savanna/colony/FeedingSpots.java:60-104`; `savanna/colony/Raid.java:354-390`, `:1796-1854`;
  `framework/colony/FoodStore.java:291-330`; `framework/colony/FieldLife.java:417-446`.
- **Unchecked link:** whether BUGS 452's termites were ones stood in cells with no way in (the bug entry says this is
  not measured). The soak diaries can say.
- **The fix proposed:** in `feedersOut` only cells with a way in (a termite the raiders cannot reach is no prey). And
  one judge of "termites feed here now" owned by the termite colony. My pick: keep "always a few there" as the stated
  rule (real Macrotermes keep workers under their sheeting by day), but only in cells the raiders can reach.
- **Also (Q-9):** a watched raid with some raiders along as numbers applies "never more than a tenth of the termite
  colony" twice, once to the numbers' catch and once to the feeders brought out (`Raid.java:1810`, `:1942-1944`); one
  count of "taken by this raid so far" for both.
- **From:** P-5, Q-9.

### R5-65 (B, proven as code; how often it bites to measure): the raid reads the termites' alarm as "they have given this spot up"

**In play (2026-11-27):** B. A raid whose arrival alarms the termites before the attack starts (a hole still open at dawn, a forager still out) is told nobody feeds there: the column looks round and goes home and the find is forgotten, or it attacks and the termites under the mud are not brought out for it.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: the alarm no longer answers `KEEP_OFF` (it answers `ALARMED`), so "keep off" means only the lasting reasons again; the foragers' plant pick asks `isAlarmed` itself (`FoodStore`). Left: Q-3, the termites plugging `OUT_THERE` for their far feeding places.**

**Checked by the lead.** `SavannaParts.java:257` answers the question `KEEP_OFF` with `Alarm::keepsIn` (line 121 is
the lasting answer, the closed holes); `Raid.java:378` (the judge) and `:1801` (`bringOutFeeders`, which returns
with no diary line) ask `keepsOff`.

- **What is wrong:** the raid asks the termite colony "do you keep off this spot?" in three places and means the
  lasting answer (attacked here before, the place shut). The alarm answers the same question with "a trouble is felt
  here right now", which lasts seconds. With a trouble of strength 16 at the raiders every place within 16 blocks
  answers "kept off". Then the judge at the halt says "no termite at the spot", or the BUGS 202 fix (feeders brought
  out) is skipped without a word, and raiders going as numbers take nothing (`Raid.java:1936`).
- **When:** the alarm rises before the first bite only when a raider comes within 3 blocks of an open way out or 4 of
  a termite outside. By day the holes are plugged, so most raids do not meet it; a raid in the first raid hour (6:00)
  can. The soak diaries can say: "(the termites keep off the spot)" or "no termite at the spot now" at a find no
  earlier raid went to.
- **The fix proposed:** fewer answers: the alarm stops answering `KEEP_OFF` (it already answers `ALARMED`, and the
  food goals ask that themselves), so "keep off" means only the lasting reasons again, as `Colony.keepsOff`'s own
  comment says.
- **Also (Q-3, graded A by its reader, B here as a narrow case):** a termite colony is "here" only with a player
  within 64 blocks of its mound's middle; the Matabele colony is here wherever its raid is (the `OUT_THERE` socket,
  `SavannaParts.java:302`), the termites plug nothing in for their feeding spots. A player who stands beyond a spot
  that lies 50 blocks or more from the mound watches real raiders attack a colony of numbers: nobody is there
  (`AwayLife.isHere`, `framework/colony/AwayLife.java:164-215`; `Raid.java:381-383`, `:1804-1807`). The fix: the
  termites plug `OUT_THERE` too with the feeding places they use by day.
- **From:** Q-8, Q-3.

### R5-66 (B, proven as code): termite soldiers never bite a player, though the comments and the plan say they lunge

**In play (2026-11-27):** C. A player can break the mound open or hit termites and the soldiers only gather, drum and stand round him.

- **What is wrong:** a soldier bites only what the fight rules let its species attack. The termites' list of animal
  foes is empty since the chickens went, and a player can be attacked only by a species with the `nest_defence` part,
  which the termites do not have. `DefendGoal.java:131` says "At a player or an animal it still lunges", step 6 of
  `docs/plans/termite-alarm-rework.md` the same; `docs/DESIGN.md:84` says players are not attacked (M8), so the code
  may be what was chosen and only the comments wrong.
- **Where:** `savanna/entity/goal/DefendGoal.java:181-200`, `:141`, `:211`; `framework/fight/Fights.java:55-103`;
  `macrotermes.json` (`fight`).
- **The fix proposed:** the comments say what the code does; then the call below.
- **From:** S-10, Q-11.

**Your call (2026-11-27):** should termite soldiers bite a player who breaks into the mound? Today they never do: they come, drum and stand. Real Macrotermes majors bite whatever comes into a breach and draw blood from a hand. My pick: yes, a soldier bites a player who stands within reach of a breach he made or who hit a termite, for small damage (1), through the nest defence part the Matabele already use (it leaves creative players and spectators alone).

### R5-67 (B, proven in the code; how it looks to see in play): the drumming is felt 16 blocks round the trouble through solid ground and is never passed on, and the nest's "does it reach" is a second rule, so the colony calls out soldiers that never hear

**In play (2026-11-27):** B. A raid or a player digging into a gallery 30 or 40 blocks out (most feeding places) is answered only by the one to three soldiers already on a beat there; no soldier comes from the nest, while a soldier in a separate gallery 10 blocks off through solid ground answers at once.

- **What is wrong:** (1) every soldier within 16 blocks of the trouble in a straight line hears it, at once, through
  soil and across unconnected galleries. A soldier that hears drums, but nobody hears that drumming: the reach is
  measured from the trouble only (`Alarm.reached`, `listen`), though `listen`'s own comment says "the others feel that
  in turn" and the plan's step 3 says it "relays outward". (2) The colony asks "does the trouble reach my nest" from
  the trouble to the nest's whole box, 24 blocks to each side of the middle (`Alarm.reachesNest`); each soldier asks
  by its own distance. For a break 24 to 40 blocks from the middle the first says yes and the second no for every
  soldier in the mound: up to 12 soldier numbers become bodies in the nest rooms, none hears, none goes, and as many
  workers are turned back into numbers to stay under the cap.
- **Real biology:** Hager and Kirchner 2013 (as the design quotes it): one pulse fades within about 40 cm (about 13
  blocks at the mod's scale), soldiers that feel it drum back within about 0.3 s, so the signal runs soldier to
  soldier along the galleries over several metres at about 1.3 m a second. The mod has one soldier's own range and
  no relay.
- **Where:** `savanna/colony/Alarm.java:449-496`; `savanna/entity/TermiteSoldierEntity.java:427-433`;
  `framework/colony/Colony.java:806-809`; `savanna/colony/Soldiers.java:578-584`; `macrotermes.json` (`alarm`).
- **The fix proposed:** one rule in place of the fixed reach and the nest's box: a soldier hears a trouble when the
  trouble, or a soldier that has heard it and drummed, is within one pulse's reach; the nest hears when a soldier in
  it hears. Decided together with R5-68 (how many answer).
- **From:** S-4, Q-4.

**Your call (2026-11-27):** should the alarm be passed on from soldier to soldier, as real termites drum it along their galleries? Today a raid far out is answered only by the soldiers within 16 blocks of it. My pick: yes, the relay (it is the real behaviour), built with a balance check on a soak, because more soldiers would then reach far raids and too many soldiers at once wiped raids out before.

### R5-68 (B, proven as code; a new side of R11-17): near and away disagree on how many soldiers answer a raid, and on what the termites remember of it

**In play (2026-11-27):** B. The same raid on the same colony is a hard fight unwatched (60% of the soldiers answer, up to 12, a quarter of them taken) and a light one watched (the one to three soldiers at the spot). (What the termites remember of it is the same now, watched or not.)

**Status (2026-11-27, `claude/review-fix-b9`): half fixed: the memory half. The termites taken by raiders going as numbers are remembered by their colony when they are taken, one entry a termite as a sting is (`Raid.remembered`, through `DangerMap.hurt`; a termite body in the open by its own id), so a raid nobody watched is remembered as a watched one (it was one death written when the raid got home, nothing for one that never did). Test: `aNearRaidsNumbersTakeTermitesFromTheColonyAndBringThemHome` asks it at the attack. Left: how many soldiers answer a raid as numbers (60%, up to 12) against as bodies (those within 16 blocks): decided with R5-67's call (the alarm passed soldier to soldier), which sets how many bodies hear.**

- **What is wrong:** (1) as numbers a raid meets 60% of all the colony's soldiers, at most 12 (`Raid.takeHidden`); as
  bodies only those within 16 blocks of the break (R5-67). (2) The termites' memory of danger decides which holes
  stay shut and which are given up for good. A watched raid writes into it at every sting (a small real wound, 2
  points, up to 12 at one place); an unwatched raid writes once, as one death (4 points), and only when the raid has
  got home; a raid that never gets home writes nothing. A hole is given up for good after two attacks that once
  reached 6: two watched raids do it within four to five days of each other, two unwatched only within about a day.
- **Where:** `savanna/colony/Raid.java:1887-1890`, `:1947-1959`; `savanna/colony/Raids.java:579-596`;
  `framework/fight/Fights.java:129-133`; `framework/colony/DangerMap.java:140-290`; `macrotermes.json:27-44`, `:266`.
- **Unchecked link:** the real counts in play (the numbers come from the measuring scene
  `aDaytimeRaidOnAFullColonyIsMeasured`); the days are arithmetic from the file's numbers.
- **The fix proposed:** decide once how many soldiers answer a raid at a feeding spot and how fast (those at the spot
  at once, more down the gallery over the next minute) and let numbers and bodies read that one rule; and the
  unwatched raid tells the memory what it took when it takes it, one entry a termite, through the call the bodies use.
- **From:** Q-5, Q-6.

### R5-69 (B, proven; a new side of R5-18 and R6-2): termite soldiers never starve and never die of age, so a colony whose workers are all gone lives on for ever as a handful of soldiers

**In play (2026-11-27):** B. A mound that should be dead (no workers, no queen, no building, no foraging) stays listed as a living colony with slow soldiers walking its beats for good, and never becomes a dead mound for the Matabele to move into.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: a soldier starves by the workers' starve time, as a number (`Meals.dietOf`, once the workers' hunger runs) and as a body (`TermiteSoldierEntity`), so a colony of soldiers alone dies out by the rule that exists (my pick: they eat only what the workers bring). No test of its own.**

- **What is wrong:** a soldier cannot feed itself; the workers bring it fungus. Without them it only slows
  (`slow_after_ticks`), as a mob and as a number: every caste but the workers has "never" as its starve time
  (`framework/colony/Meals.java:49-57`, on purpose by its comment), and only workers die of old age. A colony ends
  only when nobody is counted, and soldiers are counted.
- **Where:** as above; `framework/colony/Population.java:220-232`, `:653-667`;
  `savanna/entity/TermiteSoldierEntity.java:310-321`, `:438-449`; `framework/colony/Colony.java:724-734`.
- **The fix proposed:** one rule, not two: soldiers get the workers' starve time (they eat what the workers bring, so
  they starve when nobody brings it), as R6-2 made a Matabele queen with no workers starve. The soldier-only colony
  then ends through the rule that exists. My pick, from the real animal (soldiers depend wholly on being fed; how
  many days one lasts unfed I do not know). R5-18's king is the same question.
- **From:** S-2.

### R5-70 (B, proven as code): "a foe is near", "is this termite safe", "does the trouble reach" and "the trouble is over" are each judged in three to five places with different distances and clocks

- **What is wrong:** no part can ask "is there trouble here, and is it over". *A foe is near:* the alarm (within 3
  blocks of an open way out, 4 of a forager outside, 4 of a trouble), the raid seal (16 of the break), the body
  carriers (`Refuse.foeNear`), a worker's flight (a hunter within 3), a soldier's bite (`DefendGoal`, and
  `WalkBeatGoal`, which needs no alarm at all). *The trouble is over:* the alarm fades 80 seconds after it was last
  fed; the seal opens at night, 30 seconds after the last foe within 16 blocks, or after a day; a worker that fled
  stays in 60 seconds by its own clock; the holes stay shut by the danger memory's half-life; the raid ends its attack
  after 15 quiet seconds. *Safe:* R5-56. *Reaches:* R5-67. *Is this soldier fit:* `getSoldiers` and `Alarm.places`
  ask only "alive", so a paralysed or staggering soldier still hears, drums and keeps a place at the break while a
  fit one is put farther back (`savanna/colony/Soldiers.java:173-177`; its neighbour `soldierComing` does ask).
- **What it costs:** R5-55, R5-56, R5-65, R5-67 and R5-68 are each two such answers disagreeing, and the termites'
  side of BUGS 361, 452 and 453 will need a sixth answer unless there is one to ask.
- **Where:** `savanna/colony/Alarm.java:354-368`; `savanna/colony/RaidDefence.java:211-212`, `:301-304`;
  `framework/colony/Refuse.java:231-239`; `framework/entity/goal/FleeGoal.java:122-131`;
  `savanna/entity/goal/DefendGoal.java:181-200`; `savanna/entity/goal/WalkBeatGoal.java:148-154`. Reader Q's file has
  the table of every question and where it is judged.
- **The fix proposed:** one small record per trouble kept by the alarm (where, what made it, when last felt, over or
  not) that the seal, the flight, the body carriers and the soldiers ask, and that a raid's end can close.
  `RaidDefence`'s breaches and the alarm's troubles are already nearly the same list (both: at most 8, a place, a
  last-foe time). "Is this soldier fit" asked once, in `getSoldiers`.
- **From:** Q-7, Q-10.

### R5-71 (B, proven): a soldier already answering one trouble cannot hear a second one, however near and however bad

**In play (2026-11-27):** C. A player running about on the mound while a raid breaks into a gallery 10 blocks off: the soldiers stand at the mound drumming and the breach gets only the soldiers that had heard nothing yet.

- **What is wrong:** a soldier listens only while it answers nothing (`Alarm.listen` returns early on `hasHeard`). It
  does not hear a worse trouble until its first has faded (30 seconds after the last footstep, 80 after the last foe).
  Troubles join only within 8 blocks.
- **Where:** `savanna/colony/Alarm.java:483-506`, `:540-569`.
- **The fix proposed:** a soldier answers the worst trouble whose drumming reaches it now (breach over foe over
  felt). No new number. A test: footsteps on the mound, then a breach 10 blocks off.
- **From:** S-3.

### R5-72 (B, likely; to measure; R5-8's shape): a soldier with no way to its alarm place is handed the next place at once, one failed route search after another

**In play (2026-11-27):** C. A lag spike while a trouble lasts, only when soldiers that heard cannot reach it (a minor outside at night with the holes shut). Not measured.

- **What is wrong:** `DefendGoal` walks the soldier by asking the legs straight, not through the shared `Errand`. With
  no route to its place it marks the cell bad for 10 seconds and drops its task; 5 to 10 ticks later the alarm hands
  it the next place, another full search. The places are the ring round the trouble plus every covered nest cell
  within 24 blocks, so there can be dozens. BUGS 99 measured such failed searches at about 1 ms each. `stand` also
  plans again whenever the legs are done but the soldier is not in its cell, without asking whether the legs gave up
  (R2-2).
- **The same shape at a raid seal (S-7, Q-12):** `Soldiers.claimPost` hands a shut-out soldier its beat by the seal
  before it looks at the soldier's "no way there" list, so one that cannot reach it tries again every second or two
  until the seal opens (at most a day); and the list of shut-out soldiers is not saved, so after a reload they take
  ordinary posts whose way lies through the seal.
- **Where:** `savanna/entity/goal/DefendGoal.java:79-89`, `:254-275`; `savanna/colony/Alarm.java:595-608`,
  `:695-722`; `savanna/colony/Soldiers.java:465-470`; `savanna/entity/goal/WalkBeatGoal.java:98-106`, `:260-269`;
  `savanna/colony/RaidDefence.java:274-283`, `:397-435`.
- **The fix proposed:** walk with the shared `Errand`; on a failed search the soldier gets "no way to this trouble"
  for the trouble's life, or one search to the nearest of all free places (`Errand.setOff(Collection)`). `claimPost`
  skips a seal beat in the "no way" list as it skips every other; "shut out" is worked out when asked (outside the
  nest with a shut seal between it and home), not kept.
- **From:** S-6, S-7, Q-12.

### R5-73 (B, proven; the size of it to measure with a soak): a far termite colony forages "by the land" with no limit from the land, and one real plant left switches that free food off

**In play (2026-11-27):** B. Colonies far from the player fill up and reach the cap of 200 however bare their land, while the colony the player watches is held by its real grass and wood; after a long absence the far colonies are the big ones.

**Status (2026-11-27, `claude/review-fix-b9`): open: looked at and left as its own step. Read from the 6-day soak of `claude/review-fix-5a` (its `soak-others.txt` and `soak-sizes.txt`, 4 servers): all 12 far termite colonies sat at the cap (198 to 200 workers, 202 to 221 members), 5 of them with no plant remembered, and so did the 3 watched colonies not raided (217 to 221 members; the fourth was raided down to 72). So on the savanna the soaks make, near and far agree; the gap shows on bare land only, and the soak's colonies are full, so their food in a day is what they eat, not what the land gives. What is wanted is the land's own measure: each colony reads, when its land is loaded, how much food stands within the graze of its holes (and the bare patches' time to dry), and the free food out of sight takes from that after the remembered plants (the gate goes). That is a new saved number per colony and a balance change; to build with a soak on bare land, not guessed.**

**Checked by the lead.** `FieldLife.forageByTheLand` (`framework/colony/FieldLife.java:278-294`) and its gate at
`:470` (`placedNow == 0`). Its own comment calls it "a known departure" (BUGS 203: far colonies starved without it).

- **What is wrong:** while a colony is away and its forager numbers find no remembered plant to work this second,
  each free forager brings a piece about every 15 seconds out of nothing. The only limit is the room in the garden:
  with about 60 foragers about 1,900 pieces a night against the 50 a day a full colony eats. And the gate fights the
  rule it stands in for: the free food runs only when no forager worked any real plant this second, so one remembered
  tuft near tonight's holes switches it off for all the other free foragers that second. The more real plants the
  brain still remembers, the hungrier the colony. (Part A's R5-2 pulls the other way: it bites when forage only keeps
  up with eating, so it bites far colonies less than feared until this is mended.)
- **Unchecked link:** the real forager share of 200 workers.
- **The fix proposed:** one rule: the land out of sight gives what the land near would give. A pieces-a-day
  allowance for the colony's grazed area, measured once from a soak of a watched colony, that `forageByTheLand` takes
  from after the real plants, not instead of them (the gate goes). My pick; it needs the soak first, and R5-53 mended
  so the watched colony's number is the right one.
- **From:** E-2.

### R5-74 (B, proven; how often to measure): each colony keeps its own count of the pieces cut off a plant, so a plant two colonies share gives nearly twice its pieces

**In play (2026-11-27):** C. A little food from nothing where two colonies' ranges meet (mounds 42 to 82 blocks apart, each foraging out to about 56).

- **What is wrong:** the pieces cut are kept per colony (`FoodStore.grassClips`). The block shows it only for the
  plants eaten in stages, and a colony reads the block's stage only on its first cut; dry grass, tall grass, hay and
  the bleached branch show nothing. Colony A cuts 2 of a tuft's 4 pieces, colony B cuts 3, A cuts 2 more: 7 pieces
  off a 4-piece tuft. A 48-piece hay bale shared by two gives up to 95.
- **Where:** `framework/colony/FoodStore.java:67`, `:623-652`, `:696-702`; `savanna/block/BleachedBranchBlock.java:31-34`.
- **The fix proposed:** one truth per block: the pieces cut per position kept once for the whole level (the colony
  manager's saved data), read and written by every colony. No new number.
- **From:** E-3.

### R5-75 (B, proven; a new side of R5-11): the feeding-spot and gallery numbers were set for colonies of 600 to 700 workers and not looked at again when the cap became 200

**In play (2026-11-27):** B. A full mound keeps about 6 feeding spots at branches where 10 to 15 were chosen for a big colony, so there are fewer by-day places for the Matabele scouts to find; and its galleries stop at about 35 blocks out, not the 50 in the species file.

**Status (2026-11-27, `claude/review-fix-b8`): fixed: `nest.new_holes.per_workers` 30 to 14 (14 by-day feeding spots at the cap of 200 workers, 7 at 100, at least 3); the three reach numbers read only with the galleries off are out of the file (their defaults are the same); the galleries' note says `max_length` never binds at 200 workers and the tips' 35 blocks are kept. `tools/raid_balance.py` now caps workers at 200 as the game does: big colonies 8 to 14 spots, median 11 (6 before); a Matabele nest's nearest spot median 37 blocks (46), within 50 for 84 percent (63).**

**Status (2026-11-27, `claude/review-fix-b8`, later): open: the spot count is back at 30 workers a spot (6 at the cap). A 4-server, 5-day raid soak of the 14 showed the cost the balance model cannot: more and thinner spots, worth 75 to 90 to a scout where they were 105 to 150, and the nearest one to one Matabele nest a spot where every raid came back empty (the workers fled down the gallery past the raiders' leash); scouts found it again 26 times and it was raided again and again, nothing on the prey pile for four days, and that colony starved from 54 to 4 (on the same land with 30 it held 53 to 49, with prey from six spots). The rest stays: the dead reach numbers out of the file, the galleries' note, the model capped at 200 workers. To raise the count again, first BUGS 478 (a spot where every raid fails is never given up).

- **What is wrong:** `nest.new_holes` says "a full colony of 600 to 700 workers keeps 15, a half-grown one of 200
  keeps 6". Since the honest sizes (2026-11-11) a full colony is 200 workers: `mostFor(200)` = 6. `reach_min`,
  `reach_max` and `reach_full_at_workers` 500 are read only when the galleries are off. `galleries.max_length` 600
  never binds: at one block a worker the web is at most 200 blocks, which (4 arms of 16, then the shortest tip first,
  a fork every 8 to 12) takes the tips to about 35 blocks out; `reach` 50 is met only by pieces pulled straight to
  food. With mounds 42 to 82 blocks apart, webs of 35 meet when mounds are closer than about 70.
- **A lead for the open flake BUGS 167** (a web at 70 blocks against 58 allowed): "allowed" is not one number; a
  piece to food may go one piece over, big dead wood 32 over, a fork's second piece a little over.
- **Where:** `macrotermes.json:132-133`, `:145-149`; `framework/species/SpeciesProfile.java:872-905`;
  `savanna/species/SavannaNumbers.java:395-397`; `savanna/colony/GalleryWeb.java:361-364`.
- **The fix proposed:** numbers, not code: `per_workers` about 14 (14 spots at the cap, the "10 to 15" chosen
  before), the three dead reach numbers out of the termite file, `max_length` with a note. `tools/raid_balance.py`
  was worked out for 8 to 14 spots, so check it against 200 workers. My pick for the galleries: keep about 35 (it
  fits the mound spacing).
- **From:** E-4, W-12.

### R5-76 (B, proven): filling in a piece out to small food gives back its full length, though that length never counted, so every eaten branch shrinks the web's room to spread

**In play (2026-11-27):** C. Over many days a colony that eats many fallen branches stops spreading its galleries though its web is far shorter than its workers allow.

- **What is wrong:** a piece out to a fallen branch counts 0 of its length (`small_food.share` 0.0). Ten days after
  the branch is eaten the piece is filled in and its **full** length is added to "length given back"
  (`filledLength`), which the even spreading may not use. Each branch eaten and left takes its length out of the
  spreading for good.
- **Where:** `savanna/colony/GalleryUpkeep.java:379-383`, `:402-404`; `savanna/colony/GalleryWeb.java:282-289`,
  `:441-443`, `:876`.
- **The fix proposed:** one rule for "how long is this piece" that `length()` and the fill both call. My pick on the
  same path: the given-back length returns to the spreading after `fill_days` more days (real Macrotermes keep
  exploring all their lives; today that length is for growth toward food only, for ever).
- **From:** W-3.

### R5-77 (B, the path proven; how often likely): a tip turned back by a remembered attack is picked again every second and holds up the growth of the whole web for days

**In play (2026-11-27):** C. After a Matabele raid near the galleries the termites' web stops growing everywhere for days, not only toward the raid.

- **What is wrong:** each second the tip pulled hardest by grass, or else the shortest, is picked. When every way on
  from it runs toward a place where members were attacked, growth stops for that second and no try is counted, so the
  same tip is picked the next second, until the attack is forgotten (about four days from its top). A tip turned back
  by a neighbour is counted (three tries), but the tries are not saved; and a turned-back pull toward grass still
  answers "done", so the even spreading never gets its turn.
- **Where:** `savanna/colony/GalleryWeb.java:456-492`, `:603-605`, `:704-751`, `:92-93`.
- **The fix proposed:** one rule for "turned back", by an attack or a neighbour, counted in both branches and faded
  by game time (dropped after a day) so nothing new is saved; the pull answers "nothing grown" when it grew nothing.
- **From:** W-4.

### R5-78 (B, proven): a piece out to rich food is planned with neither the neighbour walls nor the attack check, and the walls between neighbours look only at the other web's pieces

**In play (2026-11-27):** C. Two colonies' galleries joined by a piece out to a log lying between them, or a new gallery dug straight across the place a raid just hit; the upkeep never walls the joined piece off while its food lasts.

- **What is wrong:** "may a gallery go here?" is answered twice. The tips ask three things (not toward an attack,
  not toward a neighbour the colony yields to, never within reach of a neighbour's gallery). A piece out to rich food
  asks only that the food itself is not kept off. And the neighbour check looks only at the other colony's pieces'
  own blocks, not its nest's own tunnels, feeding galleries or shaft. A new side of R5-26.
- **Where:** `savanna/colony/GalleryWeb.java:543-556`, `:765-781`, `:825-881`;
  `framework/colony/ForagingHoles.java:810-813`; `framework/colony/GroundReader.java:74-79`.
- **Unchecked link:** whether a tip in a real world gets within 3 blocks of a neighbour's nest tunnels (a soak with
  two colonies at the close end of the spacing).
- **The fix proposed:** one question for the web, asked by the tips and by `pieceTo` alike, answered from the other
  colony's whole list of tunnels (`Nest.getForagingTunnels`).
- **From:** W-5.

### R5-79 (B, to measure): all day every idle builder reads every cell of every gallery, about every two seconds, to find a hole to plug, though every hole is plugged

**In play (2026-11-27):** C. A steady cost by day beside a big colony, growing with the web and the number of real builders. Not measured.

- **What is wrong:** `PlugHoleGoal.canUse` → `ForagingHoles.pickHoleToPlug` walks every hole of the web and for each
  asks first "is the whole gallery out to it dug?" (one block read a cell of its whole way from the nest) and only
  then "is the hole open?" (one read, and false by day). A grown web is in the order of 50 holes times 20 to 40 cells.
  `holeToOpen` and `HoleTurns.pickTurns` have the same order.
- **Where:** `savanna/entity/goal/PlugHoleGoal.java:57-66`; `framework/colony/ForagingHoles.java:208-215`,
  `:251-269`, `:314-337`; `savanna/colony/HoleTurns.java:308-317`.
- **The fix proposed:** only the order: ask `isOpen(hole)` first. The answers do not change. The root is R5-92.
- **From:** H-2.

### R5-80 (B, proven; a new side of R5-3): mud crust, mud casts, pulp heaps and termite remains age and crumble only on random ticks, so near a spectator they stand still for good

**In play (2026-11-27):** B. Watching in spectator, an eaten fallen tree leaves its log-shaped mud cast standing for good, leftover plates of a filled gallery stay, every crust stays fresh (lightest colour, softest to break), pulp heaps do not sink and remains do not weather.

**Status (2026-11-27, `claude/review-fix-b9`): fixed: one framework part, `block/WithheldTicks`: the blocks of the tag `colonyframework:ticks_beside_spectators` (the savanna's mud crust, mud cast, pulp heap and termite remains) get the random ticks Minecraft holds back in the chunks within 128 blocks of a spectator where no other player is that near, at Minecraft's own pace (each chunk once a second, its section's palette asked first, so plain ground costs almost nothing); none with the game rule at 0. Minecraft's own blocks and the others of the mod stay as the game has them. The mound's drying asks the same judge of where the game ticks (`WithheldTicks.givenByTheGame`). Test `theLeftoversAgeBesideASpectator`. Left as Minecraft has it: more than 128 blocks from every player these blocks stand still, as crops do (no catch-up on return).**

- **What is wrong:** all of it runs in the blocks' own `randomTick` and nothing else does it, and Minecraft gives no
  random ticks near a spectator or 128 blocks from every player (R5-3). The runway crumbling is not hit: the colony's
  own check does it on the colony dice (`framework/colony/ForagingHoles.java:679-722`), two clocks for one kind of
  block. Nothing reads a crust's or cast's age for play but the player's breaking speed.
- **Where:** `savanna/block/MudCrustBlock.java:140-164`; `savanna/block/MudCastBlock.java:44-59`;
  `savanna/block/PulpHeapBlock.java:78-100`; `savanna/block/TermiteRemainsBlock.java:25-34`.
- **The fix proposed:** the one clock R5-3 proposes for the mound soil: the part that already walks these cells (the
  runway `crumble`, `DeadWorks` for the casts) dries and clears them by game time. One fix covers all the mud.
- **From:** H-5, P (its spectator list).

### R5-81 (B, likely): a far colony's hole blocks stay as the last real night left them, open by day for good

**In play (2026-11-27):** C. Standing 70 to 120 blocks from a mound, its holes left open from the last night stay open by day, day after day; on coming within 64 blocks by day a row of workers plugs holes in front of the player, and by night tonight's holes are bitten open as the player walks up.

- **What is wrong:** the night's turns are picked near and away alike, but only real workers open and plug the
  blocks. A colony counts as away also when its land is loaded and no player is within 64 blocks; then nothing
  touches its holes, and nothing on return sets them right out of sight, as the rooms are set right.
- **Where:** `savanna/colony/Galleries.java:369-379`; `savanna/colony/HoleTurns.java:99-110`, `:295-370`;
  `framework/colony/AwayLife.java:163-175`, `:553-580`.
- **Unchecked link:** whether a raid's scouts read a far termite colony's open holes.
- **The fix proposed:** no new rule: the return catch-up that sets the rooms right out of sight also sets each
  loaded hole to what the brain says now (plugged by day, tonight's turns open, kept-shut plugged).
- **Also (P-7):** for the 1,200 ticks of grace after the last player leaves neither the near nor the away parts run,
  so each leaving costs the colony a minute of the numbers' foraging (`AwayLife.java:396-438`); the first away second
  can stand for the whole grace (`FieldLife.seconds`, made for the slept night).
- **From:** H-3, P-7.

### R5-82 (B, proven): what the player sees of this half and no test checks

- **The web:** `GALLERIES`, the biggest record a colony saves, has no `sample` for `AwayKit.everyPartSavesAndLoads`
  (nor have `KING_CARE`'s numbers), and nothing reloads a web with tips, forks, small pieces and filled pieces in the
  middle of growth.
- **Holes and hidden trips:** no creature is ever put in a gallery with travellers (R5-58); no test runs a far
  colony through a night and a day and looks at the hole blocks (R5-81); `holesTakeTurnsNearFoodFirstAndMoreInTheRain`
  has a chicken sitting in a hole the builders try to plug but never checks it was not buried.
- **Food:** `dryGrassEatenByAHoleLeavesBareGroundAndGrowsBack` stops at the green tuft (R5-53); no tall tuft is
  eaten (R5-88); nothing bounds what `forageByTheLand` brings (R5-73); no two colonies at one plant (R5-74); the wood
  call's edges (a small log not called, the reach's end) are untested.
- **Alarm and soldiers:** no test that real majors stay real (R5-57), of a colony with soldiers and no workers
  (R5-69), of two troubles more than 8 blocks apart (R5-71), of a foe that stays (R5-55), of a soldier that cannot
  reach its place (R5-72); the raid defence tests would pass with workers that never run (R5-56).
- **The night:** the slept-night test never moves the day clock (R5-52); the rain tests pretend rain (R5-60); the
  long hole test waits any number of nights (R5-63).
- **The fix proposed:** add the checks to the scenes that exist (each finding names its scene); a `sample` web grown
  by hand. No new test classes.
- **From:** W-6, H-4, E-5, S-12, and the "would still pass" notes of P and Q.

### R5-83 (B, to measure): what a quiet colony pays for being ready, and what a trouble costs

**In play (2026-11-27):** C. Part of the tick time beside a termite colony, more while a trouble lasts. Not measured.

- **What is wrong:** `NestWalls.joinedCells` is a fresh flood through every nest cell with block reads, never kept;
  `Alarm.cellsNear` calls it for each trouble every second (up to 8), again whenever a foe's trouble moves (R5-55),
  and `Alarm.isBreakIn` for each block a player breaks beside the nest. A quiet colony that is here looks once a
  second over every living thing in a box 128 by 49 by 128 blocks (`Alarm.tick`). `Nest.isInside` rebuilds its whole
  set of inside cells on the first ask of every tick, and each worker's flight asks every 5 ticks, so with bodies
  about it is rebuilt every tick. A soldier at an alarm task scans for foes every tick while none is near (the
  field's comment says "every few ticks").
- **Where:** `savanna/colony/Alarm.java:286-308`, `:348-350`, `:626-645`; `framework/colony/NestWalls.java:97-121`;
  `framework/colony/Nest.java:1038-1081`; `savanna/entity/goal/DefendGoal.java:125-127`, `:181-200`.
- **The fix proposed:** measure first with a grown nest. Then: one kept copy of the joined cells (refreshed when a
  nest cell is dug or filled), the inside set kept until the nest changes, the soldier's scan every 4th tick.
- **From:** S-8, S-9, Q (its "cost of being ready").

## Grade C (part B)

### R5-84 (C, likely): a hidden termite is put back without checking its cell is open

**In play (2026-11-27):** C. Rarely, a termite put back inside a block a player placed in a gallery where a hidden termite "was"; it suffocates or walks out oddly.

- **What is wrong:** a break-in "filled at" a cell puts the traveller at the floor its clock says it reached; nothing
  checks that cell is still open. And if the insect is somehow already in the world, `comeOut` answers no and the
  trip is folded into the numbers: one worker too many.
- **Where:** `savanna/colony/HiddenTrips.java:93-101`, `:212-223`, `:314-353`.
- **The fix proposed:** step back along the run to the last open floor before the first filled cell; when the insect
  is already in the world, just drop the trip.
- **From:** H-6.

### R5-85 (C, proven): small leftovers in the web's and the holes' books

- A tip's failed tries are never cleared when it grows on, and the set of small pieces never when one is filled
  (`GalleryWeb.java:620-653`, `GalleryUpkeep.java:407-437`). `ClosedHoles.left` (saved) keeps a hole left for good
  after its piece is filled in, so the colony list keeps counting it (`ClosedHoles.java:60`).
  `HoleTurns.holesInTurn` can list a hole twice (`HoleTurns.java:117-130`). The hidden ways notice a changed web only
  by its count of tunnels, so a piece filled and one added in the same 10 seconds go unseen
  (`HiddenTrips.java:149-166`; `ClosedHoles` compares the list itself). `isLeaf` walks every piece each time it is
  asked, once a piece in loops over the pieces (`GalleryWeb.java:135-137`).
- **The fix proposed:** clear each where the piece leaves; compare the list as `ClosedHoles` does; a count of
  children beside `parents` if the web ever grows.
- **From:** W-7, W-11, H-7, H-9.

### R5-86 (C, likely; a new side of R5-19): a closed hole stays shut by game time, the foraging hours run on the day clock

- **What is wrong:** the danger that closes a hole fades on game time (`DangerMap`, half-life 24,000 ticks); sleeping
  moves the day clock, not game time. The design's "shut for about two and a half days" is about five sun-days for a
  player who sleeps each night. The turns themselves are safe.
- **The fix proposed:** none now (real termites avoid a raided site for days; a few more is still real). Count it in
  when R5-19's clock fix is built.
- **From:** H-10.

### R5-87 (C, proven; real biology): leaf litter is in the diet on paper only; in the mod's savannas there are almost no leaves it matches

**In play (2026-11-27):** C. Termites eat no leaves in the mod's savannas, though real Macrotermes eat a great deal of leaf litter besides grass and wood.

- **What is wrong:** the diet's `#minecraft:leaves` with `only_decaying` matches only vanilla leaves cut off their
  tree. The giant savanna trees, which replaced the vanilla acacias in new savannas, have their own leaves and dead
  leaves, and neither is food. `docs/DESIGN.md:15` also still lists vanilla short and tall grass, which the file
  rightly does not.
- **Where:** `macrotermes.json:87`; `savanna/block/TreeLeavesBlock.java:15`.
- **From:** E-7.

**Your call (2026-11-27):** should termites eat the giant trees' dead leaves? Today they eat no leaves in your savannas (only vanilla leaves cut off a tree count). Real Macrotermes eat a lot of leaf litter. My pick: yes, the dead leaves lying under the crowns, 2 pieces each, not "rich" food (no gallery is dug out to them).

### R5-88 (C, proven): an eaten tall tuft grows back as a short one, so the tall grass round a colony is gone for good

**In play (2026-11-27):** C. Over the first weeks the grazed band round a colony loses its tall dry grass; only short tufts come back.

- **What is wrong:** the regrowth has one `grows_back` block, `dry_grass`. A tall tuft (8 pieces) comes back short (4)
  and grows tall again only by bone meal. Perhaps a tenth to a fifth of the supply (an estimate).
- **Where:** `savanna/colony/BarePatches.java:115`, `:137`; `SavannaNumbers.Regrowth`.
- **The fix proposed:** the regrowth puts back the kind of plant that was eaten, from the tuft's own record. No new
  number. My pick: tall comes back tall (real savanna grass regrows to its full height).
- **From:** E-6.

### R5-89 (C, to measure): the wood call measures every piece of dead wood in its ring every minute, known or not

- **What is wrong:** `DeadWoodCall.listen` runs once a minute over every second column within 20 blocks of every dug
  hole and tip, and for each dead wood block follows the whole piece of wood (up to 4,000 blocks, 6 neighbour reads
  each) before it asks whether the colony knows it already. `isBig` measures again from scratch every 10 seconds while
  a find past the budget waits. Land not loaded is never read. Likely well under a millisecond a minute.
- **Where:** `savanna/colony/DeadWoodCall.java:60-113`, `:143-173`.
- **The fix proposed:** skip the wood the colony already knows before measuring; `isBig` uses the size learnt when
  the wood was called.
- **From:** E-8.

### R5-90 (C, proven): two lists that must be kept in step by hand

- **"Small food"** is judged by `nest.new_holes.small_pieces` 8 for the spot count and by
  `nest.galleries.small_food.pieces` 8 for the digging budget; change one and a branch is small for one rule and big
  for the other (`ForagingHoles.java:889-899`, `GalleryWeb.java:292-295`). One number.
- **"The wood termites eat"** is listed in the species file's food list, in the block tag `antcolonies:termite_wood`
  (the mud plates and the dead works, which add fallen branches and hay by hand) and in the classes of
  `DeadWoodCall.isDeadWood`. A new dead wood added to the file but not the tag gets eaten but no mud plate. The tag
  becomes the one list.
- **Checked and fine:** no vanilla log, plank or stripped log and no living tree's wood is in any of the three.
- **From:** E-9, E-10.

### R5-91 (C, proven): the savanna number records check no ranges, and some defaults in code differ from the file's sense

- **What is wrong:** nothing in `SavannaNumbers` rejects a bad number. `soldiers.minors_per_major` -1 divides by 0 and
  asks for about 2 billion majors (`SavannaNumbers.java:259-265`); `regrowth.grows_back` misspelt makes every eaten
  tuft vanish for good with no message; `regrowth.ticks` 0 regrows at once (R5-34's family). `WoodCall.DEFAULT` has
  `reach` 0 when the whole block is missing but 16 when the block is there without `reach`.
- **The fix proposed:** a small check per record with a load message naming the field; one default a field.
- **From:** E-11.

### R5-92 (C, to measure): every gallery piece stores its whole way from the nest's shaft

- **What is wrong:** a piece's record is its parent's whole tunnel plus its own blocks. A web of about 50 pieces
  stores about 2,000 block positions (about 16 KB a colony in the save, written on every growth and fill) where its
  own blocks are about 200, and every "is this piece dug?" reads the whole way again (R5-59, R5-79).
- **Where:** `savanna/colony/GalleryWeb.java:995-1001`; `savanna/colony/Galleries.java:413-423`.
- **The fix proposed:** measure first. If it matters: save each piece's own blocks and its parent, build the whole
  way on load, and keep "dug" per piece.
- **From:** W-8.

### R5-93 (C, proven; a new side of R5-6): filling a piece in reads the blocks beside each filled block with no loaded-land check

- **What is wrong:** `fillIn` checks each block it fills is in loaded land; `soilFor` then reads the block below and
  the four beside it. On a chunk edge that reads the next chunk, which loads it on the spot; the write also tells the
  neighbours. Only when a piece lies on the edge of loaded land (a low view distance).
- **Where:** `savanna/colony/GalleryUpkeep.java:329-333`, `:373-400`.
- **The fix proposed:** R5-26's shared question once it exists; until then `LandReady.at` on each side read.
- **From:** W-9.

### R5-94 (C, proven by Minecraft's source; a new side of BUGS 357): the termites' fight clocks run at half the speed their names say

- **What is wrong:** Minecraft runs a mob's goals every other tick unless the goal asks for every tick
  (`Mob.java:779-790`). `DefendGoal` and `RetreatGoal` do not ask and count in goal ticks: `BITE_INTERVAL` 20 is a
  bite every two seconds, `GIVE_UP_TICKS` 300 is 30 seconds, while the seal beside it counts real ticks. The fights
  were measured in play with these clocks, so the balance holds them; whoever tunes "20 ticks" gets double.
- **Where:** `savanna/entity/goal/DefendGoal.java:36`, `:122-123`; `savanna/entity/goal/RetreatGoal.java:27`, `:81`.
- **The fix proposed:** count these by game time, as the colony parts do (keeping today's real pace).
- **From:** Q-13.

### R5-95 (C, likely): beyond 256 mud casts the wood eaten through is no longer "inside", so a big dead giant's far parts stop being eaten by day

- **What is wrong:** the casts joined to a feeding gallery are gathered by a search that stops at 256
  (`savanna/colony/FeedingGallery.java:44`, `:85`, "a fallen tree is about 8 blocks"); a dead giant is hundreds of
  blocks. Past the cap the wood beside the rest is not feedable from inside. 256 blocks of wood is about 160 days of
  a full colony, so it may never show.
- **The fix proposed:** none now; raise or drop the cap if a soak shows it.
- **From:** E-13.

### R5-96 (C, proven): the grass's drying and regrowth have no socket for the shared season

- **What is wrong:** real savanna grass greens in the wet season and dries in the dry one. Here drying is a fixed
  random-tick chance and regrowth a fixed 3 days. Nothing reads the season by itself (the rule is kept), but no
  socket is left. R5-53's fix moves drying to the bare patches' clock, which is the natural place to ask the shared
  season source.
- **From:** E-12.

### R5-97 (C, proven): comments that disagree with the code

- `savanna/colony/Galleries.java:40-41`: the holes along the tunnels "stay usable for ever"; a filled piece's holes
  are taken out for good. `GalleryWeb.java:465`: "(no dead end: it may grow there later)", but a try was just counted.
- `savanna/SavannaEvents.java:200`: a comment about the raid view stands alone above the crocodile's handler; the
  class comment (`:26-29`) names only the termites' strength and the trees.
- `savanna/colony/Alarm.java:52-53` ("a major in the opening itself": the single holder went in step 5), `:55-56`
  (`keepsShut` does not exist; the alarm plugs no hole), `:330-331` ("called by the queen": it is the part's tick),
  `:480-481` ("the others feel that in turn": R5-67).
- `savanna/entity/TermiteSoldierEntity.java:65-68` (a soldier holding a place "stands out in the open": its place is
  under cover since step 5), `:154-167` and `:340-341` (damage 3 and 4; the species file's 1 and 1.5 count).
- `savanna/command/TermiteCommands.java:60-62`, `:74-76`: two comments sit on the wrong command.
- `savanna/colony/FeedingGallery.java:36`, `:43`: "a fallen tree is about 8 blocks" (the dead giants are far bigger);
  `macrotermes.json:132`, the new-holes note (R5-75); `docs/DESIGN.md:15`, `:19` (R5-87, R5-90).
- `savanna/block/TermiteHoleBlock.java:15-22` says it sits on every chimney; no game code places it any more (the
  mouths are `TermiteMouthBlock`); it is kept for old worlds and tests.
- `savanna/colony/Raid.java:1791-1792`: the feeders "feel the raiders and flee"; they do not (R5-56).
- **From:** W-10, S-11, E-14, H-8, Q-2.

## What is solid in part B (so no later session "fixes" what works)

- **The web's planners are pure and proven:** `planArms`, `grow`, `pickTip`, `planPiece` on made-up landscapes
  (`GalleryProofTest`); a route never opens to the air, never runs under the food, never two pieces at one hole.
  Every block the web reads to plan goes through `GroundReader` with a loaded-land check; the away side reads no
  block. Every random pick uses the colony's dice; no statics in the gallery classes.
- **Nobody is walled in:** `fillIn` refuses while anything living stands in or next to the piece, while a hidden
  traveller walks through it, while a hole of it is open or a player could see it; the raid seal waits while
  anything living stands in its cell; the plug never buries anything not of the colony and nudges nestmates down
  first. (A raider standing farther in than the hole being shut is BUGS 361's pocket.)
- **One judge for "may this hole open?"** (`ForagingHoles.mayOpen`): the night's turns, the danger closing and the
  plug goal never undo one another. Near and away pick the night's holes the same way with the colony's own dice; the
  pick is saved. A plug claim lasts 600 ticks and cannot stick (not R5-5's shape). Dawn waits until nobody is outside
  or 30 seconds.
- **One question for "is it foraging time"**, near and away (`Colony.isForagingTime`); a bed, `/time add` and
  `/tick rate` are right (R5-52 is the two cases that are not).
- **A piece is never counted twice inside one colony:** one count of pieces cut a plant for real and counted
  foragers, saved; a night that is near for its first half and away for its second keeps its holes and its counts.
  Every food block is used up exactly; none gives pieces for ever.
- **The diet is dead plant matter only:** dry grass only when dry, no living tree's wood, no vanilla log or plank; a
  player's wooden house is never eaten. Hay bales are (on purpose). The food blocks burn as they should.
- **Hidden trips keep the layers apart:** the navigator only asks "is this cell hidden?"; the same insect comes back
  with its goal and route; it is saved with the colony, folded in when the colony goes away, and no wall closes
  over a traveller.
- **The alarm's own book is safe:** the list of troubles (at most 8) is never changed while walked; nothing a
  soldier's goal holds is given back only in `stop` (held cells lapse, beats are freed when the holder is gone); a
  spectator never sets off the alarm and no animal's steps do; walking and sneaking raise nothing; troubles are not
  saved and the colony is calm after a load.
- **The soldier's goal table does not flip-flop** (reader S's file has it), and `GoInsideGoal` and `WalkBeatGoal`
  read one judge (`minorsOut`). The worker's plug, shape, nurse and seal goals share one priority, so none takes a
  worker from another in mid-job.
- **Counts across the raid's seam:** a termite taken by a raider leaves the count once, as a body or as a number; a
  stung termite wanted by a raider and a nestmate is counted once.
- **Reload in mid-night:** the night's holes, the round, the cut counts, the food map, the hidden travellers and a
  packed seal are saved; what is not saved (the plug grace, claims, part-trips) times out or starts again harmlessly.
- **The commands** all need permission 2, clamp their numbers and stay near the player.
- **Layering and dice:** no goal here steers with vanilla navigation or `MoveControl`; the species reaches the brain
  only through its parts, questions and news; nothing reads the season by itself.

## Real biology, checked in part B (no finding)

- **Foraging:** holes opened from inside at night and in wet weather, plugged before dawn with soil from inside, a
  share of the holes used each night, food first with a few rested holes to explore at the web's edge, a raided hole
  closed at once and avoided for days, a site raided twice left for new holes farther on, soil sheeting over what
  they eat, one piece a trip: all as real Macrotermes. One hole per 6 foragers is a pick, not a measured figure.
- **Soldiers:** 1 in 10 of the colony, 3 minors to a major, minors out with the foragers and along the trails, majors
  at the nest's doorways; six taps in 24 ticks is about 10 a second, near the real 10 to 20.
- **The web:** shallow galleries tens of metres out with many small holes, grown toward food and left where there is
  none; ranges that meet their neighbours'. No paper was checked for a reach in metres.
- **Scaled on purpose:** regrowth in days, danger fading in days, a colony of 200.
