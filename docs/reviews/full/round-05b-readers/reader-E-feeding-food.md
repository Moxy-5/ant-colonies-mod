# Round 5b, reader E: feeding, the food blocks and the numbers

Read at commit `a7c0a9f0` in `C:\Ant Mod` (branch `claude/motion-glue`). No code changed, nothing run or measured.
Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`.

## E-1 (A, proven; a new side of R5-3): eaten dry grass grows back green and only dries on Minecraft's random ticks, so near a spectator, and in land far from every player, a colony's grass never becomes food again

- **What is wrong:** the termites eat only dry grass (`macrotermes.json` lines 84-85: `"state": { "growth": "dry" }`).
  A tuft they eat away is remembered by the bare patches part and put back after `food.regrowth.ticks` (72,000, three
  days) as **green** grass (`macrotermes.json` lines 105-111, `"state": { "growth": "green" }`). From green it only
  becomes food by drying two steps (green, drying, dry), and each step happens only on a random tick, one random tick
  in 27 (`DryGrassBlock.DRY_CHANCE`). Minecraft gives random ticks only to chunks with a player in survival, creative
  or adventure within 128 blocks; a spectator counts for nothing (checked in Minecraft's own code: `ServerChunkCache`
  line 367 ticks a chunk only when `ChunkMap.anyPlayerCloseEnoughForSpawning`, and `ChunkMap.playerIsCloseEnoughForSpawning`
  line 984 answers no for a spectator). So:
  1. **Near a spectator** (the owner watches in spectator) every tuft the colony eats comes back green and stays green
     for good. The grass round a watched colony is eaten once and never again.
  2. **The world's own green and drying tufts** (worldgen puts 4 tufts in 9 down green or drying,
     `worldgen/configured_feature/patch_dry_grass.json` and `patch_tall_dry_grass.json`, weights 2, 2, 5) never become
     food there either.
  3. **Far land** (a colony away, its land loaded but no player within 128 blocks, or not loaded at all): the tufts
     do not grow back while the land is not loaded (`BarePatches.tick` line 120 waits for loaded land), and when they
     do they are green with nobody to dry them.
- **Near a player in survival or creative** it works, but slower than the sums so far assumed: a random tick reaches a
  block about every 68 seconds (3 a tick in a 4,096-block section), so one drying step takes on average 27 x 68 s,
  about 1.5 game days, and two steps about 3 days. **From eaten to food again is about 6 days, not 3** (reader F's sums
  used 3).
- **What the player would see:** in spectator, a colony that strips the dry grass round its holes, leaves rings of
  green tufts it never touches, and lives on what dead wood and fallen branches it reaches. A full colony needs about
  50 pieces a day (reader F); without regrowth its band of grass lasts weeks, not for ever (the sums below), and a
  colony with no dead wood in reach then shrinks and its queen starves. The owner, watching, sees termites walk past
  green grass to nothing.
- **Where:** `savanna/block/DryGrassBlock.java:61-100` (drying only in `randomTick`), `TallDryGrassBlock.java:55-71`;
  `savanna/colony/BarePatches.java:109-140` (grows back the profile's `grows_back` state, green);
  `macrotermes.json:84-85, 105-111`; `framework/species/SpeciesProfile.java:619-628` (`PlantFood.matches` needs the
  `growth` value).
- **The proof:** eaten at `FoodStore.clippedGrass` (`framework/colony/FoodStore.java:640-647`) -> `eatAway` -> no cast
  for grass (`castFor` line 832, grass is not `EatenInStages`) -> `plantEaten` -> news `PLANT_EATEN` -> `BarePatches.eatenAway`
  (line 70) keeps the tuft with `growsAt = eatenAt + 72000` -> `BarePatches.tick` puts `numbers.growsBackState()`, which
  is `dry_grass` with `growth=green` (`SavannaNumbers.Regrowth.growsBackState`, lines 495-508). The green tuft is no
  food (`PlantFood.matches` line 624: `hasValues` fails), and `FoodMap.tick` line 119 forgets the spot. Only
  `DryGrassBlock.randomTick` moves it on, and only random ticks call that.
- **Unchecked link:** how many tufts a chunk really gets (the patch feature's success rate against the vanilla
  savanna grass already standing there); the sums below use an estimate.
- **The fix proposed:** one clock in place of two. The bare patches part already keeps every eaten tuft with its own
  game-time clock; let it also keep the tuft until it is dry: put it back green at `ticks`, then set it to drying and
  to dry at its own later times (about 1.5 days each, the same average as today), in loaded land, out of the random
  ticks altogether. Grass the colony never ate keeps drying by random ticks as now (it is the world's, not the
  colony's). A test: eat a tuft, push the clock past regrowth and the two drying times with no random ticks
  (`/gamerule randomTickSpeed 0` in the test), and the spot is dry grass the colony can eat again.
- **Owner's call:** today a watched (spectator) colony's grass never comes back, and near a survival player it comes
  back in about 6 days. My pick: grass comes back green after 3 days and is dry again 3 days later, by the colony's own
  clock, whoever watches. Also say whether worldgen should keep putting down green tufts that may never dry near a
  spectator (my pick: yes, keep them; they are the look of a savanna, and the fix above does not need them).

## E-2 (B, proven; the size of it in play to measure with a soak): a far termite colony forages "by the land" with no limit from the land, so it is never short of food, while the same colony watched lives on real grass; and one real plant left switches that free food off

- **What is wrong:** while a termite colony is away and its forager numbers find no remembered plant to work this
  second, each free forager brings a piece every `TRIP_TICKS + 2 x graze x TICKS_PER_BLOCK` = 200 + 2 x 6 x 8 = 296
  ticks, about one piece every 15 seconds, out of nothing (`FieldLife.forageByTheLand`, the comment calls it "a known
  departure", BUGS 203). Nothing ties that to how much grass or wood the land round the colony holds or how fast it
  grows back: the only limit is the room in the garden (`roomNow`). Two things follow:
  1. **Food from nothing, as much as the garden takes.** With about 60 foragers (a third of 200) that is about 4 pieces
     a second, about 1,900 in a night of about 470 seconds: the garden is full every night, against the 50 a day a full
     colony eats. A far colony is never food-limited. The walk is only the graze radius (6 blocks), not the gallery
     out to the hole (up to 50 blocks), so a far trip is also cheaper than a near one.
  2. **The gate fights the rule it stands in for.** The free food runs only when `placedNow == 0`, that is when no
     forager number worked any real plant this second (`FieldLife.java:470`). One remembered tuft near tonight's holes
     takes about 1.4 forager numbers (`work`, line 541, a one-at-a-time plant with its 10-second rest) and switches the
     land foraging off for all the other free foragers that second: the colony then brings in about one piece every 25
     seconds instead of four a second. The more real plants the brain still remembers, the hungrier the colony.
- **What the player would see:** colonies far from the player fill up and reach the 200 cap however bare their land;
  the colony the player watches is held by its real grass and wood (and, in spectator, by E-1). The Matabele raids
  away (`RaidsAway`) take from far colonies that never run short. In a long absence the difference grows; on return
  the player finds the far colonies bigger than the one beside them. (R5-2's nodule fault pulls the other way: it
  bites when forage only keeps up with eating; with this free food the forage is far above eating, so R5-2 bites far
  colonies less than feared, until this is fixed.)
- **Where:** `framework/colony/FieldLife.java:268-294` (`forageByTheLand`), `:410-474` (`forageWith`, the gate at 470),
  `:523-575` (`work`), constants `:52-62`.
- **The proof:** `forageWith` sums `placedNow` in `work` (line 542); `forageByTheLand(level, free)` runs only with
  `awayNow && web != null && outside && placedNow == 0 && free > 0` (line 470); inside, `landGot += free x 20 x seconds /
  (200 + 2 x walk x 8)` with `walk = web.grazeRadius()` = `food_radius` 6 (`Galleries.grazeRadius`, line 339-341), then
  one piece per whole `landGot` while `roomNow > 0`. No other bound.
- **Unchecked link:** the real forager share of 200 workers (`Colony.shareOut` with `nurses_from_workers` was not
  worked through); the 60 above is the comment's "at least a third".
- **The fix proposed:** keep one rule: the land out of sight gives what the land near would give. Give the colony a
  "land's yield" from its own grazed area (the same numbers the sums below use: about 0.7 pieces a day per tuft spot,
  the wood's pieces as it lies), counted while away, and let `forageByTheLand` take only from that allowance, after
  the real plants, not instead of them (drop the `placedNow == 0` gate). The cheap form: a pieces-a-day number per
  colony in the species file, measured once from a soak of a watched colony. This also answers R5-2's question of how
  often a far colony is food-limited.
- **Owner's call:** today far termite colonies always have food and near ones do not. My pick: far colonies get the
  same food a day as a colony of that size near a player would on the same land (a measured allowance), so the world
  away is no richer than the world watched.

## E-3 (B, proven; how often to measure): two colonies cutting at the same plant each keep their own count of pieces cut, so a shared tuft, branch or log gives nearly twice its pieces

- **What is wrong:** how many pieces have been cut off a plant is kept per colony (`FoodStore.grassClips`, one map per
  colony's food store). The block itself shows it only for the plants eaten in stages (dead wood, fallen branches,
  pulp heaps), and even then a colony reads the block's stage only on its first cut (`putIfAbsent`, lines 630 and
  698-699); dry grass, tall dry grass, hay and the bleached branch show nothing (`BleachedBranchBlock.eatenAlready`
  always answers 0). So colony A cuts 2 of a tuft's 4 pieces, colony B cuts 3, A cuts 2 more and the tuft goes at A's
  4th: 7 pieces came off a 4-piece tuft. A 48-piece hay bale shared by two colonies gives up to 95.
- **When it happens:** termite colonies lie 42 to 82 blocks apart; each forages up to its gallery reach (50) plus the
  graze (6) from its middle, so their ranges overlap by tens of blocks. The neighbour rule walls the galleries apart
  within 3 blocks, but the graze round two holes 4 blocks apart overlaps, and the forager numbers away cut by the same
  per-colony count (`cutByNumbers`, line 696). Also the same tuft may be cut out of sight by one colony's numbers and
  in sight by the other's real foragers.
- **What the player would see:** nothing directly: food from nothing, a little, where two colonies meet.
- **Where:** `framework/colony/FoodStore.java:67` (the per-colony map), `:623-652` (`clippedGrass`), `:696-702`
  (`cutByNumbers`), `:878-880` (`getPiecesCut`); `savanna/block/BleachedBranchBlock.java:31-34`.
- **The proof:** read above; no shared store of cuts exists (`grassClips` is a private field of each colony's
  `FoodStore`, saved with that colony, lines 1088-1101).
- **The fix proposed:** one truth per block: keep the pieces cut per position in one place for the whole level (the
  colony manager's saved data), read and written by every colony; the per-colony map goes. No new number.

## E-4 (B, proven; a new side of R5-11): the feeding-spot numbers were set for colonies of 600 to 700 workers and not revisited when the cap became 200

- **What is wrong:** `macrotermes.json` `nest.new_holes` (line 132-133) says "a full colony of 600 to 700 workers keeps
  15, a half-grown one of 200 keeps 6", and the owner's pick was "10 to 15 by-day feeding spots for a big colony". Since
  the honest sizes (2026-11-11, `max_workers` 200) a full colony is 200 workers, so it keeps at most
  `mostFor(200)` = min(15, 200 / 30) = **6** spots at small food (`SpeciesProfile.NewHoles.mostFor`, lines 891-896).
  Three more numbers of the same age do nothing now: `reach_full_at_workers` 500 with `reach_min` 10 and `reach_max` 40
  (only read when the galleries are off or have no range yet: `ForagingHoles.foodReach` line 865, `Colony.foragingRadius`
  line 790; at 200 workers it would give 22, not 40), and `nest.galleries.max_length` 600 (with `workers_per_block` 1 the
  galleries may be at most `lengthFor(200)` = 200 blocks long, `SavannaNumbers.java:395-397`).
- **What the player would see:** a full mound with about 6 feeding spots at branches instead of the 10 to 15 the owner
  chose; fewer by-day places for the Matabele scouts to find (the raid balance, `tools/raid_balance.py`, was worked
  out for 8 to 14).
- **Where:** `macrotermes.json:132-133, 148`; `framework/species/SpeciesProfile.java:872-905`.
- **The proof:** the sums above, from the file's numbers and the two methods.
- **The fix proposed:** change the numbers, not the code: `per_workers` about 14 (200 / 14 = 14 spots at the cap),
  drop `reach_min`, `reach_max`, `reach_full_at_workers` from the termite file (the galleries' own reach rules), set
  `max_length` to what 200 workers can have or leave it with a note. Rewrite the note. Check `tools/raid_balance.py`
  against 200 workers.
- **Owner's call:** today a full colony feeds at about 6 small spots (plus any big wood). My pick: 14, the owner's
  "10 to 15" at today's full size.

## E-5 (B, proven): what the player sees of the food supply and no test checks

- **Regrown grass becomes food again** (E-1): `TermiteBarePatchTests.dryGrassEatenByAHoleLeavesBareGroundAndGrowsBack`
  checks only that a green tuft comes back (lines 85-90); it would still pass if the grass never dried again, which is
  the case near a spectator.
- **Tall grass grows back** (E-6): no test eats a tall tuft and looks at what comes back.
- **The free food away has a bound** (E-2): no test checks how much `forageByTheLand` brings in a night against the
  land; the soaks show only that far colonies live.
- **Two colonies at one plant** (E-3): no test.
- **The wood call's numbers** (`reach` 16, `big_reach` 20, `min_blocks` 20): `TermiteFoodTests.aStandingDeadTreeIsSensedAndGetsAGalleryToItsFoot`
  covers one dead tree; nothing checks that a small log (under 20 blocks) is not called, or the reach's edge.
- **The fix proposed:** add the checks to the scenes that exist (the bare patch scene for E-1 and E-6; an away scene of
  `TermiteAwayTests` for E-2), not new scenes.

## E-6 (C, proven; the owner's call): an eaten tall tuft grows back as a short one, so the tall grass round a colony is gone for good

- **What is wrong:** the regrowth has one `grows_back` block, `antcolonies:dry_grass` (`macrotermes.json:107`). A tall
  dry tuft (8 pieces) eaten comes back short (4 pieces) and only grows tall again by bone meal
  (`DryGrassBlock.performBonemeal`). Over the first weeks the grazed band loses its tall grass and half its pieces on
  those spots; worldgen puts about one tall patch per chunk against four short ones, so the supply drop is perhaps a
  tenth to a fifth (an estimate).
- **Real biology:** savanna grass regrows from its roots to its full height within a wet season; a grazed patch comes
  back short and grows tall again. Nothing here grows a short tuft tall.
- **Where:** `savanna/colony/BarePatches.java:115, 137`; `SavannaNumbers.Regrowth` (one `grows_back`).
- **The fix proposed:** the regrowth puts back the kind of plant that was eaten (tall for tall, short for short), from
  the tuft's own record, instead of one fixed block; no new number.
- **Owner's call:** today tall grass eaten is gone for good. My pick: it comes back tall.

## E-7 (C, proven; real biology, the owner's call): leaf litter is in the diet on paper only; in the mod's savannas there are almost no leaves it matches

- **What is wrong:** the diet's `#minecraft:leaves` with `only_decaying` (`macrotermes.json:87`) matches only vanilla
  leaves cut off their tree (`PlantFood.matches`, `SpeciesProfile.java:620-623`, needs the `persistent` and `distance`
  properties). The giant savanna trees, which replaced the vanilla acacias in new savannas (`BranchShedding` comment,
  line 25), have their own leaves (`TreeLeavesBlock extends Block`, no such properties) and dead leaves
  (`kind.deadLeaves()`), and neither is food. Real Macrotermes eat a great deal of leaf litter besides grass and wood.
  `docs/DESIGN.md` line 15 also still lists vanilla `short_grass` and `tall_grass`, which the file (rightly: green
  grass is no food) does not.
