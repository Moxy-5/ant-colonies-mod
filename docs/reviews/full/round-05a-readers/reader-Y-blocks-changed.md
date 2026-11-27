# Round 5a, reader Y: every block the mound places or removes

Read at commit `4dcd1fe6` in `C:\Ant Mod` (branch `claude/motion-glue`). No code changed, nothing run, nothing
measured. Paths are short: `savanna/...` is `savanna/src/main/java/com/antcolonies/savanna/...`, `framework/...` is
`framework/src/main/java/com/antcolonies/...`. Minecraft's own code was not opened in this session: what I say about
it (how `setBlock` tells the neighbours, what `canBeReplaced` covers, how a chunk is fetched) is from what I know of
1.21.1 and is marked where a finding leans on it.

One word used all through: **"replaceable"** is Minecraft's own flag (`state.canBeReplaced()`): air, short and tall
grass, ferns, dead bushes, vines, snow layers, fire, **water and lava**, and of this mod's blocks the dry grass, the
Termitomyces mushroom and the algae. Flowers, torches, crops, carpets, rails and every block with something in it
(chests, furnaces) are **not** replaceable.

## The table: every place the mound's code changes a block

"Flags" are Minecraft's update flags: **3** (= `UPDATE_ALL`, also what `setBlockAndUpdate`, `removeBlock` and
`destroyBlock` use) tells the six neighbours, lets their shapes follow, and sends the change to the players; **2**
sends it to the players and lets the neighbours' shapes follow, but sends no "neighbour changed" call.
`destroyBlock(pos, false)` is flags 3 plus the breaking sound and crumbs, and no drops.

### A. The living mound

