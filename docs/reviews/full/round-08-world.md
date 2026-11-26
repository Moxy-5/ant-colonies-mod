# Round 8: the world (reading round, 2026-11-23 and 24)

**Status: finished.** Every file of the system was read (list below), in two sittings (the first was cut short by the
usage limit). Nothing was measured in the game: this was a reading round, with no tests and no soak. Findings keep
their numbers for good; none was dropped (R8-1 to R8-20).

**How far each finding was checked (the second pass):** *proven* means the path was walked in the code or data named;
*likely* means the path is there but one link, named in the finding, could not be read here (mostly Minecraft's or
TerraBlender's own code, quoted from memory); *to measure* means a lag finding whose cost is an estimate, with the
scene to measure it.

## The four that matter most

1. **R8-11 (bug):** termite mounds of new land are lost when the game is left while land is still being made, and the
   lost places can turn up as mounds in the next world opened in the same sitting.
2. **R8-12 with R8-2 (lag):** the mod's 12 spawning groups are tried on every tick in every chunk of every world, also
   far from any savanna; about three times Minecraft's own spawner work. To measure.
3. **R8-18 (bug against a decision):** "no ravines or caves in the savanna" does not hold along the savanna's rivers:
   the river biome was left out of the rule, and ravines and caves started there run on into the plain.
4. **R8-14 (rules that fight):** the world maker lays mud in a picture (firm sticky edges, a drying ring round pans);
   the mud's own wet-and-dry rule wipes that picture out within minutes of a player being near.

## Read (every line unless said)

- `worldgen/`: `FlatSavanna`, `BigSavanna`, `SavannaRegion`, `SavannaSpawn`, `GroundHeights`, `MinecraftZones`,
  `RiverZones`, `RiverSites`, `RiverBends`, `SandBanks`, `PlainSurface`, `MudSites`, `NestSites`, `OxbowSites`,
  `PanSites`, `PodSites`, `RainModifier`, `WaterColourModifier`, `RiverBankFeature`, `OxbowFeature`, `PanFeature`,
  `RiverMudFeature`, `DryWallowFeature`, `RiverPlantsFeature`, `RiverAlgaeFeature`, `RiverRemainsFeature`,
  `CrocodileNestFeature`, `HippoPodFeature`, `QuietSavannaTreesFeature`, `MoundPlaces`, `TermiteMoundStructure`,
  `TermiteMoundPiece`. (The tree placing is round 7's.)
- Top level: `SavannaEvents`, `SavannaMod`, `SavannaConfig`, `SavannaSpecies`.
- `registry/`: `SavannaMobCategories`, `SavannaEntities`, `SavannaFeatures`, `SavannaStructures`, `SavannaTags`,
  `SavannaBoats`, `SavannaBlockEntities`, `SavannaParticles`, `SavannaSounds`, `SavannaBlocks`; `SavannaItems` and
  `TreeBlocks` are lists of sign-ups only and were skimmed (the tree blocks themselves are round 7's).
- `block/`: `SavannaMudBlock`, `AlgaeBlock`, `ReedBlock`, `HippoGrassBlock`, `HippoGrassClumpBlock`,
  `RiverPebblesBlock`, `RiverSand`, `PlantPush`. (`MudCrustBlock` and `MudCastBlock` are the termites' mud, round 5's.)
- `colony/NewLandMounds` lines 60 to 300 only (the hand-over from world making; the rest is round 5's).
- The data: every biome modifier, every placed feature, the mound's structure and structure set, the two water
  biomes, the biome tags, `META-INF/enumextensions.json`, the groups table in `docs/LIVING-THINGS.md`; of the terrain
  number files under `data/minecraft/worldgen/density_function/` which ones are replaced and what they point at, not
  the sums inside (made by `tools/flat_savannas/`, guarded by the `worldgen` tests).
- The world, river, mud and spawning entries of `docs/BUGS.md`.

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

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): fixed: each `RiverSites` keeps its answers with the world they were asked for (the seed, the biome source and the climate reader, by identity, as `BigSavanna` does) and starts afresh when any of them differs; all are let go when the server stops (`RiverSites.forgetAll`; `BigSavanna.forget` too, R8-9's first half). Not tried in the game (two worlds with one seed in one sitting).**

### R8-2 (A if the measure confirms it; likely, to measure): a 129-block search for animals on every spawn try

- **What is wrong:** `RiverZones.room` counts the animals of a kind within 64 blocks (`getEntitiesOfClass(Mob.class,
  box inflated by 64)`, a box 129 by 129 by 129, 9 by 9 chunks and every height) each time it is asked for a kind whose
  zone share is between 0 and 1 (12 kinds have such shares, `SHARES`). Minecraft's spawner asks a spawn rule for every
  try in every chunk round a player on every tick while a group is under its cap, and a zone share under 1 is exactly
  what can keep a group under its cap for good.
- **What the player would see:** higher tick times standing by a river whose kinds have part shares (coast and far
  inland rivers, oxbows), worse with many animals and insects about.
- **Where:** `worldgen/RiverZones.java:146-156`.
- **Proof (second session):** the caller is `SavannaEvents.inBigSavanna` (`SavannaEvents.java:118-124`), wrapped round
  every river animal's spawn rule: for a natural spawn it asks the animal's own rule first, then
  `BigSavanna.lives`, then `RiverZones.room`. So the search runs only for a try that already found a fitting spot for
  that kind, not for every try. Every one of the 12 groups is tried on every tick (R8-12). How many tries get as far as
  `room` each tick is still not measured.
- **Scene to measure:** a player standing still on a far-inland river bank and at an oxbow in a new world, Minecraft's
  profiler on the spawner (`/debug start`, 60 s), reading the share of `RiverZones.room`.
- **Fix proposed:** count per kind and 64-block cell once every second or so and reuse it; or check the cheap things
  (share 0, open sky) first and the count last (already so), and limit the search to the water's heights.

**Status: not a fault a player can meet** (2026-11-24, measured: `SpawnerCostTests.whatTheSpawnerCostsForOurGroups`). One count of a kind among 150 mobs within 64 blocks takes 5.7 microseconds (3.5 with the heights cut to 16 up and down), and it runs only for a try that already found a fitting spot for a part-share kind. Even 50 such tries in a tick are 0.3 ms. Left as it is; grade C.

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

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): open: proof owed. Whether a data pack biome mod (Terralith) really loses its biomes rests on a comment, not on TerraBlender's code or a world with such a mod; the fix proposed gives up the big savannas whenever such a mod is found, which is a game call. To do first: one world with Terralith, looked at on a map. The mods the user named (Biomes O' Plenty and other TerraBlender mods) are not hit.**

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
- **Second pass:** the keep-out round a tree is 3 blocks from its foot (`OxbowFeature.TREE_CLEAR`), far less than a
  crown's reach; leaves do count as solid for `ground`. Still owed: whether any tree of round 7 carries leaves or a
  branch as low as 4 to 7 blocks over the water's level (the young trees are the likely ones).
- **Fix proposed:** `clear` removes only up to the ground height the caller already read (`g`) plus the plants on it.

### R8-5 (C, proven in the code; how much it shows is likely): the river bank's look depends on which chunk was made first

- **What is wrong:** `RiverBankFeature` says its result is the same in any order because it "depends only on the
  sea-level water round it". But a water column only counts with *air* over it (`Waters.isWater`), and a neighbour
  chunk made earlier already has reeds, hippo grass mats or lily pads on its shallows, exactly the water nearest the
  bank. Those columns stop counting, so bank columns up to 13 blocks from the chunk's edge read the water as farther off.
- **What the player would see:** now and then a kink in the low strip, or the wet line of silt and mud missing, along
  a chunk's edge.
- **Where:** `worldgen/RiverBankFeature.java:289-304`.
- **Second pass:** the banks are made at step `local_modifications`, the reeds, mats and algae at
  `vegetal_decoration`, so a neighbour made earlier always has its plants; a reed standing in water is a block holding
  water at the water's level with a reed (not air) over it. The shift is one or two blocks of the strip's edge.
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

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): open: to measure. The work is on the world-making threads, not the tick; nobody has summed the oxbow feature's time on a flight along a river. To do first: `tools/flight.sh` with the feature's time summed; the fix (find the river's edges once a chunk, give up at once when there are none) only if the number shows.**

### R8-8 (C, proven): a log line for every site, laid or not

- `[world oxbows]`, `[world pans]`, `[world mud]`, `[world plants]` write an info line for every site chunk and every
  chunk with river plants (`OxbowFeature.java:120,127`, `PanFeature.java:92,98`, `RiverMudFeature.java:54`,
  `DryWallowFeature.java:65`, `RiverPlantsFeature.java:110`). Exploring fills `latest.log`. Fix: debug level.

### R8-9 (C, proven): two small leftovers between worlds

- `BigSavanna.store` keeps the last world's biome source and up to 400,000 answers after the world closes
  (`BigSavanna.java:45`); `SavannaSpawn.pendingMessage` is cleared only by a log-in (`SavannaSpawn.java:62`). Fix:
  clear both on `ServerStoppedEvent`.

### R8-10 (B, proven in the data; known and written down in `docs/DESIGN.md`): the flat savannas replace eleven of Minecraft's own land files

- The mod's data replaces `minecraft:overworld/erosion`, `offset`, `ridges_folded`, `sloped_cheese`,
  `caves/entrances` and `caves/noodle`, and `minecraft:overworld_large_biomes/erosion`, `factor`, `jaggedness`,
  `offset` and `sloped_cheese` (each wraps a copy of Minecraft's own, kept as `antcolonies:.../raw_*`, and adds the
  savanna's change inside the climate mask). Any other world-shaping mod or data pack replacing one of the same files
  wins or loses whole, by load order: their land, or our flat savannas and filled caves. `docs/DESIGN.md` ("Flat
  savannas") already names this as a known cost; kept here so the count of files is on record. The copies also freeze
  Minecraft 1.21.1's own numbers: a Minecraft update that changes them needs `tools/flat_savannas/` run again.

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): not a bug: a known cost of the flat savannas, decided and written down in `docs/DESIGN.md` ("Flat savannas"). Kept here for the count of files (eleven) and the reminder that a Minecraft update which changes its own numbers needs `tools/flat_savannas/` run again.**

### R8-11 (A, proven in the code): mounds of new land are lost when the game is left, and can turn up in the next world

- **What is wrong:** world making hands each termite mound's place to the server through a list that lives outside the
  world (`NewLandMounds.QUEUE`, a `static` list). The server moves the list into the saved book only on every 10th
  tick, and only while the world ticks. The list is never saved and never emptied when a world closes. So every place
  handed over after the last look is gone from that world for good (its chunk is saved as made, so world making never
  offers the place again), and it is still in the list when the next world opens in the same sitting: the next world's
  first look takes every place of the same dimension into *its* book and saves it there.
- **What the player would see:** (1) fly over new savanna, press Esc and "Save and Quit" (the world stops ticking the
  moment the menu opens, while land already asked for goes on being made): the mounds of the last strip of land are
  missing for good, a hole in the mound field, dead mounds and their Matabele colonies with them. (2) Open another
  world without closing the game: a termite mound (living colony or dead mound, with its dead tree) is put in at the
  old world's coordinates as soon as that land loads, on any diggable dry ground, in any biome (a mound in a forest or
  a village), since the server's check looks only at water, soil and other colonies near.
- **Where:** `colony/NewLandMounds.java:93` (the list), `:128-131` (`queue`, called from
  `worldgen/TermiteMoundPiece.java:65-67`), `:137-148` (taken in only when `gameTime % 10 == 0`), `:240-253` (what the
  server checks before it puts a mound in); `SavannaEvents.java:211-213` (the only thing done when the server stops is
  `RaidView.clear()`).
- **Proof:** walked: nothing reads or clears `QUEUE` but `tick` and the soak's `stillWaiting`; `Queued` holds the
  dimension's name, the same in every world. Not measured: how many places are in the list at a usual quit.
- **Fix proposed:** on `ServerStoppingEvent` (before the worlds are saved) take the list into each level's book, and on
  `ServerStoppedEvent` empty what is left; better still, hand over with the level itself (not its name) so a place can
  never reach another world.

**Status: fixed** (2026-11-24, BUGS 321; test `aMoundHandedOverAsTheWorldClosesIsNotLost`). A place is kept with its own world (not its dimension's name); when a world closes, after its last save (`LevelEvent.Unload`), the places still on their way go into its book and the book is written once more; when the server has stopped the list is emptied. Checked in Minecraft's source: on stop it finishes the land being made, saves, then closes each world, so nothing is handed over after that. **Open:** a crash (no clean stop) can still lose the places handed over in the last half second.

### R8-12 (A if the measure confirms it; to measure): twelve spawning groups are tried on every tick in every chunk, everywhere

- **What is wrong:** the mod adds 12 spawning groups (`META-INF/enumextensions.json`), every one marked "not
  persistent". Minecraft tries each such group for every chunk round every player on every tick for as long as the
  group is under its cap (the persistent ones, like its own farm animals, only every 400 ticks). Our groups have spawn
  lists only in the savanna's waters, so everywhere else (forests, oceans, caves, the Nether, the End) they are under
  their cap for ever and tried for ever, each try doing a random spot, a block read, up to three nearest-player
  searches and up to three spawn-list lookups before it finds the list empty. Minecraft itself has 6 groups tried
  every tick; with ours there are 18, so about three times the spawner's work in every world with this mod, also far
  from any savanna. `hippo_pod` has no spawn list in any biome at all (hippos come only with new land) and is tried all
  the same.
- **What the player would see:** a steady extra tick cost everywhere, growing with the players online and the view
  distance; worst in a savanna by a river, where the rules of R8-2 run on top.
- **Where:** `META-INF/enumextensions.json` (12 rows, the fourth value `false`); `registry/SavannaMobCategories.java`;
  the spawn lists `data/antcolonies/neoforge/biome_modifier/*_spawns.json` (all on `#antcolonies:savanna_waters`).
- **Proof:** the 12 rows and the spawn lists were read; Minecraft's spawner (`NaturalSpawner.spawnForChunk`,
  `spawnCategoryForPosition`) is quoted from memory, not re-read here. Not measured.
- **Scene to measure:** a new world, a player standing still (a) in a forest far from any savanna and (b) on a savanna
  river bank; `/debug start` for 60 s, reading the `mobSpawner` share of the tick; then the same with the mod's
  groups taken out of the test build (or the unmodded game) for the difference.
- **Fix proposed:** fewer groups tried every tick. Mark the rare, kept kinds' groups persistent (river_bank, bull_shark,
  river_otter, river_hunter, hippo_pod, bullfrog, river_grazer: tried every 20 seconds, which is plenty for animals
  that are kept), and take `hippo_pod` out of the spawner altogether if nothing is ever meant to spawn in it. This
  changes how fast those kinds appear, so it needs a look at the groups table in `docs/LIVING-THINGS.md` first.

**Status: measured, grade B now, open** (2026-11-24, `SpawnerCostTests.whatTheSpawnerCostsForOurGroups`, run by name, in the `stress` category). One try of a group with no spawn list costs 27 ns in solid ground (most tries: the spot's height is drawn from the world's floor up) and 803 ns in air or water. For 12 groups over the 256 chunks round one player that is about **0.15 ms a tick on land and 0.56 ms by open water** (2.5 ms if every try were in the open, which no real land is). Under 4 percent of the 15 ms target for four players, and no single slow tick: not grade A. **Done:** `hippo_pod`, which has no spawn list anywhere, is now tried only once every 20 seconds (a twelfth of the cost gone, no change in play). **Not done, on purpose:** making the other kept kinds' groups slow too would change how fast crocodiles, otters and the rest appear by a river, a game call for a gain of about 0.1 ms.

### R8-13 (C, to measure): a mound place reads 13 columns of ground before it asks which biome it is in

- **What is wrong:** the mound structure is offered in every chunk of the overworld (`spacing` 1). For every chunk
  that is a candidate place, `MoundPlaces.holds` works out the ground's height at 13 columns from the land's noise and
  only afterwards asks whether the place is in a savanna at all (one cheap reading). In oceans, forests and mountains
  the 13 columns are wasted.
- **What the player would see:** nothing by eye; a little slower new land everywhere (world-making threads).
- **Where:** `worldgen/MoundPlaces.java:222-265` (`warm` at 237, the biome check at 256-265);
  `data/antcolonies/worldgen/structure_set/termite_mounds.json` (`spacing` 1);
  `worldgen/TermiteMoundStructure.java:331-346`.
- **Scene to measure:** the flight run over land with no savanna, the structure-start time summed with and without the
  biome check moved first.
- **Fix proposed:** the biome and big-savanna checks first (at the surface height of the middle column only), the 13
  columns after.

### R8-14 (B, proven in the code): the mud's own wetness rule undoes the pattern the world maker lays

- **What is wrong:** the features lay mud in a picture: river mud with a sticky edge (`RiverMudFeature.java:96-100`),
  a pan with a sticky shore and a ring of drying mud round it (`PanFeature.java:184-185, 207, 218`). Every mud block
  then runs its own rule on random ticks (`SavannaMudBlock.nextWetness`, `:298-307`): water within 4 blocks makes it
  one step wetter each time until it is soupy; with no water near it dries. So within some minutes of a player being
  near, every laid block within 4 of the water is soupy whatever the feature chose, and the rest dries: two places
  decide the same thing, and the block always wins.
- **What the player would see:** fresh banks and pans look one way when first met and another after a while (the firm
  edge gone soupy, so walking the water's edge slows or sinks where the feature meant firm ground); land visited
  before looks different from new land.
- **Where:** as above; `block/SavannaMudBlock.java` (`isRandomlyTicking` always true).
- **Proof:** river mud is laid only within 4 blocks of the water (`RiverMudFeature.java:91`, the same 4 as the
  block's `WATER_REACH`), so every block of it counts as wetted, and a wetted block goes one step wetter on every
  random tick with no dice (`SavannaMudBlock.java:298-302`): the sticky edge is soupy after its first random tick,
  about a minute on average. Mud more than 4 from water dries a step with a chance of 1 in 6 (depth 1) a random tick,
  so a pan's drying ring is cracked after some minutes. Rain wets all of it. Not measured in the game; whether the
  sticky edge was meant to last is a design question.
- **Fix proposed:** one place decides: either the feature lays what the block's rule would settle at (so nothing
  changes after), or the rule keeps a laid edge (a "settled" wetness by distance to water: soupy at 1, sticky at 2 to
  4, drying beyond).

**Your call (2026-11-26):** Mud at the water's edge. Today new river banks and pans are laid with a firm sticky edge and a drying ring, but within minutes of you being near every mud block within 4 blocks of water turns soupy (slow, sinking) and the rest dries and cracks, so land you have visited looks and walks differently from new land. Should the mud settle by its distance to water (soupy right at the water, sticky 2 to 4 blocks out, drying beyond; rain still wets it), so the pattern the world lays is the one that stays? My pick: yes.

### R8-15 (C, proven in the code; the count rule from memory): the colonies' insects count as Minecraft's farm animals

- **What is wrong:** termites and Matabele ants are signed up in Minecraft's own `CREATURE` group
  (`registry/SavannaEntities`), and nothing takes them out of the count (`AbstractAntEntity.java:2938` only stops them
  vanishing with distance). That group's cap is 10 round a player; one colony's 50 real insects fill it many times.
- **What the player would see:** near a colony no chicken, armadillo or (in a neighbouring biome) cow, pig or sheep ever
  spawns after the land was made; the animals made with the land are not touched.
- **Second pass:** nothing in the framework or the savanna mod marks an insect persistent
  (`setPersistenceRequired` is called only for a froglet and in tests) and none overrides its group, so they count.
  That Minecraft leaves out only persistent mobs when it counts a group is quoted from memory.
- **Fix proposed:** a group of the insects' own with no spawn list and marked persistent, or `MISC`.

### R8-16 (C, likely): algae can creep out of the savanna

- `AlgaeBlock.spread` (`block/AlgaeBlock.java:146-157`) seeds a new mat on any neighbouring bed under still water, with
  no look at the biome. Where a savanna river runs into another biome's river or the sea, mats creep on along the
  shallows, slowly (1 try in 10 random ticks of a thick mat). Fix: spread only inside `#antcolonies:savanna_waters`.

### R8-17 (C, proven): notes that no longer match the code

- `SavannaMobCategories.flyCloud`'s note says a cap of 6; the data says 18. A doc comment in `SavannaEvents.java:204`
  ("The raid view forgets a player who leaves...") hangs over `onMount`, which is about the crocodile's jaws. `SavannaMod`'s
  class note still speaks of the desert mod. One misplaced note in `SavannaTags`. Fix with the next change there.

### R8-18 (A, proven in the data; Minecraft's carver rule from memory): ravines and caves still come into the savanna along its rivers

- **What is wrong:** the user's decision of 2026-11-07 is "no dips, ravines or caves" in the savanna. The rule that
  takes the ravine and cave carvers out (`no_savanna_ravines_or_caves.json`) names the biomes in `#minecraft:is_savanna`.
  The savanna's river (`antcolonies:savanna_river`, added the same week) is not in that tag (it is in `#is_river` and
  in our own `savanna_waters`), and its own file lists all three carvers. Minecraft picks a chunk's carvers by the
  biome at that chunk's corner and lets a ravine or cave started there run on for up to about 100 blocks. So every
  river chunk can start one, and it cuts on into the flat plain and the floodplain beside the river. (The noise caves
  are filled by the climate mask, which does cover the river; this is about the carved ones only.)
- **What the player would see:** now and then a ravine or a cave mouth in the flat savanna within about 100 blocks of
  a river, or a deep water-filled trench across the river's bed; for the insects, a hole that paths and nests must go
  round (BUGS 121 and 140 were scouts stuck at a ravine's lip).
- **Where:** `data/antcolonies/neoforge/biome_modifier/no_savanna_ravines_or_caves.json` (written by
  `tools/flat_savannas/flat_caves.py`, last lines); `data/antcolonies/worldgen/biome/savanna_river.json` (`carvers`).
- **Proof still owed:** not looked for in a world. A cheap check for a game test: the river biome's carver list, as the
  game sees it after the modifiers, must be empty.
- **Fix proposed:** the rule names `#antcolonies:savanna_waters` (the tag every other river rule here already uses) and
  the river's own file lists no carvers. A carver started in another biome within 100 blocks of a savanna's edge still
  reaches in; that is Minecraft's own rule and stays.

**Status: fixed** (2026-11-24, BUGS 322; test `noRavinesOrCavesStartInTheSavannaOrItsWaters`). The savanna's river lists no carvers of its own, and the rule that takes them out of the savanna names `#antcolonies:savanna_waters` (the savanna, its floodplain and its river), in the data file and in the script that writes it (`tools/flat_savannas/flat_caves.py`). Only new land changes; a ravine already cut stays. A carver started in another biome within about 100 blocks still reaches in (Minecraft's own rule).

### R8-19 (B, likely): in a Large Biomes world the cave filling looks at the wrong map

- **What is wrong:** cave mouths and the thin "noodle" caves are filled where the climate is the savanna's, by adding
  the savanna mask to two of Minecraft's cave files. Minecraft has only one copy of those two files for normal and
  Large Biomes worlds alike, and our replacement points at the normal world's mask
  (`antcolonies:overworld/savanna_flatness`, built on the normal climate noises), while a Large Biomes world's
  savannas lie where the large noises say (`overworld_large_biomes/savanna_flatness`). The land's flattening has its
  own large-biome files; the cave filling has none.
- **What the player would see:** in a Large Biomes world only: cave mouths and pits in the flat savanna, and patches
  of other land with no cave mouths (where the normal map would have had a savanna).
- **Where:** `data/minecraft/worldgen/density_function/overworld/caves/entrances.json`, `noodle.json`;
  `tools/flat_savannas/flat_caves.py`.
- **Proof still owed:** that Large Biomes worlds read the same two cave files is from memory of Minecraft's noise
  settings, not re-read; not looked for in a world.
- **Fix proposed:** a small density function of our own that answers with the mask of the world being made (the
  `antcolonies:whole_savanna` type already knows which world it is in), used by the two cave files.

**Status (2026-11-26, `claude/review-fix-b5-bc1153`): open: proof owed, and only Large Biomes worlds. That such a world reads the same two cave files is from memory of Minecraft's noise settings (mine agrees with the review's), and nobody has looked at one. The fix is a density function of our own that picks the mask of the world being made; it changes the wiring of every world's land, so it is built only once a Large Biomes savanna has been seen with cave mouths in it.**

