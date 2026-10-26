# Dead wood for the raids (plan, 2026-10-25; nothing built)

## Why (the user's idea, 2026-10-25)
Termites only build their interesting surface works (feeding galleries, mud over branches, plastered logs, mud
sheeting runways) where there is dead wood. Grass they eat from their holes at night with nothing on the surface. A
mound with no dead wood near it is holes and a lawn, so a Matabele raid (M8 step 4) has nothing to break open.

Real life agrees: *Macrotermes* sheeting and runways sit mostly over dead wood, and Matabele scouts look for exactly
these spots, termites feeding by day under mud cover. So the savanna needs a steady supply of dead wood everywhere,
and every mound needs a feeding spot from day one.

Comes **before M8 step 4** (the raids need the targets). Taking our time (the user): each part planned and agreed before
it is built.

## The parts

### Part 1: more trees (new land only)
- Big trees one per 4 chunks (was 6, separation 2 from 3): `worldgen/structure_set/big_savanna_trees.json`.
- Young trees one per 2 chunks (was 3): `young_savanna_trees.json`.
- About twice as many of each. Land already made keeps its trees.
- Watch: new land is slower to make (about 8.7 ms a tree) and more trees to draw. Measure with `/antcolonies perf` and
  the `Perf.NewLand` numbers before and after; lower the numbers if it lags.

### Part 2: trees live faster (die sooner, spread faster)
- Lifespans (`tree_kind/*.json`, `lifespan`): umbrella thorn 150-250 days to 50-80, camel thorn 300-500 to 90-140,
  baobab 1000-2000 to 250-500. **If part 4 (heart rot) is built, these come down less** (see "Your calls").
- Seedlings after rain (`tree/Forest`): `SPROUT_TRIES` 2 to 4, `SPROUT_CHANCE` 0.35 to 0.6.
- Seedling survival (`tree/TreeLife`): `SEEDLING_SURVIVAL` 0.1 to 0.2.
- About 3 to 4 times as many new trees. The `TreeLife` unit checks change their totals with the numbers.

### Part 3: every mound comes with a dead tree (new land)
- Real: mounds are not placed beside dead trees, but a fungus-growing colony does hollow and kill the trees near it
  (already built, M7.5 step 5f), so a dead tree by a mound is a real sight.
- How: the mound structure (`worldgen/TermiteMoundStructure`, `colony/NewLandMounds`) places one dead tree 6 to 12
  blocks from the mound's middle: a standing dead tree (snag) or a fallen log, half and half (my picks), on flat
  enough ground, into the tree book (`Forest`) so it wears on from there.
- Living mounds: the termites already know the wood (their galleries reach it at once, like the dead-wood call).
  Dead mounds get one too (a later Matabele colony's neighbours).
- Why not "only make a mound where a dead tree already stands": the game makes trees and mounds separately while
  building new land, so that rule would leave almost no mounds. Pairing them keeps mounds about every 96 blocks.
- Colonies founded in play (eggs, commands) still settle anywhere.

### Part 4: heart rot (trees rotting from the inside)
Real biology: the core of a trunk (heartwood) is already dead wood. Heart rot fungi (*Ganoderma*, *Phellinus*) get in
through wounds (broken limbs, fire scars, lightning, elephant damage, termite tunnels) and eat the core while the tree
stays green. Shelf-shaped brackets on the bark show it is far gone. A hollow tree can live on for decades, weaker,
shedding limbs, until it snaps or slumps in a storm. Old baobabs hollow out on their own. Termites follow the rot in
and speed it up.

Stages (each a few days; all timings my picks, to set when planned in detail):
1. **Infection:** a chance from each wound: a shed limb, a fire scar, a lightning strike, termite galleries in it, old
   age. Untouched trees may live to their full lifespan.
2. **Hidden rot:** nothing to see; the tree ages faster.
3. **Brackets:** shelf fungi on the bark (a new block, its looks picked in the model viewer first).
4. **Hollow core:** the trunk's inner logs turn to rotten wood (already a block) and then air; only a shell stays.
   Seen by breaking in, or through a hole where a limb broke off. Termites eat into the rotten core.
5. **The end:** more limbs shed, then the trunk snaps or falls in a storm, leaving the hollow log already in the game.

What it gives:
- The real reason trees die sooner: trees with fire, lightning and termites round them die young, untouched ones grow
  old.
- More dead wood all over, and more for the termites to build over (galleries up into hollow trunks, mud along
  rotten limbs): the intricate works the raids need.
- Hollows as homes later (Matabele nest at tree feet and in rotten wood, M8 step 6 moving house; centipedes and
  scorpions in M9.5).

### Part 5: the check
- A test command (my pick: `/antcolonies feedingspots`) that counts the termite feeding spots (galleries, mud casing,
  sheeting) round the nearest mound.
- Count round a few new-land mounds before and after parts 1 to 4. The aim: every living mound has at least one
  feeding spot by day, and most have several.
- Game tests: the `trees` category plus `termitefood,away`; a new check in an existing new-land mound scene that its
  dead tree is there and the termites reach it.

## Order
Parts 1, 2, 3 (small, numbers and one placement), then the user's in-game look, then part 4 (planned in detail first,
with the bracket looks in the model viewer), then part 5's count. Built in a worktree so the game in `C:\Ant Mod` is not
touched while the user plays.

## My picks
- The numbers above (spacing, lifespans, seedling numbers, dead tree 6 to 12 blocks off, snag or log half and half).
- Pairing the dead tree with the mound in the mound structure (one place for it, no extra pass over new land).
- Rotten wood reused for the hollow core; only the brackets are a new block.

## Your calls
- **Shorter lifespans and heart rot together, or heart rot in their place?** My recommendation: both, with the
  lifespans cut less than part 2 says (about half today's), and heart rot doing the rest, so how long a tree lives
  depends on what happens to it.
- **Your time:** parts 1 to 3 need one in-game look (new land, a savanna). Part 4 needs a look at the brackets in the
  model viewer and one in-game look.
