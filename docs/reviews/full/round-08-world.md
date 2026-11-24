# Round 8: the world (reading round, 2026-11-23) — UNFINISHED

**Status: cut short by the usage limit after the first pass over about half of `worldgen/`.** Nothing below was
measured in the game (reading round: no tests, no soak). Findings keep their numbers for good; a later session carries
on from "Not read yet" at the bottom and adds R8-11 onward.

**The second pass is only partly done.** Each finding says how far it was checked: *proven* means the path was walked
in the code named; *likely* means the code path is there but one link (named in the finding) was not read yet.

## Read so far (every line)

`FlatSavanna`, `BigSavanna`, `SavannaRegion`, `SavannaSpawn`, `GroundHeights`, `MinecraftZones`, `RiverZones`,
`RiverSites`, `RiverBends`, `SandBanks`, `PlainSurface`, `MudSites`, `NestSites`, `OxbowSites`, `PanSites`, `PodSites`,
`RainModifier`, `WaterColourModifier`, `RiverBankFeature`, `OxbowFeature`, `PanFeature`, `RiverMudFeature`,
`DryWallowFeature`, `RiverPlantsFeature`; and the world, river, mud and spawning entries of `docs/BUGS.md`.

## Findings

### R8-1 (B, proven in the code; what the player sees is likely): river sites are remembered by the seed alone

- **What is wrong:** `RiverSites` (the shared spacing rule for crocodile nest sites, hippo pools, oxbows, pans, river
  mud and dry wallows) keeps its answers in two maps that are thrown away only when the *seed* changes or they grow past
  200,000. The biome source and the land's noise are not part of the key. Two worlds with the same seed but different
  land (a normal world and a single-biome savanna, large biomes, with or without another biome mod) opened in one
  sitting share the first world's answers.
- **What the player would see:** after "Re-create world" (which copies the seed) with another world type, without
  restarting the game: nests, hippo pods, oxbows, pans and mud at the first world's places, so mostly missing in the
  second.
- **Where:** `worldgen/RiverSites.java:28-29` (the maps and `cachedSeed`), `:68-72` (cleared on seed change only);
  every user is a `static final` (`NestSites.java:29`, `PodSites.java:19`, `OxbowSites.java:17`, `PanSites.java:19`,
  `MudSites.java:39-40`).
- **Proof:** `candidate()` compares only `cachedSeed != seed`; `biomes` and `sampler` are passed straight to the test
  and never compared. `BigSavanna` beside it does compare both (`BigSavanna.java:53`).
- **Fix proposed:** keep the biome source and sampler with the maps (as `BigSavanna.Store` does) and start afresh when
  either differs; clear on `ServerStoppedEvent` too.

### R8-2 (A if the measure confirms it; likely, to measure): a 129-block search for animals on every spawn try

- **What is wrong:** `RiverZones.room` counts the animals of a kind within 64 blocks (`getEntitiesOfClass(Mob.class,
  box inflated by 64)`, a box 129 by 129 by 129, 9 by 9 chunks and every height) each time it is asked for a kind whose
  zone share is between 0 and 1 (12 kinds have such shares, `SHARES`). Minecraft's spawner asks a spawn rule for every
  try in every chunk round a player on every tick while a group is under its cap, and a zone share under 1 is exactly
  what can keep a group under its cap for good.
- **What the player would see:** higher tick times standing by a river whose kinds have part shares (coast and far
  inland rivers, oxbows), worse with many animals and insects about.
- **Where:** `worldgen/RiverZones.java:146-156`.
- **Proof still owed:** the callers (`SavannaEvents`, the spawn rules in `registry/SavannaEntities`) were not read yet,
  so how often `room` runs per tick is not known; and each group's spawn interval (`SavannaMobCategories`).
- **Scene to measure:** a player standing still on a far-inland river bank and at an oxbow in a new world, Minecraft's
  profiler on the spawner (`/debug start`, 60 s), reading the share of `RiverZones.room`.
- **Fix proposed:** count per kind and 64-block cell once every second or so and reuse it; or check the cheap things
  (share 0, open sky) first and the count last (already so), and limit the search to the water's heights.