- **Where:** `macrotermes.json:87`; `savanna/block/TreeLeavesBlock.java:15`; `docs/DESIGN.md:15`.
- **The fix proposed:** add the giant trees' dead leaves to the diet (a few pieces, not rich); bring DESIGN line 15
  into line with the file.
- **Owner's call:** today termites eat no leaves in the mod's savannas. My pick: they eat the giant trees' dead leaves
  lying under the crowns, 2 pieces each.

## E-8 (C, to measure): the wood call measures every piece of dead wood in its ring every minute, known or not, and the "is it big" check measures up to 4,000 blocks each time a far find waits

- **What is wrong:** `DeadWoodCall.listen` runs once a minute per colony over every second column within 20 blocks
  (`big_reach`) of every dug hole and growing tip (holes lie every 4 blocks along up to 200 blocks of gallery, so the
  ring is a band about 40 wide, a few thousand columns), and for each dead wood block it meets it follows the whole
  piece of wood (`measure`, up to `MOST` 4,000 blocks, 6 neighbour reads each) before it asks whether the colony knows
  it already (line 84-85). A standing dead giant the colony has known for weeks is measured again every minute.
  `isBig` (line 107) measures again from scratch every 10 seconds while a find past the budget waits
  (`GalleryWeb.affords:809`, from `ForagingHoles.tick` every `NEW_HOLE_CHECK_TICKS` 200, up to `FOODS_TRIED` 6 foods).
  Land not loaded is never read (good). Likely well under a millisecond a minute; not measured.
