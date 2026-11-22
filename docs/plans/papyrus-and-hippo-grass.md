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
- **Plumes start fresh and turn pale** (the user, 2026-11-22: "go fresh turning pale"): a new plume is look A (dusky
  purple-brown) and turns to look B (pale tan) with random ticks over a few game days, like the real ones by the dry
  season; with seasons (M9.5) it follows the shared season source.
- **They bend when pushed** (the user, 2026-11-22: "we're gonna need to add animations for them when a big animal or the
  player goes through them"; how, my picks): a player or a big animal (box at least 0.6 wide: hippo, crocodile, monitor,
  otter, player) moving through a reed column makes the whole column lean away from it, up to about 40 degrees, the top
  leaning most, then spring back upright with a small overshoot, in about a second. Standing still pushes nothing new (a
  hippo resting in reeds does not keep them flapping).
  - *How in Minecraft:* block models can only tilt in 22.5 degree steps and every change redraws the chunk section, so a
    smooth bend is drawn by the client itself: the server marks a pushed column (one block state, set on the push and
    cleared when the spring-back ends, two changes a push); while it is marked, the block's plain model is hidden and the
    client draws the leaning reed with a smooth spring, leaning away from wherever the pusher actually is. Only pushed
    reeds are drawn this way, so a big reed bed costs nothing until something walks through it.
  - *Checked as animations are* (the `CLAUDE.md` rule): offline, then filmed in the game frame by frame from three sides
    (a player and a hippo walking through a bed).
  - *Later, with fish hiding:* a small fish moving among the stems makes them shiver a little, the real tell of fish in a
    reed bed.
- **Where:** reed beds of about 10 to 60 clumps.
  - **Oxbows:** most of them, along the shallow edges; silting and old oxbows the most (their shallow muddy ends).
  - **Rivers:** only the slow near- and mid-inland stretches, about one bed per 100 blocks of bank, at the river mud
    areas and quiet bays.
  - **Never:** on sand bars, crocodile slides or nests, or in pans (they dry out).
- **Fish hide in them** (later, its own step, the user's say): small fish (tilapia, barb) dart into a reed bed when a
  catfish, crocodile or monitor comes near, and are harder to see there.

**Hippo grass** (`antcolonies:hippo_grass`): a grassy mat lying on the water's surface (like a lily pad, a little
higher and shaggier, short blades sticking up through it), and a rooted clump at the bank's edge it grows out from.
- **The user liked the green and flowering looks** (2026-11-22); blue-green dropped.
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
- **Mats dip and part when pushed** (with the reeds' bend, same way): a swimmer or wader pushing through a mat presses it
  under the surface and to the sides; it bobs back up over about a second.
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
2. **The blocks:** reeds (stacking, survive only in or at shallow water, plumes fresh turning pale) and hippo grass
   (mat and clump, the four stages ticking), with drops and the creative tab.
3. **Pushing through:** the reeds' bend and the mats' dip, drawn smoothly by the client; checked offline and filmed in
   the game.
4. **In new land:** beds and fringes placed as above; a game test for where they go and that they never touch a sand
   bar, slide, nest or pan; a game test for the hippo grass cycle; a flight for loading speed.
5. **Hippos nibble hippo grass** (optional, the user's say).
6. **Fish hide in the reeds** (optional, the user's say).
7. **The user's look** in a new world.