### R8-3 (B, likely): biome mods that are data packs lose all their biomes

- **What is wrong:** `MinecraftZones.replace` empties TerraBlender's zone 0 (Minecraft's own) and gives its weight to
  our region, whose table is rebuilt from Minecraft's built-in list (`new OverworldBiomeBuilder()`). By the class's own
  notes zone 0 is the only zone that uses the world preset's biome list. A mod or data pack that adds its biomes by
  changing that list (not through TerraBlender; Terralith is the best known) then has no zone left.
- **What the player would see:** with such a mod installed, none of its biomes anywhere, with no warning.
- **Where:** `worldgen/MinecraftZones.java:84-97`, `worldgen/SavannaRegion.java:110-128`.
- **Proof still owed:** TerraBlender's own classes were not re-read here; the claim rests on this file's comment
  (lines 33-36). Biomes O' Plenty and other TerraBlender mods are not hit.
- **Fix proposed:** at server start compare the preset's biome list with Minecraft's built-in one; if it has biomes of
  another namespace, leave zone 0 alone (small savannas come back in those zones) and say so in the log.

### R8-4 (C, likely): pans and oxbows cut holes in tree crowns that stand in land made earlier

- **What is wrong:** `clear()` in both features looks for the column's top from 6 or 7 blocks over the water's level
  and removes everything from the water's level up to that top plus 3. `clearAbove` only makes sure the first 3 blocks
  over the ground hold nothing but plants, so leaves or a branch 4 to 7 blocks up count as "the ground's top" and are
  removed. A pan or oxbow writes into the 3 by 3 chunks round its site, and a neighbour made earlier already has its
  trees.
- **What the player would see:** acacias beside a pan or oxbow with column-shaped holes in the crown, or a branch cut
  through, while the trunk stands.
- **Where:** `worldgen/PanFeature.java:389-397` (used at 190, 234, 240, 330, 363), `worldgen/OxbowFeature.java:539-547`.
- **Proof:** `RiverBankFeature.ground` stops at the first `isSolid` block from the top, and leaves count as solid.
- **Fix proposed:** `clear` removes only up to the ground height the caller already read (`g`) plus the plants on it.

### R8-5 (C, likely): the river bank's look depends on which chunk was made first

- **What is wrong:** `RiverBankFeature` says its result is the same in any order because it "depends only on the
  sea-level water round it". But a water column only counts with *air* over it (`Waters.isWater`), and a neighbour
  chunk made earlier already has reeds, hippo grass mats or lily pads on its shallows, exactly the water nearest the
  bank. Those columns stop counting, so bank columns up to 13 blocks from the chunk's edge read the water as farther off.
- **What the player would see:** now and then a kink in the low strip, or the wet line of silt and mud missing, along
  a chunk's edge.
- **Where:** `worldgen/RiverBankFeature.java:289-304`.
- **Fix proposed:** count water with one of our water plants or a lily pad over it as water.

### R8-6 (C, proven): the spawn search reads one row of columns from the wrong chunk

- **What is wrong:** `SavannaSpawn.groundIn` tries columns at the chunk's middle plus -8 to +6. The middle is the
  chunk's block 7, so -8 is the column *before* the chunk. Its height is then taken from this chunk's column 15
  (`x & 15`) while the blocks are read at the real place in the neighbour. The very first column tried (i = 0) is such
  a one, in both directions.
- **What the player would see:** on flat savanna nothing; on a step, the world spawn a block off or under a tree's
  crown (the "open sky" check reads the wrong column).
- **Where:** `worldgen/SavannaSpawn.java:253-275` (lines 256-258).
- **Fix proposed:** offsets from -7 to +7 inside the chunk, or read the heights from the level, not the one chunk.

### R8-7 (B, to measure): an oxbow site with no room does all its shape sums 8 times

- **What is wrong:** on an oxbow site the chunk tries 8 times; each try re-rolls the shape and, for up to 9 sizes,
  works the shape's box out 6 times over a grid of some thousands of points with trigonometry, before it finds out
  that the chunks hold no river edge with room. The answer "no river water here at all" is the same for every try.
