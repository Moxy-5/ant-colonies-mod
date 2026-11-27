# Round 5a, reader M: the mound's plan and the colony's hold on it

Read at commit `4dcd1fe6` in `C:\Ant Mod` (branch `claude/motion-glue`). No code changed, nothing run or measured.
Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`.

## M-1 (A, proven for the list command and the near tick; likely for the Matabele bite check): asking a far mound for its cells can still grow its whole plan on the game's own thread

- **What is wrong:** a mound's full plan (every cell of every stage, "about a second" of work, 1.2 to 1.8 s in the
  user's logs of 2026-11-10) is meant to be grown only in the background. The 2026-11-10 fix made the mound's stage and
  foot reach answerable from small saved counts, and keeps the plan only while a player is within 128 blocks
  (`keepShapes`). But `Mound.getParts` (the list of every cell) still falls back on growing the plan right there, on the
  server's thread, whenever the stage it last worked out differs from the stage now (always true for the first ask
  after the world opened: `cachedStage` starts at 0) and no player is within 128 blocks (`held` is null) and the plan is
  not among the 24 last grown (`MoundPlan.KEPT`). If the plan is being grown in the background right then, it waits
  for it (`later.join()`), which can be longer still when other mounds are queued ahead of it on the 1 to 4 grower
  threads.
- **What the player would see:** a freeze of one to two seconds per far termite colony when:
  (1) the owner types `/antcolonies list` (proven: every colony's line asks `describe`, which counts the finished
  blocks through `getParts`; a world with 10 far termite colonies after a restart could freeze for 10 s or more);
  (2) rarely, a termite colony ended in its first second after the world opened, or by `/antcolonies kill` from far
  (its dead mound is written down from `getParts`, `DeadMounds.addMound` and `DeadWorks.colonyEnded`; a colony that
  dies away is only ended once a player is near, R1-27, so `held` is usually there; new-land dead mounds wait for the
  plan first, `NewLandMounds` 199-213);
  (3) likely: a Matabele raider deciding whether it may bite soft termite soil asks every termite colony within 48
  blocks (and 96 for plugs) for its cells.
  The 2026-11-10 note names "a colony right beside the player when a world is opened" as the only known leftover; the
  paths above are new sides of it.
- **Where:** `savanna/colony/Mound.java:443-459` (`getParts`), `:278-285` (`stages`/`stage`: `held` else
  `MoundPlan.growth`), `:153-160` (`describe` -> `countFinished` 541-549 -> `getParts`), `:292-299` (`keepShapes`,
  128 blocks), `:233` (`cachedStage = 0` once the soil counts become known, so the next ask builds the cell map again);
  `savanna/colony/MoundPlan.java:133-156` (`growth`: kept, else `join()` on the background job, else `grow` on the
  spot); callers: `framework/command/ColonyCommands.java:260-291`; `savanna/colony/SavannaParts.java:64-65`;
  `savanna/colony/DeadWorks.java:66-67`; `savanna/entity/MatabeleEntity.java:564-573` and `:816-823`.
- **The proof:** after a reload `cachedStage` is 0; `getStage` answers at least 1, so `h != cachedStage` and
  `stage(h, numbers)` runs; for a colony with no player within 128 blocks `keepShapes` has set `held = null`, so
  `stages` calls `MoundPlan.growth`; that returns at once only when the key is in `GROWN` (24 kept, least recently used
  thrown out), else it joins the background job or grows on the spot. Nothing on the three caller paths asks whether
  the plan is kept first. `describe` is reached for every colony of the dimension by the list command.
- **Unchecked link:** whether a Matabele raider can in play stand within 48 blocks of a termite colony that has no
  player within 128 blocks (a raider is a real mob only in ticking land, which can reach past 128 blocks at a
  simulation distance of 10 or 12 chunks). Not measured how long a far colony's growth takes today.
- **The fix proposed:** one rule in one place, no new number: `getParts` never grows. If the plan is not kept it asks
  for it in the background (`growthSoon`) and returns the last cell map it has (empty after a reload). Callers that need
  the true cells while far (the dead mound writer) wait for it the way `NewLandMounds.deadTree` already does (BUGS 214:
  "check again where it is used"), or the colony's end is put off one look until the plan is there; the list line says
  "plan not grown yet" instead of counting. A test: a colony with no watcher and a dropped plan; `getParts` and
  `describe` return within a few milliseconds.
- **The near side (the 2026-11-10 "known leftover"):** `Mound.tick` (465-476) asks for the plan in the background
  (`growthSoon`) and then, on the very next line, asks `getParts`, which waits for that same background job. So the
  leftover is not a race that sometimes loses: whenever the plan is not kept yet, the tick always waits the whole
  growing time. Returning from `tick` while `held` is still null (and trying again next second) removes it.

## M-2 (B, likely): the stage moves on by "nothing built lately" clocks that do not know a layer of soil must set first

- **What is wrong:** the user's rule is that wet soil must firm up before more soil goes on it
  (`TermiteMoundBlock.canBuildOn`: the block under must be finished and at least "setting", age 1). Setting comes from
  Minecraft's random ticks: 1 chance in 20 per random tick (`MoundSoil.AGE_CHANCE[0]`), and a block gets a random tick
  about every 68 s at the normal speed, so a fresh block takes about 23 minutes on average to set, often much longer.
  The mound has three other judges of "the termites are stuck", all far shorter, none of which knows a cell is only
  waiting for the soil under it to set:
  (1) after 1 minute with nothing built (`FALLBACK_AFTER_TICKS` 1200) the real builders may build from outside at night
  or from 2 blocks away (`mayUseFallbacks`), which the comment says "the plan never needs";
  (2) after 5 minutes (`STALL_TICKS` 6000) the stage moves on unfinished (`getStage` 359-364);
  (3) near a player, since the R1-36 fix the builder numbers work near too, through the same catch-up as while away
  (`BuildLife.tickNear`, `MoundAwayBuilding.buildMound` 90-95): a pass that builds nothing and was not stopped by a
  player's eyes calls `nextStage(level, stalled = true)` at once, so the stage can climb several stages in a second
  toward the earned one while a fresh block dries.
  The numbers also put their soil in already set (`MoundAwayBuilding.setAge`, at least 1), so the drying rule binds only
  the real termites' own blocks.
- **What the player would see:** termites building from outside on the mound's face at night as a matter of course (the
  "surprise" fallback), and a mound whose plan races ahead of what is built: each stage change crumbles the spire
  pieces of earlier stages that the new stage has no cell for (`crumbleLeftovers`), and the order the plan was proven
  buildable in (one whole stage after the other, `MoundGrowth`) is no longer the order it is built in. Not seen in play
  by this reader; nothing measured.
- **Where:** `savanna/colony/Mound.java:66-78` (the two clocks), `:345-369` (`getStage`), `:392-402` (`nextStage`),
  `:756-759` (`mayUseFallbacks`); `savanna/block/MoundSoil.java:25-30`, `:45-50`; `savanna/block/TermiteMoundBlock.java:79-103`;
  `savanna/colony/MoundAwayBuilding.java:87-95`, `:163-167`; `framework/colony/BuildLife.java:115-132`.
- **The proof:** walked: a real termite's block is put in at age 0 (`TermiteMoundBlock.addLayer` 143, then 138 up to
  layer 5; nothing sets an age); `spotIn` (`Mound` 607-639) offers a cell over it only once `canStartAt` is true, which
  needs age 1; progress is only stamped by `addLayer`, `shaped` and `blockDug` (405-407), so a mound whose next cells
  all wait for setting soil stamps nothing. Then (1) after 1200 ticks and (2) after 6000 ticks fire; (3) the numbers'
  pass finds `spotIn` null for every such cell (`SKIPPED`, not `waited`), so `nextStage(level, true)` moves on.
- **Unchecked link:** how often, in play, a whole stage has nothing to build, carve or clear for a minute (carving a
  finished soil block into a tunnel does not wait for setting, `MoundShaping.isCarveJob` 93-94, and stamps progress,
  so a stage rich in tunnels keeps the clocks fed for a while). The game tests switch the near builder numbers off by
  default (`BuildLife.worksNear` is false on the test server), so judge (3) is in no mound test but one
  (`TermiteAwayTests` 256).
- **The fix proposed:** fewer rules, not another number. "Stuck" should mean "a job is left that nobody can reach"
  (the `unreachable` marks and the plan's own jobs), never "nothing was built lately": a cell whose only wait is soil
  setting under it is not stuck. Then the stage moves on only when it is done or every job left is unreachable, one
  judge used by both the real builders and the numbers (`nextStage` asks it; the numbers' `stalled` flag goes). The
  fallbacks then come only for real surprises, as their comment says.
- **Owner's call:** today the mound's stages and the outside fallbacks run on 1- and 5-minute idle clocks, so they set
  the pace whenever soil is drying; the pick: the stage waits for its soil to set (the user's own rule), fallbacks only
  when a spot is truly cut off. This may make a watched mound's stages move on more slowly.

## M-3 (B, to measure; may be A): by day every idle termite's "does the mound want soil?" walks every build spot and its 26 neighbours

- **What is wrong:** `wantsSoil` counts every build spot that "can be worked on now". By day (the termites keep a
  schedule) a spot counts only if one of its 26 neighbours is hollow inside (`isWorkableNow`), and each neighbour's
  check (`MoundReach.isHollowInside`) asks the mound for its whole cell map (`Mound.partsOf` -> `getParts` -> `getStage`
  -> `getEarnedStage` -> `getAllowance` and a walk up the stages' soil counts) and the nest whether the cell is inside.
  It counts all of them, with no stop once the answer is known. It is asked by every idle digger about once a second
  (`DigGoal.canUse` -> `Nest.findDigTarget` -> the mound's `digTarget` -> `findDigTarget` 827) and again on every bite
  (`takePellet` 891), and `pickBuildSpot` does the same walk for each builder (712). The same "work out the stage again"
  sits under every one of the many `getParts` calls a tick (`isClearJob`, `isPieceJob`, `MoundShaping`, the reach rules).
- **What the player would see:** tick cost by day beside a big mound that grows with the number of open build spots and
  idle workers. Not measured: about spots x 26 x (one stage walk, two map looks, a block read) per call, several calls a
  tick with 50 real workers.
- **Where:** `savanna/colony/Mound.java:683-698` (`isWorkableNow`), `:799-810` (`wantsSoil`), `:706-732`
  (`pickBuildSpot`), `:443-459` (`getParts` calls `getStage` every time), `:345-369`, `:306-336`;
  `savanna/colony/MoundReach.java:77-81`; `framework/entity/goal/DigGoal.java:64-80` (`looksForWork(20)`).
- **The proof:** the calls are walked above; nothing keeps the "workable spots" between calls in a tick
  (`getBuildSpots` keeps only the spots, 582-584, and is cleared after every layer, 785).
- **The fix proposed:** one list a tick: work out the spots that can be worked on now together with `getBuildSpots`
  (kept for the tick like it), so `wantsSoil` is a count and `pickBuildSpot` a walk over a short list; and let
  `getParts` keep the stage it found for the tick (cleared at the two places that move the stage, `getStage`'s own
  check and `nextStage`). Measure with the termite stress scene before and after.

## M-4 (C, owner's call; realism): the mound's building pace ignores rain and the season, and leaves no socket for the season source

- **What is wrong:** the colony earns mound blocks at one steady rate at every hour of every day
  (`getAllowance`: `blocks_per_day` + `blocks_per_day_per_worker` x workers, per tick of game time). Real Macrotermes
  build mostly when the soil is wet: after rains and in the rainy season (mound repair and growth follow the rains;
  dry-season building is slight). I am sure of the direction, not of a number. The mod's own rule says behaviour real
  insects change by season asks the one shared season source, or leaves a clear socket for it; the windows rule
  (`mayOpenWindows`, night or rain) already asks the colony's schedule, but the pace does not.
- **What the player would see:** a mound that grows as fast in a dry spell as after a storm; nothing wrong today.
- **Where:** `savanna/colony/Mound.java:301-319`.
- **The fix proposed:** no new number now: route the pace through one place that later asks the shared season and
  weather source (for example a factor from the framework's season answer, 1 today), so seasons need no mound edit.
- **Owner's call:** today a steady pace; the pick: a socket now, the wet-season boost when seasons come (M9.5).

## M-5 (C, proven): the saved soil counts are trusted for good, even after the mound's plan has changed

- **What is wrong:** once a colony's soil counts per stage are known (`wallsKnown`) they are never checked again in that
  sitting (`numbers.equals(shapesFor)` returns early), and the save writes them back (`save` 1027-1028). After a world
  opens, a colony's counts come from its save before its plan is grown (`wallsIfKnown` is empty at start, so
  `savedWalls` is used), and the freshly grown counts (`MoundPlan.WALLS`) are never looked at again. So after a mod update
  that changes the plan (it has happened: BUGS 82 changed 93 of 8,000 mounds), every old colony keeps the old counts
  forever: its stages are earned at the old thresholds while the cells come from the new plan.
- **What the player would see:** nearly nothing (a stage earned a little early or late; the colony list's "30/36 blocks
  finished" with a total from the old plan).
- **Where:** `savanna/colony/Mound.java:215-235`, `:1023-1043`.
- **The fix proposed:** in `wallsKnown`, prefer the grown counts whenever `wallsIfKnown` has them (one map look), and
  drop the saved ones then.

## M-6 (C, proven): after `/reload` with a new `max_height`, a watched mound keeps its old plan

- **What is wrong:** `held` (the plan of every stage) is set once and only let go when no player is within 128 blocks
  (`keepShapes`); `tick` refills it only when null. Nothing compares it with the numbers. After a `/reload` that
  changes `max_height`, `wallsKnown` notices (new soil counts, `cachedStage = 0`) but `stages` keeps returning the old
  `held` list, so the cells come from the old height's plan and the stage index is cut to its length.
- **What the player would see:** only the owner, tuning the species file and reloading beside a mound: a mismatch
  between the stage shown and the shape built until he walks 128 blocks away.
- **Where:** `savanna/colony/Mound.java:278-299`, `:465-469`.
- **The fix proposed:** keep `held` under the numbers it was made for (like `shapesFor`) and let it go when they differ.

## M-7 (C, likely): a mound plan that fails to grow is retried every second with no log line, and a waiting tick would crash

- **What is wrong:** `grownSoon` drops a failed background job without a word (`whenComplete` ignores `failed`), so
  the next second asks again and grows it again, for ever. If the server thread is waiting on that job at the time
  (`growth`: `later.join()`), the failure is thrown on the server thread inside the colony's tick, and nothing above it
  catches it (`ColonyManager.tickColonies`). No path that throws is known today (a stuck stage is logged, not thrown,
  `MoundGrowth` 111), so this is a guard, not a seen bug.
- **Where:** `savanna/colony/MoundPlan.java:141-143`, `:213-230`.
- **The fix proposed:** log the failure once with the seed, and remember the key as failed so it is not retried every
  second; a waiting caller gets "no plan" instead of the exception.
- **Also in the store (memory, not lag):** the kept plans (24, "some 2 MB" each by its own comment, about 48 MB) are
  static, so they stay held after the player leaves a world for the menu; `WALLS` and `FOOT_REACH` gain an entry for
  every mound grown in the sitting and are never trimmed (small: a few dozen numbers each). The comment on `KEPT` still
  says "a mound every 80 blocks"; places are 42 to 82 apart and about 1 in 3 holds one (`MoundPlaces` 33-45).

## M-8 (C, likely): `max_height` 0 in the species file crashes the colony's tick (a new side of R1-4)

- **What is wrong:** the mound numbers have no range check (`SavannaNumbers.MoundNumbers.CODEC`). With
  `max_height` 0 the shape divides by 0 stages (`MoundPlan.Builder` 325) and, if the grown list has only index 0,
  `wallsAt` / `footReach` read index 1 (`Math.max(1, Math.min(h, 0))`): out of range on the first ask (the exact
  place it breaks is not walked through `MoundGrowth.grow`). A `gallery_radius` of 0 or less leaves only the short
  shaft under the nest as galleries (`MoundPlan.galleries` 754-774), so the colony runs out of soil without a word.
- **Where:** `savanna/species/SavannaNumbers.java:165-172`; `savanna/colony/Mound.java:256`, `:271`.
- **The fix proposed:** the same range check R1-4 proposes for the shared numbers (`max_height` at least 4, the
  shape's own floor in `MoundPlan.Builder` 343; radius and levels at least 1), failing the file load with a message.

## M-9 (C, proven): comments that disagree with the code

- `savanna/block/TermiteMoundBlock.java:120-131`: the comment of `addLayer` ("Adds one layer of soil...") sits above
  `isRoomForSoil`, stacked on that method's own comment; `addLayer` (132) has none.
- `savanna/colony/Mound.java:73-77` says the plan "never needs the fallbacks"; see M-2, it does whenever soil is drying.
- `savanna/colony/MoundPlan.java:101-104`: "24 since new land has a mound every 80 blocks" (see M-7).

## Balance and real biology, checked (no finding)

- **Pace:** 2 blocks a day plus 0.25 a worker; at the 200-worker cap that is 52 blocks a Minecraft day (20 minutes), so
  the full mound (about 1,050 blocks of soil by `docs/HISTORY.md`; not counted for any seed here) takes about 20 days,
  some 7 hours of play at full size, longer for a young colony. The design asks "20+ hours"; with the young years
  counted it is about right, but a grown colony builds a full mound faster than the design's figure (my estimate, not
  measured).
- **Soil:** the galleries give about 1,240 blocks (3 levels, rings out to 13), above the full mound's soil;
  `MoundPlanTest.theGalleriesGiveEnoughSoilAndAreDugFromTheShaftOutward` checks this for its seeds. Later repairs get
  their soil from any other digging while the mound wants soil (`takePellet`).
- **Setting:** about 23 minutes (a Minecraft day) for fresh soil to set before the next layer, from random ticks; real
  bolus walls firm up within hours to a day, so the order is right. It stops entirely if a world sets
  `randomTickSpeed` to 0 (then no wall rises above its first layer; the stall clock of M-2 still moves the stages).
- **Scale:** at the mod's insect scale (1 block = 30 mm) a 32-block mound is about 1 m tall and some 0.7 m across;
  real mature Macrotermes mounds are about 2 to 3 m (M. michaelseni) and up to 6 to 9 m (M. bellicosus), several metres
  across. The mound is a young real mound at insect scale, which the world's own scale makes unavoidable; no change
  proposed, said only so nobody mistakes it for a real full-size number.
- **Shape:** a mound closed at the top with a hollow central shaft and buttresses is true to M. michaelseni and
  M. bellicosus; the open chimney mouths are closer to the open-vent mounds of other Macrotermes (M. subhyalinus,
  M. jeanneli). The user chose them; I am not sure enough to call it wrong.

## What is solid

- **The plan is pure and the same every time:** `MoundPlan` takes only the colony's seed and `max_height`, draws all its
  dice first with one seeded `java.util.Random` (so a mound keeps its look as it grows) and never touches the world;
  growing it on background threads is safe (`TermiteRampBlock.shapeFor` and `TermiteSpireBlock` widths are pure). Its
  cache key (`max_height` + seed) is exactly what the shape depends on.
- **The stage needs no plan:** the stage and the foot reach are worked out from small soil and reach counts that are
  saved with the colony (`moundWalls`, `moundReach`), so a far colony asked for its stage or reach (scouts, new land,
  `livingMoundNear`) never grows its plan (the 2026-11-10 fix holds for those paths).
- **No colony dice in the wrong place:** nothing in this slice rolls `level.getRandom()`; the soil's setting uses the
  block's own random tick, which is right for a block. Nothing reads the season by itself. The mound reaches the brain
  only through its `SavannaParts` row (ticks, news, hooks, questions, save).
- **Build rules agree:** what is left to do in a cell is judged in one place, `jobAt`, used by the stage check, the
  real builders' shaping and the numbers' builder alike; "is this wall done" (`isWallDone`) and `jobAt` agree on soil
  and on the player's own solid blocks, which are left alone and counted as done.
- **The player's builds are safe:** only empty cells, plants, wet mound soil and mud crust are built into; only the
  mound's own pieces are reshaped or crumbled; a player standing in a cell stops a layer (`addLayer` 769-774), termites
  in it are lifted onto the new layer.
- **Land not loaded is not read** by the stage check, the spot list, the crumbling of old pieces, the clear-out search
  or the finished count (each asks `LandReady` per cell first).
- **Claims are bounded:** build and soil claims run out by themselves (600 ticks), the "unreachable" marks after 200,
  the pellet counts are capped at 64 entries; none of them is saved, and losing them on a reload costs nothing.
- **Wet soil is honest:** a block still being built falls away (no drop) if what holds it goes, a finished block
  stays; a block is built only on soil that has set (the user's rule), and wet soil is never stacked.
- **The galleries** are dug in an order where every cell touches one dug before it (unit-tested), cached by the shaft
  bottom they hang from.

Read in full: `savanna/colony/Mound.java`, `MoundPlan.java`, `MoundReach.java`; `savanna/block/TermiteMoundBlock.java`,
`MoundPieceBlock.java`, `MoundSoil.java`. Read in part: `savanna/colony/SavannaParts.java` (the mound row, 60-118),
`MoundAwayBuilding.java` (40-185), `MoundShaping.java` (`isCarveJob`, 88-102), `MoundGrowth.java` (`crumblesAway`,
`isNear`), `NewLandMounds.java` (199-213, 355-366), `DeadWorks.java` (60-70), `MatabeleEntity.java` (560-578,
808-826), `worldgen/MoundPlaces.java` (25-50), `species/SavannaNumbers.java` (`MoundNumbers`), the species file's
`mound` and `colony` sections; framework `ColonyManager.java` (50-170), `BuildLife.java` (95-230),
`ColonyCommands.java` (255-300), `DigGoal.java` (55-95), `Nest.java` (300-336); the test `MoundPlanTest` (163-180) and
the mound game test names. Not read: the bodies of `TermiteMoundTests`, `TermiteCathedralTests`, `TermiteHillTests`,
`MoundGrowthTest`; `MoundGrowth.grow` (so M-8's exact break point is not walked).
