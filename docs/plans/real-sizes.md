# Real sizes and personal space (planned 2026-11-05)

**Why (the user, 2026-11-05):** the raids "look genuinely awful": the Matabele ants are so big they all stack together. The fix: every savanna insect drawn at its real size, set by one rule (a Matabele major half a block long, two to a block), with termites at their real lengths beside it, and insects that keep a small gap from each other instead of standing inside one another. The sizes were seen and picked in the size viewer (`python tools/model_viewer.py sizes`, `references/sizes_viewer.html`): "Yes, real sizes".

## The sizes (decided)

One scale: **a Matabele major (15 mm) is 0.5 blocks long, so 1 block = 30 mm of real ant.** Every insect's drawn length = its real length / 30. (This is the body scale only; the ground scale, a block a metre for ranges, does not change.)

| Insect | Real length | Blocks | Today |
|---|---|---|---|
| Matabele major | 15 mm | 0.50 | 0.94 |
| Matabele minor (model) | 7.5 mm (most minors 9 to 11, drawn from their own length as today) | 0.25 | 0.65 |
| Matabele queen | 1.2 x a major (the user's pick) | 0.60 | 1.56 |
| Termite worker | 4.5 mm (minor workers 3.5 to 4, major workers 5 to 6) | 0.15 | 0.36 |
| Termite minor soldier | 7 mm | 0.23 | 0.47 |
| Termite major soldier | 12 mm | 0.40 | 0.75 |
| Termite king | 14 mm | 0.47 | 0.42 |
| Termite queen | starts as the king, swells | see step 1 | |

Real lengths are my picks from the literature, without antennae. The harvester ant (desert mod) is left as it is until its rework.

## Steps

### 1. One size rule (the drawing, the box, the climbing)
- Each species file gets a `body_scale` (blocks per mm, 1/30) and each caste its real length; the drawing scale, the collision box (`.sized(...)` today, made from the length) and the per-ant Matabele size (`worker_sizes`) all come from it, so they can never drift apart again.
- Body plans re-measured (`python tools/body_plan.py measure`), so climbing, the waist bend and the edge tipping fit the new bodies.
- Everything carried or left behind follows: carried termites and their stacks, corpses, lost legs, clamped soldier heads, eggs, larvae, nymphs, the prey pile's look stays a block.
- The termite queen: she swells with age today; her full size checked against the real one (a mature queen is roughly 4 to 7 times the king's length) and kept below what fits her royal chamber. My pick: her swelling keeps its shape, scaled with the rest.
- Checks: anim lab, body plans, guard films (every picture changes size: looked at, then approved), `legs`.

### 2. Reach and speed
- Every distance measured from an insect's body in blocks (bite, sting and grab reach, "at its place", the march's gap, waiting spots round a breach, carrying) read through: the ones that meant "a body length" become body lengths, the ones about the ground stay blocks.
- Walking speed: the legs must not race. Speeds set in body lengths a second from real ants where known (termites about 3, the walks plan), and checked against the game's distances so a raid's trip stays about as long as today (a balance check: scout reach 96, mounds 40 to 80 apart, raid hours). My pick: block speeds lowered a little, the walk clips' stride follows the body.
- Checks: `raids`, `soldiers`, `termitefood`, `colony`, `away`.

### 3. Personal space (no more stacking)
- Not Minecraft's pushing (costly with many mobs, fights the climbing bodies, would jam the 1-block tunnels). Instead, in the legs layer: an insect walking or standing next to another (bodies overlapping) steps aside or waits; standing insects choose free spots; a march keeps a body length or so between ants, as real columns do.
- Real details kept: ants pile up and climb over each other at a crowded breach, and termites squeeze past each other in tunnels, so a little overlap is allowed there (my pick); never one body standing inside another for long.
- Cheap: neighbours looked up in a small grid per area, not every insect against every other. Measured with `/antcolonies perf` before and after.
- Checks: a new game test (20 raiders milling at an entrance: no two overlapping for more than a second), the raid tests, guard film of a column.

### 4. Full run and in-game test
- Full run on 4 servers, guard films, speed check.
- In-game test: `/summon antcolonies:matabele_major` beside termite workers and a major soldier; a raid with `/antcolonies scout anytime` near a new-land mound; look at a column from a few blocks away and from 10.

## Your calls
- The sizes: decided (real sizes, the table above).
- In-game test needed after step 4; every guard film picture changes, so the films are approved again.
- Small termite workers (0.15 blocks) are specks from 10 blocks away and harder to hit with a sword: accepted with "real sizes".

## My picks
- One scale in the species file, everything derived from it.
- The queen's swelling scaled with the rest; checked against her chamber.
- Personal space in the legs, not Minecraft's pushing; a little overlap allowed at a breach and in tunnels.
- Block speeds lowered a little so legs do not race; raid trip times kept about the same.