- **What the player would see:** a hitch in new land loading along floodplains (world-making threads, not the tick).
- **Where:** `worldgen/OxbowFeature.java:115-128`, `:162-176`, `:486-499`.
- **Scene to measure:** the flight run (`tools/flight.sh`) along a river, with the feature's time summed.
- **Fix proposed:** find the river edges once per chunk before the tries; give up at once when there are none.

### R8-8 (C, proven): a log line for every site, laid or not

- `[world oxbows]`, `[world pans]`, `[world mud]`, `[world plants]` write an info line for every site chunk and every
  chunk with river plants (`OxbowFeature.java:120,127`, `PanFeature.java:92,98`, `RiverMudFeature.java:54`,
  `DryWallowFeature.java:65`, `RiverPlantsFeature.java:110`). Exploring fills `latest.log`. Fix: debug level.

### R8-9 (C, proven): two small leftovers between worlds

- `BigSavanna.store` keeps the last world's biome source and up to 400,000 answers after the world closes
  (`BigSavanna.java:45`); `SavannaSpawn.pendingMessage` is cleared only by a log-in (`SavannaSpawn.java:62`). Fix:
  clear both on `ServerStoppedEvent`.

### R8-10 (B, likely; data not read yet): the flat savannas replace two of Minecraft's own land numbers

- `FlatSavanna`'s notes say the land is flattened by overriding `minecraft:overworld/erosion` and `ridges_folded` in
  data. Any other world-shaping mod or data pack overriding the same two files wins or loses whole: either their land
  or our flat savannas, by load order. **Proof still owed:** the data files under `data/minecraft/worldgen/` were not
  read.

## Found solid so far

- **Reaching into neighbouring chunks:** `RiverBankFeature`, `RiverMudFeature`, `RiverPlantsFeature` write only their
  own chunk and read at most 13 blocks out; `OxbowFeature`, `PanFeature`, `DryWallowFeature` keep every read and write
  inside the 3 by 3 chunks a feature may touch (bounds checked column by column). None reads the live world's chunks;
  what they take from the server level is the noise and the generator only.
- **`GroundHeights`:** capped (4,096 pages a kind), let go with its world, safe from several threads.
- **`FlatSavanna.Shared`:** the BUGS 222 leak fix holds (one memory per thread, cleared per wiring).
- **`BigSavanna.livesAt`:** answers per cell, capped, the same whoever asks first.
- **`PlainSurface`:** switches TerraBlender's chooser off only when every recipe is the same as the world's own, and
  leaves everything alone on any surprise.
- **`RiverBends`, `SandBanks`:** from the noise alone, a few dozen readings a chunk.

## Not read yet (for the session that carries on)

- `worldgen/`: `RiverAlgaeFeature`, `RiverRemainsFeature`, `CrocodileNestFeature`, `HippoPodFeature` (check its reads
  stay inside the bounds `OxbowFeature.pod` gives it), `QuietSavannaTreesFeature`, `MoundPlaces` (check that
  `MoundPlaces.Land.of(ServerLevel, ...)`, called from world-making threads by `OxbowFeature.keepout`, never reads a
  chunk of the live world), `TermiteMoundStructure`, `TermiteMoundPiece`. The tree placing is round 7's.
- `registry/` (all 12 files; `SavannaMobCategories` and `SavannaEntities` first: the groups' caps, and the callers of
  `RiverZones.room` for R8-2), `SavannaEvents`, `SavannaMod`, `SavannaConfig`, `SavannaSpecies`.
- The mud and water-plant blocks: `SavannaMudBlock`, `MudCrustBlock`, `AlgaeBlock`, `ReedBlock`, `HippoGrassBlock`,
  `HippoGrassClumpBlock`, `RiverPebblesBlock`, `RiverSand`, `PlantPush`.
- The data: biome modifiers, placed features (and the order of our features inside a chunk), the spawn lists,
  `META-INF/enumextensions.json`, `data/minecraft/worldgen/` (R8-10).
