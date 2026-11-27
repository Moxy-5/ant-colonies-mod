# Round 6, reader N: the Matabele nests, the queen, the brood and the prey pile

Read at commit `b72cba68` in `C:\Ant Mod\.claude\worktrees\review-round-6`. No code changed, nothing run or measured.
Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`.

## N-1 (A, proven for the path; likely for how often): termites held in a carrier's jaws are lost when it folds into the numbers

- **What is wrong:** a raider walking home or down to the pile with a bundle of stung termites (`MatabeleLoads.TERMITES`)
  that is folded into the colony's numbers loses the bundle. The fold only knows the framework's own loads.
- **What the player would see:** a raid comes home laden; the player walks off (or the land unloads, or a stuck carrier
  is tidied away) while the carriers are still on their way down; the pile never gets those termites. A raid's whole
  catch is often a day's food for the colony (5 termites for 100 workers), so the colony goes hungry and raids again
  for nothing.
- **Where:** `framework/colony/AwayLife.java:357-371` (`loadOf`: egg, body, nurse food, seed, else `NONE`),
  `:274-276` (`frozenOf` writes that load), `:293-317` (`foldLoaded` folds and discards every member of a far colony),
  `:343-354` (`foldInStray`), `:427-450` (`foldIn`: `NONE` adds nothing); `savanna/entity/MatabeleLoads.java:29-33`
  (the TERMITES load is its own kind, not an ITEM load); `framework/entity/AbstractAntEntity.java:409-417`
  (`getCarriedSeed` is empty for it, so `isCarryingSeed`/`isCarryingBread` are false).
- **The proof:** `loadOf` asks `isCarryingEgg`, `isCarryingCorpse`, `isCarryingBread`, `isCarryingSeed`; a TERMITES load
  answers no to all (`getCarriedSeed` reads only ITEM and CROP loads), so `Load.NONE`; `foldIn` case `NONE` does nothing;
  `foldLoaded` then `entity.discard()`. The raid itself is guarded: `Raid.toNumbers` (`savanna/colony/Raid.java:953-969`)
  reads the jaws first and moves them to `hiddenCatch`. Not guarded: every carrier no longer in a column, that is from
  the raid's end (`Raid.end`, `:1501`) until `BringPreyGoal` lays the bundle (`BringPreyGoal.java:111-113`), the hurt
  following the trail home alone (`limp_ticks` 6000), and carriers sent home "with no way to the pile"
  (`BringPreyGoal.java:123-127`). Folded by: a far colony's look (`foldLoaded`, once on going away, then every
  `FAR_LOOK_TICKS`), the land unloading past the grace (`freeze`, then `tickAway` 401-406 or `cameBack` 490), and a
  stray tidied by `Strays`.
- **Unchecked link:** how long the window is in play (the walk from the entrance down the chimney to the queen's room;
  longer for limpers and stragglers). Not measured.
- **Also on this path:** a TERMITE_REMAINS load (`MatabeleLoads.java:24-26`) folds as nothing too, so its layer never
  reaches the heap (harmless: a heap layer). A real worker folded in the 3 s it holds a "meal" look item
  (`EatGoal.java:123-125`, `WorkerFood.look`, `PreyPile.piece()`) folds as NURSE_FOOD and is counted in as a whole new
  termite (`foldIn` 439-445, `PreyPile.countedIn`): a termite from nothing, rarely. Round 3's R3-36 noted the meal held
  as an item; this is its Matabele side.
- **The fix proposed:** no new rule, one socket in the existing load row: `LoadKind` already has a `Drop` handler for a
  death; give it a "folded into the numbers" handler beside it, and let `loadOf`/`foldIn` ask the load's own kind first.
  TERMITES then counts its termites onto the pile (`PreyPile.bringInCounted(now, count)`, the path raids as numbers use),
  TERMITE_REMAINS calls `loadDropped`. A test: a far colony folds a carrier holding 5 termites; the pile's count rises by 5.

## N-2 (A, proven): a Matabele colony whose workers all die keeps its queen for ever, and its dead mound stays taken

- **What is wrong:** once every worker of a Matabele colony is dead (starved with no termites in reach, raids failing,
  old age), the queen alone counts as "founding", and a founding queen never gets hungry. She lives on in her room for
  ever. Real Megaponera queens are wingless and cannot found alone (fission only); a queen with no workers dies.
- **What the player would see:** a dead mound with a lone Matabele queen in it that never dies, year after year. Since
  one colony holds one mound (`MatabeleNest.java:118`), no other queen can ever settle there. In a world where raids
  often fail (BUGS 306), starving colonies are not rare.
- **Where:** `framework/colony/Colony.java:763-765` (`isFounding`: not queenless and no members besides co-founders);
  `framework/colony/QueenCare.java:458-461` (`royalsGetHungry` is false while founding), `:263-266` (`isSpent` is false
  with no `founding` section, which Megaponera has none of), `:441-449` (`hungerRunsOut`: `satisfied` true, so the clock
  restarts every second); `savanna/entity/MatabeleQueenEntity.java:121-124` (her only death is `queenStarves`).
- **The proof:** workers dead, so `getMemberCount() - coFounderCount() == 0` and `isFounding` is true; `royalsGetHungry`
  false; `isSpent` false (no founding); so `satisfied = !(false || false) = true`; `queenLastFed = now`; never starves.
  `Colony.tick` ends a colony only when queenless and empty (`:697`), so the colony too lives on.
- **Why it is so:** the rule was made for a spawn-egg queen waiting for spawn-egg workers to join her (design 2026-10-24;
  the test `MatabeleColonyTests.java:72` asserts it). Nobody thought of a colony that ran down to its queen.
- **The fix proposed:** one rule for a `colony_fission` species, no new number: a queen whose colony has had workers
  (its `bornAt` and any `eggsLaid`, or simply a count that once was above 0) and has none left is not founding: she
  starves on her own clock like any queen (5 days). The spawn-egg case keeps its grace. A test: a colony of 5 whose
  workers are killed; its queen dies within `starve_ticks` and the mound is free again.
- **Owner's call:** today a lone Matabele queen never dies; the reader's pick: she starves in 5 days (as the file's own
  `starve_ticks`), or dies at once since a real ergatoid queen alone has no way to feed.

## N-3 (B, a reading, not measured): the colony's hunger counts only the adults, while a new worker costs 11 termites

- **The arithmetic** (`megaponera_analis.json`): a termite is one layer (`raids.carry.termites_per_layer` 1) and feeds
  20 workers for a day (`worker.diet.workers_per_piece` 20, `meal_interval_ticks` 24000): 100 workers eat 5 termites a
  day. The queen takes `queen.meal` 6 termites for each egg; a larva `brood.larva_meal` 5. So one new worker costs 11
  termites, while a worker eats 3 termites in its whole 60-day life. A colony of 100 loses about 1.7 workers a day to
  old age (`lifespan_ticks` 1,440,000), so holding steady costs about 18 termites a day for the young and 5 for the
  adults: the young eat nearly four fifths of the food.
- **What does not fit:** "hungry" (`Raids.isHungry`/`fedShare`, `savanna/colony/Raids.java:700-720`) is the pile's
  layers times 20 against the workers: one day of adult food only. With 5 termites on the pile a colony of 100 counts as
  fed, sends no scouts and wants 1 raid, while its queen alone wants 6 for one egg. The pile is emptied by the queen and
  the larvae and the colony flickers between fed and hungry; raids follow the flicker, not the colony's real need.
- **How it came to be:** step 1e set the queen's 6 and the larva's 5 in layers when a layer was 10 termites feeding 10
  ants (`docs/DESIGN.md` line 195: "to confirm against how many termites that is"); honest sizes (2026-11-11) made a layer
  1 termite feeding 20, but `queen.meal` and `brood.larva_meal` were left as they were. In worker-days a new worker went
  from 1,100 to 220; nobody checked it against the raids' catch (`raids.catch.per_raider` 0.1, `most_catch` 10, at most
  4 raids a day: about 3 to 30 termites a day as numbers).
- **Fix proposed:** measure first (a 10-day soak line per colony: termites in, eaten by workers, queen, larvae; workers
  born and died). Then one rule: `fedShare` counts what the colony eats in a day, adults plus the queen's next egg and
  the hungry larvae, so the raids answer the whole need. No new number.
- **Owner's call:** what a Megaponera larva eats is not something I have a paper figure for (ponerine larvae are fed
  whole prey items, several over their growth); today a new worker costs 11 termites; the reader's pick: keep 11, make
  the hunger count it.

## N-4 (B, real biology; owner's call): egg and brood times are minutes on a scale where a game day is a real day

- **Where:** `megaponera_analis.json` `queen.incubation_ticks` 3600 (3 minutes from a full meal to the egg),
  `brood.hatch_ticks` 1200 (the egg hatches in 1 minute); the larva grows as fast as nurses bring 5 termites (minutes).
  Against them, on the file's own TIME scale (`raids._scale_note`: a game day a real day), the worker lives 60 days and
  a wound heals in a day.
- **What is wrong:** real ponerine brood takes weeks from egg to adult (I know this for ponerines in general, about one
  to two months, not as a checked Megaponera figure). Here food is the only limit: a fed colony turns termites into
  workers within minutes, and `max_young` 10 is the only brake.
- **What the player would see:** after a good raid, eggs, larvae and new workers within a few minutes of play.
- **Owner's call:** today an egg becomes a worker in minutes; the reader's pick: look up Megaponera development times
  and put them on the TIME scale (then `max_young` and the queen's rate need a second look), or write down that brood is
  fast on purpose.

## N-5 (B, proven): the prey pile's count and its heaps drift apart, and nothing squares them

- **What is wrong:** the count of termites (`edible`) is "the truth" and the heaps only follow it. When a heap goes
  without the count knowing (a player breaks it, a piston, water washing it out: the block has `noCollission`,
  `PushReaction.DESTROY`, `canSurvive` needs a floor, and no loot table), or when a bundle is counted with no room for a
  layer (`bringIn`, `PreyPile.java:121-130`: the layer is skipped, the count still rises), the count stays higher than
  what lies there. Nothing ever compares them.
- **What the player would see:** break the prey pile in a Matabele nest: the colony still counts itself fed, so no
  scouts go out and no raid for up to a day (`Raids.isHungry` reads the count, `Raids.java:709-711`); the real ants near
  the player find nothing to eat (`findFood` needs a heap, `PreyPile.java:422-428`) and go hungry; the numbers inside go
  on eating termites that are not there (`takeCounted`). After a day the ghost termites rot, are "carried out" from an
  empty room and the count is 0 again.
- **Where:** `savanna/colony/PreyPile.java:117-133` (`bringIn`), `:189-194` (`rot`), `:289-305` (`pickUpLoad`: a rotten
  layer with no heap goes to the ledger), `:422-439`; `framework/colony/NestLedger.java:217-229` (a negative ledger with
  no heap just resets to 0); `savanna/registry/SavannaBlocks.java:63-66`.
- **The fix proposed:** one place, no new number: `lookAfter` already walks every heap once a second; let it count the
  layers it finds and, when they are fewer than `edible + rotten` less what the ledger still owes, take the newest
  pieces out of the count (`forget`). A test: stock 6, break the heap, the colony is hungry within a second.

## N-6 (B, likely): one block a player puts anywhere in the chimney hides every room of the nest

- **What is wrong:** a room counts only while every cell of its way in is open (`Nest.getRoomCells`,
  `framework/colony/Nest.java:798-806`, `isDugOut` 775-781: air, a heap or a ramp). A Matabele nest's way in is the
  whole old chimney through the hump (`MatabeleNest.fromMound`, `MatabeleNest.java:185-218`), shared by all its rooms.
  The workers dig out soil there (`readyDigTarget`, `Nest.java:341-355`, `firstDiggable` takes soil only), but a torch,
  a cobblestone, planks or a slab stay for good.
- **What the player would see:** put a torch down a dead mound's chimney to look in: the colony loses all its rooms. The
  queen is "not home", so nobody feeds her and she lays nothing (`QueenCare.isQueenHome` 100-105, `needsFeeding`); the
  prey pile can no longer be found (`PreyPile.rooms`), so the real ants starve beside a full pile; eggs have no spot;
  the ants that keep to the rooms have none to go to.
- **Unchecked link:** whether `getHomeChamber` (`Nest.java:387`, behind `getFinishedRoomCenter` and so `isQueenHome`)
  judges "finished" by the same open-way rule (likely, not read line by line); what the ants' idle and "go inside" goals
  do with no room cells (round 3's ground); whether `isShaftOpen` with depth 0 also looks at the entrance cell.
- **The fix proposed:** either the workers clear any non-soil block a player put in their way in (as they bite a plant
  out of the entrance), or a room is reached while a walk to it exists, not only while every planned cell is air. The
  first is one rule in `readyDigTarget`; the second is a bigger change of one judge.

## N-7 (B, test gaps): what the player sees of the Matabele food and brood has almost no test

- No test raises young from the pile: nurses feeding the queen termites from the heap, an egg, a larva fed termites,
  a new worker (`MatabeleFoodTests.aFedMatabeleQueenLaysHerEggWhileAPlayerIsNear` feeds her by hand, line 262-264). If
  `FeedQueenGoal` or `FeedLarvaGoal` stopped working for the Matabele every test would still pass.
- No test of the pile's count against its heaps (N-5), of a carrier's bundle through a fold (N-1), of a colony that
  starved down to its queen (N-2; `aMatabeleColonyWithAnEmptyPreyPileStarves` stops once the workers are gone).
- No test that the same colony eats, lays and grows alike near and away (the away tests are all raids).

## N-8 (C, to measure): a homeless queen floods every near dead mound every 10 s, the taken ones too; the dead mound book only grows

- `MatabeleNest.nearestMound` (`MatabeleNest.java:108-123`) walks every dead mound in the world, then for each one
  within 32 blocks floods up to 3,000 cells (`fromMound`, 157-179) before asking whether a colony lives there (118). A
  queen with taken mounds round her repeats it every `LOOK_AGAIN` 200 ticks. Ask "taken?" by the mound's middle first
  (the entrance has the same x and z). Only spawn-egg queens do this.
- `DeadMounds` keeps every dead mound for good (`DeadMounds.java:37-40`, `mounds` never pruned), each with its columns,
  shaft and works; it grows with exploration. Cheap per mound once settled (`isChanging`), but the save grows. Size to
  measure.

## N-9 (C): small things in the pile and the nest

- `PreyPile.countedIn` (`:522-525`) stamps the piece with `lastTime`, which is not saved: folded in before the pile's
  first tick after a load it is laid at time 0 and rots at once.
- `putBack`/`putDownWhereItStands` (`PreyPile.java:464-504`, used by `FeedQueenGoal` 214, 228, 238 and `FeedLarvaGoal`)
  can lay a heap outside the rooms or on a cell an egg lies on (only `addPieceAt` asks about brood). Outside the rooms
  `piles()` never sees it: a twitching heap there for good, its termite counted and eaten off another heap.
- A colony that ends leaves its heaps as they were: `lookAfter` stops, so a fresh heap twitches for ever.
- `heapSpot` (`:317-345`) wants ground within 3 of the saved entrance; with the hump worn 2 to 5 below that entrance
  (BUGS 358) the ring round it may have no spot, every load is "scattered and gone" and the midden never shows. A new
  side of BUGS 358.
- Real ants cannot eat the meals left in a started termite once the pile is empty (`findFood` returns null), the
  numbers can (`WorkerFood.eatForNumbers` 123-125): near and away differ by up to 19 meals.

## N-10 (C): comments and numbers that disagree with the code

- `megaponera_analis.json` `_note` (line 2): "max_workers 2500", "a layer about 10 termites", "workers_per_piece 10
  meals", "a dead termite mound first, then under a fallen log, else the ground (a shaft and two rooms)": the file says
  100, 1, 20, and only dead mounds exist (`MatabeleNest`).
- `PreyPile.java:34-37` and `PreyPileBlock.java:12` "about 10 termites, one big ant's load"; `MatabeleColonies.dayOfFood`
  (`:108-111`) "one termite an ant, about 10 termites a layer": at today's numbers it gives 2.2 days' food for 100
  workers, not a day (the test only checks "no more than `dayOfFood`").
- `SavannaNumbers.CarryNumbers.DEFAULT` (`species/SavannaNumbers.java:744-745`) says "the same as Megaponera's" but has
  `termites_per_layer` 10 against the file's 1, and the codec and `PreyPile.termitesPerLayer` (`:549-552`) fall back to
  10: a species file missing the number quietly gives a tenth of the food.
- `queen.rich_meal`, `burst_eggs`, `burst_interval_ticks` are never used by the Matabele: prey is never rich
  (`takeFoodAt` and `takeCounted` give plain pieces). `food.pile` is a fungus comb stand-in.
- `MatabeleNest` class doc: a garden room with comb "is taken up once the comb is gone"; in the code it is taken at once
  as a nursery, food or refuse room (`fromMound` 205-210), its comb cells just not used until they rot.

## Solid in this slice (do not "fix")

- **Nest sites never read unloaded land:** `MatabeleNest.fromMound` checks the whole reach is loaded first (`isLoadedRound`,
  93-103, 132); new land waits for 24 blocks round (`NewLandMounds.LOADED_ROUND`), more than the 11 it needs.
- **The pile's away side touches no block:** `tickAway` only rots and counts; `bringInCounted` never reads a block; the
  heaps catch up out of sight through the ledger, both ways, and give up cleanly when there is nowhere to lay or nothing
  to take (`NestLedger.applyNurseFood`).
- **Near and away eat by one rule:** real workers and numbers share `WorkerFood`'s started piece; the queen is fed by
  the same `feedQueen` near (goal) and away (`NestLife.nurse`); rot is on game time both ways.
- **Saving the pile:** every number that matters is saved (`edible` with each piece's time, rotten, scraps, owed heap
  layers, the loose termites); `broughtIn` is only for the list and tests, as its comment says.
- **The raid's own catch through a fold** is kept (`Raid.toNumbers` reads the jaws before folding).
- **A carrier that dies** drops its termites as stung termites where it fell (`MatabeleLoads.dropTermites`).
- **The new-land dice** are one seed asked twice the same way (`wouldSettle` and `inNewLand`), so world making and the
  put agree; freshly dead mounds that will hold ants are aged past the capped chimney (`TermiteMoundStructure` 274-280).
- **The remains heap** weathers by random ticks (about a layer in 2 days) against about 1 load a day: it stays small.
- **No goal of this slice scans blocks or mobs every tick:** `BringPreyGoal` and `CarryRemainsGoal` search only when
  carrying or when a load is claimed, with retry waits; the pile's once-a-second look reads only the room cells.

Read in full: `MatabeleNest`, `MatabeleColonies`, `PreyPile`, `PreyPileBlock`, `TermiteRemainsBlock`, `DeadMounds`,
`DeadWorks`, `SavannaParts`, `MatabeleQueenEntity`, `MatabeleLoads`, `BringPreyGoal`, `CarryRemainsGoal`, the species
file, `MatabeleNestTests`, `MatabeleFoodTests`, `MatabeleColonyTests`; framework `QueenCare`, `Brood`, `EatGoal`,
`FeedQueenGoal`, `FeedLarvaGoal`, `QueenToRoomGoal`, `CarryEggGoal` (from line 50), `WorkerFood`, `Meals` (to line 230).
Read in part: `NewLandMounds` (110-140, 230-410), `TermiteMoundStructure` (60-300), `SavannaNumbers` (the carry
record), `AwayLife` (240-500), `NestLife` (240-400), `NestLedger` (95-245), `Nest` (the room-cell, dig-target and
layout methods), `Raid` and `Raids` (only where they meet the pile and the jaws), `MatabeleAwayTests` (228-387),
`MatabeleNewLandTests` (test names only). Not read: `FoodStore.java` (the Matabele's store is an unused stand-in;
nothing in this slice calls it), the rest of `Nest.java`, `MatabeleNewLandTests` bodies.