- **Where:** `savanna/colony/DeadWoodCall.java:60-101, 107-113, 143-173`.
- **The fix proposed:** keep the feet the colony already knows out of the loop before measuring (skip a column whose
  block lies within the known wood's last measure), and let `isBig` use the size learnt when the wood was called (the
  colony knows it; no new measure).

## E-9 (C, proven): "small food" is judged by two numbers in two sections of the same file

- **What is wrong:** whether food is small (a branch) is asked twice: by `nest.new_holes.small_pieces` 8 for the spot
  count (`ForagingHoles.isSmall`, line 889-899) and by `nest.galleries.small_food.pieces` 8 for the digging budget
  (`GalleryWeb.isSmallFood`, line 292-295). Today both are 8; change one and a branch counts as small for one rule and
  big for the other.
- **The fix proposed:** one number (the galleries can read `new_holes.small_pieces` through the profile), the other
  dropped.

## E-10 (C, proven): "the wood termites eat" is listed in three places

- **What is wrong:** the species file's food list (`macrotermes.json:88-99`), the block tag `antcolonies:termite_wood`
  (used by `MudCrustBlock:176` and `DeadWorks:236`, which add `FallenBranchBlock` and hay by hand) and the classes in
  `DeadWoodCall.isDeadWood` (line 195-198, which leaves out the bleached branch and fallen branches on purpose). A new
  dead wood added to the file but not to the tag gets eaten but no mud plate and no dead-works care; the tag has no
  `dead_wood` (the old fallen acacia) at all.
