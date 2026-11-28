# Round 5b, reader P: the food loop over a day and a night

Read at commit `a7c0a9f0` in `C:\Ant Mod` (branch `claude/motion-glue`). No code changed, nothing run, nothing measured.
Short paths: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`. `mc/...` is Minecraft's own 1.21.1 source as unpacked for this round.
Builds on round 1 (R1-35, R1-38, R1-26, R1-29) and on part A (R5-2, R5-3, R5-19) and does not write them again.

The question followed: does a termite colony's foraging hold together from end to end, hour by hour, watched or not?

Words used: **near** (or **here**): a player (any game mode) is within 64 blocks of the mound's middle (80 to stay) and
the middle is in loaded land; the colony's brain then runs its "near" parts once a second and real termites walk.
**Away**: anything else; the colony lives as numbers and no block is touched. **Numbers**: the workers that are only
counted, not bodies (a colony of 200 has at most 50 bodies). **Day clock**: the time of day, which a bed and
`/time set` move. **Game time**: the count of ticks the world has run, which never jumps. **Random tick**: Minecraft's
slow clock for blocks; it runs only in chunks with a player who is not a spectator within 128 blocks
(`mc/server/level/ServerChunkCache.java:367-375`, `mc/server/level/ChunkMap.java:983-990`).

Findings: P-1 (A) the day clock set back or stopped; P-2 (B) the night's holes bind only the numbers; P-3 (B) two
judges of rain; P-4 (B) the holes are opened last and one at a time; P-5 (B) the by-day record and the raid; P-6 (B)
the numbers cut in front of a spectator; P-7 (C) the minute of grace. Then the sums (path 7), the spectator (path
5), save and reload (path 6), what is solid, and what was read. In the table: step 1 is P-1 and P-3, step 2 is P-3,
step 3 is P-4, steps 4 and 6 are P-2, step 7 is P-6 and E-2, step 10 is H-3, step 11 is E-1.

## The night, step by step

| Step | Who decides | Which clock or signal | Where | Near / away |
|---|---|---|---|---|
| 1. It is foraging time | `Colony.isForagingTime` | **day clock** (`level.isDay()`, plus "before dawn" from 22000), or rain **anywhere in the world** (`level.isRaining()`) | `framework/colony/Colony.java:433-440`, `464-470`, `555-565` | the same one question near and away |
| 2. The night's holes are picked | `HoleTurns.round` then `pickTurns`, once when step 1 turns true | step 1; rain here asked a second way (`Colony.rainsNow`: rain **and** a biome with rain) | `savanna/colony/HoleTurns.java:100-110`, `295-370` | near: holes read from the world (`308-317`); away: holes last seen dug, food from memory (`300-306`, `373-390`). The pick is saved (`427-428`) |
| 3. A hole is bitten open, from inside | a builder's `DigGoal`, handed the hole by `Nest.findDigTarget` **after** every gallery block still to dig | step 1 and step 2 (`ForagingHoles.mayOpen`) | `framework/colony/Nest.java:294-328`; `framework/colony/ForagingHoles.java:236-258` | near only (real workers). Away no hole block is ever changed |
| 4. A forager decides to go out | `ClipGrassGoal.canUse` → `FoodStore.findGrass`: the known plant worth the most, counted from the nearest **open** way out; or `ScoutGoal` to a random spot in the whole range | step 1; the store has room (`wantsSeeds`) | `framework/entity/goal/ClipGrassGoal.java:69-92`; `framework/colony/FoodStore.java:291-330`; `framework/entity/goal/ScoutGoal.java:58-89` | near: real foragers, **no limit on how far from a hole**. Numbers (near and away): only plants within 6.5 blocks of a hole in turn (`framework/colony/FieldLife.java:456-461`, `481-495`) |
| 5. The walk through the galleries | the legs (`AntNavigator`); a closed stretch of 12 blocks or more is a hidden trip | game time (the walk's own length) | `savanna/colony/HiddenTrips.java:184-198`, `203-244` | near only; going away folds every traveller into the numbers (`247-256`) |
| 6. Finding food | `FoodMap.tick`: a real worker outside within 5 blocks finds a plant; the colony knows it once that worker is back inside | once a second | `framework/colony/FoodMap.java:108-160` | near: real workers only. Away: the holes picked find what the brain last saw round them (`FieldLife.findRound`, `196-223`), called only for a pick made away (`HoleTurns.java:363-367`) |
| 7. Cutting a piece | `ClipGrassGoal.clip` (50 ticks), `FoodStore.clippedGrass`; numbers: `FieldLife.work` → `cutByNumbers` | step 1 (or by day: only food touching a closed gallery) | `ClipGrassGoal.java:195-214`; `FoodStore.java:623-652`, `696-702`; `FieldLife.java:523-575` | near: both. Away: numbers; and with no plant to work, "by the land" with no block changed (`FieldLife.java:278-294`, `470-472`) |
| 8. The carry home and the comb | `StoreFoodGoal` (gives up after 1,200 ticks and drops the piece); `FoodStore.depositSeed` | none | `framework/entity/goal/StoreFoodGoal.java:41-124`; `FoodStore.java:902-911` | near: a layer on the comb at once. Numbers: counted at once, the layer owed (`NestLedger`, part A's R5-2) |
| 9. Dawn: foragers called in | each goal asks step 1 again and stops; `GoInsideGoal` | day clock, from 22000 | `ClipGrassGoal.java:95-101`; `ScoutGoal.java:133-138`; `framework/entity/goal/GoInsideGoal.java:45-72` | near only |
| 10. Holes plugged | a builder's `PlugHoleGoal`, once `ForagingHoles.wantsPlugging`: step 1 false and (nobody outside, or 600 ticks gone) | step 1; a 600-tick grace that is not saved | `ForagingHoles.java:275-285`, `297-310`, `314-337`; `savanna/entity/goal/PlugHoleGoal.java:58-132` | near only. A colony that goes away with holes open leaves them open until a player is within 64 blocks again |
| 11. Eaten grass grows back | `BarePatches.tick`: a green tuft after 72,000 ticks; then the tuft dries by **random ticks** | game time, then random ticks | `savanna/colony/BarePatches.java:109-140`; `savanna/block/DryGrassBlock.java:91-100` | the timer: near only (caught up on return, R5-49); the drying: only with a non-spectator player within 128 blocks |

Two more hand-overs the table does not show. A forager shut out at dawn (every hole plugged before it was in) looks for
a way in every 200 ticks for as long as it lives (`framework/entity/goal/GoInsideGoal.java:45-72`); the holes open again
the next night, and out of every player's sight the shared safety net folds it into the numbers after about 3 minutes
(`framework/colony/Strays.java`, part 215). A forager that cannot reach the comb drops its piece after 1,200 ticks with
a log line (`framework/entity/goal/StoreFoodGoal.java:112-124`); the colony is not told, the piece is simply lost.

## Already written by the other readers of this part (not written again here)

Three things this thread ran into were written up in full by the slice readers while this file was being made. They
are part of the food loop's answer, so they are named here with what the thread adds:

- **E-1 (A): eaten grass comes back green and only random ticks dry it** (step 11 of the table). The thread adds one
  step after it: a tuft that is dry again is not known to the colony. `FoodMap.tick` forgot the spot when the plant
  went (`framework/colony/FoodMap.java:119`), and near a player only a real worker walking within 5 blocks of it
  outside finds it again (`FoodMap.java:151-157`); the numbers never find a plant while the colony is near
  (`FieldLife.findRound` is called only for a night's pick made away, `savanna/colony/HoleTurns.java:363-367`). So the
  loop "eaten, green, dry, eaten again" has three clocks in a row (72,000 ticks, the random ticks, a real worker
  passing by), and no test walks it round once: `TermiteBarePatchTests` stops at the green tuft (lines 83-93) and
  `TermiteFoodTests` 680-694 dries a tuft by calling the random tick itself.
- **E-2 (B): away, the land feeds the colony for nothing** (step 7). The thread's sums agree (about 4 pieces a second
  for 60 foragers, until the store's room is full).
- **H-3 (B): holes left open when the colony goes away** (step 10). The thread adds the smaller sister: for the first
  1,200 ticks after the last player leaves, neither the near parts nor the away parts run
  (`framework/colony/AwayLife.java:396-438`), so each leaving costs the colony a minute of the numbers' foraging; the
  real foragers still out go on by themselves. Written as P-7 below.

## P-1 (A, proven; a new side of R1-35): a night skipped with `/time set day`, or a day clock that stands still, takes the termites' foraging, and the fix for the slept night does not see it

- **What is wrong:** the termites forage by the day clock (step 1), but they get hungry by game time, which never
  stops or jumps. Round 1 found this for a bed (R1-35) and the fix lets the numbers forage a night the player slept
  through. The fix sees a jump only when the day clock goes **forward**. Two everyday things in a creative world are
  not a forward jump:
  1. **`/time set day` typed at night.** Minecraft sets the day clock to the plain number 1000, not "the next
     morning" (`mc/server/commands/TimeCommand.java:19` and `64-70`). On any day but the world's first that is a jump
     **back** by many days. The fix's sum comes out below zero and answers "nothing jumped". The rest of that night is
     simply gone: nobody foraged it.
  2. **The daylight cycle switched off** (`/gamerule doDaylightCycle false`). Minecraft then never moves the day
     clock (`mc/server/level/ServerLevel.java:444-450`). Stopped by day, it is never night again: the termites of the
     whole world stop foraging (except while the weather is rain) and their hunger runs on. Stopped at night, the one
     night's pick of holes is kept for ever (the pick is made only when a foraging time starts,
     `savanna/colony/HoleTurns.java:100-110`), so the other holes never get a turn and the grass round them is never
     grazed by the numbers.
- **What the player would see:** the owner tests in creative and skips nights. A world where the night is skipped by
  command each evening, or where the sun is held at noon to build or film, has termite colonies that never come out.
  They live on the comb for a few days, then the workers starve (72,000 ticks without a meal, `macrotermes.json`), the
  colonies shrink and the queens die (120,000 ticks). It happens to every colony of the world at once, near or away,
  because near and away ask the same one question. A bed does not cause it (the fix works there), `/time add` does not
  (a forward jump, counted), `/tick rate` does not (it speeds every clock together).
- **Where:** `framework/colony/FieldLife.java:372-384` (`nightJumpedOver`: `jumped = (day - wasDay) - (game - wasGame)`
  and `return jumped < 200 ? 0 : ...`), `104-127` (the only caller); `framework/colony/Colony.java:433-440`
  (`isForagingTime`), `464-470` (`isDay`: `level.isDay()` or the hour before dawn).
- **The proof:** (1) at day clock 5 x 24000 + 15000 the player types `/time set day`; `TimeCommand.setTime` calls
  `setDayTime(1000)`. Next second `nightJumpedOver` has `day - wasDay` = 1000 - 135000 = -134000 and `game - wasGame`
  = 20, so `jumped` = -134020, below 200, and it returns 0 (line 383). `isForagingTime` is now false (it is morning),
  so `forageWith` passes over every plant that is not fed from inside (line 437). Hunger is counted on
  `level.getGameTime()` and went on all the while. (2) With the rule off, `ServerLevel.tickTime` skips
  `setDayTime` (lines 449-450), so `level.isDay()` never changes, `isForagingTime` never changes, and
  `jumped` = 0 - 20 each second.
- **Unchecked link:** how many days the comb carries a full colony (the store's size against 50 pieces a day is
  reader E's sum); the starving itself was read in round 1, not walked again here.
- **The fix proposed:** no new rule beside the old one; make the old one whole. Hunger and foraging should run on the
  same clock. The smallest form: in `nightJumpedOver`, treat any jump of the day clock, forward or back, as "the time
  of day changed from `wasDay % 24000` to `day % 24000`" and count the foraging time between the two times of day
  going forward (at most one night), which is what a bed, `/time set day` and `/time set night` all mean. For the
  stopped clock: when the day clock has not moved for a whole day of game time, the colony forages by game time (a
  night's share of every 24,000 ticks) until it moves again. Both are a few lines in the one place that already
  judges this.
- **No test:** `aNightSleptThroughIsForagedByTheTermiteNumbers` (`savanna/gametest/TermiteAwayTests.java:273-319`)
  calls `forageSkippedNight` itself (line 309). It never moves the day clock, so it would still pass if
  `nightJumpedOver` always answered 0.
- **Owner's call:** today a night skipped by a bed is foraged, a night skipped by `/time set day` is not, and with the
  sun stopped the termites never forage. My pick: both count as nights the termites foraged unseen, as the bed does;
  with the day clock stopped the colonies keep eating and foraging by game time, so a builder's "always noon" world
  does not starve them (they would then not be seen outside in that world; they feed out of sight).

## P-2 (B, proven in the code; how often it shows in play is likely, not measured): the night's holes bind the numbers but not the real termites, so the watched colony and the unwatched one forage by two different rules

- **What is wrong:** the design (termites underground, step 4) is that termites graze only round the holes they
  opened that night: "almost nobody crosses open ground". The numbers obey it: a plant is worked only if a hole in
  turn lies within 6.5 blocks of it (`FieldLife.forageWith` 456-461, `grazedFrom` 481-495; the comment there says "as
  the real foragers graze only round the holes they opened"). The real foragers have no such rule:
  1. **A real forager** takes the known plant worth the most, with the walk counted from the nearest **open** way
     out, and no limit on that walk (`FoodStore.findGrass` 291-330, `worth` 348-350). The plants it may pick are
     scanned round **every** dug hole, closed ones too (`GalleryWeb.scanSpots` 342-358 uses all the tunnels;
     `FoodStore.plants` 398-408). One forager cuts at a tuft at a time, so once the tufts by the open holes are
     taken, the next forager is sent to a known tuft by a closed hole, 20 or 40 blocks away over the open ground.
  2. **A scout** walks to a random spot anywhere in the colony's whole range: a random direction from the mound's
     middle, a third to the whole of the range out (`ScoutGoal.pickSpot` 78-89). The range is the farthest hole plus 6
     (`Colony.foragingRadius` 777-791), up to 56 blocks. No hole is asked. Even with food known, one forager's ask in
     six scouts anyway (`SCOUT_ANYWAY`, line 69).
  3. **The "explore" holes explore only for the numbers.** Each night two of the holes are opened where no food is
     known, in the outer half of the web, to find new ground (`HoleTurns.pickTurns` 343-355). Away, such a hole finds
     at once every plant the brain last saw within 6 blocks of it (`FieldLife.findRound` 196-223, called at
     `HoleTurns.java:366`). Near, nothing sends a real termite out of an explore hole: a scout's spot is drawn
     without it (item 2), so the hole finds food only if a scout or forager happens to pass within 5 blocks
     (`FoodMap.tick` 151-157).
- **What the player would see:** at night beside a grown mound, termites trailing far across open ground away from
  any open hole, and lone scouts wandering tens of blocks out, where the design means a tight ring of grazers round
  each open hole. These far walkers are the ones caught out at dawn (30 seconds of grace, then the holes are
  plugged) and the ones the soldiers' posts do not cover. Unwatched, the same colony grazes only round its holes: the
  two give different nights and different plants eaten. (The galleries' upkeep is not hurt by it: a piece counts as
  used while known food lies by one of its holes, whoever fetches it, `savanna/colony/GalleryUpkeep.java:202-214`.)
- **Where:** `framework/colony/FieldLife.java:431-474`, `481-495`; `framework/colony/FoodStore.java:291-330`,
  `371-428`; `framework/entity/goal/ScoutGoal.java:58-89`; `savanna/colony/GalleryWeb.java:342-358`;
  `framework/colony/Colony.java:777-791`.
- **The proof:** walked above for each. For the real forager: `findGrass` asks `plants(level)`, `foodMap.knows`,
  pieces left, claims, `mayForageAt` (only "not kept off", `Colony.java:369-371`), then `tripLength` to the nearest of
  `waysOut` (open holes, `ForagingHoles.java:108-138`); nothing compares the trip with the graze radius or asks
  `isInTurn`. `pickTurns` gives turns to at most `open - explore` holes with food (`HoleTurns.java:339-342`), so a
  colony with more fed holes than turns (10 to 15 turns for 60 to 90 foragers, about 50 holes on 200 blocks of
  gallery) always knows plants by closed holes.
- **Unchecked link:** how many far walks a night really happen (needs a watched night with a grown web), and whether
  the legs' route search gives up on the longest ones (a scout gives up after 600 ticks, `ScoutGoal` line 137).
- **The fix proposed:** one shared question in place of two rules: "may the colony take this plant tonight?" = a
  hole in turn (or a runway's hole, or a hole by rich food) within the graze of it. `FieldLife` already has it
  (`grazedFrom`); let `findGrass` and `ScoutGoal.pickSpot` ask the same (a scout's spot: within the graze of an open
  hole, the explore holes giving the new ground). That removes the far walks without a new number.
- **No test:** `holesTakeTurnsNearFoodFirstAndMoreInTheRain` (`savanna/gametest/TermiteForagingTests.java:150`) checks
  which holes open, never where a real forager or scout goes; it would pass with the real foragers walking the whole
  range.
- **Owner's call:** today real termites cross open ground to far food and scouts roam the whole range; the numbers
  do not. My pick: real ones keep to the graze of the holes open tonight, as real Macrotermes keep to the ground by
  their holes and runways.

## P-3 (B, proven; a new side of R1-38): "it rains" is judged two ways along the same night, and in a savanna the two never agree

- **What is wrong:** step 1 (is it foraging time) asks whether the **world's weather** is rain
  (`level.isRaining()`, `framework/colony/Colony.java:439`). Step 2 (how many holes) asks whether rain **falls on the
  nest** (`Colony.rainsNow` 546-548: the weather is rain and the nest's biome has rain). Every savanna has no rain:
  Minecraft's own (`mc/data/worldgen/biome/OverworldBiomes.java:327`, `biome(false, ...)`, the first value is "has
  precipitation") and the mod's floodplain (`savanna_floodplain.json:89`, `"has_precipitation": false`). So in play:
  1. When the weather turns to rain by day, the termites of every savanna **come out at noon under a dry sky**: the
     holes are bitten open and the foragers walk out, though not a drop falls there.
  2. The rule "half as many holes again in the rain" (`rain_more` 0.5, `SavannaNumbers.holesFor` 395-408, asked at
     `savanna/colony/HoleTurns.java:324-325`) **never runs in play**: `rainsNow` is false in every savanna. It runs
     only in the tests, which pretend rain with `setRainForTests` (that sets both judges at once).
  3. Round 1 called the two judges harmless and proposed to make line 439 ask `rainsOnNest` too. Done as written, that
     would switch the termites' rain foraging **off for good** (it never rains on a savanna nest), and with it the
     species file's `"forage_at": ["night", "rain"]`.
  A smaller side of the same step: a foraging time that starts in a rainy afternoon and runs on into the night is
  **one** round with one pick of holes (`HoleTurns.round` picks only when foraging turns from false to true, lines
  100-110), so the night after a rainy day brings no new holes.
- **What the player would see:** on a grey day with no rain falling in the savanna, open foraging holes and termites
  out in daylight, where the Matabele's day raids and the player find them. Never a rainy night with more holes.
- **Where:** `framework/colony/Colony.java:433-440`, `541-548`; `savanna/colony/HoleTurns.java:100-110`, `324-325`;
  `savanna/species/SavannaNumbers.java:395-408`; `savanna/src/main/resources/data/antcolonies/worldgen/biome/savanna_floodplain.json:89`.
- **The proof:** walked above; the two lines are 439 and 324, the biome values are read in the two files named.
- **Unchecked link:** which biomes a termite colony can stand in besides the savannas (the mod's `savanna_river` has
  rain, `savanna_river.json:78`; a mound on a river bank would get rule 2).
- **The fix proposed:** one judge, `Colony.rainsNow`, for both steps, and decide once what it means in a dry biome
  (the owner's call below). No new number.
- **The test would still pass:** `holesTakeTurnsNearFoodFirstAndMoreInTheRain` (`TermiteForagingTests.java:268`) and
  `termiteNumbersForageKnownDryGrassOnlyAtNightOrInTheRain` (`TermiteAwayTests.java:390-419`) both pretend rain, so
  neither sees that real rain never reaches the hole count.
- **Owner's call:** today savanna termites forage by day whenever the world's weather is rain, though no rain falls
  on them, and the "more holes in the rain" rule is dead. Real Macrotermes do come out on wet, overcast days. My
  pick: one judge that says "wet weather" for a savanna when the world's weather is rain (so the daytime foraging
  stays and the extra holes start to work), with the dry-sky look left for the seasons work (the shared season
  source) to make right.

## P-4 (B, likely; the size of it to measure): the night's holes are opened last, one at a time, by one fixed list; while galleries are being dug, or with one hole no builder can reach, the night's foraging waits

- **What is wrong:** step 3 of the night (a hole is bitten open) has no owner of its own. It is the last-but-one
  entry of the builders' one work list, `Nest.findDigTarget`, which hands every builder the same single block:
  1. **Every gallery block still to dig comes before any hole.** The list runs: plug, shaft, chambers, side rooms,
     first garden, then every foraging tunnel's and gallery piece's undug blocks and the feeding galleries (lines
     294-317), a plant in a runway (319-322), and only then `holeToOpen` (325-328). The galleries grow one piece at
     a time, and the next piece is planned within a second of the last block being dug
     (`savanna/colony/GalleryWeb.java:406-415`, called once a second), for as long as the web is shorter than the
     workers allow. So while a colony's web is growing, a hole can be opened only in the second between two pieces.
  2. **One hole at a time, in list order.** `holeToOpen` returns the first closed hole in turn in the tunnels' own
     order (`framework/colony/ForagingHoles.java:251-258`); every builder that asks gets that same hole. The night's
     10 to 15 holes lie spread over the whole web (the fed holes and two explore holes from the outer half), so the
     builders walk from one to the next and the last holes open well into a night of about 9,450 ticks.
  3. **A hole nobody can reach holds back all the rest.** `holeToOpen` does not ask whether a builder can stand
     under the hole (the other entries do: `Nest.firstDiggable` 1133-1151 skips a block with no stand spot). A
     builder that finds no way to it leaves it alone for 400 ticks (`DigGoal.java:136-137`,
     `Errand.LEAVE_ALONE_TICKS`), and its next ask gets the same hole again and answers "no work" (`DigGoal.canUse`
     79-80: one target, no second choice). The holes after it in the list, and the garden rooms and the mound's
     digging below it in the list, wait until that hole opens or the night ends.
- **What the player would see:** at dusk by a growing colony, builders busy underground and few or no holes opening
  for minutes; by a grown colony, holes opening one after another across the land instead of together; and, if (3)
  happens, a night with some holes shut that should be open, fewer foragers out, and builders standing idle. The
  numbers do not wait for any of it: away, and near for plants out of sight, they graze round a hole in turn whether
  its block is open or not (`FieldLife.java:434`, `holesInTurn`), a second way the watched and unwatched nights
  differ.
- **Where:** `framework/colony/Nest.java:213-337` (the order, 294-328); `framework/colony/ForagingHoles.java:236-258`;
  `framework/entity/goal/DigGoal.java:63-81`, `119-147`; `framework/entity/goal/Errand.java:46`, `322-337`;
  `savanna/colony/GalleryWeb.java:391-468`.
- **The proof:** (1) and (2) are the order of the lines named. (3): `holeToOpen` tests `isDug`, `!isOpen`, `!isSpent`
  and `mayOpen` only; `DigGoal.tick` asks `standSpotsFor` and `errand.goTo`, and on "no way" sets `stuckTicks` to the
  limit, so the goal stops and the hole is left alone by that builder only; `findDigTarget` knows nothing of what a
  builder left alone and returns the same hole.
- **Unchecked link:** for (1), how long the gaps between pieces are in play (a piece's soil must be carried up
  first) and how often a web is still growing at dusk; for (3), whether a hole whose tunnel is dug can in fact be
  out of reach (sand or gravel fallen into a gallery, a piece walled off against a neighbour by
  `GalleryUpkeep.wallOffNeighbours`, a creature standing in the way). Neither was run.
- **The fix proposed:** fewer rules, not more: give the opening of the night's holes its own small job in place of
  a line at the bottom of the dig list. At the start of a foraging time the holes in turn are the work (before new
  gallery pieces: food first), each builder takes the nearest closed one nobody else has taken (the plugging
  already works this way with a claim per hole, `ForagingHoles.pickHoleToPlug` 314-337), and a hole a builder found
  no way to is skipped by that builder. Opening and plugging then share one shape.
- **The test would still pass:** `termitesOpenTheirForagingHolesAtNightAndPlugThemByDay`
  (`TermiteForagingTests.java:43-101`) lets a small colony dig for real and waits any number of nights until the
  holes in turn are open (its own comment: "digging the tunnels may take more than one night"); it does not ask how
  far into a night a hole opens or whether one hole held the others back, and it is kept out of full runs.

## P-5 (B, proven for the record; its part in BUGS 452 likely): by day the colony's record names where termites could feed, not where any termite is, and a raid takes it at its word

- **What is wrong:** by day the food loop has three questions with three different answers:
  1. **Who really feeds by day.** A real forager only if the store has room, the food can be eaten from inside and
     pieces are left (`ClipGrassGoal` sets `fromInside` by day; `FoodStore.findGrass` returns nothing when
     `wantsSeeds` is false, line 292, and skips food not `isFeedableFromInside`, line 315). The numbers the same
     (`FieldLife.forageWith`: room first, 422-425; by day only `fromInside` plants, 437). With a full store, the usual
     state of a well-fed colony, **nobody feeds by day anywhere**.
  2. **What the colony's record says** (`FeedingSpots.byDayPlaces` 86-104): every feeding gallery whose food or wood
     can be eaten from inside and every runway that is closed, unless the hole is spent or the place is kept off for
     danger. It is a question about blocks. It does not ask the store's room, the pieces left, or whether anyone is
     there.
  3. **What a raid is told** (`Raid.byRecord` 372-390): with no termite seen in the open, a by-day place within
     reach and with a way in for the raiders counts as "termites there". Then `bringOutFeeders` (1796-1814) takes
     workers out of the termite colony's numbers and stands them in that place's free cells (`feedersOut`
     1821-1854), up to what the raiders could carry.
  So the raid's prey at a feeding spot by day is not the termites that were feeding there; it is made on the spot
  from the record, also when the loop says nobody was feeding. And `feedersOut` fills the cells with a way in
  first, **then the other cells** (the sort at 1839-1840 only orders them; it does not leave out cells with no way
  in), so once the open cells are full, the rest of the workers are stood where no raider can get at them.
- **What the player would see:** a raid at a log where the termites had stopped feeding (store full, or the log's
  pieces used up but the block still standing) still finds termites; and at a spot with one open cell, most of the
  termites brought out stand shut in under the wood, counted as there, with raiders pacing above them. That is the
  picture of BUGS 452 ("termites counted at a raid's spot that no raider goes for").
- **Where:** `savanna/colony/FeedingSpots.java:60-104`; `savanna/colony/Raid.java:354-390`, `1796-1854`;
  `framework/colony/FoodStore.java:291-330`; `framework/colony/FieldLife.java:417-446`.
- **The proof:** walked above, line by line. `byDayPlaces` calls only `isSpent`, `keepsOff`, `galleryByDay` and
  `isRunwayClosed`; none of them reads the store or a plant's cut count.
- **Unchecked link:** whether the termites of BUGS 452's four attacks were ones `feedersOut` stood in cells with no
  way in, or termites that were there already (the bug entry itself says this is not measured). The raid's own
  rules were read here only where they meet the feeding record; the raids are round 6's and the rewrite's ground.
- **The fix proposed:** one judge of "termites feed here now", owned by the termite colony and asked by both the
  day feeders and the raid: the place can be fed at from inside **and** the loop is feeding (room in the store,
  pieces left). And in `feedersOut`, only cells with a way in (drop the others instead of sorting them last): a
  termite the raiders cannot reach is no prey. Both remove a second opinion; neither adds a number.
- **Owner's call:** today a raid finds termites at any feeding spot that is built right, fed at or not. Real
  Macrotermes do keep workers under their sheeting by day, so a spot always holding a few is defensible. My pick:
  keep "always a few there" as the stated rule (real biology), but only in cells the raiders can reach.

## P-6 (B, proven in the code; how it looks to see in play; the owner's earlier call): beside a spectator the forager numbers cut the grass in plain view, so most of a watched night's foraging is done by termites that are not there

- **What is wrong:** the rule that keeps the watched and the counted foragers apart is "a plant a player can see is
  left to the real foragers" (`framework/colony/FieldLife.java:533-536`). The eyes it asks for leave a spectator out
  (`Population.viewersInSight` 448-450 → `viewers(..., seersOnly = true)` 421-445 → `Watchers.hiddenFrom` 48-50: "every
  watcher but a spectator"; the owner's call of 2026-11-13, made for the raids). So beside a spectator every plant
  counts as out of sight:
  1. The forager numbers work every tuft in the graze of the night's holes at their own pace: one piece about every
     300 ticks a tuft, so a tuft is gone in about a minute and the whole graze within the first minutes of the night
     (`FieldLife.work` 539-545).
  2. The cuts are shown at once, for the same reason (`FieldLife.showCuts` 589-621 asks the same eyes): the tufts
     shrink and vanish with no termite at them.
  3. The real foragers, at most a share of the 50 real bodies, find the tufts by the open holes already taken, and
     `findGrass` sends them to the known plants left, which are the ones by **closed** holes: P-2's long walks over
     open ground become the usual thing to see.
- **What the player would see:** the owner watches in spectator. At dusk the holes open, a few termites come out,
  and the grass round the holes disappears by itself faster than they can reach it; the termites that are seen
  walk far off across the open ground. Beside a survival or creative player the same night is cut by real termites
  only, round the holes.
- **Where:** `framework/colony/FieldLife.java:448`, `523-575`, `589-621`; `framework/colony/Population.java:413-450`;
  `framework/colony/Watchers.java:41-50`.
- **The proof:** walked above; `forageWith` takes `eyes` from `colony.population().viewersInSight(level)` (line 448)
  and `work` asks `Population.outOfSight(level, pos, eyes)`; with no eyes in the list every plant is out of sight.
- **Unchecked link:** how many of the foragers are real bodies at a full colony beside a spectator (the share-out of
  the 50 bodies was not worked through), so how much is left for them; not seen in play.
- **The fix proposed:** keep the owner's call for what it was made for (insects and blocks may appear in front of a
  spectator) but give the forager numbers the other list for this one question: a plant any watcher could see,
  spectator or not, is left to the real foragers. One word changes in `forageWith` and `showCuts` (the eyes of
  `Population.viewers(level)`, which counts spectators); no new rule.
- **Owner's call:** today, in spectator, the counted foragers eat the grass in front of the camera. My pick: in
  front of any player, spectator too, only real termites cut; the numbers keep to what nobody looks at.

## P-7 (C, proven for the foraging; what else stands still in that minute not walked): for a minute after the last player leaves, nobody forages for the colony

- **What is wrong:** when the last watcher goes out of range (80 blocks), the near parts stop at once, but the away
  parts start only after the grace of 1,200 ticks (`framework/colony/AwayLife.java:417-437`: every away step is
  inside `if (isAway(level))`, and away begins after `GRACE_TICKS`). In that minute the forager numbers do nothing
  (`FieldLife.tick` is not called from either side); only the real foragers still loaded carry on by themselves.
  Nothing makes the minute up afterwards.
- **What the player would see:** nothing directly. A player who walks back and forth past a mound at night takes
  about an eighth of the night's numbers foraging (1,200 of about 9,450 ticks) with each leaving.
- **Where:** `framework/colony/AwayLife.java:396-438`; `framework/colony/FieldLife.java:104-127`.
- **Unchecked link:** whether the colony's eating also stands still for that minute (then little is lost on
  balance); the eating was round 1's and part A's ground.
- **The fix proposed:** no new rule: the first away second stands for the whole grace (the numbers' pass already
  takes a length in seconds, `FieldLife.seconds`, made for the slept night).

## The sums along the loop (path 7): where the flow is narrow, by the code alone

Nothing here was run; each line says where its number comes from, and the last list says what only a soak can settle.

- **Holes a night:** one per 6 foragers, at least 1 (`SavannaNumbers.holesFor` 400-403, `foragers_per_hole` 6). The
  foragers are the real workers with the forager's job plus the forager numbers inside (`Colony.foragersNow`
  849-856). With 60 to 90 foragers: 10 to 15 holes. Two of them explore (`explore` 2, `exploreOf` 406-408). The rain
  rule (half as many again) does not run in play (P-3).
- **Holes there are:** the galleries may be one block long per worker, at most 600 (`lengthFor` 395-397,
  `workers_per_block` 1, `max_length` 600); the colony cap is 200 workers, so about 200 blocks, never 600. A hole
  about every 4 blocks (`hole_spacing`): about 50 holes. So a hole with no food by it waits about 4 to 5 nights for
  a turn; a hole with known food gets one every night while there are fewer fed holes than turns.
- **Ground a night:** the numbers graze within 6.5 blocks of a hole in turn (`FieldLife.grazedFrom` 481-495,
  `food_radius` 6): about 130 columns a hole, 1,300 to 2,000 a night, of a band of about 2,600 columns along 200
  blocks of gallery.
- **A forager number's round trip:** 200 ticks plus 16 a block of walk from the hole (`FieldLife` `TRIP_TICKS`,
  `TICKS_PER_BLOCK`, line 539): about 300 ticks for a tuft 6 blocks out. A night is about 9,450 ticks (the sun is
  down from 12,550, `Colony.isSunUp` 476-480, and the foragers head in at 22,000, `HEAD_IN_FROM`): about 30 trips a
  forager, about 1,800 pieces a night for 60 foragers if plants and room allowed it.
- **A tuft's own limit:** one forager cuts at a tuft at a time and it rests 200 ticks between cuts
  (`FieldLife.work` 539-541, `REST_TICKS`), but a tuft has only 4 pieces (8 tall), so every tuft in the night's graze
  is finished the same night. A real forager's clip takes 50 ticks (`ClipGrassGoal` `CLIP_TICKS`).
- **So the narrow place is never the walking.** The flow can carry more than 30 times the 50 pieces a day a full
  colony eats (reader E's figure). What limits a night is (a) the pieces standing dry within the graze of that
  night's holes, (b) the room in the store (`FieldLife.forageWith` 422-425 stops at no room; `findGrass` 292), and
  near a player (c) how many of the foragers are real bodies, since a plant a player can see is left to the real
  ones (`FieldLife.work` 533-536). The colony therefore takes grass as fast as its store has room, strips the graze
  of its fed holes first, and then waits on regrowth, which is about 6 days from eaten to food (E-1), not 3.
- **A night slept through** is one pass in which each plant in the graze is worked once and finished
  (`FieldLife.forageSkippedNight` 393-404; `work` gives a tuft about 30 trips' worth, capped by its 4 pieces), up to
  200 plants (`PLANTS_PER_SECOND`), plants in a player's sight left out. That is at least as much as a watched
  night brings.
- **Only a soak can settle:** how many dry tufts stand in a colony's band (the worldgen's density, reader E's
  estimate); how many nights until a band is stripped, and whether the 6-day regrowth then carries 50 pieces a day;
  the store's room night by night; how many real foragers a watched colony has out and their real round trip (the
  worker's speed is 0.22 as an attribute, `savanna/entity/TermiteWorkerEntity.java`, the walking speed in blocks a
  second was not measured); how often P-2's far walks and P-4's late holes happen.

## The spectator (path 5): every step of the loop that leans on a random tick

A colony counts as near with a spectator beside it (`AwayLife.isHere` 164-175 takes any game mode), so the brain's
near parts and the real termites all run. Minecraft gives no random ticks there (see "Words used"). Along the loop
that stops exactly these, all proven by the other readers or part A and only gathered here:

- eaten grass comes back green and stays green: `savanna/block/DryGrassBlock.java:91-100`,
  `TallDryGrassBlock.java:55-71` (E-1). The spectator sees the grass round the holes eaten once.
- mud crust and mud casts neither age nor crumble: `savanna/block/MudCrustBlock.java:141-164`,
  `MudCastBlock.java:45-59` (H-5). The spectator sees old runways and casts stand for ever.
- pulp heaps do not sink and termite remains do not weather: `savanna/block/PulpHeapBlock.java:78-100`,
  `TermiteRemainsBlock.java:25-34` (read here; not written up elsewhere as far as this reader saw).
- the mound's soil setting is part A's R5-3.

Every other step of the night (the table's 1 to 10) runs on the colony's own tick and is the same for a spectator,
but for one thing that is not a random tick: who counts as "seeing" a plant. A spectator does not, so the forager
numbers cut in front of it (P-6).

## Save, reload and land unloading in mid-step (path 6)

Walked for each thing that can be half done; nothing was found lost or doubled. The list is in "What is solid"
below. One link was not checked: that a real forager saved in mid-walk comes back with the piece still in its jaws
(the entity's own save was not read).

## What is solid

- **One question for "is it foraging time", near and away.** The goals, the holes, the hole turns, the forager
  numbers and the minor soldiers all ask `Colony.isForagingTime` (`framework/colony/Colony.java:433-440`); nobody
  keeps a second clock for it. The night ends for all of them at 22,000, before the sun (`HEAD_IN_FROM`, 555-565).
- **A bed, `/time add` and `/tick rate`.** A night slept through is foraged in one pass by the numbers
  (`FieldLife.nightJumpedOver` 372-384, `forageSkippedNight` 393-404), with that night's own holes; a forward jump
  by command is counted the same way; a faster tick rate speeds every clock together. (The two cases that are not
  solid are P-1.)
- **A piece is never counted twice between the real and the counted foragers.** There is one count of pieces cut
  per plant, used by both (`FoodStore.clippedGrass` 623-652, `cutByNumbers` 696-702) and saved (1082-1103); the
  numbers skip a plant a real forager is at (`FieldLife.work` 526-529). A night that is near for its first half and
  away for its second keeps the same holes (`tonight` and `inRound` are saved, `savanna/colony/HoleTurns.java:416-429`,
  and a round is picked only when foraging starts) and the same cut counts. A piece in a real forager's jaws when
  its colony goes away is counted once when the forager is folded into the numbers (`AwayLife.foldIn` 445-483).
- **Reload in mid-night.** Saved: the night's holes, the round, each hole's last turn, the holes known dug, the cut
  counts, the food map, the numbers' picture of the plants and what they still owe the world, the hidden travellers
  with their bodies (`savanna/colony/HiddenTrips.java:463-516`). Not saved, and harmless: the plugging grace's start
  (`ForagingHoles.closedSince`, it starts again, at most 600 ticks), the numbers' part-trips (under one piece a
  plant), the claims on plants and store places (they time out anyway), a worker's finds not yet told (found again
  when it next passes), the day clock's last value (only the first second after loading cannot see a jump).
- **Nothing along the loop grows without end.** A dead worker's untold finds are dropped
  (`framework/colony/FoodMap.java:159`); plants that are gone are forgotten (119, and `FoodStore.java:425` for their
  cut counts); the regrowth list is capped at 4,096 tufts (`savanna/colony/BarePatches.java:87-89`); a gallery piece
  filled in takes its holes out of every per-hole list (`savanna/colony/GalleryUpkeep.java:407-437`), and the "foraged
  from" notes are pruned each upkeep (213).
- **A worker left outside is not lost for good.** It looks for a way in every 200 ticks
  (`GoInsideGoal.java:66-70`), the holes open again the next night, and out of sight the shared net folds it into
  the numbers (`Strays`). By day nobody may end a walk outside (`AbstractAntEntity.mayGoOut` 2226-2241, `mayBeAt`
  2289-2292), so the day feeders stay under cover.
- **Land that is not loaded is not read** on any step walked here (`LandReady` before each block read in
  `ForagingHoles.isOpen`, `Nest.firstDiggable`, `FoodMap.tick`, `BarePatches.tick`, `FeedingSpots.galleryByDay`).

## What was read

Read in full: `framework/colony/FieldLife.java`; `savanna/colony/HoleTurns.java`, `BarePatches.java`,
`FeedingGallery.java`, `GalleryCatchUp.java`; `savanna/animal/Grazing.java`;
`framework/entity/goal/GoInsideGoal.java`; `docs/plans/termites-underground.md`; the three other readers' headings
and E-1, E-2, H-2, H-3 in full.

Read in part (line ranges): `framework/colony/Colony.java` 220-245, 368-371, 395-594, 777-856;
`framework/colony/ForagingHoles.java` 1-560; `framework/colony/Nest.java` 213-337, 578-580, 698-714, 781-787,
1125-1151; `framework/colony/FoodStore.java` 139-228, 280-430, 584-732, 757-760, 902-1023, 1082-1103;
`framework/colony/FoodMap.java` 60-205; `framework/colony/FoodWays.java` 150-269; `framework/colony/AwayLife.java`
60-638 (the parts on being here, the grace, folding in, settling); `framework/colony/Population.java` 404-462;
`framework/colony/Watchers.java` 27-50; `framework/entity/AbstractAntEntity.java` 2218-2297;
`framework/entity/goal/DigGoal.java` 40-189, `Errand.java` 22-76 and 251-338, `ScoutGoal.java` 50-144,
`ClipGrassGoal.java` 69-101 and 139-214, `StoreFoodGoal.java`, `GatherFoodGoal.java`, `WaitAtNestGoal.java`;
`savanna/colony/HiddenTrips.java` from 36, `FeedingSpots.java` from 20, `Galleries.java` 344-379, `GalleryWeb.java`
286-468, `GalleryUpkeep.java` 30-499, `Soldiers.java` 196-455, `Raid.java` 340-395 and 1790-1854,
`SavannaParts.java` (the bare patches rows only); `savanna/entity/goal/PlugHoleGoal.java` 58-132;
`savanna/entity/TermiteWorkerEntity.java` 84-129, 215-228; `savanna/species/SavannaNumbers.java` 388-409; the random
tick parts of `DryGrassBlock`, `TallDryGrassBlock`, `MudCrustBlock`, `MudCastBlock`, `PulpHeapBlock`,
`TermiteRemainsBlock`; `macrotermes.json` 78-171; the tests `TermiteAwayTests.java` 262-323,
`TermiteForagingTests.java` 28-102 and 140-289, `TermiteBarePatchTests.java` 40-96; Minecraft's
`TimeCommand.java`, `ServerLevel.java` 348-358 and 444-450, `Level.java` 425-427 and 850-855,
`ServerChunkCache.java` 367-375, `ChunkMap.java` 983-990, `OverworldBiomes.java` 39-45 and 295-328.

Not read: `savanna/colony/ClosedHoles.java`, `SentryLine.java`, `DeadWoodCall.java`, `SavannaEvents.java`;
`savanna/entity/goal/WalkBeatGoal.java`, `TermiteSoldierEntity.java`; `framework/colony/ForagingHoles.java` from 560
(the runways), `ForagingTrails.java`, `NestLedger.java`, `Strays.java` (its header only); the rest of the
`termitefood` and `away` tests; `docs/DESIGN.md`. Not finished: the soldiers' part of the night's walk (path 1) was
left to reader S, the mud crust by day (path 3) to readers E and H; the sums of path 7 are from the code alone and
none was measured.
