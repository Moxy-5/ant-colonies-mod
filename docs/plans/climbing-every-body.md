# Climbing done once, for every body (animation upgrade M4, with B3 the spine)

Planned 2026-10-18 with the user ("lets get started on climbing done once for everybody"). Branch `climbing-every-body`
(from `brain-danger`, which holds the merged animation upgrade). Review: `docs/plans/animation-engine-upgrade-review.md`,
M4 and B3.

**Built 2026-10-18** (steps 1 to 5; details and results in `docs/HISTORY.md`, "Climbing done once for every body"): the abdomens have 6 segments, one per drawn plate, not 4; the three climbing scenes are `k_climb_minor`, `l_climb_major` and `m_climb_king`; two new problems at the foot of a wall fixed (`docs/BUGS.md` 52); two old ones left as known bugs (53, 54). Not tried in-game yet.

## What is wrong today

The climbing body (the climbing session of 2026-10-16) was written for the harvester worker. Every insect uses the
worker's numbers, only scaled by its drawn size:

| Where | The worker's number | What it is |
|---|---|---|
| `EdgeOver.FRONT_FEET`, `HIND_FEET` | 3.6, 2.85 units | how far the front feet reach ahead of the middle and the hind feet behind: the tipping over an edge or round a wall's foot starts and ends there |
| `AbstractAntEntity.WAIST_AT`, `GASTER_START`, `GASTER_END` | 0.64, 2.4, 5.2 | where the waist and the gaster lie behind the middle: how the rear follows the path |
| `WAIST_TIGHTEST` | 3.0 | the tightest curve the path takes |
| `AntRenderer.CLIMB_LIFT`, `CLIMB_INTO_WALL` | 0.25, 0.17 | how far the drawn body is lifted up a wall and pushed toward it; the major soldier's set by hand (0.4, 0.27) |

Measured from the models (the lab, 2026-10-18), the termites are nothing like the worker:

| Model | Front feet | Hind feet | Rear part (behind the middle) |
|---|---|---|---|
| worker ant | 4.0 | 3.0 | gaster 2.3 to 5.6 |
| termite worker | 2.8 | 1.2 | abdomen 0.4 to 3.4 |
| termite king | 3.3 | 1.4 | abdomen 0.4 to 4.0 |
| minor soldier | 1.6 | 3.0 | abdomen 2.0 to 5.0 |
| major soldier | 2.1 to 2.7 | 5.0 | abdomen 3.7 to 8.0 |

So a major soldier going over an edge is drawn as if its hind feet were 2.85 units back when they are 5: it is tipped
over before its back half has left the old surface, and its abdomen follows the path as if it were an ant's gaster.

## The idea

Each body plan (`assets/antcolonies/body_plans/<model>.json`) gets a `climb` part with that body's own numbers,
**measured from the model by the tool** (never typed by hand), and the game reads them. A new creature then climbs
right from its first model: run `python tools/body_plan.py measure <model>` and it is done. The guard films run the
same climbing scenes for every body, so nothing slips.

**The spine (B3):** the rear follows the leader. Today the ant's waist bend is one number shared out over three
joints. Made general, **each segment of the body behind the thorax lies the way the path lay at its own distance
behind**, so a body of any length (an ant's waist and gaster, a termite's abdomen, later a centipede's 21 rings or a
lizard's tail) bends exactly along the ground it walked over: round turns, over edges, up walls.

## Steps

1. **Measure.** `tools/body_plan.py measure <model>` (and `measure all`) writes each plan's `climb` part: the front and
   hind feet (the walk's average stance), the spine (each segment's bone and where it begins and ends behind the
   middle), the tightest curve (from the body's length), and the wall lift and push (from how low the body hangs below
   its feet and how deep its feet are). `check` says when a model has changed since its numbers were measured.
   **The harvester worker keeps its numbers** (3.6, 2.85, 0.64 / 2.4 / 5.2, 0.25 / 0.17): the user approved its
   climbing in-game on 2026-10-16, so its plan holds them as approved and the tool only reports how far its own
   measuring would put them.
2. **The game reads them.** `BodyPlans.Plan` gets the climb numbers; `EdgeOver` takes the feet from the insect's plan;
   `AntRenderer` takes the lift and push from it (`withClimbPose` goes); a model with no plan keeps the worker's.
3. **The spine.** The path memory and the "follow the leader" maths move out of `AbstractAntEntity` into
   `entity/body/Spine` (body only, no deciding), for any number of segments. Each joint's bend is the difference between
   the way the path lay under the segment in front of it and under the segment behind it, each held within a real
   joint's reach. For the ant's three waist joints it gives exactly today's bend: a unit test replays recorded climbs
   (up a wall, over a top, a brink, a turn, a stop half way over an edge) through the old code and the new and they must
   agree to the last digit.
4. **Termite abdomens in soft segments (the user's choice, below).** `tools/termite_models.py` splits the abdomen of the
   worker, both soldiers and the king into 4 segment bones in a chain (`abdomen`, `abdomen_2`, `abdomen_3`, `abdomen_4`;
   the clips that sway `abdomen` still sway the whole of it), the motion library re-makes their clips, the plans list
   the 4 as the spine, and the lab checks every clip again.
5. **Proof.** Unit test: a made-up 12-segment body walked round a corner, up a wall and over an edge: every segment
   along the path, none into a block. Guard films: the new `k_climb_termites` scene (the minor, the major and the king
   up a pillar, over its top and head first down), with the existing `f_termite`. The worker's guard pictures must stay
   byte for byte the same (nothing changes for the ant). `./gradlew build`, the lab checks, game tests `legs`.

## How the user will test it

Termites on a pillar two blocks high (and on the mound's outside pieces): a worker, both soldiers and the king. Going
up and over the top and head first down, the body should tip over the edge from when its front feet reach it until its
hind feet are over, and the abdomen should follow the edge, never sticking into the block or out into the air. The
harvester ant should look exactly as before.

## The user's decision (2026-10-18)

- **Termite abdomens.** Real termites have no waist: the thorax joins the abdomen across its whole width, and the
  abdomen is soft, ten segments under a thin skin, so over an edge or round a turn it curves smoothly along its length
  and sags. Today each termite's abdomen (worker, soldiers, king) is one stiff bone hinged at the thorax. (The small
  queen's is already six segments.)
  **Picked: soft segments** (my number, to confirm: 4 segment bones each; the small queen keeps her six).
