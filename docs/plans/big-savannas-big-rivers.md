# Big savannas, big rivers (the user, 2026-11-16; steps 1, 2, 2a and 2c built)

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

**Step 2 results (2026-11-16, branch `claude/no-small-savannas-20e8a5`; only new land changes):**
- **Minecraft's own zones grow no savannas any more** (`worldgen/MinecraftZones`): Minecraft's table there with savanna
  and savanna plateau as plains, windswept savanna as windswept hills (my picks), at Minecraft's weight (10), so the
  zones keep their shares and sizes. Read in TerraBlender 4.1.0.8: the region at index 0 is never asked for biomes (that
  zone always uses Minecraft's own list), so index 0 keeps the name `minecraft:overworld` but gives no biomes (left out
  of zone picking), and our `antcolonies:minecraft_without_savannas` carries the table. Index check: the log lines
  "Registered region minecraft:overworld to index 0" and `[biome regions] indices right`, and the world test fails if
  any region's index is not its place in the list.
- **Big savannas unchanged:** typical 1,839 across, biggest 3,655, a big one per 3,752 blocks square, 21.6 percent of
  land; the spawn search in a big savanna on every seed. Deserts, jungles, plains, badlands, forests and cold land all
  still turn up (desert 1.8 percent of the maps, jungle 1.4, plains 11.1, badlands 0.8, forest 20.7, cold 5.3).
