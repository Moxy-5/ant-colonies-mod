# Big savannas, big rivers (the user, 2026-11-16; nothing built)

The user: "I want the river to have more life to it but it's gonna start getting cramped; it's also not exactly a
realistic river; we will also have to make the savanna biome bigger, which I'm okay with because I'm planning a lot of
stuff to add." The user's pick for the rivers: **widen toward the sea** (narrow inland, wide near the coast), over
"every river wider".

This plan takes in `docs/plans/no-small-savannas.md` (its steps are step 2 here).

## Why
- **Cramped:** a savanna river's water is a median 24 blocks across (a quarter 16 or less; `riverWidthIsMeasured`,
  2026-11-14), shared by a 6-block crocodile, 2-block catfish, tilapia schools, crabs and snails. Spawn caps count round
  a player (`river_fish` 60, `river_hunter` 6), not per block of water, so more water spreads the same animals out with
  no new rule.
- **Not a real river:** Minecraft draws every river from one pattern ("peaks and valleys" near its valley end), a web of
  rivers of about one width. Real savanna rivers grow downstream: streams inland, a big river near the sea.
- **Savannas too small for what is planned:** typical 1,773 blocks across, the biggest 3,596 (2026-11-06 maps), and
  about 1 zone in 4 is still Minecraft's own with its small savanna patches.

## How the river's width works today (read in the code, 2026-11-16)
- The river biome is weirdness within 0.05 of 0 (`SavannaRegion`); the floodplain biome within 0.15
  (`FLOODPLAIN_WEIRDNESS`).
- The land: `tools/flat_savannas/flat_offset.py` keeps Minecraft's own river bed where "peaks and valleys" (pv, which is
  folded weirdness: pv -1 at the river's line) is under -0.86 (`CHANNEL`, about weirdness 0.047, the river biome's
  edge), pulls the land to the floodplain height from -0.80 to -0.55, and fades to the plain by 0.
- So the water's width is the stretch where pv is under about -0.86. Moving that edge out widens the water: pv -0.70
  (weirdness about 0.10) roughly doubles it, pv -0.55 (about 0.15) roughly triples it (weirdness grows about evenly
  across a river near its line; real widths will vary with the noise; step 1 measures).
- **"How far inland" is a number both sides can read:** continentalness, which Minecraft already uses for its coast,
  near-inland, mid-inland and far-inland bands. The land's splines can nest on it (Minecraft's own do), and the biome
  table's points can be split by it. **Not tried yet:** step 1 proves it on a test map before anything else is built.

## Steps (my picks marked; a full run every 3 steps and before the merge)
**Step 1, measure and prove.** In `SavannaWorldTests` (`worldgen`):
- how savanna land splits across Minecraft's continentalness bands (if savannas are mostly far inland, wide rivers would
  be rare and the bands move);