| # | Who (class, method, line) | What block | When | Flags | What is checked first |
|---|---|---|---|---|---|
| A1 | `colony/Mound.addLayer` `:765-794` → `block/TermiteMoundBlock.addLayer` `:132-144` | mound soil: a new 1-layer block, or one layer more (5 = finished) | a worker's goal (`BuildMoundGoal.place` `:248`) | 3 | `isBuildSpot` (a cell of the plan, its land loaded, "room for soil" = replaceable **or** a mud crust, a floor or set soil beside it); no living thing in the cell but the colony's own insects, which are lifted onto the new layer (`NestWalls.liftOnto`) |
| A2 | `Mound.addLayer`, the piece branch `:775-780` | a ramp, spire piece or chimney mouth, fresh | the same goal | 3 | `isPieceSpot` `:645-648`: replaceable **and no water or lava**, and what it stands on is there; the same entity rule |
| A3 | `colony/MoundShaping.shape`, carve `:136-142` | finished soil → tunnel block (or a tunnel block's door into the shaft opened) | `ShapeMoundGoal.tick` `:152`; the away builder (A9) | 3 | plan says tunnel, a finished mound block stands there; no entity check (a full block before and after) |
| A4 | `MoundShaping.shape`, reshape `:143-167` | a piece → the plan's piece, or soil, or a tunnel block | the same | 3 | a mound piece stands there; a player or animal in the cell stops it, own insects are lifted or keep a channel |
| A5 | `MoundShaping.shape`, last branch `:168-170` | `destroyBlock` | never: `shapeJobAt` `:78-85` cannot return "clear" (only a shaft cell has that job, and it answers null for shaft cells) | - | dead code |
| A6 | `Mound.crumbleLeftovers` `:482-497` | pieces of earlier stages where the stage now has nothing → gone (`destroyBlock`, no drops) | the colony's once-a-second tick while near, once after each stage change and once after each load | 3 + sound | land loaded, the block is a mound piece |
| A7 | the shaft cleared: `Mound.isClearJob` `:856-866` answers, the framework digs (`DigGoal`; away: `BuildLife.dig` `framework/colony/BuildLife.java:463-470`) | mound soil or diggable earth in a shaft cell → air | a digger's goal; away catch-up | 3 | a shaft cell of the plan, land loaded, the block is a mound block or in the tag `colonyframework:diggable`; never the plug in the entrance or a hole |
| A8 | `block/TermiteTunnelBlock.setWindow` `:215-223`, called by `BuildMoundGoal.place` `:238` and `closeWindow` `:276` | one side of a tunnel block opened or shut | a worker's goal (window open about 12 ticks) | 3 | it is a tunnel block and the cell beside it has no collision |
| A9 | `colony/MoundAwayBuilding.buildAt`, soil `:108-124` | a whole finished block of soil, age 1 or more | away catch-up (`BuildLife.catchUp`: at most 20 blocks a tick, 200 a second) | 3 | `Mound.jobAt` says soil (replaceable and no fluid, or wet soil, or crust), `spotIn`, `BuildLife.freeAndHidden` (no living thing in the cell, out of every player's sight), soil in hand |
| A10 | `MoundAwayBuilding.buildAt`, piece `:125-137` | a ramp, spire piece or mouth, age 1 or more | the same | 3 | `isPieceSpot`, `freeAndHidden` |
| A11 | `MoundAwayBuilding.buildAt`, carve, reshape, clear `:138-158` | as A3, A4, A7 | the same | 3 | `freeAndHidden`, then the same checks |
| A12 | `MoundAwayBuilding.takeSoil` `:172-188` → `BuildLife.dig` | the next cell of the galleries under the mound → air | the same | 3 | `Nest.firstDiggable` (land loaded, soil, a place to stand, no water beside it), not in a player's sight |
| A13 | `Mound.buildInstantly` `:927-978` | the whole mound of a stage at once; mound blocks outside the new shape in a 29 x 29 box → air | the debug command, tests, and **new land** (`NewLandMounds.raise` `:498`) | 2 | a cell is filled if it is replaceable (water too), a mound block, or diggable earth; nothing else |

### B. What the blocks do by themselves

| # | Who | What | When | Flags | Checks |
|---|---|---|---|---|---|
| B1 | `block/MoundSoil.age` `:45-49` (soil, ramps, spires, mouths, tunnels) | one age older | the block's random tick, by the tick's own dice | 2 | none needed |
| B2 | `TermiteMoundBlock.updateShape` `:168-172` | a block still being built → air when its floor and the set soil beside it are gone | a neighbour changed | (the caller's) | `canSurvive` `:159-165` |
| B3 | `TermiteTunnelBlock.updateShape` `:192-196`, `TermiteRampBlock.updateShape` `:106-109`, `TermiteMouthBlock.updateShape` `:137-143` | its own open sides / corner / slim foot | a neighbour changed | (the caller's) | each looks one block away only |
| B4 | `TermiteTunnelBlock.useItemOn` `:227-243` | a window opened or shut | a player with a shovel | 3 | air beside it |
| B5 | `block/TermitomycesBlock.growOn` `:109-119` | next stage; wilted → gone; a giant button → B6 | random tick, bone meal | 3 | - |
| B6 | `block/GiantTermitomycesBlock.growAt` `:90-98` | three blocks of mushroom | from B5 | 3 | earth under it, three replaceable cells with no fluid; no look for a creature (the stalk is thin) |
| B7 | `GiantTermitomycesBlock.growOn` `:157-174`, `updateShape` `:120-130`, `playerWillDestroy` `:134-142` | next stage, or the whole mushroom gone | random tick of the foot; a part broken | 3 | - |

### C. The garden, the mushrooms, the bare patches

| # | Who | What | When | Flags | Checks |
|---|---|---|---|---|---|
| C1 | `colony/FungusGarden.grow` `:173-254` (the write is `:234`) | a comb's layers ripen, go old, crumble; the last layer gone → air | the colony's once-a-second tick, near | 3 | it is a comb; nothing crumbles under an egg or nymph; dice from `Colony.random()` |
| C2 | `FungusGarden.age` `:100-126` | layers ripened and gone old while away | on return (the away hooks) | 3 | a comb out of every player's sight |
| C3 | `FungusGarden.takeFoodAt` `:422-429`, `putBack` `:444-451` | one nodule fewer or more | a nurse's goal | 3 | it is a comb |
| C4 | comb layers laid and eaten | - | the framework's food store and `NestLedger` (round 1 read them) | - | not read here |
| C5 | `colony/Mushrooms.sprout` `:92-110` | a mushroom button (at most 8 a rain, `garden.max_mushrooms`) | the colony's tick, when a rain ends | 3 | `isGoodSpot` `:128-143`: land loaded, open sky, replaceable and no fluid, earth or finished soil under it, never in the mound's plan, the nest or a hole; dice from `Colony.random()` |
| C6 | `colony/BarePatches.eatenAway` `:84` | the grass block under an eaten tuft → the species' bare ground | when a tuft near a hole is eaten | 3 | it is a grass block |
| C7 | `BarePatches.tick` `:109-140`, `unbare` `:157-165` | bare ground → grass block; a young tuft | the colony's tick, every 200 ticks | 3 | land loaded (the spot and the block under it), nothing of the colony or any creature there, air, the tuft can live there |

### D. The dead mound (`DeadMounds.tick`, every 100 ticks, for every dead mound whose middle is in loaded land, no player needed near)

| # | Who | What | Flags | Checks |
|---|---|---|---|---|
| D1 | `colony/DeadMounds.updateCombs` `:361-383` | comb → dead comb (furred, black, rotten, fewer layers) → air | 3 | land loaded; a living or dead comb stands there. **Not counted** in the 256 budget |
| D2 | `DeadMounds.wear`, pieces `:399-410` → `crumble` `:294-300` | ramps, spire pieces, mouths → gone | `destroyBlock` (3 + sound), or 3 with no sound when `quiet` | land loaded, a mound piece, nothing of a player's over it |
| D3 | `wear`, columns `:412-438` | the top mound block of a column → gone, again and again down to its day's height | the same | the column's ground cell loaded; a mound block is taken; any other block that is not replaceable **keeps the column for good** |
| D4 | `wear`, the washed soil `:440-459` | one block of old mound soil on the ground in a ring 2 wide round the foot | 3 | replaceable, no fluid, a firm top of dirt, sand or mound soil under it. **No look for a creature in the cell** |
| D5 | `DeadMounds.cap` `:515-534` | the new top of a worn column: tunnel block → old soil (or its top opened: a way in) | 3 | - |
| D6 | `DeadMounds.closeBeside` `:543-557` | a tunnel block's side laid bare shut again | 3 | land loaded |
| D7 | `DeadMounds.grass` `:564-584` | the top mound block of a settled column → grass block, a tuft over 1 in 3 | 3 | mound soil or a tunnel block only |
| D8 | `colony/DeadWorks.crumbleAround` `:184-206`, `crumbleSpot` `:217-231` | mud crust and mud casts → gone | 3 (+ crumbs) | land loaded, the termites' own mud |
| D9 | `DeadWorks.closeHole` `:244-261` | a foraging hole (its plug, or the open hole) → grass block or soil | 3 | nobody in an open hole |
| D10 | `DeadWorks.fillIn` `:264-273` | a gallery cell that is air → the ground's own soil | 3 | `Nest.isDugOut` (air, a heap or a soil ramp), nobody in it |
| D11 | `DeadMounds.agedForNewLand` `:274-288` | D1 to D10 brought up to the mound's day **in one tick, no budget**, without sounds | as above | as above |

The budget: `BLOCKS_PER_LOOK = 256` for all dead mounds together, every 5 seconds (`:63-69`, `:245-259`).

### E. New land (`NewLandMounds.tick`, every 10 ticks, at most one mound a look; `SavannaEvents.onLevelTick` `:165-179`)

| # | Who | What | Flags | Checks |
|---|---|---|---|---|
| E0 | `worldgen/TermiteMoundPiece.postProcess` `:63-68` | **no block at all**: only hands the place to the server (`NewLandMounds.queue`) | - | round 8 read this |
| E1 | `colony/NewLandMounds.clearForMound` `:508-516` | every block in the plan's cells that is not air, replaceable, diggable, a mound block or bedrock → air | 2 | **none** (no look for a block with something in it, a tree, another structure) |
| E2 | `Mound.buildInstantly` (A13) | the mound | 2 | as A13 |
| E3 | `NewLandMounds.digNest` / `dig` `:522-545` | the shaft and every room: **anything but air and bedrock** → air | 3 | none (no look for water or lava beside the cell) |
| E4 | `NewLandMounds.fillGardens` `:548-560` | 2 or 3 layers of comb on each garden floor | (the framework's) | air over a firm floor |
| E5 | `DeadMounds.agedForNewLand` (D11) | a dead mound worn to its day | 3 | as D |
| E6 | `NewLandMounds.humpGrass` `:642-660` | grass tufts on an old hump | 2 | grass block under, air |
| E7 | `NewLandMounds.burrow` `:668-692` | a 2 x 2 tunnel from the hump's foot to the old nest → air; 8 spots of coarse dirt | 2 | diggable, a mound block, or replaceable and not air |
| E8 | `NewLandMounds.woods` `:699-730` | 1 to 3 trees on an old hump (`TreePlacer.place`) | 2 | dirt or mound under the foot, no other tree's place, the biome. **No look whether the tree's whole box is loaded** |
| E9 | `NewLandMounds.deadTree` `:762-843` | one dead tree 10 to 30 blocks past the mound's foot | 2 | the tree's whole box loaded (`LandReady.box` `:814`), nothing in its way (`blocked` `:925-962`) |

All of E runs on the server thread, in the live world, after `LandReady.round(middle, 24)` says every chunk within 24
blocks is made and in memory (`:195`, `:250-252`); E9 waits for 64 blocks (`:210`).

A note on E8, checked after the table was written: a grown thorn tree on an old hump (age 0.55 to 0.8, its foot 2.5
to 5 blocks off the middle, `:711-716`) reaches about 21 or 22 blocks from the middle by my sum (the crown grows with
age to the power 1.5, `tree/UmbrellaShape.java:213-218`, `:422`; a full crown is 21 to 24 blocks, `:116`). That is
inside the 24 blocks the mound waits for, by 2 or 3 blocks. So no finding, but the margin is thin and nothing in the
code checks it (the dead tree, E9, does check its own box).

## The findings

Grades: **A** the player can meet it; **B** it costs us when building on this; **C** small. Each is marked **proven**
(the path through the code walked, lines named), **likely** (the one link not checked is named) or **to measure**.

## Y-1 (A, proven in the code; how often it happens is not measured): the soil that washes out round a dead mound can appear inside a standing animal or ant, because it is the one placement that never looks who is in the cell

- **What is wrong.** Five to fourteen days after a colony dies, a ring of old mound soil, one block high and two
  blocks wide, settles on the ground round the mound's foot. Each block of it is a whole, solid block put straight
  into a ground-level cell. Every other place where the mound code puts a solid block first asks "is somebody
  standing here?" and waits, or lifts the colony's own insects onto the new block. This one does not ask.
- **What the player would see.** A small animal or an ant standing by a dead mound at the wrong moment is suddenly
  inside a block of soil and takes "stuck in a wall" damage until it walks out or dies. The likeliest victims are the
  Matabele ants, because a dead mound is where they live: a dead mound of new land that died less than about 14 days
  before it was made gets its ring later, in play, with the ants walking over that very ground. A player standing
  there is only pushed aside (the head is above the block), from what I know of Minecraft.
- **Where.** `savanna/.../colony/DeadMounds.java:440-459` (the loop; the write is `:452`). The block is a whole one:
  `Mound.soil` `savanna/.../colony/Mound.java:1011-1014`, shape `block/TermiteMoundBlock.java:49-53`; it is not
  marked "does not suffocate" as the tunnel and the mouth are (`registry/SavannaBlocks.java:75-77` against `:90`,
  `:101`). Ants are hurt by a wall round them: `framework/entity/AbstractAntEntity.java:709-713`.
- **The proof.** (1) `DeadMounds.tick` `:245-259` calls `update` `:351-358`, which calls `wear` `:392`. (2) The loop
  at `:440` takes every column of the ring whose day has come (`MoundWear.apronDay`: day 5 to about 14) and whose
  ground cell is in loaded land. (3) `:449-451` reads the cell and the block under it and asks four things: the cell
  is replaceable, holds no water, the block under it has a firm top, and that block is dirt, sand or mound soil.
  (4) `:452` puts the block. Between (3) and (4) there is no look for a creature. (5) The same question is asked
  everywhere else: `Mound.addLayer` `:765-794` (a stranger stops it, own insects are lifted), `MoundShaping.shape`
  `:143-167`, `framework/colony/BuildLife.java:480-482` (`freeAndHidden`), `DeadWorks.closeHole` `:251`,
  `DeadWorks.fillIn` `:268` ("never bury an animal", its own comment), `BarePatches.takenByTheColony` `:153`.
- **Unchecked link.** None in the path. Not traced: what a buried ant's legs do next (walk out, or stand). No count
  of how often a creature is in one of the ring's cells at its moment; each cell gets its block once, so it is rare
  for one cell and the ring of a big mound has about 200 cells.
- **The fix proposed.** No new rule: use the one the neighbours use. If somebody is in the cell, leave the column for
  the next look (the loop already skips columns with `continue`). Best done as part of the one shared question of
  Y-3, so the next placement cannot forget it either.

## Y-2 (A if the measure confirms it; likely, to measure): every block is checked for its own chunk only, but Minecraft reads the blocks beside it on every change, and dead mounds are worn right at the edge of the loaded land

- **What is wrong.** Before the mound code changes a block it asks "is this block's piece of land (its chunk) made
  and in memory?" That is the right question for the block itself. But when a block changes, Minecraft itself looks
  at the six blocks beside it (to tell them, and to let their shapes follow). For a block in the last row of a chunk,
  one of those six is in the next chunk. If that chunk is not in memory, Minecraft does not skip it: it stops the
  game and fetches it. The mound code never asks about that next chunk. Every write in the table uses flags 3 or 2,
  and both make Minecraft read the neighbours.
  This matters most for dead mounds: a living colony only works while a player is within 64 blocks, but a dead mound
  is worn whenever its middle is in loaded land, however far the player is. So dead mounds are worn in the outermost
  ring of loaded land, which is exactly where the next chunk is missing.
- **What the player would see.** A short freeze now and then while walking or flying through a savanna with dead
  mounds that still have wear to do (a mound that died less than 30 days ago, or one whose land was not loaded for
  days and is catching up, up to 256 blocks every 5 seconds). How long is not known: see the unchecked link.
- **Where.**
  - The question asked: `framework/colony/LandReady.java:44-60` (`at`: one chunk, `getChunkNow`).
  - Dead mounds worn with no player near: `DeadMounds.tick` `:245-259` (only `LandReady.at(mound.center)`), against
    `framework/colony/AwayLife.java:164-175` (`isHere`: a player within 64 blocks) for the living.
  - The writes: `DeadMounds.crumble` `:294-300`, `updateCombs` `:376`, `:380`, `wear` `:452`, `cap` `:525`, `:530`,
    `grass` `:576`, `:578`; `DeadWorks.crumbleAround` `:198`, `:200`, `crumbleSpot` `:225`, `closeHole` `:259`,
    `fillIn` `:271`. For the living mound and the rest: every row of parts A, B and C.
  - One read of the mod's own with the same gap: `block/TermiteMoundBlock.besideSetSoil` `:109-118` reads the four
    cells beside a wall cell; it is called through `canStartAt` `:92-103` from `Mound.spotIn` `:615`, `:634`, where
    only the wall cell's own chunk was checked (`Mound.getBuildSpots` `:589`).
  - That the authors know the trap for their own reads: `DeadMounds.closeBeside` `:547`, `DeadWorks.crumbleAround`
    `:188` and `FeedingGallery.foodBlocks` `:175` all check the cell beside before reading it.
  - The project's own record of the same thing in Minecraft's code: `docs/BUGS.md` 183 (a vault block's neighbour
    read at the edge of new land froze the game 8 to 13 seconds).
- **The proof.** (1) A full mound's foot is up to 27 blocks across (`MoundPlan.REACH = 13`), 31 with the washed soil;
  a chunk is 16. So nearly every mound lies across two to four chunks. (2) `tick` wears a mound as soon as its
  middle's chunk is ready. A cell in a chunk that is not ready is skipped (`:401`, `:414`, `:445`, `:466`); a cell in
  the last ready row is not skipped, and it is worn. (3) `crumble` is `destroyBlock`, flags 3. (4) From what I know
  of Minecraft 1.21.1: a change with flags 3 calls "neighbour changed" on the six cells beside it and lets their
  shapes follow; both read the neighbour's block through the normal `getBlockState`, which asks for the chunk "whole,
  and wait for it". (5) The outer ring of loaded land is whole and in memory, the chunk beyond it is not: that is
  what an edge is.
- **Unchecked link.** Minecraft's code was not opened, so steps (4) and (5) are from memory, and the cost of the
  fetch is not known. The neighbour chunk has been made before in almost every case (a mound is only put in once
  everything 24 blocks round it is made, `NewLandMounds.java:195`), so the fetch is a load from disk, not new land
  being made; I expect milliseconds to tens of milliseconds a time, not seconds, but that is a guess. **To measure:**
  a flight run (`tools/flight.sh`) over land with dead mounds whose clocks were set back, then look in the log for
  `[stall]` lines with `DeadMounds` or `DeadWorks` in them.
- **Who is exposed.** Dead mounds: at any view distance. Living mounds, the garden, the mushrooms, the bare patches:
  only when the player is within 64 blocks and the loaded land ends that close, so only at a view distance of 2 to 4
  chunks. New land: safe (everything 24 blocks round is ready before the first block; the mound reaches 15).
- **The fix proposed.** One place, not eleven: the shared question of Y-3 asks for the cell's chunk and, when the
  cell lies in a chunk's last row, for the chunk beside it too (`LandReady.box(x - 1, z - 1, x + 1, z + 1)`; for a
  cell not on an edge that is the same one chunk, so it costs nothing more). A cell that fails waits for a later
  look, as a cell in land not loaded does today. No new number and no flag.

## Y-3 (B, proven): "may the colony change this block?" is answered in about a dozen places, each a little differently

- **What is wrong.** Every row of the table has its own small set of checks. They mostly agree, but not on purpose:
  each was written when its feature was built. Y-1 and Y-2 are both "one place did not ask one of the questions".
  The five questions, and how the answers differ today:
  1. **Is the cell free for soil?**
     - `Mound.jobAt` `:514`: replaceable **and no water or lava**. This is what "is the stage done?"
       (`isStageDone` `:410-417`) and the builders who work while the player is away
       (`MoundAwayBuilding.buildAt` `:108-124`) go by.
     - `TermiteMoundBlock.isRoomForSoil` `:128-130`: replaceable, **water and lava included**, or a mud crust. This
       is what the real workers go by (`Mound.spotIn` `:612`, `:631`, `TermiteMoundBlock.addLayer` `:140`) and what
       "this wall cell is done" goes by (`Mound.isWallDone` `:564-570`).
     - `Mound.isPieceSpot` `:645-648`: replaceable and no fluid. `Mound.buildInstantly` (A13): replaceable, or a
       mound block, or diggable earth. The washed soil (`DeadMounds.java:450`): replaceable and no fluid.
     - So for a wall cell with water in it the same mound gives two answers: real workers near the player pack soil
       into the water (and into lava), the away builders never do, and the stage is called finished with the water
       still there. Walked: `jobAt` `:519` gives null for a water cell (`empty` is false at `:514`); `spotIn` `:612`
       lets the same cell through and `:615` offers it as a building spot.
  2. **Is somebody in the cell?** Real workers: a stranger stops the work, own insects are lifted
     (`Mound.addLayer` `:765-794`). Away builders: nobody at all, and out of every player's sight
     (`BuildLife.freeAndHidden`). Dead works: nobody (`DeadWorks.nobodyIn` `:275-277`). Bare patches: nobody
     (`BarePatches.java:153`). The washed soil: not asked (Y-1). The giant mushroom: not asked (`B6`; its stalk is
     thin, so no harm).
  3. **Is the land there?** `LandReady.at` for the cell in most rows; a whole box for the dead tree; 24 blocks round
     for new land; nothing for the cells beside (Y-2). Not asked at all in three reads that are safe today only
     because of who calls them: `Mound.addInside` `:428-440` reads the height map for every shaft cell
     (`GroundTop.isRoofed`, whose own note says the caller must check first), `FungusGarden.getCombs` `:144-152` and
     `DeadMounds.addCombs` `:233-242` (a colony only ends while a player is near, `framework/colony/ColonyManager.java`,
     so the garden is loaded).
  4. **Whose block is it?** The living mound: only replaceable cells, its own blocks, and diggable earth in its
     shaft (`Mound.isClearJob` `:856-866`). The dead wear: any block that is not a mound block and not replaceable
     is "a player's" and keeps the whole column (`DeadMounds.java:425-428`), a piece under one stays (`:486-497`).
     New land: everything in the plan's cells that is not soil is cleared (`NewLandMounds.clearForMound`
     `:508-516`), and the nest is dug through everything but bedrock (`dig` `:540-545`). See also Y-5: a mound
     block itself has no owner.
  5. **Does the world allow it?** The game rule `mobGriefing` (off = "mobs may not change blocks") is asked by the
     animals (`animal/Grazing.java:84`, `animal/HippoEntity.java:1183`) and nowhere in the colony code (a search of
     `savanna/.../colony`, `block`, `entity/goal` and `framework/.../colony` finds no use).
- **What the player would see.** Today, little: the water difference shows only at a mound that touches water (the
  places new land picks keep off water, `worldgen/MoundPlaces.java:239-243`, but a player can pour a bucket). The cost
  is in building on this: nuptial flights, floods, seasons and every later species add more block-changing code, and
  each new piece has to remember all five questions again.
- **Unchecked link.** None for the differences listed. The new-land rows (clear everything, dig through everything,
  no look for water beside a dug cell) are safe today for reasons outside the mound code that I did not walk: the
  savanna's caves are filled in and the giant trees keep off the mounds' places (round 8; its R8-18 and R8-19 are
  the cases where caves still get in).
- **The fix proposed.** One shared question in the framework, beside `LandReady` and `BuildLife.freeAndHidden`:
  "may this colony put (or take) a block here now?" It answers the five questions in one place: the land (the cell
  and the cell beside it at a chunk's edge), free (one meaning, water said out loud), nobody in it (own insects
  lifted, or wait), whose block, and the game rule. Every row of the table calls it. That removes checks; it adds
  none.
- **Owner's call:** two answers are needed to write that one question.
  - *Water.* Today workers near the player fill water and lava with soil, and the away builders and the plan do
    not. My pick: they fill it, everywhere (it is what the player already sees near the mound, and it lets a colony
    mend a mound a player has poured water on; a real mound sheds water and is rebuilt after rain).
  - *`mobGriefing`.* Today the termites, the garden and the dead wear ignore the rule. My pick: keep ignoring it for
    the colony's own building and wear (a colony that may not place a block is not a colony, and servers switch the
    rule off to stop creepers, not mounds), and say so in `docs/DESIGN.md`.

## Y-4 (B, likely; the look and the sound to be seen in play): a dead mound that was out of loaded land for days melts in front of the returning player, 256 blocks at a stroke, each with its breaking sound

- **What is wrong.** A dead mound only wears while its land is loaded. When the player comes back after several
  days, the mound has all those days to catch up, and it does so in bursts: every 5 seconds up to 256 blocks are
  broken in one tick, each with the breaking sound and crumbs. The 256 are shared by all dead mounds in loaded land,
  in the order of the book, so with two or three mounds catching up it takes minutes. The mound's land loads when
  the player is still far off, but a player on foot covers a view distance in under a minute.
  New land already knows the answer: a dead mound made in new land is worn to its age at once and **without** sound
  or crumbs, because "thousands at once flooded the sound player" (the code's own note, `DeadMounds.java:280`).
  The catch-up in play has no such rule.
- **What the player would see.** Walking up to a mound that died long ago: it stands whole, then drops a slice every
  5 seconds, top first, with a burst of breaking sounds when within hearing. Not the old weathered hump the days
  away should have left.
- **Where.** `DeadMounds.java:63-69` (the budget), `:245-259` (`tick`), `:294-300` (`crumble`: `destroyBlock` unless
  `quiet`), `:274-288` (`agedForNewLand`, the quiet way). The comb changes (`updateCombs` `:361-383`) are outside the
  budget; they are few.
- **The proof.** (1) `tick` runs every 100 ticks (`LOOK_TICKS`) and hands `update` what is left of 256. (2) `wear`
  takes the pieces and then the highest column tops while `used < budget` (`:399`, `:418`). (3) Each is
  `crumble` → `destroyBlock(pos, false)`, which sends the break effect (sound and crumbs) to the players round it.
  (4) `quiet` is only ever set in `agedForNewLand`. A full mound is some thousands of blocks; at 256 every 5 seconds
  that is one to two minutes for one mound alone.
- **Unchecked link.** Not seen in play. The comment at `:388-390` shows the top-first order was chosen so a catching
  up mound "melts from the top", so some of this is meant; what is not handled is the size of each stroke and its
  sound. The sound overflow was measured for the new-land case only (`docs/plans/savanna-loading.md`: 138
  "Maximum sound pool size" lines).
- **The fix proposed.** No new number: reuse what is there. Wear that is more than a day behind is catch-up, and
  catch-up is done the quiet way, and only where no player can see it (the framework already has that question for
  the away builders, `BuildLife.freeAndHidden`); what the player watches is only today's wear, a few blocks, with
  its sound. To see it today: stand by a dead mound a few days old and type `/time add 240000` (ten days).
- **A side note, measured by others, no finding:** a dead mound of new land is first built whole
  (`Mound.buildInstantly`, which also walks a 29 by 29 by about 44 box of cells) and then worn down block by block
  in the same tick (`NewLandMounds.putDead` `:612-631`). `docs/plans/savanna-loading.md` has it at 6 to 95 ms, once
  a mound. If that ever matters, the cheaper way is to never place the blocks the wear takes straight off again.

## Y-5 (B, likely): a mound block has no owner, so a second colony, a dead mound's wear and a player's own mound blocks all read it as "mine"

- **What is wrong.** Every rule that asks "is this the mound's?" only asks what kind of block it is
  (`Mound.isMoundBlock` `:914-917`), never whose. Three things follow, all walked in the code:
  1. **Two living colonies close together undo each other.** A cell that is hollow shaft in colony A's plan and wall
     in colony B's: A's diggers clear it (`isClearJob` `:865`: any mound block in a shaft cell is to be cleared), B's
     builders fill it again (`jobAt` `:519`: an empty wall cell wants soil). Neither ever finishes. A's plan also
     reshapes B's ramps and spire pieces standing in its wall cells into soil (`:519`, a piece in a wall cell is a
     "reshape" job).
  2. **A dead mound's book never learns that something alive took its place.** While a dead mound still wears (30
     days), its columns take whatever mound block stands at their top (`DeadMounds.java:422-424`) and turn the top
     soil to grass (`:572-576`); while its comb still rots (6.5 days), a **living** comb at a remembered place is
     turned into dead comb or air (`:368`, `:376-381`: the `living ||` branch, meant for the colony's own comb at
     the moment it died). Nothing in `DeadMounds` asks whether a living colony now stands there.
  3. **A player's own mound blocks** on a dead mound are worn away with it, while any other block of the player's
     keeps its column for good (`:422-428`).
- **What the player would see.** Today only with a spawn egg or `/summon`: a queen founds her colony on the spot
  where she stands, with no look at what is there (`entity/TermiteQueenEntity.java:149-154`). Put a queen beside a
  living mound, or on a mound that died a week ago, and the two fight over the same cells, block out, block in, for
  as long as both are near; on the dead mound the new colony's first comb turns black. When nuptial flights come
  (M9.5) queens will found by themselves, and real Macrotermes do take over old mounds, so this becomes ordinary
  play unless founding keeps a distance.
- **Where.** As named above, and `NewLandMounds.crowded` (`:286`, 40 blocks apart) as the only place that keeps
  mounds apart today: new land only.
- **Unchecked link.** I did not read the nest site scoring or the founding rules of the framework, so I cannot say
  that nothing else keeps a founding queen away from a mound. For a queen from an egg nothing does (the lines above).
  I did not walk how far apart two colonies must be for a shaft cell of one to lie in a wall of the other; a full
  mound's foot reaches 13 blocks.
- **The fix proposed.** One answer to "whose block?" inside the shared question of Y-3, in place of more special
  cases: a colony may change a mound block only inside its own plan and only if no other living colony's plan holds
  that cell (the first one there keeps it); and a dead mound's book gives up a column, a piece or a comb place the
  moment a living colony's plan or garden covers it. Both are lookups in lists the colonies already keep.
- **Owner's call:** may a new colony found on a dead mound at all? Today nothing stops it and the two then spoil
  each other. My pick: yes, it is real (old Macrotermes mounds are taken over), and the dead mound's book hands the
  place over when it happens; but that is a founding decision for M9.5, not for this round.

## Y-6 (C, proven in the code; what `/time set` does is from memory): a dead mound's wear goes by the day clock, so setting the time back stops it

- **What is wrong.** A dead mound's age is "the day clock now, less the day clock when it died"
  (`DeadMounds.java:98-100`, `:184`, `:279`). The colonies' own clocks run on the game time, which nothing sets
  back; the day clock is the one `/time set` changes. From what I know of Minecraft, `/time set day` does not keep
  the day count: it puts the clock back to the first morning. After that every dead mound's age reads 0
  (`Math.max(0, ...)`), and its wear, its comb's rot and its works stand still until the clock has passed its death
  day again: in an old world, for many days. Sleeping only moves the clock forward, which is fine.
- **Where.** `DeadMounds.java:98-100`. The trees count their days the same way (`tree/Forest.java:361`, `:670`), so
  this is the mod's habit for slow outdoor things, not a slip in one place.
- **The fix proposed.** No change unless the owner minds: it only shows after `/time set`. If it should hold, the
  dead mound keeps "game time when it died" beside the day clock and uses the larger age.

## Y-7 (C, owner's calls; the code paths proven): three places where the dead and the living meet the player's own digging and building

- **Old galleries fill a player's room.** Fifteen to 45 days after a colony dies, each remembered gallery cell that
  is air is filled with the ground's own soil, once (`DeadWorks.fillIn` `:264-273`; "air" is `Nest.isDugOut`,
  `framework/colony/Nest.java:781-787`). The only other check is that no creature is in it. A cellar or a corridor a
  player dug along the old galleries gets soil blocks in those cells. It is real (old galleries do silt up) and each
  cell is filled only once. My pick: leave it, or skip a cell that is open to more than a gallery's width.
