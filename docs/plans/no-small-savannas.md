# No small savannas (the user, 2026-11-13; steps 1 to 3 built 2026-11-16 as step 2 of `big-savannas-big-rivers.md`)

**Folded into `docs/plans/big-savannas-big-rivers.md` (2026-11-16) as its step 2.**

The user: "I kinda need every one of them to be big if the ecosystem is supposed to run properly."

## Where small savannas come from today
1. **Minecraft's own zones** (TerraBlender region `minecraft:overworld`, weight 10 against our 30, about 1 zone in 4):
   Minecraft's table with its small savannas, savanna plateaus and windswept savannas. The user's spawn of 2026-11-13
   was one (BUGS 209).
2. **Our big savannas cut at a zone's edge**, a small piece left alone on the far side.
3. **Thin climate slivers** where the warm dry climate barely reaches into another biome.

## Found: 1 can be removed (read in TerraBlender 4.1.0.8's classes, not tried)
- `Regions.register(name, region)` puts the region into a `LinkedHashMap` by name: a region signed up under
  `minecraft:overworld` replaces Minecraft's own.
- Ours: Minecraft's table (`OverworldBiomeBuilder`, as `SavannaRegion.table` already reads it) with the savanna biomes
  swapped. My picks: savanna and savanna plateau become plains, windswept savanna becomes windswept hills.
- Its weight: what Minecraft's region had (`Regions.get(OVERWORLD)`, the config's `vanilla_overworld_region_weight`).
- **The fiddly part:** on a replace the region's index is set to "regions minus 1", right only while Minecraft's is the
  only region. So replace before `SavannaRegion.signUp`, or `Regions.remove` then `register(name, 0, region)`; check the
  log line "Registered region minecraft:overworld to index 0" and the biome maps.

## What cannot be removed outright
2 and 3: biomes come from smooth climate noise, there is no "delete every savanna under 1,000 blocks". Measure the
leftovers and tune (zone edges, the climate shape). Fallback: mounds, giant trees and Matabele are not made in a patch
too small (one shared "is this savanna big here" question, as `SavannaSpawn.bigSavanna` asks). Out of reach: savannas
another biome mod brings in its own zones.

## Steps
1. The replacement region and its index check; `worldgen` tests.
2. The biome maps before and after: small patches counted (`BiomeMaps.patches`), a test band for them.
3. The leftovers tuned, or the fallback, by what step 2 shows.
4. The user's look in a new world. Only new land changes.

## Step 2 results (2026-11-16, steps 1 to 3 here, built as step 2 of `docs/plans/big-savannas-big-rivers.md`)
Full numbers in that plan's "Step 2 results". In short:
- Step 1: Minecraft's zones replaced (`worldgen/MinecraftZones`). Found while building: TerraBlender never asks index 0
  for biomes, so the plan's "a region of ours under Minecraft's name" alone would have changed nothing; index 0 is now an
  empty region under Minecraft's name and our table a region of its own at Minecraft's weight. Index checked in the log
  and the world test.
- Step 2: 919 small savannas left on the 4 maps (14.7 percent of the savanna's area), 584 lonely (10.9 percent);
  test bands set a little over that.
- Step 3: the leftovers are our own savannas cut at our zones' edges; the climate shape does not help (narrower makes
  more), a bigger share of the zones does, which is the big savannas plan's step 3. Fallback not built (a world-making
  cost and a second size rule), to revisit after step 3.

## Your calls
- What grows where Minecraft's savannas were (my pick: plains; windswept hills for the windswept ones).
- Whether leftover small patches may stay as empty grass (the fallback) if tuning cannot remove them.
