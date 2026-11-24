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
   93, top 85 / 93, front 74 / 79. Page `references/workshop/distichodus/versions.html`. v3 (the user on v1: "the top left of their eyes go past their body and stick out"): each eye one piece per head slice, a quarter pixel proud of its own slice and inside a fuller cheek (`HEAD_FLANK_DEPTH` 0.85), 2 pixels across; the pupil painted across the pieces' step; 69 boxes. v4 (the user on v3: "the pupil in the middle is now split, you can make the fish slightly bulkier so that doesn't happen"): the body 4.5 across (`WIDEST` 2.25), the head level across the eye (`CHEEK` 1.5) so each eye is one flush box with a whole pupil; 67 boxes; against the sheet side 93 / 93, top 94 / 92, front 83 / 76. Waiting for the user's OK.
3. Shape detail: skipped (my pick, as the barb's: the shape already carries the jaw, every fin and the eye). **The
   user: v4 "Shape is OK" (2026-11-22).**
4. Paint (made 2026-11-22, v5: `paint()` in `tools/distichodus_models.py`, the sheet's colours by where each texture
   pixel lies: the bronze back line, peach-gold upper flank, cream-gold lower flank, cream belly; the six straight
   bands, slate core and softer edge, down the sides and across the back; the head silver-pink, darker on top, the
   bright gill cover; the red-ringed eye; the fins red-orange, deep red in a thin strip at the body, orange tips; the
   small pale fin). **The user: "Paint is OK" (2026-11-22).**
5. Clips (made 2026-11-22, `clips()` in `tools/distichodus_models.py`; game files by `python tools/distichodus_models.py
   game`: `geo/entity/distichodus.geo.json`, `textures/entity/distichodus.png`, `animations/entity/
   distichodus.animation.json`; body plan `body_plans/distichodus.json`, family fish): `idle`, `swim` (0.6 s beat, the
   game scales it with speed), `graze` (nose up under a mat, two bites torn off with head shakes), `nibble` (nose down
   to reed shoots, three nips), `bolt` (the C-start escape), `leap` (out of the water in an arc: the game throws it and
   must not pitch the fish itself while it plays; marked airborne in the plan), `flare` (back fin stood up, breast
   fins spread, an S and head jerks), `flop`, `death`. Offline check (`clip_check.py distichodus --switches`): nothing
   inside the ground, 12 clip changes, none flagged (the leap ends straightening up, so the swim takes over gently).
   The back fin keeps its base on the back in every clip (the user: "the fin on the top cuts off of him"): folding leans
   it back 20 degrees while it grows 0.55 as tall, the flare makes it 1.36 as tall, never tipped (`FIN_LEAN`,
   `FIN_SHRINK`, `FIN_GROW`). The in-game frame films come with the entity (step 6): nothing draws the fish in the game
   before it. Waiting for the user's look.
6. Into the game: the entity, spawning (the check list, `RiverLifeTests.worldWouldSpawn`), grazing hippo grass and
   reeds, bolting and leaping, crocodiles and the bull shark taking it; game tests `river`.
   **Built 2026-11-23 (the river food web's step 5):** `animal/DistichodusEntity`, `DistichodusGrazeGoal`,
   `client/DistichodusRenderer`; its own group `river_grazer` (cap 8), groups of 2 to 4 in water 2 to 4 deep, river zones
   near, mid and oxbow. Grazes under living hippo grass mats (bites by the tilapia's outer-edge rule, one a fish every 2
   minutes), nibbles reed feet and algae for looks (my pick: reed shoots are not eaten away), flares at a smaller one now
   and then, bolts into cover (the food web's step 4), leaps when hit or (half the time) struck at close by from within 2
   blocks of the surface; teen and grown crocodiles and the bull shark take it as a tilapia. The clip blend is 6 ticks
   (3 snapped into the nose-down nibble in the offline check), 3 for the bolt and leap. Filmed in the game from three
   sides (`tools/film/distichodus_frames.film`, sheets in `references/anim_lab/distichodus/game/`). Tests in `river`.