- **A player's block in the shaft stays for good.** The diggers clear only mound soil and diggable earth out of the
  hollow shaft (`Mound.isClearJob` `:865`). A cobblestone put in it is never removed. That is right for a termite;
  whether the colony finds another way up was not traced.
- **A broken mound block is built again, and fresh soil drops itself.** Soil of age 0 and 1 drops as an item with
  any tool, older soil with a pickaxe (`data/antcolonies/loot_table/blocks/termite_mound.json` and the pieces'
  files); the plan wants the cell filled again (`jobAt` `:519`). So a mound is a slow, endless source of its own
  block. Away from the player the soil is really dug from under the mound (`MoundAwayBuilding.takeSoil` `:172-188`);
  where a near worker's soil comes from I did not walk again. My pick: fine as it is (the colony pays in work).

## Y-8 (C): small things on the thread

- **A branch that can never run.** `MoundShaping.shape` ends with a `destroyBlock` branch for a "clear" job
  (`colony/MoundShaping.java:168-170`), but `shapeJobAt` (`:78-85`) never hands it one: the shaft is cleared by the
  diggers (A7). It reads as a second place that clears the shaft, and it is not.
- **A window can stay open (likely).** A builder bites a window into a tunnel block, puts its soil and plugs the
  window again, all from its own goal (`entity/goal/BuildMoundGoal.java:238`, `:271-278`, and `stop()` `:157-160`).
  If the termite is killed or taken out of the world while the window is open, nothing else in a living mound shuts
  it (the only other callers of `setWindow` are the shovel and the dead mound's wear). Not checked: from memory,
  Minecraft does not call a goal's `stop()` when its mob dies.
- **Explosions and pistons.** The mound's loot files have no "survives explosion" condition
  (`loot_table/blocks/termite_mound.json`, `termite_spire.json`, `termite_mouth.json`, `termite_ramp.json`,
  `termite_tunnel.json`), the hole's has (`termite_hole.json`): a creeper at a mound leaves every fresh block as an
  item. The mound blocks can be pushed by pistons (no push rule in `registry/SavannaBlocks.java:75-101`; the comb
  and the mushrooms break instead). Neither does harm; the plan simply builds the cells again.
- **The dead comb's "drops nothing"** is true only because its loot file is missing: there is no
  `loot_table/blocks/dead_comb.json` and the block is not marked "no loot table" (`SavannaBlocks.java:54-57`).
- **New land clears and digs with no questions** (E1, E3): everything in the plan's cells that is not soil goes,
  and the nest is dug through anything but bedrock with no look for water or lava beside a cell. By round 8's
  reading (not walked again here) that is safe today because the savanna's caves are filled in and the giant trees
  keep off the mounds' places; it will not be safe by itself in a biome a later species mod adds. What an explosion
  really drops from a mound block is from memory of Minecraft's loot rules, not checked.

## What is solid

- **World making places no block of the mound.** `worldgen/TermiteMoundPiece.postProcess` only hands the place to
  the server; every block is put in the live world, on the server thread, once everything 24 blocks round is made.
  So nothing of the mound can be cut at a chunk border while the world is made, and world making and the live world
  cannot disagree about a block.
- **The living mound does not destroy a player's build.** Soil and pieces go only into replaceable cells (air,
  grass, water) or a thin mud crust; the diggers clear only mound soil and diggable earth, and only in the plan's
  shaft; carving and reshaping touch only the mound's own blocks. Chests, torches, rails and every solid block of
  the player's stay. (The dead wear goes further and keeps the whole column under a player's block.)
