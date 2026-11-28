# Round 5b, reader W: the gallery web and the termites' wiring

Read at commit `a7c0a9f0` in `C:\Ant Mod`. No code changed, nothing run or measured.
Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`.

## W-1 (A, the path proven; how often likely): one gallery block the termites cannot dig (a torch, a block a player put there, water) stops the whole web from growing, for good

- **What is wrong:** before any tip may grow, the web checks that every block of every piece is dug out ("one piece at
  a time: the last ones are dug first"). A block counts as dug only if it is air, a seed heap or a soil ramp. The
  diggers, for their part, skip every block that is not soil, and every soil block that touches water. So a gallery
  block that holds anything else (a torch or a stone block a player left after looking in, water from a player's
  bucket, a falling block that dropped in) is never dug and never counts as dug. The check then fails every second for
  the life of the colony, and no tip ever grows again, toward food or otherwise. Nothing anywhere notices it, plans
  round it, or drops that piece from the web.
- **What the player would see:** a player digs down into a gallery to watch the termites, puts a torch on the floor
  and fills the hole back with cobblestone. From then on the colony's galleries never spread again (the colony list's
  "N of M blocks" stays where it is, the tips count stays the same), however big the colony grows. The piece with the
  bad block and every piece grown from it also never count as dug (their tunnels include their parent's blocks), so no
  piece out to food is planned from them (`pieceTo` starts only from dug tunnels).
- **Where:** `savanna/colony/GalleryWeb.java:409-415` (the gate in `growTips`); `framework/colony/Nest.java:781-787`
  (`isDugOut`: air, seed pile, ramp), `:1133-1151` (`firstDiggable`: `continue` past anything not soil and past soil
  touching fluid), `:294-300` (the diggers' order uses `digCells`); `framework/colony/ForagingHoles.java:208-215`
  (`isDug` of a whole tunnel); `savanna/colony/GalleryWeb.java:766-776` (`pieceTo` only from dug tunnels).
- **The proof:** `Galleries.tick` (once a second) → `web.growTips` → for every piece, for every own block,
  `Nest.isDugOut` false → `return`. The only thing that could make it true is a digger; `Nest` hands the diggers the
  first block of `digCells` that `isSoil` and does not touch fluid, so a torch, cobblestone, planks or water is passed
  over for ever. The same gate runs in `growNow` (`GalleryCatchUp.java:72`), so the builder numbers cannot get past it
  either. A Matabele raid's seal (`RaidDefence`) blocks it the same way, but only until the seal is bitten open at night.
- **Unchecked link:** whether a piece's hole opening also waits for `ForagingHoles.isDug` of its whole tunnel (likely,
  the hole turns are another reader's).
- **The fix proposed:** no new rule; let the gate ask the diggers' own question. Today two judges answer "is there
  digging left on the web?": the gate (`isDugOut` on every block) and the diggers (`firstDiggable`). Make the gate "the
  diggers have nothing left to dig in the web" (`firstDiggable` over the pieces' `digCells` returns null), so a block
  the diggers will never touch no longer holds the web. A test: a web with a cobblestone and a water block put into one
  piece; the next tip still grows.

## W-2 (A, to measure; a new side of R5-8 and R1-36): near a grown colony the "new holes to food" check, with up to 6 route searches, runs every second instead of every 10 seconds, and up to 4 times a second while the web grows

- **What is wrong:** the check that plans gallery pieces out to rich food has its own clock (every 200 ticks). The
  builder numbers' galleries step (`growNow`) starts it by hand ("food first"), and resets its clock each time. Since
  R1-36 the builder numbers work beside a player too: every second their time is added, the nest and mound are looked
  at, and when those are done the galleries step runs. So for every grown colony a player stands by, the food check
  runs at least once a second (each second `growNow` finds nothing to grow, the time is dropped, and the next second
  starts again), and up to 4 times a second while pieces are being grown. Each run tries up to 6 foods; each try plans a
  route through the ground (`TunnelRoute.plan`, up to 6,000 columns), and a food the workers "do not allow yet" is not
  remembered as tried, so the same routes are planned again on every run. Before each route, `pieceTo` also reads every
  block of every piece's whole tunnel to see whether it is dug (every piece stores its way from the shaft, so this is
  the sum of all the pieces' lengths, about 2,000 block reads for a full web).
- **What the player would see:** a server that gets slower beside grown termite mounds whose web has used up its
  length while known logs or hay lie farther out (the usual state of a grown colony: "food waiting"). Not measured.
- **Where:** `framework/colony/BuildLife.java:124-132` (`tickNear`), `:187-205` (`slice`: galleries reached once the
  nest and mound are done; `work = 0` after), `:285-306` (`growGalleries`, `PIECES_PER_SECOND = 4`);
  `savanna/colony/GalleryCatchUp.java:52-74` (`growNow` → `checkNewHolesNow`); `framework/colony/ForagingHoles.java:386-389`
  (`checkNewHolesNow` clears the clock), `:445-480` (`FOODS_TRIED = 6`, `affords` false → `foodWaiting`, no
  `noRouteUntil`); `savanna/colony/GalleryWeb.java:765-781` (`pieceTo`: `isDug` over every tunnel, a new `GroundReader`,
  a route plan).
- **The proof:** walked as above. `growNow` is the only caller of `checkNewHolesNow` in my slice; the brain's own timer
  for the same check stays at 200 ticks. `growGalleries` returns false when `growNow` grew nothing, so `slice` drops the
  time and `tickNear` adds new time the next second.
- **Unchecked link:** how long one run takes on a real grown web (to measure with `Perf`), and how often the nest and
  mound are both "done" for a grown colony in play (a mound still growing keeps the galleries step from running).
- **The fix proposed:** remove the second caller rather than add a limit: `growNow` should not start the food check
  (the brain's own check, every 10 s, already plans food pieces near and on return); it grows tips only. If food must
  come first on return, run the food check once in `cameBack`, not on every piece. And remember "not allowed yet" foods
  the way "no route" foods are remembered, until the colony's workers or the web's length change.

## W-3 (B, proven): filling in a piece out to small food gives back its full length, though it never counted, so every eaten branch shrinks the web's room to spread

- **What is wrong:** a piece out to small food (a fallen branch) counts 0 of its length against what the workers allow
  (`small_food.share` is 0.0). When the branch is eaten and the piece led to no food for 10 days, it is filled in, and
  its full length is added to "length given back" (`filledLength`), which the even spreading may not use. So each
  branch the colony feeds on and then leaves takes its length out of the even spreading for good, although it never
  used any. Adding the piece only took length back off when there was some given back (the count never goes under 0),
  so a colony that never filled anything before loses the length outright.
- **What the player would see:** over many days, a colony that eats many fallen branches (the real Macrotermes' main
  food, the reason for the small-food rule) stops spreading its galleries even though its web is far shorter than its
  workers allow.
- **Where:** `savanna/colony/GalleryUpkeep.java:379-383` (`ownCellsCount` added whole), `:402-404`;
  `savanna/colony/GalleryWeb.java:282-289` (`length` counts small pieces at `share`), `:441-443` (the spreading stops at
  `length + filledLength`), `:876` (`addFoodPiece`: `Math.max(0, filledLength - fresh)`).
- **The proof:** a branch piece is added with `filledLength` 0 (nothing taken), counts 0 in `length()`; when the branch
  is gone `isLiveNewHole` is false, `isDue` after `fill_days`, `fillIn` adds `ownCellsCount` (its whole length) to
  `filledLength`; `length()` drops by 0. `length() + filledLength` has grown by the piece's length.
- **Also on this path (owner's call):** the length given back never returns to the spreading at all, only food may use
  it. So a colony at its full 200 workers whose unused branches filled in once never spreads at random again; it can
  only find food within `pull_reach` (12) of its tips or the wood call (16 to 20) of its holes. In grassy savanna most
  holes have grass near and are "used", so few pieces fill in; where the ground is bare it bites.
- **The fix proposed:** count what a filled piece gives back the way `length()` counted it (small pieces at their
  share); one rule for "how long is this piece" in one place (`length()` and the fill both call it).
- **Owner's call:** today the length given back by filled branches is kept for growth toward food for ever. Real
  Macrotermes keep exploring all their lives (galleries are dug, abandoned and dug anew). My pick: let the given-back
  length return to the spreading after `fill_days` more days, by the same clock, no new number.

## W-4 (B, the path proven; how often likely): a tip turned back by a remembered attack is picked again every second and holds up the growth of the whole web for days

- **What is wrong:** each second either the tip pulled hardest by grass, or else the shortest tip, is picked to grow.
  When every way on from it runs toward a place where members were attacked, the growth stops for that second ("not
  now"), and no try is counted. The next second the same tip is picked again (it is still the shortest, or still has
  the most grass past it), and is turned back again. So one tip facing an attack holds up every other tip until the
  attack is forgotten (danger fades with a half-life of a day; from its top it lasts about four days). A tip turned back
  by a neighbour is counted (three tries and it waits), but those tries are not saved, so after a restart it is tried
  again, and in the "toward grass" branch a turned-back pull still returns "done" for the second, so the even spreading
  never runs while it lasts.
- **What the player would see:** after a Matabele raid near the galleries, the termites' web stops growing everywhere
  for days, not only toward the raid.
- **Where:** `savanna/colony/GalleryWeb.java:456-467` (spread: `pickTip`, then `clear.isEmpty() && !grown.isEmpty()` →
  `return`, no fail), `:704-751` (pull: the same tip each second, `return true` at 747), `:475-492` (`clearOfAttacks`:
  no try counted), `:603-605` (`clearOfNeighbours` counts one), `:92-93` and `:1181` (`tipFails` not saved).
- **The proof:** `pickTip` takes the nearest tip not in `stopped`; `stopped` holds only tips with 3 fails, tips at
  food, tips whose own hole is kept shut and tips whose way on a neighbour is yielded. A tip whose hole is not near the
  attack but whose next piece is (the attack zone, `danger.reach` 6) is in none of them. `grow` wanders at most 20
  degrees (0 when pulled), so a zone across the way turns back every try.
- **Unchecked link:** how often the shortest or best-pulled tip faces an attack zone in play (raids hit feeding spots,
  which are tips at food; the tips next to them are the likely ones).
- **The fix proposed:** one rule for "turned back": a tip whose pieces are all refused, by an attack or by a neighbour,
  is counted a try in both branches, and the tries fade by time (stamped with game time and dropped after a day) rather
  than by restart, so nothing new needs saving. The pull branch returns false when it grew nothing, so the even
  spreading gets its turn.

## W-5 (B, proven): a piece out to rich food is planned with neither the neighbour walls nor the attack check, and the walls between neighbours only look at the other web's pieces

- **What is wrong:** "may a gallery go here?" is answered in two places for the web. The tips' growth asks three
  things: not toward an attack (`NO_GALLERY_THROUGH` on every new block), not toward a neighbour the colony yields to,
  and never within reach of a neighbour's gallery ("the webs never join"). A piece out to rich food asks only that the
  food itself is not where the colony keeps off; its route goes straight through an attack zone or right beside (even
  touching) a neighbour's gallery. And the neighbour check itself looks only at the other colony's pieces' own blocks:
  not its nest's own foraging tunnels, not its feeding galleries, not its shaft. With mounds 42 blocks apart and the
  webs reaching about 35 out (see W-12), a tip can slip between a neighbour's four arms and run beside its own tunnels.
- **What the player would see:** two colonies' galleries joined by a piece out to a log lying between them; a new
  gallery dug straight across the place a raid just hit. The upkeep then never walls the joined piece off while its
  food lasts ("a way to food in use is only stopped, never filled").
- **Where:** `savanna/colony/GalleryWeb.java:765-781` (`pieceTo`), `:825-848` (`planPiece`), `:854-881`
  (`addFoodPiece`): no `clearOfNeighbours`, no `clearOfAttacks`; `framework/colony/ForagingHoles.java:810-813` (only
  `keepsOff` of the food); `savanna/colony/GalleryWeb.java:543-556` (`galleryCellNear`: own blocks of the pieces only);
  `savanna/colony/GalleryUpkeep.java:159-161`; `framework/colony/GroundReader.java:74-79` (`mayDig` keeps out only this
  colony's own mound and rooms).
- **The proof:** the only callers of `pieceTo`/`addFoodPiece` are `ForagingHoles.tick` (above) and nothing between
  plans the route and adds it checks the route's blocks. A neighbour's dug gallery is air, so the route goes round it
  (`mayDig` needs soil), but the block beside it is soil and may be dug.
- **Unchecked link:** whether in a real world a tip reaches within 3 blocks of a neighbour's nest tunnels (needs a soak
  with two colonies at the close end of the spacing).
- **The fix proposed:** one question for the web, "may a piece of this colony's galleries go through these blocks?",
  asked by the tips and by `pieceTo` alike (attack, yielded ground, a neighbour's tunnels within reach), and the
  neighbour side answered from the other colony's whole list of tunnels (`Nest.getForagingTunnels`, which already holds
  its own tunnels, pieces, feeding ways and holes) rather than its pieces only.

## W-6 (B, proven): the gallery web, the biggest record a colony has, has no sample in the save and load check and no test of a reload in the middle of growth

- **What is wrong:** `GALLERIES` is the one big saved termite part signed up without a `sample` for
  `AwayKit.everyPartSavesAndLoads` (as are `KING_CARE`'s numbers); the check only sees what the test scene happened to
  grow. Nothing loads a web with tips, forks, parents, small pieces, backfilled pieces and pieces out to food, and
  checks it grows on the same. The load drops any tip whose hole is not a piece and keeps small pieces only if they
  are pieces; `repairSharedHoles` and `deriveOlderSave` run on every load.
- **What the player would see:** nothing today as far as I read: the save and load look whole (each field saved is
  loaded; the only things not saved are the tries (W-4) and the catch-up clock). The risk is the next change.
- **Where:** `savanna/colony/SavannaParts.java:31-39`; `savanna/colony/GalleryWeb.java:1110-1182`;
  `savanna/colony/GalleryUpkeep.java:449-498`.
- **The fix proposed:** a `sample` that grows a small web by hand (an arm, a fork, a piece out to small food, a
  backfilled parent, a last use, a foraged hole), so the round trip checks every field.

## W-7 (C, proven): a tip's failed tries are never cleared when it grows, and the set of small pieces is never cleared when one is filled

- **What is wrong:** `tipFails` keeps an entry for every tip that once failed and then grew on (the entry is under its
  old hole, never used again); `smallPieces` keeps the hole of every small piece ever filled in. Both are small (one
  entry a piece) and the save drops the second's leftovers, so this is tidiness, not lag.
- **Where:** `savanna/colony/GalleryWeb.java:620-653` (success removes the tip, not its fails);
  `savanna/colony/GalleryUpkeep.java:407-437` (`removePiece` does not touch `smallPieces`).
- **The fix proposed:** clear both where the piece leaves (`removePiece`) and where the tip is replaced (`addGrown`).

## W-8 (C, to measure): every piece stores its whole way from the nest's shaft, so the saved web and the "is it dug" reads grow with the square of its depth

- **What is wrong:** a piece's record is the parent's whole tunnel plus its own blocks. A web of about 50 pieces with
  ways of 20 to 50 blocks stores about 2,000 block positions (16 KB a colony in the save, written whenever the colony
  manager is marked changed, which the web does on every growth, adoption and fill), where its own blocks are about 200.
  Every `ForagingHoles.isDug` of a piece reads its whole way again (W-2).
- **Where:** `savanna/colony/GalleryWeb.java:995-1001` (`cells = tip.way().tunnel() + plan.cells()`),
  `savanna/colony/Galleries.java:413-423` (`saveTunnels`).
- **The fix proposed:** to measure first. If it matters: save each piece's own blocks and its parent, and build the
  whole way on load (the parents map is already saved).

## W-9 (C, proven; a new side of R5-6): filling a piece in reads the blocks beside each filled block with no loaded-land check, and writes with neighbour updates

- **What is wrong:** `fillIn` checks that each block it fills is in loaded land, then `soilFor` reads the block below
  and the four beside it to pick the soil. A block on a chunk edge reads the next chunk, which is not checked; reading a
  block of a chunk not loaded makes Minecraft load it on the spot. `setBlockAndUpdate` also tells the neighbours, as in
  R5-6. Reachable only when a piece lies on the edge of loaded land (the web reaches about 35 to 50 blocks from a nest a
  player is within 48 of, so a low view distance).
- **Where:** `savanna/colony/GalleryUpkeep.java:329-333`, `:373-378`, `:390-400`.
- **The fix proposed:** ask the shared "may this colony change this block now?" (R5-26) once it exists; until then,
  `LandReady.at` on each side read in `soilFor` (as `Nest.touchesFluid` already does).

## W-10 (C, proven): comments that disagree with the code

- `savanna/colony/Galleries.java:40-41` says the holes along the tunnels "stay usable for ever (the user's decision)";
  the same comment's last bullet, and `GalleryUpkeep.removePiece`, take a filled piece's holes out for good.
- `savanna/colony/GalleryWeb.java:465`: "(no dead end: it may grow there later)", but `clearOfNeighbours` (604) has
  just counted a try toward the dead end (W-4).
- `savanna/SavannaEvents.java:200`: a comment about the raid view forgetting players stands alone above the
  crocodile's dismount handler (two comments stacked; the first belongs to `onLogOut`/`onServerStopping`).
- `savanna/SavannaEvents.java:26-29`: the class comment names only the termites' strength and the trees; the hub also
  runs the dead mounds, new-land mounds, raid view, raid trails, spawning rules and phantoms.

## W-11 (C, proven): `isLeaf` searches every piece's parent each time, and is asked once per piece in loops over the pieces

- **What is wrong:** `isLeaf` is `parents.containsValue(hole)`, a walk over every piece; `isDue` (which calls it) runs
  for every tip each second in `growTips` and for every piece every 10 s in `upkeep`. About 50 by 50 small steps: not
  lag today, the shape to avoid as the web grows.
- **Where:** `savanna/colony/GalleryWeb.java:135-137`, `:449-455`; `savanna/colony/GalleryUpkeep.java:223-227`,
  `:256-260`.
- **The fix proposed:** none needed now; if the web grows, keep a count of children per piece beside `parents`.

## W-12 (C, balance; no change proposed without the owner): the web's `reach` (50) and `max_length` (600) never bind; a full colony's galleries reach about 35 blocks

- **The sums:** 200 workers at `workers_per_block` 1 allow 200 blocks. The 4 arms use about 64 (to 16 out). Growing
  the shortest tip first by 4-block pieces and forking every 8 to 12 blocks, the 136 blocks left take the tips to about
  35 blocks out before the next fork doubles them and the length runs out. So the range (farthest hole plus the 6-block
  graze) is about 41; `reach` 50 is met only by pieces pulled straight to food; `max_length` 600 is three times what 200
  workers allow.
- **Against the world:** mounds 42 to 82 blocks apart. Webs of about 35 meet when mounds are closer than about 70
  (half the pairs?), and the meeting rules (walls, yielding) then matter; at 82 they never meet. Real Macrotermes
  colonies' foraging ranges are said to reach tens of metres and to abut their neighbours' (I am not sure of a figure);
  that fits.
- **Beside the open flake BUGS 167** (a 38-worker colony's web at 70 blocks against 58 allowed): "allowed" is not one
  number. The even spreading stops at `lengthAllowed`, but a piece out to food may go one piece over
  (`affords`, `lengthAllowed + piece_length`), big dead wood 32 over (`wood_call.over_limit`), and a fork's second piece
  a little over; the test's first stage checks the strict `lengthAllowed`. My reading does not explain 12 over with hay
  (it is not wood), so this is a lead for that bug, not its cause.
- **Owner's call:** today a grown colony's galleries stop at about 35 blocks. My pick: keep it (it fits the spacing);
  if the 50 in the species file is meant, it needs about twice the length per worker.

## What is solid

- **Colony dice and layering:** every random pick in the web uses `colony.random()` (forks, wander, the shuffle); the
  web reaches the brain only through its part's hooks (`ColonyHooks.Underground`), the questions
  (`NO_GALLERY_THROUGH`, `HOLE_KEPT_SHUT`, `KEEP_OFF`) and the news (`NEIGHBOUR_MET`); nothing reads the season; no
  statics in the four gallery classes, so nothing leaks between worlds.
- **Loaded land:** every block the web reads to plan goes through `GroundReader`, which checks `LandReady` per column
  and per block; `planArms` and `growTips` wait for the land round them (`loadedAround`); the upkeep remembers dug holes
  only from loaded land, and the away side never reads a block (`tickAway`).
- **Never walling anyone in:** `fillIn` refuses while any living thing stands in or next to the piece, while an insect
  on a hidden trip walks through it, while a hole of it is open, or while a player could see any of it; one piece at a
  time, the farthest first; the 4 arms and live ways to food are never filled.
- **The pure planners:** `planArms`, `grow`, `pickTip`, `planPiece` are pure and proven on made-up landscapes
  (`GalleryProofTest`); a route never opens to the air (`isWalledIn`), never under the food, never two pieces at one
  hole (checked in `grow`, `addGrown` and `pieceTo`).
- **Near against away:** away, only the night's holes are picked from the remembered dug holes; on return the fills
  owed are done out of sight (20 a second for 30 s) and the growth by the builder numbers' time follows the same rules
  as near (W-2 is about how often, not what).
- **Old saves:** two pieces at one hole are repaired on load; a save without first-arm records rebuilds them; the
  game clock set back is caught in `upkeep`.
- **`SavannaParts`:** every saved part has a save and load, every unsaved part says why; the galleries tick near and
  away and hear "colony ended"; the parts that tick only near (closed holes, alarm, raid defence, mushrooms, bare
  patches) keep game-time stamps and pick up again on return (the alarm and soldiers are unsaved by design).
- **`SavannaEvents`:** its per-event work is cheap (the fire hook returns at once for anything but fire; the trail hook
  walks the colony list once; the chunk hook reads only the chunk's structure starts); the stores of the trees, new
  land, river sites and the savanna gate are forgotten when the server stops.

Read in full: `GalleryWeb.java` (1-1183), `Galleries.java` (1-455), `GalleryUpkeep.java` (1-499),
`GalleryCatchUp.java` (1-92), `SavannaParts.java` (1-333), `SavannaEvents.java` (1-360), `TunnelRoute.java` (1-314),
`GroundReader.java` (1-102). Read in part: `SavannaNumbers.java` (330-420), `macrotermes.json` (the galleries and
neighbours sections), `framework/colony/Nest.java` (230-330, 776-787, 900-945, 1133-1151, 1216-1228),
`ForagingHoles.java` (40-76, 208-228, 386-406, 440-480, 788-833), `BuildLife.java` (17-205, 270-306),
`AwayLife.java` (550-609), `ColonyManager.java` (80-95), `Neighbours.java` (94-120, 199-242), `ClosedHoles.java`
(85-115, 188-203), `LandReady.java`, `ColonyPart.java` (160-185), `AwayKit.everyPartSavesAndLoads`, `Raids.java`
(1261-1283), `TreeFire.changed`, `Forest.chunkLoaded`, `TermiteForagingTests.theGalleriesGrowTipByTipWithTheColony`
(its comment and checks). Not read: `HoleTurns`, `DeadWoodCall`, `DeadWorks.colonyEnded` (other readers' files),
`TermiteNeighbourTests` beyond its name, `GalleryProofTest` beyond its name. Searched: `docs/BUGS.md` for "galler" and
the earlier rounds' files for my classes' names; no earlier round names these files, and no open bug repeats a finding
here (BUGS 167, an open flake, is tied to W-12 as a lead).
