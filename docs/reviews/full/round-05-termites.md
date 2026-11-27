# Full code review, round 5: the termites (reading round)

The round is read in two halves (the user, 2026-11-27: "split this up, I'm expecting a lot of bugs"). **Part A, the
mound itself, is below (read 2026-11-27).** Part B (galleries, hidden trips, foraging holes, feeding, alarm,
soldiers) is not read yet; its findings will be added at the end of this file and carry on the numbers.

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

- **Checked by the lead with Minecraft's code:** `TimeCommand.setTime` (`:64-71`) sets every level's day time to the
  number given, so `/time set day` puts the clock back to 1000. `DeadMounds.days` (`savanna/colony/DeadMounds.java:98-100`)
  then reads 0 for every mound that died later (`:184`, `:279`).
- **The fix proposed (my pick):** keep the game time of death beside the day clock and use the larger age: `/time add`
  still moves the wear on (it is used to watch it), and `/time set` back no longer stops it. The trees count their
  days the same way (`tree/Forest.java:361`, `:670`); the same fix fits there.
- **From:** D-3, X-5, Y-6.

### R5-20 (B, proven): when a termite colony dies, the bare earth round its holes stays bare for good

**In play (2026-11-27):** B. Round every mound that died in play, little patches of coarse dirt round the old foraging holes never green over, while the holes themselves and the mound beside them grass over.

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
