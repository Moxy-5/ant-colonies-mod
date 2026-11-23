# The six-banded distichodus (M9 river life; planned 2026-11-22, step 1 made)

The user, 2026-11-22: "make the six banded distichodus", with a photo (`references/inspiration/distichodus_photo_side.png`),
and "you design it" (Claude draws the design sheet and makes the model through the workshop, not Neon). Then: "don't
make it angled like that, make it straight, that's just the image it happened to be based on", then "the face is still
curved": the photo's fish is turned toward the camera and tilted, and a tracing of it carried that angle into the head
however it was levelled (two strikes on tracing), so the side is drawn from straight lines side on ("yes, straight
lines"): the photo guides only the colours, the fins' shapes and the bands. Animals
rule: generalized, fun first, inspired by the real fish (*Distichodus sexfasciatus*, a big plant-eating fish of
central African rivers); no source checking; the game balancer holds. Every number is my pick unless marked.

## Why
The reeds and hippo grass (`docs/plans/papyrus-and-hippo-grass.md`) named it the river's big plant eater: the first
animal to graze the hippo grass mats and the young reed shoots, so the plants are part of the food web, and a bright
fish beside the silver barbs and grey tilapia.

## The fish
- **Look (step 1, the design sheet `references/inspiration/distichodus.png`, drawn by `tools/distichodus_sheet.py`
  from straight lines):** a deep, flat-sided body (depth about 0.3 of the length, the head about a quarter); the snout
  on the middle line, the head a straight wedge top and bottom, the back rising straight to the back fin and falling
  straight to the tail root, the belly nearly flat; a silver-pink head with a small mouth at the tip; a
  big eye with a red ring; six dark slate bands, straight and evenly spaced, on a peach-gold body, the first behind
  the gill cover, the last a narrow ring at the tail's base; a cream belly; a tall swept-back red back fin, a deep
  forked red tail, red-orange belly, anal and breast fins, a small pale fin on the back near the tail.
- **Size:** made 37 model pixels long, drawn 0.8 blocks grown (the tilapia 0.6, the barb 0.25), each one 0.85 to
  1.15 of that. Box budget under 70 (only a few near a player).
- **Where:** the near- and mid-inland rivers and the oxbows, in slower water 2 to 4 deep along the edges, by the reed
  beds and hippo grass mats when there are some (none at the coast, none in the pans).
- **Groups:** 2 to 4 grazing together, loosely; a big one flares its fins and chases a smaller one off its patch now
  and then (looks only).
- **Food: no hunger (fewer rules, as the barb).** Beside hippo grass it grazes the young and grown stages from under
  the mat (head tipped up, a nip and a shake), and nips young reed shoots at the stem's foot; with no plants near it
  picks at the algae for looks only. A bite sets a mat back at most one block, never kills a clump (the plants plan's
  rule for the tilapia), and each fish bites at most once every 2 minutes, so a group cannot strip a bed.
- **Danger:** a player or a hunter close makes the group bolt into the reeds or under a mat; a hard scare (a strike,
  a hit) sends one leaping clear of the water (real distichodus are strong jumpers).
- **Eaten by:** grown crocodiles and the bull shark, as they take a tilapia (the same feed). Too big for the catfish.
- **Spawning (the spawning check list):** its own group `antcolonies:river_grazer`, cap 8 round a player, kept within
  96, gone past 128; groups of 2 to 4; river zones near, mid and oxbow only. Hippo grass is not in new land yet (the
  plants' step 4), so it does not need plants to spawn.
- **Later:** breeding (the animal life cycle), young brighter orange than the grown ones.

## Steps
1. **Design sheet, brief and outline** (made 2026-11-22; `references/inspiration/distichodus.png`, `.brief.md`,
   `references/outlines/distichodus.json` from `tools/distichodus_sheet.py --outline`, page
   `references/workshop/distichodus/outline.html`; fill scores side 90 / 92, top 86 / 90, front 79 / 66, the front's small fins left to the 3D step). Waiting
   for the user's OK on the outline. **The user: "Outline is OK" (2026-11-22).**
2. 3D shape in plain colours (made 2026-11-22, v1: `tools/distichodus_models.py`, 67 boxes, 9 bones: root, body,
   head, jaw, tail_stalk, tail_fin, back_fin, fin_left, fin_right; the belly fins, the fin under the tail and the
   small pale fin sit on the body and the stalk). The top and belly run straight between the sheet's points, never
   eased; body slices 2 pixels, head slices 1 so the wedge face reads straight. Compare against the sheet: side 93 /
   93, top 85 / 93, front 74 / 79. Page `references/workshop/distichodus/versions.html`. Waiting for the user's OK.
3. Shape detail. 4. Paint (the bands painted at 2 texture pixels a model pixel).
5. Clips: `idle`, `swim`, `graze`, `bolt`, `leap`, `flop`, `death` (checked with `clip_check.py` and in-game films).
6. Into the game: the entity, spawning (the check list, `RiverLifeTests.worldWouldSpawn`), grazing hippo grass and
   reeds, bolting and leaping, crocodiles and the bull shark taking it; game tests `river`.