- **Checked and fine:** no vanilla log, planks or stripped log and no living tree's wood is in any of the three, so a
  player's wooden house and the living giant trees are never eaten (DESIGN line 19's "eat player-built wood" option
  does not exist; the answer today is a fixed no).
- **The fix proposed:** the tag is the one list of termite wood; the species file names `#antcolonies:termite_wood`
  and the two code places read the tag.

## E-11 (C, proven): the savanna number records check no ranges, and some defaults in code differ from the file's sense

- **No ranges:** nothing in `SavannaNumbers` rejects a bad number (no `Ints.POSITIVE` as the framework's `Founding`
  uses). Examples that pass quietly: `soldiers.minors_per_major` -1 makes `wantedMajors` divide by 0 and ask for about
  2 billion majors (`SavannaNumbers.java:259-265`); `regrowth.grows_back` misspelt makes every eaten tuft vanish for
  good with no message (`BarePatches.tick:124-126` only un-bares the ground); `regrowth.ticks` 0 or negative regrows at
  once. R5-34 found `max_height` 0; same family.
- **Defaults that disagree:** `WoodCall.DEFAULT` has `reach` 0 (the call off) when the whole `wood_call` block is
  missing, but `reach` 16 when the block is there without `reach` (lines 441-448). `GalleryNumbers.enabled` defaults to
  false. A species file trimmed by hand gets different behaviour from what "leave it out" suggests.