- **Living and away building never bury anyone.** `Mound.addLayer`, the reshape, the away builders
  (`BuildLife.freeAndHidden`), the dead works and the bare patches all look first; the colony's own insects are
  lifted onto a new layer. Y-1 is the single exception.
- **No ripple.** The mound's blocks look one block away when a neighbour changes (`TermiteTunnelBlock`,
  `TermiteRampBlock`, `TermiteMouthBlock`), the spire piece not at all, and only a block still being built can fall
  (`TermiteMoundBlock.updateShape`), which cannot set off the next. No block schedules its own tick. The drying
  uses the random tick with flag 2 and stops at the oldest age, so a big mound costs no more ticks than its count
  of young blocks.
- **The amounts at once are capped where the player is.** Real workers place one layer at a time; the away builders
  at most 20 blocks a tick and 200 a second, out of sight; the dead wear 256 every 5 seconds (Y-4 is about how that
  looks, not about its cost); new land one mound every 10 ticks.
- **The mod's own reads beside a cell are guarded** in the dead wear and works (`closeBeside`, `crumbleAround`,
  `FeedingGallery.foodBlocks`); the gap of Y-2 is in what Minecraft reads, and in `besideSetSoil`.
- **Dice.** I found no `level.getRandom()` behind a colony's choice. The garden, the mushrooms and the bare patches
  roll `Colony.random()`; new land rolls a seed of its place (`RandomSource.create(pick.seed())`); the dead wear
  uses no dice at all but a number worked out from each block's place (`MoundWear.jitter`), so it is the same after
  a reload; the blocks' own ageing uses the random tick's dice, which is right for a block; the builders' goals use
  the ant's dice only to spread out how often they look.
