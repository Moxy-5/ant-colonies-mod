# Body plans and the gait maker (M3: B1 + B2 of the animation upgrade review)

The user's pick, 2026-10-18: "Lets start with the gait and body plan maker". Built 2026-10-18 on branch
`claude/nostalgic-engelbart-1cf92f`. Goal: describe a body once and let every tool and the game read it, and make walks
from the real rule for when each foot steps, so the next creatures (Matabele ants, spiders, centipedes, lizards...)
are not hand-tuned from scratch. **The ants look and walk exactly as before** (proof below).

## Temporary bone names (the user's Blockbench worker, M2, is not ready yet)

Every part has two names in its body plan:

- the **role name** (left in the plan's `bones` table): the name the tools' code and the rest of the plan use. Today
  these are the placeholder models' bone names (`leg_l1`, `leg_l1_lower`, `leg_l1_foot`, `petiole`, `carry`, ...).
  They live only in code, so they never need to match anything the user makes.
- the **bone name** (right): the name the bone has in the model file.

Today both sides are the same. When the user's Blockbench model arrives with its own bone names, only the right-hand
side of its plan changes; `python tools/body_plan.py check` then says at once if a name is wrong or a bone is left out,
and `python tools/body_plan.py rename-test` proves that renaming changes nothing (it gives every bone a made-up name
and checks the lab reads, checks and walks the worker exactly the same).

## The body plan file

`src/main/resources/assets/antcolonies/body_plans/<model>.json`, one per geo model (worker ant, termite worker, king,
queen, minor and major soldier), listed in `index.json` there (the game cannot list a folder inside the mod). Parts:

- `bones`: the rename table (above).
- `legs`: each leg's key (`l` or `r`, then its pair counted from the front: `l1` is the front left leg) and its bones
  from the body outward: hip, knee, foot. (A model with more bones a leg would list them here too; the motion library
  bends three joints today.)
- `head`: the head bone, the jaws, the antennae (each a chain from the socket out) and the sockets (`carry`, where a
  load is held in the jaws).
- `head_player`: the bones the head's animation player owns; every other bone is the body's.
- `waist`: the joints behind the thorax that bend over edges and round turns, with each one's share of the bend.
- `forebody`, `root`: the bone that sways with the stride, and the one that holds everything.

Who reads it:

- the tools (`tools/body_plan.py`): the lab (`anim_lab.py`: the feet, the legs, the antennae, the checks), the motion
  library (`ant_motion.py`: the legs it bends) and the clip writer (`ant_models.py`: clips are made in role names and
  renamed to bone names as they are written). A model read from a game file has its bones renamed to their roles as it
  is read, so everything inside the tools works in role names.
- the game (`entity/body/BodyPlans`): the head player's bones (`AntAnimationController.HEAD_BONES`), the load's bone
  (`PelletLayer`), the head and the waist the live layer bends (`BodyLanguage`, through `InsectModel`). A bone no plan
  names falls back to the old name rules, so a model without a plan still works.

## The gait maker

One rule, shared by real walkers with legs in pairs (Wilson 1966, "Insect walking"): on each side the steps run as a
wave from the back leg to the front one, each leg landing a fixed share of the cycle (the **lag**) after the leg behind
it, and the two sides do the same half a cycle apart. `ant_motion.gait(legs, lag, sides, lost)` turns that into each
foot's touch-down moment. Two numbers give every real gait:

| legs | lag | gait | real animal |
|---|---|---|---|
| 6 | about 0.5 | tripod (L1 R2 L3 together, then R1 L2 R3) | ants, termites, beetles walking at speed |
| 6 | a little over 0.5 | tripod, the feet of a tripod landing one after another, hind first | the ant's walks today: 0.53, slow 0.58 |
| 6 | about 0.25 | wave gait (one or two feet up at a time) | slow insects |
| 8 | 0.5 | alternating tetrapod (L1 R2 L3 R4 together) | spiders (Wilson 1967) |
| many | small | waves running tail to head | centipedes (Manton 1952) |
| 4 | 0.25 | lateral-sequence walk (left hind, left fore, right hind, right fore) | mammals, slow lizards |
| 4 | 0.5 | trot (diagonal pairs) | lizards, dogs, horses |
| 2 | 0.5 (`sides=0`: hop) | walking, or hopping with both together | birds |

**Lost legs:** the rule runs over the legs that are left. Both middle legs lost: the four left step in diagonal pairs
like a trotting quadruped, as real insects do (Hughes 1957, Wilson 1966). One lost leg: the rule gives a start; the
balance check says whether the body still stays over its feet (with today's walk numbers it does not: a real insect
also keeps its feet down longer, which is the lost-legs step, B5, later).

**The balance check** (`ant_motion.balance`, and a new line in the lab's check for every walk): through one cycle,
how far the body's centre of weight (the middle of every part that is not a leg, weighed by its size) stays inside the
feet on the ground. A slowly walking insect is balanced at every moment; a fast runner need not be, its speed carries
it (Ting, Blickhan and Full 1994).

The ant's two step tables, typed in by hand on 2026-10-16, are now made by the gait maker (`TRIPOD = gait(lag=0.53)`,
`LOOSE_TRIPOD = gait(lag=0.58)`) and come out exactly the same numbers. The walk and turn code no longer names legs
(it asks the body plan which are the middle legs, the front left one, the pair of each), so it works for any leg count.

## Proof that nothing changed

- Every model and clip file the tools write (`worker_ant`, all the termites) is byte for byte the same as before.
- The lab's checks for the worker and every termite give the same lines as before (plus the new balance line).
- The pose viewer page is the same.
- The rename test passes (every bone renamed: same feet, same checks for all 17 clips, same walk).
- `./gradlew build`, with a new unit test (`BodyPlansTest`): every plan's bones are in its model, and the game's answers
  (the head player's bones, the load's bone, the worker's waist) are exactly the old ones for every bone of every model.
- The guard films: all checks pass, and filmed again 0 of 600 pictures changed in the walk, ledge and termite scenes (a first run's changes were noise: the unchanged code, filmed the same day, showed the same kind).

## Found, not changed

- The hot-ground walk (`walk_hot`, on stilts with the gaster raised) has the body's centre of weight outside its feet
  for 23% of the cycle (up to 0.53 units). Fine for a fast runner; to look at with the user if it should change.

## Next (not built; the user picks)

- B5 lost legs in the game (longer duty factors, hiding the lost leg's bones).
- B11 termites onto the motion library (their walk still slides its feet), using their own body plans.
- The gait maker choosing the lag and duty factor from the speed by the real numbers, in place of the three hand-set
  walks (changes how the ants look, so only with the user).
- Legs with more than three joints, for the user's Blockbench model if it has them.