- **Read by nothing:** none found in the termite records (every field I traced is read); the dead ones are E-4's
  `new_holes` numbers, which are the framework's.
- **The fix proposed:** a small `checked()` per record (as `Ints.POSITIVE`) with a load message naming the field; one
  default per field.

## E-12 (C, proven): the grass's drying and regrowth have no socket for the shared season

- **What is wrong:** real savanna grass greens in the wet season and dries in the dry season, and Macrotermes forage
  far more on dry grass in the dry season. Here drying is a fixed random-tick chance (`DryGrassBlock.DRY_CHANCE` 27) and
  regrowth a fixed 3 days (`food.regrowth.ticks`); the comment says "later, seasons (Serene Seasons)". Nothing reads
  the season today, so the shared-season rule is kept, but no socket is left. If E-1's fix moves drying to the bare
  patches' clock, that clock is the natural socket (ask the shared season source how fast grass dries).

## E-13 (C, likely): beyond 256 mud casts, the wood eaten through is no longer "inside", so a big dead giant's far parts stop being eaten by day

- **What is wrong:** the casts joined to a feeding gallery are gathered by a search that stops at `MAX_CASTS` 256
  (`FeedingGallery.java:44, 85`), with the comment "a fallen tree is about 8 blocks". A dead giant is hundreds of
  blocks; once more than 256 casts lie joined, the rest are not inside the nest (`Nest.java:1070`), so the wood beside
  them is not "feedable from inside" (`FoodStore.isFeedableFromInside`) and only the night foragers eat it.