- **Mushrooms and comb.** Mushrooms come up only under open sky on earth or finished soil, never in the plan, the
  nest or a hole, at most 8 a rain; comb never crumbles under an egg or a nymph.
- **Falling trees stop at the mound** (the tag `antcolonies:stops_falling_trees` lists every mound block, the combs
  and the heaps; BUGS 341), and the places new land picks keep off water and low ground
  (`worldgen/MoundPlaces.holds` `:222-277`).
- **Not repeated from round 8:** the hand-over from world making to `NewLandMounds` (R8-11, fixed) and its thread
  safety.

## What was read

**In full:** `savanna/.../colony/` `Mound`, `MoundShaping`, `MoundAwayBuilding`, `MoundReach`, `MoundWear`,
`DeadMounds`, `DeadWorks`, `NewLandMounds`, `BarePatches` (lines 30 to 170), `Mushrooms`, `SavannaParts`;
`entity/goal/BuildMoundGoal`, `ShapeMoundGoal`; `block/` `TermiteMoundBlock`, `MoundPieceBlock`, `MoundSoil`,
`TermiteTunnelBlock`, `TermiteRampBlock`, `TermiteMouthBlock`, `TermiteSpireBlock`, `FungusCombBlock`,
`DeadCombBlock`, `TermitomycesBlock`, `GiantTermitomycesBlock`; `registry/SavannaBlocks`;
`worldgen/TermiteMoundPiece`, `worldgen/MoundPlaces`; `framework/.../colony/LandReady`, `GroundTop`; the loot
files of the mound blocks, the comb and the mushrooms; `docs/plans/code-review.md` lines 55 to 164.

