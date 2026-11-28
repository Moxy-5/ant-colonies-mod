# Round 5b, reader H: the foraging holes, hidden trips, the worker and the mud

Read at commit `a7c0a9f0` in `C:\Ant Mod`, no code changed, nothing run or measured.
Short paths: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is `framework/src/main/java/com/antcolonies/...`.

## H-1 (A, proven): a creature in a gallery puts the hidden termites back, and they go under again the very next tick, so they flicker and slip past it unseen

- **What is wrong:** once a second the hidden trips look for a break-in. A block filled or opened, or a creature that is
  not the colony's own standing in the stretch, puts each traveller back in the world where it would be by now. But
  the rule that lets a termite go under (the legs ask "is this cell hidden?") looks only at blocks, never at creatures.
  So a termite put back because a Matabele raider (or a player, or a spider) stands in the gallery goes under again on
  its very next step, which starts on the next tick. A second later the check finds the creature again and puts it
  back again, a little farther along. This repeats until the termite's time-based place along the stretch is past the
  creature. The "put back" place is worked out from the clock alone, so the termite can come up on the far side of the
  creature without ever having been in the cell beside it.
- **What the player would see:** termites in a dug-open or raided gallery that blink into view for a tick each second
  and are gone again; a raider in a gallery (BUGS 361's trapped raiders sit there for a long time) that the termites
  walk "through"; the owner's rule "if anything breaks into a gallery, every insect travelling in it shows up as a real
  one" is kept for one tick only. Each blink is an entity removed and added again (Minecraft's add and remove, the
  chunk's entity lists, the client told twice), for every traveller in that gallery, every second.
- **Where:** `savanna/colony/HiddenTrips.java:171-175` (`isHidden`: cells, blocks beside, the closed-for-danger cells;
  no creature), `184-198` (`goUnder`: no check of the stretch for creatures), `212-223` and `260-285` (`brokenInto`:
  the creature check), `314-338` (`comeOut`); `framework/nav/AntNavigator.java:1073-1076` (goes under at the start of
  any step), `1173-1212` (`goUnder`), `1228-1246` (`cameUp` sets `stepTicks = 0`, so the very next tick is a step start).
- **The proof:** a trip is broken into by a raider standing on a cell of its run (`brokenInto` 279-281). `comeOut` puts
  the insect at `trip.reached(now)` (93-101: only the clock) and calls `cameUp`, which keeps the route and sets
  `next`, `stepTicks = 0`. Next tick `AntNavigator.followRoute` reaches line 1073 (`stepTicks == 0 && leg == 0`),
  `goUnder` asks `isHidden(at.cell())`: true, because no block changed (a raider is not a block). It scans forward
  over hidden cells; if at least 12 steps (`hidden_trip: 12`) are left the insect goes under again with a new trip from
  here. The raider's cell is still on that run, so one second later (`tick`, line 212) it is broken into again. Nothing
  between the two calls remembers the creature.
- **Unchecked link:** how many steps a termite usually has left past a raider (if fewer than 12 it stays out and walks
  for real, so the blinking stops 12 steps before the end of the stretch).
- **The fix proposed:** one judge, not two: the once-a-second look keeps the set of cells it found broken into (a
  creature in them, or a block changed) until the next look finds them clear, and `isHidden` answers false for those
  cells, as it already does for the closed-for-danger cells (`closesCell`). Then a termite put back walks for real past
  the creature and meets it, which is what the owner's rule asks for.

## H-2 (B, to measure): all day long every idle builder, about every two seconds, reads every cell of every gallery to find a hole to plug, though every hole is already plugged

- **What is wrong:** a builder with nothing to do asks "is there a hole to plug?" (`PlugHoleGoal.canUse`, at random 1
  time in 20 when Minecraft asks, every other tick, and at every ask in the two seconds before it counts as idle). By
  day the answer starts with "yes, it is plugging time" and then walks every hole of the web. For each hole it first
  checks that the whole gallery out to it is dug (`isDug`: one block read per gallery cell), and only then whether the
  hole is open (one read). So it reads the whole web cell by cell to learn that nothing is open. The galleries may
  hold up to 600 blocks (`max_length`), with a hole every 4 blocks (`hole_spacing`), and each hole's "tunnel" is the
  whole way from the nest to it (the holes along one arm share their first cells, and those are read again for each).
  A grown web is in the order of 100 holes times 20 to 40 cells: a few thousand block reads per ask.
- **What the player would see:** nothing directly; a steady cost by day at a big colony, growing with the web and the
  number of real builders. Not measured.
- **Where:** `savanna/entity/goal/PlugHoleGoal.java:57-66`; `framework/colony/ForagingHoles.java:314-337`
  (`pickHoleToPlug`: `isDug` before `isOpen`, line 323), `174-186` (`holesToPlug`), `208-215` (`isDug`);
  `framework/entity/AbstractAntEntity.java:621-623` (`looksForWork`). The same order (dug first, open second) is in
  `holeToOpen` (`ForagingHoles.java:251-258`, at night from the dig target search, digging is another reader's) and
  `countOpen` (260-269, the colony list only).
- **The proof:** `pickHoleToPlug` returns early only when it is foraging time and no hole is kept shut (316-318). By
  day `all` is true, so the loop runs over `holesToPlug(level)`, which is every tunnel of `tunnels(level)` (the nest's
  own two, the arms, the pieces to food, and every hole along them, `Nest.getForagingTunnels` 904-942), and for each it
  calls `isDug` first. `isDug` walks the tunnel's cells to the end unless one is not dug; on a finished web every cell
  is dug, so it walks them all. Nothing caches the answer.
- **Unchecked link:** the real cost in a grown web (how many holes, how long their tunnels are) and how many builders
  are real mobs at once. That is why this is "to measure".
- **The fix proposed:** no new rule, only the order: ask `isOpen(hole)` first (one block read, and false by day for
  every plugged hole), and only for an open hole ask `isDug`. The same swap in `holeToOpen` (there "not open" is the
  usual case at night, so it would ask `mayOpen` and the hole's own block first). The answers do not change.

## H-3 (B, likely): the holes' blocks stay as they were when the colony went far; a far colony's holes can stand open all day for good

- **What is wrong:** the night's turns are picked "near" and "away" alike (`HoleTurns.round`), but only real workers
  open and plug the holes' blocks. A colony counts as away not only when its land unloads but also when its land is
  loaded and no player is within 64 blocks (`AwayLife.isHere`, "far colonies"). Then it lives as numbers, and nothing
  touches its holes: the brain's turns and the closed-for-danger holes go on in the numbers (the numbers graze only
  round `holesInTurn`), while the blocks keep whatever the last real night left. Nothing on return plugs or opens
  them out of sight; the builders do it in view, as the player arrives.
- **What the player would see:** standing 70 to 120 blocks from a mound (the land loaded, the colony "far"), its holes
  left open from the last night stay open by day, day after day; a hole closed for danger (an attack seen from near)
  stays open in the world while the brain counts it shut. On coming within 64 blocks by day, a row of workers plugs
  holes in front of the player. By night it is the other way round: tonight's holes are all shut and are bitten open
  as the player walks up.
- **Where:** `savanna/colony/Galleries.java:369-379` (`tickAway`: "never reads or changes a block"),
  `savanna/colony/HoleTurns.java:99-110`, `295-370`; `framework/colony/AwayLife.java:163-175` (`isHere`: far counts as
  away), `553-580` (`settle`: the ledger catches up rooms and young, not holes); `framework/colony/ForagingHoles.java`
  (no catch-up path: grep for "plug" in `AwayLife`, `FieldLife`, `Colony` finds none).
- **The proof:** walked above: in the far state `Colony.tick` does not run the near parts (the colony ticks away), so
  no `PlugHoleGoal` runs (its workers are numbers) and no part writes a hole block; `settle` on return calls the
  brood and the ledger, neither of which knows the holes.
- **Unchecked link:** whether anything else reads a far colony's open holes (a Matabele raid's scouts against a far
  termite colony, BUGS 452 "termites nobody can get at", is the raid readers' ground). Without that it is a looks
  issue seen from 64 to about 128 blocks (beyond that the player cannot make out a hole).
- **The fix proposed:** no new rule: the return catch-up that already sets the rooms right out of sight
  (`NestLedger`, `settle`) also sets each loaded hole to what the brain says now (plugged by day; tonight's turns
  open at night; kept-shut plugged), out of every player's sight, as the rooms are. Optional, and only if the raids
  read hole blocks: the same when the colony goes far.

## H-4 (B, proven): the tests of the holes and hidden trips would still pass with H-1 and H-3 in the game, and nothing tests the plug against a creature in the hole

- **What is wrong (test gaps):**
  - `TermiteNewHoleTests.hiddenTripsGoUnseenAndShowUpWhereTheyAreWhenTheGalleryIsBrokenInto` (`:342-498`) breaks in
    only by opening a roof block. No test puts a creature (a raider, a chicken, a player) in a gallery with travellers
    in it, so H-1 is untested; worse, its last stage allows on purpose a termite "put back" to start a new hidden trip
    at once (`:479-482`), which is exactly H-1's loop.
  - No test runs a colony far (land loaded, no player within 64) over a night and a day and looks at the hole blocks
    (H-3).
  - `ForagingHoles.plug` refuses to bury anything not of the colony (`:351-370`), and the plug goal waits for it
    (`PlugHoleGoal.java:129-131`). `holesTakeTurnsNearFoodFirstAndMoreInTheRain` already has a chicken sitting 100
    ticks in a hole that is closed for danger (so the builders try to plug it then, `TermiteForagingTests.java:291-345`),
    but it never checks the chicken was not buried: a buried chicken suffocates, `chicken.isAlive()` turns false, and
    the test simply goes on to its next stage. A break here would bury a player's pet or trap a raider unnoticed.
  - The long test `termitesOpenTheirForagingHolesAtNightAndPlugThemByDay` (`TermiteForagingTests.java:32-126`) uses
    a young colony (6 workers), so it never meets a web big enough for H-2's cost, and checks only that holes open and
    are plugged with "everybody inside"; it would pass if a forager were shut out at dawn and stayed outside (it waits
    for `!isAnyoneOutside` with no time limit inside the day; a shut-out forager that walks in later by any way is
    fine for it) and if the grace (`PLUG_GRACE_TICKS`, 30 s) were gone.
- **What the player would see:** nothing until one of these breaks.
- **The fix proposed:** add the creature to the existing hidden-trip scene (a chicken set into the gallery ahead of a
  traveller: the traveller must show up and stay real while the chicken is there), and a plug-with-a-mob check to
  `holesTakeTurnsNearFoodFirstAndMoreInTheRain` (while its chicken sits in the hole: assert the hole is still open and
  the chicken was not hurt by suffocation before the test kills it). No new test classes.

## H-5 (B, proven; a new side of R5-3): the mud crust and the mud casts age and crumble only on Minecraft's random ticks, so near a spectator they stand still for ever

- **What is wrong:** a mud crust plate whose cell has been filled again "crumbles away" (1 in 4 each random tick), and
  a crust dries with age; a mud cast whose log is eaten crumbles (1 in 12 each random tick) and dries. All of it runs
  in the blocks' own `randomTick`, and nothing else does it. Minecraft gives a chunk no random ticks when the only
  player near is a spectator, or every player is 128 blocks or more away (R5-3, checked by the lead in Minecraft's
  `ServerChunkCache.tickChunks` and `ChunkMap.playerIsCloseEnoughForSpawning`).
- **What the player would see:** the owner watches in spectator. An eaten fallen tree leaves its log-shaped mud cast
  standing for good; leftover plates of a filled-in gallery stay; every crust and cast stays "fresh" (lightest colour,
  softest to break). The runway crumbling is not hit: it is done by the colony's own check
  (`framework/colony/ForagingHoles.java:679-722`, `crumble`, on the colony dice), which shows the two clocks side by
  side for the same kind of block.
- **Where:** `savanna/block/MudCrustBlock.java:140-164` (randomTick: crumble and age), `MudCastBlock.java:44-59`;
  `savanna/registry/SavannaBlocks.java:134-141` (`randomTicks()` on the crust; the cast through `pieceProperties`).
- **The proof:** as R5-3; the two blocks have no other path that ages or removes them (grep for `MudCastBlock`,
  `MudCrustBlock`: only `DeadWorks`, `FeedingGallery`, the runway `crumble` and tests touch them).
- **Unchecked link:** whether anything reads a crust's or cast's age for play (I found only the player's breaking
  speed, `MudCrustBlock.getDestroyProgress`), so I grade it B (looks), not A.
- **The fix proposed:** the same one clock R5-3 proposes for the mound soil: whatever part already walks these cells
  (the runway `crumble`, `DeadWorks` for the casts) dries and clears them by game time; the random tick goes. If the
  owner prefers to keep it small: leave it as is, and note it under R5-3 so the one fix covers all the mud.

## H-6 (C, likely): a hidden termite is put back without checking its cell is open, and the "already in the world" guard folds a second copy into the numbers

- **What is wrong:** two small holes in `comeOut`. (1) A break-in "filled at" a cell puts the traveller at the floor
  its clock says it has reached; nothing checks that cell is still open. A player can place a block in a gallery cell
  where a hidden termite "is" (Minecraft refuses a block only where a real entity stands), and the termite is then put
  back inside that block. (2) If the insect is somehow already in the world (the code says "should not be"),
  `comeOut` returns false and the trip is folded into the numbers: one more worker counted while the real one walks.
- **What the player would see:** (1) rarely, a termite inside a block it then suffocates in or walks out of oddly;
  (2) one worker too many in the count, only after a crash between two saves, if ever.
- **Where:** `savanna/colony/HiddenTrips.java:212-223` (break-in), `93-101` (`reached`: clock only), `314-338`
  (`comeOut`: `getEntity` guard at 327-329, no cell check), `341-353` (`foldIn`).
- **The proof:** walked in the lines named; `brokenInto` returns "filled at" for the first closed cell (265-267) and
  the put-back index never looks at which cell that was.
- **Unchecked link:** for (2), whether the colony's save and the chunk's save can ever disagree that way (the trip
  removes the insect with `CHANGED_DIMENSION`, which Minecraft never saves with its chunk).
- **The fix proposed:** in `comeOut`, step back along the run to the last open floor before the first filled cell;
  when the insect is already in the world, just drop the trip (nothing to fold in).

## H-7 (C, proven): the hidden ways notice a changed web only by its count of holes

- **What is wrong:** `refreshCells` works the gallery cells out again every 10 seconds, or at once only when the
  number of tunnels changed. A piece filled in and another added in the same 10 seconds keeps the count, so for up to
  10 s the legs see the old cells as hidden ways. `ClosedHoles` uses the list itself (`workedOutFor == tunnels`,
  `ClosedHoles.java:154`), which the nest replaces whenever the web changes (`Nest.getForagingTunnels` 927-941).
- **Where:** `savanna/colony/HiddenTrips.java:149-166`.
- **What the player would see:** almost nothing (a filled cell is not walkable, so a route there replans anyway).
- **The fix proposed:** compare the list as `ClosedHoles` does, one way for both.

## H-8 (C, proven): the termite hole block (the old chimney crown) is no longer placed by the game, but its comment says it sits on every chimney

- **What is wrong:** `TermiteHoleBlock`'s comment says it sits at the tower top, each side chimney and the entrances.
  No game code places `TERMITE_HOLE` any more (only three tests and the creative tab, grep `TERMITE_HOLE`); the
  chimney mouths are `TermiteMouthBlock` now (its own comment says so: "like the old chimney crown"). The block stays
  for old worlds and is still counted as mound (`Mound.java:916`). Its entity tag lets workers, the king, queen and
  nymphs through, not the soldiers.
- **Where:** `savanna/block/TermiteHoleBlock.java:15-22`; `savanna/registry/SavannaBlocks.java:79-82`.
- **The fix proposed:** say in the comment that it is kept for old worlds and tests only.

## H-9 (C, proven): small leftovers in the holes' books

- `HoleTurns.holesInTurn` (`HoleTurns.java:117-130`) can list a hole twice (a live hole to rich food is also a
  candidate for tonight's turns, 308-317, and is added again at 123-127). The only reader looks for the nearest hole
  (`FieldLife.grazedFrom`), so nothing goes wrong; it is only extra work.
- `ClosedHoles.left` (`ClosedHoles.java:60`, saved) keeps a hole left for good even after its gallery piece is filled
  in and the hole is gone (`GalleryUpkeep.java:416-421` clears the turns, not `left`), so the colony list keeps
  counting it ("N left"). `waiting` is cleaned (221), `left` never is.
- `HoleTurns.pickTurns` near (`:308-317`) asks `candidates.contains` on a list (slow only with very many holes), and
  checks `isDug` (every cell) before the cheap checks, as in H-2.
- **The fix proposed:** a set for the candidates; `holesInTurn` built as a set; `left` trimmed to the colony's holes
  when the upkeep fills a piece.

## H-10 (C, likely; a new side of R5-19): a closed hole is shut by game time, so a player who sleeps sees it shut about twice as many days

- **What is wrong:** the danger that closes a hole fades on game time (`DangerMap`, `getGameTime`, half-life 24,000
  ticks), while the colony's foraging hours run on the day clock (`Colony.isForagingTime` through `isDay`). Sleeping in
  a bed moves the day clock to morning but not game time. The design's "stays shut for about two and a half days" is
  then about five sun-days for a player who sleeps each night. The turns themselves are safe: `lastTurn` uses game
  time only to order the holes, and a slept-through night simply ends the round (`HoleTurns.round`, 99-110).
- **Where:** `framework/colony/DangerMap.java` (148-265, every `now` is game time); `savanna/colony/ClosedHoles.java`;
  `framework/colony/Colony.java:433-440`.
- **Unchecked link:** the alarm and danger readers own `DangerMap`; I did not read it in full.
- **Owner's call:** today a raided hole stays shut 2.5 game days, longer by the sun with a bed. My pick: leave it
  (real termites avoid a raided site for days, so a few more is still real), but count it in the R5-19 clock fix if
  that one changes the colony's day count.

## What is solid

- **One judge for "may this hole open?"** `ForagingHoles.mayOpen` asks `isHoleKeptShut` first, then foraging time and
  the hole's turn; `wantsPlugged` and `pickHoleToPlug` ask the same question. So the night's turns, the danger
  closing and the plug goal never undo one another: a closed hole is never bitten open, and a hole is plugged only
  when it is shut for danger or the foraging time is over. The raid's packing seals gallery cells, not holes.
- **Near and away pick the night's holes the same way**, with the same numbers and the colony's own dice
  (`HoleTurns.pickTurns`; the away list sorted first so the dice decide). `inRound` and `tonight` are saved, so a
  reload in the night keeps its turns; a bed, `/time set` and `/tick rate` only end or start a round.
- **The plug claims cannot stick (not R5-5's shape):** a claim lasts 600 ticks and is pruned by the next worker to look
  (`ForagingHoles.java:320-321`), so a plugger that dies or unloads frees its hole within 30 s.
- **The plug never buries anything not of the colony** and nudges nestmates down into the tunnel first
  (`ForagingHoles.plug`); the plug goal waits for it and gives up after 30 s.
- **Dawn grace:** holes wait until nobody is outside (searched over the whole foraging area since R1-9) or 30 s.
- **Hidden trips keep the layers apart:** the navigator only asks "is this cell hidden?" and hands over a stretch;
  `HiddenTrips` never picks where an insect goes. The same insect object comes back with its goal and route
  (`cameUp` checks the route is the one it went under on). It counts as a real mob while away, is saved with the
  colony (not its chunk, `CHANGED_DIMENSION`), is folded in when the colony goes away or the far end stays unloaded a
  minute, and walls never close over a traveller (`anyWalksThrough`, used by the upkeep). Every block read there is
  behind `LandReady` (no chunk loaded by a look, R5-6 is not hit here).
- **`ClosedHoles` is cheap:** worked out once a second or when the danger memory or the web changes, each cell read
  once. A new hole in place of a left one is at least 7 blocks from the place, beyond the 4 that leave holes, so it
  is not left again at once; `NOT_LOADED` waits instead of giving up.
- **The worker's goal table:** the plug goal shares priority 2 with shaping, nursing and sealing, so none of them can
  take a worker from another mid-job (Minecraft replaces a goal only by a lower number); only fleeing, retreating,
  eating and carrying a load home come first, and the plug goal needs empty mandibles, so no two goals take a worker
  in turn.
- **Biology:** holes opened from inside at night and in the rain, plugged before dawn with soil from inside, a share
  of the holes used each night (food first, a few rested ones to explore at the web's edge), a raided hole closed at
  once and avoided for days, a site raided twice left for new holes farther on: all match what is known of
  Macrotermes foraging. I am not sure of a real figure for the share of holes open per night (1 per 6 foragers is a
  pick, not a measured number).
- **Blocks:** crust and cast drop nothing (empty loot tables, as the mound soil they are made of), the crust is
  destroyed by pistons and cannot be washed away by water; the cast's "still needed" search is bounded (64 casts) and
  runs only on 1 random tick in 12.

## What I read

Read in full: `savanna/colony/HoleTurns.java`, `ClosedHoles.java`, `HiddenTrips.java`;
`savanna/entity/goal/PlugHoleGoal.java`; `savanna/block/TermiteHoleBlock.java`, `MudCrustBlock.java`,
`MudCastBlock.java`; `savanna/entity/TermiteWorkerEntity.java`; `framework/colony/ForagingHoles.java`.
Read in part: `framework/nav/AntNavigator.java` (1060-1259), `framework/colony/Nest.java` (290-337, 781-787,
904-947), `framework/colony/Colony.java` (231-233, 433-461, 546-565, 849-856), `framework/colony/AwayLife.java`
(63-75, 163-175, 235-264, 347-373, 391-402, 547-584), `FieldLife.java` (431-485), `ColonyManager.java` (90-125),
`framework/ModEvents.java` (30-70), `framework/entity/AbstractAntEntity.java` (505-534, 621-652, 2299-2306),
`savanna/colony/Galleries.java` (240-420), `savanna/registry/SavannaBlocks.java` (75-142), the species file
(10-60, 100-160), `SavannaNumbers` (`holesFor`, `exploreOf`), the tests `TermiteForagingTests.java` (30-365) and
`TermiteNewHoleTests.java` (340-585); Minecraft's `Entity`, `LivingEntity` removal code.
Not read: `DangerMap`, `GalleryUpkeep`, `GalleryWeb`, `RetreatGoal`, `GoInsideGoal` and `DigGoal` (other readers'
ground; where a finding leans on them it says so).
