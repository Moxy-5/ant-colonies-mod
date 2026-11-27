# Round 5a, reader F: the fungus garden and the royal pair

Read at commit `4dcd1fe6` in `C:\Ant Mod` (branch `claude/motion-glue`). No code changed, nothing run or measured.
Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`.

## F-1 (A, proven for the sums; likely for how often a queen starves): while away, forage that comes in after the numbers have eaten counts as old comb, so far colonies stop growing nodules

- **What is wrong:** while a termite colony is away, the brain keeps the comb's ages as "what it saw when it left" plus
  what is owed to the blocks (`NestLedger`). Layers added on top (new forage, fresh) and layers taken off the bottom
  (eaten or crumbled, old) are kept in **one** signed number, `ledger.store`. The garden then reads that one number two
  ways: only the part above 0 counts as fresh, only the part below 0 takes away old. So once the numbers have eaten more
  than has come in, every new piece of forage only makes the negative number smaller: it shows up as *old* comb (food
  for the old workers), never as fresh comb that ripens and grows a nodule. And when more has come in than was eaten,
  the eaten layers are taken off the fresh count instead of the old one. The total always adds up; the split is wrong.
- **What the player would see:** a far colony feeds its workers but grows far fewer fungus nodules than the same
  colony watched (the nodules grown away are only "forage minus what was eaten", instead of one per piece of forage).
  Nodules are what the queen, the king, the nymphs and the soldiers eat. When the colony's foraging only keeps up with
  its eating (a food-limited colony, the normal case for a full one), the nodule stream away stops: nymphs starve
  (`larva_starve_ticks` 2 days), no new workers come, soldiers go hungry, and after 5 days unfed the queen and the king
  die (they are fed first, so this needs no nodule for 5 days). The player comes back to a mound that dwindled or died.
- **Where:** `savanna/colony/FungusGarden.java:323-336` (`freshKnown` uses `Math.max(0, ledger.store())`, `oldKnown`
  uses `Math.min(0, ledger.store())`); `savanna/colony/GardenAway.java:45-67` (ripening only from `freshKnown`,
  nodules only from `ripeKnown`), `:76-82` (crumbling `addStore(-1)`); `framework/colony/NestLedger.java:34, 47-49`
  (one `store` number); the writers: `framework/colony/FoodStore.java:1021` (`takeCounted`, eating: -1),
  `framework/colony/FieldLife.java:288, 563` and `AwayLife.java:455` (forage in: +1).
- **The proof (the sums):** a garden seen on leaving as 0 fresh, 0 ripe, 10 old. Away, the old workers eat the 10 old
  layers: `store` -10, `oldKnown` = 10 + 0 - 10 = 0. Then the foragers bring 6 pieces: `store` -4. Now
  `freshKnown` = 0 + max(0, -4) - 0 = **0** and `oldKnown` = 10 + 0 + (-4) = **6**. The 6 new pieces never ripen
  (`GardenAway:45` adds `freshKnown`, 0) and grow no nodule (`ripeKnown` stays 0). In the game they would be 6 fresh
  layers on top, each ripening in about half a day and growing a nodule. The other way: `store` +10 after 20 in and 10
  eaten: `freshKnown` = seen + 10 (should be seen + 20, the eaten 10 were old), `oldKnown` never goes down.
- **A second door to the same end:** `GardenAway.grow` returns at once when `garden.seenCombs() == 0` (`:39`). A colony
  that goes away before its first comb block is laid (a pair the player put down and walked away from; a colony that
  had eaten its comb to nothing) never ripens a layer or grows a nodule while away, however much forage it brings in:
  `seenCombs` is only written by `grow` (here) and `look` (once, on leaving, `AwayLife.java:405-413`).
- **Unchecked link:** how often far colonies forage less than they eat (not measured; a soak's away report would show
  `NODULES_GROWN` against `COMB_RIPENED` and the pieces brought in).
- **Test gap:** `TermiteAwayTests.aTermiteColonyAwayAgesItsCombFeedsItsQueenAndEatsAsNumbers` (`:856`) brings no
  forage in after the eating, and its check "the known ages add up to the food count" (`:958`, `:990`) holds even with
  the wrong split.
- **The fix proposed:** remove the double reading of one number instead of adding a rule. Keep the comb's away ages as
  their own counts in the garden part (fresh, ripe, old, nodules, combs), moved by the same events: a piece in adds a
  fresh layer, a meal or a crumble takes an old one, ripening and nodules move fresh to ripe to old. The ledger still
  owes the blocks only the net store (that is all the blocks need on return). `seenCombs == 0` then needs no guard of
  its own: a piece laid away is a comb block owed, so count it (at most one comb per 4 layers, up to the garden's
  cells). A test: away, eat all the old comb, then bring in 6 pieces: 6 fresh layers known, and nodules grow.

## F-2 (A, proven): a termite king outlives his colony for ever

- **What is wrong:** two places judge "are the royals hungry", and they disagree. The queen's clock
  (`QueenCare.hungerRunsOut`) runs when the royals get hungry **or** the queen is "spent" (the R6-2 fix: a colony whose
  workers have all died). The king's clock (`KingCare.updateClock`) asks only `QueenCare.royalsGetHungry`, which is
  false while the colony counts as founding, and a colony with no workers left counts as founding again. So in a colony
  that has run down to its royal pair, the queen starves in 5 days and the king's clock restarts every second. When the
  queen dies, the colony is empty and ends (`ColonyManager.queenDied` → `colonyEnded`). Nothing ends the king: his tick
  finds no colony, sets his colony to none and looks every 2 seconds for a kingless queen within 32 blocks; he never
  despawns (`AbstractAntEntity.removeWhenFarAway` is false) and, with no colony, never starves. The same happens to a
  king whose colony ends any other way (queenless and dwindled to nobody: `Colony.tick:746`).
- **What the player would see:** a live king wandering in the royal chamber of every dead mound, year after year; a
  mound the Xylaria has taken over with its king still walking about. (Real Macrotermes kings die with their colony;
  they are fed only by the workers.) If a kingless queen is ever within 32 blocks (a new mound, a spawn-egg queen), the
  old king walks over and joins her colony.
- **Where:** `savanna/colony/KingCare.java:101-105` (only `royalsGetHungry`), `:107-111`;
  `framework/colony/QueenCare.java:454-475` (`hungerRunsOut` with `isSpent`; `royalsGetHungry`), `:273-280`
  (`isSpent`: a termite colony past its founding batch with no workers and no young is spent);
  `framework/colony/Colony.java:812-814` (`isFounding`), `:725-734` (`queenDied`, `isEmpty` counts no co-founder);
  `savanna/entity/TermiteKingEntity.java:133-169` (no colony: no starving, the search for a queen).
- **The proof:** workers all dead, no young: `getMemberCount() - coFounderCount() == 0` so `isFounding` true, so
  `royalsGetHungry` false; `KingCare.updateClock` sets `lastFed = now` every call; `hungerRunsOut` is never true. The
  queen: `isSpent` true (`eggsLaid > founding.eggs() && !secondBatch`, brood 0), so `satisfied` false, she starves
  after `starve_ticks` 120000. `die` → `queenDied` → `isEmpty` true → `colonyEnded`; the king's next tick
  (`TermiteKingEntity:150-153`) clears his colony id.
- **Test gap:** no test ends a colony with its king alive.
- **The fix proposed:** one judge, no new number: `QueenCare` answers one question "are the royals hungry now" (today's
  `satisfied` rule without the queen's own meal and egg), and `KingCare` asks it. And a king whose colony is gone dies:
  the `COLONY_ENDED` news (the garden and the mound already hear it) tells `KingCare`, and his own tick, finding his
  colony gone after he once had one, starves him (keeping the join for a king put down with a spawn egg, who has never
  had a colony).
- **Owner's call:** today the king never dies once his colony is gone. My pick: he dies with it (at once, as real
  royals cannot feed themselves), and a king who has had a colony never joins another.

## F-3 (B, proven): the colony's food count and the comb in the world are two truths that drift apart

- **What is wrong:** the food store keeps a number of layers (`FoodStore.food`), changed only by the colony's own
  doings (a piece laid, a layer eaten or crumbled). The comb blocks are the other truth. Nothing ever brings the two
  back together. Ways they drift in play: a player breaks comb (the count keeps its layers;
  `ModEvents.onBreak` only tells the alarm); a comb loses its floor and Minecraft removes it
  (`SeedPileBlock.updateShape`, BUGS 71); a player places a fungus comb item (an uncounted ripe layer that grows
  nodules, and when workers eat it the count drops below the real layers: `takeSeedAt` lowers it anyway).
- **What the player would see:** after digging into a garden, the colony list keeps showing comb that is gone; the
  nest unlocks levels from food it no longer has (`Nest.java:1246`, `levelsFor(workers, food)`); away, the numbers eat
  only while both say yes (`WorkerFood.eatForNumbers:127`: the garden's seen ages *and* `food > 0`), so a count below
  the real comb starves far workers beside old comb, and one above it is cut short only by the seen ages.
- **Where:** `framework/colony/FoodStore.java:79-80, 123-126, 1014-1053`; `framework/colony/WorkerFood.java:127, 143-145`;
  `savanna/colony/FungusGarden.java:144-161` (the world's comb); `framework/ModEvents.java:123-129`.
- **The proof:** every write to `food` is at `FoodStore:124, 1017, 1048, 1094` and `countIn`; none reads the blocks.
  The away test asserts the two agree (`TermiteAwayTests:909`) only on a scene where nobody touches the comb.
- **The fix proposed:** for a store whose heaps are blocks (the comb), let the blocks be the truth while the nest is
  loaded: once a second the garden already counts every comb layer (`FungusGarden.grow`); set the count to that plus
  what the ledger still owes (the same sum the away test checks). Away, the count stays as it is.

## F-4 (B, proven path; lag to measure): the comb's catch-up on return can do three times the ledger's 40 block changes a second, each try walking every comb with a sight check

- **What is wrong:** R1-17 capped the blocks changed on return at 40 a second of all kinds (`NestLedger.PER_SECOND`).
  The garden's share (`FungusGarden.age`) is handed what is left of the budget as `most`, then does up to `most`
  ripenings **and** up to `most` ageings, and its changes are never taken off the budget; the store's layers after it
  get the same budget again. Each try (`pickToAge`) walks every comb block, reads it, and for each match runs a sight
  check (`Population.outOfSight`, a ray from each player's eyes).
- **What the player would see:** a small stutter when coming back to a big mound after a long time away (many layers
  ripened and aged owed), the moment the player is near (the sight rays run only for players within sight range).
  Not measured.
- **Where:** `savanna/colony/FungusGarden.java:100-141`; `framework/colony/NestLedger.java:100-127` (`Math.max(1,
  budget)`, the budget not lowered after).
- **The proof:** up to 40 + 40 garden changes + 40 store layers in one second (120); 54 comb cells (6 garden rooms of
  3 by 3) times 80 tries is up to about 4,300 sight rays in one tick in the worst case.
- **A new side of R1-17.**
- **The fix proposed:** pick the out-of-sight combs once per call (one list, rays once each) and take the garden's
  changes off the same budget.

## F-5 (B, proven): the queen does not swell while her colony is away, so a colony that grows away keeps a small, slow-laying queen

- **What is wrong:** the queen's swelling lives in her body's tick (`TermiteQueenEntity.tick:176-183`: her size grows
  toward members / `full_size_at_members`). Away, the brain lays her eggs at the interval for the size it saw when the
  colony left (`NestLife.queenSize`, written only by `watch`, `NestLife.java:72`), and her body does not tick. So the
  laying rate away never changes, however big the colony grows, and near and away disagree.
- **What the player would see:** a colony founded and grown far from the player keeps the founding queen's rate of
  10 eggs a day (`egg_interval_ticks` 2400) instead of reaching 80 (`egg_interval_ticks_full_size` 300); on return
  she swells again by 0.25 a day. Far colonies grow slower than watched ones. (With the cap of 200 workers and the food
  limit, the gap is days, not a stuck colony.)
- **Where:** `savanna/entity/TermiteQueenEntity.java:176-183`; `framework/colony/NestLife.java:41, 65-72, 199`;
  `framework/colony/QueenCare.java:408-422`; `framework/species/SpeciesProfile.java:939-942`.
- **The fix proposed:** move the swelling rule out of the body into the colony (a small rule in the species'
  queen-body part, ticked here and away, the body only drawing the number), so one rule grows her in both places.

## F-6 (B, likely): the mushrooms come up after rain judged for the whole world, not for the mound, and the savanna may never rain

- **What is wrong:** `Mushrooms.tick` reads `level.isRaining()`, the world's weather. The colony's own judge of rain
  on the nest (`Colony.rainsNow` / `rainsOnNest`) also asks whether the biome has rain at all. Two judges of the same
  thing. In a biome with no rain (the mod's own `savanna_floodplain` has `"has_precipitation": false`; vanilla savanna
  too, from memory) the world can be "raining" with no drop falling, and the mushrooms come up "after the rain" in a
  dry, sunny savanna.
- **What the player would see:** a ring of mushrooms appears round a mound on a dry day, after a rain they never saw.
- **Where:** `savanna/colony/Mushrooms.java:61`; `framework/colony/Colony.java:540-548`;
  `savanna/src/main/resources/data/antcolonies/worldgen/biome/savanna_floodplain.json:89`.
- **Unchecked link:** vanilla 1.21.1 `savanna.json`'s `has_precipitation` (I believe false; not read). Termites
  foraging "in the rain" use the world's weather too (`Colony.isForagingTime:439`; round 5b's ground).
- **The fix proposed:** one judge (`colony.rainsNow`). That alone would end mushrooms in a savanna with no rain, so it
  goes with the season socket (F-10).
- **Owner's call:** today mushrooms come after any rain anywhere in the world. Real Termitomyces fruit when the rains
  start (the start of the wet season). My pick: they come up after a rain that falls on the mound, and in a biome with
  no rain when the shared season turns to the rains (`Seasons.now`, which answers "rains" today).

## F-7 (B, real biology; owner's call): the foragers build the comb themselves; real comb is built by young workers from their droppings

- **What is wrong:** a forager carries its piece to the garden and lays it as a comb layer (`StoreFoodGoal:86` →
  `FoodStore.depositSeed`). Real Macrotermes (and the design, `docs/DESIGN.md:51`, "the young workers make comb from
  it"): old workers bring the plant matter in, young workers eat it, pass it through their gut quickly with Termitomyces
  spores, and lay it as fresh comb (the "primary faeces"); they also eat the nodules, which is how the spores travel.
- **What the player would see:** the old foragers walking down into the garden rooms to lay comb, the young nurses
  never building it.
- **Where:** `framework/entity/goal/StoreFoodGoal.java:86`; `framework/colony/FoodStore.java:902-911`.
- **The fix proposed (only if wanted):** foragers drop the forage in the garden room's doorway (a pile, as the
  harvester's seed heap was), and a nurse-age worker eats a piece and lays it as a fresh layer (the eating already
  exists in `WorkerFood`). One hand-off, no new number. Away the numbers stay as they are.
- **Owner's call:** today the forager lays comb. My pick: the young workers lay it, as the design says; it is a
  visible real detail (and the place for the spores).

## F-8 (B, proven): what has no test

- Forage arriving while away after the numbers have eaten (F-1); a colony away with no comb yet (F-1).
- A colony ending with its king alive (F-2).
- Comb broken by a player, or lost with its floor, against the food count (F-3).
- The queen's size and laying rate after a long time away (F-5).
- The mushrooms test (`TermitomycesTests:131`) sets the colony's own rain (`setRainForTests`), so it would still pass
  if the world's rain never reached the mound (F-6).

## F-9 (C, to measure): every nurse's "is there food" rebuilds the garden and reads every comb, and looks for the king by an entity scan

- **What is wrong:** `available`, `findFood` and `pickReturnSpot` each call `getCombs` → `Nest.getGardenCells`, which
  for every garden reads the shaft, the way in and the room cells and joins them (a loop inside a loop), then reads
  every comb. `FeedLarvaGoal.canUse` and `FeedQueenGoal.canUse` ask `available` once a second per nurse, and
  `claimHungry` adds `colony.getKing(level)`, a box scan for entities round the queen (33 blocks a side), per nurse.
- **Where:** `savanna/colony/FungusGarden.java:144-152, 401-441`; `framework/colony/Nest.java:465-475, 804-828`;
  `framework/entity/goal/FeedLarvaGoal.java:87, 95, 125`; `framework/colony/Colony.java:615-626`.
- **The fix proposed:** the garden keeps the comb cells it found this second (`grow` walks them anyway) and the
  nurses read that list; the king's entity id is remembered by `KingCare` and looked up directly.

## F-10 (C): the mushrooms have no socket for the shared season

- **What is wrong:** the rule "nothing reads the season by itself" is kept (the mushrooms read the weather, not the
  date), but there is no socket for the season source that already exists (`framework/season/Seasons.now`): every
  Minecraft rain now brings a flush, several a week; real Termitomyces fruit in flushes at the start of the rains.
- **Where:** `savanna/colony/Mushrooms.java:57-70`.
- **The fix proposed:** ask `Seasons.now(level, entrance)` beside the rain (it answers "rains" until seasons come, so
  nothing changes today).

## F-11 (C, proven): comments that say the queen ticks the garden

- `savanna/colony/FungusGarden.java:164` ("called once a second (by the queen)"), `Mushrooms.java:56` (the same) and
  `TermiteQueenEntity.java:43` ("She also keeps the colony's fungus garden growing"): the garden and the mushrooms are
  colony parts ticked by the colony (`SavannaParts.java:151, 174`), with or without a queen. A stray comment
  `FungusGarden.java:62` ("The colony whose garden this is.") sits over `describe`.
- `savanna/colony/KingCare.java:40` says old saves kept the king's clock in his body and are read through
  `setLastFed`; nothing reads an old body tag (`TermiteKingEntity` has no `readAdditionalSaveData`), so an old king
  simply starts a new day. Harmless; the comment is wrong.

## F-12 (C, proven): small things

- `FungusGarden.describeMushrooms:83` calls `.get()` on the garden section; a species listing `fungus_garden` and
  `mushrooms` with no `garden` numbers would throw in `/antcolonies list` (`Mushrooms.wanted` returns 0 there).
- `GardenAway`'s fractions (`noduleGrowth`, `ripening`, `ageing`, `crumbling`) are not saved: under one of each lost per
  reload. Near, a comb under an egg or nymph does not crumble (`FungusGarden:219`); away every old layer can. Both tiny.
- `DeadCombBlock` has no floor rule: dug out from under, it hangs in the air (the living comb falls,
  `SeedPileBlock.canSurvive`).
- A comb of 4 layers drops one fungus comb item (`loot_table/blocks/fungus_comb.json`); placed, it is one ripe layer
  that grows nodules uncounted (see F-3).

## The food sums (a reading with `macrotermes.json`'s numbers, not measured)

- **What one piece of forage gives:** it is laid as one fresh layer; it ripens in about half a day (`ripen_ticks`
  12000); a nodule grows from a ripe layer within about a minute when its comb has room (`nodule_ticks` 1200, at most
  3 nodules a comb), turning that layer old; the old layer is eaten (4 worker meals, `workers_per_piece` 4) or crumbles
  after about 3 days (`crumble_ticks` 72000). So **one piece = one nodule + four worker meals.** A ripe layer whose comb
  is full of uneaten nodules goes old by itself in about 2 days (`old_ticks` 48000) and gives no nodule.
- **What a full colony eats a day** (200 workers, 20 soldiers at `per_worker` 0.1, the king, the queen):
  workers 200 meals = 50 pieces (the young share as nodules, the rest as old comb, either takes the other);
  soldiers 1 nodule each a day = 20 nodules (a soldier eats four times a worker's share); the king 1 nodule.
- **What a new member costs:** an egg 2 nodules (`food_per_egg`), a nymph 5 (`larva_meal`), so a worker 7 nodules; a
  minor soldier 2 + 6, a major 2 + 10. Workers live 60 days (`lifespan_ticks` 1440000): 200 workers need 3.3 new ones
  a day, about 23 nodules, the soldiers a few more.
- **Together:** about 45 to 50 nodules and 45 to 50 old layers a day: **about 50 pieces of forage a day** for a full
  colony, and the two streams one piece makes (a nodule and an old layer) match the two needs almost exactly. That fits
  well. 50 pieces is about 12 short dry grass tufts a day (4 pieces each, regrowing in 3 days): about 40 tufts in reach,
  or a fallen log every few days (round 5b checks the foraging side).
- **The garden's room:** at most 6 garden rooms of 3 by 3 (`rooms` 6, reached at 20 workers): 54 comb cells, 216
  layers. A full colony's flow (50 layers a day, each living 1 to 3 days) fits.
- **The queen:** she lays 10 eggs a day at size 0, 80 at full size (reached at 60 members, at most 0.25 a day). At full
  size she could use 560 nodules a day; the nursery's 10 young (`max_young`) and the food stop her long before. After
  the first days **the colony grows as fast as its forage**, not as its queen: her swelling is for the look and the
  first days. Early on (5 workers bringing perhaps 5 to 10 pieces a day) a colony is very weak, as the design wants.
- **Can it get stuck or starve?** Near: no stuck state found; with no fresh comb the young eat old comb and the royals
  wait for forage (5 days' grace). Away: yes, see F-1. **Food from nothing:** only through F-3 (a player's comb item).
- **Against the design's "scaling very hard late, 1,000+ insects":** the cap is 200 workers (`max_workers`, the user's
  2026-11-11 call, "honest sizes"), reached in perhaps 2 to 3 weeks of good foraging; after that nothing scales. Not a
  bug; noted so the balance work knows the late game is the cap.
- **Real numbers I am not sure of:** real Macrotermes queens lay thousands of eggs a day (figures of about 20,000 a day
  for *M. bellicosus* are often quoted; I have not checked the source), and comb turns over in weeks, not days; the
  file's numbers are scaled to a colony of 200 and a 20-minute day, which is the right call, but no number in `garden`
  or `laying` is from a paper.

## What is solid

- The comb's conveyor near: fresh on top, ripen, a nodule from a ripe layer turning it old, old eaten or crumbling from
  the bottom; each layer moves at most one step a second (BUGS 27); combs gone mid-second are skipped (BUGS 71); a comb
  eaten to nothing takes its nodules with it (BUGS 26, `HEAP_GONE`). `FungusCombBlock`'s age maths (`old`, `fresh`,
  `ripe`, `with`, `withoutBottomLayer`) hold for every state.
- The near and away nodule and ageing rates use the same numbers and the same game-time clocks; the brain's dice are
  the colony's own (`colony.random()`) in the garden and the mushrooms.
- The away nurses feed in the right order (queen, then king, then the young), and the king's hunger clock is kept by
  the brain, so it runs on in unloaded land and he dies on load if he starved away (tested, `TermiteAwayTests:1063`).
- The R6-2 fault (a lone queen living for ever) does not hit the termite queen: her colony past its founding batch
  counts as spent once its workers and young are gone (`QueenCare.isSpent`); only the king misses it (F-2).
- The mushrooms never load land (`LandReady` first), never sprout in the mound's plan, the nest, a hole or the
  entrance, and cannot run away: at most 8 a flush, each wilts and goes by itself. The giant mushroom drops its loot
  once, from its foot, whichever part is broken, and nothing in creative.
- The queen's size is saved on her body and with the brain; her hitbox follows it; a queen too big to walk drops her
  route (no stuck walker).
- No value is sent to players every tick: the queen's size changes once a second at most while she grows.

Read in full: `savanna/colony/FungusGarden.java`, `GardenAway.java`, `Mushrooms.java`, `KingCare.java`,
`savanna/entity/TermiteQueenEntity.java`, `TermiteKingEntity.java`, `savanna/block/FungusCombBlock.java`,
`DeadCombBlock.java`, `TermitomycesBlock.java`, `GiantTermitomycesBlock.java`, `macrotermes.json`,
`framework/colony/QueenCare.java`, `NestLedger.java`, the three loot tables. Read in part: `SavannaParts.java:30-175`,
`SavannaNumbers.java:135-227`, `framework/colony/NestLife.java:30-400`, `FoodStore.java:75-134, 895-1094`,
`AwayLife.java:380-424, 520-580`, `Colony.java:425-549, 600-830`, `Nest.java:451-503, 804-828`, `FeedLarvaGoal.java:55-134`,
`WorkerFood.java:115-145`, `ColonyManager.java:58-170`, `TermiteAwayTests.java:836-995`. Not read: the other garden and
royal game tests beyond their names; `Meals.java` and `Brood.java` beyond the lines named.