**In part:** `colony/FungusGarden` 60-459; `colony/MoundPlan` 1-300 and 754-794; `colony/MoundGrowth` (search
only); `colony/FeedingGallery` 163-182; `SavannaEvents` 60-359; `entity/TermiteQueenEntity` 132-165;
`worldgen/TreePlacer` 893-924; `tree/UmbrellaShape` 208-259; `worldgen/TermiteMoundStructure` (search only);
`framework/.../colony/BuildLife` 20-260 and its `dig` and `freeAndHidden`; `ColonyManager` 36-175; `AwayLife`
164-175; `Nest` 781-787, 1040-1088, 1133-1151; `NestWalls` 732-749; `FoodStore` 767-772;
`framework/.../entity/AbstractAntEntity` 709-713; the species file `macrotermes.json` (the mound, garden and
regrowth numbers); `docs/BUGS.md` (headings, and entries 183 and 214); `docs/plans/savanna-loading.md` 30-59;
`docs/reviews/full/round-08-world.md` (headings, R8-11, "Found solid"); `docs/DESIGN.md` (searches only).

**Not read:** `block/TermiteHoleBlock`, `MudCrustBlock`, `MudCastBlock`, `FacedWall`; `colony/GalleryUpkeep` (what
soil `soilFor` picks), `GardenAway`, `KingCare`; the framework's `DigGoal`, `CarryPelletGoal`, `NestLedger` and the
food store's comb layers (table row C4); the framework's founding and nest site scoring (Y-5's open link); the
game tests' bodies (searched by name only); `docs/DESIGN.md`'s Macrotermes and digging sections in full;
Minecraft's own code (Y-2's and Y-6's open links). Nothing was run and nothing measured.
