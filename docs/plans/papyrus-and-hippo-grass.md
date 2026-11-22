# Papyrus and hippo grass (the user's idea, 2026-11-22: "I wanna add hippo grass and papyrus to the river")

Plants of the savanna's waters: fun first, inspired by real life (the `CLAUDE.md` rule for everything but the colony
insects). Planned 2026-11-22 after the oxbows and pans (oxbows and pans steps 3 to 5); nothing built yet.

## What the real ones are like (what we copy)

- **Papyrus** (*Cyperus papyrus*): a sedge 3 to 5 m tall, a bare three-sided green stem topped by a round mop of fine
  threads (the umbel). Rooted in still or slow, shallow water (up to about 1 m), in dense stands; it needs water all
  year, so it lines quiet backwaters, oxbows and swamp edges, not fast channels, sand bars or pans that dry out. Fish,
  frogs and young crocodiles shelter in it.
- **Hippo grass** (*Vossia cuspidata*): a coarse grass rooted at the water's edge whose long stems lie out across the
  surface as a floating green fringe, often a few metres wide, along slow river banks and lake shores. Named for growing
  where hippos live; they nibble it, but graze mostly on land at night.

## In the game (my picks)

**Papyrus** (`antcolonies:papyrus`, a block like sugar cane): a column 3 to 5 blocks tall, its base in water 1 deep
(waterlogged, on the bed) or on wet river mud at the waterline; stem blocks under a head block (a big mop drawn as a
crossed plant, wider than a block). No collision (animals, fish and players pass through, as with sugar cane). Grows
back up from its base when cut, like sugar cane; does not spread for now.
- **Where:** stands of about 8 to 40 stems.
  - **Oxbows:** most of them. One stand in a fresh oxbow along its 1-deep edge; one or two in silting and old oxbows at
    their shallow muddy ends.
  - **Rivers:** only the slow near- and mid-inland stretches, about one stand per 150 blocks of bank, at the river mud
    areas and hippo pools.
  - **Never:** on sand bars, crocodile slides or nests, or in pans (they dry out).

**Hippo grass** (`antcolonies:hippo_grass`): a grassy mat lying on the water's surface (like a lily pad, a little
higher and shaggier), and a rooted clump at the bank's edge it grows out from.
- **Where:** a fringe 1 to 3 blocks wide out from the bank, in patches 5 to 20 blocks long, along slow river banks
  (near and mid inland) and round oxbows; about one patch per 60 blocks of bank. Never on sand bars, slides or nests,
  never in pans.
- **Hippos nibble it** (my pick, fun): a hippo in the water at night eats a mat now and then; the patch grows back over
  a few game days.

## Fits with what is there (checked)

- Blocks only, no entities: no lag. Placed with new land after the banks and the algae (`vegetal_decoration`), only on
  their own chunk's columns.
- Crocodiles keep their sand bars, slides and nests (no plant there). Hippos and the monitor walk through papyrus (no
  collision). Fish swim through the waterlogged bases; papyrus as fish cover can come later.
- Termites and ants: not affected (Macrotermes do not forage in wet ground; ants and termites already go round wet mud).

## Steps

1. **Looks in the model viewer:** papyrus stands (3 to 5 tall, the mop head) and the hippo grass fringe on an oxbow
   and a river bank, beside a hippo and a player for scale; the user picks. Textures and block models are Neon's lane
   (`docs/HANDOFF.md`); Claude writes the blocks, placement and tests.
2. **The blocks:** papyrus (column, grows back like sugar cane, survives only in or at shallow water) and hippo grass (mat
   and clump), with drops and the creative tab.
3. **In new land:** stands and fringes placed as above; a game test for where they go and that they never touch a sand
   bar, slide, nest or pan; a flight for loading speed.
4. **Hippos nibble hippo grass** (optional, the user's say).
5. **The user's look** in a new world.