- river width per band (today's numbers, the guard for later);
- a try-out of the channel edge set by continentalness on a test map, the land and the river biome lined up.
- Nothing changes in-game.

**Step 1 results (2026-11-16, branch `claude/big-rivers`; nothing changes in-game):**
- **Savanna land by how far inland** (savanna cells of the biome maps, 3 seeds; `riverWidthIsMeasured` now prints it):
  coast 5 percent, near inland 23, mid inland 38, far inland 34. River beside savanna splits the same way. So wide
  rivers keyed on Minecraft's bands are near inland and the coast, about 28 percent of savanna river length, and the
  mid-width ones about 38 percent: real wide rivers, not a rarity, and no need to move the bands.
- **Today's widths do not follow inland at all:** median 22 to 26 in every band (Minecraft's one river pattern).
- **The try-out works.** `tools/flat_savannas/flat_offset.py ... wide` (a new option; without it the land is
  today's, byte for byte) gives each spot one of three river profiles across the river, blended by continentalness:
  today's far inland (from 0.30), a channel to pv -0.72 with our own deeper bed at mid inland (0.15), a channel to pv
  -0.58 near inland and at the coast's inner edge (-0.11 to -0.02), today's again in the sea (to -0.19, so no sea floor
  is pulled up); the floodplain moved out as far, then the same rise to the plain. Measured on a test build of it:
  | band | today | try-out |
  |---|---|---|
  | far inland | 22 to 26 | 22 (25 percent 13, 90 percent 51) |
  | mid inland | 22 to 26 | 31 (75 percent 52, 90 percent 80) |
  | near inland | 22 to 26 | 54 (75 percent 81, 90 percent 110) |
  | coast | 22 to 26 | 36 (fading back to today's toward the sea) |
  Depth at the river's middle a median 7 (far inland 5). On the try-out the other world tests passed (gentle ground,
  floodplain a block over the water with no drops, the floodplain trees, the banks, the big savannas); only the land
  fingerprint test failed, as it must for a land change. The river **biome** was not widened yet, so the wide water is
  partly in floodplain and savanna biome: step 4 widens the biome band with the land.
- **For step 4:** the widths came out lower than the targets (mid about 40, near 60 to 100): move the wide channel
  edges out a little (mid to about pv -0.65, wide to about -0.50), and check the shallows along the sides (only the
  middle's depth was measured).
- **Found on the way: a memory leak in the land's wiring** (`docs/BUGS.md` 222): every ground height the land worked
  out stayed in memory for good, in play too; fixed (`FlatSavanna.Shared`), and the slow-tick watcher capped.
  With today's land put back: `worldgen` 10 of 10 on 6 servers, the land fingerprint unchanged (the fix moves no
  block), a ground height 1.86 ms in savanna (the same as before).

**Step 2, no small savannas** (`docs/plans/no-small-savannas.md`, its steps 1 to 3): our own region in place of
Minecraft's (the index check), Minecraft's savannas there turned to plains (windswept ones to windswept hills, my
pick), the leftover small patches counted on the biome maps and tuned.

**Step 3, bigger savannas.** Our region's weight and the savanna's climate shape (`WEIGHT`, `SHAPE`) tuned on the
biome maps to a typical savanna of **3,500 to 4,000 blocks across** (my pick, about twice today's); checked that
deserts, jungles and the rest still turn up; checked with Biomes O' Plenty's regions beside ours. TerraBlender's zone
size (`overworld_region_size`) stays untouched: it is one setting for every biome mod. Spawn in the savanna kept.

**Step 4, rivers widen toward the sea.** In the land (`flat_offset.py`, regenerated, never hand-edited) and the biome
table (`SavannaRegion`, the river and floodplain bands split by continentalness), together so land and biome line up:
- far inland: as today (water about 15 to 25);
- mid inland: about twice (water about 40);
- near the coast: about 60 to 100, the big river;
- in the widened part our own bed: a deep channel in the middle (about 8 to 10 deep), shallows along the sides (1 to
  3, where algae grows lush and crabs, snails and tilapia live), the floodplain beside it moved out to match;
- the widths fade between bands over a long stretch, so a river widens gradually, never in a step;
- the river banks, silt bars, pools, algae and old remains (`RiverBankFeature`, `FloodplainPoolFeature`, the algae and
  remains features) checked on the wide rivers;
- all numbers my picks, set from step 1's map.

**Step 5, balance and speed.**
- The mound survey again (`MatabeleNewLandTests` bands: dead mounds seen, Matabele share, a living mound within the
  scouts' reach): wider rivers and floodplains take mound land, bigger savannas give it back.
- Wide rivers stop Matabele scouts and raids crossing (ants do not swim; real rivers split ant ground too): checked
  that no Matabele nest is cut off from every living mound (`tools/raid_balance.py`).
- The river animals on wide water: spread out as hoped, or do the caps need a look (try without first: fewer rules).
- Loading speed: the river land shaping slowed loading once (`docs/plans/savanna-loading-2.md`): ground height and
  spawn search timed before and after (`tools/flight.sh`).
- Full run on 6 servers.

**Step 6, the user's look** in a new world (old worlds get a seam where new land meets old).

## Your calls
- Savanna size target (my pick 3,500 to 4,000 typical). Other biomes will be a longer walk away.
- River widths per band (my picks above; you will see them in-game at step 6).
- No other calls: the rest is code.

## Not in this plan
- The "flats" or delta biome (several channels round islands, reeds, lagoons; `docs/DESIGN.md`, "Big floodplains and
  the yearly flood"): later, it needs these big savannas first.
- Dry sandy streambeds and seasonal rivers: with the seasons.
- Hippos and other big river animals: M9 river life, after the rivers are wide.
