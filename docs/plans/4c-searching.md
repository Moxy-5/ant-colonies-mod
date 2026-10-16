# Brain step 4c: searching, site fidelity and the growing range (planned with the user, 2026-10-15)

The decisions are also in docs/DESIGN.md ("Brain part 4 decisions", "Finding food", "Foraging range grows", "4c details").

## Real behaviour (as explained to the user)
- *Pogonomyrmex barbatus* foragers do not recruit nestmates to seeds (the seeds lie scattered one by one). Each forager
  walks out along a trunk trail, leaves it near its end and **searches alone** in a looping, meandering walk, feeling
  with its antennae. It notices a seed only close by.
- A forager **keeps searching until it finds a seed** and then heads straight home. When seeds are scarce, trips get
  longer, so fewer foragers come home with food in a given time, and the door rule (Gordon) sends fewer out. Coming
  home empty is uncommon (end of the foraging period).
- **Site fidelity** (Hölldobler 1976; Beverly et al. 2009): a forager goes back day after day to the area where it last
  found food. When its spot stops paying, it searches more widely.
- **The range grows out of the ants' own failures:** an ant that keeps failing in one place drifts its search farther
  out, so the colony's reach stretches when nearby food runs out. Nothing decides it centrally. A young colony forages
  close to home; a mature one reaches about 20 m (Gordon & Kulig 1996).
- Macrotermes: exploring workers spread out through the foraging area; ground already searched is not searched again
  soon. (Part 4.)

## The user's decisions (2026-10-15)
1. **Search until found** (recommended): a searcher keeps looking until it finds food. It comes home empty only when the
   foraging time ends (midday rest, night, rain) or after a long cap (about 5 minutes, my pick). Empty trips don't
   count at the door (already so).
2. **The range stretches from the ants** (recommended): a searcher that finds nothing for a while moves its search a few
   blocks farther out along its way; a failed trip makes its next trip start farther out; a find lets it settle back.
   The outer limit is the range for the colony's size plus a stretch (my pick: +8 blocks).
3. Already decided before (DESIGN): no colony-wide knowledge of plants for harvester ants; each forager remembers its own
   spot; a spot found empty twice is dropped; range about 12 to 32 blocks for harvester ants and 12 to 48 for
   Macrotermes; termite scouts remember where they have looked.

## Parts (each tested in-game by the user before the next)
1. **Harvester ants search alone and remember their spot** (real ants and the numbers together, because the old
   "know every plant" shortcut must go for both at once).
2. **The range grows with the colony** (both species): harvester 12 to 32 (+8 stretch), Macrotermes 12 to 48, by worker
   count; the termites' new-hole reach follows the same range.
3. **Termite scouts remember where they looked** (searched ground in 4 x 4 cells, skipped for about 2 game days; scouts
   spread to unsearched ground first, near before far).

## Part 1 in detail
### Noticing (shared, `colony/FoodMap`)
- The harvester ant gets `food.find_radius` 3 (my pick: a worker outside notices a tuft or a seed within 3 blocks).
  New `food.search.alone: true`: what a worker finds is **never told** to the colony; it is that worker's only. Going
  inside clears its found plants (it keeps only its spot, below).
- Seeds lying on the ground (`GatherSeedGoal`, `FoodStore.findSeed`): for a species that searches, only seeds within the
  find radius of the ant itself (bumped into), as a real forager finds them.

### Site fidelity (new `colony/ForagerSpots`, species-neutral, only with `food.search`)
- Each real forager: its spot (where it last cut or picked up food) and how many trips found it empty. Saved.
- A trip to its own spot that finds nothing within `spot_ticks` (60 s, my pick) of searching round it counts as empty;
  `empty_to_drop` (2) empties and the spot is dropped. A find sets the spot to the new place and clears the empties.
- The numbers have a pool of spots (each held by some forager numbers). A real forager going into the numbers puts its
  spot in the pool; one coming out takes a spot from the pool (golden rule: the spot moves with the ant, never doubled
  or lost). A dead forager's spot is lost.

### Searching (`entity/goal/ScoutGoal`, made the shared searching goal; mind layer, walks only through `AntNavigator`)
- A forager with no food it found itself searches (harvester ants: always so; termites unchanged in part 1).
- Where: its own spot if it has one; otherwise out along an open trunk trail (picked by strength) to near its end; with
  no trail yet, an open direction (the patrol's) at a random distance in the inner half of the range. Plus its push
  (below).
- How: out by the trunk trail (`TrunkTrails.setOut`), then a meandering search: short hops to random cells within
  `wander` (4 blocks, my pick) of the search centre, looking round between hops. Stops as soon as it has found food
  (then `ClipGrassGoal` / `GatherSeedGoal` take over), when the foraging time ends, or after `give_up_ticks` (6000, 5
  minutes) and goes home empty.
- **Drift (the stretch from the ants):** every `drift_ticks` (60 s) without a find the centre moves `drift_blocks` (3)
  farther out along the same direction, never past the range plus `stretch` (8). An empty trip leaves the ant a push
  (its next new search starts that much farther out); a find clears it.

### The numbers (`colony/FieldLife`)
- For a species that searches alone, the brain's remembered plants are only its picture of the land (what is there,
  as last loaded), not something the colony knows.
- Forager numbers holding a spot forage the remembered plants within `wander` of their spot, by today's trip rules (out
  of sight only, the door, open directions, room in the store). No plant left near a spot counts as an empty trip; two
  and the spot's holders become searchers.
- Searching numbers: a search takes the walk to the search centre plus the time to find. They search the picture of the
  land round a trail end (or open direction) plus their push: a plant within `wander` there makes a new spot for one
  searcher after the walk plus a few seconds; nothing there and they drift outward as the real ones do. While
  searching they are out (they count against the door's outflow) and bring nothing home.

### Other parts touched
- `species/SpeciesProfile` + `red_harvester_ant.json`: `food.find_radius` 3, `food.search` {alone, wander, spot_ticks,
  empty_to_drop, give_up_ticks, drift_ticks, drift_blocks, stretch}. Absent = today's behaviour (termites unchanged).
- `FoodStore.clippedGrass` / `GatherSeedGoal.take`: tell `ForagerSpots` of the find.
- `Population` (real ant into numbers and back) and `AwayLife` (frozen ants folded in): move the spot.
- `/antcolonies list`: "searching 5, spots 23 (9 held by numbers)"; away report: searches that came home empty.
- Patrol directions still rule: no search centre in a shut direction.

### Tests (add to existing scenes where possible)
- Unit tests: spot bookkeeping (find sets the spot, two empties drop it, spot moves into the pool and back out); drift
  (the centre moves 3 blocks a minute, never past range + stretch).
- `antnest`, `workersCarrySeedsAndClipGrassIntoTheFoodStore` scene: a tuft no ant has come within 3 blocks of is not
  gone for; the same ant goes back to the place it last cut (site fidelity); a spot emptied twice is dropped; with no
  grass near, a searcher drifts outward and finds a tuft placed farther out.
- `away`, `antNumbersForageOnlyOutOfSightAndFromWhatTheyRemember` scene: numbers only forage plants near spots; a
  number spot is made by searching; a real forager's spot goes into the pool and back out.
- Early game check (the user's worry from 4b): a 3-worker colony still brings in its first seeds soon after the day
  opens in plains grass.
- Run `antnest`, `away`, `colony`; the shared `FoodMap` and `ScoutGoal` are termite parts too: run `termitefood`.
