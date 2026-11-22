# Reed beds and hippo grass (the user's idea, 2026-11-22: "I wanna add hippo grass and papyrus to the river")

Plants of the savanna's waters: fun first, inspired by real life (the `CLAUDE.md` rule for everything but the colony
insects). Planned 2026-11-22 after the oxbows and pans (oxbows and pans steps 3 to 5). **Papyrus swapped for reeds** the
same day, after its first look in the viewer (the user: "I thought they were like reeds or tall grass that fish could
hide in"; real papyrus is a bare stem with a mop head, so the plant the user meant is the common reed). The file keeps
its old name so older notes still point here. Looks in the viewer: `python tools/model_viewer.py riverplants`
(`references/river_plants_viewer.html`, painter `tools/river_plants_art.py`).

## What the real ones are like (what we copy)

- **Common reed** (*Phragmites*): leafy stalks like very tall grass, 2 to 4 m, long blades angling off them and a soft
  feathery plume on top leaning to one side (purple-brown when new, pale silvery tan by the dry season). Thick reed beds
  in shallow water and along banks of slow rivers, lakes and backwaters; small fish, tadpoles and young crocodiles hide
  in them.
- **Hippo grass** (*Vossia cuspidata*): a coarse grass rooted at the water's edge whose long stems lie out across the
  surface as a floating green fringe, often a few metres wide, along slow river banks and lake shores. Named for growing
  where hippos live; they nibble it, but graze mostly on land at night. **Its year** (the user's idea, 2026-11-22: a life
  cycle ending in dying): young shoots creep out from the bank, a grown green mat, flowering (pale spikes), then dying back
  when the water drops (straw brown, breaking up and sinking); the roots at the bank live on and it grows out again.

## In the game (my picks)

**Reeds** (`antcolonies:reeds`, a block like tall grass that stacks): 2 to 3 blocks over the water, standing in water 1
or 2 deep (waterlogged, on the bed) or on wet river mud at the waterline; stalk blocks under a top block with the plume,
drawn as three crossed planes (a thick bed), each clump nudged a little off its block's middle. No collision: animals,
fish and players pass through. Does not spread for now.
- **Plume looks:** A fresh (dusky purple-brown), B old (pale tan); the user picks one, or (my suggestion) plumes start
  fresh and turn pale with time like the real ones.
- **Where:** reed beds of about 10 to 60 clumps.
  - **Oxbows:** most of them, along the shallow edges; silting and old oxbows the most (their shallow muddy ends).
  - **Rivers:** only the slow near- and mid-inland stretches, about one bed per 100 blocks of bank, at the river mud
    areas and quiet bays.
  - **Never:** on sand bars, crocodile slides or nests, or in pans (they dry out).
- **Fish hide in them** (later, its own step, the user's say): small fish (tilapia, barb) dart into a reed bed when a
  catfish, crocodile or monitor comes near, and are harder to see there.

**Hippo grass** (`antcolonies:hippo_grass`): a grassy mat lying on the water's surface (like a lily pad, a little
higher and shaggier, short blades sticking up through it), and a rooted clump at the bank's edge it grows out from.
- **Life stages:** young shoots (light green, sparse, 1 block out), grown (lush green, out to 3), flowering (pale spikes),
  dying back (straw brown, broken up; the mat pulls back to 1 block and then to the clump alone, whose foot shows new
  green). The blue-green look is dropped (the user liked green and flowering; blue-green is not a real stage).
- **How it ticks** (my picks): each clump and mat has a stage in its block state and moves on with random ticks (cheap,
  as crops do); a full cycle about 8 game days (so a player sees it turn), every patch starting at its own stage, so a
  bank shows patches at every stage. Young and grown clumps grow mats out over the water (up to 3 blocks), dying mats
  vanish from the outside in, and the clump starts the cycle again. When seasons come (M9.5) the stage follows the one
  shared season source instead (dying back in the dry season, flowering late in the wet), per the season rule.
- **Where:** a fringe 1 to 3 blocks wide out from the bank, in patches 5 to 20 blocks long, along slow river banks
  (near and mid inland) and round oxbows; about one patch per 60 blocks of bank. Never on sand bars, slides or nests,
  never in pans.
- **Hippos nibble it** (my pick, fun): a hippo in the water at night eats a mat now and then; it grows back with the
  cycle.

## Fits with what is there (checked)

- Blocks only, no entities: no lag. Random ticks on plant blocks cost about what crops do. Placed with new land after
  the banks and the algae (`vegetal_decoration`), only on their own chunk's columns.
- Crocodiles keep their sand bars, slides and nests (no plant there). Hippos and the monitor walk through reeds (no
  collision). Fish swim through the waterlogged reeds.
- Termites and ants: not affected (Macrotermes do not forage in wet ground; ants and termites already go round wet mud).

## Steps

1. **Looks in the model viewer** (done 2026-11-22, first papyrus, then reeds and the hippo grass stages): reed beds and
   the hippo grass fringe on an oxbow and a river bank, beside a hippo and a player for scale; the user picks.
2. **The blocks:** reeds (stacking, survive only in or at shallow water) and hippo grass (mat and clump, the four
   stages ticking), with drops and the creative tab.
3. **In new land:** beds and fringes placed as above; a game test for where they go and that they never touch a sand
   bar, slide, nest or pan; a game test for the hippo grass cycle; a flight for loading speed.
4. **Hippos nibble hippo grass** (optional, the user's say).
5. **Fish hide in the reeds** (optional, the user's say).
6. **The user's look** in a new world.
