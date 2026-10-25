# The soil ramp: smooth sloping burrow floors (plan)

Planned with the user 2026-10-24, after the Matabele bare-ground burrow became a stair tunnel
(`MatabeleNest.burrow`). The looks first, in the model viewer (`python tools/burrow_viewer.py`,
`references/burrow_viewer.html`); **45 degrees picked by the user 2026-10-24; steps 1 to 3 BUILT the same day**
(step 4, the user's in-game look, open).

## Why
Real burrows (aardvark, rodent, many ants) slope in with a smooth floor packed by feet. Minecraft
blocks only give a stair. A ramp block fills each step's corner so the floor reads as one slope.
**The slope is carved, not built**: a digger bites the step's block down into a ramp instead of
removing it whole; no soil is carried in.

## The user's decisions (2026-10-24)
- **Look: the same soil it is dug in.** A dirt ramp in dirt, sand in sand, red sand in the badlands,
  and so on (one ramp per soil kind, drawn with that soil's own texture), so a burrow blends in.
- **Steepness: 45 degrees (picked 2026-10-24).** Both were drawn to compare in the viewer: 45 degrees (one block down per block forward,
  one full ramp shape) and gentler (one down per two forward, about 27 degrees, a low and a high
  half-ramp; about aardvark steepness). The gentler one's mouth is an open sloping trench for about
  4 blocks before the soil roofs it, as real aardvark burrow mouths are. Picked by eye.
- **Straight only for now.** No corners; added when a species needs curving tunnels (leafcutters).
- **A player breaking one gets its plain soil back** (dirt, sand, ...); no ramp item.

## Shared, not Matabele-only
- The block lives in the **Colony Framework** mod, so every species mod can use it (Matabele burrows
  now; aardvark burrows in M9.5; leafcutter tunnels; desert burrowers). Harvester ants keep their
  straight shafts (real *Pogonomyrmex* tunnels go mostly straight down).
- Walking needs no new code: the framework's `nav/Slope` socket (used by the termite ramp) lets the
  legs walk on any block that gives its surface heights.
- One ramp block per vanilla soil the ants dig (`colonyframework:diggable`): dirt, grass block (its
  ramp is dirt: a carved slope shows the soil), coarse dirt, rooted dirt, clay, mud, sand, red sand.
  A species mod with its own soil (the desert's deep soil) signs up its own ramp through the same helper.

## Steps (after the look is picked)
1. The ramp blocks in the framework (shape, `Slope`, drops, textures, a tag of ramps by soil).
2. Digging: a planned cell can say "carve to a ramp facing X" (the shared nest's room plan carries it);
   `DigGoal` bites it down to the ramp instead of removing it. Species-neutral.
3. The Matabele burrow uses it at the picked steepness (`MatabeleNest.burrow`), its room depth and
   tests updated; `legs` and `colony` tests, a guard film of an ant walking down a burrow.
4. The user tries it in-game.

## As built (2026-10-24)
1. `framework/.../block/SoilRampBlock` (8 steps of 2 pixels, `Slope`, facing = the high side) and `SoilRamps`
   (one ramp per soil: dirt (grass too), coarse dirt, rooted dirt, clay, mud, sand, red sand; `SoilRamps.signUp` for
   a species' own soil). Block states, models, loot tables and names written by `python tools/soil_ramp_blocks.py`.
2. `Nest.settle(site, rooms, ramps)`: the cells to carve and their high sides, saved with the rooms;
   `Nest.carvedInto` asked by `DigGoal` (the last bite sets the ramp, with the block's breaking dust) and by
   `BuildLife.dig` (away). A carved ramp counts as dug out (`Nest.isDugOut`: rooms finish, diggers stand in it).
3. `MatabeleNest.burrow`: the bottom cell of every column of the way is a ramp rising toward the mouth. Test: the
   burrow scene checks every floor cell is a ramp facing the mouth once the queen is in her room. Guard film
   `p_burrow` (a Matabele major down and up the ramps).