- **Unchecked link:** whether a colony eats a giant that far in play (256 blocks of wood is 8,000 pieces, about 160
  days of a full colony).
- **The fix proposed:** none needed now; raise or drop the cap if a soak ever shows it.

## E-14 (C, proven): comments that disagree with the code

- `FeedingGallery.java:36` "a fallen tree with its stump is at most about 8" and `:43` "a fallen tree is about 8
  blocks": the dead giants are far bigger (`DeadWoodCall.MOST` 4,000).
- `macrotermes.json:132` the new-holes note (E-4).
- `docs/DESIGN.md:15, 19` (E-7, E-10).
- `DryGrassBlock` and `TallDryGrassBlock` show no eaten stage: a tuft 3 of 4 pieces gone looks whole, so a player
  cannot see grazing until a tuft vanishes. Not wrong, but the `FallenBranchBlock` and dead wood do show it.

## The foraging sums (a reading with `macrotermes.json`'s numbers; the worldgen amounts are estimates, nothing measured)

**The demand (reader F):** a full colony (200 workers, 20 soldiers, the pair) needs about **50 pieces a day**; one piece
becomes one nodule and four worker meals. A young colony of 5 to 20 workers needs a few pieces a day plus 7 nodules
for each new worker.

**What one plant gives** (one piece a forager trip; `FoodStore.clippedGrass` cuts one piece a call):