### R8-20 (C): small things

- **A reed planted in water 2 deep never grows (proven):** `ReedBlock.grow` needs air over the top block
  (`block/ReedBlock.java`, `grow`), and in 2-deep water the block over a lone bottom block is water. The same after a
  player breaks the under-water second block of a column. Fix: grow into a water source too, holding the water.
- **No savanna villages for any mod (proven in the data):** `data/minecraft/tags/worldgen/biome/has_structure/village_savanna.json`
  has `"replace": true` and no biomes, which also wipes what other mods add to that tag. If no savanna villages is
  meant only for our savannas, this needs another way (a note for the day another biome mod is tested).
- **Minecraft's own sand, clay and gravel discs on our banks (likely, a look question):** the floodplain and
  Minecraft's savannas keep `disk_sand`, `disk_clay` and `disk_gravel`, laid after our banks and oxbows; they turn
  grass and dirt within 2 to 6 blocks of a water column to plain yellow sand or gravel, so on the low strip and round
  oxbows (the savanna's river biome itself has none). Not looked at in a world; if the user has not minded them,
  nothing to do.

## Found solid

- **Reaching into neighbouring chunks:** `RiverBankFeature`, `RiverMudFeature`, `RiverPlantsFeature`,
  `RiverAlgaeFeature`, `RiverRemainsFeature`, `CrocodileNestFeature` and `QuietSavannaTreesFeature` write only their
  own chunk (or within 8 blocks of it) and read at most 13 blocks out; `OxbowFeature`, `PanFeature`,
  `DryWallowFeature` keep every read and write inside the 3 by 3 chunks a feature may touch (bounds checked column by
  column); `HippoPodFeature` stays inside the bounds `OxbowFeature.pod` hands it (its standing spot reads one column
  past its feet, and the bounds are pulled in by one for that).
- **World-making threads and the live world:** no feature reads a chunk of the live world. What they take from the
  server level is the seed, the noise and the generator only (`MoundPlaces.Land.of`, `GroundHeights`). The one
  hand-over to the server, `NewLandMounds.queue`, is safe between threads in itself (its fault is R8-11, what happens
  to the list afterwards); the server puts a mound in only once `LandReady` says the land round it is loaded (the
  BUGS 183 lesson holds).
- **The order of our features in a chunk:** oxbows and pans at `lakes`, the banks and river mud at
  `local_modifications`, the mound's place and the giant trees at `surface_structures`, algae, remains, nests, pods
  and water plants (in that order) at `vegetal_decoration`, dry wallows last. The oxbows come before the banks on
  purpose and do not fight them: an oxbow's water stands a block or two over the river's, the banks read only water at
  the river's own level, and the oxbow keeps off the river's strip by its distance to the water, not by reading the
  strip.
- **`GroundHeights`:** capped (4,096 pages a kind), let go with its world, safe from several threads.
- **`FlatSavanna.Shared`:** the BUGS 222 leak fix holds (one memory per thread, cleared per wiring).
- **`BigSavanna.livesAt`:** answers per cell, capped, the same whoever asks first.
- **`PlainSurface`:** switches TerraBlender's chooser off only when every recipe is the same as the world's own, and
  leaves everything alone on any surprise.
- **`RiverBends`, `SandBanks`:** from the noise alone, a few dozen readings a chunk.
- **`MoundPlaces`' memories:** `isPlace` is keyed by the seed and the numbers it depends on; the dead-mound memory is
  thrown away when the seed or the mound numbers change (new numbers are read with every world), and capped.
- **The spawning groups' caps:** the 12 rows of `enumextensions.json` match the groups table in
  `docs/LIVING-THINGS.md` row by row; no rare kind shares a group with a common kept one. Every river animal's spawn
  rule goes through the quiet-savanna gate (`SavannaEvents.inBigSavanna`), its own rule first, the costly count last.
- **The spawn lists:** all on `#antcolonies:savanna_waters`; `no_vanilla_mobs` takes Minecraft's monsters, farm
  animals, bats, squid and river fish out of the same biomes and leaves the chicken and armadillo in, as planned.
- **The blocks' ticking:** mud's random tick reads at most 162 cells and only loaded land; reeds tick only at their
  top block; `PlantPush` looks again only at pushed plants (every 10 ticks, one small search) and its entity hook
  leaves at once for anything that is not a many-part animal; the fire hook in `SavannaEvents` leaves at once unless
  the changed block is fire.

## Not read here (other rounds')

- The tree placing and the tree blocks (round 7); the termites' mud blocks `MudCrustBlock`, `MudCastBlock` and the
  rest of `colony/NewLandMounds` (round 5); the animals' own spawn rules inside each animal class (the animals'
  round) — R8-2's measure needs them.
- TerraBlender's and Minecraft's own code (R8-3, R8-12, R8-15, R8-18, R8-19 quote it from memory).