- **Small savannas left (4 maps of 16,384 blocks square):** 919 under 1,000 across, 14.7 percent of the savanna's area;
  584 of them "lonely" (no big savanna within 128 blocks; a bit closer is a big savanna's ragged edge, not a savanna of
  its own), 10.9 percent of the area. **The swap alone did not cut them:** on the same pretend zones, lonely small
  patches 516 before, 559 after (Minecraft's savannas used to join some of ours). Nearly all left are **our own savannas
  cut where our zones end**, and thin climate slivers.
- **Small and big savannas are one knob, our share of the zones** (step 3). Pretend layouts with Minecraft's zones
  swapped, 4 seeds (lonely share of the savanna's area, typical size, savanna's share of land):
  | our share (weight) | lonely | typical | land |
  |---|---|---|---|
  | 0.75 (30, today) | 7 to 12 percent | 1,532 to 1,972 (the real game) | 19 to 27 percent |
  | 0.86 (60) | 4 to 10 | 2,076 to 2,842 | 22 to 32 |
  | 0.90 (90) | 3 to 10 | 2,240 to 3,071 | 23 to 33 |
  | 1.00 (Minecraft's zones gone) | 2 to 6 | 2,264 to 3,133 | 25 to 36 |
  Narrower climate shapes made more small patches, not fewer. So step 3's bigger savannas are also the cure for the
  small ones; it must keep savanna under 30 percent of land (one seed went over at share 1.0), likely by narrowing the
  shape a little as the share rises.
- **The fallback not built (my call):** no mounds or Matabele in a lonely small patch would need a flood fill of the
  biome noise per mound place (up to about 770 samples, a new cost in world making, which the user has asked to keep
  fast) and a second rule judging savanna size beside the spawn's; step 3 removes most of what it would catch. To
  revisit after step 3 if small patches still matter in play.
- Tests: `bigSavannasHaveTheAgreedSizeAndRarity` checks the index, prints small and lonely patches per seed (now, and
  the pretend zones before and after), bands my picks a little over today's (at most 1,050 small, 18 percent of the
  area, lonely 14 percent; step 3 tightens them); the other kinds of ground at least 0.1 percent of the maps.
- Land fingerprints regenerated (a change on purpose): only the biome name moved, savanna to plains in 89 of 6,000
  columns; heights and ground the same. `worldgen,mound` pass on 6 servers; the mound survey (`MatabeleNewLandTests`)
  passes (real savanna 1 mound in 2.8 looks dead, 1 in 3.5 Matabele).

**Step 2a, one recipe everywhere (the user's OK, 2026-11-16, "that sound perfect start"; BUILT, branch
`claude/no-small-savannas-20e8a5`; only new land changes).** The user asked for another way than the cleaner. Step 2's
own table already pointed at it (our share 1.00): the small savannas are mostly our big ones cut where a zone of ours
meets one of Minecraft's, so the zones' difference is removed instead of the scraps cleaned up.
- **What:** Minecraft's zones are emptied as before, but their weight (10) now goes to the big savannas' region (30 +
  10), and the "Minecraft without savannas" region is gone. With no other biome mod every zone has the same table, so
  no zone edge changes anything. Fewer rules: one region, no share to tune (`SavannaRegion.signUp`, `MinecraftZones`).
- **Measured on the 4 maps (real TerraBlender layout):**
  | | step 2 | one recipe |
  |---|---|---|
  | small savannas (under 1,000 across) | 919 | 637 |
  | their share of the savanna's area | 14.7 percent | 5.2 |
  | lonely ones | 584 | 327 |
  | lonely share of the area | 10.9 percent | 3.6 |
  | typical savanna | 1,839 | 2,792 (seeds 2,264 to 3,133) |
  | biggest | 3,655 | 5,482 |
  | savanna's share of land | 21.6 percent | 28.6 (the limit is 30) |
  | a big one per | 3,752 blocks square | 4,147 |
  Other ground still turns up: desert 1.6 percent of the maps, jungle 0.7 (was 1.4), plains 9.1, badlands 0.8, forest
  18.6, cold 5.3.
- **Not zero.** What is left are thin climate slivers and ragged edges, not cut savannas: many patches (637) but little
  ground (5 percent of the savanna). No climate shape removes them (step 2: narrower shapes made more).
- **Not tuned (to decide in step 3):** savanna at 28.6 percent of land is close to the 30 limit, and jungle halved; the
  typical size (2,792) is on the way to step 3's 3,500 to 4,000 target but that target would push savanna past 30
  percent of land, so step 3 must pick between them.
- **With another biome mod** (Biomes O' Plenty) its zones still cut our savannas: out of reach.
- Tests: bands tightened to a little over the new numbers (720 small, 7 percent, lonely 5 percent). Land fingerprints
  regenerated on purpose: only biome names moved (111, 147 and 245 of 2,000 columns a seed, mostly to savanna; warm
  oceans in the former Minecraft zones), heights and blocks the same. `worldgen,mound` on 6 servers: all passed but
  the fingerprint, as it must before regenerating; not rerun after (the saved files are that run's own output).
- **Next, for the leftover slivers (the user's call, nothing built):** the cheap gate (mounds, giant trees, Matabele and
  river animals only where there is savanna all round for about 500 blocks, the spawn's own check), or step 2b's
  cleaner, now with a third of the ground to clean.

**Step 2c, the gate (the user, 2026-11-16: "do the gate thats perfect and its kind of realistic when you think about
it a biome too small to harbor an ecosystem would never begin to form one"; BUILT, branch
`claude/no-small-savannas-20e8a5`; only new land changes). It replaces step 2b, the cleaner, which is not built.**
- **What:** a savanna too small stays savanna grass but gets none of the ecosystem: no termite mounds (so no Matabele,
  no mound trees), no giant or floodplain trees, no seedlings after rain, no algae or old remains on its river beds, no
  tilapia, catfish, crocodiles, crabs or snails spawned by the world. Biomes are untouched (nothing of Minecraft's
  biome code is changed); eggs, commands and a player's own seedlings work anywhere. Silt banks, pools, dry grass and
  fallen sticks stay (scenery).
- **The rule, one number, one place (`worldgen/BigSavanna`):** a place lives if there is at least as much savanna
  within 640 blocks of it as a round savanna 800 blocks across holds (my picks). Counted from the biome noise on a
  128-block grid, 4 looks a cell, a savanna's own river counting as savanna; answers kept per cell with a cap.
- **A first rule was wrong and is gone:** "savanna all round at 200 and 400 blocks" (the spawn's kind of check)
  silenced 39 percent of the big savannas' ground: real savannas are lobed and run along coasts, and the looks hit
  ocean, beach, forest and plains. Measured before any number was tuned, then the rule itself replaced.
- **Measured on the 4 maps:** quiet 6.8 percent of the big savannas' ground (the tips of thin points), 81 percent of
  the ground of savannas under 500 across, 71 percent of all small savannas' ground. The small ground that still lives
  is the frayed bits beside a big savanna, on purpose (the big one's animals reach them). Pictures:
  `references/big_savannas/gate_seed_*.png` (quiet savanna in red).
- **Cost:** about 5 millionths of a second a look; a cold answer at most 324 looks (about 2 ms), shared by everything
  near; the 4 whole maps 5.6 s.
- **Who asks:** `MoundPlaces.holds`, `GiantTreeStructure.plan`, `FloodplainTreeStructure.foot`, `Forest.sproutSite`,
  `RiverAlgaeFeature`, `RiverRemainsFeature`, the five river animals' spawn rules (`SavannaEvents.inBigSavanna`).
- **Known:** the land is flattened by the climate, so a quiet savanna is flat bare grass; villages and Minecraft's
  animals still turn up there. A world of only savanna always lives.
- Tests: the map test checks the bands (big savannas' quiet ground at most 10 percent, under-500 savannas' at least
  70). `worldgen,mound,trees,river,colony` on 6 servers: all passed but two, the seedlings-after-rain test (the gate judged a test arena's hand-made savanna; fixed: savanna put there by hand is not judged; then 3 of 3) and the old half-built stage 14 flake (BUGS 151, 3 of 3 alone); the mound survey unchanged (1 mound in 2.8 looks dead, 1 in 3.6 Matabele).

**Step 2b, the small-savanna cleaner (NOT BUILT: replaced by step 2c, the gate)**

**Step 2b, the small-savanna cleaner (the user, 2026-11-16: "the ecosystem just wouldn't work on them"; nothing
built).** Step 2 left 919 savannas under 1,000 blocks across on the 4 maps (14.7 percent of the savanna's area); a
small savanna has no room for the termites, the Matabele within reach of them and the river life. The cleaner removes
every one:
- **What:** in new land any savanna patch under 1,000 blocks across (the biome maps' `SMALL`, my pick) becomes plains:
  savanna and floodplain plains, its savanna river Minecraft's river. Every savanna left is a big one.
- **How:** where our biome answer is savanna, floodplain or savanna river, the patch round it is looked up on a 32-block
  grid: a flood fill over the biome noise that stops as soon as it has counted enough cells for 1,000 across (about
  770), so a big savanna is known big quickly and a small one is measured whole. Answers are saved per grid cell, in a
  shared store with a cap (the memory leak lesson, `docs/BUGS.md` 222). The count of a patch does not depend on where
  the fill starts, so every chunk gets the same answer whichever loads first: no seams.
- **Where:** one place every biome question passes through (world making, `/locate`, the spawn search, the mounds'
  biome check, the biome maps); to be read in TerraBlender's and Minecraft's code first (likely a hook on the biome
  source's lookup). Only our own savanna biomes are touched: another mod's savannas (Biomes O' Plenty) are left alone.
- **Checks:** the biome maps: 0 small savannas (the band goes to 0), the typical size and the share of land savanna
  (the cleaner takes about 15 percent of the savanna's area: about 21.6 to 18 percent of land); the spawn search; the
  land fingerprints (only biome names should move); the mound survey (`MatabeleNewLandTests`); loading time before and
  after (`tools/flight.sh`, the ground height and spawn search timings), since loading has been slow before.
- **Not known yet:** the cost (my guess small, as each patch is measured once; to measure, not to assume); thin necks
  of savanna on the 32-block grid may be cut off from a big savanna and turn to plains (a cleaner edge in play); the
  land is flattened by the climate, not the biome, so a former small savanna becomes flat plains (fine), and trees,
  mounds and Matabele follow the biome, so they go with it.
- **Why before step 3 (my pick):** step 3 tunes the savannas' size and their share of the land; with the cleaner in
  first it tunes them once, on the final picture, and only has to make the big ones bigger, no longer chasing small
  ones. Done after, step 3's numbers would all move again.

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