| Food | Pieces | How it comes back |
|---|---|---|
| dry grass tuft | 4 | grows back green after 3 days, dry about 3 days later by random ticks (E-1): about 6 days, **never near a spectator or far from players** |
| tall dry grass | 8 | grows back as a short tuft (E-6) |
| dead bush | 6 | does not; rare in savanna |
| fallen branch | 8 a block, 2 to 3 blocks | each grown giant tree drops one every 1 to 2.5 days (`BranchShedding` 0.4 to 1 a day), at most 3 lying under it |
| dead wood, bleached log or wood | 32 a block | does not; a fallen acacia of about 8 blocks is about 256 pieces, 5 days of a full colony; a dead giant is thousands |
| rotten wood, baobab fibre | 16 a block | does not |
| pulp heap | 12 (fewer for a thin heap) | sinks away by random ticks (so near a spectator it lasts) |
| hay bale | 48 | a player's; eaten bale vanishes, no drop |

**The supply on the land (estimate):** worldgen tries 4 short patches of 32 and 1 tall patch of 24 a chunk; on flat
ground about a quarter of the tries land at the right height, fewer where the vanilla grass stands: perhaps 20 to 30
tuft spots a chunk (about 0.1 a column), 5 in 9 of them dry at first. A full colony's galleries are at most 200 blocks
long (E-4) with a hole every 4 blocks; the graze is 6 blocks round each hole: a band of roughly 2,500 to 3,000
columns, about 250 to 300 tuft spots, **about 900 to 1,600 pieces of grass at the start**.

**Near a player in survival or creative (random ticks run):** each tuft spot gives about 4 pieces every 6 days, 0.7 a
day: about 175 to 200 pieces a day from the band, against 50 needed. **A full colony feeds itself for good on grass
alone**, and the holes taking turns (`holesFor`: about 10 a night of 6 foragers each) rest each patch between visits.
Trips are not the limit: a trip is about 200 ticks plus the walk, one piece each; a few dozen foragers over a night of
about 9,450 ticks (12,550 to 22,000) can carry hundreds.

