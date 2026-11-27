# Round 5a, reader D: mounds in new land, dead mounds and wear

Read at commit `4dcd1fe6` in `C:\Ant Mod`. No code changed, nothing run or measured.
Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`.

## D-1 (A if the measure confirms it; likely, to measure): a dead mound wearing at the edge of loaded land makes Minecraft load the next chunk on the spot

- **What is wrong:** the dead mound book works a dead mound as soon as the chunk of its middle is loaded
  (`LandReady.at(level, mound.center)`), and then asks "is this land loaded" for each block it changes, one block at a
  time. But changing a block with Minecraft's normal flags (`UPDATE_ALL`, `destroyBlock`, even `UPDATE_CLIENTS`) also
  makes Minecraft look at the six blocks round it (its "neighbour" and "shape" updates). When the changed block sits on
  the edge of a loaded chunk and the next chunk is not loaded (or only half made), that look makes Minecraft load the
  next chunk, or finish making it, there and then, with the game waiting. The mod already learnt this lesson once:
  `Nest.touchesFluid` (`framework/colony/Nest.java:1210-1228`) treats a neighbour across a chunk edge in land not loaded
  as unknown, "reading it would load or make that land on the spot, a freeze (review R1-8, R1-18: the cell was guarded,
  its neighbours were not)". The dead mound book makes the same mistake in many places.
- **What the player would see:** a hitch now and then while walking or flying towards (or away from) an old dead mound,
  most of all on coming back to a mound left for days: its catch-up (up to 256 blocks every 5 seconds) starts the
  moment its middle comes into the loaded land, that is right at the edge of the land the player has loaded.
- **Where:** `colony/DeadMounds.java:245-259` (`tick`: only the middle's chunk asked), `:399-410` (pieces: `crumble`
  after a check of the piece's own chunk), `:413-438` (columns: only the column's chunk asked, then `crumble`,
  `closeBeside`, `cap` and `grass` write with `UPDATE_ALL`), `:440-458` (the washed-soil ring), `:294-300` (`crumble`:
  `setBlock(..., UPDATE_ALL)` or `destroyBlock`), `:376`, `:380` (comb); `colony/DeadWorks.java:160-177` (`pass`: one
  cell's chunk asked), `:184-206` (`crumbleAround` asks the wall's chunk, then `setBlock`/`removeBlock` it), `:244-273`
  (`closeHole`, `fillIn`: `setBlock(..., UPDATE_ALL)`; `fillIn` also asks `Nest.isDugOut` and
  `GalleryUpkeep.soilFor`, not read). The galleries reach out to 50 blocks from the middle
  (`macrotermes.json` galleries `reach`), so most of a dead colony's works can lie in other chunks than its middle.
  Also, smaller: `colony/BarePatches.java:120-137` (the tuft's own column asked, then `setBlock(..., 3)`).
- **The proof:** walked in the mod: every write above is guarded only by the written cell's own chunk. Minecraft's
  side (1.21.1, from memory of its source, not re-read here): `Level.setBlock` with flag 1 calls
  `blockUpdated` → the neighbour updater reads `level.getBlockState(neighbour)` for each side, and without flag 16 it
  calls `updateNeighbourShapes`, which reads each neighbour too; `Level.getBlockState` calls `getChunk(x, z)`, which on
  the server thread waits for the chunk to be loaded at full status. A chunk at the edge of the loaded land (full but
  not ticking) has neighbours that are only half made, so this can also run world making for them.
- **Unchecked link:** how often a write falls on a chunk edge next to land not loaded, and what one such load costs
  (a saved chunk, or one still to be finished). Not measured. Vanilla Minecraft avoids it by changing blocks only in
  ticking chunks, whose neighbours are always loaded.
- **The fix proposed (fewer rules, as `NewLandMounds` already does):** one check per dead mound per look: the whole
  box of the mound and its works, plus one block, is loaded (`LandReady.round(center, reach + 1)`, `reach` the mound's
  foot or the works' `reach`, whichever is wider); the per-cell checks can then go. Same for `BarePatches`: its tufts
  lie within the galleries' reach of a living colony, so the same one check per colony would do. A test: a dead mound
  whose works cross into a chunk marked not ready (`LandReady`'s test list) changes nothing there and loads nothing.

## D-2 (A, proven by an earlier measure, not re-measured): putting one mound of new land in can take 95 ms in one tick, and the test allows 400 ms

- **What is wrong:** when the land round a waiting place is loaded, the whole mound goes in during one tick
  (`NewLandMounds.tick`, `return` after one): the colony's members, the mound built block by block
  (`Mound.buildInstantly`), the nest dug (`digNest`, every cell with `UPDATE_ALL`), the gardens filled, the king and
  queen; for a dead mound the whole mound is built and then worn down to its day at once (`agedForNewLand`, no block
  budget), so an old hump builds a full cathedral of thousands of blocks only to crumble nine tenths of it again in the
  same tick. `docs/plans/savanna-loading.md` line 43 measured "putting a mound in (6 to 95 ms, once)". The round's
  rule: a single tick past 50 ms is an A finding. One goes in every 10 ticks while places wait, so flying over new
  savanna can give a stutter every half second.
- **What the player would see:** a short freeze as each mound of new land appears, worst for big living mounds and
  for old dead ones.
- **Where:** `colony/NewLandMounds.java:184-207` (`tick`), `:280-313` (`put`), `:478-505` (`raise`), `:522-545`
  (`digNest`, `dig` with `UPDATE_ALL`), `:612-631` (`putDead`); `colony/DeadMounds.java:274-288` (`agedForNewLand`,
  budget `Integer.MAX_VALUE`); the test `gametest/TermiteNewLandTests.java:221-239`
  (`aQueuedMoundIsGrownAsideThenPutInQuickly`: a half mound, passes up to 400 ms).
- **The proof:** walked: one `putWithAnts` per look, no split over ticks; the wear of a new dead mound is called with no
  budget; the 95 ms is the plan's own measure.
- **Unchecked link:** which part costs most (the build, the dig's neighbour and light updates, the wear) was not
  measured part by part. The living colony's catch-up of 5,000,000 ticks of builders' time (`treeBy`, line 605) is spent
  by `BuildLife` later (another reader's); not followed.
- **The fix proposed:** measure part by part first (the log line already gives the total). Then two removals rather
  than new numbers: (1) a dead mound of new land is built straight at its worn heights (`MoundWear.heightAt` for each
  column) instead of built whole and crumbled, one rule for its shape; (2) the dig uses `UPDATE_CLIENTS` plus flag 16
  (no neighbour or shape updates) as world making does, since nobody has seen the land. If it is still over 50 ms, split
  a put into its steps over successive looks (the book already waits places). The test should put a full mound and an
  old hump and hold them to the tick's budget, not 400 ms.

## D-3 (B, proven): `/time set day` stops every dead mound from rotting and wearing for as many days as the world had run

- **What is wrong:** a dead mound counts its days on Minecraft's day clock (`level.getDayTime()`), the clock the
  `/time` command sets. `/time set day` (or `/time set 0`, `noon`, ...) does not move the clock to the next morning: it
  sets it to an absolute small number (1000 for "day"), taking the clock back to the world's first day. Every dead mound
  then has a death time in the future, its "days dead" reads 0 (`Math.max(0, ...)`), and since its comb and wear only
  ever go forward, nothing happens to it until the clock has run past its death time again: in a world at day 60,
  60 days of play. With the game rule `doDaylightCycle` false the day clock stands still, so dead mounds never wear at
  all. The colonies' own clocks (`Colony.setAge`/`bornAt`, `framework/colony/Colony.java:359-361`) and the bare
  patches (`BarePatches`, `getGameTime`) use the game clock, which never goes back: two clocks for one world's ageing.
- **What the player would see:** in a creative world where `/time set day` is used (the user tests that way), Xylaria
  comb that never rots, spires that never crumble, foraging holes that never grass over, until weeks of play later, and
  then everything catching up at once (up to 256 blocks every 5 seconds per look).
- **Where:** `colony/DeadMounds.java:98-100` (`days`), `:184` (`died`: `level.getDayTime()`), `:279`
  (`agedForNewLand`); the class comment `:44-45` and `:49-51` ("by the world's clock, /time add moves them on").
- **The proof:** walked: every reader of a dead mound's age goes through `DeadMound.days`; `stage`/`heightAt`/
  `pieceDay`/the works' day functions all take that number; with it at 0 nothing is due (`wear` returns at `days <= 0`,
  `update` skips the works at `days > 0`). Minecraft's `/time set` sets each level's day time to the given absolute value
  (from its source as I know it; not re-read here).
- **The fix proposed:** count by the game clock, as the colonies do (`diedAt` from `getGameTime`), one clock for all
  ageing; the tests already move dead mounds on by hand (`updateForTests`, `agedForNewLand`).
- **Owner's call:** today `/time add` moves dead mounds on (on purpose, so the wear can be watched quickly), and
  `/time set` freezes them. My pick: the game clock, so `/time add` no longer ages them; the command
  `/antcolonies` could get a "wear this dead mound N days" if watching it quickly is wanted.

## D-4 (B, proven): when a termite colony dies, the bare earth round its holes stays bare for good

- **What is wrong:** the bare patches (`BarePatches`) remember every tuft the workers cut near a foraging hole and turn
  the grass block under it to coarse dirt until the tuft grows back (3 days, `regrowth.ticks` 72000). That memory is
  part of the living colony. When the colony ends, the part is dropped with the colony: it has no "colony ended" handler
  and the dead mound book (`DeadWorks`) does not take the patches over, so the coarse dirt is never turned back. Minecraft
  grass does not spread onto coarse dirt, so it stays for ever. The dead mound's own works do grass over the holes
  themselves (`DeadWorks.closeHole`), so the player gets green holes in a ring of grey dirt. A second, rarer way to the
  same end: at 4,096 remembered tufts the oldest is dropped without growing back (`tufts.remove(0)`), leaving its dirt bare.
  Real: the bare zone round Macrotermes holes is kept bare by the foragers; once they stop, the grass comes back within a
  season.
- **What the player would see:** round every dead termite mound that died in play (or was killed by a command), little
  patches of coarse dirt round the old foraging holes that never green over, while the mound beside them slumps into a
  grassy hump.
- **Where:** `colony/BarePatches.java:65-91` (`eatenAway`: the dirt made bare, `:87-89` the oldest dropped),
  `:157-165` (`unbare`, only reached from `tick`); `colony/SavannaParts.java:275-280` (the part hears only
  `PLANT_EATEN`); `colony/DeadWorks.java:80-132` (`record`: holes, sheets, spots, galleries, no bare patches);
  `species/SavannaNumbers.java:480` (`bare_ground` defaults to `minecraft:coarse_dirt`).
- **The proof:** walked: `COLONY_ENDED` is heard by `galleries`, `mound` and `fungus_garden` only
  (`SavannaParts.java:37`, `:64`, `:153`); `ColonyManager.colonyEnded` then removes the colony
  (`framework/colony/ColonyManager.java:152-155`), so `BarePatches.tick` never runs again.
- **The fix proposed:** no new rule: let the bare patches hear `COLONY_ENDED` and hand their bare spots to the dead
  mound's works as one more list that "grows back" on a day, as the holes do (`DeadWorks`, its own day function and
  `unbare`). And at the cap, drop the oldest only after un-baring it (or let the oldest grow back at once). A test: a
  colony with a bare patch is killed; some days later the coarse dirt is grass.

## D-5 (B, likely): the nest of a mound of new land is dug through water and lava, which the termites themselves never do

- **What is wrong:** when a mound of new land goes in, its nest (shaft, rooms, side rooms, gardens, founding chamber)
  is cut out at once, "through anything but bedrock" (`digNest`, `dig`): a cell of water or lava is set to air like
  any other, with `UPDATE_ALL`, so the water or lava next to it flows in. The living termites never open a block that
  touches water or lava; the framework has one judge for that (`Nest.touchesFluid`, "the one judge of it, for the nest,
  the ground reader and the foraging holes"), which new land does not ask. The place check before a put looks only at
  the middle cell and the one under it (`put`, line 284).
- **What the player would see:** now and then a mound of new land whose rooms are flooded (a river bank, an underground
  water pocket, a cave), the queen and king put into a water-filled royal chamber, or lava running into a nest
  (Minecraft's caves often reach a few blocks under the savanna).
- **Where:** `colony/NewLandMounds.java:518-545` (`digNest`, `dig`), `:280-288` (the place check);
  `framework/colony/Nest.java:1210-1228` (`touchesFluid`).
- **The proof:** walked: `dig` tests only `isAir` and `BEDROCK`; nothing on the path from `tick` to `digNest` reads a
  fluid except the middle's two cells.
- **Unchecked link:** how deep the nest goes (`macrotermes.json` nest `max_levels` 1; the room depth was not traced) and
  so how often it meets water or a cave in real savanna land. New land allows mounds near rivers (`places.water_cut`
  only lowers the chance; `low_ground` 2 keeps the ground at least 3 blocks over the water), so rooms below the river's
  level a few blocks from its bank are possible. Not measured.
- **The fix proposed:** ask the one judge: `digNest` skips a cell `Nest.touchesFluid` refuses (as the diggers would
  leave it), or a place whose nest would touch water or lava is skipped like a place on water. The second is simpler
  and keeps every new-land nest whole. A test: a mound put beside a buried pool leaves the pool's water where it was.

## D-6 (B, balance and realism; owner's call): the dead mounds near the player all turn into humps within about a month of play, while new land keeps showing standing ones

- **What is wrong (two rules meeting):** new land places dead mounds mostly freshly dead or wearing (`dead_stages` 30,
  60, 6, 4: nine in ten under 12 days dead, "so about 1 mound in 3 reads as dead", the user's ask of 2026-11-07), and
  the wear then runs on in play at the sped-up rate the user picked (spires gone in about 5 days, a 4-block hump by
  about day 26 to 35, `MoundWear.SPIRES_DAYS`, `HUMP_DAYS`). Nothing makes new dead mounds near the player (living
  colonies of new land rarely die), so after about 30 game days (10 hours of play) every dead mound in the land the
  player lives in is a low grassy hump, and the "1 in 3 reads as dead" holds only in land just explored. The same speed
  meets the Matabele: a colony settled in a dead mound sees its home slump to a 4-block hump within a month; BUGS 358
  (the nest's entrance left floating up to 5 blocks over the hump) is one face of this.
- **Real biology:** a Macrotermes mound outlives its colony by years to decades, eroding slowly (the "mound islands"
  are centuries old); Megaponera favour these standing dead mounds (the species file cites almost half of 73 nests in
  deserted mounds). The user chose "sped up" for the wear (DESIGN, "Dead mounds, details": "about 20 to 40 days into a
  low grassy hump") before new land's mix was tuned to what the player sees; the two numbers were set for different
  questions and were not checked against each other.
- **Where:** `colony/MoundWear.java:24-66` (the days); `worldgen/TermiteMoundStructure.java:261-288` (the days dead of
  new land); `resources/data/antcolonies/worldgen/structure/termite_mound.json` `dead_stages`.
- **The proof:** read: `heightAt` reaches the hump once `(t - 5) / 25 >= 1`, with `t = days * jitter` (0.85 to 1.15);
  new land's dead are `between(0.2, 3)` 30 in 100, `between(3, 12)` 60 in 100.
- **The fix proposed:** none in code until the owner picks. Two ways with no new rule: slow the slump (for example
  `HUMP_DAYS` 300: spires still go in days, as real thin spires do, but the body stands for months of play), or keep the
  speed and accept that explored land settles to humps.
- **Owner's call:** today: dead mounds near the player are all humps after about a month of play; new land shows
  standing ones. My pick: spires and comb as today, the body's slump about ten times slower, so a standing dead
  cathedral stays a landmark and a Matabele home for the length of a long game, as real ones do for years.

## D-7 (C, to measure; a new side of R6-28): a settled dead mound keeps every column of its wear for good, and the whole book is written at every save while any mound changes

- **What is wrong:** R6-28 noted the dead mound book only grows. The new side: what each record holds after it has
  settled. A full mound's wear is about 650 columns (its foot of radius 12 to 13, about 490 columns, plus the 2-block ring
  of washed soil, about 160), 7 numbers each, plus its pieces, shaft and ways; its works keep their lists until each
  place is done. Once a mound has settled (`wear.settled`) and its works are done, nothing outside the book reads its
  columns again (searched: `columns` is read only by `wear`, `describe` and the test helpers); only the middle, the
  shaft and the ways are used (the Matabele, the tests). The book is one saved file, written whole at every autosave
  after any `setDirty`, and a wearing mound sets it dirty with every block.
- **What the player would see:** nothing today; in a long-explored savanna a larger save and a longer autosave.
  Rough size, not measured: dead mounds are a third of the mounds, about 15 dead mounds per square kilometre of
  savanna explored, so about 15 to 20 KB of numbers each, about 0.3 MB per square kilometre before compression.
- **Where:** `colony/DeadMounds.java:128-170` (`Wear`, `WearColumn`), `:474-478` (settled), `:648-718` (save).
- **The fix proposed:** when a mound settles, drop its columns and pieces (keep the middle, shaft and ways; `isSettled`
  is already true with no wear); a settled mound with no comb or works left then costs a few numbers.

## D-8 (C, proven): dead tree shapes worked out for a place that is then skipped are kept until the game closes

- **What is wrong:** each waiting place in loaded land starts its dead tree's ten shapes in the background
  (`deadTreeShapesSoon`, kept in the static `DEAD_TREE_SHAPES` by the place's seed). They are taken out only when the
  dead tree is put in (`deadTree`, line 765). A place that `put` turns down (water, no soil, a colony within 40) never
  reaches `deadTree`, so its ten grown-tree shapes (each thousands of blocks in arrays) stay in memory. The map is not
  emptied when the server stops (`forgetAll` empties only `QUEUE`), so it also carries over to the next world opened in
  the same sitting (keyed by seed, so it does no harm there beyond the memory).
- **Where:** `colony/NewLandMounds.java:198`, `:765`, `:870-877`, `:164-167`.
- **The fix proposed:** remove the entry when a place is skipped (in `put`'s "why" branch, or in `tick` right after
  `putWithAnts`), and clear the map in `forgetAll`.

## D-9 (C, proven): the "already put in" check can never be reached; a place already put in is logged as "another colony within 40"

- **What is wrong:** `put` tests `crowded` before "already put in". A colony or dead mound already put in for this place
  stands at the same middle (the colony's id comes from the place), so `crowded` answers first and the log and the
  `skipped` count say "another colony or dead mound within 40". The intended guard works by accident; the branch after it
  is dead code.
- **Where:** `colony/NewLandMounds.java:284-292`, `:403-416`, `:133-136`.
- **The fix proposed:** test "already put in" first (and do not count it as skipped), or drop it as `crowded` covers it.

## D-10 (C, proven): a dead colony of new land is given its days since death as its age

- **What is wrong:** `raise` sets the colony's age to `max(10, pick.days())` for a dead mound: the days it has been dead,
  not the age it reached. The workers' age spread uses the same number, so a fresh dead mound's workers are all one age
  (`oldest` = 1 tick). Harmless today (the colony ends in the next line), but misleading for any part that reads the
  age when the colony ends (none found).
- **Where:** `colony/NewLandMounds.java:479-486`.
- **The fix proposed:** give it the age its stage gives (`TermiteMoundStructure.ageFor`), or say in a comment that the
  age of a colony that is ended at once does not matter.

## D-11 (C, likely): the dead works' idea of "the nest, never filled" leaves out the side rooms

- **What is wrong:** `DeadWorks.record` builds its own list of the nest's cells (shaft, planned chambers, gardens,
  founding chamber) to keep out of the galleries it fills in later. It leaves out the side rooms, which the new-land dig
  does cut (`NewLandMounds.digNest` line 528) and the framework's own list counts (`Nest.buildNestCells`, through
  `NestLayout.cells`). A second judge of "what is the nest" beside the framework's. If a gallery or foraging tunnel cell
  ever lies in a side room, it would be filled with soil in the dead mound the Matabele live in.
- **Where:** `colony/DeadWorks.java:80-95`; `framework/colony/Nest.java:870-898`.
- **Unchecked link:** whether any gallery or tunnel cell can lie in a side room (the plan says the two foraging tunnels
  never touch a room, `NestPlan.foragingTunnels`; the galleries' routes were not traced).
- **The fix proposed:** add `nest.getSideRooms(level)` to `home` (or take the layout's own cell set), one list of the
  nest's rooms.

## D-12 (C, proven): notes that disagree with the numbers

- `docs/DESIGN.md` line 167 says new land's dead mounds are "25 fresh, 55 wearing, 12 slumping, 8 old humps, so about 1
  mound in 4 to 5 reads as dead"; the structure file and the code's default say 30, 60, 6, 4 (`termite_mound.json`
  line 25, `TermiteMoundStructure.java:83`, `:91`), changed the same day for the user's "1 in 3" (`docs/status-log.md`
  line 230). Line 163 ("About 1 in 3 mounds dead") agrees with the file.
- The code's default `dead_share` is 0.75 and the file's 0.55 (`TermiteMoundStructure.java:79`, `:91`): the default is
  only used if the file loses the field, but it then gives about 4 in 10 dead, not a third (the file's own note).
- `DeadMounds` class comment, line 50 to 51: "a whole mound worn at once takes a minute or two of looks": with 256
  blocks a look every 100 ticks, a full mound of a few thousand blocks takes 10 to 15 looks, about a minute; fine. But
  the wear of a new dead mound is not budgeted at all (D-2).

## D-13 (C, proven): the tests read and set world-wide "last" things, the leak BUGS 363 warned of

- `NewLandMounds.fence` is a static box every dead tree must stay inside; three tests set it and clear it only at their
  end (`TermiteNewLandTests.java:48` to `:120`, `:63`, `IdleTests.java:89-92`). A failed check in between leaves it set,
  and a test running beside it in the same batch gets its dead tree turned down "past a test's fence". The book's
  `lastDeadTree`, `lastAnts`, `lastBurrow`, `lastTrees`, `learntWood`, `lastMillis` are the last of the whole world, the
  pattern BUGS 363 traced a flake to; `treesWaitingForTests` (BUGS 348, 440) is the same.
- **The fix proposed:** clear the fence in a `finally`, or key it to the test's own box; give the tests their own
  colony's facts (by colony id) instead of the world's last.

## D-14 (C, likely; for the mound's reader): `livingMoundNear` asks every termite colony in the world for its stage, a question that moves the asked colony's stage clock

- `livingMoundNear` (`colony/NewLandMounds.java:358-373`) calls `Mound.getStage(level)` on every Macrotermes colony,
  near or far, before it looks at the distance. `getStage` (`colony/Mound.java:345-369`) is not a plain lookup: it can
  move `workStage`, `lastProgress` and `stageCheckedAt`, and through `isStageDone`/`getParts` read the mound's plan. For
  a far colony whose mound land is not loaded, `isStageDone` counts every unloaded cell as done. Cheaper and with no side
  effect: measure the distance first, and read the stage the mound last built (a field), not `getStage`.

## Test gaps

- No test of a dead mound wearing with part of it, or of its works, in land not loaded (D-1).
- No test that `/time set` back, or `doDaylightCycle` false, leaves dead mounds ageing (D-3).
- No test of the bare patches after the colony dies (D-4); `TermiteBarePatchTests` covers only living colonies.
- No test of a new-land nest beside water (D-5).
- The put's time is tested only for a half mound and allows 400 ms (D-2); nothing times a full living mound or an old hump.
- Not checked: whether the wear's tests cover a player's block on a column and the washed-soil ring on a slope (the
  wear's tests in `TermiteMoundTests` were not opened).

## What is solid

- **The hand-over from world making** (`queue`, `takeIn`, `closing`, `forgetAll`): only a concurrent queue is touched
  from world making's threads, each place carries its own world (R8-11 fixed), nothing of the live world is read there.
- **One mound a look, the place taken out of the book before it is put in,** both saved together: no mound is put in
  twice after a reload; the colony id comes from the place, so a place cannot make two colonies.
- **The loaded-land margins of new land:** 24 blocks round the middle for a mound reaching about 13; the dead tree waits
  for 64 round and checks its own box plus one block before it touches anything (`deadTree` line 814), each try's five
  ground points one by one (`deadTreeFoot`). `GroundTop` reads no land of its own: Minecraft's height map answers the
  lowest height for a chunk not loaded, so `TreePlacer.rest` reading the ground before the box check (line 811) cannot
  load land; the box check then turns that try down.
- **The slow work in the background:** mound shapes and dead tree shapes are worked out off the game's thread and the
  place waits for them (BUGS 214), at most two at a time.
- **World making by the seed:** every new-land die is `RandomSource.create(seed)` or a fixed mix of the place; no
  `level.getRandom()` in this slice; nothing reads the season.
- **`MoundWear` is pure numbers:** heights only go down, never below the hump nor above the top; the jitter is fixed per
  place; `tuft` and `opensUp` are 1 in 3 as written; the grey "opening" takes thin spires off and keeps a broad dome.
- **The wear keeps what is not the termites':** a column under a player's block is kept, a piece under one stays, only
  mound blocks crumble, nothing under the ground is touched; the shaft is never closed (the way in); the hump stays.
- **The dead works only take the termites' own blocks** and never bury an animal (`WAIT`).
- **Save and load** of the dead mound book (every field of the wear and works, the `settled` flag) and of the bare
  patches is complete; `reloadForTests` checks the wear carries on.
- **The budget:** 256 blocks a look across all dead mounds; a settled mound with nothing left costs one check.
- **`quiet`** is set and reset in a `finally` (no sound flood on new land's wear).

Read in full: `colony/NewLandMounds.java`, `colony/DeadMounds.java`, `colony/DeadWorks.java`, `colony/MoundWear.java`,
`colony/BarePatches.java`, `worldgen/TermiteMoundPiece.java`. Read in part: `worldgen/TermiteMoundStructure.java`
60-320; `SavannaEvents.java` 155-246; `colony/SavannaParts.java` 28-77 and 271-280; `colony/Mound.java` 312-468;
`framework/colony/Nest.java` 829-920 and 1205-1260; `framework/colony/LandReady.java`, `GroundTop.java`;
`framework/colony/ColonyManager.java` 149-170; `colony/FeedingGallery.java` 163-182; `SavannaNumbers.java` (the
`Regrowth`, `MoundNumbers`, `GalleryNumbers` records); `termite_mound.json`; `macrotermes.json` (regrowth, nest);
`TermiteNewLandTests.java` 200-310; `MatabeleNewLandTests.java` 150-200. Not read: `worldgen/MoundPlaces.java`
(round 8's), `colony/MoundPlan.java`, `GalleryUpkeep.soilFor`, `TreePlacer` beyond `rest`, `MatabeleColonies`.