**Near a spectator (E-1):** no grass comes back. The band's 900 to 1,600 pieces last **about 3 to 5 weeks** at 50 a day,
less as the colony grows; then the colony lives on branches and dead wood. Branches keep falling (the tree days are not
random ticks; how many grown giant trees stand in a band is not known to me, round 7 and 8's ground): each grown tree
gives about 8 to 20 pieces a day, so 3 to 6 trees in reach would carry a full colony, none would not. A colony with no
dead wood and few trees shrinks.

**Away (E-2):** the land is not the limit at all: the forager numbers bring in what the garden takes every night. A far
colony never starves for forage (R5-2's nodule fault aside).

**A young colony (5 to 20 workers):** 1 or 2 holes a night, each grazing about 110 columns, about 11 tufts, 20 to 40
pieces of dry grass, different holes on different nights. It gets going easily, near or away; the founding batch
needs no food at all.

**Two colonies 42 to 82 blocks apart:** each forages out to 56 blocks; their bands overlap. They do not starve each
other (the supply above is 3 to 4 times the demand where grass regrows), but a shared plant gives up to twice its
pieces (E-3). Near a spectator the overlap halves each one's grass; there it matters.

**Food from nothing:** E-2 (away, unbounded) and E-3 (shared plants). Not found: a block that gives pieces and is never
used up (every food block is destroyed or cast at its last piece and its count saved); pieces counted twice within one
colony (real foragers and numbers share one count); a tuft eaten and still standing that the same colony eats again (the
count stays until the block catches up).

**The giant trees' fallen logs:** eating does not outrun them in any sense that matters: one fallen giant is months of
food, and a colony's gallery reaches only 32 of its blocks at once (`FeedingGallery.MAX_FOOD_BLOCKS`), eating on through
the casts.

**A player's hay and farms:** hay bales are in the diet on purpose ("big food jackpot", DESIGN line 15). A barn within
about 50 blocks of a mound is found by a forager within 5 blocks of it, then gets a tunnel and a feeding gallery under
it where the floor is soil, or a mud runway if it lies near a hole; a bale is gone after 48 trips, leaving nothing.
Savanna villages' hay piles are in reach of some mounds. Real termites do eat thatch and stored hay, so this fits; the
owner should know it happens. Wheat and sticks are in the `termite_food` item tag, so dropped ones are likely carried in too (the item pick-up
goal not read).
Crops, planks, logs and the living trees are never eaten.

**What I could not settle without a soak:** the real tuft count per chunk; how many grown trees stand in a full
colony's band; the near foragers' real trips a night; how many pieces `forageByTheLand` brings a far colony a day.

## What is solid

- The diet is dead plant matter only: dry grass only when dry (`growth=dry`), leaves only when cut off their tree, the
  living giant trees' wood and every vanilla log and plank left alone; one piece a trip, as real foragers carry.
- Every food block is used up exactly: eaten in stages where it has them, its count saved (`grassClips` in the colony
  save), the last piece leaving a mud cast (wood) or nothing (grass, hay, a branch in the open); a plant cut out of a
  wall's way leaves its uncut pieces lying for the foragers (`cutAwayForTheForagers`).
- The food blocks burn as they should (dry grass like vanilla grass, green half as well; fallen branches, dead wood,
  frass, pulp heaps and the dead giants' wood all have fire numbers) and need ground under them; dry grass, fallen
  branches and pulp heaps break when a piston pushes them (`PushReaction.DESTROY`; the wood blocks not checked).
- Tall grass: the colony lists its top half, eats it, and both halves go; the regrowth finds the right spot under it.
- `FeedingGallery`, `FeedingSpots` and `DeadWoodCall` never read a block in land not loaded (`LandReady` before every
  read; BUGS 316 and R11-1's fixes hold), never use the world's dice, and reach the brain only through the colony's
  parts and hooks (`ColonyHooks.FEEDING_WAYS`). The feeding spots a raid hears of leave out places the colony keeps
  off and holes spent, and `byDayPlaces` scans no blocks round the spots (cheap for every scout); the box scan is only
  for the command.
- Near foragers never walk out to food that is gone: `findGrass` reads the block itself each time, and the food map
  forgets a plant whose block is no longer food.
- The bare patches: eaten tufts kept with their own game-time clock, grown back only in loaded land and never into the
  nest, a hole, a runway or a standing animal; saved and loaded.
- `SavannaNumbers.GalleryNumbers`' short constructors and the codec give the same defaults.

Read in full: `savanna/colony/FeedingGallery.java`, `FeedingSpots.java`, `DeadWoodCall.java`, `BarePatches.java`;
`savanna/block/DryGrassBlock.java`, `TallDryGrassBlock.java`, `FallenBranchBlock.java`, `PulpHeapBlock.java`,
`DeadWoodBlock.java`, `DeadTreeWoodBlock.java`, `SoftDeadWoodBlock.java`, `BleachedBranchBlock.java`, `FrassBlock.java`;
`macrotermes.json`; the tags `item/termite_food.json`, `block/termite_wood.json`; the dry grass and branch worldgen files;
`savanna/tree/BranchShedding.java`. Read in part: `SavannaNumbers.java:1-673` (every termite record; 674-903 are the
Matabele records, read only by their headings); `framework/colony/FoodStore.java:250-886, 1088-1101`;
`FieldLife.java:50-62, 200-620`; `FoodMap.java:40-205`; `ForagingHoles.java:380-500, 855-915`; `Colony.java:420-590,
770-799`; `framework/species/SpeciesProfile.java:520-905`; `GalleryWeb.java:340-400`; `Galleries.java:213-362`;
`Soldiers.java:235-295`; `TreeBlocks.java:133-190, 379-430`; `GroundTop.java`; `Nest.java:853-883, 1055-1075`;
Minecraft's `ServerChunkCache.java:360-375` and `ChunkMap.java:950-995`; `TermiteBarePatchTests.java` and the test names
of `TermiteFoodTests.java`. Not read: the forage, carry and store goals in `framework/.../entity/goal/` (the
one-piece-a-trip rule taken from `clippedGrass`), `FallenTreeFeature` and the tree worldgen amounts, the other food
tests' bodies.
